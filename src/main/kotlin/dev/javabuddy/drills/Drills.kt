package dev.javabuddy.drills

/**
 * A group of drills in the Drills tab, in course order. [workbook] is the course workbook it comes
 * from (see [WORKBOOKS]). [soon] marks topics the course hasn't reached yet.
 */
enum class Topic(val title: String, val short: String, val workbook: Int, val soon: Boolean = false) {
    VARIABLES("Variables and math", "Math", 1),
    INPUT("Scanner and printf", "Input", 1),
    DECISIONS("if, else and boolean", "if / else", 1),
    STATIC("Static methods", "Methods", 1),
    SWITCH("switch", "switch", 1),
    STRINGS("Text (Strings)", "Text", 2),
    CONVERTING("Text into numbers and dates", "Converting", 2),
    BUILDER("StringBuilder", "StringBuilder", 2),
    CLASSES("Classes and objects", "Classes", 2),
    OVERLOADING("Same name, different inputs (overloading)", "Overloading", 2),
    LOOPS("Loops", "Loops", 2),
    ARRAYS("Arrays", "Arrays", 2),
}

/** The course workbooks, by number, as the Drills tab names them. */
val WORKBOOKS = mapOf(1 to "Workbook 1: Intro to Java", 2 to "Workbook 2: Strings, Loops and Arrays")

/**
 * One hidden test. [call] is Java code run on the drill's class, like `area(3, 4)`; for [Prints]
 * it may use `in`, a Scanner reading [input]. [why] is shown when this test fails. With [onClass]
 * false, [call] is used as it is, for drills that make objects: `new Pet("Rex", 3).getName()`.
 */
sealed class Check {
    abstract val call: String
    abstract val label: String
    abstract val why: String?
    abstract val onClass: Boolean

    /** [call] must give back [expected], a Java expression such as `12`, `"K.B."` or `Math.PI * 4`. */
    data class Returns(
        override val call: String,
        val expected: String,
        override val why: String? = null,
        override val label: String = call,
        override val onClass: Boolean = true,
    ) : Check()

    /** [call] must print [expected] (or, with [contains], print something that includes it). */
    data class Prints(
        override val call: String,
        val expected: String,
        val input: String = "",
        val contains: Boolean = false,
        override val why: String? = null,
        override val label: String = call,
        override val onClass: Boolean = true,
    ) : Check()
}

/**
 * A small exercise. The learner edits [starter] in a scratch file named `[className].java`, and
 * Java Buddy runs [checks] against it. [signature] is the first line of the method they finish or
 * write, shown when Java Buddy can't call it.
 */
data class Drill(
    val className: String,
    val topic: Topic,
    val title: String,
    val task: String,
    val signature: String,
    val starter: String,
    val hint: String,
    val checks: List<Check>,
) {
    val fileName get() = "$className.java"

    /** True when the checks make objects instead of calling static methods on the class. */
    val usesObjects get() = checks.any { !it.onClass }
}

/** Starter for a drill where the method is already there and only its inside is missing. */
private fun fillIn(className: String, comment: String, signature: String, defaultReturn: String?, tryIt: List<String>, imports: String = ""): String {
    val body = if (defaultReturn == null) "        // Your code here" else "        // Your code here\n        return $defaultReturn;   // <- replace this"
    val calls = tryIt.joinToString("\n") { "        $it" }
    return """
        |${imports}public class $className {
        |
        |    // $comment
        |    $signature {
        |$body
        |    }
        |
        |    // To try it yourself, press the green ▶ next to main.
        |    // To test it, press "Check my answer" at the top of this file.
        |    public static void main(String[] args) {
        |$calls
        |    }
        |}
        |""".trimMargin()
}

/** Starter for a drill where the learner writes the whole method themselves. */
private fun writeYourOwn(className: String, signature: String, what: String, mainBody: List<String>): String {
    val calls = mainBody.joinToString("\n") { "        $it" }
    return """
        |public class $className {
        |
        |    public static void main(String[] args) {
        |$calls
        |    }
        |
        |    // Write your method below, inside the class but outside main's { }.
        |    // $what
        |    // Its first line should be:
        |    //     $signature {
        |
        |}
        |""".trimMargin()
}

private fun print(call: String) = "System.out.println($call);"

/**
 * A check [call] that runs a few statements, then gives back [result]: for objects that need
 * setting up first. Use it with `onClass = false` and a readable `label`.
 */
private fun steps(vararg statements: String, result: String) =
    "((java.util.concurrent.Callable<Object>) () -> { ${statements.joinToString(" ")} return $result; }).call()"

