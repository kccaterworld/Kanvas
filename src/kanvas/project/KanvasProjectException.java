package kanvas.project;

import kanvas.KanvasException;

public class KanvasProjectException extends KanvasException {
    public KanvasProjectException(String message) {
        super(message);
    }

    public KanvasProjectException(String message, Throwable cause) {
        super(message, cause);
    }
}
