import Foundation

#if DEBUG
import SwiftUI

/// 급식 카드처럼 전체 화면(fullScreenCover)으로 사진 보기를 띄웁니다 — 시트로 띄우면 시트 자체의 '밀어서 닫기'와 섞여 확인이 안 됩니다.
private struct DebugFullScreenPhoto: View {
    let urls: [URL]
    @State private var shown = false
    var body: some View {
        Color.clear
            .onAppear { shown = true }
            .fullScreenCover(isPresented: $shown) { PhotoViewer(urls: urls) }
    }
}
/// 시뮬레이터 확인용 — 실행 인자 `-route 이름` 으로 화면을 바로 엽니다(쉼표로 여러 장).
enum DebugRoutes {
    @MainActor
    static func open(_ raw: String) {
        for name in raw.split(separator: ",") {
            let r: Route? = switch name {
            case "all": .allPanel
            case "meal": .meal(nil)
            case "mealweek": .meal(LocalDate.today())
            case "board": .board
            case "classroom": .classroom
            case "academic": .academic
            case "timetable": .timetable
            case "apply": .apply
            case "alim": .alim
            case "weather": .weather(until: nil)
            case "settings": .settings(nil)
            case "display": .settings(.display)
            case "about": .about
            case "privacy": .privacy
            case "changelog": .changelog
            case "midnight": .midnight
            case "search": .portalSearch
            case "unified": .search
            case "status": .classroomStatus
            case "library": .seats(.library)
            case "studyroom": .seats(.studyRoom)
            case "outing": .quickApply(.outing)
            case "classroomApply": .quickApply(.classroom)
            case "outingHistory": .history(.outing)
            case "libraryHistory": .history(.library)
            default: nil
            }
            if let r { Navigator.shared.open(r) }
            if name == "accountSheet" { Navigator.shared.present(AccountDialog()) }
            // 사진 크게 보기 확인용 — 하나고 공개 급식 게시판의 실제 사진 한 장.
            if name == "photo" {
                // 급식 카드와 같게 원본 → 썸네일 순서로(원본이 404 면 썸네일).
                let file = "imgfile_23887191128.jpg"
                Navigator.shared.present(DebugFullScreenPhoto(urls: [MealClient.fullURL(file), MealParser.thumbURL(file)].compactMap { $0 }))
            }
        }
    }
}
#endif
