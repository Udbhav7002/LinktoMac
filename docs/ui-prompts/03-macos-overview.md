# Task 03 — macOS Overview Dashboard

> Prerequisite: Tasks 01 + 02 done. **Create a new file** `OverviewView.swift` in
> `allcode/LinkToMac-macOS/Sources/LinkToMac/`. The struct stays named `OverviewWorkflowView`
> (referenced by the shell's detail switch) and keeps the same `selectedSection` binding contract.

## Context

The Overview is the app's first impression. Today it is a wall of green buttons, "01/02/03" capsule
badges, and `Color.primary.opacity(0.05)` cards. Redesign it as a quiet dashboard: one hero status
card, three flat quick-access cards, a media remote and a clipboard card — all matte `surface` cards
with hairline borders, blue accent, generous whitespace. **No numbered badges, no green.**

Behavior contract (unchanged): hero CTA navigates to `.pairSetup`; quick cards navigate to
`.messages` / `.files` / `.mirrorCamera`; media buttons call `conn.sendMediaAction(...)` with the same
action strings; clipboard toggle binds `$clip.isSyncEnabled`.

---

## File — `allcode/LinkToMac-macOS/Sources/LinkToMac/OverviewView.swift` (create)

```swift
import SwiftUI

// MARK: - Overview: the quiet morning dashboard
struct OverviewWorkflowView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @ObservedObject var clip = ClipboardSyncManager.shared
    @Binding var selectedSection: DashboardSection

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: LS.xl) {
                heroCard
                quickAccessGrid
                HStack(alignment: .top, spacing: LS.l) {
                    mediaRemoteCard
                    clipboardCard
                }
            }
            .padding(LS.xxl)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(LinkTheme.canvas)
    }

    // MARK: Hero — one sentence, one action
    private var heroCard: some View {
        HStack(alignment: .firstTextBaseline, spacing: LS.xl) {
            VStack(alignment: .leading, spacing: LS.s) {
                Text(conn.isConnected
                     ? "\(conn.connectedDeviceName) is ready."
                     : "Make Android feel at home on your Mac.")
                    .font(.title2.bold())
                    .foregroundStyle(LinkTheme.textPrimary)
                Text("Messages, files, photos, clipboard and mirroring over USB or private local Wi-Fi. Call audio stays on your phone.")
                    .font(.subheadline)
                    .foregroundStyle(LinkTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: LS.l)
            Button {
                selectedSection = .pairSetup
            } label: {
                Label(conn.isConnected ? "Connection details" : "Pair a device",
                      systemImage: "qrcode")
            }
            .buttonStyle(.primary)
        }
        .linkCard(padding: LS.xl)
    }

    // MARK: Quick access — three quiet cards
    private var quickAccessGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())],
                  spacing: LS.l) {
            QuickAccessCard(
                title: "Stay in the conversation",
                subtitle: "\(conn.notifications.count) notifications · \(conn.smsThreads.count) message threads",
                icon: "message",
                buttonTitle: "Open Messages"
            ) { selectedSection = .messages }

            QuickAccessCard(
                title: "Move what you need",
                subtitle: "Drag & drop files · \(conn.remotePhotos.count) photos · synced clipboard",
                icon: "arrow.left.arrow.right",
                buttonTitle: "Browse Files & Photos"
            ) { selectedSection = .files }

            QuickAccessCard(
                title: "Use your phone from the desk",
                subtitle: "Screen mirroring, screen-off mode, webcam & media remote",
                icon: "video",
                buttonTitle: "Open Mirror & Camera"
            ) { selectedSection = .mirrorCamera }
        }
    }

    // MARK: Media remote
    private var mediaRemoteCard: some View {
        VStack(alignment: .leading, spacing: LS.m) {
            Label("Media remote", systemImage: "music.note")
                .font(.headline)
                .foregroundStyle(LinkTheme.textPrimary)

            VStack(alignment: .leading, spacing: 2) {
                Text(conn.nowPlayingTitle)
                    .font(.headline)
                    .lineLimit(1)
                Text(conn.nowPlayingArtist)
                    .font(.caption)
                    .foregroundStyle(LinkTheme.textSecondary)
                    .lineLimit(1)
            }

            HStack(spacing: LS.xs) {
                ImageToolbarButton(icon: "backward.fill")  { conn.sendMediaAction("PREV") }
                ImageToolbarButton(icon: "playpause.fill") { conn.sendMediaAction("PLAY_PAUSE") }
                ImageToolbarButton(icon: "forward.fill")   { conn.sendMediaAction("NEXT") }
                HairlineDivider().frame(width: 1, height: 18).padding(.horizontal, LS.s)
                ImageToolbarButton(icon: "speaker.minus.fill") { conn.sendMediaAction("VOL_DOWN") }
                ImageToolbarButton(icon: "speaker.plus.fill")  { conn.sendMediaAction("VOL_UP") }
                Spacer(minLength: LS.m)
                Button("Ring phone") { conn.sendMediaAction("RING_PHONE") }
                    .buttonStyle(.quietWarning)
            }
        }
        .linkCard()
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Latest clipboard
    private var clipboardCard: some View {
        VStack(alignment: .leading, spacing: LS.m) {
            HStack {
                Label("Universal clipboard", systemImage: "doc.on.clipboard")
                    .font(.headline)
                    .foregroundStyle(LinkTheme.textPrimary)
                Spacer()
                Toggle("Sync", isOn: $clip.isSyncEnabled)
                    .toggleStyle(.switch)
                    .controlSize(.mini)
                    .labelsHidden()
                    .help("Universal clipboard sync")
            }
            if let latest = clip.history.first {
                Text(latest.content)
                    .font(.subheadline.monospaced() as Font)
                    .lineLimit(2)
                    .foregroundStyle(LinkTheme.textPrimary)
                Text("Copied on \(latest.source) · ready to paste with ⌘V")
                    .font(.caption)
                    .foregroundStyle(LinkTheme.textTertiary)
            } else {
                Text("Copy text or a link on either device and it appears here, ready to paste.")
                    .font(.subheadline)
                    .foregroundStyle(LinkTheme.textSecondary)
            }
        }
        .linkCard()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Quick access card (replaces WorkflowCard — no numbers, no green)
struct QuickAccessCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let buttonTitle: String
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: LS.m) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(LinkTheme.accent)
            Text(title)
                .font(.headline)
                .foregroundStyle(LinkTheme.textPrimary)
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(LinkTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .frame(minHeight: 30, alignment: .topLeading)
            Button(buttonTitle, action: action)
                .buttonStyle(.quiet)
        }
        .linkCard()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Small square icon control used by the media remote
struct ImageToolbarButton: View {
    let icon: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .medium))
                .frame(width: 28, height: 28)
        }
        .buttonStyle(.icon)
        .accessibilityLabel(accessibilityName)
    }

    private var accessibilityName: String {
        switch icon {
        case "backward.fill":       return "Previous track"
        case "playpause.fill":      return "Play or pause"
        case "forward.fill":        return "Next track"
        case "speaker.minus.fill":  return "Volume down"
        case "speaker.plus.fill":   return "Volume up"
        default:                    return icon
        }
    }
}
```

---

## Acceptance Checklist

- [ ] No "01/02/03" strings, no `WorkflowCard`, no `Color.green` remain.
- [ ] All five media buttons fire the same action strings (`PREV`, `PLAY_PAUSE`, `NEXT`, `VOL_DOWN`,
      `VOL_UP`) and Ring phone still uses `RING_PHONE`.
- [ ] Cards are matte white (light) / `#2C2C2E` (dark) with hairline borders — no shadows, no opacity hacks.
- [ ] Hero CTA is the only solid-accent (blue) button on the screen.
- [ ] Clipboard toggle still writes through to `ClipboardSyncManager.isSyncEnabled`.
- [ ] Light and dark both look correct; text contrast ≥ 4.5:1.
