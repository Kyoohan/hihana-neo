import SwiftUI

/// 급식 — 이번 주 날짜 줄(오늘이 먼저 골라져 있음)과 그날 끼니별 식단·사진 카드. 알레르기 설정은 머리의 경고.
struct MealView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage
    @Binding var selected: LocalDate
    @State private var focused = false
    @State private var gridWidth: CGFloat = 0

    var body: some View {
        let today = model.today
        let day = selected
        let meals = model.mealDays[day.iso]
        let present = meals?.presentMeals ?? []
        // 오늘을 보고 있으면 지금(다음) 끼니로 먼저 내려갑니다.
        let focus = selected != today ? nil : model.mealPick(now: model.now).flatMap { $0.tomorrow ? nil : $0.meal }
        ScrollViewReader { proxy in
            SheetScroll(maxWidth: 1180) {
                Color.clear.frame(height: 0).onGeometryChange(for: CGFloat.self) { $0.size.width } action: { gridWidth = $0 }
                HStack {
                    Spacer()
                    if model.isLoadingMeals { OneUiLoading(size: 18, stroke: 2) }
                }
                Group {
                    // 캘린더 앱처럼 요일 위, 날짜는 동그라미 — 고른 날은 강조색, 식단이 없는 날은 흐리게.
                    HStack(spacing: 0) {
                        ForEach((0..<7).map { today.adding(days: $0) }, id: \.self) { d in
                            let on = d == selected
                            Button { withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { selected = d } } label: {
                                VStack(spacing: 6) {
                                    Text(d.weekdayShort).font(.caption.weight(.semibold)).foregroundStyle(d.weekday >= 6 ? AnyShapeStyle(Color.red.opacity(0.8)) : AnyShapeStyle(.secondary))
                                    Text("\(d.day)").font(.headline.monospacedDigit())
                                        .foregroundStyle(on ? Color.white : Color.primary)
                                        .frame(width: 38, height: 38)
                                        .background(on ? AnyShapeStyle(.tint) : AnyShapeStyle(.clear), in: Circle())
                                }
                                .frame(maxWidth: .infinity)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .opacity(model.mealDays[d.iso] == nil ? 0.45 : 1)
                        }
                    }
                    .padding(.vertical, 10)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .padding(.vertical, 8)
                }
                if present.isEmpty && model.isLoadingMeals {
                    LoadingBlock()
                } else if present.isEmpty {
                    Text("\(selected.day) \(selected.weekdayShort) 식단이 없습니다")
                        .font(.ui(.bodyLarge)).foregroundStyle(onPage?.opacity(0.9) ?? palette.onSurfaceVariant).padding(.vertical, 32)
                } else if let meals {
                    // 넓은 화면(아이패드)은 끼니 카드를 두 열로 — 같은 줄의 두 칸은 높이를 맞춰 들쭉날쭉하지 않게.
                    if gridWidth >= 700 {
                        Grid(alignment: .topLeading, horizontalSpacing: 14, verticalSpacing: 0) {
                            ForEach(Array(stride(from: 0, to: present.count, by: 2)), id: \.self) { i in
                                GridRow {
                                    ForEach(present[i..<min(i + 2, present.count)], id: \.self) { meal in
                                        MealCard(meal: meal, items: meals.items[meal] ?? [], photo: meals.photos[meal], info: model.neisDays[day.iso]?[meal], fill: true)
                                            .id(meal)
                                    }
                                    if i + 1 >= present.count { Color.clear.gridCellUnsizedAxes(.vertical) }
                                }
                            }
                        }
                    } else {
                        ForEach(present, id: \.self) { meal in
                            MealCard(meal: meal, items: meals.items[meal] ?? [], photo: meals.photos[meal], info: model.neisDays[day.iso]?[meal])
                                .id(meal)
                        }
                    }
                }
            }
            .onAppear {
                // 홈 칸이 보여 주던 끼니부터 — 한 번만.
                if !focused, let focus, present.firstIndex(of: focus).map({ $0 > 0 }) == true {
                    focused = true
                    DispatchQueue.main.async { proxy.scrollTo(focus, anchor: .top) }
                }
            }
        }
        .task(id: selected.iso) {
            for i in 0..<7 { await model.loadMealDay(today.adding(days: i), force: false) }
            await model.loadNeis(day)
        }
    }
}

/// 끼니 카드 — 색 원 배지·이름·열량, 사진(누르면 크게), 메뉴(알레르기 항목은 빨갛게), 영양성분·원산지(접어 둠).
struct MealCard: View {
    let meal: Meal
    let items: [MealItem]
    let photo: String?
    var info: NeisMealInfo?
    @Environment(\.palette) private var palette
    @State private var viewer = false
    private var prefs = Prefs.shared

