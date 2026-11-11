package com.getcode.utils

/**
 * Lets a transport tell [ErrorUtils] how to treat its own exception types, so `:libs:logging` doesn't
 * depend on that transport. `:libs:logging:grpc` provides the gRPC one.
 */
fun interface ErrorClassifier {
    /** How to treat [throwable], or `null` when it isn't one of this classifier's types. */
    fun classify(throwable: Throwable): ErrorClass?
}

enum class ErrorClass {
    /** Dropped before logging. */
    Ignore,

    /** Logged, but never shown in the top bar or sent to a reporter. */
    LogOnly,
}
