package com.yhjang.timetable

import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiBadge
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
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

/** 이 버전의 투어 — 앱을 열 때 이 값보다 작게 기록돼 있으면 한 번 보여 줍니다 (새로 설치는 새로 설치 투어, 업데이트는 업데이트 투어). */
const val TOUR_VERSION = 130
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
    HELLO, WIDGET, NOW_BAR, APPLY, SEATS, MEAL, BOARD, PERSONALIZE, GLASS, PRIVACY, PERMISSIONS, LOGIN, DONE,
    UPDATE_SUMMARY, UPDATE_OUTING, UPDATE_CLASSROOM, UPDATE_WEATHER, UPDATE_SEARCH, UPDATE_HEADER, UPDATE_CALENDAR, UPDATE_MORE,
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
                        TourPage.HELLO, TourPage.WIDGET, TourPage.NOW_BAR, TourPage.APPLY, TourPage.SEATS, TourPage.MEAL, TourPage.BOARD,
                        TourPage.PERSONALIZE, TourPage.GLASS, TourPage.PRIVACY,
                    ),
                )
                if (needsPermissions) add(TourPage.PERMISSIONS)
                add(TourPage.LOGIN)
                add(TourPage.DONE)
            }
            TourKind.UPDATE -> buildList {
                addAll(
                    listOf(
                        TourPage.UPDATE_SUMMARY, TourPage.UPDATE_OUTING, TourPage.UPDATE_CLASSROOM, TourPage.UPDATE_WEATHER,
                        TourPage.UPDATE_SEARCH, TourPage.UPDATE_HEADER, TourPage.UPDATE_CALENDAR, TourPage.UPDATE_MORE,
                    ),
                )
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

    // 투어는 다른 창이라 카드가 메인 화면의 배경 Haze 를 비출 수 없습니다 — 반투명 색으로.
    CompositionLocalProvider(com.yhjang.timetable.ui.LocalMaterialHazeState provides null) {
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
                            TourPage.SEATS -> SeatsPage()
                            TourPage.MEAL -> MealPage()
                            TourPage.BOARD -> BoardPage()
                            TourPage.PERSONALIZE -> PersonalizePage(appearance)
                            TourPage.GLASS -> GlassPage()
                            TourPage.PRIVACY -> PrivacyPage(beforeLogin = kind == TourKind.WELCOME, onMore = { showingPrivacy = true })
                            TourPage.PERMISSIONS -> PermissionsPage()
                            TourPage.LOGIN -> LoginPage(onReady = { loginAction = it }, onLinked = onAccountLinked, onLoggedIn = ::next)
                            TourPage.DONE -> DonePage()
                            TourPage.UPDATE_SUMMARY -> UpdateSummaryPage()
                            TourPage.UPDATE_OUTING -> UpdateOutingPage()
                            TourPage.UPDATE_CLASSROOM -> UpdateClassroomPage()
                            TourPage.UPDATE_WEATHER -> UpdateWeatherPage()
                            TourPage.UPDATE_SEARCH -> UpdateSearchPage()
                            TourPage.UPDATE_HEADER -> UpdateHeaderPage()
                            TourPage.UPDATE_CALENDAR -> UpdateCalendarPage()
                            TourPage.UPDATE_MORE -> UpdateMorePage()
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
        "앱 안에서 하는 신청·내역",
        "도서관, 면학실, 심야면학, 교과교실, 외출·외박 모두 하이하나 웹페이지 없이 앱에서 신청하고 내역을 확인합니다. " +
            "도서관·면학실·심야면학은 좌석 배치도에서 빈 자리를 눌러 바로 신청하고, 내 자리를 눌러 취소합니다. " +
            "외출·외박은 승인되면 출입 QR을 띄울 수 있습니다.",
    )
    val scheme = MaterialTheme.colorScheme
    // 신청·내역 탭의 서비스 카드를 한 장에 줄여 — 서비스마다 내역 · 신청 두 버튼 (실제 탭과 같은 순서·같은 버튼).
    TourCaption("신청·내역 탭")
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
            "게시판 탭의 돋보기로 하이하나의 모든 글을 기간 제한 없이 검색할 수 있습니다.",
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
            // 버튼 섬의 폭을 재서 접힌 제목 섬이 거기까지만 늘어나게 — 안 그러면 두 유리가 겹쳤습니다.
            var pillWidthPx by remember { mutableIntStateOf(0) }
            val pillReserve = with(androidx.compose.ui.platform.LocalDensity.current) { pillWidthPx.toDp() } + 10.dp + 16.dp
            com.yhjang.timetable.ui.OneUiCollapsingHeader(state = headerState, title = "홈", subtitle = "9월 24일 목요일", endReserve = pillReserve)
            // 실제 앱(홈 탭)처럼 오른쪽 섬 하나 — 새로고침 · 서울 날씨 · 알리미 · 카드 순서 · 설정.
            com.yhjang.timetable.ui.OneUiActionPill(
                pillAlpha = ((headerState.fraction - 0.3f) / 0.7f).coerceIn(0f, 1f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 10.dp).onSizeChanged { pillWidthPx = it.width },
            ) {
                @Composable fun slot(content: @Composable () -> Unit) = Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { content() }
                slot { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(22.dp)) }
                com.yhjang.timetable.ui.OneUiActionDivider()
                slot { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(22.dp)) }
                slot { Icon(painterResource(R.drawable.ic_weather_outline), contentDescription = null, modifier = Modifier.size(22.dp)) }
                com.yhjang.timetable.ui.OneUiActionDivider()
                slot { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(22.dp)) }
                slot { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(22.dp)) }
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

