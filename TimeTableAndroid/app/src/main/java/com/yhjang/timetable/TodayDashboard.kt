package com.yhjang.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiBadge
import com.yhjang.timetable.ui.OneUiBigValue
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiMetricIcon
import com.yhjang.timetable.ui.OneUiNoticeCard
import com.yhjang.timetable.ui.OneUiPageDots
import com.yhjang.timetable.ui.OneUiProgressBar
import com.yhjang.timetable.ui.OneUiRings
import com.yhjang.timetable.ui.OneUiTile
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 오늘 탭 — 삼성 헬스(One UI 9) 홈 구성을 따릅니다.
 *
 *  1. 인사이트 히어로: 지금 상태를 문장으로 풀어 쓴 카드, 옆으로 넘기면 "오늘 남은 일정" (페이지 점)
 *  2. 알림 카드: 학사시스템 미연동 시 "나중에 / 계정 연동" 알약 버튼
 *  3. "면학 위치" 활동 카드: 색 배지 아이콘 + 큰 장소 텍스트 행들, 오른쪽에 하루/현재 블록 진행 링
 *  4. 2열 타일: 급식·학사일정 / 알리미·게시판 — 작은 제목, 큰 값, 얇은 진행바, 주황 알림 점
 */
@Composable
internal fun TodayDashboard(
    today: LocalDate,
    hasTimetable: Boolean,
    currentBlock: Block?,
    remainingMinutes: Long?,
    nextTitle: String?,
    nextRoom: String?,
    dayProgress: Float?,
    blockProgress: Float?,
    slots: List<PlanSlot>,
    places: Map<PlanSlot, StudyPlace?>,
    supervisor: String?,
    upcomingGroups: List<UpcomingGroup>,
    meals: DayMeals?,
    allergyCodes: Set<Int>,
    isLoadingMeals: Boolean,
    alims: List<HanaAlim>,
    alimUnread: Int,
    isLoadingAlim: Boolean,
    schedule: List<HanaScheduleEntry>,
    isLoadingSchedule: Boolean,
    boardPosts: List<HanaBoardPost>,
    isLoadingBoard: Boolean,
    boardCategoryLabel: String,
    studentGrade: Int,
    needsAccount: Boolean,
    onConnectAccount: () -> Unit,
    onEditSlot: (PlanSlot) -> Unit,
    onOpenAlim: (HanaAlim) -> Unit,
    onOpenAlimList: () -> Unit,
    onOpenPost: (HanaBoardPost) -> Unit,
    onOpenBoardList: () -> Unit,
    onOpenSchedule: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // "나중에"를 누르면 이번 실행 동안만 알림 카드를 숨깁니다.
    var noticeDismissed by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") {
            InsightHero(
                hasTimetable = hasTimetable,
                block = currentBlock,
                remainingMinutes = remainingMinutes,
                nextTitle = nextTitle,
                nextRoom = nextRoom,
                upcomingGroups = upcomingGroups,
                supervisor = supervisor,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
            )
        }
        if (needsAccount && !noticeDismissed) {
            item(key = "notice") {
                OneUiNoticeCard(
                    text = "학사시스템 계정이 연동되지 않았습니다. 연동하면 면학 위치·학사일정·알리미를 자동으로 받아옵니다.",
                    secondaryText = "나중에",
                    onSecondary = { noticeDismissed = true },
                    primaryText = "계정 연동",
                    onPrimary = onConnectAccount,
                )
            }
        }
        item(key = "place") {
            PlaceActivityCard(
                slots = slots,
                places = places,
                supervisor = supervisor,
                dayProgress = dayProgress,
                blockProgress = blockProgress,
                onEditSlot = onEditSlot,
            )
        }
        item(key = "row_meal_schedule") {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MealTile(meals, allergyCodes, isLoadingMeals, onClick = { onNavigateToTab(2) }, modifier = Modifier.weight(1f).fillMaxHeight())
                ScheduleTile(schedule, today, studentGrade, isLoadingSchedule, onOpenSchedule, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
        item(key = "row_alim_board") {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AlimTile(alims, alimUnread, isLoadingAlim, onOpenAlim, onOpenAlimList, modifier = Modifier.weight(1f).fillMaxHeight())
                BoardTile(boardPosts, boardCategoryLabel, isLoadingBoard, onOpenPost, onOpenBoardList, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

// MARK: - 히어로

/**
 * 인사이트 히어로 — 삼성 헬스 "꾸준한 노력의 결실" 카드처럼 굵은 제목 + 문장형 본문, 아래에 페이지 점.
 * 1페이지는 지금 상태, 2페이지는 오늘 남은 일정입니다.
 */
@Composable
private fun InsightHero(
    hasTimetable: Boolean,
    block: Block?,
    remainingMinutes: Long?,
    nextTitle: String?,
    nextRoom: String?,
    upcomingGroups: List<UpcomingGroup>,
    supervisor: String?,
    onClick: () -> Unit,
) {
    val pageCount = 2
    val pagerState = rememberPagerState(pageCount = { pageCount })

    OneUiCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(212.dp),
        ) { page ->
            Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                when (page) {
                    0 -> NowInsight(hasTimetable, block, remainingMinutes, nextTitle, nextRoom)
                    else -> UpcomingInsight(upcomingGroups, supervisor)
                }
            }
        }
        OneUiPageDots(
            count = pageCount,
            current = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun NowInsight(
    hasTimetable: Boolean,
    block: Block?,
    remainingMinutes: Long?,
    nextTitle: String?,
    nextRoom: String?,
) {
    val (title, body) = when {
        !hasTimetable -> "시간표 불러오는 중" to "포털에서 오늘 시간표를 받아오고 있습니다. 잠시만 기다려 주세요."
        block == null || block.isBlank -> "오늘 일정이 모두 끝났습니다" to "남은 수업과 면학이 없습니다. 편안한 밤 보내세요."
        else -> {
            val status = block.statusLabel
            val name = block.title
            val headline = if (name.isEmpty() || name == status) status else "$status · $name"
            headline to buildString {
                block.room?.let { append("지금 장소는 ").append(it).append("입니다. ") }
                append(block.timeRangeText)
                remainingMinutes?.let { append(", ").append(remainingText(it)) }
                append(".")
                nextTitle?.let { next ->
                    append(" 다음은 ").append(next)
                    nextRoom?.let { append(" · ").append(it) }
                    append("입니다.")
                }
            }
        }
    }
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(12.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun UpcomingInsight(groups: List<UpcomingGroup>, supervisor: String?) {
    Text("오늘 남은 일정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (groups.isEmpty()) {
        Text("남은 일정이 없습니다", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val shown = groups.take(4)
    shown.forEach { group ->
        UpcomingGroupRow(group, supervision = supervisor.takeIf { group.supervisionSlot })
    }
    if (groups.size > shown.size) {
        Text(
            "외 ${groups.size - shown.size}개",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun remainingText(minutes: Long): String =
    if (minutes >= 60) "%d시간 %02d분 남음".format(minutes / 60, minutes % 60)
    else "${minutes}분 남음"

// MARK: - 면학 위치 활동 카드

/**
 * "일일 활동" 카드를 옮긴 면학 위치 카드 — 슬롯마다 색 배지 아이콘 + 큰 장소 텍스트, 오른쪽엔
 * 하루 진행(바깥 링)과 현재 블록 진행(안쪽 링). 행을 탭하면 장소 편집 다이얼로그가 열립니다.
 */
@Composable
private fun PlaceActivityCard(
    slots: List<PlanSlot>,
    places: Map<PlanSlot, StudyPlace?>,
    supervisor: String?,
    dayProgress: Float?,
    blockProgress: Float?,
    onEditSlot: (PlanSlot) -> Unit,
) {
    // 0타임은 포털 동기화로만 채워지는 자동 슬롯 — 배정이 있을 때만 평일 목록 맨 앞에 덧붙입니다.
    val isWeekday = slots.any { it == PlanSlot.weekday1 || it == PlanSlot.weekday2 }
    val shown = if (isWeekday && places[PlanSlot.weekday0] != null) listOf(PlanSlot.weekday0) + slots else slots
    val badgeColors = listOf(OneUi.Lime, OneUi.Sky, OneUi.Violet, OneUi.Amber)

    OneUiCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp)) {
        Text("면학 위치", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (shown.isEmpty()) {
                    Text("오늘은 면학이 없습니다", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                shown.forEachIndexed { index, slot ->
                    val place = places[slot]
                    val auto = slot == PlanSlot.weekday0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(OneUi.CornerSmall))
                            // 자동 슬롯은 여기서 편집할 수 없어 탭 동작을 붙이지 않습니다.
                            .then(if (auto) Modifier else Modifier.clickable { onEditSlot(slot) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OneUiMetricIcon(
                            painter = painterResource(if (slot == PlanSlot.weekday0) R.drawable.ic_bolt else R.drawable.ic_chair),
                            color = badgeColors[index % badgeColors.size],
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            OneUiBigValue(
                                value = place?.displayText ?: "미설정",
                                valueStyle = MaterialTheme.typography.titleLarge,
                                color = if (place == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            val meta = buildString {
                                append(slot.title).append(" · ").append(slot.timeText)
                                if (auto) append(" · 자동")
                                if (slot == PlanSlot.weekday1 && place is StudyPlace.StudyRoom && supervisor != null) {
                                    append(" · 감독 ").append(supervisor)
                                }
                            }
                            Text(
                                meta,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            OneUiRings(outer = dayProgress, inner = blockProgress)
        }
    }
}

// MARK: - 2열 타일

/** 급식 — 다음 끼니 라벨을 크게, 첫 메뉴 + 개수, 알레르기 경고. 탭하면 급식 탭으로. */
@Composable
private fun MealTile(
    meals: DayMeals?,
    allergyCodes: Set<Int>,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = meals?.items.orEmpty()
    val present = Meal.entries.filter { items[it.key].orEmpty().isNotEmpty() }
    val flags = present.flatMap { meal ->
        items[meal.key].orEmpty().flatMap { detectedAllergies(it.codes, it.name, allergyCodes) }
    }.let(::shortAllergyLabels)

    OneUiTile(title = "급식", showDot = flags.isNotEmpty(), onClick = onClick, modifier = modifier) {
        when {
            present.isEmpty() && isLoading -> TileEmpty("불러오는 중")
            present.isEmpty() -> TileEmpty("식단이 없습니다")
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiMetricIcon(painterResource(R.drawable.ic_meal), OneUi.Amber, size = 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        present.joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                present.forEach { meal ->
                    val list = items[meal.key].orEmpty()
                    Text(
                        buildString {
                            append(meal.label).append(" ").append(list.firstOrNull()?.name ?: "메뉴 없음")
                            if (list.size > 1) append(" 외 ${list.size - 1}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
                if (flags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ ${flags.joinToString("·")}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 학사일정 — 시험이 잡혀 있으면 D-day 를 크게 + 남은 날 진행바, 아니면 다음 일정 하나. */
@Composable
private fun ScheduleTile(
    schedule: List<HanaScheduleEntry>,
    today: LocalDate,
    studentGrade: Int,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exam = examDday(schedule, today)
    val next = filterSupervisionByGrade(schedule, studentGrade)
        .filter { it.code != "SG02020000" }
        .filter { !it.date.isBefore(today) }
        .sortedWith(compareBy({ it.date }, { it.name }))
        .firstOrNull()

    OneUiTile(title = "학사일정", onClick = onClick, modifier = modifier) {
        when {
            exam != null -> {
                OneUiBigValue(
                    value = if (exam.days == 0L) "D-Day" else "D-${exam.days}",
                    valueStyle = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    exam.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                // 30일 창 기준으로 시험이 가까워질수록 막대가 차오릅니다.
                OneUiProgressBar(fraction = 1f - (exam.days / 30f), color = OneUi.Coral)
            }
            next != null -> {
                OneUiBigValue(value = shortDate(next.date), valueStyle = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(scheduleColor(next.code)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        scheduleTitle(next),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            isLoading -> TileEmpty("불러오는 중")
            else -> TileEmpty("일정이 없습니다")
        }
    }
}

/** 알리미 — 안 읽은 개수를 크게, 가장 최근 항목 한 줄. 안 읽은 게 있으면 주황 점. */
@Composable
private fun AlimTile(
    alims: List<HanaAlim>,
    unread: Int,
    isLoading: Boolean,
    onOpenAlim: (HanaAlim) -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = alims.sortedBy { it.read }.firstOrNull()
    OneUiTile(
        title = "알리미",
        showDot = unread > 0,
        onClick = { if (latest != null && !latest.read) onOpenAlim(latest) else onOpenList() },
        modifier = modifier,
    ) {
        when {
            alims.isEmpty() && isLoading -> TileEmpty("불러오는 중")
            alims.isEmpty() -> TileEmpty("알리미가 없습니다")
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiMetricIcon(rememberVectorPainter(Icons.Default.Notifications), OneUi.Sky, size = 34.dp)
                    Spacer(Modifier.width(10.dp))
                    OneUiBigValue(
                        value = "$unread",
                        unit = if (unread > 0) "안 읽음" else "모두 읽음",
                        valueStyle = MaterialTheme.typography.headlineSmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                latest?.let { alim ->
                    Text(
                        alim.content.ifEmpty { "(내용 없음)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (!alim.read) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (!alim.read) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(alim.writer, alim.date).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 게시판 — 최신 글 제목을 크게, 작성자·날짜. 새 글(N)이 있으면 주황 점. 헤더 탭은 목록, 본문 탭은 글. */
@Composable
private fun BoardTile(
    posts: List<HanaBoardPost>,
    categoryLabel: String,
    isLoading: Boolean,
    onOpenPost: (HanaBoardPost) -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = posts.firstOrNull()
    OneUiTile(
        title = "게시판 · $categoryLabel",
        showDot = posts.any { it.isNew },
        onClick = { if (latest != null) onOpenPost(latest) else onOpenList() },
        modifier = modifier,
    ) {
        when {
            posts.isEmpty() && isLoading -> TileEmpty("불러오는 중")
            posts.isEmpty() -> TileEmpty("게시글이 없습니다")
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiMetricIcon(rememberVectorPainter(Icons.AutoMirrored.Filled.List), OneUi.Violet, size = 34.dp)
                    Spacer(Modifier.width(10.dp))
                    OneUiBigValue(value = "${posts.size}", unit = "건", valueStyle = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(12.dp))
                latest?.let { post ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.title.ifEmpty { "(제목 없음)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (ExamParser.hasExamKeyword(post.title)) {
                            Spacer(Modifier.width(6.dp))
                            OneUiBadge(
                                "시험",
                                container = MaterialTheme.colorScheme.errorContainer,
                                content = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    val meta = listOfNotNull(post.writer, post.date).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TileEmpty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 연속된 같은 장소 블록을 하나로 합친 항목 — 시간은 시작~끝 범위로 표기합니다 */
internal data class UpcomingGroup(
    val title: String,
    val room: String?,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** 1타임 면학실 블록을 포함하는 그룹인지 — 감독 교사를 덧붙일 대상. */
    val supervisionSlot: Boolean = false,
)

/**
 * 1타임(19:00) 면학실 블록인지. 평일 1타임만 대상이며, 같은 시각의 주말 3타임은
 * 일정명이 "3타임"이라 걸러집니다. 도서관·생활관 등은 대상이 아닙니다.
 */
private fun isWeekday1StudyRoom(block: Block): Boolean {
    val kind = block.kind as? BlockKind.StudyKind ?: return false
    if (kind.place !is StudyPlace.StudyRoom) return false
    return kind.session.start == Timetable.Sessions.weekday1.start && kind.session.name.contains("1타임")
}

/** 제목·교실이 같고 시간이 맞닿은 블록만 하나로 합칩니다 */
internal fun coalesceUpcoming(blocks: List<Block>): List<UpcomingGroup> {
    val result = mutableListOf<UpcomingGroup>()
    for (block in blocks) {
        val supervision = isWeekday1StudyRoom(block)
        val last = result.lastOrNull()
        if (last != null && last.title == block.title && last.room == block.room && last.end == block.start) {
            result[result.lastIndex] = last.copy(
                end = block.end,
                supervisionSlot = last.supervisionSlot || supervision,
            )
        } else {
            result += UpcomingGroup(block.title, block.room, block.start, block.end, supervision)
        }
    }
    return result
}

@Composable
private fun UpcomingGroupRow(group: UpcomingGroup, supervision: String?) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${group.start.format(formatter)} ~ ${group.end.format(formatter)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 제목은 weight(fill=false)로 남은 공간만 차지 → 장소 칩이 먼저 자기 폭을 확보해서
                // 과목명이 길어도(예: "데이터 과학과 인공지능") 장소가 세로로 눌려 쓰이지 않습니다.
                Text(
                    group.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                group.room?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            if (supervision != null) {
                Text(
                    "감독 $supervision",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
