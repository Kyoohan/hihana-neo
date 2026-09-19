package com.yhjang.timetable

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

// MARK: - 계정 저장 (EncryptedSharedPreferences)

/**
 * 아이디/비밀번호를 암호화된 SharedPreferences 에 저장합니다 (iOS Keychain 대응).
 * EncryptedSharedPreferences/MasterKey 가 2026년 기준 deprecated 상태라 컴파일 경고가 뜨는데,
 * 아직 동작은 하니 그대로 씁니다 — 구글이 후속 API를 안정화하면 교체 필요.
 */
object HanaCredentialStore {

    private const val PREFS_NAME = "hana_credentials"
    private const val KEY_ID = "mem_id"
    private const val KEY_PWD = "mem_pwd"

    @Volatile private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val appContext = context.applicationContext
            val created = runCatching { open(appContext) }.getOrElse { first ->
                // 앱을 지웠다 다시 깔면 자동 백업이 예전 암호화 파일을 되살리는데, 키스토어의 마스터 키는 새로 만들어져
                // 복호화가 실패합니다(AEADBadTagException) — 예전엔 이게 실행마다 크래시로 이어졌습니다.
                // 되살릴 방법이 없으니 그 파일을 지우고 새로 만듭니다 (다시 로그인하면 됩니다).
                Log.w("HanaCredential", "encrypted prefs unreadable, resetting", first)
                appContext.deleteSharedPreferences(PREFS_NAME)
                open(appContext)
            }
            cached = created
            return created
        }
    }

    private fun open(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun memId(context: Context): String? = prefs(context).getString(KEY_ID, null)
    fun memPwd(context: Context): String? = prefs(context).getString(KEY_PWD, null)

    fun setMemId(context: Context, value: String?) {
        prefs(context).edit().apply {
            if (value != null) putString(KEY_ID, value) else remove(KEY_ID)
        }.apply()
    }

    fun setMemPwd(context: Context, value: String?) {
        prefs(context).edit().apply {
            if (value != null) putString(KEY_PWD, value) else remove(KEY_PWD)
        }.apply()
    }

    fun hasCredentials(context: Context): Boolean = memId(context) != null && memPwd(context) != null

    fun clear(context: Context) {
        setMemId(context, null)
        setMemPwd(context, null)
    }
}

// MARK: - 오류

sealed class HanaPortalException(message: String) : Exception(message) {
    data object MissingCredentials : HanaPortalException("하이하나 아이디/비밀번호를 먼저 등록해 주세요")
    data object TokenNotFound : HanaPortalException("로그인 페이지를 불러오지 못했습니다")
    class LoginFailed(message: String) : HanaPortalException(message)
    class UnexpectedResponse(detail: String) : HanaPortalException("학사시스템 응답을 해석하지 못했습니다\n$detail")

    /** 세션 만료로 로그인 페이지가 내려온 경우 — [authenticatedRaw] 가 재로그인을 결정하는 내부 신호. */
    internal data object LoginPage : HanaPortalException("로그인 페이지가 내려왔습니다")
}

/** OkHttp 원문 응답 — JSON 이 아닌 HTML 페이지(board_view.do 등)도 다루기 위한 결과. */
data class HanaRawResponse(val code: Int, val body: String)

/**
 * hh.hana.hs.kr 이 돌려주는 한 타임 분량의 배정 원본 (도서관/면학실/생활관).
 * PlanSlot/StudyPlace 로의 해석은 [HanaAssignmentMapper] 가 맡습니다.
 */
data class HanaStudyAssignment(
    val stNm: String,              // 예: "평일1타임"
    val placeCategoryName: String, // 예: "도서관" · "면학실" · "생활관"
    val floor: String?,            // 예: "2F"
    val seatLabel: String?,        // 예: "2-34"
)

/** 1인2기(otmGubun "O") / 방과후(otmGubun "A") 배정 원본 — main/extra-program/lesson_req_my_list.json */
data class HanaProgramEntry(
    val stNm: String,      // "평일0타임"(1인2기) · "평일1타임"/"평일2타임"(방과후가 면학 타임을 대체)
    val otmGubun: String?, // "O" = 1인2기, "A" = 방과후
    val name: String,      // 활동/과목명 (앞뒤 공백 제거됨)
    val building: String?, // 예: "B동"
    val room: String?,     // 예: "B301"
    val date: String?,     // yyyy-MM-dd — otb_date
) {
    val isOneTwo: Boolean get() = otmGubun == "O"
    val isAfterSchool: Boolean get() = otmGubun == "A"
}

