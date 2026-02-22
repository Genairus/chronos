package com.genairus.chronos.compiler;

import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that an unquoted duration literal (DURATION token) is stored as
 * {@link TraitValue.StringValue} in the IR after going through the full
 * parse → lower → build-skeleton pipeline.
 */
class DurationIrConversionTest {

    @Test
    void durationVal_5m_storedAsStringValueInIr() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @timeout(duration: 5m)
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """, "<test>");

        IrModel model = result.modelOrNull();
        assertNotNull(model, "Should compile; errors: " + result.diagnostics());
        JourneyDef journey = model.journeys().get(0);
        TraitApplication trait = journey.traits().stream()
                .filter(t -> "timeout".equals(t.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("@timeout trait not found"));

        Optional<TraitValue> durOpt = trait.namedValue("duration");
        assertTrue(durOpt.isPresent(), "duration arg should be present");

        TraitValue val = durOpt.get();
        assertInstanceOf(TraitValue.StringValue.class, val,
                "DurationVal should be stored as StringValue in IR");
        assertEquals("5m", ((TraitValue.StringValue) val).value());
    }

    @Test
    void durationVal_500ms_storedAsStringValueInIr() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @timeout(duration: 500ms)
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """, "<test>");

        IrModel model = result.modelOrNull();
        assertNotNull(model, "Should compile; errors: " + result.diagnostics());
        JourneyDef journey = model.journeys().get(0);
        Optional<TraitValue> durOpt = journey.traits().stream()
                .filter(t -> "timeout".equals(t.name()))
                .findFirst()
                .flatMap(t -> t.namedValue("duration"));

        assertTrue(durOpt.isPresent());
        assertInstanceOf(TraitValue.StringValue.class, durOpt.get());
        assertEquals("500ms", ((TraitValue.StringValue) durOpt.get()).value());
    }
}
