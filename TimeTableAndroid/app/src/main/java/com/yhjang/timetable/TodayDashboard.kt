package com.yhjang.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiBadge
import com.yhjang.timetable.ui.OneUiCard
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 오늘 탭 대시보드 — 작은 위젯들을 모아 놓은 것처럼 보이도록 2열 스태거드 그리드에
 * 카드들을 쌓습니다. 히어로(지금)와 오늘 남은 일정, 알리미는 전체 폭, 나머지는 반 폭입니다.
 */
@Composable
internal fun TodayDashboard(
    today: LocalDate,
    hasTimetable: Boolean,
    currentBlock: Block?,
    remainingMinutes: Long?,
    nextTitle: String?,
    nextRoom: String?,
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
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine, key = "now") {
            NowHeroCard(hasTimetable, currentBlock, remainingMinutes, nextTitle, nextRoom) {
                scope.launch { gridState.animateScrollToItem(0) }
            }
        }
        item(span = StaggeredGridItemSpan.FullLine, key = "upcoming") {
            UpcomingCard(upcomingGroups, supervisor)
        }
        // 면학 위치·급식은 반 폭 카드 두 장이지만 높이가 달라 보이던 문제를, 한 줄 전체를
        // 차지하는 Row 로 묶고 IntrinsicSize.Min 으로 더 큰 카드에 맞춰 해결합니다.
        item(span = StaggeredGridItemSpan.FullLine, key = "place_meal") {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlaceCard(
                    slots, places, supervisor, onEditSlot,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MealMiniCard(
                    meals, allergyCodes, isLoadingMeals,
                    onClick = { onNavigateToTab(2) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        item(span = StaggeredGridItemSpan.FullLine, key = "alim") {
            AlimMiniCard(alims, alimUnread, isLoadingAlim, onOpenAlim, onOpenAlimList)
        }
        item(key = "schedule") {
            ScheduleMiniCard(schedule, today, studentGrade, isLoadingSchedule, onOpenSchedule)
        }
        item(key = "board") {
            BoardMiniCard(boardPosts, boardCategoryLabel, isLoadingBoard, onOpenPost, onOpenBoardList)
        }
    }
}

/** 카드 공통 틀 — 작은 아이콘 + 제목 헤더가 있는 One UI 컨테이너(흰색/#17171A, 26dp 라운드). */
@Composable
private fun DashboardCard(
    title: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onCardClick: (() -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OneUiCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        onClick = onCardClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** 지금 — 현재 블록 상태·장소·남은 시간과 다음 일정을 한 장에 담은 히어로 카드. */
@Composable
private fun NowHeroCard(
    hasTimetable: Boolean,
    block: Block?,
    remainingMinutes: Long?,
    nextTitle: String?,
    nextRoom: String?,
    onClick: () -> Unit,
) {
    DashboardCard(title = "지금", icon = painterResource(R.drawable.ic_place), onCardClick = onClick) {
        // 시간표가 아직 한 번도 설치되지 않은 "로딩 중" 상태를 "오늘 일정이 모두 끝났습니다"
        // (블랭크 상태)와 구분합니다 — 안 그러면 로딩 중에도 이 문구가 떠서 앱이 멈춘 것처럼
        // 보였습니다.
        if (!hasTimetable) {
            Text(
                "시간표 불러오는 중…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            DashboardEmpty("잠시만 기다려주세요")
            return@DashboardCard
        }
        if (block == null || block.isBlank) {
            Text(
                "오늘 일정이 모두 끝났습니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            DashboardEmpty("편안한 밤 보내세요")
            return@DashboardCard
        }

        val status = block.statusLabel
        val title = block.title
        val headline = if (title.isEmpty() || title == status) status else "$status · $title"
        Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        block.room?.let { room ->
            Spacer(Modifier.height(4.dp))
            Text(
                room,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                block.timeRangeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            remainingMinutes?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    remainingText(it),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        nextTitle?.let { next ->
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("다음 · ")
                    append(next)
                    nextRoom?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun remainingText(minutes: Long): String =
    if (minutes >= 60) "%d시간 %02d분 남음".format(minutes / 60, minutes % 60)
    else "${minutes}분 남음"

/** 오늘 남은 일정 — 기존에 합쳐 보여주던 시간대·제목·장소/감독 줄을 전체 폭 카드로. */
@Composable
private fun UpcomingCard(groups: List<UpcomingGroup>, supervisor: String?) {
    DashboardCard(title = "오늘 남은 일정", icon = painterResource(R.drawable.ic_next)) {
        if (groups.isEmpty()) {
            DashboardEmpty("남은 일정이 없습니다")
        } else {
            groups.forEach { group ->
                UpcomingGroupRow(group, supervision = supervisor.takeIf { group.supervisionSlot })
            }
        }
    }
}

/** 면학 위치 — 오늘 슬롯을 탭하면 기존 장소 편집 다이얼로그가 열립니다. */
@Composable
private fun PlaceCard(
    slots: List<PlanSlot>,
    places: Map<PlanSlot, StudyPlace?>,
    supervisor: String?,
    onEditSlot: (PlanSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0타임은 포털 동기화로만 채워지는 자동 슬롯 — 배정이 있을 때만 평일 목록 맨 앞에 덧붙입니다.
    val isWeekday = slots.any { it == PlanSlot.weekday1 || it == PlanSlot.weekday2 }
    val shown = if (isWeekday && places[PlanSlot.weekday0] != null) listOf(PlanSlot.weekday0) + slots else slots

    DashboardCard(title = "면학 위치", icon = painterResource(R.drawable.ic_chair), modifier = modifier) {
        if (shown.isEmpty()) {
            DashboardEmpty("오늘은 면학이 없습니다")
        } else {
            shown.forEach { slot ->
                val place = places[slot]
                val auto = slot == PlanSlot.weekday0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(OneUi.CornerSmall))
                        // 자동 슬롯은 여기서 편집할 수 없어 탭 동작을 붙이지 않습니다.
                        .then(if (auto) Modifier else Modifier.clickable { onEditSlot(slot) })
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 제목·시간 열은 최소 폭을 확보하고 한 줄 고정 → 반 폭 카드에서도 글자 단위로 쪼개지지 않게 합니다.
                    Column(Modifier.widthIn(min = 54.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                slot.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (auto) {
                                Spacer(Modifier.width(4.dp))
                                OneUiBadge(
                                    "자동",
                                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            slot.timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // 장소 텍스트는 남은 폭을 차지하고 두 줄까지만 — 넘치면 말줄임.
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            place?.displayText ?: "미설정",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (place == null) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (place == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 평일 1타임 면학실은 학사일정에서 뽑은 감독 교사를 덧붙입니다.
                        if (slot == PlanSlot.weekday1 && place is StudyPlace.StudyRoom && supervisor != null) {
                            Text(
                                "감독 $supervisor",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 급식 — 끼니 라벨 + 첫 메뉴, 알레르기 경고, 캐시된 사진 썸네일. 탭하면 급식 탭으로. */
@Composable
private fun MealMiniCard(
    meals: DayMeals?,
    allergyCodes: Set<Int>,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = meals?.items.orEmpty()
    val photos = meals?.photos.orEmpty()
    val present = Meal.entries.filter { items[it.key].orEmpty().isNotEmpty() }

    DashboardCard(title = "급식", icon = painterResource(R.drawable.ic_meal), onCardClick = onClick, modifier = modifier) {
        when {
            present.isEmpty() && isLoading -> DashboardEmpty("불러오는 중")
            present.isEmpty() -> DashboardEmpty("식단이 없습니다")
            else -> present.forEach { meal ->
                val list = items[meal.key].orEmpty()
                val first = list.firstOrNull()?.name ?: "메뉴 없음"
                val flags = shortAllergyLabels(
                    list.flatMap { detectedAllergies(it.codes, it.name, allergyCodes) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                meal.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (flags.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "⚠ ${flags.joinToString("·")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            buildString {
                                append(first)
                                if (list.size > 1) append(" 외 ${list.size - 1}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HanaMealClient.mealPhotoThumbUrl(photos[meal.key])?.let { url ->
                        Spacer(Modifier.width(8.dp))
                        RemoteThumbnail(
                            urls = listOf(url),
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(OneUi.CornerSmall)),
                        )
                    }
                }
            }
        }
    }
}

/** 알리미 — 안 읽은 것 우선 상위 3건. 항목 탭은 기존 열기 동작, 헤더 탭은 알리미 화면. */
@Composable
private fun AlimMiniCard(
    alims: List<HanaAlim>,
    unread: Int,
    isLoading: Boolean,
    onOpenAlim: (HanaAlim) -> Unit,
    onOpenList: () -> Unit,
) {
    DashboardCard(
        title = "알리미",
        icon = rememberVectorPainter(Icons.Default.Notifications),
        subtitle = if (unread > 0) "안 읽음 $unread" else null,
        onHeaderClick = onOpenList,
    ) {
        when {
            alims.isEmpty() && isLoading -> DashboardEmpty("불러오는 중")
            alims.isEmpty() -> DashboardEmpty("알리미가 없습니다")
            else -> alims.sortedBy { it.read }.take(3).forEach { alim ->
                val isUnread = !alim.read
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(OneUi.CornerSmall))
                        .clickable { onOpenAlim(alim) }
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUnread) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            alim.content.ifEmpty { "(내용 없음)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                            color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val meta = listOfNotNull(alim.writer, alim.date).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, start = if (isUnread) 13.dp else 0.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 학사일정 — 시험 D-day 칩과 다가오는 일정 3건(색 점). 탭하면 학사 탭으로. */
@Composable
private fun ScheduleMiniCard(
    schedule: List<HanaScheduleEntry>,
    today: LocalDate,
    studentGrade: Int,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    DashboardCard(title = "학사일정", icon = painterResource(R.drawable.ic_academic), onCardClick = onClick) {
        examDday(schedule, today)?.let { exam ->
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OneUiBadge(
                    if (exam.days == 0L) "D-Day" else "D-${exam.days}",
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    exam.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val visible = filterSupervisionByGrade(schedule, studentGrade)
            .filter { it.code != "SG02020000" }
            .filter { !it.date.isBefore(today) }
            .sortedWith(compareBy({ it.date }, { it.name }))
            .take(3)

        when {
            visible.isEmpty() && isLoading -> DashboardEmpty("불러오는 중")
            visible.isEmpty() -> DashboardEmpty("일정이 없습니다")
            else -> visible.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(scheduleColor(entry.code)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            scheduleTitle(entry),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            shortDate(entry.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 게시판 — 최신 글 3건. 항목 탭은 기존 상세 웹뷰, 헤더 탭은 학사 탭 게시판으로. */
@Composable
private fun BoardMiniCard(
    posts: List<HanaBoardPost>,
    categoryLabel: String,
    isLoading: Boolean,
    onOpenPost: (HanaBoardPost) -> Unit,
    onOpenList: () -> Unit,
) {
    DashboardCard(
        title = "게시판",
        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.List),
        subtitle = categoryLabel,
        onHeaderClick = onOpenList,
    ) {
        when {
            posts.isEmpty() && isLoading -> DashboardEmpty("불러오는 중")
            posts.isEmpty() -> DashboardEmpty("게시글이 없습니다")
            else -> posts.take(3).forEach { post ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(OneUi.CornerSmall))
                        .clickable { onOpenPost(post) }
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.title.ifEmpty { "(제목 없음)" },
                            style = MaterialTheme.typography.bodySmall,
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardEmpty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
