package com.yhjang.timetable

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import com.yhjang.timetable.ui.onPageVariantColor
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.yhjang.timetable.widget.iconResFor
import com.yhjang.timetable.widget.WidgetKindColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.isDark
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
    /** 현재 블록 진행률 0..1 — '지금' 카드 아래 진행바. 블록이 없으면 null. */
    blockProgress: Float?,
    /** 귀가 기간 안내 — 있으면 '지금' 카드가 일정 대신 이 문구를 보여줍니다. */
    homeStayNotice: HomeStayNotice?,
    /** 귀가 기간 안내 아래에 붙는 서울 날씨·귀교 후 첫 일정·시험. */
    homeStayExtras: HomeStayExtras? = null,
    slots: List<PlanSlot>,
    places: Map<PlanSlot, StudyPlace?>,
    supervisor: String?,
    upcomingGroups: List<UpcomingGroup>,
    /** 종류별 색(설정 → 위젯의 '종류별 색') — 남은 일정 아이콘 원 색. */
    kindColors: Map<Accent, Int>,
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
    /** 학사시스템 계정이 없을 때 — '지금' 카드가 연동 안내가 되고 탭하면 [onConnectAccount]. */
    needsAccount: Boolean,
    onConnectAccount: () -> Unit,
    onOpenAlim: (HanaAlim) -> Unit,
    onOpenAlimList: () -> Unit,
    onOpenPost: (HanaBoardPost) -> Unit,
    onOpenBoardList: () -> Unit,
    onOpenSchedule: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    /** 카드 순서 편집 중 (헤더의 연필) — 그리드 대신 위·아래 버튼 목록. '지금' 카드는 늘 맨 위라 빠집니다. */
    editingOrder: Boolean = false,
    /**
     * 카드 열 수 — 폰은 1(카드마다 전체 폭), 큰 화면은 2·3. 3열이면 첫 줄은 '지금'이 두 칸, 순서상 첫 카드가 한 칸
     * (삼성 날씨 태블릿처럼 큰 카드 옆에 작은 카드), 그다음부터는 한 칸씩 짧은 열에 쌓입니다.
     */
    lanes: Int = 1,
    /** 바뀌면 맨 위로 — 탭에 다시 들어올 때. */
    resetTick: Int = 0,
) {
    val context = LocalContext.current
    val gridState = rememberLazyStaggeredGridState()
    val listState = rememberLazyListState()
    LaunchedEffect(resetTick) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }
    val scope = rememberCoroutineScope()

    // 귀가 기간 날씨 칸을 누르면 귀교 시각까지 시간별 서울 날씨 (MAX = 귀교 시각을 모를 때 받은 예보 끝까지).
    var weatherUntil by remember { mutableStateOf<java.time.LocalDateTime?>(null) }
    weatherUntil?.let { until ->
        SeoulWeatherScreen(until.takeIf { it != java.time.LocalDateTime.MAX }, onDismiss = { weatherUntil = null })
    }
    var homeOrder by remember { mutableStateOf(HomeOrderStore.get(context)) }
    if (editingOrder) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
            OrderEditor(
                order = homeOrder,
                defaultOrder = HomeOrderStore.defaultOrder,
                label = HomeOrderStore::label,
                onMove = { from, to ->
                    homeOrder = homeOrder.toMutableList().apply { add(to, removeAt(from)) }
                    HomeOrderStore.set(context, homeOrder)
                },
                onReset = {
                    homeOrder = HomeOrderStore.defaultOrder
                    HomeOrderStore.set(context, homeOrder)
                },
            )
        }
        return
    }
    val homeStayIdle = homeStayNotice != null
    val nowCard: @Composable (Modifier) -> Unit = { cardModifier ->
        NowHeroCard(
            hasTimetable, needsAccount, currentBlock, remainingMinutes, nextTitle, nextRoom, blockProgress, homeStayNotice, homeStayExtras,
            onOpenWeather = { weatherUntil = homeStayExtras?.backAt ?: java.time.LocalDateTime.MAX },
            modifier = cardModifier,
        ) {
            if (needsAccount) onConnectAccount() else scope.launch { if (lanes == 1) listState.animateScrollToItem(0) else gridState.animateScrollToItem(0) }
        }
    }
    // 나머지 카드는 사용자가 정한 순서대로(헤더의 연필 → 위·아래). '지금' 카드는 늘 맨 위.
    val cards: List<Pair<String, @Composable (Modifier) -> Unit>> = buildList {
        homeOrder.forEach { key ->
            when (key) {
                // 귀가 기간엔 '지금' 카드가 이미 귀가를 알리므로, 비어 있는 남은 일정 카드는 빼서 같은 말을 되풀이하지 않습니다.
                HomeOrderStore.UPCOMING -> if (!(homeStayIdle && upcomingGroups.isEmpty())) add("upcoming" to { m ->
                    UpcomingCard(upcomingGroups, supervisor, kindColors, emptyText = "남은 일정이 없습니다", modifier = m)
                })
                // 면학 위치는 '오늘 남은 일정'과 같은 내용이라 홈에서는 빼고, 급식을 한 장으로 둡니다.
                HomeOrderStore.MEAL -> if (!(homeStayIdle && !isLoadingMeals && meals?.items.orEmpty().values.all { it.isEmpty() })) add("meal" to { m ->
                    MealMiniCard(
                        meals, allergyCodes, isLoadingMeals,
                        onClick = { onNavigateToTab(TabIndex.MEAL) },
                        modifier = m.fillMaxWidth(),
                    )
                })
                HomeOrderStore.BOARD -> add("board" to { m ->
                    BoardMiniCard(boardPosts, boardCategoryLabel, isLoadingBoard, onOpenPost, onOpenBoardList, modifier = m)
                })
                // 폰은 학사일정과 알리미(최근 2건)를 반 폭씩 한 줄에, 큰 화면은 각자 한 칸.
                HomeOrderStore.SCHEDULE_ALIM -> if (lanes > 1) {
                    add("schedule" to { m ->
                        ScheduleMiniCard(schedule, today, studentGrade, isLoadingSchedule, onOpenSchedule, modifier = m.fillMaxWidth(), showExam = homeStayExtras?.exam == null)
                    })
                    add("alim" to { m ->
                        AlimMiniCard(alims, alimUnread, isLoadingAlim, onOpenAlim, onOpenAlimList, limit = 3, modifier = m.fillMaxWidth())
                    })
                } else add("schedule_alim" to { m ->
                    Row(
                        modifier = m.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ScheduleMiniCard(
                            schedule, today, studentGrade, isLoadingSchedule, onOpenSchedule,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            showExam = homeStayExtras?.exam == null,
                        )
                        AlimMiniCard(
                            alims, alimUnread, isLoadingAlim, onOpenAlim, onOpenAlimList,
                            limit = 2,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                })
            }
        }
    }
    val gap = if (lanes > 1) 16.dp else 12.dp
    if (lanes == 1) {
        // 폰은 카드가 모두 전체 폭이라 지그재그 격자가 필요 없습니다 — 보통 목록이 측정이 가볍습니다.
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(key = "now") { nowCard(Modifier) }
            items(cards, key = { it.first }) { (_, card) -> card(Modifier) }
        }
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (lanes > 1) lanes else 2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalItemSpacing = gap,
    ) {
        if (lanes >= 3 && cards.isNotEmpty()) {
            // 첫 줄: '지금'(두 칸) + 순서상 첫 카드(한 칸), 높이는 둘 중 큰 쪽에 맞춥니다.
            item(span = StaggeredGridItemSpan.FullLine, key = "now") {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Box(Modifier.weight(2f).fillMaxHeight()) { nowCard(Modifier.fillMaxHeight()) }
                    Box(Modifier.weight(1f).fillMaxHeight()) { cards[0].second(Modifier.fillMaxHeight()) }
                }
            }
            cards.drop(1).forEach { (key, card) -> item(key = key) { card(Modifier) } }
        } else {
            item(span = StaggeredGridItemSpan.FullLine, key = "now") { nowCard(Modifier) }
            val span = if (lanes > 1) StaggeredGridItemSpan.SingleLane else StaggeredGridItemSpan.FullLine
            cards.forEach { (key, card) -> item(span = span, key = key) { card(Modifier) } }
        }
    }
}

