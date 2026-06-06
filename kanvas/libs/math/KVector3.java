package kanvas.libs.math;

public class KVector3 extends KVector {
    public double x, y, z;

    public KVector3(double x, double y, double z) {
        super(x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(double x, double y, double z) {
        super.set(x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public void setMagnitude(double mag) {
        if (magnitude() != 0) multiply(mag / magnitude());
    }

    public KVector3 copy() { return new KVector3(x, y, z); }

    public void add(double x, double y, double z) { this.x += x; this.y += y; this.z += z; }
    public void add(KVector3 other) { add(other.x, other.y, other.z); }
    public static KVector3 add(KVector3 a, KVector3 b) { return new KVector3(a.x + b.x, a.y + b.y, a.z + b.z); }

    public void subtract(double x, double y, double z) { this.x -= x; this.y -= y; this.z -= z; }
    public void subtract(KVector3 other) { subtract(other.x, other.y, other.z); }
    public static KVector3 subtract(KVector3 a, KVector3 b) { return new KVector3(a.x - b.x, a.y - b.y, a.z - b.z); }

    public void multiply(double scalar) { this.x *= scalar; this.y *= scalar; this.z *= scalar; }
    public static KVector3 multiply(KVector3 vector, double scalar) { return new KVector3(vector.x * scalar, vector.y * scalar, vector.z * scalar); }
    public void divide(double scalar) { if (scalar != 0) { this.x /= scalar; this.y /= scalar; this.z /= scalar; } }
    public static KVector3 divide(KVector3 vector, double scalar) { return (scalar != 0) ? new KVector3(vector.x / scalar, vector.y / scalar, vector.z / scalar) : new KVector3(0, 0, 0); }

    public double dot(KVector3 other) { return this.x * other.x + this.y * other.y + this.z * other.z; }
    public KVector3 cross(KVector3 other) { return new KVector3(this.y * other.z - this.z * other.y, this.z * other.x - this.x * other.z, this.x * other.y - this.y * other.x); }
    public double magnitude() { return Math.sqrt(x * x + y * y + z * z); }    

    public KVector3 normalize() { double mag = magnitude();
        return (mag == 0) ? new KVector3(0, 0, 0) : new KVector3(x / mag, y / mag, z / mag); }

    public static KVector3 random3D() { return new KVector3(Math.random(), Math.random(), Math.random()); }
    public static void random3D(KVector3 target) { target.set(Math.random(), Math.random(), Math.random()); }

    public double dist(double x, double y, double z) { return dist(this, new KVector3(x, y, z)); }
    public double dist(KVector3 other) { return dist(this, other); }
    public static double dist(KVector3 a, KVector3 b) { return Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y) + (b.z - a.z) * (b.z - a.z)); }

    public KVector3 lerp(double x, double y, double z, double t) { return lerp(this, new KVector3(x, y, z), t); }
    public KVector3 lerp(KVector3 other, double t) { return lerp(this, other, t); }
    public static KVector3 lerp(KVector3 a, KVector3 b, double t) { return new KVector3((a.x + t * (b.x - a.x)), (a.y + t * (b.y - a.y)), (a.z + t * (b.z - a.z))); }

    @Override
    public String toString() {
        return String.format("KVector3(%.3f, %.3f, %.3f)", x, y, z);
    }

}
