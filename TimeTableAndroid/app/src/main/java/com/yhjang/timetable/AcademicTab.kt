package com.yhjang.timetable

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiButtonStyle
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiGroup
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiSectionTitle
import com.yhjang.timetable.ui.OneUiTextButton
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** 자격증명이 없거나 로그인 실패로 학사 데이터를 못 받을 때의 센티넬 — UI 가 연동 버튼을 띄웁니다. */
const val ACADEMIC_NEEDS_LOGIN = "학사시스템 연동 필요"

internal fun scheduleColor(code: String): Color = when (code) {
    "SG01010000" -> OneUi.Blue // 학사일정
    "SG02010000" -> Color(0xFF34C759) // 하나일정
    "SG01020000" -> Color(0xFFFF3B30) // 공휴일
    SUPERVISION_CODE -> Color(0xFFFF9500) // 면학감독
    "SG02020000" -> Color(0xFF8E8E93) // 시설사용
    else -> Color(0xFF5856D6)
}

private fun scheduleLabel(code: String): String = when (code) {
    "SG01010000" -> "학사일정"
    "SG02010000" -> "하나일정"
    "SG01020000" -> "공휴일"
    SUPERVISION_CODE -> "면학감독"
    "SG02020000" -> "시설사용"
    else -> "일정"
}

internal fun alimUrl(id: String): String = "https://hh.hana.hs.kr/main/mypage_alert/received.do?aimIdx=$id"

internal fun shortDate(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.KOREAN)
    return "${date.monthValue}/${date.dayOfMonth} ($dow)"
}

/** 날짜 그룹 헤더 — "9월 17일 (목)" */
private fun dateHeader(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.KOREAN)
    return "${date.monthValue}월 ${date.dayOfMonth}일 ($dow)"
}

/** st_time/ed_time 은 "2026-09-17 09:00" 처럼 날짜가 앞에 붙어 올 수 있어 시각만 뽑습니다. */
private fun scheduleTime(entry: HanaScheduleEntry): String? {
    val start = entry.startTime?.substringAfter(' ', entry.startTime)
    val end = entry.endTime?.substringAfter(' ', entry.endTime)
    return when {
        start != null && end != null -> "$start~$end"
        else -> start ?: end
    }
}

/** 학사 탭 — 학사일정 / 게시판 두 하위 탭을 한 화면에서 전환합니다. */
@Composable
fun AcademicTab(
    subTab: Int,
    onSubTabChange: (Int) -> Unit,
    schedule: List<HanaScheduleEntry>,
    scheduleLoading: Boolean,
    scheduleError: String?,
    onRetrySchedule: () -> Unit,
    studentGrade: Int,
    boardCategoryIndex: Int,
    onBoardCategoryChange: (Int) -> Unit,
    boardPosts: List<HanaBoardPost>,
    boardLoading: Boolean,
    boardError: String?,
    onRetryBoard: () -> Unit,
    onOpenPost: (HanaBoardPost) -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenAccount: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 게시판이 기본 탭이라 맨 앞에 둡니다 (인덱스는 저장 호환을 위해 그대로: 0=학사일정, 1=게시판, 2=신청·내역).
                OneUiChip(selected = subTab == 1, onClick = { onSubTabChange(1) }, label = "게시판")
                OneUiChip(selected = subTab == 0, onClick = { onSubTabChange(0) }, label = "학사일정")
                OneUiChip(selected = subTab == 2, onClick = { onSubTabChange(2) }, label = "신청·내역")
            }
        }

        if (subTab == 0) {
            if (scheduleError != null) {
                item { AcademicErrorBlock(scheduleError, onRetrySchedule, onOpenAccount) }
            } else {
                val today = PlanStore.today()
                val visible = filterSupervisionByGrade(schedule, studentGrade)
                    .filter { it.code != "SG02020000" } // 시설사용은 학생에게 숨김
                    .filter { !it.date.isBefore(today) }
                    .sortedWith(compareBy({ it.date }, { it.name }))

                examDday(schedule, today)?.let { exam ->
                    item { ExamDdayCard(exam) }
                }

                if (scheduleLoading && visible.isEmpty()) {
                    item { LoadingBlock() }
                } else if (visible.isEmpty()) {
                    item { ScheduleEmptyBlock(onRetrySchedule) }
                }

                visible.groupBy { it.date }.forEach { (date, entries) ->
                    item(key = "schedule-header-$date") { ScheduleDateHeader(date) }
                    item(key = "schedule-group-$date") {
                        GroupedContainer(entries) { entry -> ScheduleRow(entry) }
                    }
                }
            }
        } else if (subTab == 1) {
            val category = BoardCategory.entries[boardCategoryIndex]
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BoardCategory.entries.forEachIndexed { index, item ->
                        OneUiChip(
                            selected = index == boardCategoryIndex,
                            onClick = { onBoardCategoryChange(index) },
                            label = item.label,
                        )
                    }
                }
            }

            if (boardError != null) {
                item { AcademicErrorBlock(boardError, onRetryBoard, onOpenAccount) }
            } else {
                if (boardLoading && boardPosts.isEmpty()) {
                    item { LoadingBlock() }
                } else if (boardPosts.isEmpty()) {
                    item { EmptyBlock("${category.label} 게시글이 없습니다") }
                }
                if (boardPosts.isNotEmpty()) {
                    item(key = "board-group") {
                        GroupedContainer(boardPosts) { post -> BoardRow(post, onOpenPost) }
                    }
                }
            }
        } else {
            item {
                ApplyHistorySection(onOpenWeb = onOpenWeb)
            }
        }
    }
}

