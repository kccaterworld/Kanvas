package kanvas.preprocess;

import java.util.List;

public class KanvasSyntaxTree {

    public static class SourcePos {
        public final int offset;
        public final int line;
        public final int column;

        public SourcePos(int offset, int line, int column) {
            this.offset = offset;
            this.line = line;
            this.column = column;
        }

        @Override public String toString() { return line + ":" + column; }
    }

    public abstract static class Node {
        public final SourcePos pos;
        protected Node(SourcePos pos) { this.pos = pos; }
    }

    /** Root node for a parsed .kvs file. */
    public static class KanvasFile extends Node {
        public final List<ImportDecl> imports;
        public final List<FieldDecl> fields;
        public final List<MethodDecl> methods;
        public final boolean isTUI;

        public KanvasFile(SourcePos pos, List<ImportDecl> imports, List<FieldDecl> fields,
                          List<MethodDecl> methods, boolean isTUI) {
            super(pos);
            this.imports = List.copyOf(imports);
            this.fields  = List.copyOf(fields);
            this.methods = List.copyOf(methods);
            this.isTUI   = isTUI;
        }
    }

    /** import foo.bar.*; or import kanvas.gui; */
    public static class ImportDecl extends Node {
        /** Qualified name after "import", e.g. "java.util.*" or "kanvas.tui". */
        public final String qualifiedName;
        /** Full source text, e.g. "import java.util.*;" */
        public final String raw;
        /** True for "import kanvas.etc;" — these are Kanvas directives, not Java imports. */
        public final boolean isKanvasLib;

        public ImportDecl(SourcePos pos, String qualifiedName, String raw, boolean isKanvasLib) {
            super(pos);
            this.qualifiedName = qualifiedName;
            this.raw           = raw;
            this.isKanvasLib   = isKanvasLib;
        }
    }

    /** Top-level field declaration: [modifiers] type name [= expr]; */
    public static class FieldDecl extends Node {
        public final List<String> modifiers;
        /** Best-effort type string, may include generics and array brackets. */
        public final String type;
        public final String name;
        /** Full source text including trailing semicolon. */
        public final String raw;

        public FieldDecl(SourcePos pos, List<String> modifiers, String type, String name, String raw) {
            super(pos);
            this.modifiers = List.copyOf(modifiers);
            this.type      = type;
            this.name      = name;
            this.raw       = raw;
        }
    }

    /** A single parameter in a method signature. */
    public static class Param extends Node {
        /** Type string, may include generics and array brackets. */
        public final String type;
        public final String name;
        public final boolean varargs;

        public Param(SourcePos pos, String type, String name, boolean varargs) {
            super(pos);
            this.type    = type;
            this.name    = name;
            this.varargs = varargs;
        }
    }

    /** Top-level method declaration. */
    public static class MethodDecl extends Node {
        public final List<String> modifiers;
        public final String returnType;
        public final String name;
        public final List<Param> params;
        public final List<String> throwsTypes;
        /** Parsed body block. */
        public final BlockStmt body;
        /** Raw source of the body block, including surrounding braces. */
        public final String rawBody;
        /** Full source text of the method from modifiers to closing brace. */
        public final String raw;
        /** True when the source already has a public/private/protected modifier. */
        public final boolean hasAccessModifier;

        public MethodDecl(SourcePos pos, List<String> modifiers, String returnType, String name,
                          List<Param> params, List<String> throwsTypes,
                          BlockStmt body, String rawBody, String raw, boolean hasAccessModifier) {
            super(pos);
            this.modifiers         = List.copyOf(modifiers);
            this.returnType        = returnType;
            this.name              = name;
            this.params            = List.copyOf(params);
            this.throwsTypes       = List.copyOf(throwsTypes);
            this.body              = body;
            this.rawBody           = rawBody;
            this.raw               = raw;
            this.hasAccessModifier = hasAccessModifier;
        }
    }

    // =========================================================================
    // Statements
    // =========================================================================

    public abstract static class Stmt extends Node {
        protected Stmt(SourcePos pos) { super(pos); }
    }

    public static class BlockStmt extends Stmt {
        public final List<Stmt> stmts;
        public BlockStmt(SourcePos pos, List<Stmt> stmts) {
            super(pos); this.stmts = List.copyOf(stmts);
        }
    }

