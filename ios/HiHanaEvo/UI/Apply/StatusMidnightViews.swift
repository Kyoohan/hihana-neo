import SwiftUI

// MARK: - 교과교실 현황

/// 교과교실 현황 — 타임과 건물·교실을 고르면 그 교실을 신청한 학생과 사유. 처음엔 건물 '전체'(모든 건물을 교실별로).
struct ClassroomStatusView: View {
    @State private var times: [ClassroomTime] = []
    @State private var time: String?
    @State private var buildings: [ClassroomBuildingOption] = []
    @State private var building: String?
    @State private var rooms: [ClassroomStatusRoom] = []
    @State private var room: String?
    @State private var occupants: [ClassroomOccupant]?
    @State private var loading = true
    @State private var error: String?
    @State private var reload = 0

    var body: some View {
        let all = building == nil
        let grouped = Dictionary(grouping: occupants ?? []) { o in
            if all, let b = o.building, !o.room.hasPrefix(b) { return "\(b) \(o.room)" }
            return o.room
        }.sorted { $0.key < $1.key }
        let selected = times.first { $0.id == time }
        List {
            Section {
                Picker("타임", selection: $time) { ForEach(times, id: \.id) { Text($0.label).tag(Optional($0.id)) } }.materialRow()
                if !buildings.isEmpty {
                    Picker("건물", selection: $building) {
                        Text("전체").tag(String?.none)
                        ForEach(buildings, id: \.num) { Text($0.name).tag(Optional($0.num)) }
                    }
                    .materialRow()
                }
                if !rooms.isEmpty {
                    Picker("교실", selection: $room) {
                        Text("전체").tag(String?.none)
                        ForEach(rooms, id: \.idx) { Text($0.name).tag(Optional($0.idx)) }
                    }
                    .materialRow()
                }
            }
            if let error {
                ContentUnavailableView("불러오지 못했습니다", systemImage: "exclamationmark.triangle", description: Text(error)).listRowBackground(Color.clear)
            } else if loading && occupants == nil {
                HStack(spacing: 10) { ProgressView(); Text("신청 목록을 불러오는 중").foregroundStyle(.secondary) }.listRowBackground(Color.clear)
            } else if let occupants {
                Text(occupants.isEmpty ? "이 조건으로 신청한 학생이 없습니다" : "신청 \(occupants.count)명").font(.footnote).foregroundStyle(.secondary).listRowBackground(Color.clear)
                ForEach(grouped, id: \.key) { roomName, people in
                    Section {
                        ForEach(people) { p in
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(p.name).fontWeight(.semibold)
                                    if let g = p.grade { Text("\(g)학년").font(.footnote).foregroundStyle(.secondary) }
                                    Spacer()
                                    if let s = p.status { Text(s).font(.caption).foregroundStyle(s.contains("승인") ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary)) }
                                }
                                Text(p.reason ?? "사유 없음").font(.subheadline).foregroundStyle(.secondary)
                            }
                            .materialRow()
                        }
                    } header: {
                        HStack { Text(roomName); Spacer(); Text("\(people.count)명") }
                    }
                }
            }
        }
        .navigationTitle("교과교실 현황")
        .applySubtitle(selected.map { t in [t.name, t.start.map { "\($0)–\(t.end ?? "")" }].compactMap { $0 }.joined(separator: " · ") })
        .refreshable { reload += 1 }
        .task {
            do {
                times = try await ClassroomStatusAPI.times()
                // 지금 진행 중이거나 다음에 올 타임을 먼저.
                let now = Date().minutesOfDay
                time = (times.first { t in PortalLists.time(t.end).map { $0 > now } ?? false } ?? times.last)?.id
                if times.isEmpty { error = "오늘 교과교실 타임이 없습니다" }
            } catch { self.error = applyErrorText(error) }
            if time == nil { loading = false }
        }
        .task(id: time) {
            guard let time else { return }
            error = nil
            buildings = (try? await ClassroomStatusAPI.buildings(time)) ?? []
            if !buildings.contains(where: { $0.num == building }) { building = nil }
        }
        .task(id: "\(time ?? "")-\(building ?? "")") {
            guard let time, let building else { rooms = []; room = nil; return }
            rooms = (try? await ClassroomStatusAPI.rooms(time, building: building)) ?? []
            if !rooms.contains(where: { $0.idx == room }) { room = nil }
        }
        .task(id: "\(time ?? "")-\(building ?? "")-\(room ?? "")-\(reload)") {
            guard let time else { return }
            loading = true
            do { occupants = try await ClassroomStatusAPI.occupants(time, building: building, room: room) } catch { self.error = applyErrorText(error) }
            loading = false
        }
    }
}

