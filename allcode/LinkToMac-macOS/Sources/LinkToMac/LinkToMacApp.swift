import SwiftUI

@main
struct LinkToMacApp: App {
    @ObservedObject private var conn = ConnectionManager.shared
    @ObservedObject private var clip = ClipboardSyncManager.shared

    var body: some Scene {
        WindowGroup("LinkToMac") {
            MainDashboardView()
                .frame(minWidth: 980, minHeight: 640)
                .preferredColorScheme(.dark)
        }

        // Native macOS Menu Bar Companion Popover
        MenuBarExtra {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Circle()
                        .fill(conn.isConnected ? Color.green : Color.orange)
                        .frame(width: 8, height: 8)
                    Text(conn.isConnected ? "\(conn.connectedDeviceName) (\(conn.connectionTransport))" : "LinkToMac: Waiting for Phone")
                        .font(.headline)
                }

                if conn.isConnected {
                    Text("Battery: \(conn.batteryLevel)% • Notifications: \(conn.notifications.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Divider()

                Toggle("Universal Clipboard Sync", isOn: $clip.isSyncEnabled)

                Button("Play / Pause Android Media") {
                    conn.sendMediaAction("PLAY_PAUSE")
                }
                Button("Ring My Android Phone") {
                    conn.sendMediaAction("RING_PHONE")
                }

                Divider()
                Button("Quit LinkToMac") {
                    NSApplication.shared.terminate(nil)
                }
            }
            .padding(12)
        } label: {
            HStack(spacing: 4) {
                Image(systemName: conn.isConnected ? "iphone.radiowaves.left.and.right" : "iphone.slash")
                if conn.isConnected {
                    Text("\(conn.batteryLevel)%")
                }
            }
        }
    }
}
