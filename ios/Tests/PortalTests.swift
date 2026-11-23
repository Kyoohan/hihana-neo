import XCTest
@testable import HiHanaEvo

final class TimetableParserTests: XCTestCase {
    let html = """
    <div><table class="legend"><tr><td>교시</td><td>시간</td></tr><tr><td>1</td><td>08:20</td></tr></table>
    <table class="tt">
      <tr><th>교시</th><th>월</th><th>화</th><th>수</th><th>목</th><th>금</th></tr>
      <tr><td>1</td><td>확률과 통계<br/>B301</td><td>미적분Ⅱ B303</td><td></td><td>공강</td><td>문학&nbsp;A204</td></tr>
      <tr><td>2교시</td><td>스포츠 생활1(남)(5반) 김응주 / 체육관</td><td>AP 일반물리 A201</td><td>영어독해</td><td>화학 &amp; 실험 실험실2</td><td></td></tr>
    </table></div>
    """

    func testParsesGridByDayAndPeriod() throws {
        let week = try XCTUnwrap(TimetableParser.parse(html: html))
        XCTAssertEqual(week.lessons[1]?[1], Lesson(subject: "확률과 통계", room: "B301"))
        XCTAssertEqual(week.lessons[2]?[1], Lesson(subject: "미적분Ⅱ", room: "B303"))
        XCTAssertNil(week.lessons[3]?[1])
        XCTAssertEqual(week.lessons[4]?[1], .free)
        XCTAssertEqual(week.lessons[5]?[1], Lesson(subject: "문학", room: "A204"))
        XCTAssertEqual(week.lessons[1]?[2]?.room, "체육관")
        XCTAssertEqual(week.lessons[1]?[2]?.teacher, "김응주")
        XCTAssertEqual(week.lessons[3]?[2], Lesson(subject: "영어독해", room: nil))
        XCTAssertEqual(week.lessons[4]?[2]?.room, "실험실2")
        XCTAssertEqual(week.lessons[4]?[2]?.subject, "화학 & 실험")
    }

    func testPicksTheFullestTableNotTheLegend() throws {
        let week = try XCTUnwrap(TimetableParser.parse(html: html))
        XCTAssertEqual(week.lessons.values.reduce(0) { $0 + $1.count }, 8)
    }

    func testRejectsPagesWithoutADayHeader() {
        XCTAssertNil(TimetableParser.parse(html: "<table><tr><td>1</td><td>a</td></tr></table>"))
        XCTAssertNil(TimetableParser.parse(html: "<html>no table</html>"))
    }

    func testRoomHeuristics() {
        for room in ["B301", "A205", "컴퓨터실2", "체육관", "1203", "시청각실"] { XCTAssertTrue(TimetableParser.looksLikeRoom(room), room) }
        for notRoom in ["미적분Ⅱ", "문학", "영어"] { XCTAssertFalse(TimetableParser.looksLikeRoom(notRoom), notRoom) }
    }

    func testParsedWeekFeedsTheSchedule() throws {
        let week = try XCTUnwrap(TimetableParser.parse(html: html))
        let monday = LocalDate(year: 2026, month: 10, day: 26)
        let blocks = Schedule.blocks(for: monday, context: Schedule.Context(week: week))
        XCTAssertTrue(blocks.contains { $0.statusLabel == "1교시 수업 중" && $0.room == "B301" })
        for (a, b) in zip(blocks, blocks.dropFirst()) { XCTAssertEqual(a.end, b.start) }
    }
}

final class DailySyncTests: XCTestCase {
    /// 면학 위치 저장소는 오늘 앞뒤 며칠만 남기므로 오늘 날짜로 확인합니다(평일 일과로).
    let friday = LocalDate.today()

    /// 그 날 저장된 것을 비우고 반영한 뒤, 저장된 타임만 돌려줍니다(기본값 제외).
    private func applied(_ a: [DailySync.Assignment], _ p: [DailySync.Program]) -> [PlanSlot: StudyPlace] {
        for slot in PlanSlot.allCases { PlaceStore.set(nil, slot: slot, date: friday) }
        DailySync.apply(DailySync.Sync(assignments: a, programs: p, classrooms: []), date: friday, isRestDay: false)
        var out: [PlanSlot: StudyPlace] = [:]
        for (k, v) in PlaceStore.all()[friday.iso] ?? [:] {
            if let slot = PlanSlot(rawValue: k), let place = StudyPlace(storageValue: v) { out[slot] = place }
        }
        for slot in PlanSlot.allCases { PlaceStore.set(nil, slot: slot, date: friday) }
        return out
    }

