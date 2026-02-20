package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.parser.ChronosParseException;
import com.genairus.chronos.parser.ChronosParserFacade;
import com.genairus.chronos.syntax.SyntaxModel;

/**
 * Pass 0+1: Lexes and parses the source text, then lowers the ANTLR parse tree
 * into the {@link SyntaxModel} DTO layer via {@link ChronosParserFacade}.
 *
 * <p>On a syntax error the phase reports a {@code CHR-PARSE} diagnostic for
 * each parse error and returns {@code null}, signalling to the compiler that
 * the pipeline should halt.
 */
public final class ParseAndLowerPhase implements ResolverPhase<String, SyntaxModel> {

    private final String sourceName;

    public ParseAndLowerPhase(String sourceName) {
        this.sourceName = sourceName;
    }

    @Override
    public SyntaxModel execute(String sourceText, ResolverContext ctx) {
        try {
            return new ChronosParserFacade().parse(sourceText, sourceName);
        } catch (ChronosParseException e) {
            for (var err : e.errors()) {
                // ParseError.column is 0-based; Span.at() expects 1-based.
                Span span = Span.at(err.sourceName(), err.line(), err.column() + 1);
                ctx.diagnostics().error("CHR-PARSE", err.message(), span);
            }
            return null;
        }
    }
}
