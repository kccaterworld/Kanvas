package kanvas.tui;

public sealed interface KeyEvent permits KeyEvent.Char, KeyEvent.Special, KeyEvent.Ctrl {

    record Char(char ch) implements KeyEvent {}
    record Special(Key key) implements KeyEvent {}
    record Ctrl(char ch) implements KeyEvent {} // ctrl+a through ctrl+z

    default String name() {
        return switch (this) {
            case Char c    -> String.valueOf(c.ch());
            case Special s -> s.key().name();
            case Ctrl c    -> "ctrl+" + c.ch();
        };
    }

    enum Key {
        UP, DOWN, LEFT, RIGHT,
        HOME, END,
        PAGE_UP, PAGE_DOWN,
        INSERT, DELETE,
        ENTER, BACKSPACE, ESCAPE, TAB,
        F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12
    }
}
