package com.yhjang.timetable.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import com.yhjang.timetable.R
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import android.content.Context
import android.os.Build
import com.yhjang.timetable.PlanStore

/**
 * One UI 디자인 키트에서 뽑은 고정 토큰 — 컴포넌트 파일(OneUiComponents.kt)과 화면 코드가 같이 씁니다.
 * 색은 키트 PNG 를 직접 샘플링한 값입니다: 배경 #F1F1F3/#010102, 카드 #FCFCFF/#17171A, 강조 #387AFF.
 */
object OneUi {
    /** 카드·다이얼로그·그룹 컨테이너 모서리 — 삼성 헬스 홈 타일과 같은 크기 */
    val CornerLarge = 28.dp
    /** 카드 안쪽 작은 컨테이너(썸네일, 행 하이라이트) */
    val CornerMedium = 18.dp
    val CornerSmall = 12.dp
    /** 화면 좌우 여백 — 키트의 컨테이너는 가장자리에서 16dp 떨어져 있습니다 */
    val PagePadding = 16.dp
    /** 카드 안쪽 여백 */
    val CardPadding = 20.dp
    /** 리스트 행 하나의 좌우 여백 */
    val RowPadding = 20.dp

    val Blue = Color(0xFF387AFF)
    /** 스위치/라디오 off 상태 회색 (키트 샘플 #9A999E) */
    val SwitchOff = Color(0xFF9A999E)
    /** 슬라이더 비활성 트랙 (키트 샘플 #848487) */
    val SliderInactive = Color(0xFF848487)

    /** 다이얼로그 뒷배경 스크림 */
    val Scrim = Color(0x66000000)

    /** 원형 아이콘 배지(급식 끼니 등)에 쓰는 채도 높은 색 — 카드·배경은 무채색이고 색은 배지와 강조색에만 씁니다. */
    val Lime = Color(0xFF7CC22F)
    val Sky = Color(0xFF2FA8F0)
    val Violet = Color(0xFF8C5BE8)
    val Amber = Color(0xFFF2A11B)

    /** 설정 항목 옆 알림 점 — One UI 의 주황 점 */
    val NotifyDot = Color(0xFFFF7A1A)
}

/**
 * 페이지 배경 — 초록 기운이 도는 바탕에 은은한 광원 두 개와 미세한 그레인(아크릴 질감).
 * 카드는 불투명해서 이 배경은 카드 사이와, 글래스 요소(하단 바·알약·다이얼로그·툴바) 뒤로만 보입니다 —
 * 순수 검정/회색이면 블러가 티가 안 나서, 흐려질 만한 결을 배경에 둡니다. 라이트/다크가 다릅니다.
 */
@Composable
fun Modifier.oneUiPageBackground(): Modifier {
    val dark = MaterialTheme.colorScheme.isDark
    val grain = ImageBitmap.imageResource(LocalContext.current.resources, R.drawable.page_grain_tile)
    val grainBrush = remember(grain) { ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)) }
    val photo = LocalPageBackground.current
    return drawBehind {
        val w = size.width
        val h = size.height
        if (photo != null) {
            // 사용자가 고른 사진 — 화면을 꽉 채우도록 잘라 그리고, 카드·글자가 읽히도록 테마에 맞는 스크림을 얹습니다.
            val scale = maxOf(w / photo.width, h / photo.height)
            val dw = (photo.width * scale).toInt()
            val dh = (photo.height * scale).toInt()
            drawImage(
                photo,
                dstOffset = IntOffset(((w - dw) / 2f).toInt(), ((h - dh) / 2f).toInt()),
                dstSize = IntSize(dw, dh),
            )
            drawRect(if (dark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.32f))
            drawRect(brush = grainBrush, alpha = 0.04f)
            return@drawBehind
        }
        if (dark) {
            drawRect(Color(0xFF0B1512))
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF1F4A3A), Color.Transparent),
                    center = Offset(w * 0.9f, h * 0.08f),
                    radius = w * 0.95f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF163A2F).copy(alpha = 0.9f), Color.Transparent),
                    center = Offset(w * 0.05f, h * 0.7f),
                    radius = w * 0.85f,
                ),
            )
            drawRect(brush = grainBrush, alpha = 0.07f)
        } else {
            drawRect(Color(0xFFE4EEE8))
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFBFDCCB), Color.Transparent),
                    center = Offset(w * 0.9f, h * 0.08f),
                    radius = w * 0.95f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFCDE5D6), Color.Transparent),
                    center = Offset(w * 0.05f, h * 0.7f),
                    radius = w * 0.85f,
                ),
            )
            drawRect(brush = grainBrush, alpha = 0.06f)
        }
    }
}

