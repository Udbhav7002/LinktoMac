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







