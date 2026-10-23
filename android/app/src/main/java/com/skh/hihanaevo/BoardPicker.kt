package com.skh.hihanaevo

import android.graphics.RenderEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skh.hihanaevo.ui.LiquidLens
import com.skh.hihanaevo.ui.LocalLiquidLight
import com.skh.hihanaevo.ui.PerformanceMode
import com.skh.hihanaevo.ui.FrostOpacity
import com.skh.hihanaevo.ui.isDark
import com.skh.hihanaevo.ui.oneUiFrostedBackdrop
import com.skh.hihanaevo.ui.cappedFontScale
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

/** 세그먼트 칸에 들어갈 두 글자 줄임말. */
internal val BoardCategory.shortLabel: String
    get() = when (this) {
        BoardCategory.STUDENT_NOTICE -> "학생"
        BoardCategory.SUBJECT_NOTICE -> "교과"
        BoardCategory.HOME_LETTER -> "가정"
        BoardCategory.WOOJEONG -> "우정"
        BoardCategory.STUDENT_BOARD -> "게시판"
    }

@Composable
private fun BoardIcon(category: BoardCategory, tint: Color, size: Dp) {
    val m = Modifier.size(size)
    when (category) {
        BoardCategory.STUDENT_NOTICE -> Icon(Icons.Outlined.Notifications, contentDescription = null, tint = tint, modifier = m)
        BoardCategory.SUBJECT_NOTICE -> Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null, tint = tint, modifier = m)
        BoardCategory.HOME_LETTER -> Icon(Icons.Default.Email, contentDescription = null, tint = tint, modifier = m)
        BoardCategory.WOOJEONG -> Icon(painterResource(R.drawable.ic_hotel), contentDescription = null, tint = tint, modifier = m)
        BoardCategory.STUDENT_BOARD -> Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null, tint = tint, modifier = m)
    }
}

private val SegmentBarHeight = 64.dp

/**
 * 게시판 다섯 칸 세그먼트 — 하단 내비게이션 바와 같은 액체 유리: 바는 뒤 배경을 크게 흐린 서리 유리, 고른 칸의 캡슐은 살짝만
 * 흐린 뒤 배경을 AGSL 렌즈로 굴절해 그리고, 칸을 바꾸면 캡슐이 스프링으로 미끄러집니다. 흐림 소스는 페이지 배경
 * (LocalMaterialHazeState) — 바는 목록 맨 위 항목이라 본문과 겹칠 일이 없고, 본문 소스를 쓰면 자기 자신을 흐리는 재귀가 됩니다.
 * 칸마다 안 읽은 글 수를 빨간 배지로 — 고른 칸에도 그대로 둡니다.
 */
