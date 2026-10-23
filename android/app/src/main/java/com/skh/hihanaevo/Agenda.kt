package com.skh.hihanaevo

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 통합 일정 — 학사일정(하이하나), 게시글에서 AI 가 뽑은 일정, 구글 클래스룸 과제 마감을 한 줄 모양으로 모읍니다.
 * 홈의 일정 카드와 일정 탭의 학사일정 목록이 이걸로 날짜순 한 목록을 그립니다.
 */
/** 일정 출처와 색 — 학사일정 종류 색([scheduleColor])과도 겹치지 않게(클래스룸 초록 ↔ 하나일정 청록, 게시판 노랑 ↔ 면학감독 갈색). */
enum class AgendaSource(val label: String, val color: Color) {
    ACADEMIC("학사", Color(0xFF3A7BD5)),
    BOARD("게시판", Color(0xFFE3A82E)),
    CLASSROOM("클래스룸", Color(0xFF1E8E3E)),
}

data class AgendaItem(
    val id: String,
    val source: AgendaSource,
    val title: String,
    val date: LocalDate,
    /** 기간 일정의 마지막 날 — 하루짜리면 null. */
    val endDate: LocalDate? = null,
    /** "13:00" — 시각이 없으면 null. */
    val time: String? = null,
    /** 신청·제출 기한(마감)인지 — 행사·시험은 false. */
    val deadline: Boolean = false,
    /** 출처 설명 — 게시글 제목, 과목 이름. */
    val detail: String? = null,
    /** 탭하면 열 곳 — 게시글 주소, 클래스룸 과제 주소. */
    val url: String? = null,
    /** 클래스룸 과제를 이미 냈으면 true — 목록에서 흐리게. */
    val done: Boolean = false,
    /** 학사일정 원본 — 색·종류 글자에 씁니다. */
    val academic: HanaScheduleEntry? = null,
    /** 대상 학년(1~3) — 게시글에서 AI 가 뽑은 일정만. 비어 있으면 전 학년. */
    val grades: Set<Int> = emptySet(),
) {
    /** 이 학년 학생에게 보일 일정인지 — 대상 학년이 없으면(전 학년·애매함) 늘 보입니다. */
    fun forGrade(grade: Int): Boolean = grades.isEmpty() || grade in grades

    /** 며칠짜리 일정인지 — 하루짜리는 1. */
    val spanDays: Long get() = java.time.temporal.ChronoUnit.DAYS.between(date, endDate ?: date) + 1

    /** 2주 넘게 이어지는 상시 운영 안내(방과후 강의·1인2기 쿼터 등) — 학사일정 목록에선 숨기고, 홈에선 맨 아래로. */
    val longRunning: Boolean get() = spanDays > 14

    /** 오늘 기준 며칠 남았는지 — 기간 일정은 시작일 기준, 진행 중이면 0. */
    fun daysFrom(today: LocalDate): Long {
        val end = endDate ?: date
        return if (!today.isBefore(date) && !today.isAfter(end)) 0L else java.time.temporal.ChronoUnit.DAYS.between(today, date)
    }
}

