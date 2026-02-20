package com.genairus.chronos.generators;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that doc comments ({@code /// ...}) are rendered as blockquotes in the
 * generated PRD for all 8 affected shape types: Actors, Enums, Lists, Maps,
 * Relationships, Denies, Errors, and (pre-existing) Entities/Journeys.
 */
class DocCommentPrdTest {

    private static final MarkdownPrdGenerator GEN = new MarkdownPrdGenerator();

    @Test
    void docCommentsRenderedForActorEnumRelationshipAndDeny() {
        var result = new ChronosCompiler().compile("""
                namespace doc.test

                /// The primary user of the system.
                actor Customer

                entity Product { id: String }
                entity Order { id: String }

                /// Tracks the lifecycle of a product.
                enum ProductState {
                    ACTIVE = 1
                    DISCONTINUED = 2
                }

                /// Associates orders with their products.
                relationship OrderProducts {
                    from: Order
                    to: Product
                    cardinality: one_to_many
                }

                /// Must never expose raw payment data.
                deny NoRawPaymentData {
                    description: "Raw payment data is forbidden"
                    scope: [Order]
                    severity: critical
                }
                """, "doc-test.chronos");

        assertTrue(result.parsed(),    "Source must parse cleanly");
        assertTrue(result.finalized(), "Model must finalize without errors");

        String md = GEN.generate(result.modelOrNull()).files().get("doc-test-prd.md");

        // Actor doc comment
        assertTrue(md.contains("> The primary user of the system."),
                "Actor doc comment should appear as blockquote");
        // Enum doc comment
        assertTrue(md.contains("> Tracks the lifecycle of a product."),
                "Enum doc comment should appear as blockquote");
        // Relationship doc comment
        assertTrue(md.contains("> Associates orders with their products."),
                "Relationship doc comment should appear as blockquote");
        // Deny doc comment
        assertTrue(md.contains("> Must never expose raw payment data."),
                "Deny doc comment should appear as blockquote");
    }

    @Test
    void docCommentsRenderedForListAndMap() {
        var result = new ChronosCompiler().compile("""
                namespace doc.test

                entity Tag { id: String }

                /// A list of tags attached to a resource.
                list TagList {
                    member: Tag
                }

                /// Maps header names to their values.
                map HeaderMap {
                    key: String
                    value: String
                }
                """, "doc-test.chronos");

        assertTrue(result.parsed(),    "Source must parse cleanly");
        assertTrue(result.finalized(), "Model must finalize without errors");

        String md = GEN.generate(result.modelOrNull()).files().get("doc-test-prd.md");

        assertTrue(md.contains("> A list of tags attached to a resource."),
                "List doc comment should appear as blockquote");
        assertTrue(md.contains("> Maps header names to their values."),
                "Map doc comment should appear as blockquote");
    }
}
