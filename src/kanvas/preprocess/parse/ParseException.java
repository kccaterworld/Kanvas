package kanvas.preprocess.parse;

import kanvas.preprocess.source.PreprocessException;
import kanvas.preprocess.source.SourcePos;

/** Tokens that do not form valid .kvs syntax. */
public class ParseException extends PreprocessException {
    public ParseException(String detail, SourcePos pos) { super(detail, pos); }
}
