import Foundation
import CryptoKit

/*
 * 심야면학 — 기숙사 쪽 별도 사이트(midnight-study.vercel.app, 정적 페이지 + Supabase). 학생 화면이 쓰는 호출:
 *   로그인 POST /auth/v1/token?grant_type=password {email, password} — email 은 이름을 해시한 가짜 주소
 *   갱신   POST /auth/v1/token?grant_type=refresh_token {refresh_token}
 *   현황   POST /rest/v1/rpc/get_dashboard {p_session, p_floor}
 *   신청   POST /rest/v1/rpc/reserve_seat {p_seat, p_session, p_floor}
 *   취소   POST /rest/v1/rpc/cancel_today_reservation {p_session, p_floor}
 * 비밀번호는 저장하지 않고 로그인으로 받은 세션 토큰만 키체인에.
 */

private let supabaseURL = "https://jltmfpxxgxruhalvhwnu.supabase.co"
/// 사이트 supabase-config.js 에 공개된 publishable 키(브라우저에 그대로 내려가는 공개 키).
private let supabaseKey = "sb_publishable_FB_lRADTZt6U-Dx8UimUqA_kCJozOuF"
let midnightStudyURL = URL(string: "https://midnight-study.vercel.app/")!

struct MidnightSeat: Hashable { let number: Int; let occupied: Bool; let mine: Bool; let applicantName: String?; let applicantRoom: String? }

struct MidnightDashboard: Equatable {
    var name = ""
    var room: String?
    var floor: Int?
    var eligibleFloors: [Int] = []
    var absenceCount = 0
    var bannedUntil: String?
    var roomUpdateRequired = false
    var date: String?
    var mode: String?
    var sessionStart: String?
    var sessionEnd: String?
    var session2Available = false
    var canApply = false
    var open = false
    var opensAt: String?
    var closesAt: String?
    var blockedUntil: String?
    var session1Required = false
    var hasSession2Reservation = false
    var reservationFloor: Int?
    var reservationSeat: Int?
    var seats: [MidnightSeat] = []

    var hasReservation: Bool { reservationSeat != nil }

    /// 사이트 상단 배지와 같은 문구.
    var statusText: String {
        if let b = blockedUntil { return "신청 제한 · \(b)까지" }
        if session1Required { return "1타임 신청 필요" }
        return open ? "신청 가능 · \(closesAt ?? "") 마감" : "신청 마감 · \(opensAt ?? "") 오픈"
    }

    var helpText: String {
        if blockedUntil != nil { return "불참 2회 누적으로 신청이 제한되었습니다." }
        if session1Required { return "2타임은 같은 날 1타임 좌석을 먼저 신청해야 합니다." }
        return open ? "빈 좌석을 누르면 바로 신청되고, 내 좌석을 누르면 취소됩니다." : "신청 가능 시간은 \(opensAt ?? "")~\(closesAt ?? "")입니다."
    }

    var modeLabel: String? {
        switch mode { case "normal": "평상시"; case "pre_exam": "고사 2주 전"; case "exam": "고사기간"; default: nil }
    }

    static func parse(_ json: [String: Any]) -> MidnightDashboard {
        let user = json.dict("user") ?? [:], app = json.dict("application") ?? [:], res = json.dict("reservation")
        func bool(_ o: [String: Any], _ k: String) -> Bool { (o[k] as? Bool) ?? ((o[k] as? NSNumber)?.boolValue ?? false) }
        var d = MidnightDashboard()
        d.name = user.str("name") ?? ""
        d.room = user.str("room")
        d.floor = user.int("floor")
        d.eligibleFloors = (user["eligibleFloors"] as? [Any] ?? []).compactMap { ($0 as? NSNumber)?.intValue }
        d.absenceCount = user.int("absenceCount") ?? 0
        d.bannedUntil = user.str("bannedUntil")
        d.roomUpdateRequired = bool(user, "roomUpdateRequired")
        d.date = app.str("date"); d.mode = app.str("mode")
        d.sessionStart = app.str("sessionStart"); d.sessionEnd = app.str("sessionEnd")
        d.session2Available = bool(app, "session2Available"); d.canApply = bool(app, "canApply"); d.open = bool(app, "open")
        d.opensAt = app.str("opensAt"); d.closesAt = app.str("closesAt"); d.blockedUntil = app.str("blockedUntil")
        d.session1Required = bool(app, "session1Required"); d.hasSession2Reservation = bool(app, "hasSession2Reservation")
        d.reservationFloor = res?.int("floor"); d.reservationSeat = res?.int("seat")
        d.seats = (json.array("seats") ?? []).map { s in
            MidnightSeat(number: s.int("number") ?? 0, occupied: bool(s, "occupied"), mine: bool(s, "mine"),
                         applicantName: s.str("applicantName"), applicantRoom: s.str("applicantRoom"))
        }
        return d
    }
}

