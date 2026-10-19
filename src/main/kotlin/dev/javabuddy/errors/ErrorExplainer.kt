package dev.javabuddy.errors

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import dev.javabuddy.JavaBuddySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One red error in the code, with its plain-English explanation (null when there's no rule for it yet). */
data class ExplainedError(
    val file: VirtualFile,
    /** 0-based line in the editor. */
    val line: Int,
    val offset: Int,
    val message: String,
    val explanation: Explanation?,
)

data class FileErrors(val file: VirtualFile, val errors: List<ExplainedError>)

/** A run that ended with an exception, and where in the learner's own code it happened. */
data class Crash(
    val exception: String,
    val explanation: Explanation,
    val file: VirtualFile?,
    /** 0-based line, or -1 when it's not in the project's code. */
    val line: Int,
    /** "Main.java:12", as Java printed it. */
    val location: String?,
)

/**
 * Explains errors in plain English: watches the focused Java editor's red errors (bubbles under
 * the lines plus cards in the Java Buddy panel) and keeps the latest crash reported by [CrashWatcher].
 */
@Service(Service.Level.PROJECT)
class ErrorExplainer(private val project: Project, private val cs: CoroutineScope) : Disposable {
    private val _fileErrors = MutableStateFlow<FileErrors?>(null)
    val fileErrors: StateFlow<FileErrors?> = _fileErrors.asStateFlow()

    private val _crash = MutableStateFlow<Crash?>(null)
    val crash: StateFlow<Crash?> = _crash.asStateFlow()

    /** Last edit per document, so a bubble doesn't pop up under a line while it's being typed. */
    private var lastEdit: Pair<Int, Long>? = null
    private var recheck: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        project.messageBus.connect(this).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
            override fun daemonFinished(fileEditors: Collection<FileEditor>) = refreshSelected()
        })
        project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) = refreshSelected()
        })
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                lastEdit = event.document.getLineNumber(event.offset.coerceAtMost(event.document.textLength)) to System.currentTimeMillis()
            }
        }, this)
        multicaster.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseClicked(e: EditorMouseEvent) {
                if (e.editor.project == project && e.inlay?.renderer is ErrorBubbleRenderer) {
                    e.consume()
                    showPanel()
                }
            }
        }, this)
        refreshSelected()
    }

    fun refreshSelected() {
        cs.launch(Dispatchers.EDT) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            EditorFactory.getInstance().allEditors.filter { it.project == project && it != editor }.forEach(::clearBubbles)
            if (editor == null) _fileErrors.value = null else refresh(editor)
        }
    }

    private fun refresh(editor: Editor) {
        if (editor.isDisposed || editor.editorKind != EditorKind.MAIN_EDITOR) return
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document)
        if (file == null || !file.name.endsWith(".java")) {
            clearBubbles(editor)
            _fileErrors.value = null
            return
        }

        val found = linkedMapOf<Pair<Int, String>, ExplainedError>()
        DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, 0, document.textLength) { info ->
            val message = info.description
            if (info.severity >= HighlightSeverity.ERROR && !message.isNullOrBlank()) {
                val offset = info.startOffset.coerceIn(0, document.textLength)
                val line = document.getLineNumber(offset)
                found.getOrPut(line to message) {
                    ExplainedError(file, line, offset, message, explainCompileError(message)?.let { withSuggestion(it, message, document) })
                }
            }
            true
        }
        val errors = found.values.sortedBy { it.offset }
        _fileErrors.value = FileErrors(file, errors)
        showBubbles(editor, errors)
    }

    /** For "Java doesn't know 'totl'": adds "Did you mean 'total'?" when a similar name exists in the file. */
    private fun withSuggestion(explanation: Explanation, message: String, document: com.intellij.openapi.editor.Document): Explanation {
        val unknown = Regex("""^Cannot resolve symbol '(.+?)'$""").find(message)?.groupValues?.get(1) ?: return explanation
        if (!explanation.title.startsWith("Java doesn't know")) return explanation
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return explanation
        val names = PsiTreeUtil.findChildrenOfType(psiFile, PsiNamedElement::class.java).mapNotNull { it.name }.toSet() + WELL_KNOWN_NAMES
        val best = names.filter { it != unknown && it.length >= 2 }
            .map { it to distance(it, unknown) }
            .filter { (name, d) -> d <= (if (unknown.length <= 4) 1 else 2) || name.equals(unknown, ignoreCase = true) }
            .minByOrNull { it.second }?.first
            ?: return explanation
        return explanation.copy(
            title = "Java doesn't know '$unknown'. Did you mean '$best'?",
            fix = "If you meant '$best', fix the spelling (capital letters count). " + explanation.fix,
            example = "✗ $unknown\n✓ $best",
        )
    }

    private fun showBubbles(editor: Editor, errors: List<ExplainedError>) {
        clearBubbles(editor)
        if (!JavaBuddySettings.errorBubbles) return
        val (editedLine, editedAt) = lastEdit ?: (-1 to 0L)
        val typingRecently = System.currentTimeMillis() - editedAt < TYPING_PAUSE_MS
        val shown = errors.filter { it.explanation != null && !(typingRecently && it.line == editedLine) }
            .distinctBy { it.line }
            .take(MAX_BUBBLES)
        if (typingRecently && errors.any { it.line == editedLine }) {
            // Come back once typing pauses, in case nothing else triggers a refresh.
            recheck?.cancel()
            recheck = cs.launch {
                delay(TYPING_PAUSE_MS)
                refreshSelected()
            }
        }
        val document = editor.document
        val inlays = shown.map { error ->
            editor.inlayModel.addBlockElement(document.getLineEndOffset(error.line), true, false, 0, ErrorBubbleRenderer(error))
        }.filterNotNull()
        editor.putUserData(BUBBLES, inlays)
    }

    private fun clearBubbles(editor: Editor) {
        editor.getUserData(BUBBLES)?.forEach(Disposer::dispose)
        editor.putUserData(BUBBLES, null)
    }

    fun reportCrash(crash: Crash?) {
        _crash.value = crash
    }

    fun showPanel() {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
    }

    fun navigate(file: VirtualFile, line: Int) {
        OpenFileDescriptor(project, file, line.coerceAtLeast(0), 0).navigate(true)
    }

    override fun dispose() {
        EditorFactory.getInstance().allEditors.filter { it.project == project }.forEach(::clearBubbles)
    }

    companion object {
        const val TOOL_WINDOW_ID = "Java Buddy"
        private val BUBBLES = Key.create<List<Inlay<*>>>("javaBuddy.errorBubbles")
        private const val MAX_BUBBLES = 5
        private const val TYPING_PAUSE_MS = 2000L

        /** Names beginners mistype that aren't declared in their own file. */
        private val WELL_KNOWN_NAMES = setOf(
            "String", "System", "Math", "Integer", "Double", "Boolean", "Character", "Scanner", "ArrayList", "List",
            "HashMap", "Map", "Arrays", "Random", "Object", "println", "print", "printf", "length", "main", "args",
        )

        /** Edit distance (insert, delete, change one letter = 1), case-sensitive. */
        fun distance(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val current = IntArray(b.length + 1)
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                }
                previous = current
            }
            return previous[b.length]
        }
    }
}
