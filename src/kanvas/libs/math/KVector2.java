package kanvas.libs.math;

public class KVector2 extends KVector {
    public double x, y;

    public KVector2(double x, double y) {
        super(x, y);
        this.x = x;
        this.y = y;
    }

    /** PVector-compatible setter; returns {@code this} for chaining. */
    public KVector2 set(double x, double y) {
        super.set(x, y);
        this.x = x;
        this.y = y;
        return this;
    }

    /** Alias for {@link #setMagnitude(double)}. */
    public KVector2 setMag(double mag) { return setMagnitude(mag); }
    public KVector2 setMagnitude(double mag) {
        if (magnitude() != 0) multiply(mag / magnitude());
        return this;
    }
    public void setAngle(double angle) {
        double mag = magnitude();
        x = KMath.cos(angle) * mag;
        y = KMath.sin(angle) * mag;
        super.set(x, y);
    }

    public KVector2 copy() { return new KVector2(x, y); }

    // --- PVector-style mutating operations (all return this for chaining) ---

    public KVector2 add(double x, double y) { this.x += x; this.y += y; super.set(this.x, this.y); return this; }
    public KVector2 add(KVector2 other) { return add(other.x, other.y); }
    public static KVector2 add(KVector2 a, KVector2 b) { return new KVector2(a.x + b.x, a.y + b.y); }

    public KVector2 subtract(double x, double y) { this.x -= x; this.y -= y; super.set(this.x, this.y); return this; }
    public KVector2 subtract(KVector2 other) { return subtract(other.x, other.y); }
    public static KVector2 subtract(KVector2 a, KVector2 b) { return new KVector2(a.x - b.x, a.y - b.y); }

    /** PVector-style alias for {@link #subtract}. */
    public KVector2 sub(double x, double y) { return subtract(x, y); }
    public KVector2 sub(KVector2 other) { return subtract(other); }
    public static KVector2 sub(KVector2 a, KVector2 b) { return subtract(a, b); }

    public KVector2 multiply(double scalar) { this.x *= scalar; this.y *= scalar; super.set(this.x, this.y); return this; }
    public static KVector2 multiply(KVector2 vector, double scalar) { return new KVector2(vector.x * scalar, vector.y * scalar); }

    /** PVector-style alias for {@link #multiply}. */
    public KVector2 mult(double scalar) { return multiply(scalar); }
    public static KVector2 mult(KVector2 vector, double scalar) { return multiply(vector, scalar); }

    public KVector2 divide(double scalar) { if (scalar != 0) { this.x /= scalar; this.y /= scalar; super.set(this.x, this.y); } return this; }
    public static KVector2 divide(KVector2 vector, double scalar) { return (scalar != 0) ? new KVector2(vector.x / scalar, vector.y / scalar) : new KVector2(0, 0); }

    /** PVector-style alias for {@link #divide}. */
    public KVector2 div(double scalar) { return divide(scalar); }
    public static KVector2 div(KVector2 vector, double scalar) { return divide(vector, scalar); }

    // --- PVector-style math ---

    /** PVector-style alias for {@link #magnitude()}. */
    public double mag() { return magnitude(); }
    public double magSq() { return x * x + y * y; }

    public double dot(KVector2 other) { return this.x * other.x + this.y * other.y; }
    public double cross(KVector2 other) { return this.x * other.y - this.y * other.x; }
    public double magnitude() { return KMath.sqrt(x * x + y * y); }

    /** PVector-style alias for {@link #angle()}. */
    public double heading() { return KMath.atan2(y, x); }
    public double angle() { return KMath.atan2(y, x); }
    public double angleBetween(KVector2 other) { return KMath.atan2(cross(other), dot(other)); }

    /**
     * Normalizes this vector in place and returns {@code this}
     * (PVector semantics). Previous behaviour returned a new vector; any
     * callers wanting the old copy semantics should call {@code copy().normalize()}.
     */
    public KVector2 normalize() {
        double mag = magnitude();
        if (mag != 0) { x /= mag; y /= mag; super.set(x, y); }
        return this;
    }

    /** Rotates this vector by the given angle (radians) and returns {@code this}. */
    public KVector2 rotate(double angle) {
        double cos = KMath.cos(angle), sin = KMath.sin(angle);
        double rx = x * cos - y * sin;
        double ry = x * sin + y * cos;
        x = rx; y = ry;
        super.set(x, y);
        return this;
    }

    /** Clamps this vector's magnitude to {@code max} and returns {@code this}. */
    public KVector2 limit(double max) {
        double m = magSq();
        if (m > max * max) { double s = max / KMath.sqrt(m); x *= s; y *= s; super.set(x, y); }
        return this;
    }

    /** Linearly interpolates this vector towards {@code other} and returns {@code this}. */
    public KVector2 lerp(KVector2 other, double t) {
        x += (other.x - x) * t;
        y += (other.y - y) * t;
        super.set(x, y);
        return this;
    }
    public KVector2 lerp(double x2, double y2, double t) { return lerp(new KVector2(x2, y2), t); }
    public static KVector2 lerp(KVector2 a, KVector2 b, double t) { return new KVector2((a.x + t * (b.x - a.x)), (a.y + t * (b.y - a.y))); }

    public double dist(double x, double y) { return dist(this, new KVector2(x, y)); }
    public double dist(KVector2 other) { return dist(this, other); }
    public static double dist(KVector2 a, KVector2 b) { return KMath.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)); }

    public static KVector2 random2D() { return random2D(new KVector2(0, 0)); }
    public static KVector2 random2D(KVector2 target) { return fromAngle(target, KMath.random() * 2 * KMath.PI); }
    public static KVector2 fromAngle(double angle) { return fromAngle(new KVector2(0, 0), angle); }
    public static KVector2 fromAngle(KVector2 target, double angle) {
        target.set(KMath.cos(angle), KMath.sin(angle));
        return target;
    }

    @Override
    public String toString() {
        return String.format("KVector2(%.3f, %.3f)", x, y);
    }
}
