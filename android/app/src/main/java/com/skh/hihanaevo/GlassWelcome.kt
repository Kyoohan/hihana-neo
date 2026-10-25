package com.skh.hihanaevo

import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skh.hihanaevo.ui.PerformanceMode
import com.skh.hihanaevo.ui.isDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/*
 * 처음 설치 투어의 환영 화면 — 하늘과 구름 위에 떠 있는 맑은 유리 방울 하나, 그 속으로 쏟아져 들어가는 하이하나 스티커들,
 * 가운데 정렬 세 줄 헤드라인(한 구절을 긋고 고쳐 쓰기, 셋째 줄은 돌아가며 바뀜), 아래 진한 알약 버튼 하나.
 * Appllama 에서 Wabi 의 45장 전부와 다른 앱 환영 화면 16장을 보고 정한 틀에, 사용자가 고른 하늘·구름 시안의 결을 입혔습니다.
 *
 * 방울은 액체 유리 렌즈입니다 (AGSL, Android 13+): 하늘·구름·스티커가 그려진 층 전체에 셰이더를 걸어, 방울 안은 가운데를
 * 살짝 확대하고 테 가까이에서는 바깥 화면을 끌어와 눌러 담으며(구름 가장자리가 방울 테를 따라 휘어 보임) 빨강·파랑을 다른
 * 거리로 굴절시키고, 우윳빛·가는 밝은 테·아래쪽 빛 모임·점 반사를 얹습니다. 손가락으로 끌 수 있고, 놓으면 그 속도 그대로
 * 스프링으로 제자리에 돌아오며 움직이는 쪽으로 늘어납니다. 오프닝의 완성 로고가 날아와 방울 속 표식이 됩니다.
 */

/** 환영 화면 색 — 하늘·구름·글자·버튼. 색이 도는 건 하늘과 스티커뿐, 글자와 버튼은 무채색. */
private class WelcomeColors(
    val skyTop: Color,
    val skyBottom: Color,
    val ink: Color,
    val ctaFill: Color,
    val ctaText: Color,
    /** 구름에 곱할 색(밤하늘은 어둡게) — null 이면 그대로. */
    val cloudTint: Color?,
    /** 방울 속 우윳빛 정도. */
    val milk: Float,
)

private val LightWelcome = WelcomeColors(Color(0xFF4D94B2), Color(0xFF94B9CA), Color(0xFF1D2329), Color(0xFF1A1C1E), Color.White, null, 0.12f)
private val DarkWelcome = WelcomeColors(Color(0xFF0A1524), Color(0xFF223857), Color(0xFFEEF2F6), Color(0xFFEEF2F6), Color(0xFF15171A), Color(0xFF68748E), 0.06f)

/** 들어가는 연출의 곡선 — 강한 ease-out. */
private val WelcomeEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private const val HEAD_LINE1 = "하나고의 하루를"
private const val HEAD_STRUCK = "구글 클래스룸, 인트라넷"
private const val HEAD_TYPED = "HiHana Evo"
/** 셋째 줄 — 돌아가며 바뀝니다. 어느 것이든 "하나고의 하루를 HiHana Evo ___" 로 읽힙니다. */
private val HEAD_LINE3 = listOf("하나로 챙겨요", "한 곳에서 봐요", "한 손에 담아요")
private const val CTA_LABEL = "가 볼까요"

/** 구슬 속으로 쏟아지는 스티커 — 하이하나 기능 아이콘(설정·빠른 실행과 같은 색)과 학교생활 말풍선. 모두 흰 테두리로 오려 낸 한 스타일. */
private class IconSticker(val icon: Int, val color: Color)

private val IconStickers = listOf(
    IconSticker(R.drawable.ic_meal, Color(0xFFE65B17)),            // 급식
    IconSticker(R.drawable.ic_academic, Color(0xFF387AFF)),        // 시간표·학사
    IconSticker(R.drawable.ic_menu_book, Color(0xFF9B8AE6)),       // 도서관
    IconSticker(R.drawable.ic_chair, Color(0xFF6F8BE8)),           // 면학실 좌석
    IconSticker(R.drawable.ic_directions_run, Color(0xFF34B27D)),  // 외출·외박
    IconSticker(R.drawable.ic_bedtime, Color(0xFF4B4FC9)),         // 심야면학
    IconSticker(R.drawable.ic_school, Color(0xFF2E9FAE)),          // 클래스룸
    IconSticker(R.drawable.ic_weather_clear, Color(0xFFFDBE4E)),   // 날씨
    IconSticker(R.drawable.ic_inbox, Color(0xFFEC5881)),           // 알리미·게시판
    IconSticker(R.drawable.ic_widgets, Color(0xFF65C23B)),         // 위젯
    IconSticker(R.drawable.ic_science, Color(0xFF715AFF)),         // 수업
    IconSticker(R.drawable.ic_sports_soccer, Color(0xFF6868A3)),   // 체육
)

