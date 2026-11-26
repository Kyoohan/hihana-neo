import SwiftUI
import Observation

/// 바로 가기 한 칸 — 신청·내역 안의 화면으로 곧장.
struct QuickLink: Identifiable {
    let key: String
    let label: String
    let symbol: String
    let color: Color
    let route: Route
    var id: String { key }

    static let all: [QuickLink] = [
        QuickLink(key: "library", label: "도서관 좌석", symbol: "books.vertical.fill", color: Color(argb: 0xFF8E7CFF), route: .seats(.library)),
        QuickLink(key: "study_room", label: "면학실 좌석", symbol: "chair.fill", color: Color(argb: 0xFF5C7CFA), route: .seats(.studyRoom)),
        QuickLink(key: "classroom", label: "교과교실", symbol: "door.left.hand.open", color: Color(argb: 0xFF14A8B8), route: .quickApply(.classroom)),
        QuickLink(key: "outing", label: "외출·외박", symbol: "figure.walk", color: Color(argb: 0xFF34B27D), route: .quickApply(.outing)),
        QuickLink(key: "midnight", label: "심야면학", symbol: "moon.zzz.fill", color: Color(argb: 0xFF4B4FC9), route: .midnight),
        QuickLink(key: "library_history", label: "도서관 내역", symbol: "book.fill", color: Color(argb: 0xFF9B8AE6), route: .history(.library)),
        QuickLink(key: "study_history", label: "면학실 내역", symbol: "chart.line.uptrend.xyaxis", color: Color(argb: 0xFF6F8BE8), route: .history(.studyRoom)),
        QuickLink(key: "outing_history", label: "외출 내역", symbol: "house.fill", color: Color(argb: 0xFF4FAE85), route: .history(.outing)),
        QuickLink(key: "classroom_status", label: "교실별 현황", symbol: "square.grid.3x3.fill", color: Color(argb: 0xFF2E9FAE), route: .classroomStatus),
    ]
}

/// 바로 가기에 둘 칸과 순서 — '편집'에서 고르고 기기에 저장합니다. 처음엔 좌석·신청·심야면학·외출 내역 여섯.
@Observable
final class QuickLinkStore {
    static let shared = QuickLinkStore()
    static let defaults = ["library", "study_room", "classroom", "outing", "midnight", "outing_history"]
    var keys: [String] { didSet { SharedContainer.defaults.set(keys.joined(separator: ","), forKey: "quick_links.keys") } }
    private init() {
        if let raw = SharedContainer.defaults.string(forKey: "quick_links.keys") { keys = raw.isEmpty ? [] : raw.split(separator: ",").map(String.init) }
        else { keys = Self.defaults }
    }
}

/// 갈 곳 하나 — 탭 기호와 이름, (알리미만) 안 읽은 수.
private struct Destination: Identifiable {
    let tab: AppTab
    var badge = 0
    let route: Route
    var id: AppTab { tab }
}

/// 전체 — 갈 곳 여덟과 바로 가기. 바로 가기는 '편집'에서 더하고 빼고 순서를 바꿉니다.
struct AllPanelView: View {
    @Environment(AppModel.self) private var model
    @State private var editMode: EditMode = .inactive
    private var store = QuickLinkStore.shared

    var body: some View {
        let editing = editMode.isEditing
        let chosen = store.keys.compactMap { k in QuickLink.all.first { $0.key == k } }
        let rest = QuickLink.all.filter { !store.keys.contains($0.key) }
        List {
            if !editing {
                Section {
                    ForEach(destinations) { d in
                        RouteLink(d.route) {
                            HStack {
                                Label { Text(d.tab.label) } icon: { IconTile(d.tab.tileSymbol, d.tab.tint) }
                                Spacer(minLength: 8)
                                // 설정 앱의 '업데이트 있음'처럼 빨간 알약 — 화살표 앞에.
                                if d.badge > 0 { NewBadge(text: "\(d.badge)") }
                            }
                        }
                    }
                }
            }
            Section {
                ForEach(chosen) { q in
                    RouteLink(q.route) { Label { Text(q.label) } icon: { IconTile(q.symbol, q.color) } }
                }
                .onMove { store.keys.move(fromOffsets: $0, toOffset: $1) }
                .onDelete { idx in
                    let keys = idx.map { chosen[$0].key }
                    store.keys.removeAll { keys.contains($0) }
                }
            } header: { Text("바로 가기") } footer: {
                if chosen.isEmpty && !editing { Text("'편집'을 눌러 자주 여는 곳을 두세요.") }
            }
            if editing && !rest.isEmpty {
                Section("더할 수 있는 곳") {
                    ForEach(rest) { q in
                        Button { withAnimation { store.keys.append(q.key) } } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "plus.circle.fill").font(.title3).foregroundStyle(.white, .green)
                                Label { Text(q.label).foregroundStyle(.primary) } icon: { IconTile(q.symbol, q.color) }
                            }
                        }
                        .materialRow()
                    }
                }
            }
        }
        .environment(\.editMode, $editMode)
        .navigationTitle("전체")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button(editing ? "완료" : "편집") { withAnimation { editMode = editing ? .inactive : .active } }
                Button { model.nav.open(.settings(nil)) } label: { Label("설정", systemImage: "gearshape") }
            }
        }
    }

    private var destinations: [Destination] {
        let all = allDestinations
        return all
    }

    private var allDestinations: [Destination] {
        let m = model
        return [
            Destination(tab: .timetable, route: .timetable),
            Destination(tab: .academic, route: .academic),
            Destination(tab: .meal, route: .meal(nil)),
            Destination(tab: .board, route: .board),
            Destination(tab: .apply, route: .apply),
            Destination(tab: .classroom, route: .classroom),
            Destination(tab: .alim, badge: m.alimUnread, route: .alim),
            Destination(tab: .weather, route: .weather(until: nil)),
        ]
    }
}
