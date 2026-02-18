package com.genairus.chronos.validator;

import com.genairus.chronos.model.SourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationIssueTest {

    private static final SourceLocation LOC = SourceLocation.of("checkout.chronos", 12, 1);

    @Test
    void errorIssueFormatted() {
        var issue = new ValidationIssue("CHR-001", ValidationSeverity.ERROR,
                "Journey 'GuestCheckout' must declare an actor", LOC);
        assertEquals("ERROR   [CHR-001] checkout.chronos:12  Journey 'GuestCheckout' must declare an actor",
                issue.toString());
    }

    @Test
    void warningIssueFormatted() {
        var issue = new ValidationIssue("CHR-009", ValidationSeverity.WARNING,
                "Journey 'GuestCheckout' is missing a @kpi trait", LOC);
        assertEquals("WARNING [CHR-009] checkout.chronos:12  Journey 'GuestCheckout' is missing a @kpi trait",
                issue.toString());
    }

    @Test
    void accessorsReturnConstructedValues() {
        var issue = new ValidationIssue("CHR-005", ValidationSeverity.ERROR, "Duplicate shape name 'Order'", LOC);
        assertEquals("CHR-005", issue.ruleCode());
        assertEquals(ValidationSeverity.ERROR, issue.severity());
        assertEquals("Duplicate shape name 'Order'", issue.message());
        assertEquals(LOC, issue.location());
    }
}
