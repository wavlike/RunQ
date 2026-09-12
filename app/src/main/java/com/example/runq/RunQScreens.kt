package com.example.runq

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory

// ════════════════════════════════════════════════════════
// 강릉 러닝 크루 (홈 "Find The Spot Near You" / Club 탭에서 함께 사용)
// ════════════════════════════════════════════════════════
data class RunningClub(
    val name: String,
    val location: String,
    val memberCount: Int,
    val description: String
)

val runningClubs = listOf(
    RunningClub("강릉 러너스", "경포호", 128, "매주 토요일 아침 경포호를 도는 초보자 환영 크루예요."),
    RunningClub("경포 페이서", "경포해변", 76, "페이스별로 그룹을 나눠 함께 뛰는 바다 러닝 크루입니다."),
    RunningClub("주말 아침 크루", "안목해변", 54, "커피거리에서 마무리하는 여유로운 주말 러닝 모임이에요."),
    RunningClub("강문 나이트런", "강문해변", 41, "평일 저녁 야간 러닝을 즐기는 크루입니다.")
)

// ════════════════════════════════════════════════════════
// 홈 화면: 피그마 스타일 세련된 레이아웃
// ════════════════════════════════════════════════════════
@Composable
fun HomeScreen(onNavigateToClub: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    var selectedReviewCourse by remember { mutableStateOf<Course?>(null) }

    LaunchedEffect(Unit) {
        try { safety = fetchSafety() } catch (e: Exception) { }
    }

    if (selectedReviewCourse != null) {
        ReviewDetailDialog(course = selectedReviewCourse!!) { selectedReviewCourse = null }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RunWhite),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // 상단 헤더
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(28.dp)) 
                Image(
                    painter = painterResource(R.drawable.runq_logo), 
                    contentDescription = "Run Q", 
                    modifier = Modifier.height(30.dp)
                )
                Box {
                    Icon(
                        Icons.Default.MoreVert, 
                        contentDescription = "Settings", 
                        modifier = Modifier.size(28.dp).clickable { showMenu = true }
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(RunWhite)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Profile Edit", color = RunBlack) },
                            onClick = { showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings", color = RunBlack) },
                            onClick = { showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Dark Mode", color = RunBlack) },
                            onClick = { showMenu = false }
                        )
                    }
                }
            }
        }

        // 오늘의 날씨 브리핑
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Today's Environment", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RunBgGray)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        WeatherSmallItem("Temp", safety?.temp ?: "22°C")
                        WeatherSmallItem("Rain", safety?.rain ?: "None")
                        WeatherSmallItem("Dust", safety?.pm10 ?: "Good")
                        WeatherSmallItem("Fitness", safety?.fitness ?: "Good", isHighlight = true)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // 1. 메인 배너 (Running Information)
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Running Information", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().height(200.dp).clickable { /* 행사 정보 */ },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = RunBlack)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, RunBlack.copy(alpha = 0.6f)))
                        ))
                        Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                            Text("2026 강릉 경포호 마라톤 대회\n강릉 러닝크루 연합 주최",
                                color = RunWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                            Icon(Icons.Default.ChevronRight, null, tint = RunWhite, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // 2. Find The Spot Near You
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), 
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Find The Spot Near You", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("See all", color = RunGray, fontSize = 13.sp, modifier = Modifier.clickable { onNavigateToClub() })
            }
            Spacer(Modifier.height(16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(runningClubs.take(3)) { club ->
                    SpotCard(club.name, club.location, onNavigateToClub)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // 3. Running Course Review
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), 
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Running Course Review", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("See all", color = RunGray, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
        }

        items(allCourses.take(4)) { course ->
            HomeCourseCard(course) { selectedReviewCourse = course }
        }
    }
}

