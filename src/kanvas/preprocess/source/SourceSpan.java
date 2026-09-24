package kanvas.preprocess.source;

/** The source range a node or token covers. {@code end} is exclusive: the position just after it. */
public record SourceSpan(SourcePos start, SourcePos end) {

    /** The span of code the preprocessor generates itself; line 0 means it has no place in the source. */
    public static final SourceSpan SYNTHETIC = new SourceSpan(new SourcePos(0, 0, 0), new SourcePos(0, 0, 0));

    public boolean isSynthetic() { return start.line() == 0; }

    public String text(String source) { return source.substring(start.offset(), end.offset()); }

    public boolean contains(SourceSpan other) {
        return start.offset() <= other.start.offset() && other.end.offset() <= end.offset();
    }

    @Override public String toString() { return start + "-" + end; }
}
