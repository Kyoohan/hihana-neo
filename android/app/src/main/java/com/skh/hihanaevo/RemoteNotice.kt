package com.skh.hihanaevo

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.OneUiBadge
import com.skh.hihanaevo.ui.OneUiDialog
import com.skh.hihanaevo.ui.OneUiDialogButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 원할 때 띄우는 공지 팝업 — 요약 서버 /notice 의 한 건(summary-worker/notice.mjs 로 올리고 내림). 업데이트가 아니어도
 * 사용자에게 알릴 것이 있을 때(클래스룸 다시 허용 등) 씁니다.
 */
data class RemoteNotice(
    val id: String,
    val title: String,
    val body: String,
    val link: String?,
    val button: String?,
    val postedAt: LocalDate?,
)

object RemoteNoticeStore {
    private const val ENDPOINT = "https://hihana-summary.kyoohan0711ultra.workers.dev/notice"
    private const val PREFS = "remote_notice"
    private const val KEY_SEEN = "seen"
    private const val KEY_CHECKED = "checked_at"
    /** 복귀할 때마다 묻지 않게 — 공지는 몇 분 늦어도 됩니다. */
    private const val INTERVAL_MS = 15 * 60_000L
    private const val KEEP_SEEN = 20
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 띄울 공지 — 없거나 이미 닫았거나, 기한이 지났거나, 이 빌드가 대상이 아니면 null. 15분 안에 물어봤으면 묻지 않습니다. */
    suspend fun pending(context: Context): RemoteNotice? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_CHECKED, 0L) < INTERVAL_MS) return@withContext null
        val request = Request.Builder().url(ENDPOINT).header("x-app", "hihana-neo").build()
        val raw = client.newCall(request).execute().use { res ->
            when (res.code) {
                200 -> res.body?.string()
                204 -> ""
                else -> null
            }
        } ?: return@withContext null
        p.edit().putLong(KEY_CHECKED, now).apply()
        if (raw.isBlank()) return@withContext null
        parse(JSONObject(raw))?.takeIf { it.id !in seen(context) }
    }

    /** 닫은 공지 — 같은 id 는 다시 띄우지 않습니다. 최근 것만 남깁니다. */
    fun markSeen(context: Context, id: String) {
        val list = (seen(context) - id) + id
        prefs(context).edit().putString(KEY_SEEN, list.takeLast(KEEP_SEEN).joinToString("\n")).apply()
    }

    private fun seen(context: Context): List<String> =
        prefs(context).getString(KEY_SEEN, null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    internal fun parse(o: JSONObject): RemoteNotice? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = o.optString("title").trim().takeIf { it.isNotEmpty() } ?: return null
        // 대상 — dev 는 개발 빌드만(먼저 내 폰에서 확인), public 은 일반 빌드, 없으면 모두. 모르는 대상은 띄우지 않습니다.
        val forThisBuild = when (o.optString("to")) {
            "" -> true
            "dev" -> BuildConfig.DEV
            "public" -> !BuildConfig.DEV
            else -> false
        }
        if (!forThisBuild) return null
        val until = o.optString("until").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (until != null && LocalDate.now(KST).isAfter(until)) return null
        val link = o.optString("link").takeIf { it.startsWith("https://") }
        return RemoteNotice(
            id = id,
            title = title,
            body = o.optString("body").trim(),
            link = link,
            button = if (link != null) o.optString("button").trim().ifEmpty { "열기" } else null,
            postedAt = runCatching { Instant.parse(id).atZone(KST).toLocalDate() }.getOrNull(),
        )
    }
}

/** 공지 팝업 — 업데이트 안내와 같은 One UI 다이얼로그. 링크가 있으면 '닫기 | 버튼', 없으면 '확인' 하나. */
@Composable
internal fun RemoteNoticeDialog(notice: RemoteNotice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = notice.title,
        buttons = if (notice.link != null) {
            listOf(
                OneUiDialogButton("닫기", onDismiss),
                OneUiDialogButton(notice.button ?: "열기", {
                    openExternal(context, notice.link)
                    onDismiss()
                }),
            )
        } else {
            listOf(OneUiDialogButton("확인", onDismiss))
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OneUiBadge("공지")
            notice.postedAt?.let {
                Spacer(Modifier.width(8.dp))
                Text("${it.monthValue}월 ${it.dayOfMonth}일", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
        }
        if (notice.body.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            // 긴 공지도 버튼이 화면 밖으로 밀리지 않게 본문만 스크롤.
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(notice.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface.copy(alpha = 0.9f))
            }
        }
    }
}
