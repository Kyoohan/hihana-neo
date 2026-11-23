import SwiftUI
import UIKit

/// One UI 디자인 키트에서 뽑은 고정 토큰 — 안드로이드 ui/Theme.kt 의 OneUi 와 같은 값.
enum OneUi {
    /// 카드·다이얼로그·그룹 컨테이너 모서리.
    static let cornerLarge: CGFloat = 28
    static let cornerMedium: CGFloat = 18
    static let cornerSmall: CGFloat = 12
    /// 화면 좌우 여백.
    static let pagePadding: CGFloat = 16
    /// 카드 안쪽 여백.
    static let cardPadding: CGFloat = 20
    /// 리스트 행 좌우 여백.
    static let rowPadding: CGFloat = 20

    static let blue = Color(argb: 0xFF387AFF)
    static let switchOff = Color(argb: 0xFF9A999E)
    static let sliderInactive = Color(argb: 0xFF848487)
    static let lime = Color(argb: 0xFF7CC22F)
    static let sky = Color(argb: 0xFF2FA8F0)
    static let violet = Color(argb: 0xFF8C5BE8)
    /// 설정 항목 옆 알림 점.
    static let notifyDot = Color(argb: 0xFFFF7A1A)
}

/// 설정 화면의 프리셋 8색.
let accentPresets: [UInt32] = [0xFF387AFF, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D55, 0xFFFF3B30, 0xFFFF9500, 0xFF34C759, 0xFF30B0C7]

/// 앱 색 — One UI 무채색 표면 위에 강조색 하나. 안드로이드 oneUiLightScheme/oneUiDarkScheme + withAccent 와 같은 값.
struct Palette: Equatable {
    var dark: Bool
    var primary: Color
    var onPrimary: Color
    var primaryContainer: Color
    var onPrimaryContainer: Color
    var background: Color
    var surface: Color
    var onSurface: Color
    var onSurfaceVariant: Color
    var surfaceContainerHigh: Color
    var outline: Color
    var outlineVariant: Color
    var error: Color
    /// 플로팅 알약 — 카드보다 한 톤 진한 무채색.
    var floatingPill: Color

    static func make(dark: Bool, accentARGB: UInt32) -> Palette {
        let accent = accentARGB == 0 ? UIColor(argb: 0xFF387AFF) : UIColor(argb: accentARGB)
        let onPrimary = accent.luminance > 0.6 ? Color(argb: 0xFF111114) : .white
        if dark {
            return Palette(dark: true,
                           primary: Color(accent.mix(.white, 0.12)), onPrimary: onPrimary,
                           primaryContainer: Color(accent.mix(.black, 0.55)), onPrimaryContainer: Color(accent.mix(.white, 0.80)),
                           background: Color(argb: 0xFF0D0E12), surface: Color(argb: 0xFF17171A), onSurface: Color(argb: 0xFFF4F4F6),
                           onSurfaceVariant: Color(argb: 0xFFA0A0A6), surfaceContainerHigh: Color(argb: 0xFF252528),
                           outline: Color(argb: 0xFF5C5C62), outlineVariant: Color.white.opacity(0.10), error: Color(argb: 0xFFFF5C5C),
                           floatingPill: Color(argb: 0xFF2C2C2F))
        }
        return Palette(dark: false,
                       primary: Color(accent), onPrimary: onPrimary,
                       primaryContainer: Color(accent.mix(.white, 0.82)), onPrimaryContainer: Color(accent.mix(.black, 0.50)),
                       background: Color(argb: 0xFFEDEFF4), surface: Color(argb: 0xFFFCFCFF), onSurface: Color(argb: 0xFF111114),
                       onSurfaceVariant: Color(argb: 0xFF7A7A80), surfaceContainerHigh: Color(argb: 0xFFE9E9EC),
                       outline: Color(argb: 0xFFC4C4C9), outlineVariant: Color.black.opacity(0.08), error: Color(argb: 0xFFE5343A),
                       floatingPill: Color(argb: 0xFFE3E3E7))
    }

