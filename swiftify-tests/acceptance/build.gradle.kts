plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.kotlin.stdlib)

    testImplementation(projects.swiftifySwift)
    testImplementation(projects.swiftifyGenerator)
    testImplementation(projects.swiftifyAnalyzer)
    testImplementation(projects.swiftifyGradlePlugin)
    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
