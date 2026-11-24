import SwiftUI

/// 신청·내역 블록 순서 — 서비스 넷과 심야면학. '순서'에서 바꾸고 기기에 저장합니다.
enum ApplyOrderStore {
    static let midnight = "MIDNIGHT"
    static let defaultOrder = ApplyService.allCases.map(\.rawValue) + [midnight]
    private static let key = "apply_order.order"

    static func get() -> [String] {
        let saved = (SharedContainer.defaults.string(forKey: key) ?? "").split(separator: ",").map(String.init).filter { defaultOrder.contains($0) }
        var seen = Set<String>()
        let unique = saved.filter { seen.insert($0).inserted }
        return unique + defaultOrder.filter { !unique.contains($0) }
    }
    static func set(_ order: [String]) { SharedContainer.defaults.set(order.joined(separator: ","), forKey: key) }
    static func label(_ key: String) -> String { key == midnight ? "심야면학" : ApplyService(rawValue: key)?.label ?? key }
}

/// 신청·내역 — 서비스마다 '내역'과 '신청'. 교과교실은 내역을 이 화면 안에 펼치고 '교실별 현황'을 하나 더.
struct ApplyView: View {
    @Environment(AppModel.self) private var model
    @State private var order = ApplyOrderStore.get()
    @State private var classroomRows: [HanaApplyRow]?
    @State private var classroomLoading = false
    @State private var classroomError: String?
    @State private var editingOrder = false

    var body: some View {
        List {
            ForEach(order, id: \.self) { key in
                if key == ApplyOrderStore.midnight {
                    MidnightSection()
                } else if let service = ApplyService(rawValue: key) {
                    section(service)
                }
            }
        }
        .navigationTitle("신청·내역")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { editingOrder = true } label: { Label("순서", systemImage: "arrow.up.arrow.down") }
            }
        }
        .sheet(isPresented: $editingOrder) { OrderSheet(order: $order) }
    }

    /// 서비스마다 색 아이콘이 붙은 '신청'·'내역' 줄 — 글자만 늘어서 구분이 어렵던 것.
    @ViewBuilder private func section(_ service: ApplyService) -> some View {
        Section(service.label) {
            RouteLink(service == .library ? Route.seats(.library) : service == .studyRoom ? Route.seats(.studyRoom) : Route.quickApply(service)) {
                Label { Text(service == .library || service == .studyRoom ? "좌석 신청" : "신청") } icon: { IconTile(service.symbol, service.tint) }
            }
            RouteLink(Route.history(service)) {
                Label { Text("내역") } icon: { IconTile("clock.arrow.circlepath", .gray) }
            }
            if service == .classroom {
                RouteLink(Route.classroomStatus) {
                    Label { Text("교실별 현황") } icon: { IconTile("square.grid.3x3.fill", service.tint) }
                }
            }
        }
    }

    private func loadClassroomHistory() {
        classroomLoading = true
        classroomError = nil
        Task {
            do { classroomRows = try await ClassroomHistoryAPI.fetch() }
            catch let e as PortalError where e == .missingCredentials { classroomError = academicNeedsLogin }
            catch { classroomError = applyErrorText(error) }
            classroomLoading = false
        }
    }
}

/// 순서 바꾸기 — 끌어서 옮기고 바로 저장. '기본 순서로'.
private struct OrderSheet: View {
    @Binding var order: [String]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(order, id: \.self) { Text(ApplyOrderStore.label($0)) }
                        .onMove { order.move(fromOffsets: $0, toOffset: $1); ApplyOrderStore.set(order) }
                }
                Section {
                    Button("기본 순서로") { order = ApplyOrderStore.defaultOrder; ApplyOrderStore.set(order) }
                        .disabled(order == ApplyOrderStore.defaultOrder)
                }
            }
            .environment(\.editMode, .constant(.active))
            .navigationTitle("블록 순서")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { ConfirmButton("완료") { dismiss() } } }
        }
        .presentationDetents([.medium])
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
