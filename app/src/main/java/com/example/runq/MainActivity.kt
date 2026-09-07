package com.example.runq

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Star
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

// ────────────────────────────────────────────────
// 코스 데이터 모델은 FinishHub.kt(Course/FinishHub) + RunQData.kt(assets/data/*.json 로더)로
// 이전됨. 여기 있던 allCourses 하드코딩 9개 샘플은 삭제 — RunQData.courses를 사용할 것.
// NearbyPlace / fetchNearby는 FinishHubPlace / fetchFinishHubPlaces(FinishHubScreens.kt)로
// 대체됨. 완주 전 코스 상세 단계가 아니라 완주 후 Finish Hub 단계에서 조회한다.
// ────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Foundations(Figma)가 지정한 서체는 Noto Sans KR — 색상은 화면마다 RunQTheme.kt의
        // 값을 직접 지정해 쓰는 기존 방식을 유지하고, 여기선 전역 타이포그래피만 맞춘다.
        setContent { MaterialTheme(typography = com.example.runq.ui.theme.Typography) { RunQApp() } }
    }
}

// ════════════════════════════════════════════════════════
// 앱 최상위: 스플래시 → 랜딩 → 메인(탭)
// ════════════════════════════════════════════════════════
sealed class AppState {
    object Splash : AppState()
    object Auth : AppState()
    object Main : AppState()
}

@Composable
fun RunQApp() {
    var app by remember { mutableStateOf<AppState>(AppState.Splash) }

    Box(modifier = Modifier.fillMaxSize().background(RunWhite)) {
        when (app) {
            AppState.Splash -> SplashScreen(onDone = { app = AppState.Auth })
            AppState.Auth -> AuthFlow(onAuthSuccess = { app = AppState.Main })
            AppState.Main -> MainWithTabs(onLogout = { app = AppState.Auth })
        }
    }
}

// ════════════════════════════════════════════════════════
// 하단 탭바 (Home / Course / Run / Place / My) — Figma "Bottom Nav · Gradient Glow" 기준
// ════════════════════════════════════════════════════════
enum class Tab(val label: String) {
    HOME("Home"), COURSE("Course"), RUN("Run"), PLACE("Place"), MY("MY")
}

@Composable
fun MainWithTabs(onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    // Course 탭 안에서 Run Ready/Running/Complete·Finish Hub로 넘어가면 실제 화면 성격에
    // 맞춰 하단 탭 강조를 Run/Place로 넘겨준다 (디자인상 해당 화면들은 Run/Place가 켜져 있음).
    var courseSectionTab by remember { mutableStateOf(Tab.COURSE) }
    LaunchedEffect(tab) { if (tab == Tab.COURSE) courseSectionTab = Tab.COURSE }
    val highlightedTab = if (tab == Tab.COURSE) courseSectionTab else tab

    Column(modifier = Modifier.fillMaxSize().background(RunCream)) {
        // 화면 영역
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                Tab.HOME -> HomeFlow(onFindCourses = { tab = Tab.COURSE })
                Tab.COURSE -> CourseFlow(
                    onSectionHint = { courseSectionTab = it },
                    onOpenFinishHub = { course, category ->
                        PlaceTabRequest.request(course.finishHubIds.firstOrNull(), category)
                        tab = Tab.PLACE
                    }
                )
                Tab.RUN -> RunningScreen()
                Tab.PLACE -> PlaceFlow()
                Tab.MY -> MyFlow(onLogout = onLogout)
            }
        }
        BottomNavBar(selected = highlightedTab, onSelect = { tab = it })
    }
}

@Composable
fun BottomNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(RunCream)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RunBgGray))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab.entries.forEach { t -> NavTabItem(t, t == selected) { onSelect(t) } }
        }
    }
}

