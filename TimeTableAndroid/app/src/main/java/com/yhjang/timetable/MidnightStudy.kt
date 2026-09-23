package com.yhjang.timetable

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.glance.appwidget.updateAll
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiButtonStyle
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiLiquidGlassBox
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiTextButton
import com.yhjang.timetable.ui.OneUiTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * 심야면학 — 기숙사 쪽 별도 사이트(midnight-study.vercel.app). 사이트는 정적 페이지 + Supabase 이고, 학생 화면이 쓰는
 * 호출은 모두 여기 있습니다 (사이트 app.js 기준):
 *   로그인   POST /auth/v1/token?grant_type=password  {email, password}  — email 은 이름을 해시한 가짜 주소
 *   갱신     POST /auth/v1/token?grant_type=refresh_token {refresh_token}
 *   현황     POST /rest/v1/rpc/get_dashboard {p_session, p_floor}  → {user, application, reservation, seats}
 *   신청     POST /rest/v1/rpc/reserve_seat {p_seat, p_session, p_floor} → 같은 모양
 *   취소     POST /rest/v1/rpc/cancel_today_reservation {p_session, p_floor} → 같은 모양
 * 학생용 과거 기록 API 는 없어서 "내역"은 오늘의 1·2타임 신청과 불참 횟수입니다.
 * 비밀번호는 저장하지 않고, 로그인으로 받은 세션 토큰만 이 기기에 암호화해 둡니다.
 */

private const val SUPABASE_URL = "https://jltmfpxxgxruhalvhwnu.supabase.co"
// 사이트 supabase-config.js 에 공개된 publishable 키 (브라우저에 그대로 내려가는 공개 키).
private const val SUPABASE_KEY = "sb_publishable_FB_lRADTZt6U-Dx8UimUqA_kCJozOuF"
const val MIDNIGHT_STUDY_URL = "https://midnight-study.vercel.app/"

// MARK: - 모델

data class MidnightSeat(
    val number: Int,
    val occupied: Boolean,
    val mine: Boolean,
    val applicantName: String?,
    val applicantRoom: String?,
)

