package com.flipcash.app.internal.debug

import com.bugsnag.android.OnErrorCallback

internal val FlipcashErrorCallback = OnErrorCallback onError@{ event ->
    // Only tag unhandled errors — handled errors go through ErrorUtils.handleError()
    if (!event.isUnhandled) return@onError true
    val error = event.originalError ?: return@onError true

    val cause = error.cause ?: error
    event.addMetadata("alert", "slack_notify", true)
    event.addMetadata("alert", "error_type", cause.javaClass.simpleName)
    event.addMetadata("alert", "error_family", cause.javaClass.enclosingClass?.simpleName ?: "Unknown")
    true
}