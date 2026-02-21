package com.genairus.chronos.generators;

import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JiraBacklogGenerator}.
 *
 * <p>Models are built directly from record constructors — no parser dependency needed.
 * Tests assert structural properties of CSV rows (Issue Type, ordering, fields) rather
 * than pinning exact full-document strings.
 */
class JiraBacklogGeneratorTest {

    private static final Span LOC = Span.at("test.chronos", 1, 1);
    private static final JiraBacklogGenerator GEN = new JiraBacklogGenerator();

    // ── helpers ──────────────────────────────────────────────────────────────

    private static IrModel model(IrShape... shapes) {
        return new IrModel("com.example.shop", List.of(), List.of(shapes));
    }

    private static SymbolRef actorRef(String name) {
        return SymbolRef.unresolved(SymbolKind.ACTOR, new QualifiedName(null, name), Span.UNKNOWN);
    }

    private static TraitApplication stringTrait(String name, String value) {
        var arg = new TraitArg(null, new TraitValue.StringValue(value), LOC);
        return new TraitApplication(name, List.of(arg), LOC);
    }

    private static TraitApplication namedStringTrait(String name, String k1, String v1,
                                                      String k2, String v2) {
        var a1 = new TraitArg(k1, new TraitValue.StringValue(v1), LOC);
        var a2 = new TraitArg(k2, new TraitValue.StringValue(v2), LOC);
        return new TraitApplication(name, List.of(a1, a2), LOC);
    }

    private static Step step(String name, String action) {
        var fields = List.<StepField>of(new StepField.Action(action, LOC));
        return new Step(name, List.of(), fields, LOC);
    }

    private static Step stepWithAll(String name, String action, String expectation,
                                     OutcomeExpr outcome, List<String> telemetry, String risk) {
        var fields = List.<StepField>of(
                new StepField.Action(action, LOC),
                new StepField.Expectation(expectation, LOC),
                new StepField.Outcome(outcome, LOC),
                new StepField.Telemetry(telemetry, LOC),
                new StepField.Risk(risk, LOC));
        return new Step(name, List.of(), fields, LOC);
    }

    private static JourneyDef journey(String name, List<Step> steps, Map<String, Variant> variants) {
        return new JourneyDef(name, List.of(), List.of(),
                actorRef("Customer"), List.of(), steps, variants, null, LOC);
    }

    private static PolicyDef policy(String name, String description) {
        return new PolicyDef(name, List.of(), List.of(), description, LOC);
    }

    private static PolicyDef policyWithCompliance(String name, String description, String framework) {
        return new PolicyDef(name, List.of(stringTrait("compliance", framework)),
                List.of(), description, LOC);
    }

    private static DenyDef deny(String name, String description, String severity) {
        return new DenyDef(name, List.of(), List.of(), description, List.of(), severity, LOC);
    }

    /** Returns the CSV content string from the generator output. */
    private static String csv(IrModel m) {
        var out = GEN.generate(m);
        // filename is namespace-with-hyphens + -backlog.csv
        return out.files().get("com-example-shop-backlog.csv");
    }

    /** Returns the CSV rows as a list (splits on newlines). */
    private static String[] rows(IrModel m) {
        return csv(m).split("\n", -1);
    }

    // ── filename ──────────────────────────────────────────────────────────────

    @Test
    void outputFilenameIsDerivedFromNamespace() {
        var out = GEN.generate(model(journey("J", List.of(), Map.of())));
        assertTrue(out.files().containsKey("com-example-shop-backlog.csv"),
                "Expected key com-example-shop-backlog.csv in " + out.files().keySet());
    }

    // ── header row ────────────────────────────────────────────────────────────

    @Test
    void firstRowIsHeader() {
        String[] r = rows(model(journey("J", List.of(), Map.of())));
        assertEquals("\"Summary\",\"Issue Type\",\"Description\",\"Priority\",\"Labels\","
                + "\"Epic Name\",\"Epic Link\",\"Story Points\"", r[0]);
    }

    // ── journey → Epic ────────────────────────────────────────────────────────

