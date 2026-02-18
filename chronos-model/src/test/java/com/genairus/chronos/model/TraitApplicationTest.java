package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraitApplicationTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void bareTraitHasNoArgs() {
        var trait = new TraitApplication("pii", List.of(), LOC);
        assertTrue(trait.isBare());
        assertTrue(trait.firstPositionalValue().isEmpty());
        assertTrue(trait.namedValue("anything").isEmpty());
    }

    @Test
    void positionalArgIsRetrievableAsFirstValue() {
        var arg = new TraitArg(null, new TraitValue.StringValue("GDPR"), LOC);
        var trait = new TraitApplication("compliance", List.of(arg), LOC);

        assertFalse(trait.isBare());
        assertTrue(trait.firstPositionalValue().isPresent());
        assertEquals("GDPR", ((TraitValue.StringValue) trait.firstPositionalValue().get()).value());
    }

    @Test
    void namedArgIsRetrievableByKey() {
        var metricArg = new TraitArg("metric", new TraitValue.StringValue("CheckoutConversion"), LOC);
        var targetArg = new TraitArg("target", new TraitValue.StringValue(">75%"), LOC);
        var trait = new TraitApplication("kpi", List.of(metricArg, targetArg), LOC);

        assertFalse(trait.isBare());
        assertTrue(trait.namedValue("metric").isPresent());
        assertEquals("CheckoutConversion",
                ((TraitValue.StringValue) trait.namedValue("metric").get()).value());
        assertTrue(trait.namedValue("target").isPresent());
        assertTrue(trait.namedValue("missing").isEmpty());
    }

    @Test
    void namedValueIgnoresPositionalArgs() {
        // A positional arg should not match a named lookup.
        var positional = new TraitArg(null, new TraitValue.StringValue("val"), LOC);
        var trait = new TraitApplication("description", List.of(positional), LOC);
        assertTrue(trait.namedValue("description").isEmpty());
    }

    @Test
    void firstPositionalValueIgnoresNamedArgs() {
        // A named arg should not match a positional lookup.
        var named = new TraitArg("key", new TraitValue.StringValue("val"), LOC);
        var trait = new TraitApplication("slo", List.of(named), LOC);
        assertTrue(trait.firstPositionalValue().isEmpty());
    }
}
