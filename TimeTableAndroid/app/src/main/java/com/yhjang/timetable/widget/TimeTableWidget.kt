package com.yhjang.timetable.widget

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.material3.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yhjang.timetable.Accent
import com.yhjang.timetable.Block
import com.yhjang.timetable.BlockKind
import com.yhjang.timetable.HanaAcademicRepository
import com.yhjang.timetable.HanaTimetableSync
import com.yhjang.timetable.MainActivity
import com.yhjang.timetable.Meal
import com.yhjang.timetable.PlanStore
import com.yhjang.timetable.R
import com.yhjang.timetable.Timetable
import com.yhjang.timetable.detectedAllergies
import com.yhjang.timetable.shortAllergyLabels
import com.yhjang.timetable.weekday1SupervisionTeachers
import com.yhjang.timetable.worker.WidgetRefreshWorker
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** 홈 화면 위젯 — 지금 이 순간의 면학 위치/일정과 급식 메뉴를 보여줍니다 */
class TimeTableWidget : GlanceAppWidget() {

    /**
     * 기본값인 SizeMode.Single은 LocalSize가 appwidget-provider의 minWidth/minHeight를
     * 고정 반환해서 실제 크기(2x2 등)를 알 수 없습니다. Exact로 두면 런처가 알려주는
     * 실제 크기가 들어와 높이 기반 레이아웃 분기가 동작합니다.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val opacity = runCatching { PlanStore.homeWidgetOpacity(context) }.getOrDefault(60)
        val theme = runCatching { PlanStore.homeWidgetTheme(context) }.getOrDefault(PlanStore.THEME_SYSTEM)
        val isDark = isDarkTheme(context, theme)
        // 홈 화면 월페이퍼의 대표색 — 위젯 바탕에 옅게 섞어 블러된 배경처럼 보이게 합니다.
        val wallpaperTint = wallpaperPrimaryColor(context)

        // 위젯 프로세스가 새로 떴을 때도 캐시된 포털 시간표를 쓰도록 먼저 설치합니다.
        runCatching { HanaTimetableSync.ensureInstalled(context) }

        val snapshot = runCatching {
            val today = PlanStore.today()
            val places = PlanStore.placesForToday(context, today)
            val meals = PlanStore.meals(context, today)
            val allergens = PlanStore.mealAllergens(context, today)
            val allergyCodes = PlanStore.allergyCodes(context)
            val now = LocalDateTime.now(PlanStore.seoulZone)
            val block = Timetable.blockAt(today, now) { places[it] }
            val next = Timetable.blocks(today) { places[it] }
                .firstOrNull { it.start.isAfter(now) && !it.isBlank }

            // 지금이 급식 "표시 창"(아침·점심·저녁은 종료 10분 전까지, 간식은 전체) 안이면
            // 그 끼니 메뉴를 보여주고, 아니면 아래에서 다음 일정으로 넘어갑니다.
            val activeMeal = block?.let { activeMeal(it, now) }
            // 위젯은 사진 없이 텍스트만 — 끼니 구분 없이 전체 메뉴를 넘기고 레이아웃이 줄 수로 잘라 냅니다.
            val mealText = activeMeal?.let { meals[it.key] }?.takeIf { it.isNotBlank() }
            val mealAllergyPrefix = mealText?.let {
                allergyPrefix(activeMeal?.let { m -> allergens[m.key] }.orEmpty(), it, allergyCodes)
            }.orEmpty()
            val boundary = block?.let { displayBoundary(it, now, activeMeal) }

            // 면학감독은 급식 메뉴 창이 18:50에 닫혀도 1타임 시작(19:00)까지 유지해야 하므로
            // 저녁 창(17:50~19:00) 전체를 기준으로 채웁니다. 학사일정 캐시만 읽고
            // 네트워크는 타지 않으므로 컴포지션이 막히지 않습니다.
            val supervisionText = if (isDinnerWindow(block)) {
                val teachers = weekday1SupervisionTeachers(
                    HanaAcademicRepository.cachedSchedule(context),
                    today,
                    PlanStore.studentGrade(context),
                )
                teachers.joinToString(" ").takeIf { it.isNotEmpty() }?.let { "면학감독 $it" }
            } else {
                null
            }

            WidgetSnapshot(
                block = block,
                nextTitle = next?.title,
                // 쉬는 시간(GapKind)엔 히어로가 이미 다음 교실을 보여주므로 '다음' 줄에서는 위치를 뺍니다
                nextRoom = if (block?.kind is BlockKind.GapKind) null else next?.room,
                mealText = mealText,
                mealAllergyPrefix = mealAllergyPrefix,
                supervisionText = supervisionText,
                remainingMillis = block?.let { Duration.between(now, it.end).toMillis().coerceAtLeast(0) },
                refreshDelayMillis = boundary?.let { Duration.between(now, it).toMillis() } ?: -1L,
            )
        }.getOrNull()

        // 메뉴↔다음 일정 전환 시각(또는 블록 종료 시각)에 위젯을 다시 그립니다.
        scheduleBoundaryRefresh(context, snapshot?.refreshDelayMillis ?: -1L)

        // 진행 막대를 1분 간격으로 갱신합니다. 보여줄 블록이 없으면 틱을 멈춥니다.
        if (snapshot?.block?.let { !it.isBlank } == true) {
            WidgetTickReceiver.schedule(context)
        } else {
            WidgetTickReceiver.cancel(context)
        }

        // Chronometer는 Glance 컴포저블로 그릴 수 없어 RemoteViews를 미리 만들어 넘깁니다.
        // 위젯은 앱 강조색과 무관하게, 블록 성격(수업/면학/장소/시간)에 따른 색을 그대로 씁니다.
        val countdownViews = snapshot?.let { snap ->
            val remaining = snap.remainingMillis
            val blockAccent = snap.block?.let { accentHex.getValue(it.accent).toInt() }
            if (remaining != null && blockAccent != null) {
                countdownRemoteViews(context, remaining, blockAccent)
            } else {
                null
            }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    block = snapshot?.block,
                    opacityPercent = opacity,
                    isDark = isDark,
                    wallpaperTint = wallpaperTint,
                    nextTitle = snapshot?.nextTitle,
                    nextRoom = snapshot?.nextRoom,
                    mealText = snapshot?.mealText,
                    mealAllergyPrefix = snapshot?.mealAllergyPrefix.orEmpty(),
                    supervisionText = snapshot?.supervisionText,
                    countdownViews = countdownViews,
                )
            }
        }
    }
}

/** 위젯이 한 번 그릴 때 필요한 값 묶음 */
private data class WidgetSnapshot(
    val block: Block?,
    val nextTitle: String?,
    val nextRoom: String?,
    val mealText: String?,
    val mealAllergyPrefix: String,
    /** 저녁 창(17:50~19:00) 내내 채워지는 1타임 면학감독 한 줄 — 그 외에는 null. */
    val supervisionText: String?,
    val remainingMillis: Long?,
    val refreshDelayMillis: Long,
)

/** 지금 구간이 급식 시간대이고 아직 메뉴를 보여줄 창 안이면 해당 끼니를 돌려줍니다 */
private fun activeMeal(block: Block, now: LocalDateTime): Meal? {
    val kind = block.kind as? BlockKind.GapKind ?: return null
    val meal = Meal.forGapLabel(kind.gap.label) ?: return null
    val menuEnd = if (meal == Meal.SNACK) block.end else block.end.minusMinutes(10)
    return meal.takeIf { !now.isAfter(menuEnd) }
}

/**
 * 지금이 저녁 창(17:50~19:00)인지 — 급식 메뉴 창은 18:50에 일찍 닫히지만
 * 면학감독 줄은 1타임 시작(19:00)까지 유지해야 해서 급식 창과 별도로 판정합니다.
 * blockAt 이 현재 시각을 포함하는 블록을 주므로 저녁 gap 인지로 충분합니다.
 */
private fun isDinnerWindow(block: Block?): Boolean {
    val kind = block?.kind as? BlockKind.GapKind ?: return false
    return kind.gap.accent == Accent.DINNER
}

/** 다음 리프레시 시각 — 메뉴 표시 창에서는 종료 10분 전, 그 외에는 구간 종료 시각 */
private fun displayBoundary(block: Block, now: LocalDateTime, meal: Meal?): LocalDateTime? {
    val target = if (meal != null && meal != Meal.SNACK) block.end.minusMinutes(10) else block.end
    return target.takeIf { it.isAfter(now) }
}

/** 위젯용 짧은 경고 접두사 — 선택된 알레르기만, 견과는 묶어 가운뎃점으로 구분합니다 */
private fun allergyPrefix(codes: Set<Int>, menuText: String, selected: Set<Int>): String {
    val labels = shortAllergyLabels(detectedAllergies(codes, menuText, selected))
    return if (labels.isEmpty()) "" else "⚠ ${labels.joinToString("·")} "
}

/** 위젯 탭 → MainActivity 를 열면서 열 탭을 extra(open_tab)로 전달합니다. */
private fun openTabAction(tab: String): Action =
    actionStartActivity<MainActivity>(
        actionParametersOf(ActionParameters.Key<String>(MainActivity.EXTRA_OPEN_TAB) to tab),
    )

/** MM:SS 카운트다운 — Chronometer는 RemoteViews로만 만들 수 있어 별도 레이아웃을 씁니다 */
private fun countdownRemoteViews(
    context: Context,
    remainingMillis: Long,
    accentArgb: Int,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_countdown)
    val id = R.id.widget_countdown
    views.setTextColor(id, accentArgb)
    views.setChronometerCountDown(id, true)
    // 라벨 없이 남은 시간만 "%s"(MM:SS) 로 표시하고, 글자색은 고른 강조 색을 씁니다.
    views.setChronometer(id, SystemClock.elapsedRealtime() + remainingMillis, "%s", true)
    return views
}

