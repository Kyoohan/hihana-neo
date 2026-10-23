package com.skh.hihanaevo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.shadow
import com.skh.hihanaevo.ui.FrostOpacity
import com.skh.hihanaevo.ui.PerformanceMode
import com.skh.hihanaevo.ui.oneUiFrostedBackdrop
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.skh.hihanaevo.ui.OneUiDialog
import com.skh.hihanaevo.ui.OneUiDialogButton
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiBadge
import com.skh.hihanaevo.ui.OneUiButton
import com.skh.hihanaevo.ui.OneUiButtonStyle
import com.skh.hihanaevo.ui.OneUiCard
import com.skh.hihanaevo.ui.OneUiDivider
import com.skh.hihanaevo.ui.OneUiFullScreen
import com.skh.hihanaevo.ui.OneUiGroup
import com.skh.hihanaevo.ui.OneUiGroupColumn
import com.skh.hihanaevo.ui.OneUiSectionTitle
import com.skh.hihanaevo.ui.OneUiTextButton
import com.skh.hihanaevo.ui.onPageVariantColor
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/*
 * 구글 클래스룸 — 하단 바의 클래스룸 탭과 글 화면.
 */

/** 클래스룸 칸이 쓰는 상태 묶음 — MainActivity 가 들고 있습니다. */
data class ClassroomUiState(
    val available: Boolean,
    val connected: Boolean,
    /** 브라우저에서 허용하러 갔고 아직 첫 내용을 못 받음. */
    val pending: Boolean = false,
    val snapshot: ClassroomSnapshot?,
    val loading: Boolean,
    val error: String?,
    /** null 이면 전체 수업. */
    val courseFilter: String?,
)

private val ClassroomGreen = Color(0xFF1E8E3E)

internal fun classroomDateTime(t: LocalDateTime): String {
    val dow = t.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.KOREAN)
    return "${t.monthValue}/${t.dayOfMonth} ($dow) %02d:%02d".format(t.hour, t.minute)
}

/** 마감 문구 — "마감 10/24 (금) 23:59", 지났으면 "마감 지남 10/24 (금)". */
private fun dueText(post: ClassroomPost, now: LocalDateTime): String? {
    val due = post.due ?: return null
    val when_ = if (post.dueAllDay) shortDate(due.toLocalDate()) else classroomDateTime(due)
    return if (!post.submitted && due.isBefore(now)) "마감 지남 · $when_" else "마감 $when_"
}

private fun kindColor(kind: ClassroomKind): Color = when (kind) {
    ClassroomKind.ANNOUNCEMENT -> Color(0xFF3A7BD5)
    ClassroomKind.WORK -> ClassroomGreen
    ClassroomKind.MATERIAL -> Color(0xFF8E6BD6)
}

private const val CLASSROOM_APP = "com.google.android.apps.classroom"

/**
 * 링크를 밖에서 엽니다. 클래스룸 글은 클래스룸 앱으로 — 앱은 과제·자료(…/details)와 수업 주소만 받고 공지 주소(/c/수업/p/글)는
 * 받지 않아 기본 브라우저가 열렸습니다. 공지는 끝에 /details 를 붙이면 앱이 그 글(수업 댓글 포함)을 엽니다.
 * 앱이 없거나 못 열면 원래 주소를 보통대로 브라우저로.
 */
internal fun openExternal(context: Context, url: String) {
    val uri = Uri.parse(url)
    fun view(target: Uri) = Intent(Intent.ACTION_VIEW, target).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (uri.host == "classroom.google.com") {
        val seg = uri.pathSegments
        val appUri = if (seg.size == 4 && seg[0] == "c" && seg[2] == "p") uri.buildUpon().appendPath("details").build() else uri
        if (runCatching { context.startActivity(view(appUri).setPackage(CLASSROOM_APP)) }.isSuccess) return
    }
    runCatching { context.startActivity(view(uri)) }
}

/** 스트림 한 쪽에 보이는 글 수 — 한 번에 수백 줄을 그리면 끊겨서 쪽으로 나눕니다. */
private const val STREAM_PAGE = 10

