package com.genairus.chronos.generators;

import com.genairus.chronos.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MarkdownPrdGenerator}.
 *
 * <p>Models are built directly from record constructors — no parser dependency needed.
 * Each test asserts that specific Markdown content is present (or absent) in the
 * generated output, rather than pinning exact full-document strings.
 */
class MarkdownPrdGeneratorTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);
    private static final MarkdownPrdGenerator GEN = new MarkdownPrdGenerator();

    // ── helpers ─────────────────────────────────────────────────────────────

    private static ChronosModel model(ShapeDefinition... shapes) {
        return new ChronosModel("com.example.checkout", List.of(), List.of(shapes));
    }

    private static TraitApplication trait(String name, String positionalString) {
        var arg = new TraitArg(null,
                new TraitValue.StringValue(positionalString), LOC);
        return new TraitApplication(name, List.of(arg), LOC);
    }

    private static TraitApplication namedTrait(String name, String key, String value) {
        var arg = new TraitArg(key, new TraitValue.StringValue(value), LOC);
        return new TraitApplication(name, List.of(arg), LOC);
    }

    private static TraitApplication namedTrait2(String name,
                                                  String k1, String v1,
                                                  String k2, String v2) {
        var a1 = new TraitArg(k1, new TraitValue.StringValue(v1), LOC);
        var a2 = new TraitArg(k2, new TraitValue.StringValue(v2), LOC);
        return new TraitApplication(name, List.of(a1, a2), LOC);
    }

    private static Step step(String name, String action, String expectation) {
        var fields = List.<StepField>of(
                new StepField.ActionField(action, LOC),
                new StepField.ExpectationField(expectation, LOC));
        return new Step(name, List.of(), fields, LOC);
    }

    private static Step stepWithAll(String name, String action, String expectation,
                                     OutcomeExpr outcome, List<String> events, String risk) {
        var fields = List.<StepField>of(
                new StepField.ActionField(action, LOC),
                new StepField.ExpectationField(expectation, LOC),
                new StepField.OutcomeField(outcome, LOC),
                new StepField.TelemetryField(events, LOC),
                new StepField.RiskField(risk, LOC));
        return new Step(name, List.of(), fields, LOC);
    }

    private static JourneyDef minimalJourney(String name) {
        return new JourneyDef(name, List.of(), List.of(),
                null, List.of(), List.of(), Map.of(), null, LOC);
    }

    private static String md(ChronosModel m) {
        var out = GEN.generate(m);
        return out.files().get("com-example-checkout-prd.md");
    }

    // ── filename ─────────────────────────────────────────────────────────────

    @Test
    void outputFilenameIsDerivedFromNamespace() {
        var out = GEN.generate(model(minimalJourney("J")));
        assertTrue(out.files().containsKey("com-example-checkout-prd.md"));
    }

    // ── title ─────────────────────────────────────────────────────────────────

    @Test
    void titleContainsNamespace() {
        var doc = md(model(minimalJourney("J")));
        assertTrue(doc.startsWith("# com.example.checkout — Product Requirements\n"));
    }

    // ── TOC: journeys ────────────────────────────────────────────────────────

    @Test
    void tocIncludesJourneySectionWhenPresent() {
        var doc = md(model(minimalJourney("GuestCheckout")));
        assertTrue(doc.contains("- [Journeys](#journeys)"));
        assertTrue(doc.contains("  - [GuestCheckout](#guestcheckout)"));
    }

    @Test
    void tocExcludesJourneySectionWhenNoJourneys() {
        var actor = new ActorDef("Customer", List.of(), List.of(), Optional.empty(), LOC);
        var doc = md(model(actor));
        assertFalse(doc.contains("- [Journeys]"));
    }

    // ── TOC: data-model subsections ───────────────────────────────────────────

    @Test
    void tocIncludesDataModelSubsectionsForPresentTypes() {
        var entity   = new EntityDef("Order", List.of(), List.of(), Optional.empty(), List.of(), List.of(), LOC);
        var struct   = new ShapeStructDef("Money", List.of(), List.of(), List.of(), LOC);
        var enumDef  = new EnumDef("Status", List.of(), List.of(),
                List.of(EnumMember.of("ACTIVE", LOC)), LOC);
        var listDef  = new ListDef("Tags", List.of(), List.of(),
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), LOC);
        var mapDef   = new MapDef("Headers", List.of(), List.of(),
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING),
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), LOC);
        var doc = md(model(entity, struct, enumDef, listDef, mapDef));

        assertTrue(doc.contains("- [Data Model](#data-model)"));
        assertTrue(doc.contains("  - [Entities](#entities)"));
        assertTrue(doc.contains("  - [Value Objects](#value-objects)"));
        assertTrue(doc.contains("  - [Enumerations](#enumerations)"));
        assertTrue(doc.contains("  - [Collections](#collections)"));
    }

    @Test
    void tocExcludesDataModelWhenNoDataShapes() {
        var doc = md(model(minimalJourney("J")));
        assertFalse(doc.contains("Data Model"));
    }

    @Test
    void tocIncludesActorsAndPoliciesWhenPresent() {
        var actor  = new ActorDef("Customer", List.of(), List.of(), Optional.empty(), LOC);
        var policy = new PolicyDef("DataPolicy", "Retain 7y", List.of(), List.of(), LOC);
        var doc = md(model(actor, policy));
        assertTrue(doc.contains("- [Actors](#actors)"));
        assertTrue(doc.contains("- [Policies](#policies)"));
    }

    @Test
    void tocExcludesActorsAndPoliciesWhenAbsent() {
        var doc = md(model(minimalJourney("J")));
        assertFalse(doc.contains("- [Actors]"));
        assertFalse(doc.contains("- [Policies]"));
    }

    // ── Journey: metadata block ───────────────────────────────────────────────

    @Test
    void journeyMetadataShowsActor() {
        var journey = new JourneyDef("Checkout", List.of(), List.of(),
                "Customer", List.of(), List.of(), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("**Actor:** Customer"));
    }

    @Test
    void journeyMetadataShowsKpiWithTarget() {
        var kpi = namedTrait2("kpi", "metric", "CheckoutConversion", "target", ">75%");
        var journey = new JourneyDef("Checkout", List.of(kpi), List.of(),
                null, List.of(), List.of(), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("**KPI:** CheckoutConversion → >75%"));
    }

    @Test
    void journeyMetadataShowsCompliance() {
        var compliance = trait("compliance", "GDPR");
        var journey = new JourneyDef("Checkout", List.of(compliance), List.of(),
                null, List.of(), List.of(), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("**Compliance:** GDPR"));
    }

    // ── Journey: doc comments ─────────────────────────────────────────────────

    @Test
    void journeyDocCommentsRenderedAsBlockquote() {
        var journey = new JourneyDef("Checkout", List.of(),
                List.of("Handles guest purchases", "No login required"),
                null, List.of(), List.of(), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("> Handles guest purchases\n"));
        assertTrue(doc.contains("> No login required\n"));
    }

    // ── Journey: preconditions ────────────────────────────────────────────────

    @Test
    void journeyPreconditionsRenderedAsBullets() {
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of("Cart is not empty", "User is not logged in"),
                List.of(), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("**Preconditions**"));
        assertTrue(doc.contains("- Cart is not empty\n"));
        assertTrue(doc.contains("- User is not logged in\n"));
    }

    @Test
    void journeyWithNoPreconditionsOmitsPreconditionsBlock() {
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertFalse(doc.contains("**Preconditions**"));
    }

    // ── Journey: happy-path step table ────────────────────────────────────────

    @Test
    void stepTableRendersAllColumns() {
        var s = stepWithAll("PlaceOrder",
                "Clicks confirm", "Order saved",
                new OutcomeExpr.TransitionTo("Confirmed", LOC),
                List.of("order_placed", "payment_done"),
                "Gateway may be slow");
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(s), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("**Happy Path**"));
        assertTrue(doc.contains("| Step | Action | Expectation | Outcome | Telemetry | Risk |"));
        assertTrue(doc.contains("| PlaceOrder | Clicks confirm | Order saved "
                + "| TransitionTo(Confirmed) | order_placed, payment_done | Gateway may be slow |"));
    }

    @Test
    void stepTableUsesDashForMissingFields() {
        var s = new Step("EmptyStep", List.of(), List.of(), LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(s), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("| EmptyStep | — | — | — | — | — |"));
    }

    @Test
    void stepTableRendersReturnToStep() {
        var s = new Step("Retry", List.of(),
                List.of(new StepField.ActionField("retry", LOC),
                        new StepField.ExpectationField("ok", LOC),
                        new StepField.OutcomeField(
                                new OutcomeExpr.ReturnToStep("PlaceOrder", LOC), LOC)),
                LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(s), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("ReturnToStep(PlaceOrder)"));
    }

    @Test
    void journeyWithNoStepsOmitsHappyPathBlock() {
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertFalse(doc.contains("**Happy Path**"));
    }

    // ── Journey: variants ─────────────────────────────────────────────────────

    @Test
    void variantRenderedWithTriggerAndOutcome() {
        var variant = new Variant("PaymentDeclined",
                "CardDeclinedError",
                List.of(),
                Optional.of(new OutcomeExpr.ReturnToStep("EnterCard", LOC)),
                LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(), Map.of("PaymentDeclined", variant), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("**Variants**"));
        assertTrue(doc.contains("#### PaymentDeclined"));
        assertTrue(doc.contains("- **Trigger:** CardDeclinedError"));
        assertTrue(doc.contains("- **Outcome:** ReturnToStep(EnterCard)"));
    }

    @Test
    void variantWithStepsRendersStepTable() {
        var variant = new Variant("NetworkError",
                "TimeoutError",
                List.of(step("Notify", "show msg", "msg visible")),
                Optional.empty(),
                LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(), Map.of("NetworkError", variant), null, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("| Notify |"));
    }

    @Test
    void journeyWithNoVariantsOmitsVariantsBlock() {
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(), Map.of(), null, LOC);
        var doc = md(model(journey));
        assertFalse(doc.contains("**Variants**"));
    }

    // ── Journey: outcomes ─────────────────────────────────────────────────────

    @Test
    void outcomesRenderedWithSuccessAndFailure() {
        var outcomes = new JourneyOutcomes("Order confirmed", "Cart intact", LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(), Map.of(), outcomes, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("**Outcomes**"));
        assertTrue(doc.contains("✅ Success: Order confirmed"));
        assertTrue(doc.contains("❌ Failure: Cart intact"));
    }

    @Test
    void outcomesOmitsFailureLineWhenAbsent() {
        var outcomes = new JourneyOutcomes("Order confirmed", null, LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(), "Customer",
                List.of(), List.of(), Map.of(), outcomes, LOC);
        var doc = md(model(journey));
        assertTrue(doc.contains("✅ Success: Order confirmed"));
        assertFalse(doc.contains("❌ Failure"));
    }

    @Test
    void journeyWithNoOutcomesBlockOmitsOutcomesSection() {
        var journey = minimalJourney("Checkout");
        var doc = md(model(journey));
        assertFalse(doc.contains("**Outcomes**"));
    }

    // ── Data model: entities ──────────────────────────────────────────────────

    @Test
    void entityFieldTableRendered() {
        var idField = new FieldDef("id",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING),
                List.of(new TraitApplication("required", List.of(), LOC)), LOC);
        var totalField = new FieldDef("total",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.FLOAT),
                List.of(), LOC);
        var entity = new EntityDef("Order", List.of(), List.of(), Optional.empty(), List.of(idField, totalField), List.of(), LOC);
        var doc = md(model(entity));

        assertTrue(doc.contains("## Data Model"));
        assertTrue(doc.contains("### Entities"));
        assertTrue(doc.contains("#### Order"));
        assertTrue(doc.contains("| Field | Type | Required |"));
        assertTrue(doc.contains("| id | String | ✓ |"));
        assertTrue(doc.contains("| total | Float |  |"));
    }

    @Test
    void entityDocCommentsRenderedAsBlockquote() {
        var entity = new EntityDef("Order", List.of(),
                List.of("Main order record", "Tracks lifecycle"),
                Optional.empty(), List.of(), List.of(), LOC);
        var doc = md(model(entity));
        assertTrue(doc.contains("> Main order record\n"));
        assertTrue(doc.contains("> Tracks lifecycle\n"));
    }

    @Test
    void entityWithNoFieldsRendersHeaderOnly() {
        var entity = new EntityDef("Empty", List.of(), List.of(), Optional.empty(), List.of(), List.of(), LOC);
        var doc = md(model(entity));
        assertTrue(doc.contains("#### Empty"));
        assertFalse(doc.contains("| Field |"));
    }

    @Test
    void entityWithInheritanceShowsParentAndAllFields() {
        var idField = new FieldDef("id",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC);
        var emailField = new FieldDef("email",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC);
        var tierField = new FieldDef("tier",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC);

        var parent = new EntityDef("User", List.of(), List.of(), Optional.empty(),
                List.of(idField, emailField), List.of(), LOC);
        var child = new EntityDef("PremiumUser", List.of(), List.of(), Optional.of("User"),
                List.of(tierField), List.of(), LOC);

        var doc = md(model(parent, child));

        // Parent entity should show its own fields
        assertTrue(doc.contains("#### User"));
        assertTrue(doc.contains("| id | String |"));
        assertTrue(doc.contains("| email | String |"));

        // Child entity should show parent reference and all fields (inherited + own)
        assertTrue(doc.contains("#### PremiumUser"));
        assertTrue(doc.contains("*Extends: User*"));
        assertTrue(doc.contains("| id | String |"));  // inherited
        assertTrue(doc.contains("| email | String |"));  // inherited
        assertTrue(doc.contains("| tier | String |"));  // own field
    }

    // ── Data model: shape structs ─────────────────────────────────────────────

    @Test
    void shapeStructRenderedUnderValueObjectsHeading() {
        var field = new FieldDef("amount",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.FLOAT), List.of(), LOC);
        var shape = new ShapeStructDef("Money", List.of(), List.of(), List.of(field), LOC);
        var doc = md(model(shape));
        assertTrue(doc.contains("### Value Objects"));
        assertTrue(doc.contains("#### Money"));
        assertTrue(doc.contains("| amount | Float |"));
    }

    // ── Data model: complex types ─────────────────────────────────────────────

    @Test
    void listAndMapTypeRefsRenderedCorrectly() {
        var listField = new FieldDef("tags",
                new TypeRef.ListType(new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING)),
                List.of(), LOC);
        var mapField = new FieldDef("meta",
                new TypeRef.MapType(
                        new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING),
                        new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.INTEGER)),
                List.of(), LOC);
        var namedField = new FieldDef("status",
                new TypeRef.NamedTypeRef("OrderStatus"), List.of(), LOC);
        var entity = new EntityDef("Order", List.of(), List.of(), Optional.empty(),
                List.of(listField, mapField, namedField), List.of(), LOC);
        var doc = md(model(entity));
        assertTrue(doc.contains("| tags | List<String> |"));
        assertTrue(doc.contains("| meta | Map<String, Integer> |"));
        assertTrue(doc.contains("| status | OrderStatus |"));
    }

    // ── Data model: enums ────────────────────────────────────────────────────

    @Test
    void enumMemberTableRendered() {
        var members = List.of(
                EnumMember.of("PENDING", 1, LOC),
                EnumMember.of("PAID",    2, LOC),
                EnumMember.of("FAILED",     LOC));
        var enumDef = new EnumDef("OrderStatus", List.of(), List.of(), members, LOC);
        var doc = md(model(enumDef));
        assertTrue(doc.contains("### Enumerations"));
        assertTrue(doc.contains("#### OrderStatus"));
        assertTrue(doc.contains("| PENDING | 1 |"));
        assertTrue(doc.contains("| PAID | 2 |"));
        assertTrue(doc.contains("| FAILED | — |"));
    }

    // ── Data model: collections ───────────────────────────────────────────────

    @Test
    void listDefRenderedInCollections() {
        var listDef = new ListDef("Tags", List.of(), List.of(),
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), LOC);
        var doc = md(model(listDef));
        assertTrue(doc.contains("### Collections"));
        assertTrue(doc.contains("- **Tags** — `List<String>`"));
    }

    @Test
    void mapDefRenderedInCollections() {
        var mapDef = new MapDef("Headers", List.of(), List.of(),
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING),
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), LOC);
        var doc = md(model(mapDef));
        assertTrue(doc.contains("- **Headers** — `Map<String, String>`"));
    }

    // ── Actors ────────────────────────────────────────────────────────────────

    @Test
    void actorWithDescriptionRendered() {
        var actor = new ActorDef("Customer",
                List.of(trait("description", "A guest user")), List.of(), Optional.empty(), LOC);
        var doc = md(model(actor));
        assertTrue(doc.contains("## Actors"));
        assertTrue(doc.contains("| Customer | A guest user |"));
    }

    @Test
    void actorWithoutDescriptionShowsDash() {
        var actor = new ActorDef("Customer", List.of(), List.of(), Optional.empty(), LOC);
        var doc = md(model(actor));
        assertTrue(doc.contains("| Customer | — |"));
    }

    // ── Policies ─────────────────────────────────────────────────────────────

    @Test
    void policyWithComplianceFrameworkRendered() {
        var policy = new PolicyDef("DataRetention",
                "Personal data purged after 7 years",
                List.of(trait("compliance", "GDPR")), List.of(), LOC);
        var doc = md(model(policy));
        assertTrue(doc.contains("## Policies"));
        assertTrue(doc.contains("| DataRetention | Personal data purged after 7 years | GDPR |"));
    }

    @Test
    void policyWithoutComplianceShowsDash() {
        var policy = new PolicyDef("InternalPolicy", "Keep data", List.of(), List.of(), LOC);
        var doc = md(model(policy));
        assertTrue(doc.contains("| InternalPolicy | Keep data | — |"));
    }

    // ── Prohibitions ──────────────────────────────────────────────────────────

    @Test
    void denyWithComplianceRendered() {
        var deny = new DenyDef("StorePlaintextPasswords",
                List.of(trait("compliance", "PCI-DSS")),
                List.of(),
                "The system must never store passwords in plaintext",
                List.of("UserCredential"),
                "critical",
                LOC);
        var doc = md(model(deny));
        assertTrue(doc.contains("## Prohibitions"));
        assertTrue(doc.contains("| StorePlaintextPasswords | The system must never store passwords in plaintext | UserCredential | critical | PCI-DSS |"));
    }

    @Test
    void denyWithoutComplianceShowsDash() {
        var deny = new DenyDef("InternalProhibition",
                List.of(),
                List.of(),
                "Do not do this",
                List.of("Entity1"),
                "low",
                LOC);
        var doc = md(model(deny));
        assertTrue(doc.contains("| InternalProhibition | Do not do this | Entity1 | low | — |"));
    }

    @Test
    void denyWithMultipleScopesRendered() {
        var deny = new DenyDef("ExposePIIInLogs",
                List.of(trait("compliance", "GDPR")),
                List.of(),
                "PII must never appear in logs",
                List.of("CustomerProfile", "PaymentInfo"),
                "critical",
                LOC);
        var doc = md(model(deny));
        assertTrue(doc.contains("| ExposePIIInLogs | PII must never appear in logs | CustomerProfile, PaymentInfo | critical | GDPR |"));
    }

    @Test
    void prohibitionsSectionInToc() {
        var deny = new DenyDef("TestDeny", List.of(), List.of(), "Test", List.of("Entity1"), "high", LOC);
        var doc = md(model(deny));
        assertTrue(doc.contains("- [Prohibitions](#prohibitions)"));
    }

    // ── Error Catalog ─────────────────────────────────────────────────────────

    @Test
    void errorWithPayloadRendered() {
        var payloadField1 = new FieldDef("declineReason",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC);
        var payloadField2 = new FieldDef("retryAllowed",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.BOOLEAN), List.of(), LOC);
        var error = new ErrorDef("PaymentDeclinedError",
                List.of(),
                List.of(),
                "PAYMENT_DECLINED",
                "high",
                true,
                "Payment gateway returned a declined response",
                List.of(payloadField1, payloadField2),
                LOC);
        var doc = md(model(error));
        assertTrue(doc.contains("## Error Catalog"));
        assertTrue(doc.contains("| PaymentDeclinedError | PAYMENT_DECLINED | high | Yes | Payment gateway returned a declined response | declineReason: String, retryAllowed: Boolean |"));
    }

    @Test
    void errorWithoutPayloadShowsDash() {
        var error = new ErrorDef("TimeoutError",
                List.of(),
                List.of(),
                "TIMEOUT",
                "medium",
                false,
                "Operation timed out",
                List.of(),
                LOC);
        var doc = md(model(error));
        assertTrue(doc.contains("| TimeoutError | TIMEOUT | medium | No | Operation timed out | — |"));
    }

    @Test
    void errorCatalogSectionInToc() {
        var error = new ErrorDef("TestError", List.of(), List.of(), "TEST-001", "low", true, "Test error", List.of(), LOC);
        var doc = md(model(error));
        assertTrue(doc.contains("- [Error Catalog](#error-catalog)"));
    }

    @Test
    void multipleErrorsRenderedInCatalog() {
        var error1 = new ErrorDef("Error1", List.of(), List.of(), "ERR-001", "critical", false, "Critical error", List.of(), LOC);
        var error2 = new ErrorDef("Error2", List.of(), List.of(), "ERR-002", "low", true, "Minor error", List.of(), LOC);
        var doc = md(model(error1, error2));
        assertTrue(doc.contains("| Error1 | ERR-001 | critical | No | Critical error | — |"));
        assertTrue(doc.contains("| Error2 | ERR-002 | low | Yes | Minor error | — |"));
    }

    // ── Full PRD smoke test ───────────────────────────────────────────────────

    @Test
    void fullModelProducesAllSections() {
        var kpi = namedTrait2("kpi", "metric", "Conversion", "target", ">80%");
        var step1 = step("EnterEmail", "Types email", "Field populated");
        var outcomes = new JourneyOutcomes("Order placed", "Cart intact", LOC);
        var journey = new JourneyDef("GuestCheckout", List.of(kpi),
                List.of("Main checkout flow"),
                "Customer",
                List.of("Cart not empty"),
                List.of(step1), Map.of(), outcomes, LOC);

        var entity = new EntityDef("Order", List.of(), List.of("The order"), Optional.empty(),
                List.of(new FieldDef("id",
                        new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC)),
                List.of(), LOC);
        var actor = new ActorDef("Customer",
                List.of(trait("description", "Guest user")), List.of(), Optional.empty(), LOC);
        var policy = new PolicyDef("PaymentPolicy", "PCI compliance",
                List.of(trait("compliance", "PCI-DSS")), List.of(), LOC);

        var doc = md(model(journey, entity, actor, policy));

        // Title
        assertTrue(doc.startsWith("# com.example.checkout — Product Requirements"));
        // TOC
        assertTrue(doc.contains("[Journeys](#journeys)"));
        assertTrue(doc.contains("[GuestCheckout](#guestcheckout)"));
        assertTrue(doc.contains("[Data Model](#data-model)"));
        assertTrue(doc.contains("[Entities](#entities)"));
        assertTrue(doc.contains("[Actors](#actors)"));
        assertTrue(doc.contains("[Policies](#policies)"));
        // Journey
        assertTrue(doc.contains("### GuestCheckout"));
        assertTrue(doc.contains("> Main checkout flow"));
        assertTrue(doc.contains("**Actor:** Customer"));
        assertTrue(doc.contains("**KPI:** Conversion → >80%"));
        assertTrue(doc.contains("- Cart not empty"));
        assertTrue(doc.contains("| EnterEmail | Types email | Field populated |"));
        assertTrue(doc.contains("✅ Success: Order placed"));
        assertTrue(doc.contains("❌ Failure: Cart intact"));
        // Entity
        assertTrue(doc.contains("### Entities"));
        assertTrue(doc.contains("> The order"));
        assertTrue(doc.contains("| id | String |"));
        // Actor
        assertTrue(doc.contains("| Customer | Guest user |"));
        // Policy
        assertTrue(doc.contains("| PaymentPolicy | PCI compliance | PCI-DSS |"));
    }
}