/**
 * 교과교실 신청 원본 — main/classroom/apply-list.json.
 * 자기주도학습 배정 목록(study-only-req-list)은 교과교실 신청을 반영하지 않아, 늦게 교과교실을 신청한
 * 학생은 포털 시간표엔 "교과교실"이 뜨는데 앱은 원래 배정(생활관 등)을 그대로 보여줬습니다.
 * 응답 필드명은 확정된 스키마가 없어 후보를 넓게 받고, 원문 앞부분을 [HanaSyncApplier] 로그에 남깁니다.
 */
data class HanaClassroomEntry(
    val stNm: String?,      // "평일2타임" 등 (없으면 타임을 찾지 못해 건너뜁니다)
    val room: String?,      // 예: "A201" · "세미나실"
    val date: String?,      // yyyy-MM-dd (없으면 조회한 날짜로 간주)
    val status: String?,    // 사람이 읽는 상태명 — 취소·반려 등이면 적용하지 않습니다
    val cancelled: Boolean, // del_yn/cancel_yn 등이 "Y"
)

data class HanaDailySync(
    val assignments: List<HanaStudyAssignment>,
    val programs: List<HanaProgramEntry>,
    val classrooms: List<HanaClassroomEntry> = emptyList(),
    /** 교과교실 응답 원문 앞부분 — 스키마 확인용 진단(로그에만 남깁니다). */
    val classroomRawSample: String? = null,
)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

// MARK: - hh.hana.hs.kr 클라이언트

/**
 * 로그인 후 세션 쿠키로 자기주도학습(면학실·도서관·생활관) 배정 위치를 가져옵니다.
 * 교과교실 배정은 응답 형식을 아직 확인하지 못해 이 버전에서는 다루지 않습니다.
 *
 * 헤더 구성은 iOS 판을 만들며 실기기 테스트로 확인한 내용을 그대로 반영합니다:
 * - 로그인/조회 POST 모두 Referer·Origin·User-Agent 가 없으면 404 로 숨겨서 막힙니다.
 * - JSON 응답을 기대하는 Accept 헤더가 없으면 라우팅 자체가 안 되어 404 가 납니다.
 */
class HanaPortalClient private constructor() {

