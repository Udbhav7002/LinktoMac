import SwiftUI
import CoreImage.CIFilterBuiltins

// MARK: - Pairing Mode Enum
enum PairingMode: String, CaseIterable, Identifiable {
    case local = "Local Wi-Fi"
    case remote = "Remote Relay (Anywhere)"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .local:  return "wifi"
        case .remote: return "globe"
        }
    }
}

// MARK: - Pairing: QR card + connection facts + USB bridge
struct PairingQRSetupView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @ObservedObject var tunnel = PinggyTunnelManager.shared
    @ObservedObject var adb = ADBBridge.shared
    @LocalState private var selectedMode: PairingMode = .local

    private static let qrContext = CIContext()

    /// Remote relay is ready when the tunnel process is running, host is known, and a non-zero port is parsed.
    var isRemoteReady: Bool {
        tunnel.isRunning && (tunnel.publicPort ?? 0) > 0 && !(tunnel.publicHost ?? "").isEmpty
    }

    /// JSON Payload conforming strictly to LinkToMac pairing specification.
    var qrPayloadString: String {
        let macName = Host.current().localizedName ?? "Mac"
        let host: String
        let port: Int
        var mode: String? = nil

        switch selectedMode {
        case .local:
            host = conn.localIPAddress
            port = Int(conn.listenPort)
        case .remote:
            host = tunnel.publicHost ?? ""
            port = tunnel.publicPort ?? 0
            mode = "remote"
        }

        var dict: [String: Any] = [
            "host": host,
            "port": port,
            "token": conn.pairingToken,
            "macName": macName
        ]
        if let mode = mode {
            dict["mode"] = mode
        }

        if let data = try? JSONSerialization.data(withJSONObject: dict, options: [.sortedKeys]),
           let str = String(data: data, encoding: .utf8) {
            return str
        }

        // Fallback string interpolation if JSONSerialization fails
        if let mode = mode {
            return "{\"host\":\"\(host)\",\"port\":\(port),\"token\":\"\(conn.pairingToken)\",\"macName\":\"\(macName)\",\"mode\":\"\(mode)\"}"
        } else {
            return "{\"host\":\"\(host)\",\"port\":\(port),\"token\":\"\(conn.pairingToken)\",\"macName\":\"\(macName)\"}"
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: LS.xl) {
                SectionHeader(
                    title: "Pair your Android",
                    subtitle: "Scan once in the LinkToMac Android app — or plug in a USB cable."
                ) {}

                modeSelector
                    .frame(maxWidth: 940, alignment: .leading)
                    .padding(.horizontal, LS.xl)

                HStack(alignment: .top, spacing: LS.xl) {
                    qrCard
                    connectionFacts
                }
                .frame(maxWidth: 940, alignment: .leading)
                .padding(.horizontal, LS.xl)
            }
            .padding(.bottom, LS.xxl)
        }
        .background(LinkTheme.canvas)
    }

    // MARK: - Mode Selector Bar (Quiet Segmented Control)
    private var modeSelector: some View {
        HStack(spacing: 2) {
            ForEach(PairingMode.allCases) { mode in
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        selectedMode = mode
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: mode.icon)
                            .font(.system(size: 13, weight: .medium))
                        Text(mode.rawValue)
                            .font(.subheadline.weight(selectedMode == mode ? .semibold : .regular))
                    }
                    .foregroundStyle(selectedMode == mode ? LinkTheme.textPrimary : LinkTheme.textSecondary)
                    .padding(.horizontal, LS.m)
                    .padding(.vertical, 7)
                    .background(selectedMode == mode ? LinkTheme.surface : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: LR.control - 2))
                    .overlay(
                        RoundedRectangle(cornerRadius: LR.control - 2)
                            .strokeBorder(selectedMode == mode ? LinkTheme.hairline : Color.clear, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(LinkTheme.surfaceAlt)
        .clipShape(RoundedRectangle(cornerRadius: LR.control))
    }

    // MARK: - QR Card
    private var qrCard: some View {
        VStack(spacing: LS.m) {
            if selectedMode == .remote && !isRemoteReady {
                remoteUnavailableCard
            } else if let qrImg = generateQRCode(from: qrPayloadString) {
                Image(nsImage: qrImg)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 196, height: 196)
                    .padding(LS.m)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: LR.control))

                HStack(spacing: 6) {
                    StatusDot(state: selectedMode == .remote ? (isRemoteReady ? .connected : .waiting) : .connected)
                    Text(selectedMode == .remote ? "Scan from anywhere (WAN)" : "Scan on local Wi-Fi")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(LinkTheme.textSecondary)
                }
            } else {
                Text("Generating QR Code...")
                    .font(.caption)
                    .foregroundStyle(LinkTheme.textTertiary)
                    .frame(width: 196, height: 196)
            }

            Text(selectedMode == .remote
                 ? "Traverses cellular networks & firewalls via Pinggy reverse tunnel."
                 : "Scan with the LinkToMac Android app while on the same network.")
                .font(.caption2)
                .foregroundStyle(LinkTheme.textTertiary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 220)
        }
        .linkCard(padding: LS.l)
    }

    // MARK: - Remote Unavailable / Connecting Placeholder Card
    private var remoteUnavailableCard: some View {
        VStack(spacing: LS.m) {
            ZStack {
                RoundedRectangle(cornerRadius: LR.control)
                    .fill(LinkTheme.surfaceAlt)
                    .frame(width: 196, height: 196)

                if tunnel.isRunning && (tunnel.publicPort ?? 0) == 0 {
                    VStack(spacing: LS.s) {
                        ProgressView()
                            .controlSize(.regular)
                        Text("Negotiating tunnel...")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(LinkTheme.textSecondary)
                    }
                } else {
                    VStack(spacing: LS.s) {
                        Image(systemName: "globe.badge.chevron.backward")
                            .font(.system(size: 32, weight: .light))
                            .foregroundStyle(LinkTheme.textTertiary)
                        Text("Relay Offline")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(LinkTheme.textSecondary)
                        Button("Start Relay") {
                            tunnel.startTunnel()
                        }
                        .buttonStyle(.primary)
                    }
                    .padding(LS.m)
                }
            }
        }
    }

    // MARK: - Connection Facts
    private var connectionFacts: some View {
        VStack(alignment: .leading, spacing: LS.l) {
            if selectedMode == .local {
                localWiFiFacts
            } else {
                remoteRelayFacts
            }

            HairlineDivider()

            usbBridgeFacts
        }
        .linkCard(padding: LS.l)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Local Wi-Fi Facts
    private var localWiFiFacts: some View {
        VStack(alignment: .leading, spacing: LS.m) {
            Label("Local Wi-Fi", systemImage: "wifi")
                .font(.headline)
                .foregroundStyle(LinkTheme.textPrimary)
            MonoValue(label: "Mac address", value: conn.localIPAddress)
            MonoValue(label: "Port", value: "\(conn.listenPort)")
            MonoValue(label: "Pairing token", value: conn.pairingToken, prominent: true)
        }
    }

    // MARK: Remote Relay Facts
    private var remoteRelayFacts: some View {
        VStack(alignment: .leading, spacing: LS.m) {
            HStack {
                Label("Remote WAN Relay", systemImage: "globe")
                    .font(.headline)
                    .foregroundStyle(LinkTheme.textPrimary)
                Spacer()
                HStack(spacing: 6) {
                    StatusDot(state: isRemoteReady ? .connected : (tunnel.isRunning ? .waiting : .disconnected))
                    Text(isRemoteReady ? "Active" : (tunnel.isRunning ? "Connecting" : "Offline"))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(isRemoteReady ? LinkTheme.success : (tunnel.isRunning ? LinkTheme.warning : LinkTheme.textSecondary))
                }
            }

            if isRemoteReady {
                MonoValue(label: "Relay host", value: tunnel.publicHost ?? "a.pinggy.io")
                MonoValue(label: "Relay port", value: "\(tunnel.publicPort ?? 0)")
                MonoValue(label: "Forwarded port", value: "\(conn.listenPort) (local)")
                MonoValue(label: "Pairing token", value: conn.pairingToken, prominent: true)
            } else {
                Text(tunnel.statusMessage)
                    .font(.caption)
                    .foregroundStyle(LinkTheme.textSecondary)
            }

            HStack(spacing: LS.s) {
                if tunnel.isRunning {
                    Button("Stop Remote Relay") {
                        tunnel.stopTunnel()
                    }
                    .buttonStyle(.quietDanger)
                } else {
                    Button("Start Remote Relay") {
                        tunnel.startTunnel()
                    }
                    .buttonStyle(.primary)
                }
            }
            .padding(.top, 2)
        }
    }

    // MARK: USB Bridge Facts
    private var usbBridgeFacts: some View {
        VStack(alignment: .leading, spacing: LS.m) {
            Label("USB bridge", systemImage: "cable.connector")
                .font(.headline)
                .foregroundStyle(LinkTheme.textPrimary)

            HStack(spacing: LS.s) {
                StatusDot(state: adb.isUSBDeviceConnected ? .connected : .disconnected)
                Text(adb.isUSBDeviceConnected
                     ? "Phone detected"
                     : "No USB phone detected")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(LinkTheme.textPrimary)
                if adb.isUSBDeviceConnected {
                    Text(adb.usbSerial)
                        .font(.caption2.monospaced() as Font)
                        .foregroundStyle(LinkTheme.textTertiary)
                        .lineLimit(1)
                }
            }

            Text("With USB debugging on, port 54921 is bridged automatically for zero-latency transfers and screen-off mirroring.")
                .font(.caption)
                .foregroundStyle(LinkTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            Button("Refresh network & USB") {
                conn.localIPAddress = ConnectionManager.detectLocalIPAddress() ?? "127.0.0.1"
                conn.startServer()
                adb.pollUSBDevices()
            }
            .buttonStyle(.quiet)
        }
    }

    // MARK: QR generation via CIFilter
    private func generateQRCode(from string: String) -> NSImage? {
        guard !string.isEmpty else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let transformed = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImg = Self.qrContext.createCGImage(transformed, from: transformed.extent) else { return nil }
        return NSImage(cgImage: cgImg, size: NSSize(width: 220, height: 220))
    }
}

// MARK: - Floating incoming call HUD (Preserved Exactly)
struct IncomingCallHUDView: View {
    @ObservedObject var conn = ConnectionManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: LS.m) {
            HStack(spacing: LS.m) {
                Image(systemName: "phone.down.waves.left.and.right")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(LinkTheme.accent)
                VStack(alignment: .leading, spacing: 1) {
                    Text(conn.callState.state == "RINGING"
                         ? "Incoming call"
                         : "Active call · audio on phone")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(LinkTheme.textSecondary)
                    Text(conn.callState.contactName)
                        .font(.headline)
                        .foregroundStyle(LinkTheme.textPrimary)
                    Text(conn.callState.phoneNumber)
                        .font(.caption)
                        .foregroundStyle(LinkTheme.textSecondary)
                }
            }

            HStack(spacing: LS.s) {
                if conn.callState.state == "RINGING" {
                    Button("Answer") { conn.sendCallAction("ANSWER") }
                        .buttonStyle(.primary)
                    Button("Decline") { conn.sendCallAction("DECLINE") }
                        .buttonStyle(.dangerPrimary)
                    Button("Reply by SMS") {
                        conn.sendCallAction(
                            "DECLINE_WITH_SMS",
                            smsReply: "I'm at my desk right now — I'll call you back shortly."
                        )
                    }
                    .buttonStyle(.quiet)
                } else {
                    Button(conn.callState.isMuted ? "Unmute" : "Mute") {
                        conn.sendCallAction("MUTE")
                    }
                    .buttonStyle(.quiet)
                    Button("End call") { conn.sendCallAction("END") }
                        .buttonStyle(.dangerPrimary)
                }
            }
        }
        .padding(LS.l)
        .background(.ultraThickMaterial)   // system material — allowed (HIG overlay)
        .clipShape(RoundedRectangle(cornerRadius: LR.overlay))
        .overlay(
            RoundedRectangle(cornerRadius: LR.overlay)
                .strokeBorder(LinkTheme.hairline, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.15), radius: 24, y: 8)  // the ONE allowed shadow (floating overlay)
        .frame(minWidth: 300)
    }
}
