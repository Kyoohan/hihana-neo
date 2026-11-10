package com.skh.hihanaevo.widget

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.LocalContext
import androidx.compose.ui.graphics.toArgb
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.systemAccentColor
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
import androidx.glance.appwidget.appWidgetBackground
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
import com.skh.hihanaevo.Accent
import com.skh.hihanaevo.HomeStayNotice
import androidx.glance.layout.fillMaxHeight
import com.skh.hihanaevo.SeoulWeather
import com.skh.hihanaevo.homeStayExtras
import com.skh.hihanaevo.HomeStayExtras
import com.skh.hihanaevo.homeStayNotice
import com.skh.hihanaevo.Block
import com.skh.hihanaevo.BlockKind
import com.skh.hihanaevo.HanaAcademicRepository
import com.skh.hihanaevo.HanaTimetableSync
import com.skh.hihanaevo.MainActivity
import com.skh.hihanaevo.NextUp
import com.skh.hihanaevo.Meal
import com.skh.hihanaevo.PlanStore
import com.skh.hihanaevo.R
import com.skh.hihanaevo.Timetable
import com.skh.hihanaevo.detectedAllergies
import com.skh.hihanaevo.shortAllergyLabels
import com.skh.hihanaevo.weekday1SupervisionTeachers
import com.skh.hihanaevo.worker.WidgetRefreshWorker
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** 홈 화면 위젯 — 지금 이 순간의 면학 위치/일정과 급식 메뉴를 보여줍니다 */
class HiHanaEvoWidget : GlanceAppWidget() {