object Agenda {
    /**
     * 세 출처를 하나로([extras] 는 게시글·클래스룸 일정) — [today] 이후(진행 중인 기간 일정 포함)만, 날짜 → 시각 → 마감 먼저 순.
     * 게시글에서 뽑은 일정이 같은 날 학사일정과 이름이 겹치면(중간고사처럼 둘 다 적힌 경우) 학사일정만 남깁니다.
     */
    fun merge(
        schedule: List<HanaScheduleEntry>,
        studentGrade: Int,
        extras: List<AgendaItem>,
        today: LocalDate,
    ): List<AgendaItem> {
        val academic = filterSupervisionByGrade(schedule, studentGrade)
            .filter { it.code != "SG02020000" } // 시설사용은 학생에게 숨김
            .filter { !it.date.isBefore(today) }
            .map { entry ->
                AgendaItem(
                    id = "a:${entry.date}:${entry.code}:${entry.name}:${entry.teacher.orEmpty()}",
                    source = AgendaSource.ACADEMIC,
                    title = scheduleTitle(entry),
                    date = entry.date,
                    time = scheduleTime(entry),
                    academic = entry,
                )
            }
        val academicKeys = academic.map { it.date to normalize(it.title) }.toSet()
        val others = extras
            .filter { !(it.endDate ?: it.date).isBefore(today) }
            .filter { item ->
                val key = normalize(item.title)
                academicKeys.none { (d, t) -> d == item.date && (t.contains(key) || key.contains(t)) }
            }
            // 같은 날 같은 이름은 한 번만(여러 글이 같은 일정을 알리면) — 시각·마감이 있는 쪽을 남깁니다.
            .groupBy { it.date to normalize(it.title) }
            .map { (_, same) -> same.maxBy { (if (it.time != null) 2 else 0) + (if (it.deadline) 1 else 0) } }
        return (academic + others).sortedWith(
            // 같은 날 안에선 면학감독을 맨 아래로 — 가장 덜 중요합니다.
            compareBy<AgendaItem>({ it.date }, { it.academic?.code == SUPERVISION_CODE }, { it.time ?: "99" }, { !it.deadline }, { it.source.ordinal }, { it.title }),
        )
    }

    private fun normalize(s: String): String = s.replace(Regex("[\\s·\\-()\\[\\]]"), "").lowercase()
}

/** 게시글에서 AI 가 뽑은 일정 한 건 — 요약 서버의 events 항목. */
data class PostEvent(
    val title: String,
    val date: LocalDate,
    val end: LocalDate? = null,
    val time: String? = null,
    val deadline: Boolean = false,
    /** 대상 학년(1~3) — 비어 있으면 전 학년. 한 글에 학년마다 다른 일정이 섞여 있으면 서버가 학년마다 따로 뽑습니다. */
    val grades: Set<Int> = emptySet(),
)

/**
 * 게시글 일정 보관함 — 요약을 받을 때마다 글(제목·주소)과 일정을 함께 적어 두고, 홈·일정 탭이 날짜 이후 것만 모아 씁니다.
 * 요약 캐시([PostSummarizer])와 같은 글 키를 쓰고, 오래된 글부터 지워 300건까지만 둡니다.
 */
object BoardAgendaStore {
    /** v2 — 일정마다 대상 학년을 함께 적습니다(학년 없는 예전 일정은 버리고, 요약을 새로 받으며 다시 채웁니다). */
    private const val PREFS = "board_agenda_v2"
    private const val MAX = 300

    /** 일정이라 하기 어려운 것 — 급식·식단 운영 안내("10월 4주차 급식 운영"). 이미 뽑아 둔 것도 여기서 거릅니다(요약 서버도 이제 안 뽑음). */
    private val NOT_AN_EVENT = Regex("급식|식단|조식|중식|석식")

    /**
     * 요약 서버의 일정 규칙이 바뀐 뒤(서버 v7: 날짜 없는 글은 일정 없음, v6: 기수·면학 타임) 받아 둔 게시판 글들의 요약을 한 번 새로 받아 일정을
     * 다시 채웠는지. 규칙이 또 바뀌면 [REBUILD_KEY] 를 올립니다.
     */
    fun needsRebuild(context: Context): Boolean = !prefs(context).getBoolean(REBUILD_KEY, false)

    fun markRebuilt(context: Context) {
        prefs(context).edit().putBoolean(REBUILD_KEY, true).apply()
    }

    private const val REBUILD_KEY = "rebuilt_v7"

