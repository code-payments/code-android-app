package com.getcode.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

fun flowInterval(
    delayMillis: () -> Long,
    initialDelayMillis: Long = 0L
) = flow {
    require(delayMillis() >= 0) { "delayMillis must be positive" }
    require(initialDelayMillis >=0) { "initialDelayMillis cannot be negative" }
    if (initialDelayMillis > 0) {
        delay(initialDelayMillis)
    }
    emit(System.currentTimeMillis())
    while (true) {
        delay(delayMillis())
        emit(System.currentTimeMillis())
    }
}.cancellable().buffer()

fun <T> Flow<T>.catchSafely(
    action: suspend (T) -> Unit,
): Flow<Any> = catchSafely(action, onFailure = { Timber.e(it, it.message) })

fun <T> Flow<T>.catchSafely(
    action: suspend (T) -> Unit,
    onFailure: (Throwable) -> Unit = { Timber.e(it, it.message) },
): Flow<Any> = map {
    try {
        action(it)
    } catch (e: Exception) {
        onFailure(e)
    }
}