package com.getcode.utils

import java.sql.SQLException

internal actual object PlatformLog {
    actual fun log(priority: Int, tag: String?, message: String) {
        val level = when (priority) {
            LogPriority.VERBOSE -> "V"
            LogPriority.DEBUG -> "D"
            LogPriority.INFO -> "I"
            LogPriority.WARN -> "W"
            else -> "E"
        }
        System.err.println("$level ${tag.orEmpty()}$message")
    }

    actual fun error(throwable: Throwable) {
        throwable.printStackTrace()
    }
}

internal actual fun isPlatformSqlException(throwable: Throwable): Boolean = throwable is SQLException

internal actual fun elapsedRealtimeMillis(): Long = System.nanoTime() / 1_000_000
