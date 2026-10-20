#!/bin/bash
set -e

export LINKTOMAC_HEADLESS=1
export TESTING=1
export LINKTOMAC_NO_BROWSER=1

echo "========================================================"
echo " Running All Empirical M2 Native Client Stress Tests"
echo "========================================================"

echo ""
echo "--- 1. Compilation Verification ---"
make client
cmake --build native-client/build --clean-first

echo ""
echo "--- 2. Compiling Decoder Dynamic Resolution Stress Harness ---"
clang++ -std=c++17 -O2 tests/adversarial_native/test_decoder_stress.cpp \
    -Inative-client/src \
    $(pkg-config --cflags --libs libavcodec libavformat libavutil libswscale sdl2) \
    -Lnative-client/build -lmirror_native \
    -framework VideoToolbox -framework CoreMedia -framework CoreVideo \
    -framework CoreFoundation -framework AppKit \
    -o tests/adversarial_native/test_decoder_stress

echo ""
echo "--- 3. Running Decoder Dynamic Resolution & Frame Pool Stress Harness ---"
./tests/adversarial_native/test_decoder_stress

echo ""
echo "--- 4. Running Renderer Dynamic Window Auto-Resize Stress Harness ---"
./tests/adversarial_native/test_resize_stress

echo ""
echo "========================================================"
echo " All M2 Native Client Empirical Stress Tests PASSED!"
echo "========================================================"
