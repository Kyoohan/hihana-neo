package com.yhjang.timetable

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiGroup
import com.yhjang.timetable.ui.OneUiListItem
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiSectionTitle
import com.yhjang.timetable.ui.floatingPill
import com.yhjang.timetable.ui.isDark
import com.yhjang.timetable.ui.oneUiPageBackground
import androidx.compose.ui.graphics.Color

/** 정보 화면에서 보여줄 업데이트 상태 — [MainActivity] 가 들고 있고 정보 화면은 그리기만 합니다. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * '하이하나 Neo 정보' — 삼성 갤러리의 정보 화면과 같은 구성입니다.
 * 위: 뒤로가기 / ⓘ(시스템 앱 정보). 가운데: 앱 이름, 버전, 상태 문구, 파란 '업데이트' 알약.
 * 아래: 회색 알약 세 개(변경 사항 / 오픈소스 라이선스 / 법적 고지).
 */
@Composable
internal fun AppInfoScreen(
    updateState: UpdateState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var showChangelog by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showLegal by remember { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
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

        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(Modifier.fillMaxSize().oneUiPageBackground().padding(top = statusTop, bottom = navBottom)) {
                // 상단: 뒤로가기 + 시스템 앱 정보
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { AppUpdater.openAppInfo(context) }) {
                        Icon(Icons.Default.Info, contentDescription = "앱 정보")
                    }
                }

                // 가운데: 이름 · 버전 · 상태 · 업데이트 버튼 (갤러리처럼 화면 위쪽 1/3 지점에 둡니다)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 150.dp, start = 32.dp, end = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        context.getString(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "버전 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    UpdateStatusLine(updateState)
                    Spacer(Modifier.height(20.dp))
                    UpdateActionButton(updateState, onCheckUpdate, onInstallUpdate)
                }

                // 아래: 회색 알약 세 개
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BottomPillButton("변경 사항") { showChangelog = true }
                    BottomPillButton("오픈소스 라이선스") { showLicenses = true }
                    BottomPillButton("법적 고지") { showLegal = true }
                }
            }
        }
    }

    if (showChangelog) {
        ChangelogScreen(
            pending = (updateState as? UpdateState.Available)?.info
                ?: (updateState as? UpdateState.Downloading)?.info,
            onDismiss = { showChangelog = false },
        )
    }
    if (showLicenses) {
        LicensesScreen(onDismiss = { showLicenses = false })
    }
    if (showLegal) {
        LegalNoticeScreen(onDismiss = { showLegal = false })
    }
}

@Composable
private fun UpdateStatusLine(state: UpdateState) {
    val scheme = MaterialTheme.colorScheme
    val (text, color) = when (state) {
        UpdateState.Idle -> "최신 버전을 확인하세요." to scheme.onSurfaceVariant
        UpdateState.Checking -> "업데이트를 확인하는 중…" to scheme.onSurfaceVariant
        UpdateState.UpToDate -> "최신 버전이 설치되어 있습니다." to scheme.onSurfaceVariant
        is UpdateState.Available -> "새 버전 ${state.info.versionName}을(를) 설치할 수 있습니다." to scheme.primary
        is UpdateState.Downloading -> (
            if (state.progress < 0f) "다운로드 중…"
            else "다운로드 중… ${(state.progress * 100).toInt()}%"
            ) to scheme.onSurfaceVariant
        is UpdateState.Error -> state.message to scheme.error
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center)
}

/** 파란 알약 — 새 버전이 있으면 '업데이트', 아니면 '업데이트 확인'. 확인/다운로드 중엔 스피너와 함께 비활성. */
@Composable
private fun UpdateActionButton(state: UpdateState, onCheck: () -> Unit, onInstall: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    val available = state is UpdateState.Available
    val label = when {
        state is UpdateState.Downloading -> "다운로드 중"
        state is UpdateState.Checking -> "확인 중"
        available -> "업데이트"
        else -> "업데이트 확인"
    }
    Row(
        modifier = Modifier
            .widthIn(min = 200.dp)
            .alpha(if (busy) 0.6f else 1f)
            .clip(CircleShape)
            .background(scheme.primary)
            .clickable(enabled = !busy) { if (available) onInstall() else onCheck() }
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            OneUiLoading(size = 16.dp, stroke = 2.dp, color = scheme.onPrimary)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
    }
}

