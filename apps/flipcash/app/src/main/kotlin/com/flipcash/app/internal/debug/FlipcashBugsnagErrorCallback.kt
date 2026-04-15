package com.flipcash.app.internal.debug

import com.bugsnag.android.OnErrorCallback
import io.grpc.StatusException

internal val FlipcashErrorCallback = OnErrorCallback onError@{ event ->
    val error = event.originalError ?: return@onError true
    val cause = error.cause ?: error

    // Discard gRPC deadline exceeded — these are expected transient timeouts
    if (cause is StatusException && cause.status.code == io.grpc.Status.Code.DEADLINE_EXCEEDED) {
        return@onError false
    }

    // Only tag unhandled errors — handled errors go through ErrorUtils.handleError()
    if (!event.isUnhandled) return@onError true

    event.addMetadata("alert", "slack_notify", true)
    event.addMetadata("alert", "error_type", cause.javaClass.simpleName)
    event.addMetadata("alert", "error_family", cause.javaClass.enclosingClass?.simpleName ?: "Unknown")
    true
}