# Chronos MCP API Contract (v1)

Status: Draft
Owner: Chronos Platform
Last updated: 2026-02-23

## 1. Goals

This contract defines a low-token, compiler-in-the-loop MCP interface for Chronos authoring.
It replaces the "paste a giant language spec" workflow with on-demand tool calls backed by source-of-truth artifacts:

- Grammar: `/Users/scott/Developer/projects/ai/holodeck/chronos/chronos-parser/src/main/antlr/com/genairus/chronos/parser/Chronos.g4`
- Validator rules: `/Users/scott/Developer/projects/ai/holodeck/chronos/chronos-validator/src/main/java/com/genairus/chronos/validator/ChronosValidator.java`
- Compiler pipeline: `/Users/scott/Developer/projects/ai/holodeck/chronos/chronos-compiler`
- Generators/CLI behavior: `/Users/scott/Developer/projects/ai/holodeck/chronos/chronos-cli`

## 2. Protocol Profile

- Protocol: MCP tools over JSON-RPC 2.0 (stdio transport for local use; HTTP optional later)
- Server name: `chronos-mcp`
- API version: `2026-02-23.v1`
- Content type for tool results: `application/json`
- All tool outputs return a single JSON object (no freeform prose)

## 3. Common Response Envelopes

All tools MUST return one of two shapes.

### 3.1 Success envelope

```json
{
  "ok": true,
  "data": {},
  "meta": {
    "apiVersion": "2026-02-23.v1",
    "durationMs": 42,
    "requestId": "0f2d1c6e-4e9a-4d66-9ff2-c4dbce7c5f53"
  }
}
```

### 3.2 Error envelope

```json
{
  "ok": false,
  "error": {
    "code": "CHRONOS_INPUT_NOT_FOUND",
    "message": "Input path does not exist",
    "details": {
      "path": "requirements/payments"
    },
    "retryable": false
  },
  "meta": {
    "apiVersion": "2026-02-23.v1",
    "durationMs": 3,
    "requestId": "6f212e50-7a79-4b00-b4ce-4d0f24ec8c41"
  }
}
```

## 4. Error Code Registry (server-level)

- `CHRONOS_INPUT_NOT_FOUND`
- `CHRONOS_INVALID_ARGUMENT`
- `CHRONOS_IO_ERROR`
- `CHRONOS_PARSE_FAILED`
- `CHRONOS_VALIDATION_FAILED`
- `CHRONOS_UNKNOWN_TARGET`
- `CHRONOS_BUNDLE_ERROR`
- `CHRONOS_INTERNAL_ERROR`

## 5. Tool Contracts (v1)

## 5.1 `chronos.validate`

Compile and validate one file or a directory recursively.

Input schema:

```json
{
  "type": "object",
  "required": ["inputPath"],
  "properties": {
    "inputPath": { "type": "string" },
    "workspaceRoot": { "type": "string" },
    "maxDiagnostics": { "type": "integer", "minimum": 1, "maximum": 5000, "default": 200 },
    "includeWarnings": { "type": "boolean", "default": true },
    "includeSourceExcerpt": { "type": "boolean", "default": false }
  },
  "additionalProperties": false
}
```

Success `data` schema:

```json
{
  "type": "object",
  "required": ["summary", "diagnostics"],
  "properties": {
    "summary": {
      "type": "object",
      "required": ["parsed", "finalized", "errorCount", "warningCount", "fileCount"],
      "properties": {
        "parsed": { "type": "boolean" },
        "finalized": { "type": "boolean" },
        "errorCount": { "type": "integer" },
        "warningCount": { "type": "integer" },
        "fileCount": { "type": "integer" }
      }
    },
    "diagnostics": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["severity", "code", "message", "span"],
        "properties": {
          "severity": { "type": "string", "enum": ["ERROR", "WARNING", "INFO"] },
          "code": { "type": "string" },
          "message": { "type": "string" },
          "path": { "type": ["string", "null"] },
          "span": {
            "type": "object",
            "required": ["startLine", "startCol", "endLine", "endCol"],
            "properties": {
              "startLine": { "type": "integer" },
              "startCol": { "type": "integer" },
              "endLine": { "type": "integer" },
              "endCol": { "type": "integer" }
            }
          },
          "sourceExcerpt": { "type": ["string", "null"] }
        }
      }
    }
  }
}
```

Expected clean response:

