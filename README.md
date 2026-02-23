# Chronos

**Turn a feature brief into validated, machine-readable requirements — in minutes.**

Write `.chronos` files. Get back Markdown PRDs, Jira-ready backlogs, TypeScript type definitions, state diagrams, and test scaffolding — all generated from a single source of truth your whole team can version-control and validate.

```chronos
namespace shop.checkout

@description("A customer using checkout")
actor Customer

enum OrderStatus {
    PENDING = 1
    PAID = 2
}

entity Order {
    @required
    id: String
    status: OrderStatus
}

@kpi(metric: "checkout_completion_rate", target: "95%")
journey Checkout {
    actor: Customer
    steps: [
        step ReviewCart {
            action: "Customer reviews cart"
            expectation: "Cart totals are correct"
        },
        step Pay {
            action: "Customer submits payment"
            expectation: "Payment is authorized"
            outcome: TransitionTo(PAID)
        }
    ]
    outcomes: {
        success: "Order paid and confirmation sent",
        failure: "No charge is made and cart is preserved"
    }
}
```

```sh
chronos prd checkout.chronos           # → Markdown PRD
chronos generate checkout.chronos --target jira       # → Jira-importable CSV
chronos generate checkout.chronos --target typescript # → TypeScript .d.ts
```

---

## Install

Chronos is a self-contained native binary — no JRE or runtime required.

### macOS / Linux

```sh
brew install Genairus/tap/chronos
```

### Windows

```powershell
scoop bucket add chronos https://github.com/Genairus/scoop-chronos
scoop install chronos
```

### Verify

```sh
chronos --version
# chronos 0.1.0
```

For direct downloads, ARM64, and other options see the [full install guide](docs/install.md).

---

## Quick Start

### 1. Write a model

Create a file called `checkout.chronos` with the example above (or copy one from `examples/getting-started/`).

### 2. Validate

```sh
chronos validate checkout.chronos --verbose
```

The compiler checks 50+ rules — missing fields, unresolved references, invalid state machines, broken invariants — and tells you exactly what to fix.

### 3. Generate artifacts

```sh
# Markdown PRD
chronos prd checkout.chronos --output ./generated --name checkout

# Jira backlog CSV
chronos generate checkout.chronos --target jira --output ./generated

# TypeScript types
chronos generate checkout.chronos --target typescript --output ./generated
```

### 4. Scale to multiple files

Split your model across files by concern. Chronos resolves cross-file references automatically:

```
my-project/
├── domain.chronos          # entities, shapes, enums, relationships
├── actors-policies.chronos # actors, policies
├── errors.chronos          # error types
├── events.chronos          # telemetry event types
├── statemachines.chronos   # lifecycle definitions
└── journeys.chronos        # journeys with variants
```

```sh
chronos prd my-project/ --output ./generated --name my-feature
```

---

## New User Journey (Brew -> AI Agent -> PRD -> Jira)

Follow this path exactly if this is your first time with Chronos.

### 1. Install Chronos with Homebrew

```sh
brew install Genairus/tap/chronos
chronos --version
```

### 2. Set up your coding agent (Claude, ChatGPT, Cursor, Copilot)

Use the dedicated setup guide:

**[AI Agent Setup (Claude + Chronos)](docs/ai-agent-setup.md)**

That page gives you:
- a copy-paste `CLAUDE.md` starter
- step-by-step instructions to teach your agent Chronos
- prompt templates that produce higher-quality requirements

### 3. Generate Chronos requirements with your agent

If you are using Claude Code, do this once per repository:

1. Create `CLAUDE.md` in the repo root.
2. Paste the starter from [docs/ai-agent-setup.md](docs/ai-agent-setup.md).
3. Open Claude in this repo and use this prompt:

