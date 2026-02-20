#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# scripts/audit-forbidden-imports.sh
#
# Enforces that all pipeline modules do NOT import legacy model types from
# com.genairus.chronos.model.*.
#
# The canonical IR types in com.genairus.chronos.ir.* must be used instead.
# The legacy com.genairus.chronos.model package has been deleted; this script
# guards against any accidental re-introduction of such imports.
#
# Usage:
#   ./scripts/audit-forbidden-imports.sh          # exits 1 if any violations
#   ./scripts/audit-forbidden-imports.sh --report # exits 0, just prints report
#
# Exit codes:
#   0 — no violations found
#   1 — one or more violations found (unless --report is given)
# ──────────────────────────────────────────────────────────────────────────────

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_ONLY=false
[[ "${1-}" == "--report" ]] && REPORT_ONLY=true

VIOLATIONS=0
FORBIDDEN_PATTERN="import com\.genairus\.chronos\.model\."

check_module() {
    local label="$1"
    local dir="$2"

    if [[ ! -d "$REPO_ROOT/$dir" ]]; then
        echo "  (skipping $dir — directory not found)"
        return
    fi

    local hits
    hits=$(grep -r --include="*.java" -l "$FORBIDDEN_PATTERN" "$REPO_ROOT/$dir" 2>/dev/null || true)

    if [[ -n "$hits" ]]; then
        echo "❌  FORBIDDEN: $label imports com.genairus.chronos.model.*" >&2
        echo "$hits" | while read -r f; do
            echo "      ${f#"$REPO_ROOT/"}" >&2
            # Show the specific import lines for easy diagnosis
            grep -n "$FORBIDDEN_PATTERN" "$f" | while read -r line; do
                echo "        line $line" >&2
            done
        done
        VIOLATIONS=$((VIOLATIONS + 1))
    else
        echo "✅  $label — no legacy model imports"
    fi
}

echo ""
echo "Chronos Forbidden-Import Audit (legacy model → IR)"
echo "===================================================="
echo ""
echo "Forbidden pattern: import com.genairus.chronos.model.*"
echo "Checked modules:   chronos-compiler, chronos-generators, chronos-cli, chronos-validator, chronos-parser"
echo ""

check_module "chronos-compiler"  "chronos-compiler/src/main/java"
check_module "chronos-generators" "chronos-generators/src/main/java"
check_module "chronos-cli"       "chronos-cli/src/main/java"
check_module "chronos-validator" "chronos-validator/src/main/java"
check_module "chronos-parser"    "chronos-parser/src/main/java"

echo ""
if [[ "$VIOLATIONS" -eq 0 ]]; then
    echo "✅  All modules are legacy-model-free. ($VIOLATIONS violations)"
    echo ""
    exit 0
else
    echo "❌  Found $VIOLATIONS module(s) with forbidden legacy model imports." >&2
    echo "    Migrate to com.genairus.chronos.ir.types.* and IrModel." >&2
    echo ""
    if $REPORT_ONLY; then
        exit 0
    fi
    exit 1
fi
