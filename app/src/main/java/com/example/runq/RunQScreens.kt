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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

// ════════════════════════════════════════════════════════
// 홈 화면: Figma "10 Home/Main" 기준 — 크림 배경 + 좌상단 라임/우상단 라벤더 ambient glow +
// 검색 + 거리칩 + Today's Run(라임→연노랑→라벤더 그라데이션 카드)
// ════════════════════════════════════════════════════════
@Composable
fun HomeScreen(onFindCourses: () -> Unit) {
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    var distanceFilter by remember { mutableStateOf("전체") }
    var searchText by remember { mutableStateOf("") }
    val featured = remember { RunQData.courses.filter { it.status != ContentStatus.HIDDEN }.maxByOrNull { it.rating } }

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
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(RunWhite.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "메뉴", tint = RunBlack, modifier = Modifier.size(20.dp))
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
    var distance by remember { mutableStateOf(0.0) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasFix by remember { mutableStateOf(false) }
    // 경포호 기본 위치 — 첫 GPS 픽스를 받기 전까지의 기본 지도 중심.
    var currentLocation by remember { mutableStateOf(RoutePoint(37.7946, 128.9022)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasLocationPermission = isGranted
    }

    // 권한이 없으면 화면 진입 시 바로 요청 — 예전에는 버튼을 눌러야만 요청돼서
    // "GPS가 안 잡힌다"는 착시가 생겼다.
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // 실시간 위치 갱신 — LocationManager.requestLocationUpdates로 지속 추적하고,
    // 러닝 중일 때만 이동 거리를 haversine으로 누적한다.
    DisposableEffect(hasLocationPermission) {
        var listener: android.location.LocationListener? = null
        var lm: android.location.LocationManager? = null
        if (hasLocationPermission) {
            runCatching {
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                lm = manager
                manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }
                    .maxByOrNull { it.time }?.let {
                        currentLocation = RoutePoint(it.latitude, it.longitude)
                        hasFix = true
                    }
                val provider = when {
                    manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) -> android.location.LocationManager.GPS_PROVIDER
                    manager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) -> android.location.LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    val l = android.location.LocationListener { loc ->
                        if (isRunning) {
                            distance += haversineMeters(currentLocation.lat, currentLocation.lng, loc.latitude, loc.longitude) / 1000.0
                        }
                        currentLocation = RoutePoint(loc.latitude, loc.longitude)
                        hasFix = true
                    }
                    listener = l
                    manager.requestLocationUpdates(provider, 2000L, 3f, l)
                }
            }
        }
        onDispose {
            listener?.let { l -> runCatching { lm?.removeUpdates(l) } }
        }
    }

    // 경과 시간 — 실제 이동 거리(distance)는 위 위치 리스너에서 갱신.
    LaunchedEffect(isRunning) {
        while (isRunning) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds += 1
        }
    }

    val paceLabel = remember(distance, elapsedSeconds) {
        if (distance < 0.01) "0'00\"" else {
            val paceSec = (elapsedSeconds / distance).toInt()
            "${paceSec / 60}'${(paceSec % 60).toString().padStart(2, '0')}\""
        }
    }
    val durationLabel = remember(elapsedSeconds) {
        val m = elapsedSeconds / 60; val s = elapsedSeconds % 60
        "%02d:%02d".format(m, s)
    }

    Box(modifier = Modifier.fillMaxSize().background(RunWhite)) {
        // 지도 영역 — start/finish 없이 currentLocation만 넘겨서 위치가 갱신될 때마다
        // 라벨만 이동시킨다(예전엔 currentLocation을 start/finish로도 같이 넘겨서 매번
        // 지도 전체를 다시 그리는 바람에 위치 라벨이 사라지고 다시 안 잡히는 버그가 있었음).
        if (hasLocationPermission) {
            KakaoRouteMap(
                modifier = Modifier.fillMaxSize(),
                routePoints = emptyList(),
                startPoint = null,
                finishPoint = null,
                currentLocation = currentLocation
            )
        } else {
            Box(Modifier.fillMaxSize().background(RunBgGray), contentAlignment = Alignment.Center) {
                Text("지도 표시를 위해 위치 권한이 필요합니다.", color = RunGray)
            }
        }

        // 상단 오버레이 (반투명 어둡게)
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
                Text(String.format("%.2f", distance), fontSize = 80.sp, fontWeight = FontWeight.Black, color = RunBlack)
                Text("Distance (Km)", fontSize = 14.sp, color = RunGray)
            }

            Spacer(Modifier.height(40.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem(paceLabel, "Avg Pace")
                MetricItem(durationLabel, "Duration")
                MetricItem("0 kcal", "Calories")
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    // 카메라는 위치가 갱신될 때마다 자동으로 현재 위치를 따라가므로,
                    // 권한이 없을 때만 재요청하면 된다.
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
                    modifier = Modifier.size(64.dp).clickable { distance = 0.0; elapsedSeconds = 0; isRunning = false },
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

// HistoryScreen / ClubScreen은 하단 탭이 4개(Home/Course/Run/Place)로 바뀌면서 제거됨.
// My/History 관련 화면(Figma "40 My/*")은 다음 라운드에서 Home 메뉴 등으로 재배치 예정.
