package com.skh.hihanaevo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/*
 * 구글 클래스룸 연동.
 *
 * 학교 Google 계정은 만 18세 미만이라 외부 앱의 Google 로그인(OAuth)이 학교 관리자 허용 없이는 막혀 있습니다. 대신 같은 학교
 * 계정으로 만든 Apps Script 웹 앱(classroom-bridge/Code.gs)은 학교 안의 앱으로 허용되므로, 그 스크립트가 학생 본인 권한으로
 * 15분마다 클래스룸을 읽어 암호화해 중계 서버(summary-worker /bridge)에 올리고, 앱은 그걸 받아 풉니다.
 *
 * 비밀키는 기기에서 만들어 스크립트에만 건넵니다(웹 앱 주소의 k). 서버에는 비밀키에서 만든 id 와 암호문만 있어 내용을 볼 수 없습니다.
 * 받은 글은 기기에만 둡니다.
 */

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

// MARK: - 연결 (Apps Script 중계)

class ClassroomException(message: String, val needsLogin: Boolean = false) : Exception(message)

object ClassroomBridge {
    private const val TAG = "ClassroomBridge"

    /** 학교 계정으로 배포한 웹 앱 — 실행 계정 '웹 앱에 액세스하는 사용자', 액세스 '하나고등학교의 모든 사용자'. 새 배포가 아니라 새 버전으로 올려야 주소가 유지됩니다. */
    const val SCRIPT_URL = "https://script.google.com/a/macros/hana.hs.kr/s/AKfycbxGrulUZa_ezE-akmMgvEh3njF2YWj8XuHuoOUQMKnsKJEdHIpePj-3th3Vo6Rqi1LS/exec"
    private const val WORKER = "https://hihana-summary.kyoohan0711ultra.workers.dev/bridge/"

    private const val PREFS_NAME = "google_classroom_bridge"
    private const val KEY_SECRET = "secret"
    private const val KEY_CONFIRMED = "confirmed"
    private const val KEY_EMAIL = "email"

    /** 이 빌드에서 연결할 수 있는지 — 웹 앱 주소가 있으면. */
    val available: Boolean get() = SCRIPT_URL.isNotBlank()

    /** 연결돼 내용을 한 번이라도 받았는지 — 설정·게시판 탭이 구독합니다. null 이면 아직 안 읽음. */
    val connected = mutableStateOf<Boolean?>(null)