    @Test
    void journeyProducesEpicRow() {
        var j = journey("CheckoutFlow", List.of(), Map.of());
        String content = csv(model(j));
        assertTrue(content.contains("\"Epic\""), "Expected Epic row for journey");
        assertTrue(content.contains("\"CheckoutFlow\""), "Expected journey name in Epic row");
    }

    @Test
    void epicRowHasJourneyNameInEpicNameColumn() {
        var j = journey("AddProduct", List.of(), Map.of());
        // Epic row: Summary=AddProduct, IssueType=Epic, ..., EpicName=AddProduct, EpicLink=""
        String content = csv(model(j));
        // The Epic Name column (index 5) should equal the journey name
        String[] rows = content.split("\n", -1);
        // row[1] is the Epic row (row[0] = header)
        String epicRow = rows[1];
        String[] cells = parseFirstNColumns(epicRow, 8);
        assertEquals("AddProduct", cells[0], "Summary should be journey name");
        assertEquals("Epic", cells[1], "Issue Type should be Epic");
        assertEquals("AddProduct", cells[5], "Epic Name should be journey name");
        assertEquals("", cells[6], "Epic Link should be empty for Epics");
    }

    @Test
    void epicStoryPointsEqualStepCount() {
        var steps = List.of(step("A", "do A"), step("B", "do B"), step("C", "do C"));
        var j = journey("MyJourney", steps, Map.of());
        String[] rows = csv(model(j)).split("\n", -1);
        String epicRow = rows[1];
        String[] cells = parseFirstNColumns(epicRow, 8);
        assertEquals("3", cells[7], "Story Points for Epic should equal number of steps");
    }

    // ── step → Story ──────────────────────────────────────────────────────────

    @Test
    void eachStepProducesStoryRow() {
        var steps = List.of(step("ReviewCart", "Review cart"), step("Pay", "Enter payment"));
        var j = journey("Checkout", steps, Map.of());
        String content = csv(model(j));
        // 1 header + 1 epic + 2 story rows = 4 rows (plus trailing newline = 5 elements)
        String[] r = content.split("\n", -1);
        assertEquals(5, r.length, "Expected header + epic + 2 stories + trailing empty");
    }

    @Test
    void stepRowHasCorrectEpicLink() {
        var steps = List.of(step("ReviewCart", "Customer reviews cart"));
        var j = journey("CheckoutJourney", steps, Map.of());
        String[] r = csv(model(j)).split("\n", -1);
        // r[0]=header, r[1]=epic, r[2]=step story
        String[] cells = parseFirstNColumns(r[2], 8);
        assertEquals("Story", cells[1], "Step row Issue Type should be Story");
        assertEquals("", cells[5], "Epic Name should be empty for Stories");
        assertEquals("CheckoutJourney", cells[6], "Epic Link should be parent journey name");
        assertEquals("1", cells[7], "Story Points for step should be 1");
    }

    @Test
    void stepSummaryContainsNameAndAction() {
        var steps = List.of(step("ConfirmOrder", "Customer submits the order"));
        var j = journey("Checkout", steps, Map.of());
        String content = csv(model(j));
        assertTrue(content.contains("ConfirmOrder: Customer submits the order"),
                "Step summary should be 'name: action'");
    }

    @Test
    void stepDescriptionIncludesExpectationAndRisk() {
        var s = stepWithAll("Pay", "Enter card",
                "Form validates", new OutcomeExpr.TransitionTo("PAID", LOC),
                List.of("PaymentSubmitted"), "PCI scope");
        var j = journey("Checkout", List.of(s), Map.of());
        String content = csv(model(j));
        assertTrue(content.contains("Expectation: Form validates"), "Description should include expectation");
        assertTrue(content.contains("Risk: PCI scope"), "Description should include risk");
        assertTrue(content.contains("Telemetry: PaymentSubmitted"), "Description should include telemetry");
        assertTrue(content.contains("TransitionTo(PAID)"), "Description should include outcome");
    }

    @Test
    void stepsAppearInDeclarationOrder() {
        var steps = List.of(step("First", "a"), step("Second", "b"), step("Third", "c"));
        var j = journey("J", steps, Map.of());
        String[] r = csv(model(j)).split("\n", -1);
        // r[1]=epic, r[2..4]=steps in order
        assertTrue(r[2].contains("First"), "First step should appear before Second");
        assertTrue(r[3].contains("Second"), "Second step should appear before Third");
        assertTrue(r[4].contains("Third"), "Third step should be last");
    }

