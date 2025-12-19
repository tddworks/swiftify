plugins {
    alias(libs.plugins.kotlinJvm)
    `maven-publish`
}

dependencies {
    implementation(libs.kotlin.stdlib)
    api(projects.swiftifySwift)

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
