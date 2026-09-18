package com.yhjang.timetable

/** 버전별 변경 사항 — 정보 화면의 "변경 사항"에서만 보여주고, 설정 목록에는 버전만 적습니다. 최신이 맨 앞. */
data class ChangelogEntry(val version: String, val items: List<String>)

val CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        "7.1",
        listOf(
            "홈 카드마다 아이콘 추가, 급식 카드는 메뉴를 쭉 이어 표시",
            "주간 시간표 칸을 키워 과목명이 덜 잘리게, 상단 여백 정리",
            "급식: 일간 보기 / 주간 보기 전환 버튼",
            "신청·내역: 내역 조회와 신청하기를 같은 버튼으로",
            "게시판 '시험' 태그 제거, 세션 만료 시 게시글이 안 열리던 문제 수정",
            "하단 바 끌기 시 캡슐이 튀던 버그 수정, 유리 효과 완화",
        ),
    ),
    ChangelogEntry(
        "7.0",
        listOf(
            "하단 바: 선택 캡슐을 옆으로 끌어 탭 전환, 액체 유리 굴절 효과",
            "강조 색 직접 선택(컬러 피커), '자동'은 시스템 테마 색",
            "설정 화면 툴바: 스크롤하면 뒤로가기만 글래스 원으로 남음",
            "급식 알레르기 기본값 없음 — 설정에서 고른 것만 표시",
            "학사 탭은 게시판이 먼저, 강조 색 팔레트는 여러 줄로",
            "One UI 9 타이포 비율로 정리, 다이얼로그 가운데 정렬",
        ),
    ),
    ChangelogEntry(
        "6.20",
        listOf(
            "One UI 9 스타일로 화면 전체 리디자인 — 글래스 하단 바·플로팅 아이콘·다이얼로그",
            "오늘 탭: 게시판을 전체 폭으로, 알리미는 최근 2건만 학사일정 옆에",
            "앱을 켰을 때 게시판·알리미가 비어 보이던 문제 수정",
            "위젯: 수업·면학이 바뀌는 순간 정확히 갱신 (\"알람 및 리마인더\" 권한 필요)",
            "앱 내 업데이트: 새 버전이 나오면 정보 화면에서 바로 설치",
            "설정에 '하이하나 Neo 정보' 화면 추가 (버전·업데이트 확인·변경 사항·오픈소스 라이선스)",
        ),
    ),
    ChangelogEntry(
        "6.19",
        listOf("하드코딩 시간표 제거 — 포털 시간표만 사용"),
    ),
)

/** 정보 화면의 오픈소스 라이선스 목록 */
data class OssLibrary(val name: String, val license: String, val url: String)

val OSS_LIBRARIES: List<OssLibrary> = listOf(
    OssLibrary("AndroidX / Jetpack Compose", "Apache License 2.0", "https://developer.android.com/jetpack"),
    OssLibrary("Kotlin & kotlinx.coroutines", "Apache License 2.0", "https://kotlinlang.org"),
    OssLibrary("OkHttp", "Apache License 2.0", "https://github.com/square/okhttp"),
    OssLibrary("Haze", "Apache License 2.0", "https://github.com/chrisbanes/haze"),
    OssLibrary("Glance App Widget", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/glance"),
    OssLibrary("Material Icons", "Apache License 2.0", "https://fonts.google.com/icons"),
)
