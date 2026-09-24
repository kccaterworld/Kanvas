package kanvas.preprocess.source;

import kanvas.KanvasException;

/** An error in a .kvs file, tied to the position where it occurs. */
public class PreprocessException extends KanvasException {
    private final String detail;
    private final SourcePos pos;

    public PreprocessException(String detail, SourcePos pos) {
        super(detail + " at " + pos);
        this.detail = detail;
        this.pos = pos;
    }

    /** The message without the position. */
    public String detail() { return detail; }
    public SourcePos pos() { return pos; }
}
