import Foundation

/// 날씨 — 평소엔 하나고등학교, 귀가 기간엔 서울특별시. 앱은 요약 서버의 `/weather?place=` 만 부르고, 서버가 기상청 단기예보에서
/// 곳마다 받아 30분씩 보관해 모두에게 나눠 줍니다(사용자 위치는 보내지 않고, 두 곳 중 어디인지만).
enum Weather {
    private static let endpoint = "https://hihana-summary.kyoohan0711ultra.workers.dev/weather"
    private static let ttl: TimeInterval = 30 * 60

    enum Place: String { case hana, seoul
        var label: String { self == .hana ? "하나고등학교" : "서울특별시" }
        var cacheName: String { self == .hana ? "hana_weather" : "seoul_weather" }
    }

    /// [rainText]·[snowText] 는 그 한 시간 강수량·적설 — 기상청 표현 그대로("1mm 미만", "30~50mm"), 안 오면 nil.
    /// 새 칸은 모두 옵셔널이라 이 칸들이 없던 예전 캐시도 그대로 읽힙니다(없는 칸은 nil).
    struct Hour: Codable, Equatable { let time: Date; let temp: Double?; let code: Int?; let pop: Int?; let rainText: String?; let snowText: String? }
    /// [rain](mm)·[snow](cm) 는 그날 시간별 예보의 합(대략) — 없으면 nil.
    struct Day: Codable, Equatable { let date: LocalDate; let code: Int?; let max: Double?; let min: Double?; let pop: Int?; let rain: Double?; let snow: Double? }
    struct Forecast: Codable, Equatable {
        let currentTemp: Double?
        let currentCode: Int?
        let hours: [Hour]
        let days: [Day]
        /// 지난 한 시간 동안 실제로 온 비(mm, 기상청 실황) — 안 왔으면 nil.
        let currentRain: Double?
        /// 지금 체감온도(°C) — 서버가 기상청 계산식으로(10~4월은 바람, 5~9월은 습도).
        let currentFeels: Double?
        /// 지금 습도(%).
        let currentHumidity: Int?
        /// 지금 풍속(m/s)과 바람이 불어오는 쪽(°, 0 = 북).
        let currentWind: Double?
        let currentWindDir: Int?
        /// "kma"(기상청) 또는 "open-meteo"(기상청 장애 때 대신).
        let source: String?
        let updated: Date?
    }

    /// 30분 안이면 그대로, 아니면 새로. 실패하면 남은 캐시.
    static func get(_ place: Place, force: Bool = false) async -> Forecast? {
        let stored = JSONCache.read(Forecast.self, place.cacheName)
        if !force, let stored, JSONCache.isFresh(stored.at, ttl: ttl) { return stored.value }
        if let fresh = try? await fetch(place) { JSONCache.write(fresh, place.cacheName); return fresh }
        return stored?.value
    }

    /// 네트워크 없이 캐시만.
    static func cached(_ place: Place) -> Forecast? { JSONCache.read(Forecast.self, place.cacheName)?.value }

    private static func fetch(_ place: Place) async throws -> Forecast {
        var req = URLRequest(url: URL(string: "\(endpoint)?place=\(place.rawValue)")!)
        req.timeoutInterval = 15
        req.setValue("hihana-neo", forHTTPHeaderField: "x-app")
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200, let f = decode(data) else { throw PortalError.unexpected("weather") }
        return f
    }

