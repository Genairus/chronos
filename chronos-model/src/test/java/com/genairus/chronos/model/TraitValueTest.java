package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraitValueTest {

    @Test
    void stringValue() {
        TraitValue v = new TraitValue.StringValue("GDPR");
        assertInstanceOf(TraitValue.StringValue.class, v);
        assertEquals("GDPR", ((TraitValue.StringValue) v).value());
    }

    @Test
    void numberValue() {
        TraitValue v = new TraitValue.NumberValue(99.9);
        assertInstanceOf(TraitValue.NumberValue.class, v);
        assertEquals(99.9, ((TraitValue.NumberValue) v).value());
    }

    @Test
    void boolValue() {
        TraitValue t = new TraitValue.BoolValue(true);
        TraitValue f = new TraitValue.BoolValue(false);
        assertTrue(((TraitValue.BoolValue) t).value());
        assertFalse(((TraitValue.BoolValue) f).value());
    }

    @Test
    void referenceValue() {
        TraitValue v = new TraitValue.ReferenceValue("com.example.OrderStatus");
        assertInstanceOf(TraitValue.ReferenceValue.class, v);
        assertEquals("com.example.OrderStatus", ((TraitValue.ReferenceValue) v).qualifiedId());
    }

    @Test
    void sealedExhaustivePatternMatch() {
        TraitValue v = new TraitValue.StringValue("test");
        // Exhaustive switch — compiles only if all permitted types are covered.
        String result = switch (v) {
            case TraitValue.StringValue    sv -> "string:" + sv.value();
            case TraitValue.NumberValue    nv -> "number:" + nv.value();
            case TraitValue.BoolValue      bv -> "bool:"   + bv.value();
            case TraitValue.ReferenceValue rv -> "ref:"    + rv.qualifiedId();
        };
        assertEquals("string:test", result);
    }
}
