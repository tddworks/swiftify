package io.swiftify.generator

import io.swiftify.common.SwiftEnumCase
import io.swiftify.common.SwiftEnumSpec
import io.swiftify.common.SwiftType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: Tests for Swift enum code generation.
 * The generator takes a SwiftEnumSpec and produces Swift source code.
 */
class SwiftEnumGeneratorTest {

    private val generator = SwiftEnumGenerator()

    @Test
    fun `generates simple enum without associated values`() {
        val spec = SwiftEnumSpec(
            name = "Status",
            cases = listOf(
                SwiftEnumCase(name = "idle"),
                SwiftEnumCase(name = "loading"),
                SwiftEnumCase(name = "completed")
            )
        )

        val result = generator.generate(spec)

        val expected = """
            |public enum Status {
            |    case idle
            |    case loading
            |    case completed
            |}
        """.trimMargin()

        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates enum with associated values`() {
        val spec = SwiftEnumSpec(
            name = "Result",
            cases = listOf(
                SwiftEnumCase(
                    name = "success",
                    associatedValues = listOf(
                        SwiftEnumCase.AssociatedValue("value", SwiftType.Named("String"))
                    )
                ),
                SwiftEnumCase(
                    name = "failure",
                    associatedValues = listOf(
                        SwiftEnumCase.AssociatedValue("error", SwiftType.Named("Error"))
                    )
                )
            )
        )

        val result = generator.generate(spec)

        val expected = """
            |public enum Result {
            |    case success(value: String)
            |    case failure(error: Error)
            |}
        """.trimMargin()

        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates frozen enum when exhaustive`() {
        val spec = SwiftEnumSpec(
            name = "Direction",
            cases = listOf(
                SwiftEnumCase(name = "north"),
                SwiftEnumCase(name = "south"),
                SwiftEnumCase(name = "east"),
                SwiftEnumCase(name = "west")
            ),
            isExhaustive = true
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("@frozen"))
        assertTrue(result.contains("public enum Direction"))
    }

    @Test
    fun `generates enum with protocol conformances`() {
        val spec = SwiftEnumSpec(
            name = "State",
            cases = listOf(SwiftEnumCase(name = "active")),
            conformances = listOf("Hashable", "Codable")
        )

        val result = generator.generate(spec)

        val expected = """
            |public enum State: Hashable, Codable {
            |    case active
            |}
        """.trimMargin()

        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates generic enum with type parameters`() {
        val spec = SwiftEnumSpec(
            name = "Result",
            typeParameters = listOf("T", "E"),
            cases = listOf(
                SwiftEnumCase(
                    name = "success",
                    associatedValues = listOf(
                        SwiftEnumCase.AssociatedValue("value", SwiftType.Generic("T"))
                    )
                ),
                SwiftEnumCase(
                    name = "failure",
                    associatedValues = listOf(
                        SwiftEnumCase.AssociatedValue("error", SwiftType.Generic("E"))
                    )
                )
            )
        )

        val result = generator.generate(spec)

        val expected = """
            |public enum Result<T, E> {
            |    case success(value: T)
            |    case failure(error: E)
            |}
        """.trimMargin()

        assertEquals(expected, result.trim())
    }

    @Test
    fun `generates enum case with multiple associated values`() {
        val spec = SwiftEnumSpec(
            name = "Response",
            cases = listOf(
                SwiftEnumCase(
                    name = "success",
                    associatedValues = listOf(
                        SwiftEnumCase.AssociatedValue("data", SwiftType.Named("Data")),
                        SwiftEnumCase.AssociatedValue("statusCode", SwiftType.Named("Int"))
                    )
                )
            )
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("case success(data: Data, statusCode: Int)"))
    }

    @Test
    fun `generates internal access level`() {
        val spec = SwiftEnumSpec(
            name = "InternalState",
            cases = listOf(SwiftEnumCase(name = "ready")),
            accessLevel = SwiftEnumSpec.AccessLevel.INTERNAL
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("internal enum InternalState"))
    }

    @Test
    fun `generates enum with generic constraint conformances`() {
        val spec = SwiftEnumSpec(
            name = "Result",
            typeParameters = listOf("T"),
            cases = listOf(
                SwiftEnumCase(
                    name = "success",
                    associatedValues = listOf(
                        SwiftEnumCase.AssociatedValue("value", SwiftType.Generic("T"))
                    )
                )
            ),
            conformances = listOf("Hashable"),
            isExhaustive = true
        )

        val result = generator.generate(spec)

        assertTrue(result.contains("@frozen"))
        assertTrue(result.contains("public enum Result<T>: Hashable"))
    }
}
