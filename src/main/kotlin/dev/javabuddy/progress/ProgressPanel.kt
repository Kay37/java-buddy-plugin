package dev.javabuddy.progress

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.concepts.ConceptCards
import dev.javabuddy.drills.DRILLS
import dev.javabuddy.drills.DrillService
import dev.javabuddy.pluralize
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.CardList
import dev.javabuddy.ui.emptyState
import dev.javabuddy.ui.heading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/** The "Progress" tab: every Java idea as a tile, lit up once the learner's commits have used it. */
class ProgressPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val progress = project.service<ProgressService>()
    private val list = CardList()
    private val scroll = JBScrollPane(list).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        border = null
    }

    init {
        add(scroll, BorderLayout.CENTER)
        val job = project.service<JavaBuddyScope>().cs.launch {
            progress.state.collect { state -> withContext(Dispatchers.EDT) { render(state) } }
        }
        Disposer.register(this) { job.cancel() }
        if (progress.state.value.map == null) progress.refresh()
    }

    private fun render(state: ProgressState) {
        val scrollPosition = scroll.verticalScrollBar.value
        list.removeAll()
        val map = state.map
        if (map == null) {
            list.addCard(emptyState("Reading your commits…", "Java Buddy is looking through your git history for the Java ideas you've used.", good = false))
        }
        else {
            list.addCard(summaryCard(map, state))
            state.selected?.let { id -> IDEAS.firstOrNull { it.id == id } }?.let { list.addCard(ideaCard(it, map)) }
            if (state.folders.isEmpty()) list.addCard(addFolderCard(map.suggestions))
            for (group in IdeaGroup.entries) {
                val ideas = IDEAS.filter { it.group == group }
                val used = ideas.count { it.id in map.uses }
                list.addCard(heading("${group.title}  ·  $used of ${ideas.size}"))
                list.addCard(tileGrid(ideas, map, state.selected))
            }
            journey(map)?.let {
                list.addCard(heading("Your journey"))
                list.addCard(it)
            }
        }
        list.revalidate()
        list.repaint()
        scroll.verticalScrollBar.value = scrollPosition
    }

    private fun summaryCard(map: ProgressMap, state: ProgressState): Card = Card(BuddyColors.PROGRESS_ACCENT, BuddyColors.PROGRESS_BACKGROUND).apply {
        chip("${map.uses.size} of ${IDEAS.size} ideas used")
        title("Your Java map")
        progressBar(map.uses.size, IDEAS.size)
        val sources = when {
            map.commits.isEmpty() && map.repos.isEmpty() -> "No git projects found yet. Commit your work, or add the folder that holds your course projects."
            map.commits.isEmpty() -> "No Java commits yet in ${map.repos.joinToString(", ")}. Once you commit some Java code, the ideas you used light up here."
            else -> "Built from ${pluralize(map.commits.size, "commit")} in ${pluralize(map.repos.size, "project")}. Click a tile to see when you first used it."
        }
        text(sources, muted = true)
        map.problems.take(2).forEach { text("Couldn't read $it", muted = true) }
        if (state.folders.isNotEmpty()) {
            state.folders.forEach { folder -> linkLine("Remove", "also reading $folder") { progress.removeFolder(folder) } }
        }
        links(
            (if (state.scanning) "Reading…" else "Refresh") to { progress.refresh() },
            "Add a folder…" to ::chooseFolder,
        )
    }

    private fun addFolderCard(suggestions: List<String>): Card = Card(BuddyColors.HELP_ACCENT, BuddyColors.HELP_BACKGROUND).apply {
        chip("Tip")
        title("See all your course work")
        if (suggestions.isNotEmpty()) {
            text("Right now the map only reads this project. This folder looks like it holds more of your Java projects. Add it, and every project in it counts too:")
            suggestions.forEach { folder -> linkLine("Add", folder) { progress.addFolder(folder) } }
            links("Choose a different folder…" to ::chooseFolder)
        }
        else {
            text("Right now the map only reads this project. Add the folder that holds all your course projects, and every project in it counts too.")
            links("Add a folder…" to ::chooseFolder)
        }
    }

    private fun chooseFolder() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Choose the Folder With Your Java Projects")
            .withDescription("Java Buddy reads the git history of every project in this folder.")
        FileChooser.chooseFile(descriptor, project, null)?.let { progress.addFolder(it.path) }
    }

    private fun ideaCard(idea: Idea, map: ProgressMap): Card {
        val use = map.uses[idea.id]
        val card = if (use != null) Card(BuddyColors.GOOD_ACCENT, BuddyColors.GOOD_BACKGROUND) else Card(BuddyColors.NEUTRAL_ACCENT, BuddyColors.NEUTRAL_BACKGROUND)
        return card.apply {
            chip(if (use != null) "Used ✓" else "Not yet")
            title(idea.name)
            if (use != null) {
                text("First used on ${LONG_DATE.format(use.first.date)} in ${use.first.repo}: \"${use.first.message}\".")
                text("Used in ${pluralize(use.commits, "commit")} so far.", muted = true)
            }
            else {
                text("Your commits haven't used this yet. It will light up the first time you commit code that uses it.")
            }
            val links = buildList<Pair<String, () -> Unit>> {
                idea.concept?.let { concept -> add("What is it?" to { project.service<ConceptCards>().openInPanel(concept) }) }
                idea.drills?.let { topic ->
                    val drills = project.service<DrillService>()
                    val drill = DRILLS.firstOrNull { it.topic == topic && !drills.isPassed(it) } ?: DRILLS.firstOrNull { it.topic == topic }
                    if (drill != null) add("Practice: ${drill.title}" to { drills.open(drill) })
                }
                add("Close" to { progress.select(null) })
            }
            links(*links.toTypedArray())
        }
    }

    private fun tileGrid(ideas: List<Idea>, map: ProgressMap, selected: String?): JComponent =
        object : JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(6))) {
            // WrapLayout needs the real width to wrap; take the list's width so tiles flow onto new rows.
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }.apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            ideas.forEach { add(Tile(it, map.uses[it.id], it.id == selected) { progress.select(it.id) }) }
        }

    /** The last few days with something new, newest first: "Oct 14 · shopping-list · Scanner, printf". */
    private fun journey(map: ProgressMap): Card? {
        val firsts = map.uses.values.groupBy { it.first.date to it.first.repo }.entries
            .sortedByDescending { it.key.first }
            .take(8)
        if (firsts.isEmpty()) return null
        return Card(BuddyColors.PROGRESS_ACCENT, BuddyColors.PROGRESS_BACKGROUND).apply {
            firsts.forEach { (key, uses) ->
                section("${SHORT_DATE.format(key.first).uppercase()}  ·  ${key.second}", "New: " + uses.joinToString(", ") { it.idea.name })
            }
        }
    }

    override fun dispose() = Unit

    private companion object {
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
    }
}

