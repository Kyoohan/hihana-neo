import Foundation
import Observation
import SwiftUI

/// 화면 하나 — 시트로 여는 곳들.
enum Route: Hashable {
    case allPanel
    case meal(LocalDate?)
    case board
    case classroom
    case academic
    case timetable
    case apply
    case alim
    case weather(until: Date?)
    case settings(SettingsPage?)
    case boardPost(url: String, title: String?)
    case classroomPost(id: String)
    case web(url: String, title: String, resync: Bool)
    case seats(SeatService)
    case quickApply(ApplyService)
    case history(ApplyService)
    case classroomStatus
    case midnight
    case portalSearch
    /// 통합 검색 — 게시판·클래스룸·일정·알리미를 한 번에.
    case search
    case classroomSearch
    case about
    case changelog
    case privacy
    case licenses
    case legal
}

/// 설정의 각 페이지.
enum SettingsPage: String, Hashable, CaseIterable {
    case account, display, widget, notify, board, classroom, meal, info
}

/// 앱 전체 상태 — 안드로이드 HiHanaEvoAppContent 의 상태와 불러오기 규칙을 그대로 옮겼습니다.
@Observable
@MainActor
final class AppModel {
    let isDemo: Bool
    private let prefs = Prefs.shared

    // MARK: 계정
    var hasCredentials: Bool

    // MARK: 오늘
    /// 앱에 하나뿐인 모델 — 백그라운드 새로 고침이 실시간 활동을 맞출 때 씁니다.
    static weak var current: AppModel?
    var today = LocalDate.today()
    var now = Clock.now
    /// 날짜별 타임 → 장소.
    var places: [String: [PlanSlot: StudyPlace]] = [:]
    var week: TimetableWeek?
    var midnight: [LocalDate: [Schedule.MidnightBooking]] = [:]
    var isSyncing = false
    /// 전체 새로고침 — 급식·알리미까지 포함하므로 isSyncing 보다 길게 켜져 있습니다.
    var isRefreshingAll = false
    var offline = false
    var lastSyncAt: Date?

    // MARK: 급식
    var mealDays: [String: DayMeals] = [:]
    var neisDays: [String: [Meal: NeisMealInfo]] = [:]
    var isLoadingMeals = false

    // MARK: 학사일정
    var schedule: [HanaScheduleEntry] = []
    var scheduleLoading = false
    var scheduleError: String?

    // MARK: 게시판
    var boardCategory: BoardCategory = .studentNotice
    var boardPosts: [HanaBoardPost] = []
    var boardLoading = false
    var boardError: String?
    var boardUnreadCounts: [BoardCategory: Int] = [:]
    /// 홈 카드는 늘 학생공지.
    var homeBoardPosts: [HanaBoardPost] = []
    var homeBoardLoading = false
    @ObservationIgnored private var boardForcedOnce = false

    // MARK: 알리미
    var alims: [HanaAlim] = []
    /// 최근 외출·외박 신청(반려·취소 포함 원본) — 타임라인과 홈 '지금'에 씁니다.
    var outings: [OutingRow] = []
    var alimUnread = 0
    var alimLoading = false
    var alimError: String?

    // MARK: 클래스룸
    var classroomLoading = false
    var classroomError: String?
    var classroomCourseFilter: String?

    let classroomStore = ClassroomStore.shared
    let bridge = ClassroomBridge.shared
    let boardAgenda = BoardAgendaStore.shared
    let boardRead = BoardReadStore.shared
    let summarizer = PostSummarizer.shared
    let brief = MorningBrief.shared
    let nav = Navigator.shared

    @ObservationIgnored private var tickTask: Task<Void, Never>?

    init(demo: Bool = CommandLine.arguments.contains("-demo")) {
        isDemo = demo
        hasCredentials = demo || Credentials.hasAny
        lastSyncAt = Prefs.shared.lastSyncAt
        if demo {
            week = Sample.week
            mealDays[today.iso] = Sample.meals
            mealDays[today.adding(days: 1).iso] = Sample.meals
            schedule = Sample.schedule(today)
            homeBoardPosts = Sample.board(today)
            boardPosts = homeBoardPosts
            alims = Sample.alims
            outings = Sample.outings(today)
            alimUnread = alims.filter { !$0.read }.count
            ClassroomStore.shared.installSample()
        } else {
            week = JSONCache.read(TimetableWeek.self, "timetable_latest")?.value
            schedule = AcademicRepository.cachedSchedule()
            HomeStaySchedule.remember(schedule)
            homeBoardPosts = AcademicRepository.cachedBoard(.studentNotice) ?? []
            alims = markLocalRead(AcademicRepository.cachedAlim())
            alimUnread = alims.filter { !$0.read }.count
            for d in -1...13 { let day = today.adding(days: d); if let m = MealCache.load(day) { mealDays[day.iso] = m } }
            midnight = MidnightSchedule.load()
        }
        reloadPlaces()
    }

