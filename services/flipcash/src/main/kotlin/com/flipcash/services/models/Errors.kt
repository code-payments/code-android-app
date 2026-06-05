package com.flipcash.services.models

import com.getcode.solana.keys.Checksum
import com.getcode.utils.CodeServerError
import com.getcode.utils.NotifiableError

sealed class LoginError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidTimestamp : LoginError("Invalid timestamp")
    class Denied : LoginError("Denied")
    class Unrecognized : LoginError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : LoginError(message = cause?.message, cause = cause), NotifiableError
}

sealed class RegisterError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidSignature : RegisterError("Invalid signature"), NotifiableError
    class Denied: RegisterError("Denied")
    class Unrecognized : RegisterError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : RegisterError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetUserFlagsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : GetUserFlagsError("Unrecognized"), NotifiableError
    class Denied : GetUserFlagsError("Denied")
    data class Other(override val cause: Throwable? = null) : GetUserFlagsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class PurchaseAckError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : PurchaseAckError("Unrecognized"), NotifiableError
    class Denied : PurchaseAckError("Denied")
    class InvalidReceipt: PurchaseAckError("Invalid receipt"), NotifiableError
    class InvalidMetadata: PurchaseAckError("Invalid metadata"), NotifiableError
    data class Other(override val cause: Throwable? = null) : PurchaseAckError(message = cause?.message, cause = cause), NotifiableError
}

sealed class AddTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidPushToken : AddTokenError("Invalid push token"), NotifiableError
    class Unrecognized : AddTokenError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : AddTokenError(message = cause?.message, cause = cause), NotifiableError
}

sealed class DeleteTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : DeleteTokenError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : DeleteTokenError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetActivityFeedMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : GetActivityFeedMessagesError("Denied")
    class Unrecognized : GetActivityFeedMessagesError("Unrecognized"), NotifiableError
    class NotFound: GetActivityFeedMessagesError("Not found")
    data class Other(override val cause: Throwable? = null) : GetActivityFeedMessagesError(message = cause?.message, cause = cause), NotifiableError
}

sealed class CreatePoolError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class RendezvousExists: CreatePoolError("Rendezvous exists")
    class FundingDestinationExists: CreatePoolError("Funding destination exists")
    class Denied: CreatePoolError("Denied")
    class Unrecognized : CreatePoolError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : CreatePoolError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetPoolError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: GetPoolError("Not found")
    class Unrecognized : GetPoolError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetPoolError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetPoolPageError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: GetPoolPageError("Not found")
    data class Other(override val cause: Throwable? = null) : GetPoolPageError(message = cause?.message, cause = cause), NotifiableError
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
    class Unrecognized : PlacePoolBetError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : PlacePoolBetError(message = cause?.message, cause = cause), NotifiableError
}

sealed class ResolvePoolOutcomeError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: ResolvePoolOutcomeError("Not found")
    class Denied: ResolvePoolOutcomeError("Denied")
    class PoolOpen: ResolvePoolOutcomeError("Pool still open")
    class AlreadyDeclared: ResolvePoolOutcomeError("Different outcome already declared")
    class Unrecognized : ResolvePoolOutcomeError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : ResolvePoolOutcomeError(message = cause?.message, cause = cause), NotifiableError
}

sealed class ClosePoolError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: ClosePoolError("Not found")
    class Denied: ClosePoolError("Denied")
    class Unrecognized : ClosePoolError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : ClosePoolError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetJwtError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: GetJwtError("Denied")
    class UnsupportedProvider: GetJwtError("Unsupported provider"), NotifiableError
    class InvalidApiKey: GetJwtError("Invalid api key"), NotifiableError
    class PhoneVerificationRequired: GetJwtError("Phone verification required")
    class EmailVerificationRequired: GetJwtError("Email verification required")
    class Unrecognized : GetJwtError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetJwtError(message = cause?.message, cause = cause), NotifiableError
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
    class Unrecognized : EmailVerificationError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : EmailVerificationError(message = cause?.message, cause = cause), NotifiableError
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
    class Unrecognized : PhoneVerificationError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : PhoneVerificationError(message = cause?.message, cause = cause), NotifiableError
}

