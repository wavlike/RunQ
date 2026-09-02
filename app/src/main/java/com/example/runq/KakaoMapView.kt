package com.example.runq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles

// ════════════════════════════════════════════════════════
// Kakao Maps SDK v2 공용 지도 컴포저블.
//
// ⚠️ 이 파일은 실제 컴파일 검증을 못 한 상태예요(샌드박스에 Android SDK가 없음).
// Label/RouteLine 쪽 클래스명·빌더 패턴은 Kakao 공식 문서 기준으로 최대한 맞췄지만,
// Android Studio에서 처음 빌드할 때 이 파일이 제일 먼저 손볼 후보예요 —
// 빨간 줄 뜨면 자동완성으로 실제 시그니처 맞춰주면 됩니다. 나머지 화면 코드는
// 이 파일이 제공하는 KakaoRouteMap() 하나만 갖다 쓰는 구조라 여기만 고치면 전체가 고쳐져요.
// ════════════════════════════════════════════════════════

/** MapView의 start/resume/pause/destroy를 Compose 생명주기에 맞춰 관리하는 저수준 래퍼. */
@Composable
private fun rememberKakaoMapView(
    onMapReady: (KakaoMap) -> Unit
): MapView {
    val lifecycleOwner = LocalLifecycleOwner.current
    val onMapReadyState = rememberUpdatedState(onMapReady)
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    DisposableEffect(lifecycleOwner, mapView) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() { /* no-op */ }
                override fun onMapError(exception: Exception) { /* 지도 초기화 실패 — 로그로 확인 필요 */ }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(kakaoMap: KakaoMap) {
                    onMapReadyState.value(kakaoMap)
                }
            }
        )

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return mapView
}

/**
 * 코스 경로(RouteLine) + START/FINISH 라벨을 그리는 지도.
 * routePoints가 비어있으면 start/finish 두 점만으로 직선을 그린다.
 * currentLocation이 있으면(러닝 중) 현재 위치 라벨도 함께 표시한다.
 */
@Composable
fun KakaoRouteMap(
    modifier: Modifier = Modifier,
    routePoints: List<RoutePoint>,
    startPoint: RoutePoint?,
    finishPoint: RoutePoint?,
    currentLocation: RoutePoint? = null
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        // 아직 Kakao Native App Key가 없는 상태 — 크래시 대신 안내만 표시
        Box(
            modifier = modifier.background(RunBgGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Kakao 지도 키가 설정되지 않았어요.\nlocal.properties에 KAKAO_NATIVE_APP_KEY를 채워주세요.",
                fontSize = 12.sp, color = RunGray)
        }
        return
    }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var currentLocationLabel by remember { mutableStateOf<Label?>(null) }
    val mapView = rememberKakaoMapView(onMapReady = { kakaoMap = it })

    AndroidView(modifier = modifier, factory = { mapView })

    // 지도 준비 완료 + 경로 데이터가 있을 때마다 RouteLine/라벨을 다시 그린다.
    LaunchedEffect(kakaoMap, routePoints, startPoint, finishPoint) {
        val map = kakaoMap ?: return@LaunchedEffect
        drawRoute(map, routePoints, startPoint, finishPoint)
    }

    // 러닝 중 현재 위치 — 라벨을 새로 만들지 않고 기존 라벨을 이동시켜서 깜빡임 없이 갱신.
    LaunchedEffect(kakaoMap, currentLocation) {
        val map = kakaoMap ?: return@LaunchedEffect
        val loc = currentLocation ?: return@LaunchedEffect
        val position = LatLng.from(loc.lat, loc.lng)
        val label = currentLocationLabel
        if (label != null) {
            runCatching { label.moveTo(position) }
        } else {
            runCatching {
                currentLocationLabel = map.labelManager?.layer?.addLabel(
                    LabelOptions.from(position)
                        .setStyles(LabelStyles.from(LabelStyle.from(android.R.drawable.presence_invisible)))
                )
            }
        }
        runCatching { map.moveCamera(CameraUpdateFactory.newCenterPosition(position)) }
    }
}

/**
 * 경로(points)를 따라 진행률(progress, 0~1)에 해당하는 지점을 선형보간으로 계산.
 * 실제 GPS 트래킹이 붙기 전까지 "지금 위치" 시뮬레이션에 쓴다 (지구 곡률 무시한 단순 근사).
 */
fun interpolateAlongRoute(points: List<RoutePoint>, progress: Float): RoutePoint? {
    if (points.isEmpty()) return null
    val p = progress.coerceIn(0f, 1f)
    if (points.size == 1 || p <= 0f) return points.first()
    if (p >= 1f) return points.last()

    val segLengths = (1 until points.size).map { i ->
        val a = points[i - 1]; val b = points[i]
        kotlin.math.hypot(b.lat - a.lat, b.lng - a.lng)
    }
    val total = segLengths.sum()
    if (total <= 0.0) return points.first()

    var target = total * p
    for (i in segLengths.indices) {
        val segLen = segLengths[i]
        if (target <= segLen || i == segLengths.lastIndex) {
            val t = if (segLen > 0) (target / segLen).coerceIn(0.0, 1.0) else 0.0
            val a = points[i]; val b = points[i + 1]
            return RoutePoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
        }
        target -= segLen
    }
    return points.last()
}

private fun effectiveLine(routePoints: List<RoutePoint>, start: RoutePoint?, finish: RoutePoint?): List<RoutePoint> =
    when {
        routePoints.size >= 2 -> routePoints
        start != null && finish != null -> listOf(start, finish)
        else -> emptyList()
    }

private fun drawRoute(
    kakaoMap: KakaoMap,
    routePoints: List<RoutePoint>,
    startPoint: RoutePoint?,
    finishPoint: RoutePoint?
) {
    val line = effectiveLine(routePoints, startPoint, finishPoint)
    if (line.isEmpty()) return

    val latLngs = line.map { LatLng.from(it.lat, it.lng) }

    runCatching {
        val routeLineManager = kakaoMap.routeLineManager
        val layer = routeLineManager?.layer
        layer?.removeAll()
        val styles = routeLineManager?.addStyles(
            RouteLineStyles.from(RouteLineStyle.from(14f, android.graphics.Color.parseColor("#BB87E3")))
        )
        val segment = RouteLineSegment.from(latLngs).setStyles(styles?.getStyles(0))
        layer?.addRouteLine(RouteLineOptions.from(segment))
    }

    runCatching {
        val labelManager = kakaoMap.labelManager
        val layer = labelManager?.layer
        layer?.removeAll()
        val start = startPoint ?: line.first()
        val finish = finishPoint ?: line.last()
        layer?.addLabel(
            LabelOptions.from(LatLng.from(start.lat, start.lng))
                .setStyles(LabelStyles.from(LabelStyle.from(android.R.drawable.presence_online)))
        )
        layer?.addLabel(
            LabelOptions.from(LatLng.from(finish.lat, finish.lng))
                .setStyles(LabelStyles.from(LabelStyle.from(android.R.drawable.presence_busy)))
        )
    }

    runCatching {
        val bounds = CameraUpdateFactory.fitMapPoints(latLngs.toTypedArray(), 80)
        kakaoMap.moveCamera(bounds)
    }
}
