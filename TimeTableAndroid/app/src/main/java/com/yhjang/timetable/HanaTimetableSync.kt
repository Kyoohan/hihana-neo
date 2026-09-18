package com.yhjang.timetable

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

// MARK: - 포털 시간표 조회

/** 포털이 실제로 그리는 학생시간표 페이지 — 웹뷰 폴백과 파싱이 같은 URL 을 씁니다. */
const val STUDENT_TIMETABLE_URL = "https://hh.hana.hs.kr/main/std/regularCourse/student-timetable.do"

/**
 * hh.hana.hs.kr 학생 시간표 조회.
 *
 * 페이지가 JS 로 표를 그리므로 **숨은 웹뷰로 렌더링한 DOM 의 표**(`outerHTML`)를 가장 먼저
 * [ExamParser] 로 파싱해 요일 열 · 교시 행을 주간 그리드로 옮깁니다. 웹뷰가 없거나(워커 등)
 * 렌더 결과가 비면, **정적** 학생시간표 페이지(`student-timetable.do`) HTML 의 표를 파싱하고,
 * 그래도 안 되면 과목 목록 페이지(`subject-list.do`)를 한 번 더 파싱한 뒤, 예전처럼 JSON
 * 형제 경로 후보(POST → GET)를 시도합니다. JSON 후보는 응답 모양
 * (`itemList`/`list`/`result`/`paging.result`/`result.list`)과 행 필드명을 넓게 받아들이고,
 * 되는 조합을 찾으면 그 인덱스를 저장해 다음부터 먼저 시도합니다. 각 원문의 앞부분과
 * 파싱된 그리드 요약은 `HanaTimetable` 태그로 남겨 실제 형식을 확인할 수 있게 합니다.
 */
object HanaTimetableApi {

    private const val BASE = "https://hh.hana.hs.kr"
    private const val TAG = "HanaTimetable"
    private const val REFERER = "$BASE/main/std/regularCourse/student-timetable.do"
    private const val TIMETABLE_PAGE = "/main/std/regularCourse/student-timetable.do"
    private const val SUBJECT_LIST_PAGE = "/main/std/regularCourse/subject-list.do"
    private const val PAGE_REFERER = "$BASE/main/std/regularCourse/subject-list.do"

    /** 알려진 정규수업 페이지의 JSON 형제 경로 + 확인용 변형 두 개. */
    private val paths = listOf(
        "/main/std/regularCourse/student-timetable.json",
        "/main/std/regularCourse/student-timetable-list.json",
        "/main/std/regularCourse/timetable.json",
        "/main/std/regularCourse/subject-list.json",
        "/main/std/regularCourse/regular-course-list.json",
        "/main/std/regularCourse/regular-course.json",
    )

    /** 엔드포인트 + 메서드 조합 — 인덱스 순서대로 시도합니다. */
    private data class Candidate(val path: String, val method: String)

    private val candidates: List<Candidate> =
        paths.flatMap { path -> listOf(Candidate(path, "POST"), Candidate(path, "GET")) }

