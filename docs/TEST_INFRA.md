# E2E Test Infra: LinktoMac

## Test Philosophy
- Opaque-box, requirement-driven testing based on user specifications.
- Methodology: Category-Partition + Boundary Value Analysis (BVA) + Pairwise Combinatorial + Real-World Workload Testing.
- No direct coupling to internal implementation classes; exercises endpoints, binary protocols, CLI options, build targets, and IPC surfaces.

## Feature Inventory & Test Matrix
| # | Feature | Source (Requirement) | Tier 1 (Coverage >=5) | Tier 2 (Boundary >=5) | Tier 3 (Pairwise) | Tier 4 (Workload) |
|---|---------|---------------------|:---------------------:|:---------------------:|:-----------------:|:-----------------:|
| F1 | Socket Write Synchronization | R1 (Streaming) | 5 | 5 | ✓ | ✓ |
| F2 | Bounded Queue & Backpressure | R1 (Streaming) | 5 | 5 | ✓ | ✓ |
| F3 | Dynamic Display Metrics | R1 (Streaming) | 5 | 5 | ✓ | ✓ |
| F4 | On-Demand Keyframe Sync (0x05) | R1 (Streaming) | 5 | 5 | ✓ | ✓ |
| F5 | Continuous Dragging Gestures | R2 (Input) | 5 | 5 | ✓ | ✓ |
| F6 | Decoupled Navigation Keys | R2 (Input) | 5 | 5 | ✓ | ✓ |
| F7 | UTF-8 Text Transmission | R2 (Input) | 5 | 5 | ✓ | ✓ |
| F8 | macOS System Shortcuts | R2 (Input) | 5 | 5 | ✓ | ✓ |
| F9 | Single Android App Consolidation | R3 (Architecture) | 5 | 5 | ✓ | ✓ |
| F10 | Native Wi-Fi mDNS Discovery | R3 (Connectivity) | 5 | 5 | ✓ | ✓ |
| F11 | Background USB Watcher | R3 (Connectivity) | 5 | 5 | ✓ | ✓ |
| F12 | Zero-Config Connection Hub | R3 (User Flow) | 5 | 5 | ✓ | ✓ |
| F13 | Port 27189 Security & Pairing | R4 (Security) | 5 | 5 | ✓ | ✓ |
| F14 | Package ID Standardization | R4 (Packaging) | 5 | 5 | ✓ | ✓ |
| F15 | Remote Tunnel & Endpoint Hardening | R4 (Security) | 5 | 5 | ✓ | ✓ |

## Test Architecture
- **E2E Test Runner**: `python3 tests/run_e2e_tests.py`
  - Exit code 0 if 100% of test cases pass.
  - JSON / JUnit XML reporting supported.
- **Backend Unit Tests**: `pytest orchestrator/test_backend.py orchestrator/test_ui_app.py`
- **Android App Compilation**: `./gradlew assembleDebug` in `android-app/`
- **Native Client Compilation**: `cmake -B native-client/build && cmake --build native-client/build`

## Coverage Thresholds
- Tier 1: ≥ 75 test cases (≥5 per feature across 15 features)
- Tier 2: ≥ 75 test cases (boundary & edge cases)
- Tier 3: ≥ 15 pairwise interaction tests
- Tier 4: ≥ 8 real-world application scenarios
- **Total Minimum Target**: ≥ 173 test cases
