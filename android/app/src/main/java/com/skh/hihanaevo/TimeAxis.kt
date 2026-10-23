package com.skh.hihanaevo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import com.skh.hihanaevo.ui.drawPageBackground
import com.skh.hihanaevo.ui.oneUiFrostedBackdrop
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.isDark
import com.skh.hihanaevo.widget.WidgetKindColors
import com.skh.hihanaevo.ui.AppIcons
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.skh.hihanaevo.ui.EvoSheet
import com.skh.hihanaevo.ui.MaterialExtraIcons
import com.skh.hihanaevo.ui.OneUiCard
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 시간축 화면(실험) — 탭 대신 시간 하나로 앱을 봅니다. 설정 → 화면 → 홈 화면에서 '시간축'을 고르면 폰에서 하단 탭 바 대신
 * 이 화면이 뜹니다.
 *
 * - 시간 배율: 날마다 06:30~24:00 을 세로로 잇고, 왼쪽 줄엔 일과(수업·면학·급식), 오른쪽 줄엔 그 시각에 생긴 소식(게시판·
 *   클래스룸 글, 시각이 있는 마감·일정)을 말풍선으로. '지금' 선이 그어지고 처음엔 지금 근처에서 시작합니다.
 * - 하루 배율: 하루가 막대 한 줄 + 그날의 일정으로. 날을 누르면 그날의 시간 배율로.
 * - 달 배율: 주마다 학사·게시판·클래스룸 일정. 주를 누르면 하루 배율로.
 * - 두 손가락으로 오므리면 넓게(시간 → 하루 → 달), 벌리면 좁게. 위 칩으로도 바꿉니다.
 * - 블록을 누르면 관련 화면으로(수업 → 시간표, 면학 → 신청·내역, 급식 → 급식). 나머지 화면은 하단 바의 ⊞ 모든 기능에서.
 */
/** 사진 위에 바로 놓이는 글자·선 색 — 홈 머리처럼 사진 밝기를 따릅니다(사진이 없으면 테마색). */
@Composable
private fun pageInk(): Color = com.skh.hihanaevo.ui.LocalOnPageColor.current ?: MaterialTheme.colorScheme.onSurface

/** 사진 위의 강조 글자(오늘·지금으로 등) — 강조색은 사진에 묻혀 사진이 있으면 [pageInk]. */
@Composable
private fun pageAccent(): Color = com.skh.hihanaevo.ui.LocalOnPageColor.current ?: MaterialTheme.colorScheme.primary

/**
 * 일정 칩·블록·말풍선의 바탕 — 사진 위에선 홈 카드처럼 서리 유리(뒤 사진이 흐리게 비침) 위에 색을 옅게, 사진이 없으면 색만.
 * [onDark] 를 주면 판의 실제 밝기를 돌려받아 안 글자색을 맞춥니다([FrostContent]).
 */
@Composable
private fun Modifier.tintFill(color: Color, alpha: Float, onDark: androidx.compose.runtime.MutableState<Boolean?>? = null): Modifier {
    val frost = frostPanel(onDark)
    return if (alpha > 0f) frost.background(color.copy(alpha = alpha)) else frost
}

/** 색 없는 서리 판 — 말풍선·하루 카드. 사진이 없으면 테마 면 색. */
@Composable
private fun Modifier.frostPanel(onDark: androidx.compose.runtime.MutableState<Boolean?>? = null): Modifier {
    val scheme = MaterialTheme.colorScheme
    return when {
        com.skh.hihanaevo.ui.LocalBlurredPageBackground.current == null -> this
        // 성능 모드는 흐림 없이 거의 불투명한 면 — 홈 카드와 같게.
        com.skh.hihanaevo.ui.PerformanceMode.enabled -> background(scheme.surface.copy(alpha = 0.94f))
        else -> oneUiFrostedBackdrop(scheme.surface.copy(alpha = com.skh.hihanaevo.ui.FrostOpacity.alpha), onDark)
    }
}

enum class AxisScale(val label: String) { HOUR("시간"), DAY("하루"), MONTH("주") }

/** 한 시간 높이 — 50분 블록에도 시각·과목·장소 세 줄이 들어가게(64dp 일 땐 장소가 잘렸습니다). */
private val HOUR_DP = 88.dp
private const val DAY_START = 6.5f
private const val DAY_END = 24f
private val GUTTER = 44.dp
/** 축 위 모든 항목(수업·면학 블록, 게시판·클래스룸·일정 말풍선, 종일 칩)의 모서리 — 하나로 맞춥니다. */
private val ItemShape = RoundedCornerShape(12.dp)
private val hm = DateTimeFormatter.ofPattern("HH:mm")

private fun dayLabel(date: LocalDate, today: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    val rel = when (date) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        today.minusDays(1) -> "어제"
        else -> null
    }
    return "${date.monthValue}월 ${date.dayOfMonth}일 $dow" + (rel?.let { " · $it" } ?: "")
}

/** 축 위에 놓는 한 칸 — 일과 블록(왼쪽 줄) 또는 소식 말풍선(오른쪽 줄). */
private class AxisEntry(
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val title: String,
    val meta: String?,
    val source: String,
    val color: Color,
    val onClick: (() -> Unit)?,
)

/** 일정 제목 — 면학감독(SG03010000)은 제목이 선생님 이름뿐이라 '면학감독 ○○○' 으로. */
private fun agendaTitle(item: AgendaItem): String =
    if (item.academic?.code == "SG03010000") "면학감독 ${item.title}" else item.title

private fun parsePostTime(raw: String?): LocalDateTime? {
    val m = raw?.let { Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2}))?").find(it) } ?: return null
    val (y, mo, d, h, mi) = m.destructured
    return runCatching {
        LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toIntOrNull() ?: 8, mi.toIntOrNull() ?: 0)
    }.getOrNull()
}

