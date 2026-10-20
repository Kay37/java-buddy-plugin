package dev.javabuddy.drills

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.java.JavaLanguage
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import dev.javabuddy.daily.DailyLog
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.notifyUser
import dev.javabuddy.practice.PracticePanel
import dev.javabuddy.practice.PracticeView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Everything the Drills tab shows. [lastChecked] is the drill whose result goes on top. */
data class DrillState(
    val outcomes: Map<String, DrillOutcome> = emptyMap(),
    val running: String? = null,
    val lastChecked: String? = null,
    val passed: Set<String> = emptySet(),
    val hintsShown: Set<String> = emptySet(),
)

/**
 * Opens drills as scratch files (outside the learner's project, so course repos stay clean) and
 * checks them with [DrillChecker]. Finished drills are remembered IDE-wide.
 */
@Service(Service.Level.PROJECT)
class DrillService(private val project: Project) {
    private val _state = MutableStateFlow(DrillState(passed = DrillProgress.passed()))
    val state: StateFlow<DrillState> = _state.asStateFlow()

    /** Opens the drill's file, creating it from the starter code the first time. */
    fun open(drill: Drill) {
        val file = existingFile(drill)
            ?: ScratchRootType.getInstance().createScratchFile(project, drill.fileName, JavaLanguage.INSTANCE, drill.starter, ScratchFileService.Option.create_if_missing)
        if (file == null) {
            notifyUser(project, NotificationType.WARNING, "Java Buddy couldn't create the drill file", "Try again, or check that IntelliJ can write to its Scratches folder.")
            return
        }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun hasFile(drill: Drill) = existingFile(drill) != null

    /** Puts the starter code back, for when the learner wants a fresh start. Asks first: it throws their code away. */
    fun reset(drill: Drill) {
        val file = existingFile(drill) ?: return open(drill)
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val answer = Messages.showYesNoDialog(
            project,
            "This throws away your code for \"${drill.title}\" and puts the starting code back. You can undo it with Ctrl+Z.",
            "Start the Drill Again?", "Start Again", "Keep My Code", Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return
        WriteCommandAction.runWriteCommandAction(project, "Start Drill Again", null, { document.setText(drill.starter) })
        _state.update { it.copy(outcomes = it.outcomes - drill.className, lastChecked = if (it.lastChecked == drill.className) null else it.lastChecked) }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun toggleHint(drill: Drill) = _state.update {
        it.copy(hintsShown = if (drill.className in it.hintsShown) it.hintsShown - drill.className else it.hintsShown + drill.className)
    }

    fun showHint(drill: Drill) {
        _state.update { it.copy(hintsShown = it.hintsShown + drill.className, lastChecked = drill.className) }
        showPanel()
    }

    fun check(drill: Drill) {
        DailyLog.use("drill")
        if (_state.value.running != null) return
        // Find the file here, outside any read lock: finding a scratch file may touch the disk.
        val file = existingFile(drill) ?: return open(drill)
        val cs = project.service<JavaBuddyScope>().cs
        cs.launch {
            val code = readAction { FileDocumentManager.getInstance().getDocument(file)?.text }
            if (code == null) {
                withContext(Dispatchers.EDT) { open(drill) }
                return@launch
            }
            _state.update { it.copy(running = drill.className, lastChecked = drill.className) }
            withContext(Dispatchers.EDT) { showPanel() }
            val outcome = try {
                withContext(Dispatchers.IO) { DrillChecker.check(drill, code, jdkHome()) }
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Exception) {
                LOG.warn("Checking ${drill.className} failed", e)
                DrillOutcome.Failed("Something went wrong while checking: ${e.message ?: e.javaClass.simpleName}")
            }
            val passedNow = outcome is DrillOutcome.Checked && outcome.allPassed
            if (passedNow) DrillProgress.markPassed(drill.className)
            _state.update {
                it.copy(
                    outcomes = it.outcomes + (drill.className to outcome),
                    running = null,
                    passed = if (passedNow) it.passed + drill.className else it.passed,
                )
            }
            withContext(Dispatchers.EDT) { EditorNotifications.getInstance(project).updateNotifications(file) }
        }
    }

    /** The next drill after [drill] that isn't done yet, in course order. */
    fun nextDrill(drill: Drill): Drill? {
        val passed = _state.value.passed
        val after = DRILLS.dropWhile { it != drill }.drop(1)
        return (after + DRILLS).firstOrNull { it.className !in passed && it != drill }
    }

    fun isPassed(drill: Drill) = drill.className in _state.value.passed

    /** Drills live in the Practice tab now, next to Quests. */
    fun showPanel() = project.service<PracticeView>().show(PracticePanel.Page.DRILLS)

    private fun existingFile(drill: Drill): VirtualFile? =
        runCatching { ScratchFileService.getInstance().findFile(ScratchRootType.getInstance(), drill.fileName, ScratchFileService.Option.existing_only) }
            .getOrNull()?.takeIf { it.isValid }

    /** The project's JDK if it has javac, otherwise any JDK IntelliJ knows about that has one. */
    private suspend fun jdkHome(): String? = readAction {
        val projectJdk = ProjectRootManager.getInstance(project).projectSdk?.homePath
        val candidates = listOfNotNull(projectJdk) + ProjectJdkTable.getInstance().allJdks.mapNotNull { it.homePath }
        candidates.firstOrNull { File(it, "bin/javac.exe").isFile || File(it, "bin/javac").isFile } ?: projectJdk
    }

    companion object {
        const val TOOL_WINDOW_ID = "Java Buddy"
        const val TAB_NAME = "Drills"
        private val LOG = logger<DrillService>()
    }
}

/** Finished drills, remembered across projects and restarts. */
object DrillProgress {
    private const val KEY = "javaBuddy.drills.passed"

    fun passed(): Set<String> =
        PropertiesComponent.getInstance().getValue(KEY).orEmpty().split(',').filter { it.isNotBlank() }.toSet()

    fun markPassed(className: String) {
        PropertiesComponent.getInstance().setValue(KEY, (passed() + className).sorted().joinToString(","))
    }
}
