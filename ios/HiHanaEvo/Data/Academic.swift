import Foundation
import Observation
import SwiftUI

// MARK: - 모델

/// 알리미 한 건 — [id] 는 att_idx(포털에 보낼 번호도 이것), [content] 는 HTML 을 걷어낸 본문.
struct HanaAlim: Codable, Equatable, Identifiable {
    var id: String
    var content: String
    var writer: String?
    var date: String?
    var read: Bool
    var aimIdx: String?

    var portalIdx: String { id }
}

/// 학사일정 한 건 — [date] 는 하루씩 조회하며 붙인 날짜라 믿을 수 있습니다.
struct HanaScheduleEntry: Codable, Equatable, Hashable {
    var code: String
    var name: String
    var place: String?
    var startTime: String?
    var endTime: String?
    var allDay: Bool
    var date: LocalDate
    /// 면학감독 대상 학년(`smm_class` 원값).
    var grade: String? = nil
    /// 면학감독 담당 교사.
    var teacher: String? = nil
    /// mainYn=Y 요약 조회에서 병합된 행인지.
    var mainYn: Bool = false
}

/// 게시판 글 한 건.
struct HanaBoardPost: Codable, Equatable, Hashable, Identifiable {
    var bmtIdx: Int
    var bdIdx: Int
    var title: String
    var writer: String?
    var date: String?
    var isNew: Bool
    var grades: [String]
    var url: String

    var id: String { "\(bmtIdx):\(bdIdx)" }
}

/// 시험 D-day — 오늘 기준 가장 가까운 미래 시험.
struct ExamDday: Equatable { let name: String; let date: LocalDate; let days: Int }

/// 포털 게시판 탭.
enum BoardCategory: Int, CaseIterable, Identifiable, Codable {
    case studentNotice, subjectNotice, homeLetter, woojeong, studentBoard

    var id: Int { rawValue }

    var bmtIdx: Int {
        switch self {
        case .studentNotice: 1
        case .subjectNotice: 3
        case .homeLetter: 4
        case .woojeong: 6
        case .studentBoard: 7
        }
    }

    var label: String {
        switch self {
        case .studentNotice: "학생공지"
        case .subjectNotice: "교과공지"
        case .homeLetter: "가정통신문"
        case .woojeong: "우정학사공지"
        case .studentBoard: "학생게시판"
        }
    }

    /// 가정통신문만 추가 필터(bdField10=2)가 필요합니다.
    var extraParams: [(String, String)] { self == .homeLetter ? [("bdField10", "2")] : [] }
}

/// 면학감독 일정 코드 — 학년 필터를 적용하는 유일한 일정 종류.
let supervisionCode = "SG03010000"

/// 학사시스템 계정이 없거나 로그인 실패 — 화면이 연동 버튼을 띄웁니다.
let academicNeedsLogin = "학사시스템 연동 필요"

// MARK: - 네트워크

/// 알리미·학사일정·게시판 조회 — 안드로이드 HanaAcademicApi 와 같은 엔드포인트·파라미터.
enum AcademicAPI {
    /// 포털 버전에 따라 날짜 형식·필터 지원이 달라, 되는 조합을 찾을 때까지 차례로 시도하고 성공한 것을 기억합니다.
    struct Variant: Equatable { let datePattern: String; let extra: [String] }

    static let variants: [Variant] = {
        var v: [Variant] = []
        let patterns = ["yyyy-MM-dd", "yyyy.MM.dd", "yyyyMMdd"]
        patterns.forEach { v.append(Variant(datePattern: $0, extra: [])) }
        patterns.forEach { v.append(Variant(datePattern: $0, extra: ["mainYn", "Y"])) }
        patterns.forEach { v.append(Variant(datePattern: $0, extra: ["stdYn", "Y"])) }
        return v
    }()
    private static let mainExtra = ["mainYn", "Y"]

    private static func format(_ d: LocalDate, _ pattern: String) -> String {
        switch pattern {
        case "yyyy.MM.dd": String(format: "%04d.%02d.%02d", d.year, d.month, d.day)
        case "yyyyMMdd": String(format: "%04d%02d%02d", d.year, d.month, d.day)
        default: d.iso
        }
    }

    // MARK: 알리미

    static func fetchAlim() async throws -> [HanaAlim] {
        let json = try await PortalClient.shared.authenticatedJSON(
            "/main/alim/alim-target-list.json",
            params: [("pageSize", "7"), ("alimManYn", "Y"), ("filterReserve", "Y")],
            isValid: { ($0["paging"] as? [String: Any])?["result"] != nil })
        let rows = json.dict("paging")?.array("result") ?? []
        return rows.compactMap { row in
            guard let id = row.str("att_idx") else { return nil }
            return HanaAlim(
                id: id,
                content: HTML.decode(row.str("ait_cont")),
                writer: row.str("send_mem_name") ?? row.str("writer"),
                date: row.str("display_inputdate") ?? row.str("inputdate"),
                read: ["readYn", "ait_read_yn", "ait_vyn"].contains { row.str($0) == "Y" },
                aimIdx: row.str("aim_idx"))
        }
    }

