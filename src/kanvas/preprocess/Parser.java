package kanvas.preprocess;

import kanvas.KanvasException;

import java.util.*;

public class Parser {

    public static class ParseException extends KanvasException {
        public ParseException(String message) { super(message); }
    }

    private static final Set<String> MODIFIERS = Set.of(
        "public", "private", "protected", "static", "final",
        "abstract", "synchronized", "volatile", "transient", "native", "strictfp"
    );
    private static final Set<String> ACCESS_MODIFIERS = Set.of("public", "private", "protected");

    private final String source;
    private final List<Lexer.Token> tokens;
    private int index;

    public Parser(String source) throws ParseException {
        this.source = source == null ? "" : source;
        try {
            this.tokens = new Lexer(this.source).tokenize();
        } catch (Lexer.LexerException e) {
            throw new ParseException(e.getMessage());
        }
        this.index = 0;
    }

    public KanvasSyntaxTree.KanvasFile parse() throws ParseException {
        List<KanvasSyntaxTree.ImportDecl> imports = new ArrayList<>();
        List<KanvasSyntaxTree.FieldDecl>  fields  = new ArrayList<>();
        List<KanvasSyntaxTree.MethodDecl> methods = new ArrayList<>();
        boolean isTUI = false;

        while (!atEnd()) {
            skipComments();
            if (atEnd()) break;

            Lexer.Token tok = peek();
            if (tok.is("package")) {
                readUntilSemicolon();
            } else if (tok.is("import")) {
                KanvasSyntaxTree.ImportDecl decl = readImport();
                if (decl.qualifiedName.equals("kanvas.tui")) isTUI = true;
                imports.add(decl);
            } else if (tok.is("class") || tok.is("interface") || tok.is("enum") || tok.is("record")) {
                throw new ParseException("Top-level " + tok.text + " declarations are not supported in .kvs files at " + tok.line + ":" + tok.column);
            } else {
                readMember(fields, methods);
            }
        }

        return new KanvasSyntaxTree.KanvasFile(posOf(0), imports, fields, methods, isTUI);
    }

    // -------------------------------------------------------------------------
    // Import
    // -------------------------------------------------------------------------

    private KanvasSyntaxTree.ImportDecl readImport() throws ParseException {
        Lexer.Token start = peek();
        advance(); // consume 'import'
        skipComments();

        StringBuilder qualified = new StringBuilder();
        while (!atEnd() && !peek().is(";")) {
            skipComments();
            if (!atEnd() && !peek().is(";")) {
                qualified.append(peek().text);
                advance();
            }
        }
        if (atEnd()) throw new ParseException("Expected ';' after import at " + start.line + ":" + start.column);
        advance(); // consume ';'

        String qualName = qualified.toString().trim();
        String raw = source.substring(start.offset, prev().offset + prev().text.length()).trim();
        boolean isKanvasLib = qualName.startsWith("kanvas.");
        return new KanvasSyntaxTree.ImportDecl(posOf(index - 1), qualName, raw, isKanvasLib);
    }

    // -------------------------------------------------------------------------
    // Member dispatch: field vs method
    // -------------------------------------------------------------------------

    private void readMember(List<KanvasSyntaxTree.FieldDecl> fields,
                            List<KanvasSyntaxTree.MethodDecl> methods) throws ParseException {
        int startIndex = index;
        Lexer.Token startTok = peek();

        // Pass 1: peek at modifiers to detect access modifier
        boolean hasAccessModifier = false;
        while (!atEnd() && isModifier(peek())) {
            if (ACCESS_MODIFIERS.contains(peek().text)) hasAccessModifier = true;
            advance();
            skipComments();
        }
        boolean isMethod = isMethodAhead();

        // Restore and dispatch
        index = startIndex;
        if (isMethod) {
            methods.add(readMethod(startTok.offset, hasAccessModifier));
        } else {
            fields.add(readField(startTok.offset));
        }
    }

    /**
     * Lookahead from the current position (after modifiers have been consumed)
     * to determine whether the next declaration is a method.
     * Saves and restores {@code index}.
     */
    private boolean isMethodAhead() {
        int saved = index;
        try {
            int parenDepth = 0, braceDepth = 0, bracketDepth = 0;
            boolean sawEquals = false;
            while (!atEnd()) {
                skipComments();
                if (atEnd()) break;
                Lexer.Token t = peek();
                if (t.is("(")) {
                    if (parenDepth == 0 && !sawEquals) {
                        Lexer.Token prev = prevSig(index);
                        Lexer.Token next = nextSig(index);
                        if (prev != null && next != null && isNameToken(prev)) return true;
                    }
                    parenDepth++;
                } else if (t.is(")")) {
                    parenDepth--;
                } else if (t.is("{")) {
                    braceDepth++;
                } else if (t.is("}")) {
                    braceDepth--;
                    if (braceDepth < 0) break;
                } else if (t.is("[")) {
                    bracketDepth++;
                } else if (t.is("]")) {
                    bracketDepth--;
                } else if (t.is("=") && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    sawEquals = true;
                } else if (t.is(";") && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    break;
                }
                advance();
            }
            return false;
        } finally {
            index = saved;
        }
    }

    // -------------------------------------------------------------------------
    // Method
    // -------------------------------------------------------------------------