    public static class ExprStmt extends Stmt {
        public final Expr expr;
        public ExprStmt(SourcePos pos, Expr expr) { super(pos); this.expr = expr; }
    }

    /** Local variable declaration: [final] type name [= init]; */
    public static class VarDeclStmt extends Stmt {
        public final List<String> modifiers;
        public final String type, name;
        public final Expr init; // nullable
        public VarDeclStmt(SourcePos pos, List<String> modifiers, String type, String name, Expr init) {
            super(pos);
            this.modifiers = List.copyOf(modifiers);
            this.type = type; this.name = name; this.init = init;
        }
    }

    public static class ReturnStmt extends Stmt {
        public final Expr value; // nullable
        public ReturnStmt(SourcePos pos, Expr value) { super(pos); this.value = value; }
    }

    public static class IfStmt extends Stmt {
        public final Expr cond;
        public final Stmt then;
        public final Stmt else_; // nullable
        public IfStmt(SourcePos pos, Expr cond, Stmt then, Stmt else_) {
            super(pos); this.cond = cond; this.then = then; this.else_ = else_;
        }
    }

    public static class WhileStmt extends Stmt {
        public final Expr cond;
        public final Stmt body;
        public WhileStmt(SourcePos pos, Expr cond, Stmt body) {
            super(pos); this.cond = cond; this.body = body;
        }
    }

    public static class DoWhileStmt extends Stmt {
        public final Stmt body;
        public final Expr cond;
        public DoWhileStmt(SourcePos pos, Stmt body, Expr cond) {
            super(pos); this.body = body; this.cond = cond;
        }
    }

    /** Traditional for loop. {@code init} is VarDeclStmt, ExprStmt, or null. */
    public static class ForStmt extends Stmt {
        public final Stmt init;       // nullable
        public final Expr cond;       // nullable
        public final List<Expr> update;
        public final Stmt body;
        public ForStmt(SourcePos pos, Stmt init, Expr cond, List<Expr> update, Stmt body) {
            super(pos);
            this.init = init; this.cond = cond;
            this.update = List.copyOf(update); this.body = body;
        }
    }

    public static class ForEachStmt extends Stmt {
        public final String type, name;
        public final Expr iterable;
        public final Stmt body;
        public ForEachStmt(SourcePos pos, String type, String name, Expr iterable, Stmt body) {
            super(pos); this.type = type; this.name = name; this.iterable = iterable; this.body = body;
        }
    }

    public static class BreakStmt extends Stmt {
        public final String label; // nullable
        public BreakStmt(SourcePos pos, String label) { super(pos); this.label = label; }
    }

    public static class ContinueStmt extends Stmt {
        public final String label; // nullable
        public ContinueStmt(SourcePos pos, String label) { super(pos); this.label = label; }
    }

    public static class ThrowStmt extends Stmt {
        public final Expr expr;
        public ThrowStmt(SourcePos pos, Expr expr) { super(pos); this.expr = expr; }
    }

    public static class TryStmt extends Stmt {
        public final BlockStmt body;
        public final List<CatchClause> catches;
        public final BlockStmt finally_; // nullable
        public TryStmt(SourcePos pos, BlockStmt body, List<CatchClause> catches, BlockStmt finally_) {
            super(pos); this.body = body; this.catches = List.copyOf(catches); this.finally_ = finally_;
        }
    }

    public static class CatchClause extends Node {
        public final String exceptionType, name;
        public final BlockStmt body;
        public CatchClause(SourcePos pos, String exceptionType, String name, BlockStmt body) {
            super(pos); this.exceptionType = exceptionType; this.name = name; this.body = body;
        }
    }

    public static class SynchronizedStmt extends Stmt {
        public final Expr lock;
        public final BlockStmt body;
        public SynchronizedStmt(SourcePos pos, Expr lock, BlockStmt body) {
            super(pos); this.lock = lock; this.body = body;
        }
    }

    /** Fallback for statements the parser cannot structure. */
    public static class RawStmt extends Stmt {
        public final String raw;
        public RawStmt(SourcePos pos, String raw) { super(pos); this.raw = raw; }
    }

    // =========================================================================
    // Expressions
    // =========================================================================

    public abstract static class Expr extends Node {
        protected Expr(SourcePos pos) { super(pos); }
    }

    public static class NameExpr extends Expr {
        public final String name;
        public NameExpr(SourcePos pos, String name) { super(pos); this.name = name; }
    }