@Composable
internal fun TimeAxisScreen(
    today: LocalDate,
    now: LocalDateTime,
    kindColors: Map<Accent, Int>,
    mealDays: Map<String, DayMeals>,
    schedule: List<HanaScheduleEntry>,
    studentGrade: Int,
    agendaExtras: List<AgendaItem>,
    boardPosts: List<HanaBoardPost>,
    classroom: ClassroomSnapshot?,
    /** 축에서 누른 블록이 여는 탭(급식 블록 → 급식, 수업 → 시간표, 면학 → 신청·내역). */
    onOpenTab: (Int) -> Unit,
    onOpenPost: (HanaBoardPost) -> Unit,
    onOpenClassroomPost: (ClassroomPost) -> Unit,
    onOpenAgenda: (AgendaItem) -> Unit,
    onOpenSchedule: () -> Unit,
    /** 바뀌면 처음 모습(시간 배율, 오늘 지금 근처)으로 — 화면을 버리지 않고 살려 두므로, 홈으로 돌아갈 때마다 올려 줍니다. */
    resetTick: Int = 0,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var scale by remember { mutableStateOf(AxisScale.HOUR) }
    // 하루·주 배율에서 고른 날 — 시간 배율로 내려갈 때 그날로.
    var focusDay by remember { mutableStateOf(today) }
    val days = remember(today) { (-1..6).map { today.plusDays(it.toLong()) } }

    // 날마다 면학 자리와 급식(캐시) — 축에 놓을 블록·끼니.
    var places by remember { mutableStateOf<Map<LocalDate, Map<PlanSlot, StudyPlace?>>>(emptyMap()) }
    var meals by remember { mutableStateOf<Map<String, DayMeals>>(emptyMap()) }
    LaunchedEffect(today) {
        (days + (7..13).map { today.plusDays(it.toLong()) }).forEach { d ->
            places = places + (d to PlanStore.placesForToday(context, d))
            if (PlanStore.dayKey(d) !in mealDays) MealCache.load(context, d)?.let { meals = meals + (PlanStore.dayKey(d) to it) }
        }
    }
    val allMeals = meals + mealDays
    val agenda = remember(schedule, studentGrade, agendaExtras, today) { Agenda.merge(schedule, studentGrade, agendaExtras, today.minusDays(1)) }

    fun kind(accent: Accent) = Color(kindColors[accent] ?: WidgetKindColors.defaults.getValue(accent))

    fun dayEntries(date: LocalDate): Pair<List<AxisEntry>, List<AxisEntry>> {
        val blocks = Timetable.blocks(date) { places[date]?.get(it) }
        val left = mutableListOf<AxisEntry>()
        blocks.forEach { b ->
            if (b.isBlank) return@forEach
            val meal = when (b.accent) {
                Accent.BREAKFAST -> Meal.BREAKFAST
                Accent.LUNCH -> Meal.LUNCH
                Accent.DINNER -> Meal.DINNER
                else -> null
            }
            if (b.kind is BlockKind.GapKind && meal == null) return@forEach
            if (meal != null) {
                val menu = allMeals[PlanStore.dayKey(date)]?.items?.get(meal.key).orEmpty()
                left += AxisEntry(b.start, b.end, "${meal.label} 급식", menu.take(3).joinToString(" · ") { it.name }.ifEmpty { null }, "급식", kind(b.accent)) { onOpenTab(TabIndex.MEAL) }
            } else {
                // '수업 중'은 지금 하는 수업에만 맞는 말 — 축엔 다른 날·다른 시각도 있어 수업은 교시와 과목만(공강은 '공강' 한 번).
                val title = when (val k = b.kind) {
                    is BlockKind.LessonKind -> if (k.lesson.isFree) "${k.period}교시 · 공강" else "${k.period}교시 ${k.lesson.title}"
                    else -> b.headline
                }
                // 누르면 관련 화면으로 — 수업은 시간표, 면학 타임은 신청·내역(좌석·장소 신청).
                val open: (() -> Unit)? = when (b.kind) {
                    is BlockKind.LessonKind -> { { onOpenTab(TabIndex.SCHEDULE) } }
                    is BlockKind.StudyKind -> { { onOpenTab(TabIndex.APPLY) } }
                    else -> null
                }
                left += AxisEntry(b.start, b.end, title, b.room, "", kind(b.accent), open)
            }
        }
        val right = mutableListOf<AxisEntry>()
        boardPosts.forEach { post ->
            val at = parsePostTime(post.date) ?: return@forEach
            if (at.toLocalDate() == date) right += AxisEntry(at, null, post.title.ifEmpty { "(제목 없음)" }, post.writer, "게시판", AgendaSource.BOARD.color) { onOpenPost(post) }
        }
        classroom?.posts?.forEach { post ->
            if (post.updated.toLocalDate() != date) return@forEach
            val title = post.title.ifEmpty { post.text.lineSequence().firstOrNull().orEmpty() }.ifEmpty { post.kind.label }
            right += AxisEntry(post.updated, null, title, classroom.course(post.courseId)?.name, "클래스룸", AgendaSource.CLASSROOM.color) { onOpenClassroomPost(post) }
        }
        agenda.forEach { item ->
            if (item.date != date) return@forEach
            val t = item.time?.let { Regex("(\\d{1,2}):(\\d{2})").find(it) }?.let { m -> runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() } ?: return@forEach
            val color = if (item.deadline) Color(0xFFFF6B6B) else item.academic?.let { scheduleColor(it.code) } ?: item.source.color
            val source = when {
                item.deadline -> "마감"
                item.academic != null -> scheduleLabel(item.academic.code)
                else -> item.source.label
            }
            // 말풍선은 출처 칸에 종류(면학감독 등)가 이미 있으니 제목은 그대로 — 같은 말을 두 번 적지 않게.
            right += AxisEntry(date.atTime(t), null, item.title, item.detail, source, color) {
                if (item.academic != null) onOpenSchedule() else onOpenAgenda(item)
            }
        }
        return left to right.sortedBy { it.start }
    }

    /** 그날 시각 없는 일정 — 날 머리의 칩. */
    // 기간 일정은 시작하는 날에만, 이미 진행 중인 것은 오늘에만 — 며칠씩 걸친 일정이 날마다 되풀이되지 않게.
    fun allDay(date: LocalDate): List<AgendaItem> = agenda.filter {
        it.time == null && (it.date == date || (date == today && it.date.isBefore(today) && !(it.endDate ?: it.date).isBefore(today)))
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 처음엔 지금 근처 — 오늘(목록 둘째) 머리에서 지금 시각까지 내려가고, 화면 위쪽 1/4 쯤에 지금이 오게.
    suspend fun jumpTo(date: LocalDate, hour: Float) {
        val index = days.indexOf(date).coerceAtLeast(0)
        val y = with(density) { (HOUR_DP * (hour - DAY_START).coerceAtLeast(0f) + 56.dp - 140.dp).roundToPx() }
        listState.scrollToItem(index, y.coerceAtLeast(0))
    }
    LaunchedEffect(resetTick) {
        if (resetTick == 0) return@LaunchedEffect
        scale = AxisScale.HOUR
        focusDay = today
        jumpTo(today, now.hour + now.minute / 60f)
    }
    LaunchedEffect(scale, focusDay) {
        if (scale == AxisScale.HOUR) {
            if (focusDay == today) jumpTo(today, now.hour + now.minute / 60f) else jumpTo(focusDay, 8f)
        }
    }

    // 두 손가락 오므리기·벌리기로 배율 바꾸기 — 한 손가락 스크롤은 그대로 목록으로.
    val pinch = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var startDist = 0f
            var done = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2 && !done) {
                    val d = (pressed[0].position - pressed[1].position).getDistance()
                    if (startDist == 0f) startDist = d
                    val ratio = d / startDist
                    if (ratio < 0.72f) {
                        scale = AxisScale.entries[minOf(scale.ordinal + 1, AxisScale.entries.lastIndex)]
                        done = true
                    } else if (ratio > 1.4f) {
                        scale = AxisScale.entries[maxOf(scale.ordinal - 1, 0)]
                        done = true
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 바탕은 테마 배경색 — 사진을 흐리게 깔면 사진 밝기와 테마 글자색이 엇갈려 블록 글자가 묻혔습니다.
    androidx.compose.runtime.CompositionLocalProvider(
        // 블록·카드 안 글자는 테마색 — 사진 위에 바로 놓이는 글자만 pageInk()·onPageVariantColor() 로 따로 칠합니다.
        androidx.compose.material3.LocalContentColor provides scheme.onSurface,
    ) {
    val dark = scheme.isDark
    Box(Modifier.fillMaxSize().then(pinch)) {
        // 바탕은 홈과 같은 배경 사진 그대로(사진이 없으면 기본 그라데이션).
        com.skh.hihanaevo.ui.OneUiPageBackdrop()
        Column(Modifier.fillMaxSize()) {
            // 머리 — 날짜, 배율 칩, 지금으로.
            Column(Modifier.padding(start = 20.dp, end = 16.dp, top = top + 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN), style = MaterialTheme.typography.labelLarge, color = com.skh.hihanaevo.ui.onPageVariantColor())
                        Text("${today.monthValue}월 ${today.dayOfMonth}일", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = pageInk())
                    }
                    Text(
                        "지금으로",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = pageAccent(),
                        modifier = Modifier.clip(CircleShape).clickable {
                            focusDay = today
                            scale = AxisScale.HOUR
                            scope.launch { jumpTo(today, now.hour + now.minute / 60f) }
                        }.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                val ink = pageInk()
                val onInk = if (ink.luminance() > 0.5f) Color(0xFF111114) else Color.White
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AxisScale.entries.forEach { s ->
                        val on = s == scale
                        Text(
                            s.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (on) onInk else com.skh.hihanaevo.ui.onPageVariantColor(),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) ink else ink.copy(alpha = 0.12f))
                                .clickable { scale = s }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("두 손가락으로 오므리기", style = MaterialTheme.typography.labelSmall, color = com.skh.hihanaevo.ui.onPageVariantColor())
                }
            }
            Box(Modifier.weight(1f)) {
                when (scale) {
                    AxisScale.HOUR -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(days, key = { it.toString() }) { date ->
                            val (left, right) = dayEntries(date)
                            HourDay(date, today, now, left, right, allDay(date), onOpenAgenda, onOpenSchedule)
                        }
                        item { Spacer(Modifier.height(120.dp + bottom)) }
                    }
                    AxisScale.DAY -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items((0..13).map { today.plusDays(it.toLong()) }, key = { it.toString() }) { date ->
                            val (left, right) = dayEntries(date)
                            DayCard(date, today, left, right, allDay(date)) {
                                focusDay = date
                                scale = AxisScale.HOUR
                            }
                        }
                        item { Spacer(Modifier.height(120.dp + bottom)) }
                    }
                    AxisScale.MONTH -> MonthWeeks(today, agenda) { monday ->
                        focusDay = if (monday.isAfter(today)) monday else today
                        scale = AxisScale.DAY
                    }
                }
            }
        }
    }
    }
}