    companion object {
        @Volatile private var instance: HanaPortalClient? = null

        fun get(): HanaPortalClient =
            instance ?: synchronized(this) { instance ?: HanaPortalClient().also { instance = it } }

        private const val BASE = "https://hh.hana.hs.kr"
        private val TOKEN_REGEX = Regex("hanaLoginRequestToken\\s*=\\s*\"([^\"]+)\"")
        private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        const val UA =
            "Mozilla/5.0 (Linux; Android 16; SM-S938N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                // 응답이 준 쿠키로 같은 이름만 갈아끼웁니다 — 통째로 바꾸면 이번 응답에 없던 쿠키가 사라집니다.
                mergeCookies(url.host, cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host] ?: emptyList()
        })
        .build()

    private fun mergeCookies(host: String, incoming: List<Cookie>) {
        if (incoming.isEmpty()) return
        val names = incoming.map { it.name }.toSet()
        val kept = cookieStore[host].orEmpty().filter { it.name !in names }
        cookieStore[host] = kept + incoming
    }

    /**
     * 웹뷰가 페이지를 읽고 난 뒤 웹뷰 쪽 쿠키를 OkHttp 항아리로 되가져옵니다. 포털은 웹뷰 요청에 새 세션
     * 쿠키를 내려주며 이전 세션을 버릴 수 있어서, 이걸 안 하면 두 번째 게시글부터 OkHttp 의 낡은 세션이
     * 웹뷰에 덮어씌워져 로그인 페이지가 떴습니다. 두 쪽이 항상 같은 세션을 쓰게 맞춥니다.
     */
    fun syncCookiesFromWebView() {
        val host = BASE.toHttpUrl().host
        val raw = CookieManager.getInstance().getCookie("https://$host") ?: return
        // 같은 이름이 여러 개면(다른 path) 마지막 것을 씁니다.
        val cookies = raw.split(';').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isEmpty()) null
            else Cookie.Builder().name(name).value(value).domain(host).path("/").build()
        }.associateBy { it.name }.values.toList()
        mergeCookies(host, cookies)
    }

    /**
     * WebView 로 포털 상세 페이지를 열 때 OkHttp 쿠키 항아리의 세션 쿠키를
     * android.webkit.CookieManager 로 복사합니다. 이게 없으면 웹뷰가 비로그인 상태로
     * 열려 로그인 페이지가 뜹니다. 로그인/게시판 조회로 쿠키가 이미 채워져 있어야 합니다.
     */
    fun syncCookiesToWebView() {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        // 웹뷰 쿠키 저장소는 앱을 껐다 켜도 남아 있어서, 예전 세션의 JSESSIONID 가 다른 path 로 같이 붙어
        // 있으면 브라우저가 둘 다 보내고 서버는 낡은 쪽을 골라 로그인 페이지를 돌려줬습니다(두 번째 게시글부터
        // 로그인 화면이 뜨던 원인). 이 앱의 웹뷰는 포털 전용이므로 전부 지우고 OkHttp 의 현재 세션만 심습니다.
        manager.removeAllCookies(null)
        cookieStore.forEach { (host, cookies) ->
            cookies.forEach { cookie ->
                manager.setCookie("https://$host", "${cookie.name}=${cookie.value}; path=/; domain=$host")
            }
        }
        manager.flush()
    }

    /**
     * 세션이 없거나 만료된 경우 한 번 로그인한 뒤 다시 시도합니다.
     * 면학실/도서관 배정과 1인2기·방과후 배정을 동시에 가져옵니다.
     */
    suspend fun fetchDailySync(context: Context, date: LocalDate): HanaDailySync =
        withContext(Dispatchers.IO) {
            try {
                requestDailySync(date)
            } catch (e: HanaPortalException.UnexpectedResponse) {
                login(context)
                requestDailySync(date)
            }
        }

    private suspend fun requestDailySync(date: LocalDate): HanaDailySync = coroutineScope {
        val assignments = async { requestAssignments(date) }
        val programs = async { requestPrograms(date) }
        // 교과교실 신청은 응답 형식이 바뀌어도 나머지 동기화를 막지 않도록 실패를 삼킵니다.
        val classrooms = async { runCatching { requestClassrooms(date) }.getOrElse { Pair(emptyList(), "실패: ${it.message}") } }
        val (classroomList, classroomRaw) = classrooms.await()
        HanaDailySync(assignments.await(), programs.await(), classroomList, classroomRaw)
    }

    // MARK: 교과교실 신청 조회

    /**
     * 포털 메인 JS 가 쓰는 GET /main/classroom/apply-list.json 을 그대로 재현합니다 (searchSDate/searchEDate 만).
     * 배열 위치와 필드명은 확정되지 않아 `paging.result` · `list` · `itemList` 와 여러 이름 후보를 훑습니다.
     */
    private fun requestClassrooms(date: LocalDate): Pair<List<HanaClassroomEntry>, String> {
        val day = date.format(DAY_FORMAT)
        // 면학실 목록과 같은 방식 — 포털은 GET 을 405 로 거부하므로 POST 폼으로 보냅니다.
        val body = FormBody.Builder()
            .add("cp", "1")
            .add("searchSDate", day)
            .add("searchEDate", day)
            .add("listType", "list")
            .build()
        val request = browserLikeRequestBuilder("$BASE/main/classroom/apply-list.json", referer = "$BASE/")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val sample = "HTTP ${resp.code} " + text.take(600).replace('\n', ' ')
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw HanaPortalException.UnexpectedResponse("[classroom] $sample")
            val array = json.optJSONObject("paging")?.optJSONArray("result")
                ?: json.optJSONArray("list")
                ?: json.optJSONArray("itemList")
                ?: json.optJSONArray("result")
                ?: return Pair(emptyList(), sample)

            fun first(row: JSONObject, vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
                if (!row.has(key) || row.isNull(key)) null
                else row.opt(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            }
            val entries = (0 until array.length()).mapNotNull { i ->
                val row = array.optJSONObject(i) ?: return@mapNotNull null
                HanaClassroomEntry(
                    stNm = first(row, "st_nm", "stNm", "slot_nm", "time_nm", "st_time", "cc_time_nm"),
                    room = first(
                        row, "cc_place_nm", "room_nm", "slp_nm", "place_nm", "cr_nm", "classroom_nm",
                        "ccr_nm", "slg_nm", "c_place_cd_name",
                    ),
                    date = first(row, "cc_date", "otb_date", "req_date", "apply_date", "use_date", "std_date", "ymd")
                        ?.take(10),
                    status = first(row, "cc_cd_name", "status_nm", "stat_nm", "appr_nm", "req_state", "state_nm"),
                    cancelled = listOf("del_yn", "cancel_yn", "cncl_yn").any { first(row, it)?.uppercase() == "Y" },
                )
            }
            return Pair(entries, sample)
        }
    }

    // MARK: 로그인

    /** 로그인 페이지를 새로 받을 때마다 X-Hana-Login-Token 값이 바뀌므로, 매 시도마다 새로 읽어옵니다. */
    private fun login(context: Context) {
        if (!HanaCredentialStore.hasCredentials(context)) throw HanaPortalException.MissingCredentials
        val memId = HanaCredentialStore.memId(context)!!
        val memPwd = HanaCredentialStore.memPwd(context)!!

        val loginPageUrl = "$BASE/main/login/login.do"
        val pageRequest = Request.Builder()
            .url(loginPageUrl)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .build()
        val html = client.newCall(pageRequest).execute().use { it.body?.string().orEmpty() }

        val token = TOKEN_REGEX.find(html)?.groupValues?.get(1) ?: throw HanaPortalException.TokenNotFound

        val body = FormBody.Builder().add("mem_id", memId).add("mem_pwd", memPwd).build()
        val request = browserLikeRequestBuilder("$BASE/main/login/auth/login", referer = loginPageUrl)
            .header("X-Hana-Login-Token", token)
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw HanaPortalException.UnexpectedResponse("[login] (HTTP ${resp.code}) ${text.take(300)}")

            val statusCode = if (json.has("statusCode")) json.optInt("statusCode") else 200
            if (statusCode >= 400) {
                throw HanaPortalException.LoginFailed(loginFailureMessage(json.optStringOrNull("responseMessage")))
            }
        }
    }

    private fun loginFailureMessage(code: String?): String = when (code) {
        "MEM_INVALID_PW" -> "비밀번호가 올바르지 않습니다"
        "MEM_INVALID_ID", "MEM_NOT_FOUND" -> "존재하지 않는 아이디입니다"
        "MEM_LOCKED" -> "로그인 실패 횟수 초과로 계정이 잠겼습니다"
        else -> code ?: "로그인에 실패했습니다"
    }

    // MARK: 자기주도학습 위치 조회

    private fun requestAssignments(date: LocalDate): List<HanaStudyAssignment> {
        val day = date.format(DAY_FORMAT)
        val body = FormBody.Builder()
            .add("cp", "1")
            .add("searchSDate", day)
            .add("searchEDate", day)
            .add("listType", "list")
            .build()

        val request = browserLikeRequestBuilder("$BASE/main/studyroom/study-only-req-list.json", referer = "$BASE/")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw HanaPortalException.UnexpectedResponse("[study] (HTTP ${resp.code}) ${text.take(300)}")

            return runCatching {
                val results = json.getJSONObject("paging").getJSONArray("result")
                (0 until results.length()).map { i ->
                    val row = results.getJSONObject(i)
                    HanaStudyAssignment(
                        stNm = row.getString("st_nm"),
                        placeCategoryName = row.getString("c_place_cd_name"),
                        floor = row.optStringOrNull("clr_area_nm"),
                        seatLabel = row.optStringOrNull("srt_cont"),
                    )
                }
            }.getOrElse {
                throw HanaPortalException.UnexpectedResponse("[study] (HTTP ${resp.code}) ${text.take(300)}")
            }
        }
    }

    // MARK: 1인2기 · 방과후 조회

    private fun requestPrograms(date: LocalDate): List<HanaProgramEntry> {
        val day = date.format(DAY_FORMAT)
        val body = FormBody.Builder()
            .add("cp", "1")
            .add("searchSDate", day)
            .add("searchEDate", day)
            .add("listType", "list")
            .build()

        val request = browserLikeRequestBuilder("$BASE/main/extra-program/lesson_req_my_list.json", referer = "$BASE/")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw HanaPortalException.UnexpectedResponse("[program] (HTTP ${resp.code}) ${text.take(300)}")

            return runCatching {
                val list = json.getJSONArray("list")
                (0 until list.length()).map { i ->
                    val row = list.getJSONObject(i)
                    HanaProgramEntry(
                        stNm = row.getString("st_nm"),
                        otmGubun = row.optStringOrNull("otm_gubun"),
                        name = row.getString("otl_nm").trim(),
                        building = row.optStringOrNull("slg_nm"),
                        room = row.optStringOrNull("slp_nm"),
                        date = row.optStringOrNull("otb_date"),
                    )
                }
            }.getOrElse {
                throw HanaPortalException.UnexpectedResponse("[program] (HTTP ${resp.code}) ${text.take(300)}")
            }
        }
    }

    // MARK: 공통 헤더

    /**
     * 알리미·학사일정·게시판처럼 로그인 세션이 필요한 JSON 조회의 공통 진입점.
     * hh 의 이 JSON 엔드포인트들은 GET 을 405(Method Not Allowed)로 막고 form-encoded POST 만
     * 받아들이므로, 파라미터를 쿼리가 아니라 [FormBody] 로 보냅니다.
     * 세션이 없거나 만료돼 JSON 이 아니면(로그인 페이지 HTML 등) 한 번 로그인하고 재시도합니다.
     * [requestJson] 이 던지는 예외는 그대로 호출부로 전달되므로, 자격증명 없음/로그인 실패를 구분할 수 있습니다.
     */
    /**
     * [isValid] 는 "로그인된 응답인지"를 판별합니다 — 포털은 세션이 없어도 HTML 이 아니라 목록 키가 빠진
     * JSON 을 돌려줄 때가 있어서, JSON 파싱만으로는 로그인 실패를 못 잡고 빈 목록을 캐시해 버렸습니다
     * (앱을 켜면 게시판이 "없음"으로 보이던 원인). 비어 있으면 한 번 로그인하고 다시 요청합니다.
     */
    suspend fun authenticatedJson(
        context: Context,
        path: String,
        params: List<Pair<String, String>>,
        referer: String = "$BASE/",
        isValid: (JSONObject) -> Boolean = { true },
    ): JSONObject = withContext(Dispatchers.IO) {
        val first = try {
            requestJson(path, params, referer)
        } catch (e: HanaPortalException.UnexpectedResponse) {
            null
        }
        if (first != null && isValid(first)) {
            lastAuthenticatedAt = System.currentTimeMillis()
            return@withContext first
        }
        login(context)
        requestJson(path, params, referer).also { lastAuthenticatedAt = System.currentTimeMillis() }
    }

    private fun requestJson(path: String, params: List<Pair<String, String>>, referer: String): JSONObject {
        val body = FormBody.Builder().apply {
            params.forEach { (key, value) -> add(key, value) }
        }.build()

        val request = browserLikeRequestBuilder(BASE + path, referer).post(body).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw nonJsonResponse(path, resp.code)
            return json
        }
    }

    /**
     * JSON 이 아닌 응답(로그인 HTML·405 등)을 원문 그대로 노출하지 않고 짧은 한국어 문구로 바꿉니다.
     * 호출부는 재로그인 여부만 판단하면 되므로 본문 HTML 은 메시지에 넣지 않습니다.
     */
    private fun nonJsonResponse(path: String, code: Int): HanaPortalException.UnexpectedResponse {
        val reason = when (code) {
            405 -> "POST 방식이 필요합니다"
            401, 403 -> "로그인이 만료되었습니다"
            else -> "JSON 응답이 아닙니다"
        }
        return HanaPortalException.UnexpectedResponse("[$path] HTTP $code · $reason")
    }

    /**
     * 로그인 세션이 필요한 GET/POST 의 **원문** 응답. 게시글 상세 HTML(board_view.do) 이나
     * 교과교실 신청 내역 JSON 조회처럼, JSON 파싱을 강제하지 않아야 하는 곳에 씁니다.
     * 로그인 페이지가 내려오면 한 번 재로그인 후 재시도합니다.
     */
    suspend fun authenticatedRaw(
        context: Context,
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        method: String = "GET",
        referer: String = "$BASE/",
    ): HanaRawResponse = withContext(Dispatchers.IO) {
        try {
            requestRaw(path, params, method, referer)
        } catch (e: HanaPortalException.LoginPage) {
            login(context)
            requestRaw(path, params, method, referer)
        }.also { lastAuthenticatedAt = System.currentTimeMillis() }
    }

    /**
     * 웹뷰를 열기 전에 세션이 살아 있는지 확인하고, 아니면 로그인합니다 — 웹뷰는 OkHttp 쿠키를 복사해 쓰므로
     * 여기서 세션을 살려 두면 로그인 페이지 대신 바로 게시글이 뜹니다. 실패해도 예외를 밖으로 내지 않습니다.
     */
    suspend fun ensureLoggedIn(context: Context) {
        // 방금 다른 요청이 인증된 채로 성공했으면 세션이 살아 있다고 보고 네트워크를 타지 않습니다.
        if (System.currentTimeMillis() - lastAuthenticatedAt < SESSION_FRESH_MS) return
        runCatching {
            authenticatedJson(
                context,
                "/main/alim/alim-target-list.json",
                listOf("pageSize" to "1"),
                isValid = { it.optJSONObject("paging")?.has("result") == true },
            )
        }
    }

    /**
     * 세션 상태와 무관하게 다시 로그인합니다 — 웹뷰가 로그인 페이지로 튕겼을 때(세션이 서버에서 끊긴 경우)
     * 화면이 스스로 복구하는 데 씁니다. 실패는 예외로 알립니다.
     */
    suspend fun relogin(context: Context) = withContext(Dispatchers.IO) {
        login(context)
        lastAuthenticatedAt = System.currentTimeMillis()
    }

    /** 마지막으로 인증된 응답을 받은 시각 — [ensureLoggedIn] 이 불필요한 왕복을 건너뛰는 기준. */
    @Volatile private var lastAuthenticatedAt = 0L
    private val SESSION_FRESH_MS = 3 * 60 * 1000L

    /** [authenticatedRaw] 의 본문 문자열만 필요한 호출부용 단축. */
    suspend fun authenticatedText(
        context: Context,
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        method: String = "GET",
        referer: String = "$BASE/",
    ): String = authenticatedRaw(context, path, params, method, referer).body

    private fun requestRaw(
        path: String,
        params: List<Pair<String, String>>,
        method: String,
        referer: String,
    ): HanaRawResponse {
        val builder = browserLikeRequestBuilder(BASE + path, referer)
        val request = when (method.uppercase()) {
            "GET" -> builder.url(httpUrl(path, params)).get().build()
            else -> builder.post(formBody(params)).build()
        }

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (looksLikeLoginPage(body)) throw HanaPortalException.LoginPage
            return HanaRawResponse(resp.code, body)
        }
    }

    /**
     * 응답이 로그인 폼이면 세션이 끊긴 것으로 봅니다. `hanaLoginRequestToken` 은 실제
     * 로그인 페이지의 JS 에만 있는 값이라 안전한 마커입니다. `/main/login/login.do` 는
     * 예전엔 같이 봤지만, 사이트 전역 내비게이션·로그아웃 스크립트가 이 경로를 링크로만
     * 참조해도 걸려버려 정상 로그인 상태의 페이지를 세션 만료로 오판하는 원인이었습니다.
     */
    /**
     * 로그인 페이지 HTML 이거나, 세션이 끝났을 때 포털이 돌려주는 짧은 JSON(`{"result":"expired",...}`)이면
     * 로그인 후 재요청 대상입니다 — 후자를 못 잡으면 그 JSON 이 게시글 본문으로 그대로 화면에 찍혔습니다.
     */
    private fun looksLikeLoginPage(body: String): Boolean =
        body.contains("hanaLoginRequestToken") ||
            (body.length < 200 && body.contains("\"result\"") && body.contains("\"expired\""))

    private fun formBody(params: List<Pair<String, String>>): FormBody =
        FormBody.Builder().apply { params.forEach { (key, value) -> add(key, value) } }.build()

    private fun httpUrl(path: String, params: List<Pair<String, String>>): HttpUrl {
        val builder = (BASE + path).toHttpUrl().newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    /**
     * OkHttp 은 브라우저와 달리 Referer/Origin/실제 User-Agent 를 자동으로 붙이지 않아
     * 일부 요청이 봇으로 간주돼 막힐 수 있어, 로그인/데이터 요청에 직접 채워 넣습니다.
     */
    private fun browserLikeRequestBuilder(url: String, referer: String?): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            // jQuery 는 dataType:"json" 요청에 이 Accept 헤더를 자동으로 붙이는데, 서버가
            // 이 헤더가 없는 POST 요청을 라우팅하지 못해 404 로 응답한다 — 반드시 명시해야 합니다.
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", BASE)
            .header("User-Agent", UA)
        if (referer != null) builder.header("Referer", referer)
        return builder
    }
}

