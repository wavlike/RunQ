package com.example.runq

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class RunQApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 카카오 개발자 콘솔(https://developers.kakao.com)에 등록한 네이티브 앱 키.
        // local.properties의 KAKAO_NATIVE_APP_KEY 값을 빌드 시점에 주입받습니다.
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}
