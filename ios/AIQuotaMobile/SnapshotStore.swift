import Foundation

@MainActor
final class SnapshotStore: ObservableObject {
    @Published private(set) var devices: [DeviceSummary] = []
    @Published private(set) var providers: [ProviderUsage] = []

    private let cache = SnapshotCache.shared

    func loadSampleSnapshot() {
        devices = [
            DeviceSummary(id: "sample", name: "Sample PC", lastSeenAt: Date())
        ]
        providers = [
            ProviderUsage(
                providerId: "codex",
                displayName: "Codex",
                plan: "Pro 10x",
                status: .ok,
                fetchedAt: Date(),
                errorCode: nil,
                errorMessage: nil,
                lines: [
                    UsageLine(
                        type: "progress",
                        label: "Session",
                        used: 42,
                        limit: 100,
                        remaining: nil,
                        format: UsageFormat(kind: "percent"),
                        resetsAt: nil
                    )
                ]
            ),
            ProviderUsage(
                providerId: "claude",
                displayName: "Claude",
                plan: nil,
                status: .error,
                fetchedAt: Date(),
                errorCode: "auth_expired",
                errorMessage: "PC에서 다시 로그인 필요",
                lines: []
            )
        ]
    }

    func refreshLatestSnapshot() async {
        do {
            if let snapshot = try cache.load() {
                providers = snapshot.providers
                devices = [
                    DeviceSummary(
                        id: "cached",
                        name: "Cached PC",
                        lastSeenAt: snapshot.uploadedAt
                    )
                ]
            }
        } catch {
            providers = []
        }
    }
}
