import SwiftUI

/// 홈 — 안드로이드 TodayDashboard(폰) + BentoHome: '지금' 고리 카드, 두 기둥(왼쪽 게시판·급식 | 오른쪽 클래스룸·오늘 일정),
/// 날씨 | 시험까지, 그리고 데일리 브리핑(아직 안 봤거나 오전이면 맨 위, 아니면 맨 아래).
struct HomeView: View {
    @SharedModel private var model
    @Environment(\.palette) private var palette
    /// 홈이 지금 실제로 보이는지 — 데일리 브리핑을 '봤다'고 셀 때.
    var shown: Bool = true

    @State private var briefSeen = false
    @State private var briefOnScreen = false
    @State private var seenTask: Task<Void, Never>?
    /// 넓은 화면(두 줄) 배치인지 — 폭 그 자체를 상태로 두면 회전하는 동안 매 프레임 홈 전체를 다시 계산해
    /// 실기기에서 10초 넘게 멈춰 시스템이 앱을 종료했습니다. 경계를 넘을 때만 바뀌는 값으로, 경계 근처에서 오가지 않게 여유를 둡니다.
    @State private var wide = false

    var body: some View {
        let today = model.today
        let now = model.now
        let gap: CGFloat = 12
        let briefBeforeNoon = now.minutesOfDay < 12 * 60 + 10
        let brief = model.brief.current.flatMap { $0.date == today ? $0 : nil }
        let briefTop = briefBeforeNoon || !briefSeen
        ScrollView {
            // 안드로이드 홈과 같은 순서, 구역 제목 없이: 데일리 브리핑(아직 안 봤거나 오전이면 맨 위) → 지금 → 게시판·급식 |
            // 클래스룸·오늘 일정(시간에 따라 위아래가 바뀜) → 날씨·시험까지 → 데일리 브리핑(이미 봤고 오후면 맨 아래).
            if wide {
                // 넓은 화면(아이패드) — 왼쪽 줄은 브리핑·지금·날씨·시험, 오른쪽은 두 기둥.
                HStack(alignment: .top, spacing: 16) {
                    VStack(alignment: .leading, spacing: gap) {
                        if briefTop { dailyBrief(brief, startExpanded: briefBeforeNoon && !briefSeen) }
                        BentoHome(parts: [.now, .bottom], onOpenWeather: { model.nav.open(.weather(until: nil)) })
                        if !briefTop { dailyBrief(brief, startExpanded: false) }
                    }
                    .frame(minWidth: 320, idealWidth: 460, maxWidth: 460)
                    BentoHome(parts: [.columns], onOpenWeather: {})
                }
                .frame(maxWidth: 1180)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .padding(.bottom, 24)
            } else {
                VStack(alignment: .leading, spacing: gap) {
                    if briefTop { dailyBrief(brief, startExpanded: briefBeforeNoon && !briefSeen) }
                    BentoHome(onOpenWeather: { model.nav.open(.weather(until: nil)) })
                    if !briefTop { dailyBrief(brief, startExpanded: false) }
                }
                .padding(.horizontal, OneUi.pagePadding)
                .padding(.top, 4)
                .padding(.bottom, 20)
            }
        }
        .onGeometryChange(for: Bool.self) { g in wide ? g.size.width >= 720 : g.size.width >= 800 } action: { if wide != $0 { wide = $0 } }
        .refreshable { await model.refreshAll() }
        .navigationTitle("홈")
        .homeSubtitle(subtitle)
        .toolbar {
            // 칸 폭을 줄인 묶음(BarButton) — 시스템 묶음은 칸 간격이 넓어 좁은 iPhone 에서 가운데까지 왔습니다.
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 0) {
                    // 통합 검색 — 알리미 왼쪽. 아이패드는 사이드바의 '통합 검색'으로 옮겨 갑니다(입력은 사이드바 검색창).
                    BarButton(title: "통합 검색", symbol: "magnifyingglass") { model.nav.open(.search) }
                    BarButton(title: "알리미", symbol: model.alimUnread > 0 ? "tray.full" : "tray", badge: model.alimUnread) { model.openAlimScreen() }
                    BarButton(title: "설정", symbol: "gearshape", badge: WhatsNew.shared.pending ? 1 : 0) { model.nav.open(.settings(nil)) }
                }
            }
        }
        .onAppear { briefSeen = model.brief.seen(today) }
        .onChange(of: shown) { _, s in if s { briefSeen = model.brief.seen(today) }; updateSeen() }
        .onChange(of: briefOnScreen) { _, _ in updateSeen() }
    }

    private var subtitle: String {
        var s = model.today.longText
        if model.offline {
            s += " · 오프라인"
            if let at = model.lastSyncAt { s += " · " + (at.localDate == model.today ? at.hm : "\(at.localDate.month)/\(at.localDate.day) \(at.hm)") + " 동기화" }
        }
        return s
    }

    @ViewBuilder
    private func dailyBrief(_ brief: MorningBrief.Brief?, startExpanded: Bool) -> some View {
        MorningBriefPanel(brief: brief, loading: model.brief.loading, today: model.today, startExpanded: startExpanded)
            .onGeometryChange(for: Bool.self) { g in
                let f = g.frame(in: .global)
                let screen = UIScreen.main.bounds
                return f.height > 0 && f.intersection(screen).height >= f.height / 2
            } action: { briefOnScreen = $0 }
    }

    /// 홈이 보이고 브리핑 카드의 절반 넘게 화면에 1.5초 넘게 있으면 본 것으로.
    private func updateSeen() {
        seenTask?.cancel()
        guard shown, briefOnScreen, model.brief.current?.date == model.today, !model.brief.seen(model.today) else { return }
        let day = model.today
        seenTask = Task {
            try? await Task.sleep(for: .seconds(1.5))
            if !Task.isCancelled { model.brief.markSeen(day) }
        }
    }
}

