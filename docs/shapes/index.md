# The Chronos 13: Your Modeling Toolkit

To build a complete requirement, you use a set of **13 built-in "Shapes."** Each shape is designed to capture a specific part of your product's behavior, from the data it stores to the compliance rules it must follow.

Think of these as the **LEGO bricks of Product Management.**

---

## 🧱 The Building Blocks

### **1. Data Shapes**
Define the "What" — the objects and information your system manages.
- [**`entity`**](data.md#1-entity-high-value-business-objects): A core object with a unique identity (e.g., *Patient*, *Account*).
- [**`shape`**](data.md#2-shape-lightweight-value-objects): A reusable data container (e.g., *Address*, *Money*).
- [**`enum`**](data.md#3-enum-constants-and-statuses): A fixed list of allowed values (e.g., *Status*, *Category*).
- [**`list`**](data.md#multi-value-fields): A collection of items.
- [**`map`**](data.md#multi-value-fields): A key-value lookup.

### **2. Flow Shapes**
Define the "How" — the way users and systems interact.
- [**`journey`**](flows.md#1-journey-the-engine-of-value): An end-to-end user story or process.
- [**`statemachine`**](flows.md#2-statemachine-the-lifecycle-of-a-domain-object): The internal lifecycle of a data object.

### **3. Governance Shapes**
Define the "Rules" — the boundaries of your system.
- [**`policy`**](governance.md#1-policy-strategic--compliance-rules): High-level business or compliance statements.
- [**`invariant`**](governance.md#2-invariant-hard-system-rules): Rules that must always be true.
- [**`deny`**](governance.md#3-deny-explicit-prohibitions): Prohibitions (Negative Requirements).
- [**`error`**](governance.md#4-error-structured-failures): Specific, named failure conditions.

### **4. Relationship & Actor Shapes**
Define the "Who" and the "Links" — the participants and their connections.
- [**`actor`**](actors.md#1-actor-the-participant): Who or what interacts with the system.
- [**`relationship`**](actors.md#2-relationship-connecting-your-entities): How entities are linked to one another.

---

## 🚀 Pro-Tip for TPMs

You don't need to use all 13 shapes for every feature. 

- **Starting a new feature?** Start with a [**`journey`**](flows.md#1-journey-the-engine-of-value) to map the UX.
- **Handling sensitive data?** Add a [**`deny`**](governance.md#3-deny-explicit-prohibitions) block for privacy.
- **Managing complex states?** Define a [**`statemachine`**](flows.md#2-statemachine-the-lifecycle-of-a-domain-object) to prevent bugs.
