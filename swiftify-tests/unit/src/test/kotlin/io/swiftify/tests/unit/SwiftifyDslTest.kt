package io.swiftify.tests.unit

import io.swiftify.dsl.swiftify
import io.swiftify.dsl.SwiftifySpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for the Swiftify DSL.
 * This is the user-facing configuration that implements "Swift-first mental model".
 */
class SwiftifyDslTest {

    @Test
    fun `create empty swiftify spec with defaults`() {
        val spec = swiftify { }

        assertTrue(spec.sealedClassRules.isEmpty())
        assertTrue(spec.suspendFunctionRules.isEmpty())
        assertTrue(spec.defaults.transformSealedClassesToEnums)
        assertTrue(spec.defaults.transformSuspendToAsync)
    }

    @Test
    fun `configure sealed classes transformation`() {
        val spec = swiftify {
            sealedClasses {
                transformToEnum(exhaustive = true)
            }
        }

        assertTrue(spec.sealedClassRules.isNotEmpty())
        val rule = spec.sealedClassRules.first()
        assertTrue(rule.exhaustive)
    }

    @Test
    fun `configure specific kotlin class to swift enum`() {
        val spec = swiftify {
            "com.example.NetworkResult".toSwiftEnum {
                name = "NetworkResult"
                exhaustive = true
                conformTo("Hashable", "Codable")
            }
        }

        val mapping = spec.explicitMappings["com.example.NetworkResult"]
        assertEquals("NetworkResult", mapping?.swiftName)
        assertTrue(mapping?.conformances?.contains("Hashable") == true)
    }

    @Test
    fun `configure suspend functions transformation`() {
        val spec = swiftify {
            suspendFunctions {
                transformToAsync(throwing = true)
            }
        }

        assertTrue(spec.suspendFunctionRules.isNotEmpty())
        val rule = spec.suspendFunctionRules.first()
        assertTrue(rule.throwing)
    }

    @Test
    fun `configure flow transformation`() {
        val spec = swiftify {
            flowTypes {
                transformToAsyncSequence()
            }
        }

        assertTrue(spec.flowRules.isNotEmpty())
        assertTrue(spec.flowRules.first().useAsyncSequence)
    }

    @Test
    fun `configure default arguments`() {
        val spec = swiftify {
            defaults {
                maxDefaultArguments = 3
            }
        }

        assertEquals(3, spec.defaults.maxDefaultArguments)
    }

    @Test
    fun `configure package-specific rules`() {
        val spec = swiftify {
            inPackage("com.example.api") {
                sealedClasses {
                    transformToEnum(exhaustive = true)
                }
            }
        }

        val packageRule = spec.packageRules["com.example.api"]
        assertTrue(packageRule?.sealedClassRules?.isNotEmpty() == true)
    }

    @Test
    fun `disable specific transformations`() {
        val spec = swiftify {
            defaults {
                transformSealedClassesToEnums = false
                transformSuspendToAsync = false
            }
        }

        assertTrue(!spec.defaults.transformSealedClassesToEnums)
        assertTrue(!spec.defaults.transformSuspendToAsync)
    }

    @Test
    fun `explicit class mapping with case renaming`() {
        val spec = swiftify {
            "com.example.Result".toSwiftEnum {
                name = "AppResult"
                case("Success", "success")
                case("Failure", "failure")
                case("Loading", "loading")
            }
        }

        val mapping = spec.explicitMappings["com.example.Result"]
        assertEquals("AppResult", mapping?.swiftName)
        assertEquals(3, mapping?.caseRenamings?.size)
        assertEquals("success", mapping?.caseRenamings?.get("Success"))
    }

    @Test
    fun `type mapping for custom types`() {
        val spec = swiftify {
            typeMapping {
                "kotlin.ByteArray" mapTo "Data"
                "java.util.Date" mapTo "Date"
            }
        }

        assertEquals("Data", spec.typeMappings["kotlin.ByteArray"])
        assertEquals("Date", spec.typeMappings["java.util.Date"])
    }
}
