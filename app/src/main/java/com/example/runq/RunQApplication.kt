package com.example.runq

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

// Kakao Maps SDK v2는 앱 전체에서 딱 한 번, Application.onCreate()에서 초기화한다.
// 네이티브 앱 키는 local.properties → BuildConfig.KAKAO_NATIVE_APP_KEY로 주입됨.
class RunQApplication : Application() {
    companion object {
        lateinit var instance: RunQApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 에뮬레이터(특히 x86_64)에서 카카오 맵 라이브러리 로드 실패 시 앱이 죽는 것을 방지.
        // 라이브러리가 없으면 지도는 안 보이지만 다른 기능(GPS 트래킹 등)은 테스트 가능합니다.
        runCatching {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }.onFailure {
            android.util.Log.e("RunQApplication", "KakaoMapSdk 초기화 실패 (아키텍처 미지원 가능성): ${it.message}")
        }
    }
}
