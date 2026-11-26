import Foundation
import Observation

// MARK: - 게시글 상세 (포털 JSON)

struct HanaPostFile: Equatable, Identifiable { let name: String; let sizeLabel: String?; let url: String; var id: String { url } }

struct HanaPostDetail: Equatable {
    let title: String
    let writer: String?
    let date: String?
    let views: Int?
    /// 포털 편집기가 만든 본문 HTML — 이미지·표가 들어 있어 웹 보기로 그립니다.
    let html: String
    /// 요약·알림용 순수 텍스트.
    let text: String
    /// 본문 이미지 주소 — 본문이 이미지뿐인 글은 요약 서버가 이미지 속 글자를 읽습니다.
    let images: [String]
    let files: [HanaPostFile]
}

/// 게시글 댓글 한 건 — [replies] 는 그 댓글에 달린 답글(한 단계).
struct HanaComment: Equatable, Identifiable {
    let id: Int
    let writer: String
    let date: String?
    let content: String
    let deleted: Bool
    var replies: [HanaComment] = []
    /// 선생님 답변(포털의 'A' 목록).
    var answer = false
}

enum PostAPI {
    /// 댓글 목록 — 포털 글 화면이 열릴 때 스스로 부르는 조회와 같은 요청. 읽기만 합니다(쓰기·수정·삭제는 앱에 없음).
    static func comments(_ url: String) async throws -> [HanaComment] {
        guard let ids = PostIDs.parse(url) else { return [] }
        let answers = ((try? await reList(url, ids, "R")) ?? []).map { c -> HanaComment in var c = c; c.answer = true; c.replies = []; return c }
        return answers + (try await reList(url, ids, "C"))
    }

