import XCTest
@testable import HiHanaEvo

final class AppUpdateTests: XCTestCase {
    func testVersionOrder() {
        XCTAssertTrue(AppUpdater.isNewer("14.10", than: "14.9"))
        XCTAssertTrue(AppUpdater.isNewer("15.0", than: "14.4"))
        XCTAssertFalse(AppUpdater.isNewer("14.4", than: "14.4"))
        XCTAssertFalse(AppUpdater.isNewer("14.3", than: "14.4"))
    }

    func testParsePicksIPAOnlyWhenNewer() {
        let o: [String: Any] = ["tag_name": "v14.5", "html_url": "https://github.com/Kyoohan/hihana-neo/releases/tag/v14.5", "body": "고침\n둘째 줄",
                                "assets": [["name": "hihana-neo-14.5.apk", "browser_download_url": "https://x/a.apk"],
                                           ["name": "hihana-neo-14.5-ios.ipa", "browser_download_url": "https://x/b.ipa"]]]
        let u = AppUpdater.parse(o, installed: "14.4")
        XCTAssertEqual(u?.version, "14.5")
        XCTAssertEqual(u?.ipa.absoluteString, "https://x/b.ipa")
        XCTAssertNil(AppUpdater.parse(o, installed: "14.5"))
        var noIPA = o; noIPA["assets"] = [["name": "a.apk", "browser_download_url": "https://x/a.apk"]]
        XCTAssertNil(AppUpdater.parse(noIPA, installed: "14.4"), "안드로이드만 올린 릴리스는 아이폰에 알리지 않습니다")
    }

    func testInstallLinkEncodesURL() {
        let u = AppUpdate(version: "14.5", ipa: URL(string: "https://github.com/Kyoohan/hihana-neo/releases/download/v14.5/hihana-neo-14.5-ios.ipa")!,
                          page: URL(string: "https://github.com")!, notes: "")
        let link = AppUpdater.installLink(u)
        XCTAssertEqual(link.scheme, "sidestore")
        XCTAssertEqual(link.host, "install")
        XCTAssertEqual(URLComponents(url: link, resolvingAgainstBaseURL: false)?.queryItems?.first?.value, u.ipa.absoluteString)
    }
}

final class RemoteNoticeTests: XCTestCase {
    private let today = LocalDate(year: 2026, month: 11, day: 23)
    private func notice(_ extra: [String: Any] = [:]) -> [String: Any] {
        ["id": "2026-11-23T07:25:18Z", "title": "(중요) 업데이트 요망", "body": "최신 업데이트(14.5)를 설치하세요"].merging(extra) { $1 }
    }

    func testBelowHidesFromUpdatedApps() {
        XCTAssertNil(RemoteNoticeStore.parse(notice(["below": "14.5"]), today: today, version: "15.0"))
        XCTAssertNil(RemoteNoticeStore.parse(notice(["below": "14.5"]), today: today, version: "14.5"))
        XCTAssertNotNil(RemoteNoticeStore.parse(notice(["below": "14.5"]), today: today, version: "14.4"))
    }

    func testWithoutBelowShowsToEveryone() {
        XCTAssertNotNil(RemoteNoticeStore.parse(notice(), today: today, version: "15.0"))
        XCTAssertNil(RemoteNoticeStore.parse(notice(["to": "dev"]), today: today, version: "15.0"), "안드로이드 빌드 대상 공지는 아이폰에 띄우지 않습니다")
    }
}
