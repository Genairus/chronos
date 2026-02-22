# Governance: The Guardrails of your Product

In industries like **Fintech** and **Healthcare**, compliance is not optional. In Chronos, you define your business and regulatory rules as **Governance Shapes**: **Policy**, **Invariant**, **Deny**, and **Error**.

---

## 1. `policy` — Strategic & Compliance Rules

A **Policy** is a high-level statement of intent. It documents **why** a certain rule exists and what compliance framework it belongs to.

### **Why use it?**
- To document your **Compliance Standards** (HIPAA, GDPR, PCI-DSS).
- To create a "Regulatory Library" that your product must follow.
- To group related technical rules under a single business "Why?".

### **Example (Healthcare HIPAA)**

```chronos
/// HIPAA-compliant data access policy.
@compliance("HIPAA")
policy PatientDataAccess {
    description: "Personal health information (PHI) must only be accessed by authorized clinical staff."
}
```

---

## 2. `invariant` — Hard System Rules

An **Invariant** is a rule that must **always** be true. If an Invariant is violated, the system is in an invalid state.

### **Why use it?**
- To enforce mathematical or logical constraints (e.g., an Account balance should never be negative).
- To define **Cross-Entity Rules** (e.g., a Patient should have at least one Contact Method).
- To automatically generate **Unit Tests** for your developers.

### **Example (Fintech Balance Control)**

```chronos
entity Account {
    balance: Float
    
    /// The balance must never drop below the overdraft limit.
    invariant PositiveBalance {
        expression: "balance >= -500"
        severity: error
        message: "Account balance is below the overdraft limit."
    }
}
```

---

## 3. `deny` — Explicit Prohibitions

A **Deny** block is a "Negative Requirement." It defines what the system must **never** do. In AI-assisted development, `deny` blocks are critical to preventing your AI from making dangerous implementation choices.

### **Why use it?**
- To prevent **Data Leakage** (e.g., never log sensitive healthcare data).
- To block **Unauthorized Actions** (e.g., a customer cannot change their own interest rate).
- To enforce **Security Best Practices** (e.g., no plain-text passwords).

### **Example (AI Privacy)**

```chronos
/// Prevent the AI model from seeing PII.
@compliance("GDPR")
deny SharePIIWithLLM {
    description: "The system must never send PII (Name, SSN) to a third-party LLM provider."
    scope: [PatientData]
    severity: critical
}
```

---

## 4. `error` — Structured Failures

An **Error** shape defines a named failure condition with its own metadata. Instead of generic "Error 500" messages, you define specific, business-meaningful errors.

### **Why use it?**
- To define **Retry Logic** (is this error recoverable?).
- To provide a **Structured Payload** for the frontend (e.g., a specific error code).
- To drive **Alternative Paths** in a Journey's `variants`.

### **Example (Fintech Payment Decline)**

```chronos
/// A typed error for declined payments.
error PaymentDeclinedError {
    code: "PAY-001"
    severity: high
    recoverable: true
    message: "Payment was declined by the card issuer."
    
    payload: {
        reasonCode: String
        suggestedAction: String
    }
}
```

---

### **The TPM Cheat Sheet: Severity Levels**

When defining your governance rules, use these severity levels to control the compiler's behavior:

| Severity | Impact |
|----------|--------|
| `critical` | **Build Fails.** The model is unsafe and cannot be implemented. |
| `high`     | **Warning with Review Required.** Serious issue that needs manual approval. |
| `medium`   | **General Warning.** Potential issue that should be addressed. |
| `low`      | **Information.** Helpful advice for the development team. |
