import core.Lembot;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Lembot bot = new Lembot();

        String input = scanner.next();
        if (input.equals("q")) {
            bot.announceOfftime();
            System.exit(-9);
        }
    }

}