    /** 브라우저에서 허용하러 갔고 아직 첫 내용을 못 받은 상태 — 앱으로 돌아오면 다시 확인합니다. */
    val pending = mutableStateOf(false)

    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    @Volatile private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val app = context.applicationContext
            val created = runCatching { open(app) }.getOrElse { first ->
                // 앱을 지웠다 깔면 자동 백업이 옛 암호화 파일을 되살리는데 마스터 키가 달라 못 읽습니다 — 지우고 새로 (다시 연결하면 됨).
                Log.w(TAG, "encrypted prefs unreadable, resetting", first)
                app.deleteSharedPreferences(PREFS_NAME)
                open(app)
            }
            cached = created
            return created
        }
    }

    private fun open(app: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            app, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun isConnected(context: Context): Boolean {
        val p = prefs(context)
        val on = available && p.getBoolean(KEY_CONFIRMED, false)
        val waiting = available && !on && p.contains(KEY_SECRET)
        if (connected.value != on) connected.value = on
        if (pending.value != waiting) pending.value = waiting
        return on
    }

    fun email(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)

    /**
     * 웹 앱을 크롬 커스텀 탭(없으면 기본 브라우저)으로 엽니다. 학생이 허용하면 스크립트가 비밀키를 받아 바로 한 번 올리고
     * '앱으로 돌아가기'로 [ClassroomLinkActivity] 를 엽니다. 다시 누르면 같은 비밀키로 다시 엽니다(권한을 다시 묻거나 바로 동기화).
     */
    fun beginSignIn(context: Context): Boolean {
        if (!available) return false
        val p = prefs(context)
        val secret = p.getString(KEY_SECRET, null) ?: randomSecret().also { p.edit().putString(KEY_SECRET, it).apply() }
        pending.value = !p.getBoolean(KEY_CONFIRMED, false)
        val uri = Uri.parse(SCRIPT_URL).buildUpon()
            .appendQueryParameter("k", secret)
            .appendQueryParameter("p", context.packageName)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // androidx.browser 없이 커스텀 탭으로 — 이 extra 가 있으면 크롬이 앱 안의 탭처럼 엽니다(세션 바인더는 없어도 됨).
            putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrElse {
            Log.w(TAG, "no browser", it)
            false
        }
    }

    /** 중계 서버에서 암호문을 받아 풉니다. 처음 받으면 연결됨으로 바꿉니다. */
    suspend fun fetch(context: Context): ClassroomSnapshot = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val secret = p.getString(KEY_SECRET, null) ?: throw ClassroomException("Google 클래스룸이 연결되어 있지 않습니다", needsLogin = true)
        val keys = Keys(secret)
        val req = Request.Builder().url(WORKER + keys.id).header("x-app", "hihana-neo").build()
        val body = http.newCall(req).execute().use { res ->
            when (res.code) {
                200 -> res.body?.string().orEmpty()
                404 -> throw ClassroomException(
                    if (p.getBoolean(KEY_CONFIRMED, false)) "중계 서버에 받은 내용이 없습니다. 잠시 뒤 다시 확인해 주세요"
                    else "아직 받은 내용이 없습니다. 브라우저에서 '허용'까지 마쳤다면 잠시 뒤 다시 확인해 주세요",
                )
                410 -> {
                    clear(context)
                    throw ClassroomException("클래스룸 연결이 끊겼습니다. 다시 연결해 주세요", needsLogin = true)
                }
                else -> throw IllegalStateException("bridge ${res.code}")
            }
        }
        val sealed = Base64.decode(JSONObject(body).getString("data"), Base64.DEFAULT)
        val payload = JSONObject(String(gunzip(keys.open(sealed)), Charsets.UTF_8))
        val snapshot = ClassroomApi.parse(payload)
        p.edit().putBoolean(KEY_CONFIRMED, true).putString(KEY_EMAIL, payload.optString("email").takeIf { it.isNotEmpty() }).apply()
        connected.value = true
        pending.value = false
        snapshot
    }

    /** 중계 서버의 암호문을 지우게 하고(다음 동기화에서 스크립트가 이 기기 몫을 멈춤) 기기의 비밀키·캐시를 지웁니다. */
    suspend fun disconnect(context: Context) = withContext(Dispatchers.IO) {
        prefs(context).getString(KEY_SECRET, null)?.let { secret ->
            runCatching {
                val req = Request.Builder().url(WORKER + Keys(secret).id).header("x-app", "hihana-neo").delete().build()
                http.newCall(req).execute().close()
            }.onFailure { Log.w(TAG, "bridge delete failed", it) }
        }
        clear(context)
    }

    private fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        connected.value = false
        pending.value = false
        ClassroomStore.clear(context)
    }

    private fun randomSecret(): String {
        val buf = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

    /**
     * 스크립트의 derive/seal 과 짝 — 비밀키 S 에서 id = HMAC(S,"id") 앞 16바이트(hex), enc·mac 키를 만들고,
     * 봉투(nonce 16 ‖ 암호문 ‖ 태그 32)의 태그를 확인한 뒤 HMAC 키스트림으로 풉니다.
     */
    private class Keys(secret: String) {
        private val s = Base64.decode(secret, Base64.URL_SAFE)
        val id: String = hmac(s, "id".toByteArray()).copyOf(16).joinToString("") { "%02x".format(it) }
        private val enc = hmac(s, "enc".toByteArray())
        private val mac = hmac(s, "mac".toByteArray())

        fun open(sealed: ByteArray): ByteArray {
            if (sealed.size < 48) throw IllegalStateException("bridge payload too short")
            val nonce = sealed.copyOfRange(0, 16)
            val cipher = sealed.copyOfRange(16, sealed.size - 32)
            val tag = sealed.copyOfRange(sealed.size - 32, sealed.size)
            if (!MessageDigest.isEqual(tag, hmac(mac, nonce, cipher))) throw IllegalStateException("bridge payload tampered")
            val out = ByteArray(cipher.size)
            var block = 0
            var i = 0
            while (i < cipher.size) {
                val ks = hmac(enc, nonce, byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
                for (j in 0 until minOf(32, cipher.size - i)) out[i + j] = (cipher[i + j].toInt() xor ks[j].toInt()).toByte()
                i += 32
                block++
            }
            return out
        }

        private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                parts.forEach { update(it) }
                doFinal()
            }
    }
}

