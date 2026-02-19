package com.genairus.chronos.parser;

import com.genairus.chronos.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ChronosModelParser#parseFile(Path)}.
 *
 * <p>Reads {@code examples/integration/checkout.chronos} from disk and asserts
 * shape counts, journey structure, nested generic types, and real-file source
 * locations.
 */
class ChronosModelParserIntegrationTest {

    /** Resolved at test time relative to the module's working directory. */
    private static final Path FIXTURE =
            Path.of("../examples/integration/checkout.chronos").toAbsolutePath();

    private static ChronosModel parseFixture() throws Exception {
        assertTrue(Files.exists(FIXTURE),
                "Fixture not found: " + FIXTURE + " — run from the project root or a module directory");
        return ChronosModelParser.parseFile(FIXTURE);
    }

    // ── Shape counts ──────────────────────────────────────────────────────────

    @Test
    void importIsParsed() throws Exception {
        var model = parseFixture();
        assertEquals(1, model.imports().size());
        assertEquals("Currency", model.imports().get(0).shapeName());
        assertEquals("com.example.shared", model.imports().get(0).namespace());
    }

    @Test
    void totalShapeCountIsNine() throws Exception {
        var model = parseFixture();
        assertEquals(10, model.shapes().size(),
                "Expected 1 entity + 1 shape + 1 list + 1 map + 2 enums + 1 actor + 1 policy + 1 error + 1 journey");
    }

    @Test
    void entityCountIsOne() throws Exception {
        var model = parseFixture();
        assertEquals(1, model.entities().size());
        assertEquals("CartItem", model.entities().get(0).name());
    }

    @Test
    void shapeStructCountIsOne() throws Exception {
        var model = parseFixture();
        assertEquals(1, model.shapeStructs().size());
        assertEquals("Money", model.shapeStructs().get(0).name());
    }

    @Test
    void listAndMapCountIsOne() throws Exception {
        var model = parseFixture();
        assertEquals(1, model.lists().size());
        assertEquals("CartItemList", model.lists().get(0).name());

        assertEquals(1, model.maps().size());
        assertEquals("TagMap", model.maps().get(0).name());
    }

    @Test
    void enumCountIsTwoWithAndWithoutOrdinals() throws Exception {
        var model = parseFixture();
        assertEquals(2, model.enums().size());

        var withOrdinals = model.enums().stream()
                .filter(e -> e.name().equals("PaymentStatus")).findFirst().orElseThrow();
        assertEquals(4, withOrdinals.members().size());
        assertTrue(withOrdinals.members().get(0).ordinal().isPresent());
        assertEquals(1, withOrdinals.members().get(0).ordinal().getAsInt());
        assertEquals(4, withOrdinals.members().get(3).ordinal().getAsInt());

        var withoutOrdinals = model.enums().stream()
                .filter(e -> e.name().equals("CartState")).findFirst().orElseThrow();
        assertEquals(4, withoutOrdinals.members().size());
        assertTrue(withoutOrdinals.members().stream().allMatch(m -> m.ordinal().isEmpty()));
    }

    @Test
    void actorAndPolicyCountIsOne() throws Exception {
        var model = parseFixture();
        assertEquals(1, model.actors().size());
        assertEquals("Customer", model.actors().get(0).name());

        assertEquals(1, model.policies().size());
        assertEquals("PaymentSecurity", model.policies().get(0).name());
    }

    // ── Entity: all 8 primitive field types ───────────────────────────────────

    @Test
    void cartItemHasAllEightPrimitiveFieldTypes() throws Exception {
        var model = parseFixture();
        var entity = model.entities().get(0);
        assertEquals("CartItem", entity.name());
        assertEquals(8, entity.fields().size());

        var kinds = entity.fields().stream()
                .map(f -> ((TypeRef.PrimitiveType) f.type()).kind())
                .toList();
        assertEquals(java.util.List.of(
                TypeRef.PrimitiveKind.STRING,
                TypeRef.PrimitiveKind.INTEGER,
                TypeRef.PrimitiveKind.LONG,
                TypeRef.PrimitiveKind.FLOAT,
                TypeRef.PrimitiveKind.BOOLEAN,
                TypeRef.PrimitiveKind.TIMESTAMP,
                TypeRef.PrimitiveKind.BLOB,
                TypeRef.PrimitiveKind.DOCUMENT
        ), kinds);
    }

    @Test
    void cartItemFirstFieldIsRequired() throws Exception {
        var model = parseFixture();
        var idField = model.entities().get(0).fields().get(0);
        assertEquals("id", idField.name());
        assertTrue(idField.isRequired());
    }

    // ── Entity: doc comment ───────────────────────────────────────────────────

