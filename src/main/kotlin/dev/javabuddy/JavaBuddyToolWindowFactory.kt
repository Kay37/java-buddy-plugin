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
import dev.javabuddy.errors.ErrorExplainer
import dev.javabuddy.errors.ErrorsPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Java Buddy side panel. Each learning tool gets its own tab. */
class JavaBuddyToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val errors = ErrorsPanel(project)
        Disposer.register(toolWindow.disposable, errors)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(errors, "Errors", false))
    }
}

/** Starts watching editors for errors when a project opens. */
class JavaBuddyStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        withContext(Dispatchers.EDT) { project.service<ErrorExplainer>().start() }
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

class ToggleCrashPopupsAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isSelected(e: AnActionEvent) = JavaBuddySettings.crashPopups
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        JavaBuddySettings.crashPopups = state
    }
}