@Composable
private fun ExamDdayCard(exam: ExamDday) {
    // 시험 D-day 는 강조색 배경의 카드 하나로 눈에 띄게 — One UI 캘린더의 "다음 일정" 배너와 같은 톤입니다.
    OneUiCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    exam.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    "${shortDate(exam.date)} · ${exam.days}일 남음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
            Text(
                if (exam.days == 0L) "D-Day" else "D-${exam.days}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ScheduleDateHeader(date: LocalDate) {
    OneUiSectionTitle(dateHeader(date))
}

/** One UI "Containers" 스타일 — 같은 날짜의 행들을 각자 카드로 나누지 않고, 한 컨테이너
 * 안에 얇은 구분선으로만 나눕니다. 이 컴포저블 자체는 행 내용만 그리고, 컨테이너·구분선은
 * 호출부(같은 날짜로 묶은 곳)에서 담당합니다. */
@Composable
private fun ScheduleRow(entry: HanaScheduleEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(scheduleColor(entry.code)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(scheduleTitle(entry), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            val meta = buildList {
                if (!entry.allDay) scheduleTime(entry)?.let { add(it) }
                entry.place?.let { add(it) }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            scheduleLabel(entry.code),
            style = MaterialTheme.typography.labelSmall,
            color = scheduleColor(entry.code),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** [ScheduleRow]/[BoardRow] 처럼 컨테이너 없이 내용만 그리는 행들을 One UI 그룹 컨테이너로 묶습니다. */
@Composable
private fun <T> GroupedContainer(items: List<T>, row: @Composable (T) -> Unit) {
    OneUiGroup(items = items, modifier = Modifier.padding(vertical = 4.dp), row = row)
}

/**
 * 면학감독은 일정명 대신 담당 교사를 보여줍니다 — 학년 접두사(`2.`)는 떼고 이름만 씁니다.
 * 교사명이 없는 예외 응답은 일정명으로 폴백합니다.
 */
internal fun scheduleTitle(entry: HanaScheduleEntry): String {
    if (entry.code != SUPERVISION_CODE) return entry.name
    return entry.displayTeacher ?: entry.name
}

@Composable
private fun BoardRow(post: HanaBoardPost, onOpenPost: (HanaBoardPost) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPost(post) }
            .padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                post.title.ifEmpty { "(제목 없음)" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (post.isNew) {
                Spacer(Modifier.width(6.dp))
                Text("N", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.grades.isNotEmpty()) {
                Text(
                    "학년 ${post.grades.joinToString(",")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                listOfNotNull(post.writer, post.date).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 앱 내 웹뷰로 열 페이지 — 게시글·신청·내역 페이지가 공유합니다. */
data class HanaWebPage(
    val url: String,
    val title: String,
    /** 신청·내역처럼 포털 상태를 바꾸는 페이지면 닫을 때 면학 배정을 다시 받아옵니다. */
    val resyncOnClose: Boolean = false,
)

@Composable
private fun AcademicErrorBlock(error: String, onRetry: () -> Unit, onOpenAccount: () -> Unit) {
    OneUiCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            if (error == ACADEMIC_NEEDS_LOGIN) ACADEMIC_NEEDS_LOGIN else "불러오지 못했습니다",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (error == ACADEMIC_NEEDS_LOGIN) "하이하나 계정을 등록하면 학사일정과 게시판을 볼 수 있습니다." else error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        if (error == ACADEMIC_NEEDS_LOGIN) {
            OneUiButton(text = "계정 등록", onClick = onOpenAccount)
        } else {
            OneUiButton(text = "다시 시도", onClick = onRetry, style = OneUiButtonStyle.Neutral)
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        OneUiLoading(size = 28.dp, stroke = 3.dp)
    }
}

@Composable
private fun EmptyBlock(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 32.dp),
    )
}

/** 학사일정이 비었을 때 — 포털 파라미터가 안 맞을 수 있으니 다시 시도 버튼을 함께 둡니다. */
@Composable
private fun ScheduleEmptyBlock(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
        Text(
            "예정된 학사일정이 없습니다",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "학사일정이 계속 비어 있으면 다시 시도해 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OneUiButton(text = "다시 시도", onClick = onRetry, style = OneUiButtonStyle.Neutral)
    }
}

/** 알리미 전체 화면 — 상단 앱바 종 아이콘에서 열립니다. 탭하면 읽음 처리 후 포털 웹으로 이동합니다. */
@Composable
fun AlimScreen(
    alims: List<HanaAlim>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenAlim: (HanaAlim) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasUnread = alims.any { !it.read }
    val unreadCount = alims.count { !it.read }
    OneUiFullScreen(
        title = "알리미",
        subtitle = if (unreadCount > 0) "안 읽음 $unreadCount" else null,
        onDismiss = onDismiss,
        actions = {
            IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "새로고침") }
        },
    ) { toolbar ->
        val top = toolbar.calculateTopPadding()
        when {
            error != null -> Box(Modifier.padding(start = OneUi.PagePadding, end = OneUi.PagePadding, top = top)) {
                AcademicErrorBlock(error, onRetry, onOpenAccount)
            }
            isLoading && alims.isEmpty() -> Box(Modifier.padding(top = top)) { LoadingBlock() }
            alims.isEmpty() -> Box(Modifier.padding(start = OneUi.PagePadding, end = OneUi.PagePadding, top = top)) { EmptyBlock("알리미가 없습니다") }
            // 알리미는 항목마다 카드를 따로 두지 않고 One UI 메시지 목록처럼 한 컨테이너에 행으로 쌓습니다.
            else -> LazyColumn(contentPadding = PaddingValues(start = OneUi.PagePadding, end = OneUi.PagePadding, top = top + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OneUiTextButton(
                            text = "모두 읽음",
                            onClick = onMarkAllRead,
                            enabled = hasUnread,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item(key = "alim-group") {
                    OneUiGroup(items = alims) { alim -> AlimRow(alim) { onOpenAlim(alim) } }
                }
            }
        }
    }
}

@Composable
private fun AlimRow(alim: HanaAlim, onClick: () -> Unit) {
    val unread = !alim.read
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 안 읽은 항목은 왼쪽에 강조색 점 — 읽으면 점 자리는 비워 두어 텍스트 정렬이 흔들리지 않게 합니다.
        Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
            if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                alim.content.ifEmpty { "(내용 없음)" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val meta = listOfNotNull(alim.writer, alim.date).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/**
 * 게시글 상세 — in-app WebView. HanaPortalClient 의 OkHttp 세션 쿠키를
 * android.webkit.CookieManager 로 복사한 뒤 로드해야 로그인된 페이지가 뜹니다.
 * 실패하면 "브라우저로 열기" 폴백을 제공합니다.
 */
@Composable
fun BoardDetailScreen(
    url: String,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit,
    title: String = "게시글",
) {
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // 화면 뼈대는 바로 그리고, 세션 확인(대개 즉시 통과)이 끝난 뒤에야 웹뷰를 붙여 쿠키를 복사합니다 —
    // 이걸 화면을 열기 전에 하면 열리기까지 1초 가까이 멈춰 보였습니다.
    var sessionReady by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(url) {
        HanaPortalClient.get().ensureLoggedIn(context)
        sessionReady = true
    }

    BackHandler(onBack = onDismiss)

    OneUiFullScreen(
        title = title,
        onDismiss = onDismiss,
        actions = {
            OneUiTextButton(text = "브라우저로 열기", onClick = onOpenBrowser, color = MaterialTheme.colorScheme.primary)
        },
    ) { toolbar ->
        if (failed) {
            Box(Modifier.padding(toolbar).padding(OneUi.PagePadding)) {
                OneUiCard(modifier = Modifier.fillMaxWidth()) {
                    Text("게시글을 불러오지 못했습니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "포털 로그인 세션이 만료됐거나 페이지가 웹뷰를 막고 있을 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    OneUiButton(text = "브라우저로 열기", onClick = onOpenBrowser)
                }
            }
        } else {
            // 웹 페이지는 흰 바탕이라 회색 페이지 배경 위에서 카드처럼 위쪽만 둥글게 잘라 얹습니다.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(toolbar)
                    .padding(horizontal = OneUi.PagePadding)
                    .clip(RoundedCornerShape(topStart = OneUi.CornerLarge, topEnd = OneUi.CornerLarge))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (sessionReady) AndroidView(
                    factory = { ctx ->
                        // loadUrl 전에 쿠키를 심어야 인증된 페이지가 뜹니다.
                        HanaPortalClient.get().syncCookiesToWebView()
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // 포털이 세션을 UA 에 묶을 수 있어 OkHttp 와 같은 UA 로 맞춥니다.
                            settings.userAgentString = HanaPortalClient.UA
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    // 웹뷰가 받은 최신 세션 쿠키를 앱 쪽으로 되가져와 다음 게시글도 같은 세션으로 엽니다.
                                    HanaPortalClient.get().syncCookiesFromWebView()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        failed = true
                                        loading = false
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loading = newProgress < 100
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }
    }
}
