package com.getcode.opencode.internal.network.extensions

import com.getcode.utils.SuppressibleException
import io.grpc.Status
import io.grpc.StatusException

fun <T, R> Result<T>.foldWithSuppression(
    onSuccess: (T) -> R,
    onFailure: (Throwable) -> R
): R = fold(
    onSuccess = onSuccess,
    onFailure = { throwable ->
        val shouldSuppress = when {
            didReceiveGoAway(throwable) -> true
            else -> false
        }
        if (shouldSuppress) {
            return@fold onFailure(SuppressibleException(throwable))
        }

        onFailure(throwable)
    }
)

private fun didReceiveGoAway(throwable: Throwable): Boolean =
    (throwable is StatusException &&
            throwable.status == Status.UNAVAILABLE &&
            throwable.cause?.message?.contains("Goaway") == true)