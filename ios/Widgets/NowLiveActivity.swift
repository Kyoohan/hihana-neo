import SwiftUI
import WidgetKit
import ActivityKit

/// 실시간 활동 — 잠금 화면 배너와 Dynamic Island. 지금 구간, 남은 시간, 진행 막대, 다음 일정.
struct NowLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: NowActivityAttributes.self) { context in
            LockScreenView(state: context.state.shown(stale: context.isStale))
                .activityBackgroundTint(Color.black.opacity(0.35))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            let s = context.state.shown(stale: context.isStale)
            let accent = Color(argb: s.accentARGB)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(s.status, systemImage: s.symbol).font(.caption.weight(.semibold)).foregroundStyle(accent).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(timerInterval: s.start...s.end, countsDown: true).font(.title3.weight(.semibold).monospacedDigit())
                        .foregroundStyle(accent).multilineTextAlignment(.trailing).frame(maxWidth: 80)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(s.title).font(.headline).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        ProgressView(timerInterval: s.start...s.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }.tint(accent)
                        HStack {
                            if let r = s.room { Text(r).lineLimit(1) }
                            Spacer()
                            if let n = s.next { Text("다음 · " + n).lineLimit(1) }
                        }
                        .font(.caption).foregroundStyle(.secondary)
                    }
                }
            } compactLeading: {
                Image(systemName: s.symbol).foregroundStyle(accent)
            } compactTrailing: {
                Text(timerInterval: s.start...s.end, countsDown: true).monospacedDigit().foregroundStyle(accent).frame(maxWidth: 52)
            } minimal: {
                ProgressView(timerInterval: s.start...s.end, countsDown: true) { EmptyView() } currentValueLabel: {
                    Image(systemName: s.symbol).font(.caption2)
                }
                .progressViewStyle(.circular).tint(accent)
            }
            .widgetURL(URL(string: "hihananeo://home"))
            .keylineTint(accent)
        }
    }
}

private struct LockScreenView: View {
    let state: NowActivityAttributes.ContentState

    var body: some View {
        let accent = Color(argb: state.accentARGB)
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: state.symbol).font(.title3.weight(.semibold)).foregroundStyle(.white)
                    .frame(width: 40, height: 40).background(accent, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(state.status).font(.caption.weight(.semibold)).foregroundStyle(accent).lineLimit(1)
                    Text(state.title).font(.headline).foregroundStyle(.white).lineLimit(1)
                    if let r = state.room { Text(r).font(.caption).foregroundStyle(.white.opacity(0.75)).lineLimit(1) }
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(timerInterval: state.start...state.end, countsDown: true).font(.title2.weight(.semibold).monospacedDigit())
                        .foregroundStyle(.white).multilineTextAlignment(.trailing).frame(maxWidth: 100)
                    Text("\(state.end.hm)까지").font(.caption2).foregroundStyle(.white.opacity(0.7))
                }
            }
            ProgressView(timerInterval: state.start...state.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }.tint(accent)
            if let n = state.next { Text("다음 · " + n).font(.caption).foregroundStyle(.white.opacity(0.75)).lineLimit(1) }
        }
        .padding(16)
    }
}

@main
struct HiHanaEvoWidgets: WidgetBundle {
    var body: some Widget {
        NowWidget()
        NowLiveActivity()
    }
}
