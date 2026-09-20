package com.example.runq  // ⚠️ 본인 프로젝트 package로 맞추세요

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ════════════════════════════════════════════════════════
// 안전 정보 API: 기상청(날씨) + 에어코리아(미세먼지)
// 인증키는 관광공사와 동일 (TOUR_API_KEY 재사용)
// ════════════════════════════════════════════════════════

// ── 1) 기상청 단기예보 (초단기실황) ──────────────────
// 강릉 격자 좌표: nx=92, ny=131 (기상청 격자 기준 고정값)
interface WeatherApi {
    @GET("getUltraSrtNcst")
    suspend fun getNowWeather(
        @Query("serviceKey") serviceKey: String = BuildConfig.TOUR_API_KEY,
        @Query("dataType") dataType: String = "JSON",
        @Query("numOfRows") numOfRows: Int = 60,
        @Query("pageNo") pageNo: Int = 1,
        @Query("base_date") baseDate: String,   // yyyyMMdd
        @Query("base_time") baseTime: String,   // HHmm (정시)
        @Query("nx") nx: Int = 92,
        @Query("ny") ny: Int = 131
    ): WeatherResponse

    // 초단기실황(getUltraSrtNcst)엔 하늘상태(SKY)가 없어서 "맑음"을 판단할 방법이 아예 없었다.
    // 초단기예보(getUltraSrtFcst)에만 SKY/PTY가 있어서, 실황과 별도로 이걸 조회해서 실제
    // 하늘 상태(맑음/구름많음/흐림/비/눈)를 가져온다.
    @GET("getUltraSrtFcst")
    suspend fun getFcstWeather(
        @Query("serviceKey") serviceKey: String = BuildConfig.TOUR_API_KEY,
        @Query("dataType") dataType: String = "JSON",
        @Query("numOfRows") numOfRows: Int = 60,
        @Query("pageNo") pageNo: Int = 1,
        @Query("base_date") baseDate: String,
        @Query("base_time") baseTime: String,
        @Query("nx") nx: Int = 92,
        @Query("ny") ny: Int = 131
    ): WeatherForecastResponse
}

data class WeatherResponse(@SerializedName("response") val response: WeatherBody)
data class WeatherBody(@SerializedName("body") val body: WeatherItems?)
data class WeatherItems(@SerializedName("items") val items: WeatherItemList?)
data class WeatherItemList(@SerializedName("item") val item: List<WeatherItem>?)
data class WeatherItem(
    @SerializedName("category") val category: String?,  // T1H=기온, RN1=강수, WSD=풍속
    @SerializedName("obsrValue") val obsrValue: String?
)

data class WeatherForecastResponse(@SerializedName("response") val response: WeatherForecastBody)
data class WeatherForecastBody(@SerializedName("body") val body: WeatherForecastItems?)
data class WeatherForecastItems(@SerializedName("items") val items: WeatherForecastItemList?)
data class WeatherForecastItemList(@SerializedName("item") val item: List<WeatherForecastItem>?)
data class WeatherForecastItem(
    @SerializedName("category") val category: String?,   // SKY=하늘상태, PTY=강수형태
    @SerializedName("fcstDate") val fcstDate: String?,
    @SerializedName("fcstTime") val fcstTime: String?,
    @SerializedName("fcstValue") val fcstValue: String?
)

// ── 2) 에어코리아 미세먼지 (시도별 실시간) ────────────
interface AirApi {
    @GET("getCtprvnRltmMesureDnsty")
    suspend fun getAir(
        @Query("serviceKey") serviceKey: String = BuildConfig.TOUR_API_KEY,
        @Query("returnType") returnType: String = "json",
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("pageNo") pageNo: Int = 1,
        @Query("sidoName") sidoName: String = "강원",
        @Query("ver") ver: String = "1.3"
    ): AirResponse
}

data class AirResponse(@SerializedName("response") val response: AirBody)
data class AirBody(@SerializedName("body") val body: AirItems?)
data class AirItems(@SerializedName("items") val items: List<AirItem>?)
data class AirItem(
    @SerializedName("stationName") val stationName: String?,
    @SerializedName("pm10Grade") val pm10Grade: String?,   // 1좋음 2보통 3나쁨 4매우나쁨
    @SerializedName("pm25Grade") val pm25Grade: String?,
    @SerializedName("pm10Value") val pm10Value: String?,
    @SerializedName("pm25Value") val pm25Value: String?
)

// ── Retrofit 클라이언트 (기관별 BaseURL 다름) ─────────
object WeatherClient {
    private const val BASE = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/"
    val api: WeatherApi by lazy {
        Retrofit.Builder().baseUrl(BASE)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(WeatherApi::class.java)
    }
}

object AirClient {
    private const val BASE = "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/"
    val api: AirApi by lazy {
        Retrofit.Builder().baseUrl(BASE)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(AirApi::class.java)
    }
}

