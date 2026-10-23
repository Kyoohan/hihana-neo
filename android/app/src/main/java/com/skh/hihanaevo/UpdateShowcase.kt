package com.skh.hihanaevo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.IconStyle
import com.skh.hihanaevo.ui.IconStylePreview
import com.skh.hihanaevo.ui.isDark
import com.skh.hihanaevo.ui.oneUiPageBackground
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * 업데이트 투어 첫 장의 새 기능 벤토 — 애플 발표의 '한 장에 다 보이는' 슬라이드처럼 가운데 큰 타일에 로고(오프닝 끝 장면이
 * 줄어들어 들어옴), 둘레에 크기가 다른 타일. 타일 안은 손으로 그린 그림이 아니라 앱의 실제 화면 부품을 예시 데이터로 띄워
 * 줄인 것입니다(투어 그림은 실제 화면을 그대로 줄여 그린다는 규칙). 누르기는 막아 둡니다. 4칸 격자(칸 = 폭의 1/4):
 *
 *   [ 데일리 브리핑 카드 ][ 타임라인 카드 ]
 *   [ 모든 기능 ][      가운데      ][ 아이콘 결 ]
 *   [ 블록      ][      (로고)      ][ 시간표·학사일정 블록 ]
 *   [ 오늘 브리프 카드 ][ 세 칸 하단 바 ]
 *
 * 타일은 [appear] 0 → 1 에 맞춰 가운데에서 가까운 것부터 떠오릅니다.
 */

@Composable
internal fun EvoFeatureShowcase(appear: Float, onCenter: (Rect) -> Unit, modifier: Modifier = Modifier, everything: Boolean = false) {
    val today = remember { PlanStore.today() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 8.dp
        val u = (maxWidth - gap * 3) / 4
        val v = u * 1.8f
        val wide = u * 2 + gap
        val home: @Composable () -> Unit = { Mini(400.dp) { BentoPreview() } }
        val timeline: @Composable () -> Unit = { Mini(360.dp) { TimelineDayPreview(Modifier.fillMaxWidth().height(520.dp)) } }
        val brief: @Composable () -> Unit = { Mini(340.dp) { SampleBrief(today) } }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (everything) Tile("지금 챙길 홈", wide, v, appear, 2) { home() }
                else Tile("AI 데일리 브리핑", wide, v, appear, 2) { brief() }
                Tile("타임라인", wide, v, appear, 2) { timeline() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile("전체", u, v * 2 + gap, appear, 1) {
                    Mini(170.dp) {
                        AxisDestinationsPreview(
                            if (everything) listOf("시간표", "급식", "게시판", "신청·내역", "클래스룸", "학사일정")
                            else listOf("급식", "게시판", "신청·내역", "클래스룸", "알리미", "날씨"),
                            columns = 1,
                        )
                    }
                }
                // 가운데 — 비워 두고 자리만 알립니다(로고는 위에 겹친 끝 장면이 줄어들어 들어옴).
                Tile(null, wide, v * 2 + gap, appear, 0, Modifier.onGloballyPositioned { onCenter(it.boundsInRoot()) }) {}
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (everything) Tile("게시글 AI 요약", u, v, appear, 1) { Mini(200.dp) { SampleSummary() } }
                    else Tile("클래스룸", u, v, appear, 1) { Mini(200.dp) { ClassroomPreview() } }
                    Tile("아이콘 결", u, v, appear, 1) { Mini(124.dp) { SampleIconStyles() } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (everything) Tile("AI 데일리 브리핑", wide, v, appear, 2) { brief() }
                else Tile("지금 챙길 홈", wide, v, appear, 2) { home() }
                Tile("세 칸 하단 바", wide, v, appear, 2) { Mini(260.dp) { SampleNavBar() } }
            }
        }
    }
}

