package kanvas.libs.graphics;

import kanvas.runtime.KanvasScript;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class KanvasGraphics {
    private final KanvasScript sketch;
    private Graphics2D g2d;

    public KanvasGraphics(KanvasScript sketch) {
        this.sketch = sketch;
    }

    public void setContext(Graphics2D g2d) {
        this.g2d = g2d;
        applySmoothing();
    }

    public void background(int r, int g, int b) {
        g2d.setColor(new Color(r, g, b));
        g2d.fillRect(0, 0, (int)sketch.width, (int)sketch.height);
    }

    public void ellipse(float x, float y, float w, float h) {
        int ix = (int)(x - w / 2), iy = (int)(y - h / 2), iw = (int)w, ih = (int)h;
        if (sketch.fill) { g2d.setColor(fillColor()); g2d.fillOval(ix, iy, iw, ih); }
        if (sketch.stroke) { g2d.setColor(strokeColor()); applyStroke(); g2d.drawOval(ix, iy, iw, ih); }
    }

    public void circle(float x, float y, float d) {
        ellipse(x, y, d, d);
    }

    public void rect(float x, float y, float w, float h) {
        int ix = (int)x, iy = (int)y, iw = (int)w, ih = (int)h;
        if (sketch.fill) { g2d.setColor(fillColor()); g2d.fillRect(ix, iy, iw, ih); }
        if (sketch.stroke) { g2d.setColor(strokeColor()); applyStroke(); g2d.drawRect(ix, iy, iw, ih); }
    }

    public void square(float x, float y, float s) {
        rect(x, y, s, s);
    }

    public void line(float x1, float y1, float x2, float y2) {
        if (!sketch.stroke) return;
        g2d.setColor(strokeColor());
        applyStroke();
        g2d.drawLine((int)x1, (int)y1, (int)x2, (int)y2);
    }

    public void point(float x, float y) {
        if (!sketch.stroke) return;
        g2d.setColor(strokeColor());
        g2d.fillRect((int)x, (int)y, 1, 1);
    }

    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        int[] xs = {(int)x1, (int)x2, (int)x3};
        int[] ys = {(int)y1, (int)y2, (int)y3};
        if (sketch.fill) { g2d.setColor(fillColor()); g2d.fillPolygon(xs, ys, 3); }
        if (sketch.stroke) { g2d.setColor(strokeColor()); applyStroke(); g2d.drawPolygon(xs, ys, 3); }
    }

    public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        int[] xs = {(int)x1, (int)x2, (int)x3, (int)x4};
        int[] ys = {(int)y1, (int)y2, (int)y3, (int)y4};
        if (sketch.fill) { g2d.setColor(fillColor()); g2d.fillPolygon(xs, ys, 4); }
        if (sketch.stroke) { g2d.setColor(strokeColor()); applyStroke(); g2d.drawPolygon(xs, ys, 4); }
    }

    public void arc(float x, float y, float w, float h, float start, float stop) {
        int ix = (int)(x - w / 2), iy = (int)(y - h / 2), iw = (int)w, ih = (int)h;
        // Java2D arc uses degrees CCW from 3 o'clock; Processing uses radians CW from 3 o'clock
        int startDeg = (int)Math.toDegrees(-stop);
        int extentDeg = (int)Math.toDegrees(stop - start);
        if (sketch.fill) { g2d.setColor(fillColor()); g2d.fillArc(ix, iy, iw, ih, startDeg, extentDeg); }
        if (sketch.stroke) { g2d.setColor(strokeColor()); applyStroke(); g2d.drawArc(ix, iy, iw, ih, startDeg, extentDeg); }
    }

    public void smooth() {
        if (g2d != null) g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    public void noSmooth() {
        if (g2d != null) g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void applySmoothing() {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            sketch.smoothing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void applyStroke() {
        g2d.setStroke(new BasicStroke(1f));
    }

    private Color fillColor() {
        return new Color(sketch.fillColor, true);
    }

    private Color strokeColor() {
        return new Color(sketch.strokeColor, true);
    }
}
