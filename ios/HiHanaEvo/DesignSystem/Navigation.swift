import SwiftUI
import Observation

/// 탭 — 아이폰은 아래 탭 바의 홈 · 타임라인 · 전체. 아이패드는 사이드바에 갈 곳을 모두 두고, 목록 화면은 목록 | 본문 두 칸으로.
enum AppTab: String, Hashable, CaseIterable {
    case home, timeline, all, meal, board, academic, timetable, classroom, apply, alim, weather, settings, search

    var label: String {
        switch self {
        case .home: "홈"
        case .timeline: "타임라인"
        case .all: "전체"
        case .meal: "급식"
        case .board: "게시판"
        case .academic: "학사일정"
        case .timetable: "시간표"
        case .classroom: "클래스룸"
        case .apply: "신청·내역"
        case .alim: "알리미"
        case .weather: "날씨"
        case .search: "통합 검색"
        case .settings: "설정"
        }
    }

    var symbol: String {
        switch self {
        case .home: "house"
        case .timeline: "clock"
        case .all: "square.grid.2x2"
        case .meal: "fork.knife"
        case .board: "newspaper"
        case .academic: "calendar"
        case .timetable: "tablecells"
        case .classroom: "graduationcap"
        case .apply: "list.clipboard"
        case .alim: "tray"
        case .weather: "cloud.sun"
        case .search: "sparkle.magnifyingglass"
        case .settings: "gearshape"
        }
    }

    /// 목록 아이콘(옅은 색 판 안의 색 기호)에 쓰는 채운 기호.
    var tileSymbol: String {
        switch self {
        case .home: "house.fill"
        case .timeline: "clock.fill"
        case .all: "square.grid.2x2.fill"
        case .meal: "fork.knife"
        case .board: "newspaper.fill"
        case .academic: "calendar"
        case .timetable: "tablecells.fill"
        case .classroom: "graduationcap.fill"
        case .apply: "list.clipboard.fill"
        case .alim: "tray.fill"
        case .weather: "cloud.sun.fill"
        case .search: "sparkle.magnifyingglass"
        case .settings: "gearshape.fill"
        }
    }

    /// 목록 아이콘의 색.
    var tint: Color {
        switch self {
        case .home, .timetable, .settings: .blue
        case .timeline: .indigo
        case .all: .gray
        case .meal: .orange
        case .board: Color(argb: 0xFFE0A800)
        case .academic: .red
        case .classroom: .green
        case .apply: .indigo
        case .alim: .pink
        case .weather: .cyan
        case .search: .purple
        }
    }

    /// 이 탭의 첫 화면.
    var root: Route? {
        switch self {
        case .home, .timeline: nil
        case .all: .allPanel
        case .meal: .meal(nil)
        case .board: .board
        case .academic: .academic
        case .timetable: .timetable
        case .classroom: .classroom
        case .apply: .apply
        case .alim: .alim
        case .weather: .weather(until: nil)
        case .search: .search
        case .settings: .settings(nil)
        }
    }

    /// 두 칸 탭의 오른쪽 칸이 비었을 때.
    var emptyDetail: String {
        switch self {
        case .board: "글을 고르세요"
        case .academic: "일정을 고르세요"
        case .classroom: "글을 고르세요"
        case .apply: "신청이나 내역을 고르세요"
        case .alim: "알림을 고르세요"
        case .settings: "항목을 고르세요"
        case .search: "결과를 고르세요"
        default: ""
        }
    }

    /// 아이패드에서 목록 | 본문 두 칸으로 그리는 탭.
    var isSplit: Bool { [.board, .academic, .classroom, .apply, .alim, .settings].contains(self) }

    /// 두 칸 탭에서 오른쪽 칸에 바로 여는 경로 — 그 밖의 경로는 오른쪽 칸 안에서 한 장 더 밀어 넣습니다.
    func selects(_ r: Route) -> Bool {
        switch (self, r) {
        case (.board, .boardPost), (.academic, .boardPost), (.academic, .classroomPost), (.classroom, .classroomPost), (.alim, .web): return true
        case (.settings, .settings(.some)), (.settings, .about), (.settings, .changelog), (.settings, .privacy), (.settings, .licenses), (.settings, .legal): return true
        case (.apply, .history), (.apply, .seats), (.apply, .quickApply), (.apply, .classroomStatus), (.apply, .midnight): return true
        default: return false
        }
    }

