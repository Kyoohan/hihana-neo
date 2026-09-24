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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiFullScreen
import kotlinx.coroutines.delay

/**
 * 개인정보 보호 — 정보 화면과 첫 실행 투어에서 엽니다. 제품 소개 페이지처럼 큰 글씨·원색 타일·직접 눌러 보는 시연으로 꾸몄습니다.
 * 적은 내용은 코드와 실제 연결로 확인한 사실만 씁니다:
 * 계정 저장([HanaCredentialStore]·[MidnightSessionStore] 의 EncryptedSharedPreferences, AES-256-GCM, 열쇠는 안드로이드 키스토어 —
 * StrongBox 를 요청하지 않으므로 TEE, 갤럭시는 삼성 녹스 기반), 백업 제외(res/xml/backup_rules·data_extraction_rules),
 * 통신하는 서버([PRIVACY_HOSTS]), 광고·분석 SDK 없음(app/build.gradle.kts), 학사시스템 연결 TLS 1.3 · AES-256-GCM (2026-09-24 확인).
 * 코드·서버가 바뀌면 이 문구도 같이 고칩니다.
 */

// One UI 9 설정 아이콘에서 따온 원색.
private val PvBlue = Color(0xFF387AFF)
private val PvViolet = Color(0xFF715AFF)
private val PvPink = Color(0xFFEC5881)
private val PvOrange = Color(0xFFE65B17)
private val PvGreen = Color(0xFF65C23B)
private val PvYellow = Color(0xFFFDBE4E)
private val PvTeal = Color(0xFF2FB8A6)

/** 앱이 스스로 접속하는 곳 — 이름, 쓰는 곳, 보내는 정보, 타일 색. */
private class PrivacyHost(val name: String, val use: String, val sends: String, val color: Color)

private val PRIVACY_HOSTS = listOf(
    PrivacyHost("하나고 학사시스템", "로그인 · 시간표 · 신청 · 게시판", "아이디·비밀번호\nHTTPS", PvBlue),
    PrivacyHost("심야면학 사이트", "심야면학 로그인 · 신청", "이름·비밀번호\nHTTPS", PvViolet),
    PrivacyHost("AI 요약 서버", "게시판 글 요약 (Cloudflare)", "글 제목·본문\n계정 정보 없음", PvPink),
    PrivacyHost("나이스 교육정보", "급식 칼로리·영양·원산지", "학교 코드·날짜", PvOrange),
    PrivacyHost("하나고 홈페이지", "급식 메뉴", "날짜", Color(0xFFE9A52D)),
    PrivacyHost("GitHub", "업데이트 확인·다운로드", "보내는 정보 없음", PvTeal),
)

@Composable
internal fun PrivacyScreen(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    OneUiFullScreen(title = "개인정보 보호", onDismiss = onDismiss) { toolbar ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OneUi.PagePadding,
                end = OneUi.PagePadding,
                top = toolbar.calculateTopPadding() + 4.dp,
                bottom = toolbar.calculateBottomPadding() + 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") { Reveal { PrivacyHero() } }
            item(key = "flow") { Reveal { PrivacyFlowPanel() } }
            item(key = "demo") { Reveal { EncryptionDemo() } }
            item(key = "aes") { Reveal { AesSection() } }
            item(key = "https") { Reveal { HttpsSection() } }
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
    val t by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "reveal")
    Box(Modifier.graphicsLayer { alpha = t; translationY = (1f - t) * 60f }) { content() }
}

/** 섹션 머리 — 색 점 + 작은 머리글, 큰 제목, 설명. [light] 는 진한 색 판 위(흰 글씨). */
@Composable
private fun SectionHead(eyebrow: String, color: Color, title: String, body: String? = null, light: Boolean = false) {
    val fg = if (light) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (light) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (light) Color.White else color)
    }
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = fg)
    if (body != null) {
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = sub)
    }
}

/** 둥근 판 — 색 그라데이션을 주면 진한 판, 아니면 카드 색. */
@Composable
private fun Panel(modifier: Modifier = Modifier, brush: Brush? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(MaterialTheme.colorScheme.surface))
            .padding(22.dp),
        content = content,
    )
}

// MARK: - 1. 첫 화면

