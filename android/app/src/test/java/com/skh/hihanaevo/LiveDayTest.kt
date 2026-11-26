package com.skh.hihanaevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 실시간 일정의 하루 — 블록타임 묶기와 시간 비례 막대(iOS 와 같은 규칙). */
class LiveDayTest {
    private fun t(h: Int, m: Int) = LocalDateTime.of(2026, 11, 25, h, m)
    private val math = Lesson("미적분Ⅱ", "B301")
    private val eng = Lesson("영어Ⅱ", "A201")
    private fun lesson(p: Int, l: Lesson, s: LocalDateTime, e: LocalDateTime) = Block(BlockKind.LessonKind(p, l), s, e)
    private fun gap(label: String, accent: Accent, s: LocalDateTime, e: LocalDateTime) =
        Block(BlockKind.GapKind(Gap(label, label, "", null, accent)), s, e)

    private val blocks = listOf(
        lesson(1, math, t(8, 20), t(9, 10)),
        gap("쉬는 시간", Accent.BREAK_TIME, t(9, 10), t(9, 20)),
        lesson(2, math, t(9, 20), t(10, 10)),
        gap("쉬는 시간", Accent.BREAK_TIME, t(10, 10), t(10, 20)),
        lesson(3, eng, t(10, 20), t(11, 10)),
        gap("점심시간", Accent.LUNCH, t(11, 10), t(12, 10)),
        Block(BlockKind.BlankKind, t(12, 10), t(23, 59)),
    )

    @Test
    fun blockTimeMerges() {
        val segs = LiveDay.segments(blocks)
        assertEquals("1–2교시 수업 중", segs[0].statusLabel)
        assertEquals(t(10, 10), segs[0].end)
        assertEquals("쉬는 시간", segs[1].statusLabel)
        assertEquals("3교시 수업 중", segs[2].statusLabel)
        assertEquals(5, segs.size)
    }

    @Test
    fun barIsProportionalToTime() {
        val segs = LiveDay.segments(blocks)
        val pieces = LiveDay.pieces(segs, t(10, 15))
        assertEquals(1000, pieces.sumOf { it.length })
        // 하루 8:20~12:10 = 230분. 블록타임 110분 → 478, 쉬는 시간 10분 → 43(지금 10:15 가 이 안), 3교시 50분 → 217, 점심 60분 → 261.
        assertEquals(478, pieces[0].length)
        assertTrue(pieces[1].length in 42..45)
        assertTrue(pieces.last().length in 259..263)
        // 끝난 일정 → 지금 일정(한 칸, 나누지 않음) → 남은 일정 순서.
        assertEquals(listOf(LiveDay.Zone.DONE, LiveDay.Zone.CURRENT, LiveDay.Zone.AHEAD, LiveDay.Zone.AHEAD), pieces.map { it.zone })
        assertEquals(LiveDay.progress(segs, t(10, 15)), Math.round(115 * 1000.0 / 230).toInt())
    }

    @Test
    fun manyPiecesAreCappedKeepingNow() {
        // 블록타임 없는 하루처럼 짧은 구간이 많을 때 — 칸 수 상한 안, 합 1000, '지금' 경계(진행률) 그대로.
        val many = (0 until 12).flatMap { i ->
            val s = t(8, 0).plusMinutes(60L * i)
            listOf(lesson(i + 1, Lesson("과목$i", "R$i"), s, s.plusMinutes(50)), gap("쉬는 시간", Accent.BREAK_TIME, s.plusMinutes(50), s.plusMinutes(60)))
        }
        val segs = LiveDay.segments(many)
        val now = t(13, 25)
        val pieces = LiveDay.pieces(segs, now)
        assertTrue(pieces.size <= LiveDay.MAX_PIECES)
        assertEquals(1000, pieces.sumOf { it.length })
        // 지금 일정(13:00~13:50 수업, 50분 → 1000×50/720 ≈ 69)은 다른 칸에 먹히지 않고 제 자리·제 길이 그대로.
        val current = pieces.single { it.zone == LiveDay.Zone.CURRENT }
        assertTrue(current.length in 68..71)
        val before = pieces.takeWhile { it.zone == LiveDay.Zone.DONE }.sumOf { it.length }
        assertTrue(LiveDay.progress(segs, now) in before..(before + current.length))
    }

    @Test
    fun dayStartsAtEight() {
        val morning = listOf(
            Block(BlockKind.BlankKind, t(0, 0), t(6, 50)),
            gap("아침시간", Accent.BREAKFAST, t(6, 50), t(8, 0)),
            gap("등교 및 조회", Accent.LESSON, t(8, 0), t(8, 10)),
            gap("쉬는 시간", Accent.BREAK_TIME, t(8, 10), t(8, 20)),
            lesson(1, math, t(8, 20), t(9, 10)),
        )
        val shown = LiveDay.trimMorning(morning).filter { !it.isBlank }
        assertEquals(t(8, 0), shown.first().start)
        assertEquals("등교 및 조회", shown.first().statusLabel)
        // 주말 '수업 없음'(6:50~13:30)은 12:00~13:30 점심시간으로.
        val weekend = LiveDay.trimMorning(listOf(gap("주말 · 수업 없음", Accent.IDLE, t(6, 50), t(13, 30)))).filter { !it.isBlank }
        assertEquals(t(12, 0), weekend.single().start)
        assertEquals("점심시간", weekend.single().statusLabel)
    }
}
