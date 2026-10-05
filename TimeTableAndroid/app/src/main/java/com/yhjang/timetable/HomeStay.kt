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
 * 같은 학사일정의 공휴일(빨간날)도 함께 [Timetable.installHolidays] 로 넘겨, 평일 공휴일에 휴일 일과가 뜨게 합니다.
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

    @Volatile private var installedHolidays: Map<LocalDate, String>? = null

    fun install(entries: List<HanaScheduleEntry>, grade: Int): Boolean {
        val map = compute(entries, grade)
        // 공휴일(학사일정 빨간날)도 같은 학사일정에서 — 평일이라도 휴일 일과(1~4타임)로 바뀝니다.
        val holidays = entries.filter { it.code == HOLIDAY_CODE }.associate { it.date to it.name.trim() }
        if (map == installed && holidays == installedHolidays && Timetable.homeStayInstalled()) return false
        installed = map
        installedHolidays = holidays
        Timetable.installHomeStay(map)
        Timetable.installHolidays(holidays)
        revision++
        return true
    }

    /** 학사일정의 공휴일 분류 코드 — 학사 탭이 빨간색으로 칠하는 일정. */
    private const val HOLIDAY_CODE = "SG01020000"

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

/** 홈 '지금' 카드·위젯에 띄우는 귀가 안내 — [shortDetail] 은 위젯 한 줄용. */
data class HomeStayNotice(val title: String, val detail: String, val shortDetail: String)

/** 지금 일정이 비어 있는 귀가 기간이면 안내를, 아니면 null — 귀가일은 1타임부터, 귀교일은 마지막 타임 전까지만. */
fun homeStayNotice(today: LocalDate, now: LocalDateTime): HomeStayNotice? {
    val stay = Timetable.homeStay(today) ?: return null
    val minutes = if (now.toLocalDate() == today) now.hour * 60 + now.minute else 0
    // 귀교는 날짜만 알립니다 — 돌아오는 시각은 학생마다 달라 시간은 적지 않습니다.
    return when (stay) {
        Timetable.HomeStay.RETURN -> {
            if (minutes >= Timetable.homeReturnAt(today)) null
            else HomeStayNotice("조심히 돌아오세요", "오늘 귀교", "오늘 귀교")
        }
        Timetable.HomeStay.LEAVE, Timetable.HomeStay.AWAY -> {
            if (stay == Timetable.HomeStay.LEAVE && minutes < Timetable.homeLeaveAt(today)) return null
            val back = Timetable.nextHomeReturn(today.plusDays(1))
            if (back == null) {
                HomeStayNotice("편안한 귀가 보내세요", "귀교하면 일정이 다시 표시됩니다", "귀가 기간")
            } else {
                val day = back.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
                HomeStayNotice(
                    "편안한 귀가 보내세요",
                    "${back.monthValue}월 ${back.dayOfMonth}일($day) 귀교",
                    "${back.monthValue}/${back.dayOfMonth}($day) 귀교",
                )
            }
        }
    }
}

/**
 * 귀가 기간 '지금' 카드·위젯에 안내 문구 아래로 붙이는 정보 — 서울 날씨, 귀교 후 첫 일정, 귀교 뒤 2주 안의 시험.
 * 귀가·귀교일에는 석식이 없어 급식은 넣지 않습니다. 없는 줄은 null 이라 그리지 않습니다.
 */
data class HomeStayExtras(
    val weather: WeatherLine?,
    /** 귀교 후 첫 일정 — "면학 4타임 · 6층 몰입형G G16" 과 시작 시각 "21:30". */
    val firstPlan: Pair<String, String>?,
    /** 다가오는 시험 이름과 오늘 기준 남은 날. */
    val exam: Pair<String, Long>?,
) {
    val isEmpty: Boolean get() = weather == null && firstPlan == null && exam == null
}

/** 날씨 한 줄 — [label] "오늘 서울 날씨", [value] "흐림 · 18°", [tip] "저녁부터 비 · 우산 챙기세요", [side] "강수 60%". */
data class WeatherLine(
    val label: String,
    val value: String,
    val tip: String?,
    val side: String?,
    val icon: Int,
    /** 위젯용 큰 기온 "18°" 과 짧은 설명 "서울 · 흐림 · 저녁 비". */
    val bigTemp: String?,
    val short: String,
)

/** [homeStayNotice] 가 뜨는 때에만 값을 줍니다. 날씨·학사일정은 캐시만 읽어 바로 그립니다. */
suspend fun homeStayExtras(context: Context, today: LocalDate, now: LocalDateTime): HomeStayExtras? {
    homeStayNotice(today, now) ?: return null
    val stay = Timetable.homeStay(today) ?: return null
    val back = if (stay == Timetable.HomeStay.RETURN) today else Timetable.nextHomeReturn(today.plusDays(1))
    return buildHomeStayExtras(context, stay, today, back, now)
}

/**
 * Dev 탭 미리보기 — 오늘 날씨(예보가 있는 범위)로 귀가일·귀가 중·귀교일 줄을 미리 봅니다.
 * 귀교일은 귀가일이면 이틀 뒤, 귀가 중이면 내일, 귀교일이면 오늘로 둡니다.
 */
suspend fun previewHomeStayExtras(context: Context, stay: Timetable.HomeStay, now: LocalDateTime): HomeStayExtras? {
    SeoulWeather.get(context)
    val today = now.toLocalDate()
    val back = when (stay) {
        Timetable.HomeStay.LEAVE -> today.plusDays(2)
        Timetable.HomeStay.AWAY -> today.plusDays(1)
        Timetable.HomeStay.RETURN -> today
    }
    return buildHomeStayExtras(context, stay, today, back, now)
}

