package com.yhjang.timetable

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 실시간 일정(Live Activity) — 지금 구간과 남은 시간을 진행 알림으로 띄웁니다. Android 16+ 에서는 "실시간 업데이트"
 * (promoted ongoing)로 요청해 삼성 Now Bar·상태 바 칩에 나오고, 그 아래 버전에서는 보통의 진행 알림입니다.
 *
 * 표시 창: 그날의 첫 구간(평일 아침시간, 주말 1타임)부터 마지막 구간(평일 2타임, 주말 4타임) 끝까지. 창 안에서는
 * 1분마다 갱신하고 구간이 바뀌는 시각엔 정확히 깨우며, 창 밖에서는 알림을 지우고 다음 창 시작에 맞춰 예약합니다.
 */
object LiveActivity {

    const val CHANNEL_ID = "live_activity"
    private const val NOTIFICATION_ID = 7401
    private const val PREFS_NAME = "live_activity"
    private const val KEY_ENABLED = "enabled"
    private const val TICK_MS = 60_000L
    private const val REQUEST_TICK = 7402
    private val timeFormat = DateTimeFormatter.ofPattern("H:mm")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        CoroutineScope(Dispatchers.Default).launch { update(context) }
    }

    /** 지금 상태로 알림을 새로 그리고 다음 갱신을 예약합니다. 꺼져 있으면 알림·예약을 모두 지웁니다. */
    suspend fun update(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!isEnabled(app)) {
            manager.cancel(NOTIFICATION_ID)
            cancelTick(app)
            return
        }
        runCatching { HanaTimetableSync.ensureInstalled(app) }
        val today = PlanStore.today()
        val places = runCatching { PlanStore.placesForToday(app, today) }.getOrDefault(emptyMap())
        val blocks = Timetable.blocks(today) { places[it] }
        val now = LocalDateTime.now(PlanStore.seoulZone)
        val shown = blocks.filter { !it.isBlank }
        val windowStart = shown.minOfOrNull { it.start }
        val windowEnd = shown.maxOfOrNull { it.end }

        if (windowStart == null || windowEnd == null || now.isBefore(windowStart) || !now.isBefore(windowEnd)) {
            manager.cancel(NOTIFICATION_ID)
            // 다음 창 시작(오늘 아직 안 열렸으면 오늘, 아니면 내일)에 맞춰 한 번 깨웁니다.
            val nextStart = if (windowStart != null && now.isBefore(windowStart)) windowStart
            else nextWindowStart(app, today.plusDays(1))
            scheduleAt(app, nextStart?.let { toMillis(it) } ?: (System.currentTimeMillis() + 6 * 3_600_000L), exact = false)
            return
        }

        val block = Timetable.blockAt(today, now) { places[it] }
        val next = Timetable.nextEvent(blocks, block, now)
        ensureChannel(manager)
        manager.notify(NOTIFICATION_ID, build(app, block, next, now))

        // 1분 틱 + 구간 경계엔 정확히 (권한 없으면 틱만).
        val boundary = toMillis(block.end) + 1_000
        val tickAt = System.currentTimeMillis() + TICK_MS
        scheduleAt(app, minOf(boundary, tickAt), exact = boundary <= tickAt)
    }

    private suspend fun nextWindowStart(context: Context, date: LocalDate): LocalDateTime? {
        val places = runCatching { PlanStore.placesForToday(context, date) }.getOrDefault(emptyMap())
        return Timetable.blocks(date) { places[it] }.filter { !it.isBlank }.minOfOrNull { it.start }
    }

    private fun build(context: Context, block: Block, next: Block?, now: LocalDateTime): Notification {
        val remaining = Duration.between(now, block.end).coerceAtLeast(Duration.ZERO)
        val total = Duration.between(block.start, block.end).toMillis().coerceAtLeast(1L)
        val elapsed = Duration.between(block.start, now).toMillis().coerceIn(0L, total)
        val minutes = (remaining.toMillis() / 60_000L).toInt()
        val remainingText = if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분 남음" else "${minutes}분 남음"
        val shortText = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}분"
        val title = listOfNotNull(block.statusLabel.takeIf { it.isNotBlank() }, block.title.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        val room = block.room?.takeIf { it.isNotBlank() }
        val range = "${block.start.format(timeFormat)} – ${block.end.format(timeFormat)}"
        val text = listOfNotNull(room, range, remainingText).joinToString(" · ")
        val nextText = next?.let { "다음 · " + listOfNotNull(it.title.takeIf { t -> t.isNotBlank() }, it.room).joinToString(" ") }

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle()
                .setProgress(elapsed.toInt())
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(total.toInt())))
                .setProgressTrackerIcon(null)
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_place)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(nextText)
                .setStyle(style)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(shortText)
                // 상태 바 칩·Now Bar 가 아이콘 바탕색으로 쓰는 색 (없으면 0x00000000 으로 나갔습니다).
                .setColor(0xFF3B82F6.toInt())
                .build()
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_place)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(nextText)
            .setProgress(total.toInt(), elapsed.toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "실시간 일정", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "지금 구간과 남은 시간을 Now Bar·상태 바에 표시"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun toMillis(time: LocalDateTime): Long =
        time.atZone(PlanStore.seoulZone).toInstant().toEpochMilli()

    private fun scheduleAt(context: Context, atMillis: Long, exact: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = tickIntent(context)
        if (exact && ExactAlarmPermission.isGranted(context)) {
            runCatching { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending) }
                .onSuccess { return }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }

    private fun cancelTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(tickIntent(context))
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_TICK, Intent(context, LiveActivityReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** 실시간 일정의 틱·경계 알람과 부팅 완료를 받아 알림을 다시 그립니다. */
class LiveActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { LiveActivity.update(context) }
            pending.finish()
        }
    }
}
