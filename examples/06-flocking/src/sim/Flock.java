package sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Flock {
    private static final double VIEW = 50, PERSONAL_SPACE = 20, MAX_SPEED = 3;

    private final List<Bird> birds = new ArrayList<>();
    private final Random random = new Random(7);
    private final double width, height;

    private volatile int count;
    private volatile double averageSpeed;

    public Flock(int size, double width, double height) {
        this.width = width;
        this.height = height;
        for (int i = 0; i < size; i++) add(random.nextDouble() * width, random.nextDouble() * height);
    }

    public void add(double x, double y) {
        double angle = random.nextDouble() * Math.PI * 2;
        birds.add(new Bird(x, y, Math.cos(angle) * 2, Math.sin(angle) * 2));
        count = birds.size();
    }

    public void removeNear(double x, double y, double radius) {
        Bird closest = null;
        double best = radius;
        for (Bird bird : birds) {
            double d = Math.hypot(bird.x - x, bird.y - y);
            if (d <= best) {
                best = d;
                closest = bird;
            }
        }
        if (closest != null) birds.remove(closest);
        count = birds.size();
    }

    public List<Bird> birds() { return birds; }

    public int count() { return count; }

    public double averageSpeed() { return averageSpeed; }

    public void step() {
        for (Bird bird : birds) steer(bird);
        double totalSpeed = 0;
        for (Bird bird : birds) {
            bird.limitSpeed(MAX_SPEED);
            bird.x = (bird.x + bird.vx + width) % width;
            bird.y = (bird.y + bird.vy + height) % height;
            totalSpeed += bird.speed();
        }
        averageSpeed = birds.isEmpty() ? 0 : totalSpeed / birds.size();
    }

    private void steer(Bird bird) {
        double sepX = 0, sepY = 0, alignX = 0, alignY = 0, centerX = 0, centerY = 0;
        int neighbours = 0;
        for (Bird other : birds) {
            if (other == bird) continue;
            double dx = other.x - bird.x, dy = other.y - bird.y, d = Math.hypot(dx, dy);
            if (d > VIEW) continue;
            neighbours++;
            alignX += other.vx;
            alignY += other.vy;
            centerX += other.x;
            centerY += other.y;
            if (d < PERSONAL_SPACE && d > 0) {
                sepX -= dx / d;
                sepY -= dy / d;
            }
        }
        if (neighbours == 0) return;
        bird.vx += sepX * 0.05 + (alignX / neighbours - bird.vx) * 0.05 + (centerX / neighbours - bird.x) * 0.0005;
        bird.vy += sepY * 0.05 + (alignY / neighbours - bird.vy) * 0.05 + (centerY / neighbours - bird.y) * 0.0005;
    }
}
