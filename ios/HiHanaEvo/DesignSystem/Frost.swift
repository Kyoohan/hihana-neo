import SwiftUI

/// 서리 유리 — iOS 시스템 재질(뒤를 실시간으로 흐림) 위에 옅은 면 색. 안드로이드는 흐린 사진 사본을 카드 위치에 맞춰
/// 직접 그렸지만, iOS 는 재질이 그 일을 합니다. 설정의 서리 진하기는 면 색의 옅기로만 반영합니다.
struct FrostBackground<S: Shape>: View {
    let shape: S
    var tint: Color? = nil

    @Environment(\.palette) private var palette
    @Environment(\.frostDisabled) private var disabled
    private var prefs = Prefs.shared

    init(shape: S, tint: Color? = nil) {
        self.shape = shape
        self.tint = tint
    }

    var body: some View {
        if prefs.performanceMode || disabled {
            // 불투명 서리 페이지 위 카드는 판보다 한 단계 떠 보이게 글자색 쪽으로 살짝, 성능 모드는 거의 불투명한 면.
            shape.fill(disabled ? palette.surface.mix(palette.onSurface, 0.07).opacity(0.92) : palette.surface.opacity(0.94))
        } else {
            ZStack {
                shape.fill(.ultraThinMaterial)
                // 서리 진하기 0.2~0.9 → 재질 위 면 색 0~0.6.
                shape.fill(tint ?? palette.surface.opacity(max(0, (prefs.frostOpacity - 0.2) * 0.86)))
            }
            .environment(\.colorScheme, palette.dark ? .dark : .light)
        }
    }
}

private struct FrostDisabledKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    /// 불투명 서리 페이지 위 — 카드는 서리 대신 거의 불투명한 면으로.
    var frostDisabled: Bool {
        get { self[FrostDisabledKey.self] }
        set { self[FrostDisabledKey.self] = newValue }
    }
}

/// 서리 판 안의 색 규칙 — 판 밝기가 테마와 반대면 글자 계열을 판에 맞춥니다. 사진 위 글자색(onPage)은 여기서 끊습니다.
struct FrostContent: ViewModifier {
    let onDark: Bool?
    @Environment(\.palette) private var palette

    func body(content: Content) -> some View {
        let p = palette.adapted(onDark: onDark)
        content
            .environment(\.palette, p)
            .environment(\.onPage, nil)
            .foregroundStyle(p.onSurface)
            .tint(p.primary)
    }
}

/// 재질 카드의 유리 테두리 — 위는 밝고 아래는 옅게.
struct MaterialEdge<S: InsettableShape>: View {
    let shape: S
    @Environment(\.palette) private var palette

    var body: some View {
        let top = Color.white.opacity(palette.dark ? 0.12 : 0.9)
        shape.strokeBorder(
            LinearGradient(stops: [.init(color: top, location: 0), .init(color: top.opacity(palette.dark ? 0.036 : 0.27), location: 0.45),
                                   .init(color: .black.opacity(0.07), location: 1)], startPoint: .top, endPoint: .bottom),
            lineWidth: 0.75)
    }
}

/// One UI 컨테이너 — 서리 유리 카드(모서리 28), 위쪽 빛 테두리. [color] 를 주면 그 색으로 칠한 카드(시험 D-day 등).
struct OneUiCard<Content: View>: View {
    var color: Color? = nil
    var cornerRadius: CGFloat = OneUi.cornerLarge
    var padding: EdgeInsets = EdgeInsets(top: OneUi.cardPadding, leading: OneUi.cardPadding, bottom: OneUi.cardPadding, trailing: OneUi.cardPadding)
    var spacing: CGFloat = 0
    var action: (() -> Void)? = nil
    @ViewBuilder var content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        let card = VStack(alignment: .leading, spacing: spacing) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .modifier(FrostContent(onDark: nil))
            .background {
                if let color { shape.fill(color) } else { FrostBackground(shape: shape) }
            }
            .overlay { if color == nil { MaterialEdge(shape: shape) } }
            .contentShape(shape)
        if let action {
            Button(action: action) { card }.buttonStyle(PressStyle())
        } else {
            card
        }
    }
}

/// 누르면 살짝 옅어지는 반응 — 안드로이드 clickable 의 물결 대신.
struct PressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.72 : 1)
            .animation(.spring(duration: 0.18), value: configuration.isPressed)
    }
}

/// 줄 하나를 누를 수 있게 — 누르는 동안 옅은 면을 깝니다.
struct RowPressStyle: ButtonStyle {
    @Environment(\.palette) private var palette
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .background(configuration.isPressed ? palette.onSurface.opacity(0.08) : .clear)
    }
}
