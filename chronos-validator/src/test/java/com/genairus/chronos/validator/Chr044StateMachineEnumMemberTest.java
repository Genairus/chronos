package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-044 and CHR-045: Statemachine state/enum member compatibility.
 *
 * <ul>
 *   <li>CHR-044 ERROR: A state declared in the statemachine is not a member of the bound enum.
 *   <li>CHR-045 WARNING: A bound enum member is not covered by any declared state in the statemachine.
 * </ul>
 *
 * <p>Policy: statemachine states must form a <em>subset</em> of the bound enum's members.
 * Having states not present in the enum is always an error; uncovered enum members are warned.
 */
class Chr044StateMachineEnumMemberTest {

    // ── Shared fixture helper ─────────────────────────────────────────────────

    /**
     * Builds a compilable statemachine model.
     * The enum always declares 5 members: PENDING, PAID, SHIPPED, DELIVERED, CANCELLED.
     */
    private static String model(String statesBlock, String initialState,
                                 String terminalBlock, String transitionsBlock) {
        return """
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                    DELIVERED
                    CANCELLED
                }

                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [%s]
                    initial: %s
                    terminal: [%s]
                    transitions: [%s]
                }
                """.formatted(statesBlock, initialState, terminalBlock, transitionsBlock);
    }

    // ── Test 1: Exact match → no CHR-044, no CHR-045 ─────────────────────────

    @Test
    void exactMatchBetweenStatesAndEnumMembers() {
        var m = new ChronosCompiler().compile(
                model("PENDING, PAID, SHIPPED, DELIVERED, CANCELLED",
                        "PENDING",
                        "DELIVERED, CANCELLED",
                        "PENDING -> PAID, PAID -> SHIPPED, SHIPPED -> DELIVERED, SHIPPED -> CANCELLED"),
                "test").modelOrNull();

        var result = new ChronosValidator().validate(m);
        assertFalse(result.hasErrors(), "Exact match should produce no errors; got: " + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-044".equals(d.code())),
                "No CHR-044 expected; got: " + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-045".equals(d.code())),
                "No CHR-045 expected; got: " + result.diagnostics());
    }

    // ── Test 2: Valid subset → no CHR-044, CHR-045 for uncovered members ──────

    @Test
    void validSubsetOfEnumMembers() {
        var m = new ChronosCompiler().compile(
                model("PENDING, PAID, SHIPPED",
                        "PENDING",
                        "SHIPPED",
                        "PENDING -> PAID, PAID -> SHIPPED"),
                "test").modelOrNull();

        var result = new ChronosValidator().validate(m);
        assertFalse(result.hasErrors(),
                "Subset states should not produce errors; got: " + result.diagnostics());

        var warnings045 = result.warnings().stream()
                .filter(d -> "CHR-045".equals(d.code()))
                .toList();
        assertEquals(2, warnings045.size(),
                "Expected 2 CHR-045 warnings (DELIVERED, CANCELLED); got: " + warnings045);
        assertTrue(warnings045.stream().anyMatch(d -> d.message().contains("DELIVERED")));
        assertTrue(warnings045.stream().anyMatch(d -> d.message().contains("CANCELLED")));
        assertTrue(warnings045.stream().allMatch(d -> d.message().contains("OrderLifecycle")));
        assertTrue(warnings045.stream().allMatch(d -> d.message().contains("OrderStatus")));
    }

    // ── Test 3: One state not in enum → CHR-044 ───────────────────────────────

    @Test
    void stateNotInEnumTriggersChr044() {
        // REFUNDED is not a member of OrderStatus
        var m = new ChronosCompiler().compile(
                model("PENDING, PAID, REFUNDED",
                        "PENDING",
                        "REFUNDED",
                        "PENDING -> PAID, PAID -> REFUNDED"),
                "test").modelOrNull();

        var result = new ChronosValidator().validate(m);

        var errors044 = result.errors().stream()
                .filter(d -> "CHR-044".equals(d.code()))
                .toList();
        assertEquals(1, errors044.size(),
                "Expected 1 CHR-044 for REFUNDED; got: " + errors044);
        assertTrue(errors044.get(0).message().contains("REFUNDED"));
        assertTrue(errors044.get(0).message().contains("OrderLifecycle"));
        assertTrue(errors044.get(0).message().contains("OrderStatus"));
        assertTrue(errors044.get(0).message().contains("not a member"));
    }

