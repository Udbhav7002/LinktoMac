# UI Redesign Progress Log

## Task 01 — Design Tokens
- **Files touched:**
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/LinkTheme.swift`
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/LinkComponents.swift`
  - `allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkTheme.kt`
- **Checklist results:**
  - [x] `swift build` succeeds in `allcode/LinkToMac-macOS`
  - [x] `LinkTheme.swift` contains zero `Color.green` / `.tint(.green)`
  - [x] Master palette colors match exactly
  - [x] No gradients anywhere
  - [x] `LinkComponents.swift` compiles standalone
  - [x] Android has no new Gradle dependencies (`assembleDebug` succeeds)
- **Decisions:** Followed exact specifications in `01-design-tokens.md`.

## Task 02 — macOS App Shell
- **Files touched:**
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/LinkToMacApp.swift` (replaced)
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/MainDashboardView.swift` (replaced)
- **Checklist results:**
  - [x] `swift build` succeeds in `allcode/LinkToMac-macOS`
  - [x] Window follows system appearance (adaptive light/dark, `.preferredColorScheme(.dark)` removed)
  - [x] Sidebar features blue brand mark, matte StatusPill with hairline, quiet outline SF symbols, neutral badge
  - [x] Notification count badge uses neutral `CountBadge`
  - [x] Menu bar popover uses `StatusDot` and `LinkTheme.danger`
  - [x] No `Color.green`, `.tint(.green)`, or gradients
  - [x] Call HUD mount spring animation preserved
- **Decisions:** Stubs added for `OverviewWorkflowView` and `PairingQRSetupView` in `MainDashboardView.swift` to ensure atomic build pass before Tasks 03 and 09 create their respective dedicated files.
