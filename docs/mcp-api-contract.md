# Chronos MCP API Contract

Status: Implemented
Owner: Chronos Platform
Last updated: 2026-02-23
Schema version: `1.0`

---

## Overview

The `chronos-mcp` module provides a 9-tool MCP server for AI-assisted Chronos authoring.
It replaces the "paste language spec + run CLI + copy-paste diagnostics" workflow with
on-demand compiler-in-the-loop tool calls, so agents never guess at syntax.

**Key design principles:**

- The compiler is always in the loop — tools validate before generating
- Partial results are safe — `list_symbols` returns what compiled even if finalization failed
- No LLM-generated fix suggestions — the agent writes fixes, tools validate them
- All paths in all responses are **absolute** — no ambiguity about file locations
- `diagnostics[]` is always present (never absent), even when empty

---

## 1. Response Envelope Contract

Every tool response is a JSON object with one of two shapes. Clients **MUST** check for `"error"` before reading `"result"`.

### 1.1 Success envelope

```json
{
  "schemaVersion": "1.0",
  "toolVersion": "chronos.validate@1.0",
  "result": { ... }
}
```

### 1.2 Error envelope

```json
{
  "schemaVersion": "1.0",
  "toolVersion": "chronos.validate@1.0",
  "error": {
    "code": "INVALID_INPUT",
    "message": "inputPaths is required and must not be empty",
    "retryable": false
  }
}
```

**Error codes:**

| Code | Meaning |
|------|---------|
| `INVALID_INPUT` | Bad or missing argument — fix the request |
| `PATH_OUTSIDE_WORKSPACE` | Path escapes the workspace root — security violation |
| `COMPILE_ERROR` | Compilation produced one or more errors (parse or semantic/type) |
| `INTERNAL_ERROR` | Unexpected server error (usually retryable; non-retryable if partial writes occurred) |

### 1.3 Envelope fields

| Field | Description |
|-------|-------------|
| `schemaVersion` | Contract version. Clients should reject unknown values. Currently `"1.0"`. |
| `toolVersion` | Per-tool version string `"<toolName>@<semver>"`. |
| `diagnosticSort` | Included in every `result` with a `diagnostics[]` array. Value: `"path,line,col,code"`. |

### 1.4 Path conventions

- Every field named `sourcePath`, `path`, `bundlePath`, `outputPath`, or `writtenFiles[n]`
  in any tool response is an **absolute path** (resolved via `toAbsolutePath().normalize()`).
- Relative paths are never returned.

### 1.5 Security gate

All tools reject any caller-supplied path that, after normalization, does not start with
the resolved `workspaceRoot`. Returns `PATH_OUTSIDE_WORKSPACE` error, `retryable: false`.
Applies to every path argument in every tool.

---

## 2. Tools (9 total)

### 2.1 `chronos.health`

**Purpose:** Confirm server is reachable; enumerate available tools; check versions.
**Call this first** when bootstrapping an agent session.

**Input:** No required fields.

**Response result:**

```json
{
  "status": "ok",
  "apiVersion": "1.0",
  "compilerVersion": "0.1.0",
  "serverVersion": "0.1.0",
  "tools": [
    "chronos.validate",
    "chronos.explain_diagnostic",
    "chronos.describe_shape",
    "chronos.generate",
    "chronos.emit_ir_bundle",
    "chronos.scaffold",
    "chronos.list_symbols",
    "chronos.discover",
    "chronos.health"
  ]
}
```

---

### 2.2 `chronos.discover`

**Purpose:** Find all `.chronos` files in the workspace without compiling them.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `workspaceRoot` | string | no | Defaults to `CHRONOS_WORKSPACE` env var or CWD |
| `maxDepth` | integer | no | Max directory depth to walk (default: 10) |

**Response result:**

