package com.yhjang.timetable

import android.content.Context
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiDialog
import com.yhjang.timetable.ui.OneUiDialogButton
import com.yhjang.timetable.ui.OneUiDivider
import com.yhjang.timetable.ui.OneUiGroupColumn
import com.yhjang.timetable.ui.OneUiListItem
import com.yhjang.timetable.ui.OneUiSectionTitle

/**
 * Dev 탭 (디버그 빌드 전용) — 새로 넣은 기능을 조건 없이 바로 열어 볼 수 있는 버튼 모음.
 * 설치·업데이트 직후에만 뜨는 안내, 특정 시간에만 뜨는 알림 같은 것을 기다리지 않고 확인하는 용도입니다.
 */
@Composable
fun DevTab(
    contentPadding: PaddingValues,
    onShowExactAlarmPrompt: () -> Unit,
    onOpenSeats: (SeatService) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onRefreshLive: () -> Unit,
    onStartTour: (TourKind) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sampleDialog by remember { mutableStateOf(false) }
    var devNotice by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = OneUi.PagePadding),
    ) {
        Text(
            "디버그 빌드 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        OneUiSectionTitle("첫 실행 투어")
        OneUiGroupColumn {
            OneUiListItem(
                title = "새로 설치 투어",
                subtitle = "소개 → 개인정보 보호 → 권한 → 로그인 → 완료 (이미 허용한 권한·연동된 계정이면 그 장은 빠짐)",
                onClick = { onStartTour(TourKind.WELCOME) },
            )
            OneUiDivider()
            OneUiListItem(
                title = "업데이트 투어 (11.0 → 11.1)",
                subtitle = "바뀐 것만 — 게시판·학사 탭·귀가 기간·개인정보 보호 (+ 권한이 빠졌으면 권한)",
                onClick = { onStartTour(TourKind.UPDATE) },
            )
        }
        Spacer(Modifier.height(16.dp))

        OneUiSectionTitle("다이얼로그")
        OneUiGroupColumn {
            OneUiListItem(title = "알람 및 리마인더 권한 안내", subtitle = "설치·업데이트 직후 한 번 뜨는 그 다이얼로그", onClick = onShowExactAlarmPrompt)
            OneUiDivider()
            OneUiListItem(title = "샘플 다이얼로그", subtitle = "아래 시트 위치·블러 확인용", onClick = { sampleDialog = true })
            OneUiDivider()
            OneUiListItem(
                title = "안내 기록 초기화",
                subtitle = "권한 안내가 다음 실행에 다시 뜨도록",
                onClick = { context.getSharedPreferences("prompts", Context.MODE_PRIVATE).edit().clear().apply() },
            )
        }
        Spacer(Modifier.height(16.dp))

        OneUiSectionTitle("좌석 신청")
        OneUiGroupColumn {
            OneUiListItem(title = "면학실 좌석 화면", onClick = { onOpenSeats(SeatService.STUDY_ROOM) })
            OneUiDivider()
            OneUiListItem(title = "도서관 좌석 화면", onClick = { onOpenSeats(SeatService.LIBRARY) })
            OneUiDivider()
            OneUiListItem(
                title = "포털 페이지 JS 조사 → 로그",
                subtitle = "study-apply.do / library-apply.do 를 HTML 로 받아 기기 등록 코드 주변을 HanaDiscover 로그에",
                onClick = {
                    scope.launch {
                        HanaApplyApi.dumpPageScript(context, "/main/studyroom/study-apply.do")
                        HanaApplyApi.dumpPageScript(context, "/main/library/library-apply.do")
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        // 학번·이름 — 도서관 좌석은 이름을 비워 주므로 기기에 저장한 표로 채웁니다 (dev 빌드 전용, 릴리스는 학번만).
        OneUiSectionTitle("학번·이름")
        var nameCount by remember { mutableStateOf(StudentNameCache.count(context)) }
        val pickNames = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                }
                devNotice = if (text == null) {
                    "파일을 읽지 못했습니다"
                } else {
                    val added = StudentNameCache.importText(context, text)
                    nameCount = StudentNameCache.count(context)
                    if (added == 0) "학번·이름 쌍을 찾지 못했습니다" else "$added 명 불러옴 (총 $nameCount 명)"
                }
            }
        }
        OneUiGroupColumn {
            OneUiListItem(
                title = "학번·이름 파일 불러오기",
                subtitle = "저장된 이름 $nameCount 명 · 디렉터리 복사 텍스트 / '학번,이름' 줄 / JSON — 이 기기에만 저장",
                onClick = { pickNames.launch(arrayOf("text/*", "application/json", "*/*")) },
            )
            OneUiDivider()
            OneUiListItem(
                title = "학번·이름 지우기",
                onClick = {
                    StudentNameCache.clear(context)
                    nameCount = 0
                    devNotice = "지웠습니다"
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        OneUiSectionTitle("실시간 일정")
        var liveTempUntil by remember { mutableStateOf(LiveActivity.tempUntil(context)) }
        OneUiGroupColumn {
            OneUiListItem(
                title = "Now Bar 30분만 켜기",
                subtitle = if (liveTempUntil > System.currentTimeMillis()) {
                    "${java.text.SimpleDateFormat("H:mm", java.util.Locale.KOREA).format(java.util.Date(liveTempUntil))} 에 저절로 꺼짐"
                } else "설정 스위치를 건드리지 않고 바로 띄우고, 30분 뒤 알람으로 끕니다",
                onClick = {
                    LiveActivity.enableTemporarily(context, 30)
                    liveTempUntil = LiveActivity.tempUntil(context)
                },
            )
            OneUiDivider()
            OneUiListItem(
                title = "알림 지금 다시 그리기",
                subtitle = "LiveActivity.update() — 켜져 있고 표시 창 안이면 Now Bar 갱신",
                onClick = onRefreshLive,
            )
            OneUiDivider()
            OneUiListItem(
                title = "심야면학 테스트 신청 넣기",
                subtitle = "오늘 심야 1타임 23:50–01:00 · 3층 5번 (다음 동기화 때 실제 신청으로 덮임)",
                onClick = { MidnightSchedule.injectTest(context); onRefreshLive() },
            )
            OneUiDivider()
            OneUiListItem(
                title = "심야면학 일정 지우기",
                onClick = { MidnightSchedule.clear(context); onRefreshLive() },
            )
            OneUiDivider()
            OneUiListItem(
                title = "실시간 일정 스위치 상태",
                subtitle = if (LiveActivity.isEnabled(context)) "켜짐" else "꺼짐",
                onClick = onOpenSettings,
            )
        }
        Spacer(Modifier.height(16.dp))

        OneUiSectionTitle("기타")
        OneUiGroupColumn {
            OneUiListItem(title = "설정 열기", onClick = onOpenSettings)
            OneUiDivider()
            OneUiListItem(title = "계정 시트 열기", onClick = onOpenAccount)
        }
        Spacer(Modifier.height(40.dp))
    }

    devNotice?.let { message ->
        OneUiDialog(
            onDismissRequest = { devNotice = null },
            title = "학번·이름",
            buttons = listOf(OneUiDialogButton("확인", { devNotice = null })),
        ) { Text(message, style = MaterialTheme.typography.bodyMedium) }
    }

    if (sampleDialog) {
        OneUiDialog(
            onDismissRequest = { sampleDialog = false },
            title = "샘플 다이얼로그",
            buttons = listOf(OneUiDialogButton("취소", { sampleDialog = false }), OneUiDialogButton("확인", { sampleDialog = false })),
        ) {
            Text("화면 아래에 시트처럼 뜨고, 뒤 화면과 시트 자체가 흐려져야 합니다.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