private val SpeechStickers = listOf("급식 뭐야?", "3교시 수학", "면학실 B-12", "외출 신청 완료!", "D-12 중간고사", "공지 떴다", "오늘 6교시까지", "반납일 내일")

/**
 * 스티커 하나의 길 — [sprite] 는 스티커 그림 번호(아이콘 먼저, 말풍선 뒤), [scale] 은 크기 배율, [lane] 은 화면 폭 기준 출발
 * 가로 위치(-1 .. 1), [period] 는 한 번 떨어지는 데 걸리는 시간(초), [start] 는 처음 떨어지기 시작하는 시각, [rot] 와 [spin] 은
 * 처음 기울기(도)와 떨어지며 도는 빠르기(도/초). 값은 고정 — 매번 같은 그림이 나와 확인하기 쉽습니다.
 */
private class Sticker(val sprite: Int, val scale: Float, val lane: Float, val period: Float, val start: Float, val sway: Float, val rot: Float, val spin: Float)

private val Stickers: List<Sticker> = run {
    // 무작위처럼 보이되 늘 같은 값 — 황금비로 흩습니다.
    val phi = 0.618034f
    List(32) { i ->
        val a = (i * phi) % 1f
        val b = (i * phi * phi + 0.37f) % 1f
        val c = (i * 0.381966f + 0.11f) % 1f
        val speech = i % 4 == 1
        Sticker(
            sprite = if (speech) IconStickers.size + (i / 4) % SpeechStickers.size else i % IconStickers.size,
            scale = if (speech) 0.78f + 0.26f * b else 0.5f + 0.5f * b * b,
            lane = a * 2f - 1f,
            // 떨어지는 시간을 비슷하게, 출발은 한 주기에 고르게 — 처음엔 빈 하늘에서 쏟아지기 시작해 이후로는 끊김 없이 고른 흐름.
            period = 3.8f + 0.8f * c,
            start = 0.1f + i * 0.125f,
            sway = (b - 0.5f) * 2f,
            rot = (c - 0.5f) * (if (speech) 22f else 50f),
            spin = (a - 0.5f) * (if (speech) 16f else 70f),
        )
    }.sortedBy { it.scale }   // 작은 스티커를 먼저(뒤에) 그립니다
}

/** 스티커의 위치·크기 배율·기울기 — 위에서 출발해 방울 가운데로 빨려 들어갑니다. [t] 초. 아직 안 나왔거나 삼켜진 순간이면 false. */
private fun stickerAt(s: Sticker, t: Float, w: Float, h: Float, orb: Offset, dp: Float, out: FloatArray): Boolean {
    if (t < s.start) return false
    val f = ((t - s.start) / s.period) % 1f
    // 떨어지는 동안 점점 빨라지다(중력) 방울 언저리에서 빨려 듭니다.
    val u = f.pow(1.3f)
    val x0 = w / 2f + s.lane * w * 0.46f
    val y0 = -0.08f * h
    val cx = orb.x + s.lane * w * 0.34f
    val cy = orb.y * 0.5f
    val iu = 1f - u
    var x = iu * iu * x0 + 2f * iu * u * cx + u * u * orb.x
    val y = iu * iu * y0 + 2f * iu * u * cy + u * u * orb.y
    x += sin(t * 1.3f + s.lane * 7f) * 6f * dp * s.sway * iu
    // 마지막 15% 동안 줄어들며 방울에 삼켜집니다.
    val swallow = 1f - smoothstep(0.85f, 1f, u)
    val scale = s.scale * (0.8f + 0.2f * iu) * swallow
    if (scale <= 0.01f) return false
    out[0] = x; out[1] = y; out[2] = scale; out[3] = s.rot + s.spin * (t - s.start)
    return true
}

