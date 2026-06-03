import Foundation

struct PairingCodeResponse: Codable {
    let code: String
    let expiresAt: Date

    var displayCode: String {
        let compact = code.replacingOccurrences(of: " ", with: "")
        guard compact.count == 6 else { return code }
        let split = compact.index(compact.startIndex, offsetBy: 3)
        return "\(compact[..<split]) \(compact[split...])"
    }
}

struct AIQuotaAPIClient {
    let baseURL: URL
    var session: URLSession = .shared

    func createPairingCode(idToken: String) async throws -> PairingCodeResponse {
        let request = makeRequest(path: "createPairingCode", idToken: idToken)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response)
        return try decoder.decode(PairingCodeResponse.self, from: data)
    }

    func fetchLatestSnapshot(uid: String, deviceId: String, idToken: String) async throws -> UsageSnapshot {
        let request = makeRequest(
            path: "users/\(uid)/devices/\(deviceId)/snapshots/latest",
            idToken: idToken,
            method: "GET"
        )
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response)
        return try decoder.decode(UsageSnapshot.self, from: data)
    }

    private func makeRequest(path: String, idToken: String, method: String = "POST") -> URLRequest {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = method
        request.setValue("Bearer \(idToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return request
    }

    private func validate(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw APIError.invalidResponse
        }
    }

    private var decoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}

enum APIError: Error {
    case invalidResponse
}

