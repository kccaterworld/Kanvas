package kanvas.preprocess;

import kanvas.config.ConfigException;

import java.util.*;

/*
Steps:
Tokenize: Break .kanvas source into tokens (import, class, function, variable, etc.)
Parse: Recognize constructs (global vars, functions, classes)
Generate: Spit out equivalent Java code
Error reporting: Line numbers and clear messages

In:
```
import java.util.*;

ArrayList<Point> particles;

void setup() {
    particles = new ArrayList<>();
}

void draw() {
    for (Point p : particles) {
        // draw
    }
}
```

Out:
```
package com.kanvas;
import java.util.*;

public class Sketch_Generated extends KanvasSketch {
    private ArrayList<Point> particles;
    
    @Override
    public void setup() {
        particles = new ArrayList<>();
    }
    
    @Override
    public void draw() {
        for (Point p : particles) {
            // draw
        }
    }
}
```
*/
public class Preprocessor {
    private static final String DEFAULT_CLASS_NAME = "Sketch_Generated";

    private static final Set<String> KEYWORDS = Set.of(
        "abstract", "boolean", "break", "byte", "case", "catch", "char", "class",
        "continue", "default", "do", "double", "else", "extends", "false", "final",
        "finally", "float", "for", "if", "implements", "import", "instanceof",
        "int", "interface", "long", "new", "null", "package", "private",
        "protected", "public", "return", "short", "static", "super", "switch",
        "this", "throw", "true", "try", "void", "while"
    );
    private static final Set<Character> SYMBOLS = Set.of('(', ')', '{', '}', '[', ']', ';', ',', '.');
    private static final Set<Character> OPERATOR_CHARS = Set.of('+', '-', '*', '/', '%', '=', '!', '<', '>', '&', '|', '^', '~', '?', ':');

    public List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int index = 0, line = 1, column = 1;

