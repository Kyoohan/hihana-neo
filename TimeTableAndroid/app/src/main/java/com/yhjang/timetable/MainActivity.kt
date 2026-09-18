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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.yhjang.timetable.ui.AccentPresets
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
import com.yhjang.timetable.ui.OneUiTextField
import com.yhjang.timetable.ui.TimeTableTheme
import com.yhjang.timetable.ui.isDark
import com.yhjang.timetable.ui.oneUiBackground
import com.yhjang.timetable.ui.rememberOneUiHeaderState
import com.yhjang.timetable.widget.TimeTableWidget
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
        // 한 번 소비한 뒤 제거해, 재구성 시 같은 탭 요청이 다시 적용되지 않게 합니다.
        intent?.removeExtra(EXTRA_OPEN_TAB)
        setContent {
            TimeTableApp(
                openAlimRequest = openAlimRequest,
                openTabRequest = openTabRequest,
                resumeSyncRequest = resumeSyncRequest,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAlimRequest.value = intent.getBooleanExtra(AlimNotifier.EXTRA_OPEN_ALIM, false)
        openTabRequest.value = intent.getStringExtra(EXTRA_OPEN_TAB)
        intent.removeExtra(EXTRA_OPEN_TAB)
    }

    companion object {
        /** 위젯 탭 → 앱에서 열 탭 지정용 extra 키와 값. 위젯(TimeTableWidget)도 함께 씁니다. */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_TODAY = "today"
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

    TimeTableTheme(accentArgb = accentArgb, darkTheme = darkTheme) {
        TimeTableAppContent(
            accentArgb = accentArgb,
            onAccentChange = { accentArgb = it },
            appTheme = appTheme,
            onAppThemeChange = { appTheme = it },
            openAlimRequest = openAlimRequest,
            openTabRequest = openTabRequest,
            resumeSyncRequest = resumeSyncRequest,
        )
    }
}

@Composable
private fun TimeTableAppContent(
    accentArgb: Int,
    onAccentChange: (Int) -> Unit,
    appTheme: String,
    onAppThemeChange: (String) -> Unit,
    openAlimRequest: MutableState<Boolean>,
    openTabRequest: MutableState<String?>,
    resumeSyncRequest: MutableState<Int>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var today by remember { mutableStateOf(PlanStore.today()) }
    var places by remember { mutableStateOf<Map<PlanSlot, StudyPlace?>>(emptyMap()) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var editingSlot by remember { mutableStateOf<PlanSlot?>(null) }
    var showingAccountSheet by remember { mutableStateOf(false) }
    var showingSettings by remember { mutableStateOf(false) }
    var homeWidgetOpacity by remember { androidx.compose.runtime.mutableFloatStateOf(60f) }
    var homeWidgetTheme by remember { mutableStateOf(PlanStore.THEME_SYSTEM) }
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

    // 설정을 상단 아이콘으로 옮겨 하단 탭은 오늘/주/급식/학사 네 개입니다.
    val tabTitles = listOf("오늘", "주", "급식", "학사")
    // 이전 버전 저장 상태(설정=3, 학사=4)가 복원돼도 범위를 벗어나지 않게 보정합니다.
    if (tab !in tabTitles.indices) tab = 0

    // 학사 탭
    var academicSubTab by rememberSaveable { mutableStateOf(0) }
    var scheduleEntries by remember { mutableStateOf<List<HanaScheduleEntry>>(emptyList()) }
    var scheduleLoading by remember { mutableStateOf(false) }
    var scheduleError by remember { mutableStateOf<String?>(null) }
    var boardCategoryIndex by rememberSaveable { mutableStateOf(0) }
    var boardPosts by remember { mutableStateOf<List<HanaBoardPost>>(emptyList()) }
    var boardLoading by remember { mutableStateOf(false) }
    var boardError by remember { mutableStateOf<String?>(null) }
    // 앱 내 웹뷰로 띄울 페이지 (null 이면 닫힘) — 게시글·신청·내역 페이지 공용
    var webPage by remember { mutableStateOf<HanaWebPage?>(null) }
    // 시험 키워드 게시글을 탭하면 웹뷰 대신 시험 정보 파싱 화면으로
    var examPost by remember { mutableStateOf<HanaBoardPost?>(null) }

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
            boardPosts = HanaAcademicRepository.board(context, BoardCategory.entries[boardCategoryIndex], force)
        } catch (e: HanaPortalException.MissingCredentials) {
            boardError = ACADEMIC_NEEDS_LOGIN
        } catch (e: HanaPortalException.LoginFailed) {
            boardError = e.message ?: ACADEMIC_NEEDS_LOGIN
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

    /** 게시글 열기 — 시험 키워드가 있으면 표/본문을 파싱한 시험 정보 화면, 아니면 기존 웹뷰 상세. */
    fun openBoardPost(post: HanaBoardPost) {
        if (ExamParser.hasExamKeyword(post.title)) {
            examPost = post
        } else {
            webPage = HanaWebPage(post.url, "게시글")
        }
    }

    suspend fun syncEverywhere() {
        reload()
        TimeTableWidget().updateAll(context)
    }

    LaunchedEffect(Unit) {
        // 캐시된 시간표가 있으면(네트워크 없이, 즉시) 가장 먼저 설치합니다. 이게 늦게 설치되면
        // 그 사이 Timetable.installedWeek 가 비어 있어 "오늘" 히어로 카드가 실제로는 수업·면학
        // 중이어도 "오늘 일정이 모두 끝났습니다 · 편안한 밤 보내세요"로 잘못 보였습니다
        // (블랭크 상태와 로딩 중 상태를 구분 안 하는 화면 로직 + 이 단계가 늦게 끝나는 문제가 겹친 것).
        runCatching { HanaTimetableSync.ensureInstalled(context) }
        timetableRevision++

        // "오늘" 탭에 바로 보이는 핵심 데이터(면학 위치 등)를 그다음으로, 독립적으로 채웁니다.
        // 예전엔 이 아래 단계들과 한 코루틴에 묶여 있어서, 뒤쪽의 시간표 동기화 중 하나가
        // 예외를 던지면 이 줄이 아예 실행되지 못하고 "오늘" 화면이 빈 채로 멈춰 있었습니다 —
        // 각 단계를 독립적으로 감싸 하나가 실패해도 나머지가 이어지게 합니다.
        runCatching { syncEverywhere() }

        homeWidgetOpacity = PlanStore.homeWidgetOpacity(context).toFloat()
        homeWidgetTheme = PlanStore.homeWidgetTheme(context)
        studentGrade = PlanStore.studentGrade(context)
        allergyCodes = PlanStore.allergyCodes(context)

        // 급식은 공개 엔드포인트라 로그인 없이도 갱신합니다 — 실패해도 앱 실행을 막지 않습니다.
        runCatching { loadMealDay(PlanStore.today(), force = false) }

        // TTL이 지났으면 렌더 DOM 으로 새로 받아옵니다 (실패 시 캐시 유지) — 느릴 수 있는
        // 네트워크 단계라, 이미 캐시로 화면이 채워진 뒤(위에서) 조용히 뒤따라오게 둡니다.
        runCatching { HanaTimetableSync.refresh(context, force = false) }
        timetableRevision++

        // 알리미는 로그인 계정이 있고 권한이 있을 때만 조용히 확인합니다.
        if (HanaCredentialStore.hasCredentials(context)) {
            runCatching { loadAlim(false) }
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

    // 위젯 탭으로 들어온 요청을 소비해 해당 탭을 엽니다 (오늘=0, 급식=2, 학사=3).
    LaunchedEffect(openTabRequest.value) {
        when (openTabRequest.value) {
            MainActivity.TAB_MEAL -> tab = 2
            MainActivity.TAB_ACADEMIC -> tab = 3
            MainActivity.TAB_TODAY -> tab = 0
        }
        openTabRequest.value = null
    }

    // 급식 탭에 들어오거나 주간 보기를 켜면 최신 식단으로 갱신
    LaunchedEffect(tab, mealWeekMode, selectedMealDay) {
        if (tab != 2) return@LaunchedEffect
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

    // 학사 탭에 들어올 때 데이터 로드
    LaunchedEffect(tab, academicSubTab) {
        if (tab != 3) return@LaunchedEffect
        when (academicSubTab) {
            0 -> if (scheduleEntries.isEmpty()) loadSchedule(false)
            1 -> loadBoard(false)
            // 2(신청·내역)는 섹션 컴포저블이 자체적으로 조회합니다.
            else -> Unit
        }
    }

    // 오늘 탭 카드(학사일정·게시판)는 학사 탭과 같은 캐시를 씁니다.
    // 비어 있을 때만 조용히 채우고, TTL 캐시라 대부분 네트워크를 타지 않습니다.
    LaunchedEffect(tab) {
        if (tab != 0) return@LaunchedEffect
        if (!HanaCredentialStore.hasCredentials(context)) return@LaunchedEffect
        if (scheduleEntries.isEmpty()) runCatching { loadSchedule(false) }
        if (boardPosts.isEmpty()) runCatching { loadBoard(false) }
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
        } catch (e: Exception) {
            syncError = e.message ?: "알 수 없는 오류가 발생했습니다"
        } finally {
            isSyncing = false
        }
    }

    // 외부 브라우저/포털에서 신청 후 앱으로 돌아오면 새로 받아 오늘·주·위젯을 갱신합니다.
    LaunchedEffect(resumeSyncRequest.value) {
        if (resumeSyncRequest.value == 0) return@LaunchedEffect
        if (!HanaCredentialStore.hasCredentials(context)) return@LaunchedEffect
        loadMealDay(PlanStore.today(), force = true)
        syncFromHana()
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
    val todayBlocks = remember(today, places, timetableRevision) { Timetable.blocks(today) { places[it] } }
    val currentBlock = todayBlocks.firstOrNull { !it.start.isAfter(now) && now.isBefore(it.end) } ?: todayBlocks.lastOrNull()
    val nextBlock = todayBlocks.firstOrNull { it.start.isAfter(now) && !it.isBlank }
    val upcomingGroups = coalesceUpcoming(todayBlocks.filter { it.start.isAfter(now) && !it.isBlank })
    val remainingMinutes = currentBlock
        ?.takeUnless { it.isBlank }
        ?.let { Duration.between(now, it.end).toMinutes().coerceAtLeast(0) }
    // 삼성 헬스 활동 링용 진행률 — 바깥 링은 오늘 첫 일정 시작~마지막 일정 끝 사이에서 지금이 어디쯤인지,
    // 안쪽 링은 현재 블록 안에서 얼마나 지났는지입니다. 일정이 없으면 null(트랙만 그림).
    val dayProgress = remember(todayBlocks, now) {
        val real = todayBlocks.filter { !it.isBlank }
        val first = real.minOfOrNull { it.start }
        val last = real.maxOfOrNull { it.end }
        if (first == null || last == null || !last.isAfter(first)) null
        else (Duration.between(first, now).toMillis().toFloat() / Duration.between(first, last).toMillis()).coerceIn(0f, 1f)
    }
    val blockProgress = currentBlock?.takeUnless { it.isBlank }?.let { block ->
        val total = Duration.between(block.start, block.end).toMillis()
        if (total <= 0) null else (Duration.between(block.start, now).toMillis().toFloat() / total).coerceIn(0f, 1f)
    }

    // 스크롤 시 가운데 큰 제목이 접히는 One UI 확장 헤더 동작
    val headerState = rememberOneUiHeaderState()

    val selectedMealDate = remember(selectedMealDay) {
        runCatching { LocalDate.parse(selectedMealDay) }.getOrDefault(PlanStore.today())
    }
    val mealWeekDates = remember(today) { (0 until 7).map { today.plusDays(it.toLong()) } }
    val availableMealDays = mealDays.keys

    // 페이지 배경은 Scaffold 뒤에서 그라디언트로 그리고, 헤더·콘텐츠는 그 위에 투명하게 얹습니다.
    Scaffold(
        modifier = Modifier.oneUiBackground(MaterialTheme.colorScheme.isDark),
        containerColor = Color.Transparent,
        topBar = {
            OneUiCollapsingHeader(
                state = headerState,
                title = tabTitles[tab],
                subtitle = dateText(today),
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            loadMealDay(today, force = true)
                            syncFromHana()
                            loadAlim(true)
                            if (tab == 3) {
                                when (academicSubTab) {
                                    0 -> loadSchedule(true)
                                    1 -> loadBoard(true)
                                }
                            }
                        }
                    }, enabled = !isSyncing) {
                        if (isSyncing) {
                            OneUiLoading(size = 20.dp, stroke = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "동기화")
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
                },
            )
        },
        bottomBar = {
            AppNavBar(selected = tab, onSelect = { tab = it })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(headerState.connection),
        ) {
            when (tab) {
                // 주간 시간표는 표준 카드 안에 담습니다. 포털에서 받은 표가 없으면 빈 격자 대신 안내를 띄웁니다.
                1 -> {
                    val timetableInstalled = remember(timetableRevision) { Timetable.fetchedWeek() != null }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = OneUi.PagePadding, end = OneUi.PagePadding, top = 8.dp, bottom = 16.dp),
                    ) {
                        OneUiSectionTitle("주간 시간표", modifier = Modifier.padding(bottom = 2.dp))
                        OneUiCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(if (timetableInstalled) 12.dp else 24.dp),
                        ) {
                            if (timetableInstalled) {
                                WeekTimetable(today, timetableRevision)
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

                2 -> MealTab(
                    dayMeals = mealDays[selectedMealDay],
                    isLoading = isLoadingMeals,
                    allergyCodes = allergyCodes,
                    weekMode = mealWeekMode,
                    onToggleWeek = { mealWeekMode = !mealWeekMode },
                    weekDates = mealWeekDates,
                    selectedDate = selectedMealDate,
                    availableDays = availableMealDays,
                    onSelectDate = { selectedMealDay = PlanStore.dayKey(it) },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                )

                3 -> AcademicTab(
                    subTab = academicSubTab,
                    onSubTabChange = { academicSubTab = it },
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
                    onOpenAccount = { showingAccountSheet = true },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> TodayDashboard(
                    today = today,
                    hasTimetable = hasTimetable,
                    currentBlock = currentBlock,
                    remainingMinutes = remainingMinutes,
                    nextTitle = nextBlock?.title,
                    nextRoom = nextBlock?.room,
                    dayProgress = dayProgress,
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
                    needsAccount = !HanaCredentialStore.hasCredentials(context),
                    onConnectAccount = { showingAccountSheet = true },
                    onEditSlot = { editingSlot = it },
                    onOpenAlim = { openAlim(it) },
                    onOpenAlimList = { openAlimScreen() },
                    onOpenPost = { openBoardPost(it) },
                    onOpenBoardList = {
                        academicSubTab = 1
                        tab = 3
                    },
                    onOpenSchedule = {
                        academicSubTab = 0
                        tab = 3
                    },
                    onNavigateToTab = { tab = it },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 포털 시간표 페이지를 JS로 렌더링해 DOM을 읽어올 숨은 WebView — 모든 탭에서 동작하도록 앱 루트에 둡니다.
            HanaTimetableWebViewHost()
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

    examPost?.let { post ->
        ExamInfoScreen(
            post = post,
            onDismiss = { examPost = null },
            onFallbackToWeb = { fallback ->
                examPost = null
                webPage = HanaWebPage(fallback.url, "게시글")
            },
            onOpenBrowser = { fallback ->
                openUrl(context, fallback.url)
                examPost = null
            },
        )
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
            widgetOpacity = homeWidgetOpacity,
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
            onDismiss = { showingSettings = false },
        )
    }

    syncError?.let { message ->
        OneUiAlertDialog(
            onDismissRequest = { syncError = null },
            title = "동기화 실패",
            message = message,
        )
    }
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
    studentGrade: Int,
    onStudentGradeChange: (Int) -> Unit,
    allergyCodes: Set<Int>,
    onAllergyCodesChange: (Set<Int>) -> Unit,
    widgetTheme: String,
    widgetOpacity: Float,
    onWidgetThemeChange: (String) -> Unit,
    onWidgetOpacityChange: (Float) -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val connected = remember { HanaCredentialStore.hasCredentials(context) }

    OneUiFullScreen(
        title = "설정",
        subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = OneUi.PagePadding, end = OneUi.PagePadding, top = 4.dp, bottom = 32.dp),
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
                        Text(
                            "학사일정에서 내 학년 면학감독만 표시합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
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
                        opacity = widgetOpacity,
                        onThemeChange = onWidgetThemeChange,
                        onOpacityChange = onWidgetOpacityChange,
                    )
                }
            }

            item {
                OneUiSectionTitle("급식")
                OneUiGroupColumn {
                    Column(Modifier.padding(horizontal = OneUi.RowPadding, vertical = 14.dp)) {
                        Text("알레르기", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "고른 알레르기가 있는 메뉴만 급식 화면과 위젯에서 ⚠ 표시로 강조합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
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
                    OneUiListItem(
                        title = "버전",
                        subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        trailing = {
                            Text(
                                "하드코딩 시간표 제거",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
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
@Composable
private fun AppNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val isDark = isSystemInDarkTheme()
    // One UI "In-App Navigation" 스타일 — 반투명 알약 바 위에, 선택된 항목만 그 안에서
    // 자기 자리에 캡슐형 배경이 켜지는 형태입니다 (바 위로 아이콘이 떠오르지 않습니다).
    val barColor = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    val selectedCapsuleColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f)
    val activeTint = if (isDark) Color.White else Color(0xFF1A1A1C)
    val inactiveTint = if (isDark) Color(0xFFA3A3AD) else Color(0xFF8E8E93)

    val labels = listOf("오늘", "주", "급식", "학사")

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = barColor,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(64.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.indices.forEach { index ->
                val isSelected = index == selected
                val capsuleColor by animateColorAsState(
                    targetValue = if (isSelected) selectedCapsuleColor else Color.Transparent,
                    label = "navCapsuleColor",
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(capsuleColor)
                        .clickable(onClick = { onSelect(index) })
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
    }
}

@Composable
private fun NavIcon(index: Int, tint: Color) {
    when (index) {
        0 -> Icon(Icons.Outlined.Home, contentDescription = "오늘", tint = tint)
        1 -> Icon(Icons.Outlined.DateRange, contentDescription = "주", tint = tint)
        2 -> Icon(painter = painterResource(R.drawable.ic_meal), contentDescription = "급식", tint = tint)
        else -> Icon(painter = painterResource(R.drawable.ic_academic), contentDescription = "학사", tint = tint)
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
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OneUiChip(selected = weekMode, onClick = onToggleWeek, label = "주간 보기")
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
                        OneUiChip(
                            selected = date == selectedDate,
                            onClick = { onSelectDate(date) },
                            label = shortMealDate(date) + if (hasData) "" else " ·",
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
private fun WeekTimetable(today: LocalDate, revision: Int, modifier: Modifier = Modifier) {
    val todayDow = today.dayOfWeek.value
    val dayLabels = listOf("월", "화", "수", "목", "금")
    // revision 이 바뀌면 포털에서 받은 시간표로 다시 그립니다. 교시 목록과 칸 내용을
    // 같은 스냅샷에서 함께 뽑아야, 그 사이 백그라운드 동기화가 새 표를 설치해도
    // 머리글은 옛 교시인데 칸은 새 표를 보여주는 식으로 어긋나지 않습니다.
    val (periodTimes, lessons) = remember(revision) { Timetable.activePeriodTimes to Timetable.activeLessons }
    val periods = remember(periodTimes) { periodTimes.keys.sorted() }
    val timeWidth = 52.dp
    val rowHeight = 66.dp
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
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            .padding(6.dp),
                    ) {
                        if (lesson != null) {
                            Column {
                                Text(
                                    lesson.subject,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (lesson.isFree) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                lesson.room?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
@Composable
private fun AccentPickerRow(selectedArgb: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = OneUi.RowPadding, vertical = 14.dp)) {
        Text("강조 색", style = MaterialTheme.typography.bodyLarge)
        Text(
            if (selectedArgb == PlanStore.AUTO_ACCENT_COLOR) "자동 (One UI 파란색)" else "직접 선택",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
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
        }
    }
}

/** 원형 색 스와치 — auto 면 One UI 파란색→흰색 그라디언트로 프리셋과 구분합니다. */
@Composable
private fun AccentSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    color: Color? = null,
    auto: Boolean = false,
) {
    val fill = when {
        color != null -> Modifier.background(color)
        auto -> Modifier.background(
            Brush.linearGradient(listOf(OneUi.Blue, Color(0xFF9EC1FF))),
        )
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

/** 홈 위젯의 테마(라디오 행)와 배경 불투명도(슬라이더 행) — 그룹 컨테이너 안에 들어갑니다. */
@Composable
private fun WidgetSettingsSection(
    theme: String,
    opacity: Float,
    onThemeChange: (String) -> Unit,
    onOpacityChange: (Float) -> Unit,
) {
    PlanStore.themes.forEach { option ->
        OneUiRadioRow(
            selected = theme == option,
            label = PlanStore.themeLabel(option),
            onClick = { onThemeChange(option) },
        )
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
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        OneUiTextField(
            value = memPwd,
            onValueChange = { memPwd = it },
            label = "비밀번호",
            visualTransformation = PasswordVisualTransformation(),
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
