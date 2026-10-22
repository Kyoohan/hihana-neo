import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.skh.hihanaevo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.skh.hihanaevo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "harness"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Forward -P<name>=<value> Gradle properties to the test JVM as system properties.
                listOf(
                    // RecordFramesTest (Compose -> PNG frames)
                    "framesDir", "startMs", "endMs", "fps", "qualifiers", "frameDelayExact",
                    // LottieRenderTest (lottie-android -> PNG frames)
                    "lottieJson", "lottieOut", "lottieBg", "lottieScale", "lottieWidth", "lottieHeight",
                    "lottieStartFrame", "lottieEndFrame", "lottieRenderMode", "lottieMergePaths", "evo.reference",
                ).forEach { name ->
                    project.findProperty(name)?.let { value ->
                        // Relative paths are resolved against the directory gradlew was started from.
                        val v = if (name in setOf("framesDir", "lottieJson", "lottieOut", "evo.reference")) {
                            gradle.startParameter.currentDir.resolve(value.toString()).normalize().absolutePath
                        } else value.toString()
                        test.systemProperty(name, v)
                    }
                }
                // Robolectric downloads android-all-instrumented itself at runtime (cached in ~/.m2/repository).
                // repo1.maven.org answers 429 through this proxy, so use Google's Maven Central mirror.
                test.systemProperty(
                    "robolectric.dependency.repo.url",
                    (project.findProperty("robolectricRepo")
                        ?: "https://maven-central.storage-download.googleapis.com/maven2").toString(),
                )
                test.systemProperty("java.awt.headless", "true")
                test.maxHeapSize = "4g"
                test.jvmArgs("-XX:+UseParallelGC")
                test.testLogging {
                    showStandardStreams = true
                    events("passed", "failed", "skipped")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
                // Rendering tests are side-effecting; never skip them as UP-TO-DATE.
                test.outputs.upToDateWhen { false }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // ---- JVM (Robolectric) rendering harness: test-only ----
    testImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest") // registers androidx.activity.ComponentActivity
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("com.airbnb.android:lottie:6.7.1")
}