    /// 두 열 줄에서 옆 칸 높이에 맞춰 늘어날지.
    var fill = false

    init(meal: Meal, items: [MealItem], photo: String?, info: NeisMealInfo?, fill: Bool = false) {
        self.meal = meal; self.items = items; self.photo = photo; self.info = info; self.fill = fill
    }

    var body: some View {
        let badge: Color = meal == .breakfast ? OneUi.lime : meal == .snack ? OneUi.violet : OneUi.sky
        OneUiCard {
            HStack(spacing: 12) {
                TintedCircleIcon(symbol: "fork.knife", color: badge)
                Text(meal.label).font(.ui(.titleMedium, .bold))
                Spacer()
                if let kcal = info?.kcal {
                    Text("\(Double(kcal).map { $0.formatted(.number.precision(.fractionLength(0))) } ?? kcal) kcal")
                        .font(.ui(.labelLarge)).foregroundStyle(palette.onSurfaceVariant)
                }
            }
            if let url = MealParser.thumbURL(photo) {
                RemoteImage(url: url).frame(height: 170).frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: OneUi.cornerMedium, style: .continuous))
                    .contentShape(Rectangle())
                    .onTapGesture { viewer = true }
                    .padding(.top, 12)
                    .fullScreenCover(isPresented: $viewer) { PhotoViewer(urls: [MealClient.fullURL(photo), url].compactMap { $0 }) }
            }
            VStack(alignment: .leading, spacing: 0) {
                ForEach(items, id: \.name) { item in
                    let labels = Allergens.shortLabels(Allergens.detect(codes: item.codes, menuText: item.name, selected: prefs.allergyCodes))
                    HStack(spacing: 8) {
                        Text("·").font(.ui(.bodyLarge)).foregroundStyle(palette.onSurfaceVariant)
                        Text(item.name).font(.ui(.bodyLarge, labels.isEmpty ? .regular : .bold)).foregroundStyle(labels.isEmpty ? palette.onSurface : palette.error)
                        if !labels.isEmpty {
                            Label(labels.joined(separator: "·"), systemImage: "exclamationmark.triangle.fill")
                                .labelStyle(.titleAndIcon).font(.ui(.labelMedium, .bold)).foregroundStyle(palette.error)
                        }
                    }
                    .padding(.vertical, 3)
                }
            }
            .padding(.top, 10)
            if let info, !info.nutrients.isEmpty || !info.origins.isEmpty { MealInfoSection(info: info) }
            if fill { Spacer(minLength: 0) }
        }
        .frame(maxHeight: fill ? .infinity : nil, alignment: .top)
        .padding(.vertical, 6)
    }
}

/// 영양성분·원산지 — 접어 두고 누르면 펼칩니다(NEIS).
private struct MealInfoSection: View {
    let info: NeisMealInfo
    @Environment(\.palette) private var palette
    @State private var expanded = false

