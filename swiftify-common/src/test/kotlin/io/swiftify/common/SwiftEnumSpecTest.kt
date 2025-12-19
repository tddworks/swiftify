package io.swiftify.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED PHASE: These tests define what we want SwiftEnumSpec to do.
 * Implementation will come after tests are written.
 */
class SwiftEnumSpecTest {

    @Test
    fun `create simple enum spec with cases`() {
        val spec = SwiftEnumSpec(
            name = "NetworkResult",
            cases = listOf(
                SwiftEnumCase(name = "success"),
                SwiftEnumCase(name = "failure"),
                SwiftEnumCase(name = "loading")
            )
        )

        assertEquals("NetworkResult", spec.name)
        assertEquals(3, spec.cases.size)
        assertEquals("success", spec.cases[0].name)
        assertEquals("failure", spec.cases[1].name)
        assertEquals("loading", spec.cases[2].name)
    }

    @Test
    fun `create enum case with associated value`() {
        val spec = SwiftEnumSpec(
            name = "Result",
            cases = listOf(
                SwiftEnumCase(
                    name = "success",
                    associatedValues = listOf(
                        SwiftEnumCase.AssociatedValue(
                            label = "value",
                            type = SwiftType.Generic("T")
                        )
                    )
                ),
                SwiftEnumCase(
                    name = "failure",
                    associatedValues = listOf(
                        SwiftEnumCase.AssociatedValue(
                            label = "error",
                            type = SwiftType.Named("Error")
                        )
                    )
                )
            )
        )

        assertEquals("Result", spec.name)
        assertEquals(2, spec.cases.size)

        val successCase = spec.cases[0]
        assertEquals("success", successCase.name)
        assertEquals(1, successCase.associatedValues.size)
        assertEquals("value", successCase.associatedValues[0].label)
        assertTrue(successCase.associatedValues[0].type is SwiftType.Generic)

        val failureCase = spec.cases[1]
        assertEquals("failure", failureCase.name)
        assertEquals("error", failureCase.associatedValues[0].label)
        assertEquals("Error", (failureCase.associatedValues[0].type as SwiftType.Named).name)
    }

    @Test
    fun `enum spec can be marked as exhaustive`() {
        val spec = SwiftEnumSpec(
            name = "State",
            cases = listOf(SwiftEnumCase(name = "idle")),
            isExhaustive = true
        )

        assertTrue(spec.isExhaustive)
    }

    @Test
    fun `enum spec can have type parameters`() {
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

        assertEquals(listOf("T", "E"), spec.typeParameters)
    }

    @Test
    fun `enum spec can conform to protocols`() {
        val spec = SwiftEnumSpec(
            name = "Status",
            cases = listOf(SwiftEnumCase(name = "active")),
            conformances = listOf("Hashable", "Codable")
        )

        assertEquals(listOf("Hashable", "Codable"), spec.conformances)
    }

    @Test
    fun `enum case with multiple associated values`() {
        val case = SwiftEnumCase(
            name = "response",
            associatedValues = listOf(
                SwiftEnumCase.AssociatedValue("data", SwiftType.Named("Data")),
                SwiftEnumCase.AssociatedValue("statusCode", SwiftType.Named("Int")),
                SwiftEnumCase.AssociatedValue("headers", SwiftType.Named("[String: String]"))
            )
        )

        assertEquals(3, case.associatedValues.size)
        assertEquals("data", case.associatedValues[0].label)
        assertEquals("statusCode", case.associatedValues[1].label)
        assertEquals("headers", case.associatedValues[2].label)
    }
}