/** One idea on the map: filled and dated once used, an outline until then. */
internal class Tile(private val idea: Idea, private val use: IdeaUse?, private val selected: Boolean, onClick: () -> Unit) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(6, 10, 6, 10)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = if (use != null) "First used ${DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH).format(use.first.date)} in ${use.first.repo}" else "Not used in a commit yet"
        add(JLabel(if (use != null) "✓ ${idea.name}" else idea.name).apply {
            font = JBUI.Fonts.label().deriveFont(if (use != null) Font.BOLD else Font.PLAIN)
            foreground = if (use != null) UIUtil.getLabelForeground() else UIUtil.getContextHelpForeground()
        })
        add(JLabel(use?.let { describe(it.first.date) } ?: "not yet").apply {
            font = JBUI.Fonts.smallFont()
            foreground = UIUtil.getContextHelpForeground()
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = onClick()
        })
    }

    private fun describe(date: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now())
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < 7 -> "$days days ago"
            else -> DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH).format(date)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(10)
            if (use != null) {
                g2.color = BuddyColors.GOOD_BACKGROUND
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                g2.color = BuddyColors.GOOD_ACCENT
            }
            else {
                g2.color = BuddyColors.NEUTRAL_BACKGROUND
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                g2.color = BuddyColors.TRACK
            }
            if (selected) {
                g2.color = BuddyColors.PROGRESS_ACCENT
                g2.stroke = java.awt.BasicStroke(JBUI.scale(2).toFloat())
            }
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        }
        finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}
