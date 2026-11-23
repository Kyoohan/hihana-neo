import Foundation
import Observation
import SwiftUI

/// 내 일정 — 학생이 직접 적은 일정(안드로이드 UserAgenda.kt 와 같은 꼴). 학사일정·게시글·클래스룸 일정과 똑같이 AgendaItem(.mine)으로
/// 바꿔 홈 '오늘 일정', 타임라인, 학사일정 목록, 통합 검색, 데일리 브리핑에 함께 들어갑니다. 이 기기에만 저장합니다.
struct UserEvent: Codable, Equatable, Identifiable {
    var id: String
    var title: String
    var date: LocalDate
    /// 여러 날 일정의 마지막 날 — 하루짜리면 nil.
    var endDate: LocalDate?
    /// 시작 시각(자정 기준 분) — 하루 종일이면 nil.
    var start: Int?
    /// 끝 시각 — 없어도 됩니다.
    var end: Int?
    /// 신청·제출 기한(마감)인지 — 홈의 '마감' 숫자, 빨간 표시.
    var deadline = false
    var memo: String?

    static let idPrefix = "u:"

    static func hm(_ m: Int) -> String { String(format: "%02d:%02d", m / 60, m % 60) }

    /// 통합 일정 한 줄로 — 시각은 다른 일정과 같은 "13:00" / "13:00~14:30" 모양.
    func toAgenda() -> AgendaItem {
        AgendaItem(id: Self.idPrefix + id, source: .mine, title: title, date: date,
                   endDate: endDate.flatMap { $0 > date ? $0 : nil },
                   time: start.map { s in Self.hm(s) + (end.map { "~" + Self.hm($0) } ?? "") },
                   deadline: deadline, detail: memo.flatMap { $0.isEmpty ? nil : $0 })
    }
}

@Observable
final class UserAgendaStore {
    static let shared = UserAgendaStore()
    private static let cache = "user_agenda_v1"

    private(set) var events: [UserEvent]

    private init() { events = JSONCache.read([UserEvent].self, Self.cache)?.value ?? [] }

    func items() -> [AgendaItem] { events.map { $0.toAgenda() } }

    func find(_ agendaID: String) -> UserEvent? {
        guard agendaID.hasPrefix(UserEvent.idPrefix) else { return nil }
        let id = String(agendaID.dropFirst(UserEvent.idPrefix.count))
        return events.first { $0.id == id }
    }

    func save(_ e: UserEvent) {
        if let i = events.firstIndex(where: { $0.id == e.id }) { events[i] = e } else { events.append(e) }
        events.sort { ($0.date, $0.start ?? -1) < ($1.date, $1.start ?? -1) }
        JSONCache.write(events, Self.cache)
    }

    func delete(_ id: String) {
        events.removeAll { $0.id == id }
        JSONCache.write(events, Self.cache)
    }

    static func newID() -> String { String(UUID().uuidString.lowercased().replacingOccurrences(of: "-", with: "").prefix(12)) }
}

/// 일정 추가·수정 — 캘린더 앱의 '새로운 이벤트'처럼: 제목, 일정/마감, 하루 종일, 시작·종료, 메모. [edit] 를 주면 고치고 지울 수 있습니다.
struct UserEventEditor: View {
    let edit: UserEvent?
    @Environment(\.dismiss) private var dismiss
    @State private var title: String
    @State private var deadline: Bool
    @State private var allDay: Bool
    @State private var multiDay: Bool
    @State private var start: Date
    @State private var endDate: Date
    @State private var hasEnd: Bool
    @State private var end: Date
    @State private var memo: String
    @State private var confirmDelete = false

    init(edit: UserEvent? = nil, day: LocalDate = .today()) {
        self.edit = edit
        let date = edit?.date ?? day
        // 새 일정은 다음 정시(07~23시 사이)부터 — 캘린더 앱도 다음 정시로 잡습니다.
        let nextHour = min(max(Seoul.calendar.component(.hour, from: Date()) + 1, 8), 23)
        let startMin = edit?.start ?? nextHour * 60
        _title = State(initialValue: edit?.title ?? "")
        _deadline = State(initialValue: edit?.deadline ?? false)
        _allDay = State(initialValue: edit != nil && edit?.start == nil)
        _multiDay = State(initialValue: edit?.endDate != nil)
        _start = State(initialValue: date.at(minutes: startMin))
        _endDate = State(initialValue: (edit?.endDate ?? date.adding(days: 1)).startOfDay)
        _hasEnd = State(initialValue: edit?.end != nil)
        _end = State(initialValue: date.at(minutes: edit?.end ?? min(startMin + 60, 23 * 60 + 59)))
        _memo = State(initialValue: edit?.memo ?? "")
    }

