import SwiftUI
import UniformTypeIdentifiers

// MARK: - 02 File Browser & Drag-and-Drop Transfer Workspace
struct FilesWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @LocalState private var isDropTargeted: Bool = false

    let quickBookmarks = [
        ("Downloads", "/sdcard/Download"),
        ("Camera (DCIM)", "/sdcard/DCIM/Camera"),
        ("Pictures", "/sdcard/Pictures"),
        ("Movies", "/sdcard/Movies"),
        ("Documents", "/sdcard/Documents"),
        ("Root /sdcard", "/sdcard")
    ]

    var body: some View {
        HSplitView {
            // Left Pane: Android Storage Browser
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Android Storage")
                        .font(.title3.bold())
                    Spacer()
                    Button("Send File from Mac...") {
                        pickMacFilesToSend()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)
                    Button {
                        conn.requestDirectoryListing(path: conn.currentDirectoryPath)
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)

                // Quick Folder Bookmarks
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(quickBookmarks, id: \.1) { name, path in
                            Button(name) {
                                conn.requestDirectoryListing(path: path)
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                    }
                    .padding(.horizontal, 16)
                }

                // Current Path Bar
                HStack {
                    Button {
                        let parent = (conn.currentDirectoryPath as NSString).deletingLastPathComponent
                        if !parent.isEmpty {
                            conn.requestDirectoryListing(path: parent)
                        }
                    } label: {
                        Image(systemName: "chevron.left")
                    }
                    Text(conn.currentDirectoryPath)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                    Spacer()
                }
                .padding(.horizontal, 16)

                List(conn.remoteFiles) { file in
                    HStack {
                        Image(systemName: file.isDirectory ? "folder.fill" : "doc.fill")
                            .foregroundStyle(file.isDirectory ? .yellow : .blue)
                        VStack(alignment: .leading) {
                            Text(file.name)
                                .font(.body)
                            if !file.isDirectory {
                                Text(ByteCountFormatter.string(fromByteCount: file.sizeBytes, countStyle: .file))
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        if file.isDirectory {
                            Button("Open") {
                                conn.requestDirectoryListing(path: file.path)
                            }
                            .controlSize(.small)
                        } else {
                            Button("Download to Finder") {
                                conn.pullRemoteFileToMac(remotePath: file.path, fileName: file.name, revealInFinder: true)
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                    }
                }
            }
            .frame(minWidth: 420)
            .onDrop(of: [.fileURL], isTargeted: $isDropTargeted) { providers in
                handleDroppedMacFiles(providers: providers)
                return true
            }
            .overlay {
                if isDropTargeted {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.green, lineWidth: 3)
                        .background(Color.green.opacity(0.12))
                        .overlay {
                            Label("Drop files to send to \(conn.currentDirectoryPath)", systemImage: "arrow.down.doc.fill")
                                .font(.title2.bold())
                        }
                }
            }

            // Right Pane: Active & Recent Transfers Queue
            VStack(alignment: .leading, spacing: 10) {
                Text("Transfers Queue")
                    .font(.headline)
                    .padding(.top, 14)
                    .padding(.horizontal, 14)

                if conn.transfers.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "arrow.left.arrow.right.square")
                            .font(.system(size: 34))
                            .foregroundStyle(.secondary)
                        Text("Drag files from Finder here to send to Android")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(conn.transfers) { item in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(item.fileName)
                                    .font(.subheadline.bold())
                                    .lineLimit(1)
                                Spacer()
                                Text(item.transport)
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.green.opacity(0.2))
                                    .clipShape(Capsule())
                            }
                            ProgressView(value: item.progress)
                                .tint(.green)
                            HStack {
                                Text(item.direction)
                                Spacer()
                                Text(item.isCompleted ? "Completed ✓" : "\(Int(item.progress * 100))%")
                            }
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .frame(minWidth: 260, maxWidth: 320)
        }
    }

    private func pickMacFilesToSend() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        if panel.runModal() == .OK {
            for url in panel.urls {
                conn.sendFileToAndroid(fileURL: url, destinationFolder: conn.currentDirectoryPath)
            }
        }
    }

    private func handleDroppedMacFiles(providers: [NSItemProvider]) {
        for provider in providers {
            provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { item, _ in
                if let data = item as? Data,
                   let url = URL(dataRepresentation: data, relativeTo: nil) {
                    Task { @MainActor in
                        ConnectionManager.shared.sendFileToAndroid(
                            fileURL: url,
                            destinationFolder: ConnectionManager.shared.currentDirectoryPath
                        )
                    }
                }
            }
        }
    }
}

