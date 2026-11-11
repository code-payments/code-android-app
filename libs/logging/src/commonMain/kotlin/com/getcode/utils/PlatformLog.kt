package com.getcode.utils

import kotlinx.coroutines.flow.SharedFlow

/** Log levels, numbered as `android.util.Log` numbers them so Android passes them straight through. */
object LogPriority {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
}

/** Where [trace] and [ErrorUtils] write: Timber on Android, standard error on the desktop JVM. */
internal expect object PlatformLog {
    fun log(priority: Int, tag: String?, message: String)
    fun error(throwable: Throwable)
    fun error(throwable: Throwable, message: String?)
}

/** The platform's `SQLException`, which [ErrorUtils] treats as suppressible. */
internal expect fun isPlatformSqlException(throwable: Throwable): Boolean

/** Milliseconds on a clock that keeps running while the device sleeps. */
internal expect fun elapsedRealtimeMillis(): Long

/** The file [TraceManager.getLogFile] returns: `java.io.File` on Android and the desktop JVM. */
expect class LogFile

/** Hands the `error` passed to [trace] to `ErrorUtils.handleError` on Android and the desktop JVM. */
internal expect fun reportTraceError(error: Throwable)

/**
 * [raw] without its query and fragment, for [PiiMaskingPlugin]. Android and the desktop JVM parse
 * it with `java.net.URI`.
 */
internal expect fun urlWithoutQuery(raw: String): String

/** The on-disk log behind [TraceManager.getLogFile], [TraceManager.logStream] and [TraceManager.clearLogs]. */
interface LogFileStore {
    val logStream: SharedFlow<String>
    fun getLogFile(includeHeader: Boolean = true): LogFile?
    fun clearLogs()
    /** Stops writing to the log. Called when [TraceManager] is reset. */
    fun close()
}

/**
 * Writes straight to the platform log: Timber on Android, standard error on the desktop JVM.
 *
 * Unlike [trace], it is not gated on [TraceManager] and never reports errors.
 */
object Log {
    fun d(message: String) = PlatformLog.log(LogPriority.DEBUG, null, message)

    fun e(throwable: Throwable, message: String?) = PlatformLog.error(throwable, message)
}
