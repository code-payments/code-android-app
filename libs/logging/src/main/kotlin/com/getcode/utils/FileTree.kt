package com.getcode.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.bugsnag.android.Bugsnag
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

    fun getLogFile(): File? {
        if (!logFile.exists() || logFile.length() == 0L) return null
        synchronized(lock) {
            exportFile.delete()
            exportFile.writeText(buildDeviceHeader(appContext))
            logFile.inputStream().use { input ->
                FileOutputStream(exportFile, true).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return exportFile
    }

    fun clearLogs() {
        synchronized(lock) {
            logFile.delete()
            exportFile.delete()
        }
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

    val userId = if (Bugsnag.isStarted()) Bugsnag.getUser().id else null

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
