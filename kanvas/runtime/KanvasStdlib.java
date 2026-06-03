package kanvas.runtime;

import java.util.*;

public class KanvasStdlib {
    // Global variables
    private static Map<String, Object> globals = new HashMap<>();
    public static void setGlobal(String name, Object value) {
        globals.put(name, value);
    }
    public static Object getGlobal(String name) {
        return globals.get(name);
    }
    public static boolean hasGlobal(String name) {
        return globals.containsKey(name);
    }

    // Printing
    public static void print(Object arg) {
        System.out.print(arg);
    }
    public static void println(Object arg) {
        System.out.println(arg);
    }
    public static void printf(String format, Object... args) {
        System.out.printf(format, args);
    }

    
}