```json
{
  "files": [
    {"path": "/abs/path/to/order.chronos", "namespace": "com.example.domain", "sizeBytes": 1420},
    {"path": "/abs/path/to/other.chronos", "namespace": null, "sizeBytes": 203}
  ]
}
```

`namespace` is `null` when not found within the first 100 lines.

---

### 2.3 `chronos.validate`

**Purpose:** Run the full 7-phase compiler pipeline and return all diagnostics.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `inputPaths` | string[] | yes | Absolute paths to `.chronos` files |
| `workspaceRoot` | string | no | Defaults to `CHRONOS_WORKSPACE` env var or CWD |

**Response result:**

```json
{
  "diagnosticSort": "path,line,col,code",
  "errorCount": 2,
  "warningCount": 1,
  "parsed": true,
  "finalized": false,
  "diagnostics": [
    {
      "code": "CHR-001",
      "severity": "ERROR",
      "message": "Journey 'PlaceOrder' has no actor declaration",
      "sourcePath": "/abs/path/to/order.chronos",
      "span": {"sourceName": "order.chronos", "startLine": 5, "startCol": 1, "endLine": 5, "endCol": 10}
    }
  ]
}
```

`diagnostics` is always present (empty array `[]` when none).

---

### 2.4 `chronos.explain_diagnostic`

**Purpose:** Look up detailed explanation, causes, and fixes for a CHR diagnostic code.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `code` | string | yes | e.g. `"CHR-001"` or `"CHR-W001"` |

**Response result:**

```json
{
  "code": "CHR-001",
  "severity": "ERROR",
  "title": "Journey missing actor declaration",
  "description": "Every journey must declare exactly one actor.",
  "likelyCauses": ["Forgot to add actor: field", "Copy-paste from template without filling actor"],
  "fixes": ["Add actor: <ActorName> to the journey body"],
  "examples": [
    {
      "bad": "journey Checkout { steps: [...] }",
      "good": "journey Checkout { actor: Customer steps: [...] }"
    }
  ]
}
```

All 54 diagnostic codes (CHR-001 through CHR-053 + CHR-W001) are covered.

---

### 2.5 `chronos.describe_shape`

**Purpose:** Get human-readable documentation for any of the 15 Chronos shape types.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `shape` | string | yes | Shape type name (case-insensitive) |
| `includeExample` | boolean | no | Include examples and scaffold template (default: true) |

**Available shapes:** `entity`, `shape`, `list`, `map`, `enum`, `actor`, `policy`, `journey`,
`relationship`, `invariant`, `deny`, `error`, `statemachine`, `role`, `event`

**Response result:**

```json
{
  "shape": "entity",
  "description": "Domain object with identity — persisted and referenced by ID.",
  "compilable": true,
  "requiredFields": [{"name": "id", "type": "String", "notes": "Must be unique."}],
  "optionalFields": [...],
  "applicableRules": ["CHR-005", "CHR-008", "CHR-013"],
  "commonMistakes": ["Using 'description' as a field name — it is a contextual keyword"],
  "notes": [],
  "minimalExample": "entity Order {\n    id: String\n}\n",
  "fullExample": "...",
  "scaffoldTemplate": "entity {{Name}} {\n    @required\n    id: String\n    // add fields here\n}\n"
}
```

`compilable: false` means the scaffold alone requires user-supplied cross-references.

---

### 2.6 `chronos.scaffold`

**Purpose:** Generate a valid `.chronos` file stub for one or more shape types.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `namespace` | string | yes | Namespace for the generated file |
| `shapes` | string[] | yes | List of shape type names |
| `includeComments` | boolean | no | Include `//` comment hints (default: true) |

**Response result:**

```json
{
  "content": "namespace com.example.domain\n\nentity MyEntity {\n    @required\n    id: String\n    // add fields here\n}\n",
  "compilable": true,
  "notes": []
}
```

`compilable: true` means the `content` is expected to pass `chronos.validate` with zero errors.
`notes[]` lists expected warnings and any cross-reference stubs the agent must fill in.

