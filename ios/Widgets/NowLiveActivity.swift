import SwiftUI
import WidgetKit
import ActivityKit

/*
 * 실시간 활동 — 잠금 화면 배너와 Dynamic Island.
 *
 * 푸시(유료 개발자 계정)가 없어서, 앱이 자는 동안에는 현황을 갱신할 수 없습니다. iOS 가 스스로 움직여 주는 것은 남은 시간 숫자와
 * 진행 막대뿐입니다. 그래서 앱이 켜져 있을 때 지금부터 이어지는 구간을 한 번에 올려 두고(ContentState.day), 화면은 구간별 내용을
 * 한자리에 겹쳐 그린 뒤 '시간 가림막'으로 제 시간인 구간만 보이게 합니다(Gated). 가림막은 진행 막대를 크게 늘린 것 —
 * 그 시각 전엔 비어 있고 뒤엔 꽉 차 있어, 정한 시각에 덮이거나 걷히는 칸이 됩니다. 덮인 곳의 내용을 지워(destinationOut) 투명하게 하므로
 * 배경(잠금 화면의 유리)은 그대로 보입니다.
 *
 * 지켜야 할 것(시뮬레이터에서 잰 값, 2026-11-25):
 *  - 투명도 마스크(mask + luminanceToAlpha)는 현황에서 먹지 않습니다. 지우기(destinationOut)는 됩니다.
 *  - 진행 막대의 옅은 바탕(트랙, 불투명도 약 0.25)도 내용을 조금 지워 글자가 흐려집니다(가림막 둘이면 약 60%). 가림막을 자기 자신으로
 *    두 번 오려 내면(mask) 트랙이 0.25³ 으로 사라져 글자가 제 밝기(254/255)로 나옵니다.
 *  - 곱하기+더하기(multiply·plusLighter) 방식은 배경이 검정일 때만 됩니다(유리 위에선 검은 띠). contrast·묶음 compositingGroup 은 가림을 깨뜨립니다.
 *  - 현황의 그리기 데이터에는 잠금 화면 8벌(밝음·어두움 × 화면 어둡게 × 내용 가림)과 아일랜드 여러 벌이 한 파일에 들어가고, 너무 크면
 *    iOS 가 현황을 그리지 않습니다(2.30MB 통과, 2.71MB 거절). 그래서 구간 수를 묶고(LiveActivityController.frameLimit), 보이지 않는다고 본
 *    벌(아일랜드의 잠금·어두움, 잠금 화면의 내용 가림)에는 가림막 없이 그립니다. 잠금 화면은 남은 시간 같은 움직이는 글자가 없으면
 *    다시 그리지 않으므로 보이지 않는 1초 시계(Heartbeat)를 둡니다.
 */