/**
 * 클래스룸 탭 — 연결 전엔 안내 카드, 연결 뒤엔 수업 드롭다운 → 할 일(안 낸 과제, 마감순) → 스트림(최신순, 10개씩 쪽).
 * [state] 의 스냅샷은 숨긴 수업을 이미 뺀 것입니다.
 */
@Composable
internal fun ClassroomTab(
    state: ClassroomUiState,
    onCourseFilter: (String?) -> Unit,
    onOpen: (ClassroomPost) -> Unit,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    resetTick: Int = 0,
) {
    // 한 쪽(글 10개) + 할 일뿐이라 지연 목록 대신 한 번 구성해 두고 스크롤만 — 지연 목록은 큰 묶음 카드가 화면에 다시 들어올
    // 때마다 통째로 새로 구성했습니다.
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(resetTick) { scroll.scrollTo(0) }
    var page by rememberSaveable(state.courseFilter) { mutableIntStateOf(0) }
    // 쪽을 바꾸면 '스트림' 제목으로 — 그 위치(스크롤 안 y).
    var streamTop by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val snapshot = state.snapshot
    val now = remember(snapshot) { LocalDateTime.now() }
    val posts = remember(snapshot, state.courseFilter) { snapshot?.posts.orEmpty().filter { state.courseFilter == null || it.courseId == state.courseFilter } }
    val todo = remember(posts, now) {
        posts.filter { it.kind == ClassroomKind.WORK && !it.submitted && (it.due == null || it.due.isAfter(now.minusDays(14))) }
            .sortedWith(compareBy<ClassroomPost> { it.due == null }.thenBy { it.due })
    }
    // 읽은 글 — 줄마다 저장소를 읽지 않고 한 번에.
    val readIds = remember(ClassroomReadStore.revision.intValue) { ClassroomReadStore.readIds(context) }
    val pages = (posts.size + STREAM_PAGE - 1) / STREAM_PAGE
    val current = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    // 수업 드롭다운 — 시스템 팝업(따로 뜨는 창)은 서리 배경이 창 위치를 못 맞춰, 탭 안에 서리 카드로 띄웁니다. 버튼 아래 왼쪽
    // 위치는 배치 단계에서만 읽어(스크롤로 바뀌어도 다시 구성하지 않음) 카드를 옮깁니다.
    var menuOpen by remember { mutableStateOf(false) }
    val menuAnchor = remember { mutableStateOf(IntOffset.Zero) }
    val rootInRoot = remember { mutableStateOf(Offset.Zero) }
    if (snapshot == null || !state.connected) menuOpen = false
    Box(modifier.onGloballyPositioned { rootInRoot.value = it.positionInRoot() }) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(contentPadding)) {
        when {
            !state.available -> OneUiCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("이 빌드에서는 클래스룸 연동을 지원하지 않습니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("클래스룸 연결 웹 앱 주소가 들어 있는 빌드에서만 연결할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = onPageVariantColor())
            }
            !state.connected -> ClassroomConnectCard(onConnect, onRetry, state.pending, state.loading, state.error)
            snapshot == null && state.error != null -> AcademicErrorBlock(state.error, onRetry, onConnect)
            snapshot == null -> LoadingBlock()
            else -> {
                CourseToolbar(
                    label = snapshot.courses.find { it.id == state.courseFilter }?.name ?: "전체 수업",
                    open = menuOpen,
                    onClick = { menuOpen = !menuOpen },
                    onPlaced = { bounds ->
                        val root = rootInRoot.value
                        menuAnchor.value = IntOffset((bounds.left - root.x).roundToInt(), (bounds.bottom - root.y).roundToInt())
                    },
                )
                if (state.error != null) {
                    Text("새로 받지 못해 마지막 받은 내용입니다 · ${state.error}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (todo.isNotEmpty()) {
                    OneUiSectionTitle("할 일 ${todo.size}")
                    OneUiGroup(items = todo, modifier = Modifier.padding(vertical = 4.dp)) { post ->
                        TodoRow(post, snapshot.course(post.courseId), now, selected = post.id == selectedId) { onOpen(post) }
                    }
                }
                if (posts.isEmpty()) {
                    EmptyBlock(if (snapshot.courses.isEmpty()) "보일 수업이 없습니다" else "글이 없습니다")
                } else {
                    Box(Modifier.onGloballyPositioned { streamTop = it.positionInParent().y.toInt() }) { OneUiSectionTitle("스트림") }
                    val shown = posts.subList(current * STREAM_PAGE, minOf(posts.size, (current + 1) * STREAM_PAGE))
                    OneUiGroup(items = shown, modifier = Modifier.padding(vertical = 4.dp)) { post ->
                        StreamRow(post, snapshot.course(post.courseId), now, unread = post.id !in readIds, selected = post.id == selectedId) { onOpen(post) }
                    }
                    if (pages > 1) PageBar(current, pages) { next ->
                        page = next
                        // 새 쪽은 스트림 제목부터 — 할 일이 길어도 맨 위로 올리지 않게.
                        scope.launch { scroll.animateScrollTo(streamTop) }
                    }
                }
            }
        }
    }
    if (menuOpen) {
        BackHandler { menuOpen = false }
        // 바깥을 누르면 닫힘 — 메뉴가 떠 있는 동안 뒤 목록은 눌리지 않습니다.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { menuOpen = false } })
    }
    AnimatedVisibility(
        visible = menuOpen && snapshot != null,
        modifier = Modifier.offset { menuAnchor.value + IntOffset(0, 6.dp.roundToPx()) },
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(spring(dampingRatio = 0.82f, stiffness = 520f), initialScale = 0.9f, transformOrigin = TransformOrigin(0f, 0f)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.95f, transformOrigin = TransformOrigin(0f, 0f)),
    ) {
        CourseMenu(snapshot?.courses.orEmpty(), state.courseFilter) { id ->
            onCourseFilter(id)
            menuOpen = false
        }
    }
    }
}

