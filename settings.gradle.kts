pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://download.osgeo.org/osgeo/download/osmdroid/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SkinDiseaseDetector"
include(":app")
