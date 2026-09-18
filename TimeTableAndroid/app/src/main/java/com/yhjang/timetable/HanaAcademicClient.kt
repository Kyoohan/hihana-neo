package com.yhjang.timetable

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// MARK: - 모델

/** 알리미 한 건 — [id] 는 att_idx, [content] 는 HTML 디코드·태그 제거를 마친 본문입니다. */
data class HanaAlim(
    val id: String,
    val content: String,
    val writer: String?,
    val date: String?,
    val read: Boolean,
)

/**
 * 학사일정 한 건. [date] 는 더 이상 응답에서 추측하지 않습니다 — 하루씩 조회하면서
 * 그 조회에 쓴 날짜를 그대로 붙이므로 신뢰할 수 있습니다.
 */
data class HanaScheduleEntry(
    val code: String,
    val name: String,
    val place: String?,
    val startTime: String?,
    val endTime: String?,
    val allDay: Boolean,
    val date: LocalDate,
    /** 면학감독(SG03010000) 대상 학년 — `smm_class` 원값(예: "2"). 다른 일정은 null. */
    val grade: String? = null,
    /** 면학감독 담당 교사 — `in_mem_name`/`tc_mem_name` 을 디코드·정리한 값. */
    val teacher: String? = null,
    /** mainYn=Y 요약 조회에서 병합된 행인지 — 같은 슬롯·학년에 상세 행이 있으면 밀립니다. */
    val mainYn: Boolean = false,
)

/** 게시판 글 한 건. [url] 은 포털 상세 페이지 — 앱은 웹으로만 엽니다. */
data class HanaBoardPost(
    val bmtIdx: Int,
    val bdIdx: Int,
    val title: String,
    val writer: String?,
    val date: String?,
    val isNew: Boolean,
    val grades: List<String>,
    val url: String,
)

/** 시험 D-day 카드용 — 오늘(KST) 기준 가장 가까운 미래 시험. */
data class ExamDday(val name: String, val date: LocalDate, val days: Long)

/** 포털 게시판 탭 — bmtIdx 값은 캡처된 HTML 기준입니다. */
enum class BoardCategory(val bmtIdx: Int, val label: String) {
    STUDENT_NOTICE(1, "학생공지"),
    SUBJECT_NOTICE(3, "교과공지"),
    HOME_LETTER(4, "가정통신문"),
    WOOJEONG(6, "우정학사공지"),
    STUDENT_BOARD(7, "학생게시판");

    /** 가정통신문만 추가 필터(bdField10=2)가 필요합니다. */
    val extraParams: List<Pair<String, String>>
        get() = if (this == HOME_LETTER) listOf("bdField10" to "2") else emptyList()
}

// MARK: - JSON 헬퍼

private fun JSONObject.optStr(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() && it != "null" } else null

/** `&lt;b&gt;...` 같은 HTML 엔티티를 풀고 태그를 제거한 뒤 공백을 정리합니다. */
private fun decodeHtml(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return runCatching {
        android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    }.getOrElse { raw }.replace(Regex("\\s+"), " ").trim()
}

private fun normalizePortalUrl(raw: String?, bmtIdx: Int, bdIdx: Int): String {
    val base = "https://hh.hana.hs.kr"
    val fallback = "$base/main/board/$bmtIdx/$bdIdx/board_view.do"
    if (raw.isNullOrBlank()) return fallback
    return when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("/") -> base + raw
        else -> "$base/$raw"
    }
}

// MARK: - 네트워크 (인증 필요)

/**
 * 면학감독 행의 원문 필드를 한 번의 일정 조회당 최대 [maxRows] 행까지 남깁니다. 포털마다
 * `sch_cd`/`sch_nm`/`smm_class` 표기가 달라 파싱이 빗나갈 때 사용자가 원문을 그대로
 * 신고할 수 있게 하는 진단용 로그입니다.
 */
private class SupervisionDiagnostics(private val maxRows: Int = 10) {

    private var remaining = maxRows

    fun log(list: JSONArray) {
        var index = 0
        while (index < list.length() && remaining > 0) {
            val row = list.optJSONObject(index) ?: run { index++; continue }
            if (row.optString("sch_cd") != SUPERVISION_CODE) { index++; continue }
            Log.d(
                SUPERVISION_TAG,
                "sch_cd=${row.optString("sch_cd")} | sch_nm=${row.optString("sch_nm")} | " +
                    "smm_class=${row.optString("smm_class")} | in_mem_name=${row.optString("in_mem_name")} | " +
                    "tc_mem_name=${row.optString("tc_mem_name")} | st_time=${row.optString("st_time")}",
            )
            remaining--
            index++
        }
    }
}

/**
 * 알리미·학사일정·게시판 조회. 로그인/쿠키/헤더는 [HanaPortalClient] 를 그대로 재사용합니다.
 * 응답이 `result`/`paging` 구조가 아니면 빈 목록을 돌려주지 않고 예외를 던져, 호출부가
 * "연동 필요" 상태를 구분할 수 있게 합니다(로그인 페이지 HTML 을 빈 목록으로 오인하지 않도록).
 */
object HanaAcademicApi {

    private const val BASE = "https://hh.hana.hs.kr"
    private val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 학사일정 파싱 실패를 진단할 때 쓰는 Logcat 태그 — 사용자가 원문을 그대로 신고할 수 있게 합니다. */
    private const val TAG = "HanaSchedule"

