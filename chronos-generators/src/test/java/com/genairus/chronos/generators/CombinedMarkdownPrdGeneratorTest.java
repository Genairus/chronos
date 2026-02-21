package com.genairus.chronos.generators;

import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MarkdownPrdGenerator#generateCombined}.
 *
 * <p>Models are built directly from record constructors — no parser dependency needed.
 * Tests verify namespace-qualified headings, deterministic ordering, and filename handling.
 */
class CombinedMarkdownPrdGeneratorTest {

    private static final Span LOC = Span.at("test.chronos", 1, 1);
    private static final MarkdownPrdGenerator GEN = new MarkdownPrdGenerator();

    // ── helpers ─────────────────────────────────────────────────────────────

    private static IrModel model(String ns, IrShape... shapes) {
        return new IrModel(ns, List.of(), List.of(shapes));
    }

    private static EnumDef enumDef(String name) {
        return new EnumDef(name, List.of(), List.of(),
                List.of(EnumMember.of("VALUE", LOC)), LOC);
    }

    private static ActorDef actorDef(String name) {
        return new ActorDef(name, List.of(), List.of(), Optional.empty(), LOC);
    }

    private static JourneyDef journeyDef(String name) {
        return new JourneyDef(name, List.of(), List.of(),
                null, List.of(), List.of(), Map.of(), null, LOC);
    }

    private static String combined(List<IrModel> models, String docName) {
        var output = GEN.generateCombined(models, docName);
        return output.content();
    }

    // ── tests ─────────────────────────────────────────────────────────────

    @Test
    void bothNamespacesAppearInOutput() {
        var models = List.of(
                model("a", enumDef("Foo")),
                model("b", enumDef("Bar")));
        var md = combined(models, "out");
        assertTrue(md.contains("#### a.Foo"), "Expected 'a.Foo' heading");
        assertTrue(md.contains("#### b.Bar"), "Expected 'b.Bar' heading");
    }

    @Test
    void journeyHeadingIsFullyQualified() {
        var models = List.of(
                model("shop.journeys", journeyDef("PlaceOrder")));
        var md = combined(models, "out");
        assertTrue(md.contains("### shop.journeys.PlaceOrder"),
                "Journey heading should be namespace-qualified");
    }

    @Test
    void defaultDocNameProducesCorrectFilename() {
        var models = List.of(model("com.example", enumDef("Status")));
        var output = GEN.generateCombined(models, null);
        assertTrue(output.files().containsKey("chronos-prd.md"),
                "Default filename should be chronos-prd.md; got: " + output.files().keySet());
    }

    @Test
    void customDocNameIsRespected() {
        var models = List.of(model("com.example", enumDef("Status")));
        var output = GEN.generateCombined(models, "my-doc");
        assertTrue(output.files().containsKey("my-doc.md"),
                "Custom docName should produce my-doc.md; got: " + output.files().keySet());
    }

    @Test
    void overlappingSimpleNamesGetDistinctFqHeadings() {
        var models = List.of(
                model("a", enumDef("Status")),
                model("b", enumDef("Status")));
        var md = combined(models, "out");
        assertTrue(md.contains("#### a.Status"), "Expected '#### a.Status'");
        assertTrue(md.contains("#### b.Status"), "Expected '#### b.Status'");
        // 'a.' comes before 'b.' alphabetically
        assertTrue(md.indexOf("#### a.Status") < md.indexOf("#### b.Status"),
                "a.Status must appear before b.Status in output");
    }

    @Test
    void actorHeadingIsNamespaceQualified() {
        var models = List.of(model("shop.actors", actorDef("Customer")));
        var md = combined(models, "out");
        assertTrue(md.contains("#### shop.actors.Customer"),
                "Actor heading should be namespace-qualified");
    }

    @Test
    void namespacesListedAlphabetically() {
        var models = List.of(
                model("z.last", enumDef("Z")),
                model("a.first", enumDef("A")));
        var md = combined(models, "out");
        int aIdx = md.indexOf("- `a.first`");
        int zIdx = md.indexOf("- `z.last`");
        assertTrue(aIdx >= 0 && zIdx >= 0, "Both namespaces should appear in Namespaces section");
        assertTrue(aIdx < zIdx, "Namespaces should be listed alphabetically");
    }

    @Test
    void tocIncludesJourneyFqEntry() {
        var models = List.of(model("com.journeys", journeyDef("CheckOut")));
        var md = combined(models, "out");
        assertTrue(md.contains("com.journeys.CheckOut"),
                "TOC should include FQ journey name");
    }

    @Test
    void emptyModelsListProducesEmptyDocument() {
        var output = GEN.generateCombined(List.of(), "empty");
        assertNotNull(output);
        assertTrue(output.files().containsKey("empty.md"));
    }
}
