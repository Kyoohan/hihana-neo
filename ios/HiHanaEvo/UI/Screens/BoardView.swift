import SwiftUI
import WebKit

extension BoardCategory {
    /// 세그먼트 칸의 줄임말.
    var shortLabel: String {
        switch self {
        case .studentNotice: "학생"
        case .subjectNotice: "교과"
        case .homeLetter: "가정"
        case .woojeong: "우정"
        case .studentBoard: "게시판"
        }
    }

    var symbol: String {
        switch self {
        case .studentNotice: "bell"
        case .subjectNotice: "book"
        case .homeLetter: "envelope"
        case .woojeong: "bed.double"
        case .studentBoard: "list.bullet"
        }
    }
}

/// 게시판 — Mail 받은 편지함처럼 칸 없이 이어진 목록. 위의 분할 단추로 다섯 게시판을 고르고, 검색창은 늘 보입니다
/// (치는 동안은 이 게시판에서 거르고, 검색을 누르면 하이하나 전체에서 찾습니다). '모두 읽음'은 왼쪽 위.
struct BoardView: View {
    @SharedModel private var model
    @Environment(\.listPane) private var listPane
    @State private var query = ""

    var body: some View {
        let posts = query.isEmpty ? model.boardPosts : model.boardPosts.filter { $0.title.localizedCaseInsensitiveContains(query) }
        List {
            // 아이패드 두 칸에선 분류를 목록 맨 위 줄로 — 위 막대가 본문 칸과 같은 높이여야 흐림 경계가 맞습니다.
            if listPane {
                categoryPicker
                    .listRowSeparator(.hidden).listRowBackground(Color.clear)
            }
            if let error = model.boardError {
                AcademicErrorBlock(error: error, onRetry: { Task { await model.loadBoard(force: true) } }, onOpenAccount: { model.nav.present(AccountDialog()) })
                    .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            } else if model.boardLoading && model.boardPosts.isEmpty {
                LoadingBlock().listRowBackground(Color.clear)
            } else if model.boardPosts.isEmpty {
                ContentUnavailableView("\(model.boardCategory.label) 게시글이 없습니다", systemImage: "tray").listRowBackground(Color.clear)
            } else {
                ForEach(posts) { post in
                    BoardRow(post: post) { model.openBoardPost(post, select: listPane) }
                        .plainRow(selected: model.nav.isSelected(.boardPost(url: post.url, title: post.title)))
                }
            }
        }
        .listStyle(.plain)
        .refreshable { await model.loadBoard(force: true) }
        // 클래스룸과 같은 자리 — 목록 맨 위 제목 아래에 늘 보이는 검색창.
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "하이하나에서 검색")
        .onSubmit(of: .search, submitSearch)
        .modifier(BoardTopBar(enabled: !listPane) { categoryPicker })
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if model.boardUnreadCounts.values.reduce(0, +) > 0 { Button("모두 읽음") { model.markAllBoardsRead() } }
            }
        }
        .task {
            if model.boardPosts.isEmpty { model.boardPosts = AcademicRepository.cachedBoard(model.boardCategory) ?? [] }
            await model.loadBoard(force: false)
            model.refreshBoardCounts(fetchAll: true)
        }
    }
}

extension BoardView {
    /// 검색을 누르면 하이하나 전체에서.
    fileprivate func submitSearch() {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { return }
        model.nav.pendingSearch = q
        model.nav.open(.portalSearch)
    }

    fileprivate var categoryPicker: some View {
        Picker("게시판", selection: Binding(get: { model.boardCategory }, set: { model.selectBoard($0) })) {
            ForEach(BoardCategory.allCases) { c in
                let n = model.boardUnreadCounts[c] ?? 0
                Text(n > 0 ? "\(c.shortLabel) \(n > 99 ? "99+" : "\(n)")" : c.shortLabel).tag(c)
            }
        }
        .pickerStyle(.segmented)
    }
}

