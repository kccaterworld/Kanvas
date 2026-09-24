package kanvas.gui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Handles all graphics rendering operations for Kanvas sketches.
 * Manages transformations, shapes, and drawing state.
 */
public class KanvasGraphics {
    private final KanvasScript sketch;
    private Graphics2D graphics2D;
    
    // Transform management
    private final Stack<AffineTransform> transformStack = new Stack<>();
    private AffineTransform currentTransform = new AffineTransform();
    
    // Shape building state
    private ShapeMode shapeMode = ShapeMode.OPEN;
    private final List<float[]> shapeVertices = new ArrayList<>();
    private boolean buildingShape = false;
    
    // Shape mode constants for beginShape
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

    public KanvasGraphics(KanvasScript sketch) { 
        this.sketch = sketch;
    }

    public void setContext(Graphics2D graphics2D) {
        this.graphics2D = graphics2D;
        applySmoothing();
        // Apply any pending transform
        if (!currentTransform.isIdentity()) {
            graphics2D.setTransform(currentTransform);
        }
    }

    public void background(int r, int g, int b) {
        AffineTransform saved = graphics2D.getTransform();
        graphics2D.setTransform(new AffineTransform()); // Reset transform for background
        graphics2D.setColor(new Color(r, g, b));
        graphics2D.fillRect(0, 0, (int)sketch.width, (int)sketch.height);
        graphics2D.setTransform(saved);
    }

    // ============ Transform Management ============
    
    /**
     * Saves the current transform on the stack (called from KanvasScript).
     */
    public void pushMatrix(AffineTransform transform) {
        transformStack.push(new AffineTransform(currentTransform));
    }
    
    /**
     * Restores the previous transform from the stack (called from KanvasScript).
     */
    public void popMatrix() {
        if (!transformStack.isEmpty()) {
            currentTransform = transformStack.pop();
            if (graphics2D != null) {
                graphics2D.setTransform(currentTransform);
            }
        }
    }
    
    /**
     * Sets the current transformation matrix.
     */
    public void setTransform(AffineTransform transform) {
        this.currentTransform = new AffineTransform(transform);
        if (graphics2D != null) {
            graphics2D.setTransform(this.currentTransform);
        }
    }

    // ============ Drawing with Transforms ============
    
    public void ellipse(float x, float y, float w, float h) {
        int ix = (int)x, iy = (int)y, iw = (int)w, ih = (int)h;
        if (sketch.fill) { 
            graphics2D.setColor(fillColor()); 
            graphics2D.fillOval(ix, iy, iw, ih); 
        }
        if (sketch.stroke) { 
            graphics2D.setColor(strokeColor()); 
            applyStroke(); 
            graphics2D.drawOval(ix, iy, iw, ih); 
        }
    }
    
    public void rect(float x, float y, float w, float h) {
        int ix = (int)x, iy = (int)y, iw = (int)w, ih = (int)h;
        if (sketch.fill) { 
            graphics2D.setColor(fillColor()); 
            graphics2D.fillRect(ix, iy, iw, ih); 
        }
        if (sketch.stroke) { 
            graphics2D.setColor(strokeColor()); 
            applyStroke(); 
            graphics2D.drawRect(ix, iy, iw, ih); 
        }
    }
    
    public void line(float x1, float y1, float x2, float y2) {
        if (!sketch.stroke) return;
        graphics2D.setColor(strokeColor());
        applyStroke();
        graphics2D.drawLine((int)x1, (int)y1, (int)x2, (int)y2);
    }
    
