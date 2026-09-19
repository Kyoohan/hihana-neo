package com.yhjang.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiSectionTitle
import com.yhjang.timetable.ui.OneUiButtonStyle
import kotlinx.coroutines.launch

private const val PORTAL_BASE = "https://hh.hana.hs.kr"

// MARK: - 신청·내역

private sealed interface ApplyHistoryState {
    data object Idle : ApplyHistoryState
    data object Loading : ApplyHistoryState
    data class Rows(val rows: List<HanaApplyRow>) : ApplyHistoryState
    data class Error(val message: String) : ApplyHistoryState
}

/**
 * 신청·내역 — 서비스마다 "내역"과 "신청하기" 두 동작을 제공합니다.
 * 내역은 포털 목록 JSON 으로 앱 안에서 그리고(교과교실·면학실 확인됨), JSON 엔드포인트를 아직 모르는 서비스는
 * 조용히 포털 내역 페이지를 웹뷰로 엽니다. "신청하기"는 아직 모두 포털 신청 페이지를 웹뷰로 엽니다
 * (교과교실·외출외박은 계속 웹뷰, 면학실·도서관은 API 를 알아내면 앱 안 화면으로).
 */
@Composable
internal fun ApplyHistorySection(onOpenWeb: (url: String, title: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<ApplyService, ApplyHistoryState>() }

    // 아직 API 를 모르는 페이지의 엔드포인트를 로그로 조사합니다 (프로세스당 한 번, 실패해도 조용히).
    LaunchedEffect(Unit) { runCatching { HanaApplyApi.discoverEndpoints(context) } }

    fun openHistory(service: ApplyService) {
        onOpenWeb("$PORTAL_BASE${service.historyPath}", "${service.label} 내역")
    }

    fun loadHistory(service: ApplyService) {
        states[service] = ApplyHistoryState.Loading
        scope.launch {
            states[service] = try {
                when (val result = HanaApplyApi.fetchHistory(context, service)) {
                    is ClassroomHistory.Rows -> ApplyHistoryState.Rows(result.rows)
                    ClassroomHistory.Unsupported -> {
                        // JSON 엔드포인트를 아직 모르면 오류를 띄우는 대신 포털 내역 페이지로 넘깁니다.
                        openHistory(service)
                        ApplyHistoryState.Idle
                    }
                }
            } catch (e: HanaPortalException.MissingCredentials) {
                ApplyHistoryState.Error(ACADEMIC_NEEDS_LOGIN)
            } catch (e: Exception) {
                ApplyHistoryState.Error(e.message ?: "내역을 불러오지 못했습니다")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OneUiSectionTitle("신청·내역")
        ApplyService.entries.forEach { service ->
            ApplyServiceCard(
                service = service,
                state = states[service] ?: ApplyHistoryState.Idle,
                onHistory = { loadHistory(service) },
                onApply = { onOpenWeb("$PORTAL_BASE${service.applyPath}", service.label) },
            )
        }
    }
}

@Composable
private fun ApplyServiceCard(
    service: ApplyService,
    state: ApplyHistoryState?,
    onHistory: () -> Unit,
    onApply: () -> Unit,
) {
    // 내역 조회와 신청하기는 같은 급의 동작이라, 카드 아래에 같은 모양·같은 폭의 알약 버튼 두 개로 나란히 둡니다.
    val historyLabel = when (state) {
        null, ApplyHistoryState.Idle -> "내역 조회"
        ApplyHistoryState.Loading -> "불러오는 중"
        is ApplyHistoryState.Error -> "다시 시도"
        is ApplyHistoryState.Rows -> "새로고침"
    }
    OneUiCard(modifier = Modifier.fillMaxWidth()) {
        Text(service.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state != null && state != ApplyHistoryState.Idle) {
            Spacer(Modifier.height(10.dp))
            ClassroomHistoryBody(state = state)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OneUiButton(
                text = historyLabel,
                onClick = onHistory,
                style = OneUiButtonStyle.Neutral,
                enabled = state != ApplyHistoryState.Loading,
                modifier = Modifier.weight(1f),
            )
            OneUiButton(
                text = "신청하기",
                onClick = onApply,
                style = OneUiButtonStyle.Neutral,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 교과교실 내역 본문 — 상태별 문구/목록만 그리고, 동작 버튼은 카드 아래 공용 버튼 줄이 맡습니다. */
@Composable
private fun ClassroomHistoryBody(state: ApplyHistoryState) {
    when (state) {
        ApplyHistoryState.Idle -> Unit

        ApplyHistoryState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            OneUiLoading(size = 16.dp, stroke = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "내역을 불러오는 중",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is ApplyHistoryState.Error -> Text(
            if (state.message == ACADEMIC_NEEDS_LOGIN) "하이하나 계정을 먼저 등록해 주세요" else state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is ApplyHistoryState.Rows -> if (state.rows.isEmpty()) {
            Text(
                "내역 없음",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                state.rows.forEach { row ->
                    val parts = listOfNotNull(row.slot, row.place, row.status, row.appliedDate)
                        .filter { it.isNotBlank() }
                    Text(
                        if (parts.isEmpty()) "내역" else parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}
