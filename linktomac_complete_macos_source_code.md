# LinkToMac — Complete macOS Source Code (`allcode/LinkToMac-macOS`)

Every file below belongs inside the codebase. Run `python3 stitch_allcode.py` from `LINKTOMAC_MASTER_PLAN.md` to extract them automatically.

---

### FILE: allcode/LinkToMac-macOS/Package.swift
```swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LinkToMac",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "LinkToMac", targets: ["LinkToMac"])
    ],
    targets: [
        .executableTarget(
            name: "LinkToMac",
            path: "Sources/LinkToMac"
        )
    ]
)






```

---

### FILE: allcode/LinkToMac-macOS/Info.plist
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>LinkToMac</string>
    <key>CFBundleIdentifier</key>
    <string>com.linktomac.mac</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>LinkToMac</string>
    <key>CFBundleDisplayName</key>
    <string>LinkToMac</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.61</string>
    <key>CFBundleVersion</key>
    <string>161</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSLocalNetworkUsageDescription</key>
    <string>LinkToMac connects directly to your Android phone over local Wi-Fi and USB without cloud servers.</string>
    <key>NSBonjourServices</key>
    <array>
        <string>_linktomac._tcp</string>
    </array>
</dict>
</plist>






```

---

### FILE: allcode/LinkToMac-macOS/build_mac_app.sh
```bash
#!/usr/bin/env bash
set -e

echo "=========================================="
echo " Building LinkToMac for macOS (v1.61)..."
echo "=========================================="

swift build -c release

APP_DIR="build/LinkToMac.app"
CONTENTS_DIR="$APP_DIR/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"
RESOURCES_DIR="$CONTENTS_DIR/Resources"

rm -rf "$APP_DIR"
mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"

cp ".build/release/LinkToMac" "$MACOS_DIR/LinkToMac"
cp "Info.plist" "$CONTENTS_DIR/Info.plist"
chmod +x "$MACOS_DIR/LinkToMac"

# Ad-hoc sign so macOS Local Network & Notification permissions work smoothly
codesign --force --deep --sign - "$APP_DIR"

echo "=========================================="
echo " SUCCESS: Built $APP_DIR"
echo " Run with: open $APP_DIR"
echo "=========================================="






```

---

### FILE: allcode/LinkToMac-macOS/Sources/LinkToMac/ProtocolModels.swift
```swift
import Foundation

// MARK: - Binary Wire Frame Constants
enum WireFrameType: UInt8 {
    case jsonControl = 0x01
    case fileChunk   = 0x02
    case videoFrame  = 0x03
    case audioFrame  = 0x04
}

// MARK: - Top-Level JSON Envelope
struct LinkEnvelope: Codable {
    var type: String
    var timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    var payload: [String: String] = [:]
    var notifications: [AndroidNotificationItem]? = nil
    var smsThreads: [SmsThreadItem]? = nil
    var contacts: [AndroidContactItem]? = nil
    var files: [RemoteFileItem]? = nil
    var photos: [RemotePhotoItem]? = nil
}

// MARK: - Feature Models
struct AndroidNotificationItem: Identifiable, Codable, Hashable {
    var id: String
    var packageName: String
    var appName: String
    var title: String
    var body: String
    var timestamp: Int64
    var canReply: Bool
    var otpCode: String?
    var replySentText: String?
}

struct SmsMessageItem: Identifiable, Codable, Hashable {
    var id: String
    var address: String
    var body: String
    var date: Int64
    var isIncoming: Bool
}

struct SmsThreadItem: Identifiable, Codable, Hashable {
    var id: String // phone number / address
    var contactName: String
    var address: String
    var lastMessage: String
    var timestamp: Int64
    var messages: [SmsMessageItem]
}

struct AndroidContactItem: Identifiable, Codable, Hashable {
    var id: String
    var name: String
    var phoneNumber: String
}

struct RemoteFileItem: Identifiable, Codable, Hashable {
    var id: String { path }
    var name: String
    var path: String
    var sizeBytes: Int64
    var isDirectory: Bool
    var lastModified: Int64
}

struct RemotePhotoItem: Identifiable, Codable, Hashable {
    var id: String
    var name: String
    var path: String
    var dateTaken: Int64
    var sizeBytes: Int64
    var isVideo: Bool
    var thumbnailBase64: String?
}

struct ClipboardHistoryItem: Identifiable, Hashable {
    let id = UUID()
    let content: String
    let isUrl: Bool
    let source: String // "Android" or "This Mac"
    let timestamp: Date
}

struct ActiveTransferItem: Identifiable {
    let id: String
    var fileName: String
    var direction: String // "Sending to Android" or "Receiving from Android"
    var totalBytes: Int64
    var transferredBytes: Int64
    var transport: String
    var isCompleted: Bool = false

    var progress: Double {
        guard totalBytes > 0 else { return 0 }
        return min(1.0, Double(transferredBytes) / Double(totalBytes))
    }
}

struct IncomingCallState: Equatable {
    var isActive: Bool = false
    var state: String = "IDLE" // "RINGING", "OFFHOOK", "IDLE"
    var phoneNumber: String = ""
    var contactName: String = ""
    var isMuted: Bool = false
}

// MARK: - Wire Framing Helper
enum WireFramer {
    static func packJSON(_ envelope: LinkEnvelope) -> Data? {
        guard let jsonData = try? JSONEncoder().encode(envelope) else { return nil }
        return packFrame(type: .jsonControl, payload: jsonData)
    }

