import Foundation
import WebKit

/// 학생시간표 페이지는 JS 로 표를 그립니다 — 숨은 웹뷰로 렌더한 뒤 표 HTML 만 꺼내 [TimetableParser] 로 읽습니다.
/// 로그인 세션 쿠키를 웹뷰로 옮겨 심고, 학기를 골라 다시 조회한 표가 보일 때까지 0.5초마다 확인합니다(최대 12초).
@MainActor
final class TimetableClient: NSObject, WKNavigationDelegate {
    static let pageURL = URL(string: "https://hi.hana.hs.kr/main/std/regularCourse/student-timetable.do")!

    /// 표가 준비됐는지 — 이 페이지는 처음엔 빈 표('등록된 수강내역이 없습니다')를 그린 뒤 학기 목록을 받아 스스로 한 번 더
    /// 조회(gbn_code=…)합니다. 그 두 번째 페이지가 다 읽혔고, 표에 칸이 채워졌거나 정말 수강 내역이 없을 때만 준비된 것으로 봅니다.
    private static let readyJS = """
    (function(){ try {
      if (document.readyState !== 'complete' || location.search.indexOf('gbn_code=') < 0) return false;
      var t = document.getElementById('timetable'); if (!t) return false;
      return t.querySelectorAll('tbody td').length > 1 || t.innerText.indexOf('없습니다') >= 0;
    } catch(e) { return false; } })();
    """
    private static let tablesJS = """
    (function(){ try { var ts = document.querySelectorAll('table'); var out = '';
      for (var i = 0; i < ts.length; i++) { out += ts[i].outerHTML; } return out; } catch(e) { return ''; } })();
    """

    private let webView: WKWebView
    private var finished: CheckedContinuation<Void, Never>?

    override init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        webView = WKWebView(frame: CGRect(x: 0, y: 0, width: 390, height: 800), configuration: config)
        webView.customUserAgent = PortalClient.userAgent
        super.init()
        webView.navigationDelegate = self
    }

    /// 표 HTML — 못 받으면 nil.
    func loadTableHTML() async -> String? {
        // 앱이 막 켜졌을 땐 아직 로그인 전이라 넘길 쿠키가 없습니다 — 먼저 세션을 살립니다.
        await PortalClient.shared.ensureLoggedIn()
        let cookies = await PortalClient.shared.sessionCookies()
        let store = webView.configuration.websiteDataStore.httpCookieStore
        for c in cookies { await store.setCookie(c) }
        webView.load(URLRequest(url: Self.pageURL))
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            finished = c
            Task { try? await Task.sleep(for: .seconds(12)); self.resumeIfWaiting() }
        }
        let deadline = Date().addingTimeInterval(12)
        while Date() < deadline {
            if (try? await webView.evaluateJavaScript(Self.readyJS)) as? Bool == true { break }
            try? await Task.sleep(for: .milliseconds(500))
        }
        guard let html = try? await webView.evaluateJavaScript(Self.tablesJS) as? String, !html.isEmpty else { return nil }
        return html
    }

    private func resumeIfWaiting() { finished?.resume(); finished = nil }

    nonisolated func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { Task { @MainActor in self.resumeIfWaiting() } }
    nonisolated func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { Task { @MainActor in self.resumeIfWaiting() } }
    nonisolated func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { Task { @MainActor in self.resumeIfWaiting() } }
}

enum TimetableSync {
    /// 웹뷰로 렌더한 표를 먼저, 안 되면 정적 페이지 HTML 의 표를 읽습니다.
    @MainActor static func fetch() async -> TimetableWeek? {
        let client = TimetableClient()
        if let html = await client.loadTableHTML(), let week = TimetableParser.parse(html: html) { return week }
        if let html = try? await PortalClient.shared.authenticatedHTML("/main/std/regularCourse/student-timetable.do"), let week = TimetableParser.parse(html: html) { return week }
        return nil
    }
}
