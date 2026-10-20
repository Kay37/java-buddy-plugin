package dev.javabuddy.quest

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import dev.javabuddy.JavaBuddyScope
import dev.javabuddy.notifyUser
import dev.javabuddy.practice.PracticePanel
import dev.javabuddy.practice.PracticeView
import dev.javacoach.engine.game.check.CheckResult
import dev.javacoach.engine.game.check.QuestChecker
import dev.javacoach.engine.game.content.StarterCourse
import dev.javacoach.engine.game.model.Course
import dev.javacoach.engine.game.model.Quest
import dev.javacoach.engine.game.progress.Award
import dev.javacoach.engine.game.progress.PlayerProgress
import dev.javacoach.engine.game.review.AnotherWay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A checked answer, and the XP it earned when it passed. */
data class QuestResult(val check: CheckResult, val award: Award?)

/** "Another way": the quest's own solution (only after a pass) and notes on how it differs from the learner's file. */
data class AnotherWayView(val code: String, val notes: List<String>)

/**
 * Everything the Quest tab shows. [progress] is a fresh copy each time it changes; nothing changes
 * it after it's published. [focus] is the quest whose card goes on top.
 */
data class QuestState(
    val progress: PlayerProgress,
    val results: Map<String, QuestResult> = emptyMap(),
    val running: String? = null,
    val focus: String? = null,
    val saveProblem: String? = null,
    val anotherWay: Map<String, AnotherWayView> = emptyMap(),
)

/**
 * Plays Java Quest, the Java Coach engine's game, inside the IDE. Quest files are scratch files in
 * a `java-quest` folder (outside the learner's projects). XP and finished quests are saved in the
 * engine's own file (`~/.javacoach/progress.properties`), so the command-line game and the
 * dashboard see the same progress.
 */
@Service(Service.Level.PROJECT)
class QuestService(private val project: Project) {
    private val _state = MutableStateFlow(QuestState(loadQuietly().first))
    val state: StateFlow<QuestState> = _state.asStateFlow()
    private val saving = Mutex()

    init {
        // Loads the compiler ahead of time, so the first "Check my answer" is quicker.
        project.service<JavaBuddyScope>().cs.launch(Dispatchers.IO) { runCatching { QuestChecker.warmUp() } }
    }

    /** Reads the save file again, in case the command-line game changed it. */
    fun reload() {
        project.service<JavaBuddyScope>().cs.launch {
            val (progress, problem) = withContext(Dispatchers.IO) { loadQuietly() }
            _state.update { it.copy(progress = progress, saveProblem = problem) }
        }
    }

    fun isUnlocked(quest: Quest): Boolean {
        val level = COURSE.levelOf(quest.id()).orElse(null) ?: return false
        return COURSE.isUnlocked(level, _state.value.progress.completed())
    }

    fun isDone(quest: Quest) = _state.value.progress.isDone(quest.id())

    /** Opens the quest's file, creating it the first time: the starter code, or an empty guess file. */
    fun open(quest: Quest) {
        if (!isUnlocked(quest)) return
        val file = existingFile(quest) ?: run {
            val language: Language = if (quest.kind().answersWithCode()) JavaLanguage.INSTANCE else PlainTextLanguage.INSTANCE
            val text = if (quest.kind().answersWithCode()) quest.starter() else ""
            ScratchRootType.getInstance().createScratchFile(project, scratchPath(quest), language, text, ScratchFileService.Option.create_if_missing)
        }
        if (file == null) {
            notifyUser(project, NotificationType.WARNING, "Java Buddy couldn't create the quest file", "Try again, or check that IntelliJ can write to its Scratches folder.")
            return
        }
        FileEditorManager.getInstance(project).openFile(file, true)
        if (!quest.kind().answersWithCode()) focus(quest)
    }

    fun hasFile(quest: Quest) = existingFile(quest) != null

