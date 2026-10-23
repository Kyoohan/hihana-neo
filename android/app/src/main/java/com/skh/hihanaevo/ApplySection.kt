package com.skh.hihanaevo

import com.skh.hihanaevo.ui.onPageVariantColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.OneUiButton
import com.skh.hihanaevo.ui.OneUiCard
import com.skh.hihanaevo.ui.OneUiLoading
import com.skh.hihanaevo.ui.OneUiButtonStyle
import com.skh.hihanaevo.ui.OneUiDivider
import com.skh.hihanaevo.ui.OneUiGroupColumn
import kotlinx.coroutines.launch

internal const val PORTAL_BASE = "https://hh.hana.hs.kr"

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
/** 신청·내역 블록 순서 — 서비스 4개와 심야면학. 헤더의 연필 아이콘으로 바꾸고 기기에 저장합니다. */
internal object ApplyOrderStore {
    const val MIDNIGHT = "MIDNIGHT"
    private const val PREFS = "apply_order"
    private const val KEY = "order"
    val defaultOrder: List<String> = ApplyService.entries.map { it.name } + MIDNIGHT

    /** 저장된 순서 — 모르는 이름은 빼고, 나중에 생긴 블록은 끝에 붙입니다. */
    fun get(context: android.content.Context): List<String> {
        val saved = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY, null)?.split(',')?.filter { it in defaultOrder }.orEmpty()
        return saved.distinct() + defaultOrder.filterNot { it in saved }
    }

    fun set(context: android.content.Context, order: List<String>) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, order.joinToString(",")).apply()
    }

    fun label(key: String): String =
        if (key == MIDNIGHT) "심야면학" else ApplyService.entries.firstOrNull { it.name == key }?.label ?: key
}

@Composable
internal fun ApplyHistorySection(
    onOpenWeb: (url: String, title: String) -> Unit,
    onOpenSeats: (SeatService) -> Unit,
    /** 헤더의 연필 아이콘으로 켜는 순서 편집 — 블록 대신 위·아래 버튼이 달린 목록을 보여 줍니다. */
    editing: Boolean = false,
    /**
     * 큰 화면 두 칸 — 면학실·도서관·외출외박 내역을 전체 화면 대신 오른쪽 칸에 띄웁니다(null 이면 폰처럼 전체 화면).
     * [historyShown] 은 지금 오른쪽 칸에 떠 있는 서비스 — 그 카드의 '내역' 버튼을 강조합니다.
     */
    onShowHistory: ((ApplyService) -> Unit)? = null,
    historyShown: ApplyService? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<ApplyService, ApplyHistoryState>() }
    var order by remember { mutableStateOf(ApplyOrderStore.get(context)) }
    var showingClassroomStatus by remember { mutableStateOf(false) }
    if (showingClassroomStatus) ClassroomStatusScreen(onDismiss = { showingClassroomStatus = false })

    fun openHistory(service: ApplyService) {
        onOpenWeb("$PORTAL_BASE${service.historyPath}", "${service.label} 내역")
    }

    // 면학실·도서관·외출외박 내역은 앱 안 전체 화면 — 오른쪽 위 버튼으로 포털 내역 페이지도 열 수 있습니다.
    var historyScreen by remember { mutableStateOf<ApplyService?>(null) }
    when (val shown = historyScreen) {
        ApplyService.STUDY_ROOM, ApplyService.LIBRARY -> SeatHistoryScreen(
            place = if (shown == ApplyService.LIBRARY) SeatHistoryPlace.LIBRARY else SeatHistoryPlace.STUDY_ROOM,
            onDismiss = { historyScreen = null },
            onOpenPortal = { historyScreen = null; openHistory(shown) },
        )
        ApplyService.OUTING -> OutingHistoryScreen(
            onDismiss = { historyScreen = null },
            onOpenPortal = { historyScreen = null; openHistory(shown) },
        )
        else -> Unit
    }

    // 교과교실·외출외박 신청도 앱 안 화면 — 신청·취소는 확인 창에서 한 번 더 눌러야 보냅니다.
    var applyScreen by remember { mutableStateOf<ApplyService?>(null) }
    fun openApplyPortal(service: ApplyService) = onOpenWeb("$PORTAL_BASE${service.applyPath}", service.label)
    when (applyScreen) {
        ApplyService.CLASSROOM -> ClassroomApplyScreen(
            onDismiss = { applyScreen = null },
            onOpenPortal = { applyScreen = null; openApplyPortal(ApplyService.CLASSROOM) },
        )
        ApplyService.OUTING -> OutingApplyScreen(
            onDismiss = { applyScreen = null },
            onOpenPortal = { applyScreen = null; openApplyPortal(ApplyService.OUTING) },
            onOpenHistory = {
                applyScreen = null
                if (onShowHistory != null) onShowHistory(ApplyService.OUTING) else historyScreen = ApplyService.OUTING
            },
        )
        else -> Unit
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 화면을 떠났거나(다른 앱에 갔다 오기 등) 같은 작업이 새로 시작돼 취소된 것 — 오류가 아닙니다.
                throw e
            } catch (e: Exception) {
                ApplyHistoryState.Error(e.message ?: "내역을 불러오지 못했습니다")
            }
        }
    }

    if (editing) {
        OrderEditor(
            order = order,
            defaultOrder = ApplyOrderStore.defaultOrder,
            label = ApplyOrderStore::label,
            onMove = { from, to ->
                order = order.toMutableList().apply { add(to, removeAt(from)) }
                ApplyOrderStore.set(context, order)
            },
            onReset = {
                order = ApplyOrderStore.defaultOrder
                ApplyOrderStore.set(context, order)
            },
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        order.forEach { key ->
            // 심야면학은 학교 포털이 아닌 기숙사 쪽 별도 사이트(Supabase) — 자체 로그인으로 내역·신청.
            if (key == ApplyOrderStore.MIDNIGHT) {
                MidnightStudyCard()
                return@forEach
            }
            val service = ApplyService.valueOf(key)
            ApplyServiceCard(
                service = service,
                state = states[service] ?: ApplyHistoryState.Idle,
                // 교과교실은 "누가 어느 교실에 있는지" 현황 조회를 하나 더.
                onStatus = if (service == ApplyService.CLASSROOM) ({ showingClassroomStatus = true }) else null,
                // 교과교실은 카드 안에 목록, 나머지 셋은 전체 화면.
                onHistory = {
                    when {
                        service == ApplyService.CLASSROOM -> loadHistory(service)
                        onShowHistory != null -> onShowHistory(service)
                        else -> historyScreen = service
                    }
                },
                historySelected = historyShown == service,
                // 도서관·면학실은 좌석 화면, 교과교실·외출외박은 신청 화면 — 모두 앱 안.
                onApply = {
                    when (service) {
                        ApplyService.LIBRARY -> onOpenSeats(SeatService.LIBRARY)
                        ApplyService.STUDY_ROOM -> onOpenSeats(SeatService.STUDY_ROOM)
                        ApplyService.CLASSROOM, ApplyService.OUTING -> applyScreen = service
                    }
                },
            )
        }
    }
}