    /** 바뀔 때마다 1 씩 — 홈·일정 탭이 이 값을 읽어 다시 모읍니다. */
    val revision = mutableIntStateOf(0)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(context: Context, key: String, title: String, url: String?, events: List<PostEvent>) {
        val p = prefs(context)
        val had = p.contains("e_$key")
        if (events.isEmpty()) {
            if (had) {
                p.edit().remove("e_$key").apply()
                revision.intValue++
            }
            return
        }
        val order = p.getString("order", "").orEmpty().split(',').filter { it.isNotEmpty() && it != key } + key
        val keep = order.takeLast(MAX)
        val o = JSONObject()
            .put("t", title)
            .put("u", url ?: JSONObject.NULL)
            .put("ev", JSONArray(events.map { e ->
                JSONObject().put("title", e.title).put("date", e.date.toString()).put("end", e.end?.toString() ?: JSONObject.NULL)
                    .put("time", e.time ?: JSONObject.NULL).put("deadline", e.deadline)
                    .apply { if (e.grades.isNotEmpty()) put("grades", JSONArray(e.grades.sorted())) }
            }))
        p.edit().apply {
            order.dropLast(keep.size).forEach { remove("e_$it") }
            putString("e_$key", o.toString())
            putString("order", keep.joinToString(","))
        }.apply()
        revision.intValue++
    }

    /** 보관한 모든 글의 일정 — 날짜 거르기는 [Agenda.merge] 가 합니다. */
    fun items(context: Context): List<AgendaItem> {
        val out = ArrayList<AgendaItem>()
        prefs(context).all.forEach { (k, v) ->
            if (!k.startsWith("e_") || v !is String) return@forEach
            val o = runCatching { JSONObject(v) }.getOrNull() ?: return@forEach
            val key = k.removePrefix("e_")
            val title = o.optString("t")
            val url = o.optString("u").takeIf { it.isNotEmpty() && it != "null" }
            // cr_ 는 클래스룸 공지에서 뽑은 일정(제목 자리에 'OO 공지') — 출처를 클래스룸으로.
            val classroom = key.startsWith("cr_")
            val category = if (classroom) null else url?.let { HanaPostApi.postIds(it) }?.let { (bmt, _) -> BoardCategory.entries.find { it.bmtIdx.toLong() == bmt }?.label }
            val arr = o.optJSONArray("ev") ?: return@forEach
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val start = runCatching { LocalDate.parse(e.optString("date")) }.getOrNull() ?: continue
                if (NOT_AN_EVENT.containsMatchIn(e.optString("title"))) continue
                val end = e.optString("end").takeIf { it.isNotEmpty() && it != "null" }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                // 신청 기간(10/22~10/26)으로 적힌 마감은 끝날이 마감 — 기간 내내 '오늘 마감'으로 보이지 않게 끝날 하루로.
                val deadline = e.optBoolean("deadline")
                val date = if (deadline && end != null && end.isAfter(start)) end else start
                out += AgendaItem(
                    id = if (classroom) "cr:${key.removePrefix("cr_")}:$i" else "b:$key:$i",
                    source = if (classroom) AgendaSource.CLASSROOM else AgendaSource.BOARD,
                    title = e.optString("title"),
                    date = date,
                    endDate = if (deadline) null else end,
                    time = e.optString("time").takeIf { it.isNotEmpty() && it != "null" },
                    deadline = deadline,
                    detail = listOfNotNull(category, title.takeIf { it.isNotEmpty() }).joinToString(" · "),
                    url = url,
                    // 클래스룸은 내가 듣는 수업의 글이라 학년으로 거르지 않습니다 — AI 가 '미적분2'·'물리학2'의 숫자를 학년으로 읽었습니다.
                    grades = if (classroom) emptySet() else e.optJSONArray("grades")?.let { g -> (0 until g.length()).map { g.optInt(it) }.filter { it in 1..3 }.toSet() }.orEmpty(),
                )
            }
        }
        return out
    }

    /** [prefix] 를 주면 그 키의 글만(클래스룸 연결 해제 때 "cr_"). */
    fun clear(context: Context, prefix: String? = null) {
        val p = prefs(context)
        if (prefix == null) p.edit().clear().apply()
        else {
            val order = p.getString("order", "").orEmpty().split(',').filter { it.isNotEmpty() }
            p.edit().apply {
                order.filter { it.startsWith(prefix) }.forEach { remove("e_$it") }
                putString("order", order.filterNot { it.startsWith(prefix) }.joinToString(","))
            }.apply()
        }
        revision.intValue++
    }
}
