# Chronos

Chronos is a requirements language and compiler for building machine-readable product and system requirements.

Instead of requirements living only in prose docs, Chronos gives you a typed, validated source format that can generate PRDs, backlog artifacts, type definitions, diagrams, and test scaffolding.

## Start Here: Chronos + LLM Requirements Authoring

If your goal is AI-assisted requirements creation, start with these two documents first:

1. [How-To: Use Chronos with an AI Coding Bot to Generate Requirements](docs/ai-bot-requirements-howto.md)  
Practical, copy-paste workflow for getting from feature brief to validated multi-file `.chronos` requirements, including prompt templates, correction loops, and quality checks.
2. [Chronos Vision: AI-Assisted Requirements as a System](Documentation/Chronos-AI-Assisted-Requirements-Vision.md)  
End-state operating model for how LLMs, reusable requirement libraries, and governance combine so PMs focus on decisions while engineering receives implementation-ready, machine-readable requirements.

Leadership summary: [Chronos Executive One-Pager](Documentation/Chronos-AI-Requirements-Executive-OnePager.md)

## Quick Links

- [Documentation Index](docs/index.md)
- [Install](docs/install.md)
- [CLI Reference](docs/cli.md)
- [Language Quick Reference](docs/quick-reference.md)
- [Examples](examples/README.md)
- [LLM Requirements How-To (Hands-On)](docs/ai-bot-requirements-howto.md)
- [AI-Assisted Requirements Vision (System Design)](Documentation/Chronos-AI-Assisted-Requirements-Vision.md)
- [Executive One-Pager](Documentation/Chronos-AI-Requirements-Executive-OnePager.md)
- [Feature Roadmap Recommendations](Documentation/Chronos-Feature-Priority-Order.md)

## Why Chronos

Chronos is designed for teams that want a single source of truth for requirements that works for humans, compilers, and AI coding agents.

- Structured requirements instead of ambiguous prose
- Multi-file composition with namespace/import resolution
- Semantic validation with CHR diagnostics
- Artifact generation for product, engineering, and QA workflows
- Better downstream AI code generation due to explicit constraints

## Core Capabilities

- Language constructs for:
  - `entity`, `shape`, `enum`, `list`, `map`
  - `actor`, `policy`, `journey`
  - `relationship`, `invariant`, `deny`, `error`, `statemachine`
- Compiler pipeline:
  - parse/lower
  - symbol collection
  - type and cross-link resolution
  - validation
  - finalization
- Generators:
  - `prd` / `markdown`
  - `jira`
  - `typescript`
  - `mermaid-state`
  - `test-scaffold`
  - `statemachine-tests`

## Quick Start

### 1) Install

Use one of:

- package manager or release binaries: [docs/install.md](docs/install.md)
- run from source (JDK 21):

```bash
./gradlew :chronos-cli:run --args="--help"
```

### 2) Write a minimal model

Create `checkout.chronos`:

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

### 3) Validate and generate

```bash
# Validate a single file
chronos validate checkout.chronos --verbose

# Generate a combined PRD from a file or directory
chronos prd checkout.chronos --out /tmp/chronos --name checkout-prd

# Generate a specific artifact from one file
chronos generate checkout.chronos --target typescript --output /tmp/chronos
```

## CLI At a Glance

| Command | Purpose |
|---|---|
| `chronos validate <file>` | Parse + validate one model file |
| `chronos prd <file-or-dir>` | Compile and generate Markdown PRD |
| `chronos generate <file> --target <name>` | Generate one target artifact from one file |
| `chronos build` | Build from `chronos-build.json` config |

Full details: [docs/cli.md](docs/cli.md)

## Build Configuration Example

`chronos build` uses `chronos-build.json` in the current directory by default.

```json
{
  "sources": ["examples/ecommerce/**/*.chronos"],
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
    }
  ]
}
```

Then run:

```bash
chronos build
```

## Repository Layout

| Path | Purpose |
|---|---|
| `chronos-core/` | Shared diagnostics and reference types |
| `chronos-ir/` | Canonical IR model |
| `chronos-parser/` | ANTLR grammar and syntax lowering |
| `chronos-validator/` | Semantic rule enforcement |
| `chronos-compiler/` | Multi-pass compilation pipeline |
| `chronos-generators/` | Artifact generators |
| `chronos-artifacts/` | IR artifact emission |
| `chronos-cli/` | CLI entry points |
| `examples/` | Runnable language examples |
| `docs/` | User/developer documentation |
| `Documentation/` | Strategy, roadmap, and planning documents |

## Development Workflow

Requirements:

- JDK 21
- Gradle wrapper (`./gradlew`)

Run tests:

```bash
./gradlew test
```

Run full verification (recommended before pushing):

```bash
./gradlew check
```

`check` also runs architectural boundary audits:

- `verifyArchBoundaries`
- `verifyNoLegacyImports`

These help prevent module dependency drift and forbidden imports.

## Documentation Map

- Start here: [docs/index.md](docs/index.md)
- Install guide: [docs/install.md](docs/install.md)
- Language syntax: [docs/quick-reference.md](docs/quick-reference.md)
- Generator details: [docs/generators.md](docs/generators.md)
- Examples walkthroughs: [docs/examples/getting-started.md](docs/examples/getting-started.md)

## Project Status

Chronos is actively evolving. Planned language extensions and rollout recommendations live in:

- [Documentation/Chronos-Evolution-Plan.md](Documentation/Chronos-Evolution-Plan.md)
- [Documentation/Chronos-Feature-Priority-Order.md](Documentation/Chronos-Feature-Priority-Order.md)

## License

[Apache-2.0](LICENSE)