    private static func reList(_ url: String, _ ids: (bmt: Int, bd: Int), _ gubun: String) async throws -> [HanaComment] {
        let raw = try await PortalClient.shared.authenticatedText("/main/board/\(ids.bmt)/\(ids.bd)/board_re_list.json", params: [("brtGubun", gubun)], method: "POST", referer: url)
        guard let root = try? JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [String: Any], root.str("result") == "success" else { return [] }
        let list = root.dict("paging")?.array("result") ?? []
        struct Row { let c: HanaComment; let depth: Int; let parent: Int }
        let rows: [Row] = list.map { o in
            Row(c: HanaComment(id: o.int("brt_idx") ?? 0, writer: o.str("brt_writer") ?? "", date: o.str("inputdate").map { String($0.prefix(16)) },
                               content: HTML.unescape((o.str("brt_content") ?? "").replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)).trimmingCharacters(in: .whitespacesAndNewlines),
                               deleted: o.str("brt_del_yn") == "Y"),
                depth: o.int("bd_rep_depth") ?? 0, parent: o.int("bd_ord") ?? 0)
        }
        let replies = Dictionary(grouping: rows.filter { $0.depth > 0 }, by: \.parent).mapValues { $0.map(\.c) }
        return rows.filter { $0.depth == 0 }.map { r in var c = r.c; c.replies = replies[c.id] ?? []; return c }
    }

    /// board_view.do 는 인증된 요청에 JSON(boardVo)으로 답합니다.
    /// 가끔 열리지 않던 것(2026-11-25 제보, 안드로이드와 같은 수정) 대비로 한 번 더 시도합니다 — 응답을 읽지 못하면 새로 로그인한 뒤 다시,
    /// 연결 문제면 잠깐 쉬고 그대로 다시. 비밀번호가 틀렸거나 자동 로그인을 멈춘 상태(5회 실패 잠금 방지)면 다시 로그인하지 않습니다.
    static func detail(_ url: String) async throws -> HanaPostDetail {
        let path = url.hasPrefix(PortalClient.base) ? String(url.dropFirst(PortalClient.base.count)) : url
        func load() async throws -> HanaPostDetail {
            try parse(try await PortalClient.shared.authenticatedText(path, method: "GET", referer: url))
        }
        do {
            return try await load()
        } catch is CancellationError {
            throw CancellationError()
        } catch let e as PortalError where [.missingCredentials, .suspended].contains(e) {
            throw e
        } catch PortalError.loginFailed(let m) {
            throw PortalError.loginFailed(m)
        } catch is URLError {
            try await Task.sleep(for: .milliseconds(600))
            return try await load()
        } catch {
            try await PortalClient.shared.relogin()
            return try await load()
        }
    }

    /// 글이 없는 응답은 화면에 문구가 그대로 보이게 rejected 로(unexpected 는 '학사시스템 응답을 해석하지 못했습니다' 가 앞에 붙음).
    static func parse(_ raw: String) throws -> HanaPostDetail {
        if raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { throw PortalError.rejected("학교 서버가 빈 응답을 보냈습니다") }
        guard let root = try? JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [String: Any] else {
            throw PortalError.rejected("게시글 응답을 읽지 못했습니다")
        }
        guard let vo = root.dict("boardVo") else {
            // 포털이 이유를 문장으로 주면(삭제된 글 등) 그 문장을.
            throw PortalError.rejected(root.str("resMsg").flatMap { $0 == "null" ? nil : $0 } ?? "게시글 응답을 읽지 못했습니다")
        }
        let html = vo.str("bd_content") ?? ""
        let files: [HanaPostFile] = (vo.array("fileList") ?? []).compactMap { f in
            guard let src = f.str("file_src") else { return nil }
            return HanaPostFile(name: f.str("orignl_file_nm") ?? String(src.split(separator: "/").last ?? ""), sizeLabel: f.str("file_size_nm"),
                                url: src.hasPrefix("http") ? src : PortalClient.base + src)
        }
        let imgRe = try! NSRegularExpression(pattern: #"<img[^>]+src\s*=\s*["']([^"']+)["']"#, options: .caseInsensitive)
        var images: [String] = []
        for m in imgRe.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            guard let r = Range(m.range(at: 1), in: html) else { continue }
            let src = HTML.unescape(String(html[r])).trimmingCharacters(in: .whitespaces)
            let full = src.hasPrefix("http") ? src : PortalClient.base + (src.hasPrefix("/") ? src : "/" + src)
            if !images.contains(full) { images.append(full) }
        }
        return HanaPostDetail(title: vo.str("bd_title").map(plainText) ?? "", writer: vo.str("bd_writer") ?? vo.str("in_mem_name"),
                              date: vo.str("inputdate").map { String($0.prefix(16)) }, views: vo.int("bd_view").flatMap { $0 >= 0 ? $0 : nil },
                              html: html, text: plainText(html), images: images, files: files)
    }

    /// 본문 HTML → 문단 줄바꿈만 살린 텍스트.
    static func plainText(_ html: String) -> String {
        var s = html.replacingOccurrences(of: #"(?i)<br\s*/?>"#, with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: #"(?i)</(p|div|li|tr|h[1-6]|table|section)>"#, with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: #"(?is)<(script|style)[^>]*>.*?</\1>"#, with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
        s = HTML.unescape(s).replacingOccurrences(of: "\u{FFFC}", with: " ").replacingOccurrences(of: "\u{00A0}", with: " ")
        s = s.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.joined(separator: "\n")
        s = s.replacingOccurrences(of: #"\n{3,}"#, with: "\n\n", options: .regularExpression)
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// 알림 한 칸에 넣을 본문 앞부분.
    static func excerpt(_ text: String, max: Int = 220) -> String {
        let flat = text.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
        return flat.count <= max ? flat : String(flat.prefix(max)).trimmingCharacters(in: .whitespaces) + "…"
    }
}

// MARK: - AI 요약

/// 목록에 붙는 한 줄([line])과 글 화면 위의 자세한 요약([points]).
struct PostSummary: Codable, Equatable {
    var line: String
    var points: [String]
    var ai = true
    var events: [PostEvent] = []
}

/// 게시글 요약 — 요약 서버가 글 내용마다 한 번만 AI 로 만들어 보관하고, 앱은 받은 요약을 글마다 기기에 저장해 다시 요청하지 않습니다.
@Observable
final class PostSummarizer {
    static let shared = PostSummarizer()
    static let endpoint = URL(string: "https://hihana-summary.kyoohan0711ultra.workers.dev/summarize")!
    /// 이보다 짧은 글은 한 줄 요약만 — 자세한 요약 카드는 숨깁니다.
    private static let minCardChars = 150
    private static let maxInputChars = 6000
    private static let maxCached = 300
    private static let cacheName = "post_summaries_v11"

    enum State: Equatable { case loading, done(PostSummary), failed(String), hidden }

    private struct Box: Codable { var order: [String]; var items: [String: PostSummary] }

    /// 목록 행이 구독하는 한 줄 요약.
    private(set) var lines: [String: String] = [:]
    /// 설정 › 게시판 › AI 요약 — 끄면 어디에도 요약이 보이지 않고 서버로 보내지도 않습니다.
    var enabled: Bool { didSet { SharedContainer.defaults.set(enabled, forKey: "post_summary.enabled") } }
    @ObservationIgnored private var box: Box
    @ObservationIgnored private var inFlight = Set<String>()

    private init() {
        enabled = SharedContainer.defaults.object(forKey: "post_summary.enabled") as? Bool ?? true
        box = JSONCache.read(Box.self, Self.cacheName)?.value ?? Box(order: [], items: [:])
        for (k, v) in box.items where v.ai || !v.points.isEmpty { lines[k] = v.line }
    }

    static func key(_ url: String) -> String {
        if let ids = PostIDs.parse(url), url.contains("/board/") { return "\(ids.bmt)_\(ids.bd)" }
        return url.stableHash
    }

    func cached(_ key: String) -> PostSummary? { box.items[key].flatMap { $0.ai || !$0.points.isEmpty ? $0 : nil } }

    /// 목록 행의 한 줄 요약 — 아직 없으면 nil.
    func line(_ url: String) -> String? {
        guard enabled, let l = lines[Self.key(url)], !l.isEmpty else { return nil }
        return l
    }

    @MainActor
    private func store(_ key: String, _ summary: PostSummary, title: String, url: String?) {
        BoardAgendaStore.shared.record(key: key, title: title, url: url, events: summary.events)
        box.order = box.order.filter { $0 != key } + [key]
        if box.order.count > Self.maxCached {
            for k in box.order.prefix(box.order.count - Self.maxCached) { box.items.removeValue(forKey: k) }
            box.order = Array(box.order.suffix(Self.maxCached))
        }
        box.items[key] = summary
        JSONCache.write(box, Self.cacheName)
        lines[key] = summary.line
    }

    /// 저장된 요약이 있으면 그것을, 없으면 만들어 저장하고 돌려줍니다. [posted] 는 연도 없는 날짜를 어느 해로 읽을지에.
    @MainActor
    func ensure(key: String, title: String, text: String, images: [String] = [], url: String? = nil, posted: String? = nil) async throws -> PostSummary {
        if let c = cached(key) { return c }
        let short = text.count < Self.minCardChars
        let send = short ? images : []
        var summary: PostSummary
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && send.isEmpty {
            summary = PostSummary(line: "", points: [])
        } else {
            summary = try await Self.request(title: title, text: text, images: send, posted: posted)
            if short && send.isEmpty { summary.points = [] }
        }
        store(key, summary, title: title, url: url)
        return summary
    }

    private static func request(title: String, text: String, images: [String], posted: String?) async throws -> PostSummary {
        var req = URLRequest(url: endpoint)
        req.httpMethod = "POST"
        req.timeoutInterval = 90
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.setValue("hihana-neo", forHTTPHeaderField: "x-app")
        let body: [String: Any] = ["title": title, "text": String(text.prefix(maxInputChars)), "images": images,
                                   "posted": posted.map { String($0.prefix(10)) } ?? NSNull()]
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else { throw PortalError.rejected("summary \(code): \(String(decoding: data, as: UTF8.self).prefix(200))") }
        guard var s = decode(data) else { throw PortalError.unexpected("summary unparsable") }
        s.ai = true
        return s
    }

    static func decode(_ data: Data) -> PostSummary? {
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        let line = (o["line"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let points = (o["points"] as? [Any] ?? []).compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        let events: [PostEvent] = (o["events"] as? [[String: Any]] ?? []).compactMap { e in
            guard let date = e.str("date").flatMap(LocalDate.init(iso:)), let title = e.str("title") else { return nil }
            return PostEvent(title: title, date: date, end: e.str("end").flatMap(LocalDate.init(iso:)), time: e.str("time"),
                             deadline: e["deadline"] as? Bool ?? false,
                             grades: Set((e["grades"] as? [Int] ?? []).filter { (1...3).contains($0) }))
        }
        return PostSummary(line: line, points: points, ai: o["ai"] as? Bool ?? false, events: events)
    }

    /// 글 화면용 — 상태를 차례로 알려 줍니다.
    @MainActor
    func summarize(key: String, title: String, text: String, images: [String], url: String?, posted: String?, onState: (State) -> Void) async {
        guard enabled else { onState(.hidden); return }
        if let c = cached(key) { onState(c.points.isEmpty ? .hidden : .done(c)); return }
        if (text.count < Self.minCardChars && images.isEmpty) || (text.isEmpty && images.isEmpty) {
            _ = try? await ensure(key: key, title: title, text: text, url: url, posted: posted)
            onState(.hidden)
            return
        }
        onState(.loading)
        do {
            let s = try await ensure(key: key, title: title, text: text, images: images, url: url, posted: posted)
            onState(s.points.isEmpty ? .hidden : .done(s))
        } catch {
            onState(.failed(error.localizedDescription.contains("429") ? "요약 요청이 많아 잠시 뒤 다시 시도해 주세요" : "요약을 만들지 못했습니다"))
        }
    }

    /// 목록에 보이는 글들의 한 줄 요약을 미리 — 이미 있는 글은 건너뛰고 네 건씩 함께 받습니다.
    @MainActor
    func prefetch(_ posts: [HanaBoardPost]) async {
        guard enabled, !CommandLine.arguments.contains("-demo") else { return }
        let todo = posts.filter { let k = Self.key($0.url); return lines[k] == nil && !inFlight.contains(k) }
        if todo.isEmpty { return }
        await PortalClient.shared.ensureLoggedIn()
        for chunk in stride(from: 0, to: todo.count, by: 4) {
            await withTaskGroup(of: Void.self) { group in
                for post in todo[chunk..<min(chunk + 4, todo.count)] {
                    let key = Self.key(post.url)
                    guard lines[key] == nil, inFlight.insert(key).inserted else { continue }
                    group.addTask { @MainActor in
                        defer { self.inFlight.remove(key) }
                        guard let d = try? await PostAPI.detail(post.url) else { return }
                        _ = try? await self.ensure(key: key, title: d.title.isEmpty ? post.title : d.title, text: d.text, images: d.images, url: post.url, posted: d.date ?? post.date)
                    }
                }
            }
        }
    }
}