/** 카드 공통 틀 — 작은 아이콘 + 제목 헤더가 있는 One UI 컨테이너(흰색/#17171A). */
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
            // 카드마다 성격에 맞는 작은 아이콘을 제목 왼쪽에 — 지금(위치)·남은 일정(화살표)·급식·게시판·학사·알리미.
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
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = onPageVariantColor(),
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
    needsAccount: Boolean,
    block: Block?,
    remainingMinutes: Long?,
    nextTitle: String?,
    nextRoom: String?,
    blockProgress: Float?,
    homeStayNotice: HomeStayNotice?,
    homeStayExtras: HomeStayExtras?,
    onOpenWeather: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    DashboardCard(title = "지금", icon = painterResource(R.drawable.ic_place), onCardClick = onClick, modifier = modifier) {
        // 시간표가 아직 한 번도 설치되지 않은 "로딩 중" 상태를 "오늘 일정이 모두 끝났습니다"
        // (블랭크 상태)와 구분합니다 — 안 그러면 로딩 중에도 이 문구가 떠서 앱이 멈춘 것처럼
        // 보였습니다.
        if (!hasTimetable) {
            // 계정이 없으면 "불러오는 중"이 영원히 남아 앱이 멈춘 것처럼 보였습니다 — 연동 안내로 바꿉니다.
            if (needsAccount) {
                Text(
                    "학사시스템 연동이 필요합니다",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                DashboardEmpty("계정을 연동하면 시간표·면학 위치·학사일정·게시판을 자동으로 가져옵니다. 탭해서 연동하세요.")
            } else {
                Text(
                    "시간표 불러오는 중…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                DashboardEmpty("잠시만 기다려주세요")
            }
            return@DashboardCard
        }
        if (homeStayNotice != null && (block == null || block.isBlank)) {
            Text(
                homeStayNotice.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                homeStayNotice.detail,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            homeStayExtras?.let { HomeStayExtraRows(it, onOpenWeather) }
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
                color = onPageVariantColor(),
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
                color = onPageVariantColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 카드 맨 아래 얇은 진행바 — 현재 블록이 얼마나 지났는지 (위젯의 막대와 같은 의미).
        blockProgress?.let { progress ->
            Spacer(Modifier.height(14.dp))
            BlockProgressBar(progress)
        }
    }
}

@Composable
private fun BlockProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private fun remainingText(minutes: Long): String =
    if (minutes >= 60) "%d시간 %02d분 남음".format(minutes / 60, minutes % 60)
    else "${minutes}분 남음"

/** 오늘 남은 일정 — 기존에 합쳐 보여주던 시간대·제목·장소/감독 줄을 전체 폭 카드로. */
@Composable
private fun UpcomingCard(groups: List<UpcomingGroup>, supervisor: String?, kindColors: Map<Accent, Int>, emptyText: String, modifier: Modifier = Modifier) {
    DashboardCard(title = "오늘 남은 일정", icon = painterResource(R.drawable.ic_next), modifier = modifier) {
        if (groups.isEmpty()) {
            DashboardEmpty(emptyText)
        } else {
            groups.forEach { group ->
                UpcomingGroupRow(
                    group,
                    supervision = supervisor.takeIf { group.supervisionSlot },
                    tint = Color(kindColors[group.accent] ?: WidgetKindColors.defaults.getValue(group.accent)),
                    isLast = group === groups.last(),
                )
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
                        // 카드가 전체 폭이라 대표 메뉴 + "외 n" 대신 메뉴를 쭉 이어 쓰고, 사진(또는 카드 끝)에
                        // 닿아 넘치는 부분만 …으로 줄입니다.
                        Text(
                            list.joinToString(", ") { it.name }.ifEmpty { "메뉴 없음" },
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
    limit: Int = 3,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        title = "알리미",
        icon = rememberVectorPainter(Icons.Default.Notifications),
        // 안 읽은 수는 헤더 종 아이콘의 숫자와 항목 앞 점으로 이미 보여서 카드 제목엔 적지 않습니다.
        subtitle = null,
        onHeaderClick = onOpenList,
        modifier = modifier,
    ) {
        when {
            alims.isEmpty() && isLoading -> DashboardEmpty("불러오는 중")
            alims.isEmpty() -> DashboardEmpty("알리미가 없습니다")
            else -> alims.sortedBy { it.read }.take(limit).forEach { alim ->
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
                            color = if (isUnread) MaterialTheme.colorScheme.onSurface else onPageVariantColor(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val meta = listOfNotNull(alim.writer, alim.date).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = onPageVariantColor(),
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
    modifier: Modifier = Modifier,
    /** '지금' 카드(귀가 기간)가 이미 다가오는 시험을 보여 주면 여기선 D-day 를 빼 같은 시험을 두 번 적지 않습니다. */
    showExam: Boolean = true,
) {
    // 시험 D-day 와 앞 3건은 일정·날짜·학년이 바뀔 때만 — 재구성마다 전체 일정을 거르고 정렬하면 이 카드 하나에 10ms 가 들었습니다.
    val exam = remember(schedule, today) { examDday(schedule, today) }
    val visible = remember(schedule, today, studentGrade) {
        filterSupervisionByGrade(schedule, studentGrade)
            .filter { it.code != "SG02020000" }
            .filter { !it.date.isBefore(today) }
            .sortedWith(compareBy({ it.date }, { it.name }))
            .take(3)
    }
    DashboardCard(title = "학사일정", icon = painterResource(R.drawable.ic_academic), onCardClick = onClick, modifier = modifier) {
        exam?.takeIf { showExam }?.let { exam ->
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
                            color = onPageVariantColor(),
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
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        modifier = modifier,
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
                    }
                    val meta = listOfNotNull(post.writer, prettyPortalDate(post.date)).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = onPageVariantColor(),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 귀가 기간 안내 아래 — 서울 날씨 두 칸(지금 · 귀교 밤 9시)을 나란히 크게, 그 아래 귀교 후 첫 일정·시험을 얇은 줄로.
 * 귀교일 밤 9시가 되면 안내와 함께 사라지고 홈은 평소대로 돌아갑니다.
 */
@Composable
internal fun HomeStayExtraRows(extras: HomeStayExtras, onOpenWeather: (() -> Unit)? = null) {
    val muted = onPageVariantColor()
    val divider = MaterialTheme.colorScheme.outlineVariant
    val tiles = listOfNotNull(extras.weatherNow, extras.weatherBack)
    if (tiles.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        // 두 칸은 같은 화면(귀교까지 시간별 서울 날씨)으로 이어지므로 하나의 긴 블록으로 — 누르면 블록 전체가 눌립니다.
        val scheme = MaterialTheme.colorScheme
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.onSurface.copy(alpha = if (scheme.isDark) 0.06f else 0.045f))
                .then(if (onOpenWeather != null) Modifier.clickable(onClick = onOpenWeather) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tiles.forEachIndexed { index, tile ->
                if (index > 0) {
                    // 두 칸 사이 세로선 — 블록 배경 위에서도 또렷하게.
                    Box(
                        Modifier
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .padding(vertical = 14.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(scheme.onSurfaceVariant.copy(alpha = 0.45f)),
                    )
                }
                WeatherTileView(tile, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
    @Composable
    fun row(icon: androidx.compose.ui.graphics.painter.Painter, tint: Color?, label: String, value: String, side: String?) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint ?: Color.Unspecified, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            side?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
    }
    val hasRows = extras.firstPlan != null || extras.exam != null
    if (hasRows) Spacer(Modifier.height(if (tiles.isEmpty()) 10.dp else 6.dp))
    extras.firstPlan?.let { (plan, time) ->
        HorizontalDivider(thickness = 0.8.dp, color = divider)
        row(painterResource(R.drawable.ic_place), MaterialTheme.colorScheme.primary, "귀교 후 첫 일정", plan, time)
    }
    extras.exam?.let { (name, days) ->
        HorizontalDivider(thickness = 0.8.dp, color = divider)
        row(painterResource(R.drawable.ic_academic), Color(0xFFFF9F6B), "다가오는 시험", name, if (days == 0L) "D-day" else "D-$days")
    }
}

/** 날씨 한 칸 — 이름, 큰 아이콘·기온, 설명, (비·눈이면) 파란 우산 안내. */
@Composable
private fun WeatherTileView(tile: WeatherTile, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // 배경·모서리는 두 칸을 감싼 블록이 맡고, 칸은 내용만.
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(tile.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        // 삼성 날씨 위젯처럼 기온은 보통 굵기의 큰 숫자, 아이콘은 그 옆에.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(tile.icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(8.dp))
            Text(tile.temp ?: "–", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(tile.desc, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        tile.tip?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF6FB4FF), maxLines = 2)
        }
    }
}

@Composable
private fun DashboardEmpty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = onPageVariantColor(),
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
    /** 위젯·Now Bar 와 같은 상황별 아이콘 키 — 비어 있으면 위치 아이콘. */
    val iconKey: String = "",
    /** 아이콘 원 색을 고를 종류 — 쉬는 시간·식사처럼 기다리는 구간이 다음 일정과 합쳐지면 그 다음 일정의 종류. */
    val accent: Accent = Accent.IDLE,
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

/**
 * 대기 구간(쉬는 시간·식사)은 바로 다음 수업·면학과 한 줄로 합칩니다 — 대기 구간의 제목이 이미 다음 것을
 * 가리키기 때문입니다. 대신 수업·면학 자체는 서로 합치지 않습니다: 예전엔 장소가 같으면 주말 1~4타임이
 * "13:30 ~ 23:10 생활관" 한 줄로 뭉개져 타임 구분이 사라졌습니다. 면학 타임은 제목에 "1타임 · 도서관"처럼
 * 타임 이름을 붙입니다.
 */
internal fun coalesceUpcoming(blocks: List<Block>): List<UpcomingGroup> {
    val result = mutableListOf<UpcomingGroup>()
    // 마지막 그룹이 아직 대기 구간만으로 이뤄져 있어 다음 블록을 받아들일 수 있는지.
    var lastOpen = false
    for (block in blocks) {
        val supervision = isWeekday1StudyRoom(block)
        val isGap = block.kind is BlockKind.GapKind
        // 수업은 과목명 끝의 "선생님 /" 을 떼고, 선생님은 장소 뒤에 붙습니다 (block.room = "체육관 · 김응주").
        val lesson = when (val kind = block.kind) {
            is BlockKind.LessonKind -> kind.lesson
            is BlockKind.GapKind -> (kind.gap.next as? NextUp.LessonNext)?.lesson
            else -> null
        }
        val title = when (val kind = block.kind) {
            is BlockKind.StudyKind -> "${kind.session.name.removePrefix("면학 ")} · ${block.title}"
            is BlockKind.GapKind -> when (val next = kind.gap.next) {
                is NextUp.StudyNext -> "${next.session.name.removePrefix("면학 ")} · ${block.title}"
                else -> lesson?.title ?: block.title
            }
            else -> lesson?.title ?: block.title
        }
        val last = result.lastOrNull()
        if (last != null && lastOpen && last.title == title && last.room == block.room && last.end == block.start) {
            result[result.lastIndex] = last.copy(
                end = block.end,
                supervisionSlot = last.supervisionSlot || supervision,
                // 기다리던 구간 뒤에 실제 일정이 붙으면 그 일정의 아이콘·색으로.
                iconKey = if (isGap) last.iconKey else block.iconKey.ifEmpty { last.iconKey },
                accent = if (isGap) last.accent else block.accent,
            )
            lastOpen = isGap
        } else {
            result += UpcomingGroup(title, block.room, block.start, block.end, supervision, block.iconKey, block.accent)
            lastOpen = isGap
        }
    }
    return result
}

/**
 * 남은 일정 한 줄 — 왼쪽에 종류별 색 원 아이콘(위젯·Now Bar 와 같은 아이콘·색), 오른쪽 첫 줄에 제목·장소,
 * 둘째 줄에 시간. 선생님·면학감독은 장소 옆에 붙습니다. 설정·알림 목록과 같은 One UI 두 줄 목록입니다.
 */
@Composable
private fun UpcomingGroupRow(group: UpcomingGroup, supervision: String?, tint: Color, isLast: Boolean) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = if (isLast) 0.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (group.iconKey.isEmpty()) R.drawable.ic_place else iconResFor(group.iconKey)),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
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
                // 장소 자리 — 장소 먼저: 수업은 "교실 · 선생님", 1타임 면학실은 "자리 · 감독 선생님".
                listOfNotNull(group.room, supervision?.let { "감독 $it" }).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Text(
                buildString {
                    append(group.start.format(formatter)).append(" ~ ").append(group.end.format(formatter))
                },
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Dev 탭 미리보기 — [date] 의 [at] 이후 남은 일정을 홈과 같은 카드로 (귀가 기간 등으로 오늘이 비어 있을 때 확인용). */
@Composable
internal fun UpcomingPreview(date: LocalDate, at: LocalDateTime) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var places by remember { androidx.compose.runtime.mutableStateOf<Map<PlanSlot, StudyPlace?>>(emptyMap()) }
    var kindColors by remember { androidx.compose.runtime.mutableStateOf(WidgetKindColors.defaults) }
    androidx.compose.runtime.LaunchedEffect(date) {
        places = PlanStore.placesForToday(context, date)
        kindColors = WidgetKindColors.resolve(PlanStore.widgetKindColors(context))
    }
    val groups = remember(places, date, at) {
        coalesceUpcoming(Timetable.blocks(date) { places[it] }.filter { it.start.isAfter(at) && !it.isBlank })
    }
    UpcomingCard(groups, supervisor = null, kindColors = kindColors, emptyText = "남은 일정이 없습니다")
}

/** 업데이트 투어 그림 — 예시 일정으로 채운 실제 '오늘 남은 일정' 카드 (색은 설정의 종류별 색). */
@Composable
internal fun UpcomingSampleCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var kindColors by remember { androidx.compose.runtime.mutableStateOf(WidgetKindColors.defaults) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kindColors = WidgetKindColors.resolve(PlanStore.widgetKindColors(context)) }
    val day = LocalDate.now()
    fun at(h: Int, m: Int) = day.atTime(h, m)
    val groups = listOf(
        UpcomingGroup("물리학Ⅰ(3반)", "A201 · 김하늘", at(15, 10), at(16, 0), iconKey = "bolt", accent = Accent.LESSON),
        UpcomingGroup("0타임 · 도서관", "2층 B-14", at(16, 0), at(17, 50), iconKey = "local_library", accent = Accent.LIBRARY),
        UpcomingGroup("1타임 · 면학실", "3층 A-07", at(17, 50), at(21, 0), supervisionSlot = true, iconKey = "meeting_room", accent = Accent.STUDY),
        UpcomingGroup("2타임 · 생활관", null, at(21, 0), at(23, 10), iconKey = "hotel", accent = Accent.DORM),
    )
    UpcomingCard(groups, supervisor = "이하나", kindColors = kindColors, emptyText = "")
}

/** 홈 카드 순서 — '지금' 아래 카드들. 헤더의 연필 아이콘으로 바꾸고 기기에 저장합니다. */
internal object HomeOrderStore {
    const val UPCOMING = "UPCOMING"
    const val MEAL = "MEAL"
    const val BOARD = "BOARD"
    const val SCHEDULE_ALIM = "SCHEDULE_ALIM"
    private const val PREFS = "home_order"
    private const val KEY = "order"
    val defaultOrder = listOf(UPCOMING, MEAL, BOARD, SCHEDULE_ALIM)

    fun get(context: android.content.Context): List<String> {
        val saved = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY, null)?.split(',')?.filter { it in defaultOrder }.orEmpty()
        return saved.distinct() + defaultOrder.filterNot { it in saved }
    }

    fun set(context: android.content.Context, order: List<String>) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, order.joinToString(",")).apply()
    }

    fun label(key: String): String = when (key) {
        UPCOMING -> "오늘 남은 일정"
        MEAL -> "급식"
        BOARD -> "게시판"
        SCHEDULE_ALIM -> "학사일정 · 알리미"
        else -> key
    }
}
