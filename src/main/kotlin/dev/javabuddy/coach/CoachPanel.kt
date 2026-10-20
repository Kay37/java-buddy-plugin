package dev.javabuddy.coach

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import dev.javabuddy.daily.DailyLog
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.pluralize
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.CardList
import dev.javabuddy.ui.emptyState
import dev.javabuddy.quest.QuestService
import dev.javacoach.engine.daily.WarmUps
import dev.javacoach.engine.style.Finding
import dev.javacoach.engine.style.RuleFeedback
import dev.javacoach.engine.style.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/** The "Coach" tab: the Style Coach's notes on how the open Java file is written, bugs first. */
class CoachPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
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
        if (notes != null && isShowing) DailyLog.use("coach")
        val scrollPosition = scroll.verticalScrollBar.value
        list.removeAll()
        if (notes == null) {
            list.addCard(emptyState("Open a Java file", "The coach reads how your code is written (names, methods, switch) and gives tips. It never writes the code for you.", good = false))
        }
        else {
            list.addCard(summaryCard(notes))
            notes.warmUp?.let { list.addCard(warmUpCard(it)) }
            notes.visible.forEach { list.addCard(findingCard(notes, it)) }
            if (notes.muted.isNotEmpty()) list.addCard(mutedCard(notes.muted))
        }
        list.revalidate()
        list.repaint()
        // Typing keeps the scroll position; a different file starts at the top.
        val file = notes?.file?.path
        scroll.verticalScrollBar.value = if (file == lastFile) scrollPosition else 0
        lastFile = file
    }

    private fun summaryCard(notes: CoachNotes): Card {
        val shown = notes.visible
        val bugs = shown.count { it.severity() == Severity.BUG }
        val worth = shown.count { it.severity() == Severity.WARN }
        val tips = shown.count { it.severity() == Severity.TIP }
        val nice = shown.count { it.severity() == Severity.GOOD }
        val clean = bugs == 0 && worth == 0
        val card = if (clean) Card(BuddyColors.GOOD_ACCENT, BuddyColors.GOOD_BACKGROUND) else Card(BuddyColors.HELP_ACCENT, BuddyColors.NEUTRAL_BACKGROUND)
        return card.apply {
            chip("Style Coach")
            title("Coach notes for ${notes.file.name}")
            text(listOf(
                pluralize(bugs, "bug"), "$worth worth fixing", pluralize(tips, "tip"), "$nice nice",
            ).joinToString("  ·  "))
            if (shown.isEmpty()) text("Nothing to say yet. Keep going!", muted = true)
            else if (clean) text("No bugs and nothing worth fixing. Nice! The tips below are small extras.", muted = true)
            text("The notes update a moment after you stop typing. Tips also show in the editor, dotted under the line. Press \"Not helpful\" on a tip to stop seeing that kind.", muted = true)
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
            val votes = buildList<Pair<String, () -> Unit>> {
                if (line > 0) add("Go to line $line" to { coach.navigate(notes.file, line) })
                add("Helpful" to { coach.vote(finding, true) })
                if (finding.severity() != Severity.BUG) add("Not helpful (hide these)" to { coach.vote(finding, false) })
            }
            links(*votes.toTypedArray())
        }
    }

    private fun warmUpCard(warmUp: WarmUps.WarmUp): Card = Card(BuddyColors.PROGRESS_ACCENT, BuddyColors.PROGRESS_BACKGROUND).apply {
        val quest = warmUp.quest()
        chip(if (warmUp.replay()) "Replay for a star" else "Warm-up quest")
        title(quest.title())
        text(warmUp.reason())
        links((if (warmUp.replay()) "Replay it" else "Start the warm-up") to {
            val quests = project.service<QuestService>()
            quests.open(quest)
            quests.focus(quest)
        })
    }

    private fun mutedCard(muted: Set<String>): Card = Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
        chip("Hidden tips")
        text("You said these weren't helpful, so the coach keeps them to itself. Bugs always show.", muted = true)
        muted.sorted().forEach { rule -> linkLine("Show again", RuleFeedback.label(rule)) { coach.unmute(rule) } }
    }

    override fun dispose() = Unit
}
