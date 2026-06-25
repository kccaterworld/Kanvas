package kanvas.builder;

public class MissingDependencyException extends KanvasCompileException {
    public MissingDependencyException(String message) { super(message); }

    public MissingDependencyException(String message, Throwable cause) { super(message, cause); }
}
