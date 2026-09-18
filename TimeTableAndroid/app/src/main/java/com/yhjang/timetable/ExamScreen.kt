package com.yhjang.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
