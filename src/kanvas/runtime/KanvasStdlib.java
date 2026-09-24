package kanvas.runtime;

import java.util.*;

/**
 * Kanvas standard library providing Processing-compatible functions for
 * math, graphics, input, and utility operations.
 * 
 * All methods are static and can be called directly from Kanvas scripts.
 */
public class KanvasStdlib {
    // Global variables
    private static final Map<String, Object> globals = new HashMap<>();
    
    // Random number generation with seed control
    private static Random random = new Random();
    
    // Perlin noise state
    private static PerlinNoise perlinNoise = new PerlinNoise();

    /**
     * Sets a global variable accessible throughout the sketch.
     * @param name the variable name
     * @param value the value to store
     */
    public static void setGlobal(String name, Object value) { globals.put(name, value); }
    
    /**
     * Gets a global variable value.
     * @param name the variable name
     * @return the stored value, or null if not found
     */
    public static Object getGlobal(String name) { return globals.get(name); }
    
    /**
     * Checks if a global variable exists.
     * @param name the variable name
     * @return true if the variable has been set
     */
    public static boolean hasGlobal(String name) { return globals.containsKey(name); }

    // ============ Printing ============
    
    /**
     * Prints a value without a newline.
     * @param arg the value to print
     */
    public static void print(Object arg) { System.out.print(arg); }
    
    /**
     * Prints a value with a newline.
     * @param arg the value to print
     */
    public static void println(Object arg) { System.out.println(arg); }
    
    /**
     * Prints formatted output similar to printf.
     * @param format the format string
     * @param args the arguments to format
     */
    public static void printf(String format, Object... args) { System.out.printf(format, args); }

    // ============ Basic Math ============
    
    /**
     * Returns the maximum of two values.
     * @param a the first value
     * @param b the second value
     * @return the larger value
     */
    public static double max(double a, double b) { return Math.max(a, b); }
    
    /**
     * Returns the minimum of two values.
     * @param a the first value
     * @param b the second value
     * @return the smaller value
     */
    public static double min(double a, double b) { return Math.min(a, b); }
    
    /**
     * Constrains a value between a minimum and maximum.
     * @param value the value to constrain
     * @param min the minimum bound
     * @param max the maximum bound
     * @return the constrained value
     */
    public static double constrain(double value, double min, double max) { 
        return Math.max(min, Math.min(max, value)); 
    }
    
    /**
     * Returns the floor of a value (largest integer <= value).
     * @param value the value to floor
     * @return the floored value
     */
    public static double floor(double value) { return Math.floor(value); }
    
    /**
     * Returns the ceiling of a value (smallest integer >= value).
     * @param value the value to ceil
     * @return the ceiling value
     */
    public static double ceil(double value) { return Math.ceil(value); }
    
    /**
     * Returns the absolute value.
     * @param value the input value
     * @return the absolute value
     */
    public static double abs(double value) { return Math.abs(value); }
    
    /**
     * Returns the square root of a value.
     * @param value the input value
     * @return the square root
     */
    public static double sqrt(double value) { return Math.sqrt(value); }
    
    /**
     * Returns base raised to the power of exponent.
     * @param base the base
     * @param exponent the exponent
     * @return base^exponent
     */
    public static double pow(double base, double exponent) { return Math.pow(base, exponent); }
    
    /**
     * Returns e raised to the power of value (exponential function).
     * @param value the exponent
     * @return e^value
     */
    public static double exp(double value) { return Math.exp(value); }
    
    /**
     * Returns the natural logarithm of a value.
     * @param value the input value
     * @return ln(value)
     */
    public static double log(double value) { return Math.log(value); }

    // ============ Trigonometry ============
    
    /**
     * Returns the sine of an angle in radians.
     * @param radians the angle in radians
     * @return the sine value
     */
    public static double sin(double radians) { return Math.sin(radians); }
    
    /**
     * Returns the cosine of an angle in radians.
     * @param radians the angle in radians
     * @return the cosine value
     */
    public static double cos(double radians) { return Math.cos(radians); }
    
    /**
     * Returns the tangent of an angle in radians.
     * @param radians the angle in radians
     * @return the tangent value
     */
    public static double tan(double radians) { return Math.tan(radians); }
    
