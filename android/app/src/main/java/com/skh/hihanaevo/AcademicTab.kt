package com.skh.hihanaevo

import androidx.compose.foundation.lazy.rememberLazyListState
import com.skh.hihanaevo.ui.onPageVariantColor
import androidx.compose.material3.LocalContentColor
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiBadge
import com.skh.hihanaevo.ui.OneUiButton
import com.skh.hihanaevo.ui.OneUiButtonStyle
import com.skh.hihanaevo.ui.OneUiCard
import com.skh.hihanaevo.ui.OneUiFullScreen
import com.skh.hihanaevo.ui.OneUiGroup
import com.skh.hihanaevo.ui.OneUiLoading
import com.skh.hihanaevo.ui.OneUiSectionTitle
import com.skh.hihanaevo.ui.OneUiTextButton
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** 자격증명이 없거나 로그인 실패로 학사 데이터를 못 받을 때의 센티넬 — UI 가 연동 버튼을 띄웁니다. */
const val ACADEMIC_NEEDS_LOGIN = "학사시스템 연동 필요"

internal fun scheduleColor(code: String): Color = when (code) {
    "SG01010000" -> OneUi.Blue // 학사일정
    "SG02010000" -> Color(0xFF00A6B4) // 하나일정 — 청록(클래스룸 초록과 구분)
    "SG01020000" -> Color(0xFFFF3B30) // 공휴일
    SUPERVISION_CODE -> Color(0xFFA2845E) // 면학감독 — 갈색(게시판 노랑과 구분)
    "SG02020000" -> Color(0xFF8E8E93) // 시설사용
    else -> Color(0xFF5856D6)
}

internal fun scheduleLabel(code: String): String = when (code) {
    "SG01010000" -> "학사일정"
    "SG02010000" -> "하나일정"
    "SG01020000" -> "공휴일"
    SUPERVISION_CODE -> "면학감독"
    "SG02020000" -> "시설사용"
    else -> "일정"
}

/** 포털 알리미 화면에서 한 건을 미리 골라 여는 주소 — 번호는 [HanaAlim.portalIdx](att_idx). */
internal fun alimUrl(aimIdx: String): String = "https://hh.hana.hs.kr/main/mypage_alert/received.do?aimIdx=$aimIdx"

internal fun shortDate(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.KOREAN)
    return "${date.monthValue}/${date.dayOfMonth} ($dow)"
}

/** 날짜 그룹 헤더 — "9월 17일 (목)" */
internal fun dateHeader(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.KOREAN)
    return "${date.monthValue}월 ${date.dayOfMonth}일 ($dow)"
}

/** st_time/ed_time 은 "2026-09-17 09:00" 처럼 날짜가 앞에 붙어 올 수 있어 시각만 뽑습니다. */
internal fun scheduleTime(entry: HanaScheduleEntry): String? {
    val start = entry.startTime?.substringAfter(' ', entry.startTime)
    val end = entry.endTime?.substringAfter(' ', entry.endTime)
    return when {
        start != null && end != null -> "$start~$end"
        else -> start ?: end
    }
}

