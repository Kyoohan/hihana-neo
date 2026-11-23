import Foundation

/// 급식 알레르기 번호 19종 — 추적 대상은 사용자가 설정에서 고릅니다(안드로이드 Allergens.kt 와 같은 표).
enum Allergens {
    static let legend: [(Int, String)] = [
        (1, "난류"), (2, "우유"), (3, "메밀"), (4, "땅콩"), (5, "대두"), (6, "밀"), (7, "고등어"), (8, "게"), (9, "새우"),
        (10, "돼지고기"), (11, "복숭아"), (12, "토마토"), (13, "아황산류"), (14, "호두"), (15, "닭고기"), (16, "쇠고기"),
        (17, "오징어"), (18, "조개류"), (19, "잣"),
    ]

    static func label(_ code: Int) -> String? { legend.first { $0.0 == code }?.1 }

    /// 견과류 세부 코드 — 배지·위젯에서는 '견과'로 묶어 표기 폭을 줄입니다.
    private static let nutCodes: Set<Int> = [4, 14, 19]

    /// 코드가 빠진 원문을 위한 키워드 백업.
    private static let keywords: [Int: [String]] = [4: ["땅콩"], 14: ["호두"], 19: ["잣"], 9: ["새우"]]
    private static let generalNut = ["견과", "아몬드", "캐슈", "피칸", "마카다미아", "헤이즐넛", "브라질너트"]

    struct Detected: Equatable { let label: String; let names: [String] }

    /// 선택된 알레르기만 검사해, 검출된 것마다 한 항목씩.
    static func detect(codes: Set<Int>, menuText: String, selected: Set<Int>) -> [Detected] {
        let normalized = menuText.lowercased().replacingOccurrences(of: #"\s+"#, with: "", options: .regularExpression)
        return selected.sorted().compactMap { code in
            guard let label = label(code) else { return nil }
            var names: [String] = []
            if codes.contains(code) { names.append(label) }
            var words = keywords[code] ?? []
            if nutCodes.contains(code) { words += generalNut }
            names += words.filter { normalized.contains($0) }
            var seen = Set<String>()
            names = names.filter { seen.insert($0).inserted }
            return names.isEmpty ? nil : Detected(label: label, names: names)
        }
    }

    /// 배지·위젯용 짧은 이름 — 견과류는 '견과'로 묶습니다.
    static func shortLabels(_ list: [Detected]) -> [String] {
        var seen = Set<String>()
        return list.map { ["땅콩", "호두", "잣"].contains($0.label) ? "견과" : $0.label }.filter { seen.insert($0).inserted }
    }

    /// 설정 줄의 요약 — "알레르기 표시 안 함", "알레르기 우유", "알레르기 우유 외 2개".
    static func summary(_ codes: Set<Int>) -> String {
        let names = codes.sorted().compactMap(label)
        switch names.count {
        case 0: return "알레르기 표시 안 함"
        case 1: return "알레르기 \(names[0])"
        default: return "알레르기 \(names[0]) 외 \(names.count - 1)개"
        }
    }
}