    /// 알리미 읽음을 포털에도 — 번호는 att_idx. 실패 사유(성공이면 nil).
    static func markAlimRead(_ ids: [String]) async -> String? {
        guard !ids.isEmpty else { return nil }
        return await alimReadRequest("/main/alim/alim-target-view.json", ids.map { ("aimIdxs", $0) })
    }

    /// 알리미 모두 읽음 — 포털의 '전체 읽음'과 같은 요청.
    static func markAllAlimRead() async -> String? { await alimReadRequest("/main/alim/alim-target-view-all.json", []) }

    private static func alimReadRequest(_ path: String, _ params: [(String, String)]) async -> String? {
        do {
            let json = try await PortalClient.shared.authenticatedJSON(
                path, params: params, referer: PortalClient.base + "/main/mypage_alert/received.do",
                isValid: { $0.str("result") == "success" })
            if json.str("result") == "success" { return nil }
            return json.str("resMsg") ?? json.str("result") ?? "응답 없음"
        } catch {
            return error.localizedDescription
        }
    }

    // MARK: 학사일정

    /// [start]~[end] 를 하루씩(네 개씩 함께) 질의해 각 행에 그 날짜를 붙입니다. 계정 문제는 바로 멈춥니다(계정 잠금 방지).
    /// 받은 일정과 조회가 실패한 날 — 실패한 날은 일정이 없는 날과 구별해야 받아 둔 그날 일정을 지우지 않습니다.
    struct ScheduleFetch { var entries: [HanaScheduleEntry]; var failedDays: Set<LocalDate> }

    static func fetchSchedule(start: LocalDate, end: LocalDate) async throws -> ScheduleFetch {
        let rememberedBefore = ScheduleVariantStore.get()
        let first = try await fetchRange(start: start, end: end)
        // 기억한 형식이 범위 내내 빈 응답만 주면 기억을 지우고 처음부터 다시 찾습니다.
        if first.entries.isEmpty && rememberedBefore != nil {
            ScheduleVariantStore.clear()
            return try await fetchRange(start: start, end: end)
        }
        return first
    }

    private static func fetchRange(start: LocalDate, end: LocalDate) async throws -> ScheduleFetch {
        var days: [LocalDate] = []
        var d = start
        while d <= end { days.append(d); d = d.adding(days: 1) }
        var results: [Int: Result<[HanaScheduleEntry], Error>] = [:]
        // 네 개씩 함께.
        for chunk in stride(from: 0, to: days.count, by: 4) {
            try await withThrowingTaskGroup(of: (Int, Result<[HanaScheduleEntry], Error>).self) { group in
                for i in chunk..<min(chunk + 4, days.count) {
                    let date = days[i]
                    group.addTask {
                        do { return (i, .success(try await fetchDay(date))) } catch { return (i, .failure(error)) }
                    }
                }
                for try await (i, r) in group { results[i] = r }
            }
            for (_, r) in results {
                if case .failure(let e) = r, let p = e as? PortalError, p.needsAccount { throw p }
                if case .failure(let e) = r, e is CancellationError { throw e }
            }
        }
        let ordered = days.indices.compactMap { results[$0] }
        if !ordered.contains(where: { if case .success = $0 { true } else { false } }) {
            if let firstError = ordered.compactMap({ if case .failure(let e) = $0 { e } else { nil } }).first { throw firstError }
        }
        let failed = Set(days.indices.filter { if case .failure = results[$0] { true } else { false } }.map { days[$0] })
        return ScheduleFetch(entries: ordered.flatMap { (try? $0.get()) ?? [] }, failedDays: failed)
    }

    static func isExamName(_ name: String) -> Bool {
        let c = name.replacingOccurrences(of: " ", with: "")
        return c.contains("중간고사") || c.contains("기말고사")
    }

    private static let rowDateKeys = ["sch_date", "sch_day", "sch_st_day", "sch_start_day", "sch_sdate", "sch_st_date", "st_day", "start_day",
                                      "sdate", "st_date", "sch_ymd", "ymd", "date"]

    private static func rowDate(_ row: [String: Any]) -> LocalDate? {
        for key in rowDateKeys {
            guard let raw = row.str(key) else { continue }
            let digits = raw.filter(\.isNumber)
            if digits.count >= 8, let y = Int(digits.prefix(4)), let m = Int(digits.dropFirst(4).prefix(2)), let dd = Int(digits.dropFirst(6).prefix(2)),
               (1...12).contains(m), (1...31).contains(dd) {
                return LocalDate(year: y, month: m, day: dd)
            }
        }
        return nil
    }

