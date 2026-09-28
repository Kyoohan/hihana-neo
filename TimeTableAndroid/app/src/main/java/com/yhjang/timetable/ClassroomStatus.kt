package com.yhjang.timetable

import android.content.Context
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiDivider
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

// MARK: - 교과교실 현황 API

/** 오늘의 타임 (포털 stIdxFull = "stg_idx_st_idx"). */
data class ClassroomTime(val id: String, val name: String, val start: String?, val end: String?) {
    val label: String get() = if (start != null) "$name $start" else name
}

data class ClassroomBuilding(val num: String, val name: String)
data class ClassroomRoom(val idx: String, val name: String)

/** 교과교실 신청 한 건 — 교실, 신청자 이름·학년, 사유, 상태(신청/승인 등). */
data class ClassroomOccupant(
    val room: String,
    val time: String?,
    val name: String,
    val grade: String?,
    val reason: String?,
    val status: String?,
)

/**
 * 포털 교과교실 신청 페이지(/main/classroom/apply.do)의 조회 흐름을 그대로 재현합니다 — 페이지 스크립트 기준:
 * 타임(timeList) → 건물 `cls-plg-list.json {stIdxFull}` → 교실 `cls-cr-list.json {stIdxFull, slgNum}` →
 * 신청 목록 `apply-list.json {cp, pageSize, stIdxFull, slgNum, slpIdx}` (오늘 날짜, 모든 학생의 신청이 보임).
 * 모두 POST 폼입니다.
 */
object HanaClassroomApi {
    private const val TAG = "HanaClassroom"
    private const val BASE = "/main/classroom"
    private const val REFERER = "https://hh.hana.hs.kr/main/classroom/apply.do"

    suspend fun times(context: Context): List<ClassroomTime> = withContext(Dispatchers.IO) {
        val body = HanaPortalClient.get().authenticatedText(context, "$BASE/apply.do")
        val list = runCatching { JSONObject(body).optJSONArray("timeList") }.getOrNull() ?: JSONArray()
        (0 until list.length()).mapNotNull { i ->
            val row = list.optJSONObject(i) ?: return@mapNotNull null
            val group = row.optInt("stg_idx", -1)
            val idx = row.optInt("st_idx", -1)
            if (group < 0 || idx < 0) return@mapNotNull null
            ClassroomTime(
                id = "${group}_$idx",
                name = row.optString("st_nm").ifBlank { "타임 $idx" },
                start = row.str("st_time"),
                end = row.str("ed_time"),
            )
        }.distinctBy { it.id }
    }

    suspend fun buildings(context: Context, timeId: String): List<ClassroomBuilding> {
        val json = post(context, "cls-plg-list.json", listOf("stIdxFull" to timeId))
        return json.rows().mapNotNull { row ->
            val num = row.str("slg_num") ?: return@mapNotNull null
            ClassroomBuilding(num, row.str("slg_nm") ?: num)
        }
    }

    suspend fun rooms(context: Context, timeId: String, buildingNum: String): List<ClassroomRoom> {
        val json = post(context, "cls-cr-list.json", listOf("stIdxFull" to timeId, "slgNum" to buildingNum))
        return json.rows().mapNotNull { row ->
            val idx = row.str("slp_idx") ?: return@mapNotNull null
            ClassroomRoom(idx, row.str("slp_nm") ?: idx)
        }
    }

    /** 신청 목록 — [roomIdx] 가 null 이면 건물 전체. 페이지를 끝까지(최대 10쪽) 모읍니다. */
    suspend fun occupants(context: Context, timeId: String, buildingNum: String?, roomIdx: String?): List<ClassroomOccupant> {
        val result = mutableListOf<ClassroomOccupant>()
        var page = 1
        while (page <= 10) {
            val params = buildList {
                add("cp" to page.toString())
                add("pageSize" to "50")
                add("stIdxFull" to timeId)
                buildingNum?.let { add("slgNum" to it) }
                roomIdx?.let { add("slpIdx" to it) }
            }
            val json = post(context, "apply-list.json", params)
            val paging = json.optJSONObject("paging")
            val rows = paging?.optJSONArray("result") ?: JSONArray()
            if (page == 1 && rows.length() > 0) Log.d(TAG, "apply-list keys: ${rows.optJSONObject(0)?.keys()?.asSequence()?.toList()}")
            (0 until rows.length()).mapNotNullTo(result) { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNullTo null
                ClassroomOccupant(
                    room = row.str("slp_nm") ?: "교실",
                    time = row.str("st_nm"),
                    name = row.str("in_mem_name") ?: "이름 없음",
                    grade = row.str("cmt_grade"),
                    reason = row.str("crt_cont"),
                    status = row.str("cc_cd_name"),
                )
            }
            val total = paging?.optInt("rowTotal", -1) ?: -1
            if (rows.length() < 50 || (total >= 0 && result.size >= total)) break
            page++
        }
        return result
    }

    private suspend fun post(context: Context, name: String, params: List<Pair<String, String>>): JSONObject {
        val json = HanaPortalClient.get().authenticatedJson(context, "$BASE/$name", params, referer = REFERER)
        if (json.optString("result").let { it.isNotEmpty() && it != "success" }) {
            throw HanaPortalException.Rejected(json.optString("resMsg").ifBlank { "교과교실 정보를 불러오지 못했습니다" })
        }
        return json
    }

