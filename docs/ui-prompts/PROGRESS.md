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

## Task 03 — macOS Overview Dashboard
- **Files touched:**
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/OverviewView.swift` (created)
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/MainDashboardView.swift` (removed temporary Overview stub)
- **Checklist results:**
  - [x] `swift build` succeeds in `allcode/LinkToMac-macOS`
  - [x] No "01/02/03" strings, no `WorkflowCard`, no `Color.green`
  - [x] Media buttons fire `PREV`, `PLAY_PAUSE`, `NEXT`, `VOL_DOWN`, `VOL_UP`, `RING_PHONE`
  - [x] Matte cards with hairline borders (`.linkCard()`)
  - [x] Hero CTA is the only solid-accent button
  - [x] Clipboard toggle writes through to `ClipboardSyncManager.isSyncEnabled`
  - [x] Adaptive light/dark styling with high contrast
- **Decisions:** Created `OverviewView.swift` containing `OverviewWorkflowView`, `QuickAccessCard`, and `ImageToolbarButton`.

## Task 04 — macOS Messages Workspace
- **Files touched:**
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/MessagesView.swift` (created)
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/FeatureViews.swift` (removed old MessagesWorkspaceView)
- **Checklist results:**
  - [x] `swift build` succeeds in `allcode/LinkToMac-macOS`
  - [x] Outgoing bubble: solid `LinkTheme.accent` with white text
  - [x] Incoming bubble: `surfaceAlt` fill with `textPrimary` text, `LR.card` corners
  - [x] Deterministic muted initials avatar via `AvatarView`
  - [x] Zero green; accent blue used consistently
  - [x] Thread creation, dialing, and SMS sending logic preserved identically
  - [x] Empty state uses `EmptyStateView`
  - [x] Tooltips preserved on action buttons
- **Decisions:** Created `MessagesView.swift` and extracted `ThreadRow` and `ChatBubble` components.

## Task 05 — macOS Notifications Workspace
- **Files touched:**
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/NotificationsView.swift` (created)
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/FeatureViews.swift` (removed old NotificationsWorkspaceView)
- **Checklist results:**
  - [x] `swift build` succeeds in `allcode/LinkToMac-macOS`
  - [x] App name rendered as tertiary caption; OTP chip uses quiet accent with readable digits
  - [x] "Reply sent" confirmation is the only use of semantic `success`
  - [x] Unmute-all, clear-all, OTP copy, mute, and dismiss actions wired to identical manager methods
  - [x] Reply works via Return and send icon button; clears draft
  - [x] Notification rows use matte cards with hairline borders (`.linkCard()`)
  - [x] Contrast verified in light and dark modes
- **Decisions:** Created `NotificationsView.swift` containing `NotificationsWorkspaceView` and `NotificationRow`.

## Task 06 — macOS Files & Photos Workspaces
- **Files Touched:**
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/FilesPhotosView.swift` (created)
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/FeatureViews.swift` (removed old `FilesWorkspaceView` and `PhotosWorkspaceView`)
- **Checklist Results:**
  - [x] Zero `Color.yellow` / `Color.blue` icon pairs; file icons monochrome (accent folder / tertiary doc)
  - [x] Drop overlay: 1.5pt accent stroke + 6% tint, no `lineWidth: 3`, no green
  - [x] Progress bars tinted accent; "Completed" is the only success-green text
  - [x] Drag-and-drop from Finder streams to target folder; NSOpenPanel multi-send works
  - [x] Photo cards reveal "Save" on hover, thumbnail decode path unchanged
  - [x] Transfers pane separated by a 1px hairline, transport chips neutral
  - [x] Bookmarks navigate to same paths with simplified labels
- **Decisions:** Created `FilesPhotosView.swift` containing `FilesWorkspaceView`, `FileRow`, `TransferRow`, `PhotosWorkspaceView`, and `PhotoCard`. Build passed cleanly.

## Task 07 — macOS Clipboard Workspace
- **Files Touched:**
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/ClipboardView.swift` (created)
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/FeatureViews.swift` (removed old `ClipboardWorkspaceView`)
- **Checklist Results:**
  - [x] "Open on phone" is the only solid-accent button and disables on empty input
  - [x] Row icons monochrome (accent link / tertiary doc) — no blue+green pair
  - [x] Source chip neutral (`surfaceAlt`); no green text anywhere
  - [x] All row actions hit original manager methods; concealed-password toggle intact
  - [x] Mono font only on content + chips captions stay proportional
  - [x] History rows readable with 2-line clamp
- **Decisions:** Created `ClipboardView.swift` containing `ClipboardWorkspaceView` and `ClipboardRow`. Build passed cleanly.

## Task 08 — macOS Mirror & Camera Workspace
- **Files Touched:**
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/MirrorCameraView.swift` (created)
  - `allcode/LinkToMac-macOS/Sources/LinkToMac/FeatureViews.swift` (removed old `MirrorAndCameraWorkspaceView`)
- **Checklist Results:**
  - [x] GroupBox completely replaced by LinkCard; no default macOS chrome
  - [x] Active stream option shows accent selection state (10% fill + check) — quiet, not green
  - [x] "Stop all streams" is dangerPrimary and disabled when already stopped
  - [x] Video tap gesture math identical (event.location / geo.size, clamped 0–1)
  - [x] Video well is pure black; mode chip + floating keys are 55% black capsules
  - [x] scrcpy button appears only under isScrcpyAvailable && isUSBDeviceConnected
  - [x] No animation added to video rendering
- **Decisions:** Created `MirrorCameraView.swift` containing `MirrorAndCameraWorkspaceView`, `KeyButton`, and `VideoKeyButton`. Build passed cleanly.