    static func packFrame(type: WireFrameType, payload: Data) -> Data {
        var lengthBE = UInt32(payload.count).bigEndian
        var header = Data(bytes: &lengthBE, count: 4)
        header.append(type.rawValue)
        header.append(payload)
        return header
    }
}






```

---

### FILE: allcode/LinkToMac-macOS/Sources/LinkToMac/LocalState.swift
```swift
import SwiftUI

@propertyWrapper
public struct LocalState<Value>: DynamicProperty {
    @StateObject private var container: Container

    final class Container: ObservableObject {
        @Published var value: Value
        init(_ value: Value) { self.value = value }
    }

    public init(wrappedValue: Value) {
        _container = StateObject(wrappedValue: Container(wrappedValue))
    }

    public init() where Value: ExpressibleByNilLiteral {
        self.init(wrappedValue: nil)
    }

    public var wrappedValue: Value {
        get { container.value }
        nonmutating set { container.value = newValue }
    }

    public var projectedValue: Binding<Value> {
        Binding(
            get: { self.container.value },
            set: { self.container.value = $0 }
        )
    }
}






```

---

### FILE: allcode/LinkToMac-macOS/Sources/LinkToMac/ConnectionManager.swift
```swift
import Foundation
import Network
import AppKit
import UserNotifications
import AVFoundation

@MainActor
final class ConnectionManager: ObservableObject {
    static let shared = ConnectionManager()

    // Connection State
    @Published var isConnected: Bool = false
    @Published var connectedDeviceName: String = "No Android Connected"
    @Published var connectionTransport: String = "Waiting..." // "USB" or "Local Wi-Fi"
    @Published var batteryLevel: Int = 0
    @Published var isCharging: Bool = false
    @Published var ringerMode: String = "Normal"
    @Published var localIPAddress: String = "127.0.0.1"
    @Published var listenPort: UInt16 = 54921
    @Published var pairingToken: String = ""

    // Feature State
    @Published var notifications: [AndroidNotificationItem] = []
    @Published var mutedApps: Set<String> = []
    @Published var smsThreads: [SmsThreadItem] = []
    @Published var contacts: [AndroidContactItem] = []
    @Published var currentDirectoryPath: String = "/sdcard/Download"
    @Published var remoteFiles: [RemoteFileItem] = []
    @Published var remotePhotos: [RemotePhotoItem] = []
    @Published var transfers: [ActiveTransferItem] = []
    @Published var callState: IncomingCallState = IncomingCallState()

    // Media & Live Stream State
    @Published var nowPlayingTitle: String = "Nothing Playing"
    @Published var nowPlayingArtist: String = "Android Media Remote"
    @Published var liveVideoFrame: NSImage? = nil
    @Published var activeStreamMode: String = "NONE" // "NONE", "MIRROR", "WEBCAM_BACK", "WEBCAM_FRONT"
    @Published var isScreenOffMirror: Bool = false
    @Published var androidScreenWidth: Double = 1080.0
    @Published var androidScreenHeight: Double = 2400.0
    @Published var deviceScreenWidth: Int = 1080
    @Published var deviceScreenHeight: Int = 2400

    private var listener: NWListener?
    private var activeConnection: NWConnection?
    private var receiveBuffer = Data()
    private var incomingFileHandles: [String: (url: URL, handle: FileHandle, expectedBytes: Int64)] = [:]

    // Audio player for live microphone/cast PCM frames
    private let audioEngine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private var audioConfigured = false

    private init() {
        self.localIPAddress = Self.detectLocalIPAddress() ?? "127.0.0.1"
        if let savedToken = UserDefaults.standard.string(forKey: "LinkToMacPairingToken"), !savedToken.isEmpty {
            self.pairingToken = savedToken
        } else {
            let newToken = String(format: "%06d", Int.random(in: 100000...999999))
            UserDefaults.standard.set(newToken, forKey: "LinkToMacPairingToken")
            self.pairingToken = newToken
        }

        requestMacNotificationPermission()
        startServer()
        observeMacWake()
    }

    // MARK: - Server & Bonjour mDNS
    func startServer() {
        listener?.cancel()
        do {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            let nwPort = NWEndpoint.Port(rawValue: listenPort)!
            let newListener = try NWListener(using: params, on: nwPort)

            // Advertise Bonjour _linktomac._tcp so Android discovers Mac automatically
            let hostName = Host.current().localizedName ?? "Mac"
            newListener.service = NWListener.Service(
                name: "LinkToMac-\(hostName)",
                type: "_linktomac._tcp"
            )

            newListener.newConnectionHandler = { [weak self] connection in
                Task { @MainActor in
                    self?.acceptConnection(connection)
                }
            }

            newListener.start(queue: .main)
            self.listener = newListener
        } catch {
            print("[LinkToMac] Failed to start listener: \(error)")
        }
    }

