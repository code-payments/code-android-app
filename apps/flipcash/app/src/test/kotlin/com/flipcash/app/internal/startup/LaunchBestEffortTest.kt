package com.flipcash.app.internal.startup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchBestEffortTest {

    @Test
    fun `runs the block`() = runTest {
        var ran = false

        val job = launchBestEffort(
            tag = "camerax",
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        ) { ran = true }
        job.join()

        assertTrue(ran)
        assertFalse(job.isCancelled)
    }

    @Test
    fun `reports a LinkageError instead of failing the job`() = runTest {
        val failures = mutableListOf<Pair<String, Throwable>>()

        val job = launchBestEffort(
            tag = "camerax",
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            onFailure = { tag, error -> failures += tag to error },
        ) {
            // The real Bugsnag crash: CameraX calls Context.getDeviceId() on a runtime
            // that reports API 34 but has no such method.
            throw NoSuchMethodError("No virtual method getDeviceId()I")
        }
        job.join()

        assertFalse(job.isCancelled)
        assertEquals(1, failures.size)
        assertEquals("camerax", failures.single().first)
        assertIs<NoSuchMethodError>(failures.single().second)
    }

    @Test
    fun `does not report cancellation as a failure`() = runTest {
        val failures = mutableListOf<Throwable>()

        val job = launchBestEffort(
            tag = "camerax",
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            onFailure = { _, error -> failures += error },
        ) { awaitCancellation() }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(failures.isEmpty())
    }
}
