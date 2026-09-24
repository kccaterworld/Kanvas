package kanvas.preprocess.parse;

import kanvas.preprocess.ast.KanvasSyntaxTree.*;
import kanvas.preprocess.lex.Lexer;
import kanvas.preprocess.lex.LexerException;
import kanvas.preprocess.lex.Token;
import kanvas.preprocess.lex.TokenType;
import kanvas.preprocess.source.SourcePos;
import kanvas.preprocess.source.SourceSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recursive-descent parser for .kvs files.
 *
 * <p>A .kvs file is an optional package declaration, imports, and the members of a class body:
 * fields, methods, initializer blocks and nested type declarations. Everything inside those
 * members is ordinary Java (up to Java 21). Anything the parser does not understand is reported
 * as a {@link ParseException} with its source position; nothing is passed through as raw text.
 */
public class Parser {

    private static final Set<String> PRIMITIVES = Set.of(
        "boolean", "byte", "char", "short", "int", "long", "float", "double");
    private static final Set<String> MODIFIER_KEYWORDS = Set.of(
        "public", "protected", "private", "static", "abstract", "final", "native",
        "synchronized", "transient", "volatile", "strictfp", "default");
    private static final Set<String> ASSIGN_OPS = Set.of(
        "=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=");
    private static final Set<String> PREFIX_OPS = Set.of("+", "-", "++", "--", "!", "~");
    private static final Map<String, Integer> BINARY_PRECEDENCE = Map.ofEntries(
        Map.entry("||", 1), Map.entry("&&", 2), Map.entry("|", 3), Map.entry("^", 4), Map.entry("&", 5),
        Map.entry("==", 6), Map.entry("!=", 6),
        Map.entry("<", 7), Map.entry(">", 7), Map.entry("<=", 7), Map.entry(">=", 7), Map.entry("instanceof", 7),
        Map.entry("<<", 8), Map.entry(">>", 8), Map.entry(">>>", 8),
        Map.entry("+", 9), Map.entry("-", 9),
        Map.entry("*", 10), Map.entry("/", 10), Map.entry("%", 10));

    /** Tokens with comments removed; always ends with EOF. */
    private final List<Token> tokens = new ArrayList<>();
    private int index;

    public Parser(String source) throws LexerException {
        for (Token t : new Lexer(source).tokenize())
            if (!t.isComment()) tokens.add(t);
    }

    public CompilationUnit parse() throws ParseException {
        SourcePos start = start();
        int afterAnnotations = scanAnnotations(index);
        PackageDecl packageDecl = afterAnnotations >= 0 && tok(afterAnnotations).is("package") ? parsePackage() : null;

        List<ImportDecl> imports = new ArrayList<>();
        List<Member> members = new ArrayList<>();
        while (!atEnd()) {
            if (at("import")) imports.add(parseImport());
            else if (at("package")) throw failAt("The package declaration must come first in the file", peek().start());
            else addIfPresent(members, parseMember(null, false, true));
        }
        SourceSpan span = index == 0 ? new SourceSpan(start, start) : span(start);
        return new CompilationUnit(span, packageDecl, imports, members);
    }

    // =========================================================================
    // Package and imports
    // =========================================================================

    private PackageDecl parsePackage() throws ParseException {
        SourcePos s = start();
        List<Annotation> annotations = parseAnnotations();
        expect("package");
        String name = parseQualifiedName();
        expect(";");
        return new PackageDecl(span(s), annotations, name);
    }

    private ImportDecl parseImport() throws ParseException {
        SourcePos s = start();
        expect("import");
        boolean isStatic = accept("static");
        String name = parseQualifiedName();
        boolean onDemand = false;
        if (accept(".")) { expect("*"); onDemand = true; }
        expect(";");
        return new ImportDecl(span(s), isStatic, name, onDemand);
    }

    // =========================================================================
    // Members
    // =========================================================================

    /**
     * Parses one member of a class body, or of the .kvs file itself when {@code topLevel}.
     * Returns null for a stray ';'.
     */
    private Member parseMember(String enclosingName, boolean inRecord, boolean topLevel) throws ParseException {
        SourcePos s = start();
        if (accept(";")) return null;
        if (at("{")) {
            BlockStmt body = parseBlock();
            return new InitializerDecl(span(s), false, body);
        }
        if (at("static") && peek(1).is("{")) {
            next();
            BlockStmt body = parseBlock();
            return new InitializerDecl(span(s), true, body);
        }
        if (topLevel && isIdent(peek()) && topLevelStatementAhead()) {
            int afterParens = peek(1).is("(") ? scanParens(index + 1) : -1;
            if (afterParens > 0 && (tok(afterParens).is("{") || tok(afterParens).is("throws")))
                throw failAt("A .kvs file cannot declare constructors; initialize things in setup() instead", s);
            throw failAt("Statements are not allowed at the top level of a .kvs file; move them into setup() or draw()", s);
        }

        Modifiers modifiers = parseModifiers();
        TypeDecl typeDecl = parseTypeDeclIfPresent(s, modifiers);
        if (typeDecl != null) return typeDecl;

        List<TypeParam> typeParams = at("<") ? parseTypeParams() : List.of();
        if (isIdent(peek()) && peek(1).is("(")) {
            if (topLevel) throw failAt("A .kvs file cannot declare constructors; initialize things in setup() instead", s);
            String name = next().text();
            List<Param> params = parseParams();
            List<TypeRef> throwsTypes = parseThrows();
            BlockStmt body = parseBlock();
            return new ConstructorDecl(span(s), modifiers, typeParams, name, params, throwsTypes, body);
        }
        if (inRecord && peek().is(enclosingName) && peek(1).is("{")) {
            String name = next().text();
            BlockStmt body = parseBlock();
            return new ConstructorDecl(span(s), modifiers, typeParams, name, null, List.of(), body);
        }

        TypeRef type = parseType(true);
        SourcePos nameStart = start();
        String name = expectIdent("a field or method name");
        if (at("(")) {
            List<Param> params = parseParams();
            int extraDims = parseDims();
            List<TypeRef> throwsTypes = parseThrows();
            ElementValue defaultValue = accept("default") ? parseElementValue() : null;
            BlockStmt body = null;
            if (at("{")) body = parseBlock();
            else expect(";");
            return new MethodDecl(span(s), modifiers, typeParams, type, name, params, extraDims, throwsTypes, body, defaultValue);
        }
        if (!typeParams.isEmpty()) throw failAt("Only methods and constructors can declare type parameters", s);
        List<VarDeclarator> declarators = parseDeclarators(nameStart, name);
        expect(";");
        return new FieldDecl(span(s), modifiers, type, declarators);
    }

