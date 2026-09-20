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
    /** 신청한 학생 이름·학번 (sre_mem_name / sre_std_num) — 포털 응답에 그대로 들어 있습니다. 빈 자리면 null. */
    val memberName: String? = null,
    val studentNumber: String? = null,
    /** 성별 (mem_sex M/F → 남/여) — 포털 페이지는 남의 자리에 이름 대신 이것만 보여줍니다. */
    val gender: String? = null,
    /** 지정석(sre_assign_yn=Y) — 취소할 수 없습니다. */
    val assigned: Boolean = false,
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
    /** 기기 등록(updateDiviceInfo.json)의 usegubun — 포털 페이지 JS 에서 확인: 도서관 L, 면학실 C. */
    val deviceGubun: String,
) {
    LIBRARY(
        "도서관",
        "/main/library/library-apply.do",
        listOf("/main/library/study-room-req-list.json"),
        listOf("/main/library/study-room-req.json"),
        "L",
    ),
    STUDY_ROOM(
        "면학실",
        "/main/studyroom/study-apply.do",
        listOf("/main/studyroom/study-room-req-list.json", "/main/studyroom/study-req-list.json", "/main/studyroom/studyroom-req-list.json"),
        listOf("/main/studyroom/study-room-req.json", "/main/studyroom/study-req.json", "/main/studyroom/studyroom-req.json"),
        "C",
    ),
}

/**
 * 학번 → 이름 캐시. 도서관 좌석 응답은 이름(sre_mem_name)을 비우고 학번(sre_std_num)·성별만 주지만, 면학실 응답에는
 * 학번과 이름이 함께 오므로 면학실 배치도를 볼 때마다 짝을 모아 두었다가 도서관 자리의 학번을 이름으로 바꿉니다.
 * 한 번이라도 면학실을 신청한 학생은 이름이 나오고, 아니면 학번이 나옵니다.
 */
object StudentNameCache {
    private const val PREFS = "student_names"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun name(context: Context, studentNumber: String?): String? =
        studentNumber?.let { prefs(context).getString(it, null) }

    fun count(context: Context): Int = prefs(context).all.size

    fun clear(context: Context) = prefs(context).edit().clear().apply()

