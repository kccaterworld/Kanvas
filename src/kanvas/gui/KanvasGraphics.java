package kanvas.gui;

import java.awt.*;

public class KanvasGraphics {
    private final KanvasScript sketch;
    private Graphics2D graphics2D;

    public KanvasGraphics(KanvasScript sketch) { this.sketch = sketch; }

    public void setContext(Graphics2D graphics2D) {
        this.graphics2D = graphics2D;
        applySmoothing();
    }

    public void background(int r, int g, int b) {
        graphics2D.setColor(new Color(r, g, b));
        graphics2D.fillRect(0, 0, (int)sketch.width, (int)sketch.height);
    }

    public void ellipse(float x, float y, float w, float h) {
        int ix = (int)x, iy = (int)y, iw = (int)w, ih = (int)h;
        if (sketch.fill) { graphics2D.setColor(fillColor()); graphics2D.fillOval(ix, iy, iw, ih); }
        if (sketch.stroke) { graphics2D.setColor(strokeColor()); applyStroke(); graphics2D.drawOval(ix, iy, iw, ih); }
    }
    public void rect(float x, float y, float w, float h) {
        int ix = (int)x, iy = (int)y, iw = (int)w, ih = (int)h;
        if (sketch.fill) { graphics2D.setColor(fillColor()); graphics2D.fillRect(ix, iy, iw, ih); }
        if (sketch.stroke) { graphics2D.setColor(strokeColor()); applyStroke(); graphics2D.drawRect(ix, iy, iw, ih); }
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
        if (sketch.fill) { graphics2D.setColor(fillColor()); graphics2D.fillPolygon(xs, ys, 3); }
        if (sketch.stroke) { graphics2D.setColor(strokeColor()); applyStroke(); graphics2D.drawPolygon(xs, ys, 3); }
    }

    public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        int[] xs = {(int)x1, (int)x2, (int)x3, (int)x4};
        int[] ys = {(int)y1, (int)y2, (int)y3, (int)y4};
        if (sketch.fill) { graphics2D.setColor(fillColor()); graphics2D.fillPolygon(xs, ys, 4); }
        if (sketch.stroke) { graphics2D.setColor(strokeColor()); applyStroke(); graphics2D.drawPolygon(xs, ys, 4); }
    }
    public void arc(float x, float y, float w, float h, float start, float stop) {
        int ix = (int)x, iy = (int)y, iw = (int)w, ih = (int)h;
        int startDeg = (int)Math.toDegrees(-stop);
        int extentDeg = (int)Math.toDegrees(stop - start);
        if (sketch.fill) { graphics2D.setColor(fillColor()); graphics2D.fillArc(ix, iy, iw, ih, startDeg, extentDeg); }
        if (sketch.stroke) { graphics2D.setColor(strokeColor()); applyStroke(); graphics2D.drawArc(ix, iy, iw, ih, startDeg, extentDeg); }
    }
    public void smooth() {
        if (graphics2D != null) graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    public void noSmooth() {
        if (graphics2D != null) graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void applySmoothing() {
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            (sketch.smoothing) ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void applyStroke() { graphics2D.setStroke(new BasicStroke(sketch.strokeWeight)); }
    private Color fillColor() { return new Color(sketch.fillColor, true); }

    private Color strokeColor() { return new Color(sketch.strokeColor, true); }
}
