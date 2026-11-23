import Foundation

/*
 * 신청·내역 — 안드로이드 HanaApplyClient · PortalHistory · PortalApply 의 포털 요청.
 * 목록은 조회만, 신청·수정·취소는 화면의 확인 창에서 한 번 더 눌러야만 보냅니다.
 */

// MARK: - 공통

/// 포털 목록 JSON 공통 — 폼 POST, `result` 확인, `paging.result` 읽기.
enum PortalLists {
    static func post(_ path: String, _ params: [(String, String)], referer: String) async throws -> [String: Any] {
        let json = try await PortalClient.shared.authenticatedJSON(path, params: params, referer: PortalClient.base + referer)
        guard json.str("result") == "success" else { throw PortalError.rejected(json.str("resMsg") ?? "포털이 목록을 주지 않았습니다") }
        return json
    }

    static func page<T>(_ json: [String: Any], _ map: ([String: Any]) -> T?) -> (rows: [T], total: Int) {
        let paging = json.dict("paging")
        let rows = (paging?.array("result") ?? []).compactMap(map)
        return (rows, paging?.int("rowTotal") ?? rows.count)
    }

    private static let stamp: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = Seoul.zone
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f
    }()

    /// "2026-10-11 13:39:33.0" → 분까지.
    static func dateTime(_ text: String?) -> Date? { text.flatMap { stamp.date(from: String($0.prefix(16))) } }
    static func stampText(_ d: Date) -> String { stamp.string(from: d) }

    static func time(_ text: String?) -> Int? {
        guard let t = text?.prefix(5), t.count == 5, t[t.index(t.startIndex, offsetBy: 2)] == ":" else { return nil }
        let p = t.split(separator: ":").compactMap { Int($0) }
        return p.count == 2 ? p[0] * 60 + p[1] : nil
    }
}

/// 포털에 신청·취소를 **한 번만** 보냅니다. 빈 응답·시간 초과에도 다시 보내지 않습니다 — 서버가 이미 처리했을 수 있어
/// 두 번 신청될 수 있기 때문입니다(로그인 페이지가 오면 처리되지 않은 것이라 다시 로그인 후 한 번 더).
enum PortalWrite {
    static func send(_ path: String, _ params: [(String, String)], referer: String, failText: String) async throws -> String {
        let r: RawResponse
        do {
            r = try await PortalClient.shared.authenticatedRaw(path, params: params, method: "POST", referer: PortalClient.base + referer, timeout: 30)
        } catch PortalError.timeout {
            throw PortalError.timeout("서버가 답하지 않았습니다 — 실제로 처리됐을 수 있으니 내역에서 확인해 주세요")
        }
        guard let json = try? JSONSerialization.jsonObject(with: Data(r.body.utf8)) as? [String: Any] else {
            throw PortalError.rejected(r.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                       ? "응답이 비어 있었습니다 — 처리됐을 수 있으니 내역에서 확인해 주세요"
                                       : "응답을 읽지 못했습니다 (HTTP \(r.code)) — 내역에서 확인해 주세요")
        }
        let message = json.str("resMsg")
        guard json.str("result") == "success" else { throw PortalError.rejected(message ?? failText) }
        return message ?? "완료되었습니다"
    }
}

func applyErrorText(_ e: Error) -> String {
    if let p = e as? PortalError, p == .missingCredentials { return "하이하나 계정을 먼저 등록해 주세요" }
    return e.localizedDescription.components(separatedBy: "\n").first ?? "실패했습니다"
}

func clock(_ minutes: Int) -> String { String(format: "%02d:%02d", minutes / 60, minutes % 60) }

// MARK: - 교과교실 내역 (카드 안 목록)

struct HanaApplyRow: Identifiable {
    let id = UUID()
    let slot: String?
    let place: String?
    let status: String?
    let appliedDate: String?
}

