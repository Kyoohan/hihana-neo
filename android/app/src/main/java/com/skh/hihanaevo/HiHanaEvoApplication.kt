package com.skh.hihanaevo

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.skh.hihanaevo.worker.HanaSyncWorker
import java.util.concurrent.TimeUnit

class HiHanaEvoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 디버그 빌드에서만 웹뷰를 chrome://inspect 로 들여다볼 수 있게 — 포털 페이지의 JS/DOM 문제 진단용.
        if (BuildConfig.DEV) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        // 면학실·도서관 신청 로그 파일 위치 (dev 빌드만 기록).
        AlimNotifier.ensureChannel(this)
        BoardNotifier.ensureChannel(this)
        // 하이하나 Neo 에서 업데이트한 뒤 처음 — 예전 작업·알람은 옛 클래스 이름(com.yhjang.timetable.…)을 가리켜 그대로 두면
        // 실패만 되풀이합니다(동기화 작업은 KEEP 이라 영영). 한 번은 새 이름으로 다시 겁니다. 새로 깐 사람에게도 해가 없습니다.
        val prefs = getSharedPreferences(PREFS_MIGRATION, MODE_PRIVATE)
        val rename = prefs.getInt(KEY_RENAMED, 0) < 1
        schedulePeriodicSync(replace = rename)
        // 게시판 새 글 5분 확인 — 이미 걸려 있으면 그대로 둡니다.
        BoardWatch.schedule(this, force = rename)
        if (rename) prefs.edit().putInt(KEY_RENAMED, 1).apply()
        // 데일리 브리핑 알람 — 시각이 정해져 있어 다시 걸어도 밀리지 않습니다.
        MorningBrief.ensureChannel(this)
        MorningBrief.schedule(this)
    }

    private fun schedulePeriodicSync(replace: Boolean) {
        // WorkManager 는 주기적 작업의 최소 간격을 15분으로 강제합니다 — 30분은 목표치일 뿐,
        // Doze/배터리 최적화에 따라 실제로는 더 늦게 돌 수 있습니다.
        val request = PeriodicWorkRequestBuilder<HanaSyncWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HanaSyncWorker.WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val PREFS_MIGRATION = "app_migration"
        /** 1 = Evo 클래스 이름으로 작업·알람을 다시 건 뒤. */
        const val KEY_RENAMED = "renamed_evo"
    }
}
