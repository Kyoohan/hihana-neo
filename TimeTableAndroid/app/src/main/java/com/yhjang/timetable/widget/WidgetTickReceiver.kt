package com.yhjang.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 진행 막대를 1분 간격으로 다시 그리는 경량 틱.
 *
 * Glance 위젯은 연속 애니메이션을 못 하므로, updateAll() 을 반복 호출해 막대가 근사
 * 실시간으로 차오르게 합니다. 다음 틱을 다시 예약할지 여부는 여기서 정하지 않고
 * [TimeTableWidget.provideGlance] 가 매번 그 시점의 블록 상태(블랭크인지)를 보고
 * schedule()/cancel() 로 직접 결정합니다 — 여기서 또 무조건 재예약해버리면 그 판단을
 * 덮어써서 틱이 절대 멈추지 않는 배터리 버그가 됩니다.
 * set() 은 inexact 라 Doze/배터리 최적화 상태에서는 지연될 수 있습니다.
 */
class WidgetTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { TimeTableWidget().updateAll(context) }
            pending.finish()
        }
    }

    companion object {
        private const val INTERVAL_MS = 60_000L

        // 재예약마다 같은 알람을 덮어쓰도록 고정 요청 코드를 씁니다
        private const val REQUEST_CODE = 7301
        private const val REQUEST_CODE_BOUNDARY = 7302

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // inexact 알람 — Doze/배터리 최적화에서는 지연될 수 있습니다
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + INTERVAL_MS, pendingIntent(context, REQUEST_CODE))
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent(context, REQUEST_CODE))
        }

        /**
         * 블록이 바뀌는 시각(수업 끝, 면학 시작 등)에 정확히 깨워 다시 그립니다. 1분 틱과 달리 이건
         * 몇 초만 늦어도 이전 블록의 카운트다운이 0 을 지나 음수(-00:xx)로 보이므로 exact alarm 을 씁니다.
         * 하루 10~15번이라 배터리 영향은 없고, 권한이 없으면 false 를 돌려줘 호출부가 WorkManager 로 폴백합니다.
         */
        fun scheduleBoundary(context: Context, atMillis: Long): Boolean {
            if (!com.yhjang.timetable.ExactAlarmPermission.isGranted(context)) return false
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    atMillis,
                    pendingIntent(context, REQUEST_CODE_BOUNDARY),
                )
            }.isSuccess
        }

        fun cancelBoundary(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent(context, REQUEST_CODE_BOUNDARY))
        }

        private fun pendingIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, WidgetTickReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