    /// 판 밝기가 테마와 반대일 때(어두운 사진이 비치는 서리 카드 등) 글자 계열만 바꾼 색.
    func adapted(onDark: Bool?) -> Palette {
        guard let onDark else { return self }
        var p = self
        if onDark == dark {
            // 사진이 비치는 서리 판 — 테마의 회색 보조 글자는 사진 색과 섞여 묻혔습니다. 본문 색을 옅게 써서 대비를 지킵니다.
            p.onSurfaceVariant = onSurface.opacity(0.74)
            return p
        }
        let ink = onDark ? Color(argb: 0xFFF4F4F7) : Color(argb: 0xFF111114)
        p.onSurface = ink
        p.onSurfaceVariant = ink.opacity(0.76)
        p.outlineVariant = ink.opacity(0.12)
        p.outline = ink.opacity(0.3)
        return p
    }
}

private struct PaletteKey: EnvironmentKey { static let defaultValue = Palette.make(dark: false, accentARGB: 0) }
/// 사진 위(카드 밖)에 바로 놓이는 글자색 — 사진 위쪽 밝기에 따라 흰색/검정. nil 이면 테마색.
private struct OnPageKey: EnvironmentKey { static let defaultValue: Color? = nil }

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
    var onPage: Color? {
        get { self[OnPageKey.self] }
        set { self[OnPageKey.self] = newValue }
    }
}

extension View {
    /// 카드 밖의 보조 글자색 — 사진 위면 사진 밝기 색, 아니면 테마의 회색.
    func onPageVariant(_ palette: Palette, _ onPage: Color?) -> some View { foregroundStyle(onPage?.opacity(0.9) ?? palette.onSurfaceVariant) }
}

// MARK: - 글자

/// 글자 역할 — iOS 글자 단계(Dynamic Type)에 맞춥니다. 설정에서 글자 크기를 키우면 함께 커집니다.
/// 이름은 안드로이드 OneUiTypography 의 역할 이름을 그대로 둬 두 앱을 맞춰 보기 쉽게 했습니다.
enum UiTextStyle {
    case displaySmall, headlineMedium, headlineSmall, titleLarge, titleMedium, titleSmall
    case bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall

    var textStyle: Font.TextStyle {
        switch self {
        case .displaySmall: .largeTitle
        case .headlineMedium: .title
        case .headlineSmall, .titleLarge: .title2
        case .titleMedium: .headline
        case .titleSmall: .callout
        case .bodyLarge: .body
        case .bodyMedium, .labelLarge: .subheadline
        case .bodySmall, .labelMedium: .footnote
        case .labelSmall: .caption
        }
    }

    var weight: Font.Weight {
        switch self {
        case .displaySmall, .headlineMedium, .headlineSmall, .titleLarge: .bold
        case .titleMedium, .titleSmall, .labelLarge: .semibold
        case .labelMedium, .labelSmall: .medium
        default: .regular
        }
    }
}

extension Font {
    static func ui(_ t: UiTextStyle, _ weight: Font.Weight? = nil) -> Font { .system(t.textStyle).weight(weight ?? t.weight) }
}

// MARK: - 색 도우미

extension UIColor {
    convenience init(argb: UInt32) {
        self.init(red: CGFloat((argb >> 16) & 0xFF) / 255, green: CGFloat((argb >> 8) & 0xFF) / 255,
                  blue: CGFloat(argb & 0xFF) / 255, alpha: CGFloat((argb >> 24) & 0xFF) / 255)
    }

