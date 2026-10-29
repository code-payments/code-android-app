package com.flipcash.services.models

import com.flipcash.services.models.chat.BlobRejection
import com.flipcash.services.models.chat.InviteKeyBundle
import com.getcode.solana.keys.Checksum
import com.getcode.utils.CodeServerError
import com.getcode.utils.NotifiableError
import com.getcode.utils.UnreportedError
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import java.io.IOException

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
    data class Other(override val cause: Throwable? = null) : AddTokenError(message = cause?.message, cause = cause), NotifiableError {
        /** The call never got an answer from the server: no network, or the channel gave up. */
        val isTransportFailure: Boolean
            get() = cause.hasTransportCause()
    }
}

private fun Throwable?.hasTransportCause(codes: Set<Status.Code> = transportCodes): Boolean =
    generateSequence(this) { it.cause }.take(8).any { it.isTransportCause(codes) }

private fun Throwable.isTransportCause(codes: Set<Status.Code>): Boolean = when (this) {
    is IOException -> true
    is StatusException -> status.code in codes
    is StatusRuntimeException -> status.code in codes
    else -> false
}

private val transportCodes = setOf(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED)

// A test push the call dropped never reached anyone, so it reads as unreachable. Registration
// keeps CANCELLED out of transport. Coroutine cancellation never gets here: foldWithSuppression
// rethrows it before the error is built.
private val testPushTransportCodes = transportCodes + Status.Code.CANCELLED

sealed class DeleteTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : DeleteTokenError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : DeleteTokenError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SendTestPushError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : SendTestPushError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SendTestPushError(message = cause?.message, cause = cause), NotifiableError {
        /** The call never got an answer from the server: no network, the channel gave up, or the call was cancelled. */
        val isTransportFailure: Boolean
            get() = cause.hasTransportCause(testPushTransportCodes)
    }
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

sealed class SetUsernameError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class InvalidUsername: SetUsernameError("Invalid username")
    class Denied: SetUsernameError("Denied")
    // Another user already holds this username.
    class AlreadyTaken: SetUsernameError("Username already taken")
    class FailedModerated(val category: ModerationResult.FlaggedCategory) : SetUsernameError("Content flagged: $category")
    // Claiming this username costs more than the caller can pay.
    class InsufficientBalance: SetUsernameError("Insufficient balance")
    // The username is on the server's reserved list and cannot be claimed.
    class ReservedWord: SetUsernameError("Reserved word")
    class Unrecognized : SetUsernameError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetUsernameError(message = cause?.message, cause = cause), NotifiableError
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

sealed class SetCoverPictureError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : SetCoverPictureError("Denied")
    // No such blob, or it is not owned by the caller.
    class BlobNotFound : SetCoverPictureError("Blob not found")
    // Blob is still PENDING/PROCESSING; retry once READY.
    class BlobNotReady : SetCoverPictureError("Blob not ready")
    // Blob failed validation or moderation; terminal for this id, must upload again.
    class BlobRejected : SetCoverPictureError("Blob rejected")
    // Blob is READY but unusable as a picture (e.g. not an image).
    class InvalidBlob : SetCoverPictureError("Invalid blob")
    class Unrecognized : SetCoverPictureError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetCoverPictureError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SetBioError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class InvalidBio : SetBioError("Invalid bio")
    class Denied : SetBioError("Denied")
    class FailedModerated(val category: ModerationResult.FlaggedCategory) : SetBioError("Content flagged: $category")
    class Unrecognized : SetBioError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetBioError(message = cause?.message, cause = cause), NotifiableError
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

    /**
     * The server has no such chat. Every caller handles it — a DM opened before anyone has written
     * in it, a deleted group behind a link card — so it is not reported.
     */
    class NotFound : GetChatError("Not found"), UnreportedError
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

