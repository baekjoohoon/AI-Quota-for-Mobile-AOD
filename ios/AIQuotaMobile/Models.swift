import Foundation

enum SessionState {
    case signedOut
    case signedIn
}

enum ProviderStatus: String, Codable {
    case ok
    case error
    case unknown
}

struct DeviceSummary: Identifiable, Codable {
    let id: String
    let name: String
    let lastSeenAt: Date?
}

struct UsageSnapshot: Codable {
    let schemaVersion: Int
    let fetchedAt: Date
    let uploadedAt: Date?
    let source: String
    let providers: [ProviderUsage]
}

struct ProviderUsage: Identifiable, Codable {
    var id: String { providerId }
    let providerId: String
    let displayName: String
    let plan: String?
    let status: ProviderStatus
    let fetchedAt: Date
    let errorCode: String?
    let errorMessage: String?
    let lines: [UsageLine]
}

struct UsageLine: Identifiable, Codable {
    var id: String { "\(label)-\(type)" }
    let type: String
    let label: String
    let used: Double?
    let limit: Double?
    let remaining: Double?
    let format: UsageFormat
    let resetsAt: Date?

    var displayValue: String {
        if let used, let limit {
            return "\(Int(used))/\(Int(limit))"
        }
        if let remaining {
            return "\(Int(remaining)) remaining"
        }
        return "--"
    }
}

struct UsageFormat: Codable {
    let kind: String
}

