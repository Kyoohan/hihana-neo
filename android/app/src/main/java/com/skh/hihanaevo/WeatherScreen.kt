package com.skh.hihanaevo

import com.skh.hihanaevo.ui.onPageVariantColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiCard
import com.skh.hihanaevo.ui.OneUiDivider
import com.skh.hihanaevo.ui.OneUiFullScreen
import com.skh.hihanaevo.ui.OneUiLoading
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/*
 * 날씨 — 구성과 치수는 삼성 날씨(One UI) 상세 화면을 따르되, 바탕은 앱의 다른 화면과 같은 페이지 배경·카드 재질입니다
 * (화면 전체를 날씨 색으로 칠하면 앱 안에서 이 화면만 동떨어져 보였습니다). 날씨 색은 포인트로만 — 위쪽 정보 뒤의 빛무리,
 * 기온 곡선, 최고/최저 화살표에 씁니다. 강수는 늘 파란색입니다.
 *   · 위쪽 정보: 기온 70dp(보통 굵기) · 최고/최저 13sp(화살표) · 날씨 문구 20sp · 보조 줄 13sp, 왼쪽 여백 23dp
 *   · 카드: 시간별(칸 52dp: 시각 10sp · 아이콘 24dp · 강수 물방울+10sp · 기온 12sp · 곡선 50dp) · 요일별(요일 15sp ·
 *     강수 10sp · 아이콘 24dp · 최고/최저 15sp) · 지표 타일(최소 90dp: 제목 12sp · 값 24sp · 아이콘 34dp)
 */

private val Rain = Color(0xFF3D8BFF)
private val HourWidth = 52.dp
private val SideInset = 23.dp

/** 날씨 종류의 포인트 색 — 아이콘의 주조색과 같게. */
private fun accentFor(kind: SeoulWeather.Kind): Color = when (kind) {
    SeoulWeather.Kind.CLEAR -> Color(0xFFFFB224)
    SeoulWeather.Kind.PARTLY -> Color(0xFFFFC25C)
    SeoulWeather.Kind.CLOUDY, SeoulWeather.Kind.FOG -> Color(0xFF9AAABF)
    SeoulWeather.Kind.RAIN -> Rain
    SeoulWeather.Kind.SNOW -> Color(0xFF8CCBFF)
    SeoulWeather.Kind.STORM -> Color(0xFF8A7CF0)
}

private fun deg(value: Double?) = value?.let { "${it.roundToInt()}°" } ?: "–"

/** "밤 9시" / "오전 7시" / "낮 12시". */
private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "자정"
    hour < 12 -> "오전 ${hour}시"
    hour == 12 -> "낮 12시"
    hour < 18 -> "오후 ${hour - 12}시"
    else -> "밤 ${hour - 12}시"
}

/**
 * 날씨 — 홈 날씨 칸·모든 기능의 날씨·귀가 기간 날씨 칸을 누르면 여는 화면(평소 하나고, 귀가 기간 서울). 지금 날씨를 크게, 그 아래 귀교 시각([until], 귀교일 밤 9시)까지
 * 1시간 단위 예보와 요일별 예보, 귀교 길 지표. [until] 이 없거나 예보 범위 밖이면 받은 예보 끝까지.
 */