/** 갤러리 정보 화면 아래의 넓은 회색 알약 버튼 */
@Composable
private fun BottomPillButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(260.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.floatingPill)
            .clickable(onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** 변경 사항 — 새 버전이 있으면 그 릴리스 노트를 맨 위에, 그 아래 설치된/이전 버전들의 내역. */
@Composable
private fun ChangelogScreen(pending: UpdateInfo?, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    OneUiFullScreen(title = "변경 사항", onDismiss = onDismiss) { toolbar ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OneUi.PagePadding,
                end = OneUi.PagePadding,
                top = toolbar.calculateTopPadding() + 4.dp,
                bottom = toolbar.calculateBottomPadding() + 32.dp,
            ),
        ) {
            if (pending != null) {
                item {
                    OneUiSectionTitle("새 버전 ${pending.versionName}")
                    OneUiCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            pending.notes.ifBlank { "릴리스 노트가 없습니다." },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            CHANGELOG.forEach { entry ->
                item(key = entry.version) {
                    val current = entry.version == BuildConfig.VERSION_NAME
                    OneUiSectionTitle(if (current) "${entry.version} (설치됨)" else entry.version)
                    OneUiCard(modifier = Modifier.fillMaxWidth()) {
                        entry.items.forEachIndexed { index, line ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (index != entry.items.lastIndex) Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 오픈소스 라이선스 — 항목을 탭하면 프로젝트 페이지를 브라우저로 엽니다. */
@Composable
private fun LicensesScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onDismiss)
    OneUiFullScreen(title = "오픈소스 라이선스", onDismiss = onDismiss) { toolbar ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OneUi.PagePadding,
                end = OneUi.PagePadding,
                top = toolbar.calculateTopPadding() + 12.dp,
                bottom = toolbar.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item {
                OneUiGroup(items = OSS_LIBRARIES) { lib ->
                    OneUiListItem(
                        title = lib.name,
                        subtitle = lib.license,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 법적 고지의 한 단락 — 제목과 글머리 문장들. */
private data class LegalSection(val title: String, val items: List<String>)

/**
 * 법적 고지 — 비공식 앱 안내, 생성형 AI 이용 고지(인공지능기본법 제31조: 생성형 AI 기반 서비스임을 미리 알리고
 * 결과물이 AI 생성물임을 표시), 개인정보·계정 처리, 데이터 출처, 책임의 한계.
 * 앱이 실제로 하는 일(보내는 곳, 저장하는 것)이 바뀌면 이 문구도 함께 고쳐야 합니다.
 */
private val LEGAL_SECTIONS = listOf(
    LegalSection(
        "비공식 앱 안내",
        listOf(
            "하이하나 Neo는 하나고등학교 학생들의 편의를 위한 비공식 앱이며, 하나고등학교나 하이하나 포털 등 학교 공식 시스템과 제휴하거나 승인받은 앱이 아닙니다.",
            "'하나고등학교', '하이하나' 등의 명칭과 관련 권리는 각 권리자에게 있습니다.",
            "시간표·급식·학사일정·게시글·신청 결과 등은 학교 시스템에서 받아 정리해 보여 주는 것이라 원본과 다르거나 늦게 반영될 수 있습니다. 중요한 일정과 신청·예약 결과는 반드시 공식 시스템에서 확인해 주세요.",
        ),
    ),
    LegalSection(
        "생성형 AI 이용 안내",
        listOf(
            "게시판의 'AI 요약'(목록의 한 줄 요약, 게시글 위의 자세한 요약, 새 글 알림의 요약)은 Google Gemma 기반 생성형 인공지능이 만든 결과물입니다.",
            "AI 요약은 참고용이며, 부정확하거나 원문의 의도와 다를 수 있습니다. 중요한 내용은 반드시 원문을 확인해 주세요.",
            "요약은 Cloudflare Workers AI에서 만들어집니다. 이때 게시글의 제목·본문과 본문 이미지(포털에 올라온 파일)만 요약 서버로 보내며, 계정 정보나 학번 같은 개인정보는 보내지 않습니다.",
            "만들어진 요약은 같은 글을 여는 다른 사용자와 함께 쓰기 위해 요약 서버에 최대 180일 동안 보관됩니다.",
            "게시글 요약 외의 기능(시간표, 급식, 신청 등)에는 생성형 AI를 쓰지 않습니다.",
        ),
    ),
    LegalSection(
        "개인정보와 계정",
        listOf(
            "하이하나 계정의 아이디·비밀번호는 이 기기 안에 암호화되어 저장되며, 로그인할 때 학교 포털(hh.hana.hs.kr)로만 전송됩니다. 개발자에게 전송되거나 수집되지 않습니다.",
            "심야면학 로그인 정보는 심야면학 신청 서비스로만 전송되고, 기기에는 로그인 유지를 위한 정보만 암호화되어 저장됩니다.",
            "계정 정보는 기기 백업에 포함되지 않습니다. 앱을 지우면 함께 삭제됩니다.",
            "앱에는 광고나 사용자 추적·분석 도구가 없습니다.",
        ),
    ),
    LegalSection(
        "데이터 출처",
        listOf(
            "시간표·학사일정·게시판·신청·도서관: 하이하나 포털(hh.hana.hs.kr)",
            "급식 식단: 하나고등학교 홈페이지(www.hana.hs.kr)",
            "급식 칼로리·영양성분·원산지: 나이스 교육정보 개방 포털(open.neis.go.kr) 공공데이터",
            "앱 업데이트 확인: GitHub",
        ),
    ),
    LegalSection(
        "책임의 한계",
        listOf(
            "이 앱은 개인이 무료로 제공하며, 모든 기능이 항상 정확하게 동작함을 보증하지 않습니다.",
            "앱에 표시된 정보만 믿어 생긴 신청 누락이나 일정 착오 등에 대해서는 책임지기 어렵습니다. 학교 공식 안내를 우선해 주세요.",
        ),
    ),
)

@Composable
private fun LegalNoticeScreen(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    OneUiFullScreen(title = "법적 고지", onDismiss = onDismiss) { toolbar ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OneUi.PagePadding,
                end = OneUi.PagePadding,
                top = toolbar.calculateTopPadding() + 4.dp,
                bottom = toolbar.calculateBottomPadding() + 32.dp,
            ),
        ) {
            LEGAL_SECTIONS.forEach { section ->
                item(key = section.title) {
                    OneUiSectionTitle(section.title)
                    OneUiCard(modifier = Modifier.fillMaxWidth()) {
                        section.items.forEachIndexed { index, line ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (index != section.items.lastIndex) Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
            item {
                Text(
                    "하이하나 Neo ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = OneUi.RowPadding, top = 20.dp),
                )
            }
        }
    }
}

// MARK: - 실행 시 큰 버전 업데이트 안내

/** '나중에'를 누른 버전은 하루 동안 다시 묻지 않습니다. */
object UpdatePromptStore {
    private const val PREFS = "update_prompt"
    private const val SNOOZE_MS = 24 * 60 * 60 * 1000L

    fun dismissedRecently(context: android.content.Context, version: String): Boolean {
        val at = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getLong("dismissed_$version", 0L)
        return System.currentTimeMillis() - at < SNOOZE_MS
    }

    fun dismiss(context: android.content.Context, version: String) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putLong("dismissed_$version", System.currentTimeMillis()).apply()
    }
}

/**
 * 릴리스 노트(마크다운)에서 항목 줄만 뽑아 앞 [max]개 — 글머리 기호·강조 표시는 벗깁니다.
 * 릴리스 노트가 비면 앱 안의 변경 사항에서 그 버전 항목을 대신 씁니다.
 */
private fun updateHighlights(info: UpdateInfo, max: Int = 3): List<String> {
    val fromNotes = info.notes.lines()
        .map { it.trim() }
        .filter { it.startsWith("-") || it.startsWith("*") || it.startsWith("•") }
        .map { it.trimStart('-', '*', '•', ' ').replace("**", "").replace("`", "") }
        .filter { it.isNotBlank() && !it.startsWith("Full Changelog", ignoreCase = true) }
    val picked = if (fromNotes.isNotEmpty()) fromNotes else CHANGELOG.firstOrNull { it.version == info.versionName }?.items.orEmpty()
    return picked.take(max)
}

/**
 * 앱 실행 시 큰 버전(7.x → 8.0) 업데이트를 한 번 권하는 안내 — '중요' 배지, 권장 문구, 이번 버전 요약,
 * 스토어를 거치지 않는 설치 절차 3단계. 버튼은 One UI 다이얼로그 규칙대로 아래 반반: 나중에 / 업데이트.
 */
@Composable
internal fun MajorUpdateDialog(info: UpdateInfo, onLater: () -> Unit, onUpdate: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val highlights = updateHighlights(info)
    com.yhjang.timetable.ui.OneUiDialog(
        onDismissRequest = onLater,
        title = "업데이트를 권장합니다",
        buttons = listOf(
            com.yhjang.timetable.ui.OneUiDialogButton("나중에", onLater),
            com.yhjang.timetable.ui.OneUiDialogButton("업데이트", onUpdate),
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.yhjang.timetable.ui.OneUiBadge(
                "중요",
                container = Color(0xFFFFE3B8),
                content = Color(0xFF6B3A00),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "새 버전 ${info.versionName}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "새 버전 ${info.versionName}에 중대한 버그 수정과 개선이 포함되어 있어 업데이트를 추천합니다. 설치해도 설정과 계정은 그대로 유지됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface.copy(alpha = 0.9f),
        )
        if (highlights.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(OneUi.CornerMedium))
                    .background(scheme.surfaceContainerHigh.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("이번 버전", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                highlights.forEach { line ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Text("✓", style = MaterialTheme.typography.bodySmall, color = scheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        InstallStepsBox()
    }
}

/** 설치 절차 3단계 — 실행 시 안내와 '업데이트' 버튼 앞 확인 창이 같은 내용을 씁니다. */
@Composable
private fun InstallStepsBox() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(OneUi.CornerMedium))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("설치 방법", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        InstallStep(1, "다운로드가 끝나면 설치 창이 열립니다 → 설치")
        InstallStep(2, "\"Play 프로텍트\" 경고가 뜨면 세부정보 더보기 → 검사 없이 설치")
        InstallStep(3, "설치가 끝나면 열기")
    }
}

/**
 * 정보 화면에서 '업데이트'를 눌렀을 때, 시스템 설치 창이 뜨기 전에 한 번 보여주는 설치 안내.
 * '확인'을 눌러야 다운로드·설치로 넘어갑니다.
 */
@Composable
internal fun InstallGuideDialog(info: UpdateInfo, onCancel: () -> Unit, onConfirm: () -> Unit) {
    com.yhjang.timetable.ui.OneUiDialog(
        onDismissRequest = onCancel,
        title = "업데이트 설치",
        buttons = listOf(
            com.yhjang.timetable.ui.OneUiDialogButton("취소", onCancel),
            com.yhjang.timetable.ui.OneUiDialogButton("확인", onConfirm),
        ),
    ) {
        Text(
            "새 버전 ${info.versionName}을(를) 받아 설치합니다. 설정과 계정은 그대로 유지됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(12.dp))
        InstallStepsBox()
    }
}

@Composable
private fun InstallStep(number: Int, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .width(20.dp)
                .height(20.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
