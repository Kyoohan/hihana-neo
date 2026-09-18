package com.yhjang.timetable

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Context.planDataStore: DataStore<Preferences> by preferencesDataStore(name = "study_plan")

/**
 * 면학 위치 저장소 (자정 자동 초기화).
 *
 * iOS 판은 App Group UserDefaults 로 앱·위젯·워치가 값을 공유하지만, 안드로이드는
 * 위젯이 앱과 같은 프로세스에서 도는 게 보통이라 DataStore 하나만 있으면 됩니다.
 */
object PlanStore {

    val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")
    private val dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val payloadKey = stringPreferencesKey("studyPlanPayload")
    private val dayKeyName = stringPreferencesKey("studyPlanDay")
    private val mealItemsKey = stringPreferencesKey("mealItems")
    private val mealPayloadKey = stringPreferencesKey("mealPayload")
    private val mealAllergensKey = stringPreferencesKey("mealAllergens")
    private val mealPhotosKey = stringPreferencesKey("mealPhotos")
    private val mealDayKeyName = stringPreferencesKey("mealDay")
    private val homeOpacityKey = intPreferencesKey("widgetOpacityHome")
    private val homeThemeKey = stringPreferencesKey("widgetThemeHome")
    private val accentColorKey = intPreferencesKey("accentColor")
    private val appThemeKey = stringPreferencesKey("appTheme")
    private val studentGradeKey = intPreferencesKey("studentGrade")
    private val allergyCodesKey = stringPreferencesKey("allergyCodes")

    /** 예전엔 투명도·테마를 다른 키로도 저장했습니다 — 마이그레이션용 */
    private val legacyOpacityKey = intPreferencesKey("widgetOpacityPercent")
    private val legacyThemeKey = stringPreferencesKey("widgetTheme")