/** 타일 — 위 작은 이름표(단색), 아래 실제 화면 부품을 줄인 그림. [ring] 은 가운데에서 몇 번째 고리인지(먼저 떠오르는 순서). */
@Composable
private fun Tile(label: String?, w: Dp, h: Dp, appear: Float, ring: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    val x = ((appear - 0.05f - ring * 0.12f) / 0.45f).coerceIn(0f, 1f)
    val shown = 1f - (1f - x) * (1f - x) * (1f - x)
    val tile = if (scheme.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.6f)
    Column(
        modifier
            .width(w)
            .height(h)
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * rise
                val s = 0.96f + 0.04f * shown
                scaleX = s; scaleY = s
            }
            .clip(RoundedCornerShape(20.dp))
            .drawBehind { drawRect(tile) }
            .padding(start = 10.dp, top = 10.dp, end = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface.copy(alpha = 0.8f), maxLines = 1, textAlign = TextAlign.Center)
        // 아래 여백 없이 타일 끝까지 — 넘치는 카드는 타일 가장자리에서 잘려 '화면 일부를 들여다본' 모양이 됩니다.
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clipToBounds(), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * 실제 부품을 [design] 폭으로 그려 칸 폭에 맞게 줄입니다(왼쪽 위 기준으로 줄이고 그만큼만 자리를 차지). 누르기·스크롤은 막고,
 * 내비게이션 바 인셋은 소비해 하단 바 같은 부품이 시스템 바만큼 뜨지 않게 합니다.
 */
@Composable
private fun Mini(design: Dp, content: @Composable () -> Unit) {
    val designPx = with(LocalDensity.current) { design.roundToPx() }
    Box(
        Modifier
            .consumeWindowInsets(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
            }
            .layout { measurable, constraints ->
                val p = measurable.measure(Constraints(minWidth = designPx, maxWidth = designPx))
                val s = (constraints.maxWidth.toFloat() / designPx).coerceAtMost(1f)
                val h = (p.height * s).roundToInt().coerceAtMost(constraints.maxHeight)
                layout((designPx * s).roundToInt(), h) {
                    p.placeWithLayer(0, 0) {
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
    ) { content() }
}

// MARK: - 예시 데이터로 띄운 실제 부품

/** 투어 '홈' 장의 데일리 브리핑 예시 — 실제 카드에 예시 브리핑을 펼쳐서. */
@Composable
internal fun SampleDailyBrief() = SampleBrief(remember { PlanStore.today() })

@Composable
private fun SampleBrief(today: LocalDate) {
    val brief = remember(today) {
        MorningBrief.Brief(
            date = today,
            headline = "내일 미적분 수행평가가 있어요",
            body = "오늘은 7교시까지 수업이 있고 저녁엔 면학 1타임이에요. 내일 수행평가와 금요일 신청 마감을 미리 챙겨요.",
            highlights = listOf(
                MorningBrief.Highlight(today.plusDays(1), "미적분 수행평가", "3단원 범위를 복습해요"),
                MorningBrief.Highlight(today.plusDays(2), "철학 경시대회 신청", "금요일 17시까지"),
            ),
            ai = true,
            inputKey = "",
            madeAt = 0L,
        )
    }
    MorningBriefPanel(brief, loading = false, today = today, startExpanded = true)
}

@Composable
private fun SampleSummary() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("2학기 교내 수학경시대회 참가 신청 안내", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 2)
        AiSummaryPill("10/7(수)까지 포털 신청, 10/14 시청각실")
    }
}

@Composable
private fun SampleIconStyles() {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        listOf(IconStyle.MATERIAL, IconStyle.DUOTONE, IconStyle.BADGE, IconStyle.GRADIENT).forEach {
            IconStylePreview(it, tint = scheme.onSurfaceVariant, selectedTint = scheme.onSurface)
        }
    }
}

@Composable
private fun SampleNavBar() {
    AppNavBar(selected = 0, onSelect = {}, iconStyle = IconStyle.MATERIAL)
}

// MARK: - 새 조작법 그림

/**
 * 투어 '새 조작법' 장의 움직이는 그림 — 실제 홈(벤토)·타임라인·모든 기능 부품을 줄인 작은 화면에서, 손가락 점이 옆으로 밀어 홈 → 타임라인,
 * 한 번 더 밀어 모든 기능 시트를 올리고, 쓸어내려 닫은 뒤 다시 홈으로 돌아오는 것을 되풀이합니다. 아래엔 실제 세 칸 하단 바가
 * 손가락을 따라갑니다.
 */
@Composable
internal fun NavigationDemo(modifier: Modifier = Modifier) {
    val today = remember { PlanStore.today() }
    val scheme = MaterialTheme.colorScheme
    val loop = androidx.compose.animation.core.rememberInfiniteTransition(label = "navDemo")
    fun keys(vararg points: Pair<Int, Float>) = androidx.compose.animation.core.infiniteRepeatable<Float>(
        androidx.compose.animation.core.keyframes {
            durationMillis = 7200
            points.forEach { (at, v) -> v at at using androidx.compose.animation.core.FastOutSlowInEasing }
        },
    )
    // 홈(0) ↔ 타임라인(1), 모든 기능 시트(0 내려감 · 1 올라옴), 손가락 점의 자리(칸 비율)와 보임.
    val h by loop.animateFloat(0f, 0f, keys(0 to 0f, 900 to 0f, 1600 to 1f, 5600 to 1f, 6400 to 0f, 7200 to 0f), label = "h")
    val sheet by loop.animateFloat(0f, 0f, keys(0 to 0f, 2300 to 0f, 2900 to 1f, 4600 to 1f, 5200 to 0f, 7200 to 0f), label = "sheet")
    val fx by loop.animateFloat(0.78f, 0.78f, keys(0 to 0.78f, 900 to 0.78f, 1600 to 0.22f, 1700 to 0.78f, 2100 to 0.78f, 2600 to 0.45f, 2700 to 0.5f, 5200 to 0.5f, 5300 to 0.22f, 5600 to 0.22f, 6400 to 0.78f, 7200 to 0.78f), label = "fx")
    val fy by loop.animateFloat(0.55f, 0.55f, keys(0 to 0.55f, 4400 to 0.55f, 4500 to 0.2f, 4600 to 0.2f, 5200 to 0.75f, 5300 to 0.55f, 7200 to 0.55f), label = "fy")
    val fa by loop.animateFloat(0f, 0f, keys(0 to 0f, 800 to 0f, 900 to 1f, 1600 to 1f, 1700 to 0f, 2000 to 0f, 2100 to 1f, 2600 to 1f, 2700 to 0f, 4500 to 0f, 4600 to 1f, 5200 to 1f, 5300 to 0f, 5500 to 0f, 5600 to 1f, 6400 to 1f, 6500 to 0f, 7200 to 0f), label = "fa")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(236.dp)
                .height(330.dp)
                .clip(RoundedCornerShape(28.dp))
                .oneUiPageBackground()
                .drawBehind { drawRect(scheme.onSurface.copy(alpha = 0.04f)) }
                .clipToBounds(),
        ) {
            // 홈과 타임라인 — 옆으로 밀리는 두 칸.
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = -h * size.width }) {
                Mini(360.dp) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("홈", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${today.monthValue}월 ${today.dayOfMonth}일",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = scheme.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        BentoPreview()
                    }
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = (1f - h) * size.width }) {
                Mini(360.dp) { TimelineDayPreview(Modifier.fillMaxWidth().height(500.dp).padding(top = 12.dp)) }
            }
            // 모든 기능 — 아래에서 올라오는 시트.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = (1f - sheet) * size.height }
                    .padding(top = 26.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .oneUiPageBackground(),
            ) {
                Mini(360.dp) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).drawBehind { drawRect(scheme.onSurface.copy(alpha = 0.25f)) })
                        }
                        Text(
                            "전체",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
                        )
                        AxisDestinationsPreview(listOf("시간표", "학사일정", "급식", "게시판", "신청·내역", "클래스룸"))
                    }
                }
            }
            // 손가락 자리.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        if (fa > 0f) {
                            val c = androidx.compose.ui.geometry.Offset(fx * size.width, fy * size.height)
                            drawCircle(Color.White.copy(alpha = 0.55f * fa), radius = 15.dp.toPx(), center = c)
                            drawCircle(Color.Black.copy(alpha = 0.25f * fa), radius = 15.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                        }
                    },
            )
        }
        Spacer(Modifier.height(4.dp))
        // 실제 하단 바 — 캡슐이 홈 ↔ 타임라인 넘김을 따라갑니다.
        val navSelected by remember { androidx.compose.runtime.derivedStateOf { if (h > 0.5f) 1 else 0 } }
        Mini(260.dp) { AppNavBar(selected = navSelected, onSelect = {}, follow = { h }) }
    }
}