        while (index < source.length()) {
            char c = source.charAt(index);
            if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    line++; column = 1;
                } else column++;
                index++; continue;
            }
            if (c == '/' && peek(source, index + 1) == '/') {
                index += 2; column += 2;
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++; column++;
                } continue;
            }
            if (c == '/' && peek(source, index + 1) == '*') {
                int startLine = line, startColumn = column;
                index += 2; column += 2;
                while (index < source.length() && !(source.charAt(index) == '*' && peek(source, index + 1) == '/')) {
                    if (source.charAt(index) == '\n') {
                        line++; column = 1;
                    } else column++;
                    index++;
                }
                if (index >= source.length()) throw error("Unterminated block comment", startLine, startColumn);
                index += 2; column += 2;
                continue;
            }

            if (Character.isJavaIdentifierStart(c)) {
                int start = index, startColumn = column;
                while (index < source.length() && Character.isJavaIdentifierPart(source.charAt(index))) {
                    index++; column++;
                }
                String text = source.substring(start, index);
                tokens.add(new Token(KEYWORDS.contains(text) ? TokenType.KEYWORD : TokenType.IDENTIFIER,
                    text, line, startColumn));
                continue;
            }

            if (Character.isDigit(c)) {
                int start = index, startColumn = column;
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++; column++;
                }
                if (peek(source, index) == '.' && Character.isDigit(peek(source, index + 1))) {
                    index++; column++;
                    while (index < source.length() && Character.isDigit(source.charAt(index))) {
                        index++; column++;
                    }
                }
                if ("fFdDlL".indexOf(peek(source, index)) >= 0) {
                    index++; column++;
                }
                tokens.add(new Token(TokenType.NUMBER, source.substring(start, index), line, startColumn));
                continue;
            }
            if (c == '"' || c == '\'') {
                int startColumn = column;
                char quote = c;
                StringBuilder text = new StringBuilder();
                text.append(c);
                index++; column++;
                boolean escaped = false;
                while (index < source.length()) {
                    c = source.charAt(index);
                    text.append(c);
                    index++; column++;
                    if (c == '\n') throw error("Unterminated string", line, startColumn);
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == quote) break;
                }
                if (text.charAt(text.length() - 1) != quote) throw error("Unterminated string", line, startColumn);
                tokens.add(new Token(((quote == '"') ? TokenType.STRING : TokenType.CHARACTER), text.toString(), line, startColumn));
                continue;
            }
            if (SYMBOLS.contains(c)) {
                tokens.add(new Token(TokenType.SYMBOL, Character.toString(c), line, column));
                index++; column++;
                continue;
            }
            if (OPERATOR_CHARS.contains(c)) {
                int start = index, startColumn = column;
                index++; column++;
                if (OPERATOR_CHARS.contains(peek(source, index)) &&
                    Set.of("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "->", "::")
                        .contains(source.substring(start, index + 1))) {
                    index++; column++;
                }
                tokens.add(new Token(TokenType.OPERATOR, source.substring(start, index), line, startColumn));
                continue;
            }
            throw error("Unexpected character '" + c + "'", line, column);
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    public KanvasProgram parse(String source) {
        return parse(tokenize(source));
    }

    public KanvasProgram parse(List<Token> tokens) {
        Parser parser = new Parser(tokens);
        return parser.parseProgram();
    }

    public String transpile(String kanvasSource, String packageName) {
        return transpile(kanvasSource, packageName, DEFAULT_CLASS_NAME);
    }

    public String transpile(String kanvasSource, String packageName, String className) {
        return generate(parse(kanvasSource), packageName, className);
    }

    public String generate(KanvasProgram program, String packageName) {
        return generate(program, packageName, DEFAULT_CLASS_NAME);
    }

    public String generate(KanvasProgram program, String packageName, String className) {
        String actualPackage = (packageName == null || packageName.isBlank())
            ? program.getPackageName()
            : packageName.trim();
        String actualClassName = (className == null || className.isBlank())
            ? DEFAULT_CLASS_NAME
            : className.trim();
        StringBuilder java = new StringBuilder();

        if (actualPackage != null && !actualPackage.isBlank())
            java.append("package ").append(actualPackage).append(";\n\n");

        for (ImportDefinition importDef : program.getImports())
            java.append(importDef.getText()).append("\n");
        if (!program.getImports().isEmpty()) java.append("\n");

        for (ClassDefinition classDef : program.getClasses())
            java.append(classDef.getText()).append("\n\n");

        java.append("public class ").append(actualClassName).append(" {\n");
        for (GlobalDefinition global : program.getGlobals())
            java.append("    ").append(global.getText()).append("\n");
        if (!program.getGlobals().isEmpty() && !program.getFunctions().isEmpty()) java.append("\n");

        for (int i = 0; i < program.getFunctions().size(); i++) {
            FunctionDefinition function = program.getFunctions().get(i);
            java.append("    ").append(methodDeclarationStart(function.getReturnType())).append(" ")
                .append(function.getName()).append("(").append(function.getParameters()).append(") {\n");
            appendIndentedBody(java, function.getBody(), 2);
            java.append("    }\n");
            if (i < program.getFunctions().size() - 1) java.append("\n");
        }
        java.append("}\n");
        return java.toString();
    }

    private static char peek(String source, int index) {
        return index < source.length() ? source.charAt(index) : '\0';
    }

    private static ConfigException error(String message, int line, int column) {
        return new ConfigException(message + " at line " + line + ", column " + column);
    }

    private static String joinTokens(List<Token> tokens) {
        StringBuilder out = new StringBuilder();
        Token previous = null;
        for (Token token : tokens) {
            if (token.getType() == TokenType.EOF) continue;
            if (previous != null && needsSpace(previous, token)) out.append(' ');
            out.append(token.getText());
            previous = token;
        }
        return out.toString();
    }

    private static boolean needsSpace(Token left, Token right) {
        if ("(".equals(right.getText()) || ")".equals(right.getText()) || ".".equals(right.getText())
            || ",".equals(right.getText()) || ";".equals(right.getText()) || "]".equals(right.getText())
            || "<".equals(right.getText()) || ">".equals(right.getText())) return false;
        if ("(".equals(left.getText()) || ".".equals(left.getText()) || "[".equals(left.getText())
            || "<".equals(left.getText())) return false;
        return isWordLike(left) || isWordLike(right);
    }

    private static boolean isWordLike(Token token) {
        return token.getType() == TokenType.IDENTIFIER || token.getType() == TokenType.KEYWORD
            || token.getType() == TokenType.NUMBER || token.getType() == TokenType.STRING
            || token.getType() == TokenType.CHARACTER;
    }

    private static void appendIndentedBody(StringBuilder java, String body, int indentLevel) {
        if (body == null || body.isBlank()) return;

        String indent = "    ".repeat(indentLevel);
        int depth = 0;
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                appendLine(java, indent, depth, line.toString() + " {");
                line.setLength(0);
                depth++;
            } else if (c == '}') {
                appendLine(java, indent, Math.max(0, depth - 1), line.toString());
                line.setLength(0);
                depth = Math.max(0, depth - 1);
                appendLine(java, indent, depth, "}");
            } else if (c == ';') {
                appendLine(java, indent, depth, line.toString() + ";");
                line.setLength(0);
            } else line.append(c);
        }
        appendLine(java, indent, depth, line.toString());
    }

    private static void appendLine(StringBuilder java, String indent, int depth, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return;
        java.append(indent).append("    ".repeat(depth)).append(trimmed).append("\n");
    }

    private static String methodDeclarationStart(String returnType) {
        if (returnType.startsWith("public ") || returnType.startsWith("private ")
            || returnType.startsWith("protected ")) return returnType;
        return "public " + returnType;
    }

    public enum TokenType {
        IDENTIFIER,
        KEYWORD,
        NUMBER,
        STRING,
        CHARACTER,
        SYMBOL,
        OPERATOR,
        EOF
    }

    public static class Token {
        private final TokenType type;
        private final String text;
        private final int line;
        private final int column;

        public Token(TokenType type, String text, int line, int column) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.column = column;
        }

        public TokenType getType() { return type; }
        public String getText() { return text; }
        public int getLine() { return line; }
        public int getColumn() { return column; }

        public String toString() {
            return type + "(" + text + ")@" + line + ":" + column;
        }
    }

    public static class KanvasProgram {
        private String packageName;
        private final List<ImportDefinition> imports = new ArrayList<>();
        private final List<GlobalDefinition> globals = new ArrayList<>();
        private final List<FunctionDefinition> functions = new ArrayList<>();
        private final List<ClassDefinition> classes = new ArrayList<>();

        public String getPackageName() { return packageName; }
        public List<ImportDefinition> getImports() { return imports; }
        public List<GlobalDefinition> getGlobals() { return globals; }
        public List<FunctionDefinition> getFunctions() { return functions; }
        public List<ClassDefinition> getClasses() { return classes; }
    }

    public static class ImportDefinition {
        private final String text;
        private final int line;
        private final int column;

        ImportDefinition(List<Token> tokens) {
            this.text = joinTokens(tokens);
            this.line = tokens.get(0).getLine();
            this.column = tokens.get(0).getColumn();
        }

        public String getText() { return text; }
        public int getLine() { return line; }
        public int getColumn() { return column; }

        public String toString() { return text; }
    }

    public static class GlobalDefinition {
        private final String text;
        private final int line;
        private final int column;

        GlobalDefinition(List<Token> tokens) {
            this.text = joinTokens(tokens);
            this.line = tokens.get(0).getLine();
            this.column = tokens.get(0).getColumn();
        }

        public String getText() { return text; }
        public int getLine() { return line; }
        public int getColumn() { return column; }

        public String toString() { return text; }
    }

    public static class FunctionDefinition {
        private final String name;
        private final String returnType;
        private final String parameters;
        private final String body;
        private final int line;
        private final int column;

        FunctionDefinition(String name, List<Token> returnTypeTokens, List<Token> parameterTokens,
            List<Token> bodyTokens, Token nameToken) {
            this.name = name;
            this.returnType = joinTokens(returnTypeTokens);
            this.parameters = joinTokens(parameterTokens);
            this.body = joinTokens(bodyTokens);
            this.line = nameToken.getLine();
            this.column = nameToken.getColumn();
        }

        public String getName() { return name; }
        public String getReturnType() { return returnType; }
        public String getParameters() { return parameters; }
        public String getBody() { return body; }
        public int getLine() { return line; }
        public int getColumn() { return column; }

        public String toString() {
            return returnType + " " + name + "(" + parameters + ") {" + body + "}";
        }
    }

    public static class ClassDefinition {
        private final String name;
        private final String text;
        private final int line;
        private final int column;

        ClassDefinition(String name, List<Token> tokens, Token classToken) {
            this.name = name;
            this.text = joinTokens(tokens);
            this.line = classToken.getLine();
            this.column = classToken.getColumn();
        }

        public String getName() { return name; }
        public String getText() { return text; }
        public int getLine() { return line; }
        public int getColumn() { return column; }

        public String toString() { return text; }
    }

    private static class Parser {
        private final List<Token> tokens;
        private int index = 0;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        KanvasProgram parseProgram() {
            KanvasProgram program = new KanvasProgram();
            while (!isAtEnd()) {
                if (match("package")) program.packageName = parsePackage();
                else if (match("import")) program.imports.add(parseImport());
                else if (isClassStart()) program.classes.add(parseClass());
                else if (isFunctionStart()) program.functions.add(parseFunction());
                else program.globals.add(parseGlobal());
            }
            return program;
        }

        private String parsePackage() {
            List<Token> parts = collectUntil(";");
            expect(";", "Expected ';' after package declaration");
            return joinTokens(parts);
        }

        private ImportDefinition parseImport() {
            List<Token> statement = new ArrayList<>();
            statement.add(previous());
            statement.addAll(collectUntil(";"));
            statement.add(expect(";", "Expected ';' after import"));
            return new ImportDefinition(statement);
        }

        private GlobalDefinition parseGlobal() {
            List<Token> statement = collectUntil(";");
            statement.add(expect(";", "Expected ';' after top-level declaration"));
            return new GlobalDefinition(statement);
        }

        private ClassDefinition parseClass() {
            int start = index;
            Token classToken = null;
            String name = "";
            while (!isAtEnd()) {
                if ("class".equals(peekToken().getText())) {
                    classToken = peekToken();
                    if (index + 1 < tokens.size()) name = tokens.get(index + 1).getText();
                    break;
                }
                index++;
            }
            if (classToken == null) throw error("Expected class declaration", peekToken().getLine(), peekToken().getColumn());
            while (!isAtEnd() && !"{".equals(peekToken().getText())) index++;
            if (isAtEnd()) throw error("Expected class body", classToken.getLine(), classToken.getColumn());
            int end = findMatchingBrace(index);
            List<Token> classTokens = new ArrayList<>(tokens.subList(start, end + 1));
            index = end + 1;
            return new ClassDefinition(name, classTokens, classToken);
        }

        private FunctionDefinition parseFunction() {
            int openParen = findNextTopLevel("(");
            Token nameToken = tokens.get(openParen - 1);
            String name = nameToken.getText();
            List<Token> returnType = new ArrayList<>(tokens.subList(index, openParen - 1));
            int closeParen = findMatching(openParen, "(", ")");
            int openBrace = closeParen + 1;
            while (openBrace < tokens.size() && !"{".equals(tokens.get(openBrace).getText())) openBrace++;
            int closeBrace = findMatchingBrace(openBrace);
            List<Token> parameters = new ArrayList<>(tokens.subList(openParen + 1, closeParen));
            List<Token> body = new ArrayList<>(tokens.subList(openBrace + 1, closeBrace));
            index = closeBrace + 1;
            return new FunctionDefinition(name, returnType, parameters, body, nameToken);
        }

        private boolean isFunctionStart() {
            int openParen = findNextTopLevel("(");
            if (openParen <= index || openParen >= tokens.size()) return false;
            Token name = tokens.get(openParen - 1);
            if (name.getType() != TokenType.IDENTIFIER) return false;
            if (Set.of("if", "for", "while", "switch", "catch").contains(name.getText())) return false;
            int closeParen = findMatching(openParen, "(", ")");
            if (closeParen < 0) return false;
            int next = closeParen + 1;
            while (next < tokens.size() && !isAtEnd(next)) {
                String text = tokens.get(next).getText();
                if ("{".equals(text)) return true;
                if (";".equals(text) || "}".equals(text)) return false;
                next++;
            }
            return false;
        }

        private boolean isClassStart() {
            int current = index;
            while (current < tokens.size() && !isAtEnd(current)) {
                String text = tokens.get(current).getText();
                if ("class".equals(text)) return true;
                if (";".equals(text) || "{".equals(text)) return false;
                current++;
            }
            return false;
        }

        private List<Token> collectUntil(String text) {
            List<Token> collected = new ArrayList<>();
            while (!isAtEnd() && !text.equals(peekToken().getText())) collected.add(tokens.get(index++));
            return collected;
        }

        private Token expect(String text, String message) {
            if (isAtEnd() || !text.equals(peekToken().getText()))
                throw error(message, peekToken().getLine(), peekToken().getColumn());
            return tokens.get(index++);
        }

        private boolean match(String text) {
            if (isAtEnd() || !text.equals(peekToken().getText())) return false;
            index++;
            return true;
        }

        private Token previous() {
            return tokens.get(index - 1);
        }

        private Token peekToken() {
            return tokens.get(index);
        }

        private boolean isAtEnd() {
            return isAtEnd(index);
        }

        private boolean isAtEnd(int i) {
            return i >= tokens.size() || tokens.get(i).getType() == TokenType.EOF;
        }

        private int findNextTopLevel(String text) {
            int parenDepth = 0, braceDepth = 0, bracketDepth = 0;
            for (int i = index; i < tokens.size() && !isAtEnd(i); i++) {
                String current = tokens.get(i).getText();
                if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0 && text.equals(current)) return i;
                if ("(".equals(current)) parenDepth++;
                else if (")".equals(current)) parenDepth--;
                else if ("{".equals(current)) braceDepth++;
                else if ("}".equals(current)) braceDepth--;
                else if ("[".equals(current)) bracketDepth++;
                else if ("]".equals(current)) bracketDepth--;
                else if (";".equals(current) && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) return -1;
            }
            return -1;
        }

        private int findMatchingBrace(int openBrace) {
            return findMatching(openBrace, "{", "}");
        }

        private int findMatching(int open, String left, String right) {
            if (open < 0 || open >= tokens.size() || !left.equals(tokens.get(open).getText())) return -1;
            int depth = 0;
            for (int i = open; i < tokens.size() && !isAtEnd(i); i++) {
                if (left.equals(tokens.get(i).getText())) depth++;
                else if (right.equals(tokens.get(i).getText())) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            Token token = tokens.get(open);
            throw error("Unmatched '" + left + "'", token.getLine(), token.getColumn());
        }
    }
}
