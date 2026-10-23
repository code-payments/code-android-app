package com.getcode.utils

interface ErrorReporter {
    fun report(
        error: Throwable,
        cause: Throwable,
        isNotifiable: Boolean,
    )
}
