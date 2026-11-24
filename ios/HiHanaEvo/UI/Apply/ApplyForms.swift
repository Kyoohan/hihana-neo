import SwiftUI

// MARK: - 교과교실 신청

/// 교과교실 신청 — 건물 → 교실 → 타임(지난 타임은 숨김) → 사유, 교실에 따라 승인 교사와 주의사항 동의.
/// 아래엔 오늘 내 신청과 취소. 신청·취소는 확인 창에서 한 번 더 눌러야 보냅니다.
struct ClassroomApplyView: View {
    @Environment(AppModel.self) private var model
    @State private var buildings: [ClassroomBuildingOption] = []
    @State private var building: ClassroomBuildingOption?
    @State private var rooms: [ClassroomRoomOption]?
    @State private var room: ClassroomRoomOption?
    @State private var detail: ClassroomRoomDetail?
    @State private var slots: [ClassroomSlotOption]?
    @State private var slot: ClassroomSlotOption?
    @State private var teacher: ClassroomTeacher?
    @State private var agreed: Set<String> = []
    @State private var reason = ""
    @State private var error: String?
    @State private var mine: [MyClassroomApply]?
    @State private var mineError: String?
    @State private var mineReload = 0
    @State private var confirming = false
    @State private var cancelling: MyClassroomApply?
    @State private var sending = false
    @State private var result: (title: String, message: String)?

    private var ready: Bool {
        guard building != nil, room != nil, let d = detail, slot != nil else { return false }
        return (!d.needsTeacher || teacher != nil) && (!d.needsAgreement || agreed.count == d.checks.count)
    }