/** The drills in course order: grouped by [Topic], in the order written below within a topic. */
val DRILLS: List<Drill> = listOf(
    // ---------------------------------------------------------------- Static methods
    Drill(
        "SayHello", Topic.STATIC, "Your first static method",
        task = "Write a static method called sayHello that prints \"Hello!\". main already calls it.",
        signature = "static void sayHello()",
        starter = writeYourOwn("SayHello", "static void sayHello()", "It prints \"Hello!\" and gives nothing back (that's what void means).", listOf("sayHello();   // runs your method")),
        hint = "Inside the method's { }, use System.out.println(\"Hello!\"); . The red line under sayHello() in main goes away once the method exists.",
        checks = listOf(Check.Prints("sayHello()", "Hello!", why = "It should print exactly \"Hello!\", with a capital H and an exclamation mark.")),
    ),
    Drill(
        "Square", Topic.STATIC, "Give back a value",
        task = "Write a static method square that takes a whole number and gives back that number times itself.",
        signature = "static int square(int n)",
        starter = writeYourOwn("Square", "static int square(int n)", "It gives back n times n, using return.", listOf(print("square(5)") + "   // should print 25")),
        hint = "int before the name means it gives back a whole number. Inside, write  return n * n;",
        checks = listOf(
            Check.Returns("square(5)", "25"),
            Check.Returns("square(0)", "0"),
            Check.Returns("square(-3)", "9", why = "A negative times a negative is positive: -3 * -3 is 9."),
        ),
    ),
    Drill(
        "AddTwo", Topic.STATIC, "Two values in, one out",
        task = "Write a static method add that takes two whole numbers and gives back their total.",
        signature = "static int add(int a, int b)",
        starter = writeYourOwn("AddTwo", "static int add(int a, int b)", "It gives back a + b.", listOf(print("add(2, 3)") + "   // should print 5")),
        hint = "Values go in the brackets, separated by a comma, each with its type: (int a, int b).",
        checks = listOf(Check.Returns("add(2, 3)", "5"), Check.Returns("add(-1, 1)", "0"), Check.Returns("add(100, 250)", "350")),
    ),
    Drill(
        "Greeting", Topic.STATIC, "A method that gives back text",
        task = "Write a static method greeting that takes a name and gives back \"Hi, \" + the name + \"!\". For Kim it gives back Hi, Kim!",
        signature = "static String greeting(String name)",
        starter = writeYourOwn("Greeting", "static String greeting(String name)", "It gives back text like Hi, Kim!", listOf(print("greeting(\"Kim\")") + "   // should print Hi, Kim!")),
        hint = "Join text with +:  return \"Hi, \" + name + \"!\";",
        checks = listOf(
            Check.Returns("greeting(\"Kim\")", "\"Hi, Kim!\"", why = "Check the comma, the space after it, and the ! at the end."),
            Check.Returns("greeting(\"Sam\")", "\"Hi, Sam!\""),
        ),
    ),
    Drill(
        "CanVote", Topic.STATIC, "A yes/no method",
        task = "Write a static method canVote that takes an age and gives back true if the person is 18 or older, otherwise false.",
        signature = "static boolean canVote(int age)",
        starter = writeYourOwn("CanVote", "static boolean canVote(int age)", "It gives back true or false.", listOf(print("canVote(20)") + "   // should print true", print("canVote(15)") + "   // should print false")),
        hint = "A comparison already is true or false, so you can write  return age >= 18;",
        checks = listOf(
            Check.Returns("canVote(18)", "true", why = "18-year-olds can vote. Use >= (18 or more), not > (more than 18)."),
            Check.Returns("canVote(17)", "false"),
            Check.Returns("canVote(40)", "true"),
        ),
    ),
    Drill(
        "FixTheStatic", Topic.STATIC, "Fix the static mistake",
        task = "main can't call greet yet: there's a red error. Fix greet's first line so main can call it.",
        signature = "static void greet()",
        starter = """
            |public class FixTheStatic {
            |
            |    public static void main(String[] args) {
            |        greet();
            |    }
            |
            |    void greet() {          // <- something is missing on this line
            |        System.out.println("Good morning!");
            |    }
            |}
            |""".trimMargin(),
        hint = "main has the word static. A method main calls directly needs static too.",
        checks = listOf(Check.Prints("greet()", "Good morning!")),
    ),
    Drill(
        "RingArea", Topic.STATIC, "A method that uses another method",
        task = "Finish circleArea (pi times radius times radius). Then finish ringArea: the big circle's area minus the small circle's area, using circleArea twice.",
        signature = "static double ringArea(double outer, double inner)",
        starter = """
            |public class RingArea {
            |
            |    // The area of a circle: Math.PI * radius * radius
            |    static double circleArea(double radius) {
            |        // Your code here
            |        return 0;   // <- replace this
            |    }
            |
            |    // The area of a ring (like a donut): the big circle minus the hole.
            |    // Call circleArea(...) twice instead of writing the math again.
            |    static double ringArea(double outer, double inner) {
            |        // Your code here
            |        return 0;   // <- replace this
            |    }
            |
            |    // To try it yourself, press the green ▶ next to main.
            |    // To test it, press "Check my answer" at the top of this file.
            |    public static void main(String[] args) {
            |        System.out.println(ringArea(2, 1));   // should print about 9.42
            |    }
            |}
            |""".trimMargin(),
        hint = "Inside ringArea:  return circleArea(outer) - circleArea(inner);",
        checks = listOf(
            Check.Returns("circleArea(1)", "Math.PI"),
            Check.Returns("circleArea(2)", "Math.PI * 4"),
            Check.Returns("ringArea(2, 1)", "Math.PI * 3", why = "Big circle minus small circle: circleArea(outer) - circleArea(inner)."),
            Check.Returns("ringArea(5, 5)", "0.0", why = "When both circles are the same size, there's no ring left: the answer is 0."),
        ),
    ),
    Drill(
        "SandwichPrice", Topic.STATIC, "Sandwich price, as a method",
        task = "Like your sandwich shop: a regular sandwich costs 5.45 and a large one 8.95. Loaded adds 1.00 to a regular and 1.75 to a large. Give back the price.",
        signature = "static double sandwichPrice(String size, boolean loaded)",
        starter = fillIn(
            "SandwichPrice", "size is \"regular\" or \"large\". loaded is true or false.",
            "static double sandwichPrice(String size, boolean loaded)", "0.0",
            listOf(print("sandwichPrice(\"large\", true)") + "   // should print 10.7"),
        ),
        hint = "Start with the base price using if/else on the size, then add the extra if loaded is true. Compare text with equalsIgnoreCase, not ==.",
        checks = listOf(
            Check.Returns("sandwichPrice(\"regular\", false)", "5.45"),
            Check.Returns("sandwichPrice(\"large\", false)", "8.95"),
            Check.Returns("sandwichPrice(\"regular\", true)", "6.45"),
            Check.Returns("sandwichPrice(\"large\", true)", "10.70"),
            Check.Returns("sandwichPrice(\"LARGE\", false)", "8.95", why = "People might type LARGE or Large. equalsIgnoreCase(\"large\") matches all of them; equals and == don't."),
        ),
    ),

    // ---------------------------------------------------------------- Variables and math
    Drill(
        "RectangleArea", Topic.VARIABLES, "Area of a rectangle",
        task = "Finish area so it gives back width times height.",
        signature = "static int area(int width, int height)",
        starter = fillIn("RectangleArea", "Gives back the area of a rectangle: width times height.", "static int area(int width, int height)", "0", listOf(print("area(3, 4)") + "   // should print 12")),
        hint = "* means times:  return width * height;",
        checks = listOf(Check.Returns("area(3, 4)", "12"), Check.Returns("area(10, 1)", "10"), Check.Returns("area(0, 7)", "0")),
    ),
    Drill(
        "Average", Topic.VARIABLES, "Average of three numbers",
        task = "Finish average so it gives back the average of three whole numbers, with decimals.",
        signature = "static double average(int a, int b, int c)",
        starter = fillIn("Average", "Gives back the average of a, b and c. For 1, 2 and 2 that's about 1.67.", "static double average(int a, int b, int c)", "0.0", listOf(print("average(1, 2, 2)") + "   // should print 1.666..")),
        hint = "Add them up, then divide by 3.0 (not 3), so Java keeps the decimals.",
        checks = listOf(
            Check.Returns("average(2, 4, 6)", "4.0"),
            Check.Returns("average(1, 2, 2)", "5.0 / 3", why = "If you got 1.0, Java divided whole numbers and threw the decimals away. Divide by 3.0 instead of 3."),
            Check.Returns("average(0, 0, 1)", "1.0 / 3"),
        ),
    ),
    Drill(
        "HoursAndMinutes", Topic.VARIABLES, "Minutes to hours",
        task = "Turn a number of minutes into text like 2h 15m.",
        signature = "static String hoursAndMinutes(int minutes)",
        starter = fillIn("HoursAndMinutes", "135 minutes gives back \"2h 15m\".", "static String hoursAndMinutes(int minutes)", "\"\"", listOf(print("hoursAndMinutes(135)") + "   // should print 2h 15m")),
        hint = "minutes / 60 gives the whole hours. minutes % 60 gives the minutes left over.",
        checks = listOf(
            Check.Returns("hoursAndMinutes(135)", "\"2h 15m\"", why = "/ gives the whole hours (2) and % gives what's left over (15)."),
            Check.Returns("hoursAndMinutes(60)", "\"1h 0m\""),
            Check.Returns("hoursAndMinutes(5)", "\"0h 5m\""),
        ),
    ),
    Drill(
        "PriceWithTax", Topic.VARIABLES, "Add the tax",
        task = "Give back the price with 8% tax added.",
        signature = "static double withTax(double price)",
        starter = fillIn("PriceWithTax", "Gives back the price plus 8% tax. 10.00 becomes 10.80.", "static double withTax(double price)", "0.0", listOf(print("withTax(10.0)") + "   // should print 10.8")),
        hint = "The price plus 8% is the price times 1.08.",
        checks = listOf(
            Check.Returns("withTax(10.0)", "10.8", why = "Multiply by 1.08: that's the price plus 8%."),
            Check.Returns("withTax(5.45)", "5.886"),
            Check.Returns("withTax(0)", "0.0"),
        ),
    ),
    Drill(
        "Bigger", Topic.VARIABLES, "The bigger number",
        task = "Give back whichever of the two numbers is bigger. Try Math.max.",
        signature = "static int bigger(int a, int b)",
        starter = fillIn("Bigger", "Gives back the bigger of a and b.", "static int bigger(int a, int b)", "0", listOf(print("bigger(3, 8)") + "   // should print 8")),
        hint = "Math.max(a, b) gives back the bigger one.",
        checks = listOf(Check.Returns("bigger(3, 8)", "8"), Check.Returns("bigger(8, 3)", "8"), Check.Returns("bigger(-1, -5)", "-1")),
    ),
    Drill(
        "Fahrenheit", Topic.VARIABLES, "Celsius to Fahrenheit",
        task = "Turn a Celsius temperature into Fahrenheit: multiply by 9, divide by 5, then add 32.",
        signature = "static double toFahrenheit(double celsius)",
        starter = fillIn("Fahrenheit", "0 °C is 32 °F, and 100 °C is 212 °F.", "static double toFahrenheit(double celsius)", "0.0", listOf(print("toFahrenheit(100)") + "   // should print 212.0")),
        hint = "return celsius * 9 / 5 + 32;  Java does * and / before +, just like in math class.",
        checks = listOf(
            Check.Returns("toFahrenheit(0)", "32.0"),
            Check.Returns("toFahrenheit(100)", "212.0"),
            Check.Returns("toFahrenheit(37)", "98.6", why = "If you got 69.0, 9 / 5 was worked out first with whole numbers (giving 1). Multiply celsius by 9 first, or write 9.0 / 5."),
            Check.Returns("toFahrenheit(-40)", "-40.0"),
        ),
    ),

    // ---------------------------------------------------------------- Strings
    Drill(
        "Shout", Topic.STRINGS, "Shout it",
        task = "Give back the text in capital letters, with ! on the end. hello becomes HELLO!",
        signature = "static String shout(String text)",
        starter = fillIn("Shout", "\"hello\" gives back \"HELLO!\"", "static String shout(String text)", "\"\"", listOf(print("shout(\"hello\")") + "   // should print HELLO!")),
        hint = "text.toUpperCase() gives the capital-letter version. Then add \"!\" with +.",
        checks = listOf(Check.Returns("shout(\"hello\")", "\"HELLO!\""), Check.Returns("shout(\"Java\")", "\"JAVA!\"")),
    ),
    Drill(
        "FirstLetter", Topic.STRINGS, "First letter",
        task = "Give back the first letter of a word.",
        signature = "static char firstLetter(String word)",
        starter = fillIn("FirstLetter", "\"Kim\" gives back 'K'. A char holds one letter, in single quotes.", "static char firstLetter(String word)", "' '", listOf(print("firstLetter(\"Kim\")") + "   // should print K")),
        hint = "word.charAt(0) gives the first letter. Counting starts at 0.",
        checks = listOf(Check.Returns("firstLetter(\"Kim\")", "'K'", why = "Letters are counted from 0, so the first one is charAt(0)."), Check.Returns("firstLetter(\"java\")", "'j'")),
    ),
    Drill(
        "LastLetter", Topic.STRINGS, "Last letter",
        task = "Give back the last letter of a word.",
        signature = "static char lastLetter(String word)",
        starter = fillIn("LastLetter", "\"cat\" gives back 't'.", "static char lastLetter(String word)", "' '", listOf(print("lastLetter(\"cat\")") + "   // should print t")),
        hint = "The last letter is at word.length() - 1, because counting starts at 0.",
        checks = listOf(
            Check.Returns("lastLetter(\"cat\")", "'t'", why = "\"cat\" has letters 0, 1 and 2. length() is 3, so the last one is at length() - 1."),
            Check.Returns("lastLetter(\"a\")", "'a'"),
        ),
    ),
    Drill(
        "Initials", Topic.STRINGS, "Initials",
        task = "Give back someone's initials, in capitals, each followed by a dot: Kim Ball gives K.B.",
        signature = "static String initials(String first, String last)",
        starter = fillIn("Initials", "\"Kim\", \"Ball\" gives back \"K.B.\"", "static String initials(String first, String last)", "\"\"", listOf(print("initials(\"Kim\", \"Ball\")") + "   // should print K.B.")),
        hint = "first.substring(0, 1) is the first letter as text. .toUpperCase() makes it a capital. Join the parts with +.",
        checks = listOf(
            Check.Returns("initials(\"Kim\", \"Ball\")", "\"K.B.\""),
            Check.Returns("initials(\"ada\", \"lovelace\")", "\"A.L.\"", why = "Names typed in small letters still need capital initials: use toUpperCase()."),
        ),
    ),
    Drill(
        "SaysYes", Topic.STRINGS, "Did they say yes?",
        task = "Give back true if the answer is yes, in any mix of capitals (yes, YES, Yes). Anything else gives back false.",
        signature = "static boolean saysYes(String answer)",
        starter = fillIn("SaysYes", "\"yes\", \"YES\" and \"Yes\" give back true. \"no\" gives back false.", "static boolean saysYes(String answer)", "false", listOf(print("saysYes(\"YES\")") + "   // should print true")),
        hint = "answer.equalsIgnoreCase(\"yes\") is already true or false, so you can return it straight away.",
        checks = listOf(
            Check.Returns("saysYes(\"yes\")", "true"),
            Check.Returns("saysYes(\"YES\")", "true", why = "equals cares about capital letters. equalsIgnoreCase doesn't."),
            Check.Returns("saysYes(\"Yes\")", "true"),
            Check.Returns("saysYes(\"no\")", "false"),
            Check.Returns("saysYes(\"yes please\")", "false", why = "Only the exact word yes counts, so use equalsIgnoreCase, not contains."),
        ),
    ),
    Drill(
        "BeforeDash", Topic.STRINGS, "Text before the dash",
        task = "Product codes look like ACME-12213. Give back the part before the dash: ACME.",
        signature = "static String vendor(String code)",
        starter = fillIn("BeforeDash", "\"ACME-12213\" gives back \"ACME\".", "static String vendor(String code)", "\"\"", listOf(print("vendor(\"ACME-12213\")") + "   // should print ACME")),
        hint = "code.indexOf(\"-\") tells you where the dash is (4 in ACME-12213). Then code.substring(0, dash) gives everything before it. The end number is never included.",
        checks = listOf(
            Check.Returns("vendor(\"ACME-12213\")", "\"ACME\"", why = "substring(0, dash) stops just before the dash. If you got ACME-, the end number was one too big."),
            Check.Returns("vendor(\"XY-9\")", "\"XY\""),
            Check.Returns("vendor(\"LONGNAME-1\")", "\"LONGNAME\"", why = "Find the dash with indexOf instead of using a fixed number: codes can be any length."),
        ),
    ),
    Drill(
        "AfterDash", Topic.STRINGS, "Text after the dash",
        task = "Now the other half: give back the part after the dash. ACME-12213 gives back 12213.",
        signature = "static String productNumber(String code)",
        starter = fillIn("AfterDash", "\"ACME-12213\" gives back \"12213\".", "static String productNumber(String code)", "\"\"", listOf(print("productNumber(\"ACME-12213\")") + "   // should print 12213")),
        hint = "Find the dash with indexOf, then code.substring(dash + 1). With only one number, substring goes all the way to the end.",
        checks = listOf(
            Check.Returns("productNumber(\"ACME-12213\")", "\"12213\"", why = "If you got -12213, start one past the dash: substring(dash + 1)."),
            Check.Returns("productNumber(\"XY-9\")", "\"9\""),
        ),
    ),
    Drill(
        "WithoutEnds", Topic.STRINGS, "Cut off the ends",
        task = "Give back the word without its first and last letter. Pizza gives back izz.",
        signature = "static String withoutEnds(String word)",
        starter = fillIn("WithoutEnds", "\"Pizza\" gives back \"izz\". \"cat\" gives back \"a\".", "static String withoutEnds(String word)", "\"\"", listOf(print("withoutEnds(\"Pizza\")") + "   // should print izz")),
        hint = "word.substring(1, word.length() - 1). Start at 1 to skip the first letter. The end number is never included, so length() - 1 leaves off the last letter.",
        checks = listOf(
            Check.Returns("withoutEnds(\"Pizza\")", "\"izz\"", why = "substring(start, end) gives back end - start letters. For Pizza that's substring(1, 4): 3 letters."),
            Check.Returns("withoutEnds(\"Burger\")", "\"urge\""),
            Check.Returns("withoutEnds(\"cat\")", "\"a\""),
            Check.Returns("withoutEnds(\"hi\")", "\"\"", why = "With 2 letters there's nothing in the middle: substring(1, 1) gives back \"\" (empty)."),
        ),
    ),
    Drill(
        "FullName", Topic.STRINGS, "Build a full name",
        task = "Like the full-name exercise: join the parts of a name. Leave out the middle name or suffix when it's empty, and put \", \" before a suffix: Glen C. Williams, PhD. Trim extra spaces first.",
        signature = "static String fullName(String first, String middle, String last, String suffix)",
        starter = fillIn(
            "FullName", "(\"Glen\", \"\", \"Williams\", \"PhD\") gives back \"Glen Williams, PhD\".",
            "static String fullName(String first, String middle, String last, String suffix)", "\"\"",
            listOf(print("fullName(\"Glen\", \"C.\", \"Williams\", \"\")") + "   // should print Glen C. Williams"),
        ),
        hint = "Trim each part first. Start with first. If middle isn't empty (!middle.isEmpty()), add a space and the middle name. Add a space and last. If suffix isn't empty, add \", \" and the suffix.",
        checks = listOf(
            Check.Returns("fullName(\"Glen\", \"\", \"Williams\", \"\")", "\"Glen Williams\"", why = "With no middle name there should be just one space between the first and last name."),
            Check.Returns("fullName(\"Glen\", \"C.\", \"Williams\", \"\")", "\"Glen C. Williams\""),
            Check.Returns("fullName(\"Glen\", \"\", \"Williams\", \"PhD\")", "\"Glen Williams, PhD\"", why = "A suffix comes after a comma and a space: Glen Williams, PhD"),
            Check.Returns("fullName(\"  Glen \", \"C.\", \"Williams  \", \" PhD\")", "\"Glen C. Williams, PhD\"", why = "Use trim() on each part to remove spaces at the start and end."),
            Check.Returns("fullName(\"Glen\", \"  \", \"Williams\", \"\")", "\"Glen Williams\"", why = "A middle name of only spaces is empty once it's trimmed. Trim first, then check isEmpty()."),
        ),
    ),
    Drill(
        "LastName", Topic.STRINGS, "Pick out the last name",
        task = "Like the full-name parser: a name is first last or first middle last. Give back the last name. Trim it first in case of extra spaces.",
        signature = "static String lastName(String name)",
        starter = fillIn("LastName", "\"Dana L. Wyatt\" and \"Dana Wyatt\" both give back \"Wyatt\".", "static String lastName(String name)", "\"\"", listOf(print("lastName(\"Dana L. Wyatt\")") + "   // should print Wyatt")),
        hint = "String[] parts = name.trim().split(\" \"); gives the words one by one. The last word is parts[parts.length - 1].",
        checks = listOf(
            Check.Returns("lastName(\"Dana L. Wyatt\")", "\"Wyatt\""),
            Check.Returns("lastName(\"Dana Wyatt\")", "\"Wyatt\"", why = "With 2 words the last name is parts[1]; with 3 it's parts[2]. parts[parts.length - 1] works for both."),
            Check.Returns("lastName(\"  Dana Wyatt  \")", "\"Wyatt\"", why = "Trim before you split. Spaces at the end make an empty last word."),
        ),
    ),
    Drill(
        "LastFirst", Topic.STRINGS, "Last name first",
        task = "Like the theater reservations: turn \"Dana Wyatt\" into \"Wyatt, Dana\".",
        signature = "static String lastFirst(String name)",
        starter = fillIn("LastFirst", "\"Dana Wyatt\" gives back \"Wyatt, Dana\".", "static String lastFirst(String name)", "\"\"", listOf(print("lastFirst(\"Dana Wyatt\")") + "   // should print Wyatt, Dana")),
        hint = "Split at the space: parts[0] is the first name and parts[1] the last. Join them as parts[1] + \", \" + parts[0].",
        checks = listOf(
            Check.Returns("lastFirst(\"Dana Wyatt\")", "\"Wyatt, Dana\"", why = "The last name comes first, then a comma and a space, then the first name."),
            Check.Returns("lastFirst(\"Glen Williams\")", "\"Williams, Glen\""),
        ),
    ),
    Drill(
        "Quote", Topic.STRINGS, "Quotes inside text",
        task = "Give back text with quote marks in it: says(\"Big Tex\", \"Howdy\") gives back Big Tex says \"Howdy\"!",
        signature = "static String says(String who, String words)",
        starter = fillIn("Quote", "(\"Big Tex\", \"Howdy\") gives back: Big Tex says \"Howdy\"!", "static String says(String who, String words)", "\"\"", listOf(print("says(\"Big Tex\", \"Howdy\")") + "   // should print Big Tex says \"Howdy\"!")),
        hint = "Inside text, \\\" is a quote mark that's part of the text: who + \" says \\\"\" + words + \"\\\"!\"",
        checks = listOf(
            Check.Returns("says(\"Big Tex\", \"Howdy\")", "\"Big Tex says \\\"Howdy\\\"!\"", why = "The quote marks go around Howdy, inside the text. Write each one as \\\" so Java doesn't end the text there."),
            Check.Returns("says(\"Kim\", \"Hi\")", "\"Kim says \\\"Hi\\\"!\""),
        ),
    ),

    // ---------------------------------------------------------------- if / else / boolean
    Drill(
        "IsEven", Topic.DECISIONS, "Even or odd?",
        task = "Give back true if the number is even.",
        signature = "static boolean isEven(int n)",
        starter = fillIn("IsEven", "4 gives back true, 7 gives back false.", "static boolean isEven(int n)", "false", listOf(print("isEven(4)") + "   // should print true")),
        hint = "n % 2 is what's left after dividing by 2. For even numbers that's 0.",
        checks = listOf(
            Check.Returns("isEven(4)", "true"), Check.Returns("isEven(7)", "false"),
            Check.Returns("isEven(0)", "true", why = "0 is even: 0 % 2 is 0."),
            Check.Returns("isEven(-2)", "true"),
        ),
    ),
    Drill(
        "Grade", Topic.DECISIONS, "Letter grade",
        task = "Give back the letter grade: 90 or more is A, 80 or more B, 70 or more C, 60 or more D, below that F.",
        signature = "static char grade(int score)",
        starter = fillIn("Grade", "95 gives back 'A', 72 gives back 'C'.", "static char grade(int score)", "' '", listOf(print("grade(95)") + "   // should print A")),
        hint = "Use if / else if / else, starting from the top: if (score >= 90) return 'A'; else if (score >= 80) ...",
        checks = listOf(
            Check.Returns("grade(95)", "'A'"),
            Check.Returns("grade(90)", "'A'", why = "90 should be an A: use >= 90, not > 90."),
            Check.Returns("grade(85)", "'B'"), Check.Returns("grade(72)", "'C'"), Check.Returns("grade(65)", "'D'"),
            Check.Returns("grade(40)", "'F'"),
        ),
    ),
    Drill(
        "TicketPrice", Topic.DECISIONS, "Movie ticket price",
        task = "A ticket costs 12.00. Kids under 13 pay 6.00, and people 65 or older pay 8.00.",
        signature = "static double ticketPrice(int age)",
        starter = fillIn("TicketPrice", "Gives back the price for someone of this age.", "static double ticketPrice(int age)", "0.0", listOf(print("ticketPrice(10)") + "   // should print 6.0")),
        hint = "if (age < 13) ... else if (age >= 65) ... else ...",
        checks = listOf(
            Check.Returns("ticketPrice(10)", "6.0"),
            Check.Returns("ticketPrice(13)", "12.0", why = "13 isn't under 13: use < 13, not <= 13."),
            Check.Returns("ticketPrice(30)", "12.0"),
            Check.Returns("ticketPrice(65)", "8.0", why = "65 counts as 65 or older: use >= 65."),
            Check.Returns("ticketPrice(80)", "8.0"),
        ),
    ),

    // ---------------------------------------------------------------- switch
    Drill(
        "DayName", Topic.SWITCH, "Day of the week",
        task = "Give back the day's name: 1 is Monday through 7 is Sunday. Any other number gives back Unknown.",
        signature = "static String dayName(int day)",
        starter = fillIn("DayName", "1 gives back \"Monday\", 7 gives back \"Sunday\".", "static String dayName(int day)", "\"\"", listOf(print("dayName(5)") + "   // should print Friday")),
        hint = "switch (day) { case 1: return \"Monday\"; ... default: return \"Unknown\"; }",
        checks = listOf(
            Check.Returns("dayName(1)", "\"Monday\""), Check.Returns("dayName(5)", "\"Friday\""), Check.Returns("dayName(7)", "\"Sunday\""),
            Check.Returns("dayName(9)", "\"Unknown\"", why = "Add a default case for any other number."),
        ),
    ),
    Drill(
        "DrinkPrice", Topic.SWITCH, "Drink sizes",
        task = "small costs 1.50, medium 2.00, large 2.50. Any other size gives back 0.0.",
        signature = "static double drinkPrice(String size)",
        starter = fillIn("DrinkPrice", "\"medium\" gives back 2.0.", "static double drinkPrice(String size)", "0.0", listOf(print("drinkPrice(\"medium\")") + "   // should print 2.0")),
        hint = "switch works with text too: case \"small\": return 1.50;",
        checks = listOf(
            Check.Returns("drinkPrice(\"small\")", "1.5"), Check.Returns("drinkPrice(\"medium\")", "2.0"), Check.Returns("drinkPrice(\"large\")", "2.5"),
            Check.Returns("drinkPrice(\"huge\")", "0.0", why = "Add a default that gives back 0.0."),
        ),
    ),

    // ---------------------------------------------------------------- Scanner and printf
    Drill(
        "Money", Topic.INPUT, "Show money with 2 decimals",
        task = "Give back the amount as money text with exactly 2 decimals: 5 becomes $5.00.",
        signature = "static String money(double amount)",
        starter = fillIn("Money", "12.5 gives back \"$12.50\".", "static String money(double amount)", "\"\"", listOf(print("money(12.5)") + "   // should print \$12.50")),
        hint = "String.format works like printf but gives back the text:  String.format(\"$%.2f\", amount)",
        checks = listOf(
            Check.Returns("money(5)", "\"$5.00\"", why = "%.2f always shows exactly 2 decimals."),
            Check.Returns("money(12.5)", "\"$12.50\""),
            Check.Returns("money(8.954)", "\"$8.95\""),
        ),
    ),
    Drill(
        "HelloName", Topic.INPUT, "Ask for a name",
        task = "Ask \"What's your name? \", read a whole line with the Scanner, then print Hello, NAME!",
        signature = "static void run(Scanner in)",
        starter = fillIn(
            "HelloName", "Ask for a name, read it with in.nextLine(), then print Hello, NAME!",
            "static void run(Scanner in)", null,
            listOf("run(new Scanner(System.in));   // type a name in the Run window"),
            imports = "import java.util.Scanner;\n\n",
        ),
        hint = "String name = in.nextLine();  then  System.out.println(\"Hello, \" + name + \"!\");",
        checks = listOf(
            Check.Prints("run(in)", "Hello, Kim!", input = "Kim\n", contains = true, label = "typing Kim", why = "After reading the name, print exactly Hello, Kim! (comma, space and !)."),
            Check.Prints("run(in)", "Hello, Sam!", input = "Sam\n", contains = true, label = "typing Sam"),
        ),
    ),
    Drill(
        "AddInputs", Topic.INPUT, "Add two typed numbers",
        task = "Read two whole numbers with the Scanner and print Total: and their sum, like Total: 7",
        signature = "static void run(Scanner in)",
        starter = fillIn(
            "AddInputs", "Read two numbers with in.nextInt(), then print \"Total: \" and their sum.",
            "static void run(Scanner in)", null,
            listOf("run(new Scanner(System.in));   // type two numbers in the Run window"),
            imports = "import java.util.Scanner;\n\n",
        ),
        hint = "int a = in.nextInt(); int b = in.nextInt(); then print \"Total: \" + (a + b). The brackets make Java add before joining.",
        checks = listOf(
            Check.Prints("run(in)", "Total: 7", input = "3\n4\n", contains = true, label = "typing 3 and 4", why = "If you see Total: 34, Java joined the numbers as text. Put the sum in brackets: \"Total: \" + (a + b)."),
            Check.Prints("run(in)", "Total: 30", input = "10 20\n", contains = true, label = "typing 10 and 20"),
        ),
    ),

    // ---------------------------------------------------------------- Text into numbers and dates
    Drill(
        "LineTotal", Topic.CONVERTING, "Price from a line of text",
        task = "A line looks like id|description|quantity|price, for example 111|Hot Chocolate|21|4.99. Give back quantity times price.",
        signature = "static double lineTotal(String line)",
        starter = fillIn("LineTotal", "\"111|Hot Chocolate (12-count)|21|4.99\" gives back 21 * 4.99, about 104.79.", "static double lineTotal(String line)", "0.0", listOf(print("lineTotal(\"111|Hot Chocolate (12-count)|21|4.99\")") + "   // should print about 104.79")),
        hint = "line.split(\"\\\\|\") cuts the line at each |. The | needs \\\\ in front, because split treats | as a special symbol. Then Integer.parseInt(parts[2]) and Double.parseDouble(parts[3]) turn the text into numbers.",
        checks = listOf(
            Check.Returns("lineTotal(\"111|Hot Chocolate (12-count)|21|4.99\")", "104.79", why = "Split with split(\"\\\\|\"). A plain split(\"|\") cuts between every letter."),
            Check.Returns("lineTotal(\"5|Tea|2|1.50\")", "3.0"),
        ),
    ),
    Drill(
        "HighScore", Topic.CONVERTING, "Who won?",
        task = "Like High Score Wins: a result looks like Home:Visitor|21:9 (the team names, then their scores). Give back the winning team's name.",
        signature = "static String winner(String result)",
        starter = fillIn("HighScore", "\"Home:Visitor|21:9\" gives back \"Home\".", "static String winner(String result)", "\"\"", listOf(print("winner(\"Home:Visitor|21:9\")") + "   // should print Home")),
        hint = "Split at | first, with split(\"\\\\|\"): that gives the names and the scores. Split each half at \":\". Turn the scores into numbers with Integer.parseInt, then compare them.",
        checks = listOf(
            Check.Returns("winner(\"Home:Visitor|21:9\")", "\"Home\""),
            Check.Returns("winner(\"Lions:Tigers|10:14\")", "\"Tigers\"", why = "The second team wins when its score is bigger."),
            Check.Returns("winner(\"Ants:Bees|100:99\")", "\"Ants\"", why = "Compare the scores as numbers made with Integer.parseInt, not as text: as text, \"100\" comes before \"99\"."),
        ),
    ),
    Drill(
        "ToDate", Topic.CONVERTING, "Text into a date",
        task = "Turn text like 10/17/2022 (month/day/year) into a LocalDate.",
        signature = "static LocalDate toDate(String text)",
        starter = fillIn(
            "ToDate", "\"10/17/2022\" gives back the date October 17, 2022.",
            "static LocalDate toDate(String text)", "null",
            listOf(print("toDate(\"10/17/2022\")") + "   // should print 2022-10-17"),
            imports = "import java.time.LocalDate;\nimport java.time.format.DateTimeFormatter;\n\n",
        ),
        hint = "Make a formatter with DateTimeFormatter.ofPattern(\"MM/dd/yyyy\"), then return LocalDate.parse(text, formatter). Capital MM is the month; small mm means minutes.",
        checks = listOf(
            Check.Returns("toDate(\"10/17/2022\")", "java.time.LocalDate.of(2022, 10, 17)", why = "Use \"MM/dd/yyyy\": capital MM is the month (small mm is minutes), dd the day and yyyy the year."),
            Check.Returns("toDate(\"01/05/2026\")", "java.time.LocalDate.of(2026, 1, 5)"),
        ),
    ),
    Drill(
        "BirthYear", Topic.CONVERTING, "The year from a date",
        task = "Dates written like 2002-10-17 (year-month-day) need no formatter. Give back the year as a number.",
        signature = "static int birthYear(String date)",
        starter = fillIn(
            "BirthYear", "\"2002-10-17\" gives back 2002.",
            "static int birthYear(String date)", "0",
            listOf(print("birthYear(\"2002-10-17\")") + "   // should print 2002"),
            imports = "import java.time.LocalDate;\n\n",
        ),
        hint = "LocalDate.parse(date) reads year-month-day text on its own. Then .getYear() gives back the year.",
        checks = listOf(Check.Returns("birthYear(\"2002-10-17\")", "2002"), Check.Returns("birthYear(\"1999-01-31\")", "1999")),
    ),

    // ---------------------------------------------------------------- StringBuilder
    Drill(
        "Backwards", Topic.BUILDER, "Text backwards",
        task = "Give back the text backwards, using a StringBuilder. abc gives back cba.",
        signature = "static String backwards(String text)",
        starter = fillIn("Backwards", "\"Java\" gives back \"avaJ\".", "static String backwards(String text)", "\"\"", listOf(print("backwards(\"Java\")") + "   // should print avaJ")),
        hint = "new StringBuilder(text) makes a builder holding the text. .reverse() turns it around, and .toString() turns it back into a String.",
        checks = listOf(
            Check.Returns("backwards(\"abc\")", "\"cba\""),
            Check.Returns("backwards(\"Java\")", "\"avaJ\""),
            Check.Returns("backwards(\"\")", "\"\"", why = "Empty text backwards is still empty."),
        ),
    ),
    Drill(
        "AddressLabel", Topic.BUILDER, "Build an address",
        task = "Like Address Builder: use a StringBuilder to give back a 3-line address label: the name, the street, then City, ST 12345.",
        signature = "static String label(String name, String street, String city, String state, String zip)",
        starter = fillIn(
            "AddressLabel", "Gives back \"Dana Wyatt\\n123 Main St\\nDallas, TX 75201\" (\\n starts a new line).",
            "static String label(String name, String street, String city, String state, String zip)", "\"\"",
            listOf(print("label(\"Dana Wyatt\", \"123 Main St\", \"Dallas\", \"TX\", \"75201\")")),
        ),
        hint = "StringBuilder label = new StringBuilder(); then label.append(name).append(\"\\n\"); and so on for each line. At the end, return label.toString();",
        checks = listOf(
            Check.Returns(
                "label(\"Dana Wyatt\", \"123 Main St\", \"Dallas\", \"TX\", \"75201\")", "\"Dana Wyatt\\n123 Main St\\nDallas, TX 75201\"",
                why = "Each part goes on its own line: put \"\\n\" between them, but not at the very end. The last line is City, ST 12345.",
            ),
            Check.Returns("label(\"Glen Williams\", \"9 Oak Ave\", \"Austin\", \"TX\", \"73301\")", "\"Glen Williams\\n9 Oak Ave\\nAustin, TX 73301\""),
        ),
    ),

    // ---------------------------------------------------------------- Classes and objects
    Drill(
        "Pet", Topic.CLASSES, "Your first class",
        task = "Finish the Pet class: the constructor saves the name and age, and the getters give them back.",
        signature = "public Pet(String name, int age)",
        starter = """
            |public class Pet {
            |
            |    // Each Pet has its own name and age. private: only code in this class can touch them.
            |    private String name;
            |    private int age;
            |
            |    // The constructor runs when someone writes new Pet("Rex", 3). Save the values here.
            |    public Pet(String name, int age) {
            |        // Your code here
            |    }
            |
            |    // Gives back this pet's name.
            |    public String getName() {
            |        return "";   // <- replace this
            |    }
            |
            |    // Gives back this pet's age.
            |    public int getAge() {
            |        return 0;   // <- replace this
            |    }
            |
            |    // To try it yourself, press the green ▶ next to main.
            |    // To test it, press "Check my answer" at the top of this file.
            |    public static void main(String[] args) {
            |        Pet rex = new Pet("Rex", 3);
            |        System.out.println(rex.getName() + " is " + rex.getAge());   // should print Rex is 3
            |    }
            |}
            |""".trimMargin(),
        hint = "In the constructor, this.name = name; puts the name that came in into this pet's own name. Do the same for age. The getters just return name; and return age;",
        checks = listOf(
            Check.Returns("new Pet(\"Rex\", 3).getName()", "\"Rex\"", onClass = false, why = "The constructor has to save the name: this.name = name; and getName() has to return name;"),
            Check.Returns("new Pet(\"Rex\", 3).getAge()", "3", onClass = false, why = "Save the age in the constructor too: this.age = age;"),
            Check.Returns("new Pet(\"Milo\", 7).getName()", "\"Milo\"", onClass = false),
        ),
    ),
    Drill(
        "Phone", Topic.CLASSES, "Getters and setters",
        task = "Like the Cell Phone Service: the owner's getter and setter are done. Write getNumber() and setNumber(...) the same way, and start the number as \"\" in the constructor.",
        signature = "public void setNumber(String number)",
        starter = """
            |public class Phone {
            |
            |    private String owner;
            |    private String number;
            |
            |    // A new phone starts empty: no owner and no number.
            |    public Phone() {
            |        this.owner = "";
            |        // Your code here: start the number as "" too
            |    }
            |
            |    // Done for you: the getter and setter for owner.
            |    public String getOwner() {
            |        return owner;
            |    }
            |
            |    public void setOwner(String owner) {
            |        this.owner = owner;
            |    }
            |
            |    // Your turn: write getNumber() and setNumber(String number) the same way.
            |
            |    // To try it yourself, press the green ▶ next to main.
            |    // To test it, press "Check my answer" at the top of this file.
            |    public static void main(String[] args) {
            |        Phone phone = new Phone();
            |        phone.setOwner("Sandra");
            |        phone.setNumber("888-555-1234");
            |        System.out.println(phone.getOwner() + ": " + phone.getNumber());   // should print Sandra: 888-555-1234
            |    }
            |}
            |""".trimMargin(),
        hint = "Copy the owner pair and change owner to number:  public String getNumber() { return number; }  and  public void setNumber(String number) { this.number = number; }",
        checks = listOf(
            Check.Returns(steps("Phone p = new Phone();", result = "p.getNumber()"), "\"\"", onClass = false, label = "a new phone's number", why = "A new phone's number should start as \"\" (empty), not null. Set it in the constructor: this.number = \"\";"),
            Check.Returns(steps("Phone p = new Phone();", "p.setNumber(\"888-555-1234\");", result = "p.getNumber()"), "\"888-555-1234\"", onClass = false, label = "setNumber, then getNumber()", why = "setNumber has to save the value that came in: this.number = number;"),
            Check.Returns(steps("Phone p = new Phone();", "p.setOwner(\"Sandra\");", "p.setNumber(\"555\");", result = "p.getOwner()"), "\"Sandra\"", onClass = false, label = "setNumber leaves the owner alone", why = "setNumber should only change the number, not the owner."),
        ),
    ),
    Drill(
        "Mobile", Topic.CLASSES, "A method that uses a field",
        task = "Write dial: it takes the number to call and prints OWNER's phone is calling NUMBER, using this phone's owner.",
        signature = "public void dial(String numberToCall)",
        starter = """
            |public class Mobile {
            |
            |    private String owner;
            |
            |    public Mobile(String owner) {
            |        this.owner = owner;
            |    }
            |
            |    // Write dial below. It takes the number to call (a parameter: it's different every
            |    // call, so it's not a field) and prints, for example: Sandra's phone is calling 555-1234
            |    // Its first line should be:
            |    //     public void dial(String numberToCall) {
            |
            |    // To try it yourself, press the green ▶ next to main.
            |    // To test it, press "Check my answer" at the top of this file.
            |    public static void main(String[] args) {
            |        Mobile phone = new Mobile("Sandra");
            |        phone.dial("555-1234");   // should print Sandra's phone is calling 555-1234
            |    }
            |}
            |""".trimMargin(),
        hint = "Inside dial, owner is this phone's owner and numberToCall is what was handed in:  System.out.println(owner + \"'s phone is calling \" + numberToCall);",
        checks = listOf(
            Check.Prints("new Mobile(\"Sandra\").dial(\"555-1234\")", "Sandra's phone is calling 555-1234", onClass = false, label = "Sandra dials 555-1234", why = "Use the owner field and the number that was handed in: owner + \"'s phone is calling \" + numberToCall"),
            Check.Prints("new Mobile(\"Kim\").dial(\"800-555-0000\")", "Kim's phone is calling 800-555-0000", onClass = false, label = "Kim dials 800-555-0000"),
        ),
    ),
    Drill(
        "Counter", Topic.CLASSES, "Each object remembers",
        task = "Finish click(): each click adds 1 to this counter's count. Every Counter object keeps its own count.",
        signature = "public void click()",
        starter = """
            |public class Counter {
            |
            |    private int count;   // starts at 0
            |
            |    // Adds 1 to this counter's count.
            |    public void click() {
            |        // Your code here
            |    }
            |
            |    public int getCount() {
            |        return count;
            |    }
            |
            |    // To try it yourself, press the green ▶ next to main.
            |    // To test it, press "Check my answer" at the top of this file.
            |    public static void main(String[] args) {
            |        Counter a = new Counter();
            |        Counter b = new Counter();
            |        a.click();
            |        a.click();
            |        b.click();
            |        System.out.println(a.getCount() + " and " + b.getCount());   // should print 2 and 1
            |    }
            |}
            |""".trimMargin(),
        hint = "count++; adds 1 to this object's count. The field keeps its value between calls, as long as the object exists.",
        checks = listOf(
            Check.Returns("new Counter().getCount()", "0", onClass = false, label = "a new counter"),
            Check.Returns(steps("Counter c = new Counter();", "c.click();", "c.click();", result = "c.getCount()"), "2", onClass = false, label = "click twice, then getCount()", why = "click() should add 1 to the count: count++;"),
            Check.Returns(steps("Counter a = new Counter();", "Counter b = new Counter();", "a.click();", "a.click();", "b.click();", result = "b.getCount()"), "1", onClass = false, label = "a clicked twice, b once: b's count", why = "Each object has its own count. If they share one, the field may say static: take static off."),
        ),
    ),

    // ---------------------------------------------------------------- Overloading
    Drill(
        "AreaTwoWays", Topic.OVERLOADING, "Same name, different inputs",
        task = "There's already area(width, height). Write a second method, also called area, that takes one number (a square's side) and gives back side times side.",
        signature = "static int area(int side)",
        starter = """
            |public class AreaTwoWays {
            |
            |    // Done for you: the area of a rectangle.
            |    static int area(int width, int height) {
            |        return width * height;
            |    }
            |
            |    // Your turn: write a second method, also called area, that takes ONE number
            |    // (the side of a square) and gives back side times side.
            |    // Its first line should be:
            |    //     static int area(int side) {
            |
            |    // To try it yourself, press the green ▶ next to main.
            |    // To test it, press "Check my answer" at the top of this file.
            |    public static void main(String[] args) {
            |        System.out.println(area(3, 4));   // should print 12
            |        System.out.println(area(5));      // should print 25
            |    }
            |}
            |""".trimMargin(),
        hint = "Two methods can share a name when what's in their brackets is different. Write  static int area(int side) { return side * side; }  next to the first one.",
        checks = listOf(
            Check.Returns("area(5)", "25", why = "Java picks which area to run by what's in the brackets: area(5) has one number, so it runs your new method."),
            Check.Returns("area(1)", "1"),
            Check.Returns("area(3, 4)", "12", why = "Keep the first area method too. Both can live side by side because their brackets are different."),
        ),
    ),
    Drill(
        "Box", Topic.OVERLOADING, "Two constructors",
        task = "new Box(3, 4) already works. Add a second constructor with empty brackets, so new Box() makes a 1 by 1 box.",
        signature = "public Box()",
        starter = """
            |public class Box {
            |
            |    private int width;
            |    private int height;
            |
            |    // Done for you: new Box(3, 4) makes a 3 by 4 box.
            |    public Box(int width, int height) {
            |        this.width = width;
            |        this.height = height;
            |    }
            |
            |    // Your turn: a second constructor with empty brackets.
            |    // new Box() should make a 1 by 1 box.
            |    // Its first line should be:
            |    //     public Box() {
            |
            |    public int getArea() {
            |        return width * height;
            |    }
            |
            |    // To try it yourself, press the green ▶ next to main.
            |    // To test it, press "Check my answer" at the top of this file.
            |    public static void main(String[] args) {
            |        System.out.println(new Box(3, 4).getArea());   // should print 12
            |        System.out.println(new Box().getArea());       // should print 1
            |    }
            |}
            |""".trimMargin(),
        hint = "public Box() { this.width = 1; this.height = 1; }  A class can have more than one constructor, as long as their brackets are different.",
        checks = listOf(
            Check.Returns("new Box().getArea()", "1", onClass = false, why = "new Box() should set width and height to 1. If you don't set them, they stay 0 and the area is 0."),
            Check.Returns("new Box(3, 4).getArea()", "12", onClass = false, why = "Keep the first constructor too: new Box(3, 4) should still make a 3 by 4 box."),
        ),
    ),

    // ---------------------------------------------------------------- Loops
    Drill(
        "Countdown", Topic.LOOPS, "Countdown",
        task = "Give back a countdown from the number down to 1, then \"Go!\". For 3 that's \"3 2 1 Go!\".",
        signature = "static String countdown(int from)",
        starter = fillIn("Countdown", "3 gives back \"3 2 1 Go!\"", "static String countdown(int from)", "\"\"", listOf(print("countdown(3)") + "   // should print 3 2 1 Go!")),
        hint = "Start with String text = \"\"; then a for loop from 'from' down to 1 that adds i + \" \" each time. Add \"Go!\" at the end.",
        checks = listOf(
            Check.Returns("countdown(3)", "\"3 2 1 Go!\"", why = "Check the spaces: one space after each number."),
            Check.Returns("countdown(1)", "\"1 Go!\""),
            Check.Returns("countdown(5)", "\"5 4 3 2 1 Go!\""),
        ),
    ),
    Drill(
        "SumTo", Topic.LOOPS, "Add up 1 to n",
        task = "Give back 1 + 2 + ... + n. For 3 that's 6.",
        signature = "static int sumTo(int n)",
        starter = fillIn("SumTo", "3 gives back 6 (1 + 2 + 3).", "static int sumTo(int n)", "0", listOf(print("sumTo(10)") + "   // should print 55")),
        hint = "int total = 0; then for (int i = 1; i <= n; i++) { total += i; }",
        checks = listOf(
            Check.Returns("sumTo(3)", "6"),
            Check.Returns("sumTo(10)", "55", why = "If you got 45, the loop stopped one early: use i <= n."),
            Check.Returns("sumTo(1)", "1"), Check.Returns("sumTo(0)", "0"),
        ),
    ),
    Drill(
        "CountVowels", Topic.LOOPS, "Count the vowels",
        task = "Give back how many vowels (a, e, i, o, u) are in the text. Capitals count too.",
        signature = "static int countVowels(String text)",
        starter = fillIn("CountVowels", "\"hello\" gives back 2.", "static int countVowels(String text)", "0", listOf(print("countVowels(\"hello\")") + "   // should print 2")),
        hint = "Loop i from 0 to text.length() - 1. Check each letter with \"aeiou\".indexOf(...) or a few == comparisons. Make it lower case first.",
        checks = listOf(
            Check.Returns("countVowels(\"hello\")", "2"),
            Check.Returns("countVowels(\"JAVA\")", "2", why = "Capital vowels count too: turn the text into small letters first with toLowerCase()."),
            Check.Returns("countVowels(\"rhythm\")", "0"),
        ),
    ),
    Drill(
        "TimesTable", Topic.LOOPS, "Times table",
        task = "Print the times table for n, from 1 to 5, one line each: 3 x 1 = 3 and so on.",
        signature = "static void timesTable(int n)",
        starter = fillIn("TimesTable", "For 3, print 3 x 1 = 3 up to 3 x 5 = 15, each on its own line.", "static void timesTable(int n)", null, listOf("timesTable(3);")),
        hint = "for (int i = 1; i <= 5; i++) { System.out.println(n + \" x \" + i + \" = \" + (n * i)); }",
        checks = listOf(
            Check.Prints("timesTable(3)", "3 x 1 = 3\n3 x 2 = 6\n3 x 3 = 9\n3 x 4 = 12\n3 x 5 = 15", why = "Each line looks like 3 x 2 = 6, with spaces around x and =."),
            Check.Prints("timesTable(7)", "7 x 1 = 7\n7 x 2 = 14\n7 x 3 = 21\n7 x 4 = 28\n7 x 5 = 35"),
        ),
    ),
    Drill(
        "AskAgain", Topic.LOOPS, "Ask until it's right (do/while)",
        task = "Keep reading whole numbers until one is from 1 to 10, then print Got and that number. A do/while fits: it always reads at least once, then checks.",
        signature = "static void run(Scanner in)",
        starter = fillIn(
            "AskAgain", "Read numbers with in.nextInt() until one is from 1 to 10, then print \"Got \" and the number.",
            "static void run(Scanner in)", null,
            listOf("run(new Scanner(System.in));   // type 0, then 15, then 7 in the Run window"),
            imports = "import java.util.Scanner;\n\n",
        ),
        hint = "int n;  do { n = in.nextInt(); } while (n < 1 || n > 10);  then  System.out.println(\"Got \" + n);",
        checks = listOf(
            Check.Prints("run(in)", "Got 7", input = "0\n15\n7\n", contains = true, label = "typing 0, 15, then 7", why = "Keep looping while the number is too small OR too big: while (n < 1 || n > 10);"),
            Check.Prints("run(in)", "Got 5", input = "5\n", contains = true, label = "typing 5 straight away", why = "A good first number should be used right away. do/while reads once before it checks."),
        ),
    ),
    Drill(
        "FirstDigit", Topic.LOOPS, "Stop early (break)",
        task = "Give back the position of the first digit (0 to 9) in the text, or -1 if there isn't one. Stop looking once you've found it.",
        signature = "static int firstDigit(String text)",
        starter = fillIn("FirstDigit", "\"abc4d5\" gives back 3 (the 4). \"none\" gives back -1.", "static int firstDigit(String text)", "-1", listOf(print("firstDigit(\"abc4d5\")") + "   // should print 3")),
        hint = "int found = -1;  loop i from 0 to text.length() - 1. When Character.isDigit(text.charAt(i)) is true, set found = i; and break;",
        checks = listOf(
            Check.Returns("firstDigit(\"abc4d5\")", "3", why = "Give back the FIRST digit. If you got 5, the loop kept going: break out as soon as you find one."),
            Check.Returns("firstDigit(\"7up\")", "0"),
            Check.Returns("firstDigit(\"none\")", "-1", why = "When there's no digit, give back -1, the same way indexOf does."),
        ),
    ),
    Drill(
        "SkipThrees", Topic.LOOPS, "Skip some (continue)",
        task = "Add up 1 to n, but skip the numbers 3 can divide (3, 6, 9...). For 10 that's 37.",
        signature = "static int sumSkipThrees(int n)",
        starter = fillIn("SkipThrees", "10 gives back 37: 1 + 2 + 4 + 5 + 7 + 8 + 10.", "static int sumSkipThrees(int n)", "0", listOf(print("sumSkipThrees(10)") + "   // should print 37")),
        hint = "for (int i = 1; i <= n; i++) { if (i % 3 == 0) continue; total += i; }  continue jumps to the next round of the loop.",
        checks = listOf(
            Check.Returns("sumSkipThrees(10)", "37", why = "Skip the numbers where i % 3 == 0 (3, 6 and 9). If you got 3, you used break, which stops the whole loop; continue only skips one round."),
            Check.Returns("sumSkipThrees(3)", "3"),
            Check.Returns("sumSkipThrees(6)", "12"),
        ),
    ),

    // ---------------------------------------------------------------- Arrays
    Drill(
        "ArrayTotal", Topic.ARRAYS, "Total of an array",
        task = "Give back all the numbers in the array added together.",
        signature = "static int total(int[] numbers)",
        starter = fillIn("ArrayTotal", "{1, 2, 3} gives back 6.", "static int total(int[] numbers)", "0", listOf(print("total(new int[] {1, 2, 3})") + "   // should print 6")),
        hint = "int sum = 0; then for (int number : numbers) { sum += number; }",
        checks = listOf(
            Check.Returns("total(new int[] {1, 2, 3})", "6", label = "total({1, 2, 3})"),
            Check.Returns("total(new int[] {})", "0", label = "total({})", why = "An empty array adds up to 0."),
            Check.Returns("total(new int[] {10, -5})", "5", label = "total({10, -5})"),
        ),
    ),
    Drill(
        "Biggest", Topic.ARRAYS, "Biggest in an array",
        task = "Give back the biggest number in the array. The array always has at least one number.",
        signature = "static int biggest(int[] numbers)",
        starter = fillIn("Biggest", "{3, 9, 2} gives back 9.", "static int biggest(int[] numbers)", "0", listOf(print("biggest(new int[] {3, 9, 2})") + "   // should print 9")),
        hint = "Start with int best = numbers[0]; then loop and replace best when you find a bigger number.",
        checks = listOf(
            Check.Returns("biggest(new int[] {3, 9, 2})", "9", label = "biggest({3, 9, 2})"),
            Check.Returns("biggest(new int[] {-4, -2, -8})", "-2", label = "biggest({-4, -2, -8})", why = "Start with numbers[0], not 0: 0 is bigger than every negative number."),
            Check.Returns("biggest(new int[] {7})", "7", label = "biggest({7})"),
        ),
    ),
    Drill(
        "CountAbove", Topic.ARRAYS, "How many passed?",
        task = "Give back how many scores are above the limit.",
        signature = "static int countAbove(int[] scores, int limit)",
        starter = fillIn("CountAbove", "{90, 85, 77} with limit 80 gives back 2.", "static int countAbove(int[] scores, int limit)", "0", listOf(print("countAbove(new int[] {90, 85, 77}, 80)") + "   // should print 2")),
        hint = "int count = 0; loop over the scores, and count++ when a score is > limit.",
        checks = listOf(
            Check.Returns("countAbove(new int[] {90, 85, 77}, 80)", "2", label = "countAbove({90, 85, 77}, 80)"),
            Check.Returns("countAbove(new int[] {50, 60}, 70)", "0", label = "countAbove({50, 60}, 70)"),
            Check.Returns("countAbove(new int[] {70, 71}, 70)", "1", label = "countAbove({70, 71}, 70)", why = "Above means more than: use >, not >=."),
        ),
    ),
    Drill(
        "AverageScore", Topic.ARRAYS, "Average test score",
        task = "Like Test Statistics: give back the average of the scores, with decimals. The array always has at least one score.",
        signature = "static double average(int[] scores)",
        starter = fillIn("AverageScore", "{90, 80, 70} gives back 80.0. {1, 2} gives back 1.5.", "static double average(int[] scores)", "0.0", listOf(print("average(new int[] {90, 80, 70})") + "   // should print 80.0")),
        hint = "Add them up in a loop, then divide by scores.length. Put (double) in front of the total so Java keeps the decimals: (double) total / scores.length",
        checks = listOf(
            Check.Returns("average(new int[] {90, 80, 70})", "80.0", label = "average({90, 80, 70})"),
            Check.Returns("average(new int[] {1, 2})", "1.5", label = "average({1, 2})", why = "If you got 1.0, Java divided whole numbers and dropped the .5. Use (double) total / scores.length."),
            Check.Returns("average(new int[] {100})", "100.0", label = "average({100})"),
        ),
    ),
    Drill(
        "SortedCopy", Topic.ARRAYS, "Sort a copy",
        task = "Give back a sorted copy of the numbers, smallest first. The array that came in must stay the way it was.",
        signature = "static int[] sortedCopy(int[] numbers)",
        starter = fillIn(
            "SortedCopy", "{3, 1, 2} gives back {1, 2, 3}, and the {3, 1, 2} that came in stays {3, 1, 2}.",
            "static int[] sortedCopy(int[] numbers)", "new int[0]",
            listOf(print("Arrays.toString(sortedCopy(new int[] {3, 1, 2}))") + "   // should print [1, 2, 3]"),
            imports = "import java.util.Arrays;\n\n",
        ),
        hint = "int[] copy = numbers; is NOT a copy: it's a second name for the same array. Make a real one with Arrays.copyOf(numbers, numbers.length), then Arrays.sort(copy); and return copy;",
        checks = listOf(
            Check.Returns("java.util.Arrays.toString(SortedCopy.sortedCopy(new int[] {3, 1, 2}))", "\"[1, 2, 3]\"", onClass = false, label = "sortedCopy({3, 1, 2})", why = "Arrays.sort(copy) puts the copy in order, smallest first."),
            Check.Returns(
                steps("int[] original = {3, 1, 2};", "SortedCopy.sortedCopy(original);", result = "java.util.Arrays.toString(original)"), "\"[3, 1, 2]\"",
                onClass = false, label = "the array that came in stays {3, 1, 2}",
                why = "int[] copy = numbers; doesn't copy anything: both names point to the same array, so sorting one sorts both. Make a real copy with Arrays.copyOf (or a new array and System.arraycopy).",
            ),
        ),
    ),
    Drill(
        "FindName", Topic.ARRAYS, "Find a name",
        task = "Give back the position of the name in the array, or -1 if it isn't there.",
        signature = "static int find(String[] names, String target)",
        starter = fillIn("FindName", "{\"Ann\", \"Bo\", \"Cy\"} and \"Bo\" gives back 1.", "static int find(String[] names, String target)", "-1", listOf(print("find(new String[] {\"Ann\", \"Bo\", \"Cy\"}, \"Bo\")") + "   // should print 1")),
        hint = "Loop i from 0 to names.length - 1. When names[i].equals(target), return i. After the loop, return -1.",
        checks = listOf(
            Check.Returns("find(new String[] {\"Ann\", \"Bo\", \"Cy\"}, new String(\"Bo\"))", "1", label = "find({Ann, Bo, Cy}, \"Bo\")", why = "Compare text with equals, not ==: names[i].equals(target). == can say two equal names are different."),
            Check.Returns("find(new String[] {\"Ann\", \"Bo\"}, \"Ann\")", "0", label = "find({Ann, Bo}, \"Ann\")"),
            Check.Returns("find(new String[] {\"Ann\"}, \"Zed\")", "-1", label = "find({Ann}, \"Zed\")", why = "When the name isn't there, give back -1."),
        ),
    ),
).sortedBy { it.topic.ordinal }

fun drillFor(fileName: String): Drill? = DRILLS.firstOrNull { it.fileName == fileName }
