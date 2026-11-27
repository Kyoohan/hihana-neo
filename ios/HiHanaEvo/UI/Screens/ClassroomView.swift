import SwiftUI

/*
 * 구글 클래스룸 — 연결 전엔 안내, 연결 뒤엔 할 일(안 낸 과제, 마감순) → 스트림(최신순).
 * 수업 고르기는 오른쪽 위 메뉴, 보일 수업 편집은 그 메뉴 안. 검색창은 받아 둔 글에서 바로 거릅니다.
 */

private let classroomGreen = Color(argb: 0xFF1E8E3E)

func classroomDateTime(_ d: Date) -> String {
    let l = d.localDate
    return "\(l.month)/\(l.day) (\(l.weekdayShort)) \(d.hm)"
}

/// 마감 문구 — "마감 10/24 (금) 23:59", 지났으면 "마감 지남 · 10/24 (금)".
private func dueText(_ post: ClassroomPost, now: Date) -> String? {
    guard let due = post.due else { return nil }
    let when = post.dueAllDay ? due.localDate.shortText : classroomDateTime(due)
    return !post.submitted && due < now ? "마감 지남 · \(when)" : "마감 \(when)"
}

private func kindColor(_ kind: ClassroomKind) -> Color {
    switch kind {
    case .announcement: Color(argb: 0xFF3A7BD5)
    case .work: classroomGreen
    case .material: Color(argb: 0xFF8E6BD6)
    }
}

private func trimNumber(_ v: Double) -> String { v.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(v)) : String(v) }

struct ClassroomView: View {
    @SharedModel private var model
    @Environment(\.listPane) private var listPane
    @State private var query = ""
    @State private var editingCourses = false
    @State private var showingHiddenTodo = false

