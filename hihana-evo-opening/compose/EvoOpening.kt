package com.skh.hihanaevo

/*
 * HiHana Evo — 오프닝 애니메이션 (Jetpack Compose)
 *
 * 연출: 로고의 세 갈래(민트 · 인디고 · 코랄)가 화면 밖에서 가늘고 긴 실크 리본으로 흩날려 들어와,
 *       자리에 닿는 부분부터 로고 조각의 형태로 감싸지며 완성된다. 민트와 코랄이 먼저, 인디고 다리가 마지막.
 *       완성 후 아주 작은 숨(2.2%) 한 번, 대각선 하이라이트 한 번, 글자 등장, 0.6초 페이드아웃.
 *
 * 구현 요약 (같은 연출의 Lottie 는 lottie/source 의 파이썬 생성기가 이 수식을 그대로 베이크한 것)
 *   1) 각 갈래에 재료 좌표 (u 길이, v 폭)를 붙인다. 착지 모양 P(u, v) 는 부록 벡터의 정확한 점이다.
 *   2) 섹션 u 는 출발 시각 ts(u) = T0 + κ·w(u) 부터 감쇠 스프링으로 접근 경로를 따라 날아온다.
 *      비행 중에는 가는 띠(폭 wf)로 비틀리고(θ) 흩날리며, 뒷면(cosθ < 0)은 어둡게 칠한다.
 *   3) X(u, v, t) = O(t) + (1 − α)·비행모양 + α·P(u, v).  자리에 닿는 섹션부터 α → 1, O → 0 이므로
 *      착지한 부분부터 로고 형태가 잡히고, 다 멈춘 뒤에는 정확히 원본 벡터다 (바꿔 끼우는 컷 없음).
 *   4) 그리기: 면을 u 띠 세그먼트로 나눠 경로(3차 베지어)를 매 프레임 워프하고, 그라데이션은 세그먼트별
 *      아핀 맞춤으로 따라가게 한다. 경로·셰이더·행렬은 처음에 만들어 재사용 (프레임당 할당 없음).
 *   5) 시간은 Animatable 하나(선형)로 돌리고 그리기 단계에서만 읽는다 → 재구성 없음.
 *
 * API: Path / LinearGradient / Matrix / Canvas.drawPath 만 쓴다 (API 1+). drawVertices 같은
 *      API 29+ 전용 기능을 쓰지 않으므로 minSdk 26~28 에서도 같은 경로로 그린다.
 */

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2.0 * PI).toFloat()

// 화면·글자 타이밍
private const val SKIP_AFTER = 0.6f           // 이 시각 이후 탭하면 바로 끝
private const val FADE = 0.6f                 // 마지막 페이드아웃 길이
private const val TEXT_STAGGER = 0.16f
private const val TEXT_DUR = 0.5f
private const val T_STILL = 5.4f              // '애니메이션 끄기' 일 때 보여 줄 정지 장면 시각
private const val REDUCED_HOLD_MS = 1400L

private val BACKGROUND = Brush.verticalGradient(listOf(Color(0xFF090A1C), Color(0xFF11102B), Color(0xFF0A0A18)))
private val EVO_BRUSH = Brush.linearGradient(listOf(Color(0xFF38EAA8), Color(0xFF8C6BFF), Color(0xFFFF5A97)))

// ── BEGIN 생성된 상수 (lottie/source/gen_kotlin_consts.py, 시안 A) — Lottie 와 같은 숫자. 손으로 고치지 말 것 ──
private const val TOTAL = 6.5f            // 전체 길이 (초)
private const val LOGO_CX = 204.0f; private const val LOGO_CY = 217.0f
private const val OVERLAP = 0.004f        // 세그먼트 겹침 (u)
/** 각 갈래가 눈에 멈추는 시각 (Lottie 마커 land_mint / land_indigo / land_coral 과 같은 프레임) → 햅틱 틱 */
private val LAND_MARKS = floatArrayOf(2.383333f, 3.45f, 2.683333f)
private const val TEXT_T0 = 4.433333f            // 글자 등장 = logo_complete (프레임 266)
private const val BREATH_T0 = 3.3f; private const val BREATH_T1 = 3.9f; private const val BREATH_AMP = 0.022f
private const val HL_T0 = 3.52f; private const val HL_T1 = 4.42f; private const val HL_PEAK = 0.3f
private const val HL_DIR_X = 0.545f; private const val HL_DIR_Y = 0.838f; private const val HL_SPAN = 290.0f; private const val HL_WIDTH = 70.0f

/** 갈래 순서: 0 = 왼쪽(민트), 1 = 가운데(인디고), 2 = 오른쪽(코랄). (길이축 L, 단면 방향 S) */
private val RIBBON_FRAMES = arrayOf(
    floatArrayOf(0.0f, 1.0f, 1.0f, 0.0f),   // left
    floatArrayOf(179.0f, 46.0f, 1.0f, -0.69f),   // center
    floatArrayOf(0.0f, 1.0f, 1.0f, 0.0f),   // right
)
private val SEGMENTS = intArrayOf(12, 10, 12)

private val CHOREO = arrayOf(
    RibbonCfg(   // left
        order = 0, uc = 0.5f, t0 = -0.05f, kappa = 0.7f,
        fx = -560.0f, fy = 450.0f, h0x = 420.0f, h0y = -30.0f, dinX = 0.35f, dinY = -0.94f, h1 = 430.0f, wf = 58.0f,
        flutter = floatArrayOf(46.0f, 0.6f, 0.6f, 0.0f, 12.0f, 1.6f, 1.1f, 1.3f),
        twist = floatArrayOf(2.6f, 0.8f, 0.35f, 0.4f, 0.7f, 1.9f, 0.8f, 2.0f),
        omega = 4.8f, zeta = 0.9f, sW0 = 1.55f, sW1 = 1.95f, sa0 = 0.88f, sa1 = 1.3f, sf0 = 0.45f, sf1 = 1.05f, st0 = 0.55f, st1 = 0.95f, kp = 0.24f, taper = 0.22f,
        eps = 0.2f, spread = 0.0f, twistLag = 0.25f, drape = 0.3f, v0 = 0.0f,
    ),
    RibbonCfg(   // center
        order = 0, uc = 0.5f, t0 = 1.2f, kappa = 0.58f,
        fx = 640.0f, fy = 540.0f, h0x = -50.0f, h0y = -350.0f, dinX = -0.8f, dinY = -0.6f, h1 = 330.0f, wf = 60.0f,
        flutter = floatArrayOf(28.0f, 0.75f, 0.8f, 1.0f, 8.0f, 1.9f, 1.4f, 0.0f),
        twist = floatArrayOf(2.2f, 0.9f, 0.5f, 0.0f, 0.6f, 2.0f, 1.0f, 0.4f),
        omega = 4.8f, zeta = 0.9f, sW0 = 1.55f, sW1 = 1.95f, sa0 = 0.88f, sa1 = 1.3f, sf0 = 0.35f, sf1 = 0.8f, st0 = 0.55f, st1 = 0.95f, kp = 0.24f, taper = 0.22f,
        eps = 0.2f, spread = 0.0f, twistLag = 0.25f, drape = 0.3f, v0 = 0.0f,
    ),
    RibbonCfg(   // right
        order = 1, uc = 0.5f, t0 = 0.25f, kappa = 0.7f,
        fx = 560.0f, fy = -450.0f, h0x = -420.0f, h0y = 30.0f, dinX = -0.35f, dinY = 0.94f, h1 = 430.0f, wf = 58.0f,
        flutter = floatArrayOf(46.0f, 0.6f, -0.6f, 2.0f, 12.0f, 1.6f, -1.1f, 0.3f),
        twist = floatArrayOf(2.6f, 0.8f, -0.35f, 2.1f, 0.7f, 1.9f, -0.8f, 0.7f),
        omega = 4.8f, zeta = 0.9f, sW0 = 1.55f, sW1 = 1.95f, sa0 = 0.88f, sa1 = 1.3f, sf0 = 0.45f, sf1 = 1.05f, st0 = 0.55f, st1 = 0.95f, kp = 0.24f, taper = 0.22f,
        eps = 0.2f, spread = 0.0f, twistLag = 0.25f, drape = 0.3f, v0 = 1.0f,
    ),
)