```json
{
  "ok": true,
  "data": {
    "summary": {
      "parsed": true,
      "finalized": true,
      "errorCount": 0,
      "warningCount": 1,
      "fileCount": 3
    },
    "diagnostics": [
      {
        "severity": "WARNING",
        "code": "CHR-009",
        "message": "Journey 'Checkout' is missing a @kpi trait",
        "path": "/repo/requirements/checkout/journeys.chronos",
        "span": { "startLine": 12, "startCol": 1, "endLine": 12, "endCol": 8 },
        "sourceExcerpt": null
      }
    ]
  },
  "meta": {
    "apiVersion": "2026-02-23.v1",
    "durationMs": 118,
    "requestId": "c2f5f429-f779-4aba-bfd6-3cb6546fb125"
  }
}
```

## 5.2 `chronos.explain_diagnostic`

Return canonical explanation and fix strategies for a CHR code.

Input schema:

```json
{
  "type": "object",
  "required": ["code"],
  "properties": {
    "code": { "type": "string", "pattern": "^CHR(-W)?-[0-9]{3}$" },
    "includeExamples": { "type": "boolean", "default": true }
  },
  "additionalProperties": false
}
```

Success `data` schema:

```json
{
  "type": "object",
  "required": ["code", "severity", "title", "description", "fixes"],
  "properties": {
    "code": { "type": "string" },
    "severity": { "type": "string", "enum": ["ERROR", "WARNING", "INFO"] },
    "title": { "type": "string" },
    "description": { "type": "string" },
    "likelyCauses": { "type": "array", "items": { "type": "string" } },
    "fixes": { "type": "array", "items": { "type": "string" } },
    "examples": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["bad", "good"],
        "properties": {
          "bad": { "type": "string" },
          "good": { "type": "string" }
        }
      }
    },
    "source": {
      "type": "object",
      "properties": {
        "file": { "type": "string" },
        "line": { "type": "integer" }
      }
    }
  }
}
```

Expected response:

```json
{
  "ok": true,
  "data": {
    "code": "CHR-003",
    "severity": "ERROR",
    "title": "Step requires action and expectation",
    "description": "Every step in journeys and variants must declare both action and expectation.",
    "likelyCauses": [
      "Generated step omitted expectation",
      "Refactor removed action field"
    ],
    "fixes": [
      "Add action: \"...\" to the step",
      "Add expectation: \"...\" to the step"
    ],
    "examples": [
      {
        "bad": "step Pay { action: \"Submit payment\" }",
        "good": "step Pay { action: \"Submit payment\" expectation: \"Payment authorized\" }"
      }
    ],
    "source": {
      "file": "/Users/scott/Developer/projects/ai/holodeck/chronos/chronos-validator/src/main/java/com/genairus/chronos/validator/ChronosValidator.java",
      "line": 171
    }
  },
  "meta": {
    "apiVersion": "2026-02-23.v1",
    "durationMs": 4,
    "requestId": "8a89d631-6f81-42bb-92fd-0f7f865ad932"
  }
}
```

## 5.3 `chronos.lookup_syntax`

Lookup parser/lexer rules and keyword usage from `Chronos.g4`.

Input schema:

```json
{
  "type": "object",
  "properties": {
    "rule": { "type": "string" },
    "keyword": { "type": "string" },
    "symbol": { "type": "string" },
    "maxMatches": { "type": "integer", "minimum": 1, "maximum": 50, "default": 10 }
  },
  "additionalProperties": false,
  "anyOf": [
    { "required": ["rule"] },
    { "required": ["keyword"] },
    { "required": ["symbol"] }
  ]
}
```

Success `data` schema:

```json
{
  "type": "object",
  "required": ["matches"],
  "properties": {
    "matches": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["kind", "name", "excerpt", "source"],
        "properties": {
          "kind": { "type": "string", "enum": ["parserRule", "lexerRule", "literal"] },
          "name": { "type": "string" },
          "excerpt": { "type": "string" },
          "source": {
            "type": "object",
            "required": ["file", "startLine", "endLine"],
            "properties": {
              "file": { "type": "string" },
              "startLine": { "type": "integer" },
              "endLine": { "type": "integer" }
            }
          }
        }
      }
    }
  }
}
```

Expected response:

```json
{
  "ok": true,
  "data": {
    "matches": [
      {
        "kind": "parserRule",
        "name": "stepField",
        "excerpt": "| 'input' ':' '[' dataField (',' dataField)* ']' | 'output' ':' '[' dataField (',' dataField)* ']'",
        "source": {
          "file": "/Users/scott/Developer/projects/ai/holodeck/chronos/chronos-parser/src/main/antlr/com/genairus/chronos/parser/Chronos.g4",
          "startLine": 317,
          "endLine": 324
        }
      }
    ]
  },
  "meta": {
    "apiVersion": "2026-02-23.v1",
    "durationMs": 6,
    "requestId": "8b0ef953-15d8-42eb-a6f0-0949e3f51f86"
  }
}
```

## 5.4 `chronos.generate`

Compile and run one generator target.

Input schema:

```json
{
  "type": "object",
  "required": ["inputPath", "target", "outputDir"],
  "properties": {
    "inputPath": { "type": "string" },
    "target": {
      "type": "string",
      "enum": ["prd", "markdown", "jira", "typescript", "mermaid-state", "test-scaffold", "statemachine-tests"]
    },
    "outputDir": { "type": "string" },
    "emitIrBundle": { "type": "boolean", "default": false }
  },
  "additionalProperties": false
}
```

Success `data` schema:

```json
{
  "type": "object",
  "required": ["target", "generatedFiles", "summary"],
  "properties": {
    "target": { "type": "string" },
    "generatedFiles": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["path", "bytes"],
        "properties": {
          "path": { "type": "string" },
          "bytes": { "type": "integer" },
          "sha256": { "type": "string" }
        }
      }
    },
    "bundlePath": { "type": ["string", "null"] },
    "summary": {
      "type": "object",
      "required": ["modelCount", "errorCount", "warningCount"],
      "properties": {
        "modelCount": { "type": "integer" },
        "errorCount": { "type": "integer" },
        "warningCount": { "type": "integer" }
      }
    }
  }
}
```

Expected response:

```json
{
  "ok": true,
  "data": {
    "target": "jira",
    "generatedFiles": [
      {
        "path": "/repo/generated/com-example-checkout-backlog.csv",
        "bytes": 18432,
        "sha256": "4c8e5e4f2b8d7b018f4c2b0dbf2671844a7a30b74cba5b1005f66ab6686a55b4"
      }
    ],
    "bundlePath": "/repo/generated/ir-bundle.json",
    "summary": {
      "modelCount": 6,
      "errorCount": 0,
      "warningCount": 2
    }
  },
  "meta": {
    "apiVersion": "2026-02-23.v1",
    "durationMs": 392,
    "requestId": "6d6e5f4b-f5e4-49f9-85e0-766a0ee8d4aa"
  }
}
```

## 5.5 `chronos.emit_ir_bundle`

Compile source and emit deterministic `ir-bundle.json` for LLM consumption.

Input schema:

```json
{
  "type": "object",
  "required": ["inputPath", "outputDir"],
  "properties": {
    "inputPath": { "type": "string" },
    "outputDir": { "type": "string" }
  },
  "additionalProperties": false
}
```

Success `data` schema:

```json
{
  "type": "object",
  "required": ["bundlePath", "format", "version", "modelCount"],
  "properties": {
    "bundlePath": { "type": "string" },
    "format": { "type": "string", "enum": ["chronos-ir-bundle"] },
    "version": { "type": "string", "enum": ["1"] },
    "modelCount": { "type": "integer" }
  }
}
```

Expected response:

```json
{
  "ok": true,
  "data": {
    "bundlePath": "/repo/generated/ir-bundle.json",
    "format": "chronos-ir-bundle",
    "version": "1",
    "modelCount": 4
  },
  "meta": {
    "apiVersion": "2026-02-23.v1",
    "durationMs": 165,
    "requestId": "2a57e0bc-0a58-4e8f-a2a0-07af4f6f2f25"
  }
}
```

## 5.6 `chronos.suggest_minimal_fixes`

Given diagnostics and source snapshots, return minimal edit operations.

Input schema:

```json
{
  "type": "object",
  "required": ["files", "diagnostics"],
  "properties": {
    "files": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["path", "content"],
        "properties": {
          "path": { "type": "string" },
          "content": { "type": "string" }
        }
      }
    },
    "diagnostics": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["code", "message"],
        "properties": {
          "code": { "type": "string" },
          "message": { "type": "string" },
          "path": { "type": ["string", "null"] },
          "span": { "type": ["object", "null"] }
        }
      }
    },
    "maxEdits": { "type": "integer", "minimum": 1, "maximum": 200, "default": 40 }
  },
  "additionalProperties": false
}
```

