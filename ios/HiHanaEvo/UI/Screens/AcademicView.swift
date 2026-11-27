import SwiftUI

/// 학사일정 — 학사·하나 일정 + 게시글에서 뽑은 일정 + 클래스룸 마감을 날짜순 한 목록으로. 맨 위엔 다가오는 시험 D-day.
struct AcademicView: View {
    @SharedModel private var model
    @Environment(\.listPane) private var listPane

    var body: some View {
        let today = model.today
        // 2주 넘게 이어지는 상시 안내는 몇 달 전 시작일 머리로 목록 맨 위를 차지해 뺍니다(타임라인·홈에는 그대로).
        // 학사일정을 못 받아도(로그인 전·오류) 내 일정·게시글·클래스룸 일정은 그 아래에 그대로 — 직접 적은 일정까지 사라지지 않게.
        let failed = model.scheduleError != nil
        let merged = Agenda.merge(failed ? [] : model.schedule, grade: Prefs.shared.studentGrade, extras: model.agendaExtras, today: today).filter { !$0.longRunning }
        List {
            if let error = model.scheduleError {
                AcademicErrorBlock(error: error, onRetry: { Task { await model.loadSchedule(force: true) } }, onOpenAccount: { model.nav.present(AccountDialog()) })
                    .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            } else {
                if let exam = examDday(model.schedule, today: today) {
                    ExamDdayCard(exam: exam).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                }
                if model.scheduleLoading && merged.isEmpty {
                    LoadingBlock().listRowBackground(Color.clear)
                } else if merged.isEmpty {
                    ScheduleEmptyBlock { Task { await model.loadSchedule(force: true) } }.listRowBackground(Color.clear)
                }
            }
            do {
                ForEach(groups(merged)) { g in
                    Section(g.date.headerText) {
                        ForEach(Array(rows(g.items).enumerated()), id: \.offset) { _, row in
                            switch row {
                            case .academic(let e, let times): ScheduleRow(entry: e, times: times).materialRow()
                            case .agenda(let item): AgendaRow(item: item) { model.openAgenda(item, select: listPane) }
                                .materialRow(selected: model.agendaRoute(item).map { model.nav.isSelected($0) } ?? false)
                            }
                        }
                    }
                }
            }
        }
        .refreshable { await model.loadSchedule(force: true) }
        .toolbar {
            // 캘린더 앱처럼 오른쪽 위 + 로 내 일정 추가.
            ToolbarItem(placement: .topBarTrailing) {
                Button { model.nav.present(UserEventEditor(day: today)) } label: { Label("일정 추가", systemImage: "plus") }
            }
        }
        .task { await model.loadSchedule(force: false) }
    }

    private struct Group_: Identifiable { let date: LocalDate; let items: [AgendaItem]; var id: LocalDate { date } }

    private func groups(_ items: [AgendaItem]) -> [Group_] {
        var order: [LocalDate] = []
        var map: [LocalDate: [AgendaItem]] = [:]
        for i in items {
            if map[i.date] == nil { order.append(i.date) }
            map[i.date, default: []].append(i)
        }
        return order.map { Group_(date: $0, items: map[$0]!) }
    }

    private enum Row { case academic(HanaScheduleEntry, [String]), agenda(AgendaItem) }

    /// 같은 날 같은 선생님의 면학감독(0·1타임 등)은 한 줄에 시간만 이어 붙입니다.
    /// 줄 순서 — 종일 일정을 맨 위에(iOS 캘린더·삼성 캘린더 목록처럼), 그 아래는 [Agenda.merge] 가 정한 시각순(면학감독은 맨 아래).
    /// 예전엔 학사일정을 모두 먼저 놓아 오후 4시 게시글 일정이 저녁 면학감독 아래로 밀렸습니다.
    private func rows(_ items: [AgendaItem]) -> [Row] {
        var slots: [(key: String?, row: Row?)] = []
        var grouped: [String: [HanaScheduleEntry]] = [:]
        let allDay = items.filter { $0.time == nil && !$0.isSupervision }
        for i in allDay + items.filter({ !($0.time == nil && !$0.isSupervision) }) {
            if let e = i.academic, e.code == supervisionCode {
                let k = "sv:" + ScheduleKind.title(e)
                if grouped[k] == nil { slots.append((k, nil)) }
                grouped[k, default: []].append(e)
            } else if let e = i.academic {
                slots.append((nil, .academic(e, ScheduleKind.time(e).map { [$0] } ?? [])))
            } else {
                slots.append((nil, .agenda(i)))
            }
        }
        return slots.map { slot in
            if let row = slot.row { return row }
            let g = grouped[slot.key!]!
            var seen = Set<String>()
            return .academic(g[0], g.compactMap(ScheduleKind.time).filter { seen.insert($0).inserted })
        }
    }
}

