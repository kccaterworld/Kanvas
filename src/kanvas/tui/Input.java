package kanvas.tui;

import java.io.IOException;
import java.io.InputStream;

public class Input {

    private final InputStream in;

    public Input() {
        this.in = System.in;
    }

    public KeyEvent read() throws IOException {
        int b = in.read();
        if (b == -1) return null;
        if (b == 27) {
            if (in.available() == 0) return new KeyEvent.Special(KeyEvent.Key.ESCAPE);
            int b2 = in.read();
            if (b2 == '[') return parseCSI();
            if (b2 == 'O') return parseSS3();
            return new KeyEvent.Special(KeyEvent.Key.ESCAPE);
        }
        if (b == 9) return new KeyEvent.Special(KeyEvent.Key.TAB);
        if (b == 13 || b == 10) return new KeyEvent.Special(KeyEvent.Key.ENTER);
        if (b == 127 || b == 8) return new KeyEvent.Special(KeyEvent.Key.BACKSPACE);
        if (b >= 1 && b <= 26) return new KeyEvent.Ctrl((char) ('a' + b - 1));
        if (b >= 32 && b < 127) return new KeyEvent.Char((char) b);
        return null;
    }

    private KeyEvent parseCSI() throws IOException {
        StringBuilder seq = new StringBuilder();
        while (in.available() > 0) {
            int b = in.read();
            seq.append((char) b);
            if (b >= 0x40 && b <= 0x7E) break;
        }
        return switch (seq.toString()) {
            case "A"   -> new KeyEvent.Special(KeyEvent.Key.UP);
            case "B"   -> new KeyEvent.Special(KeyEvent.Key.DOWN);
            case "C"   -> new KeyEvent.Special(KeyEvent.Key.RIGHT);
            case "D"   -> new KeyEvent.Special(KeyEvent.Key.LEFT);
            case "H"   -> new KeyEvent.Special(KeyEvent.Key.HOME);
            case "F"   -> new KeyEvent.Special(KeyEvent.Key.END);
            case "2~"  -> new KeyEvent.Special(KeyEvent.Key.INSERT);
            case "3~"  -> new KeyEvent.Special(KeyEvent.Key.DELETE);
            case "5~"  -> new KeyEvent.Special(KeyEvent.Key.PAGE_UP);
            case "6~"  -> new KeyEvent.Special(KeyEvent.Key.PAGE_DOWN);
            case "15~" -> new KeyEvent.Special(KeyEvent.Key.F5);
            case "17~" -> new KeyEvent.Special(KeyEvent.Key.F6);
            case "18~" -> new KeyEvent.Special(KeyEvent.Key.F7);
            case "19~" -> new KeyEvent.Special(KeyEvent.Key.F8);
            case "20~" -> new KeyEvent.Special(KeyEvent.Key.F9);
            case "21~" -> new KeyEvent.Special(KeyEvent.Key.F10);
            case "23~" -> new KeyEvent.Special(KeyEvent.Key.F11);
            case "24~" -> new KeyEvent.Special(KeyEvent.Key.F12);
            default    -> null;
        };
    }

    private KeyEvent parseSS3() throws IOException {
        if (in.available() == 0) return null;
        return switch (in.read()) {
            case 'P' -> new KeyEvent.Special(KeyEvent.Key.F1);
            case 'Q' -> new KeyEvent.Special(KeyEvent.Key.F2);
            case 'R' -> new KeyEvent.Special(KeyEvent.Key.F3);
            case 'S' -> new KeyEvent.Special(KeyEvent.Key.F4);
            default  -> null;
        };
    }
}