// MARK: - 02 Photos Browser Workspace
struct PhotosWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Android Photos & Gallery")
                    .font(.title2.bold())
                Spacer()
                Button {
                    conn.requestPhotosList()
                } label: {
                    Label("Refresh Gallery", systemImage: "arrow.clockwise")
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)

            if conn.remotePhotos.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "photo.stack")
                        .font(.system(size: 42))
                        .foregroundStyle(.secondary)
                    Text("No photos loaded yet")
                    Button("Load Recent Android Photos") {
                        conn.requestPhotosList()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 14)], spacing: 14) {
                        ForEach(conn.remotePhotos) { photo in
                            VStack(alignment: .leading, spacing: 6) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(Color.primary.opacity(0.08))
                                        .frame(height: 120)

                                    if let b64 = photo.thumbnailBase64,
                                       let data = Data(base64Encoded: b64),
                                       let img = NSImage(data: data) {
                                        Image(nsImage: img)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(height: 120)
                                            .clipped()
                                            .clipShape(RoundedRectangle(cornerRadius: 10))
                                    } else {
                                        Image(systemName: photo.isVideo ? "video.fill" : "photo.fill")
                                            .font(.largeTitle)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Text(photo.name)
                                    .font(.caption.bold())
                                    .lineLimit(1)
                                Button("Save to Finder") {
                                    conn.pullRemoteFileToMac(remotePath: photo.path, fileName: photo.name, revealInFinder: true)
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                            }
                            .padding(8)
                            .background(Color.primary.opacity(0.04))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                    }
                    .padding(20)
                }
            }
        }
    }
}

// MARK: - 02 Universal Clipboard & URL Handoff Workspace
struct ClipboardWorkspaceView: View {
    @ObservedObject var clip = ClipboardSyncManager.shared
    @LocalState private var handoffURLInput: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Universal Clipboard & URL Handoff")
                        .font(.title2.bold())
                    Text("Copy on Android, paste on Mac (⌘V) — and vice versa.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Toggle("Clipboard Sync", isOn: $clip.isSyncEnabled)
                    .toggleStyle(.switch)
                Toggle("Ignore Password Managers", isOn: $clip.ignoreConcealedPasswords)
                    .toggleStyle(.checkbox)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)

            // URL Handoff Bar
            HStack {
                TextField("https://example.com — Open URL directly on Android phone...", text: $handoffURLInput)
                    .textFieldStyle(.roundedBorder)
                Button("Send URL to Phone ↗") {
                    guard !handoffURLInput.isEmpty else { return }
                    clip.handoffURLToAndroid(handoffURLInput)
                    handoffURLInput = ""
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)
            }
            .padding(.horizontal, 20)

            List(clip.history) { item in
                HStack {
                    Image(systemName: item.isUrl ? "link.circle.fill" : "doc.on.clipboard")
                        .foregroundStyle(item.isUrl ? .blue : .green)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.content)
                            .font(.body.monospaced())
                            .lineLimit(2)
                        Text("Copied on \(item.source) • \(item.timestamp.formatted(date: .omitted, time: .standard))")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    if item.isUrl, let url = URL(string: item.content) {
                        Button("Open on Mac") {
                            NSWorkspace.shared.open(url)
                        }
                        Button("Open on Phone") {
                            clip.handoffURLToAndroid(item.content)
                        }
                    }
                    Button("Copy ⌘C") {
                        clip.copyItemToMac(item)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }
}

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
