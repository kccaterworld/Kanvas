package kanvas.builder;

import kanvas.KanvasException;

public class KanvasCompileException extends KanvasException {
    public KanvasCompileException(String message) { super(message); }

    public KanvasCompileException(String message, Throwable cause) { super(message, cause); }
}
