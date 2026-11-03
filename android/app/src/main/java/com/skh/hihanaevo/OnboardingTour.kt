package com.skh.hihanaevo

import com.skh.hihanaevo.ui.OneUiBadge
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.skh.hihanaevo.ui.LocalHazeState
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiCard
import com.skh.hihanaevo.ui.OneUiLoading
import com.skh.hihanaevo.ui.OneUiTextField
import com.skh.hihanaevo.ui.isDark
import com.skh.hihanaevo.ui.oneUiPageBackground
import com.skh.hihanaevo.ui.drawPageBackground
import androidx.compose.foundation.layout.offset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 첫 실행 투어 — 옆으로 넘기는 전체 화면. [TourKind.WELCOME] 은 새로 설치한 사람에게 기능 소개 → 개인정보 보호 →
 * 권한 → 로그인 → 완료를, [TourKind.UPDATE] 는 이미 쓰던 사람에게 이번 버전에서 바뀐 것만 보여 줍니다.
 * 투어 자체는 건너뛸 수 없고 끝까지 넘겨야 닫힙니다(뒤로 가기도 앞 장으로만) — 확인용 dev 빌드에서만 건너뛸 수 있습니다.
 * 권한을 이미 모두 허용했으면 권한 장은 처음부터 빠집니다. 로그인 장은 항상 있고, 이미 연동돼 있으면 '이미 연결됨'을 보여 줍니다.
 * 권한은 허용하지 않고 넘어갈 수 있고, 로그인은 '나중에'로 미룰 수 있습니다.
 */
enum class TourKind { WELCOME, UPDATE }

/** 이 버전의 투어 — 앱을 열 때 이 값보다 작게 기록돼 있으면 한 번 보여 줍니다 (새로 설치는 새로 설치 투어, 업데이트는 업데이트 투어). */
const val TOUR_VERSION = 140
private const val TOUR_PREFS = "prompts"
private const val KEY_TOUR_VERSION = "tourVersion"

/** 이번 버전 투어를 아직 안 봤으면 보여 줄 종류, 봤으면 null. 설치 시각과 마지막 업데이트 시각이 같으면 새로 설치입니다. */
fun pendingTour(context: android.content.Context): TourKind? {
    val seen = context.getSharedPreferences(TOUR_PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY_TOUR_VERSION, -1)
    if (seen >= TOUR_VERSION) return null
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val fresh = info == null || info.firstInstallTime == info.lastUpdateTime
    return if (fresh) TourKind.WELCOME else TourKind.UPDATE
}

fun markTourSeen(context: android.content.Context) {
    context.getSharedPreferences(TOUR_PREFS, android.content.Context.MODE_PRIVATE).edit().putInt(KEY_TOUR_VERSION, TOUR_VERSION).apply()
}

private enum class TourPage {
    /** 처음 투어 첫 장 — 업데이트 투어 첫 장처럼 오프닝 끝 장면이 가운데로 들어가는 벤토, 내용은 앱 전체. */
    OVERVIEW,
    WIDGET, NOW_BAR, APPLY, SEATS, MEAL, BOARD, PERSONALIZE, GLASS, PRIVACY, PERMISSIONS, LOGIN, DONE,
    /** 세 칸 하단 바와 손동작 — 환영·업데이트 투어 둘 다. */
    NAVIGATE,
    /** 뒤로 가기와 쓸어내리기 연습 — 조작법 바로 다음 장. */
    PRACTICE,
    /** 클래스룸 연결 — 처음 투어는 로그인 다음, 업데이트 투어는 맨 끝. 연결 웹 앱이 없는 빌드에선 빠집니다. */
    CLASSROOM_LINK,
    UPDATE_EVO, UPDATE_HOME, UPDATE_TIMELINE, UPDATE_AGENDA, UPDATE_GCLASSROOM, UPDATE_ALERTS, UPDATE_ICONS, UPDATE_MORE,
    /** 데일리 브리핑 — 대표 기능이라 홈 장에서 떼어 한 장으로. */
    UPDATE_BRIEF,
    /** 배경 사진을 쓰던 사람에게만 — 사진을 그대로 둘지, 새 기본 배경으로 바꿀지. */
    UPDATE_BACKGROUND,
}

/** 투어의 원 아이콘 색 — 설정 첫 화면과 같은 One UI 9 설정 색. */
private val TourBlue = Color(0xFF387AFF)
private val TourOrange = Color(0xFFE65B17)
private val TourGreen = Color(0xFF65C23B)
private val TourViolet = Color(0xFF715AFF)
private val TourYellow = Color(0xFFFDBE4E)
private val TourPink = Color(0xFFEC5881)
private val TourSlate = Color(0xFF6868A3)

