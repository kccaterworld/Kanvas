package kanvas.gui;

import kanvas.KanvasException;
import kanvas.runtime.KanvasStdlib;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;

enum filterMode { THRESHOLD, GRAY, OPAQUE, INVERT, POSTERIZE, BLUR, ERODE, DILATE }
enum blendMode { BLEND, ADD, SUBTRACT, DARKEST, LIGHTEST, DIFFERENCE, EXCLUSION, MULTIPLY, SCREEN, OVERLAY, HARD_LIGHT, SOFT_LIGHT, DODGE, BURN }

/**
 * Kanvas image type. Backed by a {@link BufferedImage} plus an ARGB pixel
 * buffer so pixel-level reads/writes and {@code save()}/rendering all work.
 */
public class KImage {
    public final int[] pixels;
    public int width, height;
    public final BufferedImage image;
    public KanvasWindow window;

    /**
     * Loads an image from disk. Relative paths resolve against the sketch
     * asset root (see {@code KanvasScript.sketchPath}).
     */
    public KImage(Path imagePath) throws KanvasException {
        this(imagePath.toFile(), null);
    }

    public KImage(Path imagePath, KanvasWindow window) throws KanvasException {
        this(imagePath.toFile(), window);
    }

    public KImage(File imageFile, KanvasWindow window) throws KanvasException {
        this.window = window;
        if (imageFile == null || !imageFile.isFile())
            throw new KanvasException("Image file not found: " + (imageFile == null ? "null" : imageFile.getPath()));
        try {
            image = ImageIO.read(imageFile);
            if (image == null)
                throw new KanvasException("Unsupported image format: " + imageFile.getPath());
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.pixels = new int[this.width * this.height];
            image.getRGB(0, 0, width, height, pixels, 0, width);
        } catch (KanvasException e) {
            throw e;
        } catch (Exception e) {
            throw new KanvasException("Failed to load image: " + e.getMessage(), e);
        }
    }

    public KImage(int[] pixels, int width, int height) {
        this(pixels, width, height, null);
    }

    public KImage(int[] pixels, int width, int height, KanvasWindow window) {
        if (width < 1) width = 1;
        if (height < 1) height = 1;
        if (pixels == null || pixels.length < width * height)
            pixels = java.util.Arrays.copyOf(pixels == null ? new int[0] : pixels, width * height);
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        this.window = window;
    }

    /**
     * Wraps an existing buffered image. The pixel buffer becomes a snapshot
     * of the image's ARGB data; call {@link #updatePixels()} after mutations.
     */
    public KImage(BufferedImage source) {
        this.width = source.getWidth();
        this.height = source.getHeight();
        this.pixels = new int[this.width * this.height];
        source.getRGB(0, 0, width, height, pixels, 0, width);
        this.image = source;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /**
     * Draws the image at its native size into the current render context.
     */
    public void display(int x, int y) {
        Object g = KanvasStdlib.getGlobal("graphics");
        if (g instanceof KanvasGraphics kg) {
            kg.image(this, x, y, width, height);
            return;
        }
        KanvasWindow w = (window != null) ? window : (KanvasWindow) KanvasStdlib.getGlobal("window");
        if (w != null) KanvasWindow.displayImage(w, this, x, y);
    }

    public void updatePixels() {
        image.setRGB(0, 0, width, height, pixels, 0, width);
    }

    /**
     * Returns a scaled copy of this image. The original image is unchanged.
     */
    public KImage resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return this;
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return new KImage(scaled);
    }

    public KImage get() { return this; }

    /**
     * Returns a copy of the rectangular region as a new KImage, or {@code null}
     * when the region is empty or out of bounds.
     */
    public KImage get(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || x < 0 || y < 0) return null;
        int x2 = Math.min(x + w, width);
        int y2 = Math.min(y + h, height);
        w = x2 - x;
        h = y2 - y;
        if (w <= 0 || h <= 0) return null;
        int[] region = new int[w * h];
        image.getRGB(x, y, w, h, region, 0, w);
        return new KImage(region, w, h);
    }

    /**
     * Reads the color at (x, y). Out-of-bounds returns 0.
     */
    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return pixels[y * width + x];
    }

    public void set(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        pixels[y * width + x] = color;
    }

    public void set(int x, int y, KImage img) {
        for (int i = 0; i < img.width; i++) {
            for (int j = 0; j < img.height; j++) {
                int sx = x + i, sy = y + j;
                if (sx < 0 || sy < 0 || sx >= width || sy >= height) continue;
                pixels[sy * width + sx] = img.pixels[j * img.width + i];
            }
        }
    }
    public void mask(KImage mask) throws KanvasException {
        throw new KanvasException("Masking is not supported yet");
    }
    public void mask(int[] mask) throws KanvasException {
        throw new KanvasException("Masking is not supported yet");
    }
    public void filter(filterMode mode) throws KanvasException {
        throw new KanvasException("Filtering is not supported yet");
    }
    public void filter(filterMode mode, float param) throws KanvasException {
        throw new KanvasException("Filtering is not supported yet");
    }

    public KImage copy() {
        return new KImage(pixels.clone(), width, height, window);
    }

    public static void copy(KImage src, int sourceX, int sourceY, int sourceW, int sourceH,
        int destX, int destY, int destW, int destH) throws KanvasException {
        src.copy(sourceX, sourceY, sourceW, sourceH, destX, destY, destW, destH);
    }

    public KImage copy(int sourceX, int sourceY, int sourceW, int sourceH,
        int destX, int destY, int destW, int destH) throws KanvasException {
        throw new KanvasException("Copying is not supported yet");
    }

    public void blendColor(int color1, int color2, blendMode mode) throws KanvasException {
        throw new KanvasException("Blending is not supported yet");
    }

    public static void blend(KImage src, blendMode mode,
        int sourceX, int sourceY, int sourceW, int sourceH,
        int destX, int destY, int destW, int destH) throws KanvasException {
        throw new KanvasException("Blending is not supported yet");
    }

    public void blend(blendMode mode, int sourceX, int sourceY, int sourceW, int sourceH,
        int destX, int destY, int destW, int destH) throws KanvasException {
        throw new KanvasException("Blending is not supported yet");
    }

    public void save(String filePath) {
        save(Path.of(filePath));
    }

    public void save(Path filePath) {
        BufferedImage out = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, this.width, this.height, pixels, 0, this.width);
        try {
            if (filePath.getParent() != null)
                java.nio.file.Files.createDirectories(filePath.getParent());
            ImageIO.write(out, "png", filePath.toFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
