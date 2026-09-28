package com.yhjang.timetable

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * NEIS 급식 부가 정보 — 끼니별 칼로리·영양성분·원산지·급식인원. 메뉴와 사진은 하나고 홈페이지 쪽이 더 자세해서
 * 그대로 쓰고, 이 값들만 NEIS 교육정보 개방 포털(mealServiceDietInfo)에서 받아 덧붙입니다.
 */
data class NeisMealInfo(
    val kcal: String?,
    val nutrients: List<Pair<String, String>>,
    val origins: List<Pair<String, String>>,
    val servings: Int?,
)

object NeisMeal {
    private const val TAG = "NeisMeal"
    private const val ENDPOINT = "https://open.neis.go.kr/hub/mealServiceDietInfo"
    // 하나고등학교 — 서울특별시교육청(B10) · 행정표준코드 7010918 (schoolInfo 로 확인).
    private const val OFFICE = "B10"
    private const val SCHOOL = "7010918"
    private const val TTL_MS = 12 * 3_600_000L
    private val ymd = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun cacheName(date: LocalDate) = "neis_meal_$date"

    /** [date] 의 끼니별 정보(키는 [Meal.key]). 12시간 캐시 — 한 키를 모든 사용자가 같이 쓰므로 호출을 아낍니다. */
    suspend fun load(context: Context, date: LocalDate, force: Boolean = false): Map<String, NeisMealInfo> {
        if (BuildConfig.NEIS_KEY.isBlank()) return emptyMap()
        val cached = PlanStore.cachedJson(context, cacheName(date))
        if (cached != null && !force && System.currentTimeMillis() - cached.at < TTL_MS) return decode(cached.value)
        val fresh = runCatching { fetch(date) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Log.d(TAG, "fetch $date 실패: ${it.message}") }
            .getOrNull()
        // 실패하거나 아직 등록 전(빈 응답)이면 있던 캐시를 그대로 씁니다.
        if (fresh.isNullOrEmpty()) return cached?.let { decode(it.value) }.orEmpty()
        PlanStore.writeCachedJson(context, cacheName(date), encode(fresh))
        return fresh
    }

    private suspend fun fetch(date: LocalDate): Map<String, NeisMealInfo> = withContext(Dispatchers.IO) {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("KEY", BuildConfig.NEIS_KEY)
            .addQueryParameter("Type", "json")
            .addQueryParameter("pIndex", "1")
            .addQueryParameter("pSize", "10")
            .addQueryParameter("ATPT_OFCDC_SC_CODE", OFFICE)
            .addQueryParameter("SD_SCHUL_CODE", SCHOOL)
            .addQueryParameter("MLSV_YMD", date.format(ymd))
            .build()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            // 데이터가 없으면 {"RESULT":{"CODE":"INFO-200",...}} 만 옵니다.
            val rows = json.optJSONArray("mealServiceDietInfo")?.optJSONObject(1)?.optJSONArray("row") ?: return@withContext emptyMap()
            (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.mapNotNull { row ->
                val meal = when (row.optString("MMEAL_SC_CODE")) {
                    "1" -> Meal.BREAKFAST
                    "2" -> Meal.LUNCH
                    "3" -> Meal.DINNER
                    else -> null
                } ?: return@mapNotNull null
                meal.key to NeisMealInfo(
                    kcal = row.optString("CAL_INFO").replace("Kcal", "", ignoreCase = true).trim().takeIf { it.isNotEmpty() },
                    nutrients = pairs(row.optString("NTR_INFO")),
                    origins = pairs(row.optString("ORPLC_INFO")),
                    servings = row.optString("MLSV_FGR").toDoubleOrNull()?.toInt(),
                )
            }.toMap()
        }
    }

    /** "탄수화물(g) : 111.3<br/>단백질(g) : 36.0" → [(탄수화물(g), 111.3), …] */
    private fun pairs(raw: String): List<Pair<String, String>> = raw
        .split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
        .mapNotNull { line ->
            val parts = line.split(":", limit = 2).map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
        }

    private fun encode(map: Map<String, NeisMealInfo>): String = JSONObject().apply {
        map.forEach { (key, info) ->
            put(key, JSONObject().apply {
                put("kcal", info.kcal ?: "")
                put("servings", info.servings ?: -1)
                put("nutrients", JSONArray(info.nutrients.map { JSONArray(listOf(it.first, it.second)) }))
                put("origins", JSONArray(info.origins.map { JSONArray(listOf(it.first, it.second)) }))
            })
        }
    }.toString()

    private fun decode(raw: String): Map<String, NeisMealInfo> = runCatching {
        val json = JSONObject(raw)
        fun list(arr: JSONArray?) = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            arr?.optJSONArray(i)?.let { it.optString(0) to it.optString(1) }
        }
        json.keys().asSequence().associateWith { key ->
            val o = json.getJSONObject(key)
            NeisMealInfo(
                kcal = o.optString("kcal").ifEmpty { null },
                nutrients = list(o.optJSONArray("nutrients")),
                origins = list(o.optJSONArray("origins")),
                servings = o.optInt("servings", -1).takeIf { it >= 0 },
            )
        }
    }.getOrDefault(emptyMap())
}