/** 학사 탭 — 학사일정 / 게시판 두 하위 탭을 한 화면에서 전환합니다. */
@Composable
fun AcademicTab(
    /** 0 학사일정, 1 게시판, 2 신청·내역 — 각각 하단 바의 일정·게시판·신청내역 탭에서 씁니다. */
    subTab: Int,
    schedule: List<HanaScheduleEntry>,
    scheduleLoading: Boolean,
    scheduleError: String?,
    onRetrySchedule: () -> Unit,
    studentGrade: Int,
    boardCategoryIndex: Int,
    onBoardCategoryChange: (Int) -> Unit,
    boardPosts: List<HanaBoardPost>,
    boardLoading: Boolean,
    boardError: String?,
    onRetryBoard: () -> Unit,
    onOpenPost: (HanaBoardPost) -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenSeats: (SeatService) -> Unit,
    onOpenAccount: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onFitsWithoutScroll: () -> Unit = {},
    /** 목록 맨 위에 얹을 내용(예: 일정 탭의 시간표/학사일정 칩). */
    topContent: (@Composable () -> Unit)? = null,
    /** 신청·내역 블록 순서 편집 중 (헤더의 연필 아이콘). */
    applyEditing: Boolean = false,
    /** 큰 화면에서 오른쪽 칸에 열려 있는 글 — 목록에서 그 줄을 표시합니다. */
    selectedPostUrl: String? = null,
    /** 큰 화면 신청·내역 — 내역을 오른쪽 칸에 (ApplyHistorySection 참고). */
    onShowHistory: ((ApplyService) -> Unit)? = null,
    historyShown: ApplyService? = null,
    /** 바뀌면 맨 위로 — 탭에 다시 들어올 때(페이지를 새로 만들지 않고 스크롤만 되돌림). */
    resetTick: Int = 0,
    /** 게시판별 안 읽은 글 수(세그먼트 배지) 와 '모두 읽음'. */
    boardUnreadCounts: Map<BoardCategory, Int> = emptyMap(),
    onMarkAllBoardsRead: () -> Unit = {},
    /** 학사일정 목록에 섞을 게시글·클래스룸 일정(통합 일정)과 탭했을 때. */
    agendaExtras: List<AgendaItem> = emptyList(),
    onOpenAgenda: (AgendaItem) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val applyScroll = rememberScrollState()
    LaunchedEffect(resetTick) {
        listState.scrollToItem(0)
        applyScroll.scrollTo(0)
    }
    // 신청·내역은 카드 몇 장뿐이라 시간표 탭처럼 화면에 다 들어가면 스크롤(과 헤더 접힘)을 두지 않습니다 —
    // 내용 높이를 재 보고 넘칠 때만 스크롤을 켭니다. 높이는 항상 스크롤 컨테이너 안에서(제한 없이) 재야 합니다:
    // 예전엔 안 넘친다고 보고 스크롤 없는 Column 에 넣어 재서 높이가 화면 크기로 잘려 보고됐고, 그래서 마지막
    // 외출·외박 카드의 버튼이 화면 밖으로 잘린 채 스크롤도 안 됐습니다.
    if (subTab == 2) {
        BoxWithConstraints(modifier) {
            var contentHeightPx by remember { mutableIntStateOf(0) }
            val availablePx = with(LocalDensity.current) {
                (maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()).roundToPx()
            }
            val fits = contentHeightPx in 1..availablePx
            LaunchedEffect(fits) { if (fits) onFitsWithoutScroll() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(applyScroll, enabled = !fits)
                    .padding(contentPadding),
            ) {
                Column(Modifier.fillMaxWidth().onSizeChanged { contentHeightPx = it.height }) {
                    topContent?.invoke()
                    ApplyHistorySection(onOpenWeb = onOpenWeb, onOpenSeats = onOpenSeats, editing = applyEditing, onShowHistory = onShowHistory, historyShown = historyShown)
                }
            }
        }
        return
    }

    LazyColumn(state = listState, modifier = modifier, contentPadding = contentPadding) {
        topContent?.let { content -> item { content() } }

        if (subTab == 0) {
            if (scheduleError != null) {
                item { AcademicErrorBlock(scheduleError, onRetrySchedule, onOpenAccount) }
            } else {
                val today = PlanStore.today()
                // 학사일정 + 게시글에서 뽑은 일정 + 클래스룸 과제 마감을 날짜순 한 목록으로. 2주 넘게 이어지는 상시 안내는
                // 몇 달 전 시작일 머리로 목록 맨 위를 차지해 뺍니다(타임라인·홈에는 그대로).
                val merged = Agenda.merge(schedule, studentGrade, agendaExtras, today).filterNot { it.longRunning }

                examDday(schedule, today)?.let { exam ->
                    item { ExamDdayCard(exam) }
                }

                if (scheduleLoading && merged.isEmpty()) {
                    item { LoadingBlock() }
                } else if (merged.isEmpty()) {
                    item { ScheduleEmptyBlock(onRetrySchedule) }
                }

                merged.groupBy { it.date }.forEach { (date, items) ->
                    item(key = "schedule-header-$date") { ScheduleDateHeader(date) }
                    item(key = "schedule-group-$date") {
                        // 같은 날 같은 선생님의 면학감독(0·1타임 등)은 한 줄에 시간만 이어 붙입니다.
                        val entries = items.mapNotNull { it.academic }
                        val academicRows: List<Any> = entries.groupBy { if (it.code == SUPERVISION_CODE) "sv:" + scheduleTitle(it) else "e:" + System.identityHashCode(it) }
                            .values.map { group -> group.first() to group.mapNotNull { scheduleTime(it) }.distinct() }
                        val rows = academicRows + items.filter { it.academic == null }
                        GroupedContainer(rows) { row ->
                            @Suppress("UNCHECKED_CAST")
                            if (row is AgendaItem) AgendaRow(row, onOpenAgenda)
                            else (row as Pair<HanaScheduleEntry, List<String>>).let { (entry, times) -> ScheduleRow(entry, times) }
                        }
                    }
                }
            }
        } else if (subTab == 1) {
            item(key = "board-picker") {
                BoardSegmentBar(boardCategoryIndex, boardUnreadCounts, onBoardCategoryChange, Modifier.padding(top = 2.dp))
                // 안 읽은 글이 있으면 그 아래 오른쪽에 '모두 읽음' — 삼성 이메일 목록 머리와 같은 자리.
                val totalUnread = boardUnreadCounts.values.sum()
                Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    if (totalUnread > 0) OneUiTextButton(text = "모두 읽음", onClick = onMarkAllBoardsRead, color = LocalContentColor.current)
                    else Spacer(Modifier.height(8.dp))
                }
            }

            if (boardError != null) {
                item { AcademicErrorBlock(boardError, onRetryBoard, onOpenAccount) }
            } else {
                val category = BoardCategory.entries[boardCategoryIndex]
                if (boardLoading && boardPosts.isEmpty()) {
                    item { LoadingBlock() }
                } else if (boardPosts.isEmpty()) {
                    item { EmptyBlock("${category.label} 게시글이 없습니다") }
                }
                if (boardPosts.isNotEmpty()) {
                    item(key = "board-group") {
                        GroupedContainer(boardPosts) { post -> BoardRow(post, onOpenPost, selected = post.url == selectedPostUrl) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamDdayCard(exam: ExamDday) {
    // 시험 D-day 는 강조색 배경의 카드 하나로 눈에 띄게 — One UI 캘린더의 "다음 일정" 배너와 같은 톤입니다.
    OneUiCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    exam.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    // 남은 날은 오른쪽 큰 D-day 가 보여 줘 날짜만.
                    shortDate(exam.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
            Text(
                if (exam.days == 0L) "D-Day" else "D-${exam.days}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ScheduleDateHeader(date: LocalDate) {
    OneUiSectionTitle(dateHeader(date))
}

/** One UI "Containers" 스타일 — 같은 날짜의 행들을 각자 카드로 나누지 않고, 한 컨테이너
 * 안에 얇은 구분선으로만 나눕니다. 이 컴포저블 자체는 행 내용만 그리고, 컨테이너·구분선은
 * 호출부(같은 날짜로 묶은 곳)에서 담당합니다. */
@Composable
private fun ScheduleRow(entry: HanaScheduleEntry, times: List<String> = listOfNotNull(scheduleTime(entry))) {
    // 종류는 오른쪽 색 글자(하나일정·학사일정·면학감독)로 보여 줘, 같은 뜻의 색 점은 두지 않습니다.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(scheduleTitle(entry), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            val meta = buildList {
                if (!entry.allDay && times.isNotEmpty()) add(times.joinToString(", "))
                entry.place?.let { add(it) }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = onPageVariantColor(),
                )
            }
        }
        Text(
            scheduleLabel(entry.code),
            style = MaterialTheme.typography.labelSmall,
            color = scheduleColor(entry.code),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 게시글·클래스룸에서 온 일정 한 줄 — 왼쪽에 이름과 시각·출처, 오른쪽에 출처 색 글자(게시판·클래스룸). 마감이면 이름 앞에 '마감' 배지.
 * 탭하면 그 글(또는 과제)을 엽니다.
 */
@Composable
private fun AgendaRow(item: AgendaItem, onOpen: (AgendaItem) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.url != null) Modifier.clickable { onOpen(item) } else Modifier)
            .padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.deadline) {
                    OneUiBadge("마감", container = MaterialTheme.colorScheme.error.copy(alpha = 0.14f), content = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                }
                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = buildList {
                item.time?.let { add(it) }
                item.endDate?.let { add("~" + shortDate(it)) }
                item.detail?.takeIf { it.isNotEmpty() }?.let { add(it) }
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = onPageVariantColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(item.source.label, style = MaterialTheme.typography.labelSmall, color = item.source.color, fontWeight = FontWeight.SemiBold)
    }
}

/** [ScheduleRow]/[BoardRow] 처럼 컨테이너 없이 내용만 그리는 행들을 One UI 그룹 컨테이너로 묶습니다. */
@Composable
private fun <T> GroupedContainer(items: List<T>, row: @Composable (T) -> Unit) {
    OneUiGroup(items = items, modifier = Modifier.padding(vertical = 4.dp), row = row)
}

/**
 * 면학감독은 일정명 대신 담당 교사를 보여줍니다 — 학년 접두사(`2.`)는 떼고 이름만 씁니다.
 * 교사명이 없는 예외 응답은 일정명으로 폴백합니다.
 */
internal fun scheduleTitle(entry: HanaScheduleEntry): String {
    if (entry.code != SUPERVISION_CODE) return entry.name
    return entry.displayTeacher ?: entry.name
}

@Composable
private fun BoardRow(post: HanaBoardPost, onOpenPost: (HanaBoardPost) -> Unit, selected: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onOpenPost(post) }
            .padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                post.title.ifEmpty { "(제목 없음)" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 'N' 은 포털의 새 글 표시가 아니라 "내가 아직 안 연 글" — revision 을 읽어 읽음 처리 즉시 사라집니다.
            val context = LocalContext.current
            val unread = remember(post, BoardReadStore.revision) { !BoardReadStore.isRead(context, post) }
            if (unread) {
                Spacer(Modifier.width(6.dp))
                Text("N", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        }
        // AI 한 줄 요약 알약 — 목록을 여는 동안 한 건씩 채워지며, 없는 글은 자리를 비워 두지 않고 예전처럼 보입니다.
        PostSummarizer.line(LocalContext.current, post.url)?.let { line ->
            AiSummaryPill(line, Modifier.padding(top = 9.dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.grades.isNotEmpty()) {
                Text(
                    post.grades.joinToString("·") { it.removeSuffix("학년").trim() } + "학년",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                listOfNotNull(post.writer, prettyPortalDate(post.date)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = onPageVariantColor(),
            )
        }
    }
}

/**
 * 포털이 주는 시각 문자열("2026-10-19 10:57:44.0", "2026-10-19")을 읽기 쉬운 꼴로 — 올해면 "10.19 10:57", 지난해면 "2025.10.19".
 * 못 읽는 꼴은 그대로 돌려줍니다.
 */
internal fun prettyPortalDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return raw
    val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2}))?").find(raw.trim()) ?: return raw
    val (y, mo, d, h, mi) = m.destructured
    val thisYear = y.toInt() == PlanStore.today().year
    val date = if (thisYear) "${mo.toInt()}.${d.toInt()}" else "$y.${mo.toInt()}.${d.toInt()}"
    return if (h.isNotEmpty() && thisYear) "$date ${h.toInt()}:$mi" else date
}

/** 앱 내 웹뷰로 열 페이지 — 게시글·신청·내역 페이지가 공유합니다. */
data class HanaWebPage(
    val url: String,
    val title: String,
    /** 신청·내역처럼 포털 상태를 바꾸는 페이지면 닫을 때 면학 배정을 다시 받아옵니다. */
    val resyncOnClose: Boolean = false,
)

@Composable
internal fun AcademicErrorBlock(error: String, onRetry: () -> Unit, onOpenAccount: () -> Unit) {
    OneUiCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            if (error == ACADEMIC_NEEDS_LOGIN) ACADEMIC_NEEDS_LOGIN else "불러오지 못했습니다",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (error == ACADEMIC_NEEDS_LOGIN) "하이하나 계정을 등록하면 학사일정과 게시판을 볼 수 있습니다." else error,
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
        )
        Spacer(Modifier.height(14.dp))
        if (error == ACADEMIC_NEEDS_LOGIN) {
            OneUiButton(text = "계정 등록", onClick = onOpenAccount)
        } else {
            OneUiButton(text = "다시 시도", onClick = onRetry, style = OneUiButtonStyle.Neutral)
        }
    }
}

@Composable
internal fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        OneUiLoading(size = 28.dp, stroke = 3.dp)
    }
}