/** 부록 벡터 (레이어 순서 뒤 → 앞). 경로 문자열은 시작할 때 한 번만 파싱한다. */
private val SURFACES = listOf(
    Surface(
        "01_left_tail", 0,
        "M0 298 C5 268 14 250 40 225 C49 237 63 243 84 249 L138 262.904192 L138 312 C138 340 122 355 100 370 L27 409 C10 421 0 412 0 396 Z",
        45.0f, 229.0f, 90.0f, 417.0f,
        floatArrayOf(0.0f, 0.48f, 1.0f),
        intArrayOf(0xFF2850F5.toInt(), 0xFF526FFF.toInt(), 0xFF829CFF.toInt()),
    ),
    Surface(
        "02_right_back", 2,
        "M387 13 C400 5 408 12 408 28 L408 141 C407 175 389 197 364 215 L266 284 L266 125 C266 100 277 82 296 69 Z",
        385.0f, 13.0f, 300.0f, 219.0f,
        floatArrayOf(0.0f, 0.25f, 0.6f, 1.0f),
        intArrayOf(0xFFFFE095.toInt(), 0xFFFFCB88.toInt(), 0xFFFF8B83.toInt(), 0xFFF24C9E.toInt()),
    ),
    Surface(
        "03_center_bridge", 1,
        "M150 147 L329 193 C343 197 355 204 364 215 C338 239 296 264 251 292 L84 249 C63 243 49 237 40 225 C69 200 108 173 150 147 Z",
        100.0f, 206.0f, 300.0f, 240.0f,
        floatArrayOf(0.0f, 0.5f, 1.0f),
        intArrayOf(0xFF2616D7.toInt(), 0xFF4925FB.toInt(), 0xFF3C14C3.toInt()),
    ),
    Surface(
        "04_center_fold", 1,
        "M266 176.810056 L329 193 C343 197 355 204 364 215 C338 239 296 264 266 282.666667 Z",
        266.0f, 180.0f, 351.0f, 250.0f,
        floatArrayOf(0.0f, 0.65f, 1.0f),
        intArrayOf(0xFF652BB4.toInt(), 0xFF45199D.toInt(), 0xFF37108F.toInt()),
    ),
    Surface(
        "05_left_front", 0,
        "M164 0 C186 0 198 14 198 40 L198 66 C198 104 180 127 150 147 C110 173 69 200 40 225 C14 250 5 268 0 298 L0 157 C0 116 21 87 52 70 L143 10 C151 5 158 0 164 0 Z",
        110.0f, 10.0f, 101.0f, 278.0f,
        floatArrayOf(0.0f, 0.32f, 0.58f, 0.8f, 1.0f),
        intArrayOf(0xFF38EAA8.toInt(), 0xFF27D9B8.toInt(), 0xFF25ACEA.toInt(), 0xFF0873F9.toInt(), 0xFF4766FF.toInt()),
    ),
    Surface(
        "06_right_front", 2,
        "M408 141 C407 175 389 197 364 215 C339 239 296 265 251 292 L235 302 C215 318 202 338 202 362 L202 399 C202 424 218 440 242 430 C264 419 314 386 357 357 C389 337 408 308 408 271 Z",
        406.0f, 186.0f, 217.0f, 415.0f,
        floatArrayOf(0.0f, 0.35f, 0.7f, 1.0f),
        intArrayOf(0xFFFFCF92.toInt(), 0xFFFFA48E.toInt(), 0xFFFF5A97.toInt(), 0xFFDF39A8.toInt()),
    ),
)
private const val SILHOUETTE =
    "M164 0 C186 0 198 14 198 40 L198 66 C198 104 180 127 150 147 L266 176.810056 L266 125 C266 100 277 82 296 69 L387 13 C400 5 408 12 408 28 L408 271 C408 308 389 337 357 357 C314 386 264 419 242 430 C218 440 202 424 202 399 L202 362 C202 338 215 318 235 302 L251 292 L138 262.904192 L138 312 C138 340 122 355 100 370 L27 409 C10 421 0 412 0 396 L0 157 C0 116 21 87 52 70 L143 10 C151 5 158 0 164 0 Z"
// ── END 생성된 상수 ──

// ─────────────────────────────────────────────────────────────────────────────
// 1. 로고 기하 — 경로 파싱, 갈래별 재료 좌표 (u, v), 세그먼트 분할  (처음 한 번만 계산)
// ─────────────────────────────────────────────────────────────────────────────

/** 3차 베지어 조각 하나: x0 y0 x1 y1 x2 y2 x3 y3. 직선도 3차로 바꿔 한 가지 형태로 다룬다. */
private typealias Cub = FloatArray

private fun lineCub(x0: Float, y0: Float, x3: Float, y3: Float): Cub = floatArrayOf(
    x0, y0, x0 + (x3 - x0) / 3f, y0 + (y3 - y0) / 3f, x0 + 2f * (x3 - x0) / 3f, y0 + 2f * (y3 - y0) / 3f, x3, y3,
)

private fun bezX(c: Cub, t: Float): Float {
    val m = 1f - t
    return m * m * m * c[0] + 3f * m * m * t * c[2] + 3f * m * t * t * c[4] + t * t * t * c[6]
}

private fun bezY(c: Cub, t: Float): Float {
    val m = 1f - t
    return m * m * m * c[1] + 3f * m * m * t * c[3] + 3f * m * t * t * c[5] + t * t * t * c[7]
}

/** de Casteljau 로 t 에서 둘로 나눈다. */
private fun splitCub(c: Cub, t: Float): Pair<Cub, Cub> {
    fun lerp(a: Float, b: Float) = a + (b - a) * t
    val x01 = lerp(c[0], c[2]); val y01 = lerp(c[1], c[3])
    val x12 = lerp(c[2], c[4]); val y12 = lerp(c[3], c[5])
    val x23 = lerp(c[4], c[6]); val y23 = lerp(c[5], c[7])
    val x012 = lerp(x01, x12); val y012 = lerp(y01, y12)
    val x123 = lerp(x12, x23); val y123 = lerp(y12, y23)
    val x = lerp(x012, x123); val y = lerp(y012, y123)
    return floatArrayOf(c[0], c[1], x01, y01, x012, y012, x, y) to floatArrayOf(x, y, x123, y123, x23, y23, c[6], c[7])
}