Success `data` schema:

```json
{
  "type": "object",
  "required": ["edits", "rationale"],
  "properties": {
    "edits": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["path", "op"],
        "properties": {
          "path": { "type": "string" },
          "op": { "type": "string", "enum": ["replace_range", "insert_after_line", "insert_before_line"] },
          "startLine": { "type": "integer" },
          "startCol": { "type": "integer" },
          "endLine": { "type": "integer" },
          "endCol": { "type": "integer" },
          "text": { "type": "string" }
        }
      }
    },
    "rationale": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["diagnosticCode", "summary"],
        "properties": {
          "diagnosticCode": { "type": "string" },
          "summary": { "type": "string" }
        }
      }
    }
  }
}
```

## 6. Implementation Plan

## Phase 0: Contract and fixtures (1-2 days)

Deliverables:

- Freeze this spec as `v1`.
- Add JSON fixtures under `docs/mcp-fixtures/` for each tool.
- Add contract tests that validate output envelopes and required fields.

Acceptance criteria:

- Every tool response validates against its JSON schema.
- Error codes are deterministic and documented.

## Phase 1: Core server shell + validation/grammar tools (3-5 days)

Work items:

- Create module: `chronos-mcp/` (Java 21).
- Implement MCP server boot + tool registry.
- Implement `chronos.validate` using `ChronosCompiler.compileAll`.
- Implement `chronos.lookup_syntax` by parsing/reading `Chronos.g4`.
- Add sorting identical to CLI diagnostics.

Acceptance criteria:

- `chronos.validate` output matches CLI diagnostic ordering.
- Handles file and directory inputs.
- Round-trip tests on `examples/` pass.

## Phase 2: Diagnostic intelligence and generation tools (3-4 days)

Work items:

- Implement `chronos.explain_diagnostic` from a generated diagnostic registry.
- Implement `chronos.generate` by invoking generator pipeline used in CLI.
- Implement `chronos.emit_ir_bundle` via `IrBundleEmitter`.

Acceptance criteria:

- Unknown target returns `CHRONOS_UNKNOWN_TARGET`.
- Bundle output reports `format=chronos-ir-bundle`, `version=1`.
- File metadata includes size + hash.

## Phase 3: Minimal-fix recommender + safety gates (4-6 days)

Work items:

- Implement `chronos.suggest_minimal_fixes` for top 15 CHR codes first.
- Add guardrails: max edits, no cross-file rename unless requested.
- Add revalidate-after-edit helper in server-side workflow (optional flag).

Acceptance criteria:

- For golden failing fixtures, generated edit ops reduce error count.
- No syntax-breaking edits for covered rules.

## Phase 4: Docs + migration from long prompt strategy (2-3 days)

Work items:

- Update `/Users/scott/Developer/projects/ai/holodeck/chronos/docs/ai-agent-setup.md` to prefer MCP tools first.
- Keep a tiny bootstrap prompt (100-180 words) that tells agents to call MCP tools for syntax/validation.
- Mark the full paste-in spec as fallback only.

Acceptance criteria:

- New quickstart uses MCP call flow:
  1) generate draft
  2) validate
  3) explain diagnostics
  4) apply minimal fixes
  5) validate clean
- Token usage drops significantly versus long prompt baseline.

## 7. Suggested Small Bootstrap Prompt (for agents)

Use this as system/project instruction once MCP is connected:

```text
You are authoring Chronos (.chronos) files. Do not invent syntax.
Use MCP tools as source of truth:
1) chronos.lookup_syntax for grammar questions
2) chronos.validate after each edit
3) chronos.explain_diagnostic for CHR code fixes
4) chronos.generate only after validation passes
Keep edits minimal and preserve user naming/structure unless diagnostics require changes.
```

## 8. Non-goals in v1

- No remote policy library resolution.
- No autonomous multi-repo code writing.
- No probabilistic diagnostic interpretation when compiler output is available.

## 9. Backward compatibility and versioning

- Breaks to response fields require new `apiVersion`.
- Additive fields are allowed in minor revisions.
- Maintain one previous API version for at least one release cycle.
