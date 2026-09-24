package com.yhjang.timetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yhjang.timetable.ui.LocalHazeState
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiTextField
import com.yhjang.timetable.ui.isDark
import com.yhjang.timetable.ui.oneUiPageBackground
import androidx.compose.foundation.layout.offset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AccountCircle
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

private enum class TourPage {
    HELLO, WIDGET, NOW_BAR, APPLY, MEAL, BOARD, PERSONALIZE, GLASS, PRIVACY, PERMISSIONS, LOGIN, DONE,
    UPDATE_SUMMARY, UPDATE_BOARD, UPDATE_TABS, UPDATE_UPCOMING, UPDATE_HOME_STAY,
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
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // 투어를 여는 순간 한 번만 정합니다 — 권한 장에서 허용하자마자 그 장이 사라져 페이지가 밀리지 않게.
    val pages = remember(kind) {
        val needsPermissions = !notificationsGranted(context) || !ExactAlarmPermission.isGranted(context)
        when (kind) {
            TourKind.WELCOME -> buildList {
                addAll(
                    listOf(
                        TourPage.HELLO, TourPage.WIDGET, TourPage.NOW_BAR, TourPage.APPLY, TourPage.MEAL, TourPage.BOARD,
                        TourPage.PERSONALIZE, TourPage.GLASS, TourPage.PRIVACY,
                    ),
                )
                if (needsPermissions) add(TourPage.PERMISSIONS)
                add(TourPage.LOGIN)
                add(TourPage.DONE)
            }
            TourKind.UPDATE -> buildList {
                addAll(listOf(TourPage.UPDATE_SUMMARY, TourPage.UPDATE_BOARD, TourPage.UPDATE_TABS, TourPage.UPDATE_UPCOMING, TourPage.UPDATE_HOME_STAY, TourPage.PRIVACY))
                if (needsPermissions) add(TourPage.PERMISSIONS)
            }
        }
    }
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var showingPrivacy by remember { mutableStateOf(false) }
    // 로그인 화면의 '로그인' 버튼은 입력·확인 상태를 알아야 해서, 페이지가 올려 주는 동작으로 바꿔 끼웁니다.
    var loginAction by remember { mutableStateOf<LoginAction?>(null) }

    fun next() {
        if (pagerState.currentPage >= pages.lastIndex) onFinish()
        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
    }

