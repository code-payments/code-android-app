package com.getcode.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

class FileTree(
    context: Context,
    private val plugins: () -> List<TraceLogPlugin>,
) : Timber.Tree() {

    private val traceDir = File(context.filesDir, "traces").apply { mkdirs() }
    private val logFile = File(traceDir, "trace.log")
    private val exportFile = File(traceDir, "trace_export.log")
    private val lock = Any()
    private val appContext: Context = context.applicationContext

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        .withZone(ZoneId.systemDefault())

    private val logFlow = MutableSharedFlow<String>(
        replay = LOG_STREAM_BUFFER_CAPACITY,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Hot stream of processed log lines, post plugin pipeline. New collectors
     * immediately receive the most recent [LOG_STREAM_BUFFER_CAPACITY] lines
     * via replay cache, so the in-app viewer opens already populated.
     */
    val logStream: SharedFlow<String> = logFlow.asSharedFlow()

    init {
        // Clear previous session logs on cold launch
        traceDir.listFiles()?.forEach { it.delete() }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val priorityLetter = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }

        val timestamp = formatter.format(Instant.now())
        val tagPart = if (tag != null) " [$tag]" else ""

        val baseLine = "[$timestamp] [$priorityLetter]$tagPart $message"
        val stackTrace = t?.stackTraceToString()?.let { "\n$it" }.orEmpty()
        val fullLine = "$baseLine$stackTrace"

        // Run through plugin pipeline
        var processed: String? = fullLine
        for (plugin in plugins()) {
            processed = plugin.process(processed ?: return)
            if (processed == null) return
        }

        synchronized(lock) {
            try {
                FileOutputStream(logFile, true).use { fos ->
                    OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                        writer.appendLine(processed)
                    }
                }
            } catch (_: Exception) {
                // Silently ignore file write failures to avoid infinite logging loops
            }
        }

        // Tee the processed (PII-masked, RPC-filtered) line into the live stream
        // for in-app viewers. tryEmit is non-suspending and thread-safe.
        logFlow.tryEmit(processed!!)
    }

    fun getLogFile(includeHeader: Boolean = true): File? {
        if (!logFile.exists() || logFile.length() == 0L) return null
        synchronized(lock) {
            exportFile.delete()
            if (includeHeader) {
                exportFile.writeText(buildDeviceHeader(appContext))
            }
            logFile.inputStream().use { input ->
                FileOutputStream(exportFile, true).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return exportFile
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearLogs() {
        synchronized(lock) {
            logFile.delete()
            exportFile.delete()
        }
        // Ensure any new collector observes an empty backlog after a clear.
        logFlow.resetReplayCache()
    }

    companion object {
        /** Max number of log lines retained in the live stream's replay cache. */
        const val LOG_STREAM_BUFFER_CAPACITY = 1000
    }
}

@Suppress("DEPRECATION")
private fun buildDeviceHeader(context: Context): String {
    val packageInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
    } ?: -1

    val userId = TraceManager.userId

    return buildString {
        appendLine("=".repeat(60))
        appendLine("DEVICE & APP INFO")
        appendLine("=".repeat(60))
        appendLine("App Version:    $versionName ($versionCode)")
        appendLine("Package:        ${context.packageName}")
        appendLine("User ID:        ${userId ?: "not set"}")
        appendLine("Device:         ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android:        ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Build:          ${Build.DISPLAY}")
        appendLine("ABI:            ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Locale:         ${Locale.getDefault()}")
        appendLine("Timezone:       ${TimeZone.getDefault().id}")
        appendLine("Exported:       ${Instant.now()}")
        appendLine("=".repeat(60))
        appendLine()
    }
}