    private static let localTime: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = Seoul.zone
        f.dateFormat = "yyyy-MM-dd'T'HH:mm"
        return f
    }()

    static func decode(_ data: Data) -> Forecast? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        func num(_ o: [String: Any], _ k: String) -> Double? { (o[k] as? NSNumber)?.doubleValue }
        func int(_ o: [String: Any], _ k: String) -> Int? { (o[k] as? NSNumber)?.intValue }
        func text(_ o: [String: Any], _ k: String) -> String? { (o[k] as? String).flatMap { $0.isEmpty ? nil : $0 } }
        let current = json["current"] as? [String: Any] ?? [:]
        let hours: [Hour] = (json["hourly"] as? [[String: Any]] ?? []).compactMap { o in
            guard let t = o["t"] as? String, let time = localTime.date(from: String(t.prefix(16))) else { return nil }
            return Hour(time: time, temp: num(o, "temp"), code: int(o, "code"), pop: int(o, "pop"),
                        rainText: text(o, "rainText"), snowText: text(o, "snowText"))
        }
        let days: [Day] = (json["daily"] as? [[String: Any]] ?? []).compactMap { o in
            guard let d = (o["date"] as? String).flatMap(LocalDate.init(iso:)) else { return nil }
            return Day(date: d, code: int(o, "code"), max: num(o, "max"), min: num(o, "min"), pop: int(o, "pop"),
                       rain: num(o, "rain"), snow: num(o, "snow"))
        }
        let updated = (json["updated"] as? String).flatMap { ISO8601DateFormatter().date(from: $0) ?? ISO8601DateFormatter.withFraction.date(from: $0) }
        return Forecast(currentTemp: num(current, "temp"), currentCode: int(current, "code"), hours: hours, days: days,
                        currentRain: num(current, "rain").flatMap { $0 > 0 ? $0 : nil },
                        currentFeels: num(current, "feels"),
                        currentHumidity: num(current, "humidity").map { Int($0.rounded()) },
                        currentWind: num(current, "wind"),
                        currentWindDir: num(current, "windDir").map { Int($0.rounded()) },
                        source: (json["source"] as? String).flatMap { $0.isEmpty ? nil : $0 }, updated: updated)
    }

    // MARK: 날씨 코드 (WMO)

    enum Kind: Int { case clear, partly, cloudy, fog, rain, snow, storm }

    static func kind(_ code: Int?) -> Kind {
        guard let code else { return .clear }
        switch code {
        case 0: return .clear
        case 1, 2: return .partly
        case 3: return .cloudy
        case 45, 48: return .fog
        case 51...67, 80...82: return .rain
        case 71...77, 85, 86: return .snow
        case 95...99: return .storm
        default: return .cloudy
        }
    }

    /// 기상청 표현에 맞춘 이름.
    static func label(_ code: Int?) -> String {
        switch kind(code) {
        case .clear: "맑음"
        case .partly: code == 2 ? "구름많음" : "구름 조금"
        case .cloudy: "흐림"
        case .fog: "안개"
        case .rain:
            switch code ?? 0 { case 80...82: "소나기"; case 51...57: "빗방울"; case 66, 67: "진눈깨비"; default: "비" }
        case .snow: code == 77 ? "눈날림" : "눈"
        case .storm: "뇌우"
        }
    }

    /// SF Symbol — 여러 색(multicolor)으로 그립니다.
    static func symbol(_ code: Int?) -> String {
        switch kind(code) {
        case .clear: "sun.max.fill"
        case .partly: "cloud.sun.fill"
        case .cloudy: "cloud.fill"
        case .fog: "cloud.fog.fill"
        case .rain: "cloud.rain.fill"
        case .snow: "cloud.snow.fill"
        case .storm: "cloud.bolt.rain.fill"
        }
    }

    /// 비·눈이 오거나 강수 확률이 50% 이상인지.
    static func wet(_ code: Int?, _ pop: Int?) -> Bool { [.rain, .snow, .storm].contains(kind(code)) || (pop ?? 0) >= 50 }

    /// 젖는 날씨의 대표 코드 — 비·눈을 우선합니다.
    static func worst(_ codes: [Int?]) -> Int? { codes.compactMap { $0 }.max { kind($0).rawValue * 100 + $0 < kind($1).rawValue * 100 + $1 } }

    /// "17°".
    static func deg(_ v: Double?) -> String? { v.map { "\(Int(($0).rounded()))°" } }

    // MARK: 지금 — 체감온도·습도·바람 (안드로이드 WeatherScreen.IndexTiles 와 같은 기준)

    /// 체감온도 밑줄 — 화면에 보이는 두 숫자(반올림한 체감·실제 기온)의 차이로. 실제 기온이 없으면 "비슷해요".
    static func feelsNote(feels: Double, temp: Double?) -> String {
        let gap = Int(feels.rounded()) - Int((temp ?? feels).rounded())
        if gap <= -1 { return "실제보다 \(-gap)° 낮아요" }
        if gap >= 1 { return "실제보다 \(gap)° 높아요" }
        return "실제 기온과 비슷해요"
    }

    /// 습도 한마디 — 35% 이하 건조, 60% 이하 적당, 80% 이하 습함, 그 위는 매우 습함.
    static func humidityNote(_ h: Int) -> String {
        switch h {
        case ...35: "건조해요"
        case ...60: "적당해요"
        case ...80: "습해요"
        default: "매우 습해요"
        }
    }

    /// 바람 세기 — 기상청 풍속 구분(0.5 / 4 / 9 / 14 m/s).
    static func windStrength(_ w: Double) -> String {
        switch w {
        case ..<0.5: "거의 없어요"
        case ..<4: "약한 바람"
        case ..<9: "약간 강한 바람"
        case ..<14: "강한 바람"
        default: "매우 강한 바람"
        }
    }

    /// 바람이 불어오는 쪽(°, 0 = 북) → "북서풍" — 8방위.
    static func windFrom(_ deg: Int) -> String {
        ["북", "북동", "동", "남동", "남", "남서", "서", "북서"][((deg % 360 + 360) % 360 + 22) / 45 % 8] + "풍"
    }

    /// 바람 밑줄 — "서풍 · 약간 강한 바람". 바람이 거의 없으면(0.5m/s 미만) 방향은 뺍니다.
    static func windNote(speed: Double, dir: Int?) -> String {
        [speed >= 0.5 ? dir.map(windFrom) : nil, windStrength(speed)].compactMap { $0 }.joined(separator: " · ")
    }

    /// "4.1m/s" / "4m/s" — 소수 한 자리, ".0"은 뗌.
    static func speed(_ w: Double) -> String { oneDecimal(w) + "m/s" }

    /// 강수량 — 1mm 미만은 "1mm 미만", 그 위는 소수 한 자리까지(".0"은 뗌).
    static func mm(_ v: Double) -> String { v < 1 ? "1mm 미만" : oneDecimal(v) + "mm" }

    /// 적설 — 1cm 미만은 "1cm 미만".
    static func cm(_ v: Double) -> String { v < 1 ? "1cm 미만" : oneDecimal(v) + "cm" }

    private static func oneDecimal(_ v: Double) -> String {
        let s = String(format: "%.1f", v)
        return s.hasSuffix(".0") ? String(s.dropLast(2)) : s
    }
}

extension ISO8601DateFormatter {
    static let withFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
}
