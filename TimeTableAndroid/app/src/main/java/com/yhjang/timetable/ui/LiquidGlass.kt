package com.yhjang.timetable.ui

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 하단 바 캡슐의 "액체 유리" 렌즈 셰이더 (AGSL, Android 13+) — liquidGL 의 개념을 옮긴 것입니다.
 * `content` 는 바 뒤의 실제 화면. 가운데는 살짝 확대(magnify)하고, 둥글게 깎인 두꺼운 유리 가장자리(원호 단면)에서는
 * SDF 법선 방향으로 샘플을 크게 밀어 캡슐 바깥 화면이 림 안쪽 띠에 눌려 들어오게 하며(refraction), 빨강·파랑을 다른
 * 거리로 굴절시켜 무지개 테(dispersion)를 만들고, 테를 따라 색이 도는 분광 림과 광원을 향한 경사면의 반사광(specular),
 * 천천히 흐르는 광택 띠를 얹습니다. Android 12 이하에서는 null.
 */
object LiquidLens {
    private const val AGSL = """
        uniform shader content;
        uniform float4 rect;
        uniform float radius;
        uniform float strength;
        uniform float2 lightDir;
        uniform float time;
        uniform float3 tint;
        uniform float tintAlpha;
        uniform float dir;
        uniform float rainbow;

        float sdRoundRect(float2 p, float2 c, float2 halfSize, float r) {
            float2 q = abs(p - c) - (halfSize - r);
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        // 코사인 팔레트 — 0..1 위상을 빨→노→초→파→보로 돌려 분광(무지개) 색을 만듭니다.
        half3 spectrum(float t) {
            return half3(0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.33, 0.67))));
        }

        half4 main(float2 p) {
            float2 c = (rect.xy + rect.zw) * 0.5;
            float2 halfSize = (rect.zw - rect.xy) * 0.5;
            float d = sdRoundRect(p, c, halfSize, radius);
            float aa = 1.0 - smoothstep(-0.6, 0.6, d);
            if (aa <= 0.0) return half4(0.0);

            // 유리 두께 단면 — 가운데는 평평, 가장자리는 둥글게 깎인 두꺼운 유리(원호). 림에서 기울기가 무한대라
            // 림 바로 안쪽의 얇은 띠에 바깥 화면이 강하게 눌려 들어오고(iOS 탭 바처럼 아이콘이 겹쳐 보임),
            // 안쪽으로 갈수록 빠르게 평평해집니다.
            float minHalf = min(halfSize.x, halfSize.y);
            float bevelW = minHalf * (0.55 + 0.20 * strength);
            float u = clamp(1.0 + d / bevelW, 0.0, 1.0);   // 0 = 평평한 안쪽, 1 = 림
            float h = sqrt(max(1.0 - u * u, 0.0));         // 원호 높이
            float bend = 1.0 - h;                          // 굴절량: 림에서 1 로 급격히

            float eps = 1.0;
            float2 grad = float2(
                sdRoundRect(p + float2(eps, 0.0), c, halfSize, radius) - sdRoundRect(p - float2(eps, 0.0), c, halfSize, radius),
                sdRoundRect(p + float2(0.0, eps), c, halfSize, radius) - sdRoundRect(p - float2(0.0, eps), c, halfSize, radius)
            );
            float2 n = normalize(grad + float2(0.0001, 0.0));

            // 굴절 — 가운데 살짝 확대, 경사면에서는 법선 방향(바깥)으로 크게 밀어 캡슐 바깥의 화면을 끌어옵니다.
            // strength 0 이면 굴절·확대 없음 (하단 바 캡슐이 놓여 있을 때) — 아이콘·글자가 찌그러지지 않게.
            float magnify = 1.0 + 0.08 * strength;
            float2 base = c + (p - c) / magnify;
            // dir = +1: 바깥 화면을 끌어옴(하단 바 캡슐, 상자에 여유가 있을 때) / -1: 안쪽을 당겨 두꺼운 유리 가장자리처럼.
            float2 disp = n * dir * bend * bevelW * 1.25 * strength;
            // 분산 — 빨강은 더 멀리, 파랑은 덜 굴절되어 대비가 큰 가장자리마다 무지개 테가 생깁니다.
            float ab = (0.03 + 0.15 * bend) * strength;
            half r = content.eval(base + disp * (1.0 + ab)).r;
            half4 g = content.eval(base + disp);
            half b = content.eval(base + disp * (1.0 - ab)).b;
            // 알파를 유지합니다 (premultiplied) — 하단 바 캡슐처럼 내용이 아이콘뿐이고 나머지가 투명한 경우,
            // 그 밑에 따로 깔린 서리 유리가 비쳐야 합니다.
            half4 src = half4(r, g.g, b, g.a);

            // 반사광 — 광원을 향한 경사면은 밝고, 반대편은 살짝 그늘.
            float2 l = normalize(lightDir + float2(0.0001, 0.0));
            float facing = dot(n, l);
            float lit = clamp(facing, 0.0, 1.0);
            float bevelLight = pow(lit, 2.0) * bend;
            float shade = clamp(-facing, 0.0, 1.0) * bend;

            // 무지개 림 — 유리 가장자리에서 빛이 분광되어 색이 갈라집니다. 테를 따라 돌면서 색이 바뀌고,
            // 광원(기울기)과 시간에 따라 천천히 흐릅니다. 림 3px 는 진하게, 경사 띠 전체엔 옅게.
            float angle = atan(n.y, n.x) / 6.28318;
            float phase = angle * 1.6 + u * 0.9 + facing * 0.25 + time * 0.05;
            // rainbow 0 이면 흰 림만, 1 이면 분광 림.
            half3 irid = mix(half3(1.0), spectrum(phase), half(rainbow));
            float rim = (1.0 - smoothstep(0.0, 3.0, -d)) * (0.45 + 0.55 * lit);
            float band = bend * bend * (0.35 + 0.65 * lit);
            // 광택 띠 — 광원에 수직으로 가로지르는 넓은 하이라이트가 천천히 지나갑니다.
            float2 perp = float2(-l.y, l.x);
            float across = dot(p - c, perp) / max(halfSize.x, 1.0);
            float sweep = sin(time * 0.5) * 0.9;
            float sheen = exp(-pow((across - sweep) * 2.6, 2.0)) * (1.0 - bend);

            // 하이라이트(림·경사 반사·광택)를 흰 레이어로 위에 합성하고, 그 위에 옅은 틴트 레이어 — source-over.
            half hl = half(clamp(rim * 0.6 + band * 0.18 + bevelLight * 0.18 + sheen * 0.08 + 0.02, 0.0, 1.0));
            half4 outc = src * (1.0 - hl) + half4(hl, hl, hl, hl);
            outc = outc * (1.0 - half(tintAlpha)) + half4(half3(tint) * half(tintAlpha), half(tintAlpha));
            outc.rgb -= half3(shade * 0.08) * outc.a;
            return clamp(outc, 0.0, 1.0) * half(aa);
        }
    """

