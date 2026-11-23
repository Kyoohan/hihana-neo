import Foundation

// MARK: - 강조 색 키 (실제 색은 UI 에서)

enum Accent { case lesson, free, breakTime, breakfast, lunch, dinner, snack, study, library, dorm, idle, afterSchool, oneTwo, outing }

// MARK: - 면학 타임

enum PlanSlot: String, CaseIterable, Codable {
    case weekday0, weekday1, weekday2, weekend1, weekend2, weekend3, weekend4

    var title: String {
        switch self {
        case .weekday0: "0타임"
        case .weekday1, .weekend1: "1타임"
        case .weekday2, .weekend2: "2타임"
        case .weekend3: "3타임"
        case .weekend4: "4타임"
        }
    }

    var timeText: String {
        switch self {
        case .weekday0: "16:20 – 17:50"
        case .weekday1: "19:00 – 21:00"
        case .weekday2: "21:30 – 23:10"
        case .weekend1: "13:30 – 15:30"
        case .weekend2: "16:00 – 17:50"
        case .weekend3: "19:00 – 21:00"
        case .weekend4: "21:30 – 23:10"
        }
    }

    /// 생활관을 고를 수 있는 타임(평일 2타임 · 주말 4타임).
    var allowsDorm: Bool { self == .weekday2 || self == .weekend4 }

    /// 아무것도 안 골랐을 때 — 생활관 타임만 자동으로.
    var defaultPlace: StudyPlace? { allowsDorm ? .dorm : nil }

    /// 손으로 고를 수 있는 타임 — 0타임은 동기화로만 채워집니다. 공휴일도 주말과 같은 1~4타임.
    static func slots(isRestDay: Bool) -> [PlanSlot] { isRestDay ? [.weekend1, .weekend2, .weekend3, .weekend4] : [.weekday1, .weekday2] }

    /// "평일0타임"·"주말3타임" 같은 포털 이름 → 슬롯. 모르는 이름이면, 이름 속 번호와 조회한 날짜(평일/휴일)로 추정합니다.
    static func from(portalName: String, date: LocalDate, isRestDay: Bool) -> PlanSlot? {
        let compact = portalName.replacingOccurrences(of: " ", with: "")
        switch compact {
        case "평일0타임": return .weekday0
        case "평일1타임": return .weekday1
        case "평일2타임": return .weekday2
        case "주말1타임": return .weekend1
        case "주말2타임": return .weekend2
        case "주말3타임": return .weekend3
        case "주말4타임": return .weekend4
        default: break
        }
        guard compact.contains("타임"),
              let m = compact.range(of: #"(\d+)\s*타임"#, options: .regularExpression),
              let n = Int(compact[m].filter(\.isNumber)) else { return nil }
        if isRestDay { return [1: .weekend1, 2: .weekend2, 3: .weekend3, 4: .weekend4][n] }
        return [0: .weekday0, 1: .weekday1, 2: .weekday2][n]
    }
}

struct StudySession: Equatable {
    let name: String
    let start: Int
    let end: Int
}

// MARK: - 장소

enum StudyPlace: Equatable, Codable {
    case classroom(String)
    case studyRoom(seat: String)
    case library(seat: String)
    case dorm
    case afterSchool(course: String, room: String?)
    case oneTwo(activity: String, room: String?)
    case other(category: String, location: String?)
    case midnight(seat: String?)

    /// 큰 글씨로 보일 장소 이름.
    var name: String {
        switch self {
        case .classroom: "교과교실"
        case .studyRoom: "면학실"
        case .library: "도서관"
        case .dorm: "생활관"
        case .afterSchool: "방과후"
        case .oneTwo: "1인2기"
        case .other(let category, _): category.isEmpty ? "기타 장소" : category
        case .midnight: "심야면학"
        }
    }

    /// 구체적인 목적지(교실이나 자리 번호).
    var detail: String? {
        switch self {
        case .classroom(let n): n
        case .studyRoom(let s), .library(let s): s
        case .dorm: nil
        case .afterSchool(let c, let r): r.map { "\(c) · \($0)" } ?? c
        case .oneTwo(let a, let r): r.map { "\(a) · \($0)" } ?? a
        case .other(_, let l): l
        case .midnight(let s): s
        }
    }

    var accent: Accent {
        switch self {
        case .classroom, .studyRoom, .other, .midnight: .study
        case .library: .library
        case .dorm: .dorm
        case .afterSchool: .afterSchool
        case .oneTwo: .oneTwo
        }
    }

