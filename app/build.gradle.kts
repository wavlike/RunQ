plugins {
    alias(libs.plugins.android.application)
    // 최신 Compose 컴파일러 플러그인 유지
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.runq"

    // API 36 설정 유지
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.runq"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }

    // ⚠️ [수정 포인트] 최신 kotlin.compose 플러그인을 쓸 때는
    // composeOptions 블록을 아예 적지 않거나 비워두어야 에러가 나지 않습니다.
}

dependencies {
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
    implementation(libs.google.maps.compose)
    implementation(libs.play.services.maps)

    testImplementation(libs.junit)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.00"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}