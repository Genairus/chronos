# Industry Blueprint: Fintech (Global Payments)

This blueprint demonstrates how to use Chronos to model a high-stakes **Fintech** application: a **Cross-Border Payment System** with built-in Anti-Money Laundering (AML) checks and multi-currency support.

---

## 1. The Data Model: Entities & Shapes

Define the core building blocks of your global payment system.

```chronos
namespace fintech.payments

use fintech.common#Currency

/// A user's multi-currency account.
entity Account {
    id: String
    balance: Money
    status: AccountStatus
}

/// A single payment transaction.
entity Transaction {
    @required
    id: String
    amount: Money
    sender: Account
    recipient: Account
    status: TransactionStatus
}

shape Money {
    amount: Float
    currency: Currency
}

enum AccountStatus {
    ACTIVE    = 1
    FROZEN    = 2
    CLOSED    = 3
}
```

---

## 2. Governance: Compliance & Deny Blocks

In Fintech, compliance is your first-class requirement. Use `policy` and `deny` to encode your regulatory obligations.

```chronos
/// Anti-Money Laundering (AML) Policy.
@compliance("AML-Regulation")
policy AMLCheck {
    description: "All transactions over $10,000 must undergo manual review."
}

/// Explicit Prohibition: No negative balances.
deny Overdraft {
    description: "The system must never allow a transaction that results in a negative balance."
    scope: [Account]
    severity: critical
}

/// Explicit Prohibition: No sanctioned countries.
deny SanctionedCountryTransfer {
    description: "The system must never transfer funds to a sanctioned jurisdiction."
    scope: [Transaction]
    severity: critical
}
```

---

## 3. The Flow: A Cross-Border Payment Journey

Model the user experience (UX) and the business logic (BL) in a single journey.

```chronos
/// The end-to-end journey for a customer sending money abroad.
journey CrossBorderPayment {
    actor: Customer
    
    preconditions: [
        "Sender account is ACTIVE",
        "Sender has sufficient balance"
    ]
    
    steps: [
        step EnterDetails {
            action: "Customer enters recipient IBAN and amount"
            expectation: "System validates IBAN format and fetches exchange rate"
            telemetry: [PaymentInitiated]
        },
        step PerformAMLCheck {
            action: "System runs transaction against AML watchlist"
            expectation: "Transaction is flagged if risk is detected"
            outcome: TransitionTo(PENDING_REVIEW)
        },
        step ExecuteTransfer {
            action: "Customer confirms the final amount and rate"
            expectation: "Funds are debited from Sender and credited to Recipient"
            outcome: TransitionTo(SETTLED)
            telemetry: [PaymentCompleted]
        }
    ]
    
    variants: {
        AMLFlagged: {
            trigger: AMLRiskError
            steps: [
                step NotifyCompliance {
                    action: "System notifies compliance officer for review"
                    expectation: "Payment is held in PENDING_REVIEW state"
                }
            ]
        },
        InsufficientFunds: {
            trigger: BalanceLowError
            steps: [
                step PromptTopUp {
                    action: "System prompts customer to add funds"
                    expectation: "Payment is cancelled or retried after top-up"
                    outcome: ReturnToStep(EnterDetails)
                }
            ]
        }
    }
}
```

---

## 4. State Management: The Transaction Lifecycle

Visualize the transaction's lifecycle using a `statemachine`.

```chronos
statemachine TransactionLifecycle {
    entity: Transaction
    field: status
    
    states: [INITIATED, PENDING_REVIEW, AUTHORIZED, SETTLED, CANCELLED, FAILED]
    initial: INITIATED
    terminal: [SETTLED, CANCELLED, FAILED]
    
    transitions: [
        INITIATED -> PENDING_REVIEW {
            guard: "Amount > 10000 OR RiskFlag == true"
        },
        INITIATED -> AUTHORIZED {
            guard: "RiskFlag == false"
        },
        AUTHORIZED -> SETTLED {
            action: "Release funds to recipient"
        }
    ]
}
```

---

### **TPM Insights: Why this works**

- **Deterministic Logic:** Your developers no longer have to guess what "AML Check" means in code. The `variants` and `statemachine` define it clearly.
- **Auto-Generated Jira:** Running `chronos generate --target jira` will create an Epic for "Cross-Border Payment" and separate Stories for "Enter Details," "Perform AML Check," and "Execute Transfer."
- **Traceability:** Every `telemetry` tag in your model maps directly to an event your data team can track.