// MARK: - 매핑

object HanaAssignmentMapper {

    /**
     * "평일0타임" / "주말3타임" 같은 st_nm 문자열을 앱의 PlanSlot 으로 변환합니다.
     * 주말 슬롯 이름은 실제 배정을 확인하지 못해 평일 명명 규칙으로 추정했습니다.
     */
    fun slotFor(stNm: String): PlanSlot? = when (stNm.replace(" ", "")) {
        "평일0타임" -> PlanSlot.weekday0
        "평일1타임" -> PlanSlot.weekday1
        "평일2타임" -> PlanSlot.weekday2
        "주말1타임" -> PlanSlot.weekend1
        "주말2타임" -> PlanSlot.weekend2
        "주말3타임" -> PlanSlot.weekend3
        "주말4타임" -> PlanSlot.weekend4
        else -> null
    }

    /**
     * 이름이 위 표와 다를 때(예: "토요일1타임" · "휴일 1타임" · 그냥 "1타임")를 위한 폴백 — 이름에서 타임 번호만
     * 뽑고, 평일/주말은 **조회한 날짜**로 정합니다. 주말 배정 이름을 실제로 확인하지 못해 주말엔 위치가
     * 전혀 안 붙던 문제를 막기 위한 것입니다.
     */
    fun slotFor(stNm: String, date: LocalDate): PlanSlot? {
        slotFor(stNm)?.let { return it }
        val compact = stNm.replace(" ", "")
        if (!compact.contains("타임")) return null
        val number = Regex("(\\d+)\\s*타임").find(compact)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val weekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY
        return if (weekend) {
            when (number) {
                1 -> PlanSlot.weekend1
                2 -> PlanSlot.weekend2
                3 -> PlanSlot.weekend3
                4 -> PlanSlot.weekend4
                else -> null
            }
        } else {
            when (number) {
                0 -> PlanSlot.weekday0
                1 -> PlanSlot.weekday1
                2 -> PlanSlot.weekday2
                else -> null
            }
        }
    }

