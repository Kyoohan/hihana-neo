import Foundation
import Observation

/// 데일리 브리핑 — 오늘 수업·일정과 앞으로 7일 일정 중 중요한 것(수행평가·시험·마감)을 AI 가 두세 문장으로 풀어 줍니다.
/// 요약 서버(/brief)에는 과목 이름·일정 제목·날짜만 보내고, 서버는 보관하지 않습니다. 하루 한 장을 기기에 두고 재료가 바뀌면 다시 만듭니다.
/// 서버에 못 닿거나 AI 를 끄면 AI 없이 중요한 일정만 추려 같은 자리에 둡니다.
@Observable
final class MorningBrief {
    static let shared = MorningBrief()
    private static let endpoint = URL(string: "https://hihana-summary.kyoohan0711ultra.workers.dev/brief")!
    /// 같은 날 일정이 바뀌어도 이보다 자주 AI 로 다시 만들지 않습니다.
    private static let minRemake: TimeInterval = 20 * 60
    private static let maxItems = 30
    private static let daysAhead = 7
    /// 수행평가·시험 — 제목으로 가립니다.
    static let examPattern = #"수행|평가|시험|고사|퀴즈|쪽지"#

    struct Highlight: Codable, Equatable { let date: LocalDate; let title: String; let note: String }

    struct Brief: Codable, Equatable {
        let date: LocalDate
        let headline: String
        let body: String
        let highlights: [Highlight]
        /// AI 가 만들었는지 — false 면 기기에서 일정만 추린 것.
        let ai: Bool
        /// 만든 재료의 지문.
        let inputKey: String
        var madeAt: Date
    }

    /// 오늘 브리핑 — 날짜가 지난 것은 화면에서 거릅니다.
    private(set) var current: Brief?
    private(set) var loading = false
    @ObservationIgnored private var running = false

    var aiEnabled: Bool { didSet { SharedContainer.defaults.set(aiEnabled, forKey: "morning_brief.ai") } }
    var notifyEnabled: Bool { didSet { SharedContainer.defaults.set(notifyEnabled, forKey: "morning_brief.notify") } }

    private init() {
        current = JSONCache.read(Brief.self, "morning_brief")?.value
        aiEnabled = SharedContainer.defaults.object(forKey: "morning_brief.ai") as? Bool ?? true
        notifyEnabled = SharedContainer.defaults.object(forKey: "morning_brief.notify") as? Bool ?? true
    }

    /// 홈에서 그날 브리핑을 본 적이 있는지 — 카드의 자리(맨 위·맨 아래)와 펼침을 정합니다.
    func seen(_ date: LocalDate) -> Bool { SharedContainer.defaults.string(forKey: "morning_brief.seen") == date.iso }
    func markSeen(_ date: LocalDate) { SharedContainer.defaults.set(date.iso, forKey: "morning_brief.seen") }

    private func save(_ b: Brief) {
        current = b
        JSONCache.write(b, "morning_brief")
    }

    // MARK: 재료

