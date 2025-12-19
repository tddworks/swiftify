rootProject.name = "swiftify"

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
include(":swiftify-common")
include(":swiftify-dsl")
include(":swiftify-analyzer")
include(":swiftify-generator")
include(":swiftify-linker")
include(":swiftify-gradle-plugin")
include(":swiftify-runtime")

// Test modules (unit tests are in their respective module packages)
include(":swiftify-tests:integration")
include(":swiftify-tests:acceptance")

// Sample project
include(":sample")
