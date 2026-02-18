package com.genairus.chronos.parser;

import com.genairus.chronos.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip parser tests: one Chronos source string per major shape type.
 * Each test verifies that the correct {@link ChronosModel} fields are produced.
 */
class ChronosModelParserTest {

    // ── helpers ────────────────────────────────────────────────────────────────

    private static ChronosModel parse(String source) {
        return ChronosModelParser.parseString("<test>", source);
    }

    // ── namespace & imports ────────────────────────────────────────────────────

    @Test
    void namespaceAndImportAreParsed() {
        var model = parse("""
                namespace com.example.checkout
                use com.example.actors#Customer
                """);
        assertEquals("com.example.checkout", model.namespace());
        assertEquals(1, model.imports().size());
        assertEquals("com.example.actors#Customer", model.imports().get(0).qualifiedId());
        assertEquals("<test>:2", model.imports().get(0).location().toString());
    }

    // ── entity ────────────────────────────────────────────────────────────────

    @Test
    void entityWithFieldsIsParsed() {
        var model = parse("""
                namespace com.example
                @pii
                entity Order {
                    @required
                    id: String
                    total: Float
                }
                """);
        var entity = model.entities().get(0);
        assertEquals("Order", entity.name());
        assertEquals(1, entity.traits().size());
        assertEquals("pii", entity.traits().get(0).name());
        assertEquals(2, entity.fields().size());
        assertEquals("id", entity.fields().get(0).name());
        assertInstanceOf(TypeRef.PrimitiveType.class, entity.fields().get(0).type());
        assertEquals(TypeRef.PrimitiveKind.STRING,
                ((TypeRef.PrimitiveType) entity.fields().get(0).type()).kind());
        assertTrue(entity.fields().get(0).isRequired());
        assertFalse(entity.fields().get(1).isRequired());
    }

    // ── shape (value object) ──────────────────────────────────────────────────