// MARK: - 심야면학

/// 심야면학 신청 — 타임·층을 고르고 빈 좌석을 누르면 바로 신청, 내 좌석을 누르면 취소.
struct MidnightView: View {
    @SharedModel private var model
    @State private var loggedIn = MidnightSession.isLoggedIn
    @State private var session = 1
    @State private var floor: Int?
    @State private var data: MidnightDashboard?
    @State private var loading = false
    @State private var error: String?
    @State private var busy = false
    @State private var notice: String?
    @State private var reload = 0
    @Environment(\.openURL) private var openURL

    var body: some View {
        Group {
            if !loggedIn {
                MidnightLoginForm { loggedIn = true; reload += 1 }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        if let d = data {
                            if d.roomUpdateRequired {
                                ContentUnavailableView {
                                    Label("호실 정보를 새로 입력해야 합니다", systemImage: "bed.double")
                                } description: { Text("새 학기 기숙사 호실은 웹 사이트에서 입력해 주세요.") } actions: {
                                    Button("웹 사이트 열기") { openURL(midnightStudyURL) }.buttonStyle(.bordered)
                                }
                            } else {
                                dashboard(d)
                            }
                        } else if let error {
                            ContentUnavailableView("불러오지 못했습니다", systemImage: "exclamationmark.triangle", description: Text(error))
                        } else {
                            HStack(spacing: 10) { ProgressView(); Text("불러오는 중").foregroundStyle(.secondary) }
                        }
                    }
                    .padding(.horizontal, 16).padding(.bottom, 80)
                }
                .refreshable { reload += 1 }
            }
        }
        .navigationTitle("심야면학 신청")
        .applySubtitle(data.map { d in [d.name.isEmpty ? nil : d.name, d.modeLabel].compactMap { $0 }.joined(separator: " · ") }.flatMap { $0.isEmpty ? nil : $0 })
        .toolbar {
            if loggedIn {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button { reload += 1 } label: { Label("새로고침", systemImage: "arrow.clockwise") }
                        Button { openURL(midnightStudyURL) } label: { Label("웹 사이트 열기", systemImage: "safari") }
                        Button(role: .destructive) { MidnightAPI.logout(model: model); loggedIn = false; data = nil } label: { Label("로그아웃", systemImage: "rectangle.portrait.and.arrow.right") }
                    } label: { Label("더 보기", systemImage: "ellipsis") }
                }
            }
        }
        .overlay(alignment: .bottom) {
            if let notice {
                Text(notice).font(.subheadline.weight(.medium)).padding(.horizontal, 20).padding(.vertical, 13)
                    .modifier(GlassCapsule()).padding(16).transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: notice)
        .task(id: notice) {
            guard let n = notice else { return }
            try? await Task.sleep(for: .seconds(2.6))
            if notice == n { notice = nil }
        }
        .task(id: "\(loggedIn)-\(session)-\(floor ?? -1)-\(reload)") { await load() }
    }

    private func load() async {
        guard loggedIn else { return }
        loading = true
        error = nil
        do {
            do { data = try await MidnightAPI.dashboard(session: session, floor: floor, model: model) }
            catch let e as MidnightError where session == 2 && e.message.range(of: "2타임|고사기간", options: .regularExpression) != nil {
                // 2타임이 없는 날이면 사이트처럼 1타임으로.
                session = 1
                data = try await MidnightAPI.dashboard(session: 1, floor: floor, model: model)
            }
        } catch {
            if (error as? MidnightError)?.loggedOut == true { loggedIn = false }
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func act(_ success: String, _ block: @escaping () async throws -> MidnightDashboard) {
        guard !busy else { return }
        busy = true
        Task {
            do { data = try await block(); notice = success }
            catch {
                if (error as? MidnightError)?.loggedOut == true { loggedIn = false }
                notice = error.localizedDescription
                reload += 1
            }
            busy = false
        }
    }

    @ViewBuilder private func dashboard(_ d: MidnightDashboard) -> some View {
        let currentFloor = floor ?? d.floor
        Picker("타임", selection: $session) {
            Text("심야 1타임").tag(1)
            if d.session2Available { Text("심야 2타임").tag(2) }
        }
        .pickerStyle(.segmented)
        if d.eligibleFloors.count > 1 {
            Picker("층", selection: Binding(get: { currentFloor ?? d.eligibleFloors[0] }, set: { floor = $0 })) {
                ForEach(d.eligibleFloors, id: \.self) { Text("\($0)층").tag($0) }
            }
            .pickerStyle(.segmented)
        }
        // 오늘의 신청 + 상태.
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("오늘의 신청 · \(session)타임").font(.headline)
                Spacer()
                Text(d.statusText).font(.caption).foregroundStyle(d.canApply ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
            }
            Text(d.hasReservation ? "\(d.reservationFloor ?? d.floor ?? 0)층 \(d.reservationSeat ?? 0)번 좌석" : "아직 신청하지 않았습니다")
                .fontWeight(d.hasReservation ? .semibold : .regular)
            Text([d.date, d.sessionStart.map { "\($0)–\(d.sessionEnd ?? "")" }, d.room.map { "\($0)호" }, "불참 \(d.absenceCount)회", d.bannedUntil.map { "\($0)까지 제한" }]
                .compactMap { $0 }.joined(separator: " · ")).font(.footnote).foregroundStyle(.secondary)
            if d.hasReservation {
                let mustCancel2First = session == 1 && d.hasSession2Reservation
                Button(role: .destructive) {
                    act("좌석 신청을 취소했습니다") { try await MidnightAPI.cancel(session: session, floor: floor, model: model) }
                } label: { Text(mustCancel2First ? "2타임 신청을 먼저 취소하세요" : "신청 취소").frame(maxWidth: .infinity) }
                .buttonStyle(.bordered).disabled(!d.open || mustCancel2First || busy).padding(.top, 6)
            }
        }
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        VStack(alignment: .leading, spacing: 10) {
            Text("\(d.floor ?? currentFloor ?? 0)층 좌석").font(.headline)
            Text(d.helpText).font(.footnote).foregroundStyle(.secondary)
            MidnightSeatLayout(seats: d.seats) { seat in
                if seat.mine { act("\(seat.number)번 좌석 신청을 취소했습니다") { try await MidnightAPI.cancel(session: session, floor: floor, model: model) } }
                else if seat.occupied { notice = "\(seat.number)번은 \(seat.applicantName ?? "다른 학생")이 신청했습니다" }
                else if d.hasReservation { notice = "이미 \(d.reservationSeat ?? 0)번을 신청했습니다 — 먼저 취소해 주세요" }
                else if !d.canApply { notice = d.helpText }
                else { act("\(seat.number)번 좌석 신청이 완료되었습니다") { try await MidnightAPI.reserve(seat: seat.number, session: session, floor: floor, model: model) } }
            }
            HStack(spacing: 14) {
                legend(MidnightColors.free, "선택 가능")
                legend(MidnightColors.taken, "신청 완료")
                HStack(spacing: 6) { RoundedRectangle(cornerRadius: 3).fill(.tint).frame(width: 10, height: 10); Text("내 좌석") }
            }
            .font(.caption).foregroundStyle(.secondary)
        }
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func legend(_ c: @escaping (Bool) -> Color, _ label: String) -> some View {
        MidnightLegend(color: c, label: label)
    }
}

private struct MidnightLegend: View {
    let color: (Bool) -> Color
    let label: String
    @Environment(\.colorScheme) private var scheme
    var body: some View { HStack(spacing: 6) { RoundedRectangle(cornerRadius: 3).fill(color(scheme == .dark)).frame(width: 10, height: 10); Text(label) } }
}

private enum MidnightColors {
    static func free(_ dark: Bool) -> Color { dark ? Color(argb: 0xFF2A3B2E) : Color(argb: 0xFFDCF5E3) }
    static func taken(_ dark: Bool) -> Color { dark ? Color(argb: 0xFF3A3A3D) : Color(argb: 0xFFE5E7EB) }
    static func freeText(_ dark: Bool) -> Color { dark ? Color(argb: 0xFFBDE5C8) : Color(argb: 0xFF387457) }
    static func takenText(_ dark: Bool) -> Color { dark ? Color(argb: 0xFFB0B4B8) : Color(argb: 0xFF6B7280) }
}

/// 좌석 배치 — 사이트와 같게: 11·15석이면 앞쪽 1~7번 묶음 + 뒤쪽 2×2 섬([9,8/10,11], [13,12/14,15]), 그 외 5열 격자.
private struct MidnightSeatLayout: View {
    let seats: [MidnightSeat]
    let onSeat: (MidnightSeat) -> Void

    var body: some View {
        let byNumber = Dictionary(seats.map { ($0.number, $0) }, uniquingKeysWith: { a, _ in a })
        if seats.count == 11 || seats.count == 15 {
            VStack(alignment: .leading, spacing: 12) {
                group { VStack(alignment: .leading, spacing: 8) { ForEach([[1, 2, 3, 4], [5, 6, 7]], id: \.self) { row in HStack(spacing: 8) { ForEach(row, id: \.self) { cell(byNumber[$0]) } } } } }
                HStack(spacing: 12) {
                    ForEach(seats.count == 15 ? [[9, 8, 10, 11], [13, 12, 14, 15]] : [[9, 8, 10, 11]], id: \.self) { pod in
                        group { VStack(spacing: 8) { ForEach([Array(pod.prefix(2)), Array(pod.suffix(2))], id: \.self) { row in HStack(spacing: 8) { ForEach(row, id: \.self) { cell(byNumber[$0]) } } } } }
                    }
                }
            }
        } else {
            let sorted = seats.sorted { $0.number < $1.number }
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(stride(from: 0, to: sorted.count, by: 5)), id: \.self) { i in
                    HStack(spacing: 8) { ForEach(sorted[i..<min(i + 5, sorted.count)], id: \.number) { cell($0) } }
                }
            }
        }
    }

    private func group<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        content().padding(8).overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(.quaternary, lineWidth: 1))
    }

    @ViewBuilder private func cell(_ seat: MidnightSeat?) -> some View {
        if let seat { MidnightSeatCell(seat: seat, onSeat: onSeat) } else { Color.clear.frame(width: 60, height: 60) }
    }
}

