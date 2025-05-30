package com.getcode.opencode.internal.transactors

import com.getcode.utils.CodeServerError
import com.getcode.utils.MetadataBuilder
import com.getcode.utils.TraceType
import com.getcode.utils.trace

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
            type = TraceType.Process
        )
        return Result.failure(error)
    }
}