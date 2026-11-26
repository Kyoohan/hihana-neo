import Foundation

/// 학교 일과 — 안드로이드 shared/Timetable.kt 와 같은 시각·같은 규칙(심야면학·귀가 기간 포함).
enum Schedule {
    // 자정 기준 분.
    static let defaultPeriodTimes: [Int: (start: Int, end: Int)] = [
        1: (8 * 60 + 20, 9 * 60 + 10),
        2: (9 * 60 + 20, 10 * 60 + 10),
        3: (10 * 60 + 20, 11 * 60 + 10),
        4: (11 * 60 + 20, 12 * 60 + 10),
        5: (13 * 60 + 10, 14 * 60),
        6: (14 * 60 + 10, 15 * 60),
        7: (15 * 60 + 10, 16 * 60),
    ]
    static let breakfast = (start: 6 * 60 + 50, end: 8 * 60)
    static let lunch = (start: 12 * 60 + 10, end: 13 * 60 + 10)
    static let dinner = (start: 17 * 60 + 50, end: 19 * 60)
    static let snack = (start: 21 * 60, end: 21 * 60 + 30)
    /// 등교 및 조회 — 아침시간이 끝난 뒤 쉬는 시간 전까지.
    static let morningAssemblyEnd = 8 * 60 + 10
    /// 면학이 끝난 뒤 이 시각 전까지는 아무것도 보이지 않습니다.
    static let wakeTime = 6 * 60 + 50

    enum Sessions {
        static let weekday0 = StudySession(name: "면학 0타임", start: 16 * 60 + 20, end: 17 * 60 + 50)
        static let weekday1 = StudySession(name: "면학 1타임", start: 19 * 60, end: 21 * 60)
        static let weekday2 = StudySession(name: "면학 2타임", start: 21 * 60 + 30, end: 23 * 60 + 10)
        static let weekend1 = StudySession(name: "면학 1타임", start: 13 * 60 + 30, end: 15 * 60 + 30)
        static let weekend2 = StudySession(name: "면학 2타임", start: 16 * 60, end: 17 * 60 + 50)
        static let weekend3 = StudySession(name: "면학 3타임", start: 19 * 60, end: 21 * 60)
        static let weekend4 = StudySession(name: "면학 4타임", start: 21 * 60 + 30, end: 23 * 60 + 10)

        static func session(for slot: PlanSlot) -> StudySession {
            switch slot {
            case .weekday0: weekday0
            case .weekday1: weekday1
            case .weekday2: weekday2
            case .weekend1: weekend1
            case .weekend2: weekend2
            case .weekend3: weekend3
            case .weekend4: weekend4
            }
        }
    }

    /// 심야면학 신청 한 건 — 신청한 날 0시 기준 분(자정을 넘기면 1440 이상, 23:50–01:00 → 1430–1500).
    struct MidnightBooking: Codable, Equatable {
        let session: Int
        let start: Int
        let end: Int
        let seat: String?
    }

    /// 학사일정의 귀가·귀교로 정한 그 날의 상태.
    enum HomeStay: String, Codable {
        /// 귀가하는 날 — 1타임부터 일정이 없습니다.
        case leave
        /// 귀가와 귀교 사이 — 하루 종일 일정이 없습니다.
        case away
        /// 귀교하는 날 — 귀교 시각(밤 9시)부터 일정이 시작합니다.
        case `return`
    }

    /// 일과를 그리는 데 필요한 바깥 정보 — 포털에서 받은 값들.
    struct Context {
        var week: TimetableWeek?
        /// 학사일정의 빨간날(날짜 → 이름).
        var holidays: [LocalDate: String] = [:]
        /// 그 날 그 타임의 면학 위치.
        var placeFor: (LocalDate, PlanSlot) -> StudyPlace? = { _, _ in nil }
        /// 날짜별 심야면학 신청.
        var midnight: [LocalDate: [MidnightBooking]] = [:]
        /// 날짜별 귀가 상태.
        var homeStay: [LocalDate: HomeStay] = [:]
        /// 외출·외박 — 그동안의 일과는 빼고 외출 블록으로.
        var outings: [OutingSpan] = []

        /// 휴일 일과인지 — 토·일요일이거나 학사일정의 빨간날.
        func isRestDay(_ date: LocalDate) -> Bool { date.weekday >= 6 || holidays[date] != nil }

