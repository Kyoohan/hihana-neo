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

#if DEBUG
/// (개발용) 하루치 실시간 현황을 눈으로 확인 — 실행 인자 -liveDayTest 로 켜면 20~40초짜리 구간 12개가 이어지는 가짜 하루를 올립니다.
/// 구간 수가 한도(frameLimit)와 같아서, 시뮬레이터의 activity-archive 파일 크기로 그리기 데이터가 한도 안인지도 볼 수 있습니다.
@MainActor
enum LiveDayTest {
    static var active: Bool { ProcessInfo.processInfo.arguments.contains("-liveDayTest") }

    static func startIfAsked() {
        // -liveSchedIn <초>: 그 초 뒤에 오늘 일과로 현황이 뜨도록 예약(LiveActivityController.scheduleNext 가 읽음).
        let args = CommandLine.arguments
        if let i = args.firstIndex(of: "-liveSchedIn"), i + 1 < args.count, let n = Double(args[i + 1]) {
            UserDefaults.standard.set(Date().addingTimeInterval(n).timeIntervalSince1970, forKey: LiveActivityController.testStartKey)
        }
        guard active else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(3))
            for a in Activity<NowActivityAttributes>.activities { await a.end(nil, dismissalPolicy: .immediate) }
            let rows: [(String, String, String, String?, Double, Accent, String, Bool)] = [
                ("등교 및 조회", "", "등교 및 조회", nil, 50, .lesson, "x.squareroot", true),
                ("1–2교시 수업 중", "미적분Ⅱ", "1–2교시 미적분Ⅱ", "B301", 40, .lesson, "x.squareroot", true),
                ("쉬는 시간", "", "쉬는 시간", nil, 20, .breakTime, "textformat", false),
                ("3–4교시 수업 중", "영어Ⅱ", "3–4교시 영어Ⅱ", "A201", 40, .lesson, "textformat", true),
                ("점심시간", "", "점심시간", nil, 30, .lunch, "atom", true),
                ("5–6교시 수업 중", "물리학Ⅱ", "5–6교시 물리학Ⅱ", "과학동 2", 40, .lesson, "atom", true),
                ("쉬는 시간", "", "쉬는 시간", nil, 20, .breakTime, "figure.run", false),
                ("7교시 수업 중", "체육", "7교시 체육", "체육관", 30, .lesson, "figure.run", true),
                ("일과 종료", "", "일과 종료", nil, 20, .idle, "chair", false),
                ("0타임", "교실 면학", "0타임", "2학년 3반", 30, .study, "chair", true),
                ("저녁시간", "", "저녁시간", nil, 30, .dinner, "books.vertical", true),
                ("1타임", "도서관", "1타임", "열람실 A-12", 30, .library, "books.vertical", true),
            ]
            // -liveSched: 지금 바로가 아니라 45초 뒤에 시작하도록 예약만(iOS 26) — 앱을 끄거나 뒤로 보내도 그 시각에 뜨는지 봅니다.
            let sched = ProcessInfo.processInfo.arguments.contains("-liveSched")
            var t = Date().addingTimeInterval(sched ? 45 : -30)
            let startAt = t
            // 글자 구간 frameLimit 개 + 막대에만 쓰는 구간 6개(현황이 사라진 뒤의 저녁 일정처럼).
            let n = Int(ProcessInfo.processInfo.environment["LIVE_N"] ?? "") ?? LiveActivityController.frameLimit
            // LIVE_SCALE=<배>: 구간 길이를 그만큼 늘림 — 한 시간 넘게 남은 때의 타이머 모양(접힌 아일랜드의 '79:19')을 봅니다.
            let mul = Double(ProcessInfo.processInfo.environment["LIVE_SCALE"] ?? "") ?? 1
            let day: [NowActivityAttributes.DayPart] = Array((rows + rows + rows).prefix(n + 6)).map { r in
                let len = TimeInterval(r.4) * mul
                defer { t = t.addingTimeInterval(len) }
                return .init(status: r.0, head: r.1, label: r.2, room: r.3, start: t, end: t.addingTimeInterval(len), accentARGB: r.5.defaultARGB, symbol: r.6, listed: r.7)
            }
            let f = day[0]
            // LIVE_LEAD=<n>: 막대 앞에 이미 지난 구간 n개(40분씩, 이름 없이)를 붙여 — 하루 시작부터 그리는 막대의 그리기 데이터 크기를 잼.
            let leadN = Int(ProcessInfo.processInfo.environment["LIVE_LEAD"] ?? "") ?? 0
            let pastParts: [NowActivityAttributes.DayPart] = (0..<leadN).map { k in
                let e = startAt.addingTimeInterval(-Double(leadN - 1 - k) * 2400)
                return .init(status: "", head: "", label: "", room: nil, start: e.addingTimeInterval(-2400), end: e,
                             accentARGB: rows[k % rows.count].5.defaultARGB, symbol: "", listed: rows[k % rows.count].7)
            }
            var state = NowActivityAttributes.ContentState(status: f.status, title: f.status, room: nil, start: f.start, end: f.end,
                                                           accentARGB: f.accentARGB, symbol: f.symbol, next: nil, then: nil, day: pastParts + day, more: true, framed: n)
            state.lead = leadN
            NSLog("LIVEDAYTEST state bytes \((try? JSONEncoder().encode(state))?.count ?? -1)")
            do {
                if sched, #available(iOS 26.0, *) {
                    let a = try Activity.request(attributes: NowActivityAttributes(), content: ActivityContent(state: state, staleDate: day.last?.end),
                                                 style: .standard,
                                                 alertConfiguration: AlertConfiguration(title: "오늘 일과 시작", body: "등교 및 조회", sound: .default),
                                                 start: startAt)
                    NSLog("LIVEDAYTEST scheduled id=\(a.id) state=\(String(describing: a.activityState)) at=\(startAt)")
                    return
                }
                let a = try Activity.request(attributes: NowActivityAttributes(), content: ActivityContent(state: state, staleDate: day.last?.end))
                // -liveDayEnd: 20초 뒤 현황을 끝내되 잠금 화면에 남겨, 8시간이 지나 iOS 가 끝낸 현황처럼 — 끝난 뒤에도 구간이 넘어가는지 봅니다.
                if ProcessInfo.processInfo.arguments.contains("-liveDayEnd") {
                    try? await Task.sleep(for: .seconds(20))
                    await a.end(nil, dismissalPolicy: .after(Date().addingTimeInterval(4 * 3600)))
                    NSLog("LIVEDAYTEST ended (kept on lock screen)")
                }
            } catch { NSLog("LIVEDAYTEST request failed: \(error)") }
        }
    }
}
#endif

