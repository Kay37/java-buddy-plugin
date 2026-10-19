package playground;

// This program compiles fine but crashes when it runs. Press the green ▶ next to main.
// Java Buddy explains the crash and points to the line. Then change WHICH to try another one.
public class TryCrash {

    static final int WHICH = 3;

    public static void main(String[] args) {
        switch (WHICH) {
            case 1 -> {                          // Going one past the end of an array
                int[] scores = {90, 85, 77};
                for (int i = 0; i <= scores.length; i++) {
                    System.out.println("Score " + i + ": " + scores[i]);
                }
            }
            case 2 -> {                          // Using a variable that holds nothing (null)
                String name = "Kim";
                System.out.println(name.length());
            }
            case 3 -> {                          // Text that isn't a number
                int age = Integer.parseInt(20);
                System.out.println(age);
            }
            case 4 -> {                          // Dividing by zero
                int people = 0;
                System.out.println(100 / people);
            }
            case 5 -> System.out.println(countdown(5));   // Recursion with no stopping point
            default -> System.out.println("Pick WHICH from 1 to 5.");
        }
    }

    static int countdown(int n) {
        return countdown(n - 1);
    }
}
