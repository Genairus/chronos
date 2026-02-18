package com.genairus.chronos.parser;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.generated.ChronosLexer;
import com.genairus.chronos.parser.generated.ChronosParser;
import org.antlr.v4.runtime.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Public entry point for parsing {@code .chronos} source text into a
 * {@link ChronosModel}.
 *
 * <p>All three overloads follow the same pipeline:
 * <ol>
 *   <li>Build an ANTLR {@link CharStream} from the input.</li>
 *   <li>Attach a {@link CollectingErrorListener} to capture syntax errors.</li>
 *   <li>Lex → parse → visit with {@link ChronosModelBuilder}.</li>
 *   <li>If any errors were collected, throw {@link ChronosParseException}.</li>
 * </ol>
 */
public final class ChronosModelParser {

    private ChronosModelParser() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Parse a {@code .chronos} file from disk.
     *
     * @param path the path to the source file
     * @return the constructed {@link ChronosModel}
     * @throws ChronosParseException if the source contains syntax errors
     * @throws IOException           if the file cannot be read
     */
    public static ChronosModel parseFile(Path path) throws IOException {
        CharStream stream = CharStreams.fromPath(path);
        return doParse(stream, path.toString());
    }

    /**
     * Parse Chronos source from a {@link String}.
     *
     * @param sourceName a logical name used in error messages (e.g. {@code "<inline>"})
     * @param source     the raw Chronos source text
     * @return the constructed {@link ChronosModel}
     * @throws ChronosParseException if the source contains syntax errors
     */
    public static ChronosModel parseString(String sourceName, String source) {
        CharStream stream = CharStreams.fromString(source, sourceName);
        return doParse(stream, sourceName);
    }

    /**
     * Parse Chronos source from an {@link InputStream}.
     *
     * @param sourceName a logical name used in error messages
     * @param input      the input stream (UTF-8 encoding assumed)
     * @return the constructed {@link ChronosModel}
     * @throws ChronosParseException if the source contains syntax errors
     * @throws IOException           if the stream cannot be read
     */
    public static ChronosModel parseStream(String sourceName, InputStream input) throws IOException {
        CharStream stream = CharStreams.fromStream(input);
        return doParse(stream, sourceName);
    }

    // ── Internal pipeline ──────────────────────────────────────────────────────

    private static ChronosModel doParse(CharStream stream, String sourceName) {
        var errors = new ArrayList<ParseError>();
        var listener = new CollectingErrorListener(sourceName, errors);

        // Lexer
        var lexer = new ChronosLexer(stream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        // Parser
        var tokenStream = new CommonTokenStream(lexer);
        var parser = new ChronosParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        // Parse
        ChronosParser.ModelContext tree = parser.model();

        if (!errors.isEmpty()) {
            throw new ChronosParseException(errors);
        }

        // Build model — tokenStream is now fully populated (parser consumed it)
        var builder = new ChronosModelBuilder(tokenStream, sourceName);
        return (ChronosModel) builder.visitModel(tree);
    }

    // ── Error listener ────────────────────────────────────────────────────────

    private static final class CollectingErrorListener extends BaseErrorListener {

        private final String sourceName;
        private final List<ParseError> errors;

        CollectingErrorListener(String sourceName, List<ParseError> errors) {
            this.sourceName = sourceName;
            this.errors     = errors;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            errors.add(new ParseError(sourceName, line, charPositionInLine, msg));
        }
    }
}
