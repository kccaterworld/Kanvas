package kanvas.dependency;

import kanvas.KanvasException;

public class DependencyException extends KanvasException {
    public DependencyException(String message) { super(message); }
    public DependencyException(String message, Throwable cause) { super(message, cause); }
}