    fun create(): RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(AGSL) }.getOrNull() else null
}

/** 캡슐 반사광의 광원 — 화면 좌표계 방향(x 오른쪽, y 아래)과 광택 띠용 시간. */
class LiquidLight(x: Float, y: Float) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var time by mutableFloatStateOf(0f)
}

/**
 * 중력 센서로 광원 방향을 실시간으로 정합니다: 폰을 왼쪽으로 기울이면 빛이 왼쪽 위에서 오는 것처럼 하이라이트가
 * 옮겨 갑니다. 화면이 보일 때만(RESUMED) 센서를 켭니다. 값은 저역 통과로 부드럽게 따라갑니다.
 */
@Composable
fun rememberLiquidLight(enabled: Boolean): LiquidLight {
    val light = remember { LiquidLight(-0.55f, -0.83f) }
    if (!enabled) return light
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // 광택 띠가 흐르도록 프레임마다 시간을 올립니다.
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> light.time = (now - start) / 1_000_000_000f }
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = manager?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                // 기기 x: 오른쪽이 +, y: 위가 +. 화면 y 는 아래가 + 라 뒤집습니다. 기본(똑바로 세움)은 왼쪽 위 광원.
                val gx = event.values[0] / 9.81f
                val gy = event.values[1] / 9.81f
                val targetX = (-0.55f - gx * 0.9f).coerceIn(-1f, 1f)
                val targetY = (-0.83f + (gy - 0.6f) * 0.6f).coerceIn(-1f, 0.2f)
                light.x += (targetX - light.x) * 0.12f
                light.y += (targetY - light.y) * 0.12f
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME ->
                    if (sensor != null) manager?.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> manager?.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager?.unregisterListener(listener)
        }
    }
    return light
}

/** 앱 전체가 함께 쓰는 광원 — 루트에서 한 번만 센서를 켜고, 하단 바·헤더 섬이 같은 빛을 받습니다. */
val LocalLiquidLight = compositionLocalOf { LiquidLight(-0.55f, -0.83f) }

