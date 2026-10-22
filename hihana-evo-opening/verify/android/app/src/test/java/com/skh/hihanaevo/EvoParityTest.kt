package com.skh.hihanaevo

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Compose 이식(EvoScene)이 Python 기준 엔진과 같은 기하를 내는지: 여러 시각의 세그먼트 3차 조각·그라데이션 끝점 비교.
 * 하네스가 EvoOpening.kt 를 복사할 때 `private class EvoScene` / `SegmentRig` 등을 internal 로 바꿔 접근한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EvoParityTest {
    @Test
    fun matchesPythonReference() {
        val ref = JSONObject(File(System.getProperty("evo.reference")!!).readText())
        val scene = EvoScene()
        val frames = ref.getJSONArray("frames")
        var worstPath = 0.0; var worstGrad = 0.0; var worstShade = 0.0
        val report = StringBuilder()
        for (fi in 0 until frames.length()) {
            val fr = frames.getJSONObject(fi)
            val t = fr.getDouble("t").toFloat()
            for (r in 0 until 3) scene.ribbons[r].update(t)
            val segs = fr.getJSONArray("segments")
            // 같은 (면, k) 끼리 짝짓는다
            val bySid = HashMap<String, ArrayList<SegmentRig>>()
            for ((i, s) in scene.surfaces.withIndex()) bySid[s.id] = ArrayList(scene.rigs[i])
            var fPath = 0.0; var fGrad = 0.0
            for (j in 0 until segs.length()) {
                val sj = segs.getJSONObject(j)
                val rig = bySid[sj.getString("sid")]!!.first { it.k == sj.getInt("k") }
                rig.update()
                val cub = sj.getJSONArray("cubics")
                check(cub.length() == rig.cub.size) { "vertex count ${sj.getString("sid")} k=${sj.getInt("k")}: ${cub.length()} vs ${rig.cub.size}" }
                var segMax = 0.0; var segQ = 0
                // 원근으로 카메라 뒤(화면 밖 수만 단위)까지 확대되는 점은 float 정밀도가 그만큼 거칠다 → 좌표 크기 1000 단위당 허용
                for (q in 0 until cub.length()) {
                    val ref = cub.getDouble(q)
                    val d = abs(ref - rig.cub[q]) / max(1.0, abs(ref) / 1000.0)
                    if (d > segMax) { segMax = d; segQ = q }
                }
                if (segMax > 0.3) println("  seg ${sj.getString("sid")} k=${sj.getInt("k")} max ${"%.3f".format(segMax)} at q=$segQ n=${rig.n} py=${cub.getDouble(segQ)} kt=${rig.cub[segQ]}")
                fPath = max(fPath, segMax)
                val g = sj.getJSONArray("grad")
                for (q in 0 until 4) fGrad = max(fGrad, abs(g.getDouble(q) - rig.grad[q]) / max(1.0, abs(g.getDouble(q)) / 1000.0))
                val sh = sj.getJSONArray("shade")
                worstShade = max(worstShade, max(abs(sh.getDouble(0) - rig.shadeA0), abs(sh.getDouble(1) - rig.shadeA1)))
            }
            report.append("t=%.2f  path max|Δ|=%.4f  grad max|Δ|=%.4f\n".format(t, fPath, fGrad))
            worstPath = max(worstPath, fPath); worstGrad = max(worstGrad, fGrad)
        }
        println(report)
        println("worst path %.4f, grad %.4f, shade %.5f (logo units; 좌표가 1000 을 넘으면 1000 단위당)".format(worstPath, worstGrad, worstShade))
        assertTrue("path diff $worstPath", worstPath < 0.25)
        assertTrue("grad diff $worstGrad", worstGrad < 0.5)
    }
}