    /** At the top level, "name(", "name =", "name++" and "name[i]" can only be the start of a statement. */
    private boolean topLevelStatementAhead() {
        Token next = peek(1);
        return next.is("(") || next.is("++") || next.is("--")
            || (next.is(TokenType.OPERATOR) && ASSIGN_OPS.contains(next.text()))
            || (next.is("[") && !peek(2).is("]"));
    }

    /** Parses a class, interface, enum, record or annotation type if one starts here; otherwise returns null. */
    private TypeDecl parseTypeDeclIfPresent(SourcePos s, Modifiers modifiers) throws ParseException {
        if (accept("class")) {
            String name = expectIdent("a class name");
            List<TypeParam> typeParams = at("<") ? parseTypeParams() : List.of();
            TypeRef superclass = accept("extends") ? parseType(true) : null;
            List<TypeRef> interfaces = accept("implements") ? parseTypeList() : List.of();
            List<TypeRef> permits = acceptContextual("permits") ? parseTypeList() : List.of();
            List<Member> body = parseClassBody(name, false);
            return new ClassDecl(span(s), modifiers, name, typeParams, superclass, interfaces, permits, body);
        }
        if (accept("interface")) {
            String name = expectIdent("an interface name");
            List<TypeParam> typeParams = at("<") ? parseTypeParams() : List.of();
            List<TypeRef> superinterfaces = accept("extends") ? parseTypeList() : List.of();
            List<TypeRef> permits = acceptContextual("permits") ? parseTypeList() : List.of();
            List<Member> body = parseClassBody(name, false);
            return new InterfaceDecl(span(s), modifiers, name, typeParams, superinterfaces, permits, body);
        }
        if (accept("enum")) return parseEnumRest(s, modifiers);
        if (at("@") && peek(1).is("interface")) {
            next();
            next();
            String name = expectIdent("an annotation type name");
            List<Member> body = parseClassBody(name, false);
            return new AnnotationTypeDecl(span(s), modifiers, name, body);
        }
        if (recordDeclAhead()) {
            next(); // record
            String name = expectIdent("a record name");
            List<TypeParam> typeParams = at("<") ? parseTypeParams() : List.of();
            List<Param> components = parseParams();
            List<TypeRef> interfaces = accept("implements") ? parseTypeList() : List.of();
            List<Member> body = parseClassBody(name, true);
            return new RecordDecl(span(s), modifiers, name, typeParams, components, interfaces, body);
        }
        return null;
    }

    private boolean recordDeclAhead() {
        return peek().is("record") && isIdent(peek()) && isIdent(peek(1)) && (peek(2).is("(") || peek(2).is("<"));
    }

    private EnumDecl parseEnumRest(SourcePos s, Modifiers modifiers) throws ParseException {
        String name = expectIdent("an enum name");
        List<TypeRef> interfaces = accept("implements") ? parseTypeList() : List.of();
        expect("{");
        List<EnumConstant> constants = new ArrayList<>();
        while (!at(";") && before("}")) {
            SourcePos cs = start();
            List<Annotation> annotations = parseAnnotations();
            String constantName = expectIdent("an enum constant");
            List<Expr> args = at("(") ? parseArgs() : null;
            List<Member> body = at("{") ? parseClassBody(constantName, false) : null;
            constants.add(new EnumConstant(span(cs), annotations, constantName, args, body));
            if (!accept(",")) break;
        }
        List<Member> members = new ArrayList<>();
        if (accept(";")) {
            while (before("}")) addIfPresent(members, parseMember(name, false, false));
        }
        expect("}");
        return new EnumDecl(span(s), modifiers, name, interfaces, constants, members);
    }

    private List<Member> parseClassBody(String className, boolean inRecord) throws ParseException {
        expect("{");
        List<Member> members = new ArrayList<>();
        while (before("}")) addIfPresent(members, parseMember(className, inRecord, false));
        expect("}");
        return members;
    }

    private List<TypeParam> parseTypeParams() throws ParseException {
        expect("<");
        List<TypeParam> params = new ArrayList<>();
        do {
            SourcePos s = start();
            List<Annotation> annotations = parseAnnotations();
            String name = expectIdent("a type parameter name");
            List<TypeRef> bounds = new ArrayList<>();
            if (accept("extends")) {
                do bounds.add(parseType(true)); while (accept("&"));
            }
            params.add(new TypeParam(span(s), annotations, name, bounds));
        } while (accept(","));
        expect(">");
        return params;
    }

    private List<Param> parseParams() throws ParseException {
        expect("(");
        List<Param> params = new ArrayList<>();
        if (!at(")")) {
            do params.add(parseFormalParam()); while (accept(","));
        }
        expect(")");
        return params;
    }

    private Param parseFormalParam() throws ParseException {
        SourcePos s = start();
        Modifiers modifiers = parseModifiers();
        TypeRef type = parseType(true);
        boolean varargs = accept("...");
        String name = at("this") ? next().text() : expectIdent("a parameter name"); // "this" is a receiver parameter
        int extraDims = parseDims();
        return new Param(span(s), modifiers, type, varargs, name, extraDims);
    }