private extension View {
    /// 큰 제목 아래 날짜 — iOS 26 의 내비게이션 부제목.
    @ViewBuilder func homeSubtitle(_ text: String) -> some View {
        if #available(iOS 26.0, *) { navigationSubtitle(text) } else { self }
    }

    /// 툴바 단추의 숫자 배지 — iOS 26 부터.
    @ViewBuilder func badgeIfAvailable(_ count: Int) -> some View {
        if #available(iOS 26.0, *) { badge(count) } else { self }
    }
}

// MARK: - 벤토

/// 홈 칸들 — 넓은 화면은 [parts] 로 나눠 두 줄에 놓습니다.
enum BentoPart: Hashable { case now, columns, bottom }

private struct BentoHome: View {
    @SharedModel private var model
    var parts: Set<BentoPart> = [.now, .columns, .bottom]
    var onOpenWeather: () -> Void

    var body: some View {
        let today = model.today
        let now = model.now
        let blocks = model.blocks(for: today)
        let current = Schedule.block(at: now, in: blocks)
        let ctx = model.context()
        let notice = homeStayNotice(today: today, now: now, context: ctx)
        let showRing = model.hasTimetable && notice == nil
        let todayAgenda = todayBrief(model.schedule, grade: Prefs.shared.studentGrade, extras: model.agendaExtras, today: today)
        let (left, right) = bentoOrder(now: now, classroom: model.classroomSnapshot)
        VStack(spacing: 12) {
            if parts.contains(.now) {
                if showRing, let block = current, !block.isBlank {
                    let next = Schedule.nextEvent(blocks: blocks, current: block, time: now)
                    let isGap = block.isGap
                    NowRingCard(
                        label: nowLabel(block),
                        timeRange: block.timeRangeText,
                        title: isGap ? block.statusLabel : block.headline,
                        where_: isGap ? nil : block.room,
                        next: next.map { n in [n.start.hm, n.title, n.room].compactMap { $0 }.joined(separator: " · ").replacingOccurrences(of: #"^(\d\d:\d\d) · "#, with: "$1 ", options: .regularExpression) },
                        progress: progress(block, now),
                        remaining: shortRemaining(max(0, Int(ceil(block.end.timeIntervalSince(now) / 60)))))
                } else {
                    NowHeroCard(block: current, notice: notice,
                                extras: HomeStayExtras.build(today: today, now: now, context: ctx, schedule: model.schedule, blocksFor: { model.blocks(for: $0) }),
                                needsAccount: !model.hasCredentials, hasTimetable: model.hasTimetable, now: now)
                }
            }
            if parts.contains(.columns) {
                BentoColumns(left: left, right: right) { slot in
                    switch slot {
                    case .board: BoardTile(posts: model.homeBoardPosts)
                    case .meal: MealTile(today: today, now: now)
                    case .classroom: ClassroomTile(snapshot: model.classroomSnapshot)
                    case .today: TodayTile(today: today, todayAgenda: todayAgenda)
                    }
                }
            }
            if parts.contains(.bottom) {
                HStack(alignment: .top, spacing: 12) {
                    WeatherTile(action: onOpenWeather)
                    ExamTile(exam: examDday(model.schedule, today: today))
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func progress(_ b: Block, _ now: Date) -> Double {
        let total = b.end.timeIntervalSince(b.start)
        return total > 0 ? (now.timeIntervalSince(b.start) / total).clamped(0, 1) : 0
    }

    /// '지금' 칸 머리 — 몇 교시인지, 몇 타임인지(구역 제목 '지금'과 겹치지 않게).
    private func nowLabel(_ b: Block) -> String {
        switch b.kind {
        case .lesson(let p, _): "\(p)교시"
        case .study(let slot, _): slot.name
        case .outing(let o): o.overnight ? "외박" : "외출"
        case .gap, .blank: "다음까지"
        }
    }

    private func shortRemaining(_ m: Int) -> String { m >= 60 ? String(format: "%d:%02d", m / 60, m % 60) : "\(m)분" }

    /// 두 기둥 안의 위아래 — 끼니 30분 전부터 끝날 때까지 급식, 6시간 안의 안 읽은 클래스룸 글, 4~9시엔 오늘 일정이 위로.
    private func bentoOrder(now: Date, classroom: ClassroomSnapshot?) -> ([BentoSlot], [BentoSlot]) {
        let minute = now.minutesOfDay
        let mealSoon = [Schedule.breakfast, Schedule.lunch, Schedule.dinner].contains { minute >= $0.start - 30 && minute < $0.end }
        let read = model.classroomStore.readIDs
        let fresh = classroom?.posts.contains { !read.contains($0.id) && $0.updated >= now.addingTimeInterval(-6 * 3600) && $0.updated <= now.addingTimeInterval(300) } ?? false
        let morning = minute >= 4 * 60 && minute < 9 * 60
        let left: [BentoSlot] = mealSoon ? [.meal, .board] : [.board, .meal]
        let right: [BentoSlot] = !fresh && morning ? [.today, .classroom] : [.classroom, .today]
        return (left, right)
    }
}

enum BentoSlot: Hashable { case classroom, board, meal, today
    var fixed: Bool { self == .meal || self == .today }
}

/// 두 기둥 — 급식·오늘 일정(고정 칸)은 둘 중 큰 쪽(최소 168), 게시판·클래스룸(목록 칸)도 둘 중 큰 쪽 높이로 맞춥니다.
private struct BentoColumns<Tile: View>: View {
    let left: [BentoSlot]
    let right: [BentoSlot]
    @ViewBuilder let tile: (BentoSlot) -> Tile

    var body: some View {
        BentoLayout(leftCount: left.count) {
            ForEach(left + right, id: \.self) { slot in tile(slot).layoutValue(key: SlotKind.self, value: slot.fixed) }
        }
    }
}

private struct SlotKind: LayoutValueKey { static let defaultValue = false }

private struct BentoLayout: Layout {
    let leftCount: Int
    let gap: CGFloat = 12

    private func heights(_ width: CGFloat, _ subviews: Subviews) -> (fixed: CGFloat, list: CGFloat) {
        let colW = (width - gap) / 2
        var fixedH: CGFloat = 168, listH: CGFloat = 0
        for s in subviews {
            let h = s.sizeThatFits(ProposedViewSize(width: colW, height: nil)).height
            if s[SlotKind.self] { fixedH = max(fixedH, h) } else { listH = max(listH, h) }
        }
        return (fixedH, listH)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 360
        let (f, l) = heights(width, subviews)
        func col(_ range: Range<Int>) -> CGFloat { range.reduce(0) { $0 + (subviews[$1][SlotKind.self] ? f : l) } + gap * CGFloat(max(0, range.count - 1)) }
        return CGSize(width: width, height: max(col(0..<leftCount), col(leftCount..<subviews.count)))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let colW = (bounds.width - gap) / 2
        let (f, l) = heights(bounds.width, subviews)
        var y = bounds.minY
        for i in 0..<leftCount {
            let h = subviews[i][SlotKind.self] ? f : l
            subviews[i].place(at: CGPoint(x: bounds.minX, y: y), proposal: ProposedViewSize(width: colW, height: h))
            y += h + gap
        }
        y = bounds.minY
        for i in leftCount..<subviews.count {
            let h = subviews[i][SlotKind.self] ? f : l
            subviews[i].place(at: CGPoint(x: bounds.minX + colW + gap, y: y), proposal: ProposedViewSize(width: colW, height: h))
            y += h + gap
        }
    }
}

// MARK: - 칸

/// 홈 칸 — 서리 카드 위에 칸 색을 옅게 덮은 판. 머리글은 칸 색(밝은 카드면 짙게).
struct BentoTile<Content: View>: View {
    let label: String
    var symbol: String
    let accent: Color
    var trailing: String? = nil
    var action: (() -> Void)? = nil
    @ViewBuilder var content: Content
    @Environment(\.palette) private var palette

    var body: some View {
        OneUiCard(cornerRadius: 26, padding: EdgeInsets(top: 14, leading: 16, bottom: 16, trailing: 14), action: action) {
            VStack(alignment: .leading, spacing: 4) {
                TileLabel(label: label, symbol: symbol, accent: accent, trailing: trailing, chevron: action != nil)
                    .padding(.bottom, 4)
                content
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).fill(accent.opacity(0.10)).allowsHitTesting(false))
    }
}

/// 칸 머리 — 건강 앱 카드처럼 색 기호와 색 제목, 오른쪽에 시각과 화살표.
private struct TileLabel: View {
    let label: String
    let symbol: String
    let accent: Color
    var trailing: String? = nil
    var chevron = true
    @Environment(\.palette) private var palette
    var body: some View {
        // 카드가 밝으면 칸 색을 짙게 — 노랑·하늘이 밝은 판 위에서 거의 안 보였습니다.
        let onLight = palette.onSurface.luminance < 0.5
        // 좁으면(iPhone 17e 두 칸 등) 오른쪽 시각을 빼고 제목을 온전히 — 시각이 자리를 먹어 '클래…'처럼 제목이 잘렸습니다.
        // 시각은 카드 안 목록에도 있어 빠져도 정보는 남습니다.
        ViewThatFits(in: .horizontal) {
            row(trailing: trailing, onLight: onLight)
            row(trailing: nil, onLight: onLight)
        }
    }

    private func row(trailing: String?, onLight: Bool) -> some View {
        HStack(spacing: 4) {
            Label(label, systemImage: symbol).font(.subheadline.weight(.semibold))
                .foregroundStyle(onLight ? accent.mix(.black, 0.3) : accent).lineLimit(1).fixedSize()
            Spacer(minLength: 4)
            if let trailing { Text(trailing).font(.footnote).foregroundStyle(palette.onSurfaceVariant).lineLimit(1).fixedSize() }
            if chevron { Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(palette.onSurfaceVariant.opacity(0.6)) }
        }
    }
}

/// 칸 안의 큰 숫자와 단위 — 건강 앱 카드의 큰 값처럼.
struct BigValue: View {
    let value: String
    var unit: String? = nil
    @Environment(\.palette) private var palette
    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(value).font(.system(.largeTitle, design: .rounded).weight(.semibold)).lineLimit(1).minimumScaleFactor(0.6)
            if let unit { Text(unit).font(.subheadline.weight(.semibold)).foregroundStyle(palette.onSurfaceVariant) }
        }
    }
}

/// 남은 시간 고리 — 블록이 지난 만큼 채운 원과 가운데 남은 분.
private struct RemainingRing: View {
    let progress: Double
    let text: String
    @Environment(\.palette) private var palette
    var body: some View {
        ZStack {
            Circle().stroke(palette.primary.opacity(0.18), lineWidth: 6)
            Circle().trim(from: 0, to: progress).stroke(palette.primary, style: StrokeStyle(lineWidth: 6, lineCap: .round)).rotationEffect(.degrees(-90))
            Text(text).font(.ui(.labelLarge, .heavy)).lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(width: 60, height: 60)
    }
}

/// 홈 맨 위 '지금' — 고리, 시간, 지금 하는 것, 있을 곳, 다음(시각 · 이름 · 장소).
struct NowRingCard: View {
    var label = "지금"
    let timeRange: String
    let title: String
    let where_: String?
    let next: String?
    let progress: Double
    let remaining: String
    @Environment(\.palette) private var palette

    var body: some View {
        OneUiCard(cornerRadius: 26, padding: EdgeInsets(top: 14, leading: 16, bottom: 16, trailing: 16)) {
            TileLabel(label: label, symbol: "clock.fill", accent: palette.primary, trailing: timeRange, chevron: false).padding(.bottom, 10)
            HStack(spacing: 16) {
                RemainingRing(progress: progress, text: remaining)
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).font(.title3.bold()).lineLimit(1)
                    if let where_ { Text(where_).font(.ui(.bodyMedium, .bold)).foregroundStyle(palette.primary).lineLimit(1) }
                    if let next { Text("다음 · \(next)").font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1).padding(.top, 2) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

/// 카드 공통 틀 — 작은 아이콘 + 제목 헤더.
struct DashboardCard<Content: View>: View {
    let title: String
    let symbol: String
    var subtitle: String? = nil
    var action: (() -> Void)? = nil
    @ViewBuilder var content: Content
    @Environment(\.palette) private var palette

    var body: some View {
        OneUiCard(cornerRadius: 26, padding: EdgeInsets(top: 14, leading: 16, bottom: 16, trailing: 16), action: action) {
            TileLabel(label: title, symbol: symbol, accent: palette.primary, trailing: subtitle, chevron: action != nil).padding(.bottom, 10)
            content
        }
    }
}

/// 고리 대신 쓰는 '지금' 카드 — 연동 전·불러오는 중·귀가 기간·하루가 끝났을 때.
private struct NowHeroCard: View {
    let block: Block?
    let notice: HomeStayNotice?
    let extras: HomeStayExtras?
    let needsAccount: Bool
    let hasTimetable: Bool
    let now: Date
    @SharedModel private var model
    @Environment(\.palette) private var palette

    var body: some View {
        DashboardCard(title: "지금", symbol: "clock.fill", action: needsAccount ? { model.nav.open(.settings(.account)) } : nil) {
            if !hasTimetable {
                if needsAccount {
                    Text("학사시스템 연동이 필요합니다").font(.ui(.titleMedium))
                    DashboardEmpty("계정을 연동하면 시간표·면학 위치·학사일정·게시판을 자동으로 가져옵니다. 탭해서 연동하세요.").padding(.top, 4)
                } else {
                    Text("시간표 불러오는 중…").font(.ui(.titleMedium))
                    DashboardEmpty("잠시만 기다려주세요").padding(.top, 4)
                }
            } else if let notice, block == nil || block?.isBlank == true {
                Text(notice.title).font(.ui(.titleMedium))
                Text(notice.detail).font(.ui(.bodyMedium, .semibold)).foregroundStyle(palette.primary).padding(.top, 4)
                if let extras { HomeStayExtraRows(extras: extras) { model.nav.open(.weather(until: extras.backAt)) } }
            } else if block == nil || block?.isBlank == true {
                Text("오늘 일정이 모두 끝났습니다").font(.ui(.titleMedium))
                DashboardEmpty("편안한 밤 보내세요").padding(.top, 4)
            } else if let block {
                Text(block.headline).font(.ui(.titleMedium, .bold))
            }
        }
    }
}

/// 귀가 기간 안내 아래 — 서울 날씨 두 칸(지금 · 귀교 밤 9시)과 귀교 후 첫 일정·시험.
struct HomeStayExtraRows: View {
    let extras: HomeStayExtras
    var onOpenWeather: (() -> Void)?
    @Environment(\.palette) private var palette

    var body: some View {
        let tiles = [extras.weatherNow, extras.weatherBack].compactMap { $0 }
        if !tiles.isEmpty {
            Button { onOpenWeather?() } label: {
                HStack(spacing: 0) {
                    ForEach(Array(tiles.enumerated()), id: \.offset) { i, t in
                        if i > 0 { Capsule().fill(palette.onSurfaceVariant.opacity(0.45)).frame(width: 1.5).padding(.vertical, 14) }
                        VStack(alignment: .leading, spacing: 0) {
                            Text(t.label).font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant)
                            HStack(spacing: 8) {
                                Image(systemName: t.symbol).symbolRenderingMode(.multicolor).font(.system(size: 30))
                                Text(t.temp ?? "–").font(.system(size: 30, weight: .regular))
                            }
                            .padding(.top, 6)
                            Text(t.desc).font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant).lineLimit(2).padding(.top, 4)
                            if let tip = t.tip { Text(tip).font(.ui(.labelMedium, .bold)).foregroundStyle(Color(argb: 0xFF6FB4FF)).lineLimit(2) }
                        }
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.onSurface.opacity(palette.dark ? 0.06 : 0.045)))
            }
            .buttonStyle(PressStyle())
            .padding(.top, 14)
        }
        if let plan = extras.firstPlan {
            Rectangle().fill(palette.outlineVariant).frame(height: 0.8).padding(.top, tiles.isEmpty ? 10 : 6)
            row("mappin.and.ellipse", palette.primary, "귀교 후 첫 일정", plan.0, plan.1)
        }
        if let exam = extras.exam {
            Rectangle().fill(palette.outlineVariant).frame(height: 0.8)
            row("calendar", Color(argb: 0xFFFF9F6B), "다가오는 시험", exam.0, exam.1 == 0 ? "D-day" : "D-\(exam.1)")
        }
    }

    private func row(_ symbol: String, _ tint: Color, _ label: String, _ value: String, _ side: String?) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 18)).foregroundStyle(tint).frame(width: 22)
            VStack(alignment: .leading, spacing: 0) {
                Text(label).font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant)
                Text(value).font(.ui(.bodyMedium, .semibold))
            }
            Spacer()
            if let side { Text(side).font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant) }
        }
        .padding(.vertical, 8)
    }
}

