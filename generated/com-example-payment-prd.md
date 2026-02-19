# com.example.payment — Product Requirements

## Table of Contents

- [Error Catalog](#error-catalog)

---

## Error Catalog

| Error Type | Code | Severity | Recoverable | Message | Payload |
|------------|------|----------|-------------|---------|----------|
| PaymentDeclinedError | PAY-001 | high | Yes | Payment was declined by the gateway | gatewayCode: String, retryable: Boolean, declineReason: String |
| NetworkTimeoutError | NET-001 | medium | Yes | Network timeout occurred during payment processing | attemptCount: Integer, lastAttemptTime: String |
| InsufficientFundsError | PAY-002 | low | No | Insufficient funds in account | availableBalance: Float, requestedAmount: Float |
| SystemFailureError | SYS-001 | critical | No | Critical system failure occurred | — |
