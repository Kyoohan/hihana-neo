package com.yhjang.timetable

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// MARK: - 장소 모델

sealed class Classroom(val name: String) {
    data object A201 : Classroom("A201")
    data object A202 : Classroom("A202")
    data object A203 : Classroom("A203")
    data object A204 : Classroom("A204")
    data object A205 : Classroom("A205")
    data object Av : Classroom("시청각실")
    data object ArtCenter : Classroom("아트센터")
    data object Seminar : Classroom("세미나실")
    data class Other(val customName: String) : Classroom(customName)

    companion object {
        val presets: List<Classroom> = listOf(A201, A202, A203, A204, A205, Av, ArtCenter, Seminar)

        fun fromName(name: String): Classroom = when (name) {
            "A201" -> A201
            "A202" -> A202
            "A203" -> A203
            "A204" -> A204
            "A205" -> A205
            "시청각실" -> Av
            "아트센터" -> ArtCenter
            "세미나실" -> Seminar
            else -> Other(name)
        }
    }
}

sealed class StudyPlace {
    data class ClassroomPlace(val classroom: Classroom) : StudyPlace()
    data class StudyRoom(val seat: String) : StudyPlace()
    data class Library(val seat: String) : StudyPlace()
    data object Dorm : StudyPlace()
    /** 방과후 수업 수강 중 — 하이하나 동기화로만 채워집니다 (수동 선택 불가) */
    data class AfterSchool(val courseName: String, val room: String?) : StudyPlace()
    /** 1인2기 활동 중 (평일 0타임) — 하이하나 동기화로만 채워집니다 (수동 선택 불가) */
    data class OneTwo(val activityName: String, val room: String?) : StudyPlace()

    /**
     * 포털이 준 장소 분류를 알려진 유형(도서관·면학실·생활관)으로 해석하지 못했을 때 쓰는
     * 일반 장소. [category] 에 원문 분류명을 그대로 담아, 알 수 없는 분류가 "교과교실"처럼
     * 특정 장소로 잘못 표시되는 것을 막습니다.
     */
    data class Other(val category: String, val location: String?) : StudyPlace()

    /** 큰 글씨로 표시할 장소명 */
    val name: String
        get() = when (this) {
            is ClassroomPlace -> "교과교실"
            is StudyRoom -> "면학실"
            is Library -> "도서관"
            Dorm -> "생활관"
            is AfterSchool -> "방과후"
            is OneTwo -> "1인2기"
            is Other -> category.ifEmpty { "기타 장소" }
        }

    /** 칩에 표시할 구체적인 목적지 (교실명 또는 자리번호) */
    val detail: String?
        get() = when (this) {
            is ClassroomPlace -> classroom.name
            is StudyRoom -> seat
            is Library -> seat
            Dorm -> null
            is AfterSchool -> room?.let { "$courseName · $it" } ?: courseName
            is OneTwo -> room?.let { "$activityName · $it" } ?: activityName
            is Other -> location
        }

    val accent: Accent
        get() = when (this) {
            is ClassroomPlace, is StudyRoom, is Other -> Accent.STUDY
            is Library -> Accent.LIBRARY
            Dorm -> Accent.DORM
            is AfterSchool -> Accent.AFTER_SCHOOL
            is OneTwo -> Accent.ONE_TWO
        }

    /**
     * 위젯에 표시할 장소별 아이콘 키 — 플랫폼별 실제 아이콘 리소스로 매핑하는 데 씁니다
     * (Android: Material Symbols 벡터 드로어블 이름과 동일).
     */
    val iconKey: String
        get() = when (this) {
            is ClassroomPlace -> "meeting_room"
            is StudyRoom -> "chair"
            is Library -> "local_library"
            Dorm -> "hotel"
            is AfterSchool -> "school"
            is OneTwo -> "directions_run"
            is Other -> "place"
        }

    /** 화면에 한 줄로 보여줄 때 */
    val displayText: String
        get() = detail?.let { "$name · $it" } ?: name

    val storageValue: String
        get() = when (this) {
            is ClassroomPlace -> "classroom:${classroom.name}"
            is StudyRoom -> "study:$seat"
            is Library -> "library:$seat"
            Dorm -> "dorm"
            is AfterSchool -> "afterschool:$courseName|${room ?: ""}"
            is OneTwo -> "onetwo:$activityName|${room ?: ""}"
            is Other -> "other:$category|${location ?: ""}"
        }

