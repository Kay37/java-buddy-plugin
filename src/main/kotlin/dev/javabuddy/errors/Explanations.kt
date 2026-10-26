package dev.javabuddy.errors

/**
 * A plain-English explanation of one error. [example] is a short before/after snippet, shown in a
 * code box; lines starting with "✗" are the mistake and "✓" the fix.
 */
data class Explanation(
    val title: String,
    val meaning: String,
    val fix: String,
    val example: String? = null,
)

/** Matches an error message (IntelliJ's own wording, or javac's) and explains it. */
class Rule(pattern: String, val explain: (MatchResult) -> Explanation) {
    val regex = Regex(pattern)
}

/** "java.lang.String" -> "String", "java.util.List<java.lang.Integer>" -> "List<Integer>". */
fun simpleType(type: String): String = type.replace(Regex("""\b(?:[a-z][\w]*\.)+(?=[A-Z])"""), "")

/** "a String", "an int", "an Integer". */
fun article(type: String): String = (if (type.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an " else "a ") + type

/** Classes beginners use most, with the import line they need. java.lang ones need none. */
val COMMON_IMPORTS = mapOf(
    "Scanner" to "java.util.Scanner",
    "ArrayList" to "java.util.ArrayList",
    "List" to "java.util.List",
    "HashMap" to "java.util.HashMap",
    "Map" to "java.util.Map",
    "HashSet" to "java.util.HashSet",
    "Set" to "java.util.Set",
    "Arrays" to "java.util.Arrays",
    "Random" to "java.util.Random",
    "Collections" to "java.util.Collections",
    "LinkedList" to "java.util.LinkedList",
    "Iterator" to "java.util.Iterator",
    "Optional" to "java.util.Optional",
    "LocalDate" to "java.time.LocalDate",
    "LocalDateTime" to "java.time.LocalDateTime",
    "File" to "java.io.File",
    "FileNotFoundException" to "java.io.FileNotFoundException",
    "IOException" to "java.io.IOException",
    "Files" to "java.nio.file.Files",
    "Path" to "java.nio.file.Path",
    "Paths" to "java.nio.file.Paths",
)

/** Errors IntelliJ underlines in red while you type. Checked in order; the first match wins. */
val COMPILE_RULES: List<Rule> = listOf(
    Rule("""^Cannot resolve symbol '(.+?)'$|^cannot find symbol""") { m ->
        val name = m.groupValues[1]
        val import = COMMON_IMPORTS[name]
        when {
            import != null -> Explanation(
                title = "Java needs to be told where '$name' comes from",
                meaning = "'$name' comes with Java, but you have to bring it into your file first. That's what an import line does.",
                fix = "Add  import $import;  at the very top of the file. Or click on '$name' and press Alt+Enter → Import class.",
                example = "✓ import $import;\n\n  public class Main { ... }",
            )
            name.firstOrNull()?.isLowerCase() == true && name.replaceFirstChar { it.uppercase() } in setOf("String", "System", "Math", "Integer", "Double", "Scanner") ->
                Explanation(
                    title = "Capital letters matter: '$name'",
                    meaning = "To Java, '$name' and '${name.replaceFirstChar { it.uppercase() }}' are two different names. Class names start with a capital letter.",
                    fix = "Change it to '${name.replaceFirstChar { it.uppercase() }}'.",
                    example = "✗ $name\n✓ ${name.replaceFirstChar { it.uppercase() }}",
                )
            else -> Explanation(
                title = "Java doesn't know anything called '$name'",
                meaning = "Java can't find anything named '$name' from this line. Usually it's a typo. " +
                    "Or the variable was made inside a different { } block, or never made at all.",
                fix = "Check the spelling (capital letters count). Make sure '$name' is created above this line, inside the same { } block or one around it.",
                example = "✗ int total = 0;\n  System.out.println(totl);\n✓ System.out.println(total);",
            )
        }
    },
    Rule("""^Cannot resolve method '(.+?)'(?: in '(.+?)')?$""") { m ->
        val method = m.groupValues[1]
        val where = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { " on ${article(simpleType(it))}" }.orEmpty()
        Explanation(
            title = "There's no method called '$method'$where",
            meaning = "You're calling '$method(...)', but Java can't find a method with that name$where.",
            fix = "Check the spelling and capital letters. Tip: type the value, then a dot, and wait. IntelliJ shows a list of everything you can call.",
            example = "✗ name.Length()\n✓ name.length()",
        )
    },
    Rule("""^Incompatible types\. Found: '(.+?)', required: '(.+?)'$|^incompatible types: (.+?) cannot be converted to (.+)$""") { m ->
        val found = simpleType(m.groupValues[1].ifEmpty { m.groupValues[3] })
        val required = simpleType(m.groupValues[2].ifEmpty { m.groupValues[4] })
        val (fix, example) = when {
            found == "String" && required in setOf("int", "Integer") ->
                "Turn the text into a number with Integer.parseInt(...)." to "✗ int age = \"21\";\n✓ int age = Integer.parseInt(\"21\");"
            found == "String" && required in setOf("double", "Double") ->
                "Turn the text into a number with Double.parseDouble(...)." to "✓ double price = Double.parseDouble(text);"
            found in setOf("int", "double", "long", "boolean", "char") && required == "String" ->
                "Turn the value into text with String.valueOf(...), or join it to some text with +." to "✗ String s = 42;\n✓ String s = String.valueOf(42);"
            found == "double" && required in setOf("int", "long") ->
                "${article(found).replaceFirstChar { it.uppercase() }} can have decimals, ${article(required)} can't. To chop the decimals off, put ($required) in front. Or make the variable a double." to "✗ int half = 7 / 2.0;\n✓ int half = (int) (7 / 2.0);   // 3"
            found == "int" && required == "boolean" ->
                "An if needs a yes/no (true/false) answer. To compare, use == (two equals signs), not = (one)." to "✗ if (x = 5)\n✓ if (x == 5)"
            found == "String" && required == "char" ->
                "Single quotes hold one letter (a char): 'a'. Double quotes hold text (a String): \"a\". Use 'a', or text.charAt(0)." to "✗ char c = \"a\";\n✓ char c = 'a';"
            else -> "Use ${article(required)} here, or change what kind of value the variable holds." to null
        }
        Explanation(
            title = "Wrong kind of value: got $found, needed $required",
            meaning = "This spot needs ${article(required)}, but you gave it ${article(found)}. Java checks this before your program runs.",
            fix = fix,
            example = example,
        )
    },
    Rule("""^Missing return statement$|^missing return statement$""") {
        Explanation(
            title = "The method can end without giving back a value",
            meaning = "This method promises to give back a value (it doesn't say void). But in some cases it reaches the closing } without a return.",
            fix = "Make sure every way through the method ends with a return, even after if/else and loops. Often a last return at the bottom is missing.",
            example = "  int sign(int x) {\n      if (x > 0) return 1;\n      else if (x < 0) return -1;\n✓     return 0;\n  }",
        )
    },
    Rule("""^';' expected$""") {
        Explanation(
            title = "Missing semicolon ;",
            meaning = "Each line of Java code ends with a semicolon, like a full stop at the end of a sentence.",
            fix = "Put ; at the end of the line. It's usually missing right where the red mark is, or at the end of the line just above.",
            example = "✗ int count = 5\n✓ int count = 5;",
        )
    },
    Rule("""^'([)(}{\]])' expected$|^'\(' or '\[' expected$""") { m ->
        val bracket = m.groupValues[1].ifEmpty { "(" }
        val (name, pair) = when (bracket) {
            ")", "(" -> "round bracket" to "( )"
            "}", "{" -> "curly brace" to "{ }"
            else -> "square bracket" to "[ ]"
        }
        Explanation(
            title = "Missing $name $bracket",
            meaning = "Brackets come in pairs $pair. One is missing, so Java can't tell where this part ends.",
            fix = "Add the missing $bracket. Tip: click just after a bracket and IntelliJ lights up its partner. The one with no partner is the problem.",
            example = if (bracket in setOf(")", "(")) "✗ System.out.println(\"Hi\";\n✓ System.out.println(\"Hi\");" else null,
        )
    },
    Rule("""^(Identifier|Expression|Statement|Identifier or type) expected$|^illegal start of (expression|type)$|^<identifier> expected$""") {
        Explanation(
            title = "Java didn't expect this here",
            meaning = "Java got confused at this spot. Common causes: a missing or extra bracket on a line above, " +
                "code written outside of any method, or a half-finished line.",
            fix = "Look at this line and the one above it. Check the brackets match. Code like println(...) has to go inside a method, such as main.",
            example = "✗ public class Main {\n      System.out.println(\"Hi\");\n  }\n✓ public class Main {\n      public static void main(String[] args) {\n          System.out.println(\"Hi\");\n      }\n  }",
        )
    },
    Rule("""^Variable '(.+?)' might not have been initialized$|^variable (\S+) might not have been initialized$""") { m ->
        val name = m.groupValues[1].ifEmpty { m.groupValues[2] }
        Explanation(
            title = "'$name' might not have a value yet",
            meaning = "'$name' was made without a starting value. In some cases (like an if with no else), it still has no value when this line runs.",
            fix = "Give '$name' a starting value where you make it, like  = 0. Or make sure it always gets a value before this line.",
            example = "✗ int score;\n  if (passed) score = 10;\n  System.out.println(score);\n✓ int score = 0;",
        )
    },
    Rule("""^Unhandled exceptions?: (.+)$|^unreported exception (\S+); must be caught or declared to be thrown$""") { m ->
        val exceptions = simpleType(m.groupValues[1].ifEmpty { m.groupValues[2] })
        Explanation(
            title = "Java wants a plan for when this goes wrong: $exceptions",
            meaning = "This line can fail with $exceptions (for example, the file isn't there). Java makes you say what should happen then.",
            fix = "Put it inside try { ... } catch ($exceptions e) { ... }. Or add  throws $exceptions  to the method's first line. Alt+Enter on the red code can do either for you.",
            example = "✓ try {\n      Scanner in = new Scanner(new File(\"data.txt\"));\n  } catch (FileNotFoundException e) {\n      System.out.println(\"File not found\");\n  }",
        )
    },
    Rule("""^Non-static (method|field|variable) '(.+?)' cannot be referenced from a static context$|^non-static (method|variable) (\S+) cannot be referenced from a static context$""") { m ->
        val kind = m.groupValues[1].ifEmpty { m.groupValues[3] }
        val name = m.groupValues[2].ifEmpty { m.groupValues[4] }
        Explanation(
            title = "main can't use '$name' because one has static and one doesn't",
            meaning = "main has the word static, so it runs on its own. '$name' is a $kind without static, so it only works through an object made with new. main can't use it directly.",
            fix = "The easy fix while learning: add static to '$name' too. Or make an object first and use it through that: new Main().$name",
            example = "✗ public static void main(String[] args) { greet(); }\n  void greet() { ... }\n✓ static void greet() { ... }",
        )
    },
    Rule("""^Unreachable statement$|^unreachable statement$""") {
        Explanation(
            title = "This line can never run",
            meaning = "Something above it always leaves first: a return, break, continue or throw, or a loop that never ends.",
            fix = "Move this line above the return (or break), or delete it if you don't need it.",
            example = "✗ return total;\n  System.out.println(\"done\");\n✓ System.out.println(\"done\");\n  return total;",
        )
    },
    Rule("""^Variable '(.+?)' is already defined in the scope$|^variable (\S+) is already defined in method""") { m ->
        val name = m.groupValues[1].ifEmpty { m.groupValues[2] }
        Explanation(
            title = "'$name' already exists here",
            meaning = "You made '$name' twice. Writing the type in front (int $name = ...) makes a brand-new variable, and you can only do that once.",
            fix = "To change the one you already have, leave off the type: $name = ... . Or pick a different name if you really need a second one.",
            example = "  int $name = 1;\n✗ int $name = 2;\n✓ $name = 2;",
        )
    },
    Rule("""^Class '(.+?)' is public, should be declared in a file named '(.+?)'$|^class (\S+) is public, should be declared in a file named (\S+)$""") { m ->
        val cls = m.groupValues[1].ifEmpty { m.groupValues[3] }
        val file = m.groupValues[2].ifEmpty { m.groupValues[4] }
        Explanation(
            title = "The file name must match the class name",
            meaning = "A public class called $cls has to be in a file called $file, with the same capital letters.",
            fix = "Rename the file (right-click it → Refactor → Rename), or rename the class, so they match.",
        )
    },
    Rule("""^Operator '(.+?)' cannot be applied to '(.+?)', '(.+?)'$|^bad operand types for binary operator '(.+?)'$""") { m ->
        val op = m.groupValues[1].ifEmpty { m.groupValues[4] }
        val left = simpleType(m.groupValues[2])
        val right = simpleType(m.groupValues[3])
        val types = if (left.isNotEmpty()) " between ${article(left)} and ${article(right)}" else ""
        val stringCompare = op in setOf("<", ">", "<=", ">=") && left == "String"
        Explanation(
            title = "'$op' doesn't work$types",
            meaning = if (stringCompare) "You can't compare text with < or >. Those only work on numbers."
                      else "The $op sign can't be used with these two kinds of values.",
            fix = if (stringCompare) "Use a.compareTo(b). It gives a number below 0 if a comes first, 0 if they're the same, above 0 if a comes after."
                  else "Check both sides hold what you expect. Text may need turning into a number first (Integer.parseInt). && and || need true/false on both sides.",
            example = if (stringCompare) "✗ if (name < other)\n✓ if (name.compareTo(other) < 0)" else null,
        )
    },
    Rule("""^Class '(.+?)' must (?:either be declared abstract or )?implement abstract method '(.+?)' in '(.+?)'$|^(\S+) is not abstract and does not override abstract method""") { m ->
        val cls = m.groupValues[1].ifEmpty { m.groupValues[4] }
        val method = m.groupValues[2]
        val parent = m.groupValues[3]
        Explanation(
            title = "$cls is missing a method it promised to have${if (method.isNotEmpty()) ": $method" else ""}",
            meaning = "$cls builds on ${parent.ifEmpty { "another class or interface" }}, which lists ${method.ifEmpty { "a method" }} but leaves the code for you to write. $cls has to write it.",
            fix = "Click the class name and press Alt+Enter → Implement methods. Then write the code inside the new method.",
        )
    },
    Rule("""^'else' without 'if'$""") {
        Explanation(
            title = "This else has no matching if",
            meaning = "An else must come right after an if block. Usually there's an extra ; after if (...), or missing { }.",
            fix = "Remove any ; right after if (...). Put the if part inside { } when it's more than one line.",
            example = "✗ if (x > 0);\n  else ...\n✓ if (x > 0) {\n      ...\n  } else {\n      ...\n  }",
        )
    },
    Rule("""^Line end not allowed in string literals$|^unclosed string literal$""") {
        Explanation(
            title = "This text is missing its closing quote \"",
            meaning = "Text (a String) starts and ends with a double quote. Without the closing one, Java thinks the rest of the line is text too.",
            fix = "Add the closing \" where the text should end.",
            example = "✗ System.out.println(\"Hello);\n✓ System.out.println(\"Hello\");",
        )
    },
    Rule("""^Too many characters in character literal$|^unclosed character literal$|^Unclosed character literal$""") {
        Explanation(
            title = "Single quotes are for one letter only",
            meaning = "'a' holds a single letter (a char). For a word or sentence you need double quotes (a String).",
            fix = "Use double quotes for text: \"hello\". Keep single quotes for one letter: 'h'.",
            example = "✗ String word = 'hello';\n✓ String word = \"hello\";",
        )
    },
    Rule("""^Empty character literal$""") {
        Explanation(
            title = "Empty single quotes ''",
            meaning = "Single quotes must hold exactly one letter or symbol, so '' (nothing inside) isn't allowed.",
            fix = "Put one character inside, like ' ' for a space. Or use empty double quotes \"\" for empty text.",
        )
    },
    Rule("""^Cannot assign a value to final variable '(.+?)'$|^cannot assign a value to final variable (\S+)$""") { m ->
        val name = m.groupValues[1].ifEmpty { m.groupValues[2] }
        Explanation(
            title = "'$name' is final, so it can't change",
            meaning = "final means \"set once, never changed\". This line tries to give '$name' a new value.",
            fix = "Remove the word final from '$name' if it needs to change. Or use a different variable for the new value.",
        )
    },
    Rule("""^'(.+?)' is abstract; cannot be instantiated$|^(\S+) is abstract; cannot be instantiated$""") { m ->
        val cls = simpleType(m.groupValues[1].ifEmpty { m.groupValues[2] })
        Explanation(
            title = "You can't use new $cls() directly",
            meaning = "$cls is only an outline with parts missing (it's abstract, or an interface). You can only use new with a class that fills those parts in.",
            fix = if (cls.startsWith("List")) "Use a real list class: new ArrayList<>()." else "Use new with a class that builds on $cls instead.",
            example = if (cls.startsWith("List")) "✗ List<String> names = new List<>();\n✓ List<String> names = new ArrayList<>();" else null,
        )
    },
    Rule("""^Expected (no arguments|\d+ arguments?) but found (\d+)$""") { m ->
        Explanation(
            title = "Wrong number of values in the brackets",
            meaning = "This method needs ${m.groupValues[1].replace("arguments", "values").replace("argument", "value")}, but you gave it ${m.groupValues[2]}.",
            fix = "Click inside the brackets and press Ctrl+P to see what the method wants. Then give exactly those values, with commas between them.",
        )
    },
    Rule("""^'(.+?)' in '(.+?)' cannot be applied to '(.+?)'$|^method (\S+) in (?:class|interface) (\S+) cannot be applied to given types""") { m ->
        val method = m.groupValues[1].ifEmpty { m.groupValues[4] }
        val given = simpleType(m.groupValues[3])
        Explanation(
            title = "The values you gave don't fit ${method.substringBefore('(')}",
            meaning = "The method wants ${simpleType(method)}${if (given.isNotEmpty()) ", but you gave it $given" else ""}. The number of values, or their kind, doesn't match.",
            fix = "Click inside the brackets and press Ctrl+P to see what it wants. Check the order too: (name, age) isn't the same as (age, name).",
        )
    },
    Rule("""^'(.+?)' has private access in '(.+?)'$|^(\S+) has private access in (\S+)$""") { m ->
        val member = m.groupValues[1].ifEmpty { m.groupValues[3] }
        val cls = simpleType(m.groupValues[2].ifEmpty { m.groupValues[4] })
        Explanation(
            title = "'$member' is private to $cls",
            meaning = "private means only code inside the $cls class can use '$member'. Code outside it isn't allowed to touch it.",
            fix = "Use a public method of $cls instead, like a getter: get${member.replaceFirstChar { it.uppercase() }}(). If you wrote $cls, you can add one.",
            example = "✗ person.name\n✓ person.getName()",
        )
    },
    Rule("""^'break' outside of switch or loop$|^break outside switch or loop$""") {
        Explanation(
            title = "break only works inside a loop or switch",
            meaning = "break means \"leave this loop (or switch) now\". This one isn't inside either, so there's nothing to leave.",
            fix = "Move it inside the loop. Or use return to leave the whole method.",
        )
    },
    Rule("""^'continue' statement outside of loop$|^continue outside of loop$""") {
        Explanation(
            title = "continue only works inside a loop",
            meaning = "continue means \"skip to the next round of the loop\". This one isn't inside a loop.",
            fix = "Move it inside the loop, or remove it.",
        )
    },
    Rule("""^Cannot return a value from a method with void result type$|^incompatible types: unexpected return value$""") {
        Explanation(
            title = "A void method can't give back a value",
            meaning = "void means the method gives back nothing. But this return has a value after it.",
            fix = "Change void to the kind of value you're giving back (like int). Or just write  return;  with nothing after it.",
            example = "✗ static void add(int a, int b) { return a + b; }\n✓ static int add(int a, int b) { return a + b; }",
        )
    },
    Rule("""^Missing return value$""") {
        Explanation(
            title = "return needs a value here",
            meaning = "This method promises to give back a value, but this return has nothing after it.",
            fix = "Put the value to give back after return, like  return total;",
        )
    },
    Rule("""^Method return type missing or constructor name does not match class name$|^Method return type missing$|^invalid method declaration; return type required$""") {
        Explanation(
            title = "The method needs to say what it gives back",
            meaning = "Before its name, every method says what it gives back: void (nothing), int, String... " +
                "The only exception is a constructor (the code that runs when you write new), whose name must match the class name exactly.",
            fix = "Add void before the name if it gives back nothing. If it's meant to be a constructor, make its name match the class name, capitals included.",
            example = "✗ static greet() { ... }\n✓ static void greet() { ... }",
        )
    },
    Rule("""^There is no no-arg constructor available in '(.+?)'$|^constructor (\S+) in class (\S+) cannot be applied to given types""") { m ->
        val cls = simpleType(m.groupValues[1].ifEmpty { m.groupValues[3] })
        Explanation(
            title = "new $cls needs some values in the brackets",
            meaning = "$cls needs some values to be created, so new $cls() with empty brackets doesn't work.",
            fix = "Click inside the brackets and press Ctrl+P to see which values it wants, then give them. Or, if you wrote $cls, add a constructor with empty brackets.",
        )
    },
    Rule("""^Inconvertible types; cannot cast '(.+?)' to '(.+?)'$|^incompatible types: (\S+) cannot be converted to (\S+)$""") { m ->
        val from = simpleType(m.groupValues[1].ifEmpty { m.groupValues[3] })
        val to = simpleType(m.groupValues[2].ifEmpty { m.groupValues[4] })
        Explanation(
            title = "Putting ($to) in front can't turn ${article(from)} into ${article(to)}",
            meaning = "Putting a type in brackets in front, like ($to), only works between close relatives, such as int and double.",
            fix = if (from == "String") "To get a number from text, use Integer.parseInt(text) or Double.parseDouble(text)." else "Use a converting method instead, such as String.valueOf(...) or Integer.parseInt(...).",
            example = if (from == "String") "✗ int n = (int) text;\n✓ int n = Integer.parseInt(text);" else null,
        )
    },
    Rule("""^Cannot inherit from (?:final class|enum|record) '(.+?)'$|^cannot inherit from final (\S+)$""") { m ->
        val cls = simpleType(m.groupValues[1].ifEmpty { m.groupValues[2] })
        Explanation(
            title = "You can't build on $cls with extends",
            meaning = "$cls is locked (final), so no class can extend it. String is a common example.",
            fix = "Instead of extending $cls, give your class a variable that holds ${article(cls)}, and use that.",
        )
    },
    Rule("""^Cannot access fields on '(.+?)' type$|^(\S+) cannot be dereferenced$""") { m ->
        val type = m.groupValues[1].ifEmpty { m.groupValues[2] }
        Explanation(
            title = "You can't put a dot after a plain $type",
            meaning = "$type is a simple value, like a number, not an object. It has no methods, so nothing can come after a dot.",
            fix = when (type) {
                "int" -> "To get text, use String.valueOf(x). For an array's size, use array.length (no brackets)."
                else -> "Use helpers like Integer.toString(x) or Double.compare(a, b) instead."
            },
            example = if (type == "int") "✗ int digits = number.length();\n✓ int digits = String.valueOf(number).length();" else null,
        )
    },
    Rule("""^Reference to variable expected on left-hand side of assignment$|^unexpected type\s+required: variable""") {
        Explanation(
            title = "The left side of = must be a variable",
            meaning = "= puts the value on the right into the variable on the left. Here the left side isn't a variable.",
            fix = "Put the variable on the left: total = 5, not 5 = total. To compare two values, use == instead.",
        )
    },
    Rule("""^Illegal character: (.+)$|^illegal character: (.+)$""") { m ->
        Explanation(
            title = "A character Java can't read: ${m.groupValues[1].ifEmpty { m.groupValues[2] }}",
            meaning = "Code copied from a website or document often brings \"curly\" quotes or invisible characters with it.",
            fix = "Delete the character and type it again yourself: straight quotes \" and ', and a normal space.",
        )
    },
    Rule("""^Duplicate class: '(.+?)'$|^duplicate class: (\S+)$""") { m ->
        val cls = m.groupValues[1].ifEmpty { m.groupValues[2] }
        Explanation(
            title = "There are two classes called $cls",
            meaning = "Two classes in the same folder have the same name, so Java can't tell them apart.",
            fix = "Rename one of them, or delete the copy you don't need.",
        )
    },
    Rule("""^Modifier '(.+?)' not allowed here$|^modifier (\S+) not allowed here$""") { m ->
        val modifier = m.groupValues[1].ifEmpty { m.groupValues[2] }
        Explanation(
            title = "'$modifier' can't be used here",
            meaning = "Words like public, private and static only fit in certain places. For example, a variable inside a method can't be public or static.",
            fix = "Remove '$modifier' from this line. If the variable should be shared by the whole class, move it outside the method.",
        )
    },
    Rule("""^Abstract methods cannot have a body$|^abstract methods cannot have a body$""") {
        Explanation(
            title = "An abstract method has no { } part",
            meaning = "abstract means \"another class will write the code for this\". So the line just ends with ; and has no code here.",
            fix = "Either remove the word abstract and keep the code, or end the line with ; and delete the { ... } part.",
        )
    },
    Rule("""^Missing method body, or declare abstract$|^missing method body, or declare abstract$""") {
        Explanation(
            title = "This method has no { } part",
            meaning = "A method needs its code between { and }. Often a ; was typed right after the brackets by mistake.",
            fix = "Remove the ; after the ( ) and add a { ... } block.",
            example = "✗ static void greet();\n✓ static void greet() {\n      System.out.println(\"Hi\");\n  }",
        )
    },
    Rule("""^Method does not override method from its superclass$|^method does not override or implement a method from a supertype$""") {
        Explanation(
            title = "@Override, but there's nothing to replace",
            meaning = "@Override says \"this replaces a method from the parent class\". But the parent has no method with this exact name and brackets.",
            fix = "Check the name, capitals and what's in the brackets match the parent's method exactly, like toString() or equals(Object o).",
            example = "✗ @Override public String tostring()\n✓ @Override public String toString()",
        )
    },
)

/**
 * Crashes while the program runs. Each rule matches "ExceptionName: message"; groups may pick
 * parts of the message out.
 */
val CRASH_RULES: List<Rule> = listOf(
    Rule("""^java\.lang\.NullPointerException(?:: (.*))?$""") { m ->
        val message = m.groupValues[1]
        val culprit = Regex("""because (?:the return value of )?"(.+?)" is null""").find(message)?.groupValues?.get(1)
            ?.replace(Regex("""^this\."""), "")
            ?.takeUnless { it.startsWith("<local") }
        val action = Regex("""Cannot (?:invoke|read field|load from|store to|read the array length) "?(.+?)"? because""").find(message)?.groupValues?.get(1)
        Explanation(
            title = if (culprit != null) "'$culprit' was empty (null)" else "Something was empty (null)",
            meaning = (if (culprit != null) "'$culprit' was empty: it didn't hold anything yet" else "A variable was empty: it didn't hold anything yet") +
                (if (action != null) ", so Java couldn't do ${simpleType(action)} with it." else ", but the code tried to use it with a dot (.)."),
            fix = "Find where it should get its value and make sure that happens first. Or check before using it:  if (x != null) { ... }",
            example = "✗ String name;           // empty: no value yet\n  name.length();\n✓ String name = \"Kim\";",
        )
    },
    Rule("""^java\.lang\.ArrayIndexOutOfBoundsException(?:: Index (-?\d+) out of bounds for length (\d+))?.*$""") { m ->
        val index = m.groupValues[1]
        val length = m.groupValues[2]
        Explanation(
            title = if (index.isNotEmpty()) "Asked for spot $index in an array with only $length spots" else "Went past the end of an array",
            meaning = if (index.isNotEmpty()) "Arrays start counting at 0. So an array with $length spots has spots 0 to ${length.toInt() - 1}. Spot $index doesn't exist."
                      else "Arrays start counting at 0, so the last spot is length - 1.",
            fix = "In loops, use  i < array.length  (not <=). Any spot number you work out must stay between 0 and length - 1.",
            example = "✗ for (int i = 0; i <= nums.length; i++)\n✓ for (int i = 0; i < nums.length; i++)",
        )
    },
    Rule("""^java\.lang\.StringIndexOutOfBoundsException(?:: (.*))?$""") { m ->
        Explanation(
            title = "Went past the end of some text",
            meaning = "Letters in a String are numbered from 0, so \"cat\" has letters 0, 1 and 2. The code asked for a letter number that isn't there." +
                (m.groupValues[1].takeIf { it.isNotEmpty() }?.let { " (Java said: $it)" } ?: ""),
            fix = "Use letter numbers from 0 to text.length() - 1. For substring(start, end), end can be at most text.length().",
            example = "✗ word.charAt(word.length())\n✓ word.charAt(word.length() - 1)   // last letter",
        )
    },
    Rule("""^java\.lang\.IndexOutOfBoundsException(?:: Index (-?\d+) out of bounds for length (\d+))?.*$""") { m ->
        val index = m.groupValues[1]
        val length = m.groupValues[2]
        Explanation(
            title = if (index.isNotEmpty()) "Asked for item $index of a list with $length items" else "Went past the end of a list",
            meaning = if (index.isNotEmpty() && length == "0") "The list is empty, so there's no item $index yet."
                      else if (index.isNotEmpty()) "Lists start counting at 0. So a list of $length items has items 0 to ${length.toInt() - 1}."
                      else "Lists start counting at 0, so the last item is size() - 1.",
            fix = "Use  i < list.size()  in loops. Check the list isn't empty before you call get(0).",
            example = "✓ if (!names.isEmpty()) {\n      System.out.println(names.get(0));\n  }",
        )
    },
    Rule("""^java\.lang\.ArithmeticException: / by zero$""") {
        Explanation(
            title = "Divided by zero",
            meaning = "Whole numbers (int, long) can't be divided by 0, so Java stops the program.",
            fix = "Check the number you're dividing by first:  if (count != 0) { average = total / count; }",
        )
    },
    Rule("""^java\.lang\.NumberFormatException: (?:For input string: "(.*)"|(.*))(?: under radix \d+)?$""") { m ->
        val input = m.groupValues[1]
        // A lone | almost always comes from split("|"), which cuts between every letter.
        if (input == "|") return@Rule Explanation(
            title = "\"|\" isn't a number: check your split",
            meaning = "The text being turned into a number is just \"|\". That usually means the line was cut with split(\"|\"). " +
                "To split, | is a special symbol, so split(\"|\") cuts between every single letter instead of at each |.",
            fix = "Put \\\\ in front of the |: split(\"\\\\|\"). Then each piece between the | marks is one part.",
            example = "✗ String[] parts = line.split(\"|\");     // cuts between every letter\n✓ String[] parts = line.split(\"\\\\|\");   // cuts at each |",
        )
        Explanation(
            title = if (input.isNotEmpty()) "\"$input\" isn't a number" else "This text isn't a number",
            meaning = "Integer.parseInt (or Double.parseDouble) was given text that isn't a plain number" +
                (if (input.isNotEmpty()) ": \"$input\"." else ".") + " Spaces, letters, commas and empty text all break it.",
            fix = "Remove extra spaces with text.trim() first. If a person types the value, check it, or catch the error and ask again.",
            example = "✓ try {\n      int age = Integer.parseInt(text.trim());\n  } catch (NumberFormatException e) {\n      System.out.println(\"Please type a number\");\n  }",
        )
    },
    Rule("""^java\.time\.format\.DateTimeParseException: Text '(.*?)' could not be parsed(.*)$""") { m ->
        val text = m.groupValues[1]
        val rest = m.groupValues[2]
        val why = when {
            "Unable to obtain" in rest ->
                " The pattern doesn't say where the month is. Small mm means minutes; the month is capital MM."
            "Invalid value" in rest ->
                " One part is out of range, like month 13 or day 32."
            else ->
                " Without a formatter, LocalDate.parse only reads year-month-day, like 2022-10-17. This text has a different shape, or a typo."
        }
        Explanation(
            title = "\"$text\" couldn't be read as a date",
            meaning = "LocalDate.parse couldn't turn this text into a date.$why",
            fix = "For dates like 10/17/2022, give LocalDate.parse a formatter: DateTimeFormatter.ofPattern(\"MM/dd/yyyy\"). " +
                "Capital MM is the month, dd the day and yyyy the year.",
            example = "✓ DateTimeFormatter f = DateTimeFormatter.ofPattern(\"MM/dd/yyyy\");\n  LocalDate day = LocalDate.parse(\"10/17/2022\", f);\n\n✗ DateTimeFormatter.ofPattern(\"mm/dd/yyyy\")   // mm is minutes",
        )
    },
    Rule("""^java\.util\.InputMismatchException.*$""") {
        Explanation(
            title = "What was typed wasn't what Scanner wanted",
            meaning = "The code asked for a whole number with nextInt(), but what was typed wasn't one (for example \"abc\" or \"3.5\").",
            fix = "Type the right kind of value. To make the program safe, check first with scanner.hasNextInt().",
            example = "✓ while (!in.hasNextInt()) {\n      System.out.println(\"Please type a number\");\n      in.next();\n  }\n  int age = in.nextInt();",
        )
    },
    Rule("""^java\.util\.NoSuchElementException(?:: (.*))?$""") { m ->
        val message = m.groupValues[1]
        Explanation(
            title = if ("line" in message.lowercase()) "Scanner ran out of input" else "Asked for the next item, but there wasn't one",
            meaning = if ("line" in message.lowercase()) "nextLine() was called, but there was nothing left to read. This often happens after a Scanner was closed, or when two Scanners read from System.in."
                      else "The code asked for the next value (from a Scanner or a list), but there was nothing left.",
            fix = "Use one Scanner for System.in for the whole program, and don't close it early. Check with hasNext() or hasNextLine() before reading.",
        )
    },
    Rule("""^java\.lang\.ClassCastException: (?:class )?(\S+) cannot be cast to (?:class )?(\S+).*$""") { m ->
        val from = simpleType(m.groupValues[1])
        val to = simpleType(m.groupValues[2])
        Explanation(
            title = "${article(from).replaceFirstChar { it.uppercase() }} isn't ${article(to)}",
            meaning = "The code put ($to) in front to treat something as ${article(to)}, but it's really ${article(from)}.",
            fix = "Before doing that, check what it really is with instanceof:  if (thing instanceof $to t) { ... }",
        )
    },
    Rule("""^java\.lang\.StackOverflowError.*$""") {
        Explanation(
            title = "A method kept calling itself forever",
            meaning = "A method called itself over and over and never stopped, until Java ran out of room.",
            fix = "Add a stopping point: an if that returns without calling itself again. Make each call get closer to that stopping point.",
            example = "  static int countdown(int n) {\n✓     if (n == 0) return 0;      // stop here\n      return countdown(n - 1);\n  }",
        )
    },
    Rule("""^java\.lang\.OutOfMemoryError(?:: (.*))?$""") {
        Explanation(
            title = "The program ran out of memory",
            meaning = "It kept making new things (often adding to a list in a loop that never ends) until there was no room left.",
            fix = "Look for a loop that never stops, or one that keeps adding to a list or to some text.",
        )
    },
    Rule("""^java\.util\.ConcurrentModificationException.*$""") {
        Explanation(
            title = "A list was changed while a loop was going through it",
            meaning = "A for-each loop over a list broke, because items were added to or removed from that same list during the loop.",
            fix = "To remove items, use list.removeIf(...) instead of a loop.",
            example = "✗ for (String n : names) if (n.isEmpty()) names.remove(n);\n✓ names.removeIf(n -> n.isEmpty());",
        )
    },
    Rule("""^java\.lang\.UnsupportedOperationException.*$""") {
        Explanation(
            title = "This list can't be changed",
            meaning = "Lists made with List.of(...) or Arrays.asList(...) are locked: you can't add to them (and List.of ones can't change at all).",
            fix = "Copy it into a normal list first:  List<String> names = new ArrayList<>(List.of(\"a\", \"b\"));",
        )
    },
    Rule("""^java\.io\.FileNotFoundException(?:: (.*))?$""") { m ->
        val file = m.groupValues[1].substringBefore(" (").trim()
        Explanation(
            title = if (file.isNotEmpty()) "Couldn't find the file $file" else "Couldn't find the file",
            meaning = "The program looked for the file, but it isn't there. A short name like \"data.txt\" is looked for in the project's top folder when you run from IntelliJ.",
            fix = "Check the name and the ending (.txt). Put the file in the project's top folder, or use its full location.",
        )
    },
    Rule("""^java\.lang\.NegativeArraySizeException(?:: (.*))?$""") {
        Explanation(
            title = "Tried to make an array with a size below zero",
            meaning = "new int[n] needs n to be 0 or more, but it was negative.",
            fix = "Check how the size is worked out before making the array.",
        )
    },
    Rule("""^java\.lang\.IllegalArgumentException(?:: (.*))?$""") { m ->
        Explanation(
            title = "A method was given a value it won't take",
            meaning = "The method looked at the value it was given and refused it." + (m.groupValues[1].takeIf { it.isNotEmpty() }?.let { " Its reason: \"$it\"." } ?: ""),
            fix = "Look at the value given on the line shown, and at what that method allows.",
        )
    },
    Rule("""^java\.lang\.IllegalStateException(?:: (.*))?$""") { m ->
        Explanation(
            title = "Something was used at the wrong time",
            meaning = "Something wasn't ready yet, or was already finished (like reading from a Scanner after closing it)." +
                (m.groupValues[1].takeIf { it.isNotEmpty() }?.let { " Java said: \"$it\"." } ?: ""),
            fix = "Check the order of steps: set things up before using them, and don't use them after closing them.",
        )
    },
)

/** Messages the program prints instead of running at all. */
val LAUNCH_RULES: List<Rule> = listOf(
    Rule("""Main method not found in class (\S+?),""") { m ->
        Explanation(
            title = "${m.groupValues[1]} has no main method to start from",
            meaning = "A Java program starts at a method that must be written exactly like  public static void main(String[] args).",
            fix = "Add that method to the class, or run the class that has it (the green ▶ next to main).",
            example = "✓ public static void main(String[] args) {\n      System.out.println(\"Hello\");\n  }",
        )
    },
    Rule("""Could not find or load main class (\S+)""") { m ->
        Explanation(
            title = "Java couldn't find the class ${m.groupValues[1]} to start",
            meaning = "The run button is set up for a class that isn't there anymore. This often happens after renaming or moving a file.",
            fix = "Open the file with main and click the green ▶ next to it. That sets up the run button again.",
        )
    },
)

/** Explanation for a crash nobody wrote a rule for: still names it and points at the line. */
fun genericCrash(exception: String, message: String?) = Explanation(
    title = "The program stopped with ${simpleType(exception)}",
    meaning = "Java hit a problem and couldn't keep going" + (message?.takeIf { it.isNotBlank() }?.let { ": \"$it\"." } ?: "."),
    fix = "Go to the line shown and check the values used there. Tip: click in the left margin of that line to add a red dot, then run with the bug icon. The program pauses there and shows each value.",
)

fun explainCompileError(message: String): Explanation? =
    COMPILE_RULES.firstNotNullOfOrNull { rule -> rule.regex.find(message.trim())?.let(rule.explain) }

fun explainCrash(exception: String, message: String?): Explanation {
    val line = if (message.isNullOrEmpty()) exception else "$exception: $message"
    return CRASH_RULES.firstNotNullOfOrNull { rule -> rule.regex.find(line)?.let(rule.explain) } ?: genericCrash(exception, message)
}
