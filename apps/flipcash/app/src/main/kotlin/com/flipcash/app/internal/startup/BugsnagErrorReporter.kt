package com.flipcash.app.internal.startup

import com.bugsnag.android.Bugsnag
import com.getcode.utils.ErrorReporter

class BugsnagErrorReporter : ErrorReporter {
    override fun report(error: Throwable, cause: Throwable, isNotifiable: Boolean) {
        if (!Bugsnag.isStarted()) return
        Bugsnag.notify(error) { event ->
            if (isNotifiable) {
                event.addMetadata("alert", "slack_notify", true)
                event.addMetadata("alert", "error_type", cause.javaClass.simpleName)
                event.addMetadata(
                    "alert", "error_family",
                    cause.javaClass.enclosingClass?.simpleName ?: "Unknown"
                )
            }
            true
        }
    }
}
