package kanvas;

public class KanvasException extends Exception {
    public KanvasException(String message) {
        super(message);
    }

    public KanvasException(String message, Throwable cause) {
        super(message, cause);
    }
}
