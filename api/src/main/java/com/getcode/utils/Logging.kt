package com.getcode.utils

import android.annotation.SuppressLint
import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Bugsnag
import timber.log.Timber
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
    type: TraceType = TraceType.Log,
    error: Throwable? = null
) {
    val tree = if (tag == null) Timber else Timber.tag(tag)
    val traceMessage = if (tag == null) message else "trace : $message"

    tree.d(traceMessage)

    if (Bugsnag.isStarted()) {
        val breadcrumb = if (tag != null) {
            "$tag | $traceMessage"
        } else {
            traceMessage
        }

        val breadcrumbType = type.toBugsnagBreadcrumbType()
        if (breadcrumbType != null) {
            Bugsnag.leaveBreadcrumb(
                breadcrumb,
                emptyMap(),
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
    error: Throwable? = null,
    block: () -> T
): T {
    var result: T
    val time = measureTime {
        result = block()
    }

    val newMessage = "$message took ${time.inWholeMilliseconds}ms"
    trace(newMessage, tag, type, error)

    return result
}