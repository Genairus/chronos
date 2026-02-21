# CLI Reference

All commands follow the form:

```sh
chronos [global options] <command> [command options] [arguments]
```

## Global options

| Option | Description |
|--------|-------------|
| `--version` | Print the version and exit |
| `--help` | Print help and exit |
| `--debug` | Enable debug-level output |
| `--no-color` | Disable ANSI color in output |
| `--force-color` | Force ANSI color even when not a TTY |

---

## `chronos prd`

Generate a Markdown PRD from one or more `.chronos` files.

```
chronos prd <input> [--out <dir>] [--name <docName>]
```

| Argument | Description |
|----------|-------------|
| `<input>` | A `.chronos` file or directory. Directories are walked recursively. |
| `--out <dir>` | Output directory (default: current directory) |
| `--name <docName>` | Combined document name when compiling a directory (default: derived from namespace) |

**Examples:**

```sh
# Single file
chronos prd checkout.chronos

# Directory → combined PRD
chronos prd examples/ecommerce/ --name ecommerce-prd --out /tmp/out/

# Getting-started example
chronos prd examples/getting-started/ --out /tmp/
```

**Exit codes:** `0` = success (warnings allowed), `1` = compilation errors.

Diagnostic format: `file.chronos:3:1 [ERROR CHR-001] Journey has no actor`.

---

## `chronos validate`

Parse and validate a single `.chronos` file. Prints diagnostics and exits.

```
chronos validate <file>
```

Useful for editor integrations and pre-commit hooks.

---

## `chronos generate`

Compile and generate output using a named generator target.

```
chronos generate <input> --target <name> [--out <dir>]
```

| Argument | Description |
|----------|-------------|
| `<input>` | A `.chronos` file or directory |
| `--target <name>` | Generator target (see [Generators](generators.md)) |
| `--out <dir>` | Output directory (default: current directory) |

**Known targets:** `prd`, `markdown`, `jira`, `typescript`, `mermaid-state`, `test-scaffold`, `statemachine-tests`

---

## `chronos build`

Compile according to a `chronos.build` configuration file.

```
chronos build [<config>]
```

| Argument | Description |
|----------|-------------|
| `<config>` | Path to build config file (default: `./chronos.build`) |

The build config specifies source globs, targets, output directories, and model projections.

---

## Diagnostic codes

| Code | Severity | Meaning |
|------|----------|---------|
| CHR-001 | ERROR | Journey has no `actor:` field |
| CHR-002 | ERROR | Journey has no `outcomes:` block |
| CHR-003 | ERROR | Duplicate shape name in namespace |
| CHR-005 | ERROR | Duplicate namespace declaration in the same compilation |
| CHR-008 | ERROR | Unresolved cross-reference (actor, parent, type) |
| CHR-012 | ERROR | Symbol reference still unresolved after finalization |
| CHR-013 | ERROR | Type reference cannot be resolved |
| CHR-014 | ERROR | Duplicate symbol in namespace |
| CHR-019 | ERROR | Invariant expression uses unsupported dot-path navigation |
| CHR-021 | ERROR | Global invariant scope references entity from another namespace |
| CHR-PARSE | ERROR | Syntax error |
| CHR-009 | WARNING | Journey actor is declared but never used outside the journey |