/// 분류 띠 — 아이폰은 막대 아래에 붙이고, 아이패드 두 칸에선 목록 안에 둡니다.
private struct BoardTopBar<Bar: View>: ViewModifier {
    let enabled: Bool
    @ViewBuilder var bar: Bar
    func body(content: Content) -> some View {
        if enabled { content.topBar { bar } } else { content }
    }
}

/// 글 한 줄 — 안 읽은 글은 왼쪽 파란 점, 굵은 제목과 오른쪽 날짜·화살표, 그 아래 작은 회색 글쓴이(부서)·대상 학년, AI 한 줄 요약.
private struct BoardRow: View {
    let post: HanaBoardPost
    let action: () -> Void
    @SharedModel private var model

    var body: some View {
        let read = model.boardRead.isRead(post)
        Button(action: action) {
            HStack(alignment: .top, spacing: 8) {
                Circle().fill(read ? AnyShapeStyle(.clear) : AnyShapeStyle(.tint)).frame(width: 10, height: 10).padding(.top, 6)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(post.title.isEmpty ? "(제목 없음)" : post.title).font(.body.weight(read ? .regular : .semibold))
                            .foregroundStyle(Color.primary).lineLimit(2).multilineTextAlignment(.leading)
                        Spacer(minLength: 6)
                        if let when = mailDate(post.date) { Text(when).font(.subheadline).foregroundStyle(Color.secondary).fixedSize() }
                        Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                    let grades = post.grades.isEmpty ? nil : post.grades.map { $0.replacingOccurrences(of: "학년", with: "").trimmingCharacters(in: .whitespaces) }.joined(separator: "·") + "학년"
                    Text([post.writer, grades].compactMap { $0 }.joined(separator: " · ")).font(.subheadline).foregroundStyle(Color.secondary).lineLimit(1)
                    if let line = model.summarizer.line(post.url) { AISummaryPill(text: line).padding(.vertical, 3) }
                }
            }
            .padding(.vertical, 2)
            .contentShape(Rectangle())
        }
    }
}

/// Mail 처럼 짧은 날짜(시각이 있는 Date).
func mailDate(_ d: Date) -> String {
    let date = d.localDate, today = LocalDate.today()
    let days = LocalDate.days(from: date, to: today)
    if days == 0 { return d.hm }
    if days == 1 { return "어제" }
    if days > 1 && days < 7 { return date.weekdayShort + "요일" }
    return date.year == today.year ? "\(date.month). \(date.day)." : "\(date.year % 100). \(date.month). \(date.day)."
}

/// Mail 처럼 짧은 날짜 — 오늘은 시각, 어제는 '어제', 한 주 안은 요일, 그 전은 '10. 21.'.
func mailDate(_ raw: String?) -> String? {
    guard let raw else { return nil }
    let re = try! NSRegularExpression(pattern: #"^(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?"#)
    let t = raw.trimmingCharacters(in: .whitespaces)
    guard let m = re.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)) else { return prettyPortalDate(raw) }
    func g(_ i: Int) -> Int? { Range(m.range(at: i), in: t).flatMap { Int(t[$0]) } }
    guard let y = g(1), let mo = g(2), let d = g(3) else { return prettyPortalDate(raw) }
    let date = LocalDate(year: y, month: mo, day: d)
    let today = LocalDate.today()
    let days = LocalDate.days(from: date, to: today)
    if days == 0, let h = g(4), let mi = g(5) { return String(format: "%d:%02d", h, mi) }
    if days == 1 { return "어제" }
    if days > 1 && days < 7 { return date.weekdayShort + "요일" }
    return y == today.year ? "\(mo). \(d)." : "\(y % 100). \(mo). \(d)."
}

