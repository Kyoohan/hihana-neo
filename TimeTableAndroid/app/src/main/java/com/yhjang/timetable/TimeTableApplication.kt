package com.yhjang.timetable

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yhjang.timetable.worker.HanaSyncWorker
import java.util.concurrent.TimeUnit

class TimeTableApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AlimNotifier.ensureChannel(this)
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        // WorkManager 는 주기적 작업의 최소 간격을 15분으로 강제합니다 — 30분은 목표치일 뿐,
        // Doze/배터리 최적화에 따라 실제로는 더 늦게 돌 수 있습니다.
        val request = PeriodicWorkRequestBuilder<HanaSyncWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HanaSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
