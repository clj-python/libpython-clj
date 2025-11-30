#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_SOURCE="$PROJECT_ROOT/resources/clj-kondo.exports/clj-python/libpython-clj"
TEST_PROJECT="/tmp/libpython-clj-kondo-test-$$"

echo "=== End-to-End clj-kondo Config Test ==="
echo "Project root: $PROJECT_ROOT"
echo "Config source: $CONFIG_SOURCE"
echo "Test project: $TEST_PROJECT"
echo ""
echo "Note: This test simulates what 'clj-kondo --copy-configs' does when"
echo "      libpython-clj is distributed as a JAR (which includes resources/)."
echo ""

cleanup() {
    if [[ -d "$TEST_PROJECT" ]]; then
        rm -rf "$TEST_PROJECT"
    fi
}
trap cleanup EXIT

mkdir -p "$TEST_PROJECT/src"
mkdir -p "$TEST_PROJECT/.clj-kondo/clj-python"

cp -r "$CONFIG_SOURCE" "$TEST_PROJECT/.clj-kondo/clj-python/libpython-clj"

cat > "$TEST_PROJECT/src/test_app.clj" << 'EOF'
(ns test-app
  (:require [libpython-clj2.require :refer [require-python import-python]]))

(import-python)

(require-python '[numpy :as np]
                '[pathlib :bind-ns true]
                '[pywebpush :bind-ns true :refer [webpush]]
                '[werkzeug.utils :refer [secure_filename]]
                '[sklearn :reload true]
                '[pandas :no-arglists true])

(defn main []
  (pathlib/Path "/tmp")
  (webpush {})
  (secure_filename "test.txt")
  (np/array [1 2 3])
  (python/len [1 2 3]))
EOF

echo "Step 1: Created test project with copied configs"
echo ""

cd "$TEST_PROJECT"

echo "Step 2: Verifying config structure..."
if [[ -f "$TEST_PROJECT/.clj-kondo/clj-python/libpython-clj/config.edn" ]]; then
    echo "  ✓ config.edn exists"
else
    echo "  ✗ config.edn NOT found"
    exit 1
fi

if [[ -f "$TEST_PROJECT/.clj-kondo/clj-python/libpython-clj/hooks/libpython_clj/require/require_python.clj" ]]; then
    echo "  ✓ require_python.clj hook exists"
else
    echo "  ✗ require_python.clj hook NOT found"
    exit 1
fi

if [[ -f "$TEST_PROJECT/.clj-kondo/clj-python/libpython-clj/hooks/libpython_clj/require/import_python.clj" ]]; then
    echo "  ✓ import_python.clj hook exists"
else
    echo "  ✗ import_python.clj hook NOT found"
    exit 1
fi
echo ""

echo "Step 3: Running clj-kondo on test file..."
OUTPUT=$(clj-kondo --lint "$TEST_PROJECT/src/test_app.clj" 2>&1) || true
echo "$OUTPUT"
echo ""

REQUIRE_PYTHON_ERRORS=$(echo "$OUTPUT" | grep -cE "(Unknown require option|:bind-ns|:reload|:no-arglists)" || true)
UNRESOLVED_REFER_ERRORS=$(echo "$OUTPUT" | grep -cE "Unresolved symbol: (webpush|secure_filename)" || true)

if [[ "$REQUIRE_PYTHON_ERRORS" -gt 0 ]]; then
    echo "✗ FAILED: Found require-python related errors"
    exit 1
fi

if [[ "$UNRESOLVED_REFER_ERRORS" -gt 0 ]]; then
    echo "✗ FAILED: Found unresolved symbol errors for :refer symbols"
    exit 1
fi

echo "=== All end-to-end tests passed ==="
echo ""
echo "The clj-kondo config will work correctly when libpython-clj is"
echo "installed via clj-kondo --copy-configs --dependencies"
