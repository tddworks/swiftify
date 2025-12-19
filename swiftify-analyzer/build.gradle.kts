plugins {
    alias(libs.plugins.kotlinJvm)
    `maven-publish`
}

dependencies {
    implementation(libs.kotlin.stdlib)
    api(projects.swiftifySwift)
    api(projects.swiftifyDsl)

    // KSP for Kotlin symbol processing
    implementation(libs.ksp.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
