package com.getcode.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime

/**
 * Categorizes trace events for routing through Timber and [BreadcrumbSink]s.
 *
 * Each type maps to a Timber log level and is forwarded to all registered
 * breadcrumb sinks, except [Silent] which is only written to the local log file.
 */
sealed interface TraceType {
    /** Local-only; not forwarded to external logging services. */
    data object Silent : TraceType

    /** An error event. */
    data object Error: TraceType

    /** A general log message. */
    data object Log : TraceType

    /** A navigation event, such as a screen opening or closing. */
    data object Navigation : TraceType

    /** A background process such as a database query. */
    data object Process : TraceType

    /** A network request. */
    data object Network : TraceType

    /** A change in application state, such as launch or memory warning. */
    data object StateChange : TraceType

    /** A user action, such as tapping a button. */
    data object User : TraceType
}

/**
 * Central coordinator for the tracing/logging subsystem.
 *
 * Must be [initialized][initialize] before any [trace] calls will produce output.
 * Calls to [trace] made before initialization are silently dropped so that
 * modules which log during early startup don't crash.
 *
 * Supports two extension points:
 * - [TraceLogPlugin]s — transform log messages before they reach Timber (e.g. PII masking).
 * - [BreadcrumbSink]s — forward structured breadcrumbs to external services (e.g. Bugsnag).
 */
object TraceManager {
    private var fileTree: FileTree? = null

    /** `true` after [initialize] has been called. [trace] is a no-op until then. */
    var initialized: Boolean = false
        private set

    /** When `true`, RPC request/response body lines are written to the log file. */
    @Volatile
    var includeRpcBodies: Boolean = false

    val plugins: MutableList<TraceLogPlugin> = mutableListOf()
    private val breadcrumbSinks = mutableListOf<BreadcrumbSink>()
    private var _userId: String? = null
    private var onUserIdChanged: ((String?) -> Unit)? = null

    /** The current user identifier, forwarded to error reporters when set. */
    var userId: String?
        get() = _userId
        set(value) {
            _userId = value
            onUserIdChanged?.invoke(value)
        }

    /** Register a listener invoked whenever [userId] changes. */
    fun setOnUserIdChanged(listener: ((String?) -> Unit)?) {
        onUserIdChanged = listener
    }

    @Volatile
    private var clockDriftMillis: Long? = null
    @Volatile
    private var clockDriftSource: ClockSource = ClockSource.Unknown
    @Volatile
    private var clockDriftSyncElapsedRealtime: Long? = null

    /**
     * Records the most recent measured clock drift (device time minus authoritative server/true
     * time; positive means the device clock is ahead). [source] identifies where the measurement
     * came from, e.g. `"sntp"` or `"event-stream"`.
     */
    fun recordClockDrift(driftMillis: Long, source: ClockSource) {
        clockDriftMillis = driftMillis
        clockDriftSource = source
        clockDriftSyncElapsedRealtime = SystemClock.elapsedRealtime()
    }

    /** Latest clock-drift measurement, or `null` if no server time has been observed yet. */
    fun clockDriftSnapshot(): ClockDriftSnapshot? {
        val drift = clockDriftMillis ?: return null
        val ageMillis = clockDriftSyncElapsedRealtime?.let { SystemClock.elapsedRealtime() - it }
        return ClockDriftSnapshot(
            driftMillis = drift,
            source = clockDriftSource,
            ageMillis = ageMillis,
        )
    }

    fun addPlugin(plugin: TraceLogPlugin) {
        plugins.add(plugin)
    }

    fun removePlugin(plugin: TraceLogPlugin) {
        plugins.remove(plugin)
    }

    fun addSink(sink: BreadcrumbSink) { breadcrumbSinks.add(sink) }
    fun removeSink(sink: BreadcrumbSink) { breadcrumbSinks.remove(sink) }
    fun sinks(): List<BreadcrumbSink> = breadcrumbSinks