    /** 위젯 테마 — 시스템 설정을 따르거나 라이트/다크로 고정합니다 */
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    val themes: List<String> = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)

    /** 설정 화면에 표시할 한글 라벨 */
    fun themeLabel(theme: String): String = when (theme) {
        THEME_LIGHT -> "라이트"
        THEME_DARK -> "다크"
        else -> "시스템"
    }

    /**
     * 강조 색 저장값 센티넬 — 0 이면 배경화면 기반 Material You 동적 색을 따릅니다.
     * (ARGB 는 0xFF______ 라 0 과 겹치지 않아 저장 스키마 변경 없이 구분됩니다.)
     */
    const val AUTO_ACCENT_COLOR: Int = 0

    /** 앱·위젯이 함께 쓰는 강조 색 (ARGB). 값이 없으면 자동(배경화면)입니다. */
    suspend fun accentColor(context: Context): Int {
        val prefs = context.planDataStore.data.first()
        return prefs[accentColorKey] ?: AUTO_ACCENT_COLOR
    }

    suspend fun setAccentColor(context: Context, argb: Int) {
        context.planDataStore.edit { prefs -> prefs[accentColorKey] = argb }
    }

    /** 면학감독 필터에 쓸 학생 학년 — 1~3 밖의 값은 기본값 2 로 보정합니다. */
    const val DEFAULT_STUDENT_GRADE = 2

    suspend fun studentGrade(context: Context): Int {
        val prefs = context.planDataStore.data.first()
        return (prefs[studentGradeKey] ?: DEFAULT_STUDENT_GRADE).coerceIn(1, 3)
    }

    suspend fun setStudentGrade(context: Context, grade: Int) {
        context.planDataStore.edit { prefs -> prefs[studentGradeKey] = grade.coerceIn(1, 3) }
    }

    /**
     * 추적할 급식 알레르기 코드(교육청 19종). 저장값이 없으면 기존 사용자와 같은
     * [DEFAULT_ALLERGY_CODES] 로 동작하고, 모두 해제하면 빈 집합이 됩니다.
     */
    suspend fun allergyCodes(context: Context): Set<Int> {
        val prefs = context.planDataStore.data.first()
        val raw = prefs[allergyCodesKey] ?: return DEFAULT_ALLERGY_CODES
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    suspend fun setAllergyCodes(context: Context, codes: Set<Int>) {
        context.planDataStore.edit { prefs ->
            prefs[allergyCodesKey] = codes.sorted().joinToString(",")
        }
    }

    fun today(): LocalDate = LocalDate.now(seoulZone)

    // MARK: 임의 JSON 캐시 (알리미·학사일정·게시판·일자별 급식)

    /** DataStore 에 저장된 캐시 한 건 — [at] 은 저장 시각(ms)이라 호출부가 TTL 을 판단합니다. */
    data class CachedJson(val value: String, val at: Long)

    private fun cacheValueKey(name: String) = stringPreferencesKey("cacheValue_$name")
    private fun cacheTimeKey(name: String) = longPreferencesKey("cacheTime_$name")

    suspend fun cachedJson(context: Context, name: String): CachedJson? {
        val prefs = context.planDataStore.data.first()
        return prefs[cacheValueKey(name)]?.let { CachedJson(it, prefs[cacheTimeKey(name)] ?: 0L) }
    }

    suspend fun writeCachedJson(context: Context, name: String, value: String, at: Long = System.currentTimeMillis()) {
        context.planDataStore.edit { prefs ->
            prefs[cacheValueKey(name)] = value
            prefs[cacheTimeKey(name)] = at
        }
    }

    /** 위젯 불투명도(%) */
    suspend fun homeWidgetOpacity(context: Context): Int = readOpacity(context, homeOpacityKey)

    suspend fun setHomeWidgetOpacity(context: Context, percent: Int) = writeOpacity(context, homeOpacityKey, percent)

    private suspend fun readOpacity(context: Context, key: Preferences.Key<Int>): Int {
        val prefs = context.planDataStore.data.first()
        return prefs[key] ?: prefs[legacyOpacityKey] ?: 60
    }

    private suspend fun writeOpacity(context: Context, key: Preferences.Key<Int>, percent: Int) {
        context.planDataStore.edit { prefs ->
            prefs[key] = percent.coerceIn(10, 100)
        }
    }

    /** 위젯 테마 */
    suspend fun homeWidgetTheme(context: Context): String = readTheme(context, homeThemeKey)

    suspend fun setHomeWidgetTheme(context: Context, theme: String) = writeTheme(context, homeThemeKey, theme)

    /**
     * 앱 자체의 테마 — 위젯 테마와 별도 키로 저장하며, 위젯 레거시 키로 폴백하지 않습니다.
     * 값이 없으면 시스템 설정을 따릅니다.
     */
    suspend fun appTheme(context: Context): String {
        val prefs = context.planDataStore.data.first()
        return prefs[appThemeKey] ?: THEME_SYSTEM
    }

    suspend fun setAppTheme(context: Context, theme: String) = writeTheme(context, appThemeKey, theme)

    private suspend fun readTheme(context: Context, key: Preferences.Key<String>): String {
        val prefs = context.planDataStore.data.first()
        return prefs[key] ?: prefs[legacyThemeKey] ?: THEME_SYSTEM
    }

    private suspend fun writeTheme(context: Context, key: Preferences.Key<String>, theme: String) {
        context.planDataStore.edit { prefs ->
            prefs[key] = if (theme in themes) theme else THEME_SYSTEM
        }
    }

    fun dayKey(date: LocalDate): String = date.format(dayFormatter)

    /** 저장된 날짜가 오늘이 아니면 빈 값을 돌려줍니다 → 자정 자동 초기화 */
    suspend fun payload(context: Context, date: LocalDate = today()): Map<String, String> {
        val prefs = context.planDataStore.data.first()
        if (prefs[dayKeyName] != dayKey(date)) return emptyMap()
        val raw = prefs[payloadKey] ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    suspend fun place(context: Context, slot: PlanSlot, date: LocalDate = today()): StudyPlace? {
        val stored = payload(context, date)[slot.name]?.let { StudyPlace.fromStorageValue(it) }
        return stored ?: slot.defaultPlace
    }

    suspend fun set(context: Context, place: StudyPlace?, slot: PlanSlot, date: LocalDate = today()) {
        val current = payload(context, date).toMutableMap()
        if (place != null) current[slot.name] = place.storageValue else current.remove(slot.name)
        write(context, current, dayKey(date))
    }

    suspend fun write(context: Context, payload: Map<String, String>, dayKey: String) {
        val json = JSONObject()
        payload.forEach { (k, v) -> json.put(k, v) }
        context.planDataStore.edit { prefs ->
            prefs[payloadKey] = json.toString()
            prefs[dayKeyName] = dayKey
        }
    }

    /** 오늘 하루치 배정을 한 번에 읽어, Timetable.blocks() 에 동기 클로저로 넘기기 위한 헬퍼 */
    suspend fun placesForToday(context: Context, date: LocalDate = today()): Map<PlanSlot, StudyPlace?> {
        return PlanSlot.entries.associateWith { place(context, it, date) }
    }

    /**
     * 오늘의 급식 메뉴 항목 — 키는 [Meal.key], 값은 항목별 (이름, 알레르기 코드) 목록입니다.
     * 저장된 날짜가 오늘이 아니면 빈 값을 돌려줍니다(자정 자동 초기화).
     * 메뉴별 코드를 저장하기 전의 구버전 캐시면 빈 목록이라 호출부가 합친 문자열로 폴백합니다.
     */
    suspend fun mealItems(context: Context, date: LocalDate = today()): Map<String, List<MealItem>> {
        val prefs = context.planDataStore.data.first()
        if (prefs[mealDayKeyName] != dayKey(date)) return emptyMap()
        return decodeMealItems(prefs[mealItemsKey])
    }

    /**
     * 위젯·레거시 호출부를 위한 합친 메뉴 문자열 — 메뉴 항목에서 파생합니다.
     * 항목이 없으면(구버전 캐시) 예전에 저장한 문자열을 그대로 돌려줍니다.
     */
    suspend fun meals(context: Context, date: LocalDate = today()): Map<String, String> {
        val items = mealItems(context, date)
        if (items.isNotEmpty()) {
            return items.mapValues { (_, list) -> list.joinToString(", ") { it.name } }
        }
        return legacyMeals(context, date)
    }

    suspend fun writeMeals(
        context: Context,
        items: Map<String, List<MealItem>>,
        dayKey: String,
        photos: Map<String, String> = emptyMap(),
    ) {
        val json = JSONObject()
        items.forEach { (key, list) ->
            val array = JSONArray()
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("n", item.name)
                obj.put("c", JSONArray(item.codes.sorted()))
                array.put(obj)
            }
            json.put(key, array)
        }
        context.planDataStore.edit { prefs ->
            prefs[mealItemsKey] = json.toString()
            prefs[mealDayKeyName] = dayKey
            if (photos.isEmpty()) {
                prefs.remove(mealPhotosKey)
            } else {
                val photoJson = JSONObject()
                photos.forEach { (key, value) -> photoJson.put(key, value) }
                prefs[mealPhotosKey] = photoJson.toString()
            }
        }
    }

    /** 끼니별 급식 사진 파일명 — 저장된 날짜가 오늘이 아니면 빈 값을 돌려줍니다. */
    suspend fun mealPhotos(context: Context, date: LocalDate = today()): Map<String, String> {
        val prefs = context.planDataStore.data.first()
        if (prefs[mealDayKeyName] != dayKey(date)) return emptyMap()
        val raw = prefs[mealPhotosKey] ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    /**
     * 끼니별 알레르기 번호 합집합 — 메뉴 항목에서 파생하며, 위젯의 메뉴 단위 경고에 씁니다.
     * 항목이 없으면(구버전 캐시) 예전에 저장한 코드를 돌려줍니다.
     */
    suspend fun mealAllergens(context: Context, date: LocalDate = today()): Map<String, Set<Int>> {
        val items = mealItems(context, date)
        if (items.isNotEmpty()) {
            return items.mapValues { (_, list) -> list.flatMapTo(mutableSetOf()) { it.codes } }
        }
        return legacyMealAllergens(context, date)
    }

    /** 구버전 캐시(메뉴 합친 문자열) 폴백 — 자정 초기화 규칙은 동일합니다. */
    private suspend fun legacyMeals(context: Context, date: LocalDate): Map<String, String> {
        val prefs = context.planDataStore.data.first()
        if (prefs[mealDayKeyName] != dayKey(date)) return emptyMap()
        val raw = prefs[mealPayloadKey] ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    /** 구버전 캐시(끼니별 코드 문자열) 폴백 */
    private suspend fun legacyMealAllergens(context: Context, date: LocalDate): Map<String, Set<Int>> {
        val prefs = context.planDataStore.data.first()
        if (prefs[mealDayKeyName] != dayKey(date)) return emptyMap()
        val raw = prefs[mealAllergensKey] ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { key ->
                json.getString(key).split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
            }
        }.getOrDefault(emptyMap())
    }

    /** `{"lunch":[{"n":"배추겉절이","c":[9]}, ...]}` 형태를 [MealItem] 목록으로 풉니다. */
    private fun decodeMealItems(raw: String?): Map<String, List<MealItem>> {
        if (raw == null) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { key ->
                val array = json.optJSONArray(key) ?: return@associateWith emptyList<MealItem>()
                (0 until array.length()).mapNotNull { index ->
                    val obj = array.optJSONObject(index) ?: return@mapNotNull null
                    val name = obj.optString("n")
                    if (name.isEmpty()) return@mapNotNull null
                    val codesArray = obj.optJSONArray("c")
                    val codes = if (codesArray == null) emptySet() else
                        (0 until codesArray.length()).mapNotNull { c ->
                            codesArray.optInt(c).takeIf { it > 0 }
                        }.toSet()
                    MealItem(name, codes)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