/** M/L/C/Z 절대 좌표 SVG 경로 → 닫힌 3차 조각 목록. (시작 시 한 번만 호출) */
private fun parsePath(d: String): List<Cub> {
    val tok = Regex("[MLCZ]|-?\\d*\\.?\\d+(?:e-?\\d+)?").findAll(d).map { it.value }.toList()
    val out = ArrayList<Cub>()
    var i = 0
    var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f
    fun num(): Float = tok[i++].toFloat()
    while (i < tok.size) {
        when (tok[i++]) {
            "M" -> { cx = num(); cy = num(); sx = cx; sy = cy }
            "L" -> { val x = num(); val y = num(); out += lineCub(cx, cy, x, y); cx = x; cy = y }
            "C" -> {
                val c = floatArrayOf(cx, cy, num(), num(), num(), num(), num(), num())
                out += c; cx = c[6]; cy = c[7]
            }
            "Z" -> { if (abs(cx - sx) > 1e-6f || abs(cy - sy) > 1e-6f) out += lineCub(cx, cy, sx, sy); cx = sx; cy = sy }
        }
    }
    return out
}

private fun interp(x: Float, xs: FloatArray, ys: FloatArray): Float {
    // np.interp 와 같음: 등간격 격자 xs (0..1), 범위 밖은 끝값
    val n = xs.size
    if (x <= xs[0]) return ys[0]
    if (x >= xs[n - 1]) return ys[n - 1]
    val f = (x - xs[0]) / (xs[n - 1] - xs[0]) * (n - 1)
    val k = min(f.toInt(), n - 2)
    val w = f - k
    return ys[k] + (ys[k + 1] - ys[k]) * w
}

/**
 * 갈래 하나의 재료 좌표.
 * 로고 좌표 p 를 사선 좌표 p = a·L + b·S 로 분해하고
 *   u = (a − a0)/(a1 − a0),   v = (b − bl(u)) / (br(u) − bl(u))
 * 로 정의한다. P(u, v) = a(u)·L + (bl + v·(br − bl))·S 는 정확한 역함수 → 착지 상태의 워프는 항등이다.
 */
private class RibbonParam(lx: Float, ly: Float, sx: Float, sy: Float, surfaces: List<List<Cub>>, n: Int = 1025) {
    val Lx: Float; val Ly: Float; val Sx: Float; val Sy: Float
    private val i00: Float; private val i01: Float; private val i10: Float; private val i11: Float  // [L S]^-1
    val a0: Float; val a1: Float
    val ug = FloatArray(n) { it / (n - 1f) }
    val bl = FloatArray(n); val br = FloatArray(n)
    val bmSmooth = FloatArray(n)

    init {
        val ln = sqrt(lx * lx + ly * ly); val sn = sqrt(sx * sx + sy * sy)
        Lx = lx / ln; Ly = ly / ln; Sx = sx / sn; Sy = sy / sn
        val det = Lx * Sy - Sx * Ly
        i00 = Sy / det; i01 = -Sx / det; i10 = -Ly / det; i11 = Lx / det
        // 조밀한 외곽 폴리라인 (사선 좌표)
        val polys = surfaces.map { cubs ->
            val m = cubs.size * 48
            val a = FloatArray(m + 1); val b = FloatArray(m + 1)
            var k = 0
            for (c in cubs) for (j in 0 until 48) {
                val t = j / 48f
                val x = bezX(c, t); val y = bezY(c, t)
                a[k] = i00 * x + i01 * y; b[k] = i10 * x + i11 * y; k++
            }
            a[m] = a[0]; b[m] = b[0]
            a to b
        }
        var amin = Float.MAX_VALUE; var amax = -Float.MAX_VALUE
        for ((a, _) in polys) for (v in a) { amin = min(amin, v); amax = max(amax, v) }
        a0 = amin; a1 = amax
        bl.fill(Float.NaN); br.fill(Float.NaN)
        for (k in 0 until n) {
            val lev = a0 + ug[k] * (a1 - a0)
            for ((a, b) in polys) for (j in 0 until a.size - 1) {
                val ap = a[j]; val aq = a[j + 1]
                if ((ap - lev) * (aq - lev) <= 0f && abs(aq - ap) > 1e-12f) {
                    val bb = b[j] + (lev - ap) / (aq - ap) * (b[j + 1] - b[j])
                    bl[k] = if (bl[k].isNaN()) bb else min(bl[k], bb)
                    br[k] = if (br[k].isNaN()) bb else max(br[k], bb)
                }
            }
        }
        fillNaN(bl); fillNaN(br)
        for (k in 0 until n) {   // 끝 단면은 점으로 줄어든다 → 최소 반폭 0.6
            val mid = 0.5f * (bl[k] + br[k]); val half = max(0.5f * (br[k] - bl[k]), 0.6f)
            bl[k] = mid - half; br[k] = mid + half
        }
        // 비행용 매끈한 척추: 단면 중앙 b 를 u 에 대한 직선으로 근사 (0.04 < u < 0.96)
        var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var t0 = 0.0; var t1 = 0.0
        for (k in 0 until n) if (ug[k] > 0.04f && ug[k] < 0.96f) {
            val u = ug[k].toDouble(); val m = 0.5 * (bl[k] + br[k])
            s0 += 1.0; s1 += u; s2 += u * u; t0 += m; t1 += u * m
        }
        val slope = (s0 * t1 - s1 * t0) / (s0 * s2 - s1 * s1); val icpt = (t0 - slope * s1) / s0
        for (k in 0 until n) bmSmooth[k] = (icpt + slope * ug[k]).toFloat()
    }

    private fun fillNaN(arr: FloatArray) {
        val good = arr.indices.filter { !arr[it].isNaN() }
        val gx = FloatArray(good.size) { ug[good[it]] }; val gy = FloatArray(good.size) { arr[good[it]] }
        for (k in arr.indices) if (arr[k].isNaN()) arr[k] = interpIrregular(ug[k], gx, gy)
    }

    private fun interpIrregular(x: Float, xs: FloatArray, ys: FloatArray): Float {
        if (x <= xs[0]) return ys[0]
        if (x >= xs.last()) return ys.last()
        var j = 1
        while (xs[j] < x) j++
        val w = (x - xs[j - 1]) / (xs[j] - xs[j - 1])
        return ys[j - 1] + (ys[j] - ys[j - 1]) * w
    }

    /** u 는 로고 좌표의 1차식: u = gx·x + gy·y + g0 */
    val gx get() = i00 / (a1 - a0)
    val gy get() = i01 / (a1 - a0)
    val g0 get() = -a0 / (a1 - a0)

    fun u(x: Float, y: Float) = ((i00 * x + i01 * y) - a0) / (a1 - a0)

    fun v(x: Float, y: Float, u: Float): Float {
        val b = i10 * x + i11 * y
        val l = interp(u, ug, bl); val r = interp(u, ug, br)
        return (b - l) / (r - l)
    }

    fun px(u: Float, v: Float): Float {
        val a = a0 + u * (a1 - a0); val l = interp(u, ug, bl); val r = interp(u, ug, br)
        return a * Lx + (l + v * (r - l)) * Sx
    }

    fun py(u: Float, v: Float): Float {
        val a = a0 + u * (a1 - a0); val l = interp(u, ug, bl); val r = interp(u, ug, br)
        return a * Ly + (l + v * (r - l)) * Sy
    }

    fun smX(u: Float) = (a0 + u * (a1 - a0)) * Lx + interp(u, ug, bmSmooth) * Sx
    fun smY(u: Float) = (a0 + u * (a1 - a0)) * Ly + interp(u, ug, bmSmooth) * Sy
}