struct NowLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: NowActivityAttributes.self) { context in
            if let frames = LiveFrame.build(context.state) {
                DayLockView(frames: frames, day: context.state.day ?? [])
                    .activityBackgroundTint(Color.black.opacity(0.35))
                    .activitySystemActionForegroundColor(.white)
            } else {
                LockScreenView(state: context.state.shown(stale: context.isStale))
                    .activityBackgroundTint(Color.black.opacity(0.35))
                    .activitySystemActionForegroundColor(.white)
            }
        } dynamicIsland: { context in
            let s = context.state.shown(stale: context.isStale)
            let accent = Color(argb: s.accentARGB)
            let frames = LiveFrame.build(context.state)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    if let frames {
                        IslandGate(frames: frames, width: 132, height: 20, alignment: .leading) { f in
                            Label(f.status, systemImage: f.symbol).font(.caption.weight(.semibold)).foregroundStyle(f.accent).lineLimit(1)
                        } plain: {
                            Label("오늘 일과", systemImage: "calendar").font(.caption.weight(.semibold)).foregroundStyle(.white.opacity(0.7))
                        }
                    } else {
                        Label(s.status, systemImage: s.symbol).font(.caption.weight(.semibold)).foregroundStyle(accent).lineLimit(1)
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if let frames {
                        // 타이머 숫자는 칸보다 살짝 넓게 그려져, 칸에 딱 맞추면 가림막이 마지막 숫자 오른쪽을 잘랐습니다 — 칸을 넓히고 오른쪽 여유.
                        IslandGate(frames: frames, width: 104, height: 28, alignment: .trailing) { f in
                            f.countdown.font(.title3.weight(.semibold).monospacedDigit()).foregroundStyle(f.accent).multilineTextAlignment(.trailing)
                                .padding(.trailing, 6)
                        } plain: { EmptyView() }
                    } else {
                        Text(s.end, style: .timer).font(.title3.weight(.semibold).monospacedDigit())
                            .foregroundStyle(accent).multilineTextAlignment(.trailing).frame(maxWidth: 80)
                    }
                }
                DynamicIslandExpandedRegion(.center) {
                    if frames == nil { Text(s.title).font(.headline).lineLimit(1) }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if let frames {
                        DayExpandedBottom(frames: frames, day: context.state.day ?? [])
                    } else {
                        VStack(alignment: .leading, spacing: 6) {
                            ProgressView(timerInterval: s.start...s.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }.tint(accent)
                            HStack {
                                if let r = s.room { Text(r).lineLimit(1) }
                                Spacer()
                                if let n = s.next { Text("다음 · " + n).lineLimit(1) }
                            }
                            .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            } compactLeading: {
                if let frames {
                    IslandGate(frames: frames, width: 24, height: 22, alignment: .center) { f in
                        Image(systemName: f.symbol).foregroundStyle(f.accent)
                    } plain: {
                        Image(systemName: "calendar").foregroundStyle(.white.opacity(0.7))
                    }
                } else {
                    Image(systemName: s.symbol).foregroundStyle(accent)
                }
            } compactTrailing: {
                if let frames {
                    IslandGate(frames: frames, width: 60, height: 24, alignment: .trailing) { f in
                        f.countdown.monospacedDigit().foregroundStyle(f.accent).multilineTextAlignment(.trailing)
                            .padding(.trailing, 4)
                    } plain: { EmptyView() }
                } else {
                    Text(s.end, style: .timer).monospacedDigit().foregroundStyle(accent).frame(maxWidth: 52)
                }
            } minimal: {
                if frames != nil, let day = context.state.day, let a = day.first, let b = day.last {
                    // 다른 앱의 현황과 함께 떠 있을 때의 작은 동그라미 — 올려 둔 구간 전체의 진행(가림막 없이: 그리기 데이터 한도).
                    ProgressView(timerInterval: a.start...b.end, countsDown: false) { EmptyView() } currentValueLabel: {
                        Image(systemName: "calendar").font(.caption2)
                    }
                    .progressViewStyle(.circular).tint(.white)
                } else {
                    ProgressView(timerInterval: s.start...s.end, countsDown: true) { EmptyView() } currentValueLabel: {
                        Image(systemName: s.symbol).font(.caption2)
                    }
                    .progressViewStyle(.circular).tint(accent)
                }
            }
            .widgetURL(URL(string: "hihananeo://home"))
            .keylineTint(accent)
        }
    }
}

// MARK: - 시간 가림막

/// 정한 시각에 왼쪽에서 오른쪽으로 덮이거나(appears) 걷히는 불투명 칸 — 놓인 자리를 꽉 채웁니다(부모가 잘라 냄).
///
/// 진행 막대를 가로 20배로 늘려 그 앞쪽 5%만 쓰고, 10초짜리 구간의 처음 0.5초에 그 5%를 지나가게 합니다. 잠금 화면에서는 진행 막대가
/// 매 순간이 아니라 띄엄띄엄 다시 그려져서, 1초짜리 막대는 다 차기 전 모습으로 몇 초 멈춰 있는 일이 있었습니다(시뮬레이터, 2026-11-25).
/// 이렇게 하면 0.5초 뒤의 어느 순간에 그려져도 '다 덮임 / 다 걷힘'이라 틀린 모습이 남지 않습니다.
private struct TimeSwitch: View {
    /// 다 덮이거나 다 걷히는 시각.
    let at: Date
    let appears: Bool
    private static let stretch: CGFloat = 20
    /// 칸을 한 번 쓸고 지나가는 데 걸리는 시간(정한 값).
    static let sweep: TimeInterval = 0.8