    public void point(float x, float y) {
        if (!sketch.stroke) return;
        graphics2D.setColor(strokeColor());
        int d = Math.max(1, Math.round(sketch.strokeWeight));
        graphics2D.fillOval((int)(x - d / 2f), (int)(y - d / 2f), d, d);
    }
    
    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        int[] xs = {(int)x1, (int)x2, (int)x3};
        int[] ys = {(int)y1, (int)y2, (int)y3};
        if (sketch.fill) { 
            graphics2D.setColor(fillColor()); 
            graphics2D.fillPolygon(xs, ys, 3); 
        }
        if (sketch.stroke) { 
            graphics2D.setColor(strokeColor()); 
            applyStroke(); 
            graphics2D.drawPolygon(xs, ys, 3); 
        }
    }

    public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        int[] xs = {(int)x1, (int)x2, (int)x3, (int)x4};
        int[] ys = {(int)y1, (int)y2, (int)y3, (int)y4};
        if (sketch.fill) { 
            graphics2D.setColor(fillColor()); 
            graphics2D.fillPolygon(xs, ys, 4); 
        }
        if (sketch.stroke) { 
            graphics2D.setColor(strokeColor()); 
            applyStroke(); 
            graphics2D.drawPolygon(xs, ys, 4); 
        }
    }
    
    public void arc(float x, float y, float w, float h, float start, float stop) {
        int ix = (int)x, iy = (int)y, iw = (int)w, ih = (int)h;
        int startDeg = (int)Math.toDegrees(-stop);
        int extentDeg = (int)Math.toDegrees(stop - start);
        if (sketch.fill) { 
            graphics2D.setColor(fillColor()); 
            graphics2D.fillArc(ix, iy, iw, ih, startDeg, extentDeg); 
        }
        if (sketch.stroke) { 
            graphics2D.setColor(strokeColor()); 
            applyStroke(); 
            graphics2D.drawArc(ix, iy, iw, ih, startDeg, extentDeg); 
        }
    }

    /**
     * Draws an image into the specified rectangle using the current transform
     * and smoothing state. The image is unaffected by fill/stroke settings.
     * Coordinates must already be corner-mode (resolved by KanvasScript).
     */
    public void image(KImage img, float x, float y, float w, float h) {
        if (img == null || img.image == null || graphics2D == null) return;
        if (w <= 0 || h <= 0) return;
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            (sketch.smoothing) ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                               : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics2D.drawImage(img.image, (int)x, (int)y, (int)w, (int)h, null);
    }
    
    public void smooth() {
        if (graphics2D != null) 
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    
    public void noSmooth() {
        if (graphics2D != null) 
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    // ============ Shape Building (beginShape/endShape/vertex) ============
    
    /**
     * Starts building a custom shape. Must be paired with endShape().
     */
    public void beginShape() {
        beginShape(POLYGON);
    }
    
    /**
     * Starts building a custom shape with the specified mode.
     * @param mode the shape mode (POINTS, LINES, TRIANGLES, POLYGON, etc.)
     */
    public void beginShape(int mode) {
        shapeVertices.clear();
        buildingShape = true;
        this.shapeMode = ShapeMode.fromInt(mode);
    }
    
    /**
     * Ends the current shape and renders it.
     */
    public void endShape() {
        endShape(8);  // OPEN mode
    }
    
    /**
     * Ends the current shape and renders it with optional closure.
     * @param closeMode CLOSE to connect last vertex to first, OPEN otherwise
     */
    public void endShape(int closeMode) {
        if (!buildingShape || shapeVertices.isEmpty()) return;
        
        boolean shouldClose = (closeMode == 9); // CLOSE == 9
        renderShape(shouldClose);
        
        buildingShape = false;
        shapeVertices.clear();
    }
    
    /**
     * Adds a vertex to the current shape.
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void vertex(float x, float y) {
        if (!buildingShape) {
            System.err.println("Warning: vertex() called outside beginShape/endShape block");
            return;
        }
        shapeVertices.add(new float[]{x, y});
    }
    
    private void renderShape(boolean close) {
        if (shapeVertices.size() < 2) return;
        
        switch (shapeMode) {
            case POINTS:
                renderPoints();
                break;
            case LINES:
                renderLines();
                break;
            case TRIANGLES:
                renderTriangles();
                break;
            case POLYGON:
            case OPEN:
                renderPolygon(close);
                break;
            default:
                renderPolygon(close);
        }
    }
    
    private void renderPoints() {
        if (!sketch.stroke) return;
        graphics2D.setColor(strokeColor());
        for (float[] v : shapeVertices) {
            int d = Math.max(1, Math.round(sketch.strokeWeight));
            graphics2D.fillOval((int)(v[0] - d/2f), (int)(v[1] - d/2f), d, d);
        }
    }
    
    private void renderLines() {
        if (!sketch.stroke) return;
        graphics2D.setColor(strokeColor());
        applyStroke();
        for (int i = 0; i < shapeVertices.size() - 1; i += 2) {
            float[] v1 = shapeVertices.get(i);
            float[] v2 = shapeVertices.get(i + 1);
            graphics2D.drawLine((int)v1[0], (int)v1[1], (int)v2[0], (int)v2[1]);
        }
    }
    
    private void renderTriangles() {
        for (int i = 0; i < shapeVertices.size() - 2; i += 3) {
            float[] v1 = shapeVertices.get(i);
            float[] v2 = shapeVertices.get(i + 1);
            float[] v3 = shapeVertices.get(i + 2);
            int[] xs = {(int)v1[0], (int)v2[0], (int)v3[0]};
            int[] ys = {(int)v1[1], (int)v2[1], (int)v3[1]};
            if (sketch.fill) { 
                graphics2D.setColor(fillColor()); 
                graphics2D.fillPolygon(xs, ys, 3); 
            }
            if (sketch.stroke) { 
                graphics2D.setColor(strokeColor()); 
                applyStroke(); 
                graphics2D.drawPolygon(xs, ys, 3); 
            }
        }
    }
    
    private void renderPolygon(boolean close) {
        int[] xs = new int[shapeVertices.size()];
        int[] ys = new int[shapeVertices.size()];
        for (int i = 0; i < shapeVertices.size(); i++) {
            float[] v = shapeVertices.get(i);
            xs[i] = (int)v[0];
            ys[i] = (int)v[1];
        }
        
        if (sketch.fill) { 
            graphics2D.setColor(fillColor()); 
            graphics2D.fillPolygon(xs, ys, shapeVertices.size()); 
        }
        if (sketch.stroke) { 
            graphics2D.setColor(strokeColor()); 
            applyStroke();
            if (close) {
                graphics2D.drawPolygon(xs, ys, shapeVertices.size());
            } else {
                graphics2D.drawPolyline(xs, ys, shapeVertices.size());
            }
        }
    }

    // ============ Utilities ============
    
    private void applySmoothing() {
        if (graphics2D == null) return;
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            (sketch.smoothing) ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void applyStroke() { 
        graphics2D.setStroke(new BasicStroke(sketch.strokeWeight, 
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); 
    }
    
    private Color fillColor() { 
        return new Color(sketch.fillColor, true); 
    }

    private Color strokeColor() { 
        return new Color(sketch.strokeColor, true); 
    }
    
    // ============ Shape Mode Enum ============
    
    private enum ShapeMode {
        POINTS(0), LINES(1), TRIANGLES(2), TRIANGLE_STRIP(3), TRIANGLE_FAN(4),
        QUADS(5), QUAD_STRIP(6), POLYGON(7), OPEN(8), CLOSE(9);
        
        private final int value;
        
        ShapeMode(int value) {
            this.value = value;
        }
        
        static ShapeMode fromInt(int v) {
            for (ShapeMode m : values()) {
                if (m.value == v) return m;
            }
            return POLYGON;
        }
    }
}
