@file:OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.getcode.opencode.internal.bidi

import io.grpc.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

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

    @Test
    fun `computeBackoffMs grows exponentially and caps at max`() {
        assertEquals(0L, computeBackoffMs(attempt = 1, baseDelayMs = 1000, maxDelayMs = 30_000))
        assertEquals(1000L, computeBackoffMs(attempt = 2, baseDelayMs = 1000, maxDelayMs = 30_000))
        assertEquals(2000L, computeBackoffMs(attempt = 3, baseDelayMs = 1000, maxDelayMs = 30_000))
        assertEquals(4000L, computeBackoffMs(attempt = 4, baseDelayMs = 1000, maxDelayMs = 30_000))
        assertEquals(8000L, computeBackoffMs(attempt = 5, baseDelayMs = 1000, maxDelayMs = 30_000))
        assertEquals(30_000L, computeBackoffMs(attempt = 20, baseDelayMs = 1000, maxDelayMs = 30_000))
        // Zero base (used by tests) must never delay.
        assertEquals(0L, computeBackoffMs(attempt = 5, baseDelayMs = 0, maxDelayMs = 30_000))
    }

    /**
     * Regression for the infinite ABORTED reconnect loop (Bugsnag 6a4528ca).
     * The stream activates (emits one response) then aborts with ABORTED on every
     * attempt. Because it never stays healthy past `healthyThreshold`, the attempt
     * counter must NOT reset, so the loop terminates at maxReconnectAttempts.
     *
     * Without the fix (reset on mere activation), this loops forever and the test
     * times out.
     */
    @Test
    fun `activate-then-abort stream stops at maxReconnectAttempts`() = runTest {
        var attemptCount = 0
        val errors = mutableListOf<Throwable>()
        val timeSource = TestTimeSource() // stays at 0 → never healthy

        val streamRef = BidirectionalStreamReference<String, String>(this, "test-stream")
        streamRef.retain()

        openBidirectionalStream<String, String, BidirectionalStreamReference<String, String>>(
            streamRef = streamRef,
            apiCall = { requestFlow ->
                attemptCount++
                flow {
                    // RENDEZVOUS channel: requestChannel.send() in the coordinator suspends until
                    // a consumer is ready. Call first() here to rendezvous so send can proceed.
                    requestFlow.first()
                    emit("activation") // activates the stream (first response)
                    throw Status.ABORTED.withDescription("stream already exists").asRuntimeException()
                }
            },
            initialRequest = { "req" },
            responseHandler = { _: String, _: (String) -> Unit -> },
            onError = { errors.add(it) },
            reconnectOnAborted = true,
            maxReconnectAttempts = 3,
            reconnectDelayMs = 0,
            healthyThreshold = 30.seconds,
            timeSource = timeSource,
        )

        advanceUntilIdle()

        // maxReconnectAttempts=3 → 4 total attempts, then give up.
        assertEquals(4, attemptCount, "Should stop after maxReconnectAttempts, got $attemptCount")
        assertTrue(
            errors.any { it is IllegalStateException },
            "Should terminate with max-attempts IllegalStateException"
        )

        streamRef.destroy()
    }

    /**
     * Regression for the "ABORTED: stream already exists" storm on device.
     *
     * destroy() MUST cancel the running reconnect loop. If the reference's
     * SupervisorJob is not actually the parent of the launched loop (context
     * composition order bug), destroy() cancels an orphaned job and the loop
     * keeps reconnecting — spawning a second live gRPC stream and producing the
     * server's "ABORTED: stream already exists".
     *
     * Here the loop reconnects forever on ABORTED (maxReconnectAttempts high,
     * healthy resets disabled by a frozen clock). On the 3rd attempt we call
     * destroy() from inside the stream. With the fix the loop is cancelled and
     * attemptCount freezes at 3. Without it, the loop runs on to give-up.
     */
    @Test
    fun `destroy cancels the reconnect loop`() = runTest {
        var attemptCount = 0
        val timeSource = TestTimeSource() // frozen → never healthy, never resets

        val streamRef = BidirectionalStreamReference<String, String>(this, "test-stream")
        streamRef.retain()

        openBidirectionalStream<String, String, BidirectionalStreamReference<String, String>>(
            streamRef = streamRef,
            apiCall = { requestFlow ->
                attemptCount++
                flow {
                    requestFlow.first()
                    emit("activation")
                    if (attemptCount >= 3) streamRef.destroy()
                    throw Status.ABORTED.withDescription("stream already exists").asRuntimeException()
                }
            },
            initialRequest = { "req" },
            responseHandler = { _: String, _: (String) -> Unit -> },
            onError = { },
            reconnectOnAborted = true,
            maxReconnectAttempts = 50,
            reconnectDelayMs = 0,
            healthyThreshold = 30.seconds,
            timeSource = timeSource,
        )

        advanceUntilIdle()

        assertEquals(
            3,
            attemptCount,
            "destroy() must cancel the loop; it kept reconnecting to $attemptCount attempts"
        )
    }

    /**
     * A stream that stays healthy (survives past healthyThreshold) between failures
     * must reset the backoff counter and keep reconnecting well beyond
     * maxReconnectAttempts, until a non-retryable error stops it.
     */
    @Test
    fun `healthy stream resets backoff counter across failures`() = runTest {
        var attemptCount = 0
        val errors = mutableListOf<Throwable>()
        val timeSource = TestTimeSource()

        val streamRef = BidirectionalStreamReference<String, String>(this, "test-stream")
        streamRef.retain()

        openBidirectionalStream<String, String, BidirectionalStreamReference<String, String>>(
            streamRef = streamRef,
            apiCall = { requestFlow ->
                attemptCount++
                flow {
                    // RENDEZVOUS channel: requestChannel.send() in the coordinator suspends until
                    // a consumer is ready. Call first() here to rendezvous so send can proceed.
                    requestFlow.first()
                    emit("activation")            // activates + marks activation time
                    timeSource += 31.seconds      // stream stayed healthy
                    if (attemptCount >= 5) {
                        throw IllegalArgumentException("stop") // non-retryable → terminate
                    }
                    throw Status.ABORTED.asRuntimeException() // healthy abort → reset counter
                }
            },
            initialRequest = { "req" },
            responseHandler = { _: String, _: (String) -> Unit -> },
            onError = { errors.add(it) },
            reconnectOnAborted = true,
            maxReconnectAttempts = 2, // would stop at 3 attempts WITHOUT resets
            reconnectDelayMs = 0,
            healthyThreshold = 30.seconds,
            timeSource = timeSource,
        )

        advanceUntilIdle()

        assertEquals(5, attemptCount, "Healthy resets should let it survive past maxReconnectAttempts")
        assertTrue(errors.last() is IllegalArgumentException)

        streamRef.destroy()
    }
}
