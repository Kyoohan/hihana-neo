package com.skh.hihanaevo

import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.AndroidComposeUiTestEnvironment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Records EvoOpening frame-by-frame to PNGs with Robolectric NATIVE graphics (no emulator).
 *
 * System properties (forwarded from -P<name>=... by app/build.gradle.kts):
 *   framesDir       output dir (default app/build/frames/evo)
 *   startMs, endMs  time window, inclusive (default 0 .. 6500)
 *   fps             default 60. Frame i is captured at exactly round(i * 1000 / fps) ms after the first frame.
 *   qualifiers      Robolectric qualifiers (default w360dp-h780dp-xxhdpi = 1080x2340 px @ density 3.0).
 *                   e.g. w360dp-h780dp-mdpi = same dp layout at 360x780 px (much faster).
 *                   Robolectric derives px = (int)(dp * density): w411dp-h891dp-420dpi gives 1078x2338.
 *   frameDelayExact true (default): Compose test frames every 1 ms, so every captured frame's animation
 *                   time is exactly the target ms. false: stock 16 ms test frames (frame times quantised
 *                   to multiples of 16 ms -> up to 16 ms timing error per captured frame).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class RecordFramesTest {

    @get:Rule
    val compose: ComposeTestRule = createEmptyComposeRule()

    @Test
    fun recordFrames() {
        val outDir = File(sysProp("framesDir") ?: "build/frames/evo").absoluteFile.apply { mkdirs() }
        val fps = sysProp("fps")?.toDouble() ?: 60.0
        val startMs = sysProp("startMs")?.toDouble() ?: 0.0
        val endMs = sysProp("endMs")?.toDouble() ?: 6500.0
        val exact = sysProp("frameDelayExact")?.toBooleanStrict() ?: true
        sysProp("qualifiers")?.let { RuntimeEnvironment.setQualifiers(it) }

        val firstIndex = ceil(startMs * fps / 1000.0 - 1e-9).toInt()
        val lastIndex = floor(endMs * fps / 1000.0 + 1e-9).toInt()
        fun targetMs(i: Int): Long = (i * 1000.0 / fps).roundToLong()

        val frameDelayMs = if (exact) 1L else 16L
        if (exact) setTestFrameDelay(compose, frameDelayMs * 1_000_000L)
        compose.mainClock.autoAdvance = false

        var finishedAt: Long? = null
        var hostView: View? = null
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            // 미리보기용: 앱에서 투어 화면이 깔릴 자리를 단색(#0B0B1A)으로 — 페이드아웃이 흰 창으로 비치지 않게
            activity.setContent {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0B0B1A)),
                ) { EvoOpening(onFinish = { finishedAt = compose.mainClock.currentTime }) }
            }
            hostView = activity.window.decorView
        }
        compose.waitForIdle()
        // Fire the first frame: this is where animations started by LaunchedEffect get their start time (t = 0).
        compose.mainClock.advanceTimeBy(frameDelayMs, ignoreFrameDuration = true)
        val base = compose.mainClock.currentTime

        println("[record] out=$outDir fps=$fps window=${startMs}..${endMs}ms frames=$firstIndex..$lastIndex " +
            "exactTiming=$exact qualifiers=${RuntimeEnvironment.getQualifiers()}")

        val t0 = System.nanoTime()
        var captureNanos = 0L
        var advanceNanos = 0L
        PngSink().use { sink ->
            for (i in firstIndex..lastIndex) {
                val target = base + targetMs(i)
                val now = compose.mainClock.currentTime
                val a = System.nanoTime()
                if (target > now) compose.mainClock.advanceTimeBy(target - now, ignoreFrameDuration = true)
                val b = System.nanoTime()
                val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
                if (i == firstIndex) println("[record] root capture size=${bmp.width}x${bmp.height}")
                sink.submit(bmp, File(outDir, "frame_%05d.png".format(i)))
                captureNanos += System.nanoTime() - b
                advanceNanos += b - a
            }
        }
        val totalS = (System.nanoTime() - t0) / 1e9
        val n = lastIndex - firstIndex + 1
        println("[record] wrote $n frames in %.1f s (%.1f ms/frame; advance %.1f ms/frame, capture+copy %.1f ms/frame)".format(
            totalS, totalS * 1000 / n, advanceNanos / 1e6 / n, captureNanos / 1e6 / n))
        // Report when onFinish fires (keeps stepping the clock 1 ms at a time, up to 3 s past the window; no capture).
        val endOfWindow = compose.mainClock.currentTime
        while (finishedAt == null && compose.mainClock.currentTime - endOfWindow < 3000) {
            compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        }
        println("[record] onFinish called at " + (finishedAt?.let { "${it - base} ms after the first frame" } ?: "<not within 3 s after the window>"))
        println("[record] decorView=${hostView?.width}x${hostView?.height}")
        // LocalView.current.performHapticFeedback() is a no-op on the JVM, but Robolectric records the last constant.
        val haptics = hostView?.let { allViews(it) }.orEmpty()
            .map { it to Shadows.shadowOf(it).lastHapticFeedbackPerformed() }
            .filter { (_, c) -> c >= 0 }
            .joinToString { (v, c) -> "${v.javaClass.simpleName}=$c" }
        println("[record] haptics recorded: ${haptics.ifEmpty { "none" }} (CLOCK_TICK=${HapticFeedbackConstants.CLOCK_TICK})")
        scenario.close()
    }

    private fun allViews(v: View): List<View> =
        listOf(v) + ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { allViews(g.getChildAt(it)) } } ?: emptyList())

    /** Replaces the Compose test frame clock's 16 ms frame interval (reflection on ui-test internals, 1.12.x). */
    private fun setTestFrameDelay(rule: ComposeTestRule, nanos: Long) {
        val env = generateSequence<Class<*>>(rule.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .first { AndroidComposeUiTestEnvironment::class.java.isAssignableFrom(it.type) }
            .apply { isAccessible = true }
            .get(rule)
        val clock = AndroidComposeUiTestEnvironment::class.java.getDeclaredField("frameClock")
            .apply { isAccessible = true }.get(env) as TestMonotonicFrameClock
        TestMonotonicFrameClock::class.java.getDeclaredField("frameDelayNanos")
            .apply { isAccessible = true }.setLong(clock, nanos)
        check(clock.frameDelayNanos == nanos) { "could not override frame delay" }
    }
}
