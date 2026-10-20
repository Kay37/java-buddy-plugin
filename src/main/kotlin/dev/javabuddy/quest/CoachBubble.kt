package dev.javabuddy.quest

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.javabuddy.coach.plain
import dev.javacoach.engine.ai.CoachAI
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

/**
 * The AI coach's reply as a bubble inside the quest file, under the line it's about: "thinking…"
 * first, then the reply (or what to do first, like signing in). Clicking it closes it. [view] changes
 * when the reply arrives; call `inlay.update()` after.
 */
class CoachBubble(var view: CoachView) : EditorCustomElementRenderer {

    /** One drawn line: words, or a line of code (in the editor's font). */
    private data class Line(val text: String, val code: Boolean = false, val muted: Boolean = false)

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = LEFT.get() + bubbleWidth(inlay.editor)

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val fonts = Fonts(inlay.editor)
        return GAP_ABOVE.get() + PAD_Y.get() * 2 + fonts.title.height + LINE_GAP.get() +
            lines(inlay.editor, fonts).sumOf { heightOf(it, fonts) } + GAP_BELOW.get()
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val editor = inlay.editor
        val fonts = Fonts(editor)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val x = targetRegion.x + LEFT.get()
            val y = targetRegion.y + GAP_ABOVE.get()
            val width = bubbleWidth(editor)
            val height = targetRegion.height - GAP_ABOVE.get() - GAP_BELOW.get()
            val arc = JBUI.scale(10)

            g2.color = BACKGROUND
            g2.fillRoundRect(x, y, width, height, arc, arc)
            g2.color = ACCENT
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc)
            g2.fillRoundRect(x, y, JBUI.scale(4), height, JBUI.scale(4), JBUI.scale(4))

            val icon = AllIcons.Actions.IntentionBulb
            val iconX = x + PAD_X.get()
            icon.paintIcon(editor.contentComponent, g2, iconX, y + PAD_Y.get())
            val textX = iconX + icon.iconWidth + ICON_GAP.get()

            var top = y + PAD_Y.get()
            g2.font = fonts.title.font
            g2.color = TITLE
            g2.drawString(title(), textX, top + fonts.title.ascent)
            g2.font = fonts.small.font
            g2.color = MUTED
            g2.drawString(CLOSE, x + width - PAD_X.get() - fonts.small.stringWidth(CLOSE), top + fonts.title.ascent)
            top += fonts.title.height + LINE_GAP.get()

            for (line in lines(editor, fonts)) {
                val metrics = metricsOf(line, fonts)
                g2.font = metrics.font
                g2.color = if (line.muted) MUTED else BODY
                g2.drawString(line.text, textX, top + metrics.ascent)
                top += heightOf(line, fonts)
            }
        }
        finally {
            g2.dispose()
        }
    }

    private fun title() = when {
        view.text == null && view.problem == null -> "AI coach · thinking…"
        view.mode == CoachAI.Mode.PLAN -> "AI coach · your plan"
        else -> "AI coach"
    }

    /** The bubble's text, wrapped to fit its width. */
    private fun lines(editor: Editor, fonts: Fonts): List<Line> {
        val room = bubbleWidth(editor) - (PAD_X.get() * 2 + AllIcons.Actions.IntentionBulb.iconWidth + ICON_GAP.get())
        val out = mutableListOf<Line>()
        fun words(text: String, muted: Boolean = false) =
            text.lines().forEach { paragraph -> wrap(paragraph, if (muted) fonts.small else fonts.body, room).forEach { out += Line(it, muted = muted) } }
        when {
            view.text == null && view.problem == null -> words("Reading your code and your last check. This takes a few seconds.", muted = true)
            view.problem != null -> {
                words("The AI coach can't answer yet: ${view.problem}")
                words("The hints in the Java Buddy panel still work without it.", muted = true)
            }
            else -> {
                var inCode = false
                for (raw in view.text.orEmpty().trim().lines()) {
                    if (raw.trimStart().startsWith("```")) { inCode = !inCode; continue }
                    if (inCode) out += Line(raw.trimEnd(), code = true) else words(plain(raw))
                }
                if (view.filtered) words("Part of the reply was held back because it gave away too much. That's on purpose.", muted = true)
                words("The coach asks questions and points at ideas. It never writes your code.", muted = true)
            }
        }
        return out
    }

    private fun wrap(text: String, metrics: FontMetrics, room: Int): List<String> {
        if (text.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.trim().split(Regex("\\s+"))) {
            val tried = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && metrics.stringWidth(tried) > room) {
                lines += current
                current = word
            }
            else current = tried
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun metricsOf(line: Line, fonts: Fonts) = when {
        line.code -> fonts.code
        line.muted -> fonts.small
        else -> fonts.body
    }

    private fun heightOf(line: Line, fonts: Fonts) = metricsOf(line, fonts).height + if (line.text.isEmpty()) 0 else JBUI.scale(1)

    /** As wide as the visible editor allows, up to about 90 characters. */
    private fun bubbleWidth(editor: Editor): Int {
        val visible = editor.scrollingModel.visibleArea.width.takeIf { it > 0 } ?: JBUI.scale(700)
        val wanted = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN)).charWidth('m') * 90
        return minOf(wanted, visible - LEFT.get() - JBUI.scale(24)).coerceAtLeast(JBUI.scale(260))
    }

    private class Fonts(editor: Editor) {
        private val base = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        private val ui = UIUtil.getLabelFont()
        private val size = base.size2D
        val title: FontMetrics = editor.contentComponent.getFontMetrics(ui.deriveFont(Font.BOLD, size))
        val body: FontMetrics = editor.contentComponent.getFontMetrics(ui.deriveFont(Font.PLAIN, size))
        val small: FontMetrics = editor.contentComponent.getFontMetrics(ui.deriveFont(Font.PLAIN, size * 0.9f))
        val code: FontMetrics = editor.contentComponent.getFontMetrics(base)
    }

    private companion object {
        const val CLOSE = "click to close  ×"
        val BACKGROUND = JBColor(Color(0xEEF4FD), Color(0x232B38))
        val ACCENT = JBColor(Color(0x3D7BE0), Color(0x5C9CF0))
        val TITLE = JBColor(Color(0x1F4E9E), Color(0xA9C8F5))
        val BODY = JBColor(Color(0x22324A), Color(0xD5DEEA))
        val MUTED = JBColor(Color(0x5E6B7D), Color(0x96A3B5))

        val LEFT = JBUI.uiIntValue("JavaBuddy.CoachBubble.left", 4)
        val GAP_ABOVE = JBUI.uiIntValue("JavaBuddy.CoachBubble.gapAbove", 4)
        val GAP_BELOW = JBUI.uiIntValue("JavaBuddy.CoachBubble.gapBelow", 6)
        val PAD_X = JBUI.uiIntValue("JavaBuddy.CoachBubble.padX", 10)
        val PAD_Y = JBUI.uiIntValue("JavaBuddy.CoachBubble.padY", 7)
        val LINE_GAP = JBUI.uiIntValue("JavaBuddy.CoachBubble.lineGap", 4)
        val ICON_GAP = JBUI.uiIntValue("JavaBuddy.CoachBubble.iconGap", 6)
    }
}
