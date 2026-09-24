package kanvas.gui;

import kanvas.KanvasException;
import kanvas.libs.math.KVector;
import kanvas.runtime.KanvasStdlib;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Stack;
import javax.swing.SwingUtilities;

public abstract class KanvasScript extends KanvasStdlib {
    // DrawMode constants so .kvs files can write CORNER, CENTER, and allat directly
    public static final DrawMode CORNER = DrawMode.CORNER;
    public static final DrawMode CORNERS = DrawMode.CORNERS;
    public static final DrawMode CENTER  = DrawMode.CENTER;
    public static final DrawMode RADIUS  = DrawMode.RADIUS;

    // Mouse button constants (Processing-compatible). Note: LEFT/RIGHT double
    // as arrow-key codes (37/39), matching Processing's shared constant table.
    public static final int LEFT = 37;
    public static final int RIGHT = 39;
    public static final int CENTER_BUTTON = 3;

    // Keyboard event constants (Processing-compatible keyCode values)
    public static final int CODED = 0xFFFF;
    public static final int UP = 38;
    public static final int DOWN = 40;
    public static final int ALT = 18;
    public static final int CONTROL = 17;
    public static final int SHIFT = 16;
    public static final int BACKSPACE = 8;
    public static final int TAB = 9;
    public static final int ENTER = 10;
    public static final int RETURN = 13;
    public static final int ESC = 27;
    public static final int DELETE = 127;
    
    // Shape building constants
    public static final int POINTS = 0;
    public static final int LINES = 1;
    public static final int TRIANGLES = 2;
    public static final int TRIANGLE_STRIP = 3;
    public static final int TRIANGLE_FAN = 4;
    public static final int QUADS = 5;
    public static final int QUAD_STRIP = 6;
    public static final int POLYGON = 7;
    public static final int OPEN = 8;
    public static final int CLOSE = 9;

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
    
    // Matrix transformation stack
    private final Stack<AffineTransform> matrixStack = new Stack<>();
    private AffineTransform currentTransform = new AffineTransform();

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
    protected volatile int mouseButton = LEFT;
    protected volatile int mouseWheelCounter;
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

    // Entry point called from generated main method. Blocks until this
    // (primary) window closes.
    public final void start() {
        launchWindow(true);
        try { window.awaitShutdown();
        } catch (InterruptedException e) { System.out.println("Main thread interrupted: " + e.getMessage()); }
    }

    /**
     * Opens this script's own window on a separate render loop and returns
     * immediately. Used to create secondary (debug) windows from another
     * {@code .kvs} file: {@code Debug debug = new Debug(); debug.launchWindow();}
     *
     * <p>Closing a secondary window stops only that script's render loop; it
     * does not exit the whole program. Use {@link #start()} for the primary
     * window, which exits on close.
     */
    public void launchWindow() {
        launchWindow(false);
    }

