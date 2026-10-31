package com.flipcash.app.internal.startup

import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Severity
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.utils.ErrorReporter

class BugsnagErrorReporter : ErrorReporter {
    override fun report(error: Throwable, cause: Throwable, isNotifiable: Boolean) {
        if (!Bugsnag.isStarted()) return
        Bugsnag.notify(error) { event ->
            // WARNING = needs active attention (also drives the Slack filter);
            // INFO = recorded for reference only, excluded from Slack.
            event.severity = if (isNotifiable) Severity.WARNING else Severity.INFO
            groupingHashFor(error, cause)?.let { event.groupingHash = it }
            true
        }
    }

    internal companion object {
        /**
         * Validation failures all share one `CodeServerError.<init>` frame, so Bugsnag would fold
         * every field into one group. Group by field path and rule instead.
         */
        fun groupingHashFor(error: Throwable, cause: Throwable): String? =
            ((error as? ValidationException) ?: (cause as? ValidationException))?.groupingHash
    }
}