    var body: some View {
        bar.mask { bar.mask { bar } }        // 옅은 바탕(트랙)을 0.25³ 으로 — 남겨야 할 내용을 지우지 않게
    }

    private var bar: some View {
        ProgressView(timerInterval: at.addingTimeInterval(-Self.sweep)...at.addingTimeInterval(Self.sweep * (Self.stretch - 1)), countsDown: !appears) { EmptyView() } currentValueLabel: { EmptyView() }
            .progressViewStyle(.linear)
            .tint(.black)
            // 덮는 막대는 왼쪽 끝을 붙잡고 늘립니다(채워진 부분이 왼→오로 자람). 걷히는 막대는 좌우를 뒤집어, 남은 부분이 오른쪽에 붙은 채
            // 왼→오로 물러나게 — 앞 구간이 덮이는 선과 다음 구간이 드러나는 선이 겹쳐 한 줄로 쓸려 바뀝니다. 세로는 막대 두께(약 4pt)의
            // 200배라, 둥근 끝이 칸 모서리를 덜 덮는 일이 없습니다.
            .scaleEffect(x: appears ? Self.stretch : -Self.stretch, y: 200,
                         anchor: appears ? .leading : UnitPoint(x: Self.stretch / (Self.stretch + 1), y: 0.5))
    }
}

/// [from, to) 동안만 내용이 보이게 — 그 밖의 시간에는 가림막이 덮은 곳을 지워 투명.
private struct Gated<Content: View>: View {
    let from: Date?
    let to: Date?
    @ViewBuilder var content: Content

    var body: some View {
        content
            .overlay {
                ZStack {
                    if let from { TimeSwitch(at: from, appears: false) }
                    if let to { TimeSwitch(at: to, appears: true) }
                }
                // 가림막을 칸보다 넉넉히 키운 뒤 흐리게 — 쓸고 가는 경계가 부드러운 띠가 되어, 이전 내용은 녹듯 사라지고 다음 내용은
                // 녹듯 나타납니다. 넉넉히 키우지 않으면 칸 가장자리에서 가림막이 옅어져 가려진 글자가 비칩니다.
                .padding(-28)
                .blur(radius: 10)
                .blendMode(.destinationOut)
            }
            .clipped()
            .compositingGroup()
    }
}

/// 구간들을 한자리에 겹쳐, 제 시간인 것만 보이게.
private struct GateStack<Content: View>: View {
    let frames: [LiveFrame]
    var alignment: Alignment = .center
    @ViewBuilder var content: (LiveFrame) -> Content

    var body: some View {
        ZStack(alignment: alignment) {
            ForEach(Array(frames.enumerated()), id: \.offset) { _, f in
                Gated(from: f.from, to: f.to) { content(f) }
            }
        }
    }
}

/// 보이지 않는 1초 시계 — 잠금 화면이 현황을 1초마다 다시 그리게 해서, 가림막이 중간 모습으로 멈춰 있지 않게 합니다
/// (남은 시간 글자가 보이는 동안에는 그것이 같은 일을 하지만, 마지막 안내나 '화면 어둡게'에는 움직이는 글자가 없습니다).
private struct Heartbeat: View {
    let from: Date
    let until: Date
    var body: some View {
        // 시작 시각이 있는 타이머(timerInterval)는 미리 올린 현황에서 시작 전 모습으로 멈춥니다(아래 countdown 참고) — 끝 시각만 받는 타이머로.
        Text(until, style: .timer)
            .font(.system(size: 1)).foregroundStyle(.white.opacity(0.02)).frame(width: 1, height: 1).clipped()
            .accessibilityHidden(true)
    }
}

// MARK: - 구간(화면용)

