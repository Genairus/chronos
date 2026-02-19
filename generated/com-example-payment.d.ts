/**
 * TypeScript type definitions for com.example.payment
 * Generated from Chronos model - do not edit manually
 */

/**
 * Payload for PaymentDeclinedError
 */
export interface PaymentDeclinedErrorPayload {
  gatewayCode: string;
  retryable: boolean;
  declineReason: string;
}

/**
 * Payment was declined by the gateway
 * Code: PAY-001
 * Severity: high
 * Recoverable: true
 */
export interface PaymentDeclinedError {
  code: "PAY-001";
  message: string;
  payload: PaymentDeclinedErrorPayload;
}

/**
 * Payload for NetworkTimeoutError
 */
export interface NetworkTimeoutErrorPayload {
  attemptCount: number;
  lastAttemptTime: string;
}

/**
 * Network timeout occurred during payment processing
 * Code: NET-001
 * Severity: medium
 * Recoverable: true
 */
export interface NetworkTimeoutError {
  code: "NET-001";
  message: string;
  payload: NetworkTimeoutErrorPayload;
}

/**
 * Payload for InsufficientFundsError
 */
export interface InsufficientFundsErrorPayload {
  availableBalance: number;
  requestedAmount: number;
}

/**
 * Insufficient funds in account
 * Code: PAY-002
 * Severity: low
 * Recoverable: false
 */
export interface InsufficientFundsError {
  code: "PAY-002";
  message: string;
  payload: InsufficientFundsErrorPayload;
}

/**
 * Critical system failure occurred
 * Code: SYS-001
 * Severity: critical
 * Recoverable: false
 */
export interface SystemFailureError {
  code: "SYS-001";
  message: string;
}

