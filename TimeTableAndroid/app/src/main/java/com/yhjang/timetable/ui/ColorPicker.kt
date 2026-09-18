package com.yhjang.timetable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 강조 색 직접 고르기 — HSV 방식. 위의 넓은 판에서 채도(가로)·밝기(세로)를, 아래 띠에서 색상(H)을 고르고,
 * 16진수 값도 직접 입력할 수 있습니다. 확정은 다이얼로그의 '적용'에서 합니다.
 */
@Composable
fun AccentColorPickerDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val hsv = remember(initialArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialArgb, it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, sat, value)
    var hexText by remember { mutableStateOf(color.toHex()) }
    // 판·띠를 움직이면 16진수 칸도 따라가고, 칸에 유효한 값을 치면 판·띠가 따라갑니다.
    var hexFromPicker by remember { mutableStateOf(true) }
    if (hexFromPicker) hexText = color.toHex()

    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "강조 색 직접 선택",
        buttons = listOf(
            OneUiDialogButton("취소", onDismiss),
            OneUiDialogButton("적용", { onApply(color.toArgb()) }),
        ),
    ) {
        SaturationValuePanel(
            hue = hue,
            saturation = sat,
            value = value,
            onChange = { s, v ->
                sat = s
                value = v
                hexFromPicker = true
            },
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
        Spacer(Modifier.height(16.dp))
        HueBar(
            hue = hue,
            onChange = {
                hue = it
                hexFromPicker = true
            },
            modifier = Modifier.fillMaxWidth().height(22.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            OneUiTextField(
                value = hexText,
                onValueChange = { typed ->
                    hexFromPicker = false
                    hexText = typed.take(7)
                    parseHex(typed)?.let { argb ->
                        val parsed = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
                        hue = parsed[0]
                        sat = parsed[1]
                        value = parsed[2]
                    }
                },
                label = "16진수 (#RRGGBB)",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "판에서 채도·밝기를, 아래 띠에서 색상을 고릅니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Color.toHex(): String = "#%06X".format(0xFFFFFF and toArgb())

private fun parseHex(text: String): Int? {
    val body = text.trim().removePrefix("#")
    if (body.length != 6 || body.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return (0xFF000000.toInt() or body.toInt(16))
}

/** 채도(가로)·밝기(세로) 판 — 흰→색상 가로 그라디언트 위에 투명→검정 세로 그라디언트를 겹칩니다. */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pure = Color.hsv(hue, 1f, 1f)
    val shape = RoundedCornerShape(OneUi.CornerMedium)
    Canvas(
        modifier = modifier
            .clip(shape)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    onChange((p.x / size.width).coerceIn(0f, 1f), 1f - (p.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        1f - (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 12.5f.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
    }
}

/** 색상 띠 — 0°~360° 무지개 그라디언트 위에 동그란 손잡이. */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val stops = remember { (0..12).map { Color.hsv(it * 30f % 360f, 1f, 1f) } }
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { p -> onChange((p.x / size.width).coerceIn(0f, 1f) * 360f) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(stops))
        val x = (hue / 360f) * size.width
        val r = size.height / 2f - 2.dp.toPx()
        drawCircle(Color.White, radius = r, center = Offset(x, size.height / 2f))
        drawCircle(Color.hsv(hue, 1f, 1f), radius = r - 3.dp.toPx(), center = Offset(x, size.height / 2f))
    }
}

/** 프리셋 밖의 색을 고르는 스와치 — 무지개 원. 사용자가 고른 색이 있으면 그 색으로, 선택 중이면 체크. */
@Composable
fun CustomAccentSwatch(
    selected: Boolean,
    customColor: Color?,
    onClick: () -> Unit,
) {
    val rainbow = remember { Brush.sweepGradient((0..12).map { Color.hsv(it * 30f % 360f, 0.85f, 1f) }) }
    Box(
        modifier = Modifier.size(44.dp).pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .then(if (customColor != null) Modifier.background(customColor) else Modifier.background(rainbow))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (selected) "✓" else "+",
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                color = if (customColor == null || customColor.luminanceIsLight()) Color(0xFF1A1A1C) else Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun Color.luminanceIsLight(): Boolean = luminance() > 0.6f
