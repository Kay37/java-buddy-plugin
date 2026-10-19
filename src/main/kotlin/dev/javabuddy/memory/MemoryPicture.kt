package dev.javabuddy.memory

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import dev.javabuddy.ui.BuddyColors
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.CubicCurve2D
import java.awt.geom.Path2D
import javax.swing.JComponent

/**
 * Draws a [MemorySnapshot] as boxes and arrows: the paused method's variables on the left, the
 * objects they point to in columns to the right (one column per step away). Values that changed
 * since the last pause are highlighted.
 */
class MemoryPicture : JComponent() {
    private var snapshot: MemorySnapshot? = null
    private var changed: Set<String> = emptySet()
    private var placement: Layout? = null

    fun show(snapshot: MemorySnapshot?, changed: Set<String>) {
        this.snapshot = snapshot
        this.changed = changed
        placement = null
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension = arrange().size

    /** Where one box goes, and where each of its rows' value cells is (for arrows and highlights). */
    private class Placed(val key: String, val title: String, val kind: BoxKind?, val rows: List<Row>, val hidden: Int, val bounds: Rectangle, val nameWidth: Int, val rowKeyPrefix: String)

    private class Layout(val boxes: List<Placed>, val byId: Map<Long, Placed>, val size: Dimension)

    private fun arrange(): Layout {
        placement?.let { return it }
        val snap = snapshot ?: return Layout(emptyList(), emptyMap(), Dimension(0, 0)).also { placement = it }
        val metrics = getFontMetrics(font())
        val pad = JBUI.scale(16)
        val gapX = JBUI.scale(56)
        val gapY = JBUI.scale(14)

        val frame = place("frame", "Variables in ${snap.method}()", null, snap.variables, 0, pad, pad, metrics, "var:")

        // Columns: objects the variables point at, then objects those point at, and so on.
        val columns = mutableListOf<List<HeapBox>>()
        val placedIds = mutableSetOf<Long>()
        var frontier = snap.variables.mapNotNull { (it.slot as? Slot.Ref)?.id }.distinct().mapNotNull { snap.boxes[it] }
        while (frontier.isNotEmpty()) {
            val column = frontier.filter { placedIds.add(it.id) }
            if (column.isEmpty()) break
            columns += column
            frontier = column.flatMap { box -> box.rows.mapNotNull { (it.slot as? Slot.Ref)?.id } }.distinct().mapNotNull { snap.boxes[it] }
        }
        // Anything not reachable in that order (shouldn't happen) goes in a last column.
        snap.boxes.values.filter { it.id !in placedIds }.takeIf { it.isNotEmpty() }?.let { columns += it }

        val placed = mutableListOf(frame)
        val byId = mutableMapOf<Long, Placed>()
        var x = frame.bounds.x + frame.bounds.width + gapX
        var bottom = frame.bounds.y + frame.bounds.height
        for (column in columns) {
            var y = pad
            var widest = 0
            for (box in column) {
                val p = place("obj:${box.id}", box.title, box.kind, box.rows, box.hidden, x, y, metrics, "obj:${box.id}:")
                placed += p
                byId[box.id] = p
                y += p.bounds.height + gapY
                widest = maxOf(widest, p.bounds.width)
            }
            bottom = maxOf(bottom, y)
            x += widest + gapX
        }
        // Extra room on the right for arrows that loop back into a box from its right side.
        return Layout(placed, byId, Dimension(x - gapX + pad + JBUI.scale(48), bottom + pad)).also { placement = it }
    }

    private fun place(key: String, title: String, kind: BoxKind?, rows: List<Row>, hidden: Int, x: Int, y: Int, metrics: FontMetrics, rowKeyPrefix: String): Placed {
        val cellPad = JBUI.scale(8)
        val nameWidth = (rows.maxOfOrNull { metrics.stringWidth(it.name) } ?: 0) + cellPad * 2
        val valueWidth = maxOf(JBUI.scale(44), (rows.maxOfOrNull { metrics.stringWidth(text(it.slot)) } ?: 0) + cellPad * 2)
        val titleWidth = metrics.stringWidth(title) + cellPad * 2
        val width = maxOf(titleWidth, nameWidth + valueWidth, JBUI.scale(120)).coerceAtMost(JBUI.scale(380))
        val extraRows = (if (hidden > 0) 1 else 0) + (if (rows.isEmpty()) 1 else 0)
        val height = headerHeight() + (rows.size + extraRows) * rowHeight()
        return Placed(key, title, kind, rows, hidden, Rectangle(x, y, width, height), nameWidth, rowKeyPrefix)
    }

    private fun font(): Font = JBUI.Fonts.label()
    private fun rowHeight() = JBUI.scale(22)
    private fun headerHeight() = JBUI.scale(26)

    private fun text(slot: Slot) = when (slot) {
        is Slot.Plain -> slot.text
        is Slot.Ref -> ""
        Slot.Null -> "null"
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.font = font()
            val layout = arrange()
            layout.boxes.forEach { paintBox(g2, it) }
            // Arrows on top, so they're never hidden behind a box.
            layout.boxes.forEach { box -> box.rows.forEachIndexed { index, row -> (row.slot as? Slot.Ref)?.let { ref -> layout.byId[ref.id]?.let { paintArrow(g2, dot(box, index), it) } } } }
        }
        finally {
            g2.dispose()
        }
    }

