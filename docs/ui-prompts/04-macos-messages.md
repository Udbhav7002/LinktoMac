# Task 04 — macOS Messages Workspace

> Prerequisite: Tasks 01–03 done. **Create a new file** `MessagesView.swift` in
> `allcode/LinkToMac-macOS/Sources/LinkToMac/`. Struct name stays `MessagesWorkspaceView`.

## Context

Messages is a two-pane `HSplitView`: thread list (left, 250–320pt) + conversation (right). Today:
`.roundedBorder` fields, a green outgoing bubble at 0.85 opacity with **black** text (a contrast
hack), no avatars, no timestamps. Redesign: deterministic muted avatars, matte bubbles —
**incoming = `surfaceAlt`, outgoing = solid accent blue with white text** (iMessage-quiet, not neon),
quiet call button, filled input style, subtle timestamps.

Behavior contract (unchanged):
- "Chat" button creates a thread if missing (`SmsThreadItem` insert at 0) and selects it.
- Phone button calls `conn.dialPhoneNumber(trimmed)`.
- Refresh calls `conn.requestSmsAndContacts()`.
- List selection binds `$selectedThreadId` with `.tag(thread.id)`.
- "Call on Phone" → `conn.dialPhoneNumber(thread.address)`.
- Reply bar: `onSubmit` + button both call `sendCurrentDraft(to:)` → `conn.sendSms(address:body:)`.

---

## File — `allcode/LinkToMac-macOS/Sources/LinkToMac/MessagesView.swift` (create)