extension View {
    /// 내비게이션 막대 바로 아래에 붙는 띠 — iOS 27 은 스크롤 가장자리 효과가 함께 걸리는 막대, iOS 26 은 바탕 없는 띠, 그 전엔 막대 재질.
    /// iOS 26 에서 safeAreaBar 를 쓰면 가장자리 흐림이 큰 제목까지 덮어 '게시판' 제목이 뿌옇게 깔렸습니다(26.5 시뮬레이터·실기기 26.5.2).
    @ViewBuilder func topBar<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        if #available(iOS 27.0, *) {
            safeAreaBar(edge: .top) { content().padding(.horizontal, 16).padding(.bottom, 8) }
        } else if #available(iOS 26.0, *) {
            safeAreaInset(edge: .top, spacing: 0) { content().padding(.horizontal, 16).padding(.bottom, 8) }
        } else {
            safeAreaInset(edge: .top, spacing: 0) { content().padding(.horizontal, 16).padding(.vertical, 8).background(.bar) }
        }
    }
}

// MARK: - AI 표식

let aiBlue = Color(argb: 0xFF6EA8FF)
let aiViolet = Color(argb: 0xFFA78BFA)
let aiPink = Color(argb: 0xFFF29FC8)
let aiGradient = LinearGradient(colors: [aiBlue, aiViolet, aiPink], startPoint: .topLeading, endPoint: .bottomTrailing)

/// 목록의 한 줄 요약 알약 — 그라데이션 테두리와 옅은 그라데이션 바탕, 최대 두 줄.
struct AISummaryPill: View {
    let text: String
    @Environment(\.palette) private var palette
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        HStack(alignment: .top, spacing: 7) {
            Image(systemName: "sparkle").font(.system(size: 13, weight: .semibold)).foregroundStyle(aiGradient).padding(.top, 2)
            Text(text).font(.ui(.bodyMedium)).foregroundStyle(palette.onSurface.opacity(0.86)).lineLimit(2).multilineTextAlignment(.leading)
        }
        .padding(.leading, 10).padding(.trailing, 12).padding(.vertical, 8)
        .background(shape.fill(LinearGradient(colors: [aiBlue.opacity(0.14), aiViolet.opacity(0.11), aiPink.opacity(0.10)], startPoint: .leading, endPoint: .trailing)))
        .overlay(shape.strokeBorder(aiGradient, lineWidth: 1))
    }
}

/// 글 화면 위 AI 요약 카드 — 한 줄 요약 + 자세한 글머리표, AI 결과물 표시.
struct SummaryCard: View {
    let state: PostSummarizer.State?
    let onRetry: () -> Void
    @Environment(\.palette) private var palette
    @State private var pulse = false

