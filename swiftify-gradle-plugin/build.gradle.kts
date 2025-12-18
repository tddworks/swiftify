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
