package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-052: @ttl action must be one of: delete, archive, notify.
 */
class Chr052TtlActionTest {

    private static boolean hasChr052(com.genairus.chronos.compiler.CompileResult result) {
        return result.diagnostics().stream().anyMatch(d -> "CHR-052".equals(d.code()));
    }

    private static com.genairus.chronos.compiler.CompileResult compileTtl(String actionArg) {
        return new ChronosCompiler().compile("""
                namespace com.example
                @ttl(duration: 7d, %s)
                entity Order { id: String }
                """.formatted(actionArg), "<test>");
    }

    // ── Valid actions ──────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"delete", "archive", "notify"})
    void validActions_doNotTriggerChr052(String action) {
        var result = compileTtl("action: \"" + action + "\"");
        assertFalse(hasChr052(result),
                "action \"" + action + "\" should be valid; got: " + result.diagnostics());
    }

    @Test
    void positionalTtlAction_archive_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                @ttl(7d, "archive")
                entity Order { id: String }
                """, "<test>");
        assertFalse(hasChr052(result),
                "Positional action 'archive' should be valid; got: " + result.diagnostics());
    }

    // ── Invalid actions → CHR-052 ─────────────────────────────────────────────

    @Test
    void invalidAction_purge_triggersChr052() {
        var result = compileTtl("action: \"purge\"");
        assertTrue(hasChr052(result),
                "action 'purge' should trigger CHR-052; got: " + result.diagnostics());
        assertTrue(result.diagnostics().stream()
                .filter(d -> "CHR-052".equals(d.code()))
                .anyMatch(d -> d.message().contains("purge")),
                "CHR-052 message should contain 'purge'");
    }

    @Test
    void invalidAction_DELETE_uppercase_triggersChr052() {
        var result = compileTtl("action: \"DELETE\"");
        assertTrue(hasChr052(result),
                "action 'DELETE' (uppercase) should trigger CHR-052 (case-sensitive); got: " + result.diagnostics());
    }
}
