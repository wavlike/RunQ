package com.example.runq

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.IconButton
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FilterList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
// 코스 데이터 모델
// ────────────────────────────────────────────────

// 지도 위 RouteLine에 쓰는 좌표 하나.
// ⚠️ 아래 allCourses의 routePoints는 실측 GPS 트랙이 아니라, 코스 설명에 나오는
// 지명(해변/공원 등)을 랜드마크 삼아 순서대로 이은 "근사 경로"다. 실제 보도/산책로
// 곡선을 따라가지 않으니 인기 코스부터 실측 데이터로 교체할 것.
data class RoutePoint(val lat: Double, val lng: Double)

data class Course(
    val name: String, val location: String, val distance: String, val distanceKm: String,
    val estimatedTime: String, val scenery: String, val difficulty: String,
    val reason: String, val nearby: String, val tags: List<String>,
    val lat: Double, val lng: Double,
    val rating: Double = 4.5,
    val reviews: List<String> = listOf("코스가 정말 예뻐요!", "초보자도 뛰기 좋습니다.", "경치가 끝내줍니다."),
    // ── Finish Hub 연동 ──
    // finishHubIds는 FinishHub.kt의 FinishHub.id를 참조하는 FK다. 반경/좌표/큐레이션
    // 리스트는 Hub 쪽에 한 번만 저장하고 여기서는 id만 들고 있는 정규화된 구조를 쓴다.
    // (두 Hub 경계에 걸치는 코스는 복수 id를 넣을 수 있음 — 이땐 첫 번째를 기본 Hub로 사용)
    val finishHubIds: List<String> = emptyList(),
    // 시작/종료 핀 좌표. 지금은 실측 좌표가 없어 lat/lng를 기본값으로 쓴다 —
    // Finish Hub 추천 로직에는 필요 없고, 지도에 START/FINISH 마커를 찍을 때만 필요하다.
    val startLat: Double = lat,
    val startLng: Double = lng,
    val finishLat: Double = lat,
    val finishLng: Double = lng,
    // 지도 RouteLine용 경로 좌표. 비어있으면 start→finish 직선으로 대체.
    val routePoints: List<RoutePoint> = emptyList()
)