/** 수업 드롭다운 버튼(전체 / 수업 하나). 보일 수업 고르기는 헤더의 연필. */
@Composable
private fun CourseToolbar(label: String, open: Boolean, onClick: () -> Unit, onPlaced: (androidx.compose.ui.geometry.Rect) -> Unit) {
    val arrow by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "courseArrow")
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .onGloballyPositioned { onPlaced(it.boundsInRoot()) }
                .clip(RoundedCornerShape(OneUi.CornerMedium))
                // 사진 위 칩처럼 서리 알약 — 성능 모드는 거의 불투명한 면.
                .then(
                    if (PerformanceMode.enabled) Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    else Modifier.oneUiFrostedBackdrop(MaterialTheme.colorScheme.surface.copy(alpha = FrostOpacity.alpha)),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = "수업 고르기", tint = onPageVariantColor(), modifier = Modifier.graphicsLayer { rotationZ = arrow })
        }
    }
}

/** 드롭다운 판 — 키트 "Drop Down Menu" 처럼 서리 카드에 구분선 없이 줄만. 고른 수업은 강조색 + 체크. */
@Composable
private fun CourseMenu(courses: List<ClassroomCourse>, selected: String?, onSelect: (String?) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    OneUiCard(
        // 뒤 카드와 같은 재질이라 옅은 그림자로 띄웁니다.
        modifier = Modifier
            .widthIn(min = 220.dp, max = 300.dp)
            .heightIn(max = 440.dp)
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f)),
        shape = shape,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            CourseMenuItem("전체 수업", null, selected == null) { onSelect(null) }
            courses.forEach { c -> CourseMenuItem(c.name, c.section, selected == c.id) { onSelect(c.id) } }
        }
    }
}

@Composable
private fun CourseMenuItem(name: String, section: String?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) scheme.primary else scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            section?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.Check, contentDescription = "고름", tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** 쪽 번호 줄 — ‹ 1 2 3 4 5 › (지금 쪽 둘레로 다섯 개까지). */
@Composable
private fun PageBar(current: Int, pages: Int, onPage: (Int) -> Unit) {
    val from = (current - 2).coerceIn(0, (pages - 5).coerceAtLeast(0))
    val to = minOf(pages - 1, from + 4)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PageCell("‹", enabled = current > 0, selected = false, label = "이전 쪽") { onPage(current - 1) }
        for (i in from..to) PageCell("${i + 1}", enabled = true, selected = i == current, label = "${i + 1}쪽") { if (i != current) onPage(i) }
        PageCell("›", enabled = current < pages - 1, selected = false, label = "다음 쪽") { onPage(current + 1) }
    }
}