/// 다음 끼니 — 아직 끝나지 않은 첫 끼니(다 끝났으면 내일 첫 끼니)의 이름·시각과 특식·사진.
private struct MealTile: View {
    let today: LocalDate
    let now: Date
    @SharedModel private var model
    @Environment(\.palette) private var palette
    @State private var picked: [String] = []

    var body: some View {
        let pick = model.mealPick(now: now)
        let label = pick.map { ($0.tomorrow ? "내일 " : "") + $0.meal.label } ?? "급식"
        let time = pick.map { String(format: "%02d:%02d", $0.meal.range.start / 60, $0.meal.range.start % 60) }
        let list = pick.flatMap { $0.meals.items[$0.meal] } ?? []
        let names = list.map(\.name)
        BentoTile(label: label, symbol: "fork.knife", accent: Color(argb: 0xFFF08A3C), trailing: time, action: { model.nav.open(.meal(pick?.tomorrow == true ? today.adding(days: 1) : nil)) }) {
            if list.isEmpty {
                DashboardEmpty(model.isLoadingMeals ? "불러오는 중" : "다음 식단이 아직 없습니다")
            } else {
                let shown = picked.isEmpty ? MealHighlights.cached(names) ?? MealHighlights.pickLocally(names, history: Array(model.mealDays.values)) : picked
                ForEach(shown, id: \.self) { Text($0).font(.headline).lineLimit(2) }
                if let url = MealParser.thumbURL(pick.flatMap { $0.meals.photos[$0.meal] }) {
                    RemoteImage(url: url).frame(height: 96).frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous)).padding(.top, 6)
                } else {
                    ForEach(names.filter { !shown.contains($0) }.prefix(4), id: \.self) {
                        Text($0).font(.ui(.bodySmall)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1)
                    }
                }
            }
        }
        .task(id: names) {
            picked = []
            if !names.isEmpty, let ai = await MealHighlights.fetch(names) { picked = ai }
        }
    }
}