    suspend fun fetch(context: Context, date: LocalDate): TimetableWeek? = withContext(Dispatchers.IO) {
        // 0) 페이지가 JS 로 표를 그리므로, 웹뷰로 렌더링한 DOM 의 표를 가장 먼저 읽습니다.
        val fromWebView = try {
            fetchFromWebView()
        } catch (e: Exception) {
            Log.d(TAG, "웹뷰 렌더 DOM 조회 실패: ${e.message}")
            null
        }
        if (fromWebView != null && fromWebView.lessons.isNotEmpty()) {
            return@withContext fromWebView
        }

        // 1) 학생시간표 페이지(HTML) — JS 렌더 DOM 을 못 얻었을 때의 정적 HTML 폴백.
        val fromPage = try {
            fetchFromPage(context)
        } catch (e: HanaPortalException.MissingCredentials) {
            throw e
        } catch (e: HanaPortalException.LoginFailed) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "학생시간표 페이지 조회 실패: ${e.message}")
            null
        }
        if (fromPage != null && fromPage.lessons.isNotEmpty()) {
            Log.d(TAG, "학생시간표 페이지에서 주간 시간표를 확인했습니다")
            return@withContext fromPage
        }
        Log.d(TAG, "학생시간표 페이지에서 시간표를 얻지 못해 JSON 후보로 내려갑니다")

        // 2) 확정되지 않은 JSON 형제 경로 추측 — 페이지가 비었을 때만.
        val params = weekParams(date)
        for (index in attemptOrder(context)) {
            val candidate = candidates[index]
            val week = tryCandidate(context, candidate, params) ?: continue
            if (looksLikeRealTimetable(week)) {
                TimetableEndpointStore.set(context, index)
                Log.d(TAG, "선택된 엔드포인트: ${candidate.method} ${candidate.path} (${week.lessons.size}일)")
                return@withContext week
            }
        }
        Log.d(TAG, "어떤 후보에서도 시간표를 확인하지 못했습니다")
        null
    }

    /**
     * 확정 안 된 JSON 형제 경로 추측 결과가 진짜 시간표처럼 보이는지 최소한으로 검증합니다.
     * 우연히 형식이 맞아떨어지는 무관한 JSON(다른 화면의 목록 응답 등)이 "시간표"로
     * 오인되어 [TimetableEndpointStore] 에 영구히 기억돼 버리는 걸 막기 위한 안전장치입니다.
     */
    private fun looksLikeRealTimetable(week: TimetableWeek): Boolean {
        val totalLessons = week.lessons.values.sumOf { it.size }
        return week.lessons.size >= 3 && totalLessons >= 10
    }

    /** 마지막으로 성공한 조합을 먼저, 나머지는 순서대로. 저장값이 범위를 벗어나면 전부 순서대로. */
    private fun attemptOrder(context: Context): List<Int> {
        val remembered = TimetableEndpointStore.get(context, candidates.size)
            ?: return candidates.indices.toList()
        return listOf(remembered) + candidates.indices.filter { it != remembered }
    }

    private suspend fun tryCandidate(
        context: Context,
        candidate: Candidate,
        params: List<Pair<String, String>>,
    ): TimetableWeek? {
        val label = "${candidate.method} ${candidate.path}"
        return try {
            val json = if (candidate.method == "POST") {
                HanaPortalClient.get().authenticatedJson(context, candidate.path, params, referer = REFERER)
                    .also { Log.d(TAG, "$label -> ${it.toString().take(300)}") }
            } else {
                val text = HanaPortalClient.get()
                    .authenticatedText(context, candidate.path, params, method = "GET", referer = REFERER)
                Log.d(TAG, "$label -> ${text.take(300)}")
                JSONObject(text)
            }
            parseWeek(json)
        } catch (e: HanaPortalException.MissingCredentials) {
            // 로그인 정보가 없으면 더 시도해도 의미가 없으니 즉시 중단 (자동 로그인 반복 방지)
            throw e
        } catch (e: HanaPortalException.LoginFailed) {
            // 비밀번호 오류로 5회 실패 잠금에 걸리지 않도록 여기서 멈춥니다
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "$label -> 실패: ${e.message}")
            null
        }
    }

    /** 이번 주 월~금 범위 — 다른 정규수업 JSON 들이 쓰는 목록 조회 파라미터 규칙을 따릅니다. */
    private fun weekParams(date: LocalDate): List<Pair<String, String>> {
        val monday = date.with(DayOfWeek.MONDAY)
        return listOf(
            "cp" to "1",
            "searchSDate" to monday.toString(),
            "searchEDate" to monday.plusDays(4).toString(),
            "listType" to "list",
        )
    }

    // MARK: 학생시간표 페이지 해석 (웹뷰 렌더 DOM → 정적 HTML)

    /**
     * 숨은 웹뷰가 렌더링한 DOM 의 표 HTML 을 받아 파싱합니다.
     * 웹뷰가 없으면(워커 등) null 을 돌려주고 호출부가 정적 HTML 폴백으로 내려갑니다.
     */
    private suspend fun fetchFromWebView(): TimetableWeek? {
        val html = HanaTimetableWebView.loadHtml() ?: return null
        Log.d(TAG, "웹뷰 렌더 DOM 길이=${html.length}")
        val week = parseTimetableHtml(html)
        Log.d(TAG, "WebView 주간: ${gridSummary(week)}")
        return week
    }

    /** 학생시간표 페이지를 먼저, 비면 과목 목록 페이지를 파싱합니다. */
    private suspend fun fetchFromPage(context: Context): TimetableWeek? {
        parsePage(context, TIMETABLE_PAGE, PAGE_REFERER)?.takeIf { it.lessons.isNotEmpty() }?.let { return it }
        return parsePage(context, SUBJECT_LIST_PAGE, "$BASE/")?.takeIf { it.lessons.isNotEmpty() }
    }

    private suspend fun parsePage(context: Context, path: String, referer: String): TimetableWeek? {
        val html = HanaPortalClient.get()
            .authenticatedText(context, path, method = "GET", referer = referer)
        Log.d(TAG, "GET $path head: ${html.take(300).replace('\n', ' ')}")
        val week = parseTimetableHtml(html)
        Log.d(TAG, "GET $path 파싱: ${gridSummary(week)}")
        return week
    }

    /** 페이지의 표들 중 가장 많은 칸을 채운 것을 시간표로 채택합니다 (안내 표·범례 표 배제). */
    private fun parseTimetableHtml(html: String): TimetableWeek? =
        runCatching { ExamParser.parse(html).tables }.getOrNull().orEmpty()
            .mapNotNull { parseTimetableTable(it) }
            .maxByOrNull { week -> week.lessons.values.sumOf { it.size } }

    /**
     * 표 하나를 주간 그리드로 옮깁니다.
     * 요일 셀이 2개 이상인 행을 머리글로 삼아 열↔요일을 정하고, 그 뒤 각 행의 첫 칸을
     * 교시로 읽습니다. 표 위에 제목 행이 끼어 있어도 머리글을 찾아냅니다.
     */
    private fun parseTimetableTable(table: ExamTable): TimetableWeek? {
        val allRows = listOf(table.header) + table.rows
        val headerIndex = allRows.indexOfFirst { row -> row.count { parseDay(it) != null } >= 2 }
        if (headerIndex < 0) return null
        val dayColumns = allRows[headerIndex].mapIndexedNotNull { column, cell ->
            parseDay(cell)?.let { column to it }
        }.toMap()
        if (dayColumns.isEmpty()) return null

        val lessons = mutableMapOf<Int, MutableMap<Int, Lesson>>()
        for (row in allRows.drop(headerIndex + 1)) {
            val period = row.firstOrNull()?.let { periodFromCell(it) } ?: continue
            for ((column, day) in dayColumns) {
                val text = row.getOrNull(column)?.trim().orEmpty()
                if (text.isEmpty()) continue
                lessons.getOrPut(day) { mutableMapOf() }[period] = lessonFromCell(text)
            }
        }
        return lessons.takeIf { it.isNotEmpty() }?.let { TimetableWeek(it) }
    }

    /** 첫 칸 "교시"·"1"·"1교시" 등에서 교시 번호만 뽑습니다. */
    private fun periodFromCell(raw: String): Int? =
        Regex("\\d{1,2}").find(raw.trim())?.value?.toIntOrNull()?.takeIf { it in 1..12 }

    /** 빈 칸은 호출부가 걸러내고, `공강`은 [Lesson.FREE], 나머지는 과목/교실로 나눕니다. */
    private fun lessonFromCell(raw: String): Lesson {
        val text = raw.trim()
        if (isFree(text)) return Lesson.FREE
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size <= 1) return Lesson(text, null)
        val last = tokens.last()
        // 교실은 보통 마지막 토큰입니다 — "미적분Ⅱ B303", "스포츠 생활1(남) 체육관".
        return if (looksLikeRoom(last)) Lesson(tokens.dropLast(1).joinToString(" ").trim(), last) else Lesson(text, null)
    }

    private val roomTokenRegex = Regex("^[A-Za-z]-?\\d{2,4}$")
    private val roomSuffixRegex = Regex("(실|관|층|장)\\d*$")
    private val knownRoomTokens = setOf(
        "체육관", "강당", "도서관", "생활관", "아트센터", "시청각실", "세미나실",
        "음악실", "미술실", "무용실", "컴퓨터실", "골프장", "테니스장",
    )

    /** `B301`·`A205`·`컴퓨터실2`·`체육관`처럼 교실로 보이는 토큰인지 — 과목명 오인을 피하려 좁게 봅니다. */
    private fun looksLikeRoom(token: String): Boolean {
        val t = token.trim()
        return when {
            t.isEmpty() -> false
            t in knownRoomTokens -> true
            roomTokenRegex.matches(t) -> true
            Regex("^\\d{3,4}$").matches(t) -> true
            else -> roomSuffixRegex.containsMatchIn(t)
        }
    }

    /** 태그로 남길 그리드 요약 — 요일별 칸 수와 실제 과목/교실 목록을 앞부분만. */
    private fun gridSummary(week: TimetableWeek?): String {
        if (week == null || week.lessons.isEmpty()) return "빈 그리드"
        val names = listOf("월", "화", "수", "목", "금")
        val counts = names.mapIndexed { index, label -> "$label=${week.lessons[index + 1]?.size ?: 0}" }.joinToString(",")
        val total = week.lessons.values.sumOf { it.size }
        val detail = (1..5).mapNotNull { day ->
            val cells = week.lessons[day]?.toSortedMap()?.entries?.joinToString(",") { (period, lesson) ->
                "$period:${lesson.subject}${lesson.room?.let { "/$it" } ?: ""}"
            } ?: return@mapNotNull null
            "${names[day - 1]}{$cells}"
        }.joinToString(" ")
        return "$counts (총 $total) · ${detail.take(1200)}"
    }

    // MARK: 응답 해석

    private val rowArrayKeys = listOf("itemList", "list", "result", "data", "rows")

    private fun parseWeek(json: JSONObject): TimetableWeek? {
        val array = firstRowArray(json) ?: return null
        val lessons = mutableMapOf<Int, MutableMap<Int, Lesson>>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val day = dayOfWeek(row) ?: continue
            val period = period(row) ?: continue
            val subject = subject(row) ?: continue
            val room = room(row)
            // 공강은 앱 모델과 동일하게 Lesson.FREE(= room null) 로 맞춥니다.
            val lesson = if (room == null && isFree(subject)) Lesson.FREE else Lesson(subject, room)
            lessons.getOrPut(day) { mutableMapOf() }[period] = lesson
        }
        if (lessons.isEmpty()) return null
        // 교시 시각은 여기서 뽑지 않고 항상 검증된 [Timetable.periodTimes] 를 씁니다 — 이 JSON 은
        // 확정된 형식이 아닌 추측 후보 응답이라, 우연히 매칭되는 배열/필드가 있으면
        // (예: 1교시가 07:00~09:10처럼) 엉뚱한 교시 시각으로 덮어써 버릴 위험이 있었습니다.
        return TimetableWeek(lessons)
    }

    private fun firstRowArray(json: JSONObject): JSONArray? {
        rowArrayKeys.forEach { key ->
            json.optJSONArray(key)?.takeIf { it.length() > 0 }?.let { return it }
        }
        json.optJSONObject("paging")?.let { paging ->
            rowArrayKeys.forEach { key -> paging.optJSONArray(key)?.takeIf { it.length() > 0 }?.let { return it } }
        }
        json.optJSONObject("result")?.let { result ->
            rowArrayKeys.forEach { key -> result.optJSONArray(key)?.takeIf { it.length() > 0 }?.let { return it } }
        }
        return null
    }

    // MARK: 행 필드 해석 (요일·교시·과목·교실)

    private val dayKeys = listOf(
        "day", "yoil", "week", "weekday", "dow", "yoil_cd", "yoil_nm",
        "day_of_week", "st_day", "day_cd", "day_nm",
    )
    private val periodKeys = listOf(
        "period", "prd", "period_no", "prd_no", "prd_cd", "st_nm",
        "교시", "class_no", "ord", "seq", "cha",
    )
    private val subjectKeys = listOf(
        "sbj_nm", "subject", "subject_nm", "class_nm", "cls_nm", "crs_nm",
        "otl_nm", "sbj", "과목", "교과",
    )
    private val roomKeys = listOf(
        "room_nm", "slp_nm", "classroom", "class_room", "room", "place",
        "loc", "slc_nm", "강의실", "교실",
    )

    private fun dayOfWeek(row: JSONObject): Int? {
        for (key in dayKeys) {
            val value = row.optString(key).trim()
            if (value.isEmpty() || value == "null") continue
            parseDay(value)?.let { return it }
        }
        return null
    }

    private fun parseDay(raw: String): Int? {
        val text = raw.uppercase()
        listOf("월" to 1, "화" to 2, "수" to 3, "목" to 4, "금" to 5, "토" to 6, "일" to 7)
            .firstOrNull { text.contains(it.first) }?.let { return it.second }
        listOf(
            "MON" to 1, "TUE" to 2, "WED" to 3, "THU" to 4, "FRI" to 5, "SAT" to 6, "SUN" to 7,
        ).firstOrNull { text.contains(it.first) }?.let { return it.second }
        val number = Regex("\\d{1,2}").find(text)?.value?.toIntOrNull() ?: return null
        return when (number) {
            in 1..7 -> number
            0 -> 7
            else -> null
        }
    }

    private fun period(row: JSONObject): Int? {
        for (key in periodKeys) {
            val value = row.optString(key).trim()
            if (value.isEmpty() || value == "null") continue
            Regex("\\d{1,2}").find(value)?.value?.toIntOrNull()?.takeIf { it in 1..12 }?.let { return it }
        }
        return null
    }

    private fun subject(row: JSONObject): String? =
        firstString(row, subjectKeys)?.trim()?.takeIf { it.isNotEmpty() }

    private fun room(row: JSONObject): String? =
        firstString(row, roomKeys)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun firstString(row: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            if (!row.has(key) || row.isNull(key)) continue
            val value = row.optString(key)
            if (value.isNotEmpty() && value != "null") return value
        }
        return null
    }

    private fun isFree(subject: String): Boolean =
        subject.contains("공강") || subject.contains("공백") || subject.contains("휴강") || subject == "없음"
}