    fun placeFor(assignment: HanaStudyAssignment): StudyPlace? {
        val location = listOfNotNull(assignment.floor?.trim(), assignment.seatLabel?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val category = assignment.placeCategoryName.trim()

        return when (category) {
            "도서관" -> StudyPlace.Library(libraryLocation(assignment.floor, assignment.seatLabel))
            "면학실" -> StudyPlace.StudyRoom(location.ifEmpty { "위치 미확인" })
            "생활관" -> StudyPlace.Dorm
            // 포털 분류가 알려진 유형이 아니면 "교과교실"로 단정하지 않고 원문 분류명과
            // 층/좌석을 그대로 보여주는 일반 장소로 폴백합니다 (분류·위치가 모두 비면 미설정).
            else -> if (category.isEmpty() && location.isEmpty()) {
                null
            } else {
                StudyPlace.Other(category.ifEmpty { "기타 장소" }, location.ifEmpty { null })
            }
        }
    }

    /** 도서관은 "2F" · "34번" 같은 포털 원문 대신 "2-34"(층수-좌석번호) 형태로 보여줍니다. */
    private fun libraryLocation(floor: String?, seat: String?): String {
        val seatTrimmed = seat?.trim()
        // 포털이 좌석 필드에 이미 "2-34"처럼 층-좌석을 합쳐서 주는 경우 그대로 씁니다.
        // (숫자만 다시 뽑아 층과 합치면 "2"+"234"="2-234"처럼 중복돼 버립니다.)
        if (seatTrimmed != null && Regex("""^\d+-\d+$""").matches(seatTrimmed)) {
            return seatTrimmed
        }
        fun digitsOnly(value: String?): String? {
            val digits = value?.filter { it.isDigit() }
            return digits?.takeIf { it.isNotEmpty() }
        }
        val f = digitsOnly(floor)
        val s = digitsOnly(seat)
        return when {
            f != null && s != null -> "$f-$s"
            else -> s ?: f ?: "위치 미확인"
        }
    }

    fun roomFor(program: HanaProgramEntry): String? {
        val location = listOfNotNull(program.building, program.room)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return location.ifEmpty { null }
    }
}

// MARK: - 동기화 결과를 PlanStore 에 반영

/** [HanaDailySync] 를 PlanStore 에 써 넣습니다. 수동 동기화(MainActivity)와 백그라운드 동기화(HanaSyncWorker)가 공유합니다. */
object HanaSyncApplier {