/// 시험 D-day — 강조색 카드 하나로 눈에 띄게(One UI 캘린더의 '다음 일정' 배너 톤).
private struct ExamDdayCard: View {
    let exam: ExamDday
    @Environment(\.palette) private var palette
    var body: some View {
        OneUiCard(color: palette.primary) {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text(exam.name).font(.headline).foregroundStyle(palette.onPrimary)
                    // 남은 날은 오른쪽 큰 D-day 가 보여 줘 날짜만.
                    Text(exam.date.shortText).font(.subheadline).foregroundStyle(palette.onPrimary.opacity(0.8))
                }
                Spacer()
                Text(exam.days == 0 ? "D-Day" : "D-\(exam.days)").font(.title2.bold()).foregroundStyle(palette.onPrimary)
            }
        }
        .padding(.vertical, 8)
    }
}

/// 학사일정 한 줄 — 종류는 오른쪽 색 글자(하나일정·학사일정·면학감독)로 보여 줘 같은 뜻의 색 점은 두지 않습니다.
private struct ScheduleRow: View {
    let entry: HanaScheduleEntry
    let times: [String]
    var body: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(ScheduleKind.title(entry))
                let meta = ((!entry.allDay && !times.isEmpty) ? [times.joined(separator: ", ")] : []) + [entry.place].compactMap { $0 }
                if !meta.isEmpty { Text(meta.joined(separator: " · ")).font(.footnote).foregroundStyle(.secondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(ScheduleKind.label(entry.code)).font(.caption.weight(.semibold)).foregroundStyle(ScheduleKind.color(entry.code))
            // 일정 줄과 출처 글자가 한 줄로 맞게 화살표 자리를 비워 둡니다(눌러도 열리는 것은 없음).
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).hidden()
        }
    }
}

/// 게시글·클래스룸에서 온 일정 한 줄 — 마감이면 이름 앞에 '마감' 배지, 오른쪽엔 출처 색 글자. 원문이 있으면 화살표가 붙고 탭하면 그 글(과제)을 엽니다.
private struct AgendaRow: View {
    let item: AgendaItem
    let onOpen: () -> Void
    /// 원문이 있거나 내 일정(눌러서 고침)이면 누를 수 있습니다.
    private var opens: Bool { item.url != nil || item.source == .mine }
    var body: some View {
        let row = HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if item.deadline {
                        Text("마감").font(.caption2.weight(.bold)).foregroundStyle(.red)
                            .padding(.horizontal, 6).padding(.vertical, 2).background(.red.opacity(0.14), in: Capsule())
                    }
                    Text(item.title).foregroundStyle(Color.primary).lineLimit(2).multilineTextAlignment(.leading)
                }
                let meta = [item.time, item.endDate.map { "~" + $0.shortText }, item.detail.flatMap { $0.isEmpty ? nil : $0 }].compactMap { $0 }
                if !meta.isEmpty { Text(meta.joined(separator: " · ")).font(.footnote).foregroundStyle(Color.secondary).lineLimit(1) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(item.source.label).font(.caption.weight(.semibold)).foregroundStyle(item.source.color)
            // 눌러서 원문(게시글·과제)이 열리는 줄만 오른쪽 화살표 — 없는 줄도 같은 폭을 비워 출처 글자가 한 줄로 맞게.
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                .opacity(opens ? 1 : 0)
        }
        if opens { Button(action: onOpen) { row.contentShape(Rectangle()) } } else { row }
    }
}

/// 학사일정이 비었을 때 — 포털 파라미터가 안 맞을 수 있으니 다시 시도 버튼을 함께.
private struct ScheduleEmptyBlock: View {
    let onRetry: () -> Void
    @Environment(\.palette) private var palette
    @Environment(\.onPage) private var onPage
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("예정된 학사일정이 없습니다").font(.ui(.bodyLarge)).onPageVariant(palette, onPage)
            Text("학사일정이 계속 비어 있으면 다시 시도해 주세요.").font(.ui(.bodySmall)).onPageVariant(palette, onPage).padding(.top, 6)
            OneUiButton("다시 시도", style: .neutral, action: onRetry).padding(.top, 12)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 32)
    }
}

