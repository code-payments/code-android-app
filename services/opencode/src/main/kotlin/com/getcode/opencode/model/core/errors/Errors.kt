package com.getcode.opencode.model.core.errors

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentResponse
import com.getcode.utils.CodeServerError

sealed class CodeAccountCheckError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : CodeAccountCheckError()
    class UnlockedTimelockAccount : CodeAccountCheckError()
    class Unrecognized : CodeAccountCheckError()
    data class Other(override val cause: Throwable? = null) : CodeAccountCheckError()
}

sealed class GetAccountsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : GetAccountsError()
    class Unrecognized : GetAccountsError()
    data class Other(override val cause: Throwable? = null) : GetAccountsError()
}

sealed class LinkAccountsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    /**
     * The action has been denied (eg. owner account not phone verified)
     */
    class Denied : LinkAccountsError()

    /**
     * An account being linked is not valid
     */
    class InvalidAccount : LinkAccountsError()
    class Unrecognized : LinkAccountsError()
    data class Other(override val cause: Throwable? = null) : LinkAccountsError()
}

sealed class GetRatesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    /**
     * No currency data is available for the requested timestamp.
     */
    class MissingData : GetRatesError()
    class Unrecognized : GetRatesError()
    data class Other(override val cause: Throwable? = null) : GetRatesError()
}

sealed class OpenMessageStreamError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : OpenMessageStreamError()
}

sealed class PollMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : PollMessagesError()
}

sealed class AckMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : AckMessagesError()
    data class Other(override val cause: Throwable? = null) : AckMessagesError()
}

sealed class SendMessageError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NoActiveStream : SendMessageError()
    class Unrecognized : SendMessageError()
    data class Other(override val cause: Throwable? = null) : SendMessageError()
}

enum class DeniedReason {
    Unspecified,
    TooManyFreeAccountsForPhoneNumber,
    TooManyFreeAccountsForDevice,
    UnsupportedCountry,
    UnsupportedDevice;

    companion object {
        fun fromValue(value: Int): DeniedReason {
            return entries.firstOrNull { it.ordinal == value } ?: Unspecified
        }
    }
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

    data class Denied(private val reasons: List<DeniedReason>) :
        SubmitIntentError(message = reasons.joinToString())

    class Unrecognized : SubmitIntentError()
    data class Other(override val cause: Throwable? = null) : SubmitIntentError()

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
                        DeniedReason.fromValue(it.denied.codeValue)
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
    class NotFound : GetIntentMetadataError()
    class Unrecognized : GetIntentMetadataError()
    data class Other(override val cause: Throwable? = null) : GetIntentMetadataError()
}

sealed class GetLimitsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : GetIntentMetadataError()
    data class Other(override val cause: Throwable? = null) : GetIntentMetadataError()
}

sealed class WithdrawalAvailabilityError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(override val cause: Throwable? = null) : WithdrawalAvailabilityError()
}

sealed class AirdropError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unavailable: AirdropError()
    class AlreadyClaimed: AirdropError()
    class Unrecognized: AirdropError()
    data class Other(override val cause: Throwable? = null) : AirdropError()
}