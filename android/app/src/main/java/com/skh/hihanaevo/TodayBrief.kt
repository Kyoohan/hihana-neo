package com.skh.hihanaevo

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.isDark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * 오늘 브리프 — 삼성 나우 브리프처럼 오늘 일정을 한 장에 모읍니다.
 * 학사일정·클래스룸(과제 마감, 공지 속 일정)·게시판 글 속 일정 중 오늘에 걸친 것만. 오전(평일 12:10, 주말 13:30 전)엔 '지금' 바로
 * 아래, 그 뒤엔 '오늘 남은 일정' 아래에 둡니다.
 */

private const val BRIEF_MAX_ROWS = 6

/** 브리프가 '지금' 바로 아래에 있을 때 — 평일은 점심 전(12:10), 주말은 늦은 점심(13:30)까지. */
internal fun briefMorning(now: LocalDateTime): Boolean {
    val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
    return now.toLocalTime().isBefore(if (weekend) LocalTime.of(13, 30) else LocalTime.of(12, 10))
}

/** 오늘에 걸친 일정 — 종일·기간 일정 먼저, 그다음 시각순(마감은 같은 시각의 행사보다 앞). */
internal fun todayBrief(schedule: List<HanaScheduleEntry>, studentGrade: Int, extras: List<AgendaItem>, today: LocalDate): List<AgendaItem> =
    Agenda.merge(schedule, studentGrade, extras, today)
        // 몇 주씩 이어지는 상시 운영 안내는 학사일정 목록처럼 '오늘 일정'에서도 뺍니다 — 홈 숫자(8)와 목록(5)이 어긋났습니다.
        .filter { it.daysFrom(today) == 0L && !it.longRunning }
        .sortedWith(compareBy<AgendaItem>({ it.academic?.code == SUPERVISION_CODE }, { it.time ?: "" }, { !it.deadline }, { it.source.ordinal }))

/** 나우 브리프의 은은한 새벽빛 — 다른 카드(서리 재질)와 한눈에 갈리도록 불투명 그라데이션. */
private data class BriefPalette(val colors: List<Color>, val text: Color, val sub: Color, val edge: Color, val chip: Color)

@Composable
private fun briefPalette(): BriefPalette =
    if (MaterialTheme.colorScheme.isDark) BriefPalette(
        colors = listOf(Color(0xFF1B2646), Color(0xFF2A2044), Color(0xFF3A2826)),
        text = Color(0xFFF2F3F7),
        sub = Color(0xFFB7BBC8),
        edge = Color.White.copy(alpha = 0.10f),
        chip = Color.White.copy(alpha = 0.10f),
    ) else BriefPalette(
        colors = listOf(Color(0xFFD8E5FF), Color(0xFFEBE2FF), Color(0xFFFFE4D2)),
        text = Color(0xFF15171C),
        sub = Color(0xFF4D5260),
        edge = Color.White.copy(alpha = 0.85f),
        chip = Color.White.copy(alpha = 0.55f),
    )

@Composable
internal fun TodayBriefCard(
    today: LocalDate,
    items: List<AgendaItem>,
    onOpen: (AgendaItem) -> Unit,
    modifier: Modifier = Modifier,
    /** 데일리 브리핑([MorningBrief]) — 있으면 '오늘 일정 n개' 대신 AI 가 풀어 쓴 머리·설명, 아래에 이번 주에 챙길 것. */
    brief: MorningBrief.Brief? = null,
) {
    val palette = briefPalette()
    val shape = RoundedCornerShape(OneUi.CornerLarge)
    val deadlines = items.count { it.deadline }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(palette.colors))
            .border(1.dp, palette.edge, shape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_pv_star), contentDescription = null, tint = palette.text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("오늘 브리프", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = palette.text, modifier = Modifier.weight(1f))
            Text(dateHeader(today), style = MaterialTheme.typography.labelMedium, color = palette.sub)
        }
        Spacer(Modifier.height(10.dp))
        if (brief != null) {
            Text(brief.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.text)
            Spacer(Modifier.height(4.dp))
            Text(brief.body, style = MaterialTheme.typography.bodyMedium, color = palette.sub)
        } else {
            Text(
                "오늘 일정 ${items.size}개",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
            if (deadlines > 0) {
                Text("그중 마감 ${deadlines}개", style = MaterialTheme.typography.bodyMedium, color = palette.sub)
            }
        }
        Spacer(Modifier.height(12.dp))
        items.take(BRIEF_MAX_ROWS).forEach { item -> BriefRow(item, palette, onOpen) }
        if (items.size > BRIEF_MAX_ROWS) {
            Text(
                "외 ${items.size - BRIEF_MAX_ROWS}개는 일정 탭에서",
                style = MaterialTheme.typography.labelMedium,
                color = palette.sub,
                modifier = Modifier.padding(start = 64.dp, top = 4.dp),
            )
        }
        // 오늘 것은 위 목록에 있으니, 브리핑이 고른 것 중 내일 이후만 '이번 주'로.
        val ahead = brief?.highlights.orEmpty().filter { it.date.isAfter(today) }
        if (ahead.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("이번 주에 챙길 것", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = palette.sub)
            Spacer(Modifier.height(2.dp))
            val labelW = rememberDayLabelWidth(ahead, today)
            ahead.forEach { BriefHighlightRow(it, today, palette, labelW) }
        }
        if (brief?.ai == true) {
            Spacer(Modifier.height(6.dp))
            Text("AI가 만든 요약 · 틀릴 수 있어요", style = MaterialTheme.typography.labelSmall, color = palette.sub)
        }
    }
}

