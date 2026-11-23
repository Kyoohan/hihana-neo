import SwiftUI

/*
 * 날씨 — 구성과 치수는 삼성 날씨(One UI) 상세 화면을 따르되, 바탕은 앱의 다른 화면과 같은 페이지 배경·카드 재질입니다.
 * 날씨 색은 포인트로만 — 위쪽 정보 뒤의 빛무리, 기온 곡선, 최고/최저 화살표. 강수는 늘 파란색.
 */

private let rain = Color(argb: 0xFF3D8BFF)
private let hourWidth: CGFloat = 52
private let sideInset: CGFloat = 23

/// 날씨 종류의 포인트 색 — 아이콘의 주조색과 같게.
private func accent(_ kind: Weather.Kind) -> Color {
    switch kind {
    case .clear: Color(argb: 0xFFFFB224)
    case .partly: Color(argb: 0xFFFFC25C)
    case .cloudy, .fog: Color(argb: 0xFF9AAABF)
    case .rain: rain
    case .snow: Color(argb: 0xFF8CCBFF)
    case .storm: Color(argb: 0xFF8A7CF0)
    }
}

private func deg(_ v: Double?) -> String { Weather.deg(v) ?? "–" }

/// "밤 9시" / "오전 7시" / "낮 12시".
private func hourLabel(_ h: Int) -> String {
    switch h {
    case 0: "자정"
    case ..<12: "오전 \(h)시"
    case 12: "낮 12시"
    case ..<18: "오후 \(h - 12)시"
    default: "밤 \(h - 12)시"
    }
}

/// 날씨 시트의 제목 — 귀교 시각까지 보는 화면(until)은 귀가 기간이라 서울, 그 밖에는 지금 있는 곳(평소 하나고).
@MainActor
func weatherPlace(until: Date?, model: AppModel) -> Weather.Place {
    if until != nil { return .seoul }
    return homeStayNotice(today: model.today, now: Date(), context: model.context()) != nil ? .seoul : .hana
}

/// 날씨 — 지금 날씨를 크게, 그 아래 귀교 시각([until])까지 1시간 단위 예보와 요일별 예보, 귀교 길 지표.
struct WeatherView: View {
    let place: Weather.Place
    let until: Date?
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage
    @State private var forecast: Weather.Forecast?
    @State private var failed = false
    @State private var now = Date()

    init(place: Weather.Place, until: Date?) {
        self.place = place; self.until = until
        _forecast = State(initialValue: Weather.cached(place))
    }

    var body: some View {
        let today = now.localDate
        let hourStart = Seoul.calendar.dateInterval(of: .hour, for: now)?.start ?? now
        let hours = (forecast?.hours ?? []).filter { $0.time >= hourStart && (until == nil || $0.time <= until!) }
        let code = forecast?.currentCode ?? hours.first?.code
        let color = accent(Weather.kind(code))
        SheetScroll(padding: EdgeInsets(top: 4, leading: 0, bottom: 28, trailing: 0)) {
            if let f = forecast {
                TopInfo(f: f, today: today, now: now, hours: hours, until: until, accent: color)
                VStack(spacing: 10) {
                    if !hours.isEmpty {
                        OneUiCard(padding: EdgeInsets()) {
                            SectionLabel(text: "시간별", leading: 20)
                            HourlyStrip(hours: hours, now: now, until: until, accent: color)
                            if let until, let last = hours.last, last.time < until {
                                Text("그 뒤는 아직 시간별 예보가 없어 아래 요일별 예보를 봐 주세요")
                                    .font(.ui(.labelSmall)).foregroundStyle(palette.onSurfaceVariant)
                                    .padding(.horizontal, 20).padding(.bottom, 14)
                            } else {
                                Color.clear.frame(height: 10)
                            }
                        }
                    }
                    let lastDay = until?.localDate ?? f.days.last?.date
                    let days = f.days.filter { $0.date >= today && (lastDay == nil || $0.date <= lastDay!) }
                    if !days.isEmpty {
                        OneUiCard(padding: EdgeInsets()) {
                            SectionLabel(text: "요일별", leading: 22)
                            ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                                if i > 0 { OneUiDivider().padding(.leading, 22 - OneUi.rowPadding) }
                                DayRow(d: d, today: today, isReturn: d.date == until?.localDate)
                            }
                            Color.clear.frame(height: 6)
                        }
                    }
                    IndexTiles(f: f, today: today, hours: hours, until: until)
                }
                .padding(.horizontal, OneUi.pagePadding)
                .padding(.top, 22)
                let updated = f.updated.map { d -> String in
                    let c = Seoul.calendar.dateComponents([.hour, .minute], from: d)
                    return String(format: "%02d:%02d 기준", c.hour ?? 0, c.minute ?? 0)
                }
                Text([f.source.map { $0.hasPrefix("kma") } ?? true ? "기상청 단기예보" : "Open-Meteo (기상청 장애 중 대신)", updated].compactMap { $0 }.joined(separator: " · "))
                    .font(.ui(.labelSmall)).onPageVariant(palette, onPage)
                    .frame(maxWidth: .infinity).padding(.top, 16)
            } else if !failed {
                HStack(spacing: 10) {
                    OneUiLoading(size: 18, stroke: 2)
                    Text("날씨를 불러오는 중").font(.ui(.bodyMedium))
                }
                .padding(.horizontal, sideInset).padding(.vertical, 24)
            } else {
                Text("날씨를 불러오지 못했습니다").font(.ui(.bodyMedium)).onPageVariant(palette, onPage)
                    .padding(.horizontal, sideInset).padding(.vertical, 24)
            }
        }
        .task {
            // 30분이 지났으면 새로 받습니다(실패하면 캐시 그대로).
            if let f = await Weather.get(place) { forecast = f }
            failed = forecast == nil
        }
    }
}

