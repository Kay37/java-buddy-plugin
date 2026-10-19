package dev.javabuddy.errors

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

/**
 * The plain-English bubble drawn under a line with an error: a lightbulb, the explanation's
 * title in bold, and how to fix it on a second line. Clicking it opens the Java Buddy panel.
 */
class ErrorBubbleRenderer(val error: ExplainedError) : EditorCustomElementRenderer {
    private val explanation = error.explanation!!

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val editor = inlay.editor
        val fonts = Fonts(editor)
        val content = maxOf(fonts.title.stringWidth(explanation.title), fonts.body.stringWidth(fixLine()))
        return (indentX(editor) + bubbleWidth(content)).coerceAtMost(maxWidth(editor))
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val fonts = Fonts(inlay.editor)
        return GAP_ABOVE.get() + PAD_Y.get() * 2 + fonts.title.height + LINE_GAP.get() + fonts.body.height + GAP_BELOW.get()
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val editor = inlay.editor
        val fonts = Fonts(editor)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val x = targetRegion.x + indentX(editor)
            val y = targetRegion.y + GAP_ABOVE.get()
            val available = (targetRegion.width - indentX(editor)).coerceAtLeast(0)
            val textRoom = available - bubbleWidth(0)
            val title = ellipsize(explanation.title, fonts.title, textRoom)
            val fix = ellipsize(fixLine(), fonts.body, textRoom)
            val width = bubbleWidth(maxOf(fonts.title.stringWidth(title), fonts.body.stringWidth(fix))).coerceAtMost(available)
            val height = targetRegion.height - GAP_ABOVE.get() - GAP_BELOW.get()
            val arc = JBUI.scale(10)

            g2.color = BACKGROUND
            g2.fillRoundRect(x, y, width, height, arc, arc)
            g2.color = BORDER
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc)
            // A solid accent down the left edge, so the bubble reads as "help" at a glance.
            g2.fillRoundRect(x, y, JBUI.scale(4), height, JBUI.scale(4), JBUI.scale(4))

            val icon = AllIcons.Actions.IntentionBulb
            val iconX = x + PAD_X.get()
            icon.paintIcon(editor.contentComponent, g2, iconX, y + PAD_Y.get())

            val textX = iconX + icon.iconWidth + ICON_GAP.get()
            var baseline = y + PAD_Y.get() + fonts.title.ascent
            g2.font = fonts.title.font
            g2.color = TITLE
            g2.drawString(title, textX, baseline)

            baseline += fonts.title.descent + LINE_GAP.get() + fonts.body.ascent
            g2.font = fonts.body.font
            g2.color = BODY
            g2.drawString(fix, textX, baseline)
        }
        finally {
            g2.dispose()
        }
    }

    private fun fixLine() = "How to fix: ${explanation.fix}   (click for more)"

    /** Lines the bubble up with the code's first character on the error line. */
    private fun indentX(editor: Editor): Int {
        val document = editor.document
        if (error.line >= document.lineCount) return 0
        val start = document.getLineStartOffset(error.line)
        val text = document.charsSequence.subSequence(start, document.getLineEndOffset(error.line))
        val indent = text.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
        return editor.offsetToXY(start + indent).x - editor.offsetToXY(start).x + JBUI.scale(2)
    }

    private fun maxWidth(editor: Editor) = editor.scrollingModel.visibleArea.width.takeIf { it > 0 } ?: Int.MAX_VALUE

    private fun bubbleWidth(content: Int) =
        PAD_X.get() + AllIcons.Actions.IntentionBulb.iconWidth + ICON_GAP.get() + content + PAD_X.get()

    private fun ellipsize(text: String, metrics: FontMetrics, room: Int): String {
        if (metrics.stringWidth(text) <= room) return text
        var end = text.length
        while (end > 0 && metrics.stringWidth(text.take(end) + "…") > room) end--
        return text.take(end).trimEnd() + "…"
    }

    private class Fonts(editor: Editor) {
        private val base = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        private val ui = UIUtil.getLabelFont()
        private val size = base.size2D
        val title: FontMetrics = editor.contentComponent.getFontMetrics(ui.deriveFont(Font.BOLD, size))
        val body: FontMetrics = editor.contentComponent.getFontMetrics(ui.deriveFont(Font.PLAIN, size * 0.92f))
    }

    private companion object {
        val BACKGROUND = JBColor(Color(0xFFF6DB), Color(0x3B3322))
        val BORDER = JBColor(Color(0xF0B429), Color(0xC99A2E))
        val TITLE = JBColor(Color(0x5C4300), Color(0xF5D78E))
        val BODY = JBColor(Color(0x6B5A2E), Color(0xD9C9A0))

        val GAP_ABOVE = JBUI.uiIntValue("JavaBuddy.Bubble.gapAbove", 2)
        val GAP_BELOW = JBUI.uiIntValue("JavaBuddy.Bubble.gapBelow", 4)
        val PAD_X = JBUI.uiIntValue("JavaBuddy.Bubble.padX", 10)
        val PAD_Y = JBUI.uiIntValue("JavaBuddy.Bubble.padY", 5)
        val LINE_GAP = JBUI.uiIntValue("JavaBuddy.Bubble.lineGap", 2)
        val ICON_GAP = JBUI.uiIntValue("JavaBuddy.Bubble.iconGap", 6)
    }
}