val allCourses = listOf(
    Course("경포호 기본런", "강릉 경포호 둘레길", "약 4.3~5km", "5KM", "약 30~40분", "호수", "쉬움",
        "평지 위주라 초보 러너가 부담 없이 완주하기 좋아요.", "경포해변 카페거리, 허균·허난설헌 기념공원",
        listOf("호수", "짧은코스", "쉬움", "초보추천"), 37.7955, 128.8962, 4.8,
        listOf("호수 따라 뛰니 힐링되네요.", "평지라 무릎 부담이 적어요.", "강릉 오면 꼭 뛰어야 하는 코스!"),
        finishHubIds = listOf("A"),
        routePoints = listOf(
            RoutePoint(37.7930, 128.8865), RoutePoint(37.7965, 128.8870),
            RoutePoint(37.8000, 128.8910), RoutePoint(37.7995, 128.8985),
            RoutePoint(37.7965, 128.9040), RoutePoint(37.7930, 128.9010),
            RoutePoint(37.7905, 128.8940), RoutePoint(37.7930, 128.8865)
        )),
    Course("안목해변 커피거리 왕복런", "강릉 안목해변", "약 4.3~5km", "5KM", "약 30~40분", "바다", "쉬움",
        "바다를 끼고 달리다 커피거리에서 마무리하기 좋은 코스예요.", "안목 커피거리, 강문해변",
        listOf("바다", "짧은코스", "쉬움", "사진명소"), 37.7713, 128.9470, 4.7,
        listOf("커피 향 맡으며 뛰니까 기분 최고!", "바다 바람이 시원해요.", "코스가 짧아서 부담 없어요."),
        finishHubIds = listOf("B"),
        routePoints = listOf(
            RoutePoint(37.7713, 128.9470), RoutePoint(37.7760, 128.9455),
            RoutePoint(37.7810, 128.9430), RoutePoint(37.7760, 128.9455),
            RoutePoint(37.7713, 128.9470)
        )),
    Course("강문해변 짧은 해송런", "강릉 강문해변", "약 3~5km", "4KM", "약 20~35분", "바다", "쉬움",
        "해송길과 해변을 오가는 짧고 편안한 힐링 코스예요.", "강문해변, 송정해변",
        listOf("바다", "짧은코스", "쉬움", "힐링"), 37.7936, 128.9163, 4.5,
        finishHubIds = listOf("A"),
        routePoints = listOf(
            RoutePoint(37.7936, 128.9163), RoutePoint(37.7900, 128.9150),
            RoutePoint(37.7870, 128.9140), RoutePoint(37.7900, 128.9150),
            RoutePoint(37.7936, 128.9163)
        )),
    Course("안목→강문→경포 바다런", "강릉 안목~경포", "약 5~7km", "6KM", "약 40~55분", "바다", "보통",
        "강릉 대표 바다 코스를 한 번에 이어 뛸 수 있어요.", "안목 커피거리, 경포해변",
        listOf("바다", "중거리", "보통", "관광연계"), 37.7825, 128.9310, 4.9,
        listOf("강릉 바다 정복 완료!", "경치가 너무 예뻐서 멈추게 되네요.", "생각보다 길지만 보람차요."),
        finishHubIds = listOf("B"),
        routePoints = listOf(
            RoutePoint(37.7713, 128.9470), RoutePoint(37.7820, 128.9300),
            RoutePoint(37.7936, 128.9163), RoutePoint(37.8000, 128.9080)
        )),
    Course("경포호 10K", "강릉 경포호", "약 10km", "10KM", "약 60~75분", "호수", "보통",
        "경포호 2바퀴로 거리를 채우는 챌린지형 코스예요.", "경포대, 경포해변",
        listOf("호수", "10K", "보통", "챌린지"), 37.7955, 128.8962, 4.6,
        finishHubIds = listOf("A"),
        // 경포호 둘레길 2바퀴 — 1바퀴분 좌표만 저장하고 지도에서 2회 순회로 표시
        routePoints = listOf(
            RoutePoint(37.7930, 128.8865), RoutePoint(37.7965, 128.8870),
            RoutePoint(37.8000, 128.8910), RoutePoint(37.7995, 128.8985),
            RoutePoint(37.7965, 128.9040), RoutePoint(37.7930, 128.9010),
            RoutePoint(37.7905, 128.8940), RoutePoint(37.7930, 128.8865)
        )),
    Course("남대천→안목해변 5K", "강릉 남대천~안목", "약 5km", "5KM", "약 35~45분", "강변", "쉬움",
        "도심 강변에서 바다로 빠지는 흐름이 좋은 코스예요.", "월화거리, 안목 커피거리",
        listOf("강변", "5K", "쉬움", "카페연계"), 37.7590, 128.9080, 4.4,
        finishHubIds = listOf("B"),
        routePoints = listOf(
            RoutePoint(37.7519, 128.8971), RoutePoint(37.7580, 128.9150),
            RoutePoint(37.7650, 128.9320), RoutePoint(37.7713, 128.9470)
        )),
    Course("오죽헌→선교장→경포호", "강릉 오죽헌 일대", "약 5~7km", "6KM", "약 40~55분", "문화", "보통",
        "문화유산을 지나며 달리는 관광 연계 코스예요.", "오죽헌, 선교장",
        listOf("문화", "중거리", "보통", "관광연계"), 37.7792, 128.8784, 4.3,
        finishHubIds = listOf("A", "C"),
        routePoints = listOf(
            RoutePoint(37.7792, 128.8784), RoutePoint(37.7850, 128.8830),
            RoutePoint(37.7930, 128.8870), RoutePoint(37.7955, 128.8962)
        )),
    Course("경포생태저류지 메타세쿼이아길", "강릉 경포생태저류지", "약 2.5~4km", "3KM", "약 20~30분", "숲길", "쉬움",
        "조용한 숲길에서 산책하듯 달리는 힐링 코스예요.", "경포호, 가시연습지",
        listOf("숲길", "짧은코스", "쉬움", "힐링"), 37.8010, 128.9010, 4.8,
        finishHubIds = listOf("A", "C"),
        routePoints = listOf(
            RoutePoint(37.8010, 128.9010), RoutePoint(37.8000, 128.8970),
            RoutePoint(37.7985, 128.8940), RoutePoint(37.8010, 128.9010)
        )),
    Course("옥계 헌화로 11K", "강릉 옥계 헌화로", "약 11km", "11KM", "약 70~90분", "바다", "어려움",
        "해안 절경을 따라 달리는 상급자·대회형 코스예요.", "헌화로 해안도로, 옥계해변",
        listOf("바다", "장거리", "어려움", "챌린지"), 37.6512, 129.0355, 4.2,
        finishHubIds = listOf("H"),
        routePoints = listOf(
            RoutePoint(37.6512, 129.0355), RoutePoint(37.6480, 129.0300),
            RoutePoint(37.6420, 129.0230), RoutePoint(37.6350, 129.0150)
        ))
)

