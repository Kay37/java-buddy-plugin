package dev.javabuddy.coach

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.pluralize
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.CardList
import dev.javabuddy.ui.emptyState
import dev.javacoach.engine.style.Finding
import dev.javacoach.engine.style.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/** The "Coach" tab: the Style Coach's notes on how the open Java file is written, bugs first. */
class CoachPanel(project: Project) : JPanel(BorderLayout()), Disposable {
    private val coach = project.service<StyleCoach>()
    private val list = CardList()
    private val scroll = JBScrollPane(list).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        border = null
    }

    init {
        add(scroll, BorderLayout.CENTER)
        coach.start()
        val job = project.service<JavaBuddyScope>().cs.launch {
            coach.notes.collect { notes -> withContext(Dispatchers.EDT) { render(notes) } }
        }
        Disposer.register(this) { job.cancel() }
    }

    private var lastFile: String? = null

    private fun render(notes: CoachNotes?) {
        val scrollPosition = scroll.verticalScrollBar.value
        list.removeAll()
        if (notes == null) {
            list.addCard(emptyState("Open a Java file", "The coach reads how your code is written (names, methods, switch) and gives tips. It never writes the code for you.", good = false))
        }
        else {
            list.addCard(summaryCard(notes))
            notes.report.findings().forEach { list.addCard(findingCard(notes, it)) }
        }
        list.revalidate()
        list.repaint()
        // Typing keeps the scroll position; a different file starts at the top.
        val file = notes?.file?.path
        scroll.verticalScrollBar.value = if (file == lastFile) scrollPosition else 0
        lastFile = file
    }

    private fun summaryCard(notes: CoachNotes): Card {
        val report = notes.report
        val bugs = report.count(Severity.BUG).toInt()
        val worth = report.count(Severity.WARN).toInt()
        val tips = report.count(Severity.TIP).toInt()
        val nice = report.count(Severity.GOOD).toInt()
        val clean = bugs == 0 && worth == 0
        val card = if (clean) Card(BuddyColors.GOOD_ACCENT, BuddyColors.GOOD_BACKGROUND) else Card(BuddyColors.HELP_ACCENT, BuddyColors.NEUTRAL_BACKGROUND)
        return card.apply {
            chip("Style Coach")
            title("Coach notes for ${notes.file.name}")
            text(listOf(
                pluralize(bugs, "bug"), "$worth worth fixing", pluralize(tips, "tip"), "$nice nice",
            ).joinToString("  ·  "))
            if (report.findings().isEmpty()) text("Nothing to say yet. Keep going!", muted = true)
            else if (clean) text("No bugs and nothing worth fixing. Nice! The tips below are small extras.", muted = true)
            text("The notes update a moment after you stop typing.", muted = true)
        }
    }

    private fun findingCard(notes: CoachNotes, finding: Finding): Card {
        val card = when (finding.severity()) {
            Severity.BUG -> Card(BuddyColors.CRASH_ACCENT, BuddyColors.CRASH_BACKGROUND)
            Severity.WARN -> Card(BuddyColors.HELP_ACCENT, BuddyColors.HELP_BACKGROUND)
            Severity.TIP -> Card(BuddyColors.CONCEPT_ACCENT, BuddyColors.CONCEPT_BACKGROUND)
            Severity.GOOD -> Card(BuddyColors.GOOD_ACCENT, BuddyColors.GOOD_BACKGROUND)
        }
        val line = finding.line()
        return card.apply {
            chip(if (line > 0) "${finding.severity().label()} · line $line" else finding.severity().label())
            title(plain(finding.title()))
            richText(finding.message())
            if (line > 0) links("Go to line $line" to { coach.navigate(notes.file, line) })
        }
    }

    override fun dispose() = Unit
}
