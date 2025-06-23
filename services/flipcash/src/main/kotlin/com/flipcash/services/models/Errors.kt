package com.flipcash.services.models

import com.getcode.utils.CodeServerError

sealed class LoginError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidTimestamp : LoginError("Invalid timestamp")
    class Denied : LoginError("Denied")
    class Unrecognized : LoginError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : LoginError(message = cause?.message, cause = cause)
}

sealed class RegisterError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidSignature : RegisterError("Invalid signature")
    class Denied: RegisterError("Denied")
    class Unrecognized : RegisterError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : RegisterError(message = cause?.message, cause = cause)
}

sealed class GetUserFlagsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : GetUserFlagsError("Unrecognized")
    class Denied : GetUserFlagsError("Denied")
    data class Other(override val cause: Throwable? = null) : GetUserFlagsError(message = cause?.message, cause = cause)
}

sealed class PurchaseAckError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : PurchaseAckError("Unrecognized")
    class Denied : PurchaseAckError("Denied")
    class InvalidReceipt: PurchaseAckError("Invalid receipt")
    class InvalidMetadata: PurchaseAckError("Invalid metadata")
    data class Other(override val cause: Throwable? = null) : PurchaseAckError(message = cause?.message, cause = cause)
}

sealed class AddTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidPushToken : AddTokenError("Invalid push token")
    class Unrecognized : AddTokenError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : AddTokenError(message = cause?.message, cause = cause)
}

sealed class DeleteTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : DeleteTokenError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : DeleteTokenError(message = cause?.message, cause = cause)
}

sealed class GetActivityFeedMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : GetActivityFeedMessagesError("Denied")
    class Unrecognized : GetActivityFeedMessagesError("Unrecognized")
    class NotFound: GetActivityFeedMessagesError("Not found")
    data class Other(override val cause: Throwable? = null) : GetActivityFeedMessagesError(message = cause?.message, cause = cause)
}

sealed class CreatePoolError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class RendezvousExists: CreatePoolError("Rendezvous exists")
    class FundingDestinationExists: CreatePoolError("Funding destination exists")
    class Unrecognized : CreatePoolError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : CreatePoolError(message = cause?.message, cause = cause)
}

sealed class GetPoolError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: GetPoolError("Not found")
    class Unrecognized : GetPoolError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetPoolError(message = cause?.message, cause = cause)
}

sealed class GetPoolPageError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: GetPoolPageError("Not found")
    data class Other(override val cause: Throwable? = null) : GetPoolPageError(message = cause?.message, cause = cause)
}

sealed class PlacePoolBetError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class PoolNotFound: PlacePoolBetError("Pool not found")
    class PoolClosed: PlacePoolBetError("Pool closed")
    class BetAlreadyMade: PlacePoolBetError("Bet already made")
    class MaxBetsReceived: PlacePoolBetError("Max bets received")
    class Unrecognized : PlacePoolBetError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : PlacePoolBetError(message = cause?.message, cause = cause)
}

sealed class ResolvePoolOutcomeError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: ResolvePoolOutcomeError("Not found")
    class Denied: ResolvePoolOutcomeError("Denied")
    class PoolOpen: ResolvePoolOutcomeError("Pool still open")
    class AlreadyDeclared: ResolvePoolOutcomeError("Different outcome already declared")
    class Unrecognized : ResolvePoolOutcomeError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : ResolvePoolOutcomeError(message = cause?.message, cause = cause)
}

sealed class ClosePoolError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: ClosePoolError("Not found")
    class Denied: ClosePoolError("Denied")
    class Unrecognized : ClosePoolError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : ClosePoolError(message = cause?.message, cause = cause)
}

