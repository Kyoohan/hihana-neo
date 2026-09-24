package com.yhjang.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 버전 로고 — iOS·One UI 처럼 큰 버전마다 그 버전의 정체성을 담은 표식. 12 는 AI 요약 카드와 같은 파랑·보라·분홍 그라데이션을
 * 굵은 숫자에 채우고, 흰 테두리·윗면 반사광으로 리퀴드 글래스를, 오른쪽 위 반짝이로 AI 요약을 표현합니다.
 * 업데이트 투어 첫 장과 하이하나 Neo 정보 화면에 씁니다. 다음 큰 버전에서 숫자·색·모양을 새로 정합니다.
 */
private val LogoBlue = Color(0xFF6EA8FF)
private val LogoViolet = Color(0xFFA78BFA)
private val LogoPink = Color(0xFFF29FC8)

@Composable
fun VersionLogo(size: Dp, modifier: Modifier = Modifier) {
    val number = "12"
    val fontSize = (size.value * 0.6f).sp
    val gradient = Brush.linearGradient(listOf(LogoBlue, LogoViolet, LogoPink))
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // 뒤에 번지는 빛
        Canvas(Modifier.size(size)) {
            drawCircle(
                Brush.radialGradient(
                    0f to LogoBlue.copy(alpha = 0.45f),
                    0.55f to LogoViolet.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    center = Offset(this.size.width * 0.42f, this.size.height * 0.42f),
                    radius = this.size.minDimension * 0.5f,
                ),
            )
        }
        val style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (-fontSize.value * 0.05f).sp)
        Text(number, style = style.copy(brush = gradient))
        // 유리 테두리 — 위가 밝고 아래로 옅어지는 흰 윤곽선.
        Text(
            number,
            style = style.copy(
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
            // AI 반짝이 — 큰 흰 별과 작은 분홍 별
            drawSparkle(Offset(w * 0.78f, w * 0.23f), w * 0.085f, Color.White)
            drawSparkle(Offset(w * 0.88f, w * 0.35f), w * 0.035f, LogoPink)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
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
