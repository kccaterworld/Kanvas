package kanvas.preprocess;

import kanvas.KanvasException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AST-based Kanvas-to-Java transpiler.
 *
 * Pipeline: source → Lexer → Parser → KanvasSyntaxTree.KanvasFile → generate()
 *
 * {@link Preprocessor1} remains as the legacy in-one-class transpiler used by
 * {@link KanvasPreprocessor} for the current build system.
 */
public class Preprocessor {

    public static final String DEFAULT_PACKAGE = "kanvas.generated";

    // =========================================================================
    // Public API
    // =========================================================================

    public static String transpile(String source, String className) throws KanvasException {
        return transpile(source, DEFAULT_PACKAGE, className);
    }

    public static String transpile(String source, String packageName, String className) throws KanvasException {
        KanvasSyntaxTree.KanvasFile ast = parse(source);
        return generate(ast, packageName, classNameFor(className));
    }

    public static KanvasSyntaxTree.KanvasFile parse(String source) throws KanvasException {
        try {
            return new Parser(source).parse();
        } catch (Parser.ParseException e) {
            throw new KanvasException("Parse error: " + e.getMessage(), e);
        }
    }

    public static String generate(KanvasSyntaxTree.KanvasFile ast, String packageName, String className) {
        Set<String> methodNames = methodNameSet(ast);
        StringBuilder out = new StringBuilder();

        if (packageName != null && !packageName.isBlank())
            out.append("package ").append(packageName).append(";\n\n");

        for (KanvasSyntaxTree.ImportDecl imp : ast.imports)
            if (!imp.isKanvasLib) out.append(imp.raw).append("\n");

        if (ast.isTUI) {
            out.append("import kanvas.tui.KanvasTUIScript;\n");
            out.append("import kanvas.tui.KeyEvent;\n\n");
            out.append("public class ").append(className).append(" extends KanvasTUIScript {\n");
        } else {
            out.append("import kanvas.gui.KanvasScript;\n\n");
            out.append("public class ").append(className).append(" extends KanvasScript {\n");
        }

        for (KanvasSyntaxTree.FieldDecl field : ast.fields)
            out.append(indent(field.raw)).append("\n\n");

        for (KanvasSyntaxTree.MethodDecl method : ast.methods)
            out.append(indent(methodToJava(method))).append("\n\n");

        addDefaultMethod(out, methodNames, "setup");
        addDefaultMethod(out, methodNames, "draw");
        if (!ast.isTUI) {
            addDefaultMethod(out, methodNames, "mousePressed");
            addDefaultMethod(out, methodNames, "mouseReleased");
            addDefaultMethod(out, methodNames, "mouseClicked");
            addDefaultMethod(out, methodNames, "mouseDragged");
            addDefaultMethod(out, methodNames, "mouseWheel");
            addDefaultMethod(out, methodNames, "keyPressed");
            addDefaultMethod(out, methodNames, "keyReleased");
            addDefaultMethod(out, methodNames, "keyTyped");
        }

        out.append("    public static void main(String[] args) {\n");
        out.append("        ").append(className).append(" instance = new ").append(className).append("();\n");
        out.append("        instance.start();\n");
        out.append("    }\n\n");
        out.append("}\n");
        return out.toString();
    }

    // =========================================================================
    // Code generation — methods
    // =========================================================================

    public static String methodToJava(KanvasSyntaxTree.MethodDecl m) {
        String mods = m.modifiers.isEmpty() ? "" : String.join(" ", m.modifiers) + " ";
        if (!m.hasAccessModifier) mods = "public " + mods;
        String params = m.params.stream()
            .map(p -> p.type + (p.varargs ? "... " : " ") + p.name)
            .collect(Collectors.joining(", "));
        String throws_ = m.throwsTypes.isEmpty() ? "" : " throws " + String.join(", ", m.throwsTypes);
        return mods + m.returnType + " " + m.name + "(" + params + ")" + throws_ + " " + stmtToJava(m.body);
    }

    // =========================================================================
    // Code generation — statements
    // =========================================================================