/** 베지어 c 위에서 u(p(t)) = level 인 t 들 (부호 변화 구간을 이분법으로). */
private fun uRoots(c: Cub, gx: Float, gy: Float, g0: Float, level: Float): List<Float> {
    fun f(t: Float) = bezX(c, t) * gx + bezY(c, t) * gy + g0 - level
    val out = ArrayList<Float>()
    val steps = 64
    var t0 = 0f; var f0 = f(0f)
    for (k in 1..steps) {
        val t1 = k / steps.toFloat(); val f1 = f(t1)
        // 반열린 구간 [t0, t1): 표본점이 정확히 0 이어도 한 번만 센다
        if ((f0 <= 0f && f1 > 0f) || (f0 >= 0f && f1 < 0f)) {
            var lo = t0; var hi = t1; var flo = f0
            repeat(40) {
                val mid = 0.5f * (lo + hi); val fm = f(mid)
                if (flo * fm <= 0f) hi = mid else { lo = mid; flo = fm }
            }
            val r = 0.5f * (lo + hi)
            if (r > 1e-6f && r < 1 - 1e-6f) out += r
        }
        t0 = t1; f0 = f1
    }
    return out
}

/** 외곽 베지어를 u 경계(levels)와 최대 길이로 잘게 나눈다 — 모양은 원래 곡선과 정확히 같다. */
private fun subdivide(cubs: List<Cub>, p: RibbonParam, levels: FloatArray, maxLen: Float = 36f): List<Cub> {
    val gx = p.gx; val gy = p.gy; val g0 = p.g0
    val out = ArrayList<Cub>()
    for (c in cubs) {
        // 띠 양 끝(u = 0, 1)은 외곽선이 접할 뿐이라 나누지 않는다
        val ts = levels.filter { it > 0f && it < 1f }.flatMap { uRoots(c, gx, gy, g0, it) }.distinctBy { (it * 1e6f).roundToInt() }.sorted()
        val pieces = ArrayList<Cub>()
        var rest = c; var tPrev = 0f
        for (t in ts) {
            val (a, b) = splitCub(rest, (t - tPrev) / (1f - tPrev))
            pieces += a; rest = b; tPrev = t
        }
        pieces += rest
        for (pc in pieces) {
            var len = 0f
            var lx = pc[0]; var ly = pc[1]
            for (j in 1..8) {
                val x = bezX(pc, j / 8f); val y = bezY(pc, j / 8f)
                len += hypot(x - lx, y - ly); lx = x; ly = y
            }
            var r = pc
            val n = max(1, ceil(len / maxLen).toInt())
            for (k in n downTo 2) { val (a, b) = splitCub(r, 1f / k); out += a; r = b }
            out += r
        }
    }
    return out.filter { hypot(it[6] - it[0], it[7] - it[1]) > 1e-6f || hypot(it[2] - it[0], it[3] - it[1]) > 1e-6f }
}

/**
 * u ≤ level (keepBelow) 또는 u ≥ level 반평면으로 자르기 (Sutherland–Hodgman).
 * 조각은 미리 level 에서 나뉘어 있어 한쪽에만 있다. 밖으로 나간 구간은 같은 단면선 위의 직선으로 잇는다.
 */
private fun clipHalf(pieces: List<Cub>, p: RibbonParam, level: Float, keepBelow: Boolean): List<Cub> {
    val inside = BooleanArray(pieces.size) {
        val u = p.u(bezX(pieces[it], 0.5f), bezY(pieces[it], 0.5f))
        if (keepBelow) u <= level + 1e-6f else u >= level - 1e-6f
    }
    if (inside.all { it }) return pieces
    if (inside.none { it }) return emptyList()
    val n = pieces.size
    val k0 = inside.indexOfFirst { it }
    val out = ArrayList<Cub>()
    var exitX = Float.NaN; var exitY = Float.NaN
    for (s in 0 until n) {
        val idx = (k0 + s) % n
        val pc = pieces[idx]
        if (inside[idx]) {
            if (!exitX.isNaN()) {
                if (hypot(pc[0] - exitX, pc[1] - exitY) > 1e-6f) out += lineCub(exitX, exitY, pc[0], pc[1])
                exitX = Float.NaN
            }
            out += pc
        } else if (exitX.isNaN()) { exitX = pc[0]; exitY = pc[1] }
    }
    if (!exitX.isNaN() && hypot(out[0][0] - exitX, out[0][1] - exitY) > 1e-6f) out += lineCub(exitX, exitY, out[0][0], out[0][1])
    return out
}

private class SegmentGeom(val k: Int, val u0: Float, val u1: Float, val pieces: List<Cub>)

/**
 * 면을 u 띠 [cut_k − overlap, cut_{k+1}] 로 나눈 세그먼트들. 이웃끼리 살짝 겹쳐 이음선이 비치지 않게 한다.
 * 세그먼트마다 자기 경계에서만 외곽선을 나눠 정점 수를 줄인다.
 */
private fun segmentSurface(cubs: List<Cub>, p: RibbonParam, cuts: FloatArray, overlap: Float): List<SegmentGeom> {
    val out = ArrayList<SegmentGeom>()
    for (k in 0 until cuts.size - 1) {
        val lo = if (k > 0) cuts[k] - overlap else cuts[k]
        val hi = cuts[k + 1]
        val loC = if (k > 0) lo else -1f
        val hiC = if (k < cuts.size - 2) hi else 2f
        val fine = subdivide(cubs, p, floatArrayOf(lo, hi))
        var pc = clipHalf(fine, p, hiC, true)
        if (pc.isNotEmpty()) pc = clipHalf(pc, p, loC, false)
        if (pc.isEmpty()) continue
        // 넓이가 거의 없는 띠는 건너뛴다
        var area = 0f
        for (c in pc) for (j in 0 until 8) {
            val t0 = j / 8f; val t1 = (j + 1) / 8f
            area += bezX(c, t0) * bezY(c, t1) - bezX(c, t1) * bezY(c, t0)
        }
        if (abs(area) * 0.5f < 0.5f) continue
        out += SegmentGeom(k, cuts[k], cuts[k + 1], pc)
    }
    return out
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. 운동 — 섹션별 스프링 비행 + 비틀림/흩날림 + 착지 형태 블렌드
// ─────────────────────────────────────────────────────────────────────────────

private fun smootherstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}

/**
 * 0 → 1 감쇠 스프링 (정지에서 출발).  r(s) = 1 − e^(−ζωs)(cos ω_d s + ζ/√(1−ζ²) sin ω_d s)
 * ζ = 0.87 이면 지나침 ≈ 0.4%. s_w0~s_w1 에서 잔차를 부드럽게 0 으로 접어 유한 시간에 정확히 멈춘다.
 */
private fun spring(s: Float, c: RibbonCfg): Float {
    val ss = max(s, 0f)
    val wd = c.omega * sqrt(1f - c.zeta * c.zeta)
    val e = exp(-c.zeta * c.omega * ss)
    val r = 1f - e * (cos(wd * ss) + c.zeta / sqrt(1f - c.zeta * c.zeta) * sin(wd * ss))
    return 1f - (1f - r) * (1f - smootherstep(c.sW0, c.sW1, ss))
}

