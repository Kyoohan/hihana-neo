package com.skh.hihanaevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 게시글 응답 읽기 — 글이 없는 응답은 원인을 담은 Unreadable 로(화면에 이유와 '다시 시도'). 정상 글은 Html(안드로이드)을 써서 기기에서 확인. */
class PostDetailParseTest {
    private fun unreadable(raw: String): HanaPostApi.Unreadable =
        try {
            HanaPostApi.parse(raw); fail("Unreadable 이 나야 합니다"); throw AssertionError()
        } catch (e: HanaPostApi.Unreadable) {
            e
        }

    @Test
    fun blankResponse() {
        assertEquals("학교 서버가 빈 응답을 보냈습니다", unreadable("  ").reason)
    }

    @Test
    fun htmlErrorPage() {
        val e = unreadable("<html><body>500 Internal Server Error</body></html>")
        assertEquals("게시글 응답을 읽지 못했습니다", e.reason)
        assertTrue(e.detail.startsWith("JSON 아님"))
    }

    @Test
    fun jsonWithoutPostUsesPortalMessage() {
        val e = unreadable("""{"result":"fail","resMsg":"삭제된 게시글입니다."}""")
        assertEquals("삭제된 게시글입니다.", e.reason)
        assertTrue(e.detail.contains("result=fail"))
    }
}