/// 오늘 일정 — 마감이 있으면 '오늘 마감'과 큰 숫자, 아니면 오늘 일정 수. 오늘이 비면 다음 일정 하나.
private struct TodayTile: View {
    let today: LocalDate
    let todayAgenda: [AgendaItem]
    @SharedModel private var model
    @Environment(\.palette) private var palette

    var body: some View {
        let deadlines = todayAgenda.filter(\.deadline).count
        let accent = deadlines > 0 ? Color(argb: 0xFFFF6B6B) : AgendaSource.academic.color
        if todayAgenda.isEmpty {
            let next = Agenda.merge(model.schedule, grade: Prefs.shared.studentGrade, extras: model.agendaExtras, today: today).first { $0.daysFrom(today) > 0 }
            BentoTile(label: "오늘 일정", symbol: "calendar", accent: accent, action: { model.nav.open(.academic) }) {
                BigValue(value: "없음")
                if let next {
                    Text("다음 · \(next.date.shortText)").font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant)
                    Text(next.title).font(.ui(.bodySmall)).lineLimit(2)
                }
            }
        } else {
            BentoTile(label: "오늘 일정", symbol: "calendar", accent: accent, action: { model.nav.open(.academic) }) {
                // 숫자 둘 — 일반 일정과 마감을 따로. 아래 목록은 둘 다 섞어서.
                HStack(alignment: .firstTextBaseline, spacing: 14) {
                    TodayCount(value: todayAgenda.count - deadlines, label: "일정", color: nil)
                    TodayCount(value: deadlines, label: "마감", color: deadlines > 0 ? Color(argb: 0xFFFF6B6B) : nil)
                }
                // 면학감독은 맨 아래로, 나머지는 마감 먼저·짧은 일정부터.
                let sorted = todayAgenda.sorted { a, b in
                    if a.isSupervision != b.isSupervision { return !a.isSupervision }
                    if a.deadline != b.deadline { return a.deadline }
                    return a.spanDays < b.spanDays
                }
                ForEach(sorted.prefix(3)) { item in
                    Button { if item.academic != nil { model.nav.open(.academic) } else { model.openAgenda(item) } } label: {
                        Text([item.time, item.title].compactMap { $0 }.joined(separator: " ")).font(.ui(.bodySmall)).lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                    }
                    .buttonStyle(PressStyle())
                }
            }
        }
    }
}

