package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JourneyDefTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    private JourneyDef buildJourney(List<TraitApplication> traits) {
        return new JourneyDef(
                "GuestCheckout", traits, List.of(),
                "Customer", List.of(), List.of(), Map.of(), null, LOC);
    }

    @Test
    void actorNamePresentWhenDeclared() {
        var journey = buildJourney(List.of());
        assertTrue(journey.actorName().isPresent());
        assertEquals("Customer", journey.actorName().get());
    }

    @Test
    void actorNameEmptyWhenNull() {
        var journey = new JourneyDef(
                "Incomplete", List.of(), List.of(),
                null, List.of(), List.of(), Map.of(), null, LOC);
        assertTrue(journey.actorName().isEmpty());
    }

    @Test
    void journeyOutcomesEmptyWhenNull() {
        var journey = buildJourney(List.of());
        assertTrue(journey.journeyOutcomes().isEmpty());
    }

    @Test
    void journeyOutcomesPresentWhenSet() {
        var outcomes = new JourneyOutcomes("Order created", "Cart intact", LOC);
        var journey = new JourneyDef(
                "GuestCheckout", List.of(), List.of(),
                "Customer", List.of(), List.of(), Map.of(), outcomes, LOC);
        assertTrue(journey.journeyOutcomes().isPresent());
        assertEquals("Order created", journey.journeyOutcomes().get().success());
    }

    @Test
    void kpiMetricExtractedFromKpiTrait() {
        var metricArg = new TraitArg("metric", new TraitValue.StringValue("CheckoutConversion"), LOC);
        var targetArg = new TraitArg("target", new TraitValue.StringValue(">75%"), LOC);
        var kpiTrait  = new TraitApplication("kpi", List.of(metricArg, targetArg), LOC);
        var journey   = buildJourney(List.of(kpiTrait));

        assertTrue(journey.kpiMetric().isPresent());
        assertEquals("CheckoutConversion", journey.kpiMetric().get());
    }

    @Test
    void kpiMetricEmptyWithNoKpiTrait() {
        var journey = buildJourney(List.of());
        assertTrue(journey.kpiMetric().isEmpty());
    }

    @Test
    void hasComplianceTraitWhenPresent() {
        var complianceArg   = new TraitArg(null, new TraitValue.StringValue("PCI-DSS"), LOC);
        var complianceTrait = new TraitApplication("compliance", List.of(complianceArg), LOC);
        var journey         = buildJourney(List.of(complianceTrait));
        assertTrue(journey.hasComplianceTrait());
    }

    @Test
    void doesNotHaveComplianceTraitWhenAbsent() {
        var journey = buildJourney(List.of());
        assertFalse(journey.hasComplianceTrait());
    }

    @Test
    void implementsShapeDefinition() {
        var journey = buildJourney(List.of());
        assertInstanceOf(ShapeDefinition.class, journey);
        assertEquals("GuestCheckout", journey.name());
    }
}
