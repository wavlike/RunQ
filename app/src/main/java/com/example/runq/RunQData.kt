package com.example.runq

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

// ════════════════════════════════════════════════════════
// RunQ 콘텐츠 DB (RunQ_DB.xlsx에서 내려받은 큐레이션 데이터)
// - 01_RUN → Course, 02~04(EAT/CAFE/SEE) → Place, 05_FINISH_HUB → FinishHub
// - 좌표/route_points/외부 API ID/실거리 등은 아직 DRAFT/빈칸 상태 그대로 유지합니다.
//   (확정 전 값을 추측해서 채우지 않는다는 원칙에 따름)
// ════════════════════════════════════════════════════════

data class Course(
    @SerializedName("course_id") val courseId: String,
    @SerializedName("course_name") val courseName: String,
    @SerializedName("headline") val headline: String?,
    @SerializedName("region") val region: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("cover_image_url") val coverImageUrl: String?,
    @SerializedName("distance_km") val distanceKm: Double?,
    @SerializedName("estimated_time_min") val estimatedTimeMin: Int?,
    @SerializedName("estimated_time_max") val estimatedTimeMax: Int?,
    @SerializedName("terrain") val terrain: String?,
    @SerializedName("scenery_type") val sceneryType: String?,
    @SerializedName("traffic_level") val trafficLevel: String?,
    @SerializedName("difficulty") val difficulty: String?,
    @SerializedName("start_pin_name") val startPinName: String?,
    @SerializedName("start_lat") val startLat: Double?,
    @SerializedName("start_lng") val startLng: Double?,
    @SerializedName("finish_pin_name") val finishPinName: String?,
    @SerializedName("finish_lat") val finishLat: Double?,
    @SerializedName("finish_lng") val finishLng: Double?,
    @SerializedName("finish_hub_id") val finishHubId: String?,
    @SerializedName("secondary_finish_hub_id") val secondaryFinishHubId: String?,
    @SerializedName("search_radius_m") val searchRadiusM: Int?,
    @SerializedName("route_points_file") val routePointsFile: String?,
    @SerializedName("weather_nx") val weatherNx: Int?,
    @SerializedName("weather_ny") val weatherNy: Int?,
    @SerializedName("is_featured") val isFeatured: Boolean = false,
    @SerializedName("display_order") val displayOrder: Int = 0,
    @SerializedName("status") val status: String = "DRAFT",
    @SerializedName("source_url") val sourceUrl: String?,
    @SerializedName("content_owner_note") val contentOwnerNote: String?
)

data class Place(
    @SerializedName("place_id") val placeId: String,
    @SerializedName("place_name") val placeName: String,
    @SerializedName("category") val category: String, // EAT / CAFE / SEE
    @SerializedName("finish_hub_id") val finishHubId: String?,
    @SerializedName("course_id") val courseId: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lng") val lng: Double?,
    @SerializedName("runq_image_url") val runqImageUrl: String?,
    @SerializedName("tourapi_content_id") val tourapiContentId: String?,
    @SerializedName("tourapi_image_url") val tourapiImageUrl: String?,
    @SerializedName("kakao_place_id") val kakaoPlaceId: String?,
    @SerializedName("kakao_place_url") val kakaoPlaceUrl: String?,
    @SerializedName("short_copy") val shortCopy: String?,
    @SerializedName("recommend_reason") val recommendReason: String?,
    @SerializedName("distance_from_hub_m") val distanceFromHubM: Int?,
    @SerializedName("walking_time_min") val walkingTimeMin: Int?,
    @SerializedName("content_type_id") val contentTypeId: String?,
    @SerializedName("is_featured") val isFeatured: Boolean = false,
    @SerializedName("display_order") val displayOrder: Int = 0,
    @SerializedName("status") val status: String = "DRAFT",
    @SerializedName("source_url") val sourceUrl: String?,
    @SerializedName("content_owner_note") val contentOwnerNote: String?
)

data class FinishHub(
    @SerializedName("finish_hub_id") val finishHubId: String,
    @SerializedName("hub_code") val hubCode: String?,
    @SerializedName("hub_name") val hubName: String,
    @SerializedName("course_id") val courseId: String?,
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lng") val lng: Double?,
    @SerializedName("eat_radius_m") val eatRadiusM: Int?,
    @SerializedName("cafe_radius_m") val cafeRadiusM: Int?,
    @SerializedName("see_radius_m") val seeRadiusM: Int?,
    @SerializedName("default_eat_count") val defaultEatCount: Int = 3,
    @SerializedName("default_cafe_count") val defaultCafeCount: Int = 3,
    @SerializedName("default_see_count") val defaultSeeCount: Int = 2,
    @SerializedName("status") val status: String = "ACTIVE",
    @SerializedName("source_url") val sourceUrl: String?,
    @SerializedName("content_owner_note") val contentOwnerNote: String?
)

