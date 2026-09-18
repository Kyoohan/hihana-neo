package com.yhjang.timetable.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * One UI 9 스타일 공통 컴포넌트 모음.
 *
 * 화면 코드는 M3 의 Card/FilterChip/Button/AlertDialog 대신 여기 있는 것들만 쓰는 게 목표입니다 —
 * 그래야 "카드는 26dp 둥근 흰 컨테이너, 버튼은 알약, 다이얼로그 버튼은 아래 한 줄로 반반" 같은
 * One UI 규칙이 한 곳에서 관리됩니다. 시각 기준은 One UI Design Kit 의 Containers / Card /
 * Buttons / Dialog / Switches / Sliders / Top App Bar / Headers 프레임입니다.
 */

// MARK: - 컨테이너

/**
 * One UI 컨테이너 — 28dp 둥근 반투명 글래스 카드(삼성 헬스 홈 타일). 그림자·테두리 없음, 뒤의
 * 그라디언트 배경이 살짝 비칩니다. 컨테이너 자체가 탭 가능하면 [onClick] 을 주면 됩니다.
 */
@Composable
fun OneUiCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.glassCard,
    shape: Shape = RoundedCornerShape(OneUi.CornerLarge),
    contentPadding: PaddingValues = PaddingValues(OneUi.CardPadding),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * 여러 행을 한 컨테이너에 담고 행 사이에만 얇은 구분선을 넣는 One UI "grouped card".
 * 행 컴포저블은 자기 여백을 갖지 않습니다 — [OneUiListItem] 처럼 좌우 [OneUi.RowPadding] 을 스스로 둡니다.
 */
@Composable
fun <T> OneUiGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    OneUiCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        items.forEachIndexed { index, item ->
            row(item)
            if (index != items.lastIndex) OneUiDivider()
        }
    }
}

/** 행 구성이 제각각일 때 쓰는 그룹 컨테이너 — 호출부가 [OneUiDivider] 를 직접 끼워 넣습니다. */
@Composable
fun OneUiGroupColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OneUiCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), content = content)
}

/** 그룹 컨테이너 안 행 사이 구분선 — 왼쪽 여백을 행 텍스트와 맞춥니다. */
@Composable
fun OneUiDivider(startIndent: Dp = OneUi.RowPadding) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent, end = OneUi.RowPadding),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * One UI 메뉴 항목 — 왼쪽 아이콘(선택), 제목 + 보조 텍스트, 오른쪽 트레일링(스위치·화살표·값).
 * 키트의 "Example menu item with icon & body" 프레임을 그대로 옮긴 것입니다.
 */
@Composable
fun OneUiListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = OneUi.RowPadding, vertical = if (subtitle == null) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    leading()
                }
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * 그룹 위에 붙는 소제목 — One UI 설정처럼 강조색의 작은 굵은 글씨로, 컨테이너 왼쪽 텍스트 라인에 맞춥니다.
 */
@Composable
fun OneUiSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = OneUi.RowPadding, top = 18.dp, bottom = 8.dp),
    )
}

// MARK: - 버튼

enum class OneUiButtonStyle {
    /** 강조색 배경 + 흰 글자 — 화면의 주 동작 */
    Filled,
    /** 회색 배경 + 본문색 글자 — 보조 동작 (키트 "Buttons" 의 어두운/흰 버튼) */
    Neutral,
    /** 강조색 1.5dp 테두리 — 키트 "Outlined Button" */
    Outlined,
}

/**
 * 알약 버튼. One UI 는 모서리를 완전히 둥글리고 글자를 굵게 씁니다.
 * [compact] 는 카드 헤더 옆에 들어가는 작은 버전(높이 36dp)입니다.
 */
@Composable
fun OneUiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OneUiButtonStyle = OneUiButtonStyle.Filled,
    enabled: Boolean = true,
    compact: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (style) {
        OneUiButtonStyle.Filled -> scheme.primary
        OneUiButtonStyle.Neutral -> scheme.floatingPill
        OneUiButtonStyle.Outlined -> Color.Transparent
    }
    val content = when (style) {
        OneUiButtonStyle.Filled -> scheme.onPrimary
        OneUiButtonStyle.Neutral -> scheme.onSurface
        OneUiButtonStyle.Outlined -> scheme.primary
    }
    val height = if (compact) 36.dp else 46.dp
    val horizontal = if (compact) 16.dp else 24.dp

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(container)
            .then(
                if (style == OneUiButtonStyle.Outlined) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = height)
            .padding(horizontal = horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
            )
        }
    }
}

