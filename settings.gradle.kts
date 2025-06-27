pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "ImpactSuite"
include(":app")
include(":edge_ai")
include(":data")
include(":feature_caption")
include(":feature_quiz")
include(":feature_cbt")
include(":feature_plant")
include(":feature_crisis")
include(":common_ui")
include(":common_utils")

 