package com.yhjang.timetable

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// MARK: - 대상

/**
 * 신청·내역 대상 서비스.
 * - [applyPath] 는 실제 신청 페이지로, "신청하기"가 웹뷰로 엽니다.
 * - [historyPath] 는 포털 내역 페이지로, 교과교실 JSON 조회가 안 되거나 JSON 스키마가
 *   확인되지 않은 면학실·도서관에서 웹뷰로 보여줍니다.
 */
/** 순서가 곧 신청·내역 카드 순서입니다 — 면학실 → 도서관 → 교과교실 → 외출·외박. */
enum class ApplyService(val label: String, val applyPath: String, val historyPath: String) {
    STUDY_ROOM("면학실", "/main/studyroom/study-apply.do", "/main/studyroom/study-history.do"),
    LIBRARY("도서관", "/main/library/library-apply.do", "/main/library/library-history.do"),
    CLASSROOM("교과교실", "/main/classroom/apply.do", "/main/classroom/history.do"),
    OUTING("외출·외박", "/main/outing/apply_inscr.do", "/main/outing/history.do"),
}

// MARK: - 모델

/** 신청 내역 한 줄 — 서비스마다 필드명이 달라 아는 이름들을 우선순위로 훑어 채웁니다. */
data class HanaApplyRow(
    val slot: String?,
    val place: String?,
    val status: String?,
    val appliedDate: String?,
)

/** 교과교실 내역 조회 결과 — JSON 이 아니면 [Unsupported] 로 알려 웹뷰 폴백을 태웁니다. */
sealed interface ClassroomHistory {
    data class Rows(val rows: List<HanaApplyRow>) : ClassroomHistory
    data object Unsupported : ClassroomHistory
}

// MARK: - API

/**
 * 교과교실 신청 내역 조회.
 * 포털 메인 페이지 JS 가 쓰는 GET /main/classroom/apply-list.json 을 그대로 재현합니다 —
 * searchSDate/searchEDate 외 파라미터는 붙이지 않습니다. 응답이 JSON 이 아니거나
 * 배열 경로가 없으면 원문 일부를 로그로 남기고 [ClassroomHistory.Unsupported] 를 돌려줍니다.
 */
object HanaApplyApi {

    private const val BASE = "https://hh.hana.hs.kr"

    /** 진단용 — JSON 이 아닐 때 사용자가 원문을 신고할 수 있게 앞부분만 남깁니다. */
    private const val TAG = "HanaApply"

    /** 서비스별 내역 JSON 엔드포인트 후보 — 확인된 것은 하나, 아직 모르는 서비스는 있을 법한 이름을 차례로 시도합니다. */
    private fun historyEndpoints(service: ApplyService): List<String> = when (service) {
        ApplyService.CLASSROOM -> listOf("/main/classroom/apply-list.json")
        ApplyService.STUDY_ROOM -> listOf("/main/studyroom/study-only-req-list.json")
        ApplyService.LIBRARY -> listOf(
            "/main/library/library-req-list.json", "/main/library/library-apply-list.json",
            "/main/library/apply-list.json", "/main/library/library-list.json",
        )
        ApplyService.OUTING -> listOf(
            "/main/outing/apply-list.json", "/main/outing/history-list.json",
            "/main/outing/outing-list.json", "/main/outing/req-list.json",
        )
    }

    /**
     * 신청 내역 조회 — 포털 목록 JSON 은 전부 POST 폼(cp/searchSDate/searchEDate/listType)입니다 (GET 은 405).
     * 최근 7일~앞으로 14일. 어느 후보도 JSON 을 주지 않으면 원문 일부를 로그로 남기고 [ClassroomHistory.Unsupported].
     */
    suspend fun fetchHistory(context: Context, service: ApplyService): ClassroomHistory = withContext(Dispatchers.IO) {
        val today = PlanStore.today()
        val params = listOf(
            "cp" to "1",
            "searchSDate" to today.minusDays(7).toString(),
            "searchEDate" to today.plusDays(14).toString(),
            "listType" to "list",
        )
        for (path in historyEndpoints(service)) {
            val response = HanaPortalClient.get().authenticatedRaw(context, path, params, method = "POST", referer = "$BASE/")
            val json = runCatching { JSONObject(response.body) }.getOrNull()
            if (json == null) {
                Log.d(TAG, "[${service.label}] $path JSON 아님 HTTP ${response.code}: ${response.body.take(200).replace('\n', ' ')}")
                continue
            }
            val array = arrayAt(json, "paging.result")
                ?: json.optJSONArray("list")
                ?: json.optJSONArray("itemList")
                ?: json.optJSONArray("result")
            if (array == null) {
                Log.d(TAG, "[${service.label}] $path result 배열 없음: ${response.body.take(300)}")
                continue
            }
            if (array.length() > 0) Log.d(TAG, "[${service.label}] $path 첫 행: ${array.optJSONObject(0)?.toString()?.take(600)}")
            return@withContext ClassroomHistory.Rows(parseRows(array))
        }
        ClassroomHistory.Unsupported
    }

    suspend fun fetchClassroomHistory(context: Context): ClassroomHistory = fetchHistory(context, ApplyService.CLASSROOM)

