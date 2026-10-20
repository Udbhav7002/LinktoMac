import Foundation
import AppKit

@MainActor
final class ClipboardSyncManager: ObservableObject {
    static let shared = ClipboardSyncManager()

    @Published var isSyncEnabled: Bool = true
    @Published var ignoreConcealedPasswords: Bool = true
    @Published var history: [ClipboardHistoryItem] = []

    private var lastChangeCount: Int = NSPasteboard.general.changeCount
    private var lastSyncedString: String = ""
    private var pollTimer: Timer?

    private init() {
        startMonitoring()
    }

    func startMonitoring() {
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.65, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.checkMacPasteboard()
            }
        }
    }

    private func checkMacPasteboard() {
        guard isSyncEnabled else { return }
        let pb = NSPasteboard.general
        guard pb.changeCount != lastChangeCount else { return }
        lastChangeCount = pb.changeCount

        // Respect password managers (1Password, Bitwarden, Keychain)
        if ignoreConcealedPasswords {
            let concealedType = NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType")
            if pb.types?.contains(concealedType) == true {
                return
            }
        }

        guard let copied = pb.string(forType: .string)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !copied.isEmpty,
              copied != lastSyncedString else { return }

        lastSyncedString = copied
        let isUrl = copied.hasPrefix("http://") || copied.hasPrefix("https://")
        recordHistory(content: copied, isUrl: isUrl, source: "This Mac")

        if ConnectionManager.shared.isConnected {
            ConnectionManager.shared.sendEnvelope(LinkEnvelope(
                type: "CLIPBOARD_SYNC",
                payload: [
                    "content": copied,
                    "isUrl": isUrl ? "true" : "false"
                ]
            ))
        }
    }

    func applyIncomingFromAndroid(text: String) {
        guard isSyncEnabled, text != lastSyncedString else { return }
        lastSyncedString = text

        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(text, forType: .string)
        lastChangeCount = pb.changeCount

        let isUrl = text.hasPrefix("http://") || text.hasPrefix("https://")
        recordHistory(content: text, isUrl: isUrl, source: "Android")
    }

    func copyItemToMac(_ item: ClipboardHistoryItem) {
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(item.content, forType: .string)
        lastChangeCount = pb.changeCount
        lastSyncedString = item.content
    }

    func handoffURLToAndroid(_ urlString: String) {
        ConnectionManager.shared.sendEnvelope(LinkEnvelope(
            type: "URL_HANDOFF",
            payload: ["url": urlString]
        ))
    }

    private func recordHistory(content: String, isUrl: Bool, source: String) {
        history.removeAll { $0.content == content }
        history.insert(ClipboardHistoryItem(
            content: content,
            isUrl: isUrl,
            source: source,
            timestamp: Date()
        ), at: 0)
        if history.count > 40 {
            history.removeLast()
        }
    }
}







