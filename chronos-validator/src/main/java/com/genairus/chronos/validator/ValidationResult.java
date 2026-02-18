package com.genairus.chronos.validator;

import java.util.List;

/**
 * The outcome of a {@link ChronosValidator#validate} call — an ordered list of
 * all {@link ValidationIssue}s produced, in the order the rules ran.
 *
 * @param issues all diagnostics, errors before warnings within each rule
 */
public record ValidationResult(List<ValidationIssue> issues) {

    /** Returns {@code true} when there are no issues at all. */
    public boolean isEmpty() {
        return issues.isEmpty();
    }

    /** Returns {@code true} when at least one issue has severity {@code ERROR}. */
    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR);
    }

    /** All issues with severity {@code ERROR}, in encounter order. */
    public List<ValidationIssue> errors() {
        return issues.stream()
                .filter(i -> i.severity() == ValidationSeverity.ERROR)
                .toList();
    }

    /** All issues with severity {@code WARNING}, in encounter order. */
    public List<ValidationIssue> warnings() {
        return issues.stream()
                .filter(i -> i.severity() == ValidationSeverity.WARNING)
                .toList();
    }
}