/**
 * 배경 없는 텍스트 버튼 — One UI 는 다이얼로그·카드 안의 보조 동작을 굵은 본문색 글자로만 둡니다 (파란색 아님).
 */
@Composable
fun OneUiTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/**
 * 상단 오른쪽 아이콘 버튼 묶음 — 삼성 헬스처럼 펼친 상태에선 배경 없이 아이콘만, 스크롤로 접히면
 * 진한 반투명 알약 안에 떠 있는 형태가 됩니다. [pillAlpha] 로 두 상태 사이를 잇습니다.
 */
@Composable
fun OneUiActionPill(
    modifier: Modifier = Modifier,
    pillAlpha: Float = 1f,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.floatingPill.copy(alpha = MaterialTheme.colorScheme.floatingPill.alpha * pillAlpha))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// MARK: - 선택 컨트롤

/**
 * 선택 칩 — 알약 모양. 선택되면 강조색 배경에 흰 굵은 글자, 아니면 회색 배경에 본문색 글자.
 */
@Composable
fun OneUiChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primary else scheme.floatingPill,
        label = "chipContainer",
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurface,
        label = "chipContent",
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .height(36.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides content) { leading() }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1,
        )
    }
}

/** One UI 스위치 — 파란 트랙에 흰 썸, 꺼지면 회색 트랙. 아이콘·테두리 없음. */
@Composable
fun OneUiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = OneUi.SwitchOff,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** 라디오 — 선택은 강조색 링 + 점, 비선택은 회색 링. */
@Composable
fun OneUiRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) scheme.primary else OneUi.SwitchOff, label = "radioRing")
    Box(
        modifier = modifier
            .size(22.dp)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(scheme.primary))
        }
    }
}

/** 라디오 행 — 그룹 컨테이너 안에서 쓰는 "Light / Dark" 같은 선택 항목. */
@Composable
fun OneUiRadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = OneUi.RowPadding, vertical = if (subtitle == null) 15.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneUiRadio(selected)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * One UI 슬라이더 — 두꺼운 둥근 트랙(강조색/회색)과 강조색 테두리의 동그란 썸. 눈금은 그리지 않습니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        valueRange = valueRange,
        steps = steps,
        interactionSource = interaction,
        colors = SliderDefaults.colors(
            thumbColor = scheme.primary,
            activeTrackColor = scheme.primary,
            inactiveTrackColor = OneUi.SliderInactive.copy(alpha = 0.55f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(scheme.surface)
                    .border(2.5.dp, scheme.primary, CircleShape),
            )
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(OneUi.SliderInactive.copy(alpha = 0.45f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        },
    )
}

/**
 * One UI 입력란 — 박스 테두리 대신 아래 밑줄 하나만 있는 형태. 포커스되면 밑줄이 강조색으로 두꺼워집니다.
 */
@Composable
fun OneUiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(topStart = OneUi.CornerSmall, topEnd = OneUi.CornerSmall),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = scheme.primary,
            unfocusedIndicatorColor = scheme.outline,
            focusedLabelColor = scheme.primary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            cursorColor = scheme.primary,
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
        ),
    )
}

/** 작은 알약 배지 — D-day, "시험", "자동" 같은 짧은 라벨. */
@Composable
fun OneUiBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = content,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 로딩 스피너 — 강조색, 얇은 선. */
@Composable
fun OneUiLoading(
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    stroke: Dp = 2.5.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = stroke,
        color = color,
        trackColor = Color.Transparent,
    )
}

// MARK: - 다이얼로그

/** [OneUiDialog] 하단에 나란히 놓이는 텍스트 버튼 한 개. */
data class OneUiDialogButton(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    /** 버튼 자리에 스피너와 함께 보여줄 진행 문구 — null 이면 [text] 를 그대로 씁니다. */
    val busyText: String? = null,
)

/**
 * One UI 다이얼로그 — 26dp 둥근 컨테이너, 왼쪽 정렬 굵은 제목, 본문, 그리고 맨 아래에
 * 버튼들이 폭을 똑같이 나눠 갖고 그 사이에 얇은 세로 구분선이 있는 한 줄 (키트 "Dialog").
 */
