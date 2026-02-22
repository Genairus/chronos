# Actors & Relationships: The "Who" and the "How"

To complete your model, you need to define **who** is interacting with your system and **how** your data objects are connected. In Chronos, you use **Actor** and **Relationship**.

---

## 1. `actor` — The Participant

An **Actor** is a person, a system, or even an AI agent that participates in a **Journey**. 

### **Why use it?**
- To clarify who is responsible for each step in a flow.
- To define **Roles** and permissions (e.g., an *Admin* can see more than a *User*).
- To capture **AI Agents** as first-class participants in your requirements.

### **Example (Healthcare)**
```chronos
/// A person seeking medical advice.
actor Patient

/// A licensed medical professional.
actor Doctor

/// An AI triage model (System Actor).
actor TriageAI
```

### **Actor Inheritance**
You can also use `extends` to create hierarchies.
```chronos
actor Staff
actor Doctor extends Staff
actor Nurse extends Staff
```

---

## 2. `relationship` — Connecting your Entities

A **Relationship** defines how two [**Entities**](data.md#1-entity-high-value-business-objects) are linked together. This is the foundation of your "Knowledge Graph."

### **Why use it?**
- To define **Cardinality** (One-to-One, One-to-Many, Many-to-Many).
- To specify **Semantics** (is this just a link, or is one object "owned" by the other?).
- To help developers understand how to build the database schema.

### **Example (Fintech)**
```chronos
/// An account has many transactions.
relationship AccountTransactions {
    from: Account
    to: Transaction
    cardinality: one_to_many
}

/// A customer has multiple accounts (Composition).
relationship CustomerAccounts {
    from: Customer
    to: Account
    cardinality: one_to_many
    semantics: composition
}
```

---

### **The TPM Cheat Sheet: Cardinality & Semantics**

When defining your relationships, use these terms:

| Term | Meaning |
|------|---------|
| `one_to_one` | Each A has exactly one B (e.g., User → Profile). |
| `one_to_many` | One A has many Bs (e.g., Order → Items). |
| `many_to_many` | Many As share many Bs (e.g., Student → Classes). |
| `association` | A simple link (A knows B). |
| `composition` | Ownership (If A is deleted, B is deleted). |
| `aggregation` | A loose collection (A uses B, but B exists alone). |