```text
Create Chronos requirements for feature "Checkout Address Validation".

Feature:
- Problem: Invalid addresses cause failed shipments
- Primary actor: Customer
- Business outcome: Reduce failed shipment rate by 30%
- In scope: capture address, validate format, block invalid submit, return actionable error
- Out of scope: carrier-specific normalization
- Compliance/security constraints: do not log raw PII in errors
- Known failure cases: invalid zip, missing house number

Authoring constraints:
- Generate one file at: requirements/checkout/checkout.chronos
- Namespace: com.example.checkout
- Include: 2+ entities/shapes, 1 actor, 1 journey with 3+ steps, 1 variant with typed error trigger, 1 invariant, 1 deny rule
```

Ask the agent for a first model (single file for easiest first run), then save it as:

`requirements/checkout/checkout.chronos`

Validate it:

```sh
chronos validate requirements/checkout/checkout.chronos --verbose
```

If you see errors, paste diagnostics back to your agent with:

```text
Apply only the minimum edits required to fix these Chronos diagnostics.
Do not rename or redesign unless required by an error.

<paste diagnostics>
```

### 4. Generate a PRD

```sh
chronos prd requirements/checkout/checkout.chronos --output ./generated --name checkout-prd
```

Output:
- `generated/checkout-prd.md`

### 5. Generate Jira epics and stories

`chronos generate` currently accepts a single `.chronos` file input, so keep your first iteration in one file.

```sh
chronos generate requirements/checkout/checkout.chronos --target jira --output ./generated
```

Output:
- `generated/<namespace>-backlog.csv` (for example: `generated/com-example-checkout-backlog.csv`)

In Jira, import this CSV from your project board using the CSV import flow, then map fields:
- `Summary`
- `Issue Type` (Epic/Story)
- `Description`
- `Priority`
- `Labels`
- `Epic Name`
- `Epic Link`
- `Story Points`

---

## What Can Chronos Generate?

| Command | Output | Use Case |
|---------|--------|----------|
| `chronos prd` | Markdown PRD | Share with stakeholders, review in GitHub |
| `--target jira` | CSV backlog | Import directly into Jira (Epics + Stories) |
| `--target typescript` | `.d.ts` types | Drop into your frontend/backend codebase |
| `--target mermaid-state` | Mermaid diagrams | Visualize state machines in docs or PRs |
| `--target test-scaffold` | JUnit tests | Scaffold invariant test cases |
| `--target statemachine-tests` | JUnit tests | Scaffold state transition test cases |

---

## Use with AI Coding Assistants

Chronos is designed to work with AI coding bots. Feed the bot a feature brief, and it generates `.chronos` files. The compiler catches mistakes instantly.

**Claude Code, ChatGPT, Copilot, Cursor — any LLM works.** See the how-to guide for:

- A copy-pasteable language spec you can drop into any LLM's system prompt
- A ready-to-use `CLAUDE.md` template that makes Claude Code an instant Chronos expert
- Prompt templates, compile-fix loops, and team conventions

👉 **[AI Agent Setup (Claude + Chronos)](docs/ai-agent-setup.md)**

👉 **[How-To: Write Requirements with Your AI Coding Bot](docs/ai-bot-requirements-howto.md)**

---

## The Language at a Glance

Chronos has **15 shape types** for modeling your domain:

| Shape | Purpose | Example |
|-------|---------|---------|
| `entity` | Domain object with identity | `entity Order { id: String }` |
| `shape` | Value object (no identity) | `shape Money { amount: Float }` |
| `enum` | Named constants | `enum Status { PENDING  PAID }` |
| `list` | Typed collection | `list Items { member: CartItem }` |
| `map` | Key-value collection | `map Tags { key: String  value: String }` |
| `actor` | Journey participant | `actor Customer` |
| `policy` | Governance rule | `policy GDPR { description: "..." }` |
| `journey` | End-to-end user flow | Steps, variants, outcomes |
| `relationship` | Association between entities | `from: A  to: B  cardinality: one_to_many` |
| `invariant` | Business rule with expression | `expression: "total > 0"` |
| `deny` | Prohibition / compliance constraint | `deny NoPIIInLogs { ... }` |
| `error` | Typed failure case | `error PayDeclined { code: "PAY-001" }` |
| `statemachine` | Entity field lifecycle | States, transitions, guards |
| `role` | Authorization role | `allow: [read]  deny: [delete]` |
| `event` | Telemetry / domain event | `event CartViewed {}` |