    private void launchWindow(boolean exitOnClose) {
        setGlobal("script", this);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        displayWidth = screen.width;
        displayHeight = screen.height;
        settings();

        window = new KanvasWindow(this, exitOnClose);
        setGlobal("window", window);

        graphics = new KanvasGraphics(this);
        setGlobal("graphics", graphics);

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

    // ============ Shape Building (beginShape/endShape/vertex) ============
    
    /**
     * Starts building a custom shape. Must be paired with {@link #endShape()}.
     */
    protected void beginShape() {
        if (graphics != null) graphics.beginShape();
    }
    
    /**
     * Starts building a custom shape with the specified mode.
     * @param mode the shape mode (POINTS, LINES, TRIANGLES, POLYGON, OPEN, CLOSE)
     */
    protected void beginShape(int mode) {
        if (graphics != null) graphics.beginShape(mode);
    }
    
    /**
     * Adds a vertex to the current shape.
     * @param x the x coordinate
     * @param y the y coordinate
     */
    protected void vertex(float x, float y) {
        if (graphics != null) graphics.vertex(x, y);
    }
    
    /**
     * Ends the current shape and renders it with optional closure.
     * @param closeMode CLOSE to connect last vertex to first, OPEN otherwise
     */
    protected void endShape(int closeMode) {
        if (graphics != null) graphics.endShape(closeMode);
    }
    
    /**
     * Ends the current shape without closure.
     */
    protected void endShape() {
        if (graphics != null) graphics.endShape();
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

    public void imageMode(DrawMode mode) { this.imageMode = mode; }
    public void rectMode(DrawMode mode) { this.rectMode = mode; }
    public void ellipseMode(DrawMode mode) { this.ellipseMode = mode; }
    public void shapeMode(DrawMode mode) { this.shapeMode = mode; }

    // ============ Images & Asset Paths ============

    /**
     * Loads an image from the sketch's asset root (or an absolute path).
     * Relative paths are resolved the same way {@link #sketchPath(String)}
     * resolves them, so {@code loadImage("head.png")} finds
     * {@code <project>/head.png} and {@code loadImage("assets/head.png")}
     * finds {@code <project>/assets/head.png}.
     *
     * @return the loaded image, or {@code null} (with an error printed) if the
     *         file cannot be found or decoded
     */
    public KImage loadImage(String filename) {
        return loadImage(new File(sketchPath(filename)));
    }

    /**
     * Loads an image from an explicit file path.
     */
    public KImage loadImage(Path path) {
        return loadImage(path.toFile());
    }

    /**
     * Loads an image from an explicit file.
     */
    public KImage loadImage(File file) {
        try {
            return new KImage(file, window);
        } catch (KanvasException e) {
            System.err.println("loadImage failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Draws an image at its native size at (x, y), respecting the current
     * image mode. Public so helper classes can call it, like Processing does
     * via {@code parent.image(...)}.
     */
    public void image(KImage img, float x, float y) {
        if (img == null) return;
        image(img, x, y, img.getWidth(), img.getHeight());
    }

    /**
     * Draws an image scaled to the given width/height, respecting the current
     * image mode and the current matrix transform (translate/rotate/scale).
     */
    public void image(KImage img, float x, float y, float w, float h) {
        if (img == null || graphics == null) return;
        float[] r = resolveToCorner(x, y, w, h, imageMode);
        graphics.image(img, r[0], r[1], r[2], r[3]);
    }

    /** Convenience overload matching the float signature with doubles. */
    public void image(KImage img, double x, double y) {
        if (img == null) return;
        image(img, (float)x, (float)y, img.getWidth(), img.getHeight());
    }

    /** Convenience overload matching the float signature with doubles. */
    public void image(KImage img, double x, double y, double w, double h) {
        image(img, (float)x, (float)y, (float)w, (float)h);
    }

    /**
     * Absolute-path getter for the sketch (project) root directory. When the
     * sketch is launched through {@code kanvas run}, this is the directory
     * containing {@code kanvas.toml} (the value is passed to the JVM via the
     * {@code kanvas.sketchPath} system property). Falls back to the process
     * working directory.
     */
    public String sketchPath() {
        String prop = System.getProperty("kanvas.sketchPath");
        if (prop != null && !prop.isBlank()) return Paths.get(prop).toAbsolutePath().normalize().toString();
        return Paths.get("").toAbsolutePath().normalize().toString();
    }

    /**
     * Resolves a relative path against the sketch root. Absolute paths are
     * returned unchanged. Because {@code sketchPath} is a prefix of the
     * returned path, paths can be nested arbitrarily deep
     * ({@code sketchPath("assets/textures/head.png")}).
     */
    public String sketchPath(String where) {
        if (where == null || where.isBlank()) return sketchPath();
        Path p = Paths.get(where);
        if (p.isAbsolute()) return p.normalize().toString();
        return Paths.get(sketchPath()).resolve(where).normalize().toString();
    }

    /**
     * Resolves a path relative to the sketch's data directory. Falls back to
     * the sketch root so projects with a {@code data/} folder or plain assets
     * both work.
     */
    public String dataPath(String where) {
        if (where == null || where.isBlank()) return sketchPath();
        Path dataDir = Paths.get(sketchPath()).resolve("data");
        if (dataDir.toFile().isDirectory()) return dataDir.resolve(where).normalize().toString();
        return sketchPath(where);
    }

    // ============ Matrix Transformations ============
    
    /**
     * Saves the current transformation matrix on the stack.
     * Used with {@link #popMatrix()} to create nested transformations.
     */
    protected void pushMatrix() {
        matrixStack.push(new AffineTransform(currentTransform));
        if (graphics != null) graphics.pushMatrix(new AffineTransform(currentTransform));
    }
    
    /**
     * Restores the previous transformation matrix from the stack.
     * Must be paired with {@link #pushMatrix()}.
     */
    protected void popMatrix() {
        if (matrixStack.isEmpty()) {
            System.err.println("Warning: popMatrix() called without matching pushMatrix()");
            return;
        }
        currentTransform = matrixStack.pop();
        if (graphics != null) graphics.popMatrix();
    }
    
    /**
     * Translates (moves) the coordinate system by the specified x and y offset.
     * @param x the horizontal offset
     * @param y the vertical offset
     */
    protected void translate(float x, float y) {
        currentTransform.translate(x, y);
        if (graphics != null) graphics.setTransform(currentTransform);
    }
    
    /**
     * Rotates the coordinate system by the specified angle in radians.
     * @param angle the rotation angle in radians (counterclockwise)
     */
    protected void rotate(float angle) {
        currentTransform.rotate(angle);
        if (graphics != null) graphics.setTransform(currentTransform);
    }
    
    /**
     * Scales the coordinate system by the specified factors.
     * @param sx the horizontal scale factor
     * @param sy the vertical scale factor
     */
    protected void scale(float sx, float sy) {
        currentTransform.scale(sx, sy);
        if (graphics != null) graphics.setTransform(currentTransform);
    }
    
    /**
     * Scales the coordinate system uniformly by the specified factor.
     * @param s the scale factor
     */
    protected void scale(float s) {
        scale(s, s);
    }
    
    /**
     * Resets the transformation matrix to the identity (no transformation).
     */
    protected void resetMatrix() {
        currentTransform = new AffineTransform();
        if (graphics != null) graphics.setTransform(currentTransform);
    }
    
    /**
     * Applies a shear transformation along the x-axis.
     * @param angle the shear angle in radians
     */
    protected void shearX(float angle) {
        currentTransform.shear(Math.tan(angle), 0);
        if (graphics != null) graphics.setTransform(currentTransform);
    }
    
    /**
     * Applies a shear transformation along the y-axis.
     * @param angle the shear angle in radians
     */
    protected void shearY(float angle) {
        currentTransform.shear(0, Math.tan(angle));
        if (graphics != null) graphics.setTransform(currentTransform);
    }
    
    /**
     * Gets the current transformation matrix.
     * @return a copy of the current AffineTransform
     */
    protected AffineTransform getMatrix() {
        return new AffineTransform(currentTransform);
    }
}