    private var cleanTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var startDay: LocalDate { start.localDate }
    private var startMinutes: Int { Seoul.calendar.component(.hour, from: start) * 60 + Seoul.calendar.component(.minute, from: start) }
    private var endMinutes: Int { Seoul.calendar.component(.hour, from: end) * 60 + Seoul.calendar.component(.minute, from: end) }

    private var orderError: String? {
        if multiDay && endDate.localDate <= startDay { return "끝나는 날은 시작하는 날 다음이어야 합니다" }
        if !allDay && !multiDay && hasEnd && endMinutes <= startMinutes { return "종료 시각은 시작 시각 뒤여야 합니다" }
        return nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("제목", text: $title).onChange(of: title) { _, v in if v.count > 80 { title = String(v.prefix(80)) } }
                    Picker("종류", selection: $deadline) {
                        Text("일정").tag(false)
                        Text("마감").tag(true)
                    }
                    .pickerStyle(.segmented)
                } footer: {
                    Text(deadline ? "신청·제출 기한 — 홈 '오늘 일정'의 마감 숫자에 들어가고 빨갛게 표시됩니다."
                                  : "행사·약속 같은 일정 — 홈·타임라인·학사일정 목록·데일리 브리핑에 함께 나옵니다.")
                }
                Section {
                    Toggle("하루 종일", isOn: $allDay)
                    DatePicker(multiDay ? "시작" : (allDay ? "날짜" : "시작"), selection: $start, displayedComponents: allDay ? [.date] : [.date, .hourAndMinute])
                    Toggle("여러 날", isOn: $multiDay.animation())
                    if multiDay {
                        DatePicker("끝나는 날", selection: $endDate, in: startDay.adding(days: 1).startOfDay..., displayedComponents: [.date])
                    }
                    if !allDay {
                        Toggle("종료 시각", isOn: $hasEnd.animation())
                        if hasEnd { DatePicker("종료", selection: $end, displayedComponents: [.hourAndMinute]) }
                    }
                } footer: {
                    if let orderError { Text(orderError).foregroundStyle(.red) }
                }
                Section {
                    TextField("메모 (장소·준비물 등)", text: $memo, axis: .vertical).lineLimit(3...8)
                        .onChange(of: memo) { _, v in if v.count > 300 { memo = String(v.prefix(300)) } }
                } footer: {
                    Text("내 일정은 이 기기에만 저장됩니다. 데일리 브리핑에서 AI 요약을 켜 두었다면 제목·메모가 브리핑을 만드는 서버로 함께 보내집니다.")
                }
                if let edit {
                    Section {
                        Button("일정 삭제", role: .destructive) { confirmDelete = true }
                            .frame(maxWidth: .infinity)
                            .confirmationDialog("이 일정을 지울까요?", isPresented: $confirmDelete, titleVisibility: .visible) {
                                Button("일정 삭제", role: .destructive) { UserAgendaStore.shared.delete(edit.id); dismiss() }
                            } message: { Text(edit.title) }
                    }
                }
            }
            .onChange(of: start) { old, new in
                // 시작을 옮기면 끝나는 날·종료 시각도 같은 간격만큼 따라갑니다(캘린더 앱처럼).
                if multiDay && endDate.localDate <= new.localDate { endDate = new.localDate.adding(days: 1).startOfDay }
                if hasEnd { end = end.addingTimeInterval(new.timeIntervalSince(old)) }
            }
            .navigationTitle(edit == nil ? "새로운 일정" : "일정 편집")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { CloseButton { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    ConfirmButton(edit == nil ? "추가" : "저장") { save() }.disabled(cleanTitle.isEmpty || orderError != nil)
                }
            }
        }
    }

    private func save() {
        guard !cleanTitle.isEmpty, orderError == nil else { return }
        let m = memo.trimmingCharacters(in: .whitespacesAndNewlines)
        UserAgendaStore.shared.save(UserEvent(
            id: edit?.id ?? UserAgendaStore.newID(), title: String(cleanTitle.prefix(80)), date: startDay,
            endDate: multiDay ? endDate.localDate : nil,
            start: allDay ? nil : startMinutes, end: allDay || !hasEnd ? nil : endMinutes,
            deadline: deadline, memo: m.isEmpty ? nil : String(m.prefix(300))))
        dismiss()
    }
}