    private List<TypeRef> parseThrows() throws ParseException {
        return accept("throws") ? parseTypeList() : List.of();
    }

    private List<TypeRef> parseTypeList() throws ParseException {
        List<TypeRef> types = new ArrayList<>();
        do types.add(parseType(true)); while (accept(","));
        return types;
    }

    /** Parses the declarators of a field or local variable, the first name having been read already. */
    private List<VarDeclarator> parseDeclarators(SourcePos firstStart, String firstName) throws ParseException {
        List<VarDeclarator> declarators = new ArrayList<>();
        declarators.add(parseDeclaratorRest(firstStart, firstName));
        while (accept(",")) {
            SourcePos s = start();
            String name = expectIdent("a variable name");
            declarators.add(parseDeclaratorRest(s, name));
        }
        return declarators;
    }

    private VarDeclarator parseDeclaratorRest(SourcePos s, String name) throws ParseException {
        int extraDims = parseDims();
        Expr init = accept("=") ? parseVarInit() : null;
        return new VarDeclarator(span(s), name, extraDims, init);
    }

    private Expr parseVarInit() throws ParseException {
        return at("{") ? parseArrayInit() : parseExpr();
    }

    // =========================================================================
    // Modifiers and annotations
    // =========================================================================

    private Modifiers parseModifiers() throws ParseException {
        List<Modifier> items = new ArrayList<>();
        while (true) {
            Token t = peek();
            SourcePos s = t.start();
            if (t.is("@") && !peek(1).is("interface")) {
                items.add(parseAnnotation());
            } else if (t.is(TokenType.KEYWORD) && MODIFIER_KEYWORDS.contains(t.text())) {
                next();
                items.add(new KeywordModifier(span(s), t.text()));
            } else if (isIdent(t) && t.is("sealed") && modifierFollows(peek(1))) {
                next();
                items.add(new KeywordModifier(span(s), "sealed"));
            } else if (isIdent(t) && t.is("non") && peek(1).is("-") && peek(2).is("sealed")
                       && adjacent(t, peek(1)) && adjacent(peek(1), peek(2))) {
                next(); next(); next();
                items.add(new KeywordModifier(span(s), "non-sealed"));
            } else {
                break;
            }
        }
        return items.isEmpty() ? Modifiers.NONE : new Modifiers(items);
    }

    /** Whether {@code t} can follow a contextual modifier such as {@code sealed}. */
    private boolean modifierFollows(Token t) {
        return t.is("class") || t.is("interface") || t.is("@") || t.is("sealed") || t.is("non")
            || (t.is(TokenType.KEYWORD) && MODIFIER_KEYWORDS.contains(t.text()));
    }

    private List<Annotation> parseAnnotations() throws ParseException {
        List<Annotation> annotations = new ArrayList<>();
        while (at("@") && !peek(1).is("interface")) annotations.add(parseAnnotation());
        return annotations;
    }

    private Annotation parseAnnotation() throws ParseException {
        SourcePos s = start();
        expect("@");
        String name = parseQualifiedName();
        List<ElementPair> args = null;
        if (accept("(")) {
            args = new ArrayList<>();
            if (!at(")")) {
                if (isIdent(peek()) && peek(1).is("=")) {
                    do {
                        SourcePos ps = start();
                        String key = expectIdent("an annotation element name");
                        expect("=");
                        ElementValue value = parseElementValue();
                        args.add(new ElementPair(span(ps), key, value));
                    } while (accept(","));
                } else {
                    SourcePos ps = start();
                    ElementValue value = parseElementValue();
                    args.add(new ElementPair(span(ps), null, value));
                }
            }
            expect(")");
        }
        return new Annotation(span(s), name, args);
    }

    private ElementValue parseElementValue() throws ParseException {
        if (at("@")) return parseAnnotation();
        if (at("{")) {
            SourcePos s = start();
            next();
            List<ElementValue> values = new ArrayList<>();
            while (before("}")) {
                values.add(parseElementValue());
                if (!accept(",")) break;
            }
            expect("}");
            return new ElementArray(span(s), values);
        }
        return parseTernary();
    }

    private String parseQualifiedName() throws ParseException {
        StringBuilder name = new StringBuilder(expectIdent("a name"));
        while (at(".") && isIdent(peek(1))) {
            next();
            name.append('.').append(next().text());
        }
        return name.toString();
    }

    // =========================================================================
    // Types
    // =========================================================================

    /** Parses a type; {@code withDims} also consumes trailing {@code []} pairs. */
    private TypeRef parseType(boolean withDims) throws ParseException {
        SourcePos s = start();
        List<Annotation> annotations = parseAnnotations();
        return parseTypeRest(s, annotations, withDims);
    }

    private TypeRef parseTypeRest(SourcePos s, List<Annotation> annotations, boolean withDims) throws ParseException {
        List<TypeName> names = new ArrayList<>();
        if (isPrimitiveOrVoid(peek())) {
            names.add(new TypeName(List.of(), next().text(), null));
        } else {
            names.add(parseTypeName(List.of()));
            while (at(".") && (isIdent(peek(1)) || peek(1).is("@"))) {
                next();
                List<Annotation> segmentAnnotations = parseAnnotations();
                names.add(parseTypeName(segmentAnnotations));
            }
        }
        int dims = withDims ? parseDims() : 0;
        return new TypeRef(span(s), annotations, names, dims);
    }

    private TypeName parseTypeName(List<Annotation> annotations) throws ParseException {
        String name = expectIdent("a type");
        List<TypeArg> typeArgs = at("<") ? parseTypeArgs() : null;
        return new TypeName(annotations, name, typeArgs);
    }

