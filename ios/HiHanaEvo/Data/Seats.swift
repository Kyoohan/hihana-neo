import Foundation

/*
 * 도서관·면학실 좌석 — 안드로이드 HanaLibraryClient.
 * - 좌석 목록: POST study-room-req-list.json {stIdxFull, subListYn=N} → hYn/hMsg(휴관), studyRoomOpenVo(격자 크기), list[좌석]
 * - 신청: POST study-room-req.json {clrIdx, srtIdx, stIdxFull} · 취소: POST /main/studyroom/study-cancel.json {sreIdx}
 * - 기기 등록: POST /main/member/updateDiviceInfo.json {usegubun} — 포털은 마지막으로 등록한 기기에서만 신청을 받습니다.
 * 다른 학생의 이름·학번은 받아도 지우고 성별만 씁니다(공개 빌드와 같게).
 */

struct LibrarySlot: Hashable, Identifiable { let id: String; let label: String }

struct LibrarySeat: Hashable {
    let cont: String
    var x: Int
    var y: Int
    let area: String?
    let type: String
    let color: String?
    let usable: Bool
    let clrIdx: Int
    let srtIdx: Int
    let sreIdx: Int?
    let mine: Bool
    let holiday: Bool
    /// 포털 페이지처럼 남의 자리엔 성별만.
    let gender: String?
    /// 지정석 — 취소할 수 없습니다.
    let assigned: Bool
    /// 서버가 "이미 예약된 좌석"이라 했지만 좌석표로 아직 확인하지 않은 자리 — 다시 누를 수 있습니다.
    var pending = false

    var isSeat: Bool { type == "1" }
    var available: Bool { isSeat && usable && sreIdx == nil && !holiday }
}

struct LibraryArea: Hashable { let label: String; let gridX: Int; let gridY: Int; let seats: [LibrarySeat] }

struct LibrarySeatMap: Equatable {
    let areas: [LibraryArea]
    /// 휴관이면 안내 문구.
    let closedMessage: String?
    var seats: [LibrarySeat] { areas.flatMap(\.seats) }

    /// 거절된 자리를 새 좌석표를 받기 전까지 '확인 중'으로 — 오픈 직후엔 잠깐 잠겼다 풀리는 자리가 있어 다시 누르면 잡힙니다.
    func markPending(_ cont: String) -> LibrarySeatMap {
        LibrarySeatMap(areas: areas.map { a in
            LibraryArea(label: a.label, gridX: a.gridX, gridY: a.gridY, seats: a.seats.map { s in
                var c = s; if s.cont == cont && s.sreIdx == nil { c.pending = true }; return c
            })
        }, closedMessage: closedMessage)
    }
}

enum HanaLibraryAPI {
    /// 신청·취소 응답을 기다리는 최대 시간 — 예약 오픈 직후엔 서버가 요청을 수십 초 붙들고 있다가 답합니다.
    private static let applyWait: TimeInterval = 90

    /// 신청 페이지가 안 읽힐 때의 기본 타임.
    private static let fallbackSlots = [
        LibrarySlot(id: "1_9", label: "평일1타임"), LibrarySlot(id: "1_10", label: "평일2타임"),
        LibrarySlot(id: "4_12", label: "휴일1타임"), LibrarySlot(id: "4_13", label: "휴일2타임"),
    ]

    private static func applyPageJSON(_ service: SeatService) async -> [String: Any]? {
        guard let body = try? await PortalClient.shared.authenticatedText(service.applyPage) else { return nil }
        return try? JSONSerialization.jsonObject(with: Data(body.utf8)) as? [String: Any]
    }

    /// 타임 목록 — 신청 페이지를 JSON 으로 부르면 timeList 를 줍니다. stIdxFull 은 `stg_idx_st_idx`.
    static func slots(_ service: SeatService) async -> [LibrarySlot] {
        guard let list = await applyPageJSON(service)?.array("timeList"), !list.isEmpty else { return fallbackSlots }
        var seen = Set<String>()
        let slots: [LibrarySlot] = list.compactMap { o in
            guard let g = o.int("stg_idx"), let i = o.int("st_idx"), g >= 0, i >= 0 else { return nil }
            let name = o.str("st_nm") ?? "타임 \(i)"
            return LibrarySlot(id: "\(g)_\(i)", label: o.str("st_time").map { "\(name) \($0)" } ?? name)
        }.filter { seen.insert($0.id).inserted }
        return slots.isEmpty ? fallbackSlots : slots
    }

    /// 신청 페이지의 deviceChk — 이 기기(세션)가 마지막 등록 기기인지. 못 읽으면 nil.
    static func isDeviceRegistered(_ service: SeatService) async -> Bool? {
        guard let json = await applyPageJSON(service), let v = json["deviceChk"] else { return nil }
        return (v as? Bool) ?? ((v as? NSNumber)?.boolValue)
    }

