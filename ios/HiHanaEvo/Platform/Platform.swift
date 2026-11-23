import Foundation
import UIKit
import Observation
import WidgetKit
import AuthenticationServices
import BackgroundTasks
import UserNotifications
import ActivityKit

/// 게시판 새 글 알림 — 켠 게시판만 백그라운드 새로 고침 때 확인해 새 글만 알립니다.
/// 외출·외박 승인 알림 — 내가 낸 신청이 신청(2)에서 승인(4)·반려(8)로 바뀌면 알립니다(안드로이드 OutingNotifier 와 같은 규칙).
/// 단체 신청은 빼고, 처음엔 기준만 잡아 지난 승인이 한꺼번에 뜨지 않게, 두 확인 사이에 내고 바로 결정된 신청은 기준 뒤에 낸 것만.
enum OutingNotify {
    private static let statesKey = "outing_notify.states"
    private static let baselineKey = "outing_notify.baseline"
    private static let enabledKey = "outing_notify.enabled"

    /// 기본은 켜짐 — 내가 한 신청의 결과라서(주문·배송 알림처럼).
    static var enabled: Bool {
        get { SharedContainer.defaults.object(forKey: enabledKey) as? Bool ?? true }
        set { SharedContainer.defaults.set(newValue, forKey: enabledKey); if newValue { Notifier.requestPermission() } }
    }

    @MainActor
    static func process(_ rows: [OutingRow]) async {
        let d = SharedContainer.defaults
        let known = d.dictionary(forKey: statesKey) as? [String: Int] ?? [:]
        let baseline = d.object(forKey: baselineKey) as? Date
        let mine = rows.filter { !$0.group }
        let decided: [OutingRow] = baseline.map { base in
            mine.filter { r in
                let waiting = known["\(r.osmNo)"].map { $0 == 2 } ?? ((r.appliedAt ?? .distantPast) > base)
                return waiting && (r.state == 4 || r.state == 8)
            }
        } ?? []
        d.set(Dictionary(mine.map { ("\($0.osmNo)", $0.state) }, uniquingKeysWith: { a, _ in a }), forKey: statesKey)
        if baseline == nil { d.set(Date(), forKey: baselineKey) }
        guard enabled else { return }
        for r in decided {
            let content = UNMutableNotificationContent()
            // 돌아오는 날이 나가는 날보다 뒤면 외박 — 포털 목록에 종류 칸이 없어 날짜로 가립니다.
            let overnight = r.plannedOut.flatMap { o in r.plannedBack.map { Seoul.calendar.startOfDay(for: $0) > Seoul.calendar.startOfDay(for: o) } } ?? false
            content.title = "\(overnight ? "외박" : "외출") \(r.state == 4 ? "승인됨" : "반려됨")"
            content.body = [span(r.plannedOut, r.plannedBack), r.reason?.nilIfEmpty].compactMap { $0 }.joined(separator: " · ").nilIfEmpty ?? r.stateLabel
            content.userInfo = ["open": "outing"]
            try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "outing-\(r.osmNo)-\(r.state)", content: content, trigger: nil))
        }
    }

    /// '11.24(화) 18:00 ~ 21:00', 날이 다르면 끝에도 날짜.
    static func span(_ out: Date?, _ back: Date?) -> String? {
        guard let out else { return nil }
        let day = DateFormatter(); day.locale = Locale(identifier: "ko_KR"); day.timeZone = Seoul.calendar.timeZone; day.dateFormat = "M.d(E) HH:mm"
        guard let back else { return day.string(from: out) }
        let time = DateFormatter(); time.locale = day.locale; time.timeZone = day.timeZone; time.dateFormat = "HH:mm"
        let same = Seoul.calendar.isDate(out, inSameDayAs: back)
        return "\(day.string(from: out)) ~ \((same ? time : day).string(from: back))"
    }
}

