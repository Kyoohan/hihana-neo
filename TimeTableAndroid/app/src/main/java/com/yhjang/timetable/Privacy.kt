package com.yhjang.timetable

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiFullScreen
import kotlinx.coroutines.delay

/**
 * 개인정보 보호 — 정보 화면에서 엽니다. 제품 소개 페이지처럼 큰 헤드라인과 차분한 카드, 색은 섹션마다 작은 포인트로만 씁니다.
 * 적은 내용은 코드·공개 문서·실제 연결로 확인한 사실만 씁니다:
 * 계정 저장([HanaCredentialStore]·[MidnightSessionStore] 의 EncryptedSharedPreferences, AES-256-GCM, 열쇠는 안드로이드 키스토어 —
 * StrongBox 를 요청하지 않으므로 TEE, 갤럭시는 삼성 녹스 기반), 백업 제외(res/xml/backup_rules·data_extraction_rules),
 * 통신하는 서버([PRIVACY_HOSTS]), 광고·분석 SDK 없음(app/build.gradle.kts), 학사시스템 연결 TLS 1.3 · AES-256-GCM,
 * AES-256 을 쓰는 곳([AES_USERS] — CNSSP-15, 각 회사 보안 문서, 은행은 인터넷 뱅킹 접속으로 확인, 2026년 9월).
 * 코드·서버가 바뀌면 이 문구도 같이 고칩니다.
 */

// 포인트 색 — 섹션마다 하나씩, 작게.
private val PvYellow = Color(0xFFF5B53D)
private val PvGreen = Color(0xFF3DBE8B)
private val PvOrange = Color(0xFFF07A45)
private val PvBlue = Color(0xFF4C7BF2)
private val PvPink = Color(0xFFEC5881)
private val PvViolet = Color(0xFF8B7BFF)

/** 앱이 스스로 접속하는 곳 — 이름, 쓰는 곳, 보내는 정보, 점 색. */
private class PrivacyHost(val name: String, val use: String, val sends: String, val color: Color)

private val PRIVACY_HOSTS = listOf(
    PrivacyHost("하나고 학사시스템", "로그인 · 시간표 · 신청 · 게시판", "아이디·비밀번호\nHTTPS", PvBlue),
    PrivacyHost("심야면학 사이트", "심야면학 로그인 · 신청", "이름·비밀번호\nHTTPS", PvViolet),
    PrivacyHost("AI 요약 서버", "Cloudflare · 게시판 글 요약", "글 제목·본문", PvPink),
    PrivacyHost("나이스 교육정보", "급식 칼로리·영양·원산지", "학교 코드·날짜", PvOrange),
    PrivacyHost("하나고 홈페이지", "급식 메뉴", "날짜", PvYellow),
    PrivacyHost("GitHub", "업데이트 확인·다운로드", "보내는 정보 없음", PvGreen),
)

/** 같은 AES-256 을 쓰는 곳 — 이름과 쓰는 곳. 두 줄로 반대 방향으로 흘러갑니다. */
private val AES_USERS = listOf(
    listOf(
        "NSA" to "최고 기밀 승인", "Apple" to "iPhone 데이터 보호", "Android" to "기기 파일 암호화", "Samsung Knox" to "파일 암호화",
        "Google Cloud" to "저장 데이터", "Amazon S3" to "저장 데이터", "Dropbox" to "저장 파일",
    ),
    listOf(
        "WhatsApp" to "메시지 암호화", "Signal" to "메시지 암호화", "Zoom" to "회의 암호화", "1Password" to "비밀번호 금고",
        "KB국민은행" to "인터넷 뱅킹", "신한은행" to "인터넷 뱅킹", "우리은행" to "인터넷 뱅킹", "하나은행" to "인터넷 뱅킹", "토스뱅크" to "인터넷 뱅킹",
    ),
)

@Composable
internal fun PrivacyScreen(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    OneUiFullScreen(title = "개인정보 보호", onDismiss = onDismiss) { toolbar ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OneUi.PagePadding + 4.dp,
                end = OneUi.PagePadding + 4.dp,
                top = toolbar.calculateTopPadding() + 12.dp,
                bottom = toolbar.calculateBottomPadding() + 56.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            item(key = "hero") { Reveal { Hero() } }
            item(key = "secret") { Reveal { TopSecretSection() } }
            item(key = "tee") { Reveal { TeeSection() } }
            item(key = "aes") { Reveal { KeySpaceSection() } }
            item(key = "demo") { Reveal { EncryptionDemo() } }
            item(key = "https") { Reveal { HttpsSection() } }
            item(key = "flow") { Reveal { FlowSection() } }
            item(key = "hosts") { Reveal { HostsSection() } }
            item(key = "cannot") { Reveal { CannotSection() } }
            item(key = "verify") { Reveal { VerifySection() } }
            item(key = "erase") { Reveal { EraseSection() } }
        }
    }
}