    /// 저장용 한 줄 — 안드로이드 storageValue 와 같은 꼴.
    var storageValue: String {
        switch self {
        case .classroom(let n): "classroom:\(n)"
        case .studyRoom(let s): "study:\(s)"
        case .library(let s): "library:\(s)"
        case .dorm: "dorm"
        case .afterSchool(let c, let r): "afterschool:\(c)|\(r ?? "")"
        case .oneTwo(let a, let r): "onetwo:\(a)|\(r ?? "")"
        case .other(let c, let l): "other:\(c)|\(l ?? "")"
        case .midnight(let s): "midnight:\(s ?? "")"
        }
    }

    init?(storageValue value: String) {
        func split(_ payload: Substring) -> (String, String?) {
            let parts = payload.split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false)
            let a = String(parts.first ?? "")
            let b = parts.count > 1 ? String(parts[1]) : ""
            return (a, b.isEmpty ? nil : b)
        }
        if value == "dorm" { self = .dorm; return }
        if value.hasPrefix("midnight:") { let s = value.dropFirst(9); self = .midnight(seat: s.isEmpty ? nil : String(s)); return }
        if value.hasPrefix("other:") { let (c, l) = split(value.dropFirst(6)); guard !c.isEmpty else { return nil }; self = .other(category: c, location: l); return }
        if value.hasPrefix("afterschool:") { let (c, r) = split(value.dropFirst(12)); guard !c.isEmpty else { return nil }; self = .afterSchool(course: c, room: r); return }
        if value.hasPrefix("onetwo:") { let (a, r) = split(value.dropFirst(7)); guard !a.isEmpty else { return nil }; self = .oneTwo(activity: a, room: r); return }
        let parts = value.split(separator: ":", maxSplits: 1)
        guard parts.count == 2, !parts[1].isEmpty else { return nil }
        switch parts[0] {
        case "classroom": self = .classroom(String(parts[1]))
        case "study": self = .studyRoom(seat: String(parts[1]))
        case "library": self = .library(seat: String(parts[1]))
        default: return nil
        }
    }

    /// SF Symbol 이름.
    var symbol: String {
        switch self {
        case .classroom: "person.2.square.stack"
        case .studyRoom: "chair"
        case .library: "books.vertical"
        case .dorm: "bed.double"
        case .afterSchool: "graduationcap"
        case .oneTwo: "figure.run"
        case .other: "mappin.and.ellipse"
        case .midnight: "moon.stars"
        }
    }
}

// MARK: - 수업

struct Lesson: Equatable, Codable {
    let subject: String
    let room: String?

    static let free = Lesson(subject: "공강", room: nil)

    var isFree: Bool { room == nil }

    /// 포털 과목명은 "스포츠 생활1(남)(5반) 김응주 /" 처럼 끝에 선생님과 빗금이 붙어 옵니다 — 그걸 뗀 과목명.
    var title: String { split.title }
    var teacher: String? { split.teacher }

    /// 화면에 쓰는 장소 — 선생님이 있으면 "체육관 · 김응주". 교실이 없으면(공강) nil.
    var place: String? { room.map { r in [r, teacher].compactMap { $0 }.joined(separator: " · ") } }

    private var split: (title: String, teacher: String?) {
        var t = subject.trimmingCharacters(in: .whitespaces)
        if t.hasSuffix("/") { t.removeLast() }
        t = t.trimmingCharacters(in: .whitespaces)
        let re = try! NSRegularExpression(pattern: #"^(.*\))\s+([가-힣]{2,4})$"#)
        if let m = re.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)),
           let a = Range(m.range(at: 1), in: t), let b = Range(m.range(at: 2), in: t) {
            return (String(t[a]).trimmingCharacters(in: .whitespaces), String(t[b]))
        }
        return (t, nil)
    }

    /// 과목 아이콘 키(안드로이드 Lesson.iconKey 와 같은 규칙) — 규칙은 SubjectIcon.
    /// 선생님 이름을 뗀 과목명(title)으로 봅니다: "국어(3반) 조형준" 의 '조형'이 미술로 잡히지 않게.
    var iconKey: String { isFree ? SubjectIcon.freeKey : SubjectIcon.key(for: title) }

    /// SF Symbol — 과목에 맞는 것, 없으면 책. 앱·위젯·실시간 활동이 모두 이것을 씁니다.
    var symbol: String { SubjectIcon.symbol(forKey: iconKey) }
}

