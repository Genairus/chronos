package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActorDefTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void descriptionExtractedFromDescriptionTrait() {
        var descArg   = new TraitArg(null, new TraitValue.StringValue("A guest user"), LOC);
        var descTrait = new TraitApplication("description", List.of(descArg), LOC);
        var actor     = new ActorDef("GuestUser", List.of(descTrait), List.of(), LOC);

        assertTrue(actor.description().isPresent());
        assertEquals("A guest user", actor.description().get());
    }

    @Test
    void descriptionEmptyWhenNoDescriptionTrait() {
        var actor = new ActorDef("GuestUser", List.of(), List.of(), LOC);
        assertTrue(actor.description().isEmpty());
    }

    @Test
    void descriptionEmptyWhenDescriptionTraitHasNamedArgOnly() {
        // A named arg is not a positional arg — description() should not match.
        var namedArg  = new TraitArg("text", new TraitValue.StringValue("desc"), LOC);
        var descTrait = new TraitApplication("description", List.of(namedArg), LOC);
        var actor     = new ActorDef("Admin", List.of(descTrait), List.of(), LOC);

        assertTrue(actor.description().isEmpty());
    }

    @Test
    void implementsShapeDefinition() {
        var actor = new ActorDef("Customer", List.of(), List.of(), LOC);
        assertInstanceOf(ShapeDefinition.class, actor);
        assertEquals("Customer", actor.name());
        assertEquals(LOC, actor.location());
    }
}
