package com.genairus.chronos.model;

/**
 * Pinpoints exactly where in source a token or construct was found.
 * Carried by every model node so validators and the CLI can emit
 * precise error messages: {@code ERROR [CHR-001] file:12  message}.
 *
 * @param file   path to the .chronos source file (may be relative)
 * @param line   1-based line number
 * @param column 1-based column number
 */
public record SourceLocation(String file, int line, int column) {

    /** Convenience factory for positions where only the file matters. */
    public static SourceLocation of(String file, int line, int column) {
        return new SourceLocation(file, line, column);
    }

    /** A sentinel used when no source information is available. */
    public static SourceLocation unknown() {
        return new SourceLocation("<unknown>", 0, 0);
    }

    @Override
    public String toString() {
        return file + ":" + line;
    }
}
