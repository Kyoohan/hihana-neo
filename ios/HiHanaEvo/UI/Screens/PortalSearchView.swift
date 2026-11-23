import SwiftUI

// MARK: - 하이하나 통합검색

struct SearchBoard: Equatable { let bmtIdx: Int; let name: String; let total: Int }
struct SearchOverview: Equatable { let query: String; let total: Int; let boards: [SearchBoard] }
struct SearchHit: Equatable, Identifiable {
    let bmtIdx: Int
    let bdIdx: Int
    let boardName: String
    let titleHTML: String
    var id: Int { bdIdx }
    var url: String { "\(PortalClient.base)/main/board/\(bmtIdx)/\(bdIdx)/board_view.do" }
    var plainTitle: String { HTML.decode(titleHTML) }
}

enum SearchOrder: String, CaseIterable { case newest = "INPUTDATEDESC", oldest = "INPUTDATEASC"
    var label: String { self == .newest ? "최신순" : "오래된순" }
}

/// 포털 머리글의 검색과 같은 요청 — 모든 게시판·모든 기간(조회만). 검색어는 2자 이상(포털 규칙).
enum PortalSearchAPI {
    static func overview(_ query: String) async throws -> SearchOverview {
        let text = try await PortalClient.shared.authenticatedText("/main/search.do", params: [("confmKey", "extra1=\(query)"), ("extra1", query)], method: "GET")
        guard let json = try? JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any] else { throw PortalError.rejected("검색 결과를 읽지 못했습니다") }
        let counts = json.dict("groupTotal") ?? [:]
        let boards: [SearchBoard] = (json.array("boardMstList") ?? []).compactMap { o in
            guard let idx = o.int("bmt_idx"), idx >= 0 else { return nil }
            return SearchBoard(bmtIdx: idx, name: o.str("bmt_name") ?? "게시판 \(idx)", total: counts.int("\(idx)") ?? 0)
        }.filter { $0.total > 0 }.sorted { $0.total > $1.total }
        return SearchOverview(query: query, total: json.int("BD_TOT") ?? boards.reduce(0) { $0 + $1.total }, boards: boards)
    }

    static func page(_ query: String, bmt: Int, page: Int, order: SearchOrder) async throws -> (hits: [SearchHit], total: Int) {
        let json = try await PortalClient.shared.authenticatedJSON("/main/search/boardPageJson.json",
                                                                    params: [("extra1", query), ("bmt_idx", "\(bmt)"), ("cp", "\(page)"), ("sortOrder", order.rawValue)],
                                                                    referer: PortalClient.base + "/main/search.do", isValid: { $0["result"] != nil })
        guard json.str("result") == "success" else { throw PortalError.rejected(json.str("resMsg") ?? "검색하지 못했습니다") }
        let data = json.dict("resData") ?? [:]
        let hits: [SearchHit] = (data.array("items") ?? []).compactMap { o in
            guard let bd = o.int("bd_idx"), bd >= 0 else { return nil }
            return SearchHit(bmtIdx: o.int("bmt_idx") ?? bmt, bdIdx: bd, boardName: o.str("bmt_name") ?? "", titleHTML: o.str("bd_title_view") ?? "")
        }
        return (hits, data.int("total") ?? hits.count)
    }
}

/// 최근 검색어 — 이 기기에만 8개까지.
enum RecentSearchStore {
    private static let key = "portal_search.recent"
    static func get() -> [String] { SharedContainer.defaults.stringArray(forKey: key) ?? [] }
    static func add(_ q: String) { SharedContainer.defaults.set(Array(([q] + get().filter { $0 != q }).prefix(8)), forKey: key) }
    static func remove(_ q: String) { SharedContainer.defaults.set(get().filter { $0 != q }, forKey: key) }
    static func clear() { SharedContainer.defaults.removeObject(forKey: key) }
}

