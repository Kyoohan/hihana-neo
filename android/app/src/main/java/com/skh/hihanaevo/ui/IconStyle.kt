package com.skh.hihanaevo.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.R

/**
 * 아이콘 결 — 설정 → 개인화에서 고릅니다. 기본은 머티리얼(예전 앱 아이콘), One UI 는 [OneUiIcons], 나머지 일곱은 디자인
 * 캔버스의 A~G 시안(두톤·입체 컬러·굵은 단색·색 배지·그라데이션 선·유리 타일·손그림)입니다. 하단 바의 탭 아이콘은 결마다
 * 따로 그리고, 버튼 섬의 작은 아이콘(검색·새로고침·설정…)은 머티리얼이면 머티리얼, 그 밖에는 One UI 선 아이콘을 씁니다.
 * 뒤의 열두 결은 바깥 아이콘 세트([IconSets]) — 탭 아이콘과 작은 아이콘 모두 그 세트의 것을 씁니다.
 */
enum class IconStyle(val label: String, /** 바깥 아이콘 세트의 Iconify 접두어([IconSets]) — 직접 그린 결이면 null. */ val set: String? = null) {
    MATERIAL("머티리얼"),
    ONE_UI("One UI"),
    DUOTONE("두톤"),
    COLOR_3D("입체 컬러"),
    BOLD("굵은 단색"),
    BADGE("색 배지"),
    GRADIENT("그라데이션 선"),
    GLASS("유리 타일"),
    SKETCH("손그림"),
    SYMBOLS("Material Symbols", "material-symbols"),
    LUCIDE("Lucide", "lucide"),
    PHOSPHOR("Phosphor", "ph"),
    FLUENT("Fluent", "fluent"),
    TABLER("Tabler", "tabler"),
    HEROICONS("Heroicons", "heroicons"),
    HUGEICONS("Hugeicons", "hugeicons"),
    ICONOIR("Iconoir", "iconoir"),
    IONICONS("Ionicons", "ion"),
    MINGCUTE("MingCute", "mingcute"),
    REMIX("Remix Icon", "ri"),
    SOLAR("Solar", "solar"),
}

object IconStyleStore {
    private const val PREFS = "icon_style"
    private const val KEY = "style"
    private const val KEY_BUTTON = "button_style"
    private const val KEY_WEIGHT = "weight"

    /** 두께 단계 — 설정의 칩. */
    val weights = listOf(0.75f to "가늘게", 1f to "보통", 1.3f to "굵게", 1.6f to "아주 굵게")

    var style by mutableStateOf(IconStyle.MATERIAL)
        private set

    /** 버튼 섬·툴바·모든 기능 블록의 결 — null 이면 하단 바와 같게. */
    var buttonStyle by mutableStateOf<IconStyle?>(null)
        private set

    /** 선 두께 배율 — 직접 그린 결과 바깥 세트에만(머티리얼·One UI 는 그대로). */
    var weight by androidx.compose.runtime.mutableFloatStateOf(1f)
        private set

    /** 버튼 쪽이 실제로 쓰는 결. */
    val buttons: IconStyle get() = buttonStyle ?: style

    fun load(context: Context) {
        IconSets.init(context)
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        style = IconStyle.entries.firstOrNull { it.name == saved } ?: IconStyle.MATERIAL
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        buttonStyle = IconStyle.entries.firstOrNull { it.name == prefs.getString(KEY_BUTTON, null) }
        weight = prefs.getFloat(KEY_WEIGHT, 1f)
    }

    fun setButton(context: Context, value: IconStyle?) {
        buttonStyle = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BUTTON, value?.name).apply()
    }

    fun setWeight(context: Context, value: Float) {
        weight = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_WEIGHT, value).apply()
    }

    fun set(context: Context, value: IconStyle) {
        style = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.name).apply()
    }
}

