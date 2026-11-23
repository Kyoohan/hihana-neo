import SwiftUI
import UserNotifications

/*
 * 첫 실행 안내 — 오프닝 뒤 순정 앱의 시작 화면(건강 '건강 시작하기', 미리 알림 '시작하기')처럼 한 장씩:
 * 시작 → 주요 기능 → 클래스룸 과제·할 일 → 내 일정 → 날씨 → 위젯 → 모양 → 계정 정보 보호 → 알림 → 로그인 → 클래스룸 연결
 * → (SideStore 가 있으면) 업데이트 → 준비 완료.
 * 새 기능 장의 그림은 실제 화면을 줄여 따라 그리고, 장에 들어올 때 조각이 차례로 나타납니다(동작 줄이기면 바로).
 * 장마다 위에 큰 그림, 굵은 제목과 설명, 아래에 넓은 '계속' 단추(미룰 수 있는 장은 그 아래 '나중에'). 제목은 명사형, 설명은 문장.
 */

enum TourStore {
    /// 이 버전의 투어 — 기록이 이보다 작으면 한 번 보여 줍니다.
    // 140 → 144: 클래스룸 과제·할 일, 내 일정, 날씨, SideStore 업데이트 장을 더했습니다.
    static let version = 144
    private static let key = "tour.version"
    static var pending: Bool { SharedContainer.defaults.integer(forKey: key) < version }
    static func markSeen() { SharedContainer.defaults.set(version, forKey: key) }
}

private enum TourPage: Hashable { case welcome, features, todo, agenda, weather, widget, display, privacy, permissions, login, classroom, update, done }

struct OnboardingView: View {
    let onFinish: () -> Void
    @Environment(AppModel.self) private var model
    @State private var opening = true
    @State private var page: TourPage = .welcome
    @State private var pages: [TourPage] = [.welcome, .features, .todo, .agenda, .weather, .widget, .display, .privacy, .permissions, .login, .classroom, .update, .done]
    @State private var showingPrivacy = false
    @State private var login = LoginState()
    @State private var notifyGranted = false