---

### 2.7 `chronos.list_symbols`

**Purpose:** List all declared symbols from compiled `.chronos` files. Supports partial results.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `inputPaths` | string[] | yes | Absolute paths to `.chronos` files |
| `workspaceRoot` | string | no | Defaults to `CHRONOS_WORKSPACE` or CWD |
| `filterKind` | string | no | Filter by shape kind (e.g. `"entity"`, `"journey"`) |
| `filterNamespace` | string | no | Filter by exact namespace string |

**Three-state partial semantics:**

| Compiler state | `partial` | `symbols` | `diagnostics` |
|----------------|-----------|-----------|---------------|
| Parse failed | `true` | `[]` | present (parse errors) |
| Parsed, not finalized | `true` | symbols from resolved models | present (CHR-013 etc.) |
| Parsed and finalized | `false` | full symbol list | may include warnings |

**Response result:**

```json
{
  "partial": false,
  "diagnosticSort": "path,line,col,code",
  "symbols": [
    {"name": "Order", "kind": "entity", "namespace": "com.example", "sourcePath": "/abs/...", "fieldNames": ["id", "status"]},
    {"name": "PlaceOrder", "kind": "journey", "namespace": "com.example", "sourcePath": "/abs/...", "actorName": "Customer", "stepNames": ["AddItem", "Checkout"]},
    {"name": "OrderStatus", "kind": "enum", "namespace": "com.example", "sourcePath": "/abs/...", "memberNames": ["PENDING", "CONFIRMED"]}
  ],
  "diagnostics": []
}
```

Use `partial=true` with `diagnostics` to understand what prevented full resolution.
When `partial=false`, `diagnostics` can still contain warnings.

---

### 2.8 `chronos.generate`

**Purpose:** Generate artifacts (PRDs, Jira CSVs, TypeScript types, etc.) from validated source files.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `inputPaths` | string[] | yes | Absolute paths to `.chronos` files |
| `outDir` | string | yes | Absolute path to output directory |
| `target` | string | yes | Generator target (see below) |
| `workspaceRoot` | string | no | Defaults to `CHRONOS_WORKSPACE` or CWD |
| `dryRun` | boolean | no | Preview without writing files (default: false) |

**Known targets:** `markdown` / `prd`, `jira`, `test-scaffold`, `typescript`, `mermaid-state`, `statemachine-tests`

**Response result:**

```json
{
  "generated": true,
  "dryRun": false,
  "writtenFiles": ["/abs/path/to/com-example-prd.md"],
  "plannedFiles": [],
  "diagnosticSort": "path,line,col,code",
  "diagnostics": []
}
```

Rules:
- `generated: false` when any diagnostic has `severity: ERROR` (no files written)
- `writtenFiles` is always present (empty array when `generated: false`)
- `diagnostics` is always present (empty array when no diagnostics)
- `dryRun: true` → `writtenFiles: []`, `plannedFiles` shows what would have been written (populated only when compilation succeeds with no errors; empty array when there are compile errors)
- **Never call `chronos.generate` if `chronos.validate` returns `errorCount > 0`**

---

### 2.9 `chronos.emit_ir_bundle`

**Purpose:** Compile source files and write an `ir-bundle.json` for offline tooling.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `inputPaths` | string[] | yes | Absolute paths to `.chronos` files |
| `outDir` | string | yes | Absolute path to output directory |
| `workspaceRoot` | string | no | Defaults to `CHRONOS_WORKSPACE` or CWD |

**Response result:**

```json
{
  "bundlePath": "/abs/path/to/ir-bundle.json",
  "format": "chronos-ir-bundle",
  "version": "1",
  "modelCount": 3
}
```

`bundlePath` is always an absolute path. Returns `COMPILE_ERROR` if source files fail to parse.

---

## 3. Bootstrap Prompt

Use this as the system prompt when starting an agent session with Chronos MCP:

```text
You are authoring Chronos (.chronos) requirements files. Do not invent syntax.

Chronos files follow this structure:
  namespace <qualified.name>           // required, one per file
  use <namespace>#<ShapeName>          // import cross-namespace shapes
  // shape definitions in any order

The 15 shape types are: entity, shape, list, map, enum, actor, policy, journey,
relationship, invariant, deny, error, statemachine, role, event.

Critical rules (compiler-enforced):
- Every journey MUST have actor: and outcomes: { success: "..." }
- Every step MUST have action: and expectation:
- Variant trigger: must reference a declared error type
- Invariant severity: error | warning | info
- Deny/error severity: critical | high | medium | low  ← different from invariant
- Step telemetry events must be declared as event types
- Entity invariant expressions reference only direct fields of that entity
- Namespace segments must not be Chronos keywords (e.g. use "orders" not "journey")

Use MCP tools as your source of truth — do not guess:
0. chronos.health        — confirm server is reachable, get tool list and compiler version
1. chronos.discover      — find .chronos files in the workspace
2. chronos.scaffold      — get a valid starting template for shape types you need
3. chronos.list_symbols  — see what shapes exist to import
4. chronos.describe_shape — get docs and examples for any shape type
5. chronos.validate      — after EVERY file write or edit
6. chronos.explain_diagnostic — for any CHR error code you see
7. chronos.generate      — only after validation passes with zero errors
   (use dryRun: true first to preview planned output files)

Never call chronos.generate if chronos.validate returns errorCount > 0.
Keep edits minimal. Preserve user naming and structure unless diagnostics require changes.
```

---

## 4. Common Patterns

### Cold start (new feature)

```
chronos.health → chronos.discover → chronos.scaffold (entity, actor, journey)
→ write file → chronos.validate → fix errors → chronos.validate (clean)
→ chronos.generate (dryRun:true) → chronos.generate (dryRun:false)
```

### Adding a shape to existing model

```
chronos.list_symbols → chronos.describe_shape → edit file
→ chronos.validate → fix errors → chronos.generate
```

### Debugging a diagnostic

```
chronos.validate → see CHR-XXX → chronos.explain_diagnostic (code: "CHR-XXX")
→ apply fix → chronos.validate
```

---

## 5. Module Architecture

The `chronos-mcp` module has these dependencies:

```
chronos-mcp → chronos-compiler (compile pipeline)
           → chronos-generators (artifact generation)
           → chronos-artifacts (IR bundle emission)
           → chronos-ir (IR model types)
           → chronos-core (diagnostics, spans)
```

Dependency boundaries are enforced by `./gradlew check` (`verifyModuleBoundaries` task).

**Knowledge layer:**

- `ShapeKnowledge.java` — generated at build time from `src/main/resources/shape-overlays/*.yaml`
- `DiagnosticKnowledge.java` — hand-authored; coverage enforced by `DiagnosticKnowledgeCoverageTest`
- Adding a new CHR code → must add to `DiagnosticKnowledge` before build passes
- Adding a new shape type → must add YAML overlay before `ShapeKnowledgeCoverageTest` passes

---

## 6. MCP Server Configuration

### Running the server

```sh
# Build the MCP server JAR
./gradlew :chronos-mcp:build

# Run (stdio transport — JSON-RPC 2.0 over stdin/stdout)
java -jar chronos-mcp/build/libs/chronos-mcp-<version>.jar
```

### Claude Code settings.json example

```json
{
  "mcpServers": {
    "chronos": {
      "command": "java",
      "args": ["-jar", "/path/to/chronos-mcp.jar"],
      "env": {
        "CHRONOS_WORKSPACE": "/path/to/your/requirements/workspace"
      }
    }
  }
}
```

The `CHRONOS_WORKSPACE` environment variable sets the workspace root used by the path security gate.
All input file paths must be under this directory.