private struct SectionLabel: View {
    let text: String
    let leading: CGFloat
    @Environment(\.palette) private var palette
    var body: some View {
        Text(text).font(.ui(.labelMedium, .semibold)).foregroundStyle(palette.onSurfaceVariant)
            .padding(.leading, leading).padding(.top, 14).padding(.trailing, 20).padding(.bottom, 4)
    }
}

/// 위쪽 정보 — 날씨 색 빛무리 위에 큰 기온, 최고/최저(화살표), 날씨 문구, 비 소식·귀교 줄. 아이콘은 오른쪽에 크게.
private struct TopInfo: View {
    let f: Weather.Forecast
    let today: LocalDate
    let now: Date
    let hours: [Weather.Hour]
    let until: Date?
    let accent: Color
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    var body: some View {
        let code = f.currentCode ?? hours.first?.code
        let day = f.days.first { $0.date == today }
        let firstWet = hours.dropFirst().first { Weather.wet($0.code, $0.pop) }
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(deg(f.currentTemp ?? hours.first?.temp)).font(.system(size: 70, weight: .regular)).kerning(-1).frame(height: 75)
                if let max = day?.max, let min = day?.min {
                    HStack(spacing: 0) {
                        Image(systemName: "arrow.up").font(.system(size: 11, weight: .bold)).foregroundStyle(accent).padding(.trailing, 2)
                        Text(deg(max))
                        Text(" / ").onPageVariant(palette, onPage)
                        Image(systemName: "arrow.down").font(.system(size: 11, weight: .bold)).foregroundStyle(rain).padding(.trailing, 2)
                        Text(deg(min))
                    }
                    .font(.system(size: 13, weight: .semibold))
                }
                Text(Weather.label(code)).font(.system(size: 20, weight: .semibold))
                if let extra = extraLine(code: code, firstWet: firstWet) {
                    Text(extra).font(.system(size: 13, weight: .semibold)).foregroundStyle(rain)
                }
                // 지난 한 시간 동안 실제로 온 비 — 기상청 실황(가까운 관측소).
                if let r = f.currentRain {
                    Text("지난 1시간 강수량 \(Weather.mm(r))").font(.system(size: 13, weight: .semibold)).foregroundStyle(rain)
                }
                Text(bottomLine).font(.system(size: 13, weight: .semibold)).onPageVariant(palette, onPage)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: Weather.symbol(code)).symbolRenderingMode(.multicolor).font(.system(size: 72)).frame(width: 92, height: 92).padding(.top, 10)
        }
        .padding(.horizontal, sideInset).padding(.top, 12)
        .background {
            // 날씨 색은 아이콘 뒤에서 번지는 빛으로만 — 이 영역 안에서 다 사라지게 반지름을 가장자리까지 거리보다 작게.
            GeometryReader { g in
                let c = CGPoint(x: g.size.width * 0.74, y: g.size.height * 0.46)
                let r = min(g.size.width - c.x, c.y, g.size.height - c.y)
                RadialGradient(stops: [
                    .init(color: accent.opacity(palette.dark ? 0.34 : 0.36), location: 0),
                    .init(color: accent.opacity(0.10), location: 0.6),
                    .init(color: .clear, location: 1),
                ], center: UnitPoint(x: 0.74, y: 0.46), startRadius: 0, endRadius: max(r, 1))
            }
        }
    }

    private func extraLine(code: Int?, firstWet: Weather.Hour?) -> String? {
        if Weather.wet(code, nil) { return "지금 \(Weather.label(code)) · 우산 챙기세요" }
        guard let w = firstWet else { return nil }
        let d = w.time.localDate
        let word = d == today ? "" : d == today.adding(days: 1) ? "내일 " : "\(d.month)/\(d.day) "
        return "\(word)\(w.time.hour)시부터 \(Weather.wet(w.code, nil) ? Weather.label(w.code) : "비 소식")"
    }

    private var bottomLine: String {
        if let until {
            let d = until.localDate
            return "귀교 \(d.month)/\(d.day)(\(d.weekdayShort)) \(hourLabel(until.hour))까지"
        }
        return today.longText
    }
}

