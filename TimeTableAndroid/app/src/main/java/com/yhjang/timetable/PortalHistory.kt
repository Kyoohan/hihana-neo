package com.yhjang.timetable

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiActionButton
import com.yhjang.timetable.ui.OneUiAlertDialog
import com.yhjang.timetable.ui.OneUiDialog
import com.yhjang.timetable.ui.OneUiDialogButton
import com.yhjang.timetable.ui.OneUiBadge
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiButtonStyle
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiDivider
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// MARK: - 면학실·도서관 내역 API

/**
 * 면학실·도서관 내역 — 두 포털 내역 페이지(study-history.do, library-history.do)가 같은 목록 JSON 을 장소 코드만 바꿔
 * 부릅니다. 학생 번호는 보내지 않아도 서버가 로그인 세션으로 본인 것만 돌려줍니다.
 *
 * POST /main/studyroom/study-only-req-list.json
 *   cp, pageSize, searchSDate, searchEDate, stIdSPAll=N, cPlaceCds(여러 개), [assignYn=N]
 * → { result, resMsg, paging: { rowTotal, result: [ { sre_dt, st_nm, c_place_cd_name, clr_area_nm, srt_cont, ca_chk_yn, cadatetime, … } ] } }
 */
enum class SeatHistoryPlace(val label: String, val referer: String, val placeCodes: List<String>, val excludeAssigned: Boolean) {
    /** 면학실 — 층별 장소 코드 셋. 포털 페이지처럼 지정석은 뺍니다. */
    STUDY_ROOM("면학실", "/main/studyroom/study-history.do", listOf("8002", "8003", "8004"), excludeAssigned = true),
    LIBRARY("도서관", "/main/library/library-history.do", listOf("8001"), excludeAssigned = false),
}

data class SeatHistoryRow(
    val id: Long,
    val date: LocalDate?,
    val slot: String,
    val slotOrder: Int,
    /** "6층 일반형E" / "2F" 처럼 장소와 구역. */
    val area: String?,
    val seat: String?,
    /** 감독 선생님의 출석 확인 시각 — 확인이 없으면 null. */
    val checkedAt: LocalDateTime?,
)

data class HistoryPage<T>(val rows: List<T>, val total: Int)

object SeatHistoryApi {
    private const val PATH = "/main/studyroom/study-only-req-list.json"

    suspend fun fetch(context: Context, place: SeatHistoryPlace, from: LocalDate, to: LocalDate, page: Int, pageSize: Int = 300): HistoryPage<SeatHistoryRow> {
        val params = buildList {
            add("cp" to page.toString())
            add("pageSize" to pageSize.toString())
            add("searchSDate" to from.toString())
            add("searchEDate" to to.toString())
            add("stIdSPAll" to "N")
            place.placeCodes.forEach { add("cPlaceCds" to it) }
            if (place.excludeAssigned) add("assignYn" to "N")
        }
        val json = PortalLists.post(context, PATH, params, place.referer)
        return PortalLists.page(json) { row ->
            SeatHistoryRow(
                id = row.optLong("sre_idx"),
                date = row.str("sre_dt")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
                slot = row.str("st_nm") ?: "타임",
                slotOrder = row.optInt("stg_idx") * 100 + row.optInt("st_idx"),
                area = listOfNotNull(
                    // 도서관은 장소 이름이 "도서관" 뿐이라 구역(2F)만, 면학실은 층 + 구역.
                    row.str("c_place_cd_name")?.takeIf { place != SeatHistoryPlace.LIBRARY },
                    row.str("clr_area_nm"),
                ).joinToString(" ").ifBlank { null },
                seat = row.str("srt_cont"),
                checkedAt = row.str("cadatetime")?.takeIf { row.str("ca_chk_yn") == "Y" }?.let { PortalLists.dateTime(it) },
            )
        }
    }
}

// MARK: - 외출외박 내역 API

/**
 * 외출외박 내역 — 포털 history.do 의 목록·상세 JSON.
 *
 * POST /main/outing/outing_detail_list.json  cp, pageSize, searchSDate…, authChkYn=ALL, osdState(2 신청 · 4 승인 · 8 반려 · 16 복귀 · 32 취소)
 * POST /main/outing/outing_detail.json       osm_no, std_mem_no → { resultVo: { trans_mtd, app_tc_mem_name, cfm_ret_mem_name, … } }
 */
