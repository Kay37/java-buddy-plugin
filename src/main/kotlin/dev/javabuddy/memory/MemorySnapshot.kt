package dev.javabuddy.memory

import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.CharValue
import com.sun.jdi.ClassType
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveValue
import com.sun.jdi.StringReference
import com.sun.jdi.Value

/** What goes in one slot of a box: a plain value written inside it, an arrow to an object, or null. */
sealed class Slot {
    data class Plain(val text: String) : Slot()
    data class Ref(val id: Long) : Slot()
    data object Null : Slot()
}

/** A named slot: a local variable, a field, an array cell ("[2]") or a map entry. */
data class Row(val name: String, val type: String, val slot: Slot)

enum class BoxKind { OBJECT, ARRAY, LIST, MAP, LIBRARY }

/** One object in memory. [hidden] is how many cells or entries didn't fit. */
data class HeapBox(val id: Long, val kind: BoxKind, val title: String, val rows: List<Row>, val hidden: Int = 0)

/** Everything the Memory Picture draws for one paused moment. */
data class MemorySnapshot(
    val method: String,
    val location: String,
    val variables: List<Row>,
    val boxes: Map<Long, HeapBox>,
    val truncated: Boolean,
) {
    /** "var:x" / "obj:12:name" -> shown text, to spot what changed since the last step. */
    fun valueKeys(): Map<String, String> {
        val keys = linkedMapOf<String, String>()
        variables.forEach { keys["var:${it.name}"] = it.slot.key() }
        boxes.values.forEach { box -> box.rows.forEach { keys["obj:${box.id}:${it.name}"] = it.slot.key() } }
        return keys
    }

    private fun Slot.key() = when (this) {
        is Slot.Plain -> text
        is Slot.Ref -> "→$id"
        Slot.Null -> "null"
    }
}

/**
 * Reads a paused stack frame through JDI and turns it into a [MemorySnapshot]. Must run on the
 * debugger's manager thread. Strings, boxed numbers and enums are shown inline, like plain values,
 * because that's how beginners think of them; Java's own objects (Scanner, Random…) aren't opened up.
 */
class SnapshotBuilder(private val maxBoxes: Int = 30, private val maxDepth: Int = 4) {
    private val boxes = linkedMapOf<Long, HeapBox>()
    private val pending = ArrayDeque<Pair<ObjectReference, Int>>()
    private val seen = mutableSetOf<Long>()
    private var truncated = false

    fun build(frame: StackFrameProxyImpl): MemorySnapshot {
        val location = frame.location()
        val method = location.method().name()
        val where = runCatching { "${location.sourceName()}:${location.lineNumber()}" }.getOrDefault("line ${location.lineNumber()}")
        val variables = mutableListOf<Row>()
        frame.thisObject()?.let { variables += Row("this", simpleName(it.referenceType().name()), slot(it, 0)) }
        for (variable in frame.visibleVariables()) {
            variables += Row(variable.name(), simpleName(variable.typeName()), slot(frame.getValue(variable), 0))
        }
        while (pending.isNotEmpty()) {
            val (obj, depth) = pending.removeFirst()
            boxes[obj.uniqueID()] = box(obj, depth)
        }
        return MemorySnapshot(method, where, variables, boxes.toMap(), truncated)
    }

    private fun slot(value: Value?, depth: Int): Slot = when (value) {
        null -> Slot.Null
        is StringReference -> Slot.Plain(quote(value.value()))
        is CharValue -> Slot.Plain("'${value.value()}'")
        is PrimitiveValue -> Slot.Plain(value.toString())
        is ObjectReference -> objectSlot(value, depth)
        else -> Slot.Plain(value.toString())
    }

    private fun objectSlot(obj: ObjectReference, depth: Int): Slot {
        val type = obj.referenceType()
        val name = type.name()
        if (name in BOXED) return slot(type.fieldByName("value")?.let(obj::getValue), depth)
        if (type is ClassType && type.isEnum) {
            val constant = type.fieldByName("name")?.let(obj::getValue) as? StringReference
            return Slot.Plain("${simpleName(name)}.${constant?.value() ?: "?"}")
        }
        val id = obj.uniqueID()
        if (id !in seen) {
            if (seen.size >= maxBoxes || depth >= maxDepth) {
                truncated = true
                return Slot.Plain("…")
            }
            seen += id
            pending += obj to depth
        }
        return Slot.Ref(id)
    }

