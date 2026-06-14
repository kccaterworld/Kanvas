package kanvas.tui;

import kanvas.runtime.KanvasStdlib;

public abstract class KanvasTUIScript extends KanvasStdlib {
    public volatile int cols;
    public volatile int rows;
    public volatile KeyEvent lastKey;
    protected volatile float frameRate = 30;
    protected volatile int frameCount;
    volatile boolean loop = true;

    protected final float PI = (float)Math.PI;
    protected final float HALF_PI = (float)Math.PI / 2;
    protected final float QUARTER_PI = (float)Math.PI / 4;
    protected final float TWO_PI = (float)(2 * Math.PI);

    int currentFg = 7;
    int currentBg = 0;

    KanvasTUITerminal terminal;

    public final void start() {
        settings();
        terminal = new KanvasTUITerminal();
        terminal.open();
        cols = terminal.cols();
        rows = terminal.rows();
        setup();
        terminal.startInputLoop(this);
        Thread renderThread = new Thread(this::renderLoop, "kanvas-tui-render");
        renderThread.setDaemon(true);
        renderThread.start();
        try { terminal.awaitShutdown();
        } catch (InterruptedException e) { System.out.println("Main thread interrupted: " + e.getMessage()); }
    }

    private void renderLoop() {
        while (terminal.isRunning()) {
            long frameStart = System.nanoTime();
            draw();
            terminal.render();
            frameCount++;
            long budget = (long)(1_000_000_000.0 / frameRate);
            long elapsed = System.nanoTime() - frameStart;
            long sleep = budget - elapsed;
            if (sleep > 0){
                try { Thread.sleep(sleep / 1_000_000, (int)(sleep % 1_000_000));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (!loop) {
                try { synchronized(this) { wait(); }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    public void settings() { frameRate = 30; }
    public void setup() {}
    public void draw() {}
    public void dispose() {}
    public void keyPressed(KeyEvent event) {}
    public void keyReleased(KeyEvent event) {}
    public void onResize(int cols, int rows) {}
    public void noLoop() { this.loop = false; }
    public void loop() { this.loop = true; synchronized(this) { notifyAll(); } }
    public void frameRate(float fps) { this.frameRate = fps; }
    public void redraw() {
        if (terminal == null) return;
        draw();
        terminal.render();
    }

    protected void setColor(int fg, int bg) { this.currentFg = fg; this.currentBg = bg; }
    protected void setColor(int fg) { this.currentFg = fg; }
    protected void setTitle(String title) {
        System.out.print("\033]0;" + title + "\007");
        System.out.flush();
    }

    protected void text(int col, int row, String str) {
        if (terminal == null) return;
        for (int i = 0; i < str.length(); i++) terminal.putChar(col + i, row, str.charAt(i), currentFg, currentBg);
    }
    protected void text(int col, int row, char ch) {
        if (terminal != null) terminal.putChar(col, row, ch, currentFg, currentBg);
    }
    protected void clear() {
        if (terminal != null) terminal.fill(' ', currentFg, currentBg);
    }
    protected void hline(int col, int row, int len, char ch) {
        if (terminal == null) return;
        for (int i = 0; i < len; i++) terminal.putChar(col + i, row, ch, currentFg, currentBg);
    }
    protected void vline(int col, int row, int len, char ch) {
        if (terminal == null) return;
        for (int i = 0; i < len; i++) terminal.putChar(col, row + i, ch, currentFg, currentBg);
    }
    protected void box(int col, int row, int w, int h) {
        if (terminal == null || w < 2 || h < 2) return;
        terminal.putChar(col, row, '┌', currentFg, currentBg);
        terminal.putChar(col + w - 1, row, '┐', currentFg, currentBg);
        terminal.putChar(col, row + h - 1, '└', currentFg, currentBg);
        terminal.putChar(col + w - 1, row + h - 1, '┘', currentFg, currentBg);
        for (int c = col + 1; c < col + w - 1; c++) {
            terminal.putChar(c, row, '─', currentFg, currentBg);
            terminal.putChar(c, row + h - 1, '─', currentFg, currentBg);
        }
        for (int r = row + 1; r < row + h - 1; r++) {
            terminal.putChar(col, r, '│', currentFg, currentBg);
            terminal.putChar(col + w - 1, r, '│', currentFg, currentBg);
        }
    }
}
