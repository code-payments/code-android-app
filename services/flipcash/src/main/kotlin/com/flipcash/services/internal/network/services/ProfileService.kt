package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.profile.v1.Model
import com.codeinc.flipcash.gen.profile.v1.ProfileService
import com.flipcash.services.internal.network.api.ProfileApi
import com.flipcash.services.internal.network.extensions.toFlaggedCategory
import com.getcode.opencode.utils.toValidationOrElse
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.LinkSocialAccountError
import com.flipcash.services.models.ProfileIdentifier
import com.flipcash.services.models.SetDisplayNameError
import com.flipcash.services.models.SetMinDmChatInitFeeError
import com.flipcash.services.models.SetProfilePictureError
import com.flipcash.services.models.SetUsernameError
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.UnlinkSocialAccountError
import com.flipcash.services.models.UpdateTipCardError
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.internal.network.extensions.toMediaItem
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.model.financial.Fiat
import javax.inject.Inject

internal class ProfileService @Inject constructor(
    private val api: ProfileApi,
) {
    suspend fun getProfile(
        identifier: ProfileIdentifier,
        owner: Ed25519.KeyPair,
    ): Result<Model.UserProfile> {
        return runCatching {
            api.getProfile(identifier, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ProfileService.GetProfileResponse.Result.OK -> Result.success(response.userProfile)
                    ProfileService.GetProfileResponse.Result.NOT_FOUND -> Result.failure(GetUserProfileError.NotFound())
                    ProfileService.GetProfileResponse.Result.UNRECOGNIZED -> Result.failure(GetUserProfileError.Unrecognized())
                }
            },
            onFailure = { Result.failure(it.toValidationOrElse { cause -> GetUserProfileError.Other(cause) }) }
        )
    }

    suspend fun setDisplayName(
        displayName: String,
        owner: Ed25519.KeyPair,
    ): Result<Unit> {
        return runCatching {
            api.setDisplayName(displayName, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ProfileService.SetDisplayNameResponse.Result.OK -> Result.success(Unit)
                    ProfileService.SetDisplayNameResponse.Result.INVALID_DISPLAY_NAME -> Result.failure(SetDisplayNameError.InvalidDisplayName())
                    ProfileService.SetDisplayNameResponse.Result.DENIED -> Result.failure(SetDisplayNameError.Denied())
                    ProfileService.SetDisplayNameResponse.Result.FAILED_MODERATED ->
                        Result.failure(SetDisplayNameError.FailedModerated(response.flaggedCategory.toFlaggedCategory()))
                    ProfileService.SetDisplayNameResponse.Result.UNRECOGNIZED -> Result.failure(SetDisplayNameError.Unrecognized())
                }
            },
            onFailure = { Result.failure(it.toValidationOrElse { cause -> SetDisplayNameError.Other(cause) }) }
        )
    }

    suspend fun setUsername(
        username: String,
        owner: Ed25519.KeyPair,
    ): Result<Unit> {
        return runCatching {
            api.setUsername(username, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ProfileService.SetUsernameResponse.Result.OK -> Result.success(Unit)
                    ProfileService.SetUsernameResponse.Result.INVALID_USERNAME -> Result.failure(SetUsernameError.InvalidUsername())
                    ProfileService.SetUsernameResponse.Result.DENIED -> Result.failure(SetUsernameError.Denied())
                    ProfileService.SetUsernameResponse.Result.ALREADY_TAKEN -> Result.failure(SetUsernameError.AlreadyTaken())
                    ProfileService.SetUsernameResponse.Result.FAILED_MODERATED ->
                        Result.failure(SetUsernameError.FailedModerated(response.flaggedCategory.toFlaggedCategory()))
                    ProfileService.SetUsernameResponse.Result.INSUFFICIENT_BALANCE -> Result.failure(SetUsernameError.InsufficientBalance())
                    ProfileService.SetUsernameResponse.Result.RESERVED_WORD -> Result.failure(SetUsernameError.ReservedWord())
                    ProfileService.SetUsernameResponse.Result.UNRECOGNIZED -> Result.failure(SetUsernameError.Unrecognized())
                    null -> Result.failure(SetUsernameError.Unrecognized())
                }
            },
            onFailure = { Result.failure(it.toValidationOrElse { cause -> SetUsernameError.Other(cause) }) }
        )
    }

    suspend fun setProfilePicture(
        blobId: BlobId,
        owner: Ed25519.KeyPair,
    ): Result<MediaItem> {
        return runCatching {
            api.setProfilePicture(blobId, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ProfileService.SetProfilePictureResponse.Result.OK -> Result.success(response.profilePicture.toMediaItem())
                    ProfileService.SetProfilePictureResponse.Result.DENIED -> Result.failure(SetProfilePictureError.Denied())
                    ProfileService.SetProfilePictureResponse.Result.BLOB_NOT_FOUND -> Result.failure(SetProfilePictureError.BlobNotFound())
                    ProfileService.SetProfilePictureResponse.Result.BLOB_NOT_READY -> Result.failure(SetProfilePictureError.BlobNotReady())
                    ProfileService.SetProfilePictureResponse.Result.BLOB_REJECTED -> Result.failure(SetProfilePictureError.BlobRejected())
                    ProfileService.SetProfilePictureResponse.Result.INVALID_BLOB -> Result.failure(SetProfilePictureError.InvalidBlob())
                    ProfileService.SetProfilePictureResponse.Result.UNRECOGNIZED -> Result.failure(SetProfilePictureError.Unrecognized())
                }
            },
            onFailure = { Result.failure(it.toValidationOrElse { cause -> SetProfilePictureError.Other(cause) }) }
        )
    }

    suspend fun updateTipCard(
        owner: Ed25519.KeyPair,
        hexColor: String,
    ): Result<Unit> {
        return runCatching {
            api.updateTipCard(owner, hexColor)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ProfileService.UpdateTipCardResponse.Result.OK -> Result.success(Unit)
                    ProfileService.UpdateTipCardResponse.Result.DENIED -> Result.failure(UpdateTipCardError.Denied())
                    ProfileService.UpdateTipCardResponse.Result.INVALID_COLOR -> Result.failure(UpdateTipCardError.InvalidColor())
                    ProfileService.UpdateTipCardResponse.Result.UNRECOGNIZED -> Result.failure(UpdateTipCardError.Unrecognized())
                    null -> Result.failure(UpdateTipCardError.Unrecognized())
                }
            },
            onFailure = { Result.failure(it.toValidationOrElse { cause -> UpdateTipCardError.Other(cause) }) }
        )
    }

    suspend fun setMinDmChatInitFee(
        owner: Ed25519.KeyPair,
        fee: Fiat,
    ): Result<Unit> {
        return runCatching {
            api.setMinDmChatInitFee(owner, fee)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ProfileService.SetMinDmChatInitFeeResponse.Result.OK -> Result.success(Unit)
                    ProfileService.SetMinDmChatInitFeeResponse.Result.DENIED -> Result.failure(SetMinDmChatInitFeeError.Denied())
                    ProfileService.SetMinDmChatInitFeeResponse.Result.INVALID_AMOUNT -> Result.failure(SetMinDmChatInitFeeError.InvalidAmount())
                    ProfileService.SetMinDmChatInitFeeResponse.Result.UNRECOGNIZED -> Result.failure(SetMinDmChatInitFeeError.Unrecognized())
                    null -> Result.failure(SetMinDmChatInitFeeError.Unrecognized())
                }
            },
            onFailure = { Result.failure(it.toValidationOrElse { cause -> SetMinDmChatInitFeeError.Other(cause) }) }
        )
    }

    suspend fun linkSocialAccount(
        request: SocialAccountLinkRequest,
        owner: Ed25519.KeyPair,
    ): Result<Model.SocialProfile> {
        return runCatching {
            api.linkSocialAccount(request, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ProfileService.LinkSocialAccountResponse.Result.OK -> Result.success(response.socialProfile)
                    ProfileService.LinkSocialAccountResponse.Result.INVALID_LINKING_TOKEN -> Result.failure(LinkSocialAccountError.InvalidLinkingToken())
                    ProfileService.LinkSocialAccountResponse.Result.EXISTING_LINK -> Result.failure(LinkSocialAccountError.ExistingLink())
                    ProfileService.LinkSocialAccountResponse.Result.DENIED -> Result.failure(LinkSocialAccountError.Denied())
                    ProfileService.LinkSocialAccountResponse.Result.UNRECOGNIZED -> Result.failure(LinkSocialAccountError.Unrecognized())
                }
            },
            onFailure = { Result.failure(it.toValidationOrElse { cause -> LinkSocialAccountError.Other(cause) }) }
        )
    }

    suspend fun unlinkSocialAccount(
        request: SocialAccountUnlinkRequest,
        owner: Ed25519.KeyPair,
    ): Result<Unit> {
        return runCatching {
            api.unlinkSocialAccount(request, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ProfileService.UnlinkSocialAccountResponse.Result.OK -> Result.success(Unit)
                    ProfileService.UnlinkSocialAccountResponse.Result.DENIED -> Result.failure(UnlinkSocialAccountError.Denied())
                    ProfileService.UnlinkSocialAccountResponse.Result.UNRECOGNIZED -> Result.failure(UnlinkSocialAccountError.Unrecognized())
                }
            },
            onFailure = { Result.failure(it.toValidationOrElse { cause -> UnlinkSocialAccountError.Other(cause) }) }
        )
    }
}