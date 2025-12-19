package io.swiftify.swift

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for SwiftDefaultsSpec - specifications for generating convenience overloads
 * for functions with default parameters.
 */
class SwiftDefaultsSpecTest {
    @Test
    fun `create simple async function spec`() {
        val spec =
            SwiftDefaultsSpec(
                name = "fetchData",
                parameters = emptyList(),
                returnType = SwiftType.Named("String"),
            )

        assertEquals("fetchData", spec.name)
        assertTrue(spec.parameters.isEmpty())
        assertEquals("String", (spec.returnType as SwiftType.Named).name)
        assertTrue(spec.isAsync)
    }

    @Test
    fun `async function with parameters`() {
        val spec =
            SwiftDefaultsSpec(
                name = "fetchUser",
                parameters =
                listOf(
                    SwiftParameter(name = "id", type = SwiftType.Named("Int")),
                    SwiftParameter(name = "includeDetails", type = SwiftType.Named("Bool")),
                ),
                returnType = SwiftType.Named("User"),
            )

        assertEquals(2, spec.parameters.size)
        assertEquals("id", spec.parameters[0].name)
        assertEquals("includeDetails", spec.parameters[1].name)
    }

    @Test
    fun `async function can throw`() {
        val spec =
            SwiftDefaultsSpec(
                name = "loadResource",
                parameters =
                listOf(
                    SwiftParameter(name = "url", type = SwiftType.Named("URL")),
                ),
                returnType = SwiftType.Named("Data"),
                isThrowing = true,
            )

        assertTrue(spec.isThrowing)
        assertTrue(spec.isAsync)
    }

    @Test
    fun `async function with void return`() {
        val spec =
            SwiftDefaultsSpec(
                name = "saveData",
                parameters =
                listOf(
                    SwiftParameter(name = "data", type = SwiftType.Named("Data")),
                ),
                returnType = SwiftType.Void,
            )

        assertTrue(spec.returnType is SwiftType.Void)
    }

    @Test
    fun `async function with optional return`() {
        val spec =
            SwiftDefaultsSpec(
                name = "findUser",
                parameters =
                listOf(
                    SwiftParameter(name = "name", type = SwiftType.Named("String")),
                ),
                returnType = SwiftType.Optional(SwiftType.Named("User")),
            )

        assertTrue(spec.returnType is SwiftType.Optional)
    }

    @Test
    fun `async function with generic return type`() {
        val spec =
            SwiftDefaultsSpec(
                name = "fetch",
                typeParameters = listOf("T"),
                parameters =
                listOf(
                    SwiftParameter(name = "request", type = SwiftType.Named("Request")),
                ),
                returnType = SwiftType.Generic("T"),
            )

        assertEquals(listOf("T"), spec.typeParameters)
        assertTrue(spec.returnType is SwiftType.Generic)
    }

    @Test
    fun `async function with default parameter values`() {
        val spec =
            SwiftDefaultsSpec(
                name = "search",
                parameters =
                listOf(
                    SwiftParameter(name = "query", type = SwiftType.Named("String")),
                    SwiftParameter(
                        name = "limit",
                        type = SwiftType.Named("Int"),
                        defaultValue = "10",
                    ),
                    SwiftParameter(
                        name = "offset",
                        type = SwiftType.Named("Int"),
                        defaultValue = "0",
                    ),
                ),
                returnType = SwiftType.Array(SwiftType.Named("Result")),
            )

        assertEquals("10", spec.parameters[1].defaultValue)
        assertEquals("0", spec.parameters[2].defaultValue)
    }

    @Test
    fun `async function with external parameter name`() {
        val spec =
            SwiftDefaultsSpec(
                name = "move",
                parameters =
                listOf(
                    SwiftParameter(
                        name = "point",
                        externalName = "to",
                        type = SwiftType.Named("Point"),
                    ),
                ),
                returnType = SwiftType.Void,
            )

        assertEquals("to", spec.parameters[0].externalName)
        assertEquals("point", spec.parameters[0].name)
    }

    @Test
    fun `async function with underscore external name (no label)`() {
        val spec =
            SwiftDefaultsSpec(
                name = "print",
                parameters =
                listOf(
                    SwiftParameter(
                        name = "message",
                        externalName = "_",
                        type = SwiftType.Named("String"),
                    ),
                ),
                returnType = SwiftType.Void,
            )

        assertEquals("_", spec.parameters[0].externalName)
    }

    @Test
    fun `async function on type (method)`() {
        val spec =
            SwiftDefaultsSpec(
                name = "reload",
                parameters = emptyList(),
                returnType = SwiftType.Void,
                receiverType = "DataManager",
            )

        assertEquals("DataManager", spec.receiverType)
    }
}
