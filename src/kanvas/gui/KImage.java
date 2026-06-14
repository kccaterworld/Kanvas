package kanvas.gui;

import java.nio.file.Path;

enum filterMode { THRESHOLD, GRAY, OPAQUE, INVERT, POSTERIZE, BLUR, ERODE, DILATE }
enum blendMode {BLEND, ADD, SUBTRACT, DARKEST, LIGHTEST, DIFFERENCE, EXCLUSION, MULTIPLY, SCREEN, OVERLAY, HARD_LIGHT, SOFT_LIGHT, DODGE, BURN}

public class KImage {
    Path imagePath;
    int[] pixels;
    float width, height;

    public KImage(Path imagePath) {
        this.imagePath = imagePath;
    }
    public KImage(int[] pixels, float width, float height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    public void updatePixels() { }
    public void resize(float newWidth, float newHeight) {
        this.width = newWidth;
        this.height = newHeight;
    }
    public KImage get() {
        return this;
    }
    public KImage get(int x, int y, int w, int h) {
        return this;
    }
    public int get(int x, int y) {
        return pixels[y * (int)width + x];
    }
    public void set(int x, int y, int color) {
        pixels[y * (int)width + x] = color;
    }
    public void set(int x, int y, KImage img) {

    }
    public void mask(KImage mask) {

    }
    public void mask(int[] mask) {

    }
    public void filter(filterMode mode) { }
    public void filter(filterMode mode, float param) { }
    public KImage copy() { return new KImage(pixels.clone(), width, height); }
    public static void copy(KImage src, int sourceX, int sourceY, int sourceW, int sourceH,
                            int destX, int destY, int destW, int destH) {
        src.copy(sourceX, sourceY, sourceW, sourceH, destX, destY, destW, destH);
    }
    public KImage copy(int sourceX, int sourceY, int sourceW, int sourceH,
                        int destX, int destY, int destW, int destH) {
        return null;
    }
    public void blendColor(int color1, int color2, blendMode mode) {

    }
    public static void blend(KImage src, blendMode mode,
                            int sourceX, int sourceY, int sourceW, int sourceH,
                            int destX, int destY, int destW, int destH) {
        src.blendColor(sourceX, sourceY, mode);
    }
    public void blend(blendMode mode, int sourceX, int sourceY, int sourceW, int sourceH,
                        int destX, int destY, int destW, int destH) {
    
    }
    public void save(String filePath) { save(Path.of(filePath)); }
    public void save(Path filePath) {

    }
}