    /// 아이패드에서 이 경로를 열면 '전체'에서 열 칸(그 칸의 첫 화면, 또는 그 칸 오른쪽에 여는 것).
    static func owner(of r: Route) -> AppTab? {
        switch r {
        case .allPanel: .all
        // 날짜가 붙은 급식(타임라인의 급식 칸)은 급식 칸으로 옮기지 않고 지금 칸에 — 옮기면 오늘 급식 위에 그날 급식이 한 겹 더 쌓였습니다.
        case .meal(nil): .meal
        case .board: .board
        case .academic: .academic
        case .timetable: .timetable
        case .classroom, .classroomSearch: .classroom
        case .apply, .history, .seats, .quickApply, .classroomStatus, .midnight: .apply
        case .alim: .alim
        case .weather(nil): .weather
        case .search: .search
        case .settings, .about, .changelog, .privacy, .licenses, .legal: .settings
        default: nil
        }
    }
}

/// 아이패드인지 — 두 칸 화면을 쓸지.
@MainActor let isPad = UIDevice.current.userInterfaceIdiom == .pad

/// 앱 전체 화면 흐름 — 탭마다 NavigationStack 경로(두 칸 탭은 오른쪽 칸의 경로), 두 칸 탭의 고른 항목, 그 위에 뜨는 시트 한 장.
@Observable
@MainActor
final class Navigator {
    static let shared = Navigator()

    var tab: AppTab = .home
    /// 아이폰 '전체' 시트가 열렸는지 — 탭으로 옮기지 않고 보던 탭 위에 올립니다(AllSheetView).
    var allOpen = false
    /// 탭마다(아이패드는 '전체' 안의 칸마다) 쌓인 화면.
    var paths: [AppTab: [Route]] = [:]
    /// 아이패드 '전체'에서 오른쪽에 열린 칸(급식·게시판·설정…).
    var section: AppTab?
    /// 두 칸 화면(게시판 등)의 오른쪽에 열린 것.
    var selection: [AppTab: Route] = [:]
    /// 아이패드 창이 좁아(Split View·윈도우 앱을 반쯤 줄인 것 등) 두 칸 화면도 아이폰처럼 한 장씩 밀어 넣는지 — [setNarrow].
    private(set) var sectionNarrow = false
    var sheet: SheetItem?

    /// 아이패드 사이드바에 나열하는 곳(순서대로).
    static let sidebar: [AppTab] = [.home, .timeline, .academic, .board, .apply, .classroom, .timetable, .meal, .alim, .weather, .settings]

    /// 아이패드에서 지금 보이는 곳.
    private var pane: AppTab? { isPad && tab != .all ? tab : nil }

    /// 화면 열기.
    /// - 아이폰: 지금 탭의 스택에 밀어 넣습니다('전체'는 그 탭으로).
    /// - 아이패드: 사이드바의 그곳으로 옮겨 가고, 지금 곳이 두 칸 화면이고 그 목록의 항목이면 오른쪽에,
    ///   그 밖은 지금 곳에 한 장 더.
    func open(_ route: Route) {
        if !isPad {
            switch route {
            case .allPanel: allOpen = true
            default: paths[tab, default: []].append(route)
            }
            return
        }
        if route == .allPanel { return }
        // 지금 칸의 목록 항목 — 오른쪽이 비어 있으면 거기에, 이미 한 장 더 열려 있으면 그 위에(검색 결과에서 글 열기 등).
        if let cur = pane, cur.isSplit, !sectionNarrow, cur.selects(route), route != cur.root {
            // 오른쪽에 이미 무언가 보고 있으면(정보 → 변경 사항 등) 바꿔 끼우지 않고 그 위에 쌓아 '<'로 돌아갈 수 있게.
            if (paths[cur] ?? []).isEmpty && selection[cur] == nil { selection[cur] = route } else { paths[cur, default: []].append(route) }
            return
        }
        if let owner = AppTab.owner(of: route), owner != .all {
            let same = pane == owner
            tab = owner
            if route == owner.root {
                paths[owner] = []
            } else if owner.isSplit && owner.selects(route) && !sectionNarrow {
                selection[owner] = route
                paths[owner] = []
            } else if same {
                paths[owner, default: []].append(route)
            } else {
                paths[owner] = [route]
            }
            return
        }
        if let cur = pane { paths[cur, default: []].append(route) } else { paths[tab, default: []].append(route) }
    }

