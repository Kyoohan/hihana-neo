import SwiftUI
import PhotosUI
import ActivityKit

extension SettingsPage {
    var title: String {
        switch self {
        case .account: "계정·학년"
        case .display: "화면"
        case .widget: "위젯"
        case .notify: "알림"
        case .board: "게시판"
        case .classroom: "클래스룸"
        case .meal: "급식"
        case .info: "정보"
        }
    }

    var symbol: String {
        switch self {
        case .account: "person.crop.circle"
        case .display: "sun.max"
        case .widget: "widget.small"
        case .notify: "bell.badge"
        case .board: AppTab.board.symbol
        case .classroom: AppTab.classroom.symbol
        case .meal: AppTab.meal.symbol
        case .info: "info.circle"
        }
    }

    /// 목록 아이콘의 채운 기호와 색.
    var tileSymbol: String {
        switch self {
        case .account: "person.crop.circle.fill"
        case .display: "sun.max.fill"
        case .widget: "widget.small"
        case .notify: "bell.badge.fill"
        case .board: AppTab.board.tileSymbol
        case .classroom: AppTab.classroom.tileSymbol
        case .meal: AppTab.meal.tileSymbol
        case .info: "info.circle.fill"
        }
    }

    var tint: Color {
        switch self {
        case .account, .display: .blue
        case .widget: .purple
        case .notify: .red
        case .board: AppTab.board.tint
        case .classroom: AppTab.classroom.tint
        case .meal: AppTab.meal.tint
        case .info: .gray
        }
    }

    /// 세부 화면 맨 위 카드의 한두 줄 설명 — iOS 27 설정의 하위 화면처럼.
    var blurb: String {
        switch self {
        case .account: "하이하나 계정과 학년을 정합니다. 계정을 연결하면 면학 위치·시간표·학사일정을 자동으로 가져옵니다."
        case .display: "앱의 모양(라이트·다크), 강조 색, 배경과 유리 효과를 정합니다."
        case .widget: "홈 화면·잠금 화면 위젯의 테마, 색, 투명도를 정합니다."
        case .notify: isPad ? "데일리 브리핑과 게시판 새 글 알림을 정합니다." : "실시간 일정, 데일리 브리핑, 게시판 새 글 알림을 정합니다."
        case .board: "게시판 AI 요약과 AI 데일리 브리핑을 켜고 끕니다."
        case .classroom: "학교 Google 계정을 연결해 클래스룸 공지와 과제를 받아 옵니다."
        case .meal: "알레르기가 있는 재료를 고르면 그 재료가 든 메뉴를 급식 화면에 표시합니다."
        case .info: ""
        }
    }
}

let appVersion = (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? ""

/// 설정 첫 화면 — iOS 설정 앱처럼 맨 위 계정 줄은 크게, 나머지는 아이콘과 제목만(값이 있으면 오른쪽에 회색).
struct SettingsRootView: View {
    @Environment(AppModel.self) private var model
    private var prefs = Prefs.shared

    var body: some View {
        List {
            Section {
                RouteLink(Route.settings(.account)) {
                    HStack(spacing: 14) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 54))
                            .foregroundStyle(.white, Color.gray.gradient)
                        VStack(alignment: .leading, spacing: 3) {
                            Text("하이하나 계정").font(.title3.bold())
                            Text(model.hasCredentials ? "\(Credentials.memId ?? "연결됨") · \(prefs.studentGrade)학년" : "로그인이 필요합니다 · \(prefs.studentGrade)학년")
                                .font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            Section {
                row(.display)
                row(.widget)
            }
            Section {
                row(.notify)
                row(.board)
                row(.classroom, value: !model.bridge.connected ? "연결 안 됨" : model.classroomStore.snapshot?.stale == true ? "동기화 멈춤" : "연결됨")
                row(.meal, value: prefs.allergyCodes.isEmpty ? nil : "알레르기 \(prefs.allergyCodes.count)개")
            }
            Section {
                RouteLink(Route.about) {
                    HStack {
                        Label { Text("HiHana Evo 정보") } icon: { IconTile(SettingsPage.info.tileSymbol, SettingsPage.info.tint) }
                        Spacer()
                        if WhatsNew.shared.pending || AppUpdater.shared.available != nil { NewBadge() } else { Text(appVersion).foregroundStyle(.secondary) }
                    }
                }
            }
        }
        .onDisappear { if WhatsNew.shared.pending { WhatsNew.shared.markSeen() } }
    }

    private func row(_ page: SettingsPage, value: String? = nil) -> some View {
        RouteLink(Route.settings(page)) {
            HStack {
                Label { Text(page.title) } icon: { IconTile(page.tileSymbol, page.tint) }
                Spacer()
                if let value { Text(value).foregroundStyle(.secondary).lineLimit(1) }
            }
        }
    }
}

/// 빨간 알약 숫자·글자 — 설정 앱의 '업데이트 있음' 표시처럼.
struct NewBadge: View {
    var text = "N"
    var body: some View {
        Text(text).font(.subheadline).foregroundStyle(.white)
            .padding(.horizontal, 7).padding(.vertical, 1)
            .frame(minWidth: 22)
            .background(.red, in: Capsule())
    }
}

/// 설정 세부 화면 맨 위 카드 — 큰 아이콘, 제목, 설명(iOS 27 설정의 하위 화면과 같은 모양).
struct SettingsHeaderCard: View {
    let symbol: String
    let tint: Color
    let title: String
    let blurb: String
    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                IconTile(symbol, tint, size: 60)
                Text(title).font(.title2.bold())
                if !blurb.isEmpty { Text(blurb).font(.subheadline).foregroundStyle(.secondary) }
            }
            .padding(.vertical, 8)
            .materialRow()
        }
    }
}