/// 실시간 활동 — 지금 구간과 남은 시간을 잠금 화면·Dynamic Island 에(위젯 확장이 그립니다). 표시 창은 그날 첫 구간(평일 아침,
/// 주말 1타임)부터 마지막 구간(평일 2타임, 주말 4타임) 끝까지. 앱이 깨어 있을 때마다 지금부터 이어지는 구간을 한 번에 올립니다.
@MainActor
enum LiveActivityController {
    /// 현황 하나에 올리는 구간 수 한도. 구간마다 가림막과 남은 시간 글자가 현황의 그리기 데이터에 쌓이는데(구간당 약 92KB), 그 데이터가
    /// 너무 크면 iOS 가 현황을 아예 그리지 않습니다(시뮬레이터에서 2.30MB 통과, 2.71MB 거절). 16개면 약 1.56MB — 실기기 한도를 몰라 여유를 둠.
    nonisolated static let frameLimit = 16
    /// 현황 하나가 보이는 최대 시간 — iOS 는 시작 8시간 뒤 현황을 끝내(아일랜드에서 사라짐), 끝난 현황도 잠금 화면에는 4시간 더 남깁니다.
    /// 그 12시간 안의 구간까지 올려, 점심때 한 번 열면 잠금 화면에 밤 면학·심면까지 이어지게 합니다.
    nonisolated private static let lifetime: TimeInterval = 12 * 3600
    /// 앱이 앞에 있을 때 이보다 오래된 현황은 새로 시작해, 그때부터 8시간을 다시 받습니다(정한 값).
    private static let renewAfter: TimeInterval = 30 * 60
    private static let startedKey = "live.startedAt"
    private static var restarting = false