    /** 실제 포털 값과 매핑 결과를 adb 로 확인하기 위한 진단 태그 — UI 에는 노출하지 않습니다. */
    private const val TAG = "HanaPlace"
    private const val MAX_LOGGED_ROWS = 10

    suspend fun apply(context: Context, sync: HanaDailySync, date: LocalDate) {
        // 원문 분류명이 예상과 다를 때(예: 면학실 대신 다른 값) 바로 확인할 수 있도록 앞 몇 줄만 남깁니다.
        sync.assignments.take(MAX_LOGGED_ROWS).forEach { assignment ->
            val mapped = HanaAssignmentMapper.placeFor(assignment)
            Log.d(
                TAG,
                "st_nm=${assignment.stNm} | placeCategoryName=${assignment.placeCategoryName} | " +
                    "floor=${assignment.floor} | seatLabel=${assignment.seatLabel} -> ${describe(mapped)}",
            )
        }

        if (sync.assignments.isEmpty()) Log.d(TAG, "배정 목록이 비어 있음 (date=$date, dow=${date.dayOfWeek})")
        for (assignment in sync.assignments) {
            val slot = HanaAssignmentMapper.slotFor(assignment.stNm, date)
            if (slot == null) {
                Log.d(TAG, "타임 이름을 해석하지 못함: st_nm=${assignment.stNm} (date=$date)")
                continue
            }
            val place = HanaAssignmentMapper.placeFor(assignment) ?: continue
            PlanStore.set(context, place, slot, date)
            Log.d(
                TAG,
                "applied $slot -> ${describe(place)} floor=${assignment.floor ?: "-"} seat=${assignment.seatLabel ?: "-"}",
            )
        }

        val dayKey = PlanStore.dayKey(date)

        // 교과교실 신청 — 배정(생활관·면학실·도서관)보다 뒤에, 1인2기·방과후보다 앞에 적용합니다.
        // 포털 시간표가 이 신청을 우선해 "교과교실"로 보여주므로 앱도 같은 순서를 따릅니다.
        sync.classroomRawSample?.let { Log.d(TAG, "classroom raw: $it") }
        for (entry in sync.classrooms) {
            Log.d(TAG, "classroom st_nm=${entry.stNm} room=${entry.room} date=${entry.date} status=${entry.status} cancelled=${entry.cancelled}")
            if (entry.cancelled) continue
            if (entry.date != null && entry.date != dayKey) continue
            val status = entry.status.orEmpty()
            if (listOf("취소", "반려", "거절", "미승인", "불가").any { it in status }) continue
            val slot = entry.stNm?.let { HanaAssignmentMapper.slotFor(it, date) } ?: continue
            val room = entry.room?.trim().orEmpty()
            // 교실명이 없으면 칩 없이 "교과교실"만 보여줍니다.
            val place = if (room.isEmpty()) StudyPlace.Other("교과교실", null) else StudyPlace.ClassroomPlace(Classroom.fromName(room))
            PlanStore.set(context, place, slot, date)
            Log.d(TAG, "applied $slot -> ${describe(place)}")
        }

        for (program in sync.programs) {
            if (program.date != dayKey) continue
            val slot = HanaAssignmentMapper.slotFor(program.stNm, date) ?: continue
            val room = HanaAssignmentMapper.roomFor(program)
            // 1인2기/방과후 둘 다 그 타임의 면학실/도서관 배정을 덮어씁니다 (동시에 발생할 수 없으므로)
            val place = when {
                program.isOneTwo -> StudyPlace.OneTwo(program.name, room)
                program.isAfterSchool -> StudyPlace.AfterSchool(program.name, room)
                else -> null
            } ?: continue
            PlanStore.set(context, place, slot, date)
            Log.d(TAG, "applied $slot -> ${describe(place)}")
        }
    }

