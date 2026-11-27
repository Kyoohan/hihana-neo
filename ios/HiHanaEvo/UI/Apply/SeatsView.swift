import SwiftUI

/// 도서관·면학실 좌석 — 위에서 타임을 고르면 그 타임의 배치도를 포털 격자 그대로. 빈 자리를 누르면 바로 신청, 내 자리를 누르면 취소.
/// 결과는 서버 문구가 아니라 새로 받은 좌석표로 판정합니다(오픈 직후엔 응답이 늦거나 비어도 실제로 잡히는 일이 있어서).
struct SeatsView: View {
    let service: SeatService
    @SharedModel private var model
    @State private var slots: [LibrarySlot] = []
    @State private var slotId: String?
    @State private var map: LibrarySeatMap?
    @State private var mapSlotId: String?
    @State private var loading = true
    @State private var error: String?
    @State private var busy = false
    @State private var pendingText: String?
    @State private var notice: String?
    @State private var favorite: String?
    @State private var quick: [String] = []
    @State private var pickingQuick = false
    @State private var deviceRegistered: Bool?
    @State private var registering = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                controls
                content
            }
            .padding(.horizontal, 16).padding(.bottom, 80)
            .readableWidth(1200)
        }
        .navigationTitle("\(service.label) 신청")
        .seatsSubtitle(slots.first { $0.id == slotId }?.label)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { Task { await loadMap() } } label: {
                    Label("새로고침", systemImage: "arrow.clockwise").symbolEffect(.rotate.byLayer, isActive: loading)
                }
                .disabled(loading)
            }
        }
        .overlay(alignment: .bottom) { hud }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: pendingText ?? notice)
        .task {
            favorite = SeatStores.favorite(service)
            quick = SeatStores.quick(service)
            // 포털 timeList 가 가끔 주중·휴일 타임을 다 줍니다 — 오늘이 주말·공휴일이면 휴일 타임만, 아니면 주중 타임만 남깁니다
            // (한쪽만 남는 게 없으면 받은 그대로).
            let all = await HanaLibraryAPI.slots(service)
            let weekend = model.context().isRestDay(model.today)
            let fit = all.filter { isRestSlot($0) == weekend }
            slots = fit.isEmpty ? all : fit
            await refreshDevice()
            slotId = slots.first?.id
            if slotId == nil { error = "타임 목록을 불러오지 못했습니다"; loading = false }
        }
        .onChange(of: slotId) { _, id in
            guard id != nil else { return }
            map = nil
            mapSlotId = nil
            Task { await loadMap() }
        }
        .task(id: notice) {
            guard let n = notice else { return }
            try? await Task.sleep(for: .seconds(2.6))
            if notice == n { notice = nil }
        }
    }

    /// 휴일(주말·공휴일) 타임인지 — 포털 이름은 '휴일 1타임', 기본값은 '휴일1타임'.
    private func isRestSlot(_ s: LibrarySlot) -> Bool { s.label.contains("휴일") || s.label.contains("주말") }

    // MARK: 조작부

    private var controls: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 빠른 예약 — 저장해 둔 자리를 누르면 지금 타임에 바로 신청(내 자리면 취소), 길게 눌러 빼기.
            HStack(spacing: 8) {
                Text("빠른 예약").font(.headline)
                Text(pickingQuick ? "저장할 좌석을 탭하세요" : quick.isEmpty ? "+ 를 누르고 자리를 골라 두세요" : "누르면 \(slots.first { $0.id == slotId }?.label ?? "") 에 바로 신청")
                    .font(.footnote).foregroundStyle(.secondary).lineLimit(1)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(quick, id: \.self) { cont in
                        let seat = map?.seats.first { $0.cont == cont }
                        let mine = seat?.mine == true
                        let blocked = seat != nil && !mine && !(seat?.available ?? false)
                        Text(cont).font(.headline).foregroundStyle(mine ? Color.white : Color.primary)
                            .frame(width: 64, height: 64)
                            .background(mine ? AnyShapeStyle(.tint) : AnyShapeStyle(.thinMaterial), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .opacity(blocked ? 0.45 : 1)
                            .onTapGesture { tapQuick(cont) }
                            .contextMenu {
                                Button("빠른 예약에서 빼기", systemImage: "minus.circle", role: .destructive) {
                                    quick.removeAll { $0 == cont }
                                    SeatStores.setQuick(service, quick)
                                    notice = "\(cont) 을 빠른 예약에서 뺐습니다"
                                }
                            }
                    }
                    if quick.count < SeatStores.quickMax {
                        Button {
                            pickingQuick.toggle()
                            notice = pickingQuick ? "저장할 좌석을 아래에서 탭하세요" : nil
                        } label: {
                            Image(systemName: "plus").font(.title2.weight(.semibold)).foregroundStyle(pickingQuick ? Color.white : Color.primary)
                                .frame(width: 64, height: 64)
                                .background(pickingQuick ? AnyShapeStyle(.tint) : AnyShapeStyle(.thinMaterial), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            Divider()
            Picker("타임", selection: $slotId) {
                ForEach(slots) { Text($0.label).tag(Optional($0.id)) }
            }
            .pickerStyle(.menu)
            .disabled(slots.isEmpty)
            // 신청 기기 — 포털은 마지막으로 등록한 기기에서만 신청을 받습니다.
            HStack {
                Text(deviceRegistered == true ? "신청 기기: 이 기기로 등록됨" : deviceRegistered == false ? "신청 기기: 다른 기기로 등록되어 있음" : "신청 기기: 확인 중")
                    .font(.footnote).foregroundStyle(deviceRegistered == false ? Color.red : Color.secondary)
                Spacer()
                if deviceRegistered != true {
                    Button(registering ? "등록 중" : "이 기기로 등록") { register() }
                        .buttonStyle(.bordered).controlSize(.small).disabled(registering)
                }
            }
        }
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    @ViewBuilder private var content: some View {
        if loading && map == nil {
            HStack(spacing: 10) { ProgressView(); Text("좌석을 불러오는 중").foregroundStyle(.secondary) }.padding(.vertical, 8)
        } else if let error {
            ContentUnavailableView("불러오지 못했습니다", systemImage: "exclamationmark.triangle", description: Text(error))
        } else if let closed = map?.closedMessage {
            ContentUnavailableView("휴관", systemImage: "moon.zzz", description: Text(closed))
        } else if let current = map {
            let mine = current.seats.first { $0.mine }
            VStack(alignment: .leading, spacing: 8) {
                Text("빈 자리 \(current.seats.filter(\.available).count)" + (mine.map { " · 내 자리 \($0.cont)" + ($0.assigned ? " (지정석)" : "") } ?? ""))
                    .font(.subheadline).foregroundStyle(.secondary)
                SeatLegend()
            }
            .padding(.horizontal, 4)
            // 즐겨찾기한 구역이 맨 위로 — 별을 누르면 그 구역 하나만 즐겨찾기. 넓은 화면(아이패드)은 구역을 나란히.
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 440), spacing: 14, alignment: .top)], spacing: 14) {
                ForEach(current.areas.sorted { ($0.label == favorite ? 0 : 1) < ($1.label == favorite ? 0 : 1) }, id: \.label) { area in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 4) {
                            Text(area.label).font(.headline)
                            Button {
                                favorite = favorite == area.label ? nil : area.label
                                SeatStores.setFavorite(service, favorite)
                            } label: {
                                Image(systemName: favorite == area.label ? "star.fill" : "star")
                                    .foregroundStyle(favorite == area.label ? Color.yellow : Color.secondary.opacity(0.5))
                                    .contentTransition(.symbolEffect(.replace))
                            }
                            .buttonStyle(.plain)
                        }
                        SeatGrid(area: area) { seatTapped($0) }
                    }
                    .padding(12)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                }
            }
            Text("빈 자리를 누르면 바로 신청되고, 내 자리를 누르면 취소됩니다.").font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 4)
        }
    }

    /// 결과는 아래쪽 알약으로 잠깐 — 기다리는 동안엔 진행 문구와 스피너.
    @ViewBuilder private var hud: some View {
        if let text = pendingText ?? notice {
            HStack(spacing: 10) {
                if pendingText != nil { ProgressView() }
                Text(text).font(.subheadline.weight(.medium)).multilineTextAlignment(.leading)
            }
            .padding(.horizontal, 20).padding(.vertical, 13)
            .modifier(GlassCapsule())
            .padding(.horizontal, 16).padding(.bottom, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    // MARK: 동작

    private func refreshDevice() async { deviceRegistered = await HanaLibraryAPI.isDeviceRegistered(service) }

    private func register() {
        guard !registering else { return }
        registering = true
        Task {
            do { try await HanaLibraryAPI.registerDevice(service); notice = "이 기기를 신청 기기로 등록했습니다" }
            catch { notice = applyErrorText(error) }
            await refreshDevice()
            registering = false
        }
    }

    /// 좌석표를 새로 받습니다. [quiet] 는 신청·취소 뒤의 재확인 — 떠 있는 좌석표를 그대로 두고 받습니다.
    private func loadMap(quiet: Bool = false) async {
        guard let id = slotId else { return }
        if !quiet { loading = true }
        error = nil
        do {
            let loaded = try await HanaLibraryAPI.seatMap(id, service: service)
            if slotId == id { map = loaded; mapSlotId = id }
        } catch {
            // 조용한 재확인이 실패하면 떠 있는 좌석표를 오류로 바꾸지 않습니다.
            if !quiet { self.error = applyErrorText(error) }
        }
        if slotId == id { loading = false }
    }

    private func tapQuick(_ cont: String) {
        guard !busy else { return }
        guard let current = map, mapSlotId == slotId else { notice = "좌석표를 불러온 뒤 다시 눌러 주세요"; return }
        guard let seat = current.seats.first(where: { $0.cont == cont }) else { notice = "\(cont) 자리를 이 타임 좌석표에서 찾지 못했습니다"; return }
        if seat.mine || seat.available { act(seat) } else { notice = "\(cont) 은 이미 다른 사람이 신청했습니다" }
    }

    private func seatTapped(_ seat: LibrarySeat) {
        if pickingQuick {
            pickingQuick = false
            if quick.contains(seat.cont) { notice = "\(seat.cont) 은 이미 빠른 예약에 있습니다" }
            else {
                quick = Array((quick + [seat.cont]).prefix(SeatStores.quickMax))
                SeatStores.setQuick(service, quick)
                notice = "\(seat.cont) 을 빠른 예약에 추가했습니다"
            }
        } else if !busy && mapSlotId == slotId {
            act(seat)
        }
    }

    /// 누르면 확인 없이 바로 — 빈 자리는 신청, 내 자리는 취소.
    private func act(_ seat: LibrarySeat, retriesLeft: Int = 3) {
        guard let id = slotId, mapSlotId == id else { return }
        let cancelling = seat.mine && seat.sreIdx != nil
        busy = true
        pendingText = cancelling ? "\(seat.cont) 취소 중…" : "\(seat.cont) 신청 중…"
        let slowHint = Task {
            try? await Task.sleep(for: .seconds(8))
            if !Task.isCancelled { pendingText = (cancelling ? "\(seat.cont) 취소 중" : "\(seat.cont) 신청 중") + " · 서버가 붐벼 오래 걸릴 수 있어요" }
        }
        Task {
            var success: String?, failure: String?, timedOut = false
            do {
                success = cancelling ? try await HanaLibraryAPI.cancel(seat.sreIdx!, service: service)
                                     : try await HanaLibraryAPI.reserve(seat, slotId: id, service: service)
            } catch PortalError.timeout {
                timedOut = true
            } catch {
                failure = applyErrorText(error)
            }
            slowHint.cancel()
            // 분명히 거절했으면 그 자리는 '확인 중'으로만 두고 바로 풀어 줍니다 — 좌석표는 뒤에서 조용히 받아 가립니다.
            if !cancelling && !timedOut && success == nil, let failure {
                map = map?.markPending(seat.cont)
                busy = false
                pendingText = nil
                notice = "\(failure) — 확인하는 중이니 그대로 두거나 다른 자리를 눌러도 됩니다"
                let before = map
                await loadMap(quiet: true)
                let after = map != before ? map?.seats.first { $0.cont == seat.cont } : nil
                if after?.mine == true {
                    notice = "\(seat.cont) 신청이 실제로는 반영됐습니다"
                } else if let after, after.available, !busy {
                    // 오픈 직후 같은 자리를 거의 동시에 누른 다른 신청이 처리되는 동안 잠겼다 풀리는 경우 — 세 번까지 다시.
                    if retriesLeft > 0 { notice = "\(seat.cont) 은 비어 있어 다시 신청합니다"; act(after, retriesLeft: retriesLeft - 1); return }
                    notice = "\(seat.cont) 은 비어 있지만 서버가 계속 거절했습니다 — 다시 눌러 보세요"
                }
                await refreshDevice()
                model.syncPlacesSoon()
                return
            }
            let before = map
            await loadMap(quiet: true)
            busy = false
            pendingText = nil
            let after = map != before ? map?.seats.first { $0.cont == seat.cont } : nil
            if let after {
                if cancelling { notice = after.mine ? (failure ?? "\(seat.cont) 은 아직 내 자리입니다 — 다시 눌러 주세요") : (success ?? "\(seat.cont) 취소가 반영됐습니다") }
                else if after.mine { notice = success ?? "\(seat.cont) 신청이 반영됐습니다" + (failure.map { " (서버 응답: \($0))" } ?? (timedOut ? " (응답은 늦었지만 좌석표에 반영)" : "")) }
                else if !after.available { notice = (failure.map { "\($0) — " } ?? "") + "\(seat.cont) 은 다른 사람이 잡았습니다" }
                else { notice = failure ?? "\(seat.cont) 은 신청되지 않았습니다 — 다시 눌러 주세요" }
            } else {
                notice = success ?? failure ?? "응답이 없었습니다 — 좌석표를 새로고침해 확인해 주세요"
            }
            await refreshDevice()
            model.syncPlacesSoon()
        }
    }
}

private extension View {
    @ViewBuilder func seatsSubtitle(_ text: String?) -> some View {
        if #available(iOS 26.0, *), let text { navigationSubtitle(text) } else { self }
    }
}

/// 떠 있는 알약 — iOS 26 은 리퀴드 글래스, 그 전엔 재질.
struct GlassCapsule: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) { content.glassEffect(.regular, in: Capsule()) }
        else { content.background(.regularMaterial, in: Capsule()) }
    }
}