    /** Puts the starter back. Asks first, because it throws the learner's code away. */
    fun reset(quest: Quest) {
        val file = existingFile(quest) ?: return open(quest)
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val answer = Messages.showYesNoDialog(
            project,
            "This throws away your work on \"${quest.title()}\" and puts the starting code back. You can undo it with Ctrl+Z.",
            "Start the Quest Again?", "Start Again", "Keep My Work", Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return
        val text = if (quest.kind().answersWithCode()) quest.starter() else ""
        WriteCommandAction.runWriteCommandAction(project, "Start Quest Again", null, { document.setText(text) })
        _state.update { it.copy(results = it.results - quest.id()) }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    /** Opens the next hint on the quest's ladder (it costs XP until the quest is done) and shows it. */
    fun hint(quest: Quest) {
        if (!isUnlocked(quest)) return
        project.service<JavaBuddyScope>().cs.launch {
            val progress = changeProgress { it.openNextHint(quest) }
            _state.update { it.copy(progress = progress ?: it.progress, focus = quest.id()) }
            withContext(Dispatchers.EDT) { showPanel() }
        }
    }

    /** True when the engine allows showing another way: the quest is passed, and it isn't a predict quest. */
    fun canShowAnotherWay(quest: Quest) = AnotherWay.reveal(quest, _state.value.progress).isPresent

    /** Shows the quest's own solution next to the learner's, with notes on how they differ. Only after a pass. */
    fun showAnotherWay(quest: Quest) {
        val theirs = AnotherWay.reveal(quest, _state.value.progress).orElse(null) ?: return
        val file = existingFile(quest)
        project.service<JavaBuddyScope>().cs.launch {
            val yours = file?.let { readAction { FileDocumentManager.getInstance().getDocument(it)?.text } }
            val notes = if (yours.isNullOrBlank()) emptyList() else AnotherWay.notes(yours, theirs)
            _state.update { it.copy(anotherWay = it.anotherWay + (quest.id() to AnotherWayView(theirs, notes)), focus = quest.id()) }
            withContext(Dispatchers.EDT) { showPanel() }
        }
    }

    fun hideAnotherWay(quest: Quest) = _state.update { it.copy(anotherWay = it.anotherWay - quest.id()) }

    /** Puts the quest's card on top of the Quest tab and shows the tab. */
    fun focus(quest: Quest) {
        _state.update { it.copy(focus = quest.id()) }
        showPanel()
    }

    fun check(quest: Quest) {
        if (_state.value.running != null || !isUnlocked(quest)) return
        // Find the file here, outside any read lock: finding a scratch file may touch the disk.
        val file = existingFile(quest) ?: return open(quest)
        project.service<JavaBuddyScope>().cs.launch {
            val answer = readAction { FileDocumentManager.getInstance().getDocument(file)?.text }
            if (answer == null) {
                withContext(Dispatchers.EDT) { open(quest) }
                return@launch
            }
            _state.update { it.copy(running = quest.id(), focus = quest.id()) }
            withContext(Dispatchers.EDT) { showPanel() }

            val predict = !quest.kind().answersWithCode()
            val result = when {
                predict && answer.isBlank() -> QuestResult(noGuessYet(), null)
                else -> runCheck(quest, answer)
            }
            _state.update { it.copy(results = it.results + (quest.id() to result), running = null) }
            withContext(Dispatchers.EDT) { EditorNotifications.getInstance(project).updateNotifications(file) }
        }
    }

    private suspend fun runCheck(quest: Quest, answer: String): QuestResult {
        val check = try {
            withContext(Dispatchers.IO) {
                val checker = QuestChecker()
                if (quest.kind().answersWithCode()) checker.check(quest, answer) else checker.checkPrediction(quest, answer)
            }
        }
        catch (e: CancellationException) {
            throw e
        }
        catch (e: Exception) {
            LOG.warn("Checking quest ${quest.id()} failed", e)
            return QuestResult(CheckResult(CheckResult.Status.CRASHED, "Something went wrong while checking: ${e.message ?: e.javaClass.simpleName}", 0, 0, 0, false), null)
        }
        var award: Award? = null
        val progress = changeProgress {
            it.recordTry(quest.id())
            if (check.isPassed) award = it.complete(COURSE, quest, check.styleClean(), LocalDate.now())
        }
        if (progress != null) _state.update { it.copy(progress = progress) }
        return QuestResult(check, award)
    }

    /**
     * Loads the save file, applies [change], saves it and returns the new copy (or null when it
     * couldn't be saved; then [QuestState.saveProblem] says why).
     */
    private suspend fun changeProgress(change: (PlayerProgress) -> Unit): PlayerProgress? = saving.withLock {
        withContext(Dispatchers.IO) {
            try {
                val progress = PlayerProgress.load(PlayerProgress.defaultFile())
                change(progress)
                progress.save(PlayerProgress.defaultFile())
                _state.update { it.copy(saveProblem = null) }
                progress
            }
            catch (e: Exception) {
                LOG.warn("Couldn't update the Java Quest save file", e)
                _state.update { it.copy(saveProblem = "Java Buddy couldn't save your quest progress (${e.message ?: e.javaClass.simpleName}).") }
                null
            }
        }
    }

    /** The next quest after [quest] that's open and not done yet, in course order. */
    fun nextQuest(quest: Quest): Quest? {
        val all = COURSE.levels().flatMap { it.quests() }
        val after = all.dropWhile { it.id() != quest.id() }.drop(1)
        return (after + all).firstOrNull { it.id() != quest.id() && !isDone(it) && isUnlocked(it) }
    }

    fun navigate(quest: Quest, line: Int) {
        val file = existingFile(quest) ?: return
        OpenFileDescriptor(project, file, (line - 1).coerceAtLeast(0), 0).navigate(true)
    }

    /** Quests live in the Practice tab, next to Drills. */
    fun showPanel() = project.service<PracticeView>().show(PracticePanel.Page.QUESTS)

    private fun existingFile(quest: Quest): VirtualFile? =
        runCatching { ScratchFileService.getInstance().findFile(ScratchRootType.getInstance(), scratchPath(quest), ScratchFileService.Option.existing_only) }
            .getOrNull()?.takeIf { it.isValid }

    private fun noGuessYet() = CheckResult(
        CheckResult.Status.WRONG_ANSWER,
        "Your guess file is empty. Type what you think the program prints, one printed line per line, then check again.",
        0, 0, 1, false,
    )

    companion object {
        const val TAB_NAME = "Quest"
        /** The scratch folder that holds quest files, so they never mix with drills. */
        const val FOLDER = "java-quest"
        val COURSE: Course = StarterCourse.create()
        private val LOG = logger<QuestService>()

        private fun loadQuietly(): Pair<PlayerProgress, String?> = try {
            PlayerProgress.load(PlayerProgress.defaultFile()) to null
        }
        catch (e: Exception) {
            LOG.warn("Couldn't read the Java Quest save file", e)
            PlayerProgress() to "Java Buddy couldn't read your saved quest progress (${e.message ?: e.javaClass.simpleName}), so it starts fresh for now."
        }

        fun className(quest: Quest): String = Regex("""\bclass\s+(\w+)""").find(quest.starter())?.groupValues?.get(1) ?: quest.id()

        /** `Thermometer.java` for code quests, `CopyTrap-guess.txt` for predict-the-output quests. */
        fun fileName(quest: Quest): String =
            if (quest.kind().answersWithCode()) "${className(quest)}.java" else "${className(quest)}-guess.txt"

        fun scratchPath(quest: Quest) = "$FOLDER/${fileName(quest)}"

        /** The quest a scratch file belongs to, or null. */
        fun questFor(file: VirtualFile): Quest? {
            if (file.parent?.name != FOLDER) return null
            if (ScratchFileService.findRootType(file) !is ScratchRootType) return null
            return COURSE.levels().flatMap { it.quests() }.firstOrNull { fileName(it) == file.name }
        }
    }
}