/** 플로팅 알약(스크롤 시 액션 아이콘 묶음, 알림 카드, 칩·중립 버튼)의 색 — 카드보다 한 톤 진한 무채색. */
val ColorScheme.floatingPill: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme()) Color(0xFF2C2C2F) else Color(0xFFE3E3E7)


/**
 * One UI 는 M3 기본보다 전반적으로 더 둥급니다 — Shapes 를 쓰는 M3 컴포넌트(TextField 등)에도 반영됩니다.
 */
private val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(OneUi.CornerSmall),
    small = RoundedCornerShape(OneUi.CornerSmall),
    medium = RoundedCornerShape(OneUi.CornerMedium),
    large = RoundedCornerShape(OneUi.CornerLarge),
    extraLarge = RoundedCornerShape(OneUi.CornerLarge),
)

/**
 * One UI 타이포 — 삼성 기기에서는 sans-serif 가 SamsungOne/One UI Sans 로 매핑되므로 기본 패밀리를 그대로 두고,
 * 크기·굵기만 키트에 맞춥니다: 큰 제목은 굵게, 본문은 15sp 전후, 보조 텍스트는 회색.
 */
private val OneUiTypography = Typography(
    // 확장 헤더 큰 제목 (One UI 확장 앱바)
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    // 툴바 제목 / 다이얼로그 제목
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    // 카드 제목
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    // 목록 1차 텍스트 17sp / 본문 15sp / 보조 13sp — One UI 설정 목록과 같은 비율
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

/**
 * One UI 는 배경화면에서 뽑은 톤(Material You dynamic color)을 쓰지 않고, 무채색 회색 표면 위에
 * 파란색 하나만 강조색으로 씁니다. `surface` 는 카드(컨테이너) 색, `background` 는 그 뒤의 페이지 색입니다.
 */
private fun oneUiLightScheme(): ColorScheme = lightColorScheme(
    primary = OneUi.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0A3E8F),
    secondary = Color(0xFF5C5C61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6EA),
    onSecondaryContainer = Color(0xFF1B1B1F),
    tertiary = Color(0xFF34C759),
    error = Color(0xFFE5343A),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF8F1418),
    background = Color(0xFFE4EEE8),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFE9E9EC),
    onSurfaceVariant = Color(0xFF7A7A80),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F9),
    surfaceContainer = Color(0xFFFCFCFF),
    surfaceContainerHigh = Color(0xFFE9E9EC),
    surfaceContainerHighest = Color(0xFFDDDDE1),
    outline = Color(0xFFC4C4C9),
    outlineVariant = Color(0xFFE6E6EA),
    inverseSurface = Color(0xFF17171A),
    inverseOnSurface = Color(0xFFFCFCFF),
    inversePrimary = Color(0xFF9EC1FF),
    surfaceTint = Color.Transparent,
)

private fun oneUiDarkScheme(): ColorScheme = darkColorScheme(
    primary = OneUi.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A3C78),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB0B0B6),
    onSecondary = Color(0xFF17171A),
    secondaryContainer = Color(0xFF2C2C30),
    onSecondaryContainer = Color(0xFFE6E6EA),
    tertiary = Color(0xFF3DD463),
    error = Color(0xFFFF5C5C),
    onError = Color.White,
    errorContainer = Color(0xFF5A1D1F),
    onErrorContainer = Color(0xFFFFDAD8),
    background = Color(0xFF0B1512),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF17171A),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF2C2C30),
    onSurfaceVariant = Color(0xFFA0A0A6),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = Color(0xFF17171A),
    surfaceContainerHigh = Color(0xFF252528),
    surfaceContainerHighest = Color(0xFF343437),
    outline = Color(0xFF5C5C62),
    outlineVariant = Color(0xFF2C2C30),
    inverseSurface = Color(0xFFFCFCFF),
    inverseOnSurface = Color(0xFF17171A),
    inversePrimary = OneUi.Blue,
    surfaceTint = Color.Transparent,
)

