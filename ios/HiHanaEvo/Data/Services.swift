import Foundation

/// 신청·내역 서비스 — 포털 경로는 안드로이드 ApplyService 와 같습니다.
enum ApplyService: String, CaseIterable, Hashable {
    case studyRoom = "STUDY_ROOM", library = "LIBRARY", classroom = "CLASSROOM", outing = "OUTING"

    var label: String {
        switch self {
        case .studyRoom: "면학실"
        case .library: "도서관"
        case .classroom: "교과교실"
        case .outing: "외출·외박"
        }
    }

    var applyPath: String {
        switch self {
        case .studyRoom: "/main/studyroom/study-apply.do"
        case .library: "/main/library/library-apply.do"
        case .classroom: "/main/classroom/apply.do"
        case .outing: "/main/outing/apply_inscr.do"
        }
    }

    var historyPath: String {
        switch self {
        case .studyRoom: "/main/studyroom/study-history.do"
        case .library: "/main/library/library-history.do"
        case .classroom: "/main/classroom/history.do"
        case .outing: "/main/outing/history.do"
        }
    }
}

/// 좌석 서비스 — 도서관·면학실.
enum SeatService: String, CaseIterable, Hashable {
    case library = "LIBRARY", studyRoom = "STUDY_ROOM"

    var label: String { self == .library ? "도서관" : "면학실" }
    var applyPage: String { self == .library ? "/main/library/library-apply.do" : "/main/studyroom/study-apply.do" }
    var listPaths: [String] {
        self == .library ? ["/main/library/study-room-req-list.json"]
            : ["/main/studyroom/study-room-req-list.json", "/main/studyroom/study-req-list.json", "/main/studyroom/studyroom-req-list.json"]
    }
    var reservePaths: [String] {
        self == .library ? ["/main/library/study-room-req.json"]
            : ["/main/studyroom/study-room-req.json", "/main/studyroom/study-req.json", "/main/studyroom/studyroom-req.json"]
    }
    /// 기기 등록의 usegubun — 도서관 L, 면학실 C.
    var deviceGubun: String { self == .library ? "L" : "C" }
}