    private KanvasSyntaxTree.MethodDecl readMethod(int startOffset, boolean hasAccessModifier) throws ParseException {
        skipComments();

        // Collect modifiers
        List<String> modifiers = new ArrayList<>();
        while (!atEnd() && isModifier(peek())) {
            modifiers.add(peek().text);
            advance();
            skipComments();
        }

        // Collect everything before '(' — last token is the method name
        List<Lexer.Token> beforeParen = new ArrayList<>();
        while (!atEnd() && !peek().is("(")) {
            skipComments();
            if (!atEnd() && !peek().is("(")) {
                beforeParen.add(peek());
                advance();
            }
        }
        if (beforeParen.isEmpty() || atEnd())
            throw new ParseException("Expected return type and method name at " + peek().line + ":" + peek().column);

        Lexer.Token nameTok = beforeParen.get(beforeParen.size() - 1);
        if (!isNameToken(nameTok))
            throw new ParseException("Expected method name at " + nameTok.line + ":" + nameTok.column);

        // Return type = all tokens before the name
        StringBuilder retType = new StringBuilder();
        for (int i = 0; i < beforeParen.size() - 1; i++) {
            Lexer.Token t = beforeParen.get(i);
            if (retType.length() > 0 && needsSpace(beforeParen.get(i - 1), t)) retType.append(' ');
            retType.append(t.text);
        }

        Lexer.Token openParen = peek();
        advance(); // consume '('

        List<KanvasSyntaxTree.Param> params = readParams(openParen);

        // Optional throws clause
        List<String> throwsTypes = new ArrayList<>();
        skipComments();
        if (!atEnd() && peek().is("throws")) {
            advance();
            while (!atEnd() && !peek().is("{")) {
                skipComments();
                if (atEnd() || peek().is("{")) break;
                Lexer.Token t = peek();
                if (!t.is(",")) throwsTypes.add(t.text);
                advance();
            }
        }

        // Body
        skipComments();
        if (atEnd() || !peek().is("{"))
            throw new ParseException("Expected method body '{' at " + (atEnd() ? "EOF" : peek().line + ":" + peek().column));

        int bodyOffset = peek().offset;
        KanvasSyntaxTree.BlockStmt body = parseBlock();
        int bodyEnd = prev().offset + prev().text.length();

        String rawBody = source.substring(bodyOffset, bodyEnd);
        String raw     = source.substring(startOffset, bodyEnd).trim();

        KanvasSyntaxTree.SourcePos pos = new KanvasSyntaxTree.SourcePos(startOffset, nameTok.line, nameTok.column);
        return new KanvasSyntaxTree.MethodDecl(pos, modifiers, retType.toString().trim(),
                nameTok.text, params, throwsTypes, body, rawBody, raw, hasAccessModifier);
    }

    // -------------------------------------------------------------------------
    // Parameters
    // -------------------------------------------------------------------------

    private List<KanvasSyntaxTree.Param> readParams(Lexer.Token openParen) throws ParseException {
        List<KanvasSyntaxTree.Param> params = new ArrayList<>();
        skipComments();
        if (!atEnd() && peek().is(")")) { advance(); return params; }

        while (!atEnd()) {
            skipComments();
            if (atEnd() || peek().is(")")) { if (!atEnd()) advance(); break; }

            // Collect tokens for one parameter (comma-separated at angle/paren depth 0)
            List<Lexer.Token> paramToks = new ArrayList<>();
            int angleDepth = 0, bracketDepth = 0, parenDepth = 0;
            while (!atEnd()) {
                skipComments();
                if (atEnd()) break;
                Lexer.Token t = peek();
                if (t.is("<"))                                         { angleDepth++; }
                else if (t.is(">") || t.is(">>") || t.is(">>>"))      { angleDepth -= t.text.length() == 1 ? 1 : t.text.length() - 1; }
                else if (t.is("["))                                    { bracketDepth++; }
                else if (t.is("]"))                                    { bracketDepth--; }
                else if (t.is("("))                                    { parenDepth++; }
                else if (t.is(")")) {
                    if (parenDepth == 0 && angleDepth == 0 && bracketDepth == 0) break;
                    parenDepth--;
                } else if (t.is(",") && angleDepth == 0 && bracketDepth == 0 && parenDepth == 0) {
                    break;
                }
                paramToks.add(t);
                advance();
            }

            if (!paramToks.isEmpty()) params.add(parseParam(paramToks, openParen));
            if (!atEnd() && peek().is(",")) { advance(); continue; }
            if (!atEnd() && peek().is(")")) { advance(); break; }
        }
        return params;
    }

    private KanvasSyntaxTree.Param parseParam(List<Lexer.Token> toks, Lexer.Token openParen) throws ParseException {
        if (toks.isEmpty())
            throw new ParseException("Empty parameter at " + openParen.line + ":" + openParen.column);

        // Skip leading annotations: @Name
        int start = 0;
        while (start < toks.size() && toks.get(start).is("@")) {
            start++; // @
            if (start < toks.size()) start++; // annotation name
        }

        if (toks.size() - start < 2) {
            // Graceful fallback: treat everything as the type with no name
            String raw = buildTokenString(toks, 0, toks.size());
            KanvasSyntaxTree.SourcePos pos = new KanvasSyntaxTree.SourcePos(toks.get(0).offset, toks.get(0).line, toks.get(0).column);
            return new KanvasSyntaxTree.Param(pos, raw, "", false);
        }

        Lexer.Token nameTok = toks.get(toks.size() - 1);
        int typeEnd = toks.size() - 1;

        // Check for varargs: the token before the name is "..."
        boolean varargs = false;
        if (typeEnd > start && toks.get(typeEnd - 1).is("...")) {
            varargs = true;
            typeEnd--;
        }

        String type = buildTokenString(toks, start, typeEnd);
        KanvasSyntaxTree.SourcePos pos = new KanvasSyntaxTree.SourcePos(toks.get(start).offset, toks.get(start).line, toks.get(start).column);
        return new KanvasSyntaxTree.Param(pos, type, nameTok.text, varargs);
    }