/**
 * 버튼 섬·툴바·모든 기능 블록의 작은 아이콘 — 버튼 결([IconStyleStore.buttons], 기본은 하단 바와 같게)이 바깥 세트면 그 세트의 것,
 * 머티리얼이면 예전 머티리얼 아이콘, 그 밖에는 One UI 선 아이콘.
 */
object AppIcons {
    private val material get() = IconStyleStore.buttons == IconStyle.MATERIAL
    private fun set() = IconStyleStore.buttons.set?.let { IconSets.get(it) }
    private fun fromSet(key: String): ImageVector? = set()?.app(key)

    /**
     * 모든 기능 블록 — 버튼 결이 바깥 세트일 때만 그 세트의 아이콘(아니면 null, 블록은 머티리얼 그대로). [key]:
     * timetable · academic · meal · board · apply · classroom · alim · weather.
     */
    fun block(key: String): ImageVector? {
        val s = set() ?: return null
        return when (key) {
            "timetable" -> s.tab(5, false)
            "academic" -> s.app("calendar")
            "meal" -> s.tab(2, false)
            "board" -> s.tab(3, false)
            "apply" -> s.tab(1, false)
            "classroom" -> s.tab(4, false)
            "alim" -> s.app("inbox")
            "weather" -> s.app("weather")
            else -> null
        }
    }
    val Check: ImageVector get() = fromSet("check") ?: if (material) Icons.Default.Check else OneUiIcons.Check
    val Edit: ImageVector get() = fromSet("edit") ?: if (material) Icons.Default.Edit else OneUiIcons.Edit
    val Calendar: ImageVector get() = fromSet("calendar") ?: if (material) Icons.Default.DateRange else OneUiIcons.Calendar
    val Search: ImageVector get() = fromSet("search") ?: if (material) Icons.Default.Search else OneUiIcons.Search
    val List: ImageVector get() = fromSet("list") ?: if (material) Icons.AutoMirrored.Filled.List else OneUiIcons.List
    val Warning: ImageVector get() = fromSet("warning") ?: if (material) Icons.Default.Warning else OneUiIcons.Warning
    val Share: ImageVector get() = fromSet("share") ?: if (material) Icons.Default.Share else OneUiIcons.Share
    val Refresh: ImageVector get() = fromSet("refresh") ?: if (material) Icons.Default.Refresh else OneUiIcons.Refresh
    val Settings: ImageVector get() = fromSet("settings") ?: if (material) Icons.Default.Settings else OneUiIcons.Settings

    @Composable
    fun bell(): Painter = fromSet("bell")?.let { rememberVectorPainter(it) } ?: if (material) painterResource(R.drawable.ic_notify_settings) else rememberVectorPainter(OneUiIcons.Bell)

    @Composable
    fun inbox(): Painter = fromSet("inbox")?.let { rememberVectorPainter(it) } ?: if (material) painterResource(R.drawable.ic_inbox) else rememberVectorPainter(OneUiIcons.Inbox)
}

/** 탭 아이콘 한 장의 뼈대(24 격자) — 칠할 몸통, 굵은 선, 안쪽 무늬(몸통 위에 파내거나 대비색으로), 구멍. */
private class Glyph(
    val fill: String = "",
    val line: String = "",
    val cut: String = "",
    val hole: String = "",
    /** 고른 탭의 바탕색 · 밝은 쪽 색(입체·배지의 그라데이션 위쪽). */
    val color: Color,
    val light: Color,
    /** 그라데이션 선의 시작·끝 색. */
    val gradA: Color,
    val gradB: Color,
    /** 손그림의 형광펜 색. */
    val mark: Color,
)

