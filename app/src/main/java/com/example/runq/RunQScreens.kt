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
import androidx.compose.foundation.shape.CircleShape
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
// 홈 화면: "10 Home/Main.png" 기준 — 라임→라벤더 그라데이션 히어로 + 검색 + 거리칩 + Today's Run
// ════════════════════════════════════════════════════════
@Composable
fun HomeScreen(onFindCourses: () -> Unit) {
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    var distanceFilter by remember { mutableStateOf("전체") }
    var searchText by remember { mutableStateOf("") }
    val featured = remember { allCourses.maxByOrNull { it.rating } ?: allCourses.first() }

    LaunchedEffect(Unit) {
        try { safety = fetchSafety() } catch (e: Exception) { }
    }

    Column(modifier = Modifier.fillMaxSize().background(RunCream)) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    colors = listOf(RunLime.copy(alpha = 0.55f), RunCream, RunLavender.copy(alpha = 0.5f))
                )
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RunQ", fontSize = 20.sp, fontWeight = FontWeight.Black, color = RunBlack)
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(RunWhite.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "메뉴", tint = RunBlack, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "오늘은 어디로\n달려볼까요?", fontSize = 26.sp, fontWeight = FontWeight.Black,
                    color = RunBlack, lineHeight = 32.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(RunWhite).padding(horizontal = 16.dp, vertical = 14.dp),
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
                Text("거리로 찾기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("전체", "3km", "5km", "10km+").forEach { label ->
                        val selected = distanceFilter == label
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (selected) RunPurple else RunWhite)
                                .clickable { distanceFilter = label }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                label, fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) RunWhite else RunBlack
                            )
                        }
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
                    Text("오늘의 추천 코스", fontSize = 17.sp, fontWeight = FontWeight.Black, color = RunBlack)
                    Text(
                        "전체보기", fontSize = 13.sp, color = RunGray,
                        modifier = Modifier.clickable { onFindCourses() }
                    )
                }
                Spacer(Modifier.height(14.dp))
                TodaysRunCard(course = featured, safety = safety, onClick = onFindCourses)
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
                Brush.linearGradient(listOf(RunLime, RunLavender.copy(alpha = 0.8f)))
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("TODAY'S RUN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunBlack.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                Text(
                    "${course.location.removePrefix("강릉 ")}, ${course.distanceKm.lowercase()} 가볍게",
                    fontSize = 19.sp, fontWeight = FontWeight.Black, color = RunBlack, lineHeight = 24.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${safety?.temp ?: "--"} · 미세먼지 ${safety?.pm10 ?: "-"} · 예상 ${course.estimatedTime}",
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

// HistoryScreen / ClubScreen은 하단 탭이 4개(Home/Course/Run/Place)로 바뀌면서 제거됨.
// My/History 관련 화면(Figma "40 My/*")은 다음 라운드에서 Home 메뉴 등으로 재배치 예정.
