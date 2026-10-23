package com.skh.hihanaevo

import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.OneUiCard
import com.skh.hihanaevo.ui.onPageVariantColor
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val hm = DateTimeFormatter.ofPattern("HH:mm")

/** 끼니 시간(분, 시작 to 끝) — 시간표의 식사 블록과 같은 값. */
private fun mealRange(meal: Meal): Pair<Int, Int> = when (meal) {
    Meal.BREAKFAST -> Timetable.breakfast
    Meal.LUNCH -> Timetable.lunch
    Meal.DINNER -> Timetable.dinner
    Meal.SNACK -> Timetable.snack
}

private fun mealTime(meal: Meal): LocalTime = mealRange(meal).first.let { LocalTime.of(it / 60, it % 60) }

/** 지금 볼 끼니와 그 날 식단 — [tomorrow] 면 내일 첫 끼니. */
internal data class MealPick(val meal: Meal, val meals: DayMeals, val date: LocalDate, val tomorrow: Boolean)

/**
 * 지금 볼 끼니 — 오늘 식단 중 아직 끝나지 않은 첫 끼니(저녁이 끝나면 간식), 오늘 것이 다 끝났으면 내일 첫 끼니(보통 아침).
 * 예전엔 다 지나면 오늘 마지막 끼니(저녁)를 계속 보여 줘 쓸모가 없었습니다. 하루는 새벽 4시에 바뀌므로 [now] 가 [today] 다음
 * 날 새벽이어도 오늘 것은 모두 지난 것으로 봅니다. 홈 급식 칸과 급식 화면(열 때 그 끼니로)이 같이 씁니다.
 */
internal fun currentMeal(today: LocalDate, todayMeals: DayMeals?, tomorrowMeals: DayMeals?, now: LocalDateTime): MealPick? {
    fun present(d: DayMeals?) = Meal.entries.filter { d?.items?.get(it.key).orEmpty().isNotEmpty() }
    present(todayMeals).firstOrNull { m -> mealRange(m).second.let { today.atTime(it / 60, it % 60) }.isAfter(now) }
        ?.let { return MealPick(it, todayMeals!!, today, tomorrow = false) }
    present(tomorrowMeals).firstOrNull()?.let { return MealPick(it, tomorrowMeals!!, today.plusDays(1), tomorrow = true) }
    return null
}

// ---------- 홈 칸 ----------

