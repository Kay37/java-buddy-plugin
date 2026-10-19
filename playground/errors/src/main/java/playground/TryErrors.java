package playground;

// Each line marked "MISTAKE" has an error on purpose. Java Buddy explains each one in a
// yellow bubble under the line, and in the Java Buddy panel on the right.
// Fix them one at a time and watch the bubbles go away.
public class TryErrors {

    public static void main(String[] args) {
        int total = 10;
        System.out.println(totl);               // MISTAKE: a typo in a name

        int age = "21";                          // MISTAKE: text where a number belongs

        Scanner in = new Scanner(System.in);     // MISTAKE: missing import

        String greeting = "Hello"                // MISTAKE: missing semicolon
        System.out.println(greeting);

        greet();                                 // MISTAKE: main is static, greet isn't
    }

    void greet() {
        System.out.println("Hi!");
    }

    static int sign(int x) {                     // MISTAKE: not every path returns a value
        if (x > 0) {
            return 1;
        } else if (x < 0) {
            return -1;
        }
    }
}
