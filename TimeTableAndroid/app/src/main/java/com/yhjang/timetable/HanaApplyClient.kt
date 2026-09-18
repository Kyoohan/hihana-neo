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
enum class ApplyService(val label: String, val applyPath: String, val historyPath: String) {
    CLASSROOM("교과교실", "/main/classroom/apply.do", "/main/classroom/apply.do"),
    STUDY_ROOM("면학실", "/main/studyroom/study-apply.do", "/main/studyroom/study-history.do"),
    LIBRARY("도서관", "/main/library/library-apply.do", "/main/library/library-history.do"),
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

    suspend fun fetchClassroomHistory(context: Context): ClassroomHistory = withContext(Dispatchers.IO) {
        val today = PlanStore.today().toString()
        val response = HanaPortalClient.get().authenticatedRaw(
            context,
            "/main/classroom/apply-list.json",
            params = listOf("searchSDate" to today, "searchEDate" to today),
            method = "GET",
            referer = "$BASE/",
        )

        val json = runCatching { JSONObject(response.body) }.getOrNull()
        if (json == null) {
            Log.d(TAG, "[교과교실] JSON 아님 HTTP ${response.code}: ${response.body.take(300)}")
            return@withContext ClassroomHistory.Unsupported
        }

        val array = arrayAt(json, "paging.result")
            ?: json.optJSONArray("list")
            ?: json.optJSONArray("itemList")
        if (array == null) {
            Log.d(TAG, "[교과교실] result 배열 없음 HTTP ${response.code}: ${response.body.take(300)}")
            return@withContext ClassroomHistory.Unsupported
        }
        ClassroomHistory.Rows(parseRows(array))
    }

    private fun parseRows(array: JSONArray): List<HanaApplyRow> =
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { row ->
                HanaApplyRow(
                    slot = firstString(row, "st_nm", "stNm", "slot_nm", "time_nm", "st_time"),
                    place = firstString(
                        row, "room_nm", "slp_nm", "cc_place_nm", "place_nm", "lib_nm",
                        "c_place_cd_name", "seat", "srt_cont",
                    ),
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
