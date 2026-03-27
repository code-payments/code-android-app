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
import com.getcode.utils.NotifiableError

sealed class CodeAccountCheckError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : CodeAccountCheckError("Not found")
    class UnlockedTimelockAccount : CodeAccountCheckError("Unlocked timelock account"), NotifiableError
    class Unrecognized : CodeAccountCheckError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : CodeAccountCheckError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetAccountsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetAccountsError("Not found")
    class Unrecognized : GetAccountsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetAccountsError(message = cause?.message, cause = cause), NotifiableError
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
    class Unrecognized : LinkAccountsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : LinkAccountsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetRatesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    /**
     * No currency data is available for the requested timestamp.
     */
    class MissingData : GetRatesError("Missing data")
    class Unrecognized : GetRatesError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetRatesError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetMintsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetMintsError("Not found")
    class Unrecognized : GetMintsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetMintsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetHistoricalMintDataError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetHistoricalMintDataError("Not found")
    class Unrecognized : GetHistoricalMintDataError("Unrecognized"), NotifiableError
    class MissingData : GetHistoricalMintDataError("Missing data")
    data class Other(override val cause: Throwable? = null) : GetHistoricalMintDataError(message = cause?.message, cause = cause), NotifiableError
}

sealed class OpenMessageStreamError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : OpenMessageStreamError(message = cause?.message, cause = cause), NotifiableError
}

sealed class PollMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : PollMessagesError(message = cause?.message, cause = cause), NotifiableError
}

sealed class AckMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : AckMessagesError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : AckMessagesError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SendMessageError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NoActiveStream : SendMessageError("No active stream")
    class Unrecognized : SendMessageError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SendMessageError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SubmitIntentError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class InvalidIntent(private val reasons: List<String>) :
        SubmitIntentError(message = reasons.joinToString()), NotifiableError

    class Signature : SubmitIntentError(), NotifiableError
    data class StaleState(private val reasons: List<String>) :
        SubmitIntentError(message = reasons.joinToString()), NotifiableError

    data class Denied(private val reasons: List<String>) :
        SubmitIntentError(message = reasons.joinToString())

    class Unrecognized : SubmitIntentError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SubmitIntentError(message = cause?.message, cause = cause), NotifiableError

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
    class Denied : GetIntentMetadataError("Denied")
    class Unrecognized : GetIntentMetadataError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetIntentMetadataError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetLimitsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : GetIntentMetadataError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetIntentMetadataError(message = cause?.message, cause = cause), NotifiableError
}

sealed class WithdrawalAvailabilityError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : WithdrawalAvailabilityError(message = cause?.message, cause = cause), NotifiableError
}

sealed class VoidGiftCardError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied: VoidGiftCardError("Denied")
    class NotFound: VoidGiftCardError("Not found")
    class AlreadyClaimed: VoidGiftCardError("Already claimed")
    class Unrecognized: VoidGiftCardError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : VoidGiftCardError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetSwapError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied: GetSwapError("Denied")
    class NotFound: GetSwapError("Not found")
    class Unrecognized: GetSwapError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetSwapError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetPendingSwapsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound: GetPendingSwapsError("Not found")
    class Unrecognized: GetPendingSwapsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetPendingSwapsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SwapError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Denied(private val reasons: List<String>) : SwapError(message = reasons.joinToString())
    class Signature : SwapError(), NotifiableError
    class Unrecognized : SwapError("Unrecognized"), NotifiableError
    class InvalidSwap(reasons: List<String>): SwapError(message = reasons.joinToString()), NotifiableError

    data class Other(override val cause: Throwable? = null) : SwapError(message = cause?.message, cause = cause), NotifiableError

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

sealed class CheckTokenAvailabilityError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unavailable: CheckTokenAvailabilityError("Unavailable")
    class Unrecognized: CheckTokenAvailabilityError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : CheckTokenAvailabilityError(message = cause?.message, cause = cause), NotifiableError
}

sealed class LaunchTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied: LaunchTokenError("Denied")
    class Exists: LaunchTokenError("Token Already Exists")
    class InvalidIcon: LaunchTokenError("Invalid icon")
    class Unrecognized: LaunchTokenError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : LaunchTokenError(message = cause?.message, cause = cause), NotifiableError
}

sealed class UpdateIconError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound: UpdateIconError("Not found")
    class Denied: UpdateIconError("Denied")
    class InvalidIcon: UpdateIconError("Invalid icon")
    class Unrecognized: UpdateIconError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : UpdateIconError(message = cause?.message, cause = cause), NotifiableError

}

sealed class UpdateMetadataError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound: UpdateMetadataError("Not found")
    class Denied: UpdateMetadataError("Denied")
    class Unrecognized: UpdateMetadataError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : UpdateMetadataError(message = cause?.message, cause = cause), NotifiableError
}

sealed class DiscoverTokensError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound: DiscoverTokensError("Not found")
    class Unrecognized: DiscoverTokensError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : DiscoverTokensError(message = cause?.message, cause = cause), NotifiableError
}
