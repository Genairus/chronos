package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumMemberTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void memberWithOrdinal() {
        var member = EnumMember.of("PAID", 2, LOC);
        assertEquals("PAID", member.name());
        assertTrue(member.ordinal().isPresent());
        assertEquals(2, member.ordinal().getAsInt());
    }

    @Test
    void memberWithoutOrdinal() {
        var member = EnumMember.of("SHIPPED", LOC);
        assertEquals("SHIPPED", member.name());
        assertTrue(member.ordinal().isEmpty());
    }

    @Test
    void directConstructorWithOrdinal() {
        var member = new EnumMember("PENDING", java.util.OptionalInt.of(1), LOC);
        assertEquals(1, member.ordinal().getAsInt());
    }

    @Test
    void recordEqualityByValue() {
        var a = EnumMember.of("PAID", 2, LOC);
        var b = EnumMember.of("PAID", 2, LOC);
        assertEquals(a, b);
    }
}
