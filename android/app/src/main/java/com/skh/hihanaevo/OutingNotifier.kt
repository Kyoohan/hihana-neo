package com.skh.hihanaevo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 외출·외박 승인 알림 — 내가 낸 외출·외박이 신청(대기)에서 승인·반려로 바뀌면 알립니다.
 *
 * 외출 내역 첫 쪽(최근 20건)의 상태를 신청 번호별로 기억해 두고, 다음에 받았을 때 '신청 → 승인/반려'로 바뀐 것만 알립니다.
 * 확인은 내역 조회(읽기)뿐입니다. 승인을 기다리는 신청이 있을 때만 [BoardWatch] 의 5분 알람이 함께 확인하고,
 * 없으면 30분 동기화와 앱을 열 때만 받습니다.
 *
 * - 단체 신청(선생님이 반·학년 전체로 넣은 것)은 내가 낸 것이 아니어서 알리지 않습니다.
 * - 처음 받을 때는 기준만 잡고 알리지 않습니다 — 업데이트하자마자 지난 승인들이 한꺼번에 뜨지 않게.
 * - 두 확인 사이에 새로 내고 바로 승인된 신청(예전 상태를 못 본 것)은 기준을 잡은 뒤에 낸 것일 때만 알립니다.
 */
object OutingNotifier {
    const val CHANNEL_ID = "outing"
    private const val PREFS = "outing_notify"
    private const val KEY_STATES = "states"
    private const val KEY_BASELINE = "baseline_at"
    private const val KEY_ENABLED = "enabled"
    private const val NOTIFY_BASE = 7500

    private const val APPLIED = 2
    private const val APPROVED = 4
    private const val REJECTED = 8

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 기본은 켜짐 — 주문·배송 상태 알림처럼 내가 한 신청의 결과라서. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        BoardWatch.schedule(context)
    }

    private fun states(context: Context): JSONObject =
        runCatching { JSONObject(prefs(context).getString(KEY_STATES, null) ?: "{}") }.getOrDefault(JSONObject())

    /** 승인을 기다리는 내 신청이 있어 5분마다 확인해야 하는지. */
    fun watching(context: Context): Boolean {
        if (!isEnabled(context)) return false
        val s = states(context)
        return s.keys().asSequence().any { s.optInt(it) == APPLIED }
    }

    /** 새로 받은 내역 첫 쪽 — 바뀐 상태를 알리고 기억합니다. */
    fun process(context: Context, rows: List<OutingRow>) {
        val prefs = prefs(context)
        val known = states(context)
        val baseline = prefs.getString(KEY_BASELINE, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val mine = rows.filter { !it.group }
        val decided = if (baseline == null) emptyList() else mine.filter { row ->
            val key = row.osmNo.toString()
            val wasWaiting = if (known.has(key)) known.optInt(key) == APPLIED else row.appliedAt?.isAfter(baseline) == true
            wasWaiting && (row.state == APPROVED || row.state == REJECTED)
        }
        // 지금 첫 쪽에 있는 내 신청만 기억합니다(오래된 것은 저절로 빠짐).
        val next = JSONObject().apply { mine.forEach { put(it.osmNo.toString(), it.state) } }
        prefs.edit().putString(KEY_STATES, next.toString())
            .apply { if (baseline == null) putString(KEY_BASELINE, LocalDateTime.now().toString()) }
            .apply()
        if (isEnabled(context)) decided.forEach { notify(context, it) }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "외출·외박 승인", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "신청한 외출·외박이 승인되거나 반려되면 알립니다"
                },
            )
        }
    }

    private fun notify(context: Context, row: OutingRow) {
        if (!AlimNotifier.canNotify(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        val out = row.plannedOut
        val back = row.plannedBack
        // 돌아오는 날이 나가는 날보다 뒤면 외박, 같은 날이면 외출 — 포털 목록에 따로 종류 칸이 없어 날짜로 가립니다.
        val kind = if (out != null && back != null && back.toLocalDate().isAfter(out.toLocalDate())) "외박" else "외출"
        val title = if (row.state == APPROVED) "$kind 승인됨" else "$kind 반려됨"
        val text = listOfNotNull(span(out, back), row.reason?.takeIf { it.isNotBlank() }).joinToString(" · ")
            .ifEmpty { row.stateLabel }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_OUTING_HISTORY)
        }
        val id = NOTIFY_BASE + (row.osmNo % 1000).toInt()
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hihana)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id, notification) }
    }

    private val dayTime = DateTimeFormatter.ofPattern("M.d(E) HH:mm", Locale.KOREAN)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

    /** '11.24(화) 18:00 ~ 21:00', 날이 다르면 '11.24(화) 18:00 ~ 11.25(수) 21:00'. */
    internal fun span(out: LocalDateTime?, back: LocalDateTime?): String? {
        if (out == null) return null
        if (back == null) return out.format(dayTime)
        val end = if (back.toLocalDate() == out.toLocalDate()) back.format(time) else back.format(dayTime)
        return "${out.format(dayTime)} ~ $end"
    }
}