        /// [from] 이후(당일 포함) 가장 가까운 귀교일.
        func nextHomeReturn(from: LocalDate) -> LocalDate? {
            homeStay.filter { $0.value == .return && $0.key >= from }.keys.min()
        }

        /// 귀가일 일정이 끝나는 시각 — 평일은 1인2기(0타임)가 끝나는 17:50, 휴일은 1타임 시작 13:30.
        func homeLeaveAt(_ date: LocalDate) -> Int { isRestDay(date) ? Sessions.weekend1.start : Sessions.weekday0.end }
    }

    /// 귀교 시각 — 밤 9시. 그 전까지가 귀가 기간입니다.
    static let homeReturnAt = 21 * 60

    /// 하루를 빈틈 없는 블록으로 — 심야면학을 이어 붙이고 귀가 기간이면 비웁니다.
    static func blocks(for date: LocalDate, context: Context) -> [Block] {
        applyOutings(date, applyHomeStay(date, blocksWithMidnight(date, context: context), context: context), context.outings)
    }

    /// 외출·외박에 걸치는 블록을 빼고 그 날에 걸친 만큼의 외출 블록을 넣습니다.
    private static func applyOutings(_ date: LocalDate, _ blocks: [Block], _ outings: [OutingSpan]) -> [Block] {
        let dayStart = date.startOfDay, dayEnd = date.adding(days: 1).startOfDay
        let spans = outings.filter { $0.start < dayEnd && $0.end > dayStart }
        guard !spans.isEmpty else { return blocks }
        var out = blocks.filter { b in !spans.contains { $0.start < b.end && b.start < $0.end } }
        out += spans.map { Block(kind: .outing($0), start: max($0.start, dayStart), end: min($0.end, dayEnd)) }
        return out.sorted { $0.start < $1.start }
    }

    private static func blocksWithMidnight(_ date: LocalDate, context: Context) -> [Block] {
        let base = baseBlocks(for: date, context: context)
        let today = (context.midnight[date] ?? []).sorted { $0.start < $1.start }
        let carry = (context.midnight[date.adding(days: -1)] ?? []).filter { $0.end > 24 * 60 }
        if today.isEmpty && carry.isEmpty { return base }
        var result = base
        func at(_ m: Int) -> Date { date.at(minutes: m) }
        func session(_ b: MidnightBooking, _ offset: Int) -> StudySession { StudySession(name: "심야 \(b.session)타임", start: b.start - offset, end: b.end - offset) }
        func block(_ b: MidnightBooking, _ offset: Int) -> Block {
            Block(kind: .study(session(b, offset), .midnight(seat: b.seat)), start: at(b.start - offset), end: at(b.end - offset))
        }
        if !today.isEmpty {
            // 끝의 빈 블록(마지막 면학 ~ 자정)을 걷어 내고 그 자리에 휴식 → 심야 타임을 잇습니다.
            let tail = result.last?.isBlank == true ? result.removeLast() : nil
            var cursor = tail?.start ?? result.last?.end ?? at(24 * 60)
            for b in today {
                let start = at(b.start)
                if start < cursor { continue }
                if start > cursor {
                    let next = NextUp.study(session(b, 0), .midnight(seat: b.seat))
                    result.append(Block(kind: .gap(Gap(label: "휴식", fallbackTitle: "휴식", caption: "심야 \(b.session)타임까지", next: next, accent: .idle)), start: cursor, end: start))
                }
                result.append(block(b, 0))
                cursor = at(b.end)
            }
            if cursor < at(24 * 60) { result.append(Block(kind: .blank, start: cursor, end: at(24 * 60))) }
        }
        if !carry.isEmpty {
            // 전날 심야가 자정을 넘겨 이어지는 부분 — 블록은 전날 시작 시각 그대로 두어 진행률이 맞게 합니다.
            let carryEnd = at(carry.map(\.end).max()! - 24 * 60)
            if let head = result.first, head.isBlank {
                result.removeFirst()
                if carryEnd < head.end { result.insert(Block(kind: .blank, start: carryEnd, end: head.end), at: 0) }
            }
            result.insert(contentsOf: carry.map { block($0, 24 * 60) }, at: 0)
        }
        return result
    }