    companion object {
        fun fromStorageValue(value: String): StudyPlace? {
            if (value == "dorm") return Dorm
            if (value.startsWith("other:")) {
                val parts = value.removePrefix("other:").split("|", limit = 2)
                val category = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return null
                val location = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
                return Other(category, location)
            }
            for (prefix in listOf("afterschool:", "onetwo:")) {
                if (!value.startsWith(prefix)) continue
                val payload = value.removePrefix(prefix)
                val parts = payload.split("|", limit = 2)
                val name = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return null
                val room = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
                return if (prefix == "afterschool:") AfterSchool(name, room) else OneTwo(name, room)
            }
            val parts = value.split(":", limit = 2)
            if (parts.size != 2 || parts[1].isEmpty()) return null
            return when (parts[0]) {
                "classroom" -> ClassroomPlace(Classroom.fromName(parts[1]))
                "study" -> StudyRoom(parts[1])
                "library" -> Library(parts[1])
                else -> null
            }
        }
    }
}

data class StudySession(val name: String, val start: Int, val end: Int)

// MARK: - 수업 모델

data class Lesson(val subject: String, val room: String?) {
    val isFree: Boolean get() = room == null

    /** 위젯에 표시할 과목별 아이콘 키 — 매칭되는 과목이 없으면 기본값(menu_book)을 씁니다 */
    val iconKey: String
        get() = when {
            isFree -> "free_breakfast"
            subject.contains("확률과 통계") -> "casino"
            subject.contains("미적분") -> "functions"
            subject.contains("화학") -> "science"
            subject.contains("물리") -> "bolt"
            subject.contains("데이터 과학") -> "query_stats"
            subject.contains("선형대수") || subject.contains("인공지능") -> "smart_toy"
            subject.contains("영어") -> "translate"
            subject.contains("스포츠") || subject.contains("체육") -> "sports_soccer"
            else -> "menu_book"
        }

    companion object {
        val FREE = Lesson("공강", null)
    }
}

// MARK: - 강조 색상 키 (실제 색 매핑은 위젯/컴포즈 쪽에서)

enum class Accent { LESSON, FREE, BREAK_TIME, BREAKFAST, LUNCH, DINNER, SNACK, STUDY, LIBRARY, DORM, IDLE, AFTER_SCHOOL, ONE_TWO }

// MARK: - 블록 모델

sealed class NextUp {
    data class LessonNext(val periodNumber: Int, val lesson: Lesson) : NextUp()
    data class StudyNext(val session: StudySession, val place: StudyPlace?) : NextUp()

    val title: String
        get() = when (this) {
            is LessonNext -> lesson.subject
            is StudyNext -> place?.name ?: "위치 미설정"
        }

    val room: String?
        get() = when (this) {
            is LessonNext -> lesson.room
            is StudyNext -> place?.detail
        }

    /** 다음이 몇 교시인지 — 위젯에서 "N교시"를 과목명과 나란히 보여줄 때 씁니다 */
    val period: Int?
        get() = (this as? LessonNext)?.periodNumber
}

/** 쉬는 시간 / 식사 시간처럼 "다음 것을 기다리는" 구간 */
data class Gap(
    val label: String,
    val fallbackTitle: String,
    val caption: String,
    val next: NextUp?,
    val accent: Accent,
)

sealed class BlockKind {
    data class LessonKind(val period: Int, val lesson: Lesson) : BlockKind()
    data class StudyKind(val session: StudySession, val place: StudyPlace?) : BlockKind()
    data class GapKind(val gap: Gap) : BlockKind()
    /** 면학 종료 후 다음날 기상 시각 전까지 — 위젯에 아무것도 표시하지 않습니다 */
    data object BlankKind : BlockKind()
}

data class Block(val kind: BlockKind, val start: LocalDateTime, val end: LocalDateTime) {

    val statusLabel: String
        get() = when (kind) {
            is BlockKind.LessonKind -> if (kind.lesson.isFree) "${kind.period}교시 · 공강" else "${kind.period}교시 수업 중"
            is BlockKind.StudyKind -> kind.session.name
            is BlockKind.GapKind -> kind.gap.label
            BlockKind.BlankKind -> ""
        }

    val title: String
        get() = when (kind) {
            is BlockKind.LessonKind -> kind.lesson.subject
            is BlockKind.StudyKind -> kind.place?.name ?: "위치 미설정"
            is BlockKind.GapKind -> kind.gap.next?.title ?: kind.gap.fallbackTitle
            BlockKind.BlankKind -> ""
        }

