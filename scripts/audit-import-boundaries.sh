#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# scripts/audit-import-boundaries.sh
#
# Read-only boundary audit for the Chronos module graph.
# Prints a warning for each forbidden import found; exits 0 always.
# Does NOT modify any files or fail the build.
#
# Usage:
#   ./scripts/audit-import-boundaries.sh
#   ./scripts/audit-import-boundaries.sh --strict   # exit 1 if any violations
#
# Boundaries checked:
#   B1  chronos-parser/src/main/java   must not import com.genairus.chronos.ir.*
#   B2  chronos-model/src/main/java    must not import org.antlr.*
#   B3  chronos-compiler/src/main/java must not import org.antlr.*
#   B4  chronos-generators/src/main/java must not import com.genairus.chronos.parser.* or .syntax.*
#   B5  chronos-cli/src/main/java      must not use ChronosParserFacade / ChronosModelParser / LoweringVisitor directly
# ──────────────────────────────────────────────────────────────────────────────

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STRICT=false
[[ "${1-}" == "--strict" ]] && STRICT=true

VIOLATIONS=0

warn() {
    echo "⚠️  BOUNDARY VIOLATION: $1" >&2
    VIOLATIONS=$((VIOLATIONS + 1))
}

ok() {
    echo "✅  $1"
}

check() {
    local label="$1"
    local dir="$2"
    local pattern="$3"

    if [[ ! -d "$REPO_ROOT/$dir" ]]; then
        echo "  (skipping $dir — directory not found)"
        return
    fi

    local hits
    hits=$(grep -r --include="*.java" -l "$pattern" "$REPO_ROOT/$dir" 2>/dev/null || true)

    if [[ -n "$hits" ]]; then
        warn "$label"
        echo "$hits" | while read -r f; do
            echo "    → ${f#"$REPO_ROOT/"}"
        done
    else
        ok "$label"
    fi
}

echo ""
echo "Chronos Import Boundary Audit"
echo "=============================="
echo ""

# B1: parser must not construct IR types
check \
    "B1  chronos-parser/main  must not import com.genairus.chronos.ir.*" \
    "chronos-parser/src/main/java" \
    "import com\.genairus\.chronos\.ir\."

# B2: model must not use ANTLR
check \
    "B2  chronos-model/main   must not import org.antlr.*" \
    "chronos-model/src/main/java" \
    "import org\.antlr\."

# B3: compiler must not use ANTLR directly
check \
    "B3  chronos-compiler/main must not import org.antlr.*" \
    "chronos-compiler/src/main/java" \
    "import org\.antlr\."

# B4: generators must not import parser or syntax packages
check \
    "B4a chronos-generators/main must not import com.genairus.chronos.parser.*" \
    "chronos-generators/src/main/java" \
    "import com\.genairus\.chronos\.parser\."

check \
    "B4b chronos-generators/main must not import com.genairus.chronos.syntax.*" \
    "chronos-generators/src/main/java" \
    "import com\.genairus\.chronos\.syntax\."

# B5: CLI must not bypass the compiler with direct parser calls
check \
    "B5  chronos-cli/main      must not call ChronosParserFacade/ChronosModelParser/LoweringVisitor" \
    "chronos-cli/src/main/java" \
    "ChronosParserFacade\|ChronosModelParser\|LoweringVisitor"

echo ""
if [[ "$VIOLATIONS" -eq 0 ]]; then
    echo "All boundaries clean. ($VIOLATIONS violations)"
else
    echo "Found $VIOLATIONS boundary violation(s)."
    if $STRICT; then
        exit 1
    fi
fi
echo ""