    /** Concatenates token texts, adding a space between adjacent identifier/keyword tokens. */
    private String buildTokenString(List<Lexer.Token> toks, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            Lexer.Token t = toks.get(i);
            if (sb.length() > 0 && i > from && needsSpace(toks.get(i - 1), t)) sb.append(' ');
            sb.append(t.text);
        }
        return sb.toString().trim();
    }

    // -------------------------------------------------------------------------
    // Field
    // -------------------------------------------------------------------------

    private KanvasSyntaxTree.FieldDecl readField(int startOffset) throws ParseException {
        skipComments();

        List<String> modifiers = new ArrayList<>();
        while (!atEnd() && isModifier(peek())) {
            modifiers.add(peek().text);
            advance();
            skipComments();
        }

        // Collect type+name tokens until '=' or ';'
        List<Lexer.Token> decl = new ArrayList<>();
        int parenDepth = 0, braceDepth = 0, bracketDepth = 0;

        outer:
        while (!atEnd()) {
            skipComments();
            if (atEnd()) break;
            Lexer.Token t = peek();

            if (t.is("("))      parenDepth++;
            else if (t.is(")")) parenDepth--;
            else if (t.is("{")) braceDepth++;
            else if (t.is("}")) braceDepth--;
            else if (t.is("[")) bracketDepth++;
            else if (t.is("]")) bracketDepth--;
            else if (t.is(";") && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                advance();
                break;
            } else if (t.is("=") && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                // Name is last token in decl — consume initializer up to matching ';'
                advance(); // skip '='
                int pd = 0, bd = 0;
                while (!atEnd()) {
                    Lexer.Token r = peek();
                    if (r.is("("))      pd++;
                    else if (r.is(")")) pd--;
                    else if (r.is("{")) bd++;
                    else if (r.is("}")) bd--;
                    if (r.is(";") && pd == 0 && bd == 0) { advance(); break outer; }
                    advance();
                }
                break;
            }

            decl.add(t);
            advance();
        }

        // Extract name: last identifier-like token in decl (skip trailing [] )
        String fieldName = "";
        String fieldType = "";
        if (!decl.isEmpty()) {
            int nameIdx = decl.size() - 1;
            while (nameIdx >= 0 && (decl.get(nameIdx).is("]") || decl.get(nameIdx).is("["))) nameIdx--;
            if (nameIdx >= 0 && isNameToken(decl.get(nameIdx))) {
                fieldName = decl.get(nameIdx).text;
                fieldType = buildTokenString(decl, 0, nameIdx);
            }
        }

        String raw = source.substring(startOffset, prev().offset + prev().text.length()).trim();
        KanvasSyntaxTree.SourcePos pos = decl.isEmpty()
                ? new KanvasSyntaxTree.SourcePos(startOffset, 1, 1)
                : new KanvasSyntaxTree.SourcePos(decl.get(0).offset, decl.get(0).line, decl.get(0).column);
        return new KanvasSyntaxTree.FieldDecl(pos, modifiers, fieldType, fieldName, raw);
    }

    // =========================================================================
    // Statement parsing
    // =========================================================================

    private static final Set<String> LOCAL_MODIFIERS = Set.of("final");
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
        "int","float","double","long","short","byte","char","boolean","void","var"
    );

    /** Parses a {@code { stmts... }} block. Consumes the surrounding braces. */
    KanvasSyntaxTree.BlockStmt parseBlock() throws ParseException {
        skipComments();
        if (atEnd() || !peek().is("{"))
            throw new ParseException("Expected '{' at " + (atEnd() ? "EOF" : peek().line + ":" + peek().column));
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // consume '{'
        List<KanvasSyntaxTree.Stmt> stmts = new ArrayList<>();
        while (!atEnd() && !peek().is("}")) {
            skipComments();
            if (!atEnd() && !peek().is("}")) stmts.add(parseStatement());
        }
        if (!atEnd()) advance(); // consume '}'
        return new KanvasSyntaxTree.BlockStmt(pos, stmts);
    }

    private KanvasSyntaxTree.Stmt parseStatement() {
        int saved = index;
        try { return parseStatementImpl(); }
        catch (Exception e) { index = saved; return parseRawStmt(); }
    }

    private KanvasSyntaxTree.Stmt parseStatementImpl() throws ParseException {
        skipComments();
        if (atEnd()) return new KanvasSyntaxTree.RawStmt(sp(), "");
        KanvasSyntaxTree.SourcePos pos = sp();
        Lexer.Token t = peek();

        if (t.is("{"))           return parseBlock();
        if (t.is("if"))          return parseIf();
        if (t.is("while"))       return parseWhile();
        if (t.is("do"))          return parseDo();
        if (t.is("for"))         return parseFor();
        if (t.is("return"))      return parseReturn();
        if (t.is("break"))       { advance(); String lbl = labelOpt(); consume(";"); return new KanvasSyntaxTree.BreakStmt(pos, lbl); }
        if (t.is("continue"))    { advance(); String lbl = labelOpt(); consume(";"); return new KanvasSyntaxTree.ContinueStmt(pos, lbl); }
        if (t.is("throw"))       { advance(); KanvasSyntaxTree.Expr e = parseExpr(); consume(";"); return new KanvasSyntaxTree.ThrowStmt(pos, e); }
        if (t.is("try"))         return parseTry();
        if (t.is("synchronized")) return parseSynchronized();
        if (t.is(";"))           { advance(); return new KanvasSyntaxTree.RawStmt(pos, ";"); }

        if (looksLikeVarDecl()) return parseVarDecl();
        return parseExprStmt();
    }

    private KanvasSyntaxTree.Stmt parseIf() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // 'if'
        consume("(");
        KanvasSyntaxTree.Expr cond = parseExpr();
        consume(")");
        KanvasSyntaxTree.Stmt then = parseStatement();
        KanvasSyntaxTree.Stmt else_ = null;
        skipComments();
        if (!atEnd() && peek().is("else")) { advance(); else_ = parseStatement(); }
        return new KanvasSyntaxTree.IfStmt(pos, cond, then, else_);
    }

    private KanvasSyntaxTree.Stmt parseWhile() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // 'while'
        consume("(");
        KanvasSyntaxTree.Expr cond = parseExpr();
        consume(")");
        return new KanvasSyntaxTree.WhileStmt(pos, cond, parseStatement());
    }

    private KanvasSyntaxTree.Stmt parseDo() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // 'do'
        KanvasSyntaxTree.Stmt body = parseStatement();
        consume("while"); consume("(");
        KanvasSyntaxTree.Expr cond = parseExpr();
        consume(")"); consume(";");
        return new KanvasSyntaxTree.DoWhileStmt(pos, body, cond);
    }

    private KanvasSyntaxTree.Stmt parseFor() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // 'for'
        consume("(");

        // Determine traditional vs enhanced for
        if (looksLikeForEach()) return parseForEach(pos);

        // Traditional: init; cond; update
        KanvasSyntaxTree.Stmt init = null;
        if (!peek().is(";")) {
            if (looksLikeVarDecl()) init = parseVarDeclNoSemi();
            else init = new KanvasSyntaxTree.ExprStmt(sp(), parseExpr());
        }
        consume(";");
        KanvasSyntaxTree.Expr cond = peek().is(";") ? null : parseExpr();
        consume(";");
        List<KanvasSyntaxTree.Expr> update = new ArrayList<>();
        while (!atEnd() && !peek().is(")")) {
            update.add(parseExpr());
            if (!atEnd() && peek().is(",")) advance();
        }
        consume(")");
        return new KanvasSyntaxTree.ForStmt(pos, init, cond, update, parseStatement());
    }

    private KanvasSyntaxTree.Stmt parseForEach(KanvasSyntaxTree.SourcePos pos) throws ParseException {
        // Already past '(' — parse: type name : iterable
        String type = parseTypeName();
        String name = peek().text; advance();
        consume(":");
        KanvasSyntaxTree.Expr iterable = parseExpr();
        consume(")");
        return new KanvasSyntaxTree.ForEachStmt(pos, type, name, iterable, parseStatement());
    }

    private KanvasSyntaxTree.Stmt parseReturn() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // 'return'
        skipComments();
        if (!atEnd() && peek().is(";")) { advance(); return new KanvasSyntaxTree.ReturnStmt(pos, null); }
        KanvasSyntaxTree.Expr val = parseExpr();
        consume(";");
        return new KanvasSyntaxTree.ReturnStmt(pos, val);
    }

    private KanvasSyntaxTree.Stmt parseTry() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // 'try'
        // Skip optional resource list: try (res) { }
        if (!atEnd() && peek().is("(")) { int d = 1; advance(); while (!atEnd() && d > 0) { if (peek().is("(")) d++; else if (peek().is(")")) d--; advance(); } }
        KanvasSyntaxTree.BlockStmt body = parseBlock();
        List<KanvasSyntaxTree.CatchClause> catches = new ArrayList<>();
        KanvasSyntaxTree.BlockStmt finally_ = null;
        skipComments();
        while (!atEnd() && peek().is("catch")) {
            advance(); consume("(");
            // type may be multi-catch: IOException | RuntimeException e
            StringBuilder excType = new StringBuilder();
            while (!atEnd() && !peek().is(")") && !(peek().type == Lexer.TokenType.IDENTIFIER && nextSig(index) != null && nextSig(index).is(")"))) {
                excType.append(peek().text); advance();
            }
            // last token before ')' is the name
            String excName = prev().text;
            String excTypeStr = excType.toString().trim();
            if (excTypeStr.endsWith(excName)) excTypeStr = excTypeStr.substring(0, excTypeStr.length() - excName.length()).trim();
            consume(")");
            catches.add(new KanvasSyntaxTree.CatchClause(pos, excTypeStr, excName, parseBlock()));
            skipComments();
        }
        if (!atEnd() && peek().is("finally")) { advance(); finally_ = parseBlock(); }
        return new KanvasSyntaxTree.TryStmt(pos, body, catches, finally_);
    }

    private KanvasSyntaxTree.Stmt parseSynchronized() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // 'synchronized'
        consume("(");
        KanvasSyntaxTree.Expr lock = parseExpr();
        consume(")");
        return new KanvasSyntaxTree.SynchronizedStmt(pos, lock, parseBlock());
    }

    private KanvasSyntaxTree.Stmt parseVarDecl() throws ParseException {
        KanvasSyntaxTree.VarDeclStmt s = parseVarDeclNoSemi();
        consume(";");
        return s;
    }

    private KanvasSyntaxTree.VarDeclStmt parseVarDeclNoSemi() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        List<String> mods = new ArrayList<>();
        if (!atEnd() && peek().is("final")) { mods.add("final"); advance(); }
        String type = parseTypeName();
        String name = peek().text; advance();
        KanvasSyntaxTree.Expr init = null;
        skipComments();
        if (!atEnd() && peek().is("=")) { advance(); init = parseExpr(); }
        return new KanvasSyntaxTree.VarDeclStmt(pos, mods, type, name, init);
    }

    private KanvasSyntaxTree.Stmt parseExprStmt() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        KanvasSyntaxTree.Expr expr = parseExpr();
        consume(";");
        return new KanvasSyntaxTree.ExprStmt(pos, expr);
    }

    /** Reads raw tokens until ';' or an unmatched '}' — used as fallback. */
    private KanvasSyntaxTree.RawStmt parseRawStmt() {
        int start = atEnd() ? source.length() : peek().offset;
        int parenD = 0, braceD = 0;
        while (!atEnd()) {
            Lexer.Token t = peek();
            if (t.is("{")) braceD++;
            else if (t.is("}")) { if (braceD == 0) break; braceD--; }
            else if (t.is("(")) parenD++;
            else if (t.is(")")) parenD--;
            advance();
            if (t.is(";") && parenD == 0 && braceD == 0) break;
        }
        int end = atEnd() ? source.length() : prev().offset + prev().text.length();
        return new KanvasSyntaxTree.RawStmt(new KanvasSyntaxTree.SourcePos(start, 1, 1), source.substring(start, end).trim());
    }

    // =========================================================================
    // Expression parsing  (recursive-descent, standard Java precedence)
    // =========================================================================

    /** Entry point — handles assignment and ternary (lowest precedence). */
    KanvasSyntaxTree.Expr parseExpr() throws ParseException {
        KanvasSyntaxTree.Expr left = parseOr();
        skipComments();
        if (atEnd()) return left;
        String op = peek().text;
        if (isAssignOp(op)) {
            advance();
            return new KanvasSyntaxTree.AssignExpr(left.pos, left, op, parseExpr());
        }
        if (peek().is("?")) {
            advance();
            KanvasSyntaxTree.Expr then = parseExpr();
            consume(":");
            return new KanvasSyntaxTree.TernaryExpr(left.pos, left, then, parseExpr());
        }
        return left;
    }

    private KanvasSyntaxTree.Expr parseOr()  throws ParseException { return parseBinary(this::parseAnd,    "||"); }
    private KanvasSyntaxTree.Expr parseAnd() throws ParseException { return parseBinary(this::parseBitOr,  "&&"); }
    private KanvasSyntaxTree.Expr parseBitOr()  throws ParseException { return parseBinary(this::parseBitXor, "|"); }
    private KanvasSyntaxTree.Expr parseBitXor() throws ParseException { return parseBinary(this::parseBitAnd, "^"); }
    private KanvasSyntaxTree.Expr parseBitAnd() throws ParseException { return parseBinary(this::parseEquality, "&"); }
    private KanvasSyntaxTree.Expr parseEquality()   throws ParseException { return parseBinary(this::parseRelational, "==", "!="); }
    private KanvasSyntaxTree.Expr parseShift()      throws ParseException { return parseBinary(this::parseAdditive, "<<", ">>", ">>>"); }
    private KanvasSyntaxTree.Expr parseAdditive()   throws ParseException { return parseBinary(this::parseMultiplicative, "+", "-"); }
    private KanvasSyntaxTree.Expr parseMultiplicative() throws ParseException { return parseBinary(this::parseUnary, "*", "/", "%"); }

    private KanvasSyntaxTree.Expr parseRelational() throws ParseException {
        KanvasSyntaxTree.Expr left = parseShift();
        skipComments();
        while (!atEnd()) {
            String op = peek().text;
            if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
                KanvasSyntaxTree.SourcePos pos = sp(); advance();
                left = new KanvasSyntaxTree.BinaryExpr(pos, left, op, parseShift());
            } else if (peek().is("instanceof")) {
                KanvasSyntaxTree.SourcePos pos = sp(); advance();
                String type = parseTypeName();
                String binding = null;
                skipComments();
                if (!atEnd() && peek().type == Lexer.TokenType.IDENTIFIER) { binding = peek().text; advance(); }
                left = new KanvasSyntaxTree.InstanceofExpr(pos, left, type, binding);
            } else break;
            skipComments();
        }
        return left;
    }

    @FunctionalInterface private interface ExprSupplier { KanvasSyntaxTree.Expr get() throws ParseException; }

    private KanvasSyntaxTree.Expr parseBinary(ExprSupplier next, String... ops) throws ParseException {
        KanvasSyntaxTree.Expr left = next.get();
        skipComments();
        while (!atEnd() && containsOp(ops, peek().text)) {
            KanvasSyntaxTree.SourcePos pos = sp();
            String op = peek().text; advance();
            left = new KanvasSyntaxTree.BinaryExpr(pos, left, op, next.get());
            skipComments();
        }
        return left;
    }

    private KanvasSyntaxTree.Expr parseUnary() throws ParseException {
        skipComments();
        if (atEnd()) throw new ParseException("Unexpected end of expression");
        KanvasSyntaxTree.SourcePos pos = sp();
        String t = peek().text;
        if (t.equals("!") || t.equals("~") || t.equals("-") || t.equals("+") || t.equals("++") || t.equals("--")) {
            advance();
            return new KanvasSyntaxTree.UnaryExpr(pos, t, parseUnary(), false);
        }
        if (peek().is("(")) {
            String cast = tryCast();
            if (cast != null) return new KanvasSyntaxTree.CastExpr(pos, cast, parseUnary());
        }
        return parsePostfix();
    }

    private KanvasSyntaxTree.Expr parsePostfix() throws ParseException {
        KanvasSyntaxTree.Expr expr = parsePrimary();
        while (!atEnd()) {
            skipComments();
            if (atEnd()) break;
            if (peek().is(".")) {
                advance(); skipComments();
                if (atEnd()) break;
                KanvasSyntaxTree.SourcePos pos = sp();
                String name = peek().text; advance();
                skipComments();
                if (!atEnd() && peek().is("(")) {
                    advance();
                    expr = new KanvasSyntaxTree.MethodCallExpr(pos, expr, name, parseArgList());
                } else {
                    expr = new KanvasSyntaxTree.FieldAccessExpr(pos, expr, name);
                }
            } else if (peek().is("(") && expr instanceof KanvasSyntaxTree.NameExpr ne) {
                KanvasSyntaxTree.SourcePos pos = expr.pos; advance();
                expr = new KanvasSyntaxTree.MethodCallExpr(pos, null, ne.name, parseArgList());
            } else if (peek().is("[")) {
                KanvasSyntaxTree.SourcePos pos = sp(); advance();
                KanvasSyntaxTree.Expr idx = parseExpr();
                consume("]");
                expr = new KanvasSyntaxTree.ArrayAccessExpr(pos, expr, idx);
            } else if (peek().is("++") || peek().is("--")) {
                KanvasSyntaxTree.SourcePos pos = sp();
                String op = peek().text; advance();
                expr = new KanvasSyntaxTree.UnaryExpr(pos, op, expr, true);
            } else {
                break;
            }
        }
        return expr;
    }

    private KanvasSyntaxTree.Expr parsePrimary() throws ParseException {
        skipComments();
        if (atEnd()) throw new ParseException("Unexpected end of expression");
        KanvasSyntaxTree.SourcePos pos = sp();
        Lexer.Token t = peek();

        // Literals
        if (t.type == Lexer.TokenType.INT_LITERAL || t.type == Lexer.TokenType.FLOAT_LITERAL ||
            t.type == Lexer.TokenType.STRING_LITERAL || t.type == Lexer.TokenType.CHAR_LITERAL) {
            advance(); return new KanvasSyntaxTree.LiteralExpr(pos, t.text);
        }
        if (t.is("true") || t.is("false") || t.is("null")) {
            advance(); return new KanvasSyntaxTree.LiteralExpr(pos, t.text);
        }

        // new
        if (t.is("new")) return parseNew();

        // Parenthesised expr — checked after trycast in parseUnary, so here it's always paren
        if (t.is("(")) {
            advance();
            KanvasSyntaxTree.Expr inner = parseExpr();
            consume(")");
            // check for lambda
            if (!atEnd() && peek().is("->")) {
                advance();
                List<KanvasSyntaxTree.Param> lparams = exprToParams(inner);
                Object body = peek().is("{") ? parseBlock() : parseExpr();
                return new KanvasSyntaxTree.LambdaExpr(pos, lparams, body);
            }
            return new KanvasSyntaxTree.ParenExpr(pos, inner);
        }

        // Array initialiser {1,2,3}
        if (t.is("{")) return parseArrayInit();

        // Lambda with single untyped param: x -> expr
        if (t.type == Lexer.TokenType.IDENTIFIER && nextSig(index) != null && nextSig(index).is("->")) {
            String name = t.text; advance(); advance(); // name, ->
            List<KanvasSyntaxTree.Param> lp = List.of(new KanvasSyntaxTree.Param(pos, "", name, false));
            Object body = peek().is("{") ? parseBlock() : parseExpr();
            return new KanvasSyntaxTree.LambdaExpr(pos, lp, body);
        }

        // Name / keyword (this, super, identifiers)
        if (t.type == Lexer.TokenType.IDENTIFIER || t.type == Lexer.TokenType.KEYWORD) {
            advance(); return new KanvasSyntaxTree.NameExpr(pos, t.text);
        }

        // Fallback: consume one token as raw
        advance(); return new KanvasSyntaxTree.RawExpr(pos, t.text);
    }

    private KanvasSyntaxTree.Expr parseNew() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // 'new'
        String type = parseTypeName();
        skipComments();
        if (!atEnd() && peek().is("[")) {
            // Array
            List<KanvasSyntaxTree.Expr> dims = new ArrayList<>();
            while (!atEnd() && peek().is("[")) {
                advance();
                dims.add(peek().is("]") ? new KanvasSyntaxTree.RawExpr(pos, "") : parseExpr());
                consume("]");
            }
            // optional initialiser
            if (!atEnd() && peek().is("{")) {
                KanvasSyntaxTree.ArrayInitExpr init = (KanvasSyntaxTree.ArrayInitExpr) parseArrayInit();
                return new KanvasSyntaxTree.RawExpr(pos, "new " + type + "[]" + rawOf(init));
            }
            return new KanvasSyntaxTree.NewArrayExpr(pos, type, dims);
        }
        consume("(");
        List<KanvasSyntaxTree.Expr> args = parseArgList();
        // skip anonymous class body if present
        if (!atEnd() && peek().is("{")) { int d=1; advance(); while (!atEnd()&&d>0){if(peek().is("{"))d++;else if(peek().is("}"))d--;advance();} }
        return new KanvasSyntaxTree.NewObjectExpr(pos, type, args);
    }

    private KanvasSyntaxTree.Expr parseArrayInit() throws ParseException {
        KanvasSyntaxTree.SourcePos pos = sp();
        advance(); // '{'
        List<KanvasSyntaxTree.Expr> elems = new ArrayList<>();
        while (!atEnd() && !peek().is("}")) {
            skipComments();
            if (peek().is("}")) break;
            elems.add(peek().is("{") ? parseArrayInit() : parseExpr());
            skipComments();
            if (!atEnd() && peek().is(",")) advance();
        }
        if (!atEnd()) advance(); // '}'
        return new KanvasSyntaxTree.ArrayInitExpr(pos, elems);
    }

    /** Parses a comma-separated argument list, consuming the closing ')'. */
    private List<KanvasSyntaxTree.Expr> parseArgList() throws ParseException {
        List<KanvasSyntaxTree.Expr> args = new ArrayList<>();
        skipComments();
        if (!atEnd() && peek().is(")")) { advance(); return args; }
        while (!atEnd()) {
            skipComments();
            if (peek().is(")")) { advance(); break; }
            args.add(parseExpr());
            skipComments();
            if (!atEnd() && peek().is(",")) { advance(); continue; }
            if (!atEnd() && peek().is(")")) { advance(); break; }
        }
        return args;
    }

    // =========================================================================
    // Expression-parsing helpers
    // =========================================================================

    /**
     * Attempts to parse a cast expression starting at '('.
     * Returns the type string if it looks like a cast, or null otherwise.
     * Does NOT advance the index on failure.
     */
    private String tryCast() {
        int saved = index;
        try {
            advance(); // consume '('
            if (atEnd()) { index = saved; return null; }
            // Must start with a type token
            if (peek().type != Lexer.TokenType.IDENTIFIER && !PRIMITIVE_TYPES.contains(peek().text)) { index = saved; return null; }
            StringBuilder type = new StringBuilder();
            type.append(peek().text); advance();
            // generic suffix
            if (!atEnd() && peek().is("<")) { int d=1; type.append("<"); advance(); while(!atEnd()&&d>0){if(peek().is("<"))d++;else if(peek().is(">"))d--;type.append(peek().text);advance();}  }
            // array suffix
            while (!atEnd() && peek().is("[")) { type.append("["); advance(); if(!atEnd()&&peek().is("]")){type.append("]");advance();} }
            if (atEnd() || !peek().is(")")) { index = saved; return null; }
            advance(); // consume ')'
            // Must be followed by an expression-starting token, not an operator
            if (atEnd() || isBinaryOpStart(peek().text)) { index = saved; return null; }
            return type.toString();
        } catch (Exception e) { index = saved; return null; }
    }

    /**
     * Best-effort: convert a parenthesised expression back to lambda params.
     * Used when we see {@code (expr) ->}.
     */
    private List<KanvasSyntaxTree.Param> exprToParams(KanvasSyntaxTree.Expr expr) {
        List<KanvasSyntaxTree.Param> p = new ArrayList<>();
        if (expr instanceof KanvasSyntaxTree.NameExpr ne)
            p.add(new KanvasSyntaxTree.Param(ne.pos, "", ne.name, false));
        // more complex cases fall back to RawExpr body — acceptable
        return p;
    }

    /** Parses a type name including generics and array brackets. */
    private String parseTypeName() throws ParseException {
        if (atEnd() || (peek().type != Lexer.TokenType.IDENTIFIER && !PRIMITIVE_TYPES.contains(peek().text)))
            throw new ParseException("Expected type name at " + peek().line + ":" + peek().column);
        StringBuilder sb = new StringBuilder(peek().text); advance();
        // dotted name: java.util.ArrayList
        while (!atEnd() && peek().is(".") && nextSig(index) != null && nextSig(index).type == Lexer.TokenType.IDENTIFIER) {
            sb.append("."); advance(); sb.append(peek().text); advance();
        }
        // generic args
        if (!atEnd() && peek().is("<")) {
            int d = 1; sb.append("<"); advance();
            while (!atEnd() && d > 0) {
                if (peek().is("<")) d++; else if (peek().is(">")) d--;
                else if (peek().is(">>")) { d -= 2; sb.append(">"); } // handle >> as two >
                sb.append(peek().text); advance();
            }
        }
        // array brackets
        while (!atEnd() && peek().is("[")) { sb.append("["); advance(); if(!atEnd()&&peek().is("]")){sb.append("]");advance();} }
        return sb.toString();
    }

    /** Lookahead: does the current position look like the start of a local var decl? */
    private boolean looksLikeVarDecl() {
        int saved = index;
        try {
            if (atEnd()) return false;
            if (peek().is("final")) advance();
            if (atEnd()) return false;
            Lexer.Token first = peek();
            if (first.type != Lexer.TokenType.IDENTIFIER && !PRIMITIVE_TYPES.contains(first.text)) return false;
            advance();
            // generic suffix
            if (!atEnd() && peek().is("<")) { int d=1; advance(); while(!atEnd()&&d>0){if(peek().is("<"))d++;else if(peek().is(">"))d--;advance();} }
            // array brackets
            while (!atEnd() && peek().is("[")) { advance(); if(!atEnd()&&peek().is("]")) advance(); }
            // next must be an identifier (the variable name)
            return !atEnd() && peek().type == Lexer.TokenType.IDENTIFIER;
        } finally { index = saved; }
    }

    /**
     * Lookahead inside a for '(' to detect whether this is a for-each loop.
     * Looks for the ':' separator that distinguishes for-each from traditional for.
     */
    private boolean looksLikeForEach() {
        int saved = index;
        try {
            int depth = 0;
            while (!atEnd()) {
                String t = peek().text;
                if (t.equals("(")) depth++;
                else if (t.equals(")")) { if (depth == 0) break; depth--; }
                else if (t.equals(":") && depth == 0) return true;
                else if (t.equals(";") && depth == 0) return false;
                advance();
            }
            return false;
        } finally { index = saved; }
    }

    /** Retrieve the optional label after break/continue. */
    private String labelOpt() {
        skipComments();
        if (!atEnd() && peek().type == Lexer.TokenType.IDENTIFIER) { String l = peek().text; advance(); return l; }
        return null;
    }

    private void consume(String text) throws ParseException {
        skipComments();
        if (atEnd() || !peek().is(text))
            throw new ParseException("Expected '" + text + "' at " + (atEnd() ? "EOF" : peek().line + ":" + peek().column));
        advance();
    }

    private static boolean isAssignOp(String op) {
        return switch (op) {
            case "=","+=","-=","*=","/=","%=","&=","|=","^=","<<=",">>=",">>>=" -> true;
            default -> false;
        };
    }

    private static boolean isBinaryOpStart(String op) {
        return switch (op) {
            case "+","-","*","/","%","&","|","^","&&","||","==","!=","<",">","<=",">=","<<",">>",">>>" -> true;
            default -> false;
        };
    }

    private static boolean containsOp(String[] ops, String op) {
        for (String o : ops) if (o.equals(op)) return true;
        return false;
    }

    private KanvasSyntaxTree.SourcePos sp() {
        return atEnd() ? new KanvasSyntaxTree.SourcePos(source.length(), 0, 0)
                       : new KanvasSyntaxTree.SourcePos(peek().offset, peek().line, peek().column);
    }

    private String rawOf(KanvasSyntaxTree.ArrayInitExpr e) {
        return "{" + e.elements.stream().map(el -> el instanceof KanvasSyntaxTree.RawExpr r ? r.raw : "?").reduce("", (a,b)->a.isEmpty()?b:a+","+b) + "}";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void readUntilSemicolon() throws ParseException {
        while (!atEnd()) {
            if (peek().is(";")) { advance(); return; }
            advance();
        }
        throw new ParseException("Expected ';' at EOF");
    }

    private void consumeBalanced(String open, String close, Lexer.Token start) throws ParseException {
        int depth = 1;
        while (!atEnd()) {
            Lexer.Token t = peek();
            if (t.is(open))  depth++;
            else if (t.is(close)) { depth--; if (depth == 0) { advance(); return; } }
            advance();
        }
        throw new ParseException("Expected '" + close + "' to match '" + open + "' at " + start.line + ":" + start.column);
    }

    private boolean isModifier(Lexer.Token tok) {
        return tok.type == Lexer.TokenType.KEYWORD && MODIFIERS.contains(tok.text);
    }

    private boolean isNameToken(Lexer.Token tok) {
        return tok.type == Lexer.TokenType.IDENTIFIER || tok.type == Lexer.TokenType.KEYWORD;
    }

    private boolean needsSpace(Lexer.Token a, Lexer.Token b) {
        return (a.type == Lexer.TokenType.IDENTIFIER || a.type == Lexer.TokenType.KEYWORD)
            && (b.type == Lexer.TokenType.IDENTIFIER || b.type == Lexer.TokenType.KEYWORD);
    }

    private Lexer.Token prevSig(int fromIndex) {
        for (int i = fromIndex - 1; i >= 0; i--) {
            Lexer.Token t = tokens.get(i);
            if (t.type != Lexer.TokenType.LINE_COMMENT && t.type != Lexer.TokenType.BLOCK_COMMENT) return t;
        }
        return null;
    }

    private Lexer.Token nextSig(int fromIndex) {
        for (int i = fromIndex + 1; i < tokens.size(); i++) {
            Lexer.Token t = tokens.get(i);
            if (t.type != Lexer.TokenType.LINE_COMMENT && t.type != Lexer.TokenType.BLOCK_COMMENT) return t;
        }
        return null;
    }

    private void skipComments() {
        while (!atEnd() && (peek().type == Lexer.TokenType.LINE_COMMENT || peek().type == Lexer.TokenType.BLOCK_COMMENT))
            advance();
    }

    private boolean atEnd() { return index >= tokens.size() || tokens.get(index).type == Lexer.TokenType.EOF; }
    private Lexer.Token peek() { return tokens.get(index); }
    private Lexer.Token prev() { return tokens.get(index - 1); }
    private void advance() { if (!atEnd()) index++; }

    private KanvasSyntaxTree.SourcePos posOf(int tokenIndex) {
        if (tokenIndex < tokens.size()) {
            Lexer.Token t = tokens.get(tokenIndex);
            return new KanvasSyntaxTree.SourcePos(t.offset, t.line, t.column);
        }
        return new KanvasSyntaxTree.SourcePos(0, 1, 1);
    }
}
