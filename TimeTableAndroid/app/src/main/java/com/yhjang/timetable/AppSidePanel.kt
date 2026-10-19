package com.yhjang.timetable

import com.yhjang.timetable.ui.onPageVariantColor
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.isDark

/**
 * 큰 화면(가로 태블릿) 내비게이션 — 갤럭시 탭의 삼성 시계·캘린더·내 파일을 재서 맞춘 틀입니다.
 * - 화면 왼쪽 10dp 안쪽에 뜬 둥근 패널, 펼치면 폭 300dp(시계 side_panel 과 같은 값), 접으면 72dp 아이콘 레일
 * - 맨 위 64dp 줄에 메뉴(≡) 버튼 — 눌러 펼치기·접기, 상태는 기억합니다
 * - 항목은 아이콘 칸 48dp + 이름, 고른 항목은 둥근 바탕으로 표시 (시계의 navigation_item_layout)
 * - 아래쪽 묶음은 모든 탭 공통인 알리미·설정 (내 파일의 휴지통·저장공간 관리 자리)
 * 폰의 하단 바·오른쪽 위 버튼 섬은 이 모드에서 쓰지 않습니다.
 */
object SidePanel {
    val Inset = 10.dp
    val ExpandedWidth = 300.dp
    val CollapsedWidth = 72.dp
    private const val PREFS = "large_screen"
    private const val KEY_OPEN = "side_panel_open"

    fun isOpen(context: Context, default: Boolean): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OPEN, default)

    fun setOpen(context: Context, open: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OPEN, open).apply()
    }
}

/**
 * 패널이 차지하는 가로 공간(왼쪽 여백 포함)의 목표값 — 콘텐츠는 이만큼 오른쪽에서 시작합니다. 애니메이션 중간값이 아니라
 * 목표값이라, 펼치고 접는 동안 화면 전체가 프레임마다 다시 계산되지 않습니다(패널 폭 애니메이션은 패널 안에서만 읽음).
 */
fun sidePanelSpace(open: Boolean): Dp = SidePanel.Inset + if (open) SidePanel.ExpandedWidth else SidePanel.CollapsedWidth

/** 겹쳐 뜬 패널 바깥의 가림막 — 투명도는 그리기 단계에서만 읽고, 펼쳐져 있을 때만 눌러서 닫힙니다. */
@Composable
fun SidePanelScrim(open: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val alpha = androidx.compose.animation.core.animateFloatAsState(if (open) 0.35f else 0f, label = "panelScrim")
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Color.Black)
            .then(if (open) Modifier.pointerInput(Unit) { detectTapGestures { onClose() } } else Modifier),
    )
}

@Composable
fun AppSidePanel(
    selected: Int,
    onSelect: (Int) -> Unit,
    open: Boolean,
    onToggle: () -> Unit,
    alimUnread: Int,
    onOpenAlim: () -> Unit,
    onOpenSettings: () -> Unit,
    showDev: Boolean,
    modifier: Modifier = Modifier,
    opaque: Boolean = false,
) {
    // 폭 애니메이션은 이 안에서만 — 측정(layout)·그리기(graphicsLayer) 단계에서 읽어, 펼치고 접는 동안 다시 구성하지 않습니다.
    val widthState = animateDpAsState(
        if (open) SidePanel.ExpandedWidth else SidePanel.CollapsedWidth,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "sidePanelWidth",
    )
    val labels = if (showDev) TabIndex.titles + "Dev" else TabIndex.titles
    // 이름은 패널이 거의 다 펼쳐졌을 때 나타납니다 — 반쯤 접힌 폭에서 잘린 글자가 보이지 않게.
    val labelAlpha = { ((widthState.value - SidePanel.CollapsedWidth) / (SidePanel.ExpandedWidth - SidePanel.CollapsedWidth) * 1.6f - 0.6f).coerceIn(0f, 1f) }
    OneUiCard(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = SidePanel.Inset, top = 8.dp, bottom = SidePanel.Inset)
            .layout { measurable, constraints ->
                val w = widthState.value.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .fillMaxHeight(),
        shape = RoundedCornerShape(26.dp),
        // 재질 카드(배경이 비침) 대신 불투명한 한 단계 밝은 면 — 겹쳐 뜬 패널 뒤의 글자가 비치지 않게.
        color = if (opaque) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Menu, contentDescription = if (open) "메뉴 접기" else "메뉴 펼치기")
            }
            Text(
                "하이하나 Neo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(start = 4.dp).graphicsLayer { alpha = labelAlpha() },
            )
        }
        Spacer(Modifier.height(8.dp))
        labels.forEachIndexed { index, label ->
            PanelItem(label = label, selected = index == selected, labelAlpha = labelAlpha, onClick = { onSelect(index) }) {
                NavIcon(index, if (index == selected) MaterialTheme.colorScheme.onSurface else onPageVariantColor())
            }
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            thickness = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        PanelItem(label = "알리미", selected = false, labelAlpha = labelAlpha, onClick = onOpenAlim) {
            BadgedBox(badge = {
                if (alimUnread > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text(if (alimUnread > 99) "99+" else "$alimUnread") }
                }
            }) { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_inbox), contentDescription = "알리미", tint = onPageVariantColor()) }
        }
        PanelItem(label = "설정", selected = false, labelAlpha = labelAlpha, onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = onPageVariantColor())
        }
    }
}

@Composable
private fun ColumnScope.PanelItem(
    label: String,
    selected: Boolean,
    labelAlpha: () -> Float,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(if (selected) scheme.onSurface.copy(alpha = if (scheme.isDark) 0.12f else 0.07f) else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = scheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(start = 4.dp).graphicsLayer { alpha = labelAlpha() },
        )
    }
}

/**
 * 큰 화면 헤더 — 접히지 않는 한 줄(삼성 캘린더 툴바 74dp 와 비슷한 높이). 왼쪽은 제목·날짜, 오른쪽은 이 탭의 버튼들.
 * 폰처럼 큰 제목이 스크롤에 따라 접히는 구조는 넓은 화면에서 위쪽 여백만 크게 남겨 쓰지 않습니다.
 */
@Composable
fun LargeScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onPageVariantColor(),
                    maxLines = 1,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

/**
 * 폰은 전체 화면([OneUiFullScreen]), 큰 화면의 두 칸 오른쪽에서는 같은 내용을 칸 안에 바로 그립니다 — 칸 위에 제목 한 줄과
 * 버튼만 두고 뒤로 가기는 없습니다(왼쪽 목록에서 다른 항목을 고르면 바뀜). 내역·알리미 원문처럼 두 곳에서 쓰는 화면용.
 */
@Composable
fun PaneOrFullScreen(
    embedded: Boolean,
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(PaddingValues) -> Unit,
) {
    if (!embedded) {
        com.yhjang.timetable.ui.OneUiFullScreen(title = title, onDismiss = onDismiss, subtitle = subtitle, actions = actions, content = content)
        return
    }
    Column(Modifier.fillMaxSize()) {
        PaneTitle(title, subtitle, actions)
        Box(Modifier.weight(1f).fillMaxWidth()) { content(PaddingValues(bottom = 16.dp)) }
    }
}

/** 두 칸 오른쪽 칸의 제목 줄 — 굵은 제목(+ 부제)과 오른쪽 버튼. */
@Composable
fun PaneTitle(title: String, subtitle: String? = null, actions: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = onPageVariantColor(), maxLines = 1)
            }
        }
        if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