    /// 귀가 기간에는 일정을 비웁니다 — 귀가일은 1타임부터, 그 사이 날은 하루 종일, 귀교일은 밤 9시 전까지.
    private static func applyHomeStay(_ date: LocalDate, _ blocks: [Block], context: Context) -> [Block] {
        guard let stay = context.homeStay[date] else { return blocks }
        let startOfDay = date.at(minutes: 0)
        let endOfDay = date.at(minutes: 24 * 60)
        switch stay {
        case .away:
            return [Block(kind: .blank, start: startOfDay, end: endOfDay)]
        case .return:
            let cutoff = date.at(minutes: homeReturnAt)
            let kept = blocks.filter { $0.end > cutoff }.map { b -> Block in var b = b; if b.start < cutoff { b.start = cutoff }; return b }
            return [Block(kind: .blank, start: startOfDay, end: cutoff)] + kept
        case .leave:
            let cutoff = date.at(minutes: context.homeLeaveAt(date))
            let kept = blocks.filter { $0.start < cutoff }.map { block -> Block in
                var b = block
                if b.end > cutoff { b.end = cutoff }
                // 1타임을 기다리던 구간(저녁시간 등)은 이제 귀가를 기다립니다.
                if case .gap(var g) = b.kind, !(b.end < cutoff) {
                    g.fallbackTitle = "귀가"; g.caption = "귀가까지"; g.next = nil
                    b.kind = .gap(g)
                }
                return b
            }
            return kept + [Block(kind: .blank, start: kept.last?.end ?? startOfDay, end: endOfDay)]
        }
    }

    private static func baseBlocks(for date: LocalDate, context: Context) -> [Block] {
        func at(_ minutes: Int) -> Date { date.at(minutes: minutes) }
        func studyBlock(_ slot: PlanSlot) -> Block {
            let s = Sessions.session(for: slot)
            return Block(kind: .study(s, context.placeFor(date, slot)), start: at(s.start), end: at(s.end))
        }
        func nextStudy(_ slot: PlanSlot) -> NextUp { .study(Sessions.session(for: slot), context.placeFor(date, slot)) }
        func gap(_ g: Gap, _ from: Int, _ to: Int) -> Block { Block(kind: .gap(g), start: at(from), end: at(to)) }

        var result: [Block] = []
        let week = context.week
        let times: (Int) -> (start: Int, end: Int)? = { p in
            if let t = week?.periodTimes?[p], t.count == 2 { return (t[0], t[1]) }
            return defaultPeriodTimes[p]
        }

        if context.isRestDay(date) {
            // ===== 주말·휴일 =====
            let wake = min(wakeTime, Sessions.weekend1.start)
            if wake > 0 { result.append(Block(kind: .blank, start: at(0), end: at(wake))) }
            let restLabel = context.holidays[date].map { "\($0) · 수업 없음" } ?? "주말 · 수업 없음"
            result.append(gap(Gap(label: restLabel, fallbackTitle: "수업 없음", caption: "1타임까지", next: nextStudy(.weekend1), accent: .idle), wake, Sessions.weekend1.start))
            result.append(studyBlock(.weekend1))
            result.append(gap(Gap(label: "쉬는 시간", fallbackTitle: "쉬는 시간", caption: "2타임까지", next: nextStudy(.weekend2), accent: .breakTime), Sessions.weekend1.end, Sessions.weekend2.start))
            result.append(studyBlock(.weekend2))
            result.append(gap(Gap(label: "저녁시간", fallbackTitle: "저녁시간", caption: "3타임까지", next: nextStudy(.weekend3), accent: .dinner), dinner.start, dinner.end))
            result.append(studyBlock(.weekend3))
            result.append(gap(Gap(label: "간식시간", fallbackTitle: "간식시간", caption: "4타임까지", next: nextStudy(.weekend4), accent: .snack), snack.start, snack.end))
            result.append(studyBlock(.weekend4))
            result.append(Block(kind: .blank, start: at(Sessions.weekend4.end), end: at(24 * 60)))
        } else {
            // ===== 평일 ===== 포털에서 받은 그 요일 과목표만 씁니다 — 없으면 시간 기반 블록만.
            let today = week?.lessons[date.weekday] ?? [:]
            var cursor = 0

            for p in today.keys.sorted() {
                guard let t = times(p), let lesson = today[p] else { continue }
                if cursor < t.start {
                    let next = NextUp.lesson(period: p, lesson)
                    if cursor == 0 {
                        // 자정~기상은 빈 화면, 그 뒤 아침시간 → 등교 및 조회 → 쉬는 시간.
                        let wakeStart = min(wakeTime, t.start)
                        if wakeStart > 0 { result.append(Block(kind: .blank, start: at(0), end: at(wakeStart))) }
                        let breakfastEnd = min(breakfast.end, t.start)
                        if breakfastEnd > wakeStart {
                            result.append(gap(Gap(label: "아침시간", fallbackTitle: "아침시간", caption: "\(p)교시까지", next: next, accent: .breakfast), wakeStart, breakfastEnd))
                        }
                        let assemblyEnd = min(morningAssemblyEnd, t.start)
                        if assemblyEnd > breakfastEnd {
                            result.append(gap(Gap(label: "등교 및 조회", fallbackTitle: "등교 및 조회", caption: "\(p)교시까지", next: next, accent: .lesson), breakfastEnd, assemblyEnd))
                        }
                        if t.start > assemblyEnd {
                            result.append(gap(Gap(label: "쉬는 시간", fallbackTitle: "쉬는 시간", caption: "\(p)교시까지", next: next, accent: .breakTime), assemblyEnd, t.start))
                        }
                    } else if cursor == lunch.start && t.start == lunch.end {
                        result.append(gap(Gap(label: "점심시간", fallbackTitle: "점심시간", caption: "\(p)교시까지", next: next, accent: .lunch), cursor, t.start))
                    } else {
                        result.append(gap(Gap(label: "쉬는 시간", fallbackTitle: "쉬는 시간", caption: "\(p)교시까지", next: next, accent: .breakTime), cursor, t.start))
                    }
                }
                result.append(Block(kind: .lesson(period: p, lesson), start: at(t.start), end: at(t.end)))
                cursor = t.end
            }

            if today.isEmpty {
                let s0 = Sessions.weekday0.start
                let blankEnd = min(wakeTime, s0)
                if blankEnd > 0 { result.append(Block(kind: .blank, start: at(0), end: at(blankEnd))) }
                if blankEnd < s0 {
                    result.append(gap(Gap(label: "수업 없음", fallbackTitle: "오늘 수업 없음", caption: "0타임까지", next: nextStudy(.weekday0), accent: .idle), blankEnd, s0))
                }
            } else if cursor < Sessions.weekday0.start {
                result.append(gap(Gap(label: "일과 종료", fallbackTitle: "오늘 수업 끝", caption: "0타임까지", next: nextStudy(.weekday0), accent: .idle), cursor, Sessions.weekday0.start))
            }

            result.append(studyBlock(.weekday0))
            result.append(gap(Gap(label: "저녁시간", fallbackTitle: "저녁시간", caption: "1타임까지", next: nextStudy(.weekday1), accent: .dinner), dinner.start, dinner.end))
            result.append(studyBlock(.weekday1))
            result.append(gap(Gap(label: "간식시간", fallbackTitle: "간식시간", caption: "2타임까지", next: nextStudy(.weekday2), accent: .snack), snack.start, snack.end))
            result.append(studyBlock(.weekday2))
            result.append(Block(kind: .blank, start: at(Sessions.weekday2.end), end: at(24 * 60)))
        }
        return result
    }