/// 설정의 세부 화면.
struct SettingsPageView: View {
    let page: SettingsPage
    @Environment(AppModel.self) private var model
    @Bindable private var prefs = Prefs.shared

    init(page: SettingsPage) { self.page = page }

    var body: some View {
        Form {
            if !page.blurb.isEmpty { SettingsHeaderCard(symbol: page.tileSymbol, tint: page.tint, title: page.title, blurb: page.blurb) }
            switch page {
            case .account: account
            case .display: display
            case .info: EmptyView()
            case .widget: WidgetSettingsSection()
            case .notify: notify
            case .board: board
            case .classroom: ClassroomSettings()
            case .meal: meal
            }
        }
    }

    // MARK: 계정·학년

    @ViewBuilder private var account: some View {
        Section("학사") {
            Button { model.nav.present(AccountDialog()) } label: {
                LabeledContent("학사시스템 연동", value: model.hasCredentials ? Credentials.memId ?? "연결됨" : "로그인 필요")
            }
            .foregroundStyle(.primary)
            .materialRow()
        }
        Section {
            Picker("학년", selection: Binding(get: { prefs.studentGrade }, set: { g in
                prefs.studentGrade = g
                Task { await model.ensureBrief() }
            })) {
                ForEach(1...3, id: \.self) { Text("\($0)학년").tag($0) }
            }
            .pickerStyle(.segmented)
            .listRowBackground(Color.clear)
        } header: { Text("학년") } footer: { Text("면학감독·학사일정·게시판 대상 학년을 이 학년에 맞춥니다.") }
    }

    // MARK: 화면

    @ViewBuilder private var display: some View {
        Section("모양") {
            AppearanceRows()
        }
        Section("강조 색") {
            AccentPickerRow().materialRow()
        }
        Section {
            BackgroundRows()
        } header: { Text("배경") } footer: {
            Text("단색은 화면 위쪽만 그 색으로 물듭니다.")
        }
        Section {
            Toggle("성능 모드", isOn: $prefs.performanceMode).materialRow()
            if !prefs.performanceMode {
                VStack(alignment: .leading) {
                    LabeledContent("유리 불투명도", value: "\(Int((prefs.frostOpacity * 100).rounded()))%")
                    Slider(value: $prefs.frostOpacity, in: 0.2...0.9)
                }
                .materialRow()
                if prefs.frostOpacity != 0.5 { Button("기본값으로") { prefs.frostOpacity = 0.5 }.materialRow() }
            }
        } header: { Text("성능") } footer: {
            Text("성능 모드는 카드의 흐림과 유리 효과를 끄고 단색으로 그립니다. 불투명도는 카드가 뒤 배경을 비치는 정도입니다.")
        }
    }

    // MARK: 알림

