package com.yhjang.timetable

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 도서관 좌석 신청 API — hhlibres(파이썬 예약 스크립트)에서 확인한 포털 엔드포인트를 그대로 씁니다.
 *
 * - 좌석 목록: POST /main/library/study-room-req-list.json {stIdxFull, subListYn=N}
 *   → result, hYn/hMsg(휴관), studyRoomOpenVo{sro_x, sro_y}(격자 크기), list[좌석]
 *   좌석: srt_cont(번호 "2-19"), srt_x/srt_y(격자 좌표), srt_type("1"만 실제 좌석, 2·3은 표지·블록), srt_use_yn,
 *   clr_idx/srt_idx(신청 키), sre_idx(예약 번호, 있으면 누군가 잡음), myYn(내 자리), holidayYn
 * - 신청: POST /main/library/study-room-req.json {clrIdx, srtIdx, stIdxFull} → result, resMsg
 * - 취소: POST /main/studyroom/study-cancel.json {sreIdx}
 * - 기기 등록: POST /main/member/updateDiviceInfo.json {usegubun=L} — 신청 전 필수 ("마지막으로 등록한 기기"만 신청 가능)
 * - 모든 .json 은 Accept 헤더가 없으면 404 (HanaPortalClient 가 붙입니다).
 */
data class LibrarySlot(val id: String, val label: String)

data class LibrarySeat(
    val cont: String,
    val x: Int,
    val y: Int,
    /** 구역 이름 ("2F" 등) — 빈 칸·블록은 null. */
    val area: String?,
    val type: String,
    val color: String?,
    val usable: Boolean,
    val clrIdx: Int,
    val srtIdx: Int,
    val sreIdx: Int?,
    val mine: Boolean,
    val holiday: Boolean,
) {
    val isSeat: Boolean get() = type == "1"
    val available: Boolean get() = isSeat && usable && sreIdx == null && !holiday
}

/** 구역 하나의 배치도 — 격자 한 장(gridX × gridY)에 놓인 칸들. */
data class LibraryArea(val label: String, val gridX: Int, val gridY: Int, val seats: List<LibrarySeat>)

data class LibrarySeatMap(
    val areas: List<LibraryArea>,
    /** 휴관이면 안내 문구, 아니면 null. */
    val closedMessage: String?,
) {
    val seats: List<LibrarySeat> get() = areas.flatMap { it.seats }
}

/** 좌석 신청 화면이 다루는 서비스 — 도서관(확인됨)과 면학실(같은 구조로 추정, 엔드포인트 후보를 차례로 시도). */
enum class SeatService(
    val label: String,
    val applyPage: String,
    val listPaths: List<String>,
    val reservePaths: List<String>,
) {
    LIBRARY(
        "도서관",
        "/main/library/library-apply.do",
        listOf("/main/library/study-room-req-list.json"),
        listOf("/main/library/study-room-req.json"),
    ),
    STUDY_ROOM(
        "면학실",
        "/main/studyroom/study-apply.do",
        listOf("/main/studyroom/study-room-req-list.json", "/main/studyroom/study-req-list.json", "/main/studyroom/studyroom-req-list.json"),
        listOf("/main/studyroom/study-room-req.json", "/main/studyroom/study-req.json", "/main/studyroom/studyroom-req.json"),
    ),
}

object HanaLibraryApi {

    private const val TAG = "HanaLibrary"
    private const val BASE = "https://hh.hana.hs.kr"

    /** 신청 페이지 응답이 안 읽힐 때의 기본값 — 포털 timeList 에서 확인된 값 (4 = 휴일A 그룹, 1 = 평일 그룹). */
    private val fallbackSlots = listOf(
        LibrarySlot("1_9", "평일1타임"),
        LibrarySlot("1_10", "평일2타임"),
        LibrarySlot("4_12", "휴일1타임"),
        LibrarySlot("4_13", "휴일2타임"),
    )