data class OutingRow(
    val osmNo: Long,
    val stdMemNo: String,
    val state: Int,
    val stateLabel: String,
    val plannedOut: LocalDateTime?,
    val plannedBack: LocalDateTime?,
    val actualOut: LocalDateTime?,
    val actualBack: LocalDateTime?,
    val duration: String?,
    val reason: String?,
    val appliedAt: LocalDateTime?,
    /** 단체 신청(선생님이 반·학년 전체로 넣은 것). */
    val group: Boolean,
)

data class OutingDetail(
    val transport: String?,
    val approver: String?,
    val returnChecker: String?,
)

enum class OutingFilter(val label: String, val code: String) {
    ALL("전체", ""), APPLIED("신청", "2"), APPROVED("승인", "4"), REJECTED("반려", "8"), RETURNED("복귀", "16"), CANCELLED("취소", "32"),
}

object OutingApi {
    private const val REFERER = "/main/outing/history.do"

    suspend fun list(context: Context, filter: OutingFilter, page: Int, pageSize: Int = 20): HistoryPage<OutingRow> {
        val params = listOf(
            "cp" to page.toString(),
            "pageSize" to pageSize.toString(),
            "searchSDate" to "", "searchEDate" to "", "searchSDate2" to "", "searchEDate2" to "",
            "addOrder" to "",
            "authChkYn" to "ALL",
            "osdState" to filter.code,
        )
        val json = PortalLists.post(context, "/main/outing/outing_detail_list.json", params, REFERER)
        return PortalLists.page(json) { row ->
            OutingRow(
                osmNo = row.optLong("osm_no"),
                stdMemNo = row.str("std_mem_no").orEmpty(),
                state = row.optInt("osd_state"),
                stateLabel = row.str("osd_state_str") ?: "",
                plannedOut = row.str("dep_exp_dt")?.let { PortalLists.dateTime(it) },
                plannedBack = row.str("ret_exp_dt")?.let { PortalLists.dateTime(it) },
                actualOut = row.str("dep_dt")?.let { PortalLists.dateTime(it) },
                actualBack = row.str("ret_dt")?.let { PortalLists.dateTime(it) },
                duration = row.str("exp_date_str"),
                reason = row.str("out_rsn"),
                appliedAt = row.str("inputdate")?.let { PortalLists.dateTime(it) },
                group = row.str("out_all_yn") == "Y",
            )
        }
    }

    suspend fun detail(context: Context, row: OutingRow): OutingDetail {
        val json = PortalLists.post(
            context, "/main/outing/outing_detail.json",
            listOf("osm_no" to row.osmNo.toString(), "std_mem_no" to row.stdMemNo), REFERER,
        )
        val vo = json.optJSONObject("resultVo") ?: throw IllegalStateException("상세를 받지 못했습니다")
        return OutingDetail(
            transport = vo.str("trans_mtd"),
            approver = vo.str("app_tc_mem_name") ?: vo.str("acc_tc_mem_name"),
            returnChecker = vo.str("cfm_ret_mem_name"),
        )
    }
}

/** 포털 목록 JSON 공통 — 폼 POST, `result` 확인, `paging.result` 읽기. */
private object PortalLists {
    private const val BASE = "https://hh.hana.hs.kr"
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    suspend fun post(context: Context, path: String, params: List<Pair<String, String>>, referer: String): JSONObject =
        withContext(Dispatchers.IO) {
            val json = HanaPortalClient.get().authenticatedJson(context, path, params, referer = BASE + referer)
            if (json.optString("result") != "success") {
                throw IllegalStateException(json.str("resMsg") ?: "포털이 목록을 주지 않았습니다")
            }
            json
        }

    fun <T> page(json: JSONObject, map: (JSONObject) -> T): HistoryPage<T> {
        val paging = json.optJSONObject("paging")
        val array = paging?.optJSONArray("result")
        val rows = (0 until (array?.length() ?: 0)).mapNotNull { i -> array?.optJSONObject(i)?.let(map) }
        return HistoryPage(rows, paging?.optInt("rowTotal", rows.size) ?: rows.size)
    }

    /** "2026-10-11 13:39:33.0" → 분까지. */
    fun dateTime(text: String): LocalDateTime? = runCatching { LocalDateTime.parse(text.take(16), stamp) }.getOrNull()
}

