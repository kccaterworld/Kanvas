package kanvas.preprocess.generate;

import kanvas.preprocess.ast.KanvasSyntaxTree.*;

import java.util.List;

/**
 * Prints syntax-tree nodes as Java source. Printing makes no decisions of its own; changes to
 * what gets generated belong in the transform stage.
 *
 * <p>The output has the same tokens as the source the parser read (comments, formatting and
 * trailing commas aside), so parse → print is a faithful round trip.
 */
public final class JavaPrinter {
    private static final String INDENT = "    ";

    private final StringBuilder out = new StringBuilder();
    private int depth;

    /** Prints any node; a {@link CompilationUnit} prints as a complete Java file. */
    public static String print(Node node) {
        JavaPrinter p = new JavaPrinter();
        p.node(node);
        return p.out.toString();
    }

    private void write(String s) { out.append(s); }
    private void newline()       { out.append('\n').append(INDENT.repeat(depth)); }

    private void node(Node node) {
        switch (node) {
            case CompilationUnit u -> compilationUnit(u);
            case PackageDecl p -> packageDecl(p);
            case ImportDecl i -> importDecl(i);
            case Member m -> member(m);
            case Stmt s -> stmt(s);
            case Expr e -> expr(e);
            case TypeArg t -> type(t);
            case Pattern p -> pattern(p);
            case Annotation a -> annotation(a);
            case ElementArray a -> elementValue(a);
            case ElementPair p -> elementPair(p);
            case KeywordModifier k -> write(k.keyword());
            case DefaultLabel d -> write("default");
            case Resource r -> resource(r);
            case TypeParam t -> typeParam(t);
            case EnumConstant c -> enumConstant(c);
            case Param p -> param(p);
            case VarDeclarator d -> declarator(d);
            case CatchClause c -> catchClause(c);
            case SwitchCase c -> switchCase(c);
        }
    }

    // =========================================================================
    // File, package, imports
    // =========================================================================

    private void compilationUnit(CompilationUnit u) {
        if (u.packageDecl() != null) { packageDecl(u.packageDecl()); write("\n\n"); }
        for (ImportDecl i : u.imports()) { importDecl(i); write("\n"); }
        if (!u.imports().isEmpty()) write("\n");
        for (int i = 0; i < u.members().size(); i++) {
            if (i > 0) write("\n");
            member(u.members().get(i));
            write("\n");
        }
    }

    private void packageDecl(PackageDecl p) {
        annotationsInline(p.annotations());
        write("package " + p.name() + ";");
    }

    private void importDecl(ImportDecl i) {
        write("import " + (i.isStatic() ? "static " : "") + i.name() + (i.onDemand() ? ".*" : "") + ";");
    }

    // =========================================================================
    // Members
    // =========================================================================

    private void member(Member m) {
        switch (m) {
            case FieldDecl f -> {
                modifiers(f.modifiers(), true);
                type(f.type());
                write(" ");
                declarators(f.declarators());
                write(";");
            }
            case MethodDecl md -> method(md);
            case ConstructorDecl c -> {
                modifiers(c.modifiers(), true);
                typeParamsThenSpace(c.typeParams());
                write(c.name());
                if (c.params() != null) params(c.params());
                throwsClause(c.throwsTypes());
                write(" ");
                block(c.body());
            }
            case InitializerDecl i -> {
                if (i.isStatic()) write("static ");
                block(i.body());
            }
            case TypeDecl t -> typeDecl(t);
        }
    }

    private void method(MethodDecl m) {
        modifiers(m.modifiers(), true);
        typeParamsThenSpace(m.typeParams());
        type(m.returnType());
        write(" " + m.name());
        params(m.params());
        write("[]".repeat(m.extraDims()));
        throwsClause(m.throwsTypes());
        if (m.defaultValue() != null) { write(" default "); elementValue(m.defaultValue()); }
        if (m.body() == null) { write(";"); return; }
        write(" ");
        block(m.body());
    }

