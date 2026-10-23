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
    @ObservedObject var tunnel = PinggyTunnelManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: LS.s) {
            HStack(spacing: LS.s) {
                StatusDot(state: conn.isConnected ? .connected : (tunnel.isRunning ? .waiting : .disconnected))
                Text(conn.isConnected ? conn.connectedDeviceName : "Waiting for Android")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(LinkTheme.textPrimary)
                    .lineLimit(1)
            }
            HStack(spacing: LS.s) {
                if conn.isConnected {
                    Label(
                        conn.connectionTransport,
                        systemImage: transportIcon(for: conn.connectionTransport)
                    )
                    Text("·").foregroundStyle(LinkTheme.textTertiary)
                    Label("\(conn.batteryLevel)%", systemImage: batterySymbol)
                } else {
                    if tunnel.isRunning, let port = tunnel.publicPort {
                        Label("Relay :\(port)", systemImage: "globe")
                    } else {
                        Label("Port \(conn.listenPort)", systemImage: "wifi")
                    }
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

    private func transportIcon(for transport: String) -> String {
        switch transport {
        case "USB":          return "cable.connector"
        case "Remote Relay": return "globe"
        default:             return "wifi"
        }
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