@Composable
internal fun EmptyBlock(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = onPageVariantColor(),
        modifier = Modifier.padding(vertical = 32.dp),
    )
}

/** 학사일정이 비었을 때 — 포털 파라미터가 안 맞을 수 있으니 다시 시도 버튼을 함께 둡니다. */
@Composable
private fun ScheduleEmptyBlock(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
        Text(
            "예정된 학사일정이 없습니다",
            style = MaterialTheme.typography.bodyLarge,
            color = onPageVariantColor(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "학사일정이 계속 비어 있으면 다시 시도해 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
        )
        Spacer(Modifier.height(12.dp))
        OneUiButton(text = "다시 시도", onClick = onRetry, style = OneUiButtonStyle.Neutral)
    }
}

/** 알리미 전체 화면 — 상단 앱바 종 아이콘에서 열립니다. 탭하면 읽음 처리 후 포털 웹으로 이동합니다. */
@Composable
fun AlimScreen(
    alims: List<HanaAlim>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenAlim: (HanaAlim) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit,
    /** 열지 않고 한 건만 읽음 — 안 읽은 줄 오른쪽의 '읽음'. */
    onMarkRead: (HanaAlim) -> Unit = {},
    /**
     * 큰 화면 두 칸 — 목록 | 원문. 항목을 누르면 [onOpenAlim] 대신 [onReadInPane](읽음 처리)만 부르고 원문은 오른쪽 칸에
     * [detailUrl] 로 엽니다. null 이면 폰처럼 목록만.
     */
    detailUrl: ((HanaAlim) -> String)? = null,
    onReadInPane: (HanaAlim) -> Unit = {},
    /** 오른쪽 칸의 '하이하나에서 보기' — 앱 안 웹 화면으로 포털 알리미를 엽니다. */
    onOpenBrowser: (String) -> Unit = {},
) {
    val hasUnread = alims.any { !it.read }
    val unreadCount = alims.count { !it.read }
    val wide = detailUrl != null
    var selected by remember { mutableStateOf<HanaAlim?>(null) }
    OneUiFullScreen(
        title = "알리미",
        subtitle = if (unreadCount > 0) "안 읽음 $unreadCount" else null,
        onDismiss = onDismiss,
        actions = {
            IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "새로고침") }
        },
        maxContentWidth = if (wide) 1400.dp else com.skh.hihanaevo.ui.Adaptive.ReadingWidth,
    ) { toolbar ->
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(460.dp).fillMaxHeight()) {
                    AlimListBody(alims, isLoading, error, onRetry, onOpenAccount, onMarkAllRead, onMarkRead, hasUnread, toolbar, selectedId = selected?.id, compact = true) { alim ->
                        selected = alim
                        onReadInPane(alim)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(top = toolbar.calculateTopPadding(), bottom = toolbar.calculateBottomPadding())) {
                    val open = selected
                    if (open == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("왼쪽에서 알림을 고르세요", style = MaterialTheme.typography.bodyLarge, color = onPageVariantColor())
                        }
                    } else key(open.id) {
                        AlimDetailPane(open, onOpenPortal = { onOpenBrowser(detailUrl!!(open)) })
                    }
                }
            }
        } else {
            AlimListBody(alims, isLoading, error, onRetry, onOpenAccount, onMarkAllRead, onMarkRead, hasUnread, toolbar, selectedId = null, onOpenAlim = onOpenAlim)
        }
    }
}

