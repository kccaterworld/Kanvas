package kanvas.runtime;

import kanvas.libs.math.KVector;

public abstract class KanvasScript  extends KanvasStdlib {
    private int bgColor = color(255);
    private int fillColor = color(0);
    private int strokeColor = color(0);
    private boolean fill = true;
    private boolean stroke = true;
    private boolean loop = true;
    private boolean resizable = false;
    private boolean fullscreen = false;
    private boolean smoothing = true;
    private KVector location = new KVector(20, 20);

    protected boolean mousePressed = false;
    protected boolean focused;
    protected float width;
    protected float height;
    protected float mouseX;
    protected float mouseY;
    protected float pmouseX;
    protected float pmouseY;
    protected float frameRate = 60;
    protected int frameCount = 0;
    protected int displayHeight;
    protected int displayWidth;

    // Processing-like lifecycle
    public abstract void settings();
    public abstract void setup();
    public abstract void draw();
    public void redraw() { draw(); }
    public void noLoop() { this.loop = false; }
    public void loop() { this.loop = true; }
    public void frameRate(float fps) { this.frameRate = fps; }
    public void fullscreen() { this.fullscreen = true; }
    public void noSmooth() { this.smoothing = false; }
    public void smooth() { this.smoothing = true; }

    public abstract void mousePressed();
    public abstract void mouseReleased();
    public abstract void mouseClicked();
    public abstract void mouseDragged();
    public abstract void mouseWheel();
    public void mouseMoved(float mouseX, float mouseY) {
        this.pmouseX = this.mouseX;
        this.pmouseY = this.mouseY;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public abstract void keyPressed();
    public abstract void keyReleased();
    public abstract void keyTyped();

    public void setLocation(float x, float y) { this.location.set(x, y); }
    public void setResizable(boolean resizable) { this.resizable = resizable; }
    public void setTitle(String title) { /* No-op for now */ }
    public void resize(int width, int height) {
        this.width = width; this.height = height;
    }
    public abstract void windowMoved();

    protected void size(float width, float height) {
        this.width = width; this.height = height;
    }
    protected void background(int r, int g, int b) { this.bgColor = color(r, g, b); }
    protected void fill(int r, int g, int b) {
        this.fill = true;
        this.fillColor = color(r, g, b);
    }
    protected void noFill() { this.fill = false; }
    protected void stroke(int r, int g, int b) {
        this.stroke = true;
        this.strokeColor = color(r, g, b);
    }

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

    // Constants
    protected final float PI = (float)Math.PI;
    protected final float HALF_PI = (float)Math.PI / 2;
    protected final float QUARTER_PI = (float)Math.PI / 4;
    protected final float TWO_PI = (float)(2 * Math.PI);
}
