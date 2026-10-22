package com.skh.hihanaevo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieFeatureFlag
import com.airbnb.lottie.LottieLogger
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.utils.Logger
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/**
 * Renders a Lottie JSON with the real lottie-android library on the JVM (Robolectric NATIVE graphics).
 *
 * System properties (forwarded from -P<name>=...):
 *   lottieJson        path to the Lottie JSON (default ../../sample/sample.json relative to app/)
 *   lottieOut         output dir (default app/build/frames/lottie-android)
 *   lottieBg          background colour, #RRGGBB or #AARRGGBB (default #0B0B1A)
 *   lottieScale       output scale relative to the composition size (default 1)
 *   lottieWidth/lottieHeight  explicit output size in px (override lottieScale)
 *   lottieStartFrame/lottieEndFrame  inclusive frame range (default ip .. op-1, i.e. what lottie-web renders)
 *   lottieRenderMode  HARDWARE (default: draw straight into the bitmap canvas) | SOFTWARE | AUTOMATIC
 *   lottieMergePaths  true to enable merge paths (LottieFeatureFlag.MergePathsApi19; off by default in the lib)
 *
 * Qualifiers are mdpi so Utils.dpScale() == 1 and composition units == JSON px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "mdpi")
class LottieRenderTest {

    @Test
    fun renderLottie() {
        val json = File(sysProp("lottieJson") ?: "../../sample/sample.json").absoluteFile
        assumeTrue("Lottie JSON not found: $json (pass -PlottieJson=...)", json.isFile)
        val outDir = File(sysProp("lottieOut") ?: "build/frames/lottie-android").absoluteFile.apply { mkdirs() }
        val bg = Color.parseColor(sysProp("lottieBg") ?: "#0B0B1A")

        Logger.setInstance(object : LottieLogger {
            override fun debug(message: String) = println("[lottie-debug] $message")
            override fun debug(message: String, exception: Throwable?) = println("[lottie-debug] $message ${exception ?: ""}")
            override fun warning(message: String) = println("[lottie-WARNING] $message")
            override fun warning(message: String, exception: Throwable?) = println("[lottie-WARNING] $message ${exception ?: ""}")
            override fun error(message: String, exception: Throwable?) = println("[lottie-ERROR] $message ${exception ?: ""}")
        })

        val result = json.inputStream().use { LottieCompositionFactory.fromJsonInputStreamSync(it, json.absolutePath) }
        result.exception?.let { throw AssertionError("Lottie failed to parse $json", it) }
        val comp = requireNotNull(result.value)

        println("[lottie] file=$json")
        println("[lottie] bounds=${comp.bounds.width()}x${comp.bounds.height()} fps=${comp.frameRate} " +
            "ip=${comp.startFrame} op=${comp.endFrame} durationMs=${comp.duration} layers=${comp.layers.size} " +
            "masksAndMattes=${comp.maskAndMatteCount} hasDashPattern=${comp.hasDashPattern()}")
        println("[lottie] composition warnings (${comp.warnings.size}):")
        comp.warnings.forEach { println("[lottie-WARNING] $it") }
        println("[lottie] markers (${comp.markers.size}):")
        comp.markers.forEach { m ->
            println("[lottie-marker] name=${m.name} startFrame=${m.startFrame} durationFrames=${m.durationFrames}")
        }

        val scale = sysProp("lottieScale")?.toFloat() ?: 1f
        val outW = sysProp("lottieWidth")?.toInt() ?: (comp.bounds.width() * scale).roundToInt()
        val outH = sysProp("lottieHeight")?.toInt() ?: (comp.bounds.height() * scale).roundToInt()
        val renderMode = RenderMode.valueOf(sysProp("lottieRenderMode") ?: "HARDWARE")

        val drawable = LottieDrawable().apply {
            if (sysProp("lottieMergePaths")?.toBoolean() == true) enableFeatureFlag(LottieFeatureFlag.MergePathsApi19, true)
            setComposition(comp)
            this.renderMode = renderMode
            setBounds(0, 0, outW, outH)
        }
        val first = sysProp("lottieStartFrame")?.toInt() ?: comp.startFrame.roundToInt()
        val last = sysProp("lottieEndFrame")?.toInt() ?: (comp.endFrame.roundToInt() - 1)

        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val t0 = System.nanoTime()
        PngSink().use { sink ->
            for (f in first..last) {
                drawable.frame = f
                canvas.drawColor(bg, android.graphics.PorterDuff.Mode.SRC)
                drawable.draw(canvas)
                sink.submit(bitmap, File(outDir, "frame_%05d.png".format(f)))
            }
        }
        val n = last - first + 1
        val s = (System.nanoTime() - t0) / 1e9
        println("[lottie] rendered $n frames ($first..$last) at ${outW}x$outH renderMode=$renderMode into $outDir " +
            "in %.1f s (%.1f ms/frame)".format(s, s * 1000 / n))
    }
}
