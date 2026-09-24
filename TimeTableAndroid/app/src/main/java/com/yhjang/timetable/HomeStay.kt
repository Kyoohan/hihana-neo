package com.yhjang.timetable

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * 귀가 기간 — 학사일정의 "귀가(1,2,3)" · "귀교(1,2,3)" 로 날짜별 상태를 정해 [Timetable.installHomeStay] 로 넘깁니다.
 * 귀가일은 1타임부터, 귀교일은 마지막 타임(21:30) 전까지, 그 사이 날은 하루 종일 일정이 없어 Now Bar·위젯이 꺼지고
 * 홈에는 귀가 안내가 뜹니다. 괄호 안 학년에 내 학년이 없으면 그 귀가·귀교는 무시합니다.
 */
object HomeStaySchedule {

    /** 바뀔 때마다 올라가는 번호 — 홈 화면이 이 값으로 오늘 블록을 다시 계산합니다. */
    var revision by mutableIntStateOf(0)
        private set

    /** "귀가", "귀교(1,2,3)" 처럼 이름 전체가 귀가·귀교인 일정만 — "코람데오 귀가 예배" 같은 행사는 빼려고 전체를 맞춥니다. */
    private val pattern = Regex("""^(귀가|귀교)\s*(?:\(([^)]*)\))?$""")

    /** 짝이 되는 귀가·귀교가 조회 범위 밖이라 안 보일 때 비울 최대 날 수. */
    private const val MAX_UNSEEN_DAYS = 10L

    /** 위젯·Now Bar 가 새 프로세스에서 떠도 귀가 기간이 반영되게, 아직 없으면 캐시에서 설치합니다. */
    suspend fun ensureInstalled(context: Context) {
        if (!Timetable.homeStayInstalled()) refresh(context)
    }

    /** 캐시된 학사일정과 지금 학년으로 다시 계산합니다. 바뀌었으면 true. */
    suspend fun refresh(context: Context): Boolean = install(
        HanaAcademicRepository.cachedSchedule(context),
        PlanStore.studentGrade(context),
    )

    @Volatile private var installed: Map<LocalDate, Timetable.HomeStay>? = null

    fun install(entries: List<HanaScheduleEntry>, grade: Int): Boolean {
        val map = compute(entries, grade)
        if (map == installed && Timetable.homeStayInstalled()) return false
        installed = map
        Timetable.installHomeStay(map)
        revision++
        return true
    }

    internal fun compute(entries: List<HanaScheduleEntry>, grade: Int): Map<LocalDate, Timetable.HomeStay> {
        if (entries.isEmpty()) return emptyMap()
        val leaves = sortedSetOf<LocalDate>()
        val returns = sortedSetOf<LocalDate>()
        for (entry in entries) {
            val match = pattern.matchEntire(entry.name.trim()) ?: continue
            val grades = match.groupValues[2].filter { it.isDigit() }.map { it.digitToInt() }
            if (grades.isNotEmpty() && grade !in grades) continue
            if (match.groupValues[1] == "귀가") leaves += entry.date else returns += entry.date
        }
        val firstDay = entries.minOf { it.date }
        val lastDay = entries.maxOf { it.date }
        val map = mutableMapOf<LocalDate, Timetable.HomeStay>()

        for (back in returns) {
            val previousReturn = returns.lower(back)
            val leave = leaves.lower(back)?.takeIf { previousReturn == null || it.isAfter(previousReturn) }
            // 짝이 되는 귀가가 안 보이면(조회 범위 앞) 범위 첫날부터 비웁니다 — 이미 집에 가 있는 중입니다.
            var day = leave?.plusDays(1) ?: maxOf(firstDay, back.minusDays(MAX_UNSEEN_DAYS))
            if (leave != null) map[leave] = Timetable.HomeStay.LEAVE
            while (day.isBefore(back)) {
                map[day] = Timetable.HomeStay.AWAY
                day = day.plusDays(1)
            }
            map[back] = Timetable.HomeStay.RETURN
        }
        // 귀교가 아직 조회 범위 밖이면 귀가일 뒤로 범위 끝까지(길어도 [MAX_UNSEEN_DAYS]일) 비웁니다 —
        // 캐시에는 두 달 뒤 정기고사도 섞여 있어 범위 끝만 믿으면 몇 주가 통째로 비었습니다.
        for (leave in leaves) {
            if (returns.higher(leave) != null) continue
            map[leave] = Timetable.HomeStay.LEAVE
            var day = leave.plusDays(1)
            val until = minOf(lastDay, leave.plusDays(MAX_UNSEEN_DAYS))
            while (!day.isAfter(until)) {
                map[day] = Timetable.HomeStay.AWAY
                day = day.plusDays(1)
            }
        }
        return map
    }
}

/** 홈 '지금' 카드에 띄우는 귀가 안내. */
data class HomeStayNotice(val title: String, val detail: String)

/** 지금 일정이 비어 있는 귀가 기간이면 안내를, 아니면 null — 귀가일은 1타임부터, 귀교일은 마지막 타임 전까지만. */
fun homeStayNotice(today: LocalDate, now: LocalDateTime): HomeStayNotice? {
    val stay = Timetable.homeStay(today) ?: return null
    val minutes = if (now.toLocalDate() == today) now.hour * 60 + now.minute else 0
    fun clock(m: Int) = "%d:%02d".format(m / 60, m % 60)
    /** "21:30 4타임" — 세션 이름 "면학 4타임"에서 타임 부분만. */
    fun returnSession(date: LocalDate): String {
        val session = Timetable.homeReturnSession(date)
        return "${clock(session.start)} ${session.name.removePrefix("면학").trim()}"
    }
    return when (stay) {
        Timetable.HomeStay.RETURN -> {
            val start = Timetable.homeReturnAt(today)
            if (minutes >= start) null
            else HomeStayNotice("조심히 돌아오세요", "오늘 귀교 · ${returnSession(today)}부터 일정이 시작됩니다")
        }
        Timetable.HomeStay.LEAVE, Timetable.HomeStay.AWAY -> {
            if (stay == Timetable.HomeStay.LEAVE && minutes < Timetable.homeLeaveAt(today)) return null
            val back = Timetable.nextHomeReturn(today.plusDays(1))
            val detail = if (back == null) {
                "귀교하면 일정이 다시 표시됩니다"
            } else {
                val day = back.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
                "${back.monthValue}월 ${back.dayOfMonth}일($day) 귀교 · ${returnSession(back)}부터 일정이 다시 시작됩니다"
            }
            HomeStayNotice("편안한 귀가 보내세요", detail)
        }
    }
}
