package com.getcode.utils

open class FlipchatServerError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Throwable(
    message = "FlipchatServerError: $message",
    cause = cause
)