    /**
     * Bootstraps Timber with a [FileTree], plants default plugins
     * ([PiiMaskingPlugin], [RpcBodyFilterPlugin]), and marks the manager
     * as [initialized]. Safe to call multiple times; subsequent calls are no-ops.
     */
    fun initialize(context: Context) {
        if (initialized) return
        val tree = FileTree(context, plugins = { plugins })
        fileTree = tree
        addPlugin(PiiMaskingPlugin())
        addPlugin(RpcBodyFilterPlugin())
        Timber.plant(tree)
        initialized = true
    }

    /** Returns the current log file, or `null` if not yet initialized. */
    fun getLogFile(includeHeader: Boolean = true): File? = fileTree?.getLogFile(includeHeader)

    /**
     * Hot stream of processed log lines for live in-app viewers. Emits only
     * after [initialize] has been called; before initialization, returns an
     * empty [SharedFlow] that will never emit.
     */
    val logStream: SharedFlow<String>
        get() = fileTree?.logStream ?: EmptyLogStream

    private val EmptyLogStream: SharedFlow<String> = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
    ).asSharedFlow()

    fun clearLogs() {
        fileTree?.clearLogs()
    }

    /** Tears down all state. Intended for tests only. */
    @androidx.annotation.VisibleForTesting
    internal fun reset() {
        fileTree?.let { Timber.uproot(it) }
        fileTree = null
        initialized = false
        plugins.clear()
        breadcrumbSinks.clear()
        _userId = null
        onUserIdChanged = null
        includeRpcBodies = false
        clockDriftMillis = null
        clockDriftSource = ClockSource.Unknown
        clockDriftSyncElapsedRealtime = null
    }
}

/** A point-in-time clock-drift measurement captured by [TraceManager.recordClockDrift]. */
data class ClockDriftSnapshot(
    /** Device time minus true/server time, in milliseconds. Positive = device clock is ahead. */
    val driftMillis: Long,
    /** Where the measurement came from, e.g. `"sntp"` or `"event-stream"`. */
    val source: ClockSource,
    /** Milliseconds since the measurement was taken, or `null` if unknown. */
    val ageMillis: Long?,
)

enum class ClockSource {
    Unknown, Sntp, EventStream
}

/**
 * Primary logging entry point. Writes [message] to Timber and forwards a
 * breadcrumb to every registered [BreadcrumbSink].
 *
 * No-op if [TraceManager] has not been [initialized][TraceManager.initialize],
 * so callers in early startup paths don't need their own guard.
 *
 * @param message Human-readable description of the event.
 * @param tag Optional tag rendered as `[tag]` prefix in the log line.
 * @param metadata Builder for structured key-value pairs appended to the log line.
 * @param error If non-null, forwarded to [ErrorUtils.handleError] after logging.
 * @param type Categorization that determines the Timber log level.
 */
@SuppressLint("TimberExceptionLogging")
fun trace(
    message: String,
    tag: String? = null,
    metadata: MetadataBuilder.() -> Unit = {},
    error: Throwable? = null,
    type: TraceType = if (error != null) TraceType.Error else TraceType.Log,
) {
    if (!TraceManager.initialized) return

    val tagBlock = tag?.let { "[$it] " }
    val tree = if (tagBlock == null) Timber else Timber.tag(tagBlock)

    val metadataMap = metadata { metadata() }

    val logMessage = if (metadataMap.isNotEmpty()) {
        val metaString = metadataMap.entries.joinToString(", ") { "${it.key}=${it.value}" }
        "$message | $metaString"
    } else {
        message
    }

    when (type) {
        TraceType.Error -> tree.e(logMessage)
        TraceType.Log -> tree.d(logMessage)
        TraceType.Navigation -> tree.d(logMessage)
        TraceType.Network -> tree.i(logMessage)
        TraceType.Process -> tree.d(logMessage)
        TraceType.Silent -> tree.d(logMessage)
        TraceType.StateChange -> tree.d(logMessage)
        TraceType.User -> tree.d(logMessage)
    }

    val rawBreadcrumb = if (tag != null) "$tagBlock $message" else message
    val sinks = TraceManager.sinks()
    if (sinks.isNotEmpty()) {
        val (maskedBreadcrumb, maskedMetadata) = applyPluginsForBreadcrumb(
            rawBreadcrumb, metadataMap, TraceManager.plugins
        )
        sinks.forEach { sink ->
            sink.record(maskedBreadcrumb, maskedMetadata, type)
        }
    }

    error?.let(ErrorUtils::handleError)
}

