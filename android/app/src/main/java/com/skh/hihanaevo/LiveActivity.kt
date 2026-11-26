package com.skh.hihanaevo

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
import com.skh.hihanaevo.widget.WidgetKindColors
import com.skh.hihanaevo.widget.iconResFor
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
 *
 * iOS 실시간 현황과 같게(2026-11-25): 블록타임은 한 구간("1–2교시 수업 중"), 막대는 지금 구간 하나가 아니라 그날 일과 전체를
 * 시간에 비례한 칸으로([LiveDay]).
 */
object LiveActivity {

    const val CHANNEL_ID = "live_activity"
    private const val NOTIFICATION_ID = 7401
    private const val GROUP_KEY = "live_activity"
    private const val SUMMARY_ID = 7400
    private const val PREFS_NAME = "live_activity"
    private const val KEY_ENABLED = "enabled"
    private const val TICK_MS = 60_000L
    private const val REQUEST_TICK = 7402
    private const val REQUEST_TEMP_OFF = 7403
    private const val KEY_TEMP_UNTIL = "temp_until"
    const val ACTION_TEMP_OFF = "com.skh.hihanaevo.LIVE_TEMP_OFF"
    private val timeFormat = DateTimeFormatter.ofPattern("H:mm")
    private const val KEY_BAR_STYLE = "bar_style"

    /** 막대 모양(설정에서 고름). */
    enum class BarStyle(val key: String, val label: String, val caption: String) {
        /** 그날 일과 전체 — 일정마다 시간에 비례한 칸, 지금 위치에 점(기본). */
        DAY("day", "하루 전체", "그날 일과 전체를 일정별 칸으로 보여 주고, 지금 위치에 점을 찍습니다."),
        /** 지금 일정 하나의 진행 — 15.1 까지의 모양. */
        NOW("now", "지금 일정만", "지금 일정이 얼마나 지났는지만 막대 하나로 보여 줍니다."),
    }

    fun barStyle(context: Context): BarStyle {
        val key = prefs(context).getString(KEY_BAR_STYLE, null)
        return BarStyle.entries.firstOrNull { it.key == key } ?: BarStyle.DAY
    }

    fun setBarStyle(context: Context, style: BarStyle) {
        prefs(context).edit().putString(KEY_BAR_STYLE, style.key).apply()
        CoroutineScope(Dispatchers.Default).launch { update(context) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).remove(KEY_TEMP_UNTIL).apply()
        cancelTempOff(context)
        CoroutineScope(Dispatchers.Default).launch { update(context) }
    }