data class MidnightDashboard(
    val name: String,
    val room: String?,
    val floor: Int?,
    val eligibleFloors: List<Int>,
    val absenceCount: Int,
    val bannedUntil: String?,
    val roomUpdateRequired: Boolean,
    val date: String?,
    val mode: String?,
    val sessionStart: String?,
    val sessionEnd: String?,
    val session2Available: Boolean,
    val canApply: Boolean,
    val open: Boolean,
    val opensAt: String?,
    val closesAt: String?,
    val blockedUntil: String?,
    val session1Required: Boolean,
    val hasSession2Reservation: Boolean,
    val reservationFloor: Int?,
    val reservationSeat: Int?,
    val seats: List<MidnightSeat>,
) {
    val hasReservation: Boolean get() = reservationSeat != null

    /** 사이트 상단 배지와 같은 문구. */
    val statusText: String
        get() = when {
            blockedUntil != null -> "신청 제한 · ${blockedUntil}까지"
            session1Required -> "1타임 신청 필요"
            open -> "신청 가능 · ${closesAt.orEmpty()} 마감"
            else -> "신청 마감 · ${opensAt.orEmpty()} 오픈"
        }

    val helpText: String
        get() = when {
            blockedUntil != null -> "불참 2회 누적으로 신청이 제한되었습니다."
            session1Required -> "2타임은 같은 날 1타임 좌석을 먼저 신청해야 합니다."
            open -> "빈 좌석을 누르면 바로 신청되고, 내 좌석을 누르면 취소됩니다."
            else -> "신청 가능 시간은 ${opensAt.orEmpty()}~${closesAt.orEmpty()}입니다."
        }

    val modeLabel: String?
        get() = when (mode) {
            "normal" -> "평상시"
            "pre_exam" -> "고사 2주 전"
            "exam" -> "고사기간"
            else -> null
        }

    companion object {
        fun parse(json: JSONObject): MidnightDashboard {
            val user = json.optJSONObject("user") ?: JSONObject()
            val app = json.optJSONObject("application") ?: JSONObject()
            val res = json.optJSONObject("reservation")
            val seatsJson = json.optJSONArray("seats")
            val floors = user.optJSONArray("eligibleFloors")
            return MidnightDashboard(
                name = user.str("name") ?: "",
                room = user.str("room"),
                floor = user.optIntOrNull("floor"),
                eligibleFloors = (0 until (floors?.length() ?: 0)).mapNotNull { floors?.optInt(it) },
                absenceCount = user.optInt("absenceCount", 0),
                bannedUntil = user.str("bannedUntil"),
                roomUpdateRequired = user.optBoolean("roomUpdateRequired", false),
                date = app.str("date"),
                mode = app.str("mode"),
                sessionStart = app.str("sessionStart"),
                sessionEnd = app.str("sessionEnd"),
                session2Available = app.optBoolean("session2Available", false),
                canApply = app.optBoolean("canApply", false),
                open = app.optBoolean("open", false),
                opensAt = app.str("opensAt"),
                closesAt = app.str("closesAt"),
                blockedUntil = app.str("blockedUntil"),
                session1Required = app.optBoolean("session1Required", false),
                hasSession2Reservation = app.optBoolean("hasSession2Reservation", false),
                reservationFloor = res?.optIntOrNull("floor"),
                reservationSeat = res?.optIntOrNull("seat"),
                seats = (0 until (seatsJson?.length() ?: 0)).mapNotNull { i ->
                    val s = seatsJson?.optJSONObject(i) ?: return@mapNotNull null
                    MidnightSeat(
                        number = s.optInt("number"),
                        occupied = s.optBoolean("occupied", false),
                        mine = s.optBoolean("mine", false),
                        applicantName = s.str("applicantName"),
                        applicantRoom = s.str("applicantRoom"),
                    )
                },
            )
        }
    }
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

class MidnightException(message: String, val loggedOut: Boolean = false) : Exception(message)

// MARK: - 세션 저장

/** 로그인으로 받은 Supabase 세션(토큰)만 암호화해 둡니다 — 비밀번호는 저장하지 않습니다. */
object MidnightSessionStore {
    private const val PREFS_NAME = "midnight_session"
    @Volatile private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val app = context.applicationContext
            val created = runCatching { open(app) }.getOrElse {
                // 재설치 뒤 복원된 암호화 파일은 열 수 없습니다 — 지우고 새로 (다시 로그인).
                app.deleteSharedPreferences(PREFS_NAME)
                open(app)
            }
            cached = created
            return created
        }
    }

    private fun open(app: Context): SharedPreferences = EncryptedSharedPreferences.create(
        app,
        PREFS_NAME,
        MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isLoggedIn(context: Context) = refreshToken(context) != null
    fun accessToken(context: Context): String? = prefs(context).getString("access", null)
    fun refreshToken(context: Context): String? = prefs(context).getString("refresh", null)
    fun expiresAt(context: Context): Long = prefs(context).getLong("expires_at", 0L)
    fun name(context: Context): String? = prefs(context).getString("name", null)

    fun save(context: Context, json: JSONObject, name: String? = null) {
        val expiresIn = json.optLong("expires_in", 3600L)
        prefs(context).edit().apply {
            putString("access", json.getString("access_token"))
            putString("refresh", json.getString("refresh_token"))
            putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000L)
            if (name != null) putString("name", name)
        }.apply()
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}

// MARK: - API

