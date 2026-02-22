# Industry Blueprint: Healthcare (AI-Assisted Triage)

This blueprint demonstrates how to model a **Healthcare AI** application: an **AI-Assisted Patient Triage** system. It highlights how to protect sensitive patient data (PHI) while leveraging LLMs to speed up clinical routing.

---

## 1. The Data Model: Patients & Clinical Data

Model your core patient information with built-in privacy markers.

```chronos
namespace health.triage

/// A patient's core medical record.
@compliance("HIPAA")
entity Patient {
    @required
    id: String
    
    @pii(type: "NAME")
    fullName: String
    
    @pii(type: "DOB")
    dateOfBirth: Timestamp
    
    @pii(type: "CONTACT")
    phoneNumber: String
    
    status: PatientStatus
    activeCondition: Condition
}

/// A specific medical condition being triaged.
shape Condition {
    @required
    id: String
    description: String
    severity: TriageUrgency
}

enum PatientStatus {
    ACTIVE    = 1
    DISCHARGED = 2
    PENDING   = 3
}
```

---

## 2. Governance: PHI Protection & AI Confidence

In Healthcare, the biggest risk is **Data Leakage** to an AI model. Use `deny` and `traits` to set hard boundaries.

```chronos
/// HIPAA-compliant policy for data handling.
@compliance("HIPAA")
policy DataPrivacy {
    description: "PHI must be encrypted at rest and never logged in plain text."
}

/// Explicit Prohibition: No PHI to the LLM.
deny PHIDataLeakage {
    description: "The system must never send PII or PHI to a third-party LLM provider."
    scope: [Patient]
    severity: critical
}

/// AI Confidence Trait (Custom Metadata)
@trait(name: "confidence", target: "95%")
enum TriageUrgency {
    LOW      = 1
    MEDIUM   = 2
    HIGH     = 3
    CRITICAL = 4
}
```

---

## 3. The Flow: An AI-Assisted Triage Journey

Model the interplay between the Patient, the AI Model, and the clinical Staff.

```chronos
/// The main patient experience for AI-assisted symptom triage.
journey AIPatientTriage {
    actor: Patient
    
    preconditions: [
        "Patient has consented to AI-assisted triage",
        "System has no active emergency alerts"
    ]
    
    steps: [
        step SymptomIntake {
            action: "Patient describes symptoms in natural language"
            expectation: "AI extracts clinical indicators without capturing PII"
            telemetry: [SymptomCaptured]
        },
        step AIRouting {
            @confidence(target: "98%")
            action: "AI suggests a triage level (e.g., CRITICAL)"
            expectation: "System routes the case to a Human Physician immediately"
            outcome: TransitionTo(URGENT_REVIEW)
        },
        step DoctorReview {
            action: "Physician reviews AI's suggestion and patient history"
            expectation: "Physician confirms or overrides the triage level"
            outcome: TransitionTo(FINAL_DISPOSITION)
            telemetry: [ClinicianDecisionMade]
        }
    ]
    
    variants: {
        AIUncertainty: {
            trigger: AIConfidenceLowError
            steps: [
                step NurseOverride {
                    action: "System flags the case for an RN for manual triage"
                    expectation: "Nurse takes over the intake process"
                    outcome: ReturnToStep(SymptomIntake)
                }
            ]
        }
    }
}
```

---

## 4. State Management: The Patient's Triage Lifecycle

Visualize how a patient moves through the triage process.

```chronos
statemachine TriageLifecycle {
    entity: Patient
    field: triageStatus
    
    states: [NEW, IN_TAKE, AI_PENDING, URGENT_REVIEW, FINAL_DISPOSITION, CANCELLED]
    initial: NEW
    terminal: [FINAL_DISPOSITION, CANCELLED]
    
    transitions: [
        NEW -> IN_TAKE {
            action: "Assign a triage session ID"
        },
        IN_TAKE -> AI_PENDING {
            action: "Invoke AI Triage Model"
        },
        AI_PENDING -> URGENT_REVIEW {
            guard: "AI Level == CRITICAL OR AI Level == HIGH"
        },
        AI_PENDING -> FINAL_DISPOSITION {
            guard: "AI Level == LOW AND Physician approved"
        }
    ]
}
```

---

### **TPM Insights: Why this works**

- **Safe AI Integration:** You've documented exactly *where* the AI is used (`step AIRouting`) and *what* it is forbidden from seeing (`deny PHIDataLeakage`).
- **Clinician-in-the-Loop:** The model explicitly requires a `step DoctorReview`, ensuring the AI is a co-pilot, not the final decision-maker.
- **Audit Trails:** Every `telemetry` tag generates a structured event log, which is critical for healthcare auditing and clinical safety reviews.
- **HIPAA Compliance:** The `@compliance("HIPAA")` markers ensure that any generated PRD or Jira ticket clearly labels the required security controls for the engineering team.