    var body: some View {
        let today = model.today
        Form {
            Section {
                if buildings.isEmpty && error == nil { ProgressView().materialRow() }
                else {
                    Picker("건물", selection: $building) { ForEach(buildings, id: \.self) { Text($0.name).tag(Optional($0)) } }.materialRow()
                    if let rooms {
                        if rooms.isEmpty { Text("이 건물에 신청할 수 있는 교실이 없습니다").foregroundStyle(.red).materialRow() }
                        else { Picker("교실", selection: $room) { Text("고르기").tag(ClassroomRoomOption?.none); ForEach(rooms, id: \.self) { Text($0.name).tag(Optional($0)) } }.materialRow() }
                    } else if building != nil {
                        HStack { Text("교실"); Spacer(); ProgressView() }.materialRow()
                    }
                }
            }
            if room != nil {
                if let d = detail {
                    Section {
                        LabeledContent("승인 방식", value: d.approvalLabel ?? room?.approvalLabel ?? "-").materialRow()
                        if let slots {
                            if slots.isEmpty { Text("오늘 남은 타임이 없습니다").foregroundStyle(.red).materialRow() }
                            else { Picker("타임", selection: $slot) { ForEach(slots, id: \.self) { Text($0.label).tag(Optional($0)) } }.materialRow() }
                        } else {
                            HStack { Text("타임"); Spacer(); ProgressView() }.materialRow()
                        }
                        if d.needsTeacher {
                            if d.teachers.isEmpty { Text("승인 교사가 없습니다 — 포털에서 신청해 주세요").foregroundStyle(.red).materialRow() }
                            else { Picker("승인 교사", selection: $teacher) { ForEach(d.teachers, id: \.self) { Text($0.name).tag(Optional($0)) } }.materialRow() }
                        }
                    }
                    if d.needsAgreement {
                        Section {
                            ForEach(d.checks, id: \.id) { c in
                                Toggle(c.text, isOn: Binding(get: { agreed.contains(c.id) }, set: { if $0 { agreed.insert(c.id) } else { agreed.remove(c.id) } }))
                                    .font(.subheadline).materialRow()
                            }
                        } header: { Text("주의사항") } footer: {
                            Text([d.notice, d.procedure].compactMap { $0 }.joined(separator: "\n\n"))
                        }
                    }
                    Section {
                        TextField("사유", text: $reason, axis: .vertical).lineLimit(2...5)
                            .onChange(of: reason) { _, v in if v.count > 200 { reason = String(v.prefix(200)) } }
                            .materialRow()
                    }
                    Section {
                        Button("신청하기") { confirming = true }.disabled(!ready || sending).fontWeight(.semibold).materialRow()
                    }
                } else {
                    HStack(spacing: 10) { ProgressView(); Text("교실 정보를 불러오는 중").foregroundStyle(.secondary) }.listRowBackground(Color.clear)
                }
            }
            if let error { Text(error).font(.footnote).foregroundStyle(.red).listRowBackground(Color.clear) }
            Section("오늘 내 신청") {
                if let mineError { Text(mineError).foregroundStyle(.red).materialRow() }
                else if let mine {
                    if mine.isEmpty { Text("오늘 신청한 교과교실이 없습니다").foregroundStyle(.secondary).materialRow() }
                    ForEach(mine) { a in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(a.room) · \(a.slot)").fontWeight(.semibold)
                                Text([a.start.map { clock($0) + (a.end.map { "–" + clock($0) } ?? "") }, a.status, a.reason].compactMap { $0 }.joined(separator: " · "))
                                    .font(.footnote).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if a.cancellable { Button("취소", role: .destructive) { cancelling = a }.buttonStyle(.bordered).controlSize(.small).disabled(sending) }
                        }
                        .materialRow()
                    }
                } else {
                    ProgressView().materialRow()
                }
            }
        }
        .navigationTitle("교과교실 신청")
        .applySubtitle("오늘 · \(today.month)월 \(today.day)일 (\(today.weekdayShort))")
        .toolbar { ToolbarItem(placement: .topBarTrailing) { PortalLink(path: ApplyService.classroom.applyPath, title: "교과교실") } }
        .task {
            do { buildings = try await ClassroomApplyAPI.buildings(); building = buildings.first } catch { self.error = applyErrorText(error) }
        }
        .task(id: building) {
            guard let b = building else { return }
            rooms = nil; room = nil; error = nil
            do { rooms = try await ClassroomApplyAPI.rooms(b.num) } catch { rooms = []; self.error = applyErrorText(error) }
        }
        .task(id: room) {
            detail = nil; slots = nil; slot = nil; teacher = nil; agreed = []
            guard let r = room else { return }
            error = nil
            do {
                let d = try await ClassroomApplyAPI.detail(r.idx)
                detail = d
                teacher = d.teachers.first
                // 포털처럼 이미 시작한 타임은 고를 수 없습니다 — 빼고, 남은 첫 타임을 고릅니다.
                let now = Date().minutesOfDay
                let open = try await ClassroomApplyAPI.slots(r.idx).filter { $0.start.map { $0 > now } ?? true }
                slots = open
                slot = open.first
            } catch { self.error = applyErrorText(error) }
        }
        .task(id: mineReload) {
            mineError = nil
            do { mine = try await ClassroomApplyAPI.mine() } catch { mineError = applyErrorText(error); mine = nil }
        }
        .alert("교과교실을 신청할까요?", isPresented: $confirming) {
            Button("신청") { apply() }
            Button("닫기", role: .cancel) {}
        } message: {
            Text(confirmLines.joined(separator: "\n"))
        }
        .alert("신청을 취소할까요?", isPresented: Binding(get: { cancelling != nil }, set: { if !$0 && !sending { cancelling = nil } }), presenting: cancelling) { a in
            Button("신청 취소", role: .destructive) { cancel(a) }
            Button("닫기", role: .cancel) {}
        } message: { a in Text("교실 \(a.room)\n타임 \(a.slot)") }
        .alert(result?.title ?? "", isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) { Button("확인") {} } message: { Text(result?.message ?? "") }
    }

    private var confirmLines: [String] {
        guard let b = building, let r = room, let s = slot, let d = detail else { return [] }
        return ["교실 \(b.name) \(r.name)", "타임 \(s.label)" + (s.end.map { "–" + clock($0) } ?? ""),
                d.needsTeacher ? teacher.map { "승인 교사 \($0.name)" } : nil,
                "사유 " + (reason.trimmingCharacters(in: .whitespaces).isEmpty ? "없음" : reason)].compactMap { $0 }
    }

    private func apply() {
        // 확인을 빠르게 두 번 눌러도 한 번만 보냅니다.
        guard !sending, let b = building, let r = room, let d = detail, let s = slot else { return }
        sending = true
        Task {
            do {
                result = ("신청했습니다", try await ClassroomApplyAPI.apply(building: b, room: r, detail: d, slot: s, reason: reason.trimmingCharacters(in: .whitespacesAndNewlines),
                                                                    teacher: teacher, agreed: d.checks.map(\.id).filter { agreed.contains($0) }))
            } catch { result = ("신청하지 못했습니다", applyErrorText(error)) }
            sending = false
            mineReload += 1
            model.syncPlacesSoon()
        }
    }

    private func cancel(_ a: MyClassroomApply) {
        guard !sending else { return }
        sending = true
        Task {
            do { result = ("취소했습니다", try await ClassroomApplyAPI.cancel(a.crtIdx)) } catch { result = ("취소하지 못했습니다", applyErrorText(error)) }
            sending = false
            cancelling = nil
            mineReload += 1
            model.syncPlacesSoon()
        }
    }
}

