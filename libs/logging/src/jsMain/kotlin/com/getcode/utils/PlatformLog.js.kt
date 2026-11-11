package com.getcode.utils

import kotlin.time.TimeSource

/** Node and browsers write [trace] output to the console. */
internal actual object PlatformLog {
    actual fun log(priority: Int, tag: String?, message: String) {
        val line = "${tag.orEmpty()}$message"
        when {
            priority >= LogPriority.ERROR -> console.error(line)
            priority == LogPriority.WARN -> console.warn(line)
            priority == LogPriority.INFO -> console.info(line)
            else -> console.log(line)
        }
    }

    actual fun error(throwable: Throwable) {
        console.error(throwable.stackTraceToString())
    }

    actual fun error(throwable: Throwable, message: String?) {
        message?.let { console.error(it) }
        console.error(throwable.stackTraceToString())
    }
}

/** JS has no SQL driver here, so nothing is a platform `SQLException`. */
internal actual fun isPlatformSqlException(throwable: Throwable): Boolean = false

private val processStart = TimeSource.Monotonic.markNow()

/** Milliseconds since this module loaded, on the monotonic clock. */
internal actual fun elapsedRealtimeMillis(): Long = processStart.elapsedNow().inWholeMilliseconds

/** JS keeps no log file; [TraceManager.getLogFile] returns `null` unless a [LogFileStore] is set. */
actual class LogFile private constructor()

/** No `ErrorUtils` on JS: the error goes to the console. */
internal actual fun reportTraceError(error: Throwable) = PlatformLog.error(error)

/** No `java.net.URI` on JS: cut at the first `?` or `#`, the fallback the JVM uses for unparseable URLs. */
internal actual fun urlWithoutQuery(raw: String): String = raw.substringBefore('?').substringBefore('#')
