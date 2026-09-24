package kanvas.preprocess;

import kanvas.KanvasException;

import java.util.*;

public class Lexer {

    public enum TokenType {
        KEYWORD, IDENTIFIER,
        INT_LITERAL, FLOAT_LITERAL, STRING_LITERAL, CHAR_LITERAL,
        OPERATOR, PUNCTUATION,
        LINE_COMMENT, BLOCK_COMMENT,
        EOF
    }

    public static class Token {
        public final TokenType type;
        public final String text;
        public final int offset;
        public final int line;
        public final int column;

        Token(TokenType type, String text, int offset, int line, int column) {
            this.type   = type;
            this.text   = text;
            this.offset = offset;
            this.line   = line;
            this.column = column;
        }

        public boolean is(String value) { return text.equals(value); }
        public boolean is(TokenType t)  { return type == t; }

        @Override
        public String toString() {
            return "Token{" + type + ", \"" + text + "\", " + line + ":" + column + "}";
        }
    }

    public static class LexerException extends KanvasException {
        public LexerException(String message) { super(message); }
    }

    private static final Set<String> KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "var", "record",
        "sealed", "permits", "yield"
    );

    private static final String PUNCT_CHARS = "(){}[];,@";
    private static final String OPERATOR_CHARS = "+-*/%=<>!&|^~?:.";

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
        while (!atEnd()) {
            char c = current();
            if (Character.isWhitespace(c)) advance();
            else if (Character.isJavaIdentifierStart(c)) tokens.add(readIdentifier());
            else if (c == '"') tokens.add(readString());
            else if (c == '\'') tokens.add(readChar());
            else if (c == '/' && peek(1) == '/') tokens.add(readLineComment());
            else if (c == '/' && peek(1) == '*') tokens.add(readBlockComment());
            else if (isDigitChar(c) || (c == '.' && isDigitChar(peek(1)))) tokens.add(readNumber());
            else if (PUNCT_CHARS.indexOf(c) >= 0) tokens.add(readPunctuation());
            else if (OPERATOR_CHARS.indexOf(c) >= 0) tokens.add(readOperator());
            else throw new LexerException("Unexpected character '" + c + "' at " + line + ":" + column);
        }
        tokens.add(new Token(TokenType.EOF, "", index, line, column));
        return tokens;
    }

    private Token readIdentifier() {
        int start = index, startLine = line, startCol = column;
        while (!atEnd() && Character.isJavaIdentifierPart(current())) advance();
        String text = source.substring(start, index);
        TokenType type = KEYWORDS.contains(text) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
        return new Token(type, text, start, startLine, startCol);
    }

    private Token readString() throws LexerException {
        int start = index, startLine = line, startCol = column;
        if (peek(1) == '"' && peek(2) == '"') {
            advance(); advance(); advance();
            while (!atEnd()) {
                if (current() == '"' && peek(1) == '"' && peek(2) == '"') {
                    advance(); advance(); advance();
                    return new Token(TokenType.STRING_LITERAL, source.substring(start, index), start, startLine, startCol);
                }
                advance();
            }
            throw new LexerException("Unterminated text block at " + startLine + ":" + startCol);
        }
        advance(); // opening "
        while (!atEnd()) {
            char c = current();
            if (c == '\\') { advance(); if (!atEnd()) advance(); }
            else if (c == '"') { advance(); return new Token(TokenType.STRING_LITERAL, source.substring(start, index), start, startLine, startCol); }
            else if (c == '\n' || c == '\r') throw new LexerException("Unterminated string at " + startLine + ":" + startCol);
            else advance();
        }
        throw new LexerException("Unterminated string at " + startLine + ":" + startCol);
    }

    private Token readChar() throws LexerException {
        int start = index, startLine = line, startCol = column;
        advance(); // opening '
        if (!atEnd()) {
            if (current() == '\\') { advance(); if (!atEnd()) advance(); }
            else advance();
        }
        if (atEnd() || current() != '\'')
            throw new LexerException("Unterminated char literal at " + startLine + ":" + startCol);
        advance(); // closing '
        return new Token(TokenType.CHAR_LITERAL, source.substring(start, index), start, startLine, startCol);
    }

    private Token readLineComment() {
        int start = index, startLine = line, startCol = column;
        while (!atEnd() && current() != '\n' && current() != '\r') advance();
        return new Token(TokenType.LINE_COMMENT, source.substring(start, index), start, startLine, startCol);
    }

    private Token readBlockComment() throws LexerException {
        int start = index, startLine = line, startCol = column;
        advance(); advance(); // /*
        while (!atEnd()) {
            if (current() == '*' && peek(1) == '/') {
                advance(); advance();
                return new Token(TokenType.BLOCK_COMMENT, source.substring(start, index), start, startLine, startCol);
            }
            advance();
        }
        throw new LexerException("Unterminated block comment at " + startLine + ":" + startCol);
    }

    private Token readNumber() {
        int start = index, startLine = line, startCol = column;
        boolean isFloat = false;
        if (current() == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            advance(); advance();
            while (!atEnd() && isHexDigit(current())) advance();
        } else if (current() == '0' && (peek(1) == 'b' || peek(1) == 'B')) {
            advance(); advance();
            while (!atEnd() && (current() == '0' || current() == '1' || current() == '_')) advance();
        } else {
            while (!atEnd() && (isDigitChar(current()) || current() == '_')) advance();
            if (!atEnd() && current() == '.' && isDigitChar(peek(1))) {
                isFloat = true;
                advance();
                while (!atEnd() && isDigitChar(current())) advance();
            }
            if (!atEnd() && (current() == 'e' || current() == 'E')) {
                isFloat = true;
                advance();
                if (!atEnd() && (current() == '+' || current() == '-')) advance();
                while (!atEnd() && isDigitChar(current())) advance();
            }
        }
        if (!atEnd() && (current() == 'L' || current() == 'l')) {
            advance();
        } else if (!atEnd() && (current() == 'f' || current() == 'F' || current() == 'd' || current() == 'D')) {
            isFloat = true;
            advance();
        }
        return new Token(isFloat ? TokenType.FLOAT_LITERAL : TokenType.INT_LITERAL,
                         source.substring(start, index), start, startLine, startCol);
    }

    private Token readPunctuation() {
        int start = index, startLine = line, startCol = column;
        char c = current();
        if (c == '.' && peek(1) == '.' && peek(2) == '.') {
            advance(); advance(); advance();
            return new Token(TokenType.PUNCTUATION, "...", start, startLine, startCol);
        }
        advance();
        return new Token(TokenType.PUNCTUATION, String.valueOf(c), start, startLine, startCol);
    }

    private Token readOperator() {
        int start = index, startLine = line, startCol = column;
        char c = current();
        advance();
        if (!atEnd()) {
            char n = current();
            String two = String.valueOf(c) + n;
            switch (two) {
                case "==": case "!=": case "<=": case ">=":
                case "&&": case "||": case "++": case "--":
                case "+=": case "-=": case "*=": case "/=":
                case "%=": case "&=": case "|=": case "^=":
                case "->": case "::": case "<<": case ">>":
                    advance();
                    // three-char: >>>, <<=, >>=, >>>=
                    if (!atEnd()) {
                        char m = current();
                        if (two.equals(">>") && m == '>') {
                            advance();
                            if (!atEnd() && current() == '=') { advance(); return tok(">>>=", start, startLine, startCol); }
                            return tok(">>>", start, startLine, startCol);
                        }
                        if (two.equals("<<") && m == '=') { advance(); return tok("<<=", start, startLine, startCol); }
                        if (two.equals(">>") && m == '=') { advance(); return tok(">>=", start, startLine, startCol); }
                    }
                    return tok(two, start, startLine, startCol);
                default:
                    break;
            }
        }
        return tok(String.valueOf(c), start, startLine, startCol);
    }

    private Token tok(String text, int offset, int line, int col) {
        return new Token(TokenType.OPERATOR, text, offset, line, col);
    }

    private boolean isDigitChar(char c) { return c >= '0' && c <= '9'; }
    private boolean isHexDigit(char c)  { return isDigitChar(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '_'; }
    private boolean atEnd()              { return index >= source.length(); }
    private char current()               { return source.charAt(index); }
    private char peek(int offset)        { int i = index + offset; return i < source.length() ? source.charAt(i) : '\0'; }

    private void advance() {
        char c = source.charAt(index++);
        if (c == '\n') { line++; column = 1; } else { column++; }
    }
}
