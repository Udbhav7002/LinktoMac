# LinktoMac UI Master Prompt Pack — "Quiet Premium"

A complete, agent-ready prompt pack that restyles every LinktoMac surface (macOS SwiftUI app,
Android Compose app, web orchestrator, menu bar apps) into one unified, minimal, matte design system.

**Design brief (from the app owner):** *"very minimal and simple to use, it need to give a rich look…
colours to be matt and no gradients or neon colours very simple and minimal."*
Direction: Apple-native · Brand accent: matte blue `#0071E3` / `#0A84FF` · Light + dark ·
Minimal functional motion · No emoji icons.

---

## How to use this pack with your agents

Feed tasks **in order, one task per agent session**. Each file is self-contained
(context → full drop-in code → acceptance checklist). Start every session with:

> Read `00-MASTER-PROMPT.md` completely, then read task file `NN-….md` and implement exactly what
> it specifies. Do not modify any file the task doesn't list. Run the task's acceptance checklist
> before reporting completion.

| Order | File | Surface | Creates / replaces |
|---|---|---|---|
| 0 | `00-MASTER-PROMPT.md` | contract | (read me first — design system, rules, workflow) |
| 1 | `01-design-tokens.md` | all | NEW `LinkTheme.swift`, `LinkComponents.swift`, `LinkTheme.kt`, CSS tokens |
| 2 | `02-macos-app-shell.md` | macOS | REPLACES `LinkToMacApp.swift`, `MainDashboardView.swift`; deletes `FeatureViews.swift` |
| 3 | `03-macos-overview.md` | macOS | NEW `OverviewView.swift` |
| 4 | `04-macos-messages.md` | macOS | NEW `MessagesView.swift` |
| 5 | `05-macos-notifications.md` | macOS | NEW `NotificationsView.swift` |
| 6 | `06-macos-files-photos.md` | macOS | NEW `FilesPhotosView.swift` |
| 7 | `07-macos-clipboard.md` | macOS | NEW `ClipboardView.swift` |
| 8 | `08-macos-mirror-camera.md` | macOS | NEW `MirrorCameraView.swift` |
| 9 | `09-macos-pairing-callhud.md` | macOS | NEW `PairingCallHUDView.swift` + migration cleanup |
| 10 | `10-android-app.md` | Android | REPLACES `MainActivity.kt` (+ `LinkTheme.kt` from Task 01) |
| 11 | `11-web-orchestrator.md` | web | REPLACES `style.css`, `index.html`; scoped patch to `script.js` |
| 12 | `12-menubar.md` | menu bar | verifies `MenuBarExtra`; patches rumps `_run_menubar` |

**Suggested parallelization:** Tasks 3–9 can run in parallel *after* Task 2 (each creates an
independent file). Tasks 10, 11, 12 are independent of each other. Do not run anything before
Tasks 0–1.

## What the redesign changes (and never changes)

**Changed (presentation only):** colors → semantic tokens; radii/spacing → 4pt grid scales;
buttons/inputs/cards → shared matte components with hairline borders; icons → outline SF Symbols /
Material outlined / inline SVG; forced-dark → adaptive light+dark; emoji → removed; chat bubbles,
avatars, permission checklist, QR pairing card, call HUD, device cards, recovery HUD → restyled.

**Never changed:** `ConnectionManager`, `ClipboardSyncManager`, `ADBBridge`, all Android services
and bridges, the binary wire protocol, ports, Bonjour service, Firestore paths, share intents,
`script.js` logic, build configs, dependencies.

## Final cross-surface QA (run after Task 12)

1. macOS: `swift build && ./build_mac_app.sh` — open app, toggle light/dark, walk all 8 sections,
   answer a call (HUD), pair via QR with the Android app.
2. Android: `./gradlew assembleDebug` — scan QR, verify permission checklist states update after
   granting, share a file to Mac.
3. Web: open `orchestrator/ui/index.html` (or run the pywebview app) — toggle system appearance,
   switch tabs, trigger the recovery HUD, confirm no console errors.
4. Grep every surface for forbidden patterns (gradients, `Color.green`, `0xFF4ADE80`, emoji) —
   expect zero hits in view/UI files.
5. Screenshot each surface in light + dark and compare against the master anti-pattern list.