```swift
import SwiftUI

// MARK: - Messages (SMS + contacts) workspace
struct MessagesWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @LocalState private var selectedThreadId: String?
    @LocalState private var draftMessage: String = ""
    @LocalState private var newRecipientNumber: String = ""

    var body: some View {
        HSplitView {
            threadListPane
            conversationPane
        }
        .background(LinkTheme.canvas)
    }

    // MARK: Left — threads
    private var threadListPane: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: LS.s) {
                Text("Messages")
                    .font(.headline)
                    .foregroundStyle(LinkTheme.textPrimary)
                Spacer()
                Button {
                    conn.requestSmsAndContacts()
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .buttonStyle(.icon)
                .help("Sync SMS & contacts from Android")
            }
            .padding(.horizontal, LS.m)
            .padding(.top, LS.m)
            .padding(.bottom, LS.s)

            HStack(spacing: LS.s) {
                TextField("Dial or new SMS number", text: $newRecipientNumber)
                    .textFieldStyle(LinkTextFieldStyle())
                Button {
                    let trimmed = newRecipientNumber.trimmingCharacters(in: .whitespaces)
                    guard !trimmed.isEmpty else { return }
                    if !conn.smsThreads.contains(where: { $0.address == trimmed }) {
                        conn.smsThreads.insert(SmsThreadItem(
                            id: trimmed,
                            contactName: trimmed,
                            address: trimmed,
                            lastMessage: "New conversation",
                            timestamp: Int64(Date().timeIntervalSince1970 * 1000),
                            messages: []
                        ), at: 0)
                    }
                    selectedThreadId = trimmed
                    newRecipientNumber = ""
                } label: {
                    Text("Chat")
                }
                .buttonStyle(.quiet)
                Button {
                    let trimmed = newRecipientNumber.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty { conn.dialPhoneNumber(trimmed) }
                } label: {
                    Image(systemName: "phone")
                }
                .buttonStyle(.icon)
                .help("Dial this number on your Android phone")
            }
            .padding(.horizontal, LS.m)
            .padding(.bottom, LS.s)

            List(conn.smsThreads, selection: $selectedThreadId) { thread in
                ThreadRow(thread: thread)
                    .tag(thread.id)
            }
            .listStyle(.plain)
        }
        .frame(minWidth: 250, maxWidth: 320)
    }

    // MARK: Right — conversation
    private var conversationPane: some View {
        VStack(spacing: 0) {
            if let threadId = selectedThreadId,
               let thread = conn.smsThreads.first(where: { $0.id == threadId }) {
                conversationHeader(thread)
                messageList(thread)
                replyBar(thread)
            } else {
                EmptyStateView(
                    icon: "message",
                    title: "No conversation selected",
                    message: "Pick a thread on the left, or start a new SMS with a phone number."
                )
            }
        }
        .background(LinkTheme.canvas)
    }

    private func conversationHeader(_ thread: SmsThreadItem) -> some View {
        HStack(spacing: LS.m) {
            AvatarView(name: thread.contactName, size: 34)
            VStack(alignment: .leading, spacing: 1) {
                Text(thread.contactName)
                    .font(.headline)
                    .foregroundStyle(LinkTheme.textPrimary)
                    .lineLimit(1)
                Text(thread.address)
                    .font(.caption)
                    .foregroundStyle(LinkTheme.textSecondary)
            }
            Spacer()
            Button {
                conn.dialPhoneNumber(thread.address)
            } label: {
                Label("Call on phone", systemImage: "phone")
            }
            .buttonStyle(.quiet)
        }
        .padding(.horizontal, LS.l)
        .padding(.vertical, LS.m)
        .background(LinkTheme.surface)
        .overlay(alignment: .bottom) { HairlineDivider() }
    }

    private func messageList(_ thread: SmsThreadItem) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: LS.s) {
                ForEach(thread.messages) { msg in
                    ChatBubble(message: msg)
                }
            }
            .padding(.horizontal, LS.l)
            .padding(.vertical, LS.l)
        }
    }

    private func replyBar(_ thread: SmsThreadItem) -> some View {
        HStack(spacing: LS.s) {
            TextField("Reply via Android SMS", text: $draftMessage)
                .textFieldStyle(LinkTextFieldStyle())
                .onSubmit { sendCurrentDraft(to: thread.address) }
            Button {
                sendCurrentDraft(to: thread.address)
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 20))
            }
            .buttonStyle(.icon)
            .disabled(draftMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .help("Send SMS via Android")
        }
        .padding(.horizontal, LS.l)
        .padding(.vertical, LS.m)
        .background(LinkTheme.surface)
        .overlay(alignment: .top) { HairlineDivider() }
    }

    private func sendCurrentDraft(to address: String) {
        let text = draftMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        conn.sendSms(address: address, body: text)
        draftMessage = ""
    }
}

// MARK: - Thread row with muted initials avatar
struct ThreadRow: View {
    let thread: SmsThreadItem

    var body: some View {
        HStack(spacing: 10) {
            AvatarView(name: thread.contactName, size: 30)
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(thread.contactName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(LinkTheme.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Text(timestamp)
                        .font(.caption2)
                        .foregroundStyle(LinkTheme.textTertiary)
                }
                Text(thread.lastMessage)
                    .font(.caption)
                    .foregroundStyle(LinkTheme.textSecondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 4)
    }

    private var timestamp: String {
        Date(timeIntervalSince1970: Double(thread.timestamp) / 1000)
            .formatted(.relative(presentation: .named))
    }
}

// MARK: - Matte chat bubble: incoming surfaceAlt / outgoing solid accent
struct ChatBubble: View {
    let message: SmsMessageItem

    var body: some View {
        HStack(alignment: .bottom, spacing: LS.s) {
            if !message.isIncoming { Spacer(minLength: 48) }
            VStack(alignment: message.isIncoming ? .leading : .trailing, spacing: 2) {
                Text(message.body)
                    .font(.subheadline)
                    .foregroundStyle(message.isIncoming
                                     ? LinkTheme.textPrimary
                                     : Color.white)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(message.isIncoming
                                ? LinkTheme.surfaceAlt
                                : LinkTheme.accent)
                    .clipShape(RoundedRectangle(cornerRadius: LR.card))
                Text(timestamp)
                    .font(.caption2)
                    .foregroundStyle(LinkTheme.textTertiary)
            }
            if message.isIncoming { Spacer(minLength: 48) }
        }
        .accessibilityElement(children: .combine)
    }

    private var timestamp: String {
        Date(timeIntervalSince1970: Double(message.date) / 1000)
            .formatted(date: .omitted, time: .shortened)
    }
}
```

---

## Acceptance Checklist

- [ ] Outgoing bubble: solid `LinkTheme.accent` fill, **white** text (no black-on-green hack).
- [ ] Incoming bubble: `surfaceAlt` fill, primary text. Both radii = `LR.card` (12).
- [ ] Avatars are deterministic (same contact → same muted color, light & dark).
- [ ] Green is completely absent; the only blue is the accent system.
- [ ] Thread "Chat"/dial/new-thread logic byte-identical to the original (same `SmsThreadItem` insert).
- [ ] Empty state uses `EmptyStateView` (tertiary, quiet).
- [ ] `.help()` tooltips preserved on refresh and send buttons.
- [ ] Selection highlight works in both appearances (plain list style + canvas background).
