package com.skh.hihanaevo

import java.time.Duration
import java.time.LocalDateTime

/**
 * 실시간 일정(Now Bar)의 하루 — iOS 실시간 현황(LiveSegments)과 같은 규칙입니다.
 *
 * - 블록타임(같은 과목·같은 교실이 쉬는 시간만 두고 이어지는 수업)은 쉬는 시간까지 묶어 한 구간으로 봅니다
 *   ("1–2교시 수업 중"). 3학점 과목처럼 한 교시는 수업·한 교시는 공강이면 묶지 않습니다.
 * - 하루 막대: 그날 첫 일정부터 마지막 일정(2타임·심면)까지를 시간에 비례한 칸으로 — 10분 쉬는 시간은 좁게, 1시간 점심은 넓게.
 */
internal class LiveSeg(val first: Block, val last: Block, val firstPeriod: Int? = null, val lastPeriod: Int? = null) {
    val start: LocalDateTime get() = first.start
    val end: LocalDateTime get() = last.end
    val merged: Boolean get() = first !== last
    val accent: Accent get() = first.accent
    val isBlank: Boolean get() = first.isBlank

    /** "1–2교시 수업 중" 처럼 — 묶이지 않았으면 블록 그대로. */
    val statusLabel: String get() = if (merged && firstPeriod != null && lastPeriod != null) "$firstPeriod–${lastPeriod}교시 수업 중" else first.statusLabel
}

internal object LiveDay {

    /**
     * 실시간 일정의 하루 시작(2026-11-25 사용자 요청) — 평일은 8시(등교 및 조회)부터, 아침시간은 뺍니다. 주말·휴일은 12시부터로,
     * 12:00~13:30(1타임 전)을 점심시간으로 보여 줍니다. 그 밖에 8시 전에 시작하는 '수업 없음'은 8시부터. 위젯은 그대로.
     */
    fun trimMorning(blocks: List<Block>): List<Block> = blocks.flatMap { b ->
        val kind = b.kind as? BlockKind.GapKind ?: return@flatMap listOf(b)
        val eight = b.start.toLocalDate().atTime(8, 0)
        val noon = b.start.toLocalDate().atTime(12, 0)
        when {
            kind.gap.accent == Accent.BREAKFAST -> listOf(Block(BlockKind.BlankKind, b.start, b.end))
            // 주말·휴일 1타임(13:30) 전 '수업 없음' → 12시까지 빈 화면, 12:00~13:30 점심시간.
            kind.gap.accent == Accent.IDLE && b.end == b.start.toLocalDate().atTime(13, 30) && b.start.isBefore(noon) ->
                listOf(
                    Block(BlockKind.BlankKind, b.start, noon),
                    Block(BlockKind.GapKind(kind.gap.copy(label = "점심시간", fallbackTitle = "점심시간", accent = Accent.LUNCH)), noon, b.end),
                )
            kind.gap.accent == Accent.IDLE && b.start.isBefore(eight) && b.end.isAfter(eight) ->
                listOf(Block(BlockKind.BlankKind, b.start, eight), Block(b.kind, eight, b.end))
            else -> listOf(b)
        }
    }

