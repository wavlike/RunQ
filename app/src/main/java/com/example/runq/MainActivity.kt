package com.example.runq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ────────────────────────────────────────────────
// 1) API 데이터 모델 (관광공사 및 날씨)
// ────────────────────────────────────────────────
data class TourResponse(val response: TourResponseData)
data class TourResponseData(val body: TourBody)
data class TourBody(val items: TourItems?, val totalCount: Int)
data class TourItems(val item: List<TourItem>)
data class TourItem(
    val title: String,
    val addr1: String,
    val contentid: String,
    val firstimage: String,
    val dist: String
)

data class WeatherResponse(val response: WeatherResponseData)
data class WeatherResponseData(val body: WeatherBody)
data class WeatherBody(val items: WeatherItems)
data class WeatherItems(val item: List<WeatherItem>)
data class WeatherItem(val category: String, val obsrValue: String)

// ────────────────────────────────────────────────
// 2) Retrofit API 서비스 정의
// ────────────────────────────────────────────────
interface RunQApiService {
    @GET("B551011/KorService2/locationBasedList2")
    suspend fun getNearbyPlaces(
        @Query("serviceKey") serviceKey: String,
        @Query("mapX") lng: Double,
        @Query("mapY") lat: Double,
        @Query("radius") radius: Int = 2000,
        @Query("MobileOS") os: String = "AND",
        @Query("MobileApp") appName: String = "RunQ",
        @Query("_type") type: String = "json",
        @Query("contentTypeId") contentTypeId: String = "12"
    ): TourResponse

    @GET("1360000/VilageFcstInfoService_2.0/getUltraSrtNcst")
    suspend fun getWeather(
        @Query("serviceKey") serviceKey: String,
        @Query("base_date") date: String,
        @Query("base_time") time: String,
        @Query("nx") nx: Int = 92,
        @Query("ny") ny: Int = 131,
        @Query("dataType") type: String = "JSON"
    ): WeatherResponse
}

object RetrofitClient {
    private const val TOUR_BASE_URL = "https://apis.data.go.kr/"

    val instance: RunQApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TOUR_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RunQApiService::class.java)
    }
}

// ────────────────────────────────────────────────
// 3) ViewModel: 데이터 관리 및 API 호출
// ────────────────────────────────────────────────
class RunQViewModel : ViewModel() {
    private val serviceKey = "YOUR_API_KEY_HERE"

    var nearbyPlaces by mutableStateOf<List<TourItem>>(emptyList())
    var currentTemperature by mutableStateOf("22")
    var isLoadingPlaces by mutableStateOf(false)

    fun fetchNearbyPlaces(lat: Double, lng: Double) {
        isLoadingPlaces = true
        nearbyPlaces = listOf(
            TourItem("경포해변", "강원도 강릉시 안현동", "1", "", "500m"),
            TourItem("초당순두부마을", "강원도 강릉시 초당동", "2", "", "1.2km")
        )
        isLoadingPlaces = false
    }
}

// ────────────────────────────────────────────────
// 4) UI 모델 및 데이터
// ────────────────────────────────────────────────
data class Course(
    val name: String,
    val location: String,
    val distance: String,
    val estimatedTime: String,
    val scenery: String,
    val difficulty: String,
    val reason: String,
    val nearby: String,
    val tags: List<String>,
    val lat: Double,
    val lng: Double,
    val trafficLevel: String = "쾌적"
)

