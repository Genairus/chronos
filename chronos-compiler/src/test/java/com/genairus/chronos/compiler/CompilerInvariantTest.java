package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.util.IrRefWalker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural invariant tests that permanently enforce the compiler contract:
 *
 * <blockquote>
 *   {@code finalized == true}  implies  no unresolved {@link com.genairus.chronos.core.refs.SymbolRef}
 *   exists anywhere in the IR.
 * </blockquote>
 *
 * <p>These tests exercise {@link IrRefWalker#findUnresolvedRefs} directly against
 * a compiled model, so any future code that introduces a new {@code SymbolRef}-bearing
 * field and forgets to resolve it will cause an automatic failure here.
 */
class CompilerInvariantTest {

    @Test
    void finalizedImpliesNoUnresolvedSymbolRefs() {
        var result = new ChronosCompiler().compile("""
                namespace compiler.contract.test

                enum Status { ACTIVE = 1 }

                entity Order {
                    id: String
                    status: Status
                }

                actor Customer

                journey PlaceOrder {
                    actor: Customer
                    steps: [
                        step Browse {
                            action: "Customer browses products"
                            expectation: "Products are displayed"
                        }
                    ]
                    outcomes: { success: "ok" }
                }
                """, "contract-test.chronos");

        assertTrue(result.finalized(), "Model should finalize; diagnostics: " + result.diagnostics());

        var unresolved = IrRefWalker.findUnresolvedRefs(result.modelOrNull());
        assertTrue(unresolved.isEmpty(),
                "finalized==true but unresolved refs exist: " + unresolved);
    }

    @Test
    void unresolvedRefsPreventFinalization() {
        var result = new ChronosCompiler().compile("""
                namespace invariant.test

                entity Order {
                    status: MissingType
                }
                """, "bad-test.chronos");

        assertFalse(result.finalized(),
                "Model with unresolved type reference must not finalize");
    }
}
