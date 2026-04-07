package com.getcode.utils

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FileTree(
    context: Context,
    private val plugins: () -> List<TraceLogPlugin>,
) : Timber.Tree() {

    private val traceDir = File(context.filesDir, "traces").apply { mkdirs() }
    private val logFile = File(traceDir, "trace.log")
    private val lock = Any()

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        .withZone(ZoneId.systemDefault())

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
    }

    fun getLogFile(): File? = if (logFile.exists() && logFile.length() > 0) logFile else null

    fun clearLogs() {
        synchronized(lock) {
            logFile.delete()
        }
    }
}
