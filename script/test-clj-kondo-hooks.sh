#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_DIR="$PROJECT_ROOT/resources/clj-kondo.exports/clj-python/libpython-clj"
FIXTURES_DIR="$PROJECT_ROOT/test/clj_kondo/fixtures"

echo "=== clj-kondo Hook Tests ==="
echo "Config dir: $CONFIG_DIR"
echo "Fixtures dir: $FIXTURES_DIR"
echo ""

if ! command -v clj-kondo &> /dev/null; then
    echo "ERROR: clj-kondo not found in PATH"
    exit 1
fi

echo "clj-kondo version: $(clj-kondo --version)"
echo ""

FAILED=0

run_test() {
    local name="$1"
    local file="$2"

    echo -n "Testing $name... "

    OUTPUT=$(clj-kondo --lint "$file" --config-dir "$CONFIG_DIR" 2>&1) || true

    REQUIRE_PYTHON_ERRORS=$(echo "$OUTPUT" | grep -cE "(Unknown require option|:bind-ns|:reload|:no-arglists)" || true)

    if [[ "$REQUIRE_PYTHON_ERRORS" -gt 0 ]]; then
        echo "FAILED"
        echo "  Found require-python related errors/warnings:"
        echo "$OUTPUT" | grep -E "(Unknown require option|:bind-ns|:reload|:no-arglists)" | sed 's/^/    /'
        FAILED=1
        return 1
    fi

    UNRESOLVED_SYMBOL_ERRORS=$(echo "$OUTPUT" | grep -cE "Unresolved symbol: (webpush|secure_filename|urlencode|urlparse)" || true)

    if [[ "$UNRESOLVED_SYMBOL_ERRORS" -gt 0 ]]; then
        echo "FAILED"
        echo "  Found unresolved symbol errors for referred symbols:"
        echo "$OUTPUT" | grep -E "Unresolved symbol:" | sed 's/^/    /'
        FAILED=1
        return 1
    fi

    echo "PASSED"
    return 0
}

run_test "require_python_test.clj" "$FIXTURES_DIR/require_python_test.clj"
run_test "require_python_edge_cases.clj" "$FIXTURES_DIR/require_python_edge_cases.clj"

echo ""
if [[ "$FAILED" -eq 0 ]]; then
    echo "=== All tests passed ==="
    exit 0
else
    echo "=== Some tests failed ==="
    exit 1
fi