    private void typeDecl(TypeDecl t) {
        modifiers(t.modifiers(), true);
        switch (t) {
            case ClassDecl c -> {
                write("class " + c.name());
                typeParams(c.typeParams());
                if (c.superclass() != null) { write(" extends "); type(c.superclass()); }
                typeList(" implements ", c.interfaces());
                typeList(" permits ", c.permits());
                write(" ");
                classBody(c.body());
            }
            case InterfaceDecl i -> {
                write("interface " + i.name());
                typeParams(i.typeParams());
                typeList(" extends ", i.superinterfaces());
                typeList(" permits ", i.permits());
                write(" ");
                classBody(i.body());
            }
            case EnumDecl e -> {
                write("enum " + e.name());
                typeList(" implements ", e.interfaces());
                write(" ");
                enumBody(e);
            }
            case RecordDecl r -> {
                write("record " + r.name());
                typeParams(r.typeParams());
                params(r.components());
                typeList(" implements ", r.interfaces());
                write(" ");
                classBody(r.body());
            }
            case AnnotationTypeDecl a -> {
                write("@interface " + a.name() + " ");
                classBody(a.body());
            }
        }
    }

    private void classBody(List<Member> members) {
        if (members.isEmpty()) { write("{}"); return; }
        write("{");
        depth++;
        Member previous = null;
        for (Member m : members) {
            // Blank line between members, except between consecutive fields.
            if (previous != null && !(previous instanceof FieldDecl && m instanceof FieldDecl)) out.append('\n');
            newline();
            member(m);
            previous = m;
        }
        depth--;
        newline();
        write("}");
    }

    private void enumBody(EnumDecl e) {
        write("{");
        depth++;
        for (int i = 0; i < e.constants().size(); i++) {
            newline();
            enumConstant(e.constants().get(i));
            if (i < e.constants().size() - 1) write(",");
        }
        if (!e.body().isEmpty()) {
            write(";");
            for (Member m : e.body()) { out.append('\n'); newline(); member(m); }
        }
        depth--;
        newline();
        write("}");
    }

    private void enumConstant(EnumConstant c) {
        annotationsInline(c.annotations());
        write(c.name());
        if (c.args() != null) args(c.args());
        if (c.body() != null) { write(" "); classBody(c.body()); }
    }

    // =========================================================================
    // Modifiers, annotations, parameters, declarators
    // =========================================================================

    /** Writes modifiers in source order. Declaration-level annotations go on their own line. */
    private void modifiers(Modifiers modifiers, boolean ownLineAnnotations) {
        for (Modifier item : modifiers.items()) {
            switch (item) {
                case Annotation a -> {
                    annotation(a);
                    if (ownLineAnnotations) newline(); else write(" ");
                }
                case KeywordModifier k -> write(k.keyword() + " ");
            }
        }
    }

    private void annotationsInline(List<Annotation> annotations) {
        for (Annotation a : annotations) { annotation(a); write(" "); }
    }

    private void annotation(Annotation a) {
        write("@" + a.name());
        if (a.args() == null) return;
        write("(");
        for (int i = 0; i < a.args().size(); i++) {
            if (i > 0) write(", ");
            elementPair(a.args().get(i));
        }
        write(")");
    }

    private void elementPair(ElementPair p) {
        if (p.name() != null) write(p.name() + " = ");
        elementValue(p.value());
    }

    private void elementValue(ElementValue v) {
        switch (v) {
            case Annotation a -> annotation(a);
            case ElementArray a -> {
                write("{");
                for (int i = 0; i < a.values().size(); i++) {
                    if (i > 0) write(", ");
                    elementValue(a.values().get(i));
                }
                write("}");
            }
            case Expr e -> expr(e);
        }
    }

