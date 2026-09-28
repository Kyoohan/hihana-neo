package com.yhjang.timetable.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yhjang.timetable.HanaTimetableSync
import com.yhjang.timetable.widget.TimeTableWidget

/**
 * 급식 표시 창 전환 시각과 블록 경계에 맞춰 위젯을 다시 그립니다.
 *
 * 예를 들어 점심시간은 종료 10분 전에 메뉴 대신 다음 일정으로 바뀌어야 하는데,
 * Glance 위젯은 스스로 갱신되지 않으므로 경계 시각에 한 번 깨워 다시 그립니다.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        // 주기 갱신 시 프로세스가 새로 떠 in-memory 설치가 비어 있을 수 있어 캐시를 다시 설치합니다.
        HanaTimetableSync.ensureInstalled(context)
        TimeTableWidget().updateAll(context)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "widget_boundary_refresh"
    }
}
