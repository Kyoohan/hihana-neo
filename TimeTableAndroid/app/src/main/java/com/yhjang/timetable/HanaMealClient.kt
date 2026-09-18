package com.yhjang.timetable

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** 파싱 결과 한 끼 — 메뉴 항목 목록과 사진 파일명(있으면). 합친 문자열/알레르기는 항목에서 파생합니다. */
data class MealMenu(val items: List<MealItem>, val photoFile: String? = null) {
    val text: String get() = items.joinToString(", ") { it.name }
    val allergens: Set<Int> get() = items.flatMapTo(mutableSetOf()) { it.codes }
}

/** 한 날짜의 급식 전체 — 화면(주간 보기 포함)이 쓰는 단위입니다. */
data class DayMeals(
    val items: Map<String, List<MealItem>>,
    val photos: Map<String, String>,
)

/**
 * 하이하나 급식 메뉴 조회 — 로그인 없이 공개된 엔드포인트라 세션/쿠키가 필요 없습니다.
 *
 * 사이트가 HTTP(cleartext)라 네트워크 보안 설정에서 hana.hs.kr 도메인만 예외로 열어 둡니다
 * (res/xml/network_security_config.xml). 브라우저 없이 POST 하면 봇으로 막히므로
 * HanaPortalSync 와 같은 Referer·Origin·Accept 헤더를 그대로 붙입니다.
 */
class HanaMealClient private constructor() {