    var body: some View {
        ZStack {
            Color(.secondarySystemBackground).ignoresSafeArea()
            VStack(spacing: 0) {
                TabView(selection: $page) {
                    ForEach(pages, id: \.self) { p in
                        content(p).tag(p)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                footer
            }
            .opacity(opening ? 0 : 1)
            if opening {
                ZStack {
                    PageBackdrop()
                    EvoOpening { withAnimation(.easeOut(duration: 0.35)) { opening = false } }
                }
                .transition(.opacity)
            }
        }
        .sheet(isPresented: $showingPrivacy) { NavigationStack { PrivacyView().toolbar { ToolbarItem(placement: .cancellationAction) { CloseButton { showingPrivacy = false } } } } }
        .task {
            // 알림을 이미 허용했으면 알림 장은 뺍니다.
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            if settings.authorizationStatus == .authorized { pages.removeAll { $0 == .permissions } }
            // SideStore 로 깔지 않았으면(시뮬레이터·Xcode 설치) 업데이트 장은 뺍니다.
            if !AppUpdater.hasSideStore && !CommandLine.arguments.contains("-tour") { pages.removeAll { $0 == .update } }
        }
    }

    private var index: Int { pages.firstIndex(of: page) ?? 0 }

    /// 아래 단추 — 넓은 '계속'(장마다 이름이 다름)과, 미룰 수 있는 장이면 그 아래 글자 단추 '나중에'.
    private var footer: some View {
        VStack(spacing: 6) {
            WideButton(primaryLabel, busy: page == .login && login.busy, action: primary)
                .disabled(page == .login && !model.hasCredentials && !login.canSubmit)
            // 순정 시작 화면처럼 '계속' 아래 글자 단추 — 없을 때도 자리를 둬 '계속'이 들썩이지 않게.
            Button("나중에") { next() }
                .font(.body.weight(.medium))
                .frame(height: 44)
                .opacity(canSkip ? 1 : 0)
                .disabled(!canSkip)
        }
        .padding(.horizontal, 28).padding(.bottom, 8).padding(.top, 8)
        .readableWidth(560)
    }

    private var canSkip: Bool {
        switch page {
        case .permissions: !notifyGranted
        case .login: !model.hasCredentials
        case .classroom: !model.bridge.connected
        case .update: true
        default: false
        }
    }

    private var primaryLabel: String {
        switch page {
        case .permissions: notifyGranted ? "계속" : "알림 허용"
        case .login: model.hasCredentials ? "계속" : "로그인"
        case .classroom: model.bridge.connected ? "계속" : "학교 계정으로 연결"
        case .update: "SideStore에 소스 추가"
        case .done: "시작하기"
        default: "계속"
        }
    }

    private func primary() {
        switch page {
        case .permissions where !notifyGranted:
            Task {
                notifyGranted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])) ?? false
                next()
            }
        case .login where !model.hasCredentials: login.submit(model: model) { next() }
        case .classroom where !model.bridge.connected: ClassroomConnect.begin(model: model)
        case .update:
            // SideStore 가 소스 추가 확인을 띄웁니다 — 돌아오면 다음 장.
            UIApplication.shared.open(AppUpdater.addSourceLink)
            next()
        default: next()
        }
    }

    private func next() {
        if index + 1 < pages.count { withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { page = pages[index + 1] } }
        else { TourStore.markSeen(); onFinish() }
    }

    @ViewBuilder private func content(_ p: TourPage) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                switch p {
                case .welcome: WelcomePage()
                case .features: FeaturesPage()
                // 쪽 넘김(TabView)은 옆 장을 미리 그려 onAppear 가 화면 밖에서 불립니다 — 지금 장인지 넘겨 그때 움직이게.
                case .todo: TodoTourPage(active: page == .todo)
                case .agenda: AgendaTourPage(active: page == .agenda)
                case .weather: WeatherTourPage(active: page == .weather)
                case .widget: WidgetPage()
                case .display: DisplayPage()
                case .privacy: PrivacyPage { showingPrivacy = true }
                case .permissions: PermissionsPage(granted: notifyGranted)
                case .login: LoginPage(state: login)
                case .classroom: ClassroomPage()
                case .update: UpdateTourPage(active: page == .update)
                case .done: DonePage()
                }
            }
            .padding(.horizontal, 32).padding(.top, 40).padding(.bottom, 24)
            .frame(maxWidth: 560, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
        .task { if p == .permissions { notifyGranted = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .authorized } }
    }
}

// MARK: - 조각

/// 장 머리 — 가운데 큰 그림, 굵은 제목, 회색 설명(순정 시작 화면의 글자 크기 그대로).
private struct Head<Art: View>: View {
    let title: String
    var desc: String? = nil
    @ViewBuilder var art: Art
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            art.frame(maxWidth: .infinity).padding(.bottom, 28)
            Text(title).font(.title.bold()).fixedSize(horizontal: false, vertical: true)
            if let desc { Text(desc).font(.title3).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true).padding(.top, 6) }
        }
    }
}

private extension Head where Art == EmptyView {
    init(title: String, desc: String? = nil) { self.init(title: title, desc: desc) { EmptyView() } }
}

/// 큰 기호 그림 — 건강 앱의 하트·자물쇠 그림 자리.
private struct BigSymbol: View {
    let symbol: String
    let color: Color
    var body: some View {
        Image(systemName: symbol).font(.system(size: 84)).foregroundStyle(color.gradient).accessibilityHidden(true)
    }
}

private struct FeatureRow: View {
    let symbol: String
    let color: Color
    let title: String
    var body_: String? = nil
    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: symbol).font(.system(size: 28)).foregroundStyle(color).frame(width: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline)
                if let body_ { Text(body_).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }
            }
        }
    }
}