object MidnightApi {
    private const val TAG = "Midnight"
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 사이트와 같은 규칙: 이름 → NFC·소문자 → SHA-256 → student-<hex>@midnightstudy.local */
    fun nameToEmail(name: String): String {
        val normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFC).lowercase(Locale.KOREAN)
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "student-$hash@midnightstudy.local"
    }

    suspend fun login(context: Context, name: String, password: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", nameToEmail(name)).put("password", password)
        val json = auth("password", body)
        MidnightSessionStore.save(context, json, name.trim())
    }

    fun logout(context: Context) {
        MidnightSessionStore.clear(context)
        MidnightSchedule.clear(context)
    }

    suspend fun dashboard(context: Context, session: Int, floor: Int?): MidnightDashboard =
        rpc(context, "get_dashboard", JSONObject().put("p_session", session).put("p_floor", floor ?: JSONObject.NULL)).also { sync(context, session, it) }

    suspend fun reserve(context: Context, seat: Int, session: Int, floor: Int?): MidnightDashboard =
        rpc(context, "reserve_seat", JSONObject().put("p_seat", seat).put("p_session", session).put("p_floor", floor ?: JSONObject.NULL)).also { sync(context, session, it) }

    suspend fun cancel(context: Context, session: Int, floor: Int?): MidnightDashboard =
        rpc(context, "cancel_today_reservation", JSONObject().put("p_session", session).put("p_floor", floor ?: JSONObject.NULL)).also { sync(context, session, it) }

    /** 받은 현황을 일정에 반영하고, 바뀌었으면 Now Bar·위젯도 다시 그립니다. */
    private suspend fun sync(context: Context, session: Int, d: MidnightDashboard) {
        if (!MidnightSchedule.record(context, session, d)) return
        runCatching { LiveActivity.update(context) }
        runCatching { com.yhjang.timetable.widget.TimeTableWidget().updateAll(context) }
    }

    private suspend fun rpc(context: Context, name: String, params: JSONObject): MidnightDashboard = withContext(Dispatchers.IO) {
        var token = validAccessToken(context)
        repeat(2) { attempt ->
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rpc/$name")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(params.toString().toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = JSONObject(text)
                    return@withContext MidnightDashboard.parse(json)
                }
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                Log.d(TAG, "$name HTTP ${resp.code} ${text.take(200)}")
                val authProblem = resp.code == 401 || Regex("jwt|session|로그인이 필요", RegexOption.IGNORE_CASE).containsMatchIn(message)
                if (authProblem && attempt == 0) {
                    token = refresh(context)
                } else if (authProblem) {
                    MidnightSessionStore.clear(context)
                    throw MidnightException("로그인이 만료되었습니다. 다시 로그인해 주세요.", loggedOut = true)
                } else {
                    throw MidnightException(message.ifBlank { "요청을 처리하지 못했습니다 (HTTP ${resp.code})" })
                }
            }
        }
        throw MidnightException("요청을 처리하지 못했습니다")
    }

    private fun validAccessToken(context: Context): String {
        val access = MidnightSessionStore.accessToken(context)
            ?: throw MidnightException("로그인이 필요합니다", loggedOut = true)
        // 만료 1분 전부터는 미리 갱신합니다.
        return if (System.currentTimeMillis() > MidnightSessionStore.expiresAt(context) - 60_000L) refresh(context) else access
    }

    private fun refresh(context: Context): String {
        val refresh = MidnightSessionStore.refreshToken(context)
            ?: throw MidnightException("로그인이 필요합니다", loggedOut = true)
        val json = try {
            auth("refresh_token", JSONObject().put("refresh_token", refresh))
        } catch (e: MidnightException) {
            MidnightSessionStore.clear(context)
            throw MidnightException("로그인이 만료되었습니다. 다시 로그인해 주세요.", loggedOut = true)
        }
        MidnightSessionStore.save(context, json)
        return json.getString("access_token")
    }

    private fun auth(grant: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$SUPABASE_URL/auth/v1/token?grant_type=$grant")
            .header("apikey", SUPABASE_KEY)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (resp.isSuccessful && json?.has("access_token") == true) return json
            val raw = json?.let { it.optString("error_description").ifBlank { it.optString("msg") }.ifBlank { it.optString("message") } }.orEmpty()
            Log.d(TAG, "auth $grant HTTP ${resp.code} ${raw.take(120)}")
            throw MidnightException(
                when {
                    Regex("invalid login credentials", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "이름 또는 비밀번호가 올바르지 않습니다."
                    raw.isNotBlank() -> raw
                    else -> "로그인하지 못했습니다 (HTTP ${resp.code})"
                },
            )
        }
    }
}

// MARK: - 신청·내역 카드

private sealed interface MidnightHistoryState {
    data object Idle : MidnightHistoryState
    data object Loading : MidnightHistoryState
    data class Loaded(val lines: List<String>) : MidnightHistoryState
    data class Error(val message: String) : MidnightHistoryState
}

