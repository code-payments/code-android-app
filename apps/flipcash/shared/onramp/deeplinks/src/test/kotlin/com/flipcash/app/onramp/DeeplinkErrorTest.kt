package com.flipcash.app.onramp

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeeplinkErrorTest {

    @Test
    fun `fromCode Long - Disconnected`() {
        assertEquals(DeeplinkError.Disconnected, DeeplinkError.fromCode(4900L))
    }

    @Test
    fun `fromCode Long - Unauthorized`() {
        assertEquals(DeeplinkError.Unauthorized, DeeplinkError.fromCode(4100L))
    }

    @Test
    fun `fromCode Long - UserRejectedRequest`() {
        assertEquals(DeeplinkError.UserRejectedRequest, DeeplinkError.fromCode(4001L))
    }

    @Test
    fun `fromCode Long - InvalidInput`() {
        assertEquals(DeeplinkError.InvalidInput, DeeplinkError.fromCode(-32000L))
    }

    @Test
    fun `fromCode Long - RequestedResourceNotAvailable`() {
        assertEquals(DeeplinkError.RequestedResourceNotAvailable, DeeplinkError.fromCode(-32002L))
    }

    @Test
    fun `fromCode Long - TransactionRejected`() {
        assertEquals(DeeplinkError.TransactionRejected, DeeplinkError.fromCode(-32003L))
    }

    @Test
    fun `fromCode Long - MethodNotFound`() {
        assertEquals(DeeplinkError.MethodNotFound, DeeplinkError.fromCode(-32601L))
    }

    @Test
    fun `fromCode Long - InternalError`() {
        assertEquals(DeeplinkError.InternalError, DeeplinkError.fromCode(-32603L))
    }

    @Test
    fun `fromCode null Long returns Unknown`() {
        assertEquals(DeeplinkError.Unknown, DeeplinkError.fromCode(null as Long?))
    }

    @Test
    fun `fromCode unrecognized Long returns Unknown`() {
        assertEquals(DeeplinkError.Unknown, DeeplinkError.fromCode(9999L))
    }

    @Test
    fun `fromCode String - UserRejectedRequest`() {
        assertEquals(DeeplinkError.UserRejectedRequest, DeeplinkError.fromCode("4001"))
    }

    @Test
    fun `fromCode non-numeric String returns Unknown`() {
        assertEquals(DeeplinkError.Unknown, DeeplinkError.fromCode("invalid"))
    }

    @Test
    fun `fromCode null String returns Unknown`() {
        assertEquals(DeeplinkError.Unknown, DeeplinkError.fromCode(null as String?))
    }

    @Test
    fun `DeeplinkOnRampError subtypes are Throwable`() {
        val errors: List<Throwable> = listOf(
            DeeplinkOnRampError.FailedToSendTransaction(message = "fail"),
            DeeplinkOnRampError.WalletProvidedError(
                error = DeeplinkError.Disconnected,
                message = "disconnected"
            ),
            DeeplinkOnRampError.FailedToCreateTransaction(message = "fail"),
            DeeplinkOnRampError.DecryptionError(message = "fail"),
        )

        errors.forEach { error ->
            assertTrue(error is Throwable, "${error::class.simpleName} should be Throwable")
        }
    }

    @Test
    fun `WalletProvidedError code matches error code`() {
        val error = DeeplinkOnRampError.WalletProvidedError(
            error = DeeplinkError.UserRejectedRequest,
            message = "rejected"
        )

        assertEquals(DeeplinkError.UserRejectedRequest.code, error.code)
        assertEquals(4001L, error.code)
    }
}
