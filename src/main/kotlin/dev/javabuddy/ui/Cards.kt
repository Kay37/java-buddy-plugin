package dev.javabuddy.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextPane
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/** Colours shared by the Java Buddy panels: each kind of card has a soft background and a strong accent. */
object BuddyColors {
    val HELP_ACCENT = JBColor(Color(0xF0B429), Color(0xC99A2E))
    val HELP_BACKGROUND = JBColor(Color(0xFFF8E6), Color(0x332C1E))
    val CRASH_ACCENT = JBColor(Color(0xD64545), Color(0xE06C6C))
    val CRASH_BACKGROUND = JBColor(Color(0xFDECEC), Color(0x3A2626))
    val GOOD_ACCENT = JBColor(Color(0x2E9E4F), Color(0x5FB865))
    val GOOD_BACKGROUND = JBColor(Color(0xEAF7EE), Color(0x223326))
    val CONCEPT_ACCENT = JBColor(Color(0x3D7BE0), Color(0x5C9CF0))
    val CONCEPT_BACKGROUND = JBColor(Color(0xEEF4FD), Color(0x232B38))
    val NEUTRAL_ACCENT = JBColor(Color(0x9AA0A6), Color(0x6E7378))
    val NEUTRAL_BACKGROUND = JBColor(Color(0xF3F4F6), Color(0x2B2D30))
    val WRONG = JBColor(Color(0xC62828), Color(0xEF7070))
    val RIGHT = JBColor(Color(0x2E7D32), Color(0x6CC070))
    val CODE_BACKGROUND = JBColor(Color(0xFFFFFF), Color(0x1E1F22))
    val PROGRESS_ACCENT = JBColor(Color(0x7E57C2), Color(0x9D7FE0))
    val PROGRESS_BACKGROUND = JBColor(Color(0xF4EFFC), Color(0x2C2738))
    val TRACK = JBColor(Color(0xE3E5E8), Color(0x3C3F44))
}

/** A vertical list that always fits the panel's width, so wrapped text reflows as the panel resizes. */
class CardList : JPanel(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(8, 10)
    }

    fun addCard(card: JComponent) {
        if (componentCount > 0) add(Box.createVerticalStrut(JBUI.scale(8)))
        card.alignmentX = Component.LEFT_ALIGNMENT
        add(card)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}