    @ViewBuilder private var notify: some View {
        // 아이패드도 켤 수 있게 — 시스템이 실시간 활동을 막아 두었으면(지원하지 않는 기기·설정에서 끔) 아래에 알려 줍니다.
        Section {
            Toggle("실시간 활동에 지금 일정 표시", isOn: Binding(get: { LiveSchedule.shared.enabled }, set: { LiveSchedule.shared.setEnabled($0, model: model) }))
                .materialRow()
        } header: { Text("실시간 일정") } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text((isPad ? "잠금 화면에" : "잠금 화면과 Dynamic Island에")
                     + " 평일은 8시(등교 및 조회)부터 2타임까지, 주말은 12시(점심시간)부터 4타임까지 하루 일과와 남은 시간을 보여 줍니다. 심야면학을 신청했으면 심면까지.")
                if #available(iOS 26.0, *) {
                    Text("하루가 시작되는 시각에 앱을 열지 않아도 저절로 뜹니다. 사흘 치까지 미리 맡겨 두므로, 사흘 넘게 앱을 열지 않으면 한 번 열어 주세요.")
                }
                if !ActivityAuthorizationInfo().areActivitiesEnabled {
                    Text("지금 이 기기에선 실시간 활동이 꺼져 있습니다 — 설정 → HiHana Evo → 실시간 현황을 켜 주세요. 기기가 지원하지 않으면 켜도 보이지 않습니다.")
                        .foregroundStyle(.orange)
                }
            }
        }
        Section {
            Toggle("데일리 브리핑 알림", isOn: Binding(get: { model.brief.notifyEnabled }, set: { model.brief.notifyEnabled = $0; BackgroundWork.scheduleMorningBrief() }))
                .materialRow()
        } header: { Text("데일리 브리핑") } footer: {
            Text("평일 오전 7시, 주말 오전 8시 30분에 오늘 일정과 이번 주에 챙길 것(수행평가·시험·마감)을 알려 줍니다.")
        }
        Section {
            Toggle("승인·반려 알림", isOn: Binding(get: { OutingNotify.enabled }, set: { OutingNotify.enabled = $0 })).materialRow()
        } header: { Text("외출·외박") } footer: {
            Text("신청한 외출·외박이 승인되거나 반려되면 알려 줍니다. iPhone이 정한 때에 백그라운드로 확인해 조금 늦을 수 있고, 앱을 열면 바로 확인합니다.")
        }
        Section("게시판 새 글") {
            ForEach(BoardCategory.allCases, id: \.self) { c in
                Toggle(c.label, isOn: Binding(get: { BoardNotify.enabled.contains(c) }, set: { _ in BoardNotify.toggle(c) })).materialRow()
            }
        }
    }

    // MARK: 게시판

    @ViewBuilder private var board: some View {
        Section {
            Toggle("AI 요약 사용", isOn: Binding(get: { model.summarizer.enabled }, set: { model.summarizer.enabled = $0 })).materialRow()
        } header: { Text("AI 요약") } footer: {
            Text("게시판 목록, 게시글, 새 글 알림에 생성형 AI가 만든 요약을 표시합니다. 끄면 게시글을 요약 서버로 보내지 않습니다.")
        }
        Section {
            Toggle("AI 데일리 브리핑", isOn: Binding(get: { model.brief.aiEnabled }, set: { model.brief.aiEnabled = $0; Task { await model.ensureBrief() } })).materialRow()
        } footer: {
            Text("오늘 수업 과목과 7일 안의 일정 제목·날짜를 요약 서버로 보내 브리핑을 만듭니다. 서버는 받은 일정을 저장하지 않습니다. 끄면 AI 없이 중요한 일정만 추립니다.")
        }
    }

    // MARK: 급식

    @ViewBuilder private var meal: some View {
        Section {
            ForEach(Allergens.legend, id: \.0) { code, name in
                Button {
                    if prefs.allergyCodes.contains(code) { prefs.allergyCodes.remove(code) } else { prefs.allergyCodes.insert(code) }
                    // 위젯의 급식 ⚠ 는 위젯이 그릴 때 계산해 두므로 — 바꾸면 바로 다시 그리게(안 그러면 다음 타임라인까지 예전 표시).
                    WidgetBridge.reload()
                } label: {
                    HStack {
                        Text(name).foregroundStyle(.primary)
                        Spacer()
                        if prefs.allergyCodes.contains(code) { Image(systemName: "checkmark").fontWeight(.semibold).foregroundStyle(.tint) }
                    }
                }
                .materialRow()
            }
        } header: { Text("알레르기") } footer: { Text("고른 재료가 들어간 메뉴를 급식 화면에서 표시합니다.") }
    }
}

// MARK: - 클래스룸

private struct ClassroomSettings: View {
    @Environment(AppModel.self) private var model
    @State private var disconnecting = false