// ── 화면에 표시할 가공된 안전정보 ─────────────────────
data class SafetyInfo(
    val temp: String,       // "22℃"
    val rain: String,       // "없음" / "있음"
    val pm10: String,       // "보통"
    val wind: String,       // "2.3m/s"
    val fitness: String,    // 종합 러닝 적합도
    val sky: String? = null // "맑음"/"구름많음"/"흐림"/"비"/"눈"/"소나기" — 값을 못 얻으면 null(절대 "맑음"으로 임의 채우지 않음)
)

// 등급 숫자 → 한글
private fun grade(g: String?): String = when (g) {
    "1" -> "좋음"; "2" -> "보통"; "3" -> "나쁨"; "4" -> "매우나쁨"; else -> "-"
}

// 오늘 날짜·정시 계산 (기상청은 매시 40분 이후 해당 정시 실황 제공 → 안전하게 1시간 전 정시 사용)
private fun baseDateTime(): Pair<String, String> {
    val now = java.util.Calendar.getInstance()
    now.add(java.util.Calendar.HOUR_OF_DAY, -1)
    val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.KOREA).format(now.time)
    val time = java.text.SimpleDateFormat("HH00", java.util.Locale.KOREA).format(now.time)
    return date to time
}

// 날씨 + 미세먼지를 한 번에 불러와 가공.
// nx/ny를 넘기면 강릉 시내 고정 격자 대신 해당 좌표(코스 위치)의 격자로 조회한다.
suspend fun fetchSafety(grid: Pair<Int, Int>? = null): SafetyInfo {
    // 날씨
    val (d, t) = baseDateTime()
    val w = (if (grid != null) WeatherClient.api.getNowWeather(baseDate = d, baseTime = t, nx = grid.first, ny = grid.second)
             else WeatherClient.api.getNowWeather(baseDate = d, baseTime = t))
        .response.body?.items?.item ?: emptyList()
    val temp = w.firstOrNull { it.category == "T1H" }?.obsrValue ?: "-"
    val rn1 = w.firstOrNull { it.category == "RN1" }?.obsrValue ?: "0"
    val rain = if (rn1 == "0" || rn1 == "강수없음") "없음" else "있음"
    val windSpeed = w.firstOrNull { it.category == "WSD" }?.obsrValue?.toDoubleOrNull()
    val wind = windSpeed?.let { "%.1fm/s".format(it) } ?: "-"

    // 미세먼지 (강릉 측정소 우선, 없으면 강원 첫 번째)
    val airList = AirClient.api.getAir().response.body?.items ?: emptyList()
    val gn = airList.firstOrNull { (it.stationName ?: "").contains("강릉") } ?: airList.firstOrNull()
    val pm10 = grade(gn?.pm10Grade)

    // 하늘 상태 — 초단기실황(getNowWeather)엔 SKY 필드 자체가 없어서 여기서 못 구한다.
    // 실패해도(서비스 점검 등) 전체 조회가 깨지지 않도록 별도로 감싼다.
    val fcst = runCatching {
        (if (grid != null) WeatherClient.api.getFcstWeather(baseDate = d, baseTime = t, nx = grid.first, ny = grid.second)
         else WeatherClient.api.getFcstWeather(baseDate = d, baseTime = t))
            .response.body?.items?.item ?: emptyList()
    }.getOrDefault(emptyList())
    // 여러 예보 시각이 섞여 오므로, 지금과 가장 가까운(가장 이른) 시각 하나만 골라 쓴다.
    val earliestKey = fcst.minByOrNull { "${it.fcstDate}${it.fcstTime}" }?.let { "${it.fcstDate}${it.fcstTime}" }
    val ptyCode = fcst.firstOrNull { it.category == "PTY" && "${it.fcstDate}${it.fcstTime}" == earliestKey }?.fcstValue
    val skyCode = fcst.firstOrNull { it.category == "SKY" && "${it.fcstDate}${it.fcstTime}" == earliestKey }?.fcstValue
    val sky = when (ptyCode) {
        "1" -> "비"; "2" -> "비/눈"; "3" -> "눈"; "4" -> "소나기"
        else -> when (skyCode) {
            "1" -> "맑음"; "3" -> "구름많음"; "4" -> "흐림"
            else -> null
        }
    }

    // 종합 적합도 (간단 규칙)
    val fitness = when {
        rain == "있음" -> "우천 주의"
        pm10 == "나쁨" || pm10 == "매우나쁨" -> "대기질 주의"
        windSpeed != null && windSpeed >= 8.0 -> "강풍 주의"
        else -> "좋음"
    }

    return SafetyInfo(
        temp = if (temp == "-") "-" else "${temp}℃",
        rain = rain,
        pm10 = pm10,
        wind = wind,
        fitness = fitness,
        sky = sky
    )
}