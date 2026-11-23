import SwiftUI
import UserNotifications

@main
struct HiHanaEvoApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model: AppModel

    init() {
        Keychain.wipeIfFreshInstall()
        let model = AppModel()
        AppModel.current = model
        _model = State(initialValue: model)
        #if DEBUG
        // 시뮬레이터 확인용 — 키체인에 쓰고 읽히는지(값은 남기지 않음).
        if CommandLine.arguments.contains("-keychainCheck") {
            let ok = Keychain.set("x", for: "selftest") && Keychain.get("selftest") == "x"
            Keychain.remove("selftest")
            print("KEYCHAIN_CHECK", ok ? "ok" : "fail")
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView().environment(model)
        }
    }
}

/// 백그라운드 새로 고침 등록과 알림 — 알림을 누르면 그 화면으로.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        BackgroundWork.register()
        BackgroundWork.scheduleRefresh()
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// 앱을 보고 있을 때 온 브리핑·게시판 알림은 배너로(알리미는 Notifier 가 앞에 있을 땐 아예 만들지 않습니다).
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        let id = response.notification.request.identifier
        await MainActor.run {
            let nav = Navigator.shared
            switch info["open"] as? String {
            case "alim":
                nav.tab = .home
                nav.paths[.home] = [.alim]
            case "outing":
                nav.tab = .home
                nav.paths[.home] = [.history(.outing)]
            case "post":
                if let url = info["url"] as? String {
                    nav.tab = .home
                    nav.paths[.home] = [.boardPost(url: url, title: nil)]
                }
            default:
                // 데일리 브리핑 — 홈 맨 위의 브리핑으로.
                if id.hasPrefix("brief-") { nav.tab = .home; nav.paths[.home] = [] }
            }
        }
    }
}
