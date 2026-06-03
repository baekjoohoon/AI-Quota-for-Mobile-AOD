import Foundation

enum SnapshotStatus: String, Codable {
    case notLinked
    case fresh
    case stale
    case offline
    case providerError
    case revoked

    static func resolve(uploadedAt: Date?, providerStatuses: [ProviderStatus], now: Date = Date()) -> SnapshotStatus {
        guard let uploadedAt else {
            return .notLinked
        }

        let age = now.timeIntervalSince(uploadedAt)
        if age >= 24 * 60 * 60 {
            return .offline
        }
        if providerStatuses.contains(.error) {
            return .providerError
        }
        if age >= 15 * 60 {
            return .stale
        }
        return .fresh
    }
}

