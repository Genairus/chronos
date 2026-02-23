# AI Agent Setup (Claude + Chronos)

This guide is the fastest path for new users to go from zero to generated requirements artifacts.

## Who This Is For

- Product managers writing first Chronos requirements with an AI agent
- Engineers helping PMs bootstrap requirements authoring
- Teams standardizing on Claude Code or another coding assistant

## 1) Install Chronos (Homebrew)

```sh
brew install Genairus/tap/chronos
chronos --version
```

If you are not on macOS/Linux Homebrew, use: [install.md](install.md).

## 2) Set Up Claude for This Repository

Claude Code reads `CLAUDE.md` automatically in your project root.

Create `CLAUDE.md` at the root of your repo and paste this starter:

```markdown
# Chronos Authoring Instructions

You are generating Chronos requirements files (.chronos) only.

Follow these sources exactly:
- docs/quick-reference.md
- chronos-parser/src/main/antlr/com/genairus/chronos/parser/Chronos.g4

Authoring rules:
1. Use one namespace per file.
2. For first draft, generate exactly one file:
   - requirements/<feature>/<feature>.chronos
3. Every journey must include:
   - actor:
   - steps: (each step must have action and expectation)
   - outcomes: with success at minimum
4. Keep names consistent and avoid duplicate shape names.
5. Ask clarifying questions if required details are missing.

Output rules:
1. Output only .chronos code in fenced blocks.
2. Do not output prose requirements docs unless asked.
3. After code, include a short self-check against the rules above.
```

For the complete long-form Chronos instruction pack and advanced Claude template, see: [ai-bot-requirements-howto.md](ai-bot-requirements-howto.md).

## 3) Prompt Claude to Generate Requirements

Use this prompt template:

```text
Create Chronos requirements for this feature.

Feature:
- Name: <feature name>
- Problem: <what is broken today>
- Primary actor: <user or role>
- Business outcome: <desired result + KPI if known>
- In scope: <list>
- Out of scope: <list>
- Compliance/security constraints: <list>
- Known failure cases: <list>

Authoring constraints:
- Generate one file at: requirements/<feature>/<feature>.chronos
- Use namespace: com.<org>.<domain>.<feature>
- Include at minimum:
  - 2 entities or shapes
  - 1 actor
  - 1 journey with 3+ steps
  - 1 variant with typed error trigger
  - 1 invariant
  - 1 deny rule
- Keep it compileable with Chronos.
```

## 4) Validate and Fix in a Loop

Run:

```sh
chronos validate requirements/<feature>/<feature>.chronos --verbose
```

If errors appear, paste diagnostics back to Claude with this prompt:

```text
Apply only the minimum edits required to fix these Chronos diagnostics.
Do not rename or redesign unless required by an error.

<paste diagnostics>
```

Repeat until the file validates cleanly.

## 5) Generate a PRD

```sh
chronos prd requirements/<feature>/<feature>.chronos --output ./generated --name <feature>-prd
```

Example output:
- `generated/<feature>-prd.md`

## 6) Generate Jira Epics and Stories

```sh
chronos generate requirements/<feature>/<feature>.chronos --target jira --output ./generated
```

Example output:
- `generated/com-yourorg-yourfeature-backlog.csv`

Chronos maps:
- each `journey` -> Epic
- each journey `step` -> Story
- each journey `variant` -> Story
- each `policy` -> Story
- each `deny` -> Story

Import in Jira from your project board using CSV import, then map:
- `Summary`
- `Issue Type`
- `Description`
- `Priority`
- `Labels`
- `Epic Name`
- `Epic Link`
- `Story Points`

## 7) Prompts That Usually Improve Quality

Use after first compile succeeds.

```text
Harden this Chronos model for implementation quality:
- make expectations deterministic and testable
- ensure every variant has a clear recovery path
- ensure deny rules cover data misuse and authorization misuse
- ensure error payloads support retries and observability
Return only updated .chronos code.
```

```text
Prepare this model for Jira planning:
- keep journey names action-oriented
- ensure each step can stand alone as a Story
- keep each variant focused on one failure mode
Return only updated .chronos code.
```

## Recommended Team Standard

- Keep one feature per file for onboarding.
- Move to multi-file composition after the team is comfortable.
- Run `chronos validate` in PR checks for every `.chronos` change.
