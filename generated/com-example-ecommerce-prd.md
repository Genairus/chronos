# com.example.ecommerce — Product Requirements

## Table of Contents

- [Data Model](#data-model)
  - [Entities](#entities)
  - [Value Objects](#value-objects)
  - [Enumerations](#enumerations)

---

## Data Model

### Entities

#### Customer

> A customer in the e-commerce system

| Field | Type | Required |
|-------|------|----------|
| id | String |  |
| email | String |  |
| orders | List<Order> |  |

#### Order

> An order placed by a customer

| Field | Type | Required |
|-------|------|----------|
| id | String |  |
| customerId | String |  |
| items | List<OrderItem> |  |
| totalAmount | Float |  |
| orderDate | Timestamp |  |
| shipDate | Timestamp |  |
| status | OrderStatus |  |

**Invariants:**

- **PositiveTotal** (error)
  - Expression: `totalAmount > 0`
  - Message: Order total must be positive
- **ShipAfterOrder** (error)
  - Expression: `shipDate > orderDate`
  - Message: Ship date must be after order date
- **ReasonableTotal** (warning)
  - Expression: `totalAmount < 1000000`
  - Message: Order total exceeds $1M - please verify

#### OrderItem

| Field | Type | Required |
|-------|------|----------|
| productId | String |  |
| quantity | Integer |  |
| unitPrice | Money |  |

### Value Objects

#### Money

| Field | Type | Required |
|-------|------|----------|
| amount | Float |  |
| currency | String |  |

### Enumerations

#### OrderStatus

| Member | Ordinal |
|--------|----------|
| PENDING | — |
| CONFIRMED | — |
| SHIPPED | — |
| DELIVERED | — |
| CANCELLED | — |

---

## Global Invariants

Cross-entity constraints that must always hold true:

### OrderCustomerExists

> Ensures every order references an existing customer

**Scope:** Order, Customer

**Expression:** `exists(Customer, c => c.id == Order.customerId)`

**Severity:** error

**Message:** Every order must reference an existing customer

### ActiveOrderLimit

> Warns if a customer has too many pending orders

**Scope:** Customer, Order

**Expression:** `count(Customer.orders, o => o.status == PENDING) <= 10`

**Severity:** warning

**Message:** Customer should not exceed 10 pending orders