/** A rounded card with a coloured stripe down its left edge. Add content with the helper methods, top to bottom. */
class Card(private val accent: Color, private val background: Color) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(10, 16, 10, 12)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(12)
            g2.color = background
            g2.fillRoundRect(0, 0, width, height, arc, arc)
            g2.color = accent
            g2.fillRoundRect(0, 0, JBUI.scale(5), height, JBUI.scale(5), JBUI.scale(5))
        }
        finally {
            g2.dispose()
        }
    }

    /** A small coloured pill above the title, like "LINE 12" or "CRASH". */
    fun chip(text: String, color: Color = accent) = row(object : JLabel(text.uppercase()) {
        init {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            foreground = Color.WHITE
            border = JBUI.Borders.empty(1, 7)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.fillRoundRect(0, 0, width, height, height, height)
            g2.dispose()
            super.paintComponent(g)
        }
    }, gapAfter = 6)

    fun title(text: String) = row(wrapping(text, JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.Fonts.label().size2D + 2f)), gapAfter = 6)

    /** A bold small heading ("What it means") followed by wrapped text. */
    fun section(heading: String, text: String) {
        row(JLabel(heading).apply { font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD); foreground = accent.darker() }, gapAfter = 2)
        row(wrapping(text, JBUI.Fonts.label()), gapAfter = 8)
    }

    fun text(text: String, muted: Boolean = false) =
        row(wrapping(text, if (muted) JBUI.Fonts.smallFont() else JBUI.Fonts.label()).apply {
            if (muted) foreground = UIUtil.getContextHelpForeground()
        }, gapAfter = 6)

    /** A code box: lines starting with ✗ are red (the mistake), ✓ green (the fix). */
    fun example(code: String) {
        val pane = JTextPane().apply {
            isEditable = false
            background = BuddyColors.CODE_BACKGROUND
            border = JBUI.Borders.compound(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(6, 8),
            )
            font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
        }
        val document = pane.styledDocument
        code.lines().forEachIndexed { index, line ->
            val style = SimpleAttributeSet().apply {
                StyleConstants.setForeground(this, when {
                    line.startsWith("✗") -> BuddyColors.WRONG
                    line.startsWith("✓") -> BuddyColors.RIGHT
                    else -> UIUtil.getLabelForeground()
                })
                if (line.startsWith("✗") || line.startsWith("✓")) StyleConstants.setBold(this, true)
            }
            document.insertString(document.length, (if (index > 0) "\n" else "") + line, style)
        }
        row(pane, gapAfter = 8)
    }

    fun links(vararg links: Pair<String, () -> Unit>) {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
        links.forEachIndexed { index, (text, onClick) ->
            if (index > 0) panel.add(Box.createHorizontalStrut(JBUI.scale(14)))
            panel.add(ActionLink(text) { onClick() })
        }
        row(panel, gapAfter = 0)
    }

    /** One line in a list: a link, then a short muted description ("static  belongs to the class"). */
    fun linkLine(text: String, detail: String, onClick: () -> Unit) {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
        panel.add(ActionLink(text) { onClick() })
        panel.add(JLabel("  $detail").apply { foreground = UIUtil.getContextHelpForeground() })
        row(panel, gapAfter = 3)
    }

    /** Any other component, full width, like a progress bar or a picture. */
    fun custom(component: JComponent, gapAfter: Int = 8) = row(component, gapAfter)

    /** A rounded bar filled to [done] out of [total]. */
    fun progressBar(done: Int, total: Int) = custom(object : JComponent() {
        override fun getPreferredSize() = Dimension(JBUI.scale(100), JBUI.scale(8))

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = BuddyColors.TRACK
                g2.fillRoundRect(0, 0, width, height, height, height)
                val filled = if (total <= 0) 0 else (width * done / total).coerceAtLeast(if (done > 0) height else 0)
                g2.color = accent
                g2.fillRoundRect(0, 0, filled, height, height, height)
            }
            finally {
                g2.dispose()
            }
        }
    })

    private fun row(component: JComponent, gapAfter: Int) {
        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(component, if (component is JLabel) BorderLayout.WEST else BorderLayout.CENTER)
        }
        add(wrapper)
        if (gapAfter > 0) add(Box.createVerticalStrut(JBUI.scale(gapAfter)))
    }

    private fun wrapping(text: String, font: Font) = JTextArea(text).apply {
        this.font = font
        lineWrap = true
        wrapStyleWord = true
        isEditable = false
        isOpaque = false
        isFocusable = false
        border = null
        foreground = UIUtil.getLabelForeground()
    }
}

/** A big friendly line for empty states, e.g. "No errors in Main.java". */
fun emptyState(title: String, detail: String, good: Boolean): JComponent =
    Card(if (good) BuddyColors.GOOD_ACCENT else BuddyColors.NEUTRAL_ACCENT, if (good) BuddyColors.GOOD_BACKGROUND else BuddyColors.NEUTRAL_BACKGROUND).apply {
        title(title)
        text(detail, muted = true)
    }

/**
 * Gives [component] a fixed [width] and the height its wrapped text needs at that width. For popups,
 * which size themselves from the preferred size before any layout has happened.
 */
fun fitToWidth(component: JComponent, width: Int): JComponent {
    var height = component.preferredSize.height
    repeat(3) {
        component.setSize(width, height.coerceAtLeast(1))
        layoutTree(component)
        height = component.preferredSize.height
    }
    component.preferredSize = Dimension(width, height)
    return component
}

private fun layoutTree(component: Component) {
    if (component !is java.awt.Container) return
    component.invalidate()
    component.doLayout()
    component.components.forEach(::layoutTree)
}

fun heading(text: String): JComponent = JLabel(text).apply {
    font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.Fonts.label().size2D + 1f)
    horizontalAlignment = SwingConstants.LEFT
    border = JBUI.Borders.empty(4, 2, 0, 0)
}
