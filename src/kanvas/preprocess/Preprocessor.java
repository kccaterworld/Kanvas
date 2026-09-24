package kanvas.preprocess;

import kanvas.preprocess.ast.KanvasSyntaxTree.CompilationUnit;
import kanvas.preprocess.generate.JavaPrinter;
import kanvas.preprocess.parse.Parser;
import kanvas.preprocess.source.PreprocessException;
import kanvas.preprocess.transform.SketchTransformer;

/**
 * The Kanvas-to-Java transpiler.
 *
 * <pre>
 * .kvs source → lex.Lexer → parse.Parser → ast (sketch) → transform.SketchTransformer
 *             → ast (Java class) → generate.JavaPrinter → Java source
 * </pre>
 *
 * {@link KanvasPreprocessor} runs this over every .kvs file in a project.
 */
public final class Preprocessor {

    public static final String DEFAULT_PACKAGE = "kanvas.generated";

    private Preprocessor() {}

    public static CompilationUnit parse(String source) throws PreprocessException {
        return new Parser(source).parse();
    }

    /** Transpiles into the file's declared package, or {@link #DEFAULT_PACKAGE} when it has none. */
    public static String transpile(String source, String className) throws PreprocessException {
        CompilationUnit sketch = parse(source);
        String packageName = sketch.packageDecl() != null ? sketch.packageDecl().name() : DEFAULT_PACKAGE;
        return generate(sketch, packageName, classNameFor(className));
    }

    public static String transpile(String source, String packageName, String className) throws PreprocessException {
        return generate(parse(source), packageName, classNameFor(className));
    }

    /** Wraps a parsed sketch in its generated class and prints the Java file. */
    public static String generate(CompilationUnit sketch, String packageName, String className) throws PreprocessException {
        return JavaPrinter.print(SketchTransformer.transform(sketch, packageName, className));
    }

    /** Turns a file name such as {@code my-sketch} into a class name such as {@code MySketch}. */
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
}
