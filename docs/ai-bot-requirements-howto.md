# How-To: Use Chronos with an AI Coding Bot to Generate Requirements

Practical onboarding guide for PMs, requirements writers, and engineers  
Version: February 2026

## What This Guide Does

This guide shows how to use an AI coding bot to produce high-quality Chronos requirements with a repeatable loop:

1. Give the bot clear language/grammar constraints.
2. Ask it to generate multi-file Chronos models from a product brief.
3. Compile and validate.
4. Feed diagnostics back to the bot for correction.
5. Generate outputs for review and implementation.

## Prerequisites

- Chronos repository and CLI available in your workspace.
- Familiarity with basic Chronos constructs (`namespace`, `use`, `entity`, `actor`, `journey`).
- A short feature brief with goals, users, constraints, and known systems.

Helpful references:

- Grammar: `/Users/scott/Developer/projects/ai/holodeck/chronos/chronos-parser/src/main/antlr/com/genairus/chronos/parser/Chronos.g4`
- Quick reference: `/Users/scott/Developer/projects/ai/holodeck/chronos/docs/quick-reference.md`
- Example models: `/Users/scott/Developer/projects/ai/holodeck/chronos/examples`

## Fast Start Workflow

1. Start with a bounded context and target namespace.
2. Ask the bot to generate 2-4 files:
- `domain.chronos`
- `actors-policies.chronos`
- `journeys.chronos`
- `errors-and-denies.chronos` (optional)
3. Validate/compile:

```bash
chronos prd /path/to/model-dir --name draft --out /tmp/chronos-draft
```

4. If diagnostics appear, paste them back to the bot and ask for targeted fixes only.
5. Repeat until compilation succeeds.
6. Generate final artifacts with `chronos prd` and/or `chronos build`.

## Copy-Paste Instructions for the LLM (System Prompt)

Use this as the instruction preamble when asking a coding bot to author Chronos files.

```text
You are generating Chronos requirements files (.chronos) only.

Authoring contract:
1) Follow Chronos grammar strictly.
2) Use one namespace per file and explicit `use ns#Shape` imports for cross-namespace references.
3) Prefer multi-file output: domain, journeys, actors/policies, errors/denies, statemachine.
4) Use doc comments (`///`) above major declarations.
5) Keep names consistent and avoid duplicate shape names in a namespace.
6) When unsure, ask clarifying questions before writing code.

Required semantic constraints:
- Every `journey` must have `actor:` and `outcomes:` containing at least `success`.
- Every `step` must include both `action:` and `expectation:`.
- Variant `trigger:` must reference a defined/imported `error` type.
- Invariant severity must be one of: `error`, `warning`, `info`.
- Deny and Error severity must be one of: `critical`, `high`, `medium`, `low`.
- State machine rules:
  - `initial` must be in `states`
  - transition states must be declared in `states`
  - non-terminal states need outbound transitions
  - terminal states must not have outbound transitions.

Output contract:
- First, list files you will create.
- Then provide each file in a separate fenced block with the file path.
- Do not include prose outside short comments unless asked.
- After writing files, provide a brief self-check against the required semantic constraints.
```

## Prompt Pattern That Produces Good Results

Use this template for most feature work.

```text
Create Chronos requirements for this feature.

Feature brief:
- Problem:
- Primary user/actor:
- Business outcome and KPI:
- In-scope behaviors:
- Out-of-scope:
- Compliance/security requirements:
- Existing systems/APIs involved:
- Known failure scenarios:

Authoring requirements:
- Generate files under: <path>
- Namespace root: <namespace>
- Reuse/import existing shapes from: <library namespaces>
- Include at minimum:
  - 2+ entities or shapes
  - 1+ actor
  - 1+ policy (if applicable)
  - 1+ journey with variants
  - typed errors for major failures
  - deny rules for prohibited behavior
  - optional state machine if lifecycle is explicit
- Make invariants realistic and testable.
- Keep expressions simple and unambiguous.

