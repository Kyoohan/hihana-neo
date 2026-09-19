package com.yhjang.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiDialog
import com.yhjang.timetable.ui.OneUiDialogButton
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.isDark
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * 도서관 좌석 신청 — 포털 신청 페이지 대신 앱 안에서 그립니다. 위에서 타임을 고르면 그 타임의 좌석 배치도가
 * 포털이 주는 격자 좌표(srt_x/srt_y) 그대로 나오고, 빈 자리를 누르면 확인 뒤 신청, 내 자리를 누르면 취소합니다.
 * 신청 규칙(같은 저녁에 면학실을 잡았으면 도서관 불가 등)은 서버 문구를 그대로 보여줍니다.
 */
@Composable
fun LibraryApplyScreen(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slots by remember { mutableStateOf<List<LibrarySlot>>(emptyList()) }
    var slotId by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<LibrarySeatMap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<LibrarySeat?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun loadMap() {
        val id = slotId ?: return
        loading = true
        error = null
        map = try {
            HanaLibraryApi.seatMap(context, id)
        } catch (e: Exception) {
            error = e.message ?: "좌석을 불러오지 못했습니다"
            null
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        slots = runCatching { HanaLibraryApi.slots(context) }.getOrDefault(emptyList())
        // 오늘이 주말이면 주말 타임을, 아니면 평일 타임을 먼저 고릅니다.
        val weekend = PlanStore.today().dayOfWeek.value >= 6
        slotId = slots.firstOrNull { it.label.contains("휴일") == weekend }?.id
            ?: slots.firstOrNull()?.id
        if (slotId == null) {
            error = "타임 목록을 불러오지 못했습니다"
            loading = false
        }
    }
    // 타임이 정해지거나 바뀔 때마다 그 타임의 좌석을 받습니다 (처음 고른 타임도 여기서).
    LaunchedEffect(slotId) { if (slotId != null) loadMap() }

    OneUiFullScreen(
        title = "도서관 신청",
        subtitle = slots.firstOrNull { it.id == slotId }?.label,
        onDismiss = onDismiss,
        actions = {
            IconButton(onClick = { scope.launch { loadMap() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "새로고침")
            }
        },
    ) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp),
        ) {
            // 타임 선택.
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = OneUi.PagePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                slots.forEach { slot ->
                    OneUiChip(selected = slot.id == slotId, onClick = { slotId = slot.id }, label = slot.label)
                }
            }
            Spacer(Modifier.height(12.dp))
            val current = map
            when {
                loading -> Row(Modifier.padding(OneUi.PagePadding), verticalAlignment = Alignment.CenterVertically) {
                    OneUiLoading(size = 18.dp, stroke = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("좌석을 불러오는 중", style = MaterialTheme.typography.bodyMedium)
                }
                error != null -> OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
                    Text("불러오지 못했습니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                current?.closedMessage != null -> OneUiCard(Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth()) {
                    Text("휴관", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(current.closedMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                current != null -> {
                    val mine = current.seats.firstOrNull { it.mine }
                    val free = current.seats.count { it.available }
                    Text(
                        buildString {
                            append("빈 자리 $free")
                            if (mine != null) append(" · 내 자리 ${mine.cont}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                    )
                    Spacer(Modifier.height(8.dp))
                    SeatLegend(Modifier.padding(horizontal = OneUi.PagePadding))
                    Spacer(Modifier.height(12.dp))
                    current.areas.forEachIndexed { index, area ->
                        if (index > 0) Spacer(Modifier.height(18.dp))
                        if (current.areas.size > 1) {
                            Text(
                                area.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        SeatGrid(
                            area = area,
                            onSeatTap = { seat -> if (!busy) pending = seat },
                            modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "빈 자리를 누르면 신청하고, 내 자리를 누르면 취소합니다. 3-x·4-x 는 토의실입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                    )
                }
            }
        }
    }

    pending?.let { seat ->
        val id = slotId ?: return@let
        val cancelling = seat.mine && seat.sreIdx != null
        OneUiDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = if (cancelling) "${seat.cont} 취소" else "${seat.cont} 신청",
            buttons = listOf(
                OneUiDialogButton("닫기", { if (!busy) pending = null }),
                OneUiDialogButton(if (cancelling) "취소하기" else "신청하기", {
                    if (busy) return@OneUiDialogButton
                    busy = true
                    scope.launch {
                        notice = try {
                            if (cancelling) HanaLibraryApi.cancel(context, seat.sreIdx!!)
                            else HanaLibraryApi.reserve(context, seat, id)
                        } catch (e: Exception) {
                            e.message ?: "실패했습니다"
                        }
                        busy = false
                        pending = null
                        loadMap()
                        onChanged()
                    }
                }),
            ),
        ) {
            Text(
                if (cancelling) "이 타임의 도서관 자리를 취소합니다." else "${slots.firstOrNull { it.id == id }?.label ?: ""} 도서관 ${seat.cont} 자리를 신청합니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (busy) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiLoading(size = 16.dp, stroke = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("처리 중", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    notice?.let { message ->
        OneUiDialog(
            onDismissRequest = { notice = null },
            title = "도서관 신청",
            buttons = listOf(OneUiDialogButton("확인", { notice = null })),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class SeatPalette(val free: Color, val taken: Color, val mine: Color, val blocked: Color, val label: Color, val labelOnDark: Color)

@Composable
private fun seatPalette(): SeatPalette {
    val dark = MaterialTheme.colorScheme.isDark
    return SeatPalette(
        free = if (dark) Color(0xFF2A3B2E) else Color(0xFFDCF5E3),
        taken = if (dark) Color(0xFF3A3A3D) else Color(0xFFE5E7EB),
        mine = MaterialTheme.colorScheme.primary,
        blocked = if (dark) Color(0xFF2A2A2D) else Color(0xFFF3F4F6),
        label = if (dark) Color(0xFFE5E7EB) else Color(0xFF1F2937),
        labelOnDark = Color.White,
    )
}

@Composable
private fun SeatLegend(modifier: Modifier = Modifier) {
    val palette = seatPalette()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("빈 자리" to palette.free, "신청됨" to palette.taken, "내 자리" to palette.mine).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 좌석 배치도 — 포털 격자(sro_x × sro_y) 좌표에 그대로 놓습니다. 칸이 너무 작아지지 않도록 최소 34dp 로 잡고,
 * 그보다 넓어지면 가로로 스크롤합니다. 표지·블록(srt_type 2·3)은 연한 칸에 이름만 적습니다.
 */
@Composable
private fun SeatGrid(area: LibraryArea, onSeatTap: (LibrarySeat) -> Unit, modifier: Modifier = Modifier) {
    val map = area
    val palette = seatPalette()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minCell = 34.dp
        val fit = maxWidth / map.gridX.coerceAtLeast(1)
        val cell = if (fit < minCell) minCell else fit
        val cellPx = with(density) { cell.toPx() }
        val gap = with(density) { 2.dp.toPx() }
        val labelPx = with(density) { 10.sp.toPx() }
        val byCell = remember(map) { map.seats.associateBy { it.x to it.y } }
        val textPaint = remember(labelPx) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = labelPx
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier
                    .width(cell * map.gridX)
                    .height(cell * map.gridY)
                    .pointerInput(map) {
                        detectTapGestures { p ->
                            val x = floor(p.x / cellPx).toInt()
                            val y = floor(p.y / cellPx).toInt()
                            val seat = byCell[x to y] ?: return@detectTapGestures
                            if (seat.isSeat && (seat.available || seat.mine)) onSeatTap(seat)
                        }
                    },
            ) {
                map.seats.forEach { seat ->
                    if (seat.x < 0 || seat.y < 0) return@forEach
                    val left = seat.x * cellPx + gap
                    val top = seat.y * cellPx + gap
                    val size = Size(cellPx - gap * 2, cellPx - gap * 2)
                    val fill = when {
                        !seat.isSeat -> palette.blocked
                        seat.mine -> palette.mine
                        seat.available -> palette.free
                        else -> palette.taken
                    }
                    drawRoundRect(fill, Offset(left, top), size, CornerRadius(with(density) { 6.dp.toPx() }))
                    if (seat.available) {
                        drawRoundRect(
                            palette.mine.copy(alpha = 0.35f), Offset(left, top), size,
                            CornerRadius(with(density) { 6.dp.toPx() }), style = Stroke(with(density) { 1.dp.toPx() }),
                        )
                    }
                    val label = seat.cont.takeIf { it.isNotBlank() } ?: return@forEach
                    textPaint.color = (if (seat.mine) palette.labelOnDark else palette.label).let { c ->
                        android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                    }
                    textPaint.alpha = if (!seat.isSeat || (!seat.available && !seat.mine)) 140 else 255
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        left + size.width / 2f,
                        top + size.height / 2f + labelPx * 0.35f,
                        textPaint,
                    )
                }
            }
        }
    }
}
