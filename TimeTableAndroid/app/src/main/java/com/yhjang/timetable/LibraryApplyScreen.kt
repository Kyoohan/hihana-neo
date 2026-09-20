package com.yhjang.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiButtonStyle
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiLiquidGlassBox
import com.yhjang.timetable.ui.isDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * 도서관 좌석 신청 — 포털 신청 페이지 대신 앱 안에서 그립니다. 위에서 타임을 고르면 그 타임의 좌석 배치도가
 * 포털이 주는 격자 좌표(srt_x/srt_y) 그대로 나오고, 빈 자리를 누르면 확인 뒤 신청, 내 자리를 누르면 취소합니다.
 * 신청 규칙(같은 저녁에 면학실을 잡았으면 도서관 불가 등)은 서버 문구를 그대로 보여줍니다.
 */
@Composable
fun LibraryApplyScreen(service: SeatService, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slots by remember { mutableStateOf<List<LibrarySlot>>(emptyList()) }
    var slotId by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<LibrarySeatMap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // 팝업이 사라지는 애니메이션 동안에도 글자가 남아 있도록 마지막 문구를 따로 둡니다.
    var noticeShown by remember { mutableStateOf("") }
    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        noticeShown = current
        delay(2600)
        if (notice == current) notice = null
    }
    var favoriteArea by remember { mutableStateOf(SeatFavoriteStore.get(context, service)) }
    // 신청 기기 — 포털은 마지막으로 등록한 기기에서만 신청을 받습니다. 상태를 보여주고 버튼으로 이 기기를 등록합니다.
    var deviceRegistered by remember { mutableStateOf<Boolean?>(null) }
    var registering by remember { mutableStateOf(false) }
    suspend fun refreshDevice() { deviceRegistered = HanaLibraryApi.isDeviceRegistered(context, service) }

    suspend fun loadMap() {
        val id = slotId ?: return
        loading = true
        error = null
        map = try {
            HanaLibraryApi.seatMap(context, id, service)
        } catch (e: Exception) {
            error = e.message ?: "좌석을 불러오지 못했습니다"
            map
        }
        loading = false
    }

    /** 좌석을 누르면 확인 없이 바로 — 빈 자리는 신청, 내 자리는 취소. 결과 문구는 아래 팝업으로. */
    fun act(seat: LibrarySeat) {
        val id = slotId ?: return
        val cancelling = seat.mine && seat.sreIdx != null
        busy = true
        scope.launch {
            notice = try {
                if (cancelling) HanaLibraryApi.cancel(context, seat.sreIdx!!, service)
                else HanaLibraryApi.reserve(context, seat, id, service)
            } catch (e: Exception) {
                e.message ?: "실패했습니다"
            }
            busy = false
            loadMap()
            runCatching { refreshDevice() }
            onChanged()
        }
    }

    LaunchedEffect(Unit) {
        slots = runCatching { HanaLibraryApi.slots(context, service) }.getOrDefault(emptyList())
        runCatching { refreshDevice() }
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
        title = "${service.label} 신청",
        subtitle = slots.firstOrNull { it.id == slotId }?.label,
        onDismiss = onDismiss,
        actions = {
            IconButton(onClick = { scope.launch { loadMap() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "새로고침")
            }
        },
        // 팝업은 콘텐츠의 haze 소스 안에 두면 유리가 아무것도 못 비춰(자기 소스 안의 효과는 제외됨) 글자만 떴습니다.
        overlay = { toolbar ->
        // 결과는 아래쪽 알약 팝업으로 잠깐 보여줍니다 (다이얼로그 없이).
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = toolbar.calculateBottomPadding() + 24.dp)
                .padding(horizontal = OneUi.PagePadding),
        ) {
            // 헤더 섬과 같은 액체 유리(서리 + 무지개 림 + 반사광) 알약.
            OneUiLiquidGlassBox(cornerRadius = 22.dp, strength = 0.8f) {
                Text(
                    noticeShown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                )
            }
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
            Spacer(Modifier.height(10.dp))
            // 신청 기기 상태 줄.
            Row(
                Modifier.padding(horizontal = OneUi.PagePadding).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (deviceRegistered) {
                        true -> "신청 기기: 이 기기로 등록됨"
                        false -> "신청 기기: 다른 기기로 등록되어 있음"
                        null -> "신청 기기: 확인 중"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deviceRegistered == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (deviceRegistered != true) {
                    OneUiButton(
                        text = if (registering) "등록 중" else "이 기기로 등록",
                        onClick = {
                            if (registering) return@OneUiButton
                            registering = true
                            scope.launch {
                                notice = try {
                                    HanaLibraryApi.registerDevice(context, service)
                                    "이 기기를 신청 기기로 등록했습니다"
                                } catch (e: Exception) {
                                    e.message ?: "등록하지 못했습니다"
                                }
                                runCatching { refreshDevice() }
                                registering = false
                            }
                        },
                        style = OneUiButtonStyle.Neutral,
                        compact = true,
                        enabled = !registering,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val current = map
            when {
                loading && current == null -> Row(Modifier.padding(OneUi.PagePadding), verticalAlignment = Alignment.CenterVertically) {
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
                            if (mine != null) append(" · 내 자리 ${mine.cont}").also { if (mine.assigned) append(" (지정석)") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                    )
                    Spacer(Modifier.height(8.dp))
                    SeatLegend(Modifier.padding(horizontal = OneUi.PagePadding))
                    Spacer(Modifier.height(12.dp))
                    // 즐겨찾기한 구역이 맨 위로 — 별을 누르면 그 구역이 즐겨찾기가 되고(하나만), 다시 누르면 해제.
                    val ordered = current.areas.sortedByDescending { it.label == favoriteArea }
                    ordered.forEachIndexed { index, area ->
                        if (index > 0) Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = OneUi.PagePadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(area.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val starred = area.label == favoriteArea
                            // 별은 이름 바로 옆, 즐겨찾기면 강조색과 상관없이 노란색.
                            IconButton(
                                onClick = {
                                    favoriteArea = if (starred) null else area.label
                                    SeatFavoriteStore.set(context, service, favoriteArea)
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = if (starred) "즐겨찾기 해제" else "즐겨찾기",
                                    tint = if (starred) Color(0xFFFACC15)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        SeatGrid(
                            area = area,
                            onSeatTap = { seat -> if (!busy) act(seat) },
                            modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "빈 자리를 누르면 바로 신청되고, 내 자리를 누르면 취소됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = OneUi.PagePadding),
                    )
                }
            }
        }
    }
}

/** "#efefef" 같은 CSS 16진수 색 → Compose Color (못 읽으면 null). */
private fun parseHexColor(text: String): Color? {
    val body = text.trim().removePrefix("#")
    if (body.length != 6 || body.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return Color(0xFF000000.toInt() or body.toInt(16))
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
    // 격자 구조(통로·간격)는 포털 그대로 두고, 바깥쪽의 빈 행·열만 잘라냅니다 — 제목·안내 글과 좌석 사이가
    // 비어 보이던 원인. 안쪽 빈 칸은 실제 도서관 배치(통로)라 그대로 둡니다.
    val map = remember(area) {
        val real = area.seats.filter { it.isSeat && it.x >= 0 && it.y >= 0 }
        val minX = real.minOfOrNull { it.x } ?: 0
        val minY = real.minOfOrNull { it.y } ?: 0
        val maxX = real.maxOfOrNull { it.x } ?: 0
        val maxY = real.maxOfOrNull { it.y } ?: 0
        LibraryArea(
            label = area.label,
            gridX = (maxX - minX + 1).coerceAtLeast(1),
            gridY = (maxY - minY + 1).coerceAtLeast(1),
            // 좌석 범위 안에 있는 벽·기둥 칸(색이 있는 블록)도 함께 — 2-34 와 2-35 사이의 칸막이 같은 구조가 보이게.
            seats = area.seats
                .filter { it.x in minX..maxX && it.y in minY..maxY }
                .map { it.copy(x = it.x - minX, y = it.y - minY) },
        )
    }
    val palette = seatPalette()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 칸 크기는 원래 격자 폭(10칸)을 화면에 맞춘 것보다 살짝(10%) 크게 — 잘라낸 뒤 남은 칸 수로 맞추면 너무 커집니다.
        val minCell = 34.dp
        val fit = maxWidth / area.gridX.coerceAtLeast(1) * 1.1f
        val cell = if (fit < minCell) minCell else fit
        val cellPx = with(density) { cell.toPx() }
        // 칸 높이는 두 줄(번호 + 이름)이 들어가도록 폭의 1.3배.
        val rowPx = cellPx * 1.3f
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
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .width(cell * map.gridX)
                    .height(cell * 1.3f * map.gridY)
                    .pointerInput(map) {
                        detectTapGestures { p ->
                            val x = floor(p.x / cellPx).toInt()
                            val y = floor(p.y / rowPx).toInt()
                            val seat = byCell[x to y] ?: return@detectTapGestures
                            if (seat.isSeat && (seat.available || (seat.mine && !seat.assigned))) onSeatTap(seat)
                        }
                    },
            ) {
                map.seats.forEach { seat ->
                    if (seat.x < 0 || seat.y < 0) return@forEach
                    val left = seat.x * cellPx + gap
                    val top = seat.y * rowPx + gap
                    val size = Size(cellPx - gap * 2, rowPx - gap * 2)
                    if (!seat.isSeat) {
                        // 통로 칸(숫자만 적힌 칸)은 비워 두고, 표지("입구", "토의실 A", "2F입구")는 글자만 씁니다 —
                        // 표지가 srt_type 2 로도 3 으로도 와서 종류 대신 글자로 가립니다.
                        if (seat.cont.isNotBlank() && !seat.cont.all { it.isDigit() }) {
                            textPaint.color = android.graphics.Color.argb(160, (palette.label.red * 255).toInt(), (palette.label.green * 255).toInt(), (palette.label.blue * 255).toInt())
                            textPaint.alpha = 160
                            drawContext.canvas.nativeCanvas.drawText(seat.cont, left + size.width / 2f, top + size.height / 2f + labelPx * 0.35f, textPaint)
                        }
                        return@forEach
                    }
                    val fill = when {
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
                    textPaint.alpha = if (!seat.available && !seat.mine) 170 else 255
                    // 두 번째 줄: 신청한 사람 — 이름(없으면 성별), 내 자리는 이름. 포털 JSON 의 sre_mem_name 을 그대로 씁니다.
                    // 이름 → (도서관처럼 이름을 비워 보내면) 면학실에서 배운 이름 → 학번 → 성별 순.
                    val who = when {
                        seat.mine -> seat.memberName ?: "나"
                        seat.sreIdx != null -> seat.memberName ?: seat.studentNumber ?: seat.gender
                        else -> null
                    }
                    val cx = left + size.width / 2f
                    val cy = top + size.height / 2f
                    if (who == null) {
                        drawContext.canvas.nativeCanvas.drawText(label, cx, cy + labelPx * 0.35f, textPaint)
                    } else {
                        // 번호 + 이름 두 줄을 한 덩어리로 보고 칸 세로 가운데에 놓습니다 (줄 간격 = 글자 크기 × 1.15).
                        val lineGap = labelPx * 1.15f
                        drawContext.canvas.nativeCanvas.drawText(label, cx, cy - lineGap / 2f + labelPx * 0.35f, textPaint)
                        val prevSize = textPaint.textSize
                        val prevColor = textPaint.color
                        val prevAlpha = textPaint.alpha
                        // 이름은 성별 색으로 — 남 파랑, 여 분홍 (내 자리는 흰 바탕 대비를 위해 그대로).
                        if (!seat.mine) {
                            when (seat.gender) {
                                "남" -> textPaint.color = android.graphics.Color.rgb(96, 165, 250)
                                "여" -> textPaint.color = android.graphics.Color.rgb(244, 114, 182)
                            }
                            textPaint.alpha = 255
                        }
                        textPaint.textSize = labelPx * 0.9f
                        drawContext.canvas.nativeCanvas.drawText(who.take(5), cx, cy + lineGap / 2f + labelPx * 0.35f, textPaint)
                        textPaint.textSize = prevSize
                        textPaint.color = prevColor
                        textPaint.alpha = prevAlpha
                    }
                }
            }
        }
    }
}

/** 서비스별 즐겨찾기 구역(하나) — 구역 이름으로 저장합니다. */
object SeatFavoriteStore {
    private const val PREFS = "seat_favorites"
    private fun prefs(context: android.content.Context) =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun get(context: android.content.Context, service: SeatService): String? = prefs(context).getString(service.name, null)

    fun set(context: android.content.Context, service: SeatService, area: String?) {
        prefs(context).edit().apply { if (area == null) remove(service.name) else putString(service.name, area) }.apply()
    }
}
