import SwiftUI

/*
 * 타임라인 — 안드로이드 TimeAxis.kt. 탭 대신 시간 하나로 앱을 봅니다.
 * - 시간 배율: 날마다 06:30~24:00 을 세로로 잇고, 왼쪽 줄엔 일과(수업·면학·급식), 오른쪽 줄엔 그 시각에 생긴 소식(게시판·클래스룸 글,
 *   시각이 있는 일정)을 말풍선으로. '지금' 선이 그어지고 처음엔 지금 근처에서 시작합니다.
 * - 하루 배율: 하루가 막대 한 줄 + 그날의 일정. 날을 누르면 그날의 시간 배율로.
 * - 주 배율: 주마다 일정 칩. 주를 누르면 하루 배율로.
 * - 두 손가락으로 오므리면 넓게(시간 → 하루 → 주), 벌리면 좁게. 오른쪽 위 보기 메뉴로도 바꿉니다(순정 캘린더와 같은 메뉴).
 */
enum AxisScale: Int, CaseIterable { case hour, day, month
    var label: String { switch self { case .hour: "시간"; case .day: "하루"; case .month: "주" } }
    /// 보기 메뉴의 기호 — 캘린더의 하루·여러 날·목록형 메뉴처럼.
    var symbol: String { switch self { case .hour: "calendar.day.timeline.left"; case .day: "list.bullet.below.rectangle"; case .month: "calendar" } }
}

private let hourHeight: CGFloat = 88
private let dayStart: CGFloat = 6.5
private let dayEnd: CGFloat = 24
private let gutter: CGFloat = 44
private let itemRadius: CGFloat = 12
/// 여러 날 보기에서 날짜 머리(날짜 + 하루 종일 일정 한두 줄)의 고정 높이 — 칸끼리 시각 줄이 맞게.
private let dayHeaderHeight: CGFloat = 76

/// 여러 날 보기 위에 고정된 날짜 머리 한 칸 — 날짜(오늘은 강조색)와 하루 종일 일정 몇 개.
private struct DayColumnHeader: View {
    let date: LocalDate
    let today: LocalDate
    let allDay: [AgendaItem]
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(dayLabel(date, today)).font(.headline)
                .foregroundStyle(date == today ? (onPage ?? palette.primary) : (onPage ?? palette.onSurface)).lineLimit(1)
            FlowLayout(spacing: 5, lineSpacing: 5) {
                ForEach(allDay.prefix(4)) { item in
                    let color = item.academic.map { ScheduleKind.color($0.code) } ?? (item.deadline ? Color(argb: 0xFFFF6B6B) : item.source.color)
                    Button { if item.academic != nil { model.nav.open(.academic) } else { model.openAgenda(item) } } label: {
                        Text(agendaTitle(item)).font(.caption.weight(.semibold)).foregroundStyle(color).lineLimit(1)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .modifier(TintFill(color: color, alpha: palette.dark ? 0.16 : 0.12))
                    }
                    .buttonStyle(PressStyle())
                }
            }
        }
        .padding(.horizontal, 10).padding(.top, 6)
        .frame(maxHeight: .infinity, alignment: .topLeading)
        .clipped()
    }
}

/// 여러 날 보기의 왼쪽 시각 눈금 — 날짜 머리 높이만큼 비우고 7:00~23:00, 지금 시각 알약.
private struct TimeGutter: View {
    let focusHour: CGFloat
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .topLeading) {
                Color.clear.frame(width: 1, height: 1).id("axis-focus")
                    .padding(.top, max(0, hourHeight * max(0, focusHour - dayStart) - 140))
                ForEach(7..<24, id: \.self) { h in
                    Text("\(h):00").font(.ui(.labelSmall)).foregroundStyle(onPage?.opacity(0.9) ?? palette.onSurfaceVariant)
                        .frame(width: gutter - 6, alignment: .leading).offset(x: 4, y: hourHeight * (CGFloat(h) - dayStart) - 8)
                }
                let m = CGFloat(model.now.minutesOfDay) / 60
                if m >= dayStart {
                    Text(model.now.hm).font(.ui(.labelSmall, .heavy)).foregroundStyle(palette.onPrimary)
                        .lineLimit(1).fixedSize()
                        .padding(.horizontal, 5).padding(.vertical, 1)
                        .background(RoundedRectangle(cornerRadius: 8).fill(palette.primary))
                        .offset(x: 2, y: hourHeight * (m - dayStart) - 9)
                }
            }
            .frame(width: gutter, height: hourHeight * (dayEnd - dayStart), alignment: .topLeading)
        }
    }
}

