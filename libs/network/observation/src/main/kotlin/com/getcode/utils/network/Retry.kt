package com.getcode.utils.network

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

suspend fun <T> retryable(
    call: suspend () -> T,
    maxRetries: Int = 3,
    delayDuration: Duration = 2.seconds,
    onRetry: (Int) -> Unit = { currentAttempt ->
//        trace(
//            message = "Retrying call",
//            metadata = {
//                "count" to currentAttempt
//            },
//            type = TraceType.Process,
//        )
    },
    onError: (startTime: TimeSource.Monotonic.ValueTimeMark) -> Unit = { startTime ->
//        trace(
//            "Failed to get a success after $maxRetries attempts in ${startTime.elapsedNow().inWholeMilliseconds} ms",
//            type = TraceType.Error
//        )
    },
): T? {
    var currentAttempt = 0
    val startTime = TimeSource.Monotonic.markNow()

    while (currentAttempt < maxRetries) {
        val result = try {
            call()
        } catch (e: Exception) {
//            trace(
//                message = "Attempt $currentAttempt failed with exception: ${e.message}",
//                error = e,
//                type = TraceType.Error
//            )
            null
        }

        if (result != null) {
            return result
        } else {
            currentAttempt++
            if (currentAttempt < maxRetries) {
                onRetry(currentAttempt)
                delay(delayDuration.inWholeMilliseconds)
            }
        }
    }

    onError(startTime)
    return null
}