    companion object {
        @Volatile private var instance: HanaMealClient? = null

        fun get(): HanaMealClient =
            instance ?: synchronized(this) { instance ?: HanaMealClient().also { instance = it } }

        private const val BASE = "http://www.hana.hs.kr"
        private const val ENDPOINT = "$BASE/daily/hanaMeal.ajax"
        private const val THUMB_BASE = "https://www.hana.hs.kr/data/bbs/0707/thumb/"
        private const val FULL_BASE = "https://www.hana.hs.kr/data/bbs/0707/"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 16; SM-S938N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        /** 끼니 사진 파일명을 썸네일 URL 로 바꿉니다. */
        fun mealPhotoThumbUrl(file: String?): String? {
            val name = file?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
            return THUMB_BASE + name
        }

        /** 끼니 사진 파일명을 원본(풀사이즈) URL 로 바꿉니다 — 전체화면 뷰어용. */
        fun mealPhotoFullUrl(file: String?): String? {
            val name = file?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
            return FULL_BASE + name
        }

        private val allergyParenRegex = Regex("""\(\s*[\d.\s]*\)""")
        private val allergyBareRegex = Regex("""\b\d+(?:\.\d+)+\b""")
        private val digitRegex = Regex("""\d+""")
        private val brRegex = Regex("""(?i)<br\s*/?>""")
        private val whitespaceRegex = Regex("""\s+""")

        /** `(1.5.13)` 과 드물게 오는 `1.5.13` 맨자 표기를 지우기 전에, 항목 조각으로 나눕니다. */
        private fun splitSegments(raw: String): List<String> =
            raw.replace(brRegex, "\n").split('\n', '/')

        /**
         * 원문에서 알레르기 번호만 뽑아냅니다. `(1.5.6.13)` 괄호 표기와 `1.5.13` 맨자 표기를
         * 모두 훑되, 각 표기 안의 숫자 덩어리를 코드로 씁니다. 지우기 전에 호출해야 합니다.
         */
        fun extractAllergenCodes(raw: String): Set<Int> {
            val codes = mutableSetOf<Int>()
            for (regex in listOf(allergyParenRegex, allergyBareRegex)) {
                regex.findAll(raw).forEach { match ->
                    digitRegex.findAll(match.value).forEach { digit ->
                        digit.value.toIntOrNull()?.let { codes += it }
                    }
                }
            }
            return codes
        }

        /**
         * contents 원문에서 알레르기 표기만 지우고 음식 이름을 남깁니다.
         * `(1.5.6.13)` 같은 괄호 표기와 드물게 오는 `1.5.13` 맨자 표기를 함께 처리합니다.
         * 여러 줄(`<br>`)은 표로 이어 한 줄짜리 메뉴로 만듭니다.
         */
        fun cleanMenuText(raw: String): String = splitSegments(raw)
            .map { line ->
                line.replace(allergyParenRegex, "")
                    .replace(allergyBareRegex, "")
                    .replace(whitespaceRegex, " ")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

        /**
         * contents 원문을 항목별 (이름, 알레르기 코드) 로 나눕니다. 항목마다 자기 코드를
         * 가지므로 `배추겉절이(9)` 처럼 이름에 원재료가 없어도 새우를 잡아낼 수 있습니다.
         */
        fun parseMealItems(raw: String): List<MealItem> = splitSegments(raw)
            .mapNotNull { segment ->
                val name = cleanMenuText(segment)
                if (name.isEmpty()) null else MealItem(name, extractAllergenCodes(segment))
            }

        fun parseMeals(text: String): Map<String, MealMenu> {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
            val hanaMeal = json.optJSONObject("result")?.optJSONObject("hanaMeal") ?: return emptyMap()
            val meals = linkedMapOf<String, MealMenu>()
            for (meal in Meal.entries) {
                val raw = hanaMeal.optString("contents${meal.index}")
                if (raw.isBlank() || raw == "null") continue
                val items = parseMealItems(raw)
                if (items.isNotEmpty()) {
                    meals[meal.key] = MealMenu(items, hanaMeal.optString("bbs_file${meal.index}"))
                }
            }
            return meals
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

        suspend fun fetchMeals(date: LocalDate): Map<String, MealMenu> =
        withContext(Dispatchers.IO) {
            // 이 엔드포인트는 GET 쿼리로 조회합니다 (POST 로는 JSON 이 아닌 응답이 옵니다).
            val url = ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("yy", "%04d".format(date.year))
                .addQueryParameter("mm", "%02d".format(date.monthValue))
                .addQueryParameter("dd", "%02d".format(date.dayOfMonth))
                .build()

            val request = Request.Builder()
                .url(url)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Referer", "$BASE/daily/cafeteria.do")
                .header("User-Agent", UA)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                parseMeals(response.body?.string().orEmpty())
            }
        }
}

/**
 * 일자별 급식 캐시 — 주간 보기에서 7일치를 각각 담아 두기 위해 제네릭 캐시(PlanStore)를 씁니다.
 * TTL(6시간) 내면 네트워크를 건너뜁니다.
 */
object MealCache {

    const val TTL_MS = 6 * 60 * 60 * 1000L

    private fun name(date: LocalDate) = "meal_${PlanStore.dayKey(date)}"

    /** [maxAgeMs] 를 주면 그보다 오래된 캐시는 null 로 무시합니다. */
    suspend fun load(context: Context, date: LocalDate, maxAgeMs: Long? = null): DayMeals? {
        val cached = PlanStore.cachedJson(context, name(date)) ?: return null
        if (maxAgeMs != null && System.currentTimeMillis() - cached.at > maxAgeMs) return null
        return decode(cached.value)
    }

    suspend fun save(context: Context, date: LocalDate, meals: Map<String, MealMenu>) {
        val itemsJson = JSONObject()
        val photosJson = JSONObject()
        meals.forEach { (key, menu) ->
            val array = JSONArray()
            menu.items.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("n", item.name)
                        put("c", JSONArray(item.codes.sorted()))
                    },
                )
            }
            itemsJson.put(key, array)
            menu.photoFile?.takeIf { it.isNotBlank() && it != "null" }?.let { photosJson.put(key, it) }
        }
        val root = JSONObject().apply {
            put("items", itemsJson)
            put("photos", photosJson)
        }
        PlanStore.writeCachedJson(context, name(date), root.toString())
    }

    private fun decode(raw: String): DayMeals? = runCatching {
        val root = JSONObject(raw)
        val itemsJson = root.optJSONObject("items") ?: JSONObject()
        val photosJson = root.optJSONObject("photos") ?: JSONObject()
        val items = itemsJson.keys().asSequence().associateWith { key ->
            val array = itemsJson.optJSONArray(key) ?: return@associateWith emptyList<MealItem>()
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val name = obj.optString("n")
                if (name.isEmpty()) return@mapNotNull null
                val codesArray = obj.optJSONArray("c")
                val codes = if (codesArray == null) emptySet() else
                    (0 until codesArray.length()).mapNotNull { c -> codesArray.optInt(c).takeIf { it > 0 } }.toSet()
                MealItem(name, codes)
            }
        }
        val photos = photosJson.keys().asSequence().associateWith { photosJson.getString(it) }
        DayMeals(items, photos)
    }.getOrNull()
}

/** 급식 캐시 갱신 — 실패해도 기존 캐시를 유지하도록 호출부에서 runCatching 으로 감쌉니다. */
object HanaMealSync {

    /**
     * [date] 하루치를 받아 일자별 캐시에 저장하고, 오늘이면 위젯용 단일 저장소에도 씁니다.
     * 공개 엔드포인트라 로그인 없이 동작합니다.
     */
    suspend fun refresh(context: Context, date: LocalDate = PlanStore.today()) {
        val meals = HanaMealClient.get().fetchMeals(date)
        // 빈 응답으로 기존 캐시를 덮어써 '식단 없음'이 되는 것을 막습니다 (실패로 간주)
        if (meals.isEmpty()) return
        MealCache.save(context, date, meals)
        if (date == PlanStore.today()) {
            PlanStore.writeMeals(
                context,
                meals.mapValues { it.value.items },
                PlanStore.dayKey(date),
                meals.mapNotNull { (key, menu) -> menu.photoFile?.let { key to it } }.toMap(),
            )
        }
    }
}
