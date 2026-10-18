package com.yhjang.timetable

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiActionButton
import com.yhjang.timetable.ui.OneUiAlertDialog
import com.yhjang.timetable.ui.OneUiBadge
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiButtonStyle
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiDialog
import com.yhjang.timetable.ui.OneUiDialogButton
import com.yhjang.timetable.ui.OneUiDivider
import com.yhjang.timetable.ui.GlassDatePickerDialog
import com.yhjang.timetable.ui.GlassTimePickerDialog
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

// MARK: - 쓰기 요청 (신청·취소)

/**
 * 포털에 신청·취소를 **한 번만** 보냅니다. 목록 조회와 달리 빈 응답·시간 초과에도 다시 보내지 않습니다 — 서버가 첫 요청을
 * 이미 처리했을 수 있어 두 번 신청될 수 있기 때문입니다. 그런 경우엔 "내역에서 확인해 주세요"로 알립니다.
 * (로그인 페이지가 내려오면 요청이 처리되지 않은 것이라 다시 로그인한 뒤 한 번 더 보냅니다 — [HanaPortalClient.authenticatedRaw].)
 */
private object PortalWrite {
    private const val BASE = "https://hh.hana.hs.kr"
    private const val WAIT_MS = 30_000L

    suspend fun send(context: Context, path: String, params: List<Pair<String, String>>, referer: String, failText: String): String =
        withContext(Dispatchers.IO) {
            val response = try {
                HanaPortalClient.get().authenticatedRaw(context, path, params, method = "POST", referer = BASE + referer, readTimeoutMs = WAIT_MS)
            } catch (e: java.io.InterruptedIOException) {
                throw HanaPortalException.Timeout("서버가 답하지 않았습니다 — 실제로 처리됐을 수 있으니 내역에서 확인해 주세요")
            }
            val json = runCatching { JSONObject(response.body) }.getOrNull()
                ?: throw IllegalStateException(
                    if (response.body.isBlank()) "응답이 비어 있었습니다 — 처리됐을 수 있으니 내역에서 확인해 주세요"
                    else "응답을 읽지 못했습니다 (HTTP ${response.code}) — 내역에서 확인해 주세요",
                )
            val message = json.text("resMsg")
            if (json.optString("result") != "success") throw HanaPortalException.Rejected(message ?: failText)
            message ?: "완료되었습니다"
        }
}

private const val PORTAL = "https://hh.hana.hs.kr"

private fun JSONObject.text(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

private fun JSONObject.list(key: String = "list"): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private suspend fun portalList(context: Context, path: String, params: List<Pair<String, String>>, referer: String): JSONObject =
    withContext(Dispatchers.IO) {
        val json = HanaPortalClient.get().authenticatedJson(context, path, params, referer = PORTAL + referer)
        if (json.optString("result") != "success") throw IllegalStateException(json.text("resMsg") ?: "불러오지 못했습니다")
        json
    }

private fun parseTime(text: String?): LocalTime? = text?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrNull() }

private fun errorText(e: Exception): String = when (e) {
    is HanaPortalException.MissingCredentials -> "하이하나 계정을 먼저 등록해 주세요"
    else -> e.message ?: "실패했습니다"
}

// MARK: - 교과교실 신청 API

/**
 * 교과교실 신청 — 포털 apply.do 의 "신청" 창과 같은 순서입니다 (오늘 날짜로만 신청).
 *   건물  POST /main/classroom/cls-plg-list.json   stIdxFull=, schClassYn=Y
 *   교실  POST /main/classroom/cls-cr-list.json    stIdxFull=, slgNum, schClassYn=Y
 *   정보  POST /main/classroom/cls-cr-info.json    crIdx → 승인 방식(ca_cd), 승인 교사, 사전 동의 항목
 *   타임  POST /main/classroom/cls-crtime-list.json crIdx → 오늘 타임 (stg_idx 8 은 포털도 뺍니다)
 *   신청  POST /main/classroom/apply-insert.do      slg_num, cr_idx, crt_cont, stIdxFull2, [tc_mem_no], [pre_conf_yn…]
 *   취소  POST /main/classroom/apply-cancel.do      crt_idx
 * 승인 방식: CA01 자동승인 · CA02 교사승인(교사 선택) · CA03 사전양식승인(교사 선택 + 주의사항 모두 동의).
 */