    private func acceptConnection(_ connection: NWConnection) {
        activeConnection?.cancel()
        activeConnection = connection
        receiveBuffer.removeAll()

        connection.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                guard let self = self else { return }
                switch state {
                case .ready:
                    self.isConnected = true
                    self.sendHello()
                    self.readNextChunk(from: connection)
                case .failed, .cancelled:
                    if self.activeConnection === connection {
                        self.isConnected = false
                        self.connectionTransport = "Disconnected"
                    }
                default:
                    break
                }
            }
        }
        connection.start(queue: .main)
    }

    private func readNextChunk(from connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 131072) { [weak self] data, _, isComplete, error in
            Task { @MainActor in
                guard let self = self else { return }
                if let data = data, !data.isEmpty {
                    self.receiveBuffer.append(data)
                    self.processReceiveBuffer()
                }
                if isComplete || error != nil {
                    if self.activeConnection === connection {
                        self.isConnected = false
                    }
                    return
                }
                self.readNextChunk(from: connection)
            }
        }
    }

    private func processReceiveBuffer() {
        while receiveBuffer.count >= 5 {
            let lengthBytes = receiveBuffer.subdata(in: 0..<4)
            let length = lengthBytes.withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
            let frameTypeByte = receiveBuffer[4]
            let totalFrameSize = 5 + Int(length)

            guard receiveBuffer.count >= totalFrameSize else { break }
            let payload = receiveBuffer.subdata(in: 5..<totalFrameSize)
            receiveBuffer.removeSubrange(0..<totalFrameSize)

            guard let frameType = WireFrameType(rawValue: frameTypeByte) else { continue }
            switch frameType {
            case .jsonControl:
                handleJSONPayload(payload)
            case .fileChunk:
                handleBinaryFileChunk(payload)
            case .videoFrame:
                if let img = NSImage(data: payload) {
                    self.liveVideoFrame = img
                }
            case .audioFrame:
                playPCMFrame(payload)
            }
        }
    }

    // MARK: - Handle Incoming JSON Commands
    private func handleJSONPayload(_ data: Data) {
        guard let env = try? JSONDecoder().decode(LinkEnvelope.self, from: data) else { return }

        switch env.type {
        case "HELLO", "STATUS_UPDATE":
            if let name = env.payload["deviceName"] { self.connectedDeviceName = name }
            if let batStr = env.payload["battery"], let bat = Int(batStr) { self.batteryLevel = bat }
            if let chg = env.payload["charging"] { self.isCharging = (chg == "true") }
            if let trans = env.payload["transport"] {
                self.connectionTransport = ADBBridge.shared.isUSBDeviceConnected ? "USB" : trans
            }
            if let ringer = env.payload["ringerMode"] { self.ringerMode = ringer }
            if let track = env.payload["mediaTitle"], !track.isEmpty {
                self.nowPlayingTitle = track
                self.nowPlayingArtist = env.payload["mediaArtist"] ?? "Android Media"
            }
            if let wStr = env.payload["screenWidth"], let w = Double(wStr), w > 0 {
                self.androidScreenWidth = w
                self.deviceScreenWidth = Int(w)
                ADBBridge.shared.deviceScreenWidth = Int(w)
            }
            if let hStr = env.payload["screenHeight"], let h = Double(hStr), h > 0 {
                self.androidScreenHeight = h
                self.deviceScreenHeight = Int(h)
                ADBBridge.shared.deviceScreenHeight = Int(h)
            }
            if env.type == "HELLO" {
                requestDirectoryListing(path: currentDirectoryPath)
                requestPhotosList()
                requestSmsAndContacts()
            }

        case "NOTIFICATION_POSTED":
            if let items = env.notifications {
                for item in items {
                    guard !mutedApps.contains(item.packageName) else { continue }
                    notifications.removeAll { $0.id == item.id }
                    notifications.insert(item, at: 0)
                    postNativeMacNotification(item)
                }
            }

        case "NOTIFICATION_REMOVED":
            if let id = env.payload["id"] {
                notifications.removeAll { $0.id == id }
            }

        case "SMS_THREADS_SYNC":
            if let threads = env.smsThreads {
                self.smsThreads = threads
            }
            if let contactList = env.contacts {
                self.contacts = contactList
            }

        case "CALL_STATE":
            let state = env.payload["state"] ?? "IDLE"
            let number = env.payload["number"] ?? "Unknown"
            let name = env.payload["contactName"] ?? number
            self.callState = IncomingCallState(
                isActive: state != "IDLE",
                state: state,
                phoneNumber: number,
                contactName: name,
                isMuted: env.payload["isMuted"] == "true"
            )

        case "CLIPBOARD_SYNC":
            if let text = env.payload["content"], !text.isEmpty {
                ClipboardSyncManager.shared.applyIncomingFromAndroid(text: text)
            }

        case "URL_HANDOFF":
            if let urlStr = env.payload["url"], let url = URL(string: urlStr) {
                NSWorkspace.shared.open(url)
            }

        case "FILE_LIST_RES":
            if let path = env.payload["path"] {
                self.currentDirectoryPath = path
            }
            if let files = env.files {
                self.remoteFiles = files.sorted {
                    if $0.isDirectory != $1.isDirectory { return $0.isDirectory && !$1.isDirectory }
                    return $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
                }
            }

        case "PHOTOS_LIST_RES":
            if let photos = env.photos {
                self.remotePhotos = photos
            }

        case "FILE_TRANSFER_START":
            guard let tid = env.payload["transferId"],
                  let fileName = env.payload["fileName"],
                  let sizeStr = env.payload["totalBytes"],
                  let totalBytes = Int64(sizeStr) else { return }

            let downloadsDir = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
                .appendingPathComponent("LinkToMac", isDirectory: true)
            try? FileManager.default.createDirectory(at: downloadsDir, withIntermediateDirectories: true)
            let destURL = downloadsDir.appendingPathComponent(fileName)
            FileManager.default.createFile(atPath: destURL.path, contents: nil)
            if let handle = try? FileHandle(forWritingTo: destURL) {
                incomingFileHandles[tid] = (destURL, handle, totalBytes)
                transfers.insert(ActiveTransferItem(
                    id: tid,
                    fileName: fileName,
                    direction: "Receiving from Android",
                    totalBytes: totalBytes,
                    transferredBytes: 0,
                    transport: connectionTransport
                ), at: 0)
            }

        case "FILE_TRANSFER_END":
            guard let tid = env.payload["transferId"] else { return }
            if let entry = incomingFileHandles.removeValue(forKey: tid) {
                try? entry.handle.close()
                if let idx = transfers.firstIndex(where: { $0.id == tid }) {
                    transfers[idx].transferredBytes = transfers[idx].totalBytes
                    transfers[idx].isCompleted = true
                }
                // Reveal in Finder if requested
                if env.payload["revealInFinder"] == "true" {
                    NSWorkspace.shared.activateFileViewerSelecting([entry.url])
                }
            }

        default:
            break
        }
    }

    // MARK: - Handle Incoming Binary File Chunks
    private func handleBinaryFileChunk(_ payload: Data) {
        guard payload.count > 36 else { return }
        let tidData = payload.subdata(in: 0..<36)
        guard let tid = String(data: tidData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) else { return }
        let chunkData = payload.subdata(in: 36..<payload.count)

        if let entry = incomingFileHandles[tid] {
            try? entry.handle.write(contentsOf: chunkData)
            if let idx = transfers.firstIndex(where: { $0.id == tid }) {
                transfers[idx].transferredBytes += Int64(chunkData.count)
            }
        }
    }

    // MARK: - Outgoing Commands
    func sendEnvelope(_ env: LinkEnvelope) {
        guard let connection = activeConnection,
              let data = WireFramer.packJSON(env) else { return }
        connection.send(content: data, completion: .contentProcessed { _ in })
    }

    func sendHello() {
        let hostName = Host.current().localizedName ?? "Mac"
        sendEnvelope(LinkEnvelope(
            type: "HELLO",
            payload: [
                "deviceName": hostName,
                "pairToken": pairingToken
            ]
        ))
    }

    func sendNotificationReply(notificationId: String, replyText: String) {
        guard !replyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        sendEnvelope(LinkEnvelope(
            type: "NOTIFICATION_REPLY",
            payload: [
                "id": notificationId,
                "replyText": replyText
            ]
        ))
        if let idx = notifications.firstIndex(where: { $0.id == notificationId }) {
            notifications[idx].replySentText = replyText
        }
    }

    func dismissNotification(notificationId: String) {
        notifications.removeAll { $0.id == notificationId }
        sendEnvelope(LinkEnvelope(
            type: "NOTIFICATION_REMOVED",
            payload: ["id": notificationId]
        ))
    }

    func sendSms(address: String, body: String) {
        guard !body.isEmpty else { return }
        sendEnvelope(LinkEnvelope(
            type: "SMS_SEND",
            payload: [
                "address": address,
                "body": body
            ]
        ))
        // Optimistically append to local thread
        let newMsg = SmsMessageItem(
            id: UUID().uuidString,
            address: address,
            body: body,
            date: Int64(Date().timeIntervalSince1970 * 1000),
            isIncoming: false
        )
        if let idx = smsThreads.firstIndex(where: { $0.address == address }) {
            smsThreads[idx].messages.append(newMsg)
            smsThreads[idx].lastMessage = body
        } else {
            smsThreads.insert(SmsThreadItem(
                id: address,
                contactName: address,
                address: address,
                lastMessage: body,
                timestamp: newMsg.date,
                messages: [newMsg]
            ), at: 0)
        }
    }

    func sendCallAction(_ action: String, smsReply: String? = nil) {
        var payload = ["action": action]
        if let smsReply = smsReply {
            payload["smsReply"] = smsReply
            payload["number"] = callState.phoneNumber
        }
        sendEnvelope(LinkEnvelope(type: "CALL_ACTION", payload: payload))
        if action == "DECLINE" || action == "END" || action == "DECLINE_WITH_SMS" {
            callState.isActive = false
            callState.state = "IDLE"
        } else if action == "MUTE" {
            callState.isMuted.toggle()
        }
    }

    func dialPhoneNumber(_ number: String) {
        sendEnvelope(LinkEnvelope(
            type: "CALL_ACTION",
            payload: ["action": "DIAL", "number": number]
        ))
    }

    func requestDirectoryListing(path: String) {
        sendEnvelope(LinkEnvelope(
            type: "FILE_LIST_REQ",
            payload: ["path": path]
        ))
    }

    func requestPhotosList() {
        sendEnvelope(LinkEnvelope(type: "PHOTOS_LIST_REQ"))
    }

    func requestSmsAndContacts() {
        sendEnvelope(LinkEnvelope(type: "SMS_THREADS_REQ"))
    }

    func pullRemoteFileToMac(remotePath: String, fileName: String, revealInFinder: Bool = true) {
        sendEnvelope(LinkEnvelope(
            type: "FILE_PULL_REQ",
            payload: [
                "path": remotePath,
                "fileName": fileName,
                "revealInFinder": revealInFinder ? "true" : "false"
            ]
        ))
    }

    // MARK: - Send File from Mac Finder to Android (Chunked High-Speed Stream)
    func sendFileToAndroid(fileURL: URL, destinationFolder: String = "/sdcard/Download/LinkToMac") {
        guard let connection = activeConnection else { return }
        let fileName = fileURL.lastPathComponent
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
              let fileSize = attrs[.size] as? Int64 else { return }

        let transferId = UUID().uuidString // 36 chars
        let transportLabel = connectionTransport

        transfers.insert(ActiveTransferItem(
            id: transferId,
            fileName: fileName,
            direction: "Sending to Android",
            totalBytes: fileSize,
            transferredBytes: 0,
            transport: transportLabel
        ), at: 0)

        sendEnvelope(LinkEnvelope(
            type: "FILE_TRANSFER_START",
            payload: [
                "transferId": transferId,
                "fileName": fileName,
                "totalBytes": "\(fileSize)",
                "destFolder": destinationFolder
            ]
        ))

        Task.detached(priority: .userInitiated) { [weak self] in
            guard let self = self else { return }
            guard let handle = try? FileHandle(forReadingFrom: fileURL) else { return }
            defer { try? handle.close() }

            let tidBytes = transferId.padding(toLength: 36, withPad: " ", startingAt: 0).data(using: .utf8)!
            var sent: Int64 = 0
            let chunkSize = 65536

            while true {
                let chunk = (try? handle.read(upToCount: chunkSize)) ?? Data()
                if chunk.isEmpty { break }

                var framePayload = Data(tidBytes)
                framePayload.append(chunk)
                let packed = WireFramer.packFrame(type: .fileChunk, payload: framePayload)
                connection.send(content: packed, completion: .contentProcessed { _ in })

                sent += Int64(chunk.count)
                let currentSent = sent
                await MainActor.run {
                    if let idx = self.transfers.firstIndex(where: { $0.id == transferId }) {
                        self.transfers[idx].transferredBytes = currentSent
                    }
                }
                try? await Task.sleep(nanoseconds: 1_500_000) // Smooth socket pacing
            }

            await MainActor.run {
                self.sendEnvelope(LinkEnvelope(
                    type: "FILE_TRANSFER_END",
                    payload: ["transferId": transferId, "fileName": fileName]
                ))
                if let idx = self.transfers.firstIndex(where: { $0.id == transferId }) {
                    self.transfers[idx].isCompleted = true
                }
                if !self.currentDirectoryPath.isEmpty {
                    self.requestDirectoryListing(path: self.currentDirectoryPath)
                }
            }
        }
    }

    func sendMediaAction(_ action: String) {
        sendEnvelope(LinkEnvelope(
            type: "MEDIA_ACTION",
            payload: ["action": action]
        ))
    }

    func setStreamMode(_ mode: String) {
        self.activeStreamMode = mode
        if mode == "NONE" {
            self.liveVideoFrame = nil
        }
        sendEnvelope(LinkEnvelope(
            type: "STREAM_CONTROL",
            payload: ["mode": mode]
        ))
    }

    func sendRemoteTap(normalizedX: Double, normalizedY: Double) {
        // If USB ADB is available, ADBBridge injects zero-latency tap; also send over socket
        sendEnvelope(LinkEnvelope(
            type: "STREAM_CONTROL",
            payload: [
                "mode": "REMOTE_TAP",
                "x": String(format: "%.4f", normalizedX),
                "y": String(format: "%.4f", normalizedY)
            ]
        ))
        ADBBridge.shared.injectTapNormalized(x: normalizedX, y: normalizedY)
    }

    func sendRemoteKeyEvent(_ keyCode: Int) {
        sendEnvelope(LinkEnvelope(
            type: "STREAM_CONTROL",
            payload: ["mode": "REMOTE_KEY", "keyCode": "\(keyCode)"]
        ))
        ADBBridge.shared.injectKeyEvent(keyCode)
    }

    // MARK: - Live Audio Playback (Android Mic / Cast)
    private func playPCMFrame(_ pcmData: Data) {
        if !audioConfigured {
            let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 1, interleaved: false)!
            audioEngine.attach(playerNode)
            audioEngine.connect(playerNode, to: audioEngine.mainMixerNode, format: format)
            try? audioEngine.start()
            playerNode.play()
            audioConfigured = true
        }
        let sampleCount = pcmData.count / 2
        guard sampleCount > 0,
              let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 1, interleaved: false),
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(sampleCount)) else { return }

        buffer.frameLength = AVAudioFrameCount(sampleCount)
        if let floatChannel = buffer.floatChannelData?[0] {
            pcmData.withUnsafeBytes { rawBuf in
                let int16Ptr = rawBuf.bindMemory(to: Int16.self)
                for i in 0..<sampleCount {
                    floatChannel[i] = Float(int16Ptr[i]) / 32768.0
                }
            }
            playerNode.scheduleBuffer(buffer, completionHandler: nil)
        }
    }

    // MARK: - Native macOS Notification Dispatch
    private func requestMacNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    private func postNativeMacNotification(_ item: AndroidNotificationItem) {
        let content = UNMutableNotificationContent()
        content.title = "\(item.appName) • \(item.title)"
        content.body = item.body
        content.sound = .default
        let req = UNNotificationRequest(identifier: item.id, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }

    // MARK: - Wake Reconnect & Local IP
    private func observeMacWake() {
        NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didWakeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.localIPAddress = Self.detectLocalIPAddress() ?? "127.0.0.1"
                self?.startServer()
                ADBBridge.shared.pollUSBDevices()
            }
        }
    }

    static func detectLocalIPAddress() -> String? {
        var address: String?
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        if getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr {
            for ptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
                let interface = ptr.pointee
                let addrFamily = interface.ifa_addr.pointee.sa_family
                if addrFamily == UInt8(AF_INET) {
                    let name = String(cString: interface.ifa_name)
                    if name == "en0" || name == "en1" || name.hasPrefix("bridge") {
                        var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                        getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                                    &hostname, socklen_t(hostname.count),
                                    nil, socklen_t(0), NI_NUMERICHOST)
                        let candidate = String(cString: hostname)
                        if !candidate.hasPrefix("127.") {
                            address = candidate
                            break
                        }
                    }
                }
            }
            freeifaddrs(ifaddr)
        }
        return address
    }
}






