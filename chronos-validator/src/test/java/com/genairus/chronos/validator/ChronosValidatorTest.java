package com.genairus.chronos.validator;

import com.genairus.chronos.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChronosValidator} — one pass-case and one fail-case
 * per rule, CHR-001 through CHR-010.
 */
class ChronosValidatorTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);
    private final ChronosValidator validator = new ChronosValidator();

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Minimal valid step — has both action and expectation. */
    private static Step validStep(String name) {
        return new Step(name, List.of(), List.of(
                new StepField.ActionField("actor does something", LOC),
                new StepField.ExpectationField("system responds", LOC)
        ), LOC);
    }

    /** Minimal valid outcomes (success + failure). */
    private static JourneyOutcomes validOutcomes() {
        return new JourneyOutcomes("Order confirmed", "Cart intact", LOC);
    }

    /**
     * Build a minimal fully-valid journey. Tests that need to break one rule
     * can call this and override the relevant parameter via a different constructor.
     */
    private static JourneyDef validJourney(String name) {
        return new JourneyDef(name, List.of(), List.of(),
                "Customer", List.of(), List.of(validStep("Step1")),
                Map.of(), validOutcomes(), LOC);
    }

    /** Wrap shapes in a minimal model with a namespace. */
    private static ChronosModel model(ShapeDefinition... shapes) {
        return new ChronosModel("com.example", List.of(), List.of(shapes));
    }

    // ── CHR-001 ───────────────────────────────────────────────────────────────

    @Test
    void chr001_passWith_actorDeclared() {
        var result = validator.validate(model(validJourney("Checkout")));
        assertTrue(result.errors().stream().noneMatch(i -> "CHR-001".equals(i.ruleCode())));
    }

    @Test
    void chr001_failWith_missingActor() {
        var journey = new JourneyDef("Checkout", List.of(), List.of(),
                null, List.of(), List.of(validStep("S1")),
                Map.of(), validOutcomes(), LOC);
        var result = validator.validate(model(journey));
        var errors = result.errors().stream().filter(i -> "CHR-001".equals(i.ruleCode())).toList();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).message().contains("Checkout"));
    }

    // ── CHR-002 ───────────────────────────────────────────────────────────────

    @Test
    void chr002_passWith_outcomesAndSuccess() {
        var result = validator.validate(model(validJourney("Checkout")));
        assertTrue(result.errors().stream().noneMatch(i -> "CHR-002".equals(i.ruleCode())));
    }

    @Test
    void chr002_failWith_missingOutcomesBlock() {
        var journey = new JourneyDef("Checkout", List.of(), List.of(),
                "Customer", List.of(), List.of(validStep("S1")),
                Map.of(), null, LOC);
        var result = validator.validate(model(journey));
        var errors = result.errors().stream().filter(i -> "CHR-002".equals(i.ruleCode())).toList();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).message().contains("outcomes block"));
    }

    @Test
    void chr002_failWith_missingSuccessOutcome() {
        var outcomes = new JourneyOutcomes(null, "Cart intact", LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(),
                "Customer", List.of(), List.of(validStep("S1")),
                Map.of(), outcomes, LOC);
        var result = validator.validate(model(journey));
        var errors = result.errors().stream().filter(i -> "CHR-002".equals(i.ruleCode())).toList();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).message().contains("success outcome"));
    }

    // ── CHR-003 ───────────────────────────────────────────────────────────────

    @Test
    void chr003_passWith_actionAndExpectation() {
        var result = validator.validate(model(validJourney("Checkout")));
        assertTrue(result.errors().stream().noneMatch(i -> "CHR-003".equals(i.ruleCode())));
    }

    @Test
    void chr003_failWith_missingAction() {
        var step = new Step("BadStep", List.of(), List.of(
                new StepField.ExpectationField("system responds", LOC)
        ), LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(),
                "Customer", List.of(), List.of(step),
                Map.of(), validOutcomes(), LOC);
        var result = validator.validate(model(journey));
        var errors = result.errors().stream().filter(i -> "CHR-003".equals(i.ruleCode())).toList();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).message().contains("action"));
    }

    @Test
    void chr003_failWith_missingExpectation() {
        var step = new Step("BadStep", List.of(), List.of(
                new StepField.ActionField("actor does something", LOC)
        ), LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(),
                "Customer", List.of(), List.of(step),
                Map.of(), validOutcomes(), LOC);
        var result = validator.validate(model(journey));
        var errors = result.errors().stream().filter(i -> "CHR-003".equals(i.ruleCode())).toList();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).message().contains("expectation"));
    }

    @Test
    void chr003_checksVariantStepsToo() {
        var badStep = new Step("VariantStep", List.of(), List.of(), LOC);
        var variant = new Variant("AltFlow", "trigger text", List.of(badStep), Optional.empty(), LOC);
        var journey = new JourneyDef("Checkout", List.of(), List.of(),
                "Customer", List.of(), List.of(validStep("S1")),
                Map.of("AltFlow", variant), validOutcomes(), LOC);
        var result = validator.validate(model(journey));
        var chr003 = result.errors().stream().filter(i -> "CHR-003".equals(i.ruleCode())).toList();
        assertEquals(2, chr003.size()); // missing action + missing expectation
        assertTrue(chr003.get(0).message().contains("Checkout/AltFlow"));
    }

    // ── CHR-004 ───────────────────────────────────────────────────────────────

    @Test
    void chr004_passWith_atLeastOneStep() {
        var result = validator.validate(model(validJourney("Checkout")));
        assertTrue(result.warnings().stream().noneMatch(i -> "CHR-004".equals(i.ruleCode())));
    }

    @Test
    void chr004_warnWith_noSteps() {
        var journey = new JourneyDef("Checkout", List.of(), List.of(),
                "Customer", List.of(), List.of(),
                Map.of(), validOutcomes(), LOC);
        var result = validator.validate(model(journey));
        var warns = result.warnings().stream().filter(i -> "CHR-004".equals(i.ruleCode())).toList();
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).message().contains("no steps"));
    }

    // ── CHR-005 ───────────────────────────────────────────────────────────────

    @Test
    void chr005_passWithUniqueNames() {
        var e1 = new EntityDef("Order", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("id", new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC)), List.of(), LOC);
        var e2 = new EntityDef("Product", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("id", new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC)), List.of(), LOC);
        var result = validator.validate(model(e1, e2));
        assertTrue(result.errors().stream().noneMatch(i -> "CHR-005".equals(i.ruleCode())));
    }

    @Test
    void chr005_failWith_duplicateName() {
        var e1 = new EntityDef("Order", List.of(), List.of(), Optional.empty(), List.of(), List.of(), LOC);
        var e2 = new EntityDef("Order", List.of(), List.of(), Optional.empty(), List.of(), List.of(), LOC);
        var result = validator.validate(model(e1, e2));
        var errors = result.errors().stream().filter(i -> "CHR-005".equals(i.ruleCode())).toList();
        assertEquals(2, errors.size()); // one for each occurrence
        assertTrue(errors.get(0).message().contains("Order"));
    }

    // ── CHR-006 ───────────────────────────────────────────────────────────────

    @Test
    void chr006_passWithFields() {
        var entity = new EntityDef("Order", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("id", new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC)), List.of(), LOC);
        var result = validator.validate(model(entity));
        assertTrue(result.warnings().stream().noneMatch(i -> "CHR-006".equals(i.ruleCode())));
    }

    @Test
    void chr006_warnWith_emptyEntity() {
        var entity = new EntityDef("EmptyOrder", List.of(), List.of(), Optional.empty(), List.of(), List.of(), LOC);
        var result = validator.validate(model(entity));
        var warns = result.warnings().stream().filter(i -> "CHR-006".equals(i.ruleCode())).toList();
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).message().contains("EmptyOrder"));
    }

    @Test
    void chr006_warnWith_emptyShapeStruct() {
        var shape = new ShapeStructDef("EmptyMoney", List.of(), List.of(), List.of(), LOC);
        var result = validator.validate(model(shape));
        var warns = result.warnings().stream().filter(i -> "CHR-006".equals(i.ruleCode())).toList();
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).message().contains("EmptyMoney"));
    }

    // ── CHR-007 ───────────────────────────────────────────────────────────────

    @Test
    void chr007_passWith_descriptionTrait() {
        var descArg = new TraitArg(null, new TraitValue.StringValue("A paying customer"), LOC);
        var descTrait = new TraitApplication("description", List.of(descArg), LOC);
        var actor = new ActorDef("Customer", List.of(descTrait), List.of(), Optional.empty(), LOC);
        var result = validator.validate(model(actor));
        assertTrue(result.warnings().stream().noneMatch(i -> "CHR-007".equals(i.ruleCode())));
    }

    @Test
    void chr007_warnWith_missingDescription() {
        var actor = new ActorDef("Customer", List.of(), List.of(), Optional.empty(), LOC);
        var result = validator.validate(model(actor));
        var warns = result.warnings().stream().filter(i -> "CHR-007".equals(i.ruleCode())).toList();
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).message().contains("Customer"));
    }

    // ── CHR-008 ───────────────────────────────────────────────────────────────

    @Test
    void chr008_passWithResolvedNamedRef() {
        var order = new EntityDef("Order", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("id", new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC)), List.of(), LOC);
        var entity = new EntityDef("Cart", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("order", new TypeRef.NamedTypeRef("Order"), List.of(), LOC)), List.of(), LOC);
        var result = validator.validate(model(order, entity));
        assertTrue(result.errors().stream().noneMatch(i -> "CHR-008".equals(i.ruleCode())));
    }

    @Test
    void chr008_passWithImportedRef() {
        var entity = new EntityDef("Cart", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("order", new TypeRef.NamedTypeRef("Order"), List.of(), LOC)), List.of(), LOC);
        var modelWithImport = new ChronosModel("com.example",
                List.of(new UseDecl("com.other", "Order", LOC)),
                List.of(entity));
        var result = validator.validate(modelWithImport);
        assertTrue(result.errors().stream().noneMatch(i -> "CHR-008".equals(i.ruleCode())));
    }

    @Test
    void chr008_failWith_unresolvedRef() {
        var entity = new EntityDef("Cart", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("order", new TypeRef.NamedTypeRef("UnknownType"), List.of(), LOC)), List.of(), LOC);
        var result = validator.validate(model(entity));
        var errors = result.errors().stream().filter(i -> "CHR-008".equals(i.ruleCode())).toList();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).message().contains("UnknownType"));
    }

    @Test
    void chr008_checksNestedListTypeRef() {
        var entity = new EntityDef("Cart", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("items",
                        new TypeRef.ListType(new TypeRef.NamedTypeRef("Ghost")), List.of(), LOC)), List.of(), LOC);
        var result = validator.validate(model(entity));
        var errors = result.errors().stream().filter(i -> "CHR-008".equals(i.ruleCode())).toList();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).message().contains("Ghost"));
    }

    // ── CHR-009 ───────────────────────────────────────────────────────────────

    @Test
    void chr009_passWith_kpiTrait() {
        var metricArg = new TraitArg("metric", new TraitValue.StringValue("Conv"), LOC);
        var targetArg = new TraitArg("target", new TraitValue.StringValue(">75%"), LOC);
        var kpi = new TraitApplication("kpi", List.of(metricArg, targetArg), LOC);
        var journey = new JourneyDef("Checkout", List.of(kpi), List.of(),
                "Customer", List.of(), List.of(validStep("S1")),
                Map.of(), validOutcomes(), LOC);
        var result = validator.validate(model(journey));
        assertTrue(result.warnings().stream().noneMatch(i -> "CHR-009".equals(i.ruleCode())));
    }

    @Test
    void chr009_warnWith_missingKpi() {
        var result = validator.validate(model(validJourney("Checkout")));
        var warns = result.warnings().stream().filter(i -> "CHR-009".equals(i.ruleCode())).toList();
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).message().contains("Checkout"));
    }

    // ── CHR-010 ───────────────────────────────────────────────────────────────

    @Test
    void chr010_passWhenNoCompliancePolicies() {
        // No policies at all — CHR-010 should not fire
        var result = validator.validate(model(validJourney("Checkout")));
        assertTrue(result.warnings().stream().noneMatch(i -> "CHR-010".equals(i.ruleCode())));
    }

    @Test
    void chr010_passWithComplianceTrait() {
        var compliancePolicy = new PolicyDef("GdprRetention", "Purge data after 7 years",
                List.of(new TraitApplication("compliance",
                        List.of(new TraitArg(null, new TraitValue.StringValue("GDPR"), LOC)), LOC)),
                List.of(), LOC);
        var complianceArg   = new TraitArg(null, new TraitValue.StringValue("GDPR"), LOC);
        var complianceTrait = new TraitApplication("compliance", List.of(complianceArg), LOC);
        var journey = new JourneyDef("Checkout", List.of(complianceTrait), List.of(),
                "Customer", List.of(), List.of(validStep("S1")),
                Map.of(), validOutcomes(), LOC);
        var result = validator.validate(model(compliancePolicy, journey));
        assertTrue(result.warnings().stream().noneMatch(i -> "CHR-010".equals(i.ruleCode())));
    }

    @Test
    void chr010_warnWith_missingComplianceOnJourney() {
        var compliancePolicy = new PolicyDef("GdprRetention", "Purge data after 7 years",
                List.of(new TraitApplication("compliance",
                        List.of(new TraitArg(null, new TraitValue.StringValue("GDPR"), LOC)), LOC)),
                List.of(), LOC);
        var result = validator.validate(model(compliancePolicy, validJourney("Checkout")));
        var warns = result.warnings().stream().filter(i -> "CHR-010".equals(i.ruleCode())).toList();
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).message().contains("Checkout"));
    }

    // ── multi-rule interaction ─────────────────────────────────────────────────

    @Test
    void cleanModelProducesNoIssues() {
        var kpiArg     = new TraitArg("metric", new TraitValue.StringValue("Conv"), LOC);
        var targetArg  = new TraitArg("target", new TraitValue.StringValue(">75%"), LOC);
        var kpi        = new TraitApplication("kpi", List.of(kpiArg, targetArg), LOC);
        var descArg    = new TraitArg(null, new TraitValue.StringValue("A buyer"), LOC);
        var descTrait  = new TraitApplication("description", List.of(descArg), LOC);
        var actor      = new ActorDef("Customer", List.of(descTrait), List.of(), Optional.empty(), LOC);
        var entity     = new EntityDef("Order", List.of(), List.of(), Optional.empty(),
                List.of(new FieldDef("id",
                        new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), List.of(), LOC)), List.of(), LOC);
        var journey    = new JourneyDef("Checkout", List.of(kpi), List.of(),
                "Customer", List.of(), List.of(validStep("PlaceOrder")),
                Map.of(), validOutcomes(), LOC);
        var result = validator.validate(model(actor, entity, journey));
        assertTrue(result.isEmpty(),
                "Expected no issues but got: " + result.issues());
    }
}