    /** Parses {@code <...>}; returns an empty list for the diamond {@code <>}. */
    private List<TypeArg> parseTypeArgs() throws ParseException {
        expect("<");
        List<TypeArg> args = new ArrayList<>();
        if (accept(">")) return args;
        do args.add(parseTypeArg()); while (accept(","));
        expect(">");
        return args;
    }

    private TypeArg parseTypeArg() throws ParseException {
        SourcePos s = start();
        List<Annotation> annotations = parseAnnotations();
        if (!accept("?")) return parseTypeRest(s, annotations, true);
        WildcardType.Bound bound = WildcardType.Bound.NONE;
        TypeRef boundType = null;
        if (accept("extends")) { bound = WildcardType.Bound.EXTENDS; boundType = parseType(true); }
        else if (accept("super")) { bound = WildcardType.Bound.SUPER; boundType = parseType(true); }
        return new WildcardType(span(s), annotations, bound, boundType);
    }

    private int parseDims() {
        int dims = 0;
        while (at("[") && peek(1).is("]")) { next(); next(); dims++; }
        return dims;
    }

    // =========================================================================
    // Statements
    // =========================================================================

    private BlockStmt parseBlock() throws ParseException {
        SourcePos s = start();
        expect("{");
        List<Stmt> stmts = new ArrayList<>();
        while (before("}")) stmts.add(parseStatement());
        expect("}");
        return new BlockStmt(span(s), stmts);
    }

    private Stmt parseStatement() throws ParseException {
        SourcePos s = start();
        Token t = peek();
        if (isIdent(t)) {
            if (t.is("yield") && yieldStatementAhead()) {
                next();
                Expr value = parseExpr();
                expect(";");
                return new YieldStmt(span(s), value);
            }
            if (peek(1).is(":")) {
                next();
                next();
                Stmt body = parseStatement();
                return new LabeledStmt(span(s), t.text(), body);
            }
            if (recordDeclAhead()) return parseLocalDeclaration(s);
        } else {
            switch (t.text()) {
                case "{" -> { return parseBlock(); }
                case ";" -> { next(); return new EmptyStmt(span(s)); }
                case "if" -> {
                    next();
                    Expr cond = parseParenExpr();
                    Stmt thenStmt = parseStatement();
                    Stmt elseStmt = accept("else") ? parseStatement() : null;
                    return new IfStmt(span(s), cond, thenStmt, elseStmt);
                }
                case "while" -> {
                    next();
                    Expr cond = parseParenExpr();
                    Stmt body = parseStatement();
                    return new WhileStmt(span(s), cond, body);
                }
                case "do" -> {
                    next();
                    Stmt body = parseStatement();
                    expect("while");
                    Expr cond = parseParenExpr();
                    expect(";");
                    return new DoWhileStmt(span(s), body, cond);
                }
                case "for" -> { return parseFor(s); }
                case "return" -> {
                    next();
                    Expr value = at(";") ? null : parseExpr();
                    expect(";");
                    return new ReturnStmt(span(s), value);
                }
                case "break", "continue" -> {
                    next();
                    String label = isIdent(peek()) ? next().text() : null;
                    expect(";");
                    return t.is("break") ? new BreakStmt(span(s), label) : new ContinueStmt(span(s), label);
                }
                case "throw" -> {
                    next();
                    Expr e = parseExpr();
                    expect(";");
                    return new ThrowStmt(span(s), e);
                }
                case "try" -> { return parseTry(s); }
                case "switch" -> {
                    next();
                    Expr selector = parseParenExpr();
                    List<SwitchCase> cases = parseSwitchBody();
                    return new SwitchStmt(span(s), selector, cases);
                }
                case "synchronized" -> {
                    next();
                    Expr lock = parseParenExpr();
                    BlockStmt body = parseBlock();
                    return new SynchronizedStmt(span(s), lock, body);
                }
                case "assert" -> {
                    next();
                    Expr cond = parseExpr();
                    Expr message = accept(":") ? parseExpr() : null;
                    expect(";");
                    return new AssertStmt(span(s), cond, message);
                }
                case "final", "abstract", "static", "strictfp", "@", "class", "interface", "enum" -> {
                    return parseLocalDeclaration(s);
                }
                default -> { }
            }
        }
        if (localVarDeclAhead()) return parseLocalDeclaration(s);
        Expr e = parseExpr();
        expect(";");
        return new ExprStmt(span(s), e);
    }

    /** A local class/record/enum/interface or a local variable declaration, with any modifiers. */
    private Stmt parseLocalDeclaration(SourcePos s) throws ParseException {
        Modifiers modifiers = parseModifiers();
        TypeDecl decl = parseTypeDeclIfPresent(s, modifiers);
        if (decl != null) return new LocalClassStmt(span(s), decl);
        LocalVarStmt declaration = parseLocalVarRest(s, modifiers);
        expect(";");
        return new LocalVarStmt(span(s), modifiers, declaration.type(), declaration.declarators());
    }

    /** Parses {@code Type name [= init], ...} without the trailing ';'. */
    private LocalVarStmt parseLocalVarRest(SourcePos s, Modifiers modifiers) throws ParseException {
        TypeRef type = parseType(true);
        SourcePos nameStart = start();
        String name = expectIdent("a variable name");
        List<VarDeclarator> declarators = parseDeclarators(nameStart, name);
        return new LocalVarStmt(span(s), modifiers, type, declarators);
    }

