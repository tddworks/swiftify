plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":swiftify-common"))
    api(project(":swiftify-dsl"))

    // KSP for Kotlin symbol processing
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.25")
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