    /**
     * 학사일정 조회 파라미터 변형. 포털 버전에 따라 mainYn/stdYn 지원 여부와 날짜 형식이
     * 달라서, 되는 조합을 찾을 때까지 순서대로 시도하고 성공한 조합을 기억합니다.
     */
    private data class ScheduleVariant(val datePattern: String, val extra: List<Pair<String, String>>)

    private val scheduleVariants: List<ScheduleVariant> = buildList {
        // 1~3: 필터 없이 날짜 형식만 바꿔가며
        listOf("yyyy-MM-dd", "yyyy.MM.dd", "yyyyMMdd").forEach { add(ScheduleVariant(it, emptyList())) }
        // 4: 위젯용 요약(mainYn=Y)
        listOf("yyyy-MM-dd", "yyyy.MM.dd", "yyyyMMdd").forEach { add(ScheduleVariant(it, listOf("mainYn" to "Y"))) }
        // 5: 학생 필터(stdYn=Y)
        listOf("yyyy-MM-dd", "yyyy.MM.dd", "yyyyMMdd").forEach { add(ScheduleVariant(it, listOf("stdYn" to "Y"))) }
    }

    /** 포털 메인 위젯 요약에서만 면학감독이 내려오는 경우를 위한 추가 필터. */
    private val MAIN_VARIANT_EXTRA = listOf("mainYn" to "Y")

    suspend fun fetchAlim(context: Context): List<HanaAlim> {
        val json = HanaPortalClient.get().authenticatedJson(
            context,
            "/main/alim/alim-target-list.json",
            listOf("pageSize" to "7", "alimManYn" to "Y", "filterReserve" to "Y"),
            isValid = { it.optJSONObject("paging")?.has("result") == true },
        )
        val result = json.optJSONObject("paging")?.optJSONArray("result") ?: return emptyList()
        return (0 until result.length()).mapNotNull { index ->
            val row = result.optJSONObject(index) ?: return@mapNotNull null
            val id = row.opt("att_idx")?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            HanaAlim(
                id = id,
                content = decodeHtml(row.optStr("ait_cont")),
                writer = row.optStr("send_mem_name") ?: row.optStr("writer"),
                date = row.optStr("display_inputdate") ?: row.optStr("inputdate"),
                read = listOf("readYn", "ait_read_yn", "ait_vyn").any { row.optStr(it) == "Y" },
            )
        }
    }

    /**
     * 학사지원시스템의 상세 학사일정. [start]~[end] 를 하루씩 질의해 각 행에 그 날짜를
     * 붙입니다 — 날짜를 응답에서 추측하지 않아도 됩니다.
     *
     * 자격증명 없음/로그인 실패는 15일 × 변형 수만큼 반복하면 계정 잠금 위험이 있어
     * 즉시 중단하고 그대로 던집니다. 그 외 오류는 하루라도 성공하면 부분 결과를 돌려줍니다.
     */
    suspend fun fetchSchedule(context: Context, start: LocalDate, end: LocalDate): List<HanaScheduleEntry> {
        val entries = mutableListOf<HanaScheduleEntry>()
        var firstError: Exception? = null
        var anySuccess = false
        // 진단 로그는 이 한 번의 조회 전체에서 최대 10행만 남깁니다.
        val supervisionDiagnostics = SupervisionDiagnostics()

        var day = start
        while (!day.isAfter(end)) {
            val date = day
            try {
                val rows = fetchScheduleDay(context, date, supervisionDiagnostics)
                anySuccess = true
                entries += rows
            } catch (e: HanaPortalException.MissingCredentials) {
                throw e
            } catch (e: HanaPortalException.LoginFailed) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
            day = day.plusDays(1)
        }

        if (!anySuccess && firstError != null) throw firstError
        return entries
    }

    /** 하루치 응답 — 파싱한 일정, 인식한 배열 키가 있었는지, 원문(진단 로그용). */
    private data class ScheduleDayResult(
        val entries: List<HanaScheduleEntry>,
        val recognized: Boolean,
        val raw: String,
    )

