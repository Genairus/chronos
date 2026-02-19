package com.genairus.chronos.model;

import java.util.List;
import java.util.Optional;

/**
 * A global invariant definition — a boolean constraint that spans multiple entities.
 *
 * <pre>
 *   invariant ActiveOrderLimit {
 *       scope: [Customer, Order]
 *       expression: "count(customer.orders, o => o.status == PENDING) <= 10"
 *       severity: warning
 *       message: "Customer should not exceed 10 pending orders"
 *   }
 * </pre>
 *
 * @param name       the invariant name (PascalCase)
 * @param traits     trait applications applied to this invariant
 * @param docComments lines from preceding {@code ///} doc comments (without the {@code ///} prefix)
 * @param scope      list of entity names referenced in the expression
 * @param expression the boolean expression as a string (to be parsed in Phase 2.1.3)
 * @param severity   the severity level (error, warning, info)
 * @param message    optional custom error message
 * @param location   source location of the {@code invariant} keyword
 */
public record InvariantDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<String> scope,
        String expression,
        String severity,
        Optional<String> message,
        SourceLocation location) implements ShapeDefinition {}

