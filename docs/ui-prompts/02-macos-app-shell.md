# Task 02 — macOS App Shell: Window, Sidebar & Status Pill

> Prerequisite: Task 01 completed (`LinkTheme.swift` + `LinkComponents.swift` exist and compile).

## Context

The shell is `LinkToMacApp.swift` (window + menu bar popover) and `MainDashboardView.swift`
(NavigationSplitView: sidebar with brand header, live connection pill, section list; detail switch;
floating call HUD mount). Today it **forces dark mode**, uses green accents, and the old
`MainDashboardView.swift` also contains `OverviewWorkflowView`, `WorkflowCard`, and
`PairingQRSetupView` — those move into their own files in Tasks 03 and 09.

**File organization change (important):** each workspace gets its own file. After this task,
`MainDashboardView.swift` contains ONLY the shell. Tasks 03–09 add new files. At the END of Task 09
you will delete the old `FeatureViews.swift` — until then the project will have duplicate struct
names if you don't remove them now. So:

1. **Replace** `LinkToMacApp.swift` with File A below.
2. **Replace** `MainDashboardView.swift` with File B below (shell only — the Overview/WorkflowCard/
   PairingQRSetupView structs are intentionally absent; they are re-created in Tasks 03 & 09).
3. **Temporarily rename** `FeatureViews.swift` → keep it, but note the duplicate structs
   (`OverviewWorkflowView`, `PairingQRSetupView`, `IncomingCallHUDView`) will collide. To keep the
   project compiling during the migration, rename the OLD structs in `FeatureViews.swift` by suffixing
   them with `Legacy` (e.g. `struct MessagesWorkspaceViewLegacy`), OR — preferred —
   **delete `FeatureViews.swift` now and let Tasks 04–08 recreate its views one file at a time.**

The cleanest path: delete `FeatureViews.swift` now, then run tasks 03→09 in order; after Task 03 the
app compiles again with Overview working, and each later task adds one more section.

**Do not touch:** `ConnectionManager`, `ClipboardSyncManager`, `ADBBridge`, `ProtocolModels`, `LocalState`.

---

## File A — `allcode/LinkToMac-macOS/Sources/LinkToMac/LinkToMacApp.swift` (replace)

```swift
import SwiftUI

@main
struct LinkToMacApp: App {
    @ObservedObject private var conn = ConnectionManager.shared
    @ObservedObject private var clip = ClipboardSyncManager.shared

    var body: some Scene {
        WindowGroup("LinkToMac") {
            MainDashboardView()
                .frame(minWidth: 980, minHeight: 640)
            // NOTE: no .preferredColorScheme(.dark) — Quiet Premium follows the system appearance.
        }

        // Native macOS menu bar companion popover
        MenuBarExtra {
            VStack(alignment: .leading, spacing: LS.m) {
                HStack(spacing: LS.s) {
                    StatusDot(state: conn.isConnected ? .connected : .waiting)
                    Text(conn.isConnected
                         ? "\(conn.connectedDeviceName) · \(conn.connectionTransport)"
                         : "Waiting for your phone")
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                }

                if conn.isConnected {
                    Text("Battery \(conn.batteryLevel)%\(conn.isCharging ? " · charging" : "") · \(conn.notifications.count) notifications")
                        .font(.caption)
                        .foregroundStyle(LinkTheme.textSecondary)
                }

                HairlineDivider()
                    .padding(.vertical, 2)

                Toggle("Universal Clipboard Sync", isOn: $clip.isSyncEnabled)
                    .font(.subheadline)

                Button("Play / Pause Android Media") { conn.sendMediaAction("PLAY_PAUSE") }
                Button("Ring My Android Phone")    { conn.sendMediaAction("RING_PHONE") }

                HairlineDivider()
                    .padding(.vertical, 2)

                Button("Quit LinkToMac") {
                    NSApplication.shared.terminate(nil)
                }
                .foregroundStyle(LinkTheme.danger)
            }
            .padding(LS.m)
            .frame(minWidth: 260)
        } label: {
            Image(systemName: conn.isConnected ? "iphone.radiowaves.left.and.right" : "iphone")
        }
        .menuBarExtraStyle(.window)
    }
}
```

---

## File B — `allcode/LinkToMac-macOS/Sources/LinkToMac/MainDashboardView.swift` (replace)

