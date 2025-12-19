plugins {
    alias(libs.plugins.kotlinJvm)
    `maven-publish`
}

dependencies {
    implementation(libs.kotlin.stdlib)

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
