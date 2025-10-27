plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.x 开启 Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.clipmon2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.clipmon2"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    // 统一 Java 到 17（修复 “Java 1.8 vs Kotlin 17” 不一致）
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 统一 Kotlin 到 17
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 使用 JDK 17 工具链（避免本机 JDK 不一致）
kotlin {
    jvmToolchain(17)
}

dependencies {
    // XML 主题里用到的 Material / AppCompat（含 Theme.Material3.*）
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // Compose（BOM 管版本）
    implementation(platform("androidx.compose:compose-bom:2024.09.01"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