private const val HOME_BODY = "M4.5 10.3 10.6 4.9Q12 3.7 13.4 4.9L19.5 10.3Q20 10.75 20 11.5V17.5Q20 20 17.5 20H6.5Q4 20 4 17.5V11.5Q4 10.75 4.5 10.3Z"
private const val CLIP_BODY = "M7 4.5H17Q19 4.5 19 6.5V19Q19 21 17 21H7Q5 21 5 19V6.5Q5 4.5 7 4.5Z"
private const val CLIP_TOP = "M9.5 3H14.5Q15.5 3 15.5 4V5.5H8.5V4Q8.5 3 9.5 3Z"
private const val SPOON = "M16.5 3.5C14.8 3.5 14 5.3 14 7.2C14 9.1 15.1 10.6 16.5 10.7C17.9 10.6 19 9.1 19 7.2C19 5.3 18.2 3.5 16.5 3.5Z"
private const val SPEAKER = "M4 10.2Q4 9 5.2 9H7.5L13.5 5.4Q14.5 4.8 14.5 6V18Q14.5 19.2 13.5 18.6L7.5 15H5.2Q4 15 4 13.8Z"
private const val CAP = "M12 4.5 21.5 9.2 12 13.9 2.5 9.2Z"
private const val CAL_BODY = "M7 5H17Q20 5 20 8V17.5Q20 20.5 17 20.5H7Q4 20.5 4 17.5V8Q4 5 7 5Z"
private const val CAL_DOTS = "M4 9.8H20 M8.5 13.6h.01 M12 13.6h.01 M15.5 13.6h.01 M8.5 17h.01 M12 17h.01"
private const val TIMELINE_NODES = "M4.5 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z M4.5 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z M4.5 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"
private const val FOUR_TILES = "M4 6Q4 4 6 4H8.8Q10.8 4 10.8 6V8.8Q10.8 10.8 8.8 10.8H6Q4 10.8 4 8.8Z " +
    "M13.2 6Q13.2 4 15.2 4H18Q20 4 20 6V8.8Q20 10.8 18 10.8H15.2Q13.2 10.8 13.2 8.8Z " +
    "M4 15.2Q4 13.2 6 13.2H8.8Q10.8 13.2 10.8 15.2V18Q10.8 20 8.8 20H6Q4 20 4 18Z " +
    "M13.2 15.2Q13.2 13.2 15.2 13.2H18Q20 13.2 20 15.2V18Q20 20 18 20H15.2Q13.2 20 13.2 18Z"

/**
 * 탭 순서(홈·신청·급식·게시판·클래스룸·일정)대로, 그 뒤에 Evo 하단 바의 타임라인·모든 기능(⊞) — 두 벌 — 칠한 결(두톤·입체·굵은 단색)은 숟가락·학사모를 통째로 칠하고,
 * 선 결(배지·그라데이션·유리·손그림)은 숟가락 자루와 학사모 아래를 선으로 둡니다.
 */
private val solidGlyphs = listOf(
    Glyph(HOME_BODY, cut = "M10 20V16Q10 15 11 15H13Q14 15 14 16V20", color = Color(0xFF2F74F0), light = Color(0xFF8FC0FF), gradA = Color(0xFF5FB3FF), gradB = Color(0xFF6C63FF), mark = Color(0xFF9CC3FF)),
    Glyph(CLIP_BODY, line = CLIP_TOP, cut = "M8.8 13.3 11 15.5 15.4 11", color = Color(0xFF6A55EE), light = Color(0xFFB9AEFF), gradA = Color(0xFFA88BFF), gradB = Color(0xFFFF7EB6), mark = Color(0xFFC6B8FF)),
    Glyph("M6 3.5H10V8Q10 10.8 8 10.8Q6 10.8 6 8Z $SPOON", line = "M8 10.8V20.5 M16.5 10.7V20.5", cut = "M8 3.5V7.4", color = Color(0xFFF0712A), light = Color(0xFFFFC28F), gradA = Color(0xFFFFC15E), gradB = Color(0xFFFF6A5C), mark = Color(0xFFFFC59A)),
    Glyph(SPEAKER, line = "M7.8 15 9 19.5 M17.5 9.5Q19 12 17.5 14.5", color = Color(0xFFF0A21E), light = Color(0xFFFFE08A), gradA = Color(0xFFFFD45E), gradB = Color(0xFFFF8A3D), mark = Color(0xFFFFE08A)),
    Glyph("$CAP M6.2 13.9 12 16.8 17.8 13.9V15.9Q12 20 6.2 15.9Z", line = "M21.5 9.2V14", color = Color(0xFF17A86A), light = Color(0xFF86EBB9), gradA = Color(0xFF5CF0B0), gradB = Color(0xFF2CA4E0), mark = Color(0xFF9DE6C1)),
    Glyph(CAL_BODY, line = "M8.5 3.3V6.6 M15.5 3.3V6.6", cut = CAL_DOTS, color = Color(0xFFEC4060), light = Color(0xFFFF9CB0), gradA = Color(0xFFFF8FA3), gradB = Color(0xFFB36CFF), mark = Color(0xFFFFB3C0)),
    Glyph(TIMELINE_NODES, line = "M6.5 8V10 M6.5 14V16 M11 6H19.5 M11 12H17 M11 18H18.5", color = Color(0xFF14A8B8), light = Color(0xFF8CE6EE), gradA = Color(0xFF38EAA8), gradB = Color(0xFF2C7BF0), mark = Color(0xFF9DE6EC)),
    Glyph(FOUR_TILES, color = Color(0xFF5B5BF0), light = Color(0xFFB4B2FF), gradA = Color(0xFF8C6BFF), gradB = Color(0xFFFF5A97), mark = Color(0xFFC3C0FF)),
)

