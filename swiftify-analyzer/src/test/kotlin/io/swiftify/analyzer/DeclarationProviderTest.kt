package io.swiftify.analyzer

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for DeclarationProvider implementations.
 *
 * User expectation: Both REGEX and KSP modes should produce equivalent declarations
 * from the same source code.
 */
@DisplayName("DeclarationProvider - Mode Equivalence")
class DeclarationProviderTest {

    @TempDir
    lateinit var tempDir: File

    @Nested
    @DisplayName("RegexDeclarationProvider")
    inner class RegexProviderTests {

        @Test
        fun `returns empty list when no source files`() {
            val provider = RegexDeclarationProvider(emptyList())

            val declarations = provider.getDeclarations()

            assertTrue(declarations.isEmpty())
        }

        @Test
        fun `hasValidInput returns false for empty list`() {
            val provider = RegexDeclarationProvider(emptyList())

            assertFalse(provider.hasValidInput())
        }

        @Test
        fun `hasValidInput returns true when files exist`() {
            val sourceFile = File(tempDir, "Test.kt").apply {
                writeText("class Test")
            }
            val provider = RegexDeclarationProvider(listOf(sourceFile))

            assertTrue(provider.hasValidInput())
        }

        @Test
        fun `finds suspend functions with @SwiftDefaults`() {
            val sourceFile = File(tempDir, "Repo.kt").apply {
                writeText(
                    """
                    package com.example

                    import io.swiftify.annotations.SwiftDefaults

                    class Repo {
                        @SwiftDefaults
                        suspend fun getItems(page: Int = 1): List<String> = emptyList()
                    }
                    """.trimIndent(),
                )
            }
            val provider = RegexDeclarationProvider(listOf(sourceFile))

            val declarations = provider.getDeclarations()

            val suspendFuncs = declarations.filterIsInstance<FunctionDeclaration>()
            assertEquals(1, suspendFuncs.size)
            assertEquals("getItems", suspendFuncs[0].name)
        }

        @Test
        fun `finds Flow functions with @SwiftFlow`() {
            val sourceFile = File(tempDir, "Repo.kt").apply {
                writeText(
                    """
                    package com.example

                    import io.swiftify.annotations.SwiftFlow
                    import kotlinx.coroutines.flow.Flow

                    class Repo {
                        @SwiftFlow
                        fun watchItems(): Flow<String> = flowOf()
                    }
                    """.trimIndent(),
                )
            }
            val provider = RegexDeclarationProvider(listOf(sourceFile))

            val declarations = provider.getDeclarations()

            val flowFuncs = declarations.filterIsInstance<FlowFunctionDeclaration>()
            assertEquals(1, flowFuncs.size)
            assertEquals("watchItems", flowFuncs[0].name)
        }

        @Test
        fun `extracts default parameter values`() {
            val sourceFile = File(tempDir, "Repo.kt").apply {
                writeText(
                    """
                    package com.example

                    import io.swiftify.annotations.SwiftDefaults

                    class Repo {
                        @SwiftDefaults
                        suspend fun search(
                            query: String,
                            limit: Int = 20,
                            includeArchived: Boolean = false
                        ): List<String> = emptyList()
                    }
                    """.trimIndent(),
                )
            }
            val provider = RegexDeclarationProvider(listOf(sourceFile))

            val declarations = provider.getDeclarations()
            val func = declarations.filterIsInstance<FunctionDeclaration>().first()

            assertEquals(3, func.parameters.size)
            assertEquals("20", func.parameters[1].defaultValue)
            assertEquals("false", func.parameters[2].defaultValue)
        }

        @Test
        fun `getSourceDescription returns file count`() {
            val file1 = File(tempDir, "A.kt").apply { writeText("class A") }
            val file2 = File(tempDir, "B.kt").apply { writeText("class B") }
            val provider = RegexDeclarationProvider(listOf(file1, file2))

            val description = provider.getSourceDescription()

            assertTrue(description.contains("2"))
            assertTrue(description.contains("Kotlin"))
        }
    }

