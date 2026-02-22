# Contributing to Chronos

This page is for developers who want to build Chronos from source, add language features, write a new generator, or fix a bug.

For end-user documentation see the [Documentation Index](index.md).

---

## Requirements

- JDK 21 (GraalVM JDK 21 with `native-image` if you want to produce a native binary)
- Gradle wrapper — no separate Gradle install needed

---

## Build and Run

**Run without building a native binary** (any JDK 21):

```sh
./gradlew :chronos-cli:run --args="--help"
./gradlew :chronos-cli:run --args="prd examples/integration/checkout.chronos"
```

**Build a native binary** (requires GraalVM JDK 21 with `native-image`):

```sh
./gradlew :chronos-cli:nativeCompile
# Binary: chronos-cli/build/native/nativeCompile/chronos
```

---

## Run Tests

```sh
# All tests
./gradlew test

# Full verification including architectural boundary audits
./gradlew check
```

`check` runs two extra tasks beyond tests:

| Task | What it checks |
|------|----------------|
| `verifyArchBoundaries` | Module dependency rules — e.g. `chronos-cli` must not import `chronos-parser` directly |
| `verifyNoLegacyImports` | Forbidden package imports left over from refactors |

Run these before every push.

---

## Module Layout

| Module | Purpose |
|--------|---------|
| `chronos-core/` | Shared types: `Span`, `QualifiedName`, `SymbolRef`, `Diagnostic`, `DiagnosticCollector` |
| `chronos-ir/` | Canonical IR model — all `IrShape` subtypes, `TraitValue`, `TypeRef`, `IrModel` |
| `chronos-parser/` | ANTLR grammar (`Chronos.g4`), `LoweringVisitor`, `SyntaxDecl` DTOs, `ChronosParserFacade` |
| `chronos-compiler/` | Multi-pass pipeline — `ChronosCompiler`, 7 phases, `BuildIrSkeletonPhase` |
| `chronos-validator/` | `ChronosValidator` — 50+ CHR diagnostic rules |
| `chronos-generators/` | `MarkdownPrdGenerator`, `JiraBacklogGenerator`, TypeScript, Mermaid, test scaffold |
| `chronos-artifacts/` | `IrArtifactEmitter` — emits IR JSON alongside generated artifacts |
| `chronos-cli/` | PicoCLI entry points: `validate`, `prd`, `generate`, `build`, `select`, `diff` |

Dependency direction: `chronos-core → chronos-ir → chronos-parser → chronos-compiler → chronos-generators → chronos-cli`

---

## Grammar

The ANTLR 4 grammar is the authoritative language definition:

```
chronos-parser/src/main/antlr/com/genairus/chronos/parser/Chronos.g4
```

The grammar is compiled automatically by `./gradlew :chronos-parser:generateGrammarSource` (or any task that depends on it). Do not edit the generated `ChronosParser.java` / `ChronosLexer.java` files directly.

Key grammar design notes:

- `DURATION` token (`500ms`, `5m`, `2h`, `7d`) is placed after `NUMBER` and before `ID` so that ANTLR's longest-match rule picks it up correctly.
- `description` is a contextual keyword (used only in `policy` bodies) — it tokenizes as `ID` everywhere else.
- `stepsDecl` uses commas between steps: `step (',' step)*`.

---

## Compiler Pipeline

`ChronosCompiler.compile(String src, String name)` runs 7 phases in order:

| Phase | Class | What it does |
|-------|-------|-------------|
| 1 | `ParseAndLowerPhase` | ANTLR parse + `LoweringVisitor` → `SyntaxModel` |
| 2 | `CollectSymbolsPhase` | Build `SymbolTable` from `SyntaxDecl`s |
| 3 | `BuildIrSkeletonPhase` | `SyntaxModel → IrModel` (unresolved refs) |
| 4 | `TypeResolutionPhase` | Resolve `TypeRef.NamedTypeRef` → transformer, returns new `IrModel` |
| 5 | `CrossLinkResolutionPhase` | Resolve `parentRef`, `statemachine` bindings, etc. |
| 6 | `ValidationPhase` | Run `ChronosValidator` → emit CHR diagnostics |
| 7 | `FinalizeIrPhase` | Mark all refs resolved; fail on any remaining unresolved |