    @Test
    void shapeWithGenericFieldIsParsed() {
        var model = parse("""
                namespace com.example
                shape Money {
                    amount: Float
                    currency: String
                    tags: List<String>
                }
                """);
        var shape = model.shapeStructs().get(0);
        assertEquals("Money", shape.name());
        assertEquals(3, shape.fields().size());
        var tagsType = (TypeRef.ListType) shape.fields().get(2).type();
        assertInstanceOf(TypeRef.PrimitiveType.class, tagsType.elementType());
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void listDefIsParsed() {
        var model = parse("""
                namespace com.example
                list OrderItemList { member: OrderItem }
                """);
        var list = model.lists().get(0);
        assertEquals("OrderItemList", list.name());
        var memberType = (TypeRef.NamedTypeRef) list.memberType();
        assertEquals("OrderItem", memberType.qualifiedId());
    }

    // ── map ───────────────────────────────────────────────────────────────────

    @Test
    void mapDefIsParsed() {
        var model = parse("""
                namespace com.example
                map MetadataMap { key: String value: String }
                """);
        var map = model.maps().get(0);
        assertEquals("MetadataMap", map.name());
        assertInstanceOf(TypeRef.PrimitiveType.class, map.keyType());
        assertInstanceOf(TypeRef.PrimitiveType.class, map.valueType());
    }

    // ── enum ──────────────────────────────────────────────────────────────────

    @Test
    void enumWithOrdinalsIsParsed() {
        var model = parse("""
                namespace com.example
                enum OrderStatus {
                    PENDING = 1
                    PAID    = 2
                    FAILED
                }
                """);
        var enumDef = model.enums().get(0);
        assertEquals("OrderStatus", enumDef.name());
        assertEquals(3, enumDef.members().size());
        assertTrue(enumDef.members().get(0).ordinal().isPresent());
        assertEquals(1, enumDef.members().get(0).ordinal().getAsInt());
        assertEquals(2, enumDef.members().get(1).ordinal().getAsInt());
        assertTrue(enumDef.members().get(2).ordinal().isEmpty());
    }

    // ── actor ─────────────────────────────────────────────────────────────────

    @Test
    void actorDefIsParsed() {
        var model = parse("""
                namespace com.example
                @description("A guest user")
                actor GuestUser
                """);
        var actor = model.actors().get(0);
        assertEquals("GuestUser", actor.name());
        assertEquals("A guest user", actor.description().orElseThrow());
    }

    // ── policy ────────────────────────────────────────────────────────────────

    @Test
    void policyDefIsParsed() {
        var model = parse("""
                namespace com.example
                @compliance("PCI-DSS")
                policy PaymentRetention {
                    description: "Card data purged after 7 years"
                }
                """);
        var policy = model.policies().get(0);
        assertEquals("PaymentRetention", policy.name());
        assertEquals("Card data purged after 7 years", policy.description());
        assertEquals("PCI-DSS", policy.complianceFramework().orElseThrow());
    }

    // ── journey (minimal) ─────────────────────────────────────────────────────

    @Test
    void minimalJourneyIsParsed() {
        var model = parse("""
                namespace com.example
                journey GuestCheckout {
                    actor: Customer
                }
                """);
        var journey = model.journeys().get(0);
        assertEquals("GuestCheckout", journey.name());
        assertEquals("Customer", journey.actorName().orElseThrow());
        assertTrue(journey.preconditions().isEmpty());
        assertTrue(journey.steps().isEmpty());
        assertTrue(journey.variants().isEmpty());
        assertTrue(journey.journeyOutcomes().isEmpty());
    }

    // ── journey (full) ────────────────────────────────────────────────────────

    @Test
    void fullJourneyIsParsed() {
        var model = parse("""
                namespace com.example
                @kpi(metric: "CheckoutConversion", target: ">75%")
                journey GuestCheckout {
                    actor: Customer
                    preconditions: ["Cart is not empty", "User is not logged in"]
                    steps: [
                        step EnterEmail {
                            action: "User types email"
                            expectation: "Email field is populated"
                        },
                        @slo(p99: "200ms")
                        step ConfirmOrder {
                            action: "User clicks confirm"
                            expectation: "Order is created"
                            outcome: TransitionTo(OrderConfirmed)
                            telemetry: [order_created]
                            risk: "Payment gateway may be down"
                        }
                    ]
                    variants: {
                        PaymentDeclined: {
                            trigger: "Gateway returned declined"
                            steps: [
                                step NotifyUser {
                                    expectation: "Error shown"
                                }
                            ]
                            outcome: ReturnToStep(EnterEmail)
                        }
                    }
                    outcomes: {
                        success: "Order confirmed",
                        failure: "Cart intact"
                    }
                }
                """);

        var journey = model.journeys().get(0);
        assertEquals("GuestCheckout", journey.name());

        // kpi trait
        assertTrue(journey.kpiMetric().isPresent());
        assertEquals("CheckoutConversion", journey.kpiMetric().get());

        // actor
        assertEquals("Customer", journey.actorName().orElseThrow());

        // preconditions
        assertEquals(2, journey.preconditions().size());
        assertEquals("Cart is not empty", journey.preconditions().get(0));

        // steps
        assertEquals(2, journey.steps().size());
        var enterEmail = journey.steps().get(0);
        assertEquals("EnterEmail", enterEmail.name());
        assertEquals("User types email", enterEmail.action().orElseThrow());
        assertEquals("Email field is populated",
                enterEmail.expectation().orElseThrow());

        var confirmOrder = journey.steps().get(1);
        assertEquals("ConfirmOrder", confirmOrder.name());
        assertTrue(confirmOrder.outcome().isPresent());
        assertInstanceOf(OutcomeExpr.TransitionTo.class, confirmOrder.outcome().get());
        assertEquals("OrderConfirmed",
                ((OutcomeExpr.TransitionTo) confirmOrder.outcome().get()).target());
        assertEquals(List.of("order_created"), confirmOrder.telemetryEvents());
        assertEquals("Payment gateway may be down", confirmOrder.risk().orElseThrow());
        assertEquals(1, confirmOrder.traits().size());
        assertEquals("slo", confirmOrder.traits().get(0).name());

        // variants
        assertEquals(1, journey.variants().size());
        var declined = journey.variants().get("PaymentDeclined");
        assertNotNull(declined);
        assertEquals("Gateway returned declined", declined.trigger());
        assertEquals(1, declined.steps().size());
        assertTrue(declined.outcome().isPresent());
        assertInstanceOf(OutcomeExpr.ReturnToStep.class, declined.outcome().get());

        // journey outcomes
        assertTrue(journey.journeyOutcomes().isPresent());
        assertEquals("Order confirmed",
                journey.journeyOutcomes().get().successOutcome().orElseThrow());
        assertEquals("Cart intact",
                journey.journeyOutcomes().get().failureOutcome().orElseThrow());
    }

    // ── type refs ─────────────────────────────────────────────────────────────

    @Test
    void allPrimitiveTypesAreParsed() {
        var model = parse("""
                namespace com.example
                entity AllTypes {
                    a: String
                    b: Integer
                    c: Long
                    d: Float
                    e: Boolean
                    f: Timestamp
                    g: Blob
                    h: Document
                }
                """);
        var fields = model.entities().get(0).fields();
        assertEquals(8, fields.size());
        var kinds = fields.stream()
                .map(f -> ((TypeRef.PrimitiveType) f.type()).kind())
                .toList();
        assertEquals(List.of(
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
    void nestedGenericTypeIsParsed() {
        var model = parse("""
                namespace com.example
                entity Nested {
                    meta: Map<String, List<String>>
                }
                """);
        var field = model.entities().get(0).fields().get(0);
        var mapType = (TypeRef.MapType) field.type();
        assertInstanceOf(TypeRef.PrimitiveType.class, mapType.keyType());
        assertInstanceOf(TypeRef.ListType.class, mapType.valueType());
    }

    // ── doc comments ─────────────────────────────────────────────────────────

    @Test
    void docCommentsAreAttachedToShapes() {
        var model = parse("""
                namespace com.example
                /// The main order entity
                /// Tracks purchase lifecycle
                entity Order {
                    id: String
                }
                """);
        var entity = model.entities().get(0);
        assertEquals(2, entity.docComments().size());
        assertEquals("The main order entity", entity.docComments().get(0));
        assertEquals("Tracks purchase lifecycle", entity.docComments().get(1));
    }

    // ── trait forms ───────────────────────────────────────────────────────────

    @Test
    void bareTraitIsParsed() {
        var model = parse("""
                namespace com.example
                @pii
                entity Sensitive { id: String }
                """);
        var trait = model.entities().get(0).traits().get(0);
        assertEquals("pii", trait.name());
        assertTrue(trait.isBare());
    }

    @Test
    void positionalTraitIsParsed() {
        var model = parse("""
                namespace com.example
                @description("An order")
                entity Order { id: String }
                """);
        var trait = model.entities().get(0).traits().get(0);
        assertEquals("description", trait.name());
        assertEquals("An order", ((TraitValue.StringValue) trait.firstPositionalValue().orElseThrow()).value());
    }

    @Test
    void namedTraitArgsAreParsed() {
        var model = parse("""
                namespace com.example
                @kpi(metric: "Conv", target: ">80%")
                journey MyJourney { actor: Actor }
                """);
        var trait = model.journeys().get(0).traits().get(0);
        assertEquals("kpi", trait.name());
        assertEquals("Conv",
                ((TraitValue.StringValue) trait.namedValue("metric").orElseThrow()).value());
    }

    // ── syntax error handling ─────────────────────────────────────────────────

    @Test
    void syntaxErrorThrowsParseException() {
        var ex = assertThrows(ChronosParseException.class, () ->
                parse("namespace com.example entity { broken }"));
        assertFalse(ex.errors().isEmpty());
    }

    // ── source locations ──────────────────────────────────────────────────────

    @Test
    void sourceLocationsAreCorrect() {
        var model = parse("""
                namespace com.example
                entity Order {
                    id: String
                }
                """);
        var entity = model.entities().get(0);
        // entity keyword starts at line 2
        assertEquals(2, entity.location().line());
        assertEquals("<test>", entity.location().file());
    }

    // ── multiple shapes in one file ───────────────────────────────────────────

    @Test
    void multipleShapesInOneFile() {
        var model = parse("""
                namespace com.example
                entity Order { id: String }
                shape Money { amount: Float }
                enum Status { PENDING PAID }
                actor Customer
                """);
        assertEquals(1, model.entities().size());
        assertEquals(1, model.shapeStructs().size());
        assertEquals(1, model.enums().size());
        assertEquals(1, model.actors().size());
        assertEquals(4, model.shapes().size());
    }
}