/// 화면에 겹쳐 둘 구간 하나 — 앞 구간이 끝난 때부터 이 구간이 끝날 때까지 보입니다.
struct LiveFrame {
    var from: Date?
    var to: Date?
    var status: String
    var head: String
    var room: String?
    var start: Date
    var end: Date
    var accent: Color
    var symbol: String
    /// "다음 · 13:10 · 5–6교시 물리학Ⅱ · 과학동 2" — 수업·면학 중일 때 아래 한 줄.
    var next: String?
    /// 펼친 아일랜드의 둘째 줄 — "B301 · 다음 3–4교시 영어Ⅱ". 아일랜드는 한 줄만(삼성 Now Bar 처럼 지금 것 위주로).
    var detail: String?
    /// 마지막 구간이 끝난 뒤의 안내(남은 시간이 없음).
    var isTail = false

    /// 남은 시간 — 끝 시각까지 남은 만큼을 바로 셉니다(구간이 보이기 전·끝난 뒤의 값은 가림막에 가려 보이지 않음).
    /// 예전엔 Text(timerInterval: start...end) 였는데, 전날 밤에 다음 날 아침 시작으로 예약해 둔 현황에서는 iPad 실기기가 이 숫자를
    /// '아직 시작 안 함(전체 길이)'으로 그린 채 시작 시각이 지나도 다시 세지 않았습니다(2026-11-26, 08:33 에 '1:50:00' 그대로).
    /// 같은 현황의 가림막·막대(ProgressView(timerInterval:))는 제때 움직였고, 시뮬레이터의 45초 앞 예약에서는 재현되지 않았습니다.
    var countdown: Text { isTail ? Text("") : Text(end, style: .timer) }

    static func build(_ state: NowActivityAttributes.ContentState) -> [LiveFrame]? {
        // 맨 앞의 지난 구간(lead)은 막대에만 — 글자 구간은 그 뒤부터.
        guard let whole = state.day else { return nil }
        let day = Array(whole.dropFirst(min(max(0, state.lead ?? 0), whole.count)))
        guard !day.isEmpty else { return nil }
        // 앞의 framed 개만 글자 구간 — 나머지는 막대에만(다음 일정 이름은 그쪽에서도 찾음).
        let shown = day.prefix(max(1, min(state.framed ?? day.count, day.count)))
        var out: [LiveFrame] = []
        for (i, p) in shown.enumerated() {
            let later = day[(i + 1)...].filter(\.listed)
            let nextListed = later.first
            var f = LiveFrame(from: i == 0 ? nil : day[i - 1].end, to: p.end, status: p.status, head: p.head, room: p.room,
                              start: p.start, end: p.end, accent: Color(argb: p.accentARGB), symbol: p.symbol)
            if p.head.isEmpty {
                // 쉬는 시간·식사 — 다음 일정을 큰 글자로.
                if let n = nextListed { f.head = "다음 · " + n.label; f.room = n.room } else { f.head = p.status }
            } else if let n = nextListed {
                f.next = "다음 · " + [n.start.hm, n.label, n.room].compactMap { $0 }.joined(separator: " · ")
            }
            // 쉬는 시간·식사는 큰 글자가 이미 다음 일정이라 교실만, 수업·면학은 교실 + 다음 일정 이름.
            f.detail = p.head.isEmpty ? f.room : [p.room, nextListed.map { "다음 " + $0.label }].compactMap { $0 }.joined(separator: "  ·  ")
            out.append(f)
        }
        if let last = shown.last {
            let more = state.more == true
            out.append(LiveFrame(from: last.end, to: nil, status: more ? "HiHana Evo" : "일과 종료",
                                 head: more ? "앱을 열면 다음 일정이 이어져요" : "오늘 일과가 끝났어요", room: nil,
                                 start: last.end, end: last.end, accent: .white.opacity(0.7), symbol: more ? "arrow.clockwise" : "moon.stars", isTail: true))
        }
        return out
    }
}

extension NowActivityAttributes.ContentState {
    /// 하루 목록(막대)에 올리는 구간.
    var listed: [NowActivityAttributes.DayPart] { (day ?? []).filter(\.listed) }
}