    /**
     * 하루치 조회. 기억해 둔 변형이 있으면 먼저 시도하고, 없거나 실패하면 변형들을 순서대로
     * 시도해 **비어 있지 않은** 목록을 주는 첫 조합을 기억합니다. 모든 변형이 빈 목록이면
     * (배열 키는 있으나 행이 없는 경우) 그중 첫 유효 변형을 기억해 다음 날부터 한 번에 조회합니다.
     * 하나도 유효하지 않고 전부 예외면 첫 예외를 다시 던져 로그인/오프라인을 구분합니다.
     *
     * 상세 변형이 면학감독 행을 누락시키는 경우가 있어, 그 날짜에 감독 행이 하나도 없으면
     * [mergeMissingSupervision] 으로 mainYn 변형을 한 번만 더 조회합니다.
     */
    private suspend fun fetchScheduleDay(
        context: Context,
        date: LocalDate,
        diagnostics: SupervisionDiagnostics? = null,
    ): List<HanaScheduleEntry> {
        val remembered = ScheduleVariantStore.get(context, scheduleVariants.size)
        if (remembered != null) {
            val variant = scheduleVariants[remembered]
            val result = runCatching { requestScheduleDay(context, date, variant, diagnostics) }.getOrNull()
            if (result != null && result.recognized) {
                return mergeMissingSupervision(context, date, result.entries, variant, diagnostics)
            }
        }

        var firstError: Exception? = null
        var firstValid: ScheduleDayResult? = null
        var firstValidIndex = -1
        var firstRaw: String? = null

        scheduleVariants.forEachIndexed { index, variant ->
            val result = try {
                requestScheduleDay(context, date, variant, diagnostics)
            } catch (e: Exception) {
                if (firstError == null) firstError = e
                return@forEachIndexed
            }
            if (firstRaw == null) firstRaw = result.raw
            if (result.recognized && firstValid == null) {
                firstValid = result
                firstValidIndex = index
            }
            if (result.entries.isNotEmpty()) {
                ScheduleVariantStore.set(context, index)
                return mergeMissingSupervision(context, date, result.entries, variant, diagnostics)
            }
        }

        firstValid?.let { valid ->
            ScheduleVariantStore.set(context, firstValidIndex)
            val variant = scheduleVariants[firstValidIndex]
            return mergeMissingSupervision(context, date, valid.entries, variant, diagnostics)
        }
        // 모든 변형이 실패 — 원문을 남겨 사용자가 신고할 수 있게 합니다.
        firstRaw?.let { Log.d(TAG, "학사일정 응답(모든 변형 실패, 첫 원문): ${it.take(2000)}") }
        firstError?.let { throw it }
        return emptyList()
    }

    /**
     * 그 날짜의 조회 결과에 면학감독(`SG03010000`) 행이 없으면, 같은 날짜 형식의
     * `mainYn=Y`(포털 메인 위젯) 변형을 **하루 최대 한 번** 더 조회해 감독 행만 병합합니다.
     * 로그인 세션을 재사용하므로 추가 로그인은 없고, 이미 mainYn 이면 다시 시도하지 않습니다.
     * 병합 시 날짜·코드·이름·시간·학년·교사가 같은 행은 중복으로 넣지 않습니다.
     */
    private suspend fun mergeMissingSupervision(
        context: Context,
        date: LocalDate,
        entries: List<HanaScheduleEntry>,
        variant: ScheduleVariant,
        diagnostics: SupervisionDiagnostics?,
    ): List<HanaScheduleEntry> {
        if (entries.any { it.code == SUPERVISION_CODE }) return entries
        if (variant.extra == MAIN_VARIANT_EXTRA) return entries
        val mainIndex = scheduleVariants.indexOfFirst {
            it.datePattern == variant.datePattern && it.extra == MAIN_VARIANT_EXTRA
        }
        if (mainIndex < 0) return entries

        val supervision = try {
            requestScheduleDay(context, date, scheduleVariants[mainIndex], diagnostics)
                .entries
                .filter { it.code == SUPERVISION_CODE }
        } catch (e: Exception) {
            return entries
        }
        if (supervision.isEmpty()) return entries

        val existing = entries.mapTo(HashSet()) { it.stableKey() }
        // 병합 직후에도 한 번 더 정리 — 상세/요약 양쪽에 같은 슬롯·학년이 있으면 상세 행만 남깁니다.
        return dedupeSupervision(entries + supervision.filter { it.stableKey() !in existing })
    }

    private suspend fun requestScheduleDay(
        context: Context,
        date: LocalDate,
        variant: ScheduleVariant,
        diagnostics: SupervisionDiagnostics? = null,
    ): ScheduleDayResult {
        val day = date.format(DateTimeFormatter.ofPattern(variant.datePattern))
        val params = mutableListOf(
            "pageSize" to "1000",
            "schStartDay" to day,
            "schEndDay" to day,
        )
        params += variant.extra

        val json = HanaPortalClient.get().authenticatedJson(context, "/main/schedule/hana-manage-data.json", params)
        val list = json.optJSONArray("itemList")
            ?: json.optJSONArray("list")
            ?: json.optJSONObject("paging")?.optJSONArray("result")
        val raw = json.toString()
        if (list == null) return ScheduleDayResult(emptyList(), recognized = false, raw = raw)
        diagnostics?.log(list)

        val entries = (0 until list.length()).mapNotNull { index ->
            val row = list.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optStr("sch_cd").orEmpty()
            val name = decodeHtml(row.optStr("sch_nm"))
            // 면학감독은 일정명이 비어 있어도 담당 교사로 표시할 수 있어 남깁니다.
            if (name.isEmpty() && code != SUPERVISION_CODE) return@mapNotNull null
            HanaScheduleEntry(
                code = code,
                name = name,
                place = row.optStr("slp_nm"),
                startTime = row.optStr("st_time"),
                endTime = row.optStr("ed_time"),
                allDay = row.optStr("all_day")?.uppercase(Locale.ROOT) in listOf("Y", "1", "TRUE"),
                date = date,
                grade = row.optStr("smm_class")?.trim()?.takeIf { it.isNotEmpty() },
                teacher = decodeHtml(row.optStr("in_mem_name") ?: row.optStr("tc_mem_name")).takeIf { it.isNotEmpty() },
                mainYn = variant.extra == MAIN_VARIANT_EXTRA,
            )
        }
        return ScheduleDayResult(dedupeSupervision(entries), recognized = true, raw = raw)
    }