@Composable
fun OneUiDialog(
    onDismissRequest: () -> Unit,
    title: String,
    buttons: List<OneUiDialogButton>,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Surface(
            shape = RoundedCornerShape(OneUi.CornerLarge),
            // 다크에서는 검정 배경 위라 카드색(#17171A)보다 한 단계 밝은 톤이어야 다이얼로그가 떠 보입니다.
            color = if (scheme.isDark) scheme.surfaceContainerHigh else scheme.surface,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    content()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    buttons.forEachIndexed { index, button ->
                        if (index > 0) {
                            VerticalDivider(
                                modifier = Modifier.height(20.dp),
                                thickness = 1.dp,
                                color = scheme.outlineVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                // 진행 중(busyText) 버튼은 비활성이어도 흐리게 하지 않아 스피너가 또렷하게 보입니다.
                                .alpha(if (button.enabled || button.busyText != null) 1f else 0.4f)
                                .clip(RoundedCornerShape(OneUi.CornerSmall))
                                .clickable(enabled = button.enabled, onClick = button.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            val busy = button.busyText
                            if (busy != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OneUiLoading(size = 16.dp, stroke = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(busy, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    button.text,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (button.destructive) scheme.error else scheme.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 제목 + 문장 + 확인 버튼 하나짜리 알림 다이얼로그. */
@Composable
fun OneUiAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String = "확인",
) {
    OneUiDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        buttons = listOf(OneUiDialogButton(confirmText, onDismissRequest)),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: - 전체 화면

/**
 * 전체 화면 다이얼로그(설정·알리미·게시글·시험 정보)의 공통 뼈대 — 회색 페이지 배경 위에
 * 왼쪽 뒤로가기 화살표, 굵은 제목(+회색 부제목), 오른쪽 액션 캡슐이 있는 One UI 툴바를 얹습니다.
 */
@Composable
fun OneUiFullScreen(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = modifier.fillMaxSize(), color = Color.Transparent) {
            Column(Modifier.fillMaxSize().oneUiBackground(scheme.isDark)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (actions != null) {
                        Spacer(Modifier.width(8.dp))
                        OneUiActionPill(content = actions)
                    }
                }
                content()
            }
        }
    }
}

// MARK: - 접히는 확장 헤더

/**
 * 메인 화면 상단 헤더의 접힘 상태. 콘텐츠를 위로 스크롤하면 큰 제목 영역이 먼저 줄어들고,
 * 맨 위에서 아래로 당기면 다시 펼쳐집니다 (One UI "Enlarged header" 동작).
 * [offsetPx] 는 0(펼침) ~ -[rangePx](접힘) 사이 값입니다.
 */
@Stable
class OneUiHeaderState(val rangePx: Float, initialOffset: Float = 0f) {
    var offsetPx by mutableFloatStateOf(initialOffset.coerceIn(-rangePx, 0f))
        private set

    /** 0 = 완전히 펼침, 1 = 완전히 접힘 */
    val fraction: Float get() = if (rangePx <= 0f) 1f else (-offsetPx / rangePx).coerceIn(0f, 1f)

    private fun consume(dy: Float): Float {
        val next = (offsetPx + dy).coerceIn(-rangePx, 0f)
        val consumed = next - offsetPx
        offsetPx = next
        return consumed
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        // 위로 스크롤(dy<0)은 콘텐츠보다 헤더가 먼저 접힙니다.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            return Offset(0f, consume(available.y))
        }

        // 아래로 당김(dy>0)은 콘텐츠가 맨 위라 못 쓰고 남긴 만큼만 헤더가 펼쳐집니다.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            return Offset(0f, consume(available.y))
        }
    }

    companion object {
        fun saver(rangePx: Float): Saver<OneUiHeaderState, Float> = Saver(
            save = { it.offsetPx },
            restore = { OneUiHeaderState(rangePx, it) },
        )
    }
}

/** 접힌 상태의 헤더 높이(상태바 제외) — 플로팅 아이콘 알약 한 줄. */
val OneUiHeaderCollapsedHeight = 60.dp

/** 펼쳤을 때 더해지는 높이 — 왼쪽 큰 제목 + 부제목이 들어갈 자리. */
val OneUiHeaderExpandedExtra = 56.dp

@Composable
fun rememberOneUiHeaderState(expandedExtra: Dp = OneUiHeaderExpandedExtra): OneUiHeaderState {
    val rangePx = with(LocalDensity.current) { expandedExtra.toPx() }
    return rememberSaveable(rangePx, saver = OneUiHeaderState.saver(rangePx)) { OneUiHeaderState(rangePx) }
}

/**
 * 삼성 헬스 홈 상단 — 펼치면 왼쪽에 큰 제목(+회색 부제목), 오른쪽에 배경 없는 아이콘들. 스크롤로
 * 접히면 제목은 사라지고 아이콘들만 진한 알약 안에 모여 콘텐츠 위에 떠 있습니다. 배경은 투명해서
 * 페이지의 그라디언트가 그대로 보입니다.
 */
@Composable
fun OneUiCollapsingHeader(
    state: OneUiHeaderState,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val fraction = state.fraction
    val extra = with(density) { (state.rangePx + state.offsetPx).toDp() }
    val titleAlpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(OneUiHeaderCollapsedHeight + extra)
                .padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(titleAlpha),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (actions != null) {
                Spacer(Modifier.width(8.dp))
                // 접히는 동안 알약 배경이 서서히 나타나 아이콘들이 한 덩어리로 묶입니다.
                OneUiActionPill(
                    pillAlpha = ((fraction - 0.3f) / 0.7f).coerceIn(0f, 1f),
                    modifier = Modifier.align(Alignment.Top).padding(top = if (extra > 0.dp) 4.dp else 6.dp),
                    content = actions,
                )
            }
        }
    }
}

// MARK: - 삼성 헬스 홈 요소

/**
 * 지표 아이콘 배지 — 채도 높은 원 안에 흰 글리프 (삼성 헬스 "일일 활동"의 걸음·시간·칼로리 아이콘).
 */
@Composable
fun OneUiMetricIcon(
    painter: Painter,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * 큰 숫자 + 작은 단위 한 줄 — "642 걸음" 처럼 값은 굵고 크게, 단위는 옆에 작게 붙입니다.
 */
@Composable
fun OneUiBigValue(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = valueStyle,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (unit != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodyLarge,
                color = color.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** 얇은 둥근 진행바 — 회색 트랙 위에 색 채움 (삼성 헬스 걸음 타일의 막대). */
@Composable
fun OneUiProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.progressTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * 겹친 링 두 개 — 삼성 헬스 활동 링처럼 바깥은 하루 전체 진행, 안쪽은 지금 블록 진행을 보여줍니다.
 * 값이 null 이면 그 링은 트랙만 그립니다.
 */
@Composable
fun OneUiRings(
    outer: Float?,
    inner: Float?,
    modifier: Modifier = Modifier,
    outerColor: Color = OneUi.Lime,
    innerColor: Color = OneUi.Sky,
    size: Dp = 96.dp,
    stroke: Dp = 11.dp,
) {
    val track = MaterialTheme.colorScheme.progressTrack
    Canvas(modifier = modifier.size(size)) {
        val strokePx = stroke.toPx()
        val gap = strokePx * 0.55f
        fun ring(value: Float?, color: Color, inset: Float) {
            val rect = androidx.compose.ui.geometry.Rect(
                inset, inset, this.size.width - inset, this.size.height - inset,
            )
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (value != null && value > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * value.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        ring(outer, outerColor, strokePx / 2f)
        ring(inner, innerColor, strokePx / 2f + strokePx + gap)
    }
}

/**
 * 홈 타일 — 왼쪽 위 작은 제목, 오른쪽 위 주황 알림 점(선택), 그 아래 내용. 2열 그리드에 나란히 놓습니다.
 */
@Composable
fun OneUiTile(
    title: String,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OneUiCard(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                content()
            }
            if (showDot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(OneUi.NotifyDot),
                )
            }
        }
    }
}

/** 페이지 점 — 히어로 카드 아래 현재 페이지는 길쭉한 알약, 나머지는 작은 점. */
@Composable
fun OneUiPageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.onSurface
    val off = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (selected) 26.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) on else off),
            )
        }
    }
}

/**
 * 알림 카드 — 본문 한 단락과 오른쪽 아래 알약 버튼들 (삼성 헬스 "동기화되지 않았습니다" 카드).
 * 카드 자체는 다른 카드보다 조금 더 불투명한 진한 톤입니다.
 */
@Composable
fun OneUiNoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryText: String,
    onPrimary: () -> Unit,
) {
    OneUiCard(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.floatingPill,
        contentPadding = PaddingValues(start = 22.dp, end = 16.dp, top = 22.dp, bottom = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (secondaryText != null && onSecondary != null) {
                NoticePillButton(secondaryText, onSecondary)
                Spacer(Modifier.width(10.dp))
            }
            NoticePillButton(primaryText, onPrimary)
        }
    }
}

@Composable
private fun NoticePillButton(text: String, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.isDark
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (dark) Color(0xFF0F1216).copy(alpha = 0.9f) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
