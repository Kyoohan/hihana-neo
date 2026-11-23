import SwiftUI

/*
 * 통합 검색 — 이 기기에 받아 둔 게시판(다섯 곳)·클래스룸·일정·알리미를 한 번에 찾습니다. 하이하나 전체 글은 맨 아래에서 이어서.
 * 검색창은 설정 앱과 같은 시스템 검색창(.searchable) — 아이패드는 사이드바 맨 위, 아이폰은 이 화면 위. 검색어는 Navigator.searchQuery.
 */

struct UnifiedSearchView: View {
    @Environment(AppModel.self) private var model
    @Bindable private var nav = Navigator.shared
    private var query: String { nav.searchQuery }

    var body: some View {
        let terms = query.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
        let r = terms.isEmpty ? nil : results(terms)
        List {
            // 하이하나 전체 글 검색은 언제나 — 검색어가 있으면 그 말로 바로 찾습니다.
            Section {
                Button {
                    let q = query.trimmingCharacters(in: .whitespaces)
                    model.nav.pendingSearch = q.isEmpty ? nil : q
                    model.nav.open(.portalSearch)
                } label: {
                    HStack(spacing: 12) {
                        IconTile("globe", .blue)
                        Text(query.trimmingCharacters(in: .whitespaces).isEmpty ? "하이하나 전체 글에서 찾기" : "하이하나 전체 글에서 ‘\(query.trimmingCharacters(in: .whitespaces))’ 찾기")
                            .foregroundStyle(Color.primary).lineLimit(1)
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                    .contentShape(Rectangle())
                }
                .materialRow()
            }
            if let r {
                if r.isEmpty {
                    ContentUnavailableView.search(text: query).listRowBackground(Color.clear)
                }
                if !r.agenda.isEmpty {
                    Section("일정 \(r.agenda.count)") {
                        ForEach(r.agenda.prefix(8)) { item in
                            row(icon: "calendar", tint: item.academic.map { ScheduleKind.color($0.code) } ?? item.source.color,
                                title: item.title, sub: [item.date.shortText, item.time, item.academic.map { ScheduleKind.label($0.code) } ?? item.source.label].compactMap { $0 }.joined(separator: " · ")) {
                                if item.academic != nil { model.nav.open(.academic) } else { model.openAgenda(item) }
                            }
                        }
                    }
                }
                if !r.board.isEmpty {
                    Section("게시판 \(r.board.count)") {
                        ForEach(r.board.prefix(10)) { p in
                            row(icon: AppTab.board.tileSymbol, tint: AppTab.board.tint, title: p.post.title,
                                sub: [p.category.label, p.post.writer, mailDate(p.post.date)].compactMap { $0 }.joined(separator: " · ")) { model.openBoardPost(p.post) }
                        }
                    }
                }
                if !r.classroom.isEmpty, let snap = model.classroomSnapshot {
                    Section("클래스룸 \(r.classroom.count)") {
                        ForEach(r.classroom.prefix(10)) { p in
                            row(icon: AppTab.classroom.tileSymbol, tint: AppTab.classroom.tint, title: p.title.isEmpty ? p.kind.label : p.title,
                                sub: [snap.course(p.courseId)?.name, p.kind.label, mailDate(p.updated)].compactMap { $0 }.joined(separator: " · ")) { model.openClassroomPost(p) }
                        }
                    }
                }
                if !r.alim.isEmpty {
                    Section("알리미 \(r.alim.count)") {
                        ForEach(r.alim.prefix(8)) { a in
                            row(icon: AppTab.alim.tileSymbol, tint: AppTab.alim.tint, title: a.content,
                                sub: [a.writer, mailDate(a.date)].compactMap { $0 }.joined(separator: " · ")) { model.openAlim(a) }
                        }
                    }
                }
                Section {
                } footer: {
                    Text("위 결과는 이 기기에 받아 둔 게시판·클래스룸·일정·알리미에서 찾은 것입니다.")
                }
            } else {
                Section {
                    Text("게시판 다섯 곳, 클래스룸 공지·과제, 학사일정과 게시글에서 뽑은 일정, 알리미를 한 번에 찾습니다.")
                        .font(.subheadline).foregroundStyle(.secondary)
                        .listRowBackground(Color.clear)
                }
            }
        }
        .navigationTitle("통합 검색")
        // 아이폰은 이 화면 위에 시스템 검색창(설정 앱과 같은 것). 아이패드는 사이드바의 검색창에서만 입력합니다.
        .modifier(PhoneSearchField(query: $nav.searchQuery))
        .onAppear {
            #if DEBUG
            // 시뮬레이터 확인용 — `-q 수학` 처럼 검색어를 미리 넣습니다.
            if let i = CommandLine.arguments.firstIndex(of: "-q"), i + 1 < CommandLine.arguments.count, query.isEmpty { nav.searchQuery = CommandLine.arguments[i + 1] }
            #endif
        }
    }

    private func row(icon: String, tint: Color, title: String, sub: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                IconTile(icon, tint)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(Color.primary).lineLimit(2).multilineTextAlignment(.leading)
                    if !sub.isEmpty { Text(sub).font(.footnote).foregroundStyle(Color.secondary).lineLimit(1) }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .materialRow()
    }

    // MARK: 찾기

    private struct BoardHit: Identifiable { let category: BoardCategory; let post: HanaBoardPost; var id: String { post.id } }

    private struct Results {
        var agenda: [AgendaItem] = []
        var board: [BoardHit] = []
        var classroom: [ClassroomPost] = []
        var alim: [HanaAlim] = []
        var isEmpty: Bool { agenda.isEmpty && board.isEmpty && classroom.isEmpty && alim.isEmpty }
    }

    private func results(_ terms: [String]) -> Results {
        func hit(_ fields: String?...) -> Bool {
            let hay = fields.compactMap { $0 }.joined(separator: "\n").lowercased()
            return terms.allSatisfy { hay.contains($0) }
        }
        var r = Results()
        r.agenda = Agenda.merge(model.schedule, grade: Prefs.shared.studentGrade, extras: model.agendaExtras, today: model.today.adding(days: -30))
            .filter { hit($0.title, $0.detail) }
        var seen = Set<String>()
        for c in BoardCategory.allCases {
            let posts = (c == model.boardCategory ? model.boardPosts : nil) ?? AcademicRepository.cachedBoard(c) ?? []
            for p in posts where hit(p.title, p.writer) && seen.insert(p.id).inserted { r.board.append(BoardHit(category: c, post: p)) }
        }
        if let snap = model.classroomSnapshot {
            r.classroom = snap.posts.filter { hit($0.title, $0.text, snap.course($0.courseId)?.name) }.sorted { $0.updated > $1.updated }
        }
        r.alim = model.alims.filter { hit($0.content, $0.writer) }
        return r
    }
}

private struct PhoneSearchField: ViewModifier {
    @Binding var query: String
    func body(content: Content) -> some View {
        if isPad { content }
        else { content.searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "게시판·클래스룸·일정·알리미") }
    }
}