    private Stmt parseFor(SourcePos s) throws ParseException {
        next(); // for
        expect("(");
        if (forEachAhead()) {
            Modifiers modifiers = parseModifiers();
            TypeRef type = parseType(true);
            String name = expectIdent("a loop variable name");
            expect(":");
            Expr iterable = parseExpr();
            expect(")");
            Stmt body = parseStatement();
            return new ForEachStmt(span(s), modifiers, type, name, iterable, body);
        }
        List<Stmt> init = new ArrayList<>();
        if (!at(";")) {
            SourcePos is = start();
            if (at("final") || at("@") || localVarDeclAhead()) {
                init.add(parseLocalVarRest(is, parseModifiers()));
            } else {
                do {
                    SourcePos es = start();
                    Expr e = parseExpr();
                    init.add(new ExprStmt(span(es), e));
                } while (accept(","));
            }
        }
        expect(";");
        Expr cond = at(";") ? null : parseExpr();
        expect(";");
        List<Expr> update = new ArrayList<>();
        if (!at(")")) {
            do update.add(parseExpr()); while (accept(","));
        }
        expect(")");
        Stmt body = parseStatement();
        return new ForStmt(span(s), init, cond, update, body);
    }

    private Stmt parseTry(SourcePos s) throws ParseException {
        next(); // try
        List<Resource> resources = new ArrayList<>();
        if (accept("(")) {
            while (before(")")) {
                resources.add(parseResource());
                if (!accept(";")) break;
            }
            expect(")");
        }
        BlockStmt body = parseBlock();
        List<CatchClause> catches = new ArrayList<>();
        while (at("catch")) {
            SourcePos cs = start();
            next();
            expect("(");
            Modifiers modifiers = parseModifiers();
            List<TypeRef> types = new ArrayList<>();
            do types.add(parseType(true)); while (accept("|"));
            String name = expectIdent("an exception variable name");
            expect(")");
            BlockStmt catchBody = parseBlock();
            catches.add(new CatchClause(span(cs), modifiers, types, name, catchBody));
        }
        BlockStmt finallyBlock = accept("finally") ? parseBlock() : null;
        if (resources.isEmpty() && catches.isEmpty() && finallyBlock == null)
            throw fail("Expected 'catch' or 'finally' after the try block");
        return new TryStmt(span(s), resources, body, catches, finallyBlock);
    }

    private Resource parseResource() throws ParseException {
        SourcePos s = start();
        if (at("final") || at("@") || localVarDeclAhead()) {
            Modifiers modifiers = parseModifiers();
            TypeRef type = parseType(true);
            String name = expectIdent("a resource name");
            expect("=");
            Expr init = parseExpr();
            return new ResourceDecl(span(s), modifiers, type, name, init);
        }
        Expr e = parseExpr();
        return new ResourceRef(span(s), e);
    }

    private List<SwitchCase> parseSwitchBody() throws ParseException {
        expect("{");
        List<SwitchCase> cases = new ArrayList<>();
        while (before("}")) cases.add(parseSwitchCase());
        expect("}");
        return cases;
    }

    private SwitchCase parseSwitchCase() throws ParseException {
        SourcePos s = start();
        List<CaseLabel> labels = new ArrayList<>();
        if (accept("default")) {
            labels.add(new DefaultLabel(span(s)));
        } else {
            expect("case");
            do {
                SourcePos ls = start();
                if (accept("default")) labels.add(new DefaultLabel(span(ls)));
                else if (patternAhead()) labels.add(parsePattern());
                else labels.add(parseTernary());
            } while (accept(","));
        }
        Expr guard = null;
        if (isIdent(peek()) && peek().is("when")) {
            next();
            guard = parseTernary();
        }
        List<Stmt> body = new ArrayList<>();
        boolean arrow = accept("->");
        if (arrow) {
            if (at("{") || at("throw")) {
                body.add(parseStatement());
            } else {
                SourcePos es = start();
                Expr e = parseExpr();
                expect(";");
                body.add(new ExprStmt(span(es), e));
            }
        } else {
            expect(":");
            while (!at("case") && !at("default") && before("}")) body.add(parseStatement());
        }
        return new SwitchCase(span(s), labels, guard, arrow, body);
    }

    private Pattern parsePattern() throws ParseException {
        SourcePos s = start();
        Modifiers modifiers = parseModifiers();
        TypeRef type = parseType(true);
        if (at("(")) return parseRecordPatternRest(s, type);
        String name = expectIdent("a pattern variable name");
        return new TypePattern(span(s), modifiers, type, name);
    }

    private RecordPattern parseRecordPatternRest(SourcePos s, TypeRef type) throws ParseException {
        expect("(");
        List<Pattern> components = new ArrayList<>();
        if (!at(")")) {
            do components.add(parsePattern()); while (accept(","));
        }
        expect(")");
        return new RecordPattern(span(s), type, components);
    }

    // =========================================================================
    // Expressions (precedence climbing, lowest precedence first)
    // =========================================================================

    /** Assignment, lambda, or anything of higher precedence. */
    Expr parseExpr() throws ParseException {
        if (lambdaAhead(index)) return parseLambda();
        Expr left = parseTernary();
        String op = peekOperator();
        if (op == null || !ASSIGN_OPS.contains(op)) return left;
        consumeOperator(op);
        Expr value = parseExpr();
        return new AssignExpr(span(left), left, op, value);
    }

    private Expr parseTernary() throws ParseException {
        Expr cond = parseBinary(1);
        if (!accept("?")) return cond;
        Expr thenExpr = parseExpr();
        expect(":");
        Expr elseExpr = lambdaAhead(index) ? parseLambda() : parseTernary();
        return new TernaryExpr(span(cond), cond, thenExpr, elseExpr);
    }

    private Expr parseBinary(int minPrecedence) throws ParseException {
        Expr left = parseUnary();
        while (true) {
            String op = peekOperator();
            Integer precedence = op == null ? null : BINARY_PRECEDENCE.get(op);
            if (precedence == null || precedence < minPrecedence) return left;
            consumeOperator(op);
            if (op.equals("instanceof")) {
                left = parseInstanceofRest(left);
            } else {
                Expr right = parseBinary(precedence + 1);
                left = new BinaryExpr(span(left), left, op, right);
            }
        }
    }

