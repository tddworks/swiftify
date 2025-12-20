package io.swiftify.generator

import io.swiftify.dsl.swiftify
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for the complete transformation pipeline.
 * Analyzer → Transformer → Generator
 */
class SwiftifyTransformerTest {
    private val transformer = SwiftifyTransformer()

    @Test
    fun `transform sealed class to swift enum`() {
        val kotlinSource =
            """
            package com.example

            sealed class NetworkResult {
                data class Success(val data: String) : NetworkResult()
                data class Failure(val error: Throwable) : NetworkResult()
                object Loading : NetworkResult()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "public enum NetworkResult")
        assertContains(result.swiftCode, "case success(data: String)")
        assertContains(result.swiftCode, "case failure(error: Error)")
        assertContains(result.swiftCode, "case loading")
    }

    @Test
    fun `transform suspend function to async with annotation`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun fetchUser(id: Int): User
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        // Kotlin Int maps to Swift Int32 in Kotlin/Native
        assertContains(result.swiftCode, "public func fetchUser(id: Int32) async throws -> User")
    }

    @Test
    fun `transform suspend function with DSL mode`() {
        val kotlinSource =
            """
            package com.example

            suspend fun fetchUser(id: Int): User
            """.trimIndent()

        // DSL mode - process all functions without annotations
        val config =
            swiftify {
                defaults {
                    requireAnnotations = false
                }
            }
        val result = transformer.transform(kotlinSource, config)

        assertContains(result.swiftCode, "public func fetchUser(id: Int32) async throws -> User")
    }

    @Test
    fun `transform with custom configuration`() {
        val kotlinSource =
            """
            package com.example

            sealed class State {
                object Idle : State()
                data class Loading(val progress: Float) : State()
            }
            """.trimIndent()

        val config =
            swiftify {
                sealedClasses {
                    transformToEnum(exhaustive = true)
                    conformTo("Hashable")
                }
            }

        val result = transformer.transform(kotlinSource, config)

        assertContains(result.swiftCode, "@frozen")
        assertContains(result.swiftCode, "Hashable")
    }

    @Test
    fun `transform annotated class with custom name`() {
        val kotlinSource =
            """
            package com.example

            @SwiftEnum(name = "AppResult", exhaustive = true)
            sealed class Result {
                data class Success(val value: String) : Result()
                object Failure : Result()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "public enum AppResult")
    }

    @Test
    fun `transform multiple declarations with DSL mode`() {
        val kotlinSource =
            """
            package com.example

            sealed class State {
                object Idle : State()
                object Loading : State()
            }

            suspend fun loadState(): State

            fun observeState(): Flow<State>
            """.trimIndent()

        // DSL mode - process all functions without annotations
        val config =
            swiftify {
                defaults {
                    requireAnnotations = false
                }
            }
        val result = transformer.transform(kotlinSource, config)

        // Should generate enum
        assertContains(result.swiftCode, "public enum State")

        // Should generate async function
        assertContains(result.swiftCode, "func loadState() async")
    }

    @Test
    fun `result contains declaration count`() {
        val kotlinSource =
            """
            package com.example

            sealed class A { object X : A() }
            sealed class B { object Y : B() }
            @SwiftDefaults
            suspend fun foo(): String
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed >= 3)
    }

    @Test
    fun `transform generic sealed class`() {
        val kotlinSource =
            """
            package com.example

            sealed class Result<T> {
                data class Success<T>(val value: T) : Result<T>()
                data class Error(val message: String) : Result<Nothing>()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "public enum Result<T>")
        assertContains(result.swiftCode, "case success(value: T)")
    }

    // ============================================================
    // Type Mapping Tests
    // ============================================================

