package com.skh.hihanaevo

import com.skh.hihanaevo.ui.onPageVariantColor
import android.content.Context
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiDialog
import com.skh.hihanaevo.ui.OneUiDialogButton
import com.skh.hihanaevo.ui.OneUiDivider
import com.skh.hihanaevo.ui.OneUiGroupColumn
import com.skh.hihanaevo.ui.OneUiListItem
import com.skh.hihanaevo.ui.OneUiSectionTitle

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
    /** 학사일정을 캐시 무시하고 새로 받기 — 걸린 시간을 토스트로. 시트엔 끌어내려 새로고침이 없어 확인용으로. */
    onReloadSchedule: () -> Unit = {},
    /** 설정 화면의 목록 항목 안에 들어갈 때 — 자기 스크롤 없이 내용 높이만큼. */
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showingOpening by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var sampleDialog by remember { mutableStateOf(false) }
    var devNotice by remember { mutableStateOf<String?>(null) }
    Column(
        if (embedded) Modifier.fillMaxWidth()
        else Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // contentPadding 에 이미 좌우 페이지 여백이 들어 있어 더하지 않습니다 — 두 번 받아 다른 탭보다 좁았습니다.
            .padding(contentPadding),
    ) {
        Text(
            "디버그 빌드 ${BuildConfig.VERSION_NAME} (${BuildConfig.DEV_BUILD})",
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
        )
        Spacer(Modifier.height(8.dp))

        OneUiSectionTitle("첫 실행 투어")
        OneUiGroupColumn {
            OneUiListItem(
                title = "새로 설치 투어",
                subtitle = "오프닝 → 환영 화면(유리 방울) → 개요(앱 전체 그림) → 새 조작법 → 위젯 … 개인정보 보호 → 권한 → 로그인 → 완료 (이미 허용한 권한이면 그 장은 빠짐 · dev 빌드만 건너뛰기 가능)",
                onClick = { onStartTour(TourKind.WELCOME) },
            )
            OneUiDivider()
            OneUiListItem(
                title = "업데이트 투어 (13.x → 14.0)",
                subtitle = "바뀐 것만 — 클래스룸 · 데일리 브리핑 · 새 조작법 · 홈 · 타임라인 · 한곳에 모은 일정 · 알림·요약 · 아이콘 · (배경 사진을 쓰면) 배경 고르기 · 그 밖에 (+ 권한이 빠졌으면 권한) · 클래스룸 연결",
                onClick = { onStartTour(TourKind.UPDATE) },
            )
            OneUiDivider()
            OneUiListItem(
                title = "오프닝 애니메이션만",
                subtitle = "HiHana Evo — 리본 세 갈래가 날아와 로고로 펼쳐지는 투어 첫 장면 (누르면 닫힘)",
                onClick = { showingOpening = true },
            )
        }
        if (showingOpening) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showingOpening = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) { EvoOpening(onFinish = { showingOpening = false }) }
        }
        Spacer(Modifier.height(16.dp))

        // 오늘이 귀가 기간이면 홈의 남은 일정이 비므로, 다음 평일(귀가 기간 아닌 날) 15:05 기준으로 미리 봅니다.
        OneUiSectionTitle("홈 남은 일정 미리보기")
        val previewDate = remember {
            generateSequence(PlanStore.today().plusDays(1)) { it.plusDays(1) }
                .take(30)
                .firstOrNull { it.dayOfWeek.value <= 5 && Timetable.homeStay(it) == null }
                ?: PlanStore.today()
        }
        Text(
            "${previewDate.monthValue}/${previewDate.dayOfMonth} 15:05 기준",
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
            modifier = Modifier.padding(start = OneUi.RowPadding, bottom = 6.dp),
        )
        UpcomingPreview(previewDate, previewDate.atTime(15, 5))
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

        OneUiSectionTitle("통합 일정 · 클래스룸")
        OneUiGroupColumn {
            OneUiListItem(
                title = "샘플 데이터 넣기",
                subtitle = "클래스룸을 연결한 것처럼(과목 2개, 과제·공지·자료) + 게시글 일정 2건 — 게시판 탭 클래스룸 칸, 홈·일정 탭 확인용. 앱을 다시 켜면 클래스룸 샘플은 풀림",
                onClick = { ClassroomStore.installSample(context) },
            )
            OneUiDivider()
            OneUiListItem(title = "게시글 일정 지우기", subtitle = "AI 가 뽑아 둔 일정 보관함 비우기(요약 캐시는 그대로)", onClick = { BoardAgendaStore.clear(context) })
            OneUiDivider()
            OneUiListItem(title = "학사일정 새로 받기", subtitle = "캐시를 무시하고 4주치를 다시 받아 걸린 시간과 개수를 토스트로", onClick = onReloadSchedule)
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

        // 귀가 기간 '지금' 카드의 날씨·귀교 후 첫 일정·시험 줄 — 실제 귀가 날을 기다리지 않고 오늘 예보로 미리 봅니다.
        OneUiSectionTitle("귀가 기간 미리보기")
    var devWeatherUntil by remember { mutableStateOf<java.time.LocalDateTime?>(null) }
    devWeatherUntil?.let { SeoulWeatherScreen(it, onDismiss = { devWeatherUntil = null }) }
        var homeStayPreview by remember { mutableStateOf<Pair<String, HomeStayExtras?>?>(null) }
        OneUiGroupColumn {
            listOf(
                Triple("귀가일 (오늘 19:00 이후로)", Timetable.HomeStay.LEAVE, java.time.LocalTime.of(19, 30)),
                Triple("귀가 중 (내일 귀교로)", Timetable.HomeStay.AWAY, null),
                Triple("귀교일 (오늘 귀교로)", Timetable.HomeStay.RETURN, null),
            ).forEachIndexed { index, (title, stay, at) ->
                if (index > 0) OneUiDivider()
                OneUiListItem(
                    title = title,
                    onClick = {
                        scope.launch {
                            val current = java.time.LocalDateTime.now(PlanStore.seoulZone)
                            val now = at?.let { t -> current.with(t).takeIf { it.isAfter(current) } } ?: current
                            homeStayPreview = title to previewHomeStayExtras(context, stay, now)
                        }
                    },
                )
            }
            homeStayPreview?.let { (title, extras) ->
                OneUiDivider()
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = onPageVariantColor())
                    if (extras == null) Text("보여 줄 줄이 없습니다", style = MaterialTheme.typography.bodySmall)
                    else HomeStayExtraRows(extras, onOpenWeather = { devWeatherUntil = extras.backAt })
                }
            }
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
