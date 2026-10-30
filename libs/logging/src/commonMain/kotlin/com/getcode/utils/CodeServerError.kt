package com.getcode.utils

open class CodeServerError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Throwable(
    message = "CodeServerError: $message",
    cause = cause
)