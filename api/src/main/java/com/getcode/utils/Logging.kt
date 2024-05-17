package com.getcode.utils

import android.annotation.SuppressLint
import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Bugsnag
import timber.log.Timber

sealed interface TraceType {
    /**
     * An error was sent to Bugsnag (internal use only)
     */

    data object Error : TraceType

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

private fun TraceType.toBugsnagBreadcrumbType(): BreadcrumbType {
    return when (this) {
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

        Bugsnag.leaveBreadcrumb(
            breadcrumb,
            emptyMap(),
            type.toBugsnagBreadcrumbType()
        )
    }

    error?.let(ErrorUtils::handleError)
}