    Dialog(
        // 다이얼로그 창의 뒤로 가기는 여기로만 옵니다(안쪽 BackHandler 는 액티비티 쪽에 걸려 받지 못했습니다).
        // 앞 장으로만 가고, 첫 장에서는 닫히지 않습니다 — 건너뛸 수 없으니까요 (dev 빌드는 확인하기 편하게 닫힘).
        onDismissRequest = {
            if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            else if (BuildConfig.DEBUG) onFinish()
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
        val page = pages[pagerState.currentPage]
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            Column(
                Modifier
                    .fillMaxSize()
                    .oneUiPageBackground()
                    .padding(top = statusTop + 12.dp, bottom = navBottom + 16.dp),
            ) {
                // 위: 진행 점 + (로그인 장에서만) 나중에 · dev 빌드는 모든 장에 건너뛰기
                Row(
                    Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TourDots(count = pages.size, current = pagerState.currentPage)
                    Spacer(Modifier.weight(1f))
                    if (page == TourPage.LOGIN && (BuildConfig.DEBUG || !HanaCredentialStore.hasCredentials(context))) {
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
                    if (BuildConfig.DEBUG && pagerState.currentPage != pages.lastIndex) {
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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                ) { index ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                    ) {
                        when (pages[index]) {
                            TourPage.HELLO -> HelloPage()
                            TourPage.WIDGET -> WidgetPage()
                            TourPage.NOW_BAR -> NowBarPage()
                            TourPage.APPLY -> ApplyPage()
                            TourPage.MEAL -> MealPage()
                            TourPage.BOARD -> BoardPage()
                            TourPage.PERSONALIZE -> PersonalizePage(appearance)
                            TourPage.GLASS -> GlassPage()
                            TourPage.PRIVACY -> PrivacyPage(beforeLogin = kind == TourKind.WELCOME, onMore = { showingPrivacy = true })
                            TourPage.PERMISSIONS -> PermissionsPage()
                            TourPage.LOGIN -> LoginPage(onReady = { loginAction = it }, onLinked = onAccountLinked, onLoggedIn = ::next)
                            TourPage.DONE -> DonePage()
                            TourPage.UPDATE_SUMMARY -> UpdateSummaryPage()
                            TourPage.UPDATE_BOARD -> UpdateBoardPage()
                            TourPage.UPDATE_TABS -> UpdateTabsPage()
                            TourPage.UPDATE_UPCOMING -> UpdateUpcomingPage()
                            TourPage.UPDATE_HOME_STAY -> UpdateHomeStayPage()
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                // 아래: 주 버튼 — 로그인 화면에서는 로그인, 마지막 장에서는 시작하기.
                val last = pagerState.currentPage == pages.lastIndex
                val login = loginAction.takeIf { page == TourPage.LOGIN }
                TourButton(
                    text = when {
                        login != null && login.busy -> "확인 중"
                        login != null && login.label != null -> login.label
                        last -> "시작하기"
                        page == TourPage.UPDATE_SUMMARY -> "둘러보기"
                        else -> "다음"
                    },
                    enabled = login?.enabled ?: true,
                    busy = login?.busy == true,
                    onClick = { login?.run?.invoke() ?: next() },
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding().coerceAtLeast(0.dp)),
                )
            }
        }
        if (showingPrivacy) PrivacyScreen(onDismiss = { showingPrivacy = false })
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
                    .background(
                        if (i == current) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                    ),
            )
        }
    }
}

@Composable
private fun TourButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(TourBlue)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            OneUiLoading(size = 16.dp, stroke = 2.dp, color = Color.White)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// MARK: - 페이지 공통 조각

@Composable
private fun ColumnScope.PageHead(eyebrow: String, title: String, desc: String? = null, isNew: Boolean = false) {
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clip(shape).background(TourBlue).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
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

@Composable
private fun ColumnScope.HelloPage() {
    Spacer(Modifier.height(56.dp))
    Box(
        Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // 적응형 아이콘은 108 중 가운데 72만 보이므로 1.5배로 그려 런처와 같은 모양으로 잘라 냅니다.
        Icon(painterResource(R.drawable.ic_launcher_background), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.requiredSize(120.dp))
        Icon(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.requiredSize(120.dp))
    }
    PageHead(
        eyebrow = "환영합니다",
        title = "하이하나 Neo",
        desc = "시간표, 면학 위치, 신청, 급식, 게시판을 하나고 학사시스템과 연결해 한곳에 모았습니다. 주요 기능을 차례로 소개합니다.",
    )
}

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
        "면학실·도서관·심야면학 신청",
        "배치도에서 빈 자리를 눌러 바로 신청하고, 내 자리를 눌러 취소합니다. 심야면학도 앱 안에서 로그인해 좌석을 신청할 수 있고, " +
            "신청한 심야 타임은 오늘 일정과 Now Bar, 위젯에 함께 표시됩니다.",
    )
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("면학실 3층 · 1타임", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        // 남 파랑 · 여 분홍 · 빈 자리 · 내 자리(테두리) — 실제 좌석 화면과 같은 색 규칙.
        val seats = "bxpbxpxbmxpbpxbxxb"
        seats.chunked(6).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                line.forEach { c ->
                    val color = when (c) {
                        'b' -> TourBlue.copy(alpha = 0.55f)
                        'p' -> TourPink.copy(alpha = 0.55f)
                        'm' -> TourBlue
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(7.dp)).background(color)
                            .then(if (c == 'm') Modifier.padding(2.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.25f)) else Modifier),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    // 심야면학 — 별도 사이트지만 앱 안에서 로그인·신청하고, 신청하면 오늘 일정 끝에 심야 타임이 붙습니다.
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_bedtime), TourViolet, "심야면학 1타임", "3층 12번 · 신청됨") {
            Text(
                "취소",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)).padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
        Spacer(Modifier.height(12.dp))
        Text("오늘 남은 일정", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        listOf("면학 2타임 · 면학실 3층 A-07", "휴식", "심야 1타임 · 3층 12번").forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
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
        "새 글 알림과 AI 요약",
        "고른 게시판에 새 글이 올라오면 알림으로 알려 드립니다. 글마다 구글의 최신 LLM인 Gemma 4가 핵심을 요약하고, 이미지뿐인 가정통신문도 읽어 요약합니다.",
    )
    OneUiCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF1F5A3C)))
            Spacer(Modifier.width(7.dp))
            Text("하이하나 Neo · 학생공지 · 방금", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    // 설정 → 화면 과 같은 부품·같은 저장 — 고르는 즉시 이 투어 화면부터 색과 배경이 바뀝니다.
    OneUiCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        AccentPickerRow(selectedArgb = appearance.accentArgb, onSelect = appearance.onAccentChange)
        com.yhjang.timetable.ui.OneUiDivider()
        PlanStore.themes.forEach { option ->
            com.yhjang.timetable.ui.OneUiRadioRow(
                selected = appearance.appTheme == option,
                label = PlanStore.themeLabel(option),
                onClick = { appearance.onAppThemeChange(option) },
            )
        }
        com.yhjang.timetable.ui.OneUiDivider()
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
    val headerState = com.yhjang.timetable.ui.rememberOneUiHeaderState()
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
                        .padding(top = com.yhjang.timetable.ui.OneUiCompactBarHeight + com.yhjang.timetable.ui.OneUiHeaderExpandedExtra + 8.dp, bottom = 96.dp),
                ) {
                    GlassBackdrop()
                }
            }
            com.yhjang.timetable.ui.OneUiCollapsingHeader(state = headerState, title = "홈", subtitle = "9월 24일 목요일")
            com.yhjang.timetable.ui.OneUiActionPill(
                pillAlpha = ((headerState.fraction - 0.3f) / 0.7f).coerceIn(0f, 1f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 10.dp),
            ) {
                listOf(Icons.Default.Refresh, Icons.Default.Notifications, Icons.Default.AccountCircle, Icons.Default.Settings).forEach { icon ->
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().align(Alignment.BottomCenter), contentAlignment = Alignment.Center) {
                val width = maxWidth
                AppNavBar(
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.requiredWidth(width + 16.dp),
                    showDev = false,
                    lensAtRest = true,
                )
            }
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
        if (BuildConfig.DEBUG) null
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
                runCatching { HanaPortalClient.get().fetchDailySync(context, PlanStore.today()) }
            }
            checking = false
            result.fold(
                onSuccess = {
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
    if (BuildConfig.DEBUG) {
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

@Composable
private fun ColumnScope.DonePage() {
    Spacer(Modifier.height(48.dp))
    Box(Modifier.size(68.dp).clip(CircleShape).background(TourGreen.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF8FDC6B), modifier = Modifier.size(34.dp))
    }
    PageHead(
        "시작",
        "준비 완료",
        "홈 화면에 위젯을 추가하면 앱을 열지 않아도 지금 일정을 볼 수 있습니다. 홈 화면 빈 곳을 길게 누르고 위젯에서 하이하나 Neo를 고르세요.",
    )
}

// MARK: - 업데이트 (11.1)
// 투어 문구 규칙: 제목은 명사형(문장 X), 설명은 문장으로.

@Composable
private fun ColumnScope.UpdateSummaryPage() {
    // 이 장들은 11.1 에서 바뀐 것을 소개하므로 버전을 고정해 적습니다 — 다음 큰 업데이트 때 내용과 함께 바꿉니다.
    PageHead("새 버전", "11.1 업데이트", "게시판, 학사 탭, 귀가 기간의 일정 표시가 달라졌습니다. 주요 변경 사항을 차례로 소개합니다.")
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_settings_book), TourViolet, "앱 내 게시글과 AI 요약")
        RowGap()
        TourRow(painterResource(R.drawable.ic_widgets), TourBlue, "학사 탭 개편")
        RowGap()
        TourRow(painterResource(R.drawable.ic_next), TourOrange, "아이콘이 붙은 오늘 남은 일정")
        RowGap()
        TourRow(painterResource(R.drawable.ic_home_stay), TourGreen, "귀가 기간 일정 자동 해제")
        RowGap()
        TourRow(painterResource(R.drawable.ic_settings_meal), TourYellow, "급식 칼로리·영양·원산지")
        RowGap()
        TourRow(rememberVectorPainter(Icons.Default.Lock), TourSlate, "개인정보 보호 안내")
    }
}

