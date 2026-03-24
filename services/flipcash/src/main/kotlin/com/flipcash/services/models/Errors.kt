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
    class Denied: CreatePoolError("Denied")
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
    class BetOutcomeSolidified: PlacePoolBetError("Bet outcome solidified")
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

sealed class GetJwtError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: GetJwtError("Denied")
    class UnsupportedProvider: GetJwtError("Unsupported provider")
    class InvalidApiKey: GetJwtError("Invalid api key")
    class PhoneVerificationRequired: GetJwtError("Phone verification required")
    class EmailVerificationRequired: GetJwtError("Email verification required")
    class Unrecognized : GetJwtError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetJwtError(message = cause?.message, cause = cause)
}

sealed class EmailVerificationError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: EmailVerificationError("Denied")
    class RateLimited: EmailVerificationError("Rate limited")
    class InvalidEmailAddress: EmailVerificationError("Invalid email address")
    class InvalidVerificationCode: EmailVerificationError("Invalid verification code")
    class NoVerification: EmailVerificationError("No verification")
    class Unrecognized : EmailVerificationError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : EmailVerificationError(message = cause?.message, cause = cause)
}


sealed class PhoneVerificationError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: PhoneVerificationError("Denied")
    class RateLimited: PhoneVerificationError("Rate limited")
    class InvalidPhoneNumber: PhoneVerificationError("Invalid phone number")
    class UnsupportedPhoneType: PhoneVerificationError("Unsupported phone type")
    class InvalidVerificationCode: PhoneVerificationError("Invalid verification code")
    class NoVerification: PhoneVerificationError("No verification")
    class Unrecognized : PhoneVerificationError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : PhoneVerificationError(message = cause?.message, cause = cause)
}

sealed class GetUserProfileError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: GetUserProfileError("Not found")
    class Unrecognized : GetUserProfileError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : GetUserProfileError(message = cause?.message, cause = cause)
}

sealed class SetDisplayNameError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class InvalidDisplayName: SetDisplayNameError("Invalid display name")
    class Denied: SetDisplayNameError("Denied")
    class Unrecognized : SetDisplayNameError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : SetDisplayNameError(message = cause?.message, cause = cause)
}

sealed class LinkSocialAccountError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class InvalidLinkingToken: LinkSocialAccountError("Invalid linking token")
    class ExistingLink: LinkSocialAccountError("Existing link")
    class Denied: LinkSocialAccountError("Denied")
    class Unrecognized : LinkSocialAccountError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : LinkSocialAccountError(message = cause?.message, cause = cause)
}

sealed class UnlinkSocialAccountError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: UnlinkSocialAccountError("Denied")
    class Unrecognized : UnlinkSocialAccountError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : UnlinkSocialAccountError(message = cause?.message, cause = cause)
}

sealed class UpdateSettingsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : UpdateSettingsError("Denied")
    class Unrecognized : UpdateSettingsError("Unrecognized")
    class InvalidLocale : UpdateSettingsError("Invalid locale")
    class InvalidRegion : UpdateSettingsError("Invalid region")
    data class Other(override val cause: Throwable? = null) : UpdateSettingsError(message = cause?.message, cause = cause)

}