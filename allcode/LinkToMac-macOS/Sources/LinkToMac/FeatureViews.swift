import SwiftUI
import UniformTypeIdentifiers

// MARK: - 03 Screen Mirroring, Virtual Webcam & Microphone Workspace
struct MirrorAndCameraWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @ObservedObject var adb = ADBBridge.shared

    var body: some View {
        HSplitView {
            // Left Control Panel
            VStack(alignment: .leading, spacing: 16) {
                Text("Desk Controls")
                    .font(.title2.bold())

                GroupBox("Screen Mirroring") {
                    VStack(alignment: .leading, spacing: 10) {
                        Toggle("Turn Android Screen Off while Mirroring", isOn: $conn.isScreenOffMirror)
                        HStack {
                            Button("Start Wireless/Socket Mirror") {
                                conn.setStreamMode("MIRROR")
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.green)

                            if adb.isScrcpyAvailable && adb.isUSBDeviceConnected {
                                Button("Launch Pro USB Mirror (scrcpy)") {
                                    adb.launchNativeScrcpy(screenOff: conn.isScreenOffMirror, stayAwake: true)
                                }
                            }
                        }
                        HStack(spacing: 8) {
                            Button("Home") { conn.sendRemoteKeyEvent(3) }
                            Button("Back") { conn.sendRemoteKeyEvent(4) }
                            Button("App Switcher") { conn.sendRemoteKeyEvent(187) }
                            Button("Lock/Wake") { conn.sendRemoteKeyEvent(26) }
                        }
                        .controlSize(.small)
                    }
                    .padding(8)
                }

                GroupBox("Android Camera & Microphone Stream") {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Use your Android phone's high-res camera and microphone right from your desk.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        HStack {
                            Button("Rear Camera") {
                                conn.setStreamMode("WEBCAM_BACK")
                            }
                            Button("Front Camera") {
                                conn.setStreamMode("WEBCAM_FRONT")
                            }
                            Button("Stream Mic Audio") {
                                conn.setStreamMode("MIC_STREAM")
                            }
                        }
                        Button("Stop All Streams") {
                            conn.setStreamMode("NONE")
                        }
                        .tint(.red)
                    }
                    .padding(8)
                }

                Spacer()
            }
            .padding(20)
            .frame(minWidth: 320, maxWidth: 360)

            // Right Live Video Canvas (Interactive click-to-tap on phone!)
            VStack {
                if let frame = conn.liveVideoFrame {
                    GeometryReader { geo in
                        Image(nsImage: frame)
                            .resizable()
                            .scaledToFit()
                            .frame(width: geo.size.width, height: geo.size.height)
                            .gesture(
                                SpatialTapGesture()
                                    .onEnded { event in
                                        let normX = max(0, min(1, event.location.x / max(1, geo.size.width)))
                                        let normY = max(0, min(1, event.location.y / max(1, geo.size.height)))
                                        conn.sendRemoteTap(normalizedX: normX, normalizedY: normY)
                                    }
                            )
                    }
                    .padding(12)
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "iphone.gen3.radiowaves.left.and.right")
                            .font(.system(size: 48))
                            .foregroundStyle(.secondary)
                        Text("Live Screen Mirror / Camera Feed")
                            .font(.headline)
                        Text("Click 'Start Wireless/Socket Mirror' or 'Rear Camera' on the left to stream live video.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .background(Color.black.opacity(0.85))
        }
    }
}

// MARK: - Floating Incoming Call HUD Overlay
struct IncomingCallHUDView: View {
    @ObservedObject var conn = ConnectionManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "phone.down.waves.left.and.right")
                    .font(.title)
                    .foregroundStyle(.green)
                VStack(alignment: .leading, spacing: 2) {
                    Text(conn.callState.state == "RINGING" ? "Incoming Android Call" : "Active Cellular Call (Audio on Phone)")
                        .font(.caption.bold())
                        .foregroundStyle(.green)
                    Text(conn.callState.contactName)
                        .font(.headline)
                    Text(conn.callState.phoneNumber)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            HStack(spacing: 10) {
                if conn.callState.state == "RINGING" {
                    Button("Answer") {
                        conn.sendCallAction("ANSWER")
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)

                    Button("Decline") {
                        conn.sendCallAction("DECLINE")
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)

                    Button("SMS: In a meeting") {
                        conn.sendCallAction("DECLINE_WITH_SMS", smsReply: "I'm at my desk right now — I'll call you back shortly.")
                    }
                    .buttonStyle(.bordered)
                } else {
                    Button(conn.callState.isMuted ? "Unmute" : "Mute") {
                        conn.sendCallAction("MUTE")
                    }
                    .buttonStyle(.bordered)

                    Button("End Call") {
                        conn.sendCallAction("END")
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                }
            }
        }
        .padding(16)
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(radius: 16)
    }
}
