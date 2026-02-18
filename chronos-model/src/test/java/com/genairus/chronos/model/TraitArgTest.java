package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraitArgTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void namedArgIsNamed() {
        var arg = new TraitArg("metric", new TraitValue.StringValue("Conversion"), LOC);
        assertTrue(arg.isNamed());
        assertEquals("metric", arg.key());
    }

    @Test
    void positionalArgIsNotNamed() {
        var arg = new TraitArg(null, new TraitValue.StringValue("GDPR"), LOC);
        assertFalse(arg.isNamed());
        assertNull(arg.key());
    }
}
