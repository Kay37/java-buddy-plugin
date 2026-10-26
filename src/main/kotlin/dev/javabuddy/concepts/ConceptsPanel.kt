package dev.javabuddy.concepts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.CardList
import dev.javabuddy.ui.emptyState
import dev.javabuddy.ui.heading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent

/** The "Concepts" tab: the current card on top, then every card grouped by topic, with a search box. */
class ConceptsPanel(project: Project) : JPanel(BorderLayout()), Disposable {
    private val cards = project.service<ConceptCards>()
    private val currentHolder = CardList().apply { border = JBUI.Borders.empty() }
    private val listHolder = CardList().apply { border = JBUI.Borders.empty() }
    private val search = SearchTextField(false).apply { textEditor.emptyText.text = "Search, e.g. static" }
    private val scroll: JBScrollPane

    init {
        val page = CardList()
        page.addCard(currentHolder)
        page.addCard(heading("All Java words"))
        page.addCard(search.fixedHeight())
        page.addCard(listHolder)
        scroll = JBScrollPane(page).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            border = null
        }
        add(scroll, BorderLayout.CENTER)

        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = renderList()
        })
        renderList()

        val job = project.service<JavaBuddyScope>().cs.launch {
            cards.current.collect { concept -> withContext(Dispatchers.EDT) { renderCurrent(concept) } }
        }
        Disposer.register(this) { job.cancel() }
    }

    private fun renderCurrent(concept: Concept?) {
        currentHolder.removeAll()
        currentHolder.addCard(
            if (concept != null) conceptCard(concept)
            else Card(BuddyColors.CONCEPT_ACCENT, BuddyColors.CONCEPT_BACKGROUND).apply {
                title("Rest your mouse on a Java word")
                text("Try static, final, extends or the < > in List<String>. A card pops up that explains it, with a small example.")
                text("Names like substring, split, parseInt, StringBuilder and LocalDate have cards too, and so do constructors, getters and setters: click on one and its card shows up here.")
                text("The card also shows up here. So does the card for the word your text cursor is on.", muted = true)
            }
        )
        currentHolder.revalidate()
        currentHolder.repaint()
    }

    private fun renderList() {
        val query = search.text.trim()
        val matches = Concepts.ALL.filter {
            query.isEmpty() || it.label.contains(query, ignoreCase = true) || it.summary.contains(query, ignoreCase = true)
        }
        listHolder.removeAll()
        if (matches.isEmpty()) {
            listHolder.addCard(emptyState("No Java word matches \"$query\"", "Try fewer letters, like \"stat\" for static.", good = false))
        }
        matches.groupBy { it.topic }.toSortedMap().forEach { (topic, concepts) ->
            listHolder.addCard(Card(BuddyColors.CONCEPT_ACCENT, BuddyColors.NEUTRAL_BACKGROUND).apply {
                chip(topic.title)
                concepts.forEach { concept -> linkLine(concept.label, concept.summary) { pick(concept) } }
            })
        }
        listHolder.revalidate()
        listHolder.repaint()
    }

    private fun pick(concept: Concept) {
        cards.select(concept)
        scroll.verticalScrollBar.value = 0
    }

    override fun dispose() = Unit
}
