package com.example.runq

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class PlaceCategory { EAT, CAFE, SEE }

data class FinishHubPlace(
    val title: String,
    val addr: String,
    val category: PlaceCategory,
    val contentId: String? = null,   // TourAPI 결과일 때만 값이 있음 → Place Detail에서 상세조회 가능
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
        .map { FinishHubPlace(it.title ?: "-", it.addr1 ?: "", PlaceCategory.CAFE, it.contentId) }
    val eatApiRaw = foodItems.filterNot { p -> cafeKeywords.any { (p.title ?: "").contains(it, true) } }
        .map { FinishHubPlace(it.title ?: "-", it.addr1 ?: "", PlaceCategory.EAT, it.contentId) }

    // SEE: 관광지/문화시설/행사/레포츠 여러 contentTypeId를 합쳐서 조회
    val seeApiRaw = seeContentTypeIds.flatMap { typeId ->
        runCatching {
            TourApiClient.api.getNearbyPlaces(
                mapX = hub.lng, mapY = hub.lat, radius = hub.radiusMeters, contentTypeId = typeId
            ).response.body.items?.item ?: emptyList()
        }.getOrDefault(emptyList())
    }.distinctBy { it.title }
        .map { FinishHubPlace(it.title ?: "-", it.addr1 ?: "", PlaceCategory.SEE, it.contentId) }

    return FinishHubResult(
        hub = hub,
        eat = mergeCurated(hub.curatedEat, eatApiRaw, PlaceCategory.EAT),
        cafe = mergeCurated(hub.curatedCafe, cafeApiRaw, PlaceCategory.CAFE),
        see = mergeCurated(hub.curatedSee, seeApiRaw, PlaceCategory.SEE)
    )
}

// ════════════════════════════════════════════════════════
// Run Ready: 러닝 시작 전 준비 화면 (기본틀)
// ════════════════════════════════════════════════════════
@Composable
fun RunReadyScreen(course: Course, onBack: () -> Unit, onStart: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(RunWhite).padding(24.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RunBlack)
        }
        Spacer(Modifier.height(24.dp))
        Text("Ready to Run", fontSize = 28.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Spacer(Modifier.height(8.dp))
        Text(course.name, fontSize = 16.sp, color = RunGray)
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(course.distanceKm, fontSize = 64.sp, fontWeight = FontWeight.Black, color = RunLime)
            Text("예상 ${course.estimatedTime}", fontSize = 14.sp, color = RunGray)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
        ) { Text("START", fontSize = 18.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(12.dp))
    }
}

// ════════════════════════════════════════════════════════
// Running: 코스 러닝 화면 (기본틀 — 거리 시뮬레이션 + 완주 버튼)
// ════════════════════════════════════════════════════════
@Composable
fun CourseRunningScreen(course: Course, onFinish: (Double) -> Unit) {
    var distance by remember { mutableStateOf(0.0) }
    var isRunning by remember { mutableStateOf(true) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000)
            distance += 0.01
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(RunBlack).padding(24.dp)) {
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(course.name, color = RunWhite.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Text(String.format("%.2f", distance), fontSize = 72.sp, fontWeight = FontWeight.Black, color = RunWhite)
            Text("Distance (Km)", fontSize = 13.sp, color = RunGray)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { isRunning = false; onFinish(distance) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
        ) { Text("완주하기 (FINISH)", fontWeight = FontWeight.Black, fontSize = 18.sp) }
        Spacer(Modifier.height(20.dp))
    }
}