// MARK: - 업데이트 (12.0)
// 투어 문구 규칙: 제목은 명사형(문장 X), 설명은 문장으로.

// MARK: - 13.0 업데이트 투어 — 12.x 를 쓰던 사람에게 이번 버전에서 바뀐 것만. 그림은 실제 화면을 그대로 줄여 그립니다.

@Composable
private fun ColumnScope.UpdateSummaryPage() {
    // 이 장들은 13.0 에서 바뀐 것을 소개하므로 버전을 고정해 적습니다 — 다음 큰 업데이트 때 내용과 함께 바꿉니다.
    Spacer(Modifier.height(20.dp))
    VersionLogo(13, 112.dp, Modifier.align(Alignment.CenterHorizontally))
    PageHead("새 버전", "13.0 업데이트", "외출·외박과 교과교실 신청을 앱에서 하고, 하이하나의 모든 글을 검색할 수 있게 됐습니다. 주요 변경 사항을 차례로 소개합니다.")
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_home_stay), TourBlue, "외출·외박 신청")
        RowGap()
        TourRow(painterResource(R.drawable.ic_meeting_room), TourGreen, "교과교실 신청")
        RowGap()
        TourRow(painterResource(R.drawable.ic_weather_outline), TourOrange, "귀가 기간 서울 날씨")
        RowGap()
        TourRow(rememberVectorPainter(Icons.Default.Search), TourViolet, "하이하나 검색")
        RowGap()
        TourRow(painterResource(R.drawable.ic_widgets), TourPink, "오른쪽 위 버튼 섬")
        RowGap()
        TourRow(rememberVectorPainter(Icons.Default.DateRange), TourSlate, "학사일정을 캘린더로")
    }
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

/** 신청 화면의 회색 보조 버튼 모양 — 실제 버튼(OneUiButton Neutral)을 그대로 씁니다. */
@Composable
private fun TourDateTimeRow(label: String, date: String, time: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OneUiButton(text = date, onClick = {}, style = com.yhjang.timetable.ui.OneUiButtonStyle.Neutral, modifier = Modifier.weight(1.6f))
        OneUiButton(text = time, onClick = {}, style = com.yhjang.timetable.ui.OneUiButtonStyle.Neutral, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ColumnScope.UpdateOutingPage() {
    PageHead(
        "외출·외박",
        "외출·외박 신청",
        "외출·외박을 하이하나 웹페이지 대신 앱에서 신청합니다. 출발과 복귀의 날짜·시각은 버튼을 누르면 뜨는 휠을 굴려 고르고, " +
            "기간과 신청 기한을 미리 보여 줍니다. 신청 중인 건은 내역에서 고치거나 취소할 수 있고, 승인되면 출입 QR을 띄웁니다.",
        isNew = true,
    )
    val scheme = MaterialTheme.colorScheme
    TourCaption("신청 화면")
    OneUiCard(Modifier.fillMaxWidth()) {
        TourDateTimeRow("출발", "10월 24일 (토)", "13:00")
        Spacer(Modifier.height(14.dp))
        TourDateTimeRow("복귀", "10월 25일 (일)", "21:00")
        Spacer(Modifier.height(12.dp))
        Text("기간 1일 8시간", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.primary)
        Text("신청 기한 10/23(금) 09:00", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
    Spacer(Modifier.height(18.dp))
    TourCaption("날짜·시각 휠 — 직접 굴려 보세요")
    var month by remember { mutableIntStateOf(9) }
    var day by remember { mutableIntStateOf(23) }
    var hour by remember { mutableIntStateOf(13) }
    var minute by remember { mutableIntStateOf(0) }
    val days = remember(month) { (1..java.time.YearMonth.of(2026, month + 1).lengthOfMonth()).map { "${it}일" } }
    OneUiCard(Modifier.fillMaxWidth()) {
        com.yhjang.timetable.ui.GlassWheelFrame {
            com.yhjang.timetable.ui.GlassWheel((1..12).map { "${it}월" }, month, { month = it }, Modifier.weight(1f))
            com.yhjang.timetable.ui.GlassWheel(days, day.coerceAtMost(days.lastIndex), { day = it }, Modifier.weight(1f))
            com.yhjang.timetable.ui.GlassWheel((0..23).map { "%02d시".format(it) }, hour, { hour = it }, Modifier.weight(1f))
            com.yhjang.timetable.ui.GlassWheel((0..59).map { "%02d분".format(it) }, minute, { minute = it }, Modifier.weight(1f))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.UpdateClassroomPage() {
    PageHead(
        "교과교실",
        "교과교실 신청",
        "교과교실도 앱에서 신청합니다. 건물, 교실, 타임 순서로 고르고, 교사 승인 교실은 승인 교사를, 사전 양식 교실은 주의사항 동의까지 " +
            "앱에서 처리합니다. 아래에 오늘 내 신청이 보이고, 타임이 시작하기 전이면 취소할 수 있습니다.",
        isNew = true,
    )
    val scheme = MaterialTheme.colorScheme
    var building by remember { mutableIntStateOf(0) }
    var room by remember { mutableIntStateOf(1) }
    var slot by remember { mutableIntStateOf(0) }
    @Composable
    fun Label(text: String) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    }
    @Composable
    fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l -> com.yhjang.timetable.ui.OneUiChip(selected = i == selected, onClick = { onSelect(i) }, label = l) }
        }
        Spacer(Modifier.height(10.dp))
    }
    TourCaption("신청 화면 — 칩을 눌러 보세요")
    OneUiCard(Modifier.fillMaxWidth()) {
        Label("건물")
        Chips(listOf("A동", "B동"), building) { building = it }
        Label("교실")
        Chips(if (building == 0) listOf("A201", "A202", "A305") else listOf("B301", "B302"), room.coerceAtMost(if (building == 0) 2 else 1)) { room = it }
        OneUiBadge("교사승인", container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer)
        Spacer(Modifier.height(8.dp))
        Label("타임")
        Chips(listOf("1타임 19:00", "2타임 21:30"), slot) { slot = it }
        Label("승인 교사")
        Chips(listOf("김하늘", "이서연"), 0) {}
        OneUiButton(text = "신청하기", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(12.dp))
    TourCaption("오늘 내 신청")
    OneUiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("A202 · 1타임", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("19:00–21:00 · 승인 · 수학 문제 풀이 스터디", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            OneUiButton(text = "취소", onClick = {}, style = com.yhjang.timetable.ui.OneUiButtonStyle.Neutral, compact = true)
        }
    }
}

@Composable
private fun ColumnScope.UpdateWeatherPage() {
    PageHead(
        "귀가 기간",
        "귀가 기간 서울 날씨",
        "귀가 기간에는 홈과 위젯에 \"지금 서울 | 귀교 밤 9시\" 날씨가 크게 보입니다. 비가 오면 우산 안내가 붙고, 귀교 후 첫 일정과 " +
            "다가오는 시험도 함께 나옵니다. 귀교일 밤 9시가 되면 사라집니다. 날씨를 누르면 귀교 때까지 1시간 단위 서울 날씨가 열립니다.",
        isNew = true,
    )
    TourCaption("홈의 날씨 블록")
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("편안한 귀가 보내세요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("10월 18일(일) 귀교", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        HomeStayExtraRows(
            HomeStayExtras(
                weatherNow = WeatherTile("지금 서울", R.drawable.ic_weather_clear, "17°", "맑음 · 25° / 13°", null),
                weatherBack = WeatherTile("귀교 밤 9시", R.drawable.ic_weather_rain, "18°", "흐림 · 강수 90%", "우산 챙기세요"),
                firstPlan = "면학 4타임 · 생활관" to "21:30",
                exam = null,
            ),
        )
    }
    Spacer(Modifier.height(18.dp))
    TourCaption("날씨 화면")
    WeatherScreenMock()
}

/** 서울 날씨 화면 축소판 — 날씨 색 빛무리 위의 큰 기온·최고/최저·문구, 카드의 시간별 줄(시각·아이콘·강수·기온·곡선). */
@Composable
private fun WeatherScreenMock() {
    val scheme = MaterialTheme.colorScheme
    val accent = Color(0xFFFFB224)
    val rain = Color(0xFF3D8BFF)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.onSurface.copy(alpha = 0.04f))
            .drawBehind {
                drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.12f), radius = size.width * 0.45f))
            }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("22°", fontSize = 44.sp, fontWeight = FontWeight.Normal, color = scheme.onSurface, lineHeight = 48.sp)
                Text("↑ 25° / ↓ 13°", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Text("맑음", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                Text("귀교 10/18(일) 밤 9시까지", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(painterResource(R.drawable.ic_weather_clear), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(14.dp))
        val white = scheme.onSurface
        val gray = scheme.onSurfaceVariant
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("시간별", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = gray)
            Spacer(Modifier.height(6.dp))
            val cols = listOf(
                Triple("지금", R.drawable.ic_weather_clear, "22°"), Triple("15시", R.drawable.ic_weather_clear, "25°"),
                Triple("18시", R.drawable.ic_weather_partly, "21°"), Triple("21시", R.drawable.ic_weather_rain, "18°"),
                Triple("귀교", R.drawable.ic_weather_rain, "18°"),
            )
            Row(Modifier.fillMaxWidth()) {
                cols.forEachIndexed { i, (time, icon, temp) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .then(if (i == cols.lastIndex) Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.primary.copy(alpha = 0.14f)) else Modifier)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(time, fontSize = 10.sp, fontWeight = if (i == 0 || i == cols.lastIndex) FontWeight.SemiBold else FontWeight.Normal, color = white)
                        Spacer(Modifier.height(4.dp))
                        Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(if (i >= 3) "90%" else " ", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = rain)
                        Text(temp, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = white)
                    }
                }
            }
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val step = size.width / cols.size
                val ys = listOf(0.55f, 0.2f, 0.5f, 0.85f, 0.85f).map { it * size.height }
                val path = androidx.compose.ui.graphics.Path()
                ys.forEachIndexed { i, y -> if (i == 0) path.moveTo(step / 2f, y) else path.lineTo(step * i + step / 2f, y) }
                drawPath(path, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                ys.forEachIndexed { i, y -> drawCircle(accent, 2.5.dp.toPx(), androidx.compose.ui.geometry.Offset(step * i + step / 2f, y)) }
            }
        }
    }
}

@Composable
private fun ColumnScope.UpdateHeaderPage() {
    PageHead(
        "헤더",
        "오른쪽 위 버튼 섬",
        "오른쪽 위 버튼이 한 섬으로 모였고, 가는 선으로 세 묶음을 나눴습니다. 맨 왼쪽은 새로고침(당기면 화면 가운데로 내려옵니다), " +
            "가운데는 탭마다 자주 쓰는 기능 둘, 오른쪽은 늘 같은 자리의 알리미와 설정입니다. 학사시스템 연동은 설정 → 계정·학년으로 옮겼습니다.",
        isNew = true,
    )
    @Composable
    fun Island(left: @Composable () -> Unit, right: @Composable () -> Unit) {
        // 그림 속 섬은 리퀴드 글래스 대신 같은 모양의 옅은 알약 — 투어 배경을 흐려 비추면 얼룩이 생겼습니다.
        Row(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (MaterialTheme.colorScheme.isDark) 0.10f else 0.07f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            @Composable fun slot(content: @Composable () -> Unit) = Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { content() }
            slot { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
            com.yhjang.timetable.ui.OneUiActionDivider(height = 14.dp, gap = 3.dp)
            slot(left)
            slot(right)
            com.yhjang.timetable.ui.OneUiActionDivider(height = 14.dp, gap = 3.dp)
            slot { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
            slot { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
    }
    @Composable
    fun TabLine(tab: String, what: String, left: @Composable () -> Unit, right: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tab, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Island(left, right)
        }
    }
    val icon = Modifier.size(18.dp)
    OneUiCard(Modifier.fillMaxWidth()) {
        TabLine("홈", "편집 · 날씨", { Icon(Icons.Default.Edit, null, icon) }, { Icon(painterResource(R.drawable.ic_weather_outline), null, icon) })
        TabLine("신청·내역", "순서 · 외출 QR", { Icon(Icons.Default.Edit, null, icon) }, { Icon(painterResource(R.drawable.ic_qr_code), null, icon) })
        TabLine("급식", "주간 · 알레르기", { Icon(Icons.Default.DateRange, null, icon) }, { Icon(Icons.Default.Warning, null, icon) })
        TabLine("게시판", "검색 · 새 글 알림", { Icon(Icons.Default.Search, null, icon) }, { Icon(painterResource(R.drawable.ic_notify_settings), null, icon) })
        TabLine("일정", "전환 · 캘린더", { Icon(Icons.AutoMirrored.Filled.List, null, icon) }, { Icon(Icons.Default.Share, null, icon) })
    }
}

@Composable
private fun ColumnScope.UpdateSearchPage() {
    PageHead(
        "게시판",
        "하이하나 검색",
        "게시판 탭 오른쪽 위의 돋보기로 하이하나의 모든 글을 찾습니다. 게시판과 기간을 가리지 않고, 게시판별 건수와 " +
            "최신순·오래된순으로 나눠 볼 수 있습니다. 찾은 글은 앱 안에서 AI 요약과 함께 열립니다.",
        isNew = true,
    )
    // 실제 검색 화면의 부품(검색창·칩·결과 줄)으로 그립니다. 글 제목은 예시입니다.
    val hits = listOf(
        SearchHit(1, 1, "학생공지", "2학기 교내 수학<span class=\"val\">경시대회</span> 참가 신청 안내"),
        SearchHit(3, 2, "교과공지", "과학 <span class=\"val\">경시대회</span> 결과 및 시상 안내"),
        SearchHit(1, 3, "학생공지", "2025학년도 정보 <span class=\"val\">경시대회</span> 운영 계획"),
    )
    SearchFieldPreview("경시대회")
    Spacer(Modifier.height(12.dp))
    Text("“경시대회” 검색 결과 18건", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
    var chip by remember { mutableIntStateOf(0) }
    Row(Modifier.horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("전체 18", "학생공지 12", "교과공지 6").forEachIndexed { i, l -> com.yhjang.timetable.ui.OneUiChip(selected = chip == i, onClick = { chip = i }, label = l) }
    }
    Spacer(Modifier.height(12.dp))
    OneUiCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        hits.forEachIndexed { i, h ->
            if (i > 0) com.yhjang.timetable.ui.OneUiDivider()
            SearchHitRow(h, showBoard = true) {}
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable
private fun ColumnScope.UpdateCalendarPage() {
    PageHead(
        "일정",
        "학사일정을 캘린더로",
        "일정 탭 오른쪽 위의 공유 버튼으로 앞으로의 학사일정을 한 번에 폰 캘린더에 넣습니다. 삼성 캘린더나 구글 캘린더처럼 " +
            "가져올 앱을 고르면 그 앱에 일정이 추가됩니다.",
        isNew = true,
    )
    val scheme = MaterialTheme.colorScheme
    val events = listOf(
        Triple("10/19 (월)", "1인2기 4쿼터 시작", scheduleColor("SG02010000")),
        Triple("10/30 (금)", "개교기념일", scheduleColor("SG01020000")),
        Triple("12/14 (월)", "기말고사(1,2)", scheduleColor("SG01010000")),
    )
    TourCaption("앞으로의 학사일정")
    OneUiCard(Modifier.fillMaxWidth()) {
        events.forEachIndexed { i, (date, name, color) ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(date, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Share, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(26.dp))
    }
    TourCaption("폰 캘린더 · 10월")
    OneUiCard(Modifier.fillMaxWidth()) {
        // 캘린더 앱의 한 주 줄처럼 — 날짜 칸 아래 색 막대로 일정.
        Row(Modifier.fillMaxWidth()) {
            listOf("일" to 18, "월" to 19, "화" to 20, "수" to 21, "목" to 22, "금" to 23, "토" to 24).forEach { (dow, d) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dow, style = MaterialTheme.typography.labelSmall, color = if (dow == "일") scheme.error else scheme.onSurfaceVariant)
                    Text("$d", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(16.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (d == 19) scheduleColor("SG02010000") else Color.Transparent),
                    )
                }
            }
        }
        Text("1인2기 4쿼터 시작 · 하나고 학사일정", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ColumnScope.UpdateMorePage() {
    PageHead("그 밖에", "그 밖에 달라진 것", "작지만 매일 쓰는 곳들도 손봤습니다.")
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(painterResource(R.drawable.ic_local_library), TourGreen, "도서관·면학실 내역", "포털 웹 대신 앱 화면으로 열리고, 날짜별로 묶어 출석 확인 시각까지 보여 줍니다.")
        RowGap()
        TourRow(rememberVectorPainter(Icons.Default.Notifications), TourSlate, "알리미", "알리미를 앱 안에서 열고, 앱에서 읽은 알리미는 하이하나에서도 읽음으로 바뀝니다.")
        RowGap()
        TourRow(rememberVectorPainter(Icons.Default.Edit), TourBlue, "홈 카드 순서", "홈 오른쪽 위의 연필로 카드 순서를 바꿉니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_academic), TourOrange, "대체공휴일", "학사일정의 공휴일 기준이라 대체공휴일 같은 평일 휴일에도 휴일 일정이 보입니다.")
    }
}
