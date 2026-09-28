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

    /** 심야면학 (선택 신청) — 기숙사 쪽 사이트에서 신청한 날만 일정에 들어갑니다. [seat] 예: "3층 5번". */
    data class Midnight(val seat: String?) : StudyPlace()

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
            is Midnight -> "심야면학"
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
            is Midnight -> seat
        }

    val accent: Accent
        get() = when (this) {
            is ClassroomPlace, is StudyRoom, is Other, is Midnight -> Accent.STUDY
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
            is Midnight -> "bedtime"
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
            is Midnight -> "midnight:${seat ?: ""}"
        }

    companion object {
        fun fromStorageValue(value: String): StudyPlace? {
            if (value == "dorm") return Dorm
            if (value.startsWith("midnight:")) return Midnight(value.removePrefix("midnight:").takeIf { it.isNotEmpty() })
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

    /**
     * 포털 과목명은 "스포츠 생활1(남)(5반) 김응주 /" 처럼 끝에 담당 선생님과 빗금이 붙어 옵니다.
     * [title] 은 그걸 뗀 과목명, [teacher] 는 선생님 이름(없으면 null) — 표시용이고 [subject] 원값은 그대로 둡니다.
     */
    val title: String get() = split.first
    val teacher: String? get() = split.second

    /** 화면에 쓰는 장소 — 선생님이 있으면 "체육관 · 김응주" 처럼 뒤에 붙입니다. 교실이 없으면(공강) null. */
    val place: String? get() = room?.let { r -> listOfNotNull(r, teacher).joinToString(" · ") }

    private val split: Pair<String, String?>
        get() {
            val trimmed = subject.trim().removeSuffix("/").trim()
            val match = TEACHER_SUFFIX.matchEntire(trimmed) ?: return trimmed to null
            return match.groupValues[1].trim() to match.groupValues[2]
        }

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

        /** "과목(반) 이름" — 괄호로 끝나는 과목명 뒤의 2~4글자 한글 이름. */
        private val TEACHER_SUFFIX = Regex("""^(.*\))\s+([가-힣]{2,4})$""")
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
            is LessonNext -> lesson.title
            is StudyNext -> place?.name ?: "위치 미설정"
        }

    val room: String?
        get() = when (this) {
            is LessonNext -> lesson.place
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
            is BlockKind.LessonKind -> kind.lesson.title
            is BlockKind.StudyKind -> kind.place?.name ?: "위치 미설정"
            is BlockKind.GapKind -> kind.gap.next?.title ?: kind.gap.fallbackTitle
            BlockKind.BlankKind -> ""
        }

    /** 이동해야 할 교실 / 자리 (공강·생활관·미설정이면 null → 칩이 숨겨집니다) */
    val room: String?
        get() = when (kind) {
            is BlockKind.LessonKind -> kind.lesson.place
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

    // ---------- 심야면학 (선택) ----------

    /**
     * 심야면학 신청 한 건. [start]/[end] 는 신청한 날 0시 기준 분 — 자정을 넘기면 1440 이상입니다 (예: 23:50–01:00 → 1430–1500).
     * 신청하지 않은 타임은 아예 넣지 않으므로, 신청이 없는 날의 일과는 평소대로 마지막 면학(23:10)에서 끝납니다.
     */
    data class MidnightBooking(val session: Int, val start: Int, val end: Int, val seat: String?)

    // 날짜별로 둡니다 — 자정을 넘긴 뒤에도 전날 신청(00:00~01:00 부분)을 오늘 맨 앞에 이어 붙여야 해서.
    @Volatile private var midnightByDate: Map<LocalDate, List<MidnightBooking>>? = null

    /** 앱 모듈(MidnightSchedule)이 저장해 둔 신청을 설치합니다. */
    fun installMidnight(byDate: Map<LocalDate, List<MidnightBooking>>) {
        midnightByDate = byDate.mapValues { (_, list) -> list.sortedBy { it.start } }
    }

    fun midnightInstalled(): Boolean = midnightByDate != null

    private fun midnightFor(date: LocalDate): List<MidnightBooking> = midnightByDate?.get(date).orEmpty()

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

    /**
     * 하루 블록 + 심야면학: 오늘 신청한 심야 타임은 마지막 면학 뒤에 (그 사이는 "휴식"), 전날 신청이 자정을 넘기면
     * 그 남은 부분을 오늘 맨 앞에 붙입니다. 신청이 없으면 평소 블록 그대로입니다.
     */
    fun blocks(date: LocalDate, placeFor: (PlanSlot) -> StudyPlace?): List<Block> =
        applyHomeStay(date, blocksWithMidnight(date, placeFor))

    private fun blocksWithMidnight(date: LocalDate, placeFor: (PlanSlot) -> StudyPlace?): List<Block> {
        val base = baseBlocks(date, placeFor)
        val today = midnightFor(date)
        val carry = midnightFor(date.minusDays(1)).filter { it.end > 24 * 60 }
        if (today.isEmpty() && carry.isEmpty()) return base
        val result = base.toMutableList()
        val startOfDay = date.atStartOfDay()
        fun at(minutes: Int): LocalDateTime = startOfDay.plusMinutes(minutes.toLong())
        fun session(b: MidnightBooking, offset: Int) = StudySession("심야 ${b.session}타임", b.start - offset, b.end - offset)
        fun block(b: MidnightBooking, offset: Int) =
            Block(BlockKind.StudyKind(session(b, offset), StudyPlace.Midnight(b.seat)), at(b.start - offset), at(b.end - offset))

        if (today.isNotEmpty()) {
            // 끝의 빈 블록(마지막 면학 ~ 자정)을 걷어 내고 그 자리에 휴식 → 심야 타임을 잇습니다.
            val tail = result.lastOrNull()?.takeIf { it.isBlank }
            if (tail != null) result.removeAt(result.lastIndex)
            var cursor = tail?.start ?: result.lastOrNull()?.end ?: at(24 * 60)
            for (b in today) {
                val start = at(b.start)
                if (start.isBefore(cursor)) continue
                if (start.isAfter(cursor)) {
                    val next = NextUp.StudyNext(session(b, 0), StudyPlace.Midnight(b.seat))
                    result += Block(BlockKind.GapKind(Gap("휴식", "휴식", "심야 ${b.session}타임까지", next, Accent.IDLE)), cursor, start)
                }
                result += block(b, 0)
                cursor = at(b.end)
            }
            if (cursor.isBefore(at(24 * 60))) result += Block(BlockKind.BlankKind, cursor, at(24 * 60))
        }

        if (carry.isNotEmpty()) {
            // 전날 심야가 자정을 넘겨 이어지는 부분 — 블록은 전날 시작 시각 그대로 두어 진행률이 맞게 합니다.
            val carryEnd = at(carry.maxOf { it.end } - 24 * 60)
            val head = result.firstOrNull()?.takeIf { it.isBlank }
            if (head != null) {
                result.removeAt(0)
                if (carryEnd.isBefore(head.end)) result.add(0, Block(BlockKind.BlankKind, carryEnd, head.end))
            }
            result.addAll(0, carry.map { block(it, 24 * 60) })
        }
        return result
    }

    // ---------- 귀가 기간 ----------

    /** 학사일정의 귀가·귀교로 정한 그날의 상태. 평소에는 null 입니다. */
    enum class HomeStay {
        /** 귀가하는 날 — 1타임부터는 일정이 없습니다. */
        LEAVE,
        /** 귀가와 귀교 사이 — 하루 종일 일정이 없습니다. */
        AWAY,
        /** 귀교하는 날 — 마지막 타임(21:30, 평일 2타임 · 주말 4타임)부터 일정이 시작합니다. */
        RETURN,
    }

    @Volatile private var homeStayByDate: Map<LocalDate, HomeStay>? = null

    /** 날짜별 귀가 상태 설치 — 학사일정에서 계산해 앱이 넘깁니다. */
    fun installHomeStay(byDate: Map<LocalDate, HomeStay>) {
        homeStayByDate = byDate
    }

    fun homeStayInstalled(): Boolean = homeStayByDate != null

    fun homeStay(date: LocalDate): HomeStay? = homeStayByDate?.get(date)

    /** [from] 이후(당일 포함) 가장 가까운 귀교일 — 모르면 null. */
    fun nextHomeReturn(from: LocalDate): LocalDate? =
        homeStayByDate?.filter { (date, stay) -> stay == HomeStay.RETURN && !date.isBefore(from) }?.keys?.minOrNull()

    /** 귀가일 일정이 끝나는 시각(자정 기준 분) — 그날 1타임 시작, 평일 19:00 · 주말 13:30. */
    fun homeLeaveAt(date: LocalDate): Int =
        if (date.dayOfWeek.value >= 6) Sessions.weekend1.start else Sessions.weekday1.start

    /** 귀교일 일정이 시작하는 타임 — 저녁에 돌아오므로 그날 마지막 타임(평일 2타임 · 주말 4타임, 둘 다 21:30). */
    fun homeReturnSession(date: LocalDate): StudySession =
        if (date.dayOfWeek.value >= 6) Sessions.weekend4 else Sessions.weekday2

    /** 귀교일 일정이 시작하는 시각(자정 기준 분). */
    fun homeReturnAt(date: LocalDate): Int = homeReturnSession(date).start

    /**
     * 귀가 기간에는 일정을 비웁니다 — 귀가일은 1타임부터, 그 사이 날은 하루 종일, 귀교일은 마지막 타임(21:30) 전까지.
     * 빈 구간은 [BlockKind.BlankKind] 라서 Now Bar·위젯이 평소 일과 뒤처럼 저절로 꺼집니다.
     * 면학 위치·신청은 그대로 두고, 하루 일정(지금·남은 일정·Now Bar·위젯)만 바뀝니다.
     */
    private fun applyHomeStay(date: LocalDate, blocks: List<Block>): List<Block> {
        val stay = homeStay(date) ?: return blocks
        val startOfDay = date.atStartOfDay()
        val endOfDay = startOfDay.plusDays(1)
        return when (stay) {
            HomeStay.AWAY -> listOf(Block(BlockKind.BlankKind, startOfDay, endOfDay))
            HomeStay.RETURN -> {
                val cutoff = startOfDay.plusMinutes(homeReturnAt(date).toLong())
                val kept = blocks.filter { !it.start.isBefore(cutoff) }
                listOf(Block(BlockKind.BlankKind, startOfDay, cutoff)) + kept
            }
            HomeStay.LEAVE -> {
                val cutoff = startOfDay.plusMinutes(homeLeaveAt(date).toLong())
                val kept = blocks.filter { it.start.isBefore(cutoff) }.map { block ->
                    val end = if (block.end.isAfter(cutoff)) cutoff else block.end
                    val kind = block.kind
                    // 1타임을 기다리던 구간(저녁시간 등)은 이제 귀가를 기다립니다 — "1타임까지" 대신 "귀가까지".
                    if (kind is BlockKind.GapKind && !end.isBefore(cutoff)) {
                        block.copy(kind = BlockKind.GapKind(kind.gap.copy(fallbackTitle = "귀가", caption = "귀가까지", next = null)), end = end)
                    } else {
                        block.copy(end = end)
                    }
                }
                val tailStart = kept.lastOrNull()?.end ?: startOfDay
                kept + Block(BlockKind.BlankKind, tailStart, endOfDay)
            }
        }
    }

    private fun baseBlocks(date: LocalDate, placeFor: (PlanSlot) -> StudyPlace?): List<Block> {
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

    /**
     * "다음 일정" — 쉬는 시간·식사 같은 대기 구간(GapKind)은 일정으로 치지 않고, 다음 수업·면학만 셉니다.
     * 지금이 대기 구간이면 그 구간의 히어로가 이미 바로 다음 것을 보여주므로, 그 다음(둘째) 일정을 돌려줍니다.
     */
    fun nextEvent(blocks: List<Block>, current: Block?, time: LocalDateTime): Block? {
        val upcoming = blocks.filter { it.start.isAfter(time) && !it.isBlank && it.kind !is BlockKind.GapKind }
        val skipFirst = current?.kind is BlockKind.GapKind && upcoming.firstOrNull()?.start == current.end
        var index = if (skipFirst) 1 else 0
        // 연강(같은 과목·같은 교실이 쉬는 시간만 사이에 두고 이어짐)은 한 수업으로 봅니다 — 안 그러면 점심시간 위젯이
        // 히어로에 "5교시 컴퓨터실2", 아래 '다음'에 "6교시 같은 과목 · 컴퓨터실2"를 겹쳐 보여줬습니다.
        var reference: Block? = if (skipFirst) upcoming.firstOrNull() else current
        while (true) {
            val candidate = upcoming.getOrNull(index) ?: return null
            if (!sameLesson(reference, candidate)) return candidate
            reference = candidate
            index++
        }
    }

    private fun sameLesson(a: Block?, b: Block): Boolean {
        val x = a?.kind as? BlockKind.LessonKind ?: return false
        val y = b.kind as? BlockKind.LessonKind ?: return false
        return !x.lesson.isFree && x.lesson.subject == y.lesson.subject && x.lesson.room == y.lesson.room
    }
}