    suspend fun fetchBoard(context: Context, category: BoardCategory): List<HanaBoardPost> {
        // jQuery 배열 직렬화는 `bmtIdx[]=`, 서버가 List 로 받으면 `bmtIdx=` 라, 둘 다 보내 안전하게 합니다.
        val params = mutableListOf(
            "pageSize" to "10",
            "bmtIdx" to category.bmtIdx.toString(),
            "bmtIdx[]" to category.bmtIdx.toString(),
        )
        params += category.extraParams

        val json = HanaPortalClient.get().authenticatedJson(
            context,
            "/main/board/board_main_list.json",
            params,
            isValid = { it.has("noticeList") },
        )
        val list = json.optJSONArray("noticeList") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val row = list.optJSONObject(index) ?: return@mapNotNull null
            val bmtIdx = row.optInt("bmt_idx", category.bmtIdx)
            val bdIdx = row.optInt("bd_idx", -1)
            HanaBoardPost(
                bmtIdx = bmtIdx,
                bdIdx = bdIdx,
                title = decodeHtml(row.optStr("bd_title")),
                writer = row.optStr("bd_writer") ?: row.optStr("in_mem_name"),
                date = row.optStr("inputdate"),
                isNew = row.optStr("new_yn") == "Y",
                grades = row.optStr("bd_title_header")
                    ?.split("|")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty(),
                url = normalizePortalUrl(row.optStr("bd_url"), bmtIdx, bdIdx),
            )
        }
    }

    /**
     * 게시글 상세 HTML 원문. 시험범위 표를 파싱하려면 WebView 가 아니라 원문이 필요합니다.
     * [url] 은 [HanaBoardPost.url] 처럼 절대 URL 이며, 포털 호스트 기준 경로만 떼어 인증 요청합니다.
     */
    suspend fun fetchBoardDetail(context: Context, url: String): String {
        val path = url.removePrefix(BASE).takeIf { it.startsWith("/") } ?: url
        return HanaPortalClient.get().authenticatedText(context, path, method = "GET", referer = url)
    }
}

// MARK: - 캐시 (오프라인 내성)

/**
 * 알리미 10분 · 게시판 10분 · 학사일정 1일 TTL 캐시.
 * 네트워크 실패 시 만료된 캐시라도 돌려주어 오프라인에서도 화면이 비지 않게 합니다.
 */
object HanaAcademicRepository {

    private const val TTL_ALIM = 10 * 60 * 1000L
    private const val TTL_BOARD = 10 * 60 * 1000L
    private const val TTL_SCHEDULE = 24 * 60 * 60 * 1000L

    suspend fun alim(context: Context, force: Boolean = false): List<HanaAlim> =
        cached(context, "academic_alim", TTL_ALIM, force, ::decodeAlim, ::encodeAlim, keepNonEmpty = true) {
            HanaAcademicApi.fetchAlim(context)
        }

    suspend fun board(context: Context, category: BoardCategory, force: Boolean = false): List<HanaBoardPost> =
        cached(context, "academic_board_${category.bmtIdx}", TTL_BOARD, force, ::decodeBoard, ::encodeBoard, keepNonEmpty = true) {
            HanaAcademicApi.fetchBoard(context, category)
        }

    /**
     * 학사일정은 다른 캐시와 달리 빈 응답으로 기존 캐시를 덮어쓰지 않습니다. 포털 파라미터가
     * 안 맞아 결과가 비었을 때 이전 일정이라도 남겨 두고, UI 가 '다시 시도'를 띄울 수 있게 합니다.
     */
    suspend fun schedule(context: Context, force: Boolean = false): List<HanaScheduleEntry> {
        val today = PlanStore.today()
        val name = "academic_schedule"
        val stored = PlanStore.cachedJson(context, name)
        if (!force && stored != null && System.currentTimeMillis() - stored.at <= TTL_SCHEDULE) {
            return decodeSchedule(stored.value)
        }

        val fresh = try {
            // 오늘-1 ~ 오늘+13 (2주) 를 하루씩 질의합니다 — 요약이 아닌 상세 학사일정.
            HanaAcademicApi.fetchSchedule(context, today.minusDays(1), today.plusDays(13))
        } catch (e: Exception) {
            stored?.let { return decodeSchedule(it.value) }
            throw e
        }

        if (fresh.isEmpty()) return emptyList()
        PlanStore.writeCachedJson(context, name, encodeSchedule(fresh))
        return fresh
    }

    /**
     * 네트워크를 타지 않고 캐시된 학사일정만 읽습니다. 위젯 컴포지션처럼 네트워크를
     * 기다릴 수 없는 곳에서 씁니다 — 캐시가 없으면 빈 목록입니다.
     */
    suspend fun cachedSchedule(context: Context): List<HanaScheduleEntry> {
        val stored = PlanStore.cachedJson(context, "academic_schedule") ?: return emptyList()
        return decodeSchedule(stored.value)
    }