/** 신청·내역 섹션의 심야면학 카드 — 다른 서비스처럼 "내역 조회"(오늘 1·2타임 신청 + 불참)와 "신청하기". */
@Composable
internal fun MidnightStudyCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MidnightHistoryState>(MidnightHistoryState.Idle) }
    var showingScreen by remember { mutableStateOf(false) }
    if (showingScreen) MidnightStudyScreen(onDismiss = { showingScreen = false; if (state !is MidnightHistoryState.Idle) state = MidnightHistoryState.Idle })

    fun loadHistory() {
        if (!MidnightSessionStore.isLoggedIn(context)) {
            showingScreen = true
            return
        }
        state = MidnightHistoryState.Loading
        scope.launch {
            state = try {
                val first = MidnightApi.dashboard(context, 1, null)
                val second = if (first.session2Available) runCatching { MidnightApi.dashboard(context, 2, first.floor) }.getOrNull() else null
                fun line(label: String, d: MidnightDashboard?) = when {
                    d == null -> "$label · 운영 안 함"
                    d.hasReservation -> "$label · ${d.reservationFloor ?: d.floor}층 ${d.reservationSeat}번"
                    else -> "$label · 신청 없음"
                }
                MidnightHistoryState.Loaded(
                    listOf(
                        line("1타임", first),
                        line("2타임", second),
                        "불참 ${first.absenceCount}회 · " + (first.bannedUntil?.let { "${it}까지 신청 제한" } ?: "신청 제한 없음"),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MidnightHistoryState.Error(e.message ?: "불러오지 못했습니다")
            }
        }
    }

    OneUiCard(modifier = Modifier.fillMaxWidth()) {
        Text("심야면학", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when (val s = state) {
            MidnightHistoryState.Idle -> Unit
            MidnightHistoryState.Loading -> {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiLoading(size = 16.dp, stroke = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("불러오는 중", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is MidnightHistoryState.Loaded -> {
                Spacer(Modifier.height(10.dp))
                s.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            is MidnightHistoryState.Error -> {
                Spacer(Modifier.height(10.dp))
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OneUiButton(
                text = when (state) {
                    MidnightHistoryState.Idle -> "내역 조회"
                    MidnightHistoryState.Loading -> "불러오는 중"
                    is MidnightHistoryState.Error -> "다시 시도"
                    is MidnightHistoryState.Loaded -> "새로고침"
                },
                onClick = ::loadHistory,
                style = OneUiButtonStyle.Neutral,
                enabled = state != MidnightHistoryState.Loading,
                modifier = Modifier.weight(1f),
            )
            OneUiButton(
                text = "신청하기",
                onClick = { showingScreen = true },
                style = OneUiButtonStyle.Neutral,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// MARK: - 신청 화면

@Composable
fun MidnightStudyScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(MidnightSessionStore.isLoggedIn(context)) }
    var session by remember { mutableStateOf(1) }
    var floor by remember { mutableStateOf<Int?>(null) }
    var data by remember { mutableStateOf<MidnightDashboard?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeShown by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        noticeShown = current
        delay(2600)
        if (notice == current) notice = null
    }

    fun handle(e: Exception): String {
        if (e is MidnightException && e.loggedOut) loggedIn = false
        return e.message ?: "요청을 처리하지 못했습니다"
    }

    LaunchedEffect(loggedIn, session, floor, reload) {
        if (!loggedIn) return@LaunchedEffect
        loading = true
        error = null
        try {
            var result = try {
                MidnightApi.dashboard(context, session, floor)
            } catch (e: MidnightException) {
                // 2타임이 없는 날(고사기간 등)이면 사이트처럼 1타임으로.
                if (session == 2 && Regex("2타임|고사기간").containsMatchIn(e.message.orEmpty())) {
                    session = 1
                    MidnightApi.dashboard(context, 1, floor)
                } else throw e
            }
            data = result
            if (floor == null) floor = result.floor
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = handle(e)
        }
        loading = false
    }

    fun act(block: suspend () -> MidnightDashboard, success: String) {
        if (busy) return
        busy = true
        scope.launch {
            notice = try {
                data = block()
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reload++
                handle(e)
            }
            busy = false
        }
    }

    OneUiFullScreen(
        title = "심야면학 신청",
        subtitle = data?.let { d -> listOfNotNull(d.name.takeIf { it.isNotEmpty() }, d.modeLabel).joinToString(" · ").ifEmpty { null } },
        onDismiss = onDismiss,
        actions = {
            if (loggedIn) {
                IconButton(onClick = { reload++ }, enabled = !loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                }
                OneUiTextButton(
                    text = "로그아웃",
                    onClick = {
                        MidnightApi.logout(context)
                        loggedIn = false
                        data = null
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        overlay = { toolbar ->
            AnimatedVisibility(
                visible = notice != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = toolbar.calculateBottomPadding() + 24.dp)
                    .padding(horizontal = OneUi.PagePadding),
            ) {
                OneUiLiquidGlassBox(cornerRadius = 22.dp, strength = 0.8f) {
                    Text(
                        noticeShown,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                    )
                }
            }
        },
    ) { toolbar ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 24.dp)
                .padding(horizontal = OneUi.PagePadding),
        ) {
            if (!loggedIn) {
                MidnightLoginForm(onLoggedIn = { loggedIn = true; reload++ })
                return@Column
            }
            val d = data
            when {
                error != null && d == null -> OneUiCard(Modifier.fillMaxWidth()) {
                    Text("불러오지 못했습니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                d == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiLoading(size = 18.dp, stroke = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("불러오는 중", style = MaterialTheme.typography.bodyMedium)
                }
                d.roomUpdateRequired -> OneUiCard(Modifier.fillMaxWidth()) {
                    Text("호실 정보를 새로 입력해야 합니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("새 학기 기숙사 호실은 웹 사이트에서 입력해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OneUiButton(text = "웹 사이트 열기", onClick = { openInBrowser(context) }, style = OneUiButtonStyle.Neutral)
                }
                else -> MidnightDashboardBody(
                    d = d,
                    session = session,
                    floor = floor ?: d.floor,
                    busy = busy,
                    onSession = { session = it },
                    onFloor = { floor = it },
                    onSeat = { seat ->
                        when {
                            seat.mine -> act({ MidnightApi.cancel(context, session, floor) }, "${seat.number}번 좌석 신청을 취소했습니다")
                            seat.occupied -> notice = "${seat.number}번은 ${seat.applicantName ?: "다른 학생"}이 신청했습니다"
                            d.hasReservation -> notice = "이미 ${d.reservationSeat}번을 신청했습니다 — 먼저 취소해 주세요"
                            !d.canApply -> notice = d.helpText
                            else -> act({ MidnightApi.reserve(context, seat.number, session, floor) }, "${seat.number}번 좌석 신청이 완료되었습니다")
                        }
                    },
                    onCancel = { act({ MidnightApi.cancel(context, session, floor) }, "좌석 신청을 취소했습니다") },
                )
            }
        }
    }
}

private fun openInBrowser(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MIDNIGHT_STUDY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun MidnightLoginForm(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(MidnightSessionStore.name(context).orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    OneUiCard(Modifier.fillMaxWidth()) {
        Text("심야면학 로그인", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "심야면학 사이트에 가입한 이름과 비밀번호로 로그인합니다. 비밀번호는 저장하지 않고, 로그인 상태(토큰)만 이 기기에 암호화해 둡니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OneUiTextField(value = name, onValueChange = { name = it }, label = "이름", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OneUiTextField(
            value = password,
            onValueChange = { password = it },
            label = "비밀번호",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(14.dp))
        OneUiButton(
            text = if (busy) "로그인 중" else "로그인",
            enabled = !busy && name.isNotBlank() && password.isNotEmpty(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        MidnightApi.login(context, name, password)
                        password = ""
                        onLoggedIn()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "로그인하지 못했습니다"
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OneUiButton(
            text = "가입·비밀번호 찾기 (웹)",
            onClick = { openInBrowser(context) },
            style = OneUiButtonStyle.Neutral,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MidnightDashboardBody(
    d: MidnightDashboard,
    session: Int,
    floor: Int?,
    busy: Boolean,
    onSession: (Int) -> Unit,
    onFloor: (Int) -> Unit,
    onSeat: (MidnightSeat) -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OneUiChip(selected = session == 1, onClick = { onSession(1) }, label = "심야 1타임")
        if (d.session2Available) OneUiChip(selected = session == 2, onClick = { onSession(2) }, label = "심야 2타임")
        if (d.eligibleFloors.size > 1) {
            Spacer(Modifier.width(8.dp))
            d.eligibleFloors.forEach { f -> OneUiChip(selected = f == floor, onClick = { onFloor(f) }, label = "${f}층") }
        }
    }
    Spacer(Modifier.height(14.dp))

    // 오늘의 신청 + 상태.
    OneUiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 신청 · ${session}타임", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                d.statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (d.canApply) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (d.hasReservation) "${d.reservationFloor ?: d.floor}층 ${d.reservationSeat}번 좌석" else "아직 신청하지 않았습니다",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (d.hasReservation) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            listOfNotNull(
                d.date,
                if (d.sessionStart != null) "${d.sessionStart}–${d.sessionEnd.orEmpty()}" else null,
                d.room?.let { "${it}호" },
                "불참 ${d.absenceCount}회",
                d.bannedUntil?.let { "${it}까지 제한" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        if (d.hasReservation) {
            Spacer(Modifier.height(12.dp))
            val mustCancel2First = session == 1 && d.hasSession2Reservation
            OneUiButton(
                text = if (mustCancel2First) "2타임 신청을 먼저 취소하세요" else "신청 취소",
                onClick = onCancel,
                style = OneUiButtonStyle.Neutral,
                enabled = d.open && !mustCancel2First && !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    Text("${d.floor ?: floor ?: ""}층 좌석", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(d.helpText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    MidnightSeatLayout(d.seats, onSeat)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(midnightSeatColors().free, "선택 가능")
        LegendDot(midnightSeatColors().taken, "신청 완료")
        LegendDot(midnightSeatColors().mine, "내 좌석")
    }
}

private data class MidnightSeatColors(val free: Color, val taken: Color, val mine: Color, val freeText: Color, val takenText: Color)

@Composable
private fun midnightSeatColors(): MidnightSeatColors {
    val dark = MaterialTheme.colorScheme.onSurface.luminanceIsLight()
    return MidnightSeatColors(
        free = if (dark) Color(0xFF2A3B2E) else Color(0xFFDCF5E3),
        taken = if (dark) Color(0xFF3A3A3D) else Color(0xFFE5E7EB),
        mine = MaterialTheme.colorScheme.primary,
        freeText = if (dark) Color(0xFFBDE5C8) else Color(0xFF387457),
        takenText = if (dark) Color(0xFFB0B4B8) else Color(0xFF6B7280),
    )
}

/** onSurface 가 밝으면(=다크 테마) true. */
private fun Color.luminanceIsLight(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 좌석 배치 — 사이트와 같게: 좌석이 11·15석이면 앞쪽 1~7번 한 줄 묶음 + 뒤쪽 2×2 "섬"([9,8/10,11], [13,12/14,15]),
 * 그 외 층은 5열 격자.
 */
@Composable
private fun MidnightSeatLayout(seats: List<MidnightSeat>, onSeat: (MidnightSeat) -> Unit) {
    val byNumber = seats.associateBy { it.number }
    val pod = seats.size == 11 || seats.size == 15
    val cell = 60.dp
    val gap = 8.dp
    @Composable
    fun seat(n: Int) {
        val s = byNumber[n]
        if (s == null) Spacer(Modifier.size(cell)) else MidnightSeatCell(s, cell, onSeat)
    }
    @Composable
    fun group(content: @Composable () -> Unit) {
        Box(
            Modifier
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)), RoundedCornerShape(10.dp))
                .padding(8.dp),
        ) { content() }
    }
    if (!pod) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            seats.sortedBy { it.number }.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { MidnightSeatCell(it, cell, onSeat) } }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        group {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { seat(it) } }
                }
            }
        }
        val pods = if (seats.size == 15) listOf(listOf(9, 8, 10, 11), listOf(13, 12, 14, 15)) else listOf(listOf(9, 8, 10, 11))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pods.forEach { numbers ->
                group {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        numbers.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { seat(it) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MidnightSeatCell(seat: MidnightSeat, size: Dp, onSeat: (MidnightSeat) -> Unit) {
    val c = midnightSeatColors()
    val bg = when {
        seat.mine -> c.mine
        seat.occupied -> c.taken
        else -> c.free
    }
    val fg = when {
        seat.mine -> MaterialTheme.colorScheme.onPrimary
        seat.occupied -> c.takenText
        else -> c.freeText
    }
    Column(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onSeat(seat) }
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(seat.number.toString(), color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        seat.applicantName?.let {
            Text(it, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        seat.applicantRoom?.let {
            Text(it, color = fg.copy(alpha = 0.8f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

// MARK: - 일정 연동

/**
 * 심야면학 신청을 오늘 일정·Now Bar·위젯에 넣기 위한 저장소. 신청 화면·내역 조회·동기화에서 받은 현황으로 갱신하고,
 * [Timetable.installMidnight] 로 블록 계산에 넘깁니다. 신청한 타임만 들어갑니다 (신청 안 한 날은 평소처럼 23:10 에 끝).
 * 내용은 층·자리 번호뿐이라 평범한 SharedPreferences 에 둡니다. 최근 이틀치만 보관.
 */
object MidnightSchedule {
    private const val PREFS = "midnight_schedule"
    private const val KEY = "bookings"

    /** 바뀔 때마다 올라가는 번호 — 홈 화면이 이 값으로 오늘 블록을 다시 계산합니다. */
    var revision by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): MutableMap<java.time.LocalDate, List<Timetable.MidnightBooking>> {
        val raw = prefs(context).getString(KEY, null) ?: return mutableMapOf()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associate { key ->
                val arr = json.getJSONArray(key)
                java.time.LocalDate.parse(key) to (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Timetable.MidnightBooking(o.getInt("session"), o.getInt("start"), o.getInt("end"), o.optString("seat").ifEmpty { null })
                }
            }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun save(context: Context, map: Map<java.time.LocalDate, List<Timetable.MidnightBooking>>) {
        val today = PlanStore.today()
        val kept = map.filterKeys { !it.isBefore(today.minusDays(1)) }.filterValues { it.isNotEmpty() }
        val json = JSONObject()
        kept.forEach { (date, list) ->
            json.put(date.toString(), org.json.JSONArray().apply {
                list.forEach { b -> put(JSONObject().put("session", b.session).put("start", b.start).put("end", b.end).put("seat", b.seat ?: "")) }
            })
        }
        prefs(context).edit().putString(KEY, json.toString()).apply()
        Timetable.installMidnight(kept)
    }

    fun ensureInstalled(context: Context) {
        if (!Timetable.midnightInstalled()) Timetable.installMidnight(load(context))
    }

    private fun minutes(hhmm: String?): Int? {
        val parts = hhmm?.split(":") ?: return null
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** [session] 타임 현황 [d] 를 반영합니다 — 신청이 있으면 넣고, 없으면 그 타임을 뺍니다. 바뀌었으면 true. */
    fun record(context: Context, session: Int, d: MidnightDashboard): Boolean {
        val date = d.date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return false
        val map = load(context)
        val before = map[date].orEmpty()
        var list = before.filter { it.session != session }
        if (session == 1 && !d.session2Available) list = list.filter { it.session != 2 }
        val startMin = minutes(d.sessionStart)
        val endMin = minutes(d.sessionEnd)
        if (d.hasReservation && startMin != null && endMin != null) {
            // 정오 이전 시작은 자정 넘어서의 타임(예: 00:10)으로, 끝이 시작보다 이르면 다음 날로 봅니다.
            val start = if (startMin < 12 * 60) startMin + 24 * 60 else startMin
            var end = endMin
            while (end <= start) end += 24 * 60
            val seat = listOfNotNull((d.reservationFloor ?: d.floor)?.let { "${it}층" }, d.reservationSeat?.let { "${it}번" }).joinToString(" ")
            list = list + Timetable.MidnightBooking(session, start, end, seat.ifEmpty { null })
        }
        list = list.sortedBy { it.start }
        if (list == before.sortedBy { it.start }) {
            ensureInstalled(context)
            return false
        }
        map[date] = list
        save(context, map)
        revision++
        return true
    }

    /** 로그인돼 있으면 오늘 현황을 받아 반영합니다 (동기화 때). */
    suspend fun refresh(context: Context) {
        if (!MidnightSessionStore.isLoggedIn(context)) return
        val first = MidnightApi.dashboard(context, 1, null)
        if (first.session2Available) runCatching { MidnightApi.dashboard(context, 2, first.floor) }
    }

    /** (Dev 탭) 오늘 심야 1타임 23:50–01:00 에 가짜 신청을 넣어 일정·Now Bar 표시를 확인합니다. 다음 동기화 때 실제 값으로 덮입니다. */
    fun injectTest(context: Context) {
        val map = load(context)
        map[PlanStore.today()] = listOf(Timetable.MidnightBooking(1, 23 * 60 + 50, 25 * 60, "3층 5번"))
        save(context, map)
        revision++
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
        Timetable.installMidnight(emptyMap())
        revision++
    }
}