    /**
     * Returns the arc sine of a value (inverse sine).
     * @param value a value between -1 and 1
     * @return the angle in radians
     */
    public static double asin(double value) { return Math.asin(value); }
    
    /**
     * Returns the arc cosine of a value (inverse cosine).
     * @param value a value between -1 and 1
     * @return the angle in radians
     */
    public static double acos(double value) { return Math.acos(value); }
    
    /**
     * Returns the arc tangent of a value.
     * @param value the input value
     * @return the angle in radians
     */
    public static double atan(double value) { return Math.atan(value); }
    
    /**
     * Returns the angle in radians between a point (x, y) and the positive x-axis.
     * @param y the y coordinate
     * @param x the x coordinate
     * @return the angle in radians
     */
    public static double atan2(double y, double x) { return Math.atan2(y, x); }
    
    /**
     * Converts degrees to radians.
     * @param degrees the angle in degrees
     * @return the angle in radians
     */
    public static double radians(double degrees) { return Math.toRadians(degrees); }
    
    /**
     * Converts radians to degrees.
     * @param radians the angle in radians
     * @return the angle in degrees
     */
    public static double degrees(double radians) { return Math.toDegrees(radians); }

    // ============ Mapping & Interpolation ============
    
    /**
     * Maps a value from one range to another.
     * For example, map(5, 0, 10, 0, 100) returns 50.
     * @param value the value to map
     * @param start1 the lower bound of the input range
     * @param stop1 the upper bound of the input range
     * @param start2 the lower bound of the output range
     * @param stop2 the upper bound of the output range
     * @return the mapped value
     */
    public static double map(double value, double start1, double stop1, double start2, double stop2) {
        return start2 + (value - start1) * (stop2 - start2) / (stop1 - start1);
    }
    
    /**
     * Linear interpolation between two values.
     * @param a the start value
     * @param b the end value
     * @param amt the interpolation amount (0 to 1)
     * @return the interpolated value
     */
    public static double lerp(double a, double b, double amt) {
        return a + (b - a) * amt;
    }
    
    /**
     * Normalizes a value to the range 0-1 based on a min and max.
     * @param value the value to normalize
     * @param start the lower bound of the input range
     * @param stop the upper bound of the input range
     * @return the normalized value between 0 and 1
     */
    public static double norm(double value, double start, double stop) {
        return (value - start) / (stop - start);
    }
    
