package com.example.runq

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ────────────────────────────────────────────────
// TourAPI 통신 설정 + 인터페이스
// ────────────────────────────────────────────────

// ⚠️⚠️ 여기에 본인 인증키(Decoding 키)를 붙여넣으세요 ⚠️⚠️
// (지금은 빠르게 테스트하려고 여기 직접 넣어요. GitHub 올릴 땐 빼야 하는데,
//  그 안전하게 빼는 방법은 이 단계 성공한 뒤에 알려줄게요.)
const val TOUR_API_KEY = "HAVRC68bADYCJJkl96ezCrfFdamvQIi3mnhg7/avItL8WBE9yOcLbFHW+YB4FD+PVnXND/TGuKbk/78heKHAKg=="
// API 요청을 정의하는 인터페이스
interface TourApi {
    @GET("locationBasedList2")
    suspend fun getNearbyPlaces(
        @Query("serviceKey") serviceKey: String = TOUR_API_KEY,
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