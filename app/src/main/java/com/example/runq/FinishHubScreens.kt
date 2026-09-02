package com.example.runq

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════
// Finish Hub 기준 EAT / CAFE / SEE 조회
//
// 코스 완주 → course.finishHubIds[0] → findHub() → hub 좌표+반경으로
// TourAPI locationBasedList2를 호출한다. API 결과를 그대로 다 보여주지 않고
// hub.curated* 이름과 매칭되는 장소를 "RunQ Pick"으로 맨 위에 고정한다.
// ════════════════════════════════════════════════════════

enum class PlaceCategory(val label: String, val accent: Color) {
    EAT("EAT", RunLime), CAFE("CAFE", RunPurple), SEE("SEE", RunLavender)
}

data class FinishHubPlace(
    val title: String,
    val addr: String,
    val category: PlaceCategory,
    val contentId: String? = null,   // TourAPI 결과일 때만 값이 있음 → Place Detail에서 상세조회 가능
    val distMeters: String? = null,  // TourAPI dist(m). curated 전용 항목은 null
    val isCurated: Boolean = false   // RunQ가 직접 고른 장소인지
)

data class FinishHubResult(
    val hub: FinishHub,
    val eat: List<FinishHubPlace>,
    val cafe: List<FinishHubPlace>,
    val see: List<FinishHubPlace>
)

private val cafeKeywords = listOf("카페", "커피", "베이커리", "로스터리", "coffee", "cafe")
private val seeContentTypeIds = listOf(12, 14, 15, 28) // 관광지 / 문화시설 / 축제행사 / 레포츠

// curated 이름을 API 결과 맨 앞에 고정하고, 아직 API에서 못 찾은 curated 항목도
// "RunQ Pick" 카드로는 노출한다 (API 원본 그대로 노출하기보다 RunQ 큐레이션 우선 노출).
private fun mergeCurated(
    curatedNames: List<String>,
    apiResults: List<FinishHubPlace>,
    category: PlaceCategory
): List<FinishHubPlace> {
    val matchedTitles = mutableSetOf<String>()
    val curatedFirst = curatedNames.map { name ->
        val found = apiResults.find { it.title.contains(name) || name.contains(it.title) }
        if (found != null) {
            matchedTitles.add(found.title)
            found.copy(isCurated = true)
        } else {
            FinishHubPlace(title = name, addr = "", category = category, isCurated = true)
        }
    }
    val rest = apiResults.filter { it.title !in matchedTitles }
    return curatedFirst + rest
}

