package com.flipcash.services.models

import com.flipcash.services.models.chat.BlobRejection
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
    class FailedModerated(val category: ModerationResult.FlaggedCategory) : SetDisplayNameError("Content flagged: $category")
    class Unrecognized : SetDisplayNameError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetDisplayNameError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SetProfilePictureError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: SetProfilePictureError("Denied")
    // No such blob, or it is not owned by the caller.
    class BlobNotFound: SetProfilePictureError("Blob not found")
    // Blob is still PENDING/PROCESSING; retry once READY.
    class BlobNotReady: SetProfilePictureError("Blob not ready")
    // Blob failed validation or moderation; terminal for this id, must upload again.
    class BlobRejected: SetProfilePictureError("Blob rejected")
    // Blob is READY but unusable as a picture (e.g. not an image).
    class InvalidBlob: SetProfilePictureError("Invalid blob")
    class Unrecognized : SetProfilePictureError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetProfilePictureError(message = cause?.message, cause = cause), NotifiableError
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
    class Flagged(val category: ModerationResult.FlaggedCategory) : TextModerationError("Content flagged: $category")
    class Denied : TextModerationError("Denied")
    class UnsupportedLanguage: TextModerationError("Unsupported Language")
    class Unrecognized : TextModerationError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : TextModerationError(message = cause?.message, cause = cause), NotifiableError
}

sealed class ImageModerationError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Flagged(val category: ModerationResult.FlaggedCategory) : TextModerationError("Content flagged: $category")

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

sealed class GetChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetChatError("Denied")
    class NotFound : GetChatError("Not found")
    class Unrecognized : GetChatError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetChatError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetDmChatFeedError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetDmChatFeedError("Denied")
    class NotFound : GetDmChatFeedError("Not found")
    class Unrecognized : GetDmChatFeedError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetDmChatFeedError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetMessageError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetMessageError("Denied")
    class NotFound : GetMessageError("Not found")
    class Unrecognized : GetMessageError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetMessageError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetMessagesError("Denied")
    class NotFound : GetMessagesError("Not found")
    class Unrecognized : GetMessagesError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetMessagesError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SendMessageError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : SendMessageError("Denied")
    class Unrecognized : SendMessageError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SendMessageError(message = cause?.message, cause = cause), NotifiableError
}

sealed class AdvancePointerError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : AdvancePointerError("Denied")
    class MessageNotFound : AdvancePointerError("Message not found")
    class Unrecognized : AdvancePointerError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : AdvancePointerError(message = cause?.message, cause = cause), NotifiableError
}

sealed class NotifyIsTypingError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : NotifyIsTypingError("Denied")
    class Unrecognized : NotifyIsTypingError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : NotifyIsTypingError(message = cause?.message, cause = cause), NotifiableError
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

sealed class GetDeltaError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetDeltaError("Denied")
    class ResetRequired : GetDeltaError("Reset required")
    class Unrecognized : GetDeltaError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetDeltaError(message = cause?.message, cause = cause), NotifiableError
}

sealed class EditMessageError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : EditMessageError("Denied")
    class MessageNotFound : EditMessageError("Message not found")
    class CannotEdit : EditMessageError("Cannot edit")
    class Conflict : EditMessageError("Conflict")
    class Unrecognized : EditMessageError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : EditMessageError(message = cause?.message, cause = cause), NotifiableError
}

sealed class DeleteMessageError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : DeleteMessageError("Denied")
    class MessageNotFound : DeleteMessageError("Message not found")
    class CannotDelete : DeleteMessageError("Cannot delete")
    class Conflict : DeleteMessageError("Conflict")
    class Unrecognized : DeleteMessageError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : DeleteMessageError(message = cause?.message, cause = cause), NotifiableError
}

sealed class AddReactionError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : AddReactionError("Denied")
    class MessageNotFound : AddReactionError("Message not found")
    class CannotReact : AddReactionError("Cannot react")
    class TooManyReactionTypes : AddReactionError("Too many reaction types")
    class Unrecognized : AddReactionError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : AddReactionError(message = cause?.message, cause = cause), NotifiableError
}

sealed class RemoveReactionError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : RemoveReactionError("Denied")
    class MessageNotFound : RemoveReactionError("Message not found")
    class Unrecognized : RemoveReactionError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : RemoveReactionError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetReactorsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetReactorsError("Denied")
    class MessageNotFound : GetReactorsError("Message not found")
    class Unrecognized : GetReactorsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetReactorsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetReactionSummaryError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetReactionSummaryError("Denied")
    class MessageNotFound : GetReactionSummaryError("Message not found")
    class Unrecognized : GetReactionSummaryError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetReactionSummaryError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetReactionSummariesError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetReactionSummariesError("Denied")
    class Unrecognized : GetReactionSummariesError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetReactionSummariesError(message = cause?.message, cause = cause), NotifiableError
}
sealed class InitiateExternalUploadError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : InitiateExternalUploadError("Denied")
    // policyVersion is echoed by the server on a policy-driven denial so the client can detect a
    // stale cached upload policy and re-fetch it.
    class UnsupportedType(val policyVersion: String? = null) : InitiateExternalUploadError("Unsupported type")
    class TooLarge(val policyVersion: String? = null) : InitiateExternalUploadError("Too large")
    class QuotaExceeded : InitiateExternalUploadError("Quota exceeded")
    class Unrecognized : InitiateExternalUploadError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : InitiateExternalUploadError(message = cause?.message, cause = cause), NotifiableError
}

sealed class CompleteExternalUploadError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class NotFound : CompleteExternalUploadError("Not found")
    class NotUploaded : CompleteExternalUploadError("Not uploaded")
    class Unrecognized : CompleteExternalUploadError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : CompleteExternalUploadError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetBlobsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : GetBlobsError("Denied")
    class Unrecognized : GetBlobsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetBlobsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetUploadPolicyError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : GetUploadPolicyError("Denied")
    class Unrecognized : GetUploadPolicyError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetUploadPolicyError(message = cause?.message, cause = cause), NotifiableError
}

sealed class BlockUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : BlockUserError("Denied")
    class UserNotFound : BlockUserError("User not found")
    class CannotBlockSelf : BlockUserError("Cannot block self")
    class Unrecognized : BlockUserError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : BlockUserError(message = cause?.message, cause = cause), NotifiableError
}

sealed class UnblockUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : UnblockUserError("Denied")
    class Unrecognized : UnblockUserError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : UnblockUserError(message = cause?.message, cause = cause), NotifiableError
}

sealed class IsBlockedError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : IsBlockedError("Denied")
    class Unrecognized : IsBlockedError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : IsBlockedError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetBlocklistError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : GetBlocklistError("Denied")
    class Unrecognized : GetBlocklistError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetBlocklistError(message = cause?.message, cause = cause), NotifiableError
}

// Thrown when a reserved blob failed server-side finalization (moderation / decode / size).
// Terminal: the client must reserve a fresh upload to retry.
class BlobRejectedException(val rejection: BlobRejection) :
    Exception("Blob rejected: ${rejection.reason}")

// Thrown when a blob did not reach READY within the client's polling window.
class BlobNotReadyException : Exception("Blob did not become ready in time")
