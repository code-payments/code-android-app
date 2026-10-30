package com.getcode.utils.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTest {

    // region retryable

    @Test
    fun `retryable succeeds on first attempt`() = runTest {
        val result = retryable(delayDuration = 1.milliseconds) { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun `retryable retries and succeeds on later attempt`() = runTest {
        var attempts = 0
        val result = retryable(maxRetries = 3, delayDuration = 1.milliseconds) {
            attempts++
            if (attempts < 3) throw RuntimeException("fail")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retryable returns null when all retries exhausted`() = runTest {
        val result = retryable(maxRetries = 2, delayDuration = 1.milliseconds) {
            throw RuntimeException("fail")
        }
        assertNull(result)
    }

    @Test
    fun `retryable with retryIf false rethrows immediately`() = runTest {
        var attempts = 0
        assertFailsWith<IllegalArgumentException> {
            retryable(
                maxRetries = 3,
                delayDuration = 1.milliseconds,
                retryIf = { it is IllegalStateException },
            ) {
                attempts++
                throw IllegalArgumentException("not retryable")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `retryable with retryIf true retries matching exceptions`() = runTest {
        var attempts = 0
        val result = retryable(
            maxRetries = 3,
            delayDuration = 1.milliseconds,
            retryIf = { it is IllegalStateException },
        ) {
            attempts++
            if (attempts < 2) throw IllegalStateException("retryable")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    // endregion

    @Test
    fun `retryable with backoffFactor retries and succeeds`() = runTest {
        var attempts = 0
        val result = retryable(
            maxRetries = 4,
            delayDuration = 100.milliseconds,
            backoffFactor = 2.0,
        ) {
            attempts++
            if (attempts < 4) throw RuntimeException("retry")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(4, attempts)
    }

    @Test
    fun `retryable with backoffFactor 1 behaves like fixed delay`() = runTest {
        var attempts = 0
        val result = retryable(
            maxRetries = 3,
            delayDuration = 1.milliseconds,
            backoffFactor = 1.0,
        ) {
            attempts++
            if (attempts < 3) throw RuntimeException("retry")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    // region retryableOrThrow

    @Test
    fun `retryableOrThrow succeeds on first attempt`() = runTest {
        val result = retryableOrThrow(delayDuration = 1.milliseconds) { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun `retryableOrThrow retries and succeeds on later attempt`() = runTest {
        var attempts = 0
        val result = retryableOrThrow(maxRetries = 3, delayDuration = 1.milliseconds) {
            attempts++
            if (attempts < 2) throw RuntimeException("fail")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `retryableOrThrow rethrows last exception when retries exhausted`() = runTest {
        val exception = assertFailsWith<RuntimeException> {
            retryableOrThrow(maxRetries = 2, delayDuration = 1.milliseconds) {
                throw RuntimeException("always fails")
            }
        }
        assertEquals("always fails", exception.message)
    }

    @Test
    fun `retryableOrThrow with retryIf false rethrows immediately`() = runTest {
        var attempts = 0
        assertFailsWith<IllegalArgumentException> {
            retryableOrThrow(
                maxRetries = 3,
                delayDuration = 1.milliseconds,
                retryIf = { it is IllegalStateException },
            ) {
                attempts++
                throw IllegalArgumentException("not retryable")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `retryableOrThrow with retryIf true retries then rethrows on exhaustion`() = runTest {
        var attempts = 0
        val exception = assertFailsWith<IllegalStateException> {
            retryableOrThrow(
                maxRetries = 2,
                delayDuration = 1.milliseconds,
                retryIf = { it is IllegalStateException },
            ) {
                attempts++
                throw IllegalStateException("attempt $attempts")
            }
        }
        assertEquals(2, attempts)
        assertEquals("attempt 2", exception.message)
    }

    // endregion
}
