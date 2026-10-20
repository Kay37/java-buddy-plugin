package dev.javabuddy

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.javabuddy.coach.CoachPanel
import dev.javabuddy.coach.StyleCoach
import dev.javabuddy.concepts.ConceptCards
import dev.javabuddy.concepts.ConceptsPanel
import dev.javabuddy.memory.MemoryPanel
import dev.javabuddy.memory.MemoryService
import dev.javabuddy.practice.PracticePanel
import dev.javabuddy.progress.ProgressPanel
import dev.javabuddy.progress.ProgressService
import dev.javabuddy.errors.ErrorExplainer
import dev.javabuddy.errors.ErrorsPanel
import com.intellij.openapi.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JComponent

/** The Java Buddy side panel. Each learning tool gets its own tab; the everyday ones come first. */
class JavaBuddyToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        fun tab(panel: JComponent, name: String) {
            if (panel is Disposable) Disposer.register(toolWindow.disposable, panel)
            toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel, name, false))
        }
        tab(ErrorsPanel(project), "Errors")
        tab(CoachPanel(project), StyleCoach.TAB_NAME)
        tab(PracticePanel(project), PracticePanel.TAB_NAME)
        tab(ConceptsPanel(project), ConceptCards.TAB_NAME)
        tab(ProgressPanel(project), ProgressService.TAB_NAME)
        tab(MemoryPanel(project), MemoryService.TAB_NAME)
    }
}

/** Starts watching editors for errors and for hovered Java words when a project opens. */
class JavaBuddyStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        withContext(Dispatchers.EDT) {
            project.service<ErrorExplainer>().start()
            project.service<ConceptCards>().start()
            project.service<MemoryService>().start()
        }
    }
}

class ToggleErrorBubblesAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isSelected(e: AnActionEvent) = JavaBuddySettings.errorBubbles
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        JavaBuddySettings.errorBubbles = state
        ProjectManager.getInstance().openProjects.forEach { it.service<ErrorExplainer>().refreshSelected() }
    }
}

class ToggleConceptHoverAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isSelected(e: AnActionEvent) = JavaBuddySettings.conceptHover
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        JavaBuddySettings.conceptHover = state
    }
}

class ToggleCrashPopupsAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isSelected(e: AnActionEvent) = JavaBuddySettings.crashPopups
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        JavaBuddySettings.crashPopups = state
    }
}
