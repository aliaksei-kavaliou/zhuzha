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
        // Boox / Onyx pen SDK (com.onyx.android.sdk:onyxsdk-pen). HTTPS
        // works so no isAllowInsecureProtocol needed.
        maven("https://repo.boox.com/repository/maven-public/")
    }
}

rootProject.name = "InkFirst"
include(":app", ":core")