private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * 맑은 유리 방울 셰이더 — 하늘·구름·스티커 층([content]) 전체에 걸립니다. 방울 밖은 그대로, 안은 얇은 유리 렌즈:
 * 가운데는 살짝 확대(0.86), 테로 갈수록 바깥 화면을 끌어와(최대 1.48R) 눌러 담아 구름 가장자리가 테를 따라 휩니다.
 * 빨강·파랑은 테 가까이에서 다른 거리로(분산). 우윳빛은 테로 갈수록 짙게, 가는 밝은 테, 아래쪽 빛 모임, 왼쪽 위 점 반사.
 * [axis]·[stretch] 는 끄는 방향으로 늘어나는 액체 변형(면적 보존).
 */
private object OrbLens {
    private const val AGSL = """
        uniform shader content;
        uniform float2 center;
        uniform float radius;
        uniform float2 axis;
        uniform float stretch;
        uniform float milk;

        half4 main(float2 p) {
            float2 d = p - center;
            float2 perp = float2(-axis.y, axis.x);
            float2 q = float2(dot(d, axis) / stretch, dot(d, perp) * stretch);
            float R = max(radius, 1.0);
            float r = length(q) / R;
            half4 bg = content.eval(p);
            float aa = 1.0 - smoothstep(1.0 - 1.2 / R, 1.0 + 0.4 / R, r);
            if (aa <= 0.0) return bg;

            float z = sqrt(max(1.0 - r * r, 0.0));
            float2 nq = q / R;
            float2 nd = nq / max(r, 0.001);

            // 얇은 유리 — 가운데는 살짝 확대, 테 가까이는 바깥 화면을 끌어와 눌러 담습니다.
            float bend = pow(1.0 - z, 1.6);
            float k = 0.86 + 0.62 * bend;
            float2 lq = nq * R * k;
            float2 ls = axis * (lq.x * stretch) + perp * (lq.y / stretch);
            float ab = 0.03 * bend;
            half4 cr = content.eval(center + ls * (1.0 + ab));
            half4 cg = content.eval(center + ls);
            half4 cb = content.eval(center + ls * (1.0 - ab));
            float3 col = float3(cr.r, cg.g, cb.b);

            // 우윳빛 — 가운데는 옅게, 테로 갈수록 밝게.
            col = mix(col, float3(1.0), milk + 0.22 * r * r * r);
            // 가는 밝은 테 — 왼쪽 위와 오른쪽 아래가 더 밝게.
            float rim = smoothstep(0.92, 0.985, r);
            float lit = 0.5 + 0.5 * abs(dot(nd, float2(-0.51, -0.86)));
            col = mix(col, float3(1.0), rim * 0.6 * lit);
            // 아래쪽 안 테두리에 빛이 모입니다.
            float lower = clamp(dot(nd, float2(0.3, 0.95)), 0.0, 1.0);
            col = mix(col, float3(1.0), smoothstep(0.72, 0.97, r) * lower * lower * 0.32);
            // 왼쪽 위 작은 점 반사.
            float2 hp = nq - float2(-0.42, -0.5);
            col = mix(col, float3(1.0), exp(-dot(hp, hp) * 70.0) * 0.5);

            return mix(bg, half4(half3(clamp(col, 0.0, 1.0)), 1.0), half(aa));
        }
    """

    fun create(): RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(AGSL) }.getOrNull() else null
}

/**
 * 환영 화면. [reveal] 은 오프닝의 어두운 바탕이 걷히는 정도(0..1) — 걷히는 동안 방울이 생기고 오프닝 로고가 그 속으로
 * 내려앉습니다(로고 자리는 [onLogoBox] 로 알립니다, 루트 기준). [skipIntro] 면 처음 연출 없이 끝 장면으로 — 투어 첫 장에서
 * 뒤로 돌아온 경우.
 */