@Composable
private fun PrivacyHero() {
    val spin = rememberInfiniteTransition(label = "heroSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "heroAngle")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            // 천천히 도는 원색 빛 고리 위에 자물쇠.
            Canvas(Modifier.size(200.dp).blur(3.dp).graphicsLayer { rotationZ = angle }) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(PvBlue, PvViolet, PvPink, PvOrange, PvYellow, PvGreen, PvTeal, PvBlue)),
                    radius = size.minDimension / 2f,
                    alpha = 0.6f,
                )
            }
            Box(Modifier.size(150.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(PvBlue, PvViolet))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(34.dp))
        SecuritySpecTiles()
        Spacer(Modifier.height(22.dp))
        Text(
            "비밀번호는\n이 폰과 학교만 압니다",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp),
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "하이하나 Neo는 학교 계정으로 로그인하는 앱입니다. 그래서 계정 정보를 어떻게 지키는지, 어디로 무엇을 보내는지 숨김없이 보여 드립니다.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private class SecuritySpec(val name: String, val caption: String, val icon: Int, val from: Color, val to: Color)

private val SECURITY_SPECS = listOf(
    SecuritySpec("AES-256", "저장할 때 암호화", R.drawable.ic_pv_key, Color(0xFFC98A0C), PvYellow),
    SecuritySpec("HTTPS", "보낼 때 암호화", R.drawable.ic_pv_globe_lock, Color(0xFF1E9E6A), PvTeal),
    SecuritySpec("TLS 1.3", "최신 보안 연결", R.drawable.ic_pv_handshake, PvOrange, PvPink),
    SecuritySpec("TEE / Knox", "열쇠는 기기 보안 영역에", R.drawable.ic_pv_knox, Color(0xFF1428A0), Color(0xFF3E6BE0)),
)

/** 맨 위 네 가지 보안 기술 — AES-256 · HTTPS · TLS 1.3 · TEE/Knox. 투어의 개인정보 장도 같이 씁니다. */
@Composable
internal fun SecuritySpecTiles() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SECURITY_SPECS.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, spec ->
                    RevealCell(delayMs = (row * 2 + col) * 80) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = spec.from, spotColor = spec.from)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Brush.linearGradient(listOf(spec.from, spec.to)))
                                .padding(16.dp),
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
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

/** 한 줄에 둘씩 놓는 칸 — 칸 폭을 나눠 가지면서 차례로 떠오릅니다. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.RevealCell(delayMs: Int, content: @Composable () -> Unit) {
    Box(Modifier.weight(1f)) { Reveal(delayMs) { content() } }
}

// MARK: - 2. 개인정보가 가는 길

/** 입력 → 이 폰 → (HTTPS) 학사시스템, 옆 갈래 개발자 서버엔 게시판 글만 — 계정 정보 통로 없음. 투어도 씁니다. */
@Composable
internal fun PrivacyFlowPanel() {
    val flow = rememberInfiniteTransition(label = "flow")
    val phase by flow.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "flowPhase")
    Panel {
        SectionHead("개인정보가 가는 길", PvOrange, "비밀번호는 한 길로만 갑니다", "입력한 아이디·비밀번호가 어디를 거쳐 어디로 가는지 그대로 그렸습니다. 개발자에게 이어지는 길은 없습니다.")
        Spacer(Modifier.height(18.dp))
        FlowNode(
            painterResource(R.drawable.ic_pv_key),
            "입력한 아이디·비밀번호",
            "로그인 화면에서",
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowWire(PvBlue, phase)
        FlowNode(
            painterResource(R.drawable.ic_pv_phone),
            "이 폰",
            "AES-256으로 잠가 저장 · 열쇠는 TEE(기기 보안 영역) 안",
            null,
            Color.White,
            brush = Brush.linearGradient(listOf(PvBlue, PvViolet)),
            titleColor = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                FlowWire(PvGreen, phase)
                FlowTag("HTTPS · TLS 1.3\n아이디·비밀번호", PvGreen)
                FlowNode(painterResource(R.drawable.ic_school), "학사시스템", "hh.hana.hs.kr", PvGreen.copy(alpha = 0.16f), PvGreen)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                FlowWire(PvPink, phase)
                FlowTag("게시판 글\n제목·본문만", PvPink)
                FlowNode(painterResource(R.drawable.ic_settings_book), "AI 요약 서버", "개발자가 운영", PvPink.copy(alpha = 0.14f), PvPink)
                Spacer(Modifier.height(10.dp))
                Text(
                    "✕ 아이디·비밀번호\n가는 통로 없음",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF6B6B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color(0xFFFF6B6B).copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .padding(vertical = 9.dp, horizontal = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            buildAnnotatedString {
                append("개발자가 운영하는 서버는 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("AI 요약 서버 하나") }
                append("이고, 여기에는 게시판 글만 갑니다. 계정 정보를 개발자에게 보내는 코드는 앱 어디에도 없으며, ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("앱의 전체 코드가 GitHub에 공개") }
                append("되어 있어 누구나 직접 확인할 수 있습니다.")
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(16.dp),
        )
    }
}

@Composable
private fun FlowNode(
    icon: Painter,
    title: String,
    caption: String,
    background: Color?,
    tint: Color,
    brush: Brush? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(background ?: Color.Transparent))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = titleColor.copy(alpha = 0.8f))
        }
    }
}

/** 세로 점선과 그 위로 흘러가는 점 — 정보가 움직이는 방향. */
@Composable
private fun FlowWire(color: Color, phase: Float) {
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val x = size.width / 2
        drawLine(color.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)))
        repeat(2) { i ->
            val y = ((phase + i / 2f) % 1f) * size.height
            drawCircle(color, radius = 9f, center = Offset(x, y))
        }
    }
}