    /// 현황에 올릴 하루 — 막대에는 그날 마지막 일정(2타임·심면)까지, 글자 구간은 현황이 보이는 12시간 안에서 frameLimit 개까지.
    /// 막대에만 쓰는 구간은 시각·색만(현황 데이터 4KB 한도), 그 가운데 첫 일정만 이름을 남깁니다(마지막 글자 구간의 '다음 …' 문구용).
    nonisolated static func dayState(_ segments: [LiveSegment], now: Date, startedAt: Date, argb: (Accent) -> UInt32)
        -> (day: [NowActivityAttributes.DayPart], framed: Int, more: Bool, lead: Int)? {
        let all = LiveSegments.frames(segments, from: now, until: .distantFuture, limit: 40)
        guard let current = all.parts.first else { return nil }
        // 이미 지난 구간 — 막대를 그날 시작부터 그리려고 앞에 붙입니다. 시각·색만(현황 데이터 4KB 한도).
        let past: [NowActivityAttributes.DayPart] = segments
            .filter { !$0.isBlank && $0.end <= current.start }
            .map { .init(status: "", head: "", label: "", room: nil, start: $0.start, end: $0.end,
                         accentARGB: argb($0.accent), symbol: "", listed: LiveSegments.isListed($0)) }
        let window = startedAt.addingTimeInterval(lifetime)
        let framed = min(frameLimit, all.parts.prefix { $0.start < window }.count)
        let nextName = all.parts.indices.first { $0 >= framed && LiveSegments.isListed(all.parts[$0]) }
        let day: [NowActivityAttributes.DayPart] = all.parts.enumerated().map { i, p in
            i < framed
                ? .init(status: p.status, head: p.head, label: p.label, room: p.room, start: p.start, end: p.end,
                        accentARGB: argb(p.accent), symbol: p.symbol, listed: LiveSegments.isListed(p))
                : .init(status: "", head: "", label: i == nextName ? p.label : "", room: nil, start: p.start, end: p.end,
                        accentARGB: argb(p.accent), symbol: "", listed: LiveSegments.isListed(p))
        }
        return (past + day, framed, framed < all.parts.count || all.more, past.count)
    }

    private static func argb(_ a: Accent) -> UInt32 { Prefs.shared.widgetKindColors[a.storageName] ?? a.defaultARGB }

    /// [block] 이 '지금 구간'인 순간([time])의 현황 내용 — 현황이 [startedAt] 에 시작한다고 보고 그 뒤 12시간 안의 구간을 글자 구간으로.
    private static func content(blocks: [Block], segments: [LiveSegment], block: LiveSegment, at time: Date, startedAt: Date)
        -> ActivityContent<NowActivityAttributes.ContentState> {
        func segment(_ b: LiveSegment, at t: Date) -> NowActivityAttributes.Segment {
            let next = Schedule.nextEvent(blocks: blocks, current: b.anchor, time: max(t, b.anchor.start))
            return .init(status: b.status, title: b.title, room: b.room,
                         start: b.start, end: b.end, accentARGB: argb(b.accent),
                         symbol: b.symbol, next: next.map { n in [n.start.hm, n.title, n.room].compactMap { $0 }.joined(separator: " · ") })
        }
        let cur = segment(block, at: time)
        // 다음 구간 — 하루치(day)를 못 올린 경우에 끝나는 시각에 위젯이 스스로 한 번 넘어가게.
        let after = LiveSegments.segment(at: block.end, in: segments).flatMap { $0.isBlank || $0.start < block.end ? nil : segment($0, at: block.end) }
        var state = NowActivityAttributes.ContentState(
            status: cur.status, title: cur.title, room: cur.room, start: cur.start, end: cur.end,
            accentARGB: cur.accentARGB, symbol: cur.symbol, next: cur.next, then: after)
        // 지금부터 이어지는 구간을 한 번에 — 푸시가 없어 앱이 자는 동안엔 갱신할 수 없으므로, 화면이 구간들을 겹쳐 두고 제 시간인 것만
        // 보여 줍니다(NowLiveActivity 의 Gated). 예약 다운로드로 앱을 깨우는 방식은 기기에서 52분~4시간 늦어 뺐습니다.
        if let d = dayState(segments, now: max(time, block.start), startedAt: startedAt, argb: argb) {
            state.day = d.day; state.framed = d.framed; state.more = d.more; state.lead = d.lead
            // 현황 데이터는 4KB 한도 — 넘치면 지난 구간(막대 앞)부터 덜고, 그래도 넘치면 막대 끝에서부터 줄입니다(남은 일정이 더 중요).
            while let n = state.day?.count, n > 1, ((try? JSONEncoder().encode(state))?.count ?? 0) > 3600 {
                if let lead = state.lead, lead > 0 {
                    state.day?.removeFirst(); state.lead = lead - 1
                } else {
                    state.day?.removeLast()
                    state.framed = min(state.framed ?? n, n - 1); state.more = true
                }
            }
        }
        // 하루치가 있으면 글자 구간의 마지막 끝이, 없으면 이 구간 끝이 stale 시각(위젯이 then 으로 넘어가는 때).
        let lastFramed = state.day.flatMap { d in
            let ahead = d.dropFirst(state.lead ?? 0)
            return state.framed.map { ahead.prefix($0).last } ?? ahead.last
        }
        return ActivityContent(state: state, staleDate: lastFramed?.end ?? block.end, relevanceScore: 1)
    }

