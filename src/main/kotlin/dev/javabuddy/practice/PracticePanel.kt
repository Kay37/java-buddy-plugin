package dev.javabuddy.practice

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.JBUI
import dev.javabuddy.drills.DrillsPanel
import dev.javabuddy.quest.QuestPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * The "Practice" tab: one place to practise, with a switch between Quests (the Java Quest game)
 * and Drills (short exercises). Before, these were two tabs that did nearly the same job.
 */
class PracticePanel(project: Project) : JPanel(BorderLayout()), Disposable {
    private val cards = CardLayout()
    private val pages = JPanel(cards)
    private val quests = JToggleButton("Quests")
    private val drills = JToggleButton("Drills")

    init {
        val questPanel = QuestPanel(project)
        val drillsPanel = DrillsPanel(project)
        Disposer.register(this, questPanel)
        Disposer.register(this, drillsPanel)
        pages.add(questPanel, Page.QUESTS.name)
        pages.add(drillsPanel, Page.DRILLS.name)

        ButtonGroup().apply { add(quests); add(drills) }
        quests.toolTipText = "Java Quest: levels, XP and boss fights"
        drills.toolTipText = "Short exercises, one idea each"
        quests.addActionListener { select(Page.QUESTS) }
        drills.addActionListener { select(Page.DRILLS) }
        val switcher = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(4))).apply {
            add(quests)
            add(drills)
        }
        add(switcher, BorderLayout.NORTH)
        add(pages, BorderLayout.CENTER)
        select(Page.QUESTS)
        project.service<PracticeView>().panel = this
        Disposer.register(this) { if (project.service<PracticeView>().panel === this) project.service<PracticeView>().panel = null }
    }

    fun select(page: Page) {
        cards.show(pages, page.name)
        (if (page == Page.QUESTS) quests else drills).isSelected = true
    }

    override fun dispose() = Unit

    enum class Page { QUESTS, DRILLS }

    companion object {
        const val TAB_NAME = "Practice"
    }
}

/** Opens the Practice tab on Quests or Drills, for code that wants to show a result there. */
@Service(Service.Level.PROJECT)
class PracticeView(private val project: Project) {
    internal var panel: PracticePanel? = null

    fun show(page: PracticePanel.Page) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Java Buddy") ?: return
        toolWindow.show {
            toolWindow.contentManager.findContent(PracticePanel.TAB_NAME)?.let { toolWindow.contentManager.setSelectedContent(it) }
            panel?.select(page)
        }
    }
}
