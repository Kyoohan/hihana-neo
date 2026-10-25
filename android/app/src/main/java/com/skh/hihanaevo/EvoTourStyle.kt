package com.skh.hihanaevo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.skh.hihanaevo.ui.HiHanaEvoTheme

/*
 * 처음 설치 투어의 결 — 오프닝 애니메이션과 같은 세계로: 깊은 남색 바탕(오프닝 바탕 그대로), 로고 세 갈래의 민트·인디고·코랄이
 * 은은한 빛무리와 비단 리본으로 깔리고, 제목은 오프닝 부제와 같은 Pretendard, 머리말과 진행 점은 'Evo' 글자의 그라데이션,
 * 주 버튼은 흰 알약. 어두운 투어의 문법(진한 바탕 + 흰 굵은 제목 + 옅은 보조 글 + 비치는 카드 + 밝은 알약 하나)은 Appllama 에서
 * Night Sky·MoonX·17V28 의 온보딩을 보고 맞췄습니다. 업데이트 투어는 예전 그대로(사진 배경 위 서리 판)입니다.
 */

/** 지금 그리는 투어가 오프닝 결(처음 설치 투어)인지 — 진행 점·주 버튼·머리말이 보고 모양을 바꿉니다. */
internal val LocalEvoTour = staticCompositionLocalOf { false }

/** 오프닝 바탕과 같은 남색 세 단. */
private val EvoNavy = listOf(Color(0xFF090A1C), Color(0xFF11102B), Color(0xFF0A0A18))

/** 로고 세 갈래의 색 — 민트, 인디고, 코랄 (오프닝 부록 벡터의 가운데 색). */
internal val EvoMint = Color(0xFF38EAA8)
internal val EvoIndigo = Color(0xFF4925FB)
internal val EvoCoral = Color(0xFFFF5A97)

/** 'Evo' 글자와 같은 그라데이션 — 머리말 글자와 지금 장 진행 점. */
internal val EvoTourBrush = Brush.linearGradient(listOf(EvoMint, Color(0xFF8C6BFF), EvoCoral))

/** 주 버튼 — 남색 위의 흰 알약과 그 위 글자. */
internal val EvoButtonFill = Color(0xFFF4F5FB)
internal val EvoButtonText = Color(0xFF0B0C1E)

/**
 * 처음 설치 투어의 테마 — 앱 테마와 상관없이 어두운 스킴에 면 색만 오프닝 남색 쪽으로 물들이고(카드가 남색 위에 비치게),
 * 제목 글꼴을 Pretendard 로 바꿉니다. 강조색은 사용자가 고른 색 그대로(화면 장에서 고르면 바로 바뀜).
 */
@Composable
internal fun EvoTourTheme(accentArgb: Int, content: @Composable () -> Unit) {
    HiHanaEvoTheme(accentArgb = accentArgb, darkTheme = true) {
        val base = MaterialTheme.colorScheme
        val scheme = remember(base) {
            base.copy(
                background = EvoNavy[1],
                surface = Color(0xFF17183A),
                surfaceContainer = Color(0xFF17183A),
                surfaceContainerLow = Color(0xFF111230),
                surfaceContainerHigh = Color(0xFF22234A),
                surfaceContainerHighest = Color(0xFF2C2D58),
                surfaceVariant = Color(0xFF26274E),
                onSurfaceVariant = Color(0xFFA9ABCB),
                secondaryContainer = Color(0xFF26274E),
            )
        }
        val type = MaterialTheme.typography
        val typography = remember(type) {
            type.copy(
                headlineSmall = type.headlineSmall.copy(fontFamily = PRETENDARD, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
                titleLarge = type.titleLarge.copy(fontFamily = PRETENDARD),
                titleMedium = type.titleMedium.copy(fontFamily = PRETENDARD),
                labelLarge = type.labelLarge.copy(fontFamily = PRETENDARD),
            )
        }
        MaterialTheme(colorScheme = scheme, shapes = MaterialTheme.shapes, typography = typography, content = content)
    }
}

/** 머리말 글자 — 'Evo' 그라데이션으로 칠한 작은 글자. */
internal fun evoEyebrowStyle(base: TextStyle): TextStyle = base.copy(brush = EvoTourBrush, fontFamily = PRETENDARD)

/**
 * 처음 설치 투어의 바탕 — 오프닝 남색 위에 로고 세 갈래 색의 빛무리 셋과 비단 리본 두 줄, 아주 옅은 결(그레인).
 * [pagePosition] 은 지금 장(소수점 = 넘기는 중) — 넘길 때 빛과 리본이 살짝 따라 흘러 장이 이어진 공간처럼 보입니다.
 * 넘기지 않는 동안은 다시 그리지 않습니다(그리기 단계에서만 읽음).
 */
@Composable
internal fun EvoTourBackdrop(pagePosition: () -> Float, modifier: Modifier = Modifier) {
    val grain = ImageBitmap.imageResource(LocalContext.current.resources, R.drawable.page_grain_tile)
    val grainBrush = remember(grain) { ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)) }
    val ribbon = remember { Path() }
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width; val h = size.height
                val p = pagePosition()
                // 장마다 빛이 화면 폭의 6%씩 옆으로 — 끝까지 넘겨도 화면 밖으로 나가지 않게 천천히 감기는 사인 곡선.
                val drift = kotlin.math.sin(p * 0.45f) * w * 0.12f
                drawRect(Brush.verticalGradient(EvoNavy))
                glow(EvoIndigo, 0.30f, Offset(w * 0.86f - drift, h * 0.30f), w * 0.95f)
                glow(EvoMint, 0.16f, Offset(w * 0.08f + drift, h * 0.04f), w * 0.75f)
                glow(EvoCoral, 0.17f, Offset(w * 0.34f + drift * 0.6f, h * 1.02f), w * 0.85f)
                drawRibbons(ribbon, w, h, drift)
                drawRect(grainBrush, alpha = 0.05f)
            },
    )
}

private fun DrawScope.glow(color: Color, alpha: Float, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center, radius), radius, center)
}

/**
 * 비단 리본 — 오프닝의 갈래가 화면 밖에서 흩날려 들어오던 그 곡선을, 아래쪽을 가로지르는 옅은 두 띠로. 가장자리는 여러 번 겹쳐
 * 그어 부드럽게(흐림 효과 없이).
 */
private fun DrawScope.drawRibbons(path: Path, w: Float, h: Float, drift: Float) {
    fun band(y0: Float, y1: Float, lift: Float, alpha: Float, width: Float) {
        path.reset()
        path.moveTo(-w * 0.1f, h * y0)
        path.cubicTo(w * 0.3f + drift, h * (y0 - lift), w * 0.62f - drift, h * (y1 + lift), w * 1.1f, h * y1)
        val brush = Brush.linearGradient(listOf(EvoMint, EvoIndigo, EvoCoral), Offset(0f, h * y0), Offset(w, h * y1))
        for (k in 3 downTo 1) {
            drawPath(path, brush, alpha = alpha / (k + 1), style = Stroke(width = width * k, cap = StrokeCap.Round))
        }
    }
    band(0.80f, 0.64f, 0.10f, 0.30f, w * 0.045f)
    band(0.90f, 0.78f, -0.06f, 0.20f, w * 0.03f)
}
