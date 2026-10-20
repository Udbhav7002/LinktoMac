# Task 09 — macOS Pairing QR & Incoming Call HUD

> Prerequisite: Tasks 01–08 done. **Create a new file** `PairingCallHUDView.swift` in
> `allcode/LinkToMac-macOS/Sources/LinkToMac/`. Contains `PairingQRSetupView` + `IncomingCallHUDView`.
>
> **Final step of the macOS migration:** if any remnants of the old `FeatureViews.swift` still exist,
> delete the file — every workspace now lives in its own file.

## Context

**PairingQRSetupView** shows the QR payload card (host/port/token JSON) + manual Wi-Fi details + USB
bridge status. **IncomingCallHUDView** floats over the top-trailing corner when the phone rings.
Today: white GroupBoxes with "01 •" numbering, green token text, green Answer button.

Redesign: a **single centered pairing card** — QR on white (scan contrast, functional), token shown
large in mono accent, connection facts as `MonoValue` rows, USB status via `StatusDot`. The Call HUD
keeps `.ultraThickMaterial` (system material = allowed) with a refined shadow, semantic Answer/Decline.

Behavior contract (unchanged):
- QR payload = `{"host":…,"port":…,"token":…,"macName":…}` via `CIFilter.qrCodeGenerator`, level "M", 10× scale.
- "Refresh" → `conn.localIPAddress = ConnectionManager.detectLocalIPAddress() ?? "127.0.0.1"`,
  `conn.startServer()`, `adb.pollUSBDevices()`.
- HUD buttons → `conn.sendCallAction("ANSWER" / "DECLINE" / "DECLINE_WITH_SMS" / "MUTE" / "END")`
  with the same canned SMS reply text.

---

## File — `allcode/LinkToMac-macOS/Sources/LinkToMac/PairingCallHUDView.swift` (create)

```swift
import SwiftUI
import CoreImage.CIFilterBuiltins

// MARK: - Pairing: QR card + connection facts + USB bridge
struct PairingQRSetupView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @ObservedObject var adb = ADBBridge.shared

    var qrPayloadString: String {
        "{\"host\":\"\(conn.localIPAddress)\",\"port\":\(conn.listenPort),\"token\":\"\(conn.pairingToken)\",\"macName\":\"\(Host.current().localizedName ?? "Mac")\"}"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: LS.xl) {
                SectionHeader(title: "Pair your Android",
                              subtitle: "Scan once in the LinkToMac Android app — or plug in a USB cable.") {}

                HStack(alignment: .top, spacing: LS.xl) {
                    qrCard
                    connectionFacts
                }
                .frame(maxWidth: 940, alignment: .leading)
            }
            .padding(.bottom, LS.xxl)
        }
        .background(LinkTheme.canvas)
    }

    // MARK: QR — white backing is functional (scanner contrast), not decoration
    private var qrCard: some View {
        VStack(spacing: LS.m) {
            if let qrImg = generateQRCode(from: qrPayloadString) {
                Image(nsImage: qrImg)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 196, height: 196)
                    .padding(LS.m)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: LR.control))
            }
            Text("Scan in the Android app")
                .font(.caption)
                .foregroundStyle(LinkTheme.textTertiary)
        }
        .linkCard(padding: LS.l)
    }

    // MARK: Facts — IP, port, token, USB bridge
    private var connectionFacts: some View {
        VStack(alignment: .leading, spacing: LS.l) {
            VStack(alignment: .leading, spacing: LS.m) {
                Label("Local Wi-Fi", systemImage: "wifi")
                    .font(.headline)
                    .foregroundStyle(LinkTheme.textPrimary)
                MonoValue(label: "Mac address", value: conn.localIPAddress)
                MonoValue(label: "Port", value: "\(conn.listenPort)")
                MonoValue(label: "Pairing token", value: conn.pairingToken, prominent: true)
            }

            HairlineDivider()

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
        .linkCard(padding: LS.l)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: QR generation (identical to original)
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

// MARK: - Floating incoming call HUD
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
```

---

## Cleanup — end of macOS migration

1. Verify `FeatureViews.swift` no longer exists (all views migrated to their own files).
2. `swift build` — zero errors, zero warnings about duplicate structs.
3. Grep the target for forbidden patterns:

```bash
cd allcode/LinkToMac-macOS
grep -rn "Color.green\|\.tint(\.green)\|0xFF4ADE80\|LinearGradient\|GroupBox(" Sources/ \
  && echo "VIOLATIONS FOUND" || echo "clean"
grep -rn "01 •\|02 •\|03 •\|v1.61" Sources/ && echo "stale copy found" || echo "clean"
```

## Acceptance Checklist

- [ ] QR scan still works end-to-end with the Android app (payload string byte-identical).
- [ ] Token renders in mono accent (`MonoValue` prominent) — selectable via `textSelection`.
- [ ] "01 •" / "02 •" numbered GroupBoxes are gone; facts are quiet `MonoValue` rows.
- [ ] Answer = solid accent; Decline/End = danger solid; SMS/Mute = quiet. No green Answer button.
- [ ] HUD: material + hairline + the single allowed soft shadow; entrance spring unchanged (from shell).
- [ ] `swift build` clean; grep checks above report "clean".
