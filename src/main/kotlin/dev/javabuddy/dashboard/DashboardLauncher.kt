package dev.javabuddy.dashboard

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import dev.javabuddy.daily.DailyLog
import dev.javabuddy.notifyUser
import dev.javacoach.dashboard.Dashboard
import dev.javacoach.engine.game.progress.PlayerProgress
import java.io.IOException

/**
 * Runs the Java Coach web dashboard inside IntelliJ, so it's one click away (no terminal).
 * It starts on the first click and stops when IntelliJ closes. It uses port 7070, or any free
 * port when 7070 is taken (for example by a dashboard started from the command line).
 */
@Service(Service.Level.APP)
class DashboardLauncher : Disposable {
    private var dashboard: Dashboard? = null

    fun open(project: Project?) {
        DailyLog.use("dashboard")
        ApplicationManager.getApplication().executeOnPooledThread {
            val url = try {
                "http://localhost:" + running().port() + "/"
            }
            catch (e: IOException) {
                LOG.warn("Couldn't start the Java Coach dashboard", e)
                if (project != null) notifyUser(project, NotificationType.WARNING, "The dashboard didn't start", e.message ?: e.javaClass.simpleName)
                return@executeOnPooledThread
            }
            BrowserUtil.browse(url)
        }
    }

    @Synchronized
    private fun running(): Dashboard {
        dashboard?.let { return it }
        val started = try {
            Dashboard(PREFERRED_PORT, PlayerProgress.defaultFile()).also { it.start() }
        }
        catch (e: IOException) {
            Dashboard(0, PlayerProgress.defaultFile()).also { it.start() }
        }
        dashboard = started
        return started
    }

    @Synchronized
    override fun dispose() {
        dashboard?.stop()
        dashboard = null
    }

    companion object {
        private const val PREFERRED_PORT = 7070
        private val LOG = logger<DashboardLauncher>()
    }
}

/** Tools > Open Java Coach Dashboard. */
class OpenDashboardAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) = service<DashboardLauncher>().open(e.project)
}
