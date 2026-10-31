package com.flipcash.app.internal.startup

import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.opencode.model.core.errors.Violation
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BugsnagErrorReporterTest {

    private fun validation(field: String, rule: String) =
        ValidationException(listOf(Violation(field = field, rule = rule, message = "m")))

    @Test
    fun `grouping hash comes from the validation error or its cause`() {
        val error = validation("messages[3].content", "string.max_len")
        val wrapped = RuntimeException("wrapped", error)

        assertEquals(error.groupingHash, BugsnagErrorReporter.groupingHashFor(error, error))
        assertEquals(error.groupingHash, BugsnagErrorReporter.groupingHashFor(wrapped, error))
    }

    @Test
    fun `messages indexes with the same rule share a hash and other rules do not`() {
        val a = BugsnagErrorReporter.groupingHashFor(
            validation("messages[3].content", "string.max_len"), RuntimeException()
        )
        val b = BugsnagErrorReporter.groupingHashFor(
            validation("messages[7].content", "string.max_len"), RuntimeException()
        )
        val c = BugsnagErrorReporter.groupingHashFor(
            validation("messages[7].content", "string.min_len"), RuntimeException()
        )

        assertEquals(a, b)
        assert(a != c)
    }

    @Test
    fun `other errors keep Bugsnag default grouping`() {
        assertNull(BugsnagErrorReporter.groupingHashFor(RuntimeException(), RuntimeException()))
    }
}