data class ClassroomBuildingOption(val num: String, val name: String)
data class ClassroomRoomOption(val idx: String, val name: String, val approval: String?, val approvalLabel: String?)
data class ClassroomTeacher(val no: String, val name: String)
data class ClassroomRoomDetail(
    val approval: String?,
    val approvalLabel: String?,
    val teachers: List<ClassroomTeacher>,
    val notice: String?,
    val procedure: String?,
    /** 사전 동의 항목 (cpc_idx, 내용). */
    val checks: List<Pair<String, String>>,
) {
    val needsTeacher: Boolean get() = approval == CA_TEACHER || approval == CA_PRESET
    val needsAgreement: Boolean get() = approval == CA_PRESET

    companion object {
        const val CA_AUTO = "CA01000000"
        const val CA_TEACHER = "CA02000000"
        const val CA_PRESET = "CA03000000"
    }
}
data class ClassroomSlotOption(val id: String, val name: String, val start: LocalTime?, val end: LocalTime?) {
    val label: String get() = listOfNotNull(name, start?.let { "%02d:%02d".format(it.hour, it.minute) }).joinToString(" ")
}
data class MyClassroomApply(
    val crtIdx: String,
    val room: String,
    val slot: String,
    val start: LocalTime?,
    val end: LocalTime?,
    val status: String?,
    val reason: String?,
    val cancellable: Boolean,
)

object ClassroomApplyApi {
    private const val APPLY_PAGE = "/main/classroom/apply.do"

    suspend fun buildings(context: Context): List<ClassroomBuildingOption> =
        portalList(context, "/main/classroom/cls-plg-list.json", listOf("stIdxFull" to "", "schClassYn" to "Y"), APPLY_PAGE)
            .list().mapNotNull { o -> o.text("slg_num")?.let { ClassroomBuildingOption(it, o.text("slg_nm") ?: it) } }

    suspend fun rooms(context: Context, buildingNum: String): List<ClassroomRoomOption> =
        portalList(context, "/main/classroom/cls-cr-list.json", listOf("stIdxFull" to "", "slgNum" to buildingNum, "schClassYn" to "Y"), APPLY_PAGE)
            .list().mapNotNull { o ->
                o.text("cr_idx")?.let { ClassroomRoomOption(it, o.text("slp_nm") ?: it, o.text("ca_cd"), o.text("ca_cd_name")) }
            }