/// 장 안의 묶음 — 시트 바탕보다 한 단계 밝은 둥근 판(건강 앱 입력 칸처럼).
private struct Card<C: View>: View {
    @ViewBuilder var content: C
    var body: some View {
        VStack(alignment: .leading, spacing: 12) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

// MARK: - 시작

/// 첫 장 — 앱 아이콘 둘레에 기능 기호(건강 앱 첫 장처럼). 기호는 그림 칸 안에만 두어 글자에 닿지 않게.
private struct WelcomePage: View {
    @State private var shown = false
    private let orbit: [(String, Color)] = [
        ("clock.fill", .blue), ("fork.knife", .orange), ("newspaper.fill", Color(argb: 0xFFE0A800)), ("graduationcap.fill", .green),
        ("calendar", .red), ("chair.fill", .indigo), ("moon.zzz.fill", .purple), ("cloud.sun.fill", .cyan), ("tray.fill", .pink), ("list.clipboard.fill", .teal),
    ]
    var body: some View {
        Head(title: "HiHana Evo 시작하기",
             desc: "시간표·급식·게시판·신청을 한곳에서 봅니다. 학교 계정을 연결하면 면학 위치와 학사일정까지 자동으로 가져옵니다.") {
            GeometryReader { g in
                let c = CGPoint(x: g.size.width / 2, y: g.size.height / 2)
                let rx = min(g.size.width / 2 - 22, 150), ry = g.size.height / 2 - 22
                ZStack {
                    ForEach(Array(orbit.enumerated()), id: \.offset) { i, o in
                        let a = Double(i) / Double(orbit.count) * 2 * .pi - .pi / 2
                        Image(systemName: o.0).font(.system(size: 28)).foregroundStyle(o.1)
                            .position(x: c.x + rx * cos(a), y: c.y + ry * sin(a))
                            .opacity(shown ? 1 : 0).scaleEffect(shown ? 1 : 0.6)
                            .animation(.spring(response: 0.6, dampingFraction: 0.8).delay(0.05 * Double(i)), value: shown)
                    }
                    Image("AppLogo").resizable().frame(width: 104, height: 104)
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                        .shadow(color: .black.opacity(0.25), radius: 12, y: 6)
                        .position(c)
                }
            }
            .frame(height: 300)
            .accessibilityHidden(true)
        }
        .onAppear { shown = true }
    }
}

/// 주요 기능 — 미리 알림 '시작하기'처럼 색 기호·굵은 제목·설명 목록.
private struct FeaturesPage: View {
    private let rows: [(String, Color, String, String)] = [
        ("house.fill", .blue, "지금 챙길 것부터 보이는 홈", "지금 있어야 할 곳과 남은 시간, 급식·게시판·클래스룸·오늘 일정이 시간에 맞춰 자리를 바꿉니다."),
        ("clock.fill", .indigo, "하루를 한 줄로 보는 타임라인", "수업·면학·급식과 그 시각에 올라온 소식을 시간순으로 봅니다."),
        ("sparkles", .purple, "데일리 브리핑", "오늘 일정과 이번 주에 챙길 수행평가·시험·마감을 AI가 두세 문장으로 정리합니다."),
        ("list.clipboard.fill", .green, "앱 안에서 하는 신청·내역", "도서관·면학실·심야면학·교과교실·외출외박을 하이하나 웹페이지 없이 신청합니다."),
        ("fork.knife", .orange, "급식과 알레르기", "이번 주 식단과 칼로리·영양성분·원산지를 보고, 내 알레르기 재료가 든 메뉴를 먼저 알려 줍니다."),
        ("newspaper.fill", Color(argb: 0xFFE0A800), "게시판 새 글 알림과 AI 요약", "고른 게시판의 새 글을 알려 주고, 긴 글과 이미지뿐인 가정통신문도 요약합니다."),
        ("graduationcap.fill", .teal, "클래스룸", "공지·과제·자료와 할 일, 제출 여부와 점수를 학교 일정과 한곳에서 봅니다."),
        ("calendar.badge.plus", .pink, "내 일정", "약속이나 마감을 직접 적어 학교 일정과 함께 봅니다."),
    ]
    var body: some View {
        Text("HiHana Evo의 주요 기능").font(.title.bold()).padding(.top, 24)
        VStack(alignment: .leading, spacing: 24) {
            ForEach(rows, id: \.2) { r in FeatureRow(symbol: r.0, color: r.1, title: r.2, body_: r.3) }
        }
        .padding(.top, 32)
    }
}

// MARK: - 위젯·모양

private struct WidgetPage: View {
    var body: some View {
        Head(title: "홈 화면 위젯과 실시간 활동",
             desc: "위젯에 지금 있어야 할 곳과 남은 시간이 보입니다. 식사 시간에는 그 끼니의 메뉴를 보여 주고, 실시간 활동을 켜면 잠금 화면과 Dynamic Island에도 나옵니다.") {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Label("면학 1타임", systemImage: "books.vertical.fill").font(.caption.weight(.semibold)).foregroundStyle(.orange)
                    Text("도서관").font(.title3.bold())
                    Text("2층 B-14").font(.caption).foregroundStyle(.secondary)
                    Spacer(minLength: 6)
                    Text("48:12").font(.title3.weight(.semibold).monospacedDigit()).foregroundStyle(.orange)
                    ProgressView(value: 0.6).tint(.orange)
                }
                .padding(14).frame(width: 150, height: 150, alignment: .topLeading)
                .background(Color(.tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "books.vertical.fill").foregroundStyle(.white).frame(width: 30, height: 30).background(.orange, in: Circle())
                        VStack(alignment: .leading, spacing: 0) {
                            Text("면학 1타임").font(.caption2).foregroundStyle(.white.opacity(0.7))
                            Text("도서관 2층 B-14").font(.caption.bold()).foregroundStyle(.white)
                        }
                    }
                    Text("48:12").font(.title3.weight(.semibold).monospacedDigit()).foregroundStyle(.white)
                    ProgressView(value: 0.6).tint(.orange)
                }
                .padding(14).frame(maxWidth: .infinity, minHeight: 150, alignment: .topLeading)
                .background(Color.black, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            }
            .accessibilityHidden(true)
        }
    }
}

