package com.yhjang.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * 버전 로고 — iOS·One UI 처럼 큰 버전마다 그 버전의 정체성을 담은 표식. 굵은 버전 숫자에 그 버전의 색 그라데이션을 채우고,
 * 흰 테두리·윗면 반사광(리퀴드 글래스)과 오른쪽 위 작은 표식으로 그 버전의 대표 기능을 나타냅니다.
 * 업데이트 투어 첫 장, 하이하나 Neo 정보 화면, 변경 사항의 버전 묶음 머리에 씁니다.
 */
private enum class Emblem { SPARKLE, MOON, SEATS, PILL, GLASS, APP_ICON, NONE }

private class VersionStyle(val colors: List<Color>, val emblem: Emblem, val emblemColor: Color)

/** 큰 버전별 색과 표식 — 그 버전 변경 사항의 첫 줄(대표 기능)에서 따왔습니다. */
private fun styleFor(major: Int): VersionStyle = when (major) {
    // 12 · 앱 안 게시글과 AI 요약 — AI 요약 카드의 파랑·보라·분홍, 반짝이
    12 -> VersionStyle(listOf(Color(0xFF6EA8FF), Color(0xFFA78BFA), Color(0xFFF29FC8)), Emblem.SPARKLE, Color.White)
    // 11 · 심야면학 — 밤하늘 남색·보라, 달
    11 -> VersionStyle(listOf(Color(0xFF5B6CFF), Color(0xFF8E6BFF), Color(0xFFC6B6FF)), Emblem.MOON, Color(0xFFFFE08A))
    // 10 · 좌석 배치도 신청 — 파랑·청록, 좌석 격자
    10 -> VersionStyle(listOf(Color(0xFF3B82F6), Color(0xFF22B8CF), Color(0xFF5EEAD4)), Emblem.SEATS, Color(0xFF5EEAD4))
    // 9 · 실시간 일정(Now Bar) — 주황·분홍, 알약
    9 -> VersionStyle(listOf(Color(0xFFFF8A3D), Color(0xFFF7568A), Color(0xFFFFB86B)), Emblem.PILL, Color(0xFFFFB86B))
    // 8 · 새 앱 아이콘·테마 아이콘 — 초록, H 가 든 작은 앱 아이콘
    8 -> VersionStyle(listOf(Color(0xFF34C77B), Color(0xFF7BE0A8), Color(0xFFB5F2CE)), Emblem.APP_ICON, Color(0xFF34C77B))
    // 7 · 리퀴드 글래스 하단 바 — 하늘·흰 유리, 빛이 맺힌 유리 방울
    7 -> VersionStyle(listOf(Color(0xFF7CC4FF), Color(0xFFB8E0FF), Color(0xFFFFFFFF)), Emblem.GLASS, Color(0xFFB8E0FF))
    // 6 이하 · One UI 리디자인 시기 — 차분한 회청색
    else -> VersionStyle(listOf(Color(0xFF8A94A6), Color(0xFFB7C0CF), Color(0xFFDDE3EC)), Emblem.NONE, Color.White)
}

/** 설치된 버전의 로고 — 업데이트 투어·정보 화면. */
@Composable
fun VersionLogo(size: Dp, modifier: Modifier = Modifier) {
    VersionLogo(BuildConfig.VERSION_NAME.substringBefore('.').filter(Char::isDigit).toIntOrNull() ?: 12, size, modifier)
}

@Composable
fun VersionLogo(major: Int, size: Dp, modifier: Modifier = Modifier) {
    val style = styleFor(major)
    val number = major.toString()
    val fontSize = (size.value * if (number.length > 1) 0.6f else 0.72f).sp
    val gradient = Brush.linearGradient(style.colors)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // 뒤에 번지는 빛
        Canvas(Modifier.size(size)) {
            drawCircle(
                Brush.radialGradient(
                    0f to style.colors[0].copy(alpha = 0.45f),
                    0.55f to style.colors[1].copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    center = Offset(this.size.width * 0.42f, this.size.height * 0.42f),
                    radius = this.size.minDimension * 0.5f,
                ),
            )
        }
        val text = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (-fontSize.value * 0.05f).sp)
        Text(number, style = text.copy(brush = gradient))
        // 유리 테두리 — 위가 밝고 아래로 옅어지는 흰 윤곽선.
        Text(
            number,
            style = text.copy(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f))),
                drawStyle = Stroke(width = size.value * 0.012f),
            ),
        )
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            // 윗면 반사광
            val arc = Path().apply {
                moveTo(w * 0.22f, w * 0.40f)
                quadraticTo(w * 0.5f, w * 0.29f, w * 0.78f, w * 0.40f)
            }
            drawPath(arc, Color.White.copy(alpha = 0.32f), style = Stroke(width = w * 0.016f, cap = StrokeCap.Round))
            drawEmblem(style.emblem, Offset(w * 0.8f, w * 0.22f), w, style.emblemColor, style.colors.last())
        }
    }
}