// MARK: - 배치도

private struct SeatColors {
    let free, taken, blocked, pending, label: Color
    init(dark: Bool) {
        free = dark ? Color(argb: 0xFF2A3B2E) : Color(argb: 0xFFDCF5E3)
        taken = dark ? Color(argb: 0xFF3A3A3D) : Color(argb: 0xFFE5E7EB)
        blocked = dark ? Color(argb: 0xFF2A2A2D) : Color(argb: 0xFFF3F4F6)
        // 확인 중 — 빈 자리와 구분되게 노란 기운.
        pending = dark ? Color(argb: 0xFF4A3F1F) : Color(argb: 0xFFFDEFC2)
        label = dark ? Color(argb: 0xFFE5E7EB) : Color(argb: 0xFF1F2937)
    }
}

private struct SeatLegend: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let c = SeatColors(dark: scheme == .dark)
        HStack(spacing: 14) {
            ForEach([("빈 자리", c.free), ("신청됨", c.taken)], id: \.0) { label, color in
                HStack(spacing: 5) { RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 12, height: 12); Text(label) }
            }
            HStack(spacing: 5) { RoundedRectangle(cornerRadius: 3).fill(.tint).frame(width: 12, height: 12); Text("내 자리") }
        }
        .font(.caption).foregroundStyle(.secondary)
    }
}