@Composable
internal fun GlassWelcome(
    reveal: () -> Float,
    skipIntro: Boolean,
    onContinue: () -> Unit,
    onLogoBox: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val dp = density.density
    val colors = if (MaterialTheme.colorScheme.isDark) DarkWelcome else LightWelcome
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val lens = remember { OrbLens.create() }
    val shader = if (PerformanceMode.enabled) null else lens
    val latestOnContinue by rememberUpdatedState(onContinue)

    // 시계 — 그리기 단계에서만 읽어 재구성 없이 스티커가 흐릅니다. 끝 장면부터면 이미 흐르는 시각에서.
    val instant = reduceMotion || skipIntro
    val clock = remember { mutableFloatStateOf(if (instant) 4.2f else 0f) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { reveal() > 0f }.first { it }
        started = true
    }
    LaunchedEffect(started, reduceMotion) {
        if (!started || reduceMotion) return@LaunchedEffect
        val base = clock.floatValue
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = base + (it - t0) / 1_000_000_000f }
    }

    // 헤드라인 — '구글 클래스룸, 인트라넷'을 긋고, 'HiHana Evo'를 한 글자씩 치고(회색 → 먹색), 셋째 줄을 칩니다.
    // 그다음 셋째 줄은 몇 초마다 지우고 다음 말로 다시 칩니다.
    val strike = remember { Animatable(if (instant) 1f else 0f) }
    var typed by remember { mutableIntStateOf(if (instant) HEAD_TYPED.length else 0) }
    val typedSettle = remember { Animatable(if (instant) 1f else 0f) }
    var phrase by remember { mutableIntStateOf(0) }
    var line3 by remember { mutableIntStateOf(if (instant) HEAD_LINE3[0].length else 0) }
    val line3Settle = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!instant) {
            snapshotFlow { reveal() >= 0.55f }.first { it }
            delay(600)
            strike.animateTo(1f, tween(320, easing = WelcomeEase))
            delay(140)
            for (i in 1..HEAD_TYPED.length) { typed = i; delay(70) }
            launch { typedSettle.animateTo(1f, tween(240, easing = WelcomeEase)) }
            delay(200)
            for (i in 1..HEAD_LINE3[0].length) { line3 = i; delay(55) }
            line3Settle.animateTo(1f, tween(240, easing = WelcomeEase))
        }
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(2600)
            for (i in HEAD_LINE3[phrase].length - 1 downTo 0) { line3 = i; delay(26) }
            line3Settle.snapTo(0f)
            phrase = (phrase + 1) % HEAD_LINE3.size
            delay(120)
            for (i in 1..HEAD_LINE3[phrase].length) { line3 = i; delay(55) }
            line3Settle.animateTo(1f, tween(240, easing = WelcomeEase))
        }
    }

    // 방울 — 끄는 동안은 손가락 자리를 바로(held), 놓으면 그 자리·속도에서 제자리로 스프링(drag). 그리기 단계에서 읽습니다.
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var held by remember { mutableStateOf<Offset?>(null) }
    var heldVelocity by remember { mutableStateOf(Offset.Zero) }
    val stretchState = remember { floatArrayOf(1f, 0f, 0f) }   // 방향 x, y, 늘어남
    // 늘어남 — 끄는(또는 돌아오는) 속도 방향으로 최대 9%. 그리기 단계에서 불러 속도를 읽게 합니다.
    fun updateStretch(): FloatArray {
        val v = if (held != null) heldVelocity else drag.velocity
        val speed = hypot(v.x, v.y)
        if (speed > 1f) { stretchState[0] = v.x / speed; stretchState[1] = v.y / speed }
        stretchState[2] = (speed / (9000f * dp / 2.75f)).coerceIn(0f, 0.09f)
        return stretchState
    }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .drawBehind { drawRect(Brush.verticalGradient(listOf(colors.skyTop, colors.skyBottom))) }
            // 아래 투어 화면으로 손가락이 새지 않게 — 이 화면이 다 받습니다.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val orbRadius = min(w * 0.16f, 72f * dp)
        // 자리 잡기 — 세로 화면은 방울 가운데가 45% 높이, 헤드라인은 방울 아래 44dp. 가로 화면·작은 창·큰 글꼴에서
        // 헤드라인과 버튼이 들어가지 않으면 방울을 올리고(상태 바 아래까지), 그래도 모자라면 방울과 헤드라인 사이를 줄입니다.
        // 둘째 줄(지운 말 + HiHana Evo)이 한 줄에 안 들어가면 두 줄로 나눕니다.
        val measurer = rememberTextMeasurer()
        val textWidth = (w - 48f * dp).roundToInt().coerceAtLeast(1)
        val splitLine2 = remember(w, density.fontScale) {
            measurer.measure("$HEAD_STRUCK $HEAD_TYPED", HeadlineStyle).size.width > textWidth
        }
        val headH = remember(w, density.fontScale, splitLine2) {
            val longest = HEAD_LINE3.maxBy { measurer.measure(it, HeadlineStyle).size.width }
            val line2 = if (splitLine2) "$HEAD_STRUCK\n$HEAD_TYPED" else "$HEAD_STRUCK $HEAD_TYPED"
            measurer.measure("$HEAD_LINE1\n$line2\n$longest", HeadlineStyle, constraints = Constraints(maxWidth = textWidth)).size.height.toFloat()
        }
        val navBottomPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
        val statusTopPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
        val below = headH + (24f + 52f + 24f) * dp + navBottomPx         // 헤드라인 + 최소 간격 + 버튼 + 아래 여백
        val topLimit = statusTopPx + orbRadius + 12f * dp
        var gap = 44f * dp
        var orbY = min(h * 0.45f, h - below - gap - orbRadius)
        if (orbY < topLimit) { gap = (gap - (topLimit - orbY)).coerceAtLeast(20f * dp); orbY = topLimit }
        val orbHome = Offset(w / 2f, orbY)
        val logoSide = orbRadius * 0.86f
        var origin by remember { mutableStateOf(Offset.Zero) }
        val logoPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

        // 바닥 구름 — 화면 아래 3분의 1 정도. 텍스처 위쪽 절반 남짓은 투명(하늘)입니다.
        val clouds = ImageBitmap.imageResource(R.drawable.welcome_clouds)
        val cloudH = h * 0.62f
        val cloudW = max(w, cloudH * clouds.width / clouds.height)
        val cloudFilter = colors.cloudTint?.let { ColorFilter.tint(it, BlendMode.Modulate) }

        // 스티커 그림 — 종류마다 한 번만 그려 두고 매 프레임 크기·기울기만 바꿔 찍습니다.
        val painters = IconStickers.map { painterResource(it.icon) }
        val layoutDirection = LocalLayoutDirection.current
        val stickerMeasurer = rememberTextMeasurer()
        val sprites = remember(dp) {
            IconStickers.mapIndexed { i, k -> renderIconSticker(k.color, painters[i], dp, density, layoutDirection) } +
                SpeechStickers.map { renderSpeechSticker(it, stickerMeasurer, dp, density, layoutDirection) }
        }

        fun orbCenter(): Offset = orbHome + (held ?: drag.value)
        // 들어오는 정도 — 오프닝 바탕이 걷히는 동안 방울이 0.92 배에서 1 로 차오르며 나타납니다.
        fun appear(): Float = WelcomeEase.transform(smoothstep(0.05f, 0.85f, reveal()))

        // 1) 하늘·구름·스티커 층 + 방울 렌즈. 렌즈가 이 층 전체를 받아 방울 안에서 굴절시킵니다.
        val pos = remember { FloatArray(4) }
        Canvas(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer {
                    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val c = orbCenter()
                        val ap = appear()
                        val st = updateStretch()
                        shader.setFloatUniform("center", c.x, c.y)
                        shader.setFloatUniform("radius", if (ap <= 0f) 0f else orbRadius * (0.92f + 0.08f * ap))
                        shader.setFloatUniform("axis", st[0], st[1])
                        shader.setFloatUniform("stretch", 1f + st[2])
                        shader.setFloatUniform("milk", colors.milk)
                        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                    }
                },
        ) {
            drawRect(Brush.verticalGradient(listOf(colors.skyTop, colors.skyBottom)))
            drawImage(
                clouds,
                dstOffset = IntOffset(((w - cloudW) / 2f).roundToInt(), (h - cloudH * 0.94f).roundToInt()),
                dstSize = IntSize(cloudW.roundToInt(), cloudH.roundToInt()),
                colorFilter = cloudFilter,
            )
            val t = clock.floatValue
            val orb = orbCenter()
            val ap = appear()
            for (s in Stickers) {
                if (!stickerAt(s, t, w, h, orb, dp, pos)) continue
                // 렌즈가 없는 기기에선 방울 가까이에서 옅어져, 방울 위에 겹쳐 보이지 않게.
                val near = if (shader == null) smoothstep(orbRadius * 0.9f, orbRadius * 2.2f, hypot(pos[0] - orb.x, pos[1] - orb.y)) else 1f
                val alpha = ap * near
                if (alpha <= 0.01f) continue
                val img = sprites[s.sprite]
                val sw = img.width * pos[2]; val sh = img.height * pos[2]
                withTransform({ rotate(pos[3], Offset(pos[0], pos[1])) }) {
                    drawImage(
                        img,
                        dstOffset = IntOffset((pos[0] - sw / 2f).roundToInt(), (pos[1] - sh / 2f).roundToInt()),
                        dstSize = IntSize(sw.roundToInt(), sh.roundToInt()),
                        alpha = alpha,
                    )
                }
            }
            if (shader == null) drawFallbackOrb(orb, orbRadius * (0.92f + 0.08f * ap), ap)
        }

        // 2) 방울 속 표식 — 오프닝의 완성 로고가 이 자리로 내려앉습니다. 방울과 함께 움직이고 늘어납니다.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 오프닝이 로고를 이 자리까지 옮겨 오는 동안은 오프닝이 그립니다 — 다 앉은 뒤부터 여기서.
                    alpha = if (reveal() >= 1f) 1f else 0f
                },
        ) {
            val c = orbCenter()
            val st = updateStretch()
            val sx = 1f + st[2]
            val deg = (kotlin.math.atan2(st[1], st[0]) * 180f / PI).toFloat()
            translate(c.x, c.y) {
                drawContext.transform.rotate(deg, Offset.Zero)
                drawContext.transform.scale(sx, 1f / sx, Offset.Zero)
                drawContext.transform.rotate(-deg, Offset.Zero)
                drawEvoLogo(Rect(Offset(-logoSide / 2f, -logoSide / 2f), Size(logoSide, logoSide)), logoPaint)
            }
        }
        LaunchedEffect(origin, orbHome, logoSide) {
            onLogoBox(Rect(origin + orbHome - Offset(logoSide / 2f, logoSide / 2f), Size(logoSide, logoSide)))
        }

        // 3) 방울을 잡는 자리 — 방울을 따라다닙니다.
        Box(
            Modifier
                .offset { (orbCenter() - Offset(orbRadius * 1.25f, orbRadius * 1.25f)).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                .size(with(density) { (orbRadius * 2.5f).toDp() })
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    var raw = Offset.Zero
                    // 멀리 끌수록 덜 따라오는 고무줄. 잡을 때 고무줄 안쪽 값을 그대로 이어받습니다(돌아오는 중에 잡아도 튀지 않게).
                    val limit = orbRadius * 2.2f
                    fun band(x: Float) = sign(x) * limit * (1f - exp(-abs(x) / limit))
                    fun unband(y: Float) = -sign(y) * limit * kotlin.math.ln(1f - (abs(y) / limit).coerceAtMost(0.999f))
                    fun release() {
                        val v = tracker.calculateVelocity()
                        val from = held ?: return
                        val fling = hypot(v.x, v.y) > 900f * dp / 2.75f
                        scope.launch {
                            // 스프링 시작점을 손가락 자리로 맞춘 뒤에 놓아, 한 프레임도 제자리로 튀지 않습니다.
                            drag.snapTo(from)
                            held = null
                            heldVelocity = Offset.Zero
                            var ticked = false
                            drag.animateTo(
                                Offset.Zero,
                                // 손가락의 기세가 있었을 때만 살짝 튕깁니다.
                                spring(dampingRatio = if (fling) 0.55f else 1f, stiffness = Spring.StiffnessMediumLow),
                                initialVelocity = Offset(v.x, v.y),
                            ) {
                                if (!ticked && value.getDistance() < 2f * dp) {
                                    ticked = true
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                    }
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            val now = held ?: drag.value
                            raw = Offset(unband(now.x), unband(now.y))
                            held = now
                            scope.launch { drag.stop() }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            raw += amount
                            tracker.addPosition(change.uptimeMillis, raw)   // 상자가 방울을 따라 움직여, 상자 기준 위치 대신 누적 이동량으로
                            held = Offset(band(raw.x), band(raw.y))
                            heldVelocity = tracker.calculateVelocity().let { Offset(it.x, it.y) }
                        },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    )
                },
        )

        // 4) 헤드라인과 버튼.
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = navBottom + 24.dp)
                .graphicsLayer { alpha = smoothstep(0.35f, 1f, reveal()) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(with(density) { (orbHome.y + orbRadius + gap).toDp() }))
            Headline(
                ink = colors.ink,
                strike = strike.value,
                typed = typed,
                typedSettle = typedSettle.value,
                splitLine2 = splitLine2,
                line3 = HEAD_LINE3[phrase],
                line3Typed = line3,
                line3Settle = line3Settle.value,
            )
            Spacer(Modifier.weight(1f))
            WelcomeButton(CTA_LABEL, colors, width = with(density) { min(w * 0.72f, 360f * dp).toDp() }, onClick = { latestOnContinue() })
        }
    }
}