/**
 * Executes [block], measures its wall-clock duration, and emits a [trace]
 * with the duration appended to [metadata].
 *
 * The block always runs even if [TraceManager] is not initialized; only
 * the trace output is gated.
 *
 * @param onComplete Called after tracing with the block's result and measured duration.
 */
fun <T> timedTrace(
    message: String,
    tag: String? = null,
    type: TraceType = TraceType.Log,
    metadata: MetadataBuilder.() -> Unit = {},
    error: Throwable? = null,
    onComplete: (T, Duration) -> Unit = { _, _ -> },
    block: () -> T
): T {
    var result: T
    val time = measureTime {
        result = block()
    }

    val timedMetadata: MetadataBuilder.() -> Unit = {
        // Add the original metadata
        metadata()
        "duration" to time.inWholeMilliseconds
    }

    trace(message = message, tag = tag, type = type, metadata = timedMetadata, error = error)
    onComplete(result, time)
    return result
}

/**
 * Suspend variant of [timedTrace] that additionally tracks named steps.
 *
 * Call the `onStep` lambda inside [block] to record intermediate durations;
 * each step's elapsed time (relative to the previous step) is appended to
 * the metadata alongside the total duration.
 */
suspend fun <T> timedTraceSuspend(
    message: String,
    tag: String? = null,
    type: TraceType = TraceType.Log,
    metadata: MetadataBuilder.() -> Unit = {},
    error: Throwable? = null,
    onComplete: (T, Duration) -> Unit = { _, _ -> },
    block: suspend (onStep: (String) -> Unit) -> T
): T {
    var result: T
    val breadcrumbs = mutableMapOf<String, Duration>()
    val clock = TimeSource.Monotonic // Use monotonic clock for precise timing
    val startMark = clock.markNow() // Mark the start of the operation
    var previousMark = startMark

    val time = measureTime {
        result = block {stepName ->
            val currentMark = clock.markNow()
            val stepDuration = currentMark - previousMark
            breadcrumbs[stepName] = stepDuration
            previousMark = currentMark
        }
    }

    val timedMetadata: MetadataBuilder.() -> Unit = {
        // Add the original metadata
        metadata()
        breadcrumbs.entries.onEach {
            it.key to it.value.inWholeMilliseconds
        }
        "total duration" to time.inWholeMilliseconds
    }

    trace(message = message, tag = tag, type = type, metadata = timedMetadata, error = error)
    onComplete(result, time)
    return result
}

/**
 * DSL builder for structured key-value metadata attached to [trace] calls.
 *
 * ```
 * trace("payment sent", metadata = {
 *     "amount" to 5.00
 *     "token"  to "JFY"
 *     "currency" to "USD"
 * })
 * ```
 */
class MetadataBuilder {
    private val map = mutableMapOf<String, Any>()

    infix fun String.to(value: Any) {
        map[this] = value
    }

    fun build(): Map<String, Any> = map
}

/** Convenience factory that builds a metadata map from [block]. */
private fun metadata(block: MetadataBuilder.() -> Unit): Map<String, Any> {
    val builder = MetadataBuilder()
    builder.block()
    return builder.build()
}

private fun applyPluginsForBreadcrumb(
    message: String,
    metadata: Map<String, Any>,
    plugins: List<TraceLogPlugin>,
): Pair<String, Map<String, Any>> {
    val maskedMessage = plugins.fold(message) { acc, plugin ->
        plugin.process(acc) ?: acc
    }
    val maskedMetadata = metadata.mapValues { (_, value) ->
        plugins.fold(value.toString()) { acc, plugin ->
            plugin.process(acc) ?: acc
        }
    }
    return maskedMessage to maskedMetadata
}