@Composable
fun NavTabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val icon = when (tab) {
        Tab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        Tab.COURSE -> if (selected) Icons.Filled.Route else Icons.Outlined.Route
        Tab.RUN -> if (selected) Icons.Filled.DirectionsRun else Icons.Outlined.DirectionsRun
        Tab.PLACE -> if (selected) Icons.Filled.Place else Icons.Outlined.Place
        Tab.MY -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp)
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    modifier = Modifier.size(40.dp).background(
                        Brush.radialGradient(listOf(RunLime.copy(alpha = 0.6f), RunLime.copy(alpha = 0f)))
                    )
                )
            }
            Icon(icon, contentDescription = tab.label, tint = if (selected) RunBlack else RunGray, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            tab.label, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
            color = if (selected) RunBlack else RunGray
        )
    }
}

// ════════════════════════════════════════════════════════
// 코스 탭 내부 흐름:
// 탐색(목록) → 상세 → Run Ready → Running → Complete
//   → (완주 후 EAT/CAFE/SEE는 별도 화면 대신 Place 탭으로 바로 이동)
// ════════════════════════════════════════════════════════
sealed class CourseStep {
    object Browse : CourseStep() // 전체 목록 보기 (필터/정렬 포함)
    data class Detail(val course: Course, val from: CourseStep) : CourseStep()
    data class RunReady(val course: Course, val from: CourseStep) : CourseStep()
    data class Running(val course: Course) : CourseStep()
    data class Complete(val course: Course, val distanceKm: Double, val elapsedSeconds: Int) : CourseStep()
}

// 화면 성격상 하단 탭에서 어디를 켜야 하는지 (Course/Run)
private fun CourseStep.sectionTab(): Tab = when (this) {
    is CourseStep.RunReady, is CourseStep.Running, is CourseStep.Complete -> Tab.RUN
    else -> Tab.COURSE
}

@Composable
fun CourseFlow(onSectionHint: (Tab) -> Unit = {}, onOpenFinishHub: (Course, PlaceCategory) -> Unit = { _, _ -> }) {
    var step by remember { mutableStateOf<CourseStep>(CourseStep.Browse) }
    LaunchedEffect(step) { onSectionHint(step.sectionTab()) }

    when (val s = step) {
        is CourseStep.Browse -> BrowseScreen(
            onCourseClick = { step = CourseStep.Detail(it, CourseStep.Browse) }
        )
        is CourseStep.Detail -> DetailScreen(
            course = s.course,
            onBack = { step = s.from },
            onStart = { step = CourseStep.RunReady(s.course, s) },
            onOpenCategory = { category -> onOpenFinishHub(s.course, category) }
        )
        is CourseStep.RunReady -> RunReadyScreen(
            course = s.course,
            onBack = { step = s.from },
            onStart = { step = CourseStep.Running(s.course) }
        )
        is CourseStep.Running -> CourseRunningScreen(
            course = s.course,
            onFinish = { dist, secs -> step = CourseStep.Complete(s.course, dist, secs) }
        )
        is CourseStep.Complete -> CompleteScreen(
            course = s.course,
            distanceKm = s.distanceKm,
            elapsedSeconds = s.elapsedSeconds,
            onCategoryClick = { category -> onOpenFinishHub(s.course, category) }
        )
    }
}

// Figma "20 Course / List" 기준: 헤더 + 지역/거리/난이도 드롭다운 필터 + 에디토리얼 리스트 카드
private val distanceBucketOptions = listOf("전체", "짧은코스", "5K", "중거리", "10K", "장거리")

