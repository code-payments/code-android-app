package com.getcode.opencode.model.core.errors

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.codeinc.opencode.gen.transaction.v1.TransactionService.SubmitIntentResponse
import com.getcode.opencode.model.core.errors.SubmitIntentError.Denied
import com.getcode.opencode.model.core.errors.SubmitIntentError.InvalidIntent
import com.getcode.opencode.model.core.errors.SubmitIntentError.Other
import com.getcode.opencode.model.core.errors.SubmitIntentError.Signature
import com.getcode.opencode.model.core.errors.SubmitIntentError.StaleState
import com.getcode.opencode.model.core.errors.SubmitIntentError.Unrecognized
import com.getcode.utils.CodeServerError

sealed class CodeAccountCheckError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : CodeAccountCheckError("Not found")
    class UnlockedTimelockAccount : CodeAccountCheckError("Unlocked timelock account")
    class Unrecognized : CodeAccountCheckError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : CodeAccountCheckError(message = cause?.message, cause = cause)
}

sealed class GetAccountsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetAccountsError("Not found")
    class Unrecognized : GetAccountsError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetAccountsError(message = cause?.message, cause = cause)
}

sealed class LinkAccountsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    /**
     * The action has been denied (eg. owner account not phone verified)
     */
    class Denied : LinkAccountsError("Denied")

    /**
     * An account being linked is not valid
     */
    class InvalidAccount : LinkAccountsError("Invalid account")
    class Unrecognized : LinkAccountsError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : LinkAccountsError(message = cause?.message, cause = cause)
}

sealed class GetRatesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    /**
     * No currency data is available for the requested timestamp.
     */
    class MissingData : GetRatesError("Missing data")
    class Unrecognized : GetRatesError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetRatesError(message = cause?.message, cause = cause)
}

sealed class GetMintsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetMintsError("Not found")
    class Unrecognized : GetMintsError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetMintsError(message = cause?.message, cause = cause)
}

sealed class GetHistoricalMintDataError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetHistoricalMintDataError("Not found")
    class Unrecognized : GetHistoricalMintDataError("Unrecognized")
    class MissingData : GetHistoricalMintDataError("Missing data")
    data class Other(override val cause: Throwable? = null) : GetHistoricalMintDataError(message = cause?.message, cause = cause)
}

sealed class OpenMessageStreamError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : OpenMessageStreamError(message = cause?.message, cause = cause)
}

sealed class PollMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : PollMessagesError(message = cause?.message, cause = cause)
}

sealed class AckMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : AckMessagesError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : AckMessagesError(message = cause?.message, cause = cause)
}

sealed class SendMessageError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NoActiveStream : SendMessageError("No active stream")
    class Unrecognized : SendMessageError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : SendMessageError(message = cause?.message, cause = cause)
}

sealed class SubmitIntentError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class InvalidIntent(private val reasons: List<String>) :
        SubmitIntentError(message = reasons.joinToString())

    class Signature : SubmitIntentError()
    data class StaleState(private val reasons: List<String>) :
        SubmitIntentError(message = reasons.joinToString())

    data class Denied(private val reasons: List<String>) :
        SubmitIntentError(message = reasons.joinToString())

    class Unrecognized : SubmitIntentError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : SubmitIntentError(message = cause?.message, cause = cause)

    companion object {
        fun typed(proto: SubmitIntentResponse.Error): SubmitIntentError {
            val reasonStrings = proto.errorDetailsList.mapNotNull {
                when (it.typeCase) {
                    TransactionService.ErrorDetails.TypeCase.REASON_STRING ->
                        it.reasonString.reason.takeIf { reason -> reason.isNotEmpty() }

                    else -> null
                }
            }
            return when (proto.code) {
                SubmitIntentResponse.Error.Code.DENIED -> {
                    val reasons = proto.errorDetailsList.mapNotNull {
                        if (!it.hasDenied()) return@mapNotNull null
                        it.denied.reason
                    }

                    Denied(reasons)
                }

                SubmitIntentResponse.Error.Code.INVALID_INTENT -> InvalidIntent(reasonStrings)
                SubmitIntentResponse.Error.Code.SIGNATURE_ERROR -> Signature()
                SubmitIntentResponse.Error.Code.STALE_STATE -> StaleState(reasonStrings)
                SubmitIntentResponse.Error.Code.UNRECOGNIZED -> Unrecognized()
                else -> return Other()
            }
        }
    }
}

sealed class GetIntentMetadataError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetIntentMetadataError("Not found")
    class Unrecognized : GetIntentMetadataError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetIntentMetadataError(message = cause?.message, cause = cause)
}

sealed class GetLimitsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : GetIntentMetadataError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetIntentMetadataError(message = cause?.message, cause = cause)
}

sealed class WithdrawalAvailabilityError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : WithdrawalAvailabilityError(message = cause?.message, cause = cause)
}

sealed class AirdropError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unavailable: AirdropError("Unavailable")
    class AlreadyClaimed: AirdropError("Already claimed")
    class Unrecognized: AirdropError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : AirdropError(message = cause?.message, cause = cause)
}

sealed class VoidGiftCardError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied: VoidGiftCardError("Denied")
    class NotFound: VoidGiftCardError("Not found")
    class AlreadyClaimed: VoidGiftCardError("Already claimed")
    class Unrecognized: VoidGiftCardError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : VoidGiftCardError(message = cause?.message, cause = cause)
}

sealed class GetSwapError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied: GetSwapError("Denied")
    class NotFound: GetSwapError("Not found")
    class Unrecognized: GetSwapError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetSwapError(message = cause?.message, cause = cause)
}

sealed class GetPendingSwapsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound: GetPendingSwapsError("Not found")
    class Unrecognized: GetPendingSwapsError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetPendingSwapsError(message = cause?.message, cause = cause)
}

sealed class SwapError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Denied(private val reasons: List<String>) : SwapError(message = reasons.joinToString())
    class Signature : SwapError()
    class Unrecognized : SwapError("Unrecognized")
    class InvalidSwap(reasons: List<String>): SwapError(message = reasons.joinToString())

    data class Other(override val cause: Throwable? = null) : SwapError(message = cause?.message, cause = cause)

    companion object {
        fun typed(proto: TransactionService.StatefulSwapResponse.Error): SwapError {
            val reasonStrings = proto.errorDetailsList.mapNotNull {
                when (it.typeCase) {
                    TransactionService.ErrorDetails.TypeCase.REASON_STRING ->
                        it.reasonString.reason.takeIf { reason -> reason.isNotEmpty() }

                    else -> null
                }
            }

            return when (proto.code) {
                TransactionService.StatefulSwapResponse.Error.Code.DENIED -> {
                    val reasons = proto.errorDetailsList.mapNotNull {
                        if (!it.hasDenied()) return@mapNotNull null
                        it.denied.reason
                    }

                    Denied(reasons)
                }

                TransactionService.StatefulSwapResponse.Error.Code.SIGNATURE_ERROR -> Signature()
                TransactionService.StatefulSwapResponse.Error.Code.UNRECOGNIZED -> Unrecognized()
                TransactionService.StatefulSwapResponse.Error.Code.INVALID_SWAP -> InvalidSwap(reasonStrings)
            }
        }
    }
}