@Composable
private fun ColumnScope.UpdateBoardPage() {
    PageHead(
        "게시판",
        "앱 내에서 열리는 게시글과 AI 요약",
        "이제 게시글이 브라우저로 하이하나를 띄우는 대신 앱 안에서 바로 열립니다. 요약은 구글의 최신 LLM인 Gemma 4로 만들고, " +
            "이미지만 있는 글도 이미지를 인식해 요약합니다.",
        isNew = true,
    )
    // 목록 — 한 줄 요약 알약.
    TourCaption("게시판 목록")
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("2학기 교내 수학경시대회 참가 신청 안내", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        AiSummaryPill("10/7(수)까지 포털 신청, 10/14 시청각실", Modifier.padding(top = 9.dp))
    }
    Spacer(Modifier.height(18.dp))
    // 글 화면 — 예전엔 브라우저 주소창이 떴던 자리에 이제 앱 화면이 그대로 열립니다.
    TourCaption("이전에는 브라우저로")
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(0.55f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "hh.hana.hs.kr/main/board/board_view.do",
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(18.dp))
    TourCaption("이제는 앱 안에서")
    InAppPostPreview()
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

/** 앱 안 글 화면을 줄여 그린 그림 — 뒤로 버튼·제목·실제 AI 요약 카드·본문·첨부. */
@Composable
private fun InAppPostPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.onSurface.copy(alpha = 0.05f))
            .padding(vertical = 14.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("학생공지", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text("2학기 교내 수학경시대회 참가 신청 안내", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("교무기획부 · 2026-09-22 · 조회 312", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        SummaryCard(
            state = PostSummarizer.State.Done(
                PostSummary(
                    line = "10/7(수)까지 포털 신청, 10/14 시청각실",
                    points = listOf("2학년 희망자 대상, 포털 신청·내역에서 신청함", "대회는 10/14(수) 7교시, 시청각실에서 진행함"),
                ),
            ),
            onRetry = {},
            horizontalPadding = 10.dp,
        )
        Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(1f, 0.92f, 0.66f).forEach { w ->
                Box(Modifier.fillMaxWidth(w).height(8.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.1f)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .padding(horizontal = 14.dp)
                .clip(CircleShape)
                .background(scheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_tour_attach), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("참가신청서.hwp", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ColumnScope.UpdateTabsPage() {
    PageHead(
        "하단 바",
        "학사 탭 개편",
        "학사 탭이 신청·내역과 게시판으로 나뉘었습니다. 시간표와 학사일정은 일정 탭 위쪽의 칩으로 바꿔 볼 수 있습니다.",
    )
    // 실제 하단 바·칩을 그대로 그립니다 — 새로 생긴 칸(신청·내역 → 게시판 → 일정)을 차례로 짚어 줍니다.
    val highlights = listOf(TabIndex.APPLY, TabIndex.BOARD, TabIndex.SCHEDULE)
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_600)
            step = (step + 1) % highlights.size
        }
    }
    var chip by remember { mutableIntStateOf(0) }
    // 칩을 직접 누르면 자동 전환 타이머를 처음부터 다시 셉니다(누르자마자 저절로 되돌아가지 않게).
    var chipTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(chipTaps) {
        while (true) {
            delay(2_400)
            chip = 1 - chip
        }
    }
    TourCaption("하단 바")
    // 탐색 바 자체의 시스템 바 여백과 뒤 화면 유리 효과는 빼고(투어 창 안이라) 모양만 그대로.
    // 바는 원래 화면 가장자리에서 20dp 안쪽에 뜨므로, 페이지 여백(24dp)을 넘어 그만큼 넓혀 실제와 같은 폭으로 그립니다.
    CompositionLocalProvider(LocalHazeState provides null) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars),
            contentAlignment = Alignment.Center,
        ) {
            AppNavBar(
                selected = highlights[step],
                onSelect = { index -> highlights.indexOf(index).takeIf { it >= 0 }?.let { step = it } },
                modifier = Modifier.requiredWidth(maxWidth + 48.dp),
                showDev = false,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    TourCaption("일정 탭")
    Box(Modifier.padding(horizontal = 6.dp)) {
        ScheduleSubTabs(chip) {
            chip = it
            chipTaps++
        }
    }
    // 칩에 따라 아래 내용이 옆으로 밀려 바뀝니다 — 시간표는 왼쪽, 학사일정은 오른쪽.
    AnimatedContent(
        targetState = chip,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
            (slideInHorizontally(slide) { it / 3 * dir } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(slide) { -it / 3 * dir } + fadeOut(tween(160))) using
                SizeTransform(clip = false)
        },
        label = "scheduleChip",
        modifier = Modifier.fillMaxWidth(),
    ) { selected ->
        if (selected == 0) MiniTimetable() else MiniAcademicSchedule()
    }
}

/** 일정 탭 · 시간표 칩 그림 — 요일 × 교시 작은 표. */
@Composable
private fun MiniTimetable() {
    val scheme = MaterialTheme.colorScheme
    val rows = listOf(
        listOf("국어", "수학", "영어", "물리", "정보"),
        listOf("영어", "화학", "수학", "국어", "체육"),
        listOf("한국사", "국어", "물리", "영어", "수학"),
        listOf("수학", "체육", "화학", "한국사", "영어"),
    )
    val tints = mapOf(
        "국어" to TourPink, "수학" to TourBlue, "영어" to TourViolet, "물리" to TourGreen,
        "화학" to TourYellow, "한국사" to TourOrange, "정보" to TourSlate, "체육" to TourGreen,
    )
    OneUiCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(18.dp))
            listOf("월", "화", "수", "목", "금").forEach { day ->
                Text(
                    day,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { period, subjects ->
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${period + 1}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.width(18.dp))
                subjects.forEach { subject ->
                    Text(
                        subject,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background((tints[subject] ?: TourSlate).copy(alpha = 0.22f))
                            .padding(vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** 일정 탭 · 학사일정 칩 그림 — D-day 와 다가오는 일정 목록. */
@Composable
private fun MiniAcademicSchedule() {
    val scheme = MaterialTheme.colorScheme
    OneUiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "D-18",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
                modifier = Modifier.clip(CircleShape).background(scheme.primary.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("중간고사", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        listOf(
            Triple("9/27 (일)", "귀교(1,2,3)", TourGreen),
            Triple("10/3 (토)", "개천절", TourOrange),
            Triple("10/12 (월)", "중간고사", TourBlue),
        ).forEachIndexed { i, (date, name, dot) ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(date, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ColumnScope.UpdateUpcomingPage() {
    PageHead(
        "홈",
        "아이콘이 붙은 오늘 남은 일정",
        "홈의 오늘 남은 일정에 수업과 면학 장소마다 색 아이콘이 붙고, 장소와 시간이 두 줄로 정리됩니다. " +
            "아이콘과 색은 위젯·Now Bar와 같으며, 설정에서 위젯의 종류별 색을 바꾸면 함께 바뀝니다. 담당 선생님과 면학감독은 장소 옆에 표시됩니다.",
        isNew = true,
    )
    UpcomingSampleCard()
}

@Composable
private fun ColumnScope.UpdateHomeStayPage() {
    PageHead(
        "일정",
        "귀가 기간 일정 자동 해제",
        "학사일정의 귀가·귀교에 맞춰 집에 있는 동안에는 일정과 Now Bar가 꺼집니다. 귀교일에는 마지막 타임부터 다시 표시됩니다.",
        isNew = true,
    )
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("지금", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("편안한 귀가 보내세요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("9월 27일(일) 귀교", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