val allCourses = listOf(
    Course("경포호 기본런", "경포호 둘레길 한 바퀴", "약 4.3~5km", "약 35분", "호수", "쉬움", "평지 위주라 초보 러너가 부담 없이 완주하기 좋아요.", "경포해변, 허균·허난설헌 기념공원", listOf("호수", "짧은코스", "쉬움", "초보추천"), 37.7946, 128.9022),
    Course("안목해변 커피거리 왕복런", "안목해변 일대", "약 4.3~5km", "약 40분", "바다", "쉬움", "바다를 끼고 달리다 커피거리에서 마무리하기 좋은 코스예요.", "안목 커피거리, 강문해변", listOf("바다", "짧은코스", "쉬움", "사진명소"), 37.7712, 128.9482),
    Course("주문진해변 → 소돌아들바위공원", "주문진해변 일대", "약 1.4~3km", "약 20분", "바다", "쉬움", "사진 명소와 해변을 오가는 짧은 코스예요.", "소돌해변, 아들바위공원", listOf("바다", "짧은코스", "쉬움", "사진명소"), 37.9000, 128.8300),
    Course("강문→순포 바다런", "강문~순포해변", "약 4km", "약 30분", "바다", "쉬움", "바다 보면서 가볍게 뛰고 싶은 분들께 추천합니다.", "테라로사 경포호수점, 순포습지", listOf("바다", "짧은코스", "쉬움", "사진명소"), 37.8085, 128.9090),
    Course("안목→강문→경포 바다런", "안목~강문~경포", "약 5~7km", "약 50분", "바다", "보통", "강릉 대표 바다 코스를 한 번에 뛰고 싶은 사람을 위한 코스입니다.", "안목 커피거리, 강문해변", listOf("바다", "중거리", "보통", "관광연계"), 37.7850, 128.9300),
    Course("경포호 5K", "경포호 한 바퀴", "5km", "약 40분", "호수", "쉬움", "초보 러너를 위한 5K 보정 코스입니다.", "경포호수공원", listOf("호수", "5K", "쉬움", "초보추천"), 37.7946, 128.9022),
    Course("경포호 10K", "경포호 2바퀴", "약 10km", "약 70분", "호수", "보통", "경포호 2바퀴로 거리를 채우는 챌린지형 코스예요.", "가시연습지, 경포대", listOf("호수", "10K", "보통", "챌린지"), 37.7946, 128.9022),
    Course("경포호 고래런", "경포호 일대", "약 12km", "약 85분", "호수", "어려움", "고래 모양 GPS 아트런에 도전해보세요!", "경포호수광장", listOf("호수", "장거리", "보통", "챌린지"), 37.7920, 128.9050),
    Course("남대천→안목해변 5K", "남대천~안목", "약 5km", "약 40분", "강변", "쉬움", "도심 강변에서 바다로 빠지는 코스를 원하는 분께 추천합니다.", "월화거리, 중앙시장", listOf("강변", "5K", "쉬움", "카페연계"), 37.7550, 128.8980),
    Course("오죽헌→선교장→경포호", "오죽헌 일대", "약 5~7km", "약 50분", "문화", "보통", "문화유산과 러닝을 같이 즐기고 싶은 사람을 위한 코스입니다.", "오죽헌, 선교장", listOf("문화", "중거리", "보통", "관광연계"), 37.7792, 128.8785),
    Course("경포생태저류지 메타세쿼이아길", "생태저류지 일대", "약 2.5~4km", "약 30분", "숲길", "쉬움", "조용한 숲길 산책형 러닝 코스입니다.", "경포생태저류지", listOf("숲길", "짧은코스", "쉬움", "힐링"), 37.7850, 128.8950),
    Course("옥계 헌화로 11K", "옥계 헌화로", "11km", "약 80분", "바다", "어려움", "상급 러너를 위한 해안 절경 대회형 코스입니다.", "금진항, 옥계해변", listOf("바다", "장거리", "어려움", "챌린지"), 37.6350, 129.0550)
)

sealed class ScreenState {
    object Condition : ScreenState()
    data class Result(val courses: List<Course>) : ScreenState()
    data class Detail(val course: Course) : ScreenState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RunQApp()
                }
            }
        }
    }
}

@Composable
fun RunQApp(viewModel: RunQViewModel = viewModel()) {
    var screen by remember { mutableStateOf<ScreenState>(ScreenState.Condition) }

    when (val current = screen) {
        is ScreenState.Condition -> ConditionScreen(
            onRecommend = { scenery, distance, difficulty ->
                val filtered = allCourses.filter { course ->
                    (scenery == "상관없음" || course.scenery == scenery) &&
                            (difficulty == "상관없음" || course.difficulty == difficulty) &&
                            (distance == "상관없음" || course.tags.contains(distance))
                }
                screen = ScreenState.Result(filtered)
            }
        )
        is ScreenState.Result -> ResultScreen(
            courses = current.courses,
            onCourseClick = { course ->
                viewModel.fetchNearbyPlaces(course.lat, course.lng)
                screen = ScreenState.Detail(course)
            },
            onBack = { screen = ScreenState.Condition }
        )
        is ScreenState.Detail -> DetailScreen(
            course = current.course,
            viewModel = viewModel,
            onBack = { screen = ScreenState.Condition }
        )
    }
}