const val CATEGORY_EAT = "EAT"
const val CATEGORY_CAFE = "CAFE"
const val CATEGORY_SEE = "SEE"

// 한글 표시용 라벨 매핑 (엑셀은 영문 enum, 화면은 기존처럼 한글로)
fun difficultyLabel(difficulty: String?): String = when (difficulty) {
    "EASY" -> "쉬움"
    "NORMAL" -> "보통"
    "HARD" -> "어려움"
    else -> "정보 준비중"
}

fun terrainLabel(terrain: String?): String = when (terrain) {
    "FLAT" -> "평지"
    "ROLLING" -> "완만한 오르내리막"
    "HILL" -> "언덕"
    "MIXED" -> "혼합"
    "TRAIL" -> "트레일"
    "COAST" -> "해안"
    else -> "기타"
}

fun difficultyRank(difficulty: String?): Int = when (difficulty) {
    "EASY" -> 0
    "NORMAL" -> 1
    "HARD" -> 2
    else -> 3
}

fun formatEstimatedTime(min: Int?, max: Int?): String = when {
    min != null && max != null -> "약 ${min}~${max}분"
    min != null -> "약 ${min}분"
    else -> "정보 준비중"
}

fun categoryLabel(category: String): String = when (category) {
    CATEGORY_EAT -> "맛집"
    CATEGORY_CAFE -> "카페"
    CATEGORY_SEE -> "볼거리"
    else -> category
}

object RunQDatabase {
    private val gson = Gson()

    var courses: List<Course> = emptyList()
        private set
    var places: List<Place> = emptyList()
        private set
    var finishHubs: List<FinishHub> = emptyList()
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        courses = readJsonArray(context, "runq_db/courses.json", object : TypeToken<List<Course>>() {}.type)
        places = readJsonArray(context, "runq_db/places.json", object : TypeToken<List<Place>>() {}.type)
        finishHubs = readJsonArray(context, "runq_db/finish_hubs.json", object : TypeToken<List<FinishHub>>() {}.type)
        initialized = true
    }

    private fun <T> readJsonArray(context: Context, assetPath: String, type: java.lang.reflect.Type): List<T> {
        val json = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return gson.fromJson(json, type)
    }

    // HIDDEN만 실제로 숨김 처리. DRAFT는 현장/좌표 검증 전 콘텐츠라도 화면에는 노출합니다.
    fun visibleCourses(): List<Course> =
        courses.filter { it.status != "HIDDEN" }.sortedBy { it.displayOrder }

    fun courseById(courseId: String?): Course? = courseId?.let { id -> courses.firstOrNull { it.courseId == id } }

    fun finishHub(hubId: String?): FinishHub? = hubId?.let { id -> finishHubs.firstOrNull { it.finishHubId == id } }

    // 장소는 ACTIVE만 노출합니다 (DRAFT는 장소명 자체가 축약/오타 우려로 검증 전인 항목).
    fun placesForHub(hubId: String?, category: String, limit: Int? = null): List<Place> {
        if (hubId == null) return emptyList()
        val filtered = places
            .filter { it.finishHubId == hubId && it.category == category && it.status == "ACTIVE" }
            .sortedWith(compareByDescending<Place> { it.isFeatured }.thenBy { it.displayOrder })
        return if (limit != null) filtered.take(limit) else filtered
    }

    fun defaultCountFor(hub: FinishHub?, category: String): Int = when (category) {
        CATEGORY_EAT -> hub?.defaultEatCount ?: 3
        CATEGORY_CAFE -> hub?.defaultCafeCount ?: 3
        CATEGORY_SEE -> hub?.defaultSeeCount ?: 2
        else -> 3
    }
}

// 00_README 이미지 처리 원칙: 1) RunQ 지정 이미지 → 2) API(TourAPI 등) 이미지 → 3) 없으면 빈칸(placeholder)
fun resolvedImageUrl(place: Place, tourApiFallback: String? = null): String? =
    place.runqImageUrl?.takeIf { it.isNotBlank() }
        ?: place.tourapiImageUrl?.takeIf { it.isNotBlank() }
        ?: tourApiFallback?.takeIf { it.isNotBlank() }

fun resolvedCoverImageUrl(course: Course): String? = course.coverImageUrl?.takeIf { it.isNotBlank() }

// 사진이 없으면 텍스트/URL을 억지로 보여주지 않고 빈 자리(아이콘 박스)로 남겨둡니다.
@Composable
fun RemoteImageOrPlaceholder(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (url.isNullOrBlank()) {
        Box(modifier = modifier.background(RunBgGray), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, contentDescription = null, tint = RunGray)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
