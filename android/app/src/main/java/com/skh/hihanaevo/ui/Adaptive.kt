package com.skh.hihanaevo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 태블릿 대응 — 창 폭으로 폰(좁음)·중간·넓음을 가릅니다(안드로이드 창 크기 기준 600dp · 840dp).
 * 폰에서는 아무것도 바뀌지 않고, 넓은 창에서는 글·목록이 한 줄로 끝없이 늘어나지 않게 폭을 제한해 가운데 둡니다.
 */
object Adaptive {
    /** 목록·글 화면의 최대 폭 — 한 줄이 너무 길어지지 않는 폭 (갤럭시 탭 설정의 오른쪽 칸 정도). */
    val ReadingWidth: Dp = 760.dp

    /** 카드를 여러 열로 펼치는 화면(홈·시간표)의 최대 폭 — 세 열까지. */
    val GridWidth: Dp = 1400.dp


    /** 하단 바·시트·투어처럼 손에 쥐고 쓰는 조작부의 최대 폭. */
    val ControlWidth: Dp = 600.dp
}

/** 지금 창 폭(dp). */
@Composable
@ReadOnlyComposable
fun windowWidthDp(): Dp = LocalConfiguration.current.screenWidthDp.dp

/** 600dp 이상 — 폴드 펼침·태블릿. */
@Composable
@ReadOnlyComposable
fun isWideWindow(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/**
 * [maxContent] 폭의 콘텐츠를 가운데 두기 위한 좌우 여백 — 창이 좁으면 기본 페이지 여백([OneUi.PagePadding]) 그대로.
 */
@Composable
@ReadOnlyComposable
fun centeredSidePadding(maxContent: Dp, minimum: Dp = OneUi.PagePadding): Dp {
    val width = windowWidthDp()
    return maxOf(minimum, (width - maxContent) / 2)
}

/**
 * 840dp 이상(안드로이드 'expanded' 창) — 가로 태블릿·큰 폴드. 이 폭부터는 폰 화면을 늘리지 않고 큰 화면 틀을 씁니다:
 * 왼쪽 내비게이션 패널(삼성 시계·캘린더·내 파일과 같은 떠 있는 둥근 패널, 접으면 아이콘 레일), 접히지 않는 헤더,
 * 하단 바·버튼 섬 없음. 세로 태블릿(600~840dp)은 아직 폰 틀입니다.
 */
@Composable
@ReadOnlyComposable
fun isExpandedWindow(): Boolean = LocalConfiguration.current.screenWidthDp >= 840

/** [area] 폭 안에서 [maxContent] 폭의 콘텐츠를 가운데 두는 좌우 여백 — 최소 [minimum]. */
fun sidePaddingIn(area: Dp, maxContent: Dp, minimum: Dp): Dp = maxOf(minimum, (area - maxContent) / 2)