private fun notificationsGranted(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
fun OnboardingTour(
    kind: TourKind,
    onFinish: () -> Unit,
    /** 로그인 장에서 실제로 로그인에 성공한 순간 — 투어가 끝나길 기다리지 않고 바로 시간표·일정 등을 받아 옵니다. */
    onAccountLinked: () -> Unit = {},
    /** 테마·강조색·배경화면 장 — 설정과 같은 값·동작. 고르는 즉시 앱(투어 포함) 색이 바뀝니다. */
    appearance: TourAppearance? = null,
) {
    // 환영 화면의 하늘(낮·밤)은 앱 테마를 따릅니다 — 투어를 오프닝 결(어두운 남색)로 바꾸기 전에 읽어 둡니다.
    val appDark = MaterialTheme.colorScheme.isDark
    if (kind == TourKind.WELCOME) {
        // 처음 설치 투어는 오프닝 애니메이션과 같은 결 — 남색 바탕·로고 세 갈래 색·Pretendard 제목·흰 알약 버튼(EvoTourStyle).
        EvoTourTheme(accentArgb = appearance?.accentArgb ?: PlanStore.AUTO_ACCENT_COLOR) {
            CompositionLocalProvider(LocalEvoTour provides true) { TourContent(kind, onFinish, onAccountLinked, appearance, appDark) }
        }
    } else {
        TourContent(kind, onFinish, onAccountLinked, appearance, appDark)
    }
}

@Composable
private fun TourContent(
    kind: TourKind,
    onFinish: () -> Unit,
    onAccountLinked: () -> Unit,
    appearance: TourAppearance?,
    appDark: Boolean,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // 투어를 여는 순간 한 번만 정합니다 — 권한 장에서 허용하자마자 그 장이 사라져 페이지가 밀리지 않게.
    val pages = remember(kind) {
        val needsPermissions = !notificationsGranted(context) || !ExactAlarmPermission.isGranted(context)
        when (kind) {
            TourKind.WELCOME -> buildList {
                addAll(
                    listOf(
                        TourPage.OVERVIEW, TourPage.NAVIGATE, TourPage.PRACTICE, TourPage.WIDGET, TourPage.NOW_BAR, TourPage.APPLY, TourPage.SEATS, TourPage.MEAL, TourPage.BOARD,
                        TourPage.PERSONALIZE, TourPage.GLASS, TourPage.PRIVACY,
                    ),
                )
                if (needsPermissions) add(TourPage.PERMISSIONS)
                add(TourPage.LOGIN)
                if (ClassroomBridge.available) add(TourPage.CLASSROOM_LINK)
                add(TourPage.DONE)
            }
            TourKind.UPDATE -> buildList {
                addAll(
                    listOf(
                        // 표지 다음은 대표 기능 둘 — 클래스룸, 데일리 브리핑 — 그다음 조작법과 화면별.
                        TourPage.UPDATE_EVO, TourPage.UPDATE_GCLASSROOM, TourPage.UPDATE_BRIEF,
                        TourPage.NAVIGATE, TourPage.PRACTICE, TourPage.UPDATE_HOME, TourPage.UPDATE_TIMELINE, TourPage.UPDATE_AGENDA,
                        TourPage.UPDATE_ALERTS, TourPage.UPDATE_ICONS,
                    ),
                )
                if (com.skh.hihanaevo.ui.PageBackgroundStore.exists(context)) add(TourPage.UPDATE_BACKGROUND)
                add(TourPage.UPDATE_MORE)
                if (needsPermissions) add(TourPage.PERMISSIONS)
                if (ClassroomBridge.available) add(TourPage.CLASSROOM_LINK)
            }
        }
    }
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var showingPrivacy by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(true) }
    // 처음 투어는 오프닝 다음에 환영 화면(GlassWelcome) — 오프닝의 완성 로고가 유리 구슬 속 표식 자리로 날아가 앉고, 어두운
    // 바탕이 걷히며 환영 화면이 드러납니다. 업데이트 투어는 예전대로 첫 장이 오프닝 끝 장면을 그대로 그려(EvoFinalFrame)
    // 아무것도 옮기지 않고 어두운 바탕만 걷힙니다.
    val welcome = kind == TourKind.WELCOME
    val handoff = remember(kind) { if (welcome) EvoHandoff(exact = true) else EvoHandoff(exact = true, stay = true) }
    var welcoming by remember { mutableStateOf(welcome) }
    var welcomeSeen by remember { mutableStateOf(false) }
    // 환영 화면 ↔ 투어 첫 장 — 들어올 땐 300ms, 나갈 땐 더 빨리(220ms).
    val welcomeShown by androidx.compose.animation.core.animateFloatAsState(
        if (welcoming) 1f else 0f,
        androidx.compose.animation.core.tween(if (welcoming) 300 else 220, easing = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "welcome",
    )
    // 첫 장 — 처음 투어는 앱 전체 개요, 업데이트 투어는 새 기능.
    val evoPage = if (kind == TourKind.UPDATE) TourPage.UPDATE_EVO else TourPage.OVERVIEW
    // 장마다 스크롤 — '다음'은 아직 끝까지 내리지 않았으면 먼저 끝까지 내리고, 한 번 더 눌러야 넘어갑니다.
    val pageScrolls = remember(pages) { pages.map { androidx.compose.foundation.ScrollState(0) } }
    // 업데이트 투어 첫 장 — 오프닝이 끝나면 끝 장면(로고·글자)이 살짝 위로 떠오르고, 그 아래로 새 기능 목록이 차례로 올라옵니다.
    // 목록을 스크롤하면 떠오른 로고·글자도 함께 올라갑니다.
    val evoLift = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(handoff) {
        // 바탕이 바뀌기 시작할 때 함께 — 바탕이 걷히는 동안 로고가 줄며 떠오릅니다.
        snapshotFlow { handoff.progress() > 0f }.first { it }
        evoLift.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0f, 1f)))
    }
    // 로그인 화면의 '로그인' 버튼은 입력·확인 상태를 알아야 해서, 페이지가 올려 주는 동작으로 바꿔 끼웁니다.
    var loginAction by remember { mutableStateOf<LoginAction?>(null) }
    // 뒤로 가기·쓸어내리기 연습을 끝까지 해야 다음 장으로 — 새 조작법을 직접 한 번은 해 보게.
    var practiced by remember { mutableStateOf(false) }

    fun next() {
        val scroll = pageScrolls[pagerState.currentPage]
        when {
            scroll.value < scroll.maxValue - 4 -> scope.launch { scroll.animateScrollTo(scroll.maxValue) }
            pagerState.currentPage >= pages.lastIndex -> onFinish()
            else -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    // 업데이트 투어는 글이 길고 이미 고른 사진 위에 뜹니다 — 사진 위 회색 설명이 밝은 곳에서 묻혀, 정보·변경 사항 화면처럼
    // 불투명 서리 바탕을 깔고 글자는 보통 테마색으로. 처음 투어는 늘 기본 배경 — 화면 장에서 사진을 골라도 투어는 그대로
    // (사진 위에선 설명·연습 글이 묻혔습니다). 고른 사진은 투어를 마치면 앱에 보입니다.
    val frostedPage = kind == TourKind.UPDATE
    // 투어는 다른 창이라 카드가 메인 화면의 배경 Haze 를 비출 수 없습니다 — 반투명 색으로.
    CompositionLocalProvider(
        com.skh.hihanaevo.ui.LocalMaterialHazeState provides null,
        com.skh.hihanaevo.ui.LocalOnPageColor provides null,
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        com.skh.hihanaevo.ui.LocalPageBackground provides if (welcome) null else com.skh.hihanaevo.ui.LocalPageBackground.current,
        com.skh.hihanaevo.ui.LocalBlurredPageBackground provides if (welcome) null else com.skh.hihanaevo.ui.LocalBlurredPageBackground.current,
    ) {
    Dialog(
        // 다이얼로그 창의 뒤로 가기는 여기로만 옵니다(안쪽 BackHandler 는 액티비티 쪽에 걸려 받지 못했습니다).
        // 앞 장으로만 가고, 첫 장에서는 닫히지 않습니다 — 건너뛸 수 없으니까요 (dev 빌드는 확인하기 편하게 닫힘).
        // 처음 투어는 환영 화면이 첫 장 앞의 한 장 — 첫 장에서 뒤로 가면 환영 화면으로(연출 없이 끝 장면).
        onDismissRequest = {
            if (welcoming) { if (BuildConfig.DEV) onFinish() }
            else if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            else if (welcome) welcoming = true
            else if (BuildConfig.DEV) onFinish()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        val view = LocalView.current
        val dark = scheme.isDark
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // 첫 장 벤토의 가운데 타일(로고 자리, 화면 기준) — 오프닝 끝 장면이 여기로 줄어들어 들어갑니다.
        var evoCenter by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        val density = LocalDensity.current
        val pagerTopPx = with(density) { (statusTop + 52.dp).toPx() }
        val page = pages[pagerState.currentPage]
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(Modifier.fillMaxSize().then(if (frostedPage || welcome) Modifier else Modifier.oneUiPageBackground()), contentAlignment = Alignment.TopCenter) {
            if (frostedPage) com.skh.hihanaevo.ui.OneUiFrostedPageBackdrop()
            if (welcome) EvoTourBackdrop(pagePosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction })
            // 태블릿에서는 투어를 손에 쥔 폰 화면 폭 정도로 가운데에 (폰은 그대로 전체 폭).
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = com.skh.hihanaevo.ui.Adaptive.ControlWidth)
                    .padding(top = statusTop + 12.dp, bottom = navBottom + 16.dp),
            ) {
                // 위: 진행 점 + (로그인 장에서만) 나중에 · dev 빌드는 모든 장에 건너뛰기
                Row(
                    Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TourDots(count = pages.size, current = pagerState.currentPage)
                    Spacer(Modifier.weight(1f))
                    if (page == TourPage.LOGIN && (BuildConfig.DEV || !HanaCredentialStore.hasCredentials(context))) {
                        Text(
                            "나중에",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { next() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    if (BuildConfig.DEV && pagerState.currentPage != pages.lastIndex) {
                        Text(
                            "건너뛰기 (dev)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onFinish)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !(pages[pagerState.currentPage] == TourPage.PRACTICE && !practiced),
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                ) { index ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(pageScrolls[index])
                            .padding(horizontal = 24.dp),
                    ) {
                        when (pages[index]) {
                            TourPage.OVERVIEW -> UpdateEvoPage(evoLift.value, everything = true) { evoCenter = it }
                            TourPage.WIDGET -> WidgetPage()
                            TourPage.NOW_BAR -> NowBarPage()
                            TourPage.APPLY -> ApplyPage()
                            TourPage.SEATS -> SeatsPage()
                            TourPage.MEAL -> MealPage()
                            TourPage.BOARD -> BoardPage()
                            TourPage.PERSONALIZE -> PersonalizePage(appearance)
                            TourPage.GLASS -> GlassPage()
                            TourPage.PRIVACY -> PrivacyPage(beforeLogin = kind == TourKind.WELCOME, onMore = { showingPrivacy = true })
                            TourPage.PERMISSIONS -> PermissionsPage()
                            TourPage.LOGIN -> LoginPage(onReady = { loginAction = it }, onLinked = onAccountLinked, onLoggedIn = ::next)
                            TourPage.DONE -> DonePage()
                            TourPage.CLASSROOM_LINK -> ClassroomLinkPage()
                            TourPage.UPDATE_EVO -> UpdateEvoPage(evoLift.value) { evoCenter = it }
                            TourPage.UPDATE_AGENDA -> UpdateAgendaPage()
                            TourPage.UPDATE_HOME -> UpdateHomePage()
                            TourPage.UPDATE_TIMELINE -> UpdateTimelinePage()
                            TourPage.UPDATE_GCLASSROOM -> UpdateGoogleClassroomPage()
                            TourPage.UPDATE_ALERTS -> UpdateAlertsPage()
                            TourPage.UPDATE_ICONS -> UpdateIconsPage()
                            TourPage.UPDATE_MORE -> UpdateMorePage()
                            TourPage.UPDATE_BACKGROUND -> UpdateBackgroundPage(appearance)
                            TourPage.UPDATE_BRIEF -> UpdateBriefPage()
                            TourPage.NAVIGATE -> NavigatePage()
                            TourPage.PRACTICE -> PracticePage(onDone = { practiced = true })
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                // 아래: 주 버튼 — 로그인 화면에서는 로그인, 마지막 장에서는 시작하기.
                val last = pagerState.currentPage == pages.lastIndex
                val login = loginAction.takeIf { page == TourPage.LOGIN }
                // 아직 아래가 남았으면 '더보기'(누르면 끝까지 내림), 끝까지 봤으면 넘어가는 말.
                val moreBelow by remember(pagerState.currentPage) {
                    derivedStateOf { pageScrolls[pagerState.currentPage].let { it.value < it.maxValue - 4 } }
                }
                TourButton(
                    text = when {
                        login != null && login.busy -> "확인 중"
                        login != null && login.label != null -> login.label
                        moreBelow -> "더보기"
                        last -> "시작하기"
                        page == evoPage -> "둘러보기"
                        else -> "다음"
                    },
                    enabled = (login?.enabled ?: true) && !(page == TourPage.PRACTICE && !practiced && !moreBelow),
                    busy = login?.busy == true,
                    onClick = { login?.run?.invoke() ?: next() },
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding().coerceAtLeast(0.dp)),
                )
            }
            }
        }
        // 처음 투어의 환영 화면 — 투어 위에 덮여 있다가 '가 볼까요'를 누르면 걷히며 첫 장이 드러납니다.
        // 뒤로 돌아와 다시 보일 땐 처음 연출 없이 끝 장면으로.
        if (welcome && (welcoming || welcomeShown > 0f)) GlassWelcome(
            reveal = { handoff.progress() },
            night = appDark,
            skipIntro = welcomeSeen,
            onContinue = { welcomeSeen = true; welcoming = false },
            onLogoBox = { handoff.target = it },
            modifier = Modifier.graphicsLayer { alpha = welcomeShown },
        )
        if (showingPrivacy) PrivacyScreen(onDismiss = { showingPrivacy = false })
        // 투어 첫 장면 — 리본이 로고로 펼쳐지는 오프닝이 끝나면(또는 누르면) 투어가 드러납니다.
        if (opening) EvoOpening(onFinish = { opening = false }, handoff = handoff)
        // 업데이트 투어 첫 장 — 오프닝 끝 장면(로고·'HiHana Evo'·'Neo를 넘어 Evo로')이 바탕이 걷히는 동안 줄어들며 벤토의
        // 가운데 타일로 들어갑니다. 화면 전체 좌표라 페이저 밖에 두고, 가운데 타일의 자리(넘기기·스크롤 포함)를 따라갑니다.
        run {
            val evoIndex = pages.indexOf(evoPage)
            EvoFinalFrame(
                // 오프닝의 흰 글자에서 투어 글자색으로 — 바탕이 걷히는 만큼.
                androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.86f), scheme.onSurface.copy(alpha = 0.9f), handoff.progress()),
                androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.66f), scheme.onSurfaceVariant, handoff.progress()),
                modifier = Modifier.graphicsLayer {
                    val offset = pagerState.currentPage + pagerState.currentPageOffsetFraction - evoIndex
                    val c = evoCenter
                    val p = evoLift.value
                    val w = size.width; val h = size.height
                    val top = evoFrameLogoTop(w, h); val bottom = evoFrameBottom(w, h, density.density)
                    val cy = (top + bottom) / 2f
                    if (c != null) {
                        // 끝 장면(로고 위 끝 ~ 부제 아래 끝)을 가운데 타일 높이의 82% 로, 타일 가운데로.
                        val target = c.height * 0.82f / (bottom - top)
                        val sc = 1f + (target - 1f) * p
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, cy / h)
                        scaleX = sc; scaleY = sc
                        translationX = (c.center.x - w / 2f) * p
                        translationY = (c.center.y - cy) * p
                    } else translationX = -offset * w
                    // 오프닝이 끝 장면을 넘겨줄 때부터 보입니다(그 전엔 오프닝이 직접 그림). 오프닝 위에 겹쳐야 걷히는 바탕 위로 보입니다.
                    // 스크롤로 가운데 타일이 위쪽 진행 점 줄 밑으로 들어가면 함께 옅어집니다.
                    alpha = when {
                        kotlin.math.abs(offset) >= 1f || (opening && handoff.progress() <= 0f) -> 0f
                        c != null && p >= 1f -> ((c.top + c.height * 0.4f - pagerTopPx) / (c.height * 0.4f)).coerceIn(0f, 1f)
                        else -> 1f
                    } * (1f - welcomeShown)   // 환영 화면이 덮여 있는 동안은 첫 장 로고도 숨깁니다(로고는 환영 화면 구슬 속에).
                },
            )
        }
    }
    }
}