    var body: some View {
        if let state, state != .hidden {
            let shape = RoundedRectangle(cornerRadius: OneUi.cornerLarge, style: .continuous)
            OneUiCard {
                HStack(spacing: 5) {
                    Image(systemName: "sparkle").font(.system(size: 12, weight: .bold))
                    Text("AI 요약").font(.ui(.labelMedium, .bold))
                }
                .foregroundStyle(.white)
                .padding(.leading, 9).padding(.trailing, 11).padding(.vertical, 4)
                .background(Capsule().fill(aiGradient))
                Group {
                    switch state {
                    case .done(let s):
                        Text(s.line).font(.ui(.titleMedium)).padding(.top, 12)
                        if !s.points.isEmpty {
                            Rectangle().fill(palette.onSurface.opacity(0.08)).frame(height: 1).padding(.vertical, 12)
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(s.points, id: \.self) { p in
                                    HStack(alignment: .top, spacing: 9) {
                                        Circle().fill(aiGradient).frame(width: 5, height: 5).padding(.top, 8)
                                        Text(p).font(.ui(.bodyMedium)).foregroundStyle(palette.onSurface.opacity(0.82))
                                    }
                                }
                            }
                        }
                        HStack(alignment: .top, spacing: 6) {
                            Image(systemName: "info.circle").font(.system(size: 12)).foregroundStyle(palette.onSurfaceVariant).padding(.top, 2)
                            Text("생성형 AI가 만든 요약으로, 부정확하거나 원문과 다를 수 있습니다. 중요한 내용은 원문을 확인하세요.")
                                .font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant)
                        }
                        .padding(.top, 14)
                    case .loading:
                        VStack(alignment: .leading, spacing: 9) {
                            ForEach([1.0, 0.86, 0.62], id: \.self) { w in
                                GeometryReader { g in RoundedRectangle(cornerRadius: 6).fill(palette.onSurface.opacity(0.12)).frame(width: g.size.width * w) }.frame(height: 12)
                            }
                        }
                        .opacity(pulse ? 0.8 : 0.35)
                        .onAppear { withAnimation(.easeInOut(duration: 0.9).repeatForever()) { pulse = true } }
                        .padding(.top, 12)
                    case .failed(let m):
                        HStack {
                            Text(m).font(.ui(.bodyMedium)).foregroundStyle(palette.onSurfaceVariant)
                            Spacer()
                            OneUiTextButton("다시 시도", color: palette.primary, action: onRetry)
                        }
                        .padding(.top, 12)
                    case .hidden: EmptyView()
                    }
                }
            }
            .overlay {
                ZStack {
                    RadialGradient(colors: [aiBlue.opacity(0.16), .clear], center: .topLeading, startRadius: 0, endRadius: 260)
                    RadialGradient(colors: [aiViolet.opacity(0.12), .clear], center: UnitPoint(x: 1, y: 0.1), startRadius: 0, endRadius: 220)
                    RadialGradient(colors: [aiPink.opacity(0.10), .clear], center: UnitPoint(x: 0.8, y: 1.1), startRadius: 0, endRadius: 240)
                }
                .clipShape(shape).allowsHitTesting(false)
            }
            .overlay(shape.strokeBorder(aiGradient, lineWidth: 1))
            .padding(.vertical, 8)
            .transition(.opacity.combined(with: .move(edge: .top)))
        }
    }
}

// MARK: - 게시글

/// 게시글 — 제목 → AI 요약 → 본문 → 첨부 → 댓글. 본문 HTML 만 웹 보기로 그리고 나머지는 앱 화면이라 포털 머리글·메뉴 없이 글만.
struct BoardPostView: View {
    let url: String
    let fallbackTitle: String?
    @SharedModel private var model
    @Environment(\.palette) private var palette
    @State private var detail: HanaPostDetail?
    @State private var error: String?
    @State private var summary: PostSummarizer.State?
    @State private var comments: [HanaComment]?
    @State private var zoom: URL?
    @State private var run = 0
    @State private var attempt = 0

