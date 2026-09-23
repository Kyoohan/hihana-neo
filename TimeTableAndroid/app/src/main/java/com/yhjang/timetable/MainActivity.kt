package com.yhjang.timetable

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import android.os.Build
import android.graphics.RuntimeShader
import android.graphics.RenderEffect
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.DisposableEffect
import android.content.Context
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.updateAll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import com.yhjang.timetable.ui.AccentColorPickerDialog
import com.yhjang.timetable.ui.AccentPresets
import com.yhjang.timetable.ui.CustomAccentSwatch
import com.yhjang.timetable.ui.LocalHazeState
import com.yhjang.timetable.ui.LocalDialogHazeState
import com.yhjang.timetable.ui.OneUiActionPill
import com.yhjang.timetable.ui.isDark
import com.yhjang.timetable.ui.oneUiGlassSurface
import com.yhjang.timetable.ui.OneUiHeaderExpandedExtra
import com.yhjang.timetable.ui.OneUiCompactBarHeight
import com.yhjang.timetable.ui.floatingPill
import androidx.compose.animation.core.animateFloat
import com.yhjang.timetable.ui.oneUiPageBackground
import com.yhjang.timetable.ui.PageBackgroundStore
import com.yhjang.timetable.ui.LiquidLens
import com.yhjang.timetable.ui.LocalLiquidLight
import com.yhjang.timetable.ui.rememberLiquidLight
import com.yhjang.timetable.ui.liquidFrostStyle
import com.yhjang.timetable.ui.BackgroundCropDialog
import com.yhjang.timetable.ui.LocalPageBackground
import com.yhjang.timetable.ui.rememberPageBackground
import androidx.activity.result.PickVisualMediaRequest
import com.yhjang.timetable.ui.systemAccentColor
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeStyle
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiAlertDialog
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiCollapsingHeader
import com.yhjang.timetable.ui.OneUiDialog
import com.yhjang.timetable.ui.OneUiDialogButton
import com.yhjang.timetable.ui.OneUiDivider
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiGroupColumn
import com.yhjang.timetable.ui.OneUiListItem
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiMetricIcon
import com.yhjang.timetable.ui.OneUiRadio
import com.yhjang.timetable.ui.OneUiRadioRow
import com.yhjang.timetable.ui.OneUiSectionTitle
import com.yhjang.timetable.ui.OneUiSlider
import com.yhjang.timetable.ui.OneUiSwitch
import com.yhjang.timetable.ui.OneUiTextButton
import com.yhjang.timetable.ui.OneUiTextField
import com.yhjang.timetable.ui.TimeTableTheme
import com.yhjang.timetable.ui.rememberOneUiHeaderState
import com.yhjang.timetable.widget.TimeTableWidget
import com.yhjang.timetable.widget.WidgetKindColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    /** 알림 탭으로 들어온 알리미 열기 요청 — 이미 떠 있는 앱에서도 반응하도록 상태로 전달합니다. */
    private val openAlimRequest = mutableStateOf(false)

    /** 위젯 탭으로 들어온 탭 열기 요청 — onCreate/onNewIntent 모두에서 채웁니다. */
    private val openTabRequest = mutableStateOf<String?>(null)

    /** 게시판 알림 탭으로 들어온 요청 — 게시판 카테고리(ordinal)와, 한 건이면 바로 열 글 주소. */
    private val openBoardRequest = mutableStateOf<Int?>(null)
    private val openPostRequest = mutableStateOf<String?>(null)

    /** 백그라운드/외부 브라우저에서 돌아올 때마다 증가 — 포털에서 장소를 바꾼 뒤 복귀 시 재동기화합니다. */
    private val resumeSyncRequest = mutableStateOf(0)

    /** 최초 onResume(앱 시작)은 재동기화 대상이 아니므로 한 번 건너뜁니다. */
    private var hasResumed = false

    override fun onResume() {
        super.onResume()
        if (hasResumed) resumeSyncRequest.value++ else hasResumed = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openAlimRequest.value = intent?.getBooleanExtra(AlimNotifier.EXTRA_OPEN_ALIM, false) ?: false
        openTabRequest.value = intent?.getStringExtra(EXTRA_OPEN_TAB)
        consumeBoardExtras(intent)
        // 한 번 소비한 뒤 제거해, 재구성 시 같은 탭 요청이 다시 적용되지 않게 합니다.
        intent?.removeExtra(EXTRA_OPEN_TAB)
        setContent {
            TimeTableApp(
                openAlimRequest = openAlimRequest,
                openTabRequest = openTabRequest,
                openBoardRequest = openBoardRequest,
                openPostRequest = openPostRequest,
                resumeSyncRequest = resumeSyncRequest,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAlimRequest.value = intent.getBooleanExtra(AlimNotifier.EXTRA_OPEN_ALIM, false)
        openTabRequest.value = intent.getStringExtra(EXTRA_OPEN_TAB)
        consumeBoardExtras(intent)
        intent.removeExtra(EXTRA_OPEN_TAB)
    }

    private fun consumeBoardExtras(intent: Intent?) {
        if (intent == null) return
        if (intent.hasExtra(BoardNotifier.EXTRA_OPEN_BOARD)) {
            openBoardRequest.value = intent.getIntExtra(BoardNotifier.EXTRA_OPEN_BOARD, 0)
            intent.removeExtra(BoardNotifier.EXTRA_OPEN_BOARD)
        }
        intent.getStringExtra(BoardNotifier.EXTRA_OPEN_POST_URL)?.let {
            openPostRequest.value = it
            intent.removeExtra(BoardNotifier.EXTRA_OPEN_POST_URL)
        }
    }

    companion object {
        /** 위젯 탭 → 앱에서 열 탭 지정용 extra 키와 값. 위젯(TimeTableWidget)도 함께 씁니다. */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_TODAY = "today"
        const val TAB_BOARD = "board"
        const val TAB_MEAL = "meal"
        const val TAB_ACADEMIC = "academic"
    }
}

/**
 * 강조 색 상태를 여기서 들고 있어야 설정에서 색을 바꿀 때 테마가 즉시 다시 그려집니다.
 */
@Composable
fun TimeTableApp(
    openAlimRequest: MutableState<Boolean> = mutableStateOf(false),
    openTabRequest: MutableState<String?> = mutableStateOf<String?>(null),
    openBoardRequest: MutableState<Int?> = mutableStateOf<Int?>(null),
    openPostRequest: MutableState<String?> = mutableStateOf<String?>(null),
    resumeSyncRequest: MutableState<Int> = mutableStateOf(0),
) {
    val context = LocalContext.current
    var accentArgb by remember { mutableStateOf(PlanStore.AUTO_ACCENT_COLOR) }
    var appTheme by remember { mutableStateOf(PlanStore.THEME_SYSTEM) }

    LaunchedEffect(Unit) {
        accentArgb = PlanStore.accentColor(context)
        appTheme = PlanStore.appTheme(context)
    }

    val darkTheme = when (appTheme) {
        PlanStore.THEME_DARK -> true
        PlanStore.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    // 앱 테마를 시스템과 다르게(라이트/다크 고정) 골랐을 때 상태바 아이콘 색도 따라가게 합니다 —
    // 안 그러면 시스템 다크 + 앱 라이트 조합에서 흰 아이콘이 밝은 배경에 묻힙니다.
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(darkTheme) {
            val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // 배경 사진 — 파일이 바뀔 때마다 버전이 올라가고, 그 키로 비트맵을 다시 읽어 전체 화면에 제공합니다.
    var backgroundVersion by remember { mutableStateOf(PageBackgroundStore.version(context)) }
    val pageBackground = rememberPageBackground(backgroundVersion)

    TimeTableTheme(accentArgb = accentArgb, darkTheme = darkTheme) {
        CompositionLocalProvider(LocalPageBackground provides pageBackground) {
        TimeTableAppContent(
            accentArgb = accentArgb,
            onAccentChange = { accentArgb = it },
            appTheme = appTheme,
            onAppThemeChange = { appTheme = it },
            hasBackgroundPhoto = pageBackground != null,
            onBackgroundChanged = { backgroundVersion = PageBackgroundStore.version(context) },
            openAlimRequest = openAlimRequest,
            openTabRequest = openTabRequest,
            openBoardRequest = openBoardRequest,
            openPostRequest = openPostRequest,
            resumeSyncRequest = resumeSyncRequest,
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeTableAppContent(
    accentArgb: Int,
    onAccentChange: (Int) -> Unit,
    appTheme: String,
    onAppThemeChange: (String) -> Unit,
    hasBackgroundPhoto: Boolean,
    onBackgroundChanged: () -> Unit,
    openAlimRequest: MutableState<Boolean>,
    openTabRequest: MutableState<String?>,
    openBoardRequest: MutableState<Int?>,
    openPostRequest: MutableState<String?>,
    resumeSyncRequest: MutableState<Int>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var today by remember { mutableStateOf(PlanStore.today()) }
    var places by remember { mutableStateOf<Map<PlanSlot, StudyPlace?>>(emptyMap()) }
    var isSyncing by remember { mutableStateOf(false) }
    // 전체 새로고침(아이콘·당겨서) 진행 중 — 급식·알리미까지 포함하므로 isSyncing 보다 길게 켜져 있습니다.
    var isRefreshingAll by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var editingSlot by remember { mutableStateOf<PlanSlot?>(null) }
    var showingAccountSheet by remember { mutableStateOf(false) }
    // 설치·업데이트 직후 한 번, '알람 및 리마인더' 권한이 없으면 켜 달라고 안내합니다 — 없으면 위젯·실시간 일정의
    // 남은 시간이 절전 중 5~15분씩 늦게 갱신됩니다. 버전마다 한 번만 (allowBackup 과 무관한 일반 SharedPreferences).
    var showingExactAlarmPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("prompts", Context.MODE_PRIVATE)
        val asked = prefs.getInt("exactAlarmPromptVersion", -1)
        if (!ExactAlarmPermission.isGranted(context) && asked != BuildConfig.VERSION_CODE) {
            prefs.edit().putInt("exactAlarmPromptVersion", BuildConfig.VERSION_CODE).apply()
            showingExactAlarmPrompt = true
        }
    }
    // 계정 유무는 화면 상태로 들고 있어야 연동 직후 '지금' 카드·게시판 등이 바로 바뀝니다.
    var hasCredentials by remember { mutableStateOf(HanaCredentialStore.hasCredentials(context)) }
    // 오프라인 표시 — 네트워크 오류로 동기화에 실패하면 다이얼로그 대신 헤더에 조용히 표시하고 캐시로 버팁니다.
    var offline by remember { mutableStateOf(false) }
    var lastSyncAt by remember { mutableStateOf(0L) }
    var showingSettings by remember { mutableStateOf(false) }
    var showingAppInfo by remember { mutableStateOf(false) }
    // 앱 내 업데이트 상태 — 실행 시 한 번(6시간 캐시) 조용히 확인하고, 정보 화면에서 수동 확인/설치합니다.
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val updateAvailable = updateState is UpdateState.Available || updateState is UpdateState.Downloading
    var homeWidgetOpacity by remember { androidx.compose.runtime.mutableFloatStateOf(60f) }
    var homeWidgetTheme by remember { mutableStateOf(PlanStore.THEME_SYSTEM) }
    var homeWidgetAccent by remember { mutableStateOf(PlanStore.WIDGET_ACCENT_KIND) }
    var widgetKindColors by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var studentGrade by remember { mutableStateOf(PlanStore.DEFAULT_STUDENT_GRADE) }
    // 추적할 급식 알레르기 — 설정에서 바꾸면 앱 배지·대시보드·위젯에 함께 반영됩니다.
    var allergyCodes by remember { mutableStateOf(DEFAULT_ALLERGY_CODES) }
    // 1타임 면학감독 교사 — 캐시된 학사일정에서 읽어 "오늘 남은 일정"의 면학실 행에 붙입니다.
    var weekday1Supervisor by remember { mutableStateOf<String?>(null) }
    var isLoadingMeals by remember { mutableStateOf(false) }
    var mealDays by remember { mutableStateOf<Map<String, DayMeals>>(emptyMap()) }
    var mealWeekMode by rememberSaveable { mutableStateOf(false) }
    var selectedMealDay by rememberSaveable { mutableStateOf(PlanStore.dayKey(PlanStore.today())) }
    var tab by rememberSaveable { mutableStateOf(0) }

    // 포털 시간표를 새로 설치할 때마다 증가 — 설치된 표가 아니라 Compose 상태라, 주간 표와
    // 오늘 블록이 이 값으로 다시 그려집니다.
    var timetableRevision by remember { mutableStateOf(0) }

    // 설정을 상단 아이콘으로 옮겨 하단 탭은 홈/시간표/급식/학사 네 개입니다.
    // 디버그 빌드에는 새 기능을 바로 눌러 볼 수 있는 Dev 탭이 하나 더 있습니다.
    val tabTitles = if (BuildConfig.DEBUG) TabIndex.titles + "Dev" else TabIndex.titles
    // 이전 버전 저장 상태(설정=3, 학사=4)가 복원돼도 범위를 벗어나지 않게 보정합니다.
    if (tab !in tabTitles.indices) tab = 0

    // 학사 탭
    // 게시판(1)을 기본으로 — 학사 탭에 들어오면 게시판이 먼저 보입니다.
    // 일정 탭 안의 선택 — 0 시간표, 1 학사일정.
    var scheduleSubTab by rememberSaveable { mutableStateOf(0) }
    var scheduleEntries by remember { mutableStateOf<List<HanaScheduleEntry>>(emptyList()) }
    var scheduleLoading by remember { mutableStateOf(false) }
    var scheduleError by remember { mutableStateOf<String?>(null) }
    var boardCategoryIndex by rememberSaveable { mutableStateOf(0) }
    var boardPosts by remember { mutableStateOf<List<HanaBoardPost>>(emptyList()) }
    var boardLoading by remember { mutableStateOf(false) }
    var boardError by remember { mutableStateOf<String?>(null) }
    // 앱 내 웹뷰로 띄울 페이지 (null 이면 닫힘) — 게시글·신청·내역 페이지 공용
    var webPage by remember { mutableStateOf<HanaWebPage?>(null) }
    var seatService by remember { mutableStateOf<SeatService?>(null) }

    // 알리미
    var alimList by remember { mutableStateOf<List<HanaAlim>>(emptyList()) }
    var alimUnread by remember { mutableStateOf(0) }
    var alimLoading by remember { mutableStateOf(false) }
    var alimError by remember { mutableStateOf<String?>(null) }
    var showingAlim by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    suspend fun reload() {
        today = PlanStore.today()
        places = PlanStore.placesForToday(context, today)
        // 학사일정 캐시는 학사 탭 진입 시 채워집니다 — 여기서는 읽기만 합니다.
        val grade = PlanStore.studentGrade(context)
        weekday1Supervisor = weekday1SupervisionTeachers(
            HanaAcademicRepository.cachedSchedule(context),
            today,
            grade,
        ).joinToString(" ").takeIf { it.isNotEmpty() }
    }

    /** 지정한 날의 급식을 캐시에서 먼저 보여주고, TTL이 지났으면 조용히 갱신합니다. */
    suspend fun loadMealDay(date: LocalDate, force: Boolean) {
        val key = PlanStore.dayKey(date)
        if (force || MealCache.load(context, date, MealCache.TTL_MS) == null) {
            isLoadingMeals = true
            runCatching { HanaMealSync.refresh(context, date) }
            isLoadingMeals = false
        }
        MealCache.load(context, date)?.let { mealDays = mealDays + (key to it) }
    }

    suspend fun loadSchedule(force: Boolean) {
        scheduleLoading = true
        scheduleError = null
        try {
            scheduleEntries = HanaAcademicRepository.schedule(context, force)
            // 방금 받은 일정으로 1타임 감독을 즉시 갱신합니다 (오늘 탭이 다음에 그릴 때 반영).
            weekday1Supervisor = weekday1SupervisionTeachers(
                scheduleEntries,
                today,
                PlanStore.studentGrade(context),
            ).joinToString(" ").takeIf { it.isNotEmpty() }
        } catch (e: HanaPortalException.MissingCredentials) {
            scheduleError = ACADEMIC_NEEDS_LOGIN
        } catch (e: HanaPortalException.LoginFailed) {
            scheduleError = e.message ?: ACADEMIC_NEEDS_LOGIN
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 화면을 떠났거나(다른 앱에 갔다 오기 등) 같은 작업이 새로 시작돼 취소된 것 — 오류가 아닙니다.
            throw e
        } catch (e: Exception) {
            scheduleError = e.message ?: "학사일정을 불러오지 못했습니다"
        } finally {
            scheduleLoading = false
        }
    }

    suspend fun loadBoard(force: Boolean) {
        boardLoading = true
        boardError = null
        try {
            val category = BoardCategory.entries[boardCategoryIndex]
            boardPosts = HanaAcademicRepository.board(context, category, force)
            // 앱에서 목록을 본 것과 새 글 알림은 별개입니다 — 여기서 "본 글"로 기록하지 않아, 앱을 먼저 열었더라도
            // 백그라운드 확인 때 알림이 옵니다.
        } catch (e: HanaPortalException.MissingCredentials) {
            boardError = ACADEMIC_NEEDS_LOGIN
        } catch (e: HanaPortalException.LoginFailed) {
            boardError = e.message ?: ACADEMIC_NEEDS_LOGIN
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 화면을 떠났거나(다른 앱에 갔다 오기 등) 같은 작업이 새로 시작돼 취소된 것 — 오류가 아닙니다.
            throw e
        } catch (e: Exception) {
            boardError = e.message ?: "게시글을 불러오지 못했습니다"
        } finally {
            boardLoading = false
        }
    }

    suspend fun loadAlim(force: Boolean) {
        alimLoading = true
        alimError = null
        try {
            val list = HanaAcademicRepository.alim(context, force)
            // 서버 readYn 이 안 내려와도 사용자가 앱에서 읽은 항목은 읽음으로 반영합니다.
            val readIds = AlimReadStore.read(context)
            alimList = list.map { if (!it.read && it.id in readIds) it.copy(read = true) else it }
            alimUnread = alimList.count { !it.read }
            AlimNotifier.process(context, alimList)
        } catch (e: HanaPortalException.MissingCredentials) {
            alimError = ACADEMIC_NEEDS_LOGIN
        } catch (e: HanaPortalException.LoginFailed) {
            alimError = e.message ?: ACADEMIC_NEEDS_LOGIN
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 화면을 떠났거나(다른 앱에 갔다 오기 등) 같은 작업이 새로 시작돼 취소된 것 — 오류가 아닙니다.
            throw e
        } catch (e: Exception) {
            alimError = e.message ?: "알리미를 불러오지 못했습니다"
        } finally {
            alimLoading = false
        }
    }

    /** 알리미 화면 열기 — 계정이 있으면 최신으로 갱신하고, 없으면 연동 안내를 남깁니다. */
    fun openAlimScreen() {
        showingAlim = true
        if (HanaCredentialStore.hasCredentials(context)) {
            scope.launch { loadAlim(true) }
        } else {
            alimError = ACADEMIC_NEEDS_LOGIN
        }
    }

    /** 알리미 한 건 열기 — 읽음 처리 후 기존 포털 웹으로 이동합니다. */
    fun openAlim(alim: HanaAlim) {
        AlimReadStore.markRead(context, listOf(alim.id))
        alimList = alimList.map { if (it.id == alim.id) it.copy(read = true) else it }
        alimUnread = alimList.count { !it.read }
        openUrl(context, alimUrl(alim.id))
    }

    /** 게시글 열기 — 화면은 즉시 뜨고, 세션 확인·로그인은 웹뷰가 페이지를 읽기 직전에 화면 안에서 합니다. */
    fun openBoardPost(post: HanaBoardPost) {
        webPage = HanaWebPage(post.url, "게시글")
    }

    suspend fun syncEverywhere() {
        reload()
        TimeTableWidget().updateAll(context)
        runCatching { LiveActivity.update(context) }
    }

    LaunchedEffect(Unit) {
        // 캐시된 시간표가 있으면(네트워크 없이, 즉시) 가장 먼저 설치합니다. 이게 늦게 설치되면
        // 그 사이 Timetable.installedWeek 가 비어 있어 "오늘" 히어로 카드가 실제로는 수업·면학
        // 중이어도 "오늘 일정이 모두 끝났습니다 · 편안한 밤 보내세요"로 잘못 보였습니다
        // (블랭크 상태와 로딩 중 상태를 구분 안 하는 화면 로직 + 이 단계가 늦게 끝나는 문제가 겹친 것).
        runCatching { HanaTimetableSync.ensureInstalled(context) }
        // 실시간 일정(Now Bar)이 켜져 있으면 앱을 열 때마다 현재 상태로 맞춥니다 (알람이 밀렸을 때의 보정).
        runCatching { LiveActivity.update(context) }
        timetableRevision++

        // "오늘" 탭에 바로 보이는 핵심 데이터(면학 위치 등)를 그다음으로, 독립적으로 채웁니다.
        // 예전엔 이 아래 단계들과 한 코루틴에 묶여 있어서, 뒤쪽의 시간표 동기화 중 하나가
        // 예외를 던지면 이 줄이 아예 실행되지 못하고 "오늘" 화면이 빈 채로 멈춰 있었습니다 —
        // 각 단계를 독립적으로 감싸 하나가 실패해도 나머지가 이어지게 합니다.
        runCatching { syncEverywhere() }

        homeWidgetOpacity = PlanStore.homeWidgetOpacity(context).toFloat()
        homeWidgetTheme = PlanStore.homeWidgetTheme(context)
        homeWidgetAccent = PlanStore.homeWidgetAccent(context)
        widgetKindColors = PlanStore.widgetKindColors(context)
        studentGrade = PlanStore.studentGrade(context)
        allergyCodes = PlanStore.allergyCodes(context)

        // 급식은 공개 엔드포인트라 로그인 없이도 갱신합니다 — 실패해도 앱 실행을 막지 않습니다.
        runCatching { loadMealDay(PlanStore.today(), force = false) }

        // TTL이 지났으면 렌더 DOM 으로 새로 받아옵니다 (실패 시 캐시 유지) — 느릴 수 있는
        // 네트워크 단계라, 이미 캐시로 화면이 채워진 뒤(위에서) 조용히 뒤따라오게 둡니다.
        runCatching { HanaTimetableSync.refresh(context, force = false) }
            .onSuccess { offline = false; PlanStore.markSynced(context); lastSyncAt = System.currentTimeMillis() }
            .onFailure { if (it is java.io.IOException) offline = true }
        timetableRevision++

        // 알리미는 로그인 계정이 있고 권한이 있을 때만 조용히 확인합니다.
        if (HanaCredentialStore.hasCredentials(context)) {
            runCatching { loadAlim(false) }
        }
    }

    // 앱 실행 시 새 버전을 조용히 확인합니다 — 큰 버전이면(자동 확인은 큰 버전만 돌려줍니다) 실행 시 안내를 한 번 띄웁니다.
    var majorUpdatePrompt by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        lastSyncAt = runCatching { PlanStore.lastSyncAt(context) }.getOrDefault(0L)
        val cached = runCatching { AppUpdater.cached(context) }.getOrNull()
        if (cached != null) updateState = UpdateState.Available(cached)
        val found = runCatching { AppUpdater.check(context) }.getOrNull() ?: cached
        if (found != null) {
            updateState = UpdateState.Available(found)
            if (!UpdatePromptStore.dismissedRecently(context, found.versionName)) majorUpdatePrompt = found
        }
    }


    fun checkUpdateNow() {
        if (!AppUpdater.isConfigured) {
            updateState = UpdateState.Error("이 빌드에는 업데이트 저장소가 설정되어 있지 않습니다.")
            return
        }
        scope.launch {
            updateState = UpdateState.Checking
            updateState = runCatching { AppUpdater.check(context, force = true) }
                .fold(
                    onSuccess = { info -> if (info != null) UpdateState.Available(info) else UpdateState.UpToDate },
                    onFailure = { e -> UpdateState.Error("확인하지 못했습니다: ${e.message ?: "네트워크 오류"}") },
                )
        }
    }

    // 정보 화면의 '업데이트'는 설치 안내를 먼저 띄우고 '확인' 뒤에 실제 설치로 갑니다.
    var installGuideFor by remember { mutableStateOf<UpdateInfo?>(null) }

    fun installUpdate() {
        val info = (updateState as? UpdateState.Available)?.info ?: return
        if (!AppUpdater.canInstall(context)) {
            // '알 수 없는 앱 설치' 허용이 먼저 — 설정에서 켜고 돌아오면 다시 '업데이트'를 누르면 됩니다.
            AppUpdater.openInstallPermission(context)
            return
        }
        scope.launch {
            updateState = UpdateState.Downloading(info, -1f)
            runCatching { AppUpdater.download(context, info) { p -> updateState = UpdateState.Downloading(info, p) } }
                .fold(
                    onSuccess = { file ->
                        updateState = UpdateState.Available(info)
                        AppUpdater.install(context, file)
                    },
                    onFailure = { e -> updateState = UpdateState.Error("다운로드하지 못했습니다: ${e.message ?: "네트워크 오류"}") },
                )
        }
    }

    // API 33+ 알림 권한 — 앱 첫 진입 시 한 번 요청합니다.
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 알림 탭으로 들어온 요청을 소비해 알리미 화면을 엽니다.
    LaunchedEffect(openAlimRequest.value) {
        if (openAlimRequest.value) {
            openAlimRequest.value = false
            showingAlim = true
            if (HanaCredentialStore.hasCredentials(context)) runCatching { loadAlim(true) }
        }
    }

    // 위젯·알림으로 들어온 요청을 소비해 해당 탭을 엽니다.
    LaunchedEffect(openTabRequest.value) {
        when (openTabRequest.value) {
            MainActivity.TAB_MEAL -> tab = TabIndex.MEAL
            // 예전 "학사" 요청(설치돼 있던 알림 등)은 게시판으로.
            MainActivity.TAB_ACADEMIC, MainActivity.TAB_BOARD -> tab = TabIndex.BOARD
            MainActivity.TAB_TODAY -> tab = TabIndex.HOME
        }
        openTabRequest.value = null
    }

    // 게시판 알림 탭 — 해당 게시판 목록으로 가고, 한 건이면 그 글을 바로 엽니다.
    LaunchedEffect(openBoardRequest.value, openPostRequest.value) {
        openBoardRequest.value?.let { ordinal ->
            boardCategoryIndex = ordinal.coerceIn(0, BoardCategory.entries.lastIndex)
            boardPosts = emptyList()
            tab = TabIndex.BOARD
            openBoardRequest.value = null
            runCatching { loadBoard(false) }
        }
        openPostRequest.value?.let { url ->
            openPostRequest.value = null
            webPage = HanaWebPage(url, "게시글")
        }
    }

    // 급식 탭에 들어오거나 주간 보기를 켜면 최신 식단으로 갱신
    LaunchedEffect(tab, mealWeekMode, selectedMealDay) {
        if (tab != TabIndex.MEAL) return@LaunchedEffect
        if (mealWeekMode) {
            val base = PlanStore.today()
            for (offset in 0 until 7) {
                val date = base.plusDays(offset.toLong())
                if (MealCache.load(context, date, MealCache.TTL_MS) == null) {
                    runCatching { HanaMealSync.refresh(context, date) }
                }
                MealCache.load(context, date)?.let { mealDays = mealDays + (PlanStore.dayKey(date) to it) }
            }
        } else {
            loadMealDay(PlanStore.today(), force = false)
        }
    }

    // 게시판·학사일정 탭에 들어올 때 데이터 로드 (신청내역은 섹션 컴포저블이 자체적으로 조회합니다).
    LaunchedEffect(tab, scheduleSubTab) {
        when {
            tab == TabIndex.BOARD -> loadBoard(false)
            tab == TabIndex.SCHEDULE && scheduleSubTab == 1 -> if (scheduleEntries.isEmpty()) loadSchedule(false)
        }
    }

    // 오늘 탭 카드(학사일정·게시판)는 학사 탭과 같은 캐시를 씁니다.
    // 비어 있을 때만 조용히 채우고, TTL 캐시라 대부분 네트워크를 타지 않습니다. 캐시가 "빈 목록"이면
    // (세션이 없을 때 받은 응답일 수 있음) 이번 실행에 한 번은 강제로 새로 받아 옵니다.
    var boardForcedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab != TabIndex.HOME) return@LaunchedEffect
        if (!HanaCredentialStore.hasCredentials(context)) return@LaunchedEffect
        if (scheduleEntries.isEmpty()) runCatching { loadSchedule(false) }
        if (boardPosts.isEmpty()) runCatching { loadBoard(false) }
        if (boardPosts.isEmpty() && !boardForcedOnce) {
            boardForcedOnce = true
            runCatching { loadBoard(true) }
        }
    }

    suspend fun syncFromHana() {
        if (!HanaCredentialStore.hasCredentials(context)) {
            showingAccountSheet = true
            return
        }
        isSyncing = true
        try {
            // 자정을 넘겨 앱이 떠 있을 수 있으니, 항상 "지금" 날짜로 새로 받아옵니다 (캐시 아님).
            val date = PlanStore.today()
            today = date
            val sync = HanaPortalClient.get().fetchDailySync(context, date)
            HanaSyncApplier.apply(context, sync, date)
            // 시간표도 함께, 항상 새로(캐시 아님) 받아옵니다 — 실패해도 예외를 던지지 않습니다.
            HanaTimetableSync.refresh(context, date, force = true)
            timetableRevision++
            syncEverywhere()
            offline = false
            PlanStore.markSynced(context)
            lastSyncAt = System.currentTimeMillis()
        } catch (e: java.io.IOException) {
            // 네트워크가 없거나 서버에 못 닿는 경우 — 오류 창 대신 오프라인 표시로 두고 캐시된 내용을 그대로 씁니다.
            offline = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 화면을 떠났거나(다른 앱에 갔다 오기 등) 같은 작업이 새로 시작돼 취소된 것 — 오류가 아닙니다.
            throw e
        } catch (e: Exception) {
            syncError = e.message ?: "알 수 없는 오류가 발생했습니다"
        } finally {
            isSyncing = false
        }
    }

    /**
     * 심야면학 현황 — 학사시스템 동기화와 따로 돕니다 (한쪽이 실패해도 다른 쪽은 그대로). 로그인해 둔 경우에만.
     * 바뀌면 MidnightSchedule 이 revision 을 올리고 Now Bar·위젯을 다시 그립니다.
     */
    suspend fun syncMidnight() {
        if (!MidnightSessionStore.isLoggedIn(context)) return
        try {
            MidnightSchedule.refresh(context)
            android.util.Log.d("Midnight", "refresh ok")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.d("Midnight", "refresh failed: ${e.message}")
        }
    }

    /** 오른쪽 위 새로고침 아이콘과 아래로 당겨 새로고침이 같이 쓰는 전체 갱신 — 현재 탭의 학사 목록까지 새로 받습니다. */
    suspend fun refreshAll() {
        isRefreshingAll = true
        try {
            loadMealDay(today, force = true)
            // 학사시스템과 심야면학은 서로 독립 — 학사시스템에서 오류가 나도 심야면학은 이어서 확인합니다.
            try {
                syncFromHana()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            syncMidnight()
            loadAlim(true)
            when {
                tab == TabIndex.BOARD -> loadBoard(true)
                tab == TabIndex.SCHEDULE && scheduleSubTab == 1 -> loadSchedule(true)
            }
        } finally {
            isRefreshingAll = false
        }
    }

    // 계정 연동 창을 닫으면 계정 상태를 다시 읽고, 방금 연동됐으면 바로 한 번 동기화합니다.
    LaunchedEffect(showingAccountSheet) {
        if (showingAccountSheet) return@LaunchedEffect
        val now = HanaCredentialStore.hasCredentials(context)
        val linkedNow = now && !hasCredentials
        hasCredentials = now
        if (linkedNow) {
            runCatching { syncFromHana() }
            runCatching { loadSchedule(true) }
            runCatching { loadBoard(true) }
            runCatching { loadAlim(true) }
        }
    }

    // 외부 브라우저/포털에서 신청 후 앱으로 돌아오면 새로 받아 오늘·주·위젯을 갱신합니다.
    LaunchedEffect(resumeSyncRequest.value) {
        if (resumeSyncRequest.value == 0) return@LaunchedEffect
        if (HanaCredentialStore.hasCredentials(context)) {
            loadMealDay(PlanStore.today(), force = true)
            runCatching { syncFromHana() }
        }
        // 학사시스템 계정 유무·동기화 결과와 상관없이 따로.
        syncMidnight()
    }

    val slots = PlanSlot.slots(today)
    // 대시보드의 "남은 시간"이 흐르도록 20초마다 현재 시각을 갱신합니다.
    var now by remember { mutableStateOf(LocalDateTime.now(PlanStore.seoulZone)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now(PlanStore.seoulZone)
        }
    }

    // 시간표가 아직 한 번도 설치되지 않은 "로딩 중" 상태와, 설치된 뒤 정말 일정이 없는(블랭크)
    // 상태를 구분하기 위한 값 — 이게 없으면 로딩 중에도 "오늘 일정이 모두 끝났습니다"라는
    // 잘못된 문구가 떠서, 앱이 멈춘 것처럼(화면이 빈 것처럼) 보이는 원인이 됐습니다.
    val hasTimetable = remember(timetableRevision) { Timetable.fetchedWeek() != null }
    val todayBlocks = remember(today, places, timetableRevision, MidnightSchedule.revision) { Timetable.blocks(today) { places[it] } }
    val currentBlock = todayBlocks.firstOrNull { !it.start.isAfter(now) && now.isBefore(it.end) } ?: todayBlocks.lastOrNull()
    // 쉬는 시간은 다음 일정이 아니므로 수업·면학만 셉니다 (위젯과 같은 규칙).
    val nextBlock = Timetable.nextEvent(todayBlocks, currentBlock, now)
    val upcomingGroups = coalesceUpcoming(todayBlocks.filter { it.start.isAfter(now) && !it.isBlank })
    val remainingMinutes = currentBlock
        ?.takeUnless { it.isBlank }
        ?.let { Duration.between(now, it.end).toMinutes().coerceAtLeast(0) }
    // '지금' 카드 아래 진행바 — 현재 블록 안에서 얼마나 지났는지 (20초 틱마다 갱신).
    val blockProgress = currentBlock?.takeUnless { it.isBlank }?.let { block ->
        val total = Duration.between(block.start, block.end).toMillis()
        if (total <= 0) null else (Duration.between(block.start, now).toMillis().toFloat() / total).coerceIn(0f, 1f)
    }

    // 스크롤 시 가운데 큰 제목이 접히는 One UI 확장 헤더 동작 — 탭을 바꾸면 펼친 상태에서 시작합니다.
    val headerState = rememberOneUiHeaderState()
    // 탭을 바꾸면 그 탭은 항상 맨 위에서 다시 시작합니다 — 헤더를 펼치고, 페이지 내용의 스크롤도 초기화합니다
    // (탭마다 접힘을 기억하는 방식은 스와이프 중 옆 페이지가 먼저 그려지며 꼬여 제목과 목록이 겹쳐 보였습니다).
    // 페이지 스크롤 초기화는 방문 횟수를 key 로 써서 그 페이지의 저장 상태(LazyListState 등)를 새로 만드는 식입니다.
    val pageVisits = remember { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(tab) {
        headerState.expand()
        pageVisits[tab] = (pageVisits[tab] ?: 0) + 1
    }

    val selectedMealDate = remember(selectedMealDay) {
        runCatching { LocalDate.parse(selectedMealDay) }.getOrDefault(PlanStore.today())
    }
    val mealWeekDates = remember(today) { (0 until 7).map { today.plusDays(it.toLong()) } }
    val availableMealDays = mealDays.keys

    // 하단 바는 콘텐츠 위에 떠 있는 알약이라 Scaffold 의 bottomBar 슬롯에 넣지 않고 오버레이로 얹습니다.
    // 대신 각 탭의 스크롤 끝에 바 높이만큼 여백을 줘서 마지막 카드가 바 위까지 올라올 수 있게 합니다.
    // 메인 화면 콘텐츠를 Haze 소스로 캡처해 하단 바·플로팅 아이콘 알약·다이얼로그가 뒤를 흐려 비춥니다.
    val hazeState = remember { HazeState() }

    // 탭 사이 스와이프 — 페이저가 자리를 잡으면 tab 을 따라가고, 하단 바 탭은 페이저를 그 페이지로 넘깁니다.
    val pagerState = rememberPagerState(initialPage = tab) { tabTitles.size }
    // 하단 바로 고른 탭 전환 중엔 캡슐이 페이저 위치를 따라가지 않습니다 — 따라가면 방금 놓은 자리에서
    // 이전 페이지 위치로 튀었다가 돌아오며 떨렸습니다.
    var navDriven by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (tab != page) tab = page
            navDriven = false
        }
    }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab && !pagerState.isScrollInProgress) pagerState.animateScrollToPage(tab)
    }

    // 아래로 당겨 새로고침 상태 — 인디케이터는 따로 그리지 않고, 오른쪽 위 새로고침 아이콘이 당긴 만큼
    // 가운데로 내려와 도는 애니메이션으로 대신합니다 (아이콘이 둘로 보이지 않게).
    val pullState = rememberPullToRefreshState()
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var refreshIconOrigin by remember { mutableStateOf(Offset.Zero) }
    val refreshTravel by animateFloatAsState(
        targetValue = if (isRefreshingAll) 1f else pullState.distanceFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "refreshTravel",
    )

    // 다이얼로그가 헤더·하단 바까지 포함한 화면 전체를 흐려 비추도록 루트를 따로 캡처합니다.
    val dialogHazeState = remember { HazeState() }
    // 하단 바·헤더 섬의 반사광 — 기울기 센서를 여기서 한 번만 켭니다.
    val liquidLight = rememberLiquidLight(enabled = remember { LiquidLens.create() != null })
    CompositionLocalProvider(LocalHazeState provides hazeState, LocalDialogHazeState provides dialogHazeState, LocalLiquidLight provides liquidLight) {
    Box(Modifier.fillMaxSize().oneUiPageBackground().hazeSource(dialogHazeState).onSizeChanged { rootSize = it }) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            OneUiCollapsingHeader(
                state = headerState,
                title = tabTitles[tab],
                subtitle = buildString {
                    append(dateText(today))
                    if (offline) {
                        append(" · 오프라인")
                        if (lastSyncAt > 0L) append(" · ").append(syncTimeText(lastSyncAt)).append(" 동기화")
                    }
                },
            )
        },
    ) { innerPadding ->
        // bottomBar 슬롯이 비어 있으므로 innerPadding 의 아래 값은 시스템 내비게이션 인셋입니다 — 그 위에 바 높이를 더합니다.
        val navBarSpace = innerPadding.calculateBottomPadding() + AppNavBarHeight + AppNavBarMargin * 2 + 8.dp
        // 위쪽은 헤더 높이만큼 — 콘텐츠 상자 자체엔 위 여백을 주지 않아, 스크롤하면 목록이 글래스 툴바 아래로 지나갑니다.
        val tabContentPadding = PaddingValues(
            start = OneUi.PagePadding,
            end = OneUi.PagePadding,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = navBarSpace,
        )
        // 아래로 당겨 새로고침 — 헤더의 nestedScroll 보다 바깥에 둬서, 접힌 헤더가 먼저 다 펼쳐진 뒤에야
        // 남은 당김이 새로고침 인디케이터로 갑니다 (안 그러면 당길 때 헤더가 안 펼쳐지고 인디케이터만 내려옵니다).
        PullToRefreshBox(
            isRefreshing = isRefreshingAll,
            onRefresh = { scope.launch { refreshAll() } },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {},
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(headerState.connection),
        ) {
            // 글래스 요소(하단 바·알약)는 소스 바깥에 둬야 자기 자신을 다시 흐리지 않습니다.
            // 배경(사진·그레인)도 이 소스 안에 그려야 하단 바·섬 유리가 배경 이미지를 비춥니다 — 루트에만 그리면
            // 유리 뒤가 비어 검은 바탕색으로 채워졌습니다.
            Box(Modifier.fillMaxSize().oneUiPageBackground().hazeSource(hazeState)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
            // 이 페이지가 실제로 보이는 탭일 때만 헤더를 건드립니다 — 옆 페이지가 미리 그려질 때 펼치면 안 됩니다.
            val isActivePage = page == tab && !pagerState.isScrollInProgress
            key(pageVisits[page] ?: 0) {
            when (page) {
                TabIndex.MEAL -> MealTab(
                    dayMeals = mealDays[if (mealWeekMode) selectedMealDay else PlanStore.dayKey(today)],
                    isLoading = isLoadingMeals,
                    allergyCodes = allergyCodes,
                    weekMode = mealWeekMode,
                    onToggleWeek = {
                        mealWeekMode = !mealWeekMode
                        // 일간 보기로 돌아오면 주간에서 고른 날짜를 버리고 오늘로 돌아갑니다.
                        if (!mealWeekMode) selectedMealDay = PlanStore.dayKey(PlanStore.today())
                    },
                    weekDates = mealWeekDates,
                    selectedDate = selectedMealDate,
                    availableDays = availableMealDays,
                    onSelectDate = { selectedMealDay = PlanStore.dayKey(it) },
                    contentPadding = tabContentPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                TabIndex.DEV -> DevTab(
                    contentPadding = tabContentPadding,
                    onShowExactAlarmPrompt = { showingExactAlarmPrompt = true },
                    onOpenSeats = { seatService = it },
                    onOpenSettings = { showingSettings = true },
                    onOpenAccount = { showingAccountSheet = true },
                    onRefreshLive = { scope.launch { runCatching { LiveActivity.update(context) } } },
                )
                TabIndex.APPLY -> AcademicTab(
                    subTab = 2,
                    schedule = scheduleEntries,
                    scheduleLoading = scheduleLoading,
                    scheduleError = scheduleError,
                    onRetrySchedule = { scope.launch { loadSchedule(true) } },
                    studentGrade = studentGrade,
                    boardCategoryIndex = boardCategoryIndex,
                    onBoardCategoryChange = { index ->
                        boardCategoryIndex = index
                        boardPosts = emptyList()
                        scope.launch { loadBoard(false) }
                    },
                    boardPosts = boardPosts,
                    boardLoading = boardLoading,
                    boardError = boardError,
                    onRetryBoard = { scope.launch { loadBoard(true) } },
                    onOpenPost = { openBoardPost(it) },
                    onOpenWeb = { url, title -> webPage = HanaWebPage(url, title, resyncOnClose = true) },
                    onOpenSeats = { seatService = it },
                    onOpenAccount = { showingAccountSheet = true },
                    contentPadding = tabContentPadding,
                    headerCollapsedBy = with(LocalDensity.current) { (-headerState.offsetPx).toDp() },
                    onFitsWithoutScroll = { if (isActivePage) headerState.expand() },
                    modifier = Modifier.fillMaxSize(),
                )
                TabIndex.BOARD -> AcademicTab(
                    subTab = 1,
                    schedule = scheduleEntries,
                    scheduleLoading = scheduleLoading,
                    scheduleError = scheduleError,
                    onRetrySchedule = { scope.launch { loadSchedule(true) } },
                    studentGrade = studentGrade,
                    boardCategoryIndex = boardCategoryIndex,
                    onBoardCategoryChange = { index ->
                        boardCategoryIndex = index
                        boardPosts = emptyList()
                        scope.launch { loadBoard(false) }
                    },
                    boardPosts = boardPosts,
                    boardLoading = boardLoading,
                    boardError = boardError,
                    onRetryBoard = { scope.launch { loadBoard(true) } },
                    onOpenPost = { openBoardPost(it) },
                    onOpenWeb = { url, title -> webPage = HanaWebPage(url, title, resyncOnClose = true) },
                    onOpenSeats = { seatService = it },
                    onOpenAccount = { showingAccountSheet = true },
                    contentPadding = tabContentPadding,
                    headerCollapsedBy = with(LocalDensity.current) { (-headerState.offsetPx).toDp() },
                    onFitsWithoutScroll = { if (isActivePage) headerState.expand() },
                    modifier = Modifier.fillMaxSize(),
                )
                // 주간 시간표는 표준 카드 안에 담습니다. 포털에서 받은 표가 없으면 빈 격자 대신 안내를 띄웁니다.
                TabIndex.SCHEDULE -> if (scheduleSubTab == 1) {
                    AcademicTab(
                        subTab = 0,
                        topContent = { ScheduleSubTabs(scheduleSubTab) { scheduleSubTab = it } },
                        schedule = scheduleEntries,
                        scheduleLoading = scheduleLoading,
                        scheduleError = scheduleError,
                        onRetrySchedule = { scope.launch { loadSchedule(true) } },
                        studentGrade = studentGrade,
                        boardCategoryIndex = boardCategoryIndex,
                        onBoardCategoryChange = {},
                        boardPosts = boardPosts,
                        boardLoading = boardLoading,
                        boardError = boardError,
                        onRetryBoard = {},
                        onOpenPost = { openBoardPost(it) },
                        onOpenWeb = { url, title -> webPage = HanaWebPage(url, title, resyncOnClose = true) },
                        onOpenSeats = { seatService = it },
                        onOpenAccount = { showingAccountSheet = true },
                        contentPadding = tabContentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else BoxWithConstraints(Modifier.fillMaxSize()) {
                    val timetableInstalled = remember(timetableRevision) { Timetable.fetchedWeek() != null }
                    // 표가 화면에 다 들어가면 스크롤(그리고 그에 딸린 헤더 접힘)을 아예 두지 않습니다 — 행 높이를
                    // 남는 높이에 맞춰 56~78dp 사이로 정하고, 그래도 넘칠 때만 스크롤을 붙입니다.
                    val periodRows = remember(timetableRevision) { Timetable.activePeriodTimes.size + 1 }
                    // 위의 시간표/학사일정 칩 줄 높이만큼도 뺍니다.
                    val cardChrome = 16.dp + ScheduleSubTabsHeight
                    // 헤더가 접혀 있으면 maxHeight 가 그만큼 커지는데, 그걸 기준으로 "다 들어간다"고 판단하면 스크롤이
                    // 사라지면서 헤더를 다시 펼칠 방법이 없어집니다 — 헤더가 펼쳐진 상태의 높이로 계산합니다.
                    val collapsedBy = with(LocalDensity.current) { (-headerState.offsetPx).toDp() }
                    val available = maxHeight - collapsedBy - tabContentPadding.calculateTopPadding() - tabContentPadding.calculateBottomPadding() - cardChrome
                    val rowHeight = (available / periodRows).coerceIn(56.dp, 78.dp)
                    val fits = !timetableInstalled || rowHeight * periodRows <= available
                    // 스크롤할 게 없는데 헤더가 접혀 있으면(다른 탭에서 넘어온 경우) 펼쳐 둡니다.
                    LaunchedEffect(fits, isActivePage) { if (fits && isActivePage) headerState.expand() }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState()))
                            .padding(tabContentPadding),
                    ) {
                        ScheduleSubTabs(scheduleSubTab) { scheduleSubTab = it }
                        OneUiCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(if (timetableInstalled) 8.dp else 24.dp),
                        ) {
                            if (timetableInstalled) {
                                WeekTimetable(today, timetableRevision, rowHeight = rowHeight)
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "시간표를 불러오지 못했습니다",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "학사시스템 연동이 필요합니다",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    OneUiButton(
                                        text = "다시 시도",
                                        onClick = {
                                            scope.launch {
                                                isSyncing = true
                                                runCatching { HanaTimetableSync.refresh(context, force = true) }
                                                timetableRevision++
                                                syncEverywhere()
                                                isSyncing = false
                                            }
                                        },
                                        enabled = !isSyncing,
                                        leading = if (isSyncing) {
                                            { OneUiLoading(size = 16.dp, stroke = 2.dp, color = LocalContentColor.current) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                else -> TodayDashboard(
                    today = today,
                    hasTimetable = hasTimetable,
                    currentBlock = currentBlock,
                    remainingMinutes = remainingMinutes,
                    nextTitle = nextBlock?.title,
                    nextRoom = nextBlock?.room,
                    blockProgress = blockProgress,
                    slots = slots,
                    places = places,
                    supervisor = weekday1Supervisor,
                    upcomingGroups = upcomingGroups,
                    meals = mealDays[PlanStore.dayKey(today)],
                    allergyCodes = allergyCodes,
                    isLoadingMeals = isLoadingMeals,
                    alims = alimList,
                    alimUnread = alimUnread,
                    isLoadingAlim = alimLoading,
                    schedule = scheduleEntries,
                    isLoadingSchedule = scheduleLoading,
                    boardPosts = boardPosts,
                    isLoadingBoard = boardLoading,
                    boardCategoryLabel = BoardCategory.entries
                        .getOrElse(boardCategoryIndex) { BoardCategory.STUDENT_NOTICE }
                        .label,
                    studentGrade = studentGrade,
                    needsAccount = !hasCredentials,
                    onConnectAccount = { showingAccountSheet = true },
                    onOpenAlim = { openAlim(it) },
                    onOpenAlimList = { openAlimScreen() },
                    onOpenPost = { openBoardPost(it) },
                    onOpenBoardList = { tab = TabIndex.BOARD },
                    onOpenSchedule = {
                        scheduleSubTab = 1
                        tab = TabIndex.SCHEDULE
                    },
                    onNavigateToTab = { tab = it },
                    contentPadding = tabContentPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            }
            }
            // 포털 시간표 페이지를 JS로 렌더링해 DOM을 읽어올 숨은 WebView — 모든 탭에서 동작하도록 앱 루트에 둡니다.
            HanaTimetableWebViewHost()
            }
            AppNavBar(
                selected = tab,
                onSelect = {
                    if (it != tab) navDriven = true
                    tab = it
                },
                // 스와이프 중엔 캡슐이 페이지 위치를 그대로 따라갑니다.
                pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                following = pagerState.isScrollInProgress && !navDriven,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        }
    }
    // 오른쪽 위 액션 아이콘 — 헤더가 펼쳐져 있을 땐 배경 없이, 접히면 콘텐츠 위에 뜬 글래스 알약이 됩니다.
    OneUiActionPill(
        pillAlpha = ((headerState.fraction - 0.3f) / 0.7f).coerceIn(0f, 1f),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 4.dp, end = 12.dp),
    ) {
        IconButton(
            onClick = { scope.launch { refreshAll() } },
            enabled = !isSyncing && !isRefreshingAll,
            modifier = Modifier.onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                refreshIconOrigin = Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f)
            },
        ) {
            // 전체 새로고침 중엔 아이콘이 가운데로 내려가 있으므로 여기 자리는 비워 둡니다.
            when {
                isRefreshingAll || refreshTravel > 0.01f -> Spacer(Modifier.size(24.dp))
                isSyncing -> OneUiLoading(size = 20.dp, stroke = 2.dp)
                else -> Icon(Icons.Default.Refresh, contentDescription = "동기화")
            }
        }
        IconButton(onClick = { openAlimScreen() }) {
            BadgedBox(badge = {
                if (alimUnread > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text(if (alimUnread > 99) "99+" else "$alimUnread") }
                }
            }) {
                Icon(Icons.Default.Notifications, contentDescription = "알리미")
            }
        }
        IconButton(onClick = { showingAccountSheet = true }) {
            Icon(Icons.Default.AccountCircle, contentDescription = "계정")
        }
        IconButton(onClick = { showingSettings = true }) {
            Icon(Icons.Default.Settings, contentDescription = "설정")
        }
    }
    // 알약에서 떠난 새로고침 아이콘 — 당긴 만큼 알약 자리에서 화면 가운데(펼친 헤더 아래)로 내려오고,
    // 새로고침 중에는 거기서 돌다가 끝나면 다시 알약으로 돌아갑니다.
    if (refreshTravel > 0.005f) {
        val density = LocalDensity.current
        val statusTop = WindowInsets.statusBars.getTop(density)
        val targetX = rootSize.width / 2f
        val targetY = statusTop + with(density) { (OneUiCompactBarHeight + OneUiHeaderExpandedExtra + 28.dp).toPx() }
        val cx = refreshIconOrigin.x + (targetX - refreshIconOrigin.x) * refreshTravel
        val cy = refreshIconOrigin.y + (targetY - refreshIconOrigin.y) * refreshTravel
        val spin by rememberInfiniteTransition(label = "refreshSpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "refreshSpinAngle",
        )
        val halfPx = with(density) { 20.dp.toPx() }
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = cx - halfPx
                    translationY = cy - halfPx
                    // 알약 자리에선 투명 배경, 내려올수록 글래스 원이 드러납니다.
                    alpha = 1f
                }
                .size(40.dp)
                .oneUiGlassSurface(CircleShape, alpha = refreshTravel, container = MaterialTheme.colorScheme.floatingPill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (isRefreshingAll) spin else refreshTravel * 300f
                },
            )
        }
    }
    }

    editingSlot?.let { slot ->
        PlaceEditorDialog(
            slot = slot,
            initial = places[slot],
            onDismiss = { editingSlot = null },
            onSave = { place ->
                scope.launch {
                    PlanStore.set(context, place, slot, today)
                    syncEverywhere()
                    editingSlot = null
                }
            },
        )
    }

    if (showingAccountSheet) {
        HanaAccountDialog(onDismiss = { showingAccountSheet = false })
    }

    majorUpdatePrompt?.let { info ->
        MajorUpdateDialog(
            info = info,
            onLater = {
                UpdatePromptStore.dismiss(context, info.versionName)
                majorUpdatePrompt = null
            },
            onUpdate = {
                majorUpdatePrompt = null
                showingAppInfo = true
                installUpdate()
            },
        )
    }

    if (showingAlim) {
        AlimScreen(
            alims = alimList,
            isLoading = alimLoading,
            error = alimError,
            onRetry = { scope.launch { loadAlim(true) } },
            onOpenAlim = { alim ->
                // 탭 즉시 읽음 처리하고 배지를 갱신한 뒤, 기존처럼 포털 웹으로 엽니다.
                openAlim(alim)
            },
            onMarkAllRead = {
                AlimReadStore.markRead(context, alimList.map { it.id })
                alimList = alimList.map { it.copy(read = true) }
                alimUnread = 0
            },
            onOpenAccount = {
                showingAlim = false
                showingAccountSheet = true
            },
            onDismiss = { showingAlim = false },
        )
    }

    if (showingExactAlarmPrompt) {
        OneUiDialog(
            onDismissRequest = { showingExactAlarmPrompt = false },
            title = "알람 및 리마인더 권한",
            buttons = listOf(
                OneUiDialogButton("나중에", { showingExactAlarmPrompt = false }),
                OneUiDialogButton("허용하기", {
                    showingExactAlarmPrompt = false
                    ExactAlarmPermission.openSettings(context)
                }),
            ),
        ) {
            Text(
                "위젯과 Now Bar 실시간 일정의 남은 시간을 제때 갱신하려면 이 권한이 필요합니다. 없으면 절전 중에 " +
                    "5~15분씩 늦게 바뀔 수 있습니다. 설정 화면에서 '하이하나 Neo'를 허용해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    seatService?.let { service ->
        LibraryApplyScreen(
            service = service,
            onDismiss = { seatService = null },
            // 자리를 잡거나 취소하면 오늘·주·위젯의 면학 위치를 바로 새로 받습니다.
            onChanged = { scope.launch { syncFromHana() } },
        )
    }

    webPage?.let { page ->
        BoardDetailScreen(
            url = page.url,
            title = page.title,
            onDismiss = {
                webPage = null
                // 신청·내역 페이지를 닫으면 포털 배정을 새로 받아 오늘·주·위젯을 즉시 갱신합니다.
                if (page.resyncOnClose) scope.launch { syncFromHana() }
            },
            onOpenBrowser = {
                openUrl(context, page.url)
                webPage = null
            },
        )
    }

    // 위젯을 길게 눌러 여는 설정 화면(WidgetConfigActivity)이 같은 값을 바꿀 수 있으므로, 설정을 열 때마다 다시 읽습니다.
    LaunchedEffect(showingSettings) {
        if (!showingSettings) return@LaunchedEffect
        homeWidgetOpacity = PlanStore.homeWidgetOpacity(context).toFloat()
        homeWidgetTheme = PlanStore.homeWidgetTheme(context)
        homeWidgetAccent = PlanStore.homeWidgetAccent(context)
        widgetKindColors = PlanStore.widgetKindColors(context)
    }

    if (showingSettings) {
        SettingsScreen(
            accentArgb = accentArgb,
            onAccentChange = { argb ->
                onAccentChange(argb)
                scope.launch { PlanStore.setAccentColor(context, argb) }
            },
            appTheme = appTheme,
            onAppThemeChange = { option ->
                onAppThemeChange(option)
                scope.launch { PlanStore.setAppTheme(context, option) }
            },
            hasBackgroundPhoto = hasBackgroundPhoto,
            onBackgroundChanged = onBackgroundChanged,
            studentGrade = studentGrade,
            onStudentGradeChange = { grade ->
                studentGrade = grade
                scope.launch {
                    PlanStore.setStudentGrade(context, grade)
                    // 학년이 바뀌면 오늘 탭 감독 줄과 위젯을 새 학년 기준으로 다시 계산합니다.
                    syncEverywhere()
                }
            },
            allergyCodes = allergyCodes,
            onAllergyCodesChange = { codes ->
                allergyCodes = codes
                scope.launch {
                    PlanStore.setAllergyCodes(context, codes)
                    syncEverywhere()
                }
            },
            widgetTheme = homeWidgetTheme,
            widgetAccent = homeWidgetAccent,
            widgetKindColors = widgetKindColors,
            onWidgetKindColorChange = { kind, argb ->
                widgetKindColors = if (argb == null) widgetKindColors - kind else widgetKindColors + (kind to argb)
                scope.launch {
                    PlanStore.setWidgetKindColor(context, kind, argb)
                    syncEverywhere()
                }
            },
            onWidgetKindColorsReset = {
                widgetKindColors = emptyMap()
                scope.launch {
                    PlanStore.resetWidgetKindColors(context)
                    syncEverywhere()
                }
            },
            widgetOpacity = homeWidgetOpacity,
            onWidgetAccentChange = { option ->
                homeWidgetAccent = option
                scope.launch {
                    PlanStore.setHomeWidgetAccent(context, option)
                    syncEverywhere()
                }
            },
            onWidgetThemeChange = { option ->
                homeWidgetTheme = option
                scope.launch {
                    PlanStore.setHomeWidgetTheme(context, option)
                    syncEverywhere()
                }
            },
            onWidgetOpacityChange = { value ->
                homeWidgetOpacity = value
                scope.launch {
                    PlanStore.setHomeWidgetOpacity(context, value.toInt())
                    syncEverywhere()
                }
            },
            onOpenAccount = {
                showingSettings = false
                showingAccountSheet = true
            },
            updateAvailable = updateAvailable,
            onOpenAppInfo = { showingAppInfo = true },
            onDismiss = { showingSettings = false },
        )
    }

    if (showingAppInfo) {
        AppInfoScreen(
            updateState = updateState,
            onCheckUpdate = { checkUpdateNow() },
            onInstallUpdate = { installGuideFor = (updateState as? UpdateState.Available)?.info },
            onDismiss = { showingAppInfo = false },
        )
    }

    installGuideFor?.let { info ->
        InstallGuideDialog(
            info = info,
            onCancel = { installGuideFor = null },
            onConfirm = {
                installGuideFor = null
                installUpdate()
            },
        )
    }

    syncError?.let { message ->
        OneUiAlertDialog(
            onDismissRequest = { syncError = null },
            title = "동기화 실패",
            message = message,
        )
    }
    } // CompositionLocalProvider(LocalHazeState) — 다이얼로그도 글래스 상태를 받도록 여기까지 감쌉니다.
}

/** "15:32" — 오프라인 표시에 붙는 마지막 동기화 시각. 오늘이 아니면 "9/17 15:32". */
private fun syncTimeText(atMillis: Long): String {
    val at = java.time.Instant.ofEpochMilli(atMillis).atZone(PlanStore.seoulZone).toLocalDateTime()
    val time = "%02d:%02d".format(at.hour, at.minute)
    return if (at.toLocalDate() == PlanStore.today()) time else "${at.monthValue}/${at.dayOfMonth} $time"
}

private fun dateText(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.KOREAN)
    return "${date.monthValue}월 ${date.dayOfMonth}일 $dow"
}

