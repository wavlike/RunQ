package com.example.runq

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ════════════════════════════════════════════════════════
// assets/data/*.json → 도메인 모델(Course/FinishHub/FinishHubPlace) 로더.
// 이 JSON들은 RunQ 팀이 정리한 "RunQ DB" 엑셀을 그대로 옮긴 파일이라, 아직 비어있는
// 필드가 많다(좌표/이미지/API ID 등). 비어있으면 null/UNKNOWN/DRAFT로 안전하게 떨어뜨리고,
// 절대 앱이 죽지 않게 한다 — 나머지는 다음 단계(API 보강)에서 채운다.
// ════════════════════════════════════════════════════════

private data class CourseJson(
    val course_id: String?, val course_name: String?, val headline: String?,
    val region: String?, val location: String?, val cover_image_url: String?,
    val distance_km: Double?, val estimated_time_min: Int?, val estimated_time_max: Int?,
    val terrain: String?, val scenery_type: String?, val traffic_level: String?, val difficulty: String?,
    val start_pin_name: String?, val start_lat: Double?, val start_lng: Double?,
    val finish_pin_name: String?, val finish_lat: Double?, val finish_lng: Double?,
    val finish_hub_id: String?, val secondary_finish_hub_id: String?, val search_radius_m: Int?,
    val route_points_file: String?, val weather_nx: Int?, val weather_ny: Int?,
    val is_featured: Boolean?, val display_order: Int?, val status: String?,
    val source_url: String?, val content_owner_note: String?
)

private data class FinishHubJson(
    val finish_hub_id: String?, val hub_code: String?, val hub_name: String?, val course_id: String?,
    val lat: Double?, val lng: Double?, val eat_radius_m: Int?, val cafe_radius_m: Int?, val see_radius_m: Int?,
    val default_eat_count: Int?, val default_cafe_count: Int?, val default_see_count: Int?,
    val status: String?, val source_url: String?, val content_owner_note: String?
)

private data class PlaceJson(
    val place_id: String?, val place_name: String?, val category: String?,
    val finish_hub_id: String?, val course_id: String?, val address: String?,
    val lat: Double?, val lng: Double?, val runq_image_url: String?,
    val tourapi_content_id: String?, val tourapi_image_url: String?,
    val kakao_place_id: String?, val kakao_place_url: String?,
    val short_copy: String?, val recommend_reason: String?,
    val distance_from_hub_m: Double?, val walking_time_min: Double?, val content_type_id: String?,
    val is_featured: Boolean?, val display_order: Int?, val status: String?,
    val source_url: String?, val content_owner_note: String?
)

private data class RoutePointJson(val lat: Double, val lng: Double)

object RunQData {
    private val gson = Gson()

    val courses: List<Course> by lazy { loadCourses() }
    val finishHubs: List<FinishHub> by lazy { loadFinishHubs() }
    val places: List<FinishHubPlace> by lazy { loadPlaces() }

