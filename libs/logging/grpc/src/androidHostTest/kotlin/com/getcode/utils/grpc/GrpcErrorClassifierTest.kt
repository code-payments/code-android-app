package com.getcode.utils.grpc

import com.getcode.utils.ErrorClass
import io.grpc.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrpcErrorClassifierTest {

    @Test
    fun `ignored status codes are dropped, as either exception type`() {
        for (code in GrpcErrorClassifier.ignoredStatusCodes) {
            val status = Status.fromCode(code)
            assertEquals(ErrorClass.Ignore, GrpcErrorClassifier.classify(status.asRuntimeException()), "$code")
            assertEquals(ErrorClass.Ignore, GrpcErrorClassifier.classify(status.asException()), "$code")
        }
    }

    @Test
    fun `other status codes are logged but not reported`() {
        val others = Status.Code.entries - GrpcErrorClassifier.ignoredStatusCodes
        for (code in others) {
            assertEquals(ErrorClass.LogOnly, GrpcErrorClassifier.classify(Status.fromCode(code).asException()), "$code")
        }
    }

    @Test
    fun `non-gRPC errors get no opinion`() {
        assertNull(GrpcErrorClassifier.classify(IllegalStateException("boom")))
    }
}
