package com.skh.hihanaevo

import com.skh.hihanaevo.Timetable.HomeStay.AWAY
import com.skh.hihanaevo.Timetable.HomeStay.LEAVE
import com.skh.hihanaevo.Timetable.HomeStay.RETURN
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 귀가 기간 계산 — 2026-11 실제 학사일정과, 이름·학년·누락 변형. */
class HomeStayScheduleTest {

    private fun d(md: String) = LocalDate.parse("2026-$md")

    private fun e(md: String, name: String, code: String = "SG02010000") = HanaScheduleEntry(
        code = code, name = name, place = null, startTime = null, endTime = null, allDay = true, date = d(md),
    )

    private fun run(entries: List<HanaScheduleEntry>, grade: Int = 2) =
        HomeStaySchedule.compute(entries.mapNotNull(HomeStaySchedule::parse), grade)

    /** 11/11 에 받은 실제 캐시의 귀가·귀교와 그 날들의 다른 일정. */
    private val real = listOf(
        e("11-12", "2학년상담", "SG02020000"),
        e("11-18", "수능 본부 설치", "SG02020000"),
        e("11-19", "대수능(3)", "SG01010000"),
        e("11-19", "1,2학년 귀가"),
        e("11-19", "수능 본부", "SG02020000"),
        e("11-20", "학부모 상담 종료(1,2학년)"),
        e("11-22", "귀교(1,2,3)"),
        e("12-04", "귀가(1,2)"),
        e("12-06", "귀교(1,2)"),
    )

    @Test fun realSchedule() {
        val map = run(real)
        assertNull("11/12 는 평소 일과(14.3 사고)", map[d("11-12")])
        assertNull(map[d("11-18")])
        assertEquals(LEAVE, map[d("11-19")])
        assertEquals(AWAY, map[d("11-20")])
        assertEquals(AWAY, map[d("11-21")])
        assertEquals(RETURN, map[d("11-22")])
        assertNull(map[d("11-23")])
        assertEquals(LEAVE, map[d("12-04")])
        assertEquals(AWAY, map[d("12-05")])
        assertEquals(RETURN, map[d("12-06")])
        assertEquals(7, map.size)
    }

    @Test fun twoShortPeriods() {
        val map = run(listOf(e("11-18", "귀가(1,2)"), e("11-19", "귀교(1,2)"), e("11-20", "귀가(1,2)"), e("11-22", "귀교(1,2,3)")))
        assertEquals(mapOf(d("11-18") to LEAVE, d("11-19") to RETURN, d("11-20") to LEAVE, d("11-21") to AWAY, d("11-22") to RETURN), map)
    }

    @Test fun unpairedReturnMarksOnlyThatDay() {
        // 귀가 이름을 못 읽어도 귀교 앞 평일을 비우지 않습니다.
        assertEquals(mapOf(d("11-22") to RETURN), run(listOf(e("11-19", "1,2학년 귀가일 안내"), e("11-22", "귀교(1,2,3)"))))
    }

    @Test fun unpairedLeaveMarksOnlyThatDay() {
        assertEquals(mapOf(d("12-04") to LEAVE), run(listOf(e("12-04", "귀가(1,2)"), e("12-06", "귀교 안내"))))
    }

    @Test fun names() {
        fun grades(name: String, code: String = "SG02010000") = HomeStaySchedule.parse(e("11-19", name, code))?.grades
        assertEquals(setOf(1, 2), grades("1,2학년 귀가"))
        assertEquals(setOf(1, 2, 3), grades("귀교(1,2,3)"))
        assertEquals(setOf(1, 2), grades("귀가 (1, 2학년)"))
        assertEquals(setOf(1, 2), grades("귀가(3학년 제외)"))
        assertEquals(emptySet<Int>(), grades("귀가(13시 이후)"))
        assertEquals(emptySet<Int>(), grades("귀가(전학년)"))
        assertEquals(emptySet<Int>(), grades("수능 귀가"))
        assertEquals(setOf(1, 2), grades("귀교（1,2）"))
        assertNull(grades("코람데오 귀가 예배"))
        assertNull(grades("버스 귀가", "SG02020000"))
        assertNull(grades("귀가", "SG03010000"))
        assertEquals(emptySet<Int>(), grades("1학기 귀가"))
    }

    @Test fun otherGradeIgnored() {
        assertEquals(emptyMap<LocalDate, Timetable.HomeStay>(), run(listOf(e("11-19", "3학년 귀가"), e("11-20", "귀가(3)"))))
    }

    @Test fun historyFillsLeaveBeforeFetchRange() {
        val stored = listOf(e("11-19", "1,2학년 귀가"), e("09-01", "귀가(1,2,3)"))
        // 11/22 에 받은 일정은 어제(11/21)부터 — 11/19 귀가는 기억에서.
        val fresh = listOf(e("11-21", "수강과목 수요조사(1,2학년)"), e("11-22", "귀교(1,2,3)"))
        val merged = HomeStaySchedule.mergeHistory(stored, fresh, d("11-22"))
        assertEquals(listOf(e("11-19", "1,2학년 귀가"), e("11-22", "귀교(1,2,3)")), merged)
        val map = HomeStaySchedule.compute(merged.mapNotNull(HomeStaySchedule::parse), 2)
        assertEquals(mapOf(d("11-19") to LEAVE, d("11-20") to AWAY, d("11-21") to AWAY, d("11-22") to RETURN), map)
    }

    @Test fun historyDropsRowsTheSchoolRemoved() {
        // 범위 안의 날짜는 새로 받은 일정만 믿습니다 — 학교가 지운 12/04 귀가는 남지 않습니다.
        val stored = listOf(e("12-04", "귀가(1,2)"))
        val merged = HomeStaySchedule.mergeHistory(stored, listOf(e("11-30", "3학년 수능후/ 특강", "SG02020000")), d("12-01"))
        assertEquals(emptyList<HanaScheduleEntry>(), merged)
    }
}