    // ── variant → Story ───────────────────────────────────────────────────────

    @Test
    void variantProducesHighPriorityStory() {
        var variantStep = step("NotifyDeclined", "System notifies customer");
        var variant = new Variant("PaymentDeclined", "PaymentDeclinedError",
                List.of(variantStep), null, LOC);
        var j = journey("Checkout", List.of(), Map.of("PaymentDeclined", variant));
        String content = csv(model(j));
        assertTrue(content.contains("\"High\""), "Variant story should have High priority");
        assertTrue(content.contains("PaymentDeclined"), "Variant name should appear in summary");
    }

    @Test
    void variantSummaryIncludesTriggerName() {
        var variant = new Variant("NetworkError", "TimeoutException", List.of(), null, LOC);
        var j = journey("Checkout", List.of(), Map.of("NetworkError", variant));
        String content = csv(model(j));
        assertTrue(content.contains("TimeoutException"), "Trigger name should appear in variant summary");
    }

    @Test
    void variantDescriptionIncludesResolutionWhenPresent() {
        var outcome = new OutcomeExpr.ReturnToStep("EnterPaymentDetails", LOC);
        var variant = new Variant("PaymentDeclined", "PaymentDeclinedError", List.of(), outcome, LOC);
        var j = journey("Checkout", List.of(), Map.of("PaymentDeclined", variant));
        String content = csv(model(j));
        assertTrue(content.contains("ReturnToStep(EnterPaymentDetails)"),
                "Variant description should include resolution outcome");
    }

    @Test
    void variantStoryPointsEqualVariantStepCount() {
        var variantSteps = List.of(step("A", "a"), step("B", "b"));
        var variant = new Variant("ErrorPath", "SomeError", variantSteps, null, LOC);
        var j = journey("J", List.of(), Map.of("ErrorPath", variant));
        String content = csv(model(j));
        // Variant description has embedded newlines (Steps: section), so we cannot split on \n.
        // The last three cells of the variant row are: EpicName="", EpicLink="J", StoryPoints="2"
        assertTrue(content.contains("\"\",\"J\",\"2\""),
                "Variant with 2 steps should have Story Points = 2 as the last cell");
    }

    @Test
    void variantsAreSortedAlphabetically() {
        var v1 = new Variant("Zebra", "ErrZ", List.of(), null, LOC);
        var v2 = new Variant("Alpha", "ErrA", List.of(), null, LOC);
        var v3 = new Variant("Middle", "ErrM", List.of(), null, LOC);
        var j = journey("J", List.of(),
                Map.of("Zebra", v1, "Alpha", v2, "Middle", v3));
        String content = csv(model(j));
        int posAlpha = content.indexOf("Alpha");
        int posMiddle = content.indexOf("Middle");
        int posZebra = content.indexOf("Zebra");
        assertTrue(posAlpha < posMiddle, "Alpha variant should come before Middle");
        assertTrue(posMiddle < posZebra, "Middle variant should come before Zebra");
    }

    // ── journey ordering ──────────────────────────────────────────────────────

    @Test
    void journeysAreSortedAlphabetically() {
        var jA = journey("ZebraJourney", List.of(), Map.of());
        var jB = journey("AlphaJourney", List.of(), Map.of());
        var jC = journey("MiddleJourney", List.of(), Map.of());
        String content = csv(model(jA, jB, jC));
        int posAlpha = content.indexOf("AlphaJourney");
        int posMiddle = content.indexOf("MiddleJourney");
        int posZebra = content.indexOf("ZebraJourney");
        assertTrue(posAlpha < posMiddle, "AlphaJourney should come before MiddleJourney");
        assertTrue(posMiddle < posZebra, "MiddleJourney should come before ZebraJourney");
    }

    // ── policy → compliance Story ─────────────────────────────────────────────

    @Test
    void policyProducesComplianceStory() {
        var p = policy("DataQuality", "All data must be accurate");
        String content = csv(model(p));
        assertTrue(content.contains("[Policy] DataQuality"), "Policy summary should have [Policy] prefix");
        assertTrue(content.contains("\"Story\""), "Policy row should be a Story");
        assertTrue(content.contains("compliance"), "Policy labels should include 'compliance'");
    }

