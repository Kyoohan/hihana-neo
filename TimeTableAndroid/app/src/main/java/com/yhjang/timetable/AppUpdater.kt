package com.yhjang.timetable

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** GitHub Releases 의 최신 릴리스 한 건 — 설치된 버전보다 새 것일 때만 [AppUpdater] 가 돌려줍니다. */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    /** 릴리스 본문(마크다운) — 정보 화면의 변경 사항에 그대로 보여줍니다. */
    val notes: String,
    val publishedAt: String?,
)

/**
 * 스토어 없이 하는 앱 내 업데이트(OTA).
 *
 * 배포 쪽은 GitHub Releases 하나면 됩니다: 태그(v6.20 같은 형식)로 릴리스를 만들고 APK 를 첨부하면,
 * 앱이 `releases/latest` API 로 태그를 읽어 설치된 버전과 비교하고, 새 버전이면 APK 를 캐시 폴더에
 * 받아 시스템 설치 화면으로 넘깁니다. 서명 키가 같으면 데이터를 유지한 채 덮어씌워집니다.
 *
 * 저장소는 `gradle.properties` 의 `updateRepo=owner/repo` 로 지정합니다(비어 있으면 확인을 건너뜁니다).
 */
object AppUpdater {

    private const val CACHE_NAME = "app_update"
    /** 앱 실행 시 자동 확인 간격 — 이보다 최근에 확인했으면 캐시된 결과를 씁니다. */
    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val isConfigured: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    /**
     * 최신 릴리스를 확인합니다.
     * - 자동([force] = false): [AUTO_CHECK_INTERVAL_MS] 안의 캐시를 재사용하고, **큰 버전이 오를 때만**(7.x → 8.0)
     *   알립니다. 7.1 → 7.2 같은 작은 버전은 조용히 지나갑니다.
     * - 수동([force] = true, 정보 화면의 '업데이트 확인'): 작은 버전이라도 새 것이면 돌려줘 직접 설치할 수 있습니다.
     * 새 버전이 없으면 null. 저장소가 설정되지 않았으면 네트워크를 타지 않고 null.
     */
    suspend fun check(context: Context, force: Boolean = false): UpdateInfo? {
        if (!isConfigured) return null
        if (!force) {
            PlanStore.cachedJson(context, CACHE_NAME)?.let { stored ->
                if (System.currentTimeMillis() - stored.at <= AUTO_CHECK_INTERVAL_MS) {
                    return decode(stored.value)?.takeIf { isMajorUpgrade(it.versionName, BuildConfig.VERSION_NAME) }
                }
            }
        }
        val info = withContext(Dispatchers.IO) { fetchLatest() }
        PlanStore.writeCachedJson(context, CACHE_NAME, info?.let(::encode) ?: "{}")
        return info?.takeIf {
            if (force) isNewer(it.versionName, BuildConfig.VERSION_NAME)
            else isMajorUpgrade(it.versionName, BuildConfig.VERSION_NAME)
        }
    }

    /** 네트워크 없이 마지막 확인 결과만 — 설정 화면의 알림 점을 즉시 그릴 때 씁니다 (자동 알림이라 큰 버전만). */
    suspend fun cached(context: Context): UpdateInfo? =
        PlanStore.cachedJson(context, CACHE_NAME)?.let { decode(it.value) }
            ?.takeIf { isMajorUpgrade(it.versionName, BuildConfig.VERSION_NAME) }

    /** 첫 번째 숫자(큰 버전)가 올랐는지 — 7.2 → 8.0 은 true, 7.1 → 7.2 는 false. */
    fun isMajorUpgrade(remote: String, installed: String): Boolean {
        val a = remote.split('.').firstOrNull()?.filter(Char::isDigit)?.toIntOrNull() ?: 0
        val b = installed.split('.').firstOrNull()?.filter(Char::isDigit)?.toIntOrNull() ?: 0
        return a > b
    }

    private fun fetchLatest(): UpdateInfo? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HiHanaNeo/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string().orEmpty())
            val tag = json.optString("tag_name").removePrefix("v").trim()
            if (tag.isEmpty()) return null
            val assets = json.optJSONArray("assets")
            val apkUrl = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?: return null
            return UpdateInfo(
                versionName = tag,
                apkUrl = apkUrl,
                notes = json.optString("body").trim(),
                publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
            )
        }
    }

    /** "6.20" 과 "6.19.1" 처럼 점으로 나뉜 숫자들을 앞에서부터 비교합니다 — 숫자가 아닌 조각은 0 으로 봅니다. */
    fun isNewer(remote: String, installed: String): Boolean {
        val a = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = installed.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** APK 를 앱 캐시의 updates/ 폴더에 받습니다. [onProgress] 는 0..1 (길이를 모르면 -1). */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // 이전에 받아 둔 다른 버전은 지워 캐시가 쌓이지 않게 합니다.
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "hihana-neo-${info.versionName}.apk")
            val request = Request.Builder().url(info.apkUrl).header("User-Agent", "HiHanaNeo/${BuildConfig.VERSION_NAME}").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("다운로드 실패 (${response.code})")
                val body = response.body ?: error("응답이 비어 있습니다")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
            target
        }

    /** 알 수 없는 앱 설치 허용 여부 — 거부 상태면 [openInstallPermission] 으로 설정 화면을 엽니다. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** 받아 둔 APK 를 시스템 패키지 설치 화면으로 넘깁니다. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    /** 시스템 앱 정보 화면 (갤러리 정보 화면의 오른쪽 위 ⓘ 와 같은 동작). */
    fun openAppInfo(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun encode(info: UpdateInfo): String = JSONObject().apply {
        put("version", info.versionName)
        put("apk", info.apkUrl)
        put("notes", info.notes)
        put("published", info.publishedAt ?: "")
    }.toString()

    private fun decode(raw: String): UpdateInfo? = runCatching {
        val json = JSONObject(raw)
        val version = json.optString("version")
        val apk = json.optString("apk")
        if (version.isEmpty() || apk.isEmpty()) null
        else UpdateInfo(version, apk, json.optString("notes"), json.optString("published").takeIf { it.isNotBlank() })
    }.getOrNull()
}

/**
 * 위젯 블록 전환을 정확한 시각에 다시 그리기 위한 exact alarm 권한 (Android 12+).
 * 13+ 에서는 사용자가 설정의 "알람 및 리마인더"에서 켜 줘야 하고, 없으면 WorkManager 로 폴백합니다.
 */
object ExactAlarmPermission {
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