/** 홈 칸 — 프로스트 카드 위에 칸 색을 옅게 깐 판. 머리글은 칸 색. */
@Composable
private fun BentoTile(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    // 타일 색은 카드 위에 옅게 덮습니다 — 서리 판 밑에 깔면 흐린 배경 사진에 가려 보이지 않습니다.
    OneUiCard(
        modifier = modifier.clip(shape).drawWithContent { drawContent(); drawRect(accent.copy(alpha = 0.10f)) },
        contentPadding = PaddingValues(16.dp),
        shape = shape,
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 카드가 밝으면(안 글자가 어두우면) 칸 색을 짙게 — 노랑(게시판)·하늘(날씨)이 밝은 서리 판 위에서 거의 안 보였습니다.
            val onLight = MaterialTheme.colorScheme.onSurface.luminance() < 0.5f
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (onLight) lerp(accent, Color.Black, 0.3f) else accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

/** 남은 시간 고리 — 블록이 지난 만큼 채운 원과 가운데 남은 분. */
@Composable
private fun RemainingRing(progress: Float, text: String, color: Color, track: Color) {
    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(60.dp)) {
            val w = 6.dp.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

private fun shortRemaining(minutes: Long): String = if (minutes >= 60) "%d:%02d".format(minutes / 60, minutes % 60) else "${minutes}분"

/** 홈 맨 위 '지금' 카드 — 남은 시간 고리, 시간, 지금 하는 것, 있을 곳, 다음(시각 · 이름 · 장소). 투어 그림도 같은 카드. */
@Composable
private fun NowCard(timeRange: String, title: String, where: String?, next: String?, progress: Float, remaining: String) {
    val scheme = MaterialTheme.colorScheme
    OneUiCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemainingRing(progress, remaining, scheme.primary, scheme.primary.copy(alpha = 0.18f))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("지금 · $timeRange", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = onPageVariantColor())
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                where?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = scheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                next?.let { Text("다음 · $it", style = MaterialTheme.typography.labelMedium, color = onPageVariantColor(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            }
        }
    }
}

/**
 * 벤토 홈 — '지금' 고리 카드 한 줄, 그 아래 두 기둥(왼쪽 급식·게시판 | 오른쪽 클래스룸·오늘 일정, 위아래는 [bentoOrder]),
 * 맨 아래 날씨 | 시험까지.
 * 블록이 없거나 귀가 기간·연동 전이면 '지금' 칸은 평소 카드([fallbackNow])를 그대로 씁니다.
 */
@Composable
internal fun BentoHome(
    today: LocalDate,
    now: LocalDateTime,
    currentBlock: Block?,
    remainingMinutes: Long?,
    nextTitle: String?,
    nextRoom: String?,
    nextStart: LocalDateTime?,
    blockProgress: Float?,
    showRing: Boolean,
    fallbackNow: @Composable (Modifier) -> Unit,
    meals: DayMeals?,
    tomorrowMeals: DayMeals? = null,
    /** 받아 둔 다른 날 식단 — 급식 칸이 이번 주에 자주 나온 메뉴를 특식에서 뺄 때 씁니다. */
    mealHistory: Collection<DayMeals>,
    isLoadingMeals: Boolean,
    todayAgenda: List<AgendaItem>,
    schedule: List<HanaScheduleEntry>,
    studentGrade: Int,
    agendaExtras: List<AgendaItem>,
    boardPosts: List<HanaBoardPost>,
    classroom: ClassroomSnapshot?,
    weather: @Composable (Modifier) -> Unit,
    /** 날씨·시험 줄 아래 맨 끝 — 이미 본 데일리 브리핑이 여기 옵니다. */
    bottom: @Composable () -> Unit,
    onOpenMeal: () -> Unit,
    /** 급식 칸이 내일 끼니를 보여 줄 때 누르면 — 급식 화면을 내일로. */
    onOpenMealTomorrow: () -> Unit = onOpenMeal,
    onOpenAgenda: (AgendaItem) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenPost: (HanaBoardPost) -> Unit,
    onOpenBoardList: () -> Unit,
    onOpenClassroom: () -> Unit,
    onOpenClassroomPost: (ClassroomPost) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val gap = 12.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        // 1) 지금
        val block = currentBlock
        if (showRing && block != null && !block.isBlank) {
            // 지금(이름 · 있을 곳)과 다음을 줄로 나눕니다 — 점심·쉬는 시간 같은 빈 시간은 Block 이 이름·장소 자리에 다음
            // 수업을 담고 있어 한 줄에 섞으면 다음 수업을 지금 하는 것처럼 읽혔습니다. 빈 시간은 그 이름만.
            val gap = block.kind is BlockKind.GapKind
            NowCard(
                timeRange = block.timeRangeText,
                title = if (gap) block.statusLabel else block.headline,
                where = block.room?.takeIf { !gap },
                next = nextTitle?.let { title -> listOfNotNull(nextStart?.format(hm), title, nextRoom).joinToString(" · ").replaceFirst(Regex("^(\\d\\d:\\d\\d) · "), "$1 ") },
                progress = blockProgress ?: 0f,
                remaining = remainingMinutes?.let(::shortRemaining) ?: "",
            )
        } else fallbackNow(Modifier)

        // 2) 두 기둥 — 왼쪽 급식·게시판, 오른쪽 클래스룸·오늘 일정. 기둥 안의 위아래만 지금 챙길 것에 따라 바뀝니다([bentoOrder]).
        //    급식·오늘 일정은 둘 중 큰 쪽 높이로, 게시판·클래스룸도 둘 중 큰 쪽 높이로 맞춥니다 — 예전엔 목록 칸이 남는 높이를
        //    따로 채워, 급식 사진이 길면 게시판만 짧아지고 칸 경계가 좌우로 어긋났습니다.
        val context = LocalContext.current
        val (left, right) = remember(now, classroom, ClassroomReadStore.revision.intValue) { bentoOrder(context, now, classroom) }
        BentoColumns(left, right, gap) { slot ->
            when (slot) {
                BentoSlot.CLASSROOM -> ClassroomTile(classroom, onOpenClassroom, onOpenClassroomPost, Modifier.fillMaxSize())
                BentoSlot.BOARD -> BoardTile(boardPosts, onOpenPost, onOpenBoardList, Modifier.fillMaxSize())
                BentoSlot.MEAL -> MealTile(now, today, meals, tomorrowMeals, mealHistory, isLoadingMeals, { if (it) onOpenMealTomorrow() else onOpenMeal() }, Modifier.fillMaxSize())
                BentoSlot.TODAY -> TodayTile(today, todayAgenda, schedule, studentGrade, agendaExtras, onOpenAgenda, onOpenSchedule, Modifier.fillMaxSize())
            }
        }

        // 3) 날씨 | 시험까지(같은 높이), 그 아래 이미 본 데일리 브리핑. 남은 일정은 타임라인이 보여 주므로 홈에 두지 않습니다.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
            weather(Modifier.weight(1f).fillMaxHeight())
            ExamTile(today, schedule, Modifier.weight(1f).fillMaxHeight(), onOpenSchedule)
        }
        bottom()
    }
}

/** 다음 끼니 — 아직 끝나지 않은 첫 끼니(다 끝났으면 내일 첫 끼니)의 이름·시각과 특식·사진. [onClick] 은 내일 끼니인지. */
@Composable
private fun MealTile(
    now: LocalDateTime,
    today: LocalDate,
    todayMeals: DayMeals?,
    tomorrowMeals: DayMeals?,
    history: Collection<DayMeals>,
    isLoading: Boolean,
    onClick: (tomorrow: Boolean) -> Unit,
    modifier: Modifier,
) {
    val pick = currentMeal(today, todayMeals, tomorrowMeals, now)
    val next = pick?.meal
    val meals = pick?.meals
    val items = meals?.items.orEmpty()
    val accent = Color(0xFFF08A3C)
    val label = pick?.let { p -> (if (p.tomorrow) "내일 " else "") + "${p.meal.label} · ${mealTime(p.meal).format(hm)}" } ?: "급식"
    BentoTile(label, accent, modifier, { onClick(pick?.tomorrow == true) }) {
        val list = next?.let { items[it.key] }.orEmpty()
        if (list.isEmpty()) {
            DashboardEmpty(if (isLoading) "불러오는 중" else "다음 식단이 아직 없습니다")
            return@BentoTile
        }
        // 특식 1~2개 — AI 가 고른 것(받아 둔 것)을, 오기 전엔 기기 안 판정으로([MealHighlights]).
        val context = LocalContext.current
        val names = remember(list) { list.map { it.name } }
        var picked by remember(names) { mutableStateOf(MealHighlights.cached(context, names) ?: MealHighlights.pickLocally(names, history)) }
        androidx.compose.runtime.LaunchedEffect(names) { MealHighlights.fetch(context, names)?.let { picked = it } }
        picked.forEach {
            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // 급식 사진이 있으면 사진, 없으면 나머지 메뉴.
        val photo = next?.let { meals?.photos?.get(it.key) }?.let { HanaMealClient.mealPhotoThumbUrl(it) }
        if (photo != null) {
            RemoteThumbnail(
                urls = listOf(photo),
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)),
            )
        } else {
            names.filter { it !in picked }.take(4).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = onPageVariantColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 홈의 두 기둥 — [left]·[right] 칸을 위에서부터 쌓되, 급식·오늘 일정(고정 칸)은 둘 중 큰 쪽(최소 168dp), 게시판·클래스룸(목록 칸)도
 * 둘 중 큰 쪽 높이로 맞춥니다. 칸 순서가 기둥마다 달라도 같은 종류끼리 높이가 같아 좌우 경계가 맞습니다.
 */
@Composable
private fun BentoColumns(left: List<BentoSlot>, right: List<BentoSlot>, gap: Dp, tile: @Composable (BentoSlot) -> Unit) {
    val slots = left + right
    Layout(content = { slots.forEach { tile(it) } }) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val minFixed = 168.dp.roundToPx()
        val colW = (constraints.maxWidth - gapPx) / 2
        fun fixed(slot: BentoSlot) = slot == BentoSlot.MEAL || slot == BentoSlot.TODAY
        val fixedH = maxOf(minFixed, slots.indices.filter { fixed(slots[it]) }.maxOfOrNull { measurables[it].maxIntrinsicHeight(colW) } ?: 0)
        val listH = slots.indices.filter { !fixed(slots[it]) }.maxOfOrNull { measurables[it].maxIntrinsicHeight(colW) } ?: 0
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(androidx.compose.ui.unit.Constraints.fixed(colW, if (fixed(slots[i])) fixedH else listH))
        }
        fun columnHeight(from: Int, count: Int) = (from until from + count).sumOf { placeables[it].height } + gapPx * (count - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, maxOf(columnHeight(0, left.size), columnHeight(left.size, right.size))) {
            var y = 0
            left.indices.forEach { i -> placeables[i].place(0, y); y += placeables[i].height + gapPx }
            y = 0
            right.indices.forEach { j -> val p = placeables[left.size + j]; p.place(colW + gapPx, y); y += p.height + gapPx }
        }
    }
}

/** 오늘 일정 — 마감이 있으면 '오늘 마감'과 큰 숫자, 아니면 오늘 일정 수. 오늘이 비면 다음 일정 하나. */
@Composable
private fun TodayTile(
    today: LocalDate,
    todayAgenda: List<AgendaItem>,
    schedule: List<HanaScheduleEntry>,
    studentGrade: Int,
    extras: List<AgendaItem>,
    onOpenAgenda: (AgendaItem) -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier,
) {
    val deadlines = todayAgenda.count { it.deadline }
    val accent = if (deadlines > 0) Color(0xFFFF6B6B) else AgendaSource.ACADEMIC.color
    if (todayAgenda.isEmpty()) {
        val next = remember(schedule, studentGrade, extras, today) { Agenda.merge(schedule, studentGrade, extras, today).firstOrNull { it.daysFrom(today) > 0 } }
        BentoTile("오늘 일정", accent, modifier, onOpenSchedule) {
            Text("없음", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            next?.let {
                Text("다음 · ${shortDate(it.date)}", style = MaterialTheme.typography.labelMedium, color = onPageVariantColor())
                Text(it.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    BentoTile(if (deadlines > 0) "오늘 마감" else "오늘 일정", accent, modifier, onOpenSchedule) {
        Text("${if (deadlines > 0) deadlines else todayAgenda.size}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
        // 면학감독은 가장 덜 중요해 맨 아래로, 나머지는 마감 먼저·짧은 일정부터.
        todayAgenda.sortedWith(compareBy<AgendaItem> { it.academic?.code == SUPERVISION_CODE }.thenByDescending { it.deadline }.thenBy { it.spanDays }).take(3).forEach { item ->
            Text(
                listOfNotNull(item.time, item.title).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { if (item.academic != null) onOpenSchedule() else onOpenAgenda(item) },
            )
        }
    }
}

/** 클래스룸 — 새 글 수와 최근 글 세 개(제목과 수업명). 연결 전이면 연결 안내. */
@Composable
private fun ClassroomTile(snapshot: ClassroomSnapshot?, onOpenTab: () -> Unit, onOpenPost: (ClassroomPost) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val accent = Color(0xFF2FB67C)
    if (snapshot == null) {
        BentoTile("클래스룸", accent, modifier, onOpenTab) {
            Text("연결하면 새 글과 과제 마감이 여기에 모입니다", style = MaterialTheme.typography.bodySmall, color = onPageVariantColor())
        }
        return
    }
    val readIds = remember(snapshot, ClassroomReadStore.revision.intValue) { ClassroomReadStore.readIds(context) }
    val recent = remember(snapshot) { snapshot.posts.sortedByDescending { it.updated }.take(3) }
    val unread = snapshot.posts.count { it.id !in readIds }
    BentoTile(if (unread > 0) "클래스룸 새 글 $unread" else "클래스룸", accent, modifier, onOpenTab) {
        if (recent.isEmpty()) DashboardEmpty("글이 없습니다")
        recent.forEach { post ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenPost(post) }
                    .padding(vertical = 5.dp),
            ) {
                Text(
                    post.title.ifEmpty { post.text.lineSequence().firstOrNull().orEmpty() }.ifEmpty { post.kind.label },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (post.id !in readIds) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 홈에선 제목과 수업명만 — 시각은 클래스룸 화면에서.
                snapshot.course(post.courseId)?.name?.let { Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = onPageVariantColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                ) }
            }
        }
    }
}

/** 시험까지 — 다가오는 시험 D-day, 없으면 학사일정 바로가기. */
@Composable
private fun ExamTile(today: LocalDate, schedule: List<HanaScheduleEntry>, modifier: Modifier, onClick: () -> Unit) {
    val exam = remember(schedule, today) { examDday(schedule, today) }
    BentoTile("시험까지", Color(0xFF8E7CFF), modifier, onClick) {
        if (exam == null) {
            DashboardEmpty("다가오는 시험이 없습니다")
        } else {
            Text(if (exam.days == 0L) "D-Day" else "D-${exam.days}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("${exam.name} ${exam.date.monthValue}/${exam.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = onPageVariantColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 게시판 — 클래스룸 칸처럼 학생공지 최신 글 세 개(안 읽은 글은 굵게). 글을 누르면 그 글, 칸의 나머지는 목록. */
@Composable
private fun BoardTile(posts: List<HanaBoardPost>, onOpenPost: (HanaBoardPost) -> Unit, onOpenList: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val recent = posts.take(3)
    val revision = BoardReadStore.revision
    val unread = remember(posts, revision) { BoardReadStore.unreadCount(context, posts) }
    BentoTile(if (unread > 0) "게시판 새 글 $unread" else "게시판", Color(0xFFF2B33D), modifier, onClick = onOpenList) {
        if (recent.isEmpty()) DashboardEmpty("게시글이 없습니다")
        recent.forEach { post ->
            val read = remember(post, revision) { BoardReadStore.isRead(context, post) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenPost(post) }
                    .padding(vertical = 5.dp),
            ) {
                Text(
                    post.title.ifEmpty { "(제목 없음)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (read) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(BoardCategory.STUDENT_NOTICE.label, prettyPortalDate(post.date)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = onPageVariantColor(),
                    maxLines = 1,
                )
            }
        }
    }
}

/** 벤토 두 기둥의 네 칸. */
private enum class BentoSlot { CLASSROOM, BOARD, MEAL, TODAY }

/**
 * 두 기둥 안의 위아래 — 왼쪽 [게시판, 급식], 오른쪽 [클래스룸, 오늘 일정]이 기본. 다음 순위대로 그 칸이 제 기둥 맨 위로 옵니다:
 * 1. 끼니 30분 전부터 끝날 때까지 급식, 2. 6시간 안에 올라온 안 읽은 클래스룸 글, 3. 하루가 시작하는 4시부터 9시 전까지 오늘 일정,
 * 4. 3시간 안에 올라온 안 읽은 게시글 — 게시판은 이미 기본으로 위라, 4는 같은 기둥의 1(급식)에만 밀립니다.
 */
private fun bentoOrder(context: android.content.Context, now: LocalDateTime, classroom: ClassroomSnapshot?): Pair<List<BentoSlot>, List<BentoSlot>> {
    val t = now.toLocalTime()
    val minute = t.hour * 60 + t.minute
    val mealSoon = listOf(Timetable.breakfast, Timetable.lunch, Timetable.dinner).any { (start, end) -> minute >= start - 30 && minute < end }
    val classroomFresh = classroom != null && run {
        val read = ClassroomReadStore.readIds(context)
        classroom.posts.any { it.id !in read && !it.updated.isBefore(now.minusHours(6)) && !it.updated.isAfter(now.plusMinutes(5)) }
    }
    val morning = !t.isBefore(LocalTime.of(4, 0)) && t.isBefore(LocalTime.of(9, 0))
    val left = if (mealSoon) listOf(BentoSlot.MEAL, BentoSlot.BOARD) else listOf(BentoSlot.BOARD, BentoSlot.MEAL)
    val right = if (!classroomFresh && morning) listOf(BentoSlot.TODAY, BentoSlot.CLASSROOM) else listOf(BentoSlot.CLASSROOM, BentoSlot.TODAY)
    return left to right
}


// ---------- 날씨 ----------

/** 벤토 맨 아래의 반 폭 날씨 칸 — 지금 하늘·기온과 오늘 최고·최저. 누르면 시간별 날씨 화면. */
@Composable
internal fun WeatherTile(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var forecast by remember { mutableStateOf<SeoulWeather.Forecast?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        SeoulWeather.cached(context)?.let { forecast = it }
        SeoulWeather.get(context)?.let { forecast = it }
    }
    val f = forecast
    val day = f?.days?.firstOrNull { it.date == LocalDate.now(PlanStore.seoulZone) }
    BentoTile("날씨", Color(0xFF3B9BF5), modifier, onOpen) {
        if (f == null) {
            DashboardEmpty("불러오는 중")
            return@BentoTile
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(SeoulWeather.icon(f.currentCode)),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(f.currentTemp?.let { "${it.roundToInt()}°" } ?: "–", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            listOfNotNull(SeoulWeather.label(f.currentCode), day?.let { d -> listOfNotNull(d.max?.let { "${it.roundToInt()}°" }, d.min?.let { "${it.roundToInt()}°" }).joinToString("/").ifEmpty { null } }).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = onPageVariantColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 날씨 카드(평소 하나고, 귀가 기간 서울) — 지금 기온·하늘, 오늘 최고·최저, 12시간 안의 비·눈 소식, 앞으로 6시간. 누르면 시간별 날씨 화면.
 * 받아 둔 예보(30분 캐시)를 먼저 그리고 새로 받으면 바꿉니다.
 */
@Composable
internal fun WeatherCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var forecast by remember { mutableStateOf<SeoulWeather.Forecast?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        SeoulWeather.cached(context)?.let { forecast = it }
        SeoulWeather.get(context)?.let { forecast = it }
    }
    val f = forecast
    val now = LocalDateTime.now()
    val today = f?.days?.firstOrNull { it.date == now.toLocalDate() }
    val upcoming = f?.hours.orEmpty().filter { !it.time.isBefore(now.withMinute(0).withSecond(0).withNano(0)) }
    DashboardCard(
        title = "날씨",
        icon = androidx.compose.ui.res.painterResource(R.drawable.ic_weather_outline),
        subtitle = f?.updated?.let { java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.of("Asia/Seoul")).format(hm) + " 기준" },
        onCardClick = onOpen,
        modifier = modifier,
    ) {
        if (f == null) {
            DashboardEmpty("날씨를 불러오는 중")
            return@DashboardCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(SeoulWeather.icon(f.currentCode)),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(f.currentTemp?.let { "${it.roundToInt()}°" } ?: "–", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Normal)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(SeoulWeather.label(f.currentCode), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                today?.let { d ->
                    Text(
                        listOfNotNull(d.max?.let { "최고 ${it.roundToInt()}°" }, d.min?.let { "최저 ${it.roundToInt()}°" }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = onPageVariantColor(),
                    )
                }
            }
        }
        // 12시간 안에 비·눈이 오면 처음 오는 시각을 한 줄로.
        upcoming.take(12).firstOrNull { SeoulWeather.wet(it.code, it.pop) }?.let { wet ->
            val what = if (SeoulWeather.kind(wet.code) == SeoulWeather.Kind.SNOW) "눈" else "비"
            Text(
                "${wet.time.hour}시부터 $what" + (wet.pop?.let { " $it%" } ?: "") + " · 우산 챙기세요",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6FB4FF),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (upcoming.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                upcoming.drop(1).take(6).forEach { h ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${h.time.hour}시", style = MaterialTheme.typography.labelSmall, color = onPageVariantColor())
                        androidx.compose.material3.Icon(
                            androidx.compose.ui.res.painterResource(SeoulWeather.icon(h.code)),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp).padding(vertical = 2.dp),
                        )
                        Text(h.temp?.let { "${it.roundToInt()}°" } ?: "–", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 투어 그림용 — 홈의 '지금' 카드, 두 기둥과 날씨·시험 줄을 예시 데이터로(점심시간이라 급식이 위, 클래스룸 새 글). 실제 칸 부품을 그대로
 * 쓰고, 누르면 아무 일도 없습니다.
 */
@Composable
internal fun BentoPreview(modifier: Modifier = Modifier) {
    val today = remember { PlanStore.today() }
    val now = remember(today) { today.atTime(11, 50) }
    val meals = remember {
        DayMeals(
            mapOf(Meal.LUNCH.key to listOf("보리밥", "순살허브치킨구이", "김치말이냉국수", "갈비손만두찜", "깍두기").map { MealItem(it, emptySet()) }),
            emptyMap(),
        )
    }
    val board = remember(today) {
        listOf(
            HanaBoardPost(0, -1, "2026 국제교류 프로그램 신청 기간 연장 안내", null, "$today 10:19", true, emptyList(), ""),
            HanaBoardPost(0, -2, "2학기 방과후학교 강의 시간 변경", null, "${today.minusDays(1)} 16:40", false, emptyList(), ""),
            HanaBoardPost(0, -3, "도서관 야간 개방 안내", null, "${today.minusDays(2)} 09:05", false, emptyList(), ""),
        )
    }
    val classroom = remember(today) { sampleClassroom(now) }
    val agenda = remember(today) {
        listOf(
            AgendaItem("t1", AgendaSource.ACADEMIC, "2학기 방과후학교 강의", today),
            AgendaItem("t2", AgendaSource.BOARD, "철학 경시대회 신청 마감", today, time = "17:00", deadline = true),
        )
    }
    val exams = remember(today) { listOf(HanaScheduleEntry("SG01010000", "2학기 기말고사", null, null, null, true, today.plusDays(52))) }
    val gap = 12.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        // 점심시간 — 빈 시간이라 이름만, 다음 수업은 '다음' 줄에.
        NowCard("11:40 – 12:40", "점심시간", null, "12:40 확률과 통계 · B301", 10f / 60f, shortRemaining(50))
        BentoColumns(listOf(BentoSlot.MEAL, BentoSlot.BOARD), listOf(BentoSlot.CLASSROOM, BentoSlot.TODAY), gap) { slot ->
            when (slot) {
                BentoSlot.MEAL -> MealTile(now, today, meals, null, emptyList(), false, {}, Modifier.fillMaxSize())
                BentoSlot.BOARD -> BoardTile(board, {}, {}, Modifier.fillMaxSize())
                BentoSlot.CLASSROOM -> ClassroomTile(classroom, {}, {}, Modifier.fillMaxSize())
                BentoSlot.TODAY -> TodayTile(today, agenda, exams, 2, emptyList(), {}, {}, Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
            WeatherTile(onOpen = {}, modifier = Modifier.weight(1f).fillMaxHeight())
            ExamTile(today, exams, Modifier.weight(1f).fillMaxHeight(), onClick = {})
        }
    }
}

/** 투어 그림용 클래스룸 — 수업 하나에 새 공지 하나, 어제 올라온 자료 하나. */
private fun sampleClassroom(now: LocalDateTime): ClassroomSnapshot = ClassroomSnapshot(
    listOf(ClassroomCourse("p1", "미적분Ⅱ", null, null)),
    listOf(
        ClassroomPost("p-a", "p1", ClassroomKind.ANNOUNCEMENT, "다음 주 수행평가 범위 안내", "", null, now.minusHours(1), now.minusHours(1)),
        ClassroomPost("p-w", "p1", ClassroomKind.WORK, "3단원 탐구 보고서", "", null, now.minusHours(5), now.minusHours(5), due = now.plusDays(2)),
        ClassroomPost("p-m", "p1", ClassroomKind.MATERIAL, "11차시 PCA 수업자료", "", null, now.minusDays(1), now.minusDays(1)),
    ),
    0L,
)

/** 투어 그림용 — 실제 홈 클래스룸 칸에 예시 글 셋. */
@Composable
internal fun ClassroomPreview(modifier: Modifier = Modifier) {
    val now = remember { PlanStore.today().atTime(11, 50) }
    val classroom = remember(now) { sampleClassroom(now) }
    ClassroomTile(classroom, {}, {}, modifier.fillMaxWidth())
}