    var body: some View {
        let snapshot = model.classroomSnapshot
        let now = Date()
        let posts = (snapshot?.posts ?? []).filter { model.classroomCourseFilter == nil || $0.courseId == model.classroomCourseFilter }
        let todoAll = posts.filter { $0.kind == .work && !$0.submitted && ($0.due == nil || $0.due! > now.addingTimeInterval(-14 * 86400)) }
            .sorted { ($0.due ?? .distantFuture) < ($1.due ?? .distantFuture) }
        let hiddenTodo = model.classroomStore.hiddenTodo
        let todo = todoAll.filter { !hiddenTodo.contains($0.id) }
        let read = model.classroomStore.readIDs
        List {
            if !model.classroomConnected {
                ConnectSection()
            } else if snapshot == nil, let error = model.classroomError {
                AcademicErrorBlock(error: error, onRetry: { Task { await model.loadClassroom(force: true) } }, onOpenAccount: { ClassroomConnect.begin(model: model) })
                    .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            } else if let snapshot {
                let terms = query.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
                if !terms.isEmpty {
                    let results = snapshot.posts.filter { p in
                        let hay = (p.title + "\n" + p.text + "\n" + (snapshot.course(p.courseId)?.name ?? "")).lowercased()
                        return terms.allSatisfy { hay.contains($0) }
                    }.prefix(50)
                    if results.isEmpty {
                        ContentUnavailableView.search(text: query).listRowBackground(Color.clear)
                    } else {
                        Section("\(results.count)\(results.count == 50 ? "건 이상" : "건")") {
                            ForEach(Array(results)) { p in
                                StreamRow(post: p, course: snapshot.course(p.courseId), now: now, unread: !read.contains(p.id)) { model.openClassroomPost(p, select: listPane) }.plainRow(selected: model.nav.isSelected(.classroomPost(id: p.id)))
                            }
                        }
                    }
                } else {
                    if let error = model.classroomError {
                        Text("새로 받지 못해 마지막 받은 내용입니다 · \(error)").font(.footnote).foregroundStyle(.red).listRowBackground(Color.clear)
                    }
                    if snapshot.needsReconnect { ReconnectSection() }
                    if !todoAll.isEmpty {
                        Section {
                            if model.classroomStore.todoOpen {
                                ForEach(todo) { p in
                                    TodoRow(post: p, course: snapshot.course(p.courseId), now: now) { model.openClassroomPost(p, select: listPane) }
                                        .plainRow(selected: model.nav.isSelected(.classroomPost(id: p.id)))
                                        // 마감이 없거나 지난 과제만 — 실은 끝났는데 '안 냄'으로 남는 것. 메일·미리 알림처럼 밀어서.
                                        .swipeActions(edge: .trailing) {
                                            if p.due.map({ $0 < now }) ?? true {
                                                Button { withAnimation { model.classroomStore.hideTodo(p.id) } } label: { Label("숨기기", systemImage: "eye.slash") }
                                                    .tint(.gray)
                                            }
                                        }
                                }
                                let hiddenCount = todoAll.count - todo.count
                                if hiddenCount > 0 {
                                    Button("숨긴 과제 \(hiddenCount)개 보기") { showingHiddenTodo = true }.plainRow(selected: false)
                                }
                            }
                        } header: {
                            // 파일·메일 사이드바처럼 머리를 눌러 접고 펼침 — 접혀 있어도 할 일 개수는 보입니다.
                            Button { withAnimation(.snappy) { model.classroomStore.todoOpen.toggle() } } label: {
                                HStack(spacing: 6) {
                                    Text("할 일")
                                    Text("\(todo.count)").monospacedDigit().foregroundStyle(.secondary)
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tint)
                                        .rotationEffect(.degrees(model.classroomStore.todoOpen ? 90 : 0))
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("할 일 \(todo.count)개, \(model.classroomStore.todoOpen ? "접기" : "펼치기")")
                        }
                    }
                    if posts.isEmpty {
                        ContentUnavailableView(snapshot.courses.isEmpty ? "보일 수업이 없습니다" : "글이 없습니다", systemImage: "graduationcap")
                            .listRowBackground(Color.clear)
                    } else {
                        Section {
                            ForEach(posts) { p in
                                StreamRow(post: p, course: snapshot.course(p.courseId), now: now, unread: !read.contains(p.id)) { model.openClassroomPost(p, select: listPane) }.plainRow(selected: model.nav.isSelected(.classroomPost(id: p.id)))
                            }
                        } header: {
                            HStack {
                                Text("스트림")
                                Spacer()
                                // 메일의 '모두 읽음'처럼 — 지금 보이는(고른 수업의) 글만.
                                Button("모두 읽음") { model.classroomStore.markRead(posts) }
                                    .font(.subheadline)
                                    .disabled(!posts.contains { !read.contains($0.id) })
                            }
                        }
                    }
                }
            } else {
                LoadingBlock().listRowBackground(Color.clear)
            }
        }
        .listStyle(.plain)
        .refreshable { await model.loadClassroom(force: true) }
        .navigationTitle(snapshot?.courses.first { $0.id == model.classroomCourseFilter }?.name ?? "클래스룸")
        .modifier(ClassroomSearchable(enabled: snapshot != nil, query: $query))
        .toolbar {
            if let snapshot, model.classroomConnected {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Picker("수업", selection: Binding(get: { model.classroomCourseFilter }, set: { model.classroomCourseFilter = $0 })) {
                            Text("전체 수업").tag(String?.none)
                            ForEach(snapshot.courses) { c in
                                if let s = c.section { Text("\(c.name)\n\(s)").tag(Optional(c.id)) } else { Text(c.name).tag(Optional(c.id)) }
                            }
                        }
                        Divider()
                        Button { editingCourses = true } label: { Label("보일 수업 고르기", systemImage: "checklist") }
                    } label: { Label("수업", systemImage: "line.3.horizontal.decrease.circle") }
                }
            }
        }
        .sheet(isPresented: $showingHiddenTodo) {
            HiddenTodoSheet(posts: todoAll.filter { hiddenTodo.contains($0.id) }, course: { snapshot?.course($0) }, now: now) {
                model.classroomStore.showTodo($0)
            }
        }
        .sheet(isPresented: $editingCourses) {
            CourseSheet(courses: model.classroomStore.snapshot?.courses ?? [], hidden: model.classroomStore.hidden) { model.classroomStore.hidden = $0 }
        }
        .task { await model.loadClassroom(force: false) }
    }
}

private struct ClassroomSearchable: ViewModifier {
    let enabled: Bool
    @Binding var query: String
    func body(content: Content) -> some View {
        // 게시판과 같은 자리 — 목록 맨 위 제목 아래에 늘 보이는 검색창(메일·메모처럼 그 목록을 찾는 검색).
        if enabled { content.searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "제목·본문·수업 이름으로 찾기") } else { content }
    }
}