    /**
     * 타임 목록 — 신청 페이지(library-apply.do)는 Accept: json 으로 부르면 HTML 대신
     * `{"deviceChk":…, "timeList":[{stg_idx, stg_nm, st_idx, st_nm, st_time, …}]}` 를 줍니다.
     * 좌석 API 의 stIdxFull 은 `stg_idx_st_idx` ("4_12") 입니다.
     */
    suspend fun slots(context: Context, service: SeatService = SeatService.LIBRARY): List<LibrarySlot> = withContext(Dispatchers.IO) {
        val body = runCatching { HanaPortalClient.get().authenticatedText(context, service.applyPage) }
            .getOrNull() ?: return@withContext fallbackSlots
        val json = runCatching { JSONObject(body) }.getOrNull()
        val list = json?.optJSONArray("timeList")
        if (list == null || list.length() == 0) {
            Log.d(TAG, "timeList not found; sample=${body.take(300).replace('\n', ' ')}")
            return@withContext fallbackSlots
        }
        (0 until list.length()).mapNotNull { i ->
            val row = list.optJSONObject(i) ?: return@mapNotNull null
            val group = row.optInt("stg_idx", -1)
            val idx = row.optInt("st_idx", -1)
            if (group < 0 || idx < 0) return@mapNotNull null
            val name = row.optString("st_nm").ifBlank { "타임 $idx" }
            val time = row.optString("st_time").takeIf { it.isNotBlank() && it != "null" }
            LibrarySlot("${group}_$idx", if (time != null) "$name $time" else name)
        }.distinctBy { it.id }.ifEmpty { fallbackSlots }
    }

    /** 후보 경로를 차례로 POST 해 처음으로 JSON 을 주는 응답을 돌려줍니다 (404 HTML 은 건너뜀). */
    private suspend fun firstJson(context: Context, paths: List<String>, params: List<Pair<String, String>>, referer: String): JSONObject {
        var last: Exception? = null
        for (path in paths) {
            try {
                val raw = HanaPortalClient.get().authenticatedRaw(context, path, params, method = "POST", referer = referer)
                val json = runCatching { JSONObject(raw.body) }.getOrNull()
                if (json != null) return json
                Log.d(TAG, "$path HTTP ${raw.code} JSON 아님: ${raw.body.take(120).replace('\n', ' ')}")
            } catch (e: Exception) {
                last = e
                Log.d(TAG, "$path 실패: ${e.message}")
            }
        }
        throw last ?: HanaPortalException.UnexpectedResponse("[${paths.first()}] 사용할 수 있는 엔드포인트가 없습니다")
    }

