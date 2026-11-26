package com.skh.hihanaevo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiRadio

/**
 * Now Bar 막대 모양 고르기 — 삼성 설정 › 디스플레이의 라이트/다크 고르기처럼, 실제 모양을 그린 그림 두 장을 나란히 두고 그 아래에
 * 이름과 라디오를 둡니다. 그림은 알림창에 뜨는 실시간 일정 카드(아이콘 원·제목·남은 시간·두 줄·막대)를 줄여 그린 것입니다.
 */
@Composable
fun LiveBarStylePicker(selected: LiveActivity.BarStyle, onSelect: (LiveActivity.BarStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = OneUi.RowPadding).padding(top = 16.dp, bottom = 14.dp)) {
        Text("막대 모양", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiveActivity.BarStyle.entries.forEach { style ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(OneUi.CornerMedium))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClickLabel = style.label,
                        ) { onSelect(style) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NowBarPreview(style)
                    Spacer(Modifier.height(10.dp))
                    Text(style.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    OneUiRadio(selected = style == selected)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(selected.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 알림창의 실시간 일정 카드를 줄여 그린 그림 — 알림창은 시스템 테마를 따르므로 그림도 그렇게(어두운 카드 / 밝은 카드). */
@Composable
private fun NowBarPreview(style: LiveActivity.BarStyle) {
    val dark = isSystemInDarkTheme()
    val card = if (dark) Color(0xFF1D1E24) else Color(0xFFF1F2F5)
    val strong = if (dark) Color.White else Color(0xFF16181D)
    val soft = if (dark) Color(0xFFC9CCD4) else Color(0xFF4A4E58)
    val accent = Color(0xFF5B67F1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUi.CornerMedium))
            .background(card)
            .padding(horizontal = 10.dp, vertical = 12.dp)
            // 그림이라 화면 낭독기에는 읽히지 않게 — 아래의 이름이 대신 읽힙니다.
            .clearAndSetSemantics { },
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_science), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A205 화학Ⅱ", fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = strong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(5.dp))
                Text("56:41", fontSize = 8.sp, lineHeight = 14.sp, color = soft, maxLines = 1)
            }
            // 좁은 그림이라 시각은 빼고 구간 이름만 — 투어의 큰 그림(3–4교시 · 10:20 – 12:10)과 같은 장면.
            Text("3–4교시 수업 중", fontSize = 8.sp, lineHeight = 12.sp, color = soft, maxLines = 1, overflow = TextOverflow.Clip)
            Text("다음 · B301 영어Ⅱ", fontSize = 8.sp, lineHeight = 12.sp, color = soft, maxLines = 1, overflow = TextOverflow.Clip)
            Spacer(Modifier.height(9.dp))
            LiveBarDrawing(style, accent, light = dark, modifier = Modifier.fillMaxWidth(), into = if (style == LiveActivity.BarStyle.NOW) 0.28f else 0.45f)
        }
    }
}

/** 그림 속 하루 막대의 칸(비율) — 그림은 좁아서 일곱 칸으로 줄여 그립니다(실제는 열 칸까지). 셋째 칸이 지금 일정. */
private val PreviewParts = listOf(0.17f, 0.12f, 0.23f, 0.11f, 0.14f, 0.11f, 0.12f)

/**
 * 실시간 일정 막대를 그대로 줄여 그린 그림 — 설정의 막대 모양 고르기와 투어가 함께 씁니다.
 * [DAY][LiveActivity.BarStyle.DAY]: 끝난 일정과 지금 일정의 지난 부분은 굵게, 지금 위치에 흰 점, 그 뒤는 가늘게(남은 일정은 회색).
 * [NOW][LiveActivity.BarStyle.NOW]: 지금 일정의 지난 만큼([into])과 남은 만큼, 두 조각 — 지난 조각은 [light] 면 흰색.
 * [parts] 는 하루 막대의 칸 비율(합 1), [current] 는 지금 일정 칸, [into] 는 지금 일정에서 지난 비율.
 */
@Composable
internal fun LiveBarDrawing(
    style: LiveActivity.BarStyle,
    accent: Color,
    light: Boolean,
    modifier: Modifier = Modifier,
    parts: List<Float> = PreviewParts,
    current: Int = 2,
    into: Float = 0.45f,
) {
    Canvas(modifier.height(10.dp)) {
        val y = size.height / 2
        val gap = 3.dp.toPx()
        val thick = 4.dp.toPx()
        val thin = 1.5.dp.toPx()
        val track = Color(0xFF8A919E)
        fun bar(from: Float, to: Float, color: Color, width: Float) {
            val inset = width / 2
            if (to - from > width) drawLine(color, Offset(from + inset, y), Offset(to - inset, y), strokeWidth = width, cap = StrokeCap.Round)
        }
        when (style) {
            LiveActivity.BarStyle.DAY -> {
                var x = 0f
                var dot = 0f
                parts.forEachIndexed { i, share ->
                    val end = x + size.width * share
                    val right = if (i == parts.lastIndex) end else end - gap
                    when {
                        i < current -> bar(x, right, accent, thick)
                        i == current -> {
                            dot = x + (right - x) * into.coerceIn(0f, 1f)
                            bar(x, dot, accent, thick)
                            bar(dot, right, accent.copy(alpha = 0.6f), thin)
                        }
                        else -> bar(x, right, track.copy(alpha = 0.7f), thin)
                    }
                    x = end
                }
                drawCircle(accent, radius = 4.2.dp.toPx(), center = Offset(dot, y))
                drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(dot, y))
            }
            LiveActivity.BarStyle.NOW -> {
                val split = size.width * into.coerceIn(0.05f, 0.95f)
                bar(0f, split - gap / 2, if (light) Color.White else Color(0xFF3A3D45), thick)
                bar(split + gap / 2, size.width, track, thick)
            }
        }
    }
}
