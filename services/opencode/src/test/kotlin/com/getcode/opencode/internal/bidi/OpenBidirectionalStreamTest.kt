@file:OptIn(ExperimentalCoroutinesApi::class)

package com.getcode.opencode.internal.bidi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenBidirectionalStreamTest {

    /**
     * ClosedSendChannelException thrown during the initial send should be
     * treated as retryable. We throw on every attempt and set maxReconnectAttempts=2
     * so the loop exhausts retries and the coroutine terminates cleanly.
     *
     * Without the fix, the first ClosedSendChannelException would call onError
     * and return immediately (attemptCount == 1). With the fix, it retries
     * until maxReconnectAttempts is exceeded (attemptCount == 3).
     */
    @Test
    fun `ClosedSendChannelException on initial send triggers retry`() = runTest {
        var attemptCount = 0
        val errors = mutableListOf<Throwable>()

        val streamRef = BidirectionalStreamReference<String, String>(this, "test-stream")
        streamRef.retain()

        openBidirectionalStream<String, String, BidirectionalStreamReference<String, String>>(
            streamRef = streamRef,
            apiCall = { _ ->
                attemptCount++
                flow { }
            },
            initialRequest = {
                // Always throw — we're testing that the retry loop continues
                throw ClosedSendChannelException("Channel was closed")
            },
            responseHandler = { _: String, _: (String) -> Unit -> },
            onError = { errors.add(it) },
            maxReconnectAttempts = 2,
            reconnectDelayMs = 0,
        )

        advanceUntilIdle()

        // With fix: retries until max attempts exceeded (3 attempts for maxReconnectAttempts=2)
        // Without fix: gives up on first attempt (attemptCount == 1)
        assertTrue(
            attemptCount > 1,
            "Should have retried after ClosedSendChannelException, but only $attemptCount attempt(s)"
        )
        // The terminal error should be the max-attempts IllegalStateException, not ClosedSendChannelException
        assertTrue(
            errors.none { it is ClosedSendChannelException },
            "ClosedSendChannelException should not be reported as a terminal error"
        )

        streamRef.destroy()
    }

    @Test
    fun `non-retryable exceptions on initial send still propagate to onError`() = runTest {
        val errors = mutableListOf<Throwable>()

        val streamRef = BidirectionalStreamReference<String, String>(this, "test-stream")
        streamRef.retain()

        openBidirectionalStream<String, String, BidirectionalStreamReference<String, String>>(
            streamRef = streamRef,
            apiCall = { _ ->
                throw IllegalArgumentException("Bad request format")
            },
            initialRequest = { "hello" },
            responseHandler = { _: String, _: (String) -> Unit -> },
            onError = { errors.add(it) },
            maxReconnectAttempts = 3,
            reconnectDelayMs = 0,
        )

        advanceUntilIdle()

        assertEquals(1, errors.size, "Non-retryable error should be reported exactly once")
        assertTrue(errors[0] is IllegalArgumentException)

        streamRef.destroy()
    }
}
