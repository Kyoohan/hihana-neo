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

    /// 번호는 하나를 같이 쓰고 한쪽만 바뀐 버전은 건너뜀 — 안드로이드만 낸 15.3 뒤에 있는 아이폰 15.2 를 찾습니다.
    func testBestSkipsOtherPlatformReleases() {
        func rel(_ tag: String, _ asset: String, draft: Bool = false) -> [String: Any] {
            ["tag_name": tag, "draft": draft, "body": "",
             "assets": [["name": asset, "browser_download_url": "https://example.com/\(asset)"]]]
        }
        let list = [rel("v15.4", "a.ipa", draft: true), rel("v15.3", "a.apk"), rel("v15.2", "a.ipa"), rel("v15.1", "a.ipa")]
        XCTAssertEqual(AppUpdater.best(list, installed: "15.1")?.version, "15.2")
        XCTAssertNil(AppUpdater.best(list, installed: "15.2"))
    }

    func testNotesForThisPlatform() {
        let body = "아이패드 화면을 다듬었습니다.\n- [iOS] 잠금 화면 현황 배경\n- [안드로이드] 위젯 글자 크기\n- 공통 수정"
        XCTAssertEqual(AppUpdater.notesForThisPlatform(body), "아이패드 화면을 다듬었습니다.\n- 잠금 화면 현황 배경\n- 공통 수정")
    }
}
