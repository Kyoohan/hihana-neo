import XCTest
@testable import HiHanaEvo

/// 귀가 기간 — 안드로이드 HomeStayScheduleTest 와 같은 경우들.
final class HomeStayTests: XCTestCase {
    func d(_ m: Int, _ day: Int) -> LocalDate { LocalDate(year: 2026, month: m, day: day) }
    func e(_ m: Int, _ day: Int, _ name: String, _ code: String = "SG02010000") -> HanaScheduleEntry {
        HanaScheduleEntry(code: code, name: name, place: nil, startTime: nil, endTime: nil, allDay: true, date: d(m, day))
    }
    func run(_ entries: [HanaScheduleEntry], grade: Int = 2) -> [LocalDate: Schedule.HomeStay] {
        HomeStaySchedule.compute(marks: entries.compactMap(HomeStaySchedule.parse), grade: grade)
    }

    func testRealSchedule() {
        let map = run([e(11, 12, "2학년상담", "SG02020000"), e(11, 19, "대수능(3)", "SG01010000"), e(11, 19, "1,2학년 귀가"),
                       e(11, 20, "학부모 상담 종료(1,2학년)"), e(11, 22, "귀교(1,2,3)"), e(12, 4, "귀가(1,2)"), e(12, 6, "귀교(1,2)")])
        XCTAssertEqual(map, [d(11, 19): .leave, d(11, 20): .away, d(11, 21): .away, d(11, 22): .return,
                             d(12, 4): .leave, d(12, 5): .away, d(12, 6): .return])
    }

    func testTwoShortPeriods() {
        XCTAssertEqual(run([e(11, 18, "귀가(1,2)"), e(11, 19, "귀교(1,2)"), e(11, 20, "귀가(1,2)"), e(11, 22, "귀교(1,2,3)")]),
                       [d(11, 18): .leave, d(11, 19): .return, d(11, 20): .leave, d(11, 21): .away, d(11, 22): .return])
    }

    func testUnpairedDaysOnly() {
        XCTAssertEqual(run([e(11, 19, "1,2학년 귀가일 안내"), e(11, 22, "귀교(1,2,3)")]), [d(11, 22): .return])
        XCTAssertEqual(run([e(12, 4, "귀가(1,2)"), e(12, 6, "귀교 안내")]), [d(12, 4): .leave])
    }

    func testNames() {
        func g(_ name: String, _ code: String = "SG02010000") -> Set<Int>? { HomeStaySchedule.parse(e(11, 19, name, code))?.grades }
        XCTAssertEqual(g("1,2학년 귀가"), [1, 2])
        XCTAssertEqual(g("귀교(1,2,3)"), [1, 2, 3])
        XCTAssertEqual(g("귀가 (1, 2학년)"), [1, 2])
        XCTAssertEqual(g("귀가(3학년 제외)"), [1, 2])
        XCTAssertEqual(g("귀가(13시 이후)"), [])
        XCTAssertEqual(g("귀가(전학년)"), [])
        XCTAssertEqual(g("수능 귀가"), [])
        XCTAssertEqual(g("귀교（1,2）"), [1, 2])
        XCTAssertNil(g("코람데오 귀가 예배"))
        XCTAssertNil(g("버스 귀가", "SG02020000"))
        XCTAssertNil(g("귀가", "SG03010000"))
        XCTAssertEqual(run([e(11, 19, "3학년 귀가"), e(11, 20, "귀가(3)")]), [:])
    }

    func testHistory() {
        let merged = HomeStaySchedule.merge([e(11, 19, "1,2학년 귀가"), e(9, 1, "귀가(1,2,3)")],
                                            [e(11, 21, "수강과목 수요조사(1,2학년)"), e(11, 22, "귀교(1,2,3)")], today: d(11, 22))
        XCTAssertEqual(merged, [e(11, 19, "1,2학년 귀가"), e(11, 22, "귀교(1,2,3)")])
        XCTAssertEqual(HomeStaySchedule.compute(marks: merged.compactMap(HomeStaySchedule.parse), grade: 2),
                       [d(11, 19): .leave, d(11, 20): .away, d(11, 21): .away, d(11, 22): .return])
        XCTAssertEqual(HomeStaySchedule.merge([e(12, 4, "귀가(1,2)")], [e(11, 30, "3학년 수능후/ 특강", "SG02020000")], today: d(12, 1)), [])
    }
}