private fun DrawScope.drawEmblem(emblem: Emblem, c: Offset, w: Float, color: Color, accent: Color) {
    when (emblem) {
        Emblem.SPARKLE -> {
            drawSparkle(c, w * 0.085f, color)
            drawSparkle(Offset(w * 0.9f, w * 0.34f), w * 0.035f, Color(0xFFF29FC8))
        }
        Emblem.MOON -> {
            val r = w * 0.075f
            // 초승달 — 큰 원에서 비껴 놓은 원을 빼서 (배경색과 무관하게) 오려 냅니다.
            val moon = Path().apply {
                op(
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) },
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.45f, c.y - r * 0.3f), r * 0.85f)) },
                    androidx.compose.ui.graphics.PathOperation.Difference,
                )
            }
            drawPath(moon, color)
            drawSparkle(Offset(w * 0.9f, w * 0.34f), w * 0.028f, color)
        }
        Emblem.SEATS -> {
            val cell = w * 0.045f
            val gap = w * 0.018f
            for (row in 0 until 2) for (col in 0 until 3) {
                val filled = (row + col) % 2 == 0
                drawRoundRect(
                    if (filled) color else color.copy(alpha = 0.35f),
                    Offset(c.x - cell * 1.5f - gap + col * (cell + gap), c.y - cell - gap / 2 + row * (cell + gap)),
                    Size(cell, cell),
                    CornerRadius(cell * 0.3f),
                )
            }
        }
        Emblem.PILL -> {
            val h = w * 0.07f
            drawRoundRect(color, Offset(c.x - h * 1.4f, c.y - h / 2), Size(h * 2.8f, h), CornerRadius(h / 2))
            drawCircle(Color.White, h * 0.3f, Offset(c.x - h * 0.9f, c.y))
        }
        Emblem.GLASS -> {
            // 유리 방울 — 옅은 유리 안쪽, 흰 테두리, 왼쪽 위 반사 호, 오른쪽 아래 작은 반짝임.
            val r = w * 0.08f
            drawCircle(color.copy(alpha = 0.22f), r, c)
            drawCircle(Color.White.copy(alpha = 0.85f), r, c, style = Stroke(w * 0.008f))
            drawArc(
                Color.White,
                startAngle = 195f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.65f, c.y - r * 0.65f),
                size = Size(r * 1.3f, r * 1.3f),
                style = Stroke(r * 0.2f, cap = StrokeCap.Round),
            )
            drawCircle(Color.White.copy(alpha = 0.8f), r * 0.13f, Offset(c.x + r * 0.38f, c.y + r * 0.4f))
        }
        Emblem.APP_ICON -> {
            // 앱 아이콘 — 둥근 사각형 안에 H 모노그램.
            val r = w * 0.075f
            drawRoundRect(color, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), CornerRadius(r * 0.45f))
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(c.x - r, c.y - r), Size(r * 2, r * 2), CornerRadius(r * 0.45f), style = Stroke(w * 0.007f))
            val sw = r * 0.22f
            drawLine(Color.White, Offset(c.x - r * 0.4f, c.y - r * 0.45f), Offset(c.x - r * 0.4f, c.y + r * 0.45f), sw, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x + r * 0.4f, c.y - r * 0.45f), Offset(c.x + r * 0.4f, c.y + r * 0.45f), sw, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x - r * 0.4f, c.y), Offset(c.x + r * 0.4f, c.y), sw, StrokeCap.Round)
        }
        Emblem.NONE -> drawCircle(accent.copy(alpha = 0.8f), w * 0.03f, c)
    }
}

private fun DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + r * 0.12f, center.y - r * 0.12f, center.x + r, center.y)
        quadraticTo(center.x + r * 0.12f, center.y + r * 0.12f, center.x, center.y + r)
        quadraticTo(center.x - r * 0.12f, center.y + r * 0.12f, center.x - r, center.y)
        quadraticTo(center.x - r * 0.12f, center.y - r * 0.12f, center.x, center.y - r)
        close()
    }
    drawPath(p, color)
}
