import Foundation
import AppKit

/// Represents an SSH target endpoint for Pinggy reverse tunnel forwarding.
public struct PinggyTarget: Equatable, Sendable {
    public let host: String
    public let port: Int
    public let user: String

    public init(host: String, port: Int, user: String = "tcp") {
        self.host = host
        self.port = port
        self.user = user
    }
}

/// Manages the background SSH reverse tunnel process to Pinggy, extracts dynamic
/// public host/port endpoints, handles connection fallbacks, and manages process lifecycles.
@MainActor
public final class PinggyTunnelManager: ObservableObject {
    public static let shared = PinggyTunnelManager()

    // MARK: - Published Properties
    @Published public private(set) var isRunning: Bool = false
    @Published public private(set) var isConnecting: Bool = false
    @Published public private(set) var publicHost: String? = nil
    @Published public private(set) var publicPort: Int? = nil
    @Published public private(set) var publicURL: String? = nil
    @Published public private(set) var statusMessage: String = "Stopped"
    @Published public private(set) var lastError: String? = nil

    // MARK: - Process & Stream State
    private var process: Process?
    private var stdoutPipe: Pipe?
    private var stdinPipe: Pipe?
    private var lineBuffer: String = ""
    private var targetIndex: Int = 0
    private var retryTimer: Timer?
    private var watchdogTimer: Timer?

    // Candidate targets in priority order (free.pinggy.io succeeds when a.pinggy.io is refused)
    private static let targets: [PinggyTarget] = [
        PinggyTarget(host: "free.pinggy.io", port: 443),
        PinggyTarget(host: "a.pinggy.io", port: 443),
        PinggyTarget(host: "free.pinggy.io", port: 22),
        PinggyTarget(host: "a.pinggy.io", port: 22)
    ]

    private init() {
        setupLifecycleObservers()
    }

    // MARK: - Lifecycle Observers
    private func setupLifecycleObservers() {
        NotificationCenter.default.addObserver(
            forName: NSApplication.willTerminateNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.stopTunnel()
            }
        }

        NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.willSleepNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.stopTunnelInternal()
            }
        }

        NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didWakeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                if self.isRunning {
                    self.restartTunnel()
                }
            }
        }
    }

    // MARK: - Public Control API
    public func startTunnel() {
        guard !isRunning && !isConnecting else { return }
        targetIndex = 0
        connectToCurrentTarget()
    }

    public func stopTunnel() {
        retryTimer?.invalidate()
        retryTimer = nil
        watchdogTimer?.invalidate()
        watchdogTimer = nil

        stopTunnelInternal()
        statusMessage = "Tunnel stopped"
        lastError = nil
    }

    public func toggleTunnel() {
        if isRunning || isConnecting {
            stopTunnel()
        } else {
            startTunnel()
        }
    }

    public func restartTunnel() {
        stopTunnel()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.startTunnel()
        }
    }

    // MARK: - Process Execution & Target Fallback
    private func connectToCurrentTarget() {
        guard targetIndex < Self.targets.count else {
            statusMessage = "All tunnel endpoints failed. Retrying in 12s..."
            isConnecting = false
            scheduleRetry(seconds: 12.0)
            return
        }

        let target = Self.targets[targetIndex]
        isConnecting = true
        statusMessage = "Connecting to relay (\(target.host):\(target.port))..."

        ensureIdentityKey()
        let keyPath = resolvedKeyPath()

        let proc = Process()
        let outPipe = Pipe()
        let inPipe = Pipe()

        proc.executableURL = URL(fileURLWithPath: "/usr/bin/ssh")
        var args = [
            "-tt",
            "-p", "\(target.port)",
            "-R0:localhost:54921",
            "\(target.user)@\(target.host)",
            "-o", "StrictHostKeyChecking=no",
            "-o", "UserKnownHostsFile=/dev/null",
            "-o", "ServerAliveInterval=15",
            "-o", "ServerAliveCountMax=3",
            "-o", "ExitOnForwardFailure=yes",
            "-o", "ConnectTimeout=8"
        ]
        if let key = keyPath {
            args.append(contentsOf: ["-i", key])
        }

        proc.arguments = args
        proc.standardOutput = outPipe
        proc.standardError = outPipe
        proc.standardInput = inPipe

        self.process = proc
        self.stdoutPipe = outPipe
        self.stdinPipe = inPipe
        self.lineBuffer = ""

        outPipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let data = handle.availableData
            guard !data.isEmpty else { return }
            if let chunk = String(data: data, encoding: .utf8) {
                Task { @MainActor in
                    self?.handleIncomingOutput(chunk)
                }
            }
        }

        proc.terminationHandler = { [weak self] terminatedProc in
            Task { @MainActor in
                self?.handleProcessExited(code: terminatedProc.terminationStatus)
            }
        }

        do {
            try proc.run()
            // 12-second watchdog timer: if target does not establish tunnel in time, try next
            watchdogTimer?.invalidate()
            watchdogTimer = Timer.scheduledTimer(withTimeInterval: 12.0, repeats: false) { [weak self] _ in
                Task { @MainActor in
                    guard let self = self else { return }
                    if self.isConnecting && !self.isRunning {
                        print("[PinggyTunnelManager] Watchdog timeout connecting to \(target.host):\(target.port)")
                        self.targetIndex += 1
                        self.stopTunnelInternal()
                        self.connectToCurrentTarget()
                    }
                }
            }
        } catch {
            print("[PinggyTunnelManager] Failed to launch ssh process: \(error)")
            lastError = error.localizedDescription
            targetIndex += 1
            connectToCurrentTarget()
        }
    }

    private func handleIncomingOutput(_ chunk: String) {
        lineBuffer.append(chunk)

        let cleanChunk = Self.stripAnsiCodes(chunk)
        if let endpoint = Self.extractTunnelEndpoint(from: cleanChunk) {
            watchdogTimer?.invalidate()
            watchdogTimer = nil
            self.publicHost = endpoint.host
            self.publicPort = endpoint.port
            self.publicURL = endpoint.url
            self.isRunning = true
            self.isConnecting = false
            self.statusMessage = "Relay Active: \(endpoint.host):\(endpoint.port)"
            self.lastError = nil
            print("[PinggyTunnelManager] Tunnel established successfully: \(endpoint.url)")
        }
    }

    private func handleProcessExited(code: Int32) {
        let wasRunning = isRunning
        stopTunnelInternal()

        if wasRunning {
            statusMessage = "Tunnel dropped (exit \(code)). Reconnecting in 3s..."
            scheduleRetry(seconds: 3.0)
        } else if isConnecting {
            print("[PinggyTunnelManager] Target \(targetIndex) failed with exit code \(code)")
            targetIndex += 1
            connectToCurrentTarget()
        }
    }

    private func stopTunnelInternal() {
        stdoutPipe?.fileHandleForReading.readabilityHandler = nil
        if let proc = process, proc.isRunning {
            proc.terminate()
            let pid = proc.processIdentifier
            DispatchQueue.global().asyncAfter(deadline: .now() + 1.0) {
                if pid > 0 {
                    kill(pid, SIGKILL)
                }
            }
        }
        process = nil
        stdoutPipe = nil
        stdinPipe = nil
        isRunning = false
        publicHost = nil
        publicPort = nil
        publicURL = nil
    }

    private func scheduleRetry(seconds: Double = 10.0) {
        retryTimer?.invalidate()
        retryTimer = Timer.scheduledTimer(withTimeInterval: seconds, repeats: false) { [weak self] _ in
            Task { @MainActor in
                self?.startTunnel()
            }
        }
    }

    // MARK: - Identity Key Provisioning (Eliminates Password Prompt)
    private func resolvedKeyPath() -> String? {
        let fm = FileManager.default
        let home = NSHomeDirectory()
        let candidates = [
            "\(home)/.linktomac/id_ed25519",
            "\(home)/.ssh/id_ed25519",
            "\(home)/.ssh/id_rsa",
            "\(home)/.ssh/spawn_ed25519"
        ]
        return candidates.first { fm.fileExists(atPath: $0) }
    }

    private func ensureIdentityKey() {
        if resolvedKeyPath() != nil { return }
        let home = NSHomeDirectory()
        let dir = "\(home)/.linktomac"
        let keyPath = "\(dir)/id_ed25519"
        let fm = FileManager.default

        try? fm.createDirectory(atPath: dir, withIntermediateDirectories: true, attributes: nil)
        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: "/usr/bin/ssh-keygen")
        proc.arguments = ["-t", "ed25519", "-N", "", "-f", keyPath, "-q"]
        try? proc.run()
        proc.waitUntilExit()
    }

    // MARK: - VT100/ANSI Code Stripping & URL Parsing
    public static func stripAnsiCodes(_ input: String) -> String {
        let pattern = #"\x1b\[[0-9;?]*[ -/]*[@-~]|\x1b\][^\x07\x1b]*(\x07|\x1b\\)|\x1b[@-Z\\-_]|\r"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
            return input
        }
        let range = NSRange(input.startIndex..<input.endIndex, in: input)
        return regex.stringByReplacingMatches(in: input, options: [], range: range, withTemplate: "")
    }

    public static func extractTunnelEndpoint(from text: String) -> (host: String, port: Int, url: String)? {
        let cleaned = stripAnsiCodes(text)

        // Matches both "tcp://<host>:<port>" and standard pinggy hostnames with ports
        let pattern = #"(?:tcp:\/\/)?([a-zA-Z0-9_\-\.]+pinggy[a-zA-Z0-9_\-\.]*\.[a-z]{2,}|(?:[a-zA-Z0-9_\-]+\.)+[a-zA-Z]{2,}):([0-9]{2,5})"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else {
            return nil
        }

        let range = NSRange(cleaned.startIndex..<cleaned.endIndex, in: cleaned)
        if let match = regex.firstMatch(in: cleaned, options: [], range: range) {
            guard let hostRange = Range(match.range(at: 1), in: cleaned),
                  let portRange = Range(match.range(at: 2), in: cleaned),
                  let port = Int(cleaned[portRange]),
                  port > 0 && port <= 65535 else {
                return nil
            }
            let host = String(cleaned[hostRange])
            let fullMatchRange = Range(match.range(at: 0), in: cleaned)!
            let fullString = String(cleaned[fullMatchRange])
            return (host: host, port: port, url: fullString)
        }

        // Secondary fallback scanning for any explicit "tcp://" token
        if let tcpIdx = cleaned.range(of: "tcp://") {
            let sub = cleaned[tcpIdx.upperBound...]
            if let token = sub.components(separatedBy: CharacterSet.whitespacesAndNewlines).first(where: { !$0.isEmpty }) {
                let parts = token.trimmingCharacters(in: CharacterSet(charactersIn: " \t\r\n\0\"'<>[]()")).components(separatedBy: ":")
                if parts.count == 2, let p = Int(parts[1]), p > 0 && p <= 65535 {
                    return (host: parts[0], port: p, url: "tcp://\(token)")
                }
            }
        }
        return nil
    }
}
