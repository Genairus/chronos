package com.genairus.chronos.compiler;

import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.EntityDef;
import com.genairus.chronos.ir.types.JourneyDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test: doc comments flow from {@code ///} source tokens through the
 * Syntax DTO layer and {@link BuildIrSkeletonPhase} into the finalized
 * {@link com.genairus.chronos.ir.model.IrModel}.
 */
class DocCommentFlowTest {

    @Test
    void entityDocComments_flowIntoIrModel() {
        String source = """
                namespace com.example.test

                /// The main order entity
                /// Tracks all purchases
                entity Order {
                    id: String
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");
        assertTrue(result.parsed(), "should parse");

        EntityDef order = result.modelOrNull().entities().get(0);
        assertEquals(List.of("The main order entity", "Tracks all purchases"),
                order.docComments(),
                "entity doc comments must flow into IR");
    }

    @Test
    void actorDocComments_flowIntoIrModel() {
        String source = """
                namespace com.example.test

                /// A registered shopper
                actor Customer

                journey Checkout {
                    actor: Customer
                    steps: [
                        step Pay {
                            action: "User pays"
                            expectation: "Payment accepted"
                        }
                    ]
                    outcomes: { success: "Done" }
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");
        assertTrue(result.parsed(), "should parse");

        ActorDef customer = result.modelOrNull().actors().get(0);
        assertEquals(List.of("A registered shopper"), customer.docComments(),
                "actor doc comments must flow into IR");
    }

    @Test
    void journeyDocComments_flowIntoIrModel() {
        String source = """
                namespace com.example.test

                actor Customer

                /// The primary checkout journey
                /// Supports credit card payments only
                journey Checkout {
                    actor: Customer
                    steps: [
                        step Pay {
                            action: "User pays"
                            expectation: "Payment accepted"
                        }
                    ]
                    outcomes: { success: "Done" }
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");
        assertTrue(result.parsed(), "should parse");

        JourneyDef checkout = result.modelOrNull().journeys().get(0);
        assertEquals(List.of("The primary checkout journey", "Supports credit card payments only"),
                checkout.docComments(),
                "journey doc comments must flow into IR");
    }

    @Test
    void noDocComment_emptyList() {
        String source = """
                namespace com.example.test

                entity Order {
                    id: String
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");
        assertTrue(result.parsed(), "should parse");

        EntityDef order = result.modelOrNull().entities().get(0);
        assertEquals(0, order.docComments().size(),
                "entity without doc comments must have empty list");
    }

    @Test
    void docCommentsAndTraits_bothPreserved() {
        String source = """
                namespace com.example.test

                /// Sensitive personal data
                @pii
                entity UserProfile {
                    email: String
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");
        assertTrue(result.parsed(), "should parse");

        EntityDef profile = result.modelOrNull().entities().get(0);
        assertEquals(List.of("Sensitive personal data"), profile.docComments(),
                "doc comments must be preserved alongside traits");
        assertEquals(1, profile.traits().size(), "pii trait must also be preserved");
        assertEquals("pii", profile.traits().get(0).name());
    }
}