/** 포털 상세 페이지를 기본 브라우저(또는 Custom Tab 처리 앱)로 엽니다. */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * 설정 전체 화면 — 하단 탭 대신 상단 톱니 아이콘에서 열립니다.
 * One UI 설정처럼 강조색 소제목 아래 흰 그룹 컨테이너를 쌓고, 선택은 칩·라디오·슬라이더로 받습니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    accentArgb: Int,
    onAccentChange: (Int) -> Unit,
    appTheme: String,
    onAppThemeChange: (String) -> Unit,
    hasBackgroundPhoto: Boolean,
    onBackgroundChanged: () -> Unit,
    studentGrade: Int,
    onStudentGradeChange: (Int) -> Unit,
    allergyCodes: Set<Int>,
    onAllergyCodesChange: (Set<Int>) -> Unit,
    widgetTheme: String,
    widgetAccent: String,
    widgetKindColors: Map<String, Int>,
    onWidgetKindColorChange: (String, Int?) -> Unit,
    onWidgetKindColorsReset: () -> Unit,
    widgetOpacity: Float,
    onWidgetThemeChange: (String) -> Unit,
    onWidgetAccentChange: (String) -> Unit,
    onWidgetOpacityChange: (Float) -> Unit,
    onOpenAccount: () -> Unit,
    updateAvailable: Boolean,
    onOpenAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected = remember { HanaCredentialStore.hasCredentials(context) }
    // 설정에서 돌아올 때 권한 상태가 바뀌었을 수 있어 매 그리기마다 다시 읽습니다 (가벼운 시스템 조회).
    val exactAlarmGranted = ExactAlarmPermission.isGranted(context)

    OneUiFullScreen(title = "설정", onDismiss = onDismiss) { toolbar ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OneUi.PagePadding,
                end = OneUi.PagePadding,
                top = toolbar.calculateTopPadding() + 4.dp,
                bottom = toolbar.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item {
                OneUiSectionTitle("화면")
                OneUiGroupColumn {
                    AccentPickerRow(selectedArgb = accentArgb, onSelect = onAccentChange)
                    OneUiDivider()
                    PlanStore.themes.forEach { option ->
                        OneUiRadioRow(
                            selected = appTheme == option,
                            label = PlanStore.themeLabel(option),
                            onClick = { onAppThemeChange(option) },
                        )
                    }
                    OneUiDivider()
                    // 배경 사진 — 시스템 사진 선택기로 한 장 고르면 줄여서 앱 안에 저장합니다.
                    // 고른 뒤에는 자르기 화면에서 위치·크기를 맞추고 저장합니다 (EXIF 회전도 여기서 바로잡음).
                    var cropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        if (uri != null) {
                            scope.launch { cropSource = PageBackgroundStore.load(context, uri) }
                        }
                    }
                    cropSource?.let { source ->
                        BackgroundCropDialog(
                            bitmap = source,
                            onDismiss = { cropSource = null },
                            onApply = { cropped ->
                                cropSource = null
                                scope.launch {
                                    if (PageBackgroundStore.saveBitmap(context, cropped)) onBackgroundChanged()
                                }
                            },
                        )
                    }
                    OneUiListItem(
                        title = "배경 이미지",
                        subtitle = if (hasBackgroundPhoto) "사진" else "기본",
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    if (hasBackgroundPhoto) {
                        OneUiDivider()
                        OneUiListItem(
                            title = "기본 배경으로 되돌리기",
                            onClick = {
                                PageBackgroundStore.clear(context)
                                onBackgroundChanged()
                            },
                        )
                    }
                }
            }

            item {
                OneUiSectionTitle("학사")
                OneUiGroupColumn {
                    OneUiListItem(
                        title = "학사시스템 연동",
                        subtitle = if (connected) "하이하나 계정으로 로그인됨" else "로그인이 필요합니다",
                        leading = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = onOpenAccount,
                    )
                    OneUiDivider()
                    Column(Modifier.padding(horizontal = OneUi.RowPadding, vertical = 14.dp)) {
                        Text("학년", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..3).forEach { grade ->
                                OneUiChip(
                                    selected = studentGrade == grade,
                                    onClick = { onStudentGradeChange(grade) },
                                    label = "$grade 학년",
                                )
                            }
                        }
                    }
                }
            }

            item {
                OneUiSectionTitle("위젯")
                OneUiGroupColumn {
                    WidgetSettingsSection(
                        theme = widgetTheme,
                        accent = widgetAccent,
                        kindColors = widgetKindColors,
                        onKindColorChange = onWidgetKindColorChange,
                        onKindColorsReset = onWidgetKindColorsReset,
                        opacity = widgetOpacity,
                        onThemeChange = onWidgetThemeChange,
                        onAccentChange = onWidgetAccentChange,
                        onOpacityChange = onWidgetOpacityChange,
                    )
                    if (!exactAlarmGranted) {
                        OneUiDivider()
                        OneUiListItem(
                            title = "정확한 시각에 갱신",
                            subtitle = "수업·면학이 바뀌는 순간 위젯을 바로 갱신하려면 '알람 및 리마인더' 권한이 필요합니다. 탭해서 허용하세요.",
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            badgeDot = true,
                            onClick = { ExactAlarmPermission.openSettings(context) },
                        )
                    }
                }
            }

            item {
                OneUiSectionTitle("실시간 일정")
                // Now Bar(Android 16 실시간 업데이트) — 평일 아침시간~2타임, 주말 1~4타임 동안 지금 구간과 남은 시간.
                var liveOn by remember { mutableStateOf(LiveActivity.isEnabled(context)) }
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    LiveActivity.setEnabled(context, granted)
                    liveOn = granted
                }
                fun setLive(on: Boolean) {
                    if (on && Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        return
                    }
                    LiveActivity.setEnabled(context, on)
                    liveOn = on
                }
                // 켜져 있는데 시스템이 실시간 알림을 막고 있으면(알림 꺼짐 / Android 16 실시간 정보 거부) 안내 줄을 보여주고,
                // 누르면 이 앱의 알림 설정으로 보냅니다. 설정에서 돌아올 때(ON_RESUME) 다시 확인합니다.
                var liveBlocked by remember { mutableStateOf(LiveActivity.blockedReason(context)) }
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, liveOn) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) liveBlocked = LiveActivity.blockedReason(context)
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                OneUiGroupColumn {
                    OneUiListItem(
                        title = "Now Bar에 지금 일정 표시",
                        subtitle = "평일은 아침시간부터 2타임까지, 주말은 1타임부터 4타임까지 남은 시간을 실시간으로",
                        trailing = { OneUiSwitch(checked = liveOn, onCheckedChange = { setLive(it) }) },
                        onClick = { setLive(!liveOn) },
                    )
                    val reason = liveBlocked
                    if (liveOn && reason != null) {
                        OneUiDivider()
                        OneUiListItem(
                            title = reason.title,
                            subtitle = reason.steps,
                            titleColor = MaterialTheme.colorScheme.error,
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { LiveActivity.openNotificationSettings(context) },
                        )
                    }
                }
            }

            item {
                OneUiSectionTitle("알림")
                // 게시판별 새 글 알림 — 켜진 게시판은 30분 주기 동기화 때 확인해 새 글만 알립니다.
                var boardNotify by remember { mutableStateOf(BoardNotifier.enabledCategories(context)) }
                OneUiGroupColumn {
                    BoardCategory.entries.forEachIndexed { index, category ->
                        OneUiListItem(
                            title = category.label,
                            trailing = {
                                OneUiSwitch(
                                    checked = category in boardNotify,
                                    onCheckedChange = { on ->
                                        BoardNotifier.setEnabled(context, category, on)
                                        boardNotify = BoardNotifier.enabledCategories(context)
                                    },
                                )
                            },
                            onClick = {
                                val on = category !in boardNotify
                                BoardNotifier.setEnabled(context, category, on)
                                boardNotify = BoardNotifier.enabledCategories(context)
                            },
                        )
                        if (index != BoardCategory.entries.lastIndex) OneUiDivider()
                    }
                }
            }

            item {
                OneUiSectionTitle("급식")
                OneUiGroupColumn {
                    Column(Modifier.padding(horizontal = OneUi.RowPadding, vertical = 14.dp)) {
                        Text("알레르기", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ALLERGY_LEGEND.forEach { (code, name) ->
                                OneUiChip(
                                    selected = code in allergyCodes,
                                    onClick = {
                                        onAllergyCodesChange(
                                            if (code in allergyCodes) allergyCodes - code
                                            else allergyCodes + code,
                                        )
                                    },
                                    label = name,
                                )
                            }
                        }
                    }
                }
            }

            item {
                OneUiSectionTitle("정보")
                OneUiGroupColumn {
                    // 갤러리 설정의 "갤러리 정보•" — 버전만 적고, 상세(업데이트·변경 사항)는 정보 화면에서.
                    OneUiListItem(
                        title = "${context.getString(R.string.app_name)} 정보",
                        subtitle = "버전 ${BuildConfig.VERSION_NAME}",
                        badgeDot = updateAvailable,
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = onOpenAppInfo,
                    )
                }
            }
        }
    }
}

