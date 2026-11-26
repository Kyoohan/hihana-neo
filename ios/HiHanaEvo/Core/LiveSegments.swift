import Foundation

/// 실시간 현황(잠금 화면·Dynamic Island)에 올릴 구간 — 일과 블록을 그대로 쓰되, 블록타임(같은 과목·같은 교실이 쉬는 시간만
/// 두고 두 교시 이어지는 수업)은 쉬는 시간까지 묶어 한 구간으로 봅니다. 3학점 과목처럼 한 교시는 수업·한 교시는 공강이면
/// 묶지 않습니다. 구간이 길수록 푸시 없이도 화면이 맞는 시간이 길고, 앱을 깨워야 하는 횟수도 줄어듭니다.
struct LiveSegment: Equatable {
    var status: String
    var title: String
    /// 큰 글자로 보일 이름 — 수업은 과목, 면학은 위치. 쉬는 시간·식사는 비워 둡니다(화면이 '다음 · …'으로 채움).
    var head: String = ""
    /// 하루 목록에 쓰는 짧은 이름 — "3–4교시 영어Ⅱ", "점심시간", "1타임".
    var label: String = ""
    var room: String?
    var start: Date
    var end: Date
    var accent: Accent
    var symbol: String
    var isGap: Bool
    var isBlank: Bool
    /// '다음 일정'을 셀 때 기준이 되는 블록(묶였으면 마지막 교시).
    var anchor: Block
}

enum LiveSegments {
    static func build(_ blocks: [Block]) -> [LiveSegment] {
        var out: [LiveSegment] = []
        var i = 0
        while i < blocks.count {
            let b = blocks[i]
            guard case .lesson(let firstPeriod, let lesson) = b.kind, !lesson.isFree else {
                out.append(plain(b)); i += 1; continue
            }
            // 수업 → (쉬는 시간) → 같은 수업 이 이어지는 동안 계속 묶습니다.
            var last = b, lastPeriod = firstPeriod, j = i
            while j + 2 < blocks.count, blocks[j + 1].isGap, blocks[j + 1].start == last.end, blocks[j + 2].start == blocks[j + 1].end,
                  Schedule.sameLesson(last, blocks[j + 2]), case .lesson(let p, _) = blocks[j + 2].kind {
                last = blocks[j + 2]; lastPeriod = p; j += 2
            }
            if j == i {
                out.append(plain(b))
            } else {
                let status = "\(firstPeriod)–\(lastPeriod)교시 수업 중"
                let span = "\(firstPeriod)–\(lastPeriod)교시"
                out.append(LiveSegment(status: status, title: lesson.title.isEmpty ? status : "\(status) \(lesson.title)",
                                       head: lesson.title.isEmpty ? status : lesson.title, label: lesson.title.isEmpty ? span : "\(span) \(lesson.title)", room: b.room,
                                       start: b.start, end: last.end, accent: b.accent, symbol: b.symbol, isGap: false, isBlank: false, anchor: last))
            }
            i = j + 1
        }
        return out
    }

    private static func plain(_ b: Block) -> LiveSegment {
        var head = "", label = b.statusLabel
        switch b.kind {
        case .lesson(let p, let l):
            head = l.isFree ? "공강" : (l.title.isEmpty ? b.statusLabel : l.title)
            label = l.isFree ? "\(p)교시 공강" : (l.title.isEmpty ? "\(p)교시" : "\(p)교시 \(l.title)")
        case .study, .outing: head = b.title
        case .gap, .blank: break
        }
        return LiveSegment(status: b.statusLabel, title: b.isGap ? b.statusLabel : b.headline, head: head, label: label, room: b.room, start: b.start, end: b.end,
                           accent: b.accent, symbol: b.symbol, isGap: b.isGap, isBlank: b.isBlank, anchor: b)
    }

    /// 하루 목록(막대)에 올리는 구간인지 — 쉬는 시간·휴식·일과 종료 같은 '사이 시간'은 올리지 않습니다.
    static func isListed(_ s: LiveSegment) -> Bool {
        !s.isBlank && !(s.isGap && (s.accent == .breakTime || s.accent == .idle))
    }