    /** Covers int, float, string, char, boolean literals and {@code null}. */
    public static class LiteralExpr extends Expr {
        public final String raw;
        public LiteralExpr(SourcePos pos, String raw) { super(pos); this.raw = raw; }
    }

    public static class FieldAccessExpr extends Expr {
        public final Expr target;
        public final String field;
        public FieldAccessExpr(SourcePos pos, Expr target, String field) {
            super(pos); this.target = target; this.field = field;
        }
    }

    public static class MethodCallExpr extends Expr {
        public final Expr target;    // nullable → unqualified call
        public final String name;
        public final List<Expr> args;
        public MethodCallExpr(SourcePos pos, Expr target, String name, List<Expr> args) {
            super(pos); this.target = target; this.name = name; this.args = List.copyOf(args);
        }
    }

    public static class ArrayAccessExpr extends Expr {
        public final Expr array, index;
        public ArrayAccessExpr(SourcePos pos, Expr array, Expr index) {
            super(pos); this.array = array; this.index = index;
        }
    }

    public static class AssignExpr extends Expr {
        public final Expr target;
        public final String op;   // "=", "+=", "-=", "*=", etc.
        public final Expr value;
        public AssignExpr(SourcePos pos, Expr target, String op, Expr value) {
            super(pos); this.target = target; this.op = op; this.value = value;
        }
    }

    public static class BinaryExpr extends Expr {
        public final Expr left;
        public final String op;
        public final Expr right;
        public BinaryExpr(SourcePos pos, Expr left, String op, Expr right) {
            super(pos); this.left = left; this.op = op; this.right = right;
        }
    }

    public static class UnaryExpr extends Expr {
        public final String op;
        public final Expr expr;
        public final boolean postfix;
        public UnaryExpr(SourcePos pos, String op, Expr expr, boolean postfix) {
            super(pos); this.op = op; this.expr = expr; this.postfix = postfix;
        }
    }

    public static class CastExpr extends Expr {
        public final String type;
        public final Expr expr;
        public CastExpr(SourcePos pos, String type, Expr expr) {
            super(pos); this.type = type; this.expr = expr;
        }
    }

    public static class NewObjectExpr extends Expr {
        public final String type;
        public final List<Expr> args;
        public NewObjectExpr(SourcePos pos, String type, List<Expr> args) {
            super(pos); this.type = type; this.args = List.copyOf(args);
        }
    }

    public static class NewArrayExpr extends Expr {
        public final String elementType;
        public final List<Expr> dimensions;
        public NewArrayExpr(SourcePos pos, String elementType, List<Expr> dimensions) {
            super(pos); this.elementType = elementType; this.dimensions = List.copyOf(dimensions);
        }
    }

    public static class ArrayInitExpr extends Expr {
        public final List<Expr> elements;
        public ArrayInitExpr(SourcePos pos, List<Expr> elements) {
            super(pos); this.elements = List.copyOf(elements);
        }
    }

    public static class TernaryExpr extends Expr {
        public final Expr cond, then, else_;
        public TernaryExpr(SourcePos pos, Expr cond, Expr then, Expr else_) {
            super(pos); this.cond = cond; this.then = then; this.else_ = else_;
        }
    }

    public static class InstanceofExpr extends Expr {
        public final Expr expr;
        public final String type;
        public final String bindingName; // nullable — Java 16+ pattern variable
        public InstanceofExpr(SourcePos pos, Expr expr, String type, String bindingName) {
            super(pos); this.expr = expr; this.type = type; this.bindingName = bindingName;
        }
    }

    /** Explicit parentheses around an expression, preserved for re-emission. */
    public static class ParenExpr extends Expr {
        public final Expr expr;
        public ParenExpr(SourcePos pos, Expr expr) { super(pos); this.expr = expr; }
    }

    public static class LambdaExpr extends Expr {
        public final List<Param> params;
        public final Object body; // Expr or BlockStmt
        public LambdaExpr(SourcePos pos, List<Param> params, Object body) {
            super(pos); this.params = List.copyOf(params); this.body = body;
        }
    }

    /** Fallback for expressions the parser cannot structure. */
    public static class RawExpr extends Expr {
        public final String raw;
        public RawExpr(SourcePos pos, String raw) { super(pos); this.raw = raw; }
    }
}
