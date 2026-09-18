package com.yhjang.timetable

/**
 * 한 끼를 이루는 메뉴 항목 하나 — 알레르기 번호를 걷어낸 이름과 그 항목에 붙은 코드입니다.
 * API 가 `배추겉절이(9)` 처럼 항목별로 코드를 주므로, 이름에 원재료가 없어도 잡아낼 수 있습니다.
 */
data class MealItem(val name: String, val codes: Set<Int>)

/**
 * 하루 네 끼. [index] 는 하이하나 급식 API 의 contents1~4 순서와 맞춥니다
 * (1=아침, 2=점심, 3=저녁, 4=간식). [key] 는 PlanStore 에 저장할 때 쓰는 값입니다.
 */
enum class Meal(val key: String, val label: String, val index: Int) {
    BREAKFAST("breakfast", "아침", 1),
    LUNCH("lunch", "점심", 2),
    DINNER("dinner", "저녁", 3),
    SNACK("snack", "간식", 4);

    companion object {
        fun fromKey(key: String): Meal? = entries.firstOrNull { it.key == key }

        /** Timetable 이 만든 Gap 라벨에서 어떤 끼니인지 되돌립니다 */
        fun forGapLabel(label: String): Meal? = when (label) {
            "아침시간" -> BREAKFAST
            "점심시간" -> LUNCH
            "저녁시간" -> DINNER
            "간식시간" -> SNACK
            else -> null
        }
    }
}
