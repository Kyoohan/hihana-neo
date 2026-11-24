import Foundation

/*
 * 원격 공지 — 안드로이드 RemoteNotice.kt 와 같은 서버 공지(summary-worker /notice, `node notice.mjs` 로 올림)를 앱을 열 때 한 번 띄웁니다.
 * 업데이트가 아니어도 중요한 안내를 알리려는 것. 닫은 공지(id)는 다시 띄우지 않습니다.
 */
struct RemoteNotice: Identifiable, Equatable {
    let id: String
    let title: String
    let body: String
    let link: URL?
    let button: String?
}

enum RemoteNoticeStore {
    private static let endpoint = URL(string: "https://hihana-summary.kyoohan0711ultra.workers.dev/notice")!
    private static let checkedKey = "remote_notice.checked"
    private static let seenKey = "remote_notice.seen"
    /// 복귀할 때마다 묻지 않게 — 공지는 몇 분 늦어도 됩니다.
    private static let interval: TimeInterval = 15 * 60
    private static let keepSeen = 20
    /// iOS 판을 되살린 날 — 그 전에 올린 공지는 안드로이드 앱 이야기(예: 14.4 업데이트 안내)라 아이폰에는 띄우지 않습니다.
    private static let iosSince = LocalDate(year: 2026, month: 11, day: 19)

    /// 띄울 공지 — 없거나 이미 닫았거나, 기한이 지났거나, 이 앱이 대상이 아니면 nil. 15분 안에 물어봤으면 묻지 않습니다.
    static func pending(now: Date = Date()) async -> RemoteNotice? {
        let d = SharedContainer.defaults
        if now.timeIntervalSince1970 - d.double(forKey: checkedKey) < interval { return nil }
        var req = URLRequest(url: endpoint)
        req.timeoutInterval = 8
        req.setValue("hihana-neo", forHTTPHeaderField: "x-app")
        guard let (data, resp) = try? await URLSession.shared.data(for: req), let code = (resp as? HTTPURLResponse)?.statusCode,
              code == 200 || code == 204 else { return nil }
        d.set(now.timeIntervalSince1970, forKey: checkedKey)
        guard code == 200, let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any], let notice = parse(o, today: now.localDate) else { return nil }
        return seen().contains(notice.id) ? nil : notice
    }

    static func markSeen(_ id: String) {
        var list = seen().filter { $0 != id }
        list.append(id)
        SharedContainer.defaults.set(Array(list.suffix(keepSeen)), forKey: seenKey)
    }

    private static func seen() -> [String] { SharedContainer.defaults.stringArray(forKey: seenKey) ?? [] }

    private static var version: String { (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "0" }

    static func parse(_ o: [String: Any], today: LocalDate, version: String = version) -> RemoteNotice? {
        guard let id = (o["id"] as? String).flatMap({ $0.isEmpty ? nil : $0 }),
              let title = (o["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !title.isEmpty else { return nil }
        // 대상 — dev·alpha·public 은 안드로이드 빌드 구분이라, 아이폰은 '모두에게'(대상 없음)만 띄웁니다.
        guard ((o["to"] as? String) ?? "").isEmpty else { return nil }
        if let until = (o["until"] as? String).flatMap(LocalDate.init(iso:)), today > until { return nil }
        // 버전 대상 — "below": "14.5" 면 14.5 보다 낮은 앱에만(이미 그 버전 이상이면 '업데이트하세요' 안내가 맞지 않음).
        if let below = (o["below"] as? String).flatMap({ $0.isEmpty ? nil : $0 }), !AppUpdater.isNewer(below, than: version) { return nil }
        let posted = ISO8601DateFormatter().date(from: id)?.localDate
        if let posted, posted < iosSince { return nil }
        let link = (o["link"] as? String).flatMap { $0.hasPrefix("https://") ? URL(string: $0) : nil }
        let button = link == nil ? nil : ((o["button"] as? String)?.trimmingCharacters(in: .whitespaces)).flatMap { $0.isEmpty ? nil : $0 } ?? "열기"
        return RemoteNotice(id: id, title: title, body: ((o["body"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines), link: link, button: button)
    }
}
