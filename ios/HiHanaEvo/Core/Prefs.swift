import Foundation
import Observation

/// 앱·위젯이 함께 쓰는 저장 위치 — App Group 이 열려 있으면 그쪽, 아니면(서명 없는 빌드) 앱 자신의 공간.
enum SharedContainer {
    /// 원래 App Group. SideStore·AltStore 로 사이드로딩하면 무료 계정에 등록된 다른 ID(예: "group.com.skh.hihanaevo.ABCDE12345")
    /// 로 다시 서명하고 그 값을 Info.plist 의 `ALTAppGroups` 에 적어 두므로, 있으면 그쪽을 씁니다 — 앱과 위젯이 같은 값을 받습니다.
    static let baseGroupID = "group.com.skh.hihanaevo"

    static let groupID: String = {
        let resigned = Bundle.main.object(forInfoDictionaryKey: "ALTAppGroups") as? [String] ?? []
        return resigned.filter { $0.hasPrefix(baseGroupID) }.min(by: { $0.count < $1.count }) ?? baseGroupID
    }()

    static let defaults: UserDefaults = UserDefaults(suiteName: groupID) ?? .standard

    static let directory: URL = {
        if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID) { return url }
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()
}

/// 이름 붙은 JSON 캐시 한 벌 — 안드로이드 PlanStore.cachedJson 과 같은 쓰임(저장 시각을 함께 둬 호출부가 TTL 을 판단).
enum JSONCache {
    private static let dir: URL = {
        let url = SharedContainer.directory.appendingPathComponent("cache", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()

    struct Entry<T> { let value: T; let at: Date }

    static func read<T: Decodable>(_ type: T.Type, _ name: String) -> Entry<T>? {
        let url = dir.appendingPathComponent(name + ".json")
        guard let data = try? Data(contentsOf: url), let value = try? JSONDecoder().decode(type, from: data) else { return nil }
        let at = (try? FileManager.default.attributesOfItem(atPath: url.path)[.modificationDate] as? Date) ?? .distantPast
        return Entry(value: value, at: at)
    }

    static func write<T: Encodable>(_ value: T, _ name: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: dir.appendingPathComponent(name + ".json"), options: .atomic)
    }

    static func remove(_ name: String) {
        try? FileManager.default.removeItem(at: dir.appendingPathComponent(name + ".json"))
    }

    static func isFresh(_ at: Date, ttl: TimeInterval) -> Bool { Date().timeIntervalSince(at) <= ttl }
}

/// 앱 설정 — 안드로이드 PlanStore 의 설정 부분과 같은 키·같은 기본값. 화면이 바로 다시 그려지도록 관찰 가능.
@Observable
final class Prefs {
    static let shared = Prefs()
    private let d = SharedContainer.defaults

    enum Theme: String, CaseIterable { case system, light, dark
        var label: String { switch self { case .system: "시스템"; case .light: "라이트"; case .dark: "다크" } }
    }

    /// 페이지 배경 — 그라데이션(기본, 안드로이드와 같은 세 색), 단색(건강 앱처럼 위쪽만 물듦), 사진.
    enum BackgroundStyle: String, CaseIterable { case gradient, solid, photo
        var label: String { switch self { case .gradient: "그라데이션"; case .solid: "단색"; case .photo: "사진" } }
    }

    enum WidgetAccent: String, CaseIterable { case kind, app, text
        var label: String { switch self { case .kind: "종류별 색"; case .app: "앱 강조 색"; case .text: "글자색과 같게" } }
    }

    /// 0 이면 자동(One UI 파랑).
    var accentARGB: UInt32 { didSet { d.set(Int(accentARGB), forKey: "accentColor") } }
    var appTheme: Theme { didSet { d.set(appTheme.rawValue, forKey: "appTheme") } }
    var studentGrade: Int { didSet { d.set(studentGrade, forKey: "studentGrade") } }
    var allergyCodes: Set<Int> { didSet { d.set(allergyCodes.sorted().map(String.init).joined(separator: ","), forKey: "allergyCodes") } }
    var widgetOpacity: Int { didSet { d.set(widgetOpacity, forKey: "widgetOpacityHome") } }
    var widgetTheme: Theme { didSet { d.set(widgetTheme.rawValue, forKey: "widgetThemeHome") } }
    var widgetAccent: WidgetAccent { didSet { d.set(widgetAccent.rawValue, forKey: "widgetAccentHome") } }
    /// '종류별 색'에서 바꾼 색 — Accent 이름 → ARGB.
    var widgetKindColors: [String: UInt32] { didSet { d.set(widgetKindColors.mapValues { Int($0) }, forKey: "widgetKindColors") } }
    /// 서리 유리 불투명도 — 기본 0.5(배경이 반쯤 비침), 0.2~0.9.
    var frostOpacity: Double { didSet { d.set(frostOpacity, forKey: "frost.alpha") } }
    /// 배경 사진 밝기(-0.4~0.4)·채도(0~1.6) — 기본 -10%·-10%.
    var backgroundBrightness: Double { didSet { d.set(backgroundBrightness, forKey: "bg.brightness_v2") } }
    var backgroundSaturation: Double { didSet { d.set(backgroundSaturation, forKey: "bg.saturation_v2") } }
    var backgroundStyle: BackgroundStyle { didSet { d.set(backgroundStyle.rawValue, forKey: "bg.style") } }
    /// 단색 배경의 색 — 0 이면 강조 색을 따릅니다.
    var backgroundSolidARGB: UInt32 { didSet { d.set(Int(backgroundSolidARGB), forKey: "bg.solid") } }
    /// 하단 바에 탭 이름을 함께 보일지.
    /// 성능 모드 — 흐림 없이 거의 불투명한 면.
    var performanceMode: Bool { didSet { d.set(performanceMode, forKey: "performance.enabled") } }
    var lastSyncAt: Date? { didSet { d.set(lastSyncAt?.timeIntervalSince1970, forKey: "lastSyncAt") } }

