plugins {
    alias(libs.plugins.kotlinJvm)
    `maven-publish`
}

dependencies {
    implementation(libs.kotlin.stdlib)
    api(projects.swiftifySwift)
    api(projects.swiftifyGenerator)

    // Test dependencies
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
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
