plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":swiftify-common"))
    implementation(project(":swiftify-dsl"))
    implementation(project(":swiftify-analyzer"))
    implementation(project(":swiftify-generator"))
    implementation(project(":swiftify-linker"))

    compileOnly(gradleApi())

    // Test dependencies
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.9")
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