sealed class GetGroupChatFeedError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetGroupChatFeedError("Denied")
    class NotFound : GetGroupChatFeedError("Not found")
    class Unrecognized : GetGroupChatFeedError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetGroupChatFeedError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetRosterError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetRosterError("Denied")
    class NotFound : GetRosterError("Not found")
    class Unrecognized : GetRosterError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetRosterError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SampleChattersError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    // Not a public group (private group or DM).
    class Denied : SampleChattersError("Denied")
    class NotFound : SampleChattersError("Not found")
    class Unrecognized : SampleChattersError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SampleChattersError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SetFeaturedGroupsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    // A group in the request is private; nothing was written.
    class Denied : SetFeaturedGroupsError("Denied")
    // A group in the request does not exist; nothing was written.
    class NotFound : SetFeaturedGroupsError("Not found")
    class Unrecognized : SetFeaturedGroupsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetFeaturedGroupsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetFeaturedGroupsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    // No user holds the username.
    class NotFound : GetFeaturedGroupsError("Not found")
    class Unrecognized : GetFeaturedGroupsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetFeaturedGroupsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetMentionSuggestionsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetMentionSuggestionsError("Denied")
    class NotFound : GetMentionSuggestionsError("Not found")
    class Unrecognized : GetMentionSuggestionsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetMentionSuggestionsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class JoinChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : JoinChatError("Denied")
    class NotFound : JoinChatError("Not found")
    // The caller does not meet the chat's listener rules — a minimum balance, or staff
    // membership. Recoverable by the user, so it is deliberately not a NotifiableError.
    class RulesNotSatisfied : JoinChatError("Rules not satisfied")
    // The creator or a moderator banned the caller from this chat. Not recoverable by the user,
    // so deliberately not a NotifiableError.
    class Banned : JoinChatError("Banned")
    // The invite token matches no invite for this chat. Not recoverable by the user (the link is
    // bad), so deliberately not a NotifiableError; likewise for the three below.
    class InviteNotFound : JoinChatError("Invite not found")
    class InviteExhausted : JoinChatError("Invite exhausted")
    class InviteExpired : JoinChatError("Invite expired")
    class InviteRevoked : JoinChatError("Invite revoked")
    class Unrecognized : JoinChatError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : JoinChatError(message = cause?.message, cause = cause), NotifiableError
}

sealed class LeaveChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : LeaveChatError("Denied")
    // Recovered to success by InternalChatRepository.leaveChat: "the chat is not there"
    // is the state LeaveChat was asked to produce. Kept as a distinct arm so the
    // repository can tell it apart from Denied.
    class NotFound : LeaveChatError("Not found")
    class Unrecognized : LeaveChatError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : LeaveChatError(message = cause?.message, cause = cause), NotifiableError
}

sealed class StartChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : StartChatError("Denied")
    class TitleModerated(val category: ModerationResult.FlaggedCategory) : StartChatError("Title flagged: $category")
    class DescriptionModerated(val category: ModerationResult.FlaggedCategory) : StartChatError("Description flagged: $category")
    class PictureBlobNotAccepted : StartChatError("Picture blob not accepted")
    class CoverPictureBlobNotAccepted : StartChatError("Cover picture blob not accepted")
    class InvalidRules : StartChatError("Invalid rules")
    // The caller does not meet the rules they are setting on the chat being created (e.g. a
    // minimum balance requirement). Recoverable by the user, so deliberately not a
    // NotifiableError, matching JoinChatError.RulesNotSatisfied.
    class RulesNotSatisfied : StartChatError("Rules not satisfied")
    class Unrecognized : StartChatError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : StartChatError(message = cause?.message, cause = cause), NotifiableError
}

sealed class EditChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : EditChatError("Denied")
    class NotFound : EditChatError("Not found")
    class TitleModerated(val category: ModerationResult.FlaggedCategory) : EditChatError("Title flagged: $category")
    class DescriptionModerated(val category: ModerationResult.FlaggedCategory) : EditChatError("Description flagged: $category")
    class PictureBlobNotAccepted : EditChatError("Picture blob not accepted")
    class CoverPictureBlobNotAccepted : EditChatError("Cover picture blob not accepted")
    // The replacement rules are malformed, or the chat is not a public group with a listener
    // minimum balance. A client bug rather than something the user can fix, so notifiable.
    class InvalidRules : EditChatError("Invalid rules"), NotifiableError
    // The caller does not meet the rules they are setting. Recoverable by the user, so
    // deliberately not a NotifiableError, matching StartChatError.RulesNotSatisfied.
    class RulesNotSatisfied : EditChatError("Rules not satisfied")
    class Unrecognized : EditChatError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : EditChatError(message = cause?.message, cause = cause), NotifiableError
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

    /**
     * The chat has no messages to return. Opening a conversation nobody has written in yet is a
     * normal outcome, not a fault, so callers render an empty transcript and it is not reported.
     */
    class NotFound : GetMessagesError("Not found"), UnreportedError
    class Unrecognized : GetMessagesError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetMessagesError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SendMessageError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    // The server refused this user in this chat. The row settles as DENIED; nothing to report.
    class Denied : SendMessageError("Denied"), UnreportedError
    // Sender sent EncryptedContent to a chat that doesn't allow it (non-DM). E2eePolicy only
    // encrypts in a DM, so this means the chat's type changed under us; the send fails and can
    // be retried.
    class EncryptionNotAllowed : SendMessageError("Encryption not allowed")
    // Sender sent plaintext to a chat that requires encryption (a private group). Not retryable
    // as-is: the content has to be sealed with the chat key first, which this client cannot do yet.
    class EncryptionRequired : SendMessageError("Encryption required")
    class Unrecognized : SendMessageError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SendMessageError(message = cause?.message, cause = cause), NotifiableError
}

