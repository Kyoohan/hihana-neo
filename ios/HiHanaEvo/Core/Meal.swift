import Foundation

/// 한 끼를 이루는 메뉴 항목 하나 — 알레르기 번호를 걷어낸 이름과 그 항목에 붙은 코드.
struct MealItem: Hashable, Codable {
    let name: String
    let codes: Set<Int>
}

/// 하루 네 끼. index 는 하이하나 급식 API 의 contents1~4 순서(1=아침 … 4=간식).
enum Meal: String, CaseIterable, Codable {
    case breakfast, lunch, dinner, snack

    var label: String {
        switch self {
        case .breakfast: "아침"
        case .lunch: "점심"
        case .dinner: "저녁"
        case .snack: "간식"
        }
    }

    var index: Int { Meal.allCases.firstIndex(of: self)! + 1 }

    /// 끼니 시간(자정 기준 분, 시작 · 끝).
    var range: (start: Int, end: Int) {
        switch self {
        case .breakfast: Schedule.breakfast
        case .lunch: Schedule.lunch
        case .dinner: Schedule.dinner
        case .snack: Schedule.snack
        }
    }
}

/// 한 날짜의 급식 전체.
struct DayMeals: Codable, Equatable {
    var items: [Meal: [MealItem]]
    var photos: [Meal: String]

    static let empty = DayMeals(items: [:], photos: [:])

    /// 메뉴가 있는 끼니만, 아침부터 순서대로.
    var presentMeals: [Meal] { Meal.allCases.filter { !(items[$0] ?? []).isEmpty } }
}

/// 지금 볼 끼니와 그 날 식단 — tomorrow 면 내일 첫 끼니.
struct MealPick: Equatable {
    let meal: Meal
    let meals: DayMeals
    let date: LocalDate
    let tomorrow: Bool
}

enum MealRules {
    /// 오늘 식단 중 아직 끝나지 않은 첫 끼니(저녁이 끝나면 간식), 오늘 것이 다 끝났으면 내일 첫 끼니(보통 아침).
    static func current(today: LocalDate, todayMeals: DayMeals?, tomorrowMeals: DayMeals?, now: Date) -> MealPick? {
        if let todayMeals {
            for meal in todayMeals.presentMeals where today.at(minutes: meal.range.end) > now {
                return MealPick(meal: meal, meals: todayMeals, date: today, tomorrow: false)
            }
        }
        if let tomorrowMeals, let first = tomorrowMeals.presentMeals.first {
            return MealPick(meal: first, meals: tomorrowMeals, date: today.adding(days: 1), tomorrow: true)
        }
        return nil
    }
}