private struct DisplayPage: View {
    var body: some View {
        Head(title: "모양과 배경", desc: "라이트·다크와 강조 색, 배경을 고릅니다. 나중에 설정 › 화면에서도 바꿀 수 있습니다.")
        VStack(spacing: 14) {
            Card { AppearanceRows() }
            Card { AccentPickerRow() }
            Card { BackgroundRows() }
        }
        .padding(.top, 24)
    }
}

// MARK: - 보호·권한

private struct PrivacyPage: View {
    let onMore: () -> Void
    var body: some View {
        Head(title: "계정 정보 보호",
             desc: "하이하나 비밀번호는 이 \(isPad ? "iPad" : "iPhone")의 키체인에 암호화해 저장하고 학교 학사시스템에만 보냅니다. 개발자를 포함해 다른 누구에게도 전달되지 않습니다.") {
            BigSymbol(symbol: "lock.shield.fill", color: .blue)
        }
        Button(action: onMore) {
            Label("더 알아보기…", systemImage: "info.circle.fill").font(.body)
        }
        .padding(.top, 18)
    }
}

private struct PermissionsPage: View {
    let granted: Bool
    var body: some View {
        Head(title: "알림",
             desc: "게시판 새 글, 알리미, 데일리 브리핑을 알림으로 받습니다. 나중에 설정에서도 바꿀 수 있습니다.") {
            BigSymbol(symbol: "bell.badge.fill", color: .red)
        }
        if granted {
            Label("허용됨", systemImage: "checkmark.circle.fill").font(.headline).foregroundStyle(.green).padding(.top, 18)
        }
    }
}

