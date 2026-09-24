package kanvas.preprocess.ast;

import kanvas.preprocess.source.SourcePos;
import kanvas.preprocess.source.SourceSpan;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Syntax tree for a .kvs file, and for the Java file the transform stage turns it into.
 *
 * <p>Every node is an immutable record carrying a {@link SourceSpan}; nodes the preprocessor
 * creates itself carry {@link SourceSpan#SYNTHETIC}. The node families ({@link Member},
 * {@link Stmt}, {@link Expr}, {@link TypeArg}, ...) are sealed, so a {@code switch} over one of
 * them is checked for exhaustiveness by the compiler. {@link #children} and {@link #walk} cover
 * generic traversal.
 *
 * <p>All node types live in this one file because a sealed interface without a {@code permits}
 * clause may only be implemented in its own compilation unit.
 *
 * <p>The tree is purely syntactic: it records what was written, not what it means.
 */
public final class KanvasSyntaxTree {
    private KanvasSyntaxTree() {}

    public sealed interface Node {
        SourceSpan span();
        default SourcePos pos() { return span().start(); }
    }

    // =========================================================================
    // Traversal
    // =========================================================================

    /** The direct child nodes of {@code node}, in record-component order. Works for every node type. */
    public static List<Node> children(Node node) {
        List<Node> out = new ArrayList<>();
        for (RecordComponent c : node.getClass().getRecordComponents()) collect(componentValue(c, node), out);
        return out;
    }

    /** Visits {@code node} and all of its descendants, parents before children. */
    public static void walk(Node node, Consumer<Node> action) {
        action.accept(node);
        for (Node child : children(node)) walk(child, action);
    }

    private static void collect(Object value, List<Node> out) {
        if (value instanceof Node n) out.add(n);
        else if (value instanceof List<?> list) for (Object item : list) collect(item, out);
        // Non-node carriers such as Modifiers and TypeName hold nodes of their own.
        else if (value instanceof Record r && !(value instanceof SourceSpan))
            for (RecordComponent c : r.getClass().getRecordComponents()) collect(componentValue(c, r), out);
    }

    private static Object componentValue(RecordComponent component, Object target) {
        try { return component.getAccessor().invoke(target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static <T> List<T> ofType(List<?> list, Class<T> type) {
        return list.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static <T> List<T> copyOrNull(List<T> list) { return list == null ? null : List.copyOf(list); }

    // =========================================================================
    // File
    // =========================================================================

    /**
     * A whole source file: an optional package, imports, and members. For a .kvs file the members
     * are what a class body can hold; for a generated Java file they are its top-level types.
     */
    public record CompilationUnit(SourceSpan span, PackageDecl packageDecl, List<ImportDecl> imports,
                                  List<Member> members) implements Node {
        public CompilationUnit { imports = List.copyOf(imports); members = List.copyOf(members); }
        public List<FieldDecl> fields()   { return ofType(members, FieldDecl.class); }
        public List<MethodDecl> methods() { return ofType(members, MethodDecl.class); }
        public List<TypeDecl> types()     { return ofType(members, TypeDecl.class); }
    }

    public record PackageDecl(SourceSpan span, List<Annotation> annotations, String name) implements Node {
        public PackageDecl { annotations = List.copyOf(annotations); }
    }

    /** {@code import [static] name[.*];} — {@code name} never includes the trailing {@code .*}. */
    public record ImportDecl(SourceSpan span, boolean isStatic, String name, boolean onDemand) implements Node {}

    // =========================================================================
    // Annotations and modifiers
    // =========================================================================

    /** A value inside an annotation: an expression, a nested annotation, or {@code {...}}. */
    public sealed interface ElementValue extends Node {}

    /** An entry in a modifier list: an annotation or a keyword such as {@code public}. */
    public sealed interface Modifier extends Node {}

    /** {@code @Name}, {@code @Name(value)} or {@code @Name(key = value, ...)}. */
    public record Annotation(SourceSpan span, String name, List<ElementPair> args) implements ElementValue, Modifier {
        /** {@code args} is null for a marker annotation written without parentheses. */
        public Annotation { args = copyOrNull(args); }
    }

    /** One annotation argument. {@code name} is null for the single-value form {@code @Name(value)}. */
    public record ElementPair(SourceSpan span, String name, ElementValue value) implements Node {}

    public record ElementArray(SourceSpan span, List<ElementValue> values) implements ElementValue {
        public ElementArray { values = List.copyOf(values); }
    }

    /** A modifier keyword: public, static, final, sealed, non-sealed, default, ... */
    public record KeywordModifier(SourceSpan span, String keyword) implements Modifier {}

    /** Annotations and keyword modifiers in source order. Not a node itself; it has no span when empty. */
    public record Modifiers(List<Modifier> items) {
        public static final Modifiers NONE = new Modifiers(List.of());
        public Modifiers { items = List.copyOf(items); }
        public List<Annotation> annotations() { return ofType(items, Annotation.class); }
        public List<String> keywords() { return ofType(items, KeywordModifier.class).stream().map(KeywordModifier::keyword).toList(); }
        public boolean has(String keyword) { return keywords().contains(keyword); }
        public boolean hasAccessModifier() { return has("public") || has("protected") || has("private"); }
        public boolean isEmpty() { return items.isEmpty(); }
    }

    // =========================================================================
    // Types
    // =========================================================================

    /** Something that can appear inside {@code <...>}: a type or a wildcard. */
    public sealed interface TypeArg extends Node {}

    /**
     * A type as written: {@code int}, {@code String[]}, {@code java.util.Map<K, List<V>>}.
     * Each dotted segment is a {@link TypeName}; {@code dims} counts trailing {@code []}.
     */
    public record TypeRef(SourceSpan span, List<Annotation> annotations, List<TypeName> names, int dims)
            implements TypeArg, MethodRefTarget {
        public TypeRef { annotations = List.copyOf(annotations); names = List.copyOf(names); }
        /** The dotted name without type arguments or dims, e.g. {@code java.util.Map}. */
        public String name() { return String.join(".", names.stream().map(TypeName::name).toList()); }
    }

    /** One segment of a type name. {@code typeArgs} is null when absent and empty for the diamond {@code <>}. */
    public record TypeName(List<Annotation> annotations, String name, List<TypeArg> typeArgs) {
        public TypeName { annotations = List.copyOf(annotations); typeArgs = copyOrNull(typeArgs); }
    }

    /** {@code ?}, {@code ? extends T} or {@code ? super T}. {@code boundType} is null when unbounded. */
    public record WildcardType(SourceSpan span, List<Annotation> annotations, Bound bound, TypeRef boundType)
            implements TypeArg {
        public enum Bound { NONE, EXTENDS, SUPER }
        public WildcardType { annotations = List.copyOf(annotations); }
    }

    /** {@code T} or {@code T extends A & B} in a type-parameter list. */
    public record TypeParam(SourceSpan span, List<Annotation> annotations, String name, List<TypeRef> bounds)
            implements Node {
        public TypeParam { annotations = List.copyOf(annotations); bounds = List.copyOf(bounds); }
    }

    // =========================================================================
    // Members
    // =========================================================================

    /** A member of a class body — or of the .kvs file itself, which is a class body. */
    public sealed interface Member extends Node {}

    /** A field declaration; {@code int a = 1, b[];} has two declarators. */
    public record FieldDecl(SourceSpan span, Modifiers modifiers, TypeRef type, List<VarDeclarator> declarators)
            implements Member {
        public FieldDecl { declarators = List.copyOf(declarators); }
    }

    /**
     * A method. {@code body} is null for abstract, native and interface methods.
     * {@code defaultValue} is set only for annotation elements ({@code String value() default "";}).
     * {@code extraDims} counts legacy {@code []} after the parameter list.
     */
    public record MethodDecl(SourceSpan span, Modifiers modifiers, List<TypeParam> typeParams, TypeRef returnType,
                             String name, List<Param> params, int extraDims, List<TypeRef> throwsTypes,
                             BlockStmt body, ElementValue defaultValue) implements Member {
        public MethodDecl {
            typeParams = List.copyOf(typeParams); params = List.copyOf(params); throwsTypes = List.copyOf(throwsTypes);
        }
        public boolean hasAccessModifier() { return modifiers.hasAccessModifier(); }
    }

    /** A constructor. {@code params} is null for a compact record constructor ({@code Point { ... }}). */
    public record ConstructorDecl(SourceSpan span, Modifiers modifiers, List<TypeParam> typeParams, String name,
                                  List<Param> params, List<TypeRef> throwsTypes, BlockStmt body) implements Member {
        public ConstructorDecl {
            typeParams = List.copyOf(typeParams); params = copyOrNull(params); throwsTypes = List.copyOf(throwsTypes);
        }
    }

    /** An instance {@code { ... }} or {@code static { ... }} initializer block. */
    public record InitializerDecl(SourceSpan span, boolean isStatic, BlockStmt body) implements Member {}

    /** A class, interface, enum, record or annotation type declaration. */
    public sealed interface TypeDecl extends Member {
        Modifiers modifiers();
        String name();
        List<Member> body();
    }

    public record ClassDecl(SourceSpan span, Modifiers modifiers, String name, List<TypeParam> typeParams,
                            TypeRef superclass, List<TypeRef> interfaces, List<TypeRef> permits,
                            List<Member> body) implements TypeDecl {
        public ClassDecl {
            typeParams = List.copyOf(typeParams); interfaces = List.copyOf(interfaces);
            permits = List.copyOf(permits); body = List.copyOf(body);
        }
    }

    public record InterfaceDecl(SourceSpan span, Modifiers modifiers, String name, List<TypeParam> typeParams,
                                List<TypeRef> superinterfaces, List<TypeRef> permits, List<Member> body)
            implements TypeDecl {
        public InterfaceDecl {
            typeParams = List.copyOf(typeParams); superinterfaces = List.copyOf(superinterfaces);
            permits = List.copyOf(permits); body = List.copyOf(body);
        }
    }

    public record EnumDecl(SourceSpan span, Modifiers modifiers, String name, List<TypeRef> interfaces,
                           List<EnumConstant> constants, List<Member> body) implements TypeDecl {
        public EnumDecl { interfaces = List.copyOf(interfaces); constants = List.copyOf(constants); body = List.copyOf(body); }
    }

    /** An enum constant. {@code args} is null without parentheses; {@code body} is null without a class body. */
    public record EnumConstant(SourceSpan span, List<Annotation> annotations, String name, List<Expr> args,
                               List<Member> body) implements Node {
        public EnumConstant { annotations = List.copyOf(annotations); args = copyOrNull(args); body = copyOrNull(body); }
    }

    public record RecordDecl(SourceSpan span, Modifiers modifiers, String name, List<TypeParam> typeParams,
                             List<Param> components, List<TypeRef> interfaces, List<Member> body) implements TypeDecl {
        public RecordDecl {
            typeParams = List.copyOf(typeParams); components = List.copyOf(components);
            interfaces = List.copyOf(interfaces); body = List.copyOf(body);
        }
    }

    /** {@code @interface Name { ... }} */
    public record AnnotationTypeDecl(SourceSpan span, Modifiers modifiers, String name, List<Member> body)
            implements TypeDecl {
        public AnnotationTypeDecl { body = List.copyOf(body); }
    }

    /**
     * A method, constructor, lambda or catch-style parameter, or a record component.
     * {@code type} is null for an inferred lambda parameter ({@code x -> ...}).
     */
    public record Param(SourceSpan span, Modifiers modifiers, TypeRef type, boolean varargs, String name,
                        int extraDims) implements Node {}

    /** One variable in a declaration: {@code name}, {@code name[] = init}, ... {@code init} may be null. */
    public record VarDeclarator(SourceSpan span, String name, int extraDims, Expr init) implements Node {}

    // =========================================================================
    // Statements
    // =========================================================================

    public sealed interface Stmt extends Node {}

    public record BlockStmt(SourceSpan span, List<Stmt> stmts) implements Stmt, LambdaBody {
        public BlockStmt { stmts = List.copyOf(stmts); }
    }

    /** A lone {@code ;}. */
    public record EmptyStmt(SourceSpan span) implements Stmt {}

    public record LocalVarStmt(SourceSpan span, Modifiers modifiers, TypeRef type, List<VarDeclarator> declarators)
            implements Stmt {
        public LocalVarStmt { declarators = List.copyOf(declarators); }
    }

    /** A class, interface, enum or record declared inside a method body. */
    public record LocalClassStmt(SourceSpan span, TypeDecl decl) implements Stmt {}

    public record ExprStmt(SourceSpan span, Expr expr) implements Stmt {}

    public record IfStmt(SourceSpan span, Expr cond, Stmt thenStmt, Stmt elseStmt) implements Stmt {}

    public record WhileStmt(SourceSpan span, Expr cond, Stmt body) implements Stmt {}

    public record DoWhileStmt(SourceSpan span, Stmt body, Expr cond) implements Stmt {}

    /** A classic for loop. {@code init} is a single {@link LocalVarStmt} or a list of {@link ExprStmt}s. */
    public record ForStmt(SourceSpan span, List<Stmt> init, Expr cond, List<Expr> update, Stmt body) implements Stmt {
        public ForStmt { init = List.copyOf(init); update = List.copyOf(update); }
    }

    public record ForEachStmt(SourceSpan span, Modifiers modifiers, TypeRef type, String name, Expr iterable,
                              Stmt body) implements Stmt {}

    public record ReturnStmt(SourceSpan span, Expr value) implements Stmt {}

    public record BreakStmt(SourceSpan span, String label) implements Stmt {}

    public record ContinueStmt(SourceSpan span, String label) implements Stmt {}

    /** {@code yield value;} inside a switch expression. */
    public record YieldStmt(SourceSpan span, Expr value) implements Stmt {}

    public record ThrowStmt(SourceSpan span, Expr expr) implements Stmt {}

    /** {@code try}, with optional resources, catch clauses and finally block. */
    public record TryStmt(SourceSpan span, List<Resource> resources, BlockStmt body, List<CatchClause> catches,
                          BlockStmt finallyBlock) implements Stmt {
        public TryStmt { resources = List.copyOf(resources); catches = List.copyOf(catches); }
    }

    /** A try-with-resources entry: a declaration, or an existing variable or field. */
    public sealed interface Resource extends Node {}

    public record ResourceDecl(SourceSpan span, Modifiers modifiers, TypeRef type, String name, Expr init)
            implements Resource {}

    public record ResourceRef(SourceSpan span, Expr expr) implements Resource {}

    /** {@code catch (A | B name) { ... }} */
    public record CatchClause(SourceSpan span, Modifiers modifiers, List<TypeRef> types, String name, BlockStmt body)
            implements Node {
        public CatchClause { types = List.copyOf(types); }
    }

    public record SwitchStmt(SourceSpan span, Expr selector, List<SwitchCase> cases) implements Stmt {
        public SwitchStmt { cases = List.copyOf(cases); }
    }

    public record SynchronizedStmt(SourceSpan span, Expr lock, BlockStmt body) implements Stmt {}

    public record LabeledStmt(SourceSpan span, String label, Stmt body) implements Stmt {}

    /** {@code assert cond;} or {@code assert cond : message;} */
    public record AssertStmt(SourceSpan span, Expr cond, Expr message) implements Stmt {}

    // =========================================================================
    // Switch cases and patterns
    // =========================================================================

    /**
     * One {@code case ...:} / {@code case ... ->} / {@code default} group.
     * With {@code arrow}, {@code body} holds exactly one statement: a block, a throw, or an
     * {@link ExprStmt} for {@code case X -> expr;}. Otherwise it holds the statements after the colon.
     */
    public record SwitchCase(SourceSpan span, List<CaseLabel> labels, Expr guard, boolean arrow, List<Stmt> body)
            implements Node {
        public SwitchCase { labels = List.copyOf(labels); body = List.copyOf(body); }
    }

    /** A case label: a constant expression, a pattern, or {@code default}. */
    public sealed interface CaseLabel extends Node {}

    public record DefaultLabel(SourceSpan span) implements CaseLabel {}

    public sealed interface Pattern extends CaseLabel {}

    /** {@code Type name}, as in {@code case Circle c} or {@code o instanceof String s}. */
    public record TypePattern(SourceSpan span, Modifiers modifiers, TypeRef type, String name) implements Pattern {}

    /** {@code Point(int x, var y)} */
    public record RecordPattern(SourceSpan span, TypeRef type, List<Pattern> components) implements Pattern {
        public RecordPattern { components = List.copyOf(components); }
    }

    // =========================================================================
    // Expressions
    // =========================================================================

    public sealed interface Expr extends LambdaBody, ElementValue, MethodRefTarget, CaseLabel {}

    /** The body of a lambda: an expression or a block. */
    public sealed interface LambdaBody extends Node {}

    /** The part before {@code ::} in a method reference: an expression or a type. */
    public sealed interface MethodRefTarget extends Node {}

    /** A simple name, or {@code this} / {@code super}. */
    public record NameExpr(SourceSpan span, String name) implements Expr {}

    public record LiteralExpr(SourceSpan span, Kind kind, String text) implements Expr {
        public enum Kind { INT, FLOAT, STRING, TEXT_BLOCK, CHAR, BOOLEAN, NULL }
    }

    /** Explicit parentheses, kept so printing reproduces the source. */
    public record ParenExpr(SourceSpan span, Expr expr) implements Expr {}

    public record FieldAccessExpr(SourceSpan span, Expr target, String name) implements Expr {}

    /** A call. {@code target} is null when unqualified; {@code typeArgs} is empty unless written ({@code a.<T>m()}). */
    public record MethodCallExpr(SourceSpan span, Expr target, List<TypeArg> typeArgs, String name, List<Expr> args)
            implements Expr {
        public MethodCallExpr { typeArgs = List.copyOf(typeArgs); args = List.copyOf(args); }
    }

    public record ArrayAccessExpr(SourceSpan span, Expr array, Expr index) implements Expr {}

    public record AssignExpr(SourceSpan span, Expr target, String op, Expr value) implements Expr {}

    public record BinaryExpr(SourceSpan span, Expr left, String op, Expr right) implements Expr {}

    public record UnaryExpr(SourceSpan span, String op, Expr expr, boolean postfix) implements Expr {}

    /** {@code (Type) expr}; more than one type for an intersection cast {@code (A & B) expr}. */
    public record CastExpr(SourceSpan span, List<TypeRef> types, Expr expr) implements Expr {
        public CastExpr { types = List.copyOf(types); }
    }

    /** {@code expr instanceof Type} or {@code expr instanceof Pattern}; exactly one of the two is set. */
    public record InstanceofExpr(SourceSpan span, Expr expr, TypeRef type, Pattern pattern) implements Expr {}

    public record TernaryExpr(SourceSpan span, Expr cond, Expr thenExpr, Expr elseExpr) implements Expr {}

    /**
     * {@code new Type(args)}, optionally qualified ({@code outer.new Inner()}) and optionally with an
     * anonymous class body ({@code body} is null when there is none).
     */
    public record NewObjectExpr(SourceSpan span, Expr outer, List<TypeArg> typeArgs, TypeRef type, List<Expr> args,
                                List<Member> body) implements Expr {
        public NewObjectExpr { typeArgs = List.copyOf(typeArgs); args = List.copyOf(args); body = copyOrNull(body); }
    }

    /**
     * {@code new T[a][b][]} or {@code new T[] {...}}: {@code dimExprs} are the sized dimensions,
     * {@code extraDims} the empty {@code []} after them, {@code init} the optional initializer.
     */
    public record NewArrayExpr(SourceSpan span, TypeRef elementType, List<Expr> dimExprs, int extraDims,
                               ArrayInitExpr init) implements Expr {
        public NewArrayExpr { dimExprs = List.copyOf(dimExprs); }
    }

    /** {@code {a, b, {c}}} */
    public record ArrayInitExpr(SourceSpan span, List<Expr> elements) implements Expr {
        public ArrayInitExpr { elements = List.copyOf(elements); }
    }

    /** {@code x -> ...} ({@code parenthesized} false) or {@code (a, b) -> ...}. */
    public record LambdaExpr(SourceSpan span, List<Param> params, boolean parenthesized, LambdaBody body)
            implements Expr {
        public LambdaExpr { params = List.copyOf(params); }
    }

    /** {@code target::name}; {@code name} is {@code "new"} for constructor references. */
    public record MethodRefExpr(SourceSpan span, MethodRefTarget target, List<TypeArg> typeArgs, String name)
            implements Expr {
        public MethodRefExpr { typeArgs = List.copyOf(typeArgs); }
    }

    /** {@code Type.class} */
    public record ClassLiteralExpr(SourceSpan span, TypeRef type) implements Expr {}

    public record SwitchExpr(SourceSpan span, Expr selector, List<SwitchCase> cases) implements Expr {
        public SwitchExpr { cases = List.copyOf(cases); }
    }
}