    var body: some View {
        Group {
            if error != nil {
                SheetScroll {
                    OneUiCard {
                        Text("게시글을 불러오지 못했습니다").font(.ui(.titleMedium, .bold))
                        // 다음에 또 떴을 때 원인을 알 수 있게 — 짧은 이유를 함께(안드로이드와 같게).
                        Text("\(error ?? ""). 다시 시도하거나 포털 페이지로 열어 볼 수 있습니다.").font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant).padding(.top, 4)
                        HStack(spacing: 8) {
                            OneUiButton("다시 시도") { error = nil; attempt += 1 }
                            OneUiButton("웹으로 보기", style: .neutral) { model.nav.open(.web(url: url, title: "게시글", resync: false)) }
                        }
                        .padding(.top, 14)
                    }
                }
            } else if let d = detail {
                SheetScroll(padding: EdgeInsets(top: 4, leading: 0, bottom: 32, trailing: 0)) {
                    PostHeader(title: d.title, meta: [d.writer, prettyPortalDate(d.date), d.views.map { "조회 \($0)" }].compactMap { $0 }.joined(separator: " · "))
                    SummaryCard(state: summary) { run += 1 }.padding(.horizontal, OneUi.pagePadding)
                    if !d.html.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        OneUiCard { PostBody(html: d.html) { zoom = $0 } }
                            .padding(.horizontal, OneUi.pagePadding).padding(.vertical, 8)
                    }
                    if !d.files.isEmpty { Attachments(files: d.files) }
                    if let comments, !comments.isEmpty { CommentsSection(comments: comments, postWriter: d.writer) }
                }
            } else {
                VStack(spacing: 0) {
                    if let fallbackTitle { PostHeader(title: fallbackTitle, meta: nil) }
                    OneUiLoading(size: 28, stroke: 3).padding(.top, 96)
                    Spacer()
                }
            }
        }
        .fullScreenCover(item: Binding(get: { zoom.map(IdentifiedURL.init) }, set: { zoom = $0?.url })) { PhotoViewer(urls: [$0.url]) }
        .task(id: "\(url)#\(attempt)") {
            do { detail = try await PostAPI.detail(url) } catch is CancellationError { return } catch {
                self.error = (error as? URLError).map { $0.code == .timedOut ? "학교 서버가 응답하지 않습니다" : "인터넷에 연결되어 있지 않습니다" }
                    ?? error.localizedDescription.trimmingCharacters(in: CharacterSet(charactersIn: ". "))
            }
            guard let d = detail else { return }
            comments = (try? await PostAPI.comments(url)) ?? []
        }
        .task(id: "\(detail?.title ?? "")-\(run)") {
            guard let d = detail else { return }
            await model.summarizer.summarize(key: PostSummarizer.key(url), title: d.title, text: d.text, images: d.images, url: url, posted: d.date) { s in
                withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) { summary = s }
            }
        }
    }
}

struct IdentifiedURL: Identifiable { let url: URL; var id: String { url.absoluteString } }

private struct PostHeader: View {
    let title: String
    let meta: String?
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.ui(.titleLarge, .bold))
            if let meta, !meta.isEmpty { Text(meta).font(.ui(.labelMedium)).foregroundStyle(onPage?.opacity(0.9) ?? palette.onSurfaceVariant) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, OneUi.pagePadding + 4).padding(.vertical, 8)
    }
}

/// 본문 HTML — 높이는 페이지가 알려 주는 실제 높이로 맞춰 바깥 스크롤 하나로만 읽습니다. 링크는 브라우저로, 그림은 크게 보기로.
struct PostBody: View {
    let html: String
    let onImage: (URL) -> Void
    @Environment(\.palette) private var palette
    @State private var height: CGFloat = 120

    var body: some View {
        HTMLBodyView(html: html, text: palette.onSurface, link: palette.primary, dark: palette.dark, height: $height, onImage: onImage)
            .frame(height: height)
    }
}

