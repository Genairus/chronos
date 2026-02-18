package com.genairus.chronos.parser;

import java.util.List;

/**
 * Thrown by {@link ChronosModelParser} when the source contains one or more
 * ANTLR syntax errors.
 */
public class ChronosParseException extends RuntimeException {

    private final List<ParseError> errors;

    public ChronosParseException(List<ParseError> errors) {
        super(buildMessage(errors));
        this.errors = List.copyOf(errors);
    }

    /** All collected syntax errors, in source order. */
    public List<ParseError> errors() {
        return errors;
    }

    private static String buildMessage(List<ParseError> errors) {
        if (errors.isEmpty()) return "Parse failed with no details";
        var sb = new StringBuilder("Parse failed with ")
                .append(errors.size())
                .append(errors.size() == 1 ? " error:" : " errors:");
        for (ParseError e : errors) {
            sb.append("\n  ").append(e);
        }
        return sb.toString();
    }
}
