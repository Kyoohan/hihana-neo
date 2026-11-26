import XCTest
@testable import HiHanaEvo

final class ScheduleTests: XCTestCase {
    // 2026-10-23(금), 2026-10-24(토)
    let friday = LocalDate(year: 2026, month: 10, day: 23)
    let saturday = LocalDate(year: 2026, month: 10, day: 24)

    func week(_ lessons: [Int: Lesson]) -> TimetableWeek {
        TimetableWeek(lessons: Dictionary(uniqueKeysWithValues: (1...5).map { ($0, lessons) }))
    }

    func testWeekdays() {
        XCTAssertEqual(friday.weekday, 5)
        XCTAssertEqual(saturday.weekday, 6)
        XCTAssertEqual(LocalDate(year: 2026, month: 10, day: 25).weekday, 7)
        XCTAssertEqual(LocalDate(year: 2026, month: 10, day: 26).weekday, 1)
    }

    func testAddingDaysAcrossMonth() {
        XCTAssertEqual(LocalDate(year: 2026, month: 10, day: 31).adding(days: 1), LocalDate(year: 2026, month: 11, day: 1))
        XCTAssertEqual(LocalDate(year: 2026, month: 3, day: 1).adding(days: -1), LocalDate(year: 2026, month: 2, day: 28))
    }

    /// 하루는 서울 자정에 바뀝니다(안드로이드 PlanStore.today() 와 같게).
    func testDayStartsAtMidnight() {
        let before = friday.at(minutes: 24 * 60 - 1) // 금 23:59
        XCTAssertEqual(LocalDate.today(now: before), friday)
        let after = friday.at(minutes: 24 * 60 + 1) // 토 00:01
        XCTAssertEqual(LocalDate.today(now: after), saturday)
    }

    func testWeekdayBlocksAreContiguousAndCoverTheDay() {
        let ctx = Schedule.Context(week: week([1: Lesson(subject: "미적분Ⅱ", room: "B301"), 2: .free, 5: Lesson(subject: "문학", room: "A201")]))
        let blocks = Schedule.blocks(for: friday, context: ctx)
        XCTAssertEqual(blocks.first?.start, friday.at(minutes: 0))
        XCTAssertEqual(blocks.last?.end, friday.at(minutes: 24 * 60))
        for (a, b) in zip(blocks, blocks.dropFirst()) { XCTAssertEqual(a.end, b.start, "\(a.statusLabel) → \(b.statusLabel)") }
    }

    func testLunchGapBetween4And5() {
        let ctx = Schedule.Context(week: week([4: Lesson(subject: "영어", room: "A202"), 5: Lesson(subject: "문학", room: "A201")]))
        let blocks = Schedule.blocks(for: friday, context: ctx)
        let lunch = blocks.first { $0.statusLabel == "점심시간" }
        XCTAssertNotNil(lunch)
        XCTAssertEqual(lunch?.title, "문학")
        XCTAssertEqual(lunch?.room, "A201")
    }

    func testNowAtLunchShowsGapAndNextEventIsSecondLesson() {
        let ctx = Schedule.Context(week: week([4: Lesson(subject: "영어", room: "A202"), 5: Lesson(subject: "문학", room: "A201"), 6: Lesson(subject: "물리", room: "A205")]))
        let blocks = Schedule.blocks(for: friday, context: ctx)
        let now = friday.at(minutes: 12 * 60 + 30)
        let current = Schedule.block(at: now, in: blocks)
        XCTAssertEqual(current?.statusLabel, "점심시간")
        // 지금이 대기 구간이면 그 구간이 5교시를 이미 보여 주므로, 다음 일정은 6교시.
        XCTAssertEqual(Schedule.nextEvent(blocks: blocks, current: current, time: now)?.title, "물리")
    }