Quality bar:
- Must compile with Chronos without errors.
- If assumptions are needed, state them at top of each file as `/// Assumption: ...`.
```

## Example Prompts You Can Use

### 1) Bootstrap a New Feature

```text
Using the Chronos authoring contract, generate a multi-file model for "Subscription Pause and Resume" in namespace `com.acme.subscriptions`.
Users: Subscriber, SupportAgent.
Must include: pause eligibility rules, billing impact, resume conditions, denial of pause for fraudulent accounts, and typed errors for payment failures.
Create files under `/Users/scott/Developer/projects/ai/holodeck/chronos/examples/new/subscriptions`.
```

### 2) Add Compliance and Security Libraries

```text
Refactor the generated files to import and reuse these policy libraries where relevant:
- `com.acme.security#PiiAccessPolicy`
- `com.acme.compliance#DataRetentionPolicy`
- `com.acme.audit#AuditEvent`
Do not duplicate controls that are already represented in imported policies.
```

### 3) Add Failure Modeling Quality

```text
Improve failure completeness:
- Add explicit `error` types for each journey variant trigger.
- Ensure each variant has trigger, recovery path, and concrete expectations.
- Add at least one `deny` rule preventing data or authorization misuse.
Return only diffs by file.
```

### 4) Add a State Machine

```text
Add a `statemachine` for entity `Subscription` on field `status`.
States should include: ACTIVE, PAUSED, RESUME_SCHEDULED, CANCELLED.
Ensure transitions satisfy Chronos terminal and outbound-transition rules.
```

### 5) Fix Compiler Diagnostics

```text
Apply only the minimum edits needed to fix these Chronos diagnostics.
Do not redesign names or structure unless required.

<paste diagnostic output here>
```

### 6) Tighten for LLM Code Generation

```text
Optimize these Chronos requirements for downstream code generation:
- remove vague wording
- ensure every step has deterministic expected outcomes
- make invariants explicit and implementation-testable
- ensure error payloads include fields needed for retries and observability
Return updated .chronos files only.
```

## Diagnostic-Driven Correction Loop

Use this loop every time:

1. Generate or update Chronos files with the bot.
2. Compile with:

```bash
chronos prd /path/to/model-dir --name draft --out /tmp/chronos-draft
```

3. Paste diagnostics into a correction prompt.
4. Ask for minimum-change fixes.
5. Re-run compile.
6. Stop only when errors are zero.

## Suggestions to Make This Useful for New Users

1. Ship a starter template folder with 4 files and placeholder sections.
2. Include a reusable "LLM instruction preamble" in your team docs.
3. Maintain a small curated library of canonical patterns:
- auth and role policy
- PII handling
- audit events
- retry/idempotency errors
- common journey step patterns
4. Enforce a naming convention from day one:
- `namespace`: `com.org.domain.context`
- error names end with `Error`
- journeys use verb phrases
- invariants use descriptive rule names
5. Add CI that runs Chronos compile on every PR touching `.chronos`.
6. Keep a "diagnostic cookbook" mapping CHR codes to fixes.
7. Make first-run experience short:
- one command to validate
- one command to generate a readable PRD.

## Common Mistakes to Prevent in Prompts

- Asking for prose-heavy output without file-level Chronos code.
- Not requiring variant triggers to map to typed errors.
- Letting the model invent severity values for invariants.
- Omitting `outcomes.success` in journeys.
- Mixing multiple unrelated bounded contexts in one namespace.

## Questions to Customize This Guide for Your Team

1. Which AI bot(s) are you standardizing on (Codex, Cursor, Claude Code, etc.)?
2. Do you want generated output as single-file first, or multi-file by default?
3. Which governance libraries are mandatory on every feature (security/compliance/privacy)?
4. Do you want the bot to fail fast with questions first, or draft immediately with assumptions?
5. Should generated requirements target human PRD readability first, or LLM codegen precision first?

## Recommended Next Step

Pilot this workflow on one medium-complexity feature and track:

- time to first valid compile
- number of correction loops
- requirement clarification requests from engineers
- defects traced to requirement ambiguity.

Use those metrics to tune your prompt templates and library strategy before scaling org-wide.
