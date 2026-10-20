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







