package com.genairus.chronos.parser;

import com.genairus.chronos.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted branch-coverage tests for the ANTLR grammar rules that were
 * under-exercised in the main round-trip suite:
 *
 * <ul>
 *   <li>{@code traitValue}   — NUMBER, BOOL, and qualifiedId (reference) forms</li>
 *   <li>{@code traitId}      — contextual keyword alternatives used as trait names</li>
 *   <li>{@code primitiveType}— all 8 primitives each driven through a distinct rule path</li>
 *   <li>{@code stepField}    — multiple telemetry IDs; each field keyword in isolation</li>
 *   <li>{@code variantBody}  — with/without steps; with/without outcome</li>
 *   <li>{@code variantsDecl} — multiple variants in one declaration</li>
 *   <li>{@code outcomeEntry} — failure-before-success ordering</li>
 *   <li>Journey with no actor declaration (null actorDecl branch)</li>
 * </ul>
 */
class ChronosParserCoverageTest {

    private static ChronosModel parse(String source) {
        return ChronosModelParser.parseString("<cov>", source);
    }

    // ── traitValue: NUMBER ────────────────────────────────────────────────────

    @Test
    void traitValueNumber_integer() {
        var model = parse("""
                namespace com.example
                @version(3)
                entity Order { id: String }
                """);
        var trait = model.entities().get(0).traits().get(0);
        assertEquals("version", trait.name());
        var val = (TraitValue.NumberValue) trait.firstPositionalValue().orElseThrow();
        assertEquals(3.0, val.value());
    }

    @Test
    void traitValueNumber_decimal() {
        var model = parse("""
                namespace com.example
                @threshold(0.95)
                entity Metric { id: String }
                """);
        var trait = model.entities().get(0).traits().get(0);
        var val = (TraitValue.NumberValue) trait.firstPositionalValue().orElseThrow();
        assertEquals(0.95, val.value(), 1e-9);
    }

    // ── traitValue: BOOL ──────────────────────────────────────────────────────

    @Test
    void traitValueBool_true() {
        var model = parse("""
                namespace com.example
                @deprecated(true)
                entity LegacyOrder { id: String }
                """);
        var trait = model.entities().get(0).traits().get(0);
        var val = (TraitValue.BoolValue) trait.firstPositionalValue().orElseThrow();
        assertTrue(val.value());
    }

    @Test
    void traitValueBool_false() {
        var model = parse("""
                namespace com.example
                @nullable(false)
                entity StrictOrder { id: String }
                """);
        var trait = model.entities().get(0).traits().get(0);
        var val = (TraitValue.BoolValue) trait.firstPositionalValue().orElseThrow();
        assertFalse(val.value());
    }

    // ── traitValue: qualifiedId (reference) ───────────────────────────────────

    @Test
    void traitValueReference_simpleId() {
        var model = parse("""
                namespace com.example
                @prototype(BaseOrder)
                entity Order { id: String }
                """);
        var trait = model.entities().get(0).traits().get(0);
        var val = (TraitValue.ReferenceValue) trait.firstPositionalValue().orElseThrow();
        assertEquals("BaseOrder", val.qualifiedId());
    }

    @Test
    void traitValueReference_qualifiedId() {
        var model = parse("""
                namespace com.example
                @type(com.example.OrderStatus)
                entity Order { id: String }
                """);
        var trait = model.entities().get(0).traits().get(0);
        var val = (TraitValue.ReferenceValue) trait.firstPositionalValue().orElseThrow();
        assertEquals("com.example.OrderStatus", val.qualifiedId());
    }

    // ── traitId: keyword alternatives as trait names ──────────────────────────

