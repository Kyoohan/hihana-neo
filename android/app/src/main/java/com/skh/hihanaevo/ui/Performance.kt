package com.skh.hihanaevo.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * 성능 모드 — 사양이 낮은 폰을 위해 그래픽을 최소로: 카드·바·대화상자의 배경 흐림(서리 유리)과 유리 렌즈를 모두 끄고
 * 불투명한 단색 면으로 그립니다. 설정 → 화면에서 켜고 끕니다. 화면 전체가 바로 다시 그려지도록 Compose 상태로 둡니다.
 */
object PerformanceMode {
    private const val PREFS = "performance"
    private const val KEY = "enabled"

    var enabled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        enabled = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        enabled = on
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}

/**
 * 서리 유리의 불투명도 — 카드·칩·보조 버튼·게시판 세그먼트가 뒤 배경 위에 얹는 면 색의 알파. 기본 0.5(배경이 반쯤 비침).
 * 성능 모드에서는 흐림이 없어 쓰이지 않습니다. 설정 → 화면 → 성능에서 조절.
 */
object FrostOpacity {
    private const val PREFS = "frost"
    private const val KEY = "alpha"
    const val DEFAULT = 0.5f
    val RANGE = 0.2f..0.9f

    var alpha by mutableStateOf(DEFAULT)
        private set

    fun load(context: Context) {
        alpha = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY, DEFAULT).coerceIn(RANGE)
    }

    fun set(context: Context, value: Float) {
        alpha = value.coerceIn(RANGE)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY, alpha).apply()
    }
}

/**
 * 새 버전의 바뀐 점 알림 — 업데이트 뒤 설정에 처음 들어갈 때까지 설정 톱니에 'N' 배지를 띄웁니다.
 * 본 버전을 저장해 두고, 지금 버전(-dev 접미사 제외)과 다르면 아직 안 본 것으로 봅니다.
 */
object WhatsNew {
    private const val PREFS = "whats_new"
    private const val KEY_SEEN = "seen_version"

    var pending by mutableStateOf(false)
        private set

    private fun current(version: String) = version.removeSuffix("-dev")

    fun load(context: Context, version: String) {
        val seen = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SEEN, null)
        // 처음 설치(본 버전 없음)는 알리지 않습니다 — 업데이트한 사람에게만.
        pending = seen != null && seen != current(version)
        if (seen == null) markSeen(context, version)
    }

    fun markSeen(context: Context, version: String) {
        pending = false
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SEEN, current(version)).apply()
    }
}

/** 하단 바에 탭 이름을 함께 보일지 — 기본은 아이콘만. 설정 → 개인화에서 바꿉니다. */
object NavBarLabels {
    private const val PREFS = "nav_bar"
    private const val KEY = "labels"

    var shown by mutableStateOf(false)
        private set

    fun load(context: Context) {
        shown = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        shown = on
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}

/**
 * 느린 기기 자동 감지 — 앱을 처음 쓸 때 프레임 시간을 재서(FrameMetrics), 화면 효과가 그 기기의 주사율을 크게 못 따라가면
 * 성능 모드를 한 번 자동으로 켜고 알립니다. 사용자는 설정 → 화면에서 다시 끌 수 있고, 판정은 기기당 한 번만 합니다.
 * 기준: 앱이 뜨고 3초가 지난 뒤의 프레임 300장 중 상위 25%(p75)가 '한 프레임 예산 + 8ms' 를 넘으면 느린 기기로 봅니다
 * (120Hz 면 약 16ms, 60Hz 면 약 25ms — S25+ 는 p75 13~14ms 로 걸리지 않고, 중급 기기는 p50 이 22ms 라 걸립니다).
 */
object AutoPerformance {
    private const val PREFS = "performance"
    private const val KEY_DECIDED = "auto_decided"
    private const val SAMPLE = 300
    private const val WARMUP_MS = 3_000L

    fun watch(activity: android.app.Activity) {
        val prefs = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DECIDED, false) || PerformanceMode.enabled) return
        val window = activity.window
        val startedAt = android.os.SystemClock.uptimeMillis()
        val refresh = (if (android.os.Build.VERSION.SDK_INT >= 30) activity.display?.refreshRate else null)
            ?: @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.refreshRate
        val budgetNs = (1_000_000_000.0 / refresh.coerceAtLeast(30f)).toLong()
        val samples = LongArray(SAMPLE)
        var n = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var listener: android.view.Window.OnFrameMetricsAvailableListener
        listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (android.os.SystemClock.uptimeMillis() - startedAt < WARMUP_MS) return@OnFrameMetricsAvailableListener
            if (metrics.getMetric(android.view.FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
            samples[n++] = metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION)
            if (n < SAMPLE) return@OnFrameMetricsAvailableListener
            runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            samples.sort()
            val p75 = samples[(SAMPLE * 3) / 4]
            prefs.edit().putBoolean(KEY_DECIDED, true).apply()
            if (p75 > budgetNs + 8_000_000L) {
                PerformanceMode.set(activity, true)
                android.widget.Toast.makeText(
                    activity,
                    "이 기기에선 화면 효과가 느려 성능 모드를 켰습니다. 설정 → 화면에서 끌 수 있습니다.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
        window.addOnFrameMetricsAvailableListener(listener, handler)
    }
}

/**
 * 성능 모드가 아닐 때만 Haze 소스로 등록합니다. 소스는 그 아래 내용을 프레임마다 별도 층(텍스처)에 다시 기록하므로,
 * 아무도 흐림을 그리지 않는 성능 모드에서도 화면 전체 크기 층 셋(루트·배경·본문)을 매 프레임 복사하는 비용이 남았습니다
 * (중급 기기에서 GPU 프레임당 10ms 가량).
 */
fun Modifier.hazeSourceUnlessPerf(state: HazeState, zIndex: Float = 0f): Modifier =
    if (PerformanceMode.enabled) this else hazeSource(state, zIndex)

// 참고: 성능 모드에서 주사율을 60Hz 로 낮추는 것도 시험했지만(2026-10-18, SM7635 기기) 오히려 나빠졌습니다 —
// 부하가 줄어 GPU 클럭이 내려가면서 프레임이 16ms 를 넘겨 60Hz 에서도 프레임을 놓쳤고(p90 28ms), 터치 반응도 둔해졌습니다.
