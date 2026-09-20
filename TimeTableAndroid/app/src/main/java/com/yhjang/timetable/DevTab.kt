package com.yhjang.timetable

import android.content.Context
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sampleDialog by remember { mutableStateOf(false) }
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

        OneUiSectionTitle("실시간 일정")
        OneUiGroupColumn {
            OneUiListItem(
                title = "알림 지금 다시 그리기",
                subtitle = "LiveActivity.update() — 켜져 있고 표시 창 안이면 Now Bar 갱신",
                onClick = onRefreshLive,
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