```

---

### FILE: allcode/LinkToMac-macOS/Sources/LinkToMac/ADBBridge.swift
```swift
import Foundation

@MainActor
final class ADBBridge: ObservableObject {
    static let shared = ADBBridge()

    @Published var isUSBDeviceConnected: Bool = false
    @Published var usbSerial: String = ""
    @Published var isScrcpyAvailable: Bool = false
    @Published var deviceScreenWidth: Int = 1080
    @Published var deviceScreenHeight: Int = 2400

    private var timer: Timer?

    private init() {
        checkScrcpyInstalled()
        startPolling()
    }

    func startPolling() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.pollUSBDevices()
            }
        }
        pollUSBDevices()
    }

    private func findBinary(_ name: String) -> String? {
        let candidates = [
            "/opt/homebrew/bin/\(name)",
            "/usr/local/bin/\(name)",
            "\(NSHomeDirectory())/Library/Android/sdk/platform-tools/\(name)",
            "/usr/bin/\(name)"
        ]
        return candidates.first { FileManager.default.isExecutableFile(atPath: $0) }
    }

    func checkScrcpyInstalled() {
        isScrcpyAvailable = (findBinary("scrcpy") != nil)
    }

    func pollUSBDevices() {
        guard let adbPath = findBinary("adb") else { return }
        Task.detached {
            let output = Self.runCommand(adbPath, args: ["devices"])
            let lines = output.components(separatedBy: .newlines)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty && !$0.hasPrefix("List of devices") && $0.hasSuffix("\tdevice") }

            if let firstLine = lines.first,
               let serial = firstLine.components(separatedBy: "\t").first {
                // Ensure port 54921 is reversed so the Android app can connect over USB via 127.0.0.1:54921
                _ = Self.runCommand(adbPath, args: ["-s", serial, "reverse", "tcp:54921", "tcp:54921"])

                // Query actual screen width/height via adb shell wm size
                let wmOutput = Self.runCommand(adbPath, args: ["-s", serial, "shell", "wm", "size"])
                var detectedW = 0
                var detectedH = 0
                for line in wmOutput.components(separatedBy: .newlines) {
                    let trimmed = line.trimmingCharacters(in: .whitespaces)
                    if trimmed.contains("size:") {
                        let parts = trimmed.components(separatedBy: "size:")
                        if parts.count > 1 {
                            let dims = parts[1].trimmingCharacters(in: .whitespaces).components(separatedBy: "x")
                            if dims.count == 2, let w = Int(dims[0]), let h = Int(dims[1]) {
                                detectedW = w
                                detectedH = h
                            }
                        }
                    }
                }

                let finalW = detectedW
                let finalH = detectedH

                await MainActor.run {
                    let wasConnected = ADBBridge.shared.isUSBDeviceConnected
                    ADBBridge.shared.isUSBDeviceConnected = true
                    ADBBridge.shared.usbSerial = serial
                    if !wasConnected {
                        ADBBridge.shared.unlockBackgroundClipboardViaADB()
                    }
                    if finalW > 0 && finalH > 0 {
                        ADBBridge.shared.deviceScreenWidth = finalW
                        ADBBridge.shared.deviceScreenHeight = finalH
                        ConnectionManager.shared.androidScreenWidth = Double(finalW)
                        ConnectionManager.shared.androidScreenHeight = Double(finalH)
                        ConnectionManager.shared.deviceScreenWidth = finalW
                        ConnectionManager.shared.deviceScreenHeight = finalH
                    }
                    if ConnectionManager.shared.isConnected {
                        ConnectionManager.shared.connectionTransport = "USB"
                    }
                }
            } else {
                await MainActor.run {
                    ADBBridge.shared.isUSBDeviceConnected = false
                    ADBBridge.shared.usbSerial = ""
                    if ConnectionManager.shared.isConnected && ConnectionManager.shared.connectionTransport == "USB" {
                        ConnectionManager.shared.connectionTransport = "Local Wi-Fi"
                    }
                }
            }
        }
    }

    func launchNativeScrcpy(screenOff: Bool, stayAwake: Bool) {
        guard let scrcpyPath = findBinary("scrcpy") else { return }
        var args = ["--window-title", "LinkToMac — \(ConnectionManager.shared.connectedDeviceName)"]
        if screenOff {
            args.append("--turn-screen-off")
        }
        if stayAwake {
            args.append("--stay-awake")
        }
        Task.detached {
            let proc = Process()
            proc.executableURL = URL(fileURLWithPath: scrcpyPath)
            proc.arguments = args
            try? proc.run()
        }
    }

    func injectTapNormalized(x: Double, y: Double) {
        guard isUSBDeviceConnected, let adbPath = findBinary("adb") else { return }
        let w = ConnectionManager.shared.androidScreenWidth > 0 ? ConnectionManager.shared.androidScreenWidth : 1080.0
        let h = ConnectionManager.shared.androidScreenHeight > 0 ? ConnectionManager.shared.androidScreenHeight : 2400.0
        let px = Int(x * w)
        let py = Int(y * h)
        Task.detached {
            _ = Self.runCommand(adbPath, args: ["shell", "input", "tap", "\(px)", "\(py)"])
        }
    }

    func injectKeyEvent(_ keyCode: Int) {
        guard isUSBDeviceConnected, let adbPath = findBinary("adb") else { return }
        Task.detached {
            _ = Self.runCommand(adbPath, args: ["shell", "input", "keyevent", "\(keyCode)"])
        }
    }

    func unlockBackgroundClipboardViaADB() {
        guard isUSBDeviceConnected, let adbPath = findBinary("adb") else { return }
        Task.detached {
            _ = Self.runCommand(adbPath, args: ["shell", "cmd", "appops", "set", "com.linktomac.android", "SYSTEM_ALERT_WINDOW", "allow"])
            _ = Self.runCommand(adbPath, args: ["shell", "pm", "grant", "com.linktomac.android", "android.permission.READ_LOGS"])
        }
    }

    nonisolated private static func runCommand(_ executable: String, args: [String]) -> String {
        let process = Process()
        let pipe = Pipe()
        process.executableURL = URL(fileURLWithPath: executable)
        process.arguments = args
        process.standardOutput = pipe
        process.standardError = pipe
        do {
            try process.run()
            process.waitUntilExit()
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            return String(data: data, encoding: .utf8) ?? ""
        } catch {
            return ""
        }
    }
}






