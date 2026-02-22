package com.genairus.chronos.parser;

import com.genairus.chronos.syntax.SyntaxJourneyDecl;
import com.genairus.chronos.syntax.SyntaxTrait;
import com.genairus.chronos.syntax.SyntaxTraitValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DURATION lexer token and {@link SyntaxTraitValue.DurationVal} lowering.
 *
 * <p>Verifies that all supported duration suffixes parse without error and that the
 * syntax-layer lowers them to {@code DurationVal}.
 */
class DurationLiteralParseTest {

    private static final ChronosParserFacade FACADE = new ChronosParserFacade();

    // ── Duration suffix variations ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"500ms", "30s", "5m", "2h", "7d", "2w"})
    void durationSuffixes_parseWithoutError(String duration) {
        String src = """
                namespace com.example
                @timeout(duration: %s)
                journey PlaceOrder {}
                """.formatted(duration);
        assertDoesNotThrow(() -> FACADE.parse(src, "<test>"),
                "Duration literal '" + duration + "' should parse without error");
    }

    // ── Syntax DTO lowering ────────────────────────────────────────────────────

    @Test
    void durationLiteral_5m_loweredToDurationVal() {
        var model = FACADE.parse("""
                namespace com.example
                @timeout(duration: 5m)
                journey PlaceOrder {}
                """, "<test>");

        assertEquals(1, model.declarations().size());
        var journey = assertInstanceOf(SyntaxJourneyDecl.class, model.declarations().get(0));
        assertEquals(1, journey.traits().size());

        SyntaxTrait trait = journey.traits().get(0);
        assertEquals("timeout", trait.name());
        assertEquals(1, trait.args().size());

        var arg = trait.args().get(0);
        assertEquals("duration", arg.keyOrNull());
        assertInstanceOf(SyntaxTraitValue.DurationVal.class, arg.value(),
                "5m should lower to DurationVal, got: " + arg.value());
        assertEquals("5m", ((SyntaxTraitValue.DurationVal) arg.value()).text());
    }

    @Test
    void durationLiteral_500ms_loweredToDurationVal() {
        var model = FACADE.parse("""
                namespace com.example
                @slo(duration: 500ms)
                journey Foo {}
                """, "<test>");

        var journey = assertInstanceOf(SyntaxJourneyDecl.class, model.declarations().get(0));
        var arg = journey.traits().get(0).args().get(0);
        assertInstanceOf(SyntaxTraitValue.DurationVal.class, arg.value(),
                "500ms should lower to DurationVal");
        assertEquals("500ms", ((SyntaxTraitValue.DurationVal) arg.value()).text());
    }
}
