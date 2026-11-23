import Foundation
import Observation
import SwiftUI

/// 통합 일정의 출처와 색 — 학사일정 종류 색과도 겹치지 않게.
enum AgendaSource: Int, Codable, Comparable {
    case academic, board, classroom
    /// 학생이 직접 적은 일정(UserAgendaStore) — 분홍은 학사일정 종류 색·마감 빨강과 겹치지 않습니다.
    case mine

    var label: String {
        switch self {
        case .academic: "학사"
        case .board: "게시판"
        case .classroom: "클래스룸"
        case .mine: "내 일정"
        }
    }

    var color: Color {
        switch self {
        case .academic: Color(argb: 0xFF3A7BD5)
        case .board: Color(argb: 0xFFE3A82E)
        case .classroom: Color(argb: 0xFF1E8E3E)
        case .mine: Color(argb: 0xFFE0569B)
        }
    }

    static func < (a: Self, b: Self) -> Bool { a.rawValue < b.rawValue }
}

/// 학사일정·게시글에서 뽑은 일정·클래스룸 과제 마감을 한 줄 모양으로.
struct AgendaItem: Identifiable, Equatable {
    var id: String
    var source: AgendaSource
    var title: String
    var date: LocalDate
    /// 기간 일정의 마지막 날 — 하루짜리면 nil.
    var endDate: LocalDate? = nil
    /// "13:00" — 시각이 없으면 nil.
    var time: String? = nil
    /// 신청·제출 기한(마감)인지.
    var deadline = false
    /// 출처 설명 — 게시글 제목, 과목 이름.
    var detail: String? = nil
    /// 탭하면 열 곳 — 게시글 주소, 클래스룸 과제 주소.
    var url: String? = nil
    /// 클래스룸 과제를 이미 냈으면 true.
    var done = false
    /// 학사일정 원본.
    var academic: HanaScheduleEntry? = nil
    /// 대상 학년(1~3) — 비어 있으면 전 학년.
    var grades: Set<Int> = []

    func forGrade(_ grade: Int) -> Bool { grades.isEmpty || grades.contains(grade) }

    /// 며칠짜리인지 — 하루짜리는 1.
    var spanDays: Int { LocalDate.days(from: date, to: endDate ?? date) + 1 }

    /// 2주 넘게 이어지는 상시 운영 안내 — 학사일정 목록에선 숨깁니다.
    var longRunning: Bool { spanDays > 14 }

    /// 오늘 기준 며칠 남았는지 — 진행 중이면 0.
    func daysFrom(_ today: LocalDate) -> Int {
        let end = endDate ?? date
        if today >= date && today <= end { return 0 }
        return LocalDate.days(from: today, to: date)
    }

    var isSupervision: Bool { academic?.code == supervisionCode }
}

enum Agenda {
    /// 세 출처를 하나로 — 오늘 이후(진행 중인 기간 일정 포함)만, 날짜 → 면학감독은 아래로 → 시각 → 마감 먼저.
    /// 게시글 일정이 같은 날 학사일정과 이름이 겹치면 학사일정만 남깁니다.
    static func merge(_ schedule: [HanaScheduleEntry], grade: Int, extras: [AgendaItem], today: LocalDate) -> [AgendaItem] {
        let academic: [AgendaItem] = Supervision.filterByGrade(schedule, grade)
            .filter { $0.code != "SG02020000" && $0.date >= today }
            .map { e in
                AgendaItem(id: "a:\(e.date.iso):\(e.code):\(e.name):\(e.teacher ?? "")", source: .academic, title: ScheduleKind.title(e),
                           date: e.date, time: ScheduleKind.time(e), academic: e)
            }
        let academicKeys = academic.map { ($0.date, normalize($0.title)) }
        var groups: [String: [AgendaItem]] = [:]
        var order: [String] = []
        // 내 일정은 학생이 직접 적은 것이라 학사일정·게시글 일정과 이름이 겹쳐도 그대로 둡니다(지우면 적은 게 사라진 것처럼 보임).
        let mine = extras.filter { $0.source == .mine && ($0.endDate ?? $0.date) >= today }
        for item in extras where item.source != .mine && (item.endDate ?? item.date) >= today {
            let key = normalize(item.title)
            if academicKeys.contains(where: { $0.0 == item.date && ($0.1.contains(key) || key.contains($0.1)) }) { continue }
            let g = "\(item.date.iso)|\(key)"
            if groups[g] == nil { order.append(g) }
            groups[g, default: []].append(item)
        }
        let others = order.map { groups[$0]!.max { score($0) < score($1) }! }
        return (academic + others + mine).sorted { a, b in
            if a.date != b.date { return a.date < b.date }
            if a.isSupervision != b.isSupervision { return !a.isSupervision }
            let ta = a.time ?? "99", tb = b.time ?? "99"
            if ta != tb { return ta < tb }
            if a.deadline != b.deadline { return a.deadline }
            if a.source != b.source { return a.source < b.source }
            return a.title < b.title
        }
    }

    private static func score(_ i: AgendaItem) -> Int { (i.time != nil ? 2 : 0) + (i.deadline ? 1 : 0) }

