import SwiftUI

/// 유리 판 — 하단 바·헤더 섬·버튼 섬. iOS 26 부터는 시스템 리퀴드 글래스, 그 전엔 서리 재질 + 옅은 틴트(안드로이드 liquidFrostStyle).
struct GlassBackground<S: Shape>: View {
    let shape: S
    var opacity: CGFloat = 1
    @Environment(\.palette) private var palette

    var body: some View {
        if opacity <= 0.001 {
            Color.clear
        } else if Prefs.shared.performanceMode {
            shape.fill((palette.dark ? Color(argb: 0xFF1C1C1E) : .white).opacity(0.96 * opacity))
        } else if #available(iOS 26.0, *) {
            Color.clear.glassEffect(.regular, in: shape).opacity(opacity)
        } else {
            ZStack {
                shape.fill(.ultraThinMaterial)
                shape.fill((palette.dark ? Color(argb: 0xFF16181B) : .white).opacity(0.55))
                shape.stroke(LinearGradient(colors: [.white.opacity(palette.dark ? 0.22 : 0.95), .white.opacity(palette.dark ? 0.04 : 0.3)],
                                            startPoint: .top, endPoint: .bottom), lineWidth: 1)
            }
            .opacity(opacity)
        }
    }
}

/// 숫자 배지 — 알리미 안 읽은 수, 'N'.
struct CountBadge: View {
    let text: String
    var color: Color
    var textColor: Color = .white
    var body: some View {
        Text(text).font(.system(size: 10, weight: .bold)).foregroundStyle(textColor)
            .padding(.horizontal, 4).frame(minWidth: 16, minHeight: 16)
            .background(Capsule().fill(color))
    }
}
