plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)
    alias(libs.plugins.version.catalog.update)
}

allprojects {
    group = "io.swiftify"
    version = "0.1.0-SNAPSHOT"
}

// Kover code coverage aggregation
dependencies {
    kover(projects.swiftifyAnnotations)
    kover(projects.swiftifySwift)
    kover(projects.swiftifyDsl)
    kover(projects.swiftifyAnalyzer)
    kover(projects.swiftifyGenerator)
    kover(projects.swiftifyLinker)
    kover(projects.swiftifyGradlePlugin)
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "io.swiftify.**.*\$*$*",
                    "io.swiftify.**.*\$Companion",
                    "*.*\$\$serializer",
                )
            }
            includes {
                classes("io.swiftify.*")
            }
        }
        verify {
            rule {
                bound {
                    minValue = 45 // Current: 47.8%
                }
            }
        }
    }
}

// Spotless code formatting
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_filename" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
            ),
        )
    }
}

subprojects {
    apply(plugin = rootProject.libs.plugins.kover.get().pluginId)

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