struct MidnightError: LocalizedError { let message: String; var loggedOut = false; var errorDescription: String? { message } }

/// 로그인으로 받은 Supabase 세션(토큰)만 키체인에 — 비밀번호는 저장하지 않습니다.
enum MidnightSession {
    static var isLoggedIn: Bool { refresh != nil }
    static var access: String? { Keychain.get("midnight.access") }
    static var refresh: String? { Keychain.get("midnight.refresh") }
    static var expiresAt: Double { SharedContainer.defaults.double(forKey: "midnight.expires_at") }
    static var name: String? { SharedContainer.defaults.string(forKey: "midnight.name") }

    static func save(_ json: [String: Any], name: String? = nil) {
        if let a = json["access_token"] as? String { _ = Keychain.set(a, for: "midnight.access") }
        if let r = json["refresh_token"] as? String { _ = Keychain.set(r, for: "midnight.refresh") }
        let expiresIn = (json["expires_in"] as? NSNumber)?.doubleValue ?? 3600
        SharedContainer.defaults.set(Date().timeIntervalSince1970 + expiresIn, forKey: "midnight.expires_at")
        if let name { SharedContainer.defaults.set(name, forKey: "midnight.name") }
    }

    static func clear() {
        Keychain.remove("midnight.access")
        Keychain.remove("midnight.refresh")
        SharedContainer.defaults.removeObject(forKey: "midnight.expires_at")
    }
}

enum MidnightAPI {
    /// 사이트와 같은 규칙: 이름 → NFC·소문자 → SHA-256 → student-<hex>@midnightstudy.local
    static func nameToEmail(_ name: String) -> String {
        let normalized = name.trimmingCharacters(in: .whitespaces).precomposedStringWithCanonicalMapping.lowercased()
        let hash = SHA256.hash(data: Data(normalized.utf8)).map { String(format: "%02x", $0) }.joined()
        return "student-\(hash)@midnightstudy.local"
    }

    static func login(name: String, password: String) async throws {
        let json = try await auth("password", ["email": nameToEmail(name), "password": password])
        MidnightSession.save(json, name: name.trimmingCharacters(in: .whitespaces))
    }

    @MainActor
    static func logout(model: AppModel) {
        MidnightSession.clear()
        MidnightSchedule.clear(model: model)
    }

    @MainActor
    static func dashboard(session: Int, floor: Int?, model: AppModel) async throws -> MidnightDashboard {
        let d = try await rpc("get_dashboard", ["p_session": session, "p_floor": floor ?? NSNull()])
        MidnightSchedule.record(session: session, d, model: model)
        return d
    }

    @MainActor
    static func reserve(seat: Int, session: Int, floor: Int?, model: AppModel) async throws -> MidnightDashboard {
        let d = try await rpc("reserve_seat", ["p_seat": seat, "p_session": session, "p_floor": floor ?? NSNull()])
        MidnightSchedule.record(session: session, d, model: model)
        return d
    }

    @MainActor
    static func cancel(session: Int, floor: Int?, model: AppModel) async throws -> MidnightDashboard {
        let d = try await rpc("cancel_today_reservation", ["p_session": session, "p_floor": floor ?? NSNull()])
        MidnightSchedule.record(session: session, d, model: model)
        return d
    }

