#!/bin/bash
set -e

export LINKTOMAC_HEADLESS=1
export TESTING=1
export LINKTOMAC_NO_BROWSER=1

echo "========================================================"
echo " Running All Empirical M1 Native Client Stress Tests"
echo "========================================================"

echo ""
echo "--- 1. Compilation Verification ---"
make client
cmake --build native-client/build --clean-first

echo ""
echo "--- 2. Handshake Integrity Stress Test ---"
python3 tests/adversarial_native/test_handshake_stress.py

echo ""
echo "--- 3. Trackpad Scroll Coalescing Stress Test ---"
./tests/adversarial_native/test_scroll_stress

echo ""
echo "--- 4. Dynamic Window Auto-Resize Stress Test ---"
./tests/adversarial_native/test_resize_stress

echo ""
echo "========================================================"
echo " All M1 Native Client Empirical Stress Tests PASSED!"
echo "========================================================"
