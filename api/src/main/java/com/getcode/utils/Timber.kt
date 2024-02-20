package com.getcode.utils

import com.getcode.api.BuildConfig
import timber.log.Timber
import kotlin.math.roundToLong

fun <T> timberTimer(message: String, block: () -> T): T {
    return if (BuildConfig.DEBUG) {
        // Prefer nanoTime over currentTimeMillis for measuring elapsed time because it does not try to
        // align with wall-time.
        val start = System.nanoTime()
        val result = block()
        Timber.d(
            "$message took ${
                (System.nanoTime() - start).toDouble().div(1_000_000).roundToLong()
            }ms",
        )
        result
    } else {
        block()
    }
}

suspend fun <T> timberTimerSuspend(message: String, block: suspend () -> T): T {
    return if (BuildConfig.DEBUG) {
        // Prefer nanoTime over currentTimeMillis for measuring elapsed time because it does not try to
        // align with wall-time.
        val start = System.nanoTime()
        val result = block()
        Timber.d(
            "$message took ${
                (System.nanoTime() - start).toDouble().div(1000000).roundToLong()
            }ms",
        )
        result
    } else {
        block()
    }
}