# Core Data: Defining your Business Domain

The first step in any requirement is defining **what** exists in your system. In Chronos, you define your business objects using three core "shapes": **Entity**, **Shape**, and **Enum**.

---

## 1. `entity` — High-Value Business Objects

An **Entity** is a core "thing" in your system that has a unique identity. In **Healthcare**, an `entity` would be a **Patient** or a **Doctor**. In **Fintech**, it's an **Account** or a **Transaction**.

### **Why use it?**
- To define high-level objects that persist over time.
- To attach **Traits** like `@pii` or `@compliance` to specific fields.
- To create **Relationships** between entities (e.g., a *Patient* has many *Medical Records*).

### **Example (Healthcare)**
```chronos
/// A patient registered in the clinic system.
@compliance("HIPAA")
entity Patient {
    @required
    id: String
    
    @pii(type: "NAME")
    fullName: String
    
    @pii(type: "CONTACT")
    email: String
    
    status: PatientStatus
}
```

---

## 2. `shape` — Lightweight Value Objects

A **Shape** (or "Value Object") is a container for data that *doesn't* have its own identity. It only exists as a part of an Entity. Think of a **Mailing Address** or a **Currency Amount**.

### **Why use it?**
- To group related fields together for reuse.
- To simplify complex entities.
- To define the "input" and "output" of a step in a Journey.

### **Example (Fintech)**
```chronos
/// A precise monetary amount with its currency.
shape Money {
    @required
    amount: Float
    
    @required
    currency: String
}

entity Account {
    id: String
    balance: Money
}
```

---

## 3. `enum` — Constants and Statuses

An **Enum** is a fixed list of allowed values. Enums are the most common way to drive **State Machines** (e.g., `PENDING` → `APPROVED` → `PAID`).

### **Why use it?**
- To ensure developers only use valid statuses.
- To provide clear "triggers" for alternative flows.
- To map human-friendly names (e.g., `ACTIVE`) to machine-friendly values (e.g., `1`).

### **Example (AI Health Triage)**
```chronos
/// The urgency levels for AI-assisted triage.
enum TriageUrgency {
    LOW      = 1
    MEDIUM   = 2
    HIGH     = 3
    CRITICAL = 4
}
```

---

### **The TPM Cheat Sheet: Primitive Types**

When defining your fields, use these built-in types:

| Type | When to use it |
|------|----------------|
| `String` | Text (names, IDs, descriptions). |
| `Integer` | Whole numbers (counts, ages). |
| `Float` | Decimal numbers (money, coordinates). |
| `Boolean` | True/False toggles. |
| `Timestamp` | Dates and times (ISO-8601). |
| `Blob` | Binary data (profile pictures, PDF uploads). |
| `Document` | Flexible, schemaless data (JSON-like blobs). |

---

### **Multi-Value Fields**

You can also create collections of types:
- `List<T>`: An ordered list (e.g., `List<String>` for tags).
- `Map<K, V>`: A key-value lookup (e.g., `Map<String, String>` for metadata).