// ════════════════════════════════════════════════════════
// Complete: 완주 요약 → Finish Hub 진입 (기본틀)
// ════════════════════════════════════════════════════════
@Composable
fun CompleteScreen(course: Course, distanceKm: Double, onGoToFinishHub: () -> Unit) {
    val hub = remember(course) { course.finishHubIds.firstOrNull()?.let { findHub(it) } }

    Column(
        modifier = Modifier.fillMaxSize().background(RunWhite).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RunLime, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("완주했어요!", fontSize = 26.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Text(course.name, fontSize = 14.sp, color = RunGray)
        Spacer(Modifier.height(24.dp))
        Text(String.format("%.2f km", distanceKm), fontSize = 48.sp, fontWeight = FontWeight.Black, color = RunPurple)
        Spacer(Modifier.weight(1f))
        Text(
            if (hub != null) "${hub.name} 근처 EAT / CAFE / SEE 보러가기" else "주변 추천 보러가기",
            fontSize = 13.sp, color = RunGray
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onGoToFinishHub,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunBlack, contentColor = RunWhite)
        ) { Text("FINISH HUB", fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(12.dp))
    }
}

// ════════════════════════════════════════════════════════
// Finish Hub: EAT / CAFE / SEE 탭 (★ 실제 API 호출 지점)
// ════════════════════════════════════════════════════════
@Composable
fun FinishHubScreen(course: Course, onBack: () -> Unit, onPlaceClick: (FinishHubPlace) -> Unit) {
    val hub = remember(course) { course.finishHubIds.firstOrNull()?.let { findHub(it) } }
    var result by remember { mutableStateOf<FinishHubResult?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(PlaceCategory.EAT) }

    LaunchedEffect(hub?.id) {
        if (hub != null) {
            result = runCatching { fetchFinishHubPlaces(hub) }.getOrNull()
            loadFailed = result == null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(RunWhite).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RunBlack)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Finish Hub", fontSize = 22.sp, fontWeight = FontWeight.Black, color = RunBlack)
                Text(
                    hub?.let { "${it.name} · 반경 ${it.radiusLabel}" } ?: "Hub 정보 없음",
                    fontSize = 13.sp, color = RunGray
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortTab("EAT", tab == PlaceCategory.EAT) { tab = PlaceCategory.EAT }
            SortTab("CAFE", tab == PlaceCategory.CAFE) { tab = PlaceCategory.CAFE }
            SortTab("SEE", tab == PlaceCategory.SEE) { tab = PlaceCategory.SEE }
        }
        Spacer(Modifier.height(16.dp))

        val list = when (tab) {
            PlaceCategory.EAT -> result?.eat
            PlaceCategory.CAFE -> result?.cafe
            PlaceCategory.SEE -> result?.see
        }
        when {
            hub == null -> Text("이 코스는 아직 Finish Hub가 지정되지 않았어요.", color = RunGray)
            loadFailed -> Text("추천 정보를 불러오지 못했어요. (네트워크 확인)", color = RunGray)
            list == null -> Row { CircularProgressIndicator(color = RunPurple) }
            list.isEmpty() -> Text("추천 장소를 찾지 못했어요.", color = RunGray)
            else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(list) { place -> FinishHubPlaceCard(place) { onPlaceClick(place) } }
            }
        }
    }
}

@Composable
fun FinishHubPlaceCard(place: FinishHubPlace, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RunBgGray)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (place.isCurated) Badge("RunQ Pick", RunLime) else Badge(place.category.name, RunPurple)
                Spacer(Modifier.width(8.dp))
                Text(place.title, fontWeight = FontWeight.Bold, color = RunBlack)
            }
            if (place.addr.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(place.addr, fontSize = 13.sp, color = RunGray)
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// Place Detail: contentId가 있으면 detailCommon2로 상세조회
// ════════════════════════════════════════════════════════
@Composable
fun PlaceDetailScreen(place: FinishHubPlace, onBack: () -> Unit) {
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

    Column(modifier = Modifier.fillMaxSize().background(RunWhite).padding(24.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RunBlack)
        }
        Spacer(Modifier.height(16.dp))
        Text(place.title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Spacer(Modifier.height(6.dp))
        Badge(place.category.name, RunPurple)
        Spacer(Modifier.height(16.dp))
        when {
            place.contentId == null -> Text(
                "RunQ가 직접 고른 장소예요. 상세정보는 TourAPI 연동(P1) 이후 채워질 예정이에요.",
                fontSize = 14.sp, color = RunGray
            )
            loading -> CircularProgressIndicator(color = RunPurple)
            detail == null -> Text("상세정보를 불러오지 못했어요.", color = RunGray)
            else -> Column {
                Text(detail!!.addr1 ?: place.addr, fontSize = 13.sp, color = RunGray)
                Spacer(Modifier.height(12.dp))
                Text(detail!!.overview ?: "설명이 없어요.", fontSize = 14.sp, color = RunBlack)
            }
        }
    }
}
