import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // 최신 Compose 컴파일러 플러그인 유지
    alias(libs.plugins.kotlin.compose)
}

// ────────────────────────────────────────────────
// local.properties (git에 안 올라감)에서 API 키를 읽어 BuildConfig로 노출.
// 소스코드에 키를 직접 박아두지 않기 위함 — local.properties에 아래 키를 채워주세요:
//   KAKAO_NATIVE_APP_KEY=...   (Kakao Maps SDK 지도 표시용)
//   KAKAO_REST_API_KEY=...    (Kakao Local 장소검색 보완용, 아직 없으면 비워둬도 빌드는 됨)
//   TOUR_API_KEY=...          (한국관광공사 TourAPI 서비스키)
// ────────────────────────────────────────────────
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key) ?: ""

android {
    namespace = "com.example.runq"

    // API 36 설정 유지
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.runq.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${localProp("KAKAO_NATIVE_APP_KEY")}\"")
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${localProp("KAKAO_REST_API_KEY")}\"")
        buildConfigField("String", "TOUR_API_KEY", "\"${localProp("TOUR_API_KEY")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ⚠️ [수정 포인트] 최신 kotlin.compose 플러그인을 쓸 때는
    // composeOptions 블록을 아예 적지 않거나 비워두어야 에러가 나지 않습니다.
}

dependencies {

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // 안정적인 Compose BOM 버전으로 변경 (2024년 4월 버전이 가장 무난합니다)
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    // Maps
    implementation(libs.kakao.maps)

    testImplementation(libs.junit)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.00"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}