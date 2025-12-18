plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(project(":swiftify-generator"))
    testImplementation(project(":swiftify-analyzer"))
    testImplementation(project(":swiftify-common"))
    testImplementation(project(":swiftify-dsl"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(kotlin("test"))
}