/**
 * 하단 내비게이션 — 화면 가장자리와 여백을 두고 뜬 알약 모양 바. 선택된 탭의 아이콘만
 * 바 위로 살짝 올라온 동그란 배지로 떠오르고, 나머지는 옅은 색 아이콘+라벨로 가라앉습니다.
 */
/** 하단 바 자체 높이와 화면 가장자리 여백 — 콘텐츠 하단 여백 계산에 씁니다. 시각 값은 바꾸지 않습니다. */
private val AppNavBarHeight = 64.dp
private val AppNavBarMargin = 10.dp

@Composable
private fun AppNavBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagePosition: Float = selected.toFloat(),
    following: Boolean = false,
) {
    // 시스템 설정이 아니라 앱에 적용된 테마를 따라야, 앱을 라이트로 고정했을 때 바만 어둡게 남지 않습니다.
    val isDark = MaterialTheme.colorScheme.isDark
    val barColor = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    val selectedCapsuleColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f)
    val activeTint = if (isDark) Color.White else Color(0xFF1A1A1C)
    val inactiveTint = if (isDark) Color(0xFFA3A3AD) else Color(0xFF8E8E93)

    val labels = if (BuildConfig.DEBUG) TabIndex.titles + "Dev" else TabIndex.titles
    val count = labels.size

    // 선택 캡슐은 항목들 뒤에 따로 두고, 탭하거나 옆으로 끌면 그 자리로 미끄러집니다 (삼성 헬스와 같은 동작).
    // dragIndex 는 끄는 동안의 실수 위치(칸 단위) — 끌기 시작할 땐 지금 자리에서 출발해 손가락 이동량만큼만 따라오고,
    // 놓으면 가장 가까운 칸으로 스프링 스냅합니다.
    var dragIndex by remember { mutableStateOf<Float?>(null) }
    var pendingIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected) {
        if (pendingIndex != null && pendingIndex == selected) {
            pendingIndex = null
            dragIndex = null
        }
    }
    val dragging = dragIndex != null && pendingIndex == null
    // pointerInput 람다는 처음 시작할 때의 값을 붙잡고 있어서, 최신 selected/onSelect 를 여기로 받아 씁니다.
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val capsuleIndex by animateFloatAsState(
        targetValue = dragIndex ?: if (following) pagePosition else selected.toFloat(),
        animationSpec = if (dragging || following) spring(dampingRatio = 1f, stiffness = 1600f) else spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "navCapsuleIndex",
    )
    // 끄는 동안 액체 유리 방울처럼 살짝 늘어나고, 굴절·림 하이라이트가 세집니다. 시작/끝 모두 부드러운 스프링.
    val liquid by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 220f),
        label = "navLiquid",
    )
    val stretchX = 1f + 0.05f * liquid
    val squashY = 1f - 0.03f * liquid

    // 액체 유리 — 바는 뒤 화면을 크게 흐린 서리 유리, 선택 캡슐은 살짝만 흐린 뒤 화면을 AGSL 렌즈로 굴절·확대해 그립니다
    // (liquidGL 의 refraction/bevel/frost/specular). 뒤 화면은 Haze 가 가져오고, 그 위에 렌즈 셰이더를 얹습니다.
    val hazeState = LocalHazeState.current
    val lensShader = remember { LiquidLens.create() }
    val liquidEnabled = lensShader != null && hazeState != null
    val light = LocalLiquidLight.current
    val density = LocalDensity.current
    val barShape = RoundedCornerShape(32.dp)
    val frostStyle = liquidFrostStyle(isDark)
    // 캡슐 속 뒤 화면 — 바와 같은 흐림에 살짝 더 밝은 틴트.
    val capsuleFrostStyle = HazeStyle(
        backgroundColor = if (isDark) Color(0xFF101214) else Color(0xFFF4F5F7),
        tints = listOf(HazeTint(Color.White.copy(alpha = if (isDark) 0.03f else 0.10f))),
        blurRadius = 26.dp,
        noiseFactor = 0.04f,
    )

    Surface(
        shape = barShape,
        color = Color.Transparent,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            // 3버튼 내비게이션에서는 시스템 바가 48dp 가까이 되므로, 그 위로 올려야 바가 잘리지 않습니다.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = AppNavBarMargin)
            .height(AppNavBarHeight),
    ) {
        // 서리 유리 바탕은 아이콘 Row 의 형제로 둡니다 — Row(haze 소스)가 바 효과의 자식이면 Haze 가 재귀를 피하려
        // 블러를 끄고 반투명 틴트만 그려, 바 뒤 글자가 선명하게 비쳤습니다.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(barShape)
                .then(
                    if (hazeState != null) Modifier.hazeEffect(hazeState, frostStyle)
                    else Modifier.background(barColor),
                )
                .drawWithContent {
                    drawContent()
                    // 위쪽이 밝은 얇은 유리 테두리.
                    val strokePx = with(density) { 1.dp.toPx() }
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.22f else 0.95f),
                                Color.White.copy(alpha = if (isDark) 0.04f else 0.3f),
                            ),
                        ),
                        cornerRadius = CornerRadius(with(density) { 32.dp.toPx() }),
                        style = Stroke(strokePx),
                        topLeft = Offset(strokePx / 2f, strokePx / 2f),
                        size = Size(size.width - strokePx, size.height - strokePx),
                    )
                },
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            val cellWidth = maxWidth / count
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val capsuleRadiusPx = with(density) { 26.dp.toPx() }
            val capsuleShape = RoundedCornerShape(26.dp)

            // 선택 캡슐 — 삼성 헬스처럼 바를 4등분한 칸 하나 폭. 셰이더가 있으면 렌즈, 없으면 반투명 틴트.
            // 렌즈는 가장자리에서 캡슐 바깥의 화면을 끌어와 보여주므로, 상자를 사방 margin 만큼 키워 뒤 화면을 더 넓게
            // 받고 실제 캡슐 모양은 셰이더 안에서 잘라냅니다 (안 그러면 가장자리가 검게 비었습니다).
            // margin 은 셰이더 최대 굴절 거리(짧은 반지름 × 0.75 × 1.25 × 1.18 ≈ 28dp)보다 커야 합니다.
            val lensMargin = if (liquidEnabled) 30.dp else 0.dp
            val lensMarginPx = with(density) { lensMargin.toPx() }
            // 탭 아이콘·글자 4칸 — 실제 Row(터치 받음)와 캡슐 속 복사본(터치 없음)이 같은 내용을 씁니다.
            val navItems: @Composable RowScope.(clickable: Boolean) -> Unit = { clickableItems ->
                labels.indices.forEach { index ->
                    val isSelected = index == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                            .then(
                                if (clickableItems) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelect(index) },
                                ) else Modifier,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        NavIcon(index, if (isSelected) activeTint else inactiveTint)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            labels[index],
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) activeTint else inactiveTint,
                        )
                    }
                }
            }
            val itemsW = with(density) { maxWidth.roundToPx() }
            val itemsH = with(density) { maxHeight.roundToPx() }
            val capsule: @Composable () -> Unit = {
                // 1) 캡슐 서리 유리 — 바와 같은 결의 블러. 렌즈 셰이더 레이어와 분리해 따로 그립니다: 셰이더 레이어 안(자식이든
                //    같은 노드든)에 블러를 두면 삼성 기기에서 캡슐을 끌었다 놓은 뒤 블러가 사라져 뒤 본문이 선명하게 비쳤습니다.
                if (liquidEnabled && hazeState != null) {
                    Box(
                        Modifier
                            .offset { IntOffset((capsuleIndex * cellWidthPx).roundToInt(), 0) }
                            .width(cellWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                scaleX = stretchX
                                scaleY = squashY
                                clip = true
                                shape = capsuleShape
                            }
                            .hazeEffect(hazeState, capsuleFrostStyle),
                    )
                }
                // 2) 렌즈 — 탭 아이콘·글자 복사본만 굴절합니다 (나머지는 투명해 아래 서리가 비침).
                Box(
                    modifier = Modifier
                        // 세로는 requiredHeight 가 부모 높이를 넘어 자동으로 가운데 정렬되므로(위아래로 margin 씩 삐져나감) 옮기지 않습니다.
                        .offset { IntOffset((capsuleIndex * cellWidthPx - lensMarginPx).roundToInt(), 0) }
                        // 부모 제약보다 커야 하므로 required 크기로 (height 는 부모 최대 높이에 눌려 절반만 보였습니다).
                        .requiredWidth(cellWidth + lensMargin * 2)
                        .requiredHeight(maxHeight + lensMargin * 2)
                        .graphicsLayer {
                            scaleX = stretchX
                            scaleY = squashY
                            if (!liquidEnabled) {
                                clip = true
                                shape = capsuleShape
                            }
                            if (liquidEnabled && lensShader != null) {
                                lensShader.setFloatUniform(
                                    "rect",
                                    lensMarginPx, lensMarginPx, size.width - lensMarginPx, size.height - lensMarginPx,
                                )
                                lensShader.setFloatUniform("radius", capsuleRadiusPx)
                                // 놓여 있을 땐 0(굴절 없음), 끌 때만 1로 — 스프링으로 부드럽게.
                                lensShader.setFloatUniform("strength", liquid)
                                lensShader.setFloatUniform("lightDir", light.x, light.y)
                                lensShader.setFloatUniform("time", light.time)
                                lensShader.setFloatUniform("tint", 1f, 1f, 1f)
                                lensShader.setFloatUniform("tintAlpha", if (isDark) 0.03f else 0.08f)
                                lensShader.setFloatUniform("dir", 1f)
                                lensShader.setFloatUniform("rainbow", 0f)
                                renderEffect = RenderEffect
                                    .createRuntimeShaderEffect(lensShader, "content")
                                    .asComposeRenderEffect()
                            }
                        }
                        .then(if (liquidEnabled) Modifier else Modifier.background(selectedCapsuleColor)),
                ) {
                    if (liquidEnabled && hazeState != null) {
                        // 그 위에 탭 아이콘·글자를 한 번 더 — 아래 Row 와 같은 자리에 겹치게 놓아 렌즈가 굴절합니다.
                        // (Row 를 haze 소스로 등록해 가져오는 방식은 첫 프레임에 비거나 뒤 화면이 새어 나와 버렸습니다.)
                        Box(
                            Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(Constraints.fixed(itemsW, itemsH))
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(
                                        (lensMarginPx - capsuleIndex * cellWidthPx).roundToInt(),
                                        lensMarginPx.roundToInt(),
                                    )
                                }
                            },
                        ) {
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { navItems(false) }
                        }
                    }
                }
            }
            // 렌즈일 때는 아이콘·글자 Row 를 캡슐 "아래"에 그리고, 캡슐 안의 복사본이 렌즈에 굴절되어 보입니다
            // (iOS 탭 바처럼 캡슐 가장자리에서 글자가 휘어짐). 셰이더가 없으면 예전처럼 캡슐 위에 Row 를 올립니다.
            // 터치는 어느 쪽이든 Row 가 받습니다.
            if (!liquidEnabled) capsule()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(count) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragIndex = currentSelected.toFloat() },
                            onDragEnd = {
                                val target = dragIndex?.roundToInt()?.coerceIn(0, count - 1)
                                if (target == null || target == currentSelected) {
                                    dragIndex = null
                                } else {
                                    // selected 가 바뀔 때까지 캡슐을 놓은 칸에 붙잡아 둡니다 — 바로 null 로 두면 한 프레임
                                    // 동안 이전 selected 로 되돌아가는 목표가 잡혀 캡슐이 떨렸습니다.
                                    dragIndex = target.toFloat()
                                    pendingIndex = target
                                    currentOnSelect(target)
                                }
                            },
                            onDragCancel = { dragIndex = null },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                dragIndex = ((dragIndex ?: currentSelected.toFloat()) + dx / cellWidthPx).coerceIn(0f, (count - 1).toFloat())
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems(true)
            }
            if (liquidEnabled) capsule()
        }
    }
}

