package dev.javabuddy.coach

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.javabuddy.JavaBuddyScope
import dev.javacoach.engine.style.StyleChecker
import dev.javacoach.engine.style.StyleReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Style Coach's notes on one file. */
data class CoachNotes(val file: VirtualFile, val report: StyleReport)

/**
 * Runs the Java Coach engine's Style Coach on the Java file being edited: when another file is
 * picked, and shortly after typing stops. It only reads the code; it never changes it.
 */
@Service(Service.Level.PROJECT)
class StyleCoach(private val project: Project) : Disposable {
    private val _notes = MutableStateFlow<CoachNotes?>(null)
    val notes: StateFlow<CoachNotes?> = _notes.asStateFlow()
    private var started = false
    private var pending: Job? = null

    /** Starts following the editor. Safe to call more than once. Call on the EDT. */
    fun start() {
        if (started) return
        started = true
        project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) = refresh(0)
        })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (file == selectedJavaFile()) refresh(TYPING_PAUSE_MS)
            }
        }, this)
        refresh(0)
    }

    private fun refresh(delayMs: Long) {
        pending?.cancel()
        pending = project.service<JavaBuddyScope>().cs.launch {
            delay(delayMs)
            val file = withContext(Dispatchers.EDT) { selectedJavaFile() }
            if (file == null) {
                _notes.value = null
                return@launch
            }
            val code = readAction { FileDocumentManager.getInstance().getDocument(file)?.text } ?: return@launch
            try {
                _notes.value = CoachNotes(file, withContext(Dispatchers.Default) { StyleChecker().check(code) })
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Exception) {
                LOG.warn("The Style Coach failed on ${file.name}", e)
            }
        }
    }

    fun navigate(file: VirtualFile, line: Int) {
        OpenFileDescriptor(project, file, (line - 1).coerceAtLeast(0), 0).navigate(true)
    }

    private fun selectedJavaFile(): VirtualFile? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.takeIf { it.isValid && it.extension == "java" }

    override fun dispose() {
        pending?.cancel()
    }

    companion object {
        const val TAB_NAME = "Coach"
        private const val TYPING_PAUSE_MS = 800L
        private val LOG = logger<StyleCoach>()
    }
}