    func testMapsAssignmentsToPlaces() {
        let a = [
            DailySync.Assignment(stNm: "평일1타임", category: "도서관", floor: "2F", seat: "46"),
            DailySync.Assignment(stNm: "평일2타임", category: "생활관", floor: nil, seat: nil),
            DailySync.Assignment(stNm: "평일 0타임", category: "면학실", floor: "3F", seat: "12"),
            DailySync.Assignment(stNm: "엉뚱한이름", category: "도서관", floor: "1F", seat: "1"),
        ]
        let out = applied(a, [])
        XCTAssertEqual(out[.weekday1], .library(seat: "2-46"))
        XCTAssertEqual(out[.weekday2], .dorm)
        XCTAssertEqual(out[.weekday0], .studyRoom(seat: "3F 12"))
        XCTAssertEqual(out.count, 3)
    }

    func testProgramsOverrideTheSameSlot() {
        let a = [DailySync.Assignment(stNm: "평일0타임", category: "도서관", floor: "2F", seat: "10")]
        let p = [
            DailySync.Program(stNm: "평일0타임", gubun: "O", name: "야구1", building: "특별실", room: "운동장", date: friday.iso),
            DailySync.Program(stNm: "평일1타임", gubun: "A", name: "수학 심화", building: nil, room: nil, date: friday.iso),
            DailySync.Program(stNm: "평일2타임", gubun: "O", name: "다른 날", building: nil, room: nil, date: friday.adding(days: 1).iso),
        ]
        let out = applied(a, p)
        XCTAssertEqual(out[.weekday0], .oneTwo(activity: "야구1", room: "특별실 운동장"))
        XCTAssertEqual(out[.weekday1], .afterSchool(course: "수학 심화", room: nil))
        XCTAssertNil(out[.weekday2])
    }

    func testLibraryLocationFormats() {
        XCTAssertEqual(DailySync.libraryLocation(floor: "2F", seat: "34번"), "2-34")
        XCTAssertEqual(DailySync.libraryLocation(floor: nil, seat: "2-46"), "2-46")
        XCTAssertEqual(DailySync.libraryLocation(floor: "2F", seat: nil), "2")
        XCTAssertEqual(DailySync.libraryLocation(floor: nil, seat: nil), "위치 미확인")
    }

    func testUnknownCategoryIsNotMislabelledAsClassroom() {
        let place = DailySync.place(for: .init(stNm: "평일1타임", category: "체육관", floor: nil, seat: "3"))
        XCTAssertEqual(place, .other(category: "체육관", location: "3"))
        XCTAssertNil(DailySync.place(for: .init(stNm: "평일1타임", category: "", floor: nil, seat: nil)))
    }
}

final class PortalClientTests: XCTestCase {
    func testFormEncodingEscapesReservedCharacters() {
        let body = String(decoding: PortalClient.form([("mem_id", "a b&c"), ("mem_pwd", "p=w+d/한")]), as: UTF8.self)
        XCTAssertEqual(body, "mem_id=a%20b%26c&mem_pwd=p%3Dw%2Bd%2F%ED%95%9C")
    }

    func testLoginPageDetection() {
        XCTAssertTrue(PortalClient.looksLikeLoginPage("var hanaLoginRequestToken = \"abc\";"))
        XCTAssertTrue(PortalClient.looksLikeLoginPage(#"{"result":"expired"}"#))
        XCTAssertFalse(PortalClient.looksLikeLoginPage(#"{"paging":{"result":[]}}"#))
    }

    func testTokenExtraction() {
        let html = "<script>var hanaLoginRequestToken = \"tok-123_x\";</script>"
        XCTAssertEqual(PortalClient.firstMatch(#"hanaLoginRequestToken\s*=\s*"([^"]+)""#, in: html), "tok-123_x")
    }
}
