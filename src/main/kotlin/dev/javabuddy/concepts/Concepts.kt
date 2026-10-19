package dev.javabuddy.concepts

/** Groups for the list in the Concepts tab, in the order they're shown. */
enum class Topic(val title: String) {
    CLASSES("Classes and objects"),
    METHODS("Methods"),
    LOOPS("Choices and loops"),
    PROBLEMS("When things go wrong"),
    VALUES("Kinds of values"),
    FILES("Setting up a file"),
}

/**
 * A short card about one Java word. [example] is shown in a code box; lines starting with "✗" are
 * red (Java won't allow it), "✓" green.
 */
data class Concept(
    val label: String,
    val topic: Topic,
    /** A few words, shown after the label: "static: belongs to the class". */
    val summary: String,
    val meaning: String,
    val example: String,
    val tip: String? = null,
) {
    val title get() = "$label: $summary"
}

object Concepts {
    val CLASS = Concept(
        "class", Topic.CLASSES, "a plan for making objects",
        "A class is a plan. It says what each object knows (its fields) and what it can do (its methods). " +
            "You make objects from it with new.",
        """
        class Dog {
            String name;              // each dog knows its name
            void bark() {             // each dog can bark
                System.out.println(name + " says woof");
            }
        }
        """,
        "Class names start with a capital letter. A public class must be in a file with the same name: Dog.java.",
    )
    val NEW = Concept(
        "new", Topic.CLASSES, "makes an object",
        "new makes a fresh object from a class and hands it to you. Each new makes a separate object with its own values.",
        """
        Dog rex = new Dog();
        Dog fido = new Dog();     // a second, separate dog
        rex.name = "Rex";         // fido's name doesn't change
        """,
    )
    val THIS = Concept(
        "this", Topic.CLASSES, "the object you're inside",
        "Inside a method, this means \"the object this method is running for\". " +
            "You need it when a parameter has the same name as a field.",
        """
        class Dog {
            String name;
            Dog(String name) {
                this.name = name;     // this.name = the field, name = the parameter
            }
        }
        """,
    )
    val PUBLIC = Concept(
        "public", Topic.CLASSES, "any class can use it",
        "public means code in any other class can see and use this. main must be public so Java can start your program.",
        """
        public class Main {
            public static void main(String[] args) { ... }
        }
        """,
        "Leave out public, private and protected, and only classes in the same package (folder) can use it.",
    )
    val PRIVATE = Concept(
        "private", Topic.CLASSES, "only this class can use it",
        "private hides something inside its own class. Other classes can't see it. " +
            "Make fields private, and give other classes methods to read or change them safely.",
        """
          class Account {
              private double balance;          // hidden
              public double getBalance() {     // the safe way in
                  return balance;
              }
          }

        ✗ account.balance = 1000000;           // not allowed from another class
        ✓ account.getBalance();
        """,
    )
    val PROTECTED = Concept(
        "protected", Topic.CLASSES, "this class, its children and its package",
        "protected is in between public and private. The class itself, classes that extend it, " +
            "and classes in the same package (folder) can use it.",
        """
        class Animal {
            protected String name;    // Dog can use this too
        }
        class Dog extends Animal {
            void hello() { System.out.println(name); }
        }
        """,
    )
    val STATIC = Concept(
        "static", Topic.METHODS, "belongs to the class, not to each object",
        "A static method belongs to the class itself, not to one object. You don't need new to use it. " +
            "main is static, so Java can run it before any object exists. " +
            "That's why main can call other static methods in the same class just by their name.",
        """
        public class Main {
            static int twice(int number) {    // a static method
                return number * 2;
            }

            public static void main(String[] args) {
                int answer = twice(5);        // call it by name: answer is 10
                System.out.println(answer);
                double root = Math.sqrt(16);  // from another class: ClassName.method()
            }
        }
        """,
        "A static variable has only one copy, shared by all objects. " +
            "From a static method, you can't use a method or variable that isn't static unless you make an object first. " +
            "Java's error \"non-static method cannot be referenced from a static context\" means exactly this.",
    )
    val VOID = Concept(
        "void", Topic.METHODS, "gives nothing back",
        "Every method says what it gives back, just before its name. void means it gives nothing back: " +
            "it just does its job, like printing. If a method should hand back a value, write that value's type there instead, and use return.",
        """
        static void sayHi(String name) {      // gives nothing back
            System.out.println("Hi " + name);
        }
        static int twice(int number) {        // gives back an int
            return number * 2;
        }

        sayHi("Ana");                         // just runs it
        int answer = twice(4);                // keeps what comes back: 8
        """,
        "You can't keep the result of a void method in a variable. There's nothing to keep.",
    )
    val RETURN = Concept(
        "return", Topic.METHODS, "hand back a value and stop",
        "return hands a value back to the code that called the method, and ends the method right there. " +
            "The value must be the type written before the method's name. In a void method, a plain return; just stops early.",
        """
        static int biggest(int a, int b) {    // promises to give back an int
            if (a > b) {
                return a;                     // stops here
            }
            return b;
        }

        public static void main(String[] args) {
            int big = biggest(3, 8);          // big is 8
            System.out.println(big);
        }
        """,
        "Java's error \"missing return statement\" means there's a way through the method that never reaches a return.",
    )
    val PARAMETERS = Concept(
        "( ) parameters", Topic.METHODS, "the values a method gets",
        "The ( ) after a method's name lists what it needs to do its job (its parameters). " +
            "Each one has a type and a name, with commas between them. " +
            "When you call the method, you put real values in the ( ), in the same order.",
        """
        static void greet(String name, int age) {    // needs a String, then an int
            System.out.println(name + " is " + age);
        }

        public static void main(String[] args) {
            greet("Ana", 12);                        // name is "Ana", age is 12
            greet("Ben", 9);                         // same method, new values
        }
        """,
        "Empty ( ) means the method needs nothing: sayHi(). A parameter is a normal variable inside the method, and it's gone when the method ends.",
    )
    val FINAL = Concept(
        "final", Topic.CLASSES, "can't be changed",
        "A final variable gets a value once and then never changes. " +
            "A final method can't be replaced in a child class, and a final class can't be extended.",
        """
          final int MAX_PLAYERS = 4;
        ✗ MAX_PLAYERS = 5;            // not allowed: it's final
        """,
        "Fixed values are often written in CAPITALS, like MAX_PLAYERS, and made static final.",
    )
    val EXTENDS = Concept(
        "extends", Topic.CLASSES, "build on another class",
        "class Dog extends Animal means a Dog is an Animal, plus more. " +
            "Dog gets everything Animal has, and can add its own things or change methods. A class can extend only one class.",
        """
        class Animal {
            void eat() { System.out.println("munch"); }
        }
        class Dog extends Animal {
            void bark() { System.out.println("woof"); }
        }

        new Dog().eat();              // Dog got eat() from Animal
        """,
        "Inside <...>, like <T extends Number>, it means \"T must be a Number or a kind of Number\".",
    )
    val SUPER = Concept(
        "super", Topic.CLASSES, "the class you extend",
        "super means \"the parent class\" (the one after extends). " +
            "super(...) runs the parent's setup code (constructor). super.method() runs the parent's version of a method.",
        """
        class Dog extends Animal {
            Dog(String name) {
                super(name);          // let Animal set up first
            }
            @Override
            void eat() {
                super.eat();          // do what Animal does...
                System.out.println("...then wag tail");
            }
        }
        """,
        "super(...) must be the first line of a constructor.",
    )
    val INTERFACE = Concept(
        "interface", Topic.CLASSES, "a list of methods a class promises to have",
        "An interface lists methods, usually without their code. A class that implements it must write all of them. " +
            "That way different classes can be used the same way.",
        """
        interface Shape {
            double area();
        }
        class Square implements Shape {
            double side = 2;
            public double area() { return side * side; }
        }
        """,
    )
    val IMPLEMENTS = Concept(
        "implements", Topic.CLASSES, "keeps an interface's promise",
        "class Square implements Shape means Square writes every method that Shape lists. " +
            "A class can implement many interfaces, with commas between them.",
        """
        class Duck implements Swimmer, Flyer {
            public void swim() { ... }
            public void fly() { ... }
        }
        """,
        "The methods must be public, even though the interface didn't say so.",
    )
    val ABSTRACT = Concept(
        "abstract", Topic.CLASSES, "unfinished on purpose",
        "An abstract class can't be made with new. It's a starting point for other classes. " +
            "An abstract method has no code; each class that extends it must write it.",
        """
          abstract class Animal {
              abstract String sound();    // no { } here
          }
          class Cat extends Animal {
              String sound() { return "meow"; }
          }

        ✗ new Animal();                   // not allowed: it's abstract
        ✓ new Cat();
        """,
    )
    val ENUM = Concept(
        "enum", Topic.CLASSES, "a fixed list of choices",
        "An enum is a type with a set list of values, like the days of the week. Java won't let you use a value that isn't on the list.",
        """
        enum Size { SMALL, MEDIUM, LARGE }

        Size coffee = Size.MEDIUM;
        if (coffee == Size.LARGE) { ... }
        """,
    )
    val RECORD = Concept(
        "record", Topic.CLASSES, "a quick class that holds data",
        "A record is a short way to write a class that only holds some values. " +
            "Java writes the setup code, the getters, equals and toString for you. Its values can't be changed.",
        """
        record Point(int x, int y) { }

        Point p = new Point(3, 4);
        System.out.println(p.x());    // 3
        System.out.println(p);        // Point[x=3, y=4]
        """,
    )
    val OVERRIDE = Concept(
        "@Override", Topic.CLASSES, "replaces a parent's method",
        "@Override goes above a method that replaces one from the parent class or an interface. " +
            "If you misspell the name, Java tells you, instead of quietly making a new method.",
        """
        class Dog extends Animal {
            @Override
            String sound() { return "woof"; }
        }
        """,
    )
    val GENERICS = Concept(
        "< >", Topic.CLASSES, "what type goes inside",
        "The angle brackets (generics) say what type of thing a class holds. List<String> is a list of Strings only. " +
            "Java stops you from adding a number by mistake, and you don't have to convert things when you take them out.",
        """
          List<String> names = new ArrayList<>();
          names.add("Ana");
        ✗ names.add(42);              // not allowed: 42 isn't a String
          String first = names.get(0);
        """,
        "Use the class versions inside < >: List<Integer>, not List<int>. Empty <> after new means \"same as on the left\".",
    )