    func testConsecutiveSameLessonCountsAsOne() {
        let l = Lesson(subject: "스포츠 생활1(남)(5반) 김응주 /", room: "체육관")
        let ctx = Schedule.Context(week: week([6: l, 7: l]))
        let blocks = Schedule.blocks(for: friday, context: ctx)
        let now = friday.at(minutes: 14 * 60 + 20)
        let current = Schedule.block(at: now, in: blocks)
        // 6교시와 7교시가 같은 수업이라 다음 일정은 면학 0타임.
        XCTAssertEqual(Schedule.nextEvent(blocks: blocks, current: current, time: now)?.statusLabel, "면학 0타임")
    }

    func testWeekendHasFourSessionsAndHolidayLabel() {
        let ctx = Schedule.Context(week: nil, holidays: [saturday: "개천절"])
        let blocks = Schedule.blocks(for: saturday, context: ctx)
        XCTAssertEqual(blocks.filter { if case .study = $0.kind { true } else { false } }.count, 4)
        XCTAssertTrue(blocks.contains { $0.statusLabel == "개천절 · 수업 없음" })
    }

    func testHolidayOnWeekdayUsesWeekendSchedule() {
        let ctx = Schedule.Context(week: week([1: Lesson(subject: "미적분Ⅱ", room: "B301")]), holidays: [friday: "대체공휴일"])
        let blocks = Schedule.blocks(for: friday, context: ctx)
        XCTAssertFalse(blocks.contains { if case .lesson = $0.kind { true } else { false } })
        XCTAssertEqual(blocks.filter { if case .study = $0.kind { true } else { false } }.count, 4)
    }

    func testStudyPlaceShowsInBlock() {
        let ctx = Schedule.Context(week: nil, placeFor: { _, slot in slot == .weekday1 ? .library(seat: "2-46") : nil })
        let blocks = Schedule.blocks(for: friday, context: ctx)
        let s1 = blocks.first { $0.statusLabel == "면학 1타임" }
        XCTAssertEqual(s1?.title, "도서관")
        XCTAssertEqual(s1?.room, "2-46")
        XCTAssertEqual(blocks.first { $0.statusLabel == "면학 0타임" }?.title, "위치 미설정")
    }

    func testNoLessonsDayStartsWithQuietBlocks() {
        let blocks = Schedule.blocks(for: friday, context: Schedule.Context(week: nil))
        XCTAssertTrue(blocks.contains { $0.statusLabel == "수업 없음" })
    }

    func testLessonTitleAndTeacherSplit() {
        let l = Lesson(subject: "스포츠 생활1(남)(5반) 김응주 /", room: "체육관")
        XCTAssertEqual(l.title, "스포츠 생활1(남)(5반)")
        XCTAssertEqual(l.teacher, "김응주")
        XCTAssertEqual(l.place, "체육관 · 김응주")
        XCTAssertEqual(Lesson(subject: "미적분Ⅱ", room: "B301").title, "미적분Ⅱ")
    }

    func testHeadlineDoesNotRepeatFreePeriod() {
        let b = Block(kind: .lesson(period: 7, .free), start: friday.at(minutes: 910), end: friday.at(minutes: 960))
        XCTAssertEqual(b.headline, "7교시 · 공강")
    }

    func testSlotFromPortalName() {
        XCTAssertEqual(PlanSlot.from(portalName: "평일 1타임", date: friday, isRestDay: false), .weekday1)
        XCTAssertEqual(PlanSlot.from(portalName: "토요일2타임", date: saturday, isRestDay: true), .weekend2)
        XCTAssertNil(PlanSlot.from(portalName: "기타", date: friday, isRestDay: false))
    }
}

final class MealTests: XCTestCase {
    func meals(_ keys: [Meal]) -> DayMeals {
        DayMeals(items: Dictionary(uniqueKeysWithValues: keys.map { ($0, [MealItem(name: "\($0.label)밥", codes: [])]) }), photos: [:])
    }
    let day = LocalDate(year: 2026, month: 10, day: 23)

