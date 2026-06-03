import Foundation

@MainActor
final class SessionViewModel: ObservableObject {
    @Published private(set) var state: SessionState = .signedOut

    func restore() async {
        state = .signedOut
    }

    func markSignedInForPreview() {
        state = .signedIn
    }
}
