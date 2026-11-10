package com.skh.hihanaevo

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.GlassDatePickerDialog
import com.skh.hihanaevo.ui.GlassTimePickerDialog
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiButton
import com.skh.hihanaevo.ui.OneUiButtonStyle
import com.skh.hihanaevo.ui.OneUiChip
import com.skh.hihanaevo.ui.OneUiDialog
import com.skh.hihanaevo.ui.OneUiDialogButton
import com.skh.hihanaevo.ui.OneUiDivider
import com.skh.hihanaevo.ui.OneUiFormCard
import com.skh.hihanaevo.ui.OneUiFullScreen
import com.skh.hihanaevo.ui.OneUiListItem
import com.skh.hihanaevo.ui.OneUiSwitch
import com.skh.hihanaevo.ui.OneUiTextField
import com.skh.hihanaevo.ui.onPageVariantColor
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * 내 일정 — 학생이 직접 적어 넣은 일정. 학사일정·게시글·클래스룸 일정과 똑같이 [AgendaItem]([AgendaSource.MINE])으로 바꿔
 * 홈 '오늘 일정', 타임라인, 학사일정 목록, 통합 검색, 데일리 브리핑에 함께 들어갑니다. 이 기기에만 저장합니다(기기 백업에는 포함).
 */
data class UserEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    /** 여러 날 일정의 마지막 날 — 하루짜리면 null. */
    val endDate: LocalDate? = null,
    /** 시작 시각 — 하루 종일이면 null. */
    val start: LocalTime? = null,
    /** 끝 시각 — 없어도 됩니다. */
    val end: LocalTime? = null,
    /** 신청·제출 기한(마감)인지 — 홈의 '마감' 숫자, 빨간 표시. */
    val deadline: Boolean = false,
    val memo: String? = null,
) {
    /** 통합 일정 한 줄로 — 시각은 다른 일정과 같은 "13:00" / "13:00~14:30" 모양(타임라인은 앞 시각으로 자리를 잡습니다). */
    fun toAgenda(): AgendaItem = AgendaItem(
        id = ID_PREFIX + id,
        source = AgendaSource.MINE,
        title = title,
        date = date,
        endDate = endDate?.takeIf { it.isAfter(date) },
        time = start?.let { s -> hhmm(s) + (end?.let { "~" + hhmm(it) } ?: "") },
        deadline = deadline,
        detail = memo?.takeIf { it.isNotBlank() },
    )

    internal fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("date", date.toString())
        .put("end_date", endDate?.toString() ?: JSONObject.NULL)
        .put("start", start?.let(::hhmm) ?: JSONObject.NULL)
        .put("end", end?.let(::hhmm) ?: JSONObject.NULL)
        .put("deadline", deadline)
        .put("memo", memo ?: JSONObject.NULL)

    companion object {
        const val ID_PREFIX = "u:"

        private fun hhmm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

        internal fun fromJson(id: String, o: JSONObject): UserEvent? = runCatching {
            fun str(key: String) = o.optString(key).takeIf { it.isNotEmpty() && it != "null" }
            UserEvent(
                id = id,
                title = o.getString("title"),
                date = LocalDate.parse(o.getString("date")),
                endDate = str("end_date")?.let(LocalDate::parse),
                start = str("start")?.let(LocalTime::parse),
                end = str("end")?.let(LocalTime::parse),
                deadline = o.optBoolean("deadline"),
                memo = str("memo"),
            )
        }.getOrNull()
    }
}

/** 내 일정 보관함 — SharedPreferences 에 일정마다 한 키("e_<id>")로 JSON 을 둡니다. [revision] 이 바뀌면 화면들이 다시 읽습니다. */
object UserAgendaStore {
    private const val PREFS = "user_agenda_v1"
    private const val KEY = "e_"

    val revision = mutableIntStateOf(0)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun events(context: Context): List<UserEvent> = prefs(context).all.mapNotNull { (key, value) ->
        if (!key.startsWith(KEY)) return@mapNotNull null
        val json = runCatching { JSONObject(value as String) }.getOrNull() ?: return@mapNotNull null
        UserEvent.fromJson(key.removePrefix(KEY), json)
    }.sortedWith(compareBy({ it.date }, { it.start }))

    /** 통합 일정에 섞을 모양 — 지난 일정 거르기는 [Agenda.merge] 가 합니다. */
    fun items(context: Context): List<AgendaItem> = events(context).map { it.toAgenda() }

