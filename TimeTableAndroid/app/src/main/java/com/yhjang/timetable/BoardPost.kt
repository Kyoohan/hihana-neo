package com.yhjang.timetable

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Html
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiButtonStyle
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiDivider
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiGroupColumn
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// MARK: - 게시글 상세 (포털 JSON)

data class HanaPostFile(val name: String, val sizeLabel: String?, val url: String)

data class HanaPostDetail(
    val title: String,
    val writer: String?,
    val date: String?,
    val views: Int?,
    /** 포털 편집기가 만든 본문 HTML — 이미지·표가 들어 있어 웹뷰로 그립니다. */
    val html: String,
    /** 요약·알림용 순수 텍스트. */
    val text: String,
    val files: List<HanaPostFile>,
)

object HanaPostApi {
    private const val BASE = "https://hh.hana.hs.kr"

    /** board_view.do 는 인증된 요청에 JSON(boardVo)으로 답합니다 — 본문·첨부를 그대로 꺼냅니다. */
    suspend fun detail(context: Context, url: String): HanaPostDetail =
        parse(HanaAcademicApi.fetchBoardDetail(context, url))

    fun parse(raw: String): HanaPostDetail {
        val vo = JSONObject(raw).getJSONObject("boardVo")
        fun str(key: String) = vo.optString(key).takeIf { !vo.isNull(key) && it.isNotBlank() && it != "null" }
        val html = str("bd_content").orEmpty()
        val files = vo.optJSONArray("fileList")?.let { list ->
            (0 until list.length()).mapNotNull { i ->
                val f = list.optJSONObject(i) ?: return@mapNotNull null
                val src = f.optString("file_src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HanaPostFile(
                    name = f.optString("orignl_file_nm").ifBlank { src.substringAfterLast('/') },
                    sizeLabel = f.optString("file_size_nm").takeIf { it.isNotBlank() },
                    url = if (src.startsWith("http")) src else BASE + src,
                )
            }
        }.orEmpty()
        return HanaPostDetail(
            title = str("bd_title")?.let(::plainText).orEmpty(),
            writer = str("bd_writer") ?: str("in_mem_name"),
            date = str("inputdate")?.take(16),
            views = vo.optInt("bd_view", -1).takeIf { it >= 0 },
            html = html,
            text = plainText(html),
            files = files,
        )
    }

    /** 본문 HTML → 문단 줄바꿈만 살린 텍스트. 이미지 자리 표시 문자(￼)와 연속 빈 줄은 걷어 냅니다. */
    fun plainText(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
            .replace('￼', ' ')
            .replace(' ', ' ')
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    /** 알림 한 칸에 넣을 본문 앞부분 — 인사말 한 줄 정도는 건너뛸 만큼 넉넉히 자릅니다. */
    fun excerpt(text: String, max: Int = 220): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= max) flat else flat.take(max).trimEnd() + "…"
    }
}

// MARK: - AI 요약 (Cloudflare Workers AI)

/** 목록에 붙는 한 줄([line])과 글 화면 위의 자세한 요약([points]). 짧은 글은 본문 앞부분이 [line], [points] 는 빈 목록. */
data class PostSummary(val line: String, val points: List<String>)

/**
 * 게시글 요약. 요약 서버(summary-worker)가 글 내용마다 한 번만 AI 로 만들고 보관해 모든 사용자가 나눠 씁니다.
 * 앱은 받은 요약을 글마다 기기에 저장해 두어, 목록·글 화면·알림 어디서든 다시 요청하지 않습니다.
 * 짧은 글은 본문이 곧 요약이라 서버를 부르지 않고 본문 앞부분을 한 줄로 씁니다.
 */
object PostSummarizer {
    private const val TAG = "PostSummary"
    private const val ENDPOINT = "https://hihana-summary.kyoohan0711ultra.workers.dev/summarize"
    private const val PREFS = "post_summaries_v6"
    private const val MAX_CACHED = 300
    /** 이보다 짧은 글은 AI 없이 본문 앞부분을 한 줄 요약으로 씁니다. */
    const val MIN_CHARS = 100
    /** 이보다 짧은 글은 한 줄 요약만 쓰고, 글 화면의 자세한 요약 카드는 숨깁니다(본문이 곧 요약). */
    private const val MIN_CARD_CHARS = 150
    private const val MAX_INPUT_CHARS = 6000

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    /** 목록 행이 구독하는 한 줄 요약 — 받아 오는 대로 행이 채워집니다. */
    private val lines = mutableStateMapOf<String, String>()
    private var linesLoaded = false
    private val inFlight = mutableSetOf<String>()

