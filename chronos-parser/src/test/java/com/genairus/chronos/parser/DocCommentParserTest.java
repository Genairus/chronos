package com.genairus.chronos.parser;

import com.genairus.chronos.syntax.*;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@code ///} doc-comment tokens are extracted from the HIDDEN channel
 * by the {@link com.genairus.chronos.parser.lowering.LoweringVisitor} and attached
 * to the correct {@link SyntaxDecl} nodes.
 *
 * <p>Normalization: {@code "///"} prefix is stripped, plus one optional leading space.
 */
class DocCommentParserTest {

    private static SyntaxModel parse(String source) {
        return new ChronosParserFacade().parse(source, "<test>");
    }

    // ── No doc comments ───────────────────────────────────────────────────────

    @Test
    void noDocComment_entityHasEmptyList() {
        var model = parse("""
                namespace com.example

                entity Order {
                    id: String
                }
                """);

        assertEquals(1, model.declarations().size());
        assertEquals(0, model.declarations().get(0).docComments().size());
    }

    // ── Single doc comment ────────────────────────────────────────────────────

    @Test
    void singleDocComment_entity_strippedAndAttached() {
        var model = parse("""
                namespace com.example

                /// The main order entity
                entity Order {
                    id: String
                }
                """);

        var decl = (SyntaxEntityDecl) model.declarations().get(0);
        assertEquals(1, decl.docComments().size());
        assertEquals("The main order entity", decl.docComments().get(0));
    }

    // ── Multiple doc comments ─────────────────────────────────────────────────

    @Test
    void multipleDocComments_entity_allAttachedInOrder() {
        var model = parse("""
                namespace com.example

                /// Represents a customer purchase
                /// Contains all line items and totals
                entity Order {
                    id: String
                }
                """);

        var decl = (SyntaxEntityDecl) model.declarations().get(0);
        assertEquals(2, decl.docComments().size());
        assertEquals("Represents a customer purchase",   decl.docComments().get(0));
        assertEquals("Contains all line items and totals", decl.docComments().get(1));
    }

    // ── Doc comment only on first shape ───────────────────────────────────────

    @Test
    void docCommentOnlyOnFirst_secondShapeHasEmpty() {
        var model = parse("""
                namespace com.example

                /// The actor who places orders
                actor Customer

                entity Order {
                    id: String
                }
                """);

        assertEquals(2, model.declarations().size());

        var actor = (SyntaxActorDecl) model.declarations().get(0);
        assertEquals(1, actor.docComments().size());
        assertEquals("The actor who places orders", actor.docComments().get(0));

        var entity = (SyntaxEntityDecl) model.declarations().get(1);
        assertEquals(0, entity.docComments().size());
    }

    // ── Doc comments don't bleed across shapes ────────────────────────────────

    @Test
    void docCommentsNotShared_eachShapeGetsOwnComments() {
        var model = parse("""
                namespace com.example

                /// First entity doc
                entity Order {
                    id: String
                }

                /// Second entity doc
                entity Product {
                    name: String
                }
                """);

        var order   = (SyntaxEntityDecl) model.declarations().get(0);
        var product = (SyntaxEntityDecl) model.declarations().get(1);

        assertEquals(List.of("First entity doc"),  order.docComments());
        assertEquals(List.of("Second entity doc"), product.docComments());
    }

    // ── Strip prefix normalization ────────────────────────────────────────────

    @Test
    void stripPrefix_noSpace_returnsEmpty() {
        // "///" with no content after it
        var model = parse("""
                namespace com.example

                ///
                entity Order {
                    id: String
                }
                """);

        var decl = (SyntaxEntityDecl) model.declarations().get(0);
        assertEquals(1, decl.docComments().size());
        assertEquals("", decl.docComments().get(0));
    }

    @Test
    void stripPrefix_singleSpace_stripped() {
        var model = parse("""
                namespace com.example

                /// With space
                entity Order {
                    id: String
                }
                """);

        var decl = (SyntaxEntityDecl) model.declarations().get(0);
        assertEquals("With space", decl.docComments().get(0));
    }

    // ── Journey doc comment ───────────────────────────────────────────────────

    @Test
    void journeyDocComment_attached() {
        var model = parse("""
                namespace com.example

                actor Customer

                /// Checkout flow for registered users
                /// Handles payment and confirmation
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
                """);

        var journey = (SyntaxJourneyDecl) model.declarations().get(1);
        assertEquals(2, journey.docComments().size());
        assertEquals("Checkout flow for registered users", journey.docComments().get(0));
        assertEquals("Handles payment and confirmation",   journey.docComments().get(1));
    }

    // ── Doc comment with trait annotations ───────────────────────────────────

    @Test
    void docCommentBeforeTraitAnnotation_attachedToShape() {
        var model = parse("""
                namespace com.example

                /// Holds sensitive user data
                @pii
                entity UserProfile {
                    email: String
                }
                """);

        var decl = (SyntaxEntityDecl) model.declarations().get(0);
        assertEquals(1, decl.docComments().size());
        assertEquals("Holds sensitive user data", decl.docComments().get(0));
        assertEquals(1, decl.traits().size());
        assertEquals("pii", decl.traits().get(0).name());
    }

    // ── Regular // comment NOT included ──────────────────────────────────────

    @Test
    void regularComment_notIncludedInDocComments() {
        var model = parse("""
                namespace com.example

                // This is a regular comment, not a doc comment
                entity Order {
                    id: String
                }
                """);

        var decl = (SyntaxEntityDecl) model.declarations().get(0);
        assertEquals(0, decl.docComments().size());
    }

    // ── Actor, enum, relationship, error ─────────────────────────────────────

    @Test
    void allShapeTypes_docCommentsAttached() {
        var model = parse("""
                namespace com.example

                /// Customer actor
                actor Customer

                /// Order status values
                enum OrderStatus {
                    PENDING = 1
                    PAID    = 2
                }

                /// Links orders to customers
                relationship CustomerOrders {
                    from: Customer
                    to: Order
                    cardinality: one_to_many
                }

                entity Order {
                    id: String
                }
                """);

        // actor
        var actor = (SyntaxActorDecl) model.declarations().get(0);
        assertEquals(List.of("Customer actor"), actor.docComments());

        // enum
        var enumDecl = (SyntaxEnumDecl) model.declarations().get(1);
        assertEquals(List.of("Order status values"), enumDecl.docComments());

        // relationship
        var rel = (SyntaxRelationshipDecl) model.declarations().get(2);
        assertEquals(List.of("Links orders to customers"), rel.docComments());

        // entity without doc comment
        var entity = (SyntaxEntityDecl) model.declarations().get(3);
        assertEquals(0, entity.docComments().size());
    }
}