    /// 앞으로 두 달쯤의 정기고사(중간·기말)만 — 범위로 한 번 묻고, 날짜를 못 읽으면 시험이 든 주만 하루씩 되묻습니다.
    static func fetchUpcomingExams(start: LocalDate, end: LocalDate) async throws -> [HanaScheduleEntry] {
        let variant = ScheduleVariantStore.get().map { variants[$0] } ?? variants[0]
        guard let rows = try await requestRows(start: start, end: end, variant: variant) else { return [] }
        let examRows = rows.filter { isExamName(HTML.decode($0.str("sch_nm"))) }
        if examRows.isEmpty { return [] }
        let dated: [HanaScheduleEntry] = examRows.compactMap { row in
            guard let date = rowDate(row), date >= start, date <= end else { return nil }
            return HanaScheduleEntry(code: row.str("sch_cd") ?? "", name: HTML.decode(row.str("sch_nm")), place: row.str("slp_nm"),
                                     startTime: row.str("st_time"), endTime: row.str("ed_time"),
                                     allDay: ["Y", "1", "TRUE"].contains(row.str("all_day")?.uppercased() ?? ""), date: date)
        }
        if !dated.isEmpty { return dedupe(dated) }
        var found: [HanaScheduleEntry] = []
        var weekStart = start
        while weekStart <= end {
            let weekEnd = min(weekStart.adding(days: 6), end)
            let weekRows = try? await requestRows(start: weekStart, end: weekEnd, variant: variant)
            if weekRows?.contains(where: { isExamName(HTML.decode($0.str("sch_nm"))) }) == true {
                var day = weekStart
                while day <= weekEnd {
                    if let r = try? await requestDay(day, variant: variant) { found += r.entries.filter { isExamName($0.name) } }
                    day = day.adding(days: 1)
                }
            }
            weekStart = weekStart.adding(days: 7)
        }
        return dedupe(found)
    }

    private static func dedupe(_ list: [HanaScheduleEntry]) -> [HanaScheduleEntry] {
        var seen = Set<String>()
        return list.filter { seen.insert("\($0.date.iso)|\($0.name)").inserted }
    }

    private static func params(_ start: String, _ end: String, _ variant: Variant) -> [(String, String)] {
        var p = [("pageSize", "1000"), ("schStartDay", start), ("schEndDay", end)]
        if variant.extra.count == 2 { p.append((variant.extra[0], variant.extra[1])) }
        return p
    }

    private static func list(_ json: [String: Any]) -> [[String: Any]]? {
        json.array("itemList") ?? json.array("list") ?? json.dict("paging")?.array("result")
    }

    private static func requestRows(start: LocalDate, end: LocalDate, variant: Variant) async throws -> [[String: Any]]? {
        let json = try await PortalClient.shared.authenticatedJSON("/main/schedule/hana-manage-data.json",
                                                                    params: params(format(start, variant.datePattern), format(end, variant.datePattern), variant))
        return list(json)
    }

    private struct DayResult { let entries: [HanaScheduleEntry]; let recognized: Bool }

    /// 하루치 — 기억한 형식이 있으면 먼저, 없거나 실패하면 형식을 차례로 시도해 **비어 있지 않은** 목록을 주는 첫 형식을 기억합니다.
    private static func fetchDay(_ date: LocalDate) async throws -> [HanaScheduleEntry] {
        if let remembered = ScheduleVariantStore.get() {
            let variant = variants[remembered]
            if let r = try? await requestDay(date, variant: variant), r.recognized {
                return await mergeMissingSupervision(date, r.entries, variant)
            }
        }
        var firstError: Error?
        var firstValid: (DayResult, Int)?
        for (index, variant) in variants.enumerated() {
            let r: DayResult
            do { r = try await requestDay(date, variant: variant) } catch {
                if let p = error as? PortalError, p.needsAccount { throw p }
                if firstError == nil { firstError = error }
                continue
            }
            if r.recognized && firstValid == nil { firstValid = (r, index) }
            if !r.entries.isEmpty {
                ScheduleVariantStore.set(index)
                return await mergeMissingSupervision(date, r.entries, variant)
            }
        }
        if let (valid, index) = firstValid { return await mergeMissingSupervision(date, valid.entries, variants[index]) }
        if let firstError { throw firstError }
        return []
    }

    /// 그 날 면학감독 행이 없으면 같은 날짜 형식의 mainYn 변형을 하루 한 번 더 조회해 감독 행만 병합합니다.
    private static func mergeMissingSupervision(_ date: LocalDate, _ entries: [HanaScheduleEntry], _ variant: Variant) async -> [HanaScheduleEntry] {
        if entries.contains(where: { $0.code == supervisionCode }) || variant.extra == mainExtra { return entries }
        guard let main = variants.first(where: { $0.datePattern == variant.datePattern && $0.extra == mainExtra }),
              let r = try? await requestDay(date, variant: main) else { return entries }
        let sup = r.entries.filter { $0.code == supervisionCode }
        if sup.isEmpty { return entries }
        let existing = Set(entries.map(Supervision.stableKey))
        return Supervision.dedupe(entries + sup.filter { !existing.contains(Supervision.stableKey($0)) })
    }

