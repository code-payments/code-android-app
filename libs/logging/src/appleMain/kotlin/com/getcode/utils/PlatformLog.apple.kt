package com.getcode.utils

import kotlin.time.TimeSource

/** Apple log sink: standard output, like the JS actual's console. No file log, no reporter. */
internal actual object PlatformLog {
    actual fun log(priority: Int, tag: String?, message: String) {
        println("${tag.orEmpty()}$message")
    }

    actual fun error(throwable: Throwable) {
        println(throwable.stackTraceToString())
    }

    actual fun error(throwable: Throwable, message: String?) {
        message?.let { println(it) }
        println(throwable.stackTraceToString())
    }
}

internal actual fun isPlatformSqlException(throwable: Throwable): Boolean = false

private val processStart = TimeSource.Monotonic.markNow()

internal actual fun elapsedRealtimeMillis(): Long = processStart.elapsedNow().inWholeMilliseconds

actual class LogFile private constructor()

internal actual fun reportTraceError(error: Throwable) = PlatformLog.error(error)

internal actual fun urlWithoutQuery(raw: String): String = raw.substringBefore('?').substringBefore('#')
