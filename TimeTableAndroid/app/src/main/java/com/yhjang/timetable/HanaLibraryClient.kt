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

data class LibrarySeatMap(
    val gridX: Int,
    val gridY: Int,
    val seats: List<LibrarySeat>,
    /** 휴관이면 안내 문구, 아니면 null. */
    val closedMessage: String?,
)

object HanaLibraryApi {

    private const val TAG = "HanaLibrary"
    private const val BASE = "https://hh.hana.hs.kr"
    private const val REFERER = "$BASE/main/library/library-apply.do"

    /** 신청 페이지의 타임 선택 옵션이 안 읽힐 때의 기본값 — hhlibres 설정에서 확인된 값. */
    private val fallbackSlots = listOf(
        LibrarySlot("1_9", "평일 1타임"),
        LibrarySlot("1_10", "평일 2타임"),
        LibrarySlot("4_12", "주말 3타임"),
        LibrarySlot("4_13", "주말 4타임"),
    )

    /** 신청 페이지(.do)의 `<option value="1_9">…</option>` 을 읽어 타임 목록을 만듭니다. 못 읽으면 기본값. */
    suspend fun slots(context: Context): List<LibrarySlot> = withContext(Dispatchers.IO) {
        val html = runCatching { HanaPortalClient.get().authenticatedText(context, "/main/library/library-apply.do") }
            .getOrNull() ?: return@withContext fallbackSlots
        val regex = Regex("""<option[^>]*value=["'](\d+_\d+)["'][^>]*>([^<]*)</option>""")
        val found = regex.findAll(html)
            .map { LibrarySlot(it.groupValues[1], it.groupValues[2].trim().replace(Regex("\\s+"), " ")) }
            .distinctBy { it.id }
            .toList()
        if (found.isEmpty()) {
            Log.d(TAG, "slot options not found; html=${html.length} chars, sample=${html.take(200).replace('\n', ' ')}")
            fallbackSlots
        } else {
            found
        }
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
            return@withContext LibrarySeatMap(0, 0, emptyList(), json.optString("hMsg").ifBlank { "휴관일" })
        }
        val open = json.optJSONObject("studyRoomOpenVo")
        val list = json.optJSONArray("list")
        val seats = (0 until (list?.length() ?: 0)).mapNotNull { i ->
            val row = list?.optJSONObject(i) ?: return@mapNotNull null
            LibrarySeat(
                cont = row.optString("srt_cont"),
                x = row.optInt("srt_x", -1),
                y = row.optInt("srt_y", -1),
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
        // 격자 크기가 없으면 좌표 최댓값으로.
        val gridX = open?.optInt("sro_x", 0)?.takeIf { it > 0 } ?: ((seats.maxOfOrNull { it.x } ?: 0) + 1)
        val gridY = open?.optInt("sro_y", 0)?.takeIf { it > 0 } ?: ((seats.maxOfOrNull { it.y } ?: 0) + 1)
        if (seats.isNotEmpty()) Log.d(TAG, "seatMap $slotId grid=${gridX}x$gridY seats=${seats.size} first=${list?.optJSONObject(0)?.toString()?.take(300)}")
        LibrarySeatMap(gridX, gridY, seats, null)
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