/// 아일랜드의 한 자리 — 실제로 보이는 한 벌(화면 밝음·내용 가림 없음)에만 구간을 겹쳐 가림막을 씌우고, 나머지 벌(잠긴 화면·항상 켜짐에서는
/// 아일랜드에 현황이 뜨지 않는다고 보고)에는 시간과 무관한 [plain] 만 둡니다(그리기 데이터를 줄이려고).
private struct IslandGate<Content: View, Plain: View>: View {
    let frames: [LiveFrame]
    let width: CGFloat
    let height: CGFloat
    let alignment: Alignment
    @ViewBuilder var content: (LiveFrame) -> Content
    @ViewBuilder var plain: () -> Plain
    @Environment(\.isLuminanceReduced) private var dim
    @Environment(\.redactionReasons) private var redaction

    var body: some View {
        if dim || !redaction.isEmpty {
            plain().frame(width: width, height: height, alignment: alignment)
        } else {
            GateStack(frames: frames) { f in
                content(f).frame(width: width, height: height, alignment: alignment)
            }
            .frame(width: width, height: height)
        }
    }
}

// MARK: - 하루 막대

/// 올려 둔 구간 전체를 시간에 비례한 막대 하나로 — 10분 쉬는 시간은 좁게, 1시간 점심은 넓게. 칸마다 스스로 차오르므로 지난 칸은 꽉,
/// 지금 칸은 차오르는 중, 남은 칸은 비어 있습니다. 쉬는 시간·휴식처럼 목록에 안 오르는 칸은 옅은 회색.
private struct TimeBar: View {
    let day: [NowActivityAttributes.DayPart]
    var times = true
    private static let gap: CGFloat = 2

    var body: some View {
        if let first = day.first, let last = day.last, last.end > first.start {
            let total = last.end.timeIntervalSince(first.start)
            VStack(spacing: 4) {
                GeometryReader { g in
                    let usable = max(0, g.size.width - Self.gap * CGFloat(day.count - 1))
                    HStack(spacing: Self.gap) {
                        ForEach(Array(day.enumerated()), id: \.offset) { _, p in
                            ProgressView(timerInterval: p.start...p.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                                .progressViewStyle(.linear)
                                .tint(p.listed ? Color(argb: p.accentARGB) : .white.opacity(0.45))
                                .frame(width: max(1, usable * CGFloat(p.end.timeIntervalSince(p.start) / total)))
                        }
                    }
                }
                .frame(height: 6)
                if times {
                    HStack {
                        Text(first.start.hm)
                        Spacer()
                        Text(last.end.hm)
                    }
                    .font(.system(size: 10, weight: .medium).monospacedDigit())
                    .foregroundStyle(.white.opacity(0.55))
                }
            }
        }
    }
}

// MARK: - 잠금 화면

private struct DayLockView: View {
    let frames: [LiveFrame]
    let day: [NowActivityAttributes.DayPart]
    @Environment(\.isLuminanceReduced) private var dim
    @Environment(\.redactionReasons) private var redaction

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if redaction.isEmpty {
                GateStack(frames: frames, alignment: .topLeading) { f in
                    header(f).frame(maxWidth: .infinity, minHeight: 78, maxHeight: 78, alignment: .topLeading)
                }
            } else {
                // 내용을 가린 잠금 화면 — 무엇을 하는 시간인지는 숨기고 하루 막대만(그리기 데이터도 줄임).
                Label("오늘 일과", systemImage: "calendar").font(.headline).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 78, maxHeight: 78, alignment: .topLeading)
            }
            if day.count > 1 { TimeBar(day: day) }
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .background(alignment: .topLeading) { if let first = frames.first, let end = frames.last?.start { Heartbeat(from: first.start, until: end) } }
    }

    private func header(_ f: LiveFrame) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: f.symbol).font(.title3.weight(.semibold)).foregroundStyle(.white)
                    .frame(width: 40, height: 40).background(f.isTail ? Color.white.opacity(0.18) : f.accent, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(f.status).font(.caption.weight(.semibold)).foregroundStyle(f.accent).lineLimit(1)
                    Text(f.head).font(.headline).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.75)
                    if let r = f.room { Text(r).font(.caption).foregroundStyle(.white.opacity(0.75)).lineLimit(1) }
                }
                Spacer(minLength: 8)
                if !f.isTail {
                    VStack(alignment: .trailing, spacing: 2) {
                        // 항상 켜짐(화면 어둡게)에서는 숫자가 1분에 한 번만 바뀌므로 끝나는 시각만 — 그리기 데이터도 그만큼 줄어듭니다.
                        if dim {
                            Text(f.end.hm).font(.title2.weight(.semibold).monospacedDigit()).foregroundStyle(.white)
                            Text("까지").font(.caption2).foregroundStyle(.white.opacity(0.7))
                        } else {
                            f.countdown.font(.title2.weight(.semibold).monospacedDigit())
                                .foregroundStyle(.white).multilineTextAlignment(.trailing).frame(maxWidth: 100)
                            Text("\(f.end.hm)까지").font(.caption2).foregroundStyle(.white.opacity(0.7))
                        }
                    }
                }
            }
            if let n = f.next { Text(n).font(.caption).foregroundStyle(.white.opacity(0.75)).lineLimit(1) }
        }
    }
}

