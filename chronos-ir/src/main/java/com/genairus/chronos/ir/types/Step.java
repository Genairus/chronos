package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;
import java.util.Optional;

/**
 * A single step inside a journey or variant in the IR.
 *
 * @param name   the step name (PascalCase)
 * @param traits trait applications on this step (e.g. {@code @slo})
 * @param fields the step body fields in declaration order
 * @param span   source location of the step name token
 */
public record Step(
        String name,
        List<TraitApplication> traits,
        List<StepField> fields,
        Span span) {

    /** Returns the action text, or empty if absent. */
    public Optional<String> action() {
        return fields.stream()
                .filter(f -> f instanceof StepField.Action)
                .map(f -> ((StepField.Action) f).text())
                .findFirst();
    }

    /** Returns the expectation text, or empty if absent. */
    public Optional<String> expectation() {
        return fields.stream()
                .filter(f -> f instanceof StepField.Expectation)
                .map(f -> ((StepField.Expectation) f).text())
                .findFirst();
    }

    /** Returns the outcome expression, or empty if absent. */
    public Optional<OutcomeExpr> outcome() {
        return fields.stream()
                .filter(f -> f instanceof StepField.Outcome)
                .map(f -> ((StepField.Outcome) f).expr())
                .findFirst();
    }

    /** Returns all telemetry event identifiers declared in this step. */
    public List<String> telemetryEvents() {
        return fields.stream()
                .filter(f -> f instanceof StepField.Telemetry)
                .flatMap(f -> ((StepField.Telemetry) f).ids().stream())
                .toList();
    }

    /** Returns the risk annotation text, or empty if absent. */
    public Optional<String> risk() {
        return fields.stream()
                .filter(f -> f instanceof StepField.Risk)
                .map(f -> ((StepField.Risk) f).text())
                .findFirst();
    }

    /** Returns all typed input fields declared in this step (in declaration order). */
    public List<DataField> inputFields() {
        return fields.stream()
                .filter(f -> f instanceof StepField.Input)
                .flatMap(f -> ((StepField.Input) f).fields().stream())
                .toList();
    }

    /** Returns all typed output fields declared in this step (in declaration order). */
    public List<DataField> outputFields() {
        return fields.stream()
                .filter(f -> f instanceof StepField.Output)
                .flatMap(f -> ((StepField.Output) f).fields().stream())
                .toList();
    }
}
