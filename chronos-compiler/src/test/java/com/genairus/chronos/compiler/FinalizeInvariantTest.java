package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.util.IrRefWalker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the finalize-phase invariant:
 * {@code finalized == true} ⟹ every {@link com.genairus.chronos.core.refs.SymbolRef}
 * in the IR is resolved.
 */
class FinalizeInvariantTest {

    // ── Test A: undefined actor ───────────────────────────────────────────────

    @Test
    void undefinedActor_parsedTrue_finalizedFalse_chr012Present() {
        String source = """
                namespace com.example.test

                journey Checkout {
                    actor: Ghost
                    steps: [
                        step Pay {
                            action: "User pays"
                            expectation: "Payment accepted"
                        }
                    ]
                    outcomes: {
                        success: "Done"
                    }
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "source must parse cleanly");
        assertFalse(result.finalized(), "unresolved ref must prevent finalization");

        boolean hasChr012 = result.errors().stream()
                .anyMatch(d -> "CHR-012".equals(d.code()));
        assertTrue(hasChr012,
                "expected CHR-012 for unresolved ref; errors: " + result.errors());
    }

    // ── Test B: actor declared and resolved ───────────────────────────────────

    @Test
    void definedActor_finalizedTrue_noChr012() {
        String source = """
                namespace com.example.test

                actor Customer

                journey Checkout {
                    actor: Customer
                    steps: [
                        step Pay {
                            action: "User pays"
                            expectation: "Payment accepted"
                        }
                    ]
                    outcomes: {
                        success: "Done"
                    }
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "source must parse cleanly");
        assertTrue(result.finalized(),
                "all refs resolved → must be finalized; errors: " + result.errors());

        boolean hasChr012 = result.diagnostics().stream()
                .anyMatch(d -> "CHR-012".equals(d.code()));
        assertFalse(hasChr012, "no CHR-012 expected in a fully resolved model");

        assertNotNull(result.modelOrNull(), "finalized model must be non-null");
        assertTrue(IrRefWalker.findUnresolvedRefs(result.modelOrNull()).isEmpty(),
                "finalized model must have zero unresolved SymbolRefs");
    }
}