// MARK: - 로그인

@Observable
@MainActor
final class LoginState {
    var id = Credentials.memId ?? ""
    var password = ""
    var busy = false
    var error: String?
    var canSubmit: Bool { !busy && !id.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty }

    func submit(model: AppModel, onSuccess: @escaping () -> Void) {
        guard canSubmit else { return }
        busy = true
        error = nil
        Task {
            do {
                try await model.signIn(id: id.trimmingCharacters(in: .whitespaces), password: password.trimmingCharacters(in: .whitespaces))
                password = ""
                busy = false
                onSuccess()
            } catch {
                self.error = (error as? PortalError).map { e in
                    if case .loginFailed(let m) = e { return m }
                    return e.localizedDescription
                } ?? "네트워크 오류로 로그인을 확인하지 못했습니다"
                busy = false
            }
        }
    }
}

private struct LoginPage: View {
    @Bindable var state: LoginState
    @Environment(AppModel.self) private var model
    var body: some View {
        Head(title: "학사시스템 로그인", desc: "하이하나(hi.hana.hs.kr) 계정으로 로그인하면 시간표·면학 위치·학사일정·게시판을 자동으로 가져옵니다.") {
            Image(systemName: "graduationcap.circle.fill").font(.system(size: 84)).foregroundStyle(.white, Color.blue.gradient).accessibilityHidden(true)
        }
        if model.hasCredentials && !model.isDemo {
            Card { FeatureRow(symbol: "checkmark.circle.fill", color: .green, title: "이미 연결되어 있습니다", body_: Credentials.memId) }.padding(.top, 24)
        } else {
            Card {
                TextField("아이디", text: $state.id)
                    .textContentType(.username).keyboardType(.asciiCapable).textInputAutocapitalization(.never).autocorrectionDisabled()
                Divider()
                SecureField("비밀번호", text: $state.password).textContentType(.password)
            }
            .padding(.top, 24)
            if let e = state.error { Text(e).font(.footnote).foregroundStyle(.red).padding(.top, 8) }
            Text("비밀번호는 이 기기의 키체인에 암호화되어 저장되고 hi.hana.hs.kr 에만 보냅니다.").font(.footnote).foregroundStyle(.secondary).padding(.top, 8)
        }
    }
}

private struct ClassroomPage: View {
    @Environment(AppModel.self) private var model
    var body: some View {
        Head(title: "클래스룸 연결",
             desc: "학교 Google 계정으로 한 번 허용하면 수업의 공지·과제·자료와 과제 마감을 앱에서 봅니다. 읽기만 하고, 내용은 암호화되어 이 기기에만 저장됩니다.") {
            BigSymbol(symbol: "graduationcap.fill", color: .green)
        }
        if model.bridge.connected {
            Card { FeatureRow(symbol: "checkmark.circle.fill", color: .green, title: "연결되었습니다", body_: model.bridge.email) }.padding(.top, 24)
        } else if model.bridge.pending {
            Card {
                FeatureRow(symbol: "hourglass", color: .orange, title: "허용을 마치면 채워집니다", body_: "브라우저에서 '허용'까지 누른 뒤 돌아오면 공지와 과제가 보입니다.")
                Button(model.classroomLoading ? "확인 중" : "확인") { Task { await model.loadClassroom(force: true) } }.disabled(model.classroomLoading)
            }
            .padding(.top, 24)
        }
    }
}

private struct DonePage: View {
    @State private var shown = false
    var body: some View {
        // 마지막 장에 오면 체크 표시가 손글씨처럼 그려집니다.
        Head(title: "준비 완료",
             desc: "홈 화면에 위젯을 추가하면 앱을 열지 않아도 지금 일정을 볼 수 있습니다. 홈 화면 빈 곳을 길게 누르고 편집 › 위젯 추가에서 HiHana Evo를 고르세요.") {
            Image(systemName: "checkmark.circle").font(.system(size: 84, weight: .medium)).foregroundStyle(.green)
                .drawOn(shown)
                .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { shown = true } }
                .accessibilityHidden(true)
        }
    }
}