private struct MidnightSeatCell: View {
    let seat: MidnightSeat
    let onSeat: (MidnightSeat) -> Void
    @Environment(\.colorScheme) private var scheme
    @Environment(\.palette) private var palette

    var body: some View {
        let dark = scheme == .dark
        let fg = seat.mine ? Color.white : seat.occupied ? MidnightColors.takenText(dark) : MidnightColors.freeText(dark)
        Button { onSeat(seat) } label: {
            VStack(spacing: 0) {
                Text("\(seat.number)").font(.system(size: 16, weight: .bold))
                if let n = seat.applicantName { Text(n).font(.system(size: 10, weight: .semibold)).lineLimit(1) }
                if let r = seat.applicantRoom { Text(r).font(.system(size: 9)).opacity(0.8).lineLimit(1) }
            }
            .foregroundStyle(fg)
            .padding(3)
            .frame(width: 60, height: 60)
            .background(seat.mine ? palette.primary : seat.occupied ? MidnightColors.taken(dark) : MidnightColors.free(dark), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// 심야면학 로그인 — 사이트에 가입한 이름과 비밀번호. 비밀번호는 저장하지 않고 로그인 상태(토큰)만 키체인에.
private struct MidnightLoginForm: View {
    let onLoggedIn: () -> Void
    @State private var name = MidnightSession.name ?? ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?
    @Environment(\.openURL) private var openURL

    var body: some View {
        Form {
            Section {
                TextField("이름", text: $name).textContentType(.name).autocorrectionDisabled().materialRow()
                SecureField("비밀번호", text: $password).textContentType(.password).materialRow()
            } header: { Text("심야면학 로그인") } footer: {
                Text("심야면학 사이트에 가입한 이름과 비밀번호로 로그인합니다. 비밀번호는 저장하지 않고, 로그인 상태(토큰)만 이 기기에 암호화해 둡니다.")
            }
            if let error { Text(error).foregroundStyle(.red).listRowBackground(Color.clear) }
            Section {
                Button(busy ? "로그인 중" : "로그인") { login() }
                    .disabled(busy || name.trimmingCharacters(in: .whitespaces).isEmpty || password.isEmpty).fontWeight(.semibold).materialRow()
                Button("가입·비밀번호 찾기 (웹)") { openURL(midnightStudyURL) }.materialRow()
            }
        }
    }

    private func login() {
        busy = true
        error = nil
        Task {
            do { try await MidnightAPI.login(name: name, password: password); password = ""; onLoggedIn() }
            catch { self.error = error.localizedDescription }
            busy = false
        }
    }
}