// NearbyPlace / fetchNearby는 FinishHubPlace / fetchFinishHubPlaces(FinishHubScreens.kt)로
// 대체됨. 완주 전 코스 상세 단계가 아니라 완주 후 Finish Hub 단계에서 조회한다.

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { RunQApp() } }
    }
}

// ════════════════════════════════════════════════════════
// 앱 최상위: 스플래시 → 랜딩 → 메인(탭)
// ════════════════════════════════════════════════════════
sealed class AppState {
    object Splash : AppState()
    object Landing : AppState()
    object Login : AppState()    // 추가
    object Main : AppState()
}

@Composable
fun RunQApp() {
    var app by remember { mutableStateOf<AppState>(AppState.Splash) }

    Box(modifier = Modifier.fillMaxSize().background(RunWhite)) {
        when (app) {
            AppState.Splash -> SplashScreen(onDone = { app = AppState.Landing })
            AppState.Landing -> LandingScreen(
                onLoginClick = { app = AppState.Login },
                onJoinUsClick = { /* 회원가입 이동 로직 */ }
            )
            AppState.Login -> LoginScreen(onLoginSuccess = { app = AppState.Main })
            AppState.Main -> MainWithTabs()
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RunWhite)
            .padding(32.dp)
    ) {
        // 상단 뒤로가기 버튼 스타일
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(RunBgGray)
                .clickable { /* 뒤로가기 로직 필요시 추가 */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RunBlack, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(40.dp))

        // 피그마 타이틀 스타일
        Text(
            text = "Welcome\nrunners !",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 48.sp,
            color = RunBlack
        )

        Spacer(Modifier.height(48.dp))

        // 입력 필드: Username
        LoginTextField(
            value = username,
            onValueChange = { username = it },
            label = "Username",
            icon = Icons.Default.Person
        )

        Spacer(Modifier.height(16.dp))

        // 입력 필드: Password
        LoginTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            icon = Icons.Default.Lock,
            isPassword = true
        )

        Spacer(Modifier.height(40.dp))

        // Log In 버튼 (피그마 라임 버튼)
        Button(
            onClick = onLoginSuccess,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
        ) {
            Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.weight(1f))

        // 소셜 로그인 섹션
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("sign up with", fontSize = 13.sp, color = RunGray)
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialIcon(Icons.Default.Translate) // 구글/번역 대용
                Spacer(Modifier.width(24.dp))
                SocialIcon(Icons.Default.AccountCircle) // 애플/계정 대용
                Spacer(Modifier.width(24.dp))
                SocialIcon(Icons.Default.Face) // 페이스북/얼굴 대용
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = RunGray, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = RunGray, fontSize = 14.sp)
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = RunBlack,
                unfocusedIndicatorColor = RunBgGray
            ),
            singleLine = true
        )
    }
}

@Composable
fun SocialIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, RunBgGray, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

// ════════════════════════════════════════════════════════
// 하단 탭바 (Home / Course / Run / Place) — Figma "Bottom Nav · Gradient Glow" 기준
// ════════════════════════════════════════════════════════
enum class Tab(val label: String) {
    HOME("Home"), COURSE("Course"), RUN("Run"), PLACE("Place")
}