/** 순서 편집 — 블록 이름마다 위·아래 버튼. 바꾸는 즉시 저장되고, 헤더의 체크 아이콘으로 끝냅니다. */
@Composable
internal fun OrderEditor(
    order: List<String>,
    defaultOrder: List<String>,
    label: (String) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    onReset: () -> Unit,
) {
    Column {
        Text(
            "위·아래 버튼으로 순서를 바꾸고, 끝나면 위의 체크를 누르세요.",
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        OneUiGroupColumn {
            order.forEachIndexed { index, key ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = onPageVariantColor(),
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        label(key),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "${label(key)} 위로")
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < order.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "${label(key)} 아래로")
                    }
                }
                if (index != order.lastIndex) OneUiDivider()
            }
        }
        Spacer(Modifier.height(12.dp))
        OneUiButton(
            text = "기본 순서로",
            onClick = onReset,
            style = OneUiButtonStyle.Neutral,
            enabled = order != defaultOrder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


@Composable
private fun ApplyServiceCard(
    service: ApplyService,
    state: ApplyHistoryState?,
    onHistory: () -> Unit,
    onApply: () -> Unit,
    onStatus: (() -> Unit)? = null,
    historySelected: Boolean = false,
) {
    // 내역 조회와 신청하기는 같은 급의 동작이라, 카드 아래에 같은 모양·같은 폭의 알약 버튼 두 개로 나란히 둡니다.
    val historyLabel = when (state) {
        null, ApplyHistoryState.Idle -> "내역"
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
                style = if (historySelected) OneUiButtonStyle.Filled else OneUiButtonStyle.Neutral,
                enabled = state != ApplyHistoryState.Loading,
                modifier = Modifier.weight(1f),
            )
            OneUiButton(
                text = "신청",
                onClick = onApply,
                style = OneUiButtonStyle.Neutral,
                modifier = Modifier.weight(1f),
            )
        }
        if (onStatus != null) {
            Spacer(Modifier.height(10.dp))
            OneUiButton(
                text = "교실별 현황",
                onClick = onStatus,
                style = OneUiButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth(),
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
                color = onPageVariantColor(),
            )
        }

        is ApplyHistoryState.Error -> Text(
            if (state.message == ACADEMIC_NEEDS_LOGIN) "하이하나 계정을 먼저 등록해 주세요" else state.message,
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
        )

        is ApplyHistoryState.Rows -> if (state.rows.isEmpty()) {
            Text(
                "내역 없음",
                style = MaterialTheme.typography.bodySmall,
                color = onPageVariantColor(),
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
