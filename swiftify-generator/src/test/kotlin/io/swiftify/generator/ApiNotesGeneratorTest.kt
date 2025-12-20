package io.swiftify.generator

import io.swiftify.analyzer.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for ApiNotes (.apinotes) file generation.
 *
 * ApiNotes are YAML files that customize how Kotlin/Native frameworks are imported into Swift.
 * @see https://clang.llvm.org/docs/APINotes.html
 */
class ApiNotesGeneratorTest {
    private val generator = ApiNotesGenerator()

    // ============================================================
    // Basic Structure Tests
    // ============================================================

    @Test
    fun `generate returns valid YAML structure with framework name`() {
        val result = generator.generate("TestFramework", emptyList())

        assertContains(result, "---")
        assertContains(result, "Name: TestFramework")
        assertContains(result, "Classes:")
    }

    @Test
    fun `generate with empty declarations produces minimal YAML`() {
        val result = generator.generate("SharedKit", emptyList())

        assertTrue(result.startsWith("---"))
        assertContains(result, "Name: SharedKit")
    }

    // ============================================================
    // Sealed Class Notes Tests
    // ============================================================

    @Test
    fun `generate sealed class produces correct YAML structure`() {
        val sealedClass = SealedClassDeclaration(
            qualifiedName = "com.example.NetworkResult",
            simpleName = "NetworkResult",
            packageName = "com.example",
            typeParameters = emptyList(),
            subclasses = listOf(
                SealedSubclass(
                    simpleName = "Success",
                    qualifiedName = "com.example.NetworkResult.Success",
                    isObject = false,
                    isDataClass = true,
                    properties = listOf(
                        PropertyDeclaration(name = "data", typeName = "String", isNullable = false),
                    ),
                ),
                SealedSubclass(
                    simpleName = "Loading",
                    qualifiedName = "com.example.NetworkResult.Loading",
                    isObject = true,
                    isDataClass = false,
                    properties = emptyList(),
                ),
            ),
        )

        val result = generator.generate("SharedKit", listOf(sealedClass))

        assertContains(result, "Name: NetworkResult")
        assertContains(result, "SwiftName: NetworkResult")
        assertContains(result, "SwiftBridge: NetworkResult")
    }

    @Test
    fun `generate sealed class with custom swift name uses annotation name`() {
        val sealedClass = SealedClassDeclaration(
            qualifiedName = "com.example.Result",
            simpleName = "Result",
            packageName = "com.example",
            typeParameters = emptyList(),
            subclasses = emptyList(),
            hasSwiftEnumAnnotation = true,
            swiftEnumName = "AppResult",
        )

        val result = generator.generate("SharedKit", listOf(sealedClass))

        assertContains(result, "SwiftName: AppResult")
        assertContains(result, "SwiftBridge: AppResult")
    }

    @Test
    fun `generate sealed class includes subclass mappings as comments`() {
        val sealedClass = SealedClassDeclaration(
            qualifiedName = "com.example.State",
            simpleName = "State",
            packageName = "com.example",
            typeParameters = emptyList(),
            subclasses = listOf(
                SealedSubclass(
                    simpleName = "Loading",
                    qualifiedName = "com.example.State.Loading",
                    isObject = true,
                    isDataClass = false,
                    properties = emptyList(),
                ),
                SealedSubclass(
                    simpleName = "Error",
                    qualifiedName = "com.example.State.Error",
                    isObject = false,
                    isDataClass = true,
                    properties = listOf(
                        PropertyDeclaration(name = "message", typeName = "String", isNullable = false),
                    ),
                ),
            ),
        )

        val result = generator.generate("SharedKit", listOf(sealedClass))

        // Subclass mappings should be documented as comments
        assertContains(result, "# Loading -> .loading")
        assertContains(result, "# Error -> .error")
    }

    // ============================================================
    // Function Declaration Notes Tests
    // ============================================================

    @Test
    fun `generate function produces method notes with selector`() {
        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.fetchUser",
            simpleName = "fetchUser",
            packageName = "com.example",
            name = "fetchUser",
            parameters = listOf(
                ParameterDeclaration(name = "id", typeName = "String", isNullable = false),
            ),
            returnTypeName = "User",
            hasSwiftDefaultsAnnotation = true,
            isThrowing = true,
            isSuspend = true,
        )

        val result = generator.generate("SharedKit", listOf(function))

