package com.example.runq

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
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
fun HomeFlow(
    onFindCourses: () -> Unit,
    onOpenPlace: (FinishHubPlace) -> Unit = {},
    onOpenNoticeCourse: (Course) -> Unit = { onFindCourses() },
    onOpenNoticePlaceCategory: (String?, PlaceCategory) -> Unit = { _, _ -> },
    onOpenNoticeHistory: () -> Unit = {},
    onOpenNoticeSaved: () -> Unit = {}
) {
    var step by remember { mutableStateOf<HomeStep>(HomeStep.Main) }
    when (step) {
        HomeStep.Main -> HomeScreen(
            onFindCourses = onFindCourses,
            onOpenSearch = { step = HomeStep.Search },
            onOpenNotifications = { step = HomeStep.Notifications }
        )
        HomeStep.Search -> SearchResultsScreen(
            onBack = { step = HomeStep.Main },
            onCourseClick = { course -> CourseTabRequest.requestDetail(course); onFindCourses() },
            onPlaceClick = onOpenPlace
        )
        HomeStep.Notifications -> NotificationsScreen(
            onBack = { step = HomeStep.Main },
            onOpenCourse = onOpenNoticeCourse,
            onOpenPlaceCategory = onOpenNoticePlaceCategory,
            onOpenHistory = onOpenNoticeHistory,
            onOpenSaved = onOpenNoticeSaved
        )
    }
}

@Composable
fun HomeScreen(onFindCourses: () -> Unit, onOpenSearch: () -> Unit = {}, onOpenNotifications: () -> Unit = {}) {
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    var distanceFilter by remember { mutableStateOf("전체") }
    var searchText by remember { mutableStateOf("") }
    // "다음 러닝으로 설정"(Saved 탭)한 코스가 있으면 최우선으로 보여주고,
    // 없으면 콘텐츠팀이 is_featured로 표시해둔 코스 중 노출순서가 가장 앞선 것을 보여준다.
    val featured = remember {
        val pinned = SavedItemsStore.nextCourseId?.let { id -> RunQData.courses.find { it.id == id && it.status != ContentStatus.HIDDEN } }
        val visible = RunQData.courses.filter { it.status != ContentStatus.HIDDEN }
        pinned ?: visible.filter { it.isFeatured }.minByOrNull { it.displayOrder } ?: visible.minByOrNull { it.displayOrder }
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
                        if (remember { hasUnreadNotices() }) {
                            Box(
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp)
                                    .size(8.dp).clip(CircleShape).background(RunPurple)
                            )
                        }
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
                                .clickable {
                                    distanceFilter = label
                                    // Course 탭으로 넘어가서 이 거리 조건이 바로 적용된 목록을 보여준다.
                                    CourseTabRequest.request(label)
                                    onFindCourses()
                                }
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
                Text("오늘의 날씨", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(14.dp))
                TodayWeatherCard(safety)
                Spacer(Modifier.height(24.dp))
            }
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
                    TodaysRunCard(course = featured, onClick = { CourseTabRequest.requestDetail(featured); onFindCourses() })
                } else {
                    Text("아직 등록된 코스가 없어요.", fontSize = 13.sp, color = RunGray)
                }
            }
            }
        }
    }
}

// 홈 화면 "오늘의 추천 코스" 카드 — 날씨 정보는 별도의 TodayWeatherCard로 분리됐으므로
// 여기선 Course 목록(CourseListRow)과 동일한 단순한 카드 스타일을 그대로 재사용한다.
@Composable
fun TodaysRunCard(course: Course, onClick: () -> Unit) {
    CourseListRow(course = course, onClick = onClick)
}

