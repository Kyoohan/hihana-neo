package com.yhjang.timetable

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * 포털 학생시간표 페이지는 JavaScript 로 표를 그리기 때문에, 정적 HTML 을 받아
 * [ExamParser] 로 파싱하면 표가 비어 버립니다. 그래서
 * **메인 스레드에 붙여 둔 숨은 WebView** 로 페이지를 실제로 렌더링한 뒤,
 * 렌더링된 DOM 의 표 바깥 HTML 을 [evaluateJavascript] 콜백으로 받아옵니다.
 *
 * WebView 는 메인 스레드에서만 만들어야 하므로 [HanaTimetableWebViewHost] 가
 * 앱 화면에 1dp 숨김으로 붙여 [attach] 해 둡니다. 화면(Activity)이 없으면
 * 호스트가 없어 조용히 null 을 돌려주고, 호출부는 기존 HTML/JSON 폴백을 씁니다.
 */
object HanaTimetableWebView {

    private const val TAG = "HanaTimetable"

    /** AJAX 표가 다 그려지길 기다리는 최대 시간. */
    private const val RENDER_DEADLINE_MS = 10_000L
    private const val POLL_INTERVAL_MS = 500L

    /** 전체 요청 상한 — 어떤 경우에도 동기화를 오래 붙잡지 않습니다. */
    private const val REQUEST_TIMEOUT_MS = 15_000L

    /** 표가 준비됐는지 — 본문에 요일이 3개 이상 보이면 그려진 것으로 봅니다. */
    private const val JS_READY = """
        (function() {
            try {
                var txt = document.body ? document.body.innerText : '';
                var days = ['월', '화', '수', '목', '금'];
                var n = 0;
                for (var i = 0; i < days.length; i++) {
                    if (txt.indexOf(days[i]) >= 0) n++;
                }
                return n >= 3;
            } catch (e) { return false; }
        })();
    """

    /** 모든 표의 바깥 HTML 을 이어 붙입니다 — 어느 표가 시간표인지는 Kotlin 파서가 고릅니다. */
    private const val JS_TABLES = """
        (function() {
            try {
                var tables = document.querySelectorAll('table');
                var out = '';
                for (var i = 0; i < tables.length; i++) { out += tables[i].outerHTML; }
                if (out) return out;
            } catch (e) {}
            try { return document.body ? document.body.innerHTML : ''; } catch (e) { return ''; }
        })();
    """

    @Volatile private var host: WebView? = null
    private val mutex = Mutex()

    /** [HanaTimetableWebViewHost] 가 메인 스레드에서 호출합니다. */
    fun attach(view: WebView) {
        host = view
    }

    /** 화면이 사라질 때 호스트 WebView 를 정리합니다. */
    fun release() {
        val view = host ?: return
        host = null
        view.stopLoading()
        view.destroy()
    }

    /**
     * 렌더링된 시간표 표 HTML 을 받아옵니다. WebView 가 없거나 시간 안에 못 받으면 null.
     * 동시 호출을 Mutex 로 직렬화해 한 WebView 를 안전하게 재사용합니다.
     */
    suspend fun loadHtml(): String? {
        val view = awaitHost() ?: run {
            Log.d(TAG, "웹뷰 호스트가 없어 렌더 DOM 조회를 건너뜁니다")
            return null
        }
        return mutex.withLock {
            val html = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                withContext(Dispatchers.Main) { awaitRender(view) }
            }
            if (html == null) Log.d(TAG, "웹뷰 렌더 DOM 을 받지 못했습니다 (시간 초과 또는 오류)")
            html
        }
    }

    /** 앱 시작 직후엔 호스트가 아직 안 붙었을 수 있어 잠깐 기다립니다. */
    private suspend fun awaitHost(timeoutMs: Long = 2_000L): WebView? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var view = host
        while (view == null && System.currentTimeMillis() < deadline) {
            delay(100)
            view = host
        }
        return view
    }

    private suspend fun awaitRender(view: WebView): String? =
        suspendCancellableCoroutine { cont ->
            var resumed = false
            var polling = false
            val startedAt = System.currentTimeMillis()

            fun deliver(html: String?) {
                if (resumed) return
                resumed = true
                view.webViewClient = WebViewClient()
                if (cont.isActive) cont.resume(html) else view.stopLoading()
            }

            fun extract() {
                if (resumed) return
                view.evaluateJavascript(JS_TABLES) { result -> deliver(decodeJsString(result)) }
            }

            fun poll() {
                if (resumed) return
                view.evaluateJavascript(JS_READY) { ready ->
                    when {
                        ready == "true" -> extract()
                        System.currentTimeMillis() - startedAt < RENDER_DEADLINE_MS ->
                            view.postDelayed({ poll() }, POLL_INTERVAL_MS)
                        // 시간이 다 되면 그래도 표가 있으면 회수하고, 없으면 빈 결과로 넘깁니다.
                        else -> extract()
                    }
                }
            }

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(w: WebView?, url: String?) {
                    // onPageFinished 는 AJAX 표가 그려지기 전일 수 있어 잠깐 뒤부터 확인합니다.
                    if (polling) return
                    polling = true
                    w?.postDelayed({ poll() }, 300L)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) deliver(null)
                }
            }

            // loadUrl 전에 포털 세션 쿠키를 심어야 로그인된 페이지가 렌더링됩니다.
            HanaPortalClient.get().syncCookiesToWebView()
            view.loadUrl(STUDENT_TIMETABLE_URL)
            cont.invokeOnCancellation { view.stopLoading() }
        }

    /** evaluateJavascript 결과는 JSON 문자열 리터럴 — 따옴표/이스케이프를 풀어 원문 HTML 로. */
    private fun decodeJsString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
    }
}

/**
 * 화면 어디서든 [HanaTimetableWebView] 가 쓸 숨은 WebView 를 앱 계층에 붙여 둡니다.
 * 주 탭뿐 아니라 모든 탭에서 ↻/복귀 동기화가 웹뷰 렌더 경로를 타도록 앱 루트에 둡니다.
 * 1dp 크기를 화면 밖으로 밀어 그리지 않지만, 창에 붙어 있어 JS 가 정상 실행됩니다.
 */
@Composable
fun HanaTimetableWebViewHost() {
    DisposableEffect(Unit) {
        onDispose { HanaTimetableWebView.release() }
    }
    // Compose 의 뷰 계층은 자식을 클리핑하지 않아, 일부 기기(삼성 WebView)에서는 1dp 웹뷰가 페이지 전체를
    // 화면 위에 그려 버립니다 — 페이지 로딩 중엔 검은 면으로 보였습니다. 영역을 명시적으로 자르고
    // 알파를 0 으로 둡니다 (visibility 를 INVISIBLE 로 하면 크로미움이 타이머를 죄어 JS 폴링이 느려지므로 안 씀).
    Box(
        modifier = Modifier
            .size(1.dp)
            .offset(x = (-10).dp, y = (-10).dp)
            .clipToBounds(),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    alpha = 0f
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    HanaTimetableWebView.attach(this)
                }
            },
            modifier = Modifier.size(1.dp).clipToBounds(),
        )
    }
}