    /// 목록에서 고르기 — 아이패드 두 칸이면 오른쪽을 그것으로 바꿉니다(그 위에 열린 것은 닫고). 아니면 [open] 과 같습니다.
    func select(_ route: Route) {
        if isPad, let cur = pane, cur.isSplit, !sectionNarrow, cur.selects(route) {
            // 이미 고른 줄을 다시 눌러도 고른 채로 둡니다 — 메일·설정처럼 오른쪽 칸이 비지 않고, 그 위에 열어 둔 화면만 닫혀 처음으로.
            selection[cur] = route
            paths[cur] = []
        } else {
            open(route)
        }
    }

    /// 목록에서 고른 줄인지 — '기타'의 칸 이름이면 그 칸이 열려 있는지, 두 칸 목록의 항목이면 오른쪽에 열려 있는지.
    func isSelected(_ route: Route) -> Bool {
        guard isPad, let cur = pane else { return false }
        if let owner = AppTab.owner(of: route), route == owner.root { return owner == cur }
        return cur.isSplit && !sectionNarrow && selection[cur] == route
    }

    /// 창 폭이 바뀔 때 — 좁아지면 오른쪽 칸에 열려 있던 것을 쌓인 화면 맨 앞으로, 넓어지면 맨 앞의 목록 항목을 오른쪽 칸으로 옮겨
    /// 보던 글이 사라지지 않게 합니다.
    func setNarrow(_ narrow: Bool) {
        guard narrow != sectionNarrow else { return }
        sectionNarrow = narrow
        for t in AppTab.allCases where t.isSplit {
            if narrow {
                if let sel = selection[t] { paths[t] = [sel] + (paths[t] ?? []); selection[t] = nil }
            } else if let first = paths[t]?.first, t.selects(first), first != t.root {
                selection[t] = first
                paths[t] = Array(paths[t]!.dropFirst())
            }
        }
    }

    func path(_ tab: AppTab) -> Binding<[Route]> {
        Binding(get: { self.paths[tab] ?? [] }, set: { self.paths[tab] = $0 })
    }

    var isEmpty: Bool { (paths[tab] ?? []).isEmpty && sheet == nil }



    // MARK: 시트

    /// 시트 띄우기 — 안에서 닫을 땐 환경의 dismissDialog(또는 dismiss).
    func present<V: View>(_ view: V) { sheet = SheetItem(view: AnyView(view)) }
    /// 하이하나 검색을 열 때 바로 찾을 말 — 게시판 검색창에서 넘겨받습니다.
    var pendingSearch: String?
    /// 통합 검색어 — 아이패드는 사이드바 검색창, 아이폰은 통합 검색 화면의 검색창에서 입력합니다.
    var searchQuery = ""
    func dismissSheet() { sheet = nil }
}

/// 목록의 한 줄 — 누르면 그 화면으로(아이폰은 밀어 넣고, 아이패드 두 칸이면 오른쪽 칸에). 고른 줄은 강조색으로 옅게.
struct RouteLink<Label: View>: View {
    let route: Route
    @ViewBuilder var label: Label
    @Environment(\.listPane) private var listPane

    init(_ route: Route, @ViewBuilder label: () -> Label) { self.route = route; self.label = label() }