@Composable
private fun WidgetContent(
    block: Block?,
    opacityPercent: Int,
    isDark: Boolean,
    wallpaperTint: Int?,
    nextTitle: String?,
    nextRoom: String?,
    mealText: String?,
    mealAllergyPrefix: String,
    supervisionText: String?,
    countdownViews: RemoteViews?,
) {
    // 슬라이더 값을 라이트/다크 모두 그대로 반영합니다.
    val alphaInt = (opacityPercent * 255 / 100).coerceIn(0, 255)
    // 위젯은 월페이퍼를 실제로 블러할 수 없어, 월페이퍼의 대표색을 흰/검 바탕에 옅게 섞어 "뒤가 비치는"
    // 헤이즈 느낌을 냅니다 (앱의 글래스 요소와 같은 인상). 대표색을 못 읽으면 중성 흰/검 그대로입니다.
    val base = if (isDark) 0x000000 else 0xFFFFFF
    val tinted = wallpaperTint?.let { blendRgb(base, it and 0xFFFFFF, if (isDark) 0.22f else 0.14f) } ?: base
    val surface = Color((tinted shr 16) and 0xFF, (tinted shr 8) and 0xFF, tinted and 0xFF, alphaInt)

    // light/dark 슬롯에 같은 스킴을 넣어 시스템 테마와 무관하게 선택된 테마로 고정합니다.
    val scheme = if (isDark) {
        darkColorScheme(
            surface = surface,
            onSurface = Color(0xFF, 0xFF, 0xFF),
            onSurfaceVariant = Color(0xE2, 0xE8, 0xF0),
            outline = Color(0xFF, 0xFF, 0xFF, 0x38),
        )
    } else {
        lightColorScheme(
            surface = surface,
            onSurface = Color(0x0F, 0x17, 0x2A),
            onSurfaceVariant = Color(0x47, 0x55, 0x69),
            outline = Color(0x00, 0x00, 0x00, 0x26),
        )
    }

    val colors = ColorProviders(light = scheme, dark = scheme)

    GlanceTheme(colors = colors) {
        // 실제 배경(월페이퍼)을 가우시안 블러하는 건 RemoteViews/AppWidget 특성상 불가능합니다.
        // 대신 3겹(① 틴트 ② 하이라이트 시트 ③ 그레인 노이즈)으로 프로스트 유리 질감을 흉내냅니다.
        // 예전엔 바깥에 1.5dp 밝은 림(테두리)을 한 겹 더 둘렀는데, 런처가 위젯을 시스템 라운드로 한 번 더
        // 자르면서 그 림이 모서리에서 어긋나 보였습니다(삼성 기본 위젯엔 테두리가 없음). 림을 없애고 모서리도
        // 시스템 위젯 라운드(Android 12+)를 그대로 써서 런처의 잘라내기와 정확히 겹치게 합니다.
        val corner = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            GlanceModifier.cornerRadius(24.dp)
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .then(corner)
                .clickable(openTabAction(MainActivity.TAB_TODAY)),
        ) {
            // ② 빛이 스치는 하이라이트
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_frost_sheen))
                    .then(corner),
            ) {
                // ③ 유리 표면의 미세한 그레인
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ImageProvider(R.drawable.widget_frost_noise))
                        .then(corner)
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (block != null && !block.isBlank) {
                        ScheduleContent(block, nextTitle, nextRoom, mealText, mealAllergyPrefix, supervisionText, countdownViews)
                    } else {
                        EmptyScheduleContent()
                    }
                }
            }
        }
    }
}

