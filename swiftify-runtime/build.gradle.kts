plugins {
    alias(libs.plugins.kotlinMultiplatform)
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
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
