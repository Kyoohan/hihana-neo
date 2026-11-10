package com.skh.hihanaevo.widget

import androidx.activity.ComponentActivity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.lifecycleScope
import com.skh.hihanaevo.Accent
import com.skh.hihanaevo.Block
import com.skh.hihanaevo.BlockKind
import com.skh.hihanaevo.Gap
import com.skh.hihanaevo.HomeStayExtras
import com.skh.hihanaevo.HomeStayNotice
import com.skh.hihanaevo.Lesson
import com.skh.hihanaevo.NextUp
import com.skh.hihanaevo.OutingSpan
import com.skh.hihanaevo.R
import com.skh.hihanaevo.StudyPlace
import com.skh.hihanaevo.StudySession
import com.skh.hihanaevo.WeatherTile
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 위젯 미리보기 (dev 빌드 전용) — 실제 위젯 그림([WidgetRoot])을 상황 × 크기 격자로 늘어놓습니다. 한 줄이 한 상황,
 * 옆으로 크기(삼성 홈 4열 격자의 칸 수). 바탕은 배경화면처럼 짙은 그라데이션이라 반투명 위젯이 보이는 그대로.
 *
 *   adb shell am start -n com.skh.hihanaevo.dev/com.skh.hihanaevo.widget.WidgetPreviewActivity [--ez dark true]
 */
class WidgetPreviewActivity : ComponentActivity() {

    /** 삼성 홈(S25+, 4×5 격자) 위젯 칸 크기 — 칸 수 → dp. 폭은 런처가 알려 주는 세로 모드 값. */
    private val sizes = listOf(
        "2×1" to DpSize(150.dp, 115.dp),
        "3×1" to DpSize(235.dp, 115.dp),
        "4×1" to DpSize(320.dp, 115.dp),
        "2×2" to DpSize(150.dp, 180.dp),
        "3×2" to DpSize(235.dp, 180.dp),
        "4×2" to DpSize(320.dp, 180.dp),
        "2×3" to DpSize(150.dp, 260.dp),
        "4×3" to DpSize(320.dp, 260.dp),
    )

    private val today = LocalDate.now()
    private fun at(h: Int, m: Int) = today.atTime(h, m)

    private fun lesson(subject: String, room: String?) = Lesson(subject, room)

