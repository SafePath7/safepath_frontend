// 폰(app)과 워치(wear)가 함께 쓰는 Data Layer 통신 계약(경로·키 상수)만 담는 모듈.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.safepath_test1.contract"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // 폰 앱의 minSdk(24)와 맞춘다. 워치(30)는 더 높아서 문제없다.
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
