package com.skh.hihanaevo

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
 * 날씨 — 평소엔 하나고등학교, 귀가 기간엔 서울특별시([currentPlace]). 앱은 AI 요약 서버(Cloudflare Worker)의 `/weather?place=`
 * 만 부르고, 서버가 기상청 단기예보(장애 때만 Open-Meteo)에서 곳마다 받아 30분씩 보관해 모든 사용자에게 나눠 줍니다(사용자
 * 위치는 보내지 않고, 두 곳 중 어디인지만).
 */
object SeoulWeather {

    private const val ENDPOINT = "https://hihana-summary.kyoohan0711ultra.workers.dev/weather"
    private const val TTL_MS = 30 * 60 * 1000L

    /** 날씨를 받는 곳 — [query] 는 서버의 place, [label] 은 날씨 화면 제목. */
    enum class Place(val query: String, val label: String, val cacheName: String) {
        HANA("hana", "하나고등학교", "hana_weather"),
        SEOUL("seoul", "서울특별시", "seoul_weather"),
    }

    /** 지금 보여 줄 곳 — 귀가 기간이면 서울, 아니면 학교. */
    fun currentPlace(): Place {
        val now = LocalDateTime.now(PlanStore.seoulZone)
        return if (homeStayNotice(now.toLocalDate(), now) != null) Place.SEOUL else Place.HANA
    }

    data class Hour(val time: LocalDateTime, val temp: Double?, val code: Int?, val pop: Int?)
    data class Day(val date: LocalDate, val code: Int?, val max: Double?, val min: Double?, val pop: Int?)
    data class Forecast(
        val currentTemp: Double?,
        val currentCode: Int?,
        val hours: List<Hour>,
        val days: List<Day>,
        /** 서버가 받은 곳 — "kma"(기상청) 또는 "open-meteo"(기상청 장애 때 대신). */
        val source: String? = null,
        /** 서버가 받아 온 시각. */
        val updated: java.time.Instant? = null,
    )

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    /** 캐시가 30분 안이면 그대로, 아니면 새로 받습니다. 실패하면 남아 있는 캐시(없으면 null). */
    suspend fun get(context: Context, place: Place = currentPlace(), force: Boolean = false): Forecast? {
        val stored = PlanStore.cachedJson(context, place.cacheName)
        if (!force && stored != null && System.currentTimeMillis() - stored.at <= TTL_MS) return decode(stored.value)
        val fresh = runCatching { fetch(place) }.getOrNull()
        if (fresh != null) {
            PlanStore.writeCachedJson(context, place.cacheName, fresh)
            return decode(fresh)
        }
        return stored?.let { decode(it.value) }
    }

    /** 네트워크 없이 캐시만 — 위젯처럼 바로 그려야 하는 곳. */
    suspend fun cached(context: Context, place: Place = currentPlace()): Forecast? = PlanStore.cachedJson(context, place.cacheName)?.let { decode(it.value) }

    private suspend fun fetch(place: Place): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$ENDPOINT?place=${place.query}").header("x-app", "hihana-neo").build()
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
        Forecast(
            current?.num("temp"), current?.int("code"), hours, days,
            source = json.optString("source").takeIf { it.isNotBlank() },
            updated = runCatching { java.time.Instant.parse(json.getString("updated")) }.getOrNull(),
        )
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

    /** 기상청 표현에 맞춘 이름 — 서버가 기상청 하늘·강수형태를 WMO 코드로 바꿔 보냅니다(구름많음 2, 빗방울 51, 진눈깨비 66 …). */
    fun label(code: Int?): String = when (kind(code)) {
        Kind.CLEAR -> "맑음"
        Kind.PARTLY -> if (code == 2) "구름많음" else "구름 조금"
        Kind.CLOUDY -> "흐림"
        Kind.FOG -> "안개"
        Kind.RAIN -> when (code) {
            in 80..82 -> "소나기"
            in 51..57 -> "빗방울"
            66, 67 -> "진눈깨비"
            else -> "비"
        }
        Kind.SNOW -> if (code == 77) "눈날림" else "눈"
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
