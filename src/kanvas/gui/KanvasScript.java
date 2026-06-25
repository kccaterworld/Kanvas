package kanvas.gui;

import kanvas.libs.math.KVector;
import kanvas.runtime.KanvasStdlib;

import java.awt.*;
import javax.swing.SwingUtilities;

public abstract class KanvasScript extends KanvasStdlib {
    // DrawMode constants so .kvs files can write CORNER, CENTER, and allat directly
    public static final DrawMode CORNER = DrawMode.CORNER;
    public static final DrawMode CORNERS = DrawMode.CORNERS;
    public static final DrawMode CENTER  = DrawMode.CENTER;
    public static final DrawMode RADIUS  = DrawMode.RADIUS;

    int bgColor = color(255);
    volatile boolean loop = true;
    boolean resizable = false;
    boolean fullscreen = false;
    DrawMode rectMode = DrawMode.CORNER,
        ellipseMode = DrawMode.CENTER,
        imageMode = DrawMode.CORNER,
        shapeMode = DrawMode.CORNER;

    public int fillColor = color(0);
    public int strokeColor = color(0);
    public boolean fill = true;
    public boolean stroke = true;
    public float strokeWeight = 1;
    public boolean smoothing = true;
    private KVector location = new KVector(20, 20);

    // Runtime references
    KanvasWindow window;
    KanvasGraphics graphics;

    // User accessible variables
    protected volatile boolean mousePressed = false;
    protected volatile boolean focused;
    public float width;
    public float height;
    protected volatile float mouseX;
    protected volatile float mouseY;
    protected volatile float pmouseX;
    protected volatile float pmouseY;
    protected volatile float frameRate = 60;
    protected volatile int frameCount;
    protected int displayHeight;
    protected int displayWidth;
    protected volatile char key;
    protected volatile int keyCode;

    // Constants
    protected final float PI = (float)(Math.PI);
    protected final float HALF_PI = (float)(Math.PI / 2);
    protected final float QUARTER_PI = (float)(Math.PI / 4);
    protected final float TWO_PI = (float)(2 * Math.PI);

    // Entry point called from generated main method
    public final void start() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        displayWidth = screen.width;
        displayHeight = screen.height;
        settings();

        window = new KanvasWindow(this);
        graphics = new KanvasGraphics(this);

        try { SwingUtilities.invokeAndWait(window::open);
        } catch (Exception e) { throw new RuntimeException("Failed to create window", e); }

        Graphics2D setupG2d = window.acquireGraphics();
        graphics.setContext(setupG2d);
        setup();
        setupG2d.dispose();
        window.show();
        Thread renderThread = new Thread(this::renderLoop, "kanvas-render");
        renderThread.setDaemon(true);
        renderThread.start();