/**
 * 웹 앱의 '앱으로 돌아가기'(`hihananeo://classroom`)가 여는 자리 — 화면 없이 메인의 클래스룸 탭으로 돌아갑니다.
 * 받아 오기는 메인이 다시 보일 때 합니다.
 */
class ClassroomLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_CLASSROOM)
            },
        )
        finish()
    }
}

// MARK: - 모델

enum class ClassroomKind(val label: String) { ANNOUNCEMENT("공지"), WORK("과제"), MATERIAL("자료") }

data class ClassroomCourse(val id: String, val name: String, val section: String?, val link: String?)

data class ClassroomAttachment(val title: String, val url: String)

/** 클래스룸 스트림의 글 하나 — 공지·과제·자료를 한 꼴로. 과제만 [due]·[submitted] 가 있습니다. */
data class ClassroomPost(
    val id: String,
    val courseId: String,
    val kind: ClassroomKind,
    val title: String,
    val text: String,
    val link: String?,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val due: LocalDateTime? = null,
    /** 마감 시각 없이 날짜만 — 그날 자정(KST)으로 둡니다. */
    val dueAllDay: Boolean = false,
    val maxPoints: Double? = null,
    val submitted: Boolean = false,
    val late: Boolean = false,
    val grade: Double? = null,
    val attachments: List<ClassroomAttachment> = emptyList(),
) {
    /** 과제인데 아직 안 냈고 마감이 있으면 그 날짜 — 통합 일정에 올립니다. */
    val pendingDue: LocalDateTime? get() = if (kind == ClassroomKind.WORK && !submitted) due else null
}

data class ClassroomSnapshot(
    val courses: List<ClassroomCourse>,
    val posts: List<ClassroomPost>,
    val fetchedAt: Long,
) {
    fun course(id: String): ClassroomCourse? = courses.find { it.id == id }

    /** 숨긴 수업([ClassroomCourseStore])을 뺀 스냅샷 — 클래스룸 탭 목록·배지·홈 일정이 모두 이걸 씁니다. */
    fun visible(hidden: Set<String>): ClassroomSnapshot =
        if (hidden.isEmpty()) this else copy(courses = courses.filter { it.id !in hidden }, posts = posts.filter { it.courseId !in hidden })

    /** 통합 일정용 — 안 낸 과제의 마감. */
    fun agenda(): List<AgendaItem> = posts.mapNotNull { post ->
        val due = post.pendingDue ?: return@mapNotNull null
        AgendaItem(
            id = "c:${post.courseId}:${post.id}",
            source = AgendaSource.CLASSROOM,
            title = post.title,
            date = due.toLocalDate(),
            time = if (post.dueAllDay) null else "%02d:%02d".format(due.hour, due.minute),
            deadline = true,
            detail = course(post.courseId)?.name,
            url = post.link,
        )
    }
}


// MARK: - 읽기

object ClassroomApi {
    /** 공지에서 일정을 뽑는 범위 — 최근 글만, 한 번에 몇 건까지만(요약 서버 하루 사용량 보호). 이미 뽑은 글은 기기 캐시로 건너뜁니다. */
    private const val EVENT_DAYS = 30L
    private const val EVENT_BATCH = 25

    suspend fun fetch(context: Context): ClassroomSnapshot = ClassroomBridge.fetch(context)

