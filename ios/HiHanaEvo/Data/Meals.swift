import Foundation

/// 하이하나 급식 — 로그인 없이 공개된 엔드포인트. 브라우저 없이 부르면 막혀서 Referer·Accept 를 맞춥니다(GET 쿼리로만 JSON).
enum MealClient {
    static func fetch(_ date: LocalDate) async throws -> DayMeals? {
        var c = URLComponents(string: "https://www.hana.hs.kr/daily/hanaMeal.ajax")!
        c.queryItems = [
            URLQueryItem(name: "yy", value: String(format: "%04d", date.year)),
            URLQueryItem(name: "mm", value: String(format: "%02d", date.month)),
            URLQueryItem(name: "dd", value: String(format: "%02d", date.day)),
        ]
        var req = URLRequest(url: c.url!)
        req.timeoutInterval = 10
        req.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        req.setValue("application/json, text/javascript, */*; q=0.01", forHTTPHeaderField: "Accept")
        req.setValue("http://www.hana.hs.kr/daily/cafeteria.do", forHTTPHeaderField: "Referer")
        req.setValue(PortalClient.userAgent, forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: req)
        return MealParser.parse(data)
    }

    /// 끼니 사진 원본(풀사이즈) — 전체 화면 보기용.
    static func fullURL(_ file: String?) -> URL? {
        guard let name = file?.trimmingCharacters(in: .whitespaces), !name.isEmpty, name != "null" else { return nil }
        return URL(string: "https://www.hana.hs.kr/data/bbs/0707/" + name)
    }
}

/// 날짜별 급식 캐시 — 6시간 안이면 네트워크를 건너뜁니다.
enum MealCache {
    static let ttl: TimeInterval = 6 * 3600

    static func load(_ date: LocalDate, maxAge: TimeInterval? = nil) -> DayMeals? {
        guard let e = JSONCache.read(DayMeals.self, "meal_\(date.iso)") else { return nil }
        if let maxAge, !JSONCache.isFresh(e.at, ttl: maxAge) { return nil }
        return e.value
    }

    /// 받아 저장합니다 — 빈 응답으로 있던 캐시를 덮어써 '식단 없음'이 되지 않게(실패로 봅니다).
    static func refresh(_ date: LocalDate) async throws {
        guard let meals = try await MealClient.fetch(date), !meals.items.isEmpty else { return }
        JSONCache.write(meals, "meal_\(date.iso)")
    }
}

/// NEIS 급식 부가 정보 — 끼니별 칼로리·영양성분·원산지·급식 인원.
struct NeisMealInfo: Codable, Equatable {
    struct Pair: Codable, Equatable { let name: String; let value: String }
    var kcal: String?
    var nutrients: [Pair]
    var origins: [Pair]
    var servings: Int?
}

enum NeisMeal {
    private static let endpoint = "https://open.neis.go.kr/hub/mealServiceDietInfo"
    // 하나고등학교 — 서울특별시교육청(B10) · 행정표준코드 7010918.
    private static let office = "B10"
    private static let school = "7010918"
    private static let ttl: TimeInterval = 12 * 3600
    /// 인증키 — 저장소에 두지 않고 빌드 설정(Secrets.xcconfig → Info.plist)에서.
    private static var key: String { (Bundle.main.object(forInfoDictionaryKey: "NEISKey") as? String)?.trimmingCharacters(in: .whitespaces) ?? "" }

    /// 그 날의 끼니별 정보 — 12시간 캐시.
    static func load(_ date: LocalDate, force: Bool = false) async -> [Meal: NeisMealInfo] {
        guard !key.isEmpty, !key.hasPrefix("$(") else { return [:] }
        let name = "neis_meal_\(date.iso)"
        let cached = JSONCache.read([Meal: NeisMealInfo].self, name)
        if let cached, !force, JSONCache.isFresh(cached.at, ttl: ttl) { return cached.value }
        let fresh = try? await fetch(date)
        guard let fresh, !fresh.isEmpty else { return cached?.value ?? [:] }
        JSONCache.write(fresh, name)
        return fresh
    }

    private static func fetch(_ date: LocalDate) async throws -> [Meal: NeisMealInfo] {
        var c = URLComponents(string: endpoint)!
        c.queryItems = [("KEY", key), ("Type", "json"), ("pIndex", "1"), ("pSize", "10"), ("ATPT_OFCDC_SC_CODE", office),
                        ("SD_SCHUL_CODE", school), ("MLSV_YMD", String(format: "%04d%02d%02d", date.year, date.month, date.day))].map { URLQueryItem(name: $0.0, value: $0.1) }
        var req = URLRequest(url: c.url!)
        req.timeoutInterval = 10
        let (data, _) = try await URLSession.shared.data(for: req)
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let info = json["mealServiceDietInfo"] as? [[String: Any]], info.count > 1,
              let rows = info[1]["row"] as? [[String: Any]] else { return [:] }
        var out: [Meal: NeisMealInfo] = [:]
        for row in rows {
            let meal: Meal? = switch row.str("MMEAL_SC_CODE") { case "1": .breakfast; case "2": .lunch; case "3": .dinner; default: nil }
            guard let meal else { continue }
            out[meal] = NeisMealInfo(
                kcal: row.str("CAL_INFO")?.replacingOccurrences(of: "Kcal", with: "", options: .caseInsensitive).trimmingCharacters(in: .whitespaces),
                nutrients: pairs(row.str("NTR_INFO") ?? ""), origins: pairs(row.str("ORPLC_INFO") ?? ""),
                servings: row.str("MLSV_FGR").flatMap(Double.init).map { Int($0) })
        }
        return out
    }

    /// "탄수화물(g) : 111.3<br/>단백질(g) : 36.0" → [(탄수화물(g), 111.3), …]
    private static func pairs(_ raw: String) -> [NeisMealInfo.Pair] {
        raw.replacingOccurrences(of: #"(?i)<br\s*/?>"#, with: "\n", options: .regularExpression).split(separator: "\n").compactMap { line in
            let p = line.split(separator: ":", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            return p.count == 2 && !p[0].isEmpty && !p[1].isEmpty ? NeisMealInfo.Pair(name: p[0], value: p[1]) : nil
        }
    }
}
