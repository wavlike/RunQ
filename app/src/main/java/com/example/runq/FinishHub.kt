package com.example.runq

// ════════════════════════════════════════════════════════
// RunQ 도메인 모델 — "RunQ DB" 엑셀(01_RUN/05_FINISH_HUB/02~04_EAT·CAFE·SEE) 스키마와 1:1 대응.
// 실제 값은 RunQData.kt가 assets/data/*.json(엑셀을 그대로 옮긴 파일)을 읽어서 채운다.
// 코드에 콘텐츠를 직접 박지 않고, 콘텐츠 데이터는 파일로 분리해 관리한다.
// ════════════════════════════════════════════════════════

data class RoutePoint(val lat: Double, val lng: Double)

data class Course(
    val id: String,                          // course_id
    val name: String,                        // course_name
    val headline: String?,                   // RunQ Picks 카드 카피
    val region: String,
    val location: String?,
    val coverImageUrl: String?,
    val distanceKm: Double?,
    val estimatedTimeMin: Int?,
    val estimatedTimeMax: Int?,
    val terrain: Terrain,
    val sceneryType: String?,
    val trafficLevel: TrafficLevel,
    val difficulty: Difficulty,
    val startPinName: String?,
    val startLat: Double?,
    val startLng: Double?,
    val finishPinName: String?,
    val finishLat: Double?,
    val finishLng: Double?,
    val finishHubId: String?,
    val secondaryFinishHubId: String?,
    val searchRadiusM: Int?,
    val routePointsFile: String?,
    val weatherNx: Int?,
    val weatherNy: Int?,
    val isFeatured: Boolean,
    val displayOrder: Int,
    val status: ContentStatus,
    val sourceUrl: String?,
    val contentOwnerNote: String?,
    val routePoints: List<RoutePoint> = emptyList(),
    val rating: Double = 4.5                 // 실제 평점 시스템 붙기 전까지의 플레이스홀더 (콘텐츠 데이터 아님)
) {
    val finishHubIds: List<String> get() = listOfNotNull(finishHubId, secondaryFinishHubId)

    fun startPoint(): RoutePoint? = startLat?.let { lat -> startLng?.let { lng -> RoutePoint(lat, lng) } }
    fun finishPoint(): RoutePoint? = finishLat?.let { lat -> finishLng?.let { lng -> RoutePoint(lat, lng) } }
}

fun Course.distanceLabel(): String = distanceKm?.let { "%.1fKM".format(it) } ?: "거리 확인중"
fun Course.distanceRangeLabel(): String = distanceKm?.let { "약 ${it}km" } ?: "거리 확인중"
fun Course.timeLabel(): String = when {
    estimatedTimeMin != null && estimatedTimeMax != null -> "약 ${estimatedTimeMin}~${estimatedTimeMax}분"
    estimatedTimeMin != null -> "약 ${estimatedTimeMin}분"
    else -> "예상 시간 확인중"
}
fun Course.sceneryLabel(): String = sceneryType?.takeIf { it.isNotBlank() } ?: terrain.label
fun Course.locationLabel(): String = location?.takeIf { it.isNotBlank() } ?: region
fun Course.reasonText(): String = headline?.takeIf { it.isNotBlank() }
    ?: "러닝 끝, 즐거움 시작.\nEAT · CAFE · SEE로 이어가요."

// 코스별 기상청 격자(nx, ny). 엑셀에 값이 있으면 그대로 쓰고, 없으면 시작/종료 좌표로
// 즉석 계산한다(계산값을 콘텐츠 JSON에 되써넣지는 않음 — 화면 표시용 파생값일 뿐).
fun Course.weatherGrid(): Pair<Int, Int>? {
    if (weatherNx != null && weatherNy != null) return weatherNx to weatherNy
    val lat = startLat ?: finishLat ?: return null
    val lng = startLng ?: finishLng ?: return null
    return latLngToWeatherGrid(lat, lng)
}

// 코스 거리 구간 매칭 (Course List/조건추천 화면의 필터 버튼용)
fun Course.matchesDistanceBucket(bucket: String): Boolean {
    val km = distanceKm ?: return false
    return when (bucket) {
        "짧은코스" -> km <= 4.0
        "5K" -> km in 3.5..6.5
        "중거리" -> km in 6.0..10.0
        "10K" -> km in 8.5..11.5
        "장거리" -> km > 11.0
        "3km" -> km <= 3.5
        "5km" -> km in 3.5..7.0
        "10km+" -> km > 8.5
        else -> true
    }
}

data class FinishHub(
    val id: String,                 // finish_hub_id
    val hubCode: String,            // 노션 A~R
    val name: String,               // hub_name
    val courseId: String?,
    val lat: Double?,
    val lng: Double?,
    val eatRadiusM: Int,
    val cafeRadiusM: Int,
    val seeRadiusM: Int,
    val defaultEatCount: Int,
    val defaultCafeCount: Int,
    val defaultSeeCount: Int,
    val status: ContentStatus,
    val sourceUrl: String?,
    val contentOwnerNote: String?
) {
    // 노션에 아직 실측 좌표가 없는 Hub가 많아 지명 기준 근사 좌표로 대체한다.
    // ⚠️ 콘텐츠 데이터(JSON)에는 추측값을 넣지 않기로 했으므로, 이 fallback은 코드 레벨에만 존재한다.
    val resolvedLat: Double get() = lat ?: approxHubCenters[hubCode]?.lat ?: 37.7519
    val resolvedLng: Double get() = lng ?: approxHubCenters[hubCode]?.lng ?: 128.8761
}

// ⚠️ 지명 기반 근사 좌표 플레이스홀더 — 실측 데이터로 교체 예정
private val approxHubCenters: Map<String, RoutePoint> = mapOf(
    "A" to RoutePoint(37.7936, 128.9163), "B" to RoutePoint(37.7713, 128.9470),
    "C" to RoutePoint(37.8046, 128.9086), "D" to RoutePoint(37.8995, 128.8281),
    "E" to RoutePoint(37.8579, 128.8477), "F" to RoutePoint(37.7519, 128.8971),
    "G" to RoutePoint(37.7524, 128.9291), "H" to RoutePoint(37.6512, 129.0355),
    "I" to RoutePoint(37.6906, 129.0343), "J" to RoutePoint(37.7220, 129.0130),
    "K" to RoutePoint(37.7524, 128.8781), "L" to RoutePoint(37.7280, 128.8642),
    "M" to RoutePoint(37.7280, 128.8060), "N" to RoutePoint(37.8280, 128.8580),
    "O" to RoutePoint(37.7060, 128.7480), "P" to RoutePoint(37.8720, 128.8390),
    "Q" to RoutePoint(37.9058, 128.8272), "R" to RoutePoint(37.6750, 128.7960)
)

fun findHub(id: String?): FinishHub? = RunQData.finishHubs.find { it.id == id }
