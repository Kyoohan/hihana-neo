import Foundation

/// 한 끼의 '특식' 1~2개 — 홈 급식 칸의 머리 메뉴. 요약 서버(/menu)의 AI 가 고른 것을 먼저 쓰고, 받기 전이나 실패하면 기기 안의
/// 판정으로 고릅니다. 서버로는 메뉴 이름만 보냅니다.
enum MealHighlights {
    private static let endpoint = URL(string: "https://hihana-summary.kyoohan0711ultra.workers.dev/menu")!
    private static let prefix = "meal_highlights."

    private static func key(_ dishes: [String]) -> String { prefix + dishes.joined(separator: "|").stableHash }

    /// 이 메뉴로 받아 둔 AI 결과(없으면 nil).
    static func cached(_ dishes: [String]) -> [String]? {
        let list = (SharedContainer.defaults.stringArray(forKey: key(dishes)) ?? []).filter(dishes.contains)
        return list.isEmpty ? nil : list
    }

    /// AI 에게 묻고 받아 둡니다 — 실패하면 nil(기기 판정을 그대로 씁니다).
    static func fetch(_ dishes: [String]) async -> [String]? {
        if let c = cached(dishes) { return c }
        if CommandLine.arguments.contains("-demo") { return nil }
        var req = URLRequest(url: endpoint)
        req.httpMethod = "POST"
        req.timeoutInterval = 20
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("hihana-neo", forHTTPHeaderField: "x-app")
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["dishes": dishes])
        guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200,
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        let picked = Array((o["highlights"] as? [String] ?? []).filter(dishes.contains).prefix(2))
        if picked.isEmpty { return nil }
        SharedContainer.defaults.set(picked, forKey: key(dishes))
        return picked
    }

    // MARK: 기기 안 판정

    /// 끝이 이러면 밥·국·밑반찬·음료 — 특식이 아닙니다.
    private static let stapleEnd = #"(밥|죽|국|탕|찌개|전골|김치|깍두기|석박지|겉절이|나물|무침|조림|장아찌|젓갈|샐러드|우유|요구르트|요거트|주스|쥬스|두유|음료|누룽지|숭늉|시리얼|김자반|계란찜|달걀찜|볶음|부침|전|떡|빵)$"#
    private static let stapleIn = ["김치", "만두", "단무지", "피클", "소스", "드레싱", "쌈장", "잼", "버터", "생선", "고등어", "꽁치", "삼치", "어묵"]
    private static let fruit = ["사과", "귤", "오렌지", "바나나", "포도", "수박", "참외", "키위", "파인애플", "방울토마토", "딸기", "멜론", "자몽", "복숭아", "배"]

    /// 특식에 가까운 말과 무게 — 메인 요리·디저트일수록 큼.
    private static let special: [(String, Int)] = [
        ("스테이크", 6), ("피자", 6), ("버거", 6), ("폭립", 6), ("장어", 6), ("초밥", 6), ("칠리새우", 6),
        ("치킨", 5), ("탕수육", 5), ("깐풍", 5), ("유린기", 5), ("깐쇼", 5), ("양장피", 5), ("파스타", 5), ("스파게티", 5),
        ("리조또", 5), ("그라탕", 5), ("바베큐", 5), ("바비큐", 5), ("등갈비", 5), ("연어", 5), ("케이크", 5), ("케익", 5),
        ("츄러스", 5), ("타르트", 5), ("마카롱", 5), ("빙수", 5), ("부리또", 5), ("타코", 5),
        ("돈가스", 4), ("돈까스", 4), ("카츠", 4), ("커틀릿", 4), ("강정", 4), ("삼겹", 4), ("떡갈비", 4), ("갈비찜", 4),
        ("함박", 4), ("쌀국수", 4), ("라멘", 4), ("와플", 4), ("아이스크림", 4), ("핫도그", 4), ("닭다리", 4), ("윙", 4), ("북경", 4),
        ("너겟", 3), ("훈제", 3), ("짬뽕", 3), ("냉면", 3), ("샌드위치", 3), ("또띠아", 3), ("새우튀김", 3),
        ("허브", 2), ("크림", 2), ("짜장", 2), ("우동", 2), ("구이", 1), ("튀김", 1),
    ]

    private static func norm(_ s: String) -> String { s.replacingOccurrences(of: #"[\s*()\[\]0-9.]"#, with: "", options: .regularExpression) }

    private static func score(_ name: String, seenDays: Int) -> Int {
        let n = norm(name)
        if n.isEmpty { return -99 }
        var s = special.reduce(0) { $0 + (n.contains($1.0) ? $1.1 : 0) }
        if n.range(of: stapleEnd, options: .regularExpression) != nil || stapleIn.contains(where: n.contains) || fruit.contains(where: { n == $0 || n.hasSuffix($0) }) { s -= 10 }
        // 이번 주에 여러 날 나온 메뉴는 일상 메뉴입니다.
        if seenDays >= 2 { s -= 3 * (seenDays - 1) }
        return s
    }

    /// 특식 점수 3 이상에서 둘까지(둘째는 첫째와 2점 안쪽일 때만). 없으면 밑반찬이 아닌 것 중 가장 높은 하나, 그것도 없으면 첫 메뉴.
    static func pickLocally(_ dishes: [String], history: [DayMeals]) -> [String] {
        guard let first = dishes.first else { return [] }
        var seen: [String: Int] = [:]
        for day in history { for n in Set(day.items.values.flatMap { $0 }.map { norm($0.name) }) { seen[n, default: 0] += 1 } }
        let ranked = dishes.map { ($0, score($0, seenDays: seen[norm($0)] ?? 0)) }.sorted { $0.1 > $1.1 }
        let sp = ranked.filter { $0.1 >= 3 }
        if let top = sp.first {
            if sp.count > 1, sp[1].1 >= top.1 - 2 { return [top.0, sp[1].0] }
            return [top.0]
        }
        return [ranked.first { $0.1 > -5 }?.0 ?? first]
    }
}
