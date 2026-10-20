#!/bin/bash
set -euo pipefail

export LINKTOMAC_HEADLESS=1
export TESTING=1
export LINKTOMAC_NO_BROWSER=1

echo "=========================================================="
echo "    M2 Adversarial Challenge & Stress Test Suite"
echo "=========================================================="
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_DIR"

echo ""
echo ">>> [1/4] Running Multi-Tenant Cloud Isolation Tests..."
node tests/adversarial_m2/test_multitenant_isolation.js

echo ""
echo ">>> [2/4] Running Remote Session Recovery Stress Tests..."
node tests/adversarial_m2/test_remote_recovery_stress.js

echo ""
echo ">>> [3/4] Running Premature Write Elimination Tests..."
node tests/adversarial_m2/test_premature_write_elimination.js

echo ""
echo ">>> [4/5] Running Challenger M2 Iteration 2 Stress Tests (Race & Endpoint Parsing)..."
node tests/adversarial_m2/test_challenger_m2_i2.js

echo ""
echo ">>> [5/5] Running Full Orchestrator Unit Test Suite..."
python3 -m unittest discover -s orchestrator -p "test_*.py"

echo ""
echo "=========================================================="
echo "    ALL M2 ADVERSARIAL & UNIT TESTS COMPLETED (PASS)"
echo "=========================================================="
