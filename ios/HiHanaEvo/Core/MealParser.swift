import Foundation

/// 하이하나 급식 응답 해석 — 안드로이드 HanaMealClient 의 파서와 같은 규칙.
enum MealParser {
    private static let parenAllergy = try! NSRegularExpression(pattern: #"\(\s*[\d.\s]*\)"#)
    private static let bareAllergy = try! NSRegularExpression(pattern: #"\b\d+(?:\.\d+)+\b"#)
    private static let digits = try! NSRegularExpression(pattern: #"\d+"#)
    private static let br = try! NSRegularExpression(pattern: #"<br\s*/?>"#, options: .caseInsensitive)
    private static let whitespace = try! NSRegularExpression(pattern: #"\s+"#)

    private static func replace(_ re: NSRegularExpression, in s: String, with t: String) -> String {
        re.stringByReplacingMatches(in: s, range: NSRange(s.startIndex..., in: s), withTemplate: t)
    }

    private static func matches(_ re: NSRegularExpression, in s: String) -> [String] {
        re.matches(in: s, range: NSRange(s.startIndex..., in: s)).compactMap { Range($0.range, in: s).map { String(s[$0]) } }
    }

    /// `<br>` 과 '/' 로 항목을 나눕니다.
    static func segments(_ raw: String) -> [String] {
        replace(br, in: raw, with: "\n").split(whereSeparator: { $0 == "\n" || $0 == "/" }).map(String.init)
    }

    /// 원문에서 알레르기 번호만 — `(1.5.6.13)` 괄호 표기와 `1.5.13` 맨자 표기.
    static func allergenCodes(_ raw: String) -> Set<Int> {
        var codes = Set<Int>()
        for re in [parenAllergy, bareAllergy] {
            for m in matches(re, in: raw) { for d in matches(digits, in: m) { if let n = Int(d) { codes.insert(n) } } }
        }
        return codes
    }

    /// 알레르기 표기를 지우고 이름만.
    static func cleanName(_ raw: String) -> String {
        var s = replace(parenAllergy, in: raw, with: "")
        s = replace(bareAllergy, in: s, with: "")
        s = replace(whitespace, in: s, with: " ")
        return s.trimmingCharacters(in: .whitespaces)
    }

    static func items(_ raw: String) -> [MealItem] {
        segments(raw).compactMap { seg in
            let name = cleanName(seg)
            return name.isEmpty ? nil : MealItem(name: name, codes: allergenCodes(seg))
        }
    }

    /// 응답 JSON → 하루 식단. 형식이 맞지 않거나 비면 nil.
    static func parse(_ data: Data) -> DayMeals? {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let result = root["result"] as? [String: Any],
              let meal = result["hanaMeal"] as? [String: Any] else { return nil }
        var items: [Meal: [MealItem]] = [:]
        var photos: [Meal: String] = [:]
        for m in Meal.allCases {
            guard let raw = meal["contents\(m.index)"] as? String, !raw.trimmingCharacters(in: .whitespaces).isEmpty, raw != "null" else { continue }
            let parsed = Self.items(raw)
            guard !parsed.isEmpty else { continue }
            items[m] = parsed
            if let photo = meal["bbs_file\(m.index)"] as? String, !photo.isEmpty, photo != "null" { photos[m] = photo }
        }
        return items.isEmpty ? nil : DayMeals(items: items, photos: photos)
    }

    static func thumbURL(_ file: String?) -> URL? {
        guard let name = file?.trimmingCharacters(in: .whitespaces), !name.isEmpty, name != "null" else { return nil }
        return URL(string: "https://www.hana.hs.kr/data/bbs/0707/thumb/" + name)
    }
}
