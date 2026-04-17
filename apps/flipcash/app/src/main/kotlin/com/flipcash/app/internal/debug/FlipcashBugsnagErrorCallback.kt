package com.flipcash.app.internal.debug

import com.bugsnag.android.OnErrorCallback
import com.getcode.utils.ErrorUtils
import io.grpc.StatusException

internal val FlipcashErrorCallback = OnErrorCallback onError@{ event ->
    val error = event.originalError ?: return@onError true
    val cause = error.cause ?: error

    // Discard gRPC client-error / validation status codes — these are not bugs
    if (cause is StatusException && cause.status.code in ErrorUtils.ignoredGrpcStatusCodes) {
        return@onError false
    }

    // Discard handled gRPC INTERNAL — server-side errors, not actionable from the client
    if (!event.isUnhandled && cause is StatusException && cause.status.code == io.grpc.Status.Code.INTERNAL) {
        return@onError false
    }

    if (!event.isUnhandled) return@onError true

    true
}