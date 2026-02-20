package com.genairus.chronos.parser;

import com.genairus.chronos.parser.generated.ChronosLexer;
import com.genairus.chronos.parser.generated.ChronosParser;
import com.genairus.chronos.parser.lowering.LoweringVisitor;
import com.genairus.chronos.syntax.SyntaxModel;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Public entry point for parsing {@code .chronos} source text into a
 * {@link SyntaxModel} (the Syntax DTO tree).
 *
 * <p>Implements Pass 0 (ANTLR parse) + Pass 1 (Lower) of the compiler pipeline.
 * No symbol resolution, IR construction, or validation is performed here.
 *
 * <p>Usage:
 * <pre>{@code
 * SyntaxModel model = new ChronosParserFacade().parse(sourceText, "my-file.chronos");
 * }</pre>
 *
 * @throws ChronosParseException if the source contains any syntax errors
 */
public final class ChronosParserFacade {

    public ChronosParserFacade() {}

    /**
     * Parses Chronos source text and returns the lowered {@link SyntaxModel}.
     *
     * @param sourceText the raw {@code .chronos} source
     * @param sourceName logical name embedded in {@link com.genairus.chronos.core.refs.Span}s
     *                   and error messages (e.g. a file path or {@code "<inline>"})
     * @return the Syntax DTO tree; never {@code null}
     * @throws ChronosParseException if the source contains syntax errors
     */
    public SyntaxModel parse(String sourceText, String sourceName) {
        var errors   = new ArrayList<ParseError>();
        var listener = new CollectingErrorListener(sourceName, errors);

        CharStream stream = CharStreams.fromString(sourceText, sourceName);

        var lexer = new ChronosLexer(stream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new ChronosParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        ChronosParser.ModelContext tree = parser.model();

        if (!errors.isEmpty()) {
            throw new ChronosParseException(errors);
        }

        var visitor = new LoweringVisitor(tokenStream, sourceName);
        return (SyntaxModel) visitor.visitModel(tree);
    }

    // ── Error listener ─────────────────────────────────────────────────────────

    private static final class CollectingErrorListener extends BaseErrorListener {

        private final String       sourceName;
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