    /// 실시간 현황에 한 번에 올릴 구간 — [from] 이 속한 구간부터 빈 시간(일과 밖)이 나오기 전까지 이어지는 구간을 그대로(쉬는 시간 포함),
    /// [until] 전에 시작하는 것만, 많아도 [limit] 개. 화면은 이 구간들을 겹쳐 두고 제 시간인 것만 보여 줍니다. more 는 뒤가 잘렸는지.
    static func frames(_ segments: [LiveSegment], from: Date, until: Date, limit: Int) -> (parts: [LiveSegment], more: Bool) {
        guard let i = segments.firstIndex(where: { $0.start <= from && from < $0.end && !$0.isBlank }) else { return ([], false) }
        var out: [LiveSegment] = []
        var j = i
        while j < segments.count, !segments[j].isBlank, segments[j].start < until, out.count < limit {
            // 이어지지 않으면(사이가 비면) 거기서 끊습니다.
            if let last = out.last, segments[j].start != last.end { break }
            out.append(segments[j]); j += 1
        }
        let more = j < segments.count && !segments[j].isBlank
        return (out, more)
    }

    /// 하루를 한 번에 올릴 때의 구간 — 블록타임으로 묶은 뒤, 쉬는 시간·휴식·일과 종료처럼 '사이 시간'은 뺍니다(구간이 너무 많아
    /// 한눈에 안 들어오던 것). 식사·조회·면학·외출은 남깁니다. 구간 사이에 빈틈이 생기는 것이 정상입니다(그 틈이 쉬는 시간).
    static func dayParts(_ segments: [LiveSegment]) -> [LiveSegment] {
        segments.filter(isListed)
    }

    /// 실시간 현황 하나가 살아 있는 동안([from] 부터 [hours] 시간 — iOS 는 현황을 최대 8시간 유지)에 걸치는 구간만, 많아도 [limit] 개.
    static func dayParts(_ segments: [LiveSegment], from: Date, hours: Double = 8, limit: Int = 8) -> [LiveSegment] {
        let until = from.addingTimeInterval(hours * 3600)
        return Array(dayParts(segments).filter { $0.end > from && $0.start < until }.prefix(limit))
    }

    /// 실시간 현황의 하루 시작(2026-11-25 사용자 요청) — 평일은 8시(등교 및 조회)부터, 아침시간은 뺍니다. 주말·휴일은 12시부터로,
    /// 12:00~13:30(1타임 전)을 점심시간으로 보여 줍니다. 그 밖에 8시 전에 시작하는 '수업 없음'은 8시부터. 홈·잠금 화면 위젯은 그대로.
    static let dayStartMinutes = 8 * 60
    static let restDayStartMinutes = 12 * 60
    static let restDayFirstSessionMinutes = 13 * 60 + 30

    static func trimMorning(_ blocks: [Block]) -> [Block] {
        var out: [Block] = []
        for b in blocks {
            guard case .gap(let g) = b.kind else { out.append(b); continue }
            let eight = b.start.localDate.at(minutes: dayStartMinutes)
            if g.accent == .breakfast {
                out.append(Block(kind: .blank, start: b.start, end: b.end))
            } else if g.accent == .idle, b.end == b.start.localDate.at(minutes: restDayFirstSessionMinutes),
                      b.start < b.start.localDate.at(minutes: restDayStartMinutes) {
                // 주말·휴일 1타임 전 '수업 없음' → 12시까지 빈 화면, 12:00~13:30 점심시간.
                let noon = b.start.localDate.at(minutes: restDayStartMinutes)
                var lunch = g; lunch.label = "점심시간"; lunch.fallbackTitle = "점심시간"; lunch.accent = .lunch
                out.append(Block(kind: .blank, start: b.start, end: noon))
                out.append(Block(kind: .gap(lunch), start: noon, end: b.end))
            } else if g.accent == .idle, b.start < eight, b.end > eight {
                out.append(Block(kind: .blank, start: b.start, end: eight))
                out.append(Block(kind: b.kind, start: eight, end: b.end))
            } else {
                out.append(b)
            }
        }
        return out
    }

    static func segment(at time: Date, in segments: [LiveSegment]) -> LiveSegment? {
        segments.first { $0.start <= time && time < $0.end }
    }
}