    func testParserStripsAllergyCodes() {
        let json = #"{"result":{"hanaMeal":{"contents2":"치킨마요밥(1.5.6.15)/우동국(1.2.5.6.7.9)<br>포기김치/사과","contents3":"찰옥수수밥/배추겉절이(9)","bbs_file3":"a.jpg","contents1":null,"contents4":""}}}"#
        let parsed = MealParser.parse(Data(json.utf8))
        XCTAssertEqual(parsed?.items[.lunch]?.map(\.name), ["치킨마요밥", "우동국", "포기김치", "사과"])
        XCTAssertEqual(parsed?.items[.lunch]?.first?.codes, [1, 5, 6, 15])
        XCTAssertEqual(parsed?.items[.dinner]?.last?.codes, [9])
        XCTAssertEqual(parsed?.photos[.dinner], "a.jpg")
        XCTAssertNil(parsed?.items[.breakfast])
        XCTAssertNil(parsed?.items[.snack])
    }

    func testParserRejectsGarbage() {
        XCTAssertNil(MealParser.parse(Data("<html>301</html>".utf8)))
        XCTAssertNil(MealParser.parse(Data(#"{"result":{"hanaMeal":{}}}"#.utf8)))
    }

    func testNextMealWalksThroughTheDay() {
        let all = meals([.breakfast, .lunch, .dinner, .snack])
        let tomorrow = meals([.breakfast, .lunch])
        func pick(_ h: Int, _ m: Int) -> MealPick? { MealRules.current(today: day, todayMeals: all, tomorrowMeals: tomorrow, now: day.at(minutes: h * 60 + m)) }
        XCTAssertEqual(pick(7, 0)?.meal, .breakfast)
        XCTAssertEqual(pick(8, 30)?.meal, .lunch)
        XCTAssertEqual(pick(17, 0)?.meal, .dinner)
        XCTAssertEqual(pick(19, 30)?.meal, .snack)
        // 간식(21:00~21:30)이 끝나면 내일 아침.
        let after = pick(21, 40)
        XCTAssertEqual(after?.meal, .breakfast)
        XCTAssertEqual(after?.tomorrow, true)
        XCTAssertEqual(after?.date, day.adding(days: 1))
    }

    func testNoMealsAnywhere() {
        XCTAssertNil(MealRules.current(today: day, todayMeals: nil, tomorrowMeals: nil, now: day.at(minutes: 600)))
    }

    /// 받아 둔 4주치에 시험이 빠져 있어도(지난주 캐시·하루 조회 실패) 따로 받아 둔 정기고사가 붙어 D-day 가 뜹니다.
    func testExamsMergedIntoStaleSchedule() {
        func e(_ name: String, _ m: Int, _ day: Int) -> HanaScheduleEntry {
            HanaScheduleEntry(code: "", name: name, place: nil, startTime: nil, endTime: nil, allDay: true, date: LocalDate(year: 2026, month: m, day: day))
        }
        let today = LocalDate(year: 2026, month: 11, day: 24)
        let stale = [e("교내 체육대회", 11, 27), e("학부모 상담", 12, 4)]
        XCTAssertNil(examDday(stale, today: today))
        let exams = [e("기말고사(1,2)", 12, 14), e("기말고사(1,2)", 12, 15)]
        let merged = AcademicRepository.withExams(stale + [exams[0]], exams)
        XCTAssertEqual(merged.filter { $0.name.hasPrefix("기말") }.count, 2)  // 같은 날·같은 이름은 하나
        let dday = examDday(merged, today: today)
        XCTAssertEqual(dday?.date, LocalDate(year: 2026, month: 12, day: 14))
        XCTAssertEqual(dday?.days, 20)
    }

    /// 하루씩 받다 실패한 날은 받아 둔 그날 일정으로 — 실패한 날을 '일정 없음'으로 저장하면 시험 첫날이 사라졌습니다.
    func testFailedDaysKeepStoredEntries() {
        func e(_ name: String, _ day: Int) -> HanaScheduleEntry {
            HanaScheduleEntry(code: "", name: name, place: nil, startTime: nil, endTime: nil, allDay: true, date: LocalDate(year: 2026, month: 12, day: day))
        }
        let stored = (14...18).map { e("기말고사(1,2)", $0) } + [e("교직원 연수", 14)]
        let failed = Set((14...17).map { LocalDate(year: 2026, month: 12, day: $0) })
        let fetched = AcademicAPI.ScheduleFetch(entries: [e("귀가(1,2)", 18), e("기말고사(1,2)", 18)], failedDays: failed)
        let merged = AcademicRepository.fillFailedDays(fetched, stored: stored)
        XCTAssertEqual(merged.count, 7)  // 18일은 새로 받은 것만(받아 둔 18일 것과 겹치지 않게)
        XCTAssertEqual(examDday(merged, today: LocalDate(year: 2026, month: 11, day: 24))?.date, LocalDate(year: 2026, month: 12, day: 14))
        // 실패한 날이 없으면 받은 그대로.
        XCTAssertEqual(AcademicRepository.fillFailedDays(AcademicAPI.ScheduleFetch(entries: [e("귀가(1,2)", 18)], failedDays: []), stored: stored).count, 1)
    }

    /// 게시글에서 뽑은 일정은 오늘부터 90일 안의 것만 — 해외 대학 '학기 개강'(2027-09-01) 같은 먼 날짜가 목록 끝에 남던 것.
    func testBoardAgendaHorizon() {
        let store = BoardAgendaStore.shared
        let key = "test_horizon"
        let today = LocalDate(year: 2026, month: 11, day: 24)
        store.record(key: key, title: "[해외 진학] 2027학년도 홍콩중문대학교", url: nil, events: [
            PostEvent(title: "홍콩중문대 정규 전형 마감", date: LocalDate(year: 2027, month: 1, day: 7), deadline: true),
            PostEvent(title: "학기 개강", date: LocalDate(year: 2027, month: 9, day: 1)),
        ])
        defer { store.record(key: key, title: "", url: nil, events: []) }
        let titles = store.items(today: today).filter { $0.id.hasPrefix("b:\(key):") }.map(\.title)
        XCTAssertEqual(titles, ["홍콩중문대 정규 전형 마감"])
        XCTAssertEqual(today.adding(days: BoardAgendaStore.horizonDays), LocalDate(year: 2027, month: 2, day: 22))
    }

}

/// 실시간 현황용 구간 — 블록타임 묶기와 깨우기 시각.
final class LiveSegmentsTests: XCTestCase {
    let friday = LocalDate(year: 2026, month: 10, day: 23)

    func week(_ lessons: [Int: Lesson]) -> TimetableWeek {
        TimetableWeek(lessons: Dictionary(uniqueKeysWithValues: (1...5).map { ($0, lessons) }))
    }

    /// 블록타임: 같은 과목·같은 교실이 쉬는 시간만 두고 두 교시 이어지면 실시간 현황에서는 한 구간(쉬는 시간 포함).
    /// 3학점 과목(한 교시 수업 + 한 교시 공강)과 점심을 사이에 둔 4·5교시는 묶지 않습니다.
    func testLiveSegmentsMergeBlockTime() {
        let math = Lesson(subject: "미적분Ⅱ", room: "B301"), eng = Lesson(subject: "영어", room: "A202")
        let ctx = Schedule.Context(week: week([1: math, 2: math, 3: eng, 4: .free, 5: eng, 6: eng, 7: eng]))
        let segs = LiveSegments.build(Schedule.blocks(for: friday, context: ctx)).filter { !$0.isBlank }
        let merged = segs.first { $0.status == "1–2교시 수업 중" }
        XCTAssertEqual(merged?.start, friday.at(minutes: 8 * 60 + 20))
        XCTAssertEqual(merged?.end, friday.at(minutes: 10 * 60 + 10))
        XCTAssertEqual(merged?.title, "1–2교시 수업 중 미적분Ⅱ")
        XCTAssertEqual(merged?.room, "B301")
        // 3교시 영어 + 4교시 공강은 따로.
        XCTAssertNotNil(segs.first { $0.status == "3교시 수업 중" })
        XCTAssertNotNil(segs.first { $0.status == "4교시 · 공강" })
        // 5·6·7교시가 같은 수업이면 한 구간으로 이어 묶습니다.
        let long = segs.first { $0.status == "5–7교시 수업 중" }
        XCTAssertEqual(long?.end, friday.at(minutes: 16 * 60))
        // 구간끼리 빈틈·겹침 없음.
        for (a, b) in zip(segs, segs.dropFirst()) { XCTAssertEqual(a.end, b.start, "\(a.status) → \(b.status)") }
        // 지금(9:15, 원래는 쉬는 시간)도 묶인 구간.
        XCTAssertEqual(LiveSegments.segment(at: friday.at(minutes: 9 * 60 + 15), in: segs)?.status, "1–2교시 수업 중")
    }

    /// 하루를 한 번에 올릴 때 — 쉬는 시간은 빠지고, 블록타임은 한 구간, 식사는 남습니다.
    func testLiveDayParts() {
        let math = Lesson(subject: "미적분Ⅱ", room: "B301"), eng = Lesson(subject: "영어Ⅱ", room: "A201"), phys = Lesson(subject: "물리학Ⅱ", room: "과학동 2")
        let ctx = Schedule.Context(week: week([1: math, 2: math, 3: eng, 4: eng, 5: phys]))
        let segs = LiveSegments.build(Schedule.blocks(for: friday, context: ctx))
        let parts = LiveSegments.dayParts(segs)
        XCTAssertFalse(parts.contains { $0.status == "쉬는 시간" })
        XCTAssertFalse(parts.contains { $0.isBlank })
        XCTAssertNotNil(parts.first { $0.status == "1–2교시 수업 중" })
        XCTAssertNotNil(parts.first { $0.status == "3–4교시 수업 중" })
        XCTAssertNotNil(parts.first { $0.status == "점심시간" })
        // 시간순이고 겹치지 않습니다(빈틈은 있어도 됨).
        for (a, b) in zip(parts, parts.dropFirst()) { XCTAssertLessThanOrEqual(a.end, b.start, "\(a.status) → \(b.status)") }
        // 8시에 열면 8시간 안에 걸치는 구간만, 이미 끝난 구간은 빠집니다.
        let from = friday.at(minutes: 10 * 60 + 30)
        let window = LiveSegments.dayParts(segs, from: from)
        XCTAssertEqual(window.first?.status, "3–4교시 수업 중")
        XCTAssertTrue(window.allSatisfy { $0.end > from && $0.start < from.addingTimeInterval(8 * 3600) })
        XCTAssertLessThanOrEqual(window.count, 8)
    }

    /// 현황에 한 번에 올리는 구간 — 지금 구간부터 빈틈 없이 이어지고(쉬는 시간 포함), 개수·시간 한도에서 끊깁니다.
    func testLiveFrames() {
        let math = Lesson(subject: "미적분Ⅱ", room: "B301"), eng = Lesson(subject: "영어Ⅱ", room: "A201"), phys = Lesson(subject: "물리학Ⅱ", room: "과학동 2")
        let ctx = Schedule.Context(week: week([1: math, 2: math, 3: eng, 4: eng, 5: phys]))
        let segs = LiveSegments.build(Schedule.blocks(for: friday, context: ctx))
        let from = friday.at(minutes: 8 * 60 + 30)          // 1–2교시 중
        let all = LiveSegments.frames(segs, from: from, until: from.addingTimeInterval(8 * 3600), limit: 12)
        XCTAssertEqual(all.parts.first?.status, "1–2교시 수업 중")
        XCTAssertEqual(all.parts.first?.head, "미적분Ⅱ")
        XCTAssertEqual(all.parts.first?.label, "1–2교시 미적분Ⅱ")
        // 쉬는 시간도 구간으로 들어 있지만(화면이 '다음 · …'을 보여 주려면 필요), 하루 목록에는 오르지 않습니다.
        let rest = all.parts.first { $0.status == "쉬는 시간" }
        XCTAssertNotNil(rest)
        XCTAssertEqual(rest?.head, "")
        XCTAssertFalse(rest.map(LiveSegments.isListed) ?? true)
        XCTAssertTrue(all.parts.first { $0.status == "점심시간" }.map(LiveSegments.isListed) ?? false)
        // 빈틈 없이 이어집니다.
        for (a, b) in zip(all.parts, all.parts.dropFirst()) { XCTAssertEqual(a.end, b.start, "\(a.status) → \(b.status)") }
        XCTAssertFalse(all.parts.contains { $0.isBlank })
        XCTAssertLessThanOrEqual(all.parts.count, 12)
        // 개수 한도에서 끊기면 more.
        let few = LiveSegments.frames(segs, from: from, until: from.addingTimeInterval(8 * 3600), limit: 3)
        XCTAssertEqual(few.parts.count, 3)
        XCTAssertTrue(few.more)
        // 시간 한도 — 그 전에 시작하는 구간까지만.
        let short = LiveSegments.frames(segs, from: from, until: friday.at(minutes: 10 * 60 + 15), limit: 12)
        XCTAssertEqual(short.parts.last?.status, "쉬는 시간")
        // 일과 밖(빈 시간)에서는 올릴 것이 없습니다.
        XCTAssertTrue(LiveSegments.frames(segs, from: friday.at(minutes: 3 * 60), until: friday.at(minutes: 11 * 60), limit: 12).parts.isEmpty)
    }

    /// 아침에 연 현황 — 글자 구간은 12시간·16개 안, 막대는 2타임 끝(23:10)까지, 현황 데이터는 4KB 안.
    func testLiveDayStateReachesNightStudy() {
        // 블록타임 없는 7교시 날(구간이 가장 많은 경우).
        let subjects = ["국어", "수학Ⅰ", "영어Ⅰ", "물리학Ⅰ", "화학Ⅰ", "한국사", "체육"].enumerated().map { Lesson(subject: $1, room: "B30\($0)") }
        let ctx = Schedule.Context(week: week(Dictionary(uniqueKeysWithValues: subjects.enumerated().map { ($0 + 1, $1) })))
        let segs = LiveSegments.build(Schedule.blocks(for: friday, context: ctx))
        let now = friday.at(minutes: 8 * 60 + 30)
        guard let d = LiveActivityController.dayState(segs, now: now, startedAt: now, argb: { _ in 0xFF5B9BFF }) else { return XCTFail() }
        XCTAssertLessThanOrEqual(d.framed, LiveActivityController.frameLimit)
        let ahead = d.day.dropFirst(d.lead)
        XCTAssertTrue(ahead.prefix(d.framed).allSatisfy { $0.start < now.addingTimeInterval(12 * 3600) })
        XCTAssertEqual(d.day.last?.end, friday.at(minutes: 23 * 60 + 10), "막대는 2타임 끝까지")
        XCTAssertTrue(d.more)
        // 막대 전용 구간은 이름이 비어 있고(첫 일정 하나만 예외), 현황 데이터가 4KB 안.
        XCTAssertLessThanOrEqual(ahead.dropFirst(d.framed).filter { !$0.label.isEmpty }.count, 1)
        var state = NowActivityAttributes.ContentState(status: "1교시 수업 중", title: "1교시 수업 중 국어", room: "B300", start: now, end: now,
                                                       accentARGB: 0, symbol: "book", next: "09:20 · 수학Ⅰ · B301", then: nil,
                                                       day: d.day, more: d.more, framed: d.framed)
        state.lead = d.lead
        let bytes = (try? JSONEncoder().encode(state))?.count ?? .max
        XCTAssertLessThanOrEqual(bytes, 3600, "현황 데이터 \(bytes)바이트")
    }

    /// 낮에 연 현황 — 막대는 그날 시작(8:00)부터, 지난 구간은 이름 없이 앞에(lead), 글자 구간은 지금 구간부터. 구간이 가장 많은 날에도 4KB 안.
    func testLiveDayBarStartsAtDayStartWhenOpenedLater() {
        let subjects = ["국어", "수학Ⅰ", "영어Ⅰ", "물리학Ⅰ", "화학Ⅰ", "한국사", "체육"].enumerated().map { Lesson(subject: $1, room: "B30\($0)") }
        let ctx = Schedule.Context(week: week(Dictionary(uniqueKeysWithValues: subjects.enumerated().map { ($0 + 1, $1) })))
        let segs = LiveSegments.build(LiveSegments.trimMorning(Schedule.blocks(for: friday, context: ctx)))
        let now = friday.at(minutes: 13 * 60 + 30)
        guard let d = LiveActivityController.dayState(segs, now: now, startedAt: now, argb: { _ in 0xFF5B9BFF }) else { return XCTFail() }
        XCTAssertGreaterThan(d.lead, 0)
        XCTAssertEqual(d.day.first?.start, friday.at(minutes: 8 * 60), "막대는 8시부터")
        let past: [NowActivityAttributes.DayPart] = Array(d.day.prefix(d.lead))
        XCTAssertTrue(past.allSatisfy { (p: NowActivityAttributes.DayPart) -> Bool in p.end.timeIntervalSince(now) <= 0 && p.label.isEmpty && p.status.isEmpty })
        let current: NowActivityAttributes.DayPart = d.day[d.lead]
        XCTAssertTrue(current.start.timeIntervalSince(now) <= 0 && now.timeIntervalSince(current.end) < 0, "글자 구간은 지금 구간부터")
        var state = NowActivityAttributes.ContentState(status: "5교시 수업 중", title: "5교시 수업 중 화학Ⅰ", room: "B304", start: now, end: now,
                                                       accentARGB: 0, symbol: "book", next: "14:10 · 한국사 · B305", then: nil,
                                                       day: d.day, more: d.more, framed: d.framed)
        state.lead = d.lead
        let bytes = (try? JSONEncoder().encode(state))?.count ?? .max
        print("LIVE_LEAD lead=\(d.lead) parts=\(d.day.count) bytes=\(bytes)")
        XCTAssertLessThanOrEqual(bytes, 3600, "현황 데이터 \(bytes)바이트")
    }

    /// 실시간 현황의 하루 — 평일은 아침시간이 빠지고 등교 및 조회(8:00)부터, 주말은 12:00~13:30 점심시간부터.
    func testLiveDayStartsAtEight() {
        let ctx = Schedule.Context(week: week([1: Lesson(subject: "미적분Ⅱ", room: "B301")]))
        let weekday = LiveSegments.build(LiveSegments.trimMorning(Schedule.blocks(for: friday, context: ctx))).filter { !$0.isBlank }
        XCTAssertEqual(weekday.first?.status, "등교 및 조회")
        XCTAssertEqual(weekday.first?.start, friday.at(minutes: 8 * 60))
        XCTAssertFalse(weekday.contains { $0.status == "아침시간" })
        XCTAssertEqual(weekday.dropFirst().first?.status, "쉬는 시간")
        XCTAssertEqual(weekday.dropFirst().first?.end, friday.at(minutes: 8 * 60 + 20))
        let saturday = friday.adding(days: 1)
        let weekend = LiveSegments.build(LiveSegments.trimMorning(Schedule.blocks(for: saturday, context: ctx))).filter { !$0.isBlank }
        XCTAssertEqual(weekend.first?.start, saturday.at(minutes: 12 * 60), "주말은 12시부터")
        XCTAssertEqual(weekend.first?.status, "점심시간")
        XCTAssertEqual(weekend.first?.end, saturday.at(minutes: 13 * 60 + 30))
    }
}