// 홈 화면 전용 "오늘의 날씨" 박스 — 기온/미세먼지/바람/강수를 한눈에 보여준다.
// (지금까지는 TodaysRunCard 한 줄 요약에만 끼워 넣었는데, 별도 박스로 분리해달라는 요청 반영)
@Composable
fun TodayWeatherCard(safety: SafetyInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RunWhite),
        border = BorderStroke(1.dp, RunBorderGray)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WeatherStat("기온", safety?.temp ?: "-")
                WeatherStat("미세먼지", safety?.pm10 ?: "-")
                WeatherStat("바람", safety?.wind ?: "-")
                WeatherStat("강수", safety?.rain ?: "-")
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(RunLime).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "러닝 적합도: ${safety?.fitness ?: "확인중"}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack
                )
            }
        }
    }
}

@Composable
private fun WeatherStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = RunGray)
    }
}

// sky는 실제 API 값이 있을 때만 채워진다(SafetyApiService.fetchSafety) — 모르면
// "맑음" 아이콘으로 단정 짓지 않고 중립적인 온도계 아이콘을 쓴다.
private fun skyIcon(sky: String?): ImageVector = when (sky) {
    "맑음" -> Icons.Default.WbSunny
    "구름많음", "흐림" -> Icons.Default.Cloud
    "비", "비/눈", "소나기" -> Icons.Default.Umbrella
    "눈" -> Icons.Default.AcUnit
    else -> Icons.Default.Thermostat
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
    // 처음 권한이 없는 상태로 들어왔을 때만 "왜 필요한지" 설명 화면을 먼저 보여준다.
    // 이미 권한이 있으면(재진입 등) 바로 지도로 들어간다.
    var showLocationRationale by remember { mutableStateOf(!hasLocationPermission) }
    var showGpsError by remember { mutableStateOf(false) }
    var hasFix by remember { mutableStateOf(false) }
    // 정지 버튼을 눌러 저장한 직후의 기록 — null이 아니면 완주 요약 모달을 보여준다.
    var finishedRecord by remember { mutableStateOf<RunRecord?>(null) }

    // 현재 코스 정보 (Saved탭에서 "다음 러닝으로 설정"한 코스가 있으면 로드)
    val currentCourse = remember {
        SavedItemsStore.nextCourseId?.let { id -> RunQData.courses.find { it.id == id } }
    }
    val routePoints = currentCourse?.routePoints ?: emptyList()

    // 상단 날씨 표시 — 예전엔 "32°C"/해 아이콘이 그냥 고정값이었다. 실제 API로 채운다.
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    LaunchedEffect(currentCourse?.id) {
        safety = runCatching { fetchSafety(currentCourse?.weatherGrid()) }.getOrNull()
    }

    // 진행 거리 (코스 위에서의 누적 거리) / 남은 거리
    var progressKm by remember { mutableStateOf(0.0) }
    val remainingKm = remember(currentCourse, progressKm) {
        val total = currentCourse?.resolvedDistanceKm() ?: 0.0
        (total - progressKm).coerceAtLeast(0.0)
    }

    var currentLocation by remember { mutableStateOf(currentCourse?.startPoint() ?: RoutePoint(37.7946, 128.9022)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasLocationPermission = isGranted
    }

    // 러닝을 시작했는데 권한은 있으면서 일정 시간 GPS 신호를 못 잡으면 안내 모달을 띄운다.
    LaunchedEffect(hasLocationPermission, isRunning) {
        if (hasLocationPermission && isRunning) {
            kotlinx.coroutines.delay(10_000)
            if (!hasFix) showGpsError = true
        }
    }
    LaunchedEffect(hasFix) { if (hasFix) showGpsError = false }

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
                    modifier = Modifier.padding(24.dp),
                    mascot = R.drawable.mascot_dragon
                )
            }
        }

        // 상단 오버레이
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(RunWhite.copy(alpha = 0.8f), Color.Transparent, Color.Transparent))))

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(skyIcon(safety?.sky), null, tint = RunBlack, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(safety?.temp?.takeIf { it != "-" } ?: "확인중", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        if (!hasLocationPermission) {
                            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            // 지도를 내 위치로 재중심 — currentLocation을 갱신하면 KakaoRouteMap이
                            // 알아서 카메라를 그 위치로 옮긴다(달리는 중이 아니어도 동작하도록
                            // requestLocationUpdates 대신 캐시된 마지막 위치를 즉시 사용).
                            try {
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                    if (location != null) {
                                        currentLocation = RoutePoint(location.latitude, location.longitude)
                                        hasFix = true
                                    }
                                }
                            } catch (e: SecurityException) { /* 권한이 방금 취소된 경우 — 조용히 무시 */ }
                        }
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
                        isRunning = false
                        if (finalDist > 0.01) {
                            val record = RunRecord(
                                id = java.util.UUID.randomUUID().toString(),
                                courseId = currentCourse?.id,
                                courseName = currentCourse?.name ?: "자유 러닝",
                                timestampMillis = System.currentTimeMillis(),
                                distanceKm = finalDist,
                                elapsedSeconds = elapsedSeconds,
                                tempLabel = safety?.temp,
                                pm10Label = safety?.pm10
                            )
                            RunHistoryStore.add(record)
                            finishedRecord = record
                        }
                        totalDistanceKm = 0.0; progressKm = 0.0; elapsedSeconds = 0
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

        finishedRecord?.let { record ->
            FreeRunSummaryModal(record = record, onDismiss = { finishedRecord = null })
        }
    }
}

