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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
 * 아래: 회색 알약 두 개(변경 사항 / 오픈소스 라이선스).
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
                    // 개발자 모드 — 이 버전 글씨를 10번 누르면 켜집니다 (설정 → 정보 아래에 '개발자' 섹션이 생김).
                    var versionTaps by remember { mutableStateOf(0) }
                    var devEnabled by remember { mutableStateOf(DeveloperMode.isEnabled(context)) }
                    Text(
                        when {
                            devEnabled -> "버전 ${BuildConfig.VERSION_NAME} · 개발자 모드"
                            versionTaps in 3..9 -> "버전 ${BuildConfig.VERSION_NAME} · ${10 - versionTaps}번 더"
                            else -> "버전 ${BuildConfig.VERSION_NAME}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (devEnabled) return@clickable
                            versionTaps++
                            if (versionTaps >= 10) {
                                DeveloperMode.setEnabled(context, true)
                                devEnabled = true
                            }
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    UpdateStatusLine(updateState)
                    Spacer(Modifier.height(20.dp))
                    UpdateActionButton(updateState, onCheckUpdate, onInstallUpdate)
                }

                // 아래: 회색 알약 두 개
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BottomPillButton("변경 사항") { showChangelog = true }
                    BottomPillButton("오픈소스 라이선스") { showLicenses = true }
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