sealed class AdvancePointerError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    // A read pointer the server won't take is dropped; callers ignore the result.
    class Denied : AdvancePointerError("Denied"), UnreportedError
    class MessageNotFound : AdvancePointerError("Message not found")
    class Unrecognized : AdvancePointerError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : AdvancePointerError(message = cause?.message, cause = cause), NotifiableError
}

sealed class NotifyIsTypingError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    // A typing notice the server won't take is dropped; callers ignore the result.
    class Denied : NotifyIsTypingError("Denied"), UnreportedError
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

sealed class GetCommunityError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound : GetCommunityError("Not found")
    class Unrecognized : GetCommunityError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetCommunityError(message = cause?.message, cause = cause), NotifiableError
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
    // Editor sent EncryptedContent to a chat that doesn't allow it (non-DM). See
    // SendMessageError.EncryptionNotAllowed.
    class EncryptionNotAllowed : EditMessageError("Encryption not allowed")
    // Editor sent plaintext to a chat that requires encryption. See SendMessageError.EncryptionRequired.
    class EncryptionRequired : EditMessageError("Encryption required")
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
    // Boosted reactions are permanent.
    class CannotRemoveBoosted : RemoveReactionError("Cannot remove boosted reaction")
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

sealed class UpdateTipCardError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: UpdateTipCardError("Denied")
    class InvalidColor: UpdateTipCardError("Invalid color")
    class Unrecognized : UpdateTipCardError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : UpdateTipCardError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SetMinDmChatInitFeeError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: SetMinDmChatInitFeeError("Denied")
    class InvalidAmount: SetMinDmChatInitFeeError("Invalid amount")
    class Unrecognized : SetMinDmChatInitFeeError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetMinDmChatInitFeeError(message = cause?.message, cause = cause), NotifiableError
}

sealed class MuteChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: MuteChatError("Caller is not a member")
    class NotFound: MuteChatError("Not found")
    class Unrecognized : MuteChatError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : MuteChatError(message = cause?.message, cause = cause), NotifiableError
}

sealed class UnmuteChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: UnmuteChatError("Caller is not a member")
    class NotFound: UnmuteChatError("Not found")
    class Unrecognized : UnmuteChatError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : UnmuteChatError(message = cause?.message, cause = cause), NotifiableError
}

sealed class EnterLobbyError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    // Not a private group, or it has no key yet.
    class Denied : EnterLobbyError("Denied")
    class NotFound : EnterLobbyError("Not found")
    class AlreadyMember : EnterLobbyError("Already a member")
    class LobbyFull : EnterLobbyError("Lobby full")
    // The caller is already waiting in as many lobbies as the server allows.
    class TooManyLobbies : EnterLobbyError("Too many lobbies")
    // The caller is banned from this chat, so they cannot ask to join it.
    class Banned : EnterLobbyError("Banned")
    class Unrecognized : EnterLobbyError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : EnterLobbyError(message = cause?.message, cause = cause), NotifiableError
}

sealed class LeaveLobbyError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : LeaveLobbyError("Denied")
    class NotFound : LeaveLobbyError("Not found")
    class Unrecognized : LeaveLobbyError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : LeaveLobbyError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetLobbyMembersError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    // Caller is not the chat's creator, or the chat is not a private group.
    class Denied : GetLobbyMembersError("Denied")
    class NotFound : GetLobbyMembersError("Not found")
    class Unrecognized : GetLobbyMembersError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetLobbyMembersError(message = cause?.message, cause = cause), NotifiableError
}

