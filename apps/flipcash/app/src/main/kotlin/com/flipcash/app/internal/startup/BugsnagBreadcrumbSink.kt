package com.flipcash.app.internal.startup

import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Bugsnag
import com.getcode.utils.BreadcrumbSink
import com.getcode.utils.TraceType

class BugsnagBreadcrumbSink : BreadcrumbSink {
    override fun record(message: String, metadata: Map<String, Any>, type: TraceType) {
        if (!Bugsnag.isStarted()) return
        val breadcrumbType = type.toBugsnagBreadcrumbType() ?: return
        Bugsnag.leaveBreadcrumb(message, metadata, breadcrumbType)
    }
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
