package com.getcode.opencode.client

import com.getcode.opencode.transport.encodeCanonical
import com.codeinc.opencode.wire.transaction.v1.ErrorDetails
import com.codeinc.opencode.wire.transaction.v1.SubmitIntentResponse
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.ServerParameter

/**
 * The messages of the `SubmitIntent` bidi stream, encoded and decoded without a network call.
 *
 * The flow: send [submitActions]; if the server answers with [SubmitIntentResult.ServerParameters],
 * apply them to the intent (`IntentType.apply`) and send [submitSignatures]; then expect
 * [SubmitIntentResult.Success] or [SubmitIntentResult.Error]. No client in this module opens the
 * stream; that, and the go-ahead to move funds, belong to the caller.
 */
object SubmitIntentCodec {
    const val METHOD = "/ocp.transaction.v1.Transaction/SubmitIntent"

    /** `SubmitIntentRequest.submit_actions`, signed by [owner] over the request without its signature. */
    fun submitActions(intent: IntentType, owner: Ed25519.KeyPair): ByteArray = intent.submitActions(owner).encodeCanonical()

    /** `SubmitIntentRequest.submit_signatures`, one per action, in action order. */
    fun submitSignatures(intent: IntentType): ByteArray = intent.submitSignatures().encodeCanonical()

    fun decodeResponse(bytes: ByteArray): SubmitIntentResult {
        val response = SubmitIntentResponse.ADAPTER.decode(bytes)
        response.server_parameters?.let { params ->
            return SubmitIntentResult.ServerParameters(params.server_parameters.map { it.toDomain() })
        }
        if (response.success != null) return SubmitIntentResult.Success
        response.error?.let { return it.toDomain() }
        return SubmitIntentResult.Empty
    }
}

sealed interface SubmitIntentResult {
    data class ServerParameters(val parameters: List<ServerParameter>) : SubmitIntentResult
    data object Success : SubmitIntentResult

    /**
     * The server rejected the intent. [details] are the reasons Android's `SubmitIntentError.typed`
     * keeps for [code]: denial reasons, reason strings, and `action=<id>` for bad signatures.
     */
    data class Error(val code: Code, val details: List<String>) : SubmitIntentResult {
        enum class Code { Denied, InvalidIntent, Signature, StaleState }
    }

    /** A response with no case set. */
    data object Empty : SubmitIntentResult
}

private fun SubmitIntentResponse.Error.toDomain(): SubmitIntentResult.Error {
    val reasonStrings = error_details.mapNotNull { it.reason_string?.reason?.takeIf(String::isNotEmpty) }
    return when (code) {
        SubmitIntentResponse.Error.Code.DENIED ->
            SubmitIntentResult.Error(SubmitIntentResult.Error.Code.Denied, error_details.mapNotNull { it.denied?.reason })
        SubmitIntentResponse.Error.Code.INVALID_INTENT ->
            SubmitIntentResult.Error(SubmitIntentResult.Error.Code.InvalidIntent, reasonStrings)
        SubmitIntentResponse.Error.Code.SIGNATURE_ERROR ->
            SubmitIntentResult.Error(SubmitIntentResult.Error.Code.Signature, error_details.mapNotNull(ErrorDetails::signatureDetail))
        SubmitIntentResponse.Error.Code.STALE_STATE ->
            SubmitIntentResult.Error(SubmitIntentResult.Error.Code.StaleState, reasonStrings)
    }
}

private fun ErrorDetails.signatureDetail(): String? =
    reason_string?.reason?.takeIf(String::isNotEmpty)
        ?: invalid_signature?.let { "action=${it.action_id}" }
