package com.flipcash.app.internal.startup

import com.bugsnag.android.BreadcrumbType
import com.getcode.utils.TraceType
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BugsnagBreadcrumbSinkTest {

    private data class Left(val message: String, val metadata: Map<String, Any>, val type: BreadcrumbType)

    private val left = mutableListOf<Left>()

    private fun TestScope.sink(
        capacity: Int = 100,
        leave: (String, Map<String, Any>, BreadcrumbType) -> Unit = { m, md, t -> left += Left(m, md, t) },
    ) = BugsnagBreadcrumbSink(
        dispatcher = StandardTestDispatcher(testScheduler),
        capacity = capacity,
        leave = leave,
    )

    @Test
    fun `record returns before Bugsnag is called`() = runTest {
        val sink = sink()

        sink.record("onMessageReceived", mapOf("seq" to "1"), TraceType.Process)

        // The ANR on 2026.9.2: the main thread sat inside this call while Bugsnag waited on a lock.
        assertTrue(left.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(Left("onMessageReceived", mapOf("seq" to "1"), BreadcrumbType.PROCESS)), left)
    }

    @Test
    fun `error breadcrumbs reach Bugsnag before record returns`() = runTest {
        val sink = sink()

        sink.record("Failed to handle push", emptyMap(), TraceType.Error)

        // trace() reports the error right after the sinks run, and the report snapshots
        // breadcrumbs then; a queued crumb would be missing from its own report.
        assertEquals(listOf("Failed to handle push"), left.map { it.message })
    }

    @Test
    fun `keeps the order breadcrumbs were recorded in`() = runTest {
        val sink = sink()

        repeat(5) { sink.record("crumb $it", emptyMap(), TraceType.Log) }
        advanceUntilIdle()

        assertEquals((0 until 5).map { "crumb $it" }, left.map { it.message })
    }

    @Test
    fun `drops the oldest when the queue is full`() = runTest {
        val sink = sink(capacity = 2)

        repeat(3) { sink.record("crumb $it", emptyMap(), TraceType.Log) }
        advanceUntilIdle()

        assertEquals(listOf("crumb 1", "crumb 2"), left.map { it.message })
    }

    @Test
    fun `skips silent traces`() = runTest {
        val sink = sink()

        sink.record("local only", emptyMap(), TraceType.Silent)
        advanceUntilIdle()

        assertTrue(left.isEmpty())
    }

    @Test
    fun `keeps delivering after Bugsnag throws`() = runTest {
        val sink = sink(leave = { m, md, t ->
            if (m == "boom") error("leaveBreadcrumb failed")
            left += Left(m, md, t)
        })

        sink.record("boom", emptyMap(), TraceType.Log)
        sink.record("after", emptyMap(), TraceType.Log)
        advanceUntilIdle()

        assertEquals(listOf("after"), left.map { it.message })
    }
}
