package sim;

public class Bird {
    public double x, y, vx, vy;

    public Bird(double x, double y, double vx, double vy) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
    }

    public double speed() { return Math.hypot(vx, vy); }

    public double heading() { return Math.atan2(vy, vx); }

    void limitSpeed(double max) {
        double speed = speed();
        if (speed > max) {
            vx = vx / speed * max;
            vy = vy / speed * max;
        }
    }
}
