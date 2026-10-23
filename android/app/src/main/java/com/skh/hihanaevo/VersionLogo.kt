package com.skh.hihanaevo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * 버전 로고 — 14부터 실크 무늬 판과 흰 경로 숫자, 이전 버전은 예전 글꼴 로고를 씁니다.
 * 업데이트 투어 첫 장, HiHana Evo 정보 화면, 변경 사항의 버전 묶음 머리에 씁니다.
 */
private enum class Emblem { SUN, SPARKLE, MOON, SEATS, PILL, GLASS, APP_ICON, NONE }

private class VersionStyle(val colors: List<Color>, val emblem: Emblem, val emblemColor: Color)

/** 13 이하의 색과 표식 — 예전 글꼴 로고를 그대로 유지합니다. */
private fun styleFor(major: Int): VersionStyle = when (major) {
    // 13 · 신청·내역을 앱 안에서, 귀가 기간 서울 날씨 — 맑은 하늘 파랑·청록에서 햇빛 노랑, 해
    13 -> VersionStyle(listOf(Color(0xFF4F9DFF), Color(0xFF35C2E0), Color(0xFFFFD27A)), Emblem.SUN, Color(0xFFFFC83D))
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
    if (major >= 14) {
        SilkVersionLogo(major, size, modifier.semantics { contentDescription = "버전 $major" })
        return
    }
    val style = styleFor(major)
    val number = major.toString()
    val fontSize = (size.value * if (number.length > 1) 0.6f else 0.72f).sp
    val gradient = Brush.linearGradient(style.colors)
    Box(modifier.size(size).semantics { contentDescription = "버전 $major" }, contentAlignment = Alignment.Center) {
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
        Emblem.SUN -> {
            // 해 — 가운데 원과 여덟 줄기 빛.
            val r = w * 0.042f
            drawCircle(color, r, c)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val dx = kotlin.math.cos(a).toFloat()
                val dy = kotlin.math.sin(a).toFloat()
                drawLine(color, Offset(c.x + dx * r * 1.55f, c.y + dy * r * 1.55f), Offset(c.x + dx * r * 2.25f, c.y + dy * r * 2.25f), w * 0.014f, StrokeCap.Round)
            }
        }
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

private class VersionDigit(val width: Float, val outline: Path)

/** 숫자 원형 — 64 높이의 굵은 띠, 속공간도 배경이 비치는 경로로 남깁니다. */
private val versionDigits by lazy {
    val outlines = listOf(
        "M21 0 C7 0 0 10 0 26 V38 C0 54 7 64 21 64 C35 64 42 54 42 38 V26 C42 10 35 0 21 0 Z " +
            "M21 13 C27 13 28 18 28 27 V37 C28 46 27 51 21 51 C15 51 14 46 14 37 V27 C14 18 15 13 21 13 Z",
        "M0 13 L14 2 Q17 0 20 0 H25 Q28 0 28 3 V61 Q28 64 25 64 H14 V18 L0 28 Z",
        "M1 7 Q10 0 22 0 C35 0 42 7 42 19 C42 30 34 36 26 42 L15 51 H42 V64 H0 V53 " +
            "C0 43 10 36 18 30 L24 25 Q28 22 28 18 Q28 12 21 12 Q11 12 3 20 Z",
        "M1 6 Q10 0 21 0 C34 0 41 7 41 17 Q41 27 32 31 C39 33 42 39 42 47 C42 58 35 64 21 64 " +
            "Q9 64 0 58 L3 45 Q11 52 20 52 Q29 52 29 45 Q29 38 18 38 H11 V26 H18 Q27 26 27 19 Q27 12 20 12 Q11 12 4 18 Z",
        "M23 0 H38 V37 H42 V50 H38 V64 H24 V50 H3 Q0 50 0 47 V36 Z M24 17 L12 37 H24 Z",
        "M3 0 H41 V13 H15 L14 24 Q18 23 23 23 C35 23 42 30 42 43 C42 57 33 64 20 64 " +
            "Q8 64 0 58 L3 45 Q11 52 19 52 Q28 52 28 44 Q28 36 18 36 Q10 36 1 40 Z",
        "M37 0 L40 12 C25 11 16 18 14 28 Q20 23 27 23 C37 23 42 31 42 42 C42 56 34 64 21 64 " +
            "C7 64 0 53 0 36 C0 14 14 0 37 0 Z M21 35 C16 35 14 39 14 43 C14 49 16 52 21 52 C26 52 28 49 28 43 C28 38 26 35 21 35 Z",
        "M3 0 H39 Q42 0 42 3 V11 L22 64 H7 L27 13 H0 V3 Q0 0 3 0 Z",
        "M21 0 C34 0 41 7 41 17 Q41 26 34 30 Q42 35 42 45 C42 57 34 64 21 64 C8 64 0 57 0 45 " +
            "Q0 35 8 30 Q1 25 1 17 C1 7 8 0 21 0 Z M21 11 C16 11 14 14 14 18 C14 23 17 25 21 25 " +
            "C25 25 28 23 28 18 C28 14 26 11 21 11 Z M21 36 C16 36 13 39 13 45 C13 50 16 53 21 53 C26 53 29 50 29 45 C29 39 26 36 21 36 Z",
        "M5 64 L2 51 C18 52 27 46 28 36 Q23 41 16 41 C5 41 0 33 0 22 C0 8 8 0 21 0 " +
            "C35 0 42 11 42 28 C42 50 28 64 5 64 Z M21 12 C16 12 14 15 14 21 C14 26 16 29 21 29 C26 29 28 25 28 21 C28 15 26 12 21 12 Z",
    )
    outlines.mapIndexed { digit, outline ->
        VersionDigit(
            if (digit == 1) 28f else 42f,
            PathParser().parsePathString(outline).toPath().apply { fillType = PathFillType.EvenOdd },
        )
    }
}

private class SilkPalette(
    val mint: Color,
    val cyan: Color,
    val blue: Color,
    val indigo: Color,
    val purple: Color,
    val gold: Color,
    val coral: Color,
    val pink: Color,
)

private val evoSilkPalette = SilkPalette(
    mint = Color(0xFF38EAA8),
    cyan = Color(0xFF25ACEA),
    blue = Color(0xFF0873F9),
    indigo = Color(0xFF2616D7),
    purple = Color(0xFF4925FB),
    gold = Color(0xFFFFE095),
    coral = Color(0xFFFF8B83),
    pink = Color(0xFFF24C9E),
)

/** 버전별 실크 색 — 새 버전은 한 줄을 더하고, 미정인 버전은 Evo 색을 씁니다. */
private val silkPalettes = mapOf(
    14 to evoSilkPalette,
)

@Composable
private fun SilkVersionLogo(major: Int, size: Dp, modifier: Modifier) {
    val palette = silkPalettes[major] ?: evoSilkPalette
    val digits = remember(major) { major.toString().map { versionDigits[it.digitToInt()] } }
    Canvas(
        modifier.size(size).drawWithCache {
            // 100 기준 좌표 — 경로·브러시·윤곽선은 크기·밀도·색이 바뀔 때 캐시를 갱신합니다.
            val unit = this.size.minDimension / 100f
            val origin = Offset((this.size.width - unit * 100f) / 2f, (this.size.height - unit * 100f) / 2f)
            // 스쿼클 — 네 곡선이 같은 접선과 곡률로 이어지고 판은 전체 폭의 88%를 씁니다.
            val plate = Path().apply {
                moveTo(50f, 6f)
                cubicTo(90f, 6f, 94f, 10f, 94f, 50f)
                cubicTo(94f, 90f, 90f, 94f, 50f, 94f)
                cubicTo(10f, 94f, 6f, 90f, 6f, 50f)
                cubicTo(6f, 10f, 10f, 6f, 50f, 6f)
                close()
            }
            // 넓은 세 면 — 가운데 인디고 위로 차가운 천과 따뜻한 천을 부드럽게 겹칩니다.
            val coolSilk = Path().apply {
                moveTo(-10f, -12f)
                lineTo(82f, -12f)
                cubicTo(76f, 6f, 55f, 11f, 42f, 23f)
                cubicTo(24f, 39f, 25f, 57f, 40f, 69f)
                cubicTo(58f, 84f, 53f, 105f, 34f, 114f)
                lineTo(-10f, 114f)
                close()
            }
            val warmSilk = Path().apply {
                moveTo(94f, -12f)
                cubicTo(98f, 10f, 83f, 20f, 68f, 33f)
                cubicTo(49f, 50f, 52f, 70f, 70f, 82f)
                cubicTo(84f, 92f, 90f, 103f, 87f, 112f)
                lineTo(112f, 112f)
                lineTo(112f, -12f)
                close()
            }
            val coolEdge = Path().apply {
                moveTo(82f, -12f)
                cubicTo(76f, 6f, 55f, 11f, 42f, 23f)
                cubicTo(24f, 39f, 25f, 57f, 40f, 69f)
                cubicTo(58f, 84f, 53f, 105f, 34f, 114f)
            }
            val warmEdge = Path().apply {
                moveTo(94f, -12f)
                cubicTo(98f, 10f, 83f, 20f, 68f, 33f)
                cubicTo(49f, 50f, 52f, 70f, 70f, 82f)
                cubicTo(84f, 92f, 90f, 103f, 87f, 112f)
            }
            val silkEdges = listOf(coolEdge, warmEdge)
            val centerBrush = Brush.linearGradient(
                0f to palette.purple, 0.48f to palette.indigo, 0.82f to palette.purple, 1f to palette.blue,
                start = Offset(32f, 5f), end = Offset(65f, 104f),
            )
            val coolBrush = Brush.linearGradient(
                0f to palette.mint, 0.36f to palette.cyan, 0.72f to palette.blue, 1f to palette.indigo,
                start = Offset(12f, 8f), end = Offset(42f, 100f),
            )
            val warmBrush = Brush.linearGradient(
                0f to palette.gold, 0.42f to palette.coral, 1f to palette.pink,
                start = Offset(92f, 9f), end = Offset(68f, 100f),
            )
            // 비스듬한 넓은 반사 — 블러 없이 투명한 그라데이션으로 천의 굴곡만 살립니다.
            val reflection = Brush.linearGradient(
                0f to Color.Transparent, 0.32f to Color.Transparent,
                0.52f to Color.White.copy(alpha = 0.12f), 0.72f to Color.Transparent, 1f to Color.Transparent,
                start = Offset(8f, 25f), end = Offset(88f, 80f),
            )
            val sheen = Brush.linearGradient(
                0f to Color.Transparent, 0.3f to Color.White, 0.65f to Color.White.copy(alpha = 0.35f), 1f to Color.Transparent,
                start = Offset(50f, 4f), end = Offset(50f, 100f),
            )
            // 64dp에서는 광택을 줄입니다 — 띠는 크기와 무관하게 넓은 세 면만 남깁니다.
            val sheenStrength = 0.55f + 0.45f * ((this.size.minDimension / density - 64f) / 48f).coerceIn(0f, 1f)
            val softSheen = Stroke(1.8f)
            val middleSheen = Stroke(0.9f)
            val fineSheen = Stroke(0.32f)
            val border = Stroke(0.6f)
            val shadowEdge = Stroke(1.3f)
            val ink = Color(0xFF14233D)
            val rim = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.02f)),
                Offset(15f, 6f), Offset(85f, 94f),
            )
            val numberWidth = digits.sumOf { it.width.toDouble() }.toFloat() + (digits.size - 1) * 5f
            val numberScale = minOf(48f / 64f, 72f / numberWidth)
            val numberOrigin = Offset((100f - numberWidth * numberScale) / 2f, (100f - 64f * numberScale) / 2f)
            val number = Path().apply {
                fillType = PathFillType.EvenOdd
                var x = 0f
                digits.forEach { digit ->
                    addPath(digit.outline, Offset(x, 0f))
                    x += digit.width + 5f
                }
            }
            val numberBrush = Brush.verticalGradient(
                0f to Color.White, 0.75f to Color.White, 1f to Color(0xFFF2F5FC),
                startY = 0f, endY = 64f,
            )
            onDrawBehind {
                withTransform({
                    translate(origin.x, origin.y)
                    scale(unit, unit, pivot = Offset.Zero)
                }) {
                    // 미세한 뒷단 — 밝은 바탕과 사진 위에서도 판의 경계만 가볍게 잡습니다.
                    withTransform({ translate(0f, 0.55f) }) {
                        drawPath(plate, ink.copy(alpha = 0.07f))
                        drawPath(plate, ink.copy(alpha = 0.035f), style = shadowEdge)
                    }
                    clipPath(plate) {
                        drawPath(plate, centerBrush)
                        drawPath(coolSilk, coolBrush)
                        drawPath(warmSilk, warmBrush)
                        drawPath(plate, reflection)
                        // 세 겹의 옅은 선 — 날카로운 테두리 대신 가장자리에서 짧게 사라지는 광택입니다.
                        for (edge in silkEdges) {
                            drawPath(edge, sheen, alpha = 0.04f * sheenStrength, style = softSheen)
                            drawPath(edge, sheen, alpha = 0.07f * sheenStrength, style = middleSheen)
                            drawPath(edge, sheen, alpha = 0.24f * sheenStrength, style = fineSheen)
                        }
                        drawPath(plate, rim, style = border)
                    }
                    drawPath(plate, ink.copy(alpha = 0.12f), style = border)
                    // 흰 경로 숫자 — 판 높이의 약 55%, 긴 버전 번호는 폭에 맞춰 함께 줄입니다.
                    withTransform({
                        translate(numberOrigin.x, numberOrigin.y)
                        scale(numberScale, numberScale, pivot = Offset.Zero)
                    }) {
                        withTransform({ translate(0f, 0.35f) }) {
                            drawPath(number, ink.copy(alpha = 0.14f))
                        }
                        drawPath(number, numberBrush)
                    }
                }
            }
        },
    ) {}
}