@Composable
fun MainWithTabs() {
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
                Tab.HOME -> HomeScreen(onFindCourses = { tab = Tab.COURSE })
                Tab.COURSE -> CourseFlow(onSectionHint = { courseSectionTab = it })
                Tab.RUN -> RunningScreen()
                Tab.PLACE -> PlaceFlow()
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
            Tab.values().forEach { t -> NavTabItem(t, t == selected) { onSelect(t) } }
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
// 탐색(목록) → 조건 → 결과 → 상세 → Run Ready → Running → Complete
//   → Finish Hub(EAT/CAFE/SEE) → Place Detail
// ════════════════════════════════════════════════════════
sealed class CourseStep {
    object Browse : CourseStep() // 전체 목록 보기 (필터/정렬 포함)
    object Condition : CourseStep() // 맞춤 추천 조건 선택
    data class Result(val courses: List<Course>) : CourseStep()
    data class Detail(val course: Course, val from: CourseStep) : CourseStep()
    data class RunReady(val course: Course, val from: CourseStep) : CourseStep()
    data class Running(val course: Course) : CourseStep()
    data class Complete(val course: Course, val distanceKm: Double, val elapsedSeconds: Int) : CourseStep()
    data class FinishHubStep(val course: Course, val initialCategory: PlaceCategory = PlaceCategory.EAT) : CourseStep()
    data class PlaceDetailStep(val course: Course, val place: FinishHubPlace) : CourseStep()
}

// 화면 성격상 하단 탭에서 어디를 켜야 하는지 (Course/Run/Place)
private fun CourseStep.sectionTab(): Tab = when (this) {
    is CourseStep.RunReady, is CourseStep.Running, is CourseStep.Complete -> Tab.RUN
    is CourseStep.FinishHubStep, is CourseStep.PlaceDetailStep -> Tab.PLACE
    else -> Tab.COURSE
}

@Composable
fun CourseFlow(onSectionHint: (Tab) -> Unit = {}) {
    var step by remember { mutableStateOf<CourseStep>(CourseStep.Browse) }
    LaunchedEffect(step) { onSectionHint(step.sectionTab()) }

    when (val s = step) {
        is CourseStep.Browse -> BrowseScreen(
            onCourseClick = { step = CourseStep.Detail(it, CourseStep.Browse) },
            onNavigateToRecommend = { step = CourseStep.Condition }
        )
        is CourseStep.Condition -> ConditionScreen(
            onBack = { step = CourseStep.Browse },
            onRecommend = { sc, di, df -> step = CourseStep.Result(filterCourses(sc, di, df)) }
        )
        is CourseStep.Result -> ResultScreen(
            courses = s.courses,
            onCourseClick = { step = CourseStep.Detail(it, s) },
            onBack = { step = CourseStep.Condition }
        )
        is CourseStep.Detail -> DetailScreen(
            course = s.course,
            onBack = { step = s.from },
            onStart = { step = CourseStep.RunReady(s.course, s) },
            onOpenCategory = { category -> step = CourseStep.FinishHubStep(s.course, category) }
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
            onCategoryClick = { category -> step = CourseStep.FinishHubStep(s.course, category) }
        )
        is CourseStep.FinishHubStep -> {
            val hub = s.course.finishHubIds.firstOrNull()?.let { findHub(it) }
            HubPlacesScreen(
                hub = hub,
                contextLabel = "러닝 후 · ${hub?.name ?: s.course.name}",
                initialCategory = s.initialCategory,
                onBack = { step = CourseStep.Browse },
                onPlaceClick = { place -> step = CourseStep.PlaceDetailStep(s.course, place) }
            )
        }
        is CourseStep.PlaceDetailStep -> PlaceDetailScreen(
            place = s.place,
            onBack = { step = CourseStep.FinishHubStep(s.course) }
        )
    }
}

private fun Course.distanceKmValue(): Double =
    distanceKm.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0

// "20 Course/List.png" 기준: 헤더 + 거리 필터칩 + 화살표 리스트 카드
@Composable
fun BrowseScreen(onCourseClick: (Course) -> Unit, onNavigateToRecommend: () -> Unit) {
    var distanceFilter by remember { mutableStateOf("전체") }

    val displayedCourses = remember(distanceFilter) {
        allCourses.filter { c ->
            when (distanceFilter) {
                "3km" -> c.distanceKmValue() <= 3.5
                "5km" -> c.distanceKmValue() in 3.5..7.0
                "10km+" -> c.distanceKmValue() > 7.0
                else -> true
            }
        }.sortedByDescending { it.rating }
    }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("러닝 코스", fontSize = 26.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Text("거리와 분위기로 찾아보세요", fontSize = 14.sp, color = RunGray)

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("전체", "3km", "5km", "10km+").forEach { label ->
                val selected = distanceFilter == label
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (selected) RunPurple else RunBgGray)
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

        Spacer(Modifier.height(14.dp))

        // 맞춤 조건 추천(Condition/Result 플로우)은 Figma엔 없지만 기존 기능이라 톤만 맞춰 유지
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToRecommend() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("조건으로 맞춤 추천 받기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunPurple)
            Icon(Icons.Default.ChevronRight, null, tint = RunPurple, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(12.dp))

        if (displayedCourses.isEmpty()) {
            Text("조건에 맞는 코스가 없어요.", color = RunGray, modifier = Modifier.padding(top = 24.dp))
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(displayedCourses) { course ->
                    CourseListRow(course) { onCourseClick(course) }
                }
            }
        }
    }
}

@Composable
fun CourseListRow(course: Course, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RunWhite)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(course.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${course.distanceKm.lowercase()} · ${course.scenery} · ${course.difficulty}",
                    fontSize = 13.sp, color = RunGray
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = RunGray, modifier = Modifier.size(20.dp))
        }
    }
}


