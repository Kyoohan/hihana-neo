package com.skh.hihanaevo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 한 끼의 '특식' 1~2개 — 홈 급식 칸의 머리 메뉴.
 *
 * 요약 서버(/menu)의 AI 가 고른 것을 먼저 쓰고(같은 메뉴는 서버가 사흘 들고 있어 학교 전체가 한 번만 부름), 받기 전이나
 * 실패하면 기기 안의 판정 모델([pickLocally])로 고릅니다. 서버로는 메뉴 이름만 보냅니다.
 */
object MealHighlights {
    private const val TAG = "MealHighlights"
    private const val ENDPOINT = "https://hihana-summary.kyoohan0711ultra.workers.dev/menu"
    private const val PREFS = "meal_highlights"
    private const val MAX_KEEP = 60

    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private fun key(dishes: List<String>): String = dishes.joinToString("|").hashCode().toString(16)

    /** 이 메뉴로 받아 둔 AI 결과(없으면 null). */
    fun cached(context: Context, dishes: List<String>): List<String>? {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(dishes), null) ?: return null
        return runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()?.filter { it in dishes }?.ifEmpty { null }
    }

    /** AI 에게 묻고 받아 둡니다 — 실패하면 null(그대로 기기 판정을 씁니다). */
    suspend fun fetch(context: Context, dishes: List<String>): List<String>? = withContext(Dispatchers.IO) {
        cached(context, dishes)?.let { return@withContext it }
        runCatching {
            val body = JSONObject().put("dishes", JSONArray(dishes)).toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(ENDPOINT).header("x-app", "hihana-neo").post(body).build()
            client.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("menu ${response.code}: ${raw.take(120)}")
                val a = JSONObject(raw).optJSONArray("highlights") ?: JSONArray()
                (0 until a.length()).map { a.getString(it) }.filter { it in dishes }.take(2).ifEmpty { null }
            }
        }.onFailure { Log.w(TAG, "menu: ${it.message}") }.getOrNull()?.also { picked ->
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            if (prefs.all.size >= MAX_KEEP) edit.clear()
            edit.putString(key(dishes), JSONArray(picked).toString()).apply()
        }
    }

    // MARK: 기기 안 판정 모델

    /** 끝이 이러면 밥·국·밑반찬·음료 — 특식이 아닙니다(볶음밥·비빔밥도 학교에선 자주 나와 같이 셉니다). */
    private val STAPLE_END = Regex("(밥|죽|국|탕|찌개|전골|김치|깍두기|석박지|겉절이|나물|무침|조림|장아찌|젓갈|샐러드|우유|요구르트|요거트|주스|쥬스|두유|음료|누룽지|숭늉|시리얼|김자반|계란찜|달걀찜|볶음|부침|전|떡|빵)$")
    private val STAPLE_IN = listOf("김치", "만두", "단무지", "피클", "소스", "드레싱", "쌈장", "잼", "버터", "생선", "고등어", "꽁치", "삼치", "어묵")
    private val FRUIT = listOf("사과", "귤", "오렌지", "바나나", "포도", "수박", "참외", "키위", "파인애플", "방울토마토", "딸기", "멜론", "자몽", "복숭아", "배")

    /** 특식에 가까운 말과 무게 — 메인 요리·디저트일수록 큼. */
    private val SPECIAL = listOf(
        "스테이크" to 6, "피자" to 6, "버거" to 6, "폭립" to 6, "장어" to 6, "초밥" to 6, "칠리새우" to 6,
        "치킨" to 5, "탕수육" to 5, "깐풍" to 5, "유린기" to 5, "깐쇼" to 5, "양장피" to 5, "파스타" to 5, "스파게티" to 5,
        "리조또" to 5, "그라탕" to 5, "바베큐" to 5, "바비큐" to 5, "등갈비" to 5, "연어" to 5, "케이크" to 5, "케익" to 5,
        "츄러스" to 5, "타르트" to 5, "마카롱" to 5, "빙수" to 5, "부리또" to 5, "타코" to 5,
        "돈가스" to 4, "돈까스" to 4, "카츠" to 4, "커틀릿" to 4, "강정" to 4, "삼겹" to 4, "떡갈비" to 4, "갈비찜" to 4,
        "함박" to 4, "쌀국수" to 4, "라멘" to 4, "와플" to 4, "아이스크림" to 4, "핫도그" to 4, "닭다리" to 4, "윙" to 4, "북경" to 4,
        "너겟" to 3, "훈제" to 3, "짬뽕" to 3, "냉면" to 3, "샌드위치" to 3, "또띠아" to 3, "새우튀김" to 3,
        "허브" to 2, "크림" to 2, "짜장" to 2, "우동" to 2, "구이" to 1, "튀김" to 1,
    )

    private fun score(name: String, seenDays: Int): Int {
        val n = name.replace(Regex("[\\s*()\\[\\]0-9.]"), "")
        if (n.isEmpty()) return -99
        var s = SPECIAL.sumOf { (word, w) -> if (n.contains(word)) w else 0 }
        if (STAPLE_END.containsMatchIn(n) || STAPLE_IN.any { n.contains(it) } || FRUIT.any { n == it || n.endsWith(it) }) s -= 10
        // 이번 주에 여러 날 나온 메뉴는 일상 메뉴입니다.
        if (seenDays >= 2) s -= 3 * (seenDays - 1)
        return s
    }

    /**
     * 기기 안에서 고르기 — 특식 점수 3 이상에서 둘까지(둘째는 첫째와 2점 안쪽일 때만). 그런 게 없으면 밑반찬이 아닌 것 중
     * 점수가 가장 높은 하나(보통 메인 요리), 그것도 없으면 첫 메뉴. [history] 는 받아 둔 다른 날 식단(자주 나오는 메뉴 판정).
     */
    fun pickLocally(dishes: List<String>, history: Collection<DayMeals>): List<String> {
        if (dishes.isEmpty()) return emptyList()
        fun norm(s: String) = s.replace(Regex("[\\s*()\\[\\]0-9.]"), "")
        val seen = HashMap<String, Int>()
        history.forEach { day -> day.items.values.flatten().map { norm(it.name) }.toSet().forEach { seen[it] = (seen[it] ?: 0) + 1 } }
        val ranked = dishes.map { it to score(it, seen[norm(it)] ?: 0) }.sortedByDescending { it.second }
        val special = ranked.filter { it.second >= 3 }
        if (special.isNotEmpty()) {
            val top = special.first()
            return listOfNotNull(top.first, special.getOrNull(1)?.takeIf { it.second >= top.second - 2 }?.first)
        }
        return listOf(ranked.firstOrNull { it.second > -5 }?.first ?: dishes.first())
    }
}
