package com.yhjang.timetable

/**
 * 급식 알레르기 번호 19종 (1.난류 2.우유 3.메밀 4.땅콩 5.대두 6.밀 7.고등어 8.게 9.새우
 * 10.돼지고기 11.복숭아 12.토마토 13.아황산류 14.호두 15.닭고기 16.쇠고기 17.오징어
 * 18.조개류 19.잣). 추적 대상은 사용자가 설정에서 직접 고릅니다.
 */
val ALLERGY_LEGEND: Map<Int, String> = linkedMapOf(
    1 to "난류",
    2 to "우유",
    3 to "메밀",
    4 to "땅콩",
    5 to "대두",
    6 to "밀",
    7 to "고등어",
    8 to "게",
    9 to "새우",
    10 to "돼지고기",
    11 to "복숭아",
    12 to "토마토",
    13 to "아황산류",
    14 to "호두",
    15 to "닭고기",
    16 to "쇠고기",
    17 to "오징어",
    18 to "조개류",
    19 to "잣",
)

/** 기본 추적값 — 없음. 처음 설치하면 아무것도 표시하지 않고, 설정에서 고른 것만 추적합니다. */
val DEFAULT_ALLERGY_CODES: Set<Int> = emptySet()

/** 알레르기 한 종류가 검출된 결과 — 표시 라벨과 매칭된 원재료 이름들(모르면 빈 목록) */
data class DetectedAllergy(val label: String, val names: List<String>)

/** 견과류 세부 코드 — 배지·위젯에서는 '견과'로 묶어 표기 폭을 줄입니다. */
private val NUT_CODES: Set<Int> = setOf(4, 14, 19)

/**
 * 코드가 누락된 원문을 위한 키워드 백업. 선택된 알레르기에만 적용합니다.
 * (API 표기가 빠졌더라도 음식 이름에 원재료가 있으면 잡아냅니다.)
 */
private val allergyKeywords: Map<Int, List<String>> = mapOf(
    4 to listOf("땅콩"),
    14 to listOf("호두"),
    19 to listOf("잣"),
    9 to listOf("새우"),
)

/** 견과류 통칭 — 땅콩·호두·잣 중 선택된 코드에도 함께 적용합니다. */
private val generalNutKeywords = listOf(
    "견과", "아몬드", "캐슈", "피칸", "마카다미아", "헤이즐넛", "브라질너트",
)

private fun normalize(menuText: String): String = menuText.lowercase().replace(Regex("""\s+"""), "")

/** 코드 하나에 대해 코드·키워드로 확인된 원재료 이름들 — 비어 있으면 미검출입니다. */
private fun matchedNames(code: Int, codes: Set<Int>, normalized: String): List<String> {
    val names = mutableListOf<String>()
    if (code in codes) names += ALLERGY_LEGEND.getValue(code)
    val keywords = buildList {
        addAll(allergyKeywords[code].orEmpty())
        if (code in NUT_CODES) addAll(generalNutKeywords)
    }
    keywords.filterTo(names) { normalized.contains(it) }
    return names.distinct()
}

/**
 * 선택된 알레르기만 검사해, 검출된 것마다 한 항목씩 돌려줍니다.
 * 라벨은 교육청 19종 표기의 한글 이름을 쓰고, [selected] 밖의 코드는 무시합니다.
 */
fun detectedAllergies(
    codes: Set<Int>,
    menuText: String,
    selected: Set<Int> = DEFAULT_ALLERGY_CODES,
): List<DetectedAllergy> {
    val normalized = normalize(menuText)
    return selected.sorted().mapNotNull { code ->
        val label = ALLERGY_LEGEND[code] ?: return@mapNotNull null
        val names = matchedNames(code, codes, normalized)
        if (names.isEmpty()) null else DetectedAllergy(label, names)
    }
}

/** 배지·위젯용 짧은 이름 — 견과류 세부 코드는 '견과'로 묶고 중복을 제거합니다. */
fun shortAllergyLabels(allergies: List<DetectedAllergy>): List<String> =
    allergies.map { if (it.label in setOf("땅콩", "호두", "잣")) "견과" else it.label }.distinct()
