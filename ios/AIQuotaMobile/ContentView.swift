import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var session: SessionViewModel
    @EnvironmentObject private var snapshotStore: SnapshotStore

    var body: some View {
        NavigationStack {
            Group {
                switch session.state {
                case .signedOut:
                    SignInView()
                case .signedIn:
                    if snapshotStore.devices.isEmpty {
                        PairingView()
                    } else {
                        SnapshotListView()
                    }
                }
            }
            .navigationTitle("AI Quota")
        }
    }
}

struct SignInView: View {
    @EnvironmentObject private var session: SessionViewModel

    var body: some View {
        VStack(spacing: 12) {
            Button("Continue with Google") {
                session.markSignedInForPreview()
            }
            Button("Continue with GitHub") {
                session.markSignedInForPreview()
            }
        }
        .buttonStyle(.borderedProminent)
        .padding()
    }
}

struct PairingView: View {
    @EnvironmentObject private var snapshotStore: SnapshotStore
    @StateObject private var viewModel = PairingCodeViewModel(
        apiClient: AIQuotaAPIClient(baseURL: URL(string: "https://example.invalid")!)
    )

    var body: some View {
        VStack(spacing: 16) {
            Text("No PC linked")
                .font(.headline)
            Text(viewModel.state.title)
                .font(.headline)
            Button("Generate PC Link Code") {
                Task {
                    await viewModel.generate(idToken: "")
                }
            }
            if case let .ready(code, _) = viewModel.state {
                Text(code)
                    .font(.system(size: 42, weight: .semibold, design: .monospaced))
                Text(viewModel.countdownText() ?? "Expires in 10:00")
                    .foregroundStyle(.secondary)
            }
            Button("Save sample snapshot") {
                snapshotStore.loadSampleSnapshot()
            }
        }
        .padding()
    }
}

struct SnapshotListView: View {
    @EnvironmentObject private var snapshotStore: SnapshotStore

    var body: some View {
        List(snapshotStore.providers) { provider in
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(provider.displayName)
                        .font(.headline)
                    Spacer()
                    Text(provider.status.rawValue)
                        .font(.caption)
                }
                ForEach(provider.lines) { line in
                    Text("\(line.label): \(line.displayValue)")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .refreshable {
            await snapshotStore.refreshLatestSnapshot()
        }
    }
}