/** 하단 바 탭 순서 — 홈 · 신청·내역 · 게시판 · 급식 · 일정 (디버그 빌드는 끝에 Dev). */
object TabIndex {
    const val HOME = 0
    const val APPLY = 1
    const val BOARD = 2
    const val MEAL = 3
    const val SCHEDULE = 4
    const val DEV = 5
    val titles = listOf("홈", "신청·내역", "게시판", "급식", "일정")
}

/** 일정 탭 위의 시간표 / 학사일정 칩 줄 (높이 [ScheduleSubTabsHeight]). */
private val ScheduleSubTabsHeight = 52.dp

@Composable
private fun ScheduleSubTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ScheduleSubTabsHeight).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneUiChip(selected = selected == 0, onClick = { onSelect(0) }, label = "시간표")
        OneUiChip(selected = selected == 1, onClick = { onSelect(1) }, label = "학사일정")
    }
}

@Composable
private fun NavIcon(index: Int, tint: Color) {
    when (index) {
        TabIndex.HOME -> Icon(Icons.Outlined.Home, contentDescription = "홈", tint = tint)
        TabIndex.APPLY -> Icon(Icons.Outlined.Edit, contentDescription = "신청·내역", tint = tint)
        TabIndex.BOARD -> Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "게시판", tint = tint)
        TabIndex.MEAL -> Icon(painter = painterResource(R.drawable.ic_meal), contentDescription = "급식", tint = tint)
        TabIndex.SCHEDULE -> Icon(Icons.Outlined.DateRange, contentDescription = "일정", tint = tint)
        else -> Icon(Icons.Outlined.Build, contentDescription = "Dev", tint = tint)
    }
}