    private fun readAsset(path: String): String? = runCatching {
        RunQApplication.instance.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { Log.w("RunQData", "asset 읽기 실패: $path", it) }.getOrNull()

    private fun loadCourses(): List<Course> {
        val json = readAsset("data/courses.json") ?: return emptyList()
        val type = object : TypeToken<List<CourseJson>>() {}.type
        val raw: List<CourseJson> = runCatching { gson.fromJson<List<CourseJson>>(json, type) }.getOrDefault(emptyList())
        return raw.mapNotNull { c ->
            val id = c.course_id ?: return@mapNotNull null
            Course(
                id = id,
                name = c.course_name ?: id,
                headline = c.headline,
                region = c.region ?: "강릉",
                location = c.location,
                coverImageUrl = c.cover_image_url,
                distanceKm = c.distance_km,
                estimatedTimeMin = c.estimated_time_min,
                estimatedTimeMax = c.estimated_time_max,
                terrain = parseTerrain(c.terrain),
                sceneryType = c.scenery_type,
                trafficLevel = parseTrafficLevel(c.traffic_level),
                difficulty = parseDifficulty(c.difficulty),
                startPinName = c.start_pin_name,
                startLat = c.start_lat,
                startLng = c.start_lng,
                finishPinName = c.finish_pin_name,
                finishLat = c.finish_lat,
                finishLng = c.finish_lng,
                finishHubId = c.finish_hub_id,
                secondaryFinishHubId = c.secondary_finish_hub_id,
                searchRadiusM = c.search_radius_m,
                routePointsFile = c.route_points_file,
                weatherNx = c.weather_nx,
                weatherNy = c.weather_ny,
                isFeatured = c.is_featured ?: false,
                displayOrder = c.display_order ?: 999,
                status = parseContentStatus(c.status),
                sourceUrl = c.source_url,
                contentOwnerNote = c.content_owner_note,
                routePoints = c.route_points_file?.let { loadRoutePoints(it) } ?: emptyList()
            )
        }.sortedBy { it.displayOrder }
    }

    private fun loadRoutePoints(assetPath: String): List<RoutePoint> {
        val json = readAsset("data/$assetPath") ?: return emptyList()
        val type = object : TypeToken<List<RoutePointJson>>() {}.type
        val raw: List<RoutePointJson> = runCatching { gson.fromJson<List<RoutePointJson>>(json, type) }.getOrDefault(emptyList())
        return raw.map { RoutePoint(it.lat, it.lng) }
    }

    private fun loadFinishHubs(): List<FinishHub> {
        val json = readAsset("data/finish_hubs.json") ?: return emptyList()
        val type = object : TypeToken<List<FinishHubJson>>() {}.type
        val raw: List<FinishHubJson> = runCatching { gson.fromJson<List<FinishHubJson>>(json, type) }.getOrDefault(emptyList())
        return raw.mapNotNull { h ->
            val id = h.finish_hub_id ?: return@mapNotNull null
            FinishHub(
                id = id,
                hubCode = h.hub_code ?: "?",
                name = h.hub_name ?: id,
                courseId = h.course_id,
                lat = h.lat,
                lng = h.lng,
                eatRadiusM = h.eat_radius_m ?: 1500,
                cafeRadiusM = h.cafe_radius_m ?: 1500,
                seeRadiusM = h.see_radius_m ?: 1500,
                defaultEatCount = h.default_eat_count ?: 3,
                defaultCafeCount = h.default_cafe_count ?: 3,
                defaultSeeCount = h.default_see_count ?: 2,
                status = parseContentStatus(h.status),
                sourceUrl = h.source_url,
                contentOwnerNote = h.content_owner_note
            )
        }
    }

    private fun loadPlaces(): List<FinishHubPlace> {
        val json = readAsset("data/places.json") ?: return emptyList()
        val type = object : TypeToken<List<PlaceJson>>() {}.type
        val raw: List<PlaceJson> = runCatching { gson.fromJson<List<PlaceJson>>(json, type) }.getOrDefault(emptyList())
        return raw.mapNotNull { p ->
            val category = when (p.category?.trim()?.uppercase()) {
                "EAT" -> PlaceCategory.EAT
                "CAFE" -> PlaceCategory.CAFE
                "SEE" -> PlaceCategory.SEE
                else -> return@mapNotNull null
            }
            FinishHubPlace(
                title = p.place_name ?: p.place_id ?: "이름 미정",
                addr = p.address ?: "",
                category = category,
                contentId = p.tourapi_content_id,
                distMeters = p.distance_from_hub_m?.let { it.toInt().toString() },
                id = p.place_id,
                finishHubId = p.finish_hub_id,
                lat = p.lat,
                lng = p.lng,
                shortCopy = p.short_copy,
                recommendReason = p.recommend_reason,
                isFeatured = p.is_featured ?: false,
                displayOrder = p.display_order ?: 999,
                status = parseContentStatus(p.status),
                isCurated = true,
                imageUrl = p.runq_image_url?.takeIf { it.isNotBlank() } ?: p.tourapi_image_url?.takeIf { it.isNotBlank() }
            )
        }
    }
}