enum ClassroomHistoryAPI {
    /// 최근 7일~앞으로 14일 — 포털 메인 페이지 JS 가 쓰는 목록.
    static func fetch() async throws -> [HanaApplyRow] {
        let today = LocalDate.today()
        let json = try await PortalClient.shared.authenticatedJSON("/main/classroom/apply-list.json", params: [
            ("cp", "1"), ("searchSDate", today.adding(days: -7).iso), ("searchEDate", today.adding(days: 14).iso), ("listType", "list"),
        ])
        let list = json.dict("paging")?.array("result") ?? json.array("list") ?? json.array("itemList") ?? json.array("result") ?? []
        func first(_ o: [String: Any], _ keys: String...) -> String? { keys.lazy.compactMap { o.str($0) }.first }
        return list.map { o in
            let place = first(o, "room_nm", "slp_nm", "cc_place_nm", "place_nm", "lib_nm", "c_place_cd_name")
            let seat = [first(o, "clr_area_nm"), first(o, "srt_cont", "seat")].compactMap { $0 }.joined(separator: " ")
            let joined = [place, seat.isEmpty ? nil : seat].compactMap { $0 }.joined(separator: " ")
            return HanaApplyRow(slot: first(o, "st_nm", "stNm", "slot_nm", "time_nm", "st_time"),
                                place: joined.isEmpty ? nil : joined,
                                status: first(o, "cc_cd_name", "status_nm", "stat_nm", "appr_nm", "req_state", "state_nm"),
                                appliedDate: first(o, "crt_dt", "reg_dt", "inputdate", "req_dt", "apply_dt"))
        }
    }
}

// MARK: - 면학실·도서관 내역

/// 두 포털 내역 페이지가 같은 목록 JSON 을 장소 코드만 바꿔 부릅니다. 학생 번호는 서버가 세션으로 알아냅니다.
enum SeatHistoryPlace {
    case studyRoom, library
    var label: String { self == .library ? "도서관" : "면학실" }
    var referer: String { self == .library ? "/main/library/library-history.do" : "/main/studyroom/study-history.do" }
    var placeCodes: [String] { self == .library ? ["8001"] : ["8002", "8003", "8004"] }
    /// 면학실은 포털 페이지처럼 지정석을 뺍니다.
    var excludeAssigned: Bool { self == .studyRoom }
    init(_ s: ApplyService) { self = s == .library ? .library : .studyRoom }
}

struct SeatHistoryRow: Identifiable {
    let id: Int
    let date: LocalDate?
    let slot: String
    let slotOrder: Int
    /// "6층 일반형E" / "2F" 처럼 장소와 구역.
    let area: String?
    let seat: String?
    /// 감독 선생님의 출석 확인 시각.
    let checkedAt: Date?
}

enum SeatHistoryAPI {
    static func fetch(_ place: SeatHistoryPlace, from: LocalDate, to: LocalDate, page: Int, pageSize: Int = 300) async throws -> (rows: [SeatHistoryRow], total: Int) {
        var params: [(String, String)] = [("cp", "\(page)"), ("pageSize", "\(pageSize)"), ("searchSDate", from.iso), ("searchEDate", to.iso), ("stIdSPAll", "N")]
        params += place.placeCodes.map { ("cPlaceCds", $0) }
        if place.excludeAssigned { params.append(("assignYn", "N")) }
        let json = try await PortalLists.post("/main/studyroom/study-only-req-list.json", params, referer: place.referer)
        return PortalLists.page(json) { o in
            SeatHistoryRow(
                id: o.int("sre_idx") ?? 0,
                date: o.str("sre_dt").flatMap { LocalDate(iso: String($0.prefix(10))) },
                slot: o.str("st_nm") ?? "타임",
                slotOrder: (o.int("stg_idx") ?? 0) * 100 + (o.int("st_idx") ?? 0),
                // 도서관은 장소 이름이 "도서관" 뿐이라 구역(2F)만, 면학실은 층 + 구역.
                area: [place == .library ? nil : o.str("c_place_cd_name"), o.str("clr_area_nm")].compactMap { $0 }.joined(separator: " ").nilIfEmpty,
                seat: o.str("srt_cont"),
                checkedAt: o.str("ca_chk_yn") == "Y" ? PortalLists.dateTime(o.str("cadatetime")) : nil)
        }
    }
}

extension String { var nilIfEmpty: String? { isEmpty ? nil : self } }

// MARK: - 외출외박 내역

struct OutingRow: Identifiable, Equatable {
    let osmNo: Int
    let stdMemNo: String
    let state: Int
    let stateLabel: String
    let plannedOut: Date?
    let plannedBack: Date?
    let actualOut: Date?
    let actualBack: Date?
    let duration: String?
    let reason: String?
    let appliedAt: Date?
    /// 단체 신청(선생님이 반·학년 전체로 넣은 것).
    let group: Bool
    var id: Int { osmNo }
}

struct OutingDetail: Equatable { let transport: String?; let approver: String?; let returnChecker: String? }

