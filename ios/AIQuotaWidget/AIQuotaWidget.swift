import WidgetKit
import SwiftUI

struct AIQuotaEntry: TimelineEntry {
    let date: Date
    let providers: [ProviderUsage]
}

struct AIQuotaProvider: TimelineProvider {
    func placeholder(in context: Context) -> AIQuotaEntry {
        AIQuotaEntry(date: Date(), providers: [])
    }

    func getSnapshot(in context: Context, completion: @escaping (AIQuotaEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AIQuotaEntry>) -> Void) {
        let entry = loadEntry()
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(15 * 60))))
    }

    private func loadEntry() -> AIQuotaEntry {
        let snapshot = try? SnapshotCache.shared.load()
        return AIQuotaEntry(date: Date(), providers: Array(snapshot?.providers.prefix(4) ?? []))
    }
}

struct AIQuotaWidgetView: View {
    let entry: AIQuotaEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("AI Quota")
                .font(.headline)
            ForEach(entry.providers) { provider in
                HStack {
                    Text(provider.displayName)
                    Spacer()
                    Text(provider.status.rawValue)
                }
                .font(.caption)
            }
        }
        .padding()
    }
}

@main
struct AIQuotaWidget: Widget {
    let kind = "AIQuotaWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: AIQuotaProvider()) { entry in
            AIQuotaWidgetView(entry: entry)
        }
        .configurationDisplayName("AI Quota")
        .description("Shows cached AI quota snapshot data.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

