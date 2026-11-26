import SwiftUI

/*
 * 아이폰 '전체' — 안드로이드 ⊞ 모든 기능(TimeAxis.kt AxisPanel)과 같은 모양. 탭 바의 '전체'를 누르면 탭이 바뀌지 않고 이 시트가
 * 아래에서 올라오고(Instagram 의 + 탭처럼 탭 바 단추가 시트를 여는 방식), 탭 바는 보던 홈·타임라인에 그대로 남습니다.
 * 갈 곳 여덟은 카드 한 장에 두 칸씩, 바로 가기는 카드 한 장에 세 칸씩 — 칸 사이는 가는 선. 고르면 시트를 닫고 보던 탭에 그 화면을
 * 밀어 넣어, '<'로 돌아가면 홈·타임라인입니다(안드로이드도 그 화면을 닫으면 홈·타임라인).
 * 아이패드는 사이드바에 갈 곳이 모두 있어 쓰지 않습니다.
 */
struct AllSheetView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    private var store = QuickLinkStore.shared
    @State private var editing = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    grid(destinations, columns: 2, height: 116) { d in destinationCell(d) }
                    HStack {
                        Text("바로 가기").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                        Spacer()
                        Button("편집") { editing = true }.font(.subheadline.weight(.semibold))
                    }
                    .padding(.horizontal, 6)
                    .padding(.top, 4)
                    let chosen = store.keys.compactMap { k in QuickLink.all.first { $0.key == k } }
                    if chosen.isEmpty {
                        Text("'편집'을 눌러 자주 여는 곳을 두세요.").font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 6)
                    } else {
                        grid(chosen, columns: 3, height: 86) { q in quickCell(q) }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .scrollBounceBehavior(.basedOnSize)
            .pageBackground()
            .navigationTitle("전체")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { CloseButton { close() } }
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 0) {
                        BarButton(title: "통합 검색", symbol: "magnifyingglass") { go(.search) }
                        BarButton(title: "설정", symbol: "gearshape") { go(.settings(nil)) }
                    }
                }
            }
            .navigationDestination(isPresented: $editing) { QuickLinkEditView().pageBackground() }
        }
        .presentationDragIndicator(.visible)
    }

    private func close() { Navigator.shared.allOpen = false }

    /// 시트를 닫고 보던 탭에 그 화면을 밀어 넣습니다.
    private func go(_ route: Route) {
        close()
        Navigator.shared.open(route)
    }

    // MARK: 칸

    /// 카드 한 장에 [columns] 칸씩 — 칸마다 따로 띄우면 구멍이 숭숭해 보여 한 장으로 묶고 가는 선으로 나눕니다(안드로이드와 같음).
    private func grid<T: Identifiable, Cell: View>(_ items: [T], columns: Int, height: CGFloat, @ViewBuilder cell: @escaping (T) -> Cell) -> some View {
        let line = palette.onSurface.opacity(0.10)
        let rows = stride(from: 0, to: items.count, by: columns).map { Array(items[$0..<min($0 + columns, items.count)]) }
        return OneUiCard(cornerRadius: 26, padding: EdgeInsets()) {
            ForEach(Array(rows.enumerated()), id: \.offset) { r, row in
                if r > 0 { line.frame(height: 0.7).padding(.horizontal, 16) }
                HStack(spacing: 0) {
                    ForEach(0..<columns, id: \.self) { c in
                        if c > 0 { (c < row.count ? line : .clear).frame(width: 0.7).padding(.vertical, 16) }
                        if c < row.count { cell(row[c]).frame(maxWidth: .infinity, maxHeight: .infinity) } else { Color.clear.frame(maxWidth: .infinity) }
                    }
                }
                .frame(height: height)
            }
        }
    }

    /// 위에 색 원 아이콘과 이름(오른쪽 끝에 안 읽은 수), 아래에 한 줄 설명.
    private func destinationCell(_ d: AllDestination) -> some View {
        Button { go(d.route) } label: {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 10) {
                    SolidCircleIcon(symbol: d.symbol, color: d.color, size: 32)
                    Text(d.label).font(.headline).foregroundStyle(palette.onSurface).lineLimit(1)
                    Spacer(minLength: 4)
                    if d.badge > 0 { NewBadge(text: d.badge > 99 ? "99+" : "\(d.badge)") }
                }
                Spacer(minLength: 6)
                Text(d.hint).font(.footnote).foregroundStyle(palette.onSurface.opacity(0.62)).lineLimit(2).multilineTextAlignment(.leading)
            }
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .buttonStyle(RowPressStyle())
    }

    /// 색 원 아이콘과 이름만.
    private func quickCell(_ q: QuickLink) -> some View {
        Button { go(q.route) } label: {
            VStack(spacing: 8) {
                SolidCircleIcon(symbol: q.symbol, color: q.color, size: 30)
                Text(q.label).font(.subheadline.weight(.semibold)).foregroundStyle(palette.onSurface).lineLimit(1).minimumScaleFactor(0.85)
            }
            .padding(.horizontal, 6)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(RowPressStyle())
    }

    private var destinations: [AllDestination] {
        [
            AllDestination(label: "시간표", symbol: "tablecells.fill", color: Color(argb: 0xFF4C8DF6), hint: "이번 주 수업", route: .timetable),
            AllDestination(label: "학사일정", symbol: "calendar", color: Color(argb: 0xFF14A8B8), hint: "시험·행사·방학", route: .academic),
            AllDestination(label: "급식", symbol: "fork.knife", color: Color(argb: 0xFFF08A3C), hint: "이번 주 식단과 알레르기", route: .meal(nil)),
            AllDestination(label: "게시판", symbol: "newspaper.fill", color: Color(argb: 0xFFE3A82E), hint: "공지·가정통신문", route: .board),
            AllDestination(label: "신청·내역", symbol: "list.clipboard.fill", color: Color(argb: 0xFF8E7CFF), hint: "좌석·외출·신청 내역", route: .apply),
            AllDestination(label: "클래스룸", symbol: "graduationcap.fill", color: Color(argb: 0xFF2FB67C), hint: "공지·과제·자료", route: .classroom),
            AllDestination(label: "알리미", symbol: "tray.fill", color: Color(argb: 0xFFFF6B6B),
                           hint: model.alimUnread > 0 ? "안 읽은 알리미 \(model.alimUnread)건" : "학교에서 온 알림", badge: model.alimUnread, route: .alim),
            AllDestination(label: "날씨", symbol: "cloud.sun.fill", color: Color(argb: 0xFF3B9BF5), hint: "시간별·주간 예보", route: .weather(until: nil)),
        ]
    }
}