    /** 스크립트가 보낸 꼴(Code.gs 의 collect) → 스냅샷. 시각은 UTC 로 오므로 한국 시각으로 바꿉니다. */
    fun parse(o: JSONObject): ClassroomSnapshot {
        val courses = o.optJSONArray("courses").objects().map { c ->
            ClassroomCourse(c.getString("id"), c.optString("name"), c.optStr("section"), c.optStr("link"))
        }
        val posts = o.optJSONArray("posts").objects().mapNotNull { p ->
            val kind = when (p.optString("k")) {
                "A" -> ClassroomKind.ANNOUNCEMENT
                "W" -> ClassroomKind.WORK
                "M" -> ClassroomKind.MATERIAL
                else -> return@mapNotNull null
            }
            val text = p.optString("text").trim()
            val d = p.optJSONObject("due")
            val allDay = d != null && d.isNull("h")
            val due = d?.let {
                val date = runCatching { LocalDate.of(it.getInt("y"), it.getInt("m"), it.getInt("d")) }.getOrNull()
                when {
                    date == null -> null
                    allDay -> date.atTime(23, 59)
                    // 마감 시각은 UTC — 한국 시각으로 바꿉니다 (오전 0시 마감이 전날 15시로 적혀 옵니다).
                    else -> date.atTime(it.optInt("h"), it.optInt("mi")).atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toLocalDateTime()
                }
            }
            val state = p.optStr("st")
            ClassroomPost(
                id = p.getString("id"), courseId = p.getString("c"), kind = kind,
                title = if (kind == ClassroomKind.ANNOUNCEMENT) text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: "(내용 없음)"
                else p.optStr("title") ?: "(제목 없음)",
                text = text, link = p.optStr("link"),
                created = time(p.optString("cr")), updated = time(p.optString("up")),
                due = due, dueAllDay = due != null && allDay,
                maxPoints = p.optDouble("pts").takeIf { !it.isNaN() },
                submitted = state == "TURNED_IN" || state == "RETURNED",
                late = p.optBoolean("late"),
                grade = p.optDouble("grade").takeIf { !it.isNaN() },
                attachments = p.optJSONArray("att").objects().mapNotNull { a -> a.optStr("u")?.let { ClassroomAttachment(a.optString("t").ifBlank { it }, it) } },
            )
        }
        return ClassroomSnapshot(courses, posts.sortedByDescending { it.updated }, System.currentTimeMillis())
    }