// MARK: - 새 기능 장

/// 장에 들어올 때 그림 조각이 차례로 — 투명도·아래 12pt·0.96배에서 제자리로, 튕김 없는 스프링(장을 넘기다 돌아와도 자연스럽게 이어짐).
/// 동작 줄이기를 켰으면 움직이지 않고 그대로 보입니다.
private struct TourEnter: ViewModifier {
    let shown: Bool
    let order: Int
    @Environment(\.accessibilityReduceMotion) private var reduce
    func body(content: Content) -> some View {
        content
            .opacity(shown || reduce ? 1 : 0)
            .offset(y: shown || reduce ? 0 : 12)
            .scaleEffect(shown || reduce ? 1 : 0.96)
            .animation(reduce ? nil : .spring(response: 0.5, dampingFraction: 0.9).delay(0.07 * Double(order)), value: shown)
    }
}

private extension View {
    func tourEnter(_ shown: Bool, _ order: Int) -> some View { modifier(TourEnter(shown: shown, order: order)) }
}

/// 그림 판 — 실제 화면의 목록 묶음처럼 둥근 판에 줄을 담습니다.
private struct MiniPanel<C: View>: View {
    @ViewBuilder var content: C
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .background(Color(.tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

/// 클래스룸 과제·할 일 — 클래스룸 화면의 '할 일' 묶음과 스트림의 과제 한 줄을 줄여서. 마감 지난 줄이 한 번 밀려 '숨기기'를 보여 줍니다.
private struct TodoTourPage: View {
    let active: Bool
    @State private var shown = false
    @State private var swiped = false
    @Environment(\.accessibilityReduceMotion) private var reduce
    var body: some View {
        Head(title: "클래스룸 과제와 할 일",
             desc: "안 낸 과제는 할 일에 마감순으로 D-day와 함께 모이고, 낸 과제는 제출 여부와 점수가 보입니다. 마감이 없거나 지난 과제는 왼쪽으로 밀어 할 일에서 숨길 수 있습니다.") {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 6) {
                    Text("할 일").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                    Text("2").font(.subheadline.weight(.semibold)).monospacedDigit().foregroundStyle(.tertiary)
                    Spacer()
                    Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tint).rotationEffect(.degrees(90))
                }
                .padding(.horizontal, 4)
                .tourEnter(shown, 0)
                MiniPanel {
                    // 마감 지난 과제 — 밀면 오른쪽에서 회색 '숨기기'가 드러납니다(메일·미리 알림처럼).
                    ZStack(alignment: .trailing) {
                        // 밀 때만 드러나게 — 가만히 있을 땐 둥근 모서리 틈으로 회색이 비치지 않도록 숨깁니다.
                        Label("숨기기", systemImage: "eye.slash").labelStyle(.iconOnly).font(.body.weight(.semibold)).foregroundStyle(.white)
                            .frame(width: 72).frame(maxHeight: .infinity).background(Color.gray)
                            .opacity(swiped ? 1 : 0)
                        miniRow("모의고사 오답노트", meta: "수학Ⅱ · 마감 지남 · 11/18 18:00", overdue: true, badge: nil)
                            .background(Color(.tertiarySystemBackground))
                            .offset(x: swiped ? -72 : 0)
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    Divider().padding(.leading, 14)
                    miniRow("수열의 극한 연습문제 3단원", meta: "수학Ⅱ · 마감 11/21 (토) 23:59", overdue: false, badge: "D-2")
                }
                .tourEnter(shown, 1)
                MiniPanel {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack { Text("영어 독해와 작문").font(.subheadline.bold()); Spacer(); Text("어제").font(.caption).foregroundStyle(.secondary) }
                        HStack(spacing: 6) { Text("과제").font(.caption.bold()).foregroundStyle(.green); Text("Unit 5 Writing").font(.subheadline) }
                        Label("제출함 18/20점", systemImage: "checkmark.circle.fill").font(.caption).foregroundStyle(.green)
                    }
                    .padding(14)
                }
                .tourEnter(shown, 2)
            }
            .accessibilityHidden(true)
        }
        .task(id: active) {
            guard active else { shown = false; swiped = false; return }
            shown = true
            guard !reduce else { return }
            // 등장이 끝난 뒤 한 번 밀었다 되돌려 몸짓을 보여 줍니다 — 화면 안 이동이라 ease-in-out. 장을 떠나면 취소됩니다.
            try? await Task.sleep(for: .seconds(1.0))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.3)) { swiped = true }
            try? await Task.sleep(for: .seconds(1.2))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.3)) { swiped = false }
        }
    }

    private func miniRow(_ title: String, meta: String, overdue: Bool, badge: String?) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline).lineLimit(1)
                Text(meta).font(.caption).foregroundStyle(overdue ? Color.red : Color.secondary).lineLimit(1)
            }
            Spacer(minLength: 4)
            if let badge {
                Text(badge).font(.caption2.weight(.bold)).foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 3).background(.tint, in: Capsule())
            }
        }
        .padding(14)
    }
}