/** 설정 화면의 프리셋 8색 — 각 항목의 ARGB int 는 [Color.toArgb] 로 얻습니다. */
val AccentPresets: List<Color> = listOf(
    OneUi.Blue,
    Color(0xFF5856D6), // indigo
    Color(0xFFAF52DE), // purple
    Color(0xFFFF2D55), // pink
    Color(0xFFFF3B30), // red
    Color(0xFFFF9500), // orange
    Color(0xFF34C759), // green
    Color(0xFF30B0C7), // teal
)

/** 배경 명도로 라이트/다크를 판별 — 시스템 설정이 아니라 실제 적용된 스킴을 봐야 색이 어긋나지 않습니다. */
private fun ColorScheme.isDarkScheme(): Boolean = background.luminance() < 0.5f

/** 현재 스킴이 다크인지 — 컴포넌트에서 순수 검정/흰색을 골라야 할 때 씁니다. */
val ColorScheme.isDark: Boolean
    @Composable @ReadOnlyComposable get() = isDarkScheme()

/** 무채색 스킴 위에 primary 계열만 고른 색으로 교체합니다. */
private fun ColorScheme.withAccent(accent: Color): ColorScheme = if (isDarkScheme()) {
    copy(
        primary = lerp(accent, Color.White, 0.12f),
        onPrimary = if (accent.luminance() > 0.6f) Color(0xFF111114) else Color.White,
        primaryContainer = lerp(accent, Color.Black, 0.55f),
        onPrimaryContainer = lerp(accent, Color.White, 0.80f),
        inversePrimary = accent,
    )
} else {
    copy(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.6f) Color(0xFF111114) else Color.White,
        primaryContainer = lerp(accent, Color.White, 0.82f),
        onPrimaryContainer = lerp(accent, Color.Black, 0.50f),
        inversePrimary = lerp(accent, Color.White, 0.35f),
    )
}

/**
 * 시스템(Material You) 테마 색 — 배경화면에서 뽑은 동적 색의 primary. Android 12 미만이면 null.
 * '자동' 강조색은 이 색을 쓰고, 없으면 One UI 파란색으로 떨어집니다. 표면·배경은 여전히 무채색입니다.
 */
fun systemAccentColor(context: Context): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching { Color(context.getColor(android.R.color.system_accent1_500)) }.getOrNull()
}

/**
 * One UI 테마 — 무채색 스킴을 기본으로 쓰고, [accentArgb] 가 [PlanStore.AUTO_ACCENT_COLOR](0) 이면
 * 시스템 테마 색(없으면 One UI 파란색)을, 아니면 고른 색을 강조색으로 얹습니다.
 */
@Composable
fun TimeTableTheme(
    accentArgb: Int = PlanStore.AUTO_ACCENT_COLOR,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) oneUiDarkScheme() else oneUiLightScheme()
    val context = LocalContext.current
    val accent = if (accentArgb == PlanStore.AUTO_ACCENT_COLOR) systemAccentColor(context) else Color(accentArgb)
    val colorScheme = if (accent == null) base else base.withAccent(accent)

    CompositionLocalProvider(
        // Material3 의 MaterialTheme 은 LocalContentColor 를 내려주지 않아 색 미지정 Text 가
        // 항상 검정으로 그려집니다 — 적용된 스킴의 onSurface 로 고정합니다.
        LocalContentColor provides colorScheme.onSurface,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = OneUiShapes,
            typography = OneUiTypography,
            content = content,
        )
    }
}
