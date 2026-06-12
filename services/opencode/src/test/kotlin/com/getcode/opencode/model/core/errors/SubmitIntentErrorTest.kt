package com.getcode.opencode.model.core.errors

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.SubmitIntentResponse
import com.codeinc.opencode.gen.transaction.v1.errorDetails
import com.codeinc.opencode.gen.transaction.v1.reasonStringErrorDetails
import com.codeinc.opencode.gen.transaction.v1.deniedErrorDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun staleStateWithGiftCardClaimedReasonIsGiftCardAlreadyClaimed() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.STALE_STATE,
                reasonStrings = listOf("gift card balance has already been claimed")
            )
        )
        assertIs<SubmitIntentError.StaleState>(error)
        assertTrue(error.isGiftCardAlreadyClaimed)
    }

    @Test
    fun staleStateWithOtherReasonIsNotGiftCardAlreadyClaimed() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.STALE_STATE,
                reasonStrings = listOf("nonce expired")
            )
        )
        assertIs<SubmitIntentError.StaleState>(error)
        assertFalse(error.isGiftCardAlreadyClaimed)
    }

    @Test
    fun deniedWithUnexpectedOwnerAccountReasonIsUnexpectedOwnerAccount() {
        val error = SubmitIntentError.Denied(listOf("unexpected owner account"))
        assertTrue(error.isUnexpectedOwnerAccount)
    }

    @Test
    fun deniedWithOtherReasonIsNotUnexpectedOwnerAccount() {
        val error = SubmitIntentError.Denied(listOf("some other reason"))
        assertFalse(error.isUnexpectedOwnerAccount)
    }

    @Test
    fun deniedWithNoReasonsIsNotUnexpectedOwnerAccount() {
        val error = SubmitIntentError.Denied(emptyList())
        assertFalse(error.isUnexpectedOwnerAccount)
    }

    @Test
    fun staleStateWithRaceDetectedIsRaceCondition() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.STALE_STATE,
                reasonStrings = listOf("race detected: cached balance version is stale")
            )
        )
        assertIs<SubmitIntentError.StaleState>(error)
        assertTrue(error.isRaceCondition)
    }

    @Test
    fun staleStateWithOtherReasonIsNotRaceCondition() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.STALE_STATE,
                reasonStrings = listOf("intent already exists")
            )
        )
        assertIs<SubmitIntentError.StaleState>(error)
        assertFalse(error.isRaceCondition)
    }

    @Test
    fun staleStateWithAccountAlreadyOpenedIsAccountAlreadyOpened() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.STALE_STATE,
                reasonStrings = listOf("actions[0]: account is already opened")
            )
        )
        assertIs<SubmitIntentError.StaleState>(error)
        assertTrue(error.isAccountAlreadyOpened)
        assertTrue(error.isExpected)
        assertFalse(error.isNotifiable)
    }

    @Test
    fun staleStateWithOtherReasonIsNotAccountAlreadyOpened() {
        val error = SubmitIntentError.typed(
            buildError(
                SubmitIntentResponse.Error.Code.STALE_STATE,
                reasonStrings = listOf("nonce expired")
            )
        )
        assertIs<SubmitIntentError.StaleState>(error)
        assertFalse(error.isAccountAlreadyOpened)
    }

    @Test
    fun invalidIntentWithPaymentNoOpIsNotNotifiable() {
        val error = SubmitIntentError.InvalidIntent(listOf("payment is a no-op"))
        assertTrue(error.isPaymentNoOp)
        assertTrue(error.isExpected)
        assertFalse(error.isNotifiable)
    }

    @Test
    fun invalidIntentWithOtherReasonIsNotifiable() {
        val error = SubmitIntentError.InvalidIntent(listOf("bad amount"))
        assertFalse(error.isPaymentNoOp)
        assertFalse(error.isExpected)
        assertTrue(error.isNotifiable)
    }

    @Test
    fun otherWrausesCause() {
        val cause = RuntimeException("root cause")
        val error = SubmitIntentError.Other(cause)
        assertEquals(cause, error.cause)
        assertEquals("root cause", error.message)
    }
}