    /** 이동해야 할 교실 / 자리 (공강·생활관·미설정이면 null → 칩이 숨겨집니다) */
    val room: String?
        get() = when (kind) {
            is BlockKind.LessonKind -> kind.lesson.room
            is BlockKind.StudyKind -> kind.place?.detail
            is BlockKind.GapKind -> kind.gap.next?.room
            BlockKind.BlankKind -> null
        }

    /** 지금(또는 다음) 이 몇 교시인지 — 위젯에서 "N교시"를 과목명과 나란히 보여줄 때 씁니다 */
    val periodNumber: Int?
        get() = when (kind) {
            is BlockKind.LessonKind -> kind.period
            is BlockKind.GapKind -> kind.gap.next?.period
            else -> null
        }

    /** 면학 종료 후 다음날 기상 시각 전까지 — 위젯이 이 값이면 아무것도 그리지 않습니다 */
    val isBlank: Boolean get() = kind == BlockKind.BlankKind

    /** 위젯에 표시할 상황별 아이콘 키 (수업이면 과목, 면학이면 장소 기준) — 빈 문자열이면 아이콘 없음 */
    val iconKey: String
        get() = when (kind) {
            is BlockKind.LessonKind -> kind.lesson.iconKey
            is BlockKind.StudyKind -> kind.place?.iconKey ?: ""
            is BlockKind.GapKind -> when (val next = kind.gap.next) {
                is NextUp.LessonNext -> next.lesson.iconKey
                is NextUp.StudyNext -> next.place?.iconKey ?: ""
                null -> ""
            }
            BlockKind.BlankKind -> ""
        }

    val accent: Accent
        get() = when (kind) {
            is BlockKind.LessonKind -> if (kind.lesson.isFree) Accent.FREE else Accent.LESSON
            is BlockKind.StudyKind -> kind.place?.accent ?: Accent.IDLE
            is BlockKind.GapKind -> kind.gap.accent
            BlockKind.BlankKind -> Accent.IDLE
        }

    val countdownCaption: String?
        get() = when (kind) {
            is BlockKind.LessonKind -> if (kind.lesson.isFree) "다음 교시까지" else "종료까지"
            is BlockKind.StudyKind -> "종료까지"
            is BlockKind.GapKind -> kind.gap.caption
            BlockKind.BlankKind -> null
        }

    val showsCountdown: Boolean get() = countdownCaption != null

    val showsHours: Boolean get() = Duration.between(start, end).toMinutes() >= 60

    val timeRangeText: String
        get() {
            val f = DateTimeFormatter.ofPattern("HH:mm")
            return "${start.format(f)} – ${end.format(f)}"
        }
}

// MARK: - 포털에서 받아 온 주간 시간표

/**
 * 포털 시간표를 앱에서 쓰는 형태로 담은 값.
 * [lessons] 는 요일 → 교시 → 수업 구조이고,
 * [periodTimes] 는 포털이 교시 시각을 함께 줄 때만 채워집니다 — 없으면 기본 수업 시각표를 씁니다.
 *
 * DataStore 캐시에 넣고 꺼내야 해서 JSON 직렬화를 함께 둡니다.
 */
data class TimetableWeek(
    val lessons: Map<Int, Map<Int, Lesson>>,
    val periodTimes: Map<Int, Pair<Int, Int>>? = null,
) {

    fun toJson(): String {
        val rows = JSONArray()
        lessons.toSortedMap().forEach { (day, byPeriod) ->
            byPeriod.toSortedMap().forEach { (period, lesson) ->
                rows.put(
                    JSONObject().apply {
                        put("day", day)
                        put("period", period)
                        put("subject", lesson.subject)
                        if (lesson.room != null) put("room", lesson.room)
                    },
                )
            }
        }
        val json = JSONObject().put("lessons", rows)
        periodTimes?.let { times ->
            val array = JSONArray()
            times.toSortedMap().forEach { (period, range) ->
                array.put(
                    JSONObject().apply {
                        put("period", period)
                        put("start", range.first)
                        put("end", range.second)
                    },
                )
            }
            json.put("periodTimes", array)
        }
        return json.toString()
    }

    companion object {
        /** 손상된 캐시·빈 시간표는 null 로 돌려 호출부가 "표 없음"으로 처리하게 합니다. */
        fun fromJson(raw: String?): TimetableWeek? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)
                val rows = json.optJSONArray("lessons") ?: return@runCatching null
                val lessons = mutableMapOf<Int, MutableMap<Int, Lesson>>()
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val day = row.optInt("day", -1)
                    val period = row.optInt("period", -1)
                    val subject = row.optString("subject")
                    if (day !in 1..7 || period <= 0 || subject.isEmpty()) continue
                    val room = row.optString("room").takeIf { it.isNotEmpty() && it != "null" }
                    lessons.getOrPut(day) { mutableMapOf() }[period] = Lesson(subject, room)
                }
                if (lessons.isEmpty()) return@runCatching null
                val timeArray = json.optJSONArray("periodTimes")
                val times = if (timeArray == null) {
                    null
                } else {
                    buildMap {
                        for (i in 0 until timeArray.length()) {
                            val row = timeArray.optJSONObject(i) ?: continue
                            val period = row.optInt("period", -1)
                            val start = row.optInt("start", -1)
                            val end = row.optInt("end", -1)
                            if (period > 0 && start >= 0 && end > start) put(period, start to end)
                        }
                    }.takeIf { it.isNotEmpty() }
                }
                TimetableWeek(lessons, times)
            }.getOrNull()
        }
    }
}

