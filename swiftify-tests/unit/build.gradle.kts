plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))

    // Modules to test
    implementation(project(":swiftify-common"))
    implementation(project(":swiftify-dsl"))
    implementation(project(":swiftify-annotations"))
    implementation(project(":swiftify-generator"))

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation(kotlin("test"))
}