    /**
     * 선생님이 글로만 올린 공지("금요일까지 제출")에서 게시판 글처럼 AI 로 일정을 뽑아 통합 일정에 올립니다([BoardAgendaStore], 키 cr_).
     * AI 요약을 꺼 두면 보내지 않습니다. 한 건씩 차례로, 실패해도 다음 글은 계속합니다.
     */
    suspend fun extractEvents(context: Context, snapshot: ClassroomSnapshot) {
        if (!PostSummarizer.isEnabled(context)) return
        val since = LocalDateTime.now(KST).minusDays(EVENT_DAYS)
        val targets = snapshot.posts
            .filter { it.kind == ClassroomKind.ANNOUNCEMENT && it.created.isAfter(since) && it.text.isNotBlank() }
            .filter { PostSummarizer.cached(context, eventKey(it)) == null }
            .take(EVENT_BATCH)
        for (post in targets) {
            try {
                val course = snapshot.course(post.courseId)?.name ?: "클래스룸"
                PostSummarizer.ensure(context, eventKey(post), "$course 공지", post.text, url = post.link, posted = post.created.toLocalDate().toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Classroom", "event extract failed ${post.id}", e)
            }
        }
    }

    fun eventKey(post: ClassroomPost): String = "cr_${post.id}"

    private fun time(raw: String): LocalDateTime =
        runCatching { Instant.parse(raw).atZone(KST).toLocalDateTime() }.getOrElse { LocalDateTime.now(KST) }

    private fun JSONObject.optStr(key: String): String? = optString(key).takeIf { it.isNotEmpty() && it != "null" }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

// MARK: - 보관

/** 마지막 스냅샷 — 앱을 열면 파일에서 바로 보여 주고, 10분 지났으면 뒤에서 새로 받습니다. */
object ClassroomStore {
    private const val FILE = "classroom_snapshot.json"
    const val TTL_MS = 10 * 60_000L

    val snapshot = mutableStateOf<ClassroomSnapshot?>(null)
    private var loaded = false

    /** 디버그 빌드의 Dev 탭 '샘플 데이터' — 네트워크·연결 없이 화면만 봅니다. 앱을 다시 켜면 풀립니다. */
    val sampleMode = mutableStateOf(false)

    /** Dev 탭 전용 — 연결된 것처럼 보이는 샘플 스냅샷(과목 두 개, 공지·과제·자료)과 게시글 일정 두 건. */
    fun installSample(context: Context) {
        val now = LocalDateTime.now()
        val courses = listOf(
            ClassroomCourse("c1", "2학년 수학Ⅱ", "2-3", "https://classroom.google.com/"),
            ClassroomCourse("c2", "영어 독해와 작문", null, "https://classroom.google.com/"),
        )
        val posts = listOf(
            ClassroomPost("w1", "c1", ClassroomKind.WORK, "수열의 극한 연습문제 3단원", "교과서 p.112~118 홀수 번 풀어서 사진으로 제출", "https://classroom.google.com/",
                now.minusDays(2), now.minusDays(2), due = now.plusDays(2).withHour(23).withMinute(59), maxPoints = 10.0),
            ClassroomPost("w2", "c2", ClassroomKind.WORK, "Unit 5 Writing: My Future Career", "200 words, double-spaced. Submit as Google Docs.", "https://classroom.google.com/",
                now.minusDays(5), now.minusDays(1), due = now.plusDays(6).withHour(9).withMinute(0), maxPoints = 20.0, submitted = true, grade = 18.0),
            ClassroomPost("w3", "c1", ClassroomKind.WORK, "모의고사 오답노트", "", "https://classroom.google.com/",
                now.minusDays(9), now.minusDays(9), due = now.minusDays(1).withHour(18).withMinute(0), maxPoints = 5.0),
            ClassroomPost("a1", "c1", ClassroomKind.ANNOUNCEMENT, "다음 주 수행평가 범위 안내", "다음 주 수행평가 범위 안내\n3단원 전체입니다. 공식 정리본은 자료에 올렸으니 참고하세요.\nhttps://classroom.google.com/", "https://classroom.google.com/",
                now.minusHours(5), now.minusHours(5)),
            ClassroomPost("m1", "c1", ClassroomKind.MATERIAL, "3단원 공식 정리본", "시험 전까지 꼭 한 번 읽어 두세요.", "https://classroom.google.com/",
                now.minusDays(1), now.minusDays(1), attachments = listOf(ClassroomAttachment("3단원_공식정리.pdf", "https://drive.google.com/"))),
        ).sortedByDescending { it.updated }
        snapshot.value = ClassroomSnapshot(courses, posts, System.currentTimeMillis())
        sampleMode.value = true
        val today = LocalDate.now()
        BoardAgendaStore.record(
            context, "sample_1", "2학기 교내 수학경시대회 참가 신청 안내", "https://hh.hana.hs.kr/main/board/1/9001/view.do",
            listOf(
                PostEvent("수학경시대회 신청 마감", today.plusDays(3), deadline = true),
                PostEvent("수학경시대회", today.plusDays(10), time = "15:10"),
            ),
        )
        BoardAgendaStore.record(
            context, "sample_2", "학부모 상담 주간 안내", "https://hh.hana.hs.kr/main/board/4/9002/view.do",
            listOf(PostEvent("학부모 상담 주간", today.plusDays(13), end = today.plusDays(17))),
        )
    }

    fun load(context: Context): ClassroomSnapshot? {
        if (!loaded) {
            loaded = true
            runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.let(::decode) }
                .onFailure { Log.w("Classroom", "cache unreadable", it) }
                .getOrNull()?.let { snapshot.value = it }
        }
        return snapshot.value
    }

    fun save(context: Context, s: ClassroomSnapshot) {
        snapshot.value = s
        runCatching { File(context.filesDir, FILE).writeText(encode(s)) }.onFailure { Log.w("Classroom", "cache write failed", it) }
    }

    fun clear(context: Context) {
        snapshot.value = null
        File(context.filesDir, FILE).delete()
        ClassroomReadStore.clear(context)
        BoardAgendaStore.clear(context, prefix = "cr_")
    }

    private fun encode(s: ClassroomSnapshot): String = JSONObject()
        .put("fetchedAt", s.fetchedAt)
        .put("courses", JSONArray(s.courses.map { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("section", c.section ?: JSONObject.NULL).put("link", c.link ?: JSONObject.NULL)
        }))
        .put("posts", JSONArray(s.posts.map { p ->
            JSONObject()
                .put("id", p.id).put("courseId", p.courseId).put("kind", p.kind.name).put("title", p.title).put("text", p.text)
                .put("link", p.link ?: JSONObject.NULL).put("created", p.created.toString()).put("updated", p.updated.toString())
                .put("due", p.due?.toString() ?: JSONObject.NULL).put("dueAllDay", p.dueAllDay)
                .put("maxPoints", p.maxPoints ?: JSONObject.NULL).put("submitted", p.submitted).put("late", p.late)
                .put("grade", p.grade ?: JSONObject.NULL)
                .put("attachments", JSONArray(p.attachments.map { a -> JSONObject().put("title", a.title).put("url", a.url) }))
        }))
        .toString()

    private fun decode(raw: String): ClassroomSnapshot {
        val o = JSONObject(raw)
        fun JSONObject.str(k: String): String? = optString(k).takeIf { it.isNotEmpty() && it != "null" }
        val courses = o.getJSONArray("courses").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }.map { c ->
            ClassroomCourse(c.getString("id"), c.optString("name"), c.str("section"), c.str("link"))
        }
        val posts = o.getJSONArray("posts").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }.map { p ->
            val att = p.optJSONArray("attachments")
            ClassroomPost(
                id = p.getString("id"), courseId = p.getString("courseId"), kind = ClassroomKind.valueOf(p.getString("kind")),
                title = p.optString("title"), text = p.optString("text"), link = p.str("link"),
                created = LocalDateTime.parse(p.getString("created")), updated = LocalDateTime.parse(p.getString("updated")),
                due = p.str("due")?.let { LocalDateTime.parse(it) }, dueAllDay = p.optBoolean("dueAllDay"),
                maxPoints = p.optDouble("maxPoints").takeIf { !it.isNaN() }, submitted = p.optBoolean("submitted"), late = p.optBoolean("late"),
                grade = p.optDouble("grade").takeIf { !it.isNaN() },
                attachments = if (att == null) emptyList() else (0 until att.length()).map { att.getJSONObject(it) }.map { ClassroomAttachment(it.optString("title"), it.optString("url")) },
            )
        }
        return ClassroomSnapshot(courses, posts, o.optLong("fetchedAt"))
    }
}