    @Test
    void policyWithComplianceFrameworkIsHighPriority() {
        var p = policyWithCompliance("PciPolicy", "No plaintext cards", "PCI-DSS");
        String content = csv(model(p));
        assertTrue(content.contains("\"High\""), "Policy with compliance framework should be High priority");
        assertTrue(content.contains("PCI-DSS"), "Compliance framework should appear in description");
    }

    @Test
    void policyWithoutComplianceFrameworkIsMediumPriority() {
        var p = policy("InternalPolicy", "Follow best practices");
        String[] r = csv(model(p)).split("\n", -1);
        // r[1] is the policy row (no journeys)
        String[] cells = parseFirstNColumns(r[1], 8);
        assertEquals("Medium", cells[3], "Policy without framework should be Medium priority");
    }

    @Test
    void policyEpicNameAndEpicLinkAreEmpty() {
        var p = policy("SomePolicy", "Description");
        String[] r = csv(model(p)).split("\n", -1);
        String[] cells = parseFirstNColumns(r[1], 8);
        assertEquals("", cells[5], "Epic Name should be empty for policy");
        assertEquals("", cells[6], "Epic Link should be empty for policy");
    }

    // ── deny → compliance Story ───────────────────────────────────────────────

    @Test
    void denyProducesComplianceStory() {
        var d = deny("NoPlaintextPasswords", "Must never store passwords in plaintext", "high");
        String content = csv(model(d));
        assertTrue(content.contains("[Compliance] NoPlaintextPasswords"),
                "Deny summary should have [Compliance] prefix");
        assertTrue(content.contains("\"Story\""), "Deny row should be a Story");
    }

    @Test
    void denySeverityCriticalMapsToHighestPriority() {
        var d = deny("CriticalDeny", "Critical prohibition", "critical");
        String[] r = csv(model(d)).split("\n", -1);
        String[] cells = parseFirstNColumns(r[1], 8);
        assertEquals("Highest", cells[3], "Critical severity should map to Highest priority");
    }

    @Test
    void denySeverityHighMapsToHighPriority() {
        var d = deny("HighDeny", "High prohibition", "high");
        String[] r = csv(model(d)).split("\n", -1);
        String[] cells = parseFirstNColumns(r[1], 8);
        assertEquals("High", cells[3], "High severity should map to High priority");
    }

    @Test
    void denySeverityDefaultMapsMediumPriority() {
        var d = deny("MediumDeny", "Medium prohibition", "medium");
        String[] r = csv(model(d)).split("\n", -1);
        String[] cells = parseFirstNColumns(r[1], 8);
        assertEquals("Medium", cells[3], "Default severity should map to Medium priority");
    }

    @Test
    void denyScopeAppearsInDescription() {
        var d = new DenyDef("NoPiiExport", List.of(), List.of(),
                "Never export PII", List.of("Customer", "Order"), "high", LOC);
        String content = csv(model(d));
        assertTrue(content.contains("Scope: Customer, Order"), "Deny description should include scope");
    }

    @Test
    void denyWithComplianceTraitAppearsInLabels() {
        var d = new DenyDef("GdprDeny", List.of(stringTrait("compliance", "GDPR")),
                List.of(), "No data export without consent", List.of(), "high", LOC);
        String content = csv(model(d));
        assertTrue(content.contains("GDPR"), "GDPR compliance framework should appear");
    }

    // ── ordering: journeys before policies before denies ─────────────────────

    @Test
    void journeysAppearBeforePoliciesBeforeDenies() {
        var j = journey("AJourney", List.of(), Map.of());
        var p = policy("APolicy", "Some policy");
        var d = deny("ADeny", "Some deny", "medium");
        String content = csv(model(j, p, d));
        int posJourney = content.indexOf("AJourney");
        int posPolicy  = content.indexOf("[Policy] APolicy");
        int posDeny    = content.indexOf("[Compliance] ADeny");
        assertTrue(posJourney < posPolicy, "Journey should appear before policies");
        assertTrue(posPolicy < posDeny, "Policies should appear before denies");
    }