/// 축 위의 한 칸 — 일과 블록(왼쪽 줄) 또는 소식 말풍선(오른쪽 줄).
struct AxisEntry: Identifiable {
    let id = UUID()
    let start: Date
    let end: Date?
    let title: String
    let meta: String?
    let source: String
    let color: Color
    let action: (() -> Void)?
}

struct TimeAxisView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    @State private var scale: AxisScale = .hour
    @State private var focusDay = LocalDate.today()
    @State private var jumpTick = 0
    @State private var pinchDone = false
    /// 넓은 화면(아이패드 가로 등)의 폭 — 시간 배율을 여러 날 나란히(캘린더의 '여러 날'처럼).
    @State private var width: CGFloat = 0
    @State private var widthTask: Task<Void, Never>?
    /// 여러 날 보기에서 맨 왼쪽 날.
    @State private var firstColumn: LocalDate? = LocalDate.today()
    /// 여러 날 보기의 위아래 자리 — 칸 안의 표식으로 옮기면 가로 스크롤 안이라 무시돼서 좌표로.
    @State private var verticalPosition = ScrollPosition(edge: .top)

    private var ink: Color { onPage ?? palette.onSurface }
    private var accent: Color { onPage ?? palette.primary }

    var body: some View {
        let today = model.today
        let agenda = Agenda.merge(model.schedule, grade: Prefs.shared.studentGrade, extras: model.agendaExtras, today: today.adding(days: -1))
        VStack(spacing: 0) {
            Group {
                switch scale {
                case .hour:
                    if width >= 900 {
                        multiDay(today: today, agenda: agenda)
                    } else {
                        ScrollViewReader { proxy in
                            ScrollView {
                                VStack(spacing: 0) {
                                    ForEach((-1...6).map { today.adding(days: $0) }, id: \.self) { date in
                                        let (left, right) = entries(date, agenda: agenda)
                                        HourDay(date: date, today: today, now: model.now, left: left, right: right, allDay: allDay(date, agenda, today),
                                                focusHour: date == focusDay ? (focusDay == model.today ? CGFloat(model.now.minutesOfDay) / 60 : 8) : nil)
                                    }
                                    Color.clear.frame(height: 24)
                                }
                                .readableWidth(860)
                            }
                            // 위 막대 아래로 지나가는 날짜 머리·칸이 단추와 겹쳐 보이지 않게 흐림을 깝니다.
                            .topEdgeBlur()
                            // 홈과 같은 전체 갱신 — 목록 맨 위에서 당기면(상태 막대를 누르면 맨 위로).
                            .refreshable { await model.refreshAll() }
                            .onAppear { proxy.scrollTo("axis-focus", anchor: .top) }
                            .onChange(of: jumpTick) { _, _ in proxy.scrollTo("axis-focus", anchor: .top) }
                        }
                    }
                case .day:
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach((0...13).map { today.adding(days: $0) }, id: \.self) { date in
                                let (left, right) = entries(date, agenda: agenda)
                                DayCard(date: date, today: today, left: left, right: right, allDay: allDay(date, agenda, today)) {
                                    focusDay = date
                                    scale = .hour
                                }
                            }
                            Color.clear.frame(height: 24)
                        }
                        .readableWidth(860)
                    }
                    .refreshable { await model.refreshAll() }
                case .month:
                    MonthWeeks(today: today, agenda: agenda) { monday in
                        focusDay = monday > today ? monday : today
                        scale = .day
                    }
                }
            }
        }
        // 폭은 회전이 끝난 뒤 한 번만 — 회전하는 동안 매 프레임 바꾸면 보름치 일정을 매번 다시 그려 멈췄습니다(홈과 같은 문제).
        .onGeometryChange(for: CGFloat.self) { $0.size.width.rounded() } action: { w in
            widthTask?.cancel()
            if width == 0 { width = w; return }
            widthTask = Task { try? await Task.sleep(for: .milliseconds(200)); if !Task.isCancelled { width = w } }
        }
        .navigationTitle("\(today.month)월 \(today.day)일")
        // 여러 날 보기는 위에 날짜 머리가 있어 큰 제목 대신 작은 제목(세로 공간을 시간 칸에).
        .navigationBarTitleDisplayMode(width >= 900 && scale == .hour ? .inline : .automatic)
        .axisSubtitle("\(today.weekdayShort)요일")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { todayButton }
            // 칸 폭을 줄인 묶음(BarButton) — 홈과 같게.
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 0) {
                    Menu {
                        Picker("보기", selection: $scale) {
                            ForEach(AxisScale.allCases, id: \.self) { Label($0.label, systemImage: $0.symbol).tag($0) }
                        }
                    } label: {
                        BarGlyph(symbol: scale.symbol)
                    }
                    .accessibilityLabel("보기")
                    BarButton(title: "검색", symbol: "magnifyingglass") { model.nav.open(.portalSearch) }
                    // 캘린더 앱처럼 + — 보고 있던 날로 새 일정을 엽니다.
                    BarButton(title: "일정 추가", symbol: "plus") { model.nav.present(UserEventEditor(day: focusDay)) }
                }
            }
        }
        .simultaneousGesture(MagnifyGesture()
            .onChanged { v in
                guard !pinchDone else { return }
                if v.magnification < 0.72 { scale = AxisScale(rawValue: min(scale.rawValue + 1, 2))!; pinchDone = true }
                else if v.magnification > 1.4 { scale = AxisScale(rawValue: max(scale.rawValue - 1, 0))!; pinchDone = true }
            }
            .onEnded { _ in pinchDone = false })
        .onChange(of: scale) { _, s in if s == .hour { jumpTo(focusDay, now: focusDay == model.today) } }
        .onAppear { jumpTo(model.today, now: true) }
        // 타임라인으로 올 때마다 낡은 게시판 글·일정·클래스룸을 조용히 — 당기지 않아도 새것이 보이게.
        .task { await model.refreshStale() }
    }

    private var todayButton: some View {
        Button("오늘") {
            focusDay = model.today
            scale = .hour
            jumpTo(model.today, now: true)
        }
    }

    /// 지금 근처 — 그날 머리에서 그 시각까지 내려가고, 화면 위쪽 1/4 쯤에 오게(그 자리에 놓은 표식으로).
    private func jumpTo(_ date: LocalDate, now: Bool) {
        focusDay = date
        firstColumn = date
        DispatchQueue.main.async { jumpTick += 1 }
    }

    /// 넓은 화면의 시간 배율 — 왼쪽에 시각 눈금 한 줄, 오른쪽에 날을 여러 칸 나란히(옆으로 밀어 다른 날). 위아래는 함께 스크롤.
    @ViewBuilder private func multiDay(today: LocalDate, agenda: [AgendaItem]) -> some View {
        let columns = min(5, max(2, Int((width - gutter) / 300)))
        let colW = (width - gutter) / CGFloat(columns)
        let first = firstColumn ?? today
        VStack(spacing: 0) {
            // 날짜 머리는 위에 고정 — 위아래로 스크롤해도 어느 칸이 무슨 날인지 보이게(캘린더처럼).
            HStack(alignment: .top, spacing: 0) {
                Color.clear.frame(width: gutter)
                ForEach((0..<columns).map { first.adding(days: $0) }, id: \.self) { date in
                    DayColumnHeader(date: date, today: today, allDay: allDay(date, agenda, today))
                        .frame(width: colW, alignment: .leading)
                }
            }
            .frame(height: dayHeaderHeight, alignment: .top)
            .animation(.spring(response: 0.35, dampingFraction: 0.9), value: first)
            ScrollViewReader { proxy in
                ScrollView(.vertical) {
                    HStack(alignment: .top, spacing: 0) {
                        TimeGutter(focusHour: focusDay == model.today ? CGFloat(model.now.minutesOfDay) / 60 : 8)
                        ScrollView(.horizontal) {
                            LazyHStack(alignment: .top, spacing: 0) {
                                ForEach((-1...13).map { today.adding(days: $0) }, id: \.self) { date in
                                    let (left, right) = entries(date, agenda: agenda)
                                    HourDay(date: date, today: today, now: model.now, left: left, right: right, allDay: [], columnMode: true)
                                        .frame(width: colW)
                                        .id(date)
                                }
                            }
                            .scrollTargetLayout()
                        }
                        // 넘기기는 하루씩만 — 세게 밀어도 한 칸.
                        .scrollTargetBehavior(.viewAligned(limitBehavior: .alwaysByOne))
                        .scrollPosition(id: $firstColumn, anchor: .leading)
                        .scrollIndicators(.hidden)
                        // 가로 스크롤은 내용 폭만큼 커지려 해서 — 화면 폭에 묶어 둡니다.
                        .frame(width: max(0, width - gutter))
                    }
                    .padding(.top, 8)
                    Color.clear.frame(height: 24)
                }
                .scrollPosition($verticalPosition)
                .topEdgeBlur()
                .refreshable { await model.refreshAll() }
                // 처음엔 고른 날(보통 오늘)을 맨 왼쪽에, 위아래는 지금 시각 근처로 — scrollPosition 만으로는 처음 자리가 안 잡힙니다.
                .onAppear { DispatchQueue.main.async { jumpColumns(proxy, to: first) } }
                .onChange(of: jumpTick) { _, _ in jumpColumns(proxy, to: focusDay) }
            }
        }
    }

    private func jumpColumns(_ proxy: ScrollViewProxy, to day: LocalDate) {
        proxy.scrollTo(day, anchor: .leading)
        firstColumn = day
        let hour = day == model.today ? CGFloat(model.now.minutesOfDay) / 60 : 8
        let y = max(0, hourHeight * (hour - dayStart) - 140)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { withAnimation { verticalPosition.scrollTo(y: y) } }
    }

    // MARK: 칸 만들기

    private func entries(_ date: LocalDate, agenda: [AgendaItem]) -> ([AxisEntry], [AxisEntry]) {
        let blocks = model.blocks(for: date)
        var left: [AxisEntry] = []
        for b in blocks where !b.isBlank {
            let meal: Meal? = switch b.accent { case .breakfast: .breakfast; case .lunch: .lunch; case .dinner: .dinner; default: nil }
            if b.isGap && meal == nil { continue }
            if let meal {
                let menu = model.mealDays[date.iso]?.items[meal] ?? MealCache.load(date)?.items[meal] ?? []
                let names = menu.prefix(3).map(\.name).joined(separator: " · ")
                left.append(AxisEntry(start: b.start, end: b.end, title: "\(meal.label) 급식", meta: names.isEmpty ? nil : names, source: "급식", color: b.accent.color) {
                    model.nav.open(.meal(date))
                })
            } else {
                // '수업 중'은 지금 하는 수업에만 맞는 말 — 축엔 다른 날·시각도 있어 교시와 과목만.
                let title: String
                if case .lesson(let p, let l) = b.kind { title = l.isFree ? "\(p)교시 · 공강" : "\(p)교시 \(l.title)" } else { title = b.headline }
                let action: (() -> Void)?
                switch b.kind {
                case .lesson: action = { model.nav.open(.timetable) }
                case .study: action = { model.nav.open(.apply) }
                case .outing: action = { model.nav.open(.history(.outing)) }
                default: action = nil
                }
                left.append(AxisEntry(start: b.start, end: b.end, title: title, meta: b.room, source: "", color: b.accent.color, action: action))
            }
        }
        var right: [AxisEntry] = []
        for post in model.homeBoardPosts {
            guard let at = parsePostTime(post.date), at.localDate == date else { continue }
            right.append(AxisEntry(start: at, end: nil, title: post.title.isEmpty ? "(제목 없음)" : post.title, meta: post.writer, source: "게시판", color: AgendaSource.board.color) { model.openBoardPost(post) })
        }
        if let classroom = model.classroomSnapshot {
            for post in classroom.posts where post.updated.localDate == date {
                let title = post.title.isEmpty ? (post.text.split(separator: "\n").first.map(String.init) ?? post.kind.label) : post.title
                right.append(AxisEntry(start: post.updated, end: nil, title: title, meta: classroom.course(post.courseId)?.name, source: "클래스룸", color: AgendaSource.classroom.color) { model.openClassroomPost(post) })
            }
        }
        for item in agenda where item.date == date {
            guard let t = item.time, let r = t.range(of: #"(\d{1,2}):(\d{2})"#, options: .regularExpression) else { continue }
            let p = t[r].split(separator: ":").compactMap { Int($0) }
            guard p.count == 2 else { continue }
            let color = item.deadline ? Color(argb: 0xFFFF6B6B) : item.academic.map { ScheduleKind.color($0.code) } ?? item.source.color
            let source = item.deadline ? "마감" : item.academic.map { ScheduleKind.label($0.code) } ?? item.source.label
            right.append(AxisEntry(start: date.at(minutes: p[0] * 60 + p[1]), end: nil, title: item.title, meta: item.detail, source: source, color: color) {
                if item.academic != nil { model.nav.open(.academic) } else { model.openAgenda(item) }
            })
        }
        return (left, right.sorted { $0.start < $1.start })
    }

    /// 그날 시각 없는 일정 — 기간 일정은 시작하는 날에만, 이미 진행 중인 것은 오늘에만.
    private func allDay(_ date: LocalDate, _ agenda: [AgendaItem], _ today: LocalDate) -> [AgendaItem] {
        agenda.filter { $0.time == nil && ($0.date == date || (date == today && $0.date < today && ($0.endDate ?? $0.date) >= today)) }
    }

    private func parsePostTime(_ raw: String?) -> Date? {
        guard let raw, let r = raw.range(of: #"(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?"#, options: .regularExpression) else { return nil }
        let nums = raw[r].split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
        guard nums.count >= 3 else { return nil }
        let d = LocalDate(year: nums[0], month: nums[1], day: nums[2])
        return d.at(minutes: (nums.count >= 5 ? nums[3] * 60 + nums[4] : 8 * 60))
    }
}

/// 면학감독은 제목이 선생님 이름뿐이라 '면학감독 ○○○' 으로.
private func agendaTitle(_ item: AgendaItem) -> String { item.isSupervision ? "면학감독 \(item.title)" : item.title }

private func dayLabel(_ date: LocalDate, _ today: LocalDate) -> String {
    let rel: String? = date == today ? "오늘" : date == today.adding(days: 1) ? "내일" : date == today.adding(days: -1) ? "어제" : nil
    return "\(date.month)월 \(date.day)일 \(date.weekdayShort)" + (rel.map { " · \($0)" } ?? "")
}

/// 일정 칩·블록·말풍선의 바탕 — 서리 유리 위에 색을 옅게.
private struct TintFill: ViewModifier {
    let color: Color?
    let alpha: Double
    func body(content: Content) -> some View {
        content.background {
            ZStack {
                FrostBackground(shape: RoundedRectangle(cornerRadius: itemRadius, style: .continuous))
                if let color { RoundedRectangle(cornerRadius: itemRadius, style: .continuous).fill(color.opacity(alpha)) }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: itemRadius, style: .continuous))
    }
}

// MARK: - 시간 배율의 하루

private struct HourDay: View {
    let date: LocalDate
    let today: LocalDate
    let now: Date
    let left: [AxisEntry]
    let right: [AxisEntry]
    let allDay: [AgendaItem]
    /// 처음 보일 자리(시) — 그 자리에서 조금 위에 표식을 둡니다.
    var focusHour: CGFloat? = nil
    /// 여러 날 나란히 보기의 한 칸 — 시각 눈금은 왼쪽 TimeGutter 가 맡고, 날짜 머리는 같은 높이로(칸끼리 시각이 맞게).
    var columnMode = false
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    var body: some View {
        let ink = onPage ?? palette.onSurface
        VStack(alignment: .leading, spacing: 0) {
            if !columnMode {
            VStack(alignment: .leading, spacing: 6) {
                Text(dayLabel(date, today)).font(.ui(.titleMedium, .heavy)).foregroundStyle(date == today ? (onPage ?? palette.primary) : ink)
                if !allDay.isEmpty {
                    FlowLayout(spacing: 6, lineSpacing: 6) {
                        ForEach(allDay.prefix(6)) { item in
                            let color = item.academic.map { ScheduleKind.color($0.code) } ?? (item.deadline ? Color(argb: 0xFFFF6B6B) : item.source.color)
                            Button { if item.academic != nil { model.nav.open(.academic) } else { model.openAgenda(item) } } label: {
                                Text(agendaTitle(item)).font(.ui(.labelMedium, .bold)).foregroundStyle(color).lineLimit(1)
                                    .padding(.horizontal, 10).padding(.vertical, 5)
                                    .modifier(TintFill(color: color, alpha: palette.dark ? 0.16 : 0.12))
                            }
                            .buttonStyle(PressStyle())
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 18).padding(.bottom, 6)
            }
            GeometryReader { g in
                let gutter = columnMode ? 0 : gutter
                let fullW = g.size.width - gutter - 10
                let narrowW = fullW * 0.58
                let bubbleX = gutter + 6 + narrowW + 4
                let bubbleW = fullW - narrowW - 4
                let tops = bubbleTops(width: bubbleW)
                ZStack(alignment: .topLeading) {
                    if let focusHour {
                        Color.clear.frame(width: 1, height: 1).id("axis-focus")
                            .padding(.top, max(0, hourHeight * max(0, focusHour - dayStart) - 140))
                    }
                    // 시각 눈금 — 사진 위에 바로 놓이니 사진 밝기 색으로.
                    ForEach(7..<24, id: \.self) { h in
                        let yy = hourHeight * (CGFloat(h) - dayStart)
                        if !columnMode {
                            Text("\(h):00").font(.ui(.labelSmall)).foregroundStyle(onPage?.opacity(0.9) ?? palette.onSurfaceVariant)
                                .frame(width: gutter - 6, alignment: .leading).offset(x: 4, y: yy - 8)
                        }
                        Rectangle().fill(ink.opacity(0.08)).frame(width: g.size.width - gutter - 4, height: 1).offset(x: gutter, y: yy)
                    }
                    Rectangle().fill(ink.opacity(0.14)).frame(width: 2, height: g.size.height).offset(x: gutter)
                    ForEach(left) { e in
                        let end = e.end ?? e.start.addingTimeInterval(50 * 60)
                        let top = y(e.start)
                        let h = max(34, y(end) - top - 2)
                        let shared = tops.contains { $0.top < top + h && $0.top + $0.height > top }
                        Button { e.action?() } label: {
                            VStack(alignment: .leading, spacing: 0) {
                                Text("\(e.start.hm) – \(end.hm)").font(.ui(.labelSmall, .bold)).foregroundStyle(e.color).lineLimit(1)
                                Text(e.title).font(.ui(.bodyMedium, .bold)).lineLimit(2)
                                if let m = e.meta { Text(m).font(.ui(.labelSmall)).foregroundStyle(palette.onSurfaceVariant).lineLimit(2) }
                            }
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .frame(width: shared ? narrowW : fullW, height: h, alignment: .topLeading)
                            .modifier(TintFill(color: e.color, alpha: palette.dark ? 0.18 : 0.14))
                            .foregroundStyle(palette.onSurface)
                            .environment(\.onPage, nil)
                        }
                        .buttonStyle(PressStyle())
                        .disabled(e.action == nil)
                        .opacity(end <= now ? 0.45 : 1)
                        .offset(x: gutter + 6, y: top)
                    }
                    ForEach(Array(right.enumerated()), id: \.element.id) { i, e in
                        Button { e.action?() } label: {
                            VStack(alignment: .leading, spacing: 0) {
                                HStack(spacing: 5) {
                                    Circle().fill(e.color).frame(width: 7, height: 7)
                                    Text(e.source).font(.ui(.labelSmall, .bold)).foregroundStyle(e.color).lineLimit(1)
                                    Spacer(minLength: 0)
                                    Text(e.start.hm).font(.ui(.labelSmall)).foregroundStyle(palette.onSurfaceVariant)
                                }
                                Text(e.title).font(.ui(.bodySmall, .semibold)).lineLimit(2).multilineTextAlignment(.leading)
                            }
                            .padding(.horizontal, 10).padding(.vertical, 7)
                            .frame(width: bubbleW, alignment: .leading)
                            .modifier(TintFill(color: nil, alpha: 0))
                            .foregroundStyle(palette.onSurface)
                        }
                        .buttonStyle(PressStyle())
                        .opacity(e.start < now ? 0.7 : 1)
                        .offset(x: bubbleX, y: tops[i].top)
                    }
                    if date == now.localDate {
                        let yy = y(now)
                        Rectangle().fill(palette.primary).frame(width: g.size.width - gutter - 4, height: 2).offset(x: gutter - 4, y: yy - 1)
                        Circle().fill(palette.primary).frame(width: 10, height: 10).offset(x: gutter - 4, y: yy - 5)
                        if !columnMode {
                            Text(now.hm).font(.ui(.labelSmall, .heavy)).foregroundStyle(palette.onPrimary)
                                .padding(.horizontal, 5).padding(.vertical, 1)
                                .background(RoundedRectangle(cornerRadius: 8).fill(palette.primary))
                                .offset(x: 2, y: yy - 9)
                        }
                    }
                }
            }
            .frame(height: hourHeight * (dayEnd - dayStart))
        }
    }

    private func y(_ t: Date) -> CGFloat {
        let minutes = CGFloat(Seoul.calendar.dateComponents([.minute], from: date.startOfDay, to: t).minute ?? 0)
        return hourHeight * (minutes / 60 - dayStart).clamped(0, dayEnd - dayStart)
    }

    /// 말풍선 자리 — 겹치면 아래로 밀어 4 띄워 쌓습니다. 높이는 제목 줄 수로 어림합니다.
    private func bubbleTops(width: CGFloat) -> [(top: CGFloat, height: CGFloat)] {
        var floor: CGFloat = 0
        return right.map { e in
            let charsPerLine = max(6, Int((width - 20) / 12.5))
            let lines = min(2, max(1, Int(ceil(Double(e.title.count) / Double(charsPerLine)))))
            let h: CGFloat = 14 + 16 + CGFloat(lines) * 18
            let top = max(y(e.start), floor)
            floor = top + h + 4
            return (top, h)
        }
    }
}

// MARK: - 하루 배율

private struct DayCard: View {
    let date: LocalDate
    let today: LocalDate
    let left: [AxisEntry]
    let right: [AxisEntry]
    let allDay: [AgendaItem]
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let from = date.at(minutes: 7 * 60)
        let span: Double = (16 * 60 + 30) * 60
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                Text(dayLabel(date, today)).font(.ui(.titleSmall, .heavy))
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        ForEach(left) { e in
                            let s = (e.start.timeIntervalSince(from) / span).clamped(0, 1)
                            let en = ((e.end ?? e.start.addingTimeInterval(3000)).timeIntervalSince(from) / span).clamped(0, 1)
                            if en > s { Rectangle().fill(e.color.opacity(0.85)).frame(width: max(0, g.size.width * (en - s) - 1)).offset(x: g.size.width * s) }
                        }
                    }
                }
                .frame(height: 22)
                .background(palette.onSurface.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 7))
                let marks = allDay.map { (agendaTitle($0), $0.academic.map { ScheduleKind.color($0.code) } ?? $0.source.color) }
                    + right.filter { $0.source != "게시판" && $0.source != "클래스룸" }.map { ("\($0.start.hm) \($0.title)", $0.color) }
                ForEach(Array(marks.prefix(3).enumerated()), id: \.offset) { _, m in
                    HStack(spacing: 8) {
                        Circle().fill(m.1).frame(width: 8, height: 8)
                        Text(m.0).font(.ui(.bodySmall)).lineLimit(1)
                    }
                }
                let news = right.filter { $0.source == "게시판" || $0.source == "클래스룸" }.count
                if news > 0 { Text("소식 \(news)").font(.ui(.labelSmall)).foregroundStyle(palette.onSurfaceVariant) }
            }
            .foregroundStyle(palette.onSurface)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                FrostBackground(shape: shape)
                if date == today { shape.fill(palette.primary.opacity(palette.dark ? 0.16 : 0.10)) }
            }
            .clipShape(shape)
            .environment(\.onPage, nil)
        }
        .buttonStyle(PressStyle())
        .padding(.horizontal, 16)
    }
}