    private void typeParams(List<TypeParam> params) {
        if (params.isEmpty()) return;
        write("<");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) write(", ");
            typeParam(params.get(i));
        }
        write(">");
    }

    private void typeParamsThenSpace(List<TypeParam> params) {
        typeParams(params);
        if (!params.isEmpty()) write(" ");
    }

    private void typeParam(TypeParam t) {
        annotationsInline(t.annotations());
        write(t.name());
        if (t.bounds().isEmpty()) return;
        write(" extends ");
        for (int i = 0; i < t.bounds().size(); i++) {
            if (i > 0) write(" & ");
            type(t.bounds().get(i));
        }
    }

    private void params(List<Param> params) {
        write("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) write(", ");
            param(params.get(i));
        }
        write(")");
    }

    private void param(Param p) {
        if (p.type() == null) { write(p.name()); return; }
        modifiers(p.modifiers(), false);
        type(p.type());
        if (p.varargs()) write("...");
        write(" " + p.name() + "[]".repeat(p.extraDims()));
    }

    private void throwsClause(List<TypeRef> types) { typeList(" throws ", types); }

    private void typeList(String prefix, List<TypeRef> types) {
        if (types.isEmpty()) return;
        write(prefix);
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) write(", ");
            type(types.get(i));
        }
    }

    private void declarators(List<VarDeclarator> declarators) {
        for (int i = 0; i < declarators.size(); i++) {
            if (i > 0) write(", ");
            declarator(declarators.get(i));
        }
    }

    private void declarator(VarDeclarator d) {
        write(d.name() + "[]".repeat(d.extraDims()));
        if (d.init() != null) { write(" = "); expr(d.init()); }
    }

    // =========================================================================
    // Types and patterns
    // =========================================================================

    private void type(TypeArg t) {
        switch (t) {
            case TypeRef r -> {
                annotationsInline(r.annotations());
                for (int i = 0; i < r.names().size(); i++) {
                    if (i > 0) write(".");
                    TypeName n = r.names().get(i);
                    annotationsInline(n.annotations());
                    write(n.name());
                    if (n.typeArgs() != null) typeArgList(n.typeArgs());
                }
                write("[]".repeat(r.dims()));
            }
            case WildcardType w -> {
                annotationsInline(w.annotations());
                write("?");
                if (w.bound() == WildcardType.Bound.EXTENDS) write(" extends ");
                if (w.bound() == WildcardType.Bound.SUPER) write(" super ");
                if (w.boundType() != null) type(w.boundType());
            }
        }
    }

    /** Writes {@code <A, B>}, or {@code <>} for an empty (diamond) list. */
    private void typeArgList(List<TypeArg> args) {
        write("<");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) write(", ");
            type(args.get(i));
        }
        write(">");
    }

    /** Writes explicit type arguments of a call or method reference, if any were given. */
    private void explicitTypeArgs(List<TypeArg> args) {
        if (!args.isEmpty()) typeArgList(args);
    }

    private void pattern(Pattern p) {
        switch (p) {
            case TypePattern t -> {
                modifiers(t.modifiers(), false);
                type(t.type());
                write(" " + t.name());
            }
            case RecordPattern r -> {
                type(r.type());
                write("(");
                for (int i = 0; i < r.components().size(); i++) {
                    if (i > 0) write(", ");
                    pattern(r.components().get(i));
                }
                write(")");
            }
        }
    }

    // =========================================================================
    // Statements
    // =========================================================================

    private void block(BlockStmt b) {
        if (b.stmts().isEmpty()) { write("{}"); return; }
        write("{");
        depth++;
        for (Stmt s : b.stmts()) { newline(); stmt(s); }
        depth--;
        newline();
        write("}");
    }

    /** The body of if/while/for: a block on the same line, anything else indented on the next. */
    private void body(Stmt s) {
        if (s instanceof BlockStmt b) { write(" "); block(b); return; }
        depth++;
        newline();
        stmt(s);
        depth--;
    }

    private void stmt(Stmt s) {
        switch (s) {
            case BlockStmt b -> block(b);
            case EmptyStmt e -> write(";");
            case LocalVarStmt v -> { localVar(v); write(";"); }
            case LocalClassStmt c -> typeDecl(c.decl());
            case ExprStmt e -> { expr(e.expr()); write(";"); }
            case IfStmt i -> {
                write("if (");
                expr(i.cond());
                write(")");
                body(i.thenStmt());
                if (i.elseStmt() == null) return;
                if (i.thenStmt() instanceof BlockStmt) write(" "); else newline();
                write("else");
                if (i.elseStmt() instanceof IfStmt) { write(" "); stmt(i.elseStmt()); }
                else body(i.elseStmt());
            }
            case WhileStmt w -> {
                write("while (");
                expr(w.cond());
                write(")");
                body(w.body());
            }
            case DoWhileStmt d -> {
                write("do");
                body(d.body());
                if (d.body() instanceof BlockStmt) write(" "); else newline();
                write("while (");
                expr(d.cond());
                write(");");
            }
            case ForStmt f -> forStmt(f);
            case ForEachStmt f -> {
                write("for (");
                modifiers(f.modifiers(), false);
                type(f.type());
                write(" " + f.name() + " : ");
                expr(f.iterable());
                write(")");
                body(f.body());
            }
            case ReturnStmt r -> {
                write("return");
                if (r.value() != null) { write(" "); expr(r.value()); }
                write(";");
            }
            case BreakStmt b -> write(b.label() == null ? "break;" : "break " + b.label() + ";");
            case ContinueStmt c -> write(c.label() == null ? "continue;" : "continue " + c.label() + ";");
            case YieldStmt y -> { write("yield "); expr(y.value()); write(";"); }
            case ThrowStmt t -> { write("throw "); expr(t.expr()); write(";"); }
            case TryStmt t -> tryStmt(t);
            case SwitchStmt sw -> {
                write("switch (");
                expr(sw.selector());
                write(") ");
                switchBody(sw.cases());
            }
            case SynchronizedStmt sy -> {
                write("synchronized (");
                expr(sy.lock());
                write(") ");
                block(sy.body());
            }
            case LabeledStmt l -> { write(l.label() + ": "); stmt(l.body()); }
            case AssertStmt a -> {
                write("assert ");
                expr(a.cond());
                if (a.message() != null) { write(" : "); expr(a.message()); }
                write(";");
            }
        }
    }

    private void localVar(LocalVarStmt v) {
        modifiers(v.modifiers(), false);
        type(v.type());
        write(" ");
        declarators(v.declarators());
    }

    private void forStmt(ForStmt f) {
        write("for (");
        for (int i = 0; i < f.init().size(); i++) {
            if (i > 0) write(", ");
            switch (f.init().get(i)) {
                case LocalVarStmt v -> localVar(v);
                case ExprStmt e -> expr(e.expr());
                default -> throw new IllegalStateException("Unexpected for-loop initializer: " + f.init().get(i));
            }
        }
        write(";");
        if (f.cond() != null) { write(" "); expr(f.cond()); }
        write(";");
        if (!f.update().isEmpty()) { write(" "); exprList(f.update()); }
        write(")");
        body(f.body());
    }

    private void tryStmt(TryStmt t) {
        write("try ");
        if (!t.resources().isEmpty()) {
            write("(");
            for (int i = 0; i < t.resources().size(); i++) {
                if (i > 0) write("; ");
                resource(t.resources().get(i));
            }
            write(") ");
        }
        block(t.body());
        for (CatchClause c : t.catches()) { write(" "); catchClause(c); }
        if (t.finallyBlock() != null) { write(" finally "); block(t.finallyBlock()); }
    }

    private void resource(Resource r) {
        switch (r) {
            case ResourceDecl d -> {
                modifiers(d.modifiers(), false);
                type(d.type());
                write(" " + d.name() + " = ");
                expr(d.init());
            }
            case ResourceRef ref -> expr(ref.expr());
        }
    }

    private void catchClause(CatchClause c) {
        write("catch (");
        modifiers(c.modifiers(), false);
        for (int i = 0; i < c.types().size(); i++) {
            if (i > 0) write(" | ");
            type(c.types().get(i));
        }
        write(" " + c.name() + ") ");
        block(c.body());
    }

    private void switchBody(List<SwitchCase> cases) {
        if (cases.isEmpty()) { write("{}"); return; }
        write("{");
        depth++;
        for (SwitchCase c : cases) { newline(); switchCase(c); }
        depth--;
        newline();
        write("}");
    }

    private void switchCase(SwitchCase c) {
        List<CaseLabel> labels = c.labels();
        if (labels.size() == 1 && labels.get(0) instanceof DefaultLabel) {
            write("default");
        } else {
            write("case ");
            for (int i = 0; i < labels.size(); i++) {
                if (i > 0) write(", ");
                caseLabel(labels.get(i));
            }
        }
        if (c.guard() != null) { write(" when "); expr(c.guard()); }
        if (c.arrow()) {
            write(" -> ");
            stmt(c.body().get(0));
            return;
        }
        write(":");
        depth++;
        for (Stmt s : c.body()) { newline(); stmt(s); }
        depth--;
    }

    private void caseLabel(CaseLabel label) {
        switch (label) {
            case DefaultLabel d -> write("default");
            case Pattern p -> pattern(p);
            case Expr e -> expr(e);
        }
    }

    // =========================================================================
    // Expressions
    // =========================================================================

    private void expr(Expr e) {
        switch (e) {
            case NameExpr n -> write(n.name());
            case LiteralExpr l -> write(l.text());
            case ParenExpr p -> { write("("); expr(p.expr()); write(")"); }
            case FieldAccessExpr f -> { expr(f.target()); write("." + f.name()); }
            case MethodCallExpr m -> {
                if (m.target() != null) {
                    expr(m.target());
                    write(".");
                    explicitTypeArgs(m.typeArgs());
                }
                write(m.name());
                args(m.args());
            }
            case ArrayAccessExpr a -> { expr(a.array()); write("["); expr(a.index()); write("]"); }
            case AssignExpr a -> { expr(a.target()); write(" " + a.op() + " "); expr(a.value()); }
            case BinaryExpr b -> { expr(b.left()); write(" " + b.op() + " "); expr(b.right()); }
            case UnaryExpr u -> {
                if (u.postfix()) { expr(u.expr()); write(u.op()); return; }
                write(u.op());
                // Keep "- -x" and "+ ++x" from fusing into "--x" / "+++x".
                if (u.expr() instanceof UnaryExpr inner && !inner.postfix() && inner.op().charAt(0) == u.op().charAt(0)
                        && (u.op().charAt(0) == '+' || u.op().charAt(0) == '-')) write(" ");
                expr(u.expr());
            }
            case CastExpr c -> {
                write("(");
                for (int i = 0; i < c.types().size(); i++) {
                    if (i > 0) write(" & ");
                    type(c.types().get(i));
                }
                write(") ");
                expr(c.expr());
            }
            case InstanceofExpr i -> {
                expr(i.expr());
                write(" instanceof ");
                if (i.pattern() != null) pattern(i.pattern()); else type(i.type());
            }
            case TernaryExpr t -> {
                expr(t.cond());
                write(" ? ");
                expr(t.thenExpr());
                write(" : ");
                expr(t.elseExpr());
            }
            case NewObjectExpr n -> {
                if (n.outer() != null) { expr(n.outer()); write("."); }
                write("new ");
                explicitTypeArgs(n.typeArgs());
                type(n.type());
                args(n.args());
                if (n.body() != null) { write(" "); classBody(n.body()); }
            }
            case NewArrayExpr n -> {
                write("new ");
                type(n.elementType());
                for (Expr d : n.dimExprs()) { write("["); expr(d); write("]"); }
                write("[]".repeat(n.extraDims()));
                if (n.init() != null) { write(" "); expr(n.init()); }
            }
            case ArrayInitExpr a -> { write("{"); exprList(a.elements()); write("}"); }
            case LambdaExpr l -> {
                if (l.parenthesized()) params(l.params()); else write(l.params().get(0).name());
                write(" -> ");
                switch (l.body()) {
                    case Expr body -> expr(body);
                    case BlockStmt body -> block(body);
                }
            }
            case MethodRefExpr m -> {
                switch (m.target()) {
                    case Expr target -> expr(target);
                    case TypeRef target -> type(target);
                }
                write("::");
                explicitTypeArgs(m.typeArgs());
                write(m.name());
            }
            case ClassLiteralExpr c -> { type(c.type()); write(".class"); }
            case SwitchExpr sw -> {
                write("switch (");
                expr(sw.selector());
                write(") ");
                switchBody(sw.cases());
            }
        }
    }

    private void args(List<Expr> args) {
        write("(");
        exprList(args);
        write(")");
    }

    private void exprList(List<Expr> exprs) {
        for (int i = 0; i < exprs.size(); i++) {
            if (i > 0) write(", ");
            expr(exprs.get(i));
        }
    }
}