    val IF = Concept(
        "if / else", Topic.LOOPS, "do something only when it's true",
        "if runs its { } block only when the test in ( ) is true. else runs when it's false. " +
            "Java checks else if parts from top to bottom and runs only the first one that's true.",
        """
        if (age >= 18) {
            System.out.println("Adult");
        } else if (age >= 13) {
            System.out.println("Teen");
        } else {
            System.out.println("Child");
        }
        """,
        "Use == to compare numbers, but .equals() to compare Strings.",
    )
    val FOR = Concept(
        "for", Topic.LOOPS, "repeat a set number of times",
        "A for loop has three parts: where to start; keep going while this is true; what to do after each round. " +
            "The short form for (String name : names) goes through every item in a list or array.",
        """
        for (int i = 0; i < 3; i++) {
            System.out.println(i);        // 0, 1, 2
        }
        for (String name : names) {
            System.out.println(name);     // every name in the list
        }
        """,
    )
    val WHILE = Concept(
        "while", Topic.LOOPS, "repeat as long as it's true",
        "while checks the test before each round and stops when it's false. " +
            "Something inside must change, or it loops forever. do { } while (...); always runs at least once.",
        """
        int lives = 3;
        while (lives > 0) {
            System.out.println("Lives left: " + lives);
            lives--;                      // without this, it never stops
        }
        """,
    )
    val SWITCH = Concept(
        "switch", Topic.LOOPS, "pick one of many paths",
        "switch looks at one value and jumps to the case that matches it. default is used when nothing matches. " +
            "With arrows (->), only the matching case runs.",
        """
        String dayName = switch (day) {
            case 1 -> "Monday";
            case 2 -> "Tuesday";
            case 6, 7 -> "Weekend";
            default -> "Some other day";
        };
        """,
        "The older style (case 1:) needs break; at the end of each case, or it runs on into the next one.",
    )
    val BREAK = Concept(
        "break", Topic.LOOPS, "leave the loop now",
        "break stops the loop (or switch) right away. Java carries on with the line after the loop.",
        """
        for (String name : names) {
            if (name.equals("Ana")) {
                System.out.println("Found her!");
                break;                    // no need to look further
            }
        }
        """,
    )
    val CONTINUE = Concept(
        "continue", Topic.LOOPS, "skip to the next round",
        "continue skips the rest of this round of the loop and starts the next round.",
        """
        for (int i = 1; i <= 5; i++) {
            if (i == 3) {
                continue;                 // skip 3
            }
            System.out.println(i);        // 1, 2, 4, 5
        }
        """,
    )