    private static func normalize(_ s: String) -> String {
        s.replacingOccurrences(of: #"[\s·\-()\[\]]"#, with: "", options: .regularExpression).lowercased()
    }
}

/// 게시글에서 AI 가 뽑은 일정 한 건 — 요약 서버의 events 항목.
struct PostEvent: Codable, Equatable {
    var title: String
    var date: LocalDate
    var end: LocalDate? = nil
    var time: String? = nil
    var deadline = false
    var grades: Set<Int> = []
}

/// 게시글 일정 보관함 — 요약을 받을 때마다 글(제목·주소)과 일정을 함께 적어 두고, 홈·일정이 날짜 이후 것만 모아 씁니다.
@Observable
final class BoardAgendaStore {
    static let shared = BoardAgendaStore()

    private struct Record: Codable { var t: String; var u: String?; var ev: [PostEvent] }
    private struct Box: Codable { var order: [String]; var records: [String: Record] }

    private let name = "board_agenda_v2"
    private let max = 300
    /// 일정이라 하기 어려운 것 — 급식·식단 운영 안내.
    private let notAnEvent = #"급식|식단|조식|중식|석식"#
    /// 바뀔 때마다 1씩.
    private(set) var revision = 0
    @ObservationIgnored private var box: Box

    private init() { box = JSONCache.read(Box.self, "board_agenda_v2")?.value ?? Box(order: [], records: [:]) }

    /// 요약 서버의 일정 규칙이 바뀐 뒤 받아 둔 글의 요약을 한 번 새로 받아 일정을 다시 채웠는지.
    var needsRebuild: Bool { !SharedContainer.defaults.bool(forKey: "board_agenda.rebuilt_v7") }
    func markRebuilt() { SharedContainer.defaults.set(true, forKey: "board_agenda.rebuilt_v7") }

    func record(key: String, title: String, url: String?, events: [PostEvent]) {
        if events.isEmpty {
            if box.records.removeValue(forKey: key) != nil { save() }
            return
        }
        box.order = box.order.filter { $0 != key } + [key]
        if box.order.count > max {
            for k in box.order.prefix(box.order.count - max) { box.records.removeValue(forKey: k) }
            box.order = Array(box.order.suffix(max))
        }
        box.records[key] = Record(t: title, u: url, ev: events)
        save()
    }

    private func save() {
        JSONCache.write(box, name)
        revision += 1
    }

    /// 보관한 모든 글의 일정 — 날짜 거르기는 Agenda.merge 가.
    func items() -> [AgendaItem] {
        _ = revision
        var out: [AgendaItem] = []
        for (key, r) in box.records {
            // cr_ 는 클래스룸 공지에서 뽑은 일정 — 출처를 클래스룸으로.
            let classroom = key.hasPrefix("cr_")
            let category = classroom ? nil : r.u.flatMap(PostIDs.parse).flatMap { ids in BoardCategory.allCases.first { $0.bmtIdx == ids.bmt }?.label }
            for (i, e) in r.ev.enumerated() {
                if e.title.range(of: notAnEvent, options: .regularExpression) != nil { continue }
                // 신청 기간으로 적힌 마감은 끝날이 마감 — 기간 내내 '오늘 마감'으로 보이지 않게 끝날 하루로.
                let date = e.deadline && e.end.map({ $0 > e.date }) == true ? e.end! : e.date
                out.append(AgendaItem(
                    id: classroom ? "cr:\(key.dropFirst(3)):\(i)" : "b:\(key):\(i)",
                    source: classroom ? .classroom : .board,
                    title: e.title, date: date, endDate: e.deadline ? nil : e.end, time: e.time, deadline: e.deadline,
                    detail: [category, r.t.isEmpty ? nil : r.t].compactMap { $0 }.joined(separator: " · "),
                    url: r.u,
                    // 클래스룸은 내 수업 글이라 학년으로 거르지 않습니다('미적분2'의 숫자를 학년으로 읽음).
                    grades: classroom ? [] : e.grades.filter { (1...3).contains($0) }))
            }
        }
        return out
    }

    /// [prefix] 를 주면 그 키의 글만(클래스룸 연결 해제 때 "cr_").
    func clear(prefix: String? = nil) {
        if let prefix {
            box.order.filter { $0.hasPrefix(prefix) }.forEach { box.records.removeValue(forKey: $0) }
            box.order.removeAll { $0.hasPrefix(prefix) }
        } else {
            box = Box(order: [], records: [:])
        }
        save()
    }
}

/// 게시글 주소에서 (게시판, 글) 번호 — ".../board/1/1234/board_view.do" 또는 쿼리.
enum PostIDs {
    static func parse(_ url: String) -> (bmt: Int, bd: Int)? {
        if let r = url.range(of: #"/board/(\d+)/(\d+)/"#, options: .regularExpression) {
            let p = url[r].split(separator: "/").compactMap { Int($0) }
            if p.count >= 2 { return (p[0], p[1]) }
        }
        guard let c = URLComponents(string: url) else { return nil }
        let q = Dictionary((c.queryItems ?? []).map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { a, _ in a })
        if let b = q["bmtIdx"].flatMap(Int.init), let d = q["bdIdx"].flatMap(Int.init) { return (b, d) }
        return nil
    }
}