/** 월페이퍼 대표색(ARGB) — 못 읽으면 null. 권한 없이 읽을 수 있는 색 정보만 씁니다. */
private fun wallpaperPrimaryColor(context: Context): Int? = runCatching {
    android.app.WallpaperManager.getInstance(context)
        .getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
        ?.primaryColor?.toArgb()
}.getOrNull()

/** 두 RGB(0xRRGGBB)를 [amount](0..1) 비율로 섞습니다. */
private fun blendRgb(a: Int, b: Int, amount: Float): Int {
    fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - amount) + ((b shr shift) and 0xFF) * amount).toInt().coerceIn(0, 255)
    return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** 테마 설정(시스템/라이트/다크)을 실제 다크 여부로 변환합니다 */
internal fun isDarkTheme(context: Context, theme: String): Boolean = when (theme) {
    PlanStore.THEME_DARK -> true
    PlanStore.THEME_LIGHT -> false
    else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}

@Composable
private fun EmptyScheduleContent() {
    val titleColor = GlanceTheme.colors.onSurface
    val subtextColor = GlanceTheme.colors.onSurfaceVariant

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_hotel),
            contentDescription = null,
            colorFilter = ColorFilter.tint(subtextColor),
            modifier = GlanceModifier.size(28.dp),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "현재 일정이 없습니다",
            style = TextStyle(
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = "면학 시간 외 / 휴식 시간",
            style = TextStyle(
                color = subtextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

/**
 * 크기(2x2 / 3x1)와 급식 표시 여부로 레이아웃을 고릅니다.
 * 급식 창이 활성일 때만 메뉴 레이아웃을 쓰고, 평소에는 기존 일정 레이아웃입니다.
 */
@Composable
private fun ScheduleContent(
    block: Block,
    nextTitle: String?,
    nextRoom: String?,
    mealText: String?,
    mealAllergyPrefix: String,
    supervisionText: String?,
    countdownViews: RemoteViews?,
) {
    val tall = LocalSize.current.height >= 120.dp
    if (mealText != null) {
        if (tall) {
            MealHeroContent(block, mealText, mealAllergyPrefix, supervisionText)
        } else {
            MealCompactContent(block, mealText, mealAllergyPrefix, supervisionText)
        }
    } else {
        if (tall) {
            NormalHeroContent(block, nextTitle, nextRoom, supervisionText, countdownViews)
        } else {
            // 3x1/2x1 처럼 세로가 짧으면 면학감독 줄까지 넣기엔 공간이 부족해 생략합니다.
            NormalCompactContent(block, nextTitle, nextRoom)
        }
    }
}

/** 상단 상태 한 줄 — 수업 중이면 현재 과목명까지 함께 보여줍니다 (예: "5교시 · 미적분Ⅱ") */
private fun statusText(block: Block): String {
    val kind = block.kind
    if (kind is BlockKind.LessonKind && !kind.lesson.isFree) {
        return "${kind.period}교시 · ${kind.lesson.subject}"
    }
    // 공강처럼 교시가 상태 라벨과 겹치면 중복을 피해 교시만 보여줍니다
    if (block.periodNumber != null && block.room == null) return "${block.periodNumber}교시"
    return block.statusLabel
}

/** 상단 상태 점 + 라벨 — 2x2/3x1에서 점 크기만 달리해 재사용합니다 */
@Composable
private fun StatusRow(
    block: Block,
    dotSize: Dp,
    gap: Dp,
    fontSize: TextUnit,
    text: String,
) {
    val accent = accentColor(block.accent)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier
                .width(dotSize)
                .height(dotSize)
                .background(accent)
                .cornerRadius(dotSize / 2),
        ) {}
        Spacer(GlanceModifier.width(gap))
        Text(
            text = text,
            style = TextStyle(color = accent, fontSize = fontSize, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/** 위치(방) 히어로 한 줄 — 아이콘 + 강조 텍스트 */
@Composable
private fun HeroRow(
    block: Block,
    heroText: String,
    iconSize: Dp,
    fontSize: TextUnit,
    color: ColorProvider? = null,
) {
    val heroColor = color ?: accentColor(block.accent)
    Row(
        modifier = GlanceModifier.clickable(openTabAction(MainActivity.TAB_TODAY)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (block.iconKey.isNotEmpty()) {
            Image(
                provider = ImageProvider(iconResFor(block.iconKey)),
                contentDescription = null,
                colorFilter = ColorFilter.tint(heroColor),
                modifier = GlanceModifier.size(iconSize),
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            text = heroText,
            style = TextStyle(color = heroColor, fontSize = fontSize, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/**
 * 하단 내용 — 급식 표시 창이면 메뉴를, 아니면 다음 일정 한 줄을 보여줍니다.
 * 그 끼니 메뉴 데이터가 없을 때도 다음 일정으로 자연스럽게 넘어갑니다.
 */
@Composable
private fun MealOrNextContent(
    mealText: String?,
    nextTitle: String?,
    nextRoom: String?,
    fontSize: TextUnit,
    maxLines: Int,
    allergyPrefix: String = "",
) {
    if (mealText != null) {
        // 알레르기 종류를 구분한 짧은 경고 표식만 메뉴 앞에 덧붙입니다 (줄 수 유지).
        Text(
            text = if (allergyPrefix.isNotEmpty()) "$allergyPrefix$mealText" else mealText,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = fontSize, fontWeight = FontWeight.Medium),
            maxLines = maxLines,
            modifier = GlanceModifier.clickable(openTabAction(MainActivity.TAB_MEAL)),
        )
    } else {
        NextRow(nextTitle, nextRoom, fontSize, maxLines)
    }
}

/**
 * '다음 일정' 한 줄 — "다음" 텍스트·가운뎃점 대신 아이콘으로 구분합니다.
 * 제목이 없으면 통째로 그리지 않습니다.
 */
@Composable
private fun NextRow(nextTitle: String?, nextRoom: String?, fontSize: TextUnit, maxLines: Int = 1) {
    val title = nextTitle?.takeIf { it.isNotEmpty() } ?: return
    val room = nextRoom?.takeIf { it.isNotEmpty() }
    val subtext = GlanceTheme.colors.onSurfaceVariant
    val onSurface = GlanceTheme.colors.onSurface

    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_next),
            contentDescription = null,
            colorFilter = ColorFilter.tint(subtext),
            modifier = GlanceModifier.size(14.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        // 제목은 남는 폭 안에서 줄바꿈하고, 장소는 자기 폭을 먼저 확보합니다 — 제목에 폭 제한이 없으면 긴 과목명이
        // 한 줄로 늘어나 장소가 위젯 밖으로 밀려 잘려 보였습니다.
        Text(
            text = title,
            style = TextStyle(color = onSurface, fontSize = fontSize, fontWeight = FontWeight.Medium),
            maxLines = maxLines,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (room != null) {
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_place),
                contentDescription = null,
                colorFilter = ColorFilter.tint(subtext),
                modifier = GlanceModifier.size(12.dp),
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = room,
                style = TextStyle(color = onSurface, fontSize = fontSize, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/** 블록 경계(급식 창 전환/구간 종료)에 맞춰 위젯을 다시 그립니다 */
private fun scheduleBoundaryRefresh(context: Context, delayMillis: Long) {
    if (delayMillis <= 0) {
        WidgetTickReceiver.cancelBoundary(context)
        return
    }
    // exact alarm 권한이 있으면 전환 시각 +1초에 정확히 깨우고, 없으면 예전처럼 WorkManager 에 맡깁니다
    // (JobScheduler 가 수십 초 미룰 수 있어 그동안 카운트다운이 음수로 보일 수 있습니다).
    if (WidgetTickReceiver.scheduleBoundary(context, System.currentTimeMillis() + delayMillis + 1_000)) {
        WorkManager.getInstance(context).cancelUniqueWork(WidgetRefreshWorker.WORK_NAME)
        return
    }
    val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
        .setInitialDelay(delayMillis + 1_500, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        WidgetRefreshWorker.WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

/**
 * Glance 는 텍스트 자동 축소를 지원하지 않아, 글자별 대략적인 폭(em)을 합산해
 * 주어진 폭에 맞는 글자 크기를 역산합니다. 한글은 1em, 숫자·기호는 그보다 좁게 봅니다.
 */
private fun fitHeroFontSize(text: String, availableDp: Float): Float {
    if (text.isEmpty() || availableDp <= 0f) return 30f
    val em = text.fold(0.0) { acc, c ->
        acc + when {
            c.code in 0xAC00..0xD7A3 || c.code in 0x3131..0x318E -> 1.0 // 한글 음절/자모
            c.isWhitespace() -> 0.35
            c.isDigit() || c in ".-()·" -> 0.55
            else -> 0.6
        }
    }
    // 0.95: 폰트 메트릭 오차로 잘리지 않도록 여유를 둡니다
    return (availableDp / (em * 0.95)).toFloat().coerceIn(14f, 30f)
}

/**
 * 3x1 등 세로가 짧은 크기용(정상 일정) — 상태 → 위치 히어로 → 다음 일정 → 시간 범위.
 */
@Composable
private fun NormalCompactContent(block: Block, nextTitle: String?, nextRoom: String?) {
    val subtext = GlanceTheme.colors.onSurfaceVariant
    val heroText = block.room ?: block.title

    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatusRow(block, dotSize = 6.dp, gap = 6.dp, fontSize = 12.sp, text = statusText(block))

        Spacer(GlanceModifier.defaultWeight())

        HeroRow(
            block = block,
            heroText = heroText,
            iconSize = 18.dp,
            fontSize = 19.sp,
            color = GlanceTheme.colors.onSurface,
        )

        if (!nextTitle.isNullOrEmpty()) {
            Spacer(GlanceModifier.height(2.dp))
            NextRow(nextTitle, nextRoom, 12.sp, maxLines = ((LocalSize.current.height.value - 90f) / 16f).toInt().coerceIn(1, 3))
        }

        Spacer(GlanceModifier.defaultWeight())

        Text(
            text = block.timeRangeText,
            style = TextStyle(color = subtext, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/**
 * 3x1 등 세로가 짧은 크기용(급식 창) — 상태·위치를 한 줄로, 아래에 메뉴.
 */
@Composable
private fun MealCompactContent(block: Block, mealText: String, allergyPrefix: String, supervisionText: String?) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatusRow(
            block = block,
            dotSize = 6.dp,
            gap = 6.dp,
            fontSize = 12.sp,
            text = "${statusText(block)} · ${block.room ?: block.title}",
        )

        Spacer(GlanceModifier.defaultWeight())

        // 가로형도 높이에 여유가 있으면 메뉴 줄 수를 늘립니다 (한 줄 ≈ 18dp).
        val mealLines = ((LocalSize.current.height.value - 32f - 20f - 8f) / 18f).toInt().coerceIn(1, 4)
        MealOrNextContent(mealText = mealText, nextTitle = null, nextRoom = null, fontSize = 13.sp, maxLines = mealLines, allergyPrefix = allergyPrefix)

        if (supervisionText != null) {
            Spacer(GlanceModifier.height(2.dp))
            SupervisionLine(supervisionText, fontSize = 11.sp)
        }
    }
}

/**
 * 저녁 창에 덧붙는 면학감독 한 줄 — 메뉴/카운트다운과 구분되도록 보조 색·작은 글씨로 그립니다.
 * Glance Text 는 overflow 를 지원하지 않아 maxLines=1 로 잘라 폭을 지킵니다.
 */
@Composable
private fun SupervisionLine(text: String, fontSize: TextUnit) {
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
}

/**
 * 2x2처럼 세로 공간이 넉넉한 크기용(정상 일정) — 위치 히어로 + 다음 일정 + 카운트다운.
 * 저녁 창(18:50~19:00)에는 하단에 면학감독 한 줄이 더 붙습니다.
 *
 * Chronometer(AndroidRemoteViews)를 같은 Column 에 넣으면 defaultWeight 계산이 깨지므로,
 * 가중치가 걸린 내용은 안쪽 Column 에 두고 카운트다운은 바깥 Box 에 겹쳐 그립니다.
 */
@Composable
private fun NormalHeroContent(
    block: Block,
    nextTitle: String?,
    nextRoom: String?,
    supervisionText: String?,
    countdownViews: RemoteViews?,
) {
    val heroText = block.room ?: block.title
    val heroSpace = LocalSize.current.width.value - 32f - (if (block.iconKey.isNotEmpty()) 36f else 0f)

    Box(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            StatusRow(block, dotSize = 8.dp, gap = 8.dp, fontSize = 13.sp, text = statusText(block))

            // 상단이 과하게 벌어지지 않도록 여기는 고정 간격, 가중치는 아래쪽에만 씁니다.
            Spacer(GlanceModifier.height(12.dp))

            HeroRow(
                block = block,
                heroText = heroText,
                iconSize = 28.dp,
                fontSize = fitHeroFontSize(heroText, heroSpace).sp,
                color = GlanceTheme.colors.onSurface,
            )

            Spacer(GlanceModifier.defaultWeight())

            // 세로 여유가 있으면 긴 과목명이 잘리지 않도록 줄 수를 높이에 맞춰 늘립니다 (2~4줄).
            if (!nextTitle.isNullOrEmpty()) {
                val nextLines = ((LocalSize.current.height.value - 150f) / 18f).toInt().coerceIn(2, 4)
                NextRow(nextTitle, nextRoom, 13.sp, maxLines = nextLines)
            }

            Spacer(GlanceModifier.defaultWeight())

            // 18:50~19:00(저녁 gap)에는 급식 레이아웃이 아니라 여기서 면학감독을 보여줍니다.
            // 카운트다운 자리 바로 위에 조용히 얹어 겹치지 않게 합니다.
            if (supervisionText != null) {
                SupervisionLine(supervisionText, fontSize = 12.sp)
                Spacer(GlanceModifier.height(2.dp))
            }

            // 26sp 카운트다운(약 34dp)이 잘리지 않도록 자리를 비워 둡니다.
            Spacer(GlanceModifier.height(42.dp))
        }

        if (countdownViews != null) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart,
            ) {
                AndroidRemoteViews(
                    remoteViews = countdownViews,
                    modifier = GlanceModifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 2x2처럼 세로 공간이 넉넉한 크기용(급식 창) — 위치 히어로는 유지하고 하단을 메뉴로.
 */
@Composable
private fun MealHeroContent(block: Block, mealText: String, allergyPrefix: String, supervisionText: String?) {
    val heroText = block.room ?: block.title
    val heroSpace = LocalSize.current.width.value - 32f - (if (block.iconKey.isNotEmpty()) 36f else 0f)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatusRow(block, dotSize = 8.dp, gap = 8.dp, fontSize = 13.sp, text = statusText(block))

        Spacer(GlanceModifier.height(12.dp))

        HeroRow(
            block = block,
            heroText = heroText,
            iconSize = 28.dp,
            fontSize = fitHeroFontSize(heroText, heroSpace).sp,
        )

        Spacer(GlanceModifier.height(8.dp))

        // 메뉴가 남는 세로 공간을 차지합니다 — 줄 수는 위젯 높이에서 상단(상태·히어로·여백)과 하단 여백을 뺀
        // 만큼 계산해서, 공간이 남는데도 4줄에서 잘리지 않게 합니다 (한 줄 ≈ 20dp).
        val fixedDp = 32f + 20f + 12f + 40f + 8f + (if (supervisionText != null) 20f else 0f)
        val mealLines = ((LocalSize.current.height.value - fixedDp) / 20f).toInt().coerceIn(2, 12)
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.TopStart,
        ) {
            MealOrNextContent(
                mealText = mealText,
                nextTitle = null,
                nextRoom = null,
                fontSize = 15.sp,
                maxLines = mealLines,
                allergyPrefix = allergyPrefix,
            )
        }

        if (supervisionText != null) {
            Spacer(GlanceModifier.height(2.dp))
            SupervisionLine(supervisionText, fontSize = 12.sp)
        }
    }
}

private val accentHex: Map<Accent, Long> = mapOf(
    Accent.LESSON to 0xFF5B67F1,
    Accent.FREE to 0xFF14B8A6,
    Accent.BREAK_TIME to 0xFFF59E0B,
    Accent.BREAKFAST to 0xFFF97316,
    Accent.LUNCH to 0xFF22C55E,
    Accent.DINNER to 0xFF10B981,
    Accent.SNACK to 0xFFEC4899,
    Accent.STUDY to 0xFF3B82F6,
    Accent.LIBRARY to 0xFF92400E,
    Accent.DORM to 0xFF8B5CF6,
    Accent.IDLE to 0xFF6B7280,
    Accent.AFTER_SCHOOL to 0xFF06B6D4,
    Accent.ONE_TWO to 0xFFEAB308,
)

private fun accentColor(accent: Accent): ColorProvider = ColorProvider(Color(accentHex.getValue(accent)))

class TimeTableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeTableWidget()

    // 마지막 위젯이 제거되면 provideGlance 가 다시 돌 일이 없어 틱 알람을 스스로 멈출 수
    // 없으므로, 여기서 확실히 취소합니다.
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetTickReceiver.cancel(context)
    }
}