/// 오늘 일정 칸의 숫자 하나 — 건강 앱 요약처럼 큰 숫자 뒤 같은 줄에 작은 이름(단위 자리).
private struct TodayCount: View {
    let value: Int
    let label: String
    let color: Color?
    @Environment(\.palette) private var palette
    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 3) {
            Text("\(value)").font(.system(.largeTitle, design: .rounded).weight(.semibold)).foregroundStyle(color ?? palette.onSurface).lineLimit(1)
            Text(label).font(.subheadline.weight(.semibold)).foregroundStyle(color ?? palette.onSurfaceVariant)
        }
    }
}

/// 클래스룸 — 새 글 수와 최근 글 셋(제목과 수업명). 연결 전이면 연결 안내.
private struct ClassroomTile: View {
    let snapshot: ClassroomSnapshot?
    @SharedModel private var model
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: 0xFF2FB67C)
        if let snapshot {
            let read = model.classroomStore.readIDs
            let recent = Array(snapshot.posts.sorted { $0.updated > $1.updated }.prefix(3))
            let unread = snapshot.posts.filter { !read.contains($0.id) }.count
            BentoTile(label: "클래스룸", symbol: "graduationcap.fill", accent: accent, trailing: recent.first.map { shortWhen($0.updated) }, action: { model.nav.open(.classroom) }) {
                if unread > 0 { BigValue(value: "\(unread)", unit: "새 글") }
                // 과제 권한이 빠진 연결 — 홈에서도 알아차리게(안드로이드와 같게 빨간 한 줄, 대신 글을 하나 덜).
                if snapshot.needsReconnect {
                    Text("과제를 보려면 다시 허용해 주세요").font(.ui(.labelMedium, .bold)).foregroundStyle(.red).lineLimit(2)
                }
                if recent.isEmpty && !snapshot.needsReconnect { DashboardEmpty("글이 없습니다") }
                ForEach(recent.prefix((unread > 0 ? 2 : 3) - (snapshot.needsReconnect ? 1 : 0))) { post in
                    Button { model.openClassroomPost(post) } label: {
                        VStack(alignment: .leading, spacing: 0) {
                            Text(post.title.isEmpty ? (post.text.split(separator: "\n").first.map(String.init) ?? post.kind.label) : post.title)
                                .font(.ui(.bodyMedium, read.contains(post.id) ? .regular : .bold)).lineLimit(2).multilineTextAlignment(.leading)
                            // 홈에선 제목과 수업명만 — 시각은 클래스룸 화면에서.
                            if let name = snapshot.course(post.courseId)?.name {
                                Text(name).font(.ui(.labelSmall)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 5).contentShape(Rectangle())
                    }
                    .buttonStyle(PressStyle())
                }
            }
        } else {
            BentoTile(label: "클래스룸", symbol: "graduationcap.fill", accent: accent, action: { model.nav.open(.classroom) }) {
                DashboardEmpty("연결하면 새 글과 과제 마감이 여기에 모입니다")
            }
        }
    }
}