    // MARK: 계산

    var holidays: [LocalDate: String] { HomeStaySchedule.holidays(schedule) }

    func context() -> Schedule.Context {
        let places = self.places
        return Schedule.Context(week: week, holidays: holidays, placeFor: { date, slot in
            places[date.iso]?[slot] ?? slot.defaultPlace
        }, midnight: midnight, homeStay: HomeStaySchedule.compute(schedule, grade: prefs.studentGrade), outings: outingSpans())
    }

    func blocks(for date: LocalDate) -> [Block] { Schedule.blocks(for: date, context: context()) }

    var hasTimetable: Bool { week != nil }

    /// 게시글에서 AI 가 뽑은 일정 + 안 낸 클래스룸 과제 마감 — 숨긴 수업·다른 학년 일정은 뺍니다.
    var agendaExtras: [AgendaItem] {
        let hidden = classroomStore.hidden
        let hiddenPosts = Set((classroomStore.snapshot?.posts ?? []).filter { hidden.contains($0.courseId) }.map(\.id))
        let grade = prefs.studentGrade
        let fromBoard = boardAgenda.items().filter { $0.forGrade(grade) }.filter { item in
            guard item.source == .classroom, item.id.hasPrefix("cr:") else { return true }
            let postID = item.id.dropFirst(3).split(separator: ":").first.map(String.init) ?? ""
            return !hiddenPosts.contains(postID)
        }
        return fromBoard + (classroomConnected ? (classroomStore.visible?.agenda() ?? []) : []) + UserAgendaStore.shared.items()
    }

    var classroomConnected: Bool { bridge.connected || classroomStore.sampleMode }

    var classroomSnapshot: ClassroomSnapshot? { classroomConnected ? classroomStore.visible : nil }

    func mealPick(now: Date) -> MealPick? {
        MealRules.current(today: today, todayMeals: mealDays[today.iso], tomorrowMeals: mealDays[today.adding(days: 1).iso], now: now)
    }

    /// 1타임 면학감독 교사.
    var weekday1Supervisor: String? {
        let t = Supervision.weekday1Teachers(schedule, date: today, grade: prefs.studentGrade)
        return t.isEmpty ? nil : t.joined(separator: " ")
    }

    // MARK: 시작

    func start() async {
        startTicking()
        if isDemo { return }
        // 급식은 공개 엔드포인트 — 로그인 없이.
        await loadMealDay(today, force: false)
        await loadMealDay(today.adding(days: 1), force: false)
        if hasCredentials {
            await refreshTimetable(force: false)
            await syncPlaces()
            async let s: Void = loadSchedule(force: false)
            async let b: Void = loadHomeBoard(force: false)
            async let a: Void = loadAlim(force: false)
            async let c: Void = loadClassroom(force: false)
            async let o: Void = loadOutings()
            _ = await (s, b, a, c, o)
            if homeBoardPosts.isEmpty && !boardForcedOnce { boardForcedOnce = true; await loadHomeBoard(force: true) }
            if boardAgenda.needsRebuild { await rebuildBoardAgenda() }
        } else {
            await loadClassroom(force: false)
        }
        await ensureBrief()
        publishSurfaces()
    }

    /// 위젯·실시간 활동을 지금 값에 맞춥니다 — 바뀐 게 없으면 아무것도 하지 않습니다.
    func publishSurfaces() {
        guard !isDemo else { return }
        WidgetBridge.publish(self)
        LiveActivityController.update(model: self)
    }

