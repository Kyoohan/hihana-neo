import java.util.Properties

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.skh.hihanaevo"
    compileSdk = 37

    defaultConfig {
        // 설치 ID 는 하이하나 Neo 와 같은 값 — 기존 사용자가 앱 내 업데이트로 Evo 를 받고 로그인·설정이 그대로 남습니다
        // (패키지 이름이 다르면 안드로이드는 다른 앱으로 봐 새로 깔아야 했습니다). 코드의 패키지(namespace)는 Evo 이름.
        applicationId = "com.yhjang.timetable"
        minSdk = 26
        targetSdk = 37
        versionCode = 140
        versionName = "14.0"

        // 앱 내 업데이트가 최신 릴리스를 읽을 GitHub 저장소(owner/repo). gradle.properties 의 updateRepo 로 지정하며,
        // 비어 있으면 업데이트 확인을 건너뜁니다.
        val updateRepo = (project.findProperty("updateRepo") as? String).orEmpty()
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("int", "DEV_BUILD", "0")
        // NEIS 급식 API 인증키 — CI 는 환경변수(NEIS_KEY 시크릿), 로컬은 local.properties 에서. 저장소에는 두지 않습니다.
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val neisKey = System.getenv("NEIS_KEY")?.takeIf { it.isNotBlank() } ?: localProps.getProperty("NEIS_KEY").orEmpty()
        buildConfigField("String", "NEIS_KEY", "\"$neisKey\"")
        // 개발 빌드인지 — BuildConfig.DEBUG 는 디버그 가능 여부를 따르는데 개발 빌드도 디버그 불가로 빌드하므로(아래) 따로 둡니다.
        buildConfigField("boolean", "DEV", "false")
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
        // 디버그 빌드는 패키지 이름을 달리 해 릴리스 앱과 나란히 설치됩니다 ("HiHana Evo (dev)") —
        // 서명이 달라 매번 지우고 다시 깔던 불편을 없애기 위함. 데이터(로그인·설정·위젯)는 서로 따로이고,
        // 앱 내 업데이트는 끕니다 (릴리스 APK 를 dev 위에 덮을 수 없으므로).
        debug {
            // 설치 ID 는 아래 androidComponents 에서 com.skh.hihanaevo.dev 로.
            versionNameSuffix = "-dev"
            // 개발자 빌드 번호(gradle.properties 의 devBuild) — 새로 빌드해 설치할 때마다 1씩.
            buildConfigField("int", "DEV_BUILD", (project.findProperty("devBuild") as? String ?: "0"))
            buildConfigField("boolean", "DEV", "true")
            resValue("string", "app_name", "HiHana Evo (dev)")
            // 디버그 가능 앱은 ART 가 최적화를 끄고 돌려 같은 코드가 출시 빌드보다 2~3배 느렸습니다(시트 처음 열기 60~90ms →
            // 20~35ms, 프레임당 메인 스레드 4.7 → 2.3ms, S25+). 느낌을 출시 빌드와 맞추려고 끕니다 — 디버거 붙이기·run-as 는
            // 안 되고, 성능 측정은 profileable(src/debug 매니페스트)로.
            isDebuggable = false
            buildConfigField("String", "UPDATE_REPO", "\"\"")
        }
        release {
            resValue("string", "app_name", "HiHana Evo")
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
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


// 개발 빌드는 Evo 로 이름을 바꾼 뒤의 설치 ID(com.skh.hihanaevo.dev) 그대로 — 폰에 깔린 dev 앱의 로그인·설정을 계속 씁니다.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.skh.hihanaevo.dev")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    // 버전은 실제로 들어가는 값에 맞춥니다 — Compose BOM 이 더 새 버전을 끌어와 예전 숫자(1.15·1.9.3·2.8.7)는 쓰이지 않았습니다.
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    // 디버그 빌드만: Perfetto 추적에 컴포저블 이름이 찍히게(재구성 병목 찾기용). 릴리스엔 들어가지 않습니다.
    debugImplementation("androidx.compose.runtime:runtime-tracing:1.7.8")
    debugImplementation("androidx.tracing:tracing-perfetto:1.0.0")
    debugImplementation("androidx.tracing:tracing-perfetto-binary:1.0.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
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

    // 게시글 본문 웹뷰를 다크 모드에서 어둡게 칠합니다.
    implementation("androidx.webkit:webkit:1.14.0")
}