/// 연결 전 — 학교 계정으로 '허용'을 한 번. 허용하러 갔다 왔으면 '확인'.
private struct ConnectSection: View {
    @SharedModel private var model
    var body: some View {
        let pending = model.bridge.pending
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Label(pending ? "허용을 마치면 채워집니다" : "Google 클래스룸 연결", systemImage: "graduationcap.fill")
                    .font(.headline).foregroundStyle(Color.primary).labelStyle(TintedIconLabel(tint: classroomGreen))
                Text(pending
                     ? "브라우저에서 학교 계정을 고르고 '허용'까지 누른 뒤 앱으로 돌아오면 공지와 과제가 보입니다. 이미 했다면 '확인'을 눌러 주세요."
                     : "브라우저에서 학교 Google 계정으로 '허용'을 한 번 누르면 수업 공지와 과제, 제출 상태를 여기서 보고 마감과 공지 속 일정이 홈 일정에 함께 올라갑니다. 읽기만 하며, 내용은 암호화되어 오고 이 기기에만 저장됩니다.")
                    .font(.subheadline).foregroundStyle(.secondary)
                if let error = model.classroomError { Text(error).font(.footnote).foregroundStyle(.red) }
            }
            .padding(.vertical, 4)
            .materialRow()
            if pending {
                Button(model.classroomLoading ? "확인 중" : "확인") { Task { await model.loadClassroom(force: true) } }
                    .disabled(model.classroomLoading).materialRow()
                Button("브라우저 다시 열기") { ClassroomConnect.begin(model: model) }.materialRow()
            } else {
                Button("학교 계정으로 연결") { ClassroomConnect.begin(model: model) }.materialRow()
            }
        }
    }
}

private struct TintedIconLabel: LabelStyle {
    let tint: Color
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 8) { configuration.icon.foregroundStyle(tint); configuration.title }
    }
}

private struct TodoRow: View {
    let post: ClassroomPost
    let course: ClassroomCourse?
    let now: Date
    let action: () -> Void

    var body: some View {
        let overdue = post.due.map { $0 < now } ?? false
        Button(action: action) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(post.title).foregroundStyle(Color.primary).lineLimit(2).multilineTextAlignment(.leading)
                    Text([course?.name, dueText(post, now: now) ?? "마감 없음"].compactMap { $0 }.joined(separator: " · "))
                        .font(.footnote).foregroundStyle(overdue ? Color.red : Color.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let due = post.due, !overdue {
                    let days = LocalDate.days(from: now.localDate, to: due.localDate)
                    Text(days <= 0 ? "오늘" : "D-\(days)").font(.caption.weight(.bold)).foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(days <= 1 ? AnyShapeStyle(Color.red) : AnyShapeStyle(.tint), in: Capsule())
                }
            }
        }
    }
}

private struct StreamRow: View {
    let post: ClassroomPost
    let course: ClassroomCourse?
    let now: Date
    let unread: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 8) {
                Circle().fill(unread ? AnyShapeStyle(.tint) : AnyShapeStyle(.clear)).frame(width: 10, height: 10).padding(.top, 6)
                VStack(alignment: .leading, spacing: 2) {
                    // Mail 처럼 굵은 수업명과 오른쪽 날짜·화살표, 그 아래 종류와 제목.
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(course?.name ?? post.kind.label).font(.headline).foregroundStyle(Color.primary).lineLimit(1)
                        Spacer(minLength: 6)
                        Text(mailDate(post.updated)).font(.subheadline).foregroundStyle(Color.secondary)
                        Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                    (Text(post.kind.label + "  ").foregroundStyle(kindColor(post.kind)).bold() + Text(post.title).foregroundStyle(Color.primary))
                        .font(.subheadline.weight(unread ? .semibold : .regular)).lineLimit(2).multilineTextAlignment(.leading)
                    // 공지는 첫 줄이 제목이므로 나머지만, 과제·자료는 설명을 두 줄까지.
                    let body = post.kind == .announcement
                        ? post.text.split(separator: "\n", omittingEmptySubsequences: false).dropFirst().joined(separator: " ").trimmingCharacters(in: .whitespaces)
                        : post.text.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
                    if !body.isEmpty { Text(body).font(.subheadline).foregroundStyle(Color.secondary).lineLimit(2).multilineTextAlignment(.leading) }
                    if post.kind == .work {
                        if post.submitted {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark.circle.fill")
                                Text(post.late ? "늦게 제출함" : "제출함")
                                if let g = post.grade { Text("\(trimNumber(g))\(post.maxPoints.map { "/" + trimNumber($0) } ?? "")점").foregroundStyle(Color.secondary) }
                            }
                            .font(.caption).foregroundStyle(classroomGreen)
                        } else {
                            let overdue = post.due.map { $0 < now } ?? false
                            Text(dueText(post, now: now) ?? "마감 없음").font(.caption.weight(.semibold))
                                .foregroundStyle(overdue ? AnyShapeStyle(Color.red) : AnyShapeStyle(.tint))
                        }
                    }
                    if !post.attachments.isEmpty { Text("첨부 \(post.attachments.count)").font(.caption).foregroundStyle(Color.secondary) }
                }
            }
        }
    }
}