    private static func requestDay(_ date: LocalDate, variant: Variant) async throws -> DayResult {
        let day = format(date, variant.datePattern)
        let json = try await PortalClient.shared.authenticatedJSON("/main/schedule/hana-manage-data.json", params: params(day, day, variant))
        guard let rows = list(json) else { return DayResult(entries: [], recognized: false) }
        let entries: [HanaScheduleEntry] = rows.compactMap { row in
            let code = row.str("sch_cd") ?? ""
            let name = HTML.decode(row.str("sch_nm"))
            if name.isEmpty && code != supervisionCode { return nil }
            let teacher = HTML.decode(row.str("in_mem_name") ?? row.str("tc_mem_name"))
            return HanaScheduleEntry(code: code, name: name, place: row.str("slp_nm"), startTime: row.str("st_time"), endTime: row.str("ed_time"),
                                     allDay: ["Y", "1", "TRUE"].contains(row.str("all_day")?.uppercased() ?? ""), date: date,
                                     grade: row.str("smm_class"), teacher: teacher.isEmpty ? nil : teacher, mainYn: variant.extra == mainExtra)
        }
        return DayResult(entries: Supervision.dedupe(entries), recognized: true)
    }

    // MARK: 게시판

    static func fetchBoard(_ category: BoardCategory) async throws -> [HanaBoardPost] {
        var p = [("pageSize", "10"), ("bmtIdx", "\(category.bmtIdx)"), ("bmtIdx[]", "\(category.bmtIdx)")]
        p += category.extraParams
        let json = try await PortalClient.shared.authenticatedJSON("/main/board/board_main_list.json", params: p, isValid: { $0["noticeList"] != nil })
        return (json.array("noticeList") ?? []).map { row in
            let bmt = row.int("bmt_idx") ?? category.bmtIdx
            let bd = row.int("bd_idx") ?? -1
            return HanaBoardPost(
                bmtIdx: bmt, bdIdx: bd,
                title: HTML.decode(row.str("bd_title")),
                writer: row.str("bd_writer") ?? row.str("in_mem_name"),
                date: row.str("inputdate"),
                isNew: row.str("new_yn") == "Y",
                grades: (row.str("bd_title_header") ?? "").split(separator: "|").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty },
                url: normalizeURL(row.str("bd_url"), bmt, bd))
        }
    }

    private static func normalizeURL(_ raw: String?, _ bmt: Int, _ bd: Int) -> String {
        let base = PortalClient.base
        guard let raw, !raw.isEmpty else { return "\(base)/main/board/\(bmt)/\(bd)/board_view.do" }
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") { return raw }
        return raw.hasPrefix("/") ? base + raw : base + "/" + raw
    }
}

/// 어떤 날짜 형식·필터 조합이 먹혔는지 — 변형 수를 벗어난 값은 무시합니다.
enum ScheduleVariantStore {
    private static let key = "hana_schedule.variant"
    static func get() -> Int? {
        let v = SharedContainer.defaults.object(forKey: key) as? Int
        return v.flatMap { (0..<AcademicAPI.variants.count).contains($0) ? $0 : nil }
    }
    static func set(_ i: Int) { SharedContainer.defaults.set(i, forKey: key) }
    static func clear() { SharedContainer.defaults.removeObject(forKey: key) }
}

// MARK: - 캐시

/// 알리미 10분 · 게시판 10분 · 학사일정 1일 캐시 — 네트워크가 실패하면 만료된 캐시라도 돌려줍니다.
enum AcademicRepository {
    private static let ttlAlim: TimeInterval = 10 * 60
    private static let ttlBoard: TimeInterval = 10 * 60
    private static let ttlSchedule: TimeInterval = 24 * 3600
    private static let ttlExams: TimeInterval = 12 * 3600

    static func alim(force: Bool = false) async throws -> [HanaAlim] {
        try await cached("academic_alim", ttl: ttlAlim, force: force) { try await AcademicAPI.fetchAlim() }
    }

    static func board(_ category: BoardCategory, force: Bool = false) async throws -> [HanaBoardPost] {
        try await cached("academic_board_\(category.bmtIdx)", ttl: ttlBoard, force: force) { try await AcademicAPI.fetchBoard(category) }
    }

    /// 받아 둔 게시판 목록만(네트워크 없이) — 한 번도 안 받았으면 nil.
    static func cachedBoard(_ category: BoardCategory) -> [HanaBoardPost]? {
        JSONCache.read([HanaBoardPost].self, "academic_board_\(category.bmtIdx)")?.value
    }

    static func cachedAlim() -> [HanaAlim] { JSONCache.read([HanaAlim].self, "academic_alim")?.value ?? [] }

