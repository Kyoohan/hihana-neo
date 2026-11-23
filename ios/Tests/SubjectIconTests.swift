import XCTest
import UIKit
@testable import HiHanaEvo

/// 과목 아이콘 규칙 — 안드로이드 14.2 Lesson.iconKey 와 같은 결과인지, 그리고 고른 SF Symbol 이 iOS 에 실제로 있는지.
final class SubjectIconTests: XCTestCase {
    private func key(_ subject: String, room: String? = "301") -> String { Lesson(subject: subject, room: room).iconKey }

    /// 편제표 과목명 → 키. 안드로이드 when 절과 같은 순서로 걸러야 나오는 값입니다.
    func testCurriculumSubjects() {
        let cases: [(String, String)] = [
            ("데이터 과학과 머신러닝", "query_stats"),
            ("확률과 통계", "casino"),
            ("인공지능 수학", "smart_toy"),            // '수학'(functions)보다 먼저
            ("선형대수", "smart_toy"),
            ("AP 프로그래밍과 문제해결", "code"),      // '문제해결'(functions)보다 먼저
            ("정보", "code"),
            ("물리학Ⅰ", "bolt"),
            ("고전역학", "bolt"),                      // '고전'(국어)보다 먼저
            ("생명과학Ⅱ", "biotech"),
            ("세포와 물질대사", "biotech"),            // '물질'(화학)보다 먼저
            ("화학Ⅰ", "science"),
            ("유기화학", "science"),
            ("지구과학Ⅰ", "public"),
            ("행성우주과학", "public"),
            ("스포츠 생활1(남)(5반) 김응주 /", "sports_soccer"),
            ("음악 연주와 창작", "music_note"),
            ("미술 창작", "palette"),
            ("세계 문화와 영어", "translate"),         // '세계'(지리)보다 먼저
            ("중국어Ⅰ", "translate"),
            ("한국사", "history_edu"),
            ("역사로 탐구하는 현대 세계", "history_edu"), // '탐구'·'세계'보다 먼저
            ("동아시아사", "history_edu"),
            ("경제", "paid"),
            ("금융과 경제생활", "paid"),
            ("정치와 법", "account_balance"),
            ("법과 사회", "account_balance"),          // '사회'(groups)보다 먼저
            ("국제 관계와 국제기구", "account_balance"),
            ("윤리와 사상", "psychology"),
            ("철학", "psychology"),
            ("한국지리", "map"),
            ("세계지리", "map"),
            ("통합사회", "groups"),
            ("사회·문화", "groups"),
            ("미적분", "functions"),
            ("기하", "functions"),
            ("수학Ⅰ", "functions"),
            ("국어", "edit_note"),
            ("문학", "edit_note"),
            ("화법과 작문", "edit_note"),
            ("언어와 매체", "edit_note"),
            ("통합과학", "science"),
            ("과학탐구실험", "science"),               // '과학'이 '탐구'보다 먼저
            ("교육학", "school"),
            ("R&E", "lightbulb"),
            ("과제 탐구", "lightbulb"),
            ("진로와 직업", "menu_book"),              // 어느 줄에도 없으면 기본값
        ]
        for (subject, expected) in cases {
            XCTAssertEqual(key(subject), expected, subject)
        }
    }

    func testFreePeriod() {
        XCTAssertEqual(Lesson.free.iconKey, "free_breakfast")
        XCTAssertEqual(Lesson.free.symbol, "cup.and.saucer")
    }

    /// 선생님 이름은 보지 않습니다 — '조형준'의 '조형'이 미술, '정치훈'의 '정치'가 정치·법으로 잡히면 안 됩니다.
    func testTeacherNameDoesNotHijack() {
        XCTAssertEqual(key("국어(3반) 조형준 /"), "edit_note")
        XCTAssertEqual(key("수학Ⅱ(2반) 정치훈"), "functions")
    }

    /// 수업 블록과 그 앞 쉬는 시간이 과목 기호를 씁니다 — 위젯·실시간 활동이 읽는 값.
    func testBlockUsesSubjectSymbol() {
        let l = Lesson(subject: "생명과학Ⅰ(1반) 김하나", room: "과학실")
        let now = Date()
        XCTAssertEqual(Block(kind: .lesson(period: 1, l), start: now, end: now).symbol, "microbe")
        let gap = Gap(label: "쉬는 시간", fallbackTitle: "", caption: "", next: .lesson(period: 2, l), accent: .lesson)
        XCTAssertEqual(Block(kind: .gap(gap), start: now, end: now).symbol, "microbe")
    }

    /// 표에 있는 모든 SF Symbol 이 이 OS 에 있어야 합니다(없으면 위젯에 빈칸).
    func testEverySymbolExists() {
        for k in SubjectIcon.allKeys {
            let name = SubjectIcon.symbol(forKey: k)
            XCTAssertNotNil(UIImage(systemName: name), "\(k) → \(name)")
        }
    }

    /// 기본값 말고는 모두 책이 아닌 제 기호를 가져야 합니다(안드로이드 iconResFor 에서 빠진 키가 책으로 뜨던 것처럼 되지 않게).
    func testEveryRuleKeyHasOwnSymbol() {
        for k in SubjectIcon.allKeys where k != SubjectIcon.fallbackKey {
            XCTAssertNotEqual(SubjectIcon.symbol(forKey: k), "book", k)
        }
    }
}
