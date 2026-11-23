import SwiftUI
import WidgetKit

/*
 * 홈 화면·잠금 화면 위젯 — 안드로이드 HiHanaEvoWidget. 지금 구간과 남은 시간, 진행 막대, 다음 일정.
 * 급식 창이면 그 끼니 메뉴(알레르기 경고 먼저), 저녁 창이면 1타임 면학감독, 귀가 기간이면 안내.
 * 하루의 구간 경계마다 항목을 미리 만들어 두어 앱을 열지 않아도 제때 바뀝니다.
 */

struct NowEntry: TimelineEntry {
    let date: Date
    let snapshot: NowSnapshot
}

struct NowProvider: TimelineProvider {
    func placeholder(in context: Context) -> NowEntry { NowEntry(date: Date(), snapshot: NowSnapshot()) }

    func getSnapshot(in context: Context, completion: @escaping (NowEntry) -> Void) {
        completion(NowEntry(date: Date(), snapshot: NowSnapshot.at(Date(), data: WidgetData.load())))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NowEntry>) -> Void) {
        let data = WidgetData.load()
        let now = Date()
        // 오늘·내일 구간의 시작과, 급식 메뉴 창이 닫히는 때(끝 10분 전)마다 한 장씩.
        var times: Set<Date> = [now]
        for day in [now.localDate, now.localDate.adding(days: 1)] {
            for b in Schedule.blocks(for: day, context: data.context()) where b.end > now {
                if b.start > now { times.insert(b.start) }
                times.insert(b.end)
                if b.isGap { times.insert(b.end.addingTimeInterval(-600)) }
            }
        }
        let horizon = now.addingTimeInterval(30 * 3600)
        let entries = times.filter { $0 >= now && $0 <= horizon }.sorted().prefix(60).map { t in
            NowEntry(date: t, snapshot: NowSnapshot.at(t.addingTimeInterval(1), data: data))
        }
        // 귀가 기간엔 경계가 없어 매시 정각에 다시 — 그 밖엔 마지막 항목 뒤에 새로.
        let next = entries.count <= 1 ? Seoul.calendar.nextDate(after: now, matching: DateComponents(minute: 0), matchingPolicy: .nextTime) ?? now.addingTimeInterval(3600) : entries.last!.date
        completion(Timeline(entries: Array(entries), policy: .after(next)))
    }
}

struct NowWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HiHanaEvoNow", provider: NowProvider()) { entry in
            NowWidgetView(entry: entry)
        }
        .configurationDisplayName("지금 일정")
        .description("지금 구간과 남은 시간, 다음 일정. 급식 시간엔 메뉴를 보여 줍니다.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular, .accessoryInline, .accessoryCircular])
    }
}

// MARK: - 색

/// 위젯 설정 — 테마·강조 색 방식·배경 불투명도(설정 → 위젯).
private struct WidgetStyle {
    let dark: Bool?
    let accentMode: Prefs.WidgetAccent
    let opacity: Double
    let appAccent: Color

    static var current: WidgetStyle {
        let p = Prefs.shared
        return WidgetStyle(dark: p.widgetTheme == .system ? nil : p.widgetTheme == .dark, accentMode: p.widgetAccent,
                           opacity: Double(p.widgetOpacity) / 100, appAccent: Color(argb: p.accentARGB == 0 ? 0xFF007AFF : p.accentARGB))
    }

    func accent(_ a: Accent?, scheme: ColorScheme) -> Color {
        switch accentMode {
        case .kind: a?.color ?? appAccent
        case .app: appAccent
        case .text: scheme == .dark ? .white : Color(argb: 0xFF0F172A)
        }
    }
}

private let allergyRed = Color(argb: 0xFFEF4444)

struct NowWidgetView: View {
    let entry: NowEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var systemScheme

    var body: some View {
        let style = WidgetStyle.current
        let scheme = style.dark.map { $0 ? ColorScheme.dark : .light } ?? systemScheme
        content(style: style, scheme: scheme)
            .environment(\.colorScheme, scheme)
            .containerBackground(for: .widget) {
                (scheme == .dark ? Color.black : Color.white).opacity(style.opacity)
            }
            .widgetURL(URL(string: "hihananeo://home"))
    }

