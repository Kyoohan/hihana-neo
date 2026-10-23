package com.skh.hihanaevo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * One UI 결의 아이콘 — 삼성 기본 앱(시계·삼성 헬스·캘린더·내 파일)의 하단 섬 바와 툴바 아이콘처럼 가는 선(24 격자에 1.55),
 * 둥근 끝·둥근 꺾임. 하단 바는 고른 탭만 채운 모양([Filled])이고 나머지는 선 모양입니다. 채운 모양은 선 모양과 바깥
 * 크기가 같도록 칠과 같은 굵기의 선을 함께 그리고, 안쪽 무늬(문·글줄·점)는 비워 둡니다.
 *
 * 삼성 아이콘 파일을 가져온 게 아니라 같은 규칙으로 새로 그린 것입니다.
 */
object OneUiIcons {
    private const val STROKE = 1.55f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    /** 선 — 둥근 끝·둥근 꺾임. */
    private fun ImageVector.Builder.line(block: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.Black), strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
    )

    /** 칠 — [evenOdd] 면 안쪽 겹친 모양(문·글줄·점)이 비워집니다. */
    private fun ImageVector.Builder.solid(evenOdd: Boolean = false, block: PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.Black), pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block,
    )

    /** 칠 + 같은 굵기 선 — 선 모양 아이콘과 바깥 크기를 맞춘 채운 조각. */
    private fun ImageVector.Builder.blob(block: PathBuilder.() -> Unit) {
        solid(block = block)
        line(block)
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        horizontalLineTo(r - rad); quadTo(r, t, r, t + rad)
        verticalLineTo(b - rad); quadTo(r, b, r - rad, b)
        horizontalLineTo(l + rad); quadTo(l, b, l, b - rad)
        verticalLineTo(t + rad); quadTo(l, t, l + rad, t)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    // MARK: - 하단 섬 바 (선 / 채움)

    private fun PathBuilder.house() {
        moveTo(4.5f, 10.3f); lineTo(10.6f, 4.9f); quadTo(12f, 3.7f, 13.4f, 4.9f); lineTo(19.5f, 10.3f)
        quadTo(20f, 10.75f, 20f, 11.5f); verticalLineTo(17.5f); quadTo(20f, 20f, 17.5f, 20f)
        horizontalLineTo(6.5f); quadTo(4f, 20f, 4f, 17.5f); verticalLineTo(11.5f); quadTo(4f, 10.75f, 4.5f, 10.3f); close()
    }

    val Home: ImageVector by lazy {
        icon("Home") {
            line { house() }
            line { moveTo(10f, 20f); verticalLineTo(16f); quadTo(10f, 15f, 11f, 15f); horizontalLineTo(13f); quadTo(14f, 15f, 14f, 16f); verticalLineTo(20f) }
        }
    }

    val HomeFilled: ImageVector by lazy {
        icon("HomeFilled") {
            solid(evenOdd = true) {
                moveTo(3.9f, 9.7f); lineTo(10.1f, 4.3f); quadTo(12f, 2.75f, 13.9f, 4.3f); lineTo(20.1f, 9.7f)
                quadTo(20.8f, 10.3f, 20.8f, 11.4f); verticalLineTo(17.6f); quadTo(20.8f, 20.8f, 17.6f, 20.8f)
                horizontalLineTo(6.4f); quadTo(3.2f, 20.8f, 3.2f, 17.6f); verticalLineTo(11.4f); quadTo(3.2f, 10.3f, 3.9f, 9.7f); close()
                // 문
                moveTo(10.25f, 20.8f); verticalLineTo(16.4f); quadTo(10.25f, 15.3f, 11.35f, 15.3f); horizontalLineTo(12.65f)
                quadTo(13.75f, 15.3f, 13.75f, 16.4f); verticalLineTo(20.8f); close()
            }
        }
    }

    private fun PathBuilder.pencil() {
        moveTo(15.2f, 4.8f); quadTo(16.2f, 3.8f, 17.2f, 4.8f); lineTo(19.2f, 6.8f); quadTo(20.2f, 7.8f, 19.2f, 8.8f)
        lineTo(9f, 19f); lineTo(4.6f, 19.4f); lineTo(5f, 15f); close()
    }

    /** 연필 — 신청·내역 탭, 순서 편집. */
    val Edit: ImageVector by lazy {
        icon("Edit") {
            line { pencil() }
            line { moveTo(13.5f, 6.5f); lineTo(17.5f, 10.5f) }
        }
    }

    val EditFilled: ImageVector by lazy {
        icon("EditFilled") {
            blob { moveTo(15.2f, 4.8f); quadTo(16.2f, 3.8f, 17.2f, 4.8f); lineTo(19.2f, 6.8f); quadTo(20.2f, 7.8f, 19.2f, 8.8f); lineTo(18.5f, 9.5f); lineTo(14.5f, 5.5f); close() }
            blob { moveTo(12.5f, 7.5f); lineTo(16.5f, 11.5f); lineTo(9f, 19f); lineTo(4.6f, 19.4f); lineTo(5f, 15f); close() }
        }
    }

    private fun PathBuilder.spoon() {
        moveTo(16.5f, 3.5f); curveTo(14.8f, 3.5f, 14f, 5.3f, 14f, 7.2f); curveTo(14f, 9.1f, 15.1f, 10.6f, 16.5f, 10.7f)
        curveTo(17.9f, 10.6f, 19f, 9.1f, 19f, 7.2f); curveTo(19f, 5.3f, 18.2f, 3.5f, 16.5f, 3.5f); close()
    }

    /** 포크와 숟가락 — 급식 탭. */
    val Meal: ImageVector by lazy {
        icon("Meal") {
            line { moveTo(5.5f, 3.5f); verticalLineTo(8f); quadTo(5.5f, 11f, 7.5f, 11f); quadTo(9.5f, 11f, 9.5f, 8f); verticalLineTo(3.5f) }
            line { moveTo(7.5f, 3.5f); verticalLineTo(20.5f) }
            line { spoon() }
            line { moveTo(16.5f, 10.7f); verticalLineTo(20.5f) }
        }
    }

    val MealFilled: ImageVector by lazy {
        icon("MealFilled") {
            line { moveTo(5.5f, 3.5f); verticalLineTo(8f) }
            line { moveTo(9.5f, 3.5f); verticalLineTo(8f) }
            line { moveTo(7.5f, 3.5f); verticalLineTo(20.5f) }
            blob { moveTo(5.5f, 7.6f); horizontalLineTo(9.5f); verticalLineTo(8f); quadTo(9.5f, 11f, 7.5f, 11f); quadTo(5.5f, 11f, 5.5f, 8f); close() }
            blob { spoon() }
            line { moveTo(16.5f, 10.7f); verticalLineTo(20.5f) }
        }
    }

    /** 글줄이 든 판 — 게시판 탭. */
    val Board: ImageVector by lazy {
        icon("Board") {
            line { roundRect(4.5f, 3.5f, 19.5f, 20.5f, 3f) }
            line { moveTo(8.5f, 8.5f); horizontalLineTo(15.5f) }
            line { moveTo(8.5f, 12f); horizontalLineTo(15.5f) }
            line { moveTo(8.5f, 15.5f); horizontalLineTo(12.5f) }
        }
    }

    val BoardFilled: ImageVector by lazy {
        icon("BoardFilled") {
            solid(evenOdd = true) {
                roundRect(3.7f, 2.7f, 20.3f, 21.3f, 3.8f)
                roundRect(7.7f, 7.7f, 16.3f, 9.3f, 0.8f)
                roundRect(7.7f, 11.2f, 16.3f, 12.8f, 0.8f)
                roundRect(7.7f, 14.7f, 13.3f, 16.3f, 0.8f)
            }
        }
    }

    /** 학사모 — 클래스룸 탭. */
    val Classroom: ImageVector by lazy {
        icon("Classroom") {
            line { moveTo(12f, 4.5f); lineTo(21.5f, 9.2f); lineTo(12f, 13.9f); lineTo(2.5f, 9.2f); close() }
            line { moveTo(6.2f, 11.3f); verticalLineTo(15.6f); quadTo(12f, 19.6f, 17.8f, 15.6f); verticalLineTo(11.3f) }
            line { moveTo(21.5f, 9.2f); verticalLineTo(14f) }
        }
    }

    val ClassroomFilled: ImageVector by lazy {
        icon("ClassroomFilled") {
            blob { moveTo(12f, 4.5f); lineTo(21.5f, 9.2f); lineTo(12f, 13.9f); lineTo(2.5f, 9.2f); close() }
            blob { moveTo(6.2f, 13.9f); lineTo(12f, 16.8f); lineTo(17.8f, 13.9f); verticalLineTo(15.9f); quadTo(12f, 20f, 6.2f, 15.9f); close() }
            line { moveTo(21.5f, 9.2f); verticalLineTo(14f) }
        }
    }

    private val calendarDots = listOf(8.5f to 13.6f, 12f to 13.6f, 15.5f to 13.6f, 8.5f to 17f, 12f to 17f)

    /** 달력 — 일정 탭, 주간 보기·학사일정 보기. */
    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            line { roundRect(4f, 5f, 20f, 20.5f, 3f) }
            line { moveTo(4f, 9.8f); horizontalLineTo(20f) }
            line { moveTo(8.5f, 3.3f); verticalLineTo(6.6f) }
            line { moveTo(15.5f, 3.3f); verticalLineTo(6.6f) }
            solid { calendarDots.forEach { (x, y) -> circle(x, y, 0.95f) } }
        }
    }

    val CalendarFilled: ImageVector by lazy {
        icon("CalendarFilled") {
            solid(evenOdd = true) {
                roundRect(3.2f, 4.2f, 20.8f, 21.3f, 3.8f)
                calendarDots.forEach { (x, y) -> circle(x, y, 1.05f) }
            }
            line { moveTo(8.5f, 2.6f); verticalLineTo(5.6f) }
            line { moveTo(15.5f, 2.6f); verticalLineTo(5.6f) }
        }
    }

    /** 타임라인 — 세로 줄 위 세 마디와 오른쪽 일정 줄(Evo 하단 바). */
    val Timeline: ImageVector by lazy {
        icon("Timeline") {
            listOf(6f, 12f, 18f).forEach { y -> line { circle(6.5f, y, 2f) } }
            line { moveTo(6.5f, 8f); verticalLineTo(10f) }
            line { moveTo(6.5f, 14f); verticalLineTo(16f) }
            line { moveTo(11f, 6f); horizontalLineTo(19.5f) }
            line { moveTo(11f, 12f); horizontalLineTo(17f) }
            line { moveTo(11f, 18f); horizontalLineTo(18.5f) }
        }
    }

    val TimelineFilled: ImageVector by lazy {
        icon("TimelineFilled") {
            solid { listOf(6f, 12f, 18f).forEach { y -> circle(6.5f, y, 2.6f) } }
            line { moveTo(6.5f, 8f); verticalLineTo(16f) }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round) {
                moveTo(11.2f, 6f); horizontalLineTo(19.5f)
                moveTo(11.2f, 12f); horizontalLineTo(17f)
                moveTo(11.2f, 18f); horizontalLineTo(18.5f)
            }
        }
    }

    private fun PathBuilder.fourTiles(inset: Float, rad: Float) {
        roundRect(4f - inset, 4f - inset, 10.8f + inset, 10.8f + inset, rad)
        roundRect(13.2f - inset, 4f - inset, 20f + inset, 10.8f + inset, rad)
        roundRect(4f - inset, 13.2f - inset, 10.8f + inset, 20f + inset, rad)
        roundRect(13.2f - inset, 13.2f - inset, 20f + inset, 20f + inset, rad)
    }

    /** 모든 기능 — 네모 넷(Evo 하단 바의 ⊞). */
    val Apps: ImageVector by lazy { icon("Apps") { line { fourTiles(0f, 2f) } } }

    val AppsFilled: ImageVector by lazy { icon("AppsFilled") { solid { fourTiles(0.6f, 2.4f) } } }

    // MARK: - 툴바·버튼 섬 (선)

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            line { moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 16.95f, 7.05f) }
            line { moveTo(13.35f, 7.05f); lineTo(16.95f, 7.05f); lineTo(16.95f, 3.45f) }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            line { circle(10.6f, 10.6f, 6.4f) }
            line { moveTo(15.4f, 15.4f); lineTo(20f, 20f) }
        }
    }

    /** 톱니 — 여덟 개의 둥근 이와 가운데 원. */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            line {
                val outer = 9f
                val inner = 7f
                for (i in 0 until 8) {
                    val c = Math.toRadians(i * 45.0)
                    fun at(r: Float, deg: Double) = (12f + r * cos(c + Math.toRadians(deg)).toFloat()) to (12f + r * sin(c + Math.toRadians(deg)).toFloat())
                    val (x0, y0) = at(inner, -14.0)
                    val (x1, y1) = at(outer, -9.0)
                    val (x2, y2) = at(outer, 9.0)
                    val (x3, y3) = at(inner, 14.0)
                    if (i == 0) moveTo(x0, y0) else lineTo(x0, y0)
                    lineTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3)
                }
                close()
            }
            line { circle(12f, 12f, 2.9f) }
        }
    }

    /** 받은 편지함 쟁반 — 알리미. */
    val Inbox: ImageVector by lazy {
        icon("Inbox") {
            line { roundRect(4f, 5f, 20f, 19f, 3f) }
            line { moveTo(4f, 13.2f); horizontalLineTo(8.4f); lineTo(9.9f, 15.4f); horizontalLineTo(14.1f); lineTo(15.6f, 13.2f); horizontalLineTo(20f) }
        }
    }

    /** 해 — 날씨. */
    val Share: ImageVector by lazy {
        icon("Share") {
            line { circle(17f, 5.8f, 2.3f) }
            line { circle(6.8f, 12f, 2.3f) }
            line { circle(17f, 18.2f, 2.3f) }
            line { moveTo(8.76f, 10.81f); lineTo(15.04f, 6.99f) }
            line { moveTo(8.76f, 13.19f); lineTo(15.04f, 17.01f) }
        }
    }

    /** 점 달린 목록 — 학사일정 목록 보기. */
    val List: ImageVector by lazy {
        icon("List") {
            solid { circle(5.4f, 7f, 1.05f); circle(5.4f, 12f, 1.05f); circle(5.4f, 17f, 1.05f) }
            line { moveTo(9f, 7f); horizontalLineTo(19.5f) }
            line { moveTo(9f, 12f); horizontalLineTo(19.5f) }
            line { moveTo(9f, 17f); horizontalLineTo(19.5f) }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") { line { moveTo(5f, 12.6f); lineTo(9.8f, 17.2f); lineTo(19f, 7.2f) } }
    }

    /** 둥근 삼각 경고 — 알레르기 설정. */
    val Warning: ImageVector by lazy {
        icon("Warning") {
            line {
                moveTo(10.3f, 4.9f); quadTo(12f, 2.1f, 13.7f, 4.9f); lineTo(20.6f, 16.9f); quadTo(22.2f, 19.8f, 18.9f, 19.8f)
                horizontalLineTo(5.1f); quadTo(1.8f, 19.8f, 3.4f, 16.9f); close()
            }
            line { moveTo(12f, 9.4f); verticalLineTo(13.5f) }
            solid { circle(12f, 16.4f, 1f) }
        }
    }

    /** 종 — 새 글 알림 설정. */
    val Bell: ImageVector by lazy {
        icon("Bell") {
            line {
                moveTo(5.2f, 17.4f); quadTo(6.4f, 16.2f, 6.4f, 14.4f); verticalLineTo(11f); quadTo(6.4f, 5.3f, 12f, 5.3f)
                quadTo(17.6f, 5.3f, 17.6f, 11f); verticalLineTo(14.4f); quadTo(17.6f, 16.2f, 18.8f, 17.4f); close()
            }
            line { moveTo(10f, 20.2f); horizontalLineTo(14f) }
            line { moveTo(12f, 3.4f); verticalLineTo(5.3f) }
        }
    }
}
