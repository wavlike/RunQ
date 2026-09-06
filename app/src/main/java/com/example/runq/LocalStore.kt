package com.example.runq

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ════════════════════════════════════════════════════════
// 기기 로컬 저장소 (SharedPreferences + Gson). 서버/계정 시스템이 없는 현재 단계에서
// My 탭(러닝 기록/저장/프로필/설정)이 실제로 동작하도록 하는 최소 구현.
// 나중에 실제 백엔드가 생기면 이 파일의 구현만 교체하면 되도록 API 형태를 유지한다.
// ════════════════════════════════════════════════════════

private val prefs by lazy { RunQApplication.instance.getSharedPreferences("runq_local", android.content.Context.MODE_PRIVATE) }
private val gson = Gson()

data class RunRecord(
    val id: String,
    val courseId: String?,      // null이면 자유 러닝
    val courseName: String,
    val timestampMillis: Long,
    val distanceKm: Double,
    val elapsedSeconds: Int,
    val tempLabel: String? = null,
    val pm10Label: String? = null
)

fun RunRecord.dateLabel(): String = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(timestampMillis)
fun RunRecord.timeOfDayLabel(): String = SimpleDateFormat("a h:mm", Locale.KOREA).format(timestampMillis)
fun RunRecord.durationLabel(): String {
    val m = elapsedSeconds / 60; val s = elapsedSeconds % 60
    return "%02d:%02d".format(m, s)
}
fun RunRecord.paceLabel(): String {
    if (distanceKm < 0.01) return "0'00\""
    val paceSec = (elapsedSeconds / distanceKm).toInt()
    return "${paceSec / 60}'${(paceSec % 60).toString().padStart(2, '0')}\""
}

object RunHistoryStore {
    private const val KEY = "run_records"

    fun all(): List<RunRecord> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<RunRecord>>() {}.type
        return runCatching { gson.fromJson<List<RunRecord>>(json, type) }.getOrDefault(emptyList())
            .sortedByDescending { it.timestampMillis }
    }

    fun add(record: RunRecord) {
        val updated = all() + record
        prefs.edit().putString(KEY, gson.toJson(updated)).apply()
    }

    fun get(id: String): RunRecord? = all().find { it.id == id }

    fun totalCount(): Int = all().size
    fun totalKm(): Double = all().sumOf { it.distanceKm }
    fun thisMonthCount(): Int {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR); val month = cal.get(Calendar.MONTH)
        return all().count {
            cal.timeInMillis = it.timestampMillis
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
        }
    }
}

object SavedItemsStore {
    private const val KEY_COURSES = "saved_course_ids"
    private const val KEY_PLACES = "saved_place_ids"
    private const val KEY_NEXT_COURSE = "next_course_id"

    private fun readSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()
    private fun writeSet(key: String, value: Set<String>) { prefs.edit().putStringSet(key, value).apply() }

    fun isCourseSaved(courseId: String): Boolean = courseId in readSet(KEY_COURSES)
    fun toggleCourse(courseId: String) {
        val current = readSet(KEY_COURSES)
        writeSet(KEY_COURSES, if (courseId in current) current - courseId else current + courseId)
    }
    fun savedCourses(): List<Course> {
        val ids = readSet(KEY_COURSES)
        return RunQData.courses.filter { it.id in ids }
    }

    fun isPlaceSaved(placeId: String): Boolean = placeId in readSet(KEY_PLACES)
    fun togglePlace(placeId: String) {
        val current = readSet(KEY_PLACES)
        writeSet(KEY_PLACES, if (placeId in current) current - placeId else current + placeId)
    }
    fun savedPlaces(): List<FinishHubPlace> {
        val ids = readSet(KEY_PLACES)
        return RunQData.places.filter { (it.id ?: "") in ids }
    }

    // "다음 러닝으로 설정" — Home의 오늘의 추천 코스가 이 값을 최우선으로 보여준다.
    var nextCourseId: String?
        get() = prefs.getString(KEY_NEXT_COURSE, null)
        set(value) { prefs.edit().putString(KEY_NEXT_COURSE, value).apply() }
}

object ProfileStore {
    private const val KEY_NICKNAME = "profile_nickname"
    private const val KEY_PUSH = "profile_push_enabled"

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "러너") ?: "러너"
        set(value) { prefs.edit().putString(KEY_NICKNAME, value).apply() }

    var pushEnabled: Boolean
        get() = prefs.getBoolean(KEY_PUSH, true)
        set(value) { prefs.edit().putBoolean(KEY_PUSH, value).apply() }
}

// 회원탈퇴/로그아웃 시 기기에 남은 러닝 기록·저장 목록·프로필을 모두 지운다.
// (실제 서버 계정이 없는 현재 구조에서 "탈퇴"가 의미할 수 있는 유일한 로컬 동작)
fun clearAllLocalData() {
    prefs.edit().clear().apply()
}