    /**
     * Converts a statement to Java source with relative indentation.
     * Each line of the result starts at column 0; callers add their own prefix.
     * Use {@link #embedInBlock} when placing inside a block.
     */
    public static String stmtToJava(KanvasSyntaxTree.Stmt stmt) {
        if (stmt instanceof KanvasSyntaxTree.BlockStmt b) {
            if (b.stmts.isEmpty()) return "{}";
            String inner = b.stmts.stream()
                .map(s -> embedInBlock(s, "    "))
                .collect(Collectors.joining("\n"));
            return "{\n" + inner + "\n}";
        }
        if (stmt instanceof KanvasSyntaxTree.ExprStmt s)    return exprToJava(s.expr) + ";";
        if (stmt instanceof KanvasSyntaxTree.VarDeclStmt s) return varDeclToJava(s);
        if (stmt instanceof KanvasSyntaxTree.ReturnStmt s)  return "return" + (s.value != null ? " " + exprToJava(s.value) : "") + ";";
        if (stmt instanceof KanvasSyntaxTree.IfStmt s)      return ifToJava(s);
        if (stmt instanceof KanvasSyntaxTree.WhileStmt s)   return "while (" + exprToJava(s.cond) + ") " + stmtToJava(s.body);
        if (stmt instanceof KanvasSyntaxTree.DoWhileStmt s) return "do " + stmtToJava(s.body) + " while (" + exprToJava(s.cond) + ");";
        if (stmt instanceof KanvasSyntaxTree.ForStmt s)     return forToJava(s);
        if (stmt instanceof KanvasSyntaxTree.ForEachStmt s) return "for (" + s.type + " " + s.name + " : " + exprToJava(s.iterable) + ") " + stmtToJava(s.body);
        if (stmt instanceof KanvasSyntaxTree.BreakStmt s)   return "break" + (s.label != null ? " " + s.label : "") + ";";
        if (stmt instanceof KanvasSyntaxTree.ContinueStmt s) return "continue" + (s.label != null ? " " + s.label : "") + ";";
        if (stmt instanceof KanvasSyntaxTree.ThrowStmt s)   return "throw " + exprToJava(s.expr) + ";";
        if (stmt instanceof KanvasSyntaxTree.TryStmt s)     return tryToJava(s);
        if (stmt instanceof KanvasSyntaxTree.SynchronizedStmt s) return "synchronized (" + exprToJava(s.lock) + ") " + stmtToJava(s.body);
        if (stmt instanceof KanvasSyntaxTree.RawStmt s)     return s.raw;
        return "/* unknown stmt */";
    }

    private static String varDeclToJava(KanvasSyntaxTree.VarDeclStmt s) {
        String mods = s.modifiers.isEmpty() ? "" : String.join(" ", s.modifiers) + " ";
        return mods + s.type + " " + s.name + (s.init != null ? " = " + exprToJava(s.init) : "") + ";";
    }

    private static String ifToJava(KanvasSyntaxTree.IfStmt s) {
        String src = "if (" + exprToJava(s.cond) + ") " + stmtToJava(s.then);
        if (s.else_ != null) src += " else " + stmtToJava(s.else_);
        return src;
    }

    private static String forToJava(KanvasSyntaxTree.ForStmt s) {
        String init = s.init == null ? "" : stmtToJavaNoSemi(s.init);
        String cond = s.cond == null ? "" : exprToJava(s.cond);
        String upd  = s.update.stream().map(Preprocessor::exprToJava).collect(Collectors.joining(", "));
        return "for (" + init + "; " + cond + "; " + upd + ") " + stmtToJava(s.body);
    }

    private static String tryToJava(KanvasSyntaxTree.TryStmt s) {
        StringBuilder sb = new StringBuilder("try ").append(stmtToJava(s.body));
        for (KanvasSyntaxTree.CatchClause c : s.catches)
            sb.append(" catch (").append(c.exceptionType).append(" ").append(c.name).append(") ").append(stmtToJava(c.body));
        if (s.finally_ != null) sb.append(" finally ").append(stmtToJava(s.finally_));
        return sb.toString();
    }

    /** Like {@link #stmtToJava} but strips the trailing ';' — used for for-loop init. */
    private static String stmtToJavaNoSemi(KanvasSyntaxTree.Stmt s) {
        String str = stmtToJava(s);
        return str.endsWith(";") ? str.substring(0, str.length() - 1) : str;
    }

    /**
     * Indents a statement for embedding inside a block at a given base indent level.
     * All lines of the statement get {@code baseIndent} prepended.
     */
    private static String embedInBlock(KanvasSyntaxTree.Stmt s, String baseIndent) {
        String src = stmtToJava(s);
        return baseIndent + src.replace("\n", "\n" + baseIndent);
    }

    // =========================================================================
    // Code generation — expressions
    // =========================================================================