    /** 상황 — 이름과 그때 위젯이 받는 값. */
    private fun cases(): List<Pair<String, WidgetSnapshot>> {
        val math = lesson("미적분Ⅱ(3반) 김하나 /", "A304")
        val english = lesson("심화 영어 독해Ⅰ(2반) 박서윤 /", "B304")
        val longSubject = lesson("인공지능을 위한 선형대수학(1반) 이도윤 /", "융합관 2층 컴퓨터실2")
        val pe = lesson("스포츠 생활1(남)(5반) 김응주 /", "체육관")
        val free = Lesson.FREE
        fun snap(
            block: Block?,
            next: Block? = null,
            meal: String? = null,
            allergy: String = "",
            supervision: String? = null,
            homeStay: HomeStayNotice? = null,
            extras: HomeStayExtras? = null,
            upcoming: List<Block> = listOfNotNull(next),
        ) = WidgetSnapshot(
            block = block, homeStay = homeStay, homeStayExtras = extras, nextTitle = next?.title, nextRoom = next?.let(::widgetPlace),
            upcoming = upcoming.map { UpcomingRow(it.start.toLocalTime().toString(), rowTitle(it), widgetPlace(it)) },
            mealText = meal, mealAllergyPrefix = allergy, supervisionText = supervision, remainingMillis = 23 * 60_000L + 41_000L,
            refreshDelayMillis = -1,
        )
        val lunchGap = Gap("점심시간", "점심", "다음 수업까지", NextUp.LessonNext(5, longSubject), Accent.LUNCH)
        val dinnerGap = Gap("저녁시간", "저녁", "1타임까지", NextUp.StudyNext(StudySession("1타임", 19 * 60, 21 * 60), StudyPlace.Library("2-46")), Accent.DINNER)
        val breakGap = Gap("쉬는 시간", "쉬는 시간", "다음 수업까지", NextUp.LessonNext(3, english), Accent.BREAK_TIME)
        val menu = "현미밥 · 소고기미역국 · 닭갈비 · 콩나물무침 · 배추김치 · 요구르트 · 과일샐러드"
        val tiles = HomeStayExtras(
            weatherNow = WeatherTile("지금 서울", R.drawable.ic_weather_partly, "17°", "구름 조금 · 강수 10%", null),
            weatherBack = WeatherTile("귀교 21시", R.drawable.ic_weather_rain, "12°", "비 · 강수 80%", "우산 챙기세요"),
            firstPlan = "면학 4타임 · 6층 몰입형G G16" to "21:30",
            exam = "2학기 기말고사" to 12L,
        )
        // 하루 — 수업 2~7교시, 점심, 저녁, 면학 두 타임. '이후 일정'은 지금 블록(쉬는 시간이면 그 다음 블록) 뒤의 것들.
        val l2 = Block(BlockKind.LessonKind(2, math), at(9, 55), at(10, 45))
        val l3 = Block(BlockKind.LessonKind(3, english), at(10, 55), at(11, 45))
        val l4 = Block(BlockKind.LessonKind(4, longSubject), at(11, 55), at(12, 45))
        val l5 = Block(BlockKind.LessonKind(5, pe), at(13, 50), at(14, 40))
        val l6 = Block(BlockKind.LessonKind(6, free), at(14, 50), at(15, 40))
        val l7 = Block(BlockKind.LessonKind(7, math), at(15, 50), at(16, 40))
        val s1 = Block(BlockKind.StudyKind(StudySession("1타임", 1140, 1260), StudyPlace.Library("2-46")), at(19, 0), at(21, 0))
        val s2 = Block(BlockKind.StudyKind(StudySession("2타임", 1290, 1410), StudyPlace.StudyRoom("6층 몰입형G G16")), at(21, 30), at(23, 30))
        val day = listOf(l2, l3, l4, l5, l6, l7, s1, s2)
        fun after(b: Block) = day.filter { it.start.isAfter(b.start) }
        fun lessonSnap(b: Block, meal: String? = null, allergy: String = "", supervision: String? = null): WidgetSnapshot {
            val rest = after(b)
            return snap(b, next = rest.firstOrNull(), meal = meal, allergy = allergy, supervision = supervision, upcoming = rest)
        }
        fun gapSnap(gap: Gap, from: Block, to: Block, heroBlock: Block, meal: String? = null, allergy: String = "", supervision: String? = null): WidgetSnapshot {
            val rest = after(heroBlock)
            return snap(Block(BlockKind.GapKind(gap), from.end, to.start), next = rest.firstOrNull(), meal = meal, allergy = allergy, supervision = supervision, upcoming = rest)
        }
        return listOf(
            "수업 (짧은 과목·교실)" to lessonSnap(l2),
            "수업 (긴 과목·긴 교실)" to lessonSnap(l4),
            "공강" to lessonSnap(l6),
            "쉬는 시간" to gapSnap(breakGap, l2, l3, l3),
            "점심 (메뉴)" to gapSnap(lunchGap, l4, l5, l5, meal = menu),
            "점심 (메뉴 + 알레르기)" to gapSnap(lunchGap, l4, l5, l5, meal = menu, allergy = "⚠ 우유·대두 "),
            "저녁 (메뉴 + 면학감독)" to gapSnap(dinnerGap, l7, s1, s1, meal = menu, supervision = "면학감독 김하나 박서윤"),
            "저녁 끝무렵 (메뉴 없음 + 면학감독)" to gapSnap(dinnerGap, l7, s1, s1, supervision = "면학감독 김하나 박서윤"),
            "면학 (도서관)" to lessonSnap(s1),
            "면학 (다음 없음)" to lessonSnap(s2),
            "외출" to snap(Block(BlockKind.OutingKind(OutingSpan(at(14, 0), at(18, 0), "병원 진료", false)), at(14, 0), at(18, 0))),
            "외박 (승인 대기)" to snap(Block(BlockKind.OutingKind(OutingSpan(at(17, 0), at(17, 0).plusDays(2), "가족 행사", true)), at(17, 0), at(23, 59))),
            "일정 없음" to snap(null),
            "귀가 (날씨·일정·시험)" to snap(null, homeStay = HomeStayNotice("편안한 귀가 보내세요", "11월 16일(일) 귀교", "11/16(일) 귀교"), extras = tiles),
            "귀가 (날씨 못 받음)" to snap(null, homeStay = HomeStayNotice("편안한 귀가 보내세요", "11월 16일(일) 귀교", "11/16(일) 귀교"), extras = tiles.copy(weatherNow = null, weatherBack = null)),
        )
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", true)
        val only = intent.getStringExtra("case")
        val density = resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).toInt()

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(12f), px(36f), px(12f), px(36f)) }
        val scroll = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1B3A6B.toInt(), 0xFF6B3A5A.toInt(), 0xFF1F5F4A.toInt()))
            addView(HorizontalScrollView(this@WidgetPreviewActivity).apply { addView(column) })
        }
        setContentView(scroll)

        val glance = GlanceRemoteViews()
        lifecycleScope.launch {
            val list = cases().filter { only == null || it.first.contains(only) }
            val blocks = mutableListOf<LinearLayout>()
            for ((name, snapshot) in list) {
                val block = LinearLayout(this@WidgetPreviewActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(px(8f), 0, px(8f), px(8f)) }
                column.addView(block)
                blocks += block
                block.addView(TextView(this@WidgetPreviewActivity).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, px(14f), 0, px(6f))
                })
                // 높이(행 수)가 같은 크기끼리 한 줄 — 한 장이 화면 폭 정도라 읽기 좋게.
                val rows = sizes.groupBy { it.second.height }.values.map { group ->
                    val row = LinearLayout(this@WidgetPreviewActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, px(8f)) }
                    block.addView(row)
                    row to group
                }
                for ((row, group) in rows) for ((label, size) in group) {
                    val cell = LinearLayout(this@WidgetPreviewActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, px(10f), 0) }
                    cell.addView(TextView(this@WidgetPreviewActivity).apply {
                        text = label
                        setTextColor(0xB3FFFFFF.toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    })
                    val frame = FrameLayout(this@WidgetPreviewActivity)
                    cell.addView(frame, LinearLayout.LayoutParams(px(size.width.value), px(size.height.value)))
                    row.addView(cell)
                    val accent = snapshot.block?.let { WidgetKindColors.defaults.getValue(it.accent) } ?: 0xFFFFFFFF.toInt()
                    val countdown = snapshot.remainingMillis?.takeIf { snapshot.block != null }?.let {
                        countdownRemoteViews(this@WidgetPreviewActivity, it, accent, snapshot.block?.countdownCaption, if (dark) 0xFFE2E8F0.toInt() else 0xFF475569.toInt())
                    }
                    runCatching {
                        val views = glance.compose(this@WidgetPreviewActivity, size) {
                            WidgetRoot(snapshot, 72, dark, null, null, WidgetKindColors.defaults, countdown)
                        }.remoteViews
                        frame.addView(views.apply(this@WidgetPreviewActivity, frame), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }.onFailure { e ->
                        frame.addView(TextView(this@WidgetPreviewActivity).apply { text = "오류 ${e.message}"; setTextColor(Color.RED) })
                    }
                }
            }
            // 상황마다 한 장씩 PNG 로 — adb pull /sdcard/Android/data/com.skh.hihanaevo.dev/files/widget-preview
            column.post {
                val dir = java.io.File(getExternalFilesDir(null), "widget-preview").apply { deleteRecursively(); mkdirs() }
                blocks.forEachIndexed { i, b ->
                    val bmp = android.graphics.Bitmap.createBitmap(b.width.coerceAtLeast(1), b.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    scroll.background.setBounds(0, 0, b.width, b.height)
                    scroll.background.draw(canvas)
                    b.draw(canvas)
                    java.io.File(dir, "%02d.png".format(i)).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
                android.util.Log.d("WidgetPreview", "saved ${blocks.size}")
            }
        }
    }
}
