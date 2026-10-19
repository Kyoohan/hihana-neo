package com.yhjang.timetable

import com.yhjang.timetable.ui.onPageVariantColor
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiButton
import com.yhjang.timetable.ui.OneUiButtonStyle
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiChip
import com.yhjang.timetable.ui.OneUiDivider
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiLiquidGlassBox
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// MARK: - 하이하나 통합검색 API

/**
 * 하이하나 통합검색 — 포털 머리글의 검색과 같은 요청입니다. 모든 게시판·모든 기간에서 찾습니다 (조회만).
 *   개요  GET  /main/search.do?extra1=검색어 (Accept: json) → { BD_TOT, groupTotal{bmt_idx: 건수}, boardMstList[{bmt_idx, bmt_name}] }
 *   목록  POST /main/search/boardPageJson.json  extra1, bmt_idx, cp, sortOrder(INPUTDATEDESC | INPUTDATEASC)
 *         → { result, resData{ total, cp, pageSize(20), items[{bmt_idx, bd_idx, bmt_name, bd_title_view, viewUrl}] } }
 * 제목의 `<span class="val">…</span>` 는 검색어 강조입니다. 검색어는 2자 이상이어야 합니다(포털 규칙).
 */
data class SearchBoard(val bmtIdx: Int, val name: String, val total: Int)
data class SearchOverview(val query: String, val total: Int, val boards: List<SearchBoard>)
data class SearchHit(val bmtIdx: Int, val bdIdx: Int, val boardName: String, val titleHtml: String) {
    val url: String get() = "https://hh.hana.hs.kr/main/board/$bmtIdx/$bdIdx/board_view.do"
    val plainTitle: String get() = HtmlCompat.fromHtml(titleHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}
data class SearchPage(val hits: List<SearchHit>, val total: Int)

enum class SearchOrder(val label: String, val code: String) { NEWEST("최신순", "INPUTDATEDESC"), OLDEST("오래된순", "INPUTDATEASC") }

object PortalSearchApi {
    private const val SEARCH_PAGE = "https://hh.hana.hs.kr/main/search.do"