enum OutingFilter: String, CaseIterable {
    case all = "", applied = "2", approved = "4", rejected = "8", returned = "16", cancelled = "32"
    var label: String {
        switch self { case .all: "전체"; case .applied: "신청"; case .approved: "승인"; case .rejected: "반려"; case .returned: "복귀"; case .cancelled: "취소" }
    }
}

enum OutingAPI {
    private static let referer = "/main/outing/history.do"

    static func list(_ filter: OutingFilter, page: Int, pageSize: Int = 20) async throws -> (rows: [OutingRow], total: Int) {
        let json = try await PortalLists.post("/main/outing/outing_detail_list.json", [
            ("cp", "\(page)"), ("pageSize", "\(pageSize)"),
            ("searchSDate", ""), ("searchEDate", ""), ("searchSDate2", ""), ("searchEDate2", ""),
            ("addOrder", ""), ("authChkYn", "ALL"), ("osdState", filter.rawValue),
        ], referer: referer)
        return PortalLists.page(json) { o in
            OutingRow(osmNo: o.int("osm_no") ?? 0, stdMemNo: o.str("std_mem_no") ?? "", state: o.int("osd_state") ?? 0,
                      stateLabel: o.str("osd_state_str") ?? "",
                      plannedOut: PortalLists.dateTime(o.str("dep_exp_dt")), plannedBack: PortalLists.dateTime(o.str("ret_exp_dt")),
                      actualOut: PortalLists.dateTime(o.str("dep_dt")), actualBack: PortalLists.dateTime(o.str("ret_dt")),
                      duration: o.str("exp_date_str"), reason: o.str("out_rsn"), appliedAt: PortalLists.dateTime(o.str("inputdate")),
                      group: o.str("out_all_yn") == "Y")
        }
    }

    static func detail(_ row: OutingRow) async throws -> OutingDetail {
        let json = try await PortalLists.post("/main/outing/outing_detail.json", [("osm_no", "\(row.osmNo)"), ("std_mem_no", row.stdMemNo)], referer: referer)
        guard let vo = json.dict("resultVo") else { throw PortalError.rejected("상세를 받지 못했습니다") }
        return OutingDetail(transport: vo.str("trans_mtd"), approver: vo.str("app_tc_mem_name") ?? vo.str("acc_tc_mem_name"), returnChecker: vo.str("cfm_ret_mem_name"))
    }
}

// MARK: - 교과교실 신청

/*
 * 포털 apply.do 의 "신청" 창과 같은 순서 (오늘 날짜로만 신청).
 *   건물 cls-plg-list → 교실 cls-cr-list → 정보 cls-cr-info(승인 방식·교사·사전 동의) → 타임 cls-crtime-list
 *   신청 apply-insert.do · 취소 apply-cancel.do
 * 승인 방식: CA01 자동승인 · CA02 교사승인(교사 선택) · CA03 사전양식승인(교사 선택 + 주의사항 모두 동의).
 */
struct ClassroomBuildingOption: Hashable { let num: String; let name: String }
struct ClassroomRoomOption: Hashable { let idx: String; let name: String; let approval: String?; let approvalLabel: String? }
struct ClassroomTeacher: Hashable { let no: String; let name: String }

struct ClassroomRoomDetail {
    static let caAuto = "CA01000000", caTeacher = "CA02000000", caPreset = "CA03000000"
    let approval: String?
    let approvalLabel: String?
    let teachers: [ClassroomTeacher]
    let notice: String?
    let procedure: String?
    /// 사전 동의 항목 (cpc_idx, 내용).
    let checks: [(id: String, text: String)]
    var needsTeacher: Bool { approval == Self.caTeacher || approval == Self.caPreset }
    var needsAgreement: Bool { approval == Self.caPreset }
}

struct ClassroomSlotOption: Hashable {
    let id: String
    let name: String
    let start: Int?
    let end: Int?
    var label: String { [name, start.map(clock)].compactMap { $0 }.joined(separator: " ") }
}

struct MyClassroomApply: Identifiable {
    let crtIdx: String
    let room: String
    let slot: String
    let start: Int?
    let end: Int?
    let status: String?
    let reason: String?
    let cancellable: Bool
    var id: String { crtIdx }
}

enum ClassroomApplyAPI {
    private static let page = "/main/classroom/apply.do"

    private static func list(_ path: String, _ params: [(String, String)], referer: String = page) async throws -> [[String: Any]] {
        try await PortalLists.post(path, params, referer: referer).array("list") ?? []
    }

