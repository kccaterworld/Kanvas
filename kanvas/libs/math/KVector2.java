package kanvas.libs.math;

public class KVector2 extends KVector {
    public double x, y;

    public KVector2(double x, double y) {
        super(x, y);
        this.x = x;
        this.y = y;
    }

    public void set(double x, double y) {
        super.set(x, y);
        this.x = x;
        this.y = y;
    }

    public KVector2 copy() { return new KVector2(x, y); }

    public void add(double x, double y) { this.x += x; this.y += y; }
    public void add(KVector2 other) { add(other.x, other.y); }
    public static KVector2 add(KVector2 a, KVector2 b) { return new KVector2(a.x + b.x, a.y + b.y); }

    public void subtract(double x, double y) { this.x -= x; this.y -= y; }
    public void subtract(KVector2 other) { subtract(other.x, other.y); }
    public static KVector2 subtract(KVector2 a, KVector2 b) { return new KVector2(a.x - b.x, a.y - b.y); }

    public void multiply(double scalar) { this.x *= scalar; this.y *= scalar; }
    public static KVector2 multiply(KVector2 vector, double scalar) { return new KVector2(vector.x * scalar, vector.y * scalar); }
    public void divide(double scalar) { if (scalar != 0) { this.x /= scalar; this.y /= scalar; } }
    public static KVector2 divide(KVector2 vector, double scalar) { return (scalar != 0) ? new KVector2(vector.x / scalar, vector.y / scalar) : new KVector2(0, 0); }

    public double dot(KVector2 other) { return this.x * other.x + this.y * other.y; }
    public double magnitude() { return Math.sqrt(x * x + y * y); }

    public KVector2 normalize() { double mag = magnitude();
        return (mag == 0) ? new KVector2(0, 0) : new KVector2(x / mag, y / mag); }

    public static KVector2 random2D() { return random2D(new KVector2(0, 0)); }
    public static KVector2 random2D(KVector2 target) { return fromAngle(target, Math.random() * 2 * Math.PI); }
    public static KVector2 fromAngle(double angle) { return fromAngle(new KVector2(0, 0), angle); }
    public static KVector2 fromAngle(KVector2 target, double angle) {
        target.set(Math.cos(angle), Math.sin(angle));
        return target;
    }

    public static double dist(KVector2 a, KVector2 b) { return Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)); }
    public double dist(KVector2 other) { return dist(this, other); }
    public double dist(double x, double y) { return dist(this, new KVector2(x, y)); }

    @Override
    public String toString() {
        return String.format("KVector2(%.3f, %.3f)", x, y);
    }
}