    var body: some View {
        if model.bridge.connected {
            let snap = model.classroomStore.snapshot
            let stale = snap?.stale == true
            Section {
                LabeledContent {
                    Text([model.bridge.email ?? "학교 Google 계정", snap?.syncedLabel()].compactMap { $0 }.joined(separator: "\n"))
                        .multilineTextAlignment(.trailing)
                } label: {
                    Text(stale ? "동기화가 멈췄습니다" : "연결됨").foregroundStyle(stale ? Color.red : Color.primary)
                }
                .materialRow()
                if stale {
                    Button("다시 연결") { ClassroomConnect.begin(model: model) }.materialRow()
                }
            } header: { Text("Google 클래스룸") } footer: {
                Text(stale
                     ? "5분마다 받아야 하는데 \(Int(ClassroomSnapshot.staleAfter / 3600))시간 넘게 새로 오지 않았습니다. 다른 기기에서 연결하면서 이 기기가 목록에서 밀려났거나 트리거가 사라진 경우입니다. 브라우저에서 '허용'을 다시 누르면 이어서 받습니다."
                     : "5분마다 공지·과제를 받아 클래스룸 화면과 홈에, 과제 마감과 공지 속 일정을 학사일정에 보여 줍니다.")
            }
            Section {
                Button(disconnecting ? "연결 해제 중" : "연결 해제", role: .destructive) {
                    guard !disconnecting else { return }
                    disconnecting = true
                    Task { await model.bridge.disconnect(); disconnecting = false }
                }
                .disabled(disconnecting)
                .materialRow()
            } footer: {
                Text("이 기기의 클래스룸 내용과 중계 서버의 사본을 지웁니다. Google 쪽 권한은 Google 계정 › 보안 › 타사 앱에서 거둘 수 있습니다.")
            }
        } else {
            Section {
                Button(model.bridge.pending ? "연결 마저 하기" : "학교 계정으로 연결") { ClassroomConnect.begin(model: model) }.materialRow()
            } header: { Text("Google 클래스룸") } footer: {
                Text("브라우저에서 학교 Google 계정으로 '허용'을 한 번 누르면 클래스룸 공지·과제를 읽어 옵니다(읽기만). 내용은 암호화되어 오고 이 기기에만 저장됩니다.")
            }
        }
    }
}

// MARK: - 모양