    val TRY = Concept(
        "try / catch", Topic.PROBLEMS, "plan B if something goes wrong",
        "Put code that might fail inside try { }. If it fails with an error (exception), Java jumps to the matching catch instead of crashing. " +
            "finally { } runs either way.",
        """
        try {
            int n = Integer.parseInt("abc");
        } catch (NumberFormatException e) {
            System.out.println("That's not a number");
        } finally {
            System.out.println("This always runs");
        }
        """,
    )
    val THROW = Concept(
        "throw", Topic.PROBLEMS, "stop with an error on purpose",
        "throw stops the method and sends an error (exception) to the code that called it. " +
            "Use it when something is wrong and the method can't carry on.",
        """
        void setAge(int age) {
            if (age < 0) {
                throw new IllegalArgumentException("Age can't be negative");
            }
            this.age = age;
        }
        """,
    )
    val THROWS = Concept(
        "throws", Topic.PROBLEMS, "warns that this method might fail",
        "throws in a method's first line lists errors (exceptions) the method passes on instead of handling them. " +
            "Code that calls it must catch them, or pass them on too.",
        """
        String readNotes() throws IOException {
            return Files.readString(Path.of("notes.txt"));
        }
        """,
        "Java insists on this only for some errors, like IOException. Mistakes like dividing by zero don't need it.",
    )

