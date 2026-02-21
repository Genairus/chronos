# Generators

Chronos compiles a model and then passes the resulting IR to a generator, which produces output files.

## Usage

```sh
# Single-file generation
chronos generate --target <name> <file.chronos> [--out <dir>]

# Directory (multi-file, all .chronos files compiled together)
chronos generate --target <name> <directory/> [--out <dir>]
```

The `prd` target is also available via the `chronos prd` shortcut command.

---

## Targets

### `prd` / `markdown`

Produces a Markdown Product Requirements Document.

```sh
chronos prd examples/ecommerce/ --name ecommerce-prd --out /tmp/out/
# → /tmp/out/ecommerce-prd.md
```

**Output includes:**
- Table of contents with anchor links
- Each journey with actor, preconditions, steps (with telemetry/risk/outcomes), variants, and outcomes
- Data model (entities, shapes, enums, collections)
- Actors, policies, invariants, deny blocks, state machines, error types

---

### `jira`

Produces a Jira-importable CSV file (RFC 4180).

```sh
chronos generate --target jira checkout.chronos --out /tmp/
# → /tmp/com.example.checkout-backlog.csv
```

**Column mapping:**

| Column | Journey (Epic) | Step (Story) | Variant (Story) | Policy (Story) | Deny (Story) |
|--------|---------------|--------------|-----------------|----------------|--------------|
| Summary | Journey name | `Step: action` | `[Variant] error path: trigger` | `[Policy] Name` | `[Compliance] Name` |
| Issue Type | Epic | Story | Story | Story | Story |
| Priority | Medium | Medium | **High** | Medium / High† | Medium / High / **Highest**‡ |
| Labels | namespace | namespace | `namespace variant` | `namespace compliance` | `namespace compliance` |
| Epic Link | — | Parent journey | Parent journey | — | — |
| Story Points | Step count | 1 | Variant step count | 0 | 0 |

† High if `@compliance` trait present. ‡ Based on `severity`: `critical` → Highest, `high` → High.

---

### `typescript`

Produces TypeScript type definitions (`.d.ts`).

```sh
chronos generate --target typescript checkout.chronos
```

**Maps:**
- `entity`, `shape` → `interface`
- `enum` → `const enum`
- `list` → typed array alias
- `map` → `Record<K, V>` alias

---

### `mermaid-state`

Produces a Mermaid state diagram for each `statemachine` in the model.

```sh
chronos generate --target mermaid-state checkout.chronos
```

---

### `test-scaffold`

Produces JUnit 5 test scaffolding for `invariant` definitions.

```sh
chronos generate --target test-scaffold checkout.chronos
```

---

### `statemachine-tests`

Produces JUnit 5 test scaffolding for `statemachine` transition rules.

```sh
chronos generate --target statemachine-tests checkout.chronos
```

---

## Output file naming

Single-file compilation: output filename is derived from the namespace — dots replaced with hyphens.

```
namespace com.example.checkout  →  com-example-checkout-prd.md
                                   com-example-checkout-backlog.csv
```

Multi-file compilation with `--name`: use the `--name` flag to set the output filename.

```sh
chronos prd examples/ecommerce/ --name ecommerce-prd
# → ecommerce-prd.md (combined from all namespaces)
```
