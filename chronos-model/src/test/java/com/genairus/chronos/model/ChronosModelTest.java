package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChronosModelTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    private ChronosModel buildModel() {
        var actor  = new ActorDef("Customer", List.of(), List.of(), LOC);
        var entity = new EntityDef("Order", List.of(), List.of(), List.of(), LOC);
        var shape  = new ShapeStructDef("Money", List.of(), List.of(), List.of(), LOC);
        var enumDef = new EnumDef("OrderStatus", List.of(), List.of(),
                List.of(EnumMember.of("PENDING", LOC), EnumMember.of("PAID", LOC)), LOC);
        var policy = new PolicyDef("DataRetention", "7 years", List.of(), List.of(), LOC);
        var listDef = new ListDef("OrderItems", List.of(), List.of(),
                new TypeRef.NamedTypeRef("Order"), LOC);
        var mapDef  = new MapDef("Metadata", List.of(), List.of(),
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING),
                new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING), LOC);
        var journey = new JourneyDef(
                "GuestCheckout", List.of(), List.of(),
                "Customer", List.of(), List.of(), Map.of(), null, LOC);

        return new ChronosModel(
                "com.example.checkout",
                List.of(new UseDecl("com.example.actors", "Customer", LOC)),
                List.of(actor, entity, shape, enumDef, policy, listDef, mapDef, journey));
    }

    @Test
    void namespaceIsAccessible() {
        assertEquals("com.example.checkout", buildModel().namespace());
    }

    @Test
    void importsAreAccessible() {
        assertEquals(1, buildModel().imports().size());
        assertEquals("com.example.actors#Customer", buildModel().imports().get(0).qualifiedId());
    }

    @Test
    void journeysTypedAccessor() {
        var journeys = buildModel().journeys();
        assertEquals(1, journeys.size());
        assertEquals("GuestCheckout", journeys.get(0).name());
    }

    @Test
    void entitiesTypedAccessor() {
        var entities = buildModel().entities();
        assertEquals(1, entities.size());
        assertEquals("Order", entities.get(0).name());
    }

    @Test
    void shapeStructsTypedAccessor() {
        assertEquals(1, buildModel().shapeStructs().size());
        assertEquals("Money", buildModel().shapeStructs().get(0).name());
    }

    @Test
    void enumsTypedAccessor() {
        var enums = buildModel().enums();
        assertEquals(1, enums.size());
        assertEquals("OrderStatus", enums.get(0).name());
    }

    @Test
    void actorsTypedAccessor() {
        assertEquals(1, buildModel().actors().size());
        assertEquals("Customer", buildModel().actors().get(0).name());
    }

    @Test
    void policiesTypedAccessor() {
        assertEquals(1, buildModel().policies().size());
        assertEquals("DataRetention", buildModel().policies().get(0).name());
    }

    @Test
    void listsTypedAccessor() {
        assertEquals(1, buildModel().lists().size());
        assertEquals("OrderItems", buildModel().lists().get(0).name());
    }

    @Test
    void mapsTypedAccessor() {
        assertEquals(1, buildModel().maps().size());
        assertEquals("Metadata", buildModel().maps().get(0).name());
    }

    @Test
    void findShapeByNameReturnsCorrectShape() {
        var result = buildModel().findShape("Order");
        assertTrue(result.isPresent());
        assertInstanceOf(EntityDef.class, result.get());
    }

    @Test
    void findShapeReturnsEmptyForUnknownName() {
        assertTrue(buildModel().findShape("NonExistent").isEmpty());
    }

    @Test
    void exhaustiveShapeDefinitionPatternMatch() {
        // Verify the sealed hierarchy covers all shape types — compiles only if exhaustive.
        for (ShapeDefinition shape : buildModel().shapes()) {
            String kind = switch (shape) {
                case ActorDef      a -> "actor";
                case EntityDef     e -> "entity";
                case EnumDef       e -> "enum";
                case JourneyDef    j -> "journey";
                case ListDef       l -> "list";
                case MapDef        m -> "map";
                case PolicyDef     p -> "policy";
                case ShapeStructDef s -> "shape";
            };
            assertNotNull(kind);
        }
    }
}