/** 로그인 화면이 아래 주 버튼에 올려 주는 상태 — [label] 이 null 이면 평소 '다음'. */
private class LoginAction(val label: String?, val enabled: Boolean, val busy: Boolean, val run: (() -> Unit)?)

@Composable
private fun TourDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(
                if (i == current) 18.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                label = "dot",
            )
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .then(
                        // 처음 설치 투어는 지금 장 점을 'Evo' 그라데이션으로.
                        if (i == current && LocalEvoTour.current) Modifier.background(EvoTourBrush)
                        else Modifier.background(
                            if (i == current) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun TourButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 처음 설치 투어는 남색 위의 흰 알약(오프닝 결), 업데이트 투어는 예전 파란 알약.
    val evo = LocalEvoTour.current
    val fill = if (evo) EvoButtonFill else TourBlue
    val ink = if (evo) EvoButtonText else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            OneUiLoading(size = 16.dp, stroke = 2.dp, color = ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
    }
}

// MARK: - 페이지 공통 조각

@Composable
private fun ColumnScope.PageHead(eyebrow: String, title: String, desc: String? = null, isNew: Boolean = false) {
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 처음 설치 투어의 머리말은 'Evo' 그라데이션 글자.
        if (LocalEvoTour.current) Text(eyebrow, style = evoEyebrowStyle(MaterialTheme.typography.labelLarge))
        else Text(eyebrow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        if (isNew) {
            Spacer(Modifier.width(6.dp))
            Text(
                "NEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F1A16),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF9FE0B4)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    if (desc != null) {
        Spacer(Modifier.height(10.dp))
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun CircleIcon(painter: Painter, tint: Color, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        Icon(painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** 색 원 아이콘 + 제목 + 설명 한 줄, 오른쪽에 선택 요소. */
@Composable
private fun TourRow(painter: Painter, tint: Color, title: String, body: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        CircleIcon(painter, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (body != null) {
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
private fun RowGap() = Spacer(Modifier.height(16.dp))

/** 권한 줄 오른쪽 — 허용 전엔 파란 알약 버튼, 허용 뒤엔 초록 '허용됨'. */
@Composable
private fun GrantChip(granted: Boolean, label: String, onClick: () -> Unit) {
    val shape = CircleShape
    if (granted) {
        Row(
            Modifier.clip(shape).background(TourGreen.copy(alpha = 0.16f)).padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF8FDC6B), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("허용됨", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF8FDC6B))
        }
    } else {
        // 처음 설치 투어는 주 버튼과 같은 흰 알약(남색 위), 업데이트 투어는 파란 알약.
        val evo = LocalEvoTour.current
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (evo) EvoButtonText else Color.White,
            modifier = Modifier.clip(shape).background(if (evo) EvoButtonFill else TourBlue).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
        )
    }
}

// MARK: - 새로 설치
// 투어 문구 규칙: 제목은 명사형(문장 X), 설명은 문장으로. 로그인 장의 '학사시스템에 로그인하세요'만 예외.

/** 테마·강조색·배경화면 장이 쓰는 설정 값과 바꾸는 동작 — MainActivity 의 설정 화면과 같은 것을 넘겨받습니다. */
class TourAppearance(
    val accentArgb: Int,
    val onAccentChange: (Int) -> Unit,
    val appTheme: String,
    val onAppThemeChange: (String) -> Unit,
    val hasBackgroundPhoto: Boolean,
    val onBackgroundChanged: () -> Unit,
)

/** 홈 화면 배경을 흉내 낸 판 — 위젯·Now Bar 그림을 얹습니다. */
@Composable
private fun WallpaperPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF1B4A7A), Color(0xFF2E6FA6), Color(0xFFE39A5B)),
                ),
            )
            .padding(16.dp),
        content = content,
    )
}

/** 실제 위젯(2×2)의 모양 — 상태 점·구간, 장소(크게), 다음 줄, 아래 내용. */
@Composable
private fun WidgetMock(status: String, dot: Color, hero: String, modifier: Modifier = Modifier, body: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB9C0C8), maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Text(hero, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        body()
    }
}

@Composable
private fun ColumnScope.WidgetPage() {
    PageHead(
        "위젯",
        "홈 화면 위젯",
        "홈 화면 위젯에 지금 있어야 할 장소와 다음 장소, 남은 시간이 표시됩니다. 수업, 면학, 식사 시간에 맞춰 저절로 바뀌고, 식사 시간에는 그 끼니의 메뉴를 보여 줍니다.",
    )
    WallpaperPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WidgetMock("면학 1타임", TourBlue, "도서관", Modifier.weight(1f)) {
                Text("→ 2층 B-14", style = MaterialTheme.typography.labelMedium, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text("0:48:12", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
            }
            // 식사 시간 — 다음 장소를 크게, 그 아래 메뉴(알레르기 재료는 빨간 글씨로 앞에).
            WidgetMock("저녁", TourOrange, "도서관", Modifier.weight(1f)) {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFEF4444), fontWeight = FontWeight.Bold))
                        append("⚠새우 ")
                        pop()
                        append("잡곡밥, 된장찌개, 제육볶음, 새우튀김")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 3,
                )
                Spacer(Modifier.weight(1f))
                Text("→ 2층 B-14", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB9C0C8))
            }
        }
    }
}

@Composable
private fun ColumnScope.NowBarPage() {
    PageHead(
        "Now Bar",
        "Now Bar 실시간 일정",
        "화면 위쪽과 잠금화면의 Now Bar에 지금 있어야 할 장소와 남은 시간이 실시간으로 표시됩니다. 설정의 알림 메뉴에서 켤 수 있습니다.",
    )
    // 남은 시간과 진행 막대가 실제처럼 흐릅니다 (1초마다).
    var remaining by remember { mutableIntStateOf(48 * 60 + 12) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            remaining = if (remaining <= 0) 48 * 60 + 12 else remaining - 1
        }
    }
    val total = 120 * 60
    WallpaperPanel {
        Text("10:45", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("9월 24일 목요일", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))
        NowBarMock(
            icon = painterResource(R.drawable.ic_local_library),
            tint = TourBlue,
            title = "도서관 2층 B-14",
            countdown = "%d:%02d".format(remaining / 60, remaining % 60),
            subtitle = "면학 1타임 · 19:00 – 21:00",
            progress = 1f - remaining.toFloat() / total,
        )
    }
}

/**
 * 실제 Now Bar(실시간 알림) 모양 — 어두운 둥근 카드 왼쪽에 종류 색이 번지고, 큰 색 원 아이콘 · 장소 + 남은 시간 ·
 * 구간과 시각 · 지난 만큼 흰색, 남은 만큼 회색인 굵은 진행 막대.
 */
