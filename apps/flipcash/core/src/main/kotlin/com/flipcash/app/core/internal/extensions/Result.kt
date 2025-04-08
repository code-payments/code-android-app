package com.flipcash.app.core.internal.extensions

import kotlinx.coroutines.delay
import kotlin.time.Duration

suspend fun <T, R> Result<T>.mapResult(transform: suspend (T) -> Result<R>): Result<R> {
    return try {
        this.fold(
            onSuccess = { value -> transform(value) },
            onFailure = { error -> Result.failure(error) }
        )
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

suspend fun <T> Result<T>.onSuccessWithDelay(minimumDelay: Long, block: suspend (T) -> Unit): Result<T> {
    val startTime = System.currentTimeMillis()
    return onSuccess { value ->
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = minimumDelay - elapsedTime
        if (remainingTime > 0) {
            delay(remainingTime)
        }
        block(value)
    }
}

suspend fun <T> Result<T>.onSuccessWithDelay(minimumDelay: Duration, block: suspend (T) -> Unit): Result<T> {
    val startTime = System.currentTimeMillis()
    return onSuccess { value ->
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = minimumDelay.inWholeMilliseconds - elapsedTime
        if (remainingTime > 0) {
            delay(remainingTime)
        }
        block(value)
    }
}