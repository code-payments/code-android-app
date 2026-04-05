package com.getcode.opencode.model.core.errors

import com.codeinc.opencode.gen.transaction.v1.TransactionService.StatefulSwapResponse
import com.codeinc.opencode.gen.transaction.v1.errorDetails
import com.codeinc.opencode.gen.transaction.v1.reasonStringErrorDetails
import com.codeinc.opencode.gen.transaction.v1.deniedErrorDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SwapErrorTest {

    private fun buildError(
        code: StatefulSwapResponse.Error.Code,
        reasonStrings: List<String> = emptyList(),
        deniedReasons: List<String> = emptyList(),
    ): StatefulSwapResponse.Error {
        val builder = StatefulSwapResponse.Error.newBuilder()
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
        val error = SwapError.typed(
            buildError(StatefulSwapResponse.Error.Code.DENIED)
        )
        assertIs<SwapError.Denied>(error)
    }

    @Test
    fun signatureErrorCodeMapsToSignature() {
        val error = SwapError.typed(
            buildError(StatefulSwapResponse.Error.Code.SIGNATURE_ERROR)
        )
        assertIs<SwapError.Signature>(error)
    }

    @Test
    fun invalidSwapCodeMapsToInvalidSwap() {
        val error = SwapError.typed(
            buildError(StatefulSwapResponse.Error.Code.INVALID_SWAP)
        )
        assertIs<SwapError.InvalidSwap>(error)
    }

    // Note: UNRECOGNIZED cannot be set via proto builders (throws IllegalArgumentException).
    // That code path is only reachable when the server sends an unknown enum value.

    // --- Reason extraction ---

    @Test
    fun deniedExtractsDeniedReasons() {
        val error = SwapError.typed(
            buildError(
                StatefulSwapResponse.Error.Code.DENIED,
                deniedReasons = listOf("insufficient balance")
            )
        )
        assertIs<SwapError.Denied>(error)
        assertTrue(error.message!!.contains("insufficient balance"))
    }

    @Test
    fun invalidSwapExtractsReasonStrings() {
        val error = SwapError.typed(
            buildError(
                StatefulSwapResponse.Error.Code.INVALID_SWAP,
                reasonStrings = listOf("slippage too high", "pool depleted")
            )
        )
        assertIs<SwapError.InvalidSwap>(error)
        assertTrue(error.message!!.contains("slippage too high"))
        assertTrue(error.message!!.contains("pool depleted"))
    }

    @Test
    fun deniedWithNoReasonsHasEmptyMessage() {
        val error = SwapError.typed(
            buildError(StatefulSwapResponse.Error.Code.DENIED)
        )
        assertIs<SwapError.Denied>(error)
        assertEquals("", error.message)
    }

    // --- Inheritance ---

    @Test
    fun allVariantsAreThrowable() {
        val errors = listOf(
            SwapError.Denied(listOf("reason")),
            SwapError.Signature(),
            SwapError.Unrecognized(),
            SwapError.InvalidSwap(listOf("reason")),
            SwapError.Other(RuntimeException("test")),
        )
        errors.forEach { assertTrue(it is Throwable) }
    }

    @Test
    fun otherWrapsCause() {
        val cause = RuntimeException("swap failed")
        val error = SwapError.Other(cause)
        assertEquals(cause, error.cause)
        assertEquals("swap failed", error.message)
    }
}
