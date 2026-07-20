package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.profile.v1.Model
import com.codeinc.flipcash.gen.profile.v1.ProfileService
import com.flipcash.services.internal.network.api.ProfileApi
import com.flipcash.services.internal.network.extensions.toFlaggedCategory
import com.getcode.opencode.utils.toValidationOrElse
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.LinkSocialAccountError
import com.flipcash.services.models.SetDisplayNameError
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.UnlinkSocialAccountError
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

internal class ProfileService @Inject constructor(
    private val api: ProfileApi,
) {
    suspend fun getProfile(
        userId: ID,
        owner: Ed25519.KeyPair,
    ): Result<Model.UserProfile> {
        return runCatching {
            api.getProfile(userId, owner)
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