    public static String exprToJava(KanvasSyntaxTree.Expr expr) {
        if (expr instanceof KanvasSyntaxTree.NameExpr e)         return e.name;
        if (expr instanceof KanvasSyntaxTree.LiteralExpr e)      return e.raw;
        if (expr instanceof KanvasSyntaxTree.ParenExpr e)        return "(" + exprToJava(e.expr) + ")";
        if (expr instanceof KanvasSyntaxTree.FieldAccessExpr e)  return exprToJava(e.target) + "." + e.field;
        if (expr instanceof KanvasSyntaxTree.MethodCallExpr e)   return callToJava(e);
        if (expr instanceof KanvasSyntaxTree.ArrayAccessExpr e)  return exprToJava(e.array) + "[" + exprToJava(e.index) + "]";
        if (expr instanceof KanvasSyntaxTree.AssignExpr e)       return exprToJava(e.target) + " " + e.op + " " + exprToJava(e.value);
        if (expr instanceof KanvasSyntaxTree.BinaryExpr e)       return "(" + exprToJava(e.left) + " " + e.op + " " + exprToJava(e.right) + ")";
        if (expr instanceof KanvasSyntaxTree.UnaryExpr e)        return e.postfix ? exprToJava(e.expr) + e.op : e.op + exprToJava(e.expr);
        if (expr instanceof KanvasSyntaxTree.CastExpr e)         return "((" + e.type + ") " + exprToJava(e.expr) + ")";
        if (expr instanceof KanvasSyntaxTree.NewObjectExpr e)    return "new " + e.type + "(" + joinArgs(e.args) + ")";
        if (expr instanceof KanvasSyntaxTree.NewArrayExpr e)     return newArrayToJava(e);
        if (expr instanceof KanvasSyntaxTree.ArrayInitExpr e)    return "{" + joinArgs(e.elements) + "}";
        if (expr instanceof KanvasSyntaxTree.TernaryExpr e)      return "(" + exprToJava(e.cond) + " ? " + exprToJava(e.then) + " : " + exprToJava(e.else_) + ")";
        if (expr instanceof KanvasSyntaxTree.InstanceofExpr e)   return exprToJava(e.expr) + " instanceof " + e.type + (e.bindingName != null ? " " + e.bindingName : "");
        if (expr instanceof KanvasSyntaxTree.LambdaExpr e)       return lambdaToJava(e);
        if (expr instanceof KanvasSyntaxTree.RawExpr e)          return e.raw;
        return "/* unknown expr */";
    }

    private static String callToJava(KanvasSyntaxTree.MethodCallExpr e) {
        String target = e.target != null ? exprToJava(e.target) + "." : "";
        return target + e.name + "(" + joinArgs(e.args) + ")";
    }

    private static String newArrayToJava(KanvasSyntaxTree.NewArrayExpr e) {
        String dims = e.dimensions.stream()
            .map(d -> "[" + (d instanceof KanvasSyntaxTree.RawExpr r && r.raw.isEmpty() ? "" : exprToJava(d)) + "]")
            .collect(Collectors.joining());
        return "new " + e.elementType + dims;
    }

    private static String lambdaToJava(KanvasSyntaxTree.LambdaExpr e) {
        String params = e.params.stream()
            .map(p -> p.type.isEmpty() ? p.name : p.type + " " + p.name)
            .collect(Collectors.joining(", "));
        String body = e.body instanceof KanvasSyntaxTree.Expr ex ? exprToJava(ex)
                    : e.body instanceof KanvasSyntaxTree.Stmt st ? stmtToJava(st)
                    : "/* ? */";
        return "(" + params + ") -> " + body;
    }

    private static String joinArgs(List<KanvasSyntaxTree.Expr> args) {
        return args.stream().map(Preprocessor::exprToJava).collect(Collectors.joining(", "));
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    public static String classNameFor(String value) {
        return Preprocessor1.classNameFor(value);
    }

    private static Set<String> methodNameSet(KanvasSyntaxTree.KanvasFile ast) {
        Set<String> names = new java.util.HashSet<>();
        for (KanvasSyntaxTree.MethodDecl m : ast.methods) names.add(m.name);
        return names;
    }

    private static void addDefaultMethod(StringBuilder out, Set<String> present, String name) {
        if (!present.contains(name))
            out.append("    public void ").append(name).append("() {}\n\n");
    }

    private static String indent(String source) {
        String[] lines = source.split("\\R", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("\n");
            if (!lines[i].isBlank()) sb.append("    ");
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