/// 게시판 — 학생공지 최신 글 셋(안 읽은 글은 굵게). 글을 누르면 그 글, 칸의 나머지는 목록.
private struct BoardTile: View {
    let posts: [HanaBoardPost]
    @SharedModel private var model
    @Environment(\.palette) private var palette

    var body: some View {
        let recent = Array(posts.prefix(3))
        let unread = model.boardRead.unreadCount(posts)
        BentoTile(label: "게시판", symbol: "newspaper.fill", accent: Color(argb: 0xFFF2B33D), trailing: recent.first.flatMap { boardTime($0.date) }, action: {
            if model.boardCategory != .studentNotice { model.selectBoard(.studentNotice) }
            model.nav.open(.board)
        }) {
            if unread > 0 { BigValue(value: "\(unread)", unit: "새 글") }
            if recent.isEmpty { DashboardEmpty("게시글이 없습니다") }
            ForEach(recent.prefix(unread > 0 ? 2 : 3)) { post in
                Button { model.openBoardPost(post) } label: {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(post.title.isEmpty ? "(제목 없음)" : post.title).font(.ui(.bodyMedium, model.boardRead.isRead(post) ? .regular : .bold))
                            .lineLimit(2).multilineTextAlignment(.leading)
                        Text([BoardCategory.studentNotice.label, prettyPortalDate(post.date)].compactMap { $0 }.joined(separator: " · "))
                            .font(.ui(.labelSmall)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 5).contentShape(Rectangle())
                }
                .buttonStyle(PressStyle())
            }
        }
    }
}

