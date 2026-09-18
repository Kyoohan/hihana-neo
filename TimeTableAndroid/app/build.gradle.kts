import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yhjang.timetable"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yhjang.timetable"
        minSdk = 26
        targetSdk = 37
        versionCode = 79
        versionName = "8.0"

        // 앱 내 업데이트가 최신 릴리스를 읽을 GitHub 저장소(owner/repo). gradle.properties 의 updateRepo 로 지정하며,
        // 비어 있으면 업데이트 확인을 건너뜁니다.
        val updateRepo = (project.findProperty("updateRepo") as? String).orEmpty()
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    // 릴리스 서명 — CI(또는 로컬)에서 환경 변수로 키스토어를 넘기면 그걸로, 없으면 디버그 키로 서명합니다.
    // 앱 내 업데이트는 같은 키로 서명된 APK 만 덮어쓸 수 있으므로, 배포용 키는 한 번 정하면 계속 같아야 합니다.
    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_KEYSTORE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("RELEASE_KEYSTORE").isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 실제 배경 블러(glassmorphism) — 하단 바·플로팅 아이콘 알약이 뒤 콘텐츠를 흐려 비춥니다 (API 31+, 그 아래는 반투명 폴백)
    implementation("dev.chrisbanes.haze:haze:1.5.3")
    implementation("dev.chrisbanes.haze:haze-materials:1.5.3")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
