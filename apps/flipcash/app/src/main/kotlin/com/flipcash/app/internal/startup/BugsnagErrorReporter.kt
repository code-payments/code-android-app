package com.flipcash.app.internal.startup

import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Severity
import com.getcode.utils.ErrorReporter

class BugsnagErrorReporter : ErrorReporter {
    override fun report(error: Throwable, cause: Throwable, isNotifiable: Boolean) {
        if (!Bugsnag.isStarted()) return
        Bugsnag.notify(error) { event ->
            // WARNING = needs active attention (also drives the Slack filter);
            // INFO = recorded for reference only, excluded from Slack.
            event.severity = if (isNotifiable) Severity.WARNING else Severity.INFO
            true
        }
    }
}