    suspend fun seatMap(context: Context, slotId: String, service: SeatService = SeatService.LIBRARY): LibrarySeatMap = withContext(Dispatchers.IO) {
        val json = firstJson(
            context, service.listPaths, listOf("stIdxFull" to slotId, "subListYn" to "N"), referer = BASE + service.applyPage,
        )
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(json.optString("resMsg").ifBlank { "좌석을 불러오지 못했습니다" })
        }
        if (json.optString("hYn") == "Y") {
            return@withContext LibrarySeatMap(emptyList(), json.optString("hMsg").ifBlank { "휴관일" })
        }
        val open = json.optJSONObject("studyRoomOpenVo")
        val list = json.optJSONArray("list")
        val seats = (0 until (list?.length() ?: 0)).mapNotNull { i ->
            val row = list?.optJSONObject(i) ?: return@mapNotNull null
            LibrarySeat(
                cont = row.optString("srt_cont"),
                // 포털 좌표는 1부터 시작합니다.
                x = row.optInt("srt_x", 0) - 1,
                y = row.optInt("srt_y", 0) - 1,
                area = row.optString("clr_area_nm").takeIf { it.isNotBlank() && it != "null" },
                type = row.optString("srt_type"),
                color = row.optString("srt_color").takeIf { it.isNotBlank() && it != "null" },
                usable = row.optString("srt_use_yn") == "Y",
                clrIdx = row.optInt("clr_idx"),
                srtIdx = row.optInt("srt_idx"),
                sreIdx = row.optInt("sre_idx", 0).takeIf { it > 0 },
                mine = row.optString("myYn") == "Y",
                holiday = row.optString("holidayYn") == "Y",
            )
        }
        // 배치도는 세로로 긴 한 장입니다 (10 × 66 정도, 사이에 없는 행도 있음). 층은 통로 칸의 바닥색으로 갈립니다
        // (2F #efefef, 1F #d9d9d9). 행마다 바닥색을 구해 같은 색끼리 한 구역으로 묶고, 없는 행이 이어지는 큰 틈은
        // 한 줄 빈 행으로 줄입니다. 구역 이름은 표지 글자("2F입구")에서 층을 읽습니다.
        val gridX = open?.optInt("sro_x", 0)?.takeIf { it > 0 } ?: ((seats.maxOfOrNull { it.x } ?: 0) + 1)
        val gridY = open?.optInt("sro_y", 0)?.takeIf { it > 0 } ?: ((seats.maxOfOrNull { it.y } ?: 0) + 1)
        val rows = seats.filter { it.x >= 0 && it.y >= 0 }.groupBy { it.y }.toSortedMap()
        fun floorColor(row: List<LibrarySeat>): String =
            row.firstOrNull { it.type == "3" && it.color != null }?.color ?: row.firstNotNullOfOrNull { it.color } ?: ""
        val groups = mutableListOf<Pair<String, MutableList<Pair<Int, List<LibrarySeat>>>>>()
        rows.forEach { (y, row) ->
            val color = floorColor(row)
            val last = groups.lastOrNull()
            if (last != null && last.first == color) last.second += y to row else groups += color to mutableListOf(y to row)
        }
        val areas = groups.mapIndexed { index, (_, rowList) ->
            // y 를 다시 매김: 연속이면 +1, 사이가 비면 빈 행 하나만.
            var next = 0
            var prevY: Int? = null
            val remapped = rowList.flatMap { (y, row) ->
                if (prevY != null) next += if (y - prevY!! > 1) 2 else 1
                prevY = y
                val yy = next
                row.map { it.copy(y = yy) }
            }
            val floorText = remapped.firstOrNull { it.type == "2" && Regex("\\d+F").containsMatchIn(it.cont) }
                ?.let { Regex("(\\d+)F").find(it.cont)?.groupValues?.get(1) + "층" }
            // 면학실 구역은 학교에서 부르는 이름대로 E·F·G·H.
            val fallback = if (service == SeatService.STUDY_ROOM && index < 4) "구역 ${'E' + index}" else "구역 ${index + 1}"
            LibraryArea(floorText ?: fallback, gridX, next + 1, remapped)
        }
        if (seats.isNotEmpty()) {
            Log.d(TAG, "seatMap $slotId grid=${gridX}x$gridY items=${seats.size} areas=${areas.map { it.label + ":" + it.seats.count { s -> s.isSeat } }}")
        }
        LibrarySeatMap(areas, null)
    }

    /** 이 기기를 "마지막 등록 기기"로 만듭니다 — 신청 전에 한 번. */
    private suspend fun registerDevice(context: Context, service: SeatService) {
        val json = HanaPortalClient.get().authenticatedJson(
            context, "/main/member/updateDiviceInfo.json", listOf("usegubun" to "L"), referer = BASE + service.applyPage,
        )
        if (json.optString("result") != "success") Log.d(TAG, "registerDevice: $json")
    }

    /** 좌석 신청 — 성공하면 서버 문구(없으면 "신청되었습니다"), 실패하면 예외 메시지에 서버 문구. */
    suspend fun reserve(context: Context, seat: LibrarySeat, slotId: String, service: SeatService = SeatService.LIBRARY): String = withContext(Dispatchers.IO) {
        registerDevice(context, service)
        val json = firstJson(
            context, service.reservePaths,
            listOf("clrIdx" to seat.clrIdx.toString(), "srtIdx" to seat.srtIdx.toString(), "stIdxFull" to slotId),
            referer = BASE + service.applyPage,
        )
        val message = json.optString("resMsg").ifBlank { null }
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(message ?: "신청하지 못했습니다")
        }
        message ?: "신청되었습니다"
    }

    suspend fun cancel(context: Context, sreIdx: Int, service: SeatService = SeatService.LIBRARY): String = withContext(Dispatchers.IO) {
        val json = HanaPortalClient.get().authenticatedJson(
            context, "/main/studyroom/study-cancel.json", listOf("sreIdx" to sreIdx.toString()), referer = BASE + service.applyPage,
        )
        val message = json.optString("resMsg").ifBlank { null }
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(message ?: "취소하지 못했습니다")
        }
        message ?: "취소되었습니다"
    }
}