```

---

### FILE: allcode/LinkToMac-macOS/Sources/LinkToMac/ClipboardSyncManager.swift
```swift
import Foundation
import AppKit

@MainActor
final class ClipboardSyncManager: ObservableObject {
    static let shared = ClipboardSyncManager()

    @Published var isSyncEnabled: Bool = true
    @Published var ignoreConcealedPasswords: Bool = true
    @Published var history: [ClipboardHistoryItem] = []

    private var lastChangeCount: Int = NSPasteboard.general.changeCount
    private var lastSyncedString: String = ""
    private var pollTimer: Timer?

    private init() {
        startMonitoring()
    }

    func startMonitoring() {
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.65, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.checkMacPasteboard()
            }
        }
    }

    private func checkMacPasteboard() {
        guard isSyncEnabled else { return }
        let pb = NSPasteboard.general
        guard pb.changeCount != lastChangeCount else { return }
        lastChangeCount = pb.changeCount

        // Respect password managers (1Password, Bitwarden, Keychain)
        if ignoreConcealedPasswords {
            let concealedType = NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType")
            if pb.types?.contains(concealedType) == true {
                return
            }
        }

        guard let copied = pb.string(forType: .string)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !copied.isEmpty,
              copied != lastSyncedString else { return }

        lastSyncedString = copied
        let isUrl = copied.hasPrefix("http://") || copied.hasPrefix("https://")
        recordHistory(content: copied, isUrl: isUrl, source: "This Mac")

        if ConnectionManager.shared.isConnected {
            ConnectionManager.shared.sendEnvelope(LinkEnvelope(
                type: "CLIPBOARD_SYNC",
                payload: [
                    "content": copied,
                    "isUrl": isUrl ? "true" : "false"
                ]
            ))
        }
    }

    func applyIncomingFromAndroid(text: String) {
        guard isSyncEnabled, text != lastSyncedString else { return }
        lastSyncedString = text

        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(text, forType: .string)
        lastChangeCount = pb.changeCount

        let isUrl = text.hasPrefix("http://") || text.hasPrefix("https://")
        recordHistory(content: text, isUrl: isUrl, source: "Android")
    }

    func copyItemToMac(_ item: ClipboardHistoryItem) {
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(item.content, forType: .string)
        lastChangeCount = pb.changeCount
        lastSyncedString = item.content
    }

    func handoffURLToAndroid(_ urlString: String) {
        ConnectionManager.shared.sendEnvelope(LinkEnvelope(
            type: "URL_HANDOFF",
            payload: ["url": urlString]
        ))
    }

    private func recordHistory(content: String, isUrl: Bool, source: String) {
        history.removeAll { $0.content == content }
        history.insert(ClipboardHistoryItem(
            content: content,
            isUrl: isUrl,
            source: source,
            timestamp: Date()
        ), at: 0)
        if history.count > 40 {
            history.removeLast()
        }
    }
}