sealed class AdmitLobbyMemberError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : AdmitLobbyMemberError("Denied")
    class NotFound : AdmitLobbyMemberError("Not found")
    // The user is neither waiting in the lobby nor a member.
    class NotInLobby : AdmitLobbyMemberError("Not in lobby")
    class Unrecognized : AdmitLobbyMemberError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : AdmitLobbyMemberError(message = cause?.message, cause = cause), NotifiableError
}

sealed class DenyLobbyMemberError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : DenyLobbyMemberError("Denied")
    class NotFound : DenyLobbyMemberError("Not found")
    class Unrecognized : DenyLobbyMemberError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : DenyLobbyMemberError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SetKeyEnvelopeError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : SetKeyEnvelopeError("Denied")
    class NotFound : SetKeyEnvelopeError("Not found")
    // The caller already stored a different envelope, which stands.
    class AlreadySet : SetKeyEnvelopeError("Already set")
    class Unrecognized : SetKeyEnvelopeError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SetKeyEnvelopeError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetKeyEnvelopeError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetKeyEnvelopeError("Denied")
    class NotFound : GetKeyEnvelopeError("Not found")
    // The caller is a member with no envelope stored.
    class NoEnvelope : GetKeyEnvelopeError("No envelope")
    // The caller joined with an invite and has not yet stored an envelope of their own; the server
    // holds only the invite's copy of the chat key. Not a usable own envelope, and never to be read
    // as one. Carries the bundle so the invite flow can open it once the invite crypto exists.
    class InviteKeyBundleOnly(val bundle: InviteKeyBundle) : GetKeyEnvelopeError("Only an invite's key copy is stored")
    class Unrecognized : GetKeyEnvelopeError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetKeyEnvelopeError(message = cause?.message, cause = cause), NotifiableError
}

sealed class ReportError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied: ReportError("Denied")
    class NotFound: ReportError("Not found")
    class Unrecognized : ReportError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : ReportError(message = cause?.message, cause = cause), NotifiableError
}

// Thrown when a reserved blob failed server-side finalization (moderation / decode / size).
// Terminal: the client must reserve a fresh upload to retry.
class BlobRejectedException(val rejection: BlobRejection) :
    Exception("Blob rejected: ${rejection.reason}")

// Thrown when a blob did not reach READY within the client's polling window.
class BlobNotReadyException : Exception("Blob did not become ready in time")

sealed class CreatePollError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : CreatePollError("Denied")
    class ChatNotSupported : CreatePollError("Chat does not support polls")
    class EncryptionNotAllowed : CreatePollError("Encryption not allowed")
    class EncryptionRequired : CreatePollError("Encryption required")
    class OptionCountMismatch : CreatePollError("Option count mismatch")
    class InvalidEndsAt : CreatePollError("Invalid end time")
    class InvalidBalanceParameters : CreatePollError("Invalid balance parameters")
    class TextModerated(val category: ModerationResult.FlaggedCategory) : CreatePollError("Poll text flagged: $category")
    class Unrecognized : CreatePollError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : CreatePollError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetPollError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetPollError("Denied")
    class NotFound : GetPollError("Not found")
    class Unrecognized : GetPollError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetPollError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetPollsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetPollsError("Denied")
    class Unrecognized : GetPollsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetPollsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class VoteError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : VoteError("Denied")
    class NotFound : VoteError("Not found")
    class PollClosed : VoteError("Poll closed")
    class InvalidSelection : VoteError("Invalid selection")
    class Unrecognized : VoteError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : VoteError(message = cause?.message, cause = cause), NotifiableError
}

sealed class SilenceUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : SilenceUserError("Denied")
    class NotFound : SilenceUserError("Not found")
    // The target user is the chat's creator.
    class CannotRestrictCreator : SilenceUserError("Cannot restrict the chat creator")
    class Unrecognized : SilenceUserError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : SilenceUserError(message = cause?.message, cause = cause), NotifiableError
}

sealed class UnsilenceUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : UnsilenceUserError("Denied")
    class NotFound : UnsilenceUserError("Not found")
    class Unrecognized : UnsilenceUserError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : UnsilenceUserError(message = cause?.message, cause = cause), NotifiableError
}

