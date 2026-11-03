package com.skh.hihanaevo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * 외출·외박 — 포털 외출 내역 최근 20건에서 신청·승인·복귀만 골라 [Timetable.installOutings] 로 넘깁니다.
 * 그동안의 일과 대신 외출 블록이 들어가 홈 '지금'·타임라인·위젯·Now Bar 가 함께 바뀝니다.
 * 위젯·Now Bar 가 새 프로세스에서 떠도 쓰도록 캐시에 둡니다.
 */
object OutingSchedule {
    private const val CACHE = "outing_spans"

    /** 바뀔 때마다 올라가는 번호 — 홈이 이 값으로 오늘 블록을 다시 계산합니다. */
    var revision by mutableIntStateOf(0)
        private set

    suspend fun ensureInstalled(context: Context) {
        if (!Timetable.outingsInstalled()) install(load(context))
    }

    /** 포털에서 새로 받습니다. 실패하면 캐시를 그대로 둡니다. */
    suspend fun refresh(context: Context) {
        val rows = runCatching { OutingApi.list(context, OutingFilter.ALL, 1).rows }.getOrNull() ?: run { ensureInstalled(context); return }
        val spans = rows.mapNotNull { o ->
            if (o.state == 8 || o.state == 32) return@mapNotNull null
            val start = o.actualOut ?: o.plannedOut ?: return@mapNotNull null
            val end = o.actualBack ?: o.plannedBack ?: return@mapNotNull null
            if (!end.isAfter(start)) null else OutingSpan(start, end, o.reason, o.state == 2)
        }
        PlanStore.writeCachedJson(context, CACHE, JSONArray(spans.map { s ->
            JSONObject().put("s", s.start.toString()).put("e", s.end.toString()).put("r", s.reason ?: "").put("p", s.pending)
        }).toString())
        install(spans)
    }

    private suspend fun load(context: Context): List<OutingSpan> {
        val raw = PlanStore.cachedJson(context, CACHE)?.value ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                OutingSpan(LocalDateTime.parse(o.getString("s")), LocalDateTime.parse(o.getString("e")),
                    o.optString("r").ifBlank { null }, o.optBoolean("p"))
            }
        }.getOrDefault(emptyList())
    }

    private fun install(spans: List<OutingSpan>) {
        Timetable.installOutings(spans)
        revision++
    }
}