    /** [HanaPlace] 로그에 남길 매핑 결과 한 줄 — 하위 타입 이름과 핵심 값만 담습니다. */
    private fun describe(place: StudyPlace?): String = when (place) {
        null -> "null"
        is StudyPlace.ClassroomPlace -> "Classroom ${place.classroom.name}"
        is StudyPlace.StudyRoom -> "StudyRoom seat=${place.seat}"
        is StudyPlace.Library -> "Library seat=${place.seat}"
        StudyPlace.Dorm -> "Dorm"
        is StudyPlace.AfterSchool -> "AfterSchool ${place.courseName} room=${place.room ?: "-"}"
        is StudyPlace.OneTwo -> "OneTwo ${place.activityName} room=${place.room ?: "-"}"
        is StudyPlace.Other -> "Other category=${place.category} location=${place.location ?: "-"}"
    }
}

// MARK: - 자동 재시도 억제 (로그인 실패 시)

/**
 * 비밀번호가 틀린 상태로 백그라운드 동기화가 반복되면 하이하나의 5회 로그인 실패 잠금에 걸릴 수 있어,
 * 로그인 실패를 한 번이라도 확인하면 사용자가 앱을 열어 문제를 해결할 때까지 자동 동기화를 쉬게 합니다.
 */
object HanaSyncGate {

    private const val PREFS_NAME = "hana_sync_gate"
    private const val KEY_SUSPENDED_UNTIL = "suspendedUntilEpochMs"
    private val cooldownMillis = java.time.Duration.ofHours(24).toMillis()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSuspended(context: Context): Boolean {
        val until = prefs(context).getLong(KEY_SUSPENDED_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun suspend(context: Context) {
        prefs(context).edit().putLong(KEY_SUSPENDED_UNTIL, System.currentTimeMillis() + cooldownMillis).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_SUSPENDED_UNTIL).apply()
    }
}
