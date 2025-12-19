package io.swiftify.analyzer

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Kotlin declaration analysis.
 * The analyzer extracts declarations from Kotlin code for transformation.
 */
class KotlinDeclarationAnalyzerTest {

    private val analyzer = KotlinDeclarationAnalyzer()

    @Test
    fun `analyze simple sealed class`() {
        val kotlinSource = """
            package com.example

            sealed class NetworkResult {
                data class Success(val data: String) : NetworkResult()
                data class Failure(val error: Throwable) : NetworkResult()
                object Loading : NetworkResult()
            }
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        assertEquals(1, declarations.size)
        val sealedClass = declarations[0] as SealedClassDeclaration
        assertEquals("com.example.NetworkResult", sealedClass.qualifiedName)
        assertEquals("NetworkResult", sealedClass.simpleName)
        assertEquals(3, sealedClass.subclasses.size)
    }

    @Test
    fun `analyze sealed class subclasses`() {
        val kotlinSource = """
            package com.example

            sealed class Result<T> {
                data class Success<T>(val value: T) : Result<T>()
                data class Error(val message: String) : Result<Nothing>()
            }
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        val sealedClass = declarations[0] as SealedClassDeclaration
        assertEquals(listOf("T"), sealedClass.typeParameters)

        val successSubclass = sealedClass.subclasses.find { it.simpleName == "Success" }!!
        assertEquals(1, successSubclass.properties.size)
        assertEquals("value", successSubclass.properties[0].name)

        val errorSubclass = sealedClass.subclasses.find { it.simpleName == "Error" }!!
        assertEquals("message", errorSubclass.properties[0].name)
    }

    @Test
    fun `analyze suspend function`() {
        val kotlinSource = """
            package com.example

            suspend fun fetchUser(id: Int): User {
                return api.getUser(id)
            }
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        assertEquals(1, declarations.size)
        val suspendFn = declarations[0] as SuspendFunctionDeclaration
        assertEquals("fetchUser", suspendFn.name)
        assertEquals(1, suspendFn.parameters.size)
        assertEquals("id", suspendFn.parameters[0].name)
        assertEquals("Int", suspendFn.parameters[0].typeName)
        assertEquals("User", suspendFn.returnTypeName)
    }

    @Test
    fun `analyze suspend function with multiple parameters`() {
        val kotlinSource = """
            package com.example

            suspend fun search(
                query: String,
                limit: Int = 10,
                offset: Int = 0
            ): List<Result>
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        val suspendFn = declarations[0] as SuspendFunctionDeclaration
        assertEquals(3, suspendFn.parameters.size)
        assertEquals("10", suspendFn.parameters[1].defaultValue)
        assertEquals("0", suspendFn.parameters[2].defaultValue)
    }

    @Test
    fun `analyze flow returning function`() {
        val kotlinSource = """
            package com.example

            fun observeUpdates(): Flow<Update> {
                return updatesChannel.receiveAsFlow()
            }
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        assertEquals(1, declarations.size)
        val flowFn = declarations[0] as FlowFunctionDeclaration
        assertEquals("observeUpdates", flowFn.name)
        assertEquals("Update", flowFn.elementTypeName)
    }

    @Test
    fun `analyze sealed class with SwiftEnum annotation`() {
        val kotlinSource = """
            package com.example

            @SwiftEnum(name = "AppResult", exhaustive = true)
            sealed class Result {
                data class Success(val data: String) : Result()
                object Failure : Result()
            }
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        val sealedClass = declarations[0] as SealedClassDeclaration
        assertTrue(sealedClass.hasSwiftEnumAnnotation)
        assertEquals("AppResult", sealedClass.swiftEnumName)
        assertTrue(sealedClass.isExhaustive)
    }

    @Test
    fun `analyze suspend function with SwiftDefaults annotation`() {
        val kotlinSource = """
            package com.example

            @SwiftDefaults
            suspend fun loadData(limit: Int = 10): Data
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        val suspendFn = declarations[0] as SuspendFunctionDeclaration
        assertTrue(suspendFn.hasSwiftAsyncAnnotation) // Field name kept for backwards compat
    }

    @Test
    fun `analyze suspend function with deprecated SwiftAsync annotation`() {
        val kotlinSource = """
            package com.example

            @SwiftAsync(throwing = true)
            suspend fun loadData(): Data
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        val suspendFn = declarations[0] as SuspendFunctionDeclaration
        assertTrue(suspendFn.hasSwiftAsyncAnnotation)
        assertTrue(suspendFn.isThrowing)
    }

    @Test
    fun `analyze multiple declarations in one file`() {
        val kotlinSource = """
            package com.example

            sealed class State {
                object Idle : State()
                data class Loading(val progress: Float) : State()
                data class Loaded(val data: String) : State()
            }

            suspend fun fetchState(): State

            fun observeState(): Flow<State>
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        assertEquals(3, declarations.size)
        assertTrue(declarations.any { it is SealedClassDeclaration })
        assertTrue(declarations.any { it is SuspendFunctionDeclaration })
        assertTrue(declarations.any { it is FlowFunctionDeclaration })
    }

    @Test
    fun `analyze object subclass of sealed class`() {
        val kotlinSource = """
            package com.example

            sealed class LoadingState {
                object Idle : LoadingState()
                object Loading : LoadingState()
                data class Error(val message: String) : LoadingState()
            }
        """.trimIndent()

        val declarations = analyzer.analyze(kotlinSource)

        val sealedClass = declarations[0] as SealedClassDeclaration
        val idleSubclass = sealedClass.subclasses.find { it.simpleName == "Idle" }!!
        assertTrue(idleSubclass.isObject)
        assertTrue(idleSubclass.properties.isEmpty())
    }
}