    private Expr parseInstanceofRest(Expr left) throws ParseException {
        SourcePos s = start();
        Modifiers modifiers = parseModifiers();
        TypeRef type = parseType(true);
        if (at("(")) {
            RecordPattern pattern = parseRecordPatternRest(s, type);
            return new InstanceofExpr(span(left), left, null, pattern);
        }
        if (isIdent(peek())) {
            String name = next().text();
            TypePattern pattern = new TypePattern(span(s), modifiers, type, name);
            return new InstanceofExpr(span(left), left, null, pattern);
        }
        if (!modifiers.isEmpty()) throw fail("Expected a pattern variable name");
        return new InstanceofExpr(span(left), left, type, null);
    }

    private Expr parseUnary() throws ParseException {
        SourcePos s = start();
        Token t = peek();
        if (t.is(TokenType.OPERATOR) && PREFIX_OPS.contains(t.text())) {
            next();
            Expr operand = parseUnary();
            return new UnaryExpr(span(s), t.text(), operand, false);
        }
        if (t.is("(") && castAhead()) {
            next();
            List<TypeRef> types = new ArrayList<>();
            do types.add(parseType(true)); while (accept("&"));
            expect(")");
            Expr operand = lambdaAhead(index) ? parseLambda() : parseUnary();
            return new CastExpr(span(s), types, operand);
        }
        return parsePostfix(parsePrimary());
    }

    private Expr parsePostfix(Expr e) throws ParseException {
        SourcePos s = e.span().start();
        while (true) {
            if (accept(".")) {
                if (accept("new")) { e = parseNewRest(s, e); continue; }
                List<TypeArg> typeArgs = at("<") ? parseTypeArgs() : List.of();
                Token name = peek();
                if (!(isIdent(name) || name.is("this") || name.is("super"))) throw fail("Expected a member name");
                next();
                if (at("(")) {
                    List<Expr> args = parseArgs();
                    e = new MethodCallExpr(span(s), e, typeArgs, name.text(), args);
                } else if (!typeArgs.isEmpty()) {
                    throw fail("Expected '('");
                } else {
                    e = new FieldAccessExpr(span(s), e, name.text());
                }
            } else if (accept("[")) {
                Expr idx = parseExpr();
                expect("]");
                e = new ArrayAccessExpr(span(s), e, idx);
            } else if (at("::")) {
                e = parseMethodRefRest(s, e);
            } else if (at("++") || at("--")) {
                String op = next().text();
                e = new UnaryExpr(span(s), op, e, true);
            } else {
                return e;
            }
        }
    }

    private Expr parsePrimary() throws ParseException {
        SourcePos s = start();
        Token t = peek();
        LiteralExpr.Kind literal = switch (t.type()) {
            case INT_LITERAL -> LiteralExpr.Kind.INT;
            case FLOAT_LITERAL -> LiteralExpr.Kind.FLOAT;
            case STRING_LITERAL -> LiteralExpr.Kind.STRING;
            case TEXT_BLOCK -> LiteralExpr.Kind.TEXT_BLOCK;
            case CHAR_LITERAL -> LiteralExpr.Kind.CHAR;
            case KEYWORD -> t.is("true") || t.is("false") ? LiteralExpr.Kind.BOOLEAN : t.is("null") ? LiteralExpr.Kind.NULL : null;
            default -> null;
        };
        if (literal != null) {
            next();
            return new LiteralExpr(span(s), literal, t.text());
        }
        if (accept("(")) {
            Expr inner = parseExpr();
            expect(")");
            return new ParenExpr(span(s), inner);
        }
        if (accept("new")) return parseNewRest(s, null);
        if (accept("switch")) {
            Expr selector = parseParenExpr();
            List<SwitchCase> cases = parseSwitchBody();
            return new SwitchExpr(span(s), selector, cases);
        }
        if (t.is("this") || t.is("super")) {
            next();
            if (!at("(")) return new NameExpr(span(s), t.text());
            List<Expr> args = parseArgs(); // this(...) / super(...) constructor call
            return new MethodCallExpr(span(s), null, List.of(), t.text(), args);
        }
        boolean primitive = isPrimitiveOrVoid(t);
        if (primitive || isIdent(t) || t.is("@")) {
            int typeEnd = scanType(index);
            if (typeEnd > 0 && tok(typeEnd).is(".") && tok(typeEnd + 1).is("class")) {
                TypeRef type = parseType(true);
                expect(".");
                expect("class");
                return new ClassLiteralExpr(span(s), type);
            }
            if (typeEnd > 0 && tok(typeEnd).is("::") && (primitive || hasTypeOnlySyntax(index, typeEnd))) {
                TypeRef type = parseType(true);
                return parseMethodRefRest(s, type);
            }
        }
        if (!isIdent(t)) throw fail("Expected an expression");
        next();
        if (!at("(")) return new NameExpr(span(s), t.text());
        List<Expr> args = parseArgs();
        return new MethodCallExpr(span(s), null, List.of(), t.text(), args);
    }

    /** Parses what follows {@code new}: an object creation (optionally anonymous) or an array creation. */
    private Expr parseNewRest(SourcePos s, Expr outer) throws ParseException {
        List<TypeArg> typeArgs = at("<") ? parseTypeArgs() : List.of();
        TypeRef type = parseType(false);
        if (at("[")) {
            if (outer != null) throw fail("Expected '('");
            List<Expr> dimExprs = new ArrayList<>();
            int extraDims = 0;
            while (accept("[")) {
                if (accept("]")) { extraDims++; continue; }
                if (extraDims > 0) throw fail("Expected ']'");
                dimExprs.add(parseExpr());
                expect("]");
            }
            ArrayInitExpr init = null;
            if (dimExprs.isEmpty()) {
                if (!at("{")) throw fail("Expected an array initializer");
                init = parseArrayInit();
            }
            return new NewArrayExpr(span(s), type, dimExprs, extraDims, init);
        }
        List<Expr> args = parseArgs();
        List<Member> body = at("{") ? parseClassBody(null, false) : null;
        return new NewObjectExpr(span(s), outer, typeArgs, type, args, body);
    }

