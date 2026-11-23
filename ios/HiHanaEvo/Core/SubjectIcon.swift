import Foundation

/*
 * 과목 아이콘 — 안드로이드 Lesson.iconKey(shared/Timetable.kt)와 widget/WidgetIcons.kt 를 그대로 옮긴 것.
 * 하나고 교육과정 편제표(2015·2022 개정, hana.hs.kr/education/curriculum.do)의 모든 과목이 어느 한 줄에 걸리도록 낱말로 고릅니다.
 * 먼저 맞는 줄이 이기므로 순서가 뜻입니다: "세계 문화와 영어"는 영어, "역사로 탐구하는 현대 세계"는 역사, "고전역학"은 물리,
 * "세포와 물질대사"는 생명, "법과 사회"는 정치·법, "AP 프로그래밍과 문제해결"은 정보. 어느 줄에도 없으면 책(menu_book).
 * 키는 안드로이드와 같은 이름을 써서 두 앱의 규칙을 줄 단위로 맞춰 볼 수 있게 했습니다. Core 라서 위젯·실시간 활동도 같은 것을 씁니다.
 */
enum SubjectIcon {
    /// 공강 — 안드로이드 free_breakfast.
    static let freeKey = "free_breakfast"
    /// 어느 줄에도 걸리지 않을 때 — 안드로이드 menu_book.
    static let fallbackKey = "menu_book"

    /// (키, 낱말들) — 위에서부터 차례로 봅니다. 안드로이드 when 절과 같은 순서·같은 낱말.
    static let rules: [(key: String, words: [String])] = [
        ("query_stats", ["데이터 과학"]),
        ("casino", ["확률과 통계", "통계"]),
        ("smart_toy", ["인공지능", "선형대수", "이산수학"]),
        ("code", ["프로그래밍", "소프트웨어", "컴퓨터", "컴퓨팅", "자료구조", "알고리즘", "정보"]),
        ("bolt", ["물리", "역학", "전자기", "양자"]),
        ("biotech", ["생명", "생물", "세포", "유전", "분자"]),
        ("science", ["화학", "물질", "유기"]),
        ("public", ["지구", "행성", "우주", "환경"]),
        ("sports_soccer", ["스포츠", "체육", "운동"]),
        ("music_note", ["음악", "합주", "합창", "연주", "공연"]),
        ("palette", ["미술", "드로잉", "조형", "디자인"]),
        ("translate", ["영어", "영미", "영문학", "퍼블릭", "스피킹", "중국", "일본"]),
        ("history_edu", ["한국사", "역사", "세계사", "동아시아사", "지성사"]),
        ("paid", ["경제", "금융"]),
        ("account_balance", ["정치", "국제", "법과 사회", "와 법"]),
        ("psychology", ["윤리", "철학", "심리", "논리", "사상"]),
        ("map", ["지리", "세계", "지역", "글로벌", "비교 문화"]),
        ("groups", ["사회"]),
        ("functions", ["미적분", "미분적분", "수학", "대수", "기하", "정수론", "함수론", "수치해석", "문제해결"]),
        ("edit_note", ["국어", "문학", "독서", "화법", "언어", "작문", "글쓰기", "고전", "논술", "스토리텔"]),
        ("science", ["과학", "실험", "융합", "프런티어", "공학"]),
        ("school", ["교육"]),
        ("lightbulb", ["탐구", "R&E", "과제연구", "과제 연구", "프로젝트", "세미나", "토론", "토의"]),
    ]

    /// 과목명 → 아이콘 키. 공강 판단은 Lesson 쪽(교실 없음)에서 합니다.
    static func key(for subject: String) -> String {
        rules.first { rule in rule.words.contains { subject.contains($0) } }?.key ?? fallbackKey
    }

    /// 아이콘 키 → SF Symbol(iOS 18 에 있는 이름만). 안드로이드 Material 아이콘과 뜻이 같은 것으로.
    static func symbol(forKey key: String) -> String {
        switch key {
        case "free_breakfast": "cup.and.saucer"
        case "query_stats": "chart.xyaxis.line"
        case "casino": "dice"
        case "smart_toy": "cpu"
        case "code": "chevron.left.forwardslash.chevron.right"
        case "bolt": "bolt"
        case "biotech": "microbe"
        case "science": "flask"
        case "public": "globe.asia.australia"
        case "sports_soccer": "soccerball"
        case "music_note": "music.note"
        case "palette": "paintpalette"
        case "translate": "character.bubble"
        case "history_edu": "scroll"
        case "paid": "wonsign.circle"
        case "account_balance": "building.columns"
        case "psychology": "brain.head.profile"
        case "map": "map"
        case "groups": "person.3"
        case "functions": "function"
        case "edit_note": "pencil.line"
        case "school": "graduationcap"
        case "lightbulb": "lightbulb"
        default: "book"
        }
    }

    /// 표에 있는 모든 키(공강·기본값 포함) — 테스트가 SF Symbol 이 실제로 있는지 훑는 데 씁니다.
    static var allKeys: [String] { [freeKey, fallbackKey] + rules.map(\.key) }
}
