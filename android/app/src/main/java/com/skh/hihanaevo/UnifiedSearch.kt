package com.skh.hihanaevo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiFullScreen
import com.skh.hihanaevo.ui.OneUiGroup
import com.skh.hihanaevo.ui.OneUiSectionTitle
import com.skh.hihanaevo.ui.onPageVariantColor
import java.time.LocalDate
import java.time.LocalDateTime

/** 통합 검색에서 고른 결과 — 여는 일은 MainActivity 가 합니다(글은 이 화면 위에, 학사일정은 검색을 닫고). */
sealed interface SearchPick {
    data class Agenda(val item: AgendaItem) : SearchPick
    data class Board(val post: HanaBoardPost) : SearchPick
    data class Classroom(val post: ClassroomPost) : SearchPick
    data class Alim(val alim: HanaAlim) : SearchPick
    /** 하이하나 전체 글 검색 — 검색어가 있으면 그 말로 바로. */
    data class Portal(val query: String?) : SearchPick
}

/**
 * 통합 검색 — 이 기기에 받아 둔 일정·게시판(다섯 곳)·클래스룸·알리미를 한 번에 찾습니다. 맨 위 줄은 언제나 하이하나 전체 글 검색으로
 * 이어지고, 띄어 쓴 낱말은 모두 들어간 것만 보입니다(iOS 의 통합 검색과 같은 규칙).
 */
