import SwiftUI

/// 알리미 — 메일 목록처럼 안 읽은 줄은 파란 점과 굵은 글자. 누르면 읽음 처리 후 포털 알리미를 앱 안 웹으로,
/// 열지 않고 읽음으로 하려면 왼쪽으로 밉니다. '모두 읽음'은 오른쪽 위.
struct AlimView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.listPane) private var listPane

    var body: some View {
        List {
            if let error = model.alimError {
                AcademicErrorBlock(error: error, onRetry: { Task { await model.loadAlim(force: true) } }, onOpenAccount: { model.nav.present(AccountDialog()) })
                    .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            } else if model.alimLoading && model.alims.isEmpty {
                LoadingBlock().listRowBackground(Color.clear)
            } else if model.alims.isEmpty {
                ContentUnavailableView("알리미가 없습니다", systemImage: "tray").listRowBackground(Color.clear)
            } else {
                ForEach(model.alims) { alim in
                    AlimRow(alim: alim) { model.openAlim(alim, select: listPane) }
                        .swipeActions(edge: .leading) {
                            if !alim.read {
                                Button { model.markAlimRead(alim) } label: { Label("읽음", systemImage: "envelope.open") }.tint(.blue)
                            }
                        }
                        .plainRow(selected: model.nav.isSelected(.web(url: alimURL(alim.portalIdx), title: "알리미", resync: false)))
                }
            }
        }
        .listStyle(.plain)
        .refreshable { await model.loadAlim(force: true) }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("모두 읽음") { model.markAllAlimRead() }.disabled(!model.alims.contains { !$0.read })
            }
        }
        .task {
            if model.hasCredentials { await model.loadAlim(force: true) } else if !model.isDemo { model.alimError = academicNeedsLogin }
        }
    }
}

private struct AlimRow: View {
    let alim: HanaAlim
    let onOpen: () -> Void

    var body: some View {
        let unread = !alim.read
        Button(action: onOpen) {
            HStack(alignment: .top, spacing: 8) {
                Circle().fill(unread ? AnyShapeStyle(.tint) : AnyShapeStyle(.clear)).frame(width: 10, height: 10).padding(.top, 6)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(alim.writer ?? "알리미").font(.headline).foregroundStyle(Color.primary).lineLimit(1)
                        Spacer(minLength: 6)
                        if let when = mailDate(alim.date) { Text(when).font(.subheadline).foregroundStyle(Color.secondary) }
                        Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                    Text(alim.content.isEmpty ? "(내용 없음)" : alim.content)
                        .font(.subheadline.weight(unread ? .semibold : .regular))
                        .foregroundStyle(unread ? Color.primary : Color.secondary)
                        .lineLimit(3)
                        .multilineTextAlignment(.leading)
                }
            }
            .padding(.vertical, 2)
            .contentShape(Rectangle())
        }
    }
}
