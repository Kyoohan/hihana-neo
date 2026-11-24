import Foundation

enum PortalError: LocalizedError, Equatable {
    case missingCredentials
    case tokenNotFound
    case loginFailed(String)
    case unexpected(String)
    /// 서버가 이유를 문장으로 돌려준 실패("이미 신청된 좌석입니다" 등) — 문구를 그대로 보여 줍니다.
    case rejected(String)
    /// 과부하로 빈 본문이 온 경우 — 세션 문제가 아니라 재로그인하지 않고 잠깐 뒤 다시 시도합니다.
    case transient(String)
    case timeout(String)
    /// 세션이 끊겨 로그인 페이지가 내려옴 — 재로그인을 정하는 내부 신호.
    case loginPage
    /// 로그인 실패가 확인된 뒤 — 계정 잠금(5회 실패)을 막으려고 계정을 다시 넣을 때까지 자동 로그인을 쉽니다.
    case suspended

    var errorDescription: String? {
        switch self {
        case .missingCredentials: "하이하나 아이디/비밀번호를 먼저 등록해 주세요"
        case .tokenNotFound: "로그인 페이지를 불러오지 못했습니다"
        case .loginFailed(let m): m
        case .unexpected(let d): "학사시스템 응답을 해석하지 못했습니다\n\(d)"
        case .rejected(let m): m
        case .transient(let d): "학사시스템이 응답하지 않습니다 (잠시 후 다시 시도)\n\(d)"
        case .timeout(let d): "응답이 오지 않았습니다\n\(d)"
        case .loginPage: "로그인 페이지가 내려왔습니다"
        case .suspended: "로그인에 실패해 자동 로그인을 멈췄습니다. 설정에서 계정을 다시 입력해 주세요"
        }
    }

    /// 계정이 없거나 로그인에 실패한 것 — 화면은 '연동 필요'로.
    var needsAccount: Bool {
        switch self {
        case .missingCredentials, .loginFailed, .suspended: true
        default: false
        }
    }
}

/// 원문 응답 — JSON 이 아닌 HTML 페이지(board_view.do 등)도 다룹니다.
struct RawResponse { let code: Int; let body: String }