/// 내 일정 — 학사일정 목록의 한 날(학교 일정 + 분홍 '내 일정')과 오른쪽 위 + 단추를 줄여서.
private struct AgendaTourPage: View {
    let active: Bool
    @State private var shown = false
    var body: some View {
        Head(title: "직접 적는 내 일정",
             desc: "학사일정과 타임라인 오른쪽 위의 +로 약속이나 제출 마감을 적어 둡니다. 적은 일정은 홈·타임라인·데일리 브리핑에 학교 일정과 함께 나오고, 눌러서 고치거나 지울 수 있습니다.") {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("11월 21일 (토)").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                    Spacer()
                    Image(systemName: "plus").font(.body.weight(.semibold)).foregroundStyle(.tint)
                        .frame(width: 36, height: 36).background(Color(.tertiarySystemBackground), in: Circle())
                }
                .padding(.horizontal, 4)
                .tourEnter(shown, 0)
                MiniPanel {
                    row(title: "학부모 상담 주간", meta: nil, source: "학사", color: Color(argb: 0xFF3A7BD5), deadline: false)
                    Divider().padding(.leading, 14)
                    row(title: "물리 실험 보고서", meta: "23:00", source: "내 일정", color: Color(argb: 0xFFE0569B), deadline: true)
                    Divider().padding(.leading, 14)
                    row(title: "동아리 회의", meta: "19:00~20:00 · 세미나실", source: "내 일정", color: Color(argb: 0xFFE0569B), deadline: false)
                }
                .tourEnter(shown, 1)
            }
            .accessibilityHidden(true)
        }
        .onChange(of: active, initial: true) { _, on in shown = on }
    }

    private func row(title: String, meta: String?, source: String, color: Color, deadline: Bool) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if deadline {
                        Text("마감").font(.caption2.weight(.bold)).foregroundStyle(.red).padding(.horizontal, 6).padding(.vertical, 2).background(.red.opacity(0.14), in: Capsule())
                    }
                    Text(title).font(.subheadline).lineLimit(1)
                }
                if let meta { Text(meta).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer(minLength: 4)
            Text(source).font(.caption.weight(.semibold)).foregroundStyle(color)
        }
        .padding(14)
    }
}

