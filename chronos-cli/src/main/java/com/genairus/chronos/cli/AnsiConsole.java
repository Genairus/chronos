package com.genairus.chronos.cli;

import java.io.PrintWriter;

/**
 * Thin console wrapper that emits ANSI colour codes when the terminal supports them.
 *
 * <p>Colour detection priority:
 * <ol>
 *   <li>{@code --no-color} → always plain</li>
 *   <li>{@code --force-color} → always coloured</li>
 *   <li>auto: coloured when {@code System.console() != null}</li>
 * </ol>
 */
public final class AnsiConsole {

    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN  = "\u001B[32m";
    private static final String RESET  = "\u001B[0m";

    private final PrintWriter out;
    private final PrintWriter err;
    private final boolean ansi;
    private final boolean debug;

    public AnsiConsole(PrintWriter out, PrintWriter err,
                       boolean forceColor, boolean noColor, boolean debug) {
        this.out   = out;
        this.err   = err;
        this.debug = debug;
        if (noColor) {
            this.ansi = false;
        } else if (forceColor) {
            this.ansi = true;
        } else {
            this.ansi = System.console() != null;
        }
    }

    /** Prints a red error message to stderr. */
    public void error(String msg) {
        err.println(ansi ? RED + msg + RESET : msg);
        err.flush();
    }

    /** Prints a yellow warning message to stderr. */
    public void warning(String msg) {
        err.println(ansi ? YELLOW + msg + RESET : msg);
        err.flush();
    }

    /** Prints a green success message to stdout. */
    public void success(String msg) {
        out.println(ansi ? GREEN + msg + RESET : msg);
        out.flush();
    }

    /** Prints a plain message to stdout. */
    public void plain(String msg) {
        out.println(msg);
        out.flush();
    }

    /** Prints a green addition line to stdout (used by diff). */
    public void added(String msg) {
        out.println(ansi ? GREEN + msg + RESET : msg);
        out.flush();
    }

    /** Prints a red removal line to stdout (used by diff). */
    public void removed(String msg) {
        out.println(ansi ? RED + msg + RESET : msg);
        out.flush();
    }

    /** Prints a yellow change line to stdout (used by diff). */
    public void changed(String msg) {
        out.println(ansi ? YELLOW + msg + RESET : msg);
        out.flush();
    }

    /**
     * Prints the exception: full stack trace when {@code --debug} is active,
     * otherwise just the error message.
     */
    public void exception(Throwable t) {
        if (debug) {
            t.printStackTrace(err);
            err.flush();
        } else {
            error(t.getMessage() != null ? t.getMessage() : t.getClass().getName());
        }
    }
}
