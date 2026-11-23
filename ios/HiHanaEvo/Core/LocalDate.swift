import Foundation

/// 서울 기준 날짜 — 안드로이드의 LocalDate 와 같은 쓰임. 시각은 [Date] 로 다루되 하루의 경계는 늘 서울 달력입니다.
enum Seoul {
    static let zone = TimeZone(identifier: "Asia/Seoul")!
    static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = zone
        c.locale = Locale(identifier: "ko_KR")
        return c
    }()
}

struct LocalDate: Hashable, Comparable, Codable {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year; self.month = month; self.day = day
    }

    init(_ date: Date) {
        let c = Seoul.calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: c.year!, month: c.month!, day: c.day!)
    }

    /// "2026-10-24" 형태. 틀린 값은 nil.
    init?(iso: String) {
        let p = iso.prefix(10).split(separator: "-").compactMap { Int($0) }
        guard p.count == 3, (1...12).contains(p[1]), (1...31).contains(p[2]) else { return nil }
        self.init(year: p[0], month: p[1], day: p[2])
    }

    /// 서울 달력의 오늘 — 안드로이드 PlanStore.today() 와 같이 자정에 바뀝니다.
    static func today(now: Date = Date()) -> LocalDate { LocalDate(now) }

    /// 서울 자정.
    var startOfDay: Date {
        Seoul.calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }

    func at(minutes: Int) -> Date { startOfDay.addingTimeInterval(TimeInterval(minutes * 60)) }

    /// 월=1 … 일=7 (안드로이드의 DayOfWeek.value).
    var weekday: Int {
        let w = Seoul.calendar.component(.weekday, from: startOfDay) // 일=1 … 토=7
        return w == 1 ? 7 : w - 1
    }

    func adding(days: Int) -> LocalDate { LocalDate(startOfDay.addingTimeInterval(TimeInterval(days * 86_400 + 12 * 3600))) }

    var iso: String { String(format: "%04d-%02d-%02d", year, month, day) }

    /// 두 날짜 사이 날 수(b - a).
    static func days(from a: LocalDate, to b: LocalDate) -> Int {
        Seoul.calendar.dateComponents([.day], from: a.startOfDay.addingTimeInterval(12 * 3600), to: b.startOfDay.addingTimeInterval(12 * 3600)).day ?? 0
    }

    /// 그 주 월요일.
    var monday: LocalDate { adding(days: -(weekday - 1)) }

    /// "월" … "일".
    var weekdayShort: String { ["월", "화", "수", "목", "금", "토", "일"][weekday - 1] }

    /// "9/17 (목)" — 안드로이드 shortDate.
    var shortText: String { "\(month)/\(day) (\(weekdayShort))" }

    /// "9월 17일 (목)" — 날짜 묶음 머리.
    var headerText: String { "\(month)월 \(day)일 (\(weekdayShort))" }

    /// "10월 24일 토요일" — 홈 머리의 날짜.
    var longText: String { "\(month)월 \(day)일 \(weekdayShort)요일" }

    static func < (a: LocalDate, b: LocalDate) -> Bool { (a.year, a.month, a.day) < (b.year, b.month, b.day) }
}

extension Date {
    /// 서울 시각의 시(0…23).
    var hour: Int { Seoul.calendar.component(.hour, from: self) }

    /// 이 시각이 든 서울 날짜.
    var localDate: LocalDate { LocalDate(self) }

    /// 자정 기준 분(0…1439).
    var minutesOfDay: Int {
        let c = Seoul.calendar.dateComponents([.hour, .minute], from: self)
        return c.hour! * 60 + c.minute!
    }

    var hm: String {
        let c = Seoul.calendar.dateComponents([.hour, .minute], from: self)
        return String(format: "%02d:%02d", c.hour!, c.minute!)
    }
}

/// Date 의 <= · >= — Xcode 27.2 SDK 는 이 둘을 'iOS 27.2 부터 있는' 새 함수로 연결하는데, iOS 27.2 베타(24B5089g)에는 그 함수가
/// 아직 없어 앱이 켜지자마자 꺼졌습니다(빈 주소로 점프). 예전부터 있던 < 로 같은 뜻을 만들어 이 모듈 안에서는 이쪽을 씁니다.
extension Date {
    @inline(__always) static func <= (l: Date, r: Date) -> Bool { !(r < l) }
    @inline(__always) static func >= (l: Date, r: Date) -> Bool { !(l < r) }
}
