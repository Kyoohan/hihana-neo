import Foundation
import Observation
import CryptoKit
import Compression

/*
 * 구글 클래스룸 연동 — 안드로이드 GoogleClassroom.kt 와 같은 방식.
 *
 * 학교 Google 계정은 만 18세 미만이라 외부 앱의 Google 로그인이 막혀 있습니다. 대신 같은 학교 계정으로 만든 Apps Script 웹 앱
 * (classroom-bridge/Code.gs)이 학생 본인 권한으로 15분마다 클래스룸을 읽어 암호화해 중계 서버(summary-worker /bridge)에 올리고,
 * 앱은 그걸 받아 풉니다. 비밀키는 기기에서 만들어 스크립트에만 건넵니다 — 서버에는 id 와 암호문만 있습니다.
 */

struct ClassroomError: LocalizedError { let message: String; var needsLogin = false; var errorDescription: String? { message } }

enum ClassroomKind: String, Codable { case announcement = "ANNOUNCEMENT", work = "WORK", material = "MATERIAL"
    var label: String { switch self { case .announcement: "공지"; case .work: "과제"; case .material: "자료" } }
}

struct ClassroomCourse: Codable, Equatable, Identifiable { let id: String; let name: String; let section: String?; let link: String? }
struct ClassroomAttachment: Codable, Equatable { let title: String; let url: String }

/// 클래스룸 스트림의 글 하나 — 공지·과제·자료를 한 꼴로. 과제만 마감·제출이 있습니다.
struct ClassroomPost: Codable, Equatable, Identifiable {
    let id: String
    let courseId: String
    let kind: ClassroomKind
    let title: String
    let text: String
    let link: String?
    let created: Date
    let updated: Date
    var due: Date? = nil
    /// 마감 시각 없이 날짜만 — 그날 23:59 로 둡니다.
    var dueAllDay = false
    var maxPoints: Double? = nil
    var submitted = false
    var late = false
    var grade: Double? = nil
    var attachments: [ClassroomAttachment] = []

    /// 과제인데 아직 안 냈고 마감이 있으면 그 시각.
    var pendingDue: Date? { kind == .work && !submitted ? due : nil }
}

struct ClassroomSnapshot: Codable, Equatable {
    var courses: [ClassroomCourse]
    var posts: [ClassroomPost]
    var fetchedAt: Date
    /// 학교 계정 스크립트가 이 내용을 올린 시각(payload 의 at) — 중계 서버에서 받은 것만, 샘플·옛 캐시는 nil.
    var syncedAt: Date? = nil
    /// 과제를 읽을 권한이 빠져 다시 허용해야 하는지(payload 의 reconnect) — 2026-11-10 전에 연결한 학생은 교사용 과제 권한만 받아
    /// 과제가 모든 수업에서 403 이었습니다. 다시 허용하면 스크립트가 표시를 거둡니다. (옛 캐시와 맞게 옵셔널로 둡니다.)
    var reconnect: Bool? = nil

    var needsReconnect: Bool { reconnect == true }

    /// 스크립트가 5분마다 올리는데 이보다 오래 새로 오지 않으면 멈춘 것 — 다른 기기에서 연결하며 밀려났거나 트리거가 사라진 경우.
    static let staleAfter: TimeInterval = 3 * 3600

    var stale: Bool { syncedAt.map { Date().timeIntervalSince($0) > Self.staleAfter } ?? false }

    /// "12분 전 동기화" · "3시간 전 동기화" · "10월 24일 동기화".
    func syncedLabel(now: Date = Date()) -> String? {
        guard let at = syncedAt else { return nil }
        let minutes = Int(now.timeIntervalSince(at) / 60)
        if minutes < 1 { return "방금 동기화" }
        if minutes < 60 { return "\(minutes)분 전 동기화" }
        if minutes < 24 * 60 { return "\(minutes / 60)시간 전 동기화" }
        let d = at.localDate
        return "\(d.month)월 \(d.day)일 동기화"
    }

    func course(_ id: String) -> ClassroomCourse? { courses.first { $0.id == id } }

    /// 숨긴 수업을 뺀 스냅샷.
    func visible(_ hidden: Set<String>) -> ClassroomSnapshot {
        guard !hidden.isEmpty else { return self }
        var out = self
        out.courses = courses.filter { !hidden.contains($0.id) }
        out.posts = posts.filter { !hidden.contains($0.courseId) }
        return out
    }

