package com.skh.hihanaevo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 머티리얼 아이콘 중 기본 묶음(material-icons-core)에 없는 것 — Google Material Icons(Apache 2.0)의 경로를 옮겼습니다.
 * 확장 묶음(material-icons-extended)은 수천 개라 앱이 몇 MB 커져(릴리스도 축소를 끔), 쓰는 것만 여기 둡니다.
 * 속이 빈 칸(구멍)은 짝홀 규칙으로 비웁니다.
 */
object MaterialExtraIcons {
    private fun icon(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(PathParser().parsePathString(d).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        }.build()

    /** 네모 넷 — Evo 하단 바의 ⊞ 모든 기능 (grid_view). */
    val GridView: ImageVector by lazy {
        icon("GridView", "M3 3v8h8V3H3zm6 6H5V5h4v4zm-6 4v8h8v-8H3zm6 6H5v-4h4v4zm4-16v8h8V3h-8zm6 6h-4V5h4v4zm-6 4v8h8v-8h-8zm6 6h-4v-4h4v4z")
    }
    val GridViewFilled: ImageVector by lazy { icon("GridViewFilled", "M3 3h8v8H3zM13 3h8v8h-8zM3 13h8v8H3zM13 13h8v8h-8z") }

    /** 시계 — Evo 하단 바의 타임라인 (schedule / watch_later). */
    val Schedule: ImageVector by lazy {
        icon("Schedule", "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zM12.5 7H11v6l5.25 3.15.75-1.23-4.5-2.67z")
    }
    val ScheduleFilled: ImageVector by lazy {
        icon("ScheduleFilled", "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM15.29 16.71L11 12.41V7h2v4.59l3.71 3.71z")
    }

    /** 표 — 시간표 (grid_on). */
    val GridOn: ImageVector by lazy {
        icon("GridOn", "M20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM8 20H4v-4h4v4zm0-6H4v-4h4v4zm0-6H4V4h4v4zm6 12h-4v-4h4v4zm0-6h-4v-4h4v4zm0-6h-4V4h4v4zm6 12h-4v-4h4v4zm0-6h-4v-4h4v4zm0-6h-4V4h4v4z")
    }

    /** 해 — 날씨 (wb_sunny). */
    val WbSunny: ImageVector by lazy {
        icon(
            "WbSunny",
            "M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z",
        )
    }
}