    /// 지금 시각의 블록 — 없으면 그 날 마지막 블록.
    static func block(at time: Date, in blocks: [Block]) -> Block? {
        blocks.first { $0.start <= time && time < $0.end } ?? blocks.last
    }

    /// '다음 일정' — 쉬는 시간·식사 같은 대기 구간은 일정으로 치지 않고 다음 수업·면학만 셉니다. 지금이 대기 구간이면 그
    /// 구간이 이미 바로 다음 것을 보여 주므로 그 다음(둘째)을. 같은 과목·같은 교실이 쉬는 시간만 두고 이어지면 한 수업으로 봅니다.
    static func nextEvent(blocks: [Block], current: Block?, time: Date) -> Block? {
        let upcoming = blocks.filter { $0.start > time && !$0.isBlank && !$0.isGap }
        let skipFirst = current?.isGap == true && upcoming.first?.start == current?.end
        var index = skipFirst ? 1 : 0
        var reference: Block? = skipFirst ? upcoming.first : current
        while true {
            guard index < upcoming.count else { return nil }
            let candidate = upcoming[index]
            if !sameLesson(reference, candidate) { return candidate }
            reference = candidate
            index += 1
        }
    }

    static func sameLesson(_ a: Block?, _ b: Block) -> Bool {
        guard case .lesson(_, let x)? = a?.kind, case .lesson(_, let y) = b.kind else { return false }
        return !x.isFree && x.subject == y.subject && x.room == y.room
    }
}