    /// 통합 일정용 — 안 낸 과제의 마감.
    func agenda() -> [AgendaItem] {
        posts.compactMap { p in
            guard let due = p.pendingDue else { return nil }
            return AgendaItem(id: "c:\(p.courseId):\(p.id)", source: .classroom, title: p.title, date: due.localDate,
                              time: p.dueAllDay ? nil : due.hm, deadline: true, detail: course(p.courseId)?.name, url: p.link)
        }
    }
}

// MARK: - 연결

@Observable
final class ClassroomBridge {
    static let shared = ClassroomBridge()

    /// 학교 계정으로 배포한 웹 앱.
    static let scriptURL = "https://script.google.com/a/macros/hana.hs.kr/s/AKfycbxGrulUZa_ezE-akmMgvEh3njF2YWj8XuHuoOUQMKnsKJEdHIpePj-3th3Vo6Rqi1LS/exec"
    private static let worker = "https://hihana-summary.kyoohan0711ultra.workers.dev/bridge/"

    /// 연결돼 내용을 한 번이라도 받았는지.
    private(set) var connected = false
    /// 브라우저에서 허용하러 갔고 아직 첫 내용을 못 받은 상태.
    private(set) var pending = false
    private(set) var email: String?

    private init() { refreshFlags() }

    private var secret: String? { Keychain.get("classroom.secret") }
    private var confirmed: Bool { SharedContainer.defaults.bool(forKey: "classroom.confirmed") }

    func refreshFlags() {
        connected = confirmed && secret != nil
        pending = !connected && secret != nil
        email = SharedContainer.defaults.string(forKey: "classroom.email")
    }

    /// 웹 앱 주소 — 학생이 허용하면 스크립트가 비밀키를 받아 바로 한 번 올립니다. 다시 열면 같은 비밀키로.
    func signInURL() -> URL {
        let s = secret ?? {
            var bytes = [UInt8](repeating: 0, count: 32)
            _ = SecRandomCopyBytes(kSecRandomDefault, 32, &bytes)
            let v = Data(bytes).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            _ = Keychain.set(v, for: "classroom.secret")
            return v
        }()
        pending = !confirmed
        var c = URLComponents(string: Self.scriptURL)!
        c.queryItems = [URLQueryItem(name: "k", value: s), URLQueryItem(name: "p", value: "ios")]
        // 브라우저에 학교 계정 로그인이 없으면(개인 계정만 있거나 아무것도 없으면) 웹 앱이 열리지 않았습니다 — Google 계정 고르기를
        // 거쳐 학교 도메인(hd) 로그인 화면부터 띄운 뒤 웹 앱으로 이어 가게 합니다.
        var chooser = URLComponents(string: "https://accounts.google.com/AccountChooser")!
        chooser.queryItems = [URLQueryItem(name: "continue", value: c.url!.absoluteString), URLQueryItem(name: "hd", value: "hana.hs.kr")]
        return chooser.url!
    }