suspend fun fetchFinishHubPlaces(hub: FinishHub): FinishHubResult {
    // EAT + CAFE 후보: TourAPI 음식점(contentTypeId=39)에서 카페 키워드로 1차 분리
    // (TourAPI에는 카페 전용 contentTypeId가 없음 → P1에서 Kakao Local CE7로 보완 예정)
    val foodItems = runCatching {
        TourApiClient.api.getNearbyPlaces(
            mapX = hub.lng, mapY = hub.lat, radius = hub.radiusMeters, contentTypeId = 39
        ).response.body.items?.item ?: emptyList()
    }.getOrDefault(emptyList())

    val cafeApiRaw = foodItems.filter { p -> cafeKeywords.any { (p.title ?: "").contains(it, true) } }
        .map { FinishHubPlace(it.title ?: "-", it.addr1 ?: "", PlaceCategory.CAFE, it.contentId, it.dist) }
    val eatApiRaw = foodItems.filterNot { p -> cafeKeywords.any { (p.title ?: "").contains(it, true) } }
        .map { FinishHubPlace(it.title ?: "-", it.addr1 ?: "", PlaceCategory.EAT, it.contentId, it.dist) }

    // SEE: 관광지/문화시설/행사/레포츠 여러 contentTypeId를 합쳐서 조회
    val seeApiRaw = seeContentTypeIds.flatMap { typeId ->
        runCatching {
            TourApiClient.api.getNearbyPlaces(
                mapX = hub.lng, mapY = hub.lat, radius = hub.radiusMeters, contentTypeId = typeId
            ).response.body.items?.item ?: emptyList()
        }.getOrDefault(emptyList())
    }.distinctBy { it.title }
        .map { FinishHubPlace(it.title ?: "-", it.addr1 ?: "", PlaceCategory.SEE, it.contentId, it.dist) }

    return FinishHubResult(
        hub = hub,
        eat = mergeCurated(hub.curatedEat, eatApiRaw, PlaceCategory.EAT),
        cafe = mergeCurated(hub.curatedCafe, cafeApiRaw, PlaceCategory.CAFE),
        see = mergeCurated(hub.curatedSee, seeApiRaw, PlaceCategory.SEE)
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
    heightDp: Int = 260,
    currentLocation: RoutePoint? = null
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(24.dp))
    ) {
        KakaoRouteMap(
            modifier = Modifier.fillMaxSize(),
            routePoints = course.routePoints,
            startPoint = RoutePoint(course.startLat, course.startLng),
            finishPoint = RoutePoint(course.finishLat, course.finishLng),
            currentLocation = currentLocation
        )
        if (title != null) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    .clip(RoundedCornerShape(10.dp)).background(RunWhite.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// Run Ready: "22 Course/Run Ready.png" 기준
// ════════════════════════════════════════════════════════
@Composable
fun RunReadyScreen(course: Course, onBack: () -> Unit, onStart: () -> Unit) {
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    LaunchedEffect(course.name) {
        safety = runCatching { fetchSafety() }.getOrNull()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(RunCream).padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RunBlack)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("러닝 준비", fontSize = 20.sp, fontWeight = FontWeight.Black, color = RunBlack)
                Text(course.name, fontSize = 13.sp, color = RunGray)
            }
        }
        Spacer(Modifier.height(20.dp))
        CourseMapCard(course = course)
        Spacer(Modifier.height(24.dp))
        Text("오늘의 러닝 환경", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(10.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RunWhite)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SafetyMetric("기온", safety?.temp ?: "-")
                SafetyMetric("미세먼지", safety?.pm10 ?: "-")
                SafetyMetric("예상 시간", course.estimatedTime)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunBlack, contentColor = RunWhite)
        ) { Text("러닝 시작", fontSize = 16.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(10.dp))
        Text(
            "GPS와 위치 권한을 확인한 뒤 시작합니다.", fontSize = 12.sp, color = RunGray,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// ════════════════════════════════════════════════════════
// Running: "23 Run/Active.png" · "24 Run/Paused.png" 기준
// ════════════════════════════════════════════════════════
@Composable
fun CourseRunningScreen(course: Course, onFinish: (distanceKm: Double, elapsedSeconds: Int) -> Unit) {
    var distance by remember { mutableStateOf(0.0) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(true) }
    val targetKm = remember(course) {
        course.distanceKm.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.takeIf { it > 0 } ?: 5.0
    }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            distance += 0.01
            elapsedSeconds += 1
        }
    }

    // GPS 실측 연동 전까지는 진행률(거리/목표거리)을 코스 경로에 투영해서 현재 위치처럼 보여준다.
    val simulatedLocation = remember(course, distance, targetKm) {
        val line = if (course.routePoints.size >= 2) course.routePoints
            else listOf(RoutePoint(course.startLat, course.startLng), RoutePoint(course.finishLat, course.finishLng))
        interpolateAlongRoute(line, (distance / targetKm).toFloat())
    }

    val paceLabel = remember(distance, elapsedSeconds) {
        if (distance < 0.01) "0'00\"" else {
            val paceSec = (elapsedSeconds / distance).toInt()
            "${paceSec / 60}'${(paceSec % 60).toString().padStart(2, '0')}\""
        }
    }
    val durationLabel = remember(elapsedSeconds) {
        val h = elapsedSeconds / 3600; val m = (elapsedSeconds % 3600) / 60; val s = elapsedSeconds % 60
        "%02d:%02d:%02d".format(h, m, s)
    }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).padding(24.dp)) {
        if (running) {
            Text(course.name, fontSize = 22.sp, fontWeight = FontWeight.Black, color = RunBlack)
            Text("RUNNING", fontSize = 12.sp, color = RunGray)
        } else {
            Text("러닝 일시정지", fontSize = 22.sp, fontWeight = FontWeight.Black, color = RunBlack)
            Text(course.name, fontSize = 12.sp, color = RunGray)
        }
        Spacer(Modifier.height(16.dp))
        CourseMapCard(course = course, currentLocation = simulatedLocation)
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricItem(String.format("%.2f km", distance), "거리")
            MetricItem(durationLabel, "시간")
            MetricItem(paceLabel, "평균 페이스")
        }
        Spacer(Modifier.weight(1f))
        if (running) {
            Button(
                onClick = { running = false },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunWhite, contentColor = RunBlack)
            ) { Text("일시정지", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { running = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunBlack, contentColor = RunWhite)
                ) { Text("계속하기", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { onFinish(distance, elapsedSeconds) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RunBlack)
                ) { Text("러닝 종료", fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(12.dp))
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

    Column(
        modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("RUN COMPLETE", fontSize = 24.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Text("${course.name} 완료!", fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))
        CourseMapCard(course = course)
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricItem(String.format("%.2f km", distanceKm), "거리")
            MetricItem(durationLabel, "시간")
            MetricItem(paceLabel, "평균 페이스")
        }
        Spacer(Modifier.height(28.dp))
        Text("다음은 어디로 갈까요?", fontSize = 17.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Spacer(Modifier.height(12.dp))
        if (hub == null) {
            Text("이 코스는 아직 Finish Hub가 지정되지 않았어요.", fontSize = 13.sp, color = RunGray)
        } else {
            NextStepRow("EAT", "러닝 후 든든하게 · ${hub.name} Finish Hub") { onCategoryClick(PlaceCategory.EAT) }
            Spacer(Modifier.height(10.dp))
            NextStepRow("CAFE", hub.curatedCafe.firstOrNull()?.let { "$it 등에서 잠깐 쉬기" } ?: "호수뷰 카페에서 잠깐 쉬기") { onCategoryClick(PlaceCategory.CAFE) }
            Spacer(Modifier.height(10.dp))
            NextStepRow("SEE", "${hub.name} 주변을 천천히 둘러보기") { onCategoryClick(PlaceCategory.SEE) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun NextStepRow(label: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RunWhite)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Black, color = RunBlack)
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = RunGray)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = RunGray, modifier = Modifier.size(18.dp))
        }
    }
}