    /**
     * (학번, 이름) 파일을 읽어 저장합니다 — 개발자 모드의 "학번·이름 파일 불러오기". 세 가지 형식을 받습니다:
     * Google 디렉터리 페이지를 통째로 복사한 텍스트(`has_<학번>@hana.hs.kr` 줄 위의 이름), `이름, 학번` 또는 `학번, 이름` 줄
     * (쉼표·탭·공백 구분), `{"학번":"이름"}` JSON. 저장한 쌍 수를 돌려줍니다.
     */
    fun importText(context: Context, text: String): Int {
        val found = LinkedHashMap<String, String>()
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) {
            runCatching { JSONObject(trimmed) }.getOrNull()?.let { json ->
                json.keys().forEach { key -> json.optString(key).takeIf { it.isNotBlank() }?.let { found[key.trim()] = it.trim() } }
            }
        }
        val lines = text.lines().map { it.trim() }
        val mail = Regex("""has_(\d{5})@hana\.hs\.kr""", RegexOption.IGNORE_CASE)
        val pair = Regex("""^(\d{5})[,\t ]+(\S{2,7})$""")
        // "이름, 학번" 순서 (예: `강건우, 25001`, 동명이인은 `김규리A, 24009`)
        val pairNameFirst = Regex("""^(\S{2,7})[,\t ]+(\d{5})$""")
        lines.forEachIndexed { i, line ->
            mail.find(line)?.let { m ->
                var j = i - 1
                while (j >= 0 && (lines[j].isEmpty() || lines[j] == "drag_indicator" || lines[j] == "연락처 저장")) j--
                val name = lines.getOrNull(j) ?: return@let
                if (name.length in 2..6 && !name.any { it.isLetter() && it.code < 128 } && !name.contains('@')) found[m.groupValues[1]] = name
            }
            pair.find(line)?.let { m -> found[m.groupValues[1]] = m.groupValues[2] }
            pairNameFirst.find(line)?.let { m -> found[m.groupValues[2]] = m.groupValues[1] }
        }
        if (found.isNotEmpty()) {
            val editor = prefs(context).edit()
            found.forEach { (number, name) -> editor.putString(number, name) }
            editor.apply()
        }
        return found.size
    }

    fun learn(context: Context, seats: List<LibrarySeat>) {
        val editor = prefs(context).edit()
        var changed = false
        seats.forEach { seat ->
            val number = seat.studentNumber ?: return@forEach
            val name = seat.memberName ?: return@forEach
            if (prefs(context).getString(number, null) != name) {
                editor.putString(number, name)
                changed = true
            }
        }
        if (changed) editor.apply()
    }
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
            // 예약 오픈 직후 과부하 때 포털이 빈 본문(HTTP 200)을 자주 돌려줍니다 — 같은 경로를 짧게 몇 번 더 두드립니다.
            var attempt = 0
            var transientHere = false
            while (attempt < 5) {
                try {
                    val raw = HanaPortalClient.get().authenticatedRaw(context, path, params, method = "POST", referer = referer)
                    val json = runCatching { JSONObject(raw.body) }.getOrNull()
                    if (json != null) return json
                    if (raw.body.isBlank()) {
                        Log.d(TAG, "$path HTTP ${raw.code} 빈 응답 (${attempt + 1}/5)")
                        last = HanaPortalException.Transient("[$path] HTTP ${raw.code} 빈 응답")
                        transientHere = true
                        attempt++
                        kotlinx.coroutines.delay(200L + 150L * attempt)
                        continue
                    }
                    Log.d(TAG, "$path HTTP ${raw.code} JSON 아님: ${raw.body.take(120).replace('\n', ' ')}")
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 화면을 닫았거나 타임을 바꿔 취소된 것 — 다음 후보로 넘어가면 안 됩니다.
                    throw e
                } catch (e: Exception) {
                    last = e
                    Log.d(TAG, "$path 실패: ${e.message}")
                    break
                }
            }
            // 빈 응답만 계속 받은 경로는 존재하는 경로이므로 다른 후보를 더 시도하지 않습니다.
            if (transientHere) break
        }
        throw last ?: HanaPortalException.UnexpectedResponse("[${paths.first()}] 사용할 수 있는 엔드포인트가 없습니다")
    }

    suspend fun seatMap(context: Context, slotId: String, service: SeatService = SeatService.LIBRARY): LibrarySeatMap = withContext(Dispatchers.IO) {
        val json = firstJson(
            context, service.listPaths, listOf("stIdxFull" to slotId, "subListYn" to "N"), referer = BASE + service.applyPage,
        )
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(cleanMessage(json.optString("resMsg")) ?: "좌석을 불러오지 못했습니다")
        }
        if (json.optString("hYn") == "Y") {
            return@withContext LibrarySeatMap(emptyList(), json.optString("hMsg").ifBlank { "휴관일" })
        }
        val open = json.optJSONObject("studyRoomOpenVo")
        val list = json.optJSONArray("list")
        val seatsRaw = (0 until (list?.length() ?: 0)).mapNotNull { i ->
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
                memberName = row.optString("sre_mem_name").takeIf { it.isNotBlank() && it != "null" },
                studentNumber = row.optString("sre_std_num").takeIf { it.isNotBlank() && it != "null" },
                gender = when (row.optString("mem_sex")) { "M" -> "남"; "F" -> "여"; else -> null },
                assigned = row.optString("sre_assign_yn").uppercase() == "Y",
            )
        }
        // 학번·이름 짝을 모아 두고(면학실 응답), 이름이 비어 온 자리(도서관 응답)는 캐시로 채웁니다.
        StudentNameCache.learn(context, seatsRaw)
        val seats = seatsRaw.map { seat ->
            if (seat.memberName == null && seat.studentNumber != null) {
                // 내장 디렉터리(학번→이름) → 면학실에서 배운 캐시 순.
                seat.copy(memberName = StudentDirectory.name(context, seat.studentNumber) ?: StudentNameCache.name(context, seat.studentNumber))
            } else {
                seat
            }
        }
        // 배치도는 세로로 긴 한 장입니다 (10 × 66 정도, 사이에 없는 행도 있음). 층은 통로 칸의 바닥색으로 갈립니다
        // (2F #efefef, 1F #d9d9d9). 행마다 바닥색을 구해 같은 색끼리 한 구역으로 묶고, 없는 행이 이어지는 큰 틈은
        // 한 줄 빈 행으로 줄입니다. 구역 이름은 표지 글자("2F입구")에서 층을 읽습니다.
        val gridX = open?.optInt("sro_x", 0)?.takeIf { it > 0 } ?: ((seats.maxOfOrNull { it.x } ?: 0) + 1)
        val gridY = open?.optInt("sro_y", 0)?.takeIf { it > 0 } ?: ((seats.maxOfOrNull { it.y } ?: 0) + 1)
        val rows = seats.filter { it.x >= 0 && it.y >= 0 }.groupBy { it.y }.toSortedMap()
        fun floorColor(row: List<LibrarySeat>): String =
            row.firstOrNull { it.type == "3" && it.color != null }?.color ?: row.firstNotNullOfOrNull { it.color } ?: ""
        // 구역 나누기: 바닥색이 바뀌면 새 구역. 도서관은 같은 층 안에서도 없는 행이 이어지는 큰 틈(2-68 과 2-69 사이)
        // 에서 한 번 더 나눠 "2층 1구역 / 2층 2구역"이 됩니다. 면학실은 색으로만 나눕니다 (E~H).
        val splitOnGap = service == SeatService.LIBRARY
        val groups = mutableListOf<Pair<String, MutableList<Pair<Int, List<LibrarySeat>>>>>()
        rows.forEach { (y, row) ->
            val color = floorColor(row)
            val last = groups.lastOrNull()
            val gap = last?.second?.lastOrNull()?.first?.let { y - it > 1 } ?: false
            if (last != null && last.first == color && !(splitOnGap && gap)) last.second += y to row
            else groups += color to mutableListOf(y to row)
        }
        // 층 이름은 그 바닥색의 어느 구역이든 표지 글자("2F입구")에서 읽습니다.
        fun floorOf(color: String): String? = groups.filter { it.first == color }
            .flatMap { it.second }.flatMap { it.second }
            // "2F입구"는 srt_type 3 으로 오기도 해서 종류를 가리지 않고 글자만 봅니다.
            .firstNotNullOfOrNull { cell -> if (!cell.isSeat) Regex("(\\d+)F").find(cell.cont)?.groupValues?.get(1) else null }
            ?.let { "${it}층" }
        val perColor = groups.groupingBy { it.first }.eachCount()
        val colorSeen = mutableMapOf<String, Int>()
        val areas = groups.mapIndexed { index, (color, rowList) ->
            // y 를 다시 매김: 연속이면 +1, 사이가 비면 빈 행 하나만.
            var next = 0
            var prevY: Int? = null
            val remapped = rowList.flatMap { (y, row) ->
                if (prevY != null) next += if (y - prevY!! > 1) 2 else 1
                prevY = y
                val yy = next
                row.map { it.copy(y = yy) }
            }
            val nth = (colorSeen[color] ?: 0) + 1
            colorSeen[color] = nth
            val floor = floorOf(color)
            val label = when {
                service == SeatService.STUDY_ROOM && index < 4 -> "구역 ${'E' + index}"
                floor != null && (perColor[color] ?: 1) > 1 -> "$floor ${nth}구역"
                floor != null -> floor
                else -> "구역 ${index + 1}"
            }
            LibraryArea(label, gridX, next + 1, remapped)
        }
        if (seats.isNotEmpty()) {
            Log.d(TAG, "seatMap $slotId grid=${gridX}x$gridY items=${seats.size} areas=${areas.map { it.label + ":" + it.seats.count { s -> s.isSeat } }}")
            // 남이 잡은 자리 하나의 원문 — 이름·학번 필드 이름 확인용 (도서관은 면학실과 필드가 다를 수 있음).
            val occupied = (0 until (list?.length() ?: 0)).map { list!!.optJSONObject(it) }
                .firstOrNull { it != null && it.optInt("sre_idx", 0) > 0 && it.optString("myYn") != "Y" }
            if (occupied != null) Log.d(TAG, "occupied sample (${service.label}): ${occupied.toString().take(900)}")
        }
        LibrarySeatMap(areas, null)
    }

    /** 신청 페이지 JSON 의 deviceChk — 이 기기(세션)가 "마지막 등록 기기"인지. 못 읽으면 null. */
    suspend fun isDeviceRegistered(context: Context, service: SeatService): Boolean? = withContext(Dispatchers.IO) {
        val body = runCatching { HanaPortalClient.get().authenticatedText(context, service.applyPage) }.getOrNull()
            ?: return@withContext null
        runCatching { JSONObject(body) }.getOrNull()?.takeIf { it.has("deviceChk") }?.optBoolean("deviceChk")
    }

    /** 이 기기를 "마지막 등록 기기"로 만듭니다 — 신청이 기기 때문에 거절될 때, 또는 사용자가 버튼으로. */
    suspend fun registerDevice(context: Context, service: SeatService) {
        val json = HanaPortalClient.get().authenticatedJson(
            context, "/main/member/updateDiviceInfo.json", listOf("usegubun" to service.deviceGubun), referer = BASE + service.applyPage,
        )
        Log.d(TAG, "registerDevice(${service.deviceGubun}): $json")
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(cleanMessage(json.optString("resMsg")) ?: "기기를 등록하지 못했습니다")
        }
    }

    /** 서버 문구의 HTML(`<br/>` 등)을 줄바꿈·공백으로 정리합니다. 비어 있으면 null. */
    private fun cleanMessage(raw: String?): String? = raw
        ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        ?.replace(Regex("<[^>]+>"), "")
        ?.replace("&nbsp;", " ")
        ?.lines()?.joinToString("\n") { it.trim() }   // <br/> 뒤에 붙어 있던 공백으로 줄이 한 칸 밀려 시작하지 않게
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /** 좌석 신청 — 성공하면 서버 문구(없으면 "신청되었습니다"), 실패하면 예외 메시지에 서버 문구. */
    /**
     * 좌석 신청 — 포털은 "마지막으로 등록한 기기"에서만 신청을 받습니다. 매번 등록하면 다른 기기(브라우저·예약 봇)의
     * 등록을 계속 빼앗으므로, 먼저 그냥 신청해 보고 기기 때문에 거절될 때만 이 기기를 등록하고 한 번 더 시도합니다.
     */
    suspend fun reserve(context: Context, seat: LibrarySeat, slotId: String, service: SeatService = SeatService.LIBRARY): String = withContext(Dispatchers.IO) {
        val params = listOf("clrIdx" to seat.clrIdx.toString(), "srtIdx" to seat.srtIdx.toString(), "stIdxFull" to slotId)
        var json = firstJson(context, service.reservePaths, params, referer = BASE + service.applyPage)
        if (json.optString("result") != "success" && json.optString("resMsg").contains("기기")) {
            Log.d(TAG, "device not registered here — registering and retrying")
            registerDevice(context, service)
            json = firstJson(context, service.reservePaths, params, referer = BASE + service.applyPage)
        }
        val message = cleanMessage(json.optString("resMsg"))
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(message ?: "신청하지 못했습니다")
        }
        message ?: "신청되었습니다"
    }

    suspend fun cancel(context: Context, sreIdx: Int, service: SeatService = SeatService.LIBRARY): String = withContext(Dispatchers.IO) {
        val json = HanaPortalClient.get().authenticatedJson(
            context, "/main/studyroom/study-cancel.json", listOf("sreIdx" to sreIdx.toString()), referer = BASE + service.applyPage,
        )
        val message = cleanMessage(json.optString("resMsg"))
        if (json.optString("result") != "success") {
            throw HanaPortalException.Rejected(message ?: "취소하지 못했습니다")
        }
        message ?: "취소되었습니다"
    }
}