    val INT = Concept(
        "int", Topic.VALUES, "a whole number",
        "int holds whole numbers like 7 or -20, up to about 2 billion. " +
            "Dividing two ints gives an int: the part after the dot is dropped.",
        """
        int apples = 7;
        int each = apples / 2;        // 3, not 3.5
        double exact = apples / 2.0;  // 3.5
        """,
    )
    val LONG = Concept(
        "long", Topic.VALUES, "a very big whole number",
        "long is like int, but goes much higher (about 9 billion billion). Put L at the end of big numbers.",
        """
        long people = 8_000_000_000L;   // too big for an int
        """,
        "The _ in numbers is only there to make them easier to read.",
    )
    val DOUBLE = Concept(
        "double", Topic.VALUES, "a number with a decimal point",
        "double holds numbers like 3.14 or 0.5. They can be very slightly off, because computers store them in binary.",
        """
        double price = 9.99;
        System.out.println(0.1 + 0.2);  // 0.30000000000000004
        """,
        "For money, count whole cents in an int or long, or use BigDecimal.",
    )
    val BOOLEAN = Concept(
        "boolean", Topic.VALUES, "true or false",
        "A boolean is either true or false, nothing else. Tests like age >= 18 give you a boolean.",
        """
        boolean isAdult = age >= 18;
        if (isAdult && hasTicket) {     // && means "and"
            System.out.println("Come in");
        }
        """,
    )
    val CHAR = Concept(
        "char", Topic.VALUES, "one single character",
        "A char holds exactly one letter, digit or symbol. It uses single quotes. A String uses double quotes.",
        """
          char grade = 'A';
        ✗ char grade = "A";           // double quotes make a String
          char first = name.charAt(0);
        """,
    )
    val STRING = Concept(
        "String", Topic.VALUES, "a piece of text",
        "A String holds text, in double quotes. Join Strings with +. It starts with a capital S, because it's a class.",
        """
          String name = "Ana";
          String hi = "Hi " + name;   // "Hi Ana"
          name.length();              // 3

        ✗ if (name == "Ana")          // may be false even when it looks true
        ✓ if (name.equals("Ana"))
        """,
    )
    val ARRAY = Concept(
        "[ ]", Topic.VALUES, "an array, a fixed row of boxes",
        "An array holds a fixed number of values of one type. The boxes are numbered from 0. " +
            "Use [number] to get or set one box.",
        """
        int[] scores = new int[3];    // 3 boxes, all 0
        scores[0] = 90;               // the first box is number 0
        int[] days = {1, 2, 3};
        System.out.println(days.length);  // 3
        """,
        "The last box is length - 1. Going past it crashes with ArrayIndexOutOfBoundsException.",
    )
    val NULL = Concept(
        "null", Topic.VALUES, "no object here",
        "null means a variable doesn't point to any object yet. Calling a method on null crashes with a NullPointerException.",
        """
        String name = null;
        if (name != null) {           // check first
            System.out.println(name.length());
        }
        """,
        "Numbers and booleans (int, double, boolean…) can't be null.",
    )
    val VAR = Concept(
        "var", Topic.VALUES, "let Java work out the type",
        "Inside a method, you can write var instead of the type. Java works out the type from the value on the right. " +
            "The type still can't change later.",
        """
          var count = 5;                      // an int
          var names = new ArrayList<String>();
        ✗ var nothing;                        // Java needs a value to work out the type
        """,
    )
    val INSTANCEOF = Concept(
        "instanceof", Topic.VALUES, "is it this kind of thing?",
        "x instanceof Dog is true when x is a Dog (or a kind of Dog). " +
            "Add a name after it, and you can use the object as a Dog straight away.",
        """
        if (pet instanceof Dog dog) {
            dog.bark();               // dog is pet, as a Dog
        }
        """,
    )