        try { window.awaitShutdown();
        } catch (InterruptedException e) { System.out.println("Main thread interrupted: " + e.getMessage()); }
    }

    private void renderLoop() {
        while (window.isRunning()) {
            long frameStart = System.nanoTime();
            Graphics2D g2d = window.acquireGraphics();
            graphics.setContext(g2d);
            draw();
            g2d.dispose();
            window.show();
            frameCount++;
            long budget = (long)(1_000_000_000.0 / frameRate);
            long elapsed = System.nanoTime() - frameStart;
            long sleep = budget - elapsed;
            if (sleep > 0) try { Thread.sleep(sleep / 1_000_000, (int)(sleep % 1_000_000));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            if (!loop) try { synchronized(this) { wait(); }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    // Runtime Methods
    public void settings() {
        frameRate = 60; frameCount = 0;
        smooth(); windowed();
        size(100, 100);
        background(200);
    }
    public void setup() { }
    public void draw() { }
    public void dispose() { }

    public void redraw() {
        if (window == null) return;
        Graphics2D g2d = window.acquireGraphics();
        graphics.setContext(g2d);
        draw();
        g2d.dispose();
        window.show();
    }

    public void noLoop() { this.loop = false; }
    public void loop() { this.loop = true; synchronized(this) { notifyAll(); } }
    public void frameRate(float fps) { this.frameRate = fps; }
    public void fullscreen() { this.fullscreen = true; }
    public void windowed() { this.fullscreen = false; }
    public void noSmooth() {
        this.smoothing = false;
        if (graphics != null) graphics.noSmooth();
    }
    public void smooth() {
        this.smoothing = true;
        if (graphics != null) graphics.smooth();
    }

    public void mousePressed() {}
    public void mouseReleased() {}
    public void mouseClicked() {}
    public void mouseDragged() {}
    public void mouseWheel() {}
    public void mouseMoved(float mouseX, float mouseY) {
        this.pmouseX = this.mouseX;
        this.pmouseY = this.mouseY;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public void keyPressed() {}
    public void keyReleased() {}
    public void keyTyped() {}
    public void windowMoved() {}

    public void setLocation(float x, float y) {
        if (window != null) SwingUtilities.invokeLater(() -> window.setLocation((int)x, (int)y));
        this.location.set(x, y);
    }
    public void setResizable(boolean resizable) {
        this.resizable = resizable;
        if (window != null) SwingUtilities.invokeLater(() -> window.setResizable(resizable));
    }
    public void setTitle(String title) {
        if (window != null) SwingUtilities.invokeLater(() -> window.setTitle(title));
    }
    public void resize(int width, int height) {
        this.width = width; this.height = height;
        if (window != null) window.resize(width, height);
    }

    protected void size(float width, float height) {
        this.width = width; this.height = height;
        if (window != null) window.resize((int)width, (int)height);
    }

    protected void background(int gray) { background(gray, gray, gray); }
    protected void background(int r, int g, int b) {
        this.bgColor = color(r, g, b);
        if (graphics != null) graphics.background(r, g, b);
    }

    protected void fill(int gray) { fill(gray, gray, gray); }
    protected void fill(int r, int g, int b) {
        this.fill = true;
        this.fillColor = color(r, g, b);
    }
    protected void noFill() { this.fill = false; }

    protected void stroke(int gray) { stroke(gray, gray, gray); }
    protected void stroke(int r, int g, int b) {
        this.stroke = true;
        this.strokeColor = color(r, g, b);
    }
    protected void noStroke() { this.stroke = false; }
    protected void strokeWeight(float weight) {
        if (graphics != null) this.strokeWeight = weight;
    }

    // Shapes
    protected void point(float x, float y) { if (graphics != null) graphics.point(x, y); }
    protected void line(float x1, float y1, float x2, float y2) { if (graphics != null) graphics.line(x1, y1, x2, y2); }
    protected void arc(float x, float y, float w, float h, float start, float stop) {
        if (graphics == null) return;
        float[] r = resolveToCorner(x, y, w, h, ellipseMode);
        graphics.arc(r[0], r[1], r[2], r[3], start, stop);
    }
    protected void circle(float x, float y, float d) { ellipse(x, y, d, d); }
    protected void ellipse(float x, float y, float w, float h) {
        if (graphics == null) return;
        float[] r = resolveToCorner(x, y, w, h, ellipseMode);
        graphics.ellipse(r[0], r[1], r[2], r[3]);
    }
    protected void square(float x, float y, float s) { rect(x, y, s, s); }
    protected void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        if (graphics != null) graphics.quad(x1, y1, x2, y2, x3, y3, x4, y4);
    }
    protected void rect(float x, float y, float w, float h) {
        if (graphics == null) return;
        float[] r = resolveToCorner(x, y, w, h, rectMode);
        graphics.rect(r[0], r[1], r[2], r[3]);
    }
    protected void triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        if (graphics != null) graphics.triangle(x1, y1, x2, y2, x3, y3);
    }

    private static float[] resolveToCorner(float x, float y, float w, float h, DrawMode mode) {
        switch (mode) {
            case CORNER:  return new float[]{x, y, w, h};
            case CORNERS: return  new float[]{x, y, w - x, h - y};
            case CENTER:  return new float[]{x - w / 2, y - h / 2, w, h};
            case RADIUS:  return new float[]{x - w, y - h, w * 2, h * 2};
            default: throw new IllegalStateException("Unexpected DrawMode: " + mode);
        }
    }

    protected void imageMode(DrawMode mode) { this.imageMode = mode; }
    protected void rectMode(DrawMode mode) { this.rectMode = mode; }
    protected void ellipseMode(DrawMode mode) { this.ellipseMode = mode; }
    protected void shapeMode(DrawMode mode) { this.shapeMode = mode; }

    // Colors
    public static int color(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
    public static int color(int r, int g, int b) { return color(255, r, g, b); }
    public static int color(int gray) { return color(gray, gray, gray); }
    public static int red(int color) { return (color >> 16) & 0xFF; }
    public static int green(int color) { return (color >> 8) & 0xFF; }
    public static int blue(int color) { return color & 0xFF; }
    public static int alpha(int color) { return (color >> 24) & 0xFF; }
    public static int lerpColor(int c1, int c2, float amt) {
        return color((int)(alpha(c1) + amt * (alpha(c2) - alpha(c1))),
            (int)(red(c1) + amt * (red(c2) - red(c1))),
            (int)(green(c1) + amt * (green(c2) - green(c1))),
            (int)(blue(c1) + amt * (blue(c2) - blue(c1))));
    }
    public static int brightness(int color) { return (int)(0.299*red(color) + 0.587*green(color) + 0.114*blue(color)); }
    public static int hue(int color) {
        int r = red(color), g = green(color), b = blue(color),
        max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return (max == min) ? 0 :
            (max == r) ? (int)(60 * (g - b) / (double)(max - min) + 360) % 360 :
            (max == g) ? (int)(60 * (b - r) / (double)(max - min) + 120) :
            (int)(60 * (r - g) / (double)(max - min) + 240);
    }
    public static int saturation(int color) {
        int r = red(color), g = green(color), b = blue(color),
        max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return (max == 0) ? 0 : (int)(255.0 * (max - min) / max);
    }
}
