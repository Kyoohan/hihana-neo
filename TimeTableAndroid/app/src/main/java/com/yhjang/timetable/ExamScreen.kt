package com.yhjang.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiLoading
import com.yhjang.timetable.ui.OneUiTextButton

// MARK: - 모델

/** 파싱한 표 하나 — 첫 행을 머리글로 보존하고 나머지를 본문 행으로 둡니다. */
data class ExamTable(val header: List<String>, val rows: List<List<String>>)

/** 게시글 상세에서 뽑아낸 시험 정보 — 표들과 표를 제외한 평문 본문. */
data class ExamDocument(val tables: List<ExamTable>, val bodyText: String)

/**
 * 게시글 HTML 에서 시험 관련 표/본문을 뽑습니다.
 * 외부 라이브러리 없이 정규식으로 `<table>/<tr>/<td|th>` 만 처리하는 단순 파서입니다 —
 * 중첩 표가 있으면 안쪽 표가 바깥 표 행에 섞일 수 있지만, 포털 게시글 수준에서는 충분합니다.
 */
object ExamParser {

    private val EXAM_KEYWORDS = listOf("중간고사", "기말고사", "고사", "시험범위")

    /** 제목 또는 본문에 시험 키워드가 있는지 — 게시판 목록의 `시험` 배지 판단에 씁니다. */
    fun hasExamKeyword(text: String?): Boolean =
        !text.isNullOrBlank() && EXAM_KEYWORDS.any { text.contains(it) }

    private val TABLE_REGEX = Regex("<table[^>]*>(.*?)</table>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val ROW_REGEX = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val CELL_REGEX = Regex("<(td|th)[^>]*>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    fun parse(html: String): ExamDocument = ExamDocument(parseTables(html), extractBodyText(html))

    private fun parseTables(html: String): List<ExamTable> =
        TABLE_REGEX.findAll(html).mapNotNull { tableMatch ->
            val rows = ROW_REGEX.findAll(tableMatch.groupValues[1]).mapNotNull { rowMatch ->
                CELL_REGEX.findAll(rowMatch.groupValues[1])
                    .map { cellText(it.groupValues[2]) }
                    .toList()
                    .takeIf { it.isNotEmpty() }
            }.toList()
            if (rows.isEmpty()) null else ExamTable(rows.first(), rows.drop(1))
        }.toList()

    /** 표를 통째로 걷어낸 뒤 태그를 벗긴 평문 — 표 내용이 본문에 중복되지 않게 합니다. */
    private fun extractBodyText(html: String): String {
        val withoutTables = html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(TABLE_REGEX, " ")
        val withBreaks = withoutTables
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), "\n")
        return decodeEntities(withBreaks.replace(Regex("(?s)<[^>]+>"), ""))
            .lines()
            .map { it.replace(Regex("[ \\t\\u00a0]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun cellText(raw: String): String {
        val withBreaks = raw
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("(?i)</p>"), " ")
        return decodeEntities(withBreaks.replace(Regex("(?s)<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeEntities(input: String): String {
        val named = input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        val hex = Regex("&#x([0-9a-fA-F]+);").replace(named) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrDefault(match.value) }
                ?: match.value
        }
        return Regex("&#(\\d+);").replace(hex) { match ->
            match.groupValues[1].toIntOrNull()?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrDefault(match.value) }
                ?: match.value
        }
    }
}

// MARK: - 화면

/**
 * 시험 정보 화면 — 게시글 상세 HTML 을 받아 표/본문을 Compose 로 그립니다.
 * HTML 을 못 받으면 [onFallbackToWeb] 으로 기존 웹뷰 상세로 넘깁니다.
 */
@Composable
internal fun ExamInfoScreen(
    post: HanaBoardPost,
    onDismiss: () -> Unit,
    onFallbackToWeb: (HanaBoardPost) -> Unit,
    onOpenBrowser: (HanaBoardPost) -> Unit,
) {
    val context = LocalContext.current
    var document by remember(post.url) { mutableStateOf<ExamDocument?>(null) }
    var failed by remember(post.url) { mutableStateOf(false) }

    LaunchedEffect(post.url) {
        val html = runCatching { HanaAcademicApi.fetchBoardDetail(context, post.url) }.getOrNull()
        if (html == null) failed = true else document = ExamParser.parse(html)
    }

    // 원문을 못 받으면 조용히 기존 웹뷰 상세로 넘깁니다.
    LaunchedEffect(failed) {
        if (failed) onFallbackToWeb(post)
    }
    if (failed) return

    BackHandler(onBack = onDismiss)

    OneUiFullScreen(
        title = "시험 정보",
        onDismiss = onDismiss,
        actions = {
            OneUiTextButton(text = "브라우저로 열기", onClick = { onOpenBrowser(post) }, color = MaterialTheme.colorScheme.primary)
        },
    ) { toolbar ->
        val doc = document
        when {
            doc == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OneUiLoading(size = 28.dp, stroke = 3.dp)
            }

            doc.tables.isEmpty() && doc.bodyText.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("표시할 시험 정보가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = OneUi.PagePadding,
                    end = OneUi.PagePadding,
                    top = toolbar.calculateTopPadding() + 4.dp,
                    bottom = toolbar.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        post.title.ifEmpty { "(제목 없음)" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                doc.tables.forEachIndexed { index, table ->
                    item(key = "table-$index") { ExamTableView(table) }
                }
                if (doc.bodyText.isNotEmpty()) {
                    item(key = "body") {
                        OneUiCard(modifier = Modifier.fillMaxWidth()) {
                            Text(doc.bodyText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** 표 하나 — 폭이 넓으면 가로 스크롤. 첫 행([ExamTable.header])은 머리글로 강조합니다. */
@Composable
private fun ExamTableView(table: ExamTable) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return

    OneUiCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(OneUi.CornerSmall)),
        ) {
            if (table.header.isNotEmpty()) {
                ExamTableRow(table.header, columns, header = true)
            }
            table.rows.forEach { row -> ExamTableRow(row, columns, header = false) }
        }
    }
}

@Composable
private fun ExamTableRow(cells: List<String>, columns: Int, header: Boolean) {
    Row {
        for (column in 0 until columns) {
            ExamCell(cells.getOrNull(column).orEmpty(), header)
        }
    }
}

@Composable
private fun ExamCell(text: String, header: Boolean) {
    // 머리글은 옅은 회색 띠, 본문 칸은 카드색 — One UI 표는 강조색을 칸에 쓰지 않습니다.
    val container = if (header) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    val content = if (header) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .width(112.dp)
            .background(container)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            color = content,
        )
    }
}