    // ── journey metadata in Epic description ─────────────────────────────────

    @Test
    void epicDescriptionIncludesActorWhenPresent() {
        var j = journey("Checkout", List.of(), Map.of()); // actor = Customer
        String content = csv(model(j));
        assertTrue(content.contains("Actor: Customer"), "Epic description should include actor name");
    }

    @Test
    void epicDescriptionIncludesPreconditionsWhenPresent() {
        var j = new JourneyDef("Checkout", List.of(), List.of(),
                actorRef("Customer"),
                List.of("Cart is not empty", "User is authenticated"),
                List.of(), Map.of(), null, LOC);
        String content = csv(model(j));
        assertTrue(content.contains("Preconditions: Cart is not empty; User is authenticated"),
                "Epic description should include preconditions");
    }

    @Test
    void epicDescriptionIncludesKpiWhenPresent() {
        var kpi = namedStringTrait("kpi", "metric", "checkout_rate", "target", "95%");
        var j = new JourneyDef("Checkout", List.of(kpi), List.of(),
                null, List.of(), List.of(), Map.of(), null, LOC);
        String content = csv(model(j));
        assertTrue(content.contains("KPI:"), "Epic description should include KPI section");
        assertTrue(content.contains("metric=checkout_rate"), "KPI should include metric");
        assertTrue(content.contains("target=95%"), "KPI should include target");
    }

    @Test
    void epicDescriptionIncludesOutcomesWhenPresent() {
        var outcomes = new JourneyOutcomes("Order placed", "Cart retained", LOC);
        var j = new JourneyDef("Checkout", List.of(), List.of(),
                null, List.of(), List.of(), Map.of(), outcomes, LOC);
        String content = csv(model(j));
        assertTrue(content.contains("Success: Order placed"), "Epic description should include success outcome");
        assertTrue(content.contains("Failure: Cart retained"), "Epic description should include failure outcome");
    }

    // ── CSV quoting ───────────────────────────────────────────────────────────

    @Test
    void csvQuoteWrapsInDoubleQuotes() {
        assertEquals("\"hello\"", JiraBacklogGenerator.csvQuote("hello"));
    }

    @Test
    void csvQuoteEscapesInternalDoubleQuotes() {
        assertEquals("\"say \"\"hi\"\"\"", JiraBacklogGenerator.csvQuote("say \"hi\""));
    }

    @Test
    void csvQuoteHandlesEmptyString() {
        assertEquals("\"\"", JiraBacklogGenerator.csvQuote(""));
    }

    @Test
    void csvQuoteHandlesNewlinesInValue() {
        String result = JiraBacklogGenerator.csvQuote("line1\nline2");
        assertEquals("\"line1\nline2\"", result,
                "Newlines inside a cell value should be preserved within quotes");
    }

    @Test
    void descriptionWithSpecialCharsIsProperlyQuoted() {
        var p = policy("DirtyPolicy", "Contains \"quotes\" and,commas");
        String content = csv(model(p));
        // The description cell should have doubled quotes and be wrapped
        assertTrue(content.contains("\"\"quotes\"\""), "Internal quotes should be doubled");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses the first {@code n} columns of a CSV row that uses RFC 4180 quoting
     * (double-quote delimited cells, internal quotes doubled).
     * This is intentionally simple — sufficient for test assertions.
     */
    private static String[] parseFirstNColumns(String row, int n) {
        String[] result = new String[n];
        int col = 0;
        int i = 0;
        while (col < n && i < row.length()) {
            if (row.charAt(i) == '"') {
                // quoted cell
                i++; // skip opening quote
                var sb = new StringBuilder();
                while (i < row.length()) {
                    char c = row.charAt(i);
                    if (c == '"') {
                        if (i + 1 < row.length() && row.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            i++; // skip closing quote
                            break;
                        }
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                result[col++] = sb.toString();
                if (i < row.length() && row.charAt(i) == ',') i++; // skip comma
            } else {
                // unquoted cell (shouldn't happen with this generator, but handle gracefully)
                int end = row.indexOf(',', i);
                if (end < 0) end = row.length();
                result[col++] = row.substring(i, end);
                i = end + 1;
            }
        }
        return result;
    }
}
