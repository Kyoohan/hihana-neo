package com.skh.hihanaevo

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 기말고사 D-day 가 빠지거나 마지막 날로 뜨던 것 — 받아 둔 일정·실패한 날 처리. */
class ExamScheduleTest {

    private fun d(day: Int) = LocalDate.of(2026, 12, day)

    private fun e(name: String, date: LocalDate) = HanaScheduleEntry(
        code = "", name = name, place = null, startTime = null, endTime = null, allDay = true, date = date,
    )

    private val today = LocalDate.of(2026, 11, 24)

    /** 지난주에 받은 4주치(12/13 까지)엔 시험이 없어도 따로 받아 둔 정기고사가 붙어 D-day 가 뜹니다. */
    @Test
    fun examsMergedIntoStaleSchedule() {
        val stale = listOf(e("교내 체육대회", LocalDate.of(2026, 11, 27)), e("학부모 상담", d(4)))
        assertNull(examDday(stale, today))
        val exams = listOf(e("기말고사(1,2)", d(14)), e("기말고사(1,2)", d(15)))
        val merged = HanaAcademicRepository.withExams(stale + exams[0], exams)
        assertEquals(2, merged.count { it.name.startsWith("기말") }) // 같은 날·같은 이름은 하나
        assertEquals(d(14), examDday(merged, today)?.date)
        assertEquals(20L, examDday(merged, today)?.days)
    }

    /** 하루씩 받다 실패한 날은 받아 둔 그날 일정으로 — 실패한 날을 '일정 없음'으로 저장하면 시험 첫날이 사라졌습니다. */
    @Test
    fun failedDaysKeepStoredEntries() {
        val stored = (14..18).map { e("기말고사(1,2)", d(it)) } + e("교직원 연수", d(14))
        val fetched = HanaAcademicApi.ScheduleFetch(
            entries = listOf(e("귀가(1,2)", d(18)), e("기말고사(1,2)", d(18))),
            failedDays = (14..17).map(::d).toSet(),
        )
        val merged = HanaAcademicRepository.fillFailedDays(fetched, stored)
        assertEquals(7, merged.size) // 18일은 새로 받은 것만
        assertEquals(d(14), examDday(merged, today)?.date)
        val clean = HanaAcademicApi.ScheduleFetch(listOf(e("귀가(1,2)", d(18))), emptySet())
        assertEquals(1, HanaAcademicRepository.fillFailedDays(clean, stored).size)
    }
}