    /// 중계 서버에서 암호문을 받아 풉니다. 처음 받으면 연결됨으로 바꿉니다.
    func fetch() async throws -> ClassroomSnapshot {
        guard let secret else { throw ClassroomError(message: "Google 클래스룸이 연결되어 있지 않습니다", needsLogin: true) }
        let keys = try Keys(secret)
        var req = URLRequest(url: URL(string: Self.worker + keys.id)!)
        req.timeoutInterval = 30
        req.setValue("hihana-neo", forHTTPHeaderField: "x-app")
        let (data, resp) = try await URLSession.shared.data(for: req)
        switch (resp as? HTTPURLResponse)?.statusCode ?? 0 {
        case 200: break
        case 404:
            throw ClassroomError(message: confirmed ? "중계 서버에 받은 내용이 없습니다. 잠시 뒤 다시 확인해 주세요"
                                 : "아직 받은 내용이 없습니다. 브라우저에서 '허용'까지 마쳤다면 잠시 뒤 다시 확인해 주세요")
        case 410:
            clear()
            throw ClassroomError(message: "클래스룸 연결이 끊겼습니다. 다시 연결해 주세요", needsLogin: true)
        case let code: throw ClassroomError(message: "bridge \(code)")
        }
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any], let b64 = o["data"] as? String,
              let sealed = Data(base64Encoded: b64, options: .ignoreUnknownCharacters) else { throw ClassroomError(message: "클래스룸 응답을 읽지 못했습니다") }
        let plain = try Gzip.decompress(try keys.open(sealed))
        guard let payload = try JSONSerialization.jsonObject(with: plain) as? [String: Any] else { throw ClassroomError(message: "클래스룸 응답을 읽지 못했습니다") }
        let snapshot = ClassroomAPI.parse(payload)
        SharedContainer.defaults.set(true, forKey: "classroom.confirmed")
        SharedContainer.defaults.set(payload.str("email"), forKey: "classroom.email")
        refreshFlags()
        return snapshot
    }

    /// 중계 서버의 암호문을 지우게 하고 기기의 비밀키·캐시를 지웁니다.
    func disconnect() async {
        if let secret, let keys = try? Keys(secret) {
            var req = URLRequest(url: URL(string: Self.worker + keys.id)!)
            req.httpMethod = "DELETE"
            req.setValue("hihana-neo", forHTTPHeaderField: "x-app")
            _ = try? await URLSession.shared.data(for: req)
        }
        clear()
    }

    private func clear() {
        Keychain.remove("classroom.secret")
        SharedContainer.defaults.removeObject(forKey: "classroom.confirmed")
        SharedContainer.defaults.removeObject(forKey: "classroom.email")
        refreshFlags()
        ClassroomStore.shared.clear()
    }

    /// 스크립트의 derive/seal 과 짝 — 비밀키 S 에서 id = HMAC(S,"id") 앞 16바이트(hex), enc·mac 키를 만들고,
    /// 봉투(nonce 16 ‖ 암호문 ‖ 태그 32)의 태그를 확인한 뒤 HMAC 키스트림으로 풉니다.
    private struct Keys {
        let id: String
        private let enc: SymmetricKey
        private let mac: SymmetricKey

        init(_ secret: String) throws {
            var b64 = secret.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            while b64.count % 4 != 0 { b64 += "=" }
            guard let s = Data(base64Encoded: b64) else { throw ClassroomError(message: "비밀키를 읽지 못했습니다", needsLogin: true) }
            let key = SymmetricKey(data: s)
            func h(_ label: String) -> Data { Data(HMAC<SHA256>.authenticationCode(for: Data(label.utf8), using: key)) }
            id = h("id").prefix(16).map { String(format: "%02x", $0) }.joined()
            enc = SymmetricKey(data: h("enc"))
            mac = SymmetricKey(data: h("mac"))
        }

        func open(_ sealed: Data) throws -> Data {
            guard sealed.count >= 48 else { throw ClassroomError(message: "bridge payload too short") }
            let bytes = [UInt8](sealed)
            let nonce = Data(bytes[0..<16])
            let cipher = Data(bytes[16..<(bytes.count - 32)])
            let tag = Data(bytes[(bytes.count - 32)...])
            var m = HMAC<SHA256>(key: mac)
            m.update(data: nonce); m.update(data: cipher)
            guard Data(m.finalize()) == tag else { throw ClassroomError(message: "bridge payload tampered") }
            var out = [UInt8](repeating: 0, count: cipher.count)
            let c = [UInt8](cipher)
            var block: UInt32 = 0
            var i = 0
            while i < c.count {
                var e = HMAC<SHA256>(key: enc)
                e.update(data: nonce)
                e.update(data: Data([UInt8(block >> 24 & 0xFF), UInt8(block >> 16 & 0xFF), UInt8(block >> 8 & 0xFF), UInt8(block & 0xFF)]))
                let ks = [UInt8](Data(e.finalize()))
                for j in 0..<min(32, c.count - i) { out[i + j] = c[i + j] ^ ks[j] }
                i += 32; block += 1
            }
            return Data(out)
        }
    }
}

