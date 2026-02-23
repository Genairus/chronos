# Example: E-Commerce Checkout

The `examples/ecommerce/` directory contains a production-quality multi-namespace model. It shows how a real bounded-context design maps onto Chronos: three namespaces, cross-file `use` imports, actor inheritance, error types, `deny` blocks, a state machine, and two journeys with error-path variants.

## Directory structure

```
ecommerce/
├── common/
│   ├── types.chronos                       # Money, Address shapes; Customer, Product entities;
│   │                                       # CartItemList, TagMap collections; global invariant
│   └── common-actors-and-policies.chronos  # BaseUser actor; GuestUser/AuthenticatedUser extending it;
│                                           # GDPR and PCI policies
├── checkout/
│   ├── checkout-domain.chronos             # CartState enum; Cart and Order entities with invariants;
│   │                                       # relationships; use imports from ecommerce.common
│   ├── checkout-journeys.chronos           # GuestCheckout and AuthenticatedCheckout journeys
│   │                                       # with variants, SLO/KPI/compliance traits
│   └── checkout-statemachine.chronos       # OrderLifecycle statemachine on Order.status
└── payments/
    └── payments.chronos                    # PaymentMethod enum; PaymentInstrument entity;
                                            # three error types; one deny block
```

Three namespaces: `ecommerce.common`, `ecommerce.checkout`, `ecommerce.payments`.

## Run it

```sh
# Combined PRD (all three namespaces in one document)
chronos prd examples/ecommerce/ --name ecommerce-prd --output /tmp/ecommerce/

# Jira backlog
chronos generate --target jira examples/ecommerce/ --output /tmp/ecommerce/

# TypeScript types
chronos generate --target typescript examples/ecommerce/ --output /tmp/ecommerce/
```

## What it demonstrates

### Actor inheritance

```chronos
// common-actors-and-policies.chronos
actor BaseUser

actor GuestUser extends BaseUser

actor AuthenticatedUser extends BaseUser
```

### Cross-namespace use imports

```chronos
// checkout-domain.chronos
namespace ecommerce.checkout

use ecommerce.common#Customer
use ecommerce.common#Product
use ecommerce.payments#PaymentMethod
```

### Journey with error-path variant

```chronos
journey GuestCheckout {
    actor: GuestUser
    steps: [ ... ]
    variants: {
        PaymentDeclined: {
            trigger: PaymentDeclinedError
            steps: [
                step NotifyCustomer {
                    action: "System shows decline reason and retry option"
                    outcome: ReturnToStep(EnterPayment)
                }
            ]
            outcome: ReturnToStep(EnterPayment)
        }
    }
    outcomes: {
        success: "Order confirmed and confirmation email sent",
        failure: "Cart intact, no charge made"
    }
}
```

### State machine

```chronos
statemachine OrderLifecycle {
    entity: Order
    field: status
    states: [PENDING, CONFIRMED, PAID, FAILED]
    initial: PENDING
    terminal: [PAID, FAILED]
    transitions: [
        PENDING -> CONFIRMED,
        CONFIRMED -> PAID { guard: "paymentAuthorized" },
        PENDING -> FAILED
    ]
}
```

### Deny block

```chronos
@compliance("PCI-DSS")
deny NoPiiInLogs {
    description: "Payment card numbers and CVV codes must never appear in application logs"
    scope: [PaymentInstrument]
    severity: critical
}
```
