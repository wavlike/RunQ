package com.example.runq

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════
// Finish Hub 기준 EAT / CAFE / SEE 조회
//
// 코스 완주 → course.finishHubIds[0] → findHub() → hub 좌표+반경으로
// TourAPI locationBasedList2를 호출한다. RunQData.places(엑셀 큐레이션)에 있는
// 장소를 "RunQ Pick"으로 맨 위에 고정하고, API 결과는 뒤에 보충한다.
// ════════════════════════════════════════════════════════

// EVENT는 Figma Foundations에 지정된 4번째 강조색이 없어서, 새 색을 만들지 않고
// 기존 팔레트의 중립색(RunGray)을 그대로 재사용한다. accent는 칩 배경(검정 텍스트가
// 얹힘)과 단독 텍스트색 둘 다로 쓰이는데, RunGray는 앱 전체에서 이미 보조 텍스트색으로
// 쓰이고 있어 어느 쪽으로 써도 눈에 잘 띄지 않거나 안 보이는 문제가 없다.
enum class PlaceCategory(val label: String, val accent: Color) {
    EAT("EAT", RunLime), CAFE("CAFE", RunPurple), SEE("SEE", RunLavender), EVENT("행사", RunGray)
}

data class FinishHubPlace(
    val title: String,
    val addr: String,
    val category: PlaceCategory,
    val contentId: String? = null,     // TourAPI 결과일 때만 값이 있음 → Place Detail에서 상세조회 가능
    val contentTypeId: String? = null, // TourAPI 콘텐츠타입(12관광지/39음식점/15행사 등) — detailIntro2 조회에 필요
    val distMeters: String? = null,    // TourAPI dist(m) 또는 distance_from_hub_m
    val id: String? = null,            // place_id (RunQ 큐레이션 항목만)
    val finishHubId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val shortCopy: String? = null,
    val recommendReason: String? = null,
    val isFeatured: Boolean = false,
    val displayOrder: Int = 999,
    val status: ContentStatus = ContentStatus.ACTIVE,
    val isCurated: Boolean = false,    // RunQ 큐레이션(엑셀) 출처인지
    val eventStartDate: String? = null, // 행사(EVENT)만 사용 — yyyyMMdd
    val eventEndDate: String? = null,   // 행사(EVENT)만 사용 — yyyyMMdd
    val imageUrl: String? = null        // 큐레이션(runq_image_url/tourapi_image_url) 또는 TourAPI firstimage
)

// 행사 진행 상태 — 오늘 날짜와 시작/종료일을 비교해 계산한다(API가 상태를 안 줘서 직접 판정).
enum class FestivalStatus(val label: String, val color: Color) {
    ONGOING("진행중", RunLime), UPCOMING("예정", RunPurple), ENDED("마감", RunGray)
}

private fun parseYmd(ymd: String?): java.util.Date? {
    if (ymd == null || ymd.length != 8) return null
    return runCatching { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.KOREA).parse(ymd) }.getOrNull()
}

fun FinishHubPlace.festivalStatus(): FestivalStatus? {
    val start = parseYmd(eventStartDate) ?: return null
    val end = parseYmd(eventEndDate) ?: start
    val today = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.time
    return when {
        today.before(start) -> FestivalStatus.UPCOMING
        today.after(end) -> FestivalStatus.ENDED
        else -> FestivalStatus.ONGOING
    }
}

fun FinishHubPlace.festivalPeriodLabel(): String? {
    fun format(ymd: String?): String? {
        if (ymd == null || ymd.length != 8) return null
        return "${ymd.substring(4, 6).toInt()}.${ymd.substring(6, 8).toInt()}"
    }
    val start = format(eventStartDate) ?: return null
    val end = format(eventEndDate)
    return if (end == null || end == start) start else "$start ~ $end"
}

data class FinishHubResult(
    val hub: FinishHub,
    val eat: List<FinishHubPlace>,
    val cafe: List<FinishHubPlace>,
    val see: List<FinishHubPlace>
)

private val cafeKeywords = listOf("카페", "커피", "베이커리", "로스터리", "coffee", "cafe")
private val seeContentTypeIds = listOf(12, 14, 15, 28) // 관광지 / 문화시설 / 축제행사 / 레포츠

// "추천순" 정렬에서 사진/소개문구가 다 채워진 곳을 위로 올리기 위한 점수.
// 운영시간까지는 여기서 못 본다 — 그건 상세화면에서만 TourAPI(detailIntro2)로 조회하는
// 정보라, 목록 단계에서 쓰려면 장소 수만큼 추가 API 호출이 필요해서 뺐다.
private fun placeRichnessScore(place: FinishHubPlace): Int {
    var score = 0
    if (place.imageUrl != null) score += 1
    if (!place.shortCopy.isNullOrBlank()) score += 1
    return score
}

// RunQData.places(엑셀 큐레이션)를 먼저 노출하고, 이름이 겹치지 않는 API 결과만 뒤에 붙인다.
private fun curatedPlaces(hub: FinishHub, category: PlaceCategory): List<FinishHubPlace> =
    RunQData.places
        .filter { it.finishHubId == hub.id && it.category == category && it.status != ContentStatus.HIDDEN }
        .sortedBy { it.displayOrder }

// distance_from_hub_m이 아직 비어있어도 장소/Hub 둘 다 좌표가 있으면 화면 표시용으로
// 즉석 계산한다(콘텐츠 JSON에는 쓰지 않음 — 계산값과 팀이 확정한 값을 구분해서 다룬다).
fun FinishHubPlace.distanceMeters(hub: FinishHub?): Double? {
    distMeters?.toDoubleOrNull()?.let { return it }
    val plat = lat; val plng = lng
    if (hub == null || plat == null || plng == null) return null
    return haversineMeters(hub.resolvedLat, hub.resolvedLng, plat, plng)
}

fun FinishHubPlace.distanceLabel(hub: FinishHub?): String? = distanceMeters(hub)?.let { "${it.toInt()}m" }

private fun mergeCurated(curated: List<FinishHubPlace>, apiResults: List<FinishHubPlace>): List<FinishHubPlace> {
    val curatedTitles = curated.map { it.title }
    val rest = apiResults.filter { api -> curatedTitles.none { it.contains(api.title) || api.title.contains(it) } }
    return curated + rest
}

// TourAPI 결과를 FinishHubPlace로 옮길 때 mapX/mapY(좌표)까지 같이 넘긴다.
// (예전엔 안 넘겨서 API로 가져온 장소가 지도에 안 찍히는 버그가 있었음)
private fun TourPlace.toFinishHubPlace(category: PlaceCategory): FinishHubPlace = FinishHubPlace(
    title = title ?: "-",
    addr = addr1 ?: "",
    category = category,
    contentId = contentId,
    contentTypeId = contentTypeId,
    distMeters = dist,
    lat = mapY?.toDoubleOrNull(),
    lng = mapX?.toDoubleOrNull(),
    imageUrl = firstImage?.takeIf { it.isNotBlank() }
)