    /** 잠깐만 켜 둡니다 (Dev 탭) — [minutes] 뒤 알람으로 저절로 꺼집니다. 이미 켜져 있으면 그대로 두고 갱신만 합니다. */
    fun enableTemporarily(context: Context, minutes: Int) {
        if (isEnabled(context) && tempUntil(context) == 0L) {
            CoroutineScope(Dispatchers.Default).launch { update(context) }
            return
        }
        val until = System.currentTimeMillis() + minutes * 60_000L
        prefs(context).edit().putBoolean(KEY_ENABLED, true).putLong(KEY_TEMP_UNTIL, until).apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, tempOffIntent(context))
        CoroutineScope(Dispatchers.Default).launch { update(context) }
    }

    /** 임시로 켜 둔 경우 꺼질 시각(epoch ms), 아니면 0. */
    fun tempUntil(context: Context): Long = prefs(context).getLong(KEY_TEMP_UNTIL, 0L)

    private fun cancelTempOff(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(tempOffIntent(context))
    }

    private fun tempOffIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_TEMP_OFF, Intent(context, LiveActivityReceiver::class.java).setAction(ACTION_TEMP_OFF),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 시스템 설정 때문에 실시간 알림이 안 보이는 이유 — 없으면 null. [open] 은 눌렀을 때 열 설정 화면. */
    class BlockedReason(val title: String, val steps: String, val open: (Context) -> Unit = ::openNotificationSettings)

    /**
     * 삼성 One UI 가 다른 앱의 실시간 정보(Android Live Update)를 Now Bar·상태 바에 올릴지 — 개발자 옵션 '모든 앱의 실시간 정보 보기'.
     * 꺼져 있으면 시스템은 알림을 promoted 로 표시해 두고도(canPostPromotedNotifications 는 true) 매분 거부해 아이콘만 남깁니다.
     * One UI 9 업데이트가 이 값을 0 으로 되돌렸습니다. 키가 없는 기기(삼성 아님·옛 One UI)는 켜진 것으로 봅니다.
     */
    private fun samsungLiveNotificationsForAllApps(context: Context): Boolean =
        !Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            runCatching { android.provider.Settings.Secure.getInt(context.contentResolver, "enable_notification_nowbar_test", 1) }.getOrDefault(1) != 0

    fun blockedReason(context: Context): BlockedReason? {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) {
            return BlockedReason("알림이 꺼져 있습니다", "눌러서 HiHana Evo의 알림을 허용해 주세요.")
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
        if (Build.VERSION.SDK_INT >= 36 && !samsungLiveNotificationsForAllApps(context)) {
            return BlockedReason(
                "Now Bar에 나오지 않습니다",
                "삼성 폰은 설정 → 개발자 옵션 → '모든 앱의 실시간 정보 보기'를 켜야 Now Bar·상태 바에 나옵니다. " +
                    "One UI 업데이트 뒤 꺼지기도 합니다. (눌러서 개발자 옵션 열기)",
                open = ::openDeveloperSettings,
            )
        }
        if (Build.VERSION.SDK_INT >= 36 && !manager.canPostPromotedNotifications()) {
            return BlockedReason(
                "Now Bar 표시가 허용되지 않았습니다",
                "삼성 폰: 설정 → 개발자 옵션 → '모든 앱의 실시간 정보 보기'를 켠 뒤, 설정 → 알림 → 고급 설정 → 실시간 정보에서 " +
                    "HiHana Evo가 켜져 있는지 확인해 주세요. (눌러서 알림 설정 열기)",
            )
        }
        return null
    }

    /** 개발자 옵션 화면 — 삼성 '모든 앱의 실시간 정보 보기'가 여기 있습니다. 개발자 옵션이 잠겨 있으면 설정 첫 화면으로. */
    fun openDeveloperSettings(context: Context) {
        val dev = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(dev) }.onFailure {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
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
        // 임시로 켜 둔 시간이 지났으면(알람이 밀렸어도) 여기서 끕니다.
        val tempUntil = tempUntil(app)
        if (tempUntil != 0L && System.currentTimeMillis() >= tempUntil) {
            prefs(app).edit().putBoolean(KEY_ENABLED, false).remove(KEY_TEMP_UNTIL).apply()
        }
        if (!isEnabled(app)) {
            cancelNotification(manager)
            cancelTick(app)
            return
        }
        runCatching { HanaTimetableSync.ensureInstalled(app) }
        val today = PlanStore.today()
        val places = runCatching { PlanStore.placesForToday(app, today) }.getOrDefault(emptyMap())
        // 하루는 8시부터(아침시간 뺌) — [LiveDay.trimMorning]. 8시 전에는 알림을 내리고 8시에 깨어 띄웁니다.
        val blocks = LiveDay.trimMorning(Timetable.blocks(today) { places[it] })
        val now = LocalDateTime.now(PlanStore.seoulZone)
        val shown = blocks.filter { !it.isBlank }
        val windowStart = shown.minOfOrNull { it.start }
        val windowEnd = shown.maxOfOrNull { it.end }

        if (windowStart == null || windowEnd == null || now.isBefore(windowStart) || !now.isBefore(windowEnd)) {
            cancelNotification(manager)
            // 다음 창 시작(오늘 아직 안 열렸으면 오늘, 아니면 내일)에 맞춰 한 번 깨웁니다.
            val nextStart = if (windowStart != null && now.isBefore(windowStart)) windowStart
            else nextWindowStart(app, today.plusDays(1))
            scheduleAt(app, nextStart?.let { toMillis(it) } ?: (System.currentTimeMillis() + 6 * 3_600_000L), exact = false)
            return
        }

        val block = Timetable.blockAt(today, now) { places[it] }
        val segs = LiveDay.segments(blocks)
        val seg = segs.firstOrNull { !it.start.isAfter(now) && now.isBefore(it.end) } ?: LiveSeg(block, block)
        // 전날 심야면학이 자정을 넘겨 이어지면 표시 창이 전날 밤부터 시작해, 그게 끝난 뒤의 새벽 빈 시간도 창 안에
        // 들어옵니다 — 빈 구간이면 알림을 내리고 다음 일정 시작에 다시 깨웁니다.
        if (block.isBlank) {
            cancelNotification(manager)
            val nextStart = shown.firstOrNull { it.start.isAfter(now) }?.start
            scheduleAt(app, nextStart?.let { toMillis(it) } ?: (System.currentTimeMillis() + 6 * 3_600_000L), exact = false)
            return
        }
        // 묶인 블록타임이면 마지막 교시를 기준으로 다음 일정을 셉니다(같은 수업의 둘째 교시를 '다음'으로 보여 주지 않게).
        val next = Timetable.nextEvent(blocks, seg.last, now)
        ensureChannel(manager)
        val kindColors = WidgetKindColors.resolve(runCatching { PlanStore.widgetKindColors(app) }.getOrDefault(emptyMap()))
        manager.notify(NOTIFICATION_ID, build(app, seg, segs, next, now, kindColors))
        postSummary(app, manager, iconResFor(block.iconKey))

        // 1분 틱과 구간 경계 모두 정확한 알람으로 (알람 및 리마인더 권한이 있을 때) — 부정확 알람은 절전 상태에서
        // 5~15분씩 미뤄져 Now Bar 의 남은 시간이 실제와 어긋났습니다. 권한이 없으면 부정확 알람으로 폴백.
        val boundary = toMillis(seg.end) + 1_000
        val tickAt = System.currentTimeMillis() + TICK_MS
        scheduleAt(app, minOf(boundary, tickAt), exact = true)
    }

    private suspend fun nextWindowStart(context: Context, date: LocalDate): LocalDateTime? {
        val places = runCatching { PlanStore.placesForToday(context, date) }.getOrDefault(emptyMap())
        return LiveDay.trimMorning(Timetable.blocks(date) { places[it] }).filter { !it.isBlank }.minOfOrNull { it.start }
    }

    private fun build(context: Context, seg: LiveSeg, segs: List<LiveSeg>, next: Block?, now: LocalDateTime, kindColors: Map<Accent, Int>): Notification {
        // 블록타임이면 첫 교시 블록을 묶인 끝까지 늘려 씁니다(남은 시간·시간 범위가 묶인 구간 기준).
        val block = seg.first.copy(end = seg.end)
        val remaining = Duration.between(now, block.end).coerceAtLeast(Duration.ZERO)
        val total = Duration.between(block.start, block.end).toMillis().coerceAtLeast(1L)
        val elapsed = Duration.between(block.start, now).toMillis().coerceIn(0L, total)
        val minutes = (remaining.toMillis() / 60_000L).toInt()
        val remainingText = if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분 남음" else "${minutes}분 남음"
        val shortText = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}분"
        val range = "${block.start.format(timeFormat)} – ${block.end.format(timeFormat)}"
        val nextPlace = next?.let(::placeFirst)
        // 제목은 "지금 있어야 할 장소"를 맨 앞에 — Now Bar·상태 바 칩은 앞부분만 보여 줘서, 과목명이나 "일과 종료" 같은 구간
        // 이름이 먼저 오면 정작 어디로 가야 하는지가 잘렸습니다. 수업은 "A201 수학Ⅱ", 면학은 "도서관 2-33", 쉬는 시간·일과
        // 종료 같은 대기 구간은 바로 다음 장소 뒤에 구간 이름 ("도서관 2-33 · 일과 종료").
        val kind = block.kind
        val title = when (kind) {
            is BlockKind.GapKind -> listOfNotNull(placeFirst(block), kind.gap.label).joinToString(" · ")
            else -> placeFirst(block) ?: block.statusLabel
        }
        // 남은 시간은 시스템 카운트다운이 이미 보여주므로(API 36) 본문에는 넣지 않습니다 — 그 아래 폴백 알림에만 씁니다.
        val text = listOfNotNull(
            seg.statusLabel.takeIf { it.isNotBlank() && kind !is BlockKind.GapKind },
            range,
            remainingText.takeIf { Build.VERSION.SDK_INT < 36 },
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
            // 막대는 그날 일과 전체 — 일정마다 시간에 비례한 칸. 끝난 일정과 지금 일정은 지금 일정의 색 한 가지, 남은 일정은 회색
            // ([LiveDay.color]). 지금 위치는 진행률로 넘겨 시스템이 그 앞은 굵게·뒤는 가늘게 그리고(styledByProgress), 그 자리에
            // 흰 점(트래커)을 찍습니다 — 갤럭시 S25+ One UI 9 알림창에서 확인(2026-11-25). 진행률은 0~1000 으로(밀리초 그대로 넘기면
            // 삼성 Now Bar 가 막대 오른쪽 끝을 잘라 그렸습니다).
            val style = if (barStyle(context) == BarStyle.NOW) {
                // '지금 일정만'(15.1 까지의 모양) — 지금 일정의 지난 만큼은 흰색, 남은 만큼은 회색, 두 조각. 반투명 색은 One UI 가
                // 알파를 무시해 같은 색으로 그려서 불투명 회색을 씁니다.
                val done = (elapsed * 1000L / total).toInt().coerceIn(1, 999)
                Notification.ProgressStyle()
                    .setProgress(done)
                    .setStyledByProgress(false)
                    .setProgressSegments(
                        listOf(
                            Notification.ProgressStyle.Segment(done).setColor(0xFFFFFFFF.toInt()),
                            Notification.ProgressStyle.Segment(1000 - done).setColor(0xFF8E96A6.toInt()),
                        ),
                    )
                    .setProgressTrackerIcon(null)
            } else {
                val pieces = LiveDay.pieces(segs, now)
                Notification.ProgressStyle()
                    .setProgress(LiveDay.progress(segs, now))
                    .setStyledByProgress(true)
                    .setProgressSegments(
                        pieces.ifEmpty { listOf(LiveDay.Piece(1000, LiveDay.Zone.AHEAD)) }
                            .map { Notification.ProgressStyle.Segment(it.length).setColor(LiveDay.color(it.zone, color)) },
                    )
                    .setProgressTrackerIcon(trackerIcon(color))
            }
            // One UI 는 subText 를 제목 바로 아래 줄에, contentText 를 그 아래에 그립니다 — 구간·시간이 먼저, 다음 장소가 뒤에
            // 오도록 자리를 바꿔 넣습니다.
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle("$title\u2002")
                .setContentText(nextText ?: text)
                .setSubText(text.takeIf { nextText != null })
                .setStyle(style)
                .setOngoing(true)
                // 자동 묶음에서 빼려고 요약이 있는 자기 그룹에 둡니다 — [postSummary] 참고.
                .setGroup(GROUP_KEY)
                .setOnlyAlertOnce(true)
                // 시스템이 직접 세는 카운트다운(구간 끝까지) — 알람이 밀려도 이 숫자는 실제 시간과 맞습니다.
                // One UI 는 이 숫자를 제목 바로 뒤에 띄어쓰기 없이 붙여 그리므로("도서관 2-3457:14") 제목 끝에 공백을 둡니다.
                .setWhen(toMillis(block.end))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setContentIntent(open)
                .apply {
                    // setRequestPromotedOngoing 은 안드로이드 16 QPR2(36.1)부터 — 16 초기판(One UI 8.0 등)에서 부르면
                    // NoSuchMethodError 로 Now Bar 를 켜는 순간 앱이 죽습니다. 그땐 같은 뜻의 extra 를 직접 넣습니다.
                    if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) setRequestPromotedOngoing(true)
                    else addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
                }
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
            .setGroup(GROUP_KEY)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    /**
     * 막대 위 '지금 위치' 점 — 흰 점에 일정 색 테두리(밝은 알림창에서도 보이게). 시스템이 아이콘을 막대 높이에 맞춰 늘려 그리므로,
     * 큰 투명 캔버스 가운데에 작게 그려 점 크기를 정합니다(반지름 = 캔버스의 1/5, 정한 값).
     */
    private fun trackerIcon(accent: Int): android.graphics.drawable.Icon {
        val d = 96
        val bitmap = android.graphics.Bitmap.createBitmap(d, d, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = accent
        canvas.drawCircle(d / 2f, d / 2f, d / 5f + 4f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(d / 2f, d / 2f, d / 5f, paint)
        return android.graphics.drawable.Icon.createWithBitmap(bitmap)
    }

    /** 장소가 앞에 오는 이름 — 수업은 교실 + 과목(선생님 이름 없이), 면학은 장소 + 자리, 대기 구간은 바로 다음 장소. 없으면 null. */
    private fun placeFirst(block: Block): String? = when (val kind = block.kind) {
        is BlockKind.LessonKind -> placeFirst(kind.lesson)
        is BlockKind.StudyKind -> placeFirst(kind.place)
        is BlockKind.GapKind -> when (val next = kind.gap.next) {
            is NextUp.LessonNext -> placeFirst(next.lesson)
            is NextUp.StudyNext -> placeFirst(next.place)
            null -> null
        }
        is BlockKind.OutingKind -> block.room
        BlockKind.BlankKind -> null
    }

    private fun placeFirst(lesson: Lesson): String? =
        if (lesson.isFree) null else listOfNotNull(lesson.room, lesson.title.takeIf { it.isNotBlank() }).joinToString(" ")

    private fun placeFirst(place: StudyPlace?): String? =
        place?.let { listOfNotNull(it.name, it.detail?.takeIf { d -> d.isNotBlank() }).joinToString(" ") }

    private fun cancelNotification(manager: NotificationManager) {
        manager.cancel(NOTIFICATION_ID)
        manager.cancel(SUMMARY_ID)
    }

    /**
     * 실시간 일정만의 그룹 요약. 앱 알림(알리미·브리핑 등)이 둘 이상이면 Android 16+ 는 그룹 없는 알림, 그리고 요약 없는
     * 그룹 알림까지 '알림 더보기' 자동 묶음으로 모읍니다. 실시간 일정이 그 안에 들어가 묶음을 지우면 함께 사라졌습니다.
     * 요약과 자식이 함께 있는 그룹은 자동 묶음에서 빠지고, 자식이 하나뿐이라 화면에는 실시간 일정 알림만 보입니다.
     *
     * 삼성 알림창 맨 아래의 '1개 알림 더보기' 줄은 이 요약 때문이 아닙니다(2026-11-25 확인) — One UI 의 알림 필터 '백그라운드 알림'
     * (noti_filter_background_notification)이 계속 떠 있는 알림을 접어 두는 줄(com.android.systemui 의 INSIGNIFICANT 알림)로,
     * 요약·그룹을 빼도, 알림 카테고리를 9가지로 바꿔 봐도 그대로였습니다. 앱에서는 없앨 수 없고 사용자가 그 필터를 꺼야 사라집니다.
     */
    private fun postSummary(context: Context, manager: NotificationManager, icon: Int) {
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("실시간 일정")
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
        manager.notify(SUMMARY_ID, summary)
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
            if (intent.action == LiveActivity.ACTION_TEMP_OFF) LiveActivity.setEnabled(context, false)
            runCatching { LiveActivity.update(context) }
            pending.finish()
        }
    }
}