    // ── Test 4: Multiple invalid states → multiple CHR-044 ───────────────────

    @Test
    void multipleStatesNotInEnumTriggersMultipleChr044() {
        // Both REFUNDED and DISPUTED are not in OrderStatus; make them terminal to avoid CHR-030
        var m = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                    DELIVERED
                    CANCELLED
                }

                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [REFUNDED, DISPUTED]
                    initial: REFUNDED
                    terminal: [REFUNDED, DISPUTED]
                    transitions: [
                        REFUNDED -> DISPUTED
                    ]
                }
                """, "test").modelOrNull();

        var errors044 = new ChronosValidator().validate(m).errors().stream()
                .filter(d -> "CHR-044".equals(d.code()))
                .toList();
        assertEquals(2, errors044.size(),
                "Expected CHR-044 for REFUNDED and DISPUTED; got: " + errors044);
        assertTrue(errors044.stream().anyMatch(d -> d.message().contains("REFUNDED")));
        assertTrue(errors044.stream().anyMatch(d -> d.message().contains("DISPUTED")));
    }

    // ── Test 5: CHR-044 message quality ──────────────────────────────────────

    @Test
    void chr044MessageContainsStateMachineAndEnumName() {
        var m = new ChronosCompiler().compile(
                model("PENDING, REFUNDED",
                        "PENDING",
                        "REFUNDED",
                        "PENDING -> REFUNDED"),
                "test").modelOrNull();

        var chr044 = new ChronosValidator().validate(m).errors().stream()
                .filter(d -> "CHR-044".equals(d.code()))
                .findFirst();

        assertTrue(chr044.isPresent(), "Expected CHR-044 to fire");
        String msg = chr044.get().message();
        assertTrue(msg.contains("REFUNDED"),        "message should contain state name");
        assertTrue(msg.contains("OrderLifecycle"),  "message should contain statemachine name");
        assertTrue(msg.contains("OrderStatus"),     "message should contain enum name");
        assertTrue(msg.contains("not a member"),    "message should explain the problem");
    }

    // ── Test 6: Imported entity → skip both checks ───────────────────────────

    @Test
    void importedEntitySkipsChr044AndChr045() {
        // Order is imported from com.domain — the entity is not in the local model.entities(),
        // so checkChr044And045 exits at the entity == null guard.
        var m = new ChronosCompiler().compile("""
                namespace com.example
                use com.domain#Order

                enum OrderStatus {
                    PENDING
                    PAID
                }

                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [REFUNDED]
                    initial: REFUNDED
                    terminal: [REFUNDED]
                    transitions: [
                        REFUNDED -> REFUNDED
                    ]
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(m);
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-044".equals(d.code())),
                "Imported entity should suppress CHR-044; got: " + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-045".equals(d.code())),
                "Imported entity should suppress CHR-045; got: " + result.diagnostics());
    }

    // ── Test 7: Enum not locally defined → skip both checks ──────────────────

    @Test
    void enumNotInLocalModelSkipsChr044AndChr045() {
        // OrderStatus is referenced via import — it won't appear in model.enums(),
        // so checkChr044And045 exits at the enumDef == null guard.
        // CHR-033 may fire for the unresolved type, but CHR-044/045 must not.
        var m = new ChronosCompiler().compile("""
                namespace com.example
                use com.domain#OrderStatus

                entity Order {
                    status: OrderStatus
                }

                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID]
                    initial: PENDING
                    terminal: [PAID]
                    transitions: [
                        PENDING -> PAID
                    ]
                }
                """, "test").modelOrNull();

        // modelOrNull() may be null if compilation failed entirely; guard against that
        if (m == null) return;

        var result = new ChronosValidator().validate(m);
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-044".equals(d.code())),
                "Enum not in local model should suppress CHR-044; got: " + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-045".equals(d.code())),
                "Enum not in local model should suppress CHR-045; got: " + result.diagnostics());
    }
}
