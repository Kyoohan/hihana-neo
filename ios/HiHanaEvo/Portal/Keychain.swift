import Foundation
import Security

/// 하이하나 아이디·비밀번호 — 키체인에만 둡니다(이 기기 밖으로 나가지 않고, 로그 어디에도 남기지 않습니다).
enum Keychain {
    private static let service = "com.skh.hihanaevo.hana"

    static func get(_ account: String) -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// 저장에 성공했는지 — 서명 없는 빌드 등에서는 키체인 쓰기가 거절될 수 있습니다.
    @discardableResult
    static func set(_ value: String, for account: String) -> Bool {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = Data(value.utf8)
        // 기기가 한 번 잠금 해제된 뒤엔 백그라운드에서도 읽을 수 있게, 다른 기기로는 옮겨 가지 않게.
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(add as CFDictionary, nil) == errSecSuccess
    }

    static func remove(_ account: String) {
        SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ] as CFDictionary)
    }

    /// 이 앱이 키체인에 둔 것을 모두 — 하이하나 계정, 심야면학 로그인, 클래스룸 연결 비밀키.
    static func removeAll() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service] as CFDictionary)
    }

    /// iOS 키체인은 앱을 지워도 남습니다 — 새로 설치해 처음 여는 것이면 지난 설치가 남긴 계정 정보를 지웁니다.
    /// 설정이 남아 있으면(업데이트) 그대로 둡니다.
    static func wipeIfFreshInstall() {
        let marker = "install.marker"
        guard !UserDefaults.standard.bool(forKey: marker) else { return }
        UserDefaults.standard.set(true, forKey: marker)
        // 앱 지원 폴더에 남은 파일이 있으면 지난 빌드에서 올라온 것(공유 저장소 자리가 바뀌었어도) — 지우지 않습니다.
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let hasFiles = !((try? FileManager.default.contentsOfDirectory(atPath: support.path)) ?? []).isEmpty
        if SharedContainer.defaults.object(forKey: "whats_new.seen") == nil && !hasFiles { removeAll() }
    }
}

enum Credentials {
    /// 키체인 쓰기가 막힌 기기·빌드에서는 이번 실행 동안만 메모리에 둡니다(다음 실행엔 다시 로그인).
    private nonisolated(unsafe) static var memory: (id: String, password: String)?

    static var memId: String? { Keychain.get("memId") ?? memory?.id }
    static var memPwd: String? { Keychain.get("memPwd") ?? memory?.password }
    static var hasAny: Bool { memId != nil && memPwd != nil }

    /// 키체인에 저장했으면 true, 못 했으면 메모리에만 둔 것.
    @discardableResult
    static func save(id: String, password: String) -> Bool {
        memory = (id, password)
        let ok = Keychain.set(id, for: "memId") && Keychain.set(password, for: "memPwd")
        if ok { memory = nil }
        return ok
    }

    static func clear() {
        memory = nil
        Keychain.remove("memId")
        Keychain.remove("memPwd")
    }
}
