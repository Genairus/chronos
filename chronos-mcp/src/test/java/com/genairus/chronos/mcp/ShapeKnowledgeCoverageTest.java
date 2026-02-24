package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.knowledge.ShapeKnowledge;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Build gate: verifies ShapeKnowledge.REGISTRY covers exactly all 15 Chronos shape types.
 *
 * <p>Adding a new shape type without adding a corresponding YAML overlay causes this
 * test to fail, blocking the build.
 */
class ShapeKnowledgeCoverageTest {

    private static final Set<String> ALL_SHAPE_TYPES = Set.of(
            "entity", "shape", "list", "map", "enum",
            "actor", "policy", "journey", "relationship",
            "invariant", "deny", "error", "statemachine", "role", "event");

    @Test
    void registryCoversAllShapeTypes() {
        var covered = ShapeKnowledge.REGISTRY.keySet();

        var missing = ALL_SHAPE_TYPES.stream()
                .filter(s -> !covered.contains(s))
                .sorted()
                .toList();
        var extra = covered.stream()
                .filter(s -> !ALL_SHAPE_TYPES.contains(s))
                .sorted()
                .toList();

        assertTrue(missing.isEmpty(),
                "ShapeKnowledge is missing entries for: " + missing
                        + "\nAdd YAML overlays in src/main/resources/shape-overlays/");
        assertTrue(extra.isEmpty(),
                "ShapeKnowledge has entries for unknown shape types: " + extra
                        + "\nRemove the YAML overlay or add the shape type to ALL_SHAPE_TYPES.");
    }

    @Test
    void everyEntryHasNonBlankRequiredFields() {
        for (var entry : ShapeKnowledge.REGISTRY.values()) {
            assertFalse(entry.shape().isBlank(),
                    "shape must not be blank");
            assertFalse(entry.description().isBlank(),
                    "description must not be blank for: " + entry.shape());
            assertFalse(entry.minimalExample().isBlank(),
                    "minimalExample must not be blank for: " + entry.shape());
            assertFalse(entry.scaffoldTemplate().isBlank(),
                    "scaffoldTemplate must not be blank for: " + entry.shape());
            assertNotNull(entry.applicableRules(),
                    "applicableRules must not be null for: " + entry.shape());
            assertNotNull(entry.commonMistakes(),
                    "commonMistakes must not be null for: " + entry.shape());
            assertNotNull(entry.notes(),
                    "notes must not be null for: " + entry.shape());
        }
    }

    @Test
    void compilableFlagsAreDefinedForAllShapes() {
        // compilable=false shapes: journey, relationship, invariant, statemachine
        var compilableFalse = Set.of("journey", "relationship", "invariant", "statemachine");

        for (var entry : ShapeKnowledge.REGISTRY.values()) {
            if (compilableFalse.contains(entry.shape())) {
                assertFalse(entry.compilable(),
                        "Expected compilable=false for: " + entry.shape()
                                + " (requires cross-references)");
                assertFalse(entry.notes().isEmpty(),
                        "compilable=false shape must have notes explaining what to supply: "
                                + entry.shape());
            } else {
                assertTrue(entry.compilable(),
                        "Expected compilable=true for: " + entry.shape());
            }
        }
    }
}
