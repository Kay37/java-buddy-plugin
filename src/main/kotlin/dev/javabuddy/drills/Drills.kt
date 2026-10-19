package dev.javabuddy.drills

/** A group of drills in the Drills tab, in course order. [soon] marks topics the course hasn't reached yet. */
enum class Topic(val title: String, val short: String, val soon: Boolean = false) {
    STATIC("Static methods", "Methods"),
    VARIABLES("Variables and math", "Math"),
    STRINGS("Text (Strings)", "Text"),
    DECISIONS("if, else and boolean", "if / else"),
    SWITCH("switch", "switch"),
    INPUT("Scanner and printf", "Input"),
    LOOPS("Loops", "Loops", soon = true),
    ARRAYS("Arrays", "Arrays", soon = true),
}

/**
 * One hidden test. [call] is Java code run on the drill's class, like `area(3, 4)`; for [Prints]
 * it may use `in`, a Scanner reading [input]. [why] is shown when this test fails.
 */
sealed class Check {
    abstract val call: String
    abstract val label: String
    abstract val why: String?

    /** [call] must give back [expected], a Java expression such as `12`, `"K.B."` or `Math.PI * 4`. */
    data class Returns(override val call: String, val expected: String, override val why: String? = null, override val label: String = call) : Check()

    /** [call] must print [expected] (or, with [contains], print something that includes it). */
    data class Prints(
        override val call: String,
        val expected: String,
        val input: String = "",
        val contains: Boolean = false,
        override val why: String? = null,
        override val label: String = call,
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

    // ---------------------------------------------------------------- Loops (coming up)
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

    // ---------------------------------------------------------------- Arrays (coming up)
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
)

fun drillFor(fileName: String): Drill? = DRILLS.firstOrNull { it.fileName == fileName }