/// 포털에서 받아 온 주간 시간표 — 요일(월=1…일=7) → 교시 → 수업. 교시 시각은 포털이 주면 그것, 아니면 기본.
struct TimetableWeek: Equatable, Codable {
    var lessons: [Int: [Int: Lesson]]
    var periodTimes: [Int: [Int]]? = nil // 교시 → [시작분, 끝분]
}

// MARK: - 블록

enum NextUp: Equatable {
    case lesson(period: Int, Lesson)
    case study(StudySession, StudyPlace?)

    var title: String {
        switch self {
        case .lesson(_, let l): l.title
        case .study(_, let p): p?.name ?? "위치 미설정"
        }
    }

    var room: String? {
        switch self {
        case .lesson(_, let l): l.place
        case .study(_, let p): p?.detail
        }
    }

    var period: Int? { if case .lesson(let p, _) = self { p } else { nil } }
}

/// 쉬는 시간·식사처럼 다음 것을 기다리는 구간.
struct Gap: Equatable {
    var label: String
    var fallbackTitle: String
    var caption: String
    var next: NextUp?
    var accent: Accent
}

/// 외출·외박 구간 — 포털 외출 내역에서(신청·승인·복귀만). 위젯도 쓰므로 저장할 수 있게.
struct OutingSpan: Codable, Equatable {
    var start: Date
    var end: Date
    var reason: String?
    /// 승인 전(신청 상태).
    var pending: Bool
    var overnight: Bool { start.localDate != end.localDate }
}

enum BlockKind: Equatable {
    case lesson(period: Int, Lesson)
    case study(StudySession, StudyPlace?)
    case gap(Gap)
    /// 외출·외박 — 그동안의 일과 대신.
    case outing(OutingSpan)
    /// 면학이 끝난 뒤 다음 날 기상 전까지 — 아무것도 보이지 않습니다.
    case blank
}

struct Block: Equatable, Identifiable {
    var kind: BlockKind
    var start: Date
    var end: Date

    var id: String { "\(start.timeIntervalSince1970)-\(statusLabel)" }

    var statusLabel: String {
        switch kind {
        case .lesson(let p, let l): l.isFree ? "\(p)교시 · 공강" : "\(p)교시 수업 중"
        case .study(let s, _): s.name
        case .gap(let g): g.label
        case .outing(let o): (o.overnight ? "외박" : "외출") + (o.pending ? " · 승인 대기" : "")
        case .blank: ""
        }
    }

    var title: String {
        switch kind {
        case .lesson(_, let l): l.title
        case .study(_, let p): p?.name ?? "위치 미설정"
        case .gap(let g): g.next?.title ?? g.fallbackTitle
        case .outing(let o): o.reason ?? (o.overnight ? "외박 중" : "외출 중")
        case .blank: ""
        }
    }

    /// 한 줄 제목 — 상태 뒤에 이름을 붙이되, 상태가 이미 이름으로 끝나면 되풀이하지 않습니다.
    var headline: String { title.isEmpty || statusLabel.hasSuffix(title) ? statusLabel : "\(statusLabel) \(title)" }

    /// 이동해야 할 교실이나 자리.
    var room: String? {
        switch kind {
        case .lesson(_, let l): l.place
        case .study(_, let p): p?.detail
        case .gap(let g): g.next?.room
        case .outing(let o): "\(o.end.localDate == start.localDate ? "" : "\(o.end.localDate.month)/\(o.end.localDate.day) ")\(o.end.hm) 복귀"
        case .blank: nil
        }
    }

    var isBlank: Bool { if case .blank = kind { true } else { false } }
    var isGap: Bool { if case .gap = kind { true } else { false } }

    var accent: Accent {
        switch kind {
        case .lesson(_, let l): l.isFree ? .free : .lesson
        case .study(_, let p): p?.accent ?? .idle
        case .gap(let g): g.accent
        case .outing: .outing
        case .blank: .idle
        }
    }

    var symbol: String {
        switch kind {
        case .lesson(_, let l): l.symbol
        case .study(_, let p): p?.symbol ?? "mappin.and.ellipse"
        case .gap(let g):
            switch g.next {
            case .lesson(_, let l): l.symbol
            case .study(_, let p): p?.symbol ?? "mappin.and.ellipse"
            case nil: "mappin.and.ellipse"
            }
        case .outing: "figure.walk"
        case .blank: "moon"
        }
    }

    var timeRangeText: String { "\(start.hm) – \(end.hm)" }
}