// MARK: - 펼친 아일랜드 아래쪽

private struct DayExpandedBottom: View {
    let frames: [LiveFrame]
    let day: [NowActivityAttributes.DayPart]
    @Environment(\.isLuminanceReduced) private var dim
    @Environment(\.redactionReasons) private var redaction

    var body: some View {
        if dim || !redaction.isEmpty {
            // 보이지 않는다고 본 벌 — 올려 둔 구간 전체의 진행 막대 하나만.
            if let a = day.first, let b = day.last {
                ProgressView(timerInterval: a.start...b.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                    .progressViewStyle(.linear).tint(.white)
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                GateStack(frames: frames, alignment: .topLeading) { f in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(f.head).font(.headline).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.75)
                        if let d = f.detail, !d.isEmpty {
                            Text(d).font(.caption).foregroundStyle(.white.opacity(0.65)).lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 40, maxHeight: 40, alignment: .topLeading)
                }
                // 아일랜드 아래 모서리가 둥글어 양 끝 막대가 잘리지 않게 안쪽으로.
                if day.count > 1 { TimeBar(day: day, times: false).padding(.horizontal, 14).padding(.bottom, 4) }
            }
        }
    }
}

// MARK: - 하루치를 못 받았을 때(예전 방식: 지금 + 다음 한 구간)

private struct LockScreenView: View {
    let state: NowActivityAttributes.ContentState

    var body: some View {
        let accent = Color(argb: state.accentARGB)
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: state.symbol).font(.title3.weight(.semibold)).foregroundStyle(.white)
                    .frame(width: 40, height: 40).background(accent, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(state.status).font(.caption.weight(.semibold)).foregroundStyle(accent).lineLimit(1)
                    Text(state.title).font(.headline).foregroundStyle(.white).lineLimit(1)
                    if let r = state.room { Text(r).font(.caption).foregroundStyle(.white.opacity(0.75)).lineLimit(1) }
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(state.end, style: .timer).font(.title2.weight(.semibold).monospacedDigit())
                        .foregroundStyle(.white).multilineTextAlignment(.trailing).frame(maxWidth: 100)
                    Text("\(state.end.hm)까지").font(.caption2).foregroundStyle(.white.opacity(0.7))
                }
            }
            ProgressView(timerInterval: state.start...state.end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }.tint(accent)
            if let n = state.next { Text("다음 · " + n).font(.caption).foregroundStyle(.white.opacity(0.75)).lineLimit(1) }
        }
        .padding(16)
    }
}

@main
struct HiHanaEvoWidgets: WidgetBundle {
    var body: some Widget {
        NowWidget()
        NowLiveActivity()
    }
}