/** 클래스룸 탭에 보일 수업 고르기 — 숨긴 수업 id 만 적어 둡니다(새로 생긴 수업은 기본으로 보임). */
object ClassroomCourseStore {
    private const val PREFS = "classroom_courses"
    private const val KEY_HIDDEN = "hidden"

    /** 숨긴 수업 — 탭·홈이 구독합니다. 처음 읽기 전엔 비어 있습니다. */
    val hidden = mutableStateOf<Set<String>>(emptySet())
    private var loaded = false

    fun load(context: Context): Set<String> {
        if (!loaded) {
            loaded = true
            hidden.value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
        }
        return hidden.value
    }

    fun set(context: Context, ids: Set<String>) {
        hidden.value = ids
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_HIDDEN, ids).apply()
    }
}

/** 클래스룸 글 읽음 — 게시판처럼 "내가 아직 안 연 글"을 세어 세그먼트 배지에. 처음 연결할 땐 그때까지의 글을 모두 읽은 것으로. */
object ClassroomReadStore {
    private const val PREFS = "classroom_read"
    private const val KEY_IDS = "ids"
    private const val KEY_SEEDED = "seeded"
    val revision = mutableIntStateOf(0)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun ids(context: Context): Set<String> = prefs(context).getStringSet(KEY_IDS, emptySet()).orEmpty()

    /** 읽은 글 id 전부 — 목록이 줄마다 묻지 않고 한 번에 받습니다. */
    fun readIds(context: Context): Set<String> = ids(context)

    fun unreadCount(context: Context, posts: List<ClassroomPost>): Int = ids(context).let { seen -> posts.count { it.id !in seen } }

    fun markRead(context: Context, posts: Collection<ClassroomPost>) {
        val p = prefs(context)
        val next = ids(context) + posts.map { it.id }
        p.edit().putStringSet(KEY_IDS, next).apply()
        revision.intValue++
    }

    fun seedIfNeeded(context: Context, posts: List<ClassroomPost>) {
        val p = prefs(context)
        if (p.getBoolean(KEY_SEEDED, false)) return
        p.edit().putStringSet(KEY_IDS, posts.map { it.id }.toSet()).putBoolean(KEY_SEEDED, true).apply()
        revision.intValue++
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        revision.intValue++
    }
}