    struct Input {
        let classes: [String]
        let items: [AgendaItem]
        let json: [String: Any]
        var key: String {
            let data = (try? JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])) ?? Data()
            return String(decoding: data, as: UTF8.self).stableHash
        }
    }

    /// 오늘 수업(과목·시각)과 오늘부터 7일 안의 일정 — 면학감독·낸 과제·진작 시작해 이번 주에 안 끝나는 운영 기간은 뺍니다.
    static func input(today: LocalDate, blocks: [Block], schedule: [HanaScheduleEntry], grade: Int, extras: [AgendaItem]) -> Input {
        let classes: [String] = blocks.compactMap { b in
            guard case .lesson(let p, let l) = b.kind, !l.isFree else { return nil }
            return "\(p)교시 \(b.start.hm) \(l.title)"
        }
        let last = today.adding(days: daysAhead)
        let items = Array(Agenda.merge(schedule, grade: grade, extras: extras, today: today)
            .filter { !$0.isSupervision && !$0.done && $0.date <= last }
            .filter { !($0.date < today && ($0.endDate ?? $0.date) > last) }
            .prefix(maxItems))
        let json: [String: Any] = [
            "date": today.iso,
            "classes": classes,
            "items": items.map { i -> [String: Any] in
                ["date": i.date.iso, "end": i.endDate.flatMap { $0 != i.date ? $0.iso : nil } ?? NSNull(), "time": i.time ?? NSNull(),
                 "title": i.title, "kind": kind(i), "from": i.detail.map { String($0.prefix(40)) } ?? NSNull()]
            },
        ]
        return Input(classes: classes, items: items, json: json)
    }

    static func kind(_ i: AgendaItem) -> String {
        if i.title.range(of: examPattern, options: .regularExpression) != nil { return "시험·수행평가" }
        if i.source == .classroom && i.deadline { return "과제 마감" }
        if i.deadline { return "마감" }
        if let a = i.academic { return ScheduleKind.label(a.code) }
        if i.source == .mine { return "내 일정" }
        return "행사"
    }

    // MARK: 만들기

    /// 재료가 그대로면 둔 것을, 바뀌었으면 새로(AI 는 20분에 한 번까지). AI 가 실패하면 오늘 이미 AI 로 만든 것을, 없으면 기기에서 추린 것을.
    @MainActor
    @discardableResult
    func ensure(today: LocalDate, input: Input) async -> Brief? {
        if running { return current?.date == today ? current : nil }
        running = true
        defer { running = false }
        let old = current?.date == today ? current : nil
        let useAI = aiEnabled
        let now = Date()
        // 꺾쇠까지 따라 쓴 AI 답으로 만든 것은 다시 만듭니다.
        if let old, !(old.ai && Self.isWrapped(old.headline)) {
            let same = old.ai == useAI
            if same && old.inputKey == input.key { return old }
            if same && old.ai && now.timeIntervalSince(old.madeAt) < Self.minRemake { return old }
        }
        if !useAI { let b = Self.local(today, input); save(b); return b }
        loading = true
        defer { loading = false }
        do {
            let made = try await Self.request(today, input)
            save(made)
            return made
        } catch {
            var keep = old.flatMap { $0.ai ? $0 : nil } ?? Self.local(today, input)
            keep.madeAt = now
            save(keep)
            return keep
        }
    }

    /// 형식 예시의 꺾쇠까지 따라 쓴 AI 답(<제목>) — 이렇게 만든 것은 다시 만들고, 글은 양끝 <> 를 벗깁니다.
    static func isWrapped(_ s: String) -> Bool { let t = s.trimmingCharacters(in: .whitespacesAndNewlines); return t.hasPrefix("<") && t.hasSuffix(">") }
    private static func unwrap(_ s: String) -> String {
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return isWrapped(t) ? String(t.dropFirst().dropLast()).trimmingCharacters(in: .whitespacesAndNewlines) : t
    }

    private static func request(_ today: LocalDate, _ input: Input) async throws -> Brief {
        var req = URLRequest(url: endpoint)
        req.httpMethod = "POST"
        req.timeoutInterval = 45
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.setValue("hihana-neo", forHTTPHeaderField: "x-app")
        req.httpBody = try JSONSerialization.data(withJSONObject: input.json)
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200, let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw PortalError.unexpected("brief")
        }
        let headline = unwrap(o["headline"] as? String ?? "")
        let body = unwrap(o["body"] as? String ?? "")
        guard !headline.isEmpty, !body.isEmpty else { throw PortalError.unexpected("brief empty") }
        var hl: [Highlight] = (o["highlights"] as? [[String: Any]] ?? []).compactMap { h in
            guard let d = h.str("date").flatMap(LocalDate.init(iso:)) else { return nil }
            return Highlight(date: d, title: h.str("title") ?? "", note: h.str("note") ?? "")
        }
        // AI 가 중요 줄을 못 지켰으면(형식이 어긋나 서버가 버림) 기기에서 추린 중요 일정으로 채웁니다.
        if hl.isEmpty { hl = local(today, input).highlights }
        return Brief(date: today, headline: headline, body: body, highlights: hl, ai: true, inputKey: input.key, madeAt: Date())
    }

    /// AI 없이 — 시험·수행평가 → 마감 → 오늘 일정 → 휴업 순으로 넷까지, 머리는 그중 첫째.
    static func local(_ today: LocalDate, _ input: Input) -> Brief {
        func isExam(_ i: AgendaItem) -> Bool { i.title.range(of: examPattern, options: .regularExpression) != nil }
        func rank(_ i: AgendaItem) -> Int {
            if isExam(i) { return 0 }
            if i.deadline { return 1 }
            if i.daysFrom(today) == 0 { return 2 }
            if i.academic?.code == "SG01020000" { return 3 }
            return 9
        }
        let top = Array(input.items.filter { rank($0) < 9 }.sorted { (rank($0), $0.date) < (rank($1), $1.date) }.prefix(4))
        let exams = input.items.filter(isExam).count
        let deadlines = input.items.filter { $0.deadline && !isExam($0) }.count
        let todayCount = input.items.filter { $0.daysFrom(today) == 0 }.count
        let headline = top.first.map { "\(relativeDay(today, $0.date)) \($0.title)" }
            ?? (input.classes.isEmpty ? "오늘은 수업이 없어요" : "오늘 수업 \(input.classes.count)개")
        var parts: [String] = []
        if todayCount > 0 { parts.append("오늘 일정 \(todayCount)개") }
        if exams > 0 { parts.append("7일 안에 시험·수행평가 \(exams)개") }
        if deadlines > 0 { parts.append("마감 \(deadlines)개") }
        let body = parts.isEmpty ? "이번 주는 큰 마감이 없어요." : parts.joined(separator: ", ") + "가 있어요."
        let hl = top.map { Highlight(date: $0.date, title: $0.title, note: [kind($0), $0.time].compactMap { $0 }.joined(separator: " · ")) }
        return Brief(date: today, headline: headline, body: body, highlights: hl, ai: false, inputKey: input.key, madeAt: Date())
    }

    /// "오늘", "내일", "모레", 그 뒤는 "10/26(월)".
    static func relativeDay(_ today: LocalDate, _ date: LocalDate) -> String {
        switch LocalDate.days(from: today, to: date) {
        case 0: "오늘"
        case 1: "내일"
        case 2: "모레"
        default: "\(date.month)/\(date.day)(\(date.weekdayShort))"
        }
    }
}

/// 브리프가 '지금' 바로 아래에 있을 때 — 평일은 점심 전(12:10), 주말은 13:30 까지.
func briefMorning(_ now: Date) -> Bool {
    let weekend = now.localDate.weekday >= 6
    return now.minutesOfDay < (weekend ? 13 * 60 + 30 : 12 * 60 + 10)
}

/// 오늘에 걸친 일정 — 면학감독은 아래로, 그다음 시각순(마감은 같은 시각의 행사보다 앞). 몇 주씩 이어지는 상시 안내는 뺍니다.
func todayBrief(_ schedule: [HanaScheduleEntry], grade: Int, extras: [AgendaItem], today: LocalDate) -> [AgendaItem] {
    Agenda.merge(schedule, grade: grade, extras: extras, today: today)
        .filter { $0.daysFrom(today) == 0 && !$0.longRunning }
        .sorted { a, b in
            if a.isSupervision != b.isSupervision { return !a.isSupervision }
            if (a.time ?? "") != (b.time ?? "") { return (a.time ?? "") < (b.time ?? "") }
            if a.deadline != b.deadline { return a.deadline }
            return a.source < b.source
        }
}