private val outlineGlyphs = solidGlyphs.mapIndexed { i, g ->
    when (i) {
        2 -> Glyph("M6 3.5V8Q6 10.8 8 10.8Q10 10.8 10 8V3.5 $SPOON", line = "M8 3.5V20.5 M16.5 10.7V20.5", color = g.color, light = g.light, gradA = g.gradA, gradB = g.gradB, mark = g.mark)
        4 -> Glyph(CAP, line = "M6.2 11.3V15.6Q12 19.6 17.8 15.6V11.3 M21.5 9.2V14", color = g.color, light = g.light, gradA = g.gradA, gradB = g.gradB, mark = g.mark)
        else -> g
    }
}

private class GlyphPaths(val fill: Path, val line: Path, val cut: Path, val hole: Path)

private fun parse(d: String): Path = if (d.isEmpty()) Path() else PathParser().parsePathString(d).toPath()

private val solidPaths by lazy { solidGlyphs.map { GlyphPaths(parse(it.fill), parse(it.line), parse(it.cut), parse(it.hole)) } }
private val outlinePaths by lazy { outlineGlyphs.map { GlyphPaths(parse(it.fill), parse(it.line), parse(it.cut), parse(it.hole)) } }

/** 직접 그린 결의 선 — 설정의 아이콘 두께를 곱합니다(그리기 단계에서 읽어 바꾸면 바로 다시 그림). */
private fun stroke(width: Float, effect: PathEffect? = null) = Stroke(width = width * IconStyleStore.weight, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)

/**
 * 손그림 — 선을 0.8 간격으로 다시 찍으며 옆으로 살짝 흔듭니다(24 격자 기준이라 크기와 상관없이 같은 정도). 씨앗이 고정이라
 * 그릴 때마다 떨림이 바뀌지 않습니다. 점(h.01)처럼 아주 짧은 조각은 그대로 둡니다.
 */
private fun sketchify(path: Path, seed: Int): Path {
    val random = java.util.Random(seed.toLong())
    val out = android.graphics.Path()
    val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
    val pos = FloatArray(2)
    val tan = FloatArray(2)
    do {
        val length = measure.length
        // 닫은 뒤 새로 시작하는 빈 조각(길이 0)은 건너뜁니다 — 점(h.01)은 길이 0.01 이라 남습니다.
        if (length < 0.001f) continue
        if (length < 0.6f) {
            measure.getPosTan(0f, pos, tan)
            out.moveTo(pos[0], pos[1]); out.lineTo(pos[0] + 0.01f, pos[1])
            continue
        }
        val steps = (length / 0.8f).toInt().coerceAtLeast(2)
        var wobble = 0f
        for (i in 0..steps) {
            measure.getPosTan(length * i / steps, pos, tan)
            wobble = (wobble * 0.6f + (random.nextFloat() - 0.5f) * 0.5f).coerceIn(-0.35f, 0.35f)
            val x = pos[0] - tan[1] * wobble
            val y = pos[1] + tan[0] * wobble
            if (i == 0) out.moveTo(x, y) else out.lineTo(x, y)
        }
    } while (measure.nextContour())
    return out.asComposePath()
}

