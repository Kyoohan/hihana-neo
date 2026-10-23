package com.skh.hihanaevo

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 데일리 브리핑 — 오늘 수업·일정과 앞으로 7일 일정 중 중요한 것(수행평가·시험·마감)을 AI 가 두세 문장으로 풀어 줍니다.
 * 시간축 머리와 홈의 '오늘 브리프' 카드에 보이고, 아침(평일 7:00, 주말 8:30)엔 알림으로 한 번 보냅니다.
 *
 * 요약 서버(/brief)에는 과목 이름·일정 제목·날짜만 보내고, 서버는 보관하지 않습니다. 앱이 하루 한 장을 기기에 두고
 * 일정이 바뀌면(새 마감이 생기면) 다시 만듭니다. 서버에 못 닿거나 AI 를 끄면 AI 없이 중요한 일정만 추려 같은 자리에 둡니다.
 */
object MorningBrief {
    private const val TAG = "MorningBrief"
    private const val ENDPOINT = "https://hihana-summary.kyoohan0711ultra.workers.dev/brief"
    private const val FILE = "morning_brief.json"
    private const val PREFS = "morning_brief"
    private const val KEY_AI = "ai"
    private const val KEY_NOTIFY = "notify"
    private const val KEY_NOTIFIED = "notified_day"
    private const val KEY_SEEN = "seen_day"
    private const val CHANNEL_ID = "morning_brief"
    private const val NOTIFICATION_ID = 7401
    /** 같은 날 일정이 바뀌어도 이보다 자주 AI 로 다시 만들지 않습니다(바뀐 재료는 다음 기회에). */
    private const val MIN_REMAKE_MS = 20 * 60_000L
    private const val MAX_ITEMS = 30
    private const val DAYS_AHEAD = 7L

    /** 수행평가·시험 — 제목으로 가립니다(학사일정·게시글·클래스룸 어디서 왔든). */
    private val EXAM = Regex("수행|평가|시험|고사|퀴즈|쪽지")

    data class Highlight(val date: LocalDate, val title: String, val note: String)

    data class Brief(
        val date: LocalDate,
        val headline: String,
        val body: String,
        val highlights: List<Highlight>,
        /** AI 가 만들었는지 — false 면 기기에서 일정만 추린 것. */
        val ai: Boolean,
        /** 만든 재료의 지문 — 재료가 바뀌면 다시 만듭니다. */
        val inputKey: String,
        val madeAt: Long,
    )

    /** 오늘 브리핑 — 홈이 구독합니다. 날짜가 지난 것은 화면에서 거릅니다. */
    val current = mutableStateOf<Brief?>(null)
    val loading = mutableStateOf(false)

    private val lock = Mutex()
    private var loaded = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // MARK: 설정

