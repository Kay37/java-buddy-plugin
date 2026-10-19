package dev.javabuddy.concepts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import dev.javabuddy.JavaBuddySettings
import dev.javabuddy.errors.ErrorExplainer
import dev.javabuddy.ui.BuddyColors
import dev.javabuddy.ui.Card
import dev.javabuddy.ui.fitToWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

/** The card for one concept, as shown in the hover popup and at the top of the Concepts tab. */
fun conceptCard(concept: Concept, openInPanel: (() -> Unit)? = null): Card =
    Card(BuddyColors.CONCEPT_ACCENT, BuddyColors.CONCEPT_BACKGROUND).apply {
        chip(concept.topic.title)
        title(concept.title)
        section("WHAT IT MEANS", concept.meaning)
        example(concept.example.trimIndent())
        concept.tip?.let { section("GOOD TO KNOW", it) }
        openInPanel?.let { links("Open in Java Buddy panel" to it) }
    }

/**
 * Concept Cards: a popup when the mouse rests on a Java word, and [current], the card the Concepts
 * tab shows (the word under the text cursor, the last one hovered, or one picked from the list).
 */
@Service(Service.Level.PROJECT)
class ConceptCards(private val project: Project, private val cs: CoroutineScope) : Disposable {
    private val _current = MutableStateFlow<Concept?>(null)
    val current: StateFlow<Concept?> = _current.asStateFlow()

    private var started = false
    private var pending: Job? = null
    private var pendingKey: Pair<Editor, Int>? = null
    private var popup: JBPopup? = null
    /** The word under the mouse, and one the user clicked (no popup for it until the mouse moves to another word). */
    private var hoverKey: Pair<Editor, Int>? = null
    private var dismissedKey: Pair<Editor, Int>? = null
    /** On screen: the hovered word plus the popup below it. The popup stays open while the mouse is in here. */
    private var safeZone: Rectangle? = null

    fun start() {
        if (started) return
        started = true
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addEditorMouseMotionListener(object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) = onMouseMoved(e)
        }, this)
        multicaster.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseExited(e: EditorMouseEvent) = cancelPending()
            override fun mousePressed(e: EditorMouseEvent) {
                if (e.editor.project != project) return
                dismissedKey = hoverKey
                cancelPending()
                hidePopup()
            }
        }, this)
        multicaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = onCaretMoved(event.editor)
        }, this)
        multicaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                cancelPending()
                hidePopup()
            }
        }, this)
    }

    /** Shows [concept] at the top of the Concepts tab. */
    fun select(concept: Concept) {
        _current.value = concept
    }

    fun openInPanel(concept: Concept) {
        select(concept)
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ErrorExplainer.TOOL_WINDOW_ID) ?: return
        toolWindow.show {
            toolWindow.contentManager.findContent(TAB_NAME)?.let { toolWindow.contentManager.setSelectedContent(it) }
        }
    }

    private fun onMouseMoved(e: EditorMouseEvent) {
        val editor = e.editor
        if (editor.project != project) return
        if (popup?.isVisible == true && safeZone?.contains(RelativePoint(e.mouseEvent).screenPoint) == true) return
        hidePopup()
        if (!JavaBuddySettings.conceptHover || editor.editorKind != EditorKind.MAIN_EDITOR) return cancelPending()

        val hit = if (e.isOverText) hitAt(editor, e.offset)?.takeIf { it.hoverable } else null
        val key = hit?.let { editor to it.range.startOffset }
        hoverKey = key
        if (key != dismissedKey) dismissedKey = null
        if (hit == null || key == dismissedKey) return cancelPending()
        if (key == pendingKey && pending?.isActive == true) return
        cancelPending()
        pendingKey = key
        pending = cs.launch(Dispatchers.EDT) {
            delay(HOVER_DELAY_MS)
            if (!editor.isDisposed) showPopup(editor, hit)
        }
    }

    private fun onCaretMoved(editor: Editor) {
        if (editor.project != project || editor.editorKind != EditorKind.MAIN_EDITOR) return
        val offset = editor.caretModel.offset
        // The cursor is often just after a word ("static|"), so look one to the left too.
        val hit = hitAt(editor, offset) ?: hitAt(editor, offset - 1) ?: return
        _current.value = hit.concept
    }

    private fun hitAt(editor: Editor, offset: Int): ConceptHit? {
        val documents = PsiDocumentManager.getInstance(project)
        if (!documents.isCommitted(editor.document)) return null
        return runReadAction {
            documents.getPsiFile(editor.document)?.let { conceptAt(it, offset) }
        }
    }

    private fun showPopup(editor: Editor, hit: ConceptHit) {
        hidePopup()
        val content = JPanel(BorderLayout()).apply {
            background = BuddyColors.CONCEPT_BACKGROUND
            add(conceptCard(hit.concept) { hidePopup(); openInPanel(hit.concept) }, BorderLayout.CENTER)
        }
        fitToWidth(content, JBUI.scale(POPUP_WIDTH))

        val start = editor.offsetToXY(hit.range.startOffset)
        val end = editor.offsetToXY(hit.range.endOffset)
        val below = Point(start.x, start.y + editor.lineHeight)
        val word = RelativePoint(editor.contentComponent, start).screenPoint
        val popupTopLeft = RelativePoint(editor.contentComponent, below).screenPoint
        safeZone = Rectangle(word.x, word.y, maxOf(end.x - start.x, 1), editor.lineHeight)
            .union(Rectangle(popupTopLeft, content.preferredSize))
            .apply { grow(JBUI.scale(4), JBUI.scale(4)) }

        val newPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null)
            .setRequestFocus(false)
            .setFocusable(false)
            .setResizable(false)
            .setMovable(false)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setCancelKeyEnabled(true)
            .setCancelOnMouseOutCallback { event -> safeZone?.contains(RelativePoint(event).screenPoint) != true }
            .createPopup()
        popup = newPopup
        newPopup.show(RelativePoint(editor.contentComponent, below))
        _current.value = hit.concept
    }

    private fun hidePopup() {
        popup?.cancel()
        popup = null
        safeZone = null
    }

    private fun cancelPending() {
        pending?.cancel()
        pending = null
        pendingKey = null
    }

    override fun dispose() {
        cancelPending()
        hidePopup()
    }

    companion object {
        const val TAB_NAME = "Concepts"
        private const val HOVER_DELAY_MS = 500L
        private const val POPUP_WIDTH = 440
    }
}

/** Keeps a JComponent at its preferred height inside a vertical BoxLayout. */
internal fun <T : JComponent> T.fixedHeight(): T = apply {
    maximumSize = java.awt.Dimension(Int.MAX_VALUE, preferredSize.height)
}
