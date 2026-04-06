package com.getcode.opencode.model.core.errors

import com.codeinc.opencode.gen.transaction.v1.TransactionService.SubmitIntentResponse
import com.codeinc.opencode.gen.transaction.v1.errorDetails
import com.codeinc.opencode.gen.transaction.v1.reasonStringErrorDetails
import com.codeinc.opencode.gen.transaction.v1.deniedErrorDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubmitIntentErrorTest {

    private fun buildError(
        code: SubmitIntentResponse.Error.Code,
        reasonStrings: List<String> = emptyList(),
        deniedReasons: List<String> = emptyList(),
    ): SubmitIntentResponse.Error {
        val builder = SubmitIntentResponse.Error.newBuilder()
            .setCode(code)

        reasonStrings.forEach { reason ->
            builder.addErrorDetails(errorDetails {
                reasonString = reasonStringErrorDetails { this.reason = reason }
            })
        }

        deniedReasons.forEach { reason ->
            builder.addErrorDetails(errorDetails {
                denied = deniedErrorDetails { this.reason = reason }
            })
        }

        return builder.build()
    }

    // --- Code mapping ---

    @Test
    fun deniedCodeMapsToDenied() {
        val error = SubmitIntentError.typed(
            buildError(SubmitIntentResponse.Error.Code.DENIED)
        )
        assertIs<SubmitIntentError.Denied>(error)
    }

    @Test
    fun invalidIntentCodeMapsToInvalidIntent() {
        val error = SubmitIntentError.typed(
            buildError(SubmitIntentResponse.Error.Code.INVALID_INTENT)
        )
        assertIs<SubmitIntentError.InvalidIntent>(error)
    }

    @Test
    fun signatureErrorCodeMapsToSignature() {
        val error = SubmitIntentError.typed(
            buildError(SubmitIntentResponse.Error.Code.SIGNATURE_ERROR)
        )
        assertIs<SubmitIntentError.Signature>(error)
    }

    @Test
    fun staleStateCodeMapsToStaleState() {
        val error = SubmitIntentError.typed(
            buildError(SubmitIntentResponse.Error.Code.STALE_STATE)
        )
        assertIs<SubmitIntentError.StaleState>(error)
    }

    // Note: UNRECOGNIZED cannot be set via proto builders (throws IllegalArgumentException).
    // That code path is only reachable when the server sends an unknown enum value.

    // --- Reason string extraction ---

    @Test
    fun invalidIntentExtractsReasonStrings() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.INVALID_INTENT,
                reasonStrings = listOf("bad amount", "missing account")
            )
        )
        assertIs<SubmitIntentError.InvalidIntent>(error)
        assertTrue(error.message!!.contains("bad amount"))
        assertTrue(error.message!!.contains("missing account"))
    }

    @Test
    fun staleStateExtractsReasonStrings() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.STALE_STATE,
                reasonStrings = listOf("nonce expired")
            )
        )
        assertIs<SubmitIntentError.StaleState>(error)
        assertEquals("nonce expired", error.message)
    }

    @Test
    fun deniedExtractsDeniedReasons() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.DENIED,
                deniedReasons = listOf("spam detected")
            )
        )
        assertIs<SubmitIntentError.Denied>(error)
        assertTrue(error.message!!.contains("spam detected"))
    }

    @Test
    fun invalidIntentWithNoReasonsHasEmptyMessage() {
        val error = SubmitIntentError.typed(
            buildError(SubmitIntentResponse.Error.Code.INVALID_INTENT)
        )
        assertIs<SubmitIntentError.InvalidIntent>(error)
        assertEquals("", error.message)
    }

    @Test
    fun emptyReasonStringsAreFiltered() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.INVALID_INTENT,
                reasonStrings = listOf("", "real reason", "")
            )
        )
        assertIs<SubmitIntentError.InvalidIntent>(error)
        assertEquals("real reason", error.message)
    }

    // --- Inheritance ---

    @Test
    fun allVariantsAreThrowable() {
        val errors = listOf(
            SubmitIntentError.Denied(listOf("reason")),
            SubmitIntentError.InvalidIntent(listOf("reason")),
            SubmitIntentError.Signature(),
            SubmitIntentError.StaleState(listOf("reason")),
            SubmitIntentError.Unrecognized(),
            SubmitIntentError.Other(RuntimeException("test")),
        )
        errors.forEach { assertTrue(it is Throwable) }
    }

    @Test
    fun otherWrausesCause() {
        val cause = RuntimeException("root cause")
        val error = SubmitIntentError.Other(cause)
        assertEquals(cause, error.cause)
        assertEquals("root cause", error.message)
    }
}