@Composable
fun ReviewDetailDialog(course: Course, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = RunBlack)) { Text("Close", color = RunWhite) } },
        title = { Text(text = "${course.name} Reviews", fontWeight = FontWeight.Black) },
        text = {
            Column {
                course.reviews.forEach { review ->
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.AccountCircle, null, tint = RunGray)
                        Spacer(Modifier.width(8.dp))
                        Text(review)
                    }
                }
            }
        },
        containerColor = RunWhite
    )
}

@Composable
fun WeatherSmallItem(label: String, value: String, isHighlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, color = if(isHighlight) RunPurple else RunBlack)
        Text(label, fontSize = 11.sp, color = RunGray)
    }
}

@Composable
fun SpotCard(name: String, location: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(width = 180.dp, height = 110.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RunBgGray)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RunBlack)
                Text(location, fontSize = 11.sp, color = RunGray)
            }
        }
    }
}

@Composable
fun HomeCourseCard(course: Course, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).height(150.dp).clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RunBlack)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(course.name.uppercase(), color = RunWhite, fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(RunWhite).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = RunLime, modifier = Modifier.size(12.dp))
                            Text(" ${course.rating}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(course.distanceKm, color = RunLime, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(course.reviews.firstOrNull() ?: "", color = RunWhite.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// 러닝 화면: 지도 및 GPS 실시간 연동
// ════════════════════════════════════════════════════════
@Composable
fun RunningScreen(runSession: RunSessionState) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var kakaoMapRef by remember { mutableStateOf<KakaoMap?>(null) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasLocationPermission = isGranted
    }

    // 실제 GPS 위치를 구독해서 이동 거리를 누적합니다 (러닝 중 + 권한이 있을 때만).
    // runSession이 탭 전환보다 위에서 유지되므로, 다른 탭에 갔다와도 진행 중인 러닝이
    // 초기화되지 않습니다.
    DisposableEffect(runSession.isRunning, hasLocationPermission) {
        if (!runSession.isRunning || !hasLocationPermission) {
            return@DisposableEffect onDispose {}
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location ->
            lastLocation?.let { prev ->
                val meters = prev.distanceTo(location)
                if (meters > 2f) { // GPS 오차로 정지 중에도 거리가 계속 늘어나는 걸 막기 위한 최소 이동거리 필터
                    runSession.distanceKm += meters / 1000.0
                }
            }
            lastLocation = location
            kakaoMapRef?.moveCamera(
                CameraUpdateFactory.newCenterPosition(LatLng.from(location.latitude, location.longitude))
            )
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                listener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // 권한이 막 취소된 경우 등 - 지도/거리 갱신만 멈추고 앱은 정상 동작
        }

        onDispose { locationManager.removeUpdates(listener) }
    }

    // 경과 시간(초) 카운트. 거리는 이제 위 GPS 콜백에서 실측으로 누적됩니다.
    LaunchedEffect(runSession.isRunning) {
        while (runSession.isRunning) {
            kotlinx.coroutines.delay(1000)
            runSession.elapsedSeconds += 1
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(RunWhite)) {
        // 지도 영역
        if (hasLocationPermission) {
            KakaoMapView(
                modifier = Modifier.fillMaxSize(),
                initialPosition = LatLng.from(37.7946, 128.9022), // 초기 강릉 경포호
                onMapReady = { kakaoMapRef = it }
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
                    Icon(Icons.Default.GpsFixed, null, tint = if(hasLocationPermission) RunPurple else RunGray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("GPS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(40.dp))

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format("%.2f", runSession.distanceKm), fontSize = 80.sp, fontWeight = FontWeight.Black, color = RunBlack)
                Text("Distance (Km)", fontSize = 14.sp, color = RunGray)
            }

            Spacer(Modifier.height(40.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem(formatPace(runSession.elapsedSeconds, runSession.distanceKm), "Avg Pace")
                MetricItem(formatDuration(runSession.elapsedSeconds), "Duration")
                MetricItem("${estimateCalories(runSession.distanceKm)} kcal", "Calories")
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
                            lastLocation?.let { loc ->
                                kakaoMapRef?.moveCamera(
                                    CameraUpdateFactory.newCenterPosition(LatLng.from(loc.latitude, loc.longitude))
                                )
                            }
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
                    onClick = { runSession.isRunning = !runSession.isRunning },
                    modifier = Modifier.height(64.dp).width(160.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if(!runSession.isRunning) RunLime else RunBlack, contentColor = if(!runSession.isRunning) RunBlack else RunWhite)
                ) {
                    Text(if(!runSession.isRunning) "START" else "PAUSE", fontWeight = FontWeight.Black, fontSize = 18.sp)
                }

                Surface(
                    modifier = Modifier.size(64.dp).clickable { runSession.finishRun() },
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

// ════════════════════════════════════════════════════════
// 카카오맵 SDK v2 래퍼 (Compose에서는 AndroidView로 감싸서 사용)
// ════════════════════════════════════════════════════════
@Composable
fun KakaoMapView(
    modifier: Modifier = Modifier,
    initialPosition: LatLng,
    initialZoomLevel: Int = 15,
    onMapReady: (KakaoMap) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

    // MapView는 Activity 생명주기에 맞춰 resume()/pause()를 직접 호출해줘야 합니다.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {}
                    override fun onMapError(error: Exception) {}
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(kakaoMap: KakaoMap) {
                        onMapReady(kakaoMap)
                    }
                    override fun getPosition(): LatLng = initialPosition
                    override fun getZoomLevel(): Int = initialZoomLevel
                }
            )
            mapView
        }
    )
}

@Composable
fun MetricItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, fontSize = 12.sp, color = RunGray)
    }
}

