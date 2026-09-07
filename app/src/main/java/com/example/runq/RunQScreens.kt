package com.example.runq

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// ════════════════════════════════════════════════════════
// 홈 화면: Figma "10 Home/Main" 기준 — 크림 배경 + 좌상단 라임/우상단 라벤더 ambient glow +
// 검색 + 거리칩 + Today's Run(라임→연노랑→라벤더 그라데이션 카드)
// ════════════════════════════════════════════════════════
sealed class HomeStep {
    object Main : HomeStep()
    object Search : HomeStep()
    object Notifications : HomeStep()
}

@Composable
fun HomeFlow(onFindCourses: () -> Unit) {
    var step by remember { mutableStateOf<HomeStep>(HomeStep.Main) }
    when (step) {
        HomeStep.Main -> HomeScreen(
            onFindCourses = onFindCourses,
            onOpenSearch = { step = HomeStep.Search },
            onOpenNotifications = { step = HomeStep.Notifications }
        )
        HomeStep.Search -> SearchResultsScreen(onBack = { step = HomeStep.Main }, onCourseClick = { onFindCourses() })
        HomeStep.Notifications -> NotificationsScreen(onBack = { step = HomeStep.Main })
    }
}

@Composable
fun HomeScreen(onFindCourses: () -> Unit, onOpenSearch: () -> Unit = {}, onOpenNotifications: () -> Unit = {}) {
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    var distanceFilter by remember { mutableStateOf("전체") }
    var searchText by remember { mutableStateOf("") }
    // "다음 러닝으로 설정"(Saved 탭)한 코스가 있으면 최우선으로 보여준다.
    val featured = remember {
        val pinned = SavedItemsStore.nextCourseId?.let { id -> RunQData.courses.find { it.id == id && it.status != ContentStatus.HIDDEN } }
        pinned ?: RunQData.courses.filter { it.status != ContentStatus.HIDDEN }.maxByOrNull { it.rating }
    }

    LaunchedEffect(featured?.id) {
        try { safety = fetchSafety(featured?.weatherGrid()) } catch (e: Exception) { }
    }

    Box(modifier = Modifier.fillMaxSize().background(RunCream)) {
        // Figma "10 Home/Main": 헤더 뒤는 통짜 그라데이션이 아니라, 좌상단 라임/우상단 라벤더
        // 은은한 ambient glow 블롭 두 개만 크림 배경 위에 흐릿하게 얹혀있음.
        Box(
            modifier = Modifier.size(310.dp).offset(x = (-100).dp, y = (-90).dp).blur(60.dp)
                .background(Brush.radialGradient(listOf(RunLime.copy(alpha = 0.5f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier.size(255.dp).offset(x = 190.dp, y = 55.dp).blur(60.dp)
                .background(Brush.radialGradient(listOf(RunLavender.copy(alpha = 0.5f), Color.Transparent)), CircleShape)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RunQ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(RunWhite.copy(alpha = 0.7f))
                            .clickable { onOpenNotifications() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "알림", tint = RunBlack, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "오늘은 어디로 달려볼까요?", fontSize = 25.sp, fontWeight = FontWeight.Bold,
                    color = RunBlack, lineHeight = 30.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(RunWhite)
                        .border(1.dp, RunBorderGray, RoundedCornerShape(18.dp))
                        .clickable { onOpenSearch() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = RunGray, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    if (searchText.isEmpty()) {
                        Text("코스, 거리, 장소를 검색해보세요", fontSize = 13.sp, color = RunGray)
                    } else {
                        Text(searchText, fontSize = 13.sp, color = RunBlack)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("거리로 찾기", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("전체", "3km", "5km", "10km+").forEach { label ->
                        val selected = distanceFilter == label
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (selected) RunPurple else RunWhite)
                                .then(if (selected) Modifier else Modifier.border(1.dp, RunBorderGray, RoundedCornerShape(20.dp)))
                                .clickable { distanceFilter = label }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RunBlack)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)
            ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("오늘의 추천 코스", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Text(
                        "전체보기", fontSize = 12.sp, color = RunGray,
                        modifier = Modifier.clickable { onFindCourses() }
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (featured != null) {
                    TodaysRunCard(course = featured, safety = safety, onClick = onFindCourses)
                } else {
                    Text("아직 등록된 코스가 없어요.", fontSize = 13.sp, color = RunGray)
                }
            }
            }
        }
    }
}

@Composable
fun TodaysRunCard(course: Course, safety: SafetyInfo?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RunLime)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(RunLime, Color(0xFFF7F592), RunLavender))
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("TODAY'S RUN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunBlack.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                Text(
                    "${course.locationLabel().removePrefix("강릉 ")}, ${course.distanceLabel()} 가볍게",
                    fontSize = 25.sp, fontWeight = FontWeight.Bold, color = RunBlack, lineHeight = 29.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${safety?.temp ?: "--"} · 미세먼지 ${safety?.pm10 ?: "-"} · 예상 ${course.timeLabel()}",
                        fontSize = 12.sp, color = RunBlack.copy(alpha = 0.75f), modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(RunBlack)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("코스 보기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunWhite)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// 러닝 화면: 지도 및 GPS 실시간 연동
// ════════════════════════════════════════════════════════
@Composable
fun RunningScreen() {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var totalDistanceKm by remember { mutableStateOf(0.0) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasFix by remember { mutableStateOf(false) }
    
    // 현재 코스 정보 (Saved탭에서 "다음 러닝으로 설정"한 코스가 있으면 로드)
    val currentCourse = remember {
        SavedItemsStore.nextCourseId?.let { id -> RunQData.courses.find { it.id == id } }
    }
    val routePoints = currentCourse?.routePoints ?: emptyList()

    // 진행 거리 (코스 위에서의 누적 거리) / 남은 거리
    var progressKm by remember { mutableStateOf(0.0) }
    val remainingKm = remember(currentCourse, progressKm) {
        val total = currentCourse?.distanceKm ?: 0.0
        (total - progressKm).coerceAtLeast(0.0)
    }

    var currentLocation by remember { mutableStateOf(currentCourse?.startPoint() ?: RoutePoint(37.7946, 128.9022)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasLocationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // FusedLocationProviderClient 연동
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    DisposableEffect(hasLocationPermission, isRunning) {
        if (!hasLocationPermission || !isRunning) return@DisposableEffect onDispose {}

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

    // 경과 시간
    LaunchedEffect(isRunning) {
        while (isRunning) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds += 1
        }
    }

    val paceLabel = remember(totalDistanceKm, elapsedSeconds) {
        if (totalDistanceKm < 0.01) "0'00\"" else {
            val paceSec = (elapsedSeconds / totalDistanceKm).toInt()
            "${paceSec / 60}'${(paceSec % 60).toString().padStart(2, '0')}\""
        }
    }
    val durationLabel = remember(elapsedSeconds) {
        val m = elapsedSeconds / 60; val s = elapsedSeconds % 60
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    Box(modifier = Modifier.fillMaxSize().background(RunWhite)) {
        if (hasLocationPermission) {
            KakaoRouteMap(
                modifier = Modifier.fillMaxSize(),
                routePoints = routePoints,
                startPoint = currentCourse?.startPoint(),
                finishPoint = currentCourse?.finishPoint(),
                currentLocation = currentLocation
            )
        } else {
            Box(Modifier.fillMaxSize().background(RunBgGray), contentAlignment = Alignment.Center) {
                PermissionPromptView(
                    icon = "📍", title = "위치 권한이 필요해요",
                    message = "지도 표시와 러닝 거리 측정을 위해 위치 권한을 허용해주세요.",
                    actionLabel = "권한 허용하기",
                    onAction = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        // 상단 오버레이
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(RunWhite.copy(alpha = 0.8f), Color.Transparent, Color.Transparent))))

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, null, tint = RunBlack, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("32°C", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GpsFixed, null, tint = if(hasFix) RunPurple else RunGray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (hasFix) "GPS" else "GPS 찾는 중", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(40.dp))

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(java.util.Locale.US, "%.2f", if (routePoints.isNotEmpty()) progressKm else totalDistanceKm), fontSize = 80.sp, fontWeight = FontWeight.Black, color = RunBlack)
                Text(if (routePoints.isNotEmpty()) "Progress (Km)" else "Distance (Km)", fontSize = 14.sp, color = RunGray)
                if (routePoints.isNotEmpty()) {
                    Text(String.format(java.util.Locale.US, "남은 거리: %.2f km", remainingKm), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunPurple)
                }
            }

            Spacer(Modifier.height(40.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem(paceLabel, "Avg Pace")
                MetricItem(durationLabel, "Duration")
                MetricItem("${(totalDistanceKm * 60).toInt()} kcal", "Calories")
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp).clickable {
                        if (!hasLocationPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    shape = RoundedCornerShape(32.dp),
                    color = RunWhite,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(24.dp), tint = if(hasLocationPermission) RunPurple else RunGray)
                    }
                }

                Button(
                    onClick = { isRunning = !isRunning },
                    modifier = Modifier.height(64.dp).width(160.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if(!isRunning) RunLime else RunBlack, contentColor = if(!isRunning) RunBlack else RunWhite)
                ) {
                    Text(if(!isRunning) "START" else "PAUSE", fontWeight = FontWeight.Black, fontSize = 18.sp)
                }

                Surface(
                    modifier = Modifier.size(64.dp).clickable {
                        val finalDist = if (routePoints.isNotEmpty()) progressKm else totalDistanceKm
                        if (finalDist > 0.01) {
                            RunHistoryStore.add(
                                RunRecord(
                                    id = java.util.UUID.randomUUID().toString(),
                                    courseId = currentCourse?.id,
                                    courseName = currentCourse?.name ?: "자유 러닝",
                                    timestampMillis = System.currentTimeMillis(),
                                    distanceKm = finalDist,
                                    elapsedSeconds = elapsedSeconds
                                )
                            )
                        }
                        totalDistanceKm = 0.0; progressKm = 0.0; elapsedSeconds = 0; isRunning = false
                    },
                    shape = RoundedCornerShape(32.dp),
                    color = RunWhite,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(28.dp), tint = Color.Red)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun MetricItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, fontSize = 12.sp, color = RunGray)
    }
}

// HistoryScreen / ClubScreen은 하단 탭이 5개(Home/Course/Run/Place/My)로 바뀌면서 제거됨.
// My/History 관련 화면(Figma "40 My/*")은 MyScreens.kt로 이동.

// ════════════════════════════════════════════════════════
// 12 Home / Search Results — 코스명·지역명으로 실제 RunQData를 필터링한다.
// ════════════════════════════════════════════════════════
@Composable
fun SearchResultsScreen(onBack: () -> Unit, onCourseClick: (Course) -> Unit) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) } // 0 = 코스, 1 = 장소

    val matchedCourses = remember(query) {
        if (query.isBlank()) emptyList() else RunQData.courses.filter {
            it.status != ContentStatus.HIDDEN &&
                (it.name.contains(query, true) || it.region.contains(query, true) || (it.location ?: "").contains(query, true))
        }
    }
    val matchedPlaces = remember(query) {
        if (query.isBlank()) emptyList() else RunQData.places.filter {
            it.status != ContentStatus.HIDDEN && (it.title.contains(query, true) || it.addr.contains(query, true))
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp))
                    .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(23.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = RunGray, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("코스, 거리, 장소를 검색해보세요", fontSize = 14.sp, color = RunGray)
                    androidx.compose.foundation.text.BasicTextField(
                        value = query, onValueChange = { query = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = RunBlack, fontFamily = com.example.runq.ui.theme.NotoSansKR),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(RunBlack)
                    )
                }
                if (query.isNotEmpty()) {
                    Text("×", fontSize = 16.sp, color = RunGray, modifier = Modifier.clickable { query = "" })
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("취소", fontSize = 14.sp, color = RunBlack, modifier = Modifier.clickable { onBack() })
        }
        Spacer(Modifier.height(16.dp))

        if (query.isBlank()) {
            EmptyStateView("🔍", "검색어를 입력해보세요", "코스명, 지역, 장소로 찾을 수 있어요.", Modifier.padding(top = 40.dp))
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFBF9F1)).padding(16.dp)) {
            Column {
                Text("SEARCH RESULTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF91876E))
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("\"$query\"로 찾은 결과", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Text("코스 ${matchedCourses.size}개 · 장소 ${matchedPlaces.size}곳", fontSize = 11.sp, color = RunGray)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(21.dp)).padding(4.dp)
        ) {
            SearchTabChip("코스 ${matchedCourses.size}", tab == 0, Modifier.weight(1f)) { tab = 0 }
            SearchTabChip("장소 ${matchedPlaces.size}", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }
        Spacer(Modifier.height(16.dp))

        if (tab == 0) {
            if (matchedCourses.isEmpty()) {
                EmptyStateView("🔍", "일치하는 코스가 없어요", "다른 검색어로 시도해보세요.", Modifier.padding(top = 30.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(matchedCourses) { index, course -> CourseListRow(course, index) { onCourseClick(course) } }
                }
            }
        } else {
            if (matchedPlaces.isEmpty()) {
                EmptyStateView("🔍", "일치하는 장소가 없어요", "다른 검색어로 시도해보세요.", Modifier.padding(top = 30.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(matchedPlaces) { place ->
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(16.dp)).padding(14.dp)
                        ) {
                            Column {
                                Text(place.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                                Spacer(Modifier.height(4.dp))
                                Text("${place.category.label} · ${place.addr.ifBlank { "주소 준비중" }}", fontSize = 11.sp, color = RunGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTabChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Color(0xFFF6F7B2) else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) RunBlack else RunGray)
    }
}

// ════════════════════════════════════════════════════════
// 13 Home / Notifications — 실제 앱 상태(러닝 기록/저장 목록/추천 코스)에서 생성한 알림만
// 보여준다. 백엔드 푸시가 없어서 Figma의 구체적인 마케팅 카피는 재현하지 않는다.
// ════════════════════════════════════════════════════════
private data class LocalNotice(val icon: String, val title: String, val message: String, val whenLabel: String)

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val notices = remember {
        buildList {
            val featured = RunQData.courses.filter { it.status != ContentStatus.HIDDEN && it.isFeatured }
            if (featured.isNotEmpty()) {
                add(LocalNotice("⚡", "오늘의 추천 코스가 있어요", "${featured.first().name} 코스를 확인해보세요.", "오늘"))
            }
            val savedCount = SavedItemsStore.savedCourses().size + SavedItemsStore.savedPlaces().size
            if (savedCount > 0) {
                add(LocalNotice("♡", "저장한 목록이 있어요", "저장한 코스·장소 ${savedCount}개를 My 탭에서 다시 확인해보세요.", "저장됨"))
            }
            val totalCount = RunHistoryStore.totalCount()
            if (totalCount > 0) {
                add(LocalNotice("🏃", "누적 러닝 ${totalCount}회 달성!", "총 ${String.format("%.1f", RunHistoryStore.totalKm())}km를 달렸어요.", "누적 기록"))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(RunWhite)
                    .border(1.dp, RunBorderGray, CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = RunBlack, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Text("알림", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        }
        Spacer(Modifier.height(10.dp))
        Text("RunQ의 새로운 소식을 확인해보세요.", fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(20.dp))
        if (notices.isEmpty()) {
            EmptyStateView("🔔", "아직 알림이 없어요", "코스를 저장하거나 러닝을 완주하면 알림이 생겨요.", Modifier.padding(top = 40.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(notices) { notice ->
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(20.dp)).padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(RunLime.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) { Text(notice.icon, fontSize = 16.sp) }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(notice.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                                Spacer(Modifier.height(4.dp))
                                Text(notice.message, fontSize = 12.sp, color = RunGray)
                                Spacer(Modifier.height(6.dp))
                                Text(notice.whenLabel, fontSize = 11.sp, color = RunGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
