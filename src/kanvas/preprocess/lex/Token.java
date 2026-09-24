package kanvas.preprocess.lex;

import kanvas.preprocess.source.SourcePos;

/** A token and the source range it covers ({@code end} is exclusive). */
public record Token(TokenType type, String text, SourcePos start, SourcePos end) {
    public boolean is(String value) { return text.equals(value); }
    public boolean is(TokenType t)  { return type == t; }
    public boolean isComment()      { return type == TokenType.LINE_COMMENT || type == TokenType.BLOCK_COMMENT; }

    @Override
    public String toString() {
        return "Token{" + type + ", \"" + text + "\", " + start + "}";
    }
}
