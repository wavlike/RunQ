package com.example.runq

import android.app.Application
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk
import java.security.MessageDigest

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
        logKeyHash()
    }

    // 지도가 MapAuthException(403)을 내며 안 뜰 때, 카카오 디벨로퍼스 콘솔에 등록해야 하는
    // 키 해시가 "지금 이 빌드를 서명한 인증서" 기준으로 정확히 뭔지 눈으로 바로 비교할 수 있게
    // Logcat에 찍어준다. 원인 확인용 임시 로그이며 배포에는 영향 없음(로그만 남기고 끝).
    private fun logKeyHash() {
        runCatching {
            @Suppress("DEPRECATION")
            val signatures = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            signatures?.forEach { signature ->
                val hash = Base64.encodeToString(
                    MessageDigest.getInstance("SHA").digest(signature.toByteArray()),
                    Base64.NO_WRAP
                )
                Log.d("RunQKeyHash", "카카오 콘솔에 등록해야 할 키 해시: $hash")
            }
        }.onFailure {
            Log.e("RunQKeyHash", "키 해시 계산 실패: ${it.message}")
        }
    }
}
