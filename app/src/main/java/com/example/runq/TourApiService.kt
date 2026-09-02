package com.example.runq

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ────────────────────────────────────────────────
// TourAPI 통신 설정 + 인터페이스
// 서비스키는 local.properties → BuildConfig.TOUR_API_KEY 로 주입됨 (소스에 직접 노출 안 함)
// ────────────────────────────────────────────────

// API 요청을 정의하는 인터페이스
interface TourApi {
    @GET("locationBasedList2")
    suspend fun getNearbyPlaces(
        @Query("serviceKey") serviceKey: String = BuildConfig.TOUR_API_KEY,
        @Query("MobileOS") mobileOS: String = "AND",
        @Query("MobileApp") mobileApp: String = "RunQ",
        @Query("_type") type: String = "json",
        @Query("mapX") mapX: Double,      // 경도 (lng)
        @Query("mapY") mapY: Double,      // 위도 (lat)
        @Query("radius") radius: Int = 1500,
        @Query("numOfRows") numOfRows: Int = 30,
        @Query("arrange") arrange: String = "E",         // E = 거리순 정렬
        @Query("contentTypeId") contentTypeId: Int       // 12관광지 / 39음식점
    ): TourResponse

    // 장소 상세정보 (Place Detail 화면에서 contentId로 조회)
    @GET("detailCommon2")
    suspend fun getDetailCommon(
        @Query("serviceKey") serviceKey: String = BuildConfig.TOUR_API_KEY,
        @Query("MobileOS") mobileOS: String = "AND",
        @Query("MobileApp") mobileApp: String = "RunQ",
        @Query("_type") type: String = "json",
        @Query("contentId") contentId: String,
        @Query("defaultYN") defaultYN: String = "Y",
        @Query("firstImageYN") firstImageYN: String = "Y",
        @Query("addrinfoYN") addrInfoYN: String = "Y",
        @Query("overviewYN") overviewYN: String = "Y"
    ): DetailCommonResponse
}

// Retrofit 객체 (앱 전체에서 하나만 만들어 재사용)
object TourApiClient {
    private const val BASE_URL = "https://apis.data.go.kr/B551011/KorService2/"

    val api: TourApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TourApi::class.java)
    }
}