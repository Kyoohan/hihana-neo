import Foundation

/// 홈 '지금' 카드·위젯의 귀가 안내 — [shortDetail] 은 위젯 한 줄용.
struct HomeStayNotice: Equatable { let title: String; let detail: String; let shortDetail: String }

/// 지금 일정이 비어 있는 귀가 기간이면 안내를 — 귀가일은 1타임부터, 귀교일은 밤 9시 전까지만.
func homeStayNotice(today: LocalDate, now: Date, context: Schedule.Context) -> HomeStayNotice? {
    guard let stay = context.homeStay[today] else { return nil }
    let minutes = now.localDate == today ? now.minutesOfDay : 0
    switch stay {
    case .return:
        return minutes >= Schedule.homeReturnAt ? nil : HomeStayNotice(title: "조심히 돌아오세요", detail: "오늘 귀교", shortDetail: "오늘 귀교")
    case .leave, .away:
        if stay == .leave && minutes < context.homeLeaveAt(today) { return nil }
        guard let back = context.nextHomeReturn(from: today.adding(days: 1)) else {
            return HomeStayNotice(title: "편안한 귀가 보내세요", detail: "귀교하면 일정이 다시 표시됩니다", shortDetail: "귀가 기간")
        }
        return HomeStayNotice(title: "편안한 귀가 보내세요", detail: "\(back.month)월 \(back.day)일(\(back.weekdayShort)) 귀교",
                              shortDetail: "\(back.month)/\(back.day)(\(back.weekdayShort)) 귀교")
    }
}