    suspend fun overview(context: Context, query: String): SearchOverview = withContext(Dispatchers.IO) {
        val text = HanaPortalClient.get().authenticatedText(
            context, "/main/search.do", listOf("confmKey" to "extra1=$query", "extra1" to query), method = "GET",
        )
        val json = runCatching { JSONObject(text) }.getOrNull() ?: throw IllegalStateException("검색 결과를 읽지 못했습니다")
        val counts = json.optJSONObject("groupTotal")
        val list = json.optJSONArray("boardMstList")
        val boards = (0 until (list?.length() ?: 0)).mapNotNull { i ->
            val o = list?.optJSONObject(i) ?: return@mapNotNull null
            val idx = o.optInt("bmt_idx", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            SearchBoard(idx, o.optString("bmt_name").ifBlank { "게시판 $idx" }.trim(), counts?.optInt(idx.toString(), 0) ?: 0)
        }.filter { it.total > 0 }.sortedByDescending { it.total }
        SearchOverview(query, json.optInt("BD_TOT", boards.sumOf { it.total }), boards)
    }

    suspend fun page(context: Context, query: String, bmtIdx: Int, page: Int, order: SearchOrder): SearchPage = withContext(Dispatchers.IO) {
        val json = HanaPortalClient.get().authenticatedJson(
            context, "/main/search/boardPageJson.json",
            listOf("extra1" to query, "bmt_idx" to bmtIdx.toString(), "cp" to page.toString(), "sortOrder" to order.code),
            referer = SEARCH_PAGE,
            isValid = { it.has("result") },
        )
        if (json.optString("result") != "success") throw IllegalStateException(json.optString("resMsg").ifBlank { "검색하지 못했습니다" })
        val data = json.optJSONObject("resData") ?: JSONObject()
        val items = data.optJSONArray("items")
        val hits = (0 until (items?.length() ?: 0)).mapNotNull { i ->
            val o = items?.optJSONObject(i) ?: return@mapNotNull null
            val bd = o.optInt("bd_idx", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            SearchHit(o.optInt("bmt_idx", bmtIdx), bd, o.optString("bmt_name").trim(), o.optString("bd_title_view"))
        }
        SearchPage(hits, data.optInt("total", hits.size))
    }
}

/** 최근 검색어 — 이 기기에만 8개까지. */
private object RecentSearchStore {
    private const val PREFS = "portal_search"
    private const val KEY = "recent"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun get(context: Context): List<String> = prefs(context).getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    fun add(context: Context, query: String) {
        val list = (listOf(query) + get(context).filter { it != query }).take(8)
        prefs(context).edit().putString(KEY, list.joinToString("\n")).apply()
    }
    fun remove(context: Context, query: String) {
        prefs(context).edit().putString(KEY, get(context).filter { it != query }.joinToString("\n")).apply()
    }
    fun clear(context: Context) = prefs(context).edit().remove(KEY).apply()
}

/** 포털 강조 `<span class="val">검색어</span>` 를 굵은 강조색으로, 나머지 HTML 은 글자로. */
internal fun highlighted(html: String, accent: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val regex = Regex("""<span class="val">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    var last = 0
    // HTML 해석은 조각 앞뒤 공백을 지워 "경시대회 참가"가 "경시대회참가"로 붙었습니다 — 앞뒤 공백은 따로 살립니다.
    fun plain(s: String): String {
        if (s.isBlank()) return if (s.isEmpty()) "" else " "
        val lead = if (s.first().isWhitespace()) " " else ""
        val trail = if (s.last().isWhitespace()) " " else ""
        return lead + HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() + trail
    }
    regex.findAll(html).forEach { m ->
        append(plain(html.substring(last, m.range.first)))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(plain(m.groupValues[1])) }
        last = m.range.last + 1
    }
    append(plain(html.substring(last)))
}

// MARK: - 화면

/**
 * 하이하나 검색 — 모든 게시판·모든 기간의 글을 하이하나 통합검색으로 찾습니다. 위에 검색창, 아래에 게시판별 건수 칩.
 * '전체'는 게시판마다 앞의 몇 건씩, 게시판 칩을 고르면 그 게시판의 결과를 20건씩 더 받아 봅니다. 글을 누르면 앱의 게시글 화면.
 */
@Composable
fun PortalSearchScreen(onDismiss: () -> Unit, onOpenPost: (url: String, title: String) -> Unit) {
    val context = LocalContextCompat()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    // 키보드·포커스는 검색 화면 창(다이얼로그) 것이어야 내려갑니다 — 화면 본문 안에서 잡아 여기 넣어 둡니다.
    var hideKeyboard by remember { mutableStateOf({}) }
    var input by remember { mutableStateOf("") }
    var overview by remember { mutableStateOf<SearchOverview?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var board by remember { mutableStateOf<Int?>(null) }
    var order by remember { mutableStateOf(SearchOrder.NEWEST) }
    var recent by remember { mutableStateOf(RecentSearchStore.get(context)) }
    // 게시판별 받은 결과 — (게시판, 정렬) 마다 쌓아 둡니다.
    val pages = remember { mutableStateMapOf<Pair<Int, SearchOrder>, List<SearchHit>>() }
    val loadingBoards = remember { mutableStateMapOf<Int, Boolean>() }

    suspend fun loadMore(q: String, bmt: Int, o: SearchOrder) {
        val key = bmt to o
        if (loadingBoards[bmt] == true) return
        val have = pages[key].orEmpty()
        loadingBoards[bmt] = true
        try {
            val next = PortalSearchApi.page(context, q, bmt, have.size / 20 + 1, o)
            pages[key] = (have + next.hits).distinctBy { it.bdIdx }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = searchErrorText(e)
        } finally {
            loadingBoards[bmt] = false
        }
    }

    fun search(q: String) {
        val query = q.trim()
        if (query.length < 2) {
            error = "검색어는 2자 이상 입력하세요"
            return
        }
        // 결과를 가리지 않게 키보드를 내립니다.
        hideKeyboard()
        input = query
        error = null
        searching = true
        overview = null
        board = null
        pages.clear()
        RecentSearchStore.add(context, query)
        recent = RecentSearchStore.get(context)
        scope.launch {
            try {
                val o = PortalSearchApi.overview(context, query)
                overview = o
                // '전체' 보기용으로 게시판마다 첫 페이지를 받아 둡니다.
                o.boards.forEach { b -> launch { loadMore(query, b.bmtIdx, order) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = searchErrorText(e)
            } finally {
                searching = false
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // 정렬을 바꾸면 고른 게시판(또는 전체의 모든 게시판)의 첫 페이지를 그 정렬로 받습니다.
    LaunchedEffect(order, board, overview) {
        val o = overview ?: return@LaunchedEffect
        val targets = board?.let { listOf(it) } ?: o.boards.map { it.bmtIdx }
        targets.forEach { bmt -> if (pages[bmt to order] == null) launch { loadMore(o.query, bmt, order) } }
    }

    OneUiFullScreen(title = "하이하나 검색", subtitle = "모든 게시판 · 모든 기간", onDismiss = onDismiss, frosted = true) { toolbar ->
        val scheme = MaterialTheme.colorScheme
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        androidx.compose.runtime.SideEffect {
            hideKeyboard = { keyboard?.hide(); focusManager.clearFocus(force = true) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OneUi.PagePadding,
                end = OneUi.PagePadding,
                top = toolbar.calculateTopPadding() + 4.dp,
                bottom = toolbar.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "field") {
                SearchField(
                    value = input,
                    onValueChange = { input = it; if (error != null) error = null },
                    onSearch = { search(input) },
                    onClear = { input = ""; runCatching { focus.requestFocus() } },
                    modifier = Modifier.focusRequester(focus),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            val o = overview
            when {
                searching -> item(key = "loading") {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OneUiLoading(size = 18.dp, stroke = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("하이하나에서 찾는 중", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                o == null -> if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("최근 검색어", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            OneUiTextButton(text = "모두 지우기", onClick = { RecentSearchStore.clear(context); recent = emptyList() }, color = scheme.onSurfaceVariant)
                        }
                        OneUiCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                            recent.forEachIndexed { i, q ->
                                if (i > 0) OneUiDivider()
                                Row(
                                    Modifier.fillMaxWidth().clickable { search(q) }.padding(start = OneUi.RowPadding, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Box(
                                        Modifier.size(40.dp).clickable { RecentSearchStore.remove(context, q); recent = RecentSearchStore.get(context) },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Default.Close, contentDescription = "$q 지우기", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
                o.total == 0 -> item(key = "empty") {
                    Text(
                        "“${o.query}” 에 맞는 글이 없습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 16.dp),
                    )
                }
                else -> {
                    item(key = "summary") {
                        Text(
                            "“${o.query}” 검색 결과 ${o.total}건",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OneUiChip(selected = board == null, onClick = { board = null }, label = "전체 ${o.total}")
                            o.boards.forEach { b -> OneUiChip(selected = board == b.bmtIdx, onClick = { board = b.bmtIdx }, label = "${b.name} ${b.total}") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchOrder.entries.forEach { s -> OneUiChip(selected = order == s, onClick = { order = s }, label = s.label) }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    val selected = board
                    if (selected == null) {
                        // 전체 — 게시판마다 앞의 다섯 건, 그 아래 '모두 보기'.
                        o.boards.forEach { b ->
                            item(key = "sec-${b.bmtIdx}-$order") {
                                Text(
                                    "${b.name} ${b.total}건",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                                )
                                val hits = pages[b.bmtIdx to order]
                                OneUiCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (hits == null) {
                                        Box(Modifier.fillMaxWidth().padding(20.dp)) { OneUiLoading(size = 18.dp, stroke = 2.dp) }
                                    } else {
                                        hits.take(5).forEachIndexed { i, h ->
                                            if (i > 0) OneUiDivider()
                                            SearchHitRow(h, showBoard = false) { onOpenPost(h.url, h.plainTitle) }
                                        }
                                        if (b.total > 5) {
                                            OneUiDivider()
                                            Text(
                                                "${b.name} 결과 ${b.total}건 모두 보기",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.primary,
                                                modifier = Modifier.fillMaxWidth().clickable { board = b.bmtIdx }.padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    } else {
                        val b = o.boards.firstOrNull { it.bmtIdx == selected }
                        val hits = pages[selected to order].orEmpty()
                        item(key = "list-$selected-$order") {
                            OneUiCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                                if (hits.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().padding(20.dp)) { OneUiLoading(size = 18.dp, stroke = 2.dp) }
                                }
                                hits.forEachIndexed { i, h ->
                                    if (i > 0) OneUiDivider()
                                    SearchHitRow(h, showBoard = false) { onOpenPost(h.url, h.plainTitle) }
                                }
                            }
                        }
                        if (b != null && hits.isNotEmpty() && hits.size < b.total) {
                            item(key = "more-$selected-$order-${hits.size}") {
                                Spacer(Modifier.height(10.dp))
                                OneUiButton(
                                    text = if (loadingBoards[selected] == true) "불러오는 중" else "더 보기 · ${b.total - hits.size}건 남음",
                                    onClick = { scope.launch { loadMore(o.query, selected, order) } },
                                    style = OneUiButtonStyle.Neutral,
                                    enabled = loadingBoards[selected] != true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalContextCompat(): Context = androidx.compose.ui.platform.LocalContext.current

private fun searchErrorText(e: Exception): String = when (e) {
    is HanaPortalException.MissingCredentials -> "하이하나 계정을 먼저 연동해 주세요"
    else -> e.message?.lineSequence()?.firstOrNull() ?: "검색하지 못했습니다"
}

/** 투어 그림용 검색창 — 실제 검색창과 같은 모양, 입력은 받지 않습니다. */
@Composable
internal fun SearchFieldPreview(text: String) {
    SearchField(value = text, onValueChange = {}, onSearch = {}, onClear = {})
}

/** 검색창 — 리퀴드 글래스 알약 안에 돋보기 · 입력 · 지우기. 키보드의 검색 키로 찾습니다. */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    OneUiLiquidGlassBox(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        cornerRadius = 25.dp,
        strength = 0.6f,
        fill = scheme.onSurface.copy(alpha = if (scheme.isDarkTheme()) 0.10f else 0.07f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text("하이하나의 모든 글에서 찾기", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Box(Modifier.size(38.dp).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "검색어 지우기", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Box(
                Modifier.size(38.dp).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) { Text("검색", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.primary) }
        }
    }
}

private fun androidx.compose.material3.ColorScheme.isDarkTheme(): Boolean =
    (0.299f * background.red + 0.587f * background.green + 0.114f * background.blue) < 0.5f

@Composable
internal fun SearchHitRow(hit: SearchHit, showBoard: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
    ) {
        Text(
            highlighted(hit.titleHtml, scheme.primary),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showBoard) {
            Text(hit.boardName, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
