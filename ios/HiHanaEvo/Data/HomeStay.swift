import Foundation

/// 귀가 기간 — 학사일정의 귀가·귀교로 날짜별 상태를 정합니다(안드로이드 HomeStay.kt 와 같은 규칙).
/// 학년은 이름 앞("1,2학년 귀가")이나 괄호("귀교(1,2,3)")에서 읽고, 내 학년이 없으면 무시합니다.
/// 같은 학사일정의 공휴일(빨간날)도 함께 돌려줘 평일 공휴일에 휴일 일과가 뜨게 합니다.
enum HomeStaySchedule {
    private static let holidayCode = "SG01020000"
    private static let historyCache = "homestay_history"
    /// 지나간 귀가·귀교를 기억하는 기간.
    private static let historyDays = 45

    /// 1: 앞의 학년, 2: 귀가|귀교, 3: 괄호 안. 이름이 귀가·귀교로 끝나야 합니다("코람데오 귀가 예배"는 빠짐).
    private static let pattern = try! NSRegularExpression(pattern: #"^(?:([1-3](?:\s*[,·]\s*[1-3])*)\s*학년\s*)?.*?(귀가|귀교)\s*(?:\(([^)]*)\))?$"#)
    /// 괄호 안 학년 목록 — "1,2" · "1, 2학년" · "3학년 제외". 이 꼴이 아니면(전학년, 13시 이후) 학년 표기가 아닙니다.
    private static let gradeNote = try! NSRegularExpression(pattern: #"^([1-3](?:\s*[,·]\s*[1-3])*)\s*(?:학년)?\s*(제외)?$"#)

    static func holidays(_ entries: [HanaScheduleEntry]) -> [LocalDate: String] {
        var out: [LocalDate: String] = [:]
        for e in entries where e.code == holidayCode { out[e.date] = e.name.trimmingCharacters(in: .whitespaces) }
        return out
    }

    struct Mark: Equatable { let date: LocalDate; let leave: Bool; let grades: Set<Int> }

    /// 학사(SG01…)와 학교 행사(SG02010000)만 — 장소 예약·감독 배정은 이름이 귀가로 끝나도 아닙니다.
    private static func isAcademic(_ code: String) -> Bool { code.hasPrefix("SG01") || code == "SG02010000" }

    static func parse(_ e: HanaScheduleEntry) -> Mark? {
        guard isAcademic(e.code) else { return nil }
        let name = e.name.precomposedStringWithCompatibilityMapping.trimmingCharacters(in: .whitespaces)
        let ns = name as NSString
        guard let m = pattern.firstMatch(in: name, range: NSRange(location: 0, length: ns.length)) else { return nil }
        func group(_ i: Int) -> String { m.range(at: i).location == NSNotFound ? "" : ns.substring(with: m.range(at: i)) }
        let prefix = Set(group(1).compactMap(\.wholeNumberValue))
        let note = group(3).trimmingCharacters(in: .whitespaces)
        var grades = prefix
        if let n = gradeNote.firstMatch(in: note, range: NSRange(location: 0, length: (note as NSString).length)) {
            let listed = Set((note as NSString).substring(with: n.range(at: 1)).compactMap(\.wholeNumberValue))
            grades = n.range(at: 2).location != NSNotFound ? Set(1...3).subtracting(listed) : listed
        }
        return Mark(date: e.date, leave: group(2) == "귀가", grades: grades)
    }

    // MARK: 지나간 귀가·귀교 기억 — 학사일정은 어제부터만 받아, 귀가 기간 한가운데에 받으면 귀가일이 빠집니다.

    private static var history: [HanaScheduleEntry] = JSONCache.read([HanaScheduleEntry].self, historyCache)?.value ?? []

    /// 받은 학사일정의 귀가·귀교를 기억에 더합니다. 범위 안은 새로 받은 것만 믿고(학교가 지운 귀가가 남지 않게), 앞선 날만 이어 붙입니다.
    static func remember(_ entries: [HanaScheduleEntry], today: LocalDate = .today()) {
        let merged = merge(history, entries, today: today)
        guard merged != history else { return }
        history = merged
        JSONCache.write(merged, historyCache)
    }

    static func merge(_ stored: [HanaScheduleEntry], _ entries: [HanaScheduleEntry], today: LocalDate) -> [HanaScheduleEntry] {
        let fresh = entries.filter { parse($0) != nil }
        let rangeStart = entries.map(\.date).min()
        let keepFrom = today.adding(days: -historyDays)
        var seen = Set<String>()
        return (stored.filter { rangeStart == nil || $0.date < rangeStart! } + fresh)
            .filter { $0.date >= keepFrom }
            .filter { seen.insert($0.date.iso + "|" + $0.name).inserted }
            .sorted { ($0.date, $0.name) < ($1.date, $1.name) }
    }

    /// 날짜별 귀가 상태 — 귀교마다 그 앞(직전 귀교 뒤)의 가장 가까운 귀가와 짝을 짓고, 짝이 없으면 아는 날만 표시합니다
    /// (앞뒤를 추정해 비우지 않습니다 — 예전엔 귀교 열흘 전부터 비워 평일을 귀가로 띄웠습니다).
    static func compute(_ entries: [HanaScheduleEntry], grade: Int) -> [LocalDate: Schedule.HomeStay] {
        compute(marks: merge(history, entries, today: .today()).compactMap(parse), grade: grade)
    }

    static func compute(marks: [Mark], grade: Int) -> [LocalDate: Schedule.HomeStay] {
        let mine = marks.filter { $0.grades.isEmpty || $0.grades.contains(grade) }
        let leaves = Array(Set(mine.filter(\.leave).map(\.date))).sorted()
        let returns = Array(Set(mine.filter { !$0.leave }.map(\.date))).sorted()
        var map: [LocalDate: Schedule.HomeStay] = [:]
        for leave in leaves { map[leave] = .leave }
        for back in returns {
            let previousReturn = returns.last { $0 < back }
            if let leave = leaves.last(where: { $0 < back }), previousReturn == nil || leave > previousReturn! {
                var day = leave.adding(days: 1)
                while day < back { map[day] = .away; day = day.adding(days: 1) }
            }
            map[back] = .return
        }
        return map
    }
}

/// 날씨 한 칸 — "지금 서울", "17°", "맑음 · 25° / 13°", "15시부터 비".
struct WeatherTileInfo: Equatable { let label: String; let symbol: String; let temp: String?; let desc: String; let tip: String? }

/// 귀가 기간 안내 아래 — 서울 날씨 두 칸(지금 · 귀교 밤 9시)과 귀교 후 첫 일정·귀교 뒤 2주 안의 시험.
struct HomeStayExtras: Equatable {
    var weatherNow: WeatherTileInfo?
    var weatherBack: WeatherTileInfo?
    /// 귀교 후 첫 일정과 시작 시각.
    var firstPlan: (String, String)?
    /// 다가오는 시험과 남은 날.
    var exam: (String, Int)?
    /// 귀교 시각 — 날씨 칸을 누르면 여기까지 시간별 날씨.
    var backAt: Date?

    var isEmpty: Bool { weatherNow == nil && weatherBack == nil && firstPlan == nil && exam == nil }

    static func == (a: Self, b: Self) -> Bool {
        a.weatherNow == b.weatherNow && a.weatherBack == b.weatherBack && a.firstPlan?.0 == b.firstPlan?.0 && a.firstPlan?.1 == b.firstPlan?.1
            && a.exam?.0 == b.exam?.0 && a.exam?.1 == b.exam?.1 && a.backAt == b.backAt
    }

    /// 안내가 뜨는 때에만 — 날씨·학사일정은 캐시만 읽어 바로 그립니다.
    static func build(today: LocalDate, now: Date, context: Schedule.Context, schedule: [HanaScheduleEntry], blocksFor: (LocalDate) -> [Block]) -> HomeStayExtras? {
        guard homeStayNotice(today: today, now: now, context: context) != nil, let stay = context.homeStay[today] else { return nil }
        let back = stay == .return ? today : context.nextHomeReturn(from: today.adding(days: 1))
        let f = Weather.cached(.seoul)
        var x = HomeStayExtras()
        x.weatherNow = f.flatMap { nowTile($0, today, now) }
        x.weatherBack = f.flatMap { f in back.flatMap { backTile(f, $0) } }
        if let back {
            let cutoff = back.at(minutes: Schedule.homeReturnAt)
            if let b = blocksFor(back).first(where: { !$0.isBlank && !$0.isGap && $0.start >= cutoff }) {
                let place = [b.title, b.room ?? ""].filter { !$0.isEmpty }.joined(separator: " ")
                x.firstPlan = ([b.statusLabel, place].filter { !$0.isEmpty }.joined(separator: " · "), b.start.hm)
            }
            if let e = examDday(schedule, today: today), e.date >= back, e.date <= back.adding(days: 14) { x.exam = (e.name, e.days) }
            x.backAt = cutoff
        }
        return x.isEmpty ? nil : x
    }

    private static func umbrella(_ code: Int?) -> String { Weather.kind(code) == .snow ? "눈 소식 · 우산 챙기세요" : "우산 챙기세요" }
    private static func precip(_ code: Int?) -> String { Weather.kind(code) == .snow ? "눈" : "비" }

    private static func nowTile(_ f: Weather.Forecast, _ today: LocalDate, _ now: Date) -> WeatherTileInfo? {
        let hourStart = Seoul.calendar.dateInterval(of: .hour, for: now)?.start ?? now
        let rest = f.hours.filter { $0.time.localDate == today && $0.time >= hourStart }
        let nearest = rest.first ?? f.hours.first { $0.time >= hourStart }
        guard let code = f.currentCode ?? nearest?.code else { return nil }
        let day = f.days.first { $0.date == today }
        let firstWet = rest.dropFirst().first { Weather.wet($0.code, $0.pop) }
        let tip: String? = Weather.wet(code, nil) ? umbrella(code) : firstWet.map { "\($0.time.hour)시부터 \(precip($0.code))" }
        let range = day.flatMap { d in d.max != nil && d.min != nil ? "\(Weather.deg(d.max)!) / \(Weather.deg(d.min)!)" : nil }
        return WeatherTileInfo(label: "지금 서울", symbol: Weather.symbol(code), temp: Weather.deg(f.currentTemp ?? nearest?.temp),
                               desc: [Weather.label(code), range].compactMap { $0 }.joined(separator: " · "), tip: tip)
    }

    private static func backTile(_ f: Weather.Forecast, _ back: LocalDate) -> WeatherTileInfo? {
        let atReturn = back.at(minutes: 21 * 60)
        let hour = f.hours.first { $0.time == atReturn }
        let day = f.days.first { $0.date == back }
        if hour == nil && day == nil { return nil }
        let travel = f.hours.filter { $0.time.localDate == back && (15...21).contains($0.time.hour) }
        let code = hour?.code ?? day?.code
        let pop = travel.compactMap(\.pop).max() ?? day?.pop
        let wetCode = Weather.worst(travel.map(\.code).filter { Weather.wet($0, nil) })
        let wet = wetCode != nil || Weather.wet(code, pop)
        let desc: String
        if hour != nil {
            desc = [Weather.label(code), pop.flatMap { $0 > 0 ? "강수 \($0)%" : nil }].compactMap { $0 }.joined(separator: " · ")
        } else {
            desc = [Weather.label(code), day.flatMap { d in d.max != nil && d.min != nil ? "\(Weather.deg(d.max)!) / \(Weather.deg(d.min)!)" : nil }].compactMap { $0 }.joined(separator: " · ")
        }
        return WeatherTileInfo(label: hour != nil ? "귀교 밤 9시" : "귀교일", symbol: Weather.symbol(wetCode ?? code), temp: Weather.deg(hour?.temp ?? day?.max),
                               desc: desc, tip: wet ? umbrella(wetCode ?? code) : nil)
    }
}