    /**
     * [keepNonEmpty] 면 새 응답이 비었을 때 이전의 비어 있지 않은 캐시를 덮어쓰지 않고 그걸 돌려줍니다 —
     * 세션 문제로 잠깐 빈 목록이 와도 10분 동안 "없음"으로 굳어 버리지 않게 합니다.
     */
    private suspend fun <T> cached(
        context: Context,
        name: String,
        ttl: Long,
        force: Boolean,
        decode: (String) -> List<T>,
        encode: (List<T>) -> String,
        keepNonEmpty: Boolean = false,
        fetch: suspend () -> List<T>,
    ): List<T> {
        val stored = PlanStore.cachedJson(context, name)
        if (!force && stored != null && System.currentTimeMillis() - stored.at <= ttl) {
            return decode(stored.value)
        }
        return try {
            val fresh = fetch()
            if (keepNonEmpty && fresh.isEmpty() && stored != null) {
                val previous = decode(stored.value)
                if (previous.isNotEmpty()) return previous
            }
            PlanStore.writeCachedJson(context, name, encode(fresh))
            fresh
        } catch (e: Exception) {
            stored?.let { return decode(it.value) }
            throw e
        }
    }

    // MARK: 직렬화

    private fun encodeAlim(list: List<HanaAlim>): String {
        val array = org.json.JSONArray()
        list.forEach { alim ->
            array.put(
                JSONObject().apply {
                    put("id", alim.id)
                    put("content", alim.content)
                    put("writer", alim.writer)
                    put("date", alim.date)
                    put("read", alim.read)
                },
            )
        }
        return array.toString()
    }

    private fun decodeAlim(raw: String): List<HanaAlim> = runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { row ->
                val id = row.optString("id")
                if (id.isEmpty()) null else HanaAlim(
                    id = id,
                    content = row.optString("content"),
                    writer = row.optString("writer").takeIf { it.isNotEmpty() && it != "null" },
                    date = row.optString("date").takeIf { it.isNotEmpty() && it != "null" },
                    read = row.optBoolean("read"),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeSchedule(list: List<HanaScheduleEntry>): String {
        val array = org.json.JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("code", entry.code)
                    put("name", entry.name)
                    put("place", entry.place)
                    put("start", entry.startTime)
                    put("end", entry.endTime)
                    put("allDay", entry.allDay)
                    put("date", entry.date.toString())
                    put("grade", entry.grade)
                    put("teacher", entry.teacher)
                    put("mainYn", entry.mainYn)
                },
            )
        }
        return array.toString()
    }

    private fun decodeSchedule(raw: String): List<HanaScheduleEntry> = runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { row ->
                // 날짜가 없는 구버전 캐시 행은 그룹핑할 수 없어 버립니다.
                val date = row.optString("date").takeIf { it.isNotEmpty() && it != "null" }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@let null
                HanaScheduleEntry(
                    code = row.optString("code"),
                    name = row.optString("name"),
                    place = row.optString("place").takeIf { it.isNotEmpty() && it != "null" },
                    startTime = row.optString("start").takeIf { it.isNotEmpty() && it != "null" },
                    endTime = row.optString("end").takeIf { it.isNotEmpty() && it != "null" },
                    allDay = row.optBoolean("allDay"),
                    date = date,
                    grade = row.optString("grade").takeIf { it.isNotEmpty() && it != "null" },
                    teacher = row.optString("teacher").takeIf { it.isNotEmpty() && it != "null" },
                    mainYn = row.optBoolean("mainYn"),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeBoard(list: List<HanaBoardPost>): String {
        val array = org.json.JSONArray()
        list.forEach { post ->
            array.put(
                JSONObject().apply {
                    put("bmtIdx", post.bmtIdx)
                    put("bdIdx", post.bdIdx)
                    put("title", post.title)
                    put("writer", post.writer)
                    put("date", post.date)
                    put("isNew", post.isNew)
                    put("grades", org.json.JSONArray(post.grades))
                    put("url", post.url)
                },
            )
        }
        return array.toString()
    }

    private fun decodeBoard(raw: String): List<HanaBoardPost> = runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { row ->
                val gradesArray = row.optJSONArray("grades")
                val grades = if (gradesArray == null) emptyList() else
                    (0 until gradesArray.length()).mapNotNull { gradesArray.optString(it).takeIf { g -> g.isNotEmpty() } }
                HanaBoardPost(
                    bmtIdx = row.optInt("bmtIdx"),
                    bdIdx = row.optInt("bdIdx"),
                    title = row.optString("title"),
                    writer = row.optString("writer").takeIf { it.isNotEmpty() && it != "null" },
                    date = row.optString("date").takeIf { it.isNotEmpty() && it != "null" },
                    isNew = row.optBoolean("isNew"),
                    grades = grades,
                    url = row.optString("url"),
                )
            }
        }
    }.getOrDefault(emptyList())
}

// MARK: - 학사일정 변형 기억

/**
 * 어떤 날짜 형식/필터 조합이 먹혔는지 저장합니다. 앱 버전이 바뀌어 조합이 달라지면
 * 새 조합을 다시 찾도록, 저장된 인덱스가 현재 변형 수를 벗어나면 무시합니다.
 */
private object ScheduleVariantStore {

    private const val PREFS_NAME = "hana_schedule"
    private const val KEY_VARIANT = "variant"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context, count: Int): Int? =
        prefs(context).getInt(KEY_VARIANT, -1).takeIf { it in 0 until count }

    fun set(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_VARIANT, index).apply()
    }
}

// MARK: - 알림 (알리미)

/** 이미 알림을 띄운 att_idx 를 저장해 새 글에만 알림이 가게 합니다. */
object AlimSeenStore {