// MARK: - 캐시 + 메모리 설치

/**
 * 포털 시간표를 주 단위 JSON 으로 캐시하고 [Timetable] 에 설치합니다.
 * 네트워크 실패 시 만료 캐시 순으로 조용히 내려가며, 그것도 없으면 설치하지 않고
 * 예외를 던지지 않습니다 — 내장 기본 시간표로 폴백하지 않습니다.
 */
object HanaTimetableSync {

    private const val TAG = "HanaTimetable"
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val NAME_PREFIX = "timetable_week_"
    private const val NAME_LATEST = "timetable_latest"

    /**
     * 포털 재조회([force]) 또는 TTL 내 캐시로 시간표를 갱신하고 설치합니다.
     * 어떤 경우에도 예외를 던지지 않아 장소 동기화를 막지 않습니다.
     */
    suspend fun refresh(
        context: Context,
        date: LocalDate = PlanStore.today(),
        force: Boolean = false,
    ): TimetableWeek? {
        val name = NAME_PREFIX + weekKey(date)
        val stored = PlanStore.cachedJson(context, name)
        if (!force && stored != null && System.currentTimeMillis() - stored.at <= TTL_MS) {
            return TimetableWeek.fromJson(stored.value)?.also { Timetable.installFetched(it) }
        }

        // 비밀번호 오류로 HanaSyncGate 가 정지시킨 동안엔 포털에 다시 로그인 시도하지 않고
        // 캐시만 씁니다 — HanaSyncWorker 와 같은 5회 실패 잠금 방지 회로차단기입니다.
        if (!HanaSyncGate.isSuspended(context)) {
            val fresh = try {
                HanaTimetableApi.fetch(context, date)
            } catch (e: HanaPortalException.LoginFailed) {
                HanaSyncGate.suspend(context)
                null
            } catch (e: Exception) {
                null
            }
            if (fresh != null && fresh.lessons.isNotEmpty()) {
                HanaSyncGate.clear(context)
                val encoded = fresh.toJson()
                PlanStore.writeCachedJson(context, name, encoded)
                PlanStore.writeCachedJson(context, NAME_LATEST, encoded)
                Timetable.installFetched(fresh)
                return fresh
            }
        }

        // 실패 — 만료 캐시라도 유지하고, 그것도 없으면 이미 설치돼 있던 표를 그대로 둡니다
        // (표를 아예 못 받았다고 해서 메모리에 있던 멀쩡한 표까지 지우지 않습니다).
        val fallback = stored?.let { TimetableWeek.fromJson(it.value) } ?: latest(context)
        if (fallback != null) {
            Timetable.installFetched(fallback)
            return fallback
        }
        Log.d(TAG, "포털 시간표를 받지 못했습니다 — 기존에 설치된 표를 유지합니다")
        return null
    }

