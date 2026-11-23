import SwiftUI

/*
 * One UI 공통 컴포넌트 — 안드로이드 ui/OneUiComponents.kt 와 같은 규칙: 카드는 28 둥근 서리 판, 버튼은 알약,
 * 다이얼로그 버튼은 아래 한 줄로 반반, 행 사이에만 얇은 구분선.
 */

// MARK: - 그룹

/// 행 구성이 제각각일 때 쓰는 그룹 컨테이너 — 호출부가 OneUiDivider 를 직접 끼워 넣습니다.
struct OneUiGroup<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        OneUiCard(padding: EdgeInsets()) { content }
    }
}

/// 그룹 안 행 사이 구분선 — 왼쪽 여백을 행 텍스트와 맞춥니다.
struct OneUiDivider: View {
    var startIndent: CGFloat = OneUi.rowPadding
    @Environment(\.palette) private var palette
    var body: some View {
        Rectangle().fill(palette.outlineVariant).frame(height: 0.8)
            .padding(.leading, startIndent).padding(.trailing, OneUi.rowPadding)
    }
}

/// 그룹 위 소제목 — 회색 작은 글씨로, 컨테이너 왼쪽 텍스트 라인에 맞춥니다.
struct OneUiSectionTitle: View {
    let text: String
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage
    var body: some View {
        Text(text).font(.ui(.labelLarge)).onPageVariant(palette, onPage)
            .padding(.leading, OneUi.rowPadding).padding(.top, 18).padding(.bottom, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// One UI 메뉴 항목 — 왼쪽 아이콘(선택), 제목 + 보조 텍스트, 오른쪽 트레일링.
struct OneUiListItem<Leading: View, Trailing: View>: View {
    let title: String
    var subtitle: String? = nil
    var titleColor: Color? = nil
    /// 제목 오른쪽 주황 점 — 새 소식.
    var badgeDot = false
    var leadingSize: CGFloat = 24
    var action: (() -> Void)? = nil
    @ViewBuilder var leading: Leading
    @ViewBuilder var trailing: Trailing

    @Environment(\.palette) private var palette

    var body: some View {
        let row = HStack(spacing: 0) {
            if !(leading is EmptyView) {
                leading.frame(width: leadingSize, height: leadingSize).foregroundStyle(palette.onSurface)
                Spacer().frame(width: 16)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .top, spacing: 3) {
                    Text(title).font(.ui(.bodyLarge)).foregroundStyle(titleColor ?? palette.onSurface)
                    if badgeDot { Circle().fill(OneUi.notifyDot).frame(width: 7, height: 7).padding(.top, 3) }
                }
                if let subtitle { Text(subtitle).font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !(trailing is EmptyView) {
                Spacer().frame(width: 12)
                trailing
            }
        }
        .padding(.horizontal, OneUi.rowPadding)
        .padding(.vertical, subtitle == nil ? 18 : 14)
        .contentShape(Rectangle())
        if let action { Button(action: action) { row }.buttonStyle(RowPressStyle()) } else { row }
    }
}

extension OneUiListItem where Leading == EmptyView, Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, titleColor: Color? = nil, badgeDot: Bool = false, action: (() -> Void)? = nil) {
        self.init(title: title, subtitle: subtitle, titleColor: titleColor, badgeDot: badgeDot, action: action, leading: { EmptyView() }, trailing: { EmptyView() })
    }
}

extension OneUiListItem where Leading == EmptyView {
    init(title: String, subtitle: String? = nil, titleColor: Color? = nil, badgeDot: Bool = false, action: (() -> Void)? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.init(title: title, subtitle: subtitle, titleColor: titleColor, badgeDot: badgeDot, action: action, leading: { EmptyView() }, trailing: trailing)
    }
}

/// 오른쪽 화살표(›) — 다른 화면으로 가는 행.
struct Chevron: View {
    @Environment(\.palette) private var palette
    var body: some View { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.onSurfaceVariant.opacity(0.7)) }
}

// MARK: - 버튼

enum OneUiButtonStyle { case filled, neutral, outlined }

/// 알약 버튼 — 굵은 글자. [compact] 는 카드 헤더 옆 작은 버전(높이 36).
struct OneUiButton: View {
    let text: String
    var style: OneUiButtonStyle = .filled
    var enabled = true
    var compact = false
    var leading: AnyView? = nil
    /// 남는 폭을 다 채울지 — 나란한 버튼(취소 | 적용) 등.
    var fill = false
    let action: () -> Void

