package com.example.runq

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════
// Finish Hub 기준 EAT / CAFE / SEE 조회
//
// 코스 완주 → course.finishHubIds[0] → findHub() → hub 좌표+반경으로
// TourAPI locationBasedList2를 호출한다. RunQData.places(엑셀 큐레이션)에 있는
// 장소를 "RunQ Pick"으로 맨 위에 고정하고, API 결과는 뒤에 보충한다.
// ════════════════════════════════════════════════════════

enum class PlaceCategory(val label: String, val accent: Color) {
    EAT("EAT", RunLime), CAFE("CAFE", RunPurple), SEE("SEE", RunLavender)
}

data class FinishHubPlace(
    val title: String,
    val addr: String,
    val category: PlaceCategory,
    val contentId: String? = null,     // TourAPI 결과일 때만 값이 있음 → Place Detail에서 상세조회 가능
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
    val isCurated: Boolean = false     // RunQ 큐레이션(엑셀) 출처인지
)

data class FinishHubResult(
    val hub: FinishHub,
    val eat: List<FinishHubPlace>,
    val cafe: List<FinishHubPlace>,
    val see: List<FinishHubPlace>
)

private val cafeKeywords = listOf("카페", "커피", "베이커리", "로스터리", "coffee", "cafe")
private val seeContentTypeIds = listOf(12, 14, 15, 28) // 관광지 / 문화시설 / 축제행사 / 레포츠

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