/// 모양 — iOS 설정 '디스플레이 및 밝기'처럼 라이트·다크 미리보기 둘과 '자동'(시스템 따라가기).
struct AppearanceRows: View {
    @Bindable private var prefs = Prefs.shared
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let dark = prefs.appTheme == .dark || (prefs.appTheme == .system && scheme == .dark)
        HStack(spacing: 0) {
            option(dark: false, selected: !dark)
            option(dark: true, selected: dark)
        }
        .padding(.vertical, 10)
        .materialRow()
        Toggle("자동", isOn: Binding(get: { prefs.appTheme == .system }, set: { prefs.appTheme = $0 ? .system : (dark ? .dark : .light) }))
            .materialRow()
    }

    private func option(dark: Bool, selected: Bool) -> some View {
        Button { prefs.appTheme = dark ? .dark : .light } label: {
            VStack(spacing: 10) {
                AppearanceThumb(dark: dark).frame(width: 76, height: 156)
                Text(dark ? "다크" : "라이트").font(.subheadline)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(selected ? AnyShapeStyle(.tint) : AnyShapeStyle(Color.secondary.opacity(0.6)))
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(dark ? "다크" : "라이트")
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 모양 미리보기 — 작은 화면에 시각과 카드 몇 장.
private struct AppearanceThumb: View {
    let dark: Bool
    var body: some View {
        let bg = Color(argb: dark ? 0xFF000000 : 0xFFF2F2F7)
        let card = Color(argb: dark ? 0xFF1C1C1E : 0xFFFFFFFF)
        let ink = Color(argb: dark ? 0xFFFFFFFF : 0xFF000000)
        RoundedRectangle(cornerRadius: 14, style: .continuous).fill(bg)
            .overlay(alignment: .top) {
                VStack(spacing: 6) {
                    Text("9:41").font(.system(size: 9, weight: .semibold)).foregroundStyle(ink)
                    RoundedRectangle(cornerRadius: 3).fill(ink.opacity(0.8)).frame(width: 30, height: 6).frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(0..<3, id: \.self) { i in
                        RoundedRectangle(cornerRadius: 6, style: .continuous).fill(card).frame(height: i == 0 ? 34 : 24)
                    }
                }
                .padding(.horizontal, 8).padding(.top, 8)
            }
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Color.primary.opacity(0.15), lineWidth: 1))
    }
}

// MARK: - 강조 색

/// 강조 색 — 자동 + 프리셋 8 + 직접 고르기(시스템 색 선택기).
struct AccentPickerRow: View {
    private var prefs = Prefs.shared

    var body: some View {
        let selected = prefs.accentARGB
        let isCustom = selected != 0 && !accentPresets.contains(selected)
        VStack(alignment: .leading, spacing: 12) {
            let columns = Array(repeating: GridItem(.flexible(), spacing: 0), count: 5)
            LazyVGrid(columns: columns, spacing: 8) {
                Swatch(color: systemBlue, selected: selected == 0) { prefs.accentARGB = 0 }
                ForEach(accentPresets, id: \.self) { argb in
                    Swatch(color: Color(argb: argb), selected: selected == argb) { prefs.accentARGB = argb }
                }
                ColorPicker("직접 선택", selection: Binding(
                    get: { Color(argb: selected == 0 ? 0xFF007AFF : selected) },
                    set: { prefs.accentARGB = UIColor($0).argb | 0xFF000000 }), supportsOpacity: false)
                    .labelsHidden()
                    .frame(width: 44, height: 44)
            }
            Text(selected == 0 ? "자동 (시스템 파란색)" : isCustom ? String(format: "직접 선택 · #%06X", selected & 0xFFFFFF) : "프리셋")
                .font(.footnote).foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}

private let systemBlue = Color(argb: 0xFF007AFF)

/// 키트 Color Pickers 의 색 원 — 고른 색은 같은 색 고리로 두릅니다.
private struct Swatch: View {
    let color: Color
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Circle().fill(color).frame(width: selected ? 26 : 32, height: selected ? 26 : 32)
                .frame(width: 36, height: 36)
                .overlay(Circle().strokeBorder(selected ? color : .clear, lineWidth: 2.5))
                .frame(width: 44, height: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 배경 사진

/// 배경 이미지 고르기·되돌리기 — 사진 보관함에서 한 장 고른 뒤 자르기 화면에서 위치·크기·밝기·채도를 맞춰 저장합니다.
/// 배경 — 그라데이션(기본) · 단색 · 사진 중 하나. 단색은 색 원, 사진은 밝기·채도를 이어서 보입니다.
struct BackgroundRows: View {
    @State private var item: PhotosPickerItem?
    @State private var picking = false
    @State private var cropping: UIImage?
    @Bindable private var prefs = Prefs.shared
    private var background = PageBackground.shared

    var body: some View {
        ForEach(Prefs.BackgroundStyle.allCases, id: \.self) { style in
            Button { choose(style) } label: {
                HStack {
                    Text(style.label)
                    Spacer()
                    if prefs.backgroundStyle == style { Image(systemName: "checkmark").fontWeight(.semibold).foregroundStyle(.tint) }
                }
            }
            .foregroundStyle(Color.primary)
            .materialRow()
        }
        .photosPicker(isPresented: $picking, selection: $item, matching: .images)
        .onChange(of: item) { _, it in
            guard let it else { return }
            Task {
                if let data = try? await it.loadTransferable(type: Data.self), let img = UIImage(data: data) { cropping = img.normalizedUp() }
                item = nil
            }
        }
        .fullScreenCover(item: Binding(get: { cropping.map(IdentifiedImage.init) }, set: { cropping = $0?.image })) { wrapped in
            BackgroundCropView(image: wrapped.image) { cropping = nil }
        }
        if prefs.backgroundStyle == .solid {
            SolidColorRow().materialRow()
        }
        if prefs.backgroundStyle == .photo && background.hasPhoto {
            Button("다른 사진 고르기") { picking = true }.materialRow()
            adjust("밝기", signed(prefs.backgroundBrightness), Binding(get: { prefs.backgroundBrightness }, set: { prefs.backgroundBrightness = ($0 * 50).rounded() / 50 }), -0.4...0.4)
            adjust("채도", signed(prefs.backgroundSaturation - 1), Binding(get: { prefs.backgroundSaturation }, set: { prefs.backgroundSaturation = ($0 * 50).rounded() / 50 }), 0...1.6)
            if !prefs.backgroundAdjustIsDefault {
                Button("밝기·채도 기본값으로") {
                    prefs.backgroundBrightness = Prefs.defaultBrightness
                    prefs.backgroundSaturation = Prefs.defaultSaturation
                    background.reload()
                }
                .materialRow()
            }
        }
    }

    /// '사진'은 고른 사진이 없으면 사진 고르기부터 — 자르고 '적용'하면 사진 배경이 됩니다.
    private func choose(_ style: Prefs.BackgroundStyle) {
        if style == .photo && !background.hasPhoto { picking = true } else { prefs.backgroundStyle = style }
    }

    private func signed(_ v: Double) -> String { let p = Int((v * 100).rounded()); return p > 0 ? "+\(p)%" : "\(p)%" }

    private func adjust(_ label: String, _ text: String, _ value: Binding<Double>, _ range: ClosedRange<Double>) -> some View {
        VStack(alignment: .leading) {
            LabeledContent(label, value: text)
            Slider(value: value, in: range) { editing in if !editing { background.reload() } }
        }
        .materialRow()
    }
}

/// 단색 배경의 색 — 강조 색 따르기 + 프리셋 + 직접 고르기.
private struct SolidColorRow: View {
    @Bindable private var prefs = Prefs.shared
    @Environment(\.palette) private var palette

    var body: some View {
        let selected = prefs.backgroundSolidARGB
        let isCustom = selected != 0 && !accentPresets.contains(selected)
        VStack(alignment: .leading, spacing: 12) {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 5), spacing: 8) {
                Swatch(color: palette.primary, selected: selected == 0) { prefs.backgroundSolidARGB = 0 }
                ForEach(accentPresets, id: \.self) { argb in
                    Swatch(color: Color(argb: argb), selected: selected == argb) { prefs.backgroundSolidARGB = argb }
                }
                ColorPicker("직접 선택", selection: Binding(
                    get: { selected == 0 ? palette.primary : Color(argb: selected) },
                    set: { prefs.backgroundSolidARGB = UIColor($0).argb | 0xFF000000 }), supportsOpacity: false)
                    .labelsHidden()
                    .frame(width: 44, height: 44)
            }
            Text(selected == 0 ? "강조 색과 같게" : isCustom ? String(format: "직접 선택 · #%06X", selected & 0xFFFFFF) : "프리셋")
                .font(.footnote).foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}

struct IdentifiedImage: Identifiable { let image: UIImage; var id: ObjectIdentifier { ObjectIdentifier(image) } }

extension UIImage {
    /// 사진의 회전 정보를 픽셀에 반영합니다 — 세로로 찍은 사진이 눕지 않게.
    func normalizedUp() -> UIImage {
        guard imageOrientation != .up else { return self }
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = scale
        return UIGraphicsImageRenderer(size: size, format: fmt).image { _ in draw(in: CGRect(origin: .zero, size: size)) }
    }
}

/// 배경 사진 자르기 — 화면 비율 그대로의 틀 안에서 끌어서 위치를, 두 손가락으로 크기를 맞춥니다. 밝기·채도도 여기서부터.
struct BackgroundCropView: View {
    let image: UIImage
    let onDone: () -> Void
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var brightness = Prefs.defaultBrightness
    @State private var saturation = Prefs.defaultSaturation

    var body: some View {
        GeometryReader { g in
            let frame = g.size
            let base = max(frame.width / image.size.width, frame.height / image.size.height)
            let s = base * scale
            ZStack {
                Color.black
                Image(uiImage: image).resizable()
                    .frame(width: image.size.width * s, height: image.size.height * s)
                    .brightness(brightness).saturation(saturation)
                    .offset(offset)
                    .gesture(SimultaneousGesture(
                        DragGesture().onChanged { d in offset = clamp(CGSize(width: lastOffset.width + d.translation.width, height: lastOffset.height + d.translation.height), s, frame) }
                            .onEnded { _ in lastOffset = offset },
                        MagnifyGesture().onChanged { v in scale = (lastScale * v.magnification).clamped(1, 6); offset = clamp(offset, base * scale, frame) }
                            .onEnded { _ in lastScale = scale; lastOffset = offset }))
                VStack {
                    Text("끌어서 위치를, 두 손가락으로 크기를 맞추세요").font(.subheadline).foregroundStyle(.white)
                        .padding(.horizontal, 16).padding(.vertical, 8).background(.ultraThinMaterial, in: Capsule())
                        .padding(.top, g.safeAreaInsets.top + 20)
                    Spacer()
                    VStack(spacing: 4) {
                        adjust("밝기", $brightness, -0.4...0.4, brightness)
                        adjust("채도", $saturation, 0...1.6, saturation - 1)
                    }
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                    HStack(spacing: 12) {
                        Button { onDone() } label: { Text("취소").frame(maxWidth: .infinity) }
                            .buttonStyle(.bordered).controlSize(.large)
                        Button {
                            let cropped = crop(scale: s, frame: frame)
                            Prefs.shared.backgroundBrightness = brightness
                            Prefs.shared.backgroundSaturation = saturation
                            if PageBackground.shared.save(cropped) { Prefs.shared.backgroundStyle = .photo }
                            onDone()
                        } label: { Text("적용").frame(maxWidth: .infinity) }
                            .buttonStyle(.borderedProminent).controlSize(.large)
                    }
                    .padding(.top, 12)
                }
                .padding(.horizontal, 20).padding(.bottom, g.safeAreaInsets.bottom + 20)
            }
            .ignoresSafeArea()
        }
        .environment(\.colorScheme, .dark)
    }

    private func adjust(_ label: String, _ value: Binding<Double>, _ range: ClosedRange<Double>, _ shown: Double) -> some View {
        HStack {
            Text(label).font(.subheadline).frame(width: 44, alignment: .leading)
            Slider(value: Binding(get: { value.wrappedValue }, set: { value.wrappedValue = ($0 * 50).rounded() / 50 }), in: range)
            let p = Int((shown * 100).rounded())
            Text(p > 0 ? "+\(p)%" : "\(p)%").font(.subheadline.monospacedDigit()).frame(width: 52, alignment: .trailing)
        }
        .foregroundStyle(.white)
    }

    private func clamp(_ o: CGSize, _ s: CGFloat, _ frame: CGSize) -> CGSize {
        let maxX = max(0, (image.size.width * s - frame.width) / 2), maxY = max(0, (image.size.height * s - frame.height) / 2)
        return CGSize(width: o.width.clamped(-maxX, maxX), height: o.height.clamped(-maxY, maxY))
    }

    /// 틀 안의 영역만 잘라 돌려줍니다(원본 해상도).
    private func crop(scale s: CGFloat, frame: CGSize) -> UIImage {
        let w = frame.width / s, h = frame.height / s
        let cx = image.size.width / 2 - offset.width / s, cy = image.size.height / 2 - offset.height / s
        let rect = CGRect(x: (cx - w / 2) * image.scale, y: (cy - h / 2) * image.scale, width: w * image.scale, height: h * image.scale).integral
        guard let cg = image.cgImage?.cropping(to: rect) else { return image }
        return UIImage(cgImage: cg)
    }
}

// MARK: - 위젯

private struct WidgetSettingsSection: View {
    @Bindable private var prefs = Prefs.shared

    var body: some View {
        Section("테마") {
            Picker("테마", selection: Binding(get: { prefs.widgetTheme }, set: { prefs.widgetTheme = $0; WidgetBridge.reload() })) {
                ForEach(Prefs.Theme.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.inline).labelsHidden().materialRow()
        }
        Section("강조 색") {
            Picker("강조 색", selection: Binding(get: { prefs.widgetAccent }, set: { prefs.widgetAccent = $0; WidgetBridge.reload() })) {
                ForEach(Prefs.WidgetAccent.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.inline).labelsHidden().materialRow()
        }
        if prefs.widgetAccent == .kind {
            Section {
                ForEach(Accent.ordered, id: \.self) { a in
                    ColorPicker(a.label, selection: Binding(
                        get: { Color(argb: prefs.widgetKindColors[a.storageName] ?? a.defaultARGB) },
                        set: { c in
                            let argb = UIColor(c).argb | 0xFF000000
                            if argb == a.defaultARGB { prefs.widgetKindColors.removeValue(forKey: a.storageName) } else { prefs.widgetKindColors[a.storageName] = argb }
                            WidgetBridge.reload()
                        }), supportsOpacity: false)
                    .materialRow()
                }
                if !prefs.widgetKindColors.isEmpty {
                    Button("기본 색으로 되돌리기") { prefs.widgetKindColors = [:]; WidgetBridge.reload() }.materialRow()
                }
            } header: { Text("종류별 색") }
        }
        Section("배경") {
            VStack(alignment: .leading) {
                LabeledContent("불투명도", value: "\(prefs.widgetOpacity)%")
                Slider(value: Binding(get: { Double(prefs.widgetOpacity) }, set: { prefs.widgetOpacity = Int(($0 / 5).rounded() * 5) }), in: 10...100) { editing in
                    if !editing { WidgetBridge.reload() }
                }
            }
            .materialRow()
        }
    }
}

// MARK: - 계정

/// 학사시스템 연동 — 아이디·비밀번호를 저장하고 바로 로그인을 확인해 결과를 시트 안에서 보여 줍니다.
struct AccountDialog: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var palette
    @Environment(\.dismissDialog) private var dismiss
    @State private var memID = Credentials.memId ?? ""
    @State private var memPwd = ""
    @State private var checking = false
    @State private var result: Bool?
    @State private var failure: String?
    @State private var confirmSignOut = false
    @FocusState private var focus: Field?

    private enum Field { case id, password }

    private var canSave: Bool {
        !checking && !memID.trimmingCharacters(in: .whitespaces).isEmpty && !memPwd.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                // 건강 앱의 '건강 세부사항 설정'처럼 — 큰 기호·제목·설명, 이름 … 값 줄, 아래 넓은 단추.
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        Image(systemName: "graduationcap.circle.fill")
                            .font(.system(size: 76))
                            .foregroundStyle(.white, palette.primary.gradient)
                            .frame(maxWidth: .infinity)
                            .padding(.bottom, 8)
                            .accessibilityHidden(true)
                        Text("학사시스템 연동").font(.title2.bold())
                        Text("하이하나(hi.hana.hs.kr) 계정으로 로그인하면 면학 위치·시간표·학사일정을 자동으로 가져옵니다. 비밀번호는 이 기기의 키체인에 암호화되어 저장됩니다.")
                            .foregroundStyle(.secondary)
                    }
                    .listRowBackground(Color.clear)
                }
                Section {
                    TextField("아이디", text: $memID)
                        .textContentType(.username).keyboardType(.asciiCapable)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .focused($focus, equals: .id).submitLabel(.next).onSubmit { focus = .password }
                    SecureField("비밀번호", text: $memPwd)
                        .textContentType(.password)
                        .focused($focus, equals: .password).submitLabel(.done).onSubmit(save)
                }
                if checking || result != nil {
                    Section {
                        if checking {
                            HStack(spacing: 10) { ProgressView(); Text("로그인 확인 중").foregroundStyle(.secondary) }
                        } else if let ok = result {
                            Label(ok ? "로그인 성공" : "로그인 실패", systemImage: ok ? "checkmark.circle.fill" : "xmark.octagon.fill")
                                .foregroundStyle(ok ? Color.green : Color.red)
                            if !ok, let failure { Text(failure).font(.footnote).foregroundStyle(.red) }
                        }
                    }
                }
                if model.hasCredentials {
                    Section {
                        Button("연결 해제", role: .destructive) { confirmSignOut = true }
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                WideButton(checking ? "확인 중" : "로그인", action: save)
                    .disabled(!canSave)
                .padding(.horizontal, 24).padding(.bottom, 8)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { CloseButton { dismiss() } }
            }
            .confirmationDialog("하이하나 계정 연결을 해제할까요?", isPresented: $confirmSignOut, titleVisibility: .visible) {
                Button("연결 해제", role: .destructive) { model.signOut(); dismiss() }
            } message: {
                Text("저장한 아이디·비밀번호와 받아 둔 학사 정보가 이 기기에서 지워집니다.")
            }
        }
        .presentationDetents([.large])
        .onAppear { focus = memID.isEmpty ? .id : .password }
    }

    private func save() {
        let id = memID.trimmingCharacters(in: .whitespaces), pwd = memPwd.trimmingCharacters(in: .whitespaces)
        guard !id.isEmpty, !pwd.isEmpty, !checking else { return }
        checking = true
        result = nil
        failure = nil
        Task {
            do {
                try await model.signIn(id: id, password: pwd)
                result = true
            } catch let e as PortalError {
                result = false
                failure = switch e {
                case .loginFailed(let m): m
                case .missingCredentials: "아이디/비밀번호를 먼저 등록해 주세요"
                case .tokenNotFound: "로그인 페이지를 불러오지 못했습니다"
                case .unexpected: "학사시스템 응답을 확인하지 못했습니다"
                default: e.localizedDescription
                }
            } catch {
                result = false
                failure = "네트워크 오류로 로그인을 확인하지 못했습니다"
            }
            checking = false
        }
    }
}
