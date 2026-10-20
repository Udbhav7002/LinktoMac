import SwiftUI
import CoreImage.CIFilterBuiltins

enum DashboardSection: String, CaseIterable, Identifiable {
    case overview      = "Dashboard"
    case messages      = "Messages"
    case notifications = "Notifications"
    case files         = "Files"
    case photos        = "Photos"
    case clipboard     = "Clipboard"
    case mirrorCamera  = "Mirror & Camera"
    case pairSetup     = "Pair Device (QR)"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .overview:      return "laptopcomputer.and.iphone"
        case .messages:      return "message.fill"
        case .notifications: return "bell.badge.fill"
        case .files:         return "folder.fill"
        case .photos:        return "photo.on.rectangle.angled"
        case .clipboard:     return "doc.on.clipboard.fill"
        case .mirrorCamera:  return "rectangle.inset.filled.and.person.filled"
        case .pairSetup:     return "qrcode.viewfinder"
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
                VStack(alignment: .leading, spacing: 12) {
                    // Brand Header
                    HStack(spacing: 10) {
                        Image(systemName: "link.circle.fill")
                            .font(.system(size: 26))
                            .foregroundStyle(.green)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("LinkToMac")
                                .font(.headline.bold())
                            Text("v1.61 • Local & Private")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 8)

                    // Live Connection Pill
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Circle()
                                .fill(conn.isConnected ? Color.green : Color.orange)
                                .frame(width: 9, height: 9)
                            Text(conn.isConnected ? conn.connectedDeviceName : "Waiting for Android")
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                        }
                        HStack(spacing: 8) {
                            Label(conn.isConnected ? conn.connectionTransport : "Port \(conn.listenPort)", systemImage: conn.connectionTransport == "USB" ? "cable.connector" : "wifi")
                            if conn.isConnected {
                                Text("•")
                                Label("\(conn.batteryLevel)%\(conn.isCharging ? " ⚡" : "")", systemImage: "battery.75")
                            }
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .padding(.horizontal, 10)

                    List(DashboardSection.allCases, id: \.self, selection: $selectedSection) { section in
                        NavigationLink(value: section) {
                            HStack {
                                Label(section.rawValue, systemImage: section.icon)
                                Spacer()
                                if section == .notifications && !conn.notifications.isEmpty {
                                    Text("\(conn.notifications.count)")
                                        .font(.caption2.bold())
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.green.opacity(0.25))
                                        .clipShape(Capsule())
                                }
                            }
                        }
                    }
                    .listStyle(.sidebar)
                }
                .frame(minWidth: 220)
            } detail: {
                Group {
                    switch selectedSection {
                    case .overview:
                        OverviewWorkflowView(selectedSection: $selectedSection)
                    case .messages:
                        MessagesWorkspaceView()
                    case .notifications:
                        NotificationsWorkspaceView()
                    case .files:
                        FilesWorkspaceView()
                    case .photos:
                        PhotosWorkspaceView()
                    case .clipboard:
                        ClipboardWorkspaceView()
                    case .mirrorCamera:
                        MirrorAndCameraWorkspaceView()
                    case .pairSetup:
                        PairingQRSetupView()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            // Floating Incoming Call HUD Overlay (Appears automatically when phone rings)
            if conn.callState.isActive {
                IncomingCallHUDView()
                    .padding(20)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.3), value: conn.callState.isActive)
    }
}

