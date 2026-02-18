package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceLocationTest {

    @Test
    void factoryMethodMatchesConstructor() {
        var loc = SourceLocation.of("checkout.chronos", 12, 5);
        assertEquals("checkout.chronos", loc.file());
        assertEquals(12, loc.line());
        assertEquals(5, loc.column());
    }

    @Test
    void unknownSentinelHasZeroLineAndColumn() {
        var loc = SourceLocation.unknown();
        assertEquals("<unknown>", loc.file());
        assertEquals(0, loc.line());
        assertEquals(0, loc.column());
    }

    @Test
    void toStringIncludesFileAndLine() {
        var loc = SourceLocation.of("requirements/checkout.chronos", 42, 1);
        assertEquals("requirements/checkout.chronos:42", loc.toString());
    }

    @Test
    void recordEqualityByValue() {
        var a = SourceLocation.of("foo.chronos", 1, 1);
        var b = SourceLocation.of("foo.chronos", 1, 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