/** 갈래 하나의 연출 값 (choreo.py 와 같은 숫자). 오프셋은 각 섹션의 최종 위치 기준 로고 단위. */
private class RibbonCfg(
    val order: Int,              // 0 = 머리부터(u=0 먼저), 1 = 꼬리부터, 2 = 가운데부터
    val uc: Float = 0.5f,
    val t0: Float, val kappa: Float,
    val fx: Float, val fy: Float, val h0x: Float, val h0y: Float,
    dinX: Float, dinY: Float, val h1: Float,
    val wf: Float,
    val flutter: FloatArray,     // (진폭, k 주기/리본, f Hz, 위상) × n
    val twist: FloatArray,
    val omega: Float = 6.2f, val zeta: Float = 0.87f,
    val sW0: Float = 1.10f, val sW1: Float = 1.45f,
    val sa0: Float = 0.44f, val sa1: Float = 0.94f,
    val sf0: Float = 0.26f, val sf1: Float = 0.72f,
    val st0: Float = 0.28f, val st1: Float = 0.56f,
    val kp: Float = 0.24f,
    val taper: Float = 0.22f,
    val eps: Float = 0.2f,       // 가운데부터 착지할 때 매끈한 |d| 의 둥글림
    val spread: Float = 0f,      // 가운데부터 착지할 때 양 끝을 벌리는 정도
    val twistLag: Float = 0.25f, // 비틀림 풀림을 섹션마다 늦추는 시간 (먼저 닿는 쪽일수록 늦게)
    val drape: Float = 0.30f,    // 바깥 가장자리(v0)부터 안쪽으로 내려앉는 지연
    val v0: Float = 0.5f,
) {
    // 접근 경로 B(r): F → F+h0 → −din·h1 → 0  (r > 1 이면 착지 방향으로 살짝 지나친다)
    val p1x = fx + h0x; val p1y = fy + h0y
    val p2x: Float; val p2y: Float
    init {
        val n = sqrt(dinX * dinX + dinY * dinY)
        p2x = -dinX / n * h1; p2y = -dinY / n * h1
    }
    val landTime get() = t0 + kappa + sW1
}

/** 매 프레임 u 격자 위에서 계산하는 갈래 상태 (배열은 재사용). */
private class Ribbon(val p: RibbonParam, val c: RibbonCfg, val nu: Int = 257) {
    val ug = FloatArray(nu) { it / (nu - 1f) }
    val smx = FloatArray(nu) { p.smX(ug[it]) }
    val smy = FloatArray(nu) { p.smY(ug[it]) }
    val wf = FloatArray(nu) {
        val u = ug[it]
        max(c.wf * (min(u, 1f - u) / c.taper).coerceIn(0f, 1f).pow(0.75f), c.wf * 0.02f)
    }
    val ox = FloatArray(nu); val oy = FloatArray(nu)
    val sec = FloatArray(nu)                       // 섹션 경과 시간 s
    val alpha = FloatArray(nu); val tx = FloatArray(nu); val ty = FloatArray(nu)
    val fl = FloatArray(nu); val th = FloatArray(nu)
    private val cx = FloatArray(nu); private val cy = FloatArray(nu)

    private fun order(u: Float): Float = when (c.order) {
        0 -> u
        1 -> 1f - u
        else -> {   // 가운데부터: 매끈한 |d| (꼭짓점에서 리본이 V 로 꺾이지 않게)
            val d = (u - c.uc) / max(c.uc, 1f - c.uc)
            (sqrt(d * d + c.eps * c.eps) - c.eps) / (sqrt(1f + c.eps * c.eps) - c.eps)
        }
    }

    fun update(t: Float) {
        for (k in 0 until nu) {
            val u = ug[k]
            val w = order(u)
            val s = t - (c.t0 + c.kappa * w)
            sec[k] = s
            val r = spring(s, c)
            val m = 1f - r
            // 3차 베지어 B(r) (P3 = 0)
            ox[k] = m * m * m * c.fx + 3f * m * m * r * c.p1x + 3f * m * r * r * c.p2x
            oy[k] = m * m * m * c.fy + 3f * m * m * r * c.p1y + 3f * m * r * r * c.p2y
            if (c.spread != 0f) {
                // 가운데부터 착지: 양 끝을 길이 방향으로 벌려 접힌 V 대신 열린 호로 (r → 1 이면 0)
                val g = (m * m * m + 3f * m * m * r) * c.spread * (u - c.uc) * (p.a1 - p.a0)
                ox[k] += g * p.Lx; oy[k] += g * p.Ly
            }
            alpha[k] = smootherstep(c.sa0, c.sa1, s)
            val envF = 1f - smootherstep(c.sf0, c.sf1, s)
            val lag = c.twistLag * (1f - w)
            val envT = 1f - smootherstep(c.st0 + lag, c.st1 + lag, s)
            var f = 0f
            var i = 0
            while (i < c.flutter.size) {
                f += c.flutter[i] * sin(TWO_PI * (c.flutter[i + 1] * u - c.flutter[i + 2] * t) + c.flutter[i + 3]); i += 4
            }
            fl[k] = f * envF
            var a = 0f
            i = 0
            while (i < c.twist.size) {
                a += c.twist[i] * sin(TWO_PI * (c.twist[i + 1] * u - c.twist[i + 2] * t) + c.twist[i + 3]); i += 4
            }
            th[k] = a * envT
            cx[k] = smx[k] + ox[k]; cy[k] = smy[k] + oy[k]
        }
        // 비행 중심선의 접선 T (np.gradient 와 같은 차분), 법선 N = (T.y, −T.x)
        for (k in 0 until nu) {
            val k0 = max(k - 1, 0); val k1 = min(k + 1, nu - 1)
            val dx = cx[k1] - cx[k0]; val dy = cy[k1] - cy[k0]
            val n = max(sqrt(dx * dx + dy * dy), 1e-9f)
            tx[k] = dx / n; ty[k] = dy / n
        }
    }