    private static func rpc(_ name: String, _ params: [String: Any]) async throws -> MidnightDashboard {
        var token = try await validAccessToken()
        for attempt in 0..<2 {
            var req = URLRequest(url: URL(string: "\(supabaseURL)/rest/v1/rpc/\(name)")!)
            req.httpMethod = "POST"
            req.timeoutInterval = 30
            req.setValue(supabaseKey, forHTTPHeaderField: "apikey")
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: params)
            let (data, resp) = try await URLSession.shared.data(for: req)
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            if (200..<300).contains(code), let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] { return .parse(json) }
            let message = ((try? JSONSerialization.jsonObject(with: data)) as? [String: Any])?.str("message") ?? ""
            let authProblem = code == 401 || message.range(of: "jwt|session|로그인이 필요", options: [.regularExpression, .caseInsensitive]) != nil
            if authProblem && attempt == 0 { token = try await refresh() }
            else if authProblem { MidnightSession.clear(); throw MidnightError(message: "로그인이 만료되었습니다. 다시 로그인해 주세요.", loggedOut: true) }
            else { throw MidnightError(message: message.isEmpty ? "요청을 처리하지 못했습니다 (HTTP \(code))" : message) }
        }
        throw MidnightError(message: "요청을 처리하지 못했습니다")
    }

    private static func validAccessToken() async throws -> String {
        guard let access = MidnightSession.access else { throw MidnightError(message: "로그인이 필요합니다", loggedOut: true) }
        // 만료 1분 전부터는 미리 갱신합니다.
        return Date().timeIntervalSince1970 > MidnightSession.expiresAt - 60 ? try await refresh() : access
    }

    private static func refresh() async throws -> String {
        guard let r = MidnightSession.refresh else { throw MidnightError(message: "로그인이 필요합니다", loggedOut: true) }
        do {
            let json = try await auth("refresh_token", ["refresh_token": r])
            MidnightSession.save(json)
            return json["access_token"] as? String ?? ""
        } catch {
            MidnightSession.clear()
            throw MidnightError(message: "로그인이 만료되었습니다. 다시 로그인해 주세요.", loggedOut: true)
        }
    }

    private static func auth(_ grant: String, _ body: [String: Any]) async throws -> [String: Any] {
        var req = URLRequest(url: URL(string: "\(supabaseURL)/auth/v1/token?grant_type=\(grant)")!)
        req.httpMethod = "POST"
        req.timeoutInterval = 30
        req.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        if (200..<300).contains(code), let json, json["access_token"] != nil { return json }
        let raw = json.flatMap { $0.str("error_description") ?? $0.str("msg") ?? $0.str("message") } ?? ""
        throw MidnightError(message: raw.range(of: "invalid login credentials", options: .caseInsensitive) != nil ? "이름 또는 비밀번호가 올바르지 않습니다."
                            : raw.isEmpty ? "로그인하지 못했습니다 (HTTP \(code))" : raw)
    }
}

/// 심야면학 신청을 오늘 일정·실시간 활동·위젯에 — 받은 현황으로 갱신해 블록 계산에 넘깁니다. 최근 이틀치만.
@MainActor
enum MidnightSchedule {
    private static let name = "midnight_schedule"

    static func load() -> [LocalDate: [Schedule.MidnightBooking]] {
        let raw = JSONCache.read([String: [Schedule.MidnightBooking]].self, name)?.value ?? [:]
        return Dictionary(uniqueKeysWithValues: raw.compactMap { k, v in LocalDate(iso: k).map { ($0, v) } })
    }

    private static func save(_ map: [LocalDate: [Schedule.MidnightBooking]], model: AppModel) {
        let today = LocalDate.today()
        let kept = map.filter { $0.key >= today.adding(days: -1) && !$0.value.isEmpty }
        JSONCache.write(Dictionary(uniqueKeysWithValues: kept.map { ($0.key.iso, $0.value) }), name)
        model.midnight = kept
        WidgetBridge.reload()
        LiveActivityController.update(model: model)
    }

    private static func minutes(_ hhmm: String?) -> Int? {
        let p = hhmm?.split(separator: ":").compactMap { Int($0) } ?? []
        return p.count >= 2 ? p[0] * 60 + p[1] : nil
    }

    /// [session] 타임 현황을 반영 — 신청이 있으면 넣고, 없으면 그 타임을 뺍니다.
    static func record(session: Int, _ d: MidnightDashboard, model: AppModel) {
        guard let date = d.date.flatMap(LocalDate.init(iso:)) else { return }
        var map = load()
        let before = (map[date] ?? []).sorted { $0.start < $1.start }
        var list = before.filter { $0.session != session }
        if session == 1 && !d.session2Available { list = list.filter { $0.session != 2 } }
        if d.hasReservation, let s = minutes(d.sessionStart), let e = minutes(d.sessionEnd) {
            // 정오 이전 시작은 자정 넘어서의 타임으로, 끝이 시작보다 이르면 다음 날로.
            let start = s < 12 * 60 ? s + 24 * 60 : s
            var end = e
            while end <= start { end += 24 * 60 }
            let seat = [(d.reservationFloor ?? d.floor).map { "\($0)층" }, d.reservationSeat.map { "\($0)번" }].compactMap { $0 }.joined(separator: " ")
            list.append(Schedule.MidnightBooking(session: session, start: start, end: end, seat: seat.isEmpty ? nil : seat))
        }
        list.sort { $0.start < $1.start }
        guard list != before else { if model.midnight.isEmpty { model.midnight = map }; return }
        map[date] = list
        save(map, model: model)
    }

    /// 로그인돼 있으면 오늘 현황을 받아 반영합니다(동기화 때).
    static func refresh(model: AppModel) async {
        guard MidnightSession.isLoggedIn, let first = try? await MidnightAPI.dashboard(session: 1, floor: nil, model: model) else { return }
        if first.session2Available { _ = try? await MidnightAPI.dashboard(session: 2, floor: first.floor, model: model) }
    }

    static func clear(model: AppModel) {
        JSONCache.remove(name)
        model.midnight = [:]
        WidgetBridge.reload()
    }
}