    @Test
    void cartItemHasDocComment() throws Exception {
        var model = parseFixture();
        var entity = model.entities().get(0);
        assertEquals(1, entity.docComments().size());
        assertEquals("A single line item in the shopping cart.", entity.docComments().get(0));
    }

    // ── Nested generic type ───────────────────────────────────────────────────

    @Test
    void tagMapValueTypeIsListOfString() throws Exception {
        var model = parseFixture();
        var tagMap = model.maps().stream()
                .filter(m -> m.name().equals("TagMap")).findFirst().orElseThrow();
        assertInstanceOf(TypeRef.PrimitiveType.class, tagMap.keyType());
        assertEquals(TypeRef.PrimitiveKind.STRING,
                ((TypeRef.PrimitiveType) tagMap.keyType()).kind());

        var valueType = (TypeRef.ListType) tagMap.valueType();
        assertInstanceOf(TypeRef.PrimitiveType.class, valueType.elementType());
        assertEquals(TypeRef.PrimitiveKind.STRING,
                ((TypeRef.PrimitiveType) valueType.elementType()).kind());
    }

    // ── Full journey structure ─────────────────────────────────────────────────

    @Test
    void journeyHasCorrectActor() throws Exception {
        var model = parseFixture();
        var journey = model.journeys().get(0);
        assertEquals("CheckoutJourney", journey.name());
        assertEquals("Customer", journey.actorName().orElseThrow());
    }

    @Test
    void journeyHasTwoPreconditions() throws Exception {
        var model = parseFixture();
        var journey = model.journeys().get(0);
        assertEquals(2, journey.preconditions().size());
        assertEquals("Cart contains at least one item", journey.preconditions().get(0));
        assertEquals("Customer is authenticated", journey.preconditions().get(1));
    }

    @Test
    void journeyHasThreeStepsWithAllFiveFieldKinds() throws Exception {
        var model = parseFixture();
        var journey = model.journeys().get(0);
        assertEquals(3, journey.steps().size());

        // ReviewCart: action, expectation, telemetry, risk (no outcome)
        var reviewCart = journey.steps().get(0);
        assertEquals("ReviewCart", reviewCart.name());
        assertTrue(reviewCart.action().isPresent());
        assertTrue(reviewCart.expectation().isPresent());
        assertFalse(reviewCart.telemetryEvents().isEmpty());
        assertTrue(reviewCart.risk().isPresent());
        assertFalse(reviewCart.outcome().isPresent());

        // ConfirmOrder: action, expectation, outcome, telemetry (no risk)
        var confirmOrder = journey.steps().get(2);
        assertEquals("ConfirmOrder", confirmOrder.name());
        assertTrue(confirmOrder.outcome().isPresent());
        assertInstanceOf(OutcomeExpr.TransitionTo.class, confirmOrder.outcome().get());
        assertEquals("OrderConfirmed",
                ((OutcomeExpr.TransitionTo) confirmOrder.outcome().get()).target());
    }

    @Test
    void journeyHasPaymentDeclinedVariant() throws Exception {
        var model = parseFixture();
        var journey = model.journeys().get(0);
        assertEquals(1, journey.variants().size());

        var variant = journey.variants().get("PaymentDeclined");
        assertNotNull(variant, "Expected variant named 'PaymentDeclined'");
        assertEquals("PaymentDeclinedError", variant.trigger());
        assertEquals(1, variant.steps().size());

        assertTrue(variant.outcome().isPresent());
        assertInstanceOf(OutcomeExpr.ReturnToStep.class, variant.outcome().get());
        assertEquals("EnterPaymentDetails",
                ((OutcomeExpr.ReturnToStep) variant.outcome().get()).target());
    }

    @Test
    void journeyHasSuccessAndFailureOutcomes() throws Exception {
        var model = parseFixture();
        var journey = model.journeys().get(0);
        assertTrue(journey.journeyOutcomes().isPresent());
        assertTrue(journey.journeyOutcomes().get().successOutcome().isPresent());
        assertTrue(journey.journeyOutcomes().get().failureOutcome().isPresent());
        assertTrue(journey.journeyOutcomes().get().successOutcome().get().contains("PAID"));
    }

    // ── Source locations reference the real filename ───────────────────────────

    @Test
    void sourceLocationsReferenceCheckoutChronosFilename() throws Exception {
        var model = parseFixture();
        var journey = model.journeys().get(0);
        String file = journey.location().file();
        assertTrue(file.contains("checkout.chronos"),
                "Expected location to reference 'checkout.chronos' but was: " + file);
        // Line must be a positive number (not "<test>" sentinel)
        assertTrue(journey.location().line() > 1,
                "Expected journey to be past line 1, was: " + journey.location().line());
    }
}
