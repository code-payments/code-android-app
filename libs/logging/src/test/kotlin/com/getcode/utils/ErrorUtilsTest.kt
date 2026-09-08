package com.getcode.utils

import java.net.UnknownHostException
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
}
