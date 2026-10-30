package com.getcode.opencode.internal.transactors

import com.getcode.utils.CodeServerError
import com.getcode.utils.MetadataBuilder
import com.getcode.utils.TraceType
import com.getcode.utils.trace

/**
 * Base class for bill/gift-card transactors. Provides a common [logAndFail]
 * helper that traces the error and wraps it in a failed [Result].
 */
abstract class Transactor<E: CodeServerError>(
    protected val tag: String
) {
    protected fun <T> logAndFail(
        error: Throwable,
        metadata: MetadataBuilder.() -> Unit = {},
    ): Result<T> {
        trace(
            tag = tag,
            message = error.message.orEmpty(),
            metadata = metadata,
            type = TraceType.Error,
            error = error
        )
        return Result.failure(error)
    }
}