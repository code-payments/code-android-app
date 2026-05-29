package com.getcode.utils.network

import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PollTimeoutException(
    tag: String,
    attempts: Int,
) : Exception("$tag: polling timed out after $attempts attempts")

suspend fun <T> pollUntil(
    call: suspend () -> T,
    required: (T) -> Boolean,
    maxAttempts: Int = 100,
    interval: Duration = 3.seconds,
    tag: String = "pollUntil",
): Result<T> {
    return doPoll(call, required, maxAttempts, interval, tag, attempt = 0)
}

private tailrec suspend fun <T> doPoll(
    call: suspend () -> T,
    predicate: (T) -> Boolean,
    maxAttempts: Int,
    interval: Duration,
    tag: String,
    attempt: Int,
): Result<T> {
    if (attempt >= maxAttempts) {
        trace(
            tag = tag,
            type = TraceType.Error,
            message = "Polling timed out after $maxAttempts attempts",
        )
        return Result.failure(PollTimeoutException(tag, maxAttempts))
    }

    if (attempt > 0) {
        delay(interval)
    }

    val value = call()

    if (predicate(value)) {
        trace(
            tag = tag,
            type = TraceType.Process,
            message = "Polling succeeded after ${attempt + 1} attempt(s)",
        )
        return Result.success(value)
    }

    trace(
        tag = tag,
        type = TraceType.Process,
        message = "Attempt ${attempt + 1}/$maxAttempts: predicate not satisfied, retrying in $interval",
    )

    return doPoll(call, predicate, maxAttempts, interval, tag, attempt + 1)
}