/// 보일 수업 — 고른 수업만 클래스룸 화면과 홈 일정에. 새로 생긴 수업은 기본으로 보입니다.
private struct CourseSheet: View {
    let courses: [ClassroomCourse]
    let onSave: (Set<String>) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var shown: Set<String>

    init(courses: [ClassroomCourse], hidden: Set<String>, onSave: @escaping (Set<String>) -> Void) {
        self.courses = courses
        self.onSave = onSave
        _shown = State(initialValue: Set(courses.map(\.id)).subtracting(hidden))
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    let all = shown.count == courses.count
                    Button(all ? "모두 해제" : "모두 선택") { shown = all ? [] : Set(courses.map(\.id)) }
                } footer: { Text("고른 수업만 클래스룸 화면과 홈 일정에 보입니다.") }
                Section {
                    ForEach(courses) { c in
                        Button {
                            if shown.contains(c.id) { shown.remove(c.id) } else { shown.insert(c.id) }
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(c.name).foregroundStyle(Color.primary)
                                    if let s = c.section { Text(s).font(.caption).foregroundStyle(Color.secondary) }
                                }
                                Spacer()
                                if shown.contains(c.id) { Image(systemName: "checkmark").fontWeight(.semibold) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("보일 수업")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { CloseButton { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    ConfirmButton("저장") { onSave(Set(courses.map(\.id)).subtracting(shown)); dismiss() }
                }
            }
        }
    }
}

/// 할 일에서 숨긴 과제 — 다시 보일 것을 골라 되돌립니다('보일 수업'과 같은 꼴).
private struct HiddenTodoSheet: View {
    let posts: [ClassroomPost]
    let course: (String) -> ClassroomCourse?
    let now: Date
    let onRestore: (Set<String>) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var picked: Set<String> = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    let all = !posts.isEmpty && picked.count == posts.count
                    Button(all ? "모두 해제" : "모두 선택") { picked = all ? [] : Set(posts.map(\.id)) }
                } footer: { Text("고른 과제를 할 일에 다시 보입니다. 숨기기는 이 기기에만 적용되고 클래스룸에는 아무것도 보내지 않습니다.") }
                Section {
                    ForEach(posts) { p in
                        Button {
                            if picked.contains(p.id) { picked.remove(p.id) } else { picked.insert(p.id) }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(p.title).foregroundStyle(Color.primary).lineLimit(2).multilineTextAlignment(.leading)
                                    Text([course(p.courseId)?.name, dueText(p, now: now) ?? "마감 없음"].compactMap { $0 }.joined(separator: " · "))
                                        .font(.caption).foregroundStyle(Color.secondary)
                                }
                                Spacer()
                                if picked.contains(p.id) { Image(systemName: "checkmark").fontWeight(.semibold) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("숨긴 과제")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { CloseButton { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    ConfirmButton("다시 보이기") { onRestore(picked); dismiss() }.disabled(picked.isEmpty)
                }
            }
        }
    }
}

/// 과제 권한이 빠진 연결 — 같은 비밀키로 웹 앱을 다시 열면 Google 이 새 권한만 묻습니다.
private struct ReconnectSection: View {
    @SharedModel private var model
    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Label("과제를 보려면 한 번 더 허용해 주세요", systemImage: "graduationcap.fill")
                    .font(.headline).foregroundStyle(Color.primary).labelStyle(TintedIconLabel(tint: classroomGreen))
                Text("과제를 읽는 권한이 빠진 채 연결돼 있습니다. 브라우저에서 학교 계정으로 '허용'을 한 번 더 누르면 과제·마감·점수가 보입니다.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
            .materialRow()
            Button("다시 허용하기") { ClassroomConnect.begin(model: model) }.materialRow()
        }
    }
}

