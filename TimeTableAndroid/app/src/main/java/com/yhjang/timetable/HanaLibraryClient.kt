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

object HanaLibraryApi {

    private const val TAG = "HanaLibrary"
    private const val BASE = "https://hh.hana.hs.kr"
    private const val REFERER = "$BASE/main/library/library-apply.do"

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
    suspend fun slots(context: Context): List<LibrarySlot> = withContext(Dispatchers.IO) {
        val body = runCatching { HanaPortalClient.get().authenticatedText(context, "/main/library/library-apply.do") }
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

    suspend fun seatMap(context: Context, slotId: String): LibrarySeatMap = withContext(Dispatchers.IO) {
        val json = HanaPortalClient.get().authenticatedJson(
            context,
            "/main/library/study-room-req-list.json",
            listOf("stIdxFull" to slotId, "subListYn" to "N"),
            referer = REFERER,
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
        // 격자 크기(sro_x × sro_y) 한 장에 구역 하나. 목록은 구역 순서대로 격자 칸 수만큼 이어져 오므로
        // (10×16 격자에 320칸 = 2구역) 그 크기로 잘라 구역을 나누고, 이름은 그 구역의 좌석에 적힌 clr_area_nm 으로.
        val gridX = open?.optInt("sro_x", 0)?.takeIf { it > 0 } ?: ((seats.maxOfOrNull { it.x } ?: 0) + 1)
        val gridY = open?.optInt("sro_y", 0)?.takeIf { it > 0 } ?: ((seats.maxOfOrNull { it.y } ?: 0) + 1)
        val perArea = (gridX * gridY).coerceAtLeast(1)
        val areas = seats.chunked(perArea).mapIndexed { index, chunk ->
            val label = chunk.firstNotNullOfOrNull { it.area } ?: "구역 ${index + 1}"
            LibraryArea(label, gridX, gridY, chunk)
        }
        if (seats.isNotEmpty()) {
            Log.d(TAG, "seatMap $slotId grid=${gridX}x$gridY items=${seats.size} areas=${areas.map { it.label + ":" + it.seats.count { s -> s.isSeat } }}")
        }
        LibrarySeatMap(areas, null)
    }

    /** 이 기기를 "마지막 등록 기기"로 만듭니다 — 신청 전에 한 번. */
    private suspend fun registerDevice(context: Context) {
        val json = HanaPortalClient.get().authenticatedJson(
            context, "/main/member/updateDiviceInfo.json", listOf("usegubun" to "L"), referer = REFERER,
        )
        if (json.optString("result") != "success") Log.d(TAG, "registerDevice: $json")
    }

    /** 좌석 신청 — 성공하면 서버 문구(없으면 "신청되었습니다"), 실패하면 예외 메시지에 서버 문구. */
    suspend fun reserve(context: Context, seat: LibrarySeat, slotId: String): String = withContext(Dispatchers.IO) {
        registerDevice(context)
        val json = HanaPortalClient.get().authenticatedJson(
            context,
            "/main/library/study-room-req.json",
            listOf("clrIdx" to seat.clrIdx.toString(), "srtIdx" to seat.srtIdx.toString(), "stIdxFull" to slotId),
            referer = REFERER,
        )
        val message = json.optString("resMsg").ifBlank { null }
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(message ?: "신청하지 못했습니다")
        }
        message ?: "신청되었습니다"
    }

    suspend fun cancel(context: Context, sreIdx: Int): String = withContext(Dispatchers.IO) {
        val json = HanaPortalClient.get().authenticatedJson(
            context, "/main/studyroom/study-cancel.json", listOf("sreIdx" to sreIdx.toString()), referer = REFERER,
        )
        val message = json.optString("resMsg").ifBlank { null }
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(message ?: "취소하지 못했습니다")
        }
        message ?: "취소되었습니다"
    }
}