Multi-file: `ChronosCompiler.compileAll(List<SourceUnit>)` runs an `IndexPass` first, then Phases 3–6 per file (no per-file Phase 7), then a combined finalize.

---

## Adding a New Shape Type

1. Add the grammar rule in `Chronos.g4`.
2. Add a `SyntaxDecl` subtype in `chronos-parser/src/main/java/com/genairus/chronos/syntax/`.
3. Add a visitor method in `LoweringVisitor`.
4. Add the `IrShape` subtype in `chronos-ir/src/main/java/com/genairus/chronos/ir/types/`.
5. Update every **exhaustive switch** over `IrShape` — grep for `return switch (shape)` in `chronos-cli/src/main/java` to find them all.
6. Add lowering in `BuildIrSkeletonPhase.buildShape()`.
7. Add serialization in `IrModelSerializer` and `IrJsonCodec`.
8. Add validation rules in `ChronosValidator` if needed.
9. Add rendering in `MarkdownPrdGenerator` if needed.

---

## Adding a New Generator

1. Create a class in `chronos-generators/src/main/java/com/genairus/chronos/generators/` that implements `Generator`.
2. Register it in `GeneratorRegistry`.
3. Add a `--target <name>` alias in the CLI `GenerateCommand` if desired.
4. Add a snapshot golden file test (see `MarkdownPrdGeneratorSnapshotTest` as a model).

---

## Diagnostic Codes

All CHR diagnostic codes are documented in the class-level Javadoc of `ChronosValidator` and registered in `DiagnosticCodeRegistryTest.ALL_KNOWN_CODES`. When adding a new rule:

1. Pick the next available code in the `CHR-0xx` sequence.
2. Add to `ChronosValidator` Javadoc.
3. Add to `ALL_KNOWN_CODES` in `DiagnosticCodeRegistryTest`.
4. Write tests in `chronos-validator/src/test/`.

---

## Golden File Tests

Snapshot tests in `chronos-generators/src/test/` compare generated output byte-for-byte against committed golden files in `examples/integration/`. If your change legitimately alters the output:

1. Delete the golden file.
2. Re-run the test — it will write a new golden file and fail with "commit it and re-run".
3. Inspect the diff, commit the new golden file, and re-run.

---

## Releases

Chronos uses GitHub Releases with native binaries attached as assets.

```sh
# Tag and create a release with auto-generated notes
gh release create v0.1.0 --generate-notes \
  --title "Chronos 0.1.0" \
  chronos-cli/build/native/nativeCompile/chronos-macos-aarch64.tar.gz \
  chronos-cli/build/native/nativeCompile/chronos-linux-x86_64.tar.gz \
  chronos-cli/build/native/nativeCompile/chronos-windows-x86_64.zip
```

After releasing, update the Homebrew tap (`Genairus/homebrew-tap`) and Scoop bucket (`Genairus/scoop-chronos`) with the new version and checksums.

---

## Repository Structure

Beyond the modules listed above, other key directories:

| Path | Purpose |
|------|---------|
| `examples/` | Runnable language examples (also used as test fixtures) |
| `docs/` | User-facing documentation (install, CLI, language reference, how-to guides) |
| `Documentation/` | Strategy, roadmap, and planning documents |
| `scripts/` | Boundary audit and CI helper scripts |

---

## Roadmap

Planned language extensions and rollout strategy:

- [Evolution Plan](../Documentation/Chronos-Evolution-Plan.md)
- [Feature Priority Order](../Documentation/Chronos-Feature-Priority-Order.md)