/// 1시간 단위 가로 띠 — 칸마다 시각(날이 바뀌면 '내일'·'10/19'), 아이콘, 강수(물방울+확률), 기온, 그 아래 기온 곡선. 귀교 칸은 강조색 바탕.
private struct HourlyStrip: View {
    let hours: [Weather.Hour]
    let now: Date
    let until: Date?
    let accent: Color
    @Environment(\.palette) private var palette
    private let curveHeight: CGFloat = 50

    var body: some View {
        let today = now.localDate
        let returnIndex = until.flatMap { u in hours.firstIndex { $0.time == u } } ?? -1
        ScrollView(.horizontal, showsIndicators: false) {
            VStack(spacing: 0) {
                HStack(spacing: 0) {
                    ForEach(Array(hours.enumerated()), id: \.offset) { i, h in
                        let midnight = h.time.hour == 0
                        let d = h.time.localDate
                        let label = i == 0 ? "지금" : i == returnIndex ? "귀교"
                            : midnight ? (d == today.adding(days: 1) ? "내일" : d == today.adding(days: 2) ? "모레" : "\(d.month)/\(d.day)")
                            : "\(h.time.hour)시"
                        let strong = i == 0 || i == returnIndex || midnight
                        Text(label).font(.system(size: 10, weight: strong ? .semibold : .regular))
                            .foregroundStyle(i == returnIndex ? palette.primary : strong ? palette.onSurface : palette.onSurfaceVariant)
                            .lineLimit(1).frame(width: hourWidth)
                    }
                }
                HStack(spacing: 0) {
                    ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                        Image(systemName: Weather.symbol(h.code)).symbolRenderingMode(.multicolor).font(.system(size: 19)).frame(width: hourWidth, height: 24)
                    }
                }
                .padding(.top, 6)
                HStack(spacing: 0) {
                    ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                        HStack(spacing: 1) {
                            if let pop = h.pop, pop > 0 {
                                Image(systemName: "drop.fill").font(.system(size: 8))
                                Text("\(pop)%").font(.system(size: 10, weight: .semibold)).lineLimit(1)
                            }
                        }
                        .foregroundStyle(rain).frame(width: hourWidth, height: 14)
                    }
                }
                .padding(.top, 4)
                // 비·눈이 오는 시간에만 강수량 줄(눈이면 적설) — 한 칸도 없으면 줄을 두지 않습니다.
                if hours.contains(where: { $0.rainText != nil || $0.snowText != nil }) {
                    HStack(spacing: 0) {
                        ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                            Text(h.snowText ?? h.rainText ?? "").font(.system(size: 9, weight: .semibold)).foregroundStyle(rain)
                                .lineLimit(1).minimumScaleFactor(0.8).frame(width: hourWidth)
                        }
                    }
                }
                HStack(spacing: 0) {
                    ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                        Text(deg(h.temp)).font(.system(size: 12, weight: .semibold)).foregroundStyle(palette.onSurface).lineLimit(1).frame(width: hourWidth)
                    }
                }
                .padding(.top, 2)
                curve.frame(width: hourWidth * CGFloat(hours.count), height: curveHeight).padding(.top, 4)
            }
            .padding(.vertical, 6)
            .background(alignment: .leading) {
                if returnIndex >= 0 {
                    RoundedRectangle(cornerRadius: 14, style: .continuous).fill(palette.primary.opacity(0.12))
                        .frame(width: hourWidth).offset(x: hourWidth * CGFloat(returnIndex))
                }
            }
            .padding(.horizontal, 8).padding(.vertical, 6)
        }
    }

    /// 기온 곡선 — 칸 가운데 점들을 부드러운 곡선(캣멀-롬 → 베지어)으로, 날씨 색으로 잇습니다.
    private var curve: some View {
        let known = hours.compactMap(\.temp)
        let hi = known.max() ?? 0, lo = known.min() ?? 0
        let span = hi - lo > 0.5 ? hi - lo : 1
        return Canvas { ctx, size in
            let top: CGFloat = 8, bottom = size.height - 8
            let pts: [CGPoint] = hours.enumerated().compactMap { i, h in
                guard let t = h.temp else { return nil }
                return CGPoint(x: hourWidth * CGFloat(i) + hourWidth / 2, y: bottom - CGFloat((t - lo) / span) * (bottom - top))
            }
            if pts.count >= 2 {
                var path = Path()
                path.move(to: pts[0])
                for i in 0..<(pts.count - 1) {
                    let p0 = pts[max(i - 1, 0)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[min(i + 2, pts.count - 1)]
                    path.addCurve(to: p2,
                                  control1: CGPoint(x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6),
                                  control2: CGPoint(x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6))
                }
                ctx.stroke(path, with: .color(accent), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
            }
            for p in pts { ctx.fill(Path(ellipseIn: CGRect(x: p.x - 2.5, y: p.y - 2.5, width: 5, height: 5)), with: .color(accent)) }
        }
    }
}

/// 요일별 한 줄 — 요일, 강수, 아이콘, 날씨, 최고/최저.
private struct DayRow: View {
    let d: Weather.Day
    let today: LocalDate
    let isReturn: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        let name = d.date == today ? "오늘" : d.date == today.adding(days: 1) ? "내일" : d.date.weekdayShort
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(name).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                if isReturn { Text("귀교").font(.system(size: 10, weight: .semibold)).foregroundStyle(palette.primary) }
            }
            .frame(width: 64, alignment: .leading)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 2) {
                    if let pop = d.pop, pop > 0 {
                        Image(systemName: "drop.fill").font(.system(size: 8))
                        Text("\(pop)%").font(.system(size: 10, weight: .semibold)).lineLimit(1)
                    }
                }
                // 그날 예상 강수량(시간별 합) — 눈이면 적설.
                if let amount = d.snow.map({ "약 \(Weather.cm($0))" }) ?? d.rain.map({ "약 \(Weather.mm($0))" }) {
                    Text(amount).font(.system(size: 10, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.8)
                }
            }
            .foregroundStyle(rain).frame(width: 56, alignment: .leading)
            Spacer(minLength: 0)
            Image(systemName: Weather.symbol(d.code)).symbolRenderingMode(.multicolor).font(.system(size: 19)).frame(width: 24, height: 24)
            Text(Weather.label(d.code)).font(.system(size: 12)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1).frame(width: 56, alignment: .leading).padding(.leading, 12)
            Spacer(minLength: 0)
            Text(deg(d.max)).font(.system(size: 15, weight: .semibold)).frame(width: 34, alignment: .trailing)
            Text(" / ").font(.system(size: 15)).foregroundStyle(palette.onSurfaceVariant)
            Text(deg(d.min)).font(.system(size: 15)).foregroundStyle(palette.onSurfaceVariant).frame(width: 30, alignment: .trailing)
        }
        .foregroundStyle(palette.onSurface)
        .padding(.horizontal, 22).padding(.vertical, 10)
    }
}

