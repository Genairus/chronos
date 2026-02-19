package com.genairus.chronos.generators;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorRegistryTest {

    @Test
    void markdownTargetResolvesToMarkdownPrdGenerator() {
        assertInstanceOf(MarkdownPrdGenerator.class, GeneratorRegistry.get("markdown"));
    }

    @Test
    void prdTargetResolvesToMarkdownPrdGenerator() {
        assertInstanceOf(MarkdownPrdGenerator.class, GeneratorRegistry.get("prd"));
    }

    @Test
    void markdownAndPrdReturnSameInstance() {
        assertSame(GeneratorRegistry.get("markdown"), GeneratorRegistry.get("prd"));
    }

    @Test
    void unknownTargetThrowsIllegalArgumentException() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> GeneratorRegistry.get("gherkin"));
        assertTrue(ex.getMessage().contains("gherkin"),
                "Exception message should include the unknown target name");
    }

    @Test
    void unknownTargetMessageContainsTargetName() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> GeneratorRegistry.get("unknown-format"));
        assertTrue(ex.getMessage().contains("unknown-format"));
    }
}