/// hi.hana.hs.kr 포털 — 안드로이드 HanaPortalClient 와 같은 요청 방식.
/// - 로그인·조회 POST 모두 Referer·Origin·User-Agent·Accept 가 없으면 404 로 막힙니다.
/// - 로그인은 한 번에 하나만 — 두 로그인이 엇갈리면 토큰과 세션 쿠키가 어긋나 포털이 거절합니다.
actor PortalClient {
    static let shared = PortalClient()

    static let base = "https://hi.hana.hs.kr"
    static let baseURL = URL(string: base)!
    static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

    private let session: URLSession
    private var lastLoginAt: Date = .distantPast
    private var lastAuthenticatedAt: Date = .distantPast
    private var loginTask: Task<Void, Error>?
    private var suspended = false
    private static let sessionFresh: TimeInterval = 3 * 60

    init() {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = .shared
        config.httpShouldSetCookies = true
        config.httpCookieAcceptPolicy = .always
        config.timeoutIntervalForRequest = 25
        session = URLSession(configuration: config)
    }

    // MARK: 로그인

    /// 로그인 — 이미 진행 중이면 그것을 기다리고, 기다리는 동안 다른 요청이 로그인을 마쳤으면 그 세션을 그대로 씁니다.
    func login() async throws {
        let requestedAt = Date()
        if let running = loginTask { try await running.value; return }
        if lastLoginAt >= requestedAt { return }
        let task = Task { try await performLogin() }
        loginTask = task
        defer { loginTask = nil }
        try await task.value
        lastLoginAt = Date()
    }

    private func performLogin() async throws {
        if suspended { throw PortalError.suspended }
        guard let id = Credentials.memId, let pwd = Credentials.memPwd else { throw PortalError.missingCredentials }

        let pageURL = Self.base + "/main/login/login.do"
        var pageReq = URLRequest(url: URL(string: pageURL)!)
        pageReq.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        pageReq.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        let (pageData, _) = try await session.data(for: pageReq)
        let html = String(decoding: pageData, as: UTF8.self)
        guard let token = Self.firstMatch(#"hanaLoginRequestToken\s*=\s*"([^"]+)""#, in: html) else { throw PortalError.tokenNotFound }

        var req = browserRequest(Self.base + "/main/login/auth/login", referer: pageURL)
        req.httpMethod = "POST"
        req.setValue(token, forHTTPHeaderField: "X-Hana-Login-Token")
        req.httpBody = Self.form([("mem_id", id), ("mem_pwd", pwd)])
        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw PortalError.unexpected("[login] (HTTP \(code)) \(String(decoding: data, as: UTF8.self).prefix(300))")
        }
        let status = (json["statusCode"] as? Int) ?? 200
        if status >= 400 {
            suspended = true
            throw PortalError.loginFailed(Self.failureMessage(json["responseMessage"] as? String))
        }
    }

    /// 계정을 새로 넣었을 때 — 멈춰 둔 자동 로그인을 풀고 세션을 비웁니다.
    func resetSession() {
        suspended = false
        lastLoginAt = .distantPast
        lastAuthenticatedAt = .distantPast
        for c in HTTPCookieStorage.shared.cookies(for: Self.baseURL) ?? [] { HTTPCookieStorage.shared.deleteCookie(c) }
    }

    /// 세션과 상관없이 다시 로그인 — 웹 화면이 로그인 페이지로 튕겼을 때 스스로 복구하는 데 씁니다.
    func relogin() async throws {
        for c in HTTPCookieStorage.shared.cookies(for: Self.baseURL) ?? [] { HTTPCookieStorage.shared.deleteCookie(c) }
        lastAuthenticatedAt = .distantPast
        lastLoginAt = .distantPast
        try await login()
        lastAuthenticatedAt = Date()
    }

    /// 웹 화면을 열기 전에 세션이 살아 있는지 확인하고, 아니면 로그인합니다. 실패해도 예외를 밖으로 내지 않습니다.
    func ensureLoggedIn() async {
        if Date().timeIntervalSince(lastAuthenticatedAt) < Self.sessionFresh { return }
        _ = try? await authenticatedJSON("/main/alim/alim-target-list.json", params: [("pageSize", "1")],
                                         isValid: { ($0["paging"] as? [String: Any])?["result"] != nil })
    }

    private static func failureMessage(_ code: String?) -> String {
        switch code {
        case "MEM_INVALID_PW": "비밀번호가 올바르지 않습니다"
        case "MEM_INVALID_ID", "MEM_NOT_FOUND": "존재하지 않는 아이디입니다"
        case "MEM_LOCKED": "로그인 실패 횟수 초과로 계정이 잠겼습니다"
        default: code ?? "로그인에 실패했습니다"
        }
    }

    // MARK: 요청

    /// 세션이 필요한 JSON 조회 — 이 엔드포인트들은 GET 을 405 로 막고 폼 POST 만 받습니다.
    /// [isValid] 는 '로그인된 응답인지' — 세션이 없어도 목록 키만 빠진 JSON 이 올 때가 있어 JSON 파싱만으로는 못 잡습니다.
    /// 빈 응답(과부하)은 짧게 몇 번 다시 보내고, 로그인이 필요한 응답은 한 번만 보고 바로 로그인합니다.
    func authenticatedJSON(
        _ path: String,
        params: [(String, String)],
        referer: String = base + "/",
        isValid: ([String: Any]) -> Bool = { _ in true }
    ) async throws -> [String: Any] {
        var transient: PortalError?
        var attempt = 0
        loop: while attempt < 4 {
            do {
                let json = try await requestJSON(path, params: params, referer: referer)
                if isValid(json) { lastAuthenticatedAt = Date(); return json }
                break loop
            } catch let e as PortalError {
                switch e {
                case .transient:
                    transient = e
                    attempt += 1
                    try await Task.sleep(nanoseconds: UInt64(250_000_000 * attempt))
                case .unexpected, .loginPage:
                    break loop
                default:
                    throw e
                }
            }
        }
        if let transient { throw transient }
        try await login()
        let json = try await requestJSON(path, params: params, referer: referer)
        lastAuthenticatedAt = Date()
        return json
    }

    private func requestJSON(_ path: String, params: [(String, String)], referer: String) async throws -> [String: Any] {
        var req = browserRequest(Self.base + path, referer: referer)
        req.httpMethod = "POST"
        req.httpBody = Self.form(params)
        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let text = String(decoding: data, as: UTF8.self)
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { throw PortalError.transient("[\(path)] HTTP \(code) 빈 응답") }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            let reason = code == 405 ? "POST 방식이 필요합니다" : (code == 401 || code == 403) ? "로그인이 만료되었습니다" : "JSON 응답이 아닙니다"
            throw PortalError.unexpected("[\(path)] HTTP \(code) · \(reason)")
        }
        // 세션이 끝나면 포털은 로그인 HTML 대신 {"result":"expired","resMsg":"Login session is required."} 를 줍니다.
        // 이걸 정상 JSON 으로 넘기면 다시 로그인하지 않고 영어 문구가 그대로 화면에 떴습니다 — 로그인 페이지와 같게.
        if json.str("result") == "expired" { throw PortalError.loginPage }
        return json
    }

    /// 세션이 필요한 GET/POST 의 원문 응답 — 게시글 상세 HTML 처럼 JSON 을 강제하지 않을 곳. 로그인 페이지가 오면 한 번 다시 로그인.
    func authenticatedRaw(_ path: String, params: [(String, String)] = [], method: String = "GET",
                          referer: String = base + "/", timeout: TimeInterval? = nil) async throws -> RawResponse {
        do {
            let r = try await requestRaw(path, params: params, method: method, referer: referer, timeout: timeout)
            lastAuthenticatedAt = Date()
            return r
        } catch PortalError.loginPage {
            try await login()
            let r = try await requestRaw(path, params: params, method: method, referer: referer, timeout: timeout)
            lastAuthenticatedAt = Date()
            return r
        }
    }

    func authenticatedText(_ path: String, params: [(String, String)] = [], method: String = "GET", referer: String = base + "/") async throws -> String {
        try await authenticatedRaw(path, params: params, method: method, referer: referer).body
    }

    private func requestRaw(_ path: String, params: [(String, String)], method: String, referer: String, timeout: TimeInterval?) async throws -> RawResponse {
        var req: URLRequest
        if method.uppercased() == "GET" {
            var c = URLComponents(string: path.hasPrefix("http") ? path : Self.base + path)!
            if !params.isEmpty { c.queryItems = (c.queryItems ?? []) + params.map { URLQueryItem(name: $0.0, value: $0.1) } }
            req = browserRequest(c.url!.absoluteString, referer: referer)
            req.httpMethod = "GET"
        } else {
            req = browserRequest(path.hasPrefix("http") ? path : Self.base + path, referer: referer)
            req.httpMethod = "POST"
            req.httpBody = Self.form(params)
        }
        if let timeout { req.timeoutInterval = timeout }
        do {
            let (data, resp) = try await session.data(for: req)
            let body = String(decoding: data, as: UTF8.self)
            if Self.looksLikeLoginPage(body) { throw PortalError.loginPage }
            return RawResponse(code: (resp as? HTTPURLResponse)?.statusCode ?? 0, body: body)
        } catch let e as URLError where e.code == .timedOut {
            throw PortalError.timeout("[\(path)]")
        }
    }

    /// 페이지를 브라우저처럼(Accept: text/html) 받습니다.
    func authenticatedHTML(_ path: String) async throws -> String {
        func fetch() async throws -> String {
            var req = browserRequest(Self.base + path, referer: Self.base + "/")
            req.httpMethod = "GET"
            req.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
            req.setValue(nil, forHTTPHeaderField: "X-Requested-With")
            req.setValue(nil, forHTTPHeaderField: "Content-Type")
            let (data, _) = try await session.data(for: req)
            let text = String(decoding: data, as: UTF8.self)
            if Self.looksLikeLoginPage(text) { throw PortalError.loginPage }
            return text
        }
        do { return try await fetch() }
        catch PortalError.loginPage { try await login(); return try await fetch() }
    }

    /// 웹 화면이 같은 로그인 세션을 쓰도록 넘겨 줄 쿠키.
    /// cookies(for:) 는 경로까지 맞춰 보므로 '/main' 같은 경로 쿠키가 빠집니다 — 도메인으로 모두.
    func sessionCookies() -> [HTTPCookie] { (HTTPCookieStorage.shared.cookies ?? []).filter { $0.domain.contains("hana.hs.kr") } }

    /// 웹 화면이 받은 새 세션 쿠키를 앱 쪽으로 — 두 쪽이 늘 같은 세션을 쓰게.
    func adoptCookies(_ cookies: [HTTPCookie]) {
        for c in cookies where c.domain.contains("hana.hs.kr") { HTTPCookieStorage.shared.setCookie(c) }
    }

    // MARK: 도우미

    private func browserRequest(_ url: String, referer: String?) -> URLRequest {
        var r = URLRequest(url: URL(string: url)!)
        r.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        r.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        // 이 헤더가 없는 POST 는 서버가 라우팅하지 못해 404 로 답합니다 — 반드시 명시.
        r.setValue("application/json, text/javascript, */*; q=0.01", forHTTPHeaderField: "Accept")
        r.setValue(Self.base, forHTTPHeaderField: "Origin")
        r.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        if let referer { r.setValue(referer, forHTTPHeaderField: "Referer") }
        return r
    }

    static func form(_ params: [(String, String)]) -> Data {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return Data(params.map { "\($0.0.addingPercentEncoding(withAllowedCharacters: allowed) ?? "")=\($0.1.addingPercentEncoding(withAllowedCharacters: allowed) ?? "")" }.joined(separator: "&").utf8)
    }

    /// 로그인 페이지 HTML 이거나, 세션이 끝났을 때 오는 짧은 JSON(`{"result":"expired"}`).
    static func looksLikeLoginPage(_ body: String) -> Bool {
        body.contains("hanaLoginRequestToken") || (body.count < 200 && body.contains("\"result\"") && body.contains("\"expired\""))
    }

    static func firstMatch(_ pattern: String, in s: String) -> String? {
        guard let re = try? NSRegularExpression(pattern: pattern), let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)),
              m.numberOfRanges > 1, let r = Range(m.range(at: 1), in: s) else { return nil }
        return String(s[r])
    }
}