/// 갈 곳 하나 — 이름·색·설명은 안드로이드 axisDestinations 와 같습니다.
private struct AllDestination: Identifiable {
    let label: String
    let symbol: String
    let color: Color
    let hint: String
    var badge = 0
    let route: Route
    var id: String { label }
}

/// 꽉 찬 색 원에 흰 기호 — 안드로이드 모든 기능 칸의 아이콘과 같은 모양(신청·내역에서도 씀).
struct SolidCircleIcon: View {
    let symbol: String
    let color: Color
    let size: CGFloat
    var body: some View {
        Image(systemName: symbol).font(.system(size: size * 0.5, weight: .semibold)).foregroundStyle(.white)
            .frame(width: size, height: size).background(color, in: Circle())
    }
}

/// 바로 가기 편집 — 고른 칸은 끌어서 순서를 바꾸고 빼기, 아래 '더할 수 있는 곳'에서 더하기. 바꾸는 즉시 저장.
private struct QuickLinkEditView: View {
    private var store = QuickLinkStore.shared

    var body: some View {
        let chosen = store.keys.compactMap { k in QuickLink.all.first { $0.key == k } }
        let rest = QuickLink.all.filter { !store.keys.contains($0.key) }
        List {
            Section {
                ForEach(chosen) { q in
                    Label { Text(q.label) } icon: { SolidCircleIcon(symbol: q.symbol, color: q.color, size: 28) }.materialRow()
                }
                .onMove { store.keys.move(fromOffsets: $0, toOffset: $1) }
                .onDelete { idx in
                    let keys = idx.map { chosen[$0].key }
                    store.keys.removeAll { keys.contains($0) }
                }
            } header: { Text("바로 가기") } footer: {
                if chosen.isEmpty { Text("아래에서 자주 여는 곳을 더하세요.") }
            }
            if !rest.isEmpty {
                Section("더할 수 있는 곳") {
                    ForEach(rest) { q in
                        Button { withAnimation { store.keys.append(q.key) } } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "plus.circle.fill").font(.title3).foregroundStyle(.white, .green)
                                Label { Text(q.label).foregroundStyle(.primary) } icon: { SolidCircleIcon(symbol: q.symbol, color: q.color, size: 28) }
                            }
                        }
                        .materialRow()
                    }
                }
            }
        }
        .environment(\.editMode, .constant(.active))
        .navigationTitle("바로 가기 편집")
        .navigationBarTitleDisplayMode(.inline)
    }
}