@Composable
private fun AlimListBody(
    alims: List<HanaAlim>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenAccount: () -> Unit,
    onMarkAllRead: () -> Unit,
    onMarkRead: (HanaAlim) -> Unit,
    hasUnread: Boolean,
    toolbar: PaddingValues,
    selectedId: String?,
    compact: Boolean = false,
    onOpenAlim: (HanaAlim) -> Unit,
) {
        val top = toolbar.calculateTopPadding()
        when {
            error != null -> Box(Modifier.padding(start = OneUi.PagePadding, end = OneUi.PagePadding, top = top)) {
                AcademicErrorBlock(error, onRetry, onOpenAccount)
            }
            isLoading && alims.isEmpty() -> Box(Modifier.padding(top = top)) { LoadingBlock() }
            alims.isEmpty() -> Box(Modifier.padding(start = OneUi.PagePadding, end = OneUi.PagePadding, top = top)) { EmptyBlock("알리미가 없습니다") }
            // 알리미는 항목마다 카드를 따로 두지 않고 One UI 메시지 목록처럼 한 컨테이너에 행으로 쌓습니다.
            else -> LazyColumn(contentPadding = PaddingValues(start = OneUi.PagePadding, end = OneUi.PagePadding, top = top + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OneUiTextButton(
                            text = "모두 읽음",
                            onClick = onMarkAllRead,
                            enabled = hasUnread,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item(key = "alim-group") {
                    OneUiGroup(items = alims) { alim ->
                        // 두 칸이면 오른쪽에 전문이 있으니 목록은 세 줄까지만.
                        AlimRow(alim, selected = alim.id == selectedId, maxLines = if (selectedId != null || compact) 3 else Int.MAX_VALUE, onMarkRead = { onMarkRead(alim) }) { onOpenAlim(alim) }
                    }
                }
            }
        }
}

@Composable
private fun AlimRow(alim: HanaAlim, selected: Boolean = false, maxLines: Int = Int.MAX_VALUE, onMarkRead: () -> Unit = {}, onClick: () -> Unit) {
    val unread = !alim.read
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 안 읽은 항목은 왼쪽에 강조색 점 — 읽으면 점 자리는 비워 두어 텍스트 정렬이 흔들리지 않게 합니다.
        Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
            if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                alim.content.ifEmpty { "(내용 없음)" },
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (unread) MaterialTheme.colorScheme.onSurface else onPageVariantColor(),
            )
            val meta = listOfNotNull(alim.writer, prettyPortalDate(alim.date)).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = onPageVariantColor(),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        // 열지 않고 이것만 읽음으로 — 삼성 메시지의 '읽음으로 표시'처럼 안 읽은 줄에만.
        if (unread) {
            Spacer(Modifier.width(8.dp))
            OneUiTextButton(text = "읽음", onClick = onMarkRead, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 게시글 상세 — in-app WebView. HanaPortalClient 의 OkHttp 세션 쿠키를
 * android.webkit.CookieManager 로 복사한 뒤 로드해야 로그인된 페이지가 뜹니다.
 * 실패하면 "브라우저로 열기" 폴백을 제공합니다.
 */
@Composable
fun BoardDetailScreen(
    url: String,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit,
    title: String = "게시글",
    /** 큰 화면 두 칸의 오른쪽 칸에 그릴 때 — 전체 화면·뒤로 가기 없이 칸 안에. */
    embedded: Boolean = false,
) {
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // 화면 뼈대는 바로 그리고, 세션 확인(대개 즉시 통과)이 끝난 뒤에야 웹뷰를 붙여 쿠키를 복사합니다 —
    // 이걸 화면을 열기 전에 하면 열리기까지 1초 가까이 멈춰 보였습니다.
    var sessionReady by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 포털이 가끔 세션을 끊어 로그인 페이지로 튕기면(재현이 어려운 간헐적 문제), 한 번은 앱이 다시 로그인하고
    // 쿠키를 새로 심은 뒤 원래 글을 다시 읽어 스스로 복구합니다. 두 번째도 실패하면 로그인 페이지를 그대로 둡니다.
    var recoveredOnce by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        HanaPortalClient.get().ensureLoggedIn(context)
        sessionReady = true
    }

    // onPageFinished 의 url 파라미터와 이름이 겹치므로 원래 열려던 주소를 따로 잡아 둡니다.
    val targetUrl = url

    BackHandler(enabled = !embedded, onBack = onDismiss)

    PaneOrFullScreen(
        embedded = embedded,
        title = title,
        onDismiss = onDismiss,
        actions = {
            OneUiTextButton(text = "브라우저로 열기", onClick = onOpenBrowser, color = MaterialTheme.colorScheme.primary)
        },
    ) { toolbar ->
        if (failed) {
            Box(Modifier.padding(toolbar).padding(OneUi.PagePadding)) {
                OneUiCard(modifier = Modifier.fillMaxWidth()) {
                    Text("게시글을 불러오지 못했습니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "포털 로그인 세션이 만료됐거나 페이지가 웹뷰를 막고 있을 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = onPageVariantColor(),
                    )
                    Spacer(Modifier.height(14.dp))
                    OneUiButton(text = "브라우저로 열기", onClick = onOpenBrowser)
                }
            }
        } else {
            // 웹 페이지는 흰 바탕이라 회색 페이지 배경 위에서 카드처럼 위쪽만 둥글게 잘라 얹습니다.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(toolbar)
                    .padding(horizontal = OneUi.PagePadding)
                    .clip(RoundedCornerShape(topStart = OneUi.CornerLarge, topEnd = OneUi.CornerLarge))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (sessionReady) AndroidView(
                    factory = { ctx ->
                        // loadUrl 전에 쿠키를 심어야 인증된 페이지가 뜹니다.
                        HanaPortalClient.get().syncCookiesToWebView()
                        WebView(ctx).apply {
                            // Compose 의 AndroidView 는 뷰의 layoutParams 가 WRAP_CONTENT 면 높이를 AT_MOST 로 재는데,
                            // 그러면 크로미움이 "높이 미정"으로 보고 페이지의 100vh 를 0 으로 계산합니다 — 포털 모달
                            // (교과교실 신청 등)이 높이 0 으로 접혀 머리글만 보이던 원인. MATCH_PARENT 로 고정합니다.
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // 포털이 세션을 UA 에 묶을 수 있어 OkHttp 와 같은 UA 로 맞춥니다.
                            settings.userAgentString = HanaPortalClient.UA
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    val bouncedToLogin = url?.contains("/main/login/") == true
                                    if (bouncedToLogin) {
                                        if (!recoveredOnce && view != null) {
                                            recoveredOnce = true
                                            loading = true
                                            scope.launch {
                                                val ok = runCatching { HanaPortalClient.get().relogin(context) }.isSuccess
                                                if (ok) {
                                                    HanaPortalClient.get().syncCookiesToWebView()
                                                    view.loadUrl(targetUrl)
                                                } else {
                                                    loading = false
                                                }
                                            }
                                        }
                                        // 로그인 페이지의 비로그인 세션 쿠키를 앱 쪽으로 가져오면 안 됩니다.
                                        return
                                    }
                                    // 웹뷰가 받은 최신 세션 쿠키를 앱 쪽으로 되가져와 다음 게시글도 같은 세션으로 엽니다.
                                    HanaPortalClient.get().syncCookiesFromWebView()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        failed = true
                                        loading = false
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loading = newProgress < 100
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }
    }
}


/** 큰 화면 알리미 오른쪽 칸 — 알림 전문과 보낸 사람·시각. 목록에 이미 전문이 있어 포털 페이지 대신 앱 화면으로 보여 줍니다. */
@Composable
private fun AlimDetailPane(alim: HanaAlim, onOpenPortal: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        PaneTitle(
            title = "알리미",
            subtitle = listOfNotNull(alim.writer, prettyPortalDate(alim.date)).joinToString(" · ").ifEmpty { null },
            actions = { OneUiTextButton(text = "하이하나에서 보기", onClick = onOpenPortal, color = MaterialTheme.colorScheme.primary) },
        )
        OneUiCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(
                alim.content.ifEmpty { "(내용 없음)" },
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            )
        }
    }
}