@Composable
fun SeoulWeatherScreen(until: LocalDateTime?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var forecast by remember { mutableStateOf<SeoulWeather.Forecast?>(null) }
    var failed by remember { mutableStateOf(false) }
    // 귀교 시각까지 보는 화면(until)은 귀가 기간이라 서울, 그 밖에는 지금 있는 곳(평소 하나고).
    val place = remember(until) { if (until != null) SeoulWeather.Place.SEOUL else SeoulWeather.currentPlace() }
    LaunchedEffect(place) {
        forecast = SeoulWeather.cached(context, place)
        // 30분이 지났으면 새로 받습니다(실패하면 캐시 그대로).
        SeoulWeather.get(context, place)?.let { forecast = it }
        failed = forecast == null
    }
    val now = remember { LocalDateTime.now(PlanStore.seoulZone) }
    val today = now.toLocalDate()
    val f = forecast
    val hourStart = now.truncatedTo(ChronoUnit.HOURS)
    val hours = f?.hours.orEmpty().filter { !it.time.isBefore(hourStart) && (until == null || !it.time.isAfter(until)) }
    val code = f?.currentCode ?: hours.firstOrNull()?.code
    val accent = accentFor(SeoulWeather.kind(code))

    OneUiFullScreen(title = place.label, onDismiss = onDismiss) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding(), bottom = toolbar.calculateBottomPadding() + 28.dp),
        ) {
            when {
                f == null && !failed -> Row(Modifier.padding(horizontal = SideInset, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    OneUiLoading(size = 18.dp, stroke = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("날씨를 불러오는 중", style = MaterialTheme.typography.bodyMedium)
                }
                f == null -> Text(
                    "날씨를 불러오지 못했습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onPageVariantColor(),
                    modifier = Modifier.padding(horizontal = SideInset, vertical = 24.dp),
                )
                else -> {
                    TopInfo(f, today, now, hours, until, accent)
                    Spacer(Modifier.height(22.dp))
                    Column(Modifier.padding(horizontal = OneUi.PagePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hours.isNotEmpty()) {
                            OneUiCard(contentPadding = PaddingValues(0.dp)) {
                                SectionLabel("시간별", start = 20.dp)
                                HourlyStrip(hours, now, until, accent)
                                if (until != null && hours.last().time.isBefore(until)) {
                                    Text(
                                        "그 뒤는 아직 시간별 예보가 없어 아래 요일별 예보를 봐 주세요",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onPageVariantColor(),
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                                    )
                                } else {
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                        val lastDay = until?.toLocalDate() ?: f.days.lastOrNull()?.date
                        val days = f.days.filter { !it.date.isBefore(today) && (lastDay == null || !it.date.isAfter(lastDay)) }
                        if (days.isNotEmpty()) {
                            OneUiCard(contentPadding = PaddingValues(0.dp)) {
                                SectionLabel("요일별", start = 22.dp)
                                days.forEachIndexed { i, d ->
                                    if (i > 0) OneUiDivider(startIndent = 22.dp)
                                    DayRow(d, today, isReturn = d.date == until?.toLocalDate())
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        IndexTiles(f, today, hours, until)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        listOfNotNull(
                            if (f.source?.startsWith("kma") != false) "기상청 단기예보" else "Open-Meteo (기상청 장애 중 대신)",
                            f.updated?.atZone(ZoneId.of("Asia/Seoul"))?.let { "%02d:%02d 기준".format(it.hour, it.minute) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = onPageVariantColor(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, start: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = onPageVariantColor(),
        modifier = Modifier.padding(start = start, top = 14.dp, end = 20.dp, bottom = 4.dp),
    )
}

/** 위쪽 정보 — 날씨 색 빛무리 위에 큰 기온, 최고/최저(화살표), 날씨 문구, 비 소식·귀교 줄. 아이콘은 오른쪽에 크게. */
@Composable
private fun TopInfo(f: SeoulWeather.Forecast, today: LocalDate, now: LocalDateTime, hours: List<SeoulWeather.Hour>, until: LocalDateTime?, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val code = f.currentCode ?: hours.firstOrNull()?.code
    val day = f.days.firstOrNull { it.date == today }
    val firstWet = hours.drop(1).firstOrNull { SeoulWeather.wet(it.code, it.pop) }
    Row(
        Modifier
            .fillMaxWidth()
            // 날씨 색은 아이콘 뒤에서 번지는 빛으로만 — 화면 전체를 칠하지 않습니다.
            .drawBehind {
                // 빛무리는 이 영역 안에서 다 사라지도록 반지름을 가장자리까지 거리보다 작게 — 안 그러면 네모로 잘려 보입니다.
                val center = Offset(size.width * 0.74f, size.height * 0.46f)
                val radius = minOf(size.width - center.x, center.y, size.height - center.y)
                drawRect(
                    Brush.radialGradient(
                        0f to accent.copy(alpha = if (scheme.isDarkScheme()) 0.34f else 0.36f),
                        0.6f to accent.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                )
            }
            .padding(start = SideInset, end = SideInset, top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                deg(f.currentTemp ?: hours.firstOrNull()?.temp),
                fontSize = 70.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 75.sp,
                letterSpacing = (-1).sp,
            )
            if (day?.max != null && day.min != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    TempArrow(up = true, color = accent)
                    Spacer(Modifier.width(2.dp))
                    Text(deg(day.max), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(" / ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = onPageVariantColor())
                    TempArrow(up = false, color = Rain)
                    Spacer(Modifier.width(2.dp))
                    Text(deg(day.min), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(SeoulWeather.label(code), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
            val extra = when {
                SeoulWeather.wet(code, null) -> "지금 ${SeoulWeather.label(code)} · 우산 챙기세요"
                firstWet != null -> {
                    val dayWord = when (firstWet.time.toLocalDate()) {
                        today -> ""
                        today.plusDays(1) -> "내일 "
                        else -> "${firstWet.time.monthValue}/${firstWet.time.dayOfMonth} "
                    }
                    "$dayWord${firstWet.time.hour}시부터 ${SeoulWeather.label(firstWet.code).takeIf { SeoulWeather.wet(firstWet.code, null) } ?: "비 소식"}"
                }
                else -> null
            }
            extra?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Rain, modifier = Modifier.padding(top = 2.dp)) }
            Text(
                until?.let { "귀교 ${it.monthValue}/${it.dayOfMonth}(${it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)}) ${hourLabel(it.hour)}까지" }
                    ?: "${now.monthValue}월 ${now.dayOfMonth}일 ${now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = onPageVariantColor(),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            painterResource(SeoulWeather.icon(code)),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.padding(top = 10.dp).size(92.dp),
        )
    }
}

private fun androidx.compose.material3.ColorScheme.isDarkScheme(): Boolean = background.luminance() < 0.5f

/** 최고/최저 앞의 가는 화살표 — 삼성 날씨의 temp_high/low 자리. */
@Composable
private fun TempArrow(up: Boolean, color: Color) {
    Canvas(Modifier.size(width = 9.dp, height = 14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val cx = w / 2f
        drawLine(color, Offset(cx, h * 0.12f), Offset(cx, h * 0.88f), stroke, StrokeCap.Round)
        val headY = if (up) h * 0.12f else h * 0.88f
        val dy = if (up) h * 0.32f else -h * 0.32f
        drawLine(color, Offset(cx, headY), Offset(cx - w * 0.42f, headY + dy), stroke, StrokeCap.Round)
        drawLine(color, Offset(cx, headY), Offset(cx + w * 0.42f, headY + dy), stroke, StrokeCap.Round)
    }
}

/**
 * 1시간 단위 가로 띠 — 칸마다 위에서부터 시각(날이 바뀌면 "내일"·"10/19"), 아이콘 24dp, 강수(물방울+확률), 기온 12sp,
 * 그 아래 칸 가운데를 잇는 기온 곡선 50dp. 귀교 시각 칸은 강조색 바탕과 "귀교" 표시. 줄 높이는 글자 크기를 따라가 잘리지 않습니다.
 */
@Composable
private fun HourlyStrip(hours: List<SeoulWeather.Hour>, now: LocalDateTime, until: LocalDateTime?, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val known = hours.mapNotNull { it.temp }
    val hi = known.maxOrNull() ?: 0.0
    val lo = known.minOrNull() ?: 0.0
    val span = (hi - lo).takeIf { it > 0.5 } ?: 1.0
    val curveHeight = 50.dp
    val today = now.toLocalDate()
    val returnIndex = until?.let { u -> hours.indexOfFirst { it.time == u } } ?: -1

    fun yFor(temp: Double?): Float {
        val t = temp ?: return Float.NaN
        val top = with(density) { 8.dp.toPx() }
        val bottom = with(density) { (curveHeight - 8.dp).toPx() }
        return (bottom - ((t - lo) / span).toFloat() * (bottom - top))
    }

    Box(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp).height(IntrinsicSize.Min)) {
        if (returnIndex >= 0) {
            Box(
                Modifier
                    .padding(start = HourWidth * returnIndex)
                    .width(HourWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.primary.copy(alpha = 0.12f)),
            )
        }
        Column(Modifier.padding(vertical = 6.dp)) {
            Row {
                hours.forEachIndexed { i, h ->
                    val label = when {
                        i == 0 -> "지금"
                        i == returnIndex -> "귀교"
                        h.time.hour == 0 -> when (h.time.toLocalDate()) {
                            today.plusDays(1) -> "내일"
                            today.plusDays(2) -> "모레"
                            else -> "${h.time.monthValue}/${h.time.dayOfMonth}"
                        }
                        else -> "${h.time.hour}시"
                    }
                    val strong = i == 0 || i == returnIndex || h.time.hour == 0
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == returnIndex) scheme.primary else if (strong) scheme.onSurface else scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(HourWidth),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                hours.forEach { h ->
                    Box(Modifier.width(HourWidth).height(24.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(SeoulWeather.icon(h.code)), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                hours.forEach { h ->
                    val pop = h.pop ?: 0
                    Row(Modifier.width(HourWidth).defaultMinSize(minHeight = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (pop > 0) {
                            Icon(painterResource(R.drawable.ic_weather_drop), contentDescription = null, tint = Rain, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(1.dp))
                            Text("$pop%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Rain, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row {
                hours.forEach { h ->
                    Text(
                        deg(h.temp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(HourWidth),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // 기온 곡선 — 칸 가운데 점들을 부드러운 곡선(캣멀-롬 → 베지어)으로, 날씨 색으로 잇습니다.
            Canvas(Modifier.width(HourWidth * hours.size).height(curveHeight)) {
                val step = HourWidth.toPx()
                val points = hours.mapIndexedNotNull { i, h ->
                    val y = yFor(h.temp)
                    if (y.isNaN()) null else Offset(step * i + step / 2f, y)
                }
                if (points.size >= 2) {
                    val path = Path()
                    path.moveTo(points[0].x, points[0].y)
                    for (i in 0 until points.lastIndex) {
                        val p0 = points.getOrElse(i - 1) { points[i] }
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points.getOrElse(i + 2) { p2 }
                        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
                        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
                        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
                    }
                    drawPath(path, accent, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                }
                points.forEach { p -> drawCircle(accent, radius = 2.5.dp.toPx(), center = p) }
            }
        }
    }
}

/** 요일별 한 줄 — 요일 15sp, 강수(물방울+확률) 10sp, 아이콘 24dp, 날씨, 최고/최저 15sp. */
@Composable
private fun DayRow(d: SeoulWeather.Day, today: LocalDate, isReturn: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val name = when (d.date) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(64.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (isReturn) Text("귀교", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
        }
        Row(Modifier.width(56.dp), verticalAlignment = Alignment.CenterVertically) {
            d.pop?.takeIf { it > 0 }?.let {
                Icon(painterResource(R.drawable.ic_weather_drop), contentDescription = null, tint = Rain, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(2.dp))
                Text("$it%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Rain, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Icon(painterResource(SeoulWeather.icon(d.code)), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(SeoulWeather.label(d.code), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.width(56.dp))
        Spacer(Modifier.weight(1f))
        Text(deg(d.max), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
        Text(" / ", fontSize = 15.sp, color = scheme.onSurfaceVariant)
        Text(deg(d.min), fontSize = 15.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
    }
}

/** 지표 타일 — 귀교 시각 날씨, 돌아오는 길 강수, 오늘 최고/최저 (귀가 기간이 아니면 최대 강수확률). 삼성 날씨 지표 카드의 꼴. */
@Composable
private fun IndexTiles(f: SeoulWeather.Forecast, today: LocalDate, hours: List<SeoulWeather.Hour>, until: LocalDateTime?) {
    val scheme = MaterialTheme.colorScheme
    data class Tile(val title: String, val value: String, val sub: String?, val icon: Int?, val valueColor: Color?)
    val tiles = buildList {
        until?.let { u ->
            val at = f.hours.firstOrNull { it.time == u }
            val day = f.days.firstOrNull { it.date == u.toLocalDate() }
            if (at != null) add(Tile("귀교 ${hourLabel(u.hour)}", deg(at.temp), listOfNotNull(SeoulWeather.label(at.code), at.pop?.takeIf { it > 0 }?.let { "강수 $it%" }).joinToString(" · "), SeoulWeather.icon(at.code), null))
            else if (day != null) add(Tile("귀교일", "${deg(day.max)} / ${deg(day.min)}", SeoulWeather.label(day.code), SeoulWeather.icon(day.code), null))
            val from = (u.hour - 6).coerceAtLeast(0)
            val travel = f.hours.filter { it.time.toLocalDate() == u.toLocalDate() && it.time.hour in from..u.hour }
            val pop = travel.mapNotNull { it.pop }.maxOrNull() ?: day?.pop
            if (pop != null) {
                val wet = travel.any { SeoulWeather.wet(it.code, null) } || pop >= 50
                add(Tile("돌아오는 길 강수", "$pop%", if (wet) "우산 챙기세요" else "${from}–${u.hour}시 최대", null, Rain))
            }
        }
        f.days.firstOrNull { it.date == today }?.let { d ->
            if (d.max != null && d.min != null) add(Tile("오늘 최고 / 최저", "${deg(d.max)} / ${deg(d.min)}", d.pop?.takeIf { it > 0 }?.let { "강수 $it%" }, null, null))
        }
        val maxPop = hours.mapNotNull { it.pop }.maxOrNull()
        if (maxPop != null && until == null) add(Tile("최대 강수확률", "$maxPop%", "앞으로 ${hours.size}시간", null, Rain))
    }
    if (tiles.isEmpty()) return
    tiles.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { t ->
                OneUiCard(
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 90.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
                ) {
                    Text(t.title, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        t.icon?.let {
                            Icon(painterResource(it), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(t.value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = t.valueColor ?: scheme.onSurface, maxLines = 1)
                    }
                    t.sub?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
