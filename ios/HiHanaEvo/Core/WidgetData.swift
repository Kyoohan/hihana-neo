import Foundation
#if canImport(ActivityKit)
import ActivityKit
#endif

/// 위젯이 그릴 때 쓰는 값 — 앱이 학사일정·급식을 받을 때마다 공유 저장소에 써 두고, 위젯은 이것과 시간표·면학 위치·심야면학
/// 캐시만으로 일과를 계산합니다(위젯은 네트워크를 타지 않습니다).
struct WidgetData: Codable, Equatable {
    /// 학사일정의 빨간날(날짜 iso → 이름).
    var holidays: [String: String] = [:]
    /// 날짜별 귀가 상태.
    var homeStay: [String: Schedule.HomeStay] = [:]
    /// 오늘·내일 급식.
    var meals: [String: DayMeals] = [:]
    /// 날짜별 평일 1타임 면학감독(저녁 창에 한 줄).
    var supervision: [String: String] = [:]
    /// 외출·외박.
    var outings: [OutingSpan] = []

    static let name = "widget_data"

    static func load() -> WidgetData { JSONCache.read(WidgetData.self, name)?.value ?? WidgetData() }

    /// 일과 계산에 필요한 바깥 정보 — 앱의 AppModel.context() 와 같은 값을 캐시에서.
    func context() -> Schedule.Context {
        let week = JSONCache.read(TimetableWeek.self, "timetable_latest")?.value
        let midnightRaw = JSONCache.read([String: [Schedule.MidnightBooking]].self, "midnight_schedule")?.value ?? [:]
        let all = PlaceStore.all()
        return Schedule.Context(
            week: week,
            holidays: Dictionary(uniqueKeysWithValues: holidays.compactMap { k, v in LocalDate(iso: k).map { ($0, v) } }),
            placeFor: { date, slot in all[date.iso]?[slot.rawValue].flatMap(StudyPlace.init(storageValue:)) ?? slot.defaultPlace },
            midnight: Dictionary(uniqueKeysWithValues: midnightRaw.compactMap { k, v in LocalDate(iso: k).map { ($0, v) } }),
            homeStay: Dictionary(uniqueKeysWithValues: homeStay.compactMap { k, v in LocalDate(iso: k).map { ($0, v) } }),
            outings: outings)
    }
}

/// 위젯·실시간 활동이 보여 줄 '지금' — 블록, 급식 창이면 그 끼니 메뉴, 저녁 창이면 면학감독, 다음 일정.
struct NowSnapshot: Equatable {
    var block: Block?
    var next: Block?
    /// 지금 뒤의 일정 몇 개 — 큰 위젯의 목록.
    var upcoming: [Block] = []
    var meal: Meal?
    var mealText: String?
    /// 고른 알레르기가 든 메뉴면 그 이름들("땅콩·우유") — 위젯이 경고 기호와 함께 그립니다.
    var allergyLabels = ""
    var supervision: String?
    var homeStay: HomeStayNotice?

    /// 다시 그려야 할 때 — 급식 메뉴 창은 끝 10분 전에 닫히고(간식은 끝까지), 그 밖에는 블록 끝.
    var boundary: Date? {
        guard let block else { return nil }
        if let meal, meal != .snack { return block.end.addingTimeInterval(-600) }
        return block.end
    }

    static func at(_ now: Date, data: WidgetData) -> NowSnapshot {
        let today = now.localDate
        let ctx = data.context()
        let blocks = Schedule.blocks(for: today, context: ctx)
        let block = Schedule.block(at: now, in: blocks)
        var s = NowSnapshot(block: block, next: Schedule.nextEvent(blocks: blocks, current: block, time: now))
        s.upcoming = Array(blocks.filter { $0.start > now && !$0.isBlank && !$0.isGap }.prefix(5))
        if block == nil || block?.isBlank == true { s.homeStay = homeStayNotice(today: today, now: now, context: ctx) }
        guard let block, case .gap(let gap) = block.kind else { return s }
        // 급식 창(아침·점심·저녁은 끝 10분 전까지, 간식은 끝까지)이면 그 끼니 메뉴.
        let meal: Meal? = switch gap.accent { case .breakfast: .breakfast; case .lunch: .lunch; case .dinner: .dinner; case .snack: .snack; default: nil }
        if let meal, now <= (meal == .snack ? block.end : block.end.addingTimeInterval(-600)), let items = data.meals[today.iso]?.items[meal], !items.isEmpty {
            s.meal = meal
            s.mealText = items.map(\.name).joined(separator: " ")
            let selected = Prefs.shared.allergyCodes
            let labels = Allergens.shortLabels(Allergens.detect(codes: Set(items.flatMap(\.codes)), menuText: s.mealText ?? "", selected: selected))
            s.allergyLabels = labels.joined(separator: "·")
        }
        // 면학감독은 저녁 창(17:50~19:00) 내내.
        if gap.accent == .dinner, let t = data.supervision[today.iso] { s.supervision = "면학감독 \(t)" }
        return s
    }
}

#if canImport(ActivityKit)
/// 실시간 활동(잠금 화면·Dynamic Island) — 안드로이드 Now Bar 와 같은 자리. 지금 블록과 다음 일정.
struct NowActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var status: String
        var title: String
        var room: String?
        var start: Date
        var end: Date
        var accentARGB: UInt32
        var symbol: String
        var next: String?
        /// 이 구간이 끝난 뒤의 구간 — 푸시 없이도 끝나는 시각(staleDate)에 위젯이 이걸로 넘어가 그립니다.
        var then: Segment?
    }

    struct Segment: Codable, Hashable {
        var status: String
        var title: String
        var room: String?
        var start: Date
        var end: Date
        var accentARGB: UInt32
        var symbol: String
        var next: String?
    }
}

extension NowActivityAttributes.ContentState {
    /// 지금 구간이 지났으면(stale) 다음 구간을 지금처럼 — 그 뒤는 앱이나 백그라운드 새로 고침이 다시 채웁니다.
    func shown(stale: Bool) -> Self {
        guard stale, let t = then else { return self }
        return .init(status: t.status, title: t.title, room: t.room, start: t.start, end: t.end, accentARGB: t.accentARGB, symbol: t.symbol, next: t.next, then: nil)
    }
}
#endif