// MARK: - 외출외박 신청

/// 고칠 신청 — 내역에서 넘겨받아 양식을 채웁니다.
struct OutingEdit: Identifiable {
    let osmNo: Int
    let out: Date
    let back: Date
    let reason: String
    let transport: String?
    var id: Int { osmNo }
}

/// 외출·외박 신청 — 출발·복귀 날짜와 시각, 사유, 이동방법. 기간과 신청 기한을 미리 보여 주고 확인 창에서 한 번 더 눌러야 보냅니다.
/// [edit] 를 주면 같은 양식으로 그 신청을 고칩니다.
struct OutingApplyView: View {
    var edit: OutingEdit? = nil
    var onEdited: () -> Void = {}
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var form: OutingFormInfo?
    @State private var formError: String?
    @State private var out: Date
    @State private var back: Date
    @State private var reason: String
    @State private var transport: OutingTransport?
    @State private var other: String
    @State private var confirming = false
    @State private var sending = false
    @State private var succeeded = false
    @State private var result: (title: String, message: String)?

    init(edit: OutingEdit? = nil, onEdited: @escaping () -> Void = {}) {
        self.edit = edit
        self.onEdited = onEdited
        let now = Date()
        let hour = Seoul.calendar.date(bySetting: .minute, value: 0, of: now) ?? now
        let out = edit?.out ?? hour
        // 복귀 기본값은 오늘 20:00 — 19시 넘어 열면 출발(다음 정각)보다 앞서 처음부터 빨간 오류가 떴습니다.
        // 그럴 땐 출발을 바꿀 때와 같은 규칙(출발 + 1시간)으로 맞춥니다.
        let evening = LocalDate.today().at(minutes: 20 * 60)
        _out = State(initialValue: out)
        _back = State(initialValue: edit?.back ?? (evening > out ? evening : out.addingTimeInterval(3600)))
        _reason = State(initialValue: edit.map { OutingApplyAPI.cleanReason($0.reason) } ?? "")
        let known = edit?.transport.flatMap { t in OutingTransport.allCases.first { $0 != .other && $0.rawValue == t } }
        _transport = State(initialValue: edit?.transport == nil ? nil : known ?? .other)
        _other = State(initialValue: edit?.transport != nil && known == nil ? edit!.transport! : "")
    }

    private var cleanReason: String { OutingApplyAPI.cleanReason(reason).trimmingCharacters(in: .whitespacesAndNewlines) }
    private var transportText: String? { transport == .other ? other.trimmingCharacters(in: .whitespaces) : transport?.rawValue }
    private var orderError: String? { back <= out ? "복귀 일시는 출발 일시 이후여야 합니다" : nil }
    /// 수정은 승인 교사 번호가 필요 없어 양식을 못 받아도 보낼 수 있습니다.
    private var ready: Bool {
        (form != nil || edit != nil) && orderError == nil && !cleanReason.isEmpty && !(transportText ?? "").isEmpty && (transportText ?? "").count <= 200
    }

