package io.swiftify.tests.acceptance

import io.swiftify.generator.SwiftifyTransformer
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * End-to-end acceptance tests for Flow → Swift AsyncSequence transformation.
 */
class FlowAcceptanceTest {

    private val transformer = SwiftifyTransformer()

    @Test
    fun `Flow function transforms to AsyncStream`() {
        val kotlinSource = """
            import kotlinx.coroutines.flow.Flow

            fun getUserUpdates(userId: String): Flow<User> {
                return flowOf(User(userId))
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "func getUserUpdates")
        assertContains(result.swiftCode, "AsyncStream")
    }

    @Test
    fun `Flow property transforms to AsyncStream property`() {
        val kotlinSource = """
            import kotlinx.coroutines.flow.StateFlow

            val currentUser: StateFlow<User?>
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "currentUser")
    }

    @Test
    fun `SharedFlow transforms with replay support`() {
        val kotlinSource = """
            import kotlinx.coroutines.flow.SharedFlow

            fun getEvents(): SharedFlow<Event> {
                return MutableSharedFlow(replay = 1)
            }
        """.trimIndent()

        val result = transformer.transform(kotlinSource)

        assertTrue(result.declarationsTransformed > 0)
        assertContains(result.swiftCode, "func getEvents")
        assertContains(result.swiftCode, "AsyncStream")
    }
}