    /**
     * 기본값인 SizeMode.Single은 LocalSize가 appwidget-provider의 minWidth/minHeight를
     * 고정 반환해서 실제 크기(2x2 등)를 알 수 없습니다. Exact로 두면 런처가 알려주는
     * 실제 크기가 들어와 높이 기반 레이아웃 분기가 동작합니다.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val opacity = runCatching { PlanStore.homeWidgetOpacity(context) }.getOrDefault(PlanStore.DEFAULT_WIDGET_OPACITY)
        val theme = runCatching { PlanStore.homeWidgetTheme(context) }.getOrDefault(PlanStore.THEME_SYSTEM)
        val isDark = isDarkTheme(context, theme)
        // 강조 글자 색 — 설정에 따라 블록 종류별 색 / 앱 강조 색(자동이면 시스템 테마 색) / 일반 글자색.
        val accentMode = runCatching { PlanStore.homeWidgetAccent(context) }.getOrDefault(PlanStore.WIDGET_ACCENT_KIND)
        val kindColors = WidgetKindColors.resolve(runCatching { PlanStore.widgetKindColors(context) }.getOrDefault(emptyMap()))
        val accentOverride: Int? = when (accentMode) {
            PlanStore.WIDGET_ACCENT_APP -> {
                val saved = runCatching { PlanStore.accentColor(context) }.getOrDefault(PlanStore.AUTO_ACCENT_COLOR)
                if (saved != PlanStore.AUTO_ACCENT_COLOR) saved
                else systemAccentColor(context)?.toArgb() ?: OneUi.Blue.toArgb()
            }
            PlanStore.WIDGET_ACCENT_TEXT -> if (isDark) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()
            else -> null
        }
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
            // 쉬는 시간은 다음 일정이 아니므로 수업·면학만 셉니다.
            val blocks = Timetable.blocks(today) { places[it] }
            val next = Timetable.nextEvent(blocks, block, now)
            // 세 칸 이상 높은 위젯의 '이후 일정' — 다음 일정부터 이어지는 몇 개(연강은 하나로).
            val upcoming = buildList {
                var ref = next
                while (ref != null && size < 6) {
                    add(UpcomingRow(ref.start.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), rowTitle(ref), widgetPlace(ref)))
                    ref = Timetable.nextEvent(blocks, ref, ref.start)
                }
            }

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

            // 귀가 기간이면 서울 날씨를 받아 두고(30분 캐시) 안내 아래에 날씨·귀교 후 첫 일정·시험을 붙입니다.
            val homeStay = homeStayNotice(today, now)
            // 위젯 갱신은 짧게 끝나야 합니다 — 서버가 느리면 캐시된 날씨로 그리고 다음 갱신에 다시 받습니다.
            if (homeStay != null) runCatching { kotlinx.coroutines.withTimeoutOrNull(8_000L) { SeoulWeather.get(context, SeoulWeather.Place.SEOUL) } }
            WidgetSnapshot(
                block = block,
                homeStay = homeStay,
                homeStayExtras = if (homeStay != null) homeStayExtras(context, today, now) else null,
                nextTitle = next?.title,
                nextRoom = next?.let(::widgetPlace),
                upcoming = upcoming,
                mealText = mealText,
                mealAllergyPrefix = mealAllergyPrefix,
                supervisionText = supervisionText,
                remainingMillis = block?.let { Duration.between(now, it.end).toMillis().coerceAtLeast(0) },
                // 귀가 기간엔 경계가 없어 30분 동기화에만 기대면 자정(귀가→귀가 중)이나 저녁 날씨 칸이 늦게 바뀝니다 — 매시 정각에 다시 그립니다.
                refreshDelayMillis = boundary?.let { Duration.between(now, it).toMillis() }
                    ?: homeStay?.let { Duration.between(now, now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1)).toMillis() }
                    ?: -1L,
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
        val countdownViews = snapshot?.let { snap ->
            val remaining = snap.remainingMillis
            val blockAccent = snap.block?.let { accentOverride ?: kindColors.getValue(it.accent) }
            if (remaining != null && blockAccent != null) {
                countdownRemoteViews(context, remaining, blockAccent, snap.block.countdownCaption, if (isDark) 0xFFE2E8F0.toInt() else 0xFF475569.toInt())
            } else {
                null
            }
        }

        provideContent {
            WidgetRoot(snapshot, opacity, isDark, wallpaperTint, accentOverride, kindColors, countdownViews)
        }
    }
}

/**
 * 위젯 한 장 — 실제 위젯과 dev 빌드의 위젯 미리보기(src/debug WidgetPreviewActivity)가 같이 씁니다. 미리보기는 같은 그림을
 * 여러 크기·상황으로 늘어놓아 레이아웃을 한눈에 비교합니다.
 */
@Composable
internal fun WidgetRoot(
    snapshot: WidgetSnapshot?,
    opacity: Int,
    isDark: Boolean,
    wallpaperTint: Int?,
    accentOverride: Int?,
    kindColors: Map<Accent, Int>,
    countdownViews: RemoteViews?,
) {
    CompositionLocalProvider(LocalAccentOverride provides accentOverride, LocalKindColors provides kindColors) {
        GlanceTheme {
            WidgetContent(
                block = snapshot?.block,
                opacityPercent = opacity,
                isDark = isDark,
                wallpaperTint = wallpaperTint,
                nextTitle = snapshot?.nextTitle,
                nextRoom = snapshot?.nextRoom,
                upcoming = snapshot?.upcoming.orEmpty(),
                mealText = snapshot?.mealText,
                mealAllergyPrefix = snapshot?.mealAllergyPrefix.orEmpty(),
                supervisionText = snapshot?.supervisionText,
                countdownViews = countdownViews,
                remainingMillis = snapshot?.remainingMillis,
                homeStay = snapshot?.homeStay,
                homeStayExtras = snapshot?.homeStayExtras,
            )
        }
    }
}

/** 위젯이 한 번 그릴 때 필요한 값 묶음 */
internal data class WidgetSnapshot(
    val block: Block?,
    /** 귀가 기간 안내 — 일정이 빈 동안 '현재 일정이 없습니다' 대신 띄웁니다. */
    val homeStay: HomeStayNotice?,
    val homeStayExtras: HomeStayExtras?,
    val nextTitle: String?,
    val nextRoom: String?,
    val mealText: String?,
    val mealAllergyPrefix: String,
    /** 저녁 창(17:50~19:00) 내내 채워지는 1타임 면학감독 한 줄 — 그 외에는 null. */
    val supervisionText: String?,
    val remainingMillis: Long?,
    val refreshDelayMillis: Long,
    /** 다음 일정부터 이어지는 몇 개 — 세 칸 이상 높은 위젯에만 보입니다. */
    val upcoming: List<UpcomingRow> = emptyList(),
)

/** '이후 일정' 한 줄 — 시작 시각, 이름("5교시 스포츠 생활1", "1타임 · 도서관"), 장소(선생님 이름 없이). */
internal data class UpcomingRow(val time: String, val title: String, val place: String?)

/**
 * 위젯에 쓰는 장소 — 수업은 선생님 이름 없이 교실만. 좁은 위젯에서 "융합관 2층 컴퓨터실2 · 이도윤"의 선생님 이름이
 * 교실을 밀어내 잘렸습니다. 다른 구간은 앱과 같은 [Block.room].
 */
internal fun widgetPlace(block: Block): String? = when (val kind = block.kind) {
    is BlockKind.LessonKind -> kind.lesson.room
    is BlockKind.GapKind -> when (val next = kind.gap.next) {
        is NextUp.LessonNext -> next.lesson.room
        else -> block.room
    }
    else -> block.room
}

/** '이후 일정' 이름 — 수업은 "5교시 과목", 면학은 "1타임 · 장소". */
internal fun rowTitle(block: Block): String = when (val kind = block.kind) {
    is BlockKind.LessonKind -> if (kind.lesson.isFree) "${kind.period}교시 공강" else "${kind.period}교시 ${kind.lesson.title}"
    is BlockKind.StudyKind -> "${kind.session.name} · ${kind.place?.name ?: "위치 미설정"}"
    is BlockKind.OutingKind -> block.statusLabel
    else -> block.title
}

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

/** 위젯 알레르기 경고 글자색 — 밝은·어두운 위젯 바탕 모두에서 읽히는 빨강. */
private val AllergyRed = Color(0xFFEF4444)

/** 급식 줄 RemoteViews — [prefix](알레르기 경고)는 빨간 굵은 글씨, 이어서 [menu]. */
private fun mealRemoteViews(context: Context, prefix: String, menu: String, textColor: Int, fontSizeSp: Float, maxLines: Int): RemoteViews {
    val text = android.text.SpannableStringBuilder().apply {
        append(prefix, android.text.style.ForegroundColorSpan(AllergyRed.toArgb()), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, prefix.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(" ")
        append(menu)
    }
    return RemoteViews(context.packageName, R.layout.widget_meal_text).apply {
        setTextViewText(R.id.widget_meal_text, text)
        setTextColor(R.id.widget_meal_text, textColor)
        setTextViewTextSize(R.id.widget_meal_text, android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        setInt(R.id.widget_meal_text, "setMaxLines", maxLines)
    }
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
internal fun countdownRemoteViews(
    context: Context,
    remainingMillis: Long,
    accentArgb: Int,
    /** 숫자 앞의 작은 설명 — "종료까지", "다음 수업까지". 없으면 숫자만. */
    caption: String?,
    captionArgb: Int,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_countdown)
    val id = R.id.widget_countdown
    views.setTextViewText(R.id.widget_countdown_caption, caption.orEmpty())
    views.setTextColor(R.id.widget_countdown_caption, captionArgb)
    views.setViewVisibility(R.id.widget_countdown_caption, if (caption.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
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
    upcoming: List<UpcomingRow>,
    mealText: String?,
    mealAllergyPrefix: String,
    supervisionText: String?,
    countdownViews: RemoteViews?,
    remainingMillis: Long?,
    homeStay: HomeStayNotice?,
    homeStayExtras: HomeStayExtras?,
) {
    // 슬라이더 값을 라이트/다크 모두 그대로 반영합니다. 삼성 홈의 흐림은 바탕이 반투명(알파 1~254)일 때만 켜져서 양 끝은 피합니다.
    val alphaInt = (opacityPercent * 255 / 100).coerceIn(1, 254)
    // 월페이퍼의 대표색을 흰/검 바탕에 옅게 섞어 둡니다 — 삼성 홈에선 이 색이 흐린 배경 위의 아크릴 틴트가 되고, 흐림이 없는
    // 런처에선 "뒤가 비치는" 헤이즈 느낌을 냅니다. 대표색을 못 읽으면 중성 흰/검 그대로입니다.
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
        // 아크릴 — 맨 바깥 상자를 위젯 바탕 뷰(@android:id/background)로 두고 반투명 틴트를 칠합니다. 삼성 One UI 7+ 홈은 위젯
        // 정보의 widgetStyle="colorful" 과 이 바탕 뷰를 보고 뒤 배경화면을 직접 흐려 줍니다(앱이 흐릴 수는 없음). 그 위에 아크릴의
        // 미세한 그레인 한 겹. 예전의 빛 하이라이트 시트는 진짜 흐림 위에선 뿌옇게 떠 보여 뺐습니다.
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
                .appWidgetBackground()
                .background(GlanceTheme.colors.surface)
                .then(corner)
                .clickable(openTabAction(MainActivity.TAB_TODAY)),
        ) {
            // 아크릴 표면의 미세한 그레인
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_frost_noise))
                    .then(corner)
                    .padding(16.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (block != null && !block.isBlank) {
                    ScheduleContent(block, nextTitle, nextRoom, upcoming, mealText, mealAllergyPrefix, supervisionText, countdownViews, remainingMillis)
                } else {
                    if (homeStay != null && homeStayExtras != null) HomeStayContent(homeStay, homeStayExtras)
                    else EmptyScheduleContent(homeStay)
                }
            }
        }
    }
}

/** 월페이퍼 대표색(ARGB) — 못 읽으면 null. 권한 없이 읽을 수 있는 색 정보만 씁니다. */
private fun wallpaperPrimaryColor(context: Context): Int? = if (android.os.Build.VERSION.SDK_INT < 27) null else runCatching {
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

/**
 * 귀가 기간 — 안내 문구·귀교 날짜 아래 서울 날씨 두 칸(지금 · 귀교 밤 9시)을 아이콘·큰 기온으로.
 * 폭이 넉넉하면(4x2 이상) 오른쪽 칸에 귀교 후 첫 일정과 시험을 더합니다. 귀교일 밤 9시면 평소 화면으로.
 */
@Composable
private fun HomeStayContent(homeStay: HomeStayNotice, extras: HomeStayExtras) {
    val titleColor = GlanceTheme.colors.onSurface
    val subtextColor = GlanceTheme.colors.onSurfaceVariant
    val wide = LocalSize.current.width >= 250.dp
    // 한 칸 높이(≈115dp)엔 날씨 칸(아이콘·큰 기온)이 들어가지 않아 잘렸습니다 — 그땐 "지금 서울 17° · 귀교 21시 12°" 한 줄로.
    val compact = widgetHeight() == WidgetHeight.COMPACT
    // 날씨 칸의 설명·한마디(구름 조금 · 우산 챙기세요)는 세 칸 이상일 때만 — 두 칸에선 아래가 잘렸습니다.
    val tall = widgetHeight() == WidgetHeight.TALL
    val returning = homeStay.shortDetail == "오늘 귀교"

    @Composable
    fun main(modifier: GlanceModifier) {
        Column(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_home_stay),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(subtextColor),
                    modifier = GlanceModifier.size(14.dp),
                )
                Spacer(GlanceModifier.width(5.dp))
                Text(if (returning) "귀교" else "귀가", style = TextStyle(color = subtextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(homeStay.title, style = TextStyle(color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(homeStay.shortDetail, style = TextStyle(color = subtextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            val tiles = listOfNotNull(extras.weatherNow, extras.weatherBack)
            if (tiles.isNotEmpty() && compact) {
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    tiles.joinToString(" · ") { "${it.label} ${it.temp ?: "–"}" },
                    style = TextStyle(color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            } else if (tiles.isNotEmpty()) {
                // 서울 날씨 두 칸(지금 · 귀교 밤 9시) — 남는 높이를 다 써서 아이콘·기온을 크게, 위아래 가운데에.
                Spacer(GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    tiles.forEachIndexed { index, tile ->
                        if (index > 0) Spacer(GlanceModifier.width(10.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(tile.label, style = TextStyle(color = subtextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Spacer(GlanceModifier.height(2.dp))
                            Image(provider = ImageProvider(tile.icon), contentDescription = null, modifier = GlanceModifier.size(34.dp))
                            Text(tile.temp ?: "–", style = TextStyle(color = titleColor, fontSize = 28.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            if (tall) {
                                // 좁은 칸이라 "강수 90%" 는 "90%" 로.
                                Text(tile.desc.replace("강수 ", ""), style = TextStyle(color = subtextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                                tile.tip?.let {
                                    Text(it, style = TextStyle(color = ColorProvider(Color(0xFF3D8BFF)), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
            } else if (!wide) {
                // 날씨를 아직 못 받았으면 좁은 위젯에서도 빈칸 대신 귀교 후 첫 일정·시험을 한 줄씩.
                extras.firstPlan?.let { (plan, time) ->
                    Text("귀교 후 $time", style = TextStyle(color = subtextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Text(plan, style = TextStyle(color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                extras.exam?.let { (name, days) ->
                    Text("$name ${if (days == 0L) "D-day" else "D-$days"}", style = TextStyle(color = subtextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
    }

    if (!wide || (extras.firstPlan == null && extras.exam == null)) {
        main(GlanceModifier.fillMaxSize())
        return
    }
    Row(modifier = GlanceModifier.fillMaxSize()) {
        main(GlanceModifier.defaultWeight().fillMaxHeight())
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.width(140.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            extras.firstPlan?.let { (plan, time) ->
                Text("귀교 후 첫 일정 · $time", style = TextStyle(color = subtextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(plan, style = TextStyle(color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = if (compact) 1 else 2)
                Spacer(GlanceModifier.height(if (compact) 6.dp else 10.dp))
            }
            extras.exam?.let { (name, days) ->
                val dday = if (days == 0L) "D-day" else "D-$days"
                // 한 칸 높이에선 시험 이름과 D-day 를 한 줄로 — 두 줄이면 D-day 가 잘렸습니다.
                if (compact) {
                    Text("$name $dday", style = TextStyle(color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                } else {
                    Text(name, style = TextStyle(color = subtextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Text(dday, style = TextStyle(color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EmptyScheduleContent(homeStay: HomeStayNotice?) {
    val titleColor = GlanceTheme.colors.onSurface
    val subtextColor = GlanceTheme.colors.onSurfaceVariant

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(if (homeStay != null) R.drawable.ic_home_stay else R.drawable.ic_hotel),
            contentDescription = null,
            colorFilter = ColorFilter.tint(subtextColor),
            modifier = GlanceModifier.size(28.dp),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = homeStay?.title ?: "현재 일정이 없습니다",
            style = TextStyle(
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = homeStay?.shortDetail ?: "면학 시간 외 / 휴식 시간",
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
 * 위젯 높이 단계 — 삼성 홈 한 칸(≈115dp) / 두 칸(≈180dp) / 세 칸 이상. 실제 줄 수·글자 크기는 이 단계 안에서 실제 높이로 정합니다.
 * 위젯 미리보기(dev 빌드)로 크기마다 그려 맞춘 값입니다.
 */
private enum class WidgetHeight { COMPACT, MEDIUM, TALL }

@Composable
private fun widgetHeight(): WidgetHeight {
    val height = LocalSize.current.height
    return when {
        height < 150.dp -> WidgetHeight.COMPACT
        height < 230.dp -> WidgetHeight.MEDIUM
        else -> WidgetHeight.TALL
    }
}

/** 두 칸 폭(≈150dp) — 장소를 아랫줄로 내리고 시간 범위 같은 곁가지를 덜어 냅니다. */
@Composable
private fun isNarrow(): Boolean = LocalSize.current.width < 200.dp

/** 위젯 안쪽(여백 16dp 를 뺀) 폭·높이(dp). */
@Composable
private fun innerWidth(): Float = LocalSize.current.width.value - 32f

@Composable
private fun innerHeight(): Float = LocalSize.current.height.value - 32f

/**
 * 한 줄 높이(dp) 어림 — 위젯 글꼴(한글 본고딕 계열)은 줄 간격이 글자 크기의 약 1.47배입니다(미리보기로 잰 값).
 * 예전엔 15sp 를 20dp, 13sp 를 18dp 로 잡아 급식 메뉴 줄 수가 넘쳐, 마지막 줄이 가로로 반 잘려 보였습니다.
 */
private fun lineDp(sp: Float): Float = sp * 1.47f

/** 히어로(장소) 글자 — 위젯이 2·3·4칸이든 장소 이름은 가장 크게, 넘치면 줄이지 않고 두 줄로([text] 에 줄바꿈이 들어감). */
private data class HeroFit(val text: String, val sp: Float, val lines: Int) {
    /** 한글이 들어가면 본고딕 줄 높이(1.47배), 숫자·영문만("2-46", "A304")이면 로보토 줄 높이(1.2배). */
    val heightDp: Float get() = lines * sp * (if (text.any { it.code in 0xAC00..0xD7A3 }) 1.47f else 1.2f)
}

/**
 * 히어로 글자 크기 — 한 줄에 [minOneLine] sp 이상으로 들어가면 한 줄, 아니면([twoLines] 일 때) 두 줄로 나눠 더 크게.
 * 좁은 2×2 에서 "융합관 2층 컴퓨터실2"가 14sp 로 줄고도 잘리던 것.
 */
private fun fitHero(text: String, spaceDp: Float, maxSp: Float, minOneLine: Float, twoLines: Boolean, twoLineMaxSp: Float = maxSp - 4f): HeroFit {
    val one = fitFont(text, spaceDp)
    val spaces = text.indices.filter { text[it] == ' ' }
    if (one >= minOneLine || !twoLines || spaces.isEmpty()) return HeroFit(text, one.coerceIn(14f, maxSp), 1)
    // 두 줄은 가운데에 가장 가까운 띄어쓰기에서 직접 나눕니다 — 맡겨 두면 "컴 / 퓨터실2"처럼 낱말 중간에서 꺾였습니다.
    val cut = spaces.minBy { kotlin.math.abs(it - text.length / 2) }
    val first = text.substring(0, cut)
    val second = text.substring(cut + 1)
    val sp = minOf(fitFont(first, spaceDp), fitFont(second, spaceDp)).coerceIn(15f, twoLineMaxSp)
    return HeroFit("$first\n$second", sp, 2)
}

/**
 * Glance 는 텍스트 자동 축소를 지원하지 않아, 글자별 대략적인 폭(em)을 합산해 주어진 폭에 맞는 글자 크기를 역산합니다.
 * 한글은 1em, 대문자·숫자·소문자·기호는 그보다 좁게 봅니다.
 */
private fun fitFont(text: String, availableDp: Float): Float {
    if (text.isEmpty() || availableDp <= 0f) return 30f
    val em = text.fold(0.0) { acc, c ->
        acc + when {
            c.code in 0xAC00..0xD7A3 || c.code in 0x3131..0x318E -> 1.0 // 한글 음절/자모
            c.isWhitespace() -> 0.3
            c.isDigit() -> 0.6
            c.isUpperCase() -> 0.68
            c in ".-()·:" -> 0.38
            else -> 0.56
        }
    }
    // 0.95: 폰트 메트릭 오차로 잘리지 않도록 여유를 둡니다
    return (availableDp / (em * 0.95)).toFloat()
}

/** 히어로에 쓸 글자 — 가야 할 장소(선생님 이름 없이), 장소가 없으면(공강·생활관) 이름. */
private fun heroText(block: Block): String = widgetPlace(block) ?: block.title

/** 외출·외박 사유 — 히어로가 "18:00 복귀"라 사유는 그 아래 줄에. */
private fun outingReason(block: Block): String? = (block.kind as? BlockKind.OutingKind)?.outing?.reason

/**
 * 높이 단계와 급식 표시 여부로 레이아웃을 고릅니다. 급식 창이 활성일 때만 메뉴 레이아웃이고, 평소에는 일정 레이아웃입니다.
 */
@Composable
private fun ScheduleContent(
    block: Block,
    nextTitle: String?,
    nextRoom: String?,
    upcoming: List<UpcomingRow>,
    mealText: String?,
    mealAllergyPrefix: String,
    supervisionText: String?,
    countdownViews: RemoteViews?,
    remainingMillis: Long?,
) {
    val compact = widgetHeight() == WidgetHeight.COMPACT
    when {
        mealText != null && compact -> MealCompactContent(block, mealText, mealAllergyPrefix, supervisionText)
        mealText != null -> MealHeroContent(block, mealText, mealAllergyPrefix, supervisionText, nextTitle, nextRoom)
        compact -> NormalCompactContent(block, nextTitle, nextRoom)
        else -> NormalHeroContent(block, nextTitle, nextRoom, upcoming, supervisionText, countdownViews, remainingMillis)
    }
}

/** 상단 상태 한 줄 — 수업 중이면 현재 과목명까지 함께 보여줍니다 (예: "5교시 · 미적분Ⅱ") */
private fun statusText(block: Block): String {
    val kind = block.kind
    if (kind is BlockKind.LessonKind && !kind.lesson.isFree) {
        return "${kind.period}교시 · ${kind.lesson.title}"
    }
    // 공강처럼 교시가 상태 라벨과 겹치면 중복을 피해 교시만 보여줍니다
    if (block.periodNumber != null && block.room == null) return "${block.periodNumber}교시"
    return block.statusLabel
}

/** 상단 상태 점 + 라벨 (+ 오른쪽 끝에 [trailing] — 넓은 위젯의 시간 범위). */
@Composable
private fun StatusRow(
    block: Block,
    dotSize: Dp,
    gap: Dp,
    fontSize: TextUnit,
    text: String,
    trailing: String? = null,
) {
    val accent = accentColor(block.accent)
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            modifier = GlanceModifier.defaultWeight(),
        )
        if (trailing != null) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = trailing,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/** 위치(방) 히어로 — 아이콘 + 큰 글자(넘치면 두 줄). */
@Composable
private fun HeroRow(
    block: Block,
    heroText: String,
    iconSize: Dp,
    fontSize: TextUnit,
    color: ColorProvider? = null,
    maxLines: Int = 1,
) {
    val heroColor = color ?: accentColor(block.accent)
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(openTabAction(MainActivity.TAB_TODAY)),
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
            maxLines = maxLines,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

/**
 * 급식 메뉴 — 알레르기 경고가 있으면 그 부분만 빨간 굵은 글씨로. Glance Text 는 한 줄 안에서 색을 나눌 수 없어 그때만
 * RemoteViews TextView(색 구간 지정)로 그립니다. 가중치 계산이 깨지지 않게 Box 로 감쌉니다.
 */
@Composable
private fun MealText(mealText: String, allergyPrefix: String, fontSize: TextUnit, maxLines: Int) {
    if (allergyPrefix.isEmpty()) {
        Text(
            text = mealText,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = fontSize, fontWeight = FontWeight.Medium),
            maxLines = maxLines,
            modifier = GlanceModifier.clickable(openTabAction(MainActivity.TAB_MEAL)),
        )
        return
    }
    val context = LocalContext.current
    val views = mealRemoteViews(
        context,
        prefix = allergyPrefix.trimEnd(),
        menu = mealText,
        textColor = GlanceTheme.colors.onSurface.getColor(context).toArgb(),
        fontSizeSp = fontSize.value,
        maxLines = maxLines,
    )
    Box(modifier = GlanceModifier.fillMaxWidth().clickable(openTabAction(MainActivity.TAB_MEAL))) {
        AndroidRemoteViews(remoteViews = views, modifier = GlanceModifier.fillMaxWidth())
    }
}

/**
 * '다음 일정' — "다음" 텍스트·가운뎃점 대신 아이콘으로 구분합니다. 제목이 없으면 통째로 그리지 않습니다.
 * [stack] 이면 장소를 제목 아랫줄로(좁은 위젯: 긴 장소가 제목을 0 폭으로 누르던 것), [showRoom] 이 아니면 장소를 뺍니다.
 */
@Composable
private fun NextRow(
    nextTitle: String?,
    nextRoom: String?,
    fontSize: TextUnit,
    maxLines: Int = 1,
    stack: Boolean = false,
    showRoom: Boolean = true,
) {
    val title = nextTitle?.takeIf { it.isNotEmpty() } ?: return
    val room = nextRoom?.takeIf { it.isNotEmpty() && showRoom }
    val subtext = GlanceTheme.colors.onSurfaceVariant
    val onSurface = GlanceTheme.colors.onSurface

    @Composable
    fun titleRow() = Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_next),
            contentDescription = null,
            colorFilter = ColorFilter.tint(subtext),
            modifier = GlanceModifier.size(14.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = title,
            style = TextStyle(color = onSurface, fontSize = fontSize, fontWeight = FontWeight.Medium),
            maxLines = maxLines,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (room != null && !stack) {
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_place),
                contentDescription = null,
                colorFilter = ColorFilter.tint(subtext),
                modifier = GlanceModifier.size(12.dp),
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(text = room, style = TextStyle(color = onSurface, fontSize = fontSize, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }

    if (room == null || !stack) {
        titleRow()
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        titleRow()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(start = 18.dp)) {
            Image(
                provider = ImageProvider(R.drawable.ic_place),
                contentDescription = null,
                colorFilter = ColorFilter.tint(subtext),
                modifier = GlanceModifier.size(12.dp),
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = room,
                style = TextStyle(color = subtext, fontSize = fontSize, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** '이후 일정' 한 줄 높이(dp) — 넓으면 시각·이름·장소 한 줄, 좁으면 시각·장소 / 이름 두 줄. */
private fun upcomingRowDp(narrow: Boolean): Float = if (narrow) lineDp(11f) + lineDp(13f) + 4f else lineDp(13f) + 4f

/**
 * 세 칸 이상 높은 위젯의 '이후 일정' — 예전엔 히어로·다음 일정·카운트다운만 있어 가운데가 크게 비었습니다.
 * 다음 일정부터 [count] 개.
 */
@Composable
private fun UpcomingList(rows: List<UpcomingRow>, count: Int, narrow: Boolean) {
    val subtext = GlanceTheme.colors.onSurfaceVariant
    val onSurface = GlanceTheme.colors.onSurface
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_next),
                contentDescription = null,
                colorFilter = ColorFilter.tint(subtext),
                modifier = GlanceModifier.size(13.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text("이후 일정", style = TextStyle(color = subtext, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        rows.take(count).forEach { row ->
            Spacer(GlanceModifier.height(4.dp))
            if (narrow) {
                Text(
                    text = listOfNotNull(row.time, row.place).joinToString(" · "),
                    style = TextStyle(color = subtext, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(row.title, style = TextStyle(color = onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            } else {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.time,
                        style = TextStyle(color = subtext, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier.width(44.dp),
                    )
                    Text(
                        text = row.title,
                        style = TextStyle(color = onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    row.place?.let {
                        Spacer(GlanceModifier.width(8.dp))
                        Text(it, style = TextStyle(color = subtext, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
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
 * 한 칸 높이(≈115dp)용(정상 일정) — 상태(넓으면 오른쪽에 시간 범위) → 장소 → 다음 일정 한 줄(좁으면 장소 빼고)
 * → (좁으면) 시간 범위. 예전엔 좁은 2×1 에서 다음 일정이 두 줄로 늘어 시간 범위가 잘렸습니다.
 */
@Composable
private fun NormalCompactContent(block: Block, nextTitle: String?, nextRoom: String?) {
    val subtext = GlanceTheme.colors.onSurfaceVariant
    val narrow = isNarrow()
    val heroText = heroText(block)
    val hero = fitHero(heroText, innerWidth() - (if (block.iconKey.isNotEmpty()) 26f else 0f), maxSp = 22f, minOneLine = 14f, twoLines = false)
    val reason = outingReason(block)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatusRow(block, dotSize = 6.dp, gap = 6.dp, fontSize = 12.sp, text = statusText(block), trailing = if (narrow) null else block.timeRangeText)

        Spacer(GlanceModifier.defaultWeight())

        HeroRow(block = block, heroText = hero.text, iconSize = 18.dp, fontSize = hero.sp.sp, color = GlanceTheme.colors.onSurface)

        when {
            reason != null -> Text(reason, style = TextStyle(color = subtext, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            !nextTitle.isNullOrEmpty() -> {
                Spacer(GlanceModifier.height(2.dp))
                NextRow(nextTitle, nextRoom, 12.sp, maxLines = 1, showRoom = !narrow)
            }
        }

        Spacer(GlanceModifier.defaultWeight())

        if (narrow) {
            Text(
                text = block.timeRangeText,
                style = TextStyle(color = subtext, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/**
 * 한 칸 높이(≈115dp)용(급식 창) — 상태·장소를 한 줄로, 아래에 메뉴. 메뉴 줄 수는 남는 높이(면학감독 줄 몫을 뺀)로.
 */
@Composable
private fun MealCompactContent(block: Block, mealText: String, allergyPrefix: String, supervisionText: String?) {
    val supervisionDp = if (supervisionText != null) lineDp(11f) + 2f else 0f
    val mealLines = ((innerHeight() - lineDp(12f) - 4f - supervisionDp) / lineDp(13f)).toInt().coerceIn(1, 4)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatusRow(
            block = block,
            dotSize = 6.dp,
            gap = 6.dp,
            fontSize = 12.sp,
            text = "${statusText(block)} · ${heroText(block)}",
        )

        Spacer(GlanceModifier.defaultWeight())

        MealText(mealText, allergyPrefix, fontSize = 13.sp, maxLines = mealLines)

        if (supervisionText != null) {
            Spacer(GlanceModifier.height(2.dp))
            SupervisionLine(supervisionText, fontSize = 11.sp)
        }
    }
}

/**
 * 두 칸 이상 높이용(정상 일정) — 상태 → 장소 히어로(넘치면 두 줄) → 다음 일정, 세 칸 이상이면 '이후 일정' 목록 → 남은 시간.
 * 저녁 창(18:50~19:00)에는 남은 시간 위에 면학감독 한 줄이 더 붙습니다.
 *
 * 남은 시간은 넓으면 초까지 세는 Chronometer("종료까지 23:40"), 좁으면 분 단위 글자("종료까지" / "23분") — 좁은 폭에선
 * 설명과 큰 숫자가 한 줄에 들어가지 않아 숫자가 "23:4 / 0"으로 꺾였습니다. 위젯은 1분마다 다시 그려집니다.
 * Chronometer(AndroidRemoteViews)를 같은 Column 에 넣으면 defaultWeight 계산이 깨지므로,
 * 가중치가 걸린 내용은 안쪽 Column 에 두고 카운트다운은 바깥 Box 에 겹쳐 그립니다.
 */
@Composable
private fun NormalHeroContent(
    block: Block,
    nextTitle: String?,
    nextRoom: String?,
    upcoming: List<UpcomingRow>,
    supervisionText: String?,
    countdownViews: RemoteViews?,
    remainingMillis: Long?,
) {
    val narrow = isNarrow()
    val tall = widgetHeight() == WidgetHeight.TALL
    val iconDp = if (narrow) 24f else 28f
    val hero = fitHero(
        heroText(block),
        innerWidth() - (if (block.iconKey.isNotEmpty()) iconDp + 8f else 0f),
        maxSp = if (narrow) 24f else 30f,
        // 두 칸 높이에선 한 줄로 조금 작게가 낫고(아래 다음 일정 자리), 세 칸 이상이나 좁은 폭에선 두 줄로 크게.
        minOneLine = if (tall || narrow) 17f else 15f,
        twoLines = true,
        // 좁은 두 칸 높이(2×2)는 두 줄을 조금 작게 — 그래야 아래 다음 일정 한 줄이 들어갑니다.
        twoLineMaxSp = when {
            narrow && !tall -> 17f
            narrow -> 20f
            else -> 26f
        },
    )
    val showCountdown = remainingMillis != null && (narrow || countdownViews != null)
    val countdownDp = when {
        !showCountdown -> 0f
        narrow -> lineDp(11f) + 22f * 1.25f
        else -> 44f
    }
    val supervisionDp = if (supervisionText != null) lineDp(12f) + 2f else 0f
    // 히어로 아래에 쓸 수 있는 높이 — 상태 줄·간격·히어로·면학감독·남은 시간을 뺀 만큼.
    val room = innerHeight() - lineDp(13f) - 8f - hero.heightDp - 8f - supervisionDp - countdownDp
    val nextLines = (room / lineDp(13f)).toInt()
    val listRows = ((room - lineDp(11f) - 2f) / upcomingRowDp(narrow)).toInt()
    val reason = outingReason(block)
    val subtext = GlanceTheme.colors.onSurfaceVariant

    Box(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            StatusRow(block, dotSize = 8.dp, gap = 8.dp, fontSize = 13.sp, text = statusText(block), trailing = if (narrow) null else block.timeRangeText)

            Spacer(GlanceModifier.height(8.dp))

            HeroRow(
                block = block,
                heroText = hero.text,
                iconSize = iconDp.dp,
                fontSize = hero.sp.sp,
                color = GlanceTheme.colors.onSurface,
                maxLines = hero.lines,
            )

            Spacer(GlanceModifier.height(8.dp))

            when {
                nextLines < 1 -> Unit
                reason != null -> Text(
                    reason,
                    style = TextStyle(color = subtext, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = nextLines.coerceAtMost(3),
                )
                tall && upcoming.size >= 2 && listRows >= 2 -> UpcomingList(upcoming, listRows.coerceAtMost(upcoming.size), narrow)
                !nextTitle.isNullOrEmpty() -> NextRow(
                    nextTitle,
                    nextRoom,
                    13.sp,
                    maxLines = if (narrow) 1 else nextLines.coerceAtMost(3),
                    // 좁으면 장소를 아랫줄로 — 그럴 높이가 없으면 장소는 빼고 이름만.
                    stack = narrow && nextLines >= 2,
                    showRoom = !narrow || nextLines >= 2,
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            if (supervisionText != null) {
                SupervisionLine(supervisionText, fontSize = 12.sp)
                Spacer(GlanceModifier.height(2.dp))
            }

            if (showCountdown && narrow) {
                block.countdownCaption?.let { Text(it, style = TextStyle(color = subtext, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1) }
                Text(
                    remainingText(remainingMillis!!),
                    style = TextStyle(color = accentColor(block.accent), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            } else if (showCountdown) {
                Spacer(GlanceModifier.height(countdownDp.dp))
            }
        }

        if (showCountdown && !narrow && countdownViews != null) {
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

/** 분 단위 남은 시간 — "23분", "1시간 5분". 1분 안이면 "1분". */
private fun remainingText(millis: Long): String {
    val minutes = ((millis + 59_999) / 60_000).coerceAtLeast(1)
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

/**
 * 두 칸 이상 높이용(급식 창) — 장소 히어로는 유지하고 그 아래를 메뉴로, 맨 아래 다음 일정 한 줄.
 * 메뉴 줄 수는 실제 줄 높이로 남는 높이를 나눠 정합니다 — 예전엔 줄 높이를 낮게 잡아 마지막 줄이 가로로 잘렸습니다.
 */
@Composable
private fun MealHeroContent(
    block: Block,
    mealText: String,
    allergyPrefix: String,
    supervisionText: String?,
    nextTitle: String?,
    nextRoom: String?,
) {
    val narrow = isNarrow()
    val heroText = heroText(block)
    val iconDp = if (narrow) 24f else 28f
    val hero = fitHero(
        heroText,
        innerWidth() - (if (block.iconKey.isNotEmpty()) iconDp + 8f else 0f),
        maxSp = if (narrow) 22f else 28f,
        minOneLine = 14f,
        twoLines = false,
    )
    val hasNext = !nextTitle.isNullOrEmpty()
    val fixedDp = lineDp(13f) + 10f + hero.heightDp + 8f +
        (if (hasNext) lineDp(12f) + 2f else 0f) + (if (supervisionText != null) lineDp(12f) + 2f else 0f)
    val mealLines = ((innerHeight() - fixedDp) / lineDp(15f)).toInt().coerceIn(1, 12)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatusRow(block, dotSize = 8.dp, gap = 8.dp, fontSize = 13.sp, text = statusText(block), trailing = if (narrow) null else block.timeRangeText)

        Spacer(GlanceModifier.height(10.dp))

        HeroRow(block = block, heroText = hero.text, iconSize = iconDp.dp, fontSize = hero.sp.sp)

        Spacer(GlanceModifier.height(8.dp))

        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.TopStart) {
            MealText(mealText, allergyPrefix, fontSize = 15.sp, maxLines = mealLines)
        }

        // 급식 창에도 다음 일정(다음 수업·면학)을 한 줄 보여줍니다.
        if (hasNext) {
            Spacer(GlanceModifier.height(2.dp))
            NextRow(nextTitle, nextRoom, 12.sp, maxLines = 1, showRoom = !narrow)
        }

        if (supervisionText != null) {
            Spacer(GlanceModifier.height(2.dp))
            SupervisionLine(supervisionText, fontSize = 12.sp)
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
 * 블록 종류별 기본 강조 색과 설정 화면용 라벨 — 설정의 '종류별 색'에서 종류마다 색을 바꿀 수 있고,
 * 바꾼 값은 [PlanStore.widgetKindColors] 에 Accent 이름으로 저장됩니다.
 */
object WidgetKindColors {
    val defaults: Map<Accent, Int> = mapOf(
        Accent.LESSON to 0xFF5B67F1.toInt(),
        Accent.FREE to 0xFF14B8A6.toInt(),
        Accent.BREAK_TIME to 0xFFF59E0B.toInt(),
        Accent.BREAKFAST to 0xFFF97316.toInt(),
        Accent.LUNCH to 0xFF22C55E.toInt(),
        Accent.DINNER to 0xFF10B981.toInt(),
        Accent.SNACK to 0xFFEC4899.toInt(),
        Accent.STUDY to 0xFF3B82F6.toInt(),
        Accent.LIBRARY to 0xFF92400E.toInt(),
        Accent.DORM to 0xFF8B5CF6.toInt(),
        Accent.IDLE to 0xFF6B7280.toInt(),
        Accent.AFTER_SCHOOL to 0xFF06B6D4.toInt(),
        Accent.ONE_TWO to 0xFFEAB308.toInt(),
        Accent.OUTING to 0xFF34B27D.toInt(),
    )

    /** 설정에 보이는 순서 — 수업 계열 → 급식 → 면학 장소 → 활동. */
    val ordered: List<Accent> = listOf(
        Accent.LESSON, Accent.FREE, Accent.BREAK_TIME, Accent.IDLE,
        Accent.BREAKFAST, Accent.LUNCH, Accent.DINNER, Accent.SNACK,
        Accent.STUDY, Accent.LIBRARY, Accent.DORM, Accent.AFTER_SCHOOL, Accent.ONE_TWO, Accent.OUTING,
    )

    fun label(accent: Accent): String = when (accent) {
        Accent.LESSON -> "수업"
        Accent.FREE -> "공강"
        Accent.BREAK_TIME -> "쉬는 시간"
        Accent.BREAKFAST -> "아침"
        Accent.LUNCH -> "점심"
        Accent.DINNER -> "저녁"
        Accent.SNACK -> "간식"
        Accent.STUDY -> "면학실·교과교실"
        Accent.LIBRARY -> "도서관"
        Accent.DORM -> "생활관"
        Accent.IDLE -> "대기"
        Accent.AFTER_SCHOOL -> "방과후"
        Accent.ONE_TWO -> "1인2기"
        Accent.OUTING -> "외출·외박"
    }

    /** 저장된 사용자 색을 기본 색 위에 덮은 최종 표. */
    fun resolve(overrides: Map<String, Int>): Map<Accent, Int> =
        defaults.mapValues { (accent, default) -> overrides[accent.name] ?: default }
}

/** 설정에서 고른 단일 강조 색(ARGB) — null 이면 [LocalKindColors] 의 종류별 색을 씁니다. */
private val LocalAccentOverride = compositionLocalOf<Int?> { null }
private val LocalKindColors = compositionLocalOf<Map<Accent, Int>> { WidgetKindColors.defaults }

@Composable
private fun accentColor(accent: Accent): ColorProvider {
    val override = LocalAccentOverride.current
    val kindColors = LocalKindColors.current
    return ColorProvider(Color(override ?: kindColors.getValue(accent)))
}

open class HiHanaEvoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HiHanaEvoWidget()

    // 마지막 위젯이 제거되면 provideGlance 가 다시 돌 일이 없어 틱 알람을 스스로 멈출 수
    // 없으므로, 여기서 확실히 취소합니다.
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetTickReceiver.cancel(context)
    }
}
