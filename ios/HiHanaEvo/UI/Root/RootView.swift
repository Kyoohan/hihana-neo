import SwiftUI

/// 앱 틀 — 아이폰은 아래 탭 홈 · 타임라인 · 전체, 아이패드는 왼쪽 사이드바에 갈 곳 모두.
/// 곳마다 NavigationStack, 목록이 있는 곳은 목록 | 본문, 시트는 그 위.
struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.colorScheme) private var systemScheme
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var prefs = Prefs.shared
    private var background = PageBackground.shared
    @Bindable private var nav = Navigator.shared
    private var toast = ToastCenter.shared

    @State private var onPageColor: Color?
    @State private var started = false
    /// 아이패드 사이드바 검색을 시작하기 전에 보던 칸 — 검색어를 지우면 돌아갑니다.
    @State private var beforeSearch: AppTab?
    /// 세 칸 화면에서 보일 칸 — 시스템 기본(automatic)이 보여 주던 모습을 그대로 적되, 좁은 세로만 바꿉니다:
    /// 가로는 사이드바·목록·본문 셋, 넓은 세로(800pt 이상, Pro 11·13)는 목록·본문 둘, 좁은 세로(iPad mini 744pt)는 기본이 오른쪽 칸 하나만
    /// 보여 목록 없이 '…을 고르세요'만 떴던 것 — 메일 앱 세로처럼 고른 게 없으면 목록을 띄우고, 고르면 목록이 비켜 본문을 다 보이게.
    /// (automatic 으로 되돌리는 방식은 좁은 세로 → 가로로 돌렸을 때 사이드바가 다시 펼쳐지지 않았습니다.)
    @State private var splitColumns: NavigationSplitViewVisibility = .automatic
    @State private var padSize: CGSize = .zero
    /// 백그라운드에 다녀왔는지 — 돌아와 active 가 될 때 한 번 새로 고칩니다.
    @State private var wasBackground = false
    /// 앱을 열 때 띄우는 창 하나 — 서버 공지(15분에 한 번까지 물어 처음 보는 것만) 또는 새 버전.
    @State private var prompt: AppPrompt?
    @Environment(\.openURL) private var openURL
    /// 처음 설치했으면 오프닝과 투어 — 첫 화면부터 덮어 홈이 먼저 비치지 않게. 데모(시뮬레이터 확인용)는 `-tour` 를 줄 때만.
    @State private var showTour = CommandLine.arguments.contains("-demo") ? CommandLine.arguments.contains("-tour") : TourStore.pending

    var body: some View {
        let dark = prefs.appTheme == .dark || (prefs.appTheme == .system && systemScheme == .dark)
        let palette = Palette.make(dark: dark, accentARGB: prefs.accentARGB)
        Group {
            if isPad { padRoot.onGeometryChange(for: CGSize.self) { CGSize(width: $0.size.width.rounded(), height: $0.size.height.rounded()) } action: { padSize = $0 } } else { phoneTabs }
        }
        // 예전 시험 빌드가 받아 둔 오프닝 영상을 지웁니다(지금은 스트리밍만).
        .task { OpeningVideo.removeOldDownloads() }
        // 화면 전체 뒤에도 같은 배경 — 두 칸 화면에서 위 막대 뒤나 열 사이가 비면 검게 보이던 것.
        .background { PageBackdrop() }
        .sheet(item: $nav.sheet) { item in
            item.view
                .environment(\.dismissDialog, { nav.dismissSheet() })
                .environment(\.palette, palette)
                .environment(model)
                .tint(palette.primary)
        }
        .overlay {
            if showTour {
                OnboardingView { withAnimation(.easeInOut(duration: 0.35)) { showTour = false } }
                    .transition(.opacity)
            }
        }
        .overlay { if let t = toast.text { ToastView(text: t) } }
        .environment(\.palette, palette)
        .environment(\.onPage, onPageColor)
        .preferredColorScheme(prefs.appTheme == .system ? nil : (dark ? .dark : .light))
        .tint(palette.primary)
        .task {
            guard !started else { return }
            started = true
            updateOnPage()
            #if DEBUG
            // 시뮬레이터 확인용 — `-axis 1` 은 타임라인, `-route meal` 처럼 화면을 바로 엽니다.
            let args = CommandLine.arguments
            if let i = args.firstIndex(of: "-axis"), i + 1 < args.count, args[i + 1] == "1" { nav.tab = .timeline }
            if let i = args.firstIndex(of: "-route"), i + 1 < args.count { DebugRoutes.open(args[i + 1]) }
            // `-bg solid` 처럼 배경 방식을 바로 바꿔 봅니다.
            if let i = args.firstIndex(of: "-bg"), i + 1 < args.count, let style = Prefs.BackgroundStyle(rawValue: args[i + 1]) { prefs.backgroundStyle = style }
            #endif
            await model.start()
            await checkPrompts()
        }
        .alert(prompt?.title ?? "", isPresented: Binding(get: { prompt != nil }, set: { if !$0 { dismissPrompt() } }), presenting: prompt) { p in
            switch p {
            case .notice(let n):
                if let link = n.link, let button = n.button {
                    Button(button) { openURL(link); dismissPrompt() }
                    Button("닫기", role: .cancel) { dismissPrompt() }
                } else {
                    Button("확인", role: .cancel) { dismissPrompt() }
                }
            case .update(let u):
                Button("업데이트") { AppUpdater.install(u); dismissPrompt() }
                Button("나중에", role: .cancel) { dismissPrompt() }
            }
        } message: { p in
            Text(p.message)
        }
        .onChange(of: background.version) { _, _ in updateOnPage() }
        .onChange(of: prefs.backgroundStyle) { _, _ in updateOnPage() }
        .onChange(of: sizeClass, initial: true) { _, c in if isPad { nav.setNarrow(c == .compact) } }
        .onChange(of: scenePhase) { _, new in
            // 돌아올 땐 background → inactive → active 로 두 번 바뀌어 'old == .background && new == .active' 는 맞지 않을 수 있습니다
            // — 백그라운드에 다녀왔는지를 따로 기억해 두고 active 가 될 때 새로 고칩니다.
            if new == .background { wasBackground = true }
            if new == .active && wasBackground { wasBackground = false; Task { await model.resumed(); await checkPrompts() } }
            // 백그라운드로 갈 때마다 다음 새로 고침을 예약해 둡니다(iOS 가 정한 때에 깨웁니다).
            if new == .background { BackgroundWork.scheduleRefresh(); BackgroundWork.scheduleMorningBrief() }
        }
    }

    /// 투어 중에는 띄우지 않습니다 — 투어를 마치고 다음에 펼칠 때. 공지가 먼저, 없으면 새 버전.
    private func checkPrompts() async {
        guard !showTour, prompt == nil, !CommandLine.arguments.contains("-demo") else { return }
        if let n = await RemoteNoticeStore.pending() { prompt = .notice(n); return }
        await AppUpdater.shared.check()
        if let u = AppUpdater.shared.prompt { prompt = .update(u) }
    }

    private func dismissPrompt() {
        switch prompt {
        case .notice(let n): RemoteNoticeStore.markSeen(n.id)
        case .update(let u): AppUpdater.shared.dismiss(u)
        case nil: break
        }
        prompt = nil
    }

    private func stack<Root: View>(_ tab: AppTab, @ViewBuilder root: () -> Root) -> some View {
        NavigationStack(path: nav.path(tab)) {
            root()
                .pageBackground()
                .navigationDestination(for: Route.self) { RouteView(route: $0).pageBackground() }
        }
    }

    /// 아이폰 — 아래 탭 홈 · 타임라인 · 전체. '전체'는 탭으로 옮기지 않고 시트로 올립니다(안드로이드 ⊞ 처럼) — 탭 바는 보던 칸에 남습니다.
    private var phoneTabs: some View {
        TabView(selection: Binding(get: { nav.tab == .all ? .home : nav.tab }, set: { t in
            if t == .all { nav.allOpen = true } else { nav.tab = t }
        })) {
            Tab(AppTab.home.label, systemImage: AppTab.home.symbol, value: AppTab.home) {
                stack(.home) { HomeView(shown: nav.tab == .home && nav.isEmpty) }
            }
            Tab(AppTab.timeline.label, systemImage: AppTab.timeline.symbol, value: AppTab.timeline) {
                stack(.timeline) { TimeAxisView() }
            }
            // 이 탭의 내용은 보이지 않습니다(누르면 시트) — 자리만.
            Tab(AppTab.all.label, systemImage: AppTab.all.symbol, value: AppTab.all) { Color.clear }
                .badge(model.alimUnread)
        }
        .sheet(isPresented: $nav.allOpen) {
            // 다른 시트(nav.sheet)와 같이 색·모델을 직접 넘깁니다.
            let dark = prefs.appTheme == .dark || (prefs.appTheme == .system && systemScheme == .dark)
            let palette = Palette.make(dark: dark, accentARGB: prefs.accentARGB)
            AllSheetView()
                .environment(model)
                .environment(\.palette, palette)
                .environment(\.onPage, onPageColor)
                .tint(palette.primary)
        }
    }

    /// 아이패드 — 설정 앱과 같은 시스템 사이드바. 목록이 있는 곳(게시판·알리미…)은 메일처럼 사이드바 | 목록 | 본문 세 칸,
    /// 나머지는 사이드바 | 화면 두 칸. 칸마다 제 막대를 가져 제목·단추가 섞이지 않습니다.
    @ViewBuilder private var padRoot: some View {
        let tab = nav.tab
        // 틀(세 칸 | 두 칸)은 검색 중에도 검색 전에 보던 칸을 따릅니다 — 검색어 첫 글자에서 틀이 바뀌면 사이드바가 새로 만들어져
        // 검색창 포커스·키보드가 닫히고 한글 조합(ㅁ+ㅏ)이 끊겼습니다. 세 칸이면 가운데 칸에 검색 결과, 연 결과는 오른쪽에.
        let layout = tab == .search ? (beforeSearch ?? .search) : tab
        // 좁은 창(compact)에선 세 칸이 접혀 오른쪽 칸이 뜨지 않으므로 — 목록을 한 칸 스택으로 그리고 글은 그 위에 밀어 넣습니다.
        if layout.isSplit, !nav.sectionNarrow, let root = layout.root {
            NavigationSplitView(columnVisibility: $splitColumns) {
                sidebar
            } content: {
                NavigationStack {
                    Group {
                        if tab == .search { UnifiedSearchView() } else { RouteView(route: root) }
                    }
                    .environment(\.listPane, true)
                    .pageBackground()
                }
                .navigationSplitViewColumnWidth(min: 320, ideal: 380, max: 460)
            } detail: {
                NavigationStack(path: nav.path(tab)) {
                    Group {
                        if let sel = nav.selection[tab] { RouteView(route: sel).id(sel) }
                        else { ContentUnavailableView(tab.emptyDetail, systemImage: tab.symbol) }
                    }
                    .pageBackground()
                    .navigationDestination(for: Route.self) { RouteView(route: $0).pageBackground() }
                }
            }
            .id("split-\(layout.rawValue)")
            .onAppear { fitColumns(layout) }
            .onChange(of: nav.selection[layout]) { _, _ in fitColumns(layout) }
            .onChange(of: padSize) { _, _ in fitColumns(layout) }
        } else {
            NavigationSplitView {
                sidebar
            } detail: {
                Group {
                    switch tab {
                    case .home: stack(.home) { HomeView(shown: nav.isEmpty) }
                    case .timeline: stack(.timeline) { TimeAxisView() }
                    case .search: stack(.search) { UnifiedSearchView() }
                    default: stack(tab) { if let root = tab.root { RouteView(route: root) } }
                    }
                }
                .id(tab)
                .background { PageBackdrop() }
            }
        }
    }

    /// 세 칸 화면의 보일 칸 — splitColumns 설명 참고.
    private func fitColumns(_ layout: AppTab) {
        guard padSize.width > 0 else { return }   // 크기를 알기 전엔 시스템에 맡김
        let want: NavigationSplitViewVisibility =
            padSize.width > padSize.height ? .all
            : padSize.width >= 800 ? .doubleColumn
            : (nav.selection[layout] == nil ? .doubleColumn : .detailOnly)
        if splitColumns != want { splitColumns = want }
    }

    private var sidebar: some View {
        List(selection: Binding<AppTab?>(get: { nav.tab }, set: { if let t = $0 { nav.tab = t } })) {
            ForEach(Navigator.sidebar, id: \.self) { t in
                Label(t.label, systemImage: t.symbol)
                    .badge(t == .alim ? model.alimUnread : 0)
                    .tag(t)
            }
        }
        .listStyle(.sidebar)
        // 한 화면에 다 들어오면 튕기며 스크롤되지 않게(넘칠 때만 스크롤).
        .scrollBounceBehavior(.basedOnSize)
        // 맨 위는 앱 이름 대신 통합 검색 — 설정 앱처럼 사이드바의 시스템 검색창. 입력하면 오른쪽에 결과.
        .searchable(text: $nav.searchQuery, placement: .sidebar, prompt: "통합 검색")
        // 검색어를 지우거나 취소하면 검색 전에 보던 칸으로(설정 앱처럼).
        .onChange(of: nav.searchQuery) { _, q in
            // 시작할 때와 지울 때 검색 칸에 쌓인 화면을 비웁니다 — 지난 검색에서 연 글이 새 결과 대신 먼저 보이지 않게.
            if !q.isEmpty && nav.tab != .search { beforeSearch = nav.tab; nav.paths[.search] = []; nav.tab = .search }
            else if q.isEmpty && nav.tab == .search, let back = beforeSearch { nav.tab = back; beforeSearch = nil; nav.paths[.search] = [] }
        }
        .toolbar(removing: .title)
        .navigationSplitViewColumnWidth(min: 220, ideal: 250, max: 300)
    }

    private func updateOnPage() {
        guard let blurred = background.blurred else { onPageColor = nil; return }
        onPageColor = PageBackground.onPageColor(window: UIScreen.main.bounds.size, image: blurred)
    }
}