private fun JSONObject.str(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

// MARK: - 날짜 문구

private fun LocalDate.dayLabel(today: LocalDate): String {
    val day = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    val base = "${monthValue}월 ${dayOfMonth}일 ($day)"
    return when (this) {
        today -> "오늘 · $base"
        today.plusDays(1) -> "내일 · $base"
        today.minusDays(1) -> "어제 · $base"
        else -> if (year != today.year) "${year}년 $base" else base
    }
}

private fun LocalDateTime.shortLabel(): String {
    val day = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    return "%d/%d(%s) %02d:%02d".format(monthValue, dayOfMonth, day, hour, minute)
}

private fun LocalDateTime.timeLabel(): String = "%02d:%02d".format(hour, minute)

private fun loadErrorText(e: Exception): String = when (e) {
    is HanaPortalException.MissingCredentials -> "하이하나 계정을 먼저 등록해 주세요"
    else -> e.message ?: "불러오지 못했습니다"
}

// MARK: - 공용 화면 조각

@Composable
private fun HistoryChipRow(chips: @Composable () -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = OneUi.PagePadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { chips() }
}

@Composable
private fun HistoryStatus(loading: Boolean, error: String?, empty: Boolean, emptyText: String) {
    when {
        error != null -> OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
            Text("불러오지 못했습니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        loading && empty -> Row(Modifier.padding(OneUi.PagePadding), verticalAlignment = Alignment.CenterVertically) {
            OneUiLoading(size = 18.dp, stroke = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("내역을 불러오는 중", style = MaterialTheme.typography.bodyMedium)
        }
        empty -> Text(
            emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = OneUi.PagePadding, vertical = 8.dp),
        )
    }
}

@Composable
private fun MoreButton(loading: Boolean, remaining: Int, onClick: () -> Unit) {
    Spacer(Modifier.height(4.dp))
    OneUiButton(
        text = if (loading) "불러오는 중" else "더 보기 · ${remaining}건 남음",
        onClick = onClick,
        style = OneUiButtonStyle.Neutral,
        enabled = !loading,
        modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
    )
}

@Composable
private fun OpenInPortalButton(onClick: () -> Unit) {
    OneUiActionButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "포털에서 열기")
    }
}

// MARK: - 면학실·도서관 내역 화면

private enum class HistoryRange(val label: String, val days: Long) { WEEK("1주", 7), TWO_WEEKS("2주", 14), MONTH("1개월", 31), QUARTER("3개월", 92) }

/**
 * 면학실·도서관 내역 — 기간 칩으로 고르고 날짜별로 묶습니다. 한 줄에 타임, 장소·자리, 출석 확인 시각.
 * 앞으로의 신청(내일 타임 등)도 보이도록 끝 날짜는 오늘 + 7일입니다.
 */
