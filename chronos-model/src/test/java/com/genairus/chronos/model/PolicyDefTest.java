package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyDefTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void complianceFrameworkExtractedFromTrait() {
        var arg   = new TraitArg(null, new TraitValue.StringValue("PCI-DSS"), LOC);
        var trait = new TraitApplication("compliance", List.of(arg), LOC);
        var policy = new PolicyDef("CardDataHandling",
                "Raw card numbers must never be stored",
                List.of(trait), List.of(), LOC);

        assertTrue(policy.complianceFramework().isPresent());
        assertEquals("PCI-DSS", policy.complianceFramework().get());
    }

    @Test
    void complianceFrameworkEmptyWithoutTrait() {
        var policy = new PolicyDef("InternalPolicy", "Some rule", List.of(), List.of(), LOC);
        assertTrue(policy.complianceFramework().isEmpty());
    }

    @Test
    void implementsShapeDefinition() {
        var policy = new PolicyDef("DataRetention", "7 years", List.of(), List.of(), LOC);
        assertInstanceOf(ShapeDefinition.class, policy);
        assertEquals("DataRetention", policy.name());
    }
}