    @Test
    void traitId_keywordStep_asTraitName() {
        // 'step' is a grammar keyword; must be allowed as @step trait name
        var model = parse("""
                namespace com.example
                @step("checkout")
                entity Order { id: String }
                """);
        assertEquals("step", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordActor_asTraitName() {
        var model = parse("""
                namespace com.example
                @actor("Customer")
                entity Order { id: String }
                """);
        assertEquals("actor", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordEntity_asTraitName() {
        var model = parse("""
                namespace com.example
                @entity("Order")
                shape Money { amount: Float }
                """);
        assertEquals("entity", model.shapeStructs().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordShape_asTraitName() {
        var model = parse("""
                namespace com.example
                @shape("value")
                entity Order { id: String }
                """);
        assertEquals("shape", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordOutcome_asTraitName() {
        var model = parse("""
                namespace com.example
                @outcome("success")
                entity Order { id: String }
                """);
        assertEquals("outcome", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordVariants_asTraitName() {
        var model = parse("""
                namespace com.example
                @variants("alt-flows")
                entity Order { id: String }
                """);
        assertEquals("variants", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordTrigger_asTraitName() {
        var model = parse("""
                namespace com.example
                @trigger("event")
                entity Order { id: String }
                """);
        assertEquals("trigger", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordSuccess_asTraitName() {
        var model = parse("""
                namespace com.example
                @success("happy path")
                entity Order { id: String }
                """);
        assertEquals("success", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordFailure_asTraitName() {
        var model = parse("""
                namespace com.example
                @failure("sad path")
                entity Order { id: String }
                """);
        assertEquals("failure", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordAction_asTraitName() {
        var model = parse("""
                namespace com.example
                @action("click")
                entity Order { id: String }
                """);
        assertEquals("action", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordExpectation_asTraitName() {
        var model = parse("""
                namespace com.example
                @expectation("visible")
                entity Order { id: String }
                """);
        assertEquals("expectation", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordTelemetry_asTraitName() {
        var model = parse("""
                namespace com.example
                @telemetry("events")
                entity Order { id: String }
                """);
        assertEquals("telemetry", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordRisk_asTraitName() {
        var model = parse("""
                namespace com.example
                @risk("high")
                entity Order { id: String }
                """);
        assertEquals("risk", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordSteps_asTraitName() {
        var model = parse("""
                namespace com.example
                @steps("ordered")
                entity Order { id: String }
                """);
        assertEquals("steps", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordOutcomes_asTraitName() {
        var model = parse("""
                namespace com.example
                @outcomes("terminal")
                entity Order { id: String }
                """);
        assertEquals("outcomes", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordPolicy_asTraitName() {
        var model = parse("""
                namespace com.example
                @policy("gdpr")
                entity Order { id: String }
                """);
        assertEquals("policy", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordJourney_asTraitName() {
        var model = parse("""
                namespace com.example
                @journey("checkout")
                entity Order { id: String }
                """);
        assertEquals("journey", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordPreconditions_asTraitName() {
        var model = parse("""
                namespace com.example
                @preconditions("required")
                entity Order { id: String }
                """);
        assertEquals("preconditions", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordMember_asTraitName() {
        var model = parse("""
                namespace com.example
                @member("item")
                entity Order { id: String }
                """);
        assertEquals("member", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordKey_asTraitName() {
        var model = parse("""
                namespace com.example
                @key("pk")
                entity Order { id: String }
                """);
        assertEquals("key", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordValue_asTraitName() {
        var model = parse("""
                namespace com.example
                @value("v")
                entity Order { id: String }
                """);
        assertEquals("value", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordList_asTraitName() {
        var model = parse("""
                namespace com.example
                @list("ordered")
                entity Order { id: String }
                """);
        assertEquals("list", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordMap_asTraitName() {
        var model = parse("""
                namespace com.example
                @map("keyed")
                entity Order { id: String }
                """);
        assertEquals("map", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordEnum_asTraitName() {
        var model = parse("""
                namespace com.example
                @enum("closed")
                entity Order { id: String }
                """);
        assertEquals("enum", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordNamespace_asTraitName() {
        var model = parse("""
                namespace com.example
                @namespace("com.example")
                entity Order { id: String }
                """);
        assertEquals("namespace", model.entities().get(0).traits().get(0).name());
    }

    @Test
    void traitId_keywordUse_asTraitName() {
        var model = parse("""
                namespace com.example
                @use("com.other")
                entity Order { id: String }
                """);
        assertEquals("use", model.entities().get(0).traits().get(0).name());
    }

    // ── primitiveType: each primitive exercised via list/map context ───────────

    @Test
    void primitiveType_inListMemberType() {
        var model = parse("""
                namespace com.example
                list Tags   { member: String }
                list Counts { member: Integer }
                list Longs  { member: Long }
                list Floats { member: Float }
                list Flags  { member: Boolean }
                list Times  { member: Timestamp }
                list Blobs  { member: Blob }
                list Docs   { member: Document }
                """);
        assertEquals(8, model.lists().size());
        var kinds = model.lists().stream()
                .map(l -> ((TypeRef.PrimitiveType) l.memberType()).kind())
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
    void primitiveType_inMapKeyAndValue() {
        var model = parse("""
                namespace com.example
                map IntToFloat { key: Integer value: Float }
                map LongToBlob { key: Long value: Blob }
                map BoolToDoc  { key: Boolean value: Document }
                map TsToString { key: Timestamp value: String }
                """);
        assertEquals(4, model.maps().size());
        assertEquals(TypeRef.PrimitiveKind.INTEGER,
                ((TypeRef.PrimitiveType) model.maps().get(0).keyType()).kind());
        assertEquals(TypeRef.PrimitiveKind.FLOAT,
                ((TypeRef.PrimitiveType) model.maps().get(0).valueType()).kind());
        assertEquals(TypeRef.PrimitiveKind.LONG,
                ((TypeRef.PrimitiveType) model.maps().get(1).keyType()).kind());
        assertEquals(TypeRef.PrimitiveKind.BLOB,
                ((TypeRef.PrimitiveType) model.maps().get(1).valueType()).kind());
    }

    // ── stepField: multiple telemetry IDs ────────────────────────────────────

    @Test
    void stepField_multipleTelemetryIds() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    steps: [
                        step PlaceOrder {
                            action: "clicks confirm"
                            expectation: "order saved"
                            telemetry: [order_placed, payment_processed, email_queued]
                        }
                    ]
                }
                """);
        var step = model.journeys().get(0).steps().get(0);
        assertEquals(List.of("order_placed", "payment_processed", "email_queued"),
                step.telemetryEvents());
    }

    @Test
    void stepField_eachKeywordInIsolation() {
        // Exercises each stepField alternative in its own step so each parser branch fires
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    steps: [
                        step OnlyAction      { action: "actor clicks" expectation: "system reacts" },
                        step WithOutcome     { action: "a" expectation: "e"
                                              outcome: TransitionTo(Next) },
                        step WithRisk        { action: "a" expectation: "e"
                                              risk: "timeout risk" },
                        step WithTelemetry   { action: "a" expectation: "e"
                                              telemetry: [evt] }
                    ]
                }
                """);
        var steps = model.journeys().get(0).steps();
        assertEquals(4, steps.size());
        assertTrue(steps.get(1).outcome().isPresent());
        assertTrue(steps.get(2).risk().isPresent());
        assertFalse(steps.get(3).telemetryEvents().isEmpty());
    }

    // ── variantBody: without steps ────────────────────────────────────────────

    @Test
    void variantBody_noStepsNoOutcome() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    variants: {
                        NetworkError: {
                            trigger: "Connection timed out"
                        }
                    }
                }
                """);
        var variant = model.journeys().get(0).variants().get("NetworkError");
        assertNotNull(variant);
        assertEquals("Connection timed out", variant.trigger());
        assertTrue(variant.steps().isEmpty());
        assertTrue(variant.outcome().isEmpty());
    }

    @Test
    void variantBody_noStepsWithOutcome() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    variants: {
                        SessionExpired: {
                            trigger: "Token expired"
                            outcome: TransitionTo(LoginPage)
                        }
                    }
                }
                """);
        var variant = model.journeys().get(0).variants().get("SessionExpired");
        assertTrue(variant.steps().isEmpty());
        assertTrue(variant.outcome().isPresent());
        assertInstanceOf(OutcomeExpr.TransitionTo.class, variant.outcome().get());
        assertEquals("LoginPage",
                ((OutcomeExpr.TransitionTo) variant.outcome().get()).target());
    }

    @Test
    void variantBody_withStepsNoOutcome() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    variants: {
                        StockWarning: {
                            trigger: "Low inventory"
                            steps: [
                                step Notify {
                                    action: "show banner"
                                    expectation: "banner visible"
                                }
                            ]
                        }
                    }
                }
                """);
        var variant = model.journeys().get(0).variants().get("StockWarning");
        assertEquals(1, variant.steps().size());
        assertTrue(variant.outcome().isEmpty());
    }

    // ── variantsDecl: multiple variants ───────────────────────────────────────

    @Test
    void variantsDecl_multipleVariants() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    variants: {
                        PaymentDeclined: {
                            trigger: "Card declined"
                            outcome: ReturnToStep(EnterCard)
                        },
                        NetworkError: {
                            trigger: "Connection failed"
                            outcome: TransitionTo(ErrorPage)
                        },
                        SessionExpired: {
                            trigger: "Token expired"
                            outcome: TransitionTo(LoginPage)
                        }
                    }
                }
                """);
        var variants = model.journeys().get(0).variants();
        assertEquals(3, variants.size());
        assertTrue(variants.containsKey("PaymentDeclined"));
        assertTrue(variants.containsKey("NetworkError"));
        assertTrue(variants.containsKey("SessionExpired"));
        assertInstanceOf(OutcomeExpr.ReturnToStep.class,
                variants.get("PaymentDeclined").outcome().get());
        assertEquals("EnterCard",
                ((OutcomeExpr.ReturnToStep) variants.get("PaymentDeclined").outcome().get()).target());
    }

    // ── outcomeEntry: failure-before-success ordering ─────────────────────────

    @Test
    void outcomeEntry_failureBeforeSuccess() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    outcomes: {
                        failure: "Cart is restored",
                        success: "Order is confirmed"
                    }
                }
                """);
        var outcomes = model.journeys().get(0).journeyOutcomes().orElseThrow();
        assertEquals("Order is confirmed", outcomes.successOutcome().orElseThrow());
        assertEquals("Cart is restored",   outcomes.failureOutcome().orElseThrow());
    }

    @Test
    void outcomeEntry_onlyFailure() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    outcomes: {
                        failure: "Cart is restored"
                    }
                }
                """);
        var outcomes = model.journeys().get(0).journeyOutcomes().orElseThrow();
        assertTrue(outcomes.successOutcome().isEmpty());
        assertEquals("Cart is restored", outcomes.failureOutcome().orElseThrow());
    }

    @Test
    void outcomeEntry_onlySuccess() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    outcomes: {
                        success: "Order confirmed"
                    }
                }
                """);
        var outcomes = model.journeys().get(0).journeyOutcomes().orElseThrow();
        assertEquals("Order confirmed", outcomes.successOutcome().orElseThrow());
        assertTrue(outcomes.failureOutcome().isEmpty());
    }

    // ── journey with no actor (null actorDecl branch in visitJourneyDef) ──────

    @Test
    void journeyWithNoActorDecl() {
        var model = parse("""
                namespace com.example
                journey IncompleteJourney {
                }
                """);
        var journey = model.journeys().get(0);
        assertTrue(journey.actorName().isEmpty());
        assertTrue(journey.preconditions().isEmpty());
        assertTrue(journey.steps().isEmpty());
        assertTrue(journey.variants().isEmpty());
        assertTrue(journey.journeyOutcomes().isEmpty());
    }

    // ── doc comments: shape with no preceding doc comments ────────────────────

    @Test
    void docComments_shapeWithNoPrecedingDocComment() {
        var model = parse("""
                namespace com.example
                entity Order { id: String }
                entity Product { name: String }
                """);
        // Both entities should have empty doc comment lists
        assertTrue(model.entities().get(0).docComments().isEmpty());
        assertTrue(model.entities().get(1).docComments().isEmpty());
    }

    @Test
    void docComments_onlyFirstShapeHasDocComment() {
        var model = parse("""
                namespace com.example
                /// First entity
                entity Order { id: String }
                entity Product { name: String }
                """);
        assertEquals(List.of("First entity"), model.entities().get(0).docComments());
        assertTrue(model.entities().get(1).docComments().isEmpty());
    }
}
