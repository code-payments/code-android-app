package com.getcode.utils.network

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

suspend inline fun <T> retryable(
    maxRetries: Int = 3,
    delayDuration: Duration = 2.seconds,
    backoffFactor: Double = 1.0,
    retryIf: (Throwable) -> Boolean = { true },
    onRetry: (Int) -> Unit = { currentAttempt ->
        trace(
            message = "Retrying call",
            metadata = {
                "count" to currentAttempt
            },
            type = TraceType.Process,
        )
    },
    onError: (startTime: TimeSource.Monotonic.ValueTimeMark) -> Unit = { startTime ->
        trace(
            "Failed to get a success after $maxRetries attempts in ${startTime.elapsedNow().inWholeMilliseconds} ms",
            type = TraceType.Error
        )
    },
    call: suspend () -> T,
): T? {
    var currentAttempt = 0
    val startTime = TimeSource.Monotonic.markNow()

    while (currentAttempt < maxRetries) {
        val result = try {
            call()
        } catch (e: Throwable) {
            if (!retryIf(e)) throw e
            null
        }

        if (result != null) {
            return result
        } else {
            currentAttempt++
            if (currentAttempt < maxRetries) {
                onRetry(currentAttempt)
                val actualDelay = delayDuration * backoffFactor.pow(currentAttempt - 1)
                delay(actualDelay.inWholeMilliseconds)
            }
        }
    }

    onError(startTime)
    return null
}

/**
 * Like [retryable] but rethrows the last exception when retries are exhausted
 * instead of returning null, guaranteeing a non-null return on success.
 */
suspend fun <T> retryableOrThrow(
    maxRetries: Int = 3,
    delayDuration: Duration = 2.seconds,
    backoffFactor: Double = 1.0,
    retryIf: (Throwable) -> Boolean = { true },
    onRetry: (Int) -> Unit = { currentAttempt ->
        trace(
            message = "Retrying call",
            metadata = {
                "count" to currentAttempt
            },
            type = TraceType.Process,
        )
    },
    onError: (startTime: TimeSource.Monotonic.ValueTimeMark) -> Unit = { startTime ->
        trace(
            "Failed to get a success after $maxRetries attempts in ${startTime.elapsedNow().inWholeMilliseconds} ms",
            type = TraceType.Error
        )
    },
    call: suspend () -> T,
): T {
    var currentAttempt = 0
    var lastException: Throwable? = null
    val startTime = TimeSource.Monotonic.markNow()

    while (currentAttempt < maxRetries) {
        try {
            return call()
        } catch (e: Throwable) {
            if (!retryIf(e)) throw e
            lastException = e
            trace(
                message = "Attempt $currentAttempt failed with exception: ${e.message}",
                error = e,
                type = TraceType.Error
            )
            currentAttempt++
            if (currentAttempt < maxRetries) {
                onRetry(currentAttempt)
                val actualDelay = delayDuration * backoffFactor.pow(currentAttempt - 1)
                delay(actualDelay.inWholeMilliseconds)
            }
        }
    }

    onError(startTime)
    throw lastException!!
}