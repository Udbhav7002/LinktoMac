#!/bin/bash
set -e

echo "==================================================================="
echo "  LinktoMac M4 Final Empirical Adversarial Stress Test Suite       "
echo "==================================================================="

echo ""
echo ">>> [1/4] Running 20-byte Handshake Parser Fuzzer & Stress Test..."
python3 tests/adversarial_m4/test_handshake_malformed_fuzzer.py

echo ""
echo ">>> [2/4] Running ABR Telemetry & Bitrate Adaptation Stress Test..."
python3 tests/adversarial_m4/test_abr_telemetry_stress.py

echo ""
echo ">>> [3/4] Running Multi-Tenant Concurrency Isolation Test..."
node tests/adversarial_m4/test_multitenant_concurrent_sessions.js

echo ""
echo ">>> [4/4] Running Gesture Chaining & Scroll Coalescing Stress Test..."
python3 tests/adversarial_m4/test_gesture_scroll_coalescing_stress.py

echo ""
echo "==================================================================="
echo "  ALL M4 EMPIRICAL ADVERSARIAL STRESS TESTS COMPLETED (PASS)       "
echo "==================================================================="
