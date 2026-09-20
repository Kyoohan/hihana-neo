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
import com.yhjang.timetable.widget.WidgetKindColors
import com.yhjang.timetable.widget.iconResFor
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

    /** 시스템 설정 때문에 실시간 알림이 안 보이는 이유 — 없으면 null. */
    class BlockedReason(val title: String, val steps: String)

    fun blockedReason(context: Context): BlockedReason? {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) {
            return BlockedReason("알림이 꺼져 있습니다", "눌러서 하이하나 Neo의 알림을 허용해 주세요.")
        }
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return BlockedReason("'실시간 일정' 알림 카테고리가 꺼져 있습니다", "눌러서 알림 카테고리 → 실시간 일정을 켜 주세요.")
        }
        if (!ExactAlarmPermission.isGranted(context)) {
            return BlockedReason(
                "남은 시간이 늦게 갱신될 수 있습니다",
                "'알람 및 리마인더' 권한이 없으면 절전 중 갱신이 5~15분 미뤄집니다. 위의 위젯 항목에서 권한을 허용해 주세요.",
            )
        }
        if (Build.VERSION.SDK_INT >= 36 && !manager.canPostPromotedNotifications()) {
            return BlockedReason(
                "Now Bar 표시가 허용되지 않았습니다",
                "삼성 폰: 설정 → 개발자 옵션 → '모든 앱의 실시간 정보 보기'를 켠 뒤, 설정 → 알림 → 고급 설정 → 실시간 정보에서 " +
                    "하이하나 Neo가 켜져 있는지 확인해 주세요. (눌러서 알림 설정 열기)",
            )
        }
        return null
    }

    /** 이 앱의 시스템 알림 설정 화면을 엽니다. */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
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
        val kindColors = WidgetKindColors.resolve(runCatching { PlanStore.widgetKindColors(app) }.getOrDefault(emptyMap()))
        manager.notify(NOTIFICATION_ID, build(app, block, next, now, kindColors))

        // 1분 틱과 구간 경계 모두 정확한 알람으로 (알람 및 리마인더 권한이 있을 때) — 부정확 알람은 절전 상태에서
        // 5~15분씩 미뤄져 Now Bar 의 남은 시간이 실제와 어긋났습니다. 권한이 없으면 부정확 알람으로 폴백.
        val boundary = toMillis(block.end) + 1_000
        val tickAt = System.currentTimeMillis() + TICK_MS
        scheduleAt(app, minOf(boundary, tickAt), exact = true)
    }

    private suspend fun nextWindowStart(context: Context, date: LocalDate): LocalDateTime? {
        val places = runCatching { PlanStore.placesForToday(context, date) }.getOrDefault(emptyMap())
        return Timetable.blocks(date) { places[it] }.filter { !it.isBlank }.minOfOrNull { it.start }
    }

    private fun build(context: Context, block: Block, next: Block?, now: LocalDateTime, kindColors: Map<Accent, Int>): Notification {
        val remaining = Duration.between(now, block.end).coerceAtLeast(Duration.ZERO)
        val total = Duration.between(block.start, block.end).toMillis().coerceAtLeast(1L)
        val elapsed = Duration.between(block.start, now).toMillis().coerceIn(0L, total)
        val minutes = (remaining.toMillis() / 60_000L).toInt()
        val remainingText = if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분 남음" else "${minutes}분 남음"
        val shortText = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}분"
        val range = "${block.start.format(timeFormat)} – ${block.end.format(timeFormat)}"
        val nextPlace = next?.let { listOfNotNull(it.title.takeIf { t -> t.isNotBlank() }, it.room).joinToString(" ") }
            ?.takeIf { it.isNotBlank() }
        // 제목은 "지금 있어야 할 장소" — 면학이면 장소+자리, 수업이면 과목+교실. 쉬는 시간·식사 같은 대기 구간에는
        // 다음에 가야 할 장소를 제목에 같이 붙입니다 ("쉬는 시간 → 교과교실 A201").
        val kind = block.kind
        val title = when (kind) {
            is BlockKind.GapKind -> listOfNotNull(kind.gap.label, nextPlace?.let { "→ $it" }).joinToString(" ")
            else -> listOfNotNull(block.title.takeIf { it.isNotBlank() }, block.room?.takeIf { it.isNotBlank() }).joinToString(" ")
                .ifBlank { block.statusLabel }
        }
        val text = listOfNotNull(
            block.statusLabel.takeIf { it.isNotBlank() && kind !is BlockKind.GapKind },
            range,
            remainingText,
        ).joinToString(" · ")
        val nextText = if (kind is BlockKind.GapKind) null else nextPlace?.let { "다음 · $it" }
        // 아이콘·색은 위젯과 같은 상황별 아이콘·종류별 색.
        val icon = iconResFor(block.iconKey)
        val color = kindColors[block.accent] ?: 0xFF3B82F6.toInt()

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= 36) {
            // 진행률은 0~1000 으로 — 밀리초 그대로(수백만) 넘기면 삼성 Now Bar 가 막대 오른쪽 끝을 잘라 그렸습니다.
            val permille = (elapsed * 1000L / total).toInt().coerceIn(0, 1000)
            val style = Notification.ProgressStyle()
                .setProgress(permille)
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(1000)))
                .setProgressTrackerIcon(null)
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(nextText)
                .setStyle(style)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                // 시스템이 직접 세는 카운트다운(구간 끝까지) — 알람이 밀려도 이 숫자는 실제 시간과 맞습니다.
                .setWhen(toMillis(block.end))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setContentIntent(open)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(shortText)
                // 상태 바 칩·Now Bar 가 아이콘 바탕색으로 쓰는 색 — 위젯의 종류별 색.
                .setColor(color)
                .build()
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(color)
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
