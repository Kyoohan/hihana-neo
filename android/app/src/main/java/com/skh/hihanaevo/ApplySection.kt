package com.skh.hihanaevo

import com.skh.hihanaevo.ui.onPageVariantColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.skh.hihanaevo.ui.isDark
import com.skh.hihanaevo.widget.WidgetKindColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.OneUiButton
import com.skh.hihanaevo.ui.OneUiCard
import com.skh.hihanaevo.ui.OneUiLoading
import com.skh.hihanaevo.ui.OneUiButtonStyle
import com.skh.hihanaevo.ui.OneUiDivider
import com.skh.hihanaevo.ui.OneUiGroupColumn
import kotlinx.coroutines.launch

internal const val PORTAL_BASE = "https://hi.hana.hs.kr"

// MARK: - 신청·내역

private sealed interface ApplyHistoryState {
    data object Idle : ApplyHistoryState
    data object Loading : ApplyHistoryState
    data class Rows(val rows: List<HanaApplyRow>) : ApplyHistoryState
    data class Error(val message: String) : ApplyHistoryState
}

/**
 * 신청·내역 — 서비스마다 "내역"과 "신청하기" 두 동작을 제공합니다.
 * 내역은 포털 목록 JSON 으로 앱 안에서 그리고(교과교실·면학실 확인됨), JSON 엔드포인트를 아직 모르는 서비스는
 * 조용히 포털 내역 페이지를 웹뷰로 엽니다. "신청하기"는 아직 모두 포털 신청 페이지를 웹뷰로 엽니다
 * (교과교실·외출외박은 계속 웹뷰, 면학실·도서관은 API 를 알아내면 앱 안 화면으로).
 */
/** 신청·내역 블록 순서 — 서비스 4개와 심야면학. 헤더의 연필 아이콘으로 바꾸고 기기에 저장합니다. */
internal object ApplyOrderStore {
    const val MIDNIGHT = "MIDNIGHT"
    private const val PREFS = "apply_order"
    private const val KEY = "order"
    val defaultOrder: List<String> = ApplyService.entries.map { it.name } + MIDNIGHT

    /** 저장된 순서 — 모르는 이름은 빼고, 나중에 생긴 블록은 끝에 붙입니다. */
    fun get(context: android.content.Context): List<String> {
        val saved = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY, null)?.split(',')?.filter { it in defaultOrder }.orEmpty()
        return saved.distinct() + defaultOrder.filterNot { it in saved }
    }

    fun set(context: android.content.Context, order: List<String>) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, order.joinToString(",")).apply()
    }

    fun label(key: String): String =
        if (key == MIDNIGHT) "심야면학" else ApplyService.entries.firstOrNull { it.name == key }?.label ?: key
}

