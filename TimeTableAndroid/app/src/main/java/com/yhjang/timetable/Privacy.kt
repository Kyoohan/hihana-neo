package com.yhjang.timetable

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhjang.timetable.ui.OneUi
import com.yhjang.timetable.ui.OneUiCard
import com.yhjang.timetable.ui.OneUiDivider
import com.yhjang.timetable.ui.OneUiFullScreen
import com.yhjang.timetable.ui.OneUiSectionTitle

/**
 * 개인정보 보호 — 정보 화면과 첫 실행 투어에서 엽니다. 여기 적은 내용은 코드로 확인한 사실만 씁니다:
 * 계정 저장([HanaCredentialStore]·[MidnightSessionStore] 의 EncryptedSharedPreferences), 백업 제외(res/xml/backup_rules·
 * data_extraction_rules), 통신하는 서버(아래 [PRIVACY_HOSTS]), 광고·분석 SDK 없음(app/build.gradle.kts).
 * 코드나 서버가 바뀌면 이 문구도 같이 고칩니다.
 */
private class PrivacyPoint(val title: String, val body: String)

private class PrivacySection(val title: String, val points: List<PrivacyPoint>)

/** 앱이 스스로 접속하는 곳 — 이름, 쓰는 곳, 보내는 정보. */
private class PrivacyHost(val name: String, val use: String, val sends: String)

private val PRIVACY_SECTIONS = listOf(
    PrivacySection(
        "저장하는 정보",
        listOf(
            PrivacyPoint(
                "하이하나 아이디·비밀번호는 이 폰에만",
                "AES-256으로 암호화해 이 기기 안에만 저장합니다. 암호화 키는 안드로이드 키스토어(기기의 보안 영역)에 있어 앱 밖으로 꺼낼 수 없습니다.",
            ),
            PrivacyPoint(
                "백업·기기 이전에도 빠집니다",
                "구글 백업과 새 폰으로 옮기기에서 계정 파일을 제외했습니다. 새 폰에서는 다시 로그인해야 합니다.",
            ),
            PrivacyPoint(
                "심야면학 비밀번호는 저장하지 않습니다",
                "로그인할 때 한 번만 쓰고, 로그인 유지에 필요한 토큰만 같은 방식으로 암호화해 둡니다.",
            ),
            PrivacyPoint(
                "시간표·급식 같은 조회 결과",
                "앱을 빨리 열려고 기기에 잠시 저장해 둡니다. 이 기기 밖으로 보내지 않습니다.",
            ),
        ),
    ),
    PrivacySection(
        "개발자가 할 수 없는 것",
        listOf(
            PrivacyPoint(
                "비밀번호를 볼 수 없습니다",
                "비밀번호는 학교 학사시스템(hh.hana.hs.kr)에만 암호화된 연결(HTTPS)로 직접 보냅니다. 개발자 서버를 거치지 않으니, 개발자에게 비밀번호가 전달될 길이 없습니다.",
            ),
            PrivacyPoint(
                "개발자 서버에는 게시판 글만 갑니다",
                "개발자가 운영하는 서버는 AI 요약 서버 하나뿐이고, 여기에는 게시판 글의 제목·본문·이미지 주소만 보냅니다. 아이디·비밀번호·학번은 보내지 않습니다. 설정에서 AI 요약을 끄면 이것도 보내지 않습니다.",
            ),
            PrivacyPoint(
                "원격으로 앱을 조작할 수 없습니다",
                "서버에서 명령이나 코드를 내려받아 실행하는 기능이 없습니다. 앱이 하는 일을 바꾸려면 새 버전을 내야 합니다.",
            ),
            PrivacyPoint(
                "사용 기록을 모으지 않습니다",
                "광고·사용 분석·오류 수집 도구를 넣지 않았습니다. 무엇을 봤는지, 언제 열었는지 같은 기록이 개발자나 다른 회사로 가지 않습니다.",
            ),
        ),
    ),
    PrivacySection(
        "직접 확인하는 방법",
        listOf(
            PrivacyPoint(
                "코드가 공개되어 있습니다",
                "앱의 모든 코드는 GitHub(github.com/Kyoohan/hihana-neo)에 공개되어 있어 누구나 위 내용이 사실인지 확인할 수 있습니다.",
            ),
            PrivacyPoint(
                "공개된 코드로 빌드합니다",
                "배포하는 설치 파일(APK)은 GitHub Actions가 공개된 코드로 만들고, 그 빌드 기록도 공개됩니다.",
            ),
            PrivacyPoint(
                "업데이트는 직접 승인해야 설치됩니다",
                "새 버전은 안드로이드 설치 화면에서 직접 눌러야 설치되고, 처음 설치한 앱과 같은 서명이어야만 설치됩니다. 개발자가 앱을 바꾸는 방법은 이렇게 공개된 업데이트뿐입니다.",
            ),
        ),
    ),
    PrivacySection(
        "지우는 방법",
        listOf(
            PrivacyPoint(
                "계정 연결 해제",
                "설정 → 계정·학년 → 학사시스템 연동에서 연결 해제를 누르면 저장된 아이디·비밀번호가 바로 지워집니다.",
            ),
            PrivacyPoint(
                "앱 삭제",
                "앱을 지우면 이 기기에 저장된 모든 정보가 함께 지워집니다.",
            ),
            PrivacyPoint(
                "AI 요약 서버의 기록",
                "요약 결과는 게시판 글 내용만 담고 180일 뒤 저절로 지워집니다. 요청 횟수 제한을 위해 IP 주소를 1시간 동안만 셉니다.",
            ),
        ),
    ),
)

