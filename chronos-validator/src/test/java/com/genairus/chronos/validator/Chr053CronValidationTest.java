package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-053: @schedule cron must be a valid 5-field cron expression.
 */
class Chr053CronValidationTest {

    private static boolean hasChr053(com.genairus.chronos.compiler.CompileResult result) {
        return result.diagnostics().stream().anyMatch(d -> "CHR-053".equals(d.code()));
    }

    private static com.genairus.chronos.compiler.CompileResult compileSchedule(String scheduleArg) {
        return new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @schedule(%s)
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """.formatted(scheduleArg), "<test>");
    }

    // ── Valid cron expressions ─────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "cron: \"0 0 * * *\"",
        "cron: \"*/5 * * * *\"",
        "cron: \"0 9 * * 1-5\""
    })
    void validCronExpressions_doNotTriggerChr053(String cronArg) {
        var result = compileSchedule(cronArg);
        assertFalse(hasChr053(result),
                cronArg + " should be valid; got: " + result.diagnostics());
    }

    @Test
    void positionalCronArg_valid() {
        var result = compileSchedule("\"0 0 * * *\"");
        assertFalse(hasChr053(result),
                "Positional cron should be valid; got: " + result.diagnostics());
    }

    // ── Invalid cron expressions → CHR-053 ────────────────────────────────────

    @Test
    void invalidCron_notACron_triggersChr053() {
        var result = compileSchedule("cron: \"not a cron\"");
        assertTrue(hasChr053(result),
                "'not a cron' should trigger CHR-053; got: " + result.diagnostics());
        assertTrue(result.diagnostics().stream()
                .filter(d -> "CHR-053".equals(d.code()))
                .anyMatch(d -> d.message().contains("not a cron")),
                "CHR-053 message should contain the invalid expression");
    }

    @Test
    void invalidCron_fourFields_triggersChr053() {
        var result = compileSchedule("cron: \"0 0 * *\"");
        assertTrue(hasChr053(result),
                "4-field cron should trigger CHR-053; got: " + result.diagnostics());
    }
}