    static func buildings() async throws -> [ClassroomBuildingOption] {
        try await list("/main/classroom/cls-plg-list.json", [("stIdxFull", ""), ("schClassYn", "Y")])
            .compactMap { o in o.str("slg_num").map { ClassroomBuildingOption(num: $0, name: o.str("slg_nm") ?? $0) } }
    }

    static func rooms(_ building: String) async throws -> [ClassroomRoomOption] {
        try await list("/main/classroom/cls-cr-list.json", [("stIdxFull", ""), ("slgNum", building), ("schClassYn", "Y")])
            .compactMap { o in o.str("cr_idx").map { ClassroomRoomOption(idx: $0, name: o.str("slp_nm") ?? $0, approval: o.str("ca_cd"), approvalLabel: o.str("ca_cd_name")) } }
    }

    static func detail(_ room: String) async throws -> ClassroomRoomDetail {
        guard let item = try await PortalLists.post("/main/classroom/cls-cr-info.json", [("crIdx", room)], referer: page).dict("item") else {
            throw PortalError.rejected("교실 정보를 받지 못했습니다")
        }
        let preset = item.dict("preSetVo")
        let roomName = item.str("slp_nm") ?? ""
        func plain(_ text: String?) -> String? {
            guard let t = text?.replacingOccurrences(of: "{{선택 장소명}}", with: roomName) else { return nil }
            var s = t.replacingOccurrences(of: #"<br\s*/?>|</p>"#, with: "\n", options: [.regularExpression, .caseInsensitive])
            s = HTML.unescape(s.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)).trimmingCharacters(in: .whitespacesAndNewlines)
            return s.isEmpty ? nil : s
        }
        return ClassroomRoomDetail(
            approval: item.str("ca_cd"), approvalLabel: item.str("ca_cd_name"),
            teachers: (item.array("teacherList") ?? []).compactMap { t in t.str("crc_mem_no").map { ClassroomTeacher(no: $0, name: t.str("crc_mem_name") ?? $0) } },
            notice: plain(preset?.str("cps_info")), procedure: plain(preset?.str("cps_pro_cont")),
            checks: (preset?.array("checkList") ?? []).compactMap { c in c.str("cpc_idx").map { (id: $0, text: c.str("cpc_cont") ?? "") } })
    }

    static func slots(_ room: String) async throws -> [ClassroomSlotOption] {
        try await list("/main/classroom/cls-crtime-list.json", [("crIdx", room)])
            .filter { $0.int("stg_idx") != 8 }
            .compactMap { o in
                guard let g = o.str("stg_idx"), let i = o.str("st_idx") else { return nil }
                return ClassroomSlotOption(id: "\(g)_\(i)", name: o.str("st_nm") ?? "타임 \(i)", start: PortalLists.time(o.str("st_time")), end: PortalLists.time(o.str("ed_time")))
            }
    }

    /// 오늘 내 신청 — 포털 내역 목록(세션 기준 본인 것만)을 오늘 하루로.
    static func mine() async throws -> [MyClassroomApply] {
        let today = LocalDate.today()
        let now = Date().minutesOfDay
        let json = try await PortalLists.post("/main/classroom/apply-list.json", [
            ("cp", "1"), ("pageSize", "50"), ("searchSDate", today.iso), ("searchEDate", today.iso), ("stIdSPAll", "N"),
        ], referer: "/main/classroom/history.do")
        return (json.dict("paging")?.array("result") ?? []).filter { $0.str("myYn") != "N" }.compactMap { o in
            guard let id = o.str("crt_idx") else { return nil }
            let start = PortalLists.time(o.str("st_time"))
            // 포털과 같은 조건 — 내 신청이고, 신청·승인 상태이고, 타임 시작 전일 때만 취소.
            let cancellable = o.str("myYn") == "Y" && o.str("preYn") == "Y" && ["CC01000000", "CC02000000"].contains(o.str("cc_cd") ?? "") && (start.map { $0 > now } ?? false)
            return MyClassroomApply(crtIdx: id, room: o.str("slp_nm") ?? "교실", slot: o.str("st_nm") ?? "", start: start,
                                    end: PortalLists.time(o.str("ed_time")), status: o.str("cc_cd_name"), reason: o.str("crt_cont"), cancellable: cancellable)
        }
    }

