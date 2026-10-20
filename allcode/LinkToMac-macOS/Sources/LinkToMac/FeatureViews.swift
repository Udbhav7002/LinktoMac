import SwiftUI
import UniformTypeIdentifiers

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