Plus: **traits** (`@required`, `@kpi(...)`, `@compliance("GDPR")`), **doc comments** (`///`), **namespaces** and **imports** (`use ns#Shape`), and an **invariant expression language** with aggregates and lambdas.

👉 **[Full Language Reference](docs/quick-reference.md)**

---

## CLI Reference

| Command | Purpose |
|---------|---------|
| `chronos validate <file>` | Parse and validate a model file |
| `chronos prd <file-or-dir>` | Compile and generate a Markdown PRD |
| `chronos generate <file> --target <name>` | Generate a specific artifact |
| `chronos build` | Build from `chronos-build.json` config |
| `chronos select <file> --filter <pattern>` | Query shapes from a compiled model |
| `chronos diff <file1> <file2>` | Diff two compiled models |

👉 **[Full CLI Reference](docs/cli.md)**

---

## Build Configuration

For repeatable builds, create a `chronos-build.json`:

```json
{
  "sources": ["requirements/**/*.chronos"],
  "targets": [
    {
      "name": "prd-docs",
      "generator": "prd",
      "output": "generated/prd"
    },
    {
      "name": "types",
      "generator": "typescript",
      "output": "generated/types"
    },
    {
      "name": "backlog",
      "generator": "jira",
      "output": "generated/jira"
    }
  ]
}
```

```sh
chronos build
```

---

## Examples

The `examples/` directory has runnable models you can compile immediately:

| Example | What It Shows |
|---------|---------------|
| [`getting-started/`](examples/README.md#1-getting-started-getting-started) | Two-file intro — entities, actors, journeys, imports |
| [`ecommerce/`](examples/README.md#2-e-commerce-ecommerce) | Production-scale multi-namespace checkout system |
| [`backlog-demo/`](examples/README.md) | Hiring domain with Jira backlog generation |
| [Single-file showcases](examples/README.md#3-single-feature-showcases-top-level-files) | One file per language feature (deny, error, invariant, statemachine) |

```sh
# Try the getting-started example right now
chronos prd examples/getting-started/ --output /tmp/chronos-demo
```

👉 **[All Examples](examples/README.md)**

---

## Why Chronos?

| Without Chronos | With Chronos |
|-----------------|--------------|
| Requirements live in Confluence (stale within a week) | `.chronos` files are the versioned source of truth |
| Jira tickets created manually from meetings | `chronos generate --target jira` creates the backlog |
| Developers write their own TypeScript types | `--target typescript` emits `.d.ts` matching the spec |
| AI bots get inconsistent, unstructured context | Feed the compiled model directly to your coding assistant |
| PRDs drift from the implementation | PRD is regenerated on every CI run |

---

## Learn More

- [Documentation Index](docs/index.md) — full docs hub
- [Language Quick Reference](docs/quick-reference.md) — every shape type and trait
- [AI Bot How-To](docs/ai-bot-requirements-howto.md) — teach your AI assistant to write Chronos
- [Generators](docs/generators.md) — all output targets and what they produce
- [Examples](examples/README.md) — runnable models to learn from
- [AI-Assisted Requirements Vision](Documentation/Chronos-AI-Assisted-Requirements-Vision.md) — the operating model
- [Executive One-Pager](Documentation/Chronos-AI-Requirements-Executive-OnePager.md) — leadership summary

---

## Contributing

Want to extend the language, add a generator, or fix a bug?

👉 **[Contributing Guide](docs/contributing.md)** — source build instructions, module layout, ANTLR grammar, test suite, and how to add new shape types and generators.

---

## License

[Apache-2.0](LICENSE)