    /// 상대 휘도(sRGB 근사).
    var luminance: CGFloat {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        func lin(_ c: CGFloat) -> CGFloat { c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    func mix(_ other: UIColor, _ t: CGFloat) -> UIColor {
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        other.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        return UIColor(red: r1 + (r2 - r1) * t, green: g1 + (g2 - g1) * t, blue: b1 + (b2 - b1) * t, alpha: a1 + (a2 - a1) * t)
    }

    var argb: UInt32 {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return UInt32(a * 255) << 24 | UInt32(r * 255) << 16 | UInt32(g * 255) << 8 | UInt32(b * 255)
    }
}

extension Color {
    func mix(_ other: Color, _ t: CGFloat) -> Color { Color(UIColor(self).mix(UIColor(other), t)) }
    var luminance: CGFloat { UIColor(self).luminance }
}

// MARK: - 블록 종류별 색

extension Accent {
    /// 안드로이드 WidgetKindColors.defaults — 설정에서 바꾼 색이 있으면 그것.
    var defaultARGB: UInt32 {
        switch self {
        case .lesson: 0xFF5B67F1
        case .free: 0xFF14B8A6
        case .breakTime: 0xFFF59E0B
        case .breakfast: 0xFFF97316
        case .lunch: 0xFF22C55E
        case .dinner: 0xFF10B981
        case .snack: 0xFFEC4899
        case .study: 0xFF3B82F6
        case .library: 0xFF92400E
        case .dorm: 0xFF8B5CF6
        case .idle: 0xFF6B7280
        case .afterSchool: 0xFF06B6D4
        case .oneTwo: 0xFFEAB308
        case .outing: 0xFF34B27D
        }
    }

    /// 저장 이름(안드로이드 Accent.name 과 같음).
    var storageName: String {
        switch self {
        case .lesson: "LESSON"
        case .free: "FREE"
        case .breakTime: "BREAK_TIME"
        case .breakfast: "BREAKFAST"
        case .lunch: "LUNCH"
        case .dinner: "DINNER"
        case .snack: "SNACK"
        case .study: "STUDY"
        case .library: "LIBRARY"
        case .dorm: "DORM"
        case .idle: "IDLE"
        case .afterSchool: "AFTER_SCHOOL"
        case .oneTwo: "ONE_TWO"
        case .outing: "OUTING"
        }
    }

    var label: String {
        switch self {
        case .lesson: "수업"
        case .free: "공강"
        case .breakTime: "쉬는 시간"
        case .breakfast: "아침"
        case .lunch: "점심"
        case .dinner: "저녁"
        case .snack: "간식"
        case .study: "면학실·교과교실"
        case .library: "도서관"
        case .dorm: "생활관"
        case .idle: "대기"
        case .afterSchool: "방과후"
        case .oneTwo: "1인2기"
        case .outing: "외출·외박"
        }
    }

    /// 설정에 보이는 순서 — 수업 계열 → 급식 → 면학 장소 → 활동.
    static let ordered: [Accent] = [.lesson, .free, .breakTime, .idle, .breakfast, .lunch, .dinner, .snack, .study, .library, .dorm, .afterSchool, .oneTwo, .outing]

    var color: Color { Color(argb: Prefs.shared.widgetKindColors[storageName] ?? defaultARGB) }
}

// MARK: - 시각

/// 시각 — 시뮬레이터 확인용으로 `-demoNow 14:20` 을 주면 오늘 그 시각인 것처럼 흐릅니다.
enum Clock {
    private static let offset: TimeInterval = {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "-demoNow"), i + 1 < args.count else { return 0 }
        let p = args[i + 1].split(separator: ":").compactMap { Int($0) }
        guard p.count == 2 else { return 0 }
        return LocalDate.today().at(minutes: p[0] * 60 + p[1]).timeIntervalSince(Date())
    }()

    static var now: Date { Date().addingTimeInterval(offset) }
    static func adjust(_ date: Date) -> Date { date.addingTimeInterval(offset) }
}

extension Color {
    /// 0xAARRGGBB 정수(안드로이드 ARGB) → 색.
    init(argb: UInt32) {
        self.init(.sRGB, red: Double((argb >> 16) & 0xFF) / 255, green: Double((argb >> 8) & 0xFF) / 255,
                  blue: Double(argb & 0xFF) / 255, opacity: Double((argb >> 24) & 0xFF) / 255)
    }
}