// MARK: - 시간표 본체

object Timetable {

    /**
     * 포털에서 받아 둔 주간 시간표(메모리). null 이면 표시할 시간표가 없다는 뜻입니다 —
     * 더 이상 내장 기본 시간표로 폴백하지 않습니다. 앱과 위젯이 보통 같은 프로세스에서 돌기
     * 때문에, 캐시에서 복원해 여기 설치해 두면 블록 계산과 주간 표가 함께 이 값을 씁니다
     * — 캐시 복원은 HanaTimetableSync 가 담당합니다.
     */
    @Volatile private var installedWeek: TimetableWeek? = null

    /** 지금 유효한 주간 과목표 — 설치된 포털 값만. 아무것도 받지 못했으면 빈 표입니다. */
    val activeLessons: Map<Int, Map<Int, Lesson>>
        get() = installedWeek?.lessons ?: emptyMap()

    /** 지금 유효한 교시 시각 — 포털이 시각을 주면 그 값을, 아니면 기본 수업 시각표를 씁니다. */
    val activePeriodTimes: Map<Int, Pair<Int, Int>>
        get() = installedWeek?.periodTimes ?: periodTimes

    /** 포털 시간표 설치. 빈 시간표는 무시해 직전에 설치된 표가 유지되게 합니다. */
    fun installFetched(week: TimetableWeek?) {
        installedWeek = week?.takeIf { it.lessons.isNotEmpty() }
    }

    fun fetchedWeek(): TimetableWeek? = installedWeek

    // ---------- 시간 구획 (자정 기준 분) ----------

    val periodTimes: Map<Int, Pair<Int, Int>> = mapOf(
        1 to (8 * 60 + 20 to 9 * 60 + 10),
        2 to (9 * 60 + 20 to 10 * 60 + 10),
        3 to (10 * 60 + 20 to 11 * 60 + 10),
        4 to (11 * 60 + 20 to 12 * 60 + 10),
        5 to (13 * 60 + 10 to 14 * 60 + 0),
        6 to (14 * 60 + 10 to 15 * 60 + 0),
        7 to (15 * 60 + 10 to 16 * 60 + 0),
    )

    val lunch: Pair<Int, Int> = (12 * 60 + 10) to (13 * 60 + 10)
    val dinner: Pair<Int, Int> = (17 * 60 + 50) to (19 * 60 + 0)
    val snack: Pair<Int, Int> = (21 * 60 + 0) to (21 * 60 + 30)
    /** 기상(06:50) 직후의 아침 식사 시간 — 평일 등교 시각 전까지만 열립니다 */
    val breakfast: Pair<Int, Int> = (6 * 60 + 50) to (8 * 60)
    /** 등교 및 조회 — 아침시간 종료 후 쉬는 시간 전까지 */
    val morningAssemblyEnd: Int = 8 * 60 + 10
    /** 면학 종료 후 이 시각 전까지는 위젯에 아무것도 띄우지 않습니다 */
    val wakeTime: Int = 6 * 60 + 50

    // ---------- 면학 ----------