    /// 예약만 걸려 있고 아직 시작하지 않은 현황(iOS 26).
    private static func isPending(_ a: Activity<NowActivityAttributes>) -> Bool {
        if #available(iOS 26.0, *) { return a.activityState == .pending }
        return false
    }

    static func update(model: AppModel) {
        #if DEBUG
        if LiveDayTest.active { return }
        #endif
        let all = Activity<NowActivityAttributes>.activities
        guard LiveSchedule.shared.enabled, ActivityAuthorizationInfo().areActivitiesEnabled else {
            end(all)
            return
        }
        let now = Date()
        let known = (UserDefaults.standard.dictionary(forKey: scheduledKey) as? [String: Double]) ?? [:]
        let pending = all.filter(isPending)
        // 떠 있는 현황 — 둘 이상이면(어제 것이 아직 남았는데 오늘 예약분이 시작됨) 늦게 시작한 것이 앞.
        let activities = all.filter { !isPending($0) }.sorted {
            (known[$0.id] ?? 0, $0.content.state.start) > (known[$1.id] ?? 0, $1.content.state.start)
        }
        // 다음 하루 시작에 현황이 저절로 뜨도록 iOS 에 예약(iOS 26+).
        scheduleNext(model: model, now: now, all: all, pending: pending)
        // 자동 시작 시험 중([scheduleTestStart])에는 지금 현황을 띄우지 않아, 예약분이 저절로 뜨는 모습만 보이게 합니다.
        if UserDefaults.standard.double(forKey: testStartKey) > now.timeIntervalSince1970 { end(activities); return }
        // 하루는 8시부터(아침시간 뺌) — LiveSegments.trimMorning.
        let blocks = LiveSegments.trimMorning(model.blocks(for: model.today))
        // 블록타임(같은 과목 두 교시)은 한 구간으로 — LiveSegments.
        let segments = LiveSegments.build(blocks)
        let shown = segments.filter { !$0.isBlank }
        // 하루 시작 전(아침)에 앱을 열었을 때: iOS 26 부터는 시작 시각에 뜨도록 예약해 두므로 미리 띄우지 않습니다. 그 전 iOS 는 예약을
        // 못 해서, 90분 전부터 미리 띄워 둡니다(첫 구간이 '곧 시작'으로 보이고 막대는 비어 있음).
        let early: TimeInterval = canSchedule ? 0 : 90 * 60
        guard let first = shown.first?.start, let last = shown.last?.end, now >= first.addingTimeInterval(-early), now < last,
              let block = now < first ? shown.first : LiveSegments.segment(at: now, in: segments), !block.isBlank else {
            end(activities)
            return
        }
        // 현황은 시작한 지 8시간이면 iOS 가 끝냅니다. 앱이 앞에 있고 현황이 오래됐으면 새로 시작합니다.
        // 예약으로 뜬 현황이면 그 예약 시각이 시작 시각입니다.
        if let cur = activities.first, let epoch = known[cur.id] { UserDefaults.standard.set(epoch, forKey: startedKey) }
        let stored = UserDefaults.standard.double(forKey: startedKey)
        let foreground = UIApplication.shared.applicationState == .active
        let renew = !activities.isEmpty && foreground && (stored == 0 || now.timeIntervalSince1970 - stored > renewAfter)
        let startedAt = activities.isEmpty || renew || stored == 0 ? now : Date(timeIntervalSince1970: stored)

        let content = content(blocks: blocks, segments: segments, block: block, at: now, startedAt: startedAt)
        if let current = activities.first, !renew {
            // 혹시 둘 이상이면 하나만 남깁니다(낡은 현황이 화면에 남던 것).
            end(Array(activities.dropFirst()))
            guard current.content.state != content.state else { return }
            Task { await current.update(content) }
        } else {
            guard !restarting else { return }
            restarting = true
            Task { @MainActor in
                // 옛 현황을 끝낸 뒤에 새로 시작합니다(겹쳐 있으면 다음 갱신 때 어느 쪽이 남을지 알 수 없음).
                for a in activities { await a.end(nil, dismissalPolicy: .immediate) }
                if (try? Activity.request(attributes: NowActivityAttributes(), content: content)) != nil {
                    UserDefaults.standard.set(now.timeIntervalSince1970, forKey: startedKey)
                }
                restarting = false
            }
        }
    }

