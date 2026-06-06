package kanvas.config;

import kanvas.KanvasException;

public class ConfigException extends KanvasException {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