    /** AI 로 만들기 — 끄면 요약 서버로 아무것도 보내지 않고 기기에서 추립니다. */
    fun aiEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AI, true)

    fun setAiEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AI, on).apply()
    }

    /** 홈에서 그날 브리핑을 본 적이 있는지 — 데일리 브리핑 카드의 자리(맨 위·맨 아래)와 펼침을 정합니다. */
    fun seen(context: Context, date: LocalDate): Boolean = prefs(context).getString(KEY_SEEN, null) == date.toString()

    fun markSeen(context: Context, date: LocalDate) {
        prefs(context).edit().putString(KEY_SEEN, date.toString()).apply()
    }

    fun notifyEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTIFY, true)

    fun setNotifyEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFY, on).apply()
        schedule(context)
    }

    // MARK: 보관

    fun load(context: Context): Brief? {
        if (!loaded) {
            loaded = true
            runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.let(::decode) }
                .onFailure { Log.w(TAG, "cache unreadable", it) }
                .getOrNull()?.let { current.value = it }
        }
        return current.value
    }

    private fun save(context: Context, brief: Brief) {
        current.value = brief
        runCatching { File(context.filesDir, FILE).writeText(encode(brief).toString()) }.onFailure { Log.w(TAG, "cache write failed", it) }
    }

    private fun encode(b: Brief): JSONObject = JSONObject()
        .put("date", b.date.toString())
        .put("headline", b.headline)
        .put("body", b.body)
        .put("highlights", JSONArray(b.highlights.map { JSONObject().put("date", it.date.toString()).put("title", it.title).put("note", it.note) }))
        .put("ai", b.ai)
        .put("key", b.inputKey)
        .put("at", b.madeAt)

    private fun decode(raw: String): Brief {
        val o = JSONObject(raw)
        return Brief(
            date = LocalDate.parse(o.getString("date")),
            headline = o.getString("headline"),
            body = o.getString("body"),
            highlights = highlights(o.optJSONArray("highlights")),
            ai = o.optBoolean("ai"),
            inputKey = o.optString("key"),
            madeAt = o.optLong("at"),
        )
    }

    private fun highlights(arr: JSONArray?): List<Highlight> = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
        val h = arr!!.optJSONObject(i) ?: return@mapNotNull null
        val date = runCatching { LocalDate.parse(h.optString("date")) }.getOrNull() ?: return@mapNotNull null
        Highlight(date, h.optString("title"), h.optString("note"))
    }

    // MARK: 재료

    /** 브리핑 재료 — 서버로 보낼 모양([json])과 기기에서 추릴 때 쓰는 일정([items]). */
    private class Input(val classes: List<String>, val items: List<AgendaItem>, val json: JSONObject) {
        val key: String = json.toString().hashCode().toString(16)
    }

    /**
     * 오늘 수업(과목·시각)과 오늘부터 7일 안의 일정. 면학감독(선생님 이름)은 빼고, 이미 낸 과제도 뺍니다. 방과후 강의 기간처럼
     * 진작 시작해 이번 주에 끝나지 않는 운영 기간도 뺍니다 — 매일 브리핑에 같은 말이 끼었습니다.
     */
    private suspend fun input(context: Context, today: LocalDate): Input {
        HanaTimetableSync.ensureInstalled(context, today)
        val classes = Timetable.blocks(today) { null }.mapNotNull { b ->
            val lesson = b.kind as? BlockKind.LessonKind ?: return@mapNotNull null
            if (lesson.lesson.isFree) null else "${lesson.period}교시 %02d:%02d ${lesson.lesson.title}".format(b.start.hour, b.start.minute)
        }
        val schedule = runCatching { HanaAcademicRepository.cachedSchedule(context) }.getOrDefault(emptyList())
        val grade = PlanStore.studentGrade(context)
        val hidden = ClassroomCourseStore.load(context)
        val all = ClassroomStore.load(context)
        val hiddenPosts = all?.posts?.filter { it.courseId in hidden }?.map { it.id }?.toSet().orEmpty()
        val extras = BoardAgendaStore.items(context)
            .filter { it.forGrade(grade) }
            .filterNot { it.source == AgendaSource.CLASSROOM && it.id.removePrefix("cr:").substringBefore(':') in hiddenPosts } +
            (all?.visible(hidden)?.agenda() ?: emptyList())
        val last = today.plusDays(DAYS_AHEAD)
        val items = Agenda.merge(schedule, grade, extras, today)
            .filter { it.academic?.code != SUPERVISION_CODE && !it.done && !it.date.isAfter(last) }
            .filterNot { it.date.isBefore(today) && (it.endDate ?: it.date).isAfter(last) }
            .take(MAX_ITEMS)
        val json = JSONObject()
            .put("date", today.toString())
            .put("classes", JSONArray(classes))
            .put("items", JSONArray(items.map { item ->
                JSONObject()
                    .put("date", item.date.toString())
                    .put("end", item.endDate?.takeIf { it != item.date }?.toString() ?: JSONObject.NULL)
                    .put("time", item.time ?: JSONObject.NULL)
                    .put("title", item.title)
                    .put("kind", kind(item))
                    .put("from", item.detail?.take(40) ?: JSONObject.NULL)
            }))
        return Input(classes, items, json)
    }

    private fun kind(item: AgendaItem): String = when {
        EXAM.containsMatchIn(item.title) -> "시험·수행평가"
        item.source == AgendaSource.CLASSROOM && item.deadline -> "과제 마감"
        item.deadline -> "마감"
        item.academic != null -> scheduleLabel(item.academic.code)
        else -> "행사"
    }

    // MARK: 만들기

    /**
     * 오늘 브리핑을 돌려줍니다 — 재료가 그대로면 둔 것을, 바뀌었으면 새로(AI 는 20분에 한 번까지). AI 를 켜고 끄면 그 결로 새로.
     * AI 가 실패하면 오늘 이미 AI 로 만든 것이 있으면 그걸 두고, 없으면 기기에서 추린 브리핑을 둡니다.
     */
    suspend fun ensure(context: Context): Brief? = lock.withLock {
        val app = context.applicationContext
        val today = PlanStore.today()
        val old = withContext(Dispatchers.IO) { load(app) }?.takeIf { it.date == today }
        val input = try {
            withContext(Dispatchers.IO) { input(app, today) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "input failed", e)
            return@withLock old
        }
        val useAi = aiEnabled(app)
        val now = System.currentTimeMillis()
        if (old != null) {
            val sameKind = old.ai == useAi
            if (sameKind && old.inputKey == input.key) return@withLock old
            if (sameKind && old.ai && now - old.madeAt < MIN_REMAKE_MS) return@withLock old
        }
        if (!useAi) return@withLock local(today, input).also { save(app, it) }
        loading.value = true
        try {
            val made = withContext(Dispatchers.IO) { request(today, input) }
            save(app, made)
            made
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "brief failed", e)
            // 다시 시도는 20분 뒤에 — 둔 것의 시각만 당겨 둡니다.
            val keep = old?.takeIf { it.ai }?.copy(madeAt = now) ?: local(today, input)
            save(app, keep)
            keep
        } finally {
            loading.value = false
        }
    }

    private fun request(today: LocalDate, input: Input): Brief {
        val body = input.json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(ENDPOINT).header("x-app", "hihana-neo").post(body).build()
        client.newCall(req).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("brief ${response.code}: ${raw.take(200)}")
            val o = JSONObject(raw)
            val headline = o.optString("headline").trim()
            val text = o.optString("body").trim()
            if (headline.isEmpty() || text.isEmpty()) throw IllegalStateException("brief empty")
            return Brief(today, headline, text, highlights(o.optJSONArray("highlights")), ai = true, inputKey = input.key, madeAt = System.currentTimeMillis())
        }
    }

    /** AI 없이 — 시험·수행평가 → 마감 → 오늘 일정 → 휴업 순으로 넷까지, 머리는 그중 첫째. */
    private fun local(today: LocalDate, input: Input): Brief {
        fun rank(item: AgendaItem): Int = when {
            EXAM.containsMatchIn(item.title) -> 0
            item.deadline -> 1
            item.daysFrom(today) == 0L -> 2
            item.academic?.code == "SG01020000" -> 3
            else -> 9
        }
        val top = input.items.filter { rank(it) < 9 }.sortedWith(compareBy({ rank(it) }, { it.date })).take(4)
        val exams = input.items.count { EXAM.containsMatchIn(it.title) }
        val deadlines = input.items.count { it.deadline && !EXAM.containsMatchIn(it.title) }
        val todayCount = input.items.count { it.daysFrom(today) == 0L }
        val headline = top.firstOrNull()?.let { "${relativeDay(today, it.date)} ${it.title}" }
            ?: if (input.classes.isEmpty()) "오늘은 수업이 없어요" else "오늘 수업 ${input.classes.size}개"
        val body = buildList {
            if (todayCount > 0) add("오늘 일정 ${todayCount}개")
            if (exams > 0) add("7일 안에 시험·수행평가 ${exams}개")
            if (deadlines > 0) add("마감 ${deadlines}개")
        }.joinToString(", ").let { if (it.isEmpty()) "이번 주는 큰 마감이 없어요." else "${it}가 있어요." }
        val highlights = top.map { item ->
            Highlight(item.date, item.title, listOfNotNull(kind(item), item.time).joinToString(" · "))
        }
        return Brief(today, headline, body, highlights, ai = false, inputKey = input.key, madeAt = System.currentTimeMillis())
    }

    /** "오늘", "내일", "모레", 그 뒤는 "10/26(월)". */
    fun relativeDay(today: LocalDate, date: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "오늘"
        1L -> "내일"
        2L -> "모레"
        else -> "${date.monthValue}/${date.dayOfMonth}(${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"
    }

    // MARK: 아침 알림

    /** 평일 7:00(기상 6:50 직후), 주말 8:30. */
    private fun timeFor(date: LocalDate): LocalTime =
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) LocalTime.of(8, 30) else LocalTime.of(7, 0)

    /** 다음 아침 알람을 겁니다(알림을 끄면 거둡니다). 시각이 정해져 있어 프로세스가 뜰 때마다 다시 걸어도 밀리지 않습니다. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            app, NOTIFICATION_ID, Intent(app, MorningBriefReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!notifyEnabled(app)) {
            alarm.cancel(pending)
            return
        }
        val now = ZonedDateTime.now(PlanStore.seoulZone)
        var day = now.toLocalDate()
        if (!now.toLocalTime().isBefore(timeFor(day))) day = day.plusDays(1)
        val at = day.atTime(timeFor(day)).atZone(PlanStore.seoulZone).toInstant().toEpochMilli()
        val exact = ExactAlarmPermission.isGranted(app) &&
            runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }.isSuccess
        if (!exact) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    internal fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<MorningBriefWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("morning_brief", ExistingWorkPolicy.KEEP, request)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "데일리 브리핑", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "아침마다 오늘 일정과 이번 주에 챙길 것을 알려 줍니다"
                },
            )
        }
    }

    /**
     * 아침 알림 — 하루 한 번. 네트워크를 기다리다 점심이 지나 버렸으면(평일 12:10, 주말 13:30) 보내지 않습니다.
     * 수업도 일정도 없는 날(방학·귀가 중)엔 조용히 넘어갑니다.
     */
    suspend fun notifyMorning(context: Context) {
        val app = context.applicationContext
        if (!notifyEnabled(app) || !AlimNotifier.canNotify(app)) return
        val today = PlanStore.today()
        val p = prefs(app)
        if (p.getString(KEY_NOTIFIED, null) == today.toString()) return
        if (!briefMorning(LocalDateTime.now(PlanStore.seoulZone))) return
        val brief = ensure(app) ?: return
        if (brief.highlights.isEmpty() && Timetable.blocks(today) { null }.none { it.kind is BlockKind.LessonKind }) return
        ensureChannel(app)
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(app, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(app, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val long = buildString {
            append(brief.body)
            brief.highlights.forEach { h -> append("\n• ").append(relativeDay(today, h.date)).append(" ").append(h.title) }
        }
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hihana)
            .setContentTitle(brief.headline)
            .setContentText(brief.body)
            .setSubText("데일리 브리핑")
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(brief.headline).bigText(long))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        p.edit().putString(KEY_NOTIFIED, today.toString()).apply()
    }
}

/** 아침 알람(과 부팅 완료) — 다음 날 알람을 걸고, 알람이면 브리핑 작업을 맡깁니다. */
class MorningBriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MorningBrief.schedule(context)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) MorningBrief.enqueue(context)
    }
}

class MorningBriefWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { MorningBrief.notifyMorning(applicationContext) }.onFailure { Log.w("MorningBrief", "notify failed", it) }
        return Result.success()
    }
}
