import XCTest
@testable import HiHanaEvo

/// 게시글 응답 읽기 — 글이 없는 응답은 화면에 그대로 보일 문구로(안드로이드 PostDetailParseTest 와 같은 경우).
final class PostParseTests: XCTestCase {
    private func reason(_ raw: String) -> String? {
        do { _ = try PostAPI.parse(raw); return nil } catch { return error.localizedDescription }
    }

    func testBlankResponse() {
        XCTAssertEqual(reason("  "), "학교 서버가 빈 응답을 보냈습니다")
    }

    func testHtmlErrorPage() {
        XCTAssertEqual(reason("<html><body>500 Internal Server Error</body></html>"), "게시글 응답을 읽지 못했습니다")
    }

    func testJsonWithoutPostUsesPortalMessage() {
        XCTAssertEqual(reason(#"{"result":"fail","resMsg":"삭제된 게시글입니다."}"#), "삭제된 게시글입니다.")
    }

    func testNormalPost() throws {
        let d = try PostAPI.parse(#"{"boardVo":{"bd_title":"수영인증시험 안내","bd_writer":"이재진","inputdate":"2026-11-25 08:35:00","bd_content":"<p>12/27(일)</p>","bd_view":12}}"#)
        XCTAssertEqual(d.title, "수영인증시험 안내")
        XCTAssertEqual(d.views, 12)
    }
}