    private fun paintBox(g: Graphics2D, box: Placed) {
        val r = box.bounds
        val arc = JBUI.scale(10)
        val accent = accentFor(box.kind)
        g.color = BOX_BACKGROUND
        g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc)
        // Header: a tinted strip with the title.
        val header = Rectangle(r.x, r.y, r.width, headerHeight())
        g.color = tint(accent)
        g.fillRoundRect(header.x, header.y, header.width, header.height + arc, arc, arc)
        g.color = BOX_BACKGROUND
        g.fillRect(header.x, header.y + header.height, header.width, arc)
        g.color = accent
        g.font = font().deriveFont(Font.BOLD)
        drawText(g, box.title, header.x + JBUI.scale(8), header)
        g.font = font()

        val metrics = g.fontMetrics
        var y = r.y + headerHeight()
        box.rows.forEachIndexed { index, row ->
            val rowRect = Rectangle(r.x, y, r.width, rowHeight())
            val valueRect = Rectangle(r.x + box.nameWidth, y, r.width - box.nameWidth, rowHeight())
            if ("${box.rowKeyPrefix}${row.name}" in changed) {
                g.color = BuddyColors.HELP_BACKGROUND
                g.fillRect(valueRect.x, valueRect.y, valueRect.width, valueRect.height)
                g.color = BuddyColors.HELP_ACCENT
                g.fillRect(valueRect.x, valueRect.y, JBUI.scale(3), valueRect.height)
            }
            g.color = JBColor.border()
            g.drawLine(r.x, y, r.x + r.width, y)
            g.drawLine(valueRect.x, y, valueRect.x, y + rowHeight())
            g.color = NAME_TEXT
            drawText(g, row.name, r.x + JBUI.scale(8), rowRect)
            when (val slot = row.slot) {
                is Slot.Ref -> {
                    val dot = dot(box, index)
                    val size = JBUI.scale(8)
                    g.color = accent
                    g.fillOval(dot.x - size / 2, dot.y - size / 2, size, size)
                }
                Slot.Null -> {
                    g.color = NAME_TEXT
                    drawText(g, "null", valueRect.x + JBUI.scale(8), valueRect)
                }
                is Slot.Plain -> {
                    g.color = VALUE_TEXT
                    val text = clip(slot.text, valueRect.width - JBUI.scale(12), metrics)
                    drawText(g, text, valueRect.x + JBUI.scale(8), valueRect)
                }
            }
            y += rowHeight()
        }
        val note = when {
            box.hidden > 0 -> "…and ${box.hidden} more"
            box.rows.isEmpty() && box.kind == BoxKind.LIBRARY -> "Java's own object"
            box.rows.isEmpty() && box.kind == BoxKind.ARRAY -> "empty"
            box.rows.isEmpty() && box.kind == BoxKind.LIST -> "empty"
            box.rows.isEmpty() && box.kind == BoxKind.MAP -> "empty"
            box.rows.isEmpty() -> if (box.kind == null) "no variables yet" else "no fields"
            else -> null
        }
        if (note != null) {
            g.color = JBColor.border()
            g.drawLine(r.x, y, r.x + r.width, y)
            g.color = NAME_TEXT
            g.font = JBUI.Fonts.smallFont().deriveFont(Font.ITALIC)
            drawText(g, note, r.x + JBUI.scale(8), Rectangle(r.x, y, r.width, rowHeight()))
            g.font = font()
        }
        g.color = accent
        g.stroke = BasicStroke(JBUI.scale(1).toFloat() + 0.5f)
        g.drawRoundRect(r.x, r.y, r.width, r.height, arc, arc)
        g.stroke = BasicStroke(1f)
    }

    private fun dot(box: Placed, row: Int): Point =
        Point(box.bounds.x + box.nameWidth + JBUI.scale(16), box.bounds.y + headerHeight() + row * rowHeight() + rowHeight() / 2)

    private fun paintArrow(g: Graphics2D, from: Point, target: Placed) {
        // A box further right is entered from its left side. A box in the same column or further
        // left (two objects pointing at each other) is entered from its right side, so the arrow
        // loops out to the right instead of cutting back across the boxes.
        val forwards = target.bounds.x > from.x
        val to = if (forwards) Point(target.bounds.x, target.bounds.y + headerHeight() / 2)
                 else Point(target.bounds.x + target.bounds.width, target.bounds.y + headerHeight() / 2)
        g.color = accentFor(target.kind)
        g.stroke = BasicStroke(JBUI.scale(2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val reach = if (forwards) maxOf(JBUI.scale(30), (to.x - from.x) / 2) else JBUI.scale(36) + kotlin.math.max(0, to.x - from.x)
        val end = if (forwards) to.x - JBUI.scale(2) else to.x + JBUI.scale(2)
        val curve = CubicCurve2D.Double(
            from.x.toDouble(), from.y.toDouble(),
            (from.x + reach).toDouble(), from.y.toDouble(),
            (if (forwards) to.x - reach else to.x + reach).toDouble(), to.y.toDouble(),
            end.toDouble(), to.y.toDouble(),
        )
        g.draw(curve)
        val size = JBUI.scale(7).toDouble()
        val back = if (forwards) -size * 1.4 else size * 1.4
        val head = Path2D.Double().apply {
            moveTo(to.x.toDouble(), to.y.toDouble())
            lineTo(to.x + back, to.y - size)
            lineTo(to.x + back, to.y + size)
            closePath()
        }
        g.fill(head)
        g.stroke = BasicStroke(1f)
    }

    private fun drawText(g: Graphics2D, text: String, x: Int, row: Rectangle) {
        val metrics = g.fontMetrics
        g.drawString(text, x, row.y + (row.height - metrics.height) / 2 + metrics.ascent)
    }

    private fun clip(text: String, width: Int, metrics: FontMetrics): String {
        if (metrics.stringWidth(text) <= width) return text
        var cut = text
        while (cut.isNotEmpty() && metrics.stringWidth("$cut…") > width) cut = cut.dropLast(1)
        return "$cut…"
    }

    private fun accentFor(kind: BoxKind?): Color = when (kind) {
        null -> BuddyColors.CONCEPT_ACCENT
        BoxKind.ARRAY, BoxKind.LIST, BoxKind.MAP -> BuddyColors.PROGRESS_ACCENT
        BoxKind.LIBRARY -> BuddyColors.NEUTRAL_ACCENT
        BoxKind.OBJECT -> BuddyColors.GOOD_ACCENT
    }

    private fun tint(color: Color): Color = when (color) {
        BuddyColors.CONCEPT_ACCENT -> BuddyColors.CONCEPT_BACKGROUND
        BuddyColors.PROGRESS_ACCENT -> BuddyColors.PROGRESS_BACKGROUND
        BuddyColors.GOOD_ACCENT -> BuddyColors.GOOD_BACKGROUND
        else -> BuddyColors.NEUTRAL_BACKGROUND
    }

    private companion object {
        val BOX_BACKGROUND = BuddyColors.CODE_BACKGROUND
        val VALUE_TEXT = JBColor(Color(0x1F2328), Color(0xDFE1E5))
        val NAME_TEXT = JBColor(Color(0x6C707E), Color(0x9DA0A8))
    }
}
