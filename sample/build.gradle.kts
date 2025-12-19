plugins {
    kotlin("multiplatform") version "2.3.0"
    id("io.swiftify")
}

group = "io.swiftify.sample"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvm()

    // Apple targets with framework configuration
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64()
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "SampleKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.swiftify:swiftify-annotations:0.1.0-SNAPSHOT")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
    }
}

// Swiftify DSL configuration
// Note: frameworkName is auto-detected from KMP's baseName = "SampleKit"
// Note: Kotlin 2.0+ already exports suspend functions as Swift async/await automatically.
swiftify {
    sealedClasses {
        transformToEnum(exhaustive = true)
    }
    defaultParameters {
        generateOverloads(maxOverloads = 5)
    }
    flowTypes {
        transformToAsyncStream()
    }
}