/** 처음 보일 때 아래에서 살짝 떠오르며 나타납니다. */
@Composable
private fun Reveal(delayMs: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        shown = true
    }
    val t by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow), label = "reveal")
    Box(Modifier.graphicsLayer { alpha = t; translationY = (1f - t) * 48f }) { content() }
}

/** 섹션 머리 — 작은 머리글(포인트 색), 큰 헤드라인, 설명. */
@Composable
private fun Head(eyebrow: String, color: Color?, title: String, lead: String? = null) {
    if (eyebrow.isNotEmpty()) {
        Text(
            eyebrow,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 33.sp), fontWeight = FontWeight.ExtraBold)
    if (lead != null) {
        Spacer(Modifier.height(12.dp))
        Text(lead, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 차분한 카드 — 카드 색에 아주 옅은 테두리. */
@Composable
private fun Plain(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
private fun Fine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(top = 12.dp, start = 2.dp),
    )
}

/** 포인트 색 아이콘 칸 — 옅게 물든 둥근 사각형 안에 색 아이콘. */
@Composable
private fun TintIcon(icon: Painter, color: Color, box: androidx.compose.ui.unit.Dp = 38.dp) {
    Box(Modifier.size(box).clip(RoundedCornerShape(box * 0.32f)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(box * 0.56f))
    }
}

// MARK: - 1. 첫 화면

private class SecuritySpec(val name: String, val caption: String, val icon: Int, val from: Color, val to: Color)

/** 네 가지 보안 기술 — 첫 화면의 차분한 카드와 투어의 원색 타일이 같은 목록을 씁니다. */
private val SECURITY_SPECS = listOf(
    SecuritySpec("AES-256", "저장할 때 암호화", R.drawable.ic_pv_key, Color(0xFFC98A0C), Color(0xFFFDBE4E)),
    SecuritySpec("HTTPS", "보낼 때 암호화", R.drawable.ic_pv_globe_lock, Color(0xFF1E9E6A), Color(0xFF2FB8A6)),
    SecuritySpec("TLS 1.3", "최신 보안 연결", R.drawable.ic_pv_handshake, Color(0xFFE65B17), Color(0xFFEC5881)),
    SecuritySpec("TEE / Knox", "열쇠는 기기 보안 영역에", R.drawable.ic_pv_knox, Color(0xFF1428A0), Color(0xFF3E6BE0)),
)

private val SPEC_POINTS = listOf(PvYellow, PvGreen, PvOrange, PvBlue)

@Composable
private fun Hero() {
    Column {
        Text("개인정보 보호", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text(
            "비밀번호는\n이 폰과 학교만 압니다.",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 33.sp, lineHeight = 41.sp),
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "하이하나 Neo는 학교 계정으로 로그인하는 앱입니다. 그래서 계정 정보를 어떻게 지키는지, 어디로 무엇을 보내는지 숨김없이 보여 드립니다.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SECURITY_SPECS.indices.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { i ->
                        val spec = SECURITY_SPECS[i]
                        Cell(delayMs = i * 90) {
                            Plain(padding = PaddingValues(16.dp)) {
                                TintIcon(painterResource(spec.icon), SPEC_POINTS[i])
                                Spacer(Modifier.height(14.dp))
                                Text(spec.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                Text(spec.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 한 줄에 둘씩 — 칸 폭을 나눠 가지면서 차례로 떠오릅니다. */
@Composable
private fun RowScope.Cell(delayMs: Int, content: @Composable () -> Unit) {
    Box(Modifier.weight(1f)) { Reveal(delayMs) { content() } }
}

/** 투어의 개인정보 장 — 원색 그라데이션 타일 네 칸. */
@Composable
internal fun SecuritySpecTiles() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SECURITY_SPECS.indices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { i ->
                    val spec = SECURITY_SPECS[i]
                    Cell(delayMs = i * 80) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = spec.from, spotColor = spec.from)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Brush.linearGradient(listOf(spec.from, spec.to)))
                                .padding(16.dp),
                        ) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                                Icon(painterResource(spec.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(spec.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                            Text(spec.caption, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 2. 최고 기밀급

@Composable
private fun TopSecretSection() {
    Column {
        Row(
            Modifier.clip(CircleShape).border(1.dp, PvYellow.copy(alpha = 0.45f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_pv_star), contentDescription = null, tint = PvYellow, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text("TOP SECRET 승인", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp), fontWeight = FontWeight.ExtraBold, color = PvYellow)
        }
        Spacer(Modifier.height(4.dp))
        Head(
            "",
            null,
            "미국 정부가\n최고 기밀에 쓰는 암호화.",
            "비밀번호를 잠그는 AES-256은 미국 국가안보시스템위원회(CNSSP-15)가 최고 기밀(TOP SECRET) 정보를 보호하도록 승인한 방식입니다.",
        )
        Spacer(Modifier.height(26.dp))
        Text("같은 AES-256을 쓰는 곳", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        Spacer(Modifier.height(12.dp))
        Belt(AES_USERS[0], durationMs = 38_000, reverse = false)
        Spacer(Modifier.height(10.dp))
        Belt(AES_USERS[1], durationMs = 44_000, reverse = true)
        Fine("2026년 9월 기준")
    }
}

/**
 * 이름 카드가 끝없이 흘러가는 띠 — 같은 목록을 두 번 이어 붙이고 절반만큼 움직이면 이음새 없이 돕니다.
 * 양 끝은 부드럽게 사라집니다.
 */
@Composable
private fun Belt(items: List<Pair<String, String>>, durationMs: Int, reverse: Boolean) {
    var half by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "belt")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "beltPhase")
    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.1f to Color.Black, 0.9f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Row(
            Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .onSizeChanged { half = it.width / 2 }
                .graphicsLayer { translationX = -(if (reverse) 1f - phase else phase) * half },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) {
                items.forEach { (name, use) ->
                    Column(
                        Modifier
                            .widthIn(min = 112.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(use, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// MARK: - 2-1. TEE

/**
 * TEE — 열쇠가 있는 곳. 앱(일반 영역)은 잠그고 푸는 일을 부탁만 하고, 열쇠는 하드웨어로 분리된 보안 영역 밖으로 나오지 않습니다.
 * 근거: 안드로이드 키스토어의 하드웨어 보관 키는 운영체제가 뚫려도 꺼낼 수 없음(Android 문서), 지문 정보는 TEE 안(CDD).
 */
@Composable
private fun TeeSection() {
    val scheme = MaterialTheme.colorScheme
    val flow = rememberInfiniteTransition(label = "tee")
    val phase by flow.animateFloat(0f, 1f, infiniteRepeatable(tween(1_500, easing = LinearEasing)), label = "teePhase")
    Column {
        Head(
            "TEE / Knox",
            PvBlue,
            "열쇠는\n금고 안의 금고에.",
            "TEE(Trusted Execution Environment)는 프로세서 안에 하드웨어로 따로 떼어 놓은 보안 영역입니다. 안드로이드와 앱이 돌아가는 곳과 분리된 채, 자체 보안 운영체제로 움직입니다.",
        )
        Spacer(Modifier.height(20.dp))
        Plain(padding = PaddingValues(16.dp)) {
            // 일반 영역 — 안드로이드와 앱
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.onSurface.copy(alpha = 0.05f)).padding(14.dp)) {
                Text("일반 영역 · 안드로이드와 앱", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF1F5A3C)), contentAlignment = Alignment.Center) {
                        Text("H", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFFEAF6EE))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("하이하나 Neo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("열쇠를 가지고 있지 않습니다", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            // 부탁은 내려가고 결과만 올라옵니다.
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.width(20.dp).height(40.dp)) {
                        val x = size.width / 2
                        drawLine(PvBlue.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)))
                        drawCircle(PvBlue, radius = 7f, center = Offset(x, phase * size.height))
                    }
                    Text("잠가 줘 · 풀어 줘", style = MaterialTheme.typography.labelSmall, color = PvBlue, fontWeight = FontWeight.Bold)
                }
                Text(
                    "하드웨어로 분리",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).border(1.dp, scheme.onSurface.copy(alpha = 0.15f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.width(20.dp).height(40.dp)) {
                        val x = size.width / 2
                        drawLine(PvGreen.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)))
                        drawCircle(PvGreen, radius = 7f, center = Offset(x, (1f - phase) * size.height))
                    }
                    Text("결과만", style = MaterialTheme.typography.labelSmall, color = PvGreen, fontWeight = FontWeight.Bold)
                }
            }
            // 보안 영역 — 열쇠
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PvBlue.copy(alpha = 0.12f))
                    .border(1.dp, PvBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text("보안 영역 · TEE", style = MaterialTheme.typography.labelMedium, color = PvBlue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TintIcon(painterResource(R.drawable.ic_pv_key), PvYellow, box = 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("AES-256 열쇠", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("이 폰에서 만들어져 이 안에만 있습니다", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        TeeFact(R.drawable.ic_pv_knox, PvBlue, "안드로이드가 뚫려도", "앱이나 운영체제가 악성 코드에 뚫리더라도, 열쇠 자체는 TEE 밖으로 꺼낼 수 없습니다.")
        Spacer(Modifier.height(18.dp))
        TeeFact(R.drawable.ic_pv_phone, PvViolet, "이 폰에 묶인 열쇠", "저장된 값을 다른 폰으로 복사해도, 그 폰에는 열쇠가 없어 풀 수 없습니다. 백업과 기기 이전에서도 빠집니다.")
        Spacer(Modifier.height(18.dp))
        TeeFact(R.drawable.ic_pv_star, PvYellow, "지문·결제와 같은 금고", "지문 정보와 모바일 결제도 같은 보안 영역이 지킵니다. 갤럭시에서는 삼성 녹스가 이 영역을 바탕으로 동작합니다.")
    }
}

@Composable
private fun TeeFact(icon: Int, color: Color, title: String, body: String) {
    Row {
        TintIcon(painterResource(icon), color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// MARK: - 3. 2^256

@Composable
private fun KeySpaceSection() {
    val marquee = rememberScrollState()
    LaunchedEffect(Unit) {
        while (true) {
            marquee.animateScrollTo(marquee.maxValue, tween(14_000, easing = LinearEasing))
            delay(900)
            marquee.scrollTo(0)
        }
    }
    Column {
        Head("AES-256", PvYellow, "추측으로는\n열 수 없습니다.")
        Spacer(Modifier.height(16.dp))
        Text(
            buildAnnotatedString {
                append("2")
                withStyle(SpanStyle(fontSize = 42.sp, baselineShift = BaselineShift(0.55f))) { append("256") }
            },
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 88.sp,
                brush = Brush.linearGradient(listOf(PvYellow, PvOrange)),
            ),
            fontWeight = FontWeight.Black,
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(marquee, enabled = false)) {
            Text(
                "115,792,089,237,316,195,423,570,985,008,687,907,853,269,984,665,640,564,039,457,584,007,913,129,639,936 가지 열쇠",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                softWrap = false,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("3.7×10⁵¹년", "1초에 100경 번 시도하는 컴퓨터로 모든 열쇠를 시도하는 시간", Modifier.weight(1f))
            Stat("10⁴¹배", "지금까지 흐른 우주의 나이(약 138억 년)보다 긴 정도", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(big: String, small: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(big, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(small, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: - 4. 저장되는 모습

@Composable
private fun EncryptionDemo() {
    var encrypted by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf("hana2026!") }
    // 암호문 예시 — 실제 저장 값처럼 무작위 바이트를 Base64 로 (보여 주기용이며 실제 비밀번호와 무관).
    val cipher = remember {
        android.util.Base64.encodeToString(ByteArray(36).also { java.util.Random(2026).nextBytes(it) }, android.util.Base64.NO_WRAP)
    }
    LaunchedEffect(encrypted) {
        val target = if (encrypted) cipher else "hana2026!"
        val pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
        for (step in 1..12) {
            shown = String(CharArray(target.length) { i -> if (i < target.length * step / 12) target[i] else pool.random() })
            delay(45)
        }
        shown = target
    }
    Column {
        Head("직접 눌러 보세요", null, "저장되는 모습.", "비밀번호는 그대로 저장되지 않습니다. 이 폰 안에 실제로 남는 형태를 확인해 보세요.")
        Spacer(Modifier.height(18.dp))
        Plain(Modifier.heightIn(min = 96.dp), padding = PaddingValues(16.dp)) {
            Text(
                if (encrypted) "이 폰에 저장된 값 (AES-256)" else "입력한 비밀번호",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                shown,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = if (encrypted) 14.sp else 22.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (encrypted) PvYellow else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (encrypted) "원래대로" else "암호화하기",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface)
                .clickable { encrypted = !encrypted }
                .padding(horizontal = 20.dp, vertical = 11.dp),
        )
    }
}

// MARK: - 5. HTTPS

@Composable
private fun HttpsSection() {
    var secure by remember { mutableStateOf(true) }
    val flow = rememberInfiniteTransition(label = "packets")
    val phase by flow.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "packetPhase")
    val line = if (secure) PvGreen else PvOrange
    val scheme = MaterialTheme.colorScheme
    Column {
        Head(
            "HTTPS · TLS 1.3",
            PvGreen,
            "학교 와이파이에서도\n안전하게.",
            "로그인할 때 비밀번호는 암호화된 연결로 학사시스템에 곧장 갑니다. 같은 와이파이의 누군가가 엿봐도 알아볼 수 없습니다.",
        )
        Spacer(Modifier.height(20.dp))
        Plain {
            Row(Modifier.clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.06f)).padding(4.dp)) {
                listOf(true to "HTTPS (지금)", false to "암호화가 없다면").forEach { (value, label) ->
                    val on = secure == value
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (on) scheme.surface else scheme.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).background(if (on) scheme.onSurface else Color.Transparent).clickable { secure = value }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Endpoint(painterResource(R.drawable.ic_pv_phone), "내 폰")
                Canvas(Modifier.weight(1f).height(24.dp).padding(horizontal = 8.dp)) {
                    val y = size.height / 2
                    drawLine(line.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)))
                    repeat(3) { i ->
                        val x = ((phase + i / 3f) % 1f) * size.width
                        drawCircle(line, radius = 7f, center = Offset(x, y))
                    }
                }
                Endpoint(painterResource(R.drawable.ic_school), "학사시스템")
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.onSurface.copy(alpha = 0.05f)).padding(14.dp)) {
                Text("같은 와이파이의 누군가에게 보이는 것", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                AnimatedContent(secure, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "sniff") { s ->
                    Text(
                        if (s) "17 03 03 01 2a e9 4c 8b f0 3d 91 7a c2 5e 08 b6 d4 13 …" else "mem_id=hana2026&mem_pwd=hana2026!",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = if (s) scheme.onSurface else PvOrange,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("TLS 1.3", "AES-256-GCM", "인증서 확인").forEach { tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).border(1.dp, scheme.onSurface.copy(alpha = 0.1f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Fine("학사시스템과의 연결은 TLS 1.3 · AES-256-GCM으로 암호화됩니다(2026년 9월 확인). 인증서로 진짜 hh.hana.hs.kr인지도 확인합니다.")
    }
}

@Composable
private fun Endpoint(icon: Painter, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: - 6. 개인정보가 가는 길

@Composable
private fun FlowSection() {
    val flow = rememberInfiniteTransition(label = "flow")
    val phase by flow.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "flowPhase")
    Column {
        Head("개인정보가 가는 길", PvOrange, "비밀번호는\n한 길로만 갑니다.")
        Spacer(Modifier.height(20.dp))
        Plain {
            FlowNode(painterResource(R.drawable.ic_pv_key), "입력한 아이디·비밀번호", "로그인 화면에서", MaterialTheme.colorScheme.onSurfaceVariant)
            FlowWire(PvBlue, phase)
            FlowNode(painterResource(R.drawable.ic_pv_phone), "이 폰", "AES-256으로 잠가 저장 · 열쇠는 TEE 안", PvBlue, highlight = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    FlowWire(PvGreen, phase)
                    FlowTag("HTTPS · TLS 1.3\n아이디·비밀번호", PvGreen)
                    FlowNode(painterResource(R.drawable.ic_school), "학사시스템", "hh.hana.hs.kr", PvGreen)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    FlowWire(PvPink, phase)
                    FlowTag("게시판 글\n제목·본문만", PvPink)
                    FlowNode(painterResource(R.drawable.ic_pv_cloud), "AI 요약 서버", "Cloudflare", PvPink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "✕ 아이디·비밀번호 전송 없음",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PvOrange,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .drawWithContent {
                                drawContent()
                                drawRoundRect(
                                    PvOrange.copy(alpha = 0.7f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                                )
                            }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                    )
                }
            }
        }
        Fine("AI 요약 서버(Cloudflare)에는 게시판 글만 갑니다. 계정 정보를 다른 곳으로 보내는 코드는 앱 어디에도 없으며, 앱의 전체 코드가 GitHub에 공개되어 있습니다.")
    }
}

@Composable
private fun FlowNode(icon: Painter, title: String, caption: String, tint: Color, highlight: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 세로 점선과 그 위로 흘러가는 점 — 정보가 움직이는 방향. */
@Composable
private fun FlowWire(color: Color, phase: Float) {
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        val x = size.width / 2
        drawLine(color.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        repeat(2) { i ->
            val y = ((phase + i / 2f) % 1f) * size.height
            drawCircle(color, radius = 7f, center = Offset(x, y))
        }
    }
}

@Composable
private fun FlowTag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp).clip(CircleShape).border(1.dp, color.copy(alpha = 0.4f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// MARK: - 7. 앱이 접속하는 곳

@Composable
private fun HostsSection() {
    Column {
        Head("앱이 접속하는 곳", null, "여섯 곳, 그게 전부.")
        Spacer(Modifier.height(20.dp))
        Plain(padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            PRIVACY_HOSTS.forEachIndexed { i, host ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)))
                Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(host.color))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(host.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(host.use, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(host.sends, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
            }
        }
        Fine("게시글을 열면 글에 들어 있는 사진·첨부파일은 그 글이 가리키는 곳(대개 학사시스템)에서 받습니다.")
    }
}

// MARK: - 8. 개발자가 할 수 없는 것

private class Promise(val title: String, val body: String, val color: Color, val icon: Int)

private val PROMISES = listOf(
    Promise("비밀번호를 볼 수 없습니다", "비밀번호는 학사시스템에만 곧장 갑니다. 개발자에게 전달될 길이 없습니다.", PvViolet, R.drawable.ic_tour_eye_off),
    Promise("요약 서버엔 게시판 글만", "AI 요약 서버(Cloudflare)는 게시판 글의 제목·본문·이미지 주소만 받습니다. 요약을 끄면 이것도 보내지 않습니다.", PvPink, R.drawable.ic_pv_cloud),
    Promise("원격 조작 기능 없음", "서버에서 명령이나 코드를 받아 실행하지 않습니다. 앱이 하는 일을 바꾸려면 새 버전을 내야 합니다.", PvBlue, R.drawable.ic_tour_swap),
    Promise("사용 기록 수집 없음", "광고·사용 분석·오류 수집 도구를 넣지 않았습니다. 무엇을 봤는지 같은 기록이 어디로도 가지 않습니다.", PvOrange, R.drawable.ic_query_stats),
)

@Composable
private fun CannotSection() {
    Column {
        Head("개발자가 할 수 없는 것", null, "만든 사람도\n못 봅니다.")
        Spacer(Modifier.height(20.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(PROMISES.size) { i ->
                val p = PROMISES[i]
                Plain(Modifier.width(230.dp).height(230.dp), padding = PaddingValues(18.dp)) {
                    TintIcon(painterResource(p.icon), p.color)
                    Spacer(Modifier.weight(1f))
                    Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(p.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// MARK: - 9. 직접 확인

@Composable
private fun VerifySection() {
    val context = LocalContext.current
    Column {
        Head(
            "직접 확인하기",
            null,
            "전체 코드가\nGitHub에 있습니다.",
            "위 내용이 사실인지 누구나 확인할 수 있습니다. 설치 파일은 GitHub Actions가 공개된 코드로 만들고, 새 버전은 직접 승인해야 같은 서명일 때만 설치됩니다.",
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_tour_code), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("github.com/Kyoohan/hihana-neo", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "GitHub에서 코드 보기",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface)
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kyoohan/hihana-neo")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                .padding(horizontal = 20.dp, vertical = 11.dp),
        )
    }
}

// MARK: - 10. 지우는 방법

@Composable
private fun EraseSection() {
    Column {
        Head("지우는 방법", null, "언제든 지울 수 있습니다.")
        Spacer(Modifier.height(20.dp))
        Plain(padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            listOf(
                "계정 연결 해제" to "설정 → 계정·학년 → 학사시스템 연동에서 연결 해제를 누르면 바로 지워집니다.",
                "앱 삭제" to "이 기기에 저장된 모든 정보가 함께 지워집니다. 백업에도 계정은 남지 않습니다.",
                "AI 요약 서버 기록" to "요약은 게시판 글 내용만 담고 180일 뒤 지워집니다. IP 주소는 요청 횟수 제한을 위해 1시간만 셉니다.",
            ).forEachIndexed { i, (title, body) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)))
                Row(Modifier.padding(vertical = 14.dp)) {
                    Text("0${i + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
