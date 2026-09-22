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

// ────────────────────────────────────────────────
// 장소 소개(detailIntro2) 응답 — 운영시간 등은 관광타입(contentTypeId)마다 필드명이 다르다.
// 응답 자체가 돌려주는 contenttypeid를 기준으로 어느 필드를 읽을지 정하는 게 우리가 요청 시
// 넘긴 값(추정치일 수 있음)보다 더 정확하다.
// ────────────────────────────────────────────────
data class DetailIntroResponse(
    @SerializedName("response") val response: DetailIntroBody
)

data class DetailIntroBody(
    @SerializedName("body") val body: DetailIntroItems
)

data class DetailIntroItems(
    @SerializedName("items") val items: DetailIntroItemList?
)

data class DetailIntroItemList(
    @SerializedName("item") val item: List<DetailIntroItem>?
)

data class DetailIntroItem(
    @SerializedName("contenttypeid") val contentTypeId: String?,
    @SerializedName("opentimefood") val openTimeFood: String?,       // 39 음식점
    @SerializedName("restdatefood") val restDateFood: String?,
    @SerializedName("usetime") val useTime: String?,                 // 12 관광지
    @SerializedName("restdate") val restDate: String?,
    @SerializedName("usetimeculture") val useTimeCulture: String?,   // 14 문화시설
    @SerializedName("restdateculture") val restDateCulture: String?,
    @SerializedName("usetimeleports") val useTimeLeports: String?,   // 28 레포츠
    @SerializedName("restdateleports") val restDateLeports: String?,
    @SerializedName("opentime") val openTimeShopping: String?,       // 38 쇼핑
    @SerializedName("restdateshopping") val restDateShopping: String?
) {
    // 실제로 응답에 채워져 있는 콘텐츠타입 기준으로 영업시간/휴무일 필드를 고른다.
    // 값이 없으면(해당 타입에 그 필드가 없거나 TourAPI가 비워둔 경우) null — 절대 임의로 채우지 않는다.
    fun hoursLabel(): String? = when (contentTypeId) {
        "39" -> openTimeFood
        "12" -> useTime
        "14" -> useTimeCulture
        "28" -> useTimeLeports
        "38" -> openTimeShopping
        else -> openTimeFood ?: useTime ?: useTimeCulture ?: useTimeLeports ?: openTimeShopping
    }?.takeIf { it.isNotBlank() }

    fun restDateLabel(): String? = when (contentTypeId) {
        "39" -> restDateFood
        "12" -> restDate
        "14" -> restDateCulture
        "28" -> restDateLeports
        "38" -> restDateShopping
        else -> restDateFood ?: restDate ?: restDateCulture ?: restDateLeports ?: restDateShopping
    }?.takeIf { it.isNotBlank() }
}

// ────────────────────────────────────────────────
// 행사/축제(searchFestival2) 응답
// 응답 JSON 구조: response > body > items > item[] (locationBasedList2와 동일한 껍데기)
// ────────────────────────────────────────────────
data class FestivalResponse(
    @SerializedName("response") val response: FestivalBody
)

data class FestivalBody(
    @SerializedName("body") val body: FestivalItems
)

data class FestivalItems(
    @SerializedName("items") val items: FestivalItemList?
)

data class FestivalItemList(
    @SerializedName("item") val item: List<TourFestival>?
)

data class TourFestival(
    @SerializedName("contentid") val contentId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("addr1") val addr1: String?,
    @SerializedName("firstimage") val firstImage: String?,
    @SerializedName("eventstartdate") val eventStartDate: String?, // yyyyMMdd
    @SerializedName("eventenddate") val eventEndDate: String?,     // yyyyMMdd
    @SerializedName("mapx") val mapX: String?,
    @SerializedName("mapy") val mapY: String?
)