@Composable
private fun NowBarMock(icon: Painter, tint: Color, title: String, countdown: String, subtitle: String, progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xFF191C1B))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to tint.copy(alpha = 0.38f),
                    0.45f to Color.Transparent,
                ),
            )
            .padding(start = 14.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CircleIcon(icon, tint, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text(countdown, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 1.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val done = progress.coerceIn(0.02f, 0.98f)
                    Box(Modifier.weight(done).height(6.dp).clip(CircleShape).background(Color.White))
                    Box(Modifier.weight(1f - done).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ApplyPage() {
    PageHead(
        "신청·내역",
        "앱 안에서 하는 신청·내역",
        "도서관, 면학실, 심야면학, 교과교실, 외출·외박 모두 하이하나 웹페이지 없이 앱에서 신청하고 내역을 확인합니다. " +
            "도서관·면학실·심야면학은 좌석 배치도에서 빈 자리를 눌러 바로 신청하고, 내 자리를 눌러 취소합니다.",
    )
    val scheme = MaterialTheme.colorScheme
    // 신청·내역 탭의 서비스 카드를 한 장에 줄여 — 서비스마다 내역 · 신청 두 버튼 (실제 탭과 같은 순서·같은 버튼).
    TourCaption("신청·내역")
    OneUiCard(Modifier.fillMaxWidth()) {
        listOf(
            Triple(R.drawable.ic_meeting_room, TourGreen, "면학실"),
            Triple(R.drawable.ic_local_library, TourBlue, "도서관"),
            Triple(R.drawable.ic_bedtime, TourViolet, "심야면학"),
            Triple(R.drawable.ic_school, TourOrange, "교과교실"),
            Triple(R.drawable.ic_home_stay, TourPink, "외출·외박"),
        ).forEachIndexed { i, (icon, tint, name) ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(painterResource(icon), tint)
                Spacer(Modifier.width(12.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                listOf("내역", "신청").forEach { label ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 13.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    TourCaption("좌석 배치도 — 면학실 3층 · 1타임")
    OneUiCard(Modifier.fillMaxWidth()) {
        // 남 파랑 · 여 분홍 · 빈 자리 · 내 자리(테두리) — 실제 좌석 화면과 같은 색 규칙.
        val seats = "bxpbxpxbmxpbpxbxxb"
        seats.chunked(6).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                line.forEach { c ->
                    val color = when (c) {
                        'b' -> TourBlue.copy(alpha = 0.55f)
                        'p' -> TourPink.copy(alpha = 0.55f)
                        'm' -> TourBlue
                        else -> scheme.onSurface.copy(alpha = 0.08f)
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(7.dp)).background(color)
                            .then(if (c == 'm') Modifier.padding(2.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.25f)) else Modifier),
                    )
                }
            }
        }
        Text("빈 자리를 누르면 바로 신청, 내 자리를 누르면 취소", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ColumnScope.SeatsPage() {
    PageHead(
        "교과교실 현황",
        "교과교실 현황",
        "타임과 건물·교실을 고르면 그 교실을 신청한 학생과 사유가 바로 보입니다. 친구가 어느 교과교실에 있는지, 빈 교실이 어디인지 포털을 뒤지지 않고 확인할 수 있습니다.",
    )
    // 실제 교과교실 현황 화면과 같은 구성 — 타임·건물·교실 칩, 신청 수, 이름·학년·상태·사유. 이름은 예시입니다.
    val rooms = listOf("A201", "A202", "B301", "B302")
    var room by remember { mutableIntStateOf(0) }
    val sample = mapOf(
        0 to listOf(Triple("김하늘", "2", "수학 문제 풀이 스터디"), Triple("이서연", "2", "물리 수행평가 준비"), Triple("박준호", "3", "과제 조사")),
        1 to listOf(Triple("최유진", "1", "영어 발표 연습")),
        2 to emptyList(),
        3 to listOf(Triple("정민재", "2", "화학 실험 보고서 작성"), Triple("한지우", "2", "화학 실험 보고서 작성")),
    )
    OneUiCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1타임", "A동").forEach { chip ->
                Text(
                    chip,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rooms.forEachIndexed { i, name ->
                val on = i == room
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .clickable { room = i }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val list = sample[room].orEmpty()
        Text(
            if (list.isEmpty()) "이 교실을 신청한 학생이 없습니다" else "신청 ${list.size}명",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        list.forEach { (name, grade, reason) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text("${grade}학년", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("승인", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(
        "교실을 눌러 보세요 (이름은 예시입니다)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
    )
}

@Composable
private fun ColumnScope.MealPage() {
    PageHead(
        "급식",
        "주간 급식과 영양 정보",
        "이번 주 급식을 끼니별로 보고, 급식 사진과 칼로리, 영양성분, 원산지까지 확인할 수 있습니다. 알레르기 재료를 설정하면 해당 메뉴를 빨간색으로 표시합니다.",
    )
    val context = LocalContext.current
    // 사진·메뉴·칼로리·영양은 가장 가까운 지난 급식에서 가져와 핵심만 간략히 — 없으면 예시 값.
    var sample by remember { mutableStateOf<TourMealSample?>(null) }
    LaunchedEffect(Unit) { sample = findTourMealSample(context) }
    val found = sample
    val scheme = MaterialTheme.colorScheme

    // 주간 — 요일 칩
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        val selectedDay = (found?.date?.dayOfWeek?.value ?: 3).coerceIn(1, 5)
        listOf("월", "화", "수", "목", "금").forEachIndexed { index, day ->
            val on = index + 1 == selectedDay
            Text(
                day,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (on) scheme.surface else scheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) scheme.onSurface else scheme.onSurface.copy(alpha = 0.08f))
                    .padding(vertical = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        Row {
            val thumb = HanaMealClient.mealPhotoThumbUrl(found?.photoFile)
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(16.dp)).background(scheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) RemoteThumbnail(listOf(thumb), Modifier.fillMaxSize())
                else Icon(painterResource(R.drawable.ic_meal), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(found?.meal?.label ?: "점심", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    val kcal = found?.info?.kcal?.toDoubleOrNull()?.let { "%,.0f kcal".format(it) } ?: "812 kcal"
                    Text(
                        kcal,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TourOrange,
                        modifier = Modifier.clip(CircleShape).background(TourOrange.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 메뉴 세 줄 — 알레르기 재료가 든 첫 메뉴는 빨간 글씨와 재료 이름으로.
                val items = found?.items?.take(3)
                    ?: listOf(MealItem("잡곡밥", emptySet()), MealItem("새우튀김", setOf(9)), MealItem("배추김치", emptySet()))
                val flaggedIndex = items.indexOfFirst { detectedAllergies(it.codes, it.name, ALLERGY_LEGEND.keys).isNotEmpty() }
                items.forEachIndexed { index, item ->
                    val flagged = index == flaggedIndex
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (flagged) FontWeight.Bold else FontWeight.Normal,
                            color = if (flagged) scheme.error else scheme.onSurface,
                            maxLines = 1,
                        )
                        if (flagged) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "⚠ " + shortAllergyLabels(detectedAllergies(item.codes, item.name, ALLERGY_LEGEND.keys)).take(2).joinToString("·"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.error,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 영양성분 세 칸
        val nutrients = found?.info?.nutrients?.take(3)?.takeIf { it.size == 3 }
            ?: listOf("탄수화물(g)" to "112.4", "단백질(g)" to "38.1", "지방(g)" to "21.7")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nutrients.forEach { (name, value) ->
                val unit = name.substringAfter("(", "").removeSuffix(")")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(scheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(name.substringBefore("("), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    Text(
                        "${value.toDoubleOrNull()?.let { "%.0f".format(it) } ?: value}$unit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 원산지 한 줄
        val origins = found?.info?.origins?.take(2)?.takeIf { it.isNotEmpty() } ?: listOf("쌀" to "국내산", "돼지고기" to "국내산")
        Text(
            "원산지 · " + origins.joinToString(", ") { (item, origin) -> "$item $origin" },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private class TourMealSample(
    val date: java.time.LocalDate,
    val meal: Meal,
    val items: List<MealItem>,
    val photoFile: String?,
    val info: NeisMealInfo?,
)

/** 오늘부터 거꾸로 열흘 동안 사진이 있는 점심(없으면 다른 끼니)을 찾습니다 — 캐시를 먼저 보고, 없으면 받아 봅니다. */
private suspend fun findTourMealSample(context: android.content.Context): TourMealSample? {
    val today = PlanStore.today()
    var fetched = 0
    for (back in 0L..10L) {
        val date = today.minusDays(back)
        if (date.dayOfWeek.value >= 6) continue
        var day = MealCache.load(context, date)
        if (day == null && fetched < 5) {
            fetched++
            runCatching { HanaMealSync.refresh(context, date) }
            day = MealCache.load(context, date)
        }
        day ?: continue
        val order = listOf(Meal.LUNCH) + Meal.entries.filter { it != Meal.LUNCH && it != Meal.SNACK }
        val meal = order.firstOrNull { !day.photos[it.key].isNullOrBlank() && day.items[it.key].orEmpty().isNotEmpty() } ?: continue
        val info = runCatching { NeisMeal.load(context, date)[meal.key] }.getOrNull()
        return TourMealSample(date, meal, day.items[meal.key].orEmpty(), day.photos[meal.key], info)
    }
    return null
}

@Composable
private fun ColumnScope.BoardPage() {
    PageHead(
        "게시판",
        "새 글 알림 · AI 요약 · 검색",
        "고른 게시판에 새 글이 올라오면 알림으로 알려 드립니다. 글마다 구글의 최신 LLM인 Gemma 4가 핵심을 요약하고, 이미지뿐인 가정통신문도 읽어 요약합니다. " +
            "게시판 오른쪽 위 돋보기로 하이하나의 모든 글을 기간 제한 없이 검색할 수 있습니다.",
    )
    OneUiCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF1F5A3C)))
            Spacer(Modifier.width(7.dp))
            Text("HiHana Evo · 학생공지 · 방금", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text("2학기 교내 수학경시대회 참가 신청 안내", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text("10/7(수)까지 포털 신청, 10/14 시청각실", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(10.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("2학기 교내 수학경시대회 참가 신청 안내", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        AiSummaryPill("10/7(수)까지 포털 신청, 10/14 시청각실", Modifier.padding(top = 9.dp))
    }
}

@Composable
private fun ColumnScope.PersonalizePage(appearance: TourAppearance?) {
    PageHead(
        "화면",
        "테마·강조색·배경화면",
        "라이트·다크 테마와 강조색을 고르고, 원하는 사진을 배경화면으로 쓸 수 있습니다. 지금 골라 보세요. 나중에 설정에서도 바꿀 수 있습니다.",
    )
    if (appearance == null) return
    // 설정 → 화면 과 같은 부품·같은 저장 — 고르는 즉시 이 투어 화면부터 색이 바뀝니다(배경 사진은 투어를 마친 뒤 앱에).
    OneUiCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        AccentPickerRow(selectedArgb = appearance.accentArgb, onSelect = appearance.onAccentChange)
        com.skh.hihanaevo.ui.OneUiDivider()
        PlanStore.themes.forEach { option ->
            com.skh.hihanaevo.ui.OneUiRadioRow(
                selected = appearance.appTheme == option,
                label = PlanStore.themeLabel(option),
                onClick = { appearance.onAppThemeChange(option) },
            )
        }
        com.skh.hihanaevo.ui.OneUiDivider()
        BackgroundPhotoRows(appearance.hasBackgroundPhoto, appearance.onBackgroundChanged)
    }
}

@Composable
private fun ColumnScope.GlassPage() {
    PageHead(
        "디자인",
        "리퀴드 글래스",
        "스크롤하면 큰 제목과 메뉴가 유리 섬으로 접히고, 하단 바와 함께 뒤 화면을 흐리고 굴절시킵니다. 아래 화면을 직접 스크롤하거나 하단 바의 선택 표시를 끌어 보세요.",
    )
    GlassDemo()
}

/**
 * 리퀴드 글래스 시연 — 실제 헤더([OneUiCollapsingHeader])·메뉴 알약([OneUiActionPill])·하단 바([AppNavBar])를 작은 화면 안에
 * 그대로 띄웁니다. 처음엔 저절로 내려갔다 올라오며 제목이 섬으로 접히는 모습을 보여 주고, 손을 대면 멈춰 직접 스크롤하게 둡니다.
 */
@Composable
private fun GlassDemo() {
    val glassState = remember { HazeState() }
    val headerState = com.skh.hihanaevo.ui.rememberOneUiHeaderState()
    val scroll = rememberScrollState()
    var selected by remember { mutableIntStateOf(0) }
    var autoplay by remember { mutableStateOf(true) }
    LaunchedEffect(autoplay) {
        if (!autoplay) return@LaunchedEffect
        // 실제 스크롤처럼 한 줄의 '스크롤 거리'로 헤더와 내용을 함께 움직입니다 — 내려갈 땐 헤더가 먼저 접히고 이어서 내용이,
        // 올라올 땐 내용이 먼저 맨 위로 온 뒤 헤더가 펼쳐집니다 (OneUiHeaderState.connection 과 같은 순서).
        // 프로그램 스크롤은 nestedScroll 을 거치지 않아 이렇게 직접 나눠 줍니다.
        var applied = 0f
        fun apply(distance: Float) {
            headerState.restore(-distance.coerceAtMost(headerState.rangePx))
            val content = (distance - headerState.rangePx).coerceAtLeast(0f)
            scroll.dispatchRawDelta(content - applied)
            applied = scroll.value.toFloat()
        }
        while (true) {
            delay(1_000)
            val far = headerState.rangePx + scroll.maxValue.coerceAtMost(700).toFloat()
            androidx.compose.animation.core.animate(0f, far, animationSpec = tween(2_400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { v, _ -> apply(v) }
            delay(1_100)
            androidx.compose.animation.core.animate(far, 0f, animationSpec = tween(2_000, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { v, _ -> apply(v) }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(460.dp)
            .clip(RoundedCornerShape(32.dp))
            .consumeWindowInsets(WindowInsets.statusBars)
            .consumeWindowInsets(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                // 손을 대는 순간 자동 시연을 멈춥니다.
                awaitPointerEventScope {
                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    autoplay = false
                }
            },
    ) {
        CompositionLocalProvider(LocalHazeState provides glassState) {
            // 뒤 화면 — 과목 색 칸 시간표와 카드. 색과 글자가 유리 아래로 지나가며 흐림·굴절이 보입니다.
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(headerState.connection)
                    .hazeSource(glassState)
                    .oneUiPageBackground(),
            ) {
                // 실제 앱처럼 헤더가 줄어든 만큼 내용도 함께 올라갑니다 (접히는 동안 내용이 제자리에 멈춰 있지 않게).
                Column(
                    Modifier
                        .fillMaxSize()
                        .offset { androidx.compose.ui.unit.IntOffset(0, headerState.offsetPx.toInt()) }
                        .verticalScroll(scroll)
                        .padding(horizontal = 14.dp)
                        .padding(top = com.skh.hihanaevo.ui.OneUiCompactBarHeight + com.skh.hihanaevo.ui.OneUiHeaderExpandedExtra + 8.dp, bottom = 96.dp),
                ) {
                    GlassBackdrop()
                }
            }
            // 버튼 섬의 폭을 재서 접힌 제목 섬이 거기까지만 늘어나게 — 안 그러면 두 유리가 겹쳤습니다.
            var pillWidthPx by remember { mutableIntStateOf(0) }
            val pillReserve = with(androidx.compose.ui.platform.LocalDensity.current) { pillWidthPx.toDp() } + 10.dp + 16.dp
            com.skh.hihanaevo.ui.OneUiCollapsingHeader(state = headerState, title = "홈", subtitle = "9월 24일 목요일", endReserve = pillReserve)
            // 실제 앱(홈 탭)처럼 오른쪽 섬 하나 — 새로고침 · 카드 순서 · 알리미 · 설정.
            com.skh.hihanaevo.ui.OneUiActionPill(
                pillAlpha = { ((headerState.fraction - 0.3f) / 0.7f).coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 10.dp).onSizeChanged { pillWidthPx = it.width },
            ) {
                @Composable fun slot(content: @Composable () -> Unit) = Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { content() }
                slot { Icon(com.skh.hihanaevo.ui.AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(22.dp)) }
                com.skh.hihanaevo.ui.OneUiActionDivider()
                slot { Icon(com.skh.hihanaevo.ui.AppIcons.Edit, contentDescription = null, modifier = Modifier.size(22.dp)) }
                com.skh.hihanaevo.ui.OneUiActionDivider()
                slot { Icon(com.skh.hihanaevo.ui.AppIcons.inbox(), contentDescription = null, modifier = Modifier.size(22.dp)) }
                slot { Icon(com.skh.hihanaevo.ui.AppIcons.Settings, contentDescription = null, modifier = Modifier.size(22.dp)) }
            }
            // 실제 앱처럼 세 칸 [홈 · 타임라인 · ⊞] — ⊞ 는 여는 단추라 캡슐이 가지 않습니다.
            AppNavBar(
                selected = selected,
                onSelect = { if (it != 2) selected = it },
                modifier = Modifier.align(Alignment.BottomCenter),
                lensAtRest = true,
            )
        }
    }
}

/**
 * 시연 화면의 내용 — 실제 앱 카드들: '지금', 종류별 색 아이콘이 붙은 남은 일정, 그라데이션 AI 요약, 강조색 견본, 좌석 배치도.
 * 여러 색과 글자가 유리 아래로 지나가며 흐림·굴절이 잘 보입니다.
 */
@Composable
private fun GlassBackdrop() {
    val scheme = MaterialTheme.colorScheme
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("지금", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("면학 1타임 · 도서관", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("2층 B-14", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = scheme.primary)
    }
    Spacer(Modifier.height(10.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("오늘 남은 일정", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        listOf(
            Triple(R.drawable.ic_free_breakfast, TourYellow, "21:00  간식시간"),
            Triple(R.drawable.ic_meeting_room, TourGreen, "21:30  면학 2타임 · 면학실 3층 A-07"),
            Triple(R.drawable.ic_bedtime, TourViolet, "23:10  심야 1타임 · 3층 12번"),
            Triple(R.drawable.ic_science, TourPink, "내일 1교시  물리 · 과학실2"),
        ).forEachIndexed { i, (icon, tint, text) ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(painterResource(icon), tint, size = 30.dp)
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    }
    // 그라데이션 테두리·빛이 번지는 실제 AI 요약 카드.
    SummaryCard(
        state = PostSummarizer.State.Done(
            PostSummary(
                line = "10/7(수)까지 포털 신청, 10/14 시청각실",
                points = listOf("2학년 희망자 대상, 포털 신청·내역에서 신청함", "대회는 10/14(수) 7교시에 진행함"),
            ),
        ),
        onRetry = {},
        horizontalPadding = 0.dp,
    )
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("강조 색", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            listOf(TourBlue, TourViolet, Color(0xFFB45CF0), Color(0xFFFF3B5C), TourOrange, Color(0xFFFFB020), TourGreen, Color(0xFF2FB8A6)).forEach { c ->
                Box(Modifier.size(26.dp).clip(CircleShape).background(c))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_local_library), TourBlue, "도서관 2층 · 1타임", "빈 자리 12석")
        Spacer(Modifier.height(12.dp))
        "bxpbmpxbbxpbpxbxpb".chunked(6).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                line.forEach { c ->
                    Box(
                        Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(6.dp)).background(
                            when (c) {
                                'b' -> TourBlue.copy(alpha = 0.6f)
                                'p' -> TourPink.copy(alpha = 0.6f)
                                'm' -> TourGreen
                                else -> scheme.onSurface.copy(alpha = 0.08f)
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PrivacyPage(beforeLogin: Boolean, onMore: () -> Unit) {
    PageHead(
        "개인정보 보호",
        "계정 정보 보호",
        (if (beforeLogin) "로그인하기 전에 계정 정보를 어떻게 다루는지 알려 드립니다. " else "") +
            "하이하나 비밀번호는 이 폰에 암호화해 저장하고 학교 학사시스템에만 보냅니다. 개발자를 포함해 다른 누구에게도 전달되지 않습니다.",
    )
    // 개인정보 보호 화면과 같은 네 가지 보안 기술 — 투어에서는 원색 타일로.
    SecuritySpecTiles()
    Text(
        "자세히 보기",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 12.dp)
            .clip(CircleShape)
            .clickable(onClick = onMore)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ColumnScope.PermissionsPage() {
    val context = LocalContext.current
    // 설정 앱에 다녀오면 상태를 다시 읽습니다.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    var notifications by remember { mutableStateOf(notificationsGranted(context)) }
    var exactAlarm by remember { mutableStateOf(ExactAlarmPermission.isGranted(context)) }
    var nowBar by remember { mutableStateOf(LiveActivity.isEnabled(context)) }
    LaunchedEffect(refresh) {
        notifications = notificationsGranted(context)
        exactAlarm = ExactAlarmPermission.isGranted(context)
        nowBar = LiveActivity.isEnabled(context)
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications = granted
        // 두 번 거절하면 시스템이 더 묻지 않으므로 알림 설정 화면을 엽니다.
        if (!granted) LiveActivity.openNotificationSettings(context)
    }

    PageHead("권한", "권한 설정", "알림과 위젯이 제때 뜨려면 아래 권한이 필요합니다. 나중에 설정에서도 바꿀 수 있습니다.")
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(rememberVectorPainter(Icons.Default.Notifications), TourOrange, "알림", "게시판 새 글, 알리미, Now Bar") {
            GrantChip(notifications, "허용") {
                if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else LiveActivity.openNotificationSettings(context)
            }
        }
        RowGap()
        TourRow(painterResource(R.drawable.ic_tour_alarm), TourViolet, "알람 및 리마인더", "위젯·Now Bar의 남은 시간을 절전 중에도 정확하게") {
            GrantChip(exactAlarm, "허용") { ExactAlarmPermission.openSettings(context) }
        }
    }
    Spacer(Modifier.height(10.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_tour_now_bar), TourBlue, "Now Bar에 일정 표시", "잠금화면과 화면 위쪽에 지금 일정과 남은 시간") {
            GrantChip(nowBar, "켜기") {
                LiveActivity.setEnabled(context, true)
                nowBar = true
            }
        }
    }
}

@Composable
private fun ColumnScope.LoginPage(onReady: (LoginAction?) -> Unit, onLinked: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // dev 빌드는 새로 설치한 상태를 보여 주려고 연동돼 있어도 로그인 입력 화면을 그립니다 (저장된 계정은 건드리지 않음).
    val connectedId = remember {
        if (BuildConfig.DEV) null
        else HanaCredentialStore.memId(context)?.takeIf { HanaCredentialStore.hasCredentials(context) }
    }
    var memId by remember { mutableStateOf("") }
    var memPwd by remember { mutableStateOf("") }
    var grade by remember { mutableIntStateOf(PlanStore.DEFAULT_STUDENT_GRADE) }
    var checking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { grade = PlanStore.studentGrade(context) }

    fun login() {
        val id = memId.trim()
        val pwd = memPwd.trim()
        HanaCredentialStore.setMemId(context, id)
        HanaCredentialStore.setMemPwd(context, pwd)
        checking = true
        failure = null
        scope.launch {
            PlanStore.setStudentGrade(context, grade)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val portal = HanaPortalClient.get()
                    portal.relogin(context)
                    portal.fetchDailySync(context, PlanStore.today())
                }
            }
            checking = false
            result.fold(
                onSuccess = {
                    HanaSyncGate.clear(context)
                    onLinked()
                    onLoggedIn()
                },
                onFailure = { e ->
                    // 틀린 계정이 남아 있으면 앱 곳곳에서 로그인 오류가 나니 지웁니다 — 다시 입력하거나 나중에 연동.
                    HanaCredentialStore.clear(context)
                    failure = loginFailureMessage(e)
                },
            )
        }
    }

    val ready = memId.isNotBlank() && memPwd.isNotBlank()
    SideEffect {
        onReady(
            if (connectedId != null) null
            else LoginAction(label = "로그인", enabled = ready, busy = checking, run = ::login),
        )
    }

    PageHead("하이하나 계정", "학사시스템에\n로그인하세요", "시간표·면학 위치·학사일정·게시판을 자동으로 가져옵니다.")
    if (connectedId != null) {
        OneUiCard(Modifier.fillMaxWidth()) {
            TourRow(rememberVectorPainter(Icons.Default.Check), TourGreen, "이미 연결되어 있습니다", connectedId)
        }
        return
    }
    OneUiTextField(
        value = memId,
        onValueChange = { memId = it },
        label = "아이디",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OneUiTextField(
        value = memPwd,
        onValueChange = { memPwd = it },
        label = "비밀번호",
        visualTransformation = PasswordVisualTransformation(),
        // 키보드 추천 줄에 비밀번호가 보이지 않도록 비밀번호 칸임을 알립니다.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
    ) {
        (1..3).forEach { g ->
            val on = g == grade
            Text(
                "${g}학년",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { grade = g }
                    .padding(vertical = 10.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.padding(horizontal = 4.dp)) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "비밀번호는 이 기기에 암호화되어 저장되고, 학사시스템(hh.hana.hs.kr)에만 보냅니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    failure?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.animateContentSize())
    }
    if (BuildConfig.DEV) {
        // 로그인 시뮬레이션 — 가짜 아이디·비밀번호를 한 글자씩 채우고 확인 중을 잠깐 보인 뒤 다음 장으로. 저장·네트워크 없음.
        Text(
            "로그인 시뮬레이션 (dev)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
                .clip(CircleShape)
                .clickable(enabled = !checking) {
                    scope.launch {
                        failure = null
                        memId = ""
                        memPwd = ""
                        "hana2026".forEach { memId += it; delay(60) }
                        "simulated".forEach { memPwd += it; delay(45) }
                        delay(250)
                        checking = true
                        delay(900)
                        checking = false
                        onLoggedIn()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * 클래스룸 연결 — 설정 → 클래스룸과 같은 방식(브라우저에서 학교 Google 계정으로 '허용' 한 번). 돌아오면 메인 화면이 첫 내용을
 * 받아 연결을 확인하고, 이 장은 그 상태([ClassroomBridge.connected]·[ClassroomBridge.pending])를 그대로 보여 줍니다.
 * 건너뛰려면 '다음'(설정에서 나중에 연결).
 */
@Composable
private fun ColumnScope.ClassroomLinkPage() {
    val context = LocalContext.current
    PageHead(
        "클래스룸",
        "클래스룸 연결",
        "학교 Google 계정으로 한 번 허용하면 수업의 공지·과제·자료와 과제 마감을 앱에서 봅니다. 읽기만 하고, 내용은 암호화되어 이 기기에만 저장됩니다. 나중에 설정 → 클래스룸에서도 연결할 수 있습니다.",
    )
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ClassroomBridge.isConnected(context) } }
    val connected = ClassroomBridge.connected.value == true
    val pending = ClassroomBridge.pending.value
    OneUiCard(Modifier.fillMaxWidth()) {
        when {
            connected -> TourRow(
                rememberVectorPainter(Icons.Default.Check), TourGreen, "연결되어 있습니다",
                ClassroomBridge.email(context) ?: "학교 Google 계정",
            )
            pending -> TourRow(
                painterResource(R.drawable.ic_school), TourGreen, "연결 확인 중",
                "브라우저에서 '허용'까지 마쳤다면 잠시 뒤 여기에 '연결되어 있습니다'가 뜹니다. 아직이면 아래 버튼으로 다시 열 수 있습니다.",
            )
            else -> TourRow(
                painterResource(R.drawable.ic_school), TourGreen, "학교 계정으로 연결",
                "아래 버튼을 누르면 브라우저가 열립니다. 학교 계정(@hana.hs.kr)을 고르고 '허용'을 누른 뒤 '앱으로 돌아가기'를 누르세요.",
            )
        }
    }
    if (!connected) {
        Spacer(Modifier.height(16.dp))
        com.skh.hihanaevo.ui.OneUiButton(
            text = if (pending) "브라우저 다시 열기" else "클래스룸 연결하기",
            onClick = { if (!ClassroomBridge.beginSignIn(context)) android.widget.Toast.makeText(context, "브라우저를 열 수 없습니다", android.widget.Toast.LENGTH_SHORT).show() },
            modifier = Modifier.fillMaxWidth(),
            // 처음 설치 투어는 아래 흰 주 버튼과 겹치지 않게 남색 위의 옅은 알약으로.
            style = if (LocalEvoTour.current) com.skh.hihanaevo.ui.OneUiButtonStyle.Neutral else com.skh.hihanaevo.ui.OneUiButtonStyle.Filled,
        )
    }
}

@Composable
private fun ColumnScope.DonePage() {
    Spacer(Modifier.height(48.dp))
    Box(Modifier.size(68.dp).clip(CircleShape).background(TourGreen.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF8FDC6B), modifier = Modifier.size(34.dp))
    }
    PageHead(
        "시작",
        "준비 완료",
        "홈 화면에 위젯을 추가하면 앱을 열지 않아도 지금 일정을 볼 수 있습니다. 홈 화면 빈 곳을 길게 누르고 위젯에서 HiHana Evo를 고르세요.",
    )
}

// MARK: - 14.0 업데이트 투어 — 13.x 를 쓰던 사람에게 이번 버전에서 바뀐 것만. 그림은 실제 화면을 그대로 줄여 그립니다.
// 투어 문구 규칙: 제목은 명사형(문장 X), 설명은 문장으로.

/**
 * 투어 첫 장의 내용 — 벤토([EvoFeatureShowcase]). 가운데 타일은 비워 두고(오프닝 끝 장면이 줄어들어 들어옴, EvoFinalFrame 이
 * 화면 전체 좌표로 그림), 둘레 타일은 끝 장면이 줄어드는 동안([lift] 0 → 1) 하나씩 떠오릅니다. 업데이트 투어는 14.0 에서 바뀐
 * 것, 처음 투어([everything])는 앱 전체를 보여 줍니다 — 다음 큰 업데이트 때 내용과 함께 바꿉니다.
 */
@Composable
private fun ColumnScope.UpdateEvoPage(lift: Float, everything: Boolean = false, onCenter: (androidx.compose.ui.geometry.Rect) -> Unit) {
    // 업데이트 투어 첫 장의 머리말 — 끝 장면이 가운데 칸으로 줄어드는 동안 함께 떠오릅니다.
    if (!everything) Text(
        "HiHana, 슈퍼앱으로 도약하다",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp)
            .graphicsLayer {
                alpha = lift
                translationY = (1f - lift) * 24.dp.toPx()
            },
    )
    else Spacer(Modifier.height(8.dp))
    EvoFeatureShowcase(lift, onCenter, everything = everything)
}

/** 그림 위 작은 설명 글씨. */
@Composable
private fun TourCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

/** 일정 탭 목록의 한 줄 — 실제 일정 탭처럼 제목(마감이면 앞에 '마감'), 아래 회색 설명, 오른쪽에 출처 색 글자. */
@Composable
private fun TourAgendaRow(title: String, meta: String?, source: String, sourceColor: Color, deadline: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (deadline) {
                    OneUiBadge("마감", container = scheme.error.copy(alpha = 0.14f), content = scheme.error)
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
            if (meta != null) Text(meta, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(source, style = MaterialTheme.typography.labelSmall, color = sourceColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.UpdateAgendaPage() {
    PageHead(
        "일정",
        "한곳에 모은 학교 일정",
        "학사일정은 하이하나 일정에, 수행평가와 신청 마감은 게시판 글 속에, 과제 기한은 클래스룸에 따로 있었습니다. " +
            "이제 앱이 세 곳을 모두 읽어 학사일정에 날짜순 한 목록으로 보여 줍니다. 게시판 글과 클래스룸 공지에 적힌 날짜는 AI가 찾아 일정으로 올리고, " +
            "과제 마감은 제출하기 전까지 함께 보입니다.",
        isNew = true,
    )
    TourCaption("흩어져 있던 세 곳")
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.DateRange), AgendaSource.ACADEMIC.color, "하이하나 학사일정", "개교기념일, 기말고사, 면학감독")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.AutoMirrored.Filled.List), AgendaSource.BOARD.color, "하이하나 게시판", "‘10/24(토)까지 신청’, ‘16기 대상 · 면학 1타임’처럼 글 속에 적힌 날짜와 대상 학년")
        RowGap()
        TourRow(painterResource(R.drawable.ic_school), AgendaSource.CLASSROOM.color, "클래스룸", "과제 마감과 공지에 적힌 수행평가 날짜")
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
    }
    TourCaption("학사일정 — 날짜순 한 목록")
    OneUiCard(Modifier.fillMaxWidth()) {
        @Composable
        fun Day(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Day("10월 23일 (금)")
        TourAgendaRow("미적분 수행평가", "2026-2 미적분2 공지", AgendaSource.CLASSROOM.label, AgendaSource.CLASSROOM.color)
        Day("10월 24일 (토)")
        TourAgendaRow("수학경시대회 신청 마감", "학생공지 · 2학기 교내 수학경시대회 참가 신청 안내", AgendaSource.BOARD.label, AgendaSource.BOARD.color, deadline = true)
        Day("10월 26일 (월)")
        TourAgendaRow("Worksheet ch. 6 제출", "23:59 · 심화영어독해1", AgendaSource.CLASSROOM.label, AgendaSource.CLASSROOM.color, deadline = true)
        Day("10월 30일 (금)")
        TourAgendaRow("개교기념일", null, "공휴일", scheduleColor("SG01020000"))
    }
}

@Composable
private fun ColumnScope.UpdateHomePage() {
    PageHead(
        "홈",
        "지금 챙길 것부터 보이는 홈",
        "홈의 칸이 시간과 새 글에 따라 자리를 바꿉니다. 왼쪽 줄은 급식과 게시판, 오른쪽 줄은 클래스룸과 오늘 일정이고, " +
            "지금 챙길 칸이 제 줄의 위로 올라옵니다. 맨 아래에는 날씨와 시험까지 남은 날이 있습니다.",
        isNew = true,
    )
    BentoPreview(Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_place), TourBlue, "지금과 다음", "맨 위 카드는 지금 하는 것과 있어야 할 곳, 그리고 다음 일정을 보여 줍니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_meal), TourOrange, "급식 시간엔 급식이 위로", "급식 시간이 다가오면 급식이 위로 올라옵니다. 메뉴 중 특식 한두 개를 AI가 골라 크게 보여 주고, 급식 사진이 있으면 사진을 띄웁니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_school), TourGreen, "새 클래스룸 글", "새로 올라온 안 읽은 글이 있으면 클래스룸이 위로 올라옵니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.DateRange), TourBlue, "아침엔 오늘 일정", "아침에는 오늘 일정이 위로 올라오고, 마감이 있으면 먼저, 면학감독은 맨 아래에 보여 줍니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.AutoMirrored.Filled.List), TourYellow, "게시판 최신 글 셋", "클래스룸처럼 최신 글 세 개를 보여 주고, 안 읽은 글은 굵게 표시합니다.")
    }
}

@Composable
private fun ColumnScope.UpdateBriefPage() {
    PageHead(
        "AI",
        "데일리 브리핑",
        "오늘 일정과 이번 주에 챙길 수행평가·시험·마감을 AI가 두세 문장으로 정리해 홈에 띄웁니다. 아침에 홈을 열면 하루가 한눈에 들어옵니다.",
        isNew = true,
    )
    SampleDailyBrief()
    Spacer(Modifier.height(16.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_pv_star), TourViolet, "챙길 것만 골라서", "시간표·학사일정·게시글 속 일정·클래스룸 마감을 한데 모아, 꼭 챙길 일정 두세 개를 날짜와 이유와 함께 골라 줍니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_tour_alarm), TourOrange, "아침엔 맨 위에", "아침에 아직 보지 않았으면 홈 맨 위에 펼쳐 두고, 본 뒤에는 접어 둡니다. 오후에는 홈 아래쪽으로 내려갑니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.Refresh), TourBlue, "일정이 바뀌면 저절로", "새 일정이 생기거나 바뀌면 브리핑도 저절로 다시 만듭니다. AI가 만든 요약이라 틀릴 수 있으니 중요한 일정은 원문을 확인하세요.")
    }
}

@Composable
private fun ColumnScope.UpdateTimelinePage() {
    PageHead(
        "타임라인",
        "하루를 한 줄로 보는 타임라인",
        "하단 바의 가운데 칸을 누르거나 홈에서 왼쪽으로 밀면 나옵니다. 수업·면학·급식을 시각순으로 놓고, " +
            "그 시간에 올라온 클래스룸·게시판 글은 옆에 말풍선으로 붙입니다.",
        isNew = true,
    )
    TimelineDayPreview(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
    )
    Spacer(Modifier.height(16.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(rememberVectorPainter(com.skh.hihanaevo.ui.MaterialExtraIcons.GridOn), TourBlue, "블록을 누르면 그 화면으로", "수업은 시간표, 면학은 신청·내역, 급식은 급식 화면이 열립니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_school), TourGreen, "그 시간의 소식", "그 시각에 올라온 클래스룸·게시판 글과 일정은 오른쪽 말풍선으로 붙고, 누르면 바로 열립니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.DateRange), TourViolet, "시간 · 하루 · 주", "위의 칩이나 두 손가락 오므리기·벌리기로 배율을 바꿉니다. 하루·주에서는 앞으로의 일정을 한눈에 봅니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.Refresh), TourSlate, "지금으로", "오른쪽 위 '지금으로'를 누르면 지금 시각으로 돌아옵니다.")
    }
}

@Composable
private fun ColumnScope.UpdateGoogleClassroomPage() {
    PageHead(
        "클래스룸",
        "클래스룸",
        "⊞ 전체에 클래스룸이 생겼습니다. 학교 Google 계정으로 한 번 허용하면 수업의 공지·과제·자료와 내 제출 상태를 받아 오고, " +
            "이후에는 저절로 새로 받습니다. 위의 수업 이름을 눌러 한 수업만 보고, 오른쪽 위 연필로 보일 수업을 고르고, 돋보기로 글을 찾습니다.",
        isNew = true,
    )
    val scheme = MaterialTheme.colorScheme
    val notice = Color(0xFF3A7BD5)
    val work = Color(0xFF1E8E3E)
    Row(
        Modifier.clip(RoundedCornerShape(OneUi.CornerMedium)).background(scheme.surface.copy(alpha = 0.7f)).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("전체 수업", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
    TourCaption("할 일")
    OneUiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Worksheet ch. 6 제출", style = MaterialTheme.typography.bodyLarge)
                Text("심화영어독해1 2분반 · 마감 10/26 (월) 23:59", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            OneUiBadge("D-4", container = scheme.primary, content = scheme.onPrimary)
        }
    }
    Spacer(Modifier.height(12.dp))
    TourCaption("스트림")
    OneUiCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        @Composable
        fun Post(kind: String, color: Color, meta: String, title: String, body: String) {
            Column(Modifier.fillMaxWidth().padding(horizontal = OneUi.RowPadding, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kind, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
                    Spacer(Modifier.width(8.dp))
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Post("공지", notice, "2026-2 미적분2 (7분반) · 10/21 (수) 10:36", "수행평가 안내", "시행 날짜 10월 23일 금요일, 조원 안내")
        com.skh.hihanaevo.ui.OneUiDivider()
        Post("과제", work, "심화영어독해1 2분반 · 10/19 (월) 14:05", "Worksheet ch. 6 제출", "Worksheet의 A & B를 수업 전에 풀어 오세요")
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        listOf("‹", "1", "2", "3", "›").forEachIndexed { i, label ->
            val selected = i == 1
            Box(
                Modifier.padding(horizontal = 3.dp).size(34.dp).clip(CircleShape).background(if (selected) scheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        selected -> scheme.onPrimary
                        i == 0 -> scheme.onSurface.copy(alpha = 0.3f)
                        else -> scheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.UpdateAlertsPage() {
    PageHead(
        "게시판",
        "더 빨라진 새 글 알림과 AI 요약",
        "새 글 알림이 더 빨리 옵니다. 게시판을 열면 글마다 AI 요약이 바로 보이고, 다른 게시판으로 옮겨도 요약을 기다리지 않습니다.",
        isNew = true,
    )
    TourCaption("새 글 알림")
    OneUiCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF1F5A3C)))
            Spacer(Modifier.width(7.dp))
            Text("HiHana Evo · 교과공지 · 방금", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text("[1,2학년] 2026 교내 철학 경시대회 안내", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text("10/22(목)까지 신청서 제출, 10/29 본선", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(14.dp))
    TourCaption("게시판 목록")
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("[1,2학년] 2026 교내 철학 경시대회 안내", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        AiSummaryPill("10/22(목)까지 신청서 제출, 10/29 본선", Modifier.padding(top = 9.dp))
        Spacer(Modifier.height(16.dp))
        Text("[상담실] 마음표현의 날(애플데이) 행사 안내", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        AiSummaryPill("10/23 점심시간, 사과에 마음을 적어 전달", Modifier.padding(top = 9.dp))
    }
}

@Composable
private fun ColumnScope.UpdateBackgroundPage(appearance: TourAppearance?) {
    PageHead(
        "화면",
        "배경 고르기",
        "기본 배경이 HiHana Evo 로고 색으로 바뀌었고, 배경 사진은 이제 홈뿐 아니라 타임라인과 다른 화면에도 깔립니다. 지금 쓰는 사진을 그대로 둘지, 새 기본 배경으로 바꿀지 고르세요. 설정 → 화면에서 언제든 다시 바꿀 수 있습니다.",
        isNew = true,
    )
    if (appearance == null) return
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // 미리보기용 사진 — 기본 배경을 골라 사진이 내려가도 왼쪽 그림은 남아 있게 처음 본 사진을 들고 있습니다.
    val current = com.skh.hihanaevo.ui.LocalPageBackground.current
    var photo by remember { mutableStateOf(current) }
    LaunchedEffect(current) { if (current != null) photo = current }
    val filter = com.skh.hihanaevo.ui.PageBackgroundAdjust.colorFilter()
    val keep = appearance.hasBackgroundPhoto
    val dark = scheme.isDark

    @Composable
    fun RowScope.Choice(label: String, selected: Boolean, image: androidx.compose.ui.graphics.ImageBitmap?, onPick: () -> Unit) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.weight(1f).clip(shape).clickable(onClick = onPick),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 홈 화면을 줄인 그림 — 배경 위에 큰 제목과 서리 카드 넷.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.62f)
                    .clip(shape)
                    .drawBehind { drawPageBackground(size.width, size.height, dark, image, null, if (image != null) filter else null) }
                    .border(if (selected) 2.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("홈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (image != null) Color.White else scheme.onSurface)
                    val card = scheme.surface.copy(alpha = if (dark) 0.55f else 0.62f)
                    Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(card))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(card))
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(card))
                    }
                    Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(10.dp)).background(card))
                }
            }
            Row(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) scheme.primary else scheme.onSurface)
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Choice("지금 사진 유지", keep, photo) {
            if (!keep && com.skh.hihanaevo.ui.PageBackgroundStore.restore(context)) appearance.onBackgroundChanged()
        }
        Choice("새 기본 배경", !keep, null) {
            if (keep) {
                com.skh.hihanaevo.ui.PageBackgroundStore.stash(context)
                appearance.onBackgroundChanged()
            }
        }
    }
}

@Composable
private fun ColumnScope.UpdateIconsPage() {
    PageHead(
        "디자인",
        "아이콘 결 고르기",
        "머티리얼(기본)과 One UI, 새로 그린 일곱 가지 결 중에서 고릅니다. 아래에서 결을 바꾸고 바의 칸을 눌러 보세요. 설정 → 개인화에서 다시 바꿀 수 있습니다.",
        isNew = true,
    )
    val context = LocalContext.current
    com.skh.hihanaevo.ui.OneUiChipStrip(Modifier.padding(bottom = 12.dp)) {
        com.skh.hihanaevo.ui.IconStyle.entries.forEach { option ->
            com.skh.hihanaevo.ui.OneUiChip(
                selected = com.skh.hihanaevo.ui.IconStyleStore.style == option,
                onClick = { com.skh.hihanaevo.ui.IconStyleStore.set(context, option) },
                label = option.label,
            )
        }
    }
    val glassState = remember { HazeState() }
    var selected by remember { mutableIntStateOf(TabIndex.HOME) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(32.dp))
            .consumeWindowInsets(WindowInsets.navigationBars),
    ) {
        CompositionLocalProvider(LocalHazeState provides glassState) {
            Box(Modifier.fillMaxSize().hazeSource(glassState).oneUiPageBackground())
            // 실제 앱처럼 세 칸 [홈 · 타임라인 · ⊞] — ⊞ 는 여는 단추라 캡슐이 가지 않습니다.
            AppNavBar(
                selected = selected,
                onSelect = { if (it != 2) selected = it },
                modifier = Modifier.align(Alignment.BottomCenter),
                lensAtRest = true,
            )
        }
    }
}

@Composable
private fun ColumnScope.NavigatePage() {
    PageHead(
        "새 조작법",
        "세 칸 하단 바와 손동작",
        "화면을 고르는 방법이 바뀌었습니다. 옆으로 밀어 홈과 타임라인을 오가고, 나머지 화면은 전체에서 엽니다. 열린 화면은 쓸어내려 닫습니다.",
        isNew = true,
    )
    NavigationDemo(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp))
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward), TourBlue, "옆으로 밀어 홈 ↔ 타임라인", "홈에서 왼쪽으로 밀면 타임라인, 다시 오른쪽으로 밀면 홈입니다. 하단 바의 칸을 눌러도 됩니다.")
        RowGap()
        TourRow(rememberVectorPainter(com.skh.hihanaevo.ui.MaterialExtraIcons.GridOn), TourViolet, "⊞ 전체", "타임라인에서 한 번 더 밀거나 ⊞ 를 누르면 시간표·급식·게시판 같은 여덟 곳이 모인 화면이 올라옵니다. 아래 바로 가기에는 좌석·교과교실·심야면학처럼 자주 여는 곳을 '편집'으로 골라 둡니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft), TourGreen, "그 자리에서 이어 열기", "고른 화면과 게시글은 같은 자리에서 옆으로 열립니다. 왼쪽 위 < 나 뒤로 가기로 한 단계씩 돌아갑니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown), TourOrange, "쓸어내려 닫기", "화면 맨 위에서 아래로 쓸어내리거나 ✕ 를 누르면 열린 화면을 모두 닫고 홈이나 타임라인으로 돌아갑니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.Refresh), TourSlate, "끌어내려 새로고침", "홈 맨 위에서 끌어내리면 시간표·급식·게시판을 새로 받습니다.")
    }
}

@Composable
private fun ColumnScope.PracticePage(onDone: () -> Unit) {
    PageHead(
        "새 조작법",
        "뒤로 가기와 쓸어내리기 연습",
        "아래 작은 화면에서 직접 해 보세요. 뒤로 가기는 한 단계 전 화면으로, 쓸어내리기는 열린 화면을 모두 닫고 홈으로 돌아갑니다. 연습을 마치면 다음으로 넘어갈 수 있습니다.",
    )
    GesturePractice(Modifier.fillMaxWidth(), onDone = onDone)
}

@Composable
private fun ColumnScope.UpdateMorePage() {
    PageHead("그 밖에", "그 밖에 달라진 것", "매일 쓰는 곳들도 손봤습니다.")
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_bolt), TourGreen, "더 부드러운 화면", "홈과 타임라인을 넘기거나 화면을 열 때 멈칫하던 곳을 찾아 고쳐 더 부드럽게 움직입니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.Refresh), TourBlue, "가벼워진 홈 머리", "날짜를 '홈' 옆에 두고, 오른쪽 위에는 알리미와 설정만 남겼습니다. 새로고침은 홈 맨 위에서 끌어내리면 됩니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.DateRange), TourBlue, "4주 앞까지 보는 일정", "타임라인의 '주'에서 앞으로 4주 일정을 봅니다. 몇 주씩 이어지는 운영 안내는 학사일정 목록에서 빼 날짜별 일정이 먼저 보입니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_meal), TourOrange, "보던 끼니부터", "홈 급식 칸을 누르면 그 칸에 보이던 끼니(저녁이면 저녁)부터 열립니다.")
        RowGap()
        TourRow(rememberVectorPainter(androidx.compose.material.icons.Icons.Filled.Search), TourViolet, "교과교실 현황 한눈에", "교과교실 현황이 건물 '전체'부터 보여, 학교 전체 신청을 교실별로 바로 볼 수 있습니다.")
    }
}