    private ArrayInitExpr parseArrayInit() throws ParseException {
        SourcePos s = start();
        expect("{");
        List<Expr> elements = new ArrayList<>();
        while (before("}")) {
            elements.add(parseVarInit());
            if (!accept(",")) break;
        }
        expect("}");
        return new ArrayInitExpr(span(s), elements);
    }

    private MethodRefExpr parseMethodRefRest(SourcePos s, MethodRefTarget target) throws ParseException {
        expect("::");
        List<TypeArg> typeArgs = at("<") ? parseTypeArgs() : List.of();
        String name = accept("new") ? "new" : expectIdent("a method name");
        return new MethodRefExpr(span(s), target, typeArgs, name);
    }

    private LambdaExpr parseLambda() throws ParseException {
        SourcePos s = start();
        List<Param> params = new ArrayList<>();
        boolean parenthesized = accept("(");
        if (!parenthesized) {
            params.add(parseInferredParam());
        } else {
            if (!at(")")) {
                boolean inferred = isIdent(peek()) && (peek(1).is(",") || peek(1).is(")"));
                do params.add(inferred ? parseInferredParam() : parseFormalParam()); while (accept(","));
            }
            expect(")");
        }
        expect("->");
        LambdaBody body = at("{") ? parseBlock() : parseExpr();
        return new LambdaExpr(span(s), params, parenthesized, body);
    }

    private Param parseInferredParam() throws ParseException {
        SourcePos s = start();
        String name = expectIdent("a lambda parameter name");
        return new Param(span(s), Modifiers.NONE, null, false, name, 0);
    }

    private List<Expr> parseArgs() throws ParseException {
        expect("(");
        List<Expr> args = new ArrayList<>();
        if (!at(")")) {
            do args.add(parseExpr()); while (accept(","));
        }
        expect(")");
        return args;
    }

    private Expr parseParenExpr() throws ParseException {
        expect("(");
        Expr e = parseExpr();
        expect(")");
        return e;
    }

    // =========================================================================
    // Lookahead. These scan tokens without consuming them or building nodes.
    // =========================================================================

    /** Index just past a type starting at token {@code i} (annotations, name, type arguments, dims), or -1. */
    private int scanType(int i) {
        i = scanAnnotations(i);
        if (i < 0) return -1;
        Token t = tok(i);
        if (isPrimitiveOrVoid(t)) {
            i++;
        } else if (isIdent(t)) {
            i = scanTypeArgsIfAny(i + 1);
            while (i > 0 && tok(i).is(".") && (isIdent(tok(i + 1)) || tok(i + 1).is("@"))) {
                i = scanAnnotations(i + 1);
                if (i < 0 || !isIdent(tok(i))) return -1;
                i = scanTypeArgsIfAny(i + 1);
            }
            if (i < 0) return -1;
        } else {
            return -1;
        }
        while (tok(i).is("[") && tok(i + 1).is("]")) i += 2;
        return i;
    }

    private int scanTypeArgsIfAny(int i) {
        if (!tok(i).is("<")) return i;
        int depth = 0;
        for (;; i++) {
            Token t = tok(i);
            if (t.is("<")) depth++;
            else if (t.is(">")) { if (--depth == 0) return i + 1; }
            else if (!(isIdent(t) || isPrimitiveOrVoid(t) || t.is(",") || t.is(".") || t.is("?") || t.is("&")
                       || t.is("[") || t.is("]") || t.is("@") || t.is("extends") || t.is("super"))) return -1;
        }
    }

    /** Index just past any annotations starting at token {@code i}, or -1 if one is malformed. */
    private int scanAnnotations(int i) {
        while (tok(i).is("@") && !tok(i + 1).is("interface")) {
            if (!isIdent(tok(i + 1))) return -1;
            i += 2;
            while (tok(i).is(".") && isIdent(tok(i + 1))) i += 2;
            if (tok(i).is("(")) {
                i = scanParens(i);
                if (i < 0) return -1;
            }
        }
        return i;
    }

    /** Index just past the parenthesized group starting at token {@code i}, or -1 if it never closes. */
    private int scanParens(int i) {
        int depth = 0;
        for (;; i++) {
            Token t = tok(i);
            if (t.is(TokenType.EOF)) return -1;
            if (t.is("(")) depth++;
            else if (t.is(")") && --depth == 0) return i + 1;
        }
    }

    /** Whether the tokens in [from, to) include {@code <} or {@code [}, which only a type can contain before {@code ::}. */
    private boolean hasTypeOnlySyntax(int from, int to) {
        for (int i = from; i < to; i++) if (tok(i).is("<") || tok(i).is("[")) return true;
        return false;
    }

    /** {@code Type name} followed by '=', ';', ',', '[' or ':' starts a variable declaration. */
    private boolean localVarDeclAhead() {
        int i = scanType(index);
        if (i < 0 || !isIdent(tok(i))) return false;
        Token after = tok(i + 1);
        return after.is("=") || after.is(";") || after.is(",") || after.is("[") || after.is(":");
    }

    /** Inside {@code for (}: [modifiers] Type name ':' */
    private boolean forEachAhead() {
        int i = index;
        while (tok(i).is("final") || tok(i).is("@")) {
            i = tok(i).is("final") ? i + 1 : scanAnnotations(i);
            if (i < 0) return false;
        }
        int j = scanType(i);
        return j > 0 && isIdent(tok(j)) && tok(j + 1).is(":");
    }

    /** In a case label: [modifiers] Type name, or a record pattern Type(...). */
    private boolean patternAhead() {
        int i = index;
        while (tok(i).is("final") || tok(i).is("@")) {
            i = tok(i).is("final") ? i + 1 : scanAnnotations(i);
            if (i < 0) return false;
        }
        int j = scanType(i);
        return j > 0 && (isIdent(tok(j)) || tok(j).is("("));
    }

