package com.getcode.utils.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PollUntilTest {

    @Test
    fun `succeeds on first attempt`() = runTest {
        val result = pollUntil(
            call = { 42 },
            required = { it == 42 },
            interval = 1.milliseconds,
        )
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `succeeds after multiple attempts`() = runTest {
        var count = 0
        val result = pollUntil(
            call = { ++count },
            required = { it >= 3 },
            maxAttempts = 10,
            interval = 1.milliseconds,
        )
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull())
    }

    @Test
    fun `times out after max attempts`() = runTest {
        val result = pollUntil(
            call = { false },
            required = { it },
            maxAttempts = 2,
            interval = 1.milliseconds,
            tag = "test-tag",
        )
        assertTrue(result.isFailure)
        assertIs<PollTimeoutException>(result.exceptionOrNull())
    }

    @Test
    fun `timeout exception carries tag`() = runTest {
        val result = pollUntil(
            call = { 0 },
            required = { false },
            maxAttempts = 3,
            interval = 1.milliseconds,
            tag = "my-tag",
        )
        val ex = result.exceptionOrNull()
        assertIs<PollTimeoutException>(ex)
        assertTrue(ex.message!!.contains("my-tag"))
    }

    @Test
    fun `single attempt timeout`() = runTest {
        val result = pollUntil(
            call = { "nope" },
            required = { false },
            maxAttempts = 1,
            interval = 1.milliseconds,
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `call count matches attempts before success`() = runTest {
        var calls = 0
        pollUntil(
            call = { ++calls },
            required = { it == 5 },
            maxAttempts = 10,
            interval = 1.milliseconds,
        )
        assertEquals(5, calls)
    }
}