/** 액체 유리 바탕 — 뒤 화면을 26dp 로 흐린 서리 유리 + 옅은 틴트. 하단 바와 헤더 섬이 같이 씁니다. */
fun liquidFrostStyle(isDark: Boolean): HazeStyle = HazeStyle(
    backgroundColor = if (isDark) Color(0xFF101214) else Color(0xFFF4F5F7),
    tints = listOf(HazeTint(if (isDark) Color(0xFF16181B).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f))),
    blurRadius = 26.dp,
    noiseFactor = 0.04f,
)

/**
 * 섬(헤더·버튼 알약·팝업)용 유리 — 하단 바처럼 26dp 로 흐리면 굴절이 보이지 않아, 뒤 화면이 살짝만 흐린 채(6dp)
 * 렌즈에 굴절되게 합니다. 글자가 읽히도록 틴트는 조금 더 진하게.
 */
fun liquidLensStyle(isDark: Boolean): HazeStyle = HazeStyle(
    backgroundColor = if (isDark) Color(0xFF101214) else Color(0xFFF4F5F7),
    // 틴트는 하단 바 캡슐 속과 같은 옅기 — 그 정도 투명함이 보기 좋았음.
    tints = listOf(HazeTint(Color.White.copy(alpha = if (isDark) 0.03f else 0.10f))),
    blurRadius = 4.dp,
    noiseFactor = 0.0f,
)

/**
 * 액체 유리 섬 — 뒤 화면을 서리 유리로 흐리고, 그 위에 렌즈 셰이더(두꺼운 유리 가장자리 굴절 + 무지개 림 + 기울기 반사광)를
 * 얹은 둥근 상자. 글자 등 [content] 는 유리 위에 또렷하게 올라갑니다. 셰이더를 못 쓰는 기기(Android 12 이하)나 haze 가
 * 없으면 보통의 글래스/반투명 알약으로 대체됩니다. [alpha] 0 이면 유리를 그리지 않습니다.
 */
@Composable
fun OneUiLiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    alpha: Float = 1f,
    strength: Float = 1f,
    contentAlignment: Alignment = Alignment.Center,
    /**
     * null 이면 뒤 화면을 haze 로 가져와 굴절합니다(섬·팝업처럼 콘텐츠 소스 밖에 있을 때). 색을 주면 뒤 화면 대신
     * 그 색 판 위에 렌즈(림·반사광)만 얹습니다 — 본문 안의 칩처럼 haze 소스 안에 있어 뒤를 잡을 수 없는 곳용.
     */
    fill: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    val shader = remember { LiquidLens.create() }
    val light = LocalLiquidLight.current
    val isDark = MaterialTheme.colorScheme.isDark
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier, contentAlignment = contentAlignment) {
        if (alpha > 0f) {
            if (fill != null && shader != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            this.alpha = alpha
                            shader.setFloatUniform("rect", 0f, 0f, size.width, size.height)
                            shader.setFloatUniform("radius", radiusPx)
                            shader.setFloatUniform("strength", strength)
                            shader.setFloatUniform("lightDir", light.x, light.y)
                            shader.setFloatUniform("time", light.time)
                            shader.setFloatUniform("tint", 1f, 1f, 1f)
                            shader.setFloatUniform("tintAlpha", 0f)
                            shader.setFloatUniform("dir", -1f)
                            shader.setFloatUniform("rainbow", 0f)
                            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                        }
                        .background(fill),
                )
            } else if (fill != null) {
                Box(Modifier.matchParentSize().clip(shape).background(fill))
            } else if (hazeState != null && shader != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            this.alpha = alpha
                            shader.setFloatUniform("rect", 0f, 0f, size.width, size.height)
                            shader.setFloatUniform("radius", radiusPx)
                            shader.setFloatUniform("strength", strength)
                            shader.setFloatUniform("lightDir", light.x, light.y)
                            shader.setFloatUniform("time", light.time)
                            shader.setFloatUniform("tint", 1f, 1f, 1f)
                            shader.setFloatUniform("tintAlpha", if (isDark) 0.03f else 0.08f)
                            shader.setFloatUniform("dir", -1f)
                            shader.setFloatUniform("rainbow", 0f)
                            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                        }
                        .hazeEffect(hazeState, liquidLensStyle(isDark)) { inputScale = HazeInputScale.None },
                )
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .oneUiGlassSurface(shape, alpha = alpha, container = MaterialTheme.colorScheme.floatingPill),
                )
            }
        }
        content()
    }
}