private val HeadlineStyle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp, textAlign = TextAlign.Center)

/** 타자 치는 글자 — 아직 안 친 글자는 자리만 차지하고(투명) 보이지 않아, 글자를 쳐도 줄이 흔들리지 않습니다. */
private fun typedText(text: String, count: Int, color: Color) = buildAnnotatedString {
    text.forEachIndexed { i, ch -> withStyle(SpanStyle(color = if (i < count) color else Color.Transparent)) { append(ch) } }
}

/**
 * 세 줄 헤드라인 — "하나고의 하루를 / ~~구글 클래스룸, 인트라넷~~ HiHana Evo / 하나로 챙겨요". 둘째 줄이 좁은 화면에 안 들어가면
 * 지운 말과 HiHana Evo 를 두 줄로. 치는 중인 글자는 회색, 다 치면 먹색으로.
 */
@Composable
private fun Headline(
    ink: Color,
    strike: Float,
    typed: Int,
    typedSettle: Float,
    splitLine2: Boolean,
    line3: String,
    line3Typed: Int,
    line3Settle: Float,
) {
    val style = HeadlineStyle
    val typing = ink.copy(alpha = 0.45f)
    Column(
        // 화면 읽기는 지운 말 없이 완성된 문장 하나로.
        Modifier.clearAndSetSemantics { heading(); contentDescription = "$HEAD_LINE1 $HEAD_TYPED ${HEAD_LINE3[0]}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(HEAD_LINE1, style = style, color = ink)
        // 둘째 줄은 한 덩어리 글로 — 한글과 영문이 같은 기준선에 앉고, 취소선은 지운 말의 글자 자리를 따라 긋습니다.
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = ink)) { append(HEAD_STRUCK) }
                append(if (splitLine2) "\n" else " ")
                HEAD_TYPED.forEachIndexed { i, ch -> withStyle(SpanStyle(color = if (i < typed) lerp(typing, ink, typedSettle) else Color.Transparent)) { append(ch) } }
            },
            style = style,
            onTextLayout = { layout = it },
            modifier = Modifier.drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                if (strike > 0f) drawStrike(ink, strike, l)
            },
        )
        Text(typedText(line3, line3Typed, lerp(typing, ink, line3Settle)), style = style)
    }
}