    /**
     * 네트워크를 타지 않고 캐시만 설치합니다. 앱/위젯 프로세스가 새로 뜰 때 설치된 표가
     * 없으면 캐시된 포털 시간표를 먼저 반영하기 위한 것으로, 이미 설치돼 있으면 즉시 돌아옵니다.
     */
    suspend fun ensureInstalled(context: Context, date: LocalDate = PlanStore.today()) {
        if (Timetable.fetchedWeek() != null) return
        val week = PlanStore.cachedJson(context, NAME_PREFIX + weekKey(date))?.let { TimetableWeek.fromJson(it.value) }
            ?: latest(context)
        if (week != null) Timetable.installFetched(week)
    }

    private suspend fun latest(context: Context): TimetableWeek? =
        PlanStore.cachedJson(context, NAME_LATEST)?.let { TimetableWeek.fromJson(it.value) }

    /** 주 단위 캐시 키 — 그 주 월요일 날짜. */
    fun weekKey(date: LocalDate): String = date.with(DayOfWeek.MONDAY).toString()
}

// MARK: - 마지막으로 성공한 엔드포인트 기억

private object TimetableEndpointStore {

    private const val PREFS_NAME = "hana_timetable"
    private const val KEY_ENDPOINT = "endpoint"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context, count: Int): Int? =
        prefs(context).getInt(KEY_ENDPOINT, -1).takeIf { it in 0 until count }

    fun set(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_ENDPOINT, index).apply()
    }
}