@Composable
fun UnifiedSearchScreen(
    schedule: List<HanaScheduleEntry>,
    studentGrade: Int,
    agendaExtras: List<AgendaItem>,
    classroom: ClassroomSnapshot?,
    alims: List<HanaAlim>,
    onDismiss: () -> Unit,
    onPick: (SearchPick) -> Unit,
    onMarkAlimRead: (HanaAlim) -> Unit,
) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var input by rememberSaveable { mutableStateOf("") }
    // 게시판은 다섯 곳 모두 받아 둔 목록에서 — 지금 연 게시판만이 아니라.
    var boards by remember { mutableStateOf<List<Pair<BoardCategory, HanaBoardPost>>>(emptyList()) }
    LaunchedEffect(Unit) {
        val seen = HashSet<String>()
        boards = BoardCategory.entries.flatMap { c ->
            HanaAcademicRepository.cachedBoard(context, c).orEmpty().filter { seen.add(it.url) }.map { c to it }
        }
        runCatching { focus.requestFocus() }
    }
    val terms = remember(input) { input.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    fun hit(vararg fields: String?): Boolean {
        val hay = fields.filterNotNull().joinToString("\n").lowercase()
        return terms.all { it in hay }
    }
    val agenda = remember(terms, schedule, agendaExtras, studentGrade) {
        if (terms.isEmpty()) emptyList()
        else Agenda.merge(schedule, studentGrade, agendaExtras, LocalDate.now().minusDays(30)).filter { hit(it.title, it.detail) }
    }
    val boardHits = remember(terms, boards) { if (terms.isEmpty()) emptyList() else boards.filter { (_, p) -> hit(p.title, p.writer) } }
    val classroomHits = remember(terms, classroom) {
        if (terms.isEmpty() || classroom == null) emptyList()
        else classroom.posts.filter { hit(it.title, it.text, classroom.course(it.courseId)?.name) }.sortedByDescending { it.updated }
    }
    val alimHits = remember(terms, alims) { if (terms.isEmpty()) emptyList() else alims.filter { hit(it.content, it.writer) } }
    val readIds = remember(ClassroomReadStore.revision.intValue) { ClassroomReadStore.readIds(context) }
    val now = remember { LocalDateTime.now() }
    val query = input.trim()

    OneUiFullScreen(title = "통합 검색", subtitle = "일정 · 게시판 · 클래스룸 · 알리미", onDismiss = onDismiss) { toolbar ->
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LazyColumn(
            Modifier.fillMaxSize(),
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
                    onValueChange = { input = it },
                    onSearch = { keyboard?.hide() },
                    onClear = { input = ""; runCatching { focus.requestFocus() } },
                    modifier = Modifier.focusRequester(focus),
                    placeholder = "일정·게시판·클래스룸·알리미에서 찾기",
                )
                Spacer(Modifier.height(12.dp))
            }
            // 하이하나 전체 글 검색은 언제나 — 받아 둔 글에 없을 때 이어서 찾습니다.
            item(key = "portal") {
                OneUiGroup(items = listOf(Unit)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(SearchPick.Portal(query.takeIf { it.length >= 2 })) }
                            .padding(horizontal = OneUi.RowPadding, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (query.isEmpty()) "하이하나 전체 글에서 찾기" else "하이하나 전체 글에서 ‘$query’ 찾기",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (terms.isEmpty()) {
                item(key = "hint") {
                    Text(
                        "게시판 다섯 곳, 클래스룸 공지·과제, 학사일정과 글에서 뽑은 일정, 알리미를 한 번에 찾습니다. 이 기기에 받아 둔 것에서 찾아 입력하는 대로 바로 걸러집니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onPageVariantColor(),
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 16.dp),
                    )
                }
                return@LazyColumn
            }
            if (agenda.isEmpty() && boardHits.isEmpty() && classroomHits.isEmpty() && alimHits.isEmpty()) {
                item(key = "empty") { EmptyBlock("받아 둔 것 중엔 ‘$query’ 이(가) 없습니다 — 위에서 하이하나 전체 글을 찾아보세요") }
            }
            if (agenda.isNotEmpty()) {
                item(key = "agenda") {
                    SearchSection("일정", agenda.size)
                    OneUiGroup(items = agenda.take(8)) { item -> SearchAgendaRow(item) { onPick(SearchPick.Agenda(item)) } }
                }
            }
            if (boardHits.isNotEmpty()) {
                item(key = "board") {
                    SearchSection("게시판", boardHits.size)
                    OneUiGroup(items = boardHits.take(10)) { (_, post) -> BoardRow(post, { onPick(SearchPick.Board(it)) }) }
                }
            }
            if (classroomHits.isNotEmpty() && classroom != null) {
                item(key = "classroom") {
                    SearchSection("클래스룸", classroomHits.size)
                    OneUiGroup(items = classroomHits.take(10)) { post ->
                        StreamRow(post, classroom.course(post.courseId), now, unread = post.id !in readIds, selected = false) { onPick(SearchPick.Classroom(post)) }
                    }
                }
            }
            if (alimHits.isNotEmpty()) {
                item(key = "alim") {
                    SearchSection("알리미", alimHits.size)
                    OneUiGroup(items = alimHits.take(8)) { alim ->
                        AlimRow(alim, maxLines = 3, onMarkRead = { onMarkAlimRead(alim) }) { onPick(SearchPick.Alim(alim)) }
                    }
                }
            }
            item(key = "footer") {
                Text(
                    "이 기기에 받아 둔 게시판·클래스룸·일정·알리미에서 찾은 결과입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onPageVariantColor(),
                    modifier = Modifier.padding(start = 6.dp, top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchSection(title: String, count: Int) {
    Spacer(Modifier.height(12.dp))
    OneUiSectionTitle("$title $count")
}

/** 일정 한 줄 — 검색에선 지난 일정도 섞이므로 날짜를 앞에. 학사일정은 눌러서 학사일정 목록으로. */
@Composable
private fun SearchAgendaRow(item: AgendaItem, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                add(shortDate(item.date) + (item.endDate?.let { " ~ " + shortDate(it) } ?: ""))
                item.time?.let { add(it) }
                item.detail?.takeIf { it.isNotEmpty() }?.let { add(it) }
            }
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = onPageVariantColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(item.source.label, style = MaterialTheme.typography.labelSmall, color = item.source.color, fontWeight = FontWeight.SemiBold)
    }
}