private val sketchPaths by lazy {
    outlinePaths.mapIndexed { i, p -> GlyphPaths(sketchify(p.fill, i * 4), sketchify(p.line, i * 4 + 1), sketchify(p.cut, i * 4 + 2), sketchify(p.hole, i * 4 + 3)) }
}

/**
 * 하단 바·패널의 탭 아이콘을 고른 결로 — [slot] 은 탭 순서(0 홈 … 5 일정), [tint] 는 바가 정한 글자색(고른 탭은 진하게).
 * 머티리얼·One UI 는 부르는 쪽에서 [ImageVector] 로 그리고, 여기는 A~G 결만 캔버스에 직접 그립니다.
 */
@Composable
internal fun StyledTabIcon(slot: Int, style: IconStyle, tint: Color, selected: Boolean, label: String, size: Dp = 26.dp) {
    val outline = style == IconStyle.BADGE || style == IconStyle.GRADIENT || style == IconStyle.GLASS || style == IconStyle.SKETCH
    val glyph = (if (outline) outlineGlyphs else solidGlyphs)[slot]
    val paths = remember(outline, slot) { (if (outline) outlinePaths else solidPaths)[slot] }
    Canvas(
        Modifier
            .size(size)
            .semantics { contentDescription = label }
            // 굵은 단색·입체의 안쪽 무늬는 몸통을 파내서(투명) 그립니다 — 바 뒤의 유리·사진이 그대로 비치게.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        when (style) {
            IconStyle.BADGE -> drawBadge(paths, glyph, tint, selected)
            IconStyle.GLASS -> drawGlass(paths, tint, selected)
            else -> scale(this.size.width / 24f, pivot = Offset.Zero) { drawGlyph(paths, glyph, style, tint, selected) }
        }
    }
}

private fun DrawScope.drawGlyph(p: GlyphPaths, g: Glyph, style: IconStyle, tint: Color, selected: Boolean) {
    when (style) {
        IconStyle.DUOTONE -> {
            val tone = if (selected) g.color.copy(alpha = 0.5f) else tint.copy(alpha = 0.16f)
            drawPath(p.fill, tone, style = Fill)
            drawPath(p.hole, tone, style = Fill)
            drawPath(p.fill, tint, style = stroke(1.7f))
            drawPath(p.hole, tint, style = stroke(1.7f))
            drawPath(p.line, tint, style = stroke(1.7f))
            drawPath(p.cut, tint, style = stroke(1.7f))
        }
        IconStyle.COLOR_3D -> {
            val brush = if (selected) Brush.linearGradient(listOf(g.light, g.color), Offset(4f, 3f), Offset(12f, 21f))
            else Brush.linearGradient(listOf(tint.copy(alpha = 0.75f), tint), Offset(4f, 3f), Offset(12f, 21f))
            drawPath(p.fill, brush, style = Fill)
            drawPath(p.fill, brush, style = stroke(1.4f))
            drawPath(p.line, brush, style = stroke(2.6f))
            drawPath(p.hole, Color.Black, style = Fill, blendMode = BlendMode.Clear)
            // 고른 탭은 흰 무늬 + 위쪽 빛 테두리, 나머지는 무늬를 파냅니다.
            if (selected) {
                drawPath(p.cut, Color.White, style = stroke(1.7f))
                drawPath(p.fill, Color.White.copy(alpha = 0.28f), style = stroke(0.5f))
            } else drawPath(p.cut, Color.Black, style = stroke(1.7f), blendMode = BlendMode.Clear)
        }
        IconStyle.BOLD -> {
            drawPath(p.fill, tint, style = Fill)
            drawPath(p.fill, tint, style = stroke(1.8f))
            drawPath(p.line, tint, style = stroke(2.5f))
            drawPath(p.hole, Color.Black, style = Fill, blendMode = BlendMode.Clear)
            drawPath(p.cut, Color.Black, style = stroke(1.7f), blendMode = BlendMode.Clear)
        }
        IconStyle.GRADIENT -> {
            val brush: Brush = if (selected) Brush.linearGradient(listOf(g.gradA, g.gradB), Offset(3f, 3f), Offset(21f, 21f))
            else Brush.linearGradient(listOf(tint, tint))
            listOf(p.fill, p.hole, p.line, p.cut).forEach { drawPath(it, brush, style = stroke(1.9f)) }
        }
        IconStyle.SKETCH -> {
            if (selected) translate(0.8f, 0.9f) { drawPath(p.fill, g.mark.copy(alpha = 0.75f), style = Fill) }
            val sk = sketchPaths[solidPaths.indexOf(p).takeIf { it >= 0 } ?: outlinePaths.indexOf(p)]
            listOf(sk.fill, sk.hole, sk.line, sk.cut).forEach { drawPath(it, tint, style = stroke(1.6f)) }
        }
        else -> Unit
    }
}

