package kanvas.preprocess;

import kanvas.KanvasException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Preprocessor {
    public static final String DEFAULT_PACKAGE = "kanvas.generated";

    private enum TokenType {
        IDENTIFIER,
        KEYWORD,
        SYMBOL,
        STRING,
        COMMENT,
        EOF
    }
    private static final Set<String> KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "var"
    );
    private static final Set<String> MODIFIERS = Set.of( "public", "private", "protected", "static", "final" );

    private String sourceCode;
    private String className;

    public Preprocessor(String kanvasSourceCode, String className) {
        this.sourceCode = kanvasSourceCode == null ? "" : kanvasSourceCode;
        this.className = classNameFor(className);
    }
    public Preprocessor(String kvsFilePath) throws KanvasException { this(new File(kvsFilePath)); }
    public Preprocessor(File kvsFile) throws KanvasException {
        if (!kvsFile.exists() || kvsFile.isDirectory() || !kvsFile.canRead())
            throw new KanvasException("File not found or not readable: " + kvsFile.getPath());
        this.className = classNameFor(kvsFile.getName().replaceFirst("[.][^.]+$", ""));
        try { this.sourceCode = Files.readString(kvsFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new KanvasException("Failed to read " + kvsFile.getPath(), e);
        }
    }

    public void setup(String sourceCode) {
        this.sourceCode = sourceCode == null ? "" : sourceCode;
    }

    public String transpile() throws KanvasException {
        return transpile(sourceCode, DEFAULT_PACKAGE, className);
    }

    public String transpile(String sourceCode, String packageName, String className) throws KanvasException {
        ParsedSource parsed = new Parser(sourceCode == null ? "" : sourceCode).parse();
        String generatedClassName = classNameFor(className);

        StringBuilder output = new StringBuilder();
        if (packageName != null && !packageName.isBlank()) {
            output.append("package ").append(packageName).append(";\n\n");
        }

        for (String importLine : parsed.imports)
            output.append(importLine).append("\n");
        output.append("import kanvas.runtime.KanvasScript;\n\n");
        output.append("public class ").append(generatedClassName).append(" extends KanvasScript {\n");

        for (String field : parsed.fields)
            output.append(indent(field)).append("\n\n");
        for (MethodDeclaration method : parsed.methods)
            output.append(indent(method.toSource())).append("\n\n");

        addDefaultMethod(output, parsed.methodNames, "settings");
        addDefaultMethod(output, parsed.methodNames, "setup");
        addDefaultMethod(output, parsed.methodNames, "draw");
        addDefaultMethod(output, parsed.methodNames, "mousePressed");
        addDefaultMethod(output, parsed.methodNames, "mouseReleased");
        addDefaultMethod(output, parsed.methodNames, "mouseClicked");
        addDefaultMethod(output, parsed.methodNames, "mouseDragged");
        addDefaultMethod(output, parsed.methodNames, "mouseWheel");
        addDefaultMethod(output, parsed.methodNames, "keyPressed");
        addDefaultMethod(output, parsed.methodNames, "keyReleased");
        addDefaultMethod(output, parsed.methodNames, "keyTyped");
        addDefaultMethod(output, parsed.methodNames, "windowMoved");

        output.append("}\n");
        return output.toString();
    }

    private static void addDefaultMethod(StringBuilder output, Set<String> methodNames, String methodName) {
        if (methodNames.contains(methodName)) return;
        output.append("    public void ").append(methodName).append("() {}\n\n");
    }

    private static String indent(String source) {
        String[] lines = source.split("\\R", -1);
        StringBuilder indented = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) indented.append("\n");
            if (!lines[i].isBlank()) indented.append("    ");
            indented.append(lines[i]);
        }
        return indented.toString();
    }

    public static String classNameFor(String value) {
        if (value == null || value.isBlank()) return "Sketch";

        StringBuilder name = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isJavaIdentifierPart(c)) {
                if (name.length() == 0 && !Character.isJavaIdentifierStart(c)) name.append('_');
                name.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else {
                capitalizeNext = true;
            }
        }

        return name.length() == 0 ? "Sketch" : name.toString();
    }

    private static class Token {
        final TokenType type;
        final String text;
        final int start;
        final int end;
        final int line;
        final int column;

        Token(TokenType type, String text, int start, int end, int line, int column) {
            this.type = type;
            this.text = text;
            this.start = start;
            this.end = end;
            this.line = line;
            this.column = column;
        }

        boolean is(String value) {
            return text.equals(value);
        }
    }

    private static class ParsedSource {
        final List<String> imports = new ArrayList<>();
        final List<String> fields = new ArrayList<>();
        final List<MethodDeclaration> methods = new ArrayList<>();
        final Set<String> methodNames = new HashSet<>();
    }

    private static class MethodDeclaration {
        final String source;
        final boolean hasAccessModifier;

        MethodDeclaration(String source, boolean hasAccessModifier) {
            this.source = source;
            this.hasAccessModifier = hasAccessModifier;
        }

        String toSource() {
            return hasAccessModifier ? source : "public " + source;
        }
    }

    private static class Lexer {
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private int index = 0;
        private int line = 1;
        private int column = 1;

        Lexer(String source) {
            this.source = source;
        }

        List<Token> lex() throws KanvasException {
            while (!atEnd()) {
                char c = current();
                if (Character.isWhitespace(c)) {
                    advance();
                } else if (Character.isJavaIdentifierStart(c)) {
                    readIdentifier();
                } else if (c == '"' || c == '\'') {
                    readString(c);
                } else if (c == '/' && peek(1) == '/') {
                    readLineComment();
                } else if (c == '/' && peek(1) == '*') {
                    readBlockComment();
                } else {
                    add(TokenType.SYMBOL, String.valueOf(c), index, index + 1);
                    advance();
                }
            }

            tokens.add(new Token(TokenType.EOF, "", source.length(), source.length(), line, column));
            return tokens;
        }

        private void readIdentifier() {
            int start = index;
            int startLine = line;
            int startColumn = column;
            while (!atEnd() && Character.isJavaIdentifierPart(current())) advance();
            String text = source.substring(start, index);
            TokenType type = KEYWORDS.contains(text) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
            tokens.add(new Token(type, text, start, index, startLine, startColumn));
        }

        private void readString(char quote) throws KanvasException {
            int start = index;
            int startLine = line;
            int startColumn = column;
            advance();
            boolean escaped = false;
            while (!atEnd()) {
                char c = current();
                advance();
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    tokens.add(new Token(TokenType.STRING, source.substring(start, index), start, index, startLine, startColumn));
                    return;
                } else if (quote == '\'' && (c == '\n' || c == '\r')) {
                    throw new KanvasException("Unterminated character literal at line " + startLine + ", column " + startColumn);
                }
            }
            throw new KanvasException("Unterminated string literal at line " + startLine + ", column " + startColumn);
        }

        private void readLineComment() {
            int start = index;
            int startLine = line;
            int startColumn = column;
            while (!atEnd() && current() != '\n' && current() != '\r') advance();
            tokens.add(new Token(TokenType.COMMENT, source.substring(start, index), start, index, startLine, startColumn));
        }

        private void readBlockComment() throws KanvasException {
            int start = index;
            int startLine = line;
            int startColumn = column;
            advance();
            advance();
            while (!atEnd()) {
                if (current() == '*' && peek(1) == '/') {
                    advance();
                    advance();
                    tokens.add(new Token(TokenType.COMMENT, source.substring(start, index), start, index, startLine, startColumn));
                    return;
                }
                advance();
            }
            throw new KanvasException("Unterminated block comment at line " + startLine + ", column " + startColumn);
        }

        private boolean atEnd() {
            return index >= source.length();
        }

        private char current() {
            return source.charAt(index);
        }

        private char peek(int offset) {
            int next = index + offset;
            return next >= source.length() ? '\0' : source.charAt(next);
        }

        private void advance() {
            char c = source.charAt(index++);
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        private void add(TokenType type, String text, int start, int end) {
            tokens.add(new Token(type, text, start, end, line, column));
        }

    }

    private static class Parser {
        private final String source;
        private final List<Token> tokens;
        private int index = 0;

        Parser(String source) throws KanvasException {
            this.source = source;
            this.tokens = new Lexer(source).lex();
        }

        ParsedSource parse() throws KanvasException {
            ParsedSource parsed = new ParsedSource();
            while (!atEnd()) {
                skipComments();
                if (atEnd()) break;

                Token token = peek();
                if (token.is("import")) {
                    parsed.imports.add(readUntilSemicolon(token.start));
                } else if (token.is("package")) {
                    readUntilSemicolon(token.start);
                } else if (token.is("class") || token.is("interface") || token.is("enum")) {
                    throw new KanvasException("Top-level " + token.text + " declarations are not supported in .kvs files yet at line " + token.line + ", column " + token.column);
                } else { readMember(parsed); }
            }
            return parsed;
        }

        private void readMember(ParsedSource parsed) throws KanvasException {
            int startIndex = index;
            Token start = peek();
            boolean hasAccessModifier = false;
            while (MODIFIERS.contains(peek().text)) {
                if (peek().is("public") || peek().is("private") || peek().is("protected")) {
                    hasAccessModifier = true;
                } advance();
            }

            MemberKind kind = findMemberKind(startIndex);
            if (kind == null) throw new KanvasException("Expected a top-level variable or method declaration at line " + start.line + ", column " + start.column);

            index = startIndex;
            if (kind.method) {
                MethodReadResult method = readMethod(start.start, hasAccessModifier);
                parsed.methods.add(new MethodDeclaration(method.source, hasAccessModifier));
                parsed.methodNames.add(method.name);
            } else { parsed.fields.add(readVariable(start.start)); }
        }

        private MemberKind findMemberKind(int startIndex) throws KanvasException {
            int parenDepth = 0;
            int braceDepth = 0;
            int bracketDepth = 0;
            boolean sawEquals = false;

            for (int i = startIndex; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.type == TokenType.EOF) return null;
                if (token.type == TokenType.COMMENT) continue;

                if (token.is("(")) {
                    if (parenDepth == 0 && !sawEquals) {
                        Token previous = prevSig(i);
                        Token next = nextSig(i);
                        if (previous != null && next != null && nextMemberCanHaveBody(next)) return new MemberKind(true, previous.text);
                    } parenDepth++;
                } else if (token.is(")")) {
                    parenDepth--;
                    if (parenDepth < 0) throw new KanvasException("Unexpected ')' at line " + token.line + ", column " + token.column);
                } else if (token.is("{")) {
                    braceDepth++;
                } else if (token.is("}")) {
                    braceDepth--;
                    if (braceDepth < 0) throw new KanvasException("Unexpected '}' at line " + token.line + ", column " + token.column);
                } else if (token.is("[")) {
                    bracketDepth++;
                } else if (token.is("]")) {
                    bracketDepth--;
                    if (bracketDepth < 0) throw new KanvasException("Unexpected ']' at line " + token.line + ", column " + token.column);
                } else if (token.is("=") && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    sawEquals = true;
                    Token previous = prevSig(i);
                    return previous == null ? null : new MemberKind(false, previous.text);
                } else if (token.is(";") && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    Token previous = prevSig(i);
                    return previous == null ? null : new MemberKind(false, previous.text);
                }
            }

            return null;
        }

        private MethodReadResult readMethod(int startOffset, boolean hasAccessModifier) throws KanvasException {
            Token openParen = null;
            Token name = null;
            while (!atEnd()) {
                if (peek().is("(")) {
                    openParen = peek();
                    name = prevSig(index);
                    break;
                } advance();
            }

            if (openParen == null || name == null) throw new KanvasException("Expected method parameter list at line " + peek().line + ", column " + peek().column);
            if (!isNameToken(name)) throw new KanvasException("Expected method name at line " + name.line + ", column " + name.column);
            advance();
            consumeBalanced("(", ")", openParen);

            while (!atEnd() && !peek().is("{")) {
                if (peek().is(";")) throw new KanvasException("Method declarations in .kvs must have a body at line " + peek().line + ", column " + peek().column);
                advance();
            }
            if (atEnd()) throw new KanvasException("Expected method body at line " + openParen.line + ", column " + openParen.column);

            Token bodyStart = peek();
            advance();
            consumeBalanced("{", "}", bodyStart);

            String methodSource = source.substring(startOffset, prev().end).trim();
            return new MethodReadResult(hasAccessModifier ? methodSource : methodSource, name.text);
        }

        private String readVariable(int startOffset) throws KanvasException {
            int parenDepth = 0;
            int braceDepth = 0;
            int bracketDepth = 0;
            while (!atEnd()) {
                Token token = peek();
                if (token.is("(")) parenDepth++;
                else if (token.is(")")) parenDepth--;
                else if (token.is("{")) braceDepth++;
                else if (token.is("}")) braceDepth--;
                else if (token.is("[")) bracketDepth++;
                else if (token.is("]")) bracketDepth--;
                else if (token.is(";") && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    advance();
                    return source.substring(startOffset, prev().end).trim();
                }

                if (parenDepth < 0 || braceDepth < 0 || bracketDepth < 0) {
                    throw new KanvasException("Unbalanced declaration at line " + token.line + ", column " + token.column);
                }
                advance();
            }
            throw new KanvasException("Expected ';' after variable declaration at line " + prev().line + ", column " + prev().column);
        }

        private String readUntilSemicolon(int startOffset) throws KanvasException {
            while (!atEnd()) {
                if (peek().is(";")) { advance();
                    return source.substring(startOffset, prev().end).trim();
                } advance();
            } throw new KanvasException("Expected ';' at line " + prev().line + ", column " + prev().column);
        }

        private void consumeBalanced(String open, String close, Token start) throws KanvasException {
            int depth = 1;
            while (!atEnd()) {
                Token token = peek();
                if (token.is(open)) depth++;
                else if (token.is(close)) depth--;
                advance();
                if (depth == 0) return;
            }
            throw new KanvasException("Expected matching '" + close + "' at line " + start.line + ", column " + start.column);
        }

        private boolean nextMemberCanHaveBody(Token token) {
            return token.is(")") || isNameToken(token) || token.type == TokenType.KEYWORD;
        }

        private boolean isNameToken(Token token) {
            return token.type == TokenType.IDENTIFIER || token.type == TokenType.KEYWORD;
        }

        private Token prevSig(int fromIndex) {
            for (int i = fromIndex - 1; i >= 0; i--) {
                Token token = tokens.get(i);
                if (token.type != TokenType.COMMENT) return token;
            }
            return null;
        }

        private Token nextSig(int fromIndex) {
            for (int i = fromIndex + 1; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.type != TokenType.COMMENT) return token;
            }
            return null;
        }

        private void skipComments() {
            while (peek().type == TokenType.COMMENT) advance();
        }

        private boolean atEnd() {
            return peek().type == TokenType.EOF;
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token prev() {
            return tokens.get(index - 1);
        }

        private void advance() {
            if (!atEnd()) index++;
        }

    }

    private static class MemberKind {
        final boolean method;
        final String name;

        MemberKind(boolean method, String name) {
            this.method = method;
            this.name = name;
        }
    }

    private static class MethodReadResult {
        final String source;
        final String name;

        MethodReadResult(String source, String name) {
            this.source = source;
            this.name = name;
        }
    }
}
