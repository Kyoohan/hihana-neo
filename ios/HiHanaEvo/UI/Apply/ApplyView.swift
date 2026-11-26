import SwiftUI

/// 신청·내역 — 안드로이드 ApplySection.kt 와 같은 구성(2026-11-26 사용자 요청 A안):
/// 맨 위 '오늘 면학'(타임마다 잡힌 자리)과 다가오는 외출·외박, 그 아래 '신청'(면학실·도서관·심야면학 세 칸 나란히, 교과교실은 신청과
/// 교실별 현황, 외출·외박), 맨 아래 '내역' 칩. 모두 기기에 있는 값이라 포털에 새로 묻지 않습니다.
/// 예전 블록 순서 편집은 자리가 정해진 구성이라 뺐습니다.
struct ApplyView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.listPane) private var listPane
    /// 내역 칩 아래 펼친 심야면학 내역(오늘 1·2타임 신청 + 불참).
    @State private var midnightLines: [String]?
    @State private var midnightLoading = false
    @State private var midnightError: String?
    @State private var midnightOpen = false

    private enum Tint {
        static let studyRoom = Color(argb: 0xFF5C7CFA)
        static let library = Color(argb: 0xFF8E7CFF)
        static let midnight = Color(argb: 0xFF4B4FC9)
        static let classroom = Color(argb: 0xFF14A8B8)
        static let classroomStatus = Color(argb: 0xFF2E9FAE)
        static let outing = Color(argb: 0xFF34B27D)
    }

    var body: some View {
        let today = model.today
        let rest = model.context().isRestDay(today)
        let slots = rest ? PlanSlot.slots(isRestDay: true) : [.weekday0] + PlanSlot.slots(isRestDay: false)
        let places: [PlanSlot: StudyPlace] = Dictionary(uniqueKeysWithValues: slots.compactMap { s in
            (model.places[today.iso]?[s] ?? s.defaultPlace).map { (s, $0) }
        })
        let outing = model.outingSpans().filter { $0.end > Date() }.min { $0.start < $1.start }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                todayCard(slots: slots, places: places)
                if let outing { outingCard(outing) }

                sectionTitle("신청")
                // 아이콘과 이름만 — 오늘 자리는 위 '오늘 면학'에 있어 되풀이하지 않습니다.
                HStack(spacing: 10) {
                    seatTile("면학실", "chair.fill", Tint.studyRoom, .seats(.studyRoom))
                    seatTile("도서관", "books.vertical.fill", Tint.library, .seats(.library))
                    seatTile("심야면학", "moon.zzz.fill", Tint.midnight, .midnight)
                }
                OneUiCard(padding: EdgeInsets()) {
                    row("교과교실", "door.left.hand.open", Tint.classroom, .quickApply(.classroom))
                    palette.onSurface.opacity(0.10).frame(height: 0.7).padding(.leading, 60)
                    row("교실별 현황", "square.grid.3x3.fill", Tint.classroomStatus, .classroomStatus)
                }
                OneUiCard(padding: EdgeInsets()) {
                    row("외출·외박", "figure.walk", Tint.outing, .quickApply(.outing))
                }

                sectionTitle("내역")
                // 다섯 곳을 카드 한 장에 같은 폭으로 — 칩을 늘어놓으면 기기 폭에 따라 줄 끝이 들쭉날쭉하고 오른쪽이 비었습니다.
                // 아이패드 두 칸에선 오른쪽에 떠 있는 내역의 칸을 그 색으로(예전 목록의 고른 줄 표시와 같은 역할).
                let shown: (ApplyService) -> Bool = { listPane && model.nav.isSelected(.history($0)) }
                historyStrip([
                    ("면학실", "chair.fill", Tint.studyRoom, shown(.studyRoom), { go(.history(.studyRoom)) }),
                    ("도서관", "books.vertical.fill", Tint.library, shown(.library), { go(.history(.library)) }),
                    ("교과교실", "door.left.hand.open", Tint.classroom, shown(.classroom), { go(.history(.classroom)) }),
                    ("외출·외박", "figure.walk", Tint.outing, shown(.outing), { go(.history(.outing)) }),
                    ("심야면학", "moon.zzz.fill", Tint.midnight, midnightOpen, { toggleMidnight() }),
                ])
                if midnightOpen { midnightCard }
            }
            .padding(.horizontal, 16)
            // 큰 제목 바로 밑에 카드가 붙던 것 — 예전 목록(List)은 제목과 첫 묶음 사이를 시스템이 띄웠는데, 카드를 직접 쌓으며 그 간격이 사라졌음.
            .padding(.top, 14)
            .padding(.bottom, 24)
            .readableWidth(860)
        }
        .scrollBounceBehavior(.basedOnSize)
        .navigationTitle("신청·내역")
    }

    // MARK: 부품

    private func go(_ route: Route) { listPane ? model.nav.select(route) : model.nav.open(route) }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(.subheadline.weight(.semibold)).foregroundStyle(palette.onSurface.opacity(0.62)).padding(.leading, 6).padding(.top, 6)
    }

    /// 신청 칸과 같은 장소는 그 칸의 색, 나머지(생활관·방과후·1인2기)는 홈 일정의 장소 색.
    private func color(of p: StudyPlace) -> Color {
        switch p {
        case .studyRoom: Tint.studyRoom
        case .library: Tint.library
        case .classroom: Tint.classroom
        case .midnight: Tint.midnight
        default: p.accent.color
        }
    }

    /// 오늘 면학 — 타임마다 시작 시각, 장소, 자리·교실. 비어 있는 타임은 점선 칸('위치 미설정', 홈 일정과 같은 말).
    private func todayCard(slots: [PlanSlot], places: [PlanSlot: StudyPlace]) -> some View {
        OneUiCard(cornerRadius: 26) {
            HStack(alignment: .lastTextBaseline) {
                Text("오늘 면학").font(.headline)
                Spacer()
                Text("\(model.today.month)월 \(model.today.day)일").font(.caption).foregroundStyle(palette.onSurface.opacity(0.62))
            }
            .padding(.bottom, 10)
            HStack(alignment: .top, spacing: 8) {
                ForEach(slots, id: \.self) { s in
                    let p = places[s]
                    let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("\(s.title) \(s.timeText.split(separator: " ").first ?? "")").font(.caption2).foregroundStyle(palette.onSurface.opacity(0.62)).lineLimit(1)
                        Text(p?.name ?? "위치 미설정").font(.subheadline.weight(.bold))
                            .foregroundStyle(p.map { color(of: $0) } ?? palette.onSurface.opacity(0.62)).brightness(palette.dark ? 0.25 : -0.12).lineLimit(1)
                        if let d = p?.detail { Text(d).font(.caption2).foregroundStyle(palette.onSurface.opacity(0.62)).lineLimit(2) }
                    }
                    .padding(.horizontal, 10).padding(.vertical, 9)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .background { if let p { shape.fill(color(of: p).opacity(palette.dark ? 0.22 : 0.13)) } }
                    .overlay { if p == nil { shape.strokeBorder(palette.onSurface.opacity(0.18), style: StrokeStyle(lineWidth: 1, dash: [4, 3])) } }
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// 다가오는(또는 지금인) 외출·외박 한 건 — 기간·사유와 승인 상태.
    private func outingCard(_ o: OutingSpan) -> some View {
        // 두 줄 — 날짜(외박은 기간)와 시각·사유. 한 줄에 날짜와 시각을 다 넣으면 좁은 폭에서 '09:00 ~ / 13:00'처럼 시각 중간에서 꺾였습니다.
        let day: (Date) -> String = { d in "\(d.localDate.month).\(d.localDate.day)(\(d.localDate.weekdayShort))" }
        let dates = o.overnight ? "\(day(o.start)) ~ \(day(o.end))" : day(o.start)
        let times = "\(o.start.hm) ~ \(o.end.hm)" + ((o.reason?.isEmpty == false) ? " · \(o.reason!)" : "")
        let tint = o.pending ? Color(argb: 0xFFE3A82E) : Color(argb: 0xFF2FB67C)
        return OneUiCard(cornerRadius: 26, padding: EdgeInsets(top: 14, leading: 16, bottom: 14, trailing: 14)) {
            HStack(spacing: 12) {
                SolidCircleIcon(symbol: "figure.walk", color: Tint.outing, size: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(o.overnight ? "외박" : "외출") · \(dates)").font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(times).font(.caption).foregroundStyle(palette.onSurface.opacity(0.62)).lineLimit(1)
                }
                Spacer(minLength: 6)
                Text(o.pending ? "승인 대기" : "승인됨").font(.caption.weight(.bold)).foregroundStyle(tint).brightness(palette.dark ? 0.2 : -0.15)
                    .padding(.horizontal, 10).padding(.vertical, 4).background(tint.opacity(0.18), in: Capsule())
            }
        }
    }

    /// 자리 신청 칸 — 색 원 아이콘과 이름만.
    private func seatTile(_ label: String, _ symbol: String, _ tint: Color, _ route: Route) -> some View {
        OneUiCard(cornerRadius: 24, padding: EdgeInsets(top: 14, leading: 8, bottom: 14, trailing: 8), action: { go(route) }) {
            VStack(spacing: 8) {
                SolidCircleIcon(symbol: symbol, color: tint, size: 36)
                Text(label).font(.subheadline.weight(.bold)).foregroundStyle(palette.onSurface).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
        }
    }

    /// 카드 안 한 줄 — 색 원 아이콘, 이름, 오른쪽 화살표.
    private func row(_ label: String, _ symbol: String, _ tint: Color, _ route: Route) -> some View {
        Button { go(route) } label: {
            HStack(spacing: 12) {
                SolidCircleIcon(symbol: symbol, color: tint, size: 32)
                Text(label).font(.subheadline.weight(.semibold)).foregroundStyle(palette.onSurface)
                Spacer(minLength: 6)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(RowPressStyle())
    }

    /// 내역 다섯 곳 — 카드 한 장을 같은 폭 칸으로 나누고 가는 선으로 구분('전체' 바로 가기와 같은 결). 펼쳐 둔 칸은 그 색으로 옅게.
    private func historyStrip(_ items: [(String, String, Color, Bool, () -> Void)]) -> some View {
        let line = palette.onSurface.opacity(0.10)
        return OneUiCard(cornerRadius: 24, padding: EdgeInsets()) {
            HStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                    if i > 0 { line.frame(width: 0.7).padding(.vertical, 14) }
                    Button(action: item.4) {
                        VStack(spacing: 6) {
                            SolidCircleIcon(symbol: item.1, color: item.2, size: 28)
                            Text(item.0).font(.caption.weight(.semibold)).foregroundStyle(palette.onSurface).lineLimit(1).minimumScaleFactor(0.8)
                        }
                        .padding(.vertical, 12).padding(.horizontal, 2)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(item.3 ? item.2.opacity(0.16) : .clear)
                    }
                    .buttonStyle(RowPressStyle())
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var midnightCard: some View {
        OneUiCard(cornerRadius: 24) {
            Text("심야면학 내역").font(.subheadline.weight(.bold)).padding(.bottom, 8)
            if midnightLoading { HStack(spacing: 8) { ProgressView(); Text("불러오는 중").font(.footnote).foregroundStyle(.secondary) } }
            if let midnightError { Text(midnightError).font(.footnote).foregroundStyle(.red) }
            ForEach(midnightLines ?? [], id: \.self) { Text($0).font(.subheadline).padding(.vertical, 2) }
        }
    }

    /// 심야면학 내역 펼치기·접기 — 로그인 전이면 신청 화면(로그인)으로.
    private func toggleMidnight() {
        if midnightOpen { midnightOpen = false; return }
        guard MidnightSession.isLoggedIn else { go(.midnight); return }
        midnightOpen = true
        midnightLoading = true
        midnightError = nil
        Task {
            do {
                let first = try await MidnightAPI.dashboard(session: 1, floor: nil, model: model)
                let second = first.session2Available ? try? await MidnightAPI.dashboard(session: 2, floor: first.floor, model: model) : nil
                func line(_ label: String, _ d: MidnightDashboard?) -> String {
                    guard let d else { return "\(label) · 운영 안 함" }
                    return d.hasReservation ? "\(label) · \(d.reservationFloor ?? d.floor ?? 0)층 \(d.reservationSeat ?? 0)번" : "\(label) · 신청 없음"
                }
                midnightLines = [line("1타임", first), line("2타임", second), "불참 \(first.absenceCount)회 · " + (first.bannedUntil.map { "\($0)까지 신청 제한" } ?? "신청 제한 없음")]
            } catch { midnightError = error.localizedDescription }
            midnightLoading = false
        }
    }
}

// MARK: - 내역 화면

private enum HistoryRange: Int, CaseIterable {
    case week = 7, twoWeeks = 14, month = 31, quarter = 92
    var label: String { switch self { case .week: "1주"; case .twoWeeks: "2주"; case .month: "1개월"; case .quarter: "3개월" } }
}

private func dayLabel(_ d: LocalDate, today: LocalDate) -> String {
    let base = "\(d.month)월 \(d.day)일 (\(d.weekdayShort))"
    if d == today { return "오늘 · " + base }
    if d == today.adding(days: 1) { return "내일 · " + base }
    if d == today.adding(days: -1) { return "어제 · " + base }
    return d.year != today.year ? "\(d.year)년 " + base : base
}

private func shortLabel(_ d: Date) -> String {
    let l = d.localDate
    return "\(l.month)/\(l.day)(\(l.weekdayShort)) \(d.hm)"
}

/// 면학실·도서관 내역 — 기간을 고르고 날짜별로. 한 줄에 타임, 장소·자리, 출석 확인 시각. 앞으로의 신청도 보이게 끝은 오늘 + 7일.
struct SeatHistoryView: View {
    let service: ApplyService
    @State private var range: HistoryRange = .twoWeeks
    @State private var rows: [SeatHistoryRow] = []
    @State private var total = 0
    @State private var page = 1
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        let today = LocalDate.today()
        let place = SeatHistoryPlace(service)
        let days = Dictionary(grouping: rows) { $0.date }.sorted { ($0.key ?? .init(year: 1, month: 1, day: 1)) > ($1.key ?? .init(year: 1, month: 1, day: 1)) }
        List {
            if let error {
                ContentUnavailableView("불러오지 못했습니다", systemImage: "exclamationmark.triangle", description: Text(error)).listRowBackground(Color.clear)
            } else if loading && rows.isEmpty {
                HStack(spacing: 10) { ProgressView(); Text("내역을 불러오는 중").foregroundStyle(.secondary) }.listRowBackground(Color.clear)
            } else if rows.isEmpty {
                ContentUnavailableView("이 기간에 \(place.label) 신청이 없습니다", systemImage: "chair").listRowBackground(Color.clear)
            } else {
                Text("출석 확인 \(rows.filter { $0.checkedAt != nil }.count)건 · 전체 \(total)건").font(.footnote).foregroundStyle(.secondary).listRowBackground(Color.clear)
                ForEach(days, id: \.key) { date, dayRows in
                    Section {
                        ForEach(dayRows.sorted { $0.slotOrder < $1.slotOrder }) { r in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(r.slot).fontWeight(.semibold)
                                    if let a = r.area { Text(a).font(.footnote).foregroundStyle(.secondary) }
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 2) {
                                    Text(r.seat ?? "-").font(.headline)
                                    if let c = r.checkedAt { Text("출석 확인 \(c.hm)").font(.caption).foregroundStyle(.tint) }
                                }
                            }
                            .materialRow()
                        }
                    } header: {
                        Text(date.map { dayLabel($0, today: today) } ?? "날짜 없음").foregroundStyle(date == today ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                    }
                }
                if rows.count < total {
                    Button(loading ? "불러오는 중" : "더 보기 · \(total - rows.count)건 남음") { Task { await load(page + 1) } }.disabled(loading).materialRow()
                }
            }
        }
        .navigationTitle("\(place.label) 내역")
        .topBar {
            Picker("기간", selection: $range) { ForEach(HistoryRange.allCases, id: \.self) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
        }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { PortalLink(path: service.historyPath, title: "\(service.label) 내역") } }
        .refreshable { await load(1) }
        .task(id: range) { rows = []; await load(1) }
    }

    private func load(_ next: Int) async {
        loading = true
        error = nil
        let today = LocalDate.today()
        do {
            let r = try await SeatHistoryAPI.fetch(SeatHistoryPlace(service), from: today.adding(days: -range.rawValue), to: today.adding(days: 7), page: next)
            // 기간·상태를 바꿔 이 작업이 취소됐으면 — 늦게 온 예전 결과·'취소됨' 오류로 새 목록을 덮지 않습니다.
            guard !Task.isCancelled else { return }
            rows = next == 1 ? r.rows : rows + r.rows
            total = r.total
            page = next
        } catch {
            guard !Task.isCancelled else { return }
            self.error = applyErrorText(error)
        }
        loading = false
    }
}

/// 포털 페이지를 앱 안 웹으로 — 앱 화면에 없는 기능을 쓸 때.
struct PortalLink: View {
    let path: String
    let title: String
    @Environment(AppModel.self) private var model
    var body: some View {
        Button { model.nav.open(.web(url: PortalClient.base + path, title: title, resync: true)) } label: { Label("포털에서 열기", systemImage: "globe") }
    }
}

/// 외출·외박 내역 — 상태로 거르고, 줄을 누르면 이동방법·승인 교사·복귀 확인을 펼칩니다. 신청(대기) 상태의 개인 신청은 수정·취소.
struct OutingHistoryView: View {
    @Environment(AppModel.self) private var model
    @State private var filter: OutingFilter = .all
    @State private var rows: [OutingRow] = []
    @State private var total = 0
    @State private var page = 1
    @State private var loading = true
    @State private var error: String?
    @State private var expanded: Int?
    @State private var details: [Int: Result<OutingDetail, Error>] = [:]
    @State private var editing: OutingEdit?
    @State private var cancelling: OutingRow?
    @State private var sending = false
    @State private var result: (title: String, message: String)?
    @State private var reload = 0

    var body: some View {
        List {
            if let error {
                ContentUnavailableView("불러오지 못했습니다", systemImage: "exclamationmark.triangle", description: Text(error)).listRowBackground(Color.clear)
            } else if loading && rows.isEmpty {
                HStack(spacing: 10) { ProgressView(); Text("내역을 불러오는 중").foregroundStyle(.secondary) }.listRowBackground(Color.clear)
            } else if rows.isEmpty {
                ContentUnavailableView(filter == .all ? "외출·외박 신청이 없습니다" : "\(filter.label) 상태인 신청이 없습니다", systemImage: "figure.walk").listRowBackground(Color.clear)
            }
            ForEach(rows) { row in
                Section { card(row).materialRow() }
            }
            if !rows.isEmpty && rows.count < total {
                Button(loading ? "불러오는 중" : "더 보기 · \(total - rows.count)건 남음") { Task { await load(page + 1) } }.disabled(loading).materialRow()
            }
        }
        .listSectionSpacing(12)
        .navigationTitle("외출·외박 내역")
        .historySubtitle(error == nil && (!rows.isEmpty || !loading) ? "\(total)건" : nil)
        .topBar {
            Picker("상태", selection: $filter) { ForEach(OutingFilter.allCases, id: \.self) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
        }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { PortalLink(path: ApplyService.outing.historyPath, title: "외출·외박 내역") } }
        .refreshable { reload += 1 }
        .task(id: "\(filter.rawValue)-\(reload)") {
            rows = []; expanded = nil; details = [:]
            await load(1)
        }
        .sheet(item: $editing) { e in
            NavigationStack { OutingApplyView(edit: e) { editing = nil; reload += 1 } }
        }
        .alert("외출·외박 신청을 취소할까요?", isPresented: Binding(get: { cancelling != nil }, set: { if !$0 && !sending { cancelling = nil } }), presenting: cancelling) { row in
            Button("신청 취소", role: .destructive) { cancel(row) }
            Button("닫기", role: .cancel) {}
        } message: { row in
            Text([row.plannedOut.map { "출발 " + shortLabel($0) }, row.plannedBack.map { "복귀 " + shortLabel($0) }, row.reason.map { "사유 " + $0 }].compactMap { $0 }.joined(separator: "\n"))
        }
        .alert(result?.title ?? "", isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) { Button("확인") {} } message: { Text(result?.message ?? "") }
    }

    private func card(_ row: OutingRow) -> some View {
        let open = expanded == row.osmNo
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                stateBadge(row)
                if row.group { Text("단체").font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.horizontal, 7).padding(.vertical, 2).background(.quaternary, in: Capsule()) }
                Spacer()
                if let d = row.duration { Text(d).font(.caption).foregroundStyle(.secondary) }
            }
            HStack(spacing: 8) {
                if let o = row.plannedOut { Text(shortLabel(o)) }
                if row.plannedOut != nil && row.plannedBack != nil { Image(systemName: "arrow.right").font(.footnote.weight(.semibold)).foregroundStyle(.secondary) }
                if let b = row.plannedBack { Text(shortLabel(b)) }
            }
            .fontWeight(.semibold)
            if let r = row.reason { Text(r).font(.subheadline).foregroundStyle(.secondary).lineLimit(open ? 6 : 1) }
            if row.actualOut != nil || row.actualBack != nil {
                Text([row.actualOut.map { "실제 출발 " + shortLabel($0) }, row.actualBack.map { "복귀 " + shortLabel($0) }].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.tint)
            }
            if open {
                Divider().padding(.vertical, 4)
                switch details[row.osmNo] {
                case nil:
                    HStack(spacing: 8) { ProgressView().controlSize(.small); Text("상세를 불러오는 중").font(.footnote).foregroundStyle(.secondary) }
                case .failure(let e):
                    Text(applyErrorText(e)).font(.footnote).foregroundStyle(.secondary)
                case .success(let d):
                    VStack(alignment: .leading, spacing: 4) {
                        detailLine("이동방법", d.transport)
                        detailLine("승인 교사", d.approver)
                        detailLine("복귀 확인", d.returnChecker)
                        detailLine("신청한 때", row.appliedAt.map(shortLabel))
                    }
                    // 포털과 같은 조건 — 신청(2)이면서 개인 신청이면 수정·취소.
                    if row.state == 2 && !row.group {
                        HStack(spacing: 8) {
                            Button {
                                if let o = row.plannedOut, let b = row.plannedBack { editing = OutingEdit(osmNo: row.osmNo, out: o, back: b, reason: row.reason ?? "", transport: d.transport) }
                            } label: { Text("수정").frame(maxWidth: .infinity) }
                            Button(role: .destructive) { cancelling = row } label: { Text("신청 취소").frame(maxWidth: .infinity) }
                        }
                        .buttonStyle(.bordered).controlSize(.small).padding(.top, 6)
                    }
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { toggle(row) }
    }

    private func stateBadge(_ row: OutingRow) -> some View {
        let (bg, fg): (AnyShapeStyle, Color) = switch row.state {
        case 4: (AnyShapeStyle(.tint), .white)
        case 8: (AnyShapeStyle(Color.red), .white)
        case 2: (AnyShapeStyle(Color.orange.opacity(0.2)), .orange)
        default: (AnyShapeStyle(.quaternary), .secondary)
        }
        return Text(row.stateLabel.isEmpty ? "상태 \(row.state)" : row.stateLabel).font(.caption.weight(.bold)).foregroundStyle(fg)
            .padding(.horizontal, 8).padding(.vertical, 3).background(bg, in: Capsule())
    }

    private func detailLine(_ label: String, _ value: String?) -> some View {
        HStack(alignment: .top) {
            Text(label).foregroundStyle(.secondary).frame(width: 72, alignment: .leading)
            Text(value ?? "-")
        }
        .font(.footnote)
    }

    private func toggle(_ row: OutingRow) {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { expanded = expanded == row.osmNo ? nil : row.osmNo }
        guard expanded == row.osmNo, (try? details[row.osmNo]?.get()) == nil else { return }
        details[row.osmNo] = nil
        Task {
            do { details[row.osmNo] = .success(try await OutingAPI.detail(row)) }
            catch { details[row.osmNo] = .failure(error) }
        }
    }

    private func load(_ next: Int) async {
        loading = true
        error = nil
        do {
            let r = try await OutingAPI.list(filter, page: next)
            // 기간·상태를 바꿔 이 작업이 취소됐으면 — 늦게 온 예전 결과·'취소됨' 오류로 새 목록을 덮지 않습니다.
            guard !Task.isCancelled else { return }
            rows = next == 1 ? r.rows : rows + r.rows
            total = r.total
            page = next
        } catch {
            guard !Task.isCancelled else { return }
            self.error = applyErrorText(error)
        }
        loading = false
    }

    private func cancel(_ row: OutingRow) {
        guard !sending else { return }
        sending = true
        Task {
            do { result = ("취소했습니다", try await OutingApplyAPI.cancel(osmNo: row.osmNo, stdMemNo: row.stdMemNo)) }
            catch { result = ("취소하지 못했습니다", applyErrorText(error)) }
            sending = false
            cancelling = nil
            reload += 1
            model.syncPlacesSoon()
        }
    }
}

/// 교과교실 내역 — 카드 안 목록과 같은 것을 한 화면으로(바로 가기에서).
struct ClassroomHistoryView: View {
    @State private var rows: [HanaApplyRow]?
    @State private var error: String?

    var body: some View {
        List {
            if let error {
                ContentUnavailableView("불러오지 못했습니다", systemImage: "exclamationmark.triangle", description: Text(error)).listRowBackground(Color.clear)
            } else if let rows {
                if rows.isEmpty { ContentUnavailableView("내역 없음", systemImage: "door.left.hand.open").listRowBackground(Color.clear) }
                ForEach(rows) { r in
                    VStack(alignment: .leading, spacing: 2) {
                        Text([r.slot, r.place].compactMap { $0 }.joined(separator: " · ")).fontWeight(.semibold)
                        Text([r.status, r.appliedDate].compactMap { $0 }.joined(separator: " · ")).font(.footnote).foregroundStyle(.secondary)
                    }
                    .materialRow()
                }
            } else {
                HStack(spacing: 10) { ProgressView(); Text("내역을 불러오는 중").foregroundStyle(.secondary) }.listRowBackground(Color.clear)
            }
        }
        .navigationTitle("교과교실 내역")
        .toolbar { ToolbarItem(placement: .topBarTrailing) { PortalLink(path: ApplyService.classroom.historyPath, title: "교과교실 내역") } }
        .refreshable { await load() }
        .task { await load() }
    }

    private func load() async {
        error = nil
        do { rows = try await ClassroomHistoryAPI.fetch() } catch { self.error = applyErrorText(error) }
    }
}

private extension View {
    @ViewBuilder func historySubtitle(_ text: String?) -> some View {
        if #available(iOS 26.0, *), let text { navigationSubtitle(text) } else { self }
    }
}

extension ApplyService {
    /// 신청 줄의 아이콘 — 바로 가기와 같은 기호·색.
    var symbol: String {
        switch self {
        case .studyRoom: "chair.fill"
        case .library: "books.vertical.fill"
        case .classroom: "door.left.hand.open"
        case .outing: "figure.walk"
        }
    }

    var tint: Color {
        switch self {
        case .studyRoom: .blue
        case .library: .brown
        case .classroom: .teal
        case .outing: .green
        }
    }
}
