package com.yhjang.timetable

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 서울특별시 날씨 — 귀가 기간 홈·위젯 안내용. 앱은 AI 요약 서버(Cloudflare Worker)의 `/weather` 만 부르고, 서버가
 * 날씨 제공처에서 받아 30분씩 보관해 모든 사용자에게 나눠 줍니다(사용자 위치는 보내지 않음). 제공처를 바꿔도 앱은 그대로입니다.
 */
object SeoulWeather {

    private const val ENDPOINT = "https://hihana-summary.kyoohan0711ultra.workers.dev/weather"
    private const val CACHE_NAME = "seoul_weather"
    private const val TTL_MS = 30 * 60 * 1000L

    data class Hour(val time: LocalDateTime, val temp: Double?, val code: Int?, val pop: Int?)
    data class Day(val date: LocalDate, val code: Int?, val max: Double?, val min: Double?, val pop: Int?)
    data class Forecast(val currentTemp: Double?, val currentCode: Int?, val hours: List<Hour>, val days: List<Day>)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    /** 캐시가 30분 안이면 그대로, 아니면 새로 받습니다. 실패하면 남아 있는 캐시(없으면 null). */
    suspend fun get(context: Context, force: Boolean = false): Forecast? {
        val stored = PlanStore.cachedJson(context, CACHE_NAME)
        if (!force && stored != null && System.currentTimeMillis() - stored.at <= TTL_MS) return decode(stored.value)
        val fresh = runCatching { fetch() }.getOrNull()
        if (fresh != null) {
            PlanStore.writeCachedJson(context, CACHE_NAME, fresh)
            return decode(fresh)
        }
        return stored?.let { decode(it.value) }
    }

    /** 네트워크 없이 캐시만 — 위젯처럼 바로 그려야 하는 곳. */
    suspend fun cached(context: Context): Forecast? = PlanStore.cachedJson(context, CACHE_NAME)?.let { decode(it.value) }

    private suspend fun fetch(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(ENDPOINT).header("x-app", "hihana-neo").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "weather ${response.code}" }
            response.body?.string().orEmpty().also { JSONObject(it) }
        }
    }

    private fun decode(raw: String): Forecast? = runCatching {
        val json = JSONObject(raw)
        fun JSONObject.num(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
        fun JSONObject.int(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
        val current = json.optJSONObject("current")
        val hours = json.optJSONArray("hourly")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Hour(LocalDateTime.parse(o.getString("t")), o.num("temp"), o.int("code"), o.int("pop"))
            }
        }.orEmpty()
        val days = json.optJSONArray("daily")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Day(LocalDate.parse(o.getString("date")), o.int("code"), o.num("max"), o.num("min"), o.int("pop"))
            }
        }.orEmpty()
        Forecast(current?.num("temp"), current?.int("code"), hours, days)
    }.getOrNull()

    // MARK: - 날씨 코드 (WMO)

    enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

    fun kind(code: Int?): Kind = when (code) {
        null, 0 -> Kind.CLEAR
        1, 2 -> Kind.PARTLY
        3 -> Kind.CLOUDY
        45, 48 -> Kind.FOG
        in 51..67, in 80..82 -> Kind.RAIN
        in 71..77, 85, 86 -> Kind.SNOW
        in 95..99 -> Kind.STORM
        else -> Kind.CLOUDY
    }

    fun label(code: Int?): String = when (kind(code)) {
        Kind.CLEAR -> "맑음"
        Kind.PARTLY -> "구름 조금"
        Kind.CLOUDY -> "흐림"
        Kind.FOG -> "안개"
        Kind.RAIN -> if (code in 80..82) "소나기" else "비"
        Kind.SNOW -> "눈"
        Kind.STORM -> "뇌우"
    }

    fun icon(code: Int?): Int = when (kind(code)) {
        Kind.CLEAR -> R.drawable.ic_weather_clear
        Kind.PARTLY -> R.drawable.ic_weather_partly
        Kind.CLOUDY, Kind.FOG -> R.drawable.ic_weather_cloud
        Kind.RAIN -> R.drawable.ic_weather_rain
        Kind.SNOW -> R.drawable.ic_weather_snow
        Kind.STORM -> R.drawable.ic_weather_storm
    }

    /** 비·눈이 오거나 강수 확률이 50% 이상인지. */
    fun wet(code: Int?, pop: Int?): Boolean = kind(code) in setOf(Kind.RAIN, Kind.SNOW, Kind.STORM) || (pop ?: 0) >= 50

    /** 젖는 날씨의 대표 코드 — 여러 시간 중 비·눈을 우선합니다. */
    fun worst(codes: List<Int?>): Int? =
        codes.filterNotNull().maxByOrNull { code -> kind(code).ordinal * 100 + code }
}
