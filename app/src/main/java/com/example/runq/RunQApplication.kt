package com.example.runq

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

// Kakao Maps SDK v2는 앱 전체에서 딱 한 번, Application.onCreate()에서 초기화한다.
// 네이티브 앱 키는 local.properties → BuildConfig.KAKAO_NATIVE_APP_KEY로 주입됨.
class RunQApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}