// ════════════════════════════════════════════════════════
// Place 탭 (Hub 자체를 순회) — "30 Places/Home.png"
// ════════════════════════════════════════════════════════
sealed class PlaceStep {
    object Home : PlaceStep()
    data class HubList(val hub: FinishHub, val category: PlaceCategory) : PlaceStep()
    data class Detail(val hub: FinishHub, val place: FinishHubPlace) : PlaceStep()
}

@Composable
fun PlaceFlow() {
    var step by remember { mutableStateOf<PlaceStep>(PlaceStep.Home) }
    when (val s = step) {
        is PlaceStep.Home -> PlaceHomeScreen(
            onHubClick = { hub, category -> step = PlaceStep.HubList(hub, category) }
        )
        is PlaceStep.HubList -> HubPlacesScreen(
            hub = s.hub,
            contextLabel = s.hub.name,
            initialCategory = s.category,
            onBack = { step = PlaceStep.Home },
            onPlaceClick = { place -> step = PlaceStep.Detail(s.hub, place) }
        )
        is PlaceStep.Detail -> PlaceDetailScreen(
            place = s.place,
            hubName = s.hub.name,
            onBack = { step = PlaceStep.HubList(s.hub, s.place.category) }
        )
    }
}

@Composable
fun PlaceHomeScreen(onHubClick: (FinishHub, PlaceCategory) -> Unit) {
    var category by remember { mutableStateOf(PlaceCategory.EAT) }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).padding(24.dp)) {
        Text("PLACE", fontSize = 26.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Text("러닝 전후, 강릉을 더 즐겨보세요", fontSize = 14.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(RunWhite).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = RunGray, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("장소, 지역을 검색해보세요", fontSize = 13.sp, color = RunGray)
        }
        Spacer(Modifier.height(24.dp))
        Text("FINISH HUB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunGray)
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(finishHubs) { hub ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onHubClick(hub, category) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RunWhite)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(hub.name, fontSize = 15.sp, fontWeight = FontWeight.Black, color = RunBlack)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "EAT ${hub.curatedEat.size} · CAFE ${hub.curatedCafe.size} · SEE ${hub.curatedSee.size}",
                                fontSize = 12.sp, color = RunGray
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = RunGray, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("CATEGORY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunGray)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceCategory.values().forEach { c -> CategoryChip(c, category == c) { category = c } }
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
    var tab by remember { mutableStateOf(initialCategory) }

    LaunchedEffect(hub?.id) {
        if (hub != null) {
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
            PlaceCategory.values().forEach { c -> CategoryChip(c, tab == c) { tab = c } }
        }
        Spacer(Modifier.height(20.dp))

        val list = when (tab) {
            PlaceCategory.EAT -> result?.eat
            PlaceCategory.CAFE -> result?.cafe
            PlaceCategory.SEE -> result?.see
        }
        when {
            hub == null -> Text("Finish Hub 정보가 없어요.", color = RunGray)
            loadFailed -> Text("추천 정보를 불러오지 못했어요. (네트워크 확인)", color = RunGray)
            list == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RunPurple)
            }
            list.isEmpty() -> Text("추천 장소를 찾지 못했어요.", color = RunGray)
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
        PlacePhotoPlaceholder(place.category.accent, 150)
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
            if (place.isCurated) "RunQ가 골라둔 스팟" else (place.addr.ifBlank { "코스 근처 스팟" }),
            fontSize = 12.sp, color = RunGray
        )
    }
}

@Composable
fun PlacePhotoPlaceholder(accent: Color, heightDp: Int) {
    Box(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text("PHOTO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunBlack.copy(alpha = 0.4f))
    }
}

// ════════════════════════════════════════════════════════
// Place Detail: "35 Places/Detail.png" — contentId가 있으면 detailCommon2로 상세조회
// ════════════════════════════════════════════════════════
@Composable
fun PlaceDetailScreen(place: FinishHubPlace, hubName: String? = null, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<DetailCommonItem?>(null) }
    var loading by remember { mutableStateOf(place.contentId != null) }

    LaunchedEffect(place.contentId) {
        val id = place.contentId
        if (id != null) {
            detail = runCatching {
                TourApiClient.api.getDetailCommon(contentId = id).response.body.items?.item?.firstOrNull()
            }.getOrNull()
            loading = false
        }
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
        Text(
            if (place.isCurated) "RunQ Pick" else place.category.label,
            fontSize = 13.sp, color = place.category.accent, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp)
        )
        Spacer(Modifier.height(16.dp))
        PlacePhotoPlaceholder(place.category.accent, 200)
        Spacer(Modifier.height(20.dp))
        Text(place.title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(hubName, place.distMeters?.let { "Finish Hub에서 ${it}m" }).joinToString(" · ")
                .ifBlank { place.category.label },
            fontSize = 13.sp, color = RunGray
        )
        Spacer(Modifier.height(14.dp))
        when {
            place.contentId == null -> Text(
                "RunQ가 직접 고른 장소예요. 상세정보는 TourAPI 연동(P1) 이후 채워질 예정이에요.",
                fontSize = 14.sp, color = RunGray
            )
            loading -> CircularProgressIndicator(color = RunPurple)
            detail == null -> Text("상세정보를 불러오지 못했어요.", color = RunGray)
            else -> Text(detail!!.overview ?: "설명이 없어요.", fontSize = 14.sp, color = RunBlack, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(20.dp))

        PlaceInfoRow("주소", (detail?.addr1 ?: place.addr).ifBlank { "주소 정보 준비중" })
        Spacer(Modifier.height(10.dp))
        PlaceInfoRow("운영 정보", "영업시간 / 휴무 / 홈페이지")
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { /* Kakao Map 연동 예정 */ },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunBlack, contentColor = RunWhite)
        ) { Text("지도에서 보기", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PlaceInfoRow(label: String, value: String) {
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
            Icon(Icons.Filled.ChevronRight, null, tint = RunGray, modifier = Modifier.size(16.dp))
        }
    }
}