suspend fun fetchFinishHubPlaces(hub: FinishHub): FinishHubResult {
    val hubLat = hub.resolvedLat
    val hubLng = hub.resolvedLng

    // EAT + CAFE 후보: TourAPI 음식점(contentTypeId=39)에서 카페 키워드로 1차 분리
    // (TourAPI에는 카페 전용 contentTypeId가 없음 → P1에서 Kakao Local CE7로 보완 예정)
    val foodItems = runCatching {
        TourApiClient.api.getNearbyPlaces(
            mapX = hubLng, mapY = hubLat, radius = maxOf(hub.eatRadiusM, hub.cafeRadiusM), contentTypeId = 39
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
                mapX = hubLng, mapY = hubLat, radius = hub.seeRadiusM, contentTypeId = typeId
            ).response.body.items?.item ?: emptyList()
        }.getOrDefault(emptyList())
    }.distinctBy { it.title }
        .map { FinishHubPlace(it.title ?: "-", it.addr1 ?: "", PlaceCategory.SEE, it.contentId, it.dist) }

    return FinishHubResult(
        hub = hub,
        eat = mergeCurated(curatedPlaces(hub, PlaceCategory.EAT), eatApiRaw),
        cafe = mergeCurated(curatedPlaces(hub, PlaceCategory.CAFE), cafeApiRaw),
        see = mergeCurated(curatedPlaces(hub, PlaceCategory.SEE), seeApiRaw)
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
    var distance by remember { mutableStateOf(0.0) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(true) }
    val targetKm = remember(course) { course.distanceKm?.takeIf { it > 0 } ?: 5.0 }

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
            else listOfNotNull(course.startPoint(), course.finishPoint())
        interpolateAlongRoute(line, (distance / targetKm).toFloat())
    }
    val progress = remember(distance, targetKm) { (distance / targetKm).toFloat().coerceIn(0f, 1f) }

    val paceLabel = remember(distance, elapsedSeconds) {
        if (distance < 0.01) "0'00\"" else {
            val paceSec = (elapsedSeconds / distance).toInt()
            "${paceSec / 60}'${(paceSec % 60).toString().padStart(2, '0')}\""
        }
    }
    val durationLabel = remember(elapsedSeconds) {
        val h = elapsedSeconds / 3600; val m = (elapsedSeconds % 3600) / 60; val s = elapsedSeconds % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    Box(modifier = Modifier.fillMaxSize().background(RunCream)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UtilityPill("잠금", Modifier.weight(1f))
                UtilityPill("설정", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            CourseMapCard(course = course, currentLocation = simulatedLocation, heightDp = 300)
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
                "${String.format("%.2f", distance)} / ${course.distanceLabel()}",
                fontSize = 10.sp, color = RunGray
            )
            Spacer(Modifier.height(24.dp))
            Text(
                String.format("%.2f", distance), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = RunBlack,
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
                            .background(RunBgGray).clickable { onFinish(distance, elapsedSeconds) },
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
            onPlaceClick = { hub, place -> step = PlaceStep.Detail(hub, place) },
            onSeeAll = { hub, category -> step = PlaceStep.HubList(hub, category) }
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
            hub = s.hub,
            onBack = { step = PlaceStep.Home }
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
    var sortByDistance by remember { mutableStateOf(true) }
    var hubMenuExpanded by remember { mutableStateOf(false) }

    val places = remember(currentHub, category, sortByDistance) {
        val hub = currentHub
        if (hub == null) emptyList() else {
            val filtered = RunQData.places.filter {
                it.finishHubId == hub.id && it.status != ContentStatus.HIDDEN && (category == null || it.category == category)
            }
            if (sortByDistance) filtered.sortedBy { it.distMeters?.toDoubleOrNull() ?: Double.MAX_VALUE }
            else filtered.sortedWith(compareByDescending<FinishHubPlace> { it.isFeatured }.thenBy { it.displayOrder })
        }
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
                Text("${places.size} PLACES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunBlack)
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
                    Text("지금 가까운 곳", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Spacer(Modifier.height(4.dp))
                    Text("Finish Hub 주변 ${places.size}곳", fontSize = 11.sp, color = RunGray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceSortChip("거리순", sortByDistance) { sortByDistance = true }
                    PlaceSortChip("추천순", !sortByDistance) { sortByDistance = false }
                }
            }
            category?.let { c ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "$c 전체보기", fontSize = 12.sp, color = RunPurple, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSeeAll(hub, c) }
                )
            }
            Spacer(Modifier.height(14.dp))
            if (places.isEmpty()) {
                EmptyStateView("📍", "아직 등록된 장소가 없어요", "다른 Hub나 카테고리를 확인해보세요.", Modifier.padding(top = 20.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Box(
                modifier = Modifier.size(70.dp).clip(RoundedCornerShape(14.dp)).background(place.category.accent.copy(alpha = 0.35f))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(place.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(place.category.accent).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(place.category.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    }
                    meters?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("${it.toInt()}m", fontSize = 11.sp, color = RunGray)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    meters?.let { "도보 ${walkingMinutes(it).toInt()} 분" }
                        ?: (if (place.isCurated) "RunQ가 골라둔 스팟" else place.addr.ifBlank { "코스 근처 스팟" }),
                    fontSize = 11.sp, color = RunGray
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
            PlaceCategory.values().forEach { c -> CategoryChip(c, tab == c) { tab = c } }
        }
        Spacer(Modifier.height(20.dp))

        val list = when (tab) {
            PlaceCategory.EAT -> result?.eat
            PlaceCategory.CAFE -> result?.cafe
            PlaceCategory.SEE -> result?.see
        }
        when {
            hub == null -> EmptyStateView("📍", "Finish Hub 정보가 없어요", "이 코스는 아직 Finish Hub가 연결되지 않았어요.", Modifier.padding(top = 30.dp))
            loadFailed -> ErrorStateView(
                "추천 정보를 불러오지 못했어요", "네트워크 연결을 확인한 뒤 다시 시도해주세요.",
                onRetry = { retryTick++ }, modifier = Modifier.padding(top = 30.dp)
            )
            list == null -> SkeletonList(count = 3, modifier = Modifier.padding(top = 4.dp))
            list.isEmpty() -> EmptyStateView("🔍", "추천 장소를 찾지 못했어요", "다른 카테고리를 확인해보세요.", Modifier.padding(top = 30.dp))
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
fun PlaceDetailScreen(place: FinishHubPlace, hubName: String? = null, hub: FinishHub? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<DetailCommonItem?>(null) }
    var loading by remember { mutableStateOf(place.contentId != null) }
    var kakaoInfo by remember { mutableStateOf<KakaoPlaceLookup?>(null) }

    LaunchedEffect(place.contentId) {
        val id = place.contentId
        if (id != null) {
            detail = runCatching {
                TourApiClient.api.getDetailCommon(contentId = id).response.body.items?.item?.firstOrNull()
            }.getOrNull()
            loading = false
        }
    }

    // 엑셀에 주소/좌표가 비어있는 RunQ 큐레이션 장소만 Kakao Local 검색으로 보완한다.
    LaunchedEffect(place.id) {
        if (place.addr.isBlank() || place.lat == null || place.lng == null) {
            kakaoInfo = fetchKakaoPlaceInfo(place.title)
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
            listOfNotNull(hubName, place.distanceLabel(hub)?.let { "Finish Hub에서 $it" }).joinToString(" · ")
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

        PlaceInfoRow("주소", (detail?.addr1 ?: place.addr).ifBlank { kakaoInfo?.address ?: "주소 정보 준비중" })
        Spacer(Modifier.height(10.dp))
        PlaceInfoRow("전화", kakaoInfo?.phone ?: "정보 없음")
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

        Button(
            onClick = {
                val lat = kakaoInfo?.lat ?: place.lat ?: hub?.resolvedLat
                val lng = kakaoInfo?.lng ?: place.lng ?: hub?.resolvedLng
                val label = Uri.encode(place.title)
                val uri = if (lat != null && lng != null) {
                    Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
                } else {
                    Uri.parse("geo:0,0?q=$label")
                }
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) {
                    // 지도 앱이 없는 기기 — 조용히 무시(스낵바 등은 디자인 확정 전까지 보류)
                }
            },
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
