package playground;

import java.util.ArrayList;
import java.util.HashMap;

// Try the Memory Picture in the Java Buddy panel:
// 1. Click in the grey margin next to the line marked "RED DOT HERE". A red dot appears.
// 2. Click the green ▶ next to main and choose Debug (the bug icon).
// 3. Open the Memory tab in the Java Buddy panel, then press F8 to go one line at a time.
public class TryMemory {

    static class Pet {
        String name;
        int age;
        Pet friend;

        Pet(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    public static void main(String[] args) {
        int count = 3;                                  // RED DOT HERE
        double price = 4.50;
        String greeting = "Hello";
        int[] scores = {90, 85, 77};

        ArrayList<String> shopping = new ArrayList<>();
        shopping.add("bread");
        shopping.add("milk");

        Pet rex = new Pet("Rex", 4);
        Pet tom = new Pet("Tom", 2);
        rex.friend = tom;                               // watch the arrow appear

        HashMap<String, Integer> stock = new HashMap<>();
        stock.put("apples", 12);
        stock.put("pears", 5);

        scores[0] = 100;                                // watch the yellow highlight
        count = count + 1;
        System.out.println(greeting + ", " + rex.name + " and " + rex.friend.name + "! " + count + " " + price);
    }
}
