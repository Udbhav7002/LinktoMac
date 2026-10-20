# Task 05 — macOS Notifications Workspace

> Prerequisite: Tasks 01–04 done. **Create a new file** `NotificationsView.swift` in
> `allcode/LinkToMac-macOS/Sources/LinkToMac/`. Struct name stays `NotificationsWorkspaceView`.

## Context

Notification mirroring with inline keyboard reply and OTP (2FA) copy. Today: green uppercase app
names, blue borderedProminent OTP button, green Reply buttons. Redesign: **tertiary caption app
name**, quiet accent OTP chip, quiet reply, icon-button dismiss. Rows sit on `surface` with hairlines
instead of default list chrome.

Behavior contract (unchanged):
- "Unmute All Apps" → `conn.mutedApps.removeAll()` (only shown when non-empty).
- "Clear All" → `conn.notifications.removeAll()`.
- OTP copy → `NSPasteboard` clear + set string.
- Mute App → insert into `conn.mutedApps` + remove matching notifications.
- Dismiss → `conn.dismissNotification(notificationId:)`.
- Reply (submit + button) → `conn.sendNotificationReply(notificationId:replyText:)`, draft cleared.

---

## File — `allcode/LinkToMac-macOS/Sources/LinkToMac/NotificationsView.swift` (create)

```swift
import SwiftUI
import AppKit

// MARK: - Android notification mirroring workspace
struct NotificationsWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @LocalState private var replyDrafts: [String: String] = [:]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader(title: "Notifications",
                          subtitle: "Live from your Android — reply inline with your keyboard") {
                HStack(spacing: LS.s) {
                    if !conn.mutedApps.isEmpty {
                        Button("Unmute all apps") { conn.mutedApps.removeAll() }
                            .buttonStyle(.quiet)
                            .help("Stop hiding notifications from \(conn.mutedApps.count) muted apps")
                    }
                    Button("Clear all") { conn.notifications.removeAll() }
                        .buttonStyle(.quietDanger)
                }
            }

            if conn.notifications.isEmpty {
                EmptyStateView(
                    icon: "bell",
                    title: "No recent notifications",
                    message: "Incoming alerts from your phone appear here with one-tap 2FA codes and inline replies."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: LS.s) {
                        ForEach(conn.notifications) { item in
                            NotificationRow(
                                item: item,
                                draft: Binding(
                                    get: { replyDrafts[item.id] ?? "" },
                                    set: { replyDrafts[item.id] = $0 }
                                ),
                                onReply: { text in
                                    conn.sendNotificationReply(notificationId: item.id, replyText: text)
                                    replyDrafts[item.id] = ""
                                }
                            )
                        }
                    }
                    .padding(.horizontal, LS.xl)
                    .padding(.bottom, LS.xl)
                }
            }
        }
        .background(LinkTheme.canvas)
    }
}

// MARK: - One notification row
struct NotificationRow: View {
    let item: AndroidNotificationItem
    @Binding var draft: String
    let onReply: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: LS.s) {
            header
            Text(item.body)
                .font(.subheadline)
                .foregroundStyle(LinkTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            if let sent = item.replySentText {
                Label("Reply sent — “\(sent)”", systemImage: "checkmark.circle")
                    .font(.caption)
                    .foregroundStyle(LinkTheme.success)   // semantic: action completed
            } else if item.canReply {
                replyBar
            }
        }
        .linkCard(padding: LS.l)
        .accessibilityElement(children: .contain)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: LS.s) {
            VStack(alignment: .leading, spacing: 1) {
                Text(item.appName.uppercased())
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(LinkTheme.textTertiary)
                    .kerning(0.4)
                Text(item.title)
                    .font(.headline)
                    .foregroundStyle(LinkTheme.textPrimary)
                    .lineLimit(2)
            }
            Spacer(minLength: LS.s)

            if let otp = item.otpCode, !otp.isEmpty {
                Button {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(otp, forType: .string)
                } label: {
                    Label("Copy code \(otp)", systemImage: "doc.on.doc")
                }
                .buttonStyle(.quiet)
                .controlSize(.small)
                .help("Copy the 2FA code to your clipboard")
            }

            Button("Mute") {
                ConnectionManager.shared.mutedApps.insert(item.packageName)
                ConnectionManager.shared.notifications.removeAll {
                    $0.packageName == item.packageName
                }
            }
            .buttonStyle(.quiet)
            .controlSize(.small)
            .help("Hide future notifications from \(item.appName)")

            Button {
                ConnectionManager.shared.dismissNotification(notificationId: item.id)
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .medium))
            }
            .buttonStyle(.icon)
            .help("Dismiss this notification")
        }
    }

    private var replyBar: some View {
        HStack(spacing: LS.s) {
            TextField("Reply from your keyboard", text: $draft)
                .textFieldStyle(LinkTextFieldStyle())
                .onSubmit { onReply(draft) }
            Button {
                onReply(draft)
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 20))
            }
            .buttonStyle(.icon)
            .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }
}
```

---

## Acceptance Checklist

- [ ] App name is tertiary caption (NOT green); OTP chip is quiet accent with mono-readable digits.
- [ ] "Reply sent" confirmation uses semantic `success` — the only green on the screen.
- [ ] All five actions (unmute-all, clear-all, OTP copy, mute, dismiss) hit the identical manager calls.
- [ ] Reply works both via Return key and the send icon; draft clears after send.
- [ ] Rows are matte cards with hairline borders; hover/press states are opacity-only.
- [ ] Works in light and dark; contrast of caption2 app-name is acceptable (tertiary on surface).