/// 학사일정을 캘린더로 — 오늘 이후 학사·하나 일정을 iCalendar(.ics) 로 만들어 미리 보기에 띄웁니다('모두 추가'로 캘린더에).
/// 면학감독·시설사용은 학생 개인 일정이 아니라 빼고, 같은 날 같은 이름은 한 번만. 시각이 있으면 그 시각, 없으면 종일.
@MainActor
enum ScheduleExport {
    static func share(_ entries: [HanaScheduleEntry]) -> Bool {
        let today = LocalDate.today()
        var seen = Set<String>()
        let events = entries
            .filter { $0.date >= today && $0.code != supervisionCode && $0.code != "SG02020000" }
            .filter { seen.insert("\($0.date.iso)|\($0.name.trimmingCharacters(in: .whitespaces))").inserted }
            .sorted { $0.date < $1.date }
        guard !events.isEmpty else { return false }
        let stamp = DateFormatter()
        stamp.locale = Locale(identifier: "en_US_POSIX")
        stamp.dateFormat = "yyyyMMdd'T'HHmmss"
        stamp.timeZone = TimeZone(identifier: "UTC")
        let now = stamp.string(from: Date()) + "Z"
        func day(_ d: LocalDate) -> String { String(format: "%04d%02d%02d", d.year, d.month, d.day) }
        func time(_ d: LocalDate, _ text: String?) -> (Int, Int)? {
            guard let hm = text?.trimmingCharacters(in: .whitespaces).split(separator: " ").last,
                  hm.range(of: #"^\d{1,2}:\d{2}$"#, options: .regularExpression) != nil else { return nil }
            let p = hm.split(separator: ":").map { Int($0) ?? 0 }
            return (min(max(p[0], 0), 23), min(max(p[1], 0), 59))
        }
        func escape(_ s: String) -> String {
            s.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: ";", with: "\\;").replacingOccurrences(of: ",", with: "\\,").replacingOccurrences(of: "\n", with: "\\n")
        }
        var lines = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//hihana-neo//schedule//KO", "CALSCALE:GREGORIAN", "X-WR-CALNAME:하나고 학사일정"]
        for e in events {
            lines += ["BEGIN:VEVENT", "UID:hihana-\(day(e.date))-\(e.name.stableHash)@hihana-neo", "DTSTAMP:\(now)"]
            if !e.allDay, let s = time(e.date, e.startTime) {
                var end = time(e.date, e.endTime) ?? (s.0 + 1, s.1)
                if end.0 * 60 + end.1 <= s.0 * 60 + s.1 { end = (min(s.0 + 1, 23), s.1) }
                lines.append("DTSTART;TZID=Asia/Seoul:\(day(e.date))T" + String(format: "%02d%02d00", s.0, s.1))
                lines.append("DTEND;TZID=Asia/Seoul:\(day(e.date))T" + String(format: "%02d%02d00", end.0, end.1))
            } else {
                lines += ["DTSTART;VALUE=DATE:\(day(e.date))", "DTEND;VALUE=DATE:\(day(e.date.adding(days: 1)))"]
            }
            lines.append("SUMMARY:\(escape(e.name.trimmingCharacters(in: .whitespaces)))")
            if let p = e.place, !p.trimmingCharacters(in: .whitespaces).isEmpty { lines.append("LOCATION:\(escape(p))") }
            lines.append("END:VEVENT")
        }
        lines.append("END:VCALENDAR")
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("hihana-schedule.ics")
        guard (try? lines.joined(separator: "\r\n").write(to: file, atomically: true, encoding: .utf8)) != nil else { return false }
        let vc = UIActivityViewController(activityItems: [file], applicationActivities: nil)
        guard let root = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first else { return false }
        // 아이패드는 공유 창이 팝오버라 기준 위치가 없으면 앱이 종료됩니다 — 오른쪽 위(공유 단추 자리)에 붙입니다.
        let top = root.topmost
        if let pop = vc.popoverPresentationController {
            pop.sourceView = top.view
            pop.sourceRect = CGRect(x: top.view.bounds.maxX - 60, y: top.view.safeAreaInsets.top + 10, width: 1, height: 1)
            pop.permittedArrowDirections = .up
        }
        top.present(vc, animated: true)
        return true
    }
}
