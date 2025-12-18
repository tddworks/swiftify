plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain.dependencies {
            // No dependencies needed for annotations
        }
    }
}