enum BoardNotify {
    private static let key = "board_notify.enabled"
    static var enabled: Set<BoardCategory> {
        Set((SharedContainer.defaults.array(forKey: key) as? [Int] ?? []).compactMap(BoardCategory.init(rawValue:)))
    }

    static func toggle(_ c: BoardCategory) {
        var s = enabled
        if s.contains(c) { s.remove(c) } else { s.insert(c); Notifier.requestPermission() }
        SharedContainer.defaults.set(s.map(\.rawValue), forKey: key)
        BackgroundWork.scheduleRefresh()
    }

    /// 이미 알린 글.
    private static let seenKey = "board_notify.seen"
    static func seen() -> Set<String> { Set(SharedContainer.defaults.stringArray(forKey: seenKey) ?? []) }
    static func markSeen(_ keys: [String]) {
        var list = SharedContainer.defaults.stringArray(forKey: seenKey) ?? []
        for k in keys where !list.contains(k) { list.append(k) }
        SharedContainer.defaults.set(Array(list.suffix(1000)), forKey: seenKey)
    }
}

/// 위젯 — 앱이 바뀐 값을 저장한 뒤 위젯에 다시 그리라고 알립니다.
enum WidgetBridge {
    static func reload() { WidgetCenter.shared.reloadAllTimelines() }

    /// 위젯이 쓸 학사일정·급식을 공유 저장소에 — 바뀌었을 때만 쓰고 위젯을 다시 그립니다.
    /// 시간표·면학 위치·심야면학이 바뀐 것도 알아채도록 그 값들의 지문을 함께 둡니다.
    @MainActor
    static func publish(_ m: AppModel) {
        let today = m.today
        var d = WidgetData()
        d.holidays = Dictionary(uniqueKeysWithValues: m.holidays.map { ($0.key.iso, $0.value) })
        d.homeStay = Dictionary(uniqueKeysWithValues: HomeStaySchedule.compute(m.schedule, grade: Prefs.shared.studentGrade).map { ($0.key.iso, $0.value) })
        for day in [today, today.adding(days: 1)] {
            if let meals = m.mealDays[day.iso] { d.meals[day.iso] = meals }
            let t = Supervision.weekday1Teachers(m.schedule, date: day, grade: Prefs.shared.studentGrade)
            if !t.isEmpty { d.supervision[day.iso] = t.joined(separator: " ") }
        }
        d.outings = m.outingSpans()
        let places = PlaceStore.all()
        let stamp = [places[today.iso]?.description, places[today.adding(days: 1).iso]?.description, m.week.map { "\($0.lessons.count)-\($0.periodTimes?.count ?? 0)" },
                     m.midnight.description].compactMap { $0 }.joined(separator: "|")
        let stampKey = "widget.stamp"
        guard d != WidgetData.load() || SharedContainer.defaults.string(forKey: stampKey) != stamp else { return }
        JSONCache.write(d, WidgetData.name)
        SharedContainer.defaults.set(stamp, forKey: stampKey)
        reload()
    }
}

/// 클래스룸 연결 — 학교 계정 웹 앱을 앱 안 인증 창으로 열고, 닫히면(허용을 마쳤든 아니든) 받아 봅니다.
@MainActor
enum ClassroomConnect {
    private static var session: ASWebAuthenticationSession?
    private static let provider = PresentationProvider()

    static func begin(model: AppModel) {
        let url = model.bridge.signInURL()
        let s = ASWebAuthenticationSession(url: url, callbackURLScheme: "hihananeo") { _, _ in
            Task { @MainActor in
                session = nil
                await model.loadClassroom(force: true)
            }
        }
        s.presentationContextProvider = provider
        s.prefersEphemeralWebBrowserSession = false
        session = s
        if !s.start() { Toast.show("브라우저를 열 수 없습니다") }
    }

    private final class PresentationProvider: NSObject, ASWebAuthenticationPresentationContextProviding {
        func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
            UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
        }
    }
}