    private func startTicking() {
        tickTask?.cancel()
        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(20))
                guard let self else { return }
                self.now = Clock.now
                let day = LocalDate.today(now: self.now)
                if day != self.today {
                    self.today = day
                    self.reloadPlaces()
                    // 자정을 넘기면 새 '내일' 급식도 — 앱을 켜 둔 채 하루가 지나면 밤 간식 뒤 홈·위젯의 다음 급식 칸이 비었습니다.
                    if !self.isDemo { Task { await self.loadMealDay(day, force: false); await self.loadMealDay(day.adding(days: 1), force: false) } }
                }
                self.publishSurfaces()
            }
        }
    }

    /// 앱으로 돌아올 때 — 포털에서 장소를 바꾼 뒤 돌아오면 새로 받습니다.
    func resumed() async {
        now = Clock.now
        today = LocalDate.today(now: now)
        guard !isDemo, hasCredentials else { return }
        await loadMealDay(today, force: true)
        await loadMealDay(today.adding(days: 1), force: false)
        await syncFromHana()
        // 앱을 다시 펼치면 클래스룸도 바로 — 스크립트가 5분마다 올리니 2분 넘은 사본이면 새로 받습니다(안드로이드 14.3).
        await loadClassroom(force: bridge.pending)
    }

    func reloadPlaces() {
        for d in -1...13 { let day = today.adding(days: d); places[day.iso] = PlaceStore.places(for: day) }
        if isDemo { places[today.iso] = Sample.places }
    }

    // MARK: 급식

    func loadMealDay(_ date: LocalDate, force: Bool) async {
        if force || MealCache.load(date, maxAge: MealCache.ttl) == nil {
            isLoadingMeals = true
            try? await MealCache.refresh(date)
            isLoadingMeals = false
        }
        if let m = MealCache.load(date) { mealDays[date.iso] = m }
    }

    func loadNeis(_ date: LocalDate) async {
        let info = await NeisMeal.load(date)
        if !info.isEmpty { neisDays[date.iso] = info }
    }

    // MARK: 시간표·면학 위치

    func refreshTimetable(force: Bool) async {
        if !force, let stored = JSONCache.read(TimetableWeek.self, "timetable_latest"), JSONCache.isFresh(stored.at, ttl: 6 * 3600) {
            week = stored.value
            return
        }
        if let w = await TimetableSync.fetch() {
            week = w
            JSONCache.write(w, "timetable_latest")
            offline = false
        }
    }

    /// 오늘의 면학 위치 — 면학 배정 + 교과교실 신청 + 1인2기·방과후.
    func syncPlaces() async {
        do {
            let rest = context().isRestDay(today)
            let sync = try await DailySync.fetch(today)
            DailySync.apply(sync, date: today, isRestDay: rest)
            reloadPlaces()
            offline = false
            prefs.lastSyncAt = Date()
            lastSyncAt = prefs.lastSyncAt
        } catch let e as URLError where [.notConnectedToInternet, .timedOut, .cannotConnectToHost, .networkConnectionLost].contains(e.code) {
            offline = true
        } catch let e as PortalError where e.needsAccount {
            hasCredentials = Credentials.hasAny
        } catch {}
    }

    /// 학사시스템 전체 — 면학 위치와 시간표를 새로(캐시 아님).
    func syncFromHana() async {
        guard hasCredentials else { nav.open(.settings(.account)); return }
        isSyncing = true
        defer { isSyncing = false }
        await syncPlaces()
        await refreshTimetable(force: true)
        await MidnightSchedule.refresh(model: self)
    }

    /// 신청·취소 뒤 — 면학 위치를 뒤에서 다시 받습니다.
    func syncPlacesSoon() { Task { await syncPlaces() } }

    /// 끌어내려 새로고침 — 급식, 학사시스템, 알리미, 홈 목록까지.
    func refreshAll() async {
        guard !isRefreshingAll else { return }
        isRefreshingAll = true
        defer { isRefreshingAll = false }
        if isDemo { try? await Task.sleep(for: .milliseconds(600)); return }
        await loadMealDay(today, force: true)
        if hasCredentials {
            await syncFromHana()
            await loadAlim(force: true)
            await loadHomeBoard(force: true)
        }
        await loadClassroom(force: true)
        await loadOutings()
        await ensureBrief()
    }

    // MARK: 외출·외박

    /// 최근 신청 20건 — 지나간 것·반려·취소는 쓸 때 거릅니다. 실패하면 이전 것을 그대로 둡니다.
    func loadOutings() async {
        if isDemo { return }
        guard hasCredentials else { return }
        guard let r = try? await OutingAPI.list(.all, page: 1, pageSize: 20) else { return }
        await OutingNotify.process(r.rows)
        if r.rows != outings { outings = r.rows; publishSurfaces() }
    }

    /// 실제로 나가 있는(또는 나갈) 구간 — 신청·승인·복귀만(반려 8·취소 32 제외), 실제 시각이 있으면 그것을.
    func outingSpans() -> [OutingSpan] {
        outings.compactMap { o in
            guard o.state != 8 && o.state != 32, let s = o.actualOut ?? o.plannedOut, let e = o.actualBack ?? o.plannedBack, e > s else { return nil }
            return OutingSpan(start: s, end: e, reason: o.reason, pending: o.state == 2)
        }
    }

    // MARK: 학사일정

    func loadSchedule(force: Bool) async {
        if isDemo { return }
        scheduleLoading = true
        scheduleError = nil
        defer { scheduleLoading = false }
        do {
            schedule = try await AcademicRepository.schedule(force: force)
            HomeStaySchedule.remember(schedule)
        } catch let e as PortalError where e.needsAccount {
            scheduleError = e == .missingCredentials ? academicNeedsLogin : (e.errorDescription ?? academicNeedsLogin)
        } catch is CancellationError {
        } catch {
            scheduleError = error.localizedDescription
        }
    }

    // MARK: 게시판

    func loadBoard(force: Bool) async {
        if isDemo { boardPosts = Sample.board(today); return }
        boardLoading = true
        boardError = nil
        defer { boardLoading = false }
        do {
            let category = boardCategory
            let posts = try await AcademicRepository.board(category, force: force)
            guard category == boardCategory else { return }
            boardPosts = posts
            if category == .studentNotice { homeBoardPosts = posts }
            refreshBoardCounts(fetchAll: false)
            Task { await summarizer.prefetch(posts) }
        } catch let e as PortalError where e.needsAccount {
            boardError = e == .missingCredentials ? academicNeedsLogin : (e.errorDescription ?? academicNeedsLogin)
        } catch is CancellationError {
        } catch {
            boardError = error.localizedDescription
        }
    }

    func selectBoard(_ c: BoardCategory) {
        boardCategory = c
        boardPosts = AcademicRepository.cachedBoard(c) ?? []
        Task { await loadBoard(force: false) }
    }

    func loadHomeBoard(force: Bool) async {
        if isDemo { return }
        homeBoardLoading = true
        defer { homeBoardLoading = false }
        if let posts = try? await AcademicRepository.board(.studentNotice, force: force) {
            homeBoardPosts = posts
            Task { await summarizer.prefetch(posts) }
        }
    }

    /// 게시판별 안 읽은 글 수 — [fetchAll] 이면 아직 안 받은 게시판 목록도 받아 옵니다(조회만).
    func refreshBoardCounts(fetchAll: Bool) {
        Task {
            if fetchAll && !isDemo { for c in BoardCategory.allCases { _ = try? await AcademicRepository.board(c) } }
            var counts: [BoardCategory: Int] = [:]
            for c in BoardCategory.allCases {
                guard let list = AcademicRepository.cachedBoard(c) else { continue }
                boardRead.seedIfNeeded(c, list)
                counts[c] = boardRead.unreadCount(list)
            }
            boardUnreadCounts = counts
        }
    }

    /// '모두 읽음' — 받아 둔 모든 게시판의 글을 읽은 것으로(앱 안에서만).
    func markAllBoardsRead() {
        for c in BoardCategory.allCases { if let list = AcademicRepository.cachedBoard(c) { boardRead.markRead(list) } }
        refreshBoardCounts(fetchAll: false)
    }

    /// [select] 는 아이패드 두 칸의 왼쪽 목록에서 고른 것 — 오른쪽 칸을 바꿉니다.
    func openBoardPost(_ post: HanaBoardPost, select: Bool = false) {
        boardRead.markRead([post])
        refreshBoardCounts(fetchAll: false)
        go(.boardPost(url: post.url, title: post.title), select: select)
    }

    private func go(_ route: Route, select: Bool) { if select { nav.select(route) } else { nav.open(route) } }

    /// 게시글 일정 보관함 규칙이 바뀐 뒤 한 번 — 받아 둔 게시판마다 위쪽 글의 요약을 새로 받아 일정을 다시 채웁니다.
    private func rebuildBoardAgenda() async {
        for c in BoardCategory.allCases { if let list = AcademicRepository.cachedBoard(c) { await summarizer.prefetch(Array(list.prefix(20))) } }
        boardAgenda.markRebuilt()
    }

    // MARK: 알리미

    private func markLocalRead(_ list: [HanaAlim]) -> [HanaAlim] {
        let readIDs = AlimReadStore.read()
        var seen = Set<String>()
        // 같은 선생님이 같은 내용을 잇달아 두 번 보내면 하나만.
        return list.map { a in var a = a; if !a.read && readIDs.contains(a.id) { a.read = true }; return a }
            .filter { seen.insert(($0.writer ?? "") + "\u{0}" + $0.content.trimmingCharacters(in: .whitespaces)).inserted }
    }

    func loadAlim(force: Bool) async {
        if isDemo { return }
        alimLoading = true
        alimError = nil
        defer { alimLoading = false }
        do {
            let list = try await AcademicRepository.alim(force: force)
            // 앱에서는 읽었는데 하이하나에는 아직 안 읽음인 것 — 다시 보내 맞춥니다.
            let readIDs = AlimReadStore.read()
            let pending = list.filter { !$0.read && readIDs.contains($0.id) }
            if !pending.isEmpty { Task { _ = await AcademicAPI.markAlimRead(pending.map(\.portalIdx)) } }
            alims = markLocalRead(list)
            alimUnread = alims.filter { !$0.read }.count
            Notifier.processAlims(alims)
        } catch let e as PortalError where e.needsAccount {
            alimError = academicNeedsLogin
        } catch is CancellationError {
        } catch {
            alimError = error.localizedDescription
        }
    }

    func openAlimScreen() {
        nav.open(.alim)
    }

    /// 알리미 한 건 읽음 — 앱과 하이하나 모두.
    func markAlimRead(_ alim: HanaAlim) {
        AlimReadStore.markRead([alim.id])
        alims = alims.map { var a = $0; if a.id == alim.id { a.read = true }; return a }
        alimUnread = alims.filter { !$0.read }.count
        if !alim.read && !isDemo { Task { if let reason = await AcademicAPI.markAlimRead([alim.portalIdx]) { alimSyncFailed(reason) } } }
    }

    /// 모두 읽음 — 포털의 '전체 읽음'을 보내고, 안 되면 하나씩. 끝나면 실제 상태로 다시 받습니다.
    func markAllAlimRead() {
        let unread = alims.filter { !$0.read }
        AlimReadStore.markRead(alims.map(\.id))
        alims = alims.map { var a = $0; a.read = true; return a }
        alimUnread = 0
        guard !isDemo else { return }
        Task {
            let all = await AcademicAPI.markAllAlimRead()
            if let all, let one = await AcademicAPI.markAlimRead(unread.map(\.portalIdx)) { alimSyncFailed("\(all) · \(one)") }
            await loadAlim(force: true)
        }
    }

    /// 알리미 열기 — 읽음 처리하고 하이하나 알리미 화면을 앱 안 웹으로.
    func openAlim(_ alim: HanaAlim, select: Bool = false) {
        markAlimRead(alim)
        go(.web(url: alimURL(alim.portalIdx), title: "알리미", resync: false), select: select)
    }

    private func alimSyncFailed(_ reason: String) {
        Toast.show("하이하나에는 읽음으로 반영하지 못했습니다 (\(reason))")
    }

    // MARK: 클래스룸

    /// 연결돼 있을 때만 — 10분 안에 받은 스냅샷이 있으면 [force] 가 아니면 그대로.
    func loadClassroom(force: Bool) async {
        if classroomStore.sampleMode { return }
        bridge.refreshFlags()
        guard bridge.connected || bridge.pending else { return }
        if !force, bridge.connected, let s = classroomStore.snapshot, Date().timeIntervalSince(s.fetchedAt) < ClassroomStore.ttl { return }
        classroomLoading = true
        classroomError = nil
        defer { classroomLoading = false }
        do {
            let snapshot = try await bridge.fetch()
            classroomStore.seedReadIfNeeded(snapshot.posts)
            classroomStore.save(snapshot)
            Task { await ClassroomAPI.extractEvents(snapshot.visible(classroomStore.hidden)) }
        } catch {
            classroomError = error.localizedDescription
        }
    }

    func openClassroomPost(_ post: ClassroomPost, select: Bool = false) {
        classroomStore.markRead([post])
        go(.classroomPost(id: post.id), select: select)
    }

    /// 통합 일정에서 게시글·클래스룸 일정을 눌렀을 때.
    /// 이 일정을 누르면 열리는 화면 — 학사일정 목록에서 고른 줄을 표시할 때.
    func agendaRoute(_ item: AgendaItem) -> Route? {
        switch item.source {
        case .board: item.url.map { .boardPost(url: $0, title: item.detail.map { $0.components(separatedBy: " · ").last ?? $0 }) }
        case .classroom: classroomStore.snapshot?.posts.first { "c:\($0.courseId):\($0.id)" == item.id || item.id.hasPrefix("cr:\($0.id):") }.map { .classroomPost(id: $0.id) }
        case .academic, .mine: nil
        }
    }

    func openAgenda(_ item: AgendaItem, select: Bool = false) {
        switch item.source {
        case .board:
            if let url = item.url { go(.boardPost(url: url, title: item.detail.map { $0.components(separatedBy: " · ").last ?? $0 }), select: select) }
        case .classroom:
            let post = classroomStore.snapshot?.posts.first { "c:\($0.courseId):\($0.id)" == item.id || item.id.hasPrefix("cr:\($0.id):") }
            if let post { openClassroomPost(post, select: select) } else if let url = item.url.flatMap(URL.init(string:)) { UIApplication.shared.open(url) }
        case .academic:
            break
        case .mine:
            // 내 일정은 눌러서 고칩니다.
            if let e = UserAgendaStore.shared.find(item.id) { nav.present(UserEventEditor(edit: e)) }
        }
    }

    // MARK: 데일리 브리핑

    func ensureBrief() async {
        // 샘플 화면에선 요약 서버를 부르지 않습니다(무료 사용량).
        if isDemo { brief.aiEnabled = false }
        // 새로 설치한 뒤엔 시간표·학사일정을 다 받기 전에 만들면 '이번 주 일정 없음'으로 굳어 버립니다 —
        // 계정을 연결하고 시간표와 학사일정을 받은 뒤에야 오늘 브리핑을 만듭니다(연동이 끝나면 afterLink 가 다시 부릅니다).
        if !isDemo && (!hasCredentials || week == nil || schedule.isEmpty) { return }
        let input = MorningBrief.input(today: today, blocks: Schedule.blocks(for: today, context: Schedule.Context(week: week, holidays: holidays)),
                                       schedule: schedule, grade: prefs.studentGrade, extras: agendaExtras)
        await brief.ensure(today: today, input: input)
    }

    // MARK: 계정

    /// 아이디·비밀번호로 로그인해 보고, 되면 저장합니다. 실패하면 아무것도 남기지 않습니다(계정 잠금 방지).
    func signIn(id: String, password: String) async throws {
        Credentials.save(id: id, password: password)
        await PortalClient.shared.resetSession()
        do {
            try await PortalClient.shared.login()
            hasCredentials = true
            Task { await afterLink() }
        } catch {
            Credentials.clear()
            throw error
        }
    }

    /// 연동 직후 — 시간표·일정·게시판·알리미를 바로.
    func afterLink() async {
        await refreshTimetable(force: true)
        await syncPlaces()
        await loadSchedule(force: true)
        await loadHomeBoard(force: true)
        await loadAlim(force: true)
        await loadOutings()
        await ensureBrief()
    }

    func signOut() {
        Credentials.clear()
        hasCredentials = false
        week = nil
        schedule = []
        homeBoardPosts = []
        boardPosts = []
        alims = []
        alimUnread = 0
        // 외출·외박도 — 남아 있으면 로그아웃(다른 계정으로 바꾸기) 뒤에도 이전 계정의 외출이 타임라인·위젯·실시간 활동에 떴습니다.
        outings = []
        for name in ["timetable_latest", "places", "academic_schedule_4w", "academic_exams", "academic_alim"] { JSONCache.remove(name) }
        for c in BoardCategory.allCases { JSONCache.remove("academic_board_\(c.bmtIdx)") }
        reloadPlaces()
        publishSurfaces()
        Task { await PortalClient.shared.resetSession() }
    }
}