private struct HTMLBodyView: UIViewRepresentable {
    let html: String
    let text: Color
    let link: Color
    let dark: Bool
    @Binding var height: CGFloat
    let onImage: (URL) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "post")
        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.isScrollEnabled = false
        web.navigationDelegate = context.coordinator
        web.customUserAgent = PortalClient.userAgent
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        context.coordinator.parent = self
        let doc = Self.wrap(html, text: text, link: link, dark: dark)
        if context.coordinator.loaded != doc {
            context.coordinator.loaded = doc
            web.loadHTMLString(doc, baseURL: URL(string: PortalClient.base + "/"))
        }
    }

    static func css(_ c: Color) -> String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a)
        return "rgb(\(Int(r * 255)),\(Int(g * 255)),\(Int(b * 255)))"
    }

    static func wrap(_ html: String, text: Color, link: Color, dark: Bool) -> String {
        """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html { font-size: 10px; -webkit-text-size-adjust: 100%; }
          body { margin: 0; padding: 0; background: transparent; color: \(css(text));
                 font-family: -apple-system, sans-serif; font-size: 15px; line-height: 1.6; word-break: keep-all; overflow-wrap: anywhere; }
          body * { font-family: inherit !important; max-width: 100%; }
          p { margin: 0 0 2px; margin-left: 0 !important; margin-right: 0 !important; }
          #c div, #c section, #c article { padding: 0 !important; margin: 0 !important; }
          img { height: auto !important; border-radius: 8px; }
          table { display: block; overflow-x: auto; border-collapse: collapse; }
          td, th { border: 1px solid rgba(128,128,128,.35); padding: 4px 6px; }
          a { color: \(css(link)); }
        </style></head><body><div id="c">\(html)</div>
        <script>
          (function () {
            var c = document.getElementById('c');
            function report() { window.webkit.messageHandlers.post.postMessage({h: c.getBoundingClientRect().height}); }
            function blank(el) { return !el.textContent.trim() && !el.querySelector('img,table,iframe,video,hr'); }
            c.querySelectorAll('ul,ol,div').forEach(function (el) { if (blank(el)) el.remove(); });
            var ps = Array.prototype.slice.call(c.querySelectorAll('p'));
            for (var i = 0; i < ps.length && blank(ps[i]); i++) ps[i].remove();
            for (var j = ps.length - 1; j >= i && blank(ps[j]); j--) ps[j].remove();
            if (\(dark ? "true" : "false")) {
              c.querySelectorAll('img').forEach(function (img) { img.style.setProperty('background-color', '#fff', 'important'); });
              var TEXT = '\(css(text))';
              function rgb(v) { var m = v && v.match(/rgba?\\(([^)]+)\\)/); if (!m) return null; var p = m[1].split(',').map(parseFloat); return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 }; }
              function lum(c) { return (0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b) / 255; }
              c.querySelectorAll('*').forEach(function (el) {
                if (el.tagName === 'IMG') return;
                var cs = getComputedStyle(el);
                var fg = rgb(cs.color);
                var colorful = fg && Math.max(fg.r, fg.g, fg.b) - Math.min(fg.r, fg.g, fg.b) > 80;
                if (fg && !colorful && lum(fg) < 0.55) el.style.setProperty('color', TEXT, 'important');
                var bg = rgb(cs.backgroundColor);
                if (bg && bg.a > 0 && lum(bg) > 0.3) el.style.setProperty('background-color', 'rgba(255,255,255,' + (lum(bg) > 0.85 ? 0.04 : 0.1) + ')', 'important');
              });
            }
            new ResizeObserver(report).observe(c);
            document.addEventListener('click', function (e) {
              var img = e.target.closest && e.target.closest('img');
              if (!img) return;
              e.preventDefault();
              window.webkit.messageHandlers.post.postMessage({img: img.currentSrc || img.src});
            }, true);
            window.addEventListener('load', report);
            report();
          })();
        </script></body></html>
        """
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: HTMLBodyView
        var loaded: String?
        init(_ p: HTMLBodyView) { parent = p }

        func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: Any] else { return }
            if let h = body["h"] as? Double { DispatchQueue.main.async { self.parent.height = CGFloat(h) + 2 } }
            if let src = body["img"] as? String, let url = URL(string: src) { DispatchQueue.main.async { self.parent.onImage(url) } }
        }

        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            if action.navigationType == .linkActivated, let url = action.request.url {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
            } else {
                decisionHandler(.allow)
            }
        }
    }
}

