import SwiftUI

/// 스택에 밀어 넣는 화면 — 경로마다 제목·버튼·내용.
struct RouteView: View {
    let route: Route
    @Environment(AppModel.self) private var model

    var body: some View {
        switch route {
        case .allPanel:
            AllPanelView()
        case .meal(let date):
            MealRoute(date: date)
        case .timetable:
            TimetableView().navigationTitle("시간표")
        case .settings(nil):
            SettingsRootView().navigationTitle("설정")
        case .settings(let page?):
            SettingsPageView(page: page).navigationTitle(page.title).navigationBarTitleDisplayMode(.inline)
        case .about:
            AboutView().navigationTitle("정보").navigationBarTitleDisplayMode(.inline)
        case .privacy:
            PrivacyView()
        case .changelog:
            ChangelogView().navigationTitle("변경 사항")
        case .licenses:
            LicensesView().navigationTitle("오픈소스 라이선스")
        case .legal:
            LegalView().navigationTitle("법적 고지")
        case .board:
            BoardView()
                .navigationTitle("게시판")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { model.nav.open(.settings(.notify)) } label: { Label("새 글 알림", systemImage: "bell") }
                    }
                }
        case .boardPost(let url, let t):
            BoardPostView(url: url, fallbackTitle: t)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { OpenInBrowserButton(url: url) } }
        case .web(let url, let t, let resync):
            PortalWebView(url: url)
                .navigationTitle(t)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { OpenInBrowserButton(url: url) } }
                .onDisappear { if resync { Task { await model.syncPlaces() } } }
        case .academic:
            AcademicView()
                .navigationTitle("학사일정")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            Task {
                                if model.schedule.isEmpty { await model.loadSchedule(force: false) }
                                if !ScheduleExport.share(model.schedule) { Toast.show("내보낼 학사일정이 없습니다") }
                            }
                        } label: { Label("캘린더로 내보내기", systemImage: "square.and.arrow.up") }
                    }
                }
        case .alim:
            AlimView().navigationTitle("알리미")
        case .weather(let until):
            let place = weatherPlace(until: until, model: model)
            WeatherView(place: place, until: until).navigationTitle(place.label)
        case .search:
            UnifiedSearchView()
        case .portalSearch:
            PortalSearchView()
        case .apply:
            ApplyView()
        case .seats(let service):
            SeatsView(service: service)
        case .quickApply(let service):
            switch service {
            case .library: SeatsView(service: .library)
            case .studyRoom: SeatsView(service: .studyRoom)
            case .classroom: ClassroomApplyView()
            case .outing: OutingApplyView()
            }
        case .history(let service):
            switch service {
            case .library, .studyRoom: SeatHistoryView(service: service)
            case .outing: OutingHistoryView()
            case .classroom: ClassroomHistoryView()
            }
        case .classroomStatus:
            ClassroomStatusView()
        case .midnight:
            MidnightView()
        case .classroom, .classroomSearch:
            ClassroomView()
        case .classroomPost(let id):
            ClassroomPostView(id: id)
        default:
            ContentUnavailableView("준비 중", systemImage: "hammer", description: Text("이 화면은 곧 옮겨 옵니다."))
                .navigationTitle(title(route))
        }
    }

    private func title(_ r: Route) -> String {
        switch r {
        case .allPanel: "전체"
        case .meal: "급식"
        case .board: "게시판"
        case .classroom: "클래스룸"
        case .academic: "학사일정"
        case .timetable: "시간표"
        case .apply: "신청·내역"
        case .alim: "알리미"
        case .weather: "날씨"
        case .settings: "설정"
        case .boardPost: "게시글"
        case .classroomPost: "클래스룸"
        case .web(_, let t, _): t
        case .seats(let s): "\(s.label) 좌석"
        case .quickApply(let s): s.label
        case .history(let s): "\(s.label) 내역"
        case .classroomStatus: "교실별 현황"
        case .midnight: "심야면학"
        case .portalSearch: "하이하나 검색"
        case .search: "통합 검색"
        case .classroomSearch: "클래스룸 검색"
        case .about: "정보"
        case .changelog: "변경 사항"
        case .privacy: "개인정보 보호"
        case .licenses: "오픈소스 라이선스"
        case .legal: "법적 고지"
        }
    }
}

/// 포털 페이지를 Safari 로 — 앱 안 화면에서 안 보이는 기능이 있을 때.
private struct OpenInBrowserButton: View {
    let url: String
    @Environment(\.openURL) private var openURL
    var body: some View {
        Button { if let u = URL(string: url) { openURL(u) } } label: { Label("Safari로 열기", systemImage: "safari") }
    }
}

/// 급식 — 오늘(일간)과 이번 주(주간)를 위의 분할 단추로, 알레르기 설정은 오른쪽 위.
private struct MealRoute: View {
    @Environment(AppModel.self) private var model
    @State private var selected: LocalDate

    init(date: LocalDate?) {
        _selected = State(initialValue: date ?? LocalDate.today())
    }

    var body: some View {
        // 오늘·이번 주를 나누지 않고 늘 이번 주 — 날짜 줄에서 오늘이 먼저 골라져 있습니다.
        MealView(selected: $selected)
            .navigationTitle("급식")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { model.nav.open(.settings(.meal)) } label: { Label("알레르기 설정", systemImage: "exclamationmark.triangle") }
                }
            }
    }
}
