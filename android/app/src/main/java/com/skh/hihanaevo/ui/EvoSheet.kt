package com.skh.hihanaevo.ui

import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 새 화면 틀(세 칸 하단 바)을 쓰는지 — 켜져 있으면 [OneUiFullScreen] 이 예전 툴바·사진 배경 대신 [EvoSheet] 로 그려집니다.
 * MainActivity 가 하단 바 설정에 맞춰 바꿉니다.
 */
object EvoChrome {
    var enabled by mutableStateOf(false)

    /** 쓸어내리기·닫기로 시트를 닫을 때마다 하나씩 — 아래 깔린 시트도 함께 닫혀 홈·타임라인으로 돌아갑니다(뒤로 가기는 한 장씩). */
    var dismissAll by androidx.compose.runtime.mutableIntStateOf(0)

    /** 떠 있는 시트(아래 → 위). 위 시트가 다 밀려 들어와 덮고 있으면 아래 시트는 그리지 않습니다. */
    internal val stack = androidx.compose.runtime.mutableStateListOf<SheetEntry>()
}

internal class SheetEntry {
    /** 다 밀려 들어와 아래 시트를 가리고 있는지 — 들어오는 중·뒤로 가며 빠지는 중엔 아래 시트가 보여야 합니다. */
    var covering by mutableStateOf(false)

