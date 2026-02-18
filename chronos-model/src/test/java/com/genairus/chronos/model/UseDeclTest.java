package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UseDeclTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void qualifiedIdJoinsNamespaceAndShapeName() {
        var decl = new UseDecl("com.genairus.data.orders", "Order", LOC);
        assertEquals("com.genairus.data.orders#Order", decl.qualifiedId());
    }

    @Test
    void fieldsAreAccessible() {
        var decl = new UseDecl("com.example", "Customer", LOC);
        assertEquals("com.example", decl.namespace());
        assertEquals("Customer", decl.shapeName());
        assertEquals(LOC, decl.location());
    }
}