/// 날씨 — 날씨 화면 아래의 지표 칸(체감온도·습도·바람)과 시간별 강수량을 줄여서.
private struct WeatherTourPage: View {
    let active: Bool
    @State private var shown = false
    var body: some View {
        Head(title: "더 자세한 날씨",
             desc: "체감온도·습도·바람과 시간별·요일별 강수량을 봅니다. 비가 오고 있으면 지난 1시간 동안 내린 양도 함께 알려 줍니다.") {
            VStack(spacing: 10) {
                MiniPanel {
                    HStack(spacing: 0) {
                        // 흰 판 위라 구름은 회색, 해는 노랑, 빗방울은 파랑으로(흰 구름이 바탕에 묻히지 않게).
                        ForEach(Array([("지금", "cloud.sun.fill", Color.yellow, "5°", nil), ("16시", "cloud.rain.fill", Color.blue, "4°", "1mm 미만"),
                                       ("17시", "cloud.rain.fill", Color.blue, "3°", "2.4mm"), ("18시", "cloud.fill", Color.gray, "2°", nil)].enumerated()), id: \.offset) { _, h in
                            VStack(spacing: 6) {
                                Text(h.0).font(.caption).foregroundStyle(.secondary)
                                Image(systemName: h.1).symbolRenderingMode(.palette).foregroundStyle(Color(.systemGray3), h.2).font(.title3).frame(height: 26)
                                Text(h.4 ?? " ").font(.system(size: 10, weight: .semibold)).foregroundStyle(.blue)
                                Text(h.3).font(.subheadline.weight(.semibold))
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.vertical, 14)
                }
                .tourEnter(shown, 0)
                HStack(spacing: 10) {
                    tile("체감온도", value: "2°", note: "실제보다 3° 낮아요").tourEnter(shown, 1)
                    tile("습도", value: "39%", note: "적당해요").tourEnter(shown, 2)
                    tile("바람", value: "4.1m/s", note: "서풍 · 약한 바람").tourEnter(shown, 3)
                }
            }
            .accessibilityHidden(true)
        }
        .onChange(of: active, initial: true) { _, on in shown = on }
    }

    private func tile(_ label: String, value: String, note: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.title3.weight(.semibold)).minimumScaleFactor(0.7).lineLimit(1)
            Text(note).font(.caption2).foregroundStyle(.secondary).lineLimit(2).fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 96, alignment: .topLeading)
        .background(Color(.tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

/// SideStore 업데이트 — 앱을 열 때 뜨는 '새 버전' 창을 줄여서. 이 장의 단추는 SideStore 에 HiHana 소스를 더합니다.
private struct UpdateTourPage: View {
    let active: Bool
    @State private var shown = false
    var body: some View {
        Head(title: "SideStore로 받는 업데이트",
             desc: "새 버전이 나오면 앱을 열 때 알려 주고, 업데이트를 누르면 SideStore가 받아 설치합니다. 앱 데이터는 그대로 남습니다. 소스를 추가해 두면 SideStore에서도 새 버전을 보여 줍니다.") {
            // 시스템 알림 창 모양 — 들어올 때 0.95배에서 커지는 건 실제 알림 창이 뜨는 모습과 같습니다.
            VStack(spacing: 0) {
                VStack(spacing: 4) {
                    Text("새 버전 14.5").font(.headline)
                    Text("SideStore에서 설치합니다. 앱 데이터는 그대로 남습니다.").font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .padding(.horizontal, 18).padding(.vertical, 18)
                Divider()
                HStack(spacing: 0) {
                    Text("나중에").frame(maxWidth: .infinity)
                    Divider().frame(height: 44)
                    Text("업데이트").fontWeight(.semibold).frame(maxWidth: .infinity)
                }
                .foregroundStyle(.tint)
            }
            .frame(width: 270)
            .background(Color(.tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: .black.opacity(0.12), radius: 16, y: 6)
            .tourEnter(shown, 0)
            .padding(.vertical, 12)
            .accessibilityHidden(true)
        }
        .onChange(of: active, initial: true) { _, on in shown = on }
    }
}