    /// 신청 — 포털 폼을 직렬화한 순서 그대로. 교사는 교사·사전양식 승인 교실만, 동의 항목은 사전양식 승인 교실만.
    static func apply(building: ClassroomBuildingOption, room: ClassroomRoomOption, detail: ClassroomRoomDetail, slot: ClassroomSlotOption,
                      reason: String, teacher: ClassroomTeacher?, agreed: [String]) async throws -> String {
        var params: [(String, String)] = [("slg_num", building.num), ("cr_idx", room.idx), ("crt_cont", reason), ("stIdxFull2", slot.id)]
        if detail.needsTeacher, let teacher { params.append(("tc_mem_no", teacher.no)) }
        if detail.needsAgreement { params += agreed.map { ("pre_conf_yn", $0) } }
        return try await PortalWrite.send("/main/classroom/apply-insert.do", params, referer: page, failText: "신청 중 오류가 발생했습니다")
    }

    static func cancel(_ crtIdx: String) async throws -> String {
        try await PortalWrite.send("/main/classroom/apply-cancel.do", [("crt_idx", crtIdx)], referer: page, failText: "취소 중 오류가 발생했습니다")
    }
}

// MARK: - 외출외박 신청

struct OutingFormInfo { let notice: String?; let approverName: String?; let approverNo: String }

enum OutingTransport: String, CaseIterable { case car = "자가용", publicTransit = "대중교통", school = "학교(교사)차량", other = "기타" }

enum OutingApplyAPI {
    private static let page = "/main/outing/apply_inscr.do"
    private static let history = "/main/outing/history.do"