private struct Attachments: View {
    let files: [HanaPostFile]
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    var body: some View {
        Text("첨부파일 \(files.count)").font(.ui(.labelLarge, .bold)).foregroundStyle(onPage?.opacity(0.9) ?? palette.onSurfaceVariant)
            .padding(.leading, OneUi.pagePadding + 4).padding(.top, 16).padding(.bottom, 8)
        OneUiGroup {
            ForEach(Array(files.enumerated()), id: \.element.id) { i, f in
                Button { Downloads.open(f) } label: {
                    HStack(spacing: 12) {
                        let ext = String((f.name.split(separator: ".").last.map(String.init) ?? "").uppercased().prefix(4))
                        Text(ext.isEmpty ? "FILE" : ext).font(.ui(.labelSmall, .bold)).foregroundStyle(palette.primary)
                            .frame(width: 36, height: 36).background(RoundedRectangle(cornerRadius: 10).fill(palette.primary.opacity(0.1)))
                        VStack(alignment: .leading, spacing: 0) {
                            Text(f.name).font(.ui(.bodyMedium)).lineLimit(2).multilineTextAlignment(.leading).foregroundStyle(palette.onSurface)
                            if let s = f.sizeLabel { Text(s).font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant) }
                        }
                        Spacer()
                        Text("받기").font(.ui(.labelLarge)).foregroundStyle(palette.primary)
                    }
                    .padding(.horizontal, OneUi.rowPadding).padding(.vertical, 14).contentShape(Rectangle())
                }
                .buttonStyle(RowPressStyle())
                if i != files.count - 1 { OneUiDivider() }
            }
        }
        .padding(.horizontal, OneUi.pagePadding)
    }
}

/// 첨부 받기 — 로그인 세션으로 받아 공유 시트(파일에 저장·다른 앱으로 열기)를 띄웁니다.
@MainActor
enum Downloads {
    static func open(_ f: HanaPostFile) {
        Task {
            guard let url = URL(string: f.url) else { return }
            Toast.show("다운로드를 시작했습니다")
            var req = URLRequest(url: url)
            req.setValue(PortalClient.userAgent, forHTTPHeaderField: "User-Agent")
            guard let (data, _) = try? await URLSession.shared.data(for: req) else { Toast.show("다운로드하지 못했습니다"); return }
            let safe = f.name.replacingOccurrences(of: #"[\\/:*?"<>|]"#, with: "_", options: .regularExpression)
            let tmp = FileManager.default.temporaryDirectory.appendingPathComponent(safe)
            try? data.write(to: tmp)
            let vc = UIActivityViewController(activityItems: [tmp], applicationActivities: nil)
            guard let top = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first?.topmost else { return }
            // 아이패드 공유 창은 팝오버라 기준 위치가 있어야 합니다(없으면 앱이 종료).
            if let pop = vc.popoverPresentationController {
                pop.sourceView = top.view
                pop.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 1, height: 1)
                pop.permittedArrowDirections = []
            }
            top.present(vc, animated: true)
        }
    }
}

extension UIViewController {
    var topmost: UIViewController { presentedViewController?.topmost ?? self }
}

/// 댓글 — 댓글 아래에 답글을 들여 쓰고, 글쓴이가 단 댓글엔 '작성자'. 읽기 전용입니다.
private struct CommentsSection: View {
    let comments: [HanaComment]
    let postWriter: String?
    @Environment(\.palette) private var palette

    var body: some View {
        let total = comments.reduce(0) { $0 + 1 + $1.replies.count }
        VStack(alignment: .leading, spacing: 8) {
            Text(comments.allSatisfy(\.answer) ? "답변 \(total)" : "댓글 \(total)").font(.ui(.titleSmall, .bold)).padding(.leading, 4)
            OneUiGroup {
                ForEach(Array(comments.enumerated()), id: \.element.id) { i, c in
                    if i > 0 { OneUiDivider() }
                    row(c, indent: false)
                    ForEach(c.replies) { row($0, indent: true) }
                }
            }
        }
        .padding(.horizontal, OneUi.pagePadding).padding(.top, 20)
    }

