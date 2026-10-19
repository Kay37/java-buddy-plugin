package playground;

import java.util.ArrayList;
import java.util.List;

// Rest your mouse on any Java word below (static, final, extends, if, int, the < > ...).
// A Java Buddy card pops up and explains it. Put your text cursor on a word, and its card
// shows in the Java Buddy panel's Concepts tab. This program runs fine: press the green ▶.
public class TryConcepts {

    static final int MAX_PETS = 3;

    enum Size { SMALL, MEDIUM, LARGE }

    record Toy(String name, double price) { }

    interface Pet {
        String sound();
    }

    abstract static class Animal implements Pet {
        protected String name;
        private int age;

        Animal(String name, int age) {
            this.name = name;
            this.age = age;
        }

        int getAge() {
            return age;
        }
    }

    static class Dog extends Animal {
        Dog(String name, int age) {
            super(name, age);
        }

        @Override
        public String sound() {
            return "woof";
        }

        void fetch() {
            System.out.println(name + " fetches the ball");
        }
    }

    static class Cat extends Animal {
        Cat(String name, int age) {
            super(name, age);
        }

        @Override
        public String sound() {
            return "meow";
        }
    }

    // A static method: main can call it just by its name. Hover static, int, return and the ( ).
    static int twice(int number) {
        return number * 2;
    }

    static void greet(String name, int age) {
        System.out.println("Hi " + name + ", you are " + age);
    }

    static void checkAge(int age) throws IllegalArgumentException {
        if (age < 0) {
            throw new IllegalArgumentException("Age can't be negative");
        }
    }

    public static void main(String[] args) {
        int answer = twice(5);
        System.out.println("twice(5) gives back " + answer);
        greet("Ana", 12);

        List<Animal> pets = new ArrayList<>();
        pets.add(new Dog("Rex", 4));
        pets.add(new Cat("Tom", 2));

        for (Animal pet : pets) {
            System.out.println(pet.name + " says " + pet.sound());
            if (pet instanceof Dog dog) {
                dog.fetch();
            } else {
                System.out.println(pet.name + " ignores the ball");
            }
        }

        int[] scores = {90, 85, 77};
        long bigNumber = 8_000_000_000L;
        double average = (scores[0] + scores[1] + scores[2]) / 3.0;
        boolean passed = average >= 80;
        char grade = 'B';
        String message = passed ? "Passed" : "Try again";
        var toy = new Toy("Ball", 2.50);
        Animal nobody = null;
        System.out.println(message + " with " + grade + ", average " + average + ", big " + bigNumber);
        System.out.println(toy + ", nobody is " + nobody);

        Size size = Size.MEDIUM;
        String label = switch (size) {
            case SMALL -> "Fits in a pocket";
            case MEDIUM -> "Fits in a bag";
            default -> "Needs a truck";
        };
        System.out.println(label);

        int count = 0;
        while (count < MAX_PETS) {
            count++;
            if (count == 2) {
                continue;
            }
            System.out.println("Count " + count);
        }

        do {
            count--;
            if (count == 1) {
                break;
            }
        } while (count > 0);

        try {
            checkAge(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("Caught: " + e.getMessage());
        } finally {
            System.out.println("Done!");
        }
    }
}