    sealed interface State {
        data object Loading : State
        data class Done(val summary: PostSummary) : State
        data class Failed(val message: String) : State
        /** 짧은 글 — 자세한 요약 카드를 숨깁니다. */
        data object Hidden : State
    }

    fun key(url: String): String =
        Regex("/board/(\\d+)/(\\d+)/").find(url)?.let { "${it.groupValues[1]}_${it.groupValues[2]}" } ?: url.hashCode().toString()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cached(context: Context, key: String): PostSummary? =
        prefs(context).getString("s_$key", null)?.let(::decode)

    /** 목록 행에서 부르는 한 줄 요약. 아직 없으면 null. */
    fun line(context: Context, url: String): String? {
        if (!linesLoaded) {
            linesLoaded = true
            prefs(context).all.forEach { (k, v) ->
                if (k.startsWith("s_") && v is String) decode(v)?.let { lines[k.removePrefix("s_")] = it.line }
            }
        }
        return lines[key(url)]?.takeIf { it.isNotEmpty() }
    }

    private fun store(context: Context, key: String, summary: PostSummary) {
        val p = prefs(context)
        val order = p.getString("order", "").orEmpty().split(',').filter { it.isNotEmpty() && it != key } + key
        val keep = order.takeLast(MAX_CACHED)
        p.edit().apply {
            order.dropLast(keep.size).forEach { remove("s_$it") }
            putString("s_$key", encode(summary))
            putString("order", keep.joinToString(","))
        }.apply()
        lines[key] = summary.line
    }

    /** 저장된 요약이 있으면 그것을, 없으면 만들어(짧은 글은 본문 앞부분) 저장하고 돌려줍니다. 실패하면 예외. */
    suspend fun ensure(context: Context, key: String, title: String, text: String): PostSummary {
        cached(context, key)?.let { return it }
        val summary = when {
            text.length < MIN_CHARS -> PostSummary(HanaPostApi.excerpt(text, 40), emptyList())
            text.length < MIN_CARD_CHARS -> request(title, text).copy(points = emptyList())
            else -> request(title, text)
        }
        store(context, key, summary)
        return summary
    }

