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
                         : (conn.connectedDeviceName != "No Android Connected" ? "Waiting for \(conn.connectedDeviceName)" : "Waiting for your phone"))
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
                if conn.isRingingPhone {
                    Button("Stop Ringing Phone") { conn.stopRingingPhone() }
                        .foregroundStyle(LinkTheme.danger)
                } else {
                    Button("Ring My Android Phone") { conn.ringPhone() }
                }

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
