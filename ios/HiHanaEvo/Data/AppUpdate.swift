import Foundation
import Observation
import UIKit

/*
 * 앱 안 업데이트 — 안드로이드 AppUpdater 처럼 공개 저장소의 최신 릴리스(releases/latest)를 읽어, 설치된 것보다 새 버전이면 알립니다.
 * 아이폰은 앱이 스스로 설치할 수 없어 SideStore 에 맡깁니다: `sidestore://install?url=<IPA>` 를 열면 SideStore 가 받아
 * 같은 Apple ID 로 다시 서명해 기존 앱 위에 설치합니다(데이터 그대로). SideStore 가 없으면 릴리스 페이지를 엽니다.
 * 친구들이 SideStore 에 'HiHana 소스'(ios/source.json)를 한 번 추가해 두면 SideStore 안에서도 업데이트가 뜹니다.
 */
struct AppUpdate: Equatable {
    let version: String
    let ipa: URL
    let page: URL
    let notes: String
}

@Observable
final class AppUpdater {
    static let shared = AppUpdater()
    private static let latestURL = URL(string: "https://api.github.com/repos/Kyoohan/hihana-neo/releases/latest")!
    /// SideStore 소스 — 공개 저장소에 둔 앱 목록(버전·IPA 주소). 한 번 추가하면 SideStore 가 알아서 새 버전을 보여 줍니다.
    static let sourceURL = URL(string: "https://raw.githubusercontent.com/Kyoohan/hihana-neo/main/ios/source.json")!
    private static let checkedKey = "update.checked"
    private static let dismissedKey = "update.dismissed"
    /// 열 때마다 GitHub 에 묻지 않게 — 6시간에 한 번(설정의 '업데이트 확인'은 바로).
    private static let interval: TimeInterval = 6 * 3600

    private(set) var available: AppUpdate?
    private(set) var checking = false
    private(set) var lastError: String?

    static var installedVersion: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0" }

    func check(force: Bool = false) async {
        let d = SharedContainer.defaults
        let now = Date().timeIntervalSince1970
        if !force && now - d.double(forKey: Self.checkedKey) < Self.interval { return }
        checking = true
        defer { checking = false }
        var req = URLRequest(url: Self.latestURL)
        req.timeoutInterval = 10
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue("HiHanaEvo-iOS/\(Self.installedVersion)", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200,
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            lastError = "업데이트를 확인하지 못했습니다"
            return
        }
        lastError = nil
        d.set(now, forKey: Self.checkedKey)
        available = Self.parse(o, installed: Self.installedVersion)
    }

    /// 릴리스 응답 → 설치된 것보다 새 버전이고 IPA 가 붙어 있으면 그 업데이트.
    static func parse(_ o: [String: Any], installed: String) -> AppUpdate? {
        guard let tag = o["tag_name"] as? String else { return nil }
        let version = tag.hasPrefix("v") ? String(tag.dropFirst()) : tag
        guard isNewer(version, than: installed) else { return nil }
        let assets = o["assets"] as? [[String: Any]] ?? []
        guard let ipa = assets.first(where: { ($0["name"] as? String)?.lowercased().hasSuffix(".ipa") == true })
                .flatMap({ ($0["browser_download_url"] as? String).flatMap(URL.init(string:)) }) else { return nil }
        let page = (o["html_url"] as? String).flatMap(URL.init(string:)) ?? URL(string: "https://github.com/Kyoohan/hihana-neo/releases/latest")!
        return AppUpdate(version: version, ipa: ipa, page: page, notes: ((o["body"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
    }

    /// "14.10" > "14.9" 처럼 점으로 나눈 숫자를 차례로 비교합니다.
    static func isNewer(_ a: String, than b: String) -> Bool {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }, y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p > q }
        }
        return false
    }

    /// 앱을 열 때 띄울 업데이트 — '나중에'를 누른 버전은 다시 띄우지 않습니다(설정 › 정보에는 계속 보임).
    var prompt: AppUpdate? {
        guard let available, SharedContainer.defaults.string(forKey: Self.dismissedKey) != available.version else { return nil }
        return available
    }

    func dismiss(_ u: AppUpdate) { SharedContainer.defaults.set(u.version, forKey: Self.dismissedKey) }

    static func installLink(_ u: AppUpdate) -> URL {
        var c = URLComponents(string: "sidestore://install")!
        c.queryItems = [URLQueryItem(name: "url", value: u.ipa.absoluteString)]
        return c.url!
    }

    static var addSourceLink: URL {
        var c = URLComponents(string: "sidestore://source")!
        c.queryItems = [URLQueryItem(name: "url", value: sourceURL.absoluteString)]
        return c.url!
    }

    static var hasSideStore: Bool { UIApplication.shared.canOpenURL(URL(string: "sidestore://")!) }

    /// SideStore 로 설치 — 없으면 릴리스 페이지(파일을 받아 직접 설치).
    @MainActor
    static func install(_ u: AppUpdate) {
        UIApplication.shared.open(hasSideStore ? installLink(u) : u.page)
    }
}
