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

    @Published var isRingingPhone: Bool = false
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
        if let savedName = UserDefaults.standard.string(forKey: "LinkToMacLastDeviceName"), !savedName.isEmpty {
            self.connectedDeviceName = savedName
        }
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
            let tcpOptions = NWProtocolTCP.Options()
            tcpOptions.enableKeepalive = true
            tcpOptions.keepaliveIdle = 5
            tcpOptions.keepaliveInterval = 5
            tcpOptions.keepaliveCount = 3
            let params = NWParameters(tls: nil, tcp: tcpOptions)
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
                    if self.pairingToken.isEmpty {
                        self.isConnected = true
                        self.sendHello()
                        self.readNextChunk(from: connection)
                    } else {
                        self.verifyPeerTokenAsync(connection) { success in
                            Task { @MainActor in
                                if success {
                                    self.isConnected = true
                                    self.sendHello()
                                    self.readNextChunk(from: connection)
                                } else {
                                    print("[LinkToMac] Auth token mismatch or missing, closing connection")
                                    connection.cancel()
                                }
                            }
                        }
                    }
                case .failed, .cancelled:
                    if self.activeConnection === connection {
                        self.isConnected = false
                        self.isRingingPhone = false
                        self.connectionTransport = "Disconnected"
                    }
                default:
                    break
                }
            }
        }
        connection.start(queue: .main)
    }

    /// Asynchronously verifies the 36-byte shared-secret authentication token from the connecting peer.
    private func verifyPeerTokenAsync(_ connection: NWConnection, completion: @escaping (Bool) -> Void) {
        let expectedToken = pairingToken
        guard !expectedToken.isEmpty else {
            completion(true)
            return
        }
        connection.receive(minimumIncompleteLength: 36, maximumLength: 36) { data, _, _, error in
            guard let data = data, data.count == 36 else {
                completion(false)
                return
            }
            let tokenBytes = Array(data)
            let expected = Array(expectedToken.utf8)
            var diff = 0
            for i in 0..<36 {
                let expByte = (i < expected.count) ? expected[i] : 0
                diff |= Int(tokenBytes[i]) ^ Int(expByte)
            }
            completion(diff == 0)
        }
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
            if let name = env.payload["deviceName"] {
                self.connectedDeviceName = name
                UserDefaults.standard.set(name, forKey: "LinkToMacLastDeviceName")
            }
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

    func ringPhone() {
        isRingingPhone = true
        sendMediaAction("RING_PHONE")
    }

    func stopRingingPhone() {
        isRingingPhone = false
        sendMediaAction("STOP_RING")
    }

    func sendMediaAction(_ action: String) {
        if action == "RING_PHONE" {
            isRingingPhone = true
        } else if action == "STOP_RING" || action == "STOP_RINGING" {
            isRingingPhone = false
        }
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

    /// Injects a normalized tap coordinates (0.0 - 1.0) on the remote Android screen.
    func sendRemoteTap(normalizedX: Double, normalizedY: Double) {
        sendEnvelope(LinkEnvelope(
            type: "STREAM_CONTROL",
            payload: [
                "mode": "REMOTE_TAP",
                "x": String(format: "%.4f", normalizedX),
                "y": String(format: "%.4f", normalizedY)
            ]
        ))
        // Only use ADB on USB transport
        if connectionTransport == "USB" {
            ADBBridge.shared.injectTapNormalized(x: normalizedX, y: normalizedY)
        }
    }

    /// Injects an Android key code event into the remote device.
    func sendRemoteKeyEvent(_ keyCode: Int) {
        sendEnvelope(LinkEnvelope(
            type: "STREAM_CONTROL",
            payload: ["mode": "REMOTE_KEY", "keyCode": "\(keyCode)"]
        ))
        // Only use ADB on USB transport
        if connectionTransport == "USB" {
            ADBBridge.shared.injectKeyEvent(keyCode)
        }
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
