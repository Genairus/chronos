# Chronos Expression Language Design

## Overview

The Chronos Expression Language is a micro-language for expressing boolean constraints in invariant blocks. It supports field references, comparison operators, arithmetic operations, and aggregation functions.

## Design Principles

1. **Type-safe**: All expressions are type-checked against entity fields
2. **Read-only**: Expressions cannot modify state, only evaluate conditions
3. **Deterministic**: No side effects, same inputs always produce same output
4. **Simple**: Minimal syntax focused on common business rule patterns

## Syntax Elements

### 1. Field References

Field references use dot notation to access entity fields:

```
fieldName
entity.fieldName
entity.nestedField.property
```

Examples:
- `total` - references the `total` field in the current entity
- `order.total` - references the `total` field of the `order` entity
- `customer.address.zipCode` - nested field access

### 2. Comparison Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `==` | Equality | `status == ACTIVE` |
| `!=` | Inequality | `quantity != 0` |
| `<` | Less than | `age < 18` |
| `<=` | Less than or equal | `price <= maxPrice` |
| `>` | Greater than | `shipDate > orderDate` |
| `>=` | Greater than or equal | `balance >= 0` |

### 3. Logical Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `&&` | Logical AND | `age >= 18 && hasConsent` |
| `\|\|` | Logical OR | `isPremium \|\| isVIP` |
| `!` | Logical NOT | `!isDeleted` |

### 4. Arithmetic Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `+` | Addition | `subtotal + tax` |
| `-` | Subtraction | `endDate - startDate` |
| `*` | Multiplication | `quantity * unitPrice` |
| `/` | Division | `total / itemCount` |
| `%` | Modulo | `orderNumber % 10` |

### 5. Aggregation Functions

Aggregation functions operate on collections (List types):

#### count(collection, predicate)
Counts elements matching a predicate.

```
count(orders, o => o.status == PENDING)
count(items, i => i.quantity > 0)
```

#### sum(collection, selector)
Sums a numeric property across collection elements.

```
sum(items, i => i.unitPrice * i.quantity)
sum(payments, p => p.amount)
```

#### min(collection, selector)
Finds the minimum value.

```
min(items, i => i.price)
min(dates, d => d.timestamp)
```

#### max(collection, selector)
Finds the maximum value.

```
max(items, i => i.price)
max(scores, s => s.value)
```

#### exists(collection, predicate)
Returns true if any element matches the predicate.

```
exists(items, i => i.isBackordered)
exists(tags, t => t == "urgent")
```

#### forAll(collection, predicate)
Returns true if all elements match the predicate.

```
forAll(items, i => i.quantity > 0)
forAll(users, u => u.hasAcceptedTerms)
```

### 6. Lambda Expressions

Lambda expressions are used in aggregation functions:

```
element => expression
```

Examples:
- `i => i.quantity > 0` - predicate checking if quantity is positive
- `o => o.status == PENDING` - predicate checking order status
- `i => i.unitPrice * i.quantity` - selector computing line total

### 7. Literals

| Type | Example |
|------|---------|
| Integer | `42`, `-10`, `0` |
| Float | `3.14`, `-0.5`, `2.0` |
| String | `"ACTIVE"`, `"pending"` |
| Boolean | `true`, `false` |
| Null | `null` |

### 8. Parentheses

Parentheses control evaluation order:

```
(subtotal + tax) * discountRate
total == (sum(items, i => i.price) + shippingCost)
```

## Type System

### Primitive Types
- `Integer` - whole numbers
- `Float` - decimal numbers
- `String` - text
- `Boolean` - true/false
- `Timestamp` - date/time values

### Collection Types
- `List<T>` - ordered collection of type T

### Type Coercion
- Integer can be used where Float is expected
- No other implicit conversions

## Examples

### Entity-Scoped Invariants

```chronos
entity Order {
    id: String
    items: List<OrderItem>
    total: Money
    shipDate: Timestamp
    orderDate: Timestamp
    status: OrderStatus

    invariant TotalMatchesItems {
        expression: "total.amount == sum(items, i => i.unitPrice.amount * i.quantity)"
        severity: error
    }

    invariant ShipAfterOrder {
        expression: "shipDate > orderDate"
        severity: error
        message: "Ship date must be after order date"
    }

    invariant HasItems {
        expression: "count(items, i => true) > 0"
        severity: error
    }
}
```

### Global Invariants

```chronos
invariant ActiveOrderLimit {
    scope: [Customer, Order]
    expression: "count(customer.orders, o => o.status == PENDING) <= 10"
    severity: warning
    message: "Customer should not exceed 10 pending orders"
}
```

## Implementation Notes

1. **Phase 2.1.2**: Parser will recognize invariant blocks and extract expression strings
2. **Phase 2.1.3**: Expression parser will parse and type-check expressions
3. **Phase 2.1.4**: Generators will emit invariant documentation
4. **Phase 2.1.5**: Validator will warn about optional field references without null guards

## Future Enhancements

- String operations (concat, substring, length)
- Date/time arithmetic
- Regular expression matching
- Custom functions

