package com.yhjang.timetable

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 학번 → 이름 표 (앱 내장). 학교 Google 디렉터리(has_<학번>@hana.hs.kr)에서 만든 `assets/students.bin` 을
 * 읽습니다 — tools/build_students.py 로 생성. 도서관 좌석 응답은 이름을 비우고 학번만 주므로 여기서 채웁니다.
 *
 * 파일은 AES-256-GCM 으로 잠겨 있고 키는 아래 상수에서 파생됩니다. APK 를 뜯어 파일을 그대로 열어 보는 정도만 막는
 * 가벼운 보호이며, 학사시스템 계정을 등록한(= 재학생인) 사용자에게만 풉니다.
 */
object StudentDirectory {

    private const val TAG = "StudentDirectory"
    private const val SECRET = "hihana-neo-students-v1"

    @Volatile private var table: Map<String, String>? = null
    @Volatile private var failed = false

    fun name(context: Context, studentNumber: String?): String? {
        val number = studentNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return load(context)?.get(number)
    }

    private fun load(context: Context): Map<String, String>? {
        table?.let { return it }
        if (failed) return null
        if (!HanaCredentialStore.hasCredentials(context)) return null
        synchronized(this) {
            table?.let { return it }
            val loaded = runCatching {
                val bytes = context.assets.open("students.bin").use { it.readBytes() }
                val key = MessageDigest.getInstance("SHA-256").digest(SECRET.toByteArray())
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes, 0, 12))
                val plain = cipher.doFinal(bytes, 12, bytes.size - 12)
                val json = JSONObject(String(plain, Charsets.UTF_8))
                json.keys().asSequence().associateWith { json.getString(it) }
            }.onFailure {
                Log.w(TAG, "students.bin unavailable: ${it.message}")
                failed = true
            }.getOrNull()
            table = loaded
            return loaded
        }
    }
}