suspend fun fetchFinishHubPlaces(hub: FinishHub): FinishHubResult {
    val hubLat = hub.resolvedLat
    val hubLng = hub.resolvedLng

    // EAT + CAFE 후보: TourAPI 음식점(contentTypeId=39)에서 카페 키워드로 1차 분리
    // (TourAPI에는 카페 전용 contentTypeId가 없음 → 아래에서 Kakao Local CE7로 보완)
    val foodItems = runCatching {
        TourApiClient.api.getNearbyPlaces(
            mapX = hubLng, mapY = hubLat, radius = maxOf(hub.eatRadiusM, hub.cafeRadiusM), contentTypeId = 39
        ).response.body.items?.item ?: emptyList()
    }.getOrDefault(emptyList())

    val cafeFromTourApi = foodItems.filter { p -> cafeKeywords.any { (p.title ?: "").contains(it, true) } }
        .map { it.toFinishHubPlace(PlaceCategory.CAFE) }
    val eatApiRaw = foodItems.filterNot { p -> cafeKeywords.any { (p.title ?: "").contains(it, true) } }
        .map { it.toFinishHubPlace(PlaceCategory.EAT) }

    // Kakao Local CE7(카페) 보완 — TourAPI 키워드 필터만으로는 카페가 잘 안 잡히므로
    // 좌표 기반 카테고리 검색으로 채운다(REST 키 없으면 빈 목록, 조용히 건너뜀).
    val cafeFromKakao = fetchKakaoCafesNear(hubLat, hubLng, hub.cafeRadiusM).map {
        FinishHubPlace(
            title = it.placeName ?: "-",
            addr = it.roadAddressName?.takeIf { addr -> addr.isNotBlank() } ?: it.addressName ?: "",
            category = PlaceCategory.CAFE,
            contentId = null,
            distMeters = it.distance,
            lat = it.y?.toDoubleOrNull(),
            lng = it.x?.toDoubleOrNull()
        )
    }
    val cafeApiRaw = (cafeFromTourApi + cafeFromKakao).distinctBy { it.title.trim() }

    // SEE: 관광지/문화시설/행사/레포츠 여러 contentTypeId를 합쳐서 조회
    val seeApiRaw = seeContentTypeIds.flatMap { typeId ->
        runCatching {
            TourApiClient.api.getNearbyPlaces(
                mapX = hubLng, mapY = hubLat, radius = hub.seeRadiusM, contentTypeId = typeId
            ).response.body.items?.item ?: emptyList()
        }.getOrDefault(emptyList())
    }.distinctBy { it.title }
        .map { it.toFinishHubPlace(PlaceCategory.SEE) }

    return FinishHubResult(
        hub = hub,
        eat = mergeCurated(curatedPlaces(hub, PlaceCategory.EAT), eatApiRaw),
        cafe = mergeCurated(curatedPlaces(hub, PlaceCategory.CAFE), cafeApiRaw),
        see = mergeCurated(curatedPlaces(hub, PlaceCategory.SEE), seeApiRaw)
    )
}

// 강릉 행사/축제(TourAPI searchFestival2) — 특정 Finish Hub에 묶이지 않고 강릉시 전체를 조회한다.
// eventStartDate를 오늘이 아니라 2주 전으로 넘겨서 "최근에 막 끝난(마감)" 행사까지 같이 받아오고,
// 진행중/예정/마감 상태는 여기서 직접 계산해 붙인다(TourAPI가 상태 자체를 주진 않음).
suspend fun fetchFestivals(): List<FinishHubPlace> {
    val queryFrom = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, -14) }.time
    val queryFromYmd = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.KOREA).format(queryFrom)
    val items = runCatching {
        TourApiClient.api.searchFestivals(eventStartDate = queryFromYmd).response.body.items?.item ?: emptyList()
    }.getOrDefault(emptyList())

    val places = items.map { f ->
        FinishHubPlace(
            title = f.title ?: "-",
            addr = f.addr1 ?: "",
            category = PlaceCategory.EVENT,
            contentId = f.contentId,
            contentTypeId = "15", // searchFestival2 결과는 항상 행사(15) 콘텐츠타입
            lat = f.mapY?.toDoubleOrNull(),
            lng = f.mapX?.toDoubleOrNull(),
            eventStartDate = f.eventStartDate,
            eventEndDate = f.eventEndDate,
            imageUrl = f.firstImage?.takeIf { it.isNotBlank() }
        )
    }
    // 진행중 → 예정 → 마감 순으로, 상태가 같으면 시작일이 빠른 순으로 보여준다.
    return places.sortedWith(
        compareBy(
            { place -> when (place.festivalStatus()) { FestivalStatus.ONGOING -> 0; FestivalStatus.UPCOMING -> 1; else -> 2 } },
            { it.eventStartDate ?: "" }
        )
    )
}