private struct ToastView: View {
    let text: String
    @Environment(\.palette) private var palette
    var body: some View {
        VStack {
            Spacer()
            Text(text).font(.ui(.bodyMedium)).foregroundStyle(palette.dark ? Color.black : Color.white)
                .padding(.horizontal, 18).padding(.vertical, 12)
                .background(Capsule().fill(palette.dark ? Color.white.opacity(0.92) : Color.black.opacity(0.82)))
                .padding(.horizontal, 24).padding(.bottom, 110)
        }
        .transition(.opacity)
        .allowsHitTesting(false)
    }
}

/// 업데이트 뒤 설정에 처음 들어갈 때까지 톱니에 'N'.
@Observable
final class WhatsNew {
    static let shared = WhatsNew()
    private(set) var pending = false
    private static var current: String { (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "" }

    private init() {
        let seen = SharedContainer.defaults.string(forKey: "whats_new.seen")
        pending = seen != nil && seen != Self.current
        if seen == nil { markSeen() }
    }

    func markSeen() {
        pending = false
        SharedContainer.defaults.set(Self.current, forKey: "whats_new.seen")
    }
}



/// 앱을 열 때 뜨는 알림 창의 내용.
enum AppPrompt: Equatable {
    case notice(RemoteNotice)
    case update(AppUpdate)

    var title: String {
        switch self {
        case .notice(let n): n.title
        case .update(let u): "새 버전 \(u.version)"
        }
    }

    var message: String {
        switch self {
        case .notice(let n): return n.body
        case .update(let u):
            // 릴리스 노트 첫 문장만 — 나머지는 설정 › HiHana Evo 정보 › 변경 사항에.
            let first = u.notes.split(separator: "\n").first.map(String.init) ?? ""
            return [first, AppUpdater.hasSideStore ? "SideStore에서 설치합니다. 앱 데이터는 그대로 남습니다." : "릴리스 페이지에서 파일을 받아 설치해 주세요."]
                .filter { !$0.isEmpty }.joined(separator: "\n\n")
        }
    }
}
