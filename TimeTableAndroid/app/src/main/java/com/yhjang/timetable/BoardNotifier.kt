package com.yhjang.timetable

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * 게시판 새 글 알림 — 게시판마다 켜고 끌 수 있고, 켜진 게시판만 주기 동기화([worker.HanaSyncWorker])에서
 * 새로 받아 마지막으로 본 글 이후의 글만 알립니다.
 *
 * "본 글"의 기준은 두 가지입니다: (1) 알림을 이미 보낸 글, (2) 앱 안에서 그 게시판 목록을 실제로 연 시점의 글.
 * 게시판을 처음 켤 때는 지금 있는 글을 전부 본 것으로 처리해, 켜자마자 옛 글 수십 건이 쏟아지지 않게 합니다.
 */
object BoardNotifier {

    const val CHANNEL_ID = "board"
    const val EXTRA_OPEN_POST_URL = "com.yhjang.timetable.OPEN_POST_URL"
    const val EXTRA_OPEN_BOARD = "com.yhjang.timetable.OPEN_BOARD"

    private const val PREFS_NAME = "board_notify"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SEEN_PREFIX = "seen_"
    private const val MAX_KEEP = 300

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // MARK: 설정

    fun enabledCategories(context: Context): Set<BoardCategory> {
        val raw = prefs(context).getStringSet(KEY_ENABLED, emptySet()).orEmpty()
        return BoardCategory.entries.filter { it.bmtIdx.toString() in raw }.toSet()
    }

    fun isEnabled(context: Context, category: BoardCategory): Boolean = category in enabledCategories(context)

    fun setEnabled(context: Context, category: BoardCategory, enabled: Boolean) {
        val current = prefs(context).getStringSet(KEY_ENABLED, emptySet()).orEmpty().toMutableSet()
        if (enabled) current += category.bmtIdx.toString() else current -= category.bmtIdx.toString()
        prefs(context).edit().putStringSet(KEY_ENABLED, current).apply()
    }

    // MARK: 본 글

    private fun seenKey(category: BoardCategory) = KEY_SEEN_PREFIX + category.bmtIdx

    private fun hasBaseline(context: Context, category: BoardCategory): Boolean =
        prefs(context).contains(seenKey(category))

    private fun seen(context: Context, category: BoardCategory): Set<String> =
        prefs(context).getStringSet(seenKey(category), emptySet()).orEmpty()

    /** 알림을 보낸(또는 기준선으로 잡은) 글을 기록합니다 — 앱에서 글을 봤는지와는 무관합니다. */
    fun markSeen(context: Context, category: BoardCategory, posts: List<HanaBoardPost>) {
        if (posts.isEmpty() && hasBaseline(context, category)) return
        val merged = (seen(context, category) + posts.map { it.key }).toList().takeLast(MAX_KEEP).toSet()
        prefs(context).edit().putStringSet(seenKey(category), merged).apply()
    }

    private val HanaBoardPost.key: String get() = "$bmtIdx:$bdIdx"

    // MARK: 알림

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "게시판 새 글", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "선택한 게시판에 새 글이 올라오면 알립니다"
                },
            )
        }
    }

    /**
     * 새로 받은 [posts] 중 본 적 없는 글을 알립니다. 게시판을 켠 뒤 첫 확인이면 알리지 않고 기준선만 잡습니다.
     * 새 글이 하나면 제목을, 여럿이면 "n건" 요약 + 제목 목록을 한 알림으로 보냅니다.
     */
    fun process(context: Context, category: BoardCategory, posts: List<HanaBoardPost>) {
        if (posts.isEmpty()) return
        if (!hasBaseline(context, category)) {
            markSeen(context, category, posts)
            return
        }
        val fresh = posts.filter { it.key !in seen(context, category) }
        if (fresh.isEmpty()) return
        markSeen(context, category, fresh)

        if (!AlimNotifier.canNotify(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return

        val first = fresh.first()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ACADEMIC)
            putExtra(EXTRA_OPEN_BOARD, category.ordinal)
            // 한 건이면 그 글을 바로 열고, 여럿이면 게시판 목록으로 갑니다.
            if (fresh.size == 1) putExtra(EXTRA_OPEN_POST_URL, first.url)
        }
        val pending = PendingIntent.getActivity(
            context,
            category.bmtIdx * 100_000 + (first.bdIdx and 0xFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (fresh.size == 1) "${category.label} 새 글" else "${category.label} 새 글 ${fresh.size}건"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_book)
            .setContentTitle(title)
            .setContentText(first.title.ifEmpty { "(제목 없음)" })
            .setContentIntent(pending)
            .setAutoCancel(true)
        if (fresh.size == 1) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(first.title))
        } else {
            val inbox = NotificationCompat.InboxStyle()
            fresh.take(6).forEach { inbox.addLine(it.title.ifEmpty { "(제목 없음)" }) }
            if (fresh.size > 6) inbox.setSummaryText("외 ${fresh.size - 6}건")
            builder.setStyle(inbox)
        }
        manager.notify(CHANNEL_ID.hashCode() + category.bmtIdx, builder.build())
    }
}
