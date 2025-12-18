plugins {
    kotlin("multiplatform")
    id("io.swiftify") version "0.1.0-SNAPSHOT"
}

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
            implementation(project(":swiftify-annotations"))
            implementation(project(":swiftify-runtime"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
    }
}

// Swiftify DSL configuration
swiftify {
    sealedClasses {
        transformToEnum(exhaustive = true)
    }
    suspendFunctions {
        transformToAsync(throwing = true)
    }
    flowTypes {
        transformToAsyncSequence()
    }
}