/** 취소선 — 지운 말([HEAD_STRUCK]) 글자 가운데 높이를 지나는 가는 줄, 왼쪽에서 오른쪽으로 그어집니다. */
private fun DrawScope.drawStrike(ink: Color, progress: Float, l: TextLayoutResult) {
    val first = l.getBoundingBox(0)
    val last = l.getBoundingBox(HEAD_STRUCK.length - 1)
    val y = l.getLineBaseline(0) - HeadlineStyle.fontSize.toPx() * 0.38f
    val over = 1.dp.toPx()
    val x0 = first.left - over
    drawLine(ink, Offset(x0, y), Offset(x0 + (last.right + over - x0) * progress, y), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
}

/** 아래 주 버튼 — 진한 알약 하나(폭은 화면의 72%, 넓은 화면에선 360dp 까지). 누르는 순간(손 닿자마자) 0.97 로 줄어듭니다. */
@Composable
private fun WelcomeButton(text: String, colors: WelcomeColors, width: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(if (pressed) 110 else 160, easing = WelcomeEase), label = "press")
    Box(
        Modifier
            .size(width, 52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .drawBehind { drawRect(colors.ctaFill) }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.ctaText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    }
}

/** 스티커 그림자·흰 테두리 공통 — 오려 낸 스티커처럼 흰 테 밖에 옅은 그림자. */
private fun DrawScope.stickerBase(shape: Path, dp: Float) {
    translate(0f, 1.5f * dp) { drawPath(shape, Color.Black.copy(alpha = 0.14f)) }
    drawPath(shape, Color.White)
}

/** 아이콘 스티커 — 흰 테두리로 오린 둥근 네모, 기능 색 위에 흰 아이콘. */
private fun renderIconSticker(color: Color, icon: Painter, dp: Float, density: Density, layoutDirection: LayoutDirection): ImageBitmap {
    val side = 64f * dp
    val pad = 4f * dp
    val px = (side + pad * 2).roundToInt()
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, ComposeCanvas(image), Size(px.toFloat(), px.toFloat())) {
        val outer = Path().apply { addRoundRect(RoundRect(Rect(pad, pad, pad + side, pad + side), CornerRadius(side * 0.3f))) }
        stickerBase(outer, dp)
        val inset = 3.5f * dp
        val inner = Rect(pad + inset, pad + inset, pad + side - inset, pad + side - inset)
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(color, Color.White, 0.22f), color, lerp(color, Color.Black, 0.12f)), inner.top, inner.bottom),
            topLeft = inner.topLeft, size = inner.size, cornerRadius = CornerRadius(side * 0.3f - inset),
        )
        val s = side * 0.5f
        translate((px - s) / 2f, (px - s) / 2f) {
            with(icon) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
    return image
}

