import Foundation

/// 학생시간표 페이지의 표 → 주간 시간표. 안드로이드 HanaTimetableSync 의 표 해석과 같은 규칙:
/// 요일 셀이 2개 이상인 행을 머리글로 삼아 열↔요일을 정하고, 그 뒤 각 행의 첫 칸을 교시로 읽습니다.
enum TimetableParser {
    struct Table { let rows: [[String]] }

    // MARK: HTML 표 읽기

    static func tables(in html: String) -> [Table] {
        matches(#"<table[^>]*>(.*?)</table>"#, in: html).compactMap { t in
            let rows = matches(#"<tr[^>]*>(.*?)</tr>"#, in: t).compactMap { r -> [String]? in
                let cells = matches(#"<(?:td|th)[^>]*>(.*?)</(?:td|th)>"#, in: r).map(cellText)
                return cells.isEmpty ? nil : cells
            }
            return rows.isEmpty ? nil : Table(rows: rows)
        }
    }

    private static func matches(_ pattern: String, in s: String) -> [String] {
        let re = try! NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators, .caseInsensitive])
        return re.matches(in: s, range: NSRange(s.startIndex..., in: s)).compactMap { m in
            Range(m.range(at: 1), in: s).map { String(s[$0]) }
        }
    }

    private static func cellText(_ raw: String) -> String {
        var s = raw.replacingOccurrences(of: #"<br\s*/?>"#, with: " ", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: #"</p>"#, with: " ", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: #"<[^>]+>"#, with: " ", options: .regularExpression)
        s = decodeEntities(s)
        return s.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func decodeEntities(_ input: String) -> String {
        var s = input
        for (k, v) in ["&nbsp;": " ", "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&#39;": "'", "&apos;": "'"] {
            s = s.replacingOccurrences(of: k, with: v)
        }
        func replaceNumeric(_ pattern: String, radix: Int) {
            let re = try! NSRegularExpression(pattern: pattern)
            let ns = s as NSString
            var out = ""; var last = 0
            for m in re.matches(in: s, range: NSRange(location: 0, length: ns.length)) {
                out += ns.substring(with: NSRange(location: last, length: m.range.location - last))
                if let code = UInt32(ns.substring(with: m.range(at: 1)), radix: radix), let u = Unicode.Scalar(code) { out.unicodeScalars.append(u) }
                last = m.range.location + m.range.length
            }
            out += ns.substring(from: last)
            s = out
        }
        replaceNumeric(#"&#x([0-9a-fA-F]+);"#, radix: 16)
        replaceNumeric(#"&#(\d+);"#, radix: 10)
        return s
    }

    // MARK: 시간표 해석

    /// 페이지의 표들 중 가장 많은 칸을 채운 것을 시간표로 — 안내 표·범례 표는 배제.
    static func parse(html: String) -> TimetableWeek? {
        tables(in: html).compactMap(parse(table:)).max { count($0) < count($1) }
    }

    private static func count(_ w: TimetableWeek) -> Int { w.lessons.values.reduce(0) { $0 + $1.count } }

    static func parse(table: Table) -> TimetableWeek? {
        let rows = table.rows
        guard let headerIndex = rows.firstIndex(where: { $0.filter { parseDay($0) != nil }.count >= 2 }) else { return nil }
        var dayColumns: [Int: Int] = [:]
        for (col, cell) in rows[headerIndex].enumerated() { if let d = parseDay(cell) { dayColumns[col] = d } }
        guard !dayColumns.isEmpty else { return nil }

        var lessons: [Int: [Int: Lesson]] = [:]
        for row in rows.dropFirst(headerIndex + 1) {
            guard let first = row.first, let period = period(from: first) else { continue }
            for (col, day) in dayColumns {
                let text = (col < row.count ? row[col] : "").trimmingCharacters(in: .whitespaces)
                if text.isEmpty { continue }
                lessons[day, default: [:]][period] = lesson(from: text)
            }
        }
        return lessons.isEmpty ? nil : TimetableWeek(lessons: lessons)
    }

    static func parseDay(_ raw: String) -> Int? {
        let t = raw.uppercased()
        for (k, v) in [("월", 1), ("화", 2), ("수", 3), ("목", 4), ("금", 5), ("토", 6), ("일", 7)] where t.contains(k) { return v }
        for (k, v) in [("MON", 1), ("TUE", 2), ("WED", 3), ("THU", 4), ("FRI", 5), ("SAT", 6), ("SUN", 7)] where t.contains(k) { return v }
        guard let r = t.range(of: #"\d{1,2}"#, options: .regularExpression), let n = Int(t[r]) else { return nil }
        if (1...7).contains(n) { return n }
        return n == 0 ? 7 : nil
    }

    private static func period(from raw: String) -> Int? {
        guard let r = raw.trimmingCharacters(in: .whitespaces).range(of: #"\d{1,2}"#, options: .regularExpression),
              let n = Int(raw.trimmingCharacters(in: .whitespaces)[r]), (1...12).contains(n) else { return nil }
        return n
    }

    /// 빈 칸은 호출부가 거르고, 공강은 [Lesson.free], 나머지는 과목/교실로 나눕니다.
    static func lesson(from raw: String) -> Lesson {
        let text = raw.trimmingCharacters(in: .whitespaces)
        if isFree(text) { return .free }
        let tokens = text.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        if tokens.count <= 1 { return Lesson(subject: text, room: nil) }
        // 교실은 보통 마지막 토큰 — "미적분Ⅱ B303", "스포츠 생활1(남) 체육관".
        if let last = tokens.last, looksLikeRoom(last) { return Lesson(subject: tokens.dropLast().joined(separator: " "), room: last) }
        return Lesson(subject: text, room: nil)
    }

    private static let knownRooms: Set<String> = ["체육관", "강당", "도서관", "생활관", "아트센터", "시청각실", "세미나실", "음악실", "미술실", "무용실", "컴퓨터실", "골프장", "테니스장"]

    /// `B301`·`A205`·`컴퓨터실2`·`체육관`처럼 교실로 보이는 토큰인지 — 과목명 오인을 피하려 좁게.
    static func looksLikeRoom(_ token: String) -> Bool {
        let t = token.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return false }
        if knownRooms.contains(t) { return true }
        if t.range(of: #"^[A-Za-z]-?\d{2,4}$"#, options: .regularExpression) != nil { return true }
        if t.range(of: #"^\d{3,4}$"#, options: .regularExpression) != nil { return true }
        return t.range(of: #"(실|관|층|장)\d*$"#, options: .regularExpression) != nil
    }

    private static func isFree(_ s: String) -> Bool { s.contains("공강") || s.contains("공백") || s.contains("휴강") || s == "없음" }
}