    object Sessions {
        // 평일 0타임: 1인2기 신청이 있으면 1인2기, 없으면(수요일 · 1인2기 종료 후) 평범한 면학 위치로 씁니다.
        // 시간은 hh.hana.hs.kr 의 period-defs.json 으로 확인했습니다.
        val weekday0 = StudySession("면학 0타임", 16 * 60 + 20, 17 * 60 + 50)
        val weekday1 = StudySession("면학 1타임", 19 * 60, 21 * 60)
        val weekday2 = StudySession("면학 2타임", 21 * 60 + 30, 23 * 60 + 10)

        val weekend1 = StudySession("면학 1타임", 13 * 60 + 30, 15 * 60 + 30)
        val weekend2 = StudySession("면학 2타임", 16 * 60, 17 * 60 + 50)
        val weekend3 = StudySession("면학 3타임", 19 * 60, 21 * 60)
        val weekend4 = StudySession("면학 4타임", 21 * 60 + 30, 23 * 60 + 10)
    }

    // ---------- 주간 시간표 ----------
    // 과목표는 포털에서 받아 [installFetched] 로만 채웁니다. 내장 기본 시간표는 두지 않습니다.

    // MARK: - 하루 전체를 빈틈 없는 블록으로 분해
    //
    // placeFor 는 PlanStore 조회를 대신합니다 — DataStore 접근이 suspend 라서
    // 이 함수 자체는 동기로 두고, 호출부에서 하루치 배정을 미리 읽어 클로저로 넘깁니다.