// ════════════════════════════════════════════════════════
// 공통 컴포넌트: 코스 지도 카드 (Kakao MapView)
// (Run Ready / Active / Paused / Complete / Course Detail Route에서 공용)
// ════════════════════════════════════════════════════════
@Composable
fun CourseMapCard(
    course: Course,
    title: String? = null,
    eyebrow: String = "ROUTE MAP",
    heightDp: Int = 260,
    currentLocation: RoutePoint? = null
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(24.dp))
            .border(1.dp, RunBorderGray, RoundedCornerShape(24.dp))
    ) {
        KakaoRouteMap(
            modifier = Modifier.fillMaxSize(),
            routePoints = course.routePoints,
            startPoint = course.startPoint(),
            finishPoint = course.finishPoint(),
            currentLocation = currentLocation
        )
        if (title != null) {
            // Figma "Route Section": 지도 위쪽에 흰색→투명 그라데이션 밴드 + eyebrow/타이틀 스택
            Box(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().height(72.dp)
                    .background(Brush.verticalGradient(listOf(RunWhite.copy(alpha = 0.95f), Color.Transparent)))
            )
            Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 14.dp, start = 16.dp)) {
                Text(eyebrow, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RunGray)
                Spacer(Modifier.height(4.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// Run Ready: Figma "22 Course / Run Ready" 기준
// ════════════════════════════════════════════════════════
@Composable
fun RunReadyScreen(course: Course, onBack: () -> Unit, onStart: () -> Unit) {
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    LaunchedEffect(course.name) {
        safety = runCatching { fetchSafety(course.weatherGrid()) }.getOrNull()
    }

    Box(modifier = Modifier.fillMaxSize().background(RunCream)) {
        Box(
            modifier = Modifier.size(260.dp).offset(x = (-115).dp, y = (-97).dp).blur(60.dp)
                .background(Brush.radialGradient(listOf(RunLime.copy(alpha = 0.5f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier.size(240.dp).offset(x = 235.dp, y = (-32).dp).blur(60.dp)
                .background(Brush.radialGradient(listOf(RunLavender.copy(alpha = 0.5f), Color.Transparent)), CircleShape)
        )

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(56.dp))
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(RunWhite)
                    .border(1.dp, RunBorderGray, CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = RunBlack, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(course.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RunBlack, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            CourseMapCard(course = course, heightDp = 300)
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(RunWhite).padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EnvChip("☀️ ${safety?.temp?.let { "맑음 $it" } ?: "날씨 확인중"}", Color(0xFFFBF8D9), Modifier.weight(1f))
                    EnvChip("🍃 미세먼지 ${safety?.pm10 ?: "-"}", Color(0xFFF1F6EC), Modifier.weight(1f))
                    EnvChip("💨 바람 ${safety?.wind ?: "-"}", Color(0xFFE9F1FB), Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RunBorderGray))
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CourseInfoLine("거리", course.distanceLabel(), Modifier.weight(1f))
                    CourseInfoLine("예상 소요시간", course.timeLabel(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CourseInfoLine("코스 특징", course.terrain.label, Modifier.weight(1f))
                    CourseInfoLine("교통량", course.trafficLevel.label, Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
                ) { Text("러닝 시작하기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EnvChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(23.dp)).background(color).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RunBlack, maxLines = 1)
    }
}

// ════════════════════════════════════════════════════════
// Running: Figma "23 Run / Active" · "24 Run / Paused" 기준
// ════════════════════════════════════════════════════════
@Composable
fun CourseRunningScreen(course: Course, onFinish: (distanceKm: Double, elapsedSeconds: Int) -> Unit) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var hasFix by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showLocationRationale by remember { mutableStateOf(!hasLocationPermission) }
    var showGpsError by remember { mutableStateOf(false) }
    val targetKm = remember(course) { course.resolvedDistanceKm()?.takeIf { it > 0 } ?: 5.0 }
    val routePoints = course.routePoints

    // totalDistanceKm: GPS로 실측한 실제 이동 거리(페이스 계산용) / progressKm: 코스 경로 위로
    // 투영한 진행 거리(진행률·완주 거리 표시용) — RunningScreen(Run 탭)과 동일한 방식.
    var totalDistanceKm by remember { mutableStateOf(0.0) }
    var progressKm by remember { mutableStateOf(0.0) }
    val displayDistanceKm = if (routePoints.isNotEmpty()) progressKm else totalDistanceKm

    var currentLocation by remember { mutableStateOf(course.startPoint() ?: RoutePoint(37.7946, 128.9022)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasLocationPermission = isGranted
    }

    // 이미 달리는 중인데 권한은 있으면서 일정 시간 GPS 신호를 못 잡으면 안내 모달을 띄운다.
    LaunchedEffect(hasLocationPermission, running) {
        if (hasLocationPermission && running) {
            delay(10_000)
            if (!hasFix) showGpsError = true
        }
    }
    LaunchedEffect(hasFix) { if (hasFix) showGpsError = false }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    DisposableEffect(hasLocationPermission, running) {
        if (!hasLocationPermission || !running) return@DisposableEffect onDispose {}

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(3f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLocation = result.lastLocation ?: return
                val newPoint = RoutePoint(lastLocation.latitude, lastLocation.longitude)

                if (hasFix) {
                    val d = haversineMeters(currentLocation.lat, currentLocation.lng, newPoint.lat, newPoint.lng) / 1000.0
                    totalDistanceKm += d
                }

                currentLocation = newPoint
                hasFix = true

                if (routePoints.isNotEmpty()) {
                    progressKm = distanceAlongRouteToClosestPoint(routePoints, newPoint)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)
        } catch (e: SecurityException) { }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            elapsedSeconds += 1
        }
    }

    val progress = remember(displayDistanceKm, targetKm) { (displayDistanceKm / targetKm).toFloat().coerceIn(0f, 1f) }

    val paceLabel = remember(totalDistanceKm, elapsedSeconds) {
        if (totalDistanceKm < 0.01) "0'00\"" else {
            val paceSec = (elapsedSeconds / totalDistanceKm).toInt()
            "${paceSec / 60}'${(paceSec % 60).toString().padStart(2, '0')}\""
        }
    }
    val durationLabel = remember(elapsedSeconds) {
        val h = elapsedSeconds / 3600; val m = (elapsedSeconds % 3600) / 60; val s = elapsedSeconds % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    if (showLocationRationale) {
        PermissionRationaleScreen(
            mascot = R.drawable.mascot_dragon,
            headerTitle = "위치 권한 안내",
            description = "현재 위치를 바탕으로 러닝 코스를 기록하고\n주변의 맛집과 카페를 추천받기 위해\n위치 권한을 허용해 주세요.",
            actionLabel = "위치 권한 허용하기",
            onAction = {
                showLocationRationale = false
                launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            onSkip = { showLocationRationale = false },
            onBack = { showLocationRationale = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(RunCream)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UtilityPill("잠금", Modifier.weight(1f))
                UtilityPill(
                    if (!hasLocationPermission) "위치 권한 필요" else if (hasFix) "GPS" else "GPS 찾는 중",
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(14.dp))
            if (hasLocationPermission) {
                CourseMapCard(course = course, currentLocation = currentLocation, heightDp = 300)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp))
                        .background(RunBgGray),
                    contentAlignment = Alignment.Center
                ) {
                    PermissionPromptView(
                        icon = "📍", title = "위치 권한이 필요해요",
                        message = "실시간 위치 표시와 거리 측정을 위해 위치 권한을 허용해주세요.",
                        actionLabel = "권한 허용하기",
                        onAction = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        modifier = Modifier.padding(20.dp),
                        mascot = R.drawable.mascot_dragon
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(course.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE0DED4))) {
                Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(RunLime))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${String.format("%.2f", displayDistanceKm)} / ${course.distanceLabel()}",
                fontSize = 10.sp, color = RunGray
            )
            Spacer(Modifier.height(24.dp))
            Text(
                String.format("%.2f", displayDistanceKm), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = RunBlack,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text("KM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunGray, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                RunStat(paceLabel, "평균 페이스", Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(58.dp).background(RunBorderGray))
                RunStat(durationLabel, "운동 시간", Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(76.dp).clip(CircleShape).background(RunLime)
                        .clickable { running = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "일시정지", tint = RunBlack, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("일시정지", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            }
            Spacer(Modifier.height(20.dp))
        }

        // Figma "24 Run / Paused": 어둡게 딤 처리 + 안내 문구 + 하단 액션 시트
        if (!running) {
            Box(modifier = Modifier.fillMaxSize().background(RunBlack.copy(alpha = 0.4f)).clickable(enabled = false) {})
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "러닝을 일시 정지했습니다.", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RunWhite,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "계속 달리거나 종료할 수 있어요.", fontSize = 13.sp, color = RunWhite.copy(alpha = 0.85f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(RunWhite).padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                Box(
                    modifier = Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(RunBorderGray).align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "현재 기록은 그대로 유지돼요", fontSize = 12.sp, color = RunGray,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.weight(0.45f).height(56.dp).clip(RoundedCornerShape(28.dp))
                            .background(RunBgGray).clickable { onFinish(displayDistanceKm, elapsedSeconds) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("■  종료하기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    }
                    Box(
                        modifier = Modifier.weight(0.55f).height(56.dp).clip(RoundedCornerShape(28.dp))
                            .background(RunLime).clickable { running = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶  계속 달리기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    }
                }
            }
        }

        if (showGpsError) {
            GpsErrorModal(
                onCancel = { showGpsError = false },
                onOpenSettings = {
                    showGpsError = false
                    try { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    catch (e: ActivityNotFoundException) { /* 설정 화면이 없는 기기 — 조용히 무시 */ }
                }
            )
        }
    }
}

@Composable
private fun UtilityPill(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunBlack)
    }
}

@Composable
private fun RunStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = RunGray)
    }
}

// ════════════════════════════════════════════════════════
// Complete: "25 Run/Complete.png" 기준
// ════════════════════════════════════════════════════════
@Composable
fun CompleteScreen(
    course: Course,
    distanceKm: Double,
    elapsedSeconds: Int,
    onCategoryClick: (PlaceCategory) -> Unit
) {
    val hub = remember(course) { course.finishHubIds.firstOrNull()?.let { findHub(it) } }
    val paceLabel = remember(distanceKm, elapsedSeconds) {
        if (distanceKm < 0.01) "0'00\"" else {
            val paceSec = (elapsedSeconds / distanceKm).toInt()
            "${paceSec / 60}'${(paceSec % 60).toString().padStart(2, '0')}\""
        }
    }
    val durationLabel = remember(elapsedSeconds) {
        val m = elapsedSeconds / 60; val s = elapsedSeconds % 60
        "%02d:%02d".format(m, s)
    }

    // 체중 등 개인 프로필이 없어 정확한 칼로리 계산은 불가 — km당 65kcal 통상치로 대략치만 표시.
    val caloriesEstimate = remember(distanceKm) { (distanceKm * 65).toInt() }

    // 완주 화면에 처음 진입했을 때 딱 한 번만 기록을 저장한다(재구성/회전 시 중복 저장 방지).
    LaunchedEffect(course.id, distanceKm, elapsedSeconds) {
        RunHistoryStore.add(
            RunRecord(
                id = java.util.UUID.randomUUID().toString(),
                courseId = course.id,
                courseName = course.name,
                timestampMillis = System.currentTimeMillis(),
                distanceKm = distanceKm,
                elapsedSeconds = elapsedSeconds
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("RUN COMPLETE", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = RunGray)
        Spacer(Modifier.height(6.dp))
        Text("러닝 완료!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(6.dp))
        Text(course.name, fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
            CourseMapCard(course = course, heightDp = 210)
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)
                    .clip(RoundedCornerShape(15.dp)).background(RunBlack).padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("✓ COMPLETE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunWhite)
            }
        }
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(24.dp)).padding(18.dp)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    CourseInfoLine("거리", String.format("%.2f KM", distanceKm), Modifier.weight(1f))
                    CourseInfoLine("운동 시간", durationLabel, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RunBorderGray))
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CourseInfoLine("평균 페이스", "$paceLabel /KM", Modifier.weight(1f))
                    CourseInfoLine("소모 칼로리", "$caloriesEstimate KCAL", Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Color(0xFFF6F1E0)).padding(18.dp)
        ) {
            Column {
                Box(modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(RunWhite).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("FINISH HUB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "잘 달렸어요.\n이제 근처에서 쉬어갈까요?",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RunBlack, lineHeight = 28.sp
                )
                Spacer(Modifier.height(10.dp))
                if (hub == null) {
                    Text("이 코스는 아직 Finish Hub가 지정되지 않았어요.", fontSize = 12.sp, color = RunGray)
                } else {
                    Text("맛집 · 카페 · 볼거리를 바로 둘러보세요.", fontSize = 11.sp, color = Color(0xFF827D75))
                    Spacer(Modifier.height(6.dp))
                    Text("EAT   ·   CAFE   ·   SEE", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF616157))
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onCategoryClick(PlaceCategory.EAT) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
                    ) { Text("📍  Finish Hub 둘러보기", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// 코스 완주(Complete)·코스 상세(EAT/CAFE/SEE)에서 "Place 탭으로 바로 이동"할 때
// 어떤 Hub/카테고리를 보여줘야 하는지 전달하는 1회성 요청함. 화면 전환(탭 전환) 사이에만
// 잠깐 살아있으면 되므로 영구 저장(LocalStore)이 아니라 메모리 객체로 둔다.
object PlaceTabRequest {
    private var pendingHubId: String? = null
    private var pendingCategory: PlaceCategory? = null
    private var pendingDetailPlace: FinishHubPlace? = null

    fun request(hubId: String?, category: PlaceCategory? = null) {
        pendingHubId = hubId
        pendingCategory = category
    }

    fun consume(): Pair<String?, PlaceCategory?> {
        val result = pendingHubId to pendingCategory
        pendingHubId = null
        pendingCategory = null
        return result
    }

    // 검색 결과 등 다른 탭에서 특정 장소를 콕 집어 상세화면으로 바로 진입시킬 때 사용.
    fun requestDetail(place: FinishHubPlace) {
        pendingDetailPlace = place
    }

    fun consumeDetail(): FinishHubPlace? {
        val result = pendingDetailPlace
        pendingDetailPlace = null
        return result
    }
}

// ════════════════════════════════════════════════════════
// Place 탭 (Hub 자체를 순회) — "30 Places/Home.png"
// ════════════════════════════════════════════════════════
sealed class PlaceStep {
    object Home : PlaceStep()
    data class HubList(val hub: FinishHub, val category: PlaceCategory) : PlaceStep()
    // from: 상세화면으로 오기 전 화면 — 뒤로가기를 눌렀을 때 항상 Place 탭 홈이 아니라
    // 실제로 들어왔던 화면(전체보기 목록 등)으로 돌아가기 위해 CourseStep.Detail과 같은 패턴을 사용.
    data class Detail(val hub: FinishHub, val place: FinishHubPlace, val from: PlaceStep) : PlaceStep()
}

@Composable
fun PlaceFlow() {
    var step by remember {
        mutableStateOf<PlaceStep>(
            PlaceTabRequest.consumeDetail()?.let { place ->
                findHub(place.finishHubId)?.let { hub -> PlaceStep.Detail(hub, place, PlaceStep.Home) }
            } ?: PlaceStep.Home
        )
    }
    when (val s = step) {
        is PlaceStep.Home -> PlaceHomeScreen(
            onPlaceClick = { hub, place -> step = PlaceStep.Detail(hub, place, PlaceStep.Home) },
            onSeeAll = { hub, category -> step = PlaceStep.HubList(hub, category) }
        )
        is PlaceStep.HubList -> HubPlacesScreen(
            hub = s.hub,
            contextLabel = s.hub.name,
            initialCategory = s.category,
            onBack = { step = PlaceStep.Home },
            onPlaceClick = { place -> step = PlaceStep.Detail(s.hub, place, s) }
        )
        is PlaceStep.Detail -> PlaceDetailScreen(
            place = s.place,
            hubName = s.hub.name,
            hub = s.hub,
            onBack = { step = s.from }
        )
    }
}

// Figma "30 Places / Home": Hub 목록을 먼저 고르는 대신, 현재 Hub 하나를 바로 보여주고
// 지도 + 카테고리 필터 + 가까운 순 리스트를 한 화면에 담는다. Hub는 헤더에서 바로 바꿀 수 있다.
@Composable
fun PlaceHomeScreen(onPlaceClick: (FinishHub, FinishHubPlace) -> Unit, onSeeAll: (FinishHub, PlaceCategory) -> Unit) {
    val activeHubs = remember { RunQData.finishHubs.filter { it.status != ContentStatus.HIDDEN } }
    val pendingRequest = remember { PlaceTabRequest.consume() }
    var currentHub by remember {
        mutableStateOf(activeHubs.firstOrNull { it.id == pendingRequest.first } ?: activeHubs.firstOrNull())
    }
    var category by remember { mutableStateOf(pendingRequest.second) }
    // 처음 들어왔을 땐 "추천순"이 기본 — 거리순은 사용자가 직접 눌렀을 때만.
    var sortByDistance by remember { mutableStateOf(false) }
    var hubMenuExpanded by remember { mutableStateOf(false) }

    // RunQ 큐레이션(JSON)만 보여주면 실시간 TourAPI 데이터가 빠지므로, HubPlacesScreen과
    // 동일하게 fetchFinishHubPlaces(TourAPI locationBasedList2 + 큐레이션 병합)를 호출한다.
    var hubResult by remember(currentHub?.id) { mutableStateOf<FinishHubResult?>(null) }
    var loadFailed by remember(currentHub?.id) { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }

    LaunchedEffect(currentHub?.id, retryTick) {
        val hub = currentHub
        if (hub != null) {
            hubResult = null
            loadFailed = false
            val fetched = runCatching { fetchFinishHubPlaces(hub) }.getOrNull()
            hubResult = fetched
            loadFailed = fetched == null
        }
    }

    // 행사(EVENT)는 특정 Hub에 묶이지 않는 강릉시 전체 정보라 별도로 한 번만 불러온다.
    var festivals by remember { mutableStateOf<List<FinishHubPlace>?>(null) }
    var festivalLoadFailed by remember { mutableStateOf(false) }
    var festivalRetryTick by remember { mutableStateOf(0) }

    LaunchedEffect(category, festivalRetryTick) {
        if (category == PlaceCategory.EVENT && (festivals == null || festivalRetryTick > 0)) {
            festivals = null
            festivalLoadFailed = false
            val fetched = runCatching { fetchFestivals() }.getOrNull()
            festivals = fetched
            festivalLoadFailed = fetched == null
        }
    }

    val isEventTab = category == PlaceCategory.EVENT
    val allPlaces = remember(hubResult) {
        val r = hubResult ?: return@remember emptyList()
        (r.eat + r.cafe + r.see).filter { it.status != ContentStatus.HIDDEN }
    }
    val places = remember(allPlaces, festivals, category, sortByDistance, currentHub) {
        val hub = currentHub
        val source = if (isEventTab) (festivals ?: emptyList())
            else if (category == null) allPlaces
            else allPlaces.filter { it.category == category }
        if (sortByDistance) source.sortedBy { it.distanceMeters(hub) ?: Double.MAX_VALUE }
        // 추천순: RunQ가 고른 곳(isFeatured)이 먼저, 그 다음은 "얼마나 채워진 정보인지"
        // (사진 + 큐레이션 소개 문구가 둘 다 있는 곳)를 위로 올린다. 운영시간까지 기준에
        // 넣고 싶었지만 그건 상세화면에서만 조회하는 TourAPI 정보라, 목록에 있는 장소
        // 수만큼 매번 추가로 API를 호출해야 해서(속도/비용 문제로) 뺐다.
        else source.sortedWith(
            compareByDescending<FinishHubPlace> { it.isFeatured }
                .thenByDescending { placeRichnessScore(it) }
                .thenBy { it.displayOrder }
        )
    }

    if (currentHub == null) {
        Box(modifier = Modifier.fillMaxSize().background(RunCream), contentAlignment = Alignment.Center) {
            Text("등록된 Finish Hub가 없어요.", color = RunGray)
        }
        return
    }
    val hub = currentHub!!

    Column(modifier = Modifier.fillMaxSize().background(RunCream)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("FINISH HUB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            Spacer(Modifier.height(6.dp))
            Text("달린 다음, 근처 좋은 곳으로.", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            Spacer(Modifier.height(6.dp))
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { hubMenuExpanded = true }
                ) {
                    Text(
                        "${hub.name} · 반경 ${"%.1f".format(hub.eatRadiusM / 1000.0)} KM",
                        fontSize = 12.sp, color = RunGray
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("▾", fontSize = 12.sp, color = RunGray)
                }
                DropdownMenu(expanded = hubMenuExpanded, onDismissRequest = { hubMenuExpanded = false }) {
                    activeHubs.forEach { h ->
                        DropdownMenuItem(text = { Text(h.name) }, onClick = { currentHub = h; hubMenuExpanded = false })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceHomeFilterChip("전체", category == null) { category = null }
                PlaceCategory.entries.forEach { c -> PlaceHomeFilterChip(c.label, category == c, c.accent) { category = c } }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            KakaoPlacesMap(
                modifier = Modifier.fillMaxSize(),
                center = RoutePoint(hub.resolvedLat, hub.resolvedLng),
                places = places.mapNotNull { p -> val lat = p.lat; val lng = p.lng; if (lat != null && lng != null) RoutePoint(lat, lng) to p.category else null }
            )
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp)
                    .clip(RoundedCornerShape(15.dp)).background(RunWhite).padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                val stillLoading = if (isEventTab) (festivals == null && !festivalLoadFailed) else (hubResult == null && !loadFailed)
                Text(if (stillLoading) "불러오는 중" else "${places.size} PLACES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFFFDFCF8)).padding(top = 20.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(if (isEventTab) "지금 강릉 행사" else "지금 가까운 곳", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isEventTab) "진행중 · 예정 행사 ${places.size}건" else "Finish Hub 주변 ${places.size}곳",
                        fontSize = 11.sp, color = RunGray
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceSortChip("거리순", sortByDistance) { sortByDistance = true }
                    PlaceSortChip("추천순", !sortByDistance) { sortByDistance = false }
                }
            }
            category?.let { c ->
                if (c != PlaceCategory.EVENT) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$c 전체보기", fontSize = 12.sp, color = RunPurple, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSeeAll(hub, c) }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                isEventTab && festivalLoadFailed -> ErrorStateView(
                    "네트워크 연결이 불안정해요", "인터넷 연결 상태를 확인하고\n다시 시도해주세요.",
                    onRetry = { festivalRetryTick++ }, modifier = Modifier.padding(top = 20.dp),
                    mascot = R.drawable.mascot_bear
                )
                isEventTab && festivals == null -> SkeletonList(count = 3, modifier = Modifier.padding(top = 4.dp))
                !isEventTab && loadFailed -> ErrorStateView(
                    "네트워크 연결이 불안정해요", "인터넷 연결 상태를 확인하고\n다시 시도해주세요.",
                    onRetry = { retryTick++ }, modifier = Modifier.padding(top = 20.dp),
                    mascot = R.drawable.mascot_bear
                )
                !isEventTab && hubResult == null -> SkeletonList(count = 3, modifier = Modifier.padding(top = 4.dp))
                places.isEmpty() -> if (isEventTab) {
                    EmptyStateView(
                        "🎪", "예정된 행사가 없어요", "곧 새로운 행사가 열리면 알려드릴게요.",
                        Modifier.padding(top = 20.dp), mascot = R.drawable.mascot_dragon
                    )
                } else {
                    EmptyStateView(
                        "📍", "지금 주변엔 추천할 장소가 없어요", "조금 더 넓은 범위에서\n새로운 장소를 찾아볼까요?",
                        Modifier.padding(top = 20.dp), mascot = R.drawable.mascot_dragon,
                        actionLabel = "반경 넓혀보기", onAction = { category = null }
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(places) { place ->
                        PlaceListCard(place) { onPlaceClick(hub, place) }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PlaceHomeFilterChip(label: String, selected: Boolean, accent: Color = RunLime, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(15.dp))
            .background(if (selected) accent else RunWhite)
            .then(if (selected) Modifier else Modifier.border(1.dp, RunBorderGray, RoundedCornerShape(15.dp)))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunBlack)
    }
}

@Composable
private fun PlaceSortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(15.dp))
            .background(RunWhite)
            .border(1.dp, if (selected) RunBlack else RunBorderGray, RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = RunBlack)
    }
}

@Composable
private fun PlaceListCard(place: FinishHubPlace, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp))
            .clickable { onClick() }.padding(12.dp)
    ) {
        val hub = findHub(place.finishHubId)
        val meters = place.distanceMeters(hub)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val thumbUrl = place.imageUrl
            if (thumbUrl != null) {
                coil.compose.AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(14.dp)).background(place.category.accent.copy(alpha = 0.2f))
                )
            } else {
                Box(
                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(14.dp)).background(place.category.accent.copy(alpha = 0.35f))
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(place.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(6.dp))
                val festivalStatus = place.festivalStatus()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(place.category.accent).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(place.category.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    }
                    if (festivalStatus != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(festivalStatus.color).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(festivalStatus.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                        }
                    } else {
                        meters?.let {
                            Spacer(Modifier.width(8.dp))
                            Text("${it.toInt()}m", fontSize = 11.sp, color = RunGray)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    // "전체보기"(PlacePickCard)와 동일하게 RunQ 큐레이션 문구(shortCopy)를 최우선으로 보여준다.
                    // Place 탭 기본 목록(이 카드)만 이 문구가 빠져 있어서 "전체보기"와 다르게 보이던 문제.
                    place.shortCopy?.takeIf { it.isNotBlank() }
                        ?: place.festivalPeriodLabel()?.let { "$it 진행" }
                        ?: meters?.let { "도보 ${walkingMinutes(it).toInt()} 분" }
                        ?: (if (place.isCurated) "RunQ가 골라둔 스팟" else place.addr.ifBlank { "코스 근처 스팟" }),
                    fontSize = 11.sp, color = RunGray, maxLines = 2
                )
            }
            val placeId = place.id
            if (placeId != null) {
                var isSaved by remember(placeId) { mutableStateOf(SavedItemsStore.isPlaceSaved(placeId)) }
                Text(
                    if (isSaved) "♥" else "♡", fontSize = 18.sp, color = if (isSaved) RunPurple else RunGray,
                    modifier = Modifier.clickable { SavedItemsStore.togglePlace(placeId); isSaved = !isSaved }
                )
            }
        }
    }
}

@Composable
fun CategoryChip(category: PlaceCategory, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) category.accent else RunWhite)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            category.label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = RunBlack
        )
    }
}

// ════════════════════════════════════════════════════════
// Hub 장소 리스트: "32/33/34 Places/EAT|CAFE|SEE/List.png" (★ 실제 API 호출 지점)
// ════════════════════════════════════════════════════════
private val categoryHeadline = mapOf(
    PlaceCategory.EAT to ("러닝 후, 한 끼까지." to "코스 근처에서 RunQ가 골라둔 식사 스팟이에요."),
    PlaceCategory.CAFE to ("잠깐, 쉬어가도 좋으니까." to "코스 근처 카페에서 여유를 즐겨보세요."),
    PlaceCategory.SEE to ("조금 더 둘러보고 싶다면." to "러닝 뒤 가볍게 이어가기 좋은 주변 스팟이에요.")
)

@Composable
fun HubPlacesScreen(
    hub: FinishHub?,
    contextLabel: String,
    initialCategory: PlaceCategory = PlaceCategory.EAT,
    onBack: () -> Unit,
    onPlaceClick: (FinishHubPlace) -> Unit
) {
    var result by remember { mutableStateOf<FinishHubResult?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(initialCategory) }

    LaunchedEffect(hub?.id, retryTick) {
        if (hub != null) {
            result = null
            result = runCatching { fetchFinishHubPlaces(hub) }.getOrNull()
            loadFailed = result == null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RUNQ PICKS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tab.accent)
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(RunWhite).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = RunBlack, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(contextLabel, fontSize = 12.sp, color = RunGray)
        Spacer(Modifier.height(4.dp))
        val (headline, sub) = categoryHeadline.getValue(tab)
        Text(headline, fontSize = 24.sp, fontWeight = FontWeight.Black, color = RunBlack, lineHeight = 30.sp)
        Spacer(Modifier.height(6.dp))
        Text(sub, fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 행사(EVENT)는 특정 Hub에 속하지 않는 강릉시 전체 정보라 이 Hub 전용 화면엔 안 보여준다.
            PlaceCategory.entries.filter { it != PlaceCategory.EVENT }.forEach { c -> CategoryChip(c, tab == c) { tab = c } }
        }
        Spacer(Modifier.height(20.dp))

        val list = when (tab) {
            PlaceCategory.EAT -> result?.eat
            PlaceCategory.CAFE -> result?.cafe
            PlaceCategory.SEE -> result?.see
            PlaceCategory.EVENT -> emptyList() // 행사는 Hub 범위가 아니라 Place 탭 홈에서만 별도로 보여줌
        }
        when {
            hub == null -> EmptyStateView(
                "📍", "Finish Hub 정보가 없어요", "이 코스는 아직 Finish Hub가 연결되지 않았어요.", Modifier.padding(top = 30.dp),
                mascot = R.drawable.mascot_bear
            )
            loadFailed -> ErrorStateView(
                "네트워크 연결이 불안정해요", "인터넷 연결 상태를 확인하고\n다시 시도해주세요.",
                onRetry = { retryTick++ }, modifier = Modifier.padding(top = 30.dp),
                mascot = R.drawable.mascot_bear
            )
            list == null -> SkeletonList(count = 3, modifier = Modifier.padding(top = 4.dp))
            list.isEmpty() -> EmptyStateView(
                "🔍", "지금 주변엔 추천할 장소가 없어요", "다른 카테고리를 확인해보세요.", Modifier.padding(top = 30.dp),
                mascot = R.drawable.mascot_dragon
            )
            else -> {
                Text("${list.size} PLACES", fontSize = 11.sp, color = RunGray)
                Spacer(Modifier.height(12.dp))
                list.forEachIndexed { index, place ->
                    PlacePickCard(index + 1, hub.name.split("·").firstOrNull()?.trim() ?: hub.name, place) {
                        onPlaceClick(place)
                    }
                    if (index != list.lastIndex) Spacer(Modifier.height(18.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun PlacePickCard(no: Int, neighborhood: String, place: FinishHubPlace, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Text(
            "${no.toString().padStart(2, '0')} · $neighborhood",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = place.category.accent
        )
        Spacer(Modifier.height(8.dp))
        PlacePhotoPlaceholder(place.category.accent, 150, place.imageUrl)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(place.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            Icon(Icons.Filled.ChevronRight, null, tint = RunGray, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            place.shortCopy?.takeIf { it.isNotBlank() }
                ?: if (place.isCurated) "RunQ가 골라둔 스팟" else place.addr.ifBlank { "코스 근처 스팟" },
            fontSize = 12.sp, color = RunGray, maxLines = 2
        )
    }
}

// imageUrl이 있으면(TourAPI firstimage 또는 큐레이션 runq_image_url/tourapi_image_url) 실제 사진을,
// 없으면 기존처럼 카테고리 색 자리표시 박스를 보여준다.
@Composable
fun PlacePhotoPlaceholder(accent: Color, heightDp: Int, imageUrl: String? = null) {
    if (imageUrl != null) {
        coil.compose.AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.15f))
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text("PHOTO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunBlack.copy(alpha = 0.4f))
        }
    }
}

// 카카오맵 앱으로 열고, 앱이 없으면 브라우저의 카카오맵 웹으로 폴백한다.
// (지도 보기/길찾기 등 여러 곳에서 반복되던 try-catch 폴백을 하나로 모음)
private fun openKakaoMapUri(context: android.content.Context, appUri: String, webUri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUri)))
    } catch (e: ActivityNotFoundException) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri))) }
        catch (e2: ActivityNotFoundException) { /* 브라우저도 없는 기기 — 조용히 무시 */ }
    }
}

// ════════════════════════════════════════════════════════
// Place Detail: "35 Places/Detail.png" — contentId가 있으면 detailCommon2로 상세조회
// ════════════════════════════════════════════════════════
@Composable
fun PlaceDetailScreen(place: FinishHubPlace, hubName: String? = null, hub: FinishHub? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var detail by remember { mutableStateOf<DetailCommonItem?>(null) }
    var intro by remember { mutableStateOf<DetailIntroItem?>(null) }
    var loading by remember { mutableStateOf(place.contentId != null) }
    var kakaoInfo by remember { mutableStateOf<KakaoPlaceLookup?>(null) }
    // 장소 목록(PlaceListCard)엔 이미 있던 저장 하트가 상세화면엔 없어서 여기서 저장할
    // 방법이 아예 없었다 — 목록과 동일한 SavedItemsStore로 상세화면에도 추가.
    val placeId = place.id
    var isSaved by remember(placeId) { mutableStateOf(placeId?.let { SavedItemsStore.isPlaceSaved(it) } ?: false) }

    LaunchedEffect(place.contentId) {
        val id = place.contentId
        if (id != null) {
            detail = runCatching {
                TourApiClient.api.getDetailCommon(contentId = id).response.body.items?.item?.firstOrNull()
            }.onFailure {
                // "상세정보를 불러오지 못했어요"로만 뭉뚱그려지지 않도록 실제 원인을 logcat에 남긴다.
                android.util.Log.w("PlaceDetail", "detailCommon2 실패 (contentId=$id, title=${place.title})", it)
            }.getOrNull()
            loading = false

            // 운영시간/휴무일은 detailIntro2에만 있다. contentTypeId는 실제로 있으면 그 값을,
            // 없으면 카테고리로 대략 추정(EAT/CAFE→39 음식점, SEE→12 관광지)해서 요청하고,
            // 최종적으로는 응답이 스스로 알려주는 contenttypeid 기준으로 필드를 골라 읽는다.
            val typeId = place.contentTypeId ?: when (place.category) {
                PlaceCategory.EAT, PlaceCategory.CAFE -> "39"
                PlaceCategory.SEE -> "12"
                PlaceCategory.EVENT -> "15"
            }
            intro = runCatching {
                TourApiClient.api.getDetailIntro(contentId = id, contentTypeId = typeId).response.body.items?.item?.firstOrNull()
            }.onFailure {
                android.util.Log.w("PlaceDetail", "detailIntro2 실패 (contentId=$id, title=${place.title})", it)
            }.getOrNull()
        }
    }

    // 전화/영업시간 등 큐레이션 DB에 없는 정보를 보완하고, 카테고리 태그도 여기서 얻는다
    // (Kakao Local의 category_name 기반) — 주소가 이미 있어도 이 두 가지 때문에 항상 조회한다.
    LaunchedEffect(place.id, place.title) {
        kakaoInfo = fetchKakaoPlaceInfo(place.title)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RunBlack)
            }
            Spacer(Modifier.width(4.dp))
            Text("장소 상세", fontSize = 20.sp, fontWeight = FontWeight.Black, color = RunBlack)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 48.dp)) {
            Text(
                if (place.isCurated) "RunQ Pick" else place.category.label,
                fontSize = 13.sp, color = place.category.accent, fontWeight = FontWeight.Bold
            )
            place.festivalStatus()?.let { status ->
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(status.color).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(status.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PlacePhotoPlaceholder(place.category.accent, 200, detail?.firstImage?.takeIf { it.isNotBlank() } ?: place.imageUrl)
        Spacer(Modifier.height(20.dp))
        Text(place.title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Spacer(Modifier.height(4.dp))
        Text(
            place.festivalPeriodLabel()?.let { "$it 진행" }
                ?: listOfNotNull(hubName, place.distanceLabel(hub)?.let { "Finish Hub에서 $it" }).joinToString(" · ")
                    .ifBlank { place.category.label },
            fontSize = 13.sp, color = RunGray
        )
        if (kakaoInfo?.tags?.isNotEmpty() == true) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kakaoInfo?.tags?.forEach { tag ->
                    Box(modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(RunBgGray).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = RunGray)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            // RunQ 큐레이션 문구가 있으면 최우선으로 보여준다 (TourAPI contentId 유무와 무관).
            !place.shortCopy.isNullOrBlank() -> {
                Text(place.shortCopy, fontSize = 14.sp, color = RunBlack, lineHeight = 20.sp)
                if (!place.recommendReason.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(place.recommendReason, fontSize = 13.sp, color = RunPurple, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                }
            }
            place.contentId == null -> Text(
                "RunQ가 직접 고른 장소예요. 상세정보는 TourAPI 연동(P1) 이후 채워질 예정이에요.",
                fontSize = 14.sp, color = RunGray
            )
            loading -> CircularProgressIndicator(color = RunPurple)
            detail == null -> Text("상세정보를 불러오지 못했어요.", color = RunGray)
            else -> Text(detail!!.overview ?: "설명이 없어요.", fontSize = 14.sp, color = RunBlack, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(20.dp))

        PlaceInfoRow("주소", (detail?.addr1 ?: place.addr).ifBlank { kakaoInfo?.address ?: "주소 정보 준비중" })
        Spacer(Modifier.height(10.dp))
        PlaceInfoRow("전화", detail?.tel?.takeIf { it.isNotBlank() } ?: kakaoInfo?.phone ?: "정보 없음")
        intro?.hoursLabel()?.let { hours ->
            Spacer(Modifier.height(10.dp))
            PlaceInfoRow("운영시간", hours + (intro?.restDateLabel()?.let { " · 휴무 $it" } ?: ""))
        }
        Spacer(Modifier.height(20.dp))

        val kakaoPlaceUrl = kakaoInfo?.placeUrl
        if (kakaoPlaceUrl != null) {
            Text(
                "카카오맵에서 영업시간·리뷰 보기 →", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunPurple,
                modifier = Modifier.clickable {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(kakaoPlaceUrl))) }
                    catch (e: ActivityNotFoundException) { /* 브라우저가 없는 기기 — 조용히 무시 */ }
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { placeId?.let { SavedItemsStore.togglePlace(it); isSaved = !isSaved } },
                modifier = Modifier.weight(0.4f).height(54.dp),
                shape = RoundedCornerShape(27.dp),
                border = BorderStroke(1.5.dp, if (isSaved) RunPurple else RunBorderGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isSaved) RunPurple else RunBlack)
            ) { Text(if (isSaved) "♥ 저장됨" else "♡ 저장하기", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Button(
                onClick = {
                    val destLat = kakaoInfo?.lat ?: place.lat ?: hub?.resolvedLat
                    val destLng = kakaoInfo?.lng ?: place.lng ?: hub?.resolvedLng
                    val label = Uri.encode(place.title)
                    if (destLat == null || destLng == null) {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://map.kakao.com/link/search/$label"))) }
                        catch (e: ActivityNotFoundException) { /* 조용히 무시 */ }
                        return@Button
                    }
                    val lookOnlyUri = "kakaomap://look?p=$destLat,$destLng"
                    val webUri = "https://map.kakao.com/link/map/$label,$destLat,$destLng"
                    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!hasLocationPermission) {
                        // 지금 탭에서는 위치를 못 얻으니 목적지만 보여주고, 권한은 다음 탭을 위해 요청해둔다.
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        openKakaoMapUri(context, lookOnlyUri, webUri)
                        return@Button
                    }
                    // 현재 위치 → 이 장소까지, 카카오맵 앱의 실제 도보 경로 안내로 바로 연결한다
                    // (우리 앱엔 자체 길찾기 엔진이 없어서, 실제 도로를 따르는 경로는 카카오맵의
                    // 진짜 경로안내 기능을 그대로 활용하는 게 직선 거리 표시보다 훨씬 정확하다).
                    try {
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { loc ->
                                val uri = if (loc != null) "kakaomap://route?sp=${loc.latitude},${loc.longitude}&ep=$destLat,$destLng&by=FOOT" else lookOnlyUri
                                openKakaoMapUri(context, uri, webUri)
                            }
                            .addOnFailureListener { openKakaoMapUri(context, lookOnlyUri, webUri) }
                    } catch (e: SecurityException) {
                        openKakaoMapUri(context, lookOnlyUri, webUri)
                    }
                },
                modifier = Modifier.weight(0.6f).height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
            ) { Text("→ 여기로 달리기", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PlaceInfoRow(label: String, value: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RunWhite)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Text(value, fontSize = 12.sp, color = RunGray)
            }
            Icon(
                Icons.Filled.ContentCopy, contentDescription = "$label 복사",
                tint = RunGray,
                modifier = Modifier.size(18.dp).clickable {
                    clipboard.setText(AnnotatedString(value))
                    Toast.makeText(context, "${label}를 복사했어요", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