    fun find(context: Context, agendaId: String): UserEvent? {
        if (!agendaId.startsWith(UserEvent.ID_PREFIX)) return null
        val id = agendaId.removePrefix(UserEvent.ID_PREFIX)
        val raw = prefs(context).getString(KEY + id, null) ?: return null
        return runCatching { UserEvent.fromJson(id, JSONObject(raw)) }.getOrNull()
    }

    fun save(context: Context, event: UserEvent) {
        prefs(context).edit().putString(KEY + event.id, event.toJson().toString()).apply()
        revision.intValue++
    }

    fun delete(context: Context, id: String) {
        prefs(context).edit().remove(KEY + id).apply()
        revision.intValue++
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)
}

/**
 * 일정 추가·수정 — 제목, 일정/마감, 날짜(여러 날이면 끝 날), 시각(하루 종일이 아니면 시작·끝), 메모.
 * [edit] 를 주면 그 일정을 고치고 지울 수 있습니다. [initialDate] 는 새 일정의 날(타임라인에서 보던 날 등).
 */
@Composable
fun UserEventScreen(
    edit: UserEvent?,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val today = remember { PlanStore.today() }
    val minDate = remember(edit) { listOfNotNull(today.minusYears(1), edit?.date).min() }
    val maxDate = remember(today) { today.plusYears(2) }

    var title by remember { mutableStateOf(edit?.title ?: "") }
    var deadline by remember { mutableStateOf(edit?.deadline ?: false) }
    var date by remember { mutableStateOf(edit?.date ?: initialDate) }
    var multiDay by remember { mutableStateOf(edit?.endDate != null) }
    var endDate by remember { mutableStateOf(edit?.endDate ?: (edit?.date ?: initialDate).plusDays(1)) }
    var allDay by remember { mutableStateOf(edit?.start == null) }
    var start by remember { mutableStateOf(edit?.start ?: LocalTime.of(LocalTime.now(PlanStore.seoulZone).hour.coerceIn(7, 22) + 1, 0)) }
    var end by remember { mutableStateOf(edit?.end) }
    var memo by remember { mutableStateOf(edit?.memo ?: "") }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val cleanTitle = title.trim()
    val orderError = when {
        multiDay && !endDate.isAfter(date) -> "끝나는 날은 시작하는 날 다음이어야 합니다"
        !allDay && end != null && !end!!.isAfter(start) && !multiDay -> "끝 시각은 시작 시각 뒤여야 합니다"
        else -> null
    }
    val ready = cleanTitle.isNotEmpty() && orderError == null

    fun save() {
        if (!ready) return
        UserAgendaStore.save(
            context,
            UserEvent(
                id = edit?.id ?: UserAgendaStore.newId(),
                title = cleanTitle.take(80),
                date = date,
                endDate = if (multiDay) endDate else null,
                start = if (allDay) null else start,
                end = if (allDay) null else end,
                deadline = deadline,
                memo = memo.trim().take(300).ifEmpty { null },
            ),
        )
        onDismiss()
    }

    OneUiFullScreen(
        title = if (edit != null) "일정 수정" else "일정 추가",
        onDismiss = onDismiss,
    ) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp),
        ) {
            OneUiFormCard {
                OneUiTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = "제목",
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                FieldLabel("종류")
                Row(Modifier.padding(horizontal = OneUi.PagePadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OneUiChip(selected = !deadline, onClick = { deadline = false }, label = "일정")
                    OneUiChip(selected = deadline, onClick = { deadline = true }, label = "마감")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (deadline) "신청·제출 기한 — 홈 '오늘 일정'의 마감 숫자에 들어가고 빨갛게 표시됩니다."
                    else "행사·약속 같은 일정 — 홈·타임라인·학사일정 목록·데일리 브리핑에 함께 나옵니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onPageVariantColor(),
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                )
            }
            Spacer(Modifier.height(14.dp))

            OneUiFormCard {
                OneUiListItem(
                    title = if (multiDay) "시작하는 날" else "날짜",
                    subtitle = date.label(today),
                    onClick = { picking = "date" },
                )
                OneUiDivider()
                OneUiListItem(
                    title = "여러 날",
                    trailing = { OneUiSwitch(checked = multiDay, onCheckedChange = { on -> multiDay = on; if (on && !endDate.isAfter(date)) endDate = date.plusDays(1) }) },
                    onClick = { multiDay = !multiDay; if (multiDay && !endDate.isAfter(date)) endDate = date.plusDays(1) },
                )
                if (multiDay) {
                    OneUiDivider()
                    OneUiListItem(title = "끝나는 날", subtitle = endDate.label(today), onClick = { picking = "endDate" })
                }
                OneUiDivider()
                OneUiListItem(
                    title = "하루 종일",
                    trailing = { OneUiSwitch(checked = allDay, onCheckedChange = { allDay = it }) },
                    onClick = { allDay = !allDay },
                )
                if (!allDay) {
                    OneUiDivider()
                    OneUiListItem(title = "시작 시각", subtitle = start.label(), onClick = { picking = "start" })
                    OneUiDivider()
                    OneUiListItem(
                        title = "끝 시각",
                        subtitle = end?.label() ?: "정하지 않음",
                        onClick = { picking = "end" },
                        trailing = if (end != null) {
                            { com.skh.hihanaevo.ui.OneUiTextButton(text = "지우기", onClick = { end = null }, color = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    )
                }
            }
            orderError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding + 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))

            OneUiFormCard {
                OneUiTextField(
                    value = memo,
                    onValueChange = { memo = it.take(300) },
                    label = "메모 (장소·준비물 등)",
                    singleLine = false,
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))

            OneUiButton(
                text = if (edit != null) "저장" else "추가",
                onClick = { save() },
                enabled = ready,
                modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
            )
            if (edit != null) {
                Spacer(Modifier.height(10.dp))
                OneUiButton(
                    text = "일정 삭제",
                    onClick = { confirmDelete = true },
                    style = OneUiButtonStyle.Neutral,
                    modifier = Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "내 일정은 이 기기에만 저장됩니다. 데일리 브리핑에서 AI 요약을 켜 두었다면 제목·메모가 브리핑을 만드는 서버로 함께 보내집니다.",
                style = MaterialTheme.typography.bodySmall,
                color = onPageVariantColor(),
                modifier = Modifier.padding(horizontal = OneUi.PagePadding + 6.dp),
            )
        }
    }

    when (picking) {
        "date" -> GlassDatePickerDialog(if (multiDay) "시작하는 날" else "날짜", date, minDate, maxDate, { d ->
            date = d
            // 끝나는 날이 시작하는 날보다 앞이 되면 다음 날로 밀어 둡니다.
            if (!endDate.isAfter(d)) endDate = d.plusDays(1)
            picking = null
        }, { picking = null })
        "endDate" -> GlassDatePickerDialog("끝나는 날", endDate, date.plusDays(1), maxDate, { endDate = it; picking = null }, { picking = null })
        "start" -> GlassTimePickerDialog("시작 시각", start, { t ->
            // 끝 시각을 정해 두었다면 같은 길이만큼 함께 옮깁니다.
            end?.let { e -> end = t.plusMinutes(java.time.Duration.between(start, e).toMinutes().coerceAtLeast(0)) }
            start = t
            picking = null
        }, { picking = null })
        "end" -> GlassTimePickerDialog("끝 시각", end ?: start.plusHours(1), { end = it; picking = null }, { picking = null })
    }

    if (confirmDelete && edit != null) {
        OneUiDialog(
            onDismissRequest = { confirmDelete = false },
            title = "이 일정을 지울까요?",
            buttons = listOf(
                OneUiDialogButton("취소", { confirmDelete = false }),
                OneUiDialogButton("삭제", {
                    UserAgendaStore.delete(context, edit.id)
                    confirmDelete = false
                    onDismiss()
                }, destructive = true),
            ),
        ) {
            Text(edit.title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = onPageVariantColor(),
        modifier = Modifier.padding(horizontal = OneUi.PagePadding),
    )
    Spacer(Modifier.height(6.dp))
}

private fun LocalDate.label(today: LocalDate): String {
    val day = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    val prefix = when (this) {
        today -> "오늘 · "
        today.plusDays(1) -> "내일 · "
        else -> ""
    }
    return prefix + (if (year != today.year) "${year}년 " else "") + "${monthValue}월 ${dayOfMonth}일 ($day)"
}

private fun LocalTime.label(): String = "%02d:%02d".format(hour, minute)
