# Task 08 — macOS Mirror & Camera Workspace

> Prerequisite: Tasks 01–07 done. **Create a new file** `MirrorCameraView.swift` in
> `allcode/LinkToMac-macOS/Sources/LinkToMac/`. Struct name stays `MirrorAndCameraWorkspaceView`.

## Context

Two-pane `HSplitView`: control panel (320–360pt, GroupBoxes today) + live video canvas (black).
Redesign: control cards (`LinkCard`) with quiet buttons; the video canvas keeps a **pure black well**
(video needs it — this is not decoration); add a **status chip** overlay (mode label) and **floating
key controls** that appear over the video only while a stream is active — functional, not decorative.

Behavior contract (unchanged):
- Screen-off toggle binds `$conn.isScreenOffMirror`.
- "Start wireless mirror" → `conn.setStreamMode("MIRROR")`; scrcpy (only when
  `adb.isScrcpyAvailable && adb.isUSBDeviceConnected`) → `adb.launchNativeScrcpy(screenOff:stayAwake:true)`.
- Key buttons → `conn.sendRemoteKeyEvent(3 / 4 / 187 / 26)`.
- Cameras/mic → `conn.setStreamMode("WEBCAM_BACK" / "WEBCAM_FRONT" / "MIC_STREAM")`;
  Stop → `conn.setStreamMode("NONE")`.
- Tap on frame → normalized coords → `conn.sendRemoteTap(normalizedX:normalizedY:)` (unchanged math).

---

## File — `allcode/LinkToMac-macOS/Sources/LinkToMac/MirrorCameraView.swift` (create)