    /**
     * 진단 — 서비스별 내역·신청 페이지(.do)를 세션으로 받아, 페이지 JS 가 부르는 엔드포인트(.json/.do)와 폼 필드 이름을
     * 로그로 남깁니다 (`adb logcat -s HanaDiscover`). 아직 API 를 모르는 도서관·외출외박 내역과 면학실·도서관 신청을
     * 앱 안에서 그리기 위한 사전 조사용이며, 프로세스당 한 번만 돕니다.
     */
    /** 페이지 JS 조사 — HTML 로 받아 `updateDiviceInfo`·`usegubun`·`.json` 호출 주변을 로그로 남깁니다 (Dev 탭에서 호출). */
    suspend fun dumpPageScript(context: Context, path: String) = withContext(Dispatchers.IO) {
        val html = runCatching { HanaPortalClient.get().authenticatedHtml(context, path) }.getOrElse {
            Log.d("HanaDiscover", "html $path 실패: ${it.message}"); return@withContext
        }
        Log.d("HanaDiscover", "html $path ${html.length} chars")
        val keys = listOf("updateDiviceInfo", "usegubun", "deviceChk", "study-room-req", "stIdxFull")
        keys.forEach { key ->
            var from = 0
            var n = 0
            while (n < 6) {
                val i = html.indexOf(key, from)
                if (i < 0) break
                Log.d("HanaDiscover", "[$key] …" + html.substring((i - 220).coerceAtLeast(0), (i + 260).coerceAtMost(html.length)).replace(Regex("\\s+"), " ") + "…")
                from = i + key.length
                n++
            }
        }
    }

    @Volatile private var discovered = false
    suspend fun discoverEndpoints(context: Context) = withContext(Dispatchers.IO) {
        if (discovered) return@withContext
        discovered = true
        val client = HanaPortalClient.get()
        val urlRegex = Regex("""["'](/main/[A-Za-z0-9_\-/.]+?\.(?:json|do))["']""")
        val nameRegex = Regex("""name=["']([A-Za-z0-9_\-]+)["']""")
        val dataRegex = Regex("""data\s*:\s*\{([^}]{0,400})\}""")
        for (service in ApplyService.entries) {
            for (path in listOf(service.historyPath, service.applyPath)) {
                val html = runCatching { client.authenticatedText(context, path) }.getOrNull() ?: continue
                // Accept: json 으로 부르면 .do 도 HTML 대신 페이지 데이터 JSON 을 주는 경우가 많습니다 — 통째로 남깁니다.
                if (html.trimStart().startsWith("{") || html.trimStart().startsWith("[")) {
                    Log.d("HanaDiscover", "== ${service.label} $path JSON: ${html.take(2500)}")
                    continue
                }
                val urls = urlRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()
                val names = nameRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()
                val datas = dataRegex.findAll(html).map { it.groupValues[1].replace(Regex("\\s+"), " ").take(300) }.distinct().toList()
                Log.d("HanaDiscover", "== ${service.label} $path (${html.length} chars)")
                Log.d("HanaDiscover", "urls: $urls")
                Log.d("HanaDiscover", "names: $names")
                datas.forEach { Log.d("HanaDiscover", "data: $it") }
            }
        }
    }

    private fun parseRows(array: JSONArray): List<HanaApplyRow> =
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { row ->
                // 면학실은 장소(c_place_cd_name) + 층(clr_area_nm) + 자리(srt_cont)를 한 칸에 이어 붙입니다.
                val place = firstString(row, "room_nm", "slp_nm", "cc_place_nm", "place_nm", "lib_nm", "c_place_cd_name")
                val seat = listOfNotNull(firstString(row, "clr_area_nm"), firstString(row, "srt_cont", "seat"))
                    .joinToString(" ").takeIf { it.isNotBlank() }
                HanaApplyRow(
                    slot = firstString(row, "st_nm", "stNm", "slot_nm", "time_nm", "st_time"),
                    place = listOfNotNull(place, seat).joinToString(" ").takeIf { it.isNotBlank() },
                    // cc_cd 같은 원문 상태 코드는 사람이 읽을 수 없어 후보에서 뺐습니다 —
                    // 읽을 수 있는 이름 필드가 하나도 없으면 이 줄은 그냥 표시에서 빠집니다
                    // (ApplySection 의 listOfNotNull 이 null 을 알아서 걸러냅니다).
                    status = firstString(row, "cc_cd_name", "status_nm", "stat_nm", "appr_nm", "req_state", "state_nm"),
                    appliedDate = firstString(row, "crt_dt", "reg_dt", "inputdate", "req_dt", "apply_dt"),
                )
            }
        }

    /** `paging.result` 처럼 점으로 중첩 경로를 내려가 배열을 찾습니다. */
    private fun arrayAt(json: JSONObject, path: String): JSONArray? {
        val parts = path.split('.')
        var current: JSONObject? = json
        for (index in 0 until parts.size - 1) current = current?.optJSONObject(parts[index])
        return current?.optJSONArray(parts.last())
    }

    private fun firstString(row: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            if (!row.has(key) || row.isNull(key)) return@firstNotNullOfOrNull null
            row.opt(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        }
}
