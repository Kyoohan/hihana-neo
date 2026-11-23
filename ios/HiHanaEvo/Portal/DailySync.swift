import Foundation

/// 그 날의 면학 위치 — 포털의 면학 배정, 교과교실 신청, 1인2기·방과후 신청을 타임별 장소로(안드로이드 HanaPortalClient·HanaSyncApplier).
enum DailySync {
    struct Assignment { let stNm: String; let category: String; let floor: String?; let seat: String? }
    struct Program { let stNm: String; let gubun: String?; let name: String; let building: String?; let room: String?; let date: String? }
    /// 교과교실 신청 — 면학 배정 목록은 이걸 반영하지 않아 따로 받습니다.
    struct ClassroomEntry { let stNm: String?; let room: String?; let date: String?; let status: String?; let cancelled: Bool }
    struct Sync { let assignments: [Assignment]; let programs: [Program]; let classrooms: [ClassroomEntry] }

    private static func params(_ date: LocalDate) -> [(String, String)] {
        [("cp", "1"), ("searchSDate", date.iso), ("searchEDate", date.iso), ("listType", "list")]
    }

    /// 세 가지를 함께 — 교과교실은 형식이 바뀌어도 나머지를 막지 않게 실패를 삼킵니다.
    static func fetch(_ date: LocalDate) async throws -> Sync {
        async let a = assignments(date)
        async let p = programs(date)
        async let c = classrooms(date)
        return Sync(assignments: try await a, programs: try await p, classrooms: (try? await c) ?? [])
    }

    static func assignments(_ date: LocalDate) async throws -> [Assignment] {
        let json = try await PortalClient.shared.authenticatedJSON("/main/studyroom/study-only-req-list.json", params: params(date),
                                                                    isValid: { ($0["paging"] as? [String: Any])?["result"] != nil })
        guard let rows = json.dict("paging")?.array("result") else { throw PortalError.unexpected("[study] 면학 배정 응답을 읽지 못했습니다") }
        return rows.compactMap { r in
            guard let st = r.str("st_nm"), let cat = r.str("c_place_cd_name") ?? Optional("") else { return nil }
            return Assignment(stNm: st, category: cat, floor: r.str("clr_area_nm"), seat: r.str("srt_cont"))
        }
    }

    static func programs(_ date: LocalDate) async throws -> [Program] {
        let json = try await PortalClient.shared.authenticatedJSON("/main/extra-program/lesson_req_my_list.json", params: params(date),
                                                                    isValid: { $0["list"] != nil })
        guard let rows = json.array("list") else { throw PortalError.unexpected("[program] 응답을 읽지 못했습니다") }
        return rows.compactMap { r in
            guard let st = r.str("st_nm"), let name = r.str("otl_nm") else { return nil }
            return Program(stNm: st, gubun: r.str("otm_gubun"), name: name, building: r.str("slg_nm"), room: r.str("slp_nm"), date: r.str("otb_date"))
        }
    }

    /// 교과교실 신청 — 배열 위치와 필드명이 확정되지 않아 후보를 넓게 훑습니다.
    static func classrooms(_ date: LocalDate) async throws -> [ClassroomEntry] {
        let json = try await PortalClient.shared.authenticatedJSON("/main/classroom/apply-list.json", params: params(date))
        let rows = json.dict("paging")?.array("result") ?? json.array("list") ?? json.array("itemList") ?? json.array("result") ?? []
        func first(_ r: [String: Any], _ keys: String...) -> String? { keys.lazy.compactMap { r.str($0) }.first }
        return rows.map { r in
            ClassroomEntry(
                stNm: first(r, "st_nm", "stNm", "slot_nm", "time_nm", "st_time", "cc_time_nm"),
                room: first(r, "cc_place_nm", "room_nm", "slp_nm", "place_nm", "cr_nm", "classroom_nm", "ccr_nm", "slg_nm", "c_place_cd_name"),
                date: first(r, "cc_date", "otb_date", "req_date", "apply_date", "use_date", "std_date", "ymd").map { String($0.prefix(10)) },
                status: first(r, "cc_cd_name", "status_nm", "stat_nm", "appr_nm", "req_state", "state_nm"),
                cancelled: ["del_yn", "cancel_yn", "cncl_yn"].contains { r.str($0)?.uppercased() == "Y" })
        }
    }

    /// 배정 → 교과교실 신청 → 1인2기·방과후 순으로 덮어씁니다(포털 시간표와 같은 우선순위).
    static func apply(_ sync: Sync, date: LocalDate, isRestDay: Bool) {
        for a in sync.assignments {
            guard let slot = PlanSlot.from(portalName: a.stNm, date: date, isRestDay: isRestDay), let place = place(for: a) else { continue }
            PlaceStore.set(place, slot: slot, date: date)
        }
        for c in sync.classrooms {
            if c.cancelled { continue }
            if let d = c.date, d != date.iso { continue }
            let status = c.status ?? ""
            if ["취소", "반려", "거절", "미승인", "불가"].contains(where: status.contains) { continue }
            guard let st = c.stNm, let slot = PlanSlot.from(portalName: st, date: date, isRestDay: isRestDay) else { continue }
            let room = (c.room ?? "").trimmingCharacters(in: .whitespaces)
            PlaceStore.set(room.isEmpty ? .other(category: "교과교실", location: nil) : .classroom(room), slot: slot, date: date)
        }
        for p in sync.programs where p.date == date.iso {
            guard let slot = PlanSlot.from(portalName: p.stNm, date: date, isRestDay: isRestDay) else { continue }
            let room = [p.building, p.room].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")
            switch p.gubun {
            case "O": PlaceStore.set(.oneTwo(activity: p.name, room: room.isEmpty ? nil : room), slot: slot, date: date)
            case "A": PlaceStore.set(.afterSchool(course: p.name, room: room.isEmpty ? nil : room), slot: slot, date: date)
            default: break
            }
        }
    }

    static func place(for a: Assignment) -> StudyPlace? {
        let location = [a.floor, a.seat].compactMap { $0?.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.joined(separator: " ")
        switch a.category.trimmingCharacters(in: .whitespaces) {
        case "도서관": return .library(seat: libraryLocation(floor: a.floor, seat: a.seat))
        case "면학실": return .studyRoom(seat: location.isEmpty ? "위치 미확인" : location)
        case "생활관": return .dorm
        case let c:
            if c.isEmpty && location.isEmpty { return nil }
            return .other(category: c.isEmpty ? "기타 장소" : c, location: location.isEmpty ? nil : location)
        }
    }

    /// 도서관은 포털 원문("2F"·"34번") 대신 "2-34"(층-좌석).
    static func libraryLocation(floor: String?, seat: String?) -> String {
        if let s = seat?.trimmingCharacters(in: .whitespaces), s.range(of: #"^\d+-\d+$"#, options: .regularExpression) != nil { return s }
        func digits(_ v: String?) -> String? { let d = v?.filter(\.isNumber); return (d?.isEmpty == false) ? d : nil }
        switch (digits(floor), digits(seat)) {
        case let (f?, s?): return "\(f)-\(s)"
        case let (f, s): return s ?? f ?? "위치 미확인"
        }
    }
}