@Composable
fun HistoryScreen(history: List<RunRecord>) {
    Column(modifier = Modifier.fillMaxSize().background(RunWhite).padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Running History", fontSize = 28.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Text("지금까지의 러닝 기록이에요", fontSize = 14.sp, color = RunGray)
        Spacer(Modifier.height(20.dp))

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DirectionsRun, null, tint = RunGray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "아직 러닝 기록이 없어요.\nRun 탭에서 첫 러닝을 시작해보세요!",
                        color = RunGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history) { record -> HistoryCard(record) }
            }
        }
    }
}

@Composable
fun HistoryCard(record: RunRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RunBgGray)
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(record.dateLabel, fontSize = 13.sp, color = RunGray)
                Spacer(Modifier.height(4.dp))
                Text(String.format("%.2f km", record.distanceKm), fontSize = 22.sp, fontWeight = FontWeight.Black, color = RunBlack)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(record.durationSeconds), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunPurple)
                Text("페이스 ${formatPace(record.durationSeconds, record.distanceKm)}", fontSize = 12.sp, color = RunGray)
            }
        }
    }
}

@Composable
fun ClubScreen() {
    var joinedClubs by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxSize().background(RunWhite)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Running Clubs", fontSize = 28.sp, fontWeight = FontWeight.Black, color = RunBlack)
            Text("강릉의 러닝 크루를 만나보세요", fontSize = 14.sp, color = RunGray)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(runningClubs) { club ->
                val joined = joinedClubs.contains(club.name)
                ClubCard(
                    club = club,
                    joined = joined,
                    onToggleJoin = {
                        joinedClubs = if (joined) joinedClubs - club.name else joinedClubs + club.name
                    }
                )
            }
        }
    }
}

@Composable
fun ClubCard(club: RunningClub, joined: Boolean, onToggleJoin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RunBgGray)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(club.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            Text("${club.location} · 멤버 ${club.memberCount}명", fontSize = 12.sp, color = RunGray)
            Spacer(Modifier.height(8.dp))
            Text(club.description, fontSize = 13.sp, color = RunBlack)
            Spacer(Modifier.height(12.dp))
            if (joined) {
                OutlinedButton(
                    onClick = onToggleJoin,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, RunBlack),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RunBlack)
                ) { Text("가입됨", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = onToggleJoin,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
                ) { Text("가입하기", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