/// gzip 풀기 — 헤더를 걷어 내고 Compression 의 DEFLATE 로.
enum Gzip {
    static func decompress(_ data: Data) throws -> Data {
        let b = [UInt8](data)
        guard b.count > 18, b[0] == 0x1f, b[1] == 0x8b, b[2] == 8 else { throw ClassroomError(message: "gzip 형식이 아닙니다") }
        let flags = b[3]
        var i = 10
        if flags & 4 != 0 { i += 2 + (Int(b[i]) | Int(b[i + 1]) << 8) }
        if flags & 8 != 0 { while i < b.count && b[i] != 0 { i += 1 }; i += 1 }
        if flags & 16 != 0 { while i < b.count && b[i] != 0 { i += 1 }; i += 1 }
        if flags & 2 != 0 { i += 2 }
        let deflated = Data(b[i..<(b.count - 8)])
        let size = Int(b[b.count - 4]) | Int(b[b.count - 3]) << 8 | Int(b[b.count - 2]) << 16 | Int(b[b.count - 1]) << 24
        let capacity = max(size, deflated.count * 8) + 1024
        var out = Data(count: capacity)
        let written = out.withUnsafeMutableBytes { dst in
            deflated.withUnsafeBytes { src in
                compression_decode_buffer(dst.bindMemory(to: UInt8.self).baseAddress!, capacity, src.bindMemory(to: UInt8.self).baseAddress!, deflated.count, nil, COMPRESSION_ZLIB)
            }
        }
        guard written > 0 else { throw ClassroomError(message: "압축을 풀지 못했습니다") }
        return out.prefix(written)
    }
}

// MARK: - 읽기

enum ClassroomAPI {
    /// 공지에서 일정을 뽑는 범위 — 최근 30일, 한 번에 25건까지(요약 서버 하루 사용량 보호).
    private static let eventDays = 30
    private static let eventBatch = 25

    private static let iso: ISO8601DateFormatter = { let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f }()
    private static let isoPlain = ISO8601DateFormatter()

    private static func time(_ raw: String?) -> Date { raw.flatMap { iso.date(from: $0) ?? isoPlain.date(from: $0) } ?? Date() }