/// 실시간 활동(잠금 화면·Dynamic Island)에 지금 일정 — 안드로이드 Now Bar 와 같은 자리.
@Observable
@MainActor
final class LiveSchedule {
    static let shared = LiveSchedule()
    private(set) var enabled: Bool
    private init() { enabled = SharedContainer.defaults.bool(forKey: "live.enabled") }

    func setEnabled(_ on: Bool, model: AppModel) {
        enabled = on
        SharedContainer.defaults.set(on, forKey: "live.enabled")
        LiveActivityController.update(model: model)
    }
}

/// 백그라운드 새로 고침 — 게시판 새 글·알리미 확인과 아침 데일리 브리핑. iOS 가 정한 때에 깨워 주므로 정확한 시각은 보장되지 않습니다.
enum BackgroundWork {
    static let refreshID = "com.skh.hihanaevo.refresh"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshID, using: nil) { task in
            guard let task = task as? BGAppRefreshTask else { return }
            scheduleRefresh()
            let work = Task { await run(); task.setTaskCompleted(success: true) }
            task.expirationHandler = { work.cancel() }
        }
    }

    static func scheduleRefresh() {
        let req = BGAppRefreshTaskRequest(identifier: refreshID)
        req.earliestBeginDate = Date().addingTimeInterval(30 * 60)
        try? BGTaskScheduler.shared.submit(req)
    }

    /// 깨어났을 때 — 알리미·켠 게시판의 새 글을 보고, 아침이면 데일리 브리핑을 알립니다.
    @MainActor
    static func run() async {
        guard Credentials.hasAny else { return }
        if let alims = try? await AcademicRepository.alim(force: true) { Notifier.processAlims(alims) }
        for c in BoardNotify.enabled {
            guard let posts = try? await AcademicRepository.board(c, force: true) else { continue }
            let seen = BoardNotify.seen()
            let fresh = posts.filter { $0.isNew && !seen.contains(BoardReadStore.key($0)) }
            BoardNotify.markSeen(posts.map(BoardReadStore.key))
            // 처음 켠 게시판은 지금 있는 글을 알리지 않습니다.
            if seen.isEmpty { continue }
            for p in fresh.prefix(3) {
                let content = UNMutableNotificationContent()
                content.title = c.label
                content.body = PostSummarizer.shared.line(p.url) ?? p.title
                content.userInfo = ["open": "post", "url": p.url, "board": c.rawValue]
                try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "post-\(BoardReadStore.key(p))", content: content, trigger: nil))
            }
        }
        // 외출·외박 승인·반려 — 기다리는 신청이 없어도 위젯의 외출 블록을 위해 받습니다(조회 한 번).
        if let r = try? await OutingAPI.list(.all, page: 1, pageSize: 20) { await OutingNotify.process(r.rows) }
        await MorningBriefNotifier.notifyIfMorning()
        if let model = AppModel.current { LiveActivityController.refreshInBackground(model: model) }
    }

    /// 아침 브리핑 알림 — 평일 7:00, 주말 8:30. 그 시각에 맞춰 둔 알림을, 받아 둔 브리핑 내용으로 채웁니다.
    static func scheduleMorningBrief() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: (0..<7).map { "brief-\($0)" })
        guard MorningBrief.shared.notifyEnabled else { return }
        Notifier.requestPermission()
        let today = LocalDate.today()
        for i in 0..<7 {
            let day = today.adding(days: i)
            let minutes = day.weekday >= 6 ? 8 * 60 + 30 : 7 * 60
            let at = day.at(minutes: minutes)
            guard at > Date() else { continue }
            let content = UNMutableNotificationContent()
            let brief = MorningBrief.shared.current.flatMap { $0.date == day ? $0 : nil }
            content.title = brief?.headline ?? "데일리 브리핑"
            content.body = brief?.body ?? "오늘 일정과 이번 주에 챙길 것을 확인하세요"
            content.subtitle = "데일리 브리핑"
            let comps = Seoul.calendar.dateComponents([.year, .month, .day, .hour, .minute], from: at)
            center.add(UNNotificationRequest(identifier: "brief-\(i)", content: content, trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)))
        }
    }
}

