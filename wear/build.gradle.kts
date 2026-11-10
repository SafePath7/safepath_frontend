plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.safepath_test1.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId =
            "com.example.safepath_test1" // matches phone module - native Data Layer node discovery
        // experiment
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    constraints {
        implementation("androidx.fragment:fragment:1.3.6") {
            because("Activity Result requires Fragment 1.3.0+; Play services requests 1.1.0")
        }
    }
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