private suspend fun buildHomeStayExtras(
    context: Context,
    stay: Timetable.HomeStay,
    today: LocalDate,
    back: LocalDate?,
    now: LocalDateTime,
): HomeStayExtras? {
    val forecast = SeoulWeather.cached(context)
    val weather = forecast?.let { weatherLine(it, stay, today, back, now) }

    val firstPlan = back?.let { date ->
        val places = runCatching { PlanStore.placesForToday(context, date) }.getOrDefault(emptyMap())
        val cutoff = date.atStartOfDay().plusMinutes(Timetable.homeReturnAt(date).toLong())
        Timetable.blocks(date) { places[it] }
            .firstOrNull { !it.isBlank && it.kind !is BlockKind.GapKind && !it.start.isBefore(cutoff) }
            ?.let { block ->
                val place = listOfNotNull(block.title.takeIf { t -> t.isNotBlank() }, block.room?.takeIf { r -> r.isNotBlank() }).joinToString(" ")
                listOf(block.statusLabel, place).filter { it.isNotBlank() }.joinToString(" · ") to
                    "%02d:%02d".format(block.start.hour, block.start.minute)
            }
    }

    val exam = back?.let { date ->
        examDday(HanaAcademicRepository.cachedSchedule(context), today)
            ?.takeIf { !it.date.isBefore(date) && !it.date.isAfter(date.plusDays(14)) }
            ?.let { it.name to it.days }
    }
    return HomeStayExtras(weather, firstPlan, exam).takeIf { !it.isEmpty }
}

private fun weatherLine(
    forecast: SeoulWeather.Forecast,
    stay: Timetable.HomeStay,
    today: LocalDate,
    back: LocalDate?,
    now: LocalDateTime,
): WeatherLine? {
    fun deg(value: Double?) = value?.let { "${Math.round(it)}°" }
    fun umbrella(code: Int?) = if (SeoulWeather.kind(code) == SeoulWeather.Kind.SNOW) "눈 소식 · 우산 챙기세요" else "우산 챙기세요"
    return when (stay) {
        // 귀가일 — 나가는 길의 저녁 날씨.
        Timetable.HomeStay.LEAVE -> {
            val evening = forecast.hours.filter { it.time.toLocalDate() == today && it.time.hour in 18..23 && !it.time.isBefore(now.withMinute(0)) }
            if (evening.isEmpty()) return null
            val code = SeoulWeather.worst(evening.map { it.code })
            val pop = evening.mapNotNull { it.pop }.maxOrNull()
            val temp = evening.first().temp
            val wet = SeoulWeather.wet(code, pop)
            WeatherLine(
                label = "오늘 저녁 서울 날씨",
                value = listOfNotNull(SeoulWeather.label(code), deg(temp)).joinToString(" · "),
                tip = if (wet) umbrella(code) else null,
                side = pop?.takeIf { it > 0 }?.let { "강수 $it%" },
                icon = SeoulWeather.icon(code),
                bigTemp = deg(temp),
                short = "서울 · " + SeoulWeather.label(code) + if (wet) " · 우산" else "",
            )
        }
        // 귀가 중 — 귀교일 예보(예보가 잡히는 3일 전부터).
        Timetable.HomeStay.AWAY -> {
            val date = back ?: return null
            if (java.time.temporal.ChronoUnit.DAYS.between(today, date) > 3) return null
            val day = forecast.days.firstOrNull { it.date == date } ?: return null
            val wet = SeoulWeather.wet(day.code, day.pop)
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
            WeatherLine(
                label = "귀교일 서울 날씨",
                value = SeoulWeather.label(day.code) + (if (day.max != null && day.min != null) " · ${deg(day.max)} / ${deg(day.min)}" else ""),
                tip = if (wet) umbrella(day.code) else null,
                side = dayName,
                icon = SeoulWeather.icon(day.code),
                bigTemp = deg(day.max),
                short = "$dayName 서울 · " + SeoulWeather.label(day.code),
            )
        }
        // 귀교일 — 지금 날씨와, 돌아오는 저녁의 비 소식.
        Timetable.HomeStay.RETURN -> {
            val rest = forecast.hours.filter { it.time.toLocalDate() == today && !it.time.isBefore(now.withMinute(0)) && it.time.hour <= 22 }
            val firstWet = rest.firstOrNull { SeoulWeather.wet(it.code, it.pop) }
            val pop = rest.mapNotNull { it.pop }.maxOrNull()
            val tip = firstWet?.let {
                val code = SeoulWeather.worst(rest.map { h -> h.code }.filter { c -> SeoulWeather.wet(c, null) }) ?: it.code
                val what = if (SeoulWeather.kind(code) == SeoulWeather.Kind.SNOW) "눈" else "비"
                (if (it.time.hour >= 17 && it.time.hour > now.hour) "저녁부터 $what" else "$what 소식") + " · 우산 챙기세요"
            }
            val code = forecast.currentCode
            WeatherLine(
                label = "오늘 서울 날씨",
                value = listOfNotNull(SeoulWeather.label(code), deg(forecast.currentTemp)).joinToString(" · "),
                tip = tip,
                side = pop?.takeIf { it > 0 }?.let { "강수 $it%" },
                icon = SeoulWeather.icon(code),
                bigTemp = deg(forecast.currentTemp),
                short = "서울 · " + SeoulWeather.label(code) + (tip?.let { " · " + it.substringBefore(" · ") } ?: ""),
            )
        }
    }
}
