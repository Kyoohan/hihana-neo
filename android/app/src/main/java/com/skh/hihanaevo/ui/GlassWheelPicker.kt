package com.skh.hihanaevo.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs

/** 휠 한 칸 높이와 보이는 칸 수 — 가운데 칸이 선택, 위아래 두 칸씩은 드럼처럼 기울며 흐려집니다. */
private val WheelItemHeight = 44.dp
private const val WheelVisible = 5

/**
 * 톱니바퀴(드럼) 휠 한 줄 — 스크롤을 놓으면 가장 가까운 칸에 맞춰 멈추고, 칸이 바뀔 때마다 짧게 진동합니다.
 * 칸마다 가운데에서 떨어진 만큼 X축으로 기울이고(원통 표면처럼) 작게·흐리게 그립니다. 선택 띠는 [GlassWheelFrame] 이 위에 얹습니다.
 */
@Composable
fun GlassWheel(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val state: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val itemPx = with(density) { WheelItemHeight.toPx() }
    val latestSelect by rememberUpdatedState(onSelect)

    // 가운데에 가장 가까운 칸 — 뷰포트 가운데와 칸 가운데의 거리로 고릅니다.
    val centered by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: state.firstVisibleItemIndex
        }
    }
    // 코드가 굴리는 중(바깥 값에 맞추기)엔 지나가는 칸을 선택으로 보내지 않습니다 — 년이 바뀌어 월 목록이 달라질 때
    // 지나가는 칸마다 값이 바뀌던 문제.
    var programmatic by remember { mutableStateOf(false) }
    suspend fun go(target: Int) {
        programmatic = true
        try {
            state.animateScrollToItem(target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 맞추는 중에 사람이 다시 굴리면 이 애니메이션만 취소됩니다 — 그 취소가 멈춤을 지켜보는 코루틴까지 끝내
            // 이후 선택이 반영되지 않던 문제. 코루틴 자체가 끝나는 경우만 그대로 던집니다.
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw e
        } finally {
            programmatic = false
        }
    }
    val latestSelected by rememberUpdatedState(selectedIndex)
    val latestCount by rememberUpdatedState(items.size)

    // 사람이 굴리는 동안엔 가운데 칸이 바뀔 때마다 짧게 진동만 합니다 (처음 값은 건너뜀).
    LaunchedEffect(state) {
        snapshotFlow { centered }.drop(1).distinctUntilChanged().collect {
            if (!programmatic && state.isScrollInProgress) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    // 선택은 멈췄을 때 한 번만 알립니다 — 칸마다 알리면 대화상자 전체가 칸마다 다시 구성돼 스크롤이 버벅였습니다.
    // 알린 뒤 바깥이 값을 반영할 틈을 조금 두고, 스냅이 덜 맞았거나 바깥이 값을 바꿨으면 그 칸으로 맞춥니다.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.drop(1).distinctUntilChanged().collect { scrolling ->
            if (scrolling || programmatic) return@collect
            val index = centered
            if (index in 0 until latestCount && index != latestSelected) latestSelect(index)
            kotlinx.coroutines.delay(60)
            val target = latestSelected.coerceIn(0, (latestCount - 1).coerceAtLeast(0))
            if (centered != target || state.firstVisibleItemScrollOffset != 0) go(target)
        }
    }
    // 바깥에서 값·목록이 바뀌면(년이 바뀌어 월 목록이 달라짐 등) 그 칸으로.
    LaunchedEffect(selectedIndex, items) {
        val target = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (!state.isScrollInProgress && centered != target) go(target)
    }

    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = WheelItemHeight * (WheelVisible / 2)),
        modifier = modifier
            .height(WheelItemHeight * WheelVisible)
            // 위아래 끝으로 갈수록 투명하게 — 드럼이 안쪽으로 말려 들어가는 그늘.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.3f to Color.Black,
                        0.7f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        itemsIndexed(items) { index, label ->
            // 가운데 칸 강조는 그리기 단계(투명도·크기)로만 — 칸이 바뀔 때 글자를 다시 배치하지 않게.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .graphicsLayer {
                        val info = state.layoutInfo
                        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        val distance = if (item == null) 2.5f else (item.offset + item.size / 2f - center) / itemPx
                        val d = distance.coerceIn(-2.6f, 2.6f)
                        rotationX = -d * 24f
                        cameraDistance = 6f * density.density * 72f
                        val shrink = 1f - abs(d) * 0.07f
                        scaleX = shrink
                        scaleY = shrink
                        // 가운데에서 반 칸만 벗어나도 확 옅어져, 선택 칸만 또렷합니다.
                        alpha = (1f - abs(d).coerceAtMost(1f) * 0.5f - (abs(d) - 1f).coerceAtLeast(0f) * 0.25f).coerceIn(0.12f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    textAlign = textAlign,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/**
 * 휠 전용 유리 렌즈 (AGSL, Android 13+) — 휠 숫자 레이어 전체에 걸지만 선택 띠의 얇은 가장자리만 바꿉니다.
 * 띠 가운데는 원래 픽셀을 그대로 써서(확대·재샘플링 없음) 선택된 숫자가 다른 칸과 같은 자리·같은 선명도로 보이고,
 * 둥글게 깎인 가장자리 테(약 6dp)에서만 바깥 칸의 숫자를 끌어와 휘게 합니다 — 굴릴 때 숫자가 띠 경계를 지나며 구부러집니다.
 * 색 분산은 없앴습니다(밝은 테두리에 노란 띠가 생겼음). 하이라이트는 흰 림과 광원 쪽 경사뿐, 시간·센서 없이 정적입니다.
 */
private object WheelLens {
    private const val AGSL = """
        uniform shader content;
        uniform float4 rect;
        uniform float radius;
        uniform float2 lightDir;

        float sdRoundRect(float2 p, float2 c, float2 halfSize, float r) {
            float2 q = abs(p - c) - (halfSize - r);
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 p) {
            half4 outside = content.eval(p);
            float2 c = (rect.xy + rect.zw) * 0.5;
            float2 halfSize = (rect.zw - rect.xy) * 0.5;
            float d = sdRoundRect(p, c, halfSize, radius);
            if (d > 1.0) return outside;

            // 굴절 테 폭 — 띠 높이의 약 1/7. 그 안쪽은 평평해 원래 픽셀 그대로입니다.
            float bevelW = min(halfSize.x, halfSize.y) * 0.28;
            if (d < -bevelW) return outside;
            float u = clamp(1.0 + d / bevelW, 0.0, 1.0);   // 0 = 평평한 안쪽, 1 = 림
            float bend = 1.0 - sqrt(max(1.0 - u * u, 0.0));

            float eps = 1.0;
            float2 grad = float2(
                sdRoundRect(p + float2(eps, 0.0), c, halfSize, radius) - sdRoundRect(p - float2(eps, 0.0), c, halfSize, radius),
                sdRoundRect(p + float2(0.0, eps), c, halfSize, radius) - sdRoundRect(p - float2(0.0, eps), c, halfSize, radius)
            );
            float2 n = normalize(grad + float2(0.0001, 0.0));

            // 가장자리에서 바깥 칸의 숫자를 끌어오는 굴절 (확대·색 분산 없음).
            half4 src = content.eval(p + n * bend * bevelW * 1.8);

            float2 l = normalize(lightDir);
            float lit = clamp(dot(n, l), 0.0, 1.0);
            float rim = (1.0 - smoothstep(0.0, 1.5, -d)) * (0.3 + 0.7 * lit);
            float spec = pow(lit, 2.0) * bend * 0.15;
            half hl = half(clamp(rim * 0.4 + spec, 0.0, 1.0));
            half4 inside = src * (1.0 - hl) + half4(hl, hl, hl, hl);

            float aa = 1.0 - smoothstep(-0.75, 0.75, d);
            return mix(outside, inside, half(aa));
        }
    """

    fun create(): RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(AGSL) }.getOrNull() else null
}

/**
 * 휠 여러 줄을 담는 틀 — 정적인 옅은 유리 판(애니메이션 없음) 위에 휠을 놓고, 가운데 선택 띠는 [WheelLens] 로
 * 휠 숫자를 직접 굴절시킵니다. 셰이더를 못 쓰는 기기(Android 12 이하)는 테두리만 있는 띠로 대신합니다.
 */
@Composable
fun GlassWheelFrame(modifier: Modifier = Modifier, wheels: @Composable RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val lens = remember { WheelLens.create() }
    val bandHeight = WheelItemHeight + 4.dp
    val bandShape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // 바닥 판 — 시간·기울기에 따라 움직이지 않는 정적인 유리 색.
        Box(
            Modifier
                .fillMaxWidth()
                .height(WheelItemHeight * WheelVisible)
                .clip(RoundedCornerShape(22.dp))
                .background(scheme.onSurface.copy(alpha = if (scheme.isDark) 0.05f else 0.035f)),
        )
        // 선택 띠의 바탕색 — 숫자 아래에 깔립니다.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(bandHeight)
                .clip(bandShape)
                .background(scheme.onSurface.copy(alpha = if (scheme.isDark) 0.08f else 0.06f))
                .then(if (lens == null) Modifier.border(1.dp, scheme.onSurface.copy(alpha = 0.12f), bandShape) else Modifier),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    if (lens != null) {
                        val inset = with(density) { 4.dp.toPx() }
                        val half = with(density) { bandHeight.toPx() } / 2f
                        val cy = size.height / 2f
                        lens.setFloatUniform("rect", inset, cy - half, size.width - inset, cy + half)
                        lens.setFloatUniform("radius", with(density) { 16.dp.toPx() })
                        lens.setFloatUniform("lightDir", -0.35f, -1f)
                        renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(lens, "content").asComposeRenderEffect()
                    }
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { wheels() }
    }
}

