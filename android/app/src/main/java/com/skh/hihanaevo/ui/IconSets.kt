package com.skh.hihanaevo.ui

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * 바깥 아이콘 세트 열두 벌(Material Symbols · Lucide · Phosphor · Fluent · Tabler · Heroicons · Hugeicons · Iconoir ·
 * Ionicons · MingCute · Remix Icon · Solar) — Iconify 에서 받은 SVG 를 경로 묶음으로 바꿔 둔 assets/icon_sets.json 을 한 번
 * 읽어, 고른 세트만 [ImageVector] 로 만듭니다. 세트마다 탭 아이콘 여덟(바깥선, 있으면 채운 것)과 버튼 섬·모든 기능의 작은
 * 아이콘 열셋. 색은 그리는 쪽의 tint 가 정하므로 경로는 검정으로 둡니다. 두께([IconStyleStore.weight])는 선이면 선 굵기를
 * 곱하고, 면으로 된 아이콘은 굵게 할 때만 같은 색 테두리를 둘러 키웁니다(면은 가늘게 할 수 없음).
 */
object IconSets {
    private const val TAG = "IconSets"

    class IconSet(val label: String, private val tab: List<Pair<ImageVector, ImageVector>>, private val app: Map<String, ImageVector>) {
        /** 탭 순서 칸([slot]) — 고른 탭은 채운 것(세트에 없으면 바깥선 그대로). */
        fun tab(slot: Int, selected: Boolean): ImageVector = tab[slot].let { if (selected) it.second else it.first }

        /** 작은 아이콘 — check · edit · calendar · search · list · warning · share · refresh · settings · qr · bell · inbox · weather. */
        fun app(key: String): ImageVector? = app[key]
    }

    private var json: JSONObject? = null
    private val cache = HashMap<String, IconSet>()

    fun init(context: Context) {
        if (json != null) return
        json = runCatching { JSONObject(context.applicationContext.assets.open("icon_sets.json").bufferedReader().use { it.readText() }) }
            .onFailure { Log.w(TAG, "icon_sets.json: ${it.message}") }
            .getOrNull()
    }

    fun get(prefix: String, weight: Float = IconStyleStore.weight): IconSet? {
        val key = "$prefix@$weight"
        return cache[key] ?: json?.optJSONObject(prefix)?.let { build(it, weight) }?.also { cache[key] = it }
    }

    private fun build(o: JSONObject, weight: Float): IconSet {
        val tabs = o.getJSONArray("tab")
        val tab = (0 until tabs.length()).map { i ->
            val pair = tabs.getJSONArray(i)
            val outline = vector(pair.getJSONObject(0), weight)
            outline to if (pair.length() > 1) vector(pair.getJSONObject(1), weight) else outline
        }
        val apps = o.getJSONObject("app")
        val app = apps.keys().asSequence().associateWith { vector(apps.getJSONObject(it), weight) }
        return IconSet(o.getString("label"), tab, app)
    }

    private fun vector(o: JSONObject, weight: Float): ImageVector {
        val viewport = o.getDouble("w").toFloat()
        val builder = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = viewport,
            viewportHeight = o.getDouble("h").toFloat(),
        )
        val paths = o.getJSONArray("p")
        for (i in 0 until paths.length()) {
            val p = paths.getJSONObject(i)
            val filled = p.optInt("f") == 1
            // 선은 두께를 곱하고, 면은 굵게 할 때만 테두리(24 격자에서 1.4 × 늘린 만큼)를 둘러 키웁니다.
            val stroke = p.optDouble("s", 0.0).toFloat().let { s ->
                when {
                    s > 0f -> s * weight
                    filled && weight > 1f -> (weight - 1f) * 1.4f * viewport / 24f
                    else -> 0f
                }
            }
            builder.addPath(
                pathData = PathParser().parsePathString(p.getString("d")).toNodes(),
                pathFillType = if (p.optInt("e") == 1) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (stroke > 0f) SolidColor(Color.Black) else null,
                strokeLineWidth = stroke,
                strokeLineCap = when (p.optString("c")) { "r" -> StrokeCap.Round; "s" -> StrokeCap.Square; else -> if (filled) StrokeCap.Round else StrokeCap.Butt },
                strokeLineJoin = when (p.optString("j")) { "r" -> StrokeJoin.Round; "b" -> StrokeJoin.Bevel; else -> if (filled) StrokeJoin.Round else StrokeJoin.Miter },
            )
        }
        return builder.build()
    }
}