/**
 * 홈의 데일리 브리핑 — 접으면 머리 한 줄, 펼치면 두세 문장과 이번 주에 챙길 것. 오전엔 펼친 채로, 오후엔 접힌 채로 시작합니다.
 * 처음 만드는 동안(둔 것이 없을 때)은 '살펴보는 중' 한 줄만.
 */
@Composable
internal fun MorningBriefPanel(
    brief: MorningBrief.Brief?,
    loading: Boolean,
    today: LocalDate,
    startExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    if (brief == null && !loading) return
    val palette = briefPalette()
    var expanded by rememberSaveable(today, startExpanded) { mutableStateOf(startExpanded) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "briefChevron")
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(palette.colors))
            .border(1.dp, palette.edge, shape)
            // 카드 전체가 누르는 자리라 리플이 카드만 한 회색 원으로 번졌습니다(펼쳐지며 더 커짐) — 펼침·화살표가 대신 알려 줍니다.
            .clickable(enabled = brief != null, interactionSource = null, indication = null) { expanded = !expanded }
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 520f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_pv_star), contentDescription = null, tint = palette.text, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("데일리 브리핑", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = palette.sub, modifier = Modifier.weight(1f))
            if (loading) Text("만드는 중", style = MaterialTheme.typography.labelSmall, color = palette.sub)
            else Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "브리핑 접기" else "브리핑 펼치기",
                tint = palette.sub,
                modifier = Modifier.size(18.dp).rotate(turn),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            brief?.headline ?: "오늘 일정을 살펴보는 중",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = palette.text,
            maxLines = if (expanded) 3 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (expanded && brief != null) {
            Spacer(Modifier.height(6.dp))
            Text(brief.body, style = MaterialTheme.typography.bodyMedium, color = palette.sub)
            if (brief.highlights.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val labelW = rememberDayLabelWidth(brief.highlights, today)
                brief.highlights.forEach { BriefHighlightRow(it, today, palette, labelW) }
            }
            Spacer(Modifier.height(8.dp))
            // '다시 만들기'는 두지 않습니다 — 누를 때마다 AI 를 불러 무료 사용량을 쓰게 됩니다. 재료(일정)가 바뀌면 저절로 다시 만듭니다.
            Text(
                if (brief.ai) "AI가 만든 요약 · 틀릴 수 있어요" else "AI 없이 일정만 추렸어요",
                style = MaterialTheme.typography.labelSmall,
                color = palette.sub,
            )
        }
    }
}

/** 날짜 칸 너비 — 목록에서 가장 긴 날짜('10/26(월)')에 맞춰, '오늘'·'모레'인 줄도 이름이 같은 자리에서 시작하게. */
@Composable
private fun rememberDayLabelWidth(items: List<MorningBrief.Highlight>, today: LocalDate): androidx.compose.ui.unit.Dp {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(items, today, style, density) {
        val widest = items.maxOfOrNull { measurer.measure(MorningBrief.relativeDay(today, it.date), style).size.width } ?: 0
        with(density) { widest.toDp() }
    }
}

/** 브리핑이 고른 일정 한 줄 — 왼쪽은 '내일'·'10/26(월)'(너비는 [labelWidth] 로 줄마다 같게), 오른쪽은 이름과 왜 챙길지. */
@Composable
private fun BriefHighlightRow(h: MorningBrief.Highlight, today: LocalDate, palette: BriefPalette, labelWidth: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(
            MorningBrief.relativeDay(today, h.date),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 1.dp, end = 10.dp).width(labelWidth.coerceAtLeast(32.dp) + 1.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(h.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.labelSmall, color = palette.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BriefRow(item: AgendaItem, palette: BriefPalette, onOpen: (AgendaItem) -> Unit) {
    val clickable = item.academic != null || item.url != null
    val start = item.time?.substringBefore('~')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUi.CornerSmall))
            .then(if (clickable) Modifier.clickable { onOpen(item) } else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 왼쪽 시각 칸 — 기간 일정은 '기간', 시각 없는 하루 일정은 '종일'.
        Text(
            when {
                start != null -> start
                item.endDate != null && item.endDate != item.date -> "기간"
                else -> "종일"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
            modifier = Modifier.width(52.dp).padding(top = 1.dp),
        )
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(item.academic?.let { scheduleColor(it.code) } ?: item.source.color),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.deadline) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "마감",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5484D),
                        modifier = Modifier.clip(CircleShape).background(palette.chip).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            val meta = buildList {
                // 학사일정은 종류로 — 면학감독은 제목이 선생님 이름뿐이라 '학사'만으론 무슨 일정인지 모릅니다.
                add(item.academic?.let { scheduleLabel(it.code) } ?: item.source.label)
                if (item.time?.contains('~') == true) add(item.time)
                item.endDate?.takeIf { it != item.date }?.let { add("~${shortDate(it)}") }
                item.detail?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelSmall, color = palette.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
