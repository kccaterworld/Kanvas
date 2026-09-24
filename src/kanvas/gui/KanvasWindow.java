package kanvas.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.concurrent.CountDownLatch;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

class KanvasWindow {
    private JFrame frame;
    private Canvas canvas;
    private BufferStrategy strategy;
    private final KanvasScript sketch;
    private final boolean exitOnClose;
    private volatile boolean running = true;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public KanvasWindow(KanvasScript sketch) {
        this(sketch, true);
    }

    /**
     * @param exitOnClose {@code true} for the primary window (closing it calls
     *                    {@code System.exit}); {@code false} for secondary
     *                    windows whose close should only stop that script.
     */
    public KanvasWindow(KanvasScript sketch, boolean exitOnClose) {
        this.sketch = sketch;
        this.exitOnClose = exitOnClose;
    }

    public void open() {
        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension((int)sketch.width, (int)sketch.height));
        canvas.setIgnoreRepaint(true);

        frame = new JFrame();
        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        frame.setResizable(sketch.resizable);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        if (sketch.fullscreen) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
        }

        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                running = false;
                sketch.dispose();
                shutdownLatch.countDown();
                if (exitOnClose) System.exit(0);
            }
            @Override
            public void windowGainedFocus(WindowEvent e) {
                sketch.focused = true;
            }
            @Override
            public void windowLostFocus(WindowEvent e) {
                sketch.focused = false;
            }
        });

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                sketch.windowMoved();
            }
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                sketch.pmouseX = sketch.mouseX;
                sketch.pmouseY = sketch.mouseY;
                sketch.mouseX = e.getX();
                sketch.mouseY = e.getY();
                sketch.mouseButton = buttonOf(e);
                sketch.mousePressed = true;
                sketch.mousePressed();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                sketch.mouseButton = buttonOf(e);
                sketch.mousePressed = false;
                sketch.mouseReleased();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                sketch.mouseClicked();
            }
        });

        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                sketch.mouseMoved(e.getX(), e.getY());
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                sketch.pmouseX = sketch.mouseX;
                sketch.pmouseY = sketch.mouseY;
                sketch.mouseX = e.getX();
                sketch.mouseY = e.getY();
                sketch.mouseDragged();
            }
        });

        canvas.addMouseWheelListener((MouseWheelEvent e) -> {
            sketch.mouseWheelCounter += e.getWheelRotation();
            sketch.mouseWheel();
        });

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sketch.key = e.getKeyChar();
                sketch.keyCode = e.getKeyCode();
                sketch.keyPressed();
            }
            @Override
            public void keyReleased(KeyEvent e) {
                sketch.key = e.getKeyChar();
                sketch.keyCode = e.getKeyCode();
                sketch.keyReleased();
            }
            @Override
            public void keyTyped(KeyEvent e) {
                sketch.key = e.getKeyChar();
                sketch.keyTyped();
            }
        });
        frame.setVisible(true);
        canvas.requestFocusInWindow();
        canvas.createBufferStrategy(2);
        strategy = canvas.getBufferStrategy();
    }

    Graphics2D acquireGraphics() { return (Graphics2D) strategy.getDrawGraphics(); }
    void show() {
        strategy.show();
        Toolkit.getDefaultToolkit().sync();
    }
    boolean isRunning() { return running; }
    void awaitShutdown() throws InterruptedException { shutdownLatch.await(); }

    void resize(int w, int h) {
        if (canvas == null) return;
        try {
            SwingUtilities.invokeAndWait(() -> {
                canvas.setPreferredSize(new Dimension(w, h));
                frame.pack();
                canvas.createBufferStrategy(2);
                strategy = canvas.getBufferStrategy();
            });
        } catch (Exception e) { throw new RuntimeException("Failed to resize window", e);
        }
    }

    void setTitle(String title) { if (frame != null) frame.setTitle(title); }
    void setResizable(boolean resizable) { if (frame != null) frame.setResizable(resizable); }

    void setLocation(int x, int y) {
        if (frame == null) return;
        SwingUtilities.invokeLater(() -> frame.setLocation(x, y));
    }

    void moveBy(int dx, int dy) {
        if (frame == null) return;
        SwingUtilities.invokeLater(() -> {
            Point p = frame.getLocationOnScreen();
            frame.setLocation(p.x + dx, p.y + dy);
        });
    }


    public static void displayImage(KanvasWindow window, KImage image, int x, int y) {
        if (window == null || image == null || image.image == null) return;
        Graphics2D g = window.acquireGraphics();
        boolean smooth = (image.window != null && image.window.sketch != null) ? image.window.sketch.smoothing : true;
        g.drawImage(image.image.getScaledInstance(image.width, image.height, (smooth ? Image.SCALE_SMOOTH : Image.SCALE_FAST)), x, y, null);
        window.show();
    }

    // Maps a java.awt MouseEvent button to Kanvas's Processing-compatible
    // mouse button codes (LEFT=37, RIGHT=39, CENTER_BUTTON=3).
    private static int buttonOf(MouseEvent e) {
        switch (e.getButton()) {
            case MouseEvent.BUTTON1: return KanvasScript.LEFT;
            case MouseEvent.BUTTON3: return KanvasScript.RIGHT;
            case MouseEvent.BUTTON2: return KanvasScript.CENTER_BUTTON;
            default: return KanvasScript.LEFT;
        }
    }
}