    /// 학사일정 — 빈 응답으로 기존 캐시를 덮어쓰지 않습니다. 로그인 전이면 묻지 않고 바로 실패.
    /// 어느 경로로 돌려주든(새로 받음·받아 둔 것·실패 후 받아 둔 것) 따로 받아 둔 정기고사를 덧붙입니다 —
    /// 예전엔 새로 받은 경우에만 덧붙여, 받기가 실패하거나 빈 응답이면 지난주 4주치만 돌아가 홈이 '다가오는 시험 없음'이 됐습니다.
    static func schedule(force: Bool = false) async throws -> [HanaScheduleEntry] {
        guard Credentials.hasAny else { throw PortalError.missingCredentials }
        let today = LocalDate.today()
        let name = "academic_schedule_4w"
        let stored = JSONCache.read([HanaScheduleEntry].self, name)
        if !force, let stored, JSONCache.isFresh(stored.at, ttl: ttlSchedule) {
            return withExams(stored.value, cachedExams())
        }
        let fetched: AcademicAPI.ScheduleFetch
        do {
            // 어제 ~ 이번 주부터 4주째 일요일까지 하루씩 — 타임라인 '주' 배율이 4주를 봅니다.
            fetched = try await AcademicAPI.fetchSchedule(start: today.adding(days: -1), end: scheduleEnd(today))
        } catch {
            if let stored { return withExams(stored.value, cachedExams()) }
            throw error
        }
        let exams = (try? await upcomingExams(today: today, force: force)) ?? cachedExams()
        if fetched.entries.isEmpty { return withExams(stored?.value ?? [], exams) }
        let merged = withExams(fillFailedDays(fetched, stored: stored?.value ?? []), exams)
        // 하루라도 실패했으면 30분 뒤 다시 받게 — 하루 동안 구멍 난 채로 두지 않습니다.
        JSONCache.write(merged, name, at: fetched.failedDays.isEmpty ? Date() : Date().addingTimeInterval(-(ttlSchedule - 30 * 60)))
        return merged
    }

    /// 조회가 실패한 날은 받아 둔 그날 일정으로 메웁니다 — 예전엔 실패한 날이 '일정 없음'으로 저장돼, 조회 실패로
    /// 12/14~17 이 빠지면 기말고사 D-day 가 마지막 날(12/18)로 떴습니다.
    static func fillFailedDays(_ fetched: AcademicAPI.ScheduleFetch, stored: [HanaScheduleEntry]) -> [HanaScheduleEntry] {
        fetched.entries + stored.filter { fetched.failedDays.contains($0.date) }
    }

    /// 상세 학사일정을 받는 끝 날 — 이번 주 월요일부터 4주째 일요일.
    static func scheduleEnd(_ today: LocalDate) -> LocalDate { today.monday.adding(days: 27) }

    /// [list] 에 없는 정기고사만 덧붙입니다(같은 날·같은 이름은 하나).
    static func withExams(_ list: [HanaScheduleEntry], _ exams: [HanaScheduleEntry]) -> [HanaScheduleEntry] {
        list + exams.filter { e in !list.contains { $0.date == e.date && $0.name == e.name } }
    }

    private static func cachedExams() -> [HanaScheduleEntry] { JSONCache.read([HanaScheduleEntry].self, "academic_exams")?.value ?? [] }

    /// 오늘 ~ 75일 뒤의 정기고사 — 12시간 캐시. 4주 상세 범위 안도 함께 찾습니다: 상세 조회가 하루라도 비거나 실패하면
    /// 그날 시험이 빠진 채 저장돼, 4주 범위 밖만 찾던 예전엔 시험 주간이 코앞인데 D-day 가 사라졌습니다.
    private static func upcomingExams(today: LocalDate, force: Bool) async throws -> [HanaScheduleEntry] {
        let name = "academic_exams"
        if !force, let stored = JSONCache.read([HanaScheduleEntry].self, name), JSONCache.isFresh(stored.at, ttl: ttlExams) { return stored.value }
        let stored = JSONCache.read([HanaScheduleEntry].self, name)
        // 새로 고침 중 실패하거나(취소·세션) 잠깐 빈 응답이 와도 받아 둔 시험을 버리지 않습니다 — 버리면 홈이 '다가오는 시험 없음'이 됐습니다.
        let fresh: [HanaScheduleEntry]
        do { fresh = try await AcademicAPI.fetchUpcomingExams(start: today, end: today.adding(days: 75)) }
        catch { if let stored { return stored.value }; throw error }
        if fresh.isEmpty, let previous = stored?.value, previous.contains(where: { $0.date >= today }) { return previous }
        JSONCache.write(fresh, name)
        return fresh
    }

    /// 네트워크 없이 받아 둔 학사일정만 — 위젯처럼 기다릴 수 없는 곳. 따로 받아 둔 정기고사도 덧붙입니다.
    static func cachedSchedule() -> [HanaScheduleEntry] {
        withExams(JSONCache.read([HanaScheduleEntry].self, "academic_schedule_4w")?.value ?? [], cachedExams())
    }