    /**
     * Calculates the distance between two points.
     * @param x1 the x coordinate of the first point
     * @param y1 the y coordinate of the first point
     * @param x2 the x coordinate of the second point
     * @param y2 the y coordinate of the second point
     * @return the Euclidean distance
     */
    public static double dist(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ============ Random Numbers ============
    
    /**
     * Sets the seed for the random number generator, enabling reproducible sequences.
     * @param seed the seed value
     */
    public static void randomSeed(long seed) {
        random = new Random(seed);
    }
    
    /**
     * Returns a random number between 0 (inclusive) and max (exclusive).
     * @param max the upper bound (exclusive)
     * @return a random value
     */
    public static double random(double max) {
        return random.nextDouble() * max;
    }
    
    /**
     * Returns a random number between min (inclusive) and max (exclusive).
     * @param min the lower bound (inclusive)
     * @param max the upper bound (exclusive)
     * @return a random value
     */
    public static double random(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
    
    /**
     * Returns a random number with a Gaussian (normal) distribution.
     * The mean is 0 and the standard deviation is 1.
     * @return a random value
     */
    public static double randomGaussian() {
        return random.nextGaussian();
    }

    // ============ Noise ============
    
    /**
     * Returns Perlin noise value for the given coordinate(s).
     * Returns a value between 0 and 1.
     * @param x the x coordinate
     * @return the noise value
     */
    public static double noise(double x) {
        return perlinNoise.noise(x);
    }
    
    /**
     * Returns 2D Perlin noise value for the given coordinates.
     * Returns a value between 0 and 1.
     * @param x the x coordinate
     * @param y the y coordinate
     * @return the noise value
     */
    public static double noise(double x, double y) {
        return perlinNoise.noise(x, y);
    }
    
    /**
     * Returns 3D Perlin noise value for the given coordinates.
     * Returns a value between 0 and 1.
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return the noise value
     */
    public static double noise(double x, double y, double z) {
        return perlinNoise.noise(x, y, z);
    }
    
    /**
     * Sets the seed for the noise generator, enabling reproducible noise sequences.
     * @param seed the seed value
     */
    public static void noiseSeed(long seed) {
        perlinNoise = new PerlinNoise(seed);
    }

    // ============ Colors ============
    
    /**
     * Creates a color with ARGB values.
     * @param a the alpha (transparency) value (0-255)
     * @param r the red component (0-255)
     * @param g the green component (0-255)
     * @param b the blue component (0-255)
     * @return the color as an integer
     */
    public static int color(int a, int r, int g, int b) { 
        return (a << 24) | (r << 16) | (g << 8) | b; 
    }
    
    /**
     * Creates a color with RGB values (full opacity).
     * @param r the red component (0-255)
     * @param g the green component (0-255)
     * @param b the blue component (0-255)
     * @return the color as an integer
     */
    public static int color(int r, int g, int b) { 
        return color(255, r, g, b); 
    }
    
    /**
     * Creates a grayscale color (full opacity).
     * @param gray the gray level (0-255, where 0 is black and 255 is white)
     * @return the color as an integer
     */
    public static int color(int gray) { 
        return color(gray, gray, gray); 
    }
    
    /**
     * Extracts the red component from a color.
     * @param color the color integer
     * @return the red component (0-255)
     */
    public static int red(int color) { 
        return (color >> 16) & 0xFF; 
    }
    
    /**
     * Extracts the green component from a color.
     * @param color the color integer
     * @return the green component (0-255)
     */
    public static int green(int color) { 
        return (color >> 8) & 0xFF; 
    }
    
    /**
     * Extracts the blue component from a color.
     * @param color the color integer
     * @return the blue component (0-255)
     */
    public static int blue(int color) { 
        return color & 0xFF; 
    }
    
    /**
     * Extracts the alpha (transparency) component from a color.
     * @param color the color integer
     * @return the alpha component (0-255)
     */
    public static int alpha(int color) { 
        return (color >> 24) & 0xFF; 
    }
    
    /**
     * Interpolates between two colors.
     * @param c1 the start color
     * @param c2 the end color
     * @param amt the interpolation amount (0 to 1)
     * @return the interpolated color
     */
    public static int lerpColor(int c1, int c2, float amt) {
        return color((int)(alpha(c1) + amt * (alpha(c2) - alpha(c1))),
            (int)(red(c1) + amt * (red(c2) - red(c1))),
            (int)(green(c1) + amt * (green(c2) - green(c1))),
            (int)(blue(c1) + amt * (blue(c2) - blue(c1))));
    }
    
    /**
     * Calculates the brightness of a color.
     * @param color the color integer
     * @return the brightness value (0-255)
     */
    public static int brightness(int color) { 
        return (int)(0.299*red(color) + 0.587*green(color) + 0.114*blue(color)); 
    }
    
    /**
     * Extracts the hue component of a color.
     * @param color the color integer
     * @return the hue value in degrees (0-360)
     */
    public static int hue(int color) {
        int r = red(color), g = green(color), b = blue(color),
        max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return (max == min) ? 0 :
            (max == r) ? (int)(60 * (g - b) / (double)(max - min) + 360) % 360 :
            (max == g) ? (int)(60 * (b - r) / (double)(max - min) + 120) :
            (int)(60 * (r - g) / (double)(max - min) + 240);
    }
    
    /**
     * Extracts the saturation component of a color.
     * @param color the color integer
     * @return the saturation value (0-255)
     */
    public static int saturation(int color) {
        int r = red(color), g = green(color), b = blue(color),
        max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return (max == 0) ? 0 : (int)(255.0 * (max - min) / max);
    }

    /**
     * Simple Perlin noise implementation.
     * Based on classic Perlin noise algorithm.
     */
    private static class PerlinNoise {
        private final int[] permutation;
        private final Random rand;

        PerlinNoise() {
            this.rand = new Random();
            this.permutation = buildPermutation(rand.nextLong());
        }

        PerlinNoise(long seed) {
            this.rand = new Random(seed);
            this.permutation = buildPermutation(seed);
        }

        private int[] buildPermutation(long seed) {
            int[] perm = new int[512];
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            
            Random r = new Random(seed);
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int temp = p[i];
                p[i] = p[j];
                p[j] = temp;
            }
            
            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
            return perm;
        }

        double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        double lerp(double a, double b, double t) {
            return a + t * (b - a);
        }

        double grad(int hash, double x) {
            return ((hash & 1) == 0) ? x : -x;
        }

        double grad(int hash, double x, double y) {
            return ((hash & 1) == 0) ? x : -x + ((hash & 2) == 0 ? y : -y);
        }

        double grad(int hash, double x, double y, double z) {
            int h = hash & 15;
            double u = h < 8 ? x : y;
            double v = h < 8 ? y : z;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        double noise(double x) {
            int xi = (int) Math.floor(x) & 255;
            x -= Math.floor(x);
            double u = fade(x);

            int p0 = permutation[xi];
            int p1 = permutation[xi + 1];

            double g0 = grad(p0, x);
            double g1 = grad(p1, x - 1);

            return (lerp(g0, g1, u) + 1) / 2;
        }

        double noise(double x, double y) {
            int xi = (int) Math.floor(x) & 255;
            int yi = (int) Math.floor(y) & 255;
            x -= Math.floor(x);
            y -= Math.floor(y);
            double u = fade(x);
            double v = fade(y);

            int p00 = permutation[permutation[xi] + yi];
            int p01 = permutation[permutation[xi] + yi + 1];
            int p10 = permutation[permutation[xi + 1] + yi];
            int p11 = permutation[permutation[xi + 1] + yi + 1];

            double g00 = grad(p00, x, y);
            double g10 = grad(p10, x - 1, y);
            double g01 = grad(p01, x, y - 1);
            double g11 = grad(p11, x - 1, y - 1);

            double nx0 = lerp(g00, g10, u);
            double nx1 = lerp(g01, g11, u);
            return (lerp(nx0, nx1, v) + 1) / 2;
        }

        double noise(double x, double y, double z) {
            int xi = (int) Math.floor(x) & 255;
            int yi = (int) Math.floor(y) & 255;
            int zi = (int) Math.floor(z) & 255;
            x -= Math.floor(x);
            y -= Math.floor(y);
            z -= Math.floor(z);
            double u = fade(x);
            double v = fade(y);
            double w = fade(z);

            int p000 = permutation[permutation[permutation[xi] + yi] + zi];
            int p100 = permutation[permutation[permutation[xi + 1] + yi] + zi];
            int p010 = permutation[permutation[permutation[xi] + yi + 1] + zi];
            int p110 = permutation[permutation[permutation[xi + 1] + yi + 1] + zi];
            int p001 = permutation[permutation[permutation[xi] + yi] + zi + 1];
            int p101 = permutation[permutation[permutation[xi + 1] + yi] + zi + 1];
            int p011 = permutation[permutation[permutation[xi] + yi + 1] + zi + 1];
            int p111 = permutation[permutation[permutation[xi + 1] + yi + 1] + zi + 1];

            double g000 = grad(p000, x, y, z);
            double g100 = grad(p100, x - 1, y, z);
            double g010 = grad(p010, x, y - 1, z);
            double g110 = grad(p110, x - 1, y - 1, z);
            double g001 = grad(p001, x, y, z - 1);
            double g101 = grad(p101, x - 1, y, z - 1);
            double g011 = grad(p011, x, y - 1, z - 1);
            double g111 = grad(p111, x - 1, y - 1, z - 1);

            double nx00 = lerp(g000, g100, u);
            double nx10 = lerp(g010, g110, u);
            double nx0 = lerp(nx00, nx10, v);
            double nx01 = lerp(g001, g101, u);
            double nx11 = lerp(g011, g111, u);
            double nx1 = lerp(nx01, nx11, v);
            return (lerp(nx0, nx1, w) + 1) / 2;
        }
    }
}
