package com.example.runq

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

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
                            Text("Half Marathon event held by\nMandiri Bank Group", 
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
                items(listOf("Tangerang Runners", "JakBar Pacer", "Sunday Morning")) { spot ->
                    SpotCard(spot, onNavigateToClub)
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
fun SpotCard(name: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(width = 180.dp, height = 110.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RunBgGray)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RunBlack)
                Text("Indonesia", fontSize = 11.sp, color = RunGray)
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
fun RunningScreen() {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var distance by remember { mutableStateOf(0.0) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasLocationPermission = isGranted
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.7946, 128.9022), 15f) // 초기 강릉 경포호
    }

    // 시뮬레이션용 시간/거리 업데이트
    LaunchedEffect(isRunning) {
        while(isRunning) {
            kotlinx.coroutines.delay(1000)
            distance += 0.01 // 초당 10미터씩 증가 (가짜 데이터)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(RunWhite)) {
        // 지도 영역
        if (hasLocationPermission) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false)
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
                Text(String.format("%.2f", distance), fontSize = 80.sp, fontWeight = FontWeight.Black, color = RunBlack)
                Text("Distance (Km)", fontSize = 14.sp, color = RunGray)
            }

            Spacer(Modifier.height(40.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem("0'00\"", "Avg Pace")
                MetricItem("00.00", "Duration")
                MetricItem("0 kcal", "Calories")
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
                        else { 
                            // 현재 위치로 카메라 이동 (실제 GPS 연동 시 필요)
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
                    modifier = Modifier.size(64.dp).clickable { distance = 0.0; isRunning = false },
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

@Composable
fun HistoryScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("History Screen", fontWeight = FontWeight.Black, fontSize = 24.sp)
    }
}

@Composable
fun ClubScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Club Screen", fontWeight = FontWeight.Black, fontSize = 24.sp)
    }
}