    static func registerDevice(_ service: SeatService) async throws {
        let json = try await PortalClient.shared.authenticatedJSON("/main/member/updateDiviceInfo.json", params: [("usegubun", service.deviceGubun)],
                                                                    referer: PortalClient.base + service.applyPage)
        guard json.str("result") == "success" else { throw PortalError.rejected(clean(json.str("resMsg")) ?? "기기를 등록하지 못했습니다") }
    }

    /// 후보 경로를 차례로 POST 해 처음으로 JSON 을 주는 응답 — 과부하 때의 빈 본문은 같은 경로를 짧게 몇 번 더.
    private static func firstJSON(_ paths: [String], _ params: [(String, String)], referer: String, timeout: TimeInterval? = nil) async throws -> [String: Any] {
        var last: Error?
        for path in paths {
            var attempt = 0, transientHere = false, networkRetries = 0
            while attempt < 5 {
                do {
                    let raw = try await PortalClient.shared.authenticatedRaw(path, params: params, method: "POST", referer: referer, timeout: timeout)
                    if let json = try? JSONSerialization.jsonObject(with: Data(raw.body.utf8)) as? [String: Any] { return json }
                    if raw.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        last = PortalError.transient("[\(path)] HTTP \(raw.code) 빈 응답")
                        transientHere = true
                        attempt += 1
                        try await Task.sleep(for: .milliseconds(200 + 150 * attempt))
                        continue
                    }
                    break
                } catch let e as PortalError {
                    // 기다리는 동안 답이 없었음 — 다시 보내지 않고 호출부가 좌석표로 확인합니다.
                    if case .timeout = e { throw e }
                    last = e
                    break
                } catch let e as URLError where [.cannotFindHost, .cannotConnectToHost, .notConnectedToInternet, .dnsLookupFailed].contains(e.code) && networkRetries < 2 {
                    // 서버에 닿기 전의 실패라 다시 보내도 안전합니다.
                    networkRetries += 1
                    try await Task.sleep(for: .milliseconds(300 * networkRetries))
                } catch {
                    last = error
                    break
                }
            }
            if transientHere { break }
        }
        throw last ?? PortalError.unexpected("[\(paths.first ?? "")] 사용할 수 있는 엔드포인트가 없습니다")
    }

    static func seatMap(_ slotId: String, service: SeatService) async throws -> LibrarySeatMap {
        let json = try await firstJSON(service.listPaths, [("stIdxFull", slotId), ("subListYn", "N")], referer: PortalClient.base + service.applyPage)
        guard json.str("result") == "success" else { throw PortalError.rejected(clean(json.str("resMsg")) ?? "좌석을 불러오지 못했습니다") }
        if json.str("hYn") == "Y" { return LibrarySeatMap(areas: [], closedMessage: json.str("hMsg") ?? "휴관일") }
        let open = json.dict("studyRoomOpenVo")
        let seats: [LibrarySeat] = (json.array("list") ?? []).map { o in
            LibrarySeat(cont: o.str("srt_cont") ?? "", x: (o.int("srt_x") ?? 0) - 1, y: (o.int("srt_y") ?? 0) - 1,
                        area: o.str("clr_area_nm"), type: o.str("srt_type") ?? "", color: o.str("srt_color"),
                        usable: o.str("srt_use_yn") == "Y", clrIdx: o.int("clr_idx") ?? 0, srtIdx: o.int("srt_idx") ?? 0,
                        sreIdx: o.int("sre_idx").flatMap { $0 > 0 ? $0 : nil }, mine: o.str("myYn") == "Y", holiday: o.str("holidayYn") == "Y",
                        gender: o.str("mem_sex") == "M" ? "남" : o.str("mem_sex") == "F" ? "여" : nil,
                        assigned: o.str("sre_assign_yn")?.uppercased() == "Y")
        }
        // 배치도는 세로로 긴 한 장 — 행마다 통로 칸의 바닥색으로 층을 갈라 구역으로 묶고, 없는 행이 이어지는 큰 틈은 빈 행 하나로 줄입니다.
        let gridX = open?.int("sro_x").flatMap { $0 > 0 ? $0 : nil } ?? ((seats.map(\.x).max() ?? 0) + 1)
        let rows = Dictionary(grouping: seats.filter { $0.x >= 0 && $0.y >= 0 }, by: \.y).sorted { $0.key < $1.key }
        func floorColor(_ row: [LibrarySeat]) -> String { row.first { $0.type == "3" && $0.color != nil }?.color ?? row.compactMap(\.color).first ?? "" }
        // 도서관은 같은 층 안에서도 큰 틈에서 한 번 더 나눠 "2층 1구역 / 2층 2구역", 면학실은 색으로만(E~H).
        let splitOnGap = service == .library
        var groups: [(color: String, rows: [(y: Int, seats: [LibrarySeat])])] = []
        for (y, row) in rows {
            let color = floorColor(row)
            if let last = groups.last, last.color == color, !(splitOnGap && (last.rows.last.map { y - $0.y > 1 } ?? false)) {
                groups[groups.count - 1].rows.append((y, row))
            } else {
                groups.append((color, [(y, row)]))
            }
        }
        func floorOf(_ color: String) -> String? {
            for g in groups where g.color == color {
                for r in g.rows {
                    for cell in r.seats where !cell.isSeat {
                        if let n = PortalClient.firstMatch(#"(\d+)F"#, in: cell.cont) { return "\(n)층" }
                    }
                }
            }
            return nil
        }
        var perColor: [String: Int] = [:]
        for g in groups { perColor[g.color, default: 0] += 1 }
        var colorSeen: [String: Int] = [:]
        let areas: [LibraryArea] = groups.enumerated().map { index, g in
            var next = 0
            var prevY: Int?
            var remapped: [LibrarySeat] = []
            for r in g.rows {
                if let p = prevY { next += r.y - p > 1 ? 2 : 1 }
                prevY = r.y
                remapped += r.seats.map { var s = $0; s.y = next; return s }
            }
            let nth = (colorSeen[g.color] ?? 0) + 1
            colorSeen[g.color] = nth
            let floor = floorOf(g.color)
            let label: String
            if service == .studyRoom && index < 4 { label = "구역 \(Character(UnicodeScalar(UInt8(69 + index))))" }
            else if let floor, (perColor[g.color] ?? 1) > 1 { label = "\(floor) \(nth)구역" }
            else if let floor { label = floor }
            else { label = "구역 \(index + 1)" }
            return LibraryArea(label: label, gridX: gridX, gridY: next + 1, seats: remapped)
        }
        return LibrarySeatMap(areas: areas, closedMessage: nil)
    }

    /// 좌석 신청 — 먼저 그냥 신청해 보고, 기기 때문에 거절될 때만 이 기기를 등록하고 한 번 더(매번 등록하면 다른 기기의 등록을 빼앗습니다).
    static func reserve(_ seat: LibrarySeat, slotId: String, service: SeatService) async throws -> String {
        let params = [("clrIdx", "\(seat.clrIdx)"), ("srtIdx", "\(seat.srtIdx)"), ("stIdxFull", slotId)]
        let referer = PortalClient.base + service.applyPage
        var json = try await firstJSON(service.reservePaths, params, referer: referer, timeout: applyWait)
        if json.str("result") != "success", json.str("resMsg")?.contains("기기") == true {
            try await registerDevice(service)
            json = try await firstJSON(service.reservePaths, params, referer: referer, timeout: applyWait)
        }
        let message = clean(json.str("resMsg"))
        guard json.str("result") == "success" else { throw PortalError.rejected(message ?? "신청하지 못했습니다") }
        return message ?? "신청되었습니다"
    }

    static func cancel(_ sreIdx: Int, service: SeatService) async throws -> String {
        let json = try await firstJSON(["/main/studyroom/study-cancel.json"], [("sreIdx", "\(sreIdx)")], referer: PortalClient.base + service.applyPage, timeout: applyWait)
        let message = clean(json.str("resMsg"))
        guard json.str("result") == "success" else { throw PortalError.rejected(message ?? "취소하지 못했습니다") }
        return message ?? "취소되었습니다"
    }

    /// 서버 문구의 HTML(`<br/>` 등)을 줄바꿈으로.
    private static func clean(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let s = HTML.unescape(raw.replacingOccurrences(of: #"<br\s*/?>"#, with: "\n", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression))
            .split(separator: "\n", omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces) }.joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return s.isEmpty ? nil : s
    }
}

/// 빠른 예약 자리(최대 4) · 즐겨찾기 구역(하나) — 서비스마다 따로.
enum SeatStores {
    static let quickMax = 4
    static func quick(_ s: SeatService) -> [String] {
        (SharedContainer.defaults.string(forKey: "quick_seats.\(s.rawValue)") ?? "").split(separator: ",").map(String.init).prefix(quickMax).map { $0 }
    }
    static func setQuick(_ s: SeatService, _ v: [String]) { SharedContainer.defaults.set(v.prefix(quickMax).joined(separator: ","), forKey: "quick_seats.\(s.rawValue)") }
    static func favorite(_ s: SeatService) -> String? { SharedContainer.defaults.string(forKey: "seat_favorite.\(s.rawValue)") }
    static func setFavorite(_ s: SeatService, _ v: String?) { SharedContainer.defaults.set(v, forKey: "seat_favorite.\(s.rawValue)") }
}
