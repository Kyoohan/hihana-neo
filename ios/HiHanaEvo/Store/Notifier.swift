import Foundation
import UserNotifications
import UIKit
import Observation

/// 알림 — 새 알리미·게시판 새 글·데일리 브리핑. 권한이 없으면 조용히 넘어갑니다.
enum Notifier {
    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }
    }

    /// 새로 온 안 읽은 알리미에만 알림을 띄웁니다.
    static func processAlims(_ alims: [HanaAlim]) {
        let seen = AlimSeenStore.seen()
        let fresh = alims.filter { !$0.read && !seen.contains($0.id) }
        guard !fresh.isEmpty else { return }
        AlimSeenStore.markSeen(fresh.map(\.id))
        // 앱을 보고 있을 땐 알림을 띄우지 않습니다(안드로이드와 달리 iOS 는 앞에 있을 때 배너를 따로 막지 않으므로).
        guard UIApplication.shared.applicationState != .active else { return }
        for a in fresh {
            let c = UNMutableNotificationContent()
            c.title = "새 알리미"
            c.body = a.content.isEmpty ? "새 알리미가 도착했습니다" : a.content
            c.userInfo = ["open": "alim"]
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "alim-\(a.id)", content: c, trigger: nil))
        }
    }
}

/// 짧은 안내 — 안드로이드 Toast.
@MainActor
enum Toast {
    static func show(_ text: String) { ToastCenter.shared.show(text) }
}

@Observable
@MainActor
final class ToastCenter {
    static let shared = ToastCenter()
    private(set) var text: String?
    private var task: Task<Void, Never>?

    func show(_ t: String) {
        text = t
        task?.cancel()
        task = Task { try? await Task.sleep(for: .seconds(2.6)); if !Task.isCancelled { text = nil } }
    }
}