// MARK: - 날짜·시각 다이얼로그

/**
 * 날짜 고르기 — 년·월·일 휠. 휠에는 [min]~[max] 안의 값만 나옵니다(오늘 이전 날·1년 뒤 이후는 아예 없음) —
 * 범위 밖을 골랐다가 되돌아오는 일이 없게. 달이 바뀌어 날이 넘치면 그 달 마지막 날로.
 */
@Composable
fun GlassDatePickerDialog(
    title: String,
    initial: LocalDate,
    min: LocalDate,
    max: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(initial.coerceIn(min, max)) }
    val years = remember(min, max) { (min.year..max.year).toList() }
    val months = remember(date.year, min, max) {
        val from = if (date.year == min.year) min.monthValue else 1
        val to = if (date.year == max.year) max.monthValue else 12
        (from..to).toList()
    }
    val days = remember(date.year, date.monthValue, min, max) {
        val ym = YearMonth.of(date.year, date.monthValue)
        val from = if (ym == YearMonth.from(min)) min.dayOfMonth else 1
        val to = if (ym == YearMonth.from(max)) max.dayOfMonth else ym.lengthOfMonth()
        (from..to).toList()
    }
    val yearLabels = remember(years) { years.map { "${it}년" } }
    val monthLabels = remember(months) { months.map { "${it}월" } }
    val dayLabels = remember(days) { days.map { "${it}일" } }
    fun set(year: Int = date.year, month: Int = date.monthValue, day: Int = date.dayOfMonth) {
        val first = LocalDate.of(year, month, 1)
        date = first.withDayOfMonth(day.coerceAtMost(first.lengthOfMonth())).coerceIn(min, max)
    }
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = listOf(
            OneUiDialogButton("취소", onDismiss),
            OneUiDialogButton("확인", { onPick(date) }),
        ),
    ) {
        // 고른 날짜는 휠 그대로라 위에 따로 적지 않습니다 (요일은 고른 뒤 양식의 날짜 버튼에 나옵니다).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassWheelFrame {
                GlassWheel(yearLabels, years.indexOf(date.year), { years.getOrNull(it)?.let { y -> set(year = y) } }, Modifier.weight(1.3f))
                GlassWheel(monthLabels, months.indexOf(date.monthValue), { months.getOrNull(it)?.let { m -> set(month = m) } }, Modifier.weight(1f))
                GlassWheel(dayLabels, days.indexOf(date.dayOfMonth), { days.getOrNull(it)?.let { d -> set(day = d) } }, Modifier.weight(1f))
            }
        }
    }
}

/** 시각 고르기 — 시·분 휠 (24시간). */
@Composable
fun GlassTimePickerDialog(
    title: String,
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = listOf(
            OneUiDialogButton("취소", onDismiss),
            OneUiDialogButton("확인", { onPick(LocalTime.of(hour, minute)) }),
        ),
    ) {
        // 시각은 휠에 보이는 그대로라 위에 따로 적지 않습니다 (날짜는 휠에 없는 요일 때문에 적어 둡니다).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassWheelFrame {
                GlassWheel(HourLabels, hour, { hour = it }, Modifier.weight(1f))
                Text(":", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(12.dp), textAlign = TextAlign.Center)
                GlassWheel(MinuteLabels, minute, { minute = it }, Modifier.weight(1f))
            }
        }
    }
}

private fun LocalDate.coerceIn(min: LocalDate, max: LocalDate): LocalDate = when {
    isBefore(min) -> min
    isAfter(max) -> max
    else -> this
}

private val HourLabels = (0..23).map { "%02d".format(it) }
private val MinuteLabels = (0..59).map { "%02d".format(it) }