// MARK: - JSON 도우미

extension Dictionary where Key == String, Value == Any {
    /// 안드로이드 optStr — 비어 있거나 "null" 이면 nil. 숫자도 문자열로.
    func str(_ key: String) -> String? {
        guard let v = self[key], !(v is NSNull) else { return nil }
        let s: String
        if let x = v as? String { s = x } else if let n = v as? NSNumber { s = n.stringValue } else { s = "\(v)" }
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty || t == "null" ? nil : t
    }

    func int(_ key: String) -> Int? {
        if let n = self[key] as? NSNumber { return n.intValue }
        return str(key).flatMap { Int($0) }
    }

    func dict(_ key: String) -> [String: Any]? { self[key] as? [String: Any] }
    func array(_ key: String) -> [[String: Any]]? { self[key] as? [[String: Any]] }
}

enum HTML {
    /// `&lt;b&gt;…` 같은 엔티티를 풀고 태그를 지운 뒤 공백을 정리합니다.
    static func decode(_ raw: String?) -> String {
        guard let raw, !raw.trimmingCharacters(in: .whitespaces).isEmpty else { return "" }
        var s = raw.replacingOccurrences(of: #"<br\s*/?>"#, with: "\n", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
        s = unescape(s)
        return s.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
    }

    /// 엔티티만 풉니다(줄바꿈은 그대로).
    static func unescape(_ s: String) -> String {
        guard s.contains("&") else { return s }
        var out = s
        let named = ["&nbsp;": " ", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&#39;": "'", "&apos;": "'", "&middot;": "·"]
        for (k, v) in named { out = out.replacingOccurrences(of: k, with: v) }
        let re = try! NSRegularExpression(pattern: "&#(x?)([0-9a-fA-F]+);")
        var result = ""
        var last = out.startIndex
        for m in re.matches(in: out, range: NSRange(out.startIndex..., in: out)) {
            guard let r = Range(m.range, in: out), let hexR = Range(m.range(at: 1), in: out), let numR = Range(m.range(at: 2), in: out) else { continue }
            result += out[last..<r.lowerBound]
            let value = UInt32(out[numR], radix: out[hexR].isEmpty ? 10 : 16)
            result += value.flatMap(Unicode.Scalar.init).map { String(Character($0)) } ?? String(out[r])
            last = r.upperBound
        }
        result += out[last...]
        return result.replacingOccurrences(of: "&amp;", with: "&")
    }
}