    /**
     * 재료점 (u, v) 의 위치.  X = O + (1−α)·F + α·P(u, v)
     *   F = Sm(u) + 흩날림·N + (v−½)·wf(u)·(cosθ·N + k·sinθ·T)   (비행 모양: 가늘고 비틀린 실크)
     *   P(u, v) = 로고의 정확한 점   (α = 1, O = 0 이면 원본 그대로)
     */
    fun x(u: Float, v: Float, out: FloatArray, o: Int) {
        val f = ((u - ug[0]) / (ug[nu - 1] - ug[0]) * (nu - 1)).coerceIn(0f, (nu - 1).toFloat())
        val k = min(f.toInt(), nu - 2); val w = f - k
        fun ip(a: FloatArray) = a[k] + (a[k + 1] - a[k]) * w
        var tX = ip(tx); var tY = ip(ty)
        val tn = max(sqrt(tX * tX + tY * tY), 1e-9f); tX /= tn; tY /= tn
        val nX = tY; val nY = -tX
        val al = if (c.drape != 0f) {
            // 바깥 가장자리(v0)부터 안쪽으로 내려앉는다: α 를 v 에 따라 조금 늦춘다 (끝 단면 근처는 약하게)
            val dv = abs(v.coerceIn(0f, 1f) - c.v0) / max(c.v0, 1f - c.v0)
            val le = c.drape * smootherstep(0.05f, 0.18f, min(u, 1f - u))
            smootherstep(c.sa0 + le * (dv - 0.5f), c.sa1 + le * (dv - 0.5f), ip(sec))
        } else ip(alpha)
        val flv = ip(fl); val thv = ip(th); val wfv = ip(wf)
        val vc = v.coerceIn(-0.05f, 1.05f) - 0.5f
        val ct = cos(thv); val st = sin(thv)
        val fx = p.smX(u) + flv * nX + vc * wfv * (ct * nX + c.kp * st * tX)
        val fy = p.smY(u) + flv * nY + vc * wfv * (ct * nY + c.kp * st * tY)
        out[o] = ip(ox) + (1f - al) * fx + al * p.px(u, v)
        out[o + 1] = ip(oy) + (1f - al) * fy + al * p.py(u, v)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. 세그먼트 — 면을 u 띠로 자른 조각. 경로·그라데이션·음영을 매 프레임 갱신 (할당 없음)
// ─────────────────────────────────────────────────────────────────────────────

private const val DT = 0.02f   // 핸들 보정용 곡선 매개변수 간격

private class SegmentRig(val surface: Surface, val rb: Ribbon, g: SegmentGeom) {
    val k = g.k; val u0 = g.u0; val u1 = g.u1
    val n = g.pieces.size
    private val pc = g.pieces
    // 앵커(조각 시작점), 조각 시작 직후·끝 직전 점의 (u, v) — 미리 계산
    private val pu = FloatArray(3 * n); private val pv = FloatArray(3 * n)
    private val ref = FloatArray(6 * n)                     // 같은 점들의 로고 좌표
    private val xs = FloatArray(6 * n)                      // 매 프레임 위치
    // 최소제곱 아핀 맞춤용 표본 (5 × 4) 과 (GᵀG)⁻¹
    private val fu = FloatArray(20); private val fv = FloatArray(20)
    private val fpx = FloatArray(20); private val fpy = FloatArray(20)
    private val gInv = DoubleArray(9)
    private val fx = FloatArray(2)
    private val bxv = DoubleArray(3); private val byv = DoubleArray(3); private val m = DoubleArray(6)   // 맞춤용 작업 배열

    val path = Path()
    val cub = FloatArray(8 * n)                             // 이번 프레임의 워프된 3차 조각 (x0 y0 … x3 y3) × n
    val grad = FloatArray(4)                                // 이번 프레임의 그라데이션 시작·끝점
    val gradMatrix = Matrix()
    val shadeMatrix = Matrix()
    var shadeA0 = 0f; var shadeA1 = 0f; var landed = 0f
    val baseShader = LinearGradient(0f, 0f, 1f, 0f, surface.colors, surface.stops, Shader.TileMode.CLAMP)
    val shadeShader = LinearGradient(0f, 0f, 1f, 0f, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)

    init {
        val p = rb.p
        for (i in 0 until n) {
            val c = pc[i]
            val pts = floatArrayOf(c[0], c[1], bezX(c, DT), bezY(c, DT), bezX(c, 1 - DT), bezY(c, 1 - DT))
            for (j in 0 until 3) {
                val x = pts[2 * j]; val y = pts[2 * j + 1]
                val q = j * n + i
                ref[2 * q] = x; ref[2 * q + 1] = y
                pu[q] = p.u(x, y); pv[q] = p.v(x, y, pu[q])
            }
        }
        var q = 0
        val a = DoubleArray(9)
        // 이웃 세그먼트와 표본을 반씩 겹쳐 맞춘다 → 이음선에서 색이 이어진다
        val du = 0.5f * (u1 - u0)
        val fa = max(u0 - du, 0f); val fb = min(u1 + du, 1f)
        for (vi in 0 until 4) for (ui in 0 until 5) {
            fu[q] = fa + (fb - fa) * ui / 4f; fv[q] = vi / 3f
            fpx[q] = p.px(fu[q], fv[q]); fpy[q] = p.py(fu[q], fv[q])
            val g = doubleArrayOf(fpx[q].toDouble(), fpy[q].toDouble(), 1.0)
            for (r in 0 until 3) for (cc in 0 until 3) a[3 * r + cc] += g[r] * g[cc]
            q++
        }
        invert3(a, gInv)
    }

    fun update() {
        for (q in 0 until 3 * n) rb.x(pu[q], pv[q], xs, 2 * q)
        // 경로: 앵커는 그대로, 핸들은 "원래 핸들 + 변위 D = X − p 의 곡선 방향 미분" (착지 후 D = 0 → 원본 곡선)
        path.rewind()
        // 비행 중 끝 단면처럼 크게 눌린 곳은 보정 핸들이 현보다 길어져 '부리'가 생긴다 →
        // 현의 0.4배로 제한하되 착지(α → 1)할수록 제한을 풀어 원본 핸들로 돌아간다
        val al = interpGrid(0.5f * (u0 + u1), rb.ug, rb.alpha)
        for (i in 0 until n) {
            val nx = (i + 1) % n
            val ax = xs[2 * i]; val ay = xs[2 * i + 1]
            val bx = xs[2 * nx]; val by = xs[2 * nx + 1]
            val d0x = ax - ref[2 * i]; val d0y = ay - ref[2 * i + 1]
            val qa = n + i; val qb = 2 * n + i
            val dax = xs[2 * qa] - ref[2 * qa]; val day = xs[2 * qa + 1] - ref[2 * qa + 1]
            val dbx = xs[2 * qb] - ref[2 * qb]; val dby = xs[2 * qb + 1] - ref[2 * qb + 1]
            val d1x = bx - ref[2 * nx]; val d1y = by - ref[2 * nx + 1]
            val c = pc[i]
            var hox = (c[2] - c[0]) + (dax - d0x) / (3f * DT); var hoy = (c[3] - c[1]) + (day - d0y) / (3f * DT)
            var hix = (c[4] - c[6]) - (d1x - dbx) / (3f * DT); var hiy = (c[5] - c[7]) - (d1y - dby) / (3f * DT)
            val chord = hypot(bx - ax, by - ay)
            val so = al + (1f - al) * min(1f, 0.4f * chord / max(hypot(hox, hoy), 1e-9f))
            val si = al + (1f - al) * min(1f, 0.4f * chord / max(hypot(hix, hiy), 1e-9f))
            hox *= so; hoy *= so; hix *= si; hiy *= si
            val c1x = ax + hox; val c1y = ay + hoy
            val c2x = bx + hix; val c2y = by + hiy
            val o = 8 * i
            cub[o] = ax; cub[o + 1] = ay; cub[o + 2] = c1x; cub[o + 3] = c1y
            cub[o + 4] = c2x; cub[o + 5] = c2y; cub[o + 6] = bx; cub[o + 7] = by
            if (i == 0) path.moveTo(ax, ay)
            path.cubicTo(c1x, c1y, c2x, c2y, bx, by)
        }
        path.close()
        // 그라데이션: 표본점 P → X 최소제곱 아핀 맞춤, 너무 납작하면 특이값을 0.2배 이상으로 (비틀려 옆면일 때)
        bxv.fill(0.0); byv.fill(0.0)
        for (q in 0 until 20) {
            rb.x(fu[q], fv[q], fx, 0)
            val g0 = fpx[q].toDouble(); val g1 = fpy[q].toDouble()
            bxv[0] += g0 * fx[0]; bxv[1] += g1 * fx[0]; bxv[2] += fx[0].toDouble()
            byv[0] += g0 * fx[1]; byv[1] += g1 * fx[1]; byv[2] += fx[1].toDouble()
        }
        // M (3×2): 열 0 = x 출력, 열 1 = y 출력
        for (r in 0 until 3) {
            m[2 * r] = gInv[3 * r] * bxv[0] + gInv[3 * r + 1] * bxv[1] + gInv[3 * r + 2] * bxv[2]
            m[2 * r + 1] = gInv[3 * r] * byv[0] + gInv[3 * r + 1] * byv[1] + gInv[3 * r + 2] * byv[2]
        }
        clampSingular(m, 0.2)
        val sx = surface.sx; val sy = surface.sy; val ex = surface.ex; val ey = surface.ey
        val gsx = (sx * m[0] + sy * m[2] + m[4]).toFloat(); val gsy = (sx * m[1] + sy * m[3] + m[5]).toFloat()
        val gex = (ex * m[0] + ey * m[2] + m[4]).toFloat(); val gey = (ex * m[1] + ey * m[3] + m[5]).toFloat()
        grad[0] = gsx; grad[1] = gsy; grad[2] = gex; grad[3] = gey
        unitSegmentTo(gradMatrix, gsx, gsy, gex, gey)
        baseShader.setLocalMatrix(gradMatrix)
        // 비틀림 음영: 세그먼트 양 끝 단면의 cosθ → 검정 덮개 불투명도 (선형 램프)
        shadeA0 = shadeOf(interpGrid(u0, rb.ug, rb.th)); shadeA1 = shadeOf(interpGrid(u1, rb.ug, rb.th))
        landed = interpGrid(0.5f * (u0 + u1), rb.ug, rb.alpha)
        if (max(shadeA0, shadeA1) > 0.003f) {
            rb.x(u0, 0.5f, fx, 0); val p0x = fx[0]; val p0y = fx[1]
            rb.x(u1, 0.5f, fx, 0); var p1x = fx[0]; val p1y = fx[1]
            if (hypot(p1x - p0x, p1y - p0y) < 0.05f) p1x = p0x + 0.05f
            rampTo(shadeMatrix, p0x, p0y, p1x, p1y, shadeA0, shadeA1)
            shadeShader.setLocalMatrix(shadeMatrix)
        }
    }
}

/** 비틀림 각 → 검정 덮개 불투명도. 앞면 0, 옆면 0.16, 뒷면 0.28 ('조금 어둡게'). */
private fun shadeOf(theta: Float): Float {
    val c = cos(theta)
    return if (c >= 0f) 0.16f * (1f - c).pow(1.4f) else 0.16f + 0.12f * (-c).pow(0.8f)
}

private fun interpGrid(x: Float, xs: FloatArray, ys: FloatArray): Float = interp(x, xs, ys)

/** 단위 선분 (0,0)→(1,0) 을 (x0,y0)→(x1,y1) 로 보내는 닮음 변환 (그라데이션 축). */
private fun unitSegmentTo(m: Matrix, x0: Float, y0: Float, x1: Float, y1: Float) {
    val dx = x1 - x0; val dy = y1 - y0
    m.setValues(floatArrayOfScratch(dx, -dy, x0, dy, dx, y0))
}

/**
 * 검정 램프 (0,0)→(1,0) 의 알파 0→1 을, 축 p0→p1 위에서 a0→a1 로 선형 변하게 놓는 행렬.
 * 셰이더 x 가 a 일 때의 위치 q = (a − a0)/(a1 − a0) (p0→p1 비율).  a0 ≈ a1 이면 축을 길게 늘여 근사.
 */
private fun rampTo(m: Matrix, p0x: Float, p0y: Float, p1x: Float, p1y: Float, a0: Float, a1: Float) {
    var da = a1 - a0
    if (abs(da) < 1e-3f) da = if (da >= 0f) 1e-3f else -1e-3f
    val q0 = -a0 / da; val q1 = (1f - a0) / da          // 셰이더 x=0, x=1 의 위치 비율
    val x0 = p0x + (p1x - p0x) * q0; val y0 = p0y + (p1y - p0y) * q0
    val x1 = p0x + (p1x - p0x) * q1; val y1 = p0y + (p1y - p0y) * q1
    unitSegmentTo(m, x0, y0, x1, y1)
}

private val matScratch = FloatArray(9)
private fun floatArrayOfScratch(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): FloatArray {
    matScratch[0] = a; matScratch[1] = b; matScratch[2] = c
    matScratch[3] = d; matScratch[4] = e; matScratch[5] = f
    matScratch[6] = 0f; matScratch[7] = 0f; matScratch[8] = 1f
    return matScratch
}

private fun invert3(a: DoubleArray, out: DoubleArray) {
    val c00 = a[4] * a[8] - a[5] * a[7]; val c01 = a[5] * a[6] - a[3] * a[8]; val c02 = a[3] * a[7] - a[4] * a[6]
    val det = a[0] * c00 + a[1] * c01 + a[2] * c02
    out[0] = c00 / det; out[1] = (a[2] * a[7] - a[1] * a[8]) / det; out[2] = (a[1] * a[5] - a[2] * a[4]) / det
    out[3] = c01 / det; out[4] = (a[0] * a[8] - a[2] * a[6]) / det; out[5] = (a[2] * a[3] - a[0] * a[5]) / det
    out[6] = c02 / det; out[7] = (a[1] * a[6] - a[0] * a[7]) / det; out[8] = (a[0] * a[4] - a[1] * a[3]) / det
}

/**
 * M 의 2×2 선형부 [[m0 m1],[m2 m3]] 특이값을 최대값의 ratio 배 이상으로 (부호 유지).
 * 닫힌 꼴 2×2 SVD: A = R(φ)·diag(s1, s2)·R(θ),  s1 = Q + R ≥ |s2|, s2 = Q − R.
 */
private fun clampSingular(m: DoubleArray, ratio: Double) {
    val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]
    val e = (a + d) / 2; val f = (a - d) / 2; val g = (c + b) / 2; val h = (c - b) / 2
    val q = sqrt(e * e + h * h); val r = sqrt(f * f + g * g)
    val s1 = q + r; var s2 = q - r
    if (abs(s2) >= ratio * s1) return
    s2 = if (s2 >= 0) ratio * s1 else -ratio * s1
    val a1 = atan2(g, f); val a2 = atan2(h, e)
    val th = (a2 - a1) / 2; val ph = (a2 + a1) / 2
    // A = R(ph) · diag(s1, s2) · R(th),  R(x) = [[cos −sin],[sin cos]]
    val cp = cos(ph); val sp = sin(ph); val ct = cos(th); val st = sin(th)
    m[0] = cp * s1 * ct - sp * s2 * st;  m[1] = -cp * s1 * st - sp * s2 * ct
    m[2] = sp * s1 * ct + cp * s2 * st;  m[3] = -sp * s1 * st + cp * s2 * ct
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. 장면 — 면 6개(뒤→앞) + 하이라이트를 한 캔버스에. 경로·셰이더·페인트는 모두 미리 만들어 재사용
// ─────────────────────────────────────────────────────────────────────────────

private class Surface(
    val id: String, val ribbon: Int, d: String,
    val sx: Float, val sy: Float, val ex: Float, val ey: Float,
    val stops: FloatArray, val colors: IntArray,
) {
    val cubics = parsePath(d)
    /** 정지 이후 그리는 원본 벡터 그대로의 경로와 그라데이션. */
    val finalPath = Path().apply {
        moveTo(cubics[0][0], cubics[0][1])
        for (c in cubics) cubicTo(c[2], c[3], c[4], c[5], c[6], c[7])
        close()
    }
    val finalShader = LinearGradient(sx, sy, ex, ey, colors, stops, Shader.TileMode.CLAMP)
}

private class EvoScene {
    val surfaces = SURFACES
    val params = Array(3) { r ->
        val f = RIBBON_FRAMES[r]
        RibbonParam(f[0], f[1], f[2], f[3], surfaces.filter { it.ribbon == r }.map { it.cubics })
    }
    val ribbons = Array(3) { Ribbon(params[it], CHOREO[it]) }
    val rigs: List<List<SegmentRig>> = surfaces.map { s ->
        val k = SEGMENTS[s.ribbon]
        val cuts = FloatArray(k + 1) { it / k.toFloat() }
        segmentSurface(s.cubics, params[s.ribbon], cuts, OVERLAP).map { SegmentRig(s, ribbons[s.ribbon], it) }
    }
    val silhouette = Path().apply {
        val c = parsePath(SILHOUETTE)
        moveTo(c[0][0], c[0][1]); for (q in c) cubicTo(q[2], q[3], q[4], q[5], q[6], q[7]); close()
    }
    val landTimes = FloatArray(3) { CHOREO[it].landTime }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hlShader = LinearGradient(
        0f, 0f, 1f, 0f, intArrayOf(0x00FFFFFF, (HL_PEAK * 255).roundToInt() shl 24 or 0xFFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    private val hlMatrix = Matrix()
    private val hlAxis = FloatArray(4)

    /** t 초의 로고를 (w × h) 화면에 그린다. 로고 높이 440 = 짧은 변의 50%, 중심 = (가로 중앙, 세로 40%). */
    fun draw(c: Canvas, t: Float, w: Float, h: Float) {
        val unit = 0.5f * min(w, h) / 440f
        val b = breath(t)
        c.save()
        c.translate(w / 2f, 0.4f * h)
        c.scale(unit * b, unit * b)
        c.translate(-LOGO_CX, -LOGO_CY)
        for (r in 0 until 3) if (t < landTimes[r]) ribbons[r].update(t)
        for (i in surfaces.indices) {
            val s = surfaces[i]
            if (t >= landTimes[s.ribbon]) {
                // 정지 후: 부록 벡터 그대로 (직전 프레임과 모양·색이 같아 바꿔 끼운 티가 나지 않는다)
                fill.shader = s.finalShader; fill.alpha = 255
                c.drawPath(s.finalPath, fill)
                continue
            }
            for (sg in rigs[i]) {
                sg.update()
                var op = 1f
                if (s.id == "04_center_fold") {
                    // 접힘은 코랄이 다리 뒤로 들어간 다음, 다리가 제자리에 닿는 만큼만 보인다
                    val x = ((sg.landed - 0.85f) / 0.15f).coerceIn(0f, 1f)
                    op = x * x * (3f - 2f * x)
                    if (op <= 0.001f) continue
                }
                fill.shader = sg.baseShader; fill.alpha = (255 * op).roundToInt()
                c.drawPath(sg.path, fill)
                if (max(sg.shadeA0, sg.shadeA1) > 0.003f) {
                    fill.shader = sg.shadeShader
                    c.drawPath(sg.path, fill)
                }
            }
        }
        if (highlight(t, hlAxis)) {
            unitSegmentTo(hlMatrix, hlAxis[0], hlAxis[1], hlAxis[2], hlAxis[3])
            hlShader.setLocalMatrix(hlMatrix)
            fill.shader = hlShader; fill.alpha = 255
            c.drawPath(silhouette, fill)
        }
        fill.shader = null
        c.restore()
    }
}

/** 완성 후 한 번의 숨: 1 + A·sin²(πx)  (기울기 0 에서 시작·끝) */
private fun breath(t: Float): Float {
    val x = ((t - BREATH_T0) / (BREATH_T1 - BREATH_T0)).coerceIn(0f, 1f)
    val s = sin(PI.toFloat() * x)
    return 1f + BREATH_AMP * s * s
}

/** 대각선 하이라이트 띠의 그라데이션 축 (로고 바깥 → 반대편 바깥) 을 out 에. 구간 밖이면 false. */
private fun highlight(t: Float, out: FloatArray): Boolean {
    val x0 = (t - HL_T0) / (HL_T1 - HL_T0)
    if (x0 <= 0f || x0 >= 1f) return false
    val x = smootherstep(0f, 1f, x0)
    val n = hypot(HL_DIR_X, HL_DIR_Y); val dx = HL_DIR_X / n; val dy = HL_DIR_Y / n
    val cx = LOGO_CX + dx * (-HL_SPAN + 2f * HL_SPAN * x); val cy = LOGO_CY + dy * (-HL_SPAN + 2f * HL_SPAN * x)
    out[0] = cx - dx * HL_WIDTH; out[1] = cy - dy * HL_WIDTH; out[2] = cx + dx * HL_WIDTH; out[3] = cy + dy * HL_WIDTH
    return true
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. 컴포저블
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 첫 실행·업데이트 투어 맨 앞의 오프닝.
 * 시간은 Animatable 하나(선형, 전체 길이)로 돌리고 그리기 단계(drawBehind / graphicsLayer)에서만 읽는다
 * → 매 프레임 재구성 없음.
 */
@Composable
internal fun EvoOpening(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val context = LocalContext.current
    val latestOnFinish by rememberUpdatedState(onFinish)
    val scene = remember { EvoScene() }                       // 경로 파싱·분할은 여기서 한 번만
    val time = remember { Animatable(0f) }
    val finished = remember { BooleanArray(1) }
    val finish = remember { { if (!finished[0]) { finished[0] = true; latestOnFinish() } } }
    // 시스템 '애니메이션 끄기' (애니메이터 길이 배율 0)
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            time.snapTo(T_STILL)                               // 완성된 로고 + 글자를 잠깐 보여 주고 끝
            delay(REDUCED_HOLD_MS)
            finish()
            return@LaunchedEffect
        }
        var last = 0f
        time.animateTo(TOTAL, tween((TOTAL * 1000).roundToInt(), easing = LinearEasing)) {
            // 각 갈래가 제자리에 멈추는 순간 짧은 틱
            for (m in LAND_MARKS) if (last < m && value >= m) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            last = value
        }
        finish()
    }

    val density = LocalDensity.current
    val rise = with(density) { 16.dp.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fadeOut(time.value) }    // 마지막 0.6초 전체 페이드아웃
            .pointerInput(Unit) {
                detectTapGestures { if (reduceMotion || time.value >= SKIP_AFTER) finish() }
            }
            .drawBehind {
                drawRect(BACKGROUND)
                drawIntoCanvas { scene.draw(it.nativeCanvas, time.value, size.width, size.height) }
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    // 로고 아래 가장자리 + 28dp 에 글자 줄을 둔다
                    val p = measurable.measure(constraints.copy(minHeight = 0))
                    val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
                    val unit = 0.5f * min(w, h) / 440f
                    val top = 0.4f * h + (440f - LOGO_CY) * unit + 28.dp.toPx()
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, top.roundToInt()) }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.graphicsLayer { reveal(time.value, TEXT_T0, rise) },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("HiHana ", color = Color.White.copy(alpha = 0.86f), fontSize = 36.sp, fontWeight = FontWeight.Normal,
                    modifier = Modifier.alignByBaseline())
                Text("Evo", fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline(),
                    style = TextStyle(brush = EVO_BRUSH))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "흩어져 있던 학교생활을 하나로", color = Color.White.copy(alpha = 0.62f), fontSize = 16.sp,
                modifier = Modifier.graphicsLayer { reveal(time.value, TEXT_T0 + TEXT_STAGGER, rise) },
            )
        }
    }
}

/** 글자: 아래에서 16dp 떠오르며 나타난다 (ease-out cubic, 0.5초). */
private fun GraphicsLayerScope.reveal(t: Float, t0: Float, rise: Float) {
    val x = ((t - t0) / TEXT_DUR).coerceIn(0f, 1f)
    val e = 1f - (1f - x) * (1f - x) * (1f - x)
    alpha = e
    translationY = (1f - e) * rise
}

private fun fadeOut(t: Float): Float = 1f - ((t - (TOTAL - FADE)) / FADE).coerceIn(0f, 1f)
