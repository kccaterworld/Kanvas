package kanvas.preprocess.transform;

import kanvas.preprocess.ast.KanvasSyntaxTree.*;
import kanvas.preprocess.source.PreprocessException;
import kanvas.preprocess.source.SourceSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns a parsed .kvs file into the Java compilation unit written to {@code build/generated}.
 *
 * <p>The file's members become the body of a public class extending {@code KanvasScript}:
 * <ul>
 *   <li>{@code import kanvas.gui;} is a directive, not a Java import, and is removed;</li>
 *   <li>top-level methods without an access modifier become {@code public} so they override
 *       the {@code KanvasScript} hooks;</li>
 *   <li>top-level types become member types, so a {@code class} can call sketch functions such
 *       as {@code ellipse()} the way it would in Processing;</li>
 *   <li>missing lifecycle hooks get empty bodies, and a {@code main} method starts the sketch.</li>
 * </ul>
 * Nodes created here carry {@link SourceSpan#SYNTHETIC}.
 */
public final class SketchTransformer {
    private static final SourceSpan SYNTHETIC = SourceSpan.SYNTHETIC;
    private static final String SCRIPT_CLASS = "KanvasScript";
    private static final String SCRIPT_IMPORT = "kanvas.gui." + SCRIPT_CLASS;

    /** {@code import kanvas.gui;} selects the sketch base class; it is not a Java import. */
    private static final String GUI_DIRECTIVE = "kanvas.gui";
    /** The kanvas.tui library was removed; importing it is reported instead of generating broken code. */
    private static final String REMOVED_TUI_DIRECTIVE = "kanvas.tui";

    private static final List<String> HOOKS = List.of(
        "setup", "draw", "mousePressed", "mouseReleased", "mouseClicked",
        "mouseDragged", "mouseWheel", "keyPressed", "keyReleased", "keyTyped");

    private SketchTransformer() {}

    /** Wraps {@code sketch} in {@code public class className extends KanvasScript} in {@code packageName}. */
    public static CompilationUnit transform(CompilationUnit sketch, String packageName, String className)
            throws PreprocessException {
        List<ImportDecl> imports = new ArrayList<>();
        for (ImportDecl imp : sketch.imports()) {
            if (isDirective(imp, REMOVED_TUI_DIRECTIVE))
                throw new PreprocessException("The kanvas.tui library has been removed; delete 'import kanvas.tui;'", imp.pos());
            if (!isDirective(imp, GUI_DIRECTIVE)) imports.add(imp);
        }
        imports.add(new ImportDecl(SYNTHETIC, false, SCRIPT_IMPORT, false));

        List<Member> body = new ArrayList<>();
        for (Member m : sketch.members())
            body.add(m instanceof MethodDecl method && !method.hasAccessModifier() ? makePublic(method) : m);

        Set<String> defined = sketch.methods().stream().map(MethodDecl::name).collect(Collectors.toSet());
        for (String hook : HOOKS)
            if (!defined.contains(hook)) body.add(emptyHook(hook));
        body.add(mainMethod(className));

        ClassDecl sketchClass = new ClassDecl(SYNTHETIC, keywords("public"), className, List.of(),
                type(SCRIPT_CLASS, 0), List.of(), List.of(), body);
        PackageDecl packageDecl = packageName == null || packageName.isBlank()
                ? null : new PackageDecl(SYNTHETIC, List.of(), packageName);
        return new CompilationUnit(SYNTHETIC, packageDecl, imports, List.of(sketchClass));
    }

    private static boolean isDirective(ImportDecl imp, String name) {
        return !imp.isStatic() && !imp.onDemand() && imp.name().equals(name);
    }

    /** Inserts {@code public} before the first keyword modifier, after any leading annotations. */
    private static MethodDecl makePublic(MethodDecl m) {
        List<Modifier> items = new ArrayList<>(m.modifiers().items());
        int firstKeyword = 0;
        while (firstKeyword < items.size() && items.get(firstKeyword) instanceof Annotation) firstKeyword++;
        items.add(firstKeyword, new KeywordModifier(SYNTHETIC, "public"));
        return new MethodDecl(m.span(), new Modifiers(items), m.typeParams(), m.returnType(), m.name(), m.params(),
                m.extraDims(), m.throwsTypes(), m.body(), m.defaultValue());
    }

    /** {@code public void name() {}} */
    private static MethodDecl emptyHook(String name) {
        return new MethodDecl(SYNTHETIC, keywords("public"), List.of(), type("void", 0), name, List.of(), 0,
                List.of(), new BlockStmt(SYNTHETIC, List.of()), null);
    }

    /** {@code public static void main(String[] args) { ClassName instance = new ClassName(); instance.start(); }} */
    private static MethodDecl mainMethod(String className) {
        Param args = new Param(SYNTHETIC, Modifiers.NONE, type("String", 1), false, "args", 0);
        Expr newSketch = new NewObjectExpr(SYNTHETIC, null, List.of(), type(className, 0), List.of(), null);
        Stmt declare = new LocalVarStmt(SYNTHETIC, Modifiers.NONE, type(className, 0),
                List.of(new VarDeclarator(SYNTHETIC, "instance", 0, newSketch)));
        Stmt start = new ExprStmt(SYNTHETIC,
                new MethodCallExpr(SYNTHETIC, new NameExpr(SYNTHETIC, "instance"), List.of(), "start", List.of()));
        return new MethodDecl(SYNTHETIC, keywords("public", "static"), List.of(), type("void", 0), "main",
                List.of(args), 0, List.of(), new BlockStmt(SYNTHETIC, List.of(declare, start)), null);
    }

    private static Modifiers keywords(String... keywords) {
        List<Modifier> items = new ArrayList<>();
        for (String k : keywords) items.add(new KeywordModifier(SYNTHETIC, k));
        return new Modifiers(items);
    }

    private static TypeRef type(String name, int dims) {
        return new TypeRef(SYNTHETIC, List.of(), List.of(new TypeName(List.of(), name, null)), dims);
    }
}