    /** {@code x ->} or {@code (...) ->} */
    private boolean lambdaAhead(int i) {
        Token t = tok(i);
        if (isIdent(t)) return tok(i + 1).is("->");
        if (!t.is("(")) return false;
        int j = scanParens(i);
        return j > 0 && tok(j).is("->");
    }

    /**
     * Whether the '(' at the current token starts a cast. A primitive cast may be followed by
     * anything; a reference cast only by an operand that cannot also continue a binary
     * expression (JLS 15.16), so {@code (a) - b} stays a subtraction.
     */
    private boolean castAhead() {
        int typeStart = scanAnnotations(index + 1);
        if (typeStart < 0) return false;
        boolean primitive = isPrimitiveOrVoid(tok(typeStart));
        int j = scanType(index + 1);
        if (j < 0) return false;
        while (!primitive && tok(j).is("&")) {
            j = scanType(j + 1);
            if (j < 0) return false;
        }
        if (!tok(j).is(")")) return false;
        if (primitive) return true;
        Token after = tok(j + 1);
        return isIdent(after) || isLiteral(after) || isPrimitiveOrVoid(after) || lambdaAhead(j + 1)
            || after.is("(") || after.is("!") || after.is("~")
            || after.is("this") || after.is("super") || after.is("new") || after.is("switch");
    }

    /** A {@code yield} that starts a yield statement rather than naming a variable. */
    private boolean yieldStatementAhead() {
        Token next = peek(1);
        if (next.is(TokenType.OPERATOR) && (ASSIGN_OPS.contains(next.text()) || next.is("++") || next.is("--")
                || next.is("->") || next.is("::") || next.is(":"))) return false;
        return !(next.is(".") || next.is("[") || next.is(";") || next.is(",") || next.is(")"));
    }

    // =========================================================================
    // Operators. The lexer never joins '>' characters so generics close cleanly;
    // '>>', '>>>', '>>=' and '>>>=' are rebuilt here from adjacent tokens.
    // =========================================================================

    /** The operator at the current token (with '>' sequences joined), "instanceof", or null. */
    private String peekOperator() {
        Token t = peek();
        if (t.is("instanceof")) return "instanceof";
        if (!t.is(TokenType.OPERATOR)) return null;
        if (!t.text().startsWith(">")) return t.text();
        String op = t.text();
        for (int j = index + 1; op.equals(">") || op.equals(">>"); j++) {
            Token next = tok(j);
            if (!adjacent(tok(j - 1), next) || !(next.is(">") || next.is(">="))) break;
            op += next.text();
        }
        return op;
    }

    /** Consumes the tokens that make up {@code op} as returned by {@link #peekOperator}. */
    private void consumeOperator(String op) {
        index += op.startsWith(">") ? op.replace("=", "").length() : 1;
    }

    // =========================================================================
    // Token helpers
    // =========================================================================

    private Token tok(int i)          { return tokens.get(Math.min(i, tokens.size() - 1)); }
    private Token peek()              { return tok(index); }
    private Token peek(int ahead)     { return tok(index + ahead); }
    private boolean atEnd()           { return peek().is(TokenType.EOF); }
    private boolean at(String text)   { return peek().is(text); }
    private SourcePos start()         { return peek().start(); }

    private Token next() {
        Token t = peek();
        if (!atEnd()) index++;
        return t;
    }

    private boolean accept(String text) {
        if (!at(text)) return false;
        next();
        return true;
    }

    private boolean acceptContextual(String word) {
        if (!isIdent(peek()) || !at(word)) return false;
        next();
        return true;
    }

    private Token expect(String text) throws ParseException {
        if (at(text)) return next();
        // A missing token at the end of a line is reported where it belongs, like javac does.
        if (index > 0 && tokens.get(index - 1).end().line() < peek().start().line())
            throw failAt("Expected '" + text + "' after '" + tokens.get(index - 1).text() + "'", tokens.get(index - 1).end());
        throw fail("Expected '" + text + "'");
    }

    private String expectIdent(String what) throws ParseException {
        if (!isIdent(peek())) throw fail("Expected " + what);
        return next().text();
    }

    /** True while the current token is not {@code closer}; fails at end of file. */
    private boolean before(String closer) throws ParseException {
        if (at(closer)) return false;
        if (atEnd()) throw fail("Expected '" + closer + "'");
        return true;
    }

    /** The span from {@code start} to the end of the last consumed token. */
    private SourceSpan span(SourcePos start) { return new SourceSpan(start, tokens.get(index - 1).end()); }
    private SourceSpan span(Node from)       { return span(from.span().start()); }

    private ParseException fail(String message) {
        Token t = peek();
        String found = t.is(TokenType.EOF) ? "end of file" : "'" + t.text() + "'";
        return new ParseException(message + " but found " + found, t.start());
    }

    private ParseException failAt(String message, SourcePos pos) { return new ParseException(message, pos); }

    private static boolean isIdent(Token t)          { return t.is(TokenType.IDENTIFIER); }
    private static boolean isPrimitiveOrVoid(Token t) { return t.is(TokenType.KEYWORD) && (PRIMITIVES.contains(t.text()) || t.is("void")); }
    private static boolean adjacent(Token a, Token b) { return a.end().offset() == b.start().offset(); }

    private static boolean isLiteral(Token t) {
        return switch (t.type()) {
            case INT_LITERAL, FLOAT_LITERAL, STRING_LITERAL, TEXT_BLOCK, CHAR_LITERAL -> true;
            case KEYWORD -> t.is("true") || t.is("false") || t.is("null");
            default -> false;
        };
    }

    private static void addIfPresent(List<Member> members, Member m) { if (m != null) members.add(m); }
}
