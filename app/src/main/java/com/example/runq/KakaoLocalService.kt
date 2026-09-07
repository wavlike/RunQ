package com.example.runq

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// ════════════════════════════════════════════════════════
// Kakao Local 키워드 검색 API — Finish Hub 큐레이션 장소(EAT/CAFE/SEE)의
// 주소/좌표/전화번호를 실시간으로 보완한다.
// 엑셀(콘텐츠 JSON)에 원래 값이 있으면 그 값을 그대로 쓰고, 비어있을 때만 이 API로
// 보완한다 — 팀이 확정한 콘텐츠 값보다 API 조회값을 우선하지 않는다는 원칙 유지.
// 인증키는 local.properties → BuildConfig.KAKAO_REST_API_KEY 로 주입되며,
// 키가 비어있으면(REST 키 미발급) 호출 자체를 하지 않고 조용히 건너뛴다.
// ════════════════════════════════════════════════════════

interface KakaoLocalApi {
    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("size") size: Int = 1
    ): KakaoLocalResponse
}

data class KakaoLocalResponse(@SerializedName("documents") val documents: List<KakaoLocalPlace>)
data class KakaoLocalPlace(
    @SerializedName("place_name") val placeName: String?,
    @SerializedName("address_name") val addressName: String?,
    @SerializedName("road_address_name") val roadAddressName: String?,
    @SerializedName("phone") val phone: String?,
    @SerializedName("place_url") val placeUrl: String?,
    @SerializedName("x") val x: String?,   // 경도(lng)
    @SerializedName("y") val y: String?    // 위도(lat)
)

object KakaoLocalClient {
    private const val BASE_URL = "https://dapi.kakao.com/"
    val api: KakaoLocalApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KakaoLocalApi::class.java)
    }
}

data class KakaoPlaceLookup(
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val phone: String?,
    val placeUrl: String?
)

// REST 키가 없으면(local.properties 미설정) 호출하지 않고 null 반환.
// "강릉"을 붙여 검색해 동명 상호 오검색을 줄인다.
suspend fun fetchKakaoPlaceInfo(placeName: String): KakaoPlaceLookup? {
    val key = BuildConfig.KAKAO_REST_API_KEY
    if (key.isBlank()) return null
    return runCatching {
        val result = KakaoLocalClient.api.searchKeyword(
            authorization = "KakaoAK $key",
            query = "강릉 $placeName"
        ).documents.firstOrNull() ?: return@runCatching null
        KakaoPlaceLookup(
            address = result.roadAddressName?.takeIf { it.isNotBlank() } ?: result.addressName,
            lat = result.y?.toDoubleOrNull(),
            lng = result.x?.toDoubleOrNull(),
            phone = result.phone?.takeIf { it.isNotBlank() },
            placeUrl = result.placeUrl
        )
    }.getOrNull()
}
