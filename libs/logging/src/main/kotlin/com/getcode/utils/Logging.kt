package com.getcode.utils

import android.annotation.SuppressLint
import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Bugsnag
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.measureTime

sealed interface TraceType {
    /**
     * This is not forwarded to logging services
     */

    data object Silent : TraceType

    /**
     * An error event
     */
    data object Error: TraceType

    /**
     * A log message
     */
    data object Log : TraceType

    /**
     * A navigation event, such as a window opening or closing
     */
    data object Navigation : TraceType

    /**
     * A background process such as a database query
     */
    data object Process : TraceType

    /**
     * A network request
     */
    data object Network : TraceType

    /**
     * A change in application state, such as launch or memory warning
     */
    data object StateChange : TraceType

    /**
     * A user action, such as tapping a button
     */
    data object User : TraceType
}

private fun TraceType.toBugsnagBreadcrumbType(): BreadcrumbType? {
    return when (this) {
        TraceType.Silent -> null
        TraceType.Error -> BreadcrumbType.ERROR
        TraceType.Log -> BreadcrumbType.LOG
        TraceType.Navigation -> BreadcrumbType.NAVIGATION
        TraceType.Network -> BreadcrumbType.REQUEST
        TraceType.Process -> BreadcrumbType.PROCESS
        TraceType.StateChange -> BreadcrumbType.STATE
        TraceType.User -> BreadcrumbType.USER
    }
}

@SuppressLint("TimberExceptionLogging")
fun trace(
    message: String,
    tag: String? = null,
    metadata: MetadataBuilder.() -> Unit = {},
    error: Throwable? = null,
    type: TraceType = if (error != null) TraceType.Error else TraceType.Log,
) {
    val tagBlock = tag?.let { "[$it] " }
    val tree = if (tagBlock == null) Timber else Timber.tag(tagBlock)

    when (type) {
        TraceType.Error -> tree.e(message)
        TraceType.Log -> tree.d(message)
        TraceType.Navigation -> tree.d(message)
        TraceType.Network -> tree.i(message)
        TraceType.Process -> tree.i(message)
        TraceType.Silent -> tree.d(message)
        TraceType.StateChange -> tree.i(message)
        TraceType.User -> tree.d(message)
    }

    val metadataMap = metadata { metadata() }

    if (Bugsnag.isStarted()) {
        val breadcrumb = if (tag != null) {
            "$tagBlock $message"
        } else {
            message
        }

        val breadcrumbType = type.toBugsnagBreadcrumbType()
        if (breadcrumbType != null) {
            Bugsnag.leaveBreadcrumb(
                breadcrumb,
                metadataMap,
                breadcrumbType
            )
        }
    }

    error?.let(ErrorUtils::handleError)
}

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

suspend fun <T> timedTraceSuspend(
    message: String,
    tag: String? = null,
    type: TraceType = TraceType.Log,
    metadata: MetadataBuilder.() -> Unit = {},
    error: Throwable? = null,
    onComplete: (T, Duration) -> Unit = { _, _ -> },
    block: suspend () -> T
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

class MetadataBuilder {
    private val map = mutableMapOf<String, Any>()

    infix fun String.to(value: Any) {
        map[this] = value
    }

    fun build(): Map<String, Any> = map
}

fun metadata(block: MetadataBuilder.() -> Unit): Map<String, Any> {
    val builder = MetadataBuilder()
    builder.block()
    return builder.build()
}