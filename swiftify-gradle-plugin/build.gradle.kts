plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(projects.swiftifySwift)
    implementation(projects.swiftifyDsl)
    implementation(projects.swiftifyAnalyzer)
    implementation(projects.swiftifyGenerator)
    implementation(projects.swiftifyLinker)

    compileOnly(gradleApi())

    // Test dependencies
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("swiftify") {
            id = "io.swiftify"
            implementationClass = "io.swiftify.gradle.SwiftifyPlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