/** 색 배지 — 둥근 네모 판 위에 선 아이콘. 고른 탭은 탭 색 그라데이션 판에 흰 선, 나머지는 옅은 판에 글자색 선. */
private fun DrawScope.drawBadge(p: GlyphPaths, g: Glyph, tint: Color, selected: Boolean) {
    val r = CornerRadius(size.width * 0.32f)
    if (selected) drawRoundRect(Brush.linearGradient(listOf(g.light, g.color), Offset(0f, 0f), Offset(size.width * 0.4f, size.height)), cornerRadius = r)
    else drawRoundRect(tint.copy(alpha = 0.14f), cornerRadius = r)
    val ink = if (selected) Color.White else tint
    val inset = size.width * 0.2f
    translate(inset, inset) {
        scale((size.width - inset * 2) / 24f, pivot = Offset.Zero) {
            listOf(p.fill, p.hole, p.line, p.cut).forEach { drawPath(it, ink, style = stroke(2.1f)) }
        }
    }
}

/** 유리 타일 — 고른 탭만 반투명 흰 판과 빛 테두리, 몸통은 옅게 칠한 선 아이콘. */
private fun DrawScope.drawGlass(p: GlyphPaths, tint: Color, selected: Boolean) {
    if (selected) {
        val r = CornerRadius(size.width * 0.34f)
        drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.45f), cornerRadius = r, style = Stroke(width = 1.dp.toPx()))
    }
    val inset = size.width * 0.16f
    translate(inset, inset) {
        scale((size.width - inset * 2) / 24f, pivot = Offset.Zero) {
            drawPath(p.fill, tint.copy(alpha = if (selected) 0.35f else 0.12f), style = Fill)
            listOf(p.fill, p.hole, p.line, p.cut).forEach { drawPath(it, tint, style = stroke(1.8f)) }
        }
    }
}

/** 설정의 버튼 아이콘 고르기 줄 미리보기 — 그 결의 알리미·검색·새로고침·설정. */
@Composable
internal fun ButtonIconPreview(style: IconStyle, tint: Color) {
    val set = style.set?.let { IconSets.get(it) }
    val icons: List<ImageVector> = when {
        set != null -> listOfNotNull(set.app("inbox"), set.app("search"), set.app("refresh"), set.app("settings"))
        style == IconStyle.MATERIAL -> listOf(Icons.Default.Search, Icons.Default.Refresh, Icons.Default.Settings)
        else -> listOf(OneUiIcons.Inbox, OneUiIcons.Search, OneUiIcons.Refresh, OneUiIcons.Settings)
    }
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        // 머티리얼 알리미는 벡터가 아니라 그림 파일이라 따로 — 다른 결처럼 알리미·검색·새로고침·설정 넷.
        if (set == null && style == IconStyle.MATERIAL) {
            Icon(painterResource(R.drawable.ic_inbox), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        icons.forEach { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
    }
}

/** 설정의 결 고르기 줄에 놓는 미리보기 — 고른 홈 + 급식·게시판·일정. */
@Composable
internal fun IconStylePreview(style: IconStyle, tint: Color, selectedTint: Color) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        listOf(0, 2, 3, 5).forEach { slot ->
            TabIconFor(slot, style, if (slot == 0) selectedTint else tint, selected = slot == 0, size = 22.dp)
        }
    }
}

