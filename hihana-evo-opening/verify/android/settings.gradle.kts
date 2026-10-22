// Repository order: Google Maven (Android/AndroidX only) -> Google's mirror of Maven Central -> Maven Central -> Plugin Portal.
// The GCS mirror is first because repo.maven.apache.org / repo1.maven.org intermittently answer 429 through the proxy.
fun RepositoryHandler.harnessRepos() {
    google {
        content {
            includeGroupByRegex("com\\.android.*")
            includeGroupByRegex("com\\.google\\.android.*")
            includeGroupByRegex("androidx.*")
            includeGroup("com.google.testing.platform")
        }
    }
    maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralGcsMirror" }
    mavenCentral()
}

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.testing.platform")
            }
        }
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralGcsMirror" }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { harnessRepos() }
}

rootProject.name = "EvoHarness"
include(":app")
