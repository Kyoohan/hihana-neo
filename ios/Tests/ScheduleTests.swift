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
}
