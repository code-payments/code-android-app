package com.getcode.utils

import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorUtilsTest {

    private class Benign : Throwable("benign"), UnreportedError

    private class Fault : Throwable("fault")

    @Test
    fun `a plain throwable is reported`() {
        val error = Fault()
        assertTrue(ErrorUtils.shouldReport(error, error))
    }

    @Test
    fun `an UnreportedError is not reported`() {
        val error = Benign()
        assertFalse(ErrorUtils.shouldReport(error, error))
    }

    @Test
    fun `an UnreportedError in the cause position is not reported`() {
        assertFalse(ErrorUtils.shouldReport(Fault(), Benign()))
    }

    @Test
    fun `ignored error types stay ignored`() {
        val error = UnknownHostException("no dns")
        assertFalse(ErrorUtils.shouldReport(error, error))
        assertFalse(ErrorUtils.shouldReport(Fault(), error))
    }

    @Test
    fun `a wrapped FCM registration failure is a transient GMS error`() {
        for (code in listOf("AUTHENTICATION_FAILED", "INTERNAL_SERVER_ERROR", "SERVICE_NOT_AVAILABLE")) {
            val error = IOException("FCM Registration failed!", ExecutionException(IOException(code)))
            assertTrue(ErrorUtils.isGmsTransientError(error), code)
        }
    }

    @Test
    fun `a bare GMS error code is a transient GMS error`() {
        assertTrue(ErrorUtils.isGmsTransientError(IOException("InternalServerError")))
    }

    @Test
    fun `an unrelated IOException is not a transient GMS error`() {
        val error = IOException("FCM Registration failed!", ExecutionException(IOException("disk full")))
        assertFalse(ErrorUtils.isGmsTransientError(error))
        assertFalse(ErrorUtils.isGmsTransientError(Fault()))
    }
}