// 코스 없이 정지 버튼을 눌러 자유 러닝을 마쳤을 때 보여주는 완주 요약 —
// CompleteScreen은 Course/Finish Hub가 있어야 해서(자유 러닝엔 둘 다 없음) 별도로 둔다.
@Composable
private fun FreeRunSummaryModal(record: RunRecord, onDismiss: () -> Unit) {
    val caloriesEstimate = remember(record.distanceKm) { (record.distanceKm * 65).toInt() }
    Box(modifier = Modifier.fillMaxSize().background(RunBlack.copy(alpha = 0.45f)).clickable(enabled = false) {})
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp)
            .clip(RoundedCornerShape(24.dp)).background(RunWhite).padding(24.dp)
    ) {
        Text("RUN COMPLETE", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = RunGray)
        Spacer(Modifier.height(6.dp))
        Text("러닝 완료!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(4.dp))
        // "다음 러닝으로 설정"한 코스를 따라 뛴 경우엔 그 코스 이름을, 아니면 "자유 러닝"을 보여준다
        // (record.courseName이 이미 이 우선순위로 채워져 있음).
        Text(record.courseName, fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            CourseInfoLine("거리", String.format(java.util.Locale.US, "%.2f KM", record.distanceKm), Modifier.weight(1f))
            CourseInfoLine("운동 시간", record.durationLabel(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            CourseInfoLine("평균 페이스", "${record.paceLabel()} /KM", Modifier.weight(1f))
            CourseInfoLine("소모 칼로리", "$caloriesEstimate KCAL", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
                .background(RunLime).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) { Text("확인", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RunBlack) }
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
fun SearchResultsScreen(onBack: () -> Unit, onCourseClick: (Course) -> Unit, onPlaceClick: (FinishHubPlace) -> Unit = {}) {
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
                EmptyStateView(
                    "🔍", "검색 결과가 없어요", "다른 지역명이나 코스 이름으로\n다시 검색해보세요.", Modifier.padding(top = 30.dp),
                    mascot = R.drawable.mascot_dragon, actionLabel = "검색 다시하기", onAction = { query = "" }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(matchedCourses) { course -> CourseListRow(course) { onCourseClick(course) } }
                }
            }
        } else {
            if (matchedPlaces.isEmpty()) {
                EmptyStateView(
                    "🔍", "검색 결과가 없어요", "다른 지역명이나 코스 이름으로\n다시 검색해보세요.", Modifier.padding(top = 30.dp),
                    mascot = R.drawable.mascot_dragon, actionLabel = "검색 다시하기", onAction = { query = "" }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(matchedPlaces) { place ->
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(16.dp))
                                .clickable { onPlaceClick(place) }.padding(14.dp)
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
// 13 Home / Notifications — 실제 앱 상태(러닝 기록/저장 목록/추천 코스/Finish Hub/주간 통계)에서
// 생성한 알림만 보여준다. 백엔드 푸시가 없어서 Figma의 구체적인 마케팅 카피는 재현하지 않는다.
// ════════════════════════════════════════════════════════

// 알림을 눌렀을 때 이동할 실제 화면 — 전부 이미 있는 탭 간 요청 패턴(CourseTabRequest 등)을 탄다.
private sealed class NoticeAction {
    data class OpenCourse(val course: Course) : NoticeAction()
    data class OpenPlaceCategory(val hubId: String?, val category: PlaceCategory) : NoticeAction()
    object OpenHistory : NoticeAction()
    object OpenSaved : NoticeAction()
}

private data class LocalNotice(
    val id: String,
    val icon: String,
    val title: String,
    val message: String,
    val timestampMillis: Long,
    val action: NoticeAction? = null
)

// 오늘/어제/그 이전 날짜별로 묶어 보여주기 위한 라벨.
private fun dayLabel(timestampMillis: Long): String {
    val cal = java.util.Calendar.getInstance()
    val todayYear = cal.get(java.util.Calendar.YEAR); val todayDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
    cal.timeInMillis = timestampMillis
    val diffDays = when {
        cal.get(java.util.Calendar.YEAR) == todayYear -> todayDay - cal.get(java.util.Calendar.DAY_OF_YEAR)
        cal.get(java.util.Calendar.YEAR) < todayYear -> Int.MAX_VALUE
        else -> Int.MIN_VALUE
    }
    return when (diffDays) {
        0 -> "오늘"
        1 -> "어제"
        else -> java.text.SimpleDateFormat("M.d", java.util.Locale.KOREA).format(timestampMillis)
    }
}

// 알림 목록 생성 — 각 알림은 전부 실제 앱 데이터에서만 만들어진다(가짜 문구 없음).
private fun buildNotices(): List<LocalNotice> {
    return buildList {
        val featured = RunQData.courses.filter { it.status != ContentStatus.HIDDEN && it.isFeatured }
        if (featured.isNotEmpty()) {
            val course = featured.first()
            add(
                LocalNotice(
                    id = "featured_${course.id}", icon = "⚡", title = "오늘의 추천 코스가 있어요",
                    message = "${course.name} 코스를 확인해보세요.", timestampMillis = System.currentTimeMillis(),
                    action = NoticeAction.OpenCourse(course)
                )
            )
        }

        val savedCount = SavedItemsStore.savedCourses().size + SavedItemsStore.savedPlaces().size
        if (savedCount > 0) {
            add(
                LocalNotice(
                    id = "saved_count_$savedCount", icon = "♡", title = "저장한 목록이 있어요",
                    message = "저장한 코스·장소 ${savedCount}개를 My 탭에서 다시 확인해보세요.",
                    timestampMillis = System.currentTimeMillis(), action = NoticeAction.OpenSaved
                )
            )
        }

        val records = RunHistoryStore.all() // timestampMillis 내림차순
        val latestRecord = records.firstOrNull()
        val totalCount = records.size
        if (totalCount > 0 && latestRecord != null) {
            add(
                LocalNotice(
                    id = "total_count_$totalCount", icon = "🏃", title = "누적 러닝 ${totalCount}회 달성!",
                    message = "총 ${String.format("%.1f", RunHistoryStore.totalKm())}km를 달렸어요.",
                    timestampMillis = latestRecord.timestampMillis, action = NoticeAction.OpenHistory
                )
            )

            // 방금 완주한 코스의 Finish Hub에 큐레이션 카페가 있을 때만 추천한다 — 없으면 알림 자체를 안 만든다.
            val hub = latestRecord.courseId
                ?.let { courseId -> RunQData.courses.find { it.id == courseId } }
                ?.finishHubIds?.firstOrNull()?.let { findHub(it) }
            if (hub != null) {
                val cafes = topCuratedCafes(hub)
                if (cafes.isNotEmpty()) {
                    add(
                        LocalNotice(
                            id = "hub_reco_${hub.id}_${latestRecord.id}", icon = "📍", title = "Finish Hub 추천",
                            message = "오늘 러닝 후 들르기 좋은 ${hub.name} 카페 ${cafes.size}곳을 모아봤어요.",
                            timestampMillis = latestRecord.timestampMillis,
                            action = NoticeAction.OpenPlaceCategory(hub.id, PlaceCategory.CAFE)
                        )
                    )
                }
            }
        }

        // 이번 주 러닝 기록이 있을 때만 리포트를 보여준다 — 지난주 기록이 있으면 거리 변화도 같이 계산.
        val thisWeek = RunHistoryStore.recordsInWeek(0)
        if (thisWeek.isNotEmpty()) {
            val thisWeekKm = thisWeek.totalDistanceKm()
            val lastWeekKm = RunHistoryStore.recordsInWeek(1).totalDistanceKm()
            val paceLabel = thisWeek.avgPaceLabel()
            val compareText = if (lastWeekKm > 0.01) {
                val diff = thisWeekKm - lastWeekKm
                val diffLabel = String.format("%.1f", kotlin.math.abs(diff))
                when {
                    diff > 0.01 -> " 지난주보다 ${diffLabel}km 더 달렸어요."
                    diff < -0.01 -> " 지난주보다 ${diffLabel}km 적게 달렸어요."
                    else -> " 지난주와 비슷한 페이스예요."
                }
            } else ""
            add(
                LocalNotice(
                    id = "weekly_report_${thisWeek.size}_${String.format("%.1f", thisWeekKm)}",
                    icon = "📊", title = "주간 러닝 리포트가 도착했어요",
                    message = "이번 주 총 ${String.format("%.1f", thisWeekKm)}km" +
                        (paceLabel?.let { " · 평균 페이스 $it/km" } ?: "") + "." + compareText,
                    timestampMillis = System.currentTimeMillis(), action = NoticeAction.OpenHistory
                )
            )
        }
    }.sortedByDescending { it.timestampMillis }
}

fun hasUnreadNotices(): Boolean = buildNotices().any { !NotificationReadStore.isRead(it.id) }

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenCourse: (Course) -> Unit = {},
    onOpenPlaceCategory: (String?, PlaceCategory) -> Unit = { _, _ -> },
    onOpenHistory: () -> Unit = {},
    onOpenSaved: () -> Unit = {}
) {
    val notices = remember { buildNotices() }
    var readVersion by remember { mutableStateOf(0) } // 읽음 처리 후 안읽음 점 갱신용 트리거

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
            EmptyStateView(
                "🔔", "아직 알림이 없어요", "코스를 저장하거나 러닝을 완주하면 알림이 생겨요.", Modifier.padding(top = 40.dp),
                mascot = R.drawable.mascot_bear
            )
        } else {
            val grouped = notices.groupBy { dayLabel(it.timestampMillis) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                grouped.forEach { (label, group) ->
                    item(key = "header_$label") {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunGray, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    }
                    items(group, key = { it.id }) { notice ->
                        key(readVersion) {
                            val isUnread = !NotificationReadStore.isRead(notice.id)
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(20.dp))
                                    .clickable {
                                        NotificationReadStore.markRead(notice.id)
                                        readVersion++
                                        when (val action = notice.action) {
                                            is NoticeAction.OpenCourse -> onOpenCourse(action.course)
                                            is NoticeAction.OpenPlaceCategory -> onOpenPlaceCategory(action.hubId, action.category)
                                            NoticeAction.OpenHistory -> onOpenHistory()
                                            NoticeAction.OpenSaved -> onOpenSaved()
                                            null -> {}
                                        }
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(RunLime.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) { Text(notice.icon, fontSize = 16.sp) }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(notice.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                                            if (isUnread) {
                                                Spacer(Modifier.width(6.dp))
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(RunPurple))
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(notice.message, fontSize = 12.sp, color = RunGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
