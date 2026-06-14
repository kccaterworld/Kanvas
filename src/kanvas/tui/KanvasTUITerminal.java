package kanvas.tui;

import kanvas.libs.math.KVector2;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

class KanvasTUITerminal {
    private int cols, rows;
    private Cell[] buffer;
    private Cell[] prev;
    private volatile boolean running = true;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final Input input = new Input();

    static class Cell {
        char ch;
        int fg, bg;
        Cell(char ch, int fg, int bg) { this.ch = ch; this.fg = fg; this.bg = bg; }
        Cell() { this(' ', 7, 0); }
    }

    void open() {
        Terminal.enable();
        Terminal.enterAltScreen();
        Terminal.hideCursor();
        KVector2 size = Terminal.getSize();
        cols = (int) size.x;
        rows = (int) size.y;
        buffer = new Cell[cols * rows];
        prev   = new Cell[cols * rows];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = new Cell();
            prev[i]   = new Cell('\0', -1, -1); // force full first render
        }
    }

    void startInputLoop(KanvasTUIScript script) {
        Thread t = new Thread(() -> {
            try {
                while (running) {
                    KeyEvent event = input.read();
                    if (event == null) continue;
                    if (event instanceof KeyEvent.Ctrl c && c.ch() == 'c') {
                        shutdown(script); return;
                    }
                    script.lastKey = event;
                    script.keyPressed(event);
                }
            } catch (IOException e) { shutdown(script);
            }
        }, "kanvas-tui-input");
        t.setDaemon(true);
        t.start();
    }

    void render() {
        StringBuilder sb = new StringBuilder();
        int lastFg = -1, lastBg = -1;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int i = row * cols + col;
                Cell c = buffer[i], p = prev[i];
                if (c.ch == p.ch && c.fg == p.fg && c.bg == p.bg) continue;
                sb.append("\033[").append(row + 1).append(";").append(col + 1).append("H");
                if (c.fg != lastFg) { sb.append("\033[38;5;").append(c.fg).append("m"); lastFg = c.fg; }
                if (c.bg != lastBg) { sb.append("\033[48;5;").append(c.bg).append("m"); lastBg = c.bg; }
                sb.append(c.ch);
                p.ch = c.ch; p.fg = c.fg; p.bg = c.bg;
            }
        }
        if (sb.length() > 0) {
            sb.append("\033[0m");
            System.out.print(sb);
            System.out.flush();
        }
    }

    void putChar(int col, int row, char ch, int fg, int bg) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) return;
        Cell cell = buffer[row * cols + col];
        cell.ch = ch; cell.fg = fg; cell.bg = bg;
    }

    void fill(char ch, int fg, int bg) {
        for (Cell cell : buffer) { cell.ch = ch; cell.fg = fg; cell.bg = bg; }
    }

    void shutdown(KanvasTUIScript script) {
        if (!running) return;
        running = false;
        script.dispose();
        Terminal.exitAltScreen();
        Terminal.showCursor();
        Terminal.disable();
        shutdownLatch.countDown();
    }

    boolean isRunning() { return running; }
    void awaitShutdown() throws InterruptedException { shutdownLatch.await(); }
    int cols() { return cols; }
    int rows() { return rows; }
}