fun filterCourses(scenery: String, distance: String, difficulty: String): List<Course> {
    return allCourses.filter { c ->
        (scenery == "상관없음" || c.scenery == scenery) &&
                (difficulty == "상관없음" || c.difficulty == difficulty) &&
                (distance == "상관없음" || c.tags.contains(distance))
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

@Composable
fun LandingScreen(onLoginClick: () -> Unit, onJoinUsClick: () -> Unit) {
    var showButtons by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, animationSpec = tween(1200)) }
        scale.animateTo(1.3f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
        delay(400)
        scale.animateTo(1.0f, animationSpec = tween(600))
        delay(500)
        showButtons = true
    }

    val handleExit = { nextAction: () -> Unit ->
        isExiting = true
        scope.launch {
            scale.animateTo(1.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            launch { alpha.animateTo(0f, animationSpec = tween(400)) }
            scale.animateTo(0f, animationSpec = tween(400))
            nextAction()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(RunWhite)) {
        if (!isExiting) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.runq_logo),
                    contentDescription = "RunQ 로고",
                    modifier = Modifier
                        .size(240.dp)
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            alpha = alpha.value
                        )
                )
                
                if (!showButtons) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Your Run, Curated", 
                        color = RunBlack,
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.graphicsLayer(alpha = alpha.value)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showButtons && !isExiting,
            enter = fadeIn(tween(800)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
        ) {
            Column {
                Button(
                    onClick = { handleExit(onJoinUsClick) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
                ) {
                    Text("Join Us", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { handleExit(onLoginClick) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, RunLime),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RunLime)
                ) {
                    Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════
// 코스: 조건 선택
// ══════════════════════════════════════════════════
@Composable
fun ConditionScreen(onBack: () -> Unit, onRecommend: (String, String, String) -> Unit) {
    var scenery by remember { mutableStateOf("상관없음") }
    var distance by remember { mutableStateOf("상관없음") }
    var difficulty by remember { mutableStateOf("상관없음") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        }
        Spacer(Modifier.height(16.dp))
        Text("Your Preference", fontSize = 28.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Text("당신에게 딱 맞는 러닝 코스를 큐레이션해드려요", fontSize = 14.sp, color = RunGray)
        Spacer(Modifier.height(28.dp))
        OptionRow("경관", listOf("상관없음", "바다", "호수", "강변", "문화", "숲길"), scenery) { scenery = it }
        Spacer(Modifier.height(20.dp))
        OptionRow("거리", listOf("상관없음", "짧은코스", "5K", "중거리", "10K", "장거리"), distance) { distance = it }
        Spacer(Modifier.height(20.dp))
        OptionRow("난이도", listOf("상관없음", "쉬움", "보통", "어려움"), difficulty) { difficulty = it }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onRecommend(scenery, distance, difficulty) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
        ) { Text("추천 코스 받기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun OptionRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RunBlack)
        Spacer(Modifier.height(12.dp))
        options.chunked(3).forEach { rowOptions ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowOptions.forEach { option ->
                    val isSel = option == selected
                    Box(
                        modifier = Modifier.padding(end = 10.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp)) // 더 현대적인 라운딩
                            .background(if (isSel) RunBlack else RunBgGray)
                            .clickable { onSelect(option) }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(option, fontSize = 14.sp,
                            color = if (isSel) RunWhite else RunBlack,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════
// 코스: 추천 결과
// ══════════════════════════════════════════════════
@Composable
fun ResultScreen(courses: List<Course>, onCourseClick: (Course) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("추천 코스", fontSize = 26.sp, fontWeight = FontWeight.Black, color = RunBlack)
        Text("${courses.size}개의 코스를 찾았어요", fontSize = 14.sp, color = RunGray)
        Spacer(Modifier.height(20.dp))
        if (courses.isEmpty()) {
            Text("조건에 맞는 코스가 없어요. 조건을 바꿔보세요.", color = RunGray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(courses) { course -> CourseCard(course) { onCourseClick(course) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, RunBlack),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RunBlack)
        ) { Text("조건 다시 선택", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun CourseCard(course: Course, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RunBlack)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(course.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RunWhite)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = RunLime, modifier = Modifier.size(16.dp))
                    Text(" ${course.rating}", color = RunWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(course.distanceKm, fontSize = 34.sp, fontWeight = FontWeight.Black, color = RunLime)
            Spacer(Modifier.height(10.dp))
            Row {
                Badge("#${course.scenery}", RunPurple)
                Spacer(Modifier.width(6.dp))
                Badge("난이도 ${course.difficulty}", RunGray)
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 12.sp, color = RunBlack, fontWeight = FontWeight.Bold)
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
    var saved by remember { mutableStateOf(false) }
    val hub = remember(course) { course.finishHubIds.firstOrNull()?.let { findHub(it) } }

    LaunchedEffect(course.name) {
        try { safety = fetchSafety() }
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
            Text(
                "ISSUE 01 · ${course.location.removePrefix("강릉 ").ifBlank { "GANGNEUNG" }}".uppercase(),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunGray
            )
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
        Text(course.reason, fontSize = 20.sp, fontWeight = FontWeight.Black, color = RunBlack, lineHeight = 27.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "${course.location}을 따라 이어지는 ${course.distanceKm} 루트를 중심으로, " +
                "러닝 뒤 가볍게 들를 수 있는 카페·식사·관광 동선을 함께 묶은 RunQ 큐레이션이에요.",
            fontSize = 13.sp, color = RunGray, lineHeight = 19.sp
        )
        Spacer(Modifier.height(24.dp))

        Text("QUICK INFO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunPurple)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickInfoCell("거리", course.distanceKm, Modifier.weight(1f))
            QuickInfoCell("예상", course.estimatedTime, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickInfoCell("난이도", course.difficulty, Modifier.weight(1f))
            QuickInfoCell("추천", course.scenery, Modifier.weight(1f))
        }
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
            "달린 뒤까지 이어지는 ${course.location.removePrefix("강릉 ")} 코스",
            fontSize = 19.sp, fontWeight = FontWeight.Black, color = RunBlack, lineHeight = 25.sp
        )
        Spacer(Modifier.height(14.dp))
        if (hub == null) {
            Box(modifier = Modifier.fillMaxWidth().background(RunWhite, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Text("이 코스는 아직 Finish Hub가 지정되지 않았어요.", fontSize = 13.sp, color = RunGray)
            }
        } else {
            HubCategoryRow("01", "EAT", RunLime, hub.curatedEat.firstOrNull()?.let { "$it 등에서 가볍게 한 끼" } ?: "") { onOpenCategory(PlaceCategory.EAT) }
            HubCategoryRow("02", "CAFE", RunPurple, hub.curatedCafe.firstOrNull()?.let { "$it 등에서 잠깐 쉬기" } ?: "") { onOpenCategory(PlaceCategory.CAFE) }
            HubCategoryRow("03", "SEE", RunLavender, hub.curatedSee.firstOrNull()?.let { "$it 주변을 천천히 둘러보기" } ?: "") { onOpenCategory(PlaceCategory.SEE) }
        }
        Spacer(Modifier.height(24.dp))

        Text("ROUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunGray)
        Spacer(Modifier.height(8.dp))
        CourseMapCard(course = course, title = course.name, heightDp = 200)
        Spacer(Modifier.height(24.dp))

        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)) {
            Text("이 코스로 달리기", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { saved = !saved }, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.5.dp, RunBlack),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RunBlack)) {
            Text(if (saved) "매거진 저장됨 ✓" else "이 매거진 저장하기", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// 실제 사진 대신 코스명을 크게 얹은 커버 플레이스홀더
// (Magazine Card Specs: "사진은 교체 가능한 표지 영역" — 실제 사진 붙이기 전 자리표시)
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
        Column(modifier = Modifier.align(Alignment.TopStart).padding(20.dp)) {
            Text(
                course.name, color = RunWhite, fontWeight = FontWeight.Black,
                fontSize = 34.sp, lineHeight = 38.sp
            )
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("FULL LOOP", color = RunWhite, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RunLime).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(course.distanceKm, color = RunBlack, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun QuickInfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = RunGray)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = RunBlack)
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