    /** 블록 → 구간(블록타임은 한 구간). */
    fun segments(blocks: List<Block>): List<LiveSeg> {
        val out = mutableListOf<LiveSeg>()
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            val kind = b.kind as? BlockKind.LessonKind
            if (kind == null || kind.lesson.isFree) { out += LiveSeg(b, b); i++; continue }
            var last = b
            var lastPeriod = kind.period
            var j = i
            while (j + 2 < blocks.size) {
                val gap = blocks[j + 1]
                val nextLesson = blocks[j + 2]
                val nk = nextLesson.kind as? BlockKind.LessonKind ?: break
                if (gap.kind !is BlockKind.GapKind || gap.start != last.end || nextLesson.start != gap.end) break
                if (nk.lesson.isFree || nk.lesson.subject != kind.lesson.subject || nk.lesson.room != kind.lesson.room) break
                last = nextLesson; lastPeriod = nk.period; j += 2
            }
            out += if (j == i) LiveSeg(b, b) else LiveSeg(b, last, kind.period, lastPeriod)
            i = j + 1
        }
        return out
    }

    /** 칸의 구실 — 색은 이것만으로 정합니다(일정 종류별 색을 쓰면 어디까지 지났는지가 안 보였습니다 — 2026-11-25 사용자 지적). */
    enum class Zone {
        /** 끝난 일정. */
        DONE,
        /** 지금 일정(지난 부분과 남은 부분을 합친 한 칸 — 어디까지 지났는지는 시스템이 진행률로 나눠 그립니다). */
        CURRENT,
        /** 아직 시작하지 않은 일정. */
        AHEAD,
    }

    /** 칸 하나 — 길이(천분율)와 구실. */
    data class Piece(val length: Int, val zone: Zone)

    /** 진행 알림의 칸 수 상한 — 11칸부터는 시스템이 칸 색을 버리고 흰 막대 하나로 그렸습니다(갤럭시 S25+ One UI, 2026-11-25 3~15칸으로 확인). */
    const val MAX_PIECES = 10

    /**
     * 하루 막대의 칸들(길이 합 1000) — 일정마다 한 칸, 칸 사이 틈은 시스템이 그립니다. 지금 위치는 칸으로 나누지 않고 진행률
     * ([progress])로 넘깁니다: 시스템이 그 앞은 굵게, 뒤는 가늘게 그려 하나의 진행 막대처럼 읽힙니다. (지금 일정을 직접 두 칸으로
     * 나누면 막 시작한 일정의 '지난 부분'이 최소 너비로 부풀어 별개의 일정처럼 보였습니다.)
     */
    fun pieces(segs: List<LiveSeg>, now: LocalDateTime): List<Piece> {
        val day = segs.filter { !it.isBlank }
        if (day.isEmpty()) return emptyList()
        val dayStart = day.first().start
        val total = Duration.between(dayStart, day.last().end).toMillis().coerceAtLeast(1L)
        fun at(t: LocalDateTime) = (Duration.between(dayStart, t).toMillis().coerceIn(0L, total) * 1000.0 / total)
        fun zone(start: LocalDateTime, end: LocalDateTime) = when {
            !end.isAfter(now) -> Zone.DONE
            start.isAfter(now) -> Zone.AHEAD
            else -> Zone.CURRENT
        }
        // 경계(천분율)를 반올림해 칸 길이를 정하면 합이 정확히 1000 이 됩니다. 0 이 되는 아주 짧은 칸은 건너뜁니다.
        val raw = mutableListOf<Pair<Double, Zone>>()          // (끝 위치, 구실)
        var cursor = dayStart
        for (s in day) {
            // 일정 사이가 비면(드묾) 그만큼도 한 칸.
            if (s.start.isAfter(cursor)) raw += at(s.start) to zone(cursor, s.start)
            raw += at(s.end) to zone(s.start, s.end)
            cursor = s.end
        }
        val out = mutableListOf<Piece>()
        var prev = 0
        for ((end, zone) in raw) {
            val e = Math.round(end).toInt().coerceIn(prev, 1000)
            if (e > prev) { out += Piece(e - prev, zone); prev = e }
        }
        if (prev < 1000 && out.isNotEmpty()) out[out.lastIndex] = out.last().copy(length = out.last().length + 1000 - prev)
        return capped(out)
    }

    /**
     * 칸이 [MAX_PIECES] 를 넘으면 가장 짧은 칸(대개 10분 쉬는 시간)부터 옆의 칸에 합칩니다 — 끝난 일정끼리, 남은 일정끼리만.
     * 지금 일정 칸은 건드리지 않아 그 시작·끝이 막대에서 그대로 보입니다. 쉬는 시간이 빠져도 칸 사이의 틈이 구분선이 됩니다.
     */
    private fun capped(pieces: List<Piece>): List<Piece> {
        val out = pieces.toMutableList()
        while (out.size > MAX_PIECES) {
            val i = out.indices.filter { out[it].zone != Zone.CURRENT }.sortedBy { out[it].length }
                .firstOrNull { k -> listOf(k - 1, k + 1).any { it in out.indices && out[it].zone == out[k].zone } } ?: break
            val j = listOf(i - 1, i + 1).filter { it in out.indices && out[it].zone == out[i].zone }.maxBy { out[it].length }
            out[j] = out[j].copy(length = out[j].length + out[i].length)
            out.removeAt(i)
        }
        return out
    }

    /** 지금 위치(천분율). */
    fun progress(segs: List<LiveSeg>, now: LocalDateTime): Int {
        val day = segs.filter { !it.isBlank }
        if (day.isEmpty()) return 0
        val total = Duration.between(day.first().start, day.last().end).toMillis().coerceAtLeast(1L)
        return (Duration.between(day.first().start, now).toMillis().coerceIn(0L, total) * 1000L / total).toInt()
    }

    /**
     * 칸 색 — 끝난 일정과 지금 일정은 [accent](지금 일정의 색) 한 가지, 남은 일정은 회색. 진행률 뒤쪽을 가늘고 흐리게 하는 것은
     * 시스템이 합니다(styledByProgress).
     */
    fun color(zone: Zone, accent: Int): Int = if (zone == Zone.AHEAD) TRACK else accent

    /** 남은 일정 칸의 회색(정한 값) — 시스템이 가늘고 흐리게 그리므로 어두운 알림창에서도 묻히지 않게 중간 밝기. */
    private const val TRACK = 0xFF8A919E.toInt()
}