@Composable
fun SeatHistoryScreen(place: SeatHistoryPlace, onDismiss: () -> Unit, onOpenPortal: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { PlanStore.today() }
    var range by remember { mutableStateOf(HistoryRange.TWO_WEEKS) }
    var rows by remember { mutableStateOf<List<SeatHistoryRow>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(range: HistoryRange, nextPage: Int) {
        loading = true
        error = null
        try {
            val result = SeatHistoryApi.fetch(context, place, today.minusDays(range.days), today.plusDays(7), nextPage)
            rows = if (nextPage == 1) result.rows else rows + result.rows
            total = result.total
            page = nextPage
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = loadErrorText(e)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(range) {
        rows = emptyList()
        load(range, 1)
    }

    OneUiFullScreen(
        title = "${place.label} 내역",
        // 기간은 칩, 건수는 요약 줄("출석 확인 N건 · 전체 M건")에 있어 부제는 두지 않습니다.
        subtitle = null,
        onDismiss = onDismiss,
        actions = { OpenInPortalButton(onOpenPortal) },
    ) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp),
        ) {
            HistoryChipRow {
                HistoryRange.entries.forEach { r -> OneUiChip(selected = r == range, onClick = { range = r }, label = r.label) }
            }
            Spacer(Modifier.height(14.dp))
            HistoryStatus(loading, error, rows.isEmpty(), "이 기간에 ${place.label} 신청이 없습니다")
            if (rows.isNotEmpty()) {
                // 한 번에 300건까지 받아 3개월도 보통 한 번에 다 옵니다 — 그래서 전체 건수와 맞습니다.
                val checked = rows.count { it.checkedAt != null }
                Text(
                    "출석 확인 ${checked}건 · 전체 ${total}건",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                )
                Spacer(Modifier.height(10.dp))
                rows.groupBy { it.date }
                    .toSortedMap(compareByDescending<LocalDate?> { it ?: LocalDate.MIN })
                    .forEach { (date, dayRows) ->
                        Text(
                            date?.dayLabel(today) ?: "날짜 없음",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = OneUi.PagePadding + 4.dp, end = OneUi.PagePadding, bottom = 6.dp),
                        )
                        OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
                            dayRows.sortedBy { it.slotOrder }.forEachIndexed { i, row ->
                                if (i > 0) OneUiDivider(startIndent = 0.dp)
                                SeatHistoryRowView(row)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                if (rows.size < total) MoreButton(loading, total - rows.size) { scope.launch { load(range, page + 1) } }
            }
        }
    }
}

@Composable
private fun SeatHistoryRowView(row: SeatHistoryRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(row.slot, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            row.area?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.seat ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            row.checkedAt?.let {
                Text(
                    "출석 확인 ${it.timeLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// MARK: - 외출외박 내역 화면

/**
 * 외출외박 내역 — 상태 칩으로 거르고, 카드를 누르면 이동방법·승인 교사·복귀 확인 교사를 펼쳐 봅니다(상세는 처음 펼칠 때 받음).
 * 펼친 카드에서 승인된 건은 출입 QR, 신청(대기) 상태의 개인 신청은 수정·취소 — 포털 내역 화면과 같은 조건입니다.
 * 수정·취소는 확인 창에서 한 번 더 눌러야 보냅니다.
 */
@Composable
fun OutingHistoryScreen(onDismiss: () -> Unit, onOpenPortal: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(OutingFilter.ALL) }
    var rows by remember { mutableStateOf<List<OutingRow>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    val details = remember { mutableStateMapOf<Long, Result<OutingDetail>>() }
    var reload by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<OutingEdit?>(null) }
    var cancelling by remember { mutableStateOf<OutingRow?>(null) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<String, String>?>(null) }
    var qrFor by remember { mutableStateOf<OutingRow?>(null) }

    suspend fun load(filter: OutingFilter, nextPage: Int) {
        loading = true
        error = null
        try {
            val result = OutingApi.list(context, filter, nextPage)
            rows = if (nextPage == 1) result.rows else rows + result.rows
            total = result.total
            page = nextPage
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = loadErrorText(e)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(filter, reload) {
        rows = emptyList()
        expanded = null
        details.clear()
        load(filter, 1)
    }

    OneUiFullScreen(
        title = "외출·외박 내역",
        // 상태는 칩이 보여 줘 건수만.
        subtitle = if (error == null && (rows.isNotEmpty() || !loading)) "${total}건" else null,
        onDismiss = onDismiss,
        actions = { OpenInPortalButton(onOpenPortal) },
    ) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp),
        ) {
            HistoryChipRow {
                OutingFilter.entries.forEach { f -> OneUiChip(selected = f == filter, onClick = { filter = f }, label = f.label) }
            }
            Spacer(Modifier.height(14.dp))
            HistoryStatus(loading, error, rows.isEmpty(), if (filter == OutingFilter.ALL) "외출·외박 신청이 없습니다" else "${filter.label} 상태인 신청이 없습니다")
            rows.forEach { row ->
                val open = expanded == row.osmNo
                OutingCard(
                    row = row,
                    open = open,
                    detail = details[row.osmNo],
                    onQr = { qrFor = row },
                    onEdit = { d ->
                        if (row.plannedOut != null && row.plannedBack != null) {
                            editing = OutingEdit(row.osmNo, row.plannedOut, row.plannedBack, row.reason.orEmpty(), d.transport)
                        }
                    },
                    onCancel = { cancelling = row },
                    onClick = {
                        expanded = if (open) null else row.osmNo
                        if (!open && details[row.osmNo]?.isSuccess != true) {
                            scope.launch {
                                details[row.osmNo] = try {
                                    Result.success(OutingApi.detail(context, row))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Result.failure(e)
                                }
                            }
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (rows.isNotEmpty() && rows.size < total) MoreButton(loading, total - rows.size) { scope.launch { load(filter, page + 1) } }
        }
    }

    editing?.let { e ->
        OutingApplyScreen(
            onDismiss = { editing = null },
            onOpenPortal = { editing = null; onOpenPortal() },
            onOpenHistory = { editing = null },
            edit = e,
            onEdited = { editing = null; reload++ },
        )
    }
    cancelling?.let { row ->
        ConfirmSendDialog(
            title = "외출·외박 신청을 취소할까요?",
            lines = listOfNotNull(
                row.plannedOut?.let { "출발" to it.shortLabel() },
                row.plannedBack?.let { "복귀" to it.shortLabel() },
                row.reason?.let { "사유" to it },
            ),
            confirmText = "신청 취소",
            busy = sending,
            destructive = true,
            onConfirm = {
                if (sending) return@ConfirmSendDialog
                sending = true
                scope.launch {
                    result = try {
                        "취소했습니다" to OutingApplyApi.cancel(context, row.osmNo, row.stdMemNo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "취소하지 못했습니다" to loadErrorText(e)
                    }
                    sending = false
                    cancelling = null
                    reload++
                }
            },
            onDismiss = { cancelling = null },
        )
    }
    qrFor?.let { row -> OutingQrDialog(row, onDismiss = { qrFor = null }) }
    result?.let { (title, message) -> OneUiAlertDialog(onDismissRequest = { result = null }, title = title, message = message) }
}

/** 출입 QR — 처음엔 포털에서 받아 저장하고, 다음부턴 저장된 그림을 바로 보여 줍니다. 화면을 최대 밝기로 올립니다. */
@Composable
internal fun OutingQrDialog(row: OutingRow, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(row.osmNo) {
        try {
            bitmap = OutingApplyApi.qr(context, row.osmNo, row.stdMemNo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = loadErrorText(e)
        }
    }
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "출입 QR",
        buttons = listOf(OneUiDialogButton("닫기", onDismiss)),
    ) {
        // 교문 리더기가 잘 읽도록 이 창이 떠 있는 동안 화면 밝기를 최대로 — 밝기는 맨 위 창(이 다이얼로그 창)에 걸어야
        // 적용됩니다. 예전엔 액티비티 창에 걸어서 다이얼로그가 떠 있는 동안엔 효과가 없었습니다.
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.DisposableEffect(dialogView) {
            val window = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            window?.attributes = window?.attributes?.also { it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            onDispose { window?.attributes = window?.attributes?.also { it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val image = bitmap
            when {
                image != null -> androidx.compose.foundation.Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "외출외박 출입 QR",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp),
                )
                error != null -> Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) { OneUiLoading() }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                listOfNotNull(row.plannedOut?.shortLabel(), row.plannedBack?.shortLabel()).joinToString("  →  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun outingStateColors(state: Int): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (state) {
        4 -> scheme.primary to scheme.onPrimary // 승인
        8 -> scheme.error to scheme.onError // 반려
        2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer // 신청(대기)
        else -> scheme.onSurface.copy(alpha = 0.08f) to scheme.onSurfaceVariant // 복귀·취소
    }
}

@Composable
private fun OutingCard(
    row: OutingRow,
    open: Boolean,
    detail: Result<OutingDetail>?,
    onQr: () -> Unit,
    onEdit: (OutingDetail) -> Unit,
    onCancel: () -> Unit,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OneUiCard(
        Modifier
            .padding(horizontal = OneUi.PagePadding)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (container, content) = outingStateColors(row.state)
            OneUiBadge(row.stateLabel.ifBlank { "상태 ${row.state}" }, container = container, content = content)
            if (row.group) {
                Spacer(Modifier.width(6.dp))
                OneUiBadge("단체", container = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), content = muted)
            }
            Spacer(Modifier.weight(1f))
            row.duration?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            listOfNotNull(row.plannedOut?.shortLabel(), row.plannedBack?.shortLabel()).joinToString("  →  "),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        row.reason?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (row.actualOut != null || row.actualBack != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(
                    row.actualOut?.let { "실제 출발 ${it.shortLabel()}" },
                    row.actualBack?.let { "복귀 ${it.shortLabel()}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(10.dp))
                OneUiDivider(startIndent = 0.dp)
                Spacer(Modifier.height(8.dp))
                val d = detail?.getOrNull()
                when {
                    detail == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        OneUiLoading(size = 14.dp, stroke = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("상세를 불러오는 중", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    d == null -> Text(
                        (detail.exceptionOrNull() as? Exception)?.let { loadErrorText(it) } ?: "상세를 불러오지 못했습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailLine("이동방법", d.transport)
                        DetailLine("승인 교사", d.approver)
                        DetailLine("복귀 확인", d.returnChecker)
                        DetailLine("신청한 때", row.appliedAt?.shortLabel())
                        // 포털과 같은 조건 — 승인(4)이면 출입 QR, 신청(2)이면서 개인 신청이면 수정·취소.
                        val canChange = row.state == 2 && !row.group
                        if (row.state == 4 || canChange) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (row.state == 4) {
                                    OneUiButton(text = "출입 QR", onClick = onQr, compact = true, modifier = Modifier.weight(1f))
                                }
                                if (canChange) {
                                    OneUiButton(text = "수정", onClick = { onEdit(d) }, style = OneUiButtonStyle.Neutral, compact = true, modifier = Modifier.weight(1f))
                                    OneUiButton(text = "신청 취소", onClick = onCancel, style = OneUiButtonStyle.Neutral, compact = true, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value ?: "-", style = MaterialTheme.typography.bodySmall)
    }
}
