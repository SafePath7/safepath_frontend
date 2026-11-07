import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services) // 파이어베이
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }

val mapboxAccessToken =
    providers
        .gradleProperty("MAPBOX_ACCESS_TOKEN")
        .orElse(localProperties.getProperty("MAPBOX_ACCESS_TOKEN") ?: "")
        .get()
        .trim()
        .replace("\"", "")

val kakaoRestApiKey =
    providers
        .gradleProperty("KAKAO_REST_API_KEY")
        .orElse(localProperties.getProperty("KAKAO_REST_API_KEY") ?: "")
        .get()
        .trim()
        .replace("\"", "")

val kakaoNativeAppKey =
    providers
        .gradleProperty("KAKAO_NATIVE_APP_KEY")
        .orElse(localProperties.getProperty("KAKAO_NATIVE_APP_KEY") ?: "")
        .get()
        .trim()
        .replace("\"", "")

val friendsApiBaseUrl =
    providers
        .gradleProperty("FRIENDS_API_BASE_URL")
        .orElse(localProperties.getProperty("FRIENDS_API_BASE_URL") ?: "")
        .get()
        .trim()
        .trimEnd('/')

// 디버그 전용 로컬 테스트 설정. 운영 빌드에는 전달하지 않는다.
val testServerUrl = localProperties.getProperty("SAFEPATH_TEST_URL", "").trim()
val testServerToken = localProperties.getProperty("SAFEPATH_TEST_TOKEN", "").trim()
require(testServerUrl.matches(Regex("[A-Za-z0-9:/._-]*"))) { "Invalid SAFEPATH_TEST_URL" }
require(testServerToken.matches(Regex("[A-Za-z0-9_-]*"))) { "Invalid SAFEPATH_TEST_TOKEN" }

android {
    namespace = "com.example.safepath_test1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.safepath_test1"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "mapbox_access_token", mapboxAccessToken)
        resValue("string", "kakao_rest_api_key", kakaoRestApiKey)
        resValue("string", "kakao_native_app_key", kakaoNativeAppKey)
        resValue("string", "friends_api_base_url", friendsApiBaseUrl)
    }

    buildTypes {
        debug {
            resValue("string", "safepath_test_url", testServerUrl)
            resValue("string", "safepath_test_token", testServerToken)
        }
        release {
            resValue("string", "safepath_test_url", "")
            resValue("string", "safepath_test_token", "")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.kakao.maps.open:android:2.15.2")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    // 파이어베이스
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // 구글 로그인 의존성 추가
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
}