    init(_ text: String, style: OneUiButtonStyle = .filled, enabled: Bool = true, compact: Bool = false, fill: Bool = false, leading: AnyView? = nil, action: @escaping () -> Void) {
        self.text = text; self.style = style; self.enabled = enabled; self.compact = compact; self.fill = fill; self.leading = leading; self.action = action
    }

    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    var body: some View {
        let neutralBase = onPage ?? palette.onSurface
        // 사진 위(카드 밖)의 보조 버튼은 칩과 같은 서리 알약.
        let frosted = style == .neutral && onPage != nil && !Prefs.shared.performanceMode
        let content: Color = switch style {
        case .filled: palette.onPrimary
        case .neutral: frosted ? palette.onSurface : neutralBase
        case .outlined: palette.primary
        }
        Button(action: action) {
            HStack(spacing: 8) {
                if let leading { leading }
                Text(text).font(.ui(compact ? .labelMedium : .labelLarge, .bold)).lineLimit(1).minimumScaleFactor(0.6)
            }
            .foregroundStyle(content)
            .padding(.horizontal, compact ? 16 : 24)
            .frame(minHeight: compact ? 36 : 46)
            .frame(maxWidth: fill ? .infinity : nil)
            .background {
                let shape = Capsule()
                switch style {
                case .filled: shape.fill(palette.primary)
                case .neutral:
                    if frosted { FrostBackground(shape: shape) }
                    else { shape.fill(neutralBase.opacity(neutralBase.luminance > 0.5 ? 0.14 : 0.07)) }
                case .outlined: shape.strokeBorder(palette.primary, lineWidth: 1.5)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(PressStyle())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}

/// 배경 없는 텍스트 버튼 — 다이얼로그·카드 안의 보조 동작.
struct OneUiTextButton: View {
    let text: String
    var color: Color? = nil
    var enabled = true
    let action: () -> Void

    init(_ text: String, color: Color? = nil, enabled: Bool = true, action: @escaping () -> Void) {
        self.text = text; self.color = color; self.enabled = enabled; self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(text).font(.ui(.labelLarge, .bold)).lineLimit(1)
                .foregroundStyle(color.map { AnyShapeStyle($0) } ?? AnyShapeStyle(.foreground))
                .padding(.horizontal, 14).padding(.vertical, 10)
                .contentShape(Capsule())
        }
        .buttonStyle(PressStyle())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}

/// 상단 오른쪽 버튼 섬 안의 아이콘 버튼 — 40pt, 아이콘 22pt.
struct OneUiActionButton<Label: View>: View {
    var size: CGFloat = 40
    var enabled = true
    let action: () -> Void
    @ViewBuilder var label: Label

    var body: some View {
        Button(action: action) {
            label.font(.system(size: 19, weight: .regular)).frame(width: size, height: size).contentShape(Circle())
        }
        .buttonStyle(PressStyle())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}

/// 버튼 섬 안의 묶음 구분선.
struct OneUiActionDivider: View {
    var height: CGFloat = 18
    @Environment(\.palette) private var palette
    var body: some View { Capsule().fill(.foreground.opacity(palette.dark ? 0.28 : 0.22)).frame(width: 1, height: height).padding(.horizontal, 4) }
}

// MARK: - 선택 컨트롤

/// 선택 칩 — 선택되면 강조색 알약에 흰 굵은 글자, 아니면 글자색의 옅은 판(사진 위에선 서리 알약).
struct OneUiChip: View {
    let label: String
    let selected: Bool
    var leading: AnyView? = nil
    let action: () -> Void

    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    var body: some View {
        let base = onPage ?? palette.onSurface
        let frosted = !selected && onPage != nil && !Prefs.shared.performanceMode
        let content: Color = selected ? palette.onPrimary
            : frosted ? palette.onSurface : base
        Button(action: action) {
            HStack(spacing: 6) {
                if let leading { leading }
                Text(label).font(.ui(.labelMedium, selected ? .bold : .medium)).lineLimit(1)
            }
            .foregroundStyle(content)
            .padding(.horizontal, 16)
            .frame(height: 36)
            .background {
                if selected { Capsule().fill(palette.primary.opacity(0.92)) }
                else if frosted { FrostBackground(shape: Capsule()) }
                else { Capsule().fill(base.opacity(base.luminance > 0.5 ? 0.14 : 0.08)) }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(PressStyle())
    }
}

/// 목록 위의 칩 줄 — 다 안 들어가면 다음 줄로 넘어갑니다.
struct OneUiChipStrip<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View { FlowLayout(spacing: 8, lineSpacing: 8) { content }.frame(maxWidth: .infinity, alignment: .leading) }
}

/// 줄바꿈 배치.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, lineH: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width { x = 0; y += lineH + lineSpacing; lineH = 0 }
            x += size.width + spacing
            maxX = max(maxX, x - spacing)
            lineH = max(lineH, size.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + lineH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, lineH: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX { x = bounds.minX; y += lineH + lineSpacing; lineH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            lineH = max(lineH, size.height)
        }
    }
}

/// One UI 스위치 — 강조색 트랙에 흰 썸, 꺼지면 회색.
struct OneUiSwitch: View {
    @Binding var isOn: Bool
    @Environment(\.palette) private var palette
    var body: some View { Toggle("", isOn: $isOn).labelsHidden().tint(palette.primary) }
}

/// 라디오 — 선택은 강조색 링 + 점, 비선택은 회색 링.
struct OneUiRadio: View {
    let selected: Bool
    @Environment(\.palette) private var palette
    var body: some View {
        ZStack {
            Circle().strokeBorder(selected ? palette.primary : OneUi.switchOff, lineWidth: 2)
            if selected { Circle().fill(palette.primary).frame(width: 11, height: 11) }
        }
        .frame(width: 22, height: 22)
        .animation(.spring(duration: 0.2), value: selected)
    }
}

/// 라디오 행 — "라이트 / 다크" 같은 선택 항목.
struct OneUiRadioRow: View {
    let selected: Bool
    let label: String
    var subtitle: String? = nil
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                OneUiRadio(selected: selected)
                VStack(alignment: .leading, spacing: 1) {
                    Text(label).font(.ui(.bodyLarge)).foregroundStyle(palette.onSurface)
                    if let subtitle { Text(subtitle).font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant) }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, OneUi.rowPadding)
            .padding(.vertical, subtitle == nil ? 15 : 12)
        }
        .buttonStyle(RowPressStyle())
    }
}

/// One UI 슬라이더 — 두꺼운 둥근 트랙(강조색 채움 / 회색) 위에 트랙과 거의 같은 지름의 손잡이(배경색 + 강조색 테두리).
struct OneUiSlider: View {
    @Binding var value: Double
    var range: ClosedRange<Double> = 0...1
    var step: Double? = nil
    var onEditingChanged: (Bool) -> Void = { _ in }
    @Environment(\.palette) private var palette

    var body: some View {
        GeometryReader { g in
            let span = range.upperBound - range.lowerBound
            let fraction = span <= 0 ? 0 : ((value - range.lowerBound) / span).clamped(0, 1)
            let thumb: CGFloat = 26
            let x = (g.size.width - thumb) * fraction
            ZStack(alignment: .leading) {
                Capsule().fill(OneUi.sliderInactive.opacity(0.45)).frame(height: 22)
                Capsule().fill(palette.primary).frame(width: max(22, x + thumb), height: 22)
                Circle().fill(palette.dark ? Color.black : Color.white)
                    .overlay(Circle().strokeBorder(palette.primary, lineWidth: 3))
                    .frame(width: thumb, height: thumb)
                    .offset(x: x)
            }
            .frame(height: g.size.height)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { d in
                    onEditingChanged(true)
                    var v = range.lowerBound + Double(((d.location.x - thumb / 2) / max(1, g.size.width - thumb)).clamped(0, 1)) * span
                    if let step, step > 0 { v = (v / step).rounded() * step }
                    value = v.clamped(range.lowerBound, range.upperBound)
                }
                .onEnded { _ in onEditingChanged(false) })
        }
        .frame(height: 34)
    }
}

/// One UI 입력란 — 아래 밑줄 하나, 포커스되면 강조색으로 두꺼워집니다.
struct OneUiTextField: View {
    let label: String
    @Binding var text: String
    var secure = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType? = nil
    var submitLabel: SubmitLabel = .next
    var onSubmit: () -> Void = {}

    @FocusState private var focused: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.ui(.labelMedium)).foregroundStyle(focused ? palette.primary : palette.onSurfaceVariant)
            Group {
                if secure { SecureField("", text: $text) } else { TextField("", text: $text) }
            }
            .font(.ui(.bodyLarge))
            .foregroundStyle(palette.onSurface)
            .keyboardType(keyboard)
            .textContentType(contentType)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(submitLabel)
            .onSubmit(onSubmit)
            .focused($focused)
            Rectangle().fill(focused ? palette.primary : palette.outline).frame(height: focused ? 2 : 1)
        }
        .padding(.vertical, 6)
    }
}

/// 작은 알약 배지 — D-day, "마감" 같은 짧은 라벨.
struct OneUiBadge: View {
    let text: String
    var container: Color? = nil
    var content: Color? = nil
    @Environment(\.palette) private var palette
    var body: some View {
        Text(text).font(.ui(.labelSmall, .bold)).lineLimit(1).fixedSize()
            .foregroundStyle(content ?? palette.onPrimaryContainer)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(Capsule().fill(container ?? palette.primaryContainer))
    }
}

/// 로딩 스피너 — 강조색, 얇은 선.
struct OneUiLoading: View {
    var size: CGFloat = 26
    var stroke: CGFloat = 2.5
    var color: Color? = nil
    @Environment(\.palette) private var palette
    @State private var spin = false