    /// 포털처럼 한글·영문·숫자·공백만.
    static func cleanReason(_ text: String) -> String {
        text.replacingOccurrences(of: #"[^가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9\s]"#, with: "", options: .regularExpression)
    }

    /// 안내문·승인 교사(JSON)와 폼이 그대로 보내는 숨은 승인 교사 번호(HTML).
    static func form() async throws -> OutingFormInfo {
        let client = PortalClient.shared
        let json = (try? await client.authenticatedText(page)).flatMap { try? JSONSerialization.jsonObject(with: Data($0.utf8)) as? [String: Any] }
        let html = (try? await client.authenticatedHTML(page)) ?? ""
        let hidden = PortalClient.firstMatch(#"name=["']acc_tc_mem_no["'][^>]*value=["'](\d+)["']"#, in: html)
            ?? PortalClient.firstMatch(#"value=["'](\d+)["'][^>]*name=["']acc_tc_mem_no["']"#, in: html)
        let manager = json?.dict("outingMngVo")
        guard let no = hidden ?? manager?.str("app_tc_no") else { throw PortalError.rejected("승인 교사를 찾지 못했습니다 — 포털에서 신청해 주세요") }
        return OutingFormInfo(notice: json?.dict("outingSettingVo")?.str("out_cont")?.replacingOccurrences(of: "\r\n", with: "\n"),
                              approverName: manager?.str("app_tc_name"), approverNo: no)
    }

    private static func transportText(_ t: OutingTransport, _ other: String) -> String { t == .other ? other.trimmingCharacters(in: .whitespaces) : t.rawValue }

    static func apply(form: OutingFormInfo, out: Date, back: Date, reason: String, transport: OutingTransport, other: String) async throws -> String {
        try await PortalWrite.send("/main/outing/apply_inproc.json", [
            ("dep_exp_dt", PortalLists.stampText(out)), ("ret_exp_dt", PortalLists.stampText(back)),
            ("out_rsn", cleanReason(reason).trimmingCharacters(in: .whitespacesAndNewlines)),
            ("trans_mtd", transportText(transport, other)), ("trans_mtd_radio", transport.rawValue), ("acc_tc_mem_no", form.approverNo),
        ], referer: page, failText: "신청하지 못했습니다")
    }

    /// 수정 — 신청(대기) 상태이고 단체 신청이 아닌 건만.
    static func update(osmNo: Int, out: Date, back: Date, reason: String, transport: OutingTransport, other: String) async throws -> String {
        try await PortalWrite.send("/main/outing/apply_upproc.json", [
            ("osm_no", "\(osmNo)"), ("dep_exp_dt", PortalLists.stampText(out)), ("ret_exp_dt", PortalLists.stampText(back)),
            ("out_rsn", cleanReason(reason).trimmingCharacters(in: .whitespacesAndNewlines)), ("trans_mtd", transportText(transport, other)),
        ], referer: history, failText: "수정하지 못했습니다")
    }

    static func cancel(osmNo: Int, stdMemNo: String) async throws -> String {
        try await PortalWrite.send("/main/outing/outing_apply_cancel.json", [("osm_no", "\(osmNo)"), ("std_mem_no", stdMemNo)],
                                   referer: history, failText: "취소하지 못했습니다")
    }

    /// 포털 안내의 신청 기한 — 평일 출발은 그날 오전 9시, 휴일 출발은 전날 오전 9시.
    static func deadline(_ out: LocalDate, isRestDay: (LocalDate) -> Bool) -> Date {
        (isRestDay(out) ? out.adding(days: -1) : out).at(minutes: 9 * 60)
    }
}

// MARK: - 교과교실 현황

/*
 * 포털 교과교실 신청 페이지의 조회 흐름 그대로 — 타임(timeList) → 건물 cls-plg-list {stIdxFull} → 교실 cls-cr-list {stIdxFull, slgNum}
 * → 신청 목록 apply-list {cp, pageSize, stIdxFull, slgNum, slpIdx} (오늘, 모든 학생의 신청이 보임).
 */
struct ClassroomTime: Hashable { let id: String; let name: String; let start: String?; let end: String?
    var label: String { start.map { "\(name) \($0)" } ?? name }
}
struct ClassroomStatusRoom: Hashable { let idx: String; let name: String }
struct ClassroomOccupant: Identifiable {
    let id = UUID()
    let room: String
    /// 건물 전체로 볼 때 같은 이름의 교실을 가르는 데 씁니다.
    let building: String?
    let name: String
    let grade: String?
    let reason: String?
    let status: String?
}

enum ClassroomStatusAPI {
    private static let referer = "/main/classroom/apply.do"

    private static func post(_ name: String, _ params: [(String, String)]) async throws -> [String: Any] {
        let json = try await PortalClient.shared.authenticatedJSON("/main/classroom/\(name)", params: params, referer: PortalClient.base + referer)
        if let r = json.str("result"), r != "success" { throw PortalError.rejected(json.str("resMsg") ?? "교과교실 정보를 불러오지 못했습니다") }
        return json
    }

    static func times() async throws -> [ClassroomTime] {
        let body = try await PortalClient.shared.authenticatedText(referer)
        let list = ((try? JSONSerialization.jsonObject(with: Data(body.utf8))) as? [String: Any])?.array("timeList") ?? []
        var seen = Set<String>()
        return list.compactMap { o -> ClassroomTime? in
            guard let g = o.int("stg_idx"), let i = o.int("st_idx"), g >= 0, i >= 0 else { return nil }
            return ClassroomTime(id: "\(g)_\(i)", name: o.str("st_nm") ?? "타임 \(i)", start: o.str("st_time"), end: o.str("ed_time"))
        }.filter { seen.insert($0.id).inserted }
    }

    static func buildings(_ time: String) async throws -> [ClassroomBuildingOption] {
        (try await post("cls-plg-list.json", [("stIdxFull", time)]).array("list") ?? [])
            .compactMap { o in o.str("slg_num").map { ClassroomBuildingOption(num: $0, name: o.str("slg_nm") ?? $0) } }
    }

    static func rooms(_ time: String, building: String) async throws -> [ClassroomStatusRoom] {
        (try await post("cls-cr-list.json", [("stIdxFull", time), ("slgNum", building)]).array("list") ?? [])
            .compactMap { o in o.str("slp_idx").map { ClassroomStatusRoom(idx: $0, name: o.str("slp_nm") ?? $0) } }
    }

    /// 신청 목록 — [room] 이 nil 이면 건물 전체. 끝까지(최대 10쪽) 모읍니다.
    static func occupants(_ time: String, building: String?, room: String?) async throws -> [ClassroomOccupant] {
        var result: [ClassroomOccupant] = []
        for page in 1...10 {
            var params = [("cp", "\(page)"), ("pageSize", "50"), ("stIdxFull", time)]
            if let building { params.append(("slgNum", building)) }
            if let room { params.append(("slpIdx", room)) }
            let paging = try await post("apply-list.json", params).dict("paging")
            let rows = paging?.array("result") ?? []
            result += rows.map { o in
                ClassroomOccupant(room: o.str("slp_nm") ?? "교실", building: o.str("slg_nm"), name: o.str("in_mem_name") ?? "이름 없음",
                                  grade: o.str("cmt_grade"), reason: o.str("crt_cont"), status: o.str("cc_cd_name"))
            }
            let total = paging?.int("rowTotal") ?? -1
            if rows.count < 50 || (total >= 0 && result.count >= total) { break }
        }
        return result
    }
}
