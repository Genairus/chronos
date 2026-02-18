package com.genairus.chronos.model;

import java.util.List;
import java.util.Optional;

/**
 * A named alternative or error flow branch within a journey.
 *
 * <pre>
 *   variants: {
 *       PaymentDeclined: {
 *           trigger: "Payment gateway returns declined status"
 *           steps: [
 *               step NotifyDecline {
 *                   expectation: "System displays payment declined message"
 *               }
 *           ]
 *           outcome: ReturnToStep(ChoosePayment)
 *       }
 *   }
 * </pre>
 *
 * @param name     the variant name (PascalCase)
 * @param trigger  the condition that activates this variant
 * @param steps    the steps within this variant (may be empty)
 * @param outcome  the terminal transition for this variant, or empty if not declared
 * @param location source location of the variant name token
 */
public record Variant(
        String name,
        String trigger,
        List<Step> steps,
        Optional<OutcomeExpr> outcome,
        SourceLocation location) {}
