package dev.javabuddy.errors

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.pluralize
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.CardList
import dev.javabuddy.ui.emptyState
import dev.javabuddy.ui.heading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/** The "Errors" tab: the last crash (if any), then every error in the open Java file, explained. */
class ErrorsPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val explainer = project.service<ErrorExplainer>()
    private val list = CardList()

    init {
        add(JBScrollPane(list).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            border = null
        }, BorderLayout.CENTER)
        val job = project.service<JavaBuddyScope>().cs.launch {
            explainer.fileErrors.combine(explainer.crash) { errors, crash -> errors to crash }
                .collect { (errors, crash) -> withContext(Dispatchers.EDT) { render(errors, crash) } }
        }
        com.intellij.openapi.util.Disposer.register(this) { job.cancel() }
    }

    private fun render(fileErrors: FileErrors?, crash: Crash?) {
        list.removeAll()
        crash?.let { list.addCard(crashCard(it)) }

        when {
            fileErrors == null ->
                list.addCard(emptyState("Open a Java file", "Errors in the file you're editing are explained here in plain English.", good = false))
            fileErrors.errors.isEmpty() ->
                list.addCard(emptyState("No errors in ${fileErrors.file.name}", "Nice! Nothing is underlined in red. Press the green ▶ next to main to run it.", good = true))
            else -> {
                list.addCard(heading("${pluralize(fileErrors.errors.size, "error")} in ${fileErrors.file.name}"))
                fileErrors.errors.forEach { list.addCard(errorCard(it)) }
            }
        }
        list.revalidate()
        list.repaint()
    }

    private fun errorCard(error: ExplainedError): Card {
        val explanation = error.explanation
        return if (explanation == null) {
            Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip("Line ${error.line + 1}")
                title(error.message)
                text("Java Buddy doesn't have a plain-English version of this one yet. Click the red code and press Alt+Enter: IntelliJ often offers a fix.", muted = true)
                links("Go to line ${error.line + 1}" to { explainer.navigate(error.file, error.line) })
            }
        }
        else {
            Card(BuddyColors.HELP_ACCENT, BuddyColors.HELP_BACKGROUND).apply {
                chip("Line ${error.line + 1}")
                title(explanation.title)
                section("WHAT IT MEANS", explanation.meaning)
                section("HOW TO FIX IT", explanation.fix)
                explanation.example?.let(::example)
                text("IntelliJ's message: ${error.message}", muted = true)
                links("Go to line ${error.line + 1}" to { explainer.navigate(error.file, error.line) })
            }
        }
    }

    private fun crashCard(crash: Crash): Card = Card(BuddyColors.CRASH_ACCENT, BuddyColors.CRASH_BACKGROUND).apply {
        chip(if (crash.location != null) "Crashed at ${crash.location}" else "Program crashed")
        title(crash.explanation.title)
        section("WHAT HAPPENED", crash.explanation.meaning)
        section("HOW TO FIX IT", crash.explanation.fix)
        crash.explanation.example?.let(::example)
        text("Java's message: ${crash.exception.substringAfterLast('.')}", muted = true)
        val file = crash.file
        if (file != null && crash.line >= 0) {
            links("Go to ${crash.location}" to { explainer.navigate(file, crash.line) }, "Dismiss" to { explainer.reportCrash(null) })
        }
        else {
            links("Dismiss" to { explainer.reportCrash(null) })
        }
    }

    override fun dispose() = Unit
}