@Composable
internal fun BoardSegmentBar(
    selected: Int,
    unreadCounts: Map<BoardCategory, Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.isDark
    val density = LocalDensity.current
    val categories = BoardCategory.entries
    val count = categories.size

    val lensShader = remember { LiquidLens.create() }
    val liquidEnabled = lensShader != null && !PerformanceMode.enabled
    // 바와 캡슐의 서리는 카드와 같이 미리 흐린 배경을 자기 위치만큼 옮겨 그립니다 — Haze 로 하면 목록을 벗어났다 돌아와
    // 다시 붙을 때 정적인 배경 소스를 다시 잡지 못해 바가 통째로 투명해졌습니다.
    // 덮개는 하단 바보다 짙게 — 하단 바는 어두운 카드 위를 흐리지만 이 바는 사진을 바로 흐려서, 0.55 로는 분홍 구름 위에서
    // 회색 아이콘·글자가 갈색으로 묻혔습니다.
    // 라이트는 0.80 이면 흰 판처럼 보여 조금 낮추고, 대신 안 고른 칸의 회색을 한 단계 진하게.
    // 설정의 서리 불투명도(기본 0.5)에 따라 — 기본에서 다크 0.80 / 라이트 0.66.
    val barTint = if (isDark) Color(0xFF16181B).copy(alpha = (FrostOpacity.alpha + 0.30f).coerceAtMost(0.95f))
    else Color.White.copy(alpha = (FrostOpacity.alpha + 0.16f).coerceAtMost(0.95f))
    val barFrost = Modifier.oneUiFrostedBackdrop(barTint)
    // 캡슐은 바보다 한 단계 밝게 — 하단 바처럼 바와 같은 판 위에 흰빛을 얹습니다. 예전엔 어두운 테마에서 흰 0.06 만 덮어
    // 사진이 거의 그대로 비쳐, 짙은 바에 구멍이 뚫린 것처럼 보였습니다. 라이트는 바(흰 0.66)보다 더 흰 판.
    val capsuleFrost = if (isDark) Modifier.oneUiFrostedBackdrop(barTint).background(Color.White.copy(alpha = 0.15f))
    else Modifier.oneUiFrostedBackdrop(Color.White.copy(alpha = 0.78f))
    val light = LocalLiquidLight.current
    val barShape = RoundedCornerShape(26.dp)
    val capsuleShape = RoundedCornerShape(20.dp)
    val barColor = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    val selectedCapsuleColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f)
    val activeTint = if (isDark) Color.White else Color(0xFF1A1A1C)
    val inactiveTint = if (isDark) Color(0xFFA3A3AD) else Color(0xFF6F6F75)

    // 캡슐은 하단 바처럼 잡아 끌 수도 있습니다 — dragIndex 는 끄는 동안의 실수 위치(칸 단위), 놓으면 가장 가까운 칸으로 스냅.
    var dragIndex by remember { mutableStateOf<Float?>(null) }
    var pendingIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected) {
        if (pendingIndex != null && pendingIndex == selected) {
            pendingIndex = null
            dragIndex = null
        }
    }
    val dragging = dragIndex != null && pendingIndex == null
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // 캡슐 위치(칸 단위) — 끌 때는 빠른 스프링으로 손가락을, 탭하면 보통 스프링으로 고른 칸을 따라갑니다.
    val capsule = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(Unit) {
        var job: Job? = null
        snapshotFlow { Triple(dragIndex ?: currentSelected.toFloat(), dragIndex != null && pendingIndex == null, dragIndex != null) }
            .collect { (target, direct, released) ->
                job?.cancel()
                // 끄는 동안은 값만 옮기고(프레임마다 스프링을 다시 시작하면 첫 프레임이 비어 제자리에 섰습니다), 놓은 뒤만 스프링.
                if (direct) capsule.snapTo(target)
                else job = launch {
                    capsule.animateTo(target, if (released) spring(dampingRatio = 1f, stiffness = 1600f) else spring(dampingRatio = 0.78f, stiffness = 320f))
                }
            }
    }
    // 끌거나 미끄러지는 동안만 렌즈 굴절, 끄는 동안은 물방울처럼 살짝 늘어남.
    val liquid by animateFloatAsState(if (dragging || capsule.isRunning) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 220f), label = "segmentLiquid")
    val stretch by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 220f), label = "segmentStretch")
    val stretchX = 1f + 0.05f * stretch
    val squashY = 1f - 0.03f * stretch

    Box(
        modifier
            .fillMaxWidth()
            .height(SegmentBarHeight)
            .clip(barShape)
            .then(if (PerformanceMode.enabled) Modifier.background(barColor.copy(alpha = 0.96f)) else barFrost)
            .drawWithContent {
                drawContent()
                // 위쪽이 밝은 얇은 유리 테두리 — 하단 바와 같은 결.
                val strokePx = 1.dp.toPx()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.22f else 0.95f),
                            Color.White.copy(alpha = if (isDark) 0.04f else 0.3f),
                        ),
                    ),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                    style = Stroke(strokePx),
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(size.width - strokePx, size.height - strokePx),
                )
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val cellWidth = maxWidth / count
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val capsuleRadiusPx = with(density) { 20.dp.toPx() }
            val lensMargin = if (liquidEnabled) 30.dp else 0.dp
            val lensMarginPx = with(density) { lensMargin.toPx() }
            val itemsW = with(density) { maxWidth.roundToPx() }
            val itemsH = with(density) { maxHeight.roundToPx() }

            // 칸 다섯 — 터치 받는 실제 Row 와 캡슐 속 복사본이 같은 내용을 씁니다.
            val items: @Composable RowScope.(clickable: Boolean) -> Unit = { clickableItems ->
                categories.forEachIndexed { index, category ->
                    val on = index == selected
                    SegmentCell(
                        category = category,
                        selected = on,
                        unread = unreadCounts[category] ?: 0,
                        tint = if (on) activeTint else inactiveTint,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(capsuleShape)
                            .then(
                                if (clickableItems) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelect(index) },
                                ) else Modifier,
                            ),
                    )
                }
            }

            val capsuleLayer: @Composable () -> Unit = {
                if (liquidEnabled) {
                    Box(
                        Modifier
                            .offset { IntOffset((capsule.value * cellWidthPx).roundToInt(), 0) }
                            .width(cellWidth)
                            .fillMaxHeight()
                            .graphicsLayer { scaleX = stretchX; scaleY = squashY; clip = true; shape = capsuleShape }
                            .then(capsuleFrost),
                    )
                }
                Box(
                    Modifier
                        .offset { IntOffset((capsule.value * cellWidthPx - lensMarginPx).roundToInt(), 0) }
                        .requiredWidth(cellWidth + lensMargin * 2)
                        .requiredHeight(maxHeight + lensMargin * 2)
                        .graphicsLayer {
                            scaleX = stretchX
                            scaleY = squashY
                            if (!liquidEnabled) {
                                clip = true
                                shape = capsuleShape
                            }
                            if (liquidEnabled && lensShader != null) {
                                lensShader.setFloatUniform("rect", lensMarginPx, lensMarginPx, size.width - lensMarginPx, size.height - lensMarginPx)
                                lensShader.setFloatUniform("radius", capsuleRadiusPx)
                                lensShader.setFloatUniform("strength", liquid)
                                lensShader.setFloatUniform("lightDir", light.x, light.y)
                                lensShader.setFloatUniform("time", light.time)
                                lensShader.setFloatUniform("tint", 1f, 1f, 1f)
                                lensShader.setFloatUniform("tintAlpha", if (isDark) 0.03f else 0.08f)
                                lensShader.setFloatUniform("dir", 1f)
                                lensShader.setFloatUniform("rainbow", 0f)
                                renderEffect = RenderEffect.createRuntimeShaderEffect(lensShader, "content").asComposeRenderEffect()
                            }
                        }
                        .then(if (liquidEnabled) Modifier else Modifier.background(selectedCapsuleColor)),
                ) {
                    if (liquidEnabled) {
                        // 캡슐 속 복사본 — 아래 Row 와 같은 자리에 겹쳐 렌즈가 굴절합니다.
                        Box(
                            Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(Constraints.fixed(itemsW, itemsH))
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place((lensMarginPx - capsule.value * cellWidthPx).roundToInt(), lensMarginPx.roundToInt())
                                }
                            },
                        ) {
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { items(false) }
                        }
                    }
                }
            }
            if (!liquidEnabled) capsuleLayer()
            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(count) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragIndex = currentSelected.toFloat() },
                            onDragEnd = {
                                val target = dragIndex?.roundToInt()?.coerceIn(0, count - 1)
                                if (target == null || target == currentSelected) {
                                    dragIndex = null
                                } else {
                                    // selected 가 바뀔 때까지 놓은 칸에 붙잡아 둡니다 — 바로 null 로 두면 한 프레임 되돌아가 떨립니다.
                                    dragIndex = target.toFloat()
                                    pendingIndex = target
                                    currentOnSelect(target)
                                }
                            },
                            onDragCancel = { dragIndex = null },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                dragIndex = ((dragIndex ?: currentSelected.toFloat()) + dx / cellWidthPx).coerceIn(0f, (count - 1).toFloat())
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) { items(true) }
            if (liquidEnabled) capsuleLayer()
        }
    }
}

@Composable
private fun SegmentCell(category: BoardCategory, selected: Boolean, unread: Int, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box {
            BoardIcon(category, tint, 22.dp)
            if (unread > 0) UnreadBadge(unread, Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-6).dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            category.shortLabel,
            style = MaterialTheme.typography.labelSmall.cappedFontScale(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
        )
    }
}

/** 안 읽은 글 수 — 빨간 알약 안 흰 숫자 (One UI 앱 아이콘 배지와 같은 꼴). */
@Composable
internal fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Text(
        if (count > 99) "99+" else count.toString(),
        style = TextStyle(fontSize = 10.sp, lineHeight = 10.sp).cappedFontScale(),
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFFE53935))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