/// 시험까지 — 다가오는 시험 D-day, 없으면 학사일정 바로가기.
private struct ExamTile: View {
    let exam: ExamDday?
    @SharedModel private var model
    @Environment(\.palette) private var palette
    var body: some View {
        BentoTile(label: "시험까지", symbol: "pencil.and.list.clipboard", accent: Color(argb: 0xFF8E7CFF), action: { model.nav.open(.academic) }) {
            if let exam {
                BigValue(value: exam.days == 0 ? "D-Day" : "D-\(exam.days)")
                Text("\(exam.name) \(exam.date.month)/\(exam.date.day)").font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1)
            } else {
                DashboardEmpty("다가오는 시험이 없습니다")
            }
        }
    }
}

/// 날씨 — 지금 하늘·기온과 오늘 최고·최저. 누르면 시간별 날씨 화면.
private struct WeatherTile: View {
    let action: () -> Void
    @State private var forecast: Weather.Forecast? = Weather.cached(.hana)
    @Environment(\.palette) private var palette

    var body: some View {
        BentoTile(label: "날씨", symbol: "cloud.sun.fill", accent: Color(argb: 0xFF3B9BF5), action: action) {
            if let f = forecast {
                let day = f.days.first { $0.date == LocalDate.today() }
                HStack(spacing: 8) {
                    Image(systemName: Weather.symbol(f.currentCode)).symbolRenderingMode(.multicolor).font(.system(size: 26))
                    BigValue(value: Weather.deg(f.currentTemp) ?? "–")
                }
                let range = [Weather.deg(day?.max), Weather.deg(day?.min)].compactMap { $0 }.joined(separator: "/")
                Text([Weather.label(f.currentCode), range.isEmpty ? nil : range].compactMap { $0 }.joined(separator: " · "))
                    .font(.ui(.labelMedium)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1)
            } else {
                DashboardEmpty("불러오는 중")
            }
        }
        .task { if let f = await Weather.get(.hana) { forecast = f } }
    }
}

/// 칸 머리의 시각 — 오늘이면 '10:19', 아니면 '10. 23.'.
private func shortWhen(_ d: Date) -> String {
    d.localDate == LocalDate.today() ? d.hm : "\(d.localDate.month). \(d.localDate.day)."
}

/// 게시판 글의 날짜 문자열('2026-10-24 10:19' 등)에서 칸 머리용 짧은 시각.
private func boardTime(_ raw: String?) -> String? {
    guard let raw, let pretty = prettyPortalDate(raw) else { return nil }
    let parts = pretty.split(separator: " ")
    return parts.count == 2 && parts[0] == "\(LocalDate.today().month).\(LocalDate.today().day)" ? String(parts[1]) : String(parts[0])
}