// MARK: - 글

/// 클래스룸 글 — 제목, 올린 시각, (과제면) 마감·제출·점수, 본문(주소는 누를 수 있게), 첨부. '클래스룸에서 열기'는 오른쪽 위.
struct ClassroomPostView: View {
    let id: String
    @SharedModel private var model

    var body: some View {
        let snapshot = model.classroomStore.snapshot
        if let post = snapshot?.posts.first(where: { $0.id == id }) {
            let course = snapshot?.course(post.courseId)
            let now = Date()
            List {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        // 공지는 클래스룸에 제목이 없어 글 전체를 본문으로 — 첫 줄만 제목처럼 크게 빼지 않습니다.
                        if post.kind != .announcement { Text(post.title).font(.title3.bold()) }
                        Text("올림 " + classroomDateTime(post.created)).font(.footnote).foregroundStyle(.secondary)
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 4, leading: 4, bottom: 4, trailing: 4))
                }
                if post.kind == .work {
                    Section {
                        let overdue = post.due.map { !post.submitted && $0 < now } ?? false
                        LabeledContent("마감") {
                            let text = post.due.map { post.dueAllDay ? $0.localDate.shortText : classroomDateTime($0) } ?? "없음"
                            Text(overdue ? "지남 · \(text)" : text).foregroundStyle(overdue ? Color.red : Color.secondary)
                        }
                        .materialRow()
                        LabeledContent("제출") {
                            Text(post.submitted ? (post.late ? "늦게 제출함" : "제출함") : "아직 제출하지 않음")
                                .foregroundStyle(post.submitted ? classroomGreen : Color.secondary)
                        }
                        .materialRow()
                        if post.maxPoints != nil || post.grade != nil {
                            LabeledContent("점수", value: post.grade.map { g in "\(trimNumber(g))\(post.maxPoints.map { "/" + trimNumber($0) } ?? "")점" }
                                           ?? "\(trimNumber(post.maxPoints ?? 0))점 만점 · 아직 채점 전")
                                .materialRow()
                        }
                    }
                }
                if !post.text.isEmpty {
                    Section {
                        Text(linked(post.text)).textSelection(.enabled).materialRow()
                    }
                }
                if !post.attachments.isEmpty {
                    Section("첨부") {
                        ForEach(post.attachments, id: \.url) { a in
                            if let u = URL(string: a.url) {
                                Link(destination: u) { Label(a.title, systemImage: "arrow.up.right.square").lineLimit(2) }.materialRow()
                            }
                        }
                    }
                }
            }
            .navigationTitle(post.kind.label)
            .navigationBarTitleDisplayMode(.inline)
            .classroomSubtitle(course?.name)
            .toolbar {
                if let link = post.link, let u = URL(string: link) {
                    ToolbarItem(placement: .topBarTrailing) { Link(destination: u) { Label("클래스룸에서 열기", systemImage: "arrow.up.forward.app") } }
                }
            }
        } else {
            ContentUnavailableView("글을 찾을 수 없습니다", systemImage: "questionmark.folder")
        }
    }

    /// 본문 속 주소를 누를 수 있게 — 클래스룸 공지는 링크를 본문에 바로 적는 일이 많습니다.
    private func linked(_ text: String) -> AttributedString {
        var out = AttributedString(text)
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else { return out }
        for m in detector.matches(in: text, range: NSRange(text.startIndex..., in: text)) {
            guard let url = m.url, let r = Range(m.range, in: text),
                  let lo = AttributedString.Index(r.lowerBound, within: out), let hi = AttributedString.Index(r.upperBound, within: out) else { continue }
            out[lo..<hi].link = url
            out[lo..<hi].underlineStyle = .single
        }
        return out
    }
}

private extension View {
    @ViewBuilder func classroomSubtitle(_ text: String?) -> some View {
        if #available(iOS 26.0, *), let text { navigationSubtitle(text) } else { self }
    }
}