    // MARK: 예약 시작 (iOS 26+)

    /// 예약해 둔 현황의 시작 시각 — 현황 id → 시각(epoch). 예약분이 떴을 때 그 시작 시각을 알고, 바뀐 예약을 가려내는 데 씁니다.
    private static let scheduledKey = "live.scheduled"
    static let testStartKey = "debug.live.start"

    /// 예약 시작을 지금 쓸 수 있는지(iOS 26+, 실시간 일정 켜짐) — 시험 단추를 보일지에 씁니다.
    static var canTestScheduledStart: Bool { canSchedule && LiveSchedule.shared.enabled && ActivityAuthorizationInfo().areActivitiesEnabled }

    /// (시험용) [seconds] 뒤에 오늘 일과로 현황이 저절로 뜨게 예약합니다 — 알파 설정의 시험 단추와 개발용 실행 인자(-liveSchedIn)가 씁니다.
    /// 그때까지는 지금 현황을 내려 두므로, 앱을 끄고 기다리면 예약분이 뜨는 순간을 그대로 볼 수 있습니다.
    static func scheduleTestStart(after seconds: TimeInterval, model: AppModel) {
        UserDefaults.standard.set(Date().addingTimeInterval(seconds).timeIntervalSince1970, forKey: testStartKey)
        update(model: model)
    }
    private static var scheduling = false
    private static var canSchedule: Bool { if #available(iOS 26.0, *) { true } else { false } }
    /// 미리 걸어 두는 하루 시작 수 — 다가오는 사흘 치(정한 값). 금요일 저녁에 열면 토·일·월 아침까지 걸립니다.
    /// 한 앱의 현황은 예약분까지 합쳐 5개가 한도(시뮬레이터: 여섯 번째부터 targetMaximumExceeded)라, 떠 있는 현황과 새로 시작할 자리 둘을 남깁니다.
    private static let scheduleAhead = 3

    /**
     * 다음 하루 시작(평일 8:00, 주말·휴일 12:00)에 현황이 저절로 뜨도록 iOS 에 맡겨 둡니다(iOS 26 의 예약 시작).
     *
     * 시뮬레이터에서 확인한 것(2026-11-25): 예약 시각이 되면 앱이 완전히 꺼져 있어도 iOS 가 현황을 시작하고, 앱도 뒤에서 실행해 줍니다
     * (liveactivitiesd: Launched process for reason "Activity started"). 그때 이 함수가 다시 돌아 그다음 날 시작을 또 예약하므로,
     * 한 번 걸리면 앱을 열지 않아도 날마다 이어집니다. 예약에는 알림 문구가 꼭 필요합니다(시작할 때 한 번 뜸) — 소리는 무음 파일.
     */
    private static func scheduleNext(model: AppModel, now: Date, all: [Activity<NowActivityAttributes>], pending: [Activity<NowActivityAttributes>]) {
        // 예약은 앱이 화면에 있을 때만 받아 줍니다 — 뒤에서(예약분이 뜨면서 iOS 가 앱을 깨웠을 때) 하면 visibility 오류(시뮬레이터 확인).
        guard #available(iOS 26.0, *), !scheduling, UIApplication.shared.applicationState != .background else { return }
        var wanted: [(start: Date, content: ActivityContent<NowActivityAttributes.ContentState>, name: String)] = []
        #if DEBUG
        NSLog("LIVE schedDBG have pending=\(pending.count) other=\(all.count - pending.count)")
        #endif
        func add(_ date: LocalDate, at forced: Date? = nil) {
            let blocks = LiveSegments.trimMorning(model.blocks(for: date))
            let segments = LiveSegments.build(blocks)
            let shown = segments.filter { !$0.isBlank }
            guard let first = shown.first, let last = shown.last else { return }
            let start = forced ?? first.start
            guard start > now.addingTimeInterval(30), start < last.end,
                  let block = forced == nil ? first : LiveSegments.segment(at: start, in: segments), !block.isBlank else { return }
            wanted.append((start, content(blocks: blocks, segments: segments, block: block, at: start, startedAt: start), block.status))
        }
        // 자동 시작 시험([scheduleTestStart]) — 하루 시작 대신 그 시각에 오늘 일과로 예약합니다.
        let test = UserDefaults.standard.double(forKey: testStartKey)
        if test > now.timeIntervalSince1970 { add(model.today, at: Date(timeIntervalSince1970: test)) }
        var offset = 0
        var ahead = scheduleAhead
        #if DEBUG
        if let i = CommandLine.arguments.firstIndex(of: "-liveAhead"), i + 1 < CommandLine.arguments.count, let n = Int(CommandLine.arguments[i + 1]) { ahead = n }
        #endif
        while wanted.count < ahead, offset <= ahead + 3 { add(model.today.adding(days: offset)); offset += 1 }

        var known = (UserDefaults.standard.dictionary(forKey: scheduledKey) as? [String: Double]) ?? [:]
        // 이미 같은 시각·같은 내용으로 걸려 있는 예약은 그대로 둡니다.
        var missing = wanted
        var keep = Set<String>()
        for a in pending {
            guard let epoch = known[a.id],
                  let i = missing.firstIndex(where: { abs($0.start.timeIntervalSince1970 - epoch) < 1 && $0.content.state == a.content.state }) else { continue }
            keep.insert(a.id); missing.remove(at: i)
        }
        let stale = pending.filter { !keep.contains($0.id) }
        // 사라진 현황의 기록은 지웁니다(떠 있는 현황의 기록은 시작 시각을 알려고 남김).
        let alive = Set(all.map(\.id)).subtracting(stale.map(\.id))
        known = known.filter { alive.contains($0.key) }
        guard !stale.isEmpty || !missing.isEmpty else { UserDefaults.standard.set(known, forKey: scheduledKey); return }
        scheduling = true
        Task { @MainActor in
            for a in stale { await a.end(nil, dismissalPolicy: .immediate) }
            for w in missing {
                do {
                    let a = try Activity.request(
                        attributes: NowActivityAttributes(), content: w.content, style: .standard,
                        alertConfiguration: AlertConfiguration(title: "오늘 일과", body: "\(w.name)부터 실시간 일정을 보여 드려요", sound: .named("live-silent.caf")),
                        start: w.start)
                    known[a.id] = w.start.timeIntervalSince1970
                    NSLog("LIVE scheduled \(w.name) at \(w.start) state=\(String(describing: a.activityState)) bg=\(UIApplication.shared.applicationState != .active)")
                } catch {
                    NSLog("LIVE schedule failed at \(w.start): \(error)")
                }
            }
            UserDefaults.standard.set(known, forKey: scheduledKey)
            scheduling = false
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