/** 탭 순서 칸([slot])의 아이콘을 [style] 로 — 머티리얼·One UI 는 벡터, 나머지는 [StyledTabIcon]. */
@Composable
internal fun TabIconFor(slot: Int, style: IconStyle, tint: Color, selected: Boolean, size: Dp = 24.dp) {
    val label = TAB_LABELS[slot]
    when (style) {
        IconStyle.MATERIAL -> when (slot) {
            2 -> Icon(painterResource(R.drawable.ic_meal), contentDescription = label, tint = tint, modifier = Modifier.size(size))
            4 -> Icon(painterResource(R.drawable.ic_school), contentDescription = label, tint = tint, modifier = Modifier.size(size))
            // 기본 묶음에 없는 머티리얼 아이콘(시계·네모 넷)은 MaterialExtraIcons 에서.
            6 -> Icon(if (selected) MaterialExtraIcons.ScheduleFilled else MaterialExtraIcons.Schedule, contentDescription = label, tint = tint, modifier = Modifier.size(size))
            7 -> Icon(if (selected) MaterialExtraIcons.GridViewFilled else MaterialExtraIcons.GridView, contentDescription = label, tint = tint, modifier = Modifier.size(size))
            else -> Icon(materialTab(slot, selected), contentDescription = label, tint = tint, modifier = Modifier.size(size))
        }
        IconStyle.ONE_UI -> Icon(oneUiTab(slot, selected), contentDescription = label, tint = tint, modifier = Modifier.size(size))
        else -> {
            val set = style.set?.let { IconSets.get(it) }
            if (set != null) Icon(set.tab(slot, selected), contentDescription = label, tint = tint, modifier = Modifier.size(size))
            else StyledTabIcon(slot, style, tint, selected, label, size = size + 2.dp)
        }
    }
}

private val TAB_LABELS = listOf("홈", "신청·내역", "급식", "게시판", "클래스룸", "일정", "타임라인", "전체")

/** Evo 하단 바(홈 · 타임라인 · 모든 기능)의 칸 → 탭 아이콘 칸. */
internal val EVO_NAV_SLOTS = listOf(0, 6, 7)

private fun materialTab(slot: Int, selected: Boolean): ImageVector = when (slot) {
    0 -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    1 -> if (selected) Icons.Filled.Edit else Icons.Outlined.Edit
    3 -> if (selected) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Outlined.List
    else -> if (selected) Icons.Filled.DateRange else Icons.Outlined.DateRange
}

private fun oneUiTab(slot: Int, selected: Boolean): ImageVector = when (slot) {
    0 -> if (selected) OneUiIcons.HomeFilled else OneUiIcons.Home
    1 -> if (selected) OneUiIcons.EditFilled else OneUiIcons.Edit
    2 -> if (selected) OneUiIcons.MealFilled else OneUiIcons.Meal
    3 -> if (selected) OneUiIcons.BoardFilled else OneUiIcons.Board
    4 -> if (selected) OneUiIcons.ClassroomFilled else OneUiIcons.Classroom
    6 -> if (selected) OneUiIcons.TimelineFilled else OneUiIcons.Timeline
    7 -> if (selected) OneUiIcons.AppsFilled else OneUiIcons.Apps
    else -> if (selected) OneUiIcons.CalendarFilled else OneUiIcons.Calendar
}
