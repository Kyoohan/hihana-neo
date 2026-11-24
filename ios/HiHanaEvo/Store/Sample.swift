import Foundation

/// 시뮬레이터 확인용 샘플 — 실행 인자 `-demo`.
enum Sample {
    static let week: TimetableWeek = {
        let mon: [Int: Lesson] = [
            1: Lesson(subject: "확률과 통계", room: "B301"),
            2: Lesson(subject: "AP 일반물리", room: "A201"),
            3: Lesson(subject: "문학", room: "A204"),
            4: Lesson(subject: "미적분Ⅱ", room: "B303"),
            5: Lesson(subject: "스포츠 생활1(남)(5반) 김응주 /", room: "체육관"),
            6: Lesson(subject: "스포츠 생활1(남)(5반) 김응주 /", room: "체육관"),
            7: .free,
        ]
        let tue: [Int: Lesson] = [
            1: Lesson(subject: "데이터 과학과 인공지능", room: "C102"),
            2: Lesson(subject: "데이터 과학과 인공지능", room: "C102"),
            3: Lesson(subject: "영어 독해와 작문", room: "A305"),
            4: .free,
            5: Lesson(subject: "화학Ⅱ", room: "과학실2"),
            6: Lesson(subject: "미적분Ⅱ", room: "B303"),
        ]
        return TimetableWeek(lessons: [1: mon, 2: tue, 3: mon, 4: tue, 5: mon])
    }()

    static let places: [PlanSlot: StudyPlace] = [
        .weekday0: .oneTwo(activity: "야구1", room: "특별실 운동장"),
        .weekday1: .library(seat: "2-46"),
        .weekday2: .dorm,
        .weekend1: .studyRoom(seat: "3F 12"),
        .weekend4: .dorm,
    ]

    static let meals = DayMeals(
        items: [
            .breakfast: ["다이제그래놀라", "초코롤케익+식빵", "우유"].map { MealItem(name: $0, codes: []) },
            .lunch: ["보리밥", "김치말이냉국수", "열탄오리돈불고기", "갈비손만두찜", "배추겉절이"].map { MealItem(name: $0, codes: []) },
            .dinner: ["율무밥", "전복한우스테이크", "마라탕", "계란찜", "취나물무침"].map { MealItem(name: $0, codes: [5, 6, 16]) },
            .snack: ["치즈곶감찹쌀떡", "디요거트"].map { MealItem(name: $0, codes: [2]) },
        ],
        photos: [:]
    )

    static func schedule(_ today: LocalDate) -> [HanaScheduleEntry] {
        [
            HanaScheduleEntry(code: "SG01010000", name: "2학기 방과후학교 강의", place: nil, startTime: nil, endTime: nil, allDay: true, date: today),
            HanaScheduleEntry(code: "SG03010000", name: "", place: nil, startTime: "19:00", endTime: "21:00", allDay: false, date: today, grade: "2", teacher: "2.김보경"),
            HanaScheduleEntry(code: "SG02010000", name: "학생회 정기회의", place: "세미나실", startTime: "17:00", endTime: "17:40", allDay: false, date: today.adding(days: 1)),
            HanaScheduleEntry(code: "SG01010000", name: "진로 탐색 주간", place: nil, startTime: nil, endTime: nil, allDay: true, date: today.adding(days: 4)),
            HanaScheduleEntry(code: "SG01010000", name: "2학기 기말고사", place: nil, startTime: nil, endTime: nil, allDay: true, date: today.adding(days: 52)),
        ]
    }

    /// 지금 걸쳐 있는 외출 하나(승인, 한 시간 전 ~ 세 시간 뒤) — 타임라인·홈 확인용.
    static func outings(_ today: LocalDate) -> [OutingRow] {
        let h = Clock.now.minutesOfDay / 60
        return [OutingRow(osmNo: 1, stdMemNo: "", state: 4, stateLabel: "승인", plannedOut: today.at(minutes: (h - 1) * 60), plannedBack: today.at(minutes: (h + 3) * 60),
                   actualOut: nil, actualBack: nil, duration: nil, reason: "병원 진료", appliedAt: nil, group: false)]
    }

    static func board(_ today: LocalDate) -> [HanaBoardPost] {
        [
            HanaBoardPost(bmtIdx: 1, bdIdx: -1, title: "2026 국제교류 프로그램 신청 기간 연장 안내", writer: "국제교류부", date: "\(today.iso) 07:40:00", isNew: true, grades: [], url: "https://hi.hana.hs.kr/main/board/1/-1/board_view.do"),
            HanaBoardPost(bmtIdx: 1, bdIdx: -2, title: "2학기 방과후학교 강의 시간 변경", writer: "교무부", date: "\(today.adding(days: -1).iso) 16:40:00", isNew: false, grades: ["2학년"], url: "https://hi.hana.hs.kr/main/board/1/-2/board_view.do"),
            HanaBoardPost(bmtIdx: 1, bdIdx: -3, title: "도서관 야간 개방 안내", writer: "도서관", date: "\(today.adding(days: -2).iso) 09:05:00", isNew: false, grades: [], url: "https://hi.hana.hs.kr/main/board/1/-3/board_view.do"),
        ]
    }

    static let alims: [HanaAlim] = [
        HanaAlim(id: "s1", content: "오늘 1타임 면학 장소가 도서관 2층으로 바뀌었습니다.", writer: "김보경", date: "2026-10-24 15:02:00", read: false),
        HanaAlim(id: "s2", content: "10/27(화) 해외진학 설명회에 참석할 학생은 7교시 후 시청각실로 오세요.", writer: "진학지원부", date: "2026-10-23 17:36:00", read: true),
    ]
}