```

---

### FILE: allcode/LinkToMac-macOS/Sources/LinkToMac/MainDashboardView.swift
```swift
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






```

---

### FILE: allcode/LinkToMac-macOS/Sources/LinkToMac/FeatureViews.swift
```swift
import SwiftUI
import UniformTypeIdentifiers

// MARK: - 01 Messages (SMS/MMS + Contacts) Workspace
struct MessagesWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @LocalState private var selectedThreadId: String?
    @LocalState private var draftMessage: String = ""
    @LocalState private var newRecipientNumber: String = ""
    @LocalState private var searchContactText: String = ""

    var body: some View {
        HSplitView {
            // Left Column: SMS Threads & Contacts Search
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Messages & Contacts")
                        .font(.headline)
                    Spacer()
                    Button {
                        conn.requestSmsAndContacts()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .help("Sync SMS & Contacts from Android")
                }
                .padding(.horizontal, 12)
                .padding(.top, 12)

                HStack {
                    TextField("Dial or New SMS #...", text: $newRecipientNumber)
                        .textFieldStyle(.roundedBorder)
                    Button("Chat") {
                        let trimmed = newRecipientNumber.trimmingCharacters(in: .whitespaces)
                        guard !trimmed.isEmpty else { return }
                        if !conn.smsThreads.contains(where: { $0.address == trimmed }) {
                            conn.smsThreads.insert(SmsThreadItem(
                                id: trimmed,
                                contactName: trimmed,
                                address: trimmed,
                                lastMessage: "New conversation",
                                timestamp: Int64(Date().timeIntervalSince1970 * 1000),
                                messages: []
                            ), at: 0)
                        }
                        selectedThreadId = trimmed
                        newRecipientNumber = ""
                    }
                    Button {
                        let trimmed = newRecipientNumber.trimmingCharacters(in: .whitespaces)
                        if !trimmed.isEmpty { conn.dialPhoneNumber(trimmed) }
                    } label: {
                        Image(systemName: "phone.fill")
                    }
                }
                .padding(.horizontal, 12)

                List(conn.smsThreads, selection: $selectedThreadId) { thread in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(thread.contactName)
                                .font(.subheadline.bold())
                            Spacer()
                            Text(thread.address)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Text(thread.lastMessage)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .padding(.vertical, 4)
                    .tag(thread.id)
                }
            }
            .frame(minWidth: 250, maxWidth: 320)

            // Right Column: Active Conversation
            VStack(spacing: 0) {
                if let threadId = selectedThreadId,
                   let thread = conn.smsThreads.first(where: { $0.id == threadId }) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(thread.contactName)
                                .font(.headline)
                            Text(thread.address)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button {
                            conn.dialPhoneNumber(thread.address)
                        } label: {
                            Label("Call on Phone", systemImage: "phone.arrow.up.right.fill")
                        }
                    }
                    .padding(14)
                    .background(Color.primary.opacity(0.05))

                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            ForEach(thread.messages) { msg in
                                HStack {
                                    if !msg.isIncoming { Spacer() }
                                    Text(msg.body)
                                        .padding(10)
                                        .background(msg.isIncoming ? Color.primary.opacity(0.1) : Color.green.opacity(0.85))
                                        .foregroundStyle(msg.isIncoming ? Color.primary : Color.black)
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                    if msg.isIncoming { Spacer() }
                                }
                            }
                        }
                        .padding(16)
                    }

                    Divider()
                    HStack(spacing: 10) {
                        TextField("Reply via Android SMS...", text: $draftMessage)
                            .textFieldStyle(.roundedBorder)
                            .onSubmit {
                                sendCurrentDraft(to: thread.address)
                            }
                        Button("Send ↵") {
                            sendCurrentDraft(to: thread.address)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.green)
                    }
                    .padding(12)
                } else {
                    VStack(spacing: 10) {
                        Image(systemName: "message.and.waveform.fill")
                            .font(.system(size: 42))
                            .foregroundStyle(.secondary)
                        Text("Select a conversation or start a new SMS")
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
    }

    private func sendCurrentDraft(to address: String) {
        let text = draftMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        conn.sendSms(address: address, body: text)
        draftMessage = ""
    }
}