    var body: some View {
        let selected = Navigator.shared.isSelected(route)
        Button { listPane ? Navigator.shared.select(route) : Navigator.shared.open(route) } label: {
            HStack(spacing: 8) {
                label.frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .foregroundStyle(Color.primary)
        .materialRow(selected: selected)
    }
}

extension RouteLink where Label == Text {
    init(_ title: String, value route: Route) { self.init(route) { Text(title) } }
}

struct SheetItem: Identifiable {
    let id = UUID()
    let view: AnyView
}

private struct ListPaneKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    /// 아이패드 두 칸의 왼쪽 목록 안인지 — 누르면 오른쪽 칸을 바꿉니다.
    var listPane: Bool {
        get { self[ListPaneKey.self] }
        set { self[ListPaneKey.self] = newValue }
    }
}

private struct DismissDialogKey: EnvironmentKey { static let defaultValue: () -> Void = {} }
extension EnvironmentValues {
    /// 지금 시트 닫기.
    var dismissDialog: () -> Void {
        get { self[DismissDialogKey.self] }
        set { self[DismissDialogKey.self] = newValue }
    }
}

extension View {
    /// 배경 사진(없으면 기본 배경)을 화면 뒤에 — 목록의 기본 회색 바탕은 걷어 냅니다.
    func pageBackground() -> some View {
        scrollContentBackground(.hidden)
            .background { PageBackdrop() }
    }

    /// 사진 위 목록 행 — 사진이 비치는 재질. 아이패드 두 칸의 왼쪽 목록은 키트 Sidebar 처럼 바탕 없이, [selected] 인 줄만 회색 캡슐.
    func materialRow(selected: Bool = false) -> some View {
        modifier(MaterialRow(selected: selected))
    }
}

extension View {
    /// 칸 없는 목록(Mail 처럼)의 줄 — 바탕을 비우고, 아이패드 왼쪽 목록에선 고른 줄만 회색 캡슐.
    func plainRow(selected: Bool = false) -> some View { modifier(PlainRow(selected: selected)) }
}

private struct PlainRow: ViewModifier {
    let selected: Bool
    @Environment(\.listPane) private var listPane
    func body(content: Content) -> some View {
        if listPane {
            content.modifier(MaterialRow(selected: selected))
        } else {
            content.listRowBackground(Color.clear)
        }
    }
}

private struct MaterialRow: ViewModifier {
    let selected: Bool
    @Environment(\.listPane) private var listPane

    func body(content: Content) -> some View {
        if listPane {
            // 여러 줄인 글 목록에서도 어색하지 않게 — 캡슐 대신 둥근 네모(Mail 의 고른 글처럼).
            // 고른 줄은 사이드바처럼 리퀴드 글래스 판(iOS 26 부터), 그 전엔 회색 둥근 판.
            // 판은 줄 바탕(listRowBackground)이 아니라 내용 기준으로 그립니다 — 줄 바탕 칸의 여백은 iOS 버전·기기 폭마다 달라
            // (iPadOS 27.2 에서 판 가장자리가 글자에 붙음) 내용 둘레로 늘 12pt 씩 여유를 둡니다(메일의 고른 글처럼).
            content
                .frame(maxWidth: .infinity, alignment: .leading)
                .background { if selected { SelectedRowGlass().padding(.horizontal, -12).padding(.vertical, -7) } }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        } else {
            content.listRowBackground(Rectangle().fill(.regularMaterial))
        }
    }
}

extension View {
    /// 넓은 화면(아이패드)에서 글·카드가 끝없이 늘어나지 않게 가운데 읽기 폭으로.
    func readableWidth(_ max: CGFloat = 760) -> some View {
        frame(maxWidth: max).frame(maxWidth: .infinity)
    }
}

/// 세로 스크롤 화면 — 카드·그래프처럼 목록이 아닌 화면.
struct SheetScroll<Content: View>: View {
    var padding = EdgeInsets(top: 4, leading: 16, bottom: 28, trailing: 16)
    var spacing: CGFloat = 0
    /// 넓은 화면에서의 최대 폭 — 급식처럼 카드를 나란히 놓는 화면은 더 넓게.
    var maxWidth: CGFloat = 820
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: spacing) { content }
                .padding(padding)
                .frame(maxWidth: maxWidth, alignment: .leading)
                .frame(maxWidth: .infinity)
        }
    }
}

/// 목록에서 고른 줄의 바탕 — 사이드바의 고른 줄과 같은 유리.
struct SelectedRowGlass: View {
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        if #available(iOS 26.0, *) {
            Color.clear.glassEffect(.regular.interactive(), in: shape)
        } else {
            shape.fill(Color(uiColor: .systemGray4))
        }
    }
}