// MARK: - Overview Morning Workflow Dashboard
struct OverviewWorkflowView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @ObservedObject var clip = ClipboardSyncManager.shared
    @Binding var selectedSection: DashboardSection

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Hero Status Banner
                HStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(conn.isConnected ? "\(conn.connectedDeviceName) is ready." : "Make Android feel at home on your Mac.")
                            .font(.title.bold())
                        Text("Messages, notifications, files, photos, clipboard, call controls, camera, and mirroring over USB or private local Wi-Fi. Cellular call audio and the microphone remain on your Android phone.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        selectedSection = .pairSetup
                    } label: {
                        Label(conn.isConnected ? "Connection Details" : "Scan Pairing QR", systemImage: "qrcode")
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)
                }
                .padding(20)
                .background(Color.primary.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 14))

                // 3 Core Workflows Grid
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                    WorkflowCard(
                        number: "01",
                        title: "Stay in the conversation",
                        subtitle: "\(conn.notifications.count) active notifications • \(conn.smsThreads.count) SMS threads",
                        icon: "bubble.left.and.bubble.right.fill",
                        buttonTitle: "Open Messages & Alerts"
                    ) {
                        selectedSection = .messages
                    }

                    WorkflowCard(
                        number: "02",
                        title: "Move the thing you need",
                        subtitle: "Drag & drop files, browse \(conn.remotePhotos.count) photos, sync clipboard",
                        icon: "folder.fill.badge.plus",
                        buttonTitle: "Browse Files & Photos"
                    ) {
                        selectedSection = .files
                    }

                    WorkflowCard(
                        number: "03",
                        title: "Use the phone from your desk",
                        subtitle: "Screen mirroring, screen-off mode, webcam & media remote",
                        icon: "iphone.smartbatterycase.gen2",
                        buttonTitle: "Open Mirror & Camera"
                    ) {
                        selectedSection = .mirrorCamera
                    }
                }

                // Quick Media Remote & Quick Actions Row
                HStack(spacing: 16) {
                    // Media Remote Card
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Android Media Remote", systemImage: "music.note.list")
                            .font(.headline)
                        Text(conn.nowPlayingTitle)
                            .font(.title3.bold())
                            .lineLimit(1)
                        Text(conn.nowPlayingArtist)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        HStack(spacing: 12) {
                            Button { conn.sendMediaAction("PREV") } label: { Image(systemName: "backward.fill") }
                            Button { conn.sendMediaAction("PLAY_PAUSE") } label: { Image(systemName: "playpause.fill") }
                            Button { conn.sendMediaAction("NEXT") } label: { Image(systemName: "forward.fill") }
                            Divider().frame(height: 18)
                            Button { conn.sendMediaAction("VOL_DOWN") } label: { Image(systemName: "speaker.minus.fill") }
                            Button { conn.sendMediaAction("VOL_UP") } label: { Image(systemName: "speaker.plus.fill") }
                            Spacer()
                            Button("Ring Phone") {
                                conn.sendMediaAction("RING_PHONE")
                            }
                            .tint(.orange)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.05))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    // Latest Clipboard Card
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Label("Universal Clipboard", systemImage: "doc.on.clipboard")
                                .font(.headline)
                            Spacer()
                            Toggle("Sync On", isOn: $clip.isSyncEnabled)
                                .toggleStyle(.switch)
                                .controlSize(.small)
                        }
                        if let latest = clip.history.first {
                            Text(latest.content)
                                .font(.subheadline.monospaced())
                                .lineLimit(2)
                            Text("Copied on \(latest.source) • Ready to paste ⌘V")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("Copy text or links on Android or Mac to sync instantly.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.05))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding(24)
        }
    }
}

struct WorkflowCard: View {
    let number: String
    let title: String
    let subtitle: String
    let icon: String
    let buttonTitle: String
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(number)
                    .font(.caption.monospaced().bold())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.green.opacity(0.2))
                    .clipShape(Capsule())
                Spacer()
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(.green)
            }
            Text(title)
                .font(.headline)
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(minHeight: 34, alignment: .topLeading)
            Button(buttonTitle, action: action)
                .buttonStyle(.bordered)
        }
        .padding(16)
        .background(Color.primary.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Pairing QR Code & Guided Setup View
struct PairingQRSetupView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @ObservedObject var adb = ADBBridge.shared

    var qrPayloadString: String {
        "{\"host\":\"\(conn.localIPAddress)\",\"port\":\(conn.listenPort),\"token\":\"\(conn.pairingToken)\",\"macName\":\"\(Host.current().localizedName ?? "Mac")\"}"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Text("Pair once. Then your Android feels nearby.")
                    .font(.title.bold())
                Text("Scan this QR code inside the LinkToMac Android companion app, or plug in a USB cable.")
                    .foregroundStyle(.secondary)

                HStack(spacing: 32) {
                    // Generated QR Code
                    if let qrImg = generateQRCode(from: qrPayloadString) {
                        Image(nsImage: qrImg)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 210, height: 210)
                            .padding(16)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }

                    // Manual & USB Status Details
                    VStack(alignment: .leading, spacing: 14) {
                        GroupBox("01 • Local Wi-Fi Pairing Details") {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Mac IP Address:  \(conn.localIPAddress)")
                                    .font(.system(.body, design: .monospaced))
                                Text("Port:            \(conn.listenPort)")
                                    .font(.system(.body, design: .monospaced))
                                Text("6-Digit Token:   \(conn.pairingToken)")
                                    .font(.system(.body, design: .monospaced).bold())
                                    .foregroundStyle(.green)
                            }
                            .padding(8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        GroupBox("02 • High-Speed USB (ADB) Bridge") {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Circle()
                                        .fill(adb.isUSBDeviceConnected ? Color.green : Color.secondary)
                                        .frame(width: 10, height: 10)
                                    Text(adb.isUSBDeviceConnected ? "USB Phone Detected (\(adb.usbSerial))" : "No USB Android Detected")
                                        .font(.subheadline.bold())
                                }
                                Text("When connected via USB with USB Debugging enabled, port 54921 is automatically bridged for zero-latency transfers and screen-off mirroring.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Button("Refresh USB & Local Network") {
                                    conn.localIPAddress = ConnectionManager.detectLocalIPAddress() ?? "127.0.0.1"
                                    conn.startServer()
                                    adb.pollUSBDevices()
                                }
                            }
                            .padding(8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .frame(maxWidth: 420)
                }
            }
            .padding(32)
        }
    }

    private func generateQRCode(from string: String) -> NSImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let transformed = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImg = context.createCGImage(transformed, from: transformed.extent) else { return nil }
        return NSImage(cgImage: cgImg, size: NSSize(width: 220, height: 220))
    }
}