/** 급식 탭 — 끼니별 식단과 사진을 카드로, 주간 보기에서는 7일을 각각 조회합니다. */
@Composable
private fun MealTab(
    dayMeals: DayMeals?,
    isLoading: Boolean,
    allergyCodes: Set<Int>,
    weekMode: Boolean,
    onToggleWeek: () -> Unit,
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    availableDays: Set<String>,
    onSelectDate: (LocalDate) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val items = dayMeals?.items.orEmpty()
    val photos = dayMeals?.photos.orEmpty()
    val present = Meal.entries.filter { items[it.key].orEmpty().isNotEmpty() }

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 일간 / 주간 — 둘 중 하나가 항상 켜져 있어 전환 버튼임이 드러납니다.
                OneUiChip(selected = !weekMode, onClick = { if (weekMode) onToggleWeek() }, label = "일간 보기")
                Spacer(Modifier.width(8.dp))
                OneUiChip(selected = weekMode, onClick = { if (!weekMode) onToggleWeek() }, label = "주간 보기")
                Spacer(Modifier.weight(1f))
                if (isLoading) {
                    OneUiLoading(size = 18.dp, stroke = 2.dp)
                }
            }
        }

        if (weekMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    weekDates.forEach { date ->
                        val hasData = PlanStore.dayKey(date) in availableDays
                        // 식단이 없는 날은 점(" ·")을 붙이는 대신 칩을 옅게 — 점이 글자 뒤에 남은 것처럼 보였습니다.
                        OneUiChip(
                            selected = date == selectedDate,
                            onClick = { onSelectDate(date) },
                            label = shortMealDate(date),
                            modifier = Modifier.alpha(if (hasData) 1f else 0.5f),
                        )
                    }
                }
            }
        }

        when {
            present.isEmpty() && isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    OneUiLoading(size = 28.dp, stroke = 3.dp)
                }
            }

            present.isEmpty() -> item {
                Text(
                    if (weekMode) "${shortMealDate(selectedDate)} 식단이 없습니다" else "등록된 식단이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }

            else -> items(present) { meal ->
                MealCard(
                    meal = meal,
                    items = items[meal.key].orEmpty(),
                    photoFile = photos[meal.key],
                    allergyCodes = allergyCodes,
                )
            }
        }
    }
}