@Composable
private fun FlowTag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

// MARK: - 3. 직접 눌러 보는 암호화

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
        // 글자가 뒤섞이며 바뀌는 효과.
        for (step in 1..12) {
            shown = String(CharArray(target.length) { i -> if (i < target.length * step / 12) target[i] else pool.random() })
            delay(45)
        }
        shown = target
    }
    Panel(brush = Brush.linearGradient(listOf(Color(0xFF1D2B6B), PvViolet, Color(0xFF9B3FD1)), start = Offset.Zero, end = Offset(900f, 900f))) {
        SectionHead("직접 눌러 보세요", PvYellow, "저장되는 모습", "비밀번호는 그대로 저장되지 않습니다. 아래 버튼을 눌러 이 폰 안에 실제로 남는 형태를 확인해 보세요.", light = true)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.28f)).padding(16.dp)) {
            Text(if (encrypted) "이 폰에 저장된 값 (AES-256)" else "입력한 비밀번호", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
            Text(
                shown,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = if (encrypted) 14.sp else 22.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (encrypted) PvYellow else Color.White,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (encrypted) "원래대로" else "암호화하기",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D2B6B),
            modifier = Modifier.clip(CircleShape).background(Color.White).clickable { encrypted = !encrypted }.padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}

// MARK: - 4. AES-256

@Composable
private fun AesSection() {
    val marquee = rememberScrollState()
    LaunchedEffect(Unit) {
        while (true) {
            marquee.animateScrollTo(marquee.maxValue, tween(10_000, easing = LinearEasing))
            delay(800)
            marquee.scrollTo(0)
        }
    }
    Panel {
        SectionHead("AES-256", PvYellow, "열쇠의 가짓수, 2²⁵⁶", "저장된 비밀번호는 AES-256으로 잠겨 있습니다. 열려면 256비트 열쇠가 필요한데, 가능한 열쇠의 수가 아래와 같습니다.")
        Spacer(Modifier.height(18.dp))
        Text(
            "2²⁵⁶",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, brush = Brush.linearGradient(listOf(PvYellow, PvOrange, PvPink))),
            fontWeight = FontWeight.Black,
        )
        // 78자리 숫자가 흘러갑니다.
        Row(Modifier.fillMaxWidth().horizontalScroll(marquee, enabled = false)) {
            Text(
                "115,792,089,237,316,195,423,570,985,008,687,907,853,269,984,665,640,564,039,457,584,007,913,129,639,936 가지",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("3.7×10⁵¹년", "1초에 100경 번 시도하는 컴퓨터로 모든 열쇠를 다 시도하는 데 걸리는 시간", PvBlue, Modifier.weight(1f))
            StatTile("우주 나이의\n10⁴¹배", "지금까지 흐른 우주의 나이(약 138억 년)와 비교하면", PvViolet, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("최고 기밀급", "미국 정부가 최고 기밀 문서에 쓰도록 승인한 방식이며, 은행과 안드로이드 기기 암호화에도 쓰입니다", PvPink, Modifier.weight(1f))
            StatTile(
                "TEE 속 열쇠",
                "잠근 열쇠는 안드로이드 키스토어를 통해 TEE(프로세서 안의 격리된 보안 영역, 갤럭시는 삼성 녹스)에 보관되어 앱 밖으로 꺼낼 수 없습니다",
                Color(0xFFE9A52D),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(big: String, small: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(color.copy(alpha = 0.15f)).padding(16.dp)) {
        Text(big, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
        Spacer(Modifier.height(6.dp))
        Text(small, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
    }
}

// MARK: - 5. HTTPS

@Composable
private fun HttpsSection() {
    var secure by remember { mutableStateOf(true) }
    val flow = rememberInfiniteTransition(label = "packets")
    val phase by flow.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "packetPhase")
    val line = if (secure) PvGreen else PvOrange
    Panel(brush = Brush.linearGradient(listOf(Color(0xFF0E3B2E), Color(0xFF12664B), Color(0xFF1E8A5C)), start = Offset.Zero, end = Offset(900f, 700f))) {
        SectionHead(
            "HTTPS",
            PvGreen,
            "학교 와이파이에서도 안전하게",
            "로그인할 때 비밀번호는 암호화된 연결(HTTPS)로 학사시스템에 곧장 갑니다. 같은 와이파이의 누군가가 엿봐도 알아볼 수 없습니다.",
            light = true,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).padding(4.dp)) {
            listOf(true to "HTTPS (지금)", false to "암호화가 없다면").forEach { (value, label) ->
                val on = secure == value
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (on) Color(0xFF0E3B2E) else Color.White,
                    modifier = Modifier.clip(CircleShape).background(if (on) Color.White else Color.Transparent).clickable { secure = value }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Endpoint(painterResource(R.drawable.ic_pv_phone), "내 폰")
            Canvas(Modifier.weight(1f).height(28.dp).padding(horizontal = 6.dp)) {
                val y = size.height / 2
                drawLine(line.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), strokeWidth = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                repeat(3) { i ->
                    val x = ((phase + i / 3f) % 1f) * size.width
                    drawCircle(line, radius = 9f, center = Offset(x, y))
                }
            }
            Endpoint(painterResource(R.drawable.ic_school), "학사시스템")
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.3f)).padding(14.dp)) {
            Text("같은 와이파이의 누군가에게 보이는 것", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
            AnimatedContent(secure, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "sniff") { s ->
                Text(
                    if (s) "17 03 03 01 2a e9 4c 8b f0 3d 91 7a c2 5e 08 b6 d4 13 …" else "mem_id=hana2026&mem_pwd=hana2026!",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (s) Color.White else PvYellow,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TLS 1.3", "AES-256-GCM", "인증서 확인").forEach { chip ->
                Text(
                    chip,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "학사시스템과의 연결은 최신 TLS 1.3과 AES-256-GCM으로 암호화됩니다(2026년 9월 확인). 인터넷 뱅킹과 같은 방식이며, 인증서로 진짜 hh.hana.hs.kr인지도 확인합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun Endpoint(icon: Painter, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF12664B), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

// MARK: - 6. 앱이 접속하는 곳

@Composable
private fun HostsSection() {
    Column {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            SectionHead("앱이 접속하는 곳", PvOrange, "여섯 곳, 그게 전부", "앱이 스스로 연결하는 서버와 보내는 정보입니다. 비밀번호가 가는 곳은 학사시스템과 심야면학 사이트뿐입니다.")
        }
        Spacer(Modifier.height(6.dp))
        PRIVACY_HOSTS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                pair.forEach { host ->
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.linearGradient(listOf(host.color, host.color.copy(alpha = 0.72f))))
                            .padding(16.dp),
                    ) {
                        Text(host.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(host.use, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f))
                        Spacer(Modifier.weight(1f))
                        Text(host.sends, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        Text(
            "게시글을 열면 글에 들어 있는 사진·첨부파일은 그 글이 가리키는 곳(대개 학사시스템)에서 받습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

// MARK: - 7. 개발자가 할 수 없는 것

private class Promise(val title: String, val body: String, val color: Color, val icon: Int)

private val PROMISES = listOf(
    Promise("비밀번호를 볼 수 없습니다", "비밀번호는 학사시스템에만 곧장 갑니다. 개발자 서버를 거치지 않으니 전달될 길이 없습니다.", PvViolet, R.drawable.ic_tour_eye_off),
    Promise("개발자 서버엔 게시판 글만", "개발자가 운영하는 서버는 AI 요약 서버 하나뿐이고, 게시판 글의 제목·본문·이미지 주소만 받습니다. 요약을 끄면 이것도 보내지 않습니다.", PvPink, R.drawable.ic_settings_book),
    Promise("원격 조작 기능 없음", "서버에서 명령이나 코드를 받아 실행하지 않습니다. 앱이 하는 일을 바꾸려면 새 버전을 내야 합니다.", PvBlue, R.drawable.ic_tour_swap),
    Promise("사용 기록 수집 없음", "광고·사용 분석·오류 수집 도구를 넣지 않았습니다. 무엇을 봤는지 같은 기록이 어디로도 가지 않습니다.", PvOrange, R.drawable.ic_query_stats),
)

@Composable
private fun CannotSection() {
    Column {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            SectionHead("개발자가 할 수 없는 것", PvViolet, "만든 사람도 못 봅니다", "옆으로 넘겨 보세요.")
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(PROMISES.size) { i ->
                val p = PROMISES[i]
                Column(
                    Modifier
                        .width(240.dp)
                        .height(270.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.verticalGradient(listOf(p.color, p.color.copy(alpha = 0.78f))))
                        .padding(20.dp),
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(p.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(p.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(p.body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.92f))
                }
            }
        }
    }
}

// MARK: - 8. 직접 확인

@Composable
private fun VerifySection() {
    val context = LocalContext.current
    Panel(brush = Brush.linearGradient(listOf(Color(0xFF14161B), Color(0xFF2A2F3A)), start = Offset.Zero, end = Offset(800f, 800f))) {
        SectionHead(
            "직접 확인하기",
            PvTeal,
            "전체 코드가 GitHub에 있습니다",
            "이 앱의 전체 코드가 GitHub에 공개되어 있어, 위 내용이 사실인지 누구나 확인할 수 있습니다. 배포하는 설치 파일은 GitHub Actions가 공개된 코드로 만들고, " +
                "그 기록도 공개됩니다. 새 버전은 안드로이드 설치 화면에서 직접 눌러야 설치되고, 처음 설치한 앱과 같은 서명이어야만 설치됩니다.",
            light = true,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_tour_code), contentDescription = null, tint = PvTeal, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("github.com/Kyoohan/hihana-neo", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp), color = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "GitHub에서 코드 보기",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF14161B),
            modifier = Modifier
                .clip(CircleShape)
                .background(PvTeal)
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kyoohan/hihana-neo")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

// MARK: - 9. 지우는 방법

@Composable
private fun EraseSection() {
    Panel {
        SectionHead("지우는 방법", PvYellow, "언제든 지울 수 있습니다")
        Spacer(Modifier.height(16.dp))
        EraseRow("1", PvBlue, "계정 연결 해제", "설정 → 계정·학년 → 학사시스템 연동에서 연결 해제를 누르면 저장된 아이디·비밀번호가 바로 지워집니다.")
        Spacer(Modifier.height(14.dp))
        EraseRow("2", PvGreen, "앱 삭제", "앱을 지우면 이 기기에 저장된 모든 정보가 함께 지워집니다. 백업에도 계정은 남지 않습니다.")
        Spacer(Modifier.height(14.dp))
        EraseRow("3", PvPink, "AI 요약 서버 기록", "요약 결과는 게시판 글 내용만 담고 180일 뒤 저절로 지워집니다. 요청 횟수 제한을 위해 IP 주소를 1시간 동안만 셉니다.")
    }
}

@Composable
private fun EraseRow(number: String, color: Color, title: String, body: String) {
    Row {
        Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(number, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