    private fun box(obj: ObjectReference, depth: Int): HeapBox {
        val id = obj.uniqueID()
        val name = obj.referenceType().name()
        return when {
            obj is ArrayReference -> {
                val length = obj.length()
                val shown = minOf(length, MAX_CELLS)
                val cells = if (shown == 0) emptyList() else obj.getValues(0, shown)
                val element = simpleName(name.removeSuffix("[]"))
                HeapBox(id, BoxKind.ARRAY, "$element[$length]", cells.mapIndexed { i, v -> Row("[$i]", element, slot(v, depth + 1)) }, length - shown)
            }
            name == "java.util.ArrayList" -> {
                val size = (intField(obj, "size") ?: 0)
                val data = obj.referenceType().fieldByName("elementData")?.let(obj::getValue) as? ArrayReference
                val shown = minOf(size, MAX_CELLS, data?.length() ?: 0)
                val rows = (0 until shown).map { i -> Row("[$i]", "", slot(data!!.getValue(i), depth + 1)) }
                HeapBox(id, BoxKind.LIST, "${simpleName(name)}  (size $size)", rows, size - shown)
            }
            name == "java.util.HashMap" || name == "java.util.LinkedHashMap" -> mapBox(obj, depth)
            LIBRARY_PREFIXES.any { name.startsWith(it) } ->
                HeapBox(id, BoxKind.LIBRARY, simpleName(name), emptyList())
            else -> {
                val fields = obj.referenceType().allFields().filter { !it.isStatic && !it.isSynthetic }
                HeapBox(id, BoxKind.OBJECT, simpleName(name), fields.map { Row(it.name(), simpleName(it.typeName()), slot(obj.getValue(it), depth + 1)) })
            }
        }
    }

    /** HashMap keeps its entries in a "table" of chained nodes; walk it in table order. */
    private fun mapBox(map: ObjectReference, depth: Int): HeapBox {
        val size = intField(map, "size") ?: 0
        val table = map.referenceType().fieldByName("table")?.let(map::getValue) as? ArrayReference
        val rows = mutableListOf<Row>()
        table?.values?.forEach { first ->
            var node = first as? ObjectReference
            while (node != null && rows.size < MAX_CELLS) {
                val type = node.referenceType()
                val key = slot(type.fieldByName("key")?.let(node::getValue), depth + 1)
                val value = slot(type.fieldByName("value")?.let(node::getValue), depth + 1)
                rows += Row(if (key is Slot.Plain) key.text else "key", "", value)
                node = type.fieldByName("next")?.let(node::getValue) as? ObjectReference
            }
        }
        return HeapBox(map.uniqueID(), BoxKind.MAP, "${simpleName(map.referenceType().name())}  (size $size)", rows, size - rows.size)
    }

    private fun intField(obj: ObjectReference, field: String): Int? =
        (obj.referenceType().fieldByName(field)?.let(obj::getValue) as? IntegerValue)?.value()

    private fun quote(text: String): String {
        val oneLine = text.replace("\n", "\\n")
        return "\"" + (if (oneLine.length > 40) oneLine.take(38) + "…" else oneLine) + "\""
    }

    companion object {
        private const val MAX_CELLS = 20
        private val BOXED = setOf(
            "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Float",
            "java.lang.Short", "java.lang.Byte", "java.lang.Character", "java.lang.Boolean",
        )
        private val LIBRARY_PREFIXES = listOf("java.", "javax.", "jdk.", "sun.", "com.sun.", "kotlin.")

        /** "java.util.Scanner" -> "Scanner", "playground.Dog$Leg" -> "Dog.Leg". */
        fun simpleName(type: String): String = type.substringAfterLast('.').replace('$', '.')
    }
}
