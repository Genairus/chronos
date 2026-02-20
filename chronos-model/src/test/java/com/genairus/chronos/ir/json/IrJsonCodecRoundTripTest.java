package com.genairus.chronos.ir.json;

import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.ShapeId;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.EntityDef;
import com.genairus.chronos.ir.types.FieldDef;
import com.genairus.chronos.ir.types.JourneyDef;
import com.genairus.chronos.ir.types.JourneyOutcomes;
import com.genairus.chronos.ir.types.Step;
import com.genairus.chronos.ir.types.StepField;
import com.genairus.chronos.ir.types.TypeRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IrJsonCodecRoundTripTest {

    /**
     * An IrModel containing two different shape kinds (entity + actor) must survive
     * a serialize → deserialize cycle unchanged, and the JSON must carry the
     * {@code "kind"} type discriminator for each shape.
     */
    @Test
    void entityAndActorRoundTrip() {
        var field  = new FieldDef(
                "id",
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING),
                List.of(),
                Span.UNKNOWN);

        var entity = new EntityDef(
                "Order",
                List.of(),
                List.of(),
                Optional.empty(),
                List.of(field),
                List.of(),
                Span.UNKNOWN);

        var actor = new ActorDef(
                "Customer",
                List.of(),
                List.of(),
                Optional.empty(),
                Span.UNKNOWN);

        var model = new IrModel("com.example.test", List.of(), List.of(entity, actor));

        String json = IrJsonCodec.toJson(model);

        // Verify kind discriminators are present
        assertTrue(json.contains("\"entity\""),    "JSON must contain entity kind discriminator");
        assertTrue(json.contains("\"actor\""),     "JSON must contain actor kind discriminator");
        assertTrue(json.contains("\"primitive\""), "JSON must contain primitive typeKind discriminator");

        // Round-trip fidelity
        IrModel restored = IrJsonCodec.fromJson(json);
        assertEquals(model, restored, "Model must be equal after serialize → deserialize");
    }

    /**
     * A JourneyDef with a resolved {@link SymbolRef} must survive a round-trip,
     * preserving the resolved state, kind, and ShapeId.
     */
    @Test
    void journeyWithResolvedActorRefRoundTrip() {
        var actorShape = new ActorDef(
                "TestUser",
                List.of(),
                List.of(),
                Optional.empty(),
                Span.UNKNOWN);

        var actorRef = SymbolRef.resolved(
                SymbolKind.ACTOR,
                ShapeId.of("com.example.test", "TestUser"),
                Span.UNKNOWN);

        List<StepField> stepFields = List.of(
                new StepField.Action("User performs action", Span.UNKNOWN),
                new StepField.Expectation("System responds", Span.UNKNOWN));
        var step = new Step("Perform", List.of(), stepFields, Span.UNKNOWN);

        var journey = new JourneyDef(
                "DoSomething",
                List.of(),
                List.of(),
                actorRef,
                List.of(),
                List.of(step),
                Map.of(),
                new JourneyOutcomes("All done", null, Span.UNKNOWN),
                Span.UNKNOWN);

        var model = new IrModel(
                "com.example.test",
                List.of(),
                List.of(actorShape, journey));

        String json = IrJsonCodec.toJson(model);

        // Discriminators present
        assertTrue(json.contains("\"actor\""),   "JSON must contain actor kind");
        assertTrue(json.contains("\"journey\""), "JSON must contain journey kind");
        assertTrue(json.contains("\"action\""),  "JSON must contain step field action kind");
        // SymbolRef resolved flag present
        assertTrue(json.contains("\"resolved\" : true"), "JSON must show resolved SymbolRef");

        // Round-trip fidelity
        IrModel restored = IrJsonCodec.fromJson(json);
        assertEquals(model, restored, "Model must be equal after serialize → deserialize");

        // Verify the actor ref is still resolved after round-trip
        var restoredJourney = restored.journeys().get(0);
        assertNotNull(restoredJourney.actorRef());
        assertTrue(restoredJourney.actorRef().isResolved(), "actorRef must remain resolved");
        assertEquals("TestUser", restoredJourney.actorRef().id().name());
        assertEquals("com.example.test", restoredJourney.actorRef().id().namespace().value());
    }

    /**
     * EntityDef and ActorDef with non-empty {@code parentRef} (resolved and unresolved forms)
     * must survive a round-trip cycle with the {@link SymbolRef} preserved exactly.
     */
    @Test
    void inheritanceParentRefRoundTrip() {
        // Base entity (no parent)
        var baseEntity = new EntityDef(
                "User",
                List.of(),
                List.of(),
                Optional.empty(),
                List.of(),
                List.of(),
                Span.UNKNOWN);

        // Child entity with a resolved parentRef pointing to 'User'
        var resolvedParentRef = SymbolRef.resolved(
                SymbolKind.ENTITY,
                ShapeId.of("com.example.test", "User"),
                Span.UNKNOWN);
        var childEntity = new EntityDef(
                "PremiumUser",
                List.of(),
                List.of(),
                Optional.of(resolvedParentRef),
                List.of(),
                List.of(),
                Span.UNKNOWN);

        // Base actor (no parent)
        var baseActor = new ActorDef(
                "BaseRole",
                List.of(),
                List.of(),
                Optional.empty(),
                Span.UNKNOWN);

        // Child actor with an unresolved parentRef (simulates pre-resolution state)
        var unresolvedParentRef = SymbolRef.unresolved(
                SymbolKind.ACTOR,
                QualifiedName.local("BaseRole"),
                Span.UNKNOWN);
        var childActor = new ActorDef(
                "AdminRole",
                List.of(),
                List.of(),
                Optional.of(unresolvedParentRef),
                Span.UNKNOWN);

        var model = new IrModel(
                "com.example.test",
                List.of(),
                List.of(baseEntity, childEntity, baseActor, childActor));

        String json = IrJsonCodec.toJson(model);

        // Both resolved and unresolved SymbolRef forms should be present
        assertTrue(json.contains("\"resolved\" : true"),  "JSON must contain resolved ref");
        assertTrue(json.contains("\"resolved\" : false"), "JSON must contain unresolved ref");

        // Round-trip fidelity
        IrModel restored = IrJsonCodec.fromJson(json);
        assertEquals(model, restored, "Model must be equal after serialize → deserialize");

        // Verify resolved parentRef on PremiumUser
        var restoredChild = restored.entities().stream()
                .filter(e -> "PremiumUser".equals(e.name()))
                .findFirst()
                .orElseThrow();
        assertTrue(restoredChild.parentRef().isPresent());
        assertTrue(restoredChild.parentRef().get().isResolved());
        assertEquals("User", restoredChild.parentRef().get().id().name());
        assertEquals("com.example.test",
                restoredChild.parentRef().get().id().namespace().value());

        // Verify unresolved parentRef on AdminRole
        var restoredChildActor = restored.actors().stream()
                .filter(a -> "AdminRole".equals(a.name()))
                .findFirst()
                .orElseThrow();
        assertTrue(restoredChildActor.parentRef().isPresent());
        assertFalse(restoredChildActor.parentRef().get().isResolved());
        assertEquals("BaseRole", restoredChildActor.parentRef().get().name().name());
    }
}