    private const val PREFS_NAME = "alim_seen"
    private const val KEY_IDS = "ids"
    private const val MAX_KEEP = 500

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun seen(context: Context): Set<String> = prefs(context).getStringSet(KEY_IDS, emptySet()).orEmpty()

    fun markSeen(context: Context, ids: Collection<String>) {
        val merged = (seen(context) + ids).toList().takeLast(MAX_KEEP).toSet()
        prefs(context).edit().putStringSet(KEY_IDS, merged).apply()
    }
}

/**
 * 사용자가 앱에서 읽은 att_idx 를 저장합니다. 알림 발송 여부를 기록하는 [AlimSeenStore] 와
 * 목적이 달라 별도 저장소를 씁니다 — 서버 readYn 이 안 내려와도 읽음 표시가 유지되게 합니다.
 */
object AlimReadStore {

    private const val PREFS_NAME = "alim_read"
    private const val KEY_IDS = "ids"
    private const val MAX_KEEP = 1000

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): Set<String> = prefs(context).getStringSet(KEY_IDS, emptySet()).orEmpty()

    fun markRead(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val merged = (read(context) + ids).toList().takeLast(MAX_KEEP).toSet()
        prefs(context).edit().putStringSet(KEY_IDS, merged).apply()
    }
}

/**
 * 새로 온 안 읽은 알리미에 대해서만 알림을 띄웁니다.
 * API 33+ 는 POST_NOTIFICATIONS 런타임 권한이 있어야 실제로 표시됩니다.
 */
object AlimNotifier {

    const val CHANNEL_ID = "alim"
    const val EXTRA_OPEN_ALIM = "com.yhjang.timetable.OPEN_ALIM"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "알리미", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "하이하나 알리미 새 글 알림"
                },
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun process(context: Context, alims: List<HanaAlim>) {
        if (!canNotify(context)) return
        val seen = AlimSeenStore.seen(context)
        val fresh = alims.filter { !it.read && it.id !in seen }
        if (fresh.isEmpty()) return

        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return

        fresh.forEach { alim ->
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_ALIM, true)
            }
            val pending = PendingIntent.getActivity(
                context,
                alim.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val body = alim.content.ifEmpty { "새 알리미가 도착했습니다" }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_school)
                .setContentTitle("새 알리미")
                .setContentText(body.take(60))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            manager.notify(alim.id.hashCode(), notification)
        }
        AlimSeenStore.markSeen(context, fresh.map { it.id })
    }
}

// MARK: - 시험 D-day

/** 중간/기말고사 등 `고사`가 들어간 일정 중 오늘 이후 가장 가까운 것을 찾습니다. */
fun examDday(entries: List<HanaScheduleEntry>, today: LocalDate): ExamDday? =
    entries.asSequence()
        .filter { entry ->
            entry.name.contains("중간고사") || entry.name.contains("기말고사") || entry.name.contains("고사")
        }
        .map { entry -> ExamDday(entry.name, entry.date, ChronoUnit.DAYS.between(today, entry.date)) }
        .filter { it.days >= 0 }
        .minByOrNull { it.days }

// MARK: - 면학감독 조회

/** 면학감독 일정 코드 — 학년 필터를 적용하는 유일한 일정 종류입니다. */
const val SUPERVISION_CODE = "SG03010000"

/** 면학감독 원문 진단 로그 태그 — 사용자가 이 태그로 실제 응답 형식을 신고할 수 있게 합니다. */
private const val SUPERVISION_TAG = "HanaSupervision"

/**
 * 같은 감독 행을 두 변형(상세 + mainYn)에서 중복 병합하지 않기 위한 키.
 * 날짜·코드·이름·장소·시간·종일·학년·교사가 모두 같으면 같은 행으로 봅니다.
 */
private fun HanaScheduleEntry.stableKey(): String =
    listOf(date, code, name, place, startTime, endTime, allDay, grade, teacher).joinToString("|")

/** `smm_class` 는 "1"/"01"/"1학년" 처럼 올 수 있어 앞자리 숫자만 비교합니다. 못 읽으면 null. */
fun parsedGrade(grade: String?): Int? =
    grade?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

/** 교사명·일정명 토큰 앞에 학년이 `2.김보경` / `2)김보경` 처럼 붙어 올 때의 접두사. */
private val GRADE_PREFIX = Regex("^\\s*([1-3])\\s*[.)]")

/** `sch_nm` 에 `N타임` 이 있으면 그 번호. */
private val SLOT_NAME = Regex("([0-9])\\s*타임")

/** `st_time` 이 "16:20~17:50"(범위) · "19:00-21:00" 형태로 와도 첫 시각 `HH:mm` 하나만 뽑습니다. */
private val CLOCK_HH_MM = Regex("(\\d{1,2}):(\\d{2})")

/** 토큰 하나 — 접두사에서 읽은 학년(없으면 null)과, 접두사를 떼고 남은 교사명. */
private data class SupervisionToken(val grade: Int?, val name: String)

/**
 * 교사명·일정명 문자열을 공백/쉼표로 나눈 뒤 각 토큰의 `N.` 접두사에서 학년과 이름을
 * 읽습니다. 포털이 `1.김보경 2.장숙경 3.신선옥` 처럼 한 문자열에 여러 학년을 몰아
 * 내려주는 경우를 위한 것으로, 접두사가 없으면 학년 미상(null) 토큰이 됩니다.
 */
