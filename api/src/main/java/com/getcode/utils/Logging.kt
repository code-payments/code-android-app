package com.getcode.utils

import com.bugsnag.android.Bugsnag
import timber.log.Timber


fun startupLog(message: String, error: Throwable? = null) {
    val tag = "app-startup"

    Timber.tag(tag).let {
        if (error != null) {
            it.e(error, message)
        } else {
            it.d(message)
        }
    }

    if (Bugsnag.isStarted()) {
        Bugsnag.leaveBreadcrumb("$tag | $message")
    }

    error?.let(ErrorUtils::handleError)
}