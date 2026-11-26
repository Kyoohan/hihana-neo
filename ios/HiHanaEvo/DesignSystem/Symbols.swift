import SwiftUI

/*
 * iOS·iPadOS 27 디자인 키트(Figma 커뮤니티)를 그대로 따르는 기호·버튼.
 * 탭 바는 채운 기호(시스템이 알아서), 목록 줄은 옅은 색 판 안의 색 기호, 시트는 왼쪽 ✕ · 오른쪽 파란 체크 원.
 * iOS 26 부터 있는 것은 그 전 버전에서 글자 버튼으로 대신합니다.
 */

/// 설정 앱처럼 둥근 네모 안의 아이콘 — 단, 꽉 찬 색 판에 흰 기호가 아니라 옅은 색 판에 그 색의 기호를 작게.
struct IconTile: View {
    let symbol: String
    let color: Color
    var size: CGFloat = 30
    init(_ symbol: String, _ color: Color, size: CGFloat = 30) { self.symbol = symbol; self.color = color; self.size = size }
    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.5, weight: .medium))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .background(color.opacity(0.17), in: RoundedRectangle(cornerRadius: size * 0.27, style: .continuous))
    }
}

/// 옅은 색 원 안의 색 기호 — 키트 Empty States 의 가운데 아이콘처럼.
struct TintedCircleIcon: View {
    let symbol: String
    let color: Color
    var size: CGFloat = 36
    var body: some View {
        Image(systemName: symbol).font(.system(size: size * 0.42, weight: .semibold)).foregroundStyle(color)
            .frame(width: size, height: size).background(color.opacity(0.16), in: Circle())
    }
}

/// 시트 왼쪽 위 닫기 — iOS 26 부터는 시스템 ✕ 유리 원.
struct CloseButton: View {
    let action: () -> Void
    var body: some View {
        if #available(iOS 26.0, *) { Button(role: .close, action: action) } else { Button("닫기", action: action) }
    }
}

/// 시트 오른쪽 위 확인(저장·완료) — iOS 26 부터는 시스템 파란 체크 원. [title] 은 손쉬운 사용 이름과 그 전 버전 글자.
struct ConfirmButton: View {
    let title: String
    let action: () -> Void
    init(_ title: String, action: @escaping () -> Void) { self.title = title; self.action = action }
    var body: some View {
        if #available(iOS 26.0, *) {
            Button(role: .confirm, action: action).accessibilityLabel(title)
        } else {
            Button(title, action: action)
        }
    }
}

/// 넓은 주 단추 — iOS 26 부터는 리퀴드 글래스(.glassProminent), 그 전엔 채운 캡슐.
struct WideButton: View {
    let title: String
    var busy = false
    let action: () -> Void
    init(_ title: String, busy: Bool = false, action: @escaping () -> Void) { self.title = title; self.busy = busy; self.action = action }
    var body: some View {
        let label = Group { if busy { ProgressView() } else { Text(title) } }
            .font(.headline).frame(maxWidth: .infinity).frame(height: 30)
        if #available(iOS 26.0, *) {
            Button(action: action) { label }.buttonStyle(.glassProminent).controlSize(.large)
        } else {
            Button(action: action) { label }.buttonStyle(.borderedProminent).buttonBorderShape(.capsule).controlSize(.large)
        }
    }
}

extension View {
    /// 나타날 때 손글씨처럼 획을 따라 그려지는 기호(Draw On). [shown] 이 true 가 되는 순간 그립니다.
    @ViewBuilder func drawOn(_ shown: Bool) -> some View {
        if #available(iOS 26.0, *) { symbolEffect(.drawOn.byLayer, isActive: !shown) } else { self }
    }
}

/// 오른쪽 위 단추 묶음의 한 칸 — 시스템 묶음(ToolbarItemGroup)은 칸마다 약 54pt 를 써서, 단추 셋이면 iPhone 17e(390pt)에서
/// 화면 가운데를 넘었습니다(2026-11-26 기기별 비교). 칸 폭을 40pt 로 고정해 한 ToolbarItem 안의 HStack 에 나란히 둡니다 —
/// 유리 캡슐은 시스템이 그대로 씌웁니다. 배지는 시스템 .badge 와 같은 빨간 알약을 직접 그립니다(HStack 안에선 .badge 가 안 그려짐).
struct BarButton: View {
    let title: String
    let symbol: String
    var badge: Int = 0
    let action: () -> Void

    var body: some View {
        Button(action: action) { BarGlyph(symbol: symbol, badge: badge) }
            .accessibilityLabel(title)
            .accessibilityValue(badge > 0 ? "\(badge)개" : "")
    }
}

/// 단추 칸의 모양 — 메뉴(Menu)의 라벨로도 씁니다.
struct BarGlyph: View {
    let symbol: String
    var badge: Int = 0

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 17, weight: .medium))
            .foregroundStyle(.tint)
            .contentTransition(.symbolEffect(.replace))
            .frame(width: 40, height: 36)
            .overlay(alignment: .topTrailing) {
                if badge > 0 {
                    Text(badge > 99 ? "99+" : "\(badge)")
                        .font(.system(size: 11, weight: .bold)).monospacedDigit().foregroundStyle(.white)
                        .padding(.horizontal, 4).frame(minWidth: 16, minHeight: 16)
                        .background(Color.red, in: Capsule())
                        .offset(x: 2, y: -2)
                }
            }
            .contentShape(Rectangle())
    }
}