    fun blocks(date: LocalDate, placeFor: (PlanSlot) -> StudyPlace?): List<Block> {
        val startOfDay = date.atStartOfDay()
        fun at(minutes: Int): LocalDateTime = startOfDay.plusMinutes(minutes.toLong())

        fun studyBlock(session: StudySession, slot: PlanSlot): Block =
            Block(BlockKind.StudyKind(session, placeFor(slot)), at(session.start), at(session.end))

        fun nextStudy(session: StudySession, slot: PlanSlot): NextUp =
            NextUp.StudyNext(session, placeFor(slot))

        fun gapBlock(gap: Gap, from: Int, to: Int): Block =
            Block(BlockKind.GapKind(gap), at(from), at(to))

        val result = mutableListOf<Block>()
        val weekday = date.dayOfWeek.value // 월=1 ... 일=7
        // installedWeek 를 한 번만 읽어 시각·과목표가 같은 스냅샷에서 나오게 합니다 —
        // activePeriodTimes/activeLessons 를 따로따로 읽으면 그 사이에 백그라운드
        // 동기화가 새 시간표를 설치할 경우 시각은 이전 표, 과목은 새 표에서 섞여 나올 수 있습니다.
        val week = installedWeek
        val times = week?.periodTimes ?: periodTimes

        if (weekday >= 6) {

            // ================= 주말 =================

            val weekendWake = minOf(wakeTime, Sessions.weekend1.start)
            if (weekendWake > 0) {
                result += Block(BlockKind.BlankKind, at(0), at(weekendWake))
            }
            result += gapBlock(
                Gap("주말 · 수업 없음", "수업 없음", "1타임까지", nextStudy(Sessions.weekend1, PlanSlot.weekend1), Accent.IDLE),
                weekendWake, Sessions.weekend1.start,
            )
            result += studyBlock(Sessions.weekend1, PlanSlot.weekend1)

            result += gapBlock(
                Gap("쉬는 시간", "쉬는 시간", "2타임까지", nextStudy(Sessions.weekend2, PlanSlot.weekend2), Accent.BREAK_TIME),
                Sessions.weekend1.end, Sessions.weekend2.start,
            )
            result += studyBlock(Sessions.weekend2, PlanSlot.weekend2)

            result += gapBlock(
                Gap("저녁시간", "저녁시간", "3타임까지", nextStudy(Sessions.weekend3, PlanSlot.weekend3), Accent.DINNER),
                dinner.first, dinner.second,
            )
            result += studyBlock(Sessions.weekend3, PlanSlot.weekend3)

            result += gapBlock(
                Gap("간식시간", "간식시간", "4타임까지", nextStudy(Sessions.weekend4, PlanSlot.weekend4), Accent.SNACK),
                snack.first, snack.second,
            )
            result += studyBlock(Sessions.weekend4, PlanSlot.weekend4)

            result += Block(BlockKind.BlankKind, at(Sessions.weekend4.end), at(24 * 60))

        } else {

            // ================= 평일 =================
            // 포털에서 받은 그 요일 과목표만 씁니다 — 없으면 수업 블록 없이 시간 기반 블록만 남깁니다.
            val today = week?.lessons?.get(weekday).orEmpty()
            var cursor = 0

            for (p in today.keys.sorted()) {
                val t = times[p] ?: continue
                val lesson = today[p] ?: continue

                if (cursor < t.first) {
                    val next = NextUp.LessonNext(p, lesson)
                    if (cursor == 0) {
                        // 자정~기상 시각은 빈 화면, 그 뒤 아침시간 → 등교 및 조회 → 쉬는 시간 순으로 채웁니다.
                        val wakeStart = minOf(wakeTime, t.first)
                        if (wakeStart > 0) {
                            result += Block(BlockKind.BlankKind, at(0), at(wakeStart))
                        }
                        // 각 구간은 실제 1교시 시작 시각보다 늦게 뻗지 않도록 잘라 블록이 겹치지 않게 합니다
                        // (비정상적으로 이른 시간표에 대비).
                        val breakfastEnd = minOf(breakfast.second, t.first)
                        if (breakfastEnd > wakeStart) {
                            result += gapBlock(
                                Gap("아침시간", "아침시간", "${p}교시까지", next, Accent.BREAKFAST),
                                wakeStart, breakfastEnd,
                            )
                        }
                        val assemblyEnd = minOf(morningAssemblyEnd, t.first)
                        if (assemblyEnd > breakfastEnd) {
                            result += gapBlock(
                                Gap("등교 및 조회", "등교 및 조회", "${p}교시까지", next, Accent.LESSON),
                                breakfastEnd, assemblyEnd,
                            )
                        }
                        if (t.first > assemblyEnd) {
                            result += gapBlock(
                                Gap("쉬는 시간", "쉬는 시간", "${p}교시까지", next, Accent.BREAK_TIME),
                                assemblyEnd, t.first,
                            )
                        }
                    } else if (cursor == lunch.first && t.first == lunch.second) {
                        result += gapBlock(Gap("점심시간", "점심시간", "${p}교시까지", next, Accent.LUNCH), cursor, t.first)
                    } else {
                        result += gapBlock(Gap("쉬는 시간", "쉬는 시간", "${p}교시까지", next, Accent.BREAK_TIME), cursor, t.first)
                    }
                }

                result += Block(BlockKind.LessonKind(p, lesson), at(t.first), at(t.second))
                cursor = t.second
            }

            if (today.isEmpty()) {
                // 수업이 하나도 없으면 자정~기상은 빈 화면, 그 뒤 0타임까지는 시간 기반 대기 블록만.
                val study0Start = Sessions.weekday0.start
                val blankEnd = minOf(wakeTime, study0Start)
                if (blankEnd > 0) {
                    result += Block(BlockKind.BlankKind, at(0), at(blankEnd))
                }
                if (blankEnd < study0Start) {
                    result += gapBlock(
                        Gap("수업 없음", "오늘 수업 없음", "0타임까지", nextStudy(Sessions.weekday0, PlanSlot.weekday0), Accent.IDLE),
                        blankEnd, study0Start,
                    )
                }
            } else if (cursor < Sessions.weekday0.start) {
                result += gapBlock(
                    Gap("일과 종료", "오늘 수업 끝", "0타임까지", nextStudy(Sessions.weekday0, PlanSlot.weekday0), Accent.IDLE),
                    cursor, Sessions.weekday0.start,
                )
            }

            result += studyBlock(Sessions.weekday0, PlanSlot.weekday0)

            result += gapBlock(
                Gap("저녁시간", "저녁시간", "1타임까지", nextStudy(Sessions.weekday1, PlanSlot.weekday1), Accent.DINNER),
                dinner.first, dinner.second,
            )

            result += studyBlock(Sessions.weekday1, PlanSlot.weekday1)

            result += gapBlock(
                Gap("간식시간", "간식시간", "2타임까지", nextStudy(Sessions.weekday2, PlanSlot.weekday2), Accent.SNACK),
                snack.first, snack.second,
            )

            result += studyBlock(Sessions.weekday2, PlanSlot.weekday2)

            result += Block(BlockKind.BlankKind, at(Sessions.weekday2.end), at(24 * 60))
        }

        return result
    }

    fun blockAt(date: LocalDate, time: LocalDateTime, placeFor: (PlanSlot) -> StudyPlace?): Block {
        val all = blocks(date, placeFor)
        return all.firstOrNull { !it.start.isAfter(time) && time.isBefore(it.end) } ?: all.last()
    }
}