private fun parseSupervisionTokens(raw: String?): List<SupervisionToken> =
    raw.orEmpty()
        .replace("선생님", " ")
        .split(Regex("[\\s,]+"))
        .mapNotNull { part ->
            val token = part.trim()
            if (token.isEmpty()) return@mapNotNull null
            val match = GRADE_PREFIX.find(token)
            if (match == null) {
                SupervisionToken(null, token)
            } else {
                val name = token.substring(match.range.last + 1).trim()
                if (name.isEmpty()) null else SupervisionToken(match.groupValues[1].toIntOrNull(), name)
            }
        }

/**
 * 한 감독 행이 담고 있는 (학년, 교사명) 목록 — 학년별로 행이 따로 오는 응답과 한 문자열에
 * 몰아 오는 응답을 모두 처리합니다. 교사명 문자열을 우선하고, 거기 학년 접두사가 하나도
 * 없는데 `smm_class` 가 있으면 그 학년을 모든 토큰에 적용합니다.
 */
private fun HanaScheduleEntry.supervisionTokens(): List<SupervisionToken> {
    val source = teacher?.takeIf { it.isNotBlank() } ?: name
    val tokens = parseSupervisionTokens(source)
    if (tokens.any { it.grade != null }) return tokens
    val rowGrade = parsedGrade(grade) ?: return tokens
    return tokens.map { it.copy(grade = rowGrade) }
}

/**
 * 선택 학년이 **확정된** 교사 이름만 뽑습니다. 접두사를 떼고 이름만 돌려주며, 선택 학년이
 * 없으면 빈 목록입니다 — 학년 미상 폴백은 [supervisionDisplayNames] 가 담당합니다.
 */
private fun HanaScheduleEntry.supervisionTeachersFor(selected: Int): List<String> =
    supervisionTokens()
        .filter { it.grade == selected }
        .map { it.name }
        .distinct()

/**
 * 학년을 못 읽는 행의 fail-safe 표시 텍스트 — `N.` 접두사를 모두 떼고 남은 교사명/일정명.
 * 파싱 토큰이 비어도(공백·접두사 없는 단일 문자열 등) 원문은 그대로 남깁니다.
 */
