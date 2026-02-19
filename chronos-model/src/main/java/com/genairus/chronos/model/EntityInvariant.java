package com.genairus.chronos.model;

import java.util.Optional;

/**
 * An invariant declared inside an entity block — a boolean constraint that must
 * always hold true for instances of that entity.
 *
 * <pre>
 *   entity Order {
 *       id: String
 *       items: List&lt;OrderItem&gt;
 *       total: Money
 *       shipDate: Timestamp
 *       orderDate: Timestamp
 *
 *       invariant TotalMatchesItems {
 *           expression: "total.amount == sum(items, i => i.unitPrice.amount * i.quantity)"
 *           severity: error
 *       }
 *
 *       invariant ShipAfterOrder {
 *           expression: "shipDate > orderDate"
 *           severity: error
 *           message: "Ship date must be after order date"
 *       }
 *   }
 * </pre>
 *
 * @param name       the invariant name (PascalCase)
 * @param expression the boolean expression as a string (to be parsed in Phase 2.1.3)
 * @param severity   the severity level (error, warning, info)
 * @param message    optional custom error message
 * @param location   source location of the {@code invariant} keyword
 */
public record EntityInvariant(
        String name,
        String expression,
        String severity,
        Optional<String> message,
        SourceLocation location) {}

