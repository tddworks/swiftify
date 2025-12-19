rootProject.name = "sample"

pluginManagement {
    // Include the main swiftify project to resolve the plugin
    includeBuild("..")

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    // Substitute swiftify dependencies with included build
    includeBuild("..") {
        dependencySubstitution {
            substitute(module("io.swiftify:swiftify-annotations")).using(project(":swiftify-annotations"))
        }
    }

    repositories {
        mavenCentral()
        google()
    }
}