```swift
import SwiftUI

// MARK: - Screen mirroring, virtual webcam & microphone workspace
struct MirrorAndCameraWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @ObservedObject var adb = ADBBridge.shared

    var body: some View {
        HSplitView {
            controlPanel
            videoCanvas
        }
        .background(LinkTheme.canvas)
    }

    // MARK: Left — controls
    private var controlPanel: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: LS.l) {
                Text("Controls")
                    .font(.title2.bold())
                    .foregroundStyle(LinkTheme.textPrimary)
                    .padding(.horizontal, LS.xs)

                // Screen mirroring card
                VStack(alignment: .leading, spacing: LS.m) {
                    Label("Screen mirroring", systemImage: "video")
                        .font(.headline)
                        .foregroundStyle(LinkTheme.textPrimary)

                    Toggle("Turn phone screen off while mirroring", isOn: $conn.isScreenOffMirror)
                        .font(.subheadline)

                    HStack(spacing: LS.s) {
                        Button {
                            conn.setStreamMode("MIRROR")
                        } label: {
                            Label(conn.activeStreamMode == "MIRROR" ? "Mirroring" : "Start mirror",
                                  systemImage: "play")
                        }
                        .buttonStyle(.primary)

                        if adb.isScrcpyAvailable && adb.isUSBDeviceConnected {
                            Button("USB mirror") {
                                adb.launchNativeScrcpy(screenOff: conn.isScreenOffMirror, stayAwake: true)
                            }
                            .buttonStyle(.quiet)
                            .help("Launch scrcpy over the USB ADB bridge — lowest latency")
                        }
                    }

                    HStack(spacing: LS.xs) {
                        KeyButton(title: "Home", key: 3)
                        KeyButton(title: "Back", key: 4)
                        KeyButton(title: "Apps", key: 187)
                        KeyButton(title: "Lock", key: 26)
                    }
                }
                .linkCard()
                .frame(maxWidth: .infinity, alignment: .leading)

                // Camera & microphone card
                VStack(alignment: .leading, spacing: LS.m) {
                    Label("Camera & microphone", systemImage: "video.badge.ellipsis")
                        .font(.headline)
                        .foregroundStyle(LinkTheme.textPrimary)

                    Text("Use your phone's high-resolution camera or microphone as a desk device.")
                        .font(.caption)
                        .foregroundStyle(LinkTheme.textSecondary)

                    VStack(spacing: LS.s) {
                        streamOption(title: "Rear camera", mode: "WEBCAM_BACK", icon: "camera")
                        streamOption(title: "Front camera", mode: "WEBCAM_FRONT", icon: "person.crop.circle")
                        streamOption(title: "Microphone audio", mode: "MIC_STREAM", icon: "mic")
                    }

                    Button {
                        conn.setStreamMode("NONE")
                    } label: {
                        Label("Stop all streams", systemImage: "stop.fill")
                    }
                    .buttonStyle(.dangerPrimary)
                    .disabled(conn.activeStreamMode == "NONE")
                }
                .linkCard()
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(LS.xl)
        }
        .frame(minWidth: 300, maxWidth: 360)
        .background(LinkTheme.surface)
        .overlay(
            Rectangle().fill(LinkTheme.hairline).frame(width: 1)
        )
    }

    private func streamOption(title: String, mode: String, icon: String) -> some View {
        let isActive = conn.activeStreamMode == mode
        return Button {
            conn.setStreamMode(mode)
        } label: {
            HStack(spacing: LS.s) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .medium))
                Text(title)
                    .font(.subheadline.weight(.medium))
                Spacer()
                if isActive {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .semibold))
                }
            }
            .foregroundStyle(isActive ? LinkTheme.accent : LinkTheme.textPrimary)
            .padding(.horizontal, LS.m)
            .padding(.vertical, 8)
            .background(isActive ? LinkTheme.accent.opacity(0.10) : LinkTheme.surfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: LR.control))
            .overlay(
                RoundedRectangle(cornerRadius: LR.control)
                    .strokeBorder(isActive ? LinkTheme.accent.opacity(0.35) : .clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: Right — live video canvas
    private var videoCanvas: some View {
        ZStack(alignment: .top) {
            Color.black  // video well — functional, allowed

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
                .padding(LS.m)

                // Mode chip — where am I streaming from (functional label)
                VStack {
                    HStack(spacing: LS.s) {
                        StatusDot(state: .connected)
                        Text(streamModeLabel)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.white)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.black.opacity(0.55))
                    .clipShape(Capsule())
                    .padding(LS.m)

                    Spacer()

                    // Floating key controls over live video (functional overlay)
                    HStack(spacing: LS.xs) {
                        videoKey(icon: "house.fill", key: 3, label: "Home")
                        videoKey(icon: "chevron.backward", key: 4, label: "Back")
                        videoKey(icon: "square.on.square", key: 187, label: "Apps")
                        videoKey(icon: "lock.fill", key: 26, label: "Lock")
                    }
                    .padding(LS.s)
                    .background(Color.black.opacity(0.55))
                    .clipShape(Capsule())
                    .padding(.bottom, LS.l)
                }
            } else {
                // The video well is ALWAYS black (both themes) — use white-on-black grays here,
                // NOT the theme tokens (tokens are for light/dark surfaces).
                VStack(spacing: LS.m) {
                    Image(systemName: "video")
                        .font(.system(size: 36, weight: .light))
                        .foregroundStyle(Color(white: 0.55))
                    Text("No live stream")
                        .font(.headline)
                        .foregroundStyle(Color(white: 0.8))
                    Text("Start mirroring or a camera stream from the controls. Click the video to tap your phone.")
                        .font(.subheadline)
                        .foregroundStyle(Color(white: 0.55))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 380)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(minWidth: 480)
    }

    private var streamModeLabel: String {
        switch conn.activeStreamMode {
        case "MIRROR":        return "Screen mirror"
        case "WEBCAM_BACK":   return "Rear camera"
        case "WEBCAM_FRONT":  return "Front camera"
        case "MIC_STREAM":    return "Microphone"
        default:              return "Streaming"
        }
    }
}

// MARK: - Quiet key button for the control panel
struct KeyButton: View {
    let title: String
    let key: Int
    @ObservedObject private var conn = ConnectionManager.shared

    var body: some View {
        Button(title) { conn.sendRemoteKeyEvent(key) }
            .buttonStyle(.quiet)
            .controlSize(.small)
    }
}

// MARK: - Small translucent control over live video
struct VideoKeyButton: View {
    let icon: String
    let key: Int
    let label: String
    @ObservedObject private var conn = ConnectionManager.shared

    var body: some View {
        Button {
            conn.sendRemoteKeyEvent(key)
        } label: {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.white)
                .frame(width: 30, height: 30)
        }
        .buttonStyle(.plain)
        .help(label)
        .accessibilityLabel(label)
    }
}

private extension MirrorAndCameraWorkspaceView {
    func videoKey(icon: String, key: Int, label: String) -> some View {
        VideoKeyButton(icon: icon, key: key, label: label)
    }
}
```

---

## Acceptance Checklist

- [ ] `GroupBox` completely replaced by `LinkCard`; no default macOS chrome.
- [ ] Active stream option shows accent selection state (10% fill + check) — quiet, not green.
- [ ] "Stop all streams" is `dangerPrimary` and disabled when already stopped.
- [ ] Video tap gesture math identical (`event.location / geo.size`, clamped 0–1).
- [ ] Video well is pure black; mode chip + floating keys are 55% black capsules (functional overlay).
- [ ] scrcpy button appears ONLY under `isScrcpyAvailable && isUSBDeviceConnected` (same condition).
- [ ] No animation added to video rendering (frames update as fast as the stream provides).