enum MorningBriefNotifier {
    /// 아침 알림 시각 전에 깨어났으면 오늘 브리핑을 만들어 그 알림 내용을 채웁니다.
    @MainActor
    static func notifyIfMorning() async {
        guard MorningBrief.shared.notifyEnabled, briefMorning(Date()) else { return }
        BackgroundWork.scheduleMorningBrief()
    }
}

/// 실시간 활동 — 지금 구간과 남은 시간을 잠금 화면·Dynamic Island 에(위젯 확장이 그립니다). 표시 창은 그날 첫 구간(평일 아침,
/// 주말 1타임)부터 마지막 구간(평일 2타임, 주말 4타임) 끝까지. 앱이 깨어 있을 때마다 맞추고, 구간 끝을 stale 시각으로 둡니다.
@MainActor
enum LiveActivityController {
    static func update(model: AppModel) {
        let activities = Activity<NowActivityAttributes>.activities
        guard LiveSchedule.shared.enabled, ActivityAuthorizationInfo().areActivitiesEnabled else {
            end(activities)
            return
        }
        let now = Date()
        let blocks = model.blocks(for: model.today)
        let shown = blocks.filter { !$0.isBlank }
        guard let first = shown.first?.start, let last = shown.last?.end, now >= first, now < last,
              let block = Schedule.block(at: now, in: blocks), !block.isBlank else {
            end(activities)
            return
        }
        func segment(_ b: Block, at time: Date) -> NowActivityAttributes.Segment {
            let next = Schedule.nextEvent(blocks: blocks, current: b, time: time)
            return .init(status: b.statusLabel, title: b.isGap ? b.statusLabel : b.headline, room: b.room,
                         start: b.start, end: b.end, accentARGB: Prefs.shared.widgetKindColors[b.accent.storageName] ?? b.accent.defaultARGB,
                         symbol: b.symbol, next: next.map { n in [n.start.hm, n.title, n.room].compactMap { $0 }.joined(separator: " · ") })
        }
        let cur = segment(block, at: now)
        // 다음 구간 — 끝나는 시각에 위젯이 스스로 넘어가게(푸시 서버가 없어 앱이 자는 동안엔 이것뿐).
        let after = Schedule.block(at: block.end, in: blocks).flatMap { $0.isBlank || $0.start < block.end ? nil : segment($0, at: block.end) }
        let state = NowActivityAttributes.ContentState(
            status: cur.status, title: cur.title, room: cur.room, start: cur.start, end: cur.end,
            accentARGB: cur.accentARGB, symbol: cur.symbol, next: cur.next, then: after)
        // 이 구간이 끝나면(stale) 위젯이 then 으로 넘어가 그립니다.
        let content = ActivityContent(state: state, staleDate: block.end, relevanceScore: 1)
        if let current = activities.first {
            guard current.content.state != state else { return }
            Task { await current.update(content) }
        } else {
            _ = try? Activity.request(attributes: NowActivityAttributes(), content: content)
        }
    }

    /// 백그라운드 새로 고침에서 — 이미 떠 있는 활동만 지금 구간으로 맞춥니다(iOS 는 백그라운드에서 새로 시작하지 못함).
    /// 시간표를 아직 못 읽었으면(빈 하루) 끄지 않고 그대로 둡니다.
    static func refreshInBackground(model: AppModel) {
        guard !Activity<NowActivityAttributes>.activities.isEmpty else { return }
        model.today = LocalDate.today()
        guard !model.blocks(for: model.today).isEmpty else { return }
        update(model: model)
    }

    private static func end(_ list: [Activity<NowActivityAttributes>]) {
        for a in list { Task { await a.end(nil, dismissalPolicy: .immediate) } }
    }
}
