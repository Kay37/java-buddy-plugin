package dev.javabuddy.progress

import dev.javabuddy.concepts.Concept
import dev.javabuddy.concepts.Concepts
import dev.javabuddy.drills.Topic as DrillTopic

/** The groups on the Progress Map, roughly in the order a course meets them. */
enum class IdeaGroup(val title: String) {
    BASICS("The basics"),
    TEXT("Text and input"),
    CHOICES("Choices"),
    METHODS("Methods"),
    LOOPS("Loops"),
    DATA("Arrays and lists"),
    OBJECTS("Classes and objects"),
    PROBLEMS("Handling problems"),
}

/**
 * One Java idea the map looks for in the learner's code. [pattern] is matched against each added
 * line with text in quotes and comments removed (or against the raw line when [raw] is set).
 */
data class Idea(
    val id: String,
    val name: String,
    val group: IdeaGroup,
    val pattern: Regex,
    val concept: Concept? = null,
    val drills: DrillTopic? = null,
    val raw: Boolean = false,
)

private fun idea(id: String, name: String, group: IdeaGroup, pattern: String, concept: Concept? = null, drills: DrillTopic? = null, raw: Boolean = false) =
    Idea(id, name, group, Regex(pattern), concept, drills, raw)

val IDEAS: List<Idea> = listOf(
    idea("print", "Printing", IdeaGroup.BASICS, """System\.out\.print(ln)?\s*\("""),
    idea("int", "Whole numbers (int)", IdeaGroup.BASICS, """\bint\s+\w+\s*[=;,)]""", Concepts.INT, DrillTopic.VARIABLES),
    idea("double", "Decimals (double)", IdeaGroup.BASICS, """\bdouble\s+\w+\s*[=;,)]""", Concepts.DOUBLE, DrillTopic.VARIABLES),
    idea("boolean", "true / false (boolean)", IdeaGroup.BASICS, """\bboolean\s+\w+""", Concepts.BOOLEAN, DrillTopic.DECISIONS),
    idea("char", "Single letters (char)", IdeaGroup.BASICS, """\bchar\s+\w+""", Concepts.CHAR, DrillTopic.STRINGS),
    idea("math", "Math methods", IdeaGroup.BASICS, """\bMath\.\w+""", drills = DrillTopic.VARIABLES),
    idea("remainder", "Remainder (%)", IdeaGroup.BASICS, """[\w)]\s*%\s*[\w(]""", drills = DrillTopic.VARIABLES),
    idea("constant", "Constants (final)", IdeaGroup.BASICS, """\bfinal\s+[\w<>\[\]]+\s+\w+\s*=""", Concepts.FINAL),

    idea("string", "Text (String)", IdeaGroup.TEXT, """\bString\s+\w+\s*[=;,)]""", Concepts.STRING, DrillTopic.STRINGS),
    idea("stringMethods", "String methods", IdeaGroup.TEXT, """\.(length|charAt|substring|toUpperCase|toLowerCase|indexOf|trim|contains|replace|split|startsWith|endsWith)\s*\(""", drills = DrillTopic.STRINGS),
    idea("equals", "Comparing text (equals)", IdeaGroup.TEXT, """\.equals(IgnoreCase)?\s*\(""", drills = DrillTopic.STRINGS),
    idea("scanner", "Reading input (Scanner)", IdeaGroup.TEXT, """new\s+Scanner\s*\(""", drills = DrillTopic.INPUT),
    idea("printf", "printf and String.format", IdeaGroup.TEXT, """\.printf\s*\(|String\.format\s*\(""", drills = DrillTopic.INPUT),
    idea("textBlock", "Text blocks (\"\"\")", IdeaGroup.TEXT, "\"\"\"", raw = true),

    idea("if", "if", IdeaGroup.CHOICES, """\bif\s*\(""", Concepts.IF, DrillTopic.DECISIONS),
    idea("else", "else", IdeaGroup.CHOICES, """\belse\b""", Concepts.IF, DrillTopic.DECISIONS),
    idea("logic", "And / or (&&, ||)", IdeaGroup.CHOICES, """&&|\|\|""", drills = DrillTopic.DECISIONS),
    idea("switch", "switch", IdeaGroup.CHOICES, """\bswitch\s*\(""", Concepts.SWITCH, DrillTopic.SWITCH),

    idea("staticMethod", "Your own static methods", IdeaGroup.METHODS, """^\s*(public\s+|private\s+)?static\s+(?!void\s+main\s*\()[\w<>\[\]]+\s+\w+\s*\(""", Concepts.STATIC, DrillTopic.STATIC),
    idea("parameters", "Methods with parameters", IdeaGroup.METHODS, """^\s*(public\s+|private\s+|protected\s+)?(static\s+)?[\w<>\[\]]+\s+(?!main\s*\()\w+\s*\(\s*[\w<>\[\]]+\s+\w+""", Concepts.PARAMETERS, DrillTopic.STATIC),
    idea("return", "Giving back a value (return)", IdeaGroup.METHODS, """\breturn\s+[^;\s]""", Concepts.RETURN, DrillTopic.STATIC),
    idea("void", "void methods", IdeaGroup.METHODS, """\bvoid\s+(?!main\s*\()\w+\s*\(""", Concepts.VOID, DrillTopic.STATIC),

    idea("for", "for loops", IdeaGroup.LOOPS, """\bfor\s*\(\s*(int|var|long)\s+\w+\s*=""", Concepts.FOR, DrillTopic.LOOPS),
    idea("forEach", "for-each loops", IdeaGroup.LOOPS, """\bfor\s*\(\s*(final\s+)?[\w<>\[\]]+\s+\w+\s*:""", Concepts.FOR, DrillTopic.LOOPS),
    idea("while", "while loops", IdeaGroup.LOOPS, """\bwhile\s*\(""", Concepts.WHILE, DrillTopic.LOOPS),
    idea("break", "break and continue", IdeaGroup.LOOPS, """\b(break|continue)\s*;""", Concepts.BREAK),

    idea("array", "Arrays", IdeaGroup.DATA, """[\w>]\s*\[\s*]\s+\w+\s*[=;]|new\s+\w+\s*\[""", Concepts.ARRAY, DrillTopic.ARRAYS),
    idea("arrayList", "ArrayList", IdeaGroup.DATA, """new\s+ArrayList\s*<""", Concepts.GENERICS),
    idea("map", "HashMap", IdeaGroup.DATA, """new\s+(Hash|Tree|LinkedHash)Map\s*<""", Concepts.GENERICS),

    idea("newObject", "Making objects (new)", IdeaGroup.OBJECTS, """new\s+(?!(Scanner|ArrayList|HashMap|TreeMap|LinkedHashMap|HashSet|Random|String|StringBuilder|File|FileReader|FileWriter|BufferedReader|BufferedWriter|PrintWriter|IllegalArgumentException|RuntimeException|Exception)\b)[A-Z]\w*\s*\(""", Concepts.NEW),
    idea("privateField", "Private fields", IdeaGroup.OBJECTS, """^\s*private\s+(?!static\b)(final\s+)?[\w<>\[\]]+\s+\w+\s*[;=]""", Concepts.PRIVATE),
    idea("constructor", "Constructors", IdeaGroup.OBJECTS, """^\s*public\s+[A-Z]\w*\s*\([^)]*\)\s*\{?\s*$"""),
    idea("this", "this", IdeaGroup.OBJECTS, """\bthis\.\w+""", Concepts.THIS),
    idea("getter", "Getters and setters", IdeaGroup.OBJECTS, """\b(get|set|is)[A-Z]\w*\s*\([^)]*\)\s*\{"""),
    idea("extends", "Inheritance (extends)", IdeaGroup.OBJECTS, """\bclass\s+\w+\s+extends\s+\w+""", Concepts.EXTENDS),
    idea("interface", "Interfaces", IdeaGroup.OBJECTS, """\binterface\s+\w+|\bimplements\s+\w+""", Concepts.INTERFACE),
    idea("override", "@Override", IdeaGroup.OBJECTS, """@Override\b""", Concepts.OVERRIDE),
    idea("enum", "enum", IdeaGroup.OBJECTS, """\benum\s+\w+\s*\{""", Concepts.ENUM),

    idea("try", "try and catch", IdeaGroup.PROBLEMS, """\bcatch\s*\(""", Concepts.TRY),
    idea("throw", "Throwing errors (throw)", IdeaGroup.PROBLEMS, """\bthrow\s+new\b""", Concepts.THROW),
    idea("files", "Reading and writing files", IdeaGroup.PROBLEMS, """new\s+(FileReader|FileWriter|BufferedReader|BufferedWriter|PrintWriter|File)\s*\(|\bFiles\.\w+\s*\("""),
)

private val STRING_LITERAL = Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])'""")
private val LINE_COMMENT = Regex("""//.*$""")

/** The ideas used in one line of code. Text in quotes and // comments are ignored. */
fun ideasIn(line: String): List<Idea> {
    val trimmed = line.trim()
    if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return emptyList()
    val code = LINE_COMMENT.replace(STRING_LITERAL.replace(line, "\"\""), "")
    return IDEAS.filter { idea -> idea.pattern.containsMatchIn(if (idea.raw) line else code) }
}
