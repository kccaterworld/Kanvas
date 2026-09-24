package kanvas.preprocess.source;

/** A point in a source file: 0-based {@code offset}, 1-based {@code line} and {@code column}. */
public record SourcePos(int offset, int line, int column) {
    @Override public String toString() { return line + ":" + column; }
}