private fun shortMealDate(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.KOREAN)
    return "${date.dayOfMonth} $dow"
}

/** 서버 썸네일을 OkHttp 로 받아 [BitmapFactory] 로 디코드합니다 (Coil 미사용). */
private object MealPhotoLoader {
    const val UA =
        "Mozilla/5.0 (Linux; Android 16; SM-S938N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/** 여러 후보 URL 을 순서대로 받아 처음 성공한 비트맵을 돌려줍니다 — 원본 404 시 썸네일로 폴백합니다. */
private suspend fun loadRemoteBitmap(urls: List<String>): ImageBitmap? {
    for (url in urls) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", MealPhotoLoader.UA).build()
                MealPhotoLoader.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bytes = response.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.getOrNull()
        }
        if (loaded != null) return loaded.asImageBitmap()
    }
    return null
}

@Composable
internal fun RemoteThumbnail(urls: List<String>, modifier: Modifier = Modifier) {
    var bitmap by remember(urls) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(urls) {
        bitmap = loadRemoteBitmap(urls)
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "급식 사진",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/** 급식 사진 전체화면 뷰어 — 검은 배경, 바깥 탭/닫기 버튼으로 닫고 핀치 줌을 지원합니다. */
@Composable
private fun MealPhotoViewer(urls: List<String>, onDismiss: () -> Unit) {
    var bitmap by remember(urls) { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(urls) { bitmap = loadRemoteBitmap(urls) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val loaded = bitmap
            if (loaded == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Image(
                    bitmap = loaded,
                    contentDescription = "급식 사진",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        // 이미지 위 탭은 부모의 닫기를 막고, 핀치/드래그로 확대·이동합니다.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        },
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
            }
        }
    }
}

/**
 * 메뉴 항목마다 그 항목에 붙은 코드(또는 이름 키워드)로 경고를 답니다.
 */
@Composable
private fun MealCard(
    meal: Meal,
    items: List<MealItem>,
    photoFile: String?,
    allergyCodes: Set<Int>,
) {
    OneUiCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // 끼니마다 색이 다른 원형 배지 — 아침 연두 / 점심 하늘 / 저녁 보라 (삼성 헬스 지표 아이콘 톤).
        val badge = when (meal) {
            Meal.entries.first() -> OneUi.Lime
            Meal.entries.last() -> OneUi.Violet
            else -> OneUi.Sky
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OneUiMetricIcon(painterResource(R.drawable.ic_meal), badge, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(meal.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        val thumbUrl = HanaMealClient.mealPhotoThumbUrl(photoFile)
        if (thumbUrl != null) {
            var showViewer by remember { mutableStateOf(false) }
            Spacer(Modifier.height(12.dp))
            RemoteThumbnail(
                urls = listOf(thumbUrl),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(OneUi.CornerMedium))
                    .clickable { showViewer = true },
            )
            if (showViewer) {
                // 원본이 비어 있으면 썸네일로 폴백합니다.
                MealPhotoViewer(
                    urls = listOfNotNull(HanaMealClient.mealPhotoFullUrl(photoFile), thumbUrl),
                    onDismiss = { showViewer = false },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        items.forEach { item ->
            // 이름에 원재료가 없어도 항목 코드로 잡습니다 (예: 배추겉절이(9) → 새우).
            val labels = shortAllergyLabels(detectedAllergies(item.codes, item.name, allergyCodes))
            val flagged = labels.isNotEmpty()
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "·",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (flagged) FontWeight.Bold else FontWeight.Normal,
                    color = if (flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (flagged) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "⚠ ${labels.joinToString("·")}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 월~금 주간 시간표 — 오늘 요일 열에 옅은 배경을 깔아 한눈에 찾을 수 있게 합니다 */
@Composable
private fun WeekTimetable(today: LocalDate, revision: Int, modifier: Modifier = Modifier, rowHeight: Dp = 78.dp) {
    val todayDow = today.dayOfWeek.value
    val dayLabels = listOf("월", "화", "수", "목", "금")
    // revision 이 바뀌면 포털에서 받은 시간표로 다시 그립니다. 교시 목록과 칸 내용을
    // 같은 스냅샷에서 함께 뽑아야, 그 사이 백그라운드 동기화가 새 표를 설치해도
    // 머리글은 옛 교시인데 칸은 새 표를 보여주는 식으로 어긋나지 않습니다.
    val (periodTimes, lessons) = remember(revision) { Timetable.activePeriodTimes to Timetable.activeLessons }
    val periods = remember(periodTimes) { periodTimes.keys.sorted() }
    // 교시 열은 좁게, 행은 넉넉히(호출부가 화면에 맞춰 정함) — 과목명이 세 줄까지 들어가고 교실이 그 아래 붙습니다.
    val timeWidth = 40.dp
    val todayTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val gridLine = MaterialTheme.colorScheme.outlineVariant

    fun timeLabel(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

    // 88dp 고정폭 대신 남는 폭을 5칸이 나눠 가져 금요일까지 스크롤 없이 보입니다.
    Column(modifier.fillMaxWidth()) {
        Row {
            Spacer(Modifier.width(timeWidth).height(rowHeight))
            dayLabels.forEachIndexed { index, label ->
                val isToday = index + 1 == todayDow
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(rowHeight)
                        .background(if (isToday) todayTint else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.8.dp, color = gridLine)

        periods.forEach { period ->
            val time = periodTimes[period]
            Row {
                Column(
                    modifier = Modifier.width(timeWidth).height(rowHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$period", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    time?.let {
                        Text(
                            timeLabel(it.first),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                dayLabels.indices.forEach { index ->
                    val dow = index + 1
                    val lesson = lessons[dow]?.get(period)
                    val isToday = dow == todayDow
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(rowHeight)
                            .background(if (isToday) todayTint else Color.Transparent)
                            .padding(horizontal = 3.dp, vertical = 5.dp),
                    ) {
                        if (lesson != null) {
                            Column {
                                Text(
                                    lesson.subject,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, lineHeight = 14.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (lesson.isFree) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                lesson.room?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 0.8.dp, color = gridLine)
        }
    }
}

/**
 * 강조 색 프리셋을 원형 스와치로 고르는 그룹 행.
 * 맨 앞의 스와치는 "자동" — One UI 기본 파란색을 씁니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPickerRow(selectedArgb: Int, onSelect: (Int) -> Unit) {
    var showingPicker by remember { mutableStateOf(false) }
    val isPreset = AccentPresets.any { it.toArgb() == selectedArgb }
    val isCustom = selectedArgb != PlanStore.AUTO_ACCENT_COLOR && !isPreset

    Column(Modifier.padding(horizontal = OneUi.RowPadding, vertical = 14.dp)) {
        Text("강조 색", style = MaterialTheme.typography.bodyLarge)
        Text(
            when {
                selectedArgb == PlanStore.AUTO_ACCENT_COLOR ->
                    if (systemAccentColor(LocalContext.current) != null) "자동 (시스템 테마 색)" else "자동 (One UI 파란색)"
                isCustom -> "직접 선택 · #%06X".format(0xFFFFFF and selectedArgb)
                else -> "프리셋"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(12.dp))
        // 스크롤 없이 한눈에 — 한 줄에 5개씩 두 줄, 카드 폭에 고르게 펼쳐 왼쪽에 몰리지 않게 합니다.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 5,
        ) {
            AccentSwatch(
                selected = selectedArgb == PlanStore.AUTO_ACCENT_COLOR,
                onClick = { onSelect(PlanStore.AUTO_ACCENT_COLOR) },
                auto = true,
            )
            AccentPresets.forEach { color ->
                AccentSwatch(
                    selected = color.toArgb() == selectedArgb,
                    onClick = { onSelect(color.toArgb()) },
                    color = color,
                )
            }
            // 프리셋 밖의 색 — 무지개 스와치를 탭하면 컬러 피커가 열립니다.
            CustomAccentSwatch(
                selected = isCustom,
                customColor = if (isCustom) Color(selectedArgb) else null,
                onClick = { showingPicker = true },
            )
        }
    }

    if (showingPicker) {
        AccentColorPickerDialog(
            initialArgb = if (selectedArgb == PlanStore.AUTO_ACCENT_COLOR) OneUi.Blue.toArgb() else selectedArgb,
            onDismiss = { showingPicker = false },
            onApply = { argb ->
                showingPicker = false
                onSelect(argb)
            },
        )
    }
}

/** 원형 색 스와치 — auto 면 자동 색(시스템 테마 색 또는 One UI 파란색)→흰색 그라디언트로 프리셋과 구분합니다. */
@Composable
private fun AccentSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    color: Color? = null,
    auto: Boolean = false,
) {
    val fill = when {
        color != null -> Modifier.background(color)
        auto -> {
            val base = systemAccentColor(LocalContext.current) ?: OneUi.Blue
            Modifier.background(Brush.linearGradient(listOf(base, lerp(base, Color.White, 0.55f))))
        }
        else -> Modifier
    }
    val checkTint = when {
        auto -> Color.White
        color != null && color.luminance() > 0.6f -> Color(0xFF1A1A1C)
        else -> Color.White
    }

    Box(
        modifier = Modifier.size(44.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .then(fill)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = checkTint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** 홈 위젯의 테마(라디오 행)·강조 색(라디오 행)·배경 불투명도(슬라이더 행) — 그룹 컨테이너 안에 들어갑니다. */
@Composable
private fun WidgetSettingsSection(
    theme: String,
    accent: String,
    kindColors: Map<String, Int>,
    onKindColorChange: (String, Int?) -> Unit,
    onKindColorsReset: () -> Unit,
    opacity: Float,
    onThemeChange: (String) -> Unit,
    onAccentChange: (String) -> Unit,
    onOpacityChange: (Float) -> Unit,
) {
    Text(
        "테마",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = OneUi.RowPadding, end = OneUi.RowPadding, top = 14.dp, bottom = 4.dp),
    )
    PlanStore.themes.forEach { option ->
        OneUiRadioRow(
            selected = theme == option,
            label = PlanStore.themeLabel(option),
            onClick = { onThemeChange(option) },
        )
    }
    OneUiDivider()
    Text(
        "강조 색",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = OneUi.RowPadding, end = OneUi.RowPadding, top = 14.dp, bottom = 4.dp),
    )
    PlanStore.widgetAccents.forEach { option ->
        OneUiRadioRow(
            selected = accent == option,
            label = PlanStore.widgetAccentLabel(option),
            onClick = { onAccentChange(option) },
        )
        // '종류별 색'이 켜져 있으면 바로 아래에 종류마다 색을 고르는 칩을 펼칩니다.
        if (option == PlanStore.WIDGET_ACCENT_KIND && accent == option) {
            WidgetKindColorChips(kindColors, onKindColorChange, onKindColorsReset)
        }
    }
    OneUiDivider()
    Column(Modifier.padding(horizontal = OneUi.RowPadding, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("배경 불투명도", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text("${opacity.toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(4.dp))
        OneUiSlider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 10f..100f,
            steps = 8,
        )
    }
}

/** 종류별 색 칩 — 색 점 + 이름, 탭하면 컬러 피커. 바꾼 항목이 있으면 '기본값으로' 버튼이 보입니다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetKindColorChips(
    kindColors: Map<String, Int>,
    onKindColorChange: (String, Int?) -> Unit,
    onKindColorsReset: () -> Unit,
) {
    var picking by remember { mutableStateOf<Accent?>(null) }
    val resolved = WidgetKindColors.resolve(kindColors)

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OneUi.RowPadding, end = OneUi.RowPadding, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WidgetKindColors.ordered.forEach { accent ->
            val color = Color(resolved.getValue(accent))
            OneUiChip(
                selected = false,
                onClick = { picking = accent },
                label = WidgetKindColors.label(accent),
                leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(color)) },
            )
        }
    }
    if (kindColors.isNotEmpty()) {
        OneUiTextButton(
            text = "기본 색으로 되돌리기",
            onClick = onKindColorsReset,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = OneUi.RowPadding - 14.dp, bottom = 6.dp),
        )
    }

    picking?.let { accent ->
        AccentColorPickerDialog(
            initialArgb = resolved.getValue(accent),
            onDismiss = { picking = null },
            onApply = { argb ->
                picking = null
                // 기본 색과 같으면 저장하지 않고 기본으로 둡니다.
                onKindColorChange(accent.name, argb.takeIf { it != WidgetKindColors.defaults[accent] })
            },
        )
    }
}

// MARK: - 위치 선택 다이얼로그

private enum class PlaceKind(val label: String) {
    NONE("미설정"), CLASSROOM("교과교실"), STUDY_ROOM("면학실"), LIBRARY("도서관"), DORM("생활관"),
}

private fun kindFor(place: StudyPlace?): PlaceKind = when (place) {
    null -> PlaceKind.NONE
    is StudyPlace.ClassroomPlace -> PlaceKind.CLASSROOM
    is StudyPlace.StudyRoom -> PlaceKind.STUDY_ROOM
    is StudyPlace.Library -> PlaceKind.LIBRARY
    StudyPlace.Dorm -> PlaceKind.DORM
    // 방과후/1인2기/기타(포털 원문 분류)는 동기화로만 채워지므로 수동 편집 화면에서는 미설정으로 취급합니다
    is StudyPlace.AfterSchool -> PlaceKind.NONE
    is StudyPlace.OneTwo -> PlaceKind.NONE
    is StudyPlace.Other -> PlaceKind.NONE
    is StudyPlace.Midnight -> PlaceKind.NONE
}

@Composable
private fun PlaceEditorDialog(
    slot: PlanSlot,
    initial: StudyPlace?,
    onDismiss: () -> Unit,
    onSave: (StudyPlace?) -> Unit,
) {
    var kind by remember { mutableStateOf(kindFor(initial)) }
    var useCustomRoom by remember { mutableStateOf(initial is StudyPlace.ClassroomPlace && initial.classroom is Classroom.Other) }
    var selectedClassroom by remember {
        mutableStateOf(
            (initial as? StudyPlace.ClassroomPlace)?.classroom?.takeIf { it !is Classroom.Other } ?: Classroom.A201,
        )
    }
    var customRoom by remember {
        mutableStateOf(((initial as? StudyPlace.ClassroomPlace)?.classroom as? Classroom.Other)?.customName ?: "")
    }
    var seat by remember {
        mutableStateOf(
            when (initial) {
                is StudyPlace.StudyRoom -> initial.seat
                is StudyPlace.Library -> initial.seat.substringAfter("-", initial.seat)
                else -> ""
            },
        )
    }
    var libraryFloor by remember {
        mutableStateOf(
            (initial as? StudyPlace.Library)?.seat
                ?.let { if (it.contains("-")) it.substringBefore("-") else "" }
                ?: "",
        )
    }

    val kinds = remember(slot) { PlaceKind.entries.filter { it != PlaceKind.DORM || slot.allowsDorm } }

    val isValid = when (kind) {
        PlaceKind.NONE, PlaceKind.DORM -> true
        PlaceKind.CLASSROOM -> if (useCustomRoom) customRoom.trim().isNotEmpty() else true
        PlaceKind.STUDY_ROOM -> seat.trim().isNotEmpty()
        PlaceKind.LIBRARY -> seat.trim().isNotEmpty() && libraryFloor.trim().isNotEmpty()
    }

    fun buildPlace(): StudyPlace? = when (kind) {
        PlaceKind.NONE -> null
        PlaceKind.DORM -> StudyPlace.Dorm
        PlaceKind.CLASSROOM -> StudyPlace.ClassroomPlace(
            if (useCustomRoom) Classroom.Other(customRoom.trim()) else selectedClassroom,
        )
        PlaceKind.STUDY_ROOM -> StudyPlace.StudyRoom(seat.trim())
        PlaceKind.LIBRARY -> StudyPlace.Library("${libraryFloor.trim()}-${seat.trim()}")
    }

    // 다이얼로그 안 라디오 목록은 컨테이너 여백 없이 촘촘하게 — 세로 12dp 행 간격입니다.
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = slot.title,
        buttons = listOf(
            OneUiDialogButton("취소", onDismiss),
            OneUiDialogButton("저장", { onSave(buildPlace()) }, enabled = isValid),
        ),
    ) {
        kinds.forEach { k ->
            DialogRadioRow(selected = kind == k, label = k.label, onClick = { kind = k })
        }

        if (kind == PlaceKind.CLASSROOM) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OneUiChip(selected = !useCustomRoom, onClick = { useCustomRoom = false }, label = "목록에서 선택")
                OneUiChip(selected = useCustomRoom, onClick = { useCustomRoom = true }, label = "직접 입력")
            }
            Spacer(Modifier.height(6.dp))
            if (useCustomRoom) {
                OneUiTextField(
                    value = customRoom,
                    onValueChange = { customRoom = it },
                    label = "교실 이름",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Classroom.presets.forEach { room ->
                    DialogRadioRow(
                        selected = selectedClassroom == room,
                        label = room.name,
                        onClick = { selectedClassroom = room },
                    )
                }
            }
        }

        if (kind == PlaceKind.STUDY_ROOM) {
            Spacer(Modifier.height(6.dp))
            OneUiTextField(
                value = seat,
                onValueChange = { seat = it },
                label = "자리 번호 (예: 37번)",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (kind == PlaceKind.LIBRARY) {
            Spacer(Modifier.height(6.dp))
            OneUiTextField(
                value = libraryFloor,
                onValueChange = { libraryFloor = it },
                label = "층수 (예: 2)",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            OneUiTextField(
                value = seat,
                onValueChange = { seat = it },
                label = "좌석 번호 (예: 34)",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 다이얼로그 본문 안의 라디오 행 — 그룹 컨테이너용 [OneUiRadioRow] 보다 좌우 여백이 없습니다. */
@Composable
private fun DialogRadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUi.CornerSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneUiRadio(selected)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// MARK: - 하이하나 계정 다이얼로그

/** 로그인 확인 실패 원인을 다이얼로그에 보여줄 짧은 한국어 문구로 바꿉니다. */
private fun loginFailureMessage(e: Throwable): String = when (e) {
    is HanaPortalException.LoginFailed -> e.message ?: "로그인에 실패했습니다"
    is HanaPortalException.MissingCredentials -> e.message ?: "아이디/비밀번호를 먼저 등록해 주세요"
    is HanaPortalException.TokenNotFound -> e.message ?: "로그인 페이지를 불러오지 못했습니다"
    is HanaPortalException.UnexpectedResponse -> "학사시스템 응답을 확인하지 못했습니다"
    else -> "네트워크 오류로 로그인을 확인하지 못했습니다"
}

@Composable
private fun HanaAccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var memId by remember { mutableStateOf(HanaCredentialStore.memId(context) ?: "") }
    var memPwd by remember { mutableStateOf("") }
    var hasCredentials by remember { mutableStateOf(HanaCredentialStore.hasCredentials(context)) }
    // 저장 직후 로그인을 실제로 확인해 결과를 다이얼로그 안에서 바로 보여줍니다.
    var isChecking by remember { mutableStateOf(false) }
    var loginSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var loginFailure by remember { mutableStateOf<String?>(null) }

    fun saveAndCheck() {
        val id = memId.trim()
        val pwd = memPwd.trim()
        HanaCredentialStore.setMemId(context, id)
        HanaCredentialStore.setMemPwd(context, pwd)
        hasCredentials = true
        loginSucceeded = null
        loginFailure = null
        isChecking = true
        scope.launch {
            // fetchDailySync 자체가 IO 로 전환하지만, 저장 직후 네트워크 대기 동안
            // 메인 스레드가 막히지 않도록 명시적으로 IO 에서 돌립니다.
            val result = withContext(Dispatchers.IO) {
                runCatching { HanaPortalClient.get().fetchDailySync(context, PlanStore.today()) }
            }
            isChecking = false
            result.fold(
                onSuccess = {
                    loginSucceeded = true
                    Log.d("HanaLogin", "로그인 성공 (id=$id)")
                },
                onFailure = { e ->
                    loginSucceeded = false
                    loginFailure = loginFailureMessage(e)
                    Log.d("HanaLogin", "로그인 실패 (id=$id) ${e::class.simpleName}: ${e.message}")
                },
            )
        }
    }

    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "학사시스템 연동",
        buttons = buildList {
            if (hasCredentials) {
                add(OneUiDialogButton("연결 해제", { HanaCredentialStore.clear(context); onDismiss() }, destructive = true))
            }
            add(OneUiDialogButton("닫기", onDismiss))
            add(
                OneUiDialogButton(
                    "저장",
                    ::saveAndCheck,
                    enabled = !isChecking && memId.trim().isNotEmpty() && memPwd.trim().isNotEmpty(),
                    busyText = if (isChecking) "확인 중" else null,
                ),
            )
        },
    ) {
        OneUiTextField(
            value = memId,
            onValueChange = { memId = it },
            label = "아이디",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        OneUiTextField(
            value = memPwd,
            onValueChange = { memPwd = it },
            label = "비밀번호",
            visualTransformation = PasswordVisualTransformation(),
            // 키보드에 비밀번호 칸임을 알립니다 — 안 그러면 삼성 키보드가 추천 단어 줄에 입력 중인 비밀번호를
            // 그대로 보여주고 학습까지 합니다.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "입력한 계정으로 hh.hana.hs.kr(하이하나)에 로그인해 평일1·2타임 면학 위치를 자동으로 가져옵니다. " +
                "비밀번호는 이 기기에 암호화되어 저장됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        loginSucceeded?.let { ok ->
            Spacer(Modifier.height(12.dp))
            Text(
                if (ok) "로그인 성공" else "로그인 실패",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!ok) {
                loginFailure?.let { reason ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
