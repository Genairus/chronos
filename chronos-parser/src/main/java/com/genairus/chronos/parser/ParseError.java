package com.genairus.chronos.parser;

/**
 * An ANTLR syntax error collected during parsing.
 *
 * @param sourceName the source file name (or {@code "<string>"} for in-memory input)
 * @param line       1-based line number
 * @param column     0-based character offset within the line
 * @param message    human-readable error description from ANTLR
 */
public record ParseError(String sourceName, int line, int column, String message) {

    @Override
    public String toString() {
        return sourceName + ":" + line + ":" + column + ": " + message;
    }
}
