package kanvas.preprocess.lex;

import kanvas.preprocess.source.PreprocessException;
import kanvas.preprocess.source.SourcePos;

/** Source text that cannot be split into tokens, such as an unterminated string. */
public class LexerException extends PreprocessException {
    public LexerException(String detail, SourcePos pos) { super(detail, pos); }
}
