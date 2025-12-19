rootProject.name = "swiftify"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// Core modules
include(":swiftify-annotations")
include(":swiftify-swift")
include(":swiftify-dsl")
include(":swiftify-analyzer")
include(":swiftify-generator")
include(":swiftify-linker")
include(":swiftify-gradle-plugin")

// Test modules (unit tests are in their respective module packages)
include(":swiftify-tests:integration")
include(":swiftify-tests:acceptance")

// Note: sample/ is a standalone project with its own settings.gradle.kts
// Build it separately: cd sample && ./gradlew build
