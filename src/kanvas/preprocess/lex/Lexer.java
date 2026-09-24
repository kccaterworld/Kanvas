package kanvas.preprocess.lex;

import kanvas.preprocess.source.SourcePos;

import java.util.*;

/** Splits .kvs source into tokens, keeping comments and the exact source range of each token. */
public class Lexer {

    /**
     * Reserved words and the literals true/false/null. Contextual keywords (var, yield, record,
     * sealed, permits, when) lex as identifiers so they stay usable as names; the parser
     * recognizes them by position.
     */
    private static final Set<String> KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while",
        "true", "false", "null"
    );

    /**
     * Operators, longest first. '>' is never joined into '>>', '>>>', '>>=' or '>>>=' so that
     * nested generics such as List<List<T>> close with single '>' tokens; the parser rejoins
     * adjacent '>' tokens where it needs a shift operator.
     */
    private static final String[] OPERATORS = {
        "<<=",
        "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=",
        "&=", "|=", "^=", "->", "::", "<<",
        "+", "-", "*", "/", "%", "=", "<", ">", "!", "~", "?", ":", "&", "|", "^"
    };
    private static final String PUNCT_CHARS = "(){}[];,@.";

    private final String source;
    private int index;
    private int line;
    private int column;

    public Lexer(String source) {
        this.source = source == null ? "" : source;
        this.index  = 0;
        this.line   = 1;
        this.column = 1;
    }

    public List<Token> tokenize() throws LexerException {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            while (!atEnd() && Character.isWhitespace(current())) advance();
            SourcePos start = pos();
            if (atEnd()) {
                tokens.add(new Token(TokenType.EOF, "", start, start));
                return tokens;
            }
            TokenType type = readToken(start);
            String text = source.substring(start.offset(), index);
            if (type == TokenType.IDENTIFIER && KEYWORDS.contains(text)) type = TokenType.KEYWORD;
            tokens.add(new Token(type, text, start, pos()));
        }
    }

    /** Consumes one token starting at the current character and returns its type. */
    private TokenType readToken(SourcePos start) throws LexerException {
        char c = current();
        if (Character.isJavaIdentifierStart(c)) {
            while (!atEnd() && Character.isJavaIdentifierPart(current())) advance();
            return TokenType.IDENTIFIER;
        }
        if (c == '"') return readString(start);
        if (c == '\'') return readChar(start);
        if (c == '/' && peek(1) == '/') {
            while (!atEnd() && current() != '\n' && current() != '\r') advance();
            return TokenType.LINE_COMMENT;
        }
        if (c == '/' && peek(1) == '*') return readBlockComment(start);
        if (isDigit(c) || (c == '.' && isDigit(peek(1)))) return readNumber();
        if (source.startsWith("...", index)) { advance(3); return TokenType.PUNCTUATION; }
        if (PUNCT_CHARS.indexOf(c) >= 0) { advance(); return TokenType.PUNCTUATION; }
        for (String op : OPERATORS) {
            if (source.startsWith(op, index)) { advance(op.length()); return TokenType.OPERATOR; }
        }
        throw new LexerException("Unexpected character '" + c + "'", start);
    }

    private TokenType readString(SourcePos start) throws LexerException {
        if (source.startsWith("\"\"\"", index)) {
            advance(3);
            while (!atEnd()) {
                if (current() == '\\') { advance(); if (!atEnd()) advance(); }
                else if (source.startsWith("\"\"\"", index)) { advance(3); return TokenType.TEXT_BLOCK; }
                else advance();
            }
            throw new LexerException("Unterminated text block", start);
        }
        advance(); // opening "
        while (!atEnd()) {
            char c = current();
            if (c == '\\') { advance(); if (!atEnd()) advance(); }
            else if (c == '"') { advance(); return TokenType.STRING_LITERAL; }
            else if (c == '\n' || c == '\r') break;
            else advance();
        }
        throw new LexerException("Unterminated string", start);
    }

    private TokenType readChar(SourcePos start) throws LexerException {
        advance(); // opening '
        int chars = 0;
        while (!atEnd() && current() != '\'' && current() != '\n' && current() != '\r') {
            if (current() == '\\') advance(); // escape: \n, \', \\, A, \123, ...
            if (!atEnd()) advance();
            chars++;
        }
        if (atEnd() || current() != '\'' || chars == 0)
            throw new LexerException("Unterminated char literal", start);
        advance(); // closing '
        return TokenType.CHAR_LITERAL;
    }

    private TokenType readBlockComment(SourcePos start) throws LexerException {
        advance(2); // /*
        while (!atEnd()) {
            if (current() == '*' && peek(1) == '/') { advance(2); return TokenType.BLOCK_COMMENT; }
            advance();
        }
        throw new LexerException("Unterminated block comment", start);
    }

    private TokenType readNumber() {
        boolean isFloat = false;
        if (current() == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            advance(2);
            while (isHexDigit(current()) || current() == '_') advance();
            if (current() == '.') {
                isFloat = true;
                advance();
                while (isHexDigit(current()) || current() == '_') advance();
            }
            if (current() == 'p' || current() == 'P') { isFloat = true; readExponent(); }
        } else if (current() == '0' && (peek(1) == 'b' || peek(1) == 'B')) {
            advance(2);
            while (current() == '0' || current() == '1' || current() == '_') advance();
        } else {
            while (isDigit(current()) || current() == '_') advance();
            if (current() == '.' && fractionFollows()) {
                isFloat = true;
                advance();
                while (isDigit(current()) || current() == '_') advance();
            }
            if (current() == 'e' || current() == 'E') { isFloat = true; readExponent(); }
        }
        if (current() == 'l' || current() == 'L') {
            advance();
        } else if ("fFdD".indexOf(current()) >= 0 && current() != '\0') {
            isFloat = true;
            advance();
        }
        return isFloat ? TokenType.FLOAT_LITERAL : TokenType.INT_LITERAL;
    }

    /**
     * Whether the '.' at the current position belongs to the number: "1.5", "1.", "1.f", "1.e5" —
     * but not "1..2" or a member access.
     */
    private boolean fractionFollows() {
        char n = peek(1);
        if (isDigit(n)) return true;
        if (n == '.') return false;
        if (!Character.isJavaIdentifierStart(n)) return true;
        if ("fFdD".indexOf(n) >= 0) return !Character.isJavaIdentifierPart(peek(2));
        if (n == 'e' || n == 'E') return isDigit(peek(2)) || peek(2) == '+' || peek(2) == '-';
        return false;
    }

    private void readExponent() {
        advance(); // e, E, p or P
        if (current() == '+' || current() == '-') advance();
        while (isDigit(current()) || current() == '_') advance();
    }

    private boolean isDigit(char c)    { return c >= '0' && c <= '9'; }
    private boolean isHexDigit(char c) { return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
    private boolean atEnd()            { return index >= source.length(); }
    private char current()             { return atEnd() ? '\0' : source.charAt(index); }
    private char peek(int offset)      { int i = index + offset; return i < source.length() ? source.charAt(i) : '\0'; }
    private SourcePos pos()            { return new SourcePos(index, line, column); }

    private void advance(int count) { for (int i = 0; i < count && !atEnd(); i++) advance(); }

    private void advance() {
        char c = source.charAt(index++);
        boolean lineBreak = c == '\n' || (c == '\r' && current() != '\n');
        if (lineBreak) { line++; column = 1; } else { column++; }
    }
}
