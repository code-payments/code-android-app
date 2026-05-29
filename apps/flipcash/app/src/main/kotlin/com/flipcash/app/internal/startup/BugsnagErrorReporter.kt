package com.flipcash.app.internal.startup

import com.bugsnag.android.Bugsnag
import com.getcode.utils.ErrorReporter

class BugsnagErrorReporter : ErrorReporter {
    override fun report(error: Throwable, cause: Throwable, isNotifiable: Boolean) {
        if (!isNotifiable) return
        if (!Bugsnag.isStarted()) return
        Bugsnag.notify(error)
    }
}
