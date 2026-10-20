import SwiftUI
import AppKit

// MARK: - Universal clipboard & URL handoff workspace
struct ClipboardWorkspaceView: View {
    @ObservedObject var clip = ClipboardSyncManager.shared
    @LocalState private var handoffURLInput: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader(title: "Clipboard",
                          subtitle: "Copy on Android, paste on Mac — and the other way around") {
                Toggle("Sync", isOn: $clip.isSyncEnabled)
                    .toggleStyle(.switch)
                    .controlSize(.mini)
                    .help("Keep both clipboards in sync")
                Toggle("Ignore password managers", isOn: $clip.ignoreConcealedPasswords)
                    .toggleStyle(.checkbox)
                    .help("Never sync concealed pasteboard items (1Password, Bitwarden, Keychain)")
            }

            // URL handoff bar
            HStack(spacing: LS.s) {
                Image(systemName: "link")
                    .foregroundStyle(LinkTheme.textTertiary)
                TextField("Paste a URL to open it on your Android phone", text: $handoffURLInput)
                    .textFieldStyle(LinkTextFieldStyle())
                    .onSubmit { sendHandoff() }
                Button {
                    sendHandoff()
                } label: {
                    Label("Open on phone", systemImage: "arrow.up.forward")
                }
                .buttonStyle(.primary)
                .disabled(handoffURLInput.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(.horizontal, LS.xl)
            .padding(.bottom, LS.m)

            if clip.history.isEmpty {
                EmptyStateView(
                    icon: "doc.on.clipboard",
                    title: "Nothing synced yet",
                    message: "Copy text or a link on either device and it will appear here, ready to use on the other."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: LS.s) {
                        ForEach(clip.history) { item in
                            ClipboardRow(item: item)
                        }
                    }
                    .padding(.horizontal, LS.xl)
                    .padding(.bottom, LS.xl)
                }
            }
        }
        .background(LinkTheme.canvas)
    }

    private func sendHandoff() {
        let trimmed = handoffURLInput.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        clip.handoffURLToAndroid(trimmed)
        handoffURLInput = ""
    }
}

// MARK: - One clipboard history row
struct ClipboardRow: View {
    let item: ClipboardHistoryItem
    @ObservedObject private var clip = ClipboardSyncManager.shared

    var body: some View {
        HStack(spacing: LS.m) {
            Image(systemName: item.isUrl ? "link" : "doc.on.clipboard")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(item.isUrl ? LinkTheme.accent : LinkTheme.textTertiary)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 3) {
                Text(item.content)
                    .font(.subheadline.monospaced() as Font)
                    .foregroundStyle(LinkTheme.textPrimary)
                    .lineLimit(2)
                HStack(spacing: LS.s) {
                    Text(item.source)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(LinkTheme.textSecondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(LinkTheme.surfaceAlt)
                        .clipShape(Capsule())
                    Text(item.timestamp.formatted(date: .omitted, time: .standard))
                        .font(.caption2)
                        .foregroundStyle(LinkTheme.textTertiary)
                }
            }

            Spacer(minLength: LS.s)

            HStack(spacing: LS.xs) {
                if item.isUrl, let url = URL(string: item.content) {
                    Button {
                        NSWorkspace.shared.open(url)
                    } label: {
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                    }
                    .buttonStyle(.icon)
                    .help("Open on this Mac")

                    Button {
                        clip.handoffURLToAndroid(item.content)
                    } label: {
                        Image(systemName: "arrow.up.forward")
                    }
                    .buttonStyle(.icon)
                    .help("Open on your Android phone")
                }
                Button {
                    clip.copyItemToMac(item)
                } label: {
                    Image(systemName: "doc.on.doc")
                }
                .buttonStyle(.icon)
                .help("Copy to this Mac's clipboard")
            }
        }
        .linkCard(padding: LS.m)
    }
}