/** 시간 배율의 하루 — 머리(날짜·종일 일정 칩)와 06:30~24:00 축. */
@Composable
private fun HourDay(
    date: LocalDate,
    today: LocalDate,
    now: LocalDateTime,
    left: List<AxisEntry>,
    right: List<AxisEntry>,
    allDay: List<AgendaItem>,
    onOpenAgenda: (AgendaItem) -> Unit,
    onOpenSchedule: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(dayLabel(date, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = if (date == today) pageAccent() else pageInk())
            if (allDay.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allDay.take(6).forEach { item ->
                    val color = item.academic?.let { scheduleColor(it.code) } ?: if (item.deadline) Color(0xFFFF6B6B) else item.source.color
                    Text(
                        agendaTitle(item),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(ItemShape)
                            .tintFill(color, if (dark) 0.16f else 0.12f)
                            .clickable { if (item.academic != null) onOpenSchedule() else onOpenAgenda(item) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(HOUR_DP * (DAY_END - DAY_START))) {
            // 일과 블록은 옆에 소식 말풍선이 없으면 전체 폭, 있을 때만 6:4 로 나눕니다 — 늘 반반이던 땐 하루 대부분 오른쪽이
            // 비어 있는데도 과목·장소가 잘렸습니다.
            // 블록·말풍선 사이와 가장자리는 4~6dp 로 바짝 — 넓게 두면 1타임 블록과 게시판 말풍선이 따로 노는 것처럼 보였습니다.
            val fullW = maxWidth - GUTTER - 10.dp
            val narrowW = fullW * 0.58f
            val bubbleX = GUTTER + 6.dp + narrowW + 4.dp
            val bubbleW = fullW - narrowW - 4.dp
            fun y(t: LocalDateTime): Dp = HOUR_DP * ((t.hour + t.minute / 60f) - DAY_START).coerceIn(0f, DAY_END - DAY_START)
            // 시각 눈금 — 사진 위에 바로 놓이니 사진 밝기 색으로.
            val hourInk = com.skh.hihanaevo.ui.onPageVariantColor()
            val lineInk = pageInk()
            for (h in 7..23) {
                val yy = HOUR_DP * (h - DAY_START)
                Text("$h:00", style = MaterialTheme.typography.labelSmall, color = hourInk, modifier = Modifier.offset(x = 4.dp, y = yy - 8.dp).width(GUTTER - 6.dp))
                Box(Modifier.offset(x = GUTTER, y = yy).width(maxWidth - GUTTER - 4.dp).height(1.dp).background(lineInk.copy(alpha = 0.08f)))
            }
            Box(Modifier.offset(x = GUTTER).width(2.dp).fillMaxSize().background(lineInk.copy(alpha = 0.14f)))
            // 소식 말풍선 자리 — 겹치면 아래로 밀어 4dp 띄워 쌓습니다. 높이는 제목 줄 수를 재서 정확히(예전엔 늘 66dp 로 잡아
            // 한 줄짜리 말풍선 아래가 휑했습니다).
            val bubbleTitleStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            val bubbleHeadStyle = MaterialTheme.typography.labelSmall
            val bubbleHeights = remember(right, bubbleW, bubbleTitleStyle, density) {
                with(density) {
                    val head = measurer.measure("게시판 00:00", bubbleHeadStyle).size.height.toDp()
                    right.map { e ->
                        val title = measurer.measure(
                            e.title, bubbleTitleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            constraints = Constraints(maxWidth = (bubbleW - 20.dp).roundToPx().coerceAtLeast(1)),
                        ).size.height.toDp()
                        14.dp + head + title
                    }
                }
            }
            val bubbleTops = run {
                var floor = 0.dp
                right.mapIndexed { i, e -> maxOf(y(e.start), floor).also { floor = it + bubbleHeights[i] + 4.dp } }
            }
            // 왼쪽 줄 — 일과
            left.forEach { e ->
                val end = e.end ?: e.start.plusMinutes(50)
                val h = (y(end) - y(e.start) - 2.dp).coerceAtLeast(34.dp)
                val past = !end.isAfter(now)
                val top = y(e.start)
                val shared = bubbleTops.indices.any { i -> bubbleTops[i] < top + h && bubbleTops[i] + bubbleHeights[i] > top }
                val onDark = remember { mutableStateOf<Boolean?>(null) }
                Column(
                    Modifier
                        .offset(x = GUTTER + 6.dp, y = top)
                        .width(if (shared) narrowW else fullW)
                        .height(h)
                        // 모서리를 먼저 자르고 흐리게 — 반대 순서면 서리 판이 둥근 모서리 밖까지 그려져 지난 블록만 각졌습니다.
                        .clip(ItemShape)
                        .alpha(if (past) 0.45f else 1f)
                        .tintFill(e.color, if (dark) 0.18f else 0.14f, onDark)
                        .then(if (e.onClick != null) Modifier.clickable(onClick = e.onClick) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    com.skh.hihanaevo.ui.FrostContent(onDark.value) {
                        Text("${e.start.format(hm)} – ${end.format(hm)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = e.color, maxLines = 1)
                        Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        e.meta?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            // 오른쪽 줄 — 소식 말풍선.
            right.forEachIndexed { i, e ->
                val top = bubbleTops[i]
                val past = e.start.isBefore(now)
                val onDark = remember { mutableStateOf<Boolean?>(null) }
                val photo = com.skh.hihanaevo.ui.LocalBlurredPageBackground.current != null
                Column(
                    Modifier
                        .offset(x = bubbleX, y = top)
                        .width(bubbleW)
                        .clip(ItemShape)
                        .alpha(if (past) 0.7f else 1f)
                        .then(if (photo) Modifier.frostPanel(onDark) else Modifier.background(scheme.surface.copy(alpha = if (dark) 0.85f else 0.92f)))
                        .then(if (e.onClick != null) Modifier.clickable(onClick = e.onClick) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    com.skh.hihanaevo.ui.FrostContent(onDark.value) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(e.color))
                            Spacer(Modifier.width(5.dp))
                            Text(e.source, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = e.color, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(e.start.format(hm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(e.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // 지금 선
            if (date == now.toLocalDate()) {
                val yy = y(now)
                Box(Modifier.offset(x = GUTTER - 4.dp, y = yy - 1.dp).width(maxWidth - GUTTER - 4.dp).height(2.dp).background(scheme.primary))
                Box(Modifier.offset(x = GUTTER - 4.dp, y = yy - 5.dp).size(10.dp).clip(CircleShape).background(scheme.primary))
                Text(
                    now.format(hm),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onPrimary,
                    modifier = Modifier.offset(x = 2.dp, y = yy - 9.dp).clip(RoundedCornerShape(8.dp)).background(scheme.primary).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** 하루 배율의 한 날 — 07:00~23:30 막대(일과 색) + 그날 일정 몇 줄. */
@Composable
private fun DayCard(date: LocalDate, today: LocalDate, left: List<AxisEntry>, right: List<AxisEntry>, allDay: List<AgendaItem>, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    val from = date.atTime(7, 0)
    val span = Duration.ofMinutes(16 * 60 + 30).toMinutes().toFloat()
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(
                when {
                    date == today -> Modifier.tintFill(scheme.primary, if (dark) 0.16f else 0.10f)
                    com.skh.hihanaevo.ui.LocalBlurredPageBackground.current != null -> Modifier.frostPanel()
                    else -> Modifier.background(scheme.surface.copy(alpha = if (dark) 0.7f else 0.85f))
                },
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(dayLabel(date, today), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
        BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(7.dp)).background(scheme.onSurface.copy(alpha = 0.06f))) {
            left.forEach { e ->
                val s = Duration.between(from, e.start).toMinutes().coerceIn(0, span.toLong()) / span
                val en = Duration.between(from, e.end ?: e.start.plusMinutes(50)).toMinutes().coerceIn(0, span.toLong()) / span
                if (en > s) Box(Modifier.offset(x = maxWidth * s).width(maxWidth * (en - s) - 1.dp).fillMaxSize().background(e.color.copy(alpha = 0.85f)))
            }
        }
        val marks = allDay.map { agendaTitle(it) to (it.academic?.let { a -> scheduleColor(a.code) } ?: it.source.color) } +
            right.filter { it.source != "게시판" && it.source != "클래스룸" }.map { "${it.start.format(hm)} ${it.title}" to it.color }
        marks.take(3).forEach { (text, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val news = right.count { it.source == "게시판" || it.source == "클래스룸" }
        if (news > 0) Text("소식 $news", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

/** 달 배율 — 이번 주부터 16주, 주마다 일정 칩. */
@Composable
private fun MonthWeeks(today: LocalDate, agenda: List<AgendaItem>, onOpenWeek: (LocalDate) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val thisMonday = today.with(DayOfWeek.MONDAY)
    // 이번 주부터 4주(학사일정을 받는 범위와 같음 — HanaAcademicRepository.scheduleEnd), 그 사이 일정이 하나도 없는 주가 나오면
    // 거기서 끊습니다 — 빈 주가 줄줄이 이어지지 않게.
    val weeks = (0..3).map { thisMonday.plusWeeks(it.toLong()) }.let { all ->
        val firstEmpty = all.indexOfFirst { monday -> monday != thisMonday && agenda.none { !it.date.isBefore(monday) && !it.date.isAfter(monday.plusDays(6)) } }
        if (firstEmpty > 0) all.take(firstEmpty) else all
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(weeks, key = { it.toString() }) { monday ->
            val sunday = monday.plusDays(6)
            // 그 주에 시작하는 일정만 — 며칠씩 걸친 일정이 주마다 되풀이되지 않게. 이미 진행 중인 것은 이번 주에 한 번.
            val events = agenda.filter {
                val startsHere = !it.date.isBefore(monday) && !it.date.isAfter(sunday)
                val ongoingNow = monday == thisMonday && it.date.isBefore(monday) && !(it.endDate ?: it.date).isBefore(monday)
                startsHere || ongoingNow
            }
            Row(
                Modifier.fillMaxWidth().clickable { onOpenWeek(monday) }.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.End) {
                    if (monday.dayOfMonth <= 7 || monday == thisMonday) Text("${monday.monthValue}월", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = pageAccent())
                    Text("${monday.dayOfMonth}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (monday == thisMonday) pageAccent() else pageInk())
                }
                Spacer(Modifier.width(12.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (events.isEmpty()) Text("—", style = MaterialTheme.typography.bodySmall, color = com.skh.hihanaevo.ui.onPageVariantColor())
                    events.take(6).forEach { item ->
                        val color = if (item.deadline) Color(0xFFFF6B6B) else item.academic?.let { scheduleColor(it.code) } ?: item.source.color
                        Text(
                            "${item.date.dayOfMonth}일 ${agendaTitle(item)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(ItemShape).tintFill(color, if (dark) 0.16f else 0.12f).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(120.dp + bottom)) }
    }
}

/** ⊞ 모든 기능의 '학사일정' 칸 — 앱 탭 번호가 따로 없어(일정 탭의 두 번째 화면) 세 칸 하단 바에서만 쓰는 번호입니다. */
const val AXIS_ACADEMIC = 100

/** 모든 기능 아래 바로 가기 한 칸 — 신청·내역 안의 화면으로 곧장. [dim] 이면 흐리게. */
internal class QuickLink(val key: String, val label: String, val icon: @Composable () -> Painter, val color: Color, val dim: Boolean = false, val open: () -> Unit)

/** 바로 가기에 둘 칸과 순서 — 모든 기능의 '편집'에서 고르고 기기에 저장합니다. 처음엔 좌석·신청·심야면학·외출 내역 여섯. */
internal object QuickLinkStore {
    private const val PREFS = "quick_links"
    private const val KEY = "keys"
    val defaults = listOf("library", "study_room", "classroom", "outing", "midnight", "outing_history")
    private var cached by mutableStateOf<List<String>?>(null)

    fun get(context: android.content.Context): List<String> {
        cached?.let { return it }
        val raw = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null)
        return (raw?.let { if (it.isEmpty()) emptyList() else it.split(',') } ?: defaults).also { cached = it }
    }

    fun set(context: android.content.Context, keys: List<String>) {
        cached = keys
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, keys.joinToString(",")).apply()
    }
}

/** 모든 기능의 블록 하나 — 색 원 아이콘, 이름, 한 줄 설명, (알리미만) 안 읽은 수. */
private class Destination(val label: String, val icon: @Composable () -> Painter, val color: Color, val hint: String, val badge: Int = 0, val open: () -> Unit)

/**
 * ⊞ 모든 기능 — Evo 하단 바의 네모 넷을 누르면 다른 화면과 같은 시트([EvoSheet])로 올라와, 갈 곳 여덟을 두 칸 블록(홈 카드와
 * 같은 재질)으로 보여 줍니다. 설정은 머리의 톱니로. 고르면 그 화면이 이 시트 위로 올라오고, 다 덮은 뒤 이 시트는 조용히
 * 닫힙니다(그 화면을 닫으면 홈·타임라인으로 돌아감). 끌어 내리기·뒤로 가기·닫기는 다른 시트와 같습니다.
 */
@Composable
internal fun AxisPanel(
    open: Boolean,
    onClose: () -> Unit,
    alimUnread: Int,
    onOpenTab: (Int) -> Unit,
    onOpenAlim: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 여덟 블록 아래 '바로 가기'로 고를 수 있는 곳 전부 — 무엇을 어떤 순서로 둘지는 [QuickLinkStore]. */
    quickLinks: List<QuickLink> = emptyList(),
) {
    if (!open) return
    // 모든 기능은 열어 둔 채 — 고른 화면이 이 시트 자리로 옆에서 밀려 들어오고, 뒤로 가면 다시 드러납니다.
    fun go(open: () -> Unit) = open()
    val dest = axisDestinations(alimUnread, onOpenTab, onOpenAlim, onOpenWeather, ::go)
    val scheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    var editingQuick by remember { mutableStateOf(false) }
    val chosenKeys = QuickLinkStore.get(context)
    val chosen = chosenKeys.mapNotNull { k -> quickLinks.find { it.key == k } }
    EvoSheet(
        title = "전체",
        onClosed = onClose,
        photo = true,
        actions = {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { go(onOpenSettings) }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(AppIcons.Settings, contentDescription = "설정", tint = pageInk())
            }
        },
    ) { pad ->
        // 폰마다 높이가 달라 고정 크기면 바로 가기가 화면 밖으로 잘렸습니다 — 남는 높이를 재서 블록과 바로 가기 칸을 같은 비율로
        // 줄여 한 화면에 들어가게 합니다. 글자 크기 설정만큼 최소 높이를 지키고, 그래도 넘치면 스크롤됩니다.
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val avail = maxHeight - pad.calculateTopPadding() - pad.calculateBottomPadding()
        val quickRows = if (quickLinks.isEmpty() || editingQuick) 0 else (chosen.size + 2) / 3
        // 카드 둘(여덟 곳 · 바로 가기)과 그 사이 '바로 가기' 머리 — 칸 사이는 카드 안 가는 선이라 따로 띄우지 않습니다.
        val gaps = if (quickLinks.isEmpty()) 0 else 2
        val header = if (quickLinks.isEmpty()) 0.dp else 44.dp
        val hint = if (quickLinks.isNotEmpty() && !editingQuick && chosen.isEmpty()) 20.dp else 0.dp
        // 높이는 글자 줄 높이(제목 24sp·설명 18sp·바로 가기 이름 21sp × 글자 크기)에서 셉니다 — 그보다 낮추면 글자 아래가 잘립니다.
        val blockFull = (68 + 60 * fs).dp
        val blockMin = (58 + 42 * fs).dp
        val cellMin = (52 + 21 * fs).dp
        val needFull = blockFull * (4f + 0.72f * quickRows) + 12.dp * gaps + header + hint
        val compact = avail < needFull
        val gap = if (compact) 10.dp else 12.dp
        val rawBlock = (avail - gap * gaps - header - hint) / (4f + 0.72f * quickRows)
        val blockH = rawBlock.coerceIn(blockMin, blockFull)
        val cellH = (blockH * 0.72f).coerceIn(cellMin, (67 + 21 * fs).dp)
        val quickHeader: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().height(header), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "바로 가기",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = com.skh.hihanaevo.ui.onPageVariantColor(),
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                com.skh.hihanaevo.ui.OneUiTextButton(if (editingQuick) "완료" else "편집", { editingQuick = !editingQuick }, color = pageAccent())
            }
        }
        val emptyHint: @Composable () -> Unit = {
            Text(
                "'편집'을 눌러 자주 여는 곳을 두세요.",
                style = MaterialTheme.typography.bodySmall,
                color = com.skh.hihanaevo.ui.onPageVariantColor(),
                modifier = Modifier.height(hint).padding(start = 6.dp),
            )
        }
        if (!editingQuick && rawBlock >= blockMin) {
            // 한 화면에 들어가면 스크롤 없이 꽉 채웁니다 — 남는 높이를 여덟 곳(4줄)과 바로 가기(줄 수 × 0.72)가 비율대로 나눠 가집니다.
            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
                DestinationGrid(dest, 2, null, compact = rawBlock < blockFull, modifier = Modifier.weight(4f))
                if (quickLinks.isNotEmpty()) {
                    quickHeader()
                    if (chosen.isEmpty()) emptyHint()
                    else QuickLinkGrid(chosen, cellH, fill = true, modifier = Modifier.weight(0.72f * quickRows))
                }
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(gap)) {
            // 편집 중이거나 아주 작은 화면 — 최소 높이를 지키고 스크롤.
            item { DestinationGrid(dest, 2, blockH, compact) }
            if (quickLinks.isNotEmpty()) {
                item { quickHeader() }
                if (editingQuick) item {
                    QuickLinkEditor(quickLinks, chosenKeys) { QuickLinkStore.set(context, it) }
                } else {
                    if (chosen.isEmpty()) item { emptyHint() }
                    if (chosen.isNotEmpty()) item { QuickLinkGrid(chosen, cellH) }
                }
            }
        }
        }
    }
}

/** ⊞ 모든 기능의 여덟 곳 — 아이콘은 버튼 결이 바깥 세트면 그 세트, 아니면 머티리얼(기본 묶음 + MaterialExtraIcons + 앱의 머티리얼 그림). [go] 는 고른 뒤 할 일을 감쌉니다. */
private fun axisDestinations(
    alimUnread: Int,
    onOpenTab: (Int) -> Unit,
    onOpenAlim: () -> Unit,
    onOpenWeather: () -> Unit,
    go: (() -> Unit) -> Unit,
): List<Destination> =
    listOf(
        Destination("시간표", { rememberVectorPainter(AppIcons.block("timetable") ?: MaterialExtraIcons.GridOn) }, Color(0xFF4C8DF6), "이번 주 수업") { go { onOpenTab(TabIndex.SCHEDULE) } },
        Destination("학사일정", { rememberVectorPainter(AppIcons.block("academic") ?: Icons.Filled.DateRange) }, Color(0xFF14A8B8), "시험·행사·방학") { go { onOpenTab(AXIS_ACADEMIC) } },
        Destination("급식", { AppIcons.block("meal")?.let { rememberVectorPainter(it) } ?: painterResource(R.drawable.ic_meal) }, Color(0xFFF08A3C), "이번 주 식단과 알레르기") { go { onOpenTab(TabIndex.MEAL) } },
        Destination("게시판", { rememberVectorPainter(AppIcons.block("board") ?: Icons.AutoMirrored.Filled.List) }, Color(0xFFE3A82E), "공지·가정통신문") { go { onOpenTab(TabIndex.BOARD) } },
        Destination("신청·내역", { rememberVectorPainter(AppIcons.block("apply") ?: Icons.Filled.Edit) }, Color(0xFF8E7CFF), "좌석·외출·신청 내역") { go { onOpenTab(TabIndex.APPLY) } },
        Destination("클래스룸", { AppIcons.block("classroom")?.let { rememberVectorPainter(it) } ?: painterResource(R.drawable.ic_school) }, Color(0xFF2FB67C), "공지·과제·자료") { go { onOpenTab(TabIndex.CLASSROOM) } },
        Destination("알리미", { AppIcons.block("alim")?.let { rememberVectorPainter(it) } ?: painterResource(R.drawable.ic_inbox) }, Color(0xFFFF6B6B), if (alimUnread > 0) "안 읽은 알리미 ${alimUnread}건" else "학교에서 온 알림", alimUnread) { go(onOpenAlim) },
        Destination("날씨", { rememberVectorPainter(AppIcons.block("weather") ?: MaterialExtraIcons.WbSunny) }, Color(0xFF3B9BF5), "시간별·주간 예보") { go(onOpenWeather) },
    )

/** 투어 그림·연습용 — 모든 기능의 블록 몇 개를 실제 모양 그대로. [names] 순서로 [columns] 칸씩, 누르면 [onOpen](이름). */
@Composable
internal fun AxisDestinationsPreview(names: List<String>, modifier: Modifier = Modifier, columns: Int = 2, onOpen: (String) -> Unit = {}) {
    val all = axisDestinations(3, {}, {}, {}) { }.map { d -> Destination(d.label, d.icon, d.color, d.hint, d.badge) { onOpen(d.label) } }
    DestinationGrid(names.mapNotNull { n -> all.find { it.label == n } }, columns, 124.dp, compact = false, modifier = modifier)
}

/** 칸 사이 가는 선 — 카드 안에서 칸을 나눕니다(카드 판 밝기에 맞춘 글자색 계열이라 사진 위에서도 은은하게). */
@Composable
private fun hairline(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

/**
 * 여덟 곳을 카드 하나에 [columns] 칸씩 — 칸마다 따로 띄운 카드가 여럿이면 빽빽해 보여(구멍이 숭숭) 한 장으로 묶고 가는 선으로
 * 나눕니다. 칸 높이는 [cellHeight].
 */
@Composable
private fun DestinationGrid(items: List<Destination>, columns: Int, cellHeight: Dp?, compact: Boolean, modifier: Modifier = Modifier) {
    OneUiCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        val line = hairline()
        items.chunked(columns).forEachIndexed { r, row ->
            if (r > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.7.dp).background(line))
            // 높이를 안 주면 카드 높이를 줄마다 똑같이 나눠 가집니다(전체 화면을 꽉 채울 때).
            Row(Modifier.fillMaxWidth().then(if (cellHeight != null) Modifier.height(cellHeight) else Modifier.weight(1f))) {
                for (c in 0 until columns) {
                    if (c > 0) Box(Modifier.padding(vertical = 16.dp).width(0.7.dp).fillMaxHeight().background(if (c < row.size) line else Color.Transparent))
                    val d = row.getOrNull(c)
                    if (d != null) DestinationCell(d, Modifier.weight(1f).fillMaxHeight(), compact) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 칸 하나 — 위에 색 원 아이콘과 이름을 나란히(오른쪽 끝에 안 읽은 수), 아래에 설명. 작은 화면([compact])에선 여백·아이콘을 줄입니다. */
@Composable
private fun DestinationCell(d: Destination, modifier: Modifier, compact: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.clickable(onClick = d.open).padding(if (compact) 12.dp else 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (compact) 28.dp else 32.dp).clip(CircleShape).background(d.color), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(d.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(d.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (d.badge > 0) Text(
                if (d.badge > 99) "99+" else "${d.badge}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary,
                modifier = Modifier.clip(CircleShape).background(scheme.primary).padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(d.hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
    }
}

/** 바로 가기 편집 — 고른 칸이 위(순서대로, 위·아래 화살표), 고르지 않은 칸이 아래. 스위치를 켜면 끝에 붙습니다. 바꾸는 즉시 저장. */
@Composable
private fun QuickLinkEditor(all: List<QuickLink>, chosen: List<String>, onChange: (List<String>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rows = chosen.mapNotNull { k -> all.find { it.key == k } } + all.filter { it.key !in chosen }
    com.skh.hihanaevo.ui.OneUiGroupColumn {
        rows.forEachIndexed { i, q ->
            val at = chosen.indexOf(q.key)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(q.color), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(q.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(q.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = if (at >= 0) scheme.onSurface else scheme.onSurfaceVariant)
                if (at >= 0) {
                    androidx.compose.material3.IconButton(onClick = { onChange(chosen.toMutableList().apply { add(at - 1, removeAt(at)) }) }, enabled = at > 0) {
                        androidx.compose.material3.Icon(Icons.Default.KeyboardArrowUp, contentDescription = "${q.label} 위로")
                    }
                    androidx.compose.material3.IconButton(onClick = { onChange(chosen.toMutableList().apply { add(at + 1, removeAt(at)) }) }, enabled = at < chosen.lastIndex) {
                        androidx.compose.material3.Icon(Icons.Default.KeyboardArrowDown, contentDescription = "${q.label} 아래로")
                    }
                }
                com.skh.hihanaevo.ui.OneUiSwitch(at >= 0, { on -> onChange(if (on) chosen + q.key else chosen - q.key) })
            }
            if (i != rows.lastIndex) com.skh.hihanaevo.ui.OneUiDivider()
        }
    }
}

/** 바로 가기 — 여덟 곳처럼 카드 한 장에 세 칸씩, 가는 선으로 나눕니다. */
@Composable
private fun QuickLinkGrid(links: List<QuickLink>, cellHeight: Dp, fill: Boolean = false, modifier: Modifier = Modifier) {
    OneUiCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        val line = hairline()
        links.chunked(3).forEachIndexed { r, row ->
            if (r > 0) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(0.7.dp).background(line))
            Row(Modifier.fillMaxWidth().then(if (fill) Modifier.weight(1f) else Modifier.height(cellHeight))) {
                for (c in 0 until 3) {
                    if (c > 0) Box(Modifier.padding(vertical = 14.dp).width(0.7.dp).fillMaxHeight().background(if (c < row.size) line else Color.Transparent))
                    val q = row.getOrNull(c)
                    if (q != null) QuickLinkCell(q, Modifier.weight(1f).fillMaxHeight(), cellHeight) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 바로 가기 칸 — 색 원 아이콘과 이름만. */
@Composable
private fun QuickLinkCell(q: QuickLink, modifier: Modifier, height: Dp) {
    Column(
        modifier
            .alpha(if (q.dim) 0.5f else 1f)
            .clickable(onClick = q.open)
            .padding(horizontal = 8.dp, vertical = if (height < 84.dp) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(q.color), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(q.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Text(q.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 투어 그림용 — 시간 배율의 오후 한 토막(13시~18시 반)을 예시 수업·면학·급식과 클래스룸·게시판 말풍선으로. 실제 [HourDay] 를
 * 그대로 쓰고 위쪽 날 머리와 오전은 잘라 냅니다. 누르면 아무 일도 없습니다.
 */
@Composable
internal fun TimelineDayPreview(modifier: Modifier = Modifier) {
    val today = remember { PlanStore.today() }
    val now = remember(today) { today.atTime(14, 25) }
    val colors = WidgetKindColors.defaults
    fun c(a: Accent) = Color(colors.getValue(a))
    val left = remember(today) {
        fun at(h: Int, m: Int) = today.atTime(h, m)
        listOf(
            AxisEntry(at(13, 10), at(14, 0), "확률과 통계", "B301", "", c(Accent.LESSON), null),
            AxisEntry(at(14, 10), at(15, 0), "AP 일반물리", "A201 · 실험실", "", c(Accent.LESSON), null),
            AxisEntry(at(15, 10), at(16, 0), "문학", "B204", "", c(Accent.LESSON), null),
            AxisEntry(at(16, 20), at(17, 50), "0타임 · 도서관", "2층 B-14", "", c(Accent.LIBRARY), null),
            AxisEntry(at(17, 50), at(19, 0), "저녁 급식", "순살허브치킨구이 · 보리밥", "급식", c(Accent.DINNER), null),
        )
    }
    val right = remember(today) {
        listOf(
            AxisEntry(today.atTime(14, 32), null, "다음 주 수행평가 범위 안내", "미적분Ⅱ", "클래스룸", AgendaSource.CLASSROOM.color, null),
            AxisEntry(today.atTime(16, 45), null, "도서관 야간 개방 안내", null, "게시판", AgendaSource.BOARD.color, null),
        )
    }
    val from = 13f
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                // 시간 칸은 맨 아래 — 그 위가 날 머리입니다.
                val grid = (HOUR_DP * (DAY_END - DAY_START)).roundToPx()
                val shift = (p.height - grid) + (HOUR_DP * (from - DAY_START)).roundToPx()
                layout(p.width, constraints.maxHeight) { p.place(0, -shift) }
            },
        ) {
            HourDay(today, today, now, left, right, emptyList(), onOpenAgenda = {}, onOpenSchedule = {})
        }
    }
}