    @Nested
    @DisplayName("KspDeclarationProvider")
    inner class KspProviderTests {

        @Test
        fun `returns empty list for empty manifest`() {
            val provider = KspDeclarationProvider("")

            val declarations = provider.getDeclarations()

            assertTrue(declarations.isEmpty())
        }

        @Test
        fun `hasValidInput returns false for empty content`() {
            val provider = KspDeclarationProvider("")

            assertFalse(provider.hasValidInput())
        }

        @Test
        fun `hasValidInput returns true for valid manifest`() {
            val manifest = """
                [suspend:com.example.Repo.getItems]
                name=getItems
                package=com.example
                return=List<String>
            """.trimIndent()
            val provider = KspDeclarationProvider(manifest)

            assertTrue(provider.hasValidInput())
        }

        @Test
        fun `parses suspend function from manifest`() {
            val manifest = """
                [suspend:com.example.Repo.getItems]
                name=getItems
                package=com.example
                return=List<String>
                class=Repo
                hasAnnotation=true
                param=page:Int=1
            """.trimIndent()
            val provider = KspDeclarationProvider(manifest)

            val declarations = provider.getDeclarations()

            val suspendFuncs = declarations.filterIsInstance<FunctionDeclaration>()
            assertEquals(1, suspendFuncs.size)
            assertEquals("getItems", suspendFuncs[0].name)
            assertEquals("1", suspendFuncs[0].parameters[0].defaultValue)
        }

        @Test
        fun `parses Flow function from manifest`() {
            val manifest = """
                [flow:com.example.Repo.watchItems]
                name=watchItems
                package=com.example
                element=String
                class=Repo
                hasAnnotation=true
            """.trimIndent()
            val provider = KspDeclarationProvider(manifest)

            val declarations = provider.getDeclarations()

            val flowFuncs = declarations.filterIsInstance<FlowFunctionDeclaration>()
            assertEquals(1, flowFuncs.size)
            assertEquals("watchItems", flowFuncs[0].name)
            assertEquals("String", flowFuncs[0].elementTypeName)
        }

        @Test
        fun `parses sealed class from manifest`() {
            val manifest = """
                [sealed:com.example.Result]
                name=Result
                package=com.example
                exhaustive=true
                subclass=Success|data:String|false
                subclass=Error|message:String|false
            """.trimIndent()
            val provider = KspDeclarationProvider(manifest)

            val declarations = provider.getDeclarations()

            val sealedClasses = declarations.filterIsInstance<SealedClassDeclaration>()
            assertEquals(1, sealedClasses.size)
            assertEquals("Result", sealedClasses[0].simpleName)
            assertEquals(2, sealedClasses[0].subclasses.size)
        }

        @Test
        fun `fromFiles merges multiple manifests`() {
            val file1 = File(tempDir, "manifest1.txt").apply {
                writeText(
                    """
                    [suspend:com.example.A.methodA]
                    name=methodA
                    package=com.example
                    return=String
                    """.trimIndent(),
                )
            }
            val file2 = File(tempDir, "manifest2.txt").apply {
                writeText(
                    """
                    [suspend:com.example.B.methodB]
                    name=methodB
                    package=com.example
                    return=Int
                    """.trimIndent(),
                )
            }
            val provider = KspDeclarationProvider.fromFiles(listOf(file1, file2))

            val declarations = provider.getDeclarations()

            assertEquals(2, declarations.size)
        }

        @Test
        fun `getSourceDescription shows declaration counts`() {
            val manifest = """
                [sealed:com.example.Result]
                name=Result

                [suspend:com.example.Repo.get]
                name=get

                [flow:com.example.Repo.watch]
                name=watch
            """.trimIndent()
            val provider = KspDeclarationProvider(manifest)

            val description = provider.getSourceDescription()

            assertTrue(description.contains("1 sealed"))
            assertTrue(description.contains("1 suspend"))
            assertTrue(description.contains("1 flow"))
        }
    }

    @Nested
    @DisplayName("Mode Equivalence")
    inner class ModeEquivalenceTests {

        @Test
        fun `REGEX and KSP produce same declaration for suspend function`() {
            // REGEX: Parse from source
            val sourceFile = File(tempDir, "Repo.kt").apply {
                writeText(
                    """
                    package com.example

                    import io.swiftify.annotations.SwiftDefaults

                    class Repo {
                        @SwiftDefaults
                        suspend fun getData(id: String, limit: Int = 10): String = ""
                    }
                    """.trimIndent(),
                )
            }
            val regexProvider = RegexDeclarationProvider(listOf(sourceFile))

            // KSP: Parse from manifest (as it would be generated by KSP processor)
            val manifest = """
                [suspend:com.example.Repo.getData]
                name=getData
                package=com.example
                return=String
                class=Repo
                hasAnnotation=true
                param=id:String
                param=limit:Int=10
            """.trimIndent()
            val kspProvider = KspDeclarationProvider(manifest)

            // Get declarations from both
            val regexDeclarations = regexProvider.getDeclarations()
            val kspDeclarations = kspProvider.getDeclarations()

            // Both should have 1 suspend function
            val regexFunc = regexDeclarations.filterIsInstance<FunctionDeclaration>().first()
            val kspFunc = kspDeclarations.filterIsInstance<FunctionDeclaration>().first()

            // Key properties should match
            assertEquals(regexFunc.name, kspFunc.name)
            assertEquals(regexFunc.parameters.size, kspFunc.parameters.size)
            assertEquals(
                regexFunc.parameters[1].defaultValue,
                kspFunc.parameters[1].defaultValue,
                "Default values should match",
            )
        }

        @Test
        fun `REGEX and KSP produce same declaration for Flow function`() {
            // REGEX: Parse from source
            val sourceFile = File(tempDir, "Repo.kt").apply {
                writeText(
                    """
                    package com.example

                    import io.swiftify.annotations.SwiftFlow
                    import kotlinx.coroutines.flow.Flow

                    class Repo {
                        @SwiftFlow
                        fun watchUpdates(userId: String): Flow<String> = flowOf()
                    }
                    """.trimIndent(),
                )
            }
            val regexProvider = RegexDeclarationProvider(listOf(sourceFile))

            // KSP: Parse from manifest
            val manifest = """
                [flow:com.example.Repo.watchUpdates]
                name=watchUpdates
                package=com.example
                element=String
                class=Repo
                hasAnnotation=true
                param=userId:String
            """.trimIndent()
            val kspProvider = KspDeclarationProvider(manifest)

            // Get declarations from both
            val regexDeclarations = regexProvider.getDeclarations()
            val kspDeclarations = kspProvider.getDeclarations()

            // Both should have 1 flow function
            val regexFunc = regexDeclarations.filterIsInstance<FlowFunctionDeclaration>().first()
            val kspFunc = kspDeclarations.filterIsInstance<FlowFunctionDeclaration>().first()

            // Key properties should match
            assertEquals(regexFunc.name, kspFunc.name)
            assertEquals(regexFunc.elementTypeName, kspFunc.elementTypeName)
            assertEquals(regexFunc.parameters.size, kspFunc.parameters.size)
        }
    }
}
