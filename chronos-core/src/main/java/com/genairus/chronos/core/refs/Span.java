package com.genairus.chronos.core.refs;

/**
 * A half-open source range {@code [start, end)} pinpointing a token or construct
 * in a {@code .chronos} source file.
 *
 * <p>Both start and end positions are 1-based line / 1-based column pairs.
 * When only a single point is known (e.g. an inserted synthetic node), set
 * {@code endLine == startLine} and {@code endCol == startCol}.
 *
 * <p>Spans are carried by every Syntax DTO so that diagnostics (CHR-xxx) can
 * report precise source locations.  They are stripped from the Finalized IR
 * because generators never need them.
 *
 * @param sourceName  path or display name of the source file (never {@code null})
 * @param startLine   1-based line of the first character of the construct
 * @param startCol    1-based column of the first character
 * @param endLine     1-based line of the character just past the construct
 * @param endCol      1-based column of the character just past the construct
 */
public record Span(
        String sourceName,
        int startLine,
        int startCol,
        int endLine,
        int endCol) {

    /** Sentinel used when no source information is available. */
    public static final Span UNKNOWN = new Span("<unknown>", 0, 0, 0, 0);

    /**
     * Creates a span that marks a single point (zero-length) at the start of
     * {@code sourceName}.  Useful for synthetic nodes that must carry a span but
     * have no meaningful source position.
     */
    public static Span unknown(String sourceName) {
        return new Span(sourceName, 0, 0, 0, 0);
    }

    /**
     * Creates a span covering exactly one line/column position (a single point).
     * Convenient for tokens where only a start position is available.
     */
    public static Span at(String sourceName, int line, int col) {
        return new Span(sourceName, line, col, line, col);
    }

    /**
     * Returns {@code true} if this span carries no real source information
     * (i.e. it was produced by {@link #UNKNOWN} or {@link #unknown(String)}).
     */
    public boolean isUnknown() {
        return startLine == 0 && startCol == 0 && endLine == 0 && endCol == 0;
    }

    /**
     * Human-readable form: {@code "file:startLine:startCol"}.
     * Used in diagnostic messages.
     */
    @Override
    public String toString() {
        if (isUnknown()) {
            return sourceName + ":<unknown>";
        }
        return sourceName + ":" + startLine + ":" + startCol;
    }
}