// MARK: - 뒤로 가기 · 쓸어내리기 연습

/**
 * 투어 '새 조작법' 장의 연습 칸 — 작은 화면 안에서 실제 하단 바와 모든 기능 블록을 눌러 보며 두 동작을 구분해 익힙니다:
 * ⊞ 로 모든 기능 → 급식 → '<'(또는 폰의 뒤로 가기)로 한 단계 돌아가기 → 다시 급식 → 위를 쓸어내려 모두 닫기.
 * 앱의 시트처럼 모든 기능은 아래에서, 급식은 그 위로 옆에서 들어오고, 쓸어내리면 둘 다 내려갑니다.
 */
@Composable
internal fun GesturePractice(modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 0: ⊞ 누르기 · 1: 급식 누르기 · 2: 뒤로 가기 · 3: 다시 급식 · 4: 쓸어내리기 · 5: 다 했음
    var step by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var panelOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    var mealOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    // 시트가 내려간 정도(쓸어내리기 중·닫힐 때)와 급식 시트가 옆으로 밀린 정도(px).
    val drop = remember { androidx.compose.animation.core.Animatable(0f) }
    val slide = remember { androidx.compose.animation.core.Animatable(1f) }
    val rise = remember { androidx.compose.animation.core.Animatable(1f) }
    val spring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

    fun openPanel() {
        if (panelOpen) return
        panelOpen = true
        scope.launch { drop.snapTo(0f); rise.snapTo(1f); rise.animateTo(0f, spring) }
        if (step == 0) step = 1
    }
    fun openMeal() {
        if (mealOpen) return
        mealOpen = true
        scope.launch { slide.snapTo(1f); slide.animateTo(0f, spring) }
        if (step == 1 || step == 3) step += 1
    }
    fun back() {
        when {
            mealOpen -> scope.launch {
                slide.animateTo(1f, androidx.compose.animation.core.tween(240))
                mealOpen = false
                if (step == 2) step = 3
            }
            panelOpen -> scope.launch {
                rise.animateTo(1f, androidx.compose.animation.core.tween(240))
                panelOpen = false
            }
        }
    }
    fun closeAll() = scope.launch {
        drop.animateTo(1f, androidx.compose.animation.core.tween(260))
        panelOpen = false; mealOpen = false
        drop.snapTo(0f); slide.snapTo(1f); rise.snapTo(1f)
        if (step == 4) { step = 5; onDone() }
    }
    // 폰의 뒤로 가기도 연습 칸 안에서는 한 단계 뒤로(투어 장을 넘기지 않음).
    androidx.activity.compose.BackHandler(enabled = panelOpen || mealOpen) { back() }

    val hint = when (step) {
        0 -> "아래 바의 ⊞ 를 눌러 전체를 열어 보세요."
        1 -> "급식을 눌러 보세요."
        2 -> "왼쪽 위 < 를 누르거나 폰의 뒤로 가기를 해 보세요. 한 단계 전(전체)으로 돌아갑니다."
        3 -> "다시 급식을 열어 보세요."
        4 -> "이번엔 급식 화면 위쪽을 아래로 쓸어내려 보세요. 열린 화면을 모두 닫고 홈으로 갑니다."
        else -> "잘했어요! 뒤로 가기는 한 단계 전으로, 쓸어내리기는 모두 닫고 홈·타임라인으로 돌아갑니다."
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        BoxWithConstraints(
            Modifier
                .width(300.dp)
                .height(430.dp)
                .clip(RoundedCornerShape(28.dp))
                .oneUiPageBackground()
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            val wPx = with(LocalDensity.current) { maxWidth.toPx() }
            val hPx = with(LocalDensity.current) { maxHeight.toPx() }
            // 홈
            Box(Modifier.fillMaxSize()) { Mini(400.dp) { Box(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) { BentoPreview() } } }
            if (!panelOpen) Box(Modifier.align(Alignment.BottomCenter)) {
                AppNavBar(selected = 0, onSelect = { if (it == 2) openPanel() })
            }
            // 모든 기능 — 아래에서 올라오는 시트
            if (panelOpen) PracticeSheet(
                title = "전체",
                back = false,
                modifier = Modifier.graphicsLayer { translationY = (rise.value + drop.value) * hPx },
                onBack = ::back,
                onClose = { closeAll() },
                onPull = { d -> scope.launch { drop.snapTo((drop.value + d / hPx).coerceAtLeast(0f)) } },
                onPullEnd = { if (drop.value > 0.18f) closeAll() else scope.launch { drop.animateTo(0f, spring) } },
            ) {
                AxisDestinationsPreview(listOf("급식", "게시판", "시간표", "클래스룸"), onOpen = { if (it == "급식") openMeal() })
            }
            // 급식 — 모든 기능 위로 옆에서 들어오는 시트
            if (mealOpen) PracticeSheet(
                title = "급식",
                back = true,
                modifier = Modifier.graphicsLayer {
                    translationX = slide.value * wPx
                    translationY = drop.value * hPx
                },
                onBack = ::back,
                onClose = { closeAll() },
                onPull = { d -> scope.launch { drop.snapTo((drop.value + d / hPx).coerceAtLeast(0f)) } },
                onPullEnd = { if (drop.value > 0.18f) closeAll() else scope.launch { drop.animateTo(0f, spring) } },
            ) {
                Text("점심 · 12:10", style = MaterialTheme.typography.labelLarge, color = Color(0xFFF08A3C), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("순살허브치킨구이", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                listOf("보리밥", "김치말이냉국수", "갈비손만두찜", "깍두기").forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
        if (step == 5) {
            Text(
                "다시 해 보기",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = scheme.primary,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).clickableNoRipple { step = 0 }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** 연습 칸의 시트 — 손잡이·제목('<' 는 다른 시트 위에 열린 것만)·✕, 위쪽을 끌어 내릴 수 있습니다. */
@Composable
private fun PracticeSheet(
    title: String,
    back: Boolean,
    modifier: Modifier,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPull: (Float) -> Unit,
    onPullEnd: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .oneUiPageBackground(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = onPullEnd,
                        onDragCancel = onPullEnd,
                    ) { change, d -> change.consume(); onPull(d) }
                }
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).drawBehind { drawRect(scheme.onSurface.copy(alpha = 0.25f)) })
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (back) Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "뒤로",
                    tint = scheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickableNoRipple(onBack).padding(4.dp).size(26.dp),
                )
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "닫기",
                    tint = scheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickableNoRipple(onClose).padding(8.dp).size(18.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(onClick) { detectTapGestures { onClick() } })