@Composable
fun BrowseScreen(onCourseClick: (Course) -> Unit) {
    var regionFilter by remember { mutableStateOf("전체 지역") }
    var distanceFilter by remember { mutableStateOf("전체") }
    var difficultyFilter by remember { mutableStateOf("전체") }
    val regionOptions = remember {
        listOf("전체 지역") + RunQData.courses.map { it.region }.distinct().sorted()
    }

    val displayedCourses = remember(regionFilter, distanceFilter, difficultyFilter) {
        RunQData.courses.filter { it.status != ContentStatus.HIDDEN }.filter { c ->
            (regionFilter == "전체 지역" || c.region == regionFilter) &&
                (distanceFilter == "전체" || c.matchesDistanceBucket(distanceFilter)) &&
                (difficultyFilter == "전체" || c.difficulty.label == difficultyFilter)
        }.sortedByDescending { it.rating }
    }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("모든 코스 보기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(8.dp))
        Text("거리와 분위기로 내 러닝 코스를 골라보세요.", fontSize = 13.sp, color = RunGray)

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdownChip("$regionFilter ▾", regionOptions) { regionFilter = it }
            FilterDropdownChip(if (distanceFilter == "전체") "거리 ▾" else "$distanceFilter ▾", distanceBucketOptions) { distanceFilter = it }
            FilterDropdownChip(if (difficultyFilter == "전체") "난이도 ▾" else "$difficultyFilter ▾", listOf("전체") + Difficulty.entries.filter { it != Difficulty.UNKNOWN }.map { it.label }) { difficultyFilter = it }
        }

        Spacer(Modifier.height(18.dp))

        Text("추천순 · ${displayedCourses.size}개 코스", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)

        Spacer(Modifier.height(12.dp))

        if (displayedCourses.isEmpty()) {
            EmptyStateView("🔍", "조건에 맞는 코스가 없어요", "필터를 바꿔서 다시 찾아보세요.", Modifier.padding(top = 24.dp))
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(displayedCourses) { index, course ->
                    CourseListRow(course, index) { onCourseClick(course) }
                }
                item { Text("아래로 스크롤해 더 많은 코스 보기", fontSize = 11.sp, color = RunGray, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun FilterDropdownChip(label: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(17.dp))
                .background(RunWhite)
                .border(1.dp, RunBorderGray, RoundedCornerShape(17.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = RunBlack)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

// 코스별 컬러 썸네일 팔레트 — Figma 카드 디자인의 장식용 배경색(콘텐츠 데이터 아님, 인덱스로 순환)
private val courseThumbnailColors = listOf(Color(0xFFDEEBED), Color(0xFFF0E5D1), Color(0xFFE3EBE0), Color(0xFFEBE5DB))

@Composable
fun CourseListRow(course: Course, index: Int = 0, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RunWhite),
        border = BorderStroke(1.dp, RunBorderGray)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CourseThumbnail(
                label = course.sceneryLabel(),
                color = courseThumbnailColors[index % courseThumbnailColors.size]
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(course.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    if (course.status == ContentStatus.DRAFT) {
                        Spacer(Modifier.width(6.dp))
                        DraftBadge()
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(course.locationLabel(), fontSize = 11.sp, color = RunGray)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${course.distanceLabel()} · ${course.timeLabel()} · ${course.difficulty.label}",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = RunBlack
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ListTag(course.sceneryLabel())
                    ListTag(course.terrain.label)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = RunGray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ListTag(text: String) {
    Box(modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(RunBgGray).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = RunGray)
    }
}

// Figma의 "Route Accent"(작은 루프 경로 아이콘) + "Finish Dot"을 Canvas로 근사한 코스 썸네일.
// 실제 코스 사진(cover_image_url)이 채워지기 전까지 쓰는 장식용 자리표시.
@Composable
private fun CourseThumbnail(label: String, color: Color) {
    Box(
        modifier = Modifier.size(width = 92.dp, height = 88.dp).clip(RoundedCornerShape(16.dp)).background(color),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(52.dp, 34.dp)) {
            val strokeWidth = 2.dp.toPx()
            drawOval(
                color = RunBlack.copy(alpha = 0.55f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height),
                style = Stroke(width = strokeWidth)
            )
            drawCircle(
                color = RunBlack.copy(alpha = 0.7f),
                radius = strokeWidth * 1.6f,
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height)
            )
        }
        Box(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                .clip(RoundedCornerShape(9.dp)).background(RunWhite.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RunGray)
        }
    }
}


// ══════════════════════════════════════════════════
// 스플래시: 로고 없이 배경만
// ══════════════════════════════════════════════════
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(500) // 아주 짧게 대기 후 랜딩으로
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize().background(RunCream)) {
        // 좌상단 라임 / 우하단 라벤더 radial glow 블롭 (Splash/Brand.png 기준)
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopStart)
                .offset((-80).dp, (-60).dp)
                .background(
                    Brush.radialGradient(listOf(RunLime.copy(alpha = 0.55f), Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(90.dp, 90.dp)
                .background(
                    Brush.radialGradient(listOf(RunLavender.copy(alpha = 0.55f), Color.Transparent)),
                    shape = CircleShape
                )
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.runq_logo),
                contentDescription = "RunQ 로고",
                modifier = Modifier.size(180.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Find your route.", fontSize = 15.sp, color = RunGray)
        }
    }
}

// 팀 콘텐츠가 아직 확정되지 않은(status=DRAFT) 코스를 개발 빌드에서 숨기지 않고
// 작은 배지로 구분 표시하기로 함(콘텐츠 미확정 상태를 사용자에게도 투명하게 노출).
@Composable
fun DraftBadge() {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(RunGray.copy(alpha = 0.25f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("DRAFT", fontSize = 10.sp, color = RunGray, fontWeight = FontWeight.Black)
    }
}

// ══════════════════════════════════════════════════
// 코스: 상세 — "21 Course/Detail" 매거진 카드 기준
// (★ API 호출 — 안전정보. EAT/CAFE/SEE 실제 조회는 Place 화면에서)
// ══════════════════════════════════════════════════
@Composable
fun DetailScreen(
    course: Course,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onOpenCategory: (PlaceCategory) -> Unit
) {
    var safety by remember { mutableStateOf<SafetyInfo?>(null) }
    var saved by remember { mutableStateOf(SavedItemsStore.isCourseSaved(course.id)) }
    val hub = remember(course) { course.finishHubIds.firstOrNull()?.let { findHub(it) } }

    LaunchedEffect(course.name) {
        try { safety = fetchSafety(course.weatherGrid()) }
        catch (e: Exception) { safety = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RunCream)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ISSUE 01 · ${course.locationLabel().removePrefix("강릉 ").ifBlank { "GANGNEUNG" }}".uppercase(),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunGray
                )
                if (course.status == ContentStatus.DRAFT) {
                    Spacer(Modifier.width(6.dp))
                    DraftBadge()
                }
            }
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(RunWhite).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기", tint = RunBlack, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        CourseCoverPlaceholder(course)
        Spacer(Modifier.height(24.dp))

        Text("WHY THIS RUN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunPurple)
        Spacer(Modifier.height(8.dp))
        Text(course.reasonText(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = RunBlack, lineHeight = 27.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "${course.locationLabel()}을 따라 이어지는 ${course.distanceLabel()} 루트를 중심으로, " +
                "러닝 뒤 가볍게 들를 수 있는 카페·식사·관광 동선을 함께 묶은 RunQ 큐레이션이에요.",
            fontSize = 13.sp, color = RunGray, lineHeight = 19.sp
        )
        Spacer(Modifier.height(24.dp))

        CourseInfoCard(course)
        Spacer(Modifier.height(24.dp))

        val s = safety
        if (s != null) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = RunWhite)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SafetyMetric("기온", s.temp)
                    SafetyMetric("미세먼지", s.pm10)
                    SafetyMetric("러닝 적합도", s.fitness)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Text("RUN → EAT → CAFE → SEE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunPurple)
        Spacer(Modifier.height(8.dp))
        Text(
            "달린 뒤까지 이어지는 ${course.locationLabel().removePrefix("강릉 ")} 코스",
            fontSize = 19.sp, fontWeight = FontWeight.Black, color = RunBlack, lineHeight = 25.sp
        )
        Spacer(Modifier.height(14.dp))
        if (hub == null) {
            Box(modifier = Modifier.fillMaxWidth().background(RunWhite, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Text("이 코스는 아직 Finish Hub가 지정되지 않았어요.", fontSize = 13.sp, color = RunGray)
            }
        } else {
            val eatPick = RunQData.places.firstOrNull { it.finishHubId == hub.id && it.category == PlaceCategory.EAT }
            val cafePick = RunQData.places.firstOrNull { it.finishHubId == hub.id && it.category == PlaceCategory.CAFE }
            val seePick = RunQData.places.firstOrNull { it.finishHubId == hub.id && it.category == PlaceCategory.SEE }
            HubCategoryRow("01", "EAT", RunLime, eatPick?.let { "${it.title} 등에서 가볍게 한 끼" } ?: "") { onOpenCategory(PlaceCategory.EAT) }
            HubCategoryRow("02", "CAFE", RunPurple, cafePick?.let { "${it.title} 등에서 잠깐 쉬기" } ?: "") { onOpenCategory(PlaceCategory.CAFE) }
            HubCategoryRow("03", "SEE", RunLavender, seePick?.let { "${it.title} 주변을 천천히 둘러보기" } ?: "") { onOpenCategory(PlaceCategory.SEE) }
        }
        Spacer(Modifier.height(24.dp))

        CourseMapCard(course = course, title = course.name, heightDp = 220)
        Spacer(Modifier.height(24.dp))

        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)) {
            Text("이 코스로 달리기", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { SavedItemsStore.toggleCourse(course.id); saved = !saved }, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.5.dp, RunBlack),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RunBlack)) {
            Text(if (saved) "코스 저장됨 ✓" else "코스 저장하기", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// Figma "COURSE INFO" 카드: 코스명/위치·거리/예상 소요 시간·경관 유형/교통량을 카드형 key-value로 표시.
@Composable
fun CourseInfoCard(course: Course) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF5F5F2))
            .border(1.dp, Color(0xFFDED6C9), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Text("COURSE INFO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunPurple)
            Spacer(Modifier.height(12.dp))
            CourseInfoLine("코스명", course.name)
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                CourseInfoLine("위치", course.locationLabel(), Modifier.weight(1f))
                CourseInfoLine("거리", course.distanceLabel(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                CourseInfoLine("예상 소요 시간", course.timeLabel(), Modifier.weight(1f))
                CourseInfoLine("경관 유형", course.sceneryLabel(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Text("교통량", fontSize = 9.sp, color = Color(0xFF7B746A))
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1F6EC)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(course.trafficLevel.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF51664B))
            }
        }
    }
}

@Composable
fun CourseInfoLine(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 9.sp, color = Color(0xFF7B746A))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RunBlack)
    }
}

// Figma "Course Cover / DB Template": 실제 사진(cover_image_url) 위에 하단 어둡게 오버레이 +
// RUN 태그칩 + 2줄 헤드라인 + 코스명 서브카피 + 거리·시간·지형 메타라인 + RUNQ PICKS.
// 실제 사진이 아직 없어서(cover_image_url 미확정) 사진 자리는 그라데이션으로 대체.
@Composable
fun CourseCoverPlaceholder(course: Course) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.82f).clip(RoundedCornerShape(20.dp))
            .background(RunBlack)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(RunPurple.copy(alpha = 0.55f), RunBlack),
                    radius = 900f
                )
            )
        )
        Box(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 24.dp, start = 20.dp)
                .clip(RoundedCornerShape(2.dp)).background(RunLime).padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("RUN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(
                course.reasonText().let { it.substringBefore('\n').ifBlank { course.name } },
                color = RunWhite, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 31.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(course.name, color = RunLime, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${course.distanceLabel()} · ${course.timeLabel()} · ${course.terrain.label}",
                color = RunWhite.copy(alpha = 0.75f), fontSize = 9.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("RUNQ PICKS", color = RunWhite.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 8.sp)
        }
    }
}

@Composable
fun HubCategoryRow(no: String, label: String, accent: Color, blurb: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("$no  $label", fontSize = 15.sp, fontWeight = FontWeight.Black, color = RunBlack)
                if (blurb.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(blurb, fontSize = 12.sp, color = RunGray)
                }
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = RunGray, modifier = Modifier.size(18.dp))
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RunBgGray))
}

// 안전정보 지표 한 개 (기온/강수/미세먼지)
@Composable
fun SafetyMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Text(label, fontSize = 12.sp, color = RunGray)
    }
}