@Composable
internal fun ApplyHistorySection(
    onOpenWeb: (url: String, title: String) -> Unit,
    onOpenSeats: (SeatService) -> Unit,
    /** 헤더의 연필 아이콘으로 켜는 순서 편집 — 블록 대신 위·아래 버튼이 달린 목록을 보여 줍니다. */
    editing: Boolean = false,
    /**
     * 큰 화면 두 칸 — 면학실·도서관·외출외박 내역을 전체 화면 대신 오른쪽 칸에 띄웁니다(null 이면 폰처럼 전체 화면).
     * [historyShown] 은 지금 오른쪽 칸에 떠 있는 서비스 — 그 카드의 '내역' 버튼을 강조합니다.
     */
    onShowHistory: ((ApplyService) -> Unit)? = null,
    historyShown: ApplyService? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<ApplyService, ApplyHistoryState>() }
    var order by remember { mutableStateOf(ApplyOrderStore.get(context)) }
    var showingClassroomStatus by remember { mutableStateOf(false) }
    if (showingClassroomStatus) ClassroomStatusScreen(onDismiss = { showingClassroomStatus = false })

    fun openHistory(service: ApplyService) {
        onOpenWeb("$PORTAL_BASE${service.historyPath}", "${service.label} 내역")
    }

    // 면학실·도서관·외출외박 내역은 앱 안 전체 화면 — 오른쪽 위 버튼으로 포털 내역 페이지도 열 수 있습니다.
    var historyScreen by remember { mutableStateOf<ApplyService?>(null) }
    when (val shown = historyScreen) {
        ApplyService.STUDY_ROOM, ApplyService.LIBRARY -> SeatHistoryScreen(
            place = if (shown == ApplyService.LIBRARY) SeatHistoryPlace.LIBRARY else SeatHistoryPlace.STUDY_ROOM,
            onDismiss = { historyScreen = null },
            onOpenPortal = { historyScreen = null; openHistory(shown) },
        )
        ApplyService.OUTING -> OutingHistoryScreen(
            onDismiss = { historyScreen = null },
            onOpenPortal = { historyScreen = null; openHistory(shown) },
        )
        else -> Unit
    }

    // 교과교실·외출외박 신청도 앱 안 화면 — 신청·취소는 확인 창에서 한 번 더 눌러야 보냅니다.
    var applyScreen by remember { mutableStateOf<ApplyService?>(null) }
    fun openApplyPortal(service: ApplyService) = onOpenWeb("$PORTAL_BASE${service.applyPath}", service.label)
    when (applyScreen) {
        ApplyService.CLASSROOM -> ClassroomApplyScreen(
            onDismiss = { applyScreen = null },
            onOpenPortal = { applyScreen = null; openApplyPortal(ApplyService.CLASSROOM) },
        )
        ApplyService.OUTING -> OutingApplyScreen(
            onDismiss = { applyScreen = null },
            onOpenPortal = { applyScreen = null; openApplyPortal(ApplyService.OUTING) },
            onOpenHistory = {
                applyScreen = null
                if (onShowHistory != null) onShowHistory(ApplyService.OUTING) else historyScreen = ApplyService.OUTING
            },
        )
        else -> Unit
    }

    fun loadHistory(service: ApplyService) {
        states[service] = ApplyHistoryState.Loading
        scope.launch {
            states[service] = try {
                when (val result = HanaApplyApi.fetchHistory(context, service)) {
                    is ClassroomHistory.Rows -> ApplyHistoryState.Rows(result.rows)
                    ClassroomHistory.Unsupported -> {
                        // JSON 엔드포인트를 아직 모르면 오류를 띄우는 대신 포털 내역 페이지로 넘깁니다.
                        openHistory(service)
                        ApplyHistoryState.Idle
                    }
                }
            } catch (e: HanaPortalException.MissingCredentials) {
                ApplyHistoryState.Error(ACADEMIC_NEEDS_LOGIN)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 화면을 떠났거나(다른 앱에 갔다 오기 등) 같은 작업이 새로 시작돼 취소된 것 — 오류가 아닙니다.
                throw e
            } catch (e: Exception) {
                ApplyHistoryState.Error(e.message ?: "내역을 불러오지 못했습니다")
            }
        }
    }

    // 심야면학 — 신청 화면, 내역(오늘 1·2타임 신청 + 불참)은 아래 '내역'에 펼쳐 보입니다.
    var showingMidnight by remember { mutableStateOf(false) }
    if (showingMidnight) MidnightStudyScreen(onDismiss = { showingMidnight = false; MidnightSchedule.ensureInstalled(context) })
    var midnightHistory by remember { mutableStateOf<MidnightHistoryState>(MidnightHistoryState.Idle) }
    // 아래 '내역'에서 펼쳐 둔 것 — 교과교실·심야면학은 화면을 따로 띄우지 않고 칩 아래 카드로(예전 카드 안 목록과 같은 내용).
    var openedInline by remember { mutableStateOf<String?>(null) }

    fun loadMidnightHistory() {
        if (!MidnightSessionStore.isLoggedIn(context)) {
            showingMidnight = true
            return
        }
        openedInline = ApplyOrderStore.MIDNIGHT
        midnightHistory = MidnightHistoryState.Loading
        scope.launch { midnightHistory = loadMidnightLines(context) }
    }

    // 오늘 타임별 자리·외출·심야면학 — 모두 기기에 이미 있는 값(포털에 새로 묻지 않음). 좌석 화면에서 신청하고 돌아오면 바뀌므로
    // 이 화면이 떠 있는 동안 3초마다 다시 읽습니다(기기 저장소만 읽어 가볍습니다).
    val today = PlanStore.today()
    var places by remember { mutableStateOf<Map<PlanSlot, StudyPlace?>>(emptyMap()) }
    LaunchedEffect(today) {
        while (true) {
            places = PlanStore.placesForToday(context, today)
            kotlinx.coroutines.delay(3_000)
        }
    }
    val outing = remember(OutingSchedule.revision) { Timetable.outingList().filter { it.end.isAfter(java.time.LocalDateTime.now(PlanStore.seoulZone)) }.minByOrNull { it.start } }
    val slots = if (Timetable.isRestDay(today)) PlanSlot.slots(today) else listOf(PlanSlot.weekday0) + PlanSlot.slots(today)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TodayStudyCard(slots, places)
        if (outing != null) OutingStatusCard(outing)

        ApplySectionTitle("신청")
        // 자리 셋 — 면학실·도서관·심야면학을 한 줄에. 아이콘과 이름만(오늘 자리는 위 '오늘 면학'에 있어 되풀이하지 않음).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SeatTile("면학실", painterResource(R.drawable.ic_chair), ApplyColors.STUDY_ROOM, Modifier.weight(1f)) { onOpenSeats(SeatService.STUDY_ROOM) }
            SeatTile("도서관", painterResource(R.drawable.ic_local_library), ApplyColors.LIBRARY, Modifier.weight(1f)) { onOpenSeats(SeatService.LIBRARY) }
            SeatTile("심야면학", painterResource(R.drawable.ic_bedtime), ApplyColors.MIDNIGHT, Modifier.weight(1f)) { showingMidnight = true }
        }
        // 교과교실 — 신청과 교실별 현황(누가 어느 교실에 있는지)을 한 카드에.
        OneUiCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            ApplyRow("교과교실", painterResource(R.drawable.ic_meeting_room), ApplyColors.CLASSROOM) { applyScreen = ApplyService.CLASSROOM }
            OneUiDivider()
            ApplyRow("교실별 현황", painterResource(R.drawable.ic_school), ApplyColors.CLASSROOM_STATUS) { showingClassroomStatus = true }
        }
        OneUiCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            ApplyRow("외출·외박", painterResource(R.drawable.ic_directions_run), ApplyColors.OUTING) { applyScreen = ApplyService.OUTING }
        }

        ApplySectionTitle("내역")
        // 다섯 곳을 카드 한 장에 같은 폭으로 — 칩을 늘어놓으면 기기 폭에 따라 줄 끝이 들쭉날쭉하고 오른쪽이 비었습니다.
        fun show(service: ApplyService) = if (onShowHistory != null) onShowHistory(service) else historyScreen = service
        HistoryStrip(
            listOf(
                HistoryItem("면학실", painterResource(R.drawable.ic_chair), ApplyColors.STUDY_ROOM, historyShown == ApplyService.STUDY_ROOM) { show(ApplyService.STUDY_ROOM) },
                HistoryItem("도서관", painterResource(R.drawable.ic_local_library), ApplyColors.LIBRARY, historyShown == ApplyService.LIBRARY) { show(ApplyService.LIBRARY) },
                HistoryItem("교과교실", painterResource(R.drawable.ic_meeting_room), ApplyColors.CLASSROOM, openedInline == ApplyService.CLASSROOM.name) {
                    if (openedInline == ApplyService.CLASSROOM.name) openedInline = null
                    else { openedInline = ApplyService.CLASSROOM.name; loadHistory(ApplyService.CLASSROOM) }
                },
                HistoryItem("외출·외박", painterResource(R.drawable.ic_directions_run), ApplyColors.OUTING, historyShown == ApplyService.OUTING) { show(ApplyService.OUTING) },
                HistoryItem("심야면학", painterResource(R.drawable.ic_bedtime), ApplyColors.MIDNIGHT, openedInline == ApplyOrderStore.MIDNIGHT) {
                    if (openedInline == ApplyOrderStore.MIDNIGHT) openedInline = null else loadMidnightHistory()
                },
            ),
        )
        when (openedInline) {
            ApplyService.CLASSROOM.name -> OneUiCard(Modifier.fillMaxWidth()) {
                Text("교과교실 내역", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ClassroomHistoryBody(states[ApplyService.CLASSROOM] ?: ApplyHistoryState.Loading)
            }
            ApplyOrderStore.MIDNIGHT -> OneUiCard(Modifier.fillMaxWidth()) {
                Text("심야면학 내역", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                when (val m = midnightHistory) {
                    MidnightHistoryState.Idle, MidnightHistoryState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        OneUiLoading(size = 16.dp, stroke = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("불러오는 중", style = MaterialTheme.typography.bodySmall, color = onPageVariantColor())
                    }
                    is MidnightHistoryState.Loaded -> m.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                    is MidnightHistoryState.Error -> Text(m.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 신청·내역의 서비스 색 — 모든 기능(⊞)의 바로 가기와 같은 색. */
private object ApplyColors {
    val STUDY_ROOM = Color(0xFF5C7CFA)
    val LIBRARY = Color(0xFF8E7CFF)
    val MIDNIGHT = Color(0xFF4B4FC9)
    val CLASSROOM = Color(0xFF14A8B8)
    val CLASSROOM_STATUS = Color(0xFF2E9FAE)
    val OUTING = Color(0xFF34B27D)
}

@Composable
private fun ApplySectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = onPageVariantColor(),
        modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

/** 오늘 면학 — 타임마다 시작 시각, 장소, 자리·교실. 자리가 없는 타임은 점선 칸(홈 일정과 같은 '위치 미설정'). */
@Composable
private fun TodayStudyCard(slots: List<PlanSlot>, places: Map<PlanSlot, StudyPlace?>) {
    val scheme = MaterialTheme.colorScheme
    OneUiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("오늘 면학", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${PlanStore.today().monthValue}월 ${PlanStore.today().dayOfMonth}일", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            slots.forEach { slot ->
                val place = places[slot]
                // 신청 칸과 같은 장소(면학실·도서관·교과교실·심야면학)는 그 칸의 색, 나머지(생활관·방과후·1인2기)는 홈 일정의 장소 색.
                val color = place?.let {
                    when (it) {
                        is StudyPlace.StudyRoom -> ApplyColors.STUDY_ROOM
                        is StudyPlace.Library -> ApplyColors.LIBRARY
                        is StudyPlace.ClassroomPlace -> ApplyColors.CLASSROOM
                        is StudyPlace.Midnight -> ApplyColors.MIDNIGHT
                        else -> Color(WidgetKindColors.defaults[it.accent] ?: 0xFF3B82F6.toInt())
                    }
                }
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .then(if (color != null) Modifier.background(color.copy(alpha = if (scheme.isDark) 0.22f else 0.13f))
                        else Modifier.border(1.dp, scheme.onSurface.copy(alpha = 0.18f), shape))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text("${slot.title} ${slot.timeText.substringBefore(" ")}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(place?.name ?: "위치 미설정", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = color?.let { if (scheme.isDark) lerp(it, Color.White, 0.45f) else lerp(it, Color.Black, 0.25f) } ?: scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    place?.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** 다가오는(또는 지금인) 외출·외박 한 건 — 기간·사유와 승인 상태. */
@Composable
private fun OutingStatusCard(o: OutingSpan) {
    val scheme = MaterialTheme.colorScheme
    // 두 줄 — 날짜(외박은 기간)와 시각·사유. 한 줄에 날짜와 시각을 다 넣으면 좁은 폭에서 '09:00 ~ / 13:00'처럼 시각 중간에서 꺾였습니다.
    val day = java.time.format.DateTimeFormatter.ofPattern("M.d(E)", java.util.Locale.KOREAN)
    val hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val dates = if (o.overnight) "${o.start.format(day)} ~ ${o.end.format(day)}" else o.start.format(day)
    val times = "${o.start.format(hm)} ~ ${o.end.format(hm)}" + (o.reason?.let { " · $it" } ?: "")
    OneUiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ApplyIcon(painterResource(R.drawable.ic_directions_run), ApplyColors.OUTING, 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${if (o.overnight) "외박" else "외출"} · $dates", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(times, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            val ok = !o.pending
            val tint = if (ok) Color(0xFF2FB67C) else Color(0xFFE3A82E)
            Text(if (ok) "승인됨" else "승인 대기", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = if (scheme.isDark) lerp(tint, Color.White, 0.35f) else lerp(tint, Color.Black, 0.3f),
                modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

/** 자리 신청 칸 — 색 원 아이콘과 이름만. */
@Composable
private fun SeatTile(label: String, icon: Painter, color: Color, modifier: Modifier, onClick: () -> Unit) {
    OneUiCard(modifier, contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ApplyIcon(icon, color, 36.dp)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** 카드 안 한 줄 — 색 원 아이콘, 이름, 오른쪽 화살표. */
@Composable
private fun ApplyRow(label: String, icon: Painter, color: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ApplyIcon(icon, color, 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ApplyIcon(icon: Painter, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

private class HistoryItem(val label: String, val icon: Painter, val color: Color, val selected: Boolean, val onClick: () -> Unit)

/** 내역 다섯 곳 — 카드 한 장을 같은 폭 칸으로 나누고 가는 선으로 구분(모든 기능 바로 가기와 같은 결). 펼쳐 둔 칸은 그 색으로 옅게. */
@Composable
private fun HistoryStrip(items: List<HistoryItem>) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.onSurface.copy(alpha = 0.10f)
    OneUiCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            items.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.padding(vertical = 14.dp).width(0.7.dp).fillMaxHeight().background(line))
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (item.selected) item.color.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable(onClick = item.onClick)
                        .padding(horizontal = 2.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ApplyIcon(item.icon, item.color, 28.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(item.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Clip, color = scheme.onSurface)
                }
            }
        }
    }
}

/** 심야면학 내역 — 오늘 1·2타임 신청과 불참 횟수(예전 심야면학 카드의 '내역'과 같은 내용). */
private suspend fun loadMidnightLines(context: android.content.Context): MidnightHistoryState = try {
    val first = MidnightApi.dashboard(context, 1, null)
    val second = if (first.session2Available) runCatching { MidnightApi.dashboard(context, 2, first.floor) }.getOrNull() else null
    fun line(label: String, d: MidnightDashboard?) = when {
        d == null -> "$label · 운영 안 함"
        d.hasReservation -> "$label · ${d.reservationFloor ?: d.floor}층 ${d.reservationSeat}번"
        else -> "$label · 신청 없음"
    }
    MidnightHistoryState.Loaded(listOf(
        line("1타임", first),
        line("2타임", second),
        "불참 ${first.absenceCount}회 · " + (first.bannedUntil?.let { "${it}까지 신청 제한" } ?: "신청 제한 없음"),
    ))
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    MidnightHistoryState.Error(e.message ?: "불러오지 못했습니다")
}

/** 순서 편집 — 블록 이름마다 위·아래 버튼. 바꾸는 즉시 저장되고, 헤더의 체크 아이콘으로 끝냅니다. */
@Composable
internal fun OrderEditor(
    order: List<String>,
    defaultOrder: List<String>,
    label: (String) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    onReset: () -> Unit,
) {
    Column {
        Text(
            "위·아래 버튼으로 순서를 바꾸고, 끝나면 위의 체크를 누르세요.",
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        OneUiGroupColumn {
            order.forEachIndexed { index, key ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = onPageVariantColor(),
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        label(key),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "${label(key)} 위로")
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < order.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "${label(key)} 아래로")
                    }
                }
                if (index != order.lastIndex) OneUiDivider()
            }
        }
        Spacer(Modifier.height(12.dp))
        OneUiButton(
            text = "기본 순서로",
            onClick = onReset,
            style = OneUiButtonStyle.Neutral,
            enabled = order != defaultOrder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


/** 교과교실 내역 본문 — 상태별 문구/목록만 그리고, 동작 버튼은 카드 아래 공용 버튼 줄이 맡습니다. */
@Composable
private fun ClassroomHistoryBody(state: ApplyHistoryState) {
    when (state) {
        ApplyHistoryState.Idle -> Unit

        ApplyHistoryState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            OneUiLoading(size = 16.dp, stroke = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "내역을 불러오는 중",
                style = MaterialTheme.typography.bodySmall,
                color = onPageVariantColor(),
            )
        }

        is ApplyHistoryState.Error -> Text(
            if (state.message == ACADEMIC_NEEDS_LOGIN) "하이하나 계정을 먼저 등록해 주세요" else state.message,
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
        )

        is ApplyHistoryState.Rows -> if (state.rows.isEmpty()) {
            Text(
                "내역 없음",
                style = MaterialTheme.typography.bodySmall,
                color = onPageVariantColor(),
            )
        } else {
            Column {
                state.rows.forEach { row ->
                    val parts = listOfNotNull(row.slot, row.place, row.status, row.appliedDate)
                        .filter { it.isNotBlank() }
                    Text(
                        if (parts.isEmpty()) "내역" else parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}