```swift
import SwiftUI

// MARK: - Sidebar sections (quiet SF Symbols, no .fill)
enum DashboardSection: String, CaseIterable, Identifiable {
    case overview      = "Dashboard"
    case messages      = "Messages"
    case notifications = "Notifications"
    case files         = "Files"
    case photos        = "Photos"
    case clipboard     = "Clipboard"
    case mirrorCamera  = "Mirror & Camera"
    case pairSetup     = "Pair Device"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .overview:      return "square.grid.2x2"
        case .messages:      return "message"
        case .notifications: return "bell"
        case .files:         return "folder"
        case .photos:        return "photo"
        case .clipboard:     return "doc.on.clipboard"
        case .mirrorCamera:  return "video"
        case .pairSetup:     return "qrcode"
        }
    }
}

struct MainDashboardView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @ObservedObject var adb = ADBBridge.shared
    @ObservedObject var clip = ClipboardSyncManager.shared
    @LocalState private var selectedSection: DashboardSection = .overview

    var body: some View {
        ZStack(alignment: .topTrailing) {
            NavigationSplitView {
                SidebarView(selectedSection: $selectedSection)
                    .frame(minWidth: 220)
            } detail: {
                Group {
                    switch selectedSection {
                    case .overview:      OverviewWorkflowView(selectedSection: $selectedSection) // Task 03
                    case .messages:      MessagesWorkspaceView()                                  // Task 04
                    case .notifications: NotificationsWorkspaceView()                            // Task 05
                    case .files:         FilesWorkspaceView()                                     // Task 06
                    case .photos:        PhotosWorkspaceView()                                    // Task 06
                    case .clipboard:     ClipboardWorkspaceView()                                 // Task 07
                    case .mirrorCamera:  MirrorAndCameraWorkspaceView()                           // Task 08
                    case .pairSetup:     PairingQRSetupView()                                     // Task 09
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(LinkTheme.canvas)
            }

            // Floating incoming-call HUD — the ONLY animated overlay (functional motion)
            if conn.callState.isActive {
                IncomingCallHUDView() // Task 09
                    .padding(LS.xl)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.28, dampingFraction: 0.9), value: conn.callState.isActive)
    }
}

// MARK: - Sidebar
private struct SidebarView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @Binding var selectedSection: DashboardSection

    var body: some View {
        VStack(alignment: .leading, spacing: LS.m) {
            brandHeader
            StatusPill()
            sectionList
        }
        .padding(.vertical, LS.m)
        .frame(minWidth: 220)
    }

    private var brandHeader: some View {
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 7)
                    .fill(LinkTheme.accent)
                    .frame(width: 26, height: 26)
                Image(systemName: "link")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.white)
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text("LinkToMac")
                    .font(.headline)
                    .foregroundStyle(LinkTheme.textPrimary)
                Text("Local & private")
                    .font(.caption2)
                    .foregroundStyle(LinkTheme.textTertiary)
            }
        }
        .padding(.horizontal, LS.m)
        .padding(.top, LS.s)
        .accessibilityElement(children: .combine)
    }

    private var sectionList: some View {
        List(DashboardSection.allCases, id: \.self, selection: $selectedSection) { section in
            NavigationLink(value: section) {
                HStack(spacing: 10) {
                    Label(section.rawValue, systemImage: section.icon)
                        .foregroundStyle(LinkTheme.textPrimary)
                    Spacer(minLength: 0)
                    if section == .notifications, !conn.notifications.isEmpty {
                        CountBadge(count: conn.notifications.count)
                    }
                }
                .padding(.vertical, 2)
            }
        }
        .listStyle(.sidebar)
    }
}

// MARK: - Unified live connection pill (reused concept on Android + web)
struct StatusPill: View {
    @ObservedObject var conn = ConnectionManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: LS.s) {
            HStack(spacing: LS.s) {
                StatusDot(state: conn.isConnected ? .connected : .waiting)
                Text(conn.isConnected ? conn.connectedDeviceName : "Waiting for Android")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(LinkTheme.textPrimary)
                    .lineLimit(1)
            }
            HStack(spacing: LS.s) {
                Label(
                    conn.isConnected ? conn.connectionTransport : "Port \(conn.listenPort)",
                    systemImage: conn.connectionTransport == "USB" ? "cable.connector" : "wifi"
                )
                if conn.isConnected {
                    Text("·").foregroundStyle(LinkTheme.textTertiary)
                    Label("\(conn.batteryLevel)%", systemImage: batterySymbol)
                }
            }
            .font(.caption)
            .foregroundStyle(LinkTheme.textSecondary)
        }
        .padding(LS.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LinkTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: LR.card))
        .overlay(
            RoundedRectangle(cornerRadius: LR.card)
                .strokeBorder(LinkTheme.hairline, lineWidth: 1)
        )
        .padding(.horizontal, LS.m)
        .accessibilityElement(children: .combine)
    }

    private var batterySymbol: String {
        if conn.isCharging { return "battery.100.bolt" }
        switch conn.batteryLevel {
        case 90...:    return "battery.100"
        case 70..<90:  return "battery.75"
        case 40..<70:  return "battery.50"
        case 15..<40:  return "battery.25"
        default:       return "battery.0"
        }
    }
}
```

---

## Notes

- The old forced dark mode (`.preferredColorScheme(.dark)`) is intentionally removed — the design
  system is adaptive. **Do not** re-add it.
- The sidebar keeps native `.listStyle(.sidebar)` (HIG-correct vibrancy) — that translucency is a
  system material, not a custom glassmorphism effect, so it is allowed.
- `OverviewWorkflowView`, `MessagesWorkspaceView`, `NotificationsWorkspaceView`, `FilesWorkspaceView`,
  `PhotosWorkspaceView`, `ClipboardWorkspaceView`, `MirrorAndCameraWorkspaceView`,
  `PairingQRSetupView`, and `IncomingCallHUDView` are referenced but **created by Tasks 03–09**.
  If you deleted `FeatureViews.swift` as instructed, the project compiles fully again after Task 03.
- Version strings ("v1.61") are removed from the UI; version belongs to the bundle, not the chrome.

## Acceptance Checklist

- [ ] `swift build` succeeds (after Task 03, or with legacy structs temporarily suffixed).
- [ ] App window follows the **system** appearance — toggling macOS light/dark re-themes instantly.
- [ ] Sidebar: blue brand mark, matte status pill with hairline, quiet outline icons, neutral badge.
- [ ] Notification count badge is neutral (surfaceAlt), NOT green.
- [ ] Menu bar label shows the phone icon; popover shows StatusDot, semantic danger "Quit".
- [ ] No `Color.green`, no `.tint(.green)`, no gradients anywhere in these two files.
- [ ] Call HUD mount still animates with the spring (functional motion preserved).