    @ViewBuilder private func content(style: WidgetStyle, scheme: ColorScheme) -> some View {
        let s = entry.snapshot
        switch family {
        case .accessoryInline:
            if let b = s.block, !b.isBlank { Text("\(b.isGap ? b.statusLabel : b.headline) · \(b.end.hm)까지") }
            else { Text(s.homeStay?.shortDetail ?? "일정 없음") }
        case .accessoryCircular:
            if let b = s.block, !b.isBlank {
                ProgressView(timerInterval: b.start...b.end, countsDown: true) { EmptyView() } currentValueLabel: {
                    Image(systemName: b.symbol)
                }
                .progressViewStyle(.circular)
            } else {
                Image(systemName: "moon.zzz")
            }
        case .accessoryRectangular:
            if let b = s.block, !b.isBlank {
                VStack(alignment: .leading, spacing: 1) {
                    Text(b.statusLabel).font(.caption2.weight(.semibold)).widgetAccentable()
                    Text(s.mealText ?? b.title).font(.headline).lineLimit(1)
                    Text(timerInterval: b.start...b.end, countsDown: true).font(.caption.monospacedDigit())
                }
            } else {
                VStack(alignment: .leading) {
                    Text(s.homeStay?.title ?? "현재 일정이 없습니다").font(.headline).lineLimit(1)
                    if let d = s.homeStay?.shortDetail { Text(d).font(.caption) }
                }
            }
        case .systemLarge:
            // 큰 위젯(아이패드 홈 화면 등) — 위는 지금 구간, 아래는 오늘 남은 일정.
            VStack(alignment: .leading, spacing: 12) {
                if let b = s.block, !b.isBlank { schedule(b, s, style: style, scheme: scheme).frame(maxHeight: 170) } else { empty(s).frame(maxHeight: 120) }
                if !s.upcoming.isEmpty {
                    Divider()
                    Text("이후 일정").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    ForEach(Array(s.upcoming.enumerated()), id: \.offset) { _, u in
                        HStack(spacing: 10) {
                            Capsule().fill(style.accent(u.accent, scheme: scheme)).frame(width: 4, height: 30)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(u.headline).font(.subheadline.weight(.semibold)).lineLimit(1)
                                Text([u.timeRangeText, u.room].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                        }
                    }
                }
                Spacer(minLength: 0)
            }
        default:
            if let b = s.block, !b.isBlank {
                schedule(b, s, style: style, scheme: scheme)
            } else {
                empty(s)
            }
        }
    }

    private func schedule(_ b: Block, _ s: NowSnapshot, style: WidgetStyle, scheme: ColorScheme) -> some View {
        let accent = style.accent(b.accent, scheme: scheme)
        return HStack(alignment: .top, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 5) {
                    Image(systemName: b.symbol).font(.caption.weight(.semibold))
                    Text(b.statusLabel).font(.caption.weight(.semibold)).lineLimit(1)
                }
                .foregroundStyle(accent)
                if let menu = s.mealText {
                    (Text(s.allergyLabels.isEmpty ? "" : "\(Image(systemName: "exclamationmark.triangle.fill")) \(s.allergyLabels) ").foregroundStyle(allergyRed).bold() + Text(menu))
                        .font(.subheadline).lineLimit(family == .systemSmall ? 4 : 3)
                } else {
                    Text(b.isGap ? b.title : b.title).font(.title3.bold()).lineLimit(2).minimumScaleFactor(0.7)
                    if let r = b.room { Text(r).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                }
                if let sv = s.supervision { Text(sv).font(.caption2).foregroundStyle(.secondary).lineLimit(1) }
                Spacer(minLength: 0)
                HStack(alignment: .firstTextBaseline) {
                    Text(timerInterval: b.start...b.end, countsDown: true).font(.title3.weight(.semibold).monospacedDigit()).foregroundStyle(accent)
                    Spacer(minLength: 0)
                    Text(b.end.hm).font(.caption2).foregroundStyle(.secondary)
                }
                ProgressView(timerInterval: b.start...b.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                    .tint(accent)
            }
            if family == .systemMedium, let n = s.next {
                VStack(alignment: .leading, spacing: 4) {
                    Text("다음").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Text(n.title).font(.headline).lineLimit(2)
                    Text([n.start.hm, n.room].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func empty(_ s: NowSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: s.homeStay != nil ? "house.fill" : "moon.zzz.fill").font(.title3).foregroundStyle(.secondary)
            Spacer(minLength: 0)
            Text(s.homeStay?.title ?? "현재 일정이 없습니다").font(.headline).lineLimit(2)
            Text(s.homeStay?.detail ?? s.next.map { "다음 · \($0.start.hm) \($0.title)" } ?? "").font(.caption).foregroundStyle(.secondary).lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