    suspend fun detail(context: Context, roomIdx: String): ClassroomRoomDetail {
        val item = portalList(context, "/main/classroom/cls-cr-info.json", listOf("crIdx" to roomIdx), APPLY_PAGE).optJSONObject("item")
            ?: throw IllegalStateException("교실 정보를 받지 못했습니다")
        val preset = item.optJSONObject("preSetVo")
        val roomName = item.text("slp_nm").orEmpty()
        fun plain(text: String?) = text?.replace("{{선택 장소명}}", roomName)
            ?.let { HtmlCompat.fromHtml(it.replace("\r\n", "<br>").replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
            ?.takeIf { it.isNotEmpty() }
        return ClassroomRoomDetail(
            approval = item.text("ca_cd"),
            approvalLabel = item.text("ca_cd_name"),
            teachers = item.list("teacherList").mapNotNull { t -> t.text("crc_mem_no")?.let { ClassroomTeacher(it, t.text("crc_mem_name") ?: it) } },
            notice = plain(preset?.text("cps_info")),
            procedure = plain(preset?.text("cps_pro_cont")),
            checks = preset?.list("checkList").orEmpty().mapNotNull { c -> c.text("cpc_idx")?.let { it to (c.text("cpc_cont") ?: "") } },
        )
    }

    suspend fun slots(context: Context, roomIdx: String): List<ClassroomSlotOption> =
        portalList(context, "/main/classroom/cls-crtime-list.json", listOf("crIdx" to roomIdx), APPLY_PAGE)
            .list().filter { it.optInt("stg_idx") != 8 }.mapNotNull { o ->
                val group = o.text("stg_idx") ?: return@mapNotNull null
                val idx = o.text("st_idx") ?: return@mapNotNull null
                ClassroomSlotOption("${group}_$idx", o.text("st_nm") ?: "타임 $idx", parseTime(o.text("st_time")), parseTime(o.text("ed_time")))
            }

    /** 오늘 내 신청 — 포털 내역 페이지의 목록(세션 기준 본인 것만)을 오늘 하루로. */
    suspend fun mine(context: Context): List<MyClassroomApply> {
        val today = PlanStore.today()
        val now = LocalTime.now(PlanStore.seoulZone)
        val json = portalList(
            context, "/main/classroom/apply-list.json",
            listOf("cp" to "1", "pageSize" to "50", "searchSDate" to today.toString(), "searchEDate" to today.toString(), "stIdSPAll" to "N"),
            "/main/classroom/history.do",
        )
        val array = json.optJSONObject("paging")?.optJSONArray("result")
        return (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
            .filter { it.text("myYn") != "N" }
            .mapNotNull { o ->
                val start = parseTime(o.text("st_time"))
                // 포털과 같은 조건 — 내 신청이고, 신청·승인 상태이고, 타임 시작 전일 때만 취소 버튼.
                val cancellable = o.text("myYn") == "Y" && o.text("preYn") == "Y" &&
                    o.text("cc_cd") in setOf("CC01000000", "CC02000000") && start != null && start.isAfter(now)
                rowOf(o, start, cancellable)
            }
    }

    private fun rowOf(o: JSONObject, start: LocalTime?, cancellable: Boolean): MyClassroomApply? {
        val id = o.text("crt_idx") ?: return null
        return MyClassroomApply(
            crtIdx = id,
            room = o.text("slp_nm") ?: "교실",
            slot = o.text("st_nm") ?: "",
            start = start,
            end = parseTime(o.text("ed_time")),
            status = o.text("cc_cd_name"),
            reason = o.text("crt_cont"),
            cancellable = cancellable,
        )
    }

    suspend fun apply(
        context: Context,
        building: ClassroomBuildingOption,
        room: ClassroomRoomOption,
        detail: ClassroomRoomDetail,
        slot: ClassroomSlotOption,
        reason: String,
        teacher: ClassroomTeacher?,
        agreed: List<String>,
    ): String {
        // 포털 폼을 직렬화한 순서 그대로 — 교사는 교사·사전양식 승인 교실만, 동의 항목은 사전양식 승인 교실만 보냅니다.
        val params = buildList {
            add("slg_num" to building.num)
            add("cr_idx" to room.idx)
            add("crt_cont" to reason)
            add("stIdxFull2" to slot.id)
            if (detail.needsTeacher && teacher != null) add("tc_mem_no" to teacher.no)
            if (detail.needsAgreement) agreed.forEach { add("pre_conf_yn" to it) }
        }
        return PortalWrite.send(context, "/main/classroom/apply-insert.do", params, APPLY_PAGE, "신청 중 오류가 발생했습니다")
    }

    suspend fun cancel(context: Context, crtIdx: String): String =
        PortalWrite.send(context, "/main/classroom/apply-cancel.do", listOf("crt_idx" to crtIdx), APPLY_PAGE, "취소 중 오류가 발생했습니다")
}

// MARK: - 외출외박 신청 API

/**
 * 외출외박 신청 — 포털 apply_inscr.do 의 폼.
 *   화면  GET  /main/outing/apply_inscr.do (JSON) → 안내문(outingSettingVo.out_cont), 승인 교사(outingMngVo)
 *         GET  /main/outing/apply_inscr.do (HTML) → 숨은 필드 acc_tc_mem_no (폼이 그대로 보내는 승인 교사 번호)
 *   신청  POST /main/outing/apply_inproc.json  dep_exp_dt, ret_exp_dt("yyyy-MM-dd HH:mm"), out_rsn, trans_mtd, trans_mtd_radio, acc_tc_mem_no
 * 사유는 포털처럼 한글·영문·숫자·공백만 받습니다. 신청 기한(평일 오전 9시 · 휴일은 전날 오전 9시)은 서버가 최종 판정합니다.
 */
data class OutingFormInfo(val notice: String?, val approverName: String?, val approverNo: String)

enum class OutingTransport(val value: String) { CAR("자가용"), PUBLIC("대중교통"), SCHOOL("학교(교사)차량"), OTHER("기타") }

object OutingApplyApi {
    private const val PAGE = "/main/outing/apply_inscr.do"
    private const val HISTORY = "/main/outing/history.do"
    private val reasonFilter = Regex("[^가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9\\s]")

    fun cleanReason(text: String): String = text.replace(reasonFilter, "")

    suspend fun form(context: Context): OutingFormInfo = withContext(Dispatchers.IO) {
        val client = HanaPortalClient.get()
        val json = runCatching { JSONObject(client.authenticatedText(context, PAGE)) }.getOrNull()
        val html = runCatching { client.authenticatedHtml(context, PAGE) }.getOrNull().orEmpty()
        val hidden = Regex("""name=["']acc_tc_mem_no["'][^>]*value=["'](\d+)["']""").find(html)?.groupValues?.get(1)
            ?: Regex("""value=["'](\d+)["'][^>]*name=["']acc_tc_mem_no["']""").find(html)?.groupValues?.get(1)
        val manager = json?.optJSONObject("outingMngVo")
        val approverNo = hidden ?: manager?.text("app_tc_no")
            ?: throw IllegalStateException("승인 교사를 찾지 못했습니다 — 포털에서 신청해 주세요")
        OutingFormInfo(
            notice = json?.optJSONObject("outingSettingVo")?.text("out_cont")?.replace("\r\n", "\n"),
            approverName = manager?.text("app_tc_name"),
            approverNo = approverNo,
        )
    }

    suspend fun apply(
        context: Context,
        form: OutingFormInfo,
        out: LocalDateTime,
        back: LocalDateTime,
        reason: String,
        transport: OutingTransport,
        otherTransport: String,
    ): String {
        fun stamp(t: LocalDateTime) = "%s %02d:%02d".format(t.toLocalDate(), t.hour, t.minute)
        val params = listOf(
            "dep_exp_dt" to stamp(out),
            "ret_exp_dt" to stamp(back),
            "out_rsn" to cleanReason(reason).trim(),
            "trans_mtd" to if (transport == OutingTransport.OTHER) otherTransport.trim() else transport.value,
            "trans_mtd_radio" to transport.value,
            "acc_tc_mem_no" to form.approverNo,
        )
        return PortalWrite.send(context, "/main/outing/apply_inproc.json", params, PAGE, "신청하지 못했습니다")
    }

    /**
     * 수정 — 포털 내역의 "상세/수정" 창과 같은 값. 학생은 신청(대기) 상태이고 단체 신청이 아닌 건만 고칠 수 있습니다.
     * POST /main/outing/apply_upproc.json  osm_no, dep_exp_dt, ret_exp_dt, out_rsn, trans_mtd
     */
    suspend fun update(
        context: Context,
        osmNo: Long,
        out: LocalDateTime,
        back: LocalDateTime,
        reason: String,
        transport: OutingTransport,
        otherTransport: String,
    ): String {
        fun stamp(t: LocalDateTime) = "%s %02d:%02d".format(t.toLocalDate(), t.hour, t.minute)
        val params = listOf(
            "osm_no" to osmNo.toString(),
            "dep_exp_dt" to stamp(out),
            "ret_exp_dt" to stamp(back),
            "out_rsn" to cleanReason(reason).trim(),
            "trans_mtd" to if (transport == OutingTransport.OTHER) otherTransport.trim() else transport.value,
        )
        return PortalWrite.send(context, "/main/outing/apply_upproc.json", params, HISTORY, "수정하지 못했습니다")
    }

    /** 신청 취소 — POST /main/outing/outing_apply_cancel.json  osm_no, std_mem_no. 수정과 같은 조건(신청 상태 · 개인 신청). */
    suspend fun cancel(context: Context, osmNo: Long, stdMemNo: String): String =
        PortalWrite.send(
            context, "/main/outing/outing_apply_cancel.json",
            listOf("osm_no" to osmNo.toString(), "std_mem_no" to stdMemNo), HISTORY, "취소하지 못했습니다",
        )

    /**
     * 출입 QR — 승인된 건만. 내용은 "CODE:OUTING_{신청번호}_{학생번호}" 이고, 포털이 만든 그림(/common/qrcode.do)을
     * 처음 한 번 받아 기기에 저장해 둡니다. 교문 앞에서 네트워크가 없어도 저장된 그림을 보여 줍니다.
     */
    suspend fun qr(context: Context, osmNo: Long, stdMemNo: String): android.graphics.Bitmap = withContext(Dispatchers.IO) {
        val dir = java.io.File(context.filesDir, "outing_qr").apply { mkdirs() }
        val file = java.io.File(dir, "${osmNo}_$stdMemNo.png")
        val cached = file.takeIf { it.length() > 0 }?.readBytes()
        val bytes = cached ?: HanaPortalClient.get().authenticatedBytes(
            context, "/common/qrcode.do",
            listOf("qrContent" to "CODE:OUTING_${osmNo}_$stdMemNo", "wd" to "500", "hd" to "500"),
            referer = PORTAL + HISTORY,
        )
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("QR 그림을 읽지 못했습니다")
        if (cached == null) runCatching { file.writeBytes(bytes) }
        bitmap
    }

    /** 포털 안내의 신청 기한 — 평일 출발은 그날 오전 9시, 휴일 출발은 전날 오전 9시. */
    fun deadline(out: LocalDate): LocalDateTime =
        if (Timetable.isRestDay(out)) out.minusDays(1).atTime(9, 0) else out.atTime(9, 0)
}

// MARK: - 공용 조각

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = OneUi.PagePadding),
    )
    Spacer(Modifier.height(6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun PortalButton(onClick: () -> Unit) {
    OneUiActionButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "포털에서 열기")
    }
}

@Composable
private fun InlineLoading(text: String) {
    Row(Modifier.padding(horizontal = OneUi.PagePadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OneUiLoading(size = 16.dp, stroke = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = OneUi.PagePadding, vertical = 6.dp),
    )
}

/** 보내기 전 확인 — 요약을 보여 주고 "신청"을 눌러야만 보냅니다. 보내는 동안엔 버튼이 스피너가 되고 닫히지 않습니다. */
@Composable
internal fun ConfirmSendDialog(
    title: String,
    lines: List<Pair<String, String>>,
    confirmText: String,
    busy: Boolean,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OneUiDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = title,
        buttons = listOf(
            OneUiDialogButton("닫기", onDismiss, enabled = !busy),
            OneUiDialogButton(confirmText, onConfirm, enabled = !busy, destructive = destructive, busyText = if (busy) "보내는 중" else null),
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { (label, value) ->
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// MARK: - 교과교실 신청 화면

/**
 * 교과교실 신청 — 건물 → 교실 → 타임(지난 타임은 숨김) → 사유, 교실에 따라 승인 교사와 주의사항 동의.
 * 아래에는 오늘 내 신청과 취소 버튼. 신청·취소는 확인 창에서 한 번 더 눌러야 보내고, 결과는 오늘 내 신청을 다시 받아 보여 줍니다.
 */
@Composable
fun ClassroomApplyScreen(onDismiss: () -> Unit, onOpenPortal: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { PlanStore.today() }

    var buildings by remember { mutableStateOf<List<ClassroomBuildingOption>>(emptyList()) }
    var building by remember { mutableStateOf<ClassroomBuildingOption?>(null) }
    var rooms by remember { mutableStateOf<List<ClassroomRoomOption>?>(null) }
    var room by remember { mutableStateOf<ClassroomRoomOption?>(null) }
    var detail by remember { mutableStateOf<ClassroomRoomDetail?>(null) }
    var slots by remember { mutableStateOf<List<ClassroomSlotOption>?>(null) }
    var slot by remember { mutableStateOf<ClassroomSlotOption?>(null) }
    var teacher by remember { mutableStateOf<ClassroomTeacher?>(null) }
    var agreed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf<List<MyClassroomApply>?>(null) }
    var mineError by remember { mutableStateOf<String?>(null) }
    var mineReload by remember { mutableStateOf(0) }
    var confirming by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf<MyClassroomApply?>(null) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        error = errorText(e)
        null
    }

    LaunchedEffect(Unit) {
        val list = guarded { ClassroomApplyApi.buildings(context) }.orEmpty()
        buildings = list
        building = list.firstOrNull()
    }
    LaunchedEffect(building) {
        val b = building ?: return@LaunchedEffect
        rooms = null
        room = null
        error = null
        rooms = guarded { ClassroomApplyApi.rooms(context, b.num) }.orEmpty()
    }
    LaunchedEffect(room) {
        detail = null
        slots = null
        slot = null
        teacher = null
        agreed = emptySet()
        val r = room ?: return@LaunchedEffect
        error = null
        val d = guarded { ClassroomApplyApi.detail(context, r.idx) } ?: return@LaunchedEffect
        detail = d
        teacher = d.teachers.firstOrNull()
        val now = LocalTime.now(PlanStore.seoulZone)
        // 포털처럼 이미 시작한 타임은 고를 수 없습니다 — 아예 빼고, 남은 첫 타임을 고릅니다.
        val open = guarded { ClassroomApplyApi.slots(context, r.idx) }.orEmpty().filter { it.start == null || it.start.isAfter(now) }
        slots = open
        slot = open.firstOrNull()
    }
    LaunchedEffect(mineReload) {
        mineError = null
        mine = try {
            ClassroomApplyApi.mine(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mineError = errorText(e)
            null
        }
    }

    val d = detail
    val ready = building != null && room != null && d != null && slot != null &&
        (!d.needsTeacher || teacher != null) && (!d.needsAgreement || agreed.size == d.checks.size)

    OneUiFullScreen(
        title = "교과교실 신청",
        subtitle = "오늘 · ${today.monthValue}월 ${today.dayOfMonth}일 (${today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})",
        onDismiss = onDismiss,
        actions = { PortalButton(onOpenPortal) },
    ) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp),
        ) {
            SectionLabel("건물")
            if (buildings.isEmpty() && error == null) InlineLoading("건물을 불러오는 중")
            ChipFlow { buildings.forEach { b -> OneUiChip(selected = b == building, onClick = { building = b }, label = b.name) } }

            if (building != null) {
                SectionLabel("교실")
                val list = rooms
                when {
                    list == null -> InlineLoading("교실을 불러오는 중")
                    list.isEmpty() -> ErrorLine("이 건물에 신청할 수 있는 교실이 없습니다")
                    else -> ChipFlow { list.forEach { r -> OneUiChip(selected = r == room, onClick = { room = r }, label = r.name) } }
                }
            }

            if (room != null) {
                if (d == null) {
                    InlineLoading("교실 정보를 불러오는 중")
                } else {
                    Row(Modifier.padding(horizontal = OneUi.PagePadding), verticalAlignment = Alignment.CenterVertically) {
                        OneUiBadge(
                            d.approvalLabel ?: room?.approvalLabel ?: "승인 방식",
                            container = if (d.approval == ClassroomRoomDetail.CA_AUTO) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            content = if (d.approval == ClassroomRoomDetail.CA_AUTO) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    SectionLabel("타임")
                    val open = slots
                    when {
                        open == null -> InlineLoading("타임을 불러오는 중")
                        open.isEmpty() -> ErrorLine("오늘 남은 타임이 없습니다")
                        else -> ChipFlow { open.forEach { s -> OneUiChip(selected = s == slot, onClick = { slot = s }, label = s.label) } }
                    }

                    if (d.needsTeacher) {
                        SectionLabel("승인 교사")
                        if (d.teachers.isEmpty()) ErrorLine("승인 교사가 없습니다 — 포털에서 신청해 주세요")
                        else ChipFlow { d.teachers.forEach { t -> OneUiChip(selected = t == teacher, onClick = { teacher = t }, label = t.name) } }
                    }

                    if (d.needsAgreement) {
                        OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
                            Text("주의사항", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            d.notice?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            d.procedure?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (d.checks.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                OneUiDivider(startIndent = 0.dp)
                                d.checks.forEach { (id, text) ->
                                    val on = id in agreed
                                    Row(
                                        Modifier.fillMaxWidth().clickable { agreed = if (on) agreed - id else agreed + id }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = on,
                                            onCheckedChange = { agreed = if (it) agreed + id else agreed - id },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                                        )
                                        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    OneUiTextField(
                        value = reason,
                        onValueChange = { reason = it.take(200) },
                        label = "사유",
                        singleLine = false,
                        modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    OneUiButton(
                        text = "신청하기",
                        onClick = { confirming = true },
                        enabled = ready && !sending,
                        modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
                    )
                }
            }
            error?.let { ErrorLine(it) }

            Spacer(Modifier.height(24.dp))
            SectionLabel("오늘 내 신청")
            val list = mine
            when {
                mineError != null -> ErrorLine(mineError!!)
                list == null -> InlineLoading("불러오는 중")
                list.isEmpty() -> Text(
                    "오늘 신청한 교과교실이 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                )
                else -> OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
                    list.forEachIndexed { i, a ->
                        if (i > 0) OneUiDivider(startIndent = 0.dp)
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${a.room} · ${a.slot}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(
                                        a.start?.let { s -> "%02d:%02d".format(s.hour, s.minute) + (a.end?.let { e -> "–%02d:%02d".format(e.hour, e.minute) } ?: "") },
                                        a.status,
                                        a.reason,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (a.cancellable) {
                                OneUiButton(text = "취소", onClick = { cancelling = a }, style = OneUiButtonStyle.Neutral, compact = true, enabled = !sending)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirming && ready) {
        val s = slot!!
        ConfirmSendDialog(
            title = "교과교실을 신청할까요?",
            lines = listOfNotNull(
                "교실" to "${building!!.name} ${room!!.name}",
                "타임" to (s.label + (s.end?.let { "–%02d:%02d".format(it.hour, it.minute) } ?: "")),
                if (d!!.needsTeacher) teacher?.let { "승인 교사" to it.name } else null,
                "사유" to reason.ifBlank { "없음" },
            ),
            confirmText = "신청",
            busy = sending,
            onConfirm = {
                // 확인 버튼을 빠르게 두 번 눌러도 한 번만 보냅니다.
                if (sending) return@ConfirmSendDialog
                sending = true
                scope.launch {
                    result = try {
                        "신청했습니다" to ClassroomApplyApi.apply(
                            context, building!!, room!!, d, s, reason.trim(), teacher, d.checks.map { it.first }.filter { it in agreed },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "신청하지 못했습니다" to errorText(e)
                    }
                    sending = false
                    confirming = false
                    mineReload++
                }
            },
            onDismiss = { confirming = false },
        )
    }
    cancelling?.let { a ->
        ConfirmSendDialog(
            title = "신청을 취소할까요?",
            lines = listOf("교실" to a.room, "타임" to a.slot),
            confirmText = "신청 취소",
            busy = sending,
            destructive = true,
            onConfirm = {
                // 확인 버튼을 빠르게 두 번 눌러도 한 번만 보냅니다.
                if (sending) return@ConfirmSendDialog
                sending = true
                scope.launch {
                    result = try {
                        "취소했습니다" to ClassroomApplyApi.cancel(context, a.crtIdx)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "취소하지 못했습니다" to errorText(e)
                    }
                    sending = false
                    cancelling = null
                    mineReload++
                }
            },
            onDismiss = { cancelling = null },
        )
    }
    result?.let { (title, message) -> OneUiAlertDialog(onDismissRequest = { result = null }, title = title, message = message) }
}

// MARK: - 외출외박 신청 화면

private fun LocalDate.pickLabel(today: LocalDate): String {
    val day = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    val prefix = when (this) {
        today -> "오늘 · "
        today.plusDays(1) -> "내일 · "
        else -> ""
    }
    return prefix + (if (year != today.year) "${year}년 " else "") + "${monthValue}월 ${dayOfMonth}일 ($day)"
}

/** 출발·복귀 한 줄 — 날짜 버튼과 시각 버튼. 누르면 리퀴드 글래스 휠이 뜹니다. */
@Composable
private fun DateTimeRow(label: String, date: LocalDate?, time: LocalTime?, today: LocalDate, onDate: () -> Unit, onTime: () -> Unit) {
    SectionLabel(label)
    Row(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OneUiButton(
            text = date?.pickLabel(today) ?: "날짜 고르기",
            onClick = onDate,
            style = OneUiButtonStyle.Neutral,
            modifier = Modifier.weight(1.6f),
        )
        OneUiButton(
            text = time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "시각",
            onClick = onTime,
            style = OneUiButtonStyle.Neutral,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(16.dp))
}

/** 수정할 신청 — 내역 카드에서 넘겨받아 양식을 채웁니다. */
data class OutingEdit(
    val osmNo: Long,
    val out: LocalDateTime,
    val back: LocalDateTime,
    val reason: String,
    val transport: String?,
)

/**
 * 외출·외박 신청 — 출발·복귀 날짜와 시각(리퀴드 글래스 휠), 사유, 이동방법. 기간과 신청 기한을 미리 보여 주고, 확인 창에서
 * "신청"을 눌러야 보냅니다. 승인은 포털이 정한 담임 선생님(방학엔 학년부장)에게 갑니다.
 * [edit] 를 주면 같은 양식으로 그 신청을 고칩니다(포털 내역의 "상세/수정").
 */
@Composable
fun OutingApplyScreen(
    onDismiss: () -> Unit,
    onOpenPortal: () -> Unit,
    onOpenHistory: () -> Unit,
    edit: OutingEdit? = null,
    onEdited: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { PlanStore.today() }
    // 포털 달력에는 날짜 제한이 없습니다 — 지난 날은 막고(고치는 신청의 원래 날은 허용), 1년 뒤까지.
    val minDate = remember(edit) { listOfNotNull(today, edit?.out?.toLocalDate()).min() }
    val maxDate = remember(today) { today.plusYears(1) }

    var form by remember { mutableStateOf<OutingFormInfo?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var outDate by remember { mutableStateOf<LocalDate?>(edit?.out?.toLocalDate() ?: today) }
    var outTime by remember { mutableStateOf<LocalTime?>(edit?.out?.toLocalTime()) }
    var backDate by remember { mutableStateOf<LocalDate?>(edit?.back?.toLocalDate()) }
    var backTime by remember { mutableStateOf<LocalTime?>(edit?.back?.toLocalTime()) }
    var picking by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf(edit?.reason?.let { OutingApplyApi.cleanReason(it) } ?: "") }
    val editTransport = edit?.transport
    val knownTransport = editTransport?.let { t -> OutingTransport.entries.firstOrNull { it != OutingTransport.OTHER && it.value == t } }
    var transport by remember { mutableStateOf(if (editTransport == null) null else knownTransport ?: OutingTransport.OTHER) }
    var otherTransport by remember { mutableStateOf(if (editTransport != null && knownTransport == null) editTransport else "") }
    var confirming by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<String, String>?>(null) }
    var succeeded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        form = try {
            OutingApplyApi.form(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            formError = errorText(e)
            null
        }
    }

    val out = if (outDate != null && outTime != null) outDate!!.atTime(outTime) else null
    val back = if (backDate != null && backTime != null) backDate!!.atTime(backTime) else null
    val orderError = if (out != null && back != null && !back.isAfter(out)) "복귀 일시는 출발 일시 이후여야 합니다" else null
    val cleanReason = OutingApplyApi.cleanReason(reason).trim()
    val transportText = if (transport == OutingTransport.OTHER) otherTransport.trim() else transport?.value
    // 수정은 승인 교사 번호가 필요 없어 양식(안내문)을 못 받아도 보낼 수 있습니다.
    val ready = (form != null || edit != null) && out != null && back != null && orderError == null && cleanReason.isNotEmpty() &&
        !transportText.isNullOrEmpty() && transportText.length <= 200

    fun durationText(from: LocalDateTime, to: LocalDateTime): String {
        val minutes = Duration.between(from, to).toMinutes()
        val days = minutes / (60 * 24)
        val hours = (minutes % (60 * 24)) / 60
        val mins = minutes % 60
        return buildString {
            if (days > 0) append("${days}일 ")
            if (hours > 0) append("${hours}시간 ")
            if (mins > 0 || isEmpty()) append("${mins}분")
        }.trim()
    }

    OneUiFullScreen(
        title = if (edit != null) "외출·외박 수정" else "외출·외박 신청",
        subtitle = form?.approverName?.let { "승인 · $it 선생님" },
        onDismiss = onDismiss,
        actions = { PortalButton(onOpenPortal) },
    ) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp),
        ) {
            when {
                formError != null -> if (edit == null) ErrorLine(formError!!)
                form == null -> InlineLoading("신청 양식을 불러오는 중")
                else -> form?.notice?.let { notice ->
                    OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
                        Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            DateTimeRow("출발", outDate, outTime, today, onDate = { picking = "outDate" }, onTime = { picking = "outTime" })
            DateTimeRow("복귀", backDate, backTime, today, onDate = { picking = "backDate" }, onTime = { picking = "backTime" })

            // 기간·기한 — 포털이 최종 판정하지만, 보내기 전에 미리 알려 줍니다.
            if (out != null && back != null) {
                Text(
                    orderError ?: "기간 ${durationText(out, back)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (orderError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                )
            }
            outDate?.let { d ->
                val deadline = OutingApplyApi.deadline(d)
                val late = LocalDateTime.now(PlanStore.seoulZone).isAfter(deadline)
                Text(
                    "신청 기한 ${deadline.monthValue}/${deadline.dayOfMonth}(${deadline.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)}) 09:00" +
                        if (late) " · 지났습니다, 서버가 거절할 수 있어요" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            OneUiTextField(
                value = reason,
                // 포털처럼 한글·영문·숫자·공백만 — 다른 글자는 입력되지 않습니다.
                onValueChange = { reason = OutingApplyApi.cleanReason(it).take(300) },
                label = "사유",
                singleLine = false,
                modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            SectionLabel("이동방법")
            ChipFlow {
                OutingTransport.entries.forEach { t -> OneUiChip(selected = t == transport, onClick = { transport = t }, label = t.value) }
            }
            if (transport == OutingTransport.OTHER) {
                OneUiTextField(
                    value = otherTransport,
                    onValueChange = { otherTransport = it.take(200) },
                    label = "이동방법 (200자 이내)",
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }

            OneUiButton(
                text = if (edit != null) "수정하기" else "신청하기",
                onClick = { confirming = true },
                enabled = ready && !sending,
                modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
            )
            if (succeeded && edit == null) {
                Spacer(Modifier.height(10.dp))
                OneUiButton(
                    text = "외출·외박 내역 보기",
                    onClick = onOpenHistory,
                    style = OneUiButtonStyle.Neutral,
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
                )
            }
        }
    }

    when (picking) {
        "outDate" -> GlassDatePickerDialog("출발 날짜", outDate ?: today, minDate, maxDate, { d ->
            outDate = d
            // 복귀 날이 출발 날보다 앞이 되면 출발 날로 당겨 둡니다.
            if (backDate != null && backDate!!.isBefore(d)) backDate = d
            picking = null
        }, { picking = null })
        "outTime" -> GlassTimePickerDialog("출발 시각", outTime ?: LocalTime.of(LocalTime.now(PlanStore.seoulZone).hour, 0), { outTime = it; picking = null }, { picking = null })
        "backDate" -> GlassDatePickerDialog("복귀 날짜", backDate ?: outDate ?: today, outDate ?: minDate, maxDate, { backDate = it; picking = null }, { picking = null })
        "backTime" -> GlassTimePickerDialog("복귀 시각", backTime ?: outTime ?: LocalTime.of(20, 0), { backTime = it; picking = null }, { picking = null })
    }

    if (confirming && ready) {
        fun label(t: LocalDateTime) = "%d/%d(%s) %02d:%02d".format(
            t.monthValue, t.dayOfMonth, t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN), t.hour, t.minute,
        )
        ConfirmSendDialog(
            title = if (edit != null) "이대로 수정할까요?" else "외출·외박을 신청할까요?",
            lines = listOfNotNull(
                "출발" to label(out!!),
                "복귀" to label(back!!),
                "기간" to durationText(out, back),
                "사유" to cleanReason,
                "이동방법" to transportText!!,
                form?.approverName?.let { "승인" to "$it 선생님" },
            ),
            confirmText = if (edit != null) "수정" else "신청",
            busy = sending,
            onConfirm = {
                // 확인 버튼을 빠르게 두 번 눌러도 한 번만 보냅니다.
                if (sending) return@ConfirmSendDialog
                sending = true
                scope.launch {
                    result = try {
                        val message = if (edit != null) {
                            OutingApplyApi.update(context, edit.osmNo, out, back, cleanReason, transport!!, otherTransport)
                        } else {
                            OutingApplyApi.apply(context, form!!, out, back, cleanReason, transport!!, otherTransport)
                        }
                        succeeded = true
                        (if (edit != null) "수정했습니다" else "신청했습니다") to message
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "신청하지 못했습니다" to errorText(e)
                    }
                    sending = false
                    confirming = false
                }
            },
            onDismiss = { confirming = false },
        )
    }
    result?.let { (title, message) ->
        OneUiAlertDialog(
            onDismissRequest = {
                result = null
                if (edit != null && succeeded) onEdited()
            },
            title = title,
            message = message,
        )
    }
}