@Composable
private fun PageCell(text: String, enabled: Boolean, selected: Boolean, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                selected -> scheme.onPrimary
                enabled -> scheme.onSurface
                else -> scheme.onSurface.copy(alpha = 0.3f)
            },
        )
    }
}

/** 수업 편집 — 클래스룸 탭과 홈 일정에 보일 수업을 고릅니다. 고른 건 기기에 저장되고 새로 생긴 수업은 기본으로 보입니다. */
@Composable
internal fun ClassroomCourseDialog(courses: List<ClassroomCourse>, hidden: Set<String>, onSave: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(courses.map { it.id }.filter { it !in hidden }.toSet()) }
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "보일 수업",
        buttons = listOf(
            OneUiDialogButton("취소", onDismiss),
            OneUiDialogButton("저장", { onSave(courses.map { it.id }.filter { it !in shown }.toSet()) }),
        ),
    ) {
        Text("고른 수업만 클래스룸 탭과 홈 일정에 보입니다.", style = MaterialTheme.typography.bodyMedium, color = onPageVariantColor())
        Spacer(Modifier.height(8.dp))
        val all = shown.size == courses.size
        CourseCheckRow(if (all) "모두 해제" else "모두 선택", null, all, bold = true) { shown = if (all) emptySet() else courses.map { it.id }.toSet() }
        OneUiDivider()
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            courses.forEach { c ->
                CourseCheckRow(c.name, c.section, c.id in shown) { shown = if (c.id in shown) shown - c.id else shown + c.id }
            }
        }
    }
}

@Composable
private fun CourseCheckRow(name: String, section: String?, checked: Boolean, bold: Boolean = false, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(OneUi.CornerSmall)).clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            section?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = onPageVariantColor()) }
        }
    }
}

@Composable
private fun ClassroomConnectCard(onConnect: () -> Unit, onRetry: () -> Unit, pending: Boolean, loading: Boolean, error: String?) {
    OneUiCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_school), contentDescription = null, tint = ClassroomGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (pending) "허용을 마치면 채워집니다" else "Google 클래스룸 연결", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (pending) "브라우저에서 학교 계정을 고르고 '허용'까지 누른 뒤 앱으로 돌아오면 공지와 과제가 보입니다. 이미 했다면 '확인'을 눌러 주세요."
            else "브라우저에서 학교 Google 계정으로 '허용'을 한 번 누르면 수업 공지와 과제, 제출 상태를 여기서 보고 마감과 공지 속 일정이 홈 일정에 함께 올라갑니다. " +
                "읽기만 하며, 내용은 암호화되어 오고 이 기기에만 저장됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = onPageVariantColor(),
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(14.dp))
        if (pending) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OneUiButton(text = if (loading) "확인 중" else "확인", onClick = onRetry, enabled = !loading, modifier = Modifier.weight(1f))
                OneUiButton(text = "브라우저 다시 열기", onClick = onConnect, style = OneUiButtonStyle.Neutral, modifier = Modifier.weight(1f))
            }
        } else {
            OneUiButton(text = "학교 계정으로 연결", onClick = onConnect)
        }
    }
}

@Composable
private fun rowBackground(selected: Boolean) =
    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent

@Composable
private fun TodoRow(post: ClassroomPost, course: ClassroomCourse?, now: LocalDateTime, selected: Boolean, onClick: () -> Unit) {
    val overdue = post.due != null && post.due.isBefore(now)
    Row(
        Modifier.fillMaxWidth().background(rowBackground(selected)).clickable(onClick = onClick).padding(horizontal = OneUi.RowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(post.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(course?.name, dueText(post, now) ?: "마감 없음").joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelMedium, color = if (overdue) MaterialTheme.colorScheme.error else onPageVariantColor())
        }
        post.due?.takeIf { !overdue }?.let { due ->
            val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
            Spacer(Modifier.width(8.dp))
            OneUiBadge(
                if (days <= 0) "오늘" else "D-$days",
                container = if (days <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                content = if (days <= 1) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun StreamRow(post: ClassroomPost, course: ClassroomCourse?, now: LocalDateTime, unread: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(rowBackground(selected)).clickable(onClick = onClick).padding(horizontal = OneUi.RowPadding, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(post.kind.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = kindColor(post.kind))
            Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull(course?.name, classroomDateTime(post.updated)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = onPageVariantColor(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (unread) Text("N", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(4.dp))
        Text(post.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // 공지는 첫 줄이 제목이므로 나머지만, 과제·자료는 설명을 두 줄까지.
        val body = if (post.kind == ClassroomKind.ANNOUNCEMENT) post.text.lineSequence().drop(1).joinToString(" ").trim() else post.text.replace('\n', ' ').trim()
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = onPageVariantColor(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (post.kind == ClassroomKind.WORK) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.submitted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ClassroomGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (post.late) "늦게 제출함" else "제출함", style = MaterialTheme.typography.labelSmall, color = ClassroomGreen)
                    post.grade?.let { g ->
                        Spacer(Modifier.width(8.dp))
                        Text("${trimNumber(g)}${post.maxPoints?.let { "/" + trimNumber(it) } ?: ""}점", style = MaterialTheme.typography.labelSmall, color = onPageVariantColor())
                    }
                } else {
                    val overdue = post.due != null && post.due.isBefore(now)
                    Text(dueText(post, now) ?: "마감 없음", style = MaterialTheme.typography.labelSmall, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (post.attachments.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("첨부 ${post.attachments.size}", style = MaterialTheme.typography.labelSmall, color = onPageVariantColor())
        }
    }
}

private fun trimNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

// MARK: - 글 화면

/** 폰 전체 화면 — 큰 화면은 [ClassroomPostPane] 을 오른쪽 칸에. */
@Composable
fun ClassroomPostScreen(post: ClassroomPost, course: ClassroomCourse?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onDismiss)
    OneUiFullScreen(
        title = post.kind.label,
        subtitle = course?.name,
        onDismiss = onDismiss,
        actions = {
            post.link?.let { OneUiTextButton(text = "클래스룸에서 열기", onClick = { openExternal(context, it) }, color = MaterialTheme.colorScheme.primary) }
        },
    ) { toolbar -> ClassroomPostPane(post, course, toolbar, underToolbar = true) }
}

@Composable
fun ClassroomPostPane(
    post: ClassroomPost,
    course: ClassroomCourse?,
    padding: PaddingValues = PaddingValues(0.dp),
    /**
     * 폰 전체 화면([ClassroomPostScreen]) 안인지 — 툴바가 이미 종류·수업 이름과 '클래스룸에서 열기'를 보여 주므로 제목 아래 줄엔
     * 올린 시각만, 글 아래 열기 버튼은 뺍니다. 큰 화면 오른쪽 칸(툴바 없음)은 다 보여 줍니다.
     */
    underToolbar: Boolean = false,
) {
    val context = LocalContext.current
    val now = remember { LocalDateTime.now() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = OneUi.PagePadding, vertical = 8.dp)) {
        // 공지는 클래스룸에 제목이 없습니다 — 목록·홈에선 첫 줄을 제목 자리에 두지만, 글 화면에선 첫 줄을 제목처럼 크게 빼지 않고
        // 글 전체를 본문으로 보여 줍니다(조 편성처럼 여러 줄이 같은 무게인 글에서 첫 줄만 제목처럼 보였습니다).
        val announcement = post.kind == ClassroomKind.ANNOUNCEMENT
        if (!announcement) {
            Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(6.dp))
        }
        Text(
            (if (underToolbar) listOf("올림 " + classroomDateTime(post.created)) else listOfNotNull(course?.name, post.kind.label, "올림 " + classroomDateTime(post.created))).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium, color = onPageVariantColor(), modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (post.kind == ClassroomKind.WORK) {
            Spacer(Modifier.height(12.dp))
            OneUiGroupColumn {
                val overdue = post.due != null && !post.submitted && post.due.isBefore(now)
                InfoRow("마감", dueText(post, now)?.removePrefix("마감 ")?.removePrefix("지남 · ")?.let { if (overdue) "지남 · $it" else it } ?: "없음", if (overdue) MaterialTheme.colorScheme.error else null)
                OneUiDivider()
                InfoRow(
                    "제출",
                    when {
                        post.submitted && post.late -> "늦게 제출함"
                        post.submitted -> "제출함"
                        else -> "아직 제출하지 않음"
                    },
                    if (post.submitted) ClassroomGreen else null,
                )
                if (post.maxPoints != null || post.grade != null) {
                    OneUiDivider()
                    InfoRow("점수", post.grade?.let { g -> "${trimNumber(g)}${post.maxPoints?.let { "/" + trimNumber(it) } ?: ""}점" } ?: "${trimNumber(post.maxPoints ?: 0.0)}점 만점 · 아직 채점 전", null)
                }
            }
        }
        val body = post.text
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OneUiCard(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer { LinkedText(body) }
            }
        }
        if (post.attachments.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OneUiSectionTitle("첨부")
            OneUiGroup(items = post.attachments) { a ->
                Row(Modifier.fillMaxWidth().clickable { openExternal(context, a.url) }.padding(horizontal = OneUi.RowPadding, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(a.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        post.link?.takeIf { !underToolbar }?.let { link ->
            Spacer(Modifier.height(16.dp))
            OneUiButton(text = "클래스룸에서 열기", onClick = { openExternal(context, link) }, style = OneUiButtonStyle.Neutral, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = OneUi.RowPadding, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = onPageVariantColor(), modifier = Modifier.width(56.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color ?: LocalContentColor.current, fontWeight = if (color != null) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** 본문 속 주소를 누를 수 있게 — 클래스룸 공지는 링크를 본문에 바로 적는 일이 많습니다. */
@Composable
private fun LinkedText(text: String) {
    val link = MaterialTheme.colorScheme.primary
    val annotated = remember(text, link) {
        buildAnnotatedString {
            var last = 0
            Regex("https?://[^\\s<>\"']+").findAll(text).forEach { m ->
                append(text.substring(last, m.range.first))
                withLink(LinkAnnotation.Url(m.value, TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))) { append(m.value) }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium)
}

// MARK: - 검색

/**
 * 클래스룸 검색 — 받아 둔 글(숨기지 않은 수업)의 제목·본문·수업 이름에서 찾습니다. 기기 안에서만 찾아 입력하는 대로 바로 걸러지고,
 * 띄어 쓴 낱말은 모두 들어간 글만. 최신순으로 50건까지.
 */
@Composable
fun ClassroomSearchScreen(snapshot: ClassroomSnapshot?, onDismiss: () -> Unit, onOpen: (ClassroomPost) -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var input by rememberSaveable { mutableStateOf("") }
    val terms = remember(input) { input.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val results = remember(snapshot, terms) {
        if (snapshot == null || terms.isEmpty()) emptyList()
        else snapshot.posts.filter { post ->
            val hay = (post.title + "\n" + post.text + "\n" + snapshot.course(post.courseId)?.name.orEmpty()).lowercase()
            terms.all { it in hay }
        }.take(50)
    }
    val readIds = remember(ClassroomReadStore.revision.intValue) { ClassroomReadStore.readIds(context) }
    val now = remember(snapshot) { LocalDateTime.now() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OneUiFullScreen(title = "클래스룸 검색", subtitle = "받아 둔 공지·과제·자료", onDismiss = onDismiss) { toolbar ->
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = OneUi.PagePadding, end = OneUi.PagePadding, top = toolbar.calculateTopPadding() + 4.dp, bottom = toolbar.calculateBottomPadding() + 32.dp),
        ) {
            SearchField(
                value = input,
                onValueChange = { input = it },
                onSearch = { keyboard?.hide() },
                onClear = { input = ""; runCatching { focus.requestFocus() } },
                modifier = Modifier.focusRequester(focus),
                placeholder = "제목·본문·수업 이름으로 찾기",
            )
            Spacer(Modifier.height(12.dp))
            when {
                snapshot == null -> EmptyBlock("클래스룸을 연결하면 받아 둔 글에서 찾을 수 있습니다")
                terms.isEmpty() -> Unit
                results.isEmpty() -> EmptyBlock("찾는 글이 없습니다")
                else -> {
                    Text("${results.size}${if (results.size == 50) "건 이상" else "건"}", style = MaterialTheme.typography.labelLarge, color = onPageVariantColor(), modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                    OneUiGroup(items = results) { post ->
                        StreamRow(post, snapshot.course(post.courseId), now, unread = post.id !in readIds, selected = false) { onOpen(post) }
                    }
                }
            }
        }
    }
}
