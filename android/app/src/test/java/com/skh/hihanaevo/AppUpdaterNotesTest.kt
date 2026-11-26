package com.skh.hihanaevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 번호를 iOS 와 같이 쓰고 한쪽만 바뀐 버전은 건너뛰는 방식 — 노트에서 이 플랫폼 줄만, 버전 비교. */
class AppUpdaterNotesTest {
    @Test
    fun notesKeepOnlyAndroidLines() {
        val body = "아이패드 화면을 다듬었습니다.\n- [iOS] 잠금 화면 현황 배경\n- [안드로이드] 위젯 글자 크기\n- 공통 수정"
        assertEquals("아이패드 화면을 다듬었습니다.\n- 위젯 글자 크기\n- 공통 수정", AppUpdater.notesForThisPlatform(body))
    }

    @Test
    fun skippedNumbersStillCompare() {
        assertTrue(AppUpdater.isNewer("15.3", "15.1"))
        assertFalse(AppUpdater.isNewer("15.2", "15.3"))
    }
}