    static let defaultBrightness = -0.1
    static let defaultSaturation = 0.9
    static let defaultWidgetOpacity = 45

    private init() {
        accentARGB = UInt32(truncatingIfNeeded: d.integer(forKey: "accentColor"))
        appTheme = Theme(rawValue: d.string(forKey: "appTheme") ?? "") ?? .system
        studentGrade = min(3, max(1, d.object(forKey: "studentGrade") as? Int ?? 2))
        allergyCodes = Set((d.string(forKey: "allergyCodes") ?? "").split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) })
        widgetOpacity = d.object(forKey: "widgetOpacityHome") as? Int ?? Self.defaultWidgetOpacity
        widgetTheme = Theme(rawValue: d.string(forKey: "widgetThemeHome") ?? "") ?? .system
        widgetAccent = WidgetAccent(rawValue: d.string(forKey: "widgetAccentHome") ?? "") ?? .kind
        widgetKindColors = (d.dictionary(forKey: "widgetKindColors") as? [String: Int] ?? [:]).mapValues { UInt32(truncatingIfNeeded: $0) }
        frostOpacity = min(0.9, max(0.2, d.object(forKey: "frost.alpha") as? Double ?? 0.5))
        backgroundBrightness = d.object(forKey: "bg.brightness_v2") as? Double ?? Self.defaultBrightness
        backgroundSaturation = d.object(forKey: "bg.saturation_v2") as? Double ?? Self.defaultSaturation
        // 처음엔 고른 사진이 있으면 사진, 없으면 그라데이션.
        let hadPhoto = FileManager.default.fileExists(atPath: SharedContainer.directory.appendingPathComponent("page_background.jpg").path)
        backgroundStyle = BackgroundStyle(rawValue: d.string(forKey: "bg.style") ?? "") ?? (hadPhoto ? .photo : .gradient)
        backgroundSolidARGB = UInt32(truncatingIfNeeded: d.integer(forKey: "bg.solid"))
        performanceMode = d.bool(forKey: "performance.enabled")
        lastSyncAt = (d.object(forKey: "lastSyncAt") as? Double).map { Date(timeIntervalSince1970: $0) }
    }

    var backgroundAdjustIsDefault: Bool { backgroundBrightness == Self.defaultBrightness && backgroundSaturation == Self.defaultSaturation }
}

/// 그 날 타임별 면학 위치 — 안드로이드 PlanStore 의 payload(날짜 하나만 두고 자정에 비움)를 날짜별로 둡니다.
enum PlaceStore {
    private static let name = "places"

    static func all() -> [String: [String: String]] { JSONCache.read([String: [String: String]].self, name)?.value ?? [:] }

    /// 그 날 저장된 타임별 장소 — 없는 타임은 기본값(생활관 타임만 생활관).
    static func places(for date: LocalDate) -> [PlanSlot: StudyPlace] {
        let stored = all()[date.iso] ?? [:]
        var out: [PlanSlot: StudyPlace] = [:]
        for slot in PlanSlot.allCases {
            if let raw = stored[slot.rawValue], let place = StudyPlace(storageValue: raw) { out[slot] = place }
            else if let place = slot.defaultPlace { out[slot] = place }
        }
        return out
    }

    static func set(_ place: StudyPlace?, slot: PlanSlot, date: LocalDate) {
        var map = all()
        var day = map[date.iso] ?? [:]
        day[slot.rawValue] = place?.storageValue
        map[date.iso] = day
        // 지난 날은 사흘만 남깁니다(자정을 넘긴 심야면학 확인용).
        let keep = Set((-3...7).map { LocalDate.today().adding(days: $0).iso })
        map = map.filter { keep.contains($0.key) }
        JSONCache.write(map, name)
    }
}

extension String {
    /// 실행마다 바뀌지 않는 짧은 해시 — 캐시 키용(Swift 의 hashValue 는 실행마다 달라집니다).
    var stableHash: String {
        var h: UInt64 = 5381
        for b in utf8 { h = (h &<< 5) &+ h &+ UInt64(b) }
        return String(h, radix: 16)
    }
}