// MARK: - 데일리 브리핑

/// 나우 브리프의 은은한 새벽빛 — 다른 카드(서리 재질)와 한눈에 갈리도록 불투명 그라데이션.
struct BriefPalette {
    let colors: [Color]; let text: Color; let sub: Color; let edge: Color; let chip: Color
    static func make(dark: Bool) -> BriefPalette {
        dark ? BriefPalette(colors: [Color(argb: 0xFF1B2646), Color(argb: 0xFF2A2044), Color(argb: 0xFF3A2826)], text: Color(argb: 0xFFF2F3F7),
                            sub: Color(argb: 0xFFB7BBC8), edge: .white.opacity(0.10), chip: .white.opacity(0.10))
            : BriefPalette(colors: [Color(argb: 0xFFD8E5FF), Color(argb: 0xFFEBE2FF), Color(argb: 0xFFFFE4D2)], text: Color(argb: 0xFF15171C),
                           sub: Color(argb: 0xFF4D5260), edge: .white.opacity(0.85), chip: .white.opacity(0.55))
    }
}

/// 홈의 데일리 브리핑 — 접으면 머리 한 줄, 펼치면 두세 문장과 이번 주에 챙길 것. 처음 만드는 동안은 '살펴보는 중' 한 줄만.
struct MorningBriefPanel: View {
    let brief: MorningBrief.Brief?
    let loading: Bool
    let today: LocalDate
    let startExpanded: Bool
    @Environment(\.palette) private var palette
    @State private var expanded: Bool?

    var body: some View {
        if brief != nil || loading {
            let p = BriefPalette.make(dark: palette.dark)
            let open = expanded ?? startExpanded
            let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles").font(.system(size: 12, weight: .semibold)).foregroundStyle(p.text)
                    Text("데일리 브리핑").font(.ui(.labelMedium, .semibold)).foregroundStyle(p.sub)
                    Spacer()
                    if loading { Text("만드는 중").font(.ui(.labelSmall)).foregroundStyle(p.sub) }
                    else { Image(systemName: "chevron.down").font(.system(size: 13, weight: .semibold)).foregroundStyle(p.sub).rotationEffect(.degrees(open ? 180 : 0)) }
                }
                Text(brief?.headline ?? "오늘 일정을 살펴보는 중").font(.ui(.titleMedium, .heavy)).foregroundStyle(p.text)
                    .lineLimit(open ? 3 : 1).padding(.top, 4)
                if open, let brief {
                    Text(brief.body).font(.ui(.bodyMedium)).foregroundStyle(p.sub).padding(.top, 6)
                    if !brief.highlights.isEmpty {
                        let w = brief.highlights.map { MorningBrief.relativeDay(today, $0.date) }.map { CGFloat($0.count) * 9 + 6 }.max() ?? 32
                        VStack(spacing: 0) { ForEach(Array(brief.highlights.enumerated()), id: \.offset) { _, h in BriefHighlightRow(h: h, today: today, p: p, labelWidth: max(32, w)) } }
                            .padding(.top, 8)
                    }
                    Text(brief.ai ? "AI가 만든 요약 · 틀릴 수 있어요" : "AI 없이 일정만 추렸어요").font(.ui(.labelSmall)).foregroundStyle(p.sub).padding(.top, 8)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(shape.fill(LinearGradient(colors: p.colors, startPoint: .topLeading, endPoint: .bottomTrailing)))
            .overlay(shape.strokeBorder(p.edge, lineWidth: 1))
            .contentShape(shape)
            .onTapGesture { if brief != nil { withAnimation(.spring(response: 0.3, dampingFraction: 0.9)) { expanded = !open } } }
        }
    }
}

struct BriefHighlightRow: View {
    let h: MorningBrief.Highlight
    let today: LocalDate
    let p: BriefPalette
    let labelWidth: CGFloat
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text(MorningBrief.relativeDay(today, h.date)).font(.ui(.labelLarge, .semibold)).foregroundStyle(p.text).lineLimit(1).fixedSize()
                .frame(width: labelWidth, alignment: .leading).padding(.top, 1)
            VStack(alignment: .leading, spacing: 0) {
                Text(h.title).font(.ui(.bodyMedium, .semibold)).foregroundStyle(p.text).lineLimit(1)
                if !h.note.trimmingCharacters(in: .whitespaces).isEmpty { Text(h.note).font(.ui(.labelSmall)).foregroundStyle(p.sub).lineLimit(2) }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 5)
    }
}

// MARK: - 그림

/// 원격 그림 — 받아 둔 것은 메모리에서 바로.
struct RemoteImage: View {
    let url: URL
    @Environment(\.palette) private var palette
    var body: some View {
        AsyncImage(url: url, transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
            switch phase {
            case .success(let image): image.resizable().scaledToFill()
            default: Rectangle().fill(palette.onSurface.opacity(0.08))
            }
        }
    }
}