    private suspend fun request(title: String, text: String): PostSummary = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("title", title)
            .put("text", text.take(MAX_INPUT_CHARS))
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(ENDPOINT).header("x-app", "hihana-neo").post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("summary ${response.code}: ${raw.take(200)}")
            decode(raw) ?: throw IllegalStateException("summary unparsable")
        }
    }

    /** 글 화면용 — 상태를 차례로 알려 줍니다. */
    suspend fun summarize(context: Context, key: String, title: String, text: String, onState: (State) -> Unit) {
        cached(context, key)?.let { onState(if (it.points.isEmpty()) State.Hidden else State.Done(it)); return }
        if (text.length < MIN_CARD_CHARS) {
            try {
                ensure(context, key, title, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "short summary failed", e)
            }
            onState(State.Hidden)
            return
        }
        onState(State.Loading)
        try {
            onState(State.Done(ensure(context, key, title, text)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "summarize failed", e)
            onState(State.Failed(if (e.message?.contains("429") == true) "요약 요청이 많아 잠시 뒤 다시 시도해 주세요" else "요약을 만들지 못했습니다"))
        }
    }

    /**
     * 목록에 보이는 글들의 한 줄 요약을 미리 채웁니다. 이미 있는 글은 건너뛰고, 포털·서버에 부담이 없게 한 건씩 차례로.
     * 한 글이 실패해도 나머지는 계속합니다.
     */
    suspend fun prefetch(context: Context, posts: List<HanaBoardPost>) {
        line(context, "")
        for (post in posts) {
            val key = key(post.url)
            if (lines.containsKey(key) || !inFlight.add(key)) continue
            try {
                val detail = HanaPostApi.detail(context, post.url)
                ensure(context, key, detail.title.ifEmpty { post.title }, detail.text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "prefetch failed ${post.url}", e)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private fun encode(s: PostSummary): String =
        JSONObject().put("line", s.line).put("points", JSONArray(s.points)).toString()

    private fun decode(raw: String): PostSummary? = runCatching {
        val o = JSONObject(raw)
        val line = o.optString("line").trim()
        val arr = o.optJSONArray("points")
        val points = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
        // 본문이 이미지뿐인 글은 빈 요약으로 저장해 두어 목록을 열 때마다 다시 받지 않게 합니다.
        PostSummary(line, points)
    }.getOrNull()
}

// MARK: - 화면

/**
 * 게시글 — 제목 → AI 요약(한 줄 + 자세히) → 본문 → 첨부. 본문 이미지를 누르면 확대 보기. 본문 HTML 만 웹뷰로 그리고 나머지는 네이티브라
 * 포털 머리글·메뉴 없이 글만 보입니다. 상세를 못 읽으면 [onFallbackWeb] 으로 예전 웹 화면을 엽니다.
 */
@Composable
fun BoardPostScreen(
    url: String,
    fallbackTitle: String?,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit,
    onFallbackWeb: () -> Unit,
) {
    val context = LocalContext.current
    var detail by remember(url) { mutableStateOf<HanaPostDetail?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var summary by remember(url) { mutableStateOf<PostSummarizer.State?>(null) }
    var zoomImage by remember(url) { mutableStateOf<String?>(null) }
    var summaryRun by remember(url) { mutableIntStateOf(0) }
    val key = remember(url) { PostSummarizer.key(url) }

    LaunchedEffect(url) {
        try {
            detail = HanaPostApi.detail(context, url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BoardPost", "detail failed", e)
            error = e.message ?: "게시글을 불러오지 못했습니다"
        }
    }
    LaunchedEffect(detail, summaryRun) {
        val d = detail ?: return@LaunchedEffect
        PostSummarizer.summarize(context, key, d.title, d.text) { summary = it }
    }

    BackHandler(onBack = onDismiss)

    OneUiFullScreen(
        title = "게시글",
        onDismiss = onDismiss,
        actions = {
            OneUiTextButton(text = "브라우저로 열기", onClick = onOpenBrowser, color = MaterialTheme.colorScheme.primary)
        },
    ) { toolbar ->
        val d = detail
        when {
            error != null -> Column(Modifier.padding(toolbar).padding(OneUi.PagePadding)) {
                OneUiCard(modifier = Modifier.fillMaxWidth()) {
                    Text("게시글을 불러오지 못했습니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "포털 페이지로 열어 볼 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    OneUiButton(text = "웹으로 보기", onClick = onFallbackWeb)
                }
            }
            d == null -> Box(Modifier.fillMaxSize().padding(toolbar), contentAlignment = Alignment.TopCenter) {
                if (fallbackTitle != null) PostHeader(fallbackTitle, null)
                OneUiLoading(Modifier.padding(top = 96.dp), size = 28.dp, stroke = 3.dp)
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = toolbar.calculateTopPadding(), bottom = toolbar.calculateBottomPadding() + 32.dp),
            ) {
                PostHeader(d.title, listOfNotNull(d.writer, d.date, d.views?.let { "조회 $it" }).joinToString(" · "))
                SummaryCard(summary, onRetry = { summaryRun++ })
                PostBody(d.html, onImage = { zoomImage = it })
                if (d.files.isNotEmpty()) Attachments(d.files)
            }
        }
    }

    zoomImage?.let { src -> ImageViewer(src, onDismiss = { zoomImage = null }) }
}

@Composable
private fun PostHeader(title: String, meta: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = OneUi.PagePadding + 4.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!meta.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// MARK: - AI 표식 (그라데이션)

/** AI 요약에만 쓰는 파랑 → 보라 → 분홍 그라데이션. 앱 강조색과 섞이지 않게 따로 둡니다. */
private val AiBlue = Color(0xFF6EA8FF)
private val AiViolet = Color(0xFFA78BFA)
private val AiPink = Color(0xFFF29FC8)
private val AiGradient = Brush.linearGradient(listOf(AiBlue, AiViolet, AiPink))
private val AiTint = Brush.linearGradient(listOf(AiBlue.copy(alpha = 0.14f), AiViolet.copy(alpha = 0.11f), AiPink.copy(alpha = 0.10f)))

/** 큰 별 + 작은 별 반짝이 — 그라데이션으로 칠합니다. */
private val AiSparkle: ImageVector by lazy {
    ImageVector.Builder("AiSparkle", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M12 2.5c.5 4.6 2.9 7 7.5 7.5-4.6.5-7 2.9-7.5 7.5-.5-4.6-2.9-7-7.5-7.5 4.6-.5 7-2.9 7.5-7.5Z"),
            fill = AiGradient,
        )
        .addPath(
            addPathNodes("M19 15.5c.2 1.9 1.1 2.8 3 3-1.9.2-2.8 1.1-3 3-.2-1.9-1.1-2.8-3-3 1.9-.2 2.8-1.1 3-3Z"),
            fill = AiGradient,
            fillAlpha = 0.85f,
        )
        .build()
}

@Composable
private fun AiSparkleIcon(size: Dp) {
    Image(AiSparkle, contentDescription = null, modifier = Modifier.size(size))
}

/** 게시판 목록의 한 줄 요약 알약 — 그라데이션 테두리와 옅은 그라데이션 바탕, 최대 두 줄. */
@Composable
fun AiSummaryPill(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(AiTint)
            .border(1.dp, AiGradient, shape)
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(Modifier.padding(top = 2.dp)) { AiSparkleIcon(15.dp) }
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 글 화면 위 AI 요약 카드 — 그라데이션 테두리, 모서리에서 은은하게 번지는 빛, 한 줄 요약 + 자세한 글머리표. */
@Composable
private fun SummaryCard(state: PostSummarizer.State?, onRetry: () -> Unit) {
    val visible = state != null && state !is PostSummarizer.State.Hidden
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(spring(stiffness = 500f, dampingRatio = 0.9f)),
    ) {
        val shape = RoundedCornerShape(OneUi.CornerLarge)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = OneUi.PagePadding, vertical = 8.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .drawBehind {
                    val r = size.maxDimension
                    drawRect(Brush.radialGradient(listOf(AiBlue.copy(alpha = 0.22f), Color.Transparent), Offset.Zero, r * 0.75f))
                    drawRect(Brush.radialGradient(listOf(AiViolet.copy(alpha = 0.18f), Color.Transparent), Offset(size.width, size.height * 0.1f), r * 0.65f))
                    drawRect(Brush.radialGradient(listOf(AiPink.copy(alpha = 0.14f), Color.Transparent), Offset(size.width * 0.8f, size.height * 1.1f), r * 0.7f))
                }
                .border(1.dp, AiGradient, shape)
                .animateContentSize(spring(stiffness = 400f, dampingRatio = 0.9f))
                .padding(OneUi.CardPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiSparkleIcon(17.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    "AI 요약",
                    style = MaterialTheme.typography.labelLarge.copy(brush = AiGradient),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            when (state) {
                is PostSummarizer.State.Done -> {
                    Text(state.summary.line, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.summary.points.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                        Spacer(Modifier.height(12.dp))
                        SummaryLines(state.summary.points)
                    }
                }
                PostSummarizer.State.Loading -> SummarySkeleton()
                is PostSummarizer.State.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OneUiTextButton(text = "다시 시도", onClick = onRetry, color = MaterialTheme.colorScheme.primary)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SummaryLines(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Row {
                Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(AiGradient))
                Spacer(Modifier.width(9.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
            }
        }
    }
}

/** 요약이 흘러나오기 전 자리 — 세 줄 막대가 천천히 숨 쉬듯 깜빡입니다. */
@Composable
private fun SummarySkeleton() {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.alpha(pulse)) {
        listOf(1f, 0.86f, 0.62f).forEach { w ->
            Box(
                Modifier
                    .fillMaxWidth(w)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            )
        }
    }
}

/**
 * 본문 HTML 을 웹뷰로 — 높이는 페이지가 알려 주는 실제 높이로 맞춰 바깥 스크롤 하나로만 읽습니다.
 * 링크는 앱 밖 브라우저로 엽니다.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun PostBody(html: String, onImage: (String) -> Unit) {
    if (html.isBlank()) return
    var heightDp by remember(html) { mutableIntStateOf(0) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    // 다크 여부는 폰 시스템 설정이 아니라 앱 테마(카드 바탕색)로 판단합니다 — 둘이 다를 수 있습니다.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val doc = remember(html, textColor, linkColor, dark) { wrapBody(html, textColor, linkColor, dark) }
    OneUiCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OneUi.PagePadding, vertical = 8.dp),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.userAgentString = HanaPortalClient.UA
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        // 웹뷰의 자동 어둡게 하기는 폰 시스템 다크 모드를 따라가 앱 테마와 어긋납니다(밝은 앱에서 글자가
                        // 흰색으로 뒤집힘). 끄고, 다크 테마일 때만 아래 스크립트가 본문 색을 직접 맞춥니다.
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun height(px: Float) {
                            post { heightDp = px.toInt() + 2 }
                        }

                        @JavascriptInterface
                        fun image(src: String) {
                            post { onImage(src) }
                        }
                    }, "PostBody")
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url ?: return true
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            return true
                        }
                    }
                    tag = doc
                    loadDataWithBaseURL("https://hh.hana.hs.kr/", doc, "text/html", "utf-8", null)
                }
            },
            // 앱 테마를 바꾸면 새 색으로 다시 그립니다.
            update = { view ->
                if (view.tag != doc) {
                    view.tag = doc
                    view.loadDataWithBaseURL("https://hh.hana.hs.kr/", doc, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxWidth().height(if (heightDp > 0) heightDp.dp else 120.dp),
        )
    }
}

private fun cssColor(argb: Int) =
    "rgb(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF})"

private fun wrapBody(html: String, textColor: Int, linkColor: Int, dark: Boolean): String = """
<!doctype html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  html { font-size: 10px; }
  body { margin: 0; padding: 0; background: transparent; color: ${cssColor(textColor)};
         font-family: sans-serif; font-size: 15px; line-height: 1.6; word-break: keep-all; overflow-wrap: anywhere; }
  body * { font-family: inherit !important; max-width: 100%; }
  p { margin: 0 0 2px; margin-left: 0 !important; margin-right: 0 !important; }
  /* 포털 편집기가 본문을 큰 여백(2.5rem 4rem 등)으로 감싸 둡니다 — 카드 안에서는 여백을 걷어 냅니다. */
  #c div, #c section, #c article { padding: 0 !important; margin-left: 0 !important; margin-right: 0 !important; margin-top: 0 !important; margin-bottom: 0 !important; }
  img { height: auto !important; border-radius: 8px; cursor: zoom-in; }
  table { display: block; overflow-x: auto; border-collapse: collapse; }
  td, th { border: 1px solid rgba(128,128,128,.35); padding: 4px 6px; }
  a { color: ${cssColor(linkColor)}; }
</style></head><body><div id="c">$html</div>
<script>
  (function () {
    var c = document.getElementById('c');
    function report() { PostBody.height(c.getBoundingClientRect().height); }
    // 빈 껍데기(첨부 자리의 빈 목록, 빈 div)와 글 앞뒤의 빈 문단을 걷어 내 카드에 빈 공간이 생기지 않게 합니다.
    function blank(el) { return !el.textContent.trim() && !el.querySelector('img,table,iframe,video,hr'); }
    c.querySelectorAll('ul,ol,div').forEach(function (el) { if (blank(el)) el.remove(); });
    var ps = Array.prototype.slice.call(c.querySelectorAll('p'));
    for (var i = 0; i < ps.length && blank(ps[i]); i++) ps[i].remove();
    for (var j = ps.length - 1; j >= i && blank(ps[j]); j--) ps[j].remove();

    // 다크 테마: 편집기가 박아 넣은 색(검은 글자, 흰·회색 칸 바탕, 검은 테두리)만 골라 어두운 바탕에 맞게 바꿉니다.
    // 빨강·파랑 같은 강조색 글자는 밝기가 충분하면 그대로 둡니다.
    if ($dark) {
      // 배경이 투명한 표·안내문 이미지(검은 글자)는 어두운 카드 위에서 안 보여 흰 바탕을 깔아 줍니다.
      c.querySelectorAll('img').forEach(function (img) { img.style.setProperty('background-color', '#fff', 'important'); });
      var TEXT = '${cssColor(textColor)}';
      function rgb(v) {
        var m = v && v.match(/rgba?\(([^)]+)\)/);
        if (!m) return null;
        var p = m[1].split(',').map(parseFloat);
        return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
      }
      function lum(c) { return (0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b) / 255; }
      c.querySelectorAll('*').forEach(function (el) {
        if (el.tagName === 'IMG') return;
        var cs = getComputedStyle(el);
        var fg = rgb(cs.color);
        var colorful = fg && Math.max(fg.r, fg.g, fg.b) - Math.min(fg.r, fg.g, fg.b) > 80;
        if (fg && !colorful && lum(fg) < 0.55) el.style.setProperty('color', TEXT, 'important');
        var bg = rgb(cs.backgroundColor);
        if (bg && bg.a > 0 && lum(bg) > 0.3) el.style.setProperty('background-color', 'rgba(255,255,255,' + (lum(bg) > 0.85 ? 0.04 : 0.1) + ')', 'important');
        ['Top', 'Right', 'Bottom', 'Left'].forEach(function (side) {
          var bc = rgb(cs['border' + side + 'Color']);
          if (bc && parseFloat(cs['border' + side + 'Width']) > 0 && lum(bc) < 0.5) el.style.setProperty('border-' + side.toLowerCase() + '-color', 'rgba(255,255,255,0.22)', 'important');
        });
      });
    }
    new ResizeObserver(report).observe(c);
    // 이미지를 누르면 앱의 확대 보기로 — 링크로 감싼 이미지도 확대를 우선합니다.
    document.addEventListener('click', function (e) {
      var img = e.target.closest && e.target.closest('img');
      if (!img) return;
      e.preventDefault();
      PostBody.image(img.currentSrc || img.src);
    }, true);
    window.addEventListener('load', report);
    report();
  })();
</script>
</body></html>
""".trimIndent()

@Composable
private fun Attachments(files: List<HanaPostFile>) {
    val context = LocalContext.current
    Text(
        "첨부파일 ${files.size}",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = OneUi.PagePadding + 4.dp, top = 16.dp, bottom = 8.dp),
    )
    OneUiGroupColumn(Modifier.padding(horizontal = OneUi.PagePadding)) {
        files.forEachIndexed { index, file ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { downloadAttachment(context, file) }
                    .padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ext = file.name.substringAfterLast('.', "").uppercase().take(4)
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ext.ifEmpty { "FILE" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (file.sizeLabel != null) {
                        Text(file.sizeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("받기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (index != files.lastIndex) OneUiDivider()
        }
    }
}

/** 첨부는 시스템 다운로드로 원래 파일 이름 그대로 '다운로드' 폴더에 받습니다. 완료 알림을 누르면 바로 열립니다. */
private fun downloadAttachment(context: Context, file: HanaPostFile) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(file.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return
    }
    runCatching {
        val safeName = file.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val request = DownloadManager.Request(Uri.parse(file.url))
            .setTitle(file.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
            .addRequestHeader("User-Agent", HanaPortalClient.UA)
        CookieManager.getInstance().getCookie(file.url)?.let { request.addRequestHeader("Cookie", it) }
        context.getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(context, "다운로드를 시작했습니다", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "다운로드하지 못했습니다", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 이미지 확대 보기 — 두 손가락으로 확대·이동, 두 번 탭으로 확대. 웹뷰의 기본 확대를 그대로 써서
 * 큰 가정통신문 이미지도 흐려지지 않고 원본 해상도로 봅니다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ImageViewer(src: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        // 투명 배경 이미지(표 캡처 등)는 흰 바탕 위에 원래 색 그대로 — 자동 어둡게 하기도 끕니다.
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                        }
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = HanaPortalClient.UA
                        val escaped = src.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
                        loadDataWithBaseURL(
                            "https://hh.hana.hs.kr/",
                            """
                            <!doctype html><html><head>
                            <meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=8, user-scalable=yes">
                            <style>html,body{margin:0;height:100%;background:#000}
                            body{display:flex;align-items:center;justify-content:center}
                            img{max-width:100%;max-height:100%;object-fit:contain;background:#fff}</style>
                            </head><body><img src="$escaped"></body></html>
                            """.trimIndent(),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
            }
        }
    }
}
