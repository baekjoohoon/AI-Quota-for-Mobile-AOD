import SwiftUI

@main
struct AIQuotaMobileApp: App {
    @StateObject private var session = SessionViewModel()
    @StateObject private var snapshotStore = SnapshotStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(session)
                .environmentObject(snapshotStore)
                .task {
                    await session.restore()
                }
        }
    }
}

