package com.example.runq

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// ════════════════════════════════════════════════════════
// 좌표 기반 파생값 계산 — 전부 순수 함수(오프라인 계산)라 API 키/네트워크가 필요 없다.
// "보강" 단계에서 자동 채우기로 예정됐던 항목 중, 실측 주소·이미지·API ID처럼 외부 호출이
// 필요한 값은 여기서 다루지 않는다(팀이 확정하기 전까지 추측값을 넣지 않는다는 원칙 유지).
// ════════════════════════════════════════════════════════

/** 두 좌표 사이 거리(m). 러닝/도보 수준의 짧은 거리에서도 오차가 커지지 않도록 haversine 사용. */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** 도보 이동시간(분) 추정 — 평균 도보 속도 4.5km/h 기준. */
fun walkingMinutes(meters: Double): Double = (meters / 1000.0) / 4.5 * 60.0

// 기상청 단기예보 격자(nx, ny) 변환 — 기상청이 공개한 위경도→격자 변환식(Lambert Conformal Conic).
// 코스별 위경도가 있으면 강릉 시내 고정 격자(92, 131) 대신 실제 코스 위치 격자를 쓸 수 있다.
private const val WEATHER_GRID_RE = 6371.00877       // 지구 반경(km)
private const val WEATHER_GRID_SIZE = 5.0            // 격자 간격(km)
private const val WEATHER_GRID_SLAT1 = 30.0          // 투영 위도1
private const val WEATHER_GRID_SLAT2 = 60.0          // 투영 위도2
private const val WEATHER_GRID_OLON = 126.0          // 기준점 경도
private const val WEATHER_GRID_OLAT = 38.0           // 기준점 위도
private const val WEATHER_GRID_XO = 43.0             // 기준점 X좌표
private const val WEATHER_GRID_YO = 136.0            // 기준점 Y좌표

fun latLngToWeatherGrid(lat: Double, lng: Double): Pair<Int, Int> {
    val degRad = PI / 180.0
    val re = WEATHER_GRID_RE / WEATHER_GRID_SIZE
    val slat1 = WEATHER_GRID_SLAT1 * degRad
    val slat2 = WEATHER_GRID_SLAT2 * degRad
    val olon = WEATHER_GRID_OLON * degRad
    val olat = WEATHER_GRID_OLAT * degRad

    var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
    sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
    var sf = tan(PI * 0.25 + slat1 * 0.5)
    sf = sf.pow(sn) * cos(slat1) / sn
    var ro = tan(PI * 0.25 + olat * 0.5)
    ro = re * sf / ro.pow(sn)

    val ra = re * sf / tan(PI * 0.25 + lat * degRad * 0.5).pow(sn)
    var theta = lng * degRad - olon
    if (theta > PI) theta -= 2.0 * PI
    if (theta < -PI) theta += 2.0 * PI
    theta *= sn

    val x = Math.floor(ra * sin(theta) + WEATHER_GRID_XO + 0.5).toInt()
    val y = Math.floor(ro - ra * cos(theta) + WEATHER_GRID_YO + 0.5).toInt()
    return x to y
}
