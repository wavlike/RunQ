package com.example.runq

import com.google.gson.annotations.SerializedName

// ────────────────────────────────────────────────
// TourAPI(locationBasedList2) 응답을 담는 데이터 클래스들
// 응답 JSON 구조: response > body > items > item[]
// Spring에서 DTO 만들던 것과 같은 개념이에요.
// ────────────────────────────────────────────────

data class TourResponse(
    @SerializedName("response") val response: TourBody
)

data class TourBody(
    @SerializedName("body") val body: TourItems
)

data class TourItems(
    // 결과가 없을 때 items가 빈 문자열("")로 오는 경우가 있어 nullable 처리
    @SerializedName("items") val items: TourItemList?
)

data class TourItemList(
    @SerializedName("item") val item: List<TourPlace>?
)

// 실제로 쓸 장소 하나의 정보
data class TourPlace(
    @SerializedName("contentid") val contentId: String?,  // 장소 상세조회(detailCommon2)용 ID
    @SerializedName("title") val title: String?,          // 장소명
    @SerializedName("addr1") val addr1: String?,          // 주소
    @SerializedName("firstimage") val firstImage: String?,// 대표 이미지 URL
    @SerializedName("dist") val dist: String?,            // 코스로부터의 거리(m)
    @SerializedName("contenttypeid") val contentTypeId: String?, // 12관광지 / 39음식점 등
    @SerializedName("mapx") val mapX: String?,            // 경도
    @SerializedName("mapy") val mapY: String?             // 위도
)

// ────────────────────────────────────────────────
// 장소 상세(detailCommon2) 응답
// 응답 JSON 구조: response > body > items > item[] (locationBasedList2와 동일한 껍데기)
// ────────────────────────────────────────────────
data class DetailCommonResponse(
    @SerializedName("response") val response: DetailCommonBody
)

data class DetailCommonBody(
    @SerializedName("body") val body: DetailCommonItems
)

data class DetailCommonItems(
    @SerializedName("items") val items: DetailCommonItemList?
)

data class DetailCommonItemList(
    @SerializedName("item") val item: List<DetailCommonItem>?
)

data class DetailCommonItem(
    @SerializedName("title") val title: String?,
    @SerializedName("addr1") val addr1: String?,
    @SerializedName("tel") val tel: String?,
    @SerializedName("homepage") val homepage: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("firstimage") val firstImage: String?
)