    /// 새 응답이 비었을 때 이전의 비어 있지 않은 캐시를 덮어쓰지 않습니다 — 세션 문제로 잠깐 빈 목록이 와도 '없음'으로 굳지 않게.
    private static func cached<T: Codable>(_ name: String, ttl: TimeInterval, force: Bool, fetch: () async throws -> [T]) async throws -> [T] {
        let stored = JSONCache.read([T].self, name)
        if !force, let stored, JSONCache.isFresh(stored.at, ttl: ttl) { return stored.value }
        do {
            let fresh = try await fetch()
            if fresh.isEmpty, let previous = stored?.value, !previous.isEmpty { return previous }
            JSONCache.write(fresh, name)
            return fresh
        } catch {
            if let stored { return stored.value }
            throw error
        }
    }
}

// MARK: - 읽음 기록

/// 앱에서 읽은 알리미(att_idx) — 서버 readYn 이 안 내려와도 읽음이 유지되게.
enum AlimReadStore {
    private static let key = "alim_read.ids"
    static func read() -> Set<String> { Set(SharedContainer.defaults.stringArray(forKey: key) ?? []) }
    static func markRead(_ ids: [String]) {
        guard !ids.isEmpty else { return }
        var list = SharedContainer.defaults.stringArray(forKey: key) ?? []
        for id in ids where !list.contains(id) { list.append(id) }
        SharedContainer.defaults.set(Array(list.suffix(1000)), forKey: key)
    }
}

/// 이미 알림을 띄운 알리미 — 새 글에만 알림이 가게.
enum AlimSeenStore {
    private static let key = "alim_seen.ids"
    static func seen() -> Set<String> { Set(SharedContainer.defaults.stringArray(forKey: key) ?? []) }
    static func markSeen(_ ids: [String]) {
        var list = SharedContainer.defaults.stringArray(forKey: key) ?? []
        for id in ids where !list.contains(id) { list.append(id) }
        SharedContainer.defaults.set(Array(list.suffix(500)), forKey: key)
    }
}

/// 게시글을 앱에서 읽었는지 — 'N' 표시와 안 읽은 수는 이걸 기준으로. 게시판마다 처음 한 번은 포털이 새 글로 표시하지 않는
/// 글을 읽은 것으로 심어, 설치 전 글이 전부 안 읽음으로 쏟아지지 않게 합니다.
@Observable
final class BoardReadStore {
    static let shared = BoardReadStore()
    private let key = "board_read.keys"
    /// 바뀔 때마다 1씩 — 화면이 이 값을 읽으면 따라 그립니다.
    private(set) var revision = 0
    @ObservationIgnored private var keys: Set<String>
    @ObservationIgnored private var order: [String]

    private init() {
        order = SharedContainer.defaults.stringArray(forKey: "board_read.keys") ?? []
        keys = Set(order)
    }

    static func key(_ post: HanaBoardPost) -> String { "\(post.bmtIdx):\(post.bdIdx)" }

    func isRead(_ post: HanaBoardPost) -> Bool { _ = revision; return keys.contains(Self.key(post)) }
    func unreadCount(_ posts: [HanaBoardPost]) -> Int { posts.filter { !isRead($0) }.count }

    func markRead(_ posts: [HanaBoardPost]) {
        let before = keys.count
        for p in posts { let k = Self.key(p); if keys.insert(k).inserted { order.append(k) } }
        guard keys.count != before else { return }
        if order.count > 3000 { order = Array(order.suffix(3000)); keys = Set(order) }
        SharedContainer.defaults.set(order, forKey: key)
        revision += 1
    }

    func seedIfNeeded(_ category: BoardCategory, _ posts: [HanaBoardPost]) {
        let seeded = "board_read.seeded_\(category.bmtIdx)"
        guard !posts.isEmpty, !SharedContainer.defaults.bool(forKey: seeded) else { return }
        markRead(posts.filter { !$0.isNew })
        SharedContainer.defaults.set(true, forKey: seeded)
    }
}

// MARK: - 시험 D-day · 면학감독

/// 중간·기말고사 중 오늘 이후 가장 가까운 것 — '다짐고사' 같은 다른 고사는 대상이 아닙니다.
func examDday(_ entries: [HanaScheduleEntry], today: LocalDate) -> ExamDday? {
    entries.filter { AcademicAPI.isExamName($0.name) }
        .map { ExamDday(name: $0.name, date: $0.date, days: LocalDate.days(from: today, to: $0.date)) }
        .filter { $0.days >= 0 }
        .min { $0.days < $1.days }
}

enum Supervision {
    private struct Token { var grade: Int?; var name: String }

    /// 같은 행을 두 변형(상세 + mainYn)에서 중복 병합하지 않기 위한 키.
    static func stableKey(_ e: HanaScheduleEntry) -> String {
        [e.date.iso, e.code, e.name, e.place ?? "", e.startTime ?? "", e.endTime ?? "", "\(e.allDay)", e.grade ?? "", e.teacher ?? ""].joined(separator: "|")
    }