/// 포털 강조 `<span class="val">검색어</span>` 를 굵은 강조색으로.
func highlighted(_ html: String, accent: Color) -> Text {
    let re = try! NSRegularExpression(pattern: #"<span class="val">(.*?)</span>"#, options: .dotMatchesLineSeparators)
    func plain(_ s: String) -> String {
        if s.trimmingCharacters(in: .whitespaces).isEmpty { return s.isEmpty ? "" : " " }
        let lead = s.first!.isWhitespace ? " " : "", trail = s.last!.isWhitespace ? " " : ""
        return lead + HTML.decode(s) + trail
    }
    var out = Text("")
    var last = html.startIndex
    for m in re.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
        guard let r = Range(m.range, in: html), let inner = Range(m.range(at: 1), in: html) else { continue }
        out = out + Text(plain(String(html[last..<r.lowerBound])))
        out = out + Text(plain(String(html[inner]))).foregroundColor(accent).bold()
        last = r.upperBound
    }
    return out + Text(plain(String(html[last...])))
}

/// 하이하나 검색 — 시스템 검색창, 검색 전엔 최근 검색어 제안. 결과는 게시판별 구역(구역마다 다섯 건과 '모두 보기'),
/// 위의 범위 단추로 게시판 하나만 고르면 20건씩 더. 정렬은 오른쪽 위 메뉴.
struct PortalSearchView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @State private var input = ""
    @State private var overview: SearchOverview?
    @State private var searching = false
    @State private var error: String?
    @State private var board: Int = -1
    @State private var order: SearchOrder = .newest
    @State private var recent = RecentSearchStore.get()
    @State private var pages: [String: [SearchHit]] = [:]
    @State private var loadingBoards: Set<Int> = []

    var body: some View {
        List {
            if let error {
                Text(error).font(.footnote).foregroundStyle(.red).listRowBackground(Color.clear)
            }
            if searching {
                HStack(spacing: 10) { ProgressView(); Text("하이하나에서 찾는 중").foregroundStyle(.secondary) }.listRowBackground(Color.clear)
            } else if let o = overview {
                if o.total == 0 {
                    ContentUnavailableView.search(text: o.query).listRowBackground(Color.clear)
                } else {
                    results(o)
                }
            } else if !recent.isEmpty {
                Section {
                    ForEach(recent, id: \.self) { q in
                        Button { search(q) } label: { Label(q, systemImage: "clock.arrow.circlepath").foregroundStyle(.primary) }
                            .materialRow()
                    }
                    .onDelete { idx in
                        for i in idx { RecentSearchStore.remove(recent[i]) }
                        recent = RecentSearchStore.get()
                    }
                } header: {
                    HStack {
                        Text("최근 검색어")
                        Spacer()
                        Button("지우기") { RecentSearchStore.clear(); recent = [] }.font(.footnote).textCase(nil)
                    }
                }
            } else {
                ContentUnavailableView("하이하나의 모든 글에서 찾기", systemImage: "magnifyingglass",
                                       description: Text("모든 게시판 · 모든 기간. 검색어는 2자 이상 입력하세요."))
                    .listRowBackground(Color.clear)
            }
        }
        .navigationTitle("하이하나 검색")
        .searchable(text: $input, placement: .navigationBarDrawer(displayMode: .always), prompt: "게시글 제목 검색")
        .onSubmit(of: .search) { search(input) }
        .task {
            if let q = Navigator.shared.pendingSearch {
                Navigator.shared.pendingSearch = nil
                input = q
                search(q)
            }
        }
        .searchScopes($board, activation: .onSearchPresentation) {
            if let o = overview, o.total > 0 {
                Text("전체 \(o.total)").tag(-1)
                ForEach(o.boards, id: \.bmtIdx) { b in Text("\(b.name) \(b.total)").tag(b.bmtIdx) }
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("정렬", selection: $order) {
                        ForEach(SearchOrder.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                } label: { Label("정렬", systemImage: "arrow.up.arrow.down") }
            }
        }
        .onChange(of: input) { _, q in
            if q.isEmpty { overview = nil; error = nil; board = -1 }
        }
        .onChange(of: order) { _, _ in fetchVisible() }
        .onChange(of: board) { _, _ in fetchVisible() }
    }

    @ViewBuilder private func results(_ o: SearchOverview) -> some View {
        if board >= 0 {
            let hits = pages[key(board)] ?? []
            Section {
                if hits.isEmpty { HStack { Spacer(); ProgressView(); Spacer() }.materialRow() }
                ForEach(hits) { h in hitRow(h) }
                if let b = o.boards.first(where: { $0.bmtIdx == board }), !hits.isEmpty, hits.count < b.total {
                    Button(loadingBoards.contains(board) ? "불러오는 중" : "더 보기 · \(b.total - hits.count)건 남음") {
                        Task { await loadMore(o.query, board) }
                    }
                    .disabled(loadingBoards.contains(board))
                    .materialRow()
                }
            } header: { Text("“\(o.query)” 검색 결과 \(o.boards.first { $0.bmtIdx == board }?.total ?? 0)건") }
        } else {
            ForEach(o.boards, id: \.bmtIdx) { b in
                Section("\(b.name) \(b.total)건") {
                    if let hits = pages[key(b.bmtIdx)] {
                        ForEach(hits.prefix(5)) { h in hitRow(h) }
                        if b.total > 5 {
                            Button("\(b.name) 결과 \(b.total)건 모두 보기") { board = b.bmtIdx }.materialRow()
                        }
                    } else {
                        HStack { Spacer(); ProgressView(); Spacer() }.materialRow()
                    }
                }
            }
        }
    }

    private func hitRow(_ h: SearchHit) -> some View {
        RouteLink(Route.boardPost(url: h.url, title: h.plainTitle)) {
            highlighted(h.titleHTML, accent: palette.primary).foregroundStyle(.primary).lineLimit(2)
        }
    }

    private func key(_ bmt: Int) -> String { "\(bmt)-\(order.rawValue)" }

    private func search(_ q: String) {
        let query = q.trimmingCharacters(in: .whitespaces)
        guard query.count >= 2 else { error = "검색어는 2자 이상 입력하세요"; return }
        input = query
        error = nil
        searching = true
        overview = nil
        board = -1
        pages = [:]
        RecentSearchStore.add(query)
        recent = RecentSearchStore.get()
        Task {
            do {
                let o = try await PortalSearchAPI.overview(query)
                overview = o
                searching = false
                await withTaskGroup(of: Void.self) { g in for b in o.boards { g.addTask { await loadMore(query, b.bmtIdx) } } }
            } catch {
                self.error = (error as? PortalError) == .missingCredentials ? "하이하나 계정을 먼저 연동해 주세요" : (error.localizedDescription.components(separatedBy: "\n").first ?? "검색하지 못했습니다")
                searching = false
            }
        }
    }

    private func fetchVisible() {
        guard let o = overview else { return }
        let targets = board >= 0 ? [board] : o.boards.map(\.bmtIdx)
        for t in targets where pages[key(t)] == nil { Task { await loadMore(o.query, t) } }
    }

    @MainActor
    private func loadMore(_ q: String, _ bmt: Int) async {
        guard !loadingBoards.contains(bmt) else { return }
        let k = key(bmt), have = pages[k] ?? []
        loadingBoards.insert(bmt)
        defer { loadingBoards.remove(bmt) }
        do {
            let next = try await PortalSearchAPI.page(q, bmt: bmt, page: have.count / 20 + 1, order: order)
            var seen = Set<Int>()
            pages[k] = (have + next.hits).filter { seen.insert($0.bdIdx).inserted }
        } catch {
            self.error = error.localizedDescription.components(separatedBy: "\n").first
        }
    }
}
