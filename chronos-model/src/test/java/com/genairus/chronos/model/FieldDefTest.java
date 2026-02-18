package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldDefTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);
    private static final TypeRef STRING_TYPE =
            new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING);

    @Test
    void fieldWithRequiredTraitIsRequired() {
        var requiredTrait = new TraitApplication("required", List.of(), LOC);
        var field = new FieldDef("id", STRING_TYPE, List.of(requiredTrait), LOC);
        assertTrue(field.isRequired());
    }

    @Test
    void fieldWithoutRequiredTraitIsOptional() {
        var field = new FieldDef("notes", STRING_TYPE, List.of(), LOC);
        assertFalse(field.isRequired());
    }

    @Test
    void fieldWithUnrelatedTraitsIsNotRequired() {
        var piiTrait = new TraitApplication("pii", List.of(), LOC);
        var field = new FieldDef("email", STRING_TYPE, List.of(piiTrait), LOC);
        assertFalse(field.isRequired());
    }

    @Test
    void fieldWithRequiredAmongMultipleTraits() {
        var piiTrait      = new TraitApplication("pii",      List.of(), LOC);
        var requiredTrait = new TraitApplication("required", List.of(), LOC);
        var field = new FieldDef("email", STRING_TYPE, List.of(piiTrait, requiredTrait), LOC);
        assertTrue(field.isRequired());
    }
}
