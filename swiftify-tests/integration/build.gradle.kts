plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.kotlin.stdlib)

    testImplementation(projects.swiftifyGenerator)
    testImplementation(projects.swiftifyAnalyzer)
    testImplementation(projects.swiftifySwift)
    testImplementation(projects.swiftifyDsl)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
