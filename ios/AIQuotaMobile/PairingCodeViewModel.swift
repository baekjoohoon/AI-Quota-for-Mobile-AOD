import Foundation

enum PairingCodeState: Equatable {
    case idle
    case loading
    case ready(code: String, expiresAt: Date)
    case failed(message: String)

    var title: String {
        switch self {
        case .idle:
            return "No PC linked"
        case .loading:
            return "Generating code"
        case .ready:
            return "Your PC link code"
        case .failed:
            return "Could not generate code"
        }
    }
}

@MainActor
final class PairingCodeViewModel: ObservableObject {
    @Published private(set) var state: PairingCodeState = .idle

    private let apiClient: AIQuotaAPIClient

    init(apiClient: AIQuotaAPIClient) {
        self.apiClient = apiClient
    }

    func generate(idToken: String) async {
        state = .loading
        do {
            let response = try await apiClient.createPairingCode(idToken: idToken)
            state = .ready(code: response.displayCode, expiresAt: response.expiresAt)
        } catch {
            state = .failed(message: "Expires in code generation failed")
        }
    }

    func countdownText(now: Date = Date()) -> String? {
        guard case let .ready(_, expiresAt) = state else {
            return nil
        }
        let remaining = max(0, Int(expiresAt.timeIntervalSince(now)))
        return "Expires in \(remaining / 60):\(String(format: "%02d", remaining % 60))"
    }
}

