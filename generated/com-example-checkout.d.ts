/**
 * TypeScript type definitions for com.example.checkout
 * Generated from Chronos model - do not edit manually
 */

/**
 * A single line item in the shopping cart.
 */
export interface CartItem {
  id: string;
  quantity?: number;
  price?: number;
  discount?: number;
  active?: boolean;
  createdAt?: Date;
  thumbnail?: Blob;
  metadata?: any;
}

export interface Money {
  amount?: number;
  currency?: Currency;
}

export enum PaymentStatus {
  PENDING = "PENDING",
  PROCESSING = "PROCESSING",
  PAID = "PAID",
  DECLINED = "DECLINED"
}

export enum CartState {
  EMPTY = "EMPTY",
  ACTIVE = "ACTIVE",
  ABANDONED = "ABANDONED",
  CHECKED_OUT = "CHECKED_OUT"
}

/**
 * Payload for PaymentDeclinedError
 */
export interface PaymentDeclinedErrorPayload {
  declineReason: string;
  retryAllowed: boolean;
}

/**
 * Payment gateway returned a declined response
 * Code: PAYMENT_DECLINED
 * Severity: high
 * Recoverable: true
 */
export interface PaymentDeclinedError {
  code: "PAYMENT_DECLINED";
  message: string;
  payload: PaymentDeclinedErrorPayload;
}

