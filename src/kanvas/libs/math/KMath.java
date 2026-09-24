package kanvas.libs.math;

public class KMath {
    /** Feilds */
    public static final double PI = Math.PI;
    public static final double TAU = 2 * Math.PI;
    public static final double HALF_PI = Math.PI / 2;
    public static final double QUARTER_PI = Math.PI / 4;
    public static final double E = Math.E;

    /** Methods */
    public static double sqrt(double value) {
        if (value < 0) {
            throw new IllegalArgumentException("Input must be a non-negative number");
        }
        return Math.sqrt(value);
    }

    public static double pow(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    public static double abs(double value) {
        return Math.abs(value);
    }

    public static double random() {
        return Math.random();
    }

    public static double random(double min, double max) {
        if (min >= max) {
                throw new IllegalArgumentException("Min must be less than max");
            }
        return min + (max - min) * Math.random();
    }

    /** TRIGONOMETRIC FUNCTIONS */
    // Standard
    public static double sin(double angle) { return Math.sin(angle); }
    public static double cos(double angle) { return Math.cos(angle); }
    public static double tan(double angle) { return Math.tan(angle); }

    // Reciprocal
    public static double csc(double angle) { return 1.0 / Math.sin(angle); }
    public static double sec(double angle) { return 1.0 / Math.cos(angle); }
    public static double cot(double angle) { return 1.0 / Math.tan(angle); }

    // Inverse
    public static double asin(double value) {
        if (value < -1 || value > 1) {
            throw new IllegalArgumentException("Input must be in the domain [-1, 1]");
        }
        return Math.asin(value);
    }
    public static double acsc(double value) {
        if (value > -1 && value < 1) {
            throw new IllegalArgumentException("Input must be in the domain [-1, 1]");
        }
        return Math.asin(1.0 / value);
    }

    public static double acos(double value) {
        if (value < -1 || value > 1) {
            throw new IllegalArgumentException("Input must be in the domain [-1, 1]");
        }
        return Math.acos(value);
    }

    public static double asec(double value) {
        if (value > -1 && value < 1) {
            throw new IllegalArgumentException("Input must be in the domain [-1, 1]");
        }
        return Math.acos(1.0 / value);
    }

    public static double atan(double value) {
        return Math.atan(value);
    }

    public static double acot(double value) {
        return Math.atan(1.0 / value);
    }

    public static double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

}
