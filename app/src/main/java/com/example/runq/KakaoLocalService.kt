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

    // 카테고리 좌표 검색 — TourAPI에 카페 전용 contentTypeId가 없어서 카페(CE7) 보완용으로 사용.
    @GET("v2/local/search/category.json")
    suspend fun searchCategory(
        @Header("Authorization") authorization: String,
        @Query("category_group_code") categoryGroupCode: String,
        @Query("x") x: Double,     // 경도(lng)
        @Query("y") y: Double,     // 위도(lat)
        @Query("radius") radius: Int,
        @Query("sort") sort: String = "distance",
        @Query("size") size: Int = 15
    ): KakaoLocalResponse
}

data class KakaoLocalResponse(@SerializedName("documents") val documents: List<KakaoLocalPlace>)
data class KakaoLocalPlace(
    @SerializedName("place_name") val placeName: String?,
    @SerializedName("address_name") val addressName: String?,
    @SerializedName("road_address_name") val roadAddressName: String?,
    @SerializedName("phone") val phone: String?,
    @SerializedName("place_url") val placeUrl: String?,
    @SerializedName("distance") val distance: String?,  // sort=distance로 검색했을 때만 채워짐(m)
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

// Finish Hub 좌표 주변 카페(CE7)를 거리순으로 찾는다. TourAPI에는 카페 전용
// contentTypeId가 없어서(음식점 39에서 키워드로 걸러내는 방식뿐) 이 API로 보완한다.
// REST 키가 없으면 빈 목록을 반환(호출 자체를 하지 않음).
suspend fun fetchKakaoCafesNear(lat: Double, lng: Double, radiusM: Int): List<KakaoLocalPlace> {
    val key = BuildConfig.KAKAO_REST_API_KEY
    if (key.isBlank()) return emptyList()
    return runCatching {
        KakaoLocalClient.api.searchCategory(
            authorization = "KakaoAK $key",
            categoryGroupCode = "CE7",
            x = lng, y = lat,
            radius = radiusM.coerceIn(1, 20000)
        ).documents
    }.getOrDefault(emptyList())
}