    @Test
    fun `transform maps Kotlin Int to Swift Int32`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun calculate(value: Int): Int
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        // Kotlin Int maps to Swift Int32 in Kotlin/Native interop
        assertContains(result.swiftCode, "Int32")
    }

    @Test
    fun `transform maps Kotlin Long to Swift Int64`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun processLong(value: Long): Long
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "Int64")
    }

    @Test
    fun `transform maps Kotlin Boolean to Swift Bool`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun toggle(flag: Boolean): Boolean
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "Bool")
    }

    @Test
    fun `transform maps Kotlin List to Swift Array`() {
        val kotlinSource =
            """
            package com.example

            sealed class Container {
                data class Items(val items: List<String>) : Container()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        // List<String> should map to [String]
        assertContains(result.swiftCode, "[String]")
    }

    @Test
    fun `transform maps nullable types to optional`() {
        val kotlinSource =
            """
            package com.example

            sealed class Wrapper {
                data class Optional(val value: String?) : Wrapper()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "String?")
    }

    @Test
    fun `transform maps Unit to Void implicitly`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun doWork(): Unit
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        // Unit return type should not show Void explicitly in signature
        assertTrue(result.swiftCode.contains("func doWork()"))
    }

    // ============================================================
    // Implementation Mode Tests
    // ============================================================

    @Test
    fun `transform with generateImplementations creates extension blocks`() {
        val kotlinSource =
            """
            package com.example

            class Repository {
                @SwiftDefaults
                suspend fun getData(id: Int = 1): String
            }
            """.trimIndent()

        val options = TransformOptions(generateImplementations = true)
        val result = transformer.transform(kotlinSource, options)

        assertContains(result.swiftCode, "extension Repository")
    }

    @Test
    fun `transform without generateImplementations creates signatures only`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun fetchData(id: Int = 1): Data
            """.trimIndent()

        val options = TransformOptions(generateImplementations = false)
        val result = transformer.transform(kotlinSource, options)

        // Should have signature but not implementation details
        assertContains(result.swiftCode, "func fetchData")
    }

    @Test
    fun `transform groups functions by containing class`() {
        val kotlinSource =
            """
            package com.example

            class UserRepository {
                @SwiftDefaults
                suspend fun getUser(id: Int = 0): User

                @SwiftDefaults
                suspend fun saveUser(user: String = "default"): Boolean
            }
            """.trimIndent()

        val options = TransformOptions(generateImplementations = true)
        val result = transformer.transform(kotlinSource, options)

        // Both functions should be in same extension block
        val extensionCount = "extension UserRepository".toRegex().findAll(result.swiftCode).count()
        assertEquals(1, extensionCount, "Expected single extension block for UserRepository")
    }

    // ============================================================
    // Flow Function Tests
    // ============================================================

    @Test
    fun `transform Flow function with SwiftFlow annotation`() {
        val kotlinSource =
            """
            package com.example

            @SwiftFlow
            fun observeData(): Flow<Data>
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertContains(result.swiftCode, "AsyncStream")
    }

    @Test
    fun `transform Flow function without annotation when requireAnnotations is false`() {
        val kotlinSource =
            """
            package com.example

            fun observeData(): Flow<Data>
            """.trimIndent()

        val config = swiftify {
            defaults {
                requireAnnotations = false
            }
        }
        val result = transformer.transform(kotlinSource, config)

        assertContains(result.swiftCode, "AsyncStream")
    }

    @Test
    fun `transform Flow function skipped when requireAnnotations is true and no annotation`() {
        val kotlinSource =
            """
            package com.example

            fun observeData(): Flow<Data>
            """.trimIndent()

        val config = swiftify {
            defaults {
                requireAnnotations = true
            }
        }
        val result = transformer.transform(kotlinSource, config)

        assertEquals(0, result.declarationsTransformed)
    }

    // ============================================================
    // Configuration Tests
    // ============================================================

    @Test
    fun `transform respects requireAnnotations for functions`() {
        val kotlinSource =
            """
            package com.example

            suspend fun unannotatedFunction(): String
            """.trimIndent()

        val configRequiring = swiftify {
            defaults {
                requireAnnotations = true
            }
        }

        val result = transformer.transform(kotlinSource, configRequiring)

        // Without @SwiftDefaults and requireAnnotations=true, should not transform
        assertEquals(0, result.declarationsTransformed)
    }

    @Test
    fun `transform respects generateDefaultOverloads config`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun withDefaults(a: Int, b: Int = 1): String
            """.trimIndent()

        val configDisabled = swiftify {
            defaults {
                generateDefaultOverloads = false
            }
        }

        val result = transformer.transform(kotlinSource, configDisabled)

        // With generateDefaultOverloads=false, should not generate
        assertEquals(0, result.declarationsTransformed)
    }

    @Test
    fun `transform respects transformFlowToAsyncStream config`() {
        val kotlinSource =
            """
            package com.example

            @SwiftFlow
            fun observe(): Flow<String>
            """.trimIndent()

        val configDisabled = swiftify {
            defaults {
                transformFlowToAsyncStream = false
            }
        }

        val result = transformer.transform(kotlinSource, configDisabled)

        assertEquals(0, result.declarationsTransformed)
    }

    @Test
    fun `transform respects transformSealedClassesToEnums config`() {
        val kotlinSource =
            """
            package com.example

            sealed class State {
                object Idle : State()
            }
            """.trimIndent()

        val configDisabled = swiftify {
            defaults {
                transformSealedClassesToEnums = false
            }
        }

        val result = transformer.transform(kotlinSource, configDisabled)

        assertEquals(0, result.declarationsTransformed)
    }

    // ============================================================
    // TransformResult Tests
    // ============================================================

    @Test
    fun `transform result contains analyzed declarations`() {
        val kotlinSource =
            """
            package com.example

            sealed class State {
                object Idle : State()
            }

            @SwiftDefaults
            suspend fun loadState(): State
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarations.isNotEmpty())
        assertTrue(result.declarations.any { it.simpleName == "State" })
        assertTrue(result.declarations.any { it.simpleName == "loadState" })
    }

    @Test
    fun `transform result swiftCode is empty when no transformations`() {
        val kotlinSource =
            """
            package com.example

            // Just a regular function, no annotations
            fun regularFunction(): String = "hello"
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.swiftCode.isBlank() || result.declarationsTransformed == 0)
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @Test
    fun `transform handles empty source`() {
        val result = transformer.transform("")

        assertEquals(0, result.declarationsTransformed)
        assertTrue(result.declarations.isEmpty())
    }

    @Test
    fun `transform handles source with only comments`() {
        val kotlinSource =
            """
            // This is just a comment
            /* Multi-line
               comment */
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertEquals(0, result.declarationsTransformed)
    }

    @Test
    fun `transform preserves package info in declarations`() {
        val kotlinSource =
            """
            package com.example.feature.subfeature

            sealed class DeepPackageClass {
                object State : DeepPackageClass()
            }
            """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarations.any {
            it.packageName == "com.example.feature.subfeature"
        })
    }

    @Test
    fun `transform with default configuration processes annotated items`() {
        val kotlinSource =
            """
            package com.example

            @SwiftDefaults
            suspend fun annotatedFunction(x: Int = 1): String

            suspend fun unannotatedFunction(): String
            """.trimIndent()

        // Default config requires annotations
        val result = transformer.transform(kotlinSource)

        // Should only process annotated function
        assertTrue(result.declarationsTransformed >= 1)
    }

    @Test
    fun `transformDeclarations with pre-analyzed declarations works`() {
        val declarations = listOf(
            io.swiftify.analyzer.SealedClassDeclaration(
                qualifiedName = "com.example.State",
                simpleName = "State",
                packageName = "com.example",
                typeParameters = emptyList(),
                subclasses = listOf(
                    io.swiftify.analyzer.SealedSubclass(
                        simpleName = "Idle",
                        qualifiedName = "com.example.State.Idle",
                        isObject = true,
                        isDataClass = false,
                        properties = emptyList(),
                    )
                ),
            )
        )

        val options = TransformOptions(generateImplementations = false)
        val result = transformer.transformDeclarations(declarations, options)

        assertContains(result.swiftCode, "enum State")
    }

    @Test
    fun `transform skips sealed classes in REGEX implementation mode`() {
        val kotlinSource =
            """
            package com.example

            sealed class State {
                object Idle : State()
            }
            """.trimIndent()

        // REGEX mode with generateImplementations = true should skip sealed classes
        // because Kotlin/Native already exports them
        val options = TransformOptions(
            generateImplementations = true,
            includeSealedClasses = false,
        )
        val result = transformer.transform(kotlinSource, options)

        // Sealed class should be skipped (Kotlin/Native handles it)
        assertEquals(0, result.declarationsTransformed)
    }

    @Test
    fun `transform includes sealed classes when includeSealedClasses is true`() {
        val kotlinSource =
            """
            package com.example

            sealed class State {
                object Idle : State()
            }
            """.trimIndent()

        val options = TransformOptions(
            generateImplementations = true,
            includeSealedClasses = true,
        )
        val result = transformer.transform(kotlinSource, options)

        assertContains(result.swiftCode, "enum State")
    }
}