        assertContains(result, "Selector:")
        assertContains(result, "SwiftName:")
    }

    @Test
    fun `generate function with no parameters produces simple selector`() {
        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.ping",
            simpleName = "ping",
            packageName = "com.example",
            name = "ping",
            parameters = emptyList(),
            returnTypeName = "String",
            isThrowing = true,
            isSuspend = true,
        )

        val result = generator.generate("SharedKit", listOf(function))

        assertContains(result, "Selector: \"ping\"")
        assertContains(result, "SwiftName: \"ping()\"")
    }

    @Test
    fun `generate function with single parameter produces correct selector`() {
        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.getUser",
            simpleName = "getUser",
            packageName = "com.example",
            name = "getUser",
            parameters = listOf(
                ParameterDeclaration(name = "id", typeName = "Int", isNullable = false),
            ),
            returnTypeName = "User",
            isThrowing = true,
            isSuspend = true,
        )

        val result = generator.generate("SharedKit", listOf(function))

        // ObjC selector format: methodNameWithFirstParam:
        assertContains(result, "Selector: \"getUserWithId:\"")
        assertContains(result, "SwiftName: \"getUser(id:)\"")
    }

    @Test
    fun `generate function with multiple parameters produces correct selector`() {
        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.search",
            simpleName = "search",
            packageName = "com.example",
            name = "search",
            parameters = listOf(
                ParameterDeclaration(name = "query", typeName = "String", isNullable = false),
                ParameterDeclaration(name = "limit", typeName = "Int", isNullable = false),
                ParameterDeclaration(name = "offset", typeName = "Int", isNullable = false),
            ),
            returnTypeName = "List<Result>",
            isThrowing = true,
            isSuspend = true,
        )

        val result = generator.generate("SharedKit", listOf(function))

        // ObjC selector: methodNameWithQuery:limit:offset:
        assertContains(result, "Selector: \"searchWithQuery:limit:offset:\"")
        assertContains(result, "SwiftName: \"search(query:,limit:,offset:)\"")
    }

    @Test
    fun `generate throwing function includes throws comment`() {
        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.fetchData",
            simpleName = "fetchData",
            packageName = "com.example",
            name = "fetchData",
            parameters = emptyList(),
            returnTypeName = "Data",
            isThrowing = true,
            isSuspend = true,
        )

        val result = generator.generate("SharedKit", listOf(function))

        assertContains(result, "# Throws errors")
    }

    @Test
    fun `generate non-throwing function does not include throws comment`() {
        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.getValue",
            simpleName = "getValue",
            packageName = "com.example",
            name = "getValue",
            parameters = emptyList(),
            returnTypeName = "Int",
            isThrowing = false,
            isSuspend = false,
        )

        val result = generator.generate("SharedKit", listOf(function))

        // Should have async comment but not throws
        assertTrue(!result.contains("# Throws errors") || result.contains("# Async"))
    }

    // ============================================================
    // Flow Function Notes Tests
    // ============================================================

    @Test
    fun `generate flow function produces method notes`() {
        val flowFunction = FlowFunctionDeclaration(
            qualifiedName = "com.example.Repo.observeUpdates",
            simpleName = "observeUpdates",
            packageName = "com.example",
            name = "observeUpdates",
            parameters = emptyList(),
            elementTypeName = "Update",
            hasSwiftFlowAnnotation = true,
            isProperty = false,
        )

        val result = generator.generate("SharedKit", listOf(flowFunction))

        assertContains(result, "Selector:")
        assertContains(result, "# Returns AsyncSequence<Update>")
    }

    @Test
    fun `generate flow function with parameters produces correct selector`() {
        val flowFunction = FlowFunctionDeclaration(
            qualifiedName = "com.example.Repo.watchUser",
            simpleName = "watchUser",
            packageName = "com.example",
            name = "watchUser",
            parameters = listOf(
                ParameterDeclaration(name = "userId", typeName = "String", isNullable = false),
            ),
            elementTypeName = "User",
            hasSwiftFlowAnnotation = true,
            isProperty = false,
        )

        val result = generator.generate("SharedKit", listOf(flowFunction))

        assertContains(result, "Selector: \"watchUserWithUserId:\"")
        assertContains(result, "SwiftName: \"watchUser(userId:)\"")
    }

    @Test
    fun `generate flow property uses property name as selector`() {
        val flowProperty = FlowFunctionDeclaration(
            qualifiedName = "com.example.Repo.currentState",
            simpleName = "currentState",
            packageName = "com.example",
            name = "currentState",
            parameters = emptyList(),
            elementTypeName = "State",
            hasSwiftFlowAnnotation = true,
            isProperty = true,
        )

        val result = generator.generate("SharedKit", listOf(flowProperty))

        assertContains(result, "Selector: \"currentState\"")
        assertContains(result, "SwiftName: \"currentState\"")
    }

    // ============================================================
    // Multiple Declarations Tests
    // ============================================================

    @Test
    fun `generate groups functions by package`() {
        val function1 = FunctionDeclaration(
            qualifiedName = "com.example.Api.fetch",
            simpleName = "fetch",
            packageName = "com.example",
            name = "fetch",
            parameters = emptyList(),
            returnTypeName = "Data",
            isThrowing = true,
            isSuspend = true,
        )
        val function2 = FunctionDeclaration(
            qualifiedName = "com.example.Api.save",
            simpleName = "save",
            packageName = "com.example",
            name = "save",
            parameters = emptyList(),
            returnTypeName = "Unit",
            isThrowing = true,
            isSuspend = true,
        )

        val result = generator.generate("SharedKit", listOf(function1, function2))

        // Functions from same package should be grouped
        assertContains(result, "Name: com.example")
        assertContains(result, "Methods:")
    }

    @Test
    fun `generate handles mixed declaration types`() {
        val sealedClass = SealedClassDeclaration(
            qualifiedName = "com.example.Result",
            simpleName = "Result",
            packageName = "com.example",
            typeParameters = emptyList(),
            subclasses = listOf(
                SealedSubclass(
                    simpleName = "Success",
                    qualifiedName = "com.example.Result.Success",
                    isObject = false,
                    isDataClass = true,
                    properties = emptyList(),
                ),
            ),
        )

        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.fetch",
            simpleName = "fetch",
            packageName = "com.example",
            name = "fetch",
            parameters = emptyList(),
            returnTypeName = "Result",
            isThrowing = true,
            isSuspend = true,
        )

        val flowFunction = FlowFunctionDeclaration(
            qualifiedName = "com.example.Repo.observe",
            simpleName = "observe",
            packageName = "com.example",
            name = "observe",
            parameters = emptyList(),
            elementTypeName = "Update",
            hasSwiftFlowAnnotation = true,
            isProperty = false,
        )

        val result = generator.generate("SharedKit", listOf(sealedClass, function, flowFunction))

        // Should contain all declaration types
        assertContains(result, "SwiftBridge: Result")
        assertContains(result, "Selector: \"fetch\"")
        assertContains(result, "# Returns AsyncSequence<Update>")
    }

    // ============================================================
    // File Name Tests
    // ============================================================

    @Test
    fun `getFileName returns correct apinotes filename`() {
        val fileName = generator.getFileName("SharedKit")

        assertEquals("SharedKit.apinotes", fileName)
    }

    @Test
    fun `getFileName handles framework names with special characters`() {
        val fileName = generator.getFileName("My_Framework")

        assertEquals("My_Framework.apinotes", fileName)
    }

    // ============================================================
    // Edge Cases Tests
    // ============================================================

    @Test
    fun `generate handles empty package name`() {
        val function = FunctionDeclaration(
            qualifiedName = "topLevelFunction",
            simpleName = "topLevelFunction",
            packageName = "",
            name = "topLevelFunction",
            parameters = emptyList(),
            returnTypeName = "String",
            isThrowing = false,
            isSuspend = true,
        )

        val result = generator.generate("SharedKit", listOf(function))

        // Should group under "Global"
        assertContains(result, "Name: Global")
    }

    @Test
    fun `generate handles sealed class with no subclasses`() {
        val sealedClass = SealedClassDeclaration(
            qualifiedName = "com.example.Empty",
            simpleName = "Empty",
            packageName = "com.example",
            typeParameters = emptyList(),
            subclasses = emptyList(),
        )

        val result = generator.generate("SharedKit", listOf(sealedClass))

        // Should still produce valid output without subclass comments
        assertContains(result, "Name: Empty")
        assertContains(result, "SwiftName: Empty")
    }

    @Test
    fun `generate handles generic sealed class`() {
        val sealedClass = SealedClassDeclaration(
            qualifiedName = "com.example.Result",
            simpleName = "Result",
            packageName = "com.example",
            typeParameters = listOf("T", "E"),
            subclasses = listOf(
                SealedSubclass(
                    simpleName = "Success",
                    qualifiedName = "com.example.Result.Success",
                    isObject = false,
                    isDataClass = true,
                    properties = listOf(
                        PropertyDeclaration(name = "value", typeName = "T", isNullable = false),
                    ),
                    typeParameters = listOf("T"),
                ),
            ),
        )

        val result = generator.generate("SharedKit", listOf(sealedClass))

        assertContains(result, "Name: Result")
        assertContains(result, "# Success -> .success")
    }

    @Test
    fun `generate handles function with default parameter values`() {
        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.search",
            simpleName = "search",
            packageName = "com.example",
            name = "search",
            parameters = listOf(
                ParameterDeclaration(name = "query", typeName = "String", isNullable = false),
                ParameterDeclaration(name = "limit", typeName = "Int", isNullable = false, defaultValue = "10"),
            ),
            returnTypeName = "List<Result>",
            isThrowing = true,
            isSuspend = true,
            hasSwiftDefaultsAnnotation = true,
        )

        val result = generator.generate("SharedKit", listOf(function))

        // Should include all parameters in selector
        assertContains(result, "Selector: \"searchWithQuery:limit:\"")
    }

    @Test
    fun `generate capitalizes first letter of parameter in selector`() {
        val function = FunctionDeclaration(
            qualifiedName = "com.example.Api.getUserById",
            simpleName = "getUserById",
            packageName = "com.example",
            name = "getUserById",
            parameters = listOf(
                ParameterDeclaration(name = "userId", typeName = "String", isNullable = false),
            ),
            returnTypeName = "User",
            isThrowing = true,
            isSuspend = true,
        )

        val result = generator.generate("SharedKit", listOf(function))

        // First parameter should be capitalized: "WithUserId:"
        assertContains(result, "Selector: \"getUserByIdWithUserId:\"")
    }
}
