package com.skh.hihanaevo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 게시판 새 글을 빨리 알립니다 — 30분 주기 동기화와 따로, 알림을 켠 게시판만 5분마다 확인합니다.
 *
 * WorkManager 의 주기 작업은 15분 아래로 줄일 수 없어(실제론 30분 넘게 밀리기도) 알람으로 깨웁니다. Doze 중에도 울리는
 * allow-while-idle 알람이 짧은 일회성 작업(가능하면 바로 도는 우선 작업)을 맡기고 다음 알람을 겁니다. 기기가 깊이 잠들면
 * 시스템이 알람을 10분 남짓으로 늦출 수 있습니다. 확인은 읽기(게시판 목록 조회)뿐입니다.
 */
object BoardWatch {
    private const val INTERVAL_MS = 5 * 60_000L
    private const val REQUEST = 7301
    private const val WORK_NAME = "board_watch"
    private const val PREFS = "board_watch"
    private const val KEY_NEXT = "next_at"

    /** 5분 확인과 30분 동기화가 겹쳐 같은 글을 두 번 알리지 않게. */
    private val lock = Mutex()

    /**
     * 다음 확인 알람을 겁니다. [force] 가 아니면 이미 걸어 둔 알람이 아직 남아 있을 때 그대로 둡니다 — 프로세스가 뜰 때마다
     * (위젯·실시간 일정 틱) 다시 걸면 알람이 계속 뒤로 밀려 영영 울리지 않습니다.
     */
    fun schedule(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (BoardNotifier.enabledCategories(app).isEmpty() || !HanaCredentialStore.hasCredentials(app)) {
            cancel(app)
            return
        }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && prefs.getLong(KEY_NEXT, 0L) in (now + 1)..(now + INTERVAL_MS)) return
        val at = now + INTERVAL_MS
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmIntent(app)
        val exact = ExactAlarmPermission.isGranted(app) &&
            runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }.isSuccess
        if (!exact) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        prefs.edit().putLong(KEY_NEXT, at).apply()
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(app))
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_NEXT).apply()
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, BoardWatchReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 알람이 울리면 — 네트워크가 있을 때 바로 도는 일회성 작업으로(이미 돌고 있으면 그대로). */
    internal fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<BoardWatchWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // 우선 작업은 Android 12+ 에서만 — 그 아래는 포그라운드 알림이 필요해 보통 작업으로 둡니다.
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * 알림을 켠 게시판의 목록을 새로 받아 [BoardNotifier.process] 로 넘깁니다. 로그인 실패([HanaPortalException.LoginFailed])는
     * 그대로 던져 부르는 쪽이 자동 동기화를 멈추게 합니다(비밀번호 오류로 잠기지 않게). 다른 실패는 게시판마다 건너뜁니다.
     */
    suspend fun check(context: Context) = lock.withLock {
        BoardNotifier.enabledCategories(context).forEach { category ->
            try {
                val posts = HanaAcademicRepository.board(context, category, force = true)
                BoardNotifier.process(context, category, posts)
            } catch (e: HanaPortalException.LoginFailed) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}

/** 5분 알람(과 부팅 완료) — 다음 알람을 걸고 확인 작업을 맡깁니다. */
class BoardWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BoardWatch.schedule(context, force = true)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) BoardWatch.enqueue(context)
    }
}

class BoardWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!HanaCredentialStore.hasCredentials(applicationContext) || HanaSyncGate.isSuspended(applicationContext)) return Result.success()
        try {
            BoardWatch.check(applicationContext)
        } catch (_: HanaPortalException.LoginFailed) {
            HanaSyncGate.suspend(applicationContext)
        } catch (_: Exception) {
        }
        return Result.success()
    }
}
