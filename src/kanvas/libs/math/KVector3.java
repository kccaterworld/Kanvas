package kanvas.libs.math;

public class KVector3 extends KVector {
    public double x, y, z;

    public KVector3(double x, double y, double z) {
        super(x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** PVector-compatible setter; returns {@code this} for chaining. */
    public KVector3 set(double x, double y, double z) {
        super.set(x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /** Alias for {@link #setMagnitude(double)}. */
    public KVector3 setMag(double mag) { return setMagnitude(mag); }
    public KVector3 setMagnitude(double mag) {
        if (magnitude() != 0) multiply(mag / magnitude());
        return this;
    }

    public KVector3 copy() { return new KVector3(x, y, z); }

    // --- PVector-style mutating operations (all return this for chaining) ---

    public KVector3 add(double x, double y, double z) { this.x += x; this.y += y; this.z += z; super.set(this.x, this.y, this.z); return this; }
    public KVector3 add(KVector3 other) { return add(other.x, other.y, other.z); }
    public static KVector3 add(KVector3 a, KVector3 b) { return new KVector3(a.x + b.x, a.y + b.y, a.z + b.z); }

    public KVector3 subtract(double x, double y, double z) { this.x -= x; this.y -= y; this.z -= z; super.set(this.x, this.y, this.z); return this; }
    public KVector3 subtract(KVector3 other) { return subtract(other.x, other.y, other.z); }
    public static KVector3 subtract(KVector3 a, KVector3 b) { return new KVector3(a.x - b.x, a.y - b.y, a.z - b.z); }

    /** PVector-style alias for {@link #subtract}. */
    public KVector3 sub(double x, double y, double z) { return subtract(x, y, z); }
    public KVector3 sub(KVector3 other) { return subtract(other); }
    public static KVector3 sub(KVector3 a, KVector3 b) { return subtract(a, b); }

    public KVector3 multiply(double scalar) { this.x *= scalar; this.y *= scalar; this.z *= scalar; super.set(this.x, this.y, this.z); return this; }
    public static KVector3 multiply(KVector3 vector, double scalar) { return new KVector3(vector.x * scalar, vector.y * scalar, vector.z * scalar); }

    /** PVector-style alias for {@link #multiply}. */
    public KVector3 mult(double scalar) { return multiply(scalar); }
    public static KVector3 mult(KVector3 vector, double scalar) { return multiply(vector, scalar); }

    public KVector3 divide(double scalar) { if (scalar != 0) { this.x /= scalar; this.y /= scalar; this.z /= scalar; super.set(this.x, this.y, this.z); } return this; }
    public static KVector3 divide(KVector3 vector, double scalar) { return (scalar != 0) ? new KVector3(vector.x / scalar, vector.y / scalar, vector.z / scalar) : new KVector3(0, 0, 0); }

    /** PVector-style alias for {@link #divide}. */
    public KVector3 div(double scalar) { return divide(scalar); }
    public static KVector3 div(KVector3 vector, double scalar) { return divide(vector, scalar); }

    // --- PVector-style math ---

    /** PVector-style alias for {@link #magnitude()}. */
    public double mag() { return magnitude(); }
    public double magSq() { return x * x + y * y + z * z; }

    public double dot(KVector3 other) { return this.x * other.x + this.y * other.y + this.z * other.z; }
    public KVector3 cross(KVector3 other) { return new KVector3(this.y * other.z - this.z * other.y, this.z * other.x - this.x * other.z, this.x * other.y - this.y * other.x); }
    public double magnitude() { return KMath.sqrt(x * x + y * y + z * z); }

    /**
     * Normalizes this vector in place and returns {@code this}
     * (PVector semantics). Previous behaviour returned a new vector; any
     * callers wanting the old copy semantics should call {@code copy().normalize()}.
     */
    public KVector3 normalize() {
        double mag = magnitude();
        if (mag != 0) { x /= mag; y /= mag; z /= mag; super.set(x, y, z); }
        return this;
    }

    /** Clamps this vector's magnitude to {@code max} and returns {@code this}. */
    public KVector3 limit(double max) {
        double m = magSq();
        if (m > max * max) { double s = max / KMath.sqrt(m); x *= s; y *= s; z *= s; super.set(x, y, z); }
        return this;
    }

    /** Linearly interpolates this vector towards {@code other} and returns {@code this}. */
    public KVector3 lerp(KVector3 other, double t) {
        x += (other.x - x) * t;
        y += (other.y - y) * t;
        z += (other.z - z) * t;
        super.set(x, y, z);
        return this;
    }
    public KVector3 lerp(double x2, double y2, double z2, double t) { return lerp(new KVector3(x2, y2, z2), t); }
    public static KVector3 lerp(KVector3 a, KVector3 b, double t) { return new KVector3((a.x + t * (b.x - a.x)), (a.y + t * (b.y - a.y)), (a.z + t * (b.z - a.z))); }

    public double dist(double x, double y, double z) { return dist(this, new KVector3(x, y, z)); }
    public double dist(KVector3 other) { return dist(this, other); }
    public static double dist(KVector3 a, KVector3 b) { return KMath.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y) + (b.z - a.z) * (b.z - a.z)); }

    public static KVector3 random3D() { return new KVector3(KMath.random(), KMath.random(), KMath.random()); }
    public static void random3D(KVector3 target) { target.set(KMath.random(), KMath.random(), KMath.random()); }

    @Override
    public String toString() {
        return String.format("KVector3(%.3f, %.3f, %.3f)", x, y, z);
    }

}