/// 포털 격자 좌표 그대로 — 바깥쪽 빈 행·열만 잘라내고 안쪽 통로는 둡니다. 칸은 최소 34pt, 넘치면 가로 스크롤.
private struct SeatGrid: View {
    let area: LibraryArea
    let onTap: (LibrarySeat) -> Void
    @Environment(\.colorScheme) private var scheme
    @Environment(\.palette) private var palette

    @State private var width: CGFloat = 340

    var body: some View {
        let real = area.seats.filter { $0.isSeat && $0.x >= 0 && $0.y >= 0 }
        let minX = real.map(\.x).min() ?? 0, minY = real.map(\.y).min() ?? 0
        let maxX = real.map(\.x).max() ?? 0, maxY = real.map(\.y).max() ?? 0
        let cols = max(maxX - minX + 1, 1), rowsN = max(maxY - minY + 1, 1)
        let cells = area.seats.filter { (minX...maxX).contains($0.x) && (minY...maxY).contains($0.y) }
            .map { s -> LibrarySeat in var c = s; c.x -= minX; c.y -= minY; return c }
        // 칸은 원래 격자 폭에 맞춘 것보다 10% 크게, 최소 34pt·최대 52pt(넓은 화면).
        let cell = min(max(34, width / CGFloat(max(area.gridX, 1)) * 1.1), 52)
        ScrollView(.horizontal, showsIndicators: false) {
            grid(cells: cells, cell: cell, cols: cols, rows: rowsN)
                .frame(minWidth: width)
        }
        .frame(height: cell * 1.3 * CGFloat(rowsN))
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }

    private func grid(cells: [LibrarySeat], cell: CGFloat, cols: Int, rows: Int) -> some View {
        let c = SeatColors(dark: scheme == .dark)
        let rowH = cell * 1.3
        return ZStack(alignment: .topLeading) {
            ForEach(cells, id: \.self) { seat in
                let frame = CGRect(x: CGFloat(seat.x) * cell + 2, y: CGFloat(seat.y) * rowH + 2, width: cell - 4, height: rowH - 4)
                if !seat.isSeat {
                    // 통로 칸(숫자만)은 비우고 표지("입구", "2F입구")는 글자만.
                    if !seat.cont.isEmpty && !seat.cont.allSatisfy(\.isNumber) {
                        Text(seat.cont).font(.system(size: 9)).foregroundStyle(c.label.opacity(0.6)).lineLimit(1).minimumScaleFactor(0.6)
                            .frame(width: frame.width, height: frame.height).offset(x: frame.minX, y: frame.minY)
                    }
                } else {
                    let fill: AnyShapeStyle = seat.mine ? AnyShapeStyle(palette.primary) : seat.pending ? AnyShapeStyle(c.pending) : seat.available ? AnyShapeStyle(c.free) : AnyShapeStyle(c.taken)
                    let who: String? = seat.mine ? "나" : seat.pending ? "확인 중" : seat.sreIdx != nil ? seat.gender : nil
                    VStack(spacing: 1) {
                        Text(seat.cont).font(.system(size: 10, weight: .medium))
                        if let who {
                            Text(who).font(.system(size: 9))
                                .foregroundStyle(seat.mine ? Color.white : seat.gender == "남" ? Color(argb: 0xFF60A5FA) : seat.gender == "여" ? Color(argb: 0xFFF472B6) : c.label)
                        }
                    }
                    .foregroundStyle(seat.mine ? Color.white : c.label.opacity(seat.available ? 1 : 0.67))
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .frame(width: frame.width, height: frame.height)
                    .background(fill, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    .overlay { if seat.available { RoundedRectangle(cornerRadius: 6, style: .continuous).strokeBorder(palette.primary.opacity(0.35), lineWidth: 1) } }
                    .offset(x: frame.minX, y: frame.minY)
                    .onTapGesture { if seat.available || (seat.mine && !seat.assigned) { onTap(seat) } }
                }
            }
        }
        .frame(width: cell * CGFloat(cols), height: rowH * CGFloat(rows), alignment: .topLeading)
    }
}