sealed class LinkForPaymentError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: LinkForPaymentError("Denied")
    class NotAssociated: LinkForPaymentError("Not associated")
    class Unrecognized : LinkForPaymentError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : LinkForPaymentError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetUserProfileError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound: GetUserProfileError("Not found")
    class Unrecognized : GetUserProfileError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetUserProfileError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SetDisplayNameError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class InvalidDisplayName: SetDisplayNameError("Invalid display name")
    class Denied: SetDisplayNameError("Denied")
    class Unrecognized : SetDisplayNameError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetDisplayNameError(message = cause?.message, cause = cause), NotifiableError
}

sealed class LinkSocialAccountError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class InvalidLinkingToken: LinkSocialAccountError("Invalid linking token"), NotifiableError
    class ExistingLink: LinkSocialAccountError("Existing link")
    class Denied: LinkSocialAccountError("Denied")
    class Unrecognized : LinkSocialAccountError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : LinkSocialAccountError(message = cause?.message, cause = cause), NotifiableError
}

sealed class UnlinkSocialAccountError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: UnlinkSocialAccountError("Denied")
    class Unrecognized : UnlinkSocialAccountError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : UnlinkSocialAccountError(message = cause?.message, cause = cause), NotifiableError
}

sealed class UpdateSettingsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : UpdateSettingsError("Denied")
    class Unrecognized : UpdateSettingsError("Unrecognized"), NotifiableError
    class InvalidLocale : UpdateSettingsError("Invalid locale")
    class InvalidRegion : UpdateSettingsError("Invalid region")
    data class Other(override val cause: Throwable? = null) : UpdateSettingsError(message = cause?.message, cause = cause), NotifiableError

}

sealed class TextModerationError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Flagged(category: ModerationResult.FlaggedCategory) : TextModerationError("Content flagged: $category")
    class Denied : TextModerationError("Denied")
    class UnsupportedLanguage: TextModerationError("Unsupported Language")
    class Unrecognized : TextModerationError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : TextModerationError(message = cause?.message, cause = cause), NotifiableError
}

sealed class ImageModerationError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Flagged(category: ModerationResult.FlaggedCategory) : TextModerationError("Content flagged: $category")

    class Denied : ImageModerationError("Denied")
    class UnsupportedFormat: ImageModerationError("Unsupported Format")
    class Unrecognized : ImageModerationError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : ImageModerationError(message = cause?.message, cause = cause), NotifiableError
}

sealed class CheckSyncError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : CheckSyncError("Denied")
    class OutOfSync(val serverChecksum: Checksum) : CheckSyncError("Out of sync")
    class Unrecognized : CheckSyncError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : CheckSyncError(message = cause?.message, cause = cause), NotifiableError
}

sealed class DeltaUploadError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : DeltaUploadError("Denied")
    class ChecksumMismatch : DeltaUploadError("Checksum mismatch")
    class ChecksumDrift : DeltaUploadError("Checksum drift")
    class TooManyContacts : DeltaUploadError("Too many contacts")
    class Unrecognized : DeltaUploadError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : DeltaUploadError(message = cause?.message, cause = cause), NotifiableError
}

sealed class FullUploadError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : FullUploadError("Denied")
    class ChecksumMismatch : FullUploadError("Checksum mismatch")
    class TooManyContacts : FullUploadError("Too many contacts")
    class Unrecognized : FullUploadError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : FullUploadError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetContactsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetContactsError("Denied")
    class NotFound : GetContactsError("Not found")
    class ChecksumDrift : GetContactsError("Checksum drift")
    class Unrecognized : GetContactsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetContactsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class StreamEventsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : StreamEventsError("Denied")
    class InvalidTimestamp : StreamEventsError("Invalid timestamp")
    class Unrecognized : StreamEventsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : StreamEventsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class ResolveContactError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound : ResolveContactError("Not found")
    class Denied : ResolveContactError("Denied")
    class Unrecognized : ResolveContactError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : ResolveContactError(message = cause?.message, cause = cause), NotifiableError
}