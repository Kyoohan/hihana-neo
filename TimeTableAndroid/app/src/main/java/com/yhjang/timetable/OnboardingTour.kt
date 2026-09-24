package com.yhjang.timetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 첫 실행 투어 — 옆으로 넘기는 전체 화면. [TourKind.WELCOME] 은 새로 설치한 사람에게 기능 소개 → 개인정보 보호 →
 * 권한 → 로그인 → 완료를, [TourKind.UPDATE] 는 이미 쓰던 사람에게 이번 버전에서 바뀐 것만 보여 줍니다.
 * 권한·로그인은 거절하거나 건너뛰어도 앱을 그대로 쓸 수 있고, 오른쪽 위 '건너뛰기'로 언제든 끝납니다.
 */
enum class TourKind { WELCOME, UPDATE }

private enum class TourPage {
    HELLO, HOME, APPLY, BOARD, PRIVACY, PERMISSIONS, LOGIN, DONE,
    UPDATE_SUMMARY, UPDATE_BOARD, UPDATE_TABS, UPDATE_HOME_STAY,
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
fun OnboardingTour(kind: TourKind, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val pages = remember(kind) {
        when (kind) {
            TourKind.WELCOME -> listOf(
                TourPage.HELLO, TourPage.HOME, TourPage.APPLY, TourPage.BOARD,
                TourPage.PRIVACY, TourPage.PERMISSIONS, TourPage.LOGIN, TourPage.DONE,
            )
            // 이미 쓰던 사람 — 알림이 꺼져 있을 때만 권한 한 장을 끼웁니다.
            TourKind.UPDATE -> buildList {
                add(TourPage.UPDATE_SUMMARY)
                add(TourPage.UPDATE_BOARD)
                add(TourPage.UPDATE_TABS)
                add(TourPage.UPDATE_HOME_STAY)
                add(TourPage.PRIVACY)
                if (!notificationsGranted(context)) add(TourPage.PERMISSIONS)
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
        onDismissRequest = onFinish,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        // 뒤로 가기는 앞 장으로, 첫 장에서는 투어를 닫습니다 (다이얼로그 안에 둬야 다이얼로그 창의 뒤로 가기를 받습니다).
        BackHandler {
            if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } else onFinish()
        }
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
                // 위: 진행 점 + 건너뛰기
                Row(
                    Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TourDots(count = pages.size, current = pagerState.currentPage)
                    Spacer(Modifier.weight(1f))
                    if (page != TourPage.DONE && pagerState.currentPage != pages.lastIndex) {
                        Text(
                            if (page == TourPage.LOGIN) "나중에" else "건너뛰기",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { if (page == TourPage.LOGIN) next() else onFinish() }
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
                            TourPage.HOME -> HomePage()
                            TourPage.APPLY -> ApplyPage()
                            TourPage.BOARD -> BoardPage()
                            TourPage.PRIVACY -> PrivacyPage(beforeLogin = kind == TourKind.WELCOME, onMore = { showingPrivacy = true })
                            TourPage.PERMISSIONS -> PermissionsPage()
                            TourPage.LOGIN -> LoginPage(onReady = { loginAction = it }, onLoggedIn = ::next)
                            TourPage.DONE -> DonePage()
                            TourPage.UPDATE_SUMMARY -> UpdateSummaryPage()
                            TourPage.UPDATE_BOARD -> UpdateBoardPage()
                            TourPage.UPDATE_TABS -> UpdateTabsPage()
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
        eyebrow = "하이하나 Neo",
        title = "지금 어디로 가야 하는지,\n한 화면에서",
        desc = "시간표, 면학 위치, 급식, 게시판, 신청을 하나고 학사시스템과 연결해 한곳에 모았습니다.",
    )
}

@Composable
private fun ColumnScope.HomePage() {
    PageHead("홈", "수업·면학 장소와\n남은 시간을 바로", "지금 있어야 할 곳, 다음 일정, 오늘 급식이 첫 화면에 뜹니다.")
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("지금", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("면학 1타임 · 도서관", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("2층 B-14", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("19:00 – 21:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text("48분 남음", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun ColumnScope.ApplyPage() {
    PageHead("신청·내역", "면학실·도서관 자리를\n배치도에서 바로 신청", "빈 자리를 누르면 신청, 내 자리를 누르면 취소. 심야면학과 교과교실 현황도 여기서 봅니다.")
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
}

@Composable
private fun ColumnScope.BoardPage() {
    PageHead("게시판", "새 공지는 요약과 함께\n알림으로", "글마다 AI 한 줄 요약이 붙고, 가정통신문 이미지도 읽어 요약합니다. 설정에서 끌 수 있습니다.")
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("2학기 교내 수학경시대회 참가 신청 안내", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        AiSummaryPill("10/7(수)까지 포털 신청, 10/14 시청각실", Modifier.padding(top = 9.dp))
    }
    Spacer(Modifier.height(10.dp))
    OneUiCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text("하이하나 Neo · 학생공지 · 방금", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("방과후학교 수강 정산 안내", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text("환불 금액은 마이페이지에서 확인", style = MaterialTheme.typography.bodySmall)
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
    OneUiCard(Modifier.fillMaxWidth()) {
        TourRow(rememberVectorPainter(Icons.Default.Lock), TourBlue, "이 폰에만 암호화해 저장", "안드로이드 키스토어 키로 잠그고, 백업·기기 이전에도 넣지 않습니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_tour_swap), TourGreen, "학교 서버로만 전송", "로그인은 HTTPS로 학사시스템에 직접 합니다. 개발자 서버를 거치지 않습니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_tour_eye_off), TourViolet, "개발자도 볼 수 없습니다", "계정 정보를 받는 개발자 서버가 없어, 비밀번호가 개발자에게 갈 길이 없습니다.")
        RowGap()
        TourRow(painterResource(R.drawable.ic_tour_code), TourSlate, "코드 공개 · 추적 도구 없음", "모든 코드와 빌드 기록을 GitHub에서 누구나 확인할 수 있습니다.")
    }
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
private fun ColumnScope.LoginPage(onReady: (LoginAction?) -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectedId = remember { HanaCredentialStore.memId(context)?.takeIf { HanaCredentialStore.hasCredentials(context) } }
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
                onSuccess = { onLoggedIn() },
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
}

@Composable
private fun ColumnScope.DonePage() {
    Spacer(Modifier.height(48.dp))
    Box(Modifier.size(68.dp).clip(CircleShape).background(TourGreen.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF8FDC6B), modifier = Modifier.size(34.dp))
    }
    PageHead("", "준비됐습니다", "홈 화면에 위젯을 추가하면 앱을 열지 않아도 지금 일정을 볼 수 있습니다. 홈 화면 빈 곳을 길게 누르고 위젯 → 하이하나 Neo를 고르세요.")
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
    var chip by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
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
        ScheduleSubTabs(chip) { chip = it }
    }
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
