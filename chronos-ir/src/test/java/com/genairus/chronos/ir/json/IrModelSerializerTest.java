package com.genairus.chronos.ir.json;

import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.ShapeId;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.EntityDef;
import com.genairus.chronos.ir.types.EnumDef;
import com.genairus.chronos.ir.types.EnumMember;
import com.genairus.chronos.ir.types.FieldDef;
import com.genairus.chronos.ir.types.JourneyDef;
import com.genairus.chronos.ir.types.JourneyOutcomes;
import com.genairus.chronos.ir.types.Step;
import com.genairus.chronos.ir.types.StepField;
import com.genairus.chronos.ir.types.TraitApplication;
import com.genairus.chronos.ir.types.TraitArg;
import com.genairus.chronos.ir.types.TraitValue;
import com.genairus.chronos.ir.types.TypeRef;
import com.genairus.chronos.ir.types.UseDecl;
import com.genairus.chronos.ir.types.Variant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IrModelSerializerTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private static IrModel buildReferenceModel() {
        var span = Span.UNKNOWN;

        // Entity with a field and a resolved parentRef
        var field = new FieldDef(
                "id",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING),
                List.of(new TraitApplication("required", List.of(), span)),
                span);

        var resolvedParent = SymbolRef.resolved(
                SymbolKind.ENTITY,
                ShapeId.of("com.example", "BaseEntity"),
                span);

        var entity = new EntityDef(
                "Order",
                List.of(),
                List.of("An order entity."),
                Optional.of(resolvedParent),
                List.of(field),
                List.of(),
                span);

        // Actor with no parent
        var actor = new ActorDef(
                "Customer",
                List.of(),
                List.of(),
                Optional.empty(),
                span);

        // Journey with resolved actorRef and steps
        var actorRef = SymbolRef.resolved(
                SymbolKind.ACTOR,
                ShapeId.of("com.example", "Customer"),
                span);

        List<StepField> stepFields = List.of(
                new StepField.Action("Customer submits order", span),
                new StepField.Expectation("System creates order", span));
        var step = new Step("SubmitOrder", List.of(), stepFields, span);

        var journey = new JourneyDef(
                "PlaceOrder",
                List.of(new TraitApplication("kpi", List.of(
                        new TraitArg(null, new TraitValue.NumberValue(0.95), span)), span)),
                List.of("Completes an order placement."),
                actorRef,
                List.of("Cart is not empty"),
                List.of(step),
                Map.of(),
                new JourneyOutcomes("Order confirmed", "Payment failed", span),
                span);

        // Enum
        var enumDef = new EnumDef(
                "Status",
                List.of(),
                List.of(),
                List.of(
                        EnumMember.of("ACTIVE", 1, span),
                        EnumMember.of("INACTIVE", 2, span)),
                span);

        // UseDecl
        var useDecl = new UseDecl("com.example.shared", "BaseEntity", span);

        return new IrModel(
                "com.example",
                List.of(useDecl),
                List.of(entity, actor, journey, enumDef));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────────

    @Test
    void outputIsValidJson() {
        IrModel model = buildReferenceModel();
        String json = IrModelSerializer.toJson(model);

        assertNotNull(json);
        assertFalse(json.isBlank());
        assertTrue(json.startsWith("{"),               "JSON must start with {");
        assertTrue(json.endsWith("}"),                 "JSON must end with }");
        assertTrue(json.contains("\"namespace\""),     "JSON must contain namespace field");
        assertTrue(json.contains("\"shapes\""),        "JSON must contain shapes field");
        assertTrue(json.contains("\"imports\""),       "JSON must contain imports field");
        assertTrue(json.contains("\"com.example\""),   "JSON must contain namespace value");
        assertTrue(json.contains("\"entity\""),        "JSON must contain entity discriminator");
        assertTrue(json.contains("\"journey\""),       "JSON must contain journey discriminator");
        assertTrue(json.contains("\"enum\""),          "JSON must contain enum discriminator");
    }

    @Test
    void outputIsDeterministic() {
        IrModel model = buildReferenceModel();
        String first  = IrModelSerializer.toJson(model);
        String second = IrModelSerializer.toJson(model);
        assertEquals(first, second, "Two calls on the same model must produce identical output");
    }

    @Test
    void propertiesAreSortedAlphabetically() {
        IrModel model = buildReferenceModel();
        String json = IrModelSerializer.toJson(model);

        // Root properties: imports < namespace < shapes
        int idxImports   = json.indexOf("\"imports\"");
        int idxNamespace = json.indexOf("\"namespace\"");
        int idxShapes    = json.indexOf("\"shapes\"");
        assertTrue(idxImports < idxNamespace, "imports must precede namespace");
        assertTrue(idxNamespace < idxShapes,  "namespace must precede shapes");
    }

    @Test
    void discriminatorAppearsBeforeOtherProperties() {
        IrModel model = buildReferenceModel();
        String json = IrModelSerializer.toJson(model);

        // For each shape object the "kind" discriminator must appear before all other
        // alphabetically-later properties such as "docComments" or "name".
        // We verify by finding the first "kind" in the shapes section.
        int idxShapes = json.indexOf("\"shapes\"");
        int idxKind   = json.indexOf("\"kind\"", idxShapes);
        int idxDoc    = json.indexOf("\"docComments\"", idxShapes);
        assertTrue(idxKind < idxDoc,
                "kind discriminator must be written before docComments inside shape objects");
    }

    @Test
    void unresolvedSymbolRefProducesCorrectShape() {
        var unresolvedRef = SymbolRef.unresolved(
                SymbolKind.ENTITY,
                QualifiedName.local("SomeEntity"),
                Span.UNKNOWN);

        var entity = new EntityDef(
                "Child",
                List.of(),
                List.of(),
                Optional.of(unresolvedRef),
                List.of(),
                List.of(),
                Span.UNKNOWN);

        var model = new IrModel("com.example", List.of(), List.of(entity));
        String json = IrModelSerializer.toJson(model);

        assertTrue(json.contains("\"resolved\" : false"), "Unresolved SymbolRef must have resolved:false");
        assertTrue(json.contains("\"name\""),             "Unresolved SymbolRef must include name object");
        assertFalse(json.contains("\"id\""),              "Unresolved SymbolRef must not include id object");
    }

    @Test
    void resolvedSymbolRefProducesCorrectShape() {
        var resolvedRef = SymbolRef.resolved(
                SymbolKind.ENTITY,
                ShapeId.of("com.example", "BaseEntity"),
                Span.UNKNOWN);

        var entity = new EntityDef(
                "Child",
                List.of(),
                List.of(),
                Optional.of(resolvedRef),
                List.of(),
                List.of(),
                Span.UNKNOWN);

        var model = new IrModel("com.example", List.of(), List.of(entity));
        String json = IrModelSerializer.toJson(model);

        assertTrue(json.contains("\"resolved\" : true"),  "Resolved SymbolRef must have resolved:true");
        assertTrue(json.contains("\"id\""),               "Resolved SymbolRef must include id object");
        assertTrue(json.contains("\"BaseEntity\""),       "Resolved SymbolRef must include shape name");
        assertTrue(json.contains("\"com.example\""),      "Resolved SymbolRef must include namespace");
    }

    @Test
    void optionalEmptySerializesAsNull() {
        var entity = new EntityDef(
                "Foo",
                List.of(),
                List.of(),
                Optional.empty(),   // parentRef absent
                List.of(),
                List.of(),
                Span.UNKNOWN);

        var model = new IrModel("com.example", List.of(), List.of(entity));
        String json = IrModelSerializer.toJson(model);

        assertTrue(json.contains("\"parentRef\" : null"),
                "Optional.empty() parentRef must serialise as null");
    }

    @Test
    void variantMapSortedByKey() {
        var span = Span.UNKNOWN;
        var variant1 = new Variant("ZapVariant", "ZapError", List.of(), null, span);
        var variant2 = new Variant("AaaVariant", "AaaError", List.of(), null, span);

        // Pass variants in reverse order; output must be alphabetically sorted
        Map<String, Variant> variants = Map.of(
                "ZapVariant", variant1,
                "AaaVariant", variant2);

        var actorRef = SymbolRef.unresolved(SymbolKind.ACTOR, QualifiedName.local("SomeActor"), span);
        var journey = new JourneyDef(
                "TestJourney",
                List.of(),
                List.of(),
                actorRef,
                List.of(),
                List.of(),
                variants,
                null,
                span);

        var model = new IrModel("com.example", List.of(), List.of(journey));
        String json = IrModelSerializer.toJson(model);

        int idxAaa = json.indexOf("\"AaaVariant\"");
        int idxZap = json.indexOf("\"ZapVariant\"");
        assertTrue(idxAaa >= 0 && idxZap >= 0, "Both variant keys must appear in JSON");
        assertTrue(idxAaa < idxZap, "AaaVariant must appear before ZapVariant (alphabetical sort)");
    }

    @Test
    void primitiveTypeKindDiscriminatorIsTypeKind() {
        var field = new FieldDef(
                "amount",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.FLOAT),
                List.of(),
                Span.UNKNOWN);
        var entity = new EntityDef("Price", List.of(), List.of(), Optional.empty(),
                List.of(field), List.of(), Span.UNKNOWN);
        var model = new IrModel("com.example", List.of(), List.of(entity));

        String json = IrModelSerializer.toJson(model);
        assertTrue(json.contains("\"typeKind\" : \"primitive\""),
                "PrimitiveType must use typeKind discriminator with value 'primitive'");
        assertTrue(json.contains("\"kind\" : \"FLOAT\""),
                "PrimitiveType kind field must be the enum name FLOAT");
    }

    @Test
    void emptyArraysHaveSpaceInsideBrackets() {
        // An entity with no fields/traits/invariants/docComments produces several empty arrays
        var entity = new EntityDef("Foo", List.of(), List.of(), Optional.empty(),
                List.of(), List.of(), Span.UNKNOWN);
        var model = new IrModel("com.example", List.of(), List.of(entity));

        String json = IrModelSerializer.toJson(model);
        // Matching Jackson's FixedSpaceIndenter: empty arrays are "[ ]" not "[]"
        assertTrue(json.contains("[ ]"), "Empty arrays must be serialised as [ ] with space inside");
        assertFalse(json.contains("[]"),  "Empty arrays must NOT be serialised as [] without space");
    }
}