/// 지표 타일 — 귀교 시각 날씨, 돌아오는 길 강수, 오늘 최고/최저(귀가 기간이 아니면 최대 강수확률), 지금 체감온도·습도·바람.
/// 삼성 날씨 지표 카드의 꼴 — 값 아래 한 줄 풀이는 Apple 날씨의 '체감 온도'·'습도'·'바람' 타일처럼.
private struct IndexTiles: View {
    let f: Weather.Forecast
    let today: LocalDate
    let hours: [Weather.Hour]
    let until: Date?
    @Environment(\.palette) private var palette

    private struct Tile { let title: String; let value: String; let sub: String?; let symbol: String?; let valueColor: Color? }

    private var tiles: [Tile] {
        var out: [Tile] = []
        if let u = until {
            let at = f.hours.first { $0.time == u }
            let day = f.days.first { $0.date == u.localDate }
            if let at {
                out.append(Tile(title: "귀교 \(hourLabel(u.hour))", value: deg(at.temp),
                                sub: [Weather.label(at.code), at.pop.flatMap { $0 > 0 ? "강수 \($0)%" : nil }].compactMap { $0 }.joined(separator: " · "),
                                symbol: Weather.symbol(at.code), valueColor: nil))
            } else if let day {
                out.append(Tile(title: "귀교일", value: "\(deg(day.max)) / \(deg(day.min))", sub: Weather.label(day.code), symbol: Weather.symbol(day.code), valueColor: nil))
            }
            let from = max(u.hour - 6, 0)
            let travel = f.hours.filter { $0.time.localDate == u.localDate && (from...u.hour).contains($0.time.hour) }
            if let pop = travel.compactMap(\.pop).max() ?? day?.pop {
                let wet = travel.contains { Weather.wet($0.code, nil) } || pop >= 50
                out.append(Tile(title: "돌아오는 길 강수", value: "\(pop)%", sub: wet ? "우산 챙기세요" : "\(from)–\(u.hour)시 최대", symbol: nil, valueColor: rain))
            }
        }
        if let d = f.days.first(where: { $0.date == today }), d.max != nil, d.min != nil {
            out.append(Tile(title: "오늘 최고 / 최저", value: "\(deg(d.max)) / \(deg(d.min))", sub: d.pop.flatMap { $0 > 0 ? "강수 \($0)%" : nil }, symbol: nil, valueColor: nil))
        }
        if let maxPop = hours.compactMap(\.pop).max(), until == nil {
            out.append(Tile(title: "최대 강수확률", value: "\(maxPop)%", sub: "앞으로 \(hours.count)시간", symbol: nil, valueColor: rain))
        }
        // 지금 — 체감온도·습도·바람 (기상청 실황).
        if let feels = f.currentFeels {
            out.append(Tile(title: "체감온도", value: deg(feels), sub: Weather.feelsNote(feels: feels, temp: f.currentTemp), symbol: nil, valueColor: nil))
        }
        if let h = f.currentHumidity {
            out.append(Tile(title: "습도", value: "\(h)%", sub: Weather.humidityNote(h), symbol: nil, valueColor: nil))
        }
        if let w = f.currentWind {
            out.append(Tile(title: "바람", value: Weather.speed(w), sub: Weather.windNote(speed: w, dir: f.currentWindDir), symbol: nil, valueColor: nil))
        }
        return out
    }

    var body: some View {
        let list = tiles
        Grid(horizontalSpacing: 10, verticalSpacing: 10) {
            ForEach(Array(stride(from: 0, to: list.count, by: 2)), id: \.self) { i in
                GridRow {
                    tile(list[i])
                    if i + 1 < list.count { tile(list[i + 1]) } else { Color.clear.gridCellUnsizedAxes([.vertical]) }
                }
            }
        }
    }

    private func tile(_ t: Tile) -> some View {
        OneUiCard(padding: EdgeInsets(top: 12, leading: 20, bottom: 18, trailing: 20)) {
            Text(t.title).font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1)
            HStack(spacing: 8) {
                if let s = t.symbol { Image(systemName: s).symbolRenderingMode(.multicolor).font(.system(size: 26)).frame(width: 34, height: 34) }
                Text(t.value).font(.system(size: 24, weight: .semibold)).foregroundStyle(t.valueColor ?? palette.onSurface).lineLimit(1).minimumScaleFactor(0.7)
            }
            .padding(.top, 8)
            if let sub = t.sub { Text(sub).font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant).lineLimit(2).padding(.top, 4) }
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .frame(minHeight: 90)
    }
}