    /// `smm_class` 는 "1"/"01"/"1학년" 처럼 올 수 있어 앞자리 숫자만.
    static func parsedGrade(_ g: String?) -> Int? {
        guard let g else { return nil }
        return Int(String(g.trimmingCharacters(in: .whitespaces).prefix(while: \.isNumber)))
    }

    private static func gradePrefix(_ token: String) -> (Int, String)? {
        guard let m = token.range(of: #"^\s*([1-3])\s*[.)]"#, options: .regularExpression) else { return nil }
        let grade = Int(token[m].filter(\.isNumber)) ?? 0
        return (grade, String(token[m.upperBound...]).trimmingCharacters(in: .whitespaces))
    }

    private static func tokens(_ raw: String?) -> [Token] {
        (raw ?? "").replacingOccurrences(of: "선생님", with: " ")
            .components(separatedBy: CharacterSet(charactersIn: " ,\n\t")).compactMap { part in
                let t = part.trimmingCharacters(in: .whitespaces)
                if t.isEmpty { return nil }
                if let (g, name) = gradePrefix(t) { return name.isEmpty ? nil : Token(grade: g, name: name) }
                return Token(grade: nil, name: t)
            }
    }

    private static func entryTokens(_ e: HanaScheduleEntry) -> [Token] {
        let source = (e.teacher?.trimmingCharacters(in: .whitespaces).isEmpty == false) ? e.teacher : e.name
        let t = tokens(source)
        if t.contains(where: { $0.grade != nil }) { return t }
        guard let rowGrade = parsedGrade(e.grade) else { return t }
        return t.map { Token(grade: rowGrade, name: $0.name) }
    }

    private static func distinct(_ a: [String]) -> [String] { var s = Set<String>(); return a.filter { s.insert($0).inserted } }

    private static func rawText(_ e: HanaScheduleEntry) -> String? {
        var names = entryTokens(e).map(\.name)
        if names.isEmpty {
            let fallback = ((e.teacher?.isEmpty == false ? e.teacher : e.name) ?? "").replacingOccurrences(of: "선생님", with: " ")
                .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
            if !fallback.isEmpty { names = [fallback] }
        }
        let s = distinct(names).joined(separator: " ")
        return s.isEmpty ? nil : s
    }

    private static func displayNames(_ e: HanaScheduleEntry, _ selected: Int) -> [String]? {
        let t = entryTokens(e)
        if t.isEmpty { return nil }
        let sel = distinct(t.filter { $0.grade == selected }.map(\.name))
        if !sel.isEmpty { return sel }
        let unknown = distinct(t.filter { $0.grade == nil }.map(\.name))
        return unknown.isEmpty ? nil : unknown
    }

    /// 화면에 보여 줄 교사명 — 학년 접두사와 '선생님' 꼬리를 뗍니다.
    static func displayTeacher(_ e: HanaScheduleEntry) -> String? {
        guard let t = e.teacher else { return nil }
        let s = t.replacingOccurrences(of: #"^\s*[1-3]\s*[.)]"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: "선생님", with: " ")
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
        return s.isEmpty ? nil : s
    }

    private static func clock(_ raw: String?) -> String? {
        guard let raw, let r = raw.range(of: #"(\d{1,2}):(\d{2})"#, options: .regularExpression) else { return nil }
        let p = raw[r].split(separator: ":")
        return String(format: "%02d:%@", Int(p[0]) ?? 0, String(p[1]))
    }

    /// 감독 타임 번호 — 일정명의 'N타임' 을 우선, 아니면 16:20 은 0타임, 19:00 은 1타임.
    static func slot(_ e: HanaScheduleEntry) -> Int? {
        if let r = e.name.range(of: #"([0-9])\s*타임"#, options: .regularExpression), let n = Int(e.name[r].prefix(1)) { return n }
        switch clock(e.startTime) {
        case "16:20": return 0
        case "19:00": return 1
        default: return nil
        }
    }

    private static func slotText(_ e: HanaScheduleEntry) -> String { slot(e).map(String.init) ?? clock(e.startTime) ?? e.startTime ?? "" }

    /// 감독 행 정리 — 완전 중복을 지우고, 같은 (날짜·타임·학년)에 담당이 여럿이면 상세 행을, 같은 출처면 뒤의 행을 남깁니다.
    static func dedupe(_ entries: [HanaScheduleEntry]) -> [HanaScheduleEntry] {
        var seen = Set<String>()
        let exact = entries.filter { e in
            e.code != supervisionCode || seen.insert([e.date.iso, slotText(e), parsedGrade(e.grade).map(String.init) ?? "", e.teacher ?? ""].joined(separator: "|")).inserted
        }
        var winner: [String: Int] = [:]
        for (i, e) in exact.enumerated() where e.code == supervisionCode {
            let key = [e.date.iso, slotText(e), parsedGrade(e.grade).map(String.init) ?? ""].joined(separator: "|")
            if let cur = winner[key] {
                let c = exact[cur]
                let wins = c.mainYn && !e.mainYn ? true : (!c.mainYn && e.mainYn ? false : i > cur)
                if wins { winner[key] = i }
            } else { winner[key] = i }
        }
        let keep = Set(winner.values)
        return exact.enumerated().filter { $0.element.code != supervisionCode || keep.contains($0.offset) }.map(\.element)
    }

    /// 선택 학년만 남긴 면학감독 — 학년을 못 읽는 행은 원문 그대로, 그날 감독이 전부 걸러지면 원문으로 되돌립니다.
    static func filterByGrade(_ entries: [HanaScheduleEntry], _ selected: Int) -> [HanaScheduleEntry] {
        var result: [HanaScheduleEntry] = []
        var byDate: [LocalDate: [HanaScheduleEntry]] = [:]
        var dates: [LocalDate] = []
        for e in entries { if byDate[e.date] == nil { dates.append(e.date) }; byDate[e.date, default: []].append(e) }
        for d in dates {
            let day = byDate[d]!
            if !day.contains(where: { $0.code == supervisionCode }) { result += day; continue }
            let filtered: [HanaScheduleEntry] = day.compactMap { e in
                guard e.code == supervisionCode else { return e }
                guard let names = displayNames(e, selected) else { return nil }
                var c = e; c.teacher = names.joined(separator: " "); c.grade = "\(selected)"; return c
            }
            if filtered.contains(where: { $0.code == supervisionCode }) { result += filtered }
            else { result += day.map { e in guard e.code == supervisionCode else { return e }; var c = e; c.teacher = rawText(e); c.grade = nil; return c } }
        }
        return result
    }

    /// 그 날 1타임 면학감독 담당 — 선택 학년을 우선, 못 읽으면 원문.
    static func weekday1Teachers(_ entries: [HanaScheduleEntry], date: LocalDate, grade: Int) -> [String] {
        let slots = entries.filter { $0.code == supervisionCode && $0.date == date && slot($0) == 1 }
        if slots.isEmpty { return [] }
        let preferred = distinct(slots.flatMap { displayNames($0, grade) ?? [] })
        if !preferred.isEmpty { return preferred }
        return distinct(slots.compactMap(rawText))
    }
}

// MARK: - 표시 도우미

enum ScheduleKind {
    static func color(_ code: String) -> Color {
        switch code {
        case "SG01010000": Color(argb: 0xFF387AFF) // 학사일정
        case "SG02010000": Color(argb: 0xFF00A6B4) // 하나일정
        case "SG01020000": Color(argb: 0xFFFF3B30) // 공휴일
        case supervisionCode: Color(argb: 0xFFA2845E) // 면학감독
        case "SG02020000": Color(argb: 0xFF8E8E93) // 시설사용
        default: Color(argb: 0xFF5856D6)
        }
    }

    static func label(_ code: String) -> String {
        switch code {
        case "SG01010000": "학사일정"
        case "SG02010000": "하나일정"
        case "SG01020000": "공휴일"
        case supervisionCode: "면학감독"
        case "SG02020000": "시설사용"
        default: "일정"
        }
    }

    /// 면학감독은 일정명 대신 담당 교사를.
    static func title(_ e: HanaScheduleEntry) -> String { e.code == supervisionCode ? (Supervision.displayTeacher(e) ?? e.name) : e.name }

    /// st_time/ed_time 은 "2026-09-17 09:00" 처럼 날짜가 앞에 붙어 올 수 있어 시각만.
    static func time(_ e: HanaScheduleEntry) -> String? {
        func clock(_ s: String?) -> String? { s.map { $0.contains(" ") ? String($0.split(separator: " ").last!) : $0 } }
        let s = clock(e.startTime), t = clock(e.endTime)
        if let s, let t { return "\(s)~\(t)" }
        return s ?? t
    }
}

/// 포털 알리미 화면에서 한 건을 미리 골라 여는 주소.
func alimURL(_ idx: String) -> String { "\(PortalClient.base)/main/mypage_alert/received.do?aimIdx=\(idx)" }

/// "2026-10-19 10:57:44.0" → 올해면 "10.19 10:57", 지난해면 "2025.10.19". 못 읽으면 그대로.
func prettyPortalDate(_ raw: String?) -> String? {
    guard let raw, !raw.trimmingCharacters(in: .whitespaces).isEmpty else { return raw }
    let re = try! NSRegularExpression(pattern: #"^(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?"#)
    let t = raw.trimmingCharacters(in: .whitespaces)
    guard let m = re.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)) else { return raw }
    func g(_ i: Int) -> String? { Range(m.range(at: i), in: t).map { String(t[$0]) } }
    guard let y = g(1), let mo = g(2).flatMap({ Int($0) }), let d = g(3).flatMap({ Int($0) }) else { return raw }
    let thisYear = Int(y) == LocalDate.today().year
    let date = thisYear ? "\(mo).\(d)" : "\(y).\(mo).\(d)"
    if let h = g(4).flatMap({ Int($0) }), let mi = g(5), thisYear { return "\(date) \(h):\(mi)" }
    return date
}