    /// 스크립트가 보낸 꼴(Code.gs 의 collect) → 스냅샷. 마감 시각은 UTC 라 한국 시각으로.
    static func parse(_ o: [String: Any]) -> ClassroomSnapshot {
        let courses = (o.array("courses") ?? []).compactMap { c -> ClassroomCourse? in
            guard let id = c.str("id") else { return nil }
            return ClassroomCourse(id: id, name: c.str("name") ?? "", section: c.str("section"), link: c.str("link"))
        }
        let posts: [ClassroomPost] = (o.array("posts") ?? []).compactMap { p in
            // 스크립트가 예전 앱을 위해 끼워 넣는 '다시 허용' 안내 글(Code.gs NOTICE_ID) — 이 앱은 대신 배너를 띄워 글은 뺍니다.
            if p.str("id") == reconnectNoticeID { return nil }
            let kind: ClassroomKind
            switch p.str("k") { case "A": kind = .announcement; case "W": kind = .work; case "M": kind = .material; default: return nil }
            guard let id = p.str("id"), let course = p.str("c") else { return nil }
            let text = (p["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            var due: Date?
            var allDay = false
            if let d = p.dict("due"), let y = d.int("y"), let m = d.int("m"), let dd = d.int("d") {
                allDay = d["h"] == nil || d["h"] is NSNull
                if allDay {
                    due = LocalDate(year: y, month: m, day: dd).at(minutes: 23 * 60 + 59)
                } else {
                    var utc = Calendar(identifier: .gregorian); utc.timeZone = TimeZone(identifier: "UTC")!
                    due = utc.date(from: DateComponents(year: y, month: m, day: dd, hour: d.int("h") ?? 0, minute: d.int("mi") ?? 0))
                }
            }
            let state = p.str("st")
            let title = kind == .announcement
                ? (text.split(separator: "\n").first { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.map { String($0.trimmingCharacters(in: .whitespaces).prefix(80)) } ?? "(내용 없음)")
                : (p.str("title") ?? "(제목 없음)")
            return ClassroomPost(
                id: id, courseId: course, kind: kind, title: title, text: text, link: p.str("link"),
                created: time(p.str("cr")), updated: time(p.str("up")), due: due, dueAllDay: due != nil && allDay,
                maxPoints: (p["pts"] as? NSNumber)?.doubleValue, submitted: state == "TURNED_IN" || state == "RETURNED",
                late: p["late"] as? Bool ?? false, grade: (p["grade"] as? NSNumber)?.doubleValue,
                attachments: (p.array("att") ?? []).compactMap { a in a.str("u").map { ClassroomAttachment(title: a.str("t") ?? $0, url: $0) } })
        }
        let at = (o["at"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1000) }
        return ClassroomSnapshot(courses: courses, posts: posts.sorted { $0.updated > $1.updated }, fetchedAt: Date(), syncedAt: at,
                                 reconnect: !(o.str("reconnect") ?? "").isEmpty)
    }

    private static let reconnectNoticeID = "hihana-reconnect"

    /// 선생님이 글로만 올린 공지에서 AI 로 일정을 뽑아 통합 일정에(키 cr_). 요약을 꺼 두면 보내지 않습니다.
    @MainActor
    static func extractEvents(_ snapshot: ClassroomSnapshot) async {
        guard PostSummarizer.shared.enabled else { return }
        let since = Date().addingTimeInterval(TimeInterval(-eventDays * 86400))
        let targets = snapshot.posts.filter { $0.kind == .announcement && $0.created > since && !$0.text.isEmpty && PostSummarizer.shared.cached(eventKey($0)) == nil }.prefix(eventBatch)
        for post in targets {
            let course = snapshot.course(post.courseId)?.name ?? "클래스룸"
            _ = try? await PostSummarizer.shared.ensure(key: eventKey(post), title: "\(course) 공지", text: post.text, url: post.link, posted: post.created.localDate.iso)
        }
    }

    static func eventKey(_ p: ClassroomPost) -> String { "cr_\(p.id)" }
}

// MARK: - 보관

/// 마지막 스냅샷·숨긴 수업·읽음·숨긴 과제 — 앱을 열면 바로 보여 주고, 2분 지났으면 뒤에서 새로 받습니다.
@Observable
final class ClassroomStore {
    static let shared = ClassroomStore()
    // 10분 → 2분 — 스크립트가 5분마다 올리는데 앱이 10분 동안 옛 사본을 들고 있어 바뀐 게 늦게 보였습니다(안드로이드와 같게).
    static let ttl: TimeInterval = 2 * 60

    private(set) var snapshot: ClassroomSnapshot?
    /// 숨긴 수업 id — 새로 생긴 수업은 기본으로 보임.
    var hidden: Set<String> { didSet { SharedContainer.defaults.set(Array(hidden), forKey: "classroom.hidden") } }
    /// 읽은 글 id — 바뀔 때마다 화면이 따라 그립니다.
    private(set) var readIDs: Set<String>
    /// 할 일에서 손으로 숨긴 과제 — 마감이 없거나 지났는데 클래스룸에선 계속 '안 냄'으로 남는 것(제출이 필요 없던 과제 등).
    /// 이 기기에만 적고 클래스룸에는 아무것도 보내지 않습니다.
    private(set) var hiddenTodo: Set<String>
    /// 할 일을 펼쳐 두었는지 — 앱을 켤 때마다 접힌 채로 시작하고, 화면이 다시 만들어져도 유지되게 화면 밖에 둡니다.
    var todoOpen = false
    /// Dev 의 '샘플 데이터' — 네트워크·연결 없이 화면만.
    private(set) var sampleMode = false

    private init() {
        snapshot = JSONCache.read(ClassroomSnapshot.self, "classroom_snapshot")?.value
        hidden = Set(SharedContainer.defaults.stringArray(forKey: "classroom.hidden") ?? [])
        readIDs = Set(SharedContainer.defaults.stringArray(forKey: "classroom.read") ?? [])
        hiddenTodo = Set(SharedContainer.defaults.stringArray(forKey: "classroom.todoHidden") ?? [])
    }

    func hideTodo(_ id: String) {
        hiddenTodo.insert(id)
        SharedContainer.defaults.set(Array(hiddenTodo), forKey: "classroom.todoHidden")
    }

    func showTodo(_ ids: Set<String>) {
        hiddenTodo.subtract(ids)
        SharedContainer.defaults.set(Array(hiddenTodo), forKey: "classroom.todoHidden")
    }

    /// 숨긴 수업을 뺀 스냅샷.
    var visible: ClassroomSnapshot? { snapshot?.visible(hidden) }

    func save(_ s: ClassroomSnapshot) {
        snapshot = s
        JSONCache.write(s, "classroom_snapshot")
    }

    func clear() {
        snapshot = nil
        sampleMode = false
        JSONCache.remove("classroom_snapshot")
        readIDs = []
        hiddenTodo = []
        SharedContainer.defaults.removeObject(forKey: "classroom.read")
        SharedContainer.defaults.removeObject(forKey: "classroom.todoHidden")
        SharedContainer.defaults.removeObject(forKey: "classroom.readSeeded")
        BoardAgendaStore.shared.clear(prefix: "cr_")
    }

    func unreadCount(_ posts: [ClassroomPost]) -> Int { posts.filter { !readIDs.contains($0.id) }.count }

    func markRead(_ posts: [ClassroomPost]) {
        readIDs.formUnion(posts.map(\.id))
        SharedContainer.defaults.set(Array(readIDs), forKey: "classroom.read")
    }

    /// 처음 연결할 땐 그때까지의 글을 모두 읽은 것으로 — 배지가 수십 개로 시작하지 않게.
    func seedReadIfNeeded(_ posts: [ClassroomPost]) {
        guard !SharedContainer.defaults.bool(forKey: "classroom.readSeeded") else { return }
        readIDs = Set(posts.map(\.id))
        SharedContainer.defaults.set(Array(readIDs), forKey: "classroom.read")
        SharedContainer.defaults.set(true, forKey: "classroom.readSeeded")
    }

    /// 연결된 것처럼 보이는 샘플(수업 둘, 공지·과제·자료)과 게시글 일정 두 건.
    func installSample() {
        let now = Date()
        func d(_ days: Double, _ hour: Int? = nil, _ minute: Int = 0) -> Date {
            let base = now.addingTimeInterval(days * 86400)
            guard let hour else { return base }
            return base.localDate.at(minutes: hour * 60 + minute)
        }
        let courses = [ClassroomCourse(id: "c1", name: "2학년 수학Ⅱ", section: "2-3", link: "https://classroom.google.com/"),
                       ClassroomCourse(id: "c2", name: "영어 독해와 작문", section: nil, link: "https://classroom.google.com/")]
        let posts = [
            ClassroomPost(id: "w1", courseId: "c1", kind: .work, title: "수열의 극한 연습문제 3단원", text: "교과서 p.112~118 홀수 번 풀어서 사진으로 제출", link: "https://classroom.google.com/",
                          created: d(-2), updated: d(-2), due: d(2, 23, 59), maxPoints: 10),
            ClassroomPost(id: "w2", courseId: "c2", kind: .work, title: "Unit 5 Writing: My Future Career", text: "200 words, double-spaced. Submit as Google Docs.", link: "https://classroom.google.com/",
                          created: d(-5), updated: d(-1), due: d(6, 9), maxPoints: 20, submitted: true, grade: 18),
            ClassroomPost(id: "w3", courseId: "c1", kind: .work, title: "모의고사 오답노트", text: "", link: "https://classroom.google.com/",
                          created: d(-9), updated: d(-9), due: d(-1, 18), maxPoints: 5),
            ClassroomPost(id: "a1", courseId: "c1", kind: .announcement, title: "다음 주 수행평가 범위 안내", text: "다음 주 수행평가 범위 안내\n3단원 전체입니다. 공식 정리본은 자료에 올렸으니 참고하세요.\nhttps://classroom.google.com/",
                          link: "https://classroom.google.com/", created: now.addingTimeInterval(-5 * 3600), updated: now.addingTimeInterval(-5 * 3600)),
            ClassroomPost(id: "m1", courseId: "c1", kind: .material, title: "3단원 공식 정리본", text: "시험 전까지 꼭 한 번 읽어 두세요.", link: "https://classroom.google.com/",
                          created: d(-1), updated: d(-1), attachments: [ClassroomAttachment(title: "3단원_공식정리.pdf", url: "https://drive.google.com/")]),
        ].sorted { $0.updated > $1.updated }
        snapshot = ClassroomSnapshot(courses: courses, posts: posts, fetchedAt: now)
        sampleMode = true
        let today = LocalDate.today()
        BoardAgendaStore.shared.record(key: "sample_1", title: "2학기 교내 수학경시대회 참가 신청 안내", url: "https://hi.hana.hs.kr/main/board/1/9001/view.do",
                                       events: [PostEvent(title: "수학경시대회 신청 마감", date: today.adding(days: 3), deadline: true),
                                                PostEvent(title: "수학경시대회", date: today.adding(days: 10), time: "15:10")])
        BoardAgendaStore.shared.record(key: "sample_2", title: "학부모 상담 주간 안내", url: "https://hi.hana.hs.kr/main/board/4/9002/view.do",
                                       events: [PostEvent(title: "학부모 상담 주간", date: today.adding(days: 13), end: today.adding(days: 17))])
    }
}