/** 말풍선 스티커 — 흰 테두리로 오린 말풍선, 먹색 테와 굵은 손글씨 느낌의 짧은 말. */
private fun renderSpeechSticker(text: String, measurer: TextMeasurer, dp: Float, density: Density, layoutDirection: LayoutDirection): ImageBitmap {
    val ink = Color(0xFF1B1D20)
    val layout = measurer.measure(text, TextStyle(color = ink, fontSize = (13f / density.fontScale).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp))
    val padH = 11f * dp; val padV = 7f * dp; val cut = 3.5f * dp; val tail = 7f * dp; val pad = 4f * dp
    val bw = layout.size.width + padH * 2; val bh = layout.size.height + padV * 2
    val pxW = (bw + (cut + pad) * 2).roundToInt(); val pxH = (bh + tail + (cut + pad) * 2).roundToInt()
    val image = ImageBitmap(pxW, pxH)
    CanvasDrawScope().draw(density, layoutDirection, ComposeCanvas(image), Size(pxW.toFloat(), pxH.toFloat())) {
        val left = cut + pad; val top = cut + pad
        fun bubble(grow: Float) = Path().apply {
            addRoundRect(RoundRect(Rect(left - grow, top - grow, left + bw + grow, top + bh + grow), CornerRadius(bh / 2f + grow)))
            // 왼쪽 아래 꼬리
            moveTo(left + bw * 0.2f - grow, top + bh - 2f * dp)
            lineTo(left + bw * 0.14f - grow * 1.4f, top + bh + tail + grow)
            lineTo(left + bw * 0.36f + grow, top + bh - 2f * dp)
            close()
        }
        stickerBase(bubble(cut), dp)
        val b = bubble(0f)
        drawPath(b, Color.White)
        drawPath(b, ink, style = Stroke(width = 1.5f * dp))
        drawText(layout, topLeft = Offset(left + padH, top + padV))
    }
    return image
}

/** 렌즈 셰이더를 못 쓰는 기기(Android 12 이하)·성능 모드 — 같은 자리의 맑은 유리 방울(옅은 흰 막 + 밝은 테 + 반사광). */
private fun DrawScope.drawFallbackOrb(c: Offset, r: Float, ap: Float) {
    if (ap <= 0f) return
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f * ap), Color.White.copy(alpha = 0.26f * ap)), center = c, radius = r), r, c)
    drawCircle(Color.White.copy(alpha = 0.7f * ap), r - 0.75f * density, c, style = Stroke(1.5f * density))
    drawArc(
        Color.White.copy(alpha = 0.75f * ap), startAngle = 200f, sweepAngle = 60f, useCenter = false,
        topLeft = c - Offset(r * 0.8f, r * 0.8f), size = Size(r * 1.6f, r * 1.6f),
        style = Stroke(width = r * 0.08f, cap = StrokeCap.Round),
    )
}
