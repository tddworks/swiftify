plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(project(":swiftify-swift"))
    testImplementation(project(":swiftify-generator"))
    testImplementation(project(":swiftify-analyzer"))
    testImplementation(project(":swiftify-gradle-plugin"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}