private val PRIVACY_HOSTS = listOf(
    PrivacyHost("하나고 학사시스템", "로그인, 시간표, 신청, 게시판", "아이디·비밀번호 (HTTPS)"),
    PrivacyHost("심야면학 사이트", "심야면학 로그인·신청", "심야면학 이름·비밀번호 (HTTPS)"),
    PrivacyHost("하나고 홈페이지", "급식 메뉴", "날짜"),
    PrivacyHost("나이스 교육정보 개방 포털", "급식 칼로리·영양·원산지", "학교 코드·날짜"),
    PrivacyHost("AI 요약 서버 (Cloudflare)", "게시판 글 요약", "글 제목·본문·이미지 주소"),
    PrivacyHost("GitHub", "업데이트 확인·다운로드", "없음"),
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
                bottom = toolbar.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "lead") {
                Text(
                    "하이하나 Neo는 학교 계정으로 로그인하는 앱이라, 계정 정보를 어떻게 다루는지 그대로 적어 둡니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = OneUi.RowPadding, vertical = 8.dp),
                )
            }
            PRIVACY_SECTIONS.forEachIndexed { index, section ->
                item(key = section.title) {
                    OneUiSectionTitle(section.title)
                    OneUiCard(modifier = Modifier.fillMaxWidth()) {
                        section.points.forEachIndexed { i, point ->
                            Text(point.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                point.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (i != section.points.lastIndex) Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                // 저장 → 개발자가 할 수 없는 것 사이에, 실제로 어디와 통신하는지 한눈에.
                if (index == 0) {
                    item(key = "hosts") {
                        OneUiSectionTitle("앱이 접속하는 곳")
                        OneUiCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                            PRIVACY_HOSTS.forEachIndexed { i, host ->
                                Row(Modifier.padding(horizontal = OneUi.CardPadding, vertical = 10.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(host.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            host.use,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        host.sends,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(120.dp),
                                    )
                                }
                                if (i != PRIVACY_HOSTS.lastIndex) OneUiDivider(startIndent = OneUi.CardPadding)
                            }
                        }
                        Text(
                            "앱이 스스로 접속하는 곳은 이것이 전부입니다. 게시글을 열면 글에 들어 있는 사진·첨부파일은 그 글이 가리키는 곳(대개 학사시스템)에서 받습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = OneUi.RowPadding, end = OneUi.RowPadding, top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
