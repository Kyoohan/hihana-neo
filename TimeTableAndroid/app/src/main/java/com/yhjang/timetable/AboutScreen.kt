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