    private func row(_ c: HanaComment, indent: Bool) -> some View {
        HStack(alignment: .top, spacing: 8) {
            if indent { Text("ㄴ").font(.ui(.bodyMedium)).foregroundStyle(palette.onSurfaceVariant) }
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 0) {
                    if c.answer { Text("답변 ").font(.ui(.labelLarge, .bold)).foregroundStyle(palette.primary) }
                    Text(c.writer.isEmpty ? "익명" : c.writer).font(.ui(.labelLarge, .bold))
                    if let w = postWriter, c.writer == w { Text(" 작성자").font(.ui(.labelMedium)).foregroundStyle(palette.primary) }
                    if let d = c.date { Text("  \(d)").font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant) }
                }
                Text(c.deleted ? "삭제된 댓글입니다." : c.content).font(.ui(.bodyMedium)).foregroundStyle(c.deleted ? palette.onSurfaceVariant : palette.onSurface)
            }
            Spacer(minLength: 0)
        }
        .padding(.leading, indent ? 36 : 20).padding(.trailing, 20).padding(.vertical, 12)
    }
}

// MARK: - 포털 웹 화면

/// 앱 안 웹 화면 — 로그인 세션 쿠키를 심고 엽니다. 로그인 페이지로 튕기면 한 번 다시 로그인해 원래 글을 다시 읽습니다.
struct PortalWebView: View {
    let url: String
    @Environment(\.palette) private var palette
    @State private var loading = true
    @State private var ready = false
    @State private var failed = false

    var body: some View {
        Group {
            if failed {
                VStack {
                    OneUiCard {
                        Text("페이지를 불러오지 못했습니다").font(.ui(.titleMedium, .bold))
                        Text("포털 로그인 세션이 만료됐거나 페이지가 웹 보기를 막고 있을 수 있습니다.").font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant).padding(.top, 4)
                        OneUiButton("브라우저로 열기") { if let u = URL(string: url) { UIApplication.shared.open(u) } }.padding(.top, 14)
                    }
                    Spacer()
                }
                .padding(OneUi.pagePadding)
            } else {
                ZStack(alignment: .top) {
                    if ready { PortalWebRepresentable(url: url, loading: $loading, failed: $failed) }
                    if loading { ProgressView().progressViewStyle(.linear).tint(palette.primary) }
                }
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: OneUi.cornerLarge, topTrailingRadius: OneUi.cornerLarge, style: .continuous))
                .background(UnevenRoundedRectangle(topLeadingRadius: OneUi.cornerLarge, topTrailingRadius: OneUi.cornerLarge, style: .continuous).fill(palette.surface))
                .padding(.horizontal, OneUi.pagePadding)
                .ignoresSafeArea(edges: .bottom)
            }
        }
        .task {
            await PortalClient.shared.ensureLoggedIn()
            ready = true
        }
    }
}

private struct PortalWebRepresentable: UIViewRepresentable {
    let url: String
    @Binding var loading: Bool
    @Binding var failed: Bool

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        let web = WKWebView(frame: .zero, configuration: config)
        web.customUserAgent = PortalClient.userAgent
        web.navigationDelegate = context.coordinator
        context.coordinator.web = web
        Task { await context.coordinator.load() }
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate {
        let parent: PortalWebRepresentable
        weak var web: WKWebView?
        var recovered = false
        init(_ p: PortalWebRepresentable) { parent = p }

        func load() async {
            guard let web, let url = URL(string: parent.url) else { return }
            let store = web.configuration.websiteDataStore.httpCookieStore
            for c in await PortalClient.shared.sessionCookies() { await store.setCookie(c) }
            web.load(URLRequest(url: url))
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) { parent.loading = true }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.loading = false
            if webView.url?.path.contains("/main/login/") == true {
                guard !recovered else { return }
                recovered = true
                parent.loading = true
                Task {
                    if (try? await PortalClient.shared.relogin()) != nil { await load() } else { parent.loading = false }
                }
                return
            }
            // 웹 화면이 받은 최신 세션 쿠키를 앱 쪽으로 — 다음 글도 같은 세션으로.
            Task {
                let cookies = await webView.configuration.websiteDataStore.httpCookieStore.allCookies()
                await PortalClient.shared.adoptCookies(cookies)
            }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            if (error as NSError).code != NSURLErrorCancelled { parent.failed = true; parent.loading = false }
        }
    }
}