    var body: some View {
        Circle().trim(from: 0, to: 0.72)
            .stroke(color ?? palette.primary, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
            .frame(width: size, height: size)
            .rotationEffect(.degrees(spin ? 360 : 0))
            .onAppear { withAnimation(.linear(duration: 0.9).repeatForever(autoreverses: false)) { spin = true } }
    }
}

// MARK: - 상태 블록

/// 카드 안 빈 상태 한 줄.
struct DashboardEmpty: View {
    let text: String
    init(_ text: String) { self.text = text }
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage
    var body: some View { Text(text).font(.ui(.bodySmall)).onPageVariant(palette, onPage) }
}

struct LoadingBlock: View {
    var body: some View { OneUiLoading(size: 28, stroke: 3).frame(maxWidth: .infinity).padding(.vertical, 32) }
}

struct EmptyBlock: View {
    let text: String
    init(_ text: String) { self.text = text }
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage
    var body: some View { Text(text).font(.ui(.bodyLarge)).onPageVariant(palette, onPage).padding(.vertical, 32).frame(maxWidth: .infinity, alignment: .leading) }
}

/// 학사 데이터를 못 받을 때 — 계정이 없으면 '계정 등록', 아니면 '다시 시도'.
struct AcademicErrorBlock: View {
    let error: String
    let onRetry: () -> Void
    let onOpenAccount: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let needsLogin = error == academicNeedsLogin
        OneUiCard {
            Text(needsLogin ? academicNeedsLogin : "불러오지 못했습니다").font(.ui(.titleMedium, .bold))
            Text(needsLogin ? "하이하나 계정을 등록하면 학사일정과 게시판을 볼 수 있습니다." : error)
                .font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant).padding(.top, 4)
            Group {
                if needsLogin { OneUiButton("계정 등록", action: onOpenAccount) } else { OneUiButton("다시 시도", style: .neutral, action: onRetry) }
            }
            .padding(.top, 14)
        }
        .padding(.vertical, 12)
    }
}
