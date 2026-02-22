<div class="hero-card">
  <h1>Requirements as Code</h1>
  <p>The "Requirements OS" for AI-Assisted Product Teams.</p>
</div>

**Chronos** is the bridge between Product intent and Engineering reality. 

Stop writing prose that goes stale in Confluence. Model your requirements in a structured, machine-readable language that your AI coding assistants, static analyzers, and documentation generators can finally understand.

---

### **For Technical Product Managers**

In an AI-first development world, **structured requirements are your most valuable asset.** Chronos allows you to:

- 🧠 **Define Intent:** Use 13 built-in "shapes" (Entities, Journeys, Policies) to model how your software should behave.
- 🛡️ **Enforce Governance:** Encode HIPAA, GDPR, or AML rules directly into your requirements.
- 🤖 **Empower AI Agents:** Give tools like Claude, Cursor, and GitHub Copilot an unambiguous "Source of Truth" to code from.
- ⚡ **Auto-Generate Everything:** One command emits your PRDs, Jira backlogs, TypeScript types, and Mermaid diagrams.

---

### **The "30-Second" Healthcare Example**

Model a complex, AI-assisted health triage flow with built-in compliance checks.

```chronos
namespace health.triage

actor Patient
actor Doctor

entity PatientData {
    @required
    patientId: String
    symptoms: String
    status: TriageStatus
}

enum TriageStatus {
    INTAKE
    UNDER_REVIEW
    URGENT_REVIEW
    RESOLVED
}

statemachine TriageLifecycle {
    entity: PatientData
    field: status
    states: [INTAKE, UNDER_REVIEW, URGENT_REVIEW, RESOLVED]
    initial: INTAKE
    terminal: [RESOLVED]
    transitions: [
        INTAKE -> UNDER_REVIEW,
        UNDER_REVIEW -> URGENT_REVIEW,
        UNDER_REVIEW -> RESOLVED,
        URGENT_REVIEW -> RESOLVED
    ]
}

event SymptomCaptured {}
event TriageRouted {}

/// HIPAA-compliant policy for sensitive data
@compliance("HIPAA")
deny LeakPHI {
    description: "The system must never log or store PHI in plain text"
    scope: [PatientData]
    severity: critical
}

/// AI-Assisted Triage Journey
@kpi(metric: "ai_triage_accuracy", target: "90%")
journey PatientTriage {
    actor: Patient
    steps: [
        step ReportSymptoms {
            action: "Patient describes symptoms to the AI"
            expectation: "AI analyzes text and extracts clinical indicators"
            telemetry: [SymptomCaptured]
        },
        step AIAnalysis {
            action: "AI suggests urgency level"
            expectation: "System routes high-urgency cases to a human Doctor"
            outcome: TransitionTo(URGENT_REVIEW)
            telemetry: [TriageRouted]
        }
    ]
    outcomes: {
        success: "Patient routed to appropriate care level",
        failure: "Triage incomplete; patient advised to contact emergency services"
    }
}
```

---

### **How it Works**

1.  **Model:** Write `.chronos` files (or have an AI assistant draft them for you).
2.  **Validate:** The Chronos compiler catches broken links and policy violations in real-time.
3.  **Generate:** Turn your model into everything the team needs to build.

```bash
chronos prd triage.chronos        # → Generates a perfect Markdown PRD
chronos generate --target jira    # → Generates your Jira Backlog (Epics & Stories)
chronos generate --target ts      # → Generates TypeScript types for developers
```

---

### **The Chronos Advantage**

| Traditional PRDs | Chronos Models |
|-----------------|--------------|
| Live in Confluence/Docs (stale) | Live in Git (Source of Truth) |
| Manual Jira entry (slow/error-prone) | Automated Jira generation |
| Ambiguous for AI assistants | Machine-readable and deterministic |
| Compliance is "checked" later | Compliance is "compiled" into the model |

---

### **Get Started**

- [**Install Chronos**](install.md) — Get the CLI tool.
- [**The Vision**](vision.md) — Learn why "Requirements as Code" is the future.
- [**Industry Blueprints**](examples/fintech.md) — See Fintech & Healthcare in action.
- [**The Language Reference**](shapes/index.md) — Deep dive into the "Chronos 13".