    var body: some View {
        Rectangle().fill(palette.outlineVariant).frame(height: 0.8).padding(.top, 10)
        Button { withAnimation(.spring(response: 0.3, dampingFraction: 0.9)) { expanded.toggle() } } label: {
            HStack {
                Text("영양성분·원산지" + (info.servings.map { " · \($0.formatted())명 급식" } ?? "")).font(.ui(.labelLarge)).foregroundStyle(palette.onSurfaceVariant)
                Spacer()
                Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.onSurfaceVariant)
            }
            .padding(.top, 10).padding(.bottom, 2).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        if expanded {
            VStack(alignment: .leading, spacing: 2) {
                ForEach(Array(stride(from: 0, to: info.nutrients.count, by: 2)), id: \.self) { i in
                    HStack(spacing: 0) {
                        ForEach(info.nutrients[i..<min(i + 2, info.nutrients.count)], id: \.name) { n in
                            let unit = n.name.range(of: #"\(([^)]+)\)"#, options: .regularExpression).map { String(n.name[$0].dropFirst().dropLast()) } ?? ""
                            let label = n.name.replacingOccurrences(of: #"\([^)]*\)"#, with: "", options: .regularExpression)
                            HStack(spacing: 6) {
                                Text(label).font(.ui(.bodyMedium)).foregroundStyle(palette.onSurfaceVariant)
                                Text(n.value + unit).font(.ui(.bodyMedium, .semibold))
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        if i + 1 >= info.nutrients.count { Spacer().frame(maxWidth: .infinity) }
                    }
                    .padding(.vertical, 2)
                }
                if !info.origins.isEmpty {
                    // 같은 산지끼리 묶어 한 줄로.
                    let groups = Dictionary(grouping: info.origins, by: \.value)
                    let order = info.origins.map(\.value).reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
                    ForEach(order, id: \.self) { origin in
                        (Text(origin).font(.ui(.bodySmall, .semibold)).foregroundColor(palette.onSurface)
                         + Text("  " + (groups[origin] ?? []).map(\.name).joined(separator: ", ")).font(.ui(.bodySmall)).foregroundColor(palette.onSurfaceVariant))
                            .padding(.vertical, 2)
                    }
                    .padding(.top, 8)
                }
            }
            .padding(.top, 8)
            .transition(.opacity)
        }
    }
}

/// 사진 크게 보기 — 검은 바탕, 사진은 화면 가운데. 두 손가락으로 확대, 두 번 누르면 2.5배.
/// 확대하지 않았을 때 끌면 사진 앱처럼 사진이 손가락을 따라오고 바탕이 옅어지며, 충분히 끌고 놓으면 닫히고 아니면 제자리로.
struct PhotoViewer: View {
    let urls: [URL]
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var image: UIImage?
    @State private var scale: CGFloat = 1
    @State private var last: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    /// 닫으려고 끄는 거리 — 확대하지 않았을 때만.
    @State private var pull: CGSize = .zero

    /// 이만큼(또는 빠르게) 끌고 놓으면 닫습니다.
    private let dismissDistance: CGFloat = 120

    var body: some View {
        GeometryReader { g in
            let progress = min(1, hypot(pull.width, pull.height) / 400)
            ZStack {
                Color.black.opacity(0.96 * (1 - progress)).ignoresSafeArea().onTapGesture { dismiss() }
                if let image {
                    Image(uiImage: image).resizable().scaledToFit()
                        // 가운데에 — 예전엔 닫기 단추와 같은 '오른쪽 위' 정렬을 따라 위에 붙었습니다.
                        .frame(width: g.size.width, height: g.size.height)
                        .scaleEffect(scale * (1 - 0.15 * progress))
                        .offset(x: offset.width + pull.width, y: offset.height + pull.height)
                        .gesture(SimultaneousGesture(
                            MagnifyGesture().onChanged { scale = (last * $0.magnification).clamped(1, 5) }.onEnded { _ in last = scale; if scale == 1 { offset = .zero; lastOffset = .zero } },
                            DragGesture()
                                .onChanged { d in
                                    if scale > 1 {
                                        offset = CGSize(width: lastOffset.width + d.translation.width, height: lastOffset.height + d.translation.height)
                                    } else {
                                        // 손가락을 그대로 따라옵니다 — 직접 끄는 동작이라 애니메이션 없이.
                                        pull = d.translation
                                    }
                                }
                                .onEnded { d in
                                    if scale > 1 { lastOffset = offset; return }
                                    let far = hypot(d.translation.width, d.translation.height) > dismissDistance
                                    let fling = hypot(d.predictedEndTranslation.width, d.predictedEndTranslation.height) > dismissDistance * 3
                                    if far || fling { close(toward: d.predictedEndTranslation, height: g.size.height) }
                                    else { withAnimation(reduceMotion ? nil : .spring(response: 0.35, dampingFraction: 0.86)) { pull = .zero } }
                                }))
                        .onTapGesture(count: 2) { withAnimation { scale = scale > 1 ? 1 : 2.5; last = scale; if scale == 1 { offset = .zero; lastOffset = .zero } } }
                } else {
                    ProgressView().tint(.white)
                }
            }
            .frame(width: g.size.width, height: g.size.height)
            .overlay(alignment: .topTrailing) {
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: 44, height: 44).background(Circle().fill(.white.opacity(0.16)))
                }
                .padding(16)
                .opacity(1 - progress)
            }
        }
        .ignoresSafeArea()
        .task {
            for url in urls {
                var req = URLRequest(url: url)
                req.setValue(PortalClient.userAgent, forHTTPHeaderField: "User-Agent")
                if let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200, let img = UIImage(data: data) {
                    image = img
                    return
                }
            }
        }
    }

    /// 끈 방향으로 마저 밀어내며 사라지고 닫습니다 — 사라지는 동작이라 빠른 ease-out. 화면이 다시 아래로 내려가는 기본 닫힘은 끕니다.
    private func close(toward v: CGSize, height: CGFloat) {
        let length = max(1, hypot(v.width, v.height))
        let target = CGSize(width: v.width / length * height, height: v.height / length * height)
        withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) { pull = target }
        DispatchQueue.main.asyncAfter(deadline: .now() + (reduceMotion ? 0 : 0.2)) {
            var t = Transaction()
            t.disablesAnimations = true
            withTransaction(t) { dismiss() }
        }
    }
}

/// 시간표 — 월~금 주간 표를 카드에. 오늘 요일 열에 옅은 바탕. 화면에 다 들어가면 스크롤 없이 행 높이를 맞춥니다.
struct TimetableView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette

    var body: some View {
        GeometryReader { g in
            let week = model.week
            let times = week.flatMap(\.periodTimes).map { Dictionary(uniqueKeysWithValues: $0.compactMap { k, v in v.count == 2 ? (k, (start: v[0], end: v[1])) : nil }) } ?? Schedule.defaultPeriodTimes
            let rows = times.count + 1
            let available = g.size.height - 4 - 28 - 16
            let rowH = ((available - CGFloat(rows)) / CGFloat(rows)).clamped(56, 78)
            SheetScroll(maxWidth: 1100) {
                OneUiCard(padding: EdgeInsets(top: week == nil ? 24 : 8, leading: week == nil ? 24 : 8, bottom: week == nil ? 24 : 8, trailing: week == nil ? 24 : 8)) {
                    if let week {
                        WeekTable(week: week, times: times, today: model.today, rowHeight: rowH)
                    } else {
                        VStack(spacing: 0) {
                            Text("시간표를 불러오지 못했습니다").font(.ui(.titleMedium, .bold))
                            Text("학사시스템 연동이 필요합니다").font(.ui(.bodyMedium)).foregroundStyle(palette.onSurfaceVariant).padding(.top, 4)
                            OneUiButton("다시 시도", enabled: !model.isSyncing, leading: model.isSyncing ? AnyView(OneUiLoading(size: 16, stroke: 2, color: palette.onPrimary)) : nil) {
                                Task { await model.refreshTimetable(force: true) }
                            }
                            .padding(.top, 16)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }
}

private struct WeekTable: View {
    let week: TimetableWeek
    let times: [Int: (start: Int, end: Int)]
    let today: LocalDate
    let rowHeight: CGFloat
    @Environment(\.palette) private var palette

    var body: some View {
        let todayDow = today.weekday
        let labels = ["월", "화", "수", "목", "금"]
        let tint = palette.primary.opacity(0.10)
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Color.clear.frame(width: 40, height: rowHeight)
                ForEach(0..<5, id: \.self) { i in
                    Text(labels[i]).font(.ui(.titleSmall, .bold)).foregroundStyle(i + 1 == todayDow ? palette.primary : palette.onSurface)
                        .frame(maxWidth: .infinity).frame(height: rowHeight).background(i + 1 == todayDow ? tint : .clear)
                }
            }
            Rectangle().fill(palette.outlineVariant).frame(height: 0.8)
            ForEach(times.keys.sorted(), id: \.self) { p in
                HStack(spacing: 0) {
                    VStack(spacing: 0) {
                        Text("\(p)").font(.ui(.titleSmall, .bold))
                        if let t = times[p] { Text(String(format: "%d:%02d", t.start / 60, t.start % 60)).font(.system(size: 10)).foregroundStyle(palette.onSurfaceVariant).lineLimit(1) }
                    }
                    .frame(width: 40, height: rowHeight)
                    ForEach(1...5, id: \.self) { dow in
                        let lesson = week.lessons[dow]?[p]
                        VStack(alignment: .leading, spacing: 0) {
                            if let lesson {
                                Text(lesson.title).font(.system(size: 11.5, weight: .semibold)).lineLimit(lesson.teacher != nil ? 2 : 3)
                                    .foregroundStyle(lesson.isFree ? palette.onSurfaceVariant : palette.onSurface)
                                ForEach([lesson.room, lesson.teacher].compactMap { $0 }, id: \.self) {
                                    Text($0).font(.system(size: 10)).foregroundStyle(palette.primary).lineLimit(1)
                                }
                            }
                        }
                        .padding(.horizontal, 3).padding(.vertical, 5)
                        .frame(maxWidth: .infinity, alignment: .topLeading).frame(height: rowHeight, alignment: .topLeading)
                        .background(dow == todayDow ? tint : .clear)
                    }
                }
                Rectangle().fill(palette.outlineVariant).frame(height: 0.8)
            }
        }
    }
}