// ── 화면 1: 조건 선택 ──────────────────────────────
// ── 화면 1: 조건 선택 (LazyColumn 스크롤 완벽 보장 버전) ──────────────────────────────
@Composable
fun ConditionScreen(onRecommend: (String, String, String) -> Unit) {
    var scenery by remember { mutableStateOf("상관없음") }
    var distance by remember { mutableStateOf("상관없음") }
    var difficulty by remember { mutableStateOf("상관없음") }

    // Column 대신 LazyColumn을 사용하여 내부 아이템들이 자연스럽게 스크롤되도록 합니다.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // 아이템 간의 간격 고정
    ) {
        // 1) 오늘 날씨 카드
        item {
            EnvironmentBriefingCard()
        }

        // 2) 타이틀 텍스트
        item {
            Spacer(Modifier.height(8.dp))
            Text("어떤 러닝을 원하세요?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // 3) 필터 옵션 그룹들
        item { OptionGroup("경관", listOf("상관없음", "바다", "호수", "강변", "문화", "숲길"), scenery) { scenery = it } }
        item { OptionGroup("거리", listOf("상관없음", "짧은코스", "5K", "중거리", "10K", "장거리"), distance) { distance = it } }
        item { OptionGroup("난이도", listOf("상관없음", "쉬움", "보통", "어려움"), difficulty) { difficulty = it } }

        // 4) 추천 버튼
        item {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onRecommend(scenery, distance, difficulty) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("맞춤 런트립 코스 찾기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable
fun EnvironmentBriefingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("강릉 오늘 러닝 환경", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                EnvironmentItem("기온", "22℃")
                EnvironmentItem("미세먼지", "좋음")
                EnvironmentItem("풍속", "보통")
                EnvironmentItem("적합도", "좋음", isHighlight = true)
            }
        }
    }
}

@Composable
fun EnvironmentItem(label: String, value: String, isHighlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if(isHighlight) Color(0xFF2E7D32) else Color.Unspecified)
    }
}

@Composable
fun OptionGroup(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                FilterChip(
                    selected = (option == selected),
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

// ── 화면 2: 결과 리스트 ─────────────────────────────
@Composable
fun ResultScreen(courses: List<Course>, onCourseClick: (Course) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text("추천 코스 ${courses.size}개", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(courses) { course ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onCourseClick(course) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(course.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${course.distance} · ${course.difficulty}", fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ── 화면 3: 상세 페이지 (지도 연동) ─────────────────────
@Composable
fun DetailScreen(course: Course, viewModel: RunQViewModel, onBack: () -> Unit) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(course.lat, course.lng), 14f)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, null) }
            Text("코스 상세", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Text(course.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(course.location, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))

                Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.LightGray, RoundedCornerShape(12.dp))) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        Marker(state = MarkerState(position = LatLng(course.lat, course.lng)), title = course.name)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    DetailInfoChip(Icons.Default.Map, course.distance)
                    DetailInfoChip(Icons.Default.Timer, course.estimatedTime)
                    DetailInfoChip(Icons.Default.Traffic, course.trafficLevel)
                }

                Spacer(Modifier.height(30.dp))
                Text("추천 이유", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(course.reason, modifier = Modifier.padding(vertical = 8.dp))

                Spacer(Modifier.height(30.dp))
                Text("주변 추천 장소 (OpenAPI)", fontWeight = FontWeight.Bold, fontSize = 17.sp)

                if (viewModel.isLoadingPlaces) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                } else {
                    viewModel.nearbyPlaces.forEach { place ->
                        NearbyPlaceItem(place)
                    }
                }

                Spacer(Modifier.height(40.dp))
                Button(onClick = { /* GPX 다운로드 로직 */ }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("GPX 코스 다운로드")
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun DetailInfoChip(icon: ImageVector, text: String) {
    Surface(color = Color(0xFFF0F2F5), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = Color.DarkGray)
            Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 13.sp)
        }
    }
}

@Composable
fun NearbyPlaceItem(place: TourItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, null, tint = Color.Red, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(place.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(place.addr1, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

// ⭐ 높이가 정상적으로 줄어들도록 수정한 FlowRow 레이아웃
@Composable
fun FlowRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }

        var totalHeight = 0
        var rowWidth = 0
        var rowMaxHeight = 0

        placeables.forEach { placeable ->
            if (rowWidth + placeable.width > constraints.maxWidth) {
                totalHeight += rowMaxHeight + 8.dp.roundToPx()
                rowWidth = 0
                rowMaxHeight = 0
            }
            rowWidth += placeable.width + 8.dp.roundToPx()
            rowMaxHeight = maxOf(rowMaxHeight, placeable.height)
        }
        totalHeight += rowMaxHeight

        layout(constraints.maxWidth, totalHeight.coerceAtMost(constraints.maxHeight)) {
            var y = 0
            var x = 0
            var maxY = 0
            placeables.forEach { placeable ->
                if (x + placeable.width > constraints.maxWidth) {
                    x = 0
                    y += maxY + 8.dp.roundToPx()
                    maxY = 0
                }
                placeable.placeRelative(x, y)
                x += placeable.width + 8.dp.roundToPx()
                maxY = maxOf(maxY, placeable.height)
            }
        }
    }
}