package com.getcode.utils.grpc

import com.getcode.utils.ErrorClass
import com.getcode.utils.ErrorClassifier
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException

/**
 * Tells [com.getcode.utils.ErrorUtils] how to treat gRPC status exceptions: codes in
 * [ignoredStatusCodes] are dropped, and every other status is logged but not reported.
 * Register it once at startup with `ErrorUtils.addClassifier(GrpcErrorClassifier)`.
 */
object GrpcErrorClassifier : ErrorClassifier {

    val ignoredStatusCodes: Set<Status.Code> = setOf(
        // Transport/transient
        Status.Code.UNAVAILABLE,
        Status.Code.CANCELLED,
        Status.Code.DEADLINE_EXCEEDED,
        Status.Code.UNIMPLEMENTED,
        // Client-error / validation (not bugs)
        Status.Code.NOT_FOUND,
        Status.Code.ALREADY_EXISTS,
        Status.Code.PERMISSION_DENIED,
        Status.Code.UNAUTHENTICATED,
        Status.Code.FAILED_PRECONDITION,
        Status.Code.OUT_OF_RANGE,
        Status.Code.RESOURCE_EXHAUSTED,
    )

    override fun classify(throwable: Throwable): ErrorClass? {
        val code = when (throwable) {
            is StatusRuntimeException -> throwable.status.code
            is StatusException -> throwable.status.code
            else -> return null
        }
        return if (code in ignoredStatusCodes) ErrorClass.Ignore else ErrorClass.LogOnly
    }
}
