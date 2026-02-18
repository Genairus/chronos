package com.genairus.chronos.model;

import java.util.Optional;

/**
 * The terminal outcome declarations for a journey.
 *
 * <pre>
 *   outcomes: {
 *       success: "Order record exists with status PAID",
 *       failure: "Cart remains intact with actionable error message"
 *   }
 * </pre>
 *
 * <p>Either entry may be {@code null} if not declared in source; the validator
 * (CHR-002) requires at minimum a {@code success} outcome on every journey.
 *
 * @param success  the success terminal description, or {@code null} if missing
 * @param failure  the failure terminal description, or {@code null} if missing
 * @param location source location of the {@code outcomes} keyword
 */
public record JourneyOutcomes(String success, String failure, SourceLocation location) {

    public Optional<String> successOutcome() {
        return Optional.ofNullable(success);
    }

    public Optional<String> failureOutcome() {
        return Optional.ofNullable(failure);
    }
}