private fun HanaScheduleEntry.rawSupervisionText(): String? {
    val names = supervisionTokens().map { it.name }.ifEmpty {
        val fallback = (teacher?.takeIf { it.isNotBlank() } ?: name)
            .replace("선생님", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        listOfNotNull(fallback.takeIf { it.isNotEmpty() })
    }
    return names.distinct().joinToString(" ").takeIf { it.isNotEmpty() }
}

/**
 * 한 감독 행에서 선택 학년 기준으로 표시할 교사명.
 *
 * - 학년이 확정된 토큰 중 선택 학년이 있으면 그것만 (정상 동작).
 * - 선택 학년이 없어도 학년 미상 토큰이 있으면 그 원문을 그대로 (fail-safe).
 * - 학년이 확정됐는데 전부 다른 학년이면 null — 그날 전체 폴백에서 되살립니다.
 */
private fun HanaScheduleEntry.supervisionDisplayNames(selected: Int): List<String>? {
    val tokens = supervisionTokens()
    if (tokens.isEmpty()) return null
    val selectedNames = supervisionTeachersFor(selected)
    if (selectedNames.isNotEmpty()) return selectedNames
    return tokens.filter { it.grade == null }.map { it.name }.distinct().takeIf { it.isNotEmpty() }
}

/** 화면에 보여줄 교사명 — 학년 접두사(`2.`)와 `선생님` 꼬리를 뗍니다. */
val HanaScheduleEntry.displayTeacher: String?
    get() = teacher
        ?.replace(GRADE_PREFIX, "")
        ?.replace("선생님", " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** st_time 이 "19:00" / "16:20~17:50"(범위) / "2026-09-17 19:00" 형태로 와도 첫 "HH:mm" 만 봅니다. */
private fun clockHhMm(raw: String?): String? {
    val match = CLOCK_HH_MM.find(raw ?: return null) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    return "%02d:%02d".format(hour, match.groupValues[2].toInt())
}

/**
 * 감독 슬롯(타임) 번호. 포털은 16:20 을 0타임, 19:00 을 1타임으로 내려주고, 일정명에
 * `N타임` 이 있으면 그 번호를 우선합니다. 슬롯을 못 읽으면 null.
 */
fun supervisionSlotOf(entry: HanaScheduleEntry): Int? {
    SLOT_NAME.find(entry.name)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    // 하드코딩 문자열 대신 Timetable.Sessions 의 실제 면학 시작 시각에서 파생시켜서,
    // 그쪽 시간이 바뀌어도 여기가 따로 어긋나지 않게 합니다.
    return when (clockHhMm(entry.startTime)) {
        hhMm(Timetable.Sessions.weekday0.start) -> 0
        hhMm(Timetable.Sessions.weekday1.start) -> 1
        else -> null
    }
}

private fun hhMm(minutesSinceMidnight: Int): String =
    "%02d:%02d".format(minutesSinceMidnight / 60, minutesSinceMidnight % 60)

/** 슬롯 번호(못 읽으면 시작 시각 원문) — 중복 판정에 씁니다. */
private fun HanaScheduleEntry.supervisionSlot(): String =
    supervisionSlotOf(this)?.toString() ?: clockHhMm(startTime) ?: startTime.orEmpty()

/** 같은 (날짜·슬롯·학년·교사) 완전 중복을 가리는 키. */
private fun HanaScheduleEntry.supervisionKey(): String =
    listOf(date.toString(), supervisionSlot(), parsedGrade(grade)?.toString().orEmpty(), teacher.orEmpty()).joinToString("|")

/** 같은 (날짜·슬롯·학년) 안에서 담당 한 명을 확정할 때 쓰는 키. */
private fun HanaScheduleEntry.supervisionSlotKey(): String =
    listOf(date.toString(), supervisionSlot(), parsedGrade(grade)?.toString().orEmpty()).joinToString("|")

/**
 * 감독 행 정리. ① (날짜·슬롯·학년·교사)가 같은 완전 중복을 먼저 제거하고, ② 같은
 * (날짜·슬롯·학년)에 담당이 여럿이면 상세(non-mainYn) 행을 요약 병합 행보다 우선하되,
 * 같은 출처면 뒤에 온 행을 최신으로 봅니다 — 서버가 같은 슬롯의 변경 전/후 행을 함께
 * 내려주거나 mainYn 이 겹쳐 내려줄 때 담당이 둘로 보이던 문제를 막습니다.
 */
private fun dedupeSupervision(entries: List<HanaScheduleEntry>): List<HanaScheduleEntry> {
    val seen = HashSet<String>()
    val exact = entries.filter { entry -> entry.code != SUPERVISION_CODE || seen.add(entry.supervisionKey()) }
    val winnerIndex = HashMap<String, Int>()
    exact.forEachIndexed { index, entry ->
        if (entry.code != SUPERVISION_CODE) return@forEachIndexed
        val key = entry.supervisionSlotKey()
        val currentIndex = winnerIndex[key]
        if (currentIndex == null) {
            winnerIndex[key] = index
        } else {
            val current = exact[currentIndex]
            val wins = when {
                current.mainYn && !entry.mainYn -> true
                !current.mainYn && entry.mainYn -> false
                else -> index > currentIndex
            }
            if (wins) winnerIndex[key] = index
        }
    }
    val winners = winnerIndex.values.toHashSet()
    return exact.filterIndexed { index, entry -> entry.code != SUPERVISION_CODE || index in winners }
}

/** 면학감독 행이 1타임(19:00 시작)인지 — 일정명에 "1타임" 이 있으면 그것도 인정합니다. */
fun isWeekday1Supervision(entry: HanaScheduleEntry): Boolean = supervisionSlotOf(entry) == 1

/**
 * 선택 학년만 남긴 면학감독 행. 선택 학년이 확인되면 그 교사만 쓰고, 학년을 못 읽는 행은
 * 버리지 않고 원문을 그대로 보여줍니다. 그날의 감독 행이 필터 때문에 하나도 남지 않으면
 * 그날 감독 행 전체를 원문으로 되돌립니다 — 학년 표기가 낯설다는 이유로 감독이 통째로
 * 사라지던 회귀를 막기 위함입니다.
 */
fun filterSupervisionByGrade(entries: List<HanaScheduleEntry>, selected: Int): List<HanaScheduleEntry> {
    val result = mutableListOf<HanaScheduleEntry>()
    entries.groupBy { it.date }.forEach { (_, dayEntries) ->
        if (dayEntries.none { it.code == SUPERVISION_CODE }) {
            result += dayEntries
            return@forEach
        }
        val filtered = dayEntries.mapNotNull { entry ->
            if (entry.code != SUPERVISION_CODE) return@mapNotNull entry
            val names = entry.supervisionDisplayNames(selected) ?: return@mapNotNull null
            entry.copy(teacher = names.joinToString(" "), grade = selected.toString())
        }
        if (filtered.any { it.code == SUPERVISION_CODE }) {
            result += filtered
        } else {
            // 그날 감독이 전부 걸러짐 — 감독이 아예 안 보이는 것보다 원문이라도 보여줍니다.
            result += dayEntries.map { entry ->
                if (entry.code != SUPERVISION_CODE) entry
                else entry.copy(teacher = entry.rawSupervisionText(), grade = null)
            }
        }
    }
    return result
}

/**
 * [entries] 에서 [date] 의 1타임 면학감독 담당 교사를 뽑습니다. 앱·위젯이 같은 규칙을
 * 쓰도록 여기에 둡니다. 선택 학년 교사를 우선하되, 학년을 못 읽거나 그날 선택 학년 행이
 * 없으면 원문을 돌려주어 감독 줄이 비지 않게 합니다.
 */
fun weekday1SupervisionTeachers(
    entries: List<HanaScheduleEntry>,
    date: LocalDate,
    studentGrade: Int,
): List<String> {
    val slots = entries.filter {
        it.code == SUPERVISION_CODE && it.date == date && isWeekday1Supervision(it)
    }
    if (slots.isEmpty()) return emptyList()
    val preferred = slots.flatMap { it.supervisionDisplayNames(studentGrade).orEmpty() }.distinct()
    if (preferred.isNotEmpty()) return preferred
    return slots.mapNotNull { it.rawSupervisionText() }.distinct()
}