    /** 옆에서 밀려 들어온 정도(0 = 밖, 1 = 다 들어옴) — 아래 시트가 이만큼 왼쪽으로 살짝 밀려나 한 단계 들어간 게 보입니다. */
    var enter by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/** 시트가 안쪽에 물려주는 흐린 배경 사진 — 시트 안에서 여는 시트용([EvoSheet]). */
private val LocalSheetPhoto = androidx.compose.runtime.compositionLocalOf<androidx.compose.ui.graphics.ImageBitmap?> { null }

/**
 * 홈·타임라인 위로 올라오는 시트 — ⊞ 모든 기능에서 고른 화면(신청·급식·게시판·클래스룸·시간표·학사일정)과 설정·알리미가
 * 여기 뜹니다. 세 칸 하단 바에선 예전 탭 화면의 틀(접히는 헤더·버튼 섬·탭 페이저·하단 바)을 그리지 않고, 화면 내용만 담습니다.
 *
 * - 바탕은 흐린 배경 사진 위에 테마 면 색을 덮은 판(사진이 없으면 기본 그라데이션), 카드·글자는 테마색.
 * - 머리: 손잡이, 제목, 그 화면의 버튼([actions]), 닫기. 머리를 끌어내리면 손가락을 따라 내려가고, 1/4 넘게 내리거나
 *   빠르게 내리면 닫힙니다(아니면 제자리로). 뒤로 가기·닫기도 같은 내려가는 움직임으로 닫습니다.
 * - 내용이 맨 위까지 올라가 있을 때 더 끌어내리면 머리를 끈 것과 같이 시트가 내려갑니다(아래 화면 — 홈·타임라인으로).
 * - 열릴 땐 아래에서 스프링으로 올라옵니다. 이미 시트가 떠 있으면(모든 기능에서 고른 화면, 게시판에서 연 글) 같은 자리에서
 *   오른쪽에서 밀려 들어오고, 뒤로 가면 오른쪽으로 빠지며 아래 화면이 드러납니다. 쓸어내리기·닫기는 쌓인 시트를 모두 닫습니다.
 */
@Composable
fun EvoSheet(
    title: String,
    onClosed: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    /**
     * 홈처럼 배경 사진을 그대로(기본) — 사진은 창에 고정해 그리고(시트가 움직여도 홈과 같은 자리), 카드는 서리 유리, 사진 위
     * 글자는 사진 밝기 색. false 면 흐린 사진에 면 색을 덮은 판.
     */
    photo: Boolean = true,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    val scope = rememberCoroutineScope()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val entry = remember { SheetEntry() }
    // 다른 시트 위에 열렸는지 — 그러면 아래에서 올라오지 않고 옆에서 밀려 들어옵니다.
    val pushed = remember { EvoChrome.stack.isNotEmpty() }
    androidx.compose.runtime.DisposableEffect(entry) {
        EvoChrome.stack.add(entry)
        onDispose { EvoChrome.stack.remove(entry) }
    }
    val hidden by remember {
        androidx.compose.runtime.derivedStateOf {
            val i = EvoChrome.stack.indexOf(entry)
            i >= 0 && EvoChrome.stack.getOrNull(i + 1)?.covering == true
        }
    }
    // 위 시트에 다 가려지면 그리지 않습니다(구성은 그대로라 스크롤 자리는 남음) — 위 시트를 끌어내리면 홈·타임라인이 드러납니다.
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = if (hidden) 0f else 1f }) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        // 옆으로 밀린 거리(px) — 다른 시트 위에 열린 시트만 씁니다.
        var slide by remember { androidx.compose.runtime.mutableFloatStateOf(if (pushed) widthPx else 0f) }
        var sliding by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        // 시트가 내려간 거리(px) — 0 이면 다 올라온 것. 끌 땐 바로 쓰고 움직임은 한 번에 하나(⊞ 모든 기능 화면을 쓸어 올리던 예전 서랍과 같은 이유:
        // 끌기 갱신을 코루틴으로 띄우면 늦게 도착한 것이 닫히는 움직임을 끊어 시트가 중간에 멈췄습니다).
        var offset by remember { androidx.compose.runtime.mutableFloatStateOf(if (pushed) 0f else heightPx * 0.35f) }
        var moving by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val spring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.88f, stiffness = 380f)
        var closing by remember { mutableStateOf(false) }
        fun moveTo(to: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>, velocity: Float = 0f, then: () -> Unit = {}) {
            moving?.cancel()
            moving = scope.launch {
                androidx.compose.animation.core.animate(offset, to, velocity, spec) { v, _ -> offset = v }
                then()
            }
        }
        LaunchedEffect(Unit) {
            if (!pushed) moveTo(0f, spring)
            else sliding = scope.launch {
                // 화면 밖에서 한 번 다 그린 뒤에 들어옵니다 — 첫 그리기가 무거운 화면(급식·게시판)은 첫 프레임들이 밀려 거의 다
                // 들어온 채 불쑥 나타났다가 왼쪽으로 붙어, 왼쪽에서 튀어나오는 것처럼 보였습니다.
                // 기다리는 동안 오른쪽 끝에 1px 만 걸쳐 둡니다 — 화면 밖이면 실제로 그리지 않아, 처음 보이는 프레임에서 사진·글자를
                // 한꺼번에 그리느라 멈췄습니다. 프레임이 고르게 돌 때까지(연달아 두 번 24ms 안) 기다립니다 — 최대 15프레임.
                slide = widthPx - 1f
                var prev = androidx.compose.runtime.withFrameNanos { it }
                var calm = 0
                for (k in 0 until 15) {
                    val t = androidx.compose.runtime.withFrameNanos { it }
                    calm = if (t - prev < 24_000_000L) calm + 1 else 0
                    prev = t
                    if (calm >= 2) break
                }
                androidx.compose.animation.core.animate(slide, 0f, 0f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 420f)) { v, _ ->
                    slide = v
                    entry.enter = 1f - v / widthPx
                }
                entry.covering = true
            }
        }
        fun close(velocity: Float = 0f) {
            if (closing) return
            closing = true
            moveTo(heightPx, androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.FastOutLinearInEasing), velocity) { onClosed() }
        }
        // 쓸어내리기·닫기는 쌓인 시트를 모두 — 위 시트가 닫히기 시작하면 이 시트도 같이 내려갑니다.
        fun dismiss(velocity: Float = 0f) {
            if (closing) return
            close(velocity)
            EvoChrome.dismissAll++
        }
        val dismissSeen = remember { EvoChrome.dismissAll }
        LaunchedEffect(EvoChrome.dismissAll) { if (EvoChrome.dismissAll != dismissSeen) close() }
        // 뒤로 가기 — 다른 시트 위에 열린 시트는 오른쪽으로 빠지고(아래 시트가 드러남), 맨 아래 시트는 내려갑니다.
        fun back() {
            if (closing) return
            if (!pushed) return close()
            closing = true
            entry.covering = false
            sliding?.cancel()
            sliding = scope.launch {
                androidx.compose.animation.core.animate(slide, widthPx, 0f, androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { v, _ ->
                    slide = v
                    entry.enter = 1f - v / widthPx
                }
                onClosed()
            }
        }
        androidx.activity.compose.BackHandler { back() }
        // 내용 스크롤과 이어 끌기 — 내용이 맨 위라 남은 아래로 끌기는 시트를 내리고, 시트가 내려간 채 위로 끌면 시트부터
        // 올립니다. 손을 떼면 머리를 놓을 때와 같은 기준(1/4·빠르기)으로 닫거나 제자리로.
        val pullToClose = remember(heightPx) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (available.y < 0f && offset > 0f && !closing && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                        val used = maxOf(available.y, -offset)
                        offset += used
                        return androidx.compose.ui.geometry.Offset(0f, used)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override fun onPostScroll(
                    consumed: androidx.compose.ui.geometry.Offset,
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    if (available.y > 0f && !closing && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                        moving?.cancel()
                        offset += available.y
                        return androidx.compose.ui.geometry.Offset(0f, available.y)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    if (offset > 0f && !closing) {
                        if (offset > heightPx * 0.25f || available.y > 1200f) dismiss(available.y) else moveTo(0f, spring, available.y)
                        return available
                    }
                    return androidx.compose.ui.unit.Velocity.Zero
                }
            }
        }
        // 끌어서 닫기 — 머리, 그리고 스크롤되지 않는 내용(짧은 안내 등). 스크롤되는 목록은 위의 [pullToClose] 로 이어 끕니다.
        val sheetDrag = Modifier.draggable(
            orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
            state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                if (!closing) offset = (offset + delta).coerceAtLeast(0f)
            },
            onDragStarted = { if (!closing) moving?.cancel() },
            onDragStopped = { velocity ->
                if (offset > heightPx * 0.25f || velocity > 1200f) dismiss(velocity)
                else moveTo(0f, spring, velocity)
            },
        )
        // 시트가 내려갈수록 뒤 시간축을 가리던 막이 걷힙니다. 옆에서 밀려 드나드는 동안엔 덮는 만큼만 아래 시트를 옅게 어둡게
        // (두 시트 바탕이 같아 경계가 보이도록).
        // 그리기 단계에서만 읽습니다 — 구성에서 읽으면 열고 닫는 내내 시트 틀 전체가 프레임마다 다시 구성됐습니다.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val scrim = if (pushed && !entry.covering) 0.15f * (1f - slide / widthPx).coerceIn(0f, 1f)
                else 0.3f * (1f - offset / heightPx).coerceIn(0f, 1f)
                drawRect(Color.Black.copy(alpha = scrim))
            },
        )
        // 바탕은 배경 사진을 흐린 위에 테마 면 색을 덮은 판(사진이 없으면 기본 그라데이션) — 화면마다 사진이 이어지면서도
        // 글이 많은 시트의 테마 글자색이 사진 밝기와 엇갈리지 않게.
        // 시트 안 내용엔 LocalBlurredPageBackground 를 끊어 두므로, 시트 안에서 다시 연 시트(신청·내역 → 도서관 내역 등)는
        // 바깥 시트가 따로 물려준 사진을 씁니다 — 안 그러면 기본 배경으로 그려졌습니다.
        val blurred = LocalBlurredPageBackground.current ?: LocalSheetPhoto.current
        val photoFilter = PageBackgroundAdjust.colorFilter()
        val tint = frostedPageTint()
        val sharp = if (photo) LocalPageBackground.current else null
        val onPage = if (sharp != null) LocalOnPageColor.current else null
        val ink = onPage ?: scheme.onSurface
        val sheetHaze = remember { dev.chrisbanes.haze.HazeState() }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides ink,
            LocalOnPageColor provides onPage,
            // 사진 바탕이면 카드가 홈처럼 서리 유리(헤이즈) — 이 값이 null 이면 카드는 '흐린 판 위 카드'(거의 불투명)로 그려졌습니다.
            // 실제 흐림은 카드가 창 위치로 흐린 사진을 그리는 것이라 이 상태는 '서리로 그리라'는 표시로만 씁니다.
            LocalMaterialHazeState provides if (sharp != null) sheetHaze else null,
            LocalBlurredPageBackground provides if (sharp != null) blurred else null,
            LocalPageBackground provides sharp,
            LocalSheetPhoto provides blurred,
        ) {
            // 위에 시트가 밀려 들어오는 만큼 왼쪽으로 1/4 비켜납니다(그리기 단계에서만 읽음).
            fun shiftX(): Float {
                val i = EvoChrome.stack.indexOf(entry)
                val above = if (i >= 0) EvoChrome.stack.getOrNull(i + 1)?.enter ?: 0f else 0f
                return slide - widthPx * 0.25f * above
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = offset
                        translationX = shiftX()
                    }
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .drawBehind {
                        if (sharp != null) {
                            // 창에 고정 — 시트가 오르내려도 사진은 홈과 같은 자리, 카드 서리 유리(창 위치로 비춤)와도 맞습니다.
                            translate(-shiftX(), -offset) { drawPageBackground(size.width, size.height, dark, sharp, null, photoFilter) }
                        } else {
                            drawPageBackground(size.width, size.height, dark, blurred, null, photoFilter)
                            if (blurred != null) drawRect(tint)
                        }
                    },
            ) {
                // 머리
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(sheetDrag)
                        .padding(top = top + 6.dp, start = 18.dp, end = 10.dp, bottom = 6.dp),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(ink.copy(alpha = 0.25f)))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 다른 시트 위에 열린 화면 — 왼쪽 위 '<' 로 아래 화면(게시글 목록·모든 기능 등)으로. 닫기·쓸어내리기는 홈·타임라인으로.
                        if (pushed) Box(
                            Modifier.padding(end = 2.dp).offset(x = (-8).dp).size(40.dp).clip(CircleShape).clickable { back() },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "뒤로",
                                tint = ink,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
                        actions()
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { dismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                                val w = 2.dp.toPx()
                                drawLine(ink, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), w, androidx.compose.ui.graphics.StrokeCap.Round)
                                drawLine(ink, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), w, androidx.compose.ui.graphics.StrokeCap.Round)
                            }
                        }
                    }
                }
                // 스크롤되는 자식이 끌기를 먼저 받으므로, 바깥 끌기는 스크롤되지 않는 곳에서만 움직입니다.
                Box(Modifier.fillMaxWidth().weight(1f).then(sheetDrag).nestedScroll(pullToClose)) {
                    content(androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom + 28.dp))
                }
            }
        }
    }
}