// MARK: - 01 Notifications Mirror & Inline Reply Workspace
struct NotificationsWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @LocalState private var replyDrafts: [String: String] = [:]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Android Notification Mirroring")
                    .font(.title2.bold())
                Spacer()
                if !conn.mutedApps.isEmpty {
                    Button("Unmute All Apps (\(conn.mutedApps.count))") {
                        conn.mutedApps.removeAll()
                    }
                }
                Button("Clear All") {
                    conn.notifications.removeAll()
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)

            if conn.notifications.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "bell.slash")
                        .font(.system(size: 42))
                        .foregroundStyle(.secondary)
                    Text("No recent Android notifications")
                        .font(.headline)
                    Text("Incoming messages and alerts from your phone will appear here with inline keyboard reply.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(conn.notifications) { item in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(item.appName.uppercased())
                                    .font(.caption.bold())
                                    .foregroundStyle(.green)
                                Text("•")
                                    .foregroundStyle(.secondary)
                                Text(item.title)
                                    .font(.headline)
                                Spacer()
                                if let otp = item.otpCode, !otp.isEmpty {
                                    Button("Copy 2FA: \(otp)") {
                                        NSPasteboard.general.clearContents()
                                        NSPasteboard.general.setString(otp, forType: .string)
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(.blue)
                                    .controlSize(.small)
                                }
                                Button("Mute App") {
                                    conn.mutedApps.insert(item.packageName)
                                    conn.notifications.removeAll { $0.packageName == item.packageName }
                                }
                                .controlSize(.small)
                                Button {
                                    conn.dismissNotification(notificationId: item.id)
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                }
                                .buttonStyle(.plain)
                            }

                            Text(item.body)
                                .font(.body)

                            if let sent = item.replySentText {
                                Label("Reply sent: \"\(sent)\"", systemImage: "checkmark.circle.fill")
                                    .font(.caption)
                                    .foregroundStyle(.green)
                            } else if item.canReply {
                                HStack {
                                    TextField("Reply from keyboard...", text: Binding(
                                        get: { replyDrafts[item.id] ?? "" },
                                        set: { replyDrafts[item.id] = $0 }
                                    ))
                                    .textFieldStyle(.roundedBorder)
                                    .onSubmit {
                                        let txt = replyDrafts[item.id] ?? ""
                                        conn.sendNotificationReply(notificationId: item.id, replyText: txt)
                                        replyDrafts[item.id] = ""
                                    }
                                    Button("Reply ↵") {
                                        let txt = replyDrafts[item.id] ?? ""
                                        conn.sendNotificationReply(notificationId: item.id, replyText: txt)
                                        replyDrafts[item.id] = ""
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(.green)
                                }
                            }
                        }
                        .padding(10)
                    }
                }
            }
        }
    }
}

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






```

---

### FILE: allcode/LinkToMac-macOS/Sources/LinkToMac/LinkToMacApp.swift
```swift
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






```

---