    private fun JSONObject.rows(): List<JSONObject> {
        val list = optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
    }

    private fun JSONObject.str(key: String): String? =
        optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
}

// MARK: - 화면

/**
 * 교과교실 현황 — 타임과 건물·교실을 고르면 그 교실을 신청한 학생과 사유를 보여줍니다 (포털 교과교실 신청 페이지의
 * 조회 표와 같은 데이터). 교실을 "전체"로 두면 건물 안의 신청을 교실별로 묶어 보여줍니다.
 */
@Composable
fun ClassroomStatusScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var times by remember { mutableStateOf<List<ClassroomTime>>(emptyList()) }
    var timeId by remember { mutableStateOf<String?>(null) }
    var buildings by remember { mutableStateOf<List<ClassroomBuilding>>(emptyList()) }
    var buildingNum by remember { mutableStateOf<String?>(null) }
    var rooms by remember { mutableStateOf<List<ClassroomRoom>>(emptyList()) }
    var roomIdx by remember { mutableStateOf<String?>(null) }
    var occupants by remember { mutableStateOf<List<ClassroomOccupant>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        error = e.message ?: "불러오지 못했습니다"
        null
    }

    // 타임 목록 — 지금 진행 중이거나 다음에 올 타임을 먼저 고릅니다.
    LaunchedEffect(Unit) {
        val list = guarded { HanaClassroomApi.times(context) } ?: emptyList()
        times = list
        val now = LocalTime.now(PlanStore.seoulZone)
        timeId = (list.firstOrNull { t -> t.end?.let { runCatching { LocalTime.parse(it) }.getOrNull() }?.isAfter(now) == true }
            ?: list.lastOrNull())?.id
        if (list.isEmpty() && error == null) error = "오늘 교과교실 타임이 없습니다"
        if (timeId == null) loading = false
    }
    // 타임이 바뀌면 건물 목록을 다시 받고 첫 건물을 고릅니다 (고른 건물이 목록에 있으면 유지).
    LaunchedEffect(timeId) {
        val id = timeId ?: return@LaunchedEffect
        error = null
        val list = guarded { HanaClassroomApi.buildings(context, id) } ?: emptyList()
        buildings = list
        if (list.none { it.num == buildingNum }) buildingNum = list.firstOrNull()?.num
    }
    LaunchedEffect(timeId, buildingNum) {
        val id = timeId ?: return@LaunchedEffect
        val num = buildingNum ?: return@LaunchedEffect
        val list = guarded { HanaClassroomApi.rooms(context, id, num) } ?: emptyList()
        rooms = list
        if (list.none { it.idx == roomIdx }) roomIdx = null
    }
    LaunchedEffect(timeId, buildingNum, roomIdx, reload) {
        val id = timeId ?: return@LaunchedEffect
        loading = true
        occupants = guarded { HanaClassroomApi.occupants(context, id, buildingNum, roomIdx) }
        loading = false
    }

    val selectedTime = times.firstOrNull { it.id == timeId }
    OneUiFullScreen(
        title = "교과교실 현황",
        subtitle = selectedTime?.let { t -> listOfNotNull(t.name, t.start?.let { s -> "$s–${t.end.orEmpty()}" }).joinToString(" · ") },
        onDismiss = onDismiss,
    ) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp),
        ) {
            ChipRow("타임") {
                times.forEach { t -> OneUiChip(selected = t.id == timeId, onClick = { timeId = t.id }, label = t.label) }
            }
            if (buildings.isNotEmpty()) {
                ChipRow("건물") {
                    buildings.forEach { b -> OneUiChip(selected = b.num == buildingNum, onClick = { buildingNum = b.num }, label = b.name) }
                }
            }
            if (rooms.isNotEmpty()) {
                ChipRow("교실") {
                    OneUiChip(selected = roomIdx == null, onClick = { roomIdx = null }, label = "전체")
                    rooms.forEach { r -> OneUiChip(selected = r.idx == roomIdx, onClick = { roomIdx = r.idx }, label = r.name) }
                }
            }
            Spacer(Modifier.height(6.dp))
            val list = occupants
            when {
                error != null -> OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
                    Text("불러오지 못했습니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                loading && list == null -> Row(Modifier.padding(OneUi.PagePadding), verticalAlignment = Alignment.CenterVertically) {
                    OneUiLoading(size = 18.dp, stroke = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("신청 목록을 불러오는 중", style = MaterialTheme.typography.bodyMedium)
                }
                list != null -> {
                    Text(
                        if (list.isEmpty()) "이 조건으로 신청한 학생이 없습니다" else "신청 ${list.size}명",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                    )
                    Spacer(Modifier.height(10.dp))
                    // 교실별로 묶어 교실 이름 순으로.
                    list.groupBy { it.room }.toSortedMap().forEach { (room, people) ->
                        OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(room, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("${people.size}명", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            people.forEachIndexed { i, p ->
                                if (i > 0) OneUiDivider(startIndent = 0.dp)
                                OccupantRow(p)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, chips: @Composable () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = OneUi.PagePadding),
    )
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = OneUi.PagePadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { chips() }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OccupantRow(p: ClassroomOccupant) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            p.grade?.let {
                Spacer(Modifier.width(6.dp))
                Text("${it}학년", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            p.status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (it.contains("승인")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            p.reason ?: "사유 없음",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
