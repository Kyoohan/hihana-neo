package com.yhjang.timetable

import java.time.DayOfWeek
import java.time.LocalDate

// MARK: - 면학 타임 슬롯

enum class PlanSlot {
    weekday0, weekday1, weekday2,
    weekend1, weekend2, weekend3, weekend4;

    val title: String
        get() = when (this) {
            weekday0 -> "0타임"
            weekday1, weekend1 -> "1타임"
            weekday2, weekend2 -> "2타임"
            weekend3 -> "3타임"
            weekend4 -> "4타임"
        }

    val timeText: String
        get() = when (this) {
            weekday0 -> "16:20 – 17:50"
            weekday1 -> "19:00 – 21:00"
            weekday2 -> "21:30 – 23:10"
            weekend1 -> "13:30 – 15:30"
            weekend2 -> "16:00 – 17:50"
            weekend3 -> "19:00 – 21:00"
            weekend4 -> "21:30 – 23:10"
        }

    /** 생활관을 고를 수 있는 타임인지 (평일 2타임 · 주말 4타임) */
    val allowsDorm: Boolean
        get() = this == weekday2 || this == weekend4

    /** 아무것도 안 골랐을 때의 값 — 생활관 타임만 자동으로 채워집니다 */
    val defaultPlace: StudyPlace?
        get() = if (allowsDorm) StudyPlace.Dorm else null

    companion object {
        /**
         * "면학 위치" 화면에서 수동으로 고를 수 있는 슬롯 목록 — 0타임은 하이하나 동기화로만
         * 채워지는 자동 슬롯이라 여기엔 넣지 않습니다 (Timetable.blocks() 에서는 그대로 씁니다)
         */
        fun slots(date: LocalDate): List<PlanSlot> {
            val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            return if (isWeekend) listOf(weekend1, weekend2, weekend3, weekend4) else listOf(weekday1, weekday2)
        }
    }
}