sealed class BanUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : BanUserError("Denied")
    class NotFound : BanUserError("Not found")
    // The target user is the chat's creator.
    class CannotRestrictCreator : BanUserError("Cannot restrict the chat creator")
    class Unrecognized : BanUserError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : BanUserError(message = cause?.message, cause = cause), NotifiableError
}

sealed class UnbanUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : UnbanUserError("Denied")
    class NotFound : UnbanUserError("Not found")
    class Unrecognized : UnbanUserError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : UnbanUserError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetRestrictedUsersError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetRestrictedUsersError("Denied")
    class NotFound : GetRestrictedUsersError("Not found")
    class Unrecognized : GetRestrictedUsersError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetRestrictedUsersError(message = cause?.message, cause = cause), NotifiableError
}

sealed class CreatePaymentRequestError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : CreatePaymentRequestError("Denied")
    class ChatNotSupported : CreatePaymentRequestError("Chat does not support payment requests")
    class EncryptionNotAllowed : CreatePaymentRequestError("Encryption not allowed")
    class EncryptionRequired : CreatePaymentRequestError("Encryption required")
    class InvalidEndsAt : CreatePaymentRequestError("Invalid end time")
    class InvalidShares : CreatePaymentRequestError("Invalid shares")
    class InvalidCurrency : CreatePaymentRequestError("Invalid currency")
    class TextModerated(val category: ModerationResult.FlaggedCategory) : CreatePaymentRequestError("Payment request text flagged: $category")
    class Unrecognized : CreatePaymentRequestError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : CreatePaymentRequestError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetPaymentRequestError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetPaymentRequestError("Denied")
    class NotFound : GetPaymentRequestError("Not found")
    class Unrecognized : GetPaymentRequestError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetPaymentRequestError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetChatPaymentRequestsError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetChatPaymentRequestsError("Denied")
    class NotFound : GetChatPaymentRequestsError("Not found")
    class Unrecognized : GetChatPaymentRequestsError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetChatPaymentRequestsError(message = cause?.message, cause = cause), NotifiableError
}

sealed class ClosePaymentRequestError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : ClosePaymentRequestError("Denied")
    class NotFound : ClosePaymentRequestError("Not found")
    class Unrecognized : ClosePaymentRequestError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : ClosePaymentRequestError(message = cause?.message, cause = cause), NotifiableError
}

sealed class MarkPaymentRequestPaidError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : MarkPaymentRequestPaidError("Denied")
    class NotFound : MarkPaymentRequestPaidError("Not found")
    // The request is no longer open.
    class NotOpen : MarkPaymentRequestPaidError("Not open")
    // The user does not owe a share of this request.
    class NotAShare : MarkPaymentRequestPaidError("Not a share")
    class Unrecognized : MarkPaymentRequestPaidError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : MarkPaymentRequestPaidError(message = cause?.message, cause = cause), NotifiableError
}

sealed class CreateInviteError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : CreateInviteError("Denied")
    class NotFound : CreateInviteError("Not found")
    class InvalidExpiresAt : CreateInviteError("Invalid expiry")
    class TooManyInvites : CreateInviteError("Too many invites")
    // The key envelope's signature did not verify against the caller's key.
    class InvalidSignature : CreateInviteError("Invalid signature"), NotifiableError
    class Unrecognized : CreateInviteError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : CreateInviteError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetInviteError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class NotFound : GetInviteError("Not found")
    // The caller is banned from the chat the invite leads to.
    class Banned : GetInviteError("Banned")
    class Unrecognized : GetInviteError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetInviteError(message = cause?.message, cause = cause), NotifiableError
}

sealed class GetChatInvitesError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : GetChatInvitesError("Denied")
    class NotFound : GetChatInvitesError("Not found")
    class Unrecognized : GetChatInvitesError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : GetChatInvitesError(message = cause?.message, cause = cause), NotifiableError
}

sealed class RevokeInviteError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    class Denied : RevokeInviteError("Denied")
    class NotFound : RevokeInviteError("Not found")
    class Unrecognized : RevokeInviteError("Unrecognized"), NotifiableError
    data class Other(override val cause: Throwable? = null) : RevokeInviteError(message = cause?.message, cause = cause), NotifiableError
}
