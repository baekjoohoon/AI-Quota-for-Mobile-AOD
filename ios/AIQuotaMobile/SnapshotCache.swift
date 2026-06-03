import Foundation

final class SnapshotCache {
    static let shared = SnapshotCache()

    private let fileName = "latest-snapshot.json"
    private let appGroupIdentifier = "group.com.aiquota.mobile"

    private init() {}

    func save(_ snapshot: UsageSnapshot) throws {
        let data = try JSONEncoder.syncEncoder.encode(snapshot)
        try data.write(to: cacheURL(), options: [.atomic])
    }

    func load() throws -> UsageSnapshot? {
        let url = cacheURL()
        guard FileManager.default.fileExists(atPath: url.path) else {
            return nil
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder.syncDecoder.decode(UsageSnapshot.self, from: data)
    }

    private func cacheURL() throws -> URL {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) else {
            throw CacheError.missingAppGroup
        }
        // App Group cache is display-only data shared with WidgetKit.
        return container.appendingPathComponent(fileName)
    }
}

enum CacheError: Error {
    case missingAppGroup
}

private extension JSONEncoder {
    static let syncEncoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()
}

private extension JSONDecoder {
    static let syncDecoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}

