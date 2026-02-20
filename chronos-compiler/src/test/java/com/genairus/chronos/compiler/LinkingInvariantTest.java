package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.util.IrRefWalker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that enforce the linking invariant:
 * {@code finalized == true} ⟹ all {@link com.genairus.chronos.core.refs.SymbolRef}s
 * in the IR are resolved to a {@link com.genairus.chronos.core.refs.ShapeId}.
 */
class LinkingInvariantTest {

    /**
     * A journey that names an actor that is never declared.
     * CrossLinkResolutionPhase must emit CHR-008 (undefined actor).
     * FinalizeIrPhase must then emit CHR-012 (ref still unresolved).
     * The model must not be finalized.
     */
    @Test
    void undefinedActor_emitsChr008AndChr012_finalizedFalse() {
        String source = """
                namespace com.example.test

                journey DoSomething {
                    actor: UndefinedActor
                    steps: [
                        step Perform {
                            action: "User performs action"
                            expectation: "System responds"
                        }
                    ]
                    outcomes: {
                        success: "All done"
                    }
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "should parse successfully");
        assertFalse(result.finalized(), "should not be finalized");

        assertTrue(result.errors().stream().anyMatch(d -> "CHR-008".equals(d.code())),
                "expected CHR-008 (undefined actor) but got: " + result.errors());
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-012".equals(d.code())),
                "expected CHR-012 (unresolved ref remains) but got: " + result.errors());
    }

    /**
     * A valid actor declaration paired with a journey that references it.
     * After compilation all SymbolRefs must be resolved and the model finalized.
     */
    @Test
    void validActorAndJourney_finalizedTrue_noUnresolvedRefs() {
        String source = """
                namespace com.example.test

                actor TestUser

                journey DoSomething {
                    actor: TestUser
                    steps: [
                        step Perform {
                            action: "User performs action"
                            expectation: "System responds"
                        }
                    ]
                    outcomes: {
                        success: "All done"
                    }
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "should parse successfully");
        assertTrue(result.finalized(),
                "should be finalized; errors: " + result.errors());
        assertNotNull(result.modelOrNull(), "model should be present");

        assertTrue(IrRefWalker.findUnresolvedRefs(result.modelOrNull()).isEmpty(),
                "expected no unresolved refs in finalized model");
    }

    /**
     * A relationship whose target entity is not declared.
     * CrossLinkResolutionPhase must emit CHR-011 (undefined entity).
     * The model must not be finalized.
     */
    @Test
    void missingRelationshipTarget_emitsChr011_finalizedFalse() {
        String source = """
                namespace com.example.test

                entity Order {
                    id: String
                }

                relationship OrderItems {
                    from: Order
                    to: MissingEntity
                    cardinality: one_to_many
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "should parse successfully");
        assertFalse(result.finalized(), "should not be finalized");

        assertTrue(result.errors().stream().anyMatch(d -> "CHR-011".equals(d.code())),
                "expected CHR-011 (undefined entity in relationship) but got: " + result.errors());
    }
}