// MARK: - 주 배율

private struct MonthWeeks: View {
    let today: LocalDate
    let agenda: [AgendaItem]
    let onOpenWeek: (LocalDate) -> Void
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage

    var body: some View {
        let thisMonday = today.monday
        // 이번 주부터 4주 — 일정이 하나도 없는 주가 나오면 거기서 끊습니다.
        let all = (0...3).map { thisMonday.adding(days: 7 * $0) }
        let firstEmpty = all.firstIndex { m in m != thisMonday && !agenda.contains { $0.date >= m && $0.date <= m.adding(days: 6) } }
        let weeks = firstEmpty.map { Array(all.prefix($0)) } ?? all
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(weeks, id: \.self) { monday in
                    let sunday = monday.adding(days: 6)
                    let events = agenda.filter { ($0.date >= monday && $0.date <= sunday) || (monday == thisMonday && $0.date < monday && ($0.endDate ?? $0.date) >= monday) }
                    Button { onOpenWeek(monday) } label: {
                        HStack(alignment: .top, spacing: 12) {
                            VStack(alignment: .trailing, spacing: 0) {
                                if monday.day <= 7 || monday == thisMonday { Text("\(monday.month)월").font(.ui(.labelSmall, .heavy)).foregroundStyle(onPage ?? palette.primary) }
                                Text("\(monday.day)").font(.ui(.titleSmall, .bold)).foregroundStyle(monday == thisMonday ? (onPage ?? palette.primary) : (onPage ?? palette.onSurface))
                            }
                            .frame(width: 44, alignment: .trailing)
                            FlowLayout(spacing: 6, lineSpacing: 6) {
                                if events.isEmpty { Text("—").font(.ui(.bodySmall)).foregroundStyle(onPage?.opacity(0.9) ?? palette.onSurfaceVariant) }
                                ForEach(events.prefix(6)) { item in
                                    let color = item.deadline ? Color(argb: 0xFFFF6B6B) : item.academic.map { ScheduleKind.color($0.code) } ?? item.source.color
                                    Text("\(item.date.day)일 \(agendaTitle(item))").font(.ui(.labelMedium, .bold)).foregroundStyle(color).lineLimit(1)
                                        .padding(.horizontal, 10).padding(.vertical, 5)
                                        .modifier(TintFill(color: color, alpha: palette.dark ? 0.16 : 0.12))
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(.leading, 16).padding(.trailing, 16).padding(.top, 6).padding(.bottom, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressStyle())
                }
                Color.clear.frame(height: 24)
            }
            .readableWidth(860)
        }
    }
}

private extension View {
    @ViewBuilder func topEdgeBlur() -> some View {
        if #available(iOS 26.0, *) { scrollEdgeEffectStyle(.soft, for: .top) } else { self }
    }

    @ViewBuilder func axisSubtitle(_ text: String) -> some View {
        if #available(iOS 26.0, *) { navigationSubtitle(text) } else { self }
    }
}