    var body: some View {
        let minDate = min(LocalDate.today().startOfDay, edit?.out ?? .distantFuture)
        let maxDate = LocalDate.today().adding(days: 366).startOfDay
        Form {
            if let formError, edit == nil {
                Text(formError).foregroundStyle(.red).listRowBackground(Color.clear)
            } else if form == nil && edit == nil {
                HStack(spacing: 10) { ProgressView(); Text("신청 양식을 불러오는 중").foregroundStyle(.secondary) }.listRowBackground(Color.clear)
            } else if let notice = form?.notice {
                Section { Text(notice).font(.footnote).foregroundStyle(.secondary).materialRow() }
            }
            Section {
                DatePicker("출발", selection: $out, in: minDate...maxDate).environment(\.timeZone, Seoul.zone).materialRow()
                    .onChange(of: out) { _, o in if back < o { back = o.addingTimeInterval(3600) } }
                DatePicker("복귀", selection: $back, in: minDate...maxDate).environment(\.timeZone, Seoul.zone).materialRow()
            } footer: {
                // 기간·기한 — 포털이 최종 판정하지만 보내기 전에 미리.
                let deadline = OutingApplyAPI.deadline(out.localDate) { model.context().isRestDay($0) }
                let late = Date() > deadline
                VStack(alignment: .leading, spacing: 2) {
                    Text(orderError ?? "기간 \(duration(out, back))").foregroundStyle(orderError != nil ? AnyShapeStyle(Color.red) : AnyShapeStyle(.tint)).fontWeight(.semibold)
                    Text("신청 기한 \(deadline.localDate.month)/\(deadline.localDate.day)(\(deadline.localDate.weekdayShort)) 09:00" + (late ? " · 지났습니다, 서버가 거절할 수 있어요" : ""))
                        .foregroundStyle(late ? Color.red : Color.secondary)
                }
            }
            Section {
                // 포털처럼 한글·영문·숫자·공백만 — 다른 글자는 입력되지 않습니다.
                TextField("사유", text: $reason, axis: .vertical).lineLimit(2...5)
                    .onChange(of: reason) { _, v in let c = String(OutingApplyAPI.cleanReason(v).prefix(300)); if c != v { reason = c } }
                    .materialRow()
            }
            Section("이동방법") {
                Picker("이동방법", selection: $transport) {
                    ForEach(OutingTransport.allCases, id: \.self) { Text($0.rawValue).tag(Optional($0)) }
                }
                .pickerStyle(.inline).labelsHidden().materialRow()
                if transport == .other {
                    TextField("이동방법 (200자 이내)", text: $other).materialRow()
                        .onChange(of: other) { _, v in if v.count > 200 { other = String(v.prefix(200)) } }
                }
            }
            Section {
                Button(edit != nil ? "수정하기" : "신청하기") { confirming = true }.disabled(!ready || sending).fontWeight(.semibold).materialRow()
                if succeeded && edit == nil {
                    RouteLink("외출·외박 내역 보기", value: Route.history(.outing))
                }
            }
        }
        .navigationTitle(edit != nil ? "외출·외박 수정" : "외출·외박 신청")
        .applySubtitle(form?.approverName.map { "승인 · \($0) 선생님" })
        .toolbar {
            if edit != nil { ToolbarItem(placement: .cancellationAction) { CloseButton { dismiss() } } }
            else { ToolbarItem(placement: .topBarTrailing) { PortalLink(path: ApplyService.outing.applyPath, title: "외출·외박") } }
        }
        .task {
            do { form = try await OutingApplyAPI.form() } catch { formError = applyErrorText(error) }
        }
        .alert(edit != nil ? "이대로 수정할까요?" : "외출·외박을 신청할까요?", isPresented: $confirming) {
            Button(edit != nil ? "수정" : "신청") { send() }
            Button("닫기", role: .cancel) {}
        } message: {
            Text(["출발 " + label(out), "복귀 " + label(back), "기간 " + duration(out, back), "사유 " + cleanReason, "이동방법 " + (transportText ?? ""),
                  form?.approverName.map { "승인 \($0) 선생님" }].compactMap { $0 }.joined(separator: "\n"))
        }
        .alert(result?.title ?? "", isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil; if edit != nil && succeeded { onEdited() } } })) {
            Button("확인") {}
        } message: { Text(result?.message ?? "") }
    }

    private func label(_ d: Date) -> String { let l = d.localDate; return "\(l.month)/\(l.day)(\(l.weekdayShort)) \(d.hm)" }

    private func duration(_ a: Date, _ b: Date) -> String {
        let minutes = max(0, Int(b.timeIntervalSince(a) / 60))
        let days = minutes / 1440, hours = minutes % 1440 / 60, mins = minutes % 60
        var s = [days > 0 ? "\(days)일" : nil, hours > 0 ? "\(hours)시간" : nil].compactMap { $0 }
        if mins > 0 || s.isEmpty { s.append("\(mins)분") }
        return s.joined(separator: " ")
    }

    private func send() {
        // 확인을 빠르게 두 번 눌러도 한 번만 보냅니다.
        guard !sending, ready, let transport else { return }
        sending = true
        Task {
            do {
                let message: String
                if let edit {
                    message = try await OutingApplyAPI.update(osmNo: edit.osmNo, out: out, back: back, reason: cleanReason, transport: transport, other: other)
                } else {
                    guard let form else { return }
                    message = try await OutingApplyAPI.apply(form: form, out: out, back: back, reason: cleanReason, transport: transport, other: other)
                }
                succeeded = true
                result = (edit != nil ? "수정했습니다" : "신청했습니다", message)
            } catch { result = ("신청하지 못했습니다", applyErrorText(error)) }
            sending = false
        }
    }
}

extension View {
    @ViewBuilder func applySubtitle(_ text: String?) -> some View {
        if #available(iOS 26.0, *), let text { navigationSubtitle(text) } else { self }
    }
}