    val IMPORT = Concept(
        "import", Topic.FILES, "bring in a class from somewhere else",
        "import tells Java where to find a class that lives in another package (folder). Imports go at the top of the file.",
        """
        import java.util.Scanner;

        Scanner input = new Scanner(System.in);
        """,
        "Classes in java.lang, like String and Math, never need an import.",
    )
    val PACKAGE = Concept(
        "package", Topic.FILES, "which folder this file is in",
        "The package line says which package (folder) the class belongs to. It must match the folders the file is in.",
        """
        package games.cards;          // file is in games/cards/
        """,
    )

    val ALL: List<Concept> = listOf(
        CLASS, NEW, THIS, PUBLIC, PRIVATE, PROTECTED, FINAL, EXTENDS, SUPER,
        INTERFACE, IMPLEMENTS, ABSTRACT, ENUM, RECORD, OVERRIDE, GENERICS,
        STATIC, VOID, RETURN, PARAMETERS,
        IF, FOR, WHILE, SWITCH, BREAK, CONTINUE,
        TRY, THROW, THROWS,
        INT, LONG, DOUBLE, BOOLEAN, CHAR, STRING, ARRAY, NULL, VAR, INSTANCEOF,
        IMPORT, PACKAGE,
    )

    /** Java words (as typed in code) and the card each one opens. */
    val BY_KEYWORD: Map<String, Concept> = mapOf(
        "class" to CLASS, "new" to NEW, "this" to THIS, "public" to PUBLIC, "private" to PRIVATE,
        "protected" to PROTECTED, "static" to STATIC, "final" to FINAL, "void" to VOID, "return" to RETURN,
        "extends" to EXTENDS, "super" to SUPER, "interface" to INTERFACE, "implements" to IMPLEMENTS,
        "abstract" to ABSTRACT, "enum" to ENUM, "record" to RECORD,
        "if" to IF, "else" to IF, "for" to FOR, "while" to WHILE, "do" to WHILE, "switch" to SWITCH, "case" to SWITCH,
        "break" to BREAK, "continue" to CONTINUE,
        "try" to TRY, "catch" to TRY, "finally" to TRY, "throw" to THROW, "throws" to THROWS,
        "int" to INT, "long" to LONG, "double" to DOUBLE, "boolean" to BOOLEAN, "char" to CHAR, "null" to NULL,
        "var" to VAR, "instanceof" to INSTANCEOF, "import" to IMPORT, "package" to PACKAGE